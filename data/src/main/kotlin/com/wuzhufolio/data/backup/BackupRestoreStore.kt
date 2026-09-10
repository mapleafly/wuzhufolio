package com.wuzhufolio.data.backup

import com.wuzhufolio.data.exchange.ApiKeysTable
import com.wuzhufolio.data.exchange.SyncLogsTable
import com.wuzhufolio.data.exchange.TransactionsTable
import com.wuzhufolio.data.ledger.CapitalFlowsTable
import com.wuzhufolio.data.ledger.FeeRulesTable
import com.wuzhufolio.data.ledger.ReconciliationRecordsTable
import com.wuzhufolio.data.settings.SettingsTable
import com.wuzhufolio.domain.backup.BackupMergePlanner
import com.wuzhufolio.domain.backup.RestoreTableCounts
import com.wuzhufolio.domain.security.CryptoService
import java.math.BigDecimal
import java.time.Instant
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * 恢复应用器（M9 · T9.2 · 单写事务照 [BackupMergePlanner] 清单执行）。
 *
 * 事务边界：[deleteAccountBusinessRowsWithinTx] / [applyWithinTx] 都必须在**单个
 * [com.wuzhufolio.data.db.DbGate.write] 回调内**执行（全部为直接表访问，不得再入闸门——
 * 写 Mutex 不可重入）；全量覆盖的清空与导入同事务，任一步失败整体回滚（不留半套数据）。
 *
 * 审计字段保真：交易/资金/校准行按载荷回填 uuid / created_at / source / price_status
 * （去重键与审计口径跨设备稳定）；fee_rules.created_at 无审计语义，插入时取当前时刻。
 *
 * api_keys 凭证重加密（ADR-005 §2）：先插空列拿行 id → 以**目标账户 DEK** FieldCipher 加密回写
 * （AAD = account_id|api_key_id|column，与 M6 同口径）；全程在事务内完成。
 */
class BackupRestoreStore(private val crypto: CryptoService) {

    /**
     * 全量覆盖清空（事务内）：当前账户账户级业务数据 + 账户级设置 + 该账户密钥的同步日志。
     * **全局公共表（coins/exchange_coin_map/price_snapshots）与全局设置（account_id NULL）不动**（PRD 5.2-6）。
     * FK 序：sync_logs → api_keys → 业务表 → 账户设置。
     */
    fun deleteAccountBusinessRowsWithinTx(accountId: Int) {
        SyncLogsTable.deleteWhere { SyncLogsTable.accountId eq accountId }
        ApiKeysTable.deleteWhere { ApiKeysTable.accountId eq accountId }
        FeeRulesTable.deleteWhere { FeeRulesTable.accountId eq accountId }
        CapitalFlowsTable.deleteWhere { CapitalFlowsTable.accountId eq accountId }
        ReconciliationRecordsTable.deleteWhere { ReconciliationRecordsTable.accountId eq accountId }
        TransactionsTable.deleteWhere { TransactionsTable.accountId eq accountId }
        SettingsTable.deleteWhere { SettingsTable.accountId eq accountId.toString() }
    }

    /**
     * 照单应用（事务内）：交易/资金/校准/密钥逐条插入，费率 upsert（备份优先）、账户设置 upsert
     * （备份优先）、快照幂等插入（本地已有同桶行保留本地）。
     *
     * @param coinIdOf cg_id → 本地 coins.id（调用方已按目录解析；规划阶段保证全部可解析）。
     */
    fun applyWithinTx(
        accountId: Int,
        dek: ByteArray,
        plan: BackupMergePlanner.MergePlan,
        coinIdOf: (String) -> Int?,
        snapshotStore: BackupSnapshotStore,
    ): RestoreTableCounts = RestoreTableCounts(
        transactions = insertTransactionsWithinTx(accountId, plan.transactions, coinIdOf),
        capitalFlows = insertFlowsWithinTx(accountId, plan.capitalFlows, coinIdOf),
        reconciliationRecords = insertReconsWithinTx(accountId, plan.reconciliationRecords, coinIdOf),
        feeRules = upsertFeeRulesWithinTx(accountId, plan.feeRules),
        apiKeys = insertApiKeysWithinTx(accountId, dek, plan.apiKeys),
        settings = upsertSettingsWithinTx(accountId, plan.settings),
        priceSnapshots = insertSnapshotsWithinTx(plan.priceSnapshots, coinIdOf, snapshotStore),
    )

    /** 交易插入（uuid/created_at/source/price_status 按载荷保真）。 */
    private fun insertTransactionsWithinTx(
        accountId: Int,
        rows: List<com.wuzhufolio.domain.backup.CproTransaction>,
        coinIdOf: (String) -> Int?,
    ): Int {
        var count = 0
        for (tx in rows) {
            val base = coinIdOf(tx.baseCgId)
            val quote = coinIdOf(tx.quoteCgId)
            if (base == null || quote == null) continue // 规划已过滤；防御性跳过
            TransactionsTable.insert {
                it[TransactionsTable.accountId] = accountId
                it[exchange] = tx.exchange
                it[exchangeOrderId] = tx.exchangeOrderId
                it[pair] = tx.pair
                it[baseCoinId] = base
                it[quoteCoinId] = quote
                it[type] = tx.type
                it[price] = tx.price
                it[quantity] = tx.quantity
                it[fee] = tx.fee
                it[feeCurrency] = tx.feeCurrency
                it[transactionTime] = tx.transactionTime
                it[notes] = tx.notes
                it[createdAt] = tx.createdAt
                it[recordSource] = tx.source
                it[uuid] = tx.uuid
                it[priceStatus] = tx.priceStatus
            }
            count++
        }
        return count
    }

    /** 资金流水插入。 */
    private fun insertFlowsWithinTx(
        accountId: Int,
        rows: List<com.wuzhufolio.domain.backup.CproCapitalFlow>,
        coinIdOf: (String) -> Int?,
    ): Int {
        var count = 0
        for (flow in rows) {
            val coin = coinIdOf(flow.coinCgId) ?: continue
            CapitalFlowsTable.insert {
                it[CapitalFlowsTable.accountId] = accountId
                it[type] = flow.type
                it[amount] = flow.amount
                it[baseAmount] = flow.baseAmount
                it[currency] = flow.currency
                it[coinId] = coin
                it[flowTime] = flow.flowTime
                it[sourceDest] = flow.sourceDest
                it[notes] = flow.notes
                it[createdAt] = flow.createdAt
                it[uuid] = flow.uuid
                it[priceStatus] = flow.priceStatus
            }
            count++
        }
        return count
    }

    /** 校准记录插入。 */
    private fun insertReconsWithinTx(
        accountId: Int,
        rows: List<com.wuzhufolio.domain.backup.CproReconciliation>,
        coinIdOf: (String) -> Int?,
    ): Int {
        var count = 0
        for (recon in rows) {
            val coin = coinIdOf(recon.coinCgId) ?: continue
            ReconciliationRecordsTable.insert {
                it[ReconciliationRecordsTable.accountId] = accountId
                it[symbol] = recon.symbol
                it[ReconciliationRecordsTable.coinId] = coin
                it[exchange] = recon.exchange
                it[localQuantity] = recon.localQuantity
                it[exchangeQuantity] = recon.exchangeQuantity
                it[delta] = recon.delta
                it[baseAmount] = recon.baseAmount
                it[uuid] = recon.uuid
                it[createdAt] = recon.createdAt
            }
            count++
        }
        return count
    }

    /** 费率规则 upsert（备份优先覆盖，PRD 5.2-6）。 */
    private fun upsertFeeRulesWithinTx(
        accountId: Int,
        rules: List<com.wuzhufolio.domain.backup.CproFeeRule>,
    ): Int {
        var count = 0
        for (rule in rules) {
            val exchangeKey = rule.exchange.trim().uppercase()
            val existing = FeeRulesTable.selectAll()
                .where { (FeeRulesTable.accountId eq accountId) and (FeeRulesTable.exchange eq exchangeKey) }
                .singleOrNull()
            if (existing == null) {
                FeeRulesTable.insert {
                    it[FeeRulesTable.accountId] = accountId
                    it[FeeRulesTable.exchange] = exchangeKey
                    it[buyRate] = rule.buyRate
                    it[sellRate] = rule.sellRate
                    it[createdAt] = Instant.now().toString()
                }
            } else {
                FeeRulesTable.update({ FeeRulesTable.id eq existing[FeeRulesTable.id] }) {
                    it[buyRate] = rule.buyRate
                    it[sellRate] = rule.sellRate
                }
            }
            count++
        }
        return count
    }

    /** API 密钥插入（先插后包：目标账户 DEK 重加密，AAD 依赖真实行 id）。 */
    private fun insertApiKeysWithinTx(
        accountId: Int,
        dek: ByteArray,
        rows: List<com.wuzhufolio.domain.backup.CproApiKey>,
    ): Int {
        var count = 0
        for (key in rows) {
            val rowId = ApiKeysTable.insert {
                it[ApiKeysTable.accountId] = accountId
                it[ApiKeysTable.name] = key.name
                it[ApiKeysTable.exchangeName] = key.exchangeName
                it[apiKey] = ""
                it[secretKey] = ""
                it[passphrase] = if (key.passphrase != null) "" else null
                it[extra] = if (key.extra != null) "" else null
                it[lastSyncTime] = key.lastSyncTime
                it[status] = key.status
            } get ApiKeysTable.id
            val aadId = rowId.toString()
            ApiKeysTable.update(where = { ApiKeysTable.id eq rowId }) {
                it[apiKey] = crypto.encryptField(key.apiKey, dek, accountId.toString(), aadId, "api_key")
                it[secretKey] = crypto.encryptField(key.secretKey, dek, accountId.toString(), aadId, "secret_key")
                it[passphrase] = key.passphrase?.let { plain ->
                    crypto.encryptField(plain, dek, accountId.toString(), aadId, "passphrase")
                }
                it[extra] = key.extra?.let { plain ->
                    crypto.encryptField(plain, dek, accountId.toString(), aadId, "extra")
                }
            }
            count++
        }
        return count
    }

    /** 账户级设置 upsert（备份优先覆盖，PRD 5.2-6）。 */
    private fun upsertSettingsWithinTx(
        accountId: Int,
        rows: List<com.wuzhufolio.domain.backup.CproSetting>,
    ): Int {
        var count = 0
        for (setting in rows) {
            val existing = SettingsTable.selectAll()
                .where {
                    (SettingsTable.accountId eq accountId.toString()) and
                        (SettingsTable.key eq setting.key)
                }
                .singleOrNull()
            if (existing == null) {
                SettingsTable.insert {
                    it[SettingsTable.key] = setting.key
                    it[SettingsTable.accountId] = accountId.toString()
                    it[SettingsTable.value] = setting.value
                    it[SettingsTable.updatedAt] = Instant.now().toString()
                }
            } else {
                SettingsTable.update({
                    (SettingsTable.accountId eq accountId.toString()) and (SettingsTable.key eq setting.key)
                }) {
                    it[value] = setting.value
                    it[updatedAt] = Instant.now().toString()
                }
            }
            count++
        }
        return count
    }

    /** 快照幂等插入（本地已有同桶行保留本地）。 */
    private fun insertSnapshotsWithinTx(
        rows: List<com.wuzhufolio.domain.backup.CproSnapshot>,
        coinIdOf: (String) -> Int?,
        snapshotStore: BackupSnapshotStore,
    ): Int {
        var count = 0
        for (snapshot in rows) {
            val coin = coinIdOf(snapshot.coinCgId) ?: continue
            val inserted = snapshotStore.insertIfAbsentWithinTx(
                coinId = coin,
                fiat = snapshot.fiat,
                price = BigDecimal(snapshot.price),
                source = snapshot.priceSource,
                at = Instant.parse(snapshot.recordedAt),
            )
            if (inserted) count++
        }
        return count
    }
}
