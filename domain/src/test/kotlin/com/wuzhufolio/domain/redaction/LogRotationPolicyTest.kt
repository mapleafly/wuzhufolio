package com.wuzhufolio.domain.redaction

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 日志轮转纯规则（M10 T10.2）：1 万条/90 天「先到为准」口径。 */
class LogRotationPolicyTest {

    @Test
    fun `keep last lines trims beyond limit`() {
        val all = (1..10_500).map { "line-" + it }
        val kept = LogRotationPolicy.keepLastLines(all)
        assertEquals(LogRotationPolicy.MAX_LINES, kept.size)
        assertEquals("line-501", kept.first())
        assertEquals("line-10500", kept.last())
    }

    @Test
    fun `keep last lines is no-op under limit`() {
        val all = listOf("a", "b", "c")
        assertEquals(all, LogRotationPolicy.keepLastLines(all))
        assertEquals(emptyList(), LogRotationPolicy.keepLastLines(emptyList()))
    }

    @Test
    fun `expiry honours 90 days boundary`() {
        val now = Instant.parse("2026-09-10T00:00:00Z")
        val fresh = now.minusSeconds(89 * 24 * 3600)
        val stale = now.minusSeconds(91 * 24 * 3600)
        assertFalse(LogRotationPolicy.isExpired(fresh, now), "90 天内不删")
        assertTrue(LogRotationPolicy.isExpired(stale, now), "超过 90 天删除")
        assertEquals(now.minusSeconds(90 * 24 * 3600), LogRotationPolicy.cutoff(now))
    }
}
