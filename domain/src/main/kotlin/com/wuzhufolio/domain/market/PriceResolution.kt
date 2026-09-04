package com.wuzhufolio.domain.market

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 快照时间分辨率纯规则（T5.2/T5.3 · PRD 全局说明「行情数据与时间分辨率规则」/共享规范 §5）：
 *
 * - 存储：每 (coin, fiat, 小时桶) 至多一条（同小时取末条——upsert 覆盖），recorded_at 落任意桶内时刻；
 * - 降采样：**近 90 天保留小时级；更早仅日级（当日 00:00 UTC 行）**——压缩 = 删除截止线前
 *   非整点行（整点 00:00 行即当日日线，保留）；
 * - 读取：90 天内 → 目标小时桶行；更早 → 目标日 00:00（日线）行；
 * - 24h 配对：t−24h 所在小时桶行（同小时去重保证至多一条）。
 *
 * 数据访问由 data 层实现（快照仓库）；本对象只做桶/界/取舍规则，纯函数可单测。
 */
object PriceResolution {

    const val HOURLY_HORIZON_DAYS: Long = 90

    /** 时间 t 所属小时桶（UTC，截断到小时）。 */
    fun hourBucketOf(at: Instant): Instant = at.truncatedTo(ChronoUnit.HOURS)

    /** t 所在 UTC 日 00:00。 */
    fun dayStartOf(at: Instant): Instant = at.truncatedTo(ChronoUnit.DAYS)

    /** 是否整点 00:00 桶行（日线候选行）。 */
    fun isMidnightBucket(at: Instant): Boolean = hourBucketOf(at) == dayStartOf(at)

    /** 90 天小时级保留界（早于 [cutoff] 且非整点的行可压缩）。 */
    fun hourlyCutoff(now: Instant): Instant = now.minus(HOURLY_HORIZON_DAYS, ChronoUnit.DAYS)

    /** 降采样判定：早于保留界且非整点行 → 压缩（仅日线行留存）。 */
    fun shouldCompact(at: Instant, now: Instant): Boolean =
        at.isBefore(hourlyCutoff(now)) && !isMidnightBucket(at)

    /** 保留界内（可小时级解析）。 */
    fun isWithinHourlyHorizon(at: Instant, now: Instant): Boolean = !at.isBefore(hourlyCutoff(now))

    /** 目标解析时刻（24h 配对/历史折算）：now − 24h。 */
    fun pairInstant(now: Instant): Instant = now.minus(Duration.ofHours(24))

    /** 目标解析时刻的小时桶。 */
    fun resolutionKey(at: Instant): Instant = hourBucketOf(at)

    /** 非整点行是否可被压缩删除（存储层循环判定用）。 */
    fun compactable(recordedAt: Instant, now: Instant): Boolean = shouldCompact(recordedAt, now)
}
