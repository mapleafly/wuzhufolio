package com.wuzhufolio.app.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * M13 T13.1 加固：日志脱敏漏斗（logback `%msg` 覆盖，见 [RedactingMessageConverter]）。
 * 守护口径：**任何**调用点（即使忘记显式脱敏）写入的日志行都不得携带密钥/令牌原文。
 */
class RedactingMessageConverterTest {

    private fun convert(message: String): String {
        val context = LoggerContext()
        val event = LoggingEvent(
            "test",
            context.getLogger("wuzhufolio.test"),
            Level.INFO,
            message,
            null,
            null,
        )
        return RedactingMessageConverter().convert(event)
    }

    @Test
    fun `key value pairs are masked without an explicit call site redaction`() {
        val out = convert("market.coingecko_key=CG-DEMO-0123456789abcdef0123456789abcdef | refresh ok")
        assertFalse(out.contains("CG-DEMO-0123456789abcdef0123456789abcdef"), "密钥原文不得出现在日志行")
        assertTrue(out.contains("market.coingecko_key=****"), "键值形态遮蔽（实际：$out）")
        assertTrue(out.contains("refresh ok"), "非敏感正文保留")
    }

    @Test
    fun `bare secret shaped tokens are masked`() {
        val signature = "b".repeat(64)
        val out = convert("binance signature " + signature + " rejected")
        assertFalse(out.contains(signature))
        assertTrue(out.contains("****"))
    }

    @Test
    fun `already redacted lines stay stable`() {
        val out = convert("market_api_key=**** saved")
        assertEquals("market_api_key=**** saved", out, "二次脱敏幂等（调用点显式脱敏保留为第二道）")
    }

    @Test
    fun `ordinary log lines pass through unchanged`() {
        val line = "bootstrap ok | schema=12 | settings(count=13)"
        assertEquals(line, convert(line))
    }
}
