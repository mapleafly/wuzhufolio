package com.wuzhufolio.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PRD 附录 A 黄金用例 1–12 引擎验收（task-breakdown M4 里程碑门槛：**黄金用例 1–9、12 通过**）。
 * 用例口述数值 = 引擎重放 + 组合指标的直接断言（基础法币 USD、USDT≈1:1 口径同附录说明）。
 */
class GoldenCasesTest {

    private val prices = mapOf(Ev.USDT to "1".bd(), Ev.USDC to "1".bd())

    // ---- 黄金用例 1：建仓与已实现盈亏 ----

    @Test
    fun `golden 1 - buy then sell realizes 4915 and keeps avg cost 50050`() {
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "100000", "100000"),
            Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", fee = "50"),
            Ev.sell(3, Ev.BTC, Ev.USDT, price = "60000", qty = "0.5", fee = "60"),
        )
        val outcome = ReplayEngine.replay(events)

        val btc = outcome.holdings.getValue(Ev.BTC)
        assertMoney("0.5", btc.quantity, "btc quantity")
        assertMoney("25025", btc.costFiat, "btc total cost")
        assertMoney("50050", assertNotNull(btc.avgCostFiat), "btc avg cost")
        assertMoney("4915", btc.realizedPnlFiat, "btc realized")
        assertFalse(btc.anomalous)

        val usdt = outcome.holdings.getValue(Ev.USDT)
        assertMoney("79890", usdt.quantity, "usdt quantity") // 100000 − 50050 + 29940
        assertMoney("4915", outcome.realizedPnlFiat, "portfolio realized")
        assertMoney("100000", outcome.cumulativeDepositsFiat)
        assertMoney("0", outcome.cumulativeWithdrawalsFiat)
        assertTrue(outcome.violations.isEmpty())
    }

    // ---- 黄金用例 2：增资与撤资不产生盈亏 ----

    @Test
    fun `golden 2 - deposit then withdrawal produces no pnl and net principal returns to zero`() {
        val dep = Ev.deposit(1, Ev.USDT, "100", "100")
        val wd = Ev.withdraw(2, Ev.USDT, "100", "100")
        val outcome = ReplayEngine.replay(listOf(dep, wd))

        assertEquals(0, outcome.realizedPnlFiat.compareTo("0".bd()))
        assertMoney("100", outcome.cumulativeDepositsFiat)
        assertMoney("100", outcome.cumulativeWithdrawalsFiat)
        assertMoney("0", outcome.investedNetFiat)

        val metrics = PortfolioCalculator().compute(outcome, prices)
        assertMoney("0", metrics.netValueFiat)
        assertMoney("0", metrics.availableCashFiat)
        assertMoney("0", metrics.totalReturnFiat!!)
        assertMoney("0", metrics.roiPercent!!)
        assertMoney("0", metrics.realizedPnlFiat)
    }

    // ---- 黄金用例 3：撤资后 ROI（总收益口径） ----

    @Test
    fun `golden 3 - profitable withdrawal leaves return 50 and roi 50 percent`() {
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "100", "100"),
            Ev.buy(2, Ev.BTC, Ev.USDT, price = "100", qty = "1", fee = "0"), // 100 USDT 等值 BTC
            // BTC 涨至 150（市价），撤资 150（把 1 BTC 按当时市价 150 转出）
            Ev.withdraw(3, Ev.BTC, "1", "150"),
        )
        val outcome = ReplayEngine.replay(events)
        assertMoney("100", outcome.cumulativeDepositsFiat)
        assertMoney("150", outcome.cumulativeWithdrawalsFiat)
        assertEquals(0, outcome.realizedPnlFiat.compareTo("0".bd()), "资金操作不产生已实现盈亏")

        val metrics = PortfolioCalculator().compute(outcome, mapOf(Ev.USDT to "1".bd(), Ev.BTC to "150".bd()))
        assertMoney("0", metrics.netValueFiat)
        assertMoney("50", assertNotNull(metrics.totalReturnFiat))
        assertMoney("50", assertNotNull(metrics.roiPercent))
    }

    // ---- 黄金用例 4：盈利撤资再增资 ----

    @Test
    fun `golden 4 - deposit again after profitable withdrawal keeps return 50 and roi 25 percent`() {
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "100", "100"),
            Ev.buy(2, Ev.BTC, Ev.USDT, price = "100", qty = "1"),
            Ev.withdraw(3, Ev.BTC, "1", "150"), // 盈利后撤资 150 → 净值 0、总收益 50
            Ev.deposit(4, Ev.USDT, "100", "100"),
            Ev.buy(5, Ev.BTC, Ev.USDT, price = "100", qty = "1"), // 再增资 100 买入
        )
        val outcome = ReplayEngine.replay(events)
        assertMoney("200", outcome.cumulativeDepositsFiat)
        assertMoney("150", outcome.cumulativeWithdrawalsFiat)

        val metrics = PortfolioCalculator().compute(outcome, mapOf(Ev.USDT to "1".bd(), Ev.BTC to "100".bd()))
        assertMoney("100", metrics.netValueFiat)
        assertMoney("50", assertNotNull(metrics.totalReturnFiat), "总收益 = 100 + 150 − 200")
        assertMoney("25", assertNotNull(metrics.roiPercent), "ROI = 50 / 200")
    }

    // ---- 黄金用例 5：第三币种手续费（BNB 抵扣） ----

    @Test
    fun `golden 5 - third currency fee is converted into cost and deducts bnb holdings`() {
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "60000", "60000"),
            Ev.deposit(1, Ev.BNB, "0.05", "25"), // BNB 市价 500
            Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", fee = "0.01", feeCoin = Ev.BNB, feePx = "500"),
        )
        val outcome = ReplayEngine.replay(events)

        val btc = outcome.holdings.getValue(Ev.BTC)
        assertMoney("1", btc.quantity)
        assertMoney("50005", btc.costFiat, "买入总成本含折算手续费 5")
        val bnb = outcome.holdings.getValue(Ev.BNB)
        assertMoney("0.04", bnb.quantity, "BNB 持仓联动扣减 0.01")
        assertMoney("20", bnb.costFiat, "BNB 成本按均价移出 5")
        val usdt = outcome.holdings.getValue(Ev.USDT)
        assertMoney("10000", usdt.quantity)
        assertTrue(outcome.violations.isEmpty())
    }

    // ---- 黄金用例 6：CSV 导入负持仓后补增资 ----

    @Test
    fun `golden 6 - csv import negative position is anomalous then backdated deposit clears it`() {
        val csvSell = Ev.sell(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", source = RecordSource.Csv)
        val imported = ReplayEngine.replay(listOf(csvSell), NegativePolicy.LENIENT)
        val btcBefore = imported.holdings.getValue(Ev.BTC)
        assertTrue(btcBefore.anomalous, "导入后 −1 BTC 应标记持仓异常")
        assertMoney("-1", btcBefore.quantity)
        assertTrue(imported.violations.isNotEmpty(), "宽松模式记录负边界")

        // 补录 1 BTC 增资（发生在卖出之前——回填历史），手动新增走校验：不得被既有负持仓误拦
        val deposit = Ev.deposit(1, Ev.BTC, "1", "50000")
        val verdict = ReplayEngine.validateMutation(listOf(deposit, csvSell), listOf(csvSell))
        assertTrue(verdict is MutationVerdict.Ok, "补录增资应通过校验：$verdict")

        val fixed = ReplayEngine.replay(listOf(deposit, csvSell), NegativePolicy.STRICT)
        assertMoney("0", fixed.holdings.getValue(Ev.BTC).quantity)
        assertFalse(fixed.holdings.getValue(Ev.BTC).anomalous, "持仓归 0 以上、异常标记消除")
    }

    @Test
    fun `golden 6b - deposit recorded after the csv sell is not blocked by the pre-existing dip`() {
        val csvSell = Ev.sell(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", source = RecordSource.Csv)
        val deposit = Ev.deposit(3, Ev.BTC, "1", "50000") // 增资时间在卖出之后
        val verdict = ReplayEngine.validateMutation(listOf(csvSell, deposit), listOf(csvSell))
        assertTrue(verdict is MutationVerdict.Ok, "既有宽松负边界非本操作制造，不应拦截补录增资：$verdict")
        val fixed = ReplayEngine.replay(listOf(csvSell, deposit), NegativePolicy.LENIENT)
        assertMoney("0", fixed.holdings.getValue(Ev.BTC).quantity)
        assertFalse(fixed.holdings.getValue(Ev.BTC).anomalous)
    }

    // ---- 黄金用例 7：编辑历史交易触发重放校验失败 ----

    @Test
    fun `golden 7 - shrinking the deposit is blocked because the buy would go negative`() {
        val deposit100 = Ev.deposit(1, Ev.USDT, "100", "100", id = "d1")
        val buy = Ev.buy(2, Ev.BTC, Ev.USDT, price = "100", qty = "1", id = "b1")
        val original = listOf(deposit100, buy)
        assertTrue(ReplayEngine.replay(original).violations.isEmpty())

        val deposit50 = deposit100.copy(quantity = "50".bd(), fiatValue = "50".bd())
        val verdict = ReplayEngine.validateMutation(listOf(deposit50, buy), original)
        assertTrue(verdict is MutationVerdict.Blocked, "调小增资应被阻止")
        assertEquals(Ev.USDT, verdict.violation.coinId)
        assertEquals("b1", verdict.violation.eventId)
        assertMoney("-50", verdict.violation.shortage)

        // 删除该笔增资同样被阻止（该笔增资已被后续买入消耗）
        val deleteVerdict = ReplayEngine.validateMutation(listOf(buy), original)
        assertTrue(deleteVerdict is MutationVerdict.Blocked)
    }

    // ---- 黄金用例 8：校准负差额（锚点 + 撤资语义） ----

    @Test
    fun `golden 8 - negative reconciliation keeps identity and anchor survives pre-anchor edits`() {
        val marketPx = "55000"
        val deposit = Ev.deposit(1, Ev.USDT, "60000", "60000")
        val apiBuy = Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", source = RecordSource.Exchange("BINANCE"))
        // 本地 1.0 BTC、交易所 0.8 → delta −0.2；校准时市价 55000 → 撤资计值 11000
        val anchor = Ev.anchor(3, Ev.BTC, exchangeQty = "0.8", delta = "-0.2", deltaFiat = "11000")

        val before = PortfolioCalculator().compute(
            ReplayEngine.replay(listOf(deposit, apiBuy)),
            mapOf(Ev.USDT to "1".bd(), Ev.BTC to marketPx.bd()),
        )
        val after = PortfolioCalculator().compute(
            ReplayEngine.replay(listOf(deposit, apiBuy, anchor)),
            mapOf(Ev.USDT to "1".bd(), Ev.BTC to marketPx.bd()),
        )

        val btc = after.holdings.getValue(Ev.BTC)
        assertMoney("0.8", btc.quantity, "锚点强制对齐")
        assertMoney("40000", btc.costFiat, "负差额按当时平均成本移出 0.2×50000")
        assertMoney("50000", assertNotNull(btc.avgCostFiat), "平均成本不变")
        assertMoney("11000", after.cumulativeWithdrawalsFiat)
        // 恒等式保持：校准不制造凭空盈亏（总收益前后一致）
        assertMoney(
            assertNotNull(before.totalReturnFiat).toPlainString(),
            assertNotNull(after.totalReturnFiat),
            "恒等式：总收益 = 净值 + 撤资 − 增资 校准前后保持",
        )

        // 校准前历史记录编辑/删除不回滚校准效果：删除买入 → 重放至锚点仍强制对齐 0.8，撤资计值不变
        val deleted = ReplayEngine.replay(listOf(deposit, anchor), NegativePolicy.LENIENT)
        assertMoney("0.8", deleted.holdings.getValue(Ev.BTC).quantity, "删除校准前记录不回滚校准效果")
        assertMoney("11000", deleted.cumulativeWithdrawalsFiat)
    }

    // ---- 黄金用例 9：PENDING 回填前后对比 ----

    @Test
    fun `golden 9 - pending fee price marks estimates then backfill corrects and clears markers`() {
        val deposit = Ev.deposit(1, Ev.USDT, "60000", "60000")
        val bnbDeposit = Ev.deposit(1, Ev.BNB, "0.01", "4.9")
        // 离线：BNB 无实时价 → 按最近可得价 490 估算（feeFiat = 4.9），记录标记 PENDING
        val pendingBuy = Ev.buy(
            2, Ev.BTC, Ev.USDT, price = "50000", qty = "1",
            fee = "0.01", feeCoin = Ev.BNB, feePx = "490", estimated = true,
        )
        val pending = ReplayEngine.replay(listOf(deposit, bnbDeposit, pendingBuy))
        assertMoney("50004.9", pending.holdings.getValue(Ev.BTC).costFiat)
        assertTrue(pending.holdings.getValue(Ev.BTC).estimated, "估算中标记")
        assertTrue(pending.estimated)

        // 联网回填真实价 500 → 重建事件（feeFiat = 5.0、PENDING 消除）→ 重放自动重算
        val settled = ReplayEngine.replay(
            listOf(deposit, bnbDeposit, pendingBuy.copy(feeFiat = "5".bd(), estimated = false)),
        )
        assertMoney("50005", settled.holdings.getValue(Ev.BTC).costFiat, "回填后成本重算")
        assertFalse(settled.holdings.getValue(Ev.BTC).estimated, "估算标注消除")
        assertFalse(settled.estimated)
    }

    // ---- 黄金用例 11 数值口径（24h 属 M5 计算面；此处校验引擎不为缺失价引入估算或异常，展示位在 UI） ----
    // 见模块记录 §5：24h 计算按 T5.3 归属 M5，M4 里程碑门槛不含 11。

    // ---- 黄金用例 12：法币计价交易对归一化（USD → USDT 孪生联动） ----

    @Test
    fun `golden 12 - fiat quoted pair is normalized to twin stablecoin before replay`() {
        // 归一发生在事件构造层（M3 FiatNormalizer twin USD→USDT + M7 币键解析）；
        // 引擎视角：BTC/USD 买入 == BTC/USDT 买入，quote 侧按 USDT 联动持仓与余额校验
        val normalizer = com.wuzhufolio.domain.catalog.FiatNormalizer()
        assertEquals("USDT", normalizer.twinStableSymbolOf("USD"), "M3 归一前置：USD→USDT")
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "100100", "100100"),
            // USD 计价 50,000、fee 50 USD → 归一后 quote/fee 均为 USDT（tether）
            Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", fee = "50"),
        )
        val outcome = ReplayEngine.replay(events)
        val btc = outcome.holdings.getValue(Ev.BTC)
        assertMoney("50050", btc.costFiat)
        assertMoney("1", btc.quantity)
        assertMoney("50050", assertNotNull(btc.avgCostFiat))
        val usdt = outcome.holdings.getValue(Ev.USDT)
        assertMoney("50050", "100100".bd() - usdt.quantity, "归一后按 USDT 扣减总价+手续费")
        assertTrue(outcome.violations.isEmpty(), "余额校验在归一化 quote 上进行")
    }
}
