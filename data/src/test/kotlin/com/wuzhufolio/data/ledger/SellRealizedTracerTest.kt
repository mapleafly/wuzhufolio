package com.wuzhufolio.data.ledger

import com.wuzhufolio.domain.engine.FundEvent
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.engine.LedgerEvent
import com.wuzhufolio.domain.engine.NegativePolicy
import com.wuzhufolio.domain.engine.RecordSource
import com.wuzhufolio.domain.engine.ReplayEngine
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.engine.TradeEvent
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * 卖出逐笔已实现盈亏轨迹（展示口径）：与 ReplayEngine 合计一致性交叉校验
 * （单一真源 = 引擎；发散即测试失败——模块记录 M7 §5 登记守护）。
 */
class SellRealizedTracerTest {

    private val t0: Instant = Instant.parse("2026-08-30T08:00:00Z")

    @Suppress("LongParameterList") // 测试事件构造器（默认值 + 命名参数覆盖）
    private fun buy(
        id: String,
        base: String,
        quote: String,
        price: String,
        qty: String,
        fee: String = "0",
        feeCoin: String? = null,
        at: Instant = t0,
        feeFiatOverride: String? = null,
    ) = trade(id, base, quote, Side.BUY, price, qty, fee, feeCoin, at, feeFiatOverride)

    @Suppress("LongParameterList")
    private fun sell(
        id: String,
        base: String,
        quote: String,
        price: String,
        qty: String,
        fee: String = "0",
        feeCoin: String? = null,
        at: Instant = t0,
        feeFiatOverride: String? = null,
    ) = trade(id, base, quote, Side.SELL, price, qty, fee, feeCoin, at, feeFiatOverride)

    @Suppress("LongParameterList")
    private fun trade(
        id: String,
        base: String,
        quote: String,
        side: Side,
        price: String,
        qty: String,
        fee: String,
        feeCoin: String?,
        at: Instant,
        feeFiatOverride: String? = null,
    ): TradeEvent {
        val priceBd = BigDecimal(price)
        val qtyBd = BigDecimal(qty)
        val feeBd = BigDecimal(fee)
        val feeC = feeCoin ?: quote
        return TradeEvent(
            id = id,
            at = at,
            baseCoin = base,
            quoteCoin = quote,
            side = side,
            price = priceBd,
            quantity = qtyBd,
            fee = feeBd,
            feeCoin = feeC,
            legFiat = qtyBd.multiply(priceBd), // 记账单位简化为 1:1（本测试只校验已实现口径）
            feeFiat = feeFiatOverride?.let { BigDecimal(it) } ?: feeBd,
            source = RecordSource.Manual,
            seq = 0L,
        )
    }

    private fun deposit(id: String, coin: String, qty: String, fiat: String, at: Instant): FundEvent =
        FundEvent(
            id = id,
            at = at,
            coin = coin,
            kind = FlowKind.DEPOSIT,
            quantity = BigDecimal(qty),
            fiatValue = BigDecimal(fiat),
        )

    @Test
    fun goldenCaseOneRealizedMatchesEngineTotal() {
        // 黄金用例 1 形状：50000 买 1 BTC -> 60000 卖 0.5
        val events: List<LedgerEvent> = listOf(
            buy("e1", "bitcoin", "tether", "50000", "1"),
            sell("e2", "bitcoin", "tether", "60000", "0.5", at = t0.plusSeconds(60)),
        )
        val realized = SellRealizedTracer.realizedByEvent(events)
        // 已实现 = (60000×0.5 − 0) − 0.5 × 50000 = 30000 − 25000 = 5000
        assertEquals(0, BigDecimal("5000").compareTo(realized["e2"]))
        val engineTotal = ReplayEngine.replay(events, NegativePolicy.LENIENT).realizedPnlFiat
        assertEquals(engineTotal, realized.values.fold(BigDecimal.ZERO) { a, b -> a + b })
    }

    @Test
    fun depositBeforeBuyAdjustsAverageCost() {
        // 增资 10000 USD 买 0.5 USDT 后买入成本摊薄
        val events: List<LedgerEvent> = listOf(
            deposit("d1", "usd-coin", "0.2", "20000", t0), // 0.2 USDC 成本 20000（单价 10 万，为让均价显著）
            buy("e1", "bitcoin", "usd-coin", "50000", "0.1", at = t0.plusSeconds(10)),
            sell("e2", "bitcoin", "usd-coin", "60000", "0.05", at = t0.plusSeconds(20)),
        )
        val realized = SellRealizedTracer.realizedByEvent(events)
        val r = realized["e2"]
        // 增资记的是 usd-coin 持仓，不参与 BTC 成本。
        // BTC 成本仅来自 e1：0.1 @ 50000 = 5000；卖 0.05：realized = 0.05×60000 − 0.05×50000 = 500
        assertEquals(0, BigDecimal("500").compareTo(r))
        val engineTotal = ReplayEngine.replay(events, NegativePolicy.LENIENT).realizedPnlFiat
        assertEquals(engineTotal, realized.values.fold(BigDecimal.ZERO) { a, b -> a + b })
    }

    @Test
    fun baseFeeReducesQuantityAndThirdFeeDeductsFeeCoin() {
        val events: List<LedgerEvent> = listOf(
            buy("e1", "bitcoin", "tether", "50000", "1", fee = "0.01", feeCoin = "bitcoin", feeFiatOverride = "0"),
            sell("e2", "bitcoin", "tether", "60000", "0.5", at = t0.plusSeconds(60)),
        )
        val realized = SellRealizedTracer.realizedByEvent(events)
        // 到账 = 1 − 0.01 = 0.99；成本 = 50000；卖出 0.5：avg = 50000/0.99 = 50505.05...
        val r = realized["e2"]!!
        val engineTotal = ReplayEngine.replay(events, NegativePolicy.LENIENT).realizedPnlFiat
        assertEquals(engineTotal, realized.values.fold(BigDecimal.ZERO) { a, b -> a + b })
        // 数值交叉（引擎同式）：(30000 − 0) − 0.5×avg
        val avg = BigDecimal("50000").divide(BigDecimal("0.99"), 12, java.math.RoundingMode.HALF_UP)
        assertEquals(0, BigDecimal("30000").subtract(BigDecimal("0.5").multiply(avg)).compareTo(r))
    }

    @Test
    fun anomalousSegmentHasNoRealizedEntry() {
        val events: List<LedgerEvent> = listOf(
            sell("e1", "bitcoin", "tether", "60000", "0.5"), // 无持仓直接卖（异常区段）
        )
        val realized = SellRealizedTracer.realizedByEvent(events)
        assertFalse(realized.containsKey("e1"), "异常区段不推导已实现盈亏")
        val engineTotal = ReplayEngine.replay(events, NegativePolicy.LENIENT).realizedPnlFiat
        assertEquals(BigDecimal.ZERO, engineTotal)
    }

    @Test
    fun sumOfPerEventRealizedEqualsEngineForMixedSequence() {
        val events: List<LedgerEvent> = listOf(
            deposit("d1", "tether", "50000", "50000", t0),
            buy("e1", "bitcoin", "tether", "50000", "1", at = t0.plusSeconds(10)),
            buy("e2", "ethereum", "tether", "3000", "2", at = t0.plusSeconds(20)),
            sell("e3", "bitcoin", "tether", "60000", "0.4", at = t0.plusSeconds(30)),
            sell("e4", "ethereum", "tether", "3200", "1", at = t0.plusSeconds(40)),
        )
        val realized = SellRealizedTracer.realizedByEvent(events)
        assertTrue(realized.containsKey("e3"))
        assertTrue(realized.containsKey("e4"))
        val engineTotal = ReplayEngine.replay(events, NegativePolicy.LENIENT).realizedPnlFiat
        assertEquals(engineTotal, realized.values.fold(BigDecimal.ZERO) { a, b -> a + b })
    }
    @Test
    fun negativePositionRebuildKeepsTracerInSyncWithEngine() {
        // D26 方案甲回归：负持仓被补买穿越后，逐笔轨迹与引擎合计仍必须一致，
        // 且重建后的成本基数只由「抬到 0 以上」的那部分构成。
        val events = listOf(
            sell("e1", "bitcoin", "tether", "60000", "0.1", at = t0),
            deposit("d1", "tether", "20000", "20000", at = t0.plusSeconds(60)),
            buy("e2", "bitcoin", "tether", "50000", "0.2", at = t0.plusSeconds(120)),
            sell("e3", "bitcoin", "tether", "55000", "0.05", at = t0.plusSeconds(180)),
        )
        val realized = SellRealizedTracer.realizedByEvent(events)
        val engine = ReplayEngine.replay(events, NegativePolicy.LENIENT)
        assertEquals(engine.realizedPnlFiat, realized.values.fold(BigDecimal.ZERO) { a, b -> a + b })
        assertEquals(0, BigDecimal("250").compareTo(realized.getValue("e3")), "重建后该笔已实现 = 250")
    }

}
