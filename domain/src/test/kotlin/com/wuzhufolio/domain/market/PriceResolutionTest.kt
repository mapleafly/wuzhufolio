package com.wuzhufolio.domain.market

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 快照时间分辨率规则（T5.2/T5.3：小时桶/日线候选/90 天压缩界）。 */
class PriceResolutionTest {

    @Test
    fun `hour bucket truncates to utc hour`() {
        val at = Instant.parse("2026-01-15T10:59:59Z")
        assertEquals(
            Instant.parse("2026-01-15T10:00:00Z"),
            PriceResolution.hourBucketOf(at),
        )
        assertEquals(Instant.parse("2026-01-15T10:00:00Z"),
        PriceResolution.hourBucketOf(Instant.parse("2026-01-15T10:00:00Z")))
    }

    @Test
    fun `midnight bucket is the daily line candidate`() {
        assertTrue(PriceResolution.isMidnightBucket(Instant.parse("2026-01-15T00:00:00Z")))
        assertTrue(PriceResolution.isMidnightBucket(Instant.parse("2026-01-15T00:30:00Z")), "桶内任意时刻")
        assertFalse(PriceResolution.isMidnightBucket(Instant.parse("2026-01-15T01:00:00Z")))
    }

    @Test
    fun `compaction keeps only midnight rows older than 90 days`() {
        val now = Instant.parse("2026-04-15T00:00:00Z")
        val old = Instant.parse("2026-01-01T12:00:00Z") // > 90d 前
        assertTrue(PriceResolution.shouldCompact(old, now))
        val oldMidnight = Instant.parse("2026-01-01T00:00:00Z")
        assertFalse(PriceResolution.shouldCompact(oldMidnight, now), "整点行 = 日线，保留")
        val fresh = Instant.parse("2026-02-01T12:00:00Z")
        assertFalse(PriceResolution.shouldCompact(fresh, now), "90 天内小时级保留")
    }

    @Test
    fun `hourly horizon boundary sits at exactly 90 days`() {
        val now = Instant.parse("2026-04-15T00:00:00Z")
        assertTrue(PriceResolution.isWithinHourlyHorizon(Instant.parse("2026-01-15T00:00:00Z"), now))
        assertFalse(PriceResolution.isWithinHourlyHorizon(Instant.parse("2026-01-14T23:59:59Z"), now))
    }

    @Test
    fun `pair instant is exactly 24 hours back`() {
        val now = Instant.parse("2026-01-15T10:00:00Z")
        assertEquals(Instant.parse("2026-01-14T10:00:00Z"), PriceResolution.pairInstant(now))
        assertEquals(
            Instant.parse("2026-01-14T10:00:00Z"),
            PriceResolution.resolutionKey(PriceResolution.pairInstant(now)),
        )
    }
}
