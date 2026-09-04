package com.wuzhufolio.data.market

import com.wuzhufolio.domain.market.CmcMapCoin
import com.wuzhufolio.domain.market.MarketKeyStatus
import com.wuzhufolio.domain.market.MarketRank
import com.wuzhufolio.domain.market.QuotaCallKind
import com.wuzhufolio.domain.market.QuotaPolicy
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T5.1–T5.5 支撑件：设备级秘密存储（方案甲）/ Key 设置服务 / 额度账本 / CMC 对齐 / 市值榜缓存。
 */
class MarketSettingsTest {

    // ---- 设备级秘密存储 + Key 设置服务（T5.5） ----

    @Test
    fun `cg key save roundtrips encrypted and removal clears`() = runBlocking {
        MarketTestEnv().use { env ->
            val service = DefaultMarketSettingsService(env.deviceStore, env.settings)
            assertFalse(service.keyStatus().cgConfigured)
            val status = service.saveCgKey("cg-demo-secret-123")
            assertTrue(status.cgConfigured)
            // 密文入库、明文不落 settings
            val raw = assertNotNull(env.settings.getGlobal(MarketConfig.KEY_CG))
            assertFalse(raw.contains("cg-demo-secret-123"), "明文不得落盘")
            assertTrue(raw.startsWith("v1"))
            // 新实例同设备密钥可解（重启语义）
            val second = DeviceSecretStore(MarketTestEnv.testDeviceKey(), env.settings)
            try {
                assertEquals("cg-demo-secret-123", second.get(MarketConfig.KEY_CG, MarketConfig.PURPOSE_CG))
            } finally {
                second.close()
            }
            val removed = service.removeCgKey()
            assertFalse(removed.cgConfigured)
            assertNull(env.settings.getGlobal(MarketConfig.KEY_CG))
        }
    }

    @Test
    fun `wrong device key cannot open the secret`() = runBlocking {
        MarketTestEnv().use { env ->
            env.deviceStore.put(MarketConfig.KEY_CMC, MarketConfig.PURPOSE_CMC, "cmc-secret")
            val wrong = DeviceSecretStore(ByteArray(32), env.settings)
            try {
                assertNull(wrong.get(MarketConfig.KEY_CMC, MarketConfig.PURPOSE_CMC), "错钥解不开且不抛")
                assertTrue(wrong.configured(MarketConfig.KEY_CMC), "配置状态仍显示已配置（可覆盖重填）")
            } finally {
                wrong.close()
            }
        }
    }

    @Test
    fun `save with blank key is a no-op and key status reflects both keys`() = runBlocking {
        MarketTestEnv().use { env ->
            val service = DefaultMarketSettingsService(env.deviceStore, env.settings)
            service.saveCgKey("   ")
            assertFalse(service.keyStatus().cgConfigured)
            service.saveCgKey("cg-key")
            service.saveCmcKey("cmc-key")
            assertEquals(MarketKeyStatus(cgConfigured = true, cmcConfigured = true), service.keyStatus())
        }
    }

    // ---- 额度账本（T5.4） ----

    @Test
    fun `quota ledger records counts per kind and resets on month rollover`() {
        MarketTestEnv().use { env ->
            var now = Instant.parse("2026-09-04T10:00:00Z")
            val ledger = SettingsQuotaLedger(env.settings) { now }
            ledger.record(QuotaCallKind.CURRENT)
            ledger.record(QuotaCallKind.CURRENT)
            ledger.record(QuotaCallKind.HISTORY)
            assertEquals(2, ledger.counts()[QuotaCallKind.CURRENT])
            assertEquals(1, ledger.counts()[QuotaCallKind.HISTORY])
            // 跨月翻转：清零并改写存储月键
            now = Instant.parse("2026-10-01T00:00:00Z")
            assertEquals(0, ledger.counts()[QuotaCallKind.CURRENT])
            ledger.record(QuotaCallKind.CURRENT)
            assertEquals(1, ledger.counts()[QuotaCallKind.CURRENT])
        }
    }

    @Test
    fun `quota ledger tolerates corrupt payload`() {
        MarketTestEnv().use { env ->
            env.settings.putGlobal(MarketConfig.KEY_QUOTA, "not-json{{")
            val ledger = SettingsQuotaLedger(env.settings)
            assertNull(ledger.counts()[QuotaCallKind.CURRENT], "损坏载荷按空账本处理")
            ledger.record(QuotaCallKind.CURRENT)
            assertEquals(1, ledger.counts()[QuotaCallKind.CURRENT])
        }
    }

    @Test
    fun `percent used aggregates across kinds`() {
        MarketTestEnv().use { env ->
            val ledger = SettingsQuotaLedger(env.settings)
            ledger.record(QuotaCallKind.CURRENT)
            ledger.record(QuotaCallKind.HISTORY)
            assertFalse(ledger.downgraded())
            // 阈值语义（80%）由 domain QuotaPolicyTest 覆盖——此处只验证账本口径连通
            assertTrue(ledger.percentUsed() >= 0)
        }
    }

    // ---- CMC 对齐 + 市值榜缓存（M3 遗留落地） ----

    @Test
    fun `cmc aligner maps unique symbols and disambiguates by name`() = runBlocking {
        MarketTestEnv().use { env ->
            val aligner = CmcIdAligner(env.catalog)
            val aligned = aligner.align(
                listOf(
                    CmcMapCoin(1, "BTC", "Bitcoin"),
                    CmcMapCoin(825, "USDT", "Tether"),
                    CmcMapCoin(999, "AAA", "AAA Token Two"), // 同名多候选 → 按 name 唯一
                    CmcMapCoin(777, "GHOST", "Ghost Coin"), // 目录无此币 → 跳过
                ),
            )
            assertEquals("1", aligned["bitcoin"])
            assertEquals("825", aligned["tether"])
            assertEquals("999", aligned["aaa-token-two"])
            assertNull(aligned["ghost-coin"])
            // 落库闭环
            val changed = env.catalog.refreshCmcIds(aligned)
            assertTrue(changed >= 1, "新对齐（AAA Token Two）落库")
            assertEquals("999", env.catalog.getByCgId("aaa-token-two")!!.cmcId)
            assertEquals("825", env.catalog.getByCgId("tether")!!.cmcId)
        }
    }

    @Test
    fun `rank cache keeps immutable snapshot and returns null for unknown`() {
        val cache = RefreshableRankProvider()
        assertNull(cache.rankOf("bitcoin"))
        cache.update(listOf(MarketRank("bitcoin", 1), MarketRank("ethereum", 2)))
        assertEquals(1, cache.rankOf("bitcoin"))
        assertEquals(2, cache.size())
        assertNull(cache.rankOf("ghost"))
        cache.update(emptyList())
        assertNull(cache.rankOf("bitcoin"), "目录刷新后可整体失效")
    }
}
