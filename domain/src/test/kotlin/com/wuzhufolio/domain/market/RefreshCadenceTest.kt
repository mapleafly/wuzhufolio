package com.wuzhufolio.domain.market

import kotlin.test.Test
import kotlin.test.assertEquals

/** 行情刷新节奏（T5.4：窗口可见频率 / 托盘按同步间隔降频 / 额度 80% 降一档）。 */
class RefreshCadenceTest {

    private fun cadence(frequency: RefreshFrequency = RefreshFrequency.MIN5, sync: Int = 30) =
        RefreshCadence(frequency, sync)

    @Test
    fun `visible window follows configured frequency`() {
        assertEquals(
    5,
    cadence(RefreshFrequency.MIN5).nextIntervalMinutes(windowVisible = true, quotaDowngraded = false),
)
        assertEquals(15, cadence(RefreshFrequency.MIN15).nextIntervalMinutes(true, false))
        assertEquals(30, cadence(RefreshFrequency.MIN30).nextIntervalMinutes(true, false))
    }

    @Test
    fun `tray mode degrades to the api sync interval`() {
        assertEquals(
    30,
    cadence(RefreshFrequency.MIN5).nextIntervalMinutes(windowVisible = false, quotaDowngraded = false),
)
        assertEquals(30, cadence(RefreshFrequency.MIN5, sync = 30).nextIntervalMinutes(false, false))
        assertEquals(60, cadence(RefreshFrequency.MIN5, sync = 60).nextIntervalMinutes(false, false))
    }

    @Test
    fun `quota downgrade bumps visible frequency one step`() {
        assertEquals(15, cadence(RefreshFrequency.MIN5).nextIntervalMinutes(true, quotaDowngraded = true))
        assertEquals(30, cadence(RefreshFrequency.MIN15).nextIntervalMinutes(true, true))
        assertEquals(30, cadence(RefreshFrequency.MIN30).nextIntervalMinutes(true, true), "最高档不再上调")
    }

    @Test
    fun `tray cadence keeps the slower of downgraded frequency and sync interval`() {
        assertEquals(
            30,
            cadence(RefreshFrequency.MIN5).nextIntervalMinutes(false, quotaDowngraded = true),
            "降档 15 分钟仍低于同步间隔 30 分钟",
        )
        assertEquals(60, cadence(RefreshFrequency.MIN5, sync = 60).nextIntervalMinutes(false, true))
    }

    @Test
    fun `frequency storage roundtrip`() {
        assertEquals(RefreshFrequency.MIN5, RefreshFrequency.fromStorage("5"))
        assertEquals(RefreshFrequency.MIN15, RefreshFrequency.fromStorage("15"))
        assertEquals(RefreshFrequency.MIN30, RefreshFrequency.fromStorage("30"))
        assertEquals(RefreshFrequency.MIN5, RefreshFrequency.fromStorage("bogus"))
        assertEquals(RefreshFrequency.MIN5, RefreshFrequency.fromStorage(null))
    }
}
