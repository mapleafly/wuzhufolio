package com.wuzhufolio.ui.market

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.market.MarketKeyStatus
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.MarketRefreshResult
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.MarketSettingsService
import com.wuzhufolio.domain.market.PriceSource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T5.5 设置页行情分组 UI 走查（Compose 离屏测试）：行/频率/Key 弹窗（空校验、保存即生效、移除）、
 * 数据源指示 + 429 提示文案可达（MarketCopy 映射）。
 */
@OptIn(ExperimentalTestApi::class)
class MarketSettingsPageUiTest {

    private class FakeSettings : MarketSettingsService {
        var status = MarketKeyStatus(cgConfigured = false, cmcConfigured = false)
        var frequency = 5
        var savedCg: String? = null
        var removedCg = false

        override suspend fun keyStatus(): MarketKeyStatus = status
        override suspend fun saveCgKey(key: String): MarketKeyStatus {
            savedCg = key
            status = status.copy(cgConfigured = true)
            return status
        }
        override suspend fun removeCgKey(): MarketKeyStatus {
            removedCg = true
            status = status.copy(cgConfigured = false)
            return status
        }
        override suspend fun saveCmcKey(key: String): MarketKeyStatus {
            status = status.copy(cmcConfigured = true)
            return status
        }
        override suspend fun removeCmcKey(): MarketKeyStatus {
            status = status.copy(cmcConfigured = false)
            return status
        }
        override suspend fun refreshFrequencyMinutes(): Int = frequency
        override suspend fun saveRefreshFrequencyMinutes(minutes: Int) {
            frequency = minutes
        }
    }

    private class FakeRefresh(
        var result: MarketRefreshResult? = null,
    ) : MarketRefreshService {
        var refreshCalls = 0

        override suspend fun refresh(manual: Boolean, coins: List<String>, fiats: List<String>): MarketRefreshResult {
            refreshCalls++
            return result ?: emptyResult()
        }

        override fun lastResult(): MarketRefreshResult? = result
        override suspend fun quotaPercentUsed(): Int? = null
        override suspend fun directoryFresh(): Boolean = true

        private fun emptyResult() = MarketRefreshResult(
            at = null, source = null, refreshedCoins = 0, untracked = emptyList(),
            quotaPercentUsed = null, error = null,
            cgConfigured = false, cmcConfigured = false,
        )
    }

    private fun keyedResult(source: PriceSource, error: MarketRefreshError? = null) = MarketRefreshResult(
        at = Instant.parse("2026-09-04T01:30:00Z"),
        source = source,
        refreshedCoins = 2,
        untracked = emptyList(),
        quotaPercentUsed = null,
        error = error,
        cgConfigured = source == PriceSource.COINGECKO,
        cmcConfigured = source == PriceSource.COINMARKETCAP,
    )

    private fun androidx.compose.ui.test.ComposeUiTest.textCount(text: String): Int =
        onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun `page renders rows with keyless defaults and frequency markers`() = runComposeUiTest {
        setContent { MarketSettingsPage(FakeSettings(), FakeRefresh()) }
        onNodeWithTag("row-cg-key").assertIsDisplayed()
        onNodeWithTag("row-cmc-key").assertIsDisplayed()
        onNodeWithTag("row-freq").assertIsDisplayed()
        assertTrue(textCount(MarketCopy.STATUS_UNCONFIGURED) >= 2, "CG/CMC 两行均未配置")
        onNodeWithTag("market-refresh-now").assertIsDisplayed()
    }

    @Test
    fun `frequency selection persists via settings service`() = runComposeUiTest {
        val settings = FakeSettings()
        setContent { MarketSettingsPage(settings, FakeRefresh()) }
        onNodeWithTag("freq-15").performClick()
        waitUntil(timeoutMillis = 2_000) { settings.frequency == 15 }
        assertTrue(textCount("● 15 分钟") >= 1, "选中态标记随状态切换")
    }

    @Test
    fun `empty key save shows validation error without calling service`() = runComposeUiTest {
        val settings = FakeSettings()
        setContent { MarketSettingsPage(settings, FakeRefresh()) }
        onNodeWithTag("row-cg-key-action").performClick()
        onNodeWithTag("market-key-modal").assertIsDisplayed()
        onNodeWithTag("market-key-save").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(MarketCopy.EMPTY_KEY_ERROR_CG) >= 1 }
        assertNull(settings.savedCg, "空 Key 不触达服务")
    }

    @Test
    fun `cg key save applies immediately and remove returns to keyless`() = runComposeUiTest {
        val settings = FakeSettings()
        setContent { MarketSettingsPage(settings, FakeRefresh()) }
        onNodeWithTag("row-cg-key-action").performClick()
        onNodeWithTag("market-key-modal").assertIsDisplayed()
        // 共性约束 7.3-②：打开即聚焦首输入框（键盘/IME 立即可用，无需先点按）
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("market-key-input").assertIsFocused() }.isSuccess
        }
        onNodeWithTag("market-key-input").performTextInput("cg-demo-key")
        onNodeWithTag("market-key-save").performClick()
        waitUntil(timeoutMillis = 2_000) { settings.status.cgConfigured }
        assertEquals("cg-demo-key", settings.savedCg)
        // 保存即生效：出现「已配置」状态
        assertTrue(textCount(MarketCopy.STATUS_CONFIGURED) >= 1)
        // 重开弹窗 → 移除 Key → 回到全未配置
        onNodeWithTag("row-cg-key-action").performClick()
        onNodeWithTag("market-key-remove").performClick()
        waitUntil(timeoutMillis = 2_000) { settings.removedCg }
        assertEquals(0, textCount(MarketCopy.STATUS_CONFIGURED), "移除后无已配置行")
    }

    @Test
    fun `manual refresh with rate limit error surfaces keyless hint toast`() = runComposeUiTest {
        val refresh = FakeRefresh(
            keyedResult(
                PriceSource.COINGECKO,
                error = MarketRefreshError.RateLimited(PriceSource.COINGECKO, keylessHint = true),
            ),
        )
        setContent { MarketSettingsPage(FakeSettings(), refresh) }
        onNodeWithTag("market-refresh-now").performClick()
        val expected = MarketCopy.errorText(MarketRefreshError.RateLimited(PriceSource.COINGECKO, keylessHint = true))
        waitUntil(timeoutMillis = 3_000) { textCount(expected) >= 1 }
    }

    @Test
    fun `cmc fallback source indicator is displayed after refresh`() = runComposeUiTest {
        val refresh = FakeRefresh(keyedResult(PriceSource.COINMARKETCAP))
        setContent { MarketSettingsPage(FakeSettings(), refresh) }
        onNodeWithTag("market-refresh-now").performClick()
        waitUntil(timeoutMillis = 3_000) { refresh.refreshCalls == 1 }
        onNodeWithTag("market-source-status").assertIsDisplayed()
        onNodeWithText(MarketCopy.SOURCE_CMC, substring = true).assertIsDisplayed()
    }

    @Test
    fun `source indicator reflects configured key before any refresh`() = runComposeUiTest {
        val settings = FakeSettings().apply { status = MarketKeyStatus(cgConfigured = true, cmcConfigured = false) }
        setContent { MarketSettingsPage(settings, FakeRefresh()) }
        // 修复轮：已配置 Key（保存即生效）但尚无刷新 → 专属额度 + （尚无刷新）
        waitUntil(timeoutMillis = 2_000) {
            textCount(MarketCopy.SOURCE_CG_KEYED + "（尚无刷新）") >= 1
        }
    }
}
