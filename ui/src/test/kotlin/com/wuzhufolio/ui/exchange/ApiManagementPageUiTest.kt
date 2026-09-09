package com.wuzhufolio.ui.exchange

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.exchange.ApiKeyInfo
import com.wuzhufolio.domain.exchange.ApiKeyInput
import com.wuzhufolio.domain.exchange.ApiKeySyncResult
import com.wuzhufolio.domain.exchange.CredentialValidation
import com.wuzhufolio.domain.exchange.CredentialValidationFailed
import com.wuzhufolio.domain.exchange.ExchangeError
import com.wuzhufolio.domain.exchange.ExchangeCadence
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.exchange.SyncLogRow
import com.wuzhufolio.domain.exchange.SyncStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T6.4 API 管理页 UI 走查（Compose 离屏测试）：空态/列表/同步间隔/添加弹窗
 * （GUI 共性约束 7.3：首输入框聚焦 + performTextInput 键盘路径）/校验文案/保存即首次同步 toast/移除。
 */
@OptIn(ExperimentalTestApi::class)
class ApiManagementPageUiTest {

    private class FakeSyncService : ExchangeSyncService {
        var keys: MutableList<ApiKeyInfo> = mutableListOf()
        var logs: MutableList<SyncLogRow> = mutableListOf()
        var interval = ExchangeCadence.DEFAULT_MINUTES
        var savedInput: ApiKeyInput? = null
        var testResult: CredentialValidation = CredentialValidation.Ok
        var removedId: Long? = null

        override suspend fun listKeys(): List<ApiKeyInfo> = keys.toList()

        override suspend fun addAndSync(input: ApiKeyInput): ApiKeySyncResult {
            val failed = testResult
            if (failed is CredentialValidation.Failed) {
                throw CredentialValidationFailed(failed)
            }
            savedInput = input
            val result = ApiKeySyncResult(
                apiKeyId = 1L, apiKeyName = input.name, status = SyncStatus.OK,
                newTrades = 2, duplicatesSkipped = 0, unresolvedSkipped = 0,
                partial = false, queuedSymbols = 0, error = null,
                message = "同步成功 · 新增 2", at = Instant.parse("2026-09-04T10:00:00Z"),
            )
            keys.add(ApiKeyInfo(1L, 1L, input.name, "BINANCE", Instant.now(), "OK", true))
            return result
        }

        override suspend fun removeKey(apiKeyId: Long) { removedId = apiKeyId }

        var updatedId: Long? = null
        var updatedInput: ApiKeyInput? = null

        override suspend fun updateKey(apiKeyId: Long, input: ApiKeyInput) {
            updatedId = apiKeyId
            updatedInput = input
            keys.replaceAll { if (it.id == apiKeyId) it.copy(name = input.name) else it }
        }

        override suspend fun testCredentials(input: ApiKeyInput): CredentialValidation = testResult

        var syncNowIds: MutableList<Long?> = mutableListOf()

        override suspend fun syncNow(apiKeyId: Long?): List<ApiKeySyncResult> {
            syncNowIds.add(apiKeyId)
            return keys.map {
                ApiKeySyncResult(
                    apiKeyId = it.id, apiKeyName = it.name, status = SyncStatus.OK,
                    newTrades = 3, duplicatesSkipped = 0, unresolvedSkipped = 0,
                    partial = false, queuedSymbols = 0, error = null,
                    message = "同步成功 · 新增 3", at = Instant.now(),
                )
            }
        }

        override suspend fun recentSyncLogs(limit: Int): List<SyncLogRow> = logs.take(limit)

        override suspend fun syncIntervalMinutes(): Int = interval

        override suspend fun saveSyncIntervalMinutes(minutes: Int) { interval = minutes }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.textCount(text: String, substring: Boolean = true): Int =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    @Test
    fun `empty state shows add button and no key rows`() = runComposeUiTest {
        setContent { ApiManagementPage(FakeSyncService()) }
        onNodeWithTag("api-management").assertIsDisplayed()
        onNodeWithTag("api-empty").assertIsDisplayed()
        onNodeWithTag("api-add").assertIsDisplayed()
        // 页面级手动同步入口常驻（2026-09-08 走查补口）
        onNodeWithTag("api-sync-all").assertIsDisplayed()
    }

    @Test
    fun `sync all with no keys shows guidance toast`() = runComposeUiTest {
        val svc = FakeSyncService()
        setContent { ApiManagementPage(svc) }
        onNodeWithTag("api-sync-all").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(ApiCopy.SYNC_ALL_EMPTY) >= 1 }
        assertTrue(svc.syncNowIds.isEmpty(), "无密钥不得触达同步服务")
    }

    @Test
    fun `sync all with keys syncs every key and toasts summary`() = runComposeUiTest {
        val svc = FakeSyncService().apply {
            keys.add(ApiKeyInfo(7L, 1L, "主号", "BINANCE", Instant.now(), "OK", true))
        }
        setContent { ApiManagementPage(svc) }
        waitUntil(timeoutMillis = 2_000) { textCount("主号") >= 1 }
        onNodeWithTag("api-sync-all").performClick()
        waitUntil(timeoutMillis = 2_000) { svc.syncNowIds.isNotEmpty() }
        assertEquals(listOf<Long?>(null), svc.syncNowIds, "页面级同步应同步全部密钥（null）")
        waitUntil(timeoutMillis = 2_000) { textCount("同步完成 · 1 个密钥", substring = true) >= 1 }
    }

    @Test
    fun `add modal focuses alias input and save triggers first sync toast`() = runComposeUiTest {
        val svc = FakeSyncService()
        setContent { ApiManagementPage(svc) }
        onNodeWithTag("api-add").performClick()
        onNodeWithTag("api-modal", useUnmergedTree = true).assertIsDisplayed()
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("api-name-input").assertIsFocused() }.isSuccess
        }
        onNodeWithTag("api-name-input").performTextInput("币安主号")
        onNodeWithTag("api-key-input").performTextInput("ak-abc")
        onNodeWithTag("api-secret-input").performTextInput("sk-xyz")
        onNodeWithTag("api-save").performClick()
        waitUntil(timeoutMillis = 2_000) { svc.savedInput != null }
        assertEquals("币安主号", svc.savedInput!!.name)
        waitUntil(timeoutMillis = 2_000) { textCount(ApiCopy.SAVE_AND_SYNC_TOAST, substring = true) >= 1 }
    }

    @Test
    fun `empty fields show validation errors`() = runComposeUiTest {
        val svc = FakeSyncService()
        setContent { ApiManagementPage(svc) }
        onNodeWithTag("api-add").performClick()
        onNodeWithTag("api-save", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(ApiCopy.ERR_NAME_EMPTY) >= 1 }
        assertTrue(svc.savedInput == null, "空表单不得触达服务")
    }

    @Test
    fun `invalid credentials test surfaces B2 copy and does not save`() = runComposeUiTest {
        val svc = FakeSyncService().apply {
            testResult = CredentialValidation.Failed(ExchangeError.InvalidKey)
        }
        setContent { ApiManagementPage(svc) }
        onNodeWithTag("api-add").performClick()
        onNodeWithTag("api-name-input").performTextInput("bad")
        onNodeWithTag("api-key-input").performTextInput("bad-key")
        onNodeWithTag("api-secret-input").performTextInput("bad-secret")
        onNodeWithTag("api-save", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(ApiCopy.errorText(ExchangeError.InvalidKey)) >= 1 }
        assertTrue(svc.savedInput == null, "校验失败不得落库")
        onNodeWithTag("api-dialog-error", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `key list renders status and interval selector persists`() = runComposeUiTest {
        val svc = FakeSyncService().apply {
            keys.add(ApiKeyInfo(7L, 1L, "主号", "BINANCE", Instant.now(), "OK", true))
            logs.add(SyncLogRow(1L, 1L, 7L, Instant.parse("2026-09-04T09:00:00Z"),
                SyncStatus.OK, 3, "同步成功 · 新增 3"))
        }
        setContent { ApiManagementPage(svc) }
        waitUntil(timeoutMillis = 2_000) { textCount("主号") >= 1 }
        onNodeWithTag("api-key-row-7").assertIsDisplayed()
        onNodeWithTag("api-sync-logs").assertIsDisplayed()
        onNodeWithTag("api-interval-15").performClick()
        waitUntil(timeoutMillis = 2_000) { svc.interval == 15 }
        assertEquals(15, svc.interval)
    }

    @Test
    fun `remove key triggers removal`() = runComposeUiTest {
        val svc = FakeSyncService().apply {
            keys.add(ApiKeyInfo(7L, 1L, "主号", "BINANCE", Instant.now(), "OK", true))
        }
        setContent { ApiManagementPage(svc) }
        waitUntil(timeoutMillis = 2_000) { textCount("主号") >= 1 }
        onNodeWithTag("api-key-remove-7").performClick()
        waitUntil(timeoutMillis = 2_000) { svc.removedId == 7L }
        assertEquals(7L, svc.removedId)
    }

    @Test
    fun `edit modal keeps secret blank and alias-only save routes to update not add`() = runComposeUiTest {
        val svc = FakeSyncService().apply {
            keys.add(ApiKeyInfo(7L, 1L, "主号", "BINANCE", Instant.now(), "OK", true))
        }
        setContent { ApiManagementPage(svc) }
        waitUntil(timeoutMillis = 2_000) { textCount("主号") >= 1 }
        onNodeWithTag("api-key-edit-7").performClick()
        onNodeWithTag("api-modal", useUnmergedTree = true).assertIsDisplayed()
        // 编辑态：密钥不回显（安全口径），placeholder 提示留空 = 保持不变
        onNodeWithTag("api-key-input").assertIsDisplayed()
        waitUntil(timeoutMillis = 2_000) { textCount(ApiCopy.EDIT_KEY_PLACEHOLDER) >= 1 }
        onNodeWithTag("api-name-input").performTextClearance()
        onNodeWithTag("api-name-input").performTextInput("主号改")
        onNodeWithTag("api-save", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { svc.updatedId == 7L }
        assertEquals(7L, svc.updatedId)
        assertEquals("主号改", svc.updatedInput?.name)
        assertEquals(null, svc.savedInput, "编辑不得走新增（addAndSync）")
    }

    @Test
    fun `edit with only one credential filled is rejected`() = runComposeUiTest {
        val svc = FakeSyncService().apply {
            keys.add(ApiKeyInfo(7L, 1L, "主号", "BINANCE", Instant.now(), "OK", true))
        }
        setContent { ApiManagementPage(svc) }
        waitUntil(timeoutMillis = 2_000) { textCount("主号") >= 1 }
        onNodeWithTag("api-key-edit-7").performClick()
        onNodeWithTag("api-key-input").performTextInput("ak-only")
        onNodeWithTag("api-save", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(ApiCopy.ERR_UPDATE_CREDS_PAIR) >= 1 }
        assertEquals(null, svc.updatedId, "只填其一应被拒绝，不触达服务")
    }
}
