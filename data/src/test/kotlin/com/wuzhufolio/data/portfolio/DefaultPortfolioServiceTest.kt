package com.wuzhufolio.data.portfolio

import com.wuzhufolio.data.ledger.DefaultTransactionLedgerService
import com.wuzhufolio.data.ledger.LedgerTestEnv
import com.wuzhufolio.data.ledger.NewReconciliationRow
import com.wuzhufolio.data.market.SnapshotWrite
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.engine.PortfolioCalculator
import com.wuzhufolio.domain.engine.RecordSource
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.engine.SourceClassification
import com.wuzhufolio.domain.ledger.FundFilter
import com.wuzhufolio.domain.ledger.FundInput
import com.wuzhufolio.domain.ledger.TransactionInput
import com.wuzhufolio.domain.market.PriceSource
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * 聚合页用例（M12 T12.1）：全量重放 -> PortfolioCalculator -> 逐币行的编排口径。
 *
 * 覆盖：行序（市值降序/缺价置末按名称升序）、占比与净值边界、零持仓行保留、24h 覆盖数与异源标注、
 * 持仓异常与估算标记、币种详情（目录字段 + 同一计算的行 + 校准历史倒序 + 校准入口可见性）、
 * 基础法币与白名单注入、账户隔离。固定时钟使 24h 配对可确定性断言。
 */
class DefaultPortfolioServiceTest {

    @Test
    fun emptyLedgerYieldsNeutralSnapshot() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val snapshot = portfolioService(env).snapshot()
            assertEquals("USD", snapshot.fiat)
            assertTrue(snapshot.rows.isEmpty())
            assertEquals(0, BigDecimal.ZERO.compareTo(snapshot.metrics.netValueFiat))
            assertNull(snapshot.metrics.totalReturnFiat, "累计增资 = 0 → 总收益 null（展示 --）")
            assertNull(snapshot.metrics.roiPercent)
            assertNull(snapshot.priceAsOf, "尚无行情")
            assertTrue(!snapshot.estimated)
            assertTrue(snapshot.anomalousCoins.isEmpty())
            assertEquals(0, snapshot.change24h.covered)
            assertEquals(0, snapshot.change24h.total)
            assertNull(snapshot.change24h.pnlFiat)
        }
    }

    @Test
    fun rowsAreMarketValueDescendingWithCatalogFieldsAndShares() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            env.fundService.saveFund(deposit("1000"))
            env.service.saveTransaction(buy("BTC", price = "50000", quantity = "0.002"))
            env.putSnapshot(env.coin("BTC")!!.id, "USD", "60000", NOW)
            env.putSnapshot(env.coin("USDT")!!.id, "USD", "1", NOW)
            val snapshot = portfolioService(env).snapshot()
            // 市值降序：USDT 900 > BTC 120
            assertEquals(listOf("tether", "bitcoin"), snapshot.rows.map { it.cgId })
            val usdt = snapshot.rows[0]
            assertEquals("USDT", usdt.symbol)
            assertEquals("Tether", usdt.name)
            assertEquals(0, BigDecimal("1").compareTo(usdt.priceFiat!!))
            assertEquals(0, BigDecimal("900").compareTo(usdt.marketValueFiat!!))
            assertEquals(0, BigDecimal("88.235294117647").compareTo(usdt.sharePercent!!))
            assertTrue(usdt.priced)
            assertTrue(!usdt.anomalous)
            val btc = snapshot.rows[1]
            assertEquals("BTC", btc.symbol)
            assertEquals(0, BigDecimal("0.002").compareTo(btc.quantity))
            assertEquals(0, BigDecimal("50000").compareTo(btc.avgCostFiat!!))
            assertEquals(0, BigDecimal("120").compareTo(btc.marketValueFiat!!))
            assertEquals(0, BigDecimal("20").compareTo(btc.floatPnlFiat!!))
            assertEquals(0, BigDecimal("20").compareTo(btc.floatPnlPercent!!))
            assertEquals(0, BigDecimal("11.764705882353").compareTo(btc.sharePercent!!))
            assertEquals(0, BigDecimal.ZERO.compareTo(btc.realizedPnlFiat))
            // 账户级指标 = PortfolioCalculator 单一真源（本层不重算）
            assertEquals(0, BigDecimal("1020").compareTo(snapshot.metrics.netValueFiat))
            assertEquals(0, BigDecimal("900").compareTo(snapshot.metrics.availableCashFiat))
            assertEquals(0, BigDecimal("1000").compareTo(snapshot.metrics.cumulativeDepositsFiat))
            assertEquals(0, BigDecimal("20").compareTo(snapshot.metrics.unrealizedPnlFiat))
            assertEquals(NOW, snapshot.priceAsOf, "现价快照时刻 = 最近一次成功刷新")
            assertTrue(!snapshot.estimated)
        }
    }

    @Test
    fun unpricedCoinsGoLastOrderedByNameAndStayOutOfNetValue() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            env.fundService.saveFund(deposit("3000"))
            env.service.saveTransaction(buy("ETH", price = "2000", quantity = "1"))
            env.service.saveTransaction(buy("BNB", price = "300", quantity = "0.5"))
            env.putSnapshot(env.coin("USDT")!!.id, "USD", "1", NOW)
            val snapshot = portfolioService(env).snapshot()
            // 缺价币置末按名称升序：BNB < Ethereum
            assertEquals(listOf("tether", "binancecoin", "ethereum"), snapshot.rows.map { it.cgId })
            val eth = snapshot.rows.first { it.cgId == "ethereum" }
            assertNull(eth.priceFiat)
            assertNull(eth.marketValueFiat)
            assertNull(eth.floatPnlFiat)
            assertNull(eth.sharePercent, "缺价币无市值 → 无占比")
            assertTrue(!eth.priced, "无行情标记")
            assertEquals(0, BigDecimal("1").compareTo(eth.quantity))
            // 缺价币不计入净值：USDT = 3000 − 2000 − 150
            assertEquals(0, BigDecimal("850").compareTo(snapshot.metrics.netValueFiat))
            assertEquals(setOf("binancecoin", "ethereum"), snapshot.metrics.missingPricedCoins.toSet())
        }
    }

    @Test
    fun fullyExitedCoinKeepsZeroQuantityRow() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            env.fundService.saveFund(deposit("1000"))
            env.service.saveTransaction(buy("BTC", price = "50000", quantity = "0.01"))
            env.service.saveTransaction(sell("BTC", price = "60000", quantity = "0.01"))
            env.putSnapshot(env.coin("BTC")!!.id, "USD", "60000", NOW)
            env.putSnapshot(env.coin("USDT")!!.id, "USD", "1", NOW)
            val snapshot = portfolioService(env).snapshot()
            // 清仓币仍出行（资产列表展示 0 持仓），有现价 → 市值 0、占比 0
            assertEquals(listOf("tether", "bitcoin"), snapshot.rows.map { it.cgId })
            val btc = snapshot.rows.first { it.cgId == "bitcoin" }
            assertEquals(0, BigDecimal.ZERO.compareTo(btc.quantity))
            assertTrue(btc.priced)
            assertEquals(0, BigDecimal.ZERO.compareTo(btc.marketValueFiat!!))
            assertEquals(0, BigDecimal.ZERO.compareTo(btc.sharePercent!!))
            assertNull(btc.avgCostFiat, "无持仓 → 均价 null")
            assertEquals(0, BigDecimal("100").compareTo(btc.realizedPnlFiat), "(60000−50000)×0.01")
        }
    }

    @Test
    fun change24hPairsHourBucketAndMarksMixedSource() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            env.fundService.saveFund(deposit("3000"))
            env.service.saveTransaction(buy("BTC", price = "50000", quantity = "0.01"))
            env.service.saveTransaction(buy("ETH", price = "2000", quantity = "0.5"))
            val btcId = env.coin("BTC")!!.id
            val usdtId = env.coin("USDT")!!.id
            env.putSnapshot(btcId, "USD", "60000", NOW)
            env.putSnapshot(usdtId, "USD", "1", NOW)
            // BTC 的 24h 前价来自 CMC（异源配对）；ETH 无现价 → 不计入 total
            env.snapshots.upsert(
                SnapshotWrite(btcId.toInt(), "USD", BigDecimal("50000"), PriceSource.COINMARKETCAP, NOW.minus(DAY)),
            )
            val service = portfolioService(env)
            val partial = service.snapshot().change24h
            assertEquals(1, partial.covered, "仅 BTC 有 24h 前价")
            assertEquals(2, partial.total, "有现价的持仓 = BTC + USDT（ETH 无行情）")
            assertEquals(0, BigDecimal("100").compareTo(partial.pnlFiat!!), "0.01×(60000−50000)")
            assertEquals(0, BigDecimal("20").compareTo(partial.pct!!))
            assertTrue(partial.mixedSource, "现价 CG / 24h 前价 CMC → 混合数据源")
            // 补齐 USDT 的 24h 前价（同源）→ 覆盖 2/2；混合标注仍由 BTC 配对承担
            env.putSnapshot(usdtId, "USD", "1", NOW.minus(DAY))
            val full = service.snapshot().change24h
            assertEquals(2, full.covered)
            assertEquals(2, full.total)
            assertEquals(0, BigDecimal("100").compareTo(full.pnlFiat!!))
            assertTrue(full.mixedSource)
        }
    }

    @Test
    fun anomalousHoldingsAreSortedAndFlagged() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            // 直插交易所卖出（无买入 → 宽松重放负持仓；聚合页按「持仓异常」展示，不阻断）
            // 逆序直插三个币，断言输出为 cg_id 排序（重放结果来自 HashMap，输出必须确定）
            env.seedExchangeTrade("ETH", Side.SELL, "2000", "2", NOW.minusSeconds(7200))
            env.seedExchangeTrade("BTC", Side.SELL, "50000", "1", NOW.minusSeconds(5400))
            env.seedExchangeTrade("BNB", Side.SELL, "300", "5", NOW.minusSeconds(3600))
            val snapshot = portfolioService(env).snapshot()
            assertEquals(
                listOf("binancecoin", "bitcoin", "ethereum"),
                snapshot.anomalousCoins,
                "异常币 cg_id 排序输出",
            )
            assertEquals(3, snapshot.rows.count { it.anomalous })
            assertTrue(snapshot.rows.all { it.sources.contains(RecordSource.Exchange("BINANCE")) })
            assertTrue(snapshot.rows.all { it.sourceClassification is SourceClassification.SingleExchange })
            assertTrue(snapshot.rows.all { it.calibratable })
        }
    }

    /**
     * 现价兜底链（与资金页 fundsOverview 同源）：全新账户尚未刷新过行情时，USD 锚定稳定币按 1:1 计现价，
     * 净值 = 可用现金 = 入金金额（不再出现「仪表盘 0 / 资金页 1000」的不一致）；兜底价无快照行 →
     * priceAsOf 保持 null。
     */
    @Test
    fun usdAnchoredStablecoinWithoutAnySnapshotCountsAsCash() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            env.fundService.saveFund(deposit("1000"))
            val snapshot = portfolioService(env).snapshot()
            val usdt = snapshot.rows.single()
            assertEquals("tether", usdt.cgId)
            assertTrue(usdt.priced, "USD 锚定兜底 → 有现价（不标「无行情」）")
            assertEquals(0, BigDecimal.ONE.compareTo(usdt.priceFiat!!))
            assertEquals(0, BigDecimal("1000").compareTo(usdt.marketValueFiat!!))
            assertEquals(0, BigDecimal("1000").compareTo(snapshot.metrics.netValueFiat))
            assertEquals(0, BigDecimal("1000").compareTo(snapshot.metrics.availableCashFiat))
            assertTrue(snapshot.metrics.missingPricedCoins.isEmpty(), "兜底有价 → 不计入缺价币")
            assertNull(snapshot.priceAsOf, "兜底价无快照行 → 不臆造现价时刻")
            // 与资金页同口径：可用现金一致（首次行情刷新前两页不再互相矛盾）
            val overview = env.fundService.listFunds(FundFilter()).overview!!
            assertEquals(0, overview.availableCashFiat.compareTo(snapshot.metrics.availableCashFiat))
            assertEquals(0, overview.investedNetFiat.compareTo(snapshot.metrics.investedNetFiat))
        }
    }

    /** 快照价优先于 USD 锚定 1:1；priceAsOf = 实际使用的快照 recordedAt。 */
    @Test
    fun snapshotPriceWinsOverUsdAnchorAndDrivesPriceAsOf() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            env.fundService.saveFund(deposit("1000", at = NOW.minusSeconds(7200)))
            env.putSnapshot(env.coin("USDT")!!.id, "USD", "0.98", NOW)
            val snapshot = portfolioService(env).snapshot()
            val usdt = snapshot.rows.single()
            assertEquals(0, BigDecimal("0.98").compareTo(usdt.priceFiat!!), "快照价优先于锚定 1:1")
            assertEquals(0, BigDecimal("980").compareTo(usdt.marketValueFiat!!))
            assertEquals(0, BigDecimal("980").compareTo(snapshot.metrics.netValueFiat))
            assertEquals(NOW, snapshot.priceAsOf, "priceAsOf = 实际使用的快照时刻")
        }
    }

    @Test
    fun pendingConversionMarksSnapshotAndRowEstimated() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            // 无任何行情价：资金事件名义零折算 + 估算（黄金用例 9 的聚合页投影）
            env.fundService.saveFund(deposit("1", coin = "BTC"))
            val snapshot = portfolioService(env).snapshot()
            assertTrue(snapshot.estimated)
            val btc = snapshot.rows.single { it.cgId == "bitcoin" }
            assertTrue(btc.estimated)
            assertEquals(0, BigDecimal("1").compareTo(btc.quantity))
            assertTrue(!btc.priced)
            assertNull(btc.marketValueFiat)
            assertEquals(0, BigDecimal.ZERO.compareTo(snapshot.metrics.netValueFiat))
        }
    }

    @Test
    fun coinDetailReusesSnapshotRowAndListsCalibrationsDescending() = runBlocking {
        LedgerTestEnv().use { env ->
            val session = env.login()
            env.seedExchangeTrade("BTC", Side.BUY, "50000", "1", NOW.minusSeconds(7200))
            env.putSnapshot(env.coin("BTC")!!.id, "USD", "60000", NOW)
            val btc = env.coin("BTC")!!
            val firstId = env.recons.insert(recon(session.account.id, btc.id, local = "1.0", exchangeQty = "1.2"))
            val secondId = env.recons.insert(recon(session.account.id, btc.id, local = "1.2", exchangeQty = "1.0"))
            val service = portfolioService(env)
            val row = service.snapshot().rows.first { it.cgId == "bitcoin" }
            val detail = service.coinDetail("bitcoin")
            assertEquals("bitcoin", detail.cgId)
            assertEquals("BTC", detail.symbol)
            assertEquals("Bitcoin", detail.name)
            assertEquals(row, detail.row, "详情行复用快照同一次计算口径")
            assertEquals(listOf(secondId, firstId), detail.calibrations.map { it.id }, "校准历史时间降序")
            val latest = detail.calibrations.first()
            assertEquals("BINANCE", latest.exchange)
            assertNull(latest.notes, "reconciliation_records 无 notes 列 → null")
            assertEquals(0, BigDecimal("-0.2").compareTo(latest.delta))
            assertEquals(0, BigDecimal("12000").compareTo(latest.deltaFiat), "差额折算按记录值固定")
            assertTrue(detail.calibratable, "单一交易所来源 → 校准入口可见")
        }
    }

    @Test
    fun coinDetailForUnknownCgIdReturnsMinimalStructure() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val detail = portfolioService(env).coinDetail("not-a-coin")
            assertEquals("not-a-coin", detail.cgId)
            assertEquals("not-a-coin", detail.symbol)
            assertEquals("not-a-coin", detail.name)
            assertNull(detail.row)
            assertTrue(detail.calibrations.isEmpty())
            assertTrue(!detail.calibratable)
        }
    }

    @Test
    fun coinDetailForCatalogCoinWithoutLedgerEntriesHasNoRow() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val detail = portfolioService(env).coinDetail("ethereum")
            assertEquals("ETH", detail.symbol)
            assertEquals("Ethereum", detail.name)
            assertNull(detail.row, "目录收录但无任何账本事件 → 无持仓行")
            assertTrue(!detail.calibratable)
        }
    }

    @Test
    fun multiSourceCoinHidesCalibrationEntry() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            env.fundService.saveFund(deposit("1000"))
            env.service.saveTransaction(buy("BTC", price = "50000", quantity = "0.001"))
            env.seedExchangeTrade("BTC", Side.BUY, "50000", "0.5", NOW.minusSeconds(900))
            val detail = portfolioService(env).coinDetail("bitcoin")
            assertEquals(2, detail.row!!.sources.size, "手动 + 交易所 API → 多来源")
            assertTrue(detail.row!!.sourceClassification is SourceClassification.Multi)
            assertTrue(!detail.calibratable, "多来源 → 校准入口隐藏（PRD 故事 4.1-5）")
        }
    }

    @Test
    fun baseFiatSettingDrivesQuoteLookup() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            env.settings.putGlobal(DefaultTransactionLedgerService.SETTING_FIAT, "EUR")
            env.putSnapshot(env.coin("USDT")!!.id, "EUR", "0.9", NOW.minusSeconds(7200))
            env.fundService.saveFund(deposit("100"))
            val snapshot = portfolioService(env).snapshot()
            assertEquals("EUR", snapshot.fiat)
            val row = snapshot.rows.single()
            assertEquals(0, BigDecimal("0.9").compareTo(row.priceFiat!!))
            assertEquals(0, BigDecimal("90").compareTo(row.marketValueFiat!!))
            assertEquals(0, BigDecimal("90").compareTo(snapshot.metrics.netValueFiat))
            assertTrue(!row.estimated, "记录时刻已有 EUR 快照 → 非估算")
        }
    }

    @Test
    fun cashWhitelistProviderDrivesAvailableCash() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            env.fundService.saveFund(deposit("1000"))
            env.putSnapshot(env.coin("USDT")!!.id, "USD", "1", NOW)
            val withoutCash = portfolioService(env, cashCoinIds = { emptySet() }).snapshot()
            assertEquals(0, BigDecimal.ZERO.compareTo(withoutCash.metrics.availableCashFiat))
            assertEquals(0, BigDecimal("1000").compareTo(withoutCash.metrics.netValueFiat))
            val withCash = portfolioService(env).snapshot()
            assertEquals(0, BigDecimal("1000").compareTo(withCash.metrics.availableCashFiat))
        }
    }

    @Test
    fun snapshotIsScopedToActiveAccount() = runBlocking {
        LedgerTestEnv().use { env ->
            val first = env.login("alice")
            env.fundService.saveFund(deposit("1000"))
            env.putSnapshot(env.coin("USDT")!!.id, "USD", "1", NOW)
            val service = portfolioService(env)
            assertEquals(1, service.snapshot().rows.size)
            env.login("bob")
            assertTrue(service.snapshot().rows.isEmpty(), "账户隔离：新账户不见他人账本")
            env.sessions.set(first)
            assertEquals(1, service.snapshot().rows.size, "切回原账户恢复")
        }
    }

    // ---- 测试夹具 ----

    private fun portfolioService(
        env: LedgerTestEnv,
        now: Instant = NOW,
        cashCoinIds: () -> Set<String> = { PortfolioCalculator.DEFAULT_CASH_COIN_IDS },
    ): DefaultPortfolioService = DefaultPortfolioService(
        sessions = env.sessions,
        transactions = env.repository,
        funds = env.funds,
        recons = env.recons,
        catalog = env.catalog,
        snapshots = env.snapshots,
        settings = env.settings,
        assembler = env.assembler,
        eventBuilder = env.eventBuilder,
        cashCoinIds = cashCoinIds,
        clock = { now },
    )

    private fun deposit(
        quantity: String,
        coin: String = "USDT",
        at: Instant = NOW.minusSeconds(3600),
    ): FundInput = FundInput(
        kind = FlowKind.DEPOSIT,
        coinSymbol = coin,
        quantity = BigDecimal(quantity),
        time = at,
    )

    private fun buy(
        symbol: String,
        price: String,
        quantity: String,
        at: Instant = NOW.minusSeconds(1800),
    ): TransactionInput = trade(symbol, Side.BUY, price, quantity, at)

    private fun sell(
        symbol: String,
        price: String,
        quantity: String,
        at: Instant = NOW.minusSeconds(900),
    ): TransactionInput = trade(symbol, Side.SELL, price, quantity, at)

    private fun trade(
        symbol: String,
        side: Side,
        price: String,
        quantity: String,
        at: Instant,
    ): TransactionInput = TransactionInput(
        exchange = "BINANCE",
        baseSymbol = symbol,
        quoteSymbol = "USDT",
        side = side,
        price = BigDecimal(price),
        quantity = BigDecimal(quantity),
        fee = BigDecimal.ZERO,
        feeCoinSymbol = "USDT",
        time = at,
    )

    /** 校准记录直插夹具（差额账务字段一一对应；delta = 交易所余额 − 本地持仓）。 */
    private fun recon(
        accountId: Int,
        coinId: Long,
        local: String,
        exchangeQty: String,
    ): NewReconciliationRow = NewReconciliationRow(
        accountId = accountId,
        symbol = "BTC",
        coinId = coinId.toInt(),
        exchange = "BINANCE",
        localQuantity = BigDecimal(local),
        exchangeQuantity = BigDecimal(exchangeQty),
        delta = BigDecimal(exchangeQty) - BigDecimal(local),
        baseAmount = BigDecimal("12000"),
    )

    private companion object {
        /** 固定「现在」（测试时钟）：24h 配对桶 = 2026-09-09T12:00Z。 */
        val NOW: Instant = Instant.parse("2026-09-10T12:30:00Z")

        val DAY: Duration = Duration.ofHours(24)
    }
    @Test
    fun `coins with a negative history are flagged as unreliable cost basis`() = runBlocking {
        // 场景（2026-09-11 走查实测形态）：先卖出从未买入的币 → 负持仓边界；再补买使其回正。
        // 期末持仓为正，但成本基数不可信 —— costReliable 必须为 false（比 anomalous 更宽的口径）。
        LedgerTestEnv().use { env ->
            env.login()
            env.fundService.saveFund(deposit("10000"))
            // 负边界只能由导入路径（LENIENT）造成：交易所同步入账一笔从未买入的卖出
            env.seedExchangeTrade("BTC", Side.SELL, "60000", "0.1", NOW.minusSeconds(1800))
            env.service.saveTransaction(buy("BTC", price = "50000", quantity = "0.2", at = NOW.minusSeconds(900)))
            env.putSnapshot(env.coin("BTC")!!.id, "USD", "60000", NOW)
            env.putSnapshot(env.coin("USDT")!!.id, "USD", "1", NOW)

            val btc = portfolioService(env).snapshot().rows.first { it.cgId == "bitcoin" }
            assertTrue(btc.quantity.signum() > 0, "期末持仓应为正")
            assertTrue(!btc.anomalous, "期末非负 → 不是 anomalous（旧口径不会报警）")
            assertTrue(!btc.costReliable, "中途出现过负持仓 → 成本基数必须标记不可靠")
        }
    }
}
