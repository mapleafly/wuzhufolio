package com.wuzhufolio.ui.i18n

/**
 * 设置模块文案（设置页静态文案 + 三个设置 VM 的运行期提示；M12 T12.4 zh/en 双档）。
 *
 * 来源与口径：
 * - 静态文案 = M10/M11 既有 `ui/settings/SettingsCopy.kt` 的中文字面量（原型 wuzhufolio-light.html
 *   设置页逐字基准 + ia.md §2.12/§2.16；PRD §7.2 模块 6 / §6「日志管理与可追溯性」）；
 * - 运行期提示 = [com.wuzhufolio.ui.settings.GeneralSettingsViewModel] /
 *   [com.wuzhufolio.ui.settings.DesktopSettingsViewModel] /
 *   [com.wuzhufolio.ui.settings.LogsDiagnosticsViewModel] 的 toast/错误上浮文案
 *   （带参数的写成接口函数，不在调用点拼装中文）；
 * - **zh 档逐字等于原中文字面量**（既有 UI 测试按字面匹配，不得改写）；
 * - 文件对话框标题（查看/导出日志、保存诊断报告）同样属于用户可见文案，一并入表。
 *
 * 命名约定：`SettingsCopy` 的 `SCREAMING_CASE` 常量 → 本表小驼峰属性；文案对象保持原成员名不变，
 * 仅把 `const val` 改为动态取值属性（调用点零改动，见 [CommonStrings] 头注的目录约定）。
 */
interface SettingsStrings {

    // ---- 设置页分组标题 ----

    val generalGroup: String
    val networkGroup: String
    val marketSyncGroup: String
    val logsGroup: String
    val feeGroup: String
    val apiGroup: String
    val dataGroup: String
    val aboutGroup: String
    val trayGroup: String

    val pageSub: String

    // ---- 通用分组 ----

    val fiatLabel: String
    val fiatSub: String
    val fiatUnsupported: String

    val themeLabel: String
    val themeSub: String

    val pnlLabel: String
    val pnlSub: String

    val precisionLabel: String
    val precisionSub: String

    val usernameEnumLabel: String
    val usernameEnumSub: String

    val cashLabel: String
    val cashSub: String
    val cashAddButton: String
    val cashInputLabel: String
    val cashRemove: String
    val cashDefaultMark: String

    val thresholdLabel: String
    val thresholdSub: String
    val thresholdInputLabel: String
    val thresholdInvalid: String

    // ---- 网络 / 行情与同步 ----

    val proxyLabel: String
    val proxySub: String

    val syncIntervalLabel: String
    val syncIntervalSub: String

    // ---- 托盘与后台 ----

    val minimizeLabel: String
    val minimizeSub: String
    val minimizeUnavailable: String

    val autostartLabel: String
    val autostartSub: String
    val autostartUnavailable: String

    val syncNotifyLabel: String
    val syncNotifySub: String

    val backupReminderLabel: String
    val backupReminderSub: String

    // ---- 日志与诊断 ----

    val logsViewLabel: String
    val logsViewSub: String
    val logsViewButton: String

    val logsExportLabel: String
    val logsExportSub: String
    val logsExportButton: String
    val logsExportConfirm: String
    val logsExportedToast: String
    val logsExportFailedToast: String

    val logsModalTitle: String
    val logsEmpty: String

    val diagLabel: String
    val diagSub: String
    val diagButton: String
    val diagModalTitle: String
    val diagSaveButton: String
    val diagSavedToast: String
    val diagFailedToast: String

    // ---- 关于 ----

    val aboutVersionLabel: String
    val aboutDevLabel: String
    val aboutDevValue: String

    val aboutPrivacyLabel: String
    val aboutPrivacyValue: String

    val aboutSourceLabel: String
    val aboutSourceValue: String

    val aboutNotelemetryLabel: String
    val aboutNotelemetryValue: String

    // ---- 通用设置写入提示（GeneralSettingsViewModel） ----

    val loadFailed: String
    val saveFailed: String

    fun baseFiatSwitched(code: String): String
    val precisionSaved: String
    fun usernameEnumSet(on: Boolean): String
    fun cashCoinAdded(id: String): String
    fun cashCoinRemoved(id: String): String
    fun thresholdSaved(value: String): String
    fun proxySet(on: Boolean): String

    // ---- 托盘与后台写入提示（DesktopSettingsViewModel） ----

    val autostartRegisterFailed: String

    fun minimizeOnCloseSet(on: Boolean): String
    fun syncNotificationSet(on: Boolean): String
    fun backupReminderSet(on: Boolean): String
    fun autostartSet(on: Boolean): String

    // ---- 日志与诊断对话框/提示（LogsDiagnosticsViewModel） ----

    val logsExportDialogTitle: String
    val diagnosticsSaveDialogTitle: String

    /** 导出日志 toast 的行数尾注（`（N 行）`；前缀见 [logsExportedToast]）。 */
    fun logLineCount(lines: Int): String

    // ---- 设置页页头与「通用」组剩余文案（M12 T12.4：原先散落在 SettingsPage 内联的少量文案归位） ----

    val pageTitle: String
    val themeLight: String
    val themeDark: String
    val languageLabel: String
    val languageSub: String
    val continueExport: String

    /** API 同步间隔下拉项（如「30 分钟」/「30 min」）。 */
    fun intervalMinutes(minutes: Int): String

    /** 盈亏配色方案展示名（PRD §7.2 模块 6.1 三档）。 */
    fun pnlSchemeLabel(storageValue: String): String
}

object SettingsStringsZh : SettingsStrings {
    override val generalGroup = "通用"
    override val networkGroup = "网络"
    override val marketSyncGroup = "行情与同步"
    override val logsGroup = "日志与诊断"
    override val feeGroup = "手续费"
    override val apiGroup = "API 管理"
    override val dataGroup = "数据管理"
    override val aboutGroup = "关于"
    override val trayGroup = "托盘与后台"

    override val pageSub = "账户级设置与全局设置 · 切换即时生效"

    override val fiatLabel = "基础法币"
    override val fiatSub = "计价与折算基准（默认 USD）"
    override val fiatUnsupported = "不支持的基础法币"

    override val themeLabel = "主题（明 / 暗）"
    override val themeSub = "与顶栏 ☾ 快捷切换同步（PRD 6.1）"

    override val pnlLabel = "盈亏颜色方案"
    override val pnlSub = "数值强制显示 ± 符号，颜色仅辅助"

    override val precisionLabel = "默认精度"
    override val precisionSub = "金额/价格 8 位、市值/盈亏 2 位、百分比 2 位"

    override val usernameEnumLabel = "登录页用户名枚举"
    override val usernameEnumSub = "关闭后用户名改为纯手动输入"

    override val cashLabel = "稳定币白名单"
    override val cashSub = "现金类币种判定（默认 USDT/USDC/DAI/TUSD，可扩展）"
    override val cashAddButton = "添加"
    override val cashInputLabel = "币种标识（CoinGecko id，如 usd-coin）"
    override val cashRemove = "移除"
    override val cashDefaultMark = "默认"

    override val thresholdLabel = "小额币种阈值"
    override val thresholdSub = "低于阈值的币种合计归入「其他」（按基础法币计；0 = 不启用）。" +
        "自由数值，按自身规模设定（参考：总资产净值的 1% 左右，如总额 100 可设 1、总额 200 万可设 2 万）"
    override val thresholdInputLabel = "阈值（基础法币，0 = 不启用）"
    override val thresholdInvalid = "请输入不小于 0 的数值（0 = 不启用）"

    override val proxyLabel = "系统代理"
    override val proxySub = "自动检测并使用操作系统代理；关闭则全部直连（状态栏显示当前连接方式）"

    override val syncIntervalLabel = "API 同步间隔（交易数据）"
    override val syncIntervalSub = "自动增量同步的间隔（默认 30 分钟）"

    override val minimizeLabel = "关闭窗口最小化到托盘"
    override val minimizeSub = "关闭后应用继续在托盘驻留并按时同步；关闭此开关则关窗即退出"
    override val minimizeUnavailable = "当前系统环境不支持系统托盘——关窗将直接退出"

    override val autostartLabel = "开机自动启动"
    override val autostartSub = "随系统启动并驻留托盘（默认关闭）"
    override val autostartUnavailable = "当前运行方式无法注册开机自启"

    override val syncNotifyLabel = "同步完成/失败通知"
    override val syncNotifySub = "后台同步结束时的桌面通知（失败必提示）"

    override val backupReminderLabel = "备份提醒"
    override val backupReminderSub = "距上次备份超过 30 天时提醒导出 .cpro 备份"

    override val logsViewLabel = "查看日志"
    override val logsViewSub = "本地关键操作与错误日志（账户名与金额默认脱敏）"
    override val logsViewButton = "查看"

    override val logsExportLabel = "导出日志"
    override val logsExportSub = "导出前提示检查敏感信息；密钥明文/哈希与完整响应体不落日志"
    override val logsExportButton = "导出"
    override val logsExportConfirm = "导出前请检查是否包含敏感信息（日志已自动脱敏）。继续导出？"
    override val logsExportedToast = "日志已导出："
    override val logsExportFailedToast = "日志导出失败："

    override val logsModalTitle = "日志（已脱敏）"
    override val logsEmpty = "暂无日志"

    override val diagLabel = "生成诊断报告"
    override val diagSub = "应用/OS 版本 · schema 版本 · 最近日志片段（已脱敏）· 调用计数"
    override val diagButton = "生成"
    override val diagModalTitle = "诊断报告"
    override val diagSaveButton = "保存到文件"
    override val diagSavedToast = "诊断报告已保存："
    override val diagFailedToast = "诊断报告生成失败："

    override val aboutVersionLabel = "版本"
    override val aboutDevLabel = "开发者信息"
    override val aboutDevValue = "WuZhuFolio 开源项目（社区维护 · AGPL-3.0）"

    override val aboutPrivacyLabel = "隐私政策"
    override val aboutPrivacyValue = "数据完全本地化：交易/密钥/资金流水只存本机，禁止云端上传（PRD §1.1）"

    override val aboutSourceLabel = "行情数据源说明"
    override val aboutSourceValue =
        "主源 CoinGecko（默认无 Key 公共 API 开箱即用，可配置个人 Key 获专属额度）；" +
            "兜底 CoinMarketCap（免费档，需配置个人 Key）；应用不内置任何 API Key"

    override val aboutNotelemetryLabel = "无遥测声明"
    override val aboutNotelemetryValue = "本应用不收集、不上传任何使用数据"

    override val loadFailed = "读取设置失败"
    override val saveFailed = "保存失败"

    override fun baseFiatSwitched(code: String) = "基础法币已切换为 " + code
    override val precisionSaved = "默认精度已保存"
    override fun usernameEnumSet(on: Boolean) =
        if (on) "登录页用户名枚举已开启" else "登录页用户名枚举已关闭"
    override fun cashCoinAdded(id: String) = "已加入稳定币白名单：" + id
    override fun cashCoinRemoved(id: String) = "已移除：" + id
    override fun thresholdSaved(value: String) = "小额阈值已保存（" + value + "）"
    override fun proxySet(on: Boolean) =
        if (on) "系统代理已开启（自动检测）" else "系统代理已关闭（直连）"

    override val autostartRegisterFailed = "平台注册失败"

    override fun minimizeOnCloseSet(on: Boolean) =
        if (on) "关闭窗口将最小化到托盘" else "关闭窗口将直接退出"
    override fun syncNotificationSet(on: Boolean) =
        if (on) "同步通知已开启" else "同步通知已关闭"
    override fun backupReminderSet(on: Boolean) =
        if (on) "备份提醒已开启" else "备份提醒已关闭"
    override fun autostartSet(on: Boolean) =
        if (on) "已设置开机自启" else "已取消开机自启"

    override val logsExportDialogTitle = "导出日志（已脱敏）"
    override val diagnosticsSaveDialogTitle = "保存诊断报告"

    override fun logLineCount(lines: Int) = "（" + lines + " 行）"

    override val pageTitle = "设置"
    override val themeLight = "明亮"
    override val themeDark = "黑暗"
    override val languageLabel = "界面语言"
    override val languageSub = "界面文案与日期时间格式随语言切换（即时生效）"
    override val continueExport = "继续导出"
    override fun intervalMinutes(minutes: Int) = minutes.toString() + " 分钟"
    override fun pnlSchemeLabel(storageValue: String) = when (storageValue) {
        "red_up" -> "红涨绿跌"
        "colorblind" -> "色盲友好（蓝涨橙跌）"
        else -> "绿涨红跌（默认）"
    }
}

object SettingsStringsEn : SettingsStrings {
    override val generalGroup = "General"
    override val networkGroup = "Network"
    override val marketSyncGroup = "Markets & Sync"
    override val logsGroup = "Logs & Diagnostics"
    override val feeGroup = "Fees"
    override val apiGroup = "API Management"
    override val dataGroup = "Data Management"
    override val aboutGroup = "About"
    override val trayGroup = "Tray & Background"

    override val pageSub = "Account-level and global settings · changes take effect immediately"

    override val fiatLabel = "Base fiat currency"
    override val fiatSub = "Basis for pricing and conversion (default: USD)"
    override val fiatUnsupported = "Unsupported base fiat currency"

    override val themeLabel = "Theme (light / dark)"
    override val themeSub = "Synced with the ☾ toggle in the top bar (PRD 6.1)"

    override val pnlLabel = "P&L colour scheme"
    override val pnlSub = "Values always show a ± sign; colour is supplementary"

    override val precisionLabel = "Default precision"
    override val precisionSub =
        "Amounts/prices 8 decimals, market value/P&L 2 decimals, percentages 2 decimals"

    override val usernameEnumLabel = "Username enumeration on login"
    override val usernameEnumSub = "When off, the username must be typed in manually"

    override val cashLabel = "Stablecoin whitelist"
    override val cashSub = "Which coins count as cash (default USDT/USDC/DAI/TUSD, extensible)"
    override val cashAddButton = "Add"
    override val cashInputLabel = "Coin identifier (CoinGecko id, e.g. usd-coin)"
    override val cashRemove = "Remove"
    override val cashDefaultMark = "Default"

    override val thresholdLabel = "Small-balance threshold"
    override val thresholdSub =
        "Holdings below the threshold are grouped into “Other” (in the base fiat currency; 0 = disabled). " +
            "Free-form value — set it to suit your own scale (rule of thumb: about 1% of net assets, " +
            "e.g. 1 for a total of 100, 20,000 for a total of 2,000,000)"
    override val thresholdInputLabel = "Threshold (base fiat currency, 0 = disabled)"
    override val thresholdInvalid = "Enter a value of 0 or greater (0 = disabled)"

    override val proxyLabel = "System proxy"
    override val proxySub =
        "Detect and use the operating system proxy automatically; when off, all requests connect " +
            "directly (the status bar shows the current mode)"

    override val syncIntervalLabel = "API sync interval (transaction data)"
    override val syncIntervalSub = "Interval between automatic incremental syncs (default 30 minutes)"

    override val minimizeLabel = "Minimise to tray on close"
    override val minimizeSub =
        "When on, the app stays in the tray and keeps syncing on schedule; when off, closing the " +
            "window quits the app"
    override val minimizeUnavailable =
        "The system tray is not supported in this environment — closing the window will quit the app"

    override val autostartLabel = "Launch at login"
    override val autostartSub = "Start with the system and stay in the tray (off by default)"
    override val autostartUnavailable = "Launch at login cannot be registered in the current run mode"

    override val syncNotifyLabel = "Sync success/failure notifications"
    override val syncNotifySub =
        "Desktop notification when a background sync finishes (failures always notify)"

    override val backupReminderLabel = "Backup reminder"
    override val backupReminderSub =
        "Remind me to export a .cpro backup when the last backup is over 30 days old"

    override val logsViewLabel = "View logs"
    override val logsViewSub =
        "Local log of key operations and errors (account names and amounts are redacted by default)"
    override val logsViewButton = "View"

    override val logsExportLabel = "Export logs"
    override val logsExportSub =
        "Prompts you to check for sensitive information before exporting; plaintext/hashed keys and " +
            "full response bodies are never logged"
    override val logsExportButton = "Export"
    override val logsExportConfirm =
        "Check the export for sensitive information first (logs are redacted automatically). Continue?"
    override val logsExportedToast = "Logs exported: "
    override val logsExportFailedToast = "Log export failed: "

    override val logsModalTitle = "Logs (redacted)"
    override val logsEmpty = "No logs"

    override val diagLabel = "Generate diagnostics report"
    override val diagSub = "App/OS version · schema version · recent log excerpt (redacted) · call counts"
    override val diagButton = "Generate"
    override val diagModalTitle = "Diagnostics report"
    override val diagSaveButton = "Save to file"
    override val diagSavedToast = "Diagnostics report saved: "
    override val diagFailedToast = "Diagnostics report failed: "

    override val aboutVersionLabel = "Version"
    override val aboutDevLabel = "Developer"
    override val aboutDevValue = "WuZhuFolio open-source project (community-maintained · AGPL-3.0)"

    override val aboutPrivacyLabel = "Privacy policy"
    override val aboutPrivacyValue =
        "Fully local data: transactions, keys and fund flows stay on this device only; " +
            "no cloud upload (PRD §1.1)"

    override val aboutSourceLabel = "Market data sources"
    override val aboutSourceValue =
        "Primary: CoinGecko (keyless public API works out of the box; a personal key gives you a " +
            "dedicated quota); fallback: CoinMarketCap (free tier, requires a personal key); " +
            "the app bundles no API keys"

    override val aboutNotelemetryLabel = "No telemetry"
    override val aboutNotelemetryValue = "This app collects and uploads no usage data"

    override val loadFailed = "Failed to load settings"
    override val saveFailed = "Save failed"

    override fun baseFiatSwitched(code: String) = "Base fiat currency switched to " + code
    override val precisionSaved = "Default precision saved"
    override fun usernameEnumSet(on: Boolean) =
        if (on) "Username enumeration on the login page enabled"
        else "Username enumeration on the login page disabled"
    override fun cashCoinAdded(id: String) = "Added to the stablecoin whitelist: " + id
    override fun cashCoinRemoved(id: String) = "Removed: " + id
    override fun thresholdSaved(value: String) = "Small-balance threshold saved (" + value + ")"
    override fun proxySet(on: Boolean) =
        if (on) "System proxy enabled (auto-detect)" else "System proxy disabled (direct)"

    override val autostartRegisterFailed = "Platform registration failed"

    override fun minimizeOnCloseSet(on: Boolean) =
        if (on) "Closing the window will minimise to tray" else "Closing the window will quit the app"
    override fun syncNotificationSet(on: Boolean) =
        if (on) "Sync notifications on" else "Sync notifications off"
    override fun backupReminderSet(on: Boolean) =
        if (on) "Backup reminder on" else "Backup reminder off"
    override fun autostartSet(on: Boolean) =
        if (on) "Launch at login enabled" else "Launch at login disabled"

    override val logsExportDialogTitle = "Export logs (redacted)"
    override val diagnosticsSaveDialogTitle = "Save diagnostics report"

    override fun logLineCount(lines: Int) = " (" + lines + " lines)"
    override val pageTitle = "Settings"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val languageLabel = "Language"
    override val languageSub = "UI copy and date/time formatting follow this setting (applied immediately)"
    override val continueExport = "Export anyway"
    override fun intervalMinutes(minutes: Int) = minutes.toString() + " min"
    override fun pnlSchemeLabel(storageValue: String) = when (storageValue) {
        "red_up" -> "Red up / green down"
        "colorblind" -> "Colour-blind friendly (blue up / orange down)"
        else -> "Green up / red down (default)"
    }

}

val settingsStrings: SettingsStrings get() = if (I18n.isZh) SettingsStringsZh else SettingsStringsEn
