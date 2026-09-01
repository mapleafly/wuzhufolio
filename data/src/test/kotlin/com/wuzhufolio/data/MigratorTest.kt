package com.wuzhufolio.data

import com.wuzhufolio.data.db.WzDatabase
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T0.5 验收：空库初始化 + 迁移跑通 + 幂等。 */
class MigratorTest {

    private lateinit var db: WzDatabase

    @BeforeTest
    fun setUp() {
        val dir = Files.createTempDirectory("wuzhufolio-test")
        db = WzDatabase(dir.resolve("test.db"))
    }

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `fresh database migrates to latest version`() {
        assertEquals(0, db.schemaVersion())
        val version = db.migrateToLatest()
        assertEquals(2, version)
        assertEquals(2, db.schemaVersion())
    }

    @Test
    fun `migration is idempotent and seeds defaults exactly once`() {
        db.migrateToLatest()
        db.migrateToLatest()
        assertEquals(2, db.schemaVersion())
        db.connection.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM schema_version").use { rs ->
                rs.next(); assertEquals(2, rs.getInt(1))
            }
            st.executeQuery("SELECT COUNT(*) FROM settings").use { rs ->
                rs.next(); assertEquals(4, rs.getInt(1))
            }
        }
    }

    @Test
    fun `global unique index allows one null-account row per key`() {
        db.migrateToLatest()
        val violated = runCatching {
            db.connection.prepareStatement(
                "INSERT INTO settings(key, account_id, value, updated_at) VALUES ('theme', NULL, 'dark', 'now')",
            ).use { it.executeUpdate() }
        }.isFailure
        assertTrue(violated, "duplicate global row for same key must violate unique index")
    }

    @Test
    fun `account rows coexist with global rows for same key`() {
        db.migrateToLatest()
        db.connection.prepareStatement(
            "INSERT INTO settings(key, account_id, value, updated_at) VALUES ('theme', 'acc-1', 'dark', 'now')",
        ).use { it.executeUpdate() }
        db.connection.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM settings WHERE key = 'theme'").use { rs ->
                rs.next(); assertEquals(2, rs.getInt(1))
            }
        }
    }
}
