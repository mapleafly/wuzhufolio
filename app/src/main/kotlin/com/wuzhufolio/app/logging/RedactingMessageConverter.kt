package com.wuzhufolio.app.logging

import ch.qos.logback.classic.pattern.MessageConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import com.wuzhufolio.domain.redaction.LogRedactor

/**
 * 日志脱敏统一漏斗（M13 T13.1 安全自查加固；PRD §6 / AGENTS.md §1.1 硬约束 3）。
 *
 * 背景（安全自查发现项 G6）：此前脱敏只靠**调用点纪律**——生产代码约 80 处 logger 调用中仅 10 处显式
 * 经 [LogRedactor.redact]，logback 侧无 filter/converter；新增日志调用点一旦携带密钥/令牌即直接落盘。
 *
 * 本类经 `logback.xml` 的 `<conversionRule>` 覆盖 `%msg` / `%message` / `%m`，使**所有** appender
 * （控制台、文件，以及未来新增的 appender）的每条消息在渲染前统一过 [LogRedactor]：
 * - 调用点已有的显式脱敏保留为第二道（[LogRedactor] 幂等——`****` 不再被二次改写）；
 * - 覆盖 `%msg` 而非逐个 appender 配置，避免「新增 appender 忘配 filter」的同类缺口。
 */
class RedactingMessageConverter : MessageConverter() {

    override fun convert(event: ILoggingEvent): String = LogRedactor.redact(super.convert(event))
}
