package com.wuzhufolio.domain.redaction

import java.time.Duration
import java.time.Instant

/**
 * 日志轮转纯规则（M10 · T10.2 · interaction.md §2.6「日志轮转」行；PRD §6）：
 *
 * 本地日志与 sync_logs 各保留**最近 1 万条或 90 天（先到为准）**，超出自动清理。
 * - 条数口径：保留最新 [MAX_LINES] 条（文件 = 末尾行；sync_logs = 最新 id 段）；
 * - 时间口径：早于 [MAX_AGE_DAYS] 天的记录删除（文件按文件修改时间整档删除；行/记录按时间戳）。
 * 磁盘护栏（logback 5MB 分卷）与 PRD 轮转正交保留——只增不减的额外保护，不替代本规则。
 */
object LogRotationPolicy {

    /** 保留上限（条）。 */
    const val MAX_LINES: Int = 10_000

    /** 保留天数（天）。 */
    const val MAX_AGE_DAYS: Long = 90

    /** 条数裁剪：保留最新 [maxLines] 条（输入序 = 追加序；末尾为最新）。 */
    fun keepLastLines(all: List<String>, maxLines: Int = MAX_LINES): List<String> =
        if (all.size <= maxLines) all else all.subList(all.size - maxLines, all.size)

    /** 文件是否过期（最后修改时间早于保留期）。 */
    fun isExpired(lastModified: Instant, now: Instant, maxAgeDays: Long = MAX_AGE_DAYS): Boolean =
        Duration.between(lastModified, now) > Duration.ofDays(maxAgeDays)

    /** 轮转截止时刻（早于它的记录/文件应删除）。 */
    fun cutoff(now: Instant, maxAgeDays: Long = MAX_AGE_DAYS): Instant =
        now.minus(Duration.ofDays(maxAgeDays))
}
