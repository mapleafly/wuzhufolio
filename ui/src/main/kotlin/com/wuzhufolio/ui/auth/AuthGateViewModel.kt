package com.wuzhufolio.ui.auth

import com.wuzhufolio.domain.accounts.AccountPolicy
import com.wuzhufolio.domain.accounts.AccountService
import com.wuzhufolio.domain.accounts.AccountSummary
import com.wuzhufolio.domain.accounts.ChangePwdReq
import com.wuzhufolio.domain.accounts.CreateAccountReq
import com.wuzhufolio.domain.accounts.InvalidCredentialsException
import com.wuzhufolio.domain.accounts.LoginReq
import com.wuzhufolio.domain.accounts.OldPasswordMismatchException
import com.wuzhufolio.domain.accounts.PasswordMismatchException
import com.wuzhufolio.domain.accounts.Session
import com.wuzhufolio.domain.accounts.SwitchReq
import com.wuzhufolio.domain.accounts.UsernameTakenException
import com.wuzhufolio.domain.security.Zeroization
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import com.wuzhufolio.ui.shell.ShellPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 登录链路 gate 阶段（T2.5 状态机；原型 showGate/enterShell 对应物）。 */
enum class GateStage { RESTORING, LOGIN, CREATE, WIZARD, FORGOT, SHELL }

/** 账户菜单弹窗（原型侧栏下拉 → M2 居中模态，功能等价；偏差记模块文档）。 */
enum class AccountDialog { NONE, MENU, SWITCH, CHANGE_PASSWORD }

/** 向导四方式（原型 wizardPick）。 */
enum class WizardKind { MANUAL, CSV, API, RESTORE }

/** 风险确认门控载荷（字符串驻内存为 UI 瞬态；confirm 后立即转 CharArray 并于服务层清零）。 */
data class PendingCreate(val username: String, val password: String, val rememberMe: Boolean)

data class AuthUiState(
    val stage: GateStage = GateStage.RESTORING,
    val accounts: List<AccountSummary> = emptyList(),
    val session: Session? = null,
    val busyText: String? = null,
    val formError: String? = null,
    val dialog: AccountDialog = AccountDialog.NONE,
    val dialogTarget: AccountSummary? = null,
    val dialogBusy: Boolean = false,
    val dialogError: String? = null,
    val toast: WzToast? = null,
    val pendingCreate: PendingCreate? = null,
)

/**
 * 会话门控 VM（M2 T2.1–T2.5）：启动恢复（记住我）→ 登录/创建/向导/主壳；登出/切换/改密。
 * 字段级校验留在页面本地；本 VM 负责用例调用、统一异常文案（A1/A2/A3）与阶段迁移。
 * 动作面广（每页/每对话框一组动作）是门控控制器的固有形态，故豁免 TooManyFunctions；
 * 异常统一映射为面向用户的固定文案（A1/A2/A3/通用），原始异常不入 UI（SwallowedException 豁免点）。
 */
@Suppress("TooManyFunctions", "SwallowedException")
class AuthGateViewModel(private val service: AccountService) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _shellToPage = MutableStateFlow(ShellPage.DASHBOARD)
    val shellToPage: StateFlow<ShellPage> = _shellToPage.asStateFlow()

    /** 启动：记住我恢复 → 主壳；否则按账户存在性路由（无账户=创建引导）。 */
    fun start() {
        scope.launch {
            try {
                val restored = service.restoreSession()
                if (restored != null) {
                    enterShell(restored, service.listAccounts())
                } else {
                    val accounts = service.listAccounts()
                    _state.value = AuthUiState(
                        stage = if (accounts.isEmpty()) GateStage.CREATE else GateStage.LOGIN,
                        accounts = accounts,
                    )
                }
            } catch (t: Throwable) {
                _state.value = AuthUiState(stage = GateStage.LOGIN, formError = AuthCopy.ERR_GENERIC)
            }
        }
    }

    fun clearFormError() {
        _state.value = _state.value.copy(formError = null)
    }

    fun goLogin() {
        _state.value = _state.value.copy(stage = GateStage.LOGIN, formError = null)
    }

    fun goCreate() {
        _state.value = _state.value.copy(stage = GateStage.CREATE, formError = null)
    }

    fun goForgot() {
        _state.value = _state.value.copy(stage = GateStage.FORGOT, formError = null)
    }

    fun submitLogin(username: String, password: String, rememberMe: Boolean) {
        val pw = password.toCharArray()
        scope.launch {
            _state.value = _state.value.copy(busyText = AuthCopy.LOGIN_LOADING, formError = null)
            try {
                val session = service.login(LoginReq(username, pw, rememberMe))
                showToast(WzToastKind.Success, String.format(AuthCopy.LOGIN_TOAST_OK, session.account.username))
                _shellToPage.value = ShellPage.DASHBOARD
                val accounts = service.listAccounts()
                _state.value = _state.value.copy(
                    stage = GateStage.SHELL,
                    accounts = accounts,
                    session = session,
                    busyText = null,
                )
            } catch (e: InvalidCredentialsException) {
                _state.value = _state.value.copy(busyText = null, formError = AuthCopy.ERR_A1_LOGIN)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(busyText = null, formError = AuthCopy.ERR_GENERIC)
            } finally {
                Zeroization.wipe(pw)
            }
        }
    }

    /** 创建页校验通过 → 打开风险确认（PRD 1.1-6：勾选前不得完成创建）。 */
    fun openRiskConfirm(username: String, password: String, rememberMe: Boolean) {
        _state.value = _state.value.copy(pendingCreate = PendingCreate(username, password, rememberMe))
    }

    fun cancelRisk() {
        _state.value = _state.value.copy(pendingCreate = null)
    }

    /** 勾选「我已了解上述风险」→ 确认创建。 */
    fun confirmCreate() {
        val pending = _state.value.pendingCreate ?: return
        _state.value = _state.value.copy(pendingCreate = null)
        submitCreate(pending.username, pending.password, pending.rememberMe)
    }

    fun submitCreate(username: String, password: String, rememberMe: Boolean) {
        if (!AccountPolicy.meetsMinimum(password)) return
        val pw = password.toCharArray()
        scope.launch {
            _state.value = _state.value.copy(busyText = AuthCopy.LOGIN_LOADING, formError = null)
            try {
                val session = service.createAccount(CreateAccountReq(username, pw, rememberMe))
                showToast(WzToastKind.Success, String.format(AuthCopy.CREATE_TOAST_OK, session.account.username))
                val accounts = service.listAccounts()
                _shellToPage.value = ShellPage.DASHBOARD
                _state.value = _state.value.copy(
                    stage = GateStage.WIZARD,
                    accounts = accounts,
                    session = session,
                    busyText = null,
                )
            } catch (e: UsernameTakenException) {
                _state.value = _state.value.copy(busyText = null, formError = AuthCopy.CREATE_ERROR_USERNAME_TAKEN)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(busyText = null, formError = AuthCopy.ERR_GENERIC)
            } finally {
                Zeroization.wipe(pw)
            }
        }
    }

    /** 向导选择：落到主壳对应页 + 模块预告 toast（M6/M7/M9 落地后替换为真实跳转/弹窗）。 */
    fun wizardPick(kind: WizardKind, pageFor: (WizardKind) -> ShellPage) {
        val session = _state.value.session ?: return
        val module = when (kind) {
            WizardKind.MANUAL -> AuthCopy.MODULE_MANUAL
            WizardKind.CSV -> AuthCopy.MODULE_CSV
            WizardKind.API -> AuthCopy.MODULE_API
            WizardKind.RESTORE -> AuthCopy.MODULE_RESTORE
        }
        _shellToPage.value = pageFor(kind)
        scope.launch {
            showToast(WzToastKind.Failure, String.format(AuthCopy.WIZARD_PICK_TOAST, module))
            enterShellQuiet(session, service.listAccounts())
        }
    }

    fun wizardLater() {
        val session = _state.value.session ?: return
        _shellToPage.value = ShellPage.DASHBOARD
        scope.launch {
            enterShellQuiet(session, service.listAccounts())
        }
    }

    // ---- 账户菜单 / 切换 / 改密 / 登出 ----

    fun openAccountMenu() {
        _state.value = _state.value.copy(dialog = AccountDialog.MENU, dialogError = null)
    }

    fun requestSwitch(target: AccountSummary) {
        _state.value = _state.value.copy(dialog = AccountDialog.SWITCH, dialogTarget = target, dialogError = null)
    }

    fun requestChangePassword() {
        _state.value = _state.value.copy(dialog = AccountDialog.CHANGE_PASSWORD, dialogError = null)
    }

    fun closeDialog() {
        _state.value = _state.value.copy(dialog = AccountDialog.NONE, dialogTarget = null, dialogError = null,
                    dialogBusy = false)
    }

    fun submitSwitch(target: AccountSummary, password: String) {
        val pw = password.toCharArray()
        scope.launch {
            _state.value = _state.value.copy(dialogBusy = true, dialogError = null)
            try {
                val session = service.switchAccount(SwitchReq(target.id, pw))
                _state.value = _state.value.copy(dialog = AccountDialog.NONE, dialogTarget = null, dialogBusy = false)
                showToast(WzToastKind.Success, String.format(AuthCopy.SWITCH_TOAST_OK, session.account.username))
                _state.value = _state.value.copy(session = session, accounts = service.listAccounts())
            } catch (e: PasswordMismatchException) {
                _state.value = _state.value.copy(dialogBusy = false, dialogError = AuthCopy.ERR_PASSWORD_MISMATCH)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(dialogBusy = false, dialogError = AuthCopy.ERR_A3_ACCOUNT_LOAD)
            } finally {
                Zeroization.wipe(pw)
            }
        }
    }

    fun submitChangePassword(current: String, newPassword: String) {
        val cur = current.toCharArray()
        val neu = newPassword.toCharArray()
        scope.launch {
            _state.value = _state.value.copy(dialogBusy = true, dialogError = null)
            try {
                service.changePassword(ChangePwdReq(cur, neu))
                _state.value = _state.value.copy(dialog = AccountDialog.NONE, dialogTarget = null, dialogBusy = false)
                showToast(WzToastKind.Success, AuthCopy.CHANGE_PW_TOAST_OK)
                // PRD 5.1-6 / ADR-002 §3：改密作废令牌 → 回登录页重新登录
                _state.value = _state.value.copy(
                    stage = GateStage.LOGIN,
                    accounts = service.listAccounts(),
                    busyText = null,
                )
            } catch (e: OldPasswordMismatchException) {
                _state.value = _state.value.copy(dialogBusy = false, dialogError = AuthCopy.ERR_A2_OLD_PASSWORD)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(dialogBusy = false, dialogError = AuthCopy.ERR_GENERIC)
            } finally {
                Zeroization.wipe(cur)
                Zeroization.wipe(neu)
            }
        }
    }

    @Suppress("SwallowedException") // 登出尽力清除钥匙串条目；内存会话已由服务层先行擦除
    fun logout() {
        scope.launch {
            try {
                service.logout()
            } catch (t: Throwable) {
                // 尽力清除；内存会话已由服务层先行擦除
            }
            showToast(WzToastKind.Success, AuthCopy.LOGOUT_TOAST)
            _state.value = _state.value.copy(
                stage = GateStage.LOGIN,
                accounts = service.listAccounts(),
                dialog = AccountDialog.NONE,
                dialogTarget = null,
                dialogBusy = false,
                pendingCreate = null,
                busyText = null,
            )
        }
    }

    fun dismissToast() {
        _state.value = _state.value.copy(toast = null)
    }

    fun dispose() {
        scope.cancel()
    }

    private suspend fun enterShellQuiet(session: Session, accounts: List<AccountSummary>) {
        _state.value = _state.value.copy(
            stage = GateStage.SHELL,
            session = session,
            accounts = accounts,
            busyText = null,
        )
    }

    private suspend fun enterShell(session: Session, accounts: List<AccountSummary>) {
        _shellToPage.value = ShellPage.DASHBOARD
        _state.value = AuthUiState(stage = GateStage.SHELL, accounts = accounts, session = session)
    }

    private fun showToast(kind: WzToastKind, message: String) {
        _state.value = _state.value.copy(toast = WzToast(kind, message))
    }
}
