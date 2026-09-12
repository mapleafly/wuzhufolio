package com.wuzhufolio.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.accounts.AccountService
import com.wuzhufolio.domain.accounts.AccountSummary
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.i18n.authStrings
import com.wuzhufolio.ui.i18n.shellStrings
import com.wuzhufolio.ui.shell.MainShell
import com.wuzhufolio.ui.shell.ShellPage
import com.wuzhufolio.ui.shell.ShellStatus
import com.wuzhufolio.ui.shell.ShellViewModel
import com.wuzhufolio.ui.theme.WuzhuTheme
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 会话门控宿主（M2 T2.5）：启动恢复 → 登录/创建/向导/忘记 → 主壳（含账户菜单/切换/改密/登出）。
 * 主题/盈亏配色来自启动设置；主壳实例随会话变化重建（切账户即重置导航到仪表盘）。
 */
@Composable
fun AuthGate(
    authService: AccountService,
    themeMode: ThemeMode,
    pnlScheme: PnlColorScheme,
    /** M12 T12.4：界面语言（驱动主题整树重建；登录链路与主壳共用一档）。 */
    language: AppLanguage = AppLanguage.ZH,
    /** 登录页用户名枚举开关读取器（M10：登录页每次进入时重读——设置页开关关闭后即时生效）。 */
    usernameEnumEnabled: () -> Boolean,
    /** M1：钥匙串降级等启动安全说明（非空弹一次）。 */
    startupNotice: String? = null,
    /** M10：完整设置页内容（参数 = ShellViewModel，主题/盈亏配色双向同步；null = 占位页）。 */
    settingsPageContent: (@Composable (ShellViewModel) -> Unit)? = null,
    /** M10：主壳偏好持久化钩子（theme / pnl_scheme / ui.language → settings 全局行）。 */
    onShellPreferenceChange: suspend (key: String, value: String) -> Unit = { _, _ -> },
    /** D21：行情页内容（null = 占位页）。 */
    watchPageContent: (@Composable () -> Unit)? = null,
    /** M12：仪表盘（聚合页；参数 = 当前账户名；null = 占位页）。 */
    dashboardPageContent: (@Composable (accountName: String) -> Unit)? = null,
    /** M12：资产列表（聚合页；参数 = 打开币种详情回调；null = 占位页）。 */
    assetsPageContent: (@Composable (onOpenCoin: (String) -> Unit) -> Unit)? = null,
    /** M12：币种资产详情（参数 = cg_id + 返回回调；null = 占位页）。 */
    coinDetailPageContent: (@Composable (cgId: String, onBack: () -> Unit) -> Unit)? = null,
    /** M7：交易管理页内容（null = 占位页）。 */
    transactionsPageContent: (@Composable () -> Unit)? = null,
    /** M8：资金管理页内容（增资/撤资/校准；null = 占位页）。 */
    fundsPageContent: (@Composable () -> Unit)? = null,
    /** M7 补口：顶栏手动同步（null = 不显示按钮）。 */
    onManualSync: (() -> Unit)? = null,
    manualSyncing: Boolean = false,
    manualSyncToast: com.wuzhufolio.ui.components.WzToast? = null,
    onManualSyncToastDismiss: () -> Unit = {},
    /** M12 补口：顶栏手动刷新行情（null = 不显示按钮）。 */
    onRefreshQuotes: (() -> Unit)? = null,
    refreshQuotesBusy: Boolean = false,
    refreshQuotesToast: com.wuzhufolio.ui.components.WzToast? = null,
    onRefreshQuotesToastDismiss: () -> Unit = {},
    /** M11 T11.3：状态栏代理指示（直连 / 系统代理；PRD 4.2 验收 3）。 */
    proxyStatus: com.wuzhufolio.domain.proxy.ProxyStatus =
        com.wuzhufolio.domain.proxy.ProxyStatus.DEFAULT,
    /** M12：状态栏数据源（同步状态/数据源/额度/备份提醒/断链）。 */
    shellStatus: ShellStatus = ShellStatus(),
) {
    val vm = remember { AuthGateViewModel(authService).also { it.start() } }
    DisposableEffect(vm) {
        onDispose { vm.dispose() }
    }
    val state by vm.state.collectAsState()
    val initialShellPage by vm.shellToPage.collectAsState()
    var showStartupNotice by remember { mutableStateOf(startupNotice != null) }

    // M12 修复轮：主壳 VM 提到门控层持有——语言/主题的运行期真源在 VM，门控层需跟随（否则登出回登录页
    // 会退回启动期语言）。会话变化（登录/登出/切换账户）时按账户 id 重建。
    val sessionKey = state.session?.account?.id
    val shellViewModel = remember(sessionKey) {
        state.session?.let {
            ShellViewModel(
                initialTheme = themeMode,
                initialPnlScheme = pnlScheme,
                initialPage = initialShellPage,
                initialLanguage = language,
                onPreferenceChange = onShellPreferenceChange,
            )
        }
    }
    var activeLanguage by remember(language) { mutableStateOf(language) }
    LaunchedEffect(shellViewModel) {
        shellViewModel?.language?.collect { activeLanguage = it }
    }

    WuzhuTheme(themeMode = themeMode, pnlScheme = pnlScheme, language = activeLanguage) {
        val colors = WzTheme.colors
        Box(modifier = Modifier.fillMaxSize().background(colors.bg).testTag("auth-gate")) {
            when (state.stage) {
                GateStage.RESTORING -> GateCard(width = 400) {
                    Text(
                        text = authStrings.loadingSession,
                        color = colors.ink2,
                        style = WzTheme.typography.body,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
                GateStage.LOGIN -> LoginPage(
                    accounts = state.accounts,
                    // M10：登录页进入时重读设置（枚举开关关闭后无需重启即生效）
                    usernameEnumEnabled = remember(state.stage) { usernameEnumEnabled() },
                    busyText = state.busyText,
                    formError = state.formError,
                    onLogin = vm::submitLogin,
                    onForgot = vm::goForgot,
                    onCreate = vm::goCreate,
                    onTyping = vm::clearFormError,
                )
                GateStage.CREATE -> CreatePage(
                    busyText = state.busyText,
                    formError = state.formError,
                    onBack = vm::goLogin,
                    onTyping = vm::clearFormError,
                    openRiskConfirm = vm::openRiskConfirm,
                )
                GateStage.WIZARD -> WizardPage(
                    username = state.session?.account?.username ?: "",
                    onPick = { kind ->
                        vm.wizardPick(kind) { picked ->
                            when (picked) {
                                WizardKind.MANUAL, WizardKind.CSV -> ShellPage.TRANSACTIONS
                                WizardKind.API, WizardKind.RESTORE -> ShellPage.SETTINGS
                            }
                        }
                    },
                    onLater = vm::wizardLater,
                )
                GateStage.FORGOT -> ForgotPage(onBack = vm::goLogin)
                GateStage.SHELL -> {
                    val session = state.session
                    if (session != null && shellViewModel != null) {
                        MainShell(
                            viewModel = shellViewModel,
                            accountName = session.account.username,
                            language = language,
                            accountArea = {
                                AccountChip(
                                    username = session.account.username,
                                    onClick = vm::openAccountMenu,
                                )
                            },
                            settingsPageContent = settingsPageContent,
                            watchPageContent = watchPageContent,
                            dashboardPageContent = dashboardPageContent,
                            assetsPageContent = assetsPageContent,
                            coinDetailPageContent = coinDetailPageContent,
                            transactionsPageContent = transactionsPageContent,
                            fundsPageContent = fundsPageContent,
                            onManualSync = onManualSync,
                            manualSyncing = manualSyncing,
                            manualSyncToast = manualSyncToast,
                            onManualSyncToastDismiss = onManualSyncToastDismiss,
                            onRefreshQuotes = onRefreshQuotes,
                            refreshQuotesBusy = refreshQuotesBusy,
                            refreshQuotesToast = refreshQuotesToast,
                            onRefreshQuotesToastDismiss = onRefreshQuotesToastDismiss,
                            proxyStatus = proxyStatus,
                            shellStatus = shellStatus,
                        )
                    }
                }
            }

            // 风险确认弹窗（创建页提交后硬门控）
            val pending = state.pendingCreate
            if (pending != null) {
                RiskConfirmModal(
                    busy = state.busyText != null,
                    onConfirm = vm::confirmCreate,
                    onCancel = vm::cancelRisk,
                )
            }
            when (state.dialog) {
                AccountDialog.MENU -> AccountMenuModal(
                    accounts = state.accounts,
                    current = state.session?.account,
                    onSwitchRequest = vm::requestSwitch,
                    onChangePasswordRequest = vm::requestChangePassword,
                    onLogout = { vm.logout(); vm.closeDialog() },
                    onDismiss = vm::closeDialog,
                )
                AccountDialog.SWITCH -> {
                    val target = state.dialogTarget
                    if (target != null) {
                        SwitchAccountModal(
                            target = target,
                            busy = state.dialogBusy,
                            error = state.dialogError,
                            onSubmit = { vm.submitSwitch(target, it) },
                            onDismiss = vm::closeDialog,
                        )
                    }
                }
                AccountDialog.CHANGE_PASSWORD -> ChangePasswordModal(
                    busy = state.dialogBusy,
                    error = state.dialogError,
                    onSubmit = vm::submitChangePassword,
                    onDismiss = vm::closeDialog,
                )
                AccountDialog.NONE -> Unit
            }
            WzToastHost(toast = state.toast, onDismiss = vm::dismissToast)
            if (showStartupNotice && startupNotice != null) {
                InPlaceModal(
                    title = shellStrings.startupNoticeTitle,
                    onDismiss = { showStartupNotice = false },
                    testTag = "startup-notice",
                ) {
                    Text(text = startupNotice, color = colors.ink2, style = WzTheme.typography.body)
                    com.wuzhufolio.ui.components.WzButton(
                        text = shellStrings.startupNoticeOk,
                        onClick = { showStartupNotice = false },
                        modifier = Modifier.padding(top = 16.dp),
                        testTag = "startup-notice-ok",
                    )
                }
            }
        }
    }
}

/** 侧栏底部账户区（原型 acctBtn：头像 + 账户名 + 副行「切换账户 / 登出」）。 */
@Composable
fun AccountChip(username: String, onClick: () -> Unit) {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("acct-chip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(username = username, testTag = "acct-ava")
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(text = username, color = colors.ink, style = WzTheme.typography.bodyStrong)
            Text(text = AuthCopy.ACCOUNT_MENU_SUB, color = colors.ink3, style = WzTheme.typography.caption)
        }
    }
}

/** 占位弹窗（向导选项指向 M6/M7/M9 时的统一占位，禁做假交互）。 */
@Composable
fun PlaceholderNoticeModal(title: String, message: String, onDismiss: () -> Unit, testTag: String) {
    val colors = WzTheme.colors
    InPlaceModal(title = title, onDismiss = onDismiss, width = 420.dp, testTag = testTag) {
        Text(text = message, color = colors.ink2, style = WzTheme.typography.body)
    }
}
