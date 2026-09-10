package com.wuzhufolio.app.tray

import com.wuzhufolio.domain.exchange.ApiKeySyncResult
import com.wuzhufolio.domain.exchange.SyncStatus

/** 通知级别（映射到托盘通知的 severity；托盘不可用时降级为主壳日志）。 */
enum class NoticeLevel { INFO, WARNING, ERROR }

/** 桌面通知内容（纯数据——便于单测；转 Compose `Notification` 由 [TrayNotifications] 完成）。 */
data class DesktopNotice(val title: String, val message: String, val level: NoticeLevel)

/**
 * 桌面通知文案（M11 · T11.1 · PRD §9.4「后台同步完成或失败时发送桌面通知（可在设置中关闭）」；
 * design-tokens §5「桌面通知：同步完成 / 同步失败」；备份提醒 PRD §7.2 模块 6.1）。
 *
 * 口径：
 * - **行情刷新不发通知**——默认 5 分钟一轮，失败通知会形成噪音（行情异常态由行情页/状态栏承载，
 *   PRD 只要求「同步完成/失败」通知）；
 * - 通知正文只含计数与**已脱敏**的短消息（[ApiKeySyncResult.message] 由域层产出，不含密钥；
 *   硬约束「日志脱敏」同口径），并截断到 [MESSAGE_LIMIT] 字符；
 * - 无 API 密钥（results 为空）不发通知——用户尚未配置交易所，不该收到空同步提示。
 */
object DesktopNoticeText {

    /** 正文截断长度（托盘气泡宽度有限；防超长消息被系统裁掉关键信息）。 */
    const val MESSAGE_LIMIT: Int = 120

    /** 同步完成/失败通知；[results] 为空 → null（不发）。 */
    fun syncFinished(results: List<ApiKeySyncResult>): DesktopNotice? {
        if (results.isEmpty()) return null
        val failed = results.filter { it.status == SyncStatus.FAILED }
        return if (failed.isEmpty()) okNotice(results) else failedNotice(failed, results.size)
    }

    /** 备份提醒（PRD 6.1：距上次备份超过 30 天）。 */
    fun backupReminder(days: Long): DesktopNotice = DesktopNotice(
        title = "建议备份",
        message = "距上次备份已超过 30 天（当前 " + days + " 天）。建议在「设置 → 数据管理」导出 .cpro 备份。",
        level = NoticeLevel.WARNING,
    )

    private fun okNotice(results: List<ApiKeySyncResult>): DesktopNotice {
        val newTrades = results.sumOf { it.newTrades }
        val duplicates = results.sumOf { it.duplicatesSkipped }
        val unresolved = results.sumOf { it.unresolvedSkipped }
        val parts = mutableListOf<String>()
        parts += if (newTrades > 0) "新增 " + newTrades + " 笔交易" else "无新增交易"
        if (duplicates > 0) parts += "去重跳过 " + duplicates + " 笔"
        if (unresolved > 0) parts += "待解析 " + unresolved + " 笔"
        return DesktopNotice(
            title = "同步完成",
            message = truncate(parts.joinToString(" · ")),
            level = NoticeLevel.INFO,
        )
    }

    private fun failedNotice(failed: List<ApiKeySyncResult>, total: Int): DesktopNotice {
        val first = failed.first()
        // 多密钥时给出失败占比（单个密钥失败时标题已表达，无需赘述）
        val suffix = if (total > 1) "（共 " + failed.size + "/" + total + " 个密钥失败）" else ""
        val detail = first.message.takeIf { it.isNotBlank() } ?: "原因未明"
        return DesktopNotice(
            title = if (failed.size == total) "同步失败" else "部分密钥同步失败",
            message = truncate(first.apiKeyName + "：" + detail + suffix),
            level = NoticeLevel.ERROR,
        )
    }

    private fun truncate(text: String): String =
        if (text.length <= MESSAGE_LIMIT) text else text.take(MESSAGE_LIMIT - 1) + "…"
}
