package com.wuzhufolio.data.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.sqlite.SQLiteConfig
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * 应用数据库（单库多账户，PRD §10 注）。
 *
 * M0 口径：xerial 明文 SQLite + WAL + busy_timeout + foreign_keys，供迁移框架与 hello 链路使用；
 * M1 T1.1 切换为 Willena SQLCipher 驱动（整库加密、密钥经连接属性注入，ADR-002），本类是唯一切换点。
 *
 * 连接模型（ADR-002 §4 读写分离的 M0 雏形）：
 * - [connection]：共享写连接，迁移执行器使用（M1 起由单写队列 Mutex 串行化）；
 * - [exposed]：Exposed 访问入口，使用**新连接工厂**——Exposed 的 metadata() 在无事务上下文会
 *   调用 connector() 取连接并在 finally 中 close，传入共享单连接会被它关闭（M0 实测踩坑记录）。
 */
class WzDatabase(dbPath: Path, private val migrations: List<Migration> = ALL_MIGRATIONS) : Closeable {

    private val jdbcUrl: String
    private val connectionProps: java.util.Properties

    /** 共享写连接（迁移执行器使用；M1 起同时是单写队列的写连接）。 */
    val connection: Connection

    /** Exposed 访问入口（查询/DAO 层使用，每次取连接都是新连接）。 */
    val exposed: Database

    init {
        Files.createDirectories(dbPath.toAbsolutePath().parent)
        jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath()
        connectionProps = SQLiteConfig().apply {
            setBusyTimeout(BUSY_TIMEOUT_MS)
            enforceForeignKeys(true)
            setJournalMode(SQLiteConfig.JournalMode.WAL)
        }.toProperties()
        connection = DriverManager.getConnection(jdbcUrl, connectionProps)
        exposed = Database.connect({ DriverManager.getConnection(jdbcUrl, connectionProps) })
    }

    /** 应用全部待执行迁移，返回最终 schema 版本。 */
    fun migrateToLatest(): Int = Migrator(migrations).migrateToLatest(connection)

    /** 当前 schema 版本。 */
    fun schemaVersion(): Int = Migrator(migrations).currentVersion(connection)

    override fun close() {
        runCatching { connection.close() }
    }

    companion object {
        const val BUSY_TIMEOUT_MS = 5000
    }
}
