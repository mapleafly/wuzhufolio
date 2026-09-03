package com.wuzhufolio.data.db

import java.sql.Connection
import java.time.Instant

/**
 * M001：settings 键值表（PRD §10 settings：全局行 account_id 为 NULL，账户行按 account_id 隔离）。
 * 唯一约束经 COALESCE 表达式索引实现（评审 N1），使 (key, NULL) 与 (key, account) 均唯一。
 */
object M001CreateSettings : Migration {
    override val version = 1
    override val description = "create settings table"

    override fun migrate(connection: Connection) {
        connection.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE settings (
                    key TEXT NOT NULL,
                    account_id TEXT,
                    value TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
            st.executeUpdate(
                "CREATE UNIQUE INDEX idx_settings_key_account ON settings(key, COALESCE(account_id, ''))",
            )
        }
    }
}

/** M002：写入全局默认设置（主题/基础法币/语言/盈亏配色），幂等可重入。 */
object M002SeedDefaultSettings : Migration {
    override val version = 2
    override val description = "seed default global settings"

    private val defaults = linkedMapOf(
        "theme" to "light",
        "fiat" to "USD",
        "locale" to "zh-CN",
        "pnl_scheme" to "green_up",
    )

    override fun migrate(connection: Connection) {
        val now = Instant.now().toString()
        connection.prepareStatement(
            "INSERT INTO settings(key, account_id, value, updated_at) " +
                "SELECT ?, NULL, ?, ? " +
                "WHERE NOT EXISTS (SELECT 1 FROM settings WHERE key = ? AND account_id IS NULL)",
        ).use { ps ->
            for ((k, v) in defaults) {
                ps.setString(1, k)
                ps.setString(2, v)
                ps.setString(3, now)
                ps.setString(4, k)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }
}

/**
 * M003：accounts 账户表（data-model §2.1 / PRD §10-4）。
 * 密码哈希与 KDF 参数随行存储；凭证字段不在此表（api_keys 按账户 DEK 字段级加密，M6 建表）。
 */
object M003CreateAccounts : Migration {
    override val version = 3
    override val description = "create accounts table"

    override fun migrate(connection: Connection) {
        connection.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE accounts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL,
                    password_hash TEXT NOT NULL,
                    kdf_salt TEXT NOT NULL,
                    kdf_params TEXT NOT NULL,
                    wrapped_dek TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
            st.executeUpdate("CREATE UNIQUE INDEX idx_accounts_username ON accounts(username)")
        }
    }
}

/** 全部迁移，按版本升序登记。新迁移只追加、不改历史。 */
val ALL_MIGRATIONS: List<Migration> = listOf(
    M001CreateSettings,
    M002SeedDefaultSettings,
    M003CreateAccounts,
)
