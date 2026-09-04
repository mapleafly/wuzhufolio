package com.wuzhufolio.data.exchange

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.domain.exchange.ExchangeTrade
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * transactions 表映射（DDL = M009；data-model §2.5 / PRD §10-1；账户级）。
 *
 * M6 只负责 **API 导入写**（source='BINANCE API'）：去重键 (account_id, exchange, exchange_order_id)
 * 由部分唯一索引守护（exchange_order_id 非空行）；已同步 pair 枚举 = 本账户该交易所的 distinct pair
 * （pair 存 "BTC/USDT" 展示形态，原始 symbol = pair.replace("/", "")——Binance symbol = base+quote 拼接，
 * 双方均无 "/"；切分仍以 exchangeInfo 注册表为准，展示拼接只用于账本行）。
 *
 * price/quantity/fee 存 TEXT 十进制串（SQLite NUMERIC 浮点截断风险勘误同 price_snapshots，见模块记录 M5 §5）；
 * price_status = OK（API 成交价已知；手续费第三币种折算属 M7 事件构造层，登记 M6 规格落档）。
 */
object TransactionsTable : Table("transactions") {
    val id = integer("id").autoIncrement()
    val accountId = integer("account_id")
    val exchange = varchar("exchange", 24)
    val exchangeOrderId = varchar("exchange_order_id", 64).nullable()
    val pair = varchar("pair", 32)
    val baseCoinId = integer("base_coin_id")
    val quoteCoinId = integer("quote_coin_id")
    val type = varchar("type", 8)
    val price = text("price")
    val quantity = text("quantity")
    val fee = text("fee")
    val feeCurrency = varchar("fee_currency", 24).nullable()
    val transactionTime = varchar("transaction_time", 40)
    val notes = text("notes").nullable()
    val createdAt = varchar("created_at", 40)
    val recordSource = varchar("source", 24)
    val uuid = varchar("uuid", 48)
    val priceStatus = varchar("price_status", 16)

    override val primaryKey = PrimaryKey(id)
}

/** 导入成交的账本行构造输入（sync 编排内部使用；base/quote 币 id 已在写入前解析冻结）。 */
data class ImportedTradeRow(
    val accountId: Int,
    val exchange: String,
    val exchangeOrderId: String,
    val pair: String,
    val baseCoinId: Int,
    val quoteCoinId: Int,
    val type: String,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val fee: BigDecimal,
    val feeCurrency: String?,
    val transactionTime: Instant,
)

/** 已同步 pair 概览（增量去重 + 候选 symbol 枚举输入）。 */
data class SyncedPairInfo(val pair: String, val maxOrderId: Long?)

/**
 * 交易账本仓库（M6 导入写 + 枚举；手动/CSV 读写归 M7，本仓库先行落 DDL/导入面）。
 * 经 DbGate 单写队列（写）/WAL（读）。
 */
class ExchangeTransactionRepository(private val gate: DbGate) {

    /** 插入一条导入成交；重复（同 exchange+order id 已存在）返回 false（不覆盖——增量去重语义）。 */
    fun insertIfAbsent(row: ImportedTradeRow): Boolean = gate.writeBlocking {
        val existing = TransactionsTable.selectAll()
            .where { existingWhere(row) }
            .singleOrNull()
        if (existing != null) {
            return@writeBlocking false
        }
        TransactionsTable.insert {
            it[accountId] = row.accountId
            it[exchange] = row.exchange
            it[exchangeOrderId] = row.exchangeOrderId
            it[pair] = row.pair
            it[baseCoinId] = row.baseCoinId
            it[quoteCoinId] = row.quoteCoinId
            it[type] = row.type
            it[price] = row.price.toPlainString()
            it[quantity] = row.quantity.toPlainString()
            it[fee] = row.fee.toPlainString()
            it[feeCurrency] = row.feeCurrency
            it[transactionTime] = row.transactionTime.toString()
            it[notes] = null
            it[createdAt] = Instant.now().toString()
            it[recordSource] = SOURCE_BINANCE
            it[uuid] = UUID.randomUUID().toString()
            it[priceStatus] = "OK"
        }
        true
    }

    /** 已同步 pair 与每 pair 最大成交 id（增量游标：sinceId = max order id）。 */
    fun syncedPairs(accountId: Int, exchange: String): List<SyncedPairInfo> = gate.readBlocking {
        TransactionsTable.selectAll()
            .where {
                (TransactionsTable.accountId eq accountId) and (TransactionsTable.exchange eq exchange)
            }
            .toList()
            .groupBy { it[TransactionsTable.pair] }
            .map { (pair, rows) ->
                SyncedPairInfo(
                    pair = pair,
                    maxOrderId = rows.mapNotNull { it[TransactionsTable.exchangeOrderId]?.toLongOrNull() }
                        .maxOrNull(),
                )
            }
            .sortedBy { it.pair }
    }

    /** 同步结果计数（本轮新增；测试/诊断）。 */
    fun countByAccount(accountId: Int): Long = gate.readBlocking {
        TransactionsTable.selectAll()
            .where { TransactionsTable.accountId eq accountId }
            .count()
    }

    private fun existingWhere(row: ImportedTradeRow): org.jetbrains.exposed.v1.core.Op<Boolean> =
        (TransactionsTable.accountId eq row.accountId) and
            (TransactionsTable.exchange eq row.exchange) and
            (TransactionsTable.exchangeOrderId eq row.exchangeOrderId)

    companion object {
        const val SOURCE_BINANCE = "BINANCE API"
    }
}
