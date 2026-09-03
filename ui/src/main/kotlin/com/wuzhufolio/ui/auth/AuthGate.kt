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
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.shell.MainShell
import com.wuzhufolio.ui.shell.ShellPage
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
    usernameEnumEnabled: Boolean,
    /** M1：钥匙串降级等启动安全说明（非空弹一次）。 */
    startupNotice: String? = null,
) {
    val vm = remember { AuthGateViewModel(authService).also { it.start() } }
    DisposableEffect(vm) {
        onDispose { vm.dispose() }
    }
    val state by vm.state.collectAsState()
    val initialShellPage by vm.shellToPage.collectAsState()
    var showStartupNotice by remember { mutableStateOf(startupNotice != null) }

    WuzhuTheme(themeMode = themeMode, pnlScheme = pnlScheme) {
        val colors = WzTheme.colors
        Box(modifier = Modifier.fillMaxSize().background(colors.bg).testTag("auth-gate")) {
            when (state.stage) {
                GateStage.RESTORING -> GateCard(width = 400) {
                    Text(
                        text = "加载会话…",
                        color = colors.ink2,
                        style = WzTheme.typography.body,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
                GateStage.LOGIN -> LoginPage(
                    accounts = state.accounts,
                    usernameEnumEnabled = usernameEnumEnabled,
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
                    if (session != null) {
                        val shellViewModel = remember(session.account.id) {
                            ShellViewModel(themeMode, pnlScheme, initialPage = initialShellPage)
                        }
                        MainShell(
                            viewModel = shellViewModel,
                            accountArea = {
                                AccountChip(
                                    username = session.account.username,
                                    onClick = vm::openAccountMenu,
                                )
                            },
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
                InPlaceModal(title = "安全提示", onDismiss = { showStartupNotice = false }, testTag = "startup-notice") {
                    Text(text = startupNotice, color = colors.ink2, style = WzTheme.typography.body)
                    com.wuzhufolio.ui.components.WzButton(
                        text = "我知道了",
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
