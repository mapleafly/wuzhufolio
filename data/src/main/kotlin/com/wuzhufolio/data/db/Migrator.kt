package com.wuzhufolio.data.db

import java.sql.Connection
import java.time.Instant

/**
 * 迁移执行器（T0.5）：维护 schema_version 表，按版本升序应用未执行迁移。
 * 每条迁移在独立事务内执行，失败即回滚并抛出，不允许半迁移状态。
 */
class Migrator(private val migrations: List<Migration>) {

    init {
        val versions = migrations.map { it.version }
        require(versions.distinct().size == versions.size) { "duplicate migration versions: " + versions }
        require(versions == versions.sorted()) { "migrations must be sorted by version: " + versions }
    }

    /** 当前库版本；空库为 0。 */
    fun currentVersion(connection: Connection): Int {
        ensureVersionTable(connection)
        connection.createStatement().use { st ->
            st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version").use { rs ->
                rs.next()
                return rs.getInt(1)
            }
        }
    }

    /** 应用全部待执行迁移，返回最终版本。幂等：已是最新时不做任何事。 */
    fun migrateToLatest(connection: Connection): Int {
        ensureVersionTable(connection)
        var version = currentVersion(connection)
        for (migration in migrations) {
            if (migration.version <= version) continue
            apply(connection, migration)
            version = migration.version
        }
        return version
    }

    private fun apply(connection: Connection, migration: Migration) {
        val prevAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            migration.migrate(connection)
            connection.prepareStatement(
                "INSERT INTO schema_version(version, description, applied_at) VALUES (?, ?, ?)",
            ).use { ps ->
                ps.setInt(1, migration.version)
                ps.setString(2, migration.description)
                ps.setString(3, Instant.now().toString())
                ps.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw MigrationException(migration.version, e)
        } finally {
            connection.autoCommit = prevAutoCommit
        }
    }

    private fun ensureVersionTable(connection: Connection) {
        connection.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY,
                    description TEXT NOT NULL,
                    applied_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }
}

class MigrationException(version: Int, cause: Exception) :
    RuntimeException("migration " + version + " failed: " + cause.message, cause)
