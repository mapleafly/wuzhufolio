package com.wuzhufolio.data.security

import com.github.javakeyring.Keyring
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** KeyringRememberMeStore 真实后端测试：无 Secret Service 环境跳过；win/mac CI 真实验证（T2.2 条目语义）。 */
class KeyringRememberMeStoreTest {

    private fun entry(accountId: Int = 7): RememberMeEntry {
        val token = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return RememberMeEntry(accountId, token, "v1" + "A".repeat(80))
    }

    @Suppress("SwallowedException") // 后端探测：不可用即跳过（与 MasterKeyStoreTest 同口径）
    private fun available(): Boolean = try {
        Keyring.create().use { true }
    } catch (e: Exception) {
        false
    }

    @Test
    fun `save load clear roundtrip through real os keyring`() {
        assumeTrue(available(), "OS keyring backend not available (skip)")
        val store = KeyringRememberMeStore()
        try {
            store.clear()
            assertNull(store.load())
            val saved = entry(accountId = 42)
            store.save(saved)
            val loaded = store.load()
            assertNotNull(loaded)
            assertEquals(42, loaded.accountId)
            assertContentEquals(saved.token, loaded.token)
            assertEquals(saved.wrappedDekSession, loaded.wrappedDekSession)
            store.clear()
            assertNull(store.load())
        } finally {
            @Suppress("SwallowedException") // 清理尽力而为
            runCatching { store.clear() }
            store.close()
        }
    }

    @Test
    fun `corrupt value surfaces typed failure not silent null`() {
        assumeTrue(available(), "OS keyring backend not available (skip)")
        val store = KeyringRememberMeStore()
        try {
            store.clear()
            store.save(entry(accountId = 9))
            // 直接覆写为损坏文本，load 必须抛而非静默返回 null
            val ring = Keyring.create()
            ring.use { it.setPassword(KeychainAccounts.SERVICE, "remember-me", "broken!!") }
            kotlin.test.assertFailsWith<IllegalStateException> { store.load() }
        } finally {
            runCatching { store.clear() }
            store.close()
        }
    }

    /**
     * parse 契约回归（全平台可跑，不依赖钥匙串后端；2026-09-08 C0 勘误）：
     * 损坏值一律抛 IllegalStateException——形状/账户/长度三处原用 require/error 抛 IAE，
     * 与 KDoc 契约不符，win/mac CI 钥匙串真实后端测试因此变红。
     */
    @Test
    fun `parse corrupt shapes throw typed ISE on every platform`() {
        val token = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32))
        val shortToken = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(16))
        // 坏形状（无分隔符 / 段数不足）
        kotlin.test.assertFailsWith<IllegalStateException> { KeyringRememberMeStore.parse("broken!!") }
        // 坏账户（非数字）
        kotlin.test.assertFailsWith<IllegalStateException> { KeyringRememberMeStore.parse("zz|" + token + "|wrapped") }
        // token 长度不符
        kotlin.test.assertFailsWith<IllegalStateException> { KeyringRememberMeStore.parse("9|" + shortToken + "|wrapped") }
        // 合法值可正常解析
        val ok = KeyringRememberMeStore.parse("9|" + token + "|wrapped")
        assertEquals(9, ok.accountId)
        assertEquals(32, ok.token.size)
        assertEquals("wrapped", ok.wrappedDekSession)
    }
}
