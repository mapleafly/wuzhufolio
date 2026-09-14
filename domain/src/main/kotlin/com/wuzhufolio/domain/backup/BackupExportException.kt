package com.wuzhufolio.domain.backup

/**
 * `.cpro` **导出**失败（P6 · P5-4 登记项闭环）。
 *
 * 与 [CproDecodeException]（导入侧）区分：导出侧此前对凭证解密失败**无类型化错误**——直接上浮
 * `AuthenticationFailedException`（AES-GCM 认证失败），UI 只能把原始加密异常文案透出给用户，
 * 既不可读也不可定位（见 `docs/test/integration-report.md` P5-4）。
 *
 * [Reason] 语义：
 * - [Reason.CREDENTIAL_UNREADABLE]：库内 `api_keys` 凭证列密文无法用**当前账户 DEK/AAD** 解密
 *   （DB 被外部改动、位翻转损坏、跨账户误拷）。导出会**整体中止且不落文件**——凭证解不开就不能进备份，
 *   失败方向偏安全侧（拒绝导出，而不是导出残缺/错误内容）。用户可编辑/移除该密钥后重试。
 *
 * [keyName] 仅供 UI 点名问题密钥；**不含**密钥明文、密文或 DEK。
 */
class BackupExportException(
    val reason: Reason,
    override val message: String,
    val keyName: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    enum class Reason { CREDENTIAL_UNREADABLE }
}
