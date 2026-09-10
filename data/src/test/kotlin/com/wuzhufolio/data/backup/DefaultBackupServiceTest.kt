package com.wuzhufolio.data.backup

import com.wuzhufolio.data.accounts.AccountRepository
import com.wuzhufolio.data.accounts.ActiveSession
import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.data.catalog.SqlCoinCatalog
import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.exchange.ApiKeyRepository
import com.wuzhufolio.data.ledger.FundFlowRepository
import com.wuzhufolio.data.ledger.FeeRuleRepository
import com.wuzhufolio.data.ledger.LedgerEventAssembler
import com.wuzhufolio.data.ledger.LedgerTransactionRepository
import com.wuzhufolio.data.ledger.NewFundFlowRow
import com.wuzhufolio.data.ledger.NewLedgerTxRow
import com.wuzhufolio.data.ledger.TransactionEventBuilder
import com.wuzhufolio.data.market.PriceSnapshotRepository
import com.wuzhufolio.data.market.SnapshotWrite
import com.wuzhufolio.data.randomDbKey
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.accounts.AccountSummary
import com.wuzhufolio.domain.backup.BackupService
import com.wuzhufolio.domain.backup.CproCodec
import com.wuzhufolio.domain.backup.CproDecodeException
import com.wuzhufolio.domain.backup.CproHeader
import com.wuzhufolio.domain.backup.CproPayload
import com.wuzhufolio.domain.backup.CproRecords
import com.wuzhufolio.domain.backup.CproTransaction
import com.wuzhufolio.domain.backup.CsvExportKind
import com.wuzhufolio.domain.backup.RestoreMode
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.security.CryptoService
import java.math.BigDecimal
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * 备份恢复服务端到端测试（M9 · T9.1–T9.3 · 黄金用例 10「备份增量合并幂等」真实库形状 + 全量覆盖 + CSV 无密钥）。
 */
class DefaultBackupServiceTest {

    private class BackupTestEnv : AutoCloseable {
        val dir: Path = Files.createTempDirectory("wuzhufolio-backup-test")
        val db: WzDatabase = WzDatabase(dir.resolve("test.db"), randomDbKey())
        val gate: DbGate = DbGate(db)
        val settings: SettingsRepository = SettingsRepository(gate)
        val accounts: AccountRepository = AccountRepository(gate)
        val sessions: ActiveSessionStore = ActiveSessionStore()
        val catalog: SqlCoinCatalog = SqlCoinCatalog(gate)
        val snapshotsRepo: PriceSnapshotRepository = PriceSnapshotRepository(gate)
        val txRepo: LedgerTransactionRepository = LedgerTransactionRepository(gate)
        val fundsRepo: FundFlowRepository = FundFlowRepository(gate)
        val reconsRepo: com.wuzhufolio.data.ledger.ReconciliationRepository =
            com.wuzhufolio.data.ledger.ReconciliationRepository(gate)
        val feeRulesRepo: FeeRuleRepository = FeeRuleRepository(gate)
        val apiKeysRepo: ApiKeyRepository = ApiKeyRepository(gate)
        val crypto: CryptoService = CryptoService()
        val settingsStore: BackupSettingsStore = BackupSettingsStore(gate)
        val snapshotStore: BackupSnapshotStore = BackupSnapshotStore(gate)
        val ledgerRead: BackupLedgerReadStore = BackupLedgerReadStore(gate)
        val assembler: LedgerEventAssembler = LedgerEventAssembler(
            catalog,
            TransactionEventBuilder(catalog, snapshotsRepo),
        )
        val service: BackupService = DefaultBackupService(
            sessions = sessions,
            crypto = crypto,
            gate = gate,
            catalog = catalog,
            settings = settings,
            ledgerRead = ledgerRead,
            feeRules = feeRulesRepo,
            apiKeys = apiKeysRepo,
            assembler = assembler,
            settingsStore = settingsStore,
            snapshotStore = snapshotStore,
            restoreStore = BackupRestoreStore(crypto),
            backupsDir = dir.resolve("backups"),
            logger = LoggerFactory.getLogger("backup-test"),
        )

        fun login(username: String): ActiveSession {
            val account = runBlocking {
                accounts.createWrapped(username, "hash", "00".repeat(16), "p") { mid -> "wrapped-" + mid }
            }
            val session = ActiveSession(AccountSummary(account.id, username), dek())
            sessions.set(session)
            return session
        }

        suspend fun coin(symbol: String): CatalogCoin =
            catalog.getBySymbol(symbol.uppercase()).firstOrNull() ?: error("coin $symbol not seeded")

        fun cproPath(name: String): Path = dir.resolve(name)

        /** 仅测试用：清空全局快照表（模拟新设备）。 */
        suspend fun clearAllForTest() {
            gate.writeBlocking {
                com.wuzhufolio.data.market.PriceSnapshotsTable.deleteWhere {
                    com.wuzhufolio.data.market.PriceSnapshotsTable.coinId greater 0
                }
            }
        }

        override fun close() {
            db.close()
        }

        companion object {
            fun dek(): ByteArray = ByteArray(32) { (it + 1).toByte() }
        }

        init {
            db.migrateToLatest()
            runBlocking {
                catalog.refreshDirectory(
                    listOf(
                        CoinDirectoryEntry("bitcoin", "btc", "Bitcoin"),
                        CoinDirectoryEntry("tether", "usdt", "Tether"),
                        CoinDirectoryEntry("usd-coin", "usdc", "USD Coin"),
                    ),
                )
            }
        }
    }

    private fun newEnv(): BackupTestEnv = BackupTestEnv()

    /** 账户 A 种子：增资 10,000 USDT → 买入 0.1 BTC @50,000（费 10 USDT）→ 快照 2 条 → 费率规则 → API 密钥 → 账户设置。 */
    private suspend fun seedAccountA(env: BackupTestEnv) {
        val session = env.sessions.get() ?: error("login first")
        val accountId = session.account.id
        val usdt = env.coin("USDT")
        val btc = env.coin("BTC")

        env.fundsRepo.insert(
            NewFundFlowRow(
                accountId = accountId,
                kind = com.wuzhufolio.domain.engine.FlowKind.DEPOSIT,
                amount = BigDecimal("10000"),
                baseAmount = BigDecimal("10000"),
                currency = "USDT",
                coinId = usdt.id.toInt(),
                time = Instant.parse("2025-01-01T00:00:00Z"),
                sourceDest = "交易所转入",
                notes = "初始本金",
                priceStatus = "OK",
            ),
        )
        env.txRepo.insert(
            NewLedgerTxRow(
                accountId = accountId,
                exchange = "BINANCE",
                exchangeOrderId = null,
                pair = "BTC/USDT",
                baseCoinId = btc.id.toInt(),
                quoteCoinId = usdt.id.toInt(),
                side = Side.BUY,
                price = BigDecimal("50000"),
                quantity = BigDecimal("0.1"),
                fee = BigDecimal("10"),
                feeCurrency = "USDT",
                time = Instant.parse("2025-01-02T08:00:00Z"),
                notes = "备注,含逗号与\"引号\"",
                source = "Manual",
                priceStatus = "OK",
            ),
        )
        seedSnapshotsRulesKeySetting(env, accountId, btc.id.toInt(), usdt.id.toInt(), session)
    }

    /** 快照/费率/密钥/账户设置种子（seedAccountA 后半段）。 */
    private suspend fun seedSnapshotsRulesKeySetting(
        env: BackupTestEnv,
        accountId: Int,
        btcId: Int,
        usdtId: Int,
        session: ActiveSession,
    ) {
        val cgSource = com.wuzhufolio.domain.market.PriceSource.COINGECKO
        env.snapshotsRepo.upsert(
            SnapshotWrite(btcId, "USD", BigDecimal("50000"), cgSource, Instant.parse("2025-01-02T08:00:00Z")),
        )
        env.snapshotsRepo.upsert(
            SnapshotWrite(usdtId, "USD", BigDecimal("1"), cgSource, Instant.parse("2025-01-02T08:00:00Z")),
        )
        env.feeRulesRepo.upsert(accountId, "", BigDecimal("0.1"), BigDecimal("0.1"))
        env.feeRulesRepo.upsert(accountId, "BINANCE", BigDecimal("0.05"), BigDecimal("0.06"))
        env.apiKeysRepo.create(accountId, "主号", "BINANCE") { rowId ->
            com.wuzhufolio.data.exchange.ApiKeyCiphers(
                apiKey = env.crypto.encryptField(
                    "AKIA-TEST-KEY", session.dek, accountId.toString(), rowId.toString(), "api_key",
                ),
                secretKey = env.crypto.encryptField(
                    "SECRET-TEST-XYZ", session.dek, accountId.toString(), rowId.toString(), "secret_key",
                ),
            )
        }
        env.settingsStore.putAccountSetting(accountId, "test.pref", "v1")
    }

    private suspend fun countRows(env: BackupTestEnv, accountId: Int): Int =
        env.ledgerRead.listTransactionsFull(accountId).size +
            env.ledgerRead.listFlowsFull(accountId).size +
            env.ledgerRead.listReconsFull(accountId).size

    // ---- T9.1：导出 → 恢复往返无损（跨账户） ----

    @Test
    fun `export and restore roundtrip is lossless across accounts`() = runBlocking {
        newEnv().use { env ->
            val a = env.login("alice")
            seedAccountA(env)
            val file = env.cproPath("backup.cpro")
            val password = "Backup-Pass-1".toCharArray()

            val summary = env.service.exportBackup(file, password)
            assertEquals(1, summary.counts.transactions)
            assertEquals(1, summary.counts.capitalFlows)
            assertEquals(2, summary.counts.feeRules)
            assertEquals(1, summary.counts.apiKeys)
            assertEquals(1, summary.counts.settings)
            assertEquals(2, summary.counts.priceSnapshots)
            assertEquals(0, summary.counts.reconciliationRecords)

            // 摘要预览（无需密码）
            val preview = env.service.previewBackup(file)
            assertEquals(1, preview.formatVersion)
            assertEquals(summary.counts, preview.counts)

            // 跨账户恢复（B：同库另一账户）——恢复只依赖备份密码，与原账户无关
            val b = env.login("bob")
            val prepared = env.service.prepareRestore(file, password)
            assertEquals(1, prepared.plan.transactions.size)
            assertEquals(0, prepared.plan.missingCoinSkipped)
            val restoreSummary = env.service.restoreBackup(file, password, RestoreMode.MERGE)
            assertEquals(1, restoreSummary.imported.transactions)
            assertEquals(1, restoreSummary.imported.capitalFlows)
            assertEquals(2, restoreSummary.imported.feeRules)
            assertEquals(1, restoreSummary.imported.apiKeys)
            assertEquals(1, restoreSummary.imported.settings)
            assertEquals(
                0,
                restoreSummary.imported.priceSnapshots,
                "快照属全局公共表：同库恢复时同桶行已存在 → 幂等跳过（数据本身已可用）",
            )
            assertTrue(restoreSummary.anomalousCoins.isEmpty(), "增资 10000 后买入不应产生异常持仓")

            // 往返无损：行值/审计字段一致
            val aTx = env.ledgerRead.listTransactionsFull(a.account.id).single()
            val bTx = env.ledgerRead.listTransactionsFull(b.account.id).single()
            assertEquals(aTx.uuid, bTx.uuid, "uuid 去重键跨账户保真")
            assertEquals(aTx.createdAt, bTx.createdAt)
            assertEquals(aTx.source, bTx.source)
            assertEquals(aTx.notes, bTx.notes)
            assertEquals(aTx.price, bTx.price)
            val bKey = env.apiKeysRepo.list(b.account.id).single()
            val dek = BackupTestEnv.dek()
            assertEquals(
                "AKIA-TEST-KEY",
                env.crypto.decryptField(bKey.apiKeyCipher, dek, b.account.id.toString(), bKey.id.toString(), "api_key"),
                "凭证以目标账户 DEK 重加密后可解回原明文（PRD 5.2-9）",
            )
            assertTrue(
                env.settingsStore.listAccountSettings(b.account.id).any { it.key == "test.pref" && it.value == "v1" },
                "账户级设置随备份恢复",
            )
            assertEquals(2, env.snapshotsRepo.count(), "全局快照表不被重复写入")
            // 元数据
            val meta = env.service.backupMetadata()
            assertTrue(meta.lastRestoreAt != null, "恢复后记录 restore.last_at")
        }
    }

    // ---- 黄金用例 10：同一 .cpro 导入两次幂等 ----

    @Test
    fun `golden case 10 - restoring the same cpro twice is idempotent`() = runBlocking {
        newEnv().use { env ->
            env.login("alice")
            seedAccountA(env)
            val file = env.cproPath("backup.cpro")
            val password = "Backup-Pass-1".toCharArray()
            env.service.exportBackup(file, password)

            val b = env.login("bob")
            env.service.restoreBackup(file, password, RestoreMode.MERGE)
            val rowsAfterFirst = countRows(env, b.account.id)
            val txUuidsAfterFirst = env.ledgerRead.listTransactionsFull(b.account.id).map { it.uuid }.toSet()

            val second = env.service.restoreBackup(file, password, RestoreMode.MERGE)
            assertEquals(0, second.imported.transactions + second.imported.capitalFlows +
                second.imported.reconciliationRecords + second.imported.apiKeys, "第二次导入不产生重复记录")
            assertEquals(0, second.imported.priceSnapshots, "快照按桶幂等")
            assertEquals(rowsAfterFirst, countRows(env, b.account.id))
            assertEquals(txUuidsAfterFirst, env.ledgerRead.listTransactionsFull(b.account.id).map { it.uuid }.toSet())
            assertTrue(second.duplicateSkipped > 0)
        }
    }

    // ---- 全量覆盖：二次确认口径的服务面 + 临时备份 + 不清空全局表 ----

    @Test
    fun `full overwrite creates temp backup and replaces account data`() = runBlocking {
        newEnv().use { env ->
            env.login("alice")
            seedAccountA(env)
            val file = env.cproPath("backup.cpro")
            val password = "Backup-Pass-1".toCharArray()
            env.service.exportBackup(file, password)

            val b = env.login("bob")
            env.service.restoreBackup(file, password, RestoreMode.MERGE)
            // 恢复后 B 多记一笔（全量覆盖应清掉）
            val usdt = env.coin("USDT")
            val btc = env.coin("BTC")
            env.txRepo.insert(
                NewLedgerTxRow(
                    accountId = b.account.id,
                    exchange = "BINANCE",
                    exchangeOrderId = null,
                    pair = "BTC/USDT",
                    baseCoinId = btc.id.toInt(),
                    quoteCoinId = usdt.id.toInt(),
                    side = Side.BUY,
                    price = BigDecimal("60000"),
                    quantity = BigDecimal("0.01"),
                    fee = BigDecimal.ZERO,
                    feeCurrency = "USDT",
                    time = Instant.parse("2025-03-01T00:00:00Z"),
                    notes = null,
                    source = "Manual",
                    priceStatus = "OK",
                ),
            )
            val snapshotsBefore = env.snapshotsRepo.count()

            val overwrite = env.service.restoreBackup(file, password, RestoreMode.FULL_OVERWRITE)
            assertEquals(RestoreMode.FULL_OVERWRITE, overwrite.mode)
            val temp = overwrite.tempBackupPath
            assertEquals(true, temp != null && Files.exists(temp), "覆盖前自动生成临时 .cpro")
            assertEquals(1, overwrite.imported.transactions, "清空后按载荷全量重导")
            assertEquals(1, env.ledgerRead.listTransactionsFull(b.account.id).size, "额外交易已被覆盖清除")
            assertEquals(snapshotsBefore, env.snapshotsRepo.count(), "全局公共表 price_snapshots 不清空")
            // 临时备份可解（同备份密码）
            val tempHeader = CproCodec.parseHeader(Files.readAllBytes(temp!!))
            assertEquals(CproCodec.FORMAT_VERSION, tempHeader.formatVersion)
            val tempPayload = CproCodec.decode(Files.readAllBytes(temp), password)
            assertEquals(2, tempPayload.records.transactions.size, "临时备份捕获覆盖前 B 的全部业务数据")
        }
    }

    @Test
    fun `snapshots are restored when the global table lacks them`() = runBlocking {
        newEnv().use { env ->
            env.login("alice")
            seedAccountA(env)
            val file = env.cproPath("backup.cpro")
            val password = "Backup-Pass-1".toCharArray()
            env.service.exportBackup(file, password)
            // 模拟新设备：清空全局快照表（coins/exchange_coin_map 保留）
            env.clearAllForTest()

            val b = env.login("bob")
            val summary = env.service.restoreBackup(file, password, RestoreMode.MERGE)
            assertEquals(2, summary.imported.priceSnapshots, "空快照表 → 按载荷全量插入")
            assertEquals(2, env.snapshotsRepo.count())
        }
    }

    // ---- 缺失币种：跳过并计数，不破坏导入 ----

    @Test
    fun `rows referencing missing coins are skipped with reasons`() = runBlocking {
        newEnv().use { env ->
            env.login("alice")
            // 手工构造载荷：引用目录中不存在的 cg_id
            val payload = CproPayload(
                meta = com.wuzhufolio.domain.backup.CproMeta("2026-09-09T00:00:00Z", "USD"),
                records = CproRecords(
                    transactions = listOf(
                        CproTransaction(
                            uuid = "u-unknown",
                            exchange = "BINANCE",
                            exchangeOrderId = null,
                            pair = "XYZ/USDT",
                            baseCgId = "unknown-coin",
                            quoteCgId = "tether",
                            type = "BUY",
                            price = "1",
                            quantity = "1",
                            fee = "0",
                            transactionTime = "2025-01-01T00:00:00Z",
                            createdAt = "2025-01-01T00:00:00Z",
                            source = "Manual",
                            priceStatus = "OK",
                        ),
                    ),
                ),
            )
            val header = CproHeader(
                formatVersion = CproCodec.FORMAT_VERSION,
                appVersion = "test",
                exportedAt = "2026-09-09T00:00:00Z",
                counts = com.wuzhufolio.domain.backup.CproCounts(1, 0, 0, 0, 0, 0, 0),
                range = com.wuzhufolio.domain.backup.CproRange(null, null),
                kdf = com.wuzhufolio.domain.backup.CproKdf(
                    CproCodec.KDF_ALG,
                    CproCodec.hex(ByteArray(16)),
                    1024, 1, 1,
                ),
                cipher = CproCodec.CIPHER,
            )
            val file = env.cproPath("missing-coin.cpro")
            Files.write(file, CproCodec.encode(header, payload, "Backup-Pass-1".toCharArray()))

            val prepared = env.service.prepareRestore(file, "Backup-Pass-1".toCharArray())
            assertEquals(1, prepared.plan.missingCoinSkipped)
            assertTrue(prepared.plan.missingCoinIds.contains("unknown-coin"))
            val summary = env.service.restoreBackup(file, "Backup-Pass-1".toCharArray(), RestoreMode.MERGE)
            assertEquals(0, summary.imported.transactions)
            assertEquals(listOf("unknown-coin"), summary.missingCoinIds)
        }
    }

    @Test
    fun `same-account merge restore reports no new anomalies for pre-existing ones`() = runBlocking {
        newEnv().use { env ->
            val a = env.login("alice")
            val usdt = env.coin("USDT")
            val btc = env.coin("BTC")
            // 无买入先卖出 1 BTC → 账户既存「持仓异常」（LENIENT 导入路径例外）
            env.txRepo.insert(
                NewLedgerTxRow(
                    accountId = a.account.id,
                    exchange = "BINANCE",
                    exchangeOrderId = null,
                    pair = "BTC/USDT",
                    baseCoinId = btc.id.toInt(),
                    quoteCoinId = usdt.id.toInt(),
                    side = Side.SELL,
                    price = BigDecimal("50000"),
                    quantity = BigDecimal("1"),
                    fee = BigDecimal.ZERO,
                    feeCurrency = "USDT",
                    time = Instant.parse("2025-01-01T00:00:00Z"),
                    notes = null,
                    source = "Manual",
                    priceStatus = "OK",
                ),
            )
            val file = env.cproPath("backup.cpro")
            val password = "Backup-Pass-1".toCharArray()
            env.service.exportBackup(file, password)

            val summary = env.service.restoreBackup(file, password, RestoreMode.MERGE)
            assertEquals(0, summary.imported.transactions, "同账户回导：uuid 全命中，无新增")
            assertTrue(
                summary.anomalousCoins.isEmpty(),
                "既存异常不应记为「本次导入新产生」",
            )
            assertEquals(listOf("BTC"), summary.preExistingAnomalousCoins, "既存异常随摘要透明披露")
        }
    }

    // ---- 密码语义 ----

    @Test
    fun `wrong password is rejected with typed reason`() = runBlocking {
        newEnv().use { env ->
            env.login("alice")
            seedAccountA(env)
            val file = env.cproPath("backup.cpro")
            env.service.exportBackup(file, "Backup-Pass-1".toCharArray())
            val e = assertFailsWith<CproDecodeException> {
                env.service.prepareRestore(file, "Wrong-Pass-9".toCharArray())
            }
            assertEquals(CproDecodeException.Reason.WRONG_PASSWORD_OR_CORRUPTED, e.reason)
        }
    }

    @Test
    fun `weak export password is rejected`() = runBlocking {
        newEnv().use { env ->
            env.login("alice")
            seedAccountA(env)
            val e = assertFailsWith<IllegalArgumentException> {
                env.service.exportBackup(env.cproPath("weak.cpro"), "short".toCharArray())
            }
            assertTrue(e.message!!.contains("8 位"))
        }
    }

    // ---- CSV 明文导出：不含任何 API 密钥 ----

    @Test
    fun `csv exports contain no api key material`() = runBlocking {
        newEnv().use { env ->
            env.login("alice")
            seedAccountA(env)
            val txCsv = env.cproPath("tx.csv")
            val fundsCsv = env.cproPath("funds.csv")
            val holdingsCsv = env.cproPath("holdings.csv")
            env.service.exportCsv(CsvExportKind.TRANSACTIONS, txCsv)
            env.service.exportCsv(CsvExportKind.FUNDS, fundsCsv)
            env.service.exportCsv(CsvExportKind.HOLDINGS, holdingsCsv)

            val txText = Files.readString(txCsv)
            assertTrue(txText.contains("BINANCE") && txText.contains("BTC/USDT"))
            assertTrue(!txText.contains("AKIA-TEST-KEY") && !txText.contains("SECRET-TEST-XYZ"))
            assertTrue(txText.contains("备注,含逗号") || txText.contains("\"备注,含逗号与\"\"引号\"\"\""), "CSV 转义")

            val fundsText = Files.readString(fundsCsv)
            assertTrue(fundsText.contains("增资"))
            assertTrue(!fundsText.contains("SECRET-TEST-XYZ"))

            val holdingsText = Files.readString(holdingsCsv)
            assertTrue(holdingsText.contains("btc") || holdingsText.contains("BTC"))
            assertTrue(!holdingsText.contains("AKIA-TEST-KEY"))
        }
    }
}
