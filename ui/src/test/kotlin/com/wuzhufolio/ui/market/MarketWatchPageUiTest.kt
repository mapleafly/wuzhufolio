package com.wuzhufolio.ui.market

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.market.MarketKeyStatus
import com.wuzhufolio.domain.market.MarketQuotesService
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.MarketRefreshResult
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.MarketSettingsService
import com.wuzhufolio.domain.market.MarketWatchService
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.domain.market.WatchQuoteRow
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * D21 行情页 UI 走查：默认种子行渲染、无行情 "--"、搜索添加（自选持久化服务联动）、移除。
 */
@OptIn(ExperimentalTestApi::class)
class MarketWatchPageUiTest {

    private class FakeWatch : MarketWatchService {
        var stored: List<String>? = null // null = 未写入（默认种子）
        var searchHits: List<CatalogCoin> = emptyList()

        override suspend fun watchCoins(): List<CatalogCoin> {
            val ids = stored ?: listOf("tether", "usd-coin", "dai", "true-usd")
            return ids.mapIndexed { index, cg ->
                catalogCoin(cg, symbolOf(cg), cg.capitalizeName())
            }
        }

        override suspend fun hasCustomList(): Boolean = stored != null
        override suspend fun addCoin(cgId: String) {
            val base = stored ?: listOf("tether", "usd-coin", "dai", "true-usd")
            stored = (base + cgId).distinct()
        }
        override suspend fun removeCoin(cgId: String) {
            val base = stored ?: listOf("tether", "usd-coin", "dai", "true-usd")
            stored = base.filterNot { it == cgId }
        }
        override suspend fun searchCandidates(query: String, limit: Int): List<CatalogCoin> = searchHits
    }

    private class FakeQuotes : MarketQuotesService {
        var priced: Set<String> = setOf("tether")

        override suspend fun quotesFor(coins: List<CatalogCoin>, fiat: String): List<WatchQuoteRow> =
            coins.map { cg ->
                if (cg.cgId in priced) {
                    WatchQuoteRow(
                        cgId = cg.cgId, symbol = cg.symbol, name = cg.name, fiat = fiat,
                        price = "1.01".bd(), source = PriceSource.COINGECKO,
                        at = Instant.parse("2026-09-04T01:30:00Z"),
                    )
                } else {
                    WatchQuoteRow(cg.cgId, cg.symbol, cg.name, fiat, null, null, null)
                }
            }
    }

    private open class FakeRefresh : MarketRefreshService {
        open override suspend fun refresh(manual: Boolean, coins: List<String>, fiats: List<String>):
        MarketRefreshResult =
            MarketRefreshResult(
                at = Instant.now(), source = PriceSource.COINGECKO, refreshedCoins = coins.size,
                untracked = emptyList(), quotaPercentUsed = null, error = null,
                cgConfigured = true, cmcConfigured = false,
            )
        override fun lastResult(): MarketRefreshResult? = null
        override suspend fun quotaPercentUsed(): Int? = null
        override suspend fun directoryFresh(): Boolean = true
    }

    private class FakeSettings : MarketSettingsService {
        override suspend fun keyStatus(): MarketKeyStatus = MarketKeyStatus(false, false)
        override suspend fun saveCgKey(key: String): MarketKeyStatus = keyStatus()
        override suspend fun removeCgKey(): MarketKeyStatus = keyStatus()
        override suspend fun saveCmcKey(key: String): MarketKeyStatus = keyStatus()
        override suspend fun removeCmcKey(): MarketKeyStatus = keyStatus()
        override suspend fun baseFiat(): String = "USD"
        override suspend fun refreshFrequencyMinutes(): Int = 5
        override suspend fun saveRefreshFrequencyMinutes(minutes: Int) = Unit
    }

    private fun androidx.compose.ui.test.ComposeUiTest.textCount(text: String): Int =
        onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun `default seed renders four cash rows and price dash for unpriced`() = runComposeUiTest {
        val quotes = FakeQuotes().apply { priced = setOf("tether") }
        setContent { MarketWatchPage(FakeWatch(), quotes, FakeRefresh(), FakeSettings()) }
        waitUntil(timeoutMillis = 2_000) { textCount("USDT") >= 1 && textCount("USDC") >= 1 }
        // tether 有价；usd-coin 无快照 → "--"
        onNodeWithTag("watch-row-tether").assertIsDisplayed()
        onNodeWithTag("watch-row-usd-coin").assertIsDisplayed()
        assertTrue(textCount(MarketCopy.WATCH_ADDED_HINT) == 0)
        onNodeWithTag("watch-price-usd-coin").assert(hasText("--", substring = true))
    }

    @Test
    fun `search candidate can be added into the watch list`() = runComposeUiTest {
        val watch = FakeWatch().apply {
            searchHits = listOf(catalogCoin("bitcoin", "BTC", "Bitcoin"))
        }
        setContent { MarketWatchPage(watch, FakeQuotes(), FakeRefresh(), FakeSettings()) }
        waitUntil(timeoutMillis = 2_000) { textCount("USDT") >= 1 }
        onNodeWithTag("watch-search-input").performTextInput("btc")
        waitUntil(timeoutMillis = 2_000) { onAllNodesWithText("BTC · Bitcoin").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("watch-add-bitcoin").performClick()
        waitUntil(timeoutMillis = 2_000) { watch.stored?.contains("bitcoin") == true }
        onNodeWithTag("watch-row-bitcoin").assertIsDisplayed()
    }

    @Test
    fun `remove action drops row and persists`() = runComposeUiTest {
        val watch = FakeWatch()
        setContent { MarketWatchPage(watch, FakeQuotes(), FakeRefresh(), FakeSettings()) }
        waitUntil(timeoutMillis = 2_000) { textCount("USDT") >= 1 }
        onNodeWithTag("watch-remove-tether").performClick()
        waitUntil(timeoutMillis = 2_000) { watch.stored?.contains("tether") == false }
        waitUntil(timeoutMillis = 2_000) { textCount("USDT") == 0 }
        assertTrue(watch.stored != null, "移除即写入自定义列表")
    }

    @Test
    fun `manual refresh triggers refresh service with visible coins`() = runComposeUiTest {
        var refreshedCoins: List<String>? = null
        val refresh = object : FakeRefresh() {
            override suspend fun refresh(manual: Boolean, coins: List<String>, fiats: List<String>):
            MarketRefreshResult {
                refreshedCoins = coins
                return super.refresh(manual, coins, fiats)
            }
        }
        setContent { MarketWatchPage(FakeWatch(), FakeQuotes(), refresh, FakeSettings()) }
        waitUntil(timeoutMillis = 2_000) { textCount("USDT") >= 1 }
        onNodeWithTag("watch-refresh-now").performClick()
        waitUntil(timeoutMillis = 3_000) { refreshedCoins != null }
        assertTrue(refreshedCoins!!.contains("tether"))
    }

}

private fun String.bd(): BigDecimal = BigDecimal(this)

private fun catalogCoin(cgId: String, symbol: String, name: String) = CatalogCoin(
    id = 1, cgId = cgId, cmcId = null, symbol = symbol, name = name,
    status = com.wuzhufolio.domain.catalog.CoinStatus.ACTIVE,
)

private fun symbolOf(cgId: String): String = when (cgId) {
    "tether" -> "USDT"
    "usd-coin" -> "USDC"
    "dai" -> "DAI"
    "true-usd" -> "TUSD"
    "bitcoin" -> "BTC"
    "ethereum" -> "ETH"
    else -> cgId.substringBefore("-").uppercase().take(5)
}

private fun String.capitalizeName(): String = replaceFirstChar { it.uppercase() }
