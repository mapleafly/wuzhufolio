package com.wuzhufolio.domain.market

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 月度额度治理（T5.4：计数口径/80% 降档线/月度键）。 */
class QuotaPolicyTest {

    @Test
    fun `month key uses utc yyyy-MM`() {
        val at = Instant.parse("2026-09-04T23:30:00Z")
        assertEquals("2026-09", QuotaPolicy.monthKey(at))
        val nextMonth = Instant.parse("2026-10-01T00:00:00Z")
        assertEquals("2026-10", QuotaPolicy.monthKey(nextMonth))
    }

    @Test
    fun `percent used is capped at 100 and rounds down`() {
        assertEquals(0, QuotaPolicy.percentUsed(emptyMap()))
        assertEquals(1, QuotaPolicy.percentUsed(mapOf(QuotaCallKind.CURRENT to 100)))
        assertEquals(50, QuotaPolicy.percentUsed(mapOf(QuotaCallKind.CURRENT to 5_000)))
        assertEquals(100, QuotaPolicy.percentUsed(mapOf(QuotaCallKind.CURRENT to 10_000)))
        assertEquals(100, QuotaPolicy.percentUsed(mapOf(QuotaCallKind.CURRENT to 12_345)))
    }

    @Test
    fun `counts aggregate across kinds`() {
        val counts = mapOf(
            QuotaCallKind.CURRENT to 7_000,
            QuotaCallKind.HISTORY to 500,
            QuotaCallKind.DIRECTORY to 500,
        )
        assertEquals(80, QuotaPolicy.percentUsed(counts))
        assertTrue(QuotaPolicy.isDowngraded(counts))
        assertFalse(
            QuotaPolicy.isDowngraded(mapOf(QuotaCallKind.CURRENT to 7_999)),
            "7,999/10,000 = 79% 未达降档线",
        )
        assertTrue(QuotaPolicy.isDowngraded(mapOf(QuotaCallKind.CURRENT to 8_000)))
    }
}
