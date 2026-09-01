package com.wuzhufolio.domain.redaction

/**
 * 日志脱敏器（PRD §6、§1.1 硬约束：密钥与登录凭据永不落盘、日志脱敏）。
 *
 * 纯函数、无 IO，可单测；是 M10 日志服务的脱敏核心。所有日志消息在写入前必须经 [redact] 处理。
 * 脱敏策略（宁严勿宽）：
 * 1. 键值对形式：键名包含 key/secret/passphrase/password/token/session 等敏感词时，其值整体遮蔽；
 * 2. 裸值形式：连续 >=32 位的 hex/base64/base58 字符（疑似密钥、签名、令牌）整体遮蔽。
 */
object LogRedactor {

    const val MASK = "****"

    private const val SENSITIVE_WORDS = "key|secret|passphrase|password|passwd|token|session"

    /** 形如 name=value / name: value（值可为引号串或裸词），键名含敏感词即遮蔽值。 */
    private val sensitiveKeyValue = Regex(
        """(?i)\b([\w.-]*?(?:$SENSITIVE_WORDS)[\w.-]*?)\b(\s*[=:]\s*)("[^"]*"|'[^']*'|\S+)"""
    )

    /** 连续 >=32 位的疑似密钥/签名/令牌串。 */
    private val longSecretLike = Regex("""\b[0-9a-zA-Z+/_-]{32,}={0,2}\b""")

    /** 返回脱敏后的日志消息。 */
    fun redact(message: String): String {
        val maskedKeys = sensitiveKeyValue.replace(message) { m ->
            m.groupValues[1] + m.groupValues[2] + MASK
        }
        return longSecretLike.replace(maskedKeys, MASK)
    }
}
