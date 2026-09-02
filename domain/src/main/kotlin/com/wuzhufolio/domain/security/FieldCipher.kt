package com.wuzhufolio.domain.security

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * 凭证字段级加密（ADR-002 §2 / PRD 故事 5.1-1：仅 api_keys 的 api_key/secret_key/passphrase/extra 四列；
 * AAD=account_id+"|"+api_key_id+"|"+column；不做筛选/排序/索引）。
 *
 * 存储形态（M6 落库值）：v1 + base64(payloadVersion(1 字节)‖nonce‖ciphertext‖tag)。
 * 列隔离经 AAD 绑定：同一 DEK 下密文不可跨列/跨条目/跨账户解密（防复制粘贴串位）。
 */
object FieldCipher {

    private const val FORMAT_VERSION = "v1"
    private const val PAYLOAD_VERSION: Byte = 1

    /** 加密明文字段为存储串。 */
    fun encrypt(plain: String, dek: ByteArray, accountId: String, apiKeyId: String, column: String): String {
        require(dek.size == 32) { "DEK must be 32 bytes" }
        val sealed = AesGcm.encrypt(dek, plain.toByteArray(StandardCharsets.UTF_8), aadOf(accountId, apiKeyId, column))
        val payload = AesGcm.concat(byteArrayOf(PAYLOAD_VERSION), sealed.nonce, sealed.body)
        return FORMAT_VERSION + Base64.getEncoder().encodeToString(payload)
    }

    /** 解密存储串为明文字段（认证失败抛 [AuthenticationFailedException]；格式损坏抛 [IllegalArgumentException]）。 */
    fun decrypt(encoded: String, dek: ByteArray, accountId: String, apiKeyId: String, column: String): String {
        require(dek.size == 32) { "DEK must be 32 bytes" }
        require(encoded.startsWith(FORMAT_VERSION)) { "unsupported field cipher format: " + encoded.take(2) }
        val raw = Base64.getDecoder().decode(encoded.removePrefix(FORMAT_VERSION))
        require(raw.isNotEmpty()) { "field cipher payload empty" }
        val version = raw[0]
        require(version == PAYLOAD_VERSION) { "unsupported field cipher payload version: " + version }
        require(raw.size >= 1 + AesGcm.NONCE_BYTES + AesGcm.TAG_BYTES) { "field cipher payload too short" }
        val nonce = raw.copyOfRange(1, 1 + AesGcm.NONCE_BYTES)
        val body = raw.copyOfRange(1 + AesGcm.NONCE_BYTES, raw.size)
        val plain = AesGcm.decrypt(dek, nonce, body, aadOf(accountId, apiKeyId, column))
        return String(plain, StandardCharsets.UTF_8)
    }

    private fun aadOf(accountId: String, apiKeyId: String, column: String): ByteArray =
        (accountId + "|" + apiKeyId + "|" + column).toByteArray(StandardCharsets.UTF_8)
}
