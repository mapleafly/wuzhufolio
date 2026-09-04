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

/**
 * M006：price_snapshots 价格快照表（data-model §2.11 / PRD §10-6；全局公共表，随备份打包降采样数据）。
 *
 * - 每 (coin_id, fiat) 每小时至多一条（应用层同小时取末条 upsert，经单写队列串行保证——评审 N1 口径）；
 * - price 存 TEXT（Decimal 十进制字符串）：SQLite NUMERIC 对非整小数落 REAL（IEEE 双精度，
 *   15–17 位有效数字），账本级价格精度（8 位小数 + 24h/折算推导）不允许浮点截断——TEXT 精确无损。
 *   **勘误登记：data-model §2.11 price 类型 NUMERIC → TEXT（十进制串），登记见 docs/dev/modules/M5.md §5**；
 * - recorded_at 为 UTC Instant.toString()（同全库时间口径）；降采样按 domain PriceResolution 规则执行
 *   （近 90 天小时级、更早仅整点日线行；存储层提供 compact）。
 */
object M006CreatePriceSnapshots : Migration {
    override val version = 6
    override val description = "create price_snapshots table"

    override fun migrate(connection: Connection) {
        connection.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE price_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    coin_id INTEGER NOT NULL,
                    fiat TEXT NOT NULL,
                    price TEXT NOT NULL,
                    price_source TEXT NOT NULL,
                    recorded_at TEXT NOT NULL,
                    FOREIGN KEY (coin_id) REFERENCES coins(id)
                )
                """.trimIndent(),
            )
            st.executeUpdate(
                "CREATE INDEX idx_price_snapshots_lookup ON price_snapshots(coin_id, fiat, recorded_at)",
            )
        }
    }
}

/** M007：api_keys API 密钥表（data-model §2.2 / PRD §10-2；账户级）。凭证四列（api_key/secret_key/passphrase/extra）
 * 由应用层按账户 DEK 字段级加密（FieldCipher v1，AAD=account_id|api_key_id|column——ADR-002 §2），
 * 库内只存密文；last_sync_time/status 为同步编排状态（OK/FAILED）。 */
object M007CreateApiKeys : Migration {
    override val version = 7
    override val description = "create api_keys table"

    override fun migrate(connection: Connection) {
        connection.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE api_keys (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    account_id INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    exchange_name TEXT NOT NULL,
                    api_key TEXT NOT NULL,
                    secret_key TEXT NOT NULL,
                    passphrase TEXT,
                    extra TEXT,
                    last_sync_time TEXT,
                    status TEXT NOT NULL DEFAULT 'OK',
                    FOREIGN KEY (account_id) REFERENCES accounts(id)
                )
                """.trimIndent(),
            )
            st.executeUpdate(
                "CREATE UNIQUE INDEX idx_api_keys_account_exchange_name ON api_keys(account_id, exchange_name, name)",
            )
        }
    }
}

/** M008：sync_logs 同步日志表（data-model §2.8 / PRD §10-9；账户级）。message 只允许脱敏结果（禁密钥/完整响应体）。 */
object M008CreateSyncLogs : Migration {
    override val version = 8
    override val description = "create sync_logs table"

    override fun migrate(connection: Connection) {
        connection.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE sync_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    account_id INTEGER NOT NULL,
                    api_key_id INTEGER,
                    sync_time TEXT NOT NULL,
                    status TEXT NOT NULL,
                    new_trades_count INTEGER NOT NULL DEFAULT 0,
                    message TEXT NOT NULL,
                    FOREIGN KEY (account_id) REFERENCES accounts(id),
                    FOREIGN KEY (api_key_id) REFERENCES api_keys(id)
                )
                """.trimIndent(),
            )
            st.executeUpdate("CREATE INDEX idx_sync_logs_account_time ON sync_logs(account_id, sync_time)")
        }
    }
}

/**
 * M009：transactions 交易记录表（data-model §2.5 / PRD §10-1；账户级）。
 * 记录口径（M4 引擎消费，模块记录 M4 §5）：base/quote_coin_id 冻结 coins 行 FK；price/quantity/fee 存 TEXT
 * 十进制串（SQLite NUMERIC 浮点截断风险勘误，与 M006 price TEXT 同源，见模块记录 M5 §5/M6 规格落档）；
 * 去重键 = (account_id, exchange, exchange_order_id)（exchange_order_id 非空行）——部分唯一索引防并发漏重（评审 N1）。
 */
object M009CreateTransactions : Migration {
    override val version = 9
    override val description = "create transactions table"

    override fun migrate(connection: Connection) {
        connection.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    account_id INTEGER NOT NULL,
                    exchange TEXT NOT NULL,
                    exchange_order_id TEXT,
                    pair TEXT NOT NULL,
                    base_coin_id INTEGER NOT NULL,
                    quote_coin_id INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    price TEXT NOT NULL,
                    quantity TEXT NOT NULL,
                    fee TEXT NOT NULL DEFAULT '0',
                    fee_currency TEXT,
                    transaction_time TEXT NOT NULL,
                    notes TEXT,
                    created_at TEXT NOT NULL,
                    source TEXT NOT NULL,
                    uuid TEXT NOT NULL,
                    price_status TEXT NOT NULL DEFAULT 'OK',
                    FOREIGN KEY (account_id) REFERENCES accounts(id),
                    FOREIGN KEY (base_coin_id) REFERENCES coins(id),
                    FOREIGN KEY (quote_coin_id) REFERENCES coins(id)
                )
                """.trimIndent(),
            )
            st.executeUpdate("CREATE INDEX idx_transactions_account_time ON transactions(account_id, transaction_time)")
            st.executeUpdate(
                "CREATE UNIQUE INDEX idx_transactions_dedup ON transactions(account_id, exchange, exchange_order_id)" +
                    " WHERE exchange_order_id IS NOT NULL",
            )
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
    M006CreatePriceSnapshots,
    M007CreateApiKeys,
    M008CreateSyncLogs,
    M009CreateTransactions,
)
