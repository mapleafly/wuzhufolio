package com.wuzhufolio.domain.backup

/**
 * .cpro 解码失败（T9.1 · flows §7「密码错误或文件损坏」/「未知更高版本提示升级应用」）。
 *
 * [reason] 语义：
 * - [Reason.MALFORMED_FILE]：头部缺失/JSON 损坏/长度不足/载荷 JSON 非法（文件不是 .cpro 或已截断）；
 * - [Reason.UNSUPPORTED_FORMAT]：format_version 高于本应用、cipher/KDF 算法不识别（提示升级应用）；
 * - [Reason.WRONG_PASSWORD_OR_CORRUPTED]：GCM 认证失败（密码错误或密文被篡改/损坏——PRD 有意不区分，
 *   避免向攻击者泄露「密码正确但文件坏」的旁路信息）。
 */
class CproDecodeException(
    val reason: Reason,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    enum class Reason { MALFORMED_FILE, UNSUPPORTED_FORMAT, WRONG_PASSWORD_OR_CORRUPTED }
}
