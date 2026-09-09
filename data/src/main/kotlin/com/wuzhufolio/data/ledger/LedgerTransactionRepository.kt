package com.wuzhufolio.data.ledger

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.exchange.TransactionsTable
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.TxFilter
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * 交易账本仓库（M7 全量读写面；M009 DDL 不变——M6 规格 §5-1「M7 只扩展不重建」）。
 *
 * 与 M6 ExchangeTransactionRepository（同步导入写）并存：同步编排不回改（已通过模块不动），
 * 本仓库服务手动/CSV/列表/编辑/删除。全部经 DbGate 单写队列（写）/WAL（读）。
 * 行读取统一走 [rowOf]：金额列 TEXT 十进制串精确还原 BigDecimal，时间列 SqlUtc/Instant 文本解析。
 */
class LedgerTransactionRepository(private val gate: com.wuzhufolio.data.db.DbGate) {

    /** 全量读取（账户级；按交易时间升序——事件构造按 (at, seq=rowId) 稳定重放的输入序）。 */
    suspend fun listAll(accountId: Int): List<LedgerTxRow> = gate.read {
        TransactionsTable.selectAll()
            .where { TransactionsTable.accountId eq accountId }
            .toList()
            .map { it.rowOf() }
            .sortedWith(compareBy({ it.time }, { it.id }))
    }

    /** 筛选列表（PRD §9.6：币种/交易所/类型筛选 + 搜索；交易时间降序 = 默认展示序）。 */
    suspend fun listFiltered(accountId: Int, filter: TxFilter): List<LedgerTxRow> {
        val coin = filter.coinSymbol?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        val exchange = filter.exchange?.trim()?.takeIf { it.isNotEmpty() }
        val query = filter.query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return listAll(accountId).asReversed().filter { row ->
            (filter.side == null || row.side == filter.side) &&
                (exchange == null || row.exchange.equals(exchange, ignoreCase = true)) &&
                (coin == null ||
                    row.pair.uppercase().contains(coin) ||
                    row.feeCurrency?.uppercase() == coin) &&
                (query == null ||
                    row.pair.lowercase().contains(query) ||
                    row.exchange.lowercase().contains(query) ||
                    (row.notes?.lowercase()?.contains(query) == true))
        }
    }

    suspend fun findById(accountId: Int, id: Long): LedgerTxRow? = gate.read {
        TransactionsTable.selectAll()
            .where { (TransactionsTable.accountId eq accountId) and (TransactionsTable.id eq id.toInt()) }
            .singleOrNull()
    }?.rowOf()

    /** 精确去重（PRD 故事 2.3-4：有订单号按「交易所 + 订单号」；手动/CSV 行 order_id 可空）。 */
    suspend fun existsExact(accountId: Int, exchange: String, orderId: String): Boolean = gate.read {
        TransactionsTable.selectAll()
            .where {
                (TransactionsTable.accountId eq accountId) and
                    (TransactionsTable.exchange eq exchange) and
                    (TransactionsTable.exchangeOrderId eq orderId)
            }
            .any()
    }

    /** 模糊去重（PRD 故事 2.3-4：无订单号按「交易时间 + 交易对 + 类型 + 数量 + 价格」）。 */
    @Suppress("LongParameterList") // 去重键 7 元组（时间/交易对/类型/数量/价格/账户/交易所），语义内聚
    suspend fun findFuzzy(
        accountId: Int,
        exchange: String,
        time: Instant,
        pair: String,
        type: String,
        quantity: BigDecimal,
        price: BigDecimal,
    ): Long? = gate.read {
        TransactionsTable.selectAll()
            .where {
                (TransactionsTable.accountId eq accountId) and
                    (TransactionsTable.exchange eq exchange) and
                    (TransactionsTable.transactionTime eq time.toString()) and
                    (TransactionsTable.pair eq pair) and
                    (TransactionsTable.type eq type) and
                    (TransactionsTable.quantity eq quantity.toPlainString()) and
                    (TransactionsTable.price eq price.toPlainString())
            }
            .singleOrNull()
    }?.get(TransactionsTable.id)?.toLong()

    /** 插入（手动/CSV 共用；uuid = 事件 id + 备份去重键）。返回新行 id。 */
    suspend fun insert(row: NewLedgerTxRow): Long = gate.write {
        TransactionsTable.insert {
            it[accountId] = row.accountId
            it[exchange] = row.exchange
            it[exchangeOrderId] = row.exchangeOrderId
            it[pair] = row.pair
            it[baseCoinId] = row.baseCoinId
            it[quoteCoinId] = row.quoteCoinId
            it[type] = row.side.name
            it[price] = row.price.toPlainString()
            it[quantity] = row.quantity.toPlainString()
            it[fee] = row.fee.toPlainString()
            it[feeCurrency] = row.feeCurrency
            it[transactionTime] = row.time.toString()
            it[notes] = row.notes
            it[createdAt] = Instant.now().toString()
            it[recordSource] = row.source
            it[uuid] = row.uuid
            it[priceStatus] = row.priceStatus
        } get TransactionsTable.id
    }.toLong()

    /** 编辑（不动 uuid/来源/created_at；pair 与币种 FK 随录入重建冻结）。 */
    suspend fun update(row: UpdatedLedgerTxRow) = gate.write {
        TransactionsTable.update({
            (TransactionsTable.accountId eq row.accountId) and (TransactionsTable.id eq row.id.toInt())
        }) {
            it[exchange] = row.exchange
            it[exchangeOrderId] = row.exchangeOrderId
            it[pair] = row.pair
            it[baseCoinId] = row.baseCoinId
            it[quoteCoinId] = row.quoteCoinId
            it[type] = row.side.name
            it[price] = row.price.toPlainString()
            it[quantity] = row.quantity.toPlainString()
            it[fee] = row.fee.toPlainString()
            it[feeCurrency] = row.feeCurrency
            it[transactionTime] = row.time.toString()
            it[notes] = row.notes
        }
    }

    /** 批量删除（手动删除路径；批次校验在服务层先行）。 */
    suspend fun deleteByIds(accountId: Int, ids: List<Long>) = gate.write {
        if (ids.isNotEmpty()) {
            TransactionsTable.deleteWhere {
                (TransactionsTable.accountId eq accountId) and
                    (TransactionsTable.id inList ids.map { it.toInt() })
            }
        }
    }

    /** 最大行 id（新记录 seq 取 max+1：同时刻既有记录之后应用，确定性口径见模块记录 §5）。 */
    suspend fun maxSeq(accountId: Int): Long = listAll(accountId).maxOfOrNull { it.id } ?: 0L

    private fun org.jetbrains.exposed.v1.core.ResultRow.rowOf(): LedgerTxRow = LedgerTxRow(
        id = this[TransactionsTable.id].toLong(),
        accountId = this[TransactionsTable.accountId],
        exchange = this[TransactionsTable.exchange],
        exchangeOrderId = this[TransactionsTable.exchangeOrderId],
        pair = this[TransactionsTable.pair],
        baseCoinId = this[TransactionsTable.baseCoinId].toLong(),
        quoteCoinId = this[TransactionsTable.quoteCoinId].toLong(),
        side = if (this[TransactionsTable.type] == "SELL") Side.SELL else Side.BUY,
        price = BigDecimal(this[TransactionsTable.price]),
        quantity = BigDecimal(this[TransactionsTable.quantity]),
        fee = BigDecimal(this[TransactionsTable.fee]),
        feeCurrency = this[TransactionsTable.feeCurrency],
        time = Instant.parse(this[TransactionsTable.transactionTime]),
        notes = this[TransactionsTable.notes],
        source = this[TransactionsTable.recordSource],
        uuid = this[TransactionsTable.uuid],
    )
}

/** transactions 行（仓库形态；金额 TEXT 十进制串还原 BigDecimal，时间 UTC Instant）。 */
data class LedgerTxRow(
    val id: Long,
    val accountId: Int,
    val exchange: String,
    val exchangeOrderId: String?,
    val pair: String,
    val baseCoinId: Long,
    val quoteCoinId: Long,
    val side: Side,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val fee: BigDecimal,
    val feeCurrency: String?,
    val time: Instant,
    val notes: String?,
    val source: String,
    val uuid: String,
)

/** 新行（手动/CSV；source = Manual / CSV；币 FK 为 coins.id 整型主键）。 */
data class NewLedgerTxRow(
    val accountId: Int,
    val exchange: String,
    val exchangeOrderId: String?,
    val pair: String,
    val baseCoinId: Int,
    val quoteCoinId: Int,
    val side: Side,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val fee: BigDecimal,
    val feeCurrency: String?,
    val time: Instant,
    val notes: String?,
    val source: String,
    val priceStatus: String,
) {
    val uuid: String = UUID.randomUUID().toString()
}

/** 编辑行（id 定位；其余字段全量覆盖；币 FK 为 coins.id 整型主键）。 */
data class UpdatedLedgerTxRow(
    val accountId: Int,
    val id: Long,
    val exchange: String,
    val exchangeOrderId: String?,
    val pair: String,
    val baseCoinId: Int,
    val quoteCoinId: Int,
    val side: Side,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val fee: BigDecimal,
    val feeCurrency: String?,
    val time: Instant,
    val notes: String?,
)
