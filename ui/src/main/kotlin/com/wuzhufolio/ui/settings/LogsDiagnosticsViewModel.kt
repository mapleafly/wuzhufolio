package com.wuzhufolio.ui.settings

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.settings.DiagnosticsService
import com.wuzhufolio.domain.settings.DiagnosticsReportText
import com.wuzhufolio.domain.settings.LogAccess
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import com.wuzhufolio.ui.i18n.settingsStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 弹窗状态（查看日志 / 导出确认 / 诊断报告预览）。 */
enum class LogsDialog { NONE, VIEW, EXPORT_CONFIRM, REPORT }

/** 日志与诊断 UI 状态。 */
data class LogsDiagnosticsUiState(
    val dialog: LogsDialog = LogsDialog.NONE,
    /** 查看日志：尾部行（已脱敏）。 */
    val logLines: List<String> = emptyList(),
    /** 诊断报告预览文本（渲染 = DiagnosticsReportText，导出同源）。 */
    val reportText: String? = null,
    val busy: Boolean = false,
    val toast: WzToast? = null,
)

/**
 * 日志与诊断 VM（M10 · T10.2/T10.3）：
 * 查看日志（尾部已脱敏行）/ 导出日志（确认提示 → 用户选路径 → 逐行脱敏写盘）/
 * 生成诊断报告（内容受限清单 → 预览 → 保存文件）。文件对话框经注入 lambda（EDT 口径同 M7 FilePicker）。
 */
class LogsDiagnosticsViewModel(
    private val diagnosticsService: DiagnosticsService,
    private val logAccess: LogAccess,
    /** 系统保存对话框（返回所选绝对路径；取消返回 null）。 */
    private val pickSave: (title: String) -> String?,
    /** 文本写盘（诊断报告保存；路径已由 pickSave 选定）。 */
    private val writeTextFile: (path: String, content: String) -> Unit,
) : ViewModel() {

    /** 查看日志条数上限（弹窗展示口径；interaction §2.6「最近日志片段」）。 */
    private val viewLines = 200

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(LogsDiagnosticsUiState())
    val state: StateFlow<LogsDiagnosticsUiState> = _state.asStateFlow()

    /** 查看日志（原型 openLogs：时间戳/操作类型/结果，已脱敏）。 */
    fun openLogs() {
        _state.update { it.copy(dialog = LogsDialog.VIEW, busy = true) }
        scope.launch {
            val lines = runCatching { logAccess.tailLines(viewLines) }.getOrDefault(emptyList())
            _state.update { it.copy(logLines = lines, busy = false) }
        }
    }

    /** 导出日志第一步：确认提示（PRD「导出日志前提示用户检查敏感信息」）。 */
    fun openExportConfirm() {
        _state.update { it.copy(dialog = LogsDialog.EXPORT_CONFIRM) }
    }

    /** 导出日志第二步：用户已确认 → 选路径 → 脱敏导出。 */
    fun confirmExport() {
        _state.update { it.copy(dialog = LogsDialog.NONE) }
        val target = pickSave(settingsStrings.logsExportDialogTitle) ?: return
        scope.launch {
            runCatching { logAccess.exportTo(target) }
                .onSuccess { count ->
                    _state.update {
                        val msg = SettingsCopy.LOGS_EXPORTED_TOAST + target + settingsStrings.logLineCount(count)
                        it.copy(toast = WzToast(WzToastKind.Success, msg))
                    }
                }
                .onFailure { t ->
                    _state.update {
                        val msg = SettingsCopy.LOGS_EXPORT_FAILED_TOAST + (t.message ?: "")
                        it.copy(toast = WzToast(WzToastKind.Failure, msg))
                    }
                }
        }
    }

    /** 生成诊断报告（内容受限清单；预览与导出同源渲染）。 */
    fun generateReport() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        scope.launch {
            runCatching { DiagnosticsReportText.render(diagnosticsService.generate()) }
                .onSuccess { text -> _state.update { it.copy(reportText = text, dialog = LogsDialog.REPORT) } }
                .onFailure { t ->
                    _state.update {
                        val msg = SettingsCopy.DIAG_FAILED_TOAST + (t.message ?: "")
                        it.copy(toast = WzToast(WzToastKind.Failure, msg))
                    }
                }
            _state.update { it.copy(busy = false) }
        }
    }

    /** 诊断报告保存到文件（用户选路径）。 */
    fun saveReport() {
        val text = _state.value.reportText ?: return
        val target = pickSave(settingsStrings.diagnosticsSaveDialogTitle) ?: return
        scope.launch {
            runCatching { writeTextFile(target, text) }
                .onSuccess {
                    _state.update {
                        val msg = SettingsCopy.DIAG_SAVED_TOAST + target
                        it.copy(dialog = LogsDialog.NONE, toast = WzToast(WzToastKind.Success, msg))
                    }
                }
                .onFailure { t ->
                    _state.update {
                        val msg = SettingsCopy.DIAG_FAILED_TOAST + (t.message ?: "")
                        it.copy(toast = WzToast(WzToastKind.Failure, msg))
                    }
                }
        }
    }

    fun closeDialog() = _state.update { it.copy(dialog = LogsDialog.NONE) }

    fun dismissToast() = _state.update { it.copy(toast = null) }

    override fun onCleared() {
        scope.cancel()
    }

    fun dispose() {
        scope.cancel()
    }
}

/** API 同步间隔 VM（M10：间隔行自 API 管理页归位「行情与同步」分组——M6 遗留 2）。 */
class SyncIntervalViewModel(
    private val loadInterval: suspend () -> Int,
    private val saveInterval: suspend (Int) -> Unit,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _minutes = MutableStateFlow(30)
    val minutes: StateFlow<Int> = _minutes.asStateFlow()

    init {
        scope.launch {
            runCatching { loadInterval() }.onSuccess { _minutes.value = it }
        }
    }

    fun select(minutes: Int) {
        if (minutes == _minutes.value) return
        val previous = _minutes.value
        _minutes.value = minutes
        scope.launch {
            runCatching { saveInterval(minutes) }
                .onFailure {
                    _minutes.value = previous // 回滚显示
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
