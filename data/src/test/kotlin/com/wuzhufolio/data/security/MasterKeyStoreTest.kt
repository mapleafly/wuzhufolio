package com.wuzhufolio.data.security

import com.github.javakeyring.Keyring
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** FileMasterKeyStore：往返/缺失/权限 0600/损坏拒绝（不静默覆盖）。 */
class FileMasterKeyStoreTest {

    private val dir: Path = Files.createTempDirectory("wuzhufolio-keystore")
    private val path: Path = dir.resolve("master.key")

    private fun key(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @Test
    fun `store load roundtrip and missing returns null`() {
        val store = FileMasterKeyStore(path)
        assertNull(store.load(), "空库缺失应返回 null")
        val secret = key()
        store.store(secret)
        val loaded = store.load()
        assertNotNull(loaded)
        assertContentEquals(secret, loaded)
        // 文件内容为 64 位 hex + 换行（文档化格式）
        val text = Files.readString(path)
        assertEquals(65, text.length)
        assertEquals(64, text.trim().length)
    }

    @Test
    fun `overwrite replaces previous key`() {
        val store = FileMasterKeyStore(path)
        store.store(key())
        val second = key()
        store.store(second)
        assertContentEquals(second, store.load()!!)
    }

    @Suppress("SwallowedException") // Windows/NTFS 无 POSIX：按平台能力跳过
    @Test
    fun `posix permissions restricted to owner rw on posix filesystems`() {
        val store = FileMasterKeyStore(path)
        store.store(key())
        val perms = try {
            Files.getPosixFilePermissions(path)
        } catch (e: UnsupportedOperationException) {
            null // Windows/NTFS：跳过（依赖用户目录 ACL），不构成测试失败
        }
        if (perms != null) {
            assertEquals(
                setOf(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                ),
                perms,
            )
        }
    }

    @Test
    fun `corrupt key file is rejected not overwritten`() {
        Files.writeString(path, "zz-not-a-key\n")
        assertFailsWith<MasterKeyFileException> { FileMasterKeyStore(path).load() }
        assertEquals("zz-not-a-key", Files.readString(path).trim(), "损坏文件不得被静默覆盖")
    }
}

/**
 * KeychainMasterKeyStore 真实后端测试：仅在后端可用时执行（Linux 无 Secret Service / CI headless 时跳过，
 * macOS/Windows runner 上为真实验证）。区分「无条目(null)」与「不可用(异常)」的探针语义一并覆盖。
 */
class KeychainMasterKeyStoreTest {

    private fun key(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @Suppress("SwallowedException") // 后端探测：不可用即返回 false 触发跳过，非失败
    private fun available(): Boolean = try {
        Keyring.create().use { true }
    } catch (e: Exception) {
        false
    }

    @Test
    fun `roundtrip through real os keyring when available`() {
        assumeTrue(available(), "OS keyring backend not available on this machine (skip)")
        val account = "test.key." + System.nanoTime()
        val store = KeychainMasterKeyStore(KeychainAccounts.SERVICE, account)
        try {
            // 首建前：条目缺失 → null（探针写-读-删往返成功证明后端健康）
            assertNull(store.load())
            val secret = key()
            store.store(secret)
            val loaded = store.load()
            assertNotNull(loaded)
            assertContentEquals(secret, loaded)
        } finally {
            @Suppress("SwallowedException") // 清理路径尽力而为
            runCatching {
                Keyring.create().use { it.deletePassword(KeychainAccounts.SERVICE, account) }
            }
            store.close()
        }
    }

    @Test
    fun `stored value is base64 of 32 bytes and decode enforced`() {
        assumeTrue(available(), "OS keyring backend not available on this machine (skip)")
        val account = "test.key." + System.nanoTime()
        val keyring = Keyring.create()
        try {
            val secret = key()
            keyring.setPassword(KeychainAccounts.SERVICE, account, Base64.getEncoder().encodeToString(secret))
            val store = KeychainMasterKeyStore(KeychainAccounts.SERVICE, account)
            assertContentEquals(secret, store.load()!!)
        } finally {
            @Suppress("SwallowedException") // 清理路径尽力而为
            runCatching { keyring.deletePassword(KeychainAccounts.SERVICE, account) }
            keyring.close()
        }
    }
}
