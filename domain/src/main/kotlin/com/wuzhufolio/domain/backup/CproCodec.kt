package com.wuzhufolio.domain.backup

import com.wuzhufolio.domain.security.AesGcm
import com.wuzhufolio.domain.security.Argon2Kdf
import com.wuzhufolio.domain.security.AuthenticationFailedException
import com.wuzhufolio.domain.security.KdfParams
import com.wuzhufolio.domain.security.Zeroization
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json

/**
 * .cpro 编解码器（M9 · T9.1 · ADR-005 §1/§2「明文头部 + AES-256-GCM 载荷，解密→重加密」）。
 *
 * 文件字节布局（v1）：
 * ```
 *   headerJson (UTF-8 单行 JSON)   ← 明文，恢复前仅解析此段展示摘要（PRD 故事 5.2-5）
 *   '\n'                            ← 分隔符（kotlinx JSON 转义控制字符，头部件内不会出现裸换行）
 *   nonce (12B) ‖ ciphertext‖tag   ← AES-256-GCM（AAD = 头部完整字节，防头部替换/篡改）
 * ```
 * 备份密钥 = Argon2id(备份密码, 头部 salt, 头部 m/t/p)（参数记入头部 `kdf`，与 M2 accounts.kdf_params
 * 同一 [KdfParams] 契约——KdfParams KDoc「M9 .cpro 内同样承载」口径）。派生密钥用后即擦（[Zeroization]）。
 *
 * 纯计算无 IO；文件读写归 data 层编排（DefaultBackupService）。
 */
object CproCodec {

    /** 当前格式版本（导入只接受 <= 此值；更高版本抛 UNSUPPORTED_FORMAT 提示升级）。 */
    const val FORMAT_VERSION: Int = 1

    const val CIPHER: String = "aes-256-gcm"
    const val KDF_ALG: String = "argon2id"

    private val JSON = Json {
        ignoreUnknownKeys = true // 跨小版本前向兼容：未知字段忽略（大版本仍由 format_version 分支）
        encodeDefaults = true
    }

    /** JSON 单行序列化（kotlinx 转义控制字符；防御性断言保证分隔符唯一）。 */
    private fun <T> encodeJson(serializer: kotlinx.serialization.KSerializer<T>, value: T): ByteArray {
        val text = JSON.encodeToString(serializer, value)
        require('\n' !in text && '\r' !in text) { "backup json must be single-line" }
        return text.toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * 编码完整 .cpro 字节：头部 + 分隔符 + nonce + 密文。
     * [password] 为备份文件密码（调用方负责强度校验与用后擦除）。
     */
    fun encode(header: CproHeader, payload: CproPayload, password: CharArray): ByteArray {
        val headerBytes = encodeJson(CproHeader.serializer(), header)
        val salt = unhex(header.kdf.salt, "kdf.salt")
        val params = KdfParams(
            memoryKiB = header.kdf.m,
            iterations = header.kdf.t,
            parallelism = header.kdf.p,
        )
        val key = Argon2Kdf.deriveKek(password, salt, params)
        try {
            val payloadBytes = encodeJson(CproPayload.serializer(), payload)
            val sealed = AesGcm.encrypt(key, payloadBytes, headerBytes)
            return AesGcm.concat(headerBytes, byteArrayOf(SEPARATOR), sealed.nonce, sealed.body)
        } finally {
            Zeroization.wipe(key)
        }
    }

    /** 仅解析明文头部（预览路径，无需密码）；文件不是合法 .cpro 抛 [CproDecodeException]。 */
    fun parseHeader(bytes: ByteArray): CproHeader = splitAndDecodeHeader(bytes).second

    /** 解码载荷（密码验证 + 认证解密 + JSON 解析）；失败语义见 [CproDecodeException]。 */
    fun decode(bytes: ByteArray, password: CharArray): CproPayload {
        val (headerBytes, header) = splitAndDecodeHeader(bytes)
        checkSupported(header)
        val body = bytes.copyOfRange(headerBytes.size + 1, bytes.size)
        checkBodySize(body)
        return decryptPayload(body, headerBytes, header, password)
    }

    /** 切分并解析明文头部（头部 JSON 单行 + '\n' 分隔符）。 */
    private fun splitAndDecodeHeader(bytes: ByteArray): Pair<ByteArray, CproHeader> {
        val sep = bytes.indexOf(SEPARATOR)
        if (sep <= 0) {
            throw CproDecodeException(
                CproDecodeException.Reason.MALFORMED_FILE,
                "not a .cpro file (header separator missing)",
            )
        }
        val headerBytes = bytes.copyOfRange(0, sep)
        val header = try {
            JSON.decodeFromString(CproHeader.serializer(), String(headerBytes, StandardCharsets.UTF_8))
        } catch (e: Exception) {
            throw CproDecodeException(
                CproDecodeException.Reason.MALFORMED_FILE,
                "backup header json malformed",
                e,
            )
        }
        return headerBytes to header
    }

    /** 密文长度下限（nonce + tag 至少各一段；不足 = 截断文件）。 */
    private fun checkBodySize(body: ByteArray) {
        if (body.size <= AesGcm.NONCE_BYTES + AesGcm.TAG_BYTES) {
            throw CproDecodeException(
                CproDecodeException.Reason.MALFORMED_FILE,
                "encrypted payload too short: " + body.size + " bytes",
            )
        }
    }

    /** 版本/算法支持检查（未知更高版本提示升级应用——ADR-005 §5）。 */
    private fun checkSupported(header: CproHeader) {
        if (header.formatVersion > FORMAT_VERSION) {
            throw CproDecodeException(
                CproDecodeException.Reason.UNSUPPORTED_FORMAT,
                "backup format_version " + header.formatVersion + " is newer than supported " + FORMAT_VERSION,
            )
        }
        if (header.cipher != CIPHER || header.kdf.alg != KDF_ALG) {
            throw CproDecodeException(
                CproDecodeException.Reason.UNSUPPORTED_FORMAT,
                "unsupported cipher/kdf: " + header.cipher + "/" + header.kdf.alg,
            )
        }
    }

    /** 认证解密 + JSON 解析（AAD = 头部完整字节，防头部替换）。 */
    private fun decryptPayload(
        body: ByteArray,
        headerBytes: ByteArray,
        header: CproHeader,
        password: CharArray,
    ): CproPayload {
        val salt = unhex(header.kdf.salt, "kdf.salt")
        val params = KdfParams(
            memoryKiB = header.kdf.m,
            iterations = header.kdf.t,
            parallelism = header.kdf.p,
        )
        val key = Argon2Kdf.deriveKek(password, salt, params)
        try {
            val nonce = body.copyOfRange(0, AesGcm.NONCE_BYTES)
            val ciphertext = body.copyOfRange(AesGcm.NONCE_BYTES, body.size)
            val plaintext = try {
                AesGcm.decrypt(key, nonce, ciphertext, headerBytes)
            } catch (e: AuthenticationFailedException) {
                throw CproDecodeException(
                    CproDecodeException.Reason.WRONG_PASSWORD_OR_CORRUPTED,
                    "backup authentication failed: wrong password or corrupted file",
                    e,
                )
            }
            return try {
                JSON.decodeFromString(CproPayload.serializer(), String(plaintext, StandardCharsets.UTF_8))
            } catch (e: Exception) {
                throw CproDecodeException(
                    CproDecodeException.Reason.MALFORMED_FILE,
                    "payload json malformed after decryption",
                    e,
                )
            }
        } finally {
            Zeroization.wipe(key)
        }
    }

    private fun unhex(text: String, field: String): ByteArray {
        val clean = text.trim()
        if (clean.length % 2 != 0 || clean.isEmpty()) {
            throw CproDecodeException(
                CproDecodeException.Reason.MALFORMED_FILE,
                "malformed hex field: " + field,
            )
        }
        return try {
            clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: NumberFormatException) {
            throw CproDecodeException(
                CproDecodeException.Reason.MALFORMED_FILE,
                "malformed hex field: " + field,
                e,
            )
        }
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private const val SEPARATOR: Byte = '\n'.code.toByte()
}
