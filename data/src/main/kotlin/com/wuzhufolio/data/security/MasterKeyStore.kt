package com.wuzhufolio.data.security

import com.github.javakeyring.Keyring
import com.wuzhufolio.domain.security.Zeroization
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64

/**
 * 主密钥存储（T1.1 + ADR-002 §2.1）：DB 整库密钥与设备密钥（行情平台 Key 等应用级秘密）同生命周期、
 * 同降级策略——条目按 service/account 命名空间隔离，值为随机 256-bit 的 Base64(44 字符)。
 */
object KeychainAccounts {
    const val SERVICE = "WuZhuFolio"

    /** 整库主密钥（T1.1）。 */
    const val DB_KEY = "db.master-key"

    /** 后端健康探针条目（区分「条目缺失」与「后端不可用」，见 KeychainMasterKeyStore）。 */
    internal const val PROBE = "backend.probe"
}

/** 密钥驻留后端：UI 依此展示降级提示（T1.1「无钥匙串降级提示」）与 P6 安全自查。 */
enum class KeyStorageBackend { OS_KEYCHAIN, FILE_FALLBACK }

/** 取钥结果。key 生命周期归调用方：用毕 Zeroization.wipe。 */
data class KeyStorageReport(val key: ByteArray, val backend: KeyStorageBackend) {
    fun wipe() {
        Zeroization.wipe(key)
    }
}

/** OS 钥匙串不可用（无 Secret Service / dbus 缺失 / 权限不足等）——触发降级路径（ADR-002 风险表）。 */
class KeychainUnavailableException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** 降级密钥文件损坏/权限受限。 */
class MasterKeyFileException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** 主密钥存取抽象（按用途分别 service/account，实例可复用）。 */
interface MasterKeyStore : AutoCloseable {

    /** 读取密钥；条目不存在返回 null；后端不可用抛 [KeychainUnavailableException]。 */
    fun load(): ByteArray?

    /** 写入（覆盖）密钥；失败抛 [KeychainUnavailableException]（钥匙串后端）或 [MasterKeyFileException]（文件后端）。 */
    fun store(key: ByteArray)

    override fun close() = Unit
}

/**
 * OS 钥匙串实现（java-keyring：Windows 凭据管理器 / macOS Keychain / Linux Secret Service）。
 *
 * 「无条目」与「后端不可用」的区分（跨后端没有统一缺失语义）：读失败后执行一次探针写-读-删往返——
 * 往返成功 = 后端健康、条目确实缺失（返回 null）；往返失败 = 后端不可用（抛 [KeychainUnavailableException]）。
 * 探针仅发生在首建路径（此后条目存在，直接读到）。
 */
class KeychainMasterKeyStore(
    private val service: String = KeychainAccounts.SERVICE,
    private val account: String,
) : MasterKeyStore {

    private val lock = Any()
    private var ring: Keyring? = null

    @Suppress("SwallowedException") // 读失败语义交由探针判定（条目缺失 vs 后端不可用），原异常进探针路径
    override fun load(): ByteArray? {
        val keyring = keyring()
        val value = try {
            keyring.getPassword(service, account)
        } catch (e: Exception) {
            null
        }
        if (value != null) return decodeSecret(value)
        if (!backendHealthy(keyring)) {
            throw KeychainUnavailableException(
                "os keyring read failed and probe roundtrip failed ($service/$account)",
                null,
            )
        }
        return null
    }

    override fun store(key: ByteArray) {
        val keyring = keyring()
        try {
            keyring.setPassword(service, account, encodeSecret(key))
        } catch (e: Exception) {
            throw KeychainUnavailableException(
                "os keyring write failed ($service/$account): " + e.message,
                e,
            )
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { ring?.close() }
            ring = null
        }
    }

    private fun keyring(): Keyring = synchronized(lock) {
        ring ?: try {
            Keyring.create().also { ring = it }
        } catch (e: Exception) {
            throw KeychainUnavailableException("os keyring not supported: " + e.message, e)
        }
    }

    @Suppress("SwallowedException") // 探针语义：任一环节失败即判定后端不可用，细节由上层提示
    private fun backendHealthy(keyring: Keyring): Boolean {
        val token = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val value = Base64.getEncoder().encodeToString(token)
        return try {
            keyring.setPassword(service, KeychainAccounts.PROBE, value)
            val back = keyring.getPassword(service, KeychainAccounts.PROBE)
            val ok = back == value
            if (ok) keyring.deletePassword(service, KeychainAccounts.PROBE)
            ok
        } catch (e: Exception) {
            false
        } finally {
            token.fill(0)
        }
    }

    private fun encodeSecret(key: ByteArray): String = Base64.getEncoder().encodeToString(key)

    private fun decodeSecret(value: String): ByteArray {
        val decoded = try {
            Base64.getDecoder().decode(value)
        } catch (e: IllegalArgumentException) {
            throw KeychainUnavailableException("os keyring entry corrupt (bad base64): " + account, e)
        }
        if (decoded.size != 32) {
            throw KeychainUnavailableException("os keyring entry corrupt (bad length): " + account, null)
        }
        return decoded
    }
}

/**
 * 降级后端：本地密钥文件（0600）。仅当 OS 钥匙串不可用时启用（ADR-002 风险表「提示用户手动保护密钥文件」），
 * 文件内容 = 64 位 hex + 换行；损坏时抛 [MasterKeyFileException]（不静默覆盖）。
 */
class FileMasterKeyStore(private val path: Path) : MasterKeyStore {

    override fun load(): ByteArray? {
        if (!Files.exists(path)) return null
        val text = try {
            Files.readString(path)
        } catch (e: Exception) {
            throw MasterKeyFileException("cannot read key file: " + path, e)
        }
        return decodeHex(text.trim())
    }

    override fun store(key: ByteArray) {
        try {
            Files.createDirectories(path.toAbsolutePath().parent)
            Files.writeString(path, encodeHex(key) + "\n")
        } catch (e: Exception) {
            throw MasterKeyFileException("cannot write key file: " + path, e)
        }
        restrictPermissions()
    }

    @Suppress("SwallowedException") // 非 POSIX 文件系统（Windows NTFS）无权限模型：按平台能力静默
    private fun restrictPermissions() {
        try {
            Files.setPosixFilePermissions(
                path,
                java.util.EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        } catch (e: UnsupportedOperationException) {
            // 非 POSIX 文件系统（如 Windows NTFS）：依赖用户目录 ACL，不阻断
        } catch (e: Exception) {
            throw MasterKeyFileException("failed to restrict key file permissions: " + path, e)
        }
    }

    private fun encodeHex(key: ByteArray): String = buildString(key.size * 2) {
        for (byte in key) {
            val v = byte.toInt() and 0xFF
            append(HEX[v ushr 4])
            append(HEX[v and 0x0F])
        }
    }

    private fun decodeHex(text: String): ByteArray {
        if (!HEX_PATTERN.matches(text)) {
            throw MasterKeyFileException("key file content is corrupt (expected 64 hex chars): " + path, null)
        }
        return ByteArray(32) { i ->
            ((Character.digit(text[i * 2], 16) shl 4) or Character.digit(text[i * 2 + 1], 16)).toByte()
        }
    }

    companion object {
        private const val HEX = "0123456789abcdef"
        private val HEX_PATTERN = Regex("^[0-9a-fA-F]{64}$")
    }
}

/**
 * 主密钥取用（T1.1）：优先 OS 钥匙串，缺失则首建；不可用则降级本地密钥文件并如实上报后端。
 * 钥匙串健康而首建写失败（只读后端等）同样转降级，绝不静默接受空密钥。
 */
object MasterKeyResolver {

    /** 随机 256-bit 主密钥。 */
    fun newKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    /** 取得（或首建）主密钥；返回值 key 的擦除由调用方负责。 */
    fun obtainMasterKey(
        primary: MasterKeyStore,
        fallback: MasterKeyStore,
        logger: Logger,
    ): KeyStorageReport = tryObtainFromKeychain(primary, logger) ?: obtainFromFallback(fallback, logger)

    /** 钥匙串路径：已有条目直接取用；健康但无条目则首建；不可用/写失败返回 null 转降级。 */
    private fun tryObtainFromKeychain(primary: MasterKeyStore, logger: Logger): KeyStorageReport? {
        val existing = try {
            primary.load()
        } catch (e: KeychainUnavailableException) {
            logger.warn("OS keyring unavailable ({}); will fall back to key file", e.message)
            null
        }
        return if (existing != null) {
            KeyStorageReport(existing, KeyStorageBackend.OS_KEYCHAIN)
        } else {
            createOnKeychain(primary, logger)
        }
    }

    private fun createOnKeychain(primary: MasterKeyStore, logger: Logger): KeyStorageReport? {
        val fresh = newKey()
        return try {
            primary.store(fresh)
            logger.info("created master key in OS keyring")
            KeyStorageReport(fresh, KeyStorageBackend.OS_KEYCHAIN)
        } catch (e: KeychainUnavailableException) {
            Zeroization.wipe(fresh)
            logger.warn("OS keyring write failed ({}); will fall back to key file", e.message)
            null
        }
    }

    /** 降级路径：本地密钥文件（0600）。损坏时抛出（引导层致命提示，不静默覆盖）。 */
    private fun obtainFromFallback(fallback: MasterKeyStore, logger: Logger): KeyStorageReport {
        val existing = fallback.load()
        if (existing != null) {
            logger.warn("master key loaded from degraded key file (OS keyring unavailable)")
            return KeyStorageReport(existing, KeyStorageBackend.FILE_FALLBACK)
        }
        val created = newKey()
        var stored = false
        try {
            fallback.store(created)
            stored = true
        } finally {
            if (!stored) Zeroization.wipe(created)
        }
        logger.warn("created degraded master key file (OS keyring unavailable); user must protect this file manually")
        return KeyStorageReport(created, KeyStorageBackend.FILE_FALLBACK)
    }
}
