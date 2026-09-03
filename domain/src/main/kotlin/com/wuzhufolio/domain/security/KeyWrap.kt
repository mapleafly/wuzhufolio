package com.wuzhufolio.domain.security

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * DEK/KEK 包解包（ADR-002 §2：AES-256-GCM，AAD=account_id+"wrapped_dek"，随机 96-bit nonce；
 * 存储形态 v1+base64(nonce‖ciphertext‖tag)）。数据列 accounts.wrapped_dek（data-model §2.1）。
 *
 * 语义（ADR-002 §3）：解包 = 密码认证——KEK 错误必然 GCM 认证失败（[AuthenticationFailedException]）。
 */
object KeyWrap {

    private const val FORMAT_VERSION = "v1"
    private const val AAD_SUFFIX = "wrapped_dek"
    private const val SESSION_AAD_SUFFIX = "session"

    /** 用账户 KEK 包裹账户 DEK。 */
    fun wrap(dek: ByteArray, kek: ByteArray, accountId: String): String {
        require(dek.size == 32) { "DEK must be 32 bytes" }
        require(kek.size == 32) { "KEK must be 32 bytes" }
        val sealed = AesGcm.encrypt(kek, dek, aadOf(accountId))
        val payload = AesGcm.concat(sealed.nonce, sealed.body)
        return FORMAT_VERSION + Base64.getEncoder().encodeToString(payload)
    }

    /** 解包 DEK（认证失败抛 [AuthenticationFailedException]；格式损坏抛 [IllegalArgumentException]）。 */
    fun unwrap(wrapped: String, kek: ByteArray, accountId: String): ByteArray {
        require(kek.size == 32) { "KEK must be 32 bytes" }
        require(wrapped.startsWith(FORMAT_VERSION)) { "unsupported wrapped_dek format: " + wrapped.take(2) }
        val raw = Base64.getDecoder().decode(wrapped.removePrefix(FORMAT_VERSION))
        require(raw.size >= AesGcm.NONCE_BYTES + AesGcm.TAG_BYTES) { "wrapped_dek payload too short" }
        val nonce = raw.copyOfRange(0, AesGcm.NONCE_BYTES)
        val body = raw.copyOfRange(AesGcm.NONCE_BYTES, raw.size)
        return AesGcm.decrypt(kek, nonce, body, aadOf(accountId))
    }

    /** 记住我：以会话令牌（随机 256-bit，与密码无推导关系）包裹 DEK（ADR-002 §3），存入 OS 钥匙串。 */
    fun wrapForSession(dek: ByteArray, token: ByteArray, accountId: String): String {
        require(dek.size == 32) { "DEK must be 32 bytes" }
        require(token.size == 32) { "session token must be 32 bytes" }
        val sealed = AesGcm.encrypt(token, dek, aadOf(accountId, SESSION_AAD_SUFFIX))
        val payload = AesGcm.concat(sealed.nonce, sealed.body)
        return FORMAT_VERSION + Base64.getEncoder().encodeToString(payload)
    }

    /** 记住我恢复：令牌解包 DEK（令牌错误/条目被篡改 → AuthenticationFailedException）。 */
    fun unwrapForSession(wrapped: String, token: ByteArray, accountId: String): ByteArray {
        require(token.size == 32) { "session token must be 32 bytes" }
        require(wrapped.startsWith(FORMAT_VERSION)) { "unsupported wrapped_dek format: " + wrapped.take(2) }
        val raw = Base64.getDecoder().decode(wrapped.removePrefix(FORMAT_VERSION))
        require(raw.size >= AesGcm.NONCE_BYTES + AesGcm.TAG_BYTES) { "wrapped_dek payload too short" }
        val nonce = raw.copyOfRange(0, AesGcm.NONCE_BYTES)
        val body = raw.copyOfRange(AesGcm.NONCE_BYTES, raw.size)
        return AesGcm.decrypt(token, nonce, body, aadOf(accountId, SESSION_AAD_SUFFIX))
    }

    private fun aadOf(accountId: String, suffix: String): ByteArray =
        (accountId + suffix).toByteArray(StandardCharsets.UTF_8)

    private fun aadOf(accountId: String): ByteArray = aadOf(accountId, AAD_SUFFIX)
}
