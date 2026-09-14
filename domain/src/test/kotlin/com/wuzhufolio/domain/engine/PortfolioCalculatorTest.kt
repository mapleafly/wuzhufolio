package com.wuzhufolio.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PortfolioCalculator 测试（T4.2 验收）：净值/可用现金/投入本金/总收益/ROI/已实现盈亏；
 * 累计增资 = 0 → 总收益与 ROI 为 null（"--"）。
 */
class PortfolioCalculatorTest {

    private fun usdtDeposit(qty: String, fiat: String = qty) = Ev.deposit(1, Ev.USDT, qty, fiat)

    @Test
    fun `net value equals sum of priced holdings`() {
        val outcome = ReplayEngine.replay(
            listOf(
                usdtDeposit("1000"),
                Ev.deposit(2, Ev.BTC, "1", "50000"),
            ),
        )
        val metrics = PortfolioCalculator().compute(outcome, mapOf(Ev.USDT to "1".bd(), Ev.BTC to "60000".bd()))
        assertMoney("61000", metrics.netValueFiat)
        assertMoney("1000", metrics.availableCashFiat, "白名单币种计入可用现金")
    }

    @Test
    fun `cash whitelist is injectable`() {
        val outcome = ReplayEngine.replay(listOf(Ev.deposit(1, Ev.BTC, "1", "50000")))
        val noCash = PortfolioCalculator(emptySet()).compute(outcome, mapOf(Ev.BTC to "60000".bd()))
        assertMoney("0", noCash.availableCashFiat)
        val withBtc = PortfolioCalculator(setOf(Ev.BTC)).compute(outcome, mapOf(Ev.BTC to "60000".bd()))
        assertMoney("60000", withBtc.availableCashFiat)
    }

    @Test
    fun `default cash whitelist is usdt only and anchor set is fixed to usdt`() {
        // D28（2026-09-13 人工拍板）：现金白名单默认仅 USDT（其余稳定币用户可增删，按市价折算）；
        // 1:1 锚定集合固定 = USDT（不可通过设置扩展）
        assertEquals(setOf("tether"), PortfolioCalculator.DEFAULT_CASH_COIN_IDS)
        assertEquals(setOf("tether"), PortfolioCalculator.ANCHORED_COIN_IDS)
    }

    /** D28 边界：白名单**扩展项**计入「可用现金」口径（现金分类），但按市价折算（不锚定）。 */
    @Test
    fun `cash whitelist extension counts as cash but is valued at market price`() {
        val outcome = ReplayEngine.replay(
            listOf(
                Ev.deposit(1, Ev.USDT, "1000", "1000"),
                Ev.deposit(2, "usd-coin", "500", "500"),
            ),
        )
        val metrics = PortfolioCalculator(cashCoinIds = setOf("tether", "usd-coin")).compute(
            outcome,
            mapOf(Ev.USDT to "1".bd(), "usd-coin" to "0.98".bd()),
        )
        assertMoney("1490", metrics.netValueFiat, "1000×1 + 500×0.98")
        assertMoney("1490", metrics.availableCashFiat, "扩展项计入现金口径（按市价）")
    }

    @Test
    fun `missing priced coins are excluded from net value and listed`() {
        val outcome = ReplayEngine.replay(
            listOf(
                usdtDeposit("1000"),
                Ev.deposit(2, Ev.BTC, "1", "50000"),
                Ev.deposit(2, "unknown-coin", "5", "100"),
            ),
        )
        val metrics = PortfolioCalculator().compute(outcome, mapOf(Ev.USDT to "1".bd(), Ev.BTC to "60000".bd()))
        assertMoney("61000", metrics.netValueFiat, "缺价币不计入总资产市值（无行情）")
        assertEquals(listOf("unknown-coin"), metrics.missingPricedCoins)
        val missing = metrics.holdings.getValue("unknown-coin")
        assertNull(missing.marketValue)
        assertNull(missing.floatPnlFiat)
        assertFalse(missing.priced)
    }

    @Test
    fun `no deposits means total return and roi are null for dash display`() {
        // API 导入路径：无任何手动增资（宽松重放）
        val outcome = ReplayEngine.replay(
            listOf(Ev.buy(1, Ev.BTC, Ev.USDT, price = "50000", qty = "1", source = RecordSource.Exchange("BINANCE"))),
            NegativePolicy.LENIENT,
        )
        val metrics = PortfolioCalculator().compute(outcome, mapOf(Ev.USDT to "1".bd(), Ev.BTC to "55000".bd()))
        assertNull(metrics.totalReturnFiat)
        assertNull(metrics.roiPercent)
        assertMoney("0", metrics.investedNetFiat)
        assertMoney("0", metrics.cumulativeDepositsFiat)
    }

    @Test
    fun `roi uses cumulative deposits as denominator regardless of withdrawals`() {
        val outcome = ReplayEngine.replay(
            listOf(
                Ev.deposit(1, Ev.USDT, "100", "100"),
                Ev.buy(2, Ev.BTC, Ev.USDT, price = "100", qty = "1"),
                Ev.withdraw(3, Ev.BTC, "1", "150"),
                Ev.deposit(4, Ev.USDT, "100", "100"),
                Ev.buy(5, Ev.BTC, Ev.USDT, price = "100", qty = "1"),
            ),
        )
        val metrics = PortfolioCalculator().compute(outcome, mapOf(Ev.USDT to "1".bd(), Ev.BTC to "100".bd()))
        assertMoney("200", metrics.cumulativeDepositsFiat)
        assertMoney("150", metrics.cumulativeWithdrawalsFiat)
        assertMoney("50", metrics.investedNetFiat, "投入本金（净）")
        assertMoney("100", metrics.netValueFiat)
        assertMoney("50", assertNotNull(metrics.totalReturnFiat))
        assertMoney("25", assertNotNull(metrics.roiPercent))
    }

    @Test
    fun `holding metrics expose float pnl and percentages`() {
        val outcome = ReplayEngine.replay(
            listOf(
                Ev.deposit(1, Ev.USDT, "50050", "50050"),
                Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", fee = "50"),
            ),
        )
        val metrics = PortfolioCalculator().compute(outcome, mapOf(Ev.USDT to "1".bd(), Ev.BTC to "60000".bd()))
        val btc = metrics.holdings.getValue(Ev.BTC)
        assertMoney("60000", assertNotNull(btc.marketValue))
        assertMoney("9950", assertNotNull(btc.floatPnlFiat))
        // 9950 / 50050 ≈ 19.880119880120%（12 位 HALF_UP）
        assertMoney("19.880119880120", assertNotNull(btc.floatPnlPercent))
        assertMoney("0", btc.realizedPnlFiat)
        assertMoney("9950", metrics.unrealizedPnlFiat)
        assertMoney("50050", metrics.totalCostFiat)
        assertFalse(metrics.estimated)
        assertTrue(metrics.holdings.values.all { !it.estimated })
    }

    @Test
    fun `float pnl percent is null when cost basis is zero`() {
        // 清仓后 0 数量 0 成本：浮动盈亏率无基数 → null（展示 --）
        val outcome = ReplayEngine.replay(
            listOf(Ev.deposit(1, Ev.USDT, "100", "100"), Ev.withdraw(2, Ev.USDT, "100", "100")),
        )
        val metrics = PortfolioCalculator().compute(outcome, mapOf(Ev.USDT to "1".bd()))
        val usdt = metrics.holdings.getValue(Ev.USDT)
        assertMoney("0", usdt.costFiat)
        assertNull(usdt.floatPnlPercent)
        assertNull(usdt.avgCostFiat)
    }

    @Test
    fun `anomalous negative holding is excluded from net value and reported separately`() {
        val outcome = ReplayEngine.replay(
            listOf(Ev.sell(1, Ev.BTC, Ev.USDT, price = "50000", qty = "1", source = RecordSource.Csv)),
            NegativePolicy.LENIENT,
        )
        // 卖价 50,000 入账 +50,000 USDT、BTC −1；现价 BTC 60,000
        // D29（2026-09-13 人工拍板方案 B）：负持仓市值**不计入净值**（数据缺口信号，非真实头寸），
        // 合计值单列 anomalousExcludedFiat 供界面提示 → 净值 = 50,000（现金），排除 −60,000
        val metrics = PortfolioCalculator().compute(outcome, mapOf(Ev.BTC to "60000".bd(), Ev.USDT to "1".bd()))
        assertMoney("50000", metrics.netValueFiat, "负持仓市值不计入净值")
        assertMoney("-60000", metrics.anomalousExcludedFiat, "被排除的负市值单列（供界面提示）")
        val btc = metrics.holdings.getValue(Ev.BTC)
        assertTrue(btc.anomalous, "币种行仍标记「持仓异常」")
        assertMoney("-60000", assertNotNull(btc.marketValue), "行内仍展示负市值（透明可见）")
        assertMoney("-60000", assertNotNull(btc.floatPnlFiat), "0 成本基数的负持仓浮亏按全额市值计")
        assertNull(btc.floatPnlPercent)
        assertTrue(metrics.missingPricedCoins.isEmpty())
    }

    /** D29 边界：负持仓的**可用现金**同样不计入（否则现金口径与净值口径分裂）。 */
    @Test
    fun `anomalous negative cash coin is excluded from available cash too`() {
        val outcome = ReplayEngine.replay(
            listOf(
                Ev.deposit(1, Ev.USDT, "1000", "1000"),
                // 导入路径：无对应入金的稳定币卖出 → usd-coin 负持仓
                Ev.sell(2, "usd-coin", Ev.USDT, price = "1", qty = "400", source = RecordSource.Csv),
            ),
            NegativePolicy.LENIENT,
        )
        val metrics = PortfolioCalculator(cashCoinIds = setOf("tether", "usd-coin")).compute(
            outcome,
            mapOf(Ev.USDT to "1".bd(), "usd-coin" to "1".bd()),
        )
        assertMoney("1400", metrics.netValueFiat, "现金 1,400；usd-coin −400 不计入")
        assertMoney("1400", metrics.availableCashFiat, "负持仓币不计入可用现金")
        assertMoney("-400", metrics.anomalousExcludedFiat)
    }

    @Test
    fun `realized aggregates across coins from sell events`() {
        val outcome = ReplayEngine.replay(
            listOf(
                Ev.deposit(1, Ev.USDT, "200000", "200000"),
                Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1"),
                Ev.sell(3, Ev.BTC, Ev.USDT, price = "60000", qty = "1"),
                Ev.deposit(4, "ethereum", "10", "3000"),
                Ev.sell(5, "ethereum", Ev.USDT, price = "500", qty = "2"),
            ),
        )
        val metrics = PortfolioCalculator().compute(
            outcome,
            mapOf(Ev.USDT to "1".bd(), Ev.BTC to "1".bd(), "ethereum" to "1".bd()),
        )
        assertMoney("10000", assertNotNull(outcome.holdings.getValue(Ev.BTC).realizedPnlFiat))
        // ETH：买 10 @300（均价 300 经存款折算）→ 卖 2@500 = 1000 − 2×300
        assertMoney("400", assertNotNull(outcome.holdings.getValue("ethereum").realizedPnlFiat))
        assertMoney("10400", metrics.realizedPnlFiat)
    }
}
