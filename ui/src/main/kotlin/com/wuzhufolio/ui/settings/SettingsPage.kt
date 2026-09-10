package com.wuzhufolio.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.backup.BackupService
import com.wuzhufolio.domain.backup.CsvExportKind
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.ledger.FeeRuleService
import com.wuzhufolio.domain.market.MarketSettingsService
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.BASE_FIAT_OPTIONS
import com.wuzhufolio.domain.settings.DesktopSettingsService
import com.wuzhufolio.domain.settings.DiagnosticsService
import com.wuzhufolio.domain.settings.GeneralSettingsService
import com.wuzhufolio.domain.settings.LogAccess
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.PrecisionPreset
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.backup.DataManagementSection
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzSelect
import com.wuzhufolio.ui.components.WzSegmented
import com.wuzhufolio.ui.components.WzSwitch
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.exchange.ApiManagementSection
import com.wuzhufolio.ui.i18n.commonStrings
import com.wuzhufolio.ui.ledger.FeeRuleSettingsSection
import com.wuzhufolio.ui.market.MarketSettingsSection
import com.wuzhufolio.ui.shell.ShellViewModel
import com.wuzhufolio.ui.theme.WzTheme

/** 设置页文件对话框集合（EDT 口径同 M7 FilePicker；由 app 组合根注入）。 */
class SettingsFilePickers(
    /** 日志导出保存路径（取消 = null）。 */
    val pickLogSave: (title: String) -> String?,
    /** 诊断报告保存路径（取消 = null）。 */
    val pickReportSave: (title: String) -> String?,
    /** 文本写盘（诊断报告）。 */
    val writeTextFile: (path: String, content: String) -> Unit,
    /** 备份 .cpro 保存路径。 */
    val pickCproSave: () -> String? = { null },
    /** 备份 .cpro 读取路径。 */
    val pickCproLoad: () -> String? = { null },
    /** CSV 导出保存路径。 */
    val pickCsvSave: (CsvExportKind) -> String? = { _ -> null },
)

/**
 * 设置页（M10 · T10.4 · ia.md §2.12 / 原型 pageSettings 走查基准）：单页分组、页面级滚动。
 * 分组 = 通用 / 网络 / 行情与同步（T5.5 行情数据源 + API 同步间隔归位）/ 日志与诊断（T10.2/T10.3）/
 * 手续费（T10.1 CRUD）/ API 管理（T6.4）/ 数据管理（M9）/ 关于。
 * 托盘与后台分组随 M11 落地（行内行为 = T11.1/T11.2 验收载体，避免无行为开关——见模块记录 M10 §5）。
 * 主题/盈亏配色经 [ShellViewModel] 写入并持久化（顶栏与设置双向同步，PRD 6.1）。
 */
@Composable
fun SettingsPage(
    shellViewModel: ShellViewModel,
    generalSettings: GeneralSettingsService,
    marketSettingsService: MarketSettingsService,
    marketRefreshService: MarketRefreshService,
    syncService: ExchangeSyncService,
    feeRuleService: FeeRuleService,
    diagnosticsService: DiagnosticsService,
    logAccess: LogAccess,
    backupService: BackupService,
    appVersion: String,
    pickers: SettingsFilePickers,
    modifier: Modifier = Modifier,
    /** M11 T11.1/T11.2：托盘与后台分组用例（null = 不渲染该分组）。 */
    desktopSettings: DesktopSettingsService? = null,
    /** 当前环境是否支持系统托盘（false → 「最小化到托盘」行置灰并说明）。 */
    trayAvailable: Boolean = true,
    /** M11 T11.3：代理开关写入后的运行期生效钩子（同步 ProxyRuntime 检测态）。 */
    onProxyEnabledChange: (Boolean) -> Unit = {},
) {
    val generalVm = remember {
        GeneralSettingsViewModel(generalSettings, onProxyEnabledChange = onProxyEnabledChange)
    }
    val logsVm = remember {
        LogsDiagnosticsViewModel(diagnosticsService, logAccess, pickers.pickLogSave, pickers.writeTextFile)
    }
    val intervalVm = remember {
        SyncIntervalViewModel(
            loadInterval = { syncService.syncIntervalMinutes() },
            saveInterval = { syncService.saveSyncIntervalMinutes(it) },
        )
    }
    DisposableEffect(generalVm) { onDispose { generalVm.dispose() } }
    DisposableEffect(logsVm) { onDispose { logsVm.dispose() } }
    DisposableEffect(intervalVm) { onDispose { intervalVm.dispose() } }

    // M11：托盘与后台分组（用例可空——既有 UI 测试与局部预览不必构造该依赖）
    val desktopVm = remember(desktopSettings) {
        desktopSettings?.let { DesktopSettingsViewModel(it) }
    }
    DisposableEffect(desktopVm) { onDispose { desktopVm?.dispose() } }
    val desktopState = desktopVm?.state?.collectAsState()?.value ?: DesktopSettingsUiState()

    val generalState by generalVm.state.collectAsState()
    val logsState by logsVm.state.collectAsState()
    val intervalMinutes by intervalVm.minutes.collectAsState()
    val themeMode by shellViewModel.themeMode.collectAsState()
    val pnlScheme by shellViewModel.pnlScheme.collectAsState()
    val language by shellViewModel.language.collectAsState()

    val colors = WzTheme.colors

    Box(modifier = modifier.fillMaxSize().testTag("settings-page")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag("settings-scroll"),
        ) {
            Text(text = SettingsCopy.PAGE_TITLE, color = colors.ink, style = WzTheme.typography.pageTitle)
            Text(
                text = SettingsCopy.PAGE_SUB,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            // ---- 通用 ----
            SettingsGroup(title = SettingsCopy.GROUP_GENERAL, testTag = "group-general") {
                SettingsRow(
                    title = SettingsCopy.FIAT_LABEL,
                    description = SettingsCopy.FIAT_SUB,
                ) {
                    WzSelect(
                        options = BASE_FIAT_OPTIONS,
                        selected = generalState.view.baseFiat,
                        onSelect = generalVm::setBaseFiat,
                        labelOf = { it },
                        testTag = "fiat-select",
                    )
                }
                SettingsRow(
                    title = SettingsCopy.THEME_LABEL,
                    description = SettingsCopy.THEME_SUB,
                ) {
                    WzSegmented(
                        options = listOf(ThemeMode.LIGHT, ThemeMode.DARK),
                        selected = themeMode,
                        onSelect = shellViewModel::setTheme,
                        labelOf = { if (it == ThemeMode.LIGHT) SettingsCopy.THEME_LIGHT else SettingsCopy.THEME_DARK },
                        testTag = "theme-seg",
                    )
                }
                // M12 T12.4：界面语言（PRD §6 I18N；与顶栏/主壳文案同源，切换即时生效并持久化）
                SettingsRow(
                    title = SettingsCopy.LANGUAGE_LABEL,
                    description = SettingsCopy.LANGUAGE_SUB,
                    testTag = "language-row",
                ) {
                    WzSegmented(
                        options = AppLanguage.entries.toList(),
                        selected = language,
                        onSelect = shellViewModel::setLanguage,
                        labelOf = { it.nativeLabel },
                        testTag = "language-seg",
                    )
                }
                SettingsRow(
                    title = SettingsCopy.PNL_LABEL,
                    description = SettingsCopy.PNL_SUB,
                ) {
                    WzSelect(
                        options = PnlColorScheme.entries.toList(),
                        selected = pnlScheme,
                        onSelect = shellViewModel::setPnlScheme,
                        labelOf = { pnlLabel(it) },
                        testTag = "pnl-select",
                    )
                }
                SettingsRow(
                    title = SettingsCopy.PRECISION_LABEL,
                    description = SettingsCopy.PRECISION_SUB,
                ) {
                    WzSelect(
                        options = PrecisionPreset.entries.toList(),
                        selected = generalState.view.precision,
                        onSelect = generalVm::setPrecision,
                        labelOf = { it.label },
                        testTag = "precision-select",
                    )
                }
                SettingsRow(
                    title = SettingsCopy.USERNAME_ENUM_LABEL,
                    description = SettingsCopy.USERNAME_ENUM_SUB,
                ) {
                    WzSwitch(
                        on = generalState.view.usernameEnumOn,
                        onToggle = { generalVm.setUsernameEnum(!generalState.view.usernameEnumOn) },
                        testTag = "username-enum-switch",
                    )
                }
                SettingsRow(
                    title = SettingsCopy.CASH_LABEL,
                    description = SettingsCopy.CASH_SUB,
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        generalState.view.cashCoinIds.forEach { cgId ->
                            val isDefault = cgId !in generalState.view.cashCoinExtra
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 2.dp).testTag("cash-coin-" + cgId),
                            ) {
                                Text(
                                    text = cgId + if (isDefault) "（" + SettingsCopy.CASH_DEFAULT_MARK + "）" else "",
                                    color = if (isDefault) colors.ink3 else colors.ink,
                                    style = WzTheme.typography.caption,
                                )
                                if (!isDefault) {
                                    WzButton(
                                        text = SettingsCopy.CASH_REMOVE,
                                        onClick = { generalVm.removeCashCoin(cgId) },
                                        variant = WzButtonVariant.Secondary,
                                        testTag = "cash-remove-" + cgId,
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            WzTextField(
                                value = generalState.cashInput,
                                onValueChange = generalVm::onCashInput,
                                label = SettingsCopy.CASH_INPUT_LABEL,
                                placeholder = "usd-coin",
                                modifier = Modifier.width(240.dp),
                                testTag = "cash-input",
                            )
                            WzButton(
                                text = SettingsCopy.CASH_ADD_BUTTON,
                                onClick = generalVm::addCashCoin,
                                variant = WzButtonVariant.Secondary,
                                testTag = "cash-add",
                            )
                        }
                    }
                }
                SettingsRow(
                    title = SettingsCopy.THRESHOLD_LABEL,
                    description = SettingsCopy.THRESHOLD_SUB,
                ) {
                    // M10 走查反馈修复轮：预设档 → 自由数值输入（用户规模差异大，0 = 不启用）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        WzTextField(
                            value = generalState.thresholdInput,
                            onValueChange = generalVm::onThresholdInput,
                            label = SettingsCopy.THRESHOLD_INPUT_LABEL,
                            placeholder = "0",
                            modifier = Modifier.width(160.dp),
                            testTag = "threshold-input",
                        )
                        WzButton(
                            text = settingsSaveLabel,
                            onClick = generalVm::saveSmallAmountThreshold,
                            variant = WzButtonVariant.Secondary,
                            testTag = "threshold-save",
                        )
                    }
                }
            }

            // ---- 网络 ----
            SettingsGroup(title = SettingsCopy.GROUP_NETWORK, testTag = "group-network") {
                SettingsRow(
                    title = SettingsCopy.PROXY_LABEL,
                    description = SettingsCopy.PROXY_SUB,
                ) {
                    WzSwitch(
                        on = generalState.view.proxyEnabled,
                        onToggle = { generalVm.setProxyEnabled(!generalState.view.proxyEnabled) },
                        testTag = "proxy-switch",
                    )
                }
            }

            // ---- 托盘与后台（M11 · T11.1/T11.2；PRD 6.1） ----
            if (desktopVm != null) {
                SettingsGroup(title = SettingsCopy.GROUP_TRAY, testTag = "group-tray") {
                    SettingsRow(
                        title = SettingsCopy.MINIMIZE_LABEL,
                        description = if (trayAvailable) {
                            SettingsCopy.MINIMIZE_SUB
                        } else {
                            SettingsCopy.MINIMIZE_UNAVAILABLE
                        },
                        testTag = "tray-minimize",
                    ) {
                        WzSwitch(
                            on = desktopState.view.minimizeOnClose && trayAvailable,
                            enabled = trayAvailable,
                            onToggle = { desktopVm.setMinimizeOnClose(!desktopState.view.minimizeOnClose) },
                            testTag = "tray-minimize-switch",
                        )
                    }
                    SettingsRow(
                        title = SettingsCopy.AUTOSTART_LABEL,
                        description = if (desktopState.autostart.supported) {
                            SettingsCopy.AUTOSTART_SUB
                        } else {
                            desktopState.autostart.unsupportedReason ?: SettingsCopy.AUTOSTART_UNAVAILABLE
                        },
                        testTag = "tray-autostart",
                    ) {
                        WzSwitch(
                            on = desktopState.autostart.enabled,
                            enabled = desktopState.autostart.supported && !desktopState.busy,
                            onToggle = { desktopVm.setAutostart(!desktopState.autostart.enabled) },
                            testTag = "tray-autostart-switch",
                        )
                    }
                    SettingsRow(
                        title = SettingsCopy.SYNC_NOTIFY_LABEL,
                        description = SettingsCopy.SYNC_NOTIFY_SUB,
                        testTag = "tray-sync-notify",
                    ) {
                        WzSwitch(
                            on = desktopState.view.syncNotification,
                            onToggle = { desktopVm.setSyncNotification(!desktopState.view.syncNotification) },
                            testTag = "tray-sync-notify-switch",
                        )
                    }
                    SettingsRow(
                        title = SettingsCopy.BACKUP_REMINDER_LABEL,
                        description = SettingsCopy.BACKUP_REMINDER_SUB,
                        testTag = "tray-backup-reminder",
                    ) {
                        WzSwitch(
                            on = desktopState.view.backupReminder,
                            onToggle = { desktopVm.setBackupReminder(!desktopState.view.backupReminder) },
                            testTag = "tray-backup-reminder-switch",
                        )
                    }
                }
            }

            // ---- 行情与同步 ----
            SettingsGroup(title = SettingsCopy.GROUP_MARKET_SYNC, testTag = "group-market-sync") {
                MarketSettingsSection(
                    settingsService = marketSettingsService,
                    refreshService = marketRefreshService,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsRow(
                    title = SettingsCopy.SYNC_INTERVAL_LABEL,
                    description = SettingsCopy.SYNC_INTERVAL_SUB,
                    testTag = "api-sync-interval",
                ) {
                    WzSelect(
                        options = listOf(15, 30, 60),
                        selected = intervalMinutes,
                        onSelect = intervalVm::select,
                        labelOf = SettingsCopy::intervalLabel,
                        testTag = "interval-select",
                    )
                }
            }

            // ---- 日志与诊断 ----
            SettingsGroup(title = SettingsCopy.GROUP_LOGS, testTag = "group-logs") {
                SettingsRow(
                    title = SettingsCopy.LOGS_VIEW_LABEL,
                    description = SettingsCopy.LOGS_VIEW_SUB,
                ) {
                    WzButton(
                        text = SettingsCopy.LOGS_VIEW_BUTTON,
                        onClick = logsVm::openLogs,
                        variant = WzButtonVariant.Secondary,
                        testTag = "logs-view",
                    )
                }
                SettingsRow(
                    title = SettingsCopy.LOGS_EXPORT_LABEL,
                    description = SettingsCopy.LOGS_EXPORT_SUB,
                ) {
                    WzButton(
                        text = SettingsCopy.LOGS_EXPORT_BUTTON,
                        onClick = logsVm::openExportConfirm,
                        variant = WzButtonVariant.Secondary,
                        testTag = "logs-export",
                    )
                }
                SettingsRow(
                    title = SettingsCopy.DIAG_LABEL,
                    description = SettingsCopy.DIAG_SUB,
                ) {
                    WzButton(
                        text = SettingsCopy.DIAG_BUTTON,
                        onClick = logsVm::generateReport,
                        variant = WzButtonVariant.Secondary,
                        enabled = !logsState.busy,
                        testTag = "diag-generate",
                    )
                }
            }

            // ---- 手续费 ----
            SettingsGroup(title = SettingsCopy.GROUP_FEE, testTag = "group-fee") {
                FeeRuleSettingsSection(service = feeRuleService, modifier = Modifier.fillMaxWidth())
            }

            // ---- API 管理 ----
            SettingsGroup(title = SettingsCopy.GROUP_API, testTag = "group-api") {
                ApiManagementSection(service = syncService, modifier = Modifier.fillMaxWidth())
            }

            // ---- 数据管理 ----
            SettingsGroup(title = SettingsCopy.GROUP_DATA, testTag = "group-data") {
                DataManagementSection(
                    service = backupService,
                    pickCproSave = pickers.pickCproSave,
                    pickCproLoad = pickers.pickCproLoad,
                    pickCsvSave = pickers.pickCsvSave,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- 关于 ----
            SettingsGroup(title = SettingsCopy.GROUP_ABOUT, testTag = "group-about") {
                AboutLine(SettingsCopy.ABOUT_VERSION_LABEL, appVersion + " · AGPL-3.0", "about-version")
                AboutLine(SettingsCopy.ABOUT_DEV_LABEL, SettingsCopy.ABOUT_DEV_VALUE, "about-dev")
                AboutLine(SettingsCopy.ABOUT_PRIVACY_LABEL, SettingsCopy.ABOUT_PRIVACY_VALUE, "about-privacy")
                AboutLine(SettingsCopy.ABOUT_SOURCE_LABEL, SettingsCopy.ABOUT_SOURCE_VALUE, "about-source")
                AboutLine(
                    SettingsCopy.ABOUT_NOTELEMETRY_LABEL,
                    SettingsCopy.ABOUT_NOTELEMETRY_VALUE,
                    "about-notelemetry",
                )
            }
        }

        // 页面级 toast（通用设置 / 日志诊断 / 托盘与后台；各分区 toast 由分区自己的 Box 承载）
        WzToastHost(toast = generalState.toast, onDismiss = generalVm::dismissToast)
        WzToastHost(toast = logsState.toast, onDismiss = logsVm::dismissToast)
        WzToastHost(toast = desktopState.toast, onDismiss = { desktopVm?.dismissToast() })
    }

    // ---- 日志与诊断弹窗 ----
    when (logsState.dialog) {
        LogsDialog.VIEW -> LogsModal(
            title = SettingsCopy.LOGS_MODAL_TITLE,
            body = logsState.logLines.joinToString("\n").ifEmpty { SettingsCopy.LOGS_EMPTY },
            confirmLabel = null,
            onConfirm = {},
            onClose = logsVm::closeDialog,
        )
        LogsDialog.EXPORT_CONFIRM -> LogsModal(
            title = SettingsCopy.LOGS_EXPORT_BUTTON,
            body = SettingsCopy.LOGS_EXPORT_CONFIRM,
            confirmLabel = SettingsCopy.CONTINUE_EXPORT,
            onConfirm = logsVm::confirmExport,
            onClose = logsVm::closeDialog,
        )
        LogsDialog.REPORT -> LogsModal(
            title = SettingsCopy.DIAG_MODAL_TITLE,
            body = logsState.reportText ?: "",
            confirmLabel = SettingsCopy.DIAG_SAVE_BUTTON,
            onConfirm = logsVm::saveReport,
            onClose = logsVm::closeDialog,
        )
        LogsDialog.NONE -> Unit
    }
}

@Composable
private fun SettingsGroup(title: String, testTag: String, content: @Composable () -> Unit) {
    val colors = WzTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 22.dp)
            .background(colors.surface, RoundedCornerShape(8.dp))
            .border(1.dp, colors.line, RoundedCornerShape(8.dp))
            .padding(16.dp)
            .testTag(testTag),
    ) {
        Text(
            text = title,
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

@Composable
private fun SettingsRow(
    title: String,
    description: String,
    testTag: String? = null,
    control: @Composable () -> Unit,
) {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.ink, style = WzTheme.typography.body)
            Text(
                text = description,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        control()
    }
}

@Composable
private fun AboutLine(label: String, value: String, testTag: String) {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(testTag),
    ) {
        Text(
            text = label,
            color = colors.ink,
            style = WzTheme.typography.body,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(text = value, color = colors.ink3, style = WzTheme.typography.caption)
    }
}

/** 日志/报告查看弹窗（滚动 + 确认按钮可选；导出确认走同构弹窗——PRD「导出前提示」）。 */
@Composable
private fun LogsModal(
    title: String,
    body: String,
    confirmLabel: String?,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = WzTheme.colors
    WzModal(
        title = title,
        onDismiss = onClose,
        width = 720.dp,
        testTag = "logs-modal",
    ) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .background(colors.bg, RoundedCornerShape(7.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(7.dp))
                    .padding(10.dp)
                    .testTag("logs-modal-body"),
            ) {
                Text(
                    text = body,
                    color = colors.ink2,
                    style = WzTheme.typography.caption,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // （WzModal 自带「关闭」按钮：tag = logs-modal-close，此处只放可选确认按钮）
                if (confirmLabel != null) {
                    WzButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        testTag = "logs-modal-confirm",
                    )
                    Box(modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

private fun pnlLabel(scheme: PnlColorScheme): String = SettingsCopy.pnlLabel(scheme.storageValue)

/** 设置页「保存」按钮文案（公共按钮目录，M12 T12.4）。 */
private val settingsSaveLabel: String get() = commonStrings.save
