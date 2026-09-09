package com.wuzhufolio.data.ledger

import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.exchange.ExchangeError
import com.wuzhufolio.domain.ledger.CalibrationBlockedException
import com.wuzhufolio.domain.ledger.LedgerErrorCode
import com.wuzhufolio.domain.ledger.LedgerValidationException
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * 持仓校准用例（M8 · T8.2 + M4 遗留「校准执行流」）：单一来源门（无记录/多来源）、密钥缺失、
 * 市价缺失（行情完全不可用）、执行（差额规划 -> 锚点入库 -> sync_logs 留痕）、零差额不生成记录、
 * 锚点其后记录冲突 V9、余额获取失败映射。黄金用例 8 的引擎数值已由 M4 GoldenCasesTest 守护。
 */
class CalibrationServiceTest {

    /** 单一来源币种 = 仅交易所 API 同步行（BINANCE）。 */
    private suspend fun seedSingleSourceCoin(env: LedgerTestEnv, quantity: String) {
        env.seedExchangeTrade("BTC", Side.BUY, "50000", quantity, Instant.parse("2026-08-01T00:00:00Z"))
    }

    @Test
    fun noRecordsCoinBlocked() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val e = assertFailsWith<CalibrationBlockedException> { env.calibrationService.prepare("BTC") }
            assertEquals(CalibrationBlockedException.Reason.NO_RECORDS, e.reason)
        }
    }

    @Test
    fun multiSourceCoinBlockedWithGuidance() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            seedSingleSourceCoin(env, "1.0")
            // 手动 CSV 来源加入 -> 多来源（Manual/CSV 与 Exchange 混合）
            val csv = "exchange,order_id,pair,side,price,quantity,fee,fee_currency,time,notes\n" +
                "BINANCE,,BTC/USDT,买入,50000,0.1,0,USDT,2026-08-02 00:00:00,"
            val preview = env.service.parseCsv(csv.toByteArray())
            env.service.confirmCsvImport(preview.sessionId)
            val e = assertFailsWith<CalibrationBlockedException> { env.calibrationService.prepare("BTC") }
            assertEquals(CalibrationBlockedException.Reason.MULTI_SOURCE, e.reason)
            assertTrue(e.message.contains("多来源"), "提示应包含 PRD 多来源文案")
        }
    }

    @Test
    fun missingExchangeKeyBlocked() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            seedSingleSourceCoin(env, "1.0")
            val e = assertFailsWith<CalibrationBlockedException> { env.calibrationService.prepare("BTC") }
            assertEquals(CalibrationBlockedException.Reason.NO_EXCHANGE_KEY, e.reason)
            assertTrue(e.message.contains("API 管理"), "提示应引导到设置页添加只读密钥")
        }
    }

    @Test
    fun missingMarketPriceBlocked() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            seedSingleSourceCoin(env, "1.0")
            env.addKey()
            val e = assertFailsWith<CalibrationBlockedException> { env.calibrationService.prepare("BTC") }
            assertEquals(CalibrationBlockedException.Reason.NO_MARKET_PRICE, e.reason)
        }
    }

    @Test
    fun balanceFetchFailureSurfacesTypedCopy() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            seedSingleSourceCoin(env, "1.0")
            env.addKey()
            val btc = env.coin("BTC")!!
            env.putSnapshot(btc.id, "USD", "50000", Instant.parse("2026-08-01T00:00:00Z"))
            env.fakeBalanceError = ExchangeError.InvalidKey
            val e = assertFailsWith<CalibrationBlockedException> { env.calibrationService.prepare("BTC") }
            assertEquals(CalibrationBlockedException.Reason.BALANCE_FETCH_FAILED, e.reason)
            assertTrue(e.message.contains("密钥已失效"), "B2 家族文案")
        }
    }

    @Test
    fun prepareReturnsPlanAndExecuteWritesAnchorAndSyncLog() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            seedSingleSourceCoin(env, "1.0")
            env.addKey(name = "recon-key")
            val btc = env.coin("BTC")!!
            env.putSnapshot(btc.id, "USD", "50000", Instant.parse("2026-08-01T00:00:00Z"))
            env.fakeBalances = listOf(
                com.wuzhufolio.domain.exchange.Balance("BTC", BigDecimal("0.8"), BigDecimal.ZERO),
            )
            val prep = env.calibrationService.prepare("BTC")
            assertEquals("BINANCE", prep.exchangeName)
            assertEquals(0, BigDecimal("1.0").compareTo(prep.localQuantity))
            assertEquals(0, BigDecimal("0.8").compareTo(prep.exchangeQuantity))
            assertEquals(0, BigDecimal("-0.2").compareTo(prep.delta))
            assertEquals(0, BigDecimal("10000").compareTo(prep.deltaFiat), "|-0.2| × 50,000")
            assertEquals(com.wuzhufolio.domain.engine.FlowKind.WITHDRAWAL, prep.direction)

            val result = env.calibrationService.execute("BTC")
            assertTrue(result.recorded)
            assertEquals(0, BigDecimal("-0.2").compareTo(result.row!!.delta))
            // reconciliation_records 入库（资金列表「校准」行数据源）
            assertEquals(1, env.recons.listAll(env.sessions.get()!!.account.id).size)
            // sync_logs 留痕（PRD 故事 4.1-5）
            val logs = com.wuzhufolio.data.exchange.SyncLogRepository(env.gate)
                .recent(env.sessions.get()!!.account.id, 10)
            assertEquals(1, logs.size)
            assertTrue(logs.first().message.contains("持仓校准 BTC"))
            assertEquals("OK", logs.first().status.storageValue)
        }
    }

    @Test
    fun zeroDeltaExecutesWithoutRecord() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            seedSingleSourceCoin(env, "1.0")
            env.addKey()
            val btc = env.coin("BTC")!!
            env.putSnapshot(btc.id, "USD", "50000", Instant.parse("2026-08-01T00:00:00Z"))
            env.fakeBalances = listOf(
                com.wuzhufolio.domain.exchange.Balance("BTC", BigDecimal("1.0"), BigDecimal.ZERO),
            )
            val result = env.calibrationService.execute("BTC")
            assertTrue(!result.recorded)
            assertEquals(0, env.recons.listAll(env.sessions.get()!!.account.id).size)
        }
    }

    @Test
    fun anchorMakingLaterSellNegativeBlockedV9() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            seedSingleSourceCoin(env, "1.0")
            // 卖出在校准时刻**之后**：锚点把持仓对齐到 0.5 后，该笔 0.9 卖出将转负
            env.seedExchangeTrade(
                "BTC",
                Side.SELL,
                "60000",
                "0.9",
                Instant.now().plusSeconds(86400),
            )
            env.addKey()
            val btc = env.coin("BTC")!!
            env.putSnapshot(btc.id, "USD", "50000", Instant.parse("2026-08-01T00:00:00Z"))
            env.fakeBalances = listOf(
                com.wuzhufolio.domain.exchange.Balance("BTC", BigDecimal("0.5"), BigDecimal.ZERO),
            )
            // 预览可达（本地 0.1 -> 交易所 0.5，差额 +0.4）；执行被相对校验拦住
            val prep = env.calibrationService.prepare("BTC")
            assertEquals(0, BigDecimal("0.4").compareTo(prep.delta))
            val e = assertFailsWith<LedgerValidationException> { env.calibrationService.execute("BTC") }
            assertEquals(LedgerErrorCode.REPLAY_CONFLICT, e.code)
            assertEquals("BTC", e.coinSymbol)
            assertEquals(0, env.recons.listAll(env.sessions.get()!!.account.id).size, "拦截后不得入库")
        }
    }

    @Test
    fun historyListsReconciliationRowsForCoin() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val session = env.sessions.get()!!
            val btc = env.coin("BTC")!!
            val eth = env.coin("ETH")!!
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
            env.recons.insert(
                NewReconciliationRow(
                    accountId = session.account.id,
                    symbol = "ETH",
                    coinId = eth.id.toInt(),
                    exchange = "BINANCE",
                    localQuantity = BigDecimal("10"),
                    exchangeQuantity = BigDecimal("12"),
                    delta = BigDecimal("2"),
                    baseAmount = BigDecimal("6000"),
                ),
            )
            val btcHistory = env.calibrationService.history("BTC")
            assertEquals(1, btcHistory.size)
            assertEquals("BINANCE", btcHistory.first().exchange)
        }
    }
}
