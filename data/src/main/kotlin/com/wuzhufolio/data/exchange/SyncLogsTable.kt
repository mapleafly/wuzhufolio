package com.wuzhufolio.data.exchange

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.domain.exchange.SyncLogRow
import com.wuzhufolio.domain.exchange.SyncStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant

/**
 * sync_logs 表映射（DDL = M008；data-model §2.8 / PRD §10-9）。message 只允许脱敏结果
 * （PRD §6：禁密钥明文/哈希与完整响应体）——写入前经 LogRedactor.redact 兜底 + 服务层只拼计数摘要。
 */
object SyncLogsTable : Table("sync_logs") {
    val id = integer("id").autoIncrement()
    val accountId = integer("account_id")
    val apiKeyId = integer("api_key_id").nullable()
    val syncTime = varchar("sync_time", 40)
    val status = varchar("status", 16)
    val newTradesCount = integer("new_trades_count")
    val message = text("message")

    override val primaryKey = PrimaryKey(id)
}

/** 同步日志持久化（T6.3；轮转 1 万条或 90 天随 M10 日志轮转统一执行，本模块只写/读）。 */
class SyncLogRepository(private val gate: DbGate) {

    fun append(
        accountId: Int,
        apiKeyId: Int?,
        status: SyncStatus,
        newTradesCount: Int,
        message: String,
    ) {
        gate.writeBlocking {
            SyncLogsTable.insert {
                it[SyncLogsTable.accountId] = accountId
                it[SyncLogsTable.apiKeyId] = apiKeyId
                it[SyncLogsTable.syncTime] = Instant.now().toString()
                it[SyncLogsTable.status] = status.storageValue
                it[SyncLogsTable.newTradesCount] = newTradesCount
                it[SyncLogsTable.message] = message
            }
        }
    }

    /** 最近 [limit] 条（倒序；API 管理页「同步记录展示」）。 */
    fun recent(accountId: Int, limit: Int): List<SyncLogRow> = gate.readBlocking {
        SyncLogsTable.selectAll()
            .where { SyncLogsTable.accountId eq accountId }
            .orderBy(SyncLogsTable.syncTime to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                SyncLogRow(
                    id = row[SyncLogsTable.id].toLong(),
                    accountId = row[SyncLogsTable.accountId].toLong(),
                    apiKeyId = row[SyncLogsTable.apiKeyId]?.toLong(),
                    syncTime = Instant.parse(row[SyncLogsTable.syncTime]),
                    status = SyncStatus.fromStorage(row[SyncLogsTable.status]),
                    newTradesCount = row[SyncLogsTable.newTradesCount],
                    message = row[SyncLogsTable.message],
                )
            }
    }

    /** 某 key 最近同步记录（单 key 详情；倒序）。 */
    fun recentFor(accountId: Int, apiKeyId: Int, limit: Int): List<SyncLogRow> = gate.readBlocking {
        SyncLogsTable.selectAll()
            .where { (SyncLogsTable.accountId eq accountId) and (SyncLogsTable.apiKeyId eq apiKeyId) }
            .orderBy(SyncLogsTable.syncTime to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                SyncLogRow(
                    id = row[SyncLogsTable.id].toLong(),
                    accountId = row[SyncLogsTable.accountId].toLong(),
                    apiKeyId = row[SyncLogsTable.apiKeyId]?.toLong(),
                    syncTime = Instant.parse(row[SyncLogsTable.syncTime]),
                    status = SyncStatus.fromStorage(row[SyncLogsTable.status]),
                    newTradesCount = row[SyncLogsTable.newTradesCount],
                    message = row[SyncLogsTable.message],
                )
            }
    }
}
