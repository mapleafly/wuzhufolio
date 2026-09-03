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

/**
 * M004：coins 币种目录表（data-model §2.9 / PRD §10-10；全局公共表，不随账户备份导出）。
 * 比 data-model 增列 contracts（各链合约地址 JSON）——桌面端勘误登记见模块记录 M3.md §5：
 * PRD §10-10 注「含各链合约地址缓存」+ 消歧规则②（合约地址精确匹配）需要，移动端 SRD coins.contracts 同源。
 */
object M004CreateCoins : Migration {
    override val version = 4
    override val description = "create coins table"

    override fun migrate(connection: Connection) {
        connection.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE coins (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    cg_id TEXT NOT NULL,
                    cmc_id TEXT,
                    symbol TEXT NOT NULL,
                    name TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    display_precision INTEGER NOT NULL DEFAULT 8,
                    contracts TEXT NOT NULL DEFAULT '{}',
                    updated_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
            st.executeUpdate("CREATE UNIQUE INDEX idx_coins_cg_id ON coins(cg_id)")
            st.executeUpdate("CREATE INDEX idx_coins_symbol ON coins(symbol)")
            st.executeUpdate("CREATE INDEX idx_coins_name ON coins(name)")
        }
    }
}

/**
 * M005：exchange_coin_map 交易所资产映射表（data-model §2.10 / PRD §10-11；全局公共表）。
 * 唯一约束 (exchange, exchange_asset)；source = AUTO（自动消歧）/ MANUAL（用户确认固化，一次性决策）。
 */
object M005CreateExchangeCoinMap : Migration {
    override val version = 5
    override val description = "create exchange_coin_map table"

    override fun migrate(connection: Connection) {
        connection.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE exchange_coin_map (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    exchange TEXT NOT NULL,
                    exchange_asset TEXT NOT NULL,
                    coin_id INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY (coin_id) REFERENCES coins(id)
                )
                """.trimIndent(),
            )
            st.executeUpdate(
                "CREATE UNIQUE INDEX idx_exchange_coin_map_key ON exchange_coin_map(exchange, exchange_asset)",
            )
            st.executeUpdate("CREATE INDEX idx_exchange_coin_map_coin ON exchange_coin_map(coin_id)")
        }
    }
}

/** 全部迁移，按版本升序登记。新迁移只追加、不改历史。 */
val ALL_MIGRATIONS: List<Migration> = listOf(
    M001CreateSettings,
    M002SeedDefaultSettings,
    M003CreateAccounts,
    M004CreateCoins,
    M005CreateExchangeCoinMap,
)
