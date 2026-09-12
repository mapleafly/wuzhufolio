package com.wuzhufolio.ui.shell

import com.wuzhufolio.domain.exchange.SyncStatus
import com.wuzhufolio.ui.i18n.WzFormat
import com.wuzhufolio.ui.i18n.shellStrings

/**
 * 状态栏/顶栏文案派生（M12 · 2026-09-11 走查修复轮二）。
 *
 * **为什么在渲染期派生而不是存进状态**：[ShellStatus] 只持有原始数据（来源枚举/时刻/计数），
 * 文案每次组合时按当前语言现算——这样语言切换后顶栏「数据源」徽章与状态栏立即变英文，
 * 不必等下一次 30s 轮询（走查实测：此前把算好的中文串存进 StateFlow，切换语言后该处文案滞后）。
 *
 * 口径：与 interaction.md §1.1（同步状态/数据源/断链）与 §2.5（额度/限流）逐条对应；
 * 额度提示优先于备份提醒（异常级优先于常规建议）。
 */

/** 同步状态文案（空闲 / 同步中 / 成功 N 条 / 失败原因 + 最近同步时刻）。 */
fun ShellStatus.syncText(syncing: Boolean = false): String = when {
    syncing -> shellStrings.syncRunning()
    syncStatus == null -> shellStrings.syncIdle()
    else -> {
        val base = when (syncStatus) {
            SyncStatus.OK -> shellStrings.syncOk(syncNewTrades)
            SyncStatus.FAILED -> shellStrings.syncFailed(syncMessage ?: WzFormat.DASH)
        }
        syncAt?.let { base + " · " + WzFormat.dateTime(it) } ?: base
    }
}

/** 数据源徽章（CoinGecko / CoinMarketCap 兜底 + 上次成功时刻；未刷新则如实标注）。 */
fun ShellStatus.dataSourceText(): String =
    shellStrings.dataSourceBadge(marketSource, marketAt?.let { WzFormat.dateTime(it) })

/** 提示文案（额度 80% > 共享限流 > 备份提醒；无提示返回 null）。 */
fun ShellStatus.noticeText(): String? = when {
    quotaPercent != null && quotaPercent >= ShellStatusViewModel.QUOTA_WARN_PERCENT ->
        shellStrings.quotaWarning(quotaPercent)
    rateLimited -> shellStrings.sharedRateLimitHint()
    backupDays != null && backupDays > 0 -> shellStrings.backupReminder(backupDays)
    else -> null
}

/** 提示是否为警示级（额度/限流 = 警示色；备份提醒 = 常规色）。 */
fun ShellStatus.noticeIsWarn(): Boolean =
    (quotaPercent != null && quotaPercent >= ShellStatusViewModel.QUOTA_WARN_PERCENT) || rateLimited
