package com.wuzhufolio.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ReconciliationService 测试（T4.4 验收：单一数据来源判定 + 差额账务恒等式 + 价格前提）。
 * 锚点参与重放后的恒等式数值断言见 GoldenCasesTest.golden8。
 */
class ReconciliationServiceTest {

    private val service = ReconciliationService()

    // ---- 单一来源判定（多来源 → 入口隐藏） ----

    @Test
    fun `no trade records means calibration not applicable`() {
        assertEquals(SourceClassification.NoRecords, service.classifySources(emptySet()))
    }

    @Test
    fun `single exchange api source is eligible`() {
        val classification = service.classifySources(setOf(RecordSource.Exchange("BINANCE")))
        assertEquals(SourceClassification.SingleExchange("BINANCE"), classification)
    }

    @Test
    fun `fund events never contribute sources so api coins stay single source`() {
        val outcome = ReplayEngine.replay(
            listOf(
                Ev.deposit(1, Ev.USDT, "50050", "50050"),
                Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", fee = "50",
                    source = RecordSource.Exchange("BINANCE")),
                Ev.deposit(2, Ev.BTC, "0.1", "5000"), // 手动补录增资（交易所内未同步到账的转入）
            ),
        )
        val classification = service.classifySources(outcome.holdings.getValue(Ev.BTC).sources)
        assertEquals(SourceClassification.SingleExchange("BINANCE"), classification)
    }

    @Test
    fun `manual or csv trades make the coin multi source`() {
        val manual = service.classifySources(setOf(RecordSource.Exchange("BINANCE"), RecordSource.Manual))
        assertTrue(manual is SourceClassification.Multi)
        assertEquals(listOf("exchange:BINANCE", "manual"), manual.sources)

        val csvMixed = service.classifySources(setOf(RecordSource.Csv, RecordSource.Exchange("BINANCE")))
        assertTrue(csvMixed is SourceClassification.Multi)

        val manualOnly = service.classifySources(setOf(RecordSource.Manual))
        assertTrue(manualOnly is SourceClassification.Multi, "手动记录币种不满足「全部来自同一交易所 API 同步」")
    }

    @Test
    fun `trade on both legs marks quote coin multi source when mixed`() {
        val outcome = ReplayEngine.replay(
            listOf(
                Ev.deposit(1, Ev.USDT, "50050", "50050"),
                Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", fee = "50",
                    source = RecordSource.Exchange("BINANCE")),
                Ev.sell(3, Ev.USDT, Ev.USDC, price = "1", qty = "1", source = RecordSource.Manual),
            ),
            NegativePolicy.LENIENT,
        )
        val classification = service.classifySources(outcome.holdings.getValue(Ev.USDT).sources)
        assertTrue(classification is SourceClassification.Multi, "USDT 两腿来源混合 → 多来源")
    }

    // ---- 差额规划 ----

    @Test
    fun `plan computes delta and fiat value with direction`() {
        val negative = service.plan(
            "bitcoin", localQuantity = "1".bd(), exchangeQuantity = "0.8".bd(), marketFiatPrice = "55000".bd(),
        )
        assertMoney("-0.2", negative.delta)
        assertMoney("11000", negative.deltaFiat, "|delta| × 校准时市价")
        assertEquals(FlowKind.WITHDRAWAL, negative.direction)

        val positive = service.plan(
            "bitcoin", localQuantity = "1".bd(), exchangeQuantity = "1.2".bd(), marketFiatPrice = "55000".bd(),
        )
        assertMoney("0.2", positive.delta)
        assertMoney("11000", positive.deltaFiat)
        assertEquals(FlowKind.DEPOSIT, positive.direction)
    }

    @Test
    fun `zero delta plan needs no price`() {
        val plan = service.plan(
            "bitcoin", localQuantity = "1".bd(), exchangeQuantity = "1".bd(), marketFiatPrice = null,
        )
        assertMoney("0", plan.delta)
        assertNull(plan.direction)
    }

    @Test
    fun `price unavailable blocks reconciliation when delta is not zero`() {
        val e = assertFailsWith<CalibrationPriceUnavailableException> {
            service.plan(
                "bitcoin", localQuantity = "1".bd(), exchangeQuantity = "0.8".bd(), marketFiatPrice = null,
            )
        }
        assertEquals("bitcoin", e.message?.substringAfter("of "))
    }

    @Test
    fun `anchor from plan keeps identity under a positive delta`() {
        // 与 golden 8 对称的正差额：总收益在校准前后一致（恒等式保持、零凭空盈亏）
        val marketPx = "55000".bd()
        val deposit = Ev.deposit(1, Ev.USDT, "60000", "60000")
        val buy = Ev.buy(2, Ev.BTC, Ev.USDT, price = "50000", qty = "1", source = RecordSource.Exchange("BINANCE"))
        val plan = service.plan(
            "bitcoin", localQuantity = "1".bd(), exchangeQuantity = "1.2".bd(), marketFiatPrice = marketPx,
        )
        val anchor = Ev.anchor(3, Ev.BTC, exchangeQty = plan.delta.add("1".bd()).toPlainString(),
            delta = plan.delta.toPlainString(), deltaFiat = plan.deltaFiat.toPlainString())

        val before = PortfolioCalculator().compute(
            ReplayEngine.replay(listOf(deposit, buy)),
            mapOf(Ev.USDT to "1".bd(), Ev.BTC to marketPx),
        )
        val after = PortfolioCalculator().compute(
            ReplayEngine.replay(listOf(deposit, buy, anchor)),
            mapOf(Ev.USDT to "1".bd(), Ev.BTC to marketPx),
        )
        assertMoney(
            assertNotNull(before.totalReturnFiat).toPlainString(),
            assertNotNull(after.totalReturnFiat),
            "正差额校准亦不制造凭空盈亏",
        )
        assertMoney("11000", after.cumulativeDepositsFiat - "60000".bd(), "正差额计入累计增资")
        assertMoney("1.2", after.holdings.getValue(Ev.BTC).quantity)
    }
}
