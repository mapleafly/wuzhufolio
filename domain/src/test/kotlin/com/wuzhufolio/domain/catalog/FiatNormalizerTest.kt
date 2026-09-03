package com.wuzhufolio.domain.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T3.3 验收（规则层）：法币交易对 1:1 孪生映射 / 第三币种语义（共享规范 §3；BTC/USD->quote=USDT、EUR->EURC）。 */
class FiatNormalizerTest {

    private val normalizer = FiatNormalizer()

    @Test
    fun `default twins map USD to USDT and EUR to EURC`() {
        assertEquals("USDT", normalizer.twinStableSymbolOf("USD"))
        assertEquals("EURC", normalizer.twinStableSymbolOf("EUR"))
        assertNull(normalizer.twinStableSymbolOf("GBP"))
        assertNull(normalizer.twinStableSymbolOf("BTC"))
    }

    @Test
    fun `classification is case and whitespace insensitive`() {
        assertEquals(
            FiatNormalizer.Classification.TwinMapped("USD", "USDT"),
            normalizer.classify("usd"),
        )
        assertEquals(
            FiatNormalizer.Classification.TwinMapped("EUR", "EURC"),
            normalizer.classify(" eur "),
        )
        assertEquals(
            FiatNormalizer.Classification.ThirdCurrencyFiat("GBP"),
            normalizer.classify("gbp"),
        )
    }

    @Test
    fun `fiat without twin mapping is third currency semantics`() {
        assertTrue(normalizer.classify("GBP") is FiatNormalizer.Classification.ThirdCurrencyFiat)
        assertTrue(normalizer.classify("CNY") is FiatNormalizer.Classification.ThirdCurrencyFiat)
        assertTrue(normalizer.classify("JPY") is FiatNormalizer.Classification.ThirdCurrencyFiat)
    }

    @Test
    fun `crypto symbols and blanks are not fiat`() {
        assertTrue(normalizer.classify("BTC") is FiatNormalizer.Classification.NotFiat)
        assertTrue(normalizer.classify("USDT") is FiatNormalizer.Classification.NotFiat)
        assertTrue(normalizer.classify("") is FiatNormalizer.Classification.NotFiat)
        assertTrue(normalizer.classify("   ") is FiatNormalizer.Classification.NotFiat)
    }

    @Test
    fun `known fiat predicate covers twins and third currency codes`() {
        assertTrue(normalizer.isKnownFiat("USD"))
        assertTrue(normalizer.isKnownFiat("eur"))
        assertTrue(normalizer.isKnownFiat("CNY"))
        assertFalse(normalizer.isKnownFiat("BTC"))
        assertFalse(normalizer.isKnownFiat("USDT"))
    }

    @Test
    fun `custom twin map can be injected for M10 settings overrides`() {
        val custom = FiatNormalizer(linkedMapOf("GBP" to "USDC"))
        assertEquals(
            FiatNormalizer.Classification.TwinMapped("GBP", "USDC"),
            custom.classify("GBP"),
        )
        // 未覆盖的已知法币回落到第三币种语义（调用方负责与默认表合并后再注入）
        assertTrue(custom.classify("USD") is FiatNormalizer.Classification.ThirdCurrencyFiat)
    }
}
