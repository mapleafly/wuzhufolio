package com.wuzhufolio.data.settings

import com.wuzhufolio.data.db.WzDatabase
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

/** settings 表映射（DDL 由迁移管理，见 M001；此处仅作查询映射）。 */
object SettingsTable : Table("settings") {
    val key = varchar("key", 128)
    val accountId = text("account_id").nullable()
    val value = text("value")
    val updatedAt = varchar("updated_at", 40)
}

/**
 * settings 读写（PRD §7.2 模块 6；全局行 account_id 为 NULL）。
 * M0 范围：全局设置读写；账户级设置随 M2/M10 扩展同一入口。
 */
class SettingsRepository(database: WzDatabase) {

    private val db = database.exposed

    /** 读取全部全局设置（key -> value）。 */
    fun getAllGlobal(): Map<String, String> = transaction(db) {
        SettingsTable.selectAll()
            .where { SettingsTable.accountId.isNull() }
            .associate { it[SettingsTable.key] to it[SettingsTable.value] }
    }

    /** 读取单个全局设置，不存在返回 null。 */
    fun getGlobal(key: String): String? = transaction(db) {
        SettingsTable.selectAll()
            .where { (SettingsTable.key eq key) and SettingsTable.accountId.isNull() }
            .singleOrNull()
            ?.get(SettingsTable.value)
    }

    /** 写入/更新单个全局设置（先查后写，保持 COALESCE 唯一索引语义）。 */
    fun putGlobal(key: String, value: String) = transaction(db) {
        val existing = SettingsTable.selectAll()
            .where { (SettingsTable.key eq key) and SettingsTable.accountId.isNull() }
            .singleOrNull()
        val now = Instant.now().toString()
        if (existing == null) {
            SettingsTable.insert {
                it[SettingsTable.key] = key
                it[accountId] = null
                it[SettingsTable.value] = value
                it[updatedAt] = now
            }
        } else {
            SettingsTable.update({ (SettingsTable.key eq key) and SettingsTable.accountId.isNull() }) {
                it[SettingsTable.value] = value
                it[updatedAt] = now
            }
        }
    }
}
