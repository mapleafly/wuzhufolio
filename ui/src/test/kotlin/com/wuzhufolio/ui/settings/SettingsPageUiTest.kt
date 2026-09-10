package com.wuzhufolio.ui.settings

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.backup.BackupService
import com.wuzhufolio.domain.backup.CsvExportKind
import com.wuzhufolio.domain.backup.RestoreMode
import com.wuzhufolio.domain.exchange.ApiKeyInfo
import com.wuzhufolio.domain.exchange.ApiKeyInput
import com.wuzhufolio.domain.exchange.ApiKeySyncResult
import com.wuzhufolio.domain.exchange.CredentialValidation
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.exchange.SyncLogRow
import com.wuzhufolio.domain.ledger.FeeRuleRow
import com.wuzhufolio.domain.ledger.FeeRuleService
import com.wuzhufolio.domain.market.MarketKeyStatus
import com.wuzhufolio.domain.market.MarketRefreshResult
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.MarketSettingsService
import com.wuzhufolio.domain.market.QuotaCallKind
import com.wuzhufolio.domain.settings.DiagnosticsReport
import com.wuzhufolio.domain.settings.DiagnosticsService
import com.wuzhufolio.domain.settings.GeneralSettingsService
import com.wuzhufolio.domain.settings.GeneralSettingsView
import com.wuzhufolio.domain.settings.LogAccess
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.PrecisionPreset
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.shell.ShellViewModel
import com.wuzhufolio.ui.theme.WuzhuTheme
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 完整设置页 UI 走查（M10 T10.4 · Compose 离屏测试）：
 * 分组齐全 / 开关与下拉写入持久化 / 白名单增删 / 日志弹窗与导出确认（PRD「导出前提示」）/
 * 诊断报告生成 / 主题段与 ShellViewModel 双向同步 / API 同步间隔归位「行情与同步」。
 */
@OptIn(ExperimentalTestApi::class)
class SettingsPageUiTest {

    private class FakeGeneralSettings : GeneralSettingsService {
        var view = GeneralSettingsView()
        var savedFiat: String? = null
        var savedEnum: Boolean? = null
        var addedCoin: String? = null
        var removedCoin: String? = null
        var savedProxy: Boolean? = null
        var savedThreshold: BigDecimal? = null

        override suspend fun view(): GeneralSettingsView = view
        override suspend fun setBaseFiat(code: String) {
            savedFiat = code
            view = view.copy(baseFiat = code.uppercase())
        }
        override suspend fun setPrecision(preset: PrecisionPreset) {
            view = view.copy(precision = preset)
        }
        override suspend fun setUsernameEnum(on: Boolean) {
            savedEnum = on
            view = view.copy(usernameEnumOn = on)
        }
        override suspend fun addCashCoin(cgId: String) {
            addedCoin = cgId.trim().lowercase()
            view = view.copy(cashCoinExtra = view.cashCoinExtra + addedCoin!!)
        }
        override suspend fun removeCashCoin(cgId: String) {
            removedCoin = cgId
            view = view.copy(cashCoinExtra = view.cashCoinExtra - cgId)
        }
        override suspend fun setSmallAmountThreshold(threshold: BigDecimal) {
            savedThreshold = threshold
            view = view.copy(smallThreshold = threshold)
        }
        override suspend fun setProxyEnabled(on: Boolean) {
            savedProxy = on
            view = view.copy(proxyEnabled = on)
        }
    }

    private class FakeMarketSettings : MarketSettingsService {
        override suspend fun baseFiat(): String = "USD"
        override suspend fun keyStatus(): MarketKeyStatus = MarketKeyStatus(false, false)
        override suspend fun saveCgKey(key: String) = keyStatus()
        override suspend fun removeCgKey() = keyStatus()
        override suspend fun saveCmcKey(key: String) = keyStatus()
        override suspend fun removeCmcKey() = keyStatus()
        override suspend fun refreshFrequencyMinutes(): Int = 5
        override suspend fun saveRefreshFrequencyMinutes(minutes: Int) = Unit
    }

    private class FakeMarketRefresh : MarketRefreshService {
        override suspend fun refresh(manual: Boolean, coins: List<String>, fiats: List<String>) =
            MarketRefreshResult(null, null, 0, emptyList(), null, null, false, false)
        override fun lastResult(): MarketRefreshResult? = null
        override suspend fun quotaPercentUsed(): Int? = null
        override suspend fun directoryFresh(): Boolean = true
    }

    private class FakeSync : ExchangeSyncService {
        var interval = 30
        override suspend fun listKeys() = emptyList<ApiKeyInfo>()
        override suspend fun addAndSync(input: ApiKeyInput) = throw UnsupportedOperationException()
        override suspend fun removeKey(apiKeyId: Long) = Unit
        override suspend fun updateKey(apiKeyId: Long, input: ApiKeyInput) = Unit
        override suspend fun testCredentials(input: ApiKeyInput) = CredentialValidation.Ok
        override suspend fun syncNow(apiKeyId: Long?) = emptyList<ApiKeySyncResult>()
        override suspend fun recentSyncLogs(limit: Int) = emptyList<SyncLogRow>()
        override suspend fun syncIntervalMinutes(): Int = interval
        override suspend fun saveSyncIntervalMinutes(minutes: Int) {
            interval = minutes
        }
    }

    private class FakeFeeRules : FeeRuleService {
        override suspend fun listRules() = emptyList<FeeRuleRow>()
        override suspend fun saveGlobal(buyPercent: BigDecimal, sellPercent: BigDecimal) = Unit
        override suspend fun saveExchange(exchange: String, buyPercent: BigDecimal, sellPercent: BigDecimal) = Unit
        override suspend fun saveExchangeEdit(
            id: Long,
            exchange: String,
            buyPercent: BigDecimal,
            sellPercent: BigDecimal,
        ) = Unit
        override suspend fun removeRule(id: Long) = Unit
    }

    private class FakeDiagnostics : DiagnosticsService {
        override suspend fun generate() = DiagnosticsReport(
            appVersion = "0.1.0-test",
            osName = "Linux",
            osVersion = "6.6",
            osArch = "amd64",
            schemaVersion = 12,
            marketCalls = mapOf(QuotaCallKind.CURRENT to 1),
            syncLogCount = 0,
            lastSyncAt = null,
            logTail = listOf("2026-09-10 08:00:00.000 INFO [main] w - bootstrap ok"),
            generatedAt = Instant.parse("2026-09-10T09:00:00Z"),
        )
    }

    private class FakeLogAccess : LogAccess {
        val lines = listOf("2026-09-10 08:00:00.000 INFO [main] w - bootstrap ok | schema=12")
        var exportedTo: String? = null
        override fun path(): String? = "/tmp/wuzhufolio.log"
        override fun tailLines(max: Int): List<String> = lines.take(max)
        override fun exportTo(targetPath: String): Int {
            exportedTo = targetPath
            return lines.size
        }
    }

    private class FakeBackup : BackupService {
        override suspend fun backupMetadata() =
            com.wuzhufolio.domain.backup.BackupMetadata(lastBackupAt = null, lastRestoreAt = null)
        override suspend fun exportBackup(path: java.nio.file.Path, password: CharArray) =
            throw UnsupportedOperationException()
        override suspend fun previewBackup(path: java.nio.file.Path) = throw UnsupportedOperationException()
        override suspend fun prepareRestore(path: java.nio.file.Path, password: CharArray) =
            throw UnsupportedOperationException()
        override suspend fun restoreBackup(path: java.nio.file.Path, password: CharArray, mode: RestoreMode) =
            throw UnsupportedOperationException()
        override suspend fun exportCsv(kind: CsvExportKind, path: java.nio.file.Path) = Unit
    }

    private fun recordingPickers() = SettingsFilePickers(
        pickLogSave = { "/tmp/wuzhufolio-export.log" },
        pickReportSave = { "/tmp/wuzhufolio-report.txt" },
        writeTextFile = { _, _ -> },
    )

    private fun ComposeUiTest.textCount(text: String): Int =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    /** 长页滚动到目标分组（单页纵向滚动；离屏节点点击前必须先滚到位）。 */
    private fun ComposeUiTest.scrollToTag(tag: String) {
        onNodeWithTag("settings-scroll").performScrollToNode(hasTestTag(tag))
    }

    /** 组合根：注入全部 Fake 并返回（ShellViewModel, LogAccess, SyncService）供断言。 */
    private fun ComposeUiTest.install(
        general: FakeGeneralSettings = FakeGeneralSettings(),
        logAccess: FakeLogAccess = FakeLogAccess(),
        sync: FakeSync = FakeSync(),
    ): Triple<ShellViewModel, FakeLogAccess, FakeSync> {
        val shell = ShellViewModel(ThemeMode.LIGHT, PnlColorScheme.GREEN_UP)
        setContent {
            WuzhuTheme(themeMode = ThemeMode.LIGHT) {
                SettingsPage(
                    shellViewModel = shell,
                    generalSettings = general,
                    marketSettingsService = FakeMarketSettings(),
                    marketRefreshService = FakeMarketRefresh(),
                    syncService = sync,
                    feeRuleService = FakeFeeRules(),
                    diagnosticsService = FakeDiagnostics(),
                    logAccess = logAccess,
                    backupService = FakeBackup(),
                    appVersion = "0.1.0-test",
                    pickers = recordingPickers(),
                )
            }
        }
        return Triple(shell, logAccess, sync)
    }

    @Test
    fun `all groups render on single page`() = runComposeUiTest {
        install()
        listOf(
            "group-general", "group-network", "group-market-sync", "group-logs",
            "group-fee", "group-api", "group-data", "group-about",
        ).forEach { tag ->
            scrollToTag(tag)
            onNodeWithTag(tag).assertIsDisplayed()
        }
        scrollToTag("group-about")
        onNodeWithText("版本").assertIsDisplayed()
        onNodeWithText("无遥测声明").assertIsDisplayed()
    }

    @Test
    fun `username enum and proxy switches persist via service`() = runComposeUiTest {
        val general = FakeGeneralSettings()
        install(general = general)
        scrollToTag("username-enum-switch")
        onNodeWithTag("username-enum-switch").assertIsDisplayed()
        onNodeWithTag("username-enum-switch").performClick()
        waitUntil(timeoutMillis = 2_000) { general.savedEnum == false }
        // 滚到下一分组（而非贴边）：避免目标开关停在视口底缘导致点击注入落空
        scrollToTag("group-market-sync")
        onNodeWithTag("proxy-switch").assertIsDisplayed()
        onNodeWithTag("proxy-switch").performClick()
        waitUntil(timeoutMillis = 2_000) { general.savedProxy == false }
    }

    @Test
    fun `whitelist add and remove flow`() = runComposeUiTest {
        val general = FakeGeneralSettings()
        install(general = general)
        // 默认项展示且无移除按钮
        onNodeWithTag("cash-coin-tether").assertIsDisplayed()
        // 添加扩展项
        onNodeWithTag("cash-input").performTextInput("usdd")
        onNodeWithTag("cash-add").performClick()
        waitUntil(timeoutMillis = 2_000) { general.addedCoin == "usdd" }
        // 扩展项可移除
        onNodeWithTag("cash-remove-usdd", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { general.removedCoin == "usdd" }
    }

    @Test
    fun `small threshold free input saves custom value`() = runComposeUiTest {
        val general = FakeGeneralSettings()
        install(general = general)
        scrollToTag("group-general")
        // M10 修复轮：预设档 → 自由数值（总额小的用户可设 5，总额大的用户可设 20000）
        onNodeWithTag("threshold-input").performTextClearance() // 初始回填 "0"：先清空再输入（光标默认在头部）
        onNodeWithTag("threshold-input").performTextInput("5")
        onNodeWithTag("threshold-save").performClick()
        waitUntil(timeoutMillis = 2_000) { general.savedThreshold != null }
        assertEquals(0, BigDecimal("5").compareTo(general.savedThreshold!!))
    }

    @Test
    fun `theme segment updates shell viewmodel state`() = runComposeUiTest {
        val (shell, _, _) = install()
        onNodeWithTag("theme-seg-1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { shell.themeMode.value == ThemeMode.DARK }
    }

    @Test
    fun `view logs modal shows tail and export confirm gates export`() = runComposeUiTest {
        val (_, logAccess, _) = install()
        scrollToTag("group-logs")
        // 查看日志（已脱敏）
        onNodeWithTag("logs-view").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount("bootstrap ok") >= 1 }
        onNodeWithTag("logs-modal-close").performClick()
        // 导出：先提示检查敏感信息，确认后才触达导出（PRD §6）
        onNodeWithTag("logs-export").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(SettingsCopy.LOGS_EXPORT_CONFIRM) >= 1 }
        onNodeWithTag("logs-modal-confirm").performClick()
        waitUntil(timeoutMillis = 2_000) { logAccess.exportedTo != null }
    }

    @Test
    fun `diagnostics report generates preview`() = runComposeUiTest {
        install()
        scrollToTag("group-logs")
        onNodeWithTag("diag-generate").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount("数据库 schema 版本：12") >= 1 }
        onNodeWithText("bootstrap ok", substring = true).assertIsDisplayed()
    }

    @Test
    fun `api sync interval row lives in market sync group`() = runComposeUiTest {
        val (_, _, sync) = install()
        scrollToTag("group-market-sync")
        onNodeWithTag("api-sync-interval").assertIsDisplayed()
        onNodeWithTag("interval-select").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount("15 分钟") >= 1 }
        onNodeWithTag("interval-select-opt-0", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { sync.interval == 15 }
    }
}
