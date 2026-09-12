package com.wuzhufolio.ui.shell

import com.wuzhufolio.domain.exchange.ApiKeyInfo
import com.wuzhufolio.domain.exchange.ApiKeyInput
import com.wuzhufolio.domain.exchange.ApiKeySyncResult
import com.wuzhufolio.domain.exchange.CredentialValidation
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.exchange.SyncLogRow
import com.wuzhufolio.domain.exchange.SyncStatus
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.MarketRefreshResult
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.ui.i18n.I18n
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 状态栏数据源单测（M12 T12.1/T12.2 · M11 §6 遗留「状态栏数据源/额度/429 文案」闭环）。
 *
 * 覆盖：同步状态四态（空闲/进行中/成功 N 条/失败原因）、数据源徽章（有无/兜底）、
 * 额度 80% 提示、共享限流提示、断链标记、备份提醒，以及中英双档文案。
 */
class ShellStatusViewModelTest {

    @AfterTest
    fun restoreLanguage() {
        I18n.set(AppLanguage.ZH)
    }

    private class FakeRefresh(
        var result: MarketRefreshResult? = null,
        var quota: Int? = null,
    ) : MarketRefreshService {
        override suspend fun refresh(manual: Boolean, coins: List<String>, fiats: List<String>) =
            result ?: error("not used")

        override fun lastResult(): MarketRefreshResult? = result
        override suspend fun quotaPercentUsed(): Int? = quota
        override suspend fun directoryFresh(): Boolean = true
    }

    private class FakeSync(private val logs: List<SyncLogRow>) : ExchangeSyncService {
        override suspend fun listKeys(): List<ApiKeyInfo> = emptyList()
        override suspend fun addAndSync(input: ApiKeyInput): ApiKeySyncResult = error("not used")
        override suspend fun updateKey(apiKeyId: Long, input: ApiKeyInput) = Unit
        override suspend fun removeKey(apiKeyId: Long) = Unit
        override suspend fun testCredentials(input: ApiKeyInput): CredentialValidation = error("not used")
        override suspend fun syncNow(apiKeyId: Long?): List<ApiKeySyncResult> = emptyList()
        override suspend fun recentSyncLogs(limit: Int): List<SyncLogRow> = logs
        override suspend fun syncIntervalMinutes(): Int = 30
        override suspend fun saveSyncIntervalMinutes(minutes: Int) = Unit
    }

    private fun result(
        at: Instant? = NOW,
        source: PriceSource? = PriceSource.COINGECKO,
        error: MarketRefreshError? = null,
        quota: Int? = null,
    ) = MarketRefreshResult(
        at = at,
        source = source,
        refreshedCoins = 4,
        untracked = emptyList(),
        quotaPercentUsed = quota,
        error = error,
        cgConfigured = quota != null,
        cmcConfigured = false,
    )

    private fun log(status: SyncStatus, newTrades: Int, message: String) = SyncLogRow(
        id = 1,
        accountId = 1,
        apiKeyId = 1,
        syncTime = NOW,
        status = status,
        newTradesCount = newTrades,
        message = message,
    )

    private suspend fun statusOf(
        refresh: FakeRefresh,
        sync: FakeSync = FakeSync(emptyList()),
        backupDays: Long? = null,
    ): ShellStatus {
        val vm = ShellStatusViewModel(
            marketRefreshService = refresh,
            exchangeSyncService = sync,
            backupReminderDays = { backupDays },
            appVersion = "v1.0.0",
        )
        vm.refresh()
        // 首帧 refresh 为异步；以 loaded 落位为就绪信号（状态只存原始数据，不再有文案可等）
        repeat(100) {
            if (vm.state.value.loaded) return vm.state.value
            kotlinx.coroutines.delay(10)
        }
        return vm.state.value
    }

    @Test
    fun `idle sync without any log`() = kotlinx.coroutines.runBlocking {
        val status = statusOf(FakeRefresh())
        assertEquals("同步：空闲", status.syncText())
        assertEquals("数据源：未刷新", status.dataSourceText())
        assertFalse(status.marketOffline)
        assertNull(status.noticeText())
    }

    @Test
    fun `successful sync shows the new trade count and timestamp`() = kotlinx.coroutines.runBlocking {
        val status = statusOf(
            FakeRefresh(result(quota = 10)),
            FakeSync(listOf(log(SyncStatus.OK, 120, "binance sync ok"))),
        )
        assertTrue(status.syncText().startsWith("同步：成功（新增 120 条）"), status.syncText())
        assertTrue(status.dataSourceText().startsWith("数据源：CoinGecko"), status.dataSourceText())
        assertEquals("v1.0.0", status.version)
    }

    @Test
    fun `manual sync in flight takes precedence over the last result`() = kotlinx.coroutines.runBlocking {
        val status = statusOf(
            FakeRefresh(result(quota = 10)),
            FakeSync(listOf(log(SyncStatus.OK, 3, "ok"))),
        )
        assertEquals("同步中…", status.syncText(syncing = true))
    }

    @Test
    fun `failed sync surfaces the redacted reason`() = kotlinx.coroutines.runBlocking {
        val status = statusOf(
            FakeRefresh(),
            FakeSync(listOf(log(SyncStatus.FAILED, 0, "Binance API 密钥已失效，请检查或更新"))),
        )
        assertTrue(
            status.syncText().startsWith("同步失败：Binance API 密钥已失效，请检查或更新"),
            status.syncText(),
        )
    }

    @Test
    fun `quota at eighty percent warns and wins over the backup reminder`() = kotlinx.coroutines.runBlocking {
        val status = statusOf(FakeRefresh(result(quota = 85), quota = 85), backupDays = 45)
        assertTrue(status.noticeText().orEmpty().contains("80%"), status.noticeText().orEmpty())
        assertTrue(status.noticeIsWarn())
    }

    @Test
    fun `shared rate limit hint appears when the keyless api is throttled`() = kotlinx.coroutines.runBlocking {
        val status = statusOf(
            FakeRefresh(
                result(source = null, error = MarketRefreshError.RateLimited(PriceSource.COINGECKO, keylessHint = true)),
            ),
        )
        assertTrue(status.noticeText().orEmpty().contains("注册免费个人 Key"), status.noticeText().orEmpty())
        assertTrue(status.noticeIsWarn())
    }

    @Test
    fun `network error marks the status bar as offline`() = kotlinx.coroutines.runBlocking {
        val status = statusOf(
            FakeRefresh(result(at = null, source = null, error = MarketRefreshError.Network(PriceSource.COINGECKO))),
        )
        assertTrue(status.marketOffline)
        assertEquals("数据源：未刷新", status.dataSourceText())
    }

    @Test
    fun `fallback source is labelled in the badge`() = kotlinx.coroutines.runBlocking {
        val status = statusOf(FakeRefresh(result(source = PriceSource.COINMARKETCAP)))
        assertTrue(
            status.dataSourceText().startsWith("数据源：CoinMarketCap（兜底）"),
            status.dataSourceText(),
        )
    }

    @Test
    fun `backup reminder is shown when due`() = kotlinx.coroutines.runBlocking {
        val status = statusOf(FakeRefresh(), backupDays = 31)
        assertTrue(status.noticeText().orEmpty().contains("31"), status.noticeText().orEmpty())
        assertFalse(status.noticeIsWarn())
    }

    @Test
    fun `english catalogue is used for the status bar`() = kotlinx.coroutines.runBlocking {
        I18n.set(AppLanguage.EN)
        val status = statusOf(FakeRefresh(result(quota = 5)))
        assertEquals("Sync: idle", status.syncText())
        assertTrue(status.dataSourceText().startsWith("Source: CoinGecko"), status.dataSourceText())
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-10T07:24:00Z")
    }
}
