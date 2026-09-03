package com.wuzhufolio.domain.security

import java.security.MessageDigest
import java.util.Locale

/**
 * 登录快速判错校验值（ADR-002 §3：password_hash 不落第二趟 KDF，存 KEK 派生校验值
 * SHA-256(KEK ‖ "verify")，用于避免把 GCM 认证当唯一判错路径时的语义负担）。
 * 列：accounts.password_hash（data-model §2.1）。
 */
object KekVerify {

    private const val SUFFIX = "verify"

    /** 计算存储值（64 位 hex 小写）。 */
    fun compute(kek: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(AesGcm.concat(kek, SUFFIX.toByteArray(Charsets.UTF_8)))
        return digest.joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 0xFF) }
    }

    /** 校验（不泄漏明文差异；判错后仍会走 GCM 解包做最终认证）。 */
    fun verify(kek: ByteArray, storedHex: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(AesGcm.concat(kek, SUFFIX.toByteArray(Charsets.UTF_8)))
        val actual = digest.joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 0xFF) }
        return MessageDigest.isEqual(actual.toByteArray(Charsets.US_ASCII), storedHex.toByteArray(Charsets.US_ASCII))
    }
}
