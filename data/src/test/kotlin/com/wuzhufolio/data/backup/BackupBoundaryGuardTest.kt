package com.wuzhufolio.data.backup

import com.wuzhufolio.data.accounts.AccountRepository
import com.wuzhufolio.data.accounts.ActiveSession
import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.data.catalog.SqlCoinCatalog
import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.exchange.ApiKeyRepository
import com.wuzhufolio.data.ledger.FeeRuleRepository
import com.wuzhufolio.data.ledger.LedgerEventAssembler
import com.wuzhufolio.data.ledger.TransactionEventBuilder
import com.wuzhufolio.data.market.DeviceSecretStore
import com.wuzhufolio.data.market.MarketConfig
import com.wuzhufolio.data.market.PriceSnapshotRepository
import com.wuzhufolio.data.randomDbKey
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.accounts.AccountSummary
import com.wuzhufolio.domain.backup.BackupService
import com.wuzhufolio.domain.backup.CproCodec
import com.wuzhufolio.domain.backup.CproRecords
import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import com.wuzhufolio.domain.security.CryptoService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * 硬约束 5 边界守护测试（M13 T13.1 安全自查；对应审计缺口 F-8/F-9）：
 * 1. 备份载荷**只含 7 张账户级表**（模型形状守护）；
 * 2. **行情 Key（设备级秘密）绝不进备份**（ADR-002 §2.1 方案甲 / ADR-005 §3）；
 * 3. 账户行、coins 目录、全局设置（非账户级）不进备份（PRD / ADR-005 §3 内容范围）。
 *
 * 此前这三条只有结构性保证（构造参数与模型字段），无回归护栏——本测试固化。
 */
class BackupBoundaryGuardTest {

    private class Env : AutoCloseable {
        val dir: Path = Files.createTempDirectory("wuzhufolio-backup-boundary")
        val db: WzDatabase = WzDatabase(dir.resolve("test.db"), randomDbKey())
        val gate: DbGate = DbGate(db)
        val settings: SettingsRepository = SettingsRepository(gate)
        val sessions: ActiveSessionStore = ActiveSessionStore()
        val catalog: SqlCoinCatalog = SqlCoinCatalog(gate)
        val crypto: CryptoService = CryptoService()
        val snapshots: PriceSnapshotRepository = PriceSnapshotRepository(gate)
        val service: BackupService = DefaultBackupService(
            sessions = sessions,
            crypto = crypto,
            gate = gate,
            catalog = catalog,
            settings = settings,
            ledgerRead = BackupLedgerReadStore(gate),
            feeRules = FeeRuleRepository(gate),
            apiKeys = ApiKeyRepository(gate),
            assembler = LedgerEventAssembler(catalog, TransactionEventBuilder(catalog, snapshots)),
            settingsStore = BackupSettingsStore(gate),
            snapshotStore = BackupSnapshotStore(gate),
            restoreStore = BackupRestoreStore(crypto),
            backupsDir = dir.resolve("backups"),
            logger = LoggerFactory.getLogger("backup-boundary-test"),
        )

        init {
            db.migrateToLatest()
            runBlocking {
                catalog.refreshDirectory(
                    listOf(
                        CoinDirectoryEntry("bitcoin", "btc", "Bitcoin"),
                        CoinDirectoryEntry("tether", "usdt", "Tether"),
                    ),
                )
            }
        }

        fun login(username: String, dek: ByteArray) {
            val accounts = AccountRepository(gate)
            val record = accounts.createWrapped(username, "hash", "00".repeat(16), "p") { mid ->
                "wrapped-" + mid
            }
            sessions.set(ActiveSession(AccountSummary(record.id, username), dek))
        }

        override fun close() {
            db.close()
        }
    }

    private val backupPassword = "Backup-Pass-123".toCharArray()

    @Test
    fun `backup payload model carries exactly the seven account scoped tables`() {
        val fields = CproRecords::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .map { it.name }
            .filterNot { it == "Companion" || it.startsWith("$") }
            .toSet()
        assertEquals(
            setOf(
                "transactions", "capitalFlows", "reconciliationRecords", "feeRules",
                "apiKeys", "settings", "priceSnapshots",
            ),
            fields,
            "备份载荷范围（ADR-005 §3）：不得新增 accounts/coins/exchange_coin_map/sync_logs 等字段",
        )
    }

    @Test
    fun `market api key never enters the backup file`() {
        val env = Env()
        env.use {
            val dek = ByteArray(32) { (it + 7).toByte() }
            env.login("alice", dek)
            // 设备级秘密：行情 Key 按设备密钥加密存 settings 全局行（ADR-002 §2.1）
            val deviceKey = ByteArray(32) { (it + 3).toByte() }
            val deviceSecrets = DeviceSecretStore(deviceKey, env.settings, LoggerFactory.getLogger("test"))
            val secret = "CG-DEVICE-SECRET-0123456789abcdef"
            deviceSecrets.put(MarketConfig.KEY_CG, MarketConfig.PURPOSE_CG, secret)
            deviceSecrets.put(MarketConfig.KEY_CMC, MarketConfig.PURPOSE_CMC, "CMC-DEVICE-SECRET-abcdef0123456789")

            val path = env.dir.resolve("boundary.cpro")
            runBlocking { env.service.exportBackup(path, backupPassword.copyOf()) }

            val bytes = Files.readAllBytes(path)
            val payload = CproCodec.decode(bytes, backupPassword.copyOf())
            assertFalse(
                String(bytes, Charsets.ISO_8859_1).contains(secret),
                "行情 Key 明文绝不得出现在 .cpro 文件任何位置（含明文头部）",
            )
            assertFalse(
                payload.toString().contains(secret),
                "行情 Key 不得进入解密后的载荷",
            )
            assertFalse(
                payload.toString().contains(MarketConfig.KEY_CG),
                "行情 Key 条目（含键名）不得进入载荷",
            )
            assertTrue(
                payload.records.settings.isEmpty(),
                "全局行不属于任何账户：备份载荷的 settings 只应是账户级行（实际：" +
                    payload.records.settings.size + " 行）",
            )
            deviceSecrets.close()
        }
    }

    @Test
    fun `account rows coins and global settings stay out of the backup`() {
        val env = Env()
        env.use {
            val dek = ByteArray(32) { (it + 11).toByte() }
            env.login("bob-secret-username", dek)
            // 全局设置（非账户级）：账户无关偏好，不属于备份内容（ADR-005 §3）
            env.settings.putGlobal("market.quota", "{\"used\":42}")
            env.settings.putGlobal("theme", "DARK")

            val path = env.dir.resolve("boundary2.cpro")
            runBlocking { env.service.exportBackup(path, backupPassword.copyOf()) }

            val payload = CproCodec.decode(Files.readAllBytes(path), backupPassword.copyOf())
            val text = payload.toString()
            assertFalse(text.contains("bob-secret-username"), "accounts 表不在备份范围（PRD 5.2：账户不随备份）")
            assertFalse(text.contains("market.quota"), "全局设置行不进备份（行情 Key 所在命名空间）")
            assertFalse(text.contains("Bitcoin"), "coins 目录为全局公共数据，不进备份（ADR-005 §3）")
        }
    }
}
