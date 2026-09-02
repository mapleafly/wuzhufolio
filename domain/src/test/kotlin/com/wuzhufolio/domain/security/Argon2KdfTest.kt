package com.wuzhufolio.domain.security

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** T1.2/Argon2id：派生确定性、盐敏感、输出长度、非法输入（盐长/空密码/算法）。 */
class Argon2KdfTest {

    private fun salt(): ByteArray = ByteArray(Argon2Kdf.SALT_BYTES).also { SecureRandom().nextBytes(it) }

    @Test
    fun `derives 32 bytes deterministically with same salt and password`() {
        val salt = salt()
        val password = "pass-phrase-质保-1".toCharArray()
        try {
            val a = Argon2Kdf.deriveKek(password, salt, KdfParams.DEFAULT)
            val b = Argon2Kdf.deriveKek(password, salt, KdfParams.DEFAULT)
            assertEquals(32, a.size)
            assertContentEquals(a, b)
            Zeroization.wipe(a, b)
        } finally {
            Zeroization.wipe(password)
        }
    }

    @Test
    fun `different salt or different password yield different kek`() {
        val password = "correct horse battery staple".toCharArray()
        try {
            val kek1 = Argon2Kdf.deriveKek(password, salt(), KdfParams.DEFAULT)
            val kek2 = Argon2Kdf.deriveKek(password, salt(), KdfParams.DEFAULT)
            val kek3 = Argon2Kdf.deriveKek("another password".toCharArray(), salt(), KdfParams.DEFAULT)
            assertFalse(kek1.contentEquals(kek2))
            assertFalse(kek1.contentEquals(kek3))
            Zeroization.wipe(kek1, kek2, kek3)
        } finally {
            Zeroization.wipe(password)
        }
    }

    @Test
    fun `utf8 multibyte passwords derive deterministically`() {
        val salt = salt()
        val password = "密码质保-🔐-pass".toCharArray()
        try {
            val a = Argon2Kdf.deriveKek(password, salt, KdfParams.OWASP_MINIMUM)
            val b = Argon2Kdf.deriveKek(password, salt, KdfParams.OWASP_MINIMUM)
            assertContentEquals(a, b)
            Zeroization.wipe(a, b)
        } finally {
            Zeroization.wipe(password)
        }
    }

    @Test
    fun `rejects wrong salt size and empty password`() {
        assertFailsWith<IllegalArgumentException> {
            Argon2Kdf.deriveKek("pw".toCharArray(), ByteArray(8), KdfParams.DEFAULT)
        }
        assertFailsWith<IllegalArgumentException> {
            Argon2Kdf.deriveKek(CharArray(0), salt(), KdfParams.DEFAULT)
        }
    }
}
