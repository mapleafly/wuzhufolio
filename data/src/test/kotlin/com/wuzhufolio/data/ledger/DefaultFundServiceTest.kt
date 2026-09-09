package com.wuzhufolio.data.ledger

import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.CoinResolutionException
import com.wuzhufolio.domain.ledger.FundDateRange
import com.wuzhufolio.domain.ledger.FundEntryType
import com.wuzhufolio.domain.ledger.FundFilter
import com.wuzhufolio.domain.ledger.FundInput
import com.wuzhufolio.domain.ledger.LedgerErrorCode
import com.wuzhufolio.domain.ledger.LedgerValidationException
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * 资金管理用例（M8 · T8.1/T8.2 列表半边）：增资/撤资手动增删改（相对校验 V7/V9 + V7 同点绝对收紧）、
 * 黄金用例 2/3/7 接线形状（增资建立持仓 -> 买入解锁 V5 / 撤资零盈亏 / 调小删增资被拦）、
 * 折算（记录时价/离线 PENDING 回填纠正/黄金 9）、列表混合校准行 + 筛选。
 */
class DefaultFundServiceTest {

    @Suppress("LongParameterList") // 测试输入构造器（默认值 + 命名参数覆盖，M7 同口径）
    private fun input(
        kind: FlowKind = FlowKind.DEPOSIT,
        coin: String = "USDT",
        pickedCoinId: Long? = null,
        quantity: String = "1000",
        at: Instant = Instant.parse("2026-08-30T08:00:00Z"),
        sourceDest: String? = null,
        notes: String? = null,
    ) = FundInput(
        kind = kind,
        coinSymbol = coin,
        pickedCoinId = pickedCoinId,
        quantity = BigDecimal(quantity),
        time = at,
        sourceDest = sourceDest,
        notes = notes,
    )

    @Test
    fun depositWithUsdAnchoredStablecoinSavesAndFeedsOverview() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val id = env.fundService.saveFund(input())
            assertTrue(id > 0)
            val page = env.fundService.listFunds(FundFilter())
            assertEquals(1, page.rows.size)
            val row = page.rows.first()
            assertEquals(FundEntryType.DEPOSIT, row.entryType)
            assertEquals("USDT", row.coinSymbol)
            assertEquals(0, BigDecimal("1000").compareTo(row.baseAmount), "USD 锚定稳定币 1:1 非估算")
            assertTrue(!row.estimated)
            // 总览卡：现金 1000（白名单市值）、投入本金（净）1000、累计增资 1000
            val overview = page.overview!!
            assertEquals(0, BigDecimal("1000").compareTo(overview.availableCashFiat))
            assertEquals(0, BigDecimal("1000").compareTo(overview.investedNetFiat))
            assertEquals(0, BigDecimal("1000").compareTo(overview.cumulativeDepositsFiat))
            assertEquals(0, BigDecimal.ZERO.compareTo(overview.cumulativeWithdrawalsFiat))
        }
    }

    /** 黄金用例 2：转入 100 USDT -> 撤资 100：投入本金回 0、不产生盈亏（资金事件经服务进重放）。 */
    @Test
    fun goldenCase2ShapeDepositThenWithdrawPrincipalBackToZero() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            env.fundService.saveFund(input(quantity = "100"))
            val overview = env.fundService.listFunds(FundFilter()).overview!!
            assertEquals(0, BigDecimal("100").compareTo(overview.investedNetFiat))
            env.fundService.saveFund(
                input(kind = FlowKind.WITHDRAWAL, quantity = "100", at = Instant.parse("2026-08-30T09:00:00Z")),
            )
            val after = env.fundService.listFunds(FundFilter()).overview!!
            assertEquals(0, BigDecimal("100").compareTo(after.cumulativeDepositsFiat))
            assertEquals(0, BigDecimal("100").compareTo(after.cumulativeWithdrawalsFiat))
            assertEquals(0, BigDecimal.ZERO.compareTo(after.investedNetFiat))
        }
    }

    /** 黄金用例 3 形状（接线）：增资 100 -> 买 100 等值 BTC -> 涨至 150 卖出 -> 撤资 150：已实现 50、净值 0。 */
    @Test
    fun goldenCase3ShapeWithdrawalAfterProfit() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            env.fundService.saveFund(input(quantity = "100"))
            val sellId = env.service.saveTransaction(
                com.wuzhufolio.domain.ledger.TransactionInput(
                    exchange = "BINANCE",
                    baseSymbol = "BTC",
                    quoteSymbol = "USDT",
                    side = Side.BUY,
                    price = BigDecimal("50000"),
                    quantity = BigDecimal("0.002"),
                    fee = BigDecimal.ZERO,
                    feeCoinSymbol = "USDT",
                    time = Instant.parse("2026-08-30T08:00:00Z"),
                ),
            ).let { buyId ->
                assertTrue(buyId > 0, "增资后手动买入 V5 不再拦截（M7 §6 遗留的解锁路径）")
                env.service.saveTransaction(
                    com.wuzhufolio.domain.ledger.TransactionInput(
                        exchange = "BINANCE",
                        baseSymbol = "BTC",
                        quoteSymbol = "USDT",
                        side = Side.SELL,
                        price = BigDecimal("75000"),
                        quantity = BigDecimal("0.002"),
                        fee = BigDecimal.ZERO,
                        feeCoinSymbol = "USDT",
                        time = Instant.parse("2026-08-30T09:00:00Z"),
                    ),
                )
            }
            env.fundService.saveFund(
                input(kind = FlowKind.WITHDRAWAL, quantity = "150", at = Instant.parse("2026-08-30T10:00:00Z")),
            )
            val rows = env.service.listTransactions(com.wuzhufolio.domain.ledger.TxFilter())
            val sell = rows.first { it.id == sellId }
            assertEquals(0, BigDecimal("50").compareTo(sell.realizedPnlFiat!!), "(75000-50000)*0.002=50")
            val overview = env.fundService.listFunds(FundFilter()).overview!!
            assertEquals(0, BigDecimal("100").compareTo(overview.cumulativeDepositsFiat))
            assertEquals(0, BigDecimal("150").compareTo(overview.cumulativeWithdrawalsFiat))
        }
    }

    @Test
    fun withdrawalBeyondPositionBlockedV7() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            env.fundService.saveFund(input(quantity = "100"))
            val e = assertFailsWith<LedgerValidationException> {
                env.fundService.saveFund(input(kind = FlowKind.WITHDRAWAL, quantity = "200"))
            }
            assertEquals(LedgerErrorCode.INSUFFICIENT_POSITION, e.code)
            assertEquals("USDT", e.coinSymbol)
        }
    }

    /** V7 同点绝对收紧（M8 口径）：导入异常位点（负持仓）上的撤资不允许继续加深。 */
    @Test
    fun withdrawalDeepeningImportedNegativeStillBlockedV7() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            // CSV 导入卖出 1 BTC（无买入 -> LENIENT 负持仓异常，不拦截）
            val csv = "exchange,order_id,pair,side,price,quantity,fee,fee_currency,time,notes\n" +
                "BINANCE,,BTC/USDT,卖出,50000,1,0,USDT,2026-08-01 00:00:00,"
            val preview = env.service.parseCsv(csv.toByteArray())
            val summary = env.service.confirmCsvImport(preview.sessionId)
            assertTrue(summary.anomalousCoins.contains("BTC"), "导入路径例外：BTC 持仓异常")
            // 相对校验对既存负位点放行（M4 §5-5），但撤资本位点绝对校验收紧 -> V7 阻止加深
            val e = assertFailsWith<LedgerValidationException> {
                env.fundService.saveFund(
                    input(kind = FlowKind.WITHDRAWAL, coin = "BTC", quantity = "0.5"),
                )
            }
            assertEquals(LedgerErrorCode.INSUFFICIENT_POSITION, e.code)
            assertEquals("BTC", e.coinSymbol)
        }
    }

    /** 黄金用例 7（接线）：增资 100、买入消费 100、把增资改为 50 -> 重放至买入时持仓为负，V9 阻止。 */
    @Test
    fun shrinkingDepositBreaksLaterBuyBlockedV9() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val id = env.fundService.saveFund(input(quantity = "100"))
            env.service.saveTransaction(
                TransactionInputs.buy(price = "100", quantity = "1"),
            )
            val e = assertFailsWith<LedgerValidationException> {
                env.fundService.updateFund(id, input(quantity = "50"))
            }
            assertEquals(LedgerErrorCode.REPLAY_CONFLICT, e.code)
        }
    }

    /** 黄金用例 7 形状（接线）：删除被依赖的增资 -> V9 阻止（确认框在 UI 层）。 */
    @Test
    fun deletingDepositBlockedV9() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val id = env.fundService.saveFund(input(quantity = "100"))
            env.service.saveTransaction(
                TransactionInputs.buy(price = "100", quantity = "1"),
            )
            val page = env.fundService.listFunds(FundFilter())
            val uuid = page.rows.first { it.id == id }.uuid
            val e = assertFailsWith<LedgerValidationException> {
                env.fundService.deleteFunds(listOf(uuid))
            }
            assertEquals(LedgerErrorCode.REPLAY_CONFLICT, e.code)
        }
    }

    /** 修复轮 §8-1（GUI 走查）：同名符号歧义下纯符号解析拒绝，候选点选（pickedCoinId）直达保存。 */
    @Test
    fun ambiguousSymbolRequiresPickedCoinWhichBypassesResolution() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            // 注入同名资产（Abstract 链 USDT）——目录出现 2 个 USDT（真实目录为 49 个同形场景）
            env.catalog.refreshDirectory(
                listOf(CoinDirectoryEntry("usdt-abstract", "usdt", "Abstract USD")),
            )
            val tether = env.coin("USDT")!!
            val abstractUsdt = env.catalog.getByCgId("usdt-abstract")!!
            // 纯符号保存 -> 歧义拒绝
            val e = assertFailsWith<CoinResolutionException> { env.fundService.saveFund(input()) }
            assertTrue(e.reason.contains("歧义"), "应报同名歧义：${e.reason}")
            // 候选点选（选 Abstract USDT）-> 直达保存成功
            val id = env.fundService.saveFund(input(pickedCoinId = abstractUsdt.id))
            assertTrue(id > 0)
            val row = env.fundService.listFunds(FundFilter()).rows.first { it.id == id }
            assertEquals(abstractUsdt.id, row.coinId)
            // 点选与输入符号不一致 -> 类型化拒绝
            assertFailsWith<CoinResolutionException> {
                env.fundService.saveFund(input(coin = "BTC", pickedCoinId = tether.id))
            }
        }
    }

    /** 修复轮 §8-1（GUI 走查）：默认币种按白名单 cg_id 直取（唯一确定），返回完整目录行。 */
    @Test
    fun defaultCoinResolvesByWhitelistCgId() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val default = env.fundService.defaultCoin()
            assertEquals("tether", default!!.cgId)
            assertEquals("USDT", default.symbol)
            env.settings.putGlobal(DefaultTransactionLedgerService.SETTING_FIAT, "EUR")
            assertEquals("usd-coin", env.fundService.defaultCoin()!!.cgId)
        }
    }

    @Test
    fun fiatInputRejectedWithStablecoinHint() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val e = assertFailsWith<CoinResolutionException> { env.fundService.saveFund(input(coin = "USD")) }
            assertTrue(e.message!!.contains("USDT"), "孪生映射法币应点名稳定币：${e.message}")
            val e2 = assertFailsWith<CoinResolutionException> { env.fundService.saveFund(input(coin = "CNY")) }
            assertTrue(e2.message!!.contains("稳定币"), "无映射法币同样拒绝")
        }
    }

    @Test
    fun offlineDepositMarkedPendingAndBackfillCorrectsValue() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val btc = env.coin("BTC")!!
            // 无任何价格：名义零折算 + 估算（保数量链）
            val id = env.fundService.saveFund(input(coin = "BTC", quantity = "1"))
            var page = env.fundService.listFunds(FundFilter())
            assertTrue(page.rows.first { it.id == id }.estimated, "离线保存应标估算中")
            assertEquals(0, BigDecimal.ZERO.compareTo(page.rows.first { it.id == id }.baseAmount))
            // 黄金用例 9 语义：行情回填后重建事件自动纠正（无需改行）
            env.putSnapshot(btc.id, "USD", "60000", Instant.parse("2026-08-30T07:00:00Z"))
            page = env.fundService.listFunds(FundFilter())
            val row = page.rows.first { it.id == id }
            assertTrue(!row.estimated)
            assertEquals(0, BigDecimal("60000").compareTo(row.baseAmount))
            assertEquals(0, BigDecimal("60000").compareTo(page.overview!!.cumulativeDepositsFiat))
        }
    }

    @Test
    fun depositUsesRecordTimeSnapshotPrice() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val btc = env.coin("BTC")!!
            env.putSnapshot(btc.id, "USD", "50000", Instant.parse("2026-08-30T07:00:00Z"))
            val id = env.fundService.saveFund(input(coin = "BTC", quantity = "2"))
            val row = env.fundService.listFunds(FundFilter()).rows.first { it.id == id }
            assertEquals(0, BigDecimal("100000").compareTo(row.baseAmount), "2 × 记录时价 50,000")
        }
    }

    @Test
    fun fundsListMergesReconciliationRowsAndFiltersApply() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val session = env.sessions.get()!!
            val btc = env.coin("BTC")!!
            env.fundService.saveFund(input(quantity = "1000", sourceDest = "交易所A", notes = "入金"))
            env.fundService.saveFund(
                input(kind = FlowKind.WITHDRAWAL, quantity = "100", at = Instant.parse("2026-09-01T08:00:00Z")),
            )
            // 直插校准记录（校准执行流的入库形状，执行流测试见 CalibrationServiceTest）
            env.recons.insert(
                NewReconciliationRow(
                    accountId = session.account.id,
                    symbol = "BTC",
                    coinId = btc.id.toInt(),
                    exchange = "BINANCE",
                    localQuantity = BigDecimal("1.0"),
                    exchangeQuantity = BigDecimal("0.8"),
                    delta = BigDecimal("-0.2"),
                    baseAmount = BigDecimal("10000"),
                ),
            )
            val all = env.fundService.listFunds(FundFilter()).rows
            assertEquals(3, all.size)
            assertEquals(
                FundEntryType.RECONCILIATION,
                all.first { it.entryType == FundEntryType.RECONCILIATION }.entryType,
            )
            assertEquals("BINANCE", all.first { it.entryType == FundEntryType.RECONCILIATION }.sourceDest)

            // 类型筛选
            assertEquals(1, env.fundService.listFunds(FundFilter(type = FundEntryType.RECONCILIATION)).rows.size)
            assertEquals(1, env.fundService.listFunds(FundFilter(type = FundEntryType.DEPOSIT)).rows.size)
            // 搜索（来源/币种/备注）
            assertEquals(1, env.fundService.listFunds(FundFilter(query = "交易所A")).rows.size)
            assertEquals(1, env.fundService.listFunds(FundFilter(query = "btc")).rows.size)
            assertEquals(1, env.fundService.listFunds(FundFilter(query = "入金")).rows.size)
            // 日期筛选：近 30 天（以 2026-09-08 为「现在」口径由 Instant.now 决定——只验 Older 档排除当月行）
            val older = env.fundService.listFunds(FundFilter(dateRange = FundDateRange.OLDER)).rows
            assertTrue(older.none { it.time.isAfter(Instant.now().minus(90, ChronoUnit.DAYS)) })
        }
    }

    @Test
    fun deleteReconciliationRowThatBreaksLaterSellBlockedV9() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val session = env.sessions.get()!!
            val btc = env.coin("BTC")!!
            // 本地链：买入 0.4（单一来源）；其后（校准时刻之后）卖出 0.9 依赖锚点对齐后的余额
            env.seedExchangeTrade("BTC", Side.BUY, "50000", "0.4", Instant.parse("2026-08-01T00:00:00Z"))
            env.seedExchangeTrade("BTC", Side.SELL, "60000", "0.9", Instant.now().plusSeconds(86400))
            // 校准（正差额 +0.5）把持仓对齐到 0.9 -> 其后卖出 0.9 恰好清零；
            // 删除锚点后回退到本地链 0.4 − 0.9 = −0.5（操作新制造负边界）-> V9 阻止
            env.recons.insert(
                NewReconciliationRow(
                    accountId = session.account.id,
                    symbol = "BTC",
                    coinId = btc.id.toInt(),
                    exchange = "BINANCE",
                    localQuantity = BigDecimal("0.4"),
                    exchangeQuantity = BigDecimal("0.9"),
                    delta = BigDecimal("0.5"),
                    baseAmount = BigDecimal("25000"),
                ),
            )
            val reconUuid = env.recons.listAll(session.account.id).first().uuid
            val e = assertFailsWith<LedgerValidationException> {
                env.fundService.deleteFunds(listOf(reconUuid))
            }
            assertEquals(LedgerErrorCode.REPLAY_CONFLICT, e.code)
            assertEquals("BTC", e.coinSymbol)
        }
    }
}

/** 测试内交易输入工厂（复用 M7 测试口径：BINANCE + USDT 计价）。 */
private object TransactionInputs {
    fun buy(price: String, quantity: String, at: Instant = Instant.parse("2026-08-30T08:00:00Z")) =
        com.wuzhufolio.domain.ledger.TransactionInput(
            exchange = "BINANCE",
            baseSymbol = "BTC",
            quoteSymbol = "USDT",
            side = Side.BUY,
            price = BigDecimal(price),
            quantity = BigDecimal(quantity),
            fee = BigDecimal.ZERO,
            feeCoinSymbol = "USDT",
            time = at,
        )
}
