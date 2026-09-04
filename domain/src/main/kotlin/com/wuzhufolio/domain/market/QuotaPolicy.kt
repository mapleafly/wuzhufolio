package com.wuzhufolio.domain.market

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 月度额度治理纯规则（T5.4 · ADR-003 §4 / PRD 共享规范「额度治理」）：
 *
 * - 仅配置个人 CoinGecko Key 后启用（无 Key 公共 API 无月度专属额度——只做 429 退避与降频保护）；
 * - 本地持久化月度调用计数（当前价 + 历史回填 + 目录，[QuotaCallKind]）；
 * - 达到月度额度 80% → 刷新频率自动降一档并在状态栏提示（[isDowngraded]）。
 */
object QuotaPolicy {

    /** CG 免费 Demo 档月度上限（次，ADR-003 §2 / PRD）。 */
    const val CG_MONTHLY_LIMIT: Int = 10_000

    /** 降档阈值（%）。 */
    const val DOWNGRADE_PERCENT: Int = 80

    private val MONTH_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC)

    /** 月度键（UTC，如 "2026-09"）。 */
    fun monthKey(now: Instant = Instant.now()): String = MONTH_KEY.format(now)

    /** 已用百分比（0–100，向下取整；超过 100 截断到 100）。 */
    fun percentUsed(counts: Map<QuotaCallKind, Int>): Int {
        val used = counts.values.sum()
        if (used <= 0) return 0
        val percent = used.toLong() * 100 / CG_MONTHLY_LIMIT
        return minOf(100, percent.toInt())
    }

    /** 是否达到降档线。 */
    fun isDowngraded(counts: Map<QuotaCallKind, Int>): Boolean = percentUsed(counts) >= DOWNGRADE_PERCENT
}

/** 额度计数类别（ADR-003 §4：当前价 + 历史回填 + 目录）。 */
enum class QuotaCallKind(val storageValue: String) {
    CURRENT("CURRENT"),
    HISTORY("HISTORY"),
    DIRECTORY("DIRECTORY"),
    ;

    companion object {
        fun fromStorage(value: String): QuotaCallKind =
            entries.firstOrNull { it.storageValue == value } ?: CURRENT
    }
}
