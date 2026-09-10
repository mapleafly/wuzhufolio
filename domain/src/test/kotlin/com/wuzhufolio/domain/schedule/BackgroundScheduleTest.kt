package com.wuzhufolio.domain.schedule

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 后台调度纯规则（M11）：抖动 / 备份提醒 30 天 / 运行期日志轮转执行点。 */
class BackgroundScheduleTest {

    @Test
    fun `jitter stays within 20 percent and never zero`() {
        val base = 300_000L
        assertEquals(240_000L, ScheduleJitter.apply(base, 0.0), "下界 = -20%")
        assertEquals(360_000L, ScheduleJitter.apply(base, 0.999_999), "上界 = +20%")
        assertEquals(base, ScheduleJitter.apply(base, 0.5), "中位 = 原值")
        assertEquals(0L, ScheduleJitter.apply(0L, 0.5), "零延迟保持零")
        assertEquals(1_000L, ScheduleJitter.apply(1_000L, 0.0), "下限 1s——不退化为忙轮询")
        assertTrue(ScheduleJitter.apply(base, 5.0) <= 360_000L, "越界样本被夹取")
    }

    @Test
    fun `backup reminder fires only past 30 days`() {
        val now = Instant.parse("2026-09-10T00:00:00Z")
        val day = 24L * 3600
        // 第 30 天整不算「超过」
        assertFalse(BackupReminder.isDue(now.minusSeconds(30 * day), null, now))
        assertTrue(BackupReminder.isDue(now.minusSeconds(31 * day), null, now))
        // 从未备份 → 以账户创建时刻为基线（新装用户满 30 天前不打扰）
        assertFalse(BackupReminder.isDue(null, now.minusSeconds(10 * day), now))
        assertTrue(BackupReminder.isDue(null, now.minusSeconds(40 * day), now))
        // 有备份记录时忽略创建时刻
        assertFalse(BackupReminder.isDue(now.minusSeconds(day), now.minusSeconds(400 * day), now))
        // 两个基线都不可得 → 不提醒（宁缺勿扰）
        assertFalse(BackupReminder.isDue(null, null, now))
    }

    @Test
    fun `log rotation cadence is due every six hours`() {
        val now = Instant.parse("2026-09-10T12:00:00Z")
        assertTrue(LogRotationCadence.isDue(null, now), "从未运行过 → 立即到期")
        assertFalse(LogRotationCadence.isDue(now.minusSeconds(5 * 3600), now))
        assertTrue(LogRotationCadence.isDue(now.minusSeconds(6 * 3600), now))
        assertTrue(LogRotationCadence.isDue(now.minusSeconds(24 * 3600), now))
        assertEquals(6L, LogRotationCadence.INTERVAL_HOURS)
        assertEquals(30L, BackupReminder.INTERVAL_DAYS)
    }
}
