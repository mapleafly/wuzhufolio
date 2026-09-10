package com.wuzhufolio.ui.settings

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.autostart.AutostartStatus
import com.wuzhufolio.domain.settings.DesktopSettingsService
import com.wuzhufolio.domain.settings.DesktopSettingsView
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import com.wuzhufolio.ui.i18n.settingsStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 托盘与后台 UI 状态（M11 · T11.1/T11.2）。 */
data class DesktopSettingsUiState(
    val view: DesktopSettingsView = DesktopSettingsView(),
    /** 自启平台能力（supported=false → 开关置灰并显示 [AutostartStatus.unsupportedReason]）。 */
    val autostart: AutostartStatus = AutostartStatus(supported = true, enabled = false, executablePath = null),
    val busy: Boolean = false,
    val error: String? = null,
    val toast: WzToast? = null,
)

/**
 * 托盘与后台 VM（M11）：四项开关读取与写入。
 *
 * 自启是唯一可能**失败**的开关（平台注册），故其走 [setAutostart] 专用路径：
 * 失败时不改视图、回读真实状态并提示原因——避免出现「界面已开启、系统未注册」的假象。
 */
class DesktopSettingsViewModel(
    private val service: DesktopSettingsService,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(DesktopSettingsUiState())
    val state: StateFlow<DesktopSettingsUiState> = _state.asStateFlow()

    /**
     * 写入串行化锁。
     *
     * 与 M10 `GeneralSettingsViewModel` 的写法差异（有意）：那里用「busy 中直接 return」防重入，
     * 会**静默丢弃**用户在写入未完成时的第二次切换（连续拨两个开关即触发）——开关类交互被吞掉
     * 是可见缺陷。此处改为排队串行：busy 仅用于 UI 置忙，不再吞动作。
     */
    private val writeLock = Mutex()

    init {
        reload()
    }

    fun reload() {
        scope.launch { reloadNow() }
    }

    private suspend fun reloadNow() {
        runCatching { service.view() to service.autostartStatus() }
            .onSuccess { (view, autostart) ->
                _state.update { it.copy(view = view, autostart = autostart, error = null) }
            }
            .onFailure { t -> _state.update { it.copy(error = t.message ?: settingsStrings.loadFailed) } }
    }

    fun setMinimizeOnClose(on: Boolean) = launch(
        { service.setMinimizeOnClose(on) },
        settingsStrings.minimizeOnCloseSet(on),
    )

    fun setSyncNotification(on: Boolean) = launch(
        { service.setSyncNotification(on) },
        settingsStrings.syncNotificationSet(on),
    )

    fun setBackupReminder(on: Boolean) = launch(
        { service.setBackupReminder(on) },
        settingsStrings.backupReminderSet(on),
    )

    /** 开机自启（平台注册可能失败：失败提示原因并回读真实注册态）。 */
    fun setAutostart(on: Boolean) {
        scope.launch {
            writeLock.withLock {
                _state.update { it.copy(busy = true, error = null) }
                service.setAutostartEnabled(on)
                    .onSuccess {
                        _state.update {
                            it.copy(
                                toast = WzToast(
                                    WzToastKind.Success,
                                    settingsStrings.autostartSet(on),
                                ),
                            )
                        }
                    }
                    .onFailure { t ->
                        val reason = t.message ?: settingsStrings.autostartRegisterFailed
                        _state.update { it.copy(error = reason, toast = WzToast(WzToastKind.Failure, reason)) }
                    }
                reloadNow()
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun dismissToast() = _state.update { it.copy(toast = null) }

    private fun launch(block: suspend () -> Unit, successMessage: String) {
        scope.launch {
            writeLock.withLock {
                _state.update { it.copy(busy = true, error = null) }
                runCatching { block() }
                    .onSuccess {
                        _state.update { it.copy(toast = WzToast(WzToastKind.Success, successMessage)) }
                    }
                    .onFailure { t ->
                        val message = t.message ?: settingsStrings.saveFailed
                        _state.update {
                            it.copy(error = message, toast = WzToast(WzToastKind.Failure, message))
                        }
                    }
                reloadNow()
                _state.update { it.copy(busy = false) }
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
    }

    fun dispose() {
        scope.cancel()
    }
}
