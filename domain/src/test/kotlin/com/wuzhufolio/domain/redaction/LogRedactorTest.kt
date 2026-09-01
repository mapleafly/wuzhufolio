package com.wuzhufolio.domain.redaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogRedactorTest {

    @Test
    fun `masks api key value in key-pair form`() {
        assertEquals("api_key=****", LogRedactor.redact("api_key=abcd1234"))
        assertEquals("secret: **** saved", LogRedactor.redact("secret: hunter2 saved"))
        assertEquals("passphrase=**** ok", LogRedactor.redact("passphrase=\"my secret phrase\" ok"))
    }

    @Test
    fun `masks provider-style key names`() {
        assertEquals(
            "market.coingecko_key=**** saved",
            LogRedactor.redact("market.coingecko_key=CG-abc123 saved"),
        )
        assertEquals("binance_secret_key=****", LogRedactor.redact("binance_secret_key=xyz"))
    }

    @Test
    fun `masks long bare secrets such as hex db keys and signatures`() {
        val hexKey = "a".repeat(64)
        assertEquals("db key ****", LogRedactor.redact("db key " + hexKey))
        val hmac = "0123456789abcdef0123456789abcdef"
        assertEquals("sig=****", LogRedactor.redact("sig=" + hmac))
    }

    @Test
    fun `keeps ordinary text intact`() {
        val msg = "settings loaded theme=light fiat=USD locale=zh-CN count=4"
        assertEquals(msg, LogRedactor.redact(msg))
    }

    @Test
    fun `full hello style line contains no raw secret`() {
        val raw = "hello-chain ok | schema_version=2 | market_api_key=CG-DEMO-0123456789abcdef0123456789abcdef"
        val out = LogRedactor.redact(raw)
        assertTrue(out.contains("market_api_key=****"), "key value must be masked: " + out)
        assertFalse(out.contains("0123456789abcdef0123456789abcdef"), "raw secret must not survive: " + out)
    }
}
