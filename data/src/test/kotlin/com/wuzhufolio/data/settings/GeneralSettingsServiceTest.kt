package com.wuzhufolio.data.settings

import com.wuzhufolio.data.accounts.AccountRepository
import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.exchange.SyncLogRepository
import com.wuzhufolio.data.exchange.SyncLogsTable
import com.wuzhufolio.data.randomDbKey
import com.wuzhufolio.data.ledger.FeeRuleRepository
import com.wuzhufolio.data.ledger.LedgerTestEnv
import com.wuzhufolio.domain.exchange.SyncStatus
import com.wuzhufolio.domain.settings.PrecisionPreset
import java.math.BigDecimal
import java.nio.file.Files
import java.time.Instant
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 通用设置用例（M10 T10.1）：持久化 / 白名单扩展与默认保护 / 损坏自愈。 */
class GeneralSettingsServiceTest {

    private class Env : AutoCloseable {
        val db: WzDatabase = WzDatabase(
            Files.createTempDirectory("wuzhufolio-settings").resolve("settings.db"),
            randomDbKey(),
        )
        val gate: DbGate = DbGate(db)
        val settings = SettingsRepository(gate)
        val service = DefaultGeneralSettingsService(settings)

        init {
            db.migrateToLatest()
        }

        override fun close() {
            db.close()
        }
    }

    @Test
    fun `defaults are returned before any write`() {
        Env().use { env ->
            runBlockingTest {
                val view = env.service.view()
                assertEquals("USD", view.baseFiat)
                assertEquals(PrecisionPreset.DEFAULT, view.precision)
                assertTrue(view.usernameEnumOn)
                assertTrue(view.proxyEnabled)
                assertEquals(0, BigDecimal.ZERO.compareTo(view.smallThreshold), "默认 0 = 不启用")
                assertEquals(setOf("tether", "usd-coin", "dai", "true-usd"), view.cashCoinIds)
            }
        }
    }

    @Test
    fun `writes persist and round-trip`() {
        Env().use { env ->
            runBlockingTest {
                env.service.setBaseFiat("eur")
                env.service.setPrecision(PrecisionPreset.SIMPLIFIED)
                env.service.setUsernameEnum(false)
                env.service.setSmallAmountThreshold(BigDecimal("20000"))
                env.service.setProxyEnabled(false)
                val view = env.service.view()
                assertEquals("EUR", view.baseFiat)
                assertEquals(PrecisionPreset.SIMPLIFIED, view.precision)
                assertEquals(false, view.usernameEnumOn)
                assertEquals(0, BigDecimal("20000").compareTo(view.smallThreshold), "自由数值（总额 200 万用户示例）")
                assertEquals(false, view.proxyEnabled)
            }
        }
    }

    @Test
    fun `base fiat restricted to candidates`() {
        Env().use { env ->
            runBlockingTest {
                assertFailsWith<IllegalArgumentException> { env.service.setBaseFiat("JPY") }
            }
        }
    }

    @Test
    fun `negative small threshold rejected`() {
        Env().use { env ->
            runBlockingTest {
                assertFailsWith<IllegalArgumentException> { env.service.setSmallAmountThreshold(BigDecimal("-1")) }
                // 0 合法（= 不启用）
                env.service.setSmallAmountThreshold(BigDecimal.ZERO)
                assertEquals(0, BigDecimal.ZERO.compareTo(env.service.view().smallThreshold))
            }
        }
    }

    @Test
    fun `cash whitelist extend remove and default protection`() {
        Env().use { env ->
            runBlockingTest {
                env.service.addCashCoin("First-Digital-USD")
                assertTrue("first-digital-usd" in env.service.view().cashCoinIds)
                // 幂等：重复添加不产生重复项
                env.service.addCashCoin("first-digital-usd")
                assertEquals(1, env.service.view().cashCoinExtra.size)
                // 默认项不可移除；扩展项可移除
                assertFailsWith<IllegalArgumentException> { env.service.removeCashCoin("tether") }
                env.service.removeCashCoin("first-digital-usd")
                assertTrue("first-digital-usd" !in env.service.view().cashCoinIds)
                // 默认集合不受移除影响
                assertEquals(setOf("tether", "usd-coin", "dai", "true-usd"), env.service.view().cashCoinIds)
            }
        }
    }

    @Test
    fun `corrupted whitelist payload heals to empty extension`() {
        Env().use { env ->
            runBlockingTest {
                env.settings.putGlobal(GeneralSettingsKeys.CASH_COINS, "{not-json")
                assertTrue(env.service.view().cashCoinExtra.isEmpty())
                // 自愈后可正常扩展
                env.service.addCashCoin("usdd")
                assertTrue("usdd" in env.service.view().cashCoinExtra)
            }
        }
    }

    private fun runBlockingTest(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }
}

/** 费率规则完整 CRUD（M10 T10.1 扩展：编辑既有规则 + 键迁移）。 */
class FeeRuleEditTest {

    @Test
    fun `edit same exchange overwrites in place`() = kotlinx.coroutines.runBlocking {
        LedgerTestEnv().use { env ->
            val session = login(env)
            val repo = FeeRuleRepository(env.gate)
            val service = com.wuzhufolio.data.ledger.DefaultFeeRuleService(env.sessions, repo)
            val buy = java.math.BigDecimal("0.1")
            repo.upsert(session.account.id, "BINANCE", buy, buy)
            val row = repo.listEntries(session.account.id).first { it.exchange == "BINANCE" }
            val newBuy = java.math.BigDecimal("0.2")
            service.saveExchangeEdit(row.id.toLong(), "BINANCE", newBuy, newBuy)
            val entries = repo.listEntries(session.account.id)
            assertEquals(1, entries.size, "同键覆盖不新增行")
            assertEquals(java.math.BigDecimal("0.2"), entries.single().buyRatePercent)
        }
    }

    @Test
    fun `rename migrates dedup key account plus exchange`() = kotlinx.coroutines.runBlocking {
        LedgerTestEnv().use { env ->
            val session = login(env)
            val repo = FeeRuleRepository(env.gate)
            val service = com.wuzhufolio.data.ledger.DefaultFeeRuleService(env.sessions, repo)
            val buy = java.math.BigDecimal("0.1")
            val rowId = repo.upsert(session.account.id, "BINANCE", buy, buy)
            service.saveExchangeEdit(rowId.toLong(), "okx", java.math.BigDecimal("0.08"), java.math.BigDecimal("0.08"))
            val entries = repo.listEntries(session.account.id)
            assertEquals(1, entries.size, "改名 = 旧键删除 + 新键落库（备份去重键联动）")
            assertEquals("OKX", entries.single().exchange)
        }
    }

    @Test
    fun `editing global row via exchange edit is rejected`() = kotlinx.coroutines.runBlocking {
        LedgerTestEnv().use { env ->
            val session = login(env)
            val repo = FeeRuleRepository(env.gate)
            val service = com.wuzhufolio.data.ledger.DefaultFeeRuleService(env.sessions, repo)
            val buy = java.math.BigDecimal("0.1")
            val rowId = repo.upsert(session.account.id, "", buy, buy)
            val newBuy = java.math.BigDecimal("0.2")
            assertFailsWith<IllegalArgumentException> {
                service.saveExchangeEdit(rowId.toLong(), "BINANCE", newBuy, newBuy)
            }
        }
    }

    @Test
    fun `missing id is rejected`() = kotlinx.coroutines.runBlocking {
        LedgerTestEnv().use { env ->
            login(env)
            val service = com.wuzhufolio.data.ledger.DefaultFeeRuleService(env.sessions, FeeRuleRepository(env.gate))
            assertFailsWith<IllegalArgumentException> {
                service.saveExchangeEdit(999L, "BINANCE", java.math.BigDecimal("0.2"), java.math.BigDecimal("0.2"))
            }
        }
    }

    private fun login(env: LedgerTestEnv) = env.login()
}

/** sync_logs 轮转（M10 T10.2：1 万条或 90 天，先到为准）。 */
class SyncLogRotationTest {

    private class Env : AutoCloseable {
        val db: WzDatabase = WzDatabase(
            Files.createTempDirectory("wuzhufolio-syncrot").resolve("sync.db"),
            randomDbKey(),
        )
        val gate: DbGate = DbGate(db)
        val repo = SyncLogRepository(gate)
        val accountId: Int

        init {
            db.migrateToLatest()
            val accounts = AccountRepository(gate)
            accountId = kotlinx.coroutines.runBlocking {
                accounts.createWrapped("rot-user", "hash", "00".repeat(16), "p") { id -> "wrapped-" + id }.id
            }
        }

        /** 直插一条指定时刻的同步日志（append 固定 now，轮转时间口径需可控时刻）。
         *  注意：insert lambda 的 receiver = 表对象，类属性同名 `accountId` 会被解析为列
         *  （Exposed v1 遮蔽陷阱，与仓库方法参数语义不同）——故取局部别名 valueAccountId。 */
        fun insertAt(at: Instant) {
            val valueAccountId = accountId
            gate.writeBlocking {
                SyncLogsTable.insert {
                    it[SyncLogsTable.accountId] = valueAccountId
                    it[SyncLogsTable.apiKeyId] = null
                    it[SyncLogsTable.syncTime] = at.toString()
                    it[SyncLogsTable.status] = SyncStatus.OK.storageValue
                    it[SyncLogsTable.newTradesCount] = 0
                    it[SyncLogsTable.message] = "rotate-test"
                }
            }
        }

        override fun close() {
            db.close()
        }
    }

    @Test
    fun `rotate trims beyond max rows keeping newest`() {
        Env().use { env ->
            kotlinx.coroutines.runBlocking {
                val now = Instant.parse("2026-09-10T00:00:00Z")
                repeat(30) { env.insertAt(now.plusSeconds(it.toLong())) }
                val (byAge, byCount) = env.repo.rotate(now, maxRows = 10, maxAgeDays = 90)
                assertEquals(0, byAge)
                assertEquals(20, byCount, "超出 newest 10 条的旧行删除")
                assertEquals(10, env.repo.countAll())
            }
        }
    }

    @Test
    fun `rotate deletes rows older than 90 days`() {
        Env().use { env ->
            kotlinx.coroutines.runBlocking {
                val now = Instant.parse("2026-09-10T00:00:00Z")
                env.insertAt(now.minusSeconds(91 * 24 * 3600)) // 过期
                env.insertAt(now.minusSeconds(89 * 24 * 3600)) // 保留
                val (byAge, byCount) = env.repo.rotate(now, maxRows = 10_000, maxAgeDays = 90)
                assertEquals(1, byAge)
                assertEquals(0, byCount)
                assertEquals(1, env.repo.countAll())
            }
        }
    }

    @Test
    fun `countAll and lastSyncAt serve diagnostics`() {
        Env().use { env ->
            kotlinx.coroutines.runBlocking {
                env.insertAt(Instant.parse("2026-09-10T08:00:00Z"))
                assertEquals(1, env.repo.countAll())
                assertEquals(Instant.parse("2026-09-10T08:00:00Z"), env.repo.lastSyncAt())
            }
        }
    }
}
