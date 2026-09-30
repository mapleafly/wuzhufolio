package com.wuzhufolio.data.market

import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import com.wuzhufolio.domain.market.MarketWatchService
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.domain.market.WatchQuoteRow
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D21 行情页数据层验收：自选默认种子/增删/持久化/上限/目录解析清理 + 报价组装（latest 快照口径）。
 */
class MarketWatchServicesTest {

    @Test
    fun `default seed is usdt only when no custom list written`() = runBlocking {
        MarketTestEnv().use { env ->
            val service = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore())
            assertFalse(service.hasCustomList())
            val coins = service.watchCoins()
            // 2026-09-13 人工拍板：开箱种子由 4 个主流稳定币收敛为仅 USDT
            assertEquals(listOf("tether"), coins.map { it.cgId })
        }
    }

    @Test
    fun `add and remove persist across service instances and switching semantics`() = runBlocking {
        MarketTestEnv().use { env ->
            val service = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore())
            service.addCoin("bitcoin")
            service.addCoin("ethereum")
            service.addCoin("usd-coin")
            service.removeCoin("tether") // 从默认种子移除（写入自定义列表）
            assertTrue(service.hasCustomList())

            val reloaded = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore())
            assertEquals(listOf("bitcoin", "ethereum", "usd-coin"), reloaded.watchCoins().map { it.cgId })
        }
    }

    @Test
    fun `add duplicate is idempotent and there is no length limit`() = runBlocking {
        MarketTestEnv().use { env ->
            val service = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore())
            service.addCoin("bitcoin")
            service.addCoin("bitcoin")
            assertEquals(2, service.watchCoins().size, "默认 1（USDT）+ 1 不重复")
            // D35（2026-09-24 人工口径）：自选**不设数量上限**——原 50 条上限与 IllegalArgumentException 已移除
            val extra = (0 until 80).map {
                CoinDirectoryEntry("extra-coin-$it", "x$it", "Extra Coin $it")
            }
            env.seedDirectory(MarketTestEnv.DEFAULT_ENTRIES + extra)
            repeat(80) { i -> service.addCoin("extra-coin-$i") }
            assertEquals(82, service.watchCoins().size)
        }
    }

    // ---- D35：成交币批量自动加入（幂等 / 保序 / 去空）----

    @Test
    fun `batch add appends new coins in order and is idempotent`() = runBlocking {
        MarketTestEnv().use { env ->
            val service = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore())
            service.addCoins(listOf("bitcoin", "ethereum"))
            service.addCoins(listOf("ethereum", "dai", "bitcoin"))
            assertEquals(
                listOf("tether", "bitcoin", "ethereum", "dai"),
                service.watchCoins().map { it.cgId },
                "重复币不重复写入、既有顺序不变、新币追加在末尾",
            )
        }
    }

    @Test
    fun `batch add ignores blank entries and empty batches`() = runBlocking {
        MarketTestEnv().use { env ->
            val service = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore())
            service.addCoins(listOf("  ", "bitcoin", "bitcoin", ""))
            service.addCoins(emptyList())
            assertEquals(listOf("tether", "bitcoin"), service.watchCoins().map { it.cgId })
        }
    }

    @Test
    fun `removed coin is added back by the next batch (auto add wins over manual removal)`() = runBlocking {
        MarketTestEnv().use { env ->
            val service = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore())
            service.removeCoin("tether")
            assertEquals(emptyList(), service.watchCoins().map { it.cgId }, "手动移除后自选为空")
            // D35 口径①：下次同步/交易触达该币时**自动加回**（不记录「用户已移除」排除集）
            service.addCoins(listOf("tether"))
            assertEquals(listOf("tether"), service.watchCoins().map { it.cgId })
        }
    }

    @Test
    fun `coins missing in catalog are pruned on read without altering storage`() = runBlocking {
        MarketTestEnv().use { env ->
            val service = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore())
            service.addCoin("bitcoin")
            service.addCoin("ghost-coin-not-in-catalog")
            val visible = service.watchCoins()
            assertEquals(listOf("tether", "bitcoin"), visible.map { it.cgId })
            // 存储保留 ghost（目录恢复后可重新出现）。D41：自选改**账户级**（settings.account_id=1），
            // 故此处从账户行读取（原断言读全局行，属被迁移改变的位置，按新口径更新）。
            val raw = assertNotNull(env.settings.getAccount(1, MarketWatchService.SETTINGS_KEY))
            assertTrue(raw.contains("ghost-coin-not-in-catalog"))
        }
    }

    @Test
    fun `corrupt watch payload falls back to default seed`() = runBlocking {
        MarketTestEnv().use { env ->
            env.settings.putAccount(1, MarketWatchService.SETTINGS_KEY, "{not-json")
            val service = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore())
            assertEquals(1, service.watchCoins().size, "损坏载荷回退默认种子（仅 USDT）")
        }
    }

    @Test
    fun `quotes assembly maps catalog plus latest snapshot with unpriced dash`() = runBlocking {
        MarketTestEnv().use { env ->
            val btc = env.catalog.getByCgId("bitcoin")!!
            env.snapshots.upsert(
                SnapshotWrite(
                    btc.id.toInt(), "USD", "79171".bd(), PriceSource.COINGECKO,
                    Instant.parse("2026-09-04T01:30:00Z"),
                ),
            )
            val service = SnapshotMarketQuotesService(env.snapshots)
            val rows = service.quotesFor(listOf(btc, env.catalog.getByCgId("tether")!!), fiat = "USD")
            assertEquals(2, rows.size)
            val btcRow: WatchQuoteRow = rows.first { it.cgId == "bitcoin" }
            assertTrue(btcRow.priced)
            assertEquals(0, "79171".bd().compareTo(btcRow.price!!))
            assertEquals(PriceSource.COINGECKO, btcRow.source)
            val tetherRow = rows.first { it.cgId == "tether" }
            assertNull(tetherRow.price, "无快照 = 无行情")
            assertNull(tetherRow.source)
            assertNull(tetherRow.at)
            assertFalse(tetherRow.priced)
        }
    }

    private fun String.bd(): BigDecimal = BigDecimal(this)

    // ── D41（2026-09-29 人工拍板 C2）：自选清单改**账户级**（账户隔离 §1.1-5）────────────────

    @Test
    fun `watch list is account scoped and invisible across accounts`() = runBlocking {
        MarketTestEnv().use { env ->
            val accountA = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore(1))
            val accountB = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore(2))

            accountA.addCoin("bitcoin")
            assertEquals(listOf("tether", "bitcoin"), accountA.watchCoins().map { it.cgId })

            // B 账户看不到 A 的自选：仍是自己的默认种子
            assertEquals(listOf("tether"), accountB.watchCoins().map { it.cgId })
            assertFalse(accountB.hasCustomList(), "B 账户不得继承 A 的自选")

            accountB.addCoin("ethereum")
            assertEquals(listOf("tether", "ethereum"), accountB.watchCoins().map { it.cgId })
            assertEquals(listOf("tether", "bitcoin"), accountA.watchCoins().map { it.cgId }, "A 的自选不受 B 影响")
        }
    }

    @Test
    fun `watch list is empty and read only when no session`() = runBlocking {
        MarketTestEnv().use { env ->
            val anonymous = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore(null))
            assertEquals(emptyList(), anonymous.watchCoins(), "未登录不展示任何账户的自选")
            assertFalse(anonymous.hasCustomList())

            anonymous.addCoin("bitcoin")
            anonymous.removeCoin("tether")
            val loggedIn = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore(1))
            assertEquals(listOf("tether"), loggedIn.watchCoins().map { it.cgId }, "未登录时的写入不得落库")
        }
    }

    @Test
    fun `legacy global watch list is claimed once by first account`() = runBlocking {
        MarketTestEnv().use { env ->
            // 模拟升级前的老数据：全局行 + 无迁移标记
            env.settings.putGlobal(
                MarketWatchService.SETTINGS_KEY,
                """["bitcoin","ethereum"]""",
            )

            val first = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore(1))
            assertEquals(listOf("bitcoin", "ethereum"), first.watchCoins().map { it.cgId }, "首个账户认领旧全局清单")
            assertTrue(first.hasCustomList())

            // 第二个账户不再认领（标记已置位）⇒ 用默认种子
            val second = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore(2))
            assertEquals(listOf("tether"), second.watchCoins().map { it.cgId })
            assertEquals("1", env.settings.getGlobal("watch.migrated"), "迁移标记应置位")
            // 旧全局行保留只读（便于回溯），不再参与展示
            assertEquals("""["bitcoin","ethereum"]""", env.settings.getGlobal(MarketWatchService.SETTINGS_KEY))
        }
    }

    @Test
    fun `d35 auto add lands in the current account only`() = runBlocking {
        MarketTestEnv().use { env ->
            val accountA = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore(1))
            val accountB = SettingsMarketWatchService(env.settings, env.catalog, env.sessionStore(2))
            // D35：成交币自动入自选 —— 必须只落在**当前账户**
            accountA.addCoins(listOf("bitcoin", "ethereum"))
            assertEquals(listOf("tether", "bitcoin", "ethereum"), accountA.watchCoins().map { it.cgId })
            assertEquals(listOf("tether"), accountB.watchCoins().map { it.cgId }, "B 账户不得看到 A 的成交币")
        }
    }
}
