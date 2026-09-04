package com.wuzhufolio.domain.market

import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 24h 固定数量回算法测试（T5.3 验收 · 黄金用例 11：部分缺价覆盖口径 / 全缺失 "--" / 同源标注）。
 */
class TwentyFourHourTest {

    private fun input(
        coin: String,
        qty: String,
        now: String?,
        ago: String?,
        sourceNow: PriceSource = PriceSource.COINGECKO,
    ): TwentyFourHour.CoinInput = TwentyFourHour.CoinInput(
        coinId = coin,
        quantity = qty.bd(),
        priceNow = now?.bd(),
        sourceNow = sourceNow,
        priceAgo = ago?.bd(),
        sourceAgo = sourceNow, // 单源配对（异源场景由测试直接构造 CoinInput 覆盖）
        mixed = false,
    )

    @Test
    fun `golden 11 - partial missing 24h prices are excluded and coverage is 8 of 10`() {
        val inputs = (1..10).map { i ->
            val coin = "coin-$i"
            // 8 个有 24h 前价（涨幅 10%），2 个（9、10）没有 24h 前价
            val ago = if (i <= 8) "100" else null
            input(coin, qty = "1", now = "110", ago = ago)
        }
        val result = TwentyFourHour.compute(inputs)

        assertEquals(8, result.covered, "覆盖 8/10")
        assertEquals(10, result.total)
        // Σ(1×(110−100)) = 80
        assertEquals(0, "80".bd().compareTo(result.pnlFiat!!))
        // pct = 80 / Σ(1×100) = 80/800 = 10%
        assertEquals(0, "10".bd().compareTo(result.pct!!))
        assertFalse(result.mixedSource)
    }

    @Test
    fun `golden 11 - all coins without 24h price yield nulls (display dash)`() {
        val inputs = (1..10).map { i -> input("coin-$i", qty = "1", now = "110", ago = null) }
        val result = TwentyFourHour.compute(inputs)
        assertEquals(0, result.covered)
        assertEquals(10, result.total)
        assertNull(result.pnlFiat)
        assertNull(result.pct)
    }

    @Test
    fun `coins without current price are unpriced and excluded from coverage base`() {
        val inputs = listOf(
            input("a", qty = "1", now = "110", ago = "100"),
            input("b", qty = "2", now = null, ago = "50"), // 「无行情」：不计入 total
        )
        val result = TwentyFourHour.compute(inputs)
        assertEquals(1, result.total)
        assertEquals(1, result.covered)
        assertEquals(0, "10".bd().compareTo(result.pnlFiat!!))
    }

    @Test
    fun `mixed source pairs are annotated`() {
        val inputs = listOf(
            TwentyFourHour.CoinInput(
                coinId = "a", quantity = "1".bd(),
                priceNow = "110".bd(), sourceNow = PriceSource.COINMARKETCAP,
                priceAgo = "100".bd(), sourceAgo = PriceSource.COINGECKO, mixed = true,
            ),
        )
        val result = TwentyFourHour.compute(inputs)
        assertTrue(result.mixedSource)
        assertEquals(1, result.covered)
    }

    @Test
    fun `loss produces negative pnl with negative pct`() {
        val inputs = listOf(input("a", qty = "1", now = "90", ago = "100"))
        val result = TwentyFourHour.compute(inputs)
        assertEquals(0, "-10".bd().compareTo(result.pnlFiat!!))
        assertEquals(0, "-10".bd().compareTo(result.pct!!))
    }

    @Test
    fun `empty inputs produce dash result`() {
        val result = TwentyFourHour.compute(emptyList())
        assertNull(result.pnlFiat)
        assertNull(result.pct)
        assertEquals(0, result.covered)
        assertEquals(0, result.total)
    }

    private fun String.bd(): BigDecimal = BigDecimal(this)
}
