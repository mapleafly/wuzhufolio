package com.wuzhufolio.data.backup

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.ledger.CapitalFlowsTable
import com.wuzhufolio.data.ledger.ReconciliationRecordsTable
import com.wuzhufolio.data.market.PriceSnapshotsTable
import com.wuzhufolio.data.market.SqlUtc
import com.wuzhufolio.data.settings.SettingsTable
import com.wuzhufolio.domain.backup.CproSetting
import com.wuzhufolio.domain.market.PriceResolution
import com.wuzhufolio.domain.market.PriceSource
import java.math.BigDecimal
import java.time.Instant
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * 备份专用低层数据访问（M9 · T9.1–T9.3 · 只用既有表对象，不改任何已通过模块的仓库）。
 *
 * 三块：
 * - [BackupSettingsStore]：账户级 settings 行读写（备份内容范围 = 账户级设置，ADR-005 §3；
 *   全局行 account_id NULL 不在范围）；account_id 以文本存（M001 DDL 口径，与其他账户行同库不同表无 FK）；
 * - [BackupSnapshotStore]：快照导出读取（按币种集合）+ 导入幂等插入（币种+法币+小时桶，缺行才插）；
 * - [BackupRestoreStore]：恢复应用器（单写事务照 [BackupMergePlanner] 清单执行；全量覆盖先清账户业务表）。
 *
 * 全部经 [DbGate]（写 = 单写队列；读 = WAL 并行）。
 */

/** 账户级 settings 读写（备份导出/导入 + 上次备份/恢复时刻记录）。 */
class BackupSettingsStore(private val gate: DbGate) {

    /** 账户级全部设置行（导出载荷来源）。 */
    fun listAccountSettings(accountId: Int): List<CproSetting> = gate.readBlocking {
        SettingsTable.selectAll()
            .where { SettingsTable.accountId eq accountId.toString() }
            .map { CproSetting(key = it[SettingsTable.key], value = it[SettingsTable.value]) }
    }

    /** 单个账户级设置（元数据读取）。 */
    fun getAccountSetting(accountId: Int, key: String): String? = gate.readBlocking {
        SettingsTable.selectAll()
            .where {
                (SettingsTable.accountId eq accountId.toString()) and (SettingsTable.key eq key)
            }
            .singleOrNull()
            ?.get(SettingsTable.value)
    }

    /** 账户级设置 upsert（key 唯一，先查后写——M001 COALESCE 唯一索引语义）。 */
    fun putAccountSetting(accountId: Int, key: String, value: String) = gate.writeBlocking {
        val existing = SettingsTable.selectAll()
            .where {
                (SettingsTable.accountId eq accountId.toString()) and (SettingsTable.key eq key)
            }
            .singleOrNull()
        val now = Instant.now().toString()
        if (existing == null) {
            SettingsTable.insert {
                it[SettingsTable.key] = key
                it[SettingsTable.accountId] = accountId.toString()
                it[SettingsTable.value] = value
                it[SettingsTable.updatedAt] = now
            }
        } else {
            SettingsTable.update({
                (SettingsTable.accountId eq accountId.toString()) and (SettingsTable.key eq key)
            }) {
                it[SettingsTable.value] = value
                it[updatedAt] = now
            }
        }
    }

    /** 删除账户全部设置行（全量覆盖路径）。 */
    fun deleteAllAccountSettings(accountId: Int) = gate.writeBlocking {
        SettingsTable.deleteWhere { SettingsTable.accountId eq accountId.toString() }
    }
}

/** 快照原始行（导出映射输入；coinId 为本地行主键，cg 映射由服务层经目录完成）。 */
data class BackupSnapshotRow(
    val coinId: Long,
    val fiat: String,
    val price: BigDecimal,
    val source: String,
    val recordedAt: Instant,
)

/** 快照导出读取 + 导入幂等插入（同小时桶缺行才插——黄金用例 10「快照幂等合并」）。 */
class BackupSnapshotStore(private val gate: DbGate) {

    /** 按币种行主键集合读取全部快照（fiat 不限——涉及币种的全部法币快照随备份，ADR-005 §3）。 */
    fun listByCoinIds(coinIds: Collection<Long>): List<BackupSnapshotRow> {
        if (coinIds.isEmpty()) return emptyList()
        return gate.readBlocking {
            PriceSnapshotsTable.selectAll()
                .where { PriceSnapshotsTable.coinId inList coinIds.map { it.toInt() } }
                .map { row ->
                    BackupSnapshotRow(
                        coinId = row[PriceSnapshotsTable.coinId].toLong(),
                        fiat = row[PriceSnapshotsTable.fiat],
                        price = BigDecimal(row[PriceSnapshotsTable.price]),
                        source = row[PriceSnapshotsTable.priceSource],
                        recordedAt = SqlUtc.parse(row[PriceSnapshotsTable.recordedAt]),
                    )
                }
        }
    }

    /** 全部快照的 (coin_id, fiat, recordedAt)（导入前既有桶键装配输入）。 */
    fun listAllForBuckets(): List<Triple<Long, String, Instant>> = gate.readBlocking {
        PriceSnapshotsTable.selectAll()
            .map { row ->
                Triple(
                    row[PriceSnapshotsTable.coinId].toLong(),
                    row[PriceSnapshotsTable.fiat],
                    SqlUtc.parse(row[PriceSnapshotsTable.recordedAt]),
                )
            }
    }

    /**
     * 幂等插入（**必须在 [DbGate.write] 事务内调用**——直接表操作，不得再入闸门）。
     * 同 (coin_id, fiat, 小时桶) 已有行 → 保留本地返回 false；缺行 → 插入返回 true。
     */
    fun insertIfAbsentWithinTx(coinId: Int, fiat: String, price: BigDecimal, source: String, at: Instant): Boolean {
        val bucketStart = PriceResolution.hourBucketOf(at)
        val bucketEnd = bucketStart.plusSeconds(3600)
        val existing = PriceSnapshotsTable.selectAll()
            .where {
                (PriceSnapshotsTable.coinId eq coinId) and
                    (PriceSnapshotsTable.fiat eq fiat) and
                    (PriceSnapshotsTable.recordedAt greaterEq SqlUtc.format(bucketStart)) and
                    (PriceSnapshotsTable.recordedAt less SqlUtc.format(bucketEnd))
            }
            .any()
        if (existing) return false
        PriceSnapshotsTable.insert {
            it[PriceSnapshotsTable.coinId] = coinId
            it[PriceSnapshotsTable.fiat] = fiat
            it[PriceSnapshotsTable.price] = price.toPlainString()
            it[PriceSnapshotsTable.priceSource] = source
            it[PriceSnapshotsTable.recordedAt] = SqlUtc.format(at)
        }
        return true
    }
}

// ---------------------------------------------------------------------------
// 导出全列读取（备份保真需要 uuid/created_at/source/price_status 审计字段——
// M6/M7/M8 仓库行形态不含这些列，为不动已通过模块，备份侧直读表对象）
// ---------------------------------------------------------------------------

/** 交易全列行（导出映射输入；金额保留 TEXT 原串，零转换损耗）。 */
data class BackupTxRow(
    val id: Long,
    val uuid: String,
    val exchange: String,
    val exchangeOrderId: String?,
    val pair: String,
    val baseCoinId: Long,
    val quoteCoinId: Long,
    val type: String,
    val price: String,
    val quantity: String,
    val fee: String,
    val feeCurrency: String?,
    val transactionTime: String,
    val notes: String?,
    val createdAt: String,
    val source: String,
    val priceStatus: String,
)

/** 资金全列行。 */
data class BackupFlowRow(
    val id: Long,
    val uuid: String,
    val type: String,
    val amount: String,
    val baseAmount: String,
    val currency: String,
    val coinId: Long,
    val flowTime: String,
    val sourceDest: String?,
    val notes: String?,
    val createdAt: String,
    val priceStatus: String,
)

/** 校准全列行。 */
data class BackupReconRow(
    val id: Long,
    val uuid: String,
    val symbol: String,
    val coinId: Long,
    val exchange: String,
    val localQuantity: String,
    val exchangeQuantity: String,
    val delta: String,
    val baseAmount: String,
    val createdAt: String,
)

/** 业务三表全列读取（导出 + 恢复后重放共用；只读，经 [DbGate] WAL 读）。 */
class BackupLedgerReadStore(private val gate: DbGate) {

    fun listTransactionsFull(accountId: Int): List<BackupTxRow> = gate.readBlocking {
        com.wuzhufolio.data.exchange.TransactionsTable.selectAll()
            .where { com.wuzhufolio.data.exchange.TransactionsTable.accountId eq accountId }
            .map { row ->
                BackupTxRow(
                    id = row[com.wuzhufolio.data.exchange.TransactionsTable.id].toLong(),
                    uuid = row[com.wuzhufolio.data.exchange.TransactionsTable.uuid],
                    exchange = row[com.wuzhufolio.data.exchange.TransactionsTable.exchange],
                    exchangeOrderId = row[com.wuzhufolio.data.exchange.TransactionsTable.exchangeOrderId],
                    pair = row[com.wuzhufolio.data.exchange.TransactionsTable.pair],
                    baseCoinId = row[com.wuzhufolio.data.exchange.TransactionsTable.baseCoinId].toLong(),
                    quoteCoinId = row[com.wuzhufolio.data.exchange.TransactionsTable.quoteCoinId].toLong(),
                    type = row[com.wuzhufolio.data.exchange.TransactionsTable.type],
                    price = row[com.wuzhufolio.data.exchange.TransactionsTable.price],
                    quantity = row[com.wuzhufolio.data.exchange.TransactionsTable.quantity],
                    fee = row[com.wuzhufolio.data.exchange.TransactionsTable.fee],
                    feeCurrency = row[com.wuzhufolio.data.exchange.TransactionsTable.feeCurrency],
                    transactionTime = row[com.wuzhufolio.data.exchange.TransactionsTable.transactionTime],
                    notes = row[com.wuzhufolio.data.exchange.TransactionsTable.notes],
                    createdAt = row[com.wuzhufolio.data.exchange.TransactionsTable.createdAt],
                    source = row[com.wuzhufolio.data.exchange.TransactionsTable.recordSource],
                    priceStatus = row[com.wuzhufolio.data.exchange.TransactionsTable.priceStatus],
                )
            }
    }

    fun listFlowsFull(accountId: Int): List<BackupFlowRow> = gate.readBlocking {
        CapitalFlowsTable.selectAll()
            .where { CapitalFlowsTable.accountId eq accountId }
            .map { row ->
                BackupFlowRow(
                    id = row[CapitalFlowsTable.id].toLong(),
                    uuid = row[CapitalFlowsTable.uuid],
                    type = row[CapitalFlowsTable.type],
                    amount = row[CapitalFlowsTable.amount],
                    baseAmount = row[CapitalFlowsTable.baseAmount],
                    currency = row[CapitalFlowsTable.currency],
                    coinId = row[CapitalFlowsTable.coinId].toLong(),
                    flowTime = row[CapitalFlowsTable.flowTime],
                    sourceDest = row[CapitalFlowsTable.sourceDest],
                    notes = row[CapitalFlowsTable.notes],
                    createdAt = row[CapitalFlowsTable.createdAt],
                    priceStatus = row[CapitalFlowsTable.priceStatus],
                )
            }
    }

    fun listReconsFull(accountId: Int): List<BackupReconRow> = gate.readBlocking {
        ReconciliationRecordsTable.selectAll()
            .where { ReconciliationRecordsTable.accountId eq accountId }
            .map { row ->
                BackupReconRow(
                    id = row[ReconciliationRecordsTable.id].toLong(),
                    uuid = row[ReconciliationRecordsTable.uuid],
                    symbol = row[ReconciliationRecordsTable.symbol],
                    coinId = row[ReconciliationRecordsTable.coinId].toLong(),
                    exchange = row[ReconciliationRecordsTable.exchange],
                    localQuantity = row[ReconciliationRecordsTable.localQuantity],
                    exchangeQuantity = row[ReconciliationRecordsTable.exchangeQuantity],
                    delta = row[ReconciliationRecordsTable.delta],
                    baseAmount = row[ReconciliationRecordsTable.baseAmount],
                    createdAt = row[ReconciliationRecordsTable.createdAt],
                )
            }
    }
}
