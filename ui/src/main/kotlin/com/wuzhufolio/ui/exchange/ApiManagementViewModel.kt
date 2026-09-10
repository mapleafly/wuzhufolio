package com.wuzhufolio.ui.exchange

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.exchange.ApiKeyInfo
import com.wuzhufolio.domain.exchange.ApiKeyInput
import com.wuzhufolio.domain.exchange.ApiKeySyncResult
import com.wuzhufolio.domain.exchange.CredentialValidation
import com.wuzhufolio.domain.exchange.CredentialValidationFailed
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.exchange.SyncLogRow
import com.wuzhufolio.domain.exchange.SyncStatus
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
    val dialog: ApiDialog = ApiDialog.NONE,
    val editingKey: ApiKeyInfo? = null,
    val dialogBusy: Boolean = false,
    val dialogError: String? = null,
    val syncingKeyId: Long? = null,
    /** 页面级「立即同步（全部密钥）」进行中（与行级互斥）。 */
    val syncingAll: Boolean = false,
    val toast: WzToast? = null,
)

/**
 * API 管理 VM（T6.4 · 契约 = ExchangeSyncService）：列表/最近同步记录/间隔档位加载；
 * 添加（测试请求 → 保存即首次同步）/移除/单 key 立即同步；状态经 UI 状态流，明文 Key 只驻留输入框。
 * （API 同步间隔行随 M10 归位设置页「行情与同步」分组——本 VM 不再持有间隔状态。）
 */
@Suppress("TooManyFunctions", "SwallowedException", "CyclomaticComplexMethod") // 保存路由（新增/编辑）+ 异常映射（脱敏口径），复杂度来自分支固有
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
                _state.update { it.copy(keys = keys, syncLogs = logs) }
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

    /**
     * 保存（M6 验收修复轮）：
     * - 新增：别名/Key/Secret 均必填 → 校验通过落库 + 立即首次同步；
     * - 编辑：别名必填；密钥**均留空 = 仅更新别名**、均提供 = 验证后重包覆盖原行、只填其一拒绝
     *   （安全口径：编辑不回显密钥，属预期——见 ApiCopy.EDIT_KEY_PLACEHOLDER）。
     */
    fun save(input: ApiKeyInput) {
        if (_state.value.dialogBusy) return
        val editing = _state.value.editingKey
        val keyBlank = input.apiKey.trim().isEmpty()
        val secretBlank = input.secretKey.trim().isEmpty()
        val fieldError = when {
            input.name.trim().isEmpty() -> ApiCopy.ERR_NAME_EMPTY
            editing == null && (keyBlank || secretBlank) ->
                if (keyBlank) ApiCopy.ERR_KEY_EMPTY else ApiCopy.ERR_SECRET_EMPTY
            editing != null && keyBlank != secretBlank -> ApiCopy.ERR_UPDATE_CREDS_PAIR
            else -> null
        }
        if (fieldError != null) {
            _state.update { it.copy(dialogError = fieldError) }
            return
        }
        _state.update { it.copy(dialogBusy = true, dialogError = null) }
        scope.launch {
            try {
                if (editing == null) {
                    val result = service.addAndSync(input)
                    closeDialog()
                    onSaveResult(result)
                } else {
                    service.updateKey(editing.id, input)
                    closeDialog()
                    toast(WzToastKind.Success, ApiCopy.KEY_UPDATED_TOAST)
                }
                load()
            } catch (e: CredentialValidationFailed) {
                _state.update { it.copy(dialogBusy = false, dialogError = ApiCopy.errorText(e.validation.kind)) }
            } catch (e: com.wuzhufolio.domain.exchange.DuplicateApiKeyNameException) {
                _state.update { it.copy(dialogBusy = false, dialogError = ApiCopy.ERR_DUPLICATE) }
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
        if (_state.value.syncingKeyId != null || _state.value.syncingAll) return
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

    /**
     * 页面级手动同步（PRD 故事 4.3「提供手动同步按钮」；2026-09-08 走查补口）。
     * 无密钥 -> 提示引导；有密钥 -> 同步全部（syncNow(null)），汇总新增/失败计数。
     */
    fun syncAll() {
        if (_state.value.keys.isEmpty()) {
            toast(WzToastKind.Failure, ApiCopy.SYNC_ALL_EMPTY)
            return
        }
        if (_state.value.syncingAll || _state.value.syncingKeyId != null) return
        _state.update { it.copy(syncingAll = true) }
        scope.launch {
            try {
                val results = service.syncNow(null)
                val newTrades = results.sumOf { it.newTrades }
                val failed = results.count { it.status == SyncStatus.FAILED }
                val message = if (failed > 0) {
                    "同步完成（部分失败 " + failed + " 个密钥）· 新增 " + newTrades
                } else {
                    "同步完成 · " + results.size + " 个密钥 · 新增 " + newTrades
                }
                toast(if (failed > 0) WzToastKind.Failure else WzToastKind.Success, message)
                load()
            } catch (t: Throwable) {
                toast(WzToastKind.Failure, t.message ?: ApiCopy.ERR_GENERIC)
            } finally {
                _state.update { it.copy(syncingAll = false) }
            }
        }
    }

    fun dismissToast() { _state.update { it.copy(toast = null) } }

    /** 保存并首次同步完成提示（原型 saveApi toast 口径 + 结果摘要合并，单条 toast）。 */
    private fun onSaveResult(result: ApiKeySyncResult) {
        val error = result.error
        if (error != null) {
            toast(WzToastKind.Failure, ApiCopy.errorText(error))
            return
        }
        toast(WzToastKind.Success, ApiCopy.SAVE_AND_SYNC_TOAST + " 结果：" + result.message)
    }

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
