package com.wuzhufolio.app.logging

import ch.qos.logback.classic.pattern.ThrowableProxyConverter
import ch.qos.logback.classic.spi.IThrowableProxy
import com.wuzhufolio.domain.redaction.LogRedactor

/**
 * **异常堆栈脱敏**（C0，2026-10-12；四线独立复核 P2-3；PRD §6 / `AGENTS.md §1.1` 硬约束 3）。
 *
 * 背景：`logback.xml` 的 pattern 原先**不含** `%ex`，而 logback 在 pattern 无 throwable 转换词时
 * **仍会自动追加**异常堆栈；该路径**不经** [RedactingMessageConverter]（后者只包 `%msg`/`%message`/`%m`）
 * ⇒ 只要某处 `logger.warn("...", e)` 的异常消息或堆栈里含密钥/令牌，就会绕过脱敏漏斗直接落盘。
 * （复核时实测仅 2 处 logger 传 Throwable——`Main.kt` bootstrap 与 `StatusNotifierService` SNI——
 * 且均不载凭据，故当时影响有界；但这是**结构性旁路**，与 [RedactingMessageConverter] 的设立初衷相悖。）
 *
 * 做法：① 在 pattern 里**显式**写 `%ex`（消除「自动追加」这条旁路）；② 把 `%ex` 绑到本类——
 * 先取父类（[ThrowableProxyConverter]）的渲染结果，再过 [LogRedactor]（**幂等**，已遮蔽的 `****` 不再二次改写）。
 * 于是**堆栈文本与消息同处一个漏斗**，新增 appender 只要沿用同一 pattern 即自动受保护。
 */
class RedactingThrowableConverter : ThrowableProxyConverter() {

    override fun throwableProxyToString(tp: IThrowableProxy): String =
        LogRedactor.redact(super.throwableProxyToString(tp))
}
