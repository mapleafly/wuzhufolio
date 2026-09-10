package com.wuzhufolio.data.settings

import com.wuzhufolio.data.market.SettingsQuotaLedger
import com.wuzhufolio.domain.redaction.LogRedactor
import com.wuzhufolio.domain.settings.DiagnosticsReport
import com.wuzhufolio.domain.settings.DiagnosticsService
import java.time.Instant

/**
 * 诊断报告实现（M10 · T10.3 · PRD §6 内容受限清单）：
 * 应用/OS 版本 + schema 版本（boot 迁移结果）+ 最近日志片段（尾部读取 + **导出前再脱敏一次**）+
 * 行情调用计数（额度账本）与同步调用计数（sync_logs 累计条数/最近同步时刻）。
 * 不读取任何密钥字段与响应体——结构上即不含（见模块记录 M10 §5）。
 */
class DefaultDiagnosticsService(
    private val appVersion: String,
    private val schemaVersion: Int,
    private val quota: SettingsQuotaLedger,
    private val syncCount: () -> Int,
    private val lastSyncAt: () -> Instant?,
    /** 日志尾部原始行（最新在后；M10 日志写入侧已脱敏，此处生成报告时再兜底脱敏）。 */
    private val logTail: () -> List<String>,
) : DiagnosticsService {

    override suspend fun generate(): DiagnosticsReport {
        val calls = quota.counts()
        return DiagnosticsReport(
            appVersion = appVersion,
            osName = System.getProperty("os.name") ?: "unknown",
            osVersion = System.getProperty("os.version") ?: "unknown",
            osArch = System.getProperty("os.arch") ?: "unknown",
            schemaVersion = schemaVersion,
            marketCalls = calls,
            syncLogCount = syncCount(),
            lastSyncAt = lastSyncAt(),
            logTail = logTail().map { LogRedactor.redact(it) },
            generatedAt = Instant.now(),
        )
    }
}
