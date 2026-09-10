package com.wuzhufolio.ui.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.backup.BackupPreview
import com.wuzhufolio.domain.backup.BackupService
import com.wuzhufolio.domain.backup.CsvExportKind
import com.wuzhufolio.domain.backup.RestoreMode
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 数据管理区（备份恢复 · M9 · T9.4 · ia.md §2.15 / PRD §9.9 / 原型数据管理走查）：
 * 备份区域（备份按钮 + 上次备份时间）/ 恢复区域（恢复按钮 + 上次恢复时间）/
 * 明文导出（CSV：交易 / 资金流水 / 持仓汇总，不含 API 密钥）。
 * 弹窗一律 WzModal 就地叠加（AGENTS.md §7.3），含输入框弹窗首输入框打开即聚焦。
 */
@Composable
fun DataManagementSection(
    service: BackupService,
    pickCproSave: () -> String?,
    pickCproLoad: () -> String?,
    pickCsvSave: (CsvExportKind) -> String?,
    modifier: Modifier = Modifier,
) {
    val vm = remember { BackupViewModel(service, pickCproSave, pickCproLoad, pickCsvSave) }
    DisposableEffect(vm) {
        onDispose { vm.dispose() }
    }
    val state by vm.state.collectAsState()
    val colors = WzTheme.colors

    Box(modifier = modifier.fillMaxSize().testTag("backup-section")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            SectionCard(title = BackupCopy.BACKUP_GROUP_TITLE, testTag = "backup-group") {
                MetadataLine(
                    label = BackupCopy.LAST_BACKUP_PREFIX,
                    value = BackupViewModel.formatInstant(state.metadata?.lastBackupAt),
                    testTag = "backup-last-at",
                )
                WzButton(
                    text = BackupCopy.BACKUP_BUTTON,
                    onClick = vm::openExport,
                    variant = WzButtonVariant.Primary,
                    testTag = "backup-open",
                )
            }
            SectionCard(title = BackupCopy.RESTORE_GROUP_TITLE, testTag = "restore-group") {
                MetadataLine(
                    label = BackupCopy.LAST_RESTORE_PREFIX,
                    value = BackupViewModel.formatInstant(state.metadata?.lastRestoreAt),
                    testTag = "restore-last-at",
                )
                WzButton(
                    text = BackupCopy.RESTORE_BUTTON,
                    onClick = vm::openRestore,
                    variant = WzButtonVariant.Secondary,
                    testTag = "restore-open",
                )
            }
            SectionCard(title = BackupCopy.CSV_GROUP_TITLE, testTag = "csv-group") {
                Text(
                    text = BackupCopy.CSV_NOTE,
                    color = colors.ink3,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    WzButton(
                        text = BackupCopy.CSV_TRANSACTIONS,
                        onClick = { vm.exportCsv(CsvExportKind.TRANSACTIONS) },
                        testTag = "csv-export-transactions",
                    )
                    WzButton(
                        text = BackupCopy.CSV_FUNDS,
                        onClick = { vm.exportCsv(CsvExportKind.FUNDS) },
                        testTag = "csv-export-funds",
                    )
                    WzButton(
                        text = BackupCopy.CSV_HOLDINGS,
                        onClick = { vm.exportCsv(CsvExportKind.HOLDINGS) },
                        testTag = "csv-export-holdings",
                    )
                }
            }
        }

        state.export?.let { export ->
            BackupExportModal(
                state = export,
                onPasswordChange = vm::onExportPasswordChange,
                onConfirmChange = vm::onExportConfirmChange,
                onConfirm = vm::exportBackup,
                onDismiss = vm::closeExport,
            )
        }
        state.restore?.let { restore ->
            RestoreWizardModal(
                state = restore,
                onPickFile = vm::pickRestoreFile,
                onPasswordChange = vm::onRestorePasswordChange,
                onUnlock = vm::unlockRestore,
                onModeChange = vm::setMode,
                onOverwriteConfirmChange = vm::setOverwriteConfirmed,
                onExecute = vm::executeRestore,
                onDismiss = vm::closeRestore,
            )
        }
        WzToastHost(toast = state.toast, onDismiss = vm::dismissToast)
    }
}

/** 分区卡（surface 底 + 1px 边框；与设置分组卡片同款视觉）。 */
@Composable
private fun SectionCard(
    title: String,
    testTag: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = WzTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(10.dp))
            .padding(16.dp)
            .testTag(testTag),
    ) {
        Text(text = title, color = colors.ink, style = WzTheme.typography.pageTitle)
        content()
    }
}

@Composable
private fun MetadataLine(label: String, value: String, testTag: String) {
    val colors = WzTheme.colors
    Text(
        text = label + value,
        color = colors.ink3,
        style = WzTheme.typography.caption,
        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp).testTag(testTag),
    )
}

// ---------------------------------------------------------------------------
// 备份弹窗（PRD 5.2-3/4：密码设置 + 敏感提示内嵌；确认后选路径生成）
// ---------------------------------------------------------------------------

@Composable
private fun BackupExportModal(
    state: BackupExportState,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WzTheme.colors
    val pwdFocus = remember { FocusRequester() }
    WzModal(
        title = BackupCopy.EXPORT_TITLE,
        onDismiss = onDismiss,
        width = 520.dp,
        testTag = "backup-export-modal",
        initialFocusRequester = pwdFocus,
    ) {
        Text(
            text = BackupCopy.EXPORT_SENSITIVE_WARNING,
            color = colors.warn,
            style = WzTheme.typography.body,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(colors.surface2)
                .border(1.dp, colors.line, RoundedCornerShape(7.dp))
                .padding(12.dp)
                .testTag("backup-sensitive-warning"),
        )
        Text(
            text = BackupCopy.EXPORT_PWD_NOTE,
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.padding(top = 10.dp),
        )
        WzTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = BackupCopy.EXPORT_PWD_LABEL,
            placeholder = BackupCopy.EXPORT_PWD_RULE,
            isPassword = true,
            error = state.errors[BackupViewModel.KEY_PASSWORD],
            fieldFocusRequester = pwdFocus,
            testTag = "backup-pwd",
            modifier = Modifier.padding(top = 10.dp),
        )
        WzTextField(
            value = state.confirm,
            onValueChange = onConfirmChange,
            label = BackupCopy.EXPORT_PWD_CONFIRM_LABEL,
            isPassword = true,
            error = state.errors[BackupViewModel.KEY_CONFIRM],
            testTag = "backup-pwd-confirm",
            modifier = Modifier.padding(top = 8.dp),
        )
        WzButton(
            text = if (state.busy) "生成中…" else BackupCopy.EXPORT_CONFIRM,
            onClick = onConfirm,
            variant = WzButtonVariant.Primary,
            enabled = !state.busy,
            testTag = "backup-export-confirm",
            modifier = Modifier.padding(top = 14.dp).align(Alignment.End),
        )
    }
}

// ---------------------------------------------------------------------------
// 恢复向导（flows §6 全路径：选文件 → 摘要 → 密码 → 方式 → 结果）
// ---------------------------------------------------------------------------

@Composable
private fun RestoreWizardModal(
    state: RestoreWizardState,
    onPickFile: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onModeChange: (RestoreMode) -> Unit,
    onOverwriteConfirmChange: (Boolean) -> Unit,
    onExecute: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WzTheme.colors
    val firstInput = remember { FocusRequester() }
    WzModal(
        title = BackupCopy.RESTORE_TITLE,
        onDismiss = onDismiss,
        width = 560.dp,
        testTag = "restore-modal",
    ) {
        // 多步向导：WzModal 的首组合聚焦只跑一次，步骤进入 SUMMARY 时显式聚焦首输入框（共性约束 7.3-②）
        androidx.compose.runtime.LaunchedEffect(state.step) {
            if (state.step == RestoreWizardState.Step.SUMMARY) {
                firstInput.requestFocus()
            }
        }
        when (state.step) {
            RestoreWizardState.Step.SELECT -> {
                Text(
                    text = BackupCopy.RESTORE_PICK_HINT,
                    color = colors.ink2,
                    style = WzTheme.typography.body,
                )
                state.error?.let {
                    Text(
                        text = it,
                        color = colors.loss,
                        style = WzTheme.typography.caption,
                        modifier = Modifier.padding(top = 8.dp).testTag("restore-error"),
                    )
                }
                WzButton(
                    text = BackupCopy.RESTORE_PICK_BUTTON,
                    onClick = onPickFile,
                    variant = WzButtonVariant.Primary,
                    enabled = !state.busy,
                    testTag = "restore-pick",
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            RestoreWizardState.Step.SUMMARY -> {
                state.header?.let { HeaderSummary(header = it) }
                WzTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = BackupCopy.RESTORE_PWD_LABEL,
                    isPassword = true,
                    fieldFocusRequester = firstInput,
                    testTag = "restore-pwd",
                    modifier = Modifier.padding(top = 12.dp),
                )
                state.error?.let {
                    Text(
                        text = it,
                        color = colors.loss,
                        style = WzTheme.typography.caption,
                        modifier = Modifier.padding(top = 8.dp).testTag("restore-error"),
                    )
                }
                WzButton(
                    text = if (state.busy) "验证中…" else BackupCopy.RESTORE_UNLOCK,
                    onClick = onUnlock,
                    variant = WzButtonVariant.Primary,
                    enabled = !state.busy && state.password.isNotEmpty(),
                    testTag = "restore-unlock",
                    modifier = Modifier.padding(top = 14.dp).align(Alignment.End),
                )
            }
            RestoreWizardState.Step.MODE -> {
                Text(
                    text = BackupCopy.MODE_TITLE,
                    color = colors.ink,
                    style = WzTheme.typography.bodyStrong,
                )
                state.preview?.let { preview ->
                    Text(
                        text = BackupCopy.MERGE_PREVIEW_PREFIX +
                            preview.plan.businessInserts + BackupCopy.MERGE_PREVIEW_DUP +
                            preview.plan.duplicateSkipped + BackupCopy.MERGE_PREVIEW_MISSING +
                            preview.plan.missingCoinSkipped + "条",
                        color = colors.ink3,
                        style = WzTheme.typography.caption,
                        modifier = Modifier.padding(top = 4.dp).testTag("restore-plan"),
                    )
                    if (preview.plan.missingCoinIds.isNotEmpty()) {
                        Text(
                            text = preview.plan.missingCoinIds.joinToString(", "),
                            color = colors.warn,
                            style = WzTheme.typography.caption,
                            modifier = Modifier.padding(top = 2.dp).testTag("restore-missing-coins"),
                        )
                    }
                }
                ModeOption(
                    title = BackupCopy.MODE_MERGE,
                    desc = BackupCopy.MODE_MERGE_DESC,
                    selected = state.mode == RestoreMode.MERGE,
                    onClick = { onModeChange(RestoreMode.MERGE) },
                    testTag = "restore-mode-merge",
                )
                ModeOption(
                    title = BackupCopy.MODE_OVERWRITE,
                    desc = BackupCopy.MODE_OVERWRITE_DESC,
                    selected = state.mode == RestoreMode.FULL_OVERWRITE,
                    onClick = { onModeChange(RestoreMode.FULL_OVERWRITE) },
                    testTag = "restore-mode-overwrite",
                )
                if (state.mode == RestoreMode.FULL_OVERWRITE) {
                    ConfirmCheckRow(
                        label = BackupCopy.OVERWRITE_CONFIRM_LABEL,
                        checked = state.overwriteConfirmed,
                        onCheckedChange = onOverwriteConfirmChange,
                        testTag = "restore-overwrite-confirm",
                    )
                }
                state.error?.let {
                    Text(
                        text = it,
                        color = colors.loss,
                        style = WzTheme.typography.caption,
                        modifier = Modifier.padding(top = 8.dp).testTag("restore-error"),
                    )
                }
                WzButton(
                    text = if (state.busy) "恢复中…" else BackupCopy.RESTORE_EXECUTE,
                    onClick = onExecute,
                    variant = WzButtonVariant.Primary,
                    enabled = !state.busy &&
                        (state.mode == RestoreMode.MERGE || state.overwriteConfirmed),
                    testTag = "restore-execute",
                    modifier = Modifier.padding(top = 14.dp).align(Alignment.End),
                )
            }
            RestoreWizardState.Step.RESULT -> {
                state.result?.let { result ->
                    val imported = result.imported
                    Text(
                        text = BackupCopy.RESULT_IMPORTED_PREFIX +
                            "${BackupCopy.COUNTS_TX}${imported.transactions} / " +
                            "${BackupCopy.COUNTS_FLOW}${imported.capitalFlows} / " +
                            "${BackupCopy.COUNTS_RECON}${imported.reconciliationRecords} / " +
                            "${BackupCopy.COUNTS_FEE}${imported.feeRules} / " +
                            "${BackupCopy.COUNTS_KEYS}${imported.apiKeys} / " +
                            BackupCopy.COUNTS_SNAPSHOTS + imported.priceSnapshots,
                        color = colors.ink2,
                        style = WzTheme.typography.body,
                        modifier = Modifier.testTag("restore-result"),
                    )
                    Text(
                        text = BackupCopy.RESULT_SKIPPED_PREFIX + result.duplicateSkipped,
                        color = colors.ink3,
                        style = WzTheme.typography.caption,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    val missing = if (result.missingCoinIds.isEmpty()) {
                        BackupCopy.RESULT_NONE
                    } else {
                        result.missingCoinIds.joinToString(", ")
                    }
                    Text(
                        text = BackupCopy.RESULT_MISSING_PREFIX + missing,
                        color = if (result.missingCoinIds.isEmpty()) colors.ink3 else colors.warn,
                        style = WzTheme.typography.caption,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    result.tempBackupPath?.let { temp ->
                        Text(
                            text = BackupCopy.RESULT_TEMP_BACKUP_PREFIX + temp,
                            color = colors.ink3,
                            style = WzTheme.typography.caption,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    val newAnomalous = if (result.anomalousCoins.isEmpty()) {
                        BackupCopy.RESULT_NONE
                    } else {
                        result.anomalousCoins.joinToString(", ")
                    }
                    Text(
                        text = BackupCopy.RESULT_ANOMALOUS_PREFIX + newAnomalous,
                        color = if (result.anomalousCoins.isEmpty()) colors.ink3 else colors.warn,
                        style = WzTheme.typography.caption,
                        modifier = Modifier.padding(top = 4.dp).testTag("restore-anomalous"),
                    )
                    val existingAnomalous = result.preExistingAnomalousCoins
                    if (existingAnomalous.isNotEmpty()) {
                        Text(
                            text = BackupCopy.RESULT_ANOMALOUS_EXISTING_PREFIX +
                                existingAnomalous.joinToString(", "),
                            color = colors.ink3,
                            style = WzTheme.typography.caption,
                            modifier = Modifier.padding(top = 2.dp).testTag("restore-anomalous-existing"),
                        )
                        Text(
                            text = BackupCopy.RESULT_ANOMALOUS_EXISTING_NOTE,
                            color = colors.ink3,
                            style = WzTheme.typography.caption,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                WzButton(
                    text = BackupCopy.RESULT_DONE,
                    onClick = onDismiss,
                    variant = WzButtonVariant.Primary,
                    testTag = "restore-result-done",
                    modifier = Modifier.padding(top = 14.dp).align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun HeaderSummary(header: BackupPreview) {
    val colors = WzTheme.colors
    val counts = header.counts
    val range = listOf(BackupViewModel.formatInstant(header.rangeMin), BackupViewModel.formatInstant(header.rangeMax))
        .joinToString(" ~ ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(colors.surface2)
            .border(1.dp, colors.line, RoundedCornerShape(7.dp))
            .padding(12.dp)
            .testTag("restore-summary"),
    ) {
        SummaryLine(BackupCopy.SUMMARY_VERSION + header.formatVersion)
        SummaryLine(BackupCopy.SUMMARY_APP + header.appVersion)
        SummaryLine(BackupCopy.SUMMARY_EXPORTED_AT + BackupViewModel.formatInstant(header.exportedAt))
        SummaryLine(BackupCopy.SUMMARY_RANGE + range)
        SummaryLine(
            BackupCopy.SUMMARY_COUNTS +
                BackupCopy.COUNTS_TX + counts.transactions + " · " +
                BackupCopy.COUNTS_FLOW + counts.capitalFlows + " · " +
                BackupCopy.COUNTS_RECON + counts.reconciliationRecords + " · " +
                BackupCopy.COUNTS_FEE + counts.feeRules + " · " +
                BackupCopy.COUNTS_KEYS + counts.apiKeys + " · " +
                BackupCopy.COUNTS_SNAPSHOTS + counts.priceSnapshots,
        )
    }
}

@Composable
private fun SummaryLine(text: String) {
    val colors = WzTheme.colors
    Text(
        text = text,
        color = colors.ink2,
        style = WzTheme.typography.caption,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun ModeOption(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = WzTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(colors.surface2)
            .border(
                1.dp,
                if (selected) colors.accent else colors.line,
                RoundedCornerShape(7.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag(testTag),
    ) {
        Text(
            text = if (selected) "● " + title else "○ " + title,
            color = colors.ink,
            style = WzTheme.typography.bodyStrong,
        )
        Text(
            text = desc,
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ConfirmCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clickable { onCheckedChange(!checked) }
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null, // 整行可点（与 M2 风险确认同款，避免双触发）
            colors = CheckboxDefaults.colors(
                checkedColor = colors.accent,
                uncheckedColor = colors.line,
                checkmarkColor = colors.accentInk,
            ),
        )
        Text(text = label, color = colors.ink2, style = WzTheme.typography.caption)
    }
}
