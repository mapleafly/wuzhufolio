package com.wuzhufolio.ui.backup

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.backup.BackupMetadata
import com.wuzhufolio.domain.backup.BackupPreview
import com.wuzhufolio.domain.backup.BackupService
import com.wuzhufolio.domain.backup.CproDecodeException
import com.wuzhufolio.domain.backup.CsvExportKind
import com.wuzhufolio.domain.backup.RestoreMode
import com.wuzhufolio.domain.backup.RestorePreview
import com.wuzhufolio.domain.backup.RestoreSummary
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import java.nio.file.Path
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 备份弹窗状态（密码驻 UI 瞬态；提交时转 CharArray 并于 finally 擦除——M2 同口径）。 */
data class BackupExportState(
    val password: String = "",
    val confirm: String = "",
    val errors: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
)

/** 恢复向导状态机（PRD 5.2-5/6：选文件 → 摘要+密码 → 导入方式 → 结果）。 */
data class RestoreWizardState(
    val step: Step = Step.SELECT,
    val path: Path? = null,
    val header: BackupPreview? = null,
    val password: String = "",
    val error: String? = null,
    val preview: RestorePreview? = null,
    val mode: RestoreMode = RestoreMode.MERGE,
    val overwriteConfirmed: Boolean = false,
    val result: RestoreSummary? = null,
    val busy: Boolean = false,
) {
    enum class Step { SELECT, SUMMARY, MODE, RESULT }
}

/** 数据管理区状态（M9 · T9.4）。 */
data class BackupUiState(
    val metadata: BackupMetadata? = null,
    val export: BackupExportState? = null,
    val restore: RestoreWizardState? = null,
    val toast: WzToast? = null,
)

/**
 * 数据管理（备份恢复）VM（M9）：导出（密码 + 敏感提示 → 选路径 → 生成 .cpro）/
 * 恢复向导（文件 → 头部摘要 → 密码验证 → 合并规划预览 → 模式/二次确认 → 执行 → 结果）/
 * CSV 明文导出。文件对话框经注入 lambda（与 M7 FilePicker 同口径，由 app 层接 AWT EDT）。
 */
@Suppress("TooManyFunctions") // 页面动作面（导出/恢复向导 4 步/CSV×3/元数据/toast），小而直白
class BackupViewModel(
    private val service: BackupService,
    private val pickCproSave: () -> String?,
    private val pickCproLoad: () -> String?,
    private val pickCsvSave: (CsvExportKind) -> String?,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        reloadMetadata()
    }

    fun reloadMetadata() {
        scope.launch {
            val meta = runCatching { service.backupMetadata() }.getOrNull()
            _state.update { it.copy(metadata = meta) }
        }
    }

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }

    // ---- 导出 ----

    fun openExport() {
        _state.update { it.copy(export = BackupExportState()) }
    }

    fun closeExport() {
        _state.update { it.copy(export = null) }
    }

    fun onExportPasswordChange(value: String) {
        patchExport { it.copy(password = value, errors = it.errors - KEY_PASSWORD) }
    }

    fun onExportConfirmChange(value: String) {
        patchExport { it.copy(confirm = value, errors = it.errors - KEY_CONFIRM) }
    }

    /** 导出：本地强度/一致校验（PRD 5.2-3）→ 敏感提示已在弹窗内嵌（PRD 5.2-4）→ 选路径 → 生成。 */
    @Suppress("ReturnCount") // 校验/取路径/占用三段早退（M7/M8 表单保存同口径）
    fun exportBackup() {
        val export = _state.value.export ?: return
        if (export.busy) return
        val errors = LinkedHashMap<String, String>()
        if (!com.wuzhufolio.domain.accounts.AccountPolicy.meetsMinimum(export.password)) {
            errors[KEY_PASSWORD] = BackupCopy.EXPORT_ERROR_PWD
        }
        if (export.confirm != export.password) {
            errors[KEY_CONFIRM] = BackupCopy.EXPORT_ERROR_MISMATCH
        }
        if (errors.isNotEmpty()) {
            patchExport { it.copy(errors = errors) }
            return
        }
        val path = pickCproSave() ?: return
        patchExport { it.copy(busy = true) }
        val pw = export.password.toCharArray()
        scope.launch {
            runCatching { service.exportBackup(Path.of(path), pw) }
                .onSuccess {
                    _state.update { st ->
                        st.copy(
                            export = null,
                            toast = WzToast(WzToastKind.Success, BackupCopy.EXPORT_SUCCESS_PREFIX + path),
                        )
                    }
                    reloadMetadata()
                }
                .onFailure { t ->
                    patchExport { e -> e.copy(busy = false, errors = e.errors + (KEY_PASSWORD to (t.message ?: ""))) }
                    _state.update {
                        it.copy(
                            toast = WzToast(WzToastKind.Failure, BackupCopy.EXPORT_FAILED_PREFIX + (t.message ?: "")),
                        )
                    }
                }
        }.invokeOnCompletion { pw.fill('\u0000') }
    }

    // ---- 恢复向导 ----

    fun openRestore() {
        _state.update { it.copy(restore = RestoreWizardState()) }
    }

    fun closeRestore() {
        _state.update { it.copy(restore = null) }
    }

    /** 第一步：选择 .cpro 并解析明文头部（无需密码，PRD 5.2-5）。 */
    @Suppress("ReturnCount") // 状态/占用/取消三段早退
    fun pickRestoreFile() {
        val restore = _state.value.restore ?: return
        if (restore.busy) return
        val path = pickCproLoad() ?: return
        _state.update { it.copy(restore = restore.copy(busy = true, error = null)) }
        scope.launch {
            runCatching { service.previewBackup(Path.of(path)) }
                .onSuccess { header ->
                    _state.update {
                        it.copy(
                            restore = it.restore?.copy(
                                step = RestoreWizardState.Step.SUMMARY,
                                path = Path.of(path),
                                header = header,
                                busy = false,
                            ),
                        )
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(
                            restore = it.restore?.copy(busy = false, error = decodeCopy(t)),
                            toast = WzToast(WzToastKind.Failure, decodeCopy(t)),
                        )
                    }
                }
        }
    }

    fun onRestorePasswordChange(value: String) {
        patchRestore { it.copy(password = value, error = null) }
    }

    /** 第二步：密码验证 + 解密 + 合并规划预览（不落库）。 */
    @Suppress("ReturnCount") // 状态/路径/占用三段早退
    fun unlockRestore() {
        val restore = _state.value.restore ?: return
        val path = restore.path ?: return
        if (restore.password.isEmpty() || restore.busy) return
        _state.update { it.copy(restore = restore.copy(busy = true, error = null)) }
        val pw = restore.password.toCharArray()
        scope.launch {
            runCatching { service.prepareRestore(path, pw) }
                .onSuccess { preview ->
                    _state.update {
                        it.copy(
                            restore = it.restore?.copy(
                                step = RestoreWizardState.Step.MODE,
                                preview = preview,
                                busy = false,
                            ),
                        )
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(
                            restore = it.restore?.copy(busy = false, error = decodeCopy(t)),
                        )
                    }
                }
        }.invokeOnCompletion { pw.fill('\u0000') }
    }

    fun setMode(mode: RestoreMode) {
        patchRestore { st ->
            val confirmed = if (mode == RestoreMode.MERGE) false else st.overwriteConfirmed
            st.copy(mode = mode, overwriteConfirmed = confirmed)
        }
    }

    fun setOverwriteConfirmed(value: Boolean) {
        patchRestore { it.copy(overwriteConfirmed = value) }
    }

    /** 第三步：执行恢复（全量覆盖需二次确认勾选；完成后展示结果 + 重算提示）。 */
    @Suppress("ReturnCount") // 状态/路径/占用/确认四段早退
    fun executeRestore() {
        val restore = _state.value.restore ?: return
        val path = restore.path ?: return
        if (restore.busy) return
        if (restore.mode == RestoreMode.FULL_OVERWRITE && !restore.overwriteConfirmed) return
        _state.update { it.copy(restore = restore.copy(busy = true, error = null)) }
        val pw = restore.password.toCharArray()
        scope.launch {
            runCatching { service.restoreBackup(path, pw, restore.mode) }
                .onSuccess { summary ->
                    _state.update {
                        it.copy(
                            restore = it.restore?.copy(
                                step = RestoreWizardState.Step.RESULT,
                                result = summary,
                                busy = false,
                            ),
                            toast = WzToast(WzToastKind.Success, BackupCopy.RESTORE_SUCCESS_TOAST),
                        )
                    }
                    reloadMetadata()
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(
                            restore = it.restore?.copy(busy = false, error = decodeCopy(t)),
                            toast = WzToast(WzToastKind.Failure, decodeCopy(t)),
                        )
                    }
                }
        }.invokeOnCompletion { pw.fill('\u0000') }
    }

    // ---- CSV 明文导出 ----

    fun exportCsv(kind: CsvExportKind) {
        val path = pickCsvSave(kind) ?: return
        scope.launch {
            runCatching { service.exportCsv(kind, Path.of(path)) }
                .onSuccess {
                    _state.update { st ->
                        st.copy(toast = WzToast(WzToastKind.Success, BackupCopy.CSV_SUCCESS_PREFIX + path))
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(toast = WzToast(WzToastKind.Failure, BackupCopy.CSV_FAILED_PREFIX + (t.message ?: "")))
                    }
                }
        }
    }

    fun dispose() {
        scope.cancel()
    }

    // ---- 内部 ----

    private fun patchExport(transform: (BackupExportState) -> BackupExportState) {
        _state.update { st ->
            val export = st.export
            if (export == null) st else st.copy(export = transform(export))
        }
    }

    private fun patchRestore(transform: (RestoreWizardState) -> RestoreWizardState) {
        _state.update { st ->
            val restore = st.restore
            if (restore == null) st else st.copy(restore = transform(restore))
        }
    }

    private fun decodeCopy(t: Throwable): String = when (t) {
        is CproDecodeException -> BackupCopy.decodeErrorCopy(t.reason)
        else -> BackupCopy.ERR_GENERIC + (t.message ?: "")
    }

    companion object {
        const val KEY_PASSWORD = "password"
        const val KEY_CONFIRM = "confirm"

        private val LOCAL_DT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        /** UTC → 本地展示（PRD 时间规则「UTC 存、本地显」）。 */
        fun formatInstant(instant: java.time.Instant?): String =
            instant?.atZone(ZoneId.systemDefault())?.format(LOCAL_DT) ?: BackupCopy.NEVER_DONE
    }
}
