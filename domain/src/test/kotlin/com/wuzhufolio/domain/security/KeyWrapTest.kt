package com.wuzhufolio.domain.security

import java.security.SecureRandom
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T1.2 验收：包解包 / 错误密钥失败 / 密文不相等（密文不相等 = 每次随机 nonce）。 */
class KeyWrapTest {

    private val rnd = SecureRandom()

    private fun key(): ByteArray = ByteArray(32).also { rnd.nextBytes(it) }

    private fun wrappedShape(): Regex = Regex("^v1[A-Za-z0-9+/]+={0,2}$")

    @Test
    fun `wrap unwrap roundtrip restores dek`() {
        val dek = key()
        val kek = key()
        val wrapped = KeyWrap.wrap(dek, kek, "account-1")
        assertTrue(wrappedShape().matches(wrapped), wrapped)
        // v1 + base64(12 + 32 + 16 = 60 字节) → 前缀 2 + 80 = 82 字符
        assertEquals(82, wrapped.length)
        val restored = KeyWrap.unwrap(wrapped, kek, "account-1")
        assertContentEquals(dek, restored)
        Zeroization.wipe(dek, kek, restored)
    }

    @Test
    fun `wrong kek fails authentication`() {
        val dek = key()
        val kek = key()
        val wrong = key()
        val wrapped = KeyWrap.wrap(dek, kek, "account-1")
        assertFailsWith<AuthenticationFailedException> {
            KeyWrap.unwrap(wrapped, wrong, "account-1")
        }
        Zeroization.wipe(dek, kek, wrong)
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val dek = key()
        val kek = key()
        val wrapped = KeyWrap.wrap(dek, kek, "account-1")
        val raw = Base64.getDecoder().decode(wrapped.removePrefix("v1"))
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0x01).toByte()
        val tampered = "v1" + Base64.getEncoder().encodeToString(raw)
        assertFailsWith<AuthenticationFailedException> {
            KeyWrap.unwrap(tampered, kek, "account-1")
        }
        Zeroization.wipe(dek, kek)
    }

    @Test
    fun `wrong account id aad fails authentication`() {
        val dek = key()
        val kek = key()
        val wrapped = KeyWrap.wrap(dek, kek, "account-1")
        assertFailsWith<AuthenticationFailedException> {
            KeyWrap.unwrap(wrapped, kek, "account-1-other")
        }
        Zeroization.wipe(dek, kek)
    }

    @Test
    fun `ciphertexts differ between wraps of the same dek`() {
        val dek = key()
        val kek = key()
        val first = KeyWrap.wrap(dek, kek, "account-1")
        val second = KeyWrap.wrap(dek, kek, "account-1")
        assertFalse(first == second, "random nonce must produce distinct ciphertexts")
        Zeroization.wipe(dek, kek)
    }

    @Test
    fun `rejects malformed input`() {
        assertFailsWith<IllegalArgumentException> { KeyWrap.unwrap("v9AAAA", key(), "a") }
        assertFailsWith<IllegalArgumentException> { KeyWrap.unwrap("v1!!!!", key(), "a") }
    }
}
