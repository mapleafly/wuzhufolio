package com.wuzhufolio.domain.settings

import com.wuzhufolio.domain.market.QuotaCallKind
import java.time.Instant

/**
 * 诊断报告用例契约（M10 · T10.3 · task-breakdown §3 M10；ia.md §2.12「日志与诊断」；
 * interaction.md §2.6；PRD §6「日志管理与可追溯性」）。
 *
 * **内容受限清单（PRD 原文口径，逐项对应 [DiagnosticsReport] 字段）**：
 * 应用/OS 版本、数据库 schema 版本、最近日志片段（已脱敏）、同步与行情调用计数；
 * **不含密钥与完整 API 响应体**（日志片段写入前已经 LogRedactor 脱敏，导出前再兜底脱敏一次）。
 */
interface DiagnosticsService {

    /** 生成诊断报告（只读聚合，不落库、不改状态）。 */
    suspend fun generate(): DiagnosticsReport
}

/** 诊断报告（纯数据；文本渲染见 [DiagnosticsReportText]，导出/预览共用同一渲染）。 */
data class DiagnosticsReport(
    val appVersion: String,
    val osName: String,
    val osVersion: String,
    val osArch: String,
    /** 数据库 schema 版本（Migrations 最终版本）。 */
    val schemaVersion: Int,
    /** 本月行情调用计数（额度账本口径；仅个人 Key 模式计数，无 Key 全 0）。 */
    val marketCalls: Map<QuotaCallKind, Int>,
    /** 累计同步记录条数（sync_logs 全账户；同步调用计数口径，见模块记录 M10 §5）。 */
    val syncLogCount: Int,
    /** 最近一次同步时刻（全部记录中最新；无则 null）。 */
    val lastSyncAt: Instant?,
    /** 最近日志片段（已脱敏，最新在后）。 */
    val logTail: List<String>,
    val generatedAt: Instant,
)

/** 诊断报告文本渲染（预览与导出文件同源；纯函数可单测）。 */
object DiagnosticsReportText {

    /** 渲染为纯文本（键值行 + 日志片段缩进块；无密钥/响应体字段，结构即受限清单）。 */
    fun render(report: DiagnosticsReport): String = buildString {
        appendLine("WuZhuFolio 诊断报告")
        appendLine("生成时间：".plus(report.generatedAt.toString()))
        appendLine("应用版本：".plus(report.appVersion))
        appendLine("操作系统：".plus(report.osName + " " + report.osVersion + " (" + report.osArch + ")"))
        appendLine("数据库 schema 版本：".plus(report.schemaVersion))
        appendLine(
            "行情调用计数（本月）：" +
                report.marketCalls.entries.joinToString(" · ") { it.key.storageValue + "=" + it.value },
        )
        appendLine("同步调用计数（累计记录）：".plus(report.syncLogCount))
        appendLine(
            "最近同步：" + (report.lastSyncAt?.toString() ?: "无"),
        )
        appendLine("最近日志片段（已脱敏）：")
        if (report.logTail.isEmpty()) {
            appendLine("  （无日志）")
        } else {
            report.logTail.forEach { appendLine("  " + it) }
        }
        appendLine("注：本报告不含 API 密钥与完整响应体（PRD §6 内容受限清单）。")
    }
}
