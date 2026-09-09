package com.wuzhufolio.data.ledger

import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.CoinResolutionException
import com.wuzhufolio.domain.ledger.CsvRowStatus
import com.wuzhufolio.domain.ledger.FeeQuoteRequest
import com.wuzhufolio.domain.ledger.LedgerErrorCode
import com.wuzhufolio.domain.ledger.LedgerValidationException
import com.wuzhufolio.domain.ledger.TransactionInput
import com.wuzhufolio.domain.ledger.TxFilter
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * 交易账本用例（T7.1–T7.4）：手动增删改（相对校验 V5/V9）、费率自动计算（交易所 > 全局 + 三态）、
 * CSV 解析预览/消歧/确认导入（LENIENT + 持仓异常预告）。黄金用例 6 形状（负持仓导入）覆盖于 CSV 路径。
 */
class DefaultTransactionLedgerServiceTest {

    @Suppress("LongParameterList") // 测试输入构造器（默认值 + 命名参数覆盖）
    private fun input(
        base: String = "BTC",
        quote: String = "USDT",
        side: Side = Side.BUY,
        price: String = "50000",
        quantity: String = "0.1",
        fee: String = "0",
        feeCoin: String = "USDT",
        at: Instant = Instant.parse("2026-08-30T08:00:00Z"),
    ) = TransactionInput(
        exchange = "BINANCE",
        baseSymbol = base,
        quoteSymbol = quote,
        side = side,
        price = BigDecimal(price),
        quantity = BigDecimal(quantity),
        fee = BigDecimal(fee),
        feeCoinSymbol = feeCoin,
        time = at,
    )

    private val header = "exchange,order_id,pair,side,price,quantity,fee,fee_currency,time,notes"

    /** 导入一笔 USDT/USDC 买入（LENIENT：不校验余额，建立 USDT 计价持仓——手动买入余额校验的前提）。 */
    private suspend fun seedUsdtBalance(env: LedgerTestEnv, amount: String = "50000"): String {
        val csv = header + "\nBINANCE,,USDT/USDC,买入,1,$amount,0,USDC,2026-08-01 00:00:00,"
        val preview = env.service.parseCsv(csv.toByteArray())
        return env.service.confirmCsvImport(preview.sessionId).let { it.imported.toString() }
    }

    @Test
    fun manualBuySucceedsAfterUsdtBalanceFromCsv() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            assertEquals("1", seedUsdtBalance(env))
            val id = env.service.saveTransaction(input())
            assertTrue(id > 0)
            val rows = env.service.listTransactions(TxFilter())
            assertEquals(2, rows.size)
            val buy = rows.first { it.id == id }
            assertEquals("BTC/USDT", buy.pair)
            assertEquals(Side.BUY, buy.side)
            assertEquals(0, BigDecimal("5000").compareTo(buy.total))
            assertEquals("Manual", buy.source)
            assertTrue(!buy.estimated, "USD 锚定稳定币非估算")
        }
    }

    @Test
    fun manualBuyWithoutQuoteBalanceBlockedV5() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val e = assertFailsWith<LedgerValidationException> { env.service.saveTransaction(input()) }
            assertEquals(LedgerErrorCode.INSUFFICIENT_BALANCE, e.code)
            assertEquals("USDT", e.coinSymbol)
        }
    }

    @Test
    fun manualSellBeyondPositionBlockedV7Like() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            seedUsdtBalance(env)
            val e = assertFailsWith<LedgerValidationException> {
                env.service.saveTransaction(input(side = Side.SELL, price = "60000", quantity = "0.5"))
            }
            assertEquals(LedgerErrorCode.INSUFFICIENT_POSITION, e.code)
            assertEquals("BTC", e.coinSymbol)
        }
    }

    @Test
    fun editingEarlierTradeThatBreaksLaterOneBlockedAsReplayConflict() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            seedUsdtBalance(env)
            val buyId = env.service.saveTransaction(input(quantity = "1"))
            val sellId = env.service.saveTransaction(
                input(side = Side.SELL, price = "60000", quantity = "0.5", at = Instant.parse("2026-08-30T09:00:00Z")),
            )
            assertTrue(sellId > 0)
            // 把买入数量改小到 0.2：卖出位点 BTC 0.2-0.5 -> 负（违例在卖出事件，非被编辑事件）
            val e = assertFailsWith<LedgerValidationException> {
                env.service.updateTransaction(buyId, input(quantity = "0.2"))
            }
            assertEquals(LedgerErrorCode.REPLAY_CONFLICT, e.code)
            assertEquals("BTC", e.coinSymbol)
        }
    }

    @Test
    fun deleteMiddleTradeThatBreaksLaterOneBlockedAsReplayConflict() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            seedUsdtBalance(env)
            val buyId = env.service.saveTransaction(input(quantity = "1"))
            env.service.saveTransaction(
                input(side = Side.SELL, price = "60000", quantity = "0.5", at = Instant.parse("2026-08-30T09:00:00Z")),
            )
            // 删除买入 -> 卖出位点 BTC 0-0.5 负
            val e = assertFailsWith<LedgerValidationException> { env.service.deleteTransactions(listOf(buyId)) }
            assertEquals(LedgerErrorCode.REPLAY_CONFLICT, e.code)
            // 冲突定位上下文（2026-09-08 修复轮）：提示可指向具体冲突记录（卖出 0.5 BTC/USDT）
            assertEquals("BTC", e.coinSymbol)
            assertEquals(Side.SELL, e.conflictSide)
            assertEquals("BTC/USDT", e.conflictPair)
            assertEquals(0, BigDecimal("0.5").compareTo(e.conflictQuantity))
            assertEquals(Instant.parse("2026-08-30T09:00:00Z"), e.conflictAt)
        }
    }

    @Test
    fun unresolvableCoinRejectedWithResolutionError() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            seedUsdtBalance(env)
            val e = assertFailsWith<CoinResolutionException> {
                env.service.saveTransaction(input(base = "DOGE"))
            }
            assertTrue(e.symbol == "DOGE")
        }
    }

    @Test
    fun feeQuotePrefersExchangeRateOverGlobalAndHandlesThirdRole() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            env.feeRules.upsert(env.sessions.get()!!.account.id, "", BigDecimal("0.2"), BigDecimal("0.2")) // 全局
            env.feeRules.upsert(env.sessions.get()!!.account.id, "BINANCE", BigDecimal("0.05"), BigDecimal("0.05"))
            val bnb = env.coin("BNB")!!
            env.putSnapshot(bnb.id, "USD", "600", Instant.now().minusSeconds(60))

            // 计价币手续费（quote 角色）：费率 0.05% × 总价 5000 = 2.5
            val quote = env.service.feeQuote(
                FeeQuoteRequest("BINANCE", Side.BUY, BigDecimal("0.1"), BigDecimal("50000"), "USDT", "BTC", "USDT"),
            )
            assertNotNull(quote)
            assertEquals(BigDecimal("0.05"), quote.ratePercent)
            assertEquals(0, BigDecimal("2.5").compareTo(quote.coinQty))

            // 第三币种（BNB 抵扣）：数量 = 费率×总价×px(quote)/px(fee) = 2.5 × 1 / 600
            val third = env.service.feeQuote(
                FeeQuoteRequest("BINANCE", Side.BUY, BigDecimal("0.1"), BigDecimal("50000"), "BNB", "BTC", "USDT"),
            )
            assertNotNull(third)
            assertEquals(
                0,
                BigDecimal("2.5").divide(BigDecimal("600"), 12, java.math.RoundingMode.HALF_UP)
                    .compareTo(third.coinQty),
            )
        }
    }

    @Test
    fun feeQuoteReturnsNullWithoutRules() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val result = env.service.feeQuote(
                FeeQuoteRequest("BINANCE", Side.BUY, BigDecimal("0.1"), BigDecimal("50000"), "USDT", "BTC", "USDT"),
            )
            assertNull(result)
        }
    }

    @Test
    fun csvPreviewAndConfirmImportWithDedupAndAnomaly() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val accountId = env.sessions.get()!!.account.id
            // 含：1 新行、1 疑似重复（先手工建同键行）、1 负持仓卖出（黄金 6 形状）
            env.repository.insert(
                NewLedgerTxRow(
                    accountId = accountId,
                    exchange = "BINANCE",
                    exchangeOrderId = "ord-1",
                    pair = "ETH/USDT",
                    baseCoinId = env.coin("ETH")!!.id.toInt(),
                    quoteCoinId = env.coin("USDT")!!.id.toInt(),
                    side = Side.BUY,
                    price = BigDecimal("3400"),
                    quantity = BigDecimal("1"),
                    fee = BigDecimal.ZERO,
                    feeCurrency = "USDT",
                    time = Instant.parse("2025-03-01T00:00:00Z"),
                    notes = null,
                    source = "CSV",
                    priceStatus = "OK",
                ),
            )
            val csv = header + "\n" +
                "BINANCE,ord-1,ETH/USDT,买入,3400,1,0,USDT,2025-03-01 00:00:00,重复\n" +
                "BINANCE,,BTC/USDT,卖出,60000,0.5,0,USDT,2025-06-01 00:00:00,负持仓\n" +
                "BINANCE,,SOL/USDT,买入,150,2,0,USDT,2025-07-01 00:00:00,未收录"
            val preview = env.service.parseCsv(csv.toByteArray())
            assertEquals(3, preview.totalRows)
            assertEquals(1, preview.duplicateRows)
            assertEquals(1, preview.unresolvedRows)
            assertEquals(1, preview.newRows)
            assertEquals(0, preview.errorRows.size, "SOL 未收录 = UNRESOLVED 行，非格式错误")
            val sell = preview.rows.first { it.pair == "BTC/USDT" }
            assertEquals(CsvRowStatus.IMPORT, sell.status)
            // 影响摘要含 BTC 负持仓预告（黄金 6 形状）
            assertTrue(preview.anomalousCoins.isNotEmpty(), "负持仓预告应包含 BTC")

            val summary = env.service.confirmCsvImport(preview.sessionId)
            assertEquals(1, summary.imported) // BTC 卖出 + ETH 新行？——重复行未勾选 -> 跳过
            assertEquals(1, summary.duplicatesSkipped)
            assertEquals(1, summary.unresolvedSkipped)
            assertTrue(summary.anomalousCoins.contains("BTC"), "导入后 BTC 持仓为负 -> 持仓异常清单")
        }
    }

    @Test
    fun csvAmbiguityChoiceFreezesMappingAndImports() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            // 注入第二个 "btc" 同名资产 -> BTC 歧义
            runBlocking {
                env.catalog.refreshDirectory(listOf(CoinDirectoryEntry("bitcoin-sv-fake", "btc", "Fake Bitcoin SV")))
            }
            val csv = header + "\nBINANCE,,BTC/USDT,买入,50000,0.1,0,USDT,2025-03-01 00:00:00,"
            val preview = env.service.parseCsv(csv.toByteArray())
            assertEquals(1, preview.unresolvedRows)
            assertTrue(preview.ambiguous.isNotEmpty(), "歧义 ticker 应上浮")
            val ticker = preview.ambiguous.first { it.asset == "BTC" }
            assertTrue(ticker.candidates.containsKey("bitcoin"))

            val summary = env.service.confirmCsvImport(
                preview.sessionId,
                ambiguityChoices = mapOf("BINANCE|BTC" to "bitcoin"),
            )
            assertEquals(1, summary.imported)
            // 固化后复用（FROZEN_MAP）：再导一次应跳过（疑似重复）
            val preview2 = env.service.parseCsv(csv.toByteArray())
            assertEquals(1, preview2.duplicateRows)
        }
    }

    @Test
    fun listShowsSellRealizedAndEstimatedFlag() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            seedUsdtBalance(env)
            env.service.saveTransaction(input(quantity = "1"))
            env.service.saveTransaction(
                input(side = Side.SELL, price = "60000", quantity = "0.5", at = Instant.parse("2026-08-30T09:00:00Z")),
            )
            val rows = env.service.listTransactions(TxFilter())
            val sell = rows.first { it.side == Side.SELL }
            assertNotNull(sell.realizedPnlFiat, "卖出行显示该笔已实现盈亏")
            // 已实现 = (60000×0.5 − 0) − 0.5×50000 = 5000
            assertEquals(0, BigDecimal("5000").compareTo(sell.realizedPnlFiat))
        }
    }

    @Test
    fun csvTemplateContainsHeaderAndComments() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val tpl = env.service.csvTemplateCsv()
            assertTrue(tpl.startsWith("# WuZhuFolio"))
            assertTrue(tpl.contains("exchange,order_id,pair,side,price,quantity,fee,fee_currency,time,notes"))
            // 模板本身可被解析（# 注释行跳过 + 示例行可解析）
            val preview = env.service.parseCsv(tpl.toByteArray())
            assertEquals(0, preview.errorRows.size)
            assertTrue(preview.totalRows >= 3, "示例行应可解析")
        }
    }
}
