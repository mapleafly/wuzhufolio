package com.wuzhufolio.data.backup

import com.wuzhufolio.data.accounts.ActiveSession
import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.exchange.ApiKeyRepository
import com.wuzhufolio.data.ledger.FeeRuleRepository
import com.wuzhufolio.data.ledger.FundFlowRow
import com.wuzhufolio.data.ledger.LedgerEventAssembler
import com.wuzhufolio.data.ledger.LedgerTxRow
import com.wuzhufolio.data.ledger.ReconciliationRecordRow
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.accounts.AccountPolicy
import com.wuzhufolio.domain.backup.BackupExportSummary
import com.wuzhufolio.domain.backup.BackupMergePlanner
import com.wuzhufolio.domain.backup.BackupMetadata
import com.wuzhufolio.domain.backup.BackupPreview
import com.wuzhufolio.domain.backup.BackupService
import com.wuzhufolio.domain.backup.CproCodec
import com.wuzhufolio.domain.backup.CproCounts
import com.wuzhufolio.domain.backup.CproHeader
import com.wuzhufolio.domain.backup.CproKdf
import com.wuzhufolio.domain.backup.CproPayload
import com.wuzhufolio.domain.backup.CproRange
import com.wuzhufolio.domain.backup.CproRecords
import com.wuzhufolio.domain.backup.CproSnapshot
import com.wuzhufolio.domain.backup.CsvExportKind
import com.wuzhufolio.domain.backup.RestoreMode
import com.wuzhufolio.domain.backup.RestorePreview
import com.wuzhufolio.domain.backup.RestoreSummary
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.engine.NegativePolicy
import com.wuzhufolio.domain.engine.ReplayEngine
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.security.CryptoService
import com.wuzhufolio.domain.security.KdfParams
import java.math.BigDecimal
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import org.slf4j.Logger

/**
 * 备份恢复用例实现（M9 · T9.1–T9.3 · 契约 = [BackupService]；UI 面 T9.4 在 ui/backup）。
 *
 * 编排口径：
 * - 导出（T9.1）：业务三表全列读（[BackupLedgerReadStore]，保真 uuid/created_at/source/price_status）→
 *   币种引用统一解析为 **cg_id**（跨设备键，共享规范 §6）→ api_keys 凭证**解密**入载荷（PRD 5.2-9）→
 *   账户级设置 + 涉及币种快照 → 头部计数/范围 → CproCodec 编码（Argon2id + AES-256-GCM，AAD 绑头部）→
 *   原子写文件（临时文件 + rename）；
 * - 恢复（T9.2/T9.3）：头部预览（无需密码）→ 密码解密 + 合并规划（[BackupMergePlanner]，预览不落库）→
 *   **单写事务**应用（全量覆盖先清账户业务表 + 自动临时备份；全局公共表不清空）→
 *   恢复后全量重放（LENIENT）出「持仓异常」币种清单（导入路径例外，PRD 全局说明）；
 * - CSV 明文导出（PRD §9.9）：交易/资金流水/持仓汇总，**不含任何 API 密钥**。
 *
 * 密码生命周期：[password] CharArray 由调用方（VM）在 finally 擦除；本类不留存。
 * 「界面默认填当前账户密码」（PRD 5.2-3）**无法实现**——密码永不落盘/不留存（PRD 5.1-4、ADR-002），
 * 会话只持 DEK 无密码可回填；备份密码由用户输入，恢复语义不受影响。偏差登记见模块记录 M9 §5。
 */
@Suppress("TooManyFunctions", "LongParameterList")
// 用例面（导出/预览/准备/恢复/CSV×3/既有键/重放）+ 注入袋（仓库/存储/恢复器/目录/设置/会话）固有，拆散反损可读性
class DefaultBackupService(
    private val sessions: ActiveSessionStore,
    private val crypto: CryptoService,
    private val gate: DbGate,
    private val catalog: CoinCatalog,
    private val settings: SettingsRepository,
    private val ledgerRead: BackupLedgerReadStore,
    private val feeRules: FeeRuleRepository,
    private val apiKeys: ApiKeyRepository,
    private val assembler: LedgerEventAssembler,
    private val settingsStore: BackupSettingsStore,
    private val snapshotStore: BackupSnapshotStore,
    private val restoreStore: BackupRestoreStore,
    /** 全量覆盖前的自动临时备份目录（app 装配注入，如 ~/.wuzhufolio/backups）。 */
    private val backupsDir: Path,
    private val logger: Logger,
) : BackupService {

    private fun requireSession(): ActiveSession =
        sessions.get() ?: error("未登录：请先登录账户")

    // ---- 元数据 ----

    override suspend fun backupMetadata(): BackupMetadata {
        val accountId = requireSession().account.id
        return BackupMetadata(
            lastBackupAt = parseInstant(settingsStore.getAccountSetting(accountId, SETTING_LAST_BACKUP)),
            lastRestoreAt = parseInstant(settingsStore.getAccountSetting(accountId, SETTING_LAST_RESTORE)),
        )
    }

    // ---- T9.1 导出 ----

    override suspend fun exportBackup(path: Path, password: CharArray): BackupExportSummary =
        exportInternal(path, password, recordMetadata = true)

    private suspend fun exportInternal(
        path: Path,
        password: CharArray,
        recordMetadata: Boolean,
    ): BackupExportSummary {
        val s = requireSession()
        val accountId = s.account.id
        require(AccountPolicy.meetsMinimum(String(password))) { "备份密码须至少 8 位且包含字母与数字" }
        val now = Instant.now()

        val built = buildPayload(s)
        val records = built.records
        val counts = CproCounts(
            transactions = records.transactions.size,
            capitalFlows = records.capitalFlows.size,
            reconciliationRecords = records.reconciliationRecords.size,
            feeRules = records.feeRules.size,
            apiKeys = records.apiKeys.size,
            settings = records.settings.size,
            priceSnapshots = records.priceSnapshots.size,
        )
        val header = CproHeader(
            formatVersion = CproCodec.FORMAT_VERSION,
            appVersion = APP_VERSION,
            exportedAt = now.toString(),
            counts = counts,
            range = CproRange(
                minTime = built.minTime?.toString(),
                maxTime = built.maxTime?.toString(),
            ),
            kdf = CproKdf(
                alg = CproCodec.KDF_ALG,
                salt = CproCodec.hex(newSalt()),
                m = KdfParams.DEFAULT.memoryKiB,
                t = KdfParams.DEFAULT.iterations,
                p = KdfParams.DEFAULT.parallelism,
            ),
            cipher = CproCodec.CIPHER,
        )
        val payload = CproPayload(
            meta = com.wuzhufolio.domain.backup.CproMeta(exportedAt = now.toString(), baseFiat = baseFiat()),
            records = records,
        )
        val bytes = CproCodec.encode(header, payload, password)
        writeAtomic(path, bytes)
        if (recordMetadata) {
            settingsStore.putAccountSetting(accountId, SETTING_LAST_BACKUP, now.toString())
        }
        logger.info(
            "backup exported | path={} | tx={} flows={} recons={} keys={} settings={} snapshots={}",
            path, counts.transactions, counts.capitalFlows, counts.reconciliationRecords,
            counts.apiKeys, counts.settings, counts.priceSnapshots,
        )
        return BackupExportSummary(path = path, counts = counts, exportedAt = now, sizeBytes = bytes.size.toLong())
    }

    /** 导出载荷装配结果（记录集 + 业务时间范围）。 */
    private class BuiltPayload(
        val records: CproRecords,
        val minTime: Instant?,
        val maxTime: Instant?,
    )

    /** 读当前账户业务数据并装配载荷（币种引用解析为 cg_id；凭证解密入载荷）。 */
    private suspend fun buildPayload(s: ActiveSession): BuiltPayload {
        val accountId = s.account.id
        val txRows = ledgerRead.listTransactionsFull(accountId)
        val flowRows = ledgerRead.listFlowsFull(accountId)
        val reconRows = ledgerRead.listReconsFull(accountId)
        val feeEntries = feeRules.listEntries(accountId)
        val keyRows = apiKeyRowsToPayload(accountId, s)
        val accountSettings = settingsStore.listAccountSettings(accountId)

        // 币种引用解析：本地 coins.id → cg_id（载荷跨设备键；目录缺失行防御性跳过并留痕）
        val cgCache = HashMap<Long, String?>()
        suspend fun cgOf(coinId: Long): String? = cgCache.getOrPut(coinId) { catalog.getById(coinId)?.cgId }
        val txPayload = txRows.mapNotNull { row ->
            val baseCg = cgOf(row.baseCoinId)
            val quoteCg = cgOf(row.quoteCoinId)
            if (baseCg == null || quoteCg == null) {
                null
            } else {
                CproRecordsBuilder.tx(row, baseCg, quoteCg)
            }
        }
        val flowPayload = flowRows.mapNotNull { row ->
            cgOf(row.coinId)?.let { cg -> CproRecordsBuilder.flow(row, cg) }
        }
        val reconPayload = reconRows.mapNotNull { row ->
            cgOf(row.coinId)?.let { cg -> CproRecordsBuilder.recon(row, cg) }
        }
        val droppedRows = (txRows.size - txPayload.size) + (flowRows.size - flowPayload.size) +
            (reconRows.size - reconPayload.size)
        if (droppedRows > 0) {
            logger.warn("backup export skipped {} rows with unresolvable coin catalog entries", droppedRows)
        }

        val snapshots = snapshotsToPayload(involvedCoinIds(txRows, flowRows, reconRows))
        val records = CproRecords(
            transactions = txPayload,
            capitalFlows = flowPayload,
            reconciliationRecords = reconPayload,
            feeRules = feeEntries.map {
                com.wuzhufolio.domain.backup.CproFeeRule(
                    exchange = it.exchange ?: "",
                    buyRate = it.buyRatePercent.toPlainString(),
                    sellRate = it.sellRatePercent.toPlainString(),
                )
            },
            apiKeys = keyRows,
            settings = accountSettings,
            priceSnapshots = snapshots,
        )
        val times = buildList {
            addAll(txRows.map { parseInstant(it.transactionTime) })
            addAll(flowRows.map { parseInstant(it.flowTime) })
            addAll(reconRows.map { parseInstant(it.createdAt) })
            addAll(snapshots.map { parseInstant(it.recordedAt) })
        }.filterNotNull()
        return BuiltPayload(records, times.minOrNull(), times.maxOrNull())
    }

    // ---- T9.3 预览 / 准备 ----

    override suspend fun previewBackup(path: Path): BackupPreview {
        val header = CproCodec.parseHeader(Files.readAllBytes(path))
        return BackupPreview(
            formatVersion = header.formatVersion,
            appVersion = header.appVersion,
            exportedAt = parseInstant(header.exportedAt),
            counts = header.counts,
            rangeMin = parseInstant(header.range.minTime),
            rangeMax = parseInstant(header.range.maxTime),
            cipher = header.cipher,
        )
    }

    override suspend fun prepareRestore(path: Path, password: CharArray): RestorePreview {
        val bytes = Files.readAllBytes(path)
        val header = CproCodec.parseHeader(bytes)
        val payload = CproCodec.decode(bytes, password)
        val plan = BackupMergePlanner.plan(
            records = payload.records,
            existing = existingKeys(requireSession().account.id),
            resolvableCoinIds = resolvableCoinIds(payload.records),
            fullOverwrite = false,
        )
        return RestorePreview(header = header, plan = plan, mode = RestoreMode.MERGE)
    }

    // ---- T9.2 恢复 ----

    override suspend fun restoreBackup(path: Path, password: CharArray, mode: RestoreMode): RestoreSummary {
        val s = requireSession()
        val accountId = s.account.id
        val bytes = Files.readAllBytes(path)
        val header = CproCodec.parseHeader(bytes)
        val payload = CproCodec.decode(bytes, password)
        val fullOverwrite = mode == RestoreMode.FULL_OVERWRITE

        // 全量覆盖：覆盖前自动临时备份（PRD 5.2-6；以同一备份密码加密——用户已持有该密码，不记元数据）
        val tempBackupPath = if (fullOverwrite) createTempBackup(password) else null

        val plan = BackupMergePlanner.plan(
            records = payload.records,
            existing = existingKeys(accountId),
            resolvableCoinIds = resolvableCoinIds(payload.records),
            fullOverwrite = fullOverwrite,
        )
        // 恢复前既存异常集（用于区分「本次导入新产生」与「账户既存」持仓异常——恢复摘要口径）
        val preAnomalous = replayAnomalousCoins(accountId).toSet()
        // cg_id → 本地 coins.id 预解析（规划已保证 plan 内记录全部可解析；缺失返回 null 走防御跳过）
        val coinIdByCg = HashMap<String, Int?>()
        for (cgId in resolvableCoinIds(payload.records)) {
            coinIdByCg[cgId] = catalog.getByCgId(cgId)?.id?.toInt()
        }
        val coinIdOf: (String) -> Int? = { cgId -> coinIdByCg[cgId] }

        val imported = gate.write {
            if (fullOverwrite) {
                restoreStore.deleteAccountBusinessRowsWithinTx(accountId)
            }
            restoreStore.applyWithinTx(
                accountId = accountId,
                dek = s.dek,
                plan = plan,
                coinIdOf = coinIdOf,
                snapshotStore = snapshotStore,
            )
        }

        val postAnomalous = replayAnomalousCoins(accountId).toSet()
        val newAnomalous = (postAnomalous - preAnomalous).toList()
        val preExistingAnomalous = (postAnomalous intersect preAnomalous).toList()
        val now = Instant.now()
        settingsStore.putAccountSetting(accountId, SETTING_LAST_RESTORE, now.toString())
        logger.info(
            "backup restored | mode={} | imported(tx={} flows={} recons={} keys={}) dup_skipped={} missing_coins={}",
            mode, imported.transactions, imported.capitalFlows, imported.reconciliationRecords,
            imported.apiKeys, plan.duplicateSkipped, plan.missingCoinIds,
        )
        return RestoreSummary(
            mode = mode,
            imported = imported,
            duplicateSkipped = plan.duplicateSkipped,
            missingCoinSkipped = plan.missingCoinSkipped,
            missingCoinIds = plan.missingCoinIds,
            anomalousCoins = newAnomalous.sorted(),
            preExistingAnomalousCoins = preExistingAnomalous.sorted(),
            tempBackupPath = tempBackupPath,
            completedAt = now,
        )
    }

    // ---- T9.3 CSV 明文导出 ----

    override suspend fun exportCsv(kind: CsvExportKind, path: Path) {
        val accountId = requireSession().account.id
        val lines = when (kind) {
            CsvExportKind.TRANSACTIONS -> CsvExportWriter.transactions(ledgerRead.listTransactionsFull(accountId))
            CsvExportKind.FUNDS -> {
                val flows = ledgerRead.listFlowsFull(accountId)
                val recons = ledgerRead.listReconsFull(accountId)
                val symbolCache = HashMap<Long, String?>()
                suspend fun symbolOf(coinId: Long): String? =
                    symbolCache.getOrPut(coinId) { catalog.getById(coinId)?.symbol }
                val rows = flows.map { row ->
                    CsvExportWriter.FundCsvRow(
                        timeText = row.flowTime,
                        typeText = if (row.type == "WITHDRAWAL") "撤资" else "增资",
                        coin = symbolOf(row.coinId) ?: row.currency,
                        amount = row.amount,
                        baseAmount = row.baseAmount,
                        sourceDest = row.sourceDest,
                        notes = row.notes,
                        uuid = row.uuid,
                    )
                } + recons.map { row ->
                    CsvExportWriter.FundCsvRow(
                        timeText = row.createdAt,
                        typeText = "校准",
                        coin = symbolOf(row.coinId) ?: row.symbol,
                        amount = row.delta,
                        baseAmount = row.baseAmount,
                        sourceDest = row.exchange,
                        notes = null,
                        uuid = row.uuid,
                    )
                }
                CsvExportWriter.funds(rows)
            }
            CsvExportKind.HOLDINGS -> CsvExportWriter.holdings(holdingsRows(accountId))
        }
        writeAtomic(path, (lines.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8))
        logger.info("csv exported | kind={} | path={}", kind, path)
    }

    /** 全量覆盖前的自动临时备份（PRD 5.2-6）：当前账户数据 → backups/pre-restore-<UTC>.cpro。 */
    private suspend fun createTempBackup(password: CharArray): Path {
        val target = backupsDir.resolve("pre-restore-" + nowFileStamp() + ".cpro")
        exportInternal(target, password, recordMetadata = false)
        return target
    }

    // ---- 内部：导出装配 ----

    private fun apiKeyRowsToPayload(accountId: Int, s: ActiveSession): List<com.wuzhufolio.domain.backup.CproApiKey> =
        apiKeys.list(accountId).map { rec ->
            com.wuzhufolio.domain.backup.CproApiKey(
                name = rec.name,
                exchangeName = rec.exchangeName,
                apiKey = crypto.decryptField(
                    rec.apiKeyCipher, s.dek, accountId.toString(), rec.id.toString(), "api_key",
                ),
                secretKey = crypto.decryptField(
                    rec.secretKeyCipher, s.dek, accountId.toString(), rec.id.toString(), "secret_key",
                ),
                passphrase = rec.passphraseCipher?.let {
                    crypto.decryptField(it, s.dek, accountId.toString(), rec.id.toString(), "passphrase")
                },
                extra = rec.extraCipher?.let {
                    crypto.decryptField(it, s.dek, accountId.toString(), rec.id.toString(), "extra")
                },
                lastSyncTime = rec.lastSyncTime,
                status = rec.status,
            )
        }

    /** 涉及币种（交易两腿 + 资金/校准币种）——快照导出范围（ADR-005 §3）。 */
    private fun involvedCoinIds(
        txRows: List<BackupTxRow>,
        flowRows: List<BackupFlowRow>,
        reconRows: List<BackupReconRow>,
    ): Set<Long> = buildSet {
        txRows.forEach { add(it.baseCoinId); add(it.quoteCoinId) }
        flowRows.forEach { add(it.coinId) }
        reconRows.forEach { add(it.coinId) }
    }

    private suspend fun snapshotsToPayload(coinIds: Set<Long>): List<CproSnapshot> {
        if (coinIds.isEmpty()) return emptyList()
        val cgCache = HashMap<Long, String?>()
        suspend fun cgOf(coinId: Long): String? = cgCache.getOrPut(coinId) { catalog.getById(coinId)?.cgId }
        return snapshotStore.listByCoinIds(coinIds).mapNotNull { row ->
            val cgId = cgOf(row.coinId) ?: return@mapNotNull null // 目录缺失行防御性跳过（coins 不删，理论不触发）
            CproSnapshot(
                coinCgId = cgId,
                fiat = row.fiat,
                price = row.price.toPlainString(),
                priceSource = row.source,
                recordedAt = row.recordedAt.toString(),
            )
        }
    }

    // ---- 内部：恢复装配 ----

    private suspend fun existingKeys(accountId: Int): BackupMergePlanner.ExistingKeys {
        val txRows = ledgerRead.listTransactionsFull(accountId)
        val flowRows = ledgerRead.listFlowsFull(accountId)
        val reconRows = ledgerRead.listReconsFull(accountId)
        val keyRows = apiKeys.list(accountId)
        val buckets = HashSet<String>()
        val cgCache = HashMap<Long, String?>()
        for ((coinId, fiat, at) in snapshotStore.listAllForBuckets()) {
            val cg = cgCache.getOrPut(coinId) { catalog.getById(coinId)?.cgId } ?: continue
            buckets += cg + "|" + fiat.uppercase() + "|" + (at.epochSecond / 3600)
        }
        return BackupMergePlanner.ExistingKeys(
            txUuids = txRows.map { it.uuid }.toSet(),
            txOrderKeys = txRows.mapNotNull { row ->
                row.exchangeOrderId?.let { row.exchange.uppercase() + "|" + it }
            }.toSet(),
            flowUuids = flowRows.map { it.uuid }.toSet(),
            reconUuids = reconRows.map { it.uuid }.toSet(),
            apiKeyKeys = keyRows.map { it.exchangeName.uppercase() + "|" + it.name }.toSet(),
            snapshotBuckets = buckets,
        )
    }

    /** 载荷内全部 cg_id ∩ 本地目录（可解析集——规划器据此跳过缺失币种行）。 */
    private suspend fun resolvableCoinIds(records: CproRecords): Set<String> {
        val cgIds = buildSet {
            records.transactions.forEach { add(it.baseCgId); add(it.quoteCgId) }
            records.capitalFlows.forEach { add(it.coinCgId) }
            records.reconciliationRecords.forEach { add(it.coinCgId) }
            records.priceSnapshots.forEach { add(it.coinCgId) }
        }
        return cgIds.filter { catalog.getByCgId(it) != null }.toSet()
    }

    /** 恢复后全量重放（LENIENT——导入路径例外），产出「持仓异常」币种符号。 */
    private suspend fun replayAnomalousCoins(accountId: Int): List<String> {
        val outcome = replayOutcome(accountId) ?: return emptyList()
        return outcome.anomalousCoins.mapNotNull { catalog.getByCgId(it)?.symbol }.distinct().sorted()
    }

    /** CSV 持仓汇总行（重放 LENIENT 期末持仓，数量非零）。 */
    private suspend fun holdingsRows(accountId: Int): List<CsvExportWriter.HoldingCsvRow> {
        val outcome = replayOutcome(accountId) ?: return emptyList()
        return outcome.holdings.values
            .filter { it.quantity.signum() != 0 }
            .map { holding ->
                val coin = catalog.getByCgId(holding.coinId)
                CsvExportWriter.HoldingCsvRow(
                    symbol = coin?.symbol ?: holding.coinId,
                    coinName = coin?.name ?: "",
                    quantity = holding.quantity,
                    avgCostFiat = holding.avgCostFiat,
                    costFiat = holding.costFiat,
                    realizedPnlFiat = holding.realizedPnlFiat,
                    estimated = holding.estimated,
                    anomalous = holding.anomalous,
                )
            }
            .sortedBy { it.symbol }
    }

    /** 全量事件装配 + 宽松重放（无任何业务记录返回 null）。 */
    private suspend fun replayOutcome(accountId: Int): com.wuzhufolio.domain.engine.ReplayOutcome? {
        val txRows = ledgerRead.listTransactionsFull(accountId)
        val flowRows = ledgerRead.listFlowsFull(accountId)
        val reconRows = ledgerRead.listReconsFull(accountId)
        if (txRows.isEmpty() && flowRows.isEmpty() && reconRows.isEmpty()) return null
        val build = assembler.build(
            txRows = txRows.map { it.toLedgerTxRow() },
            fundRows = flowRows.map { it.toFundRow() },
            reconRows = reconRows.map { it.toReconRow() },
            baseFiat = baseFiat(),
        )
        return ReplayEngine.replay(build.events, NegativePolicy.LENIENT)
    }

    private suspend fun symbolOf(coinId: Long): String? = catalog.getById(coinId)?.symbol

    private fun baseFiat(): String = settings.getGlobal("fiat")?.takeIf { it.isNotBlank() } ?: "USD"

    private fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun nowFileStamp(): String = Instant.now().toString().replace(":", "-")

    companion object {
        const val SETTING_LAST_BACKUP = "backup.last_at"
        const val SETTING_LAST_RESTORE = "restore.last_at"

        /** 应用版本（头部 app_version；构建注入随 M13 发布链落地，先取里程碑常量）。 */
        const val APP_VERSION = "0.1.0-dev"
    }
}

/** 全列行 → 引擎仓库行 / 载荷 DTO 的纯映射（金额十进制原串透传，零精度损耗）。 */
private object CproRecordsBuilder {

    fun tx(row: BackupTxRow, baseCg: String, quoteCg: String): com.wuzhufolio.domain.backup.CproTransaction =
        com.wuzhufolio.domain.backup.CproTransaction(
            uuid = row.uuid,
            exchange = row.exchange,
            exchangeOrderId = row.exchangeOrderId,
            pair = row.pair,
            baseCgId = baseCg,
            quoteCgId = quoteCg,
            type = row.type,
            price = row.price,
            quantity = row.quantity,
            fee = row.fee,
            feeCurrency = row.feeCurrency,
            transactionTime = row.transactionTime,
            notes = row.notes,
            createdAt = row.createdAt,
            source = row.source,
            priceStatus = row.priceStatus,
        )

    fun flow(row: BackupFlowRow, coinCg: String): com.wuzhufolio.domain.backup.CproCapitalFlow =
        com.wuzhufolio.domain.backup.CproCapitalFlow(
            uuid = row.uuid,
            type = row.type,
            amount = row.amount,
            baseAmount = row.baseAmount,
            currency = row.currency,
            coinCgId = coinCg,
            flowTime = row.flowTime,
            sourceDest = row.sourceDest,
            notes = row.notes,
            createdAt = row.createdAt,
            priceStatus = row.priceStatus,
        )

    fun recon(row: BackupReconRow, coinCg: String): com.wuzhufolio.domain.backup.CproReconciliation =
        com.wuzhufolio.domain.backup.CproReconciliation(
            uuid = row.uuid,
            symbol = row.symbol,
            coinCgId = coinCg,
            exchange = row.exchange,
            localQuantity = row.localQuantity,
            exchangeQuantity = row.exchangeQuantity,
            delta = row.delta,
            baseAmount = row.baseAmount,
            createdAt = row.createdAt,
        )
}

/** 交易全列行 → 引擎仓库行（重放输入；本地行 id 以序号替代——重放只依赖 (at, seq) 稳定序）。 */
private fun BackupTxRow.toLedgerTxRow(): LedgerTxRow = LedgerTxRow(
    id = id,
    accountId = 0,
    exchange = exchange,
    exchangeOrderId = exchangeOrderId,
    pair = pair,
    baseCoinId = baseCoinId,
    quoteCoinId = quoteCoinId,
    side = if (type == "SELL") Side.SELL else Side.BUY,
    price = BigDecimal(price),
    quantity = BigDecimal(quantity),
    fee = BigDecimal(fee),
    feeCurrency = feeCurrency,
    time = Instant.parse(transactionTime),
    notes = notes,
    source = source,
    uuid = uuid,
)

/** 资金全列行 → 引擎仓库行。 */
private fun BackupFlowRow.toFundRow(): FundFlowRow = FundFlowRow(
    id = id,
    accountId = 0,
    kind = if (type == "WITHDRAWAL") FlowKind.WITHDRAWAL else FlowKind.DEPOSIT,
    amount = BigDecimal(amount),
    baseAmount = BigDecimal(baseAmount),
    currency = currency,
    coinId = coinId,
    time = Instant.parse(flowTime),
    sourceDest = sourceDest,
    notes = notes,
    uuid = uuid,
)

/** 校准全列行 → 引擎仓库行。 */
private fun BackupReconRow.toReconRow(): ReconciliationRecordRow = ReconciliationRecordRow(
    id = id,
    accountId = 0,
    symbol = symbol,
    coinId = coinId,
    exchange = exchange,
    localQuantity = BigDecimal(localQuantity),
    exchangeQuantity = BigDecimal(exchangeQuantity),
    delta = BigDecimal(delta),
    baseAmount = BigDecimal(baseAmount),
    uuid = uuid,
    createdAt = Instant.parse(createdAt),
)

/** 头部/载荷时间解析（UTC ISO-8601；OffsetDateTime 与 Instant 两种形态兼容）。 */
internal fun parseInstant(text: String?): Instant? {
    val t = text?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    return try {
        OffsetDateTime.parse(t).toInstant()
    } catch (e: DateTimeParseException) {
        try {
            Instant.parse(t)
        } catch (e2: DateTimeParseException) {
            null
        }
    }
}

/** 原子写文件：临时文件 + rename（进程中断不留半文件）。 */
internal fun writeAtomic(path: Path, bytes: ByteArray) {
    path.parent?.let { Files.createDirectories(it) }
    val tmp = path.resolveSibling(path.fileName.toString() + ".tmp-" + System.nanoTime())
    Files.write(tmp, bytes)
    try {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (@Suppress("SwallowedException") e: AtomicMoveNotSupportedException) {
        // 原子 rename 不被文件系统支持 → 降级为普通 rename（功能等价，进程中断窗口极小）
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
    }
}
