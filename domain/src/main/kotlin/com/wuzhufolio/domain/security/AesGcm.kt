package com.wuzhufolio.domain.security

import java.security.SecureRandom
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM 认证加密原语（ADR-002 §2；PRD 故事 5.1-3「防篡改采用认证加密（GCM）」）。
 * JDK javax.crypto；96-bit 随机 nonce；JDK GCM 输出形态 = 密文 ‖ 16 字节 tag。
 * 本对象不感知业务格式（格式/版本/AAD 由 KeyWrap/FieldCipher 定义）。
 */
internal object AesGcm {

    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    const val TAG_BYTES = 16
    const val TAG_BITS = TAG_BYTES * 8

    /** nonce 与 密文‖tag 载荷。 */
    data class Sealed(val nonce: ByteArray, val body: ByteArray)

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray): Sealed {
        require(key.size == KEY_BYTES) { "AES-256 key must be exactly $KEY_BYTES bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return Sealed(nonce, cipher.doFinal(plaintext))
    }

    /** 解密并认证。失败（错钥/AAD 不符/篡改）抛 [AuthenticationFailedException]。 */
    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 key must be exactly $KEY_BYTES bytes" }
        require(nonce.size == NONCE_BYTES) { "GCM nonce must be $NONCE_BYTES bytes" }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(aad)
            cipher.doFinal(ciphertextAndTag)
        } catch (e: GeneralSecurityException) {
            throw AuthenticationFailedException(
                "AES-GCM authentication failed: wrong key, AAD mismatch or tampered ciphertext", e,
            )
        }
    }

    /** 按序拼接字节数组。 */
    fun concat(vararg parts: ByteArray): ByteArray {
        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var offset = 0
        for (part in parts) {
            part.copyInto(out, offset)
            offset += part.size
        }
        return out
    }
}
