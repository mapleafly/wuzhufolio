package com.wuzhufolio.domain.settings

/**
 * 本地日志文件访问契约（M10 · T10.2「查看/导出日志」· ia.md §2.12「日志与诊断」）。
 *
 * 脱敏口径（PRD §6）：写入侧已脱敏；本契约实现必须在**返回/导出前再经 LogRedactor 兜底**，
 * 导出文件与查看视图同源同规则。禁止把完整 API 响应体/密钥带出日志文件。
 */
interface LogAccess {

    /** 日志文件路径（展示用；不可得返回 null）。 */
    fun path(): String?

    /** 尾部行（最多 [max] 行，最新在后；已脱敏）。 */
    fun tailLines(max: Int): List<String>

    /**
     * 导出日志到目标路径（逐行脱敏后写盘；返回导出行数；失败抛 IOException，文案由 UI 层映射）。
     * 目标路径由用户经系统保存对话框选择。
     */
    fun exportTo(targetPath: String): Int
}
