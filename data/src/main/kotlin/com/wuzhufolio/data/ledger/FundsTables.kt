package com.wuzhufolio.data.ledger

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.domain.engine.FlowKind
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * 资金流水/校准记录表映射（M8 · DDL = M011/M012；data-model §2.6/§2.7 / PRD §10-5/§10-8）。
 * 金额列 TEXT 十进制串（勘误链见迁移注记）；时间列 SqlUtc 文本；coin_id 冻结 coins 行 FK。
 */
object CapitalFlowsTable : Table("capital_flows") {
    val id = integer("id").autoIncrement()
    val accountId = integer("account_id")
    val type = varchar("type", 16)
    val amount = text("amount")
    val baseAmount = text("base_amount")
    val currency = varchar("currency", 32)
    val coinId = integer("coin_id")
    val flowTime = varchar("flow_time", 40)
    val sourceDest = text("source_dest").nullable()
    val notes = text("notes").nullable()
    val createdAt = varchar("created_at", 40)
    val uuid = varchar("uuid", 64)
    val priceStatus = varchar("price_status", 16).default("OK")

    override val primaryKey = PrimaryKey(id)
}

object ReconciliationRecordsTable : Table("reconciliation_records") {
    val id = integer("id").autoIncrement()
    val accountId = integer("account_id")
    val symbol = varchar("symbol", 32)
    val coinId = integer("coin_id")
    val exchange = varchar("exchange", 24)
    val localQuantity = text("local_quantity")
    val exchangeQuantity = text("exchange_quantity")
    val delta = text("delta")
    val baseAmount = text("base_amount")
    val uuid = varchar("uuid", 64)
    val createdAt = varchar("created_at", 40)

    override val primaryKey = PrimaryKey(id)
}

/** capital_flows 行（仓库形态；金额 TEXT 十进制串还原 BigDecimal，时间 SqlUtc/Instant）。 */
data class FundFlowRow(
    val id: Long,
    val accountId: Int,
    val kind: FlowKind,
    val amount: BigDecimal,
    /** 记录时折算快照（审计口径；引擎以事件构造层动态重建为准）。 */
    val baseAmount: BigDecimal,
    val currency: String,
    val coinId: Long,
    val time: Instant,
    val sourceDest: String?,
    val notes: String?,
    val uuid: String,
)

/** 新资金行（手动路径；uuid = 事件 id + 备份去重键）。 */
data class NewFundFlowRow(
    val accountId: Int,
    val kind: FlowKind,
    val amount: BigDecimal,
    val baseAmount: BigDecimal,
    val currency: String,
    val coinId: Int,
    val time: Instant,
    val sourceDest: String?,
    val notes: String?,
    val priceStatus: String,
) {
    val uuid: String = UUID.randomUUID().toString()
}

/** 编辑资金行（id 定位；uuid/created_at 稳定）。 */
data class UpdatedFundFlowRow(
    val id: Long,
    val accountId: Int,
    val kind: FlowKind,
    val amount: BigDecimal,
    val baseAmount: BigDecimal,
    val currency: String,
    val coinId: Int,
    val time: Instant,
    val sourceDest: String?,
    val notes: String?,
)

/** reconciliation_records 行（仓库形态）。 */
data class ReconciliationRecordRow(
    val id: Long,
    val accountId: Int,
    val symbol: String,
    val coinId: Long,
    val exchange: String,
    val localQuantity: BigDecimal,
    val exchangeQuantity: BigDecimal,
    val delta: BigDecimal,
    val baseAmount: BigDecimal,
    val uuid: String,
    val createdAt: Instant,
)

/** 新校准记录行（校准执行入库）。 */
data class NewReconciliationRow(
    val accountId: Int,
    val symbol: String,
    val coinId: Int,
    val exchange: String,
    val localQuantity: BigDecimal,
    val exchangeQuantity: BigDecimal,
    val delta: BigDecimal,
    val baseAmount: BigDecimal,
) {
    val uuid: String = UUID.randomUUID().toString()
}

/**
 * capital_flows 仓库（M8 T8.1；全部经 DbGate 单写队列（写）/WAL（读））。
 * 读取统一走 [rowOf]：金额 TEXT 精确还原、时间 SqlUtc 文本解析。
 */
class FundFlowRepository(private val gate: DbGate) {

    /** 全量读取（账户级；按 (flow_time, id) 升序——事件构造稳定排序输入）。 */
    suspend fun listAll(accountId: Int): List<FundFlowRow> = gate.read {
        CapitalFlowsTable.selectAll()
            .where { CapitalFlowsTable.accountId eq accountId }
            .toList()
            .map { it.rowOf() }
            .sortedWith(compareBy({ it.time }, { it.id }))
    }

    suspend fun findById(accountId: Int, id: Long): FundFlowRow? = gate.read {
        CapitalFlowsTable.selectAll()
            .where { (CapitalFlowsTable.accountId eq accountId) and (CapitalFlowsTable.id eq id.toInt()) }
            .singleOrNull()
    }?.rowOf()

    /** 按 uuid 批量读取（删除批次校验输入）。 */
    suspend fun findByUuids(accountId: Int, uuids: List<String>): List<FundFlowRow> {
        if (uuids.isEmpty()) return emptyList()
        return gate.read {
            CapitalFlowsTable.selectAll()
                .where {
                    (CapitalFlowsTable.accountId eq accountId) and
                        (CapitalFlowsTable.uuid inList uuids)
                }
                .toList()
        }.map { it.rowOf() }
    }

    suspend fun insert(row: NewFundFlowRow): Long = gate.write {
        CapitalFlowsTable.insert {
            it[accountId] = row.accountId
            it[type] = row.kind.name
            it[amount] = row.amount.toPlainString()
            it[baseAmount] = row.baseAmount.toPlainString()
            it[currency] = row.currency
            it[coinId] = row.coinId
            it[flowTime] = row.time.toString()
            it[sourceDest] = row.sourceDest
            it[notes] = row.notes
            it[createdAt] = Instant.now().toString()
            it[uuid] = row.uuid
            it[priceStatus] = row.priceStatus
        } get CapitalFlowsTable.id
    }.toLong()

    suspend fun update(row: UpdatedFundFlowRow) = gate.write {
        CapitalFlowsTable.update({
            (CapitalFlowsTable.accountId eq row.accountId) and (CapitalFlowsTable.id eq row.id.toInt())
        }) {
            it[type] = row.kind.name
            it[amount] = row.amount.toPlainString()
            it[baseAmount] = row.baseAmount.toPlainString()
            it[currency] = row.currency
            it[coinId] = row.coinId
            it[flowTime] = row.time.toString()
            it[sourceDest] = row.sourceDest
            it[notes] = row.notes
        }
    }

    /** 批量删除（批次相对校验在服务层先行）。 */
    suspend fun deleteByUuids(accountId: Int, uuids: List<String>) = gate.write {
        if (uuids.isNotEmpty()) {
            CapitalFlowsTable.deleteWhere {
                (CapitalFlowsTable.accountId eq accountId) and (CapitalFlowsTable.uuid inList uuids)
            }
        }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.rowOf(): FundFlowRow = FundFlowRow(
        id = this[CapitalFlowsTable.id].toLong(),
        accountId = this[CapitalFlowsTable.accountId],
        kind = if (this[CapitalFlowsTable.type] == "WITHDRAWAL") FlowKind.WITHDRAWAL else FlowKind.DEPOSIT,
        amount = BigDecimal(this[CapitalFlowsTable.amount]),
        baseAmount = BigDecimal(this[CapitalFlowsTable.baseAmount]),
        currency = this[CapitalFlowsTable.currency],
        coinId = this[CapitalFlowsTable.coinId].toLong(),
        time = Instant.parse(this[CapitalFlowsTable.flowTime]),
        sourceDest = this[CapitalFlowsTable.sourceDest],
        notes = this[CapitalFlowsTable.notes],
        uuid = this[CapitalFlowsTable.uuid],
    )
}

/** reconciliation_records 仓库（M8 T8.2；锚点语义消费见 ReplayEngine.applyAnchor）。 */
class ReconciliationRepository(private val gate: DbGate) {

    /** 全量读取（账户级；按 (created_at, id) 升序）。 */
    suspend fun listAll(accountId: Int): List<ReconciliationRecordRow> = gate.read {
        ReconciliationRecordsTable.selectAll()
            .where { ReconciliationRecordsTable.accountId eq accountId }
            .toList()
            .map { it.rowOf() }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
    }

    /** 币种校准历史（币种详情页输入；时间倒序）。 */
    suspend fun listForCoin(accountId: Int, coinId: Long): List<ReconciliationRecordRow> = gate.read {
        ReconciliationRecordsTable.selectAll()
            .where {
                (ReconciliationRecordsTable.accountId eq accountId) and
                    (ReconciliationRecordsTable.coinId eq coinId.toInt())
            }
            .toList()
    }.map { it.rowOf() }
        .sortedWith(compareByDescending<ReconciliationRecordRow> { it.createdAt }.thenByDescending { it.id })

    suspend fun insert(row: NewReconciliationRow): Long = gate.write {
        ReconciliationRecordsTable.insert {
            it[accountId] = row.accountId
            it[symbol] = row.symbol
            it[coinId] = row.coinId
            it[exchange] = row.exchange
            it[localQuantity] = row.localQuantity.toPlainString()
            it[exchangeQuantity] = row.exchangeQuantity.toPlainString()
            it[delta] = row.delta.toPlainString()
            it[baseAmount] = row.baseAmount.toPlainString()
            it[uuid] = row.uuid
            it[createdAt] = Instant.now().toString()
        } get ReconciliationRecordsTable.id
    }.toLong()

    suspend fun findByUuids(accountId: Int, uuids: List<String>): List<ReconciliationRecordRow> {
        if (uuids.isEmpty()) return emptyList()
        return gate.read {
            ReconciliationRecordsTable.selectAll()
                .where {
                    (ReconciliationRecordsTable.accountId eq accountId) and
                        (ReconciliationRecordsTable.uuid inList uuids)
                }
                .toList()
        }.map { it.rowOf() }
    }

    suspend fun deleteByUuids(accountId: Int, uuids: List<String>) = gate.write {
        if (uuids.isNotEmpty()) {
            ReconciliationRecordsTable.deleteWhere {
                (ReconciliationRecordsTable.accountId eq accountId) and
                    (ReconciliationRecordsTable.uuid inList uuids)
            }
        }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.rowOf(): ReconciliationRecordRow =
        ReconciliationRecordRow(
            id = this[ReconciliationRecordsTable.id].toLong(),
            accountId = this[ReconciliationRecordsTable.accountId],
            symbol = this[ReconciliationRecordsTable.symbol],
            coinId = this[ReconciliationRecordsTable.coinId].toLong(),
            exchange = this[ReconciliationRecordsTable.exchange],
            localQuantity = BigDecimal(this[ReconciliationRecordsTable.localQuantity]),
            exchangeQuantity = BigDecimal(this[ReconciliationRecordsTable.exchangeQuantity]),
            delta = BigDecimal(this[ReconciliationRecordsTable.delta]),
            baseAmount = BigDecimal(this[ReconciliationRecordsTable.baseAmount]),
            uuid = this[ReconciliationRecordsTable.uuid],
            createdAt = Instant.parse(this[ReconciliationRecordsTable.createdAt]),
        )
}
