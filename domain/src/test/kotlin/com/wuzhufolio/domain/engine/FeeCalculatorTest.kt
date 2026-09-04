package com.wuzhufolio.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * FeeCalculator / FeeRateResolver 测试（T4.3 验收：费率优先级「交易所 > 全局」、三币种计费基数）。
 * 费率存储口径 = 百分比数值（fee_rules.buy_rate/sell_rate，data-model §2.4）。
 */
class FeeCalculatorTest {

    // ---- 币种角色判定 ----

    @Test
    fun `fee coin role is derived from the pair`() {
        assertEquals(FeeCoinRole.QUOTE, FeeCalculator.roleOf("bitcoin", "tether", "tether"))
        assertEquals(FeeCoinRole.BASE, FeeCalculator.roleOf("bitcoin", "tether", "bitcoin"))
        assertEquals(FeeCoinRole.THIRD, FeeCalculator.roleOf("bitcoin", "tether", "binancecoin"))
    }

    // ---- 三币种基数 ----

    @Test
    fun `quote fee base is total price`() {
        val fee = FeeCalculator.feeFor(
            ratePercent = "0.1".bd(),
            quantity = "1".bd(),
            price = "50000".bd(),
            role = FeeCoinRole.QUOTE,
            prices = ConversionPx(quoteFiat = "1".bd()),
        )
        val quote = assertNotNull(fee)
        assertMoney("50", quote.coinQty, "0.1% × 50000")
        assertMoney("50", quote.fiatValue)
    }

    @Test
    fun `base fee base is quantity`() {
        val fee = FeeCalculator.feeFor(
            ratePercent = "0.1".bd(),
            quantity = "1".bd(),
            price = "50000".bd(),
            role = FeeCoinRole.BASE,
            prices = ConversionPx(feeFiat = "50000".bd()),
        )
        val quote = assertNotNull(fee)
        assertMoney("0.001", quote.coinQty, "0.1% × 1 BTC")
        assertMoney("50", quote.fiatValue, "0.001 × 市价 50000")
    }

    @Test
    fun `third currency fee is converted into fee coin quantity at market price`() {
        val fee = FeeCalculator.feeFor(
            ratePercent = "0.1".bd(),
            quantity = "1".bd(),
            price = "50000".bd(),
            role = FeeCoinRole.THIRD,
            prices = ConversionPx(quoteFiat = "1".bd(), feeFiat = "500".bd()),
        )
        val quote = assertNotNull(fee)
        assertMoney("0.1", quote.coinQty, "50 USDT 等值 / BNB 500")
        assertMoney("50", quote.fiatValue)
    }

    @Test
    fun `auto fee returns null when required price is missing`() {
        assertNull(
            FeeCalculator.feeFor(
                ratePercent = "0.1".bd(),
                quantity = "1".bd(),
                price = "50000".bd(),
                role = FeeCoinRole.THIRD,
                prices = ConversionPx(quoteFiat = "1".bd()),
            ),
            "第三币种折算需要手续费币种行情价",
        )
        assertNull(
            FeeCalculator.feeFor(
                ratePercent = "0.1".bd(),
                quantity = "1".bd(),
                price = "50000".bd(),
                role = FeeCoinRole.QUOTE,
            ),
        )
        assertNull(
            FeeCalculator.feeFor(
                ratePercent = "0.1".bd(),
                quantity = "1".bd(),
                price = "50000".bd(),
                role = FeeCoinRole.BASE,
            ),
        )
    }

    @Test
    fun `auto fee feeds trade event bookkeeping end to end`() {
        // 0.1% 自动费率 → fee 0.1 BNB（50 USDT 等值）→ 成本含折算手续费、BNB 持仓联动扣减
        val fee = assertNotNull(
            FeeCalculator.feeFor(
                ratePercent = "0.1".bd(),
                quantity = "1".bd(),
                price = "50000".bd(),
                role = FeeCoinRole.THIRD,
                prices = ConversionPx(quoteFiat = "1".bd(), feeFiat = "500".bd()),
            ),
        )
        val events = listOf(
            Ev.deposit(1, Ev.USDT, "60000", "60000"),
            Ev.deposit(1, Ev.BNB, "0.2", "100"),
            Ev.buy(
                2, Ev.BTC, Ev.USDT, price = "50000", qty = "1",
                fee = fee.coinQty.toPlainString(), feeCoin = Ev.BNB, feePx = "500",
            ),
        )
        val outcome = ReplayEngine.replay(events)
        assertMoney("50050", outcome.holdings.getValue(Ev.BTC).costFiat)
        assertMoney("0.1", outcome.holdings.getValue(Ev.BNB).quantity)
        assertMoney("50", outcome.holdings.getValue(Ev.BNB).costFiat, "BNB 成本按均价移出")
        assertTrue(outcome.violations.isEmpty())
    }

    // ---- 费率优先级：交易所 > 全局 ----

    @Test
    fun `exchange rule wins over global rule for matching exchange`() {
        val resolver = FeeRateResolver(
            listOf(
                FeeRule(exchange = null, buyRatePercent = "0.2".bd(), sellRatePercent = "0.2".bd()),
                FeeRule(exchange = "BINANCE", buyRatePercent = "0.1".bd(), sellRatePercent = "0.075".bd()),
            ),
        )
        assertMoney("0.1", assertNotNull(resolver.ratePercentFor("BINANCE", Side.BUY)))
        assertMoney("0.075", assertNotNull(resolver.ratePercentFor("Binance", Side.SELL)), "交易所匹配大小写不敏感")
        assertMoney("0.2", assertNotNull(resolver.ratePercentFor("KRAKEN", Side.BUY)), "无交易所规则回落全局")
    }

    @Test
    fun `global rule is used when no exchange rule exists and null when no rules at all`() {
        assertNull(FeeRateResolver(emptyList()).ratePercentFor("BINANCE", Side.BUY))
        val globalOnly = FeeRateResolver(listOf(FeeRule(null, "0.1".bd(), "0.1".bd())))
        assertMoney("0.1", assertNotNull(globalOnly.ratePercentFor("BINANCE", Side.SELL)))
    }

    @Test
    fun `buy and sell rates are selected independently`() {
        val resolver = FeeRateResolver(
            listOf(FeeRule(exchange = "BINANCE", buyRatePercent = "0.1".bd(), sellRatePercent = "0.2".bd())),
        )
        assertMoney("0.1", assertNotNull(resolver.ratePercentFor("BINANCE", Side.BUY)))
        assertMoney("0.2", assertNotNull(resolver.ratePercentFor("BINANCE", Side.SELL)))
    }
}
