package com.wuzhufolio.ui.market

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.market.MarketKeyStatus
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.MarketRefreshResult
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.MarketSettingsService
import com.wuzhufolio.domain.market.PriceSource
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

/** Key 编辑弹窗（原型 openCgKey/openCmcKey 对应物）。 */
enum class MarketKeyDialog { NONE, CG, CMC }

/** 行情设置页状态（T5.5）。 */
data class MarketSettingsUiState(
    val keyStatus: MarketKeyStatus = MarketKeyStatus(cgConfigured = false, cmcConfigured = false),
    val refreshMinutes: Int = 5,
    val dialog: MarketKeyDialog = MarketKeyDialog.NONE,
    val dialogBusy: Boolean = false,
    val dialogError: String? = null,
    val refreshBusy: Boolean = false,
    val lastRefresh: MarketRefreshResult? = null,
    val toast: WzToast? = null,
)

/**
 * 行情设置 VM（T5.5）：Key 保存/移除（保存即生效）、刷新频率档位、手动立即刷新 + 结果/错误文案映射
 * （B3/B4/B5/N 文案经 MarketCopy.errorText）；明文 Key 只短暂驻留输入框，不经 VM 状态。
 */
@Suppress("TooManyFunctions") // 每个对话框/动作一个入口（key 保存/移除 × 2 + 频率 + 刷新 + 内部状态），动作面固有
class MarketSettingsViewModel(
    private val settingsService: MarketSettingsService,
    private val refreshService: MarketRefreshService,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(MarketSettingsUiState())
    val state: StateFlow<MarketSettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        scope.launch {
            _state.update {
                it.copy(
                    keyStatus = settingsService.keyStatus(),
                    refreshMinutes = settingsService.refreshFrequencyMinutes(),
                    lastRefresh = refreshService.lastResult(),
                )
            }
        }
    }

    // ---- Key 弹窗 ----

    fun openDialog(dialog: MarketKeyDialog) {
        _state.update { it.copy(dialog = dialog, dialogError = null) }
    }

    fun closeDialog() {
        _state.update { it.copy(dialog = MarketKeyDialog.NONE, dialogBusy = false, dialogError = null) }
    }

    /** 保存 CG Key（空串保持现状——原型 toast 语义）。 */
    fun saveCgKey(input: String) {
        val key = input.trim()
        if (key.isEmpty()) {
            _state.update { it.copy(dialogError = MarketCopy.EMPTY_KEY_ERROR_CG) }
            return
        }
        save({ settingsService.saveCgKey(key) }) {
            toast(WzToastKind.Success, MarketCopy.TOAST_CG_SAVED)
        }
    }

    fun removeCgKey() {
        save({ settingsService.removeCgKey() }) {
            toast(WzToastKind.Success, MarketCopy.TOAST_CG_REMOVED)
        }
    }

    /** 保存 CMC Key（空串保持现状）。 */
    fun saveCmcKey(input: String) {
        val key = input.trim()
        if (key.isEmpty()) {
            _state.update { it.copy(dialogError = MarketCopy.EMPTY_KEY_ERROR_CMC) }
            return
        }
        save({ settingsService.saveCmcKey(key) }) {
            toast(WzToastKind.Success, MarketCopy.TOAST_CMC_SAVED)
        }
    }

    fun removeCmcKey() {
        save({ settingsService.removeCmcKey() }) {
            toast(WzToastKind.Success, MarketCopy.TOAST_CMC_REMOVED)
        }
    }

    private fun save(block: suspend () -> MarketKeyStatus, onSuccess: () -> Unit) {
        if (_state.value.dialogBusy) return
        _state.update { it.copy(dialogBusy = true, dialogError = null) }
        scope.launch {
            try {
                val status = block()
                _state.update { it.copy(keyStatus = status, dialog = MarketKeyDialog.NONE, dialogError = null) }
                onSuccess()
            } catch (t: Throwable) {
                _state.update { it.copy(dialogError = t.message ?: MarketCopy.SAVE_FAILED) }
            } finally {
                _state.update { it.copy(dialogBusy = false) }
            }
        }
    }

    // ---- 刷新频率 ----

    fun selectRefreshMinutes(minutes: Int) {
        if (_state.value.refreshMinutes == minutes) return
        scope.launch {
            runCatching { settingsService.saveRefreshFrequencyMinutes(minutes) }
                .onSuccess { _state.update { it.copy(refreshMinutes = minutes) } }
                .onFailure { toast(WzToastKind.Failure, MarketCopy.saveFrequencyFailed(it.message ?: "")) }
        }
    }

    // ---- 手动刷新 ----

    fun refreshNow() {
        if (_state.value.refreshBusy) return
        _state.update { it.copy(refreshBusy = true) }
        scope.launch {
            try {
                val result = refreshService.refresh(manual = true)
                _state.update { it.copy(lastRefresh = result, keyStatus = settingsService.keyStatus()) }
                onRefreshResult(result)
            } catch (t: Throwable) {
                toast(WzToastKind.Failure, t.message ?: MarketCopy.REFRESH_FAILED)
            } finally {
                _state.update { it.copy(refreshBusy = false) }
            }
        }
    }

    private fun onRefreshResult(result: MarketRefreshResult) {
        val error = result.error
        if (error != null) {
            toast(WzToastKind.Failure, MarketCopy.errorText(error))
            return
        }
        if (result.refreshedCoins == 0 && result.untracked.isNotEmpty()) {
            toast(WzToastKind.Failure, MarketCopy.errorText(MarketRefreshError.Untracked(result.untracked.first())))
            return
        }
        val text = when (result.source) {
            PriceSource.COINMARKETCAP -> MarketCopy.TOAST_REFRESH_CMC
            else -> if (result.cgConfigured) {
                MarketCopy.TOAST_REFRESH_CG_KEYED
            } else {
                MarketCopy.TOAST_REFRESH_CG_KEYLESS
            }
        }
        toast(WzToastKind.Success, text)
    }

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun toast(kind: WzToastKind, message: String) {
        _state.update { it.copy(toast = WzToast(kind, message)) }
    }

    override fun onCleared() {
        scope.cancel()
    }

    /** 页面卸载显式释放（Compose remember VM 生命周期无宿主时调用）。 */
    fun dispose() {
        scope.cancel()
    }
}
