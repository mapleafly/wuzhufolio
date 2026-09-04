package com.wuzhufolio.domain.market

/**
 * 行情刷新节奏纯规则（T5.4 · PRD 故事 3.2-6 / §7.2 模块 6.1/9.2）：
 *
 * - 窗口可见：按行情刷新频率（5/15/30 分钟，默认 5）；
 * - 托盘驻留：按 API 同步间隔降频（默认 30 分钟，与交易同步同频）；
 * - 额度治理：配置个人 Key 后月度额度达 80% → 行情刷新频率自动降一档（5→15→30），
 *   托盘降频取「降档后频率」与「同步间隔」的较大者（同步间隔本就是最低频基准）。
 */
class RefreshCadence(
    private val frequency: RefreshFrequency = RefreshFrequency.MIN5,
    private val syncIntervalMinutes: Int = 30,
) {

    /** 本次刷新与下次刷新之间的间隔（分钟）。 */
    fun nextIntervalMinutes(windowVisible: Boolean, quotaDowngraded: Boolean): Int {
        val effective = if (quotaDowngraded) bumpDown(frequency.minutes) else frequency.minutes
        return if (windowVisible) effective else maxOf(effective, syncIntervalMinutes)
    }

    private fun bumpDown(minutes: Int): Int = when {
        minutes <= 5 -> 15
        minutes <= 15 -> 30
        else -> 30
    }
}

/** 行情刷新频率档位（PRD §7.2 模块 6.1：5/15/30 分钟，默认 5；settings key market.refresh_minutes）。 */
enum class RefreshFrequency(val minutes: Int, val storageValue: String) {
    MIN5(5, "5"),
    MIN15(15, "15"),
    MIN30(30, "30"),
    ;

    companion object {
        fun fromStorage(value: String?): RefreshFrequency =
            entries.firstOrNull { it.storageValue == value } ?: MIN5
    }
}
