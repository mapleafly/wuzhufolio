package com.wuzhufolio.domain.security

import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 会话令牌包解包（ADR-002 §3）+ KEK 校验值（password_hash 口径）。 */
class SessionAndVerifyTest {

    private val rnd = SecureRandom()

    @Test
    fun `session wrap unwrap roundtrip with token`() {
        val dek = ByteArray(32).also { rnd.nextBytes(it) }
        val token = ByteArray(32).also { rnd.nextBytes(it) }
        val wrapped = KeyWrap.wrapForSession(dek, token, "42")
        assertTrue(wrapped.startsWith("v1"), wrapped)
        val restored = KeyWrap.unwrapForSession(wrapped, token, "42")
        assertContentEquals(dek, restored)
        Zeroization.wipe(dek, token, restored)
    }

    @Test
    fun `wrong token or wrong account fails session unwrap`() {
        val dek = ByteArray(32).also { rnd.nextBytes(it) }
        val token = ByteArray(32).also { rnd.nextBytes(it) }
        val wrong = ByteArray(32).also { rnd.nextBytes(it) }
        val wrapped = KeyWrap.wrapForSession(dek, token, "42")
        assertFailsWith<AuthenticationFailedException> { KeyWrap.unwrapForSession(wrapped, wrong, "42") }
        assertFailsWith<AuthenticationFailedException> { KeyWrap.unwrapForSession(wrapped, token, "43") }
        Zeroization.wipe(dek, token, wrong)
    }

    @Test
    fun `kek verify hash roundtrip and mismatch`() {
        val kek = ByteArray(32).also { rnd.nextBytes(it) }
        val other = ByteArray(32).also { rnd.nextBytes(it) }
        val hex = KekVerify.compute(kek)
        assertEquals(64, hex.length)
        assertTrue(KekVerify.verify(kek, hex))
        assertFalse(KekVerify.verify(other, hex))
        assertFalse(KekVerify.verify(kek, "0".repeat(64)))
        // 校验值与会话包解包语义正交（快速判错 ≠ 认证）
        assertFalse(hex == KekVerify.compute(other))
        Zeroization.wipe(kek, other)
    }

    @Test
    fun `crypto facade session helpers`() {
        val service = CryptoService()
        val dek = service.newDek()
        val token = service.newSessionToken()
        try {
            val wrapped = service.wrapDekForSession(dek, token, "7")
            assertContentEquals(dek, service.unwrapDekForSession(wrapped, token, "7"))
            val hash = service.kekVerifyHash(token)
            assertTrue(service.kekVerify(token, hash))
        } finally {
            service.wipe(dek, token)
        }
    }
}
