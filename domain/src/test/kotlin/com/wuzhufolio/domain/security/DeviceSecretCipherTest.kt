package com.wuzhufolio.domain.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 设备级秘密封装（M5 · 方案甲：行情 Key 等应用级秘密按设备密钥加密存全局行）。 */
class DeviceSecretCipherTest {

    private val deviceKey = ByteArray(32) { it.toByte() }
    private val otherKey = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun `seal and open roundtrip`() {
        val sealed = DeviceSecretCipher.seal(deviceKey, "cg-secret-123", "market.coingecko")
        assertTrue(sealed.startsWith("v1"))
        assertEquals("cg-secret-123", DeviceSecretCipher.open(sealed, deviceKey, "market.coingecko"))
    }

    @Test
    fun `same plaintext yields different ciphertexts (random nonce)`() {
        val a = DeviceSecretCipher.seal(deviceKey, "same", "market.coingecko")
        val b = DeviceSecretCipher.seal(deviceKey, "same", "market.coingecko")
        assertNotEquals(a, b)
    }

    @Test
    fun `wrong device key fails authentication`() {
        val sealed = DeviceSecretCipher.seal(deviceKey, "secret", "market.coingecko")
        assertFailsWith<AuthenticationFailedException> {
            DeviceSecretCipher.open(sealed, otherKey, "market.coingecko")
        }
    }

    @Test
    fun `purpose mismatch fails authentication (cross placement protection)`() {
        val sealed = DeviceSecretCipher.seal(deviceKey, "secret", "market.coingecko")
        assertFailsWith<AuthenticationFailedException> {
            DeviceSecretCipher.open(sealed, deviceKey, "market.cmc")
        }
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val sealed = DeviceSecretCipher.seal(deviceKey, "secret", "market.coingecko")
        val raw = java.util.Base64.getDecoder().decode(sealed.removePrefix("v1"))
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0x01).toByte()
        val tampered = "v1" + java.util.Base64.getEncoder().encodeToString(raw)
        assertFailsWith<AuthenticationFailedException> {
            DeviceSecretCipher.open(tampered, deviceKey, "market.coingecko")
        }
    }

    @Test
    fun `bad format is rejected`() {
        assertFailsWith<IllegalArgumentException> { DeviceSecretCipher.open("x1bogus", deviceKey, "market.coingecko") }
        assertFailsWith<IllegalArgumentException> {
            DeviceSecretCipher.open("v1", deviceKey, "market.coingecko")
        }
    }
}
