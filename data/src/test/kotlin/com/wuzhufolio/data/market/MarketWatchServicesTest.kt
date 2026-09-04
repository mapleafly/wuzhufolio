package com.wuzhufolio.data.market

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
    fun `default seed is the stablecoin whitelist when no custom list written`() = runBlocking {
        MarketTestEnv().use { env ->
            val service = SettingsMarketWatchService(env.settings, env.catalog)
            assertFalse(service.hasCustomList())
            val coins = service.watchCoins()
            assertEquals(listOf("tether", "usd-coin", "dai", "true-usd"), coins.map { it.cgId })
        }
    }

    @Test
    fun `add and remove persist across service instances and switching semantics`() = runBlocking {
        MarketTestEnv().use { env ->
            val service = SettingsMarketWatchService(env.settings, env.catalog)
            service.addCoin("bitcoin")
            service.addCoin("ethereum")
            service.removeCoin("tether") // 从默认种子移除（写入自定义列表）
            assertTrue(service.hasCustomList())

            val reloaded = SettingsMarketWatchService(env.settings, env.catalog)
            assertEquals(listOf("usd-coin", "dai", "true-usd", "bitcoin", "ethereum"), reloaded.watchCoins().map {
            it.cgId })
        }
    }

    @Test
    fun `add duplicate is idempotent and limit is enforced`() = runBlocking {
        MarketTestEnv().use { env ->
            val service = SettingsMarketWatchService(env.settings, env.catalog)
            service.addCoin("bitcoin")
            service.addCoin("bitcoin")
            assertEquals(5, service.watchCoins().size, "默认 4 + 1 不重复")
            repeat(45) { i -> service.addCoin("extra-coin-$i") }
            assertEquals(MarketWatchService.WATCH_LIMIT, service.watchCoins().size)
            assertFailsWith<IllegalArgumentException> { service.addCoin("overflow-coin") }
        }
    }

    @Test
    fun `coins missing in catalog are pruned on read without altering storage`() = runBlocking {
        MarketTestEnv().use { env ->
            val service = SettingsMarketWatchService(env.settings, env.catalog)
            service.addCoin("bitcoin")
            service.addCoin("ghost-coin-not-in-catalog")
            val visible = service.watchCoins()
            assertEquals(listOf("tether", "usd-coin", "dai", "true-usd", "bitcoin"), visible.map { it.cgId })
            // 存储保留 ghost（目录恢复后可重新出现）
            val raw = assertNotNull(env.settings.getGlobal(MarketWatchService.SETTINGS_KEY))
            assertTrue(raw.contains("ghost-coin-not-in-catalog"))
        }
    }

    @Test
    fun `corrupt watch payload falls back to default seed`() = runBlocking {
        MarketTestEnv().use { env ->
            env.settings.putGlobal(MarketWatchService.SETTINGS_KEY, "{not-json")
            val service = SettingsMarketWatchService(env.settings, env.catalog)
            assertEquals(4, service.watchCoins().size)
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
}
