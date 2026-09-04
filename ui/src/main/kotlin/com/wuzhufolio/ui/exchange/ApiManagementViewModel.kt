package com.wuzhufolio.ui.exchange

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.exchange.ApiKeyInfo
import com.wuzhufolio.domain.exchange.ApiKeyInput
import com.wuzhufolio.domain.exchange.ApiKeySyncResult
import com.wuzhufolio.domain.exchange.CredentialValidation
import com.wuzhufolio.domain.exchange.CredentialValidationFailed
import com.wuzhufolio.domain.exchange.ExchangeCadence
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.exchange.SyncLogRow
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** API 管理弹窗状态（T6.4：添加/编辑共用表单）。 */
enum class ApiDialog { NONE, ADD, EDIT }

/** API 管理页状态。 */
data class ApiManagementUiState(
    val keys: List<ApiKeyInfo> = emptyList(),
    val syncLogs: List<SyncLogRow> = emptyList(),
    val intervalMinutes: Int = ExchangeCadence.DEFAULT_MINUTES,
    val dialog: ApiDialog = ApiDialog.NONE,
    val editingKey: ApiKeyInfo? = null,
    val dialogBusy: Boolean = false,
    val dialogError: String? = null,
    val syncingKeyId: Long? = null,
    val toast: WzToast? = null,
)

/**
 * API 管理 VM（T6.4 · 契约 = ExchangeSyncService）：列表/最近同步记录/间隔档位加载；
 * 添加（测试请求 → 保存即首次同步）/移除/单 key 立即同步；状态经 UI 状态流，明文 Key 只驻留输入框。
 */
@Suppress("TooManyFunctions")
class ApiManagementViewModel(private val service: ExchangeSyncService) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(ApiManagementUiState())
    val state: StateFlow<ApiManagementUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        scope.launch {
            runCatching {
                val keys = service.listKeys()
                val logs = service.recentSyncLogs(5)
                val interval = service.syncIntervalMinutes()
                _state.update { it.copy(keys = keys, syncLogs = logs, intervalMinutes = interval) }
            }.onFailure { t -> toast(WzToastKind.Failure, t.message ?: ApiCopy.ERR_GENERIC) }
        }
    }

    // ---- 弹窗 -

    fun openAdd() {
        _state.update { it.copy(dialog = ApiDialog.ADD, editingKey = null, dialogError = null) }
    }

    fun openEdit(key: ApiKeyInfo) {
        _state.update { it.copy(dialog = ApiDialog.EDIT, editingKey = key, dialogError = null) }
    }

    fun closeDialog() {
        _state.update { it.copy(dialog = ApiDialog.NONE, editingKey = null, dialogBusy = false, dialogError = null) }
    }

    /** 测试请求（只校验不落库；原型 testApi → toast）。 */
    fun test(input: ApiKeyInput) {
        if (_state.value.dialogBusy) return
        _state.update { it.copy(dialogBusy = true, dialogError = null) }
        scope.launch {
            try {
                when (val r = service.testCredentials(input)) {
                    is CredentialValidation.Ok -> toast(WzToastKind.Success, ApiCopy.TEST_PASSED)
                    is CredentialValidation.Failed -> _state.update { it.copy(dialogError = ApiCopy.errorText(r.kind)) }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(dialogError = t.message ?: ApiCopy.ERR_GENERIC) }
            } finally {
                _state.update { it.copy(dialogBusy = false) }
            }
        }
    }

    /** 保存（编辑 = 校验 + 覆盖重包；添加 = 校验通过落库 + 立即首次同步）。 */
    fun save(input: ApiKeyInput) {
        if (_state.value.dialogBusy) return
        if (input.name.trim().isEmpty()) {
            _state.update { it.copy(dialogError = ApiCopy.ERR_NAME_EMPTY) }
            return
        }
        if (input.apiKey.trim().isEmpty()) {
            _state.update { it.copy(dialogError = ApiCopy.ERR_KEY_EMPTY) }
            return
        }
        if (input.secretKey.trim().isEmpty()) {
            _state.update { it.copy(dialogError = ApiCopy.ERR_SECRET_EMPTY) }
            return
        }
        _state.update { it.copy(dialogBusy = true, dialogError = null) }
        scope.launch {
            try {
                val result = service.addAndSync(input)
                closeDialog()
                toast(WzToastKind.Success, ApiCopy.SAVE_AND_SYNC_TOAST)
                onSyncResult(result)
                load()
            } catch (e: CredentialValidationFailed) {
                _state.update { it.copy(dialogBusy = false, dialogError = ApiCopy.errorText(e.validation.kind)) }
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(dialogBusy = false, dialogError = e.message ?: ApiCopy.ERR_GENERIC) }
            } catch (t: Throwable) {
                _state.update { it.copy(dialogBusy = false, dialogError = t.message ?: ApiCopy.ERR_GENERIC) }
            }
        }
    }

    fun remove(key: ApiKeyInfo) {
        scope.launch {
            try {
                service.removeKey(key.id)
                toast(WzToastKind.Success, ApiCopy.REMOVED_TOAST)
                load()
            } catch (t: Throwable) {
                toast(WzToastKind.Failure, t.message ?: ApiCopy.ERR_GENERIC)
            }
        }
    }

    fun syncNow(key: ApiKeyInfo) {
        if (_state.value.syncingKeyId != null) return
        _state.update { it.copy(syncingKeyId = key.id) }
        scope.launch {
            try {
                val result = service.syncNow(key.id).singleOrNull()
                if (result != null) onSyncResult(result)
                load()
            } catch (t: Throwable) {
                toast(WzToastKind.Failure, t.message ?: ApiCopy.ERR_GENERIC)
            } finally {
                _state.update { it.copy(syncingKeyId = null) }
            }
        }
    }

    fun selectInterval(minutes: Int) {
        if (minutes == _state.value.intervalMinutes) return
        scope.launch {
            runCatching { service.saveSyncIntervalMinutes(minutes) }
                .onSuccess { _state.update { it.copy(intervalMinutes = minutes) } }
                .onFailure { toast(WzToastKind.Failure, it.message ?: ApiCopy.ERR_GENERIC) }
        }
    }

    fun dismissToast() { _state.update { it.copy(toast = null) } }

    private fun onSyncResult(result: ApiKeySyncResult) {
        val error = result.error
        if (error != null) {
            toast(WzToastKind.Failure, ApiCopy.errorText(error))
            return
        }
        val text = if (result.partial) {
            ApiCopy.SYNC_PARTIAL_TOAST.format(result.newTrades, result.queuedSymbols)
        } else {
            ApiCopy.SYNC_DONE_TOAST.format(result.newTrades, result.duplicatesSkipped)
        }
        toast(WzToastKind.Success, text)
    }

    private fun toast(kind: WzToastKind, message: String) {
        _state.update { it.copy(toast = WzToast(kind, message)) }
    }

    override fun onCleared() { scope.cancel() }

    fun dispose() { scope.cancel() }
}