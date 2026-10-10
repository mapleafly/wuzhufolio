package com.wuzhufolio.ui.market

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinStatus
import com.wuzhufolio.domain.market.AccountWatchSummary
import com.wuzhufolio.domain.market.MarketKeyStatus
import com.wuzhufolio.domain.market.MarketQuotesService
import com.wuzhufolio.domain.market.MarketRefreshResult
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.MarketSettingsService
import com.wuzhufolio.domain.market.MarketWatchService
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.domain.market.WatchImportFailReason
import com.wuzhufolio.domain.market.WatchImportResult
import com.wuzhufolio.domain.market.WatchImportService
import com.wuzhufolio.domain.market.WatchQuoteRow
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 行情页「自选跨账户导入」UI 走查（B 迭代 **B3** · `D48` · T15.5）——**注入 fake 服务**（不接真库）。
 *
 * 覆盖：入口显隐三态（有其它账户 / 无其它账户 / 未注入服务）· **选择 → 二次确认**全流程且**确认前零调用** ·
 * 结果文案四态（导入成功含跳过 / 全量重复 / 来源为空 / 未登录）· **真实按键 Esc 关窗**（`AGENTS.md §7.3-7②` 口径）。
 */
@OptIn(ExperimentalTestApi::class)
class WatchImportUiTest {

    // ---- fakes（与 MarketWatchPageUiTest 同构；本文件自持以便独立演进） ----

    private class FakeWatch : MarketWatchService {
        override suspend fun watchCoins(): List<CatalogCoin> =
            listOf(coin("tether", "USDT", "Tether"))

        override suspend fun hasCustomList(): Boolean = true
        override suspend fun addCoin(cgId: String) = Unit
        override suspend fun addCoins(cgIds: Collection<String>) = Unit
        override suspend fun removeCoin(cgId: String) = Unit
        override suspend fun searchCandidates(query: String, limit: Int): List<CatalogCoin> = emptyList()
    }

    private class FakeQuotes : MarketQuotesService {
        override suspend fun quotesFor(coins: List<CatalogCoin>, fiat: String): List<WatchQuoteRow> =
            coins.map {
                WatchQuoteRow(
                    cgId = it.cgId, symbol = it.symbol, name = it.name, fiat = fiat,
                    price = BigDecimal("1.01"), source = PriceSource.COINGECKO,
                    at = Instant.parse("2026-10-10T01:30:00Z"),
                )
            }
    }

    private class FakeRefresh : MarketRefreshService {
        override suspend fun refresh(manual: Boolean, coins: List<String>, fiats: List<String>): MarketRefreshResult =
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

    /** 导入用例 fake：只读摘要可配、结果可配，并记录调用次数与来源 id。 */
    private class FakeImport(
        private val sources: List<AccountWatchSummary> = emptyList(),
        private val result: WatchImportResult = WatchImportResult.Imported(2, 1, 1, "account-a"),
    ) : WatchImportService {

        var importCalls: Int = 0
        var lastSourceId: Int? = null

        override suspend fun listOtherAccounts(): List<AccountWatchSummary> = sources

        override suspend fun importFrom(sourceAccountId: Int): WatchImportResult {
            importCalls += 1
            lastSourceId = sourceAccountId
            return result
        }
    }

    private fun summary(
        accountId: Int = 1,
        username: String = "account-a",
        cgIds: List<String> = listOf("tether", "bitcoin"),
        displayNames: List<String> = listOf("USDT", "BTC"),
        defaultSeed: Boolean = false,
    ) = AccountWatchSummary(accountId, username, cgIds, displayNames, defaultSeed)

    private fun ComposeUiTest.textCount(text: String): Int =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun ComposeUiTest.tagCount(tag: String): Int =
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    /** 打开弹层并走到二次确认（不点确认）。 */
    private fun ComposeUiTest.openImportAndPickSource() {
        waitUntil(timeoutMillis = 2_000) { tagCount("watch-import-open") >= 1 }
        onNodeWithTag("watch-import-open").performClick()
        waitUntil(timeoutMillis = 2_000) { tagCount("watch-import-source-1") >= 1 }
        onNodeWithTag("watch-import-source-1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { tagCount("watch-import-summary") >= 1 }
        onNodeWithTag("watch-import-next", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { tagCount("watch-import-confirm") >= 1 }
    }

    // ---- 入口显隐 ----

    @Test
    fun `entry is hidden when there are no other accounts`() = runComposeUiTest {
        setContent {
            MarketWatchPage(
                FakeWatch(), FakeQuotes(), FakeRefresh(), FakeSettings(),
                importService = FakeImport(sources = emptyList()),
            )
        }
        waitUntil(timeoutMillis = 2_000) { textCount("USDT") >= 1 }
        assertEquals(0, tagCount("watch-import-open"), "无其它账户 ⇒ 导入入口不渲染")
    }

    @Test
    fun `entry is hidden when the import service is not injected`() = runComposeUiTest {
        setContent { MarketWatchPage(FakeWatch(), FakeQuotes(), FakeRefresh(), FakeSettings()) }
        waitUntil(timeoutMillis = 2_000) { textCount("USDT") >= 1 }
        assertEquals(0, tagCount("watch-import-open"), "未注入服务 ⇒ 页面行为与改造前一致（无入口）")
    }

    @Test
    fun `entry is visible when other accounts exist`() = runComposeUiTest {
        setContent {
            MarketWatchPage(
                FakeWatch(), FakeQuotes(), FakeRefresh(), FakeSettings(),
                importService = FakeImport(sources = listOf(summary())),
            )
        }
        waitUntil(timeoutMillis = 2_000) { tagCount("watch-import-open") >= 1 }
        onNodeWithTag("watch-import-open").assertIsDisplayed()
    }

    // ---- 显式性：选择 → 二次确认（确认前零调用） ----

    @Test
    fun `import requires an explicit source selection and a second confirmation`() = runComposeUiTest {
        val fake = FakeImport(sources = listOf(summary()))
        setContent {
            MarketWatchPage(FakeWatch(), FakeQuotes(), FakeRefresh(), FakeSettings(), importService = fake)
        }
        waitUntil(timeoutMillis = 2_000) { tagCount("watch-import-open") >= 1 }
        onNodeWithTag("watch-import-open").performClick()
        waitUntil(timeoutMillis = 2_000) { tagCount("watch-import-modal") >= 1 }
        onNodeWithTag("watch-import-modal", useUnmergedTree = true).assertIsDisplayed()
        // 未选来源：点「下一步」不得进入确认步骤（也不得调用服务）
        onNodeWithTag("watch-import-next", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(0, tagCount("watch-import-confirm"), "未选来源时不得进入二次确认")
        assertEquals(0, fake.importCalls, "打开弹层 / 未确认前不得调用导入服务")

        onNodeWithTag("watch-import-source-1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { tagCount("watch-import-summary") >= 1 }
        onNodeWithTag("watch-import-next", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { tagCount("watch-import-confirm") >= 1 }
        // 二次确认显示来源账户名 + 将导入的数量与列表
        assertTrue(
            textCount(MarketCopy.watchImportConfirmSummary("account-a", 2)) >= 1,
            "二次确认必须显示「来源账户 + 将导入的币种数量」摘要",
        )
        assertTrue(textCount(MarketCopy.watchImportNames(listOf("USDT", "BTC"))) >= 1, "二次确认必须显示币种列表")
        assertEquals(0, fake.importCalls, "二次确认前仍不得调用导入服务")

        onNodeWithTag("watch-import-confirm-btn", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { fake.importCalls == 1 }
        assertEquals(1, fake.lastSourceId, "必须把用户选择的来源账户 id 传给服务")
        waitUntil(timeoutMillis = 2_000) { tagCount("watch-import-modal") == 0 }
    }

    @Test
    fun `escape closes the import modal without importing`() = runComposeUiTest {
        val fake = FakeImport(sources = listOf(summary()))
        setContent {
            MarketWatchPage(FakeWatch(), FakeQuotes(), FakeRefresh(), FakeSettings(), importService = fake)
        }
        waitUntil(timeoutMillis = 2_000) { tagCount("watch-import-open") >= 1 }
        onNodeWithTag("watch-import-open").performClick()
        waitUntil(timeoutMillis = 2_000) { tagCount("watch-import-modal") >= 1 }

        onRoot().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(0, tagCount("watch-import-modal"), "Esc 应关闭导入弹层（§7.3-7：遮罩不参与焦点/键盘）")
        assertEquals(0, fake.importCalls, "关闭弹层不得触发任何导入")
    }

    // ---- 结果文案（逐态） ----

    @Test
    fun `imported result reports added and skipped counts`() = runComposeUiTest {
        val fake = FakeImport(
            sources = listOf(summary()),
            result = WatchImportResult.Imported(added = 2, skipped = 1, sourceAccountId = 1, sourceUsername = "account-a"),
        )
        setContent {
            MarketWatchPage(FakeWatch(), FakeQuotes(), FakeRefresh(), FakeSettings(), importService = fake)
        }
        openImportAndPickSource()
        onNodeWithTag("watch-import-confirm-btn", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { fake.importCalls == 1 }
        waitUntil(timeoutMillis = 2_000) { textCount(MarketCopy.watchImportDoneSkipped("account-a", 2, 1)) >= 1 }
    }

    @Test
    fun `already complete result is reported as a no-op`() = runComposeUiTest {
        val fake = FakeImport(
            sources = listOf(summary()),
            result = WatchImportResult.AlreadyComplete(sourceAccountId = 1, sourceUsername = "account-a"),
        )
        setContent {
            MarketWatchPage(FakeWatch(), FakeQuotes(), FakeRefresh(), FakeSettings(), importService = fake)
        }
        openImportAndPickSource()
        onNodeWithTag("watch-import-confirm-btn", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { fake.importCalls == 1 }
        waitUntil(timeoutMillis = 2_000) { textCount(MarketCopy.watchImportAlreadyComplete("account-a")) >= 1 }
    }

    @Test
    fun `empty source result is reported`() = runComposeUiTest {
        val fake = FakeImport(
            sources = listOf(summary(cgIds = emptyList(), displayNames = emptyList())),
            result = WatchImportResult.NoSource(sourceAccountId = 1, sourceUsername = "account-a"),
        )
        setContent {
            MarketWatchPage(FakeWatch(), FakeQuotes(), FakeRefresh(), FakeSettings(), importService = fake)
        }
        openImportAndPickSource()
        onNodeWithTag("watch-import-confirm-btn", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { fake.importCalls == 1 }
        waitUntil(timeoutMillis = 2_000) { textCount(MarketCopy.watchImportNoSource("account-a")) >= 1 }
    }

    @Test
    fun `missing session result asks the user to sign in`() = runComposeUiTest {
        val fake = FakeImport(
            sources = listOf(summary()),
            result = WatchImportResult.Failed(WatchImportFailReason.NO_SESSION),
        )
        setContent {
            MarketWatchPage(FakeWatch(), FakeQuotes(), FakeRefresh(), FakeSettings(), importService = fake)
        }
        openImportAndPickSource()
        onNodeWithTag("watch-import-confirm-btn", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { fake.importCalls == 1 }
        waitUntil(timeoutMillis = 2_000) { textCount(MarketCopy.WATCH_IMPORT_LOGIN_REQUIRED) >= 1 }
    }
}

private fun coin(cgId: String, symbol: String, name: String) = CatalogCoin(
    id = 1, cgId = cgId, cmcId = null, symbol = symbol, name = name, status = CoinStatus.ACTIVE,
)
