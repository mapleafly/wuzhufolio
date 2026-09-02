package com.wuzhufolio.data.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.sqlite.SQLiteConfig
import org.sqlite.mc.SQLiteMCSqlCipherConfig
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties

/**
 * 应用数据库（单库多账户，PRD §10 注）——M1 T1.1 起为 SQLCipher 整库加密（ADR-002）。
 *
 * 密钥模型：
 * - 随机 256-bit 库密钥，由调用方（生产 = OS 钥匙串，见 data.security.MasterKeyResolver）取得后传入；
 * - **raw-key 注入**：32 字节经 SQLiteMCSqlCipherConfig.withRawUnsaltedKey 编码为 x'hex'（SQLCipher 4 默认
 *   参数 + raw key 模式，跳过 SQLCipher 自带 KDF，ADR-002 备注），密钥随连接属性注入（评审 N4：驱动触碰
 *   数据库后再执行 PRAGMA key 会失败）；每个新连接（含 Exposed 取连接工厂）都带同一密钥。
 * - 本类不负责密钥落盘/擦除；调用方在构造完成后擦除传入数组（Zeroization）。
 *
 * 连接模型（ADR-002 §4 / PRD §10 注）：
 * - [connection]：共享写连接（迁移执行器使用，boot 期单线程）；
 * - [exposed]：Exposed 入口，每次取连接均新建（读事务 + DbGate 单写队列写事务）。
 * - WAL + busy_timeout(5000) + foreign_keys=ON 经连接属性注入；WAL 生效性由 SqlCipherDatabaseTest 守护。
 */
class WzDatabase(
    dbPath: Path,
    masterKey: ByteArray,
    private val migrations: List<Migration> = ALL_MIGRATIONS,
) : Closeable {

    private val jdbcUrl: String
    private val connectionProps: Properties

    /** 共享写连接（迁移执行器；boot 期外不直接写，写一律走 DbGate）。 */
    val connection: Connection

    /** Exposed 访问入口（查询/DAO 层，新连接工厂，每连接均带库密钥）。 */
    val exposed: Database

    init {
        require(masterKey.size == DB_KEY_BYTES) {
            "database master key must be exactly $DB_KEY_BYTES bytes (got " + masterKey.size + ")"
        }
        Files.createDirectories(dbPath.toAbsolutePath().parent)
        jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath()
        connectionProps = sqlCipherProperties(masterKey)
        connection = try {
            DriverManager.getConnection(jdbcUrl, connectionProps)
        } catch (e: SQLException) {
            throw mapOpenFailure(dbPath, e)
        }
        exposed = Database.connect({ DriverManager.getConnection(jdbcUrl, connectionProps) })
    }

    /** 应用全部待执行迁移，返回最终 schema 版本（boot 期单线程执行，不经 DbGate）。 */
    fun migrateToLatest(): Int = Migrator(migrations).migrateToLatest(connection)

    /** 当前 schema 版本。 */
    fun schemaVersion(): Int = Migrator(migrations).currentVersion(connection)

    override fun close() {
        runCatching { connection.close() }
    }

    companion object {
        const val DB_KEY_BYTES = 32
        const val BUSY_TIMEOUT_MS = 5000

        /**
         * SQLCipher 连接属性（T1.1 实证，见模块记录 M1.md）：
         * SQLiteMCSqlCipherConfig.getDefault() = SQLCipher 4 参数（kdf_iter 256000/hmac SHA512/页 4096 等），
         * raw 256-bit key 免 KDF；busy_timeout/journal_mode(WAL)/foreign_keys 随其余 pragma 应用。
         */
        fun sqlCipherProperties(masterKey: ByteArray): Properties {
            val config = SQLiteMCSqlCipherConfig.getDefault()
                .withRawUnsaltedKey(masterKey)
                .build()
            config.setBusyTimeout(BUSY_TIMEOUT_MS)
            config.enforceForeignKeys(true)
            config.setJournalMode(SQLiteConfig.JournalMode.WAL)
            return config.toProperties()
        }

        private fun mapOpenFailure(dbPath: Path, e: SQLException): RuntimeException {
            val message = e.message.orEmpty()
            return if (message.contains("not a database")) {
                DatabaseKeyMismatchException(dbPath, e)
            } else {
                DatabaseOpenException(dbPath, e)
            }
        }
    }
}
