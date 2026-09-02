package com.wuzhufolio.domain.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/** T1.2/CryptoService 门面：派生→包解包→字段级全链路 + 擦除。 */
class CryptoServiceTest {

    @Test
    fun `full account-key lifecycle`() {
        val service = CryptoService()
        val password = "V3ry-Str0ng-P@ss-质保".toCharArray()
        try {
            val salt = service.newSalt()
            assertEquals(16, salt.size)

            val dek = service.newDek()
            assertEquals(32, dek.size)

            val kek = service.deriveKek(password, salt)
            val wrapped = service.wrapDek(dek, kek, "account-1")
            val unwrapped = service.unwrapDek(wrapped, kek, "account-1")
            assertContentEquals(dek, unwrapped)
            service.wipe(dek, unwrapped)
        } finally {
            Zeroization.wipe(password)
        }
    }

    @Test
    fun `wrong password derivation cannot unwrap`() {
        val service = CryptoService()
        val salt = service.newSalt()
        val dek = service.newDek()
        val kek = service.deriveKek("real-password".toCharArray(), salt)
        val wrongKek = service.deriveKek("wrong-password".toCharArray(), salt)
        try {
            val wrapped = service.wrapDek(dek, kek, "account-1")
            assertFailsWith<AuthenticationFailedException> {
                service.unwrapDek(wrapped, wrongKek, "account-1")
            }
            service.wipe(dek)
        } finally {
            Zeroization.wipe(kek, wrongKek)
        }
    }

    @Test
    fun `field encrypt decrypt through facade`() {
        val service = CryptoService()
        val dek = service.newDek()
        try {
            val encoded = service.encryptField("api-secret-质保", dek, "acc-1", "key-9", "secret_key")
            assertEquals("api-secret-质保", service.decryptField(encoded, dek, "acc-1", "key-9", "secret_key"))
        } finally {
            service.wipe(dek)
        }
    }

    @Test
    fun `custom params derivation honours stored params`() {
        val service = CryptoService()
        val salt = service.newSalt()
        val password = "pw-1".toCharArray()
        try {
            val kekDefault = service.deriveKek(password, salt)
            val kekOwasps = service.deriveKek(password, salt, KdfParams.OWASP_MINIMUM)
            val sameStorage = KdfParams.OWASP_MINIMUM.toStorageString()
            val kekSameParams = service.deriveKek(password, salt, KdfParams.fromStorageString(sameStorage))
            assertNotEquals(kekDefault.contentToString(), kekOwasps.contentToString())
            assertContentEquals(kekOwasps, kekSameParams)
            Zeroization.wipe(kekDefault, kekOwasps, kekSameParams)
        } finally {
            Zeroization.wipe(password)
        }
    }

    @Test
    fun `wipe zeroes key material`() {
        val key = ByteArray(32) { 1 }
        CryptoService().wipe(key)
        assertEquals(0, key.sumOf { it.toInt() and 0xFF })
    }
}
