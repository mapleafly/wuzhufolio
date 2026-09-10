package com.wuzhufolio.ui.settings

import com.wuzhufolio.ui.i18n.settingsStrings

/**
 * 设置页文案（M10 · T10.4 · 以 P1 原型 wuzhufolio-light.html 设置页逐字基准 + ia.md §2.12/§2.16；
 * PRD §7.2 模块 6 / §6「日志管理与可追溯性」）。
 *
 * M12 T12.4：正文全部改为**动态取值属性**（zh/en 双档见 `ui/i18n/SettingsStrings.kt`）——
 * 成员名与调用点保持不变，每次读取按当前语言解析（切换语言由 WuzhuTheme 的 key 强制重组，见 I18n 头注）。
 */
object SettingsCopy {

    // ---- 分组标题（原型 settings-group h3 逐字） ----

    val GROUP_GENERAL: String get() = settingsStrings.generalGroup
    val GROUP_NETWORK: String get() = settingsStrings.networkGroup
    val GROUP_MARKET_SYNC: String get() = settingsStrings.marketSyncGroup
    val GROUP_LOGS: String get() = settingsStrings.logsGroup
    val GROUP_FEE: String get() = settingsStrings.feeGroup
    val GROUP_API: String get() = settingsStrings.apiGroup
    val GROUP_DATA: String get() = settingsStrings.dataGroup
    val GROUP_ABOUT: String get() = settingsStrings.aboutGroup

    val PAGE_SUB: String get() = settingsStrings.pageSub

    /** 设置页页头标题。 */
    val PAGE_TITLE: String get() = settingsStrings.pageTitle

    /** 主题分段（明/暗）。 */
    val THEME_LIGHT: String get() = settingsStrings.themeLight
    val THEME_DARK: String get() = settingsStrings.themeDark

    /** 界面语言行（M12 T12.4）。 */
    val LANGUAGE_LABEL: String get() = settingsStrings.languageLabel
    val LANGUAGE_SUB: String get() = settingsStrings.languageSub

    /** 日志导出确认按钮（PRD「导出前提示」）。 */
    val CONTINUE_EXPORT: String get() = settingsStrings.continueExport

    /** API 同步间隔下拉项文案。 */
    fun intervalLabel(minutes: Int): String = settingsStrings.intervalMinutes(minutes)

    /** 盈亏配色方案展示名（入参 = [com.wuzhufolio.domain.settings.PnlColorScheme.storageValue]）。 */
    fun pnlLabel(storageValue: String): String = settingsStrings.pnlSchemeLabel(storageValue)

    // ---- 通用分组行（原型 settingsRow 逐字基准） ----

    val FIAT_LABEL: String get() = settingsStrings.fiatLabel
    val FIAT_SUB: String get() = settingsStrings.fiatSub
    val FIAT_UNSUPPORTED: String get() = settingsStrings.fiatUnsupported

    val THEME_LABEL: String get() = settingsStrings.themeLabel
    val THEME_SUB: String get() = settingsStrings.themeSub

    val PNL_LABEL: String get() = settingsStrings.pnlLabel
    val PNL_SUB: String get() = settingsStrings.pnlSub

    val PRECISION_LABEL: String get() = settingsStrings.precisionLabel
    val PRECISION_SUB: String get() = settingsStrings.precisionSub

    val USERNAME_ENUM_LABEL: String get() = settingsStrings.usernameEnumLabel
    val USERNAME_ENUM_SUB: String get() = settingsStrings.usernameEnumSub

    val CASH_LABEL: String get() = settingsStrings.cashLabel
    val CASH_SUB: String get() = settingsStrings.cashSub
    val CASH_ADD_BUTTON: String get() = settingsStrings.cashAddButton
    val CASH_INPUT_LABEL: String get() = settingsStrings.cashInputLabel
    val CASH_REMOVE: String get() = settingsStrings.cashRemove
    val CASH_DEFAULT_MARK: String get() = settingsStrings.cashDefaultMark

    val THRESHOLD_LABEL: String get() = settingsStrings.thresholdLabel
    val THRESHOLD_SUB: String get() = settingsStrings.thresholdSub
    val THRESHOLD_INPUT_LABEL: String get() = settingsStrings.thresholdInputLabel
    val THRESHOLD_INVALID: String get() = settingsStrings.thresholdInvalid

    // ---- 网络 / 行情与同步 ----

    val PROXY_LABEL: String get() = settingsStrings.proxyLabel
    val PROXY_SUB: String get() = settingsStrings.proxySub

    val SYNC_INTERVAL_LABEL: String get() = settingsStrings.syncIntervalLabel
    val SYNC_INTERVAL_SUB: String get() = settingsStrings.syncIntervalSub

    // ---- 托盘与后台（M11 T11.1/T11.2；PRD 6.1「托盘与后台」+「备份提醒」） ----

    val GROUP_TRAY: String get() = settingsStrings.trayGroup

    val MINIMIZE_LABEL: String get() = settingsStrings.minimizeLabel
    val MINIMIZE_SUB: String get() = settingsStrings.minimizeSub
    val MINIMIZE_UNAVAILABLE: String get() = settingsStrings.minimizeUnavailable

    val AUTOSTART_LABEL: String get() = settingsStrings.autostartLabel
    val AUTOSTART_SUB: String get() = settingsStrings.autostartSub
    val AUTOSTART_UNAVAILABLE: String get() = settingsStrings.autostartUnavailable

    val SYNC_NOTIFY_LABEL: String get() = settingsStrings.syncNotifyLabel
    val SYNC_NOTIFY_SUB: String get() = settingsStrings.syncNotifySub

    val BACKUP_REMINDER_LABEL: String get() = settingsStrings.backupReminderLabel
    val BACKUP_REMINDER_SUB: String get() = settingsStrings.backupReminderSub

    // ---- 日志与诊断（interaction §2.6 逐字口径） ----

    val LOGS_VIEW_LABEL: String get() = settingsStrings.logsViewLabel
    val LOGS_VIEW_SUB: String get() = settingsStrings.logsViewSub
    val LOGS_VIEW_BUTTON: String get() = settingsStrings.logsViewButton
    val LOGS_EXPORT_LABEL: String get() = settingsStrings.logsExportLabel
    val LOGS_EXPORT_SUB: String get() = settingsStrings.logsExportSub
    val LOGS_EXPORT_BUTTON: String get() = settingsStrings.logsExportButton
    val LOGS_EXPORT_CONFIRM: String get() = settingsStrings.logsExportConfirm
    val LOGS_EXPORTED_TOAST: String get() = settingsStrings.logsExportedToast
    val LOGS_EXPORT_FAILED_TOAST: String get() = settingsStrings.logsExportFailedToast
    val LOGS_MODAL_TITLE: String get() = settingsStrings.logsModalTitle
    val LOGS_EMPTY: String get() = settingsStrings.logsEmpty

    val DIAG_LABEL: String get() = settingsStrings.diagLabel
    val DIAG_SUB: String get() = settingsStrings.diagSub
    val DIAG_BUTTON: String get() = settingsStrings.diagButton
    val DIAG_MODAL_TITLE: String get() = settingsStrings.diagModalTitle
    val DIAG_SAVE_BUTTON: String get() = settingsStrings.diagSaveButton
    val DIAG_SAVED_TOAST: String get() = settingsStrings.diagSavedToast
    val DIAG_FAILED_TOAST: String get() = settingsStrings.diagFailedToast

    // ---- 关于（PRD §7.2-6.4） ----

    val ABOUT_VERSION_LABEL: String get() = settingsStrings.aboutVersionLabel
    val ABOUT_DEV_LABEL: String get() = settingsStrings.aboutDevLabel
    val ABOUT_DEV_VALUE: String get() = settingsStrings.aboutDevValue
    val ABOUT_PRIVACY_LABEL: String get() = settingsStrings.aboutPrivacyLabel
    val ABOUT_PRIVACY_VALUE: String get() = settingsStrings.aboutPrivacyValue
    val ABOUT_SOURCE_LABEL: String get() = settingsStrings.aboutSourceLabel
    val ABOUT_SOURCE_VALUE: String get() = settingsStrings.aboutSourceValue
    val ABOUT_NOTELEMETRY_LABEL: String get() = settingsStrings.aboutNotelemetryLabel
    val ABOUT_NOTELEMETRY_VALUE: String get() = settingsStrings.aboutNotelemetryValue
}
