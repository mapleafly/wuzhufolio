package com.wuzhufolio.data.security

import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64

/**
 * 「记住我」会话条目（ADR-002 §3）：令牌 = 随机 256-bit（与密码无推导关系），
 * wrapped_dek_session = AES-256-GCM(DEK, key=token, AAD=account_id+"session")——恢复全程不触密码。
 * 条目经设备级安全存储（OS 钥匙串；降级 = 0600 本地文件，与 DB 密钥降级同口径）。
 */
data class RememberMeEntry(
    val accountId: Int,
    val token: ByteArray,
    val wrappedDekSession: String,
) {
    fun wipe() {
        token.fill(0)
    }
}

/** 会话条目存取抽象（M2 T2.2；登出/改密/切换即 clear，ADR-002 §3）。 */
interface RememberMeStore : AutoCloseable {
    /** 读取当前条目；无条目返回 null；后端不可用抛 [KeychainUnavailableException]。 */
    fun load(): RememberMeEntry?

    /** 保存（覆盖）当前条目。 */
    fun save(entry: RememberMeEntry)

    /** 清除（登出/改密/切换）。 */
    fun clear()

    override fun close() = Unit
}

/**
 * 钥匙串实现：单条目（account="remember-me"），值 = 长度前缀无歧义文本：
 * accountId + "|" + base64url(token) + "|" + wrapped（wrapped 为标准 base64，不含 "|"）。
 * 缺失 vs 不可用：与 KeychainMasterKeyStore 同口径探针（写-读-删往返）。
 */
@Suppress("SwallowedException") // 探针/缺失语义：读删失败交探针往返判定，判定过程吞异常属设计
class KeyringRememberMeStore(
    private val service: String = KeychainAccounts.SERVICE,
    private val account: String = REMEMBER_ME_ACCOUNT,
) : RememberMeStore {

    private val lock = Any()
    private var ring: com.github.javakeyring.Keyring? = null

    override fun load(): RememberMeEntry? {
        val keyring = keyring()
        val value = try {
            keyring.getPassword(service, account)
        } catch (e: Exception) {
            null
        }
        if (value != null) return parse(value)
        if (!backendHealthy(keyring)) {
            throw KeychainUnavailableException("os keyring read failed and probe roundtrip failed (remember-me)", null)
        }
        return null
    }

    override fun save(entry: RememberMeEntry) {
        keyring().setPassword(service, account, encode(entry))
    }

    override fun clear() {
        val keyring = keyring()
        try {
            keyring.deletePassword(service, account)
        } catch (e: Exception) {
            // 条目缺失（多数情况）与后端故障同抛：探针往返判定，缺失视为已清除
            if (!backendHealthy(keyring)) {
                throw KeychainUnavailableException("os keyring delete failed (remember-me): " + e.message, e)
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { ring?.close() }
            ring = null
        }
    }

    private fun keyring(): com.github.javakeyring.Keyring = synchronized(lock) {
        ring ?: try {
            com.github.javakeyring.Keyring.create().also { ring = it }
        } catch (e: Exception) {
            throw KeychainUnavailableException("os keyring not supported: " + e.message, e)
        }
    }

    @Suppress("SwallowedException") // 探针语义：任一环节失败即判定后端不可用
    private fun backendHealthy(keyring: com.github.javakeyring.Keyring): Boolean {
        val token = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val value = Base64.getEncoder().encodeToString(token)
        return try {
            keyring.setPassword(service, PROBE_ACCOUNT, value)
            val back = keyring.getPassword(service, PROBE_ACCOUNT)
            val ok = back == value
            if (ok) keyring.deletePassword(service, PROBE_ACCOUNT)
            ok
        } catch (e: Exception) {
            false
        } finally {
            token.fill(0)
        }
    }

    companion object {
        private const val REMEMBER_ME_ACCOUNT = "remember-me"
        private const val PROBE_ACCOUNT = "remember-me.probe"

        /** 序列化：accountId + "|" + base64url(token) + "|" + wrapped（wrapped 标准 base64 不含 "|"）。 */
        internal fun encode(entry: RememberMeEntry): String =
            entry.accountId.toString() + "|" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(entry.token) + "|" +
                entry.wrappedDekSession

        /** 反序列化；损坏抛 IllegalStateException（不允许静默丢弃）。 */
        internal fun parse(value: String): RememberMeEntry {
            val parts = value.split("|", limit = 3)
            require(parts.size == 3) { "remember-me entry corrupt (bad shape)" }
            val accountId = parts[0].toIntOrNull()
                ?: error("remember-me entry corrupt (bad account)")
            val token = try {
                Base64.getUrlDecoder().decode(parts[1])
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("remember-me entry corrupt (bad token)", e)
            }
            require(token.size == 32) { "remember-me entry corrupt (token length)" }
            return RememberMeEntry(accountId, token, parts[2])
        }
    }
}

/** 降级实现：0600 本地文件（KeyStorageBackend.FILE_FALLBACK 时启用，随主密钥降级口径）。 */
@Suppress("SwallowedException") // 文件损坏/IO 失败原样上抛（带 cause），catch 仅为统一消息包装
class FileRememberMeStore(private val path: Path) : RememberMeStore {

    override fun load(): RememberMeEntry? {
        if (!Files.exists(path)) return null
        val text = try {
            Files.readString(path)
        } catch (e: Exception) {
            throw IllegalStateException("cannot read remember-me file: " + path, e)
        }
        return KeyringRememberMeStore.parse(text.trim())
    }

    override fun save(entry: RememberMeEntry) {
        try {
            Files.createDirectories(path.toAbsolutePath().parent)
            Files.writeString(path, KeyringRememberMeStore.encode(entry) + "\n")
        } catch (e: Exception) {
            throw IllegalStateException("cannot write remember-me file: " + path, e)
        }
        try {
            Files.setPosixFilePermissions(
                path,
                java.util.EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        } catch (e: UnsupportedOperationException) {
            // 非 POSIX 文件系统：依赖用户目录 ACL
        }
    }

    override fun clear() {
        runCatching { Files.deleteIfExists(path) }
    }
}

/** 按主密钥后端选择会话存储（钥匙串可用 → 钥匙串；降级 → 文件），与 DB 密钥同生命周期。 */
object RememberMeStoreFactory {
    fun open(backend: KeyStorageBackend, dataDir: Path, logger: Logger): RememberMeStore {
        if (backend == KeyStorageBackend.OS_KEYCHAIN) {
            return KeyringRememberMeStore()
        }
        logger.warn("remember-me degrades to protected local file (OS keyring unavailable)")
        return FileRememberMeStore(dataDir.resolve("remember-me.dat"))
    }
}
