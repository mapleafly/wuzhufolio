package com.wuzhufolio.domain.schedule

import java.time.Duration
import java.time.Instant

/**
 * 后台调度纯规则（M11 · 调度循环宿主 · PRD §7.2 模块 9.2 + 6.1）。
 *
 * 宿主职责（data 模块 BackgroundScheduler）只做「delay → 执行 → 记录」；**所有间隔与到期判定
 * 都是本文件的纯函数**，便于不依赖时钟的单元测试。间隔主口径见 [com.wuzhufolio.domain.market.RefreshCadence]
 * （行情：窗口可见按刷新频率、托盘驻留按同步间隔降频、额度 80% 降一档）与
 * [com.wuzhufolio.domain.exchange.ExchangeCadence]（同步：15/30/60，默认 30）。
 */

/**
 * 调度抖动（[com.wuzhufolio.domain.market.RateBackoff] 头注口径：宿主在基准延迟上 ±20% 内随机施加）。
 * 目的 = 避免多实例/多任务在同一秒齐步请求（429 风暴防护）。
 */
object ScheduleJitter {

    /** 抖动比例（±20%）。 */
    const val RATIO: Double = 0.2

    /**
     * 在 [baseMillis] 上施加 ±20% 抖动。
     * [sample] ∈ [0, 1) 由宿主以随机数提供（本函数保持纯函数——同一输入恒得同一输出，可测）。
     * sample < 0.5 → 向下抖动，≥ 0.5 → 向上抖动；结果下限 1s（不退化为 0 延迟忙轮询）。
     */
    fun apply(baseMillis: Long, sample: Double): Long {
        if (baseMillis <= 0L) return 0L
        val clamped = sample.coerceIn(0.0, 0.999_999)
        val factor = 1.0 - RATIO + 2 * RATIO * clamped
        return kotlin.math.round(baseMillis * factor).toLong().coerceAtLeast(1_000L)
    }
}

/**
 * 备份提醒（PRD §7.2 模块 6.1：「开关（默认开启），距上次备份超过 30 天时在状态栏/通知提示『建议备份』」；
 * design-tokens §5 同口径）。
 *
 * 基线口径：优先「上次备份时刻」（settings 账户行 `backup.last_at`，M9 落盘）；
 * 从未备份过时退化为「账户创建时刻」——新装用户在满 30 天前不打扰，避免首次启动即弹提醒。
 * 两者都不可得（异常/旧数据）→ 不判到期（宁缺勿扰）。
 */
object BackupReminder {

    /** 提醒阈值（天）。 */
    const val INTERVAL_DAYS: Long = 30

    /**
     * 是否应提示备份。
     * @param lastBackupAt 上次成功导出备份的时刻（null = 从未备份）。
     * @param accountCreatedAt 账户创建时刻（[lastBackupAt] 为 null 时的基线）。
     * @param now 当前时刻（注入便于测试）。
     */
    fun isDue(lastBackupAt: Instant?, accountCreatedAt: Instant?, now: Instant): Boolean {
        val baseline = lastBackupAt ?: accountCreatedAt ?: return false
        return Duration.between(baseline, now).toDays() > INTERVAL_DAYS
    }
}

/**
 * 运行中日志轮转执行点（M10 §5-5 遗留：「运行中实时轮转未做（启动 + 后续可挂调度执行点，M11 调度宿主登记）」）。
 *
 * 口径：启动时已由引导层执行一次（[com.wuzhufolio.data.logging.LogRotator]）；运行期按固定间隔复查，
 * 使长驻托盘（不重启）的用户也能命中「1 万条或 90 天先到为准」的裁剪。间隔取 6 小时——
 * 相对 90 天量级足够密，又不形成可观开销（轮转为本地文件读，无网络）。
 */
object LogRotationCadence {

    /** 运行期复查间隔（小时）。 */
    const val INTERVAL_HOURS: Long = 6

    /** 距上次轮转是否已到复查点（[lastRunAt] 为 null = 尚未运行过 → 立即到期）。 */
    fun isDue(lastRunAt: Instant?, now: Instant): Boolean =
        lastRunAt == null || Duration.between(lastRunAt, now).toHours() >= INTERVAL_HOURS
}
