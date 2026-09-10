package com.wuzhufolio.data.settings

import com.wuzhufolio.data.accounts.AccountRepository
import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.exchange.SyncLogRepository
import com.wuzhufolio.data.logging.FileLogAccess
import com.wuzhufolio.data.market.SettingsQuotaLedger
import com.wuzhufolio.data.randomDbKey
import com.wuzhufolio.domain.market.QuotaCallKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 诊断报告聚合（M10 T10.3）：受限清单字段来源 + 日志片段再脱敏。 */
class DiagnosticsServiceTest {

    private class Env : AutoCloseable {
        val db: WzDatabase = WzDatabase(
            Files.createTempDirectory("wuzhufolio-diag").resolve("diag.db"),
            randomDbKey(),
        )
        val gate: DbGate = DbGate(db)
        val settings = SettingsRepository(gate)
        val logDir: Path = Files.createTempDirectory("wuzhufolio-diag-logs")

        init {
            db.migrateToLatest()
        }

        val service = DefaultDiagnosticsService(
            appVersion = "0.1.0-test",
            schemaVersion = 12,
            quota = SettingsQuotaLedger(settings),
            syncCount = { SyncLogRepository(gate).countAll() },
            lastSyncAt = { SyncLogRepository(gate).lastSyncAt() },
            logTail = { FileLogAccess(logDir).tailLines(50) },
        )

        override fun close() {
            db.close()
        }
    }

    @Test
    fun `report aggregates version schema counts and redacted log tail`() {
        Env().use { env ->
            kotlinx.coroutines.runBlocking {
                // 行情调用计数经额度账本记数（跨月归零语义由账本自身保证，测试避免写死月份键）
                val quota = SettingsQuotaLedger(env.settings)
                repeat(5) { quota.record(QuotaCallKind.CURRENT) }
                quota.record(QuotaCallKind.HISTORY)
                val log = env.logDir.resolve("wuzhufolio.log")
                Files.write(
                    log,
                    listOf(
                        "2026-09-10 08:00:00.000 INFO [main] w - bootstrap ok | schema=12",
                        "2026-09-10 08:00:01.000 INFO [main] w - market_api_key=CG-DEMO-0123456789abcdef",
                        "2026-09-10 08:00:01.000 INFO [main] w - x",
                    ),
                )
                val report = env.service.generate()
                assertEquals("0.1.0-test", report.appVersion)
                assertEquals(12, report.schemaVersion)
                assertEquals(5, report.marketCalls[QuotaCallKind.CURRENT])
                assertEquals(0, report.syncLogCount)
                assertEquals(null, report.lastSyncAt)
                assertTrue(report.logTail.size == 3)
                assertTrue(report.logTail.none { "CG-DEMO" in it }, "报告内日志片段必须脱敏")
                assertTrue(report.logTail.any { "bootstrap ok" in it })
            }
        }
    }

    @Test
    fun `sync counts reflect sync_logs rows`() {
        Env().use { env ->
            kotlinx.coroutines.runBlocking {
                val accounts = AccountRepository(env.gate)
                val account = accounts
                    .createWrapped("diag-user", "hash", "00".repeat(16), "p") { id -> "wrapped-" + id }
                val repo = SyncLogRepository(env.gate)
                repo.append(account.id, null, com.wuzhufolio.domain.exchange.SyncStatus.OK, 2, "diag-test")
                val report = env.service.generate()
                assertEquals(1, report.syncLogCount)
                assertTrue(report.lastSyncAt != null)
            }
        }
    }
}
