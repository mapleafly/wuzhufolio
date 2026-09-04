package com.wuzhufolio.domain.security

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * 设备级秘密封装（M5 · ADR-002 §2.1 方案甲：行情平台 Key 等**应用级秘密**用与 DB 密钥同级的
 * 「设备密钥」（随机 256-bit，OS 钥匙串条目 device.key，降级同 DB 密钥口径）加密；
 * 密文存 settings 全局行、**不进 .cpro 备份**（恢复后重新配置）。
 *
 * 存储形态 v1 + base64(nonce‖ciphertext‖tag)；AAD = "device-secret:" + purpose（CG/CMC 密钥条目隔离，
 * 防止密文跨用途搬移）；认证失败抛 [AuthenticationFailedException]（错钥/篡改），格式损坏抛 [IllegalArgumentException]。
 */
object DeviceSecretCipher {

    private const val FORMAT_VERSION = "v1"

    fun seal(deviceKey: ByteArray, plaintext: String, purpose: String): String {
        require(deviceKey.size == 32) { "device key must be 32 bytes" }
        val sealed = AesGcm.encrypt(
            deviceKey,
            plaintext.toByteArray(StandardCharsets.UTF_8),
            aadOf(purpose),
        )
        val payload = AesGcm.concat(sealed.nonce, sealed.body)
        return FORMAT_VERSION + Base64.getEncoder().encodeToString(payload)
    }

    /** 解密认证（错钥/篡改 → [AuthenticationFailedException]；非本格式 → [IllegalArgumentException]）。 */
    fun open(sealedText: String, deviceKey: ByteArray, purpose: String): String {
        require(deviceKey.size == 32) { "device key must be 32 bytes" }
        require(sealedText.startsWith(FORMAT_VERSION)) { "unsupported device secret format: " + sealedText.take(2) }
        val raw = Base64.getDecoder().decode(sealedText.removePrefix(FORMAT_VERSION))
        require(raw.size >= AesGcm.NONCE_BYTES + AesGcm.TAG_BYTES) { "device secret payload too short" }
        val nonce = raw.copyOfRange(0, AesGcm.NONCE_BYTES)
        val body = raw.copyOfRange(AesGcm.NONCE_BYTES, raw.size)
        val plain = AesGcm.decrypt(deviceKey, nonce, body, aadOf(purpose))
        return String(plain, StandardCharsets.UTF_8)
    }

    private fun aadOf(purpose: String): ByteArray =
        ("device-secret:" + purpose).toByteArray(StandardCharsets.UTF_8)
}
