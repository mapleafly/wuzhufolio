package com.wuzhufolio.ui.exchange

import com.wuzhufolio.domain.exchange.ApiKeyInfo
import com.wuzhufolio.domain.exchange.ApiKeyInput
import com.wuzhufolio.domain.exchange.ApiKeySyncResult
import com.wuzhufolio.domain.exchange.CredentialValidation
import com.wuzhufolio.domain.exchange.ExchangeCadence
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.exchange.SyncLogRow
import com.wuzhufolio.domain.exchange.SyncStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * 顶栏手动同步 VM（M7 补口 · PRD 故事 4.3）：无密钥引导提示、有密钥汇总新增/失败计数、传 null 同步全部。
 */
class TopBarSyncViewModelTest {

    private class FakeSyncService(private val results: List<ApiKeySyncResult>) : ExchangeSyncService {
        var calls = 0
        var lastApiKeyId: Long? = -1

        override suspend fun listKeys(): List<ApiKeyInfo> = emptyList()
        override suspend fun addAndSync(input: ApiKeyInput): ApiKeySyncResult = error("unused")
        override suspend fun updateKey(apiKeyId: Long, input: ApiKeyInput) = Unit
        override suspend fun removeKey(apiKeyId: Long) = Unit
        override suspend fun testCredentials(input: ApiKeyInput): CredentialValidation = CredentialValidation.Ok
        override suspend fun syncNow(apiKeyId: Long?): List<ApiKeySyncResult> {
            calls++
            lastApiKeyId = apiKeyId
            return results
        }
        override suspend fun recentSyncLogs(limit: Int): List<SyncLogRow> = emptyList()
        override suspend fun syncIntervalMinutes(): Int = ExchangeCadence.DEFAULT_MINUTES
        override suspend fun saveSyncIntervalMinutes(minutes: Int) = Unit
    }

    private fun result(status: SyncStatus, newTrades: Int) = ApiKeySyncResult(
        apiKeyId = 1L,
        apiKeyName = "主号",
        status = status,
        newTrades = newTrades,
        duplicatesSkipped = 0,
        unresolvedSkipped = 0,
        partial = false,
        queuedSymbols = 0,
        error = null,
        message = "",
        at = Instant.now(),
    )

    private fun awaitToast(vm: TopBarSyncViewModel): String = runBlocking {
        withTimeout(3_000) {
            while (vm.toast.value == null) delay(10)
            vm.toast.value!!.message
        }
    }

    @Test
    fun noKeysShowsGuidanceAndDoesNotSync() {
        val service = FakeSyncService(emptyList())
        val vm = TopBarSyncViewModel(service)
        vm.syncNow()
        val message = awaitToast(vm)
        assertEquals(ApiCopy.SYNC_ALL_EMPTY, message)
        assertEquals(1, service.calls)
    }

    @Test
    fun keysAggregateNewTradesAndPassNullForAll() {
        val service = FakeSyncService(listOf(result(SyncStatus.OK, 3), result(SyncStatus.OK, 2)))
        val vm = TopBarSyncViewModel(service)
        vm.syncNow()
        val message = awaitToast(vm)
        assertEquals("同步完成 · 2 个密钥 · 新增 5", message)
        assertEquals(null, service.lastApiKeyId, "页面级同步应传 null 同步全部密钥")
    }

    @Test
    fun failedKeySurfacesPartialFailure() {
        val service = FakeSyncService(listOf(result(SyncStatus.OK, 1), result(SyncStatus.FAILED, 0)))
        val vm = TopBarSyncViewModel(service)
        vm.syncNow()
        val message = awaitToast(vm)
        assertEquals("同步完成（部分失败 1 个密钥）· 新增 1", message)
    }
}
