package com.wuzhufolio.domain.security

import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T1.2/字段级：往返、AAD 列隔离（账户/条目/列任一不符即认证失败）、密文不相等（随机 nonce）。 */
class FieldCipherTest {

    private val dek: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    companion object {
        private const val SAMPLE =
            "wR3VvZoU6Egh8KzMmm7pFQx2BbNcYdXq4JjU9Tn0PkLsA1IwS2hG5fD7cV9bN3mEaZ8yCx4vBnM6"
    }

    @Test
    fun `roundtrip preserves plaintext`() {
        val encoded = FieldCipher.encrypt(SAMPLE, dek, "acc-1", "key-7", "secret_key")
        assertTrue(encoded.startsWith("v1"), encoded)
        val restored = FieldCipher.decrypt(encoded, dek, "acc-1", "key-7", "secret_key")
        assertEquals(SAMPLE, restored)
    }

    @Test
    fun `empty plaintext roundtrips`() {
        val encoded = FieldCipher.encrypt("", dek, "acc-1", "key-7", "passphrase")
        assertEquals("", FieldCipher.decrypt(encoded, dek, "acc-1", "key-7", "passphrase"))
    }

    @Test
    fun `aad isolates account apiKey and column`() {
        val encoded = FieldCipher.encrypt(SAMPLE, dek, "acc-1", "key-7", "api_key")
        assertFailsWith<AuthenticationFailedException> {
            FieldCipher.decrypt(encoded, dek, "acc-2", "key-7", "api_key")
        }
        assertFailsWith<AuthenticationFailedException> {
            FieldCipher.decrypt(encoded, dek, "acc-1", "key-8", "api_key")
        }
        assertFailsWith<AuthenticationFailedException> {
            FieldCipher.decrypt(encoded, dek, "acc-1", "key-7", "secret_key")
        }
    }

    @Test
    fun `ciphertexts differ per encryption of same plaintext`() {
        val a = FieldCipher.encrypt(SAMPLE, dek, "acc-1", "key-7", "api_key")
        val b = FieldCipher.encrypt(SAMPLE, dek, "acc-1", "key-7", "api_key")
        assertFalse(a == b, "random nonce required")
    }

    @Test
    fun `wrong dek fails`() {
        val wrong = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val encoded = FieldCipher.encrypt(SAMPLE, dek, "acc-1", "key-7", "extra")
        assertFailsWith<AuthenticationFailedException> {
            FieldCipher.decrypt(encoded, wrong, "acc-1", "key-7", "extra")
        }
    }

    @Test
    fun `rejects malformed payloads`() {
        val encoded = FieldCipher.encrypt(SAMPLE, dek, "acc-1", "key-7", "extra")
        // 前缀版本不识别（结构损坏 → IllegalArgumentException）
        assertFailsWith<IllegalArgumentException> {
            FieldCipher.decrypt("v9" + encoded.drop(2), dek, "acc-1", "key-7", "extra")
        }
        // 载荷长度不足（结构损坏 → IllegalArgumentException）
        assertFailsWith<IllegalArgumentException> {
            FieldCipher.decrypt("v1AQ==", dek, "acc-1", "key-7", "extra")
        }
        // 密文被改（结构完整但 GCM tag 校验失败 → AuthenticationFailedException）
        val raw = java.util.Base64.getDecoder().decode(encoded.drop(2))
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 1).toByte()
        val tampered = "v1" + java.util.Base64.getEncoder().encodeToString(raw)
        assertFailsWith<AuthenticationFailedException> {
            FieldCipher.decrypt(tampered, dek, "acc-1", "key-7", "extra")
        }
    }
}
