package com.wuzhufolio.data

import com.wuzhufolio.data.db.DatabaseKeyMismatchException
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.settings.SettingsTable
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import java.util.Locale
import java.nio.file.Path
import java.sql.Connection
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T1.1 验收（SQLCipher 整库加密 + 锁版）——本测试在 CI 三平台矩阵（ubuntu/windows/macos）执行，
 * 即「三平台 SQLCipher 驱动可用性验证」：任一平台原生库缺失/不兼容会在加载或建库时失败。
 *
 * 守护项：① 文件头非明文（整库加密生效）；② 重启可解锁（同密钥重开 + 迁移幂等 + 数据仍在）；
 * ③ 错钥打开失败（类型化 DatabaseKeyMismatchException）；④ WAL/busy_timeout/foreign_keys 在新旧连接均生效；
 * ⑤ Exposed 取新连接（带密钥）读写正常。
 */
class SqlCipherDatabaseTest {

    private val dir: Path = Files.createTempDirectory("wuzhufolio-sqlcipher")
    private val dbPath: Path = dir.resolve("t1.db")

    private fun openWith(key: ByteArray = randomDbKey()): WzDatabase = WzDatabase(dbPath, key)

    @Test
    fun `fresh database is encrypted at rest and migrates to latest`() {
        val db = openWith()
        try {
            assertEquals(2, db.migrateToLatest())
            db.connection.prepareStatement(
                "INSERT INTO settings(key, account_id, value, updated_at) " +
                    "VALUES ('sqlcipher.probe.fiat', NULL, 'USD', 'now')",
            ).use { it.executeUpdate() }
        } finally {
            db.close()
        }
        val head = Files.readAllBytes(dbPath).copyOf(16)
        val headerText = String(head, Charsets.ISO_8859_1)
        assertFalse(
            headerText.startsWith("SQLite format 3"),
            "SQLCipher 加密库文件头不得为明文 SQLite 头，实际前 16 字节=" +
                head.joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt()) },
        )
    }

    @Test
    fun `restart with the same key unlocks and preserves data`() {
        val key = randomDbKey()
        val first = openWith(key)
        try {
            first.migrateToLatest()
            first.connection.prepareStatement(
                "INSERT INTO settings(key, account_id, value, updated_at) " +
                    "VALUES ('sqlcipher.probe.theme', NULL, 'dark', 'now')",
            ).use { it.executeUpdate() }
        } finally {
            first.close()
        }
        // 模拟进程重启：同一密钥重新建连接 + 全量迁移（幂等）+ 数据仍在
        val second = openWith(key)
        try {
            assertEquals(2, second.schemaVersion())
            assertEquals(2, second.migrateToLatest())
            second.connection.createStatement().use { st ->
                st.executeQuery("SELECT value FROM settings WHERE key = 'sqlcipher.probe.theme'").use { rs ->
                    rs.next()
                    assertEquals("dark", rs.getString(1))
                }
            }
        } finally {
            second.close()
        }
    }

    @Test
    fun `wrong key open fails with typed mismatch error`() {
        openWith().close() // 先用某密钥建立加密库
        val exception = assertFailsWith<DatabaseKeyMismatchException> {
            openWith(randomDbKey()).close() // 换密钥打开必须失败
        }
        assertTrue(exception.message.orEmpty().contains("unlock"), exception.message)
    }

    @Test
    fun `wal busy_timeout and foreign_keys are effective on every connection`() {
        val key = randomDbKey()
        val db = openWith(key)
        try {
            db.migrateToLatest()
            assertPragmas(db.connection)
            // 新连接（模拟 Exposed 读/写事务连接）同样带全部 pragma
            java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + dbPath.toAbsolutePath(),
                WzDatabase.sqlCipherProperties(key),
            ).use { assertPragmas(it) }
        } finally {
            db.close()
        }
    }

    private fun assertPragmas(conn: Connection) {
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA journal_mode").use { rs ->
                rs.next()
                assertEquals("wal", rs.getString(1), "journal_mode 必须为 wal（SQLCipher 下 WAL 生效性 = T1.1 锁版验证项）")
            }
            st.executeQuery("PRAGMA busy_timeout").use { rs ->
                rs.next()
                assertEquals(WzDatabase.BUSY_TIMEOUT_MS.toString(), rs.getString(1))
            }
            st.executeQuery("PRAGMA foreign_keys").use { rs ->
                rs.next()
                assertEquals("1", rs.getString(1))
            }
        }
    }

    @Test
    fun `exposed connections carry the key on fresh connections`() {
        val db = openWith()
        try {
            db.migrateToLatest()
            val count = transaction(db.exposed) {
                SettingsTable.selectAll().count()
            }
            assertTrue(count >= 4, "默认设置必须能经 Exposed 读出，实际=" + count)
        } finally {
            db.close()
        }
    }
}
