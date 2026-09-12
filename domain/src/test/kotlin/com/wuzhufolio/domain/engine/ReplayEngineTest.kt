package com.wuzhufolio.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ReplayEngine 语义测试（T4.1 验收补充）：负持仓校验两模式、手动操作相对校验、成本口径三角色手续费、
 * 锚点正差额、来源集合、估算标记、事件排序确定性。
 */
class ReplayEngineTest {

    // ---- 负持仓校验 ----

    @Test
    fun `strict replay throws when buy spends more quote than held`() {
        val buy = Ev.buy(1, Ev.BTC, Ev.USDT, price = "50000", qty = "1", id = "buy1")
        val e = assertFailsWith<NegativePositionException> {
            ReplayEngine.replay(listOf(buy), NegativePolicy.STRICT)
        }
        assertEquals(Ev.USDT, e.coinId)
        assertEquals("buy1", e.eventId)
        assertMoney("-50000", e.shortage)
    }

    @Test
    fun `strict is the default policy`() {
        assertFailsWith<NegativePositionException> {
            ReplayEngine.replay(listOf(Ev.buy(1, Ev.BTC, Ev.USDT, price = "50000", qty = "1")))
        }
    }

    @Test
    fun `lenient replay records violation and marks holding anomalous`() {
        val outcome = ReplayEngine.replay(
            listOf(Ev.buy(1, Ev.BTC, Ev.USDT, price = "50000", qty = "1", source = RecordSource.Csv)),
            NegativePolicy.LENIENT,
        )
        assertEquals(1, outcome.violations.size)
        val btc = outcome.holdings.getValue(Ev.BTC)
        assertFalse(btc.anomalous, "买入侧为正持仓不异常")
        assertFalse(btc.hadNegativeBoundary)
        val usdt = outcome.holdings.getValue(Ev.USDT)
        assertTrue(usdt.anomalous)
        assertMoney("-50000", usdt.quantity)
        assertTrue(usdt.hadNegativeBoundary)
        assertEquals(setOf("tether"), outcome.anomalousCoins)
    }

    @Test
    fun `strict replay blocks withdrawal beyond holdings`() {
        val events = listOf(Ev.deposit(1, Ev.USDT, "100", "100"), Ev.withdraw(2, Ev.USDT, "150", "150"))
        val e = assertFailsWith<NegativePositionException> { ReplayEngine.replay(events) }
        assertEquals(Ev.USDT, e.coinId)
        assertMoney("-50", e.shortage)
    }

    @Test
    fun `strict replay blocks selling beyond holdings`() {
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "100", "100"),
            Ev.buy(2, Ev.BTC, Ev.USDT, price = "100", qty = "1"),
            Ev.sell(3, Ev.BTC, Ev.USDT, price = "100", qty = "2"),
        )
        val e = assertFailsWith<NegativePositionException> { ReplayEngine.replay(events) }
        assertEquals(Ev.BTC, e.coinId)
        assertMoney("-1", e.shortage)
    }

    @Test
    fun `strict replay blocks buy when third currency fee coin holdings are insufficient`() {
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "50000", "50000"),
            Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", fee = "0.01", feeCoin = Ev.BNB, feePx = "500"),
        )
        val e = assertFailsWith<NegativePositionException> { ReplayEngine.replay(events) }
        assertEquals(Ev.BNB, e.coinId)
        assertMoney("-0.01", e.shortage)
    }

    // ---- 成本口径：资金操作不改变均价/不产生盈亏 ----

    @Test
    fun `deposit adds cost at market value and withdrawal removes at avg cost keeping avg unchanged`() {
        val events = listOf(
            Ev.deposit(1, Ev.BTC, "2", "120000"), // 市价 60000 → 成本 120000、均价 60000
            Ev.withdraw(2, Ev.BTC, "0.5", "40000"), // 撤资 0.5（记录时市价 80000 计值，不产生盈亏）
        )
        val outcome = ReplayEngine.replay(events)
        val btc = outcome.holdings.getValue(Ev.BTC)
        assertMoney("1.5", btc.quantity)
        assertMoney("90000", btc.costFiat)
        assertMoney("60000", assertNotNull(btc.avgCostFiat), "均价不受撤资影响")
        assertMoney("0", outcome.realizedPnlFiat)
        assertMoney("120000", outcome.cumulativeDepositsFiat)
        assertMoney("40000", outcome.cumulativeWithdrawalsFiat)
    }

    // ---- 手续费三角色 ----

    @Test
    fun `base currency fee on buy reduces received quantity and includes fee value in cost`() {
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "50000", "50000"),
            Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", fee = "0.001", feeCoin = Ev.BTC, feePx = "50000"),
        )
        val btc = ReplayEngine.replay(events).holdings.getValue(Ev.BTC)
        assertMoney("0.999", btc.quantity, "实际到账数量减少")
        assertMoney("50050", btc.costFiat, "买入成本 = 总价 + 折算手续费")
    }

    @Test
    fun `base currency fee on sell is netted from proceeds for realized pnl`() {
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "100050", "100050"),
            Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", fee = "50"),
            Ev.sell(3, Ev.BTC, Ev.USDT, price = "60000", qty = "1", fee = "0.01", feeCoin = Ev.BTC, feePx = "60000"),
        )
        val outcome = ReplayEngine.replay(events)
        val btc = outcome.holdings.getValue(Ev.BTC)
        assertMoney("0", btc.quantity)
        // 净收入 60000 − 600（0.01×60000）− 数量×当时均价 50050
        assertMoney("9350", btc.realizedPnlFiat)
        val usdt = outcome.holdings.getValue(Ev.USDT)
        assertMoney("110000", usdt.quantity, "卖出以总价计入计价侧（手续费在 BTC 侧扣除）")
    }

    @Test
    fun `third currency fee on sell deducts fee coin holding and fees net from realized`() {
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "100050", "100050"),
            Ev.deposit(1, Ev.BNB, "0.05", "25"),
            Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", fee = "50"),
            Ev.sell(3, Ev.BTC, Ev.USDT, price = "60000", qty = "1", fee = "0.01", feeCoin = Ev.BNB, feePx = "500"),
        )
        val outcome = ReplayEngine.replay(events)
        val btc = outcome.holdings.getValue(Ev.BTC)
        assertMoney("0", btc.quantity)
        assertMoney("9945", btc.realizedPnlFiat) // 60000 − 5 − 50050
        assertMoney("0.04", outcome.holdings.getValue(Ev.BNB).quantity, "BNB 持仓联动扣减")
        val usdt = outcome.holdings.getValue(Ev.USDT)
        assertMoney("110000", usdt.quantity)
    }

    @Test
    fun `quote currency fee on sell reduces proceeds units and cash cost accordingly`() {
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "100000", "100000"),
            Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", fee = "50"),
            Ev.sell(3, Ev.BTC, Ev.USDT, price = "60000", qty = "1", fee = "60"),
        )
        val outcome = ReplayEngine.replay(events)
        val usdt = outcome.holdings.getValue(Ev.USDT)
        assertMoney("109890", usdt.quantity, "49,950 + 净到账 59,940")
        assertMoney("109890", usdt.costFiat, "计价侧成本按净到账计入（均价保持 1）")
        assertMoney("9890", outcome.holdings.getValue(Ev.BTC).realizedPnlFiat) // 60000−60−50050
    }

    @Test
    fun `sold-out coin keeps realized pnl with zero quantity`() {
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "50050", "50050"),
            Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", fee = "50"),
            Ev.sell(3, Ev.BTC, Ev.USDT, price = "60000", qty = "1", fee = "60"),
        )
        val btc = ReplayEngine.replay(events).holdings.getValue(Ev.BTC)
        assertMoney("0", btc.quantity)
        assertMoney("9890", btc.realizedPnlFiat)
        assertNull(btc.avgCostFiat, "无持仓无均价（展示 --）")
    }

    // ---- 来源集合（单一数据来源判定输入） ----

    @Test
    fun `trade sources mark both legs and fund events do not pollute sources`() {
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "60000", "60000"), // 资金事件不携带来源
            Ev.buy(
                2, Ev.BTC, Ev.USDT, price = "50000", qty = "1",
                fee = "50", source = RecordSource.Exchange("BINANCE"),
            ),
        )
        val outcome = ReplayEngine.replay(events)
        assertEquals(setOf(RecordSource.Exchange("BINANCE")), outcome.holdings.getValue(Ev.BTC).sources)
        assertEquals(setOf(RecordSource.Exchange("BINANCE")), outcome.holdings.getValue(Ev.USDT).sources)
        // 追加手动交易 → 两腿变多来源
        val withManual = ReplayEngine.replay(
            events + Ev.sell(3, Ev.USDT, Ev.USDC, price = "1", qty = "1", source = RecordSource.Manual),
        )
        assertEquals(
            setOf(RecordSource.Exchange("BINANCE"), RecordSource.Manual),
            withManual.holdings.getValue(Ev.USDT).sources,
        )
        assertEquals(setOf(RecordSource.Manual), withManual.holdings.getValue(Ev.USDC).sources)
    }

    // ---- 估算标记 ----

    @Test
    fun `estimated event propagates to coin and outcome`() {
        val outcome = ReplayEngine.replay(
            listOf(
                Ev.deposit(1, Ev.USDT, "100", "100"),
                Ev.buy(2, Ev.BTC, Ev.USDT, price = "100", qty = "1", estimated = true),
            ),
        )
        assertTrue(outcome.estimated)
        assertTrue(outcome.holdings.getValue(Ev.BTC).estimated)
        assertTrue(outcome.holdings.getValue(Ev.USDT).estimated, "事件涉及的 quote 侧同标")
        val clean = ReplayEngine.replay(
            listOf(Ev.deposit(1, Ev.USDT, "100", "100"), Ev.buy(2, Ev.BTC, Ev.USDT, price = "100", qty = "1")),
        )
        assertFalse(clean.estimated)
    }

    // ---- 锚点正差额 ----

    @Test
    fun `positive delta anchor adds deposit and cost at market price`() {
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "60000", "60000"),
            Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", source = RecordSource.Exchange("BINANCE")),
            Ev.anchor(3, Ev.BTC, exchangeQty = "1.2", delta = "0.2", deltaFiat = "11000"), // 市价 55000
        )
        val outcome = ReplayEngine.replay(events)
        val btc = outcome.holdings.getValue(Ev.BTC)
        assertMoney("1.2", btc.quantity)
        assertMoney("61000", btc.costFiat, "正差额按校准时市价计入成本")
        assertMoney("71000", outcome.cumulativeDepositsFiat, "正差额计入累计增资（增大 ROI 分母）")
        assertMoney("0", outcome.realizedPnlFiat)
    }

    // ---- 排序确定性 ----

    @Test
    fun `events are replayed in time order regardless of input order`() {
        val buy = Ev.buy(2, Ev.BTC, Ev.USDT, price = "100", qty = "1")
        val dep = Ev.deposit(1, Ev.USDT, "100", "100")
        val a = ReplayEngine.replay(listOf(buy, dep))
        val b = ReplayEngine.replay(listOf(dep, buy))
        assertMoney("0", a.holdings.getValue(Ev.USDT).quantity)
        assertEquals(a.holdings, b.holdings)
    }

    @Test
    fun `same instant events tie break by seq then input order deterministically`() {
        val dep = Ev.deposit(1, Ev.USDT, "100", "100", id = "d")
        val buy = Ev.buy(1, Ev.BTC, Ev.USDT, price = "100", qty = "1", id = "b")
        // buy 先于 dep 处理（同刻，输入序）→ 严格模式应违例；反转输入 → 不违例
        assertFailsWith<NegativePositionException> { ReplayEngine.replay(listOf(buy, dep)) }
        ReplayEngine.replay(listOf(dep, buy)) // 同刻但增资在前：USDT 0，允许
        // seq 显式参与排序：buy(seq=0) 在 dep(seq=1) 前 → 违例
        assertFailsWith<NegativePositionException> {
            ReplayEngine.replay(
                listOf(dep.copy(seq = 1), buy.copy(seq = 0)),
            )
        }
    }

    // ---- 手动操作相对校验（新增/编辑/删除） ----

    @Test
    fun `validateMutation blocks a new trade that would create a new negative boundary`() {
        val dep = Ev.deposit(1, Ev.USDT, "100", "100")
        val newSell = Ev.sell(2, Ev.USDT, Ev.USDC, price = "1", qty = "150")
        val verdict = ReplayEngine.validateMutation(listOf(dep, newSell), listOf(dep))
        assertTrue(verdict is MutationVerdict.Blocked)
        assertEquals(Ev.USDT, verdict.violation.coinId)
    }

    @Test
    fun `validateMutation allows deleting an imported sell that caused the dip`() {
        val csvSell = Ev.sell(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", source = RecordSource.Csv)
        val verdict = ReplayEngine.validateMutation(emptyList(), listOf(csvSell))
        assertTrue(verdict is MutationVerdict.Ok, "删除宽松导入的异常记录是修复动作，应放行")
    }

    @Test
    fun `validateMutation allows unrelated operations while a dip exists earlier`() {
        val csvSell = Ev.sell(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", source = RecordSource.Csv)
        val deposit = Ev.deposit(3, Ev.BTC, "1", "50000")
        val sellLater = Ev.sell(4, Ev.BTC, Ev.USDT, price = "60000", qty = "0.5", source = RecordSource.Manual)
        // 卖出 0.5：从 -1+1=0 到 -0.5 —— 新负边界由本操作制造 → 拦截（先修复旧异常）
        val blocked = ReplayEngine.validateMutation(listOf(csvSell, deposit, sellLater), listOf(csvSell, deposit))
        assertTrue(blocked is MutationVerdict.Blocked)
    }

    @Test
    fun `worsening an already negative boundary at the same event position stays allowed permissive`() {
        // 既有负边界（导入卖出 −1）；把该卖出的数量编辑得更大（−2）：同一位点负值未新造 → 放行（宽松异常由标记承载）
        val csvSell = Ev.sell(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", source = RecordSource.Csv, id = "s1")
        val bigger = Ev.sell(2, Ev.BTC, Ev.USDT, price = "50000", qty = "2", source = RecordSource.Csv, id = "s1")
        val verdict = ReplayEngine.validateMutation(listOf(bigger), listOf(csvSell))
        assertTrue(verdict is MutationVerdict.Ok, "宽松异常位点的编辑不按新负边界拦截（M4 裁决，登记模块记录）")
        val replayed = ReplayEngine.replay(listOf(bigger), NegativePolicy.LENIENT)
        assertMoney("-2", replayed.holdings.getValue(Ev.BTC).quantity)
    }

    @Test
    fun `zero delta anchor is rejected as no-op`() {
        assertFailsWith<IllegalArgumentException> {
            Ev.anchor(1, Ev.BTC, exchangeQty = "1", delta = "0", deltaFiat = "0")
        }
    }
    // ---- D26 负持仓区间成本口径（方案甲，C2 返工验收） ----

    @Test
    fun `d26 negative position is fully repaid without building cost`() {
        // 卖出未持有的 1 BTC（宽松导入）→ −1 BTC；再补买 0.4（仍为负）→ 成本必须保持 0
        val sell = Ev.sell(1, Ev.BTC, Ev.USDT, price = "50000", qty = "1", source = RecordSource.Csv)
        val buyPart = Ev.buy(2, Ev.BTC, Ev.USDT, price = "40000", qty = "0.4", source = RecordSource.Csv)
        val holding = ReplayEngine.replay(listOf(sell, buyPart), NegativePolicy.LENIENT)
            .holdings.getValue(Ev.BTC)
        assertMoney("-0.6", holding.quantity)
        assertMoney("0", holding.costFiat, "清偿部分不建成本（不变式：数量 ≤ 0 ⟹ 成本 = 0）")
    }

    @Test
    fun `d26 crossing zero splits the inflow by quantity`() {
        // −1 BTC 空头被一笔 2.5 BTC 的买入（折算 100,000）穿越：只有 1.5 BTC 承担成本 = 100,000×1.5/2.5
        val sell = Ev.sell(1, Ev.BTC, Ev.USDT, price = "50000", qty = "1", source = RecordSource.Csv)
        val buy = Ev.buy(2, Ev.BTC, Ev.USDT, price = "40000", qty = "2.5", source = RecordSource.Csv)
        val holding = ReplayEngine.replay(listOf(sell, buy), NegativePolicy.LENIENT).holdings.getValue(Ev.BTC)
        assertMoney("1.5", holding.quantity)
        assertMoney("60000", holding.costFiat, "穿越点拆分：100,000 × (2.5 − 1) / 2.5")
        assertMoney("40000", holding.avgCostFiat!!, "均价应回到买入价（不再被空头污染）")
    }

    @Test
    fun `d26 deposit into a negative position also splits at the crossing point`() {
        val sell = Ev.sell(1, Ev.BTC, Ev.USDT, price = "50000", qty = "1", source = RecordSource.Csv)
        val deposit = Ev.deposit(2, Ev.BTC, "2", "80000")
        val holding = ReplayEngine.replay(listOf(sell, deposit), NegativePolicy.LENIENT).holdings.getValue(Ev.BTC)
        assertMoney("1", holding.quantity)
        assertMoney("40000", holding.costFiat, "80,000 × (2 − 1) / 2")
    }

    @Test
    fun `d26 anchored quantity zero forces cost to zero`() {
        val buy = Ev.buy(1, Ev.BTC, Ev.USDT, price = "40000", qty = "1", source = RecordSource.Csv)
        val deposit = Ev.deposit(2, Ev.USDT, "100000", "100000")
        // 校准把持仓对齐到 0 → 成本必须归零（不变式）
        val anchor = Ev.anchor(3, Ev.BTC, exchangeQty = "0", delta = "-1", deltaFiat = "40000")
        val holding = ReplayEngine.replay(listOf(buy, deposit, anchor), NegativePolicy.LENIENT)
            .holdings.getValue(Ev.BTC)
        assertMoney("0", holding.quantity)
        assertMoney("0", holding.costFiat, "锚点对齐到 0 → 成本归零")
    }

    @Test
    fun `d26 walkthrough scenario rebuids a clean basis after the short`() {
        // 走查实测形态（M12.md §7.2）：卖出未持有 → 补买回正 → 再卖出。
        // 修复前：均价被抬到 997,018 量级，再卖出产生 −57,843 的假亏损；
        // 修复后：均价 = 补买价，再卖出的已实现盈亏为正常量级。
        val shortSell = Ev.sell(1, Ev.BTC, Ev.USDT, price = "60000", qty = "0.1", source = RecordSource.Csv)
        val deposit = Ev.deposit(2, Ev.USDT, "20000", "20000")
        val buyBack = Ev.buy(3, Ev.BTC, Ev.USDT, price = "50000", qty = "0.2")
        val sell = Ev.sell(4, Ev.BTC, Ev.USDT, price = "55000", qty = "0.05")
        val outcome = ReplayEngine.replay(listOf(shortSell, deposit, buyBack, sell), NegativePolicy.LENIENT)
        val btc = outcome.holdings.getValue(Ev.BTC)
        assertMoney("0.05", btc.quantity)
        assertMoney("50000", btc.avgCostFiat!!, "重建后的均价 = 补买价（不再被空头污染）")
        // 已实现 = (2,750 − 0) − 0.05×50,000 = 250（修复前该笔会被 997,018 量级的均价放大成巨额假亏损）
        assertMoney("250", btc.realizedPnlFiat)
    }

}
