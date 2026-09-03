package com.wuzhufolio.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.accounts.AccountPolicy
import com.wuzhufolio.domain.accounts.AccountSummary
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.theme.WzTheme

/** 登录页（原型 lg*；用户名枚举下拉或纯输入双形态；记住我默认勾选）。 */
@Composable
fun LoginPage(
    accounts: List<AccountSummary>,
    usernameEnumEnabled: Boolean,
    busyText: String?,
    formError: String?,
    onLogin: (username: String, password: String, remember: Boolean) -> Unit,
    onForgot: () -> Unit,
    onCreate: () -> Unit,
    onTyping: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var selectedUser by remember { mutableStateOf(accounts.firstOrNull()?.username ?: "") }
    var typedUser by remember { mutableStateOf("") }

    GateCard(width = 400) {
        GateHeading(AuthCopy.LOGIN_TITLE, AuthCopy.LOGIN_SUBTITLE, testTag = "login-title")
        Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            if (usernameEnumEnabled && accounts.isNotEmpty()) {
                Text(text = "用户名", color = WzTheme.colors.ink2, style = WzTheme.typography.caption)
                UsernameEnumRow(
                    accounts = accounts.map { it.username },
                    selected = selectedUser,
                    onSelect = { selectedUser = it; onTyping() },
                    testTag = "lg-user",
                )
                Text(
                    text = AuthCopy.LOGIN_USER_HINT,
                    color = WzTheme.colors.ink3,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                WzTextField(
                    value = typedUser,
                    onValueChange = { typedUser = it; onTyping() },
                    label = "用户名",
                    testTag = "lg-user",
                )
            }
            WzTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = null
                    onTyping()
                },
                label = "密码",
                placeholder = AuthCopy.LOGIN_PASSWORD_PLACEHOLDER,
                error = passwordError,
                isPassword = true,
                testTag = "lg-pw",
            )
            RememberRow(AuthCopy.LOGIN_REMEMBER_LABEL, rememberMe, { rememberMe = it }, testTag = "lg-remember")
            WzButton(
                text = busyText ?: AuthCopy.LOGIN_BUTTON,
                onClick = {
                    if (password.isEmpty()) {
                        passwordError = AuthCopy.LOGIN_ERROR_PASSWORD_EMPTY
                        return@WzButton
                    }
                    val user = if (usernameEnumEnabled && accounts.isNotEmpty()) selectedUser else typedUser.trim()
                    onLogin(user, password, rememberMe)
                },
                enabled = busyText == null,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                testTag = "lg-btn",
            )
            FormError(formError)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glink(AuthCopy.LOGIN_LINK_FORGOT, onForgot, testTag = "lg-forgot")
                Glink(AuthCopy.LOGIN_LINK_CREATE, onCreate, testTag = "lg-create-link")
            }
        }
    }
}

/** 用户名枚举（原型 lgUser 下拉：点击展开列表选择；空账户/枚举关闭时由登录页走输入框）。 */
@Composable
private fun UsernameEnumRow(
    accounts: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    testTag: String,
) {
    var open by remember { mutableStateOf(false) }
    val colors = WzTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(10.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = selected, color = colors.ink, style = WzTheme.typography.body, modifier = Modifier.weight(1f))
            Text(text = "▾", color = colors.ink3, style = WzTheme.typography.caption)
        }
        if (open) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp).testTag(testTag + "-list")) {
                accounts.forEach { name ->
                    Text(
                        text = name,
                        color = if (name == selected) colors.accent else colors.ink,
                        style = WzTheme.typography.body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(name)
                                open = false
                            }
                            .padding(8.dp)
                            .testTag(testTag + "-opt-" + name),
                    )
                }
            }
        }
    }
}

/** 创建页（原型 cu*；密码强度条/二次确认/记住我；风险确认门控在弹窗内，勾选才可提交）。 */
@Composable
fun CreatePage(
    busyText: String?,
    formError: String?,
    onBack: () -> Unit,
    onTyping: () -> Unit,
    openRiskConfirm: (username: String, password: String, remember: Boolean) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var userError by remember { mutableStateOf<String?>(null) }
    var pwError by remember { mutableStateOf<String?>(null) }
    var pw2Error by remember { mutableStateOf<String?>(null) }
    val strength = if (password.isEmpty()) null else AccountPolicy.strength(password)

    GateCard(width = 400) {
        GateHeading(AuthCopy.CREATE_TITLE, AuthCopy.CREATE_SUBTITLE, testTag = "create-title")
        Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            WzTextField(
                value = username,
                onValueChange = { username = it; userError = null; onTyping() },
                label = "用户名",
                placeholder = AuthCopy.CREATE_USER_PLACEHOLDER,
                error = userError,
                testTag = "cu-user",
            )
            WzTextField(
                value = password,
                onValueChange = { password = it; pwError = null; onTyping() },
                label = "密码",
                placeholder = AuthCopy.LOGIN_PASSWORD_PLACEHOLDER,
                error = pwError,
                isPassword = true,
                testTag = "cu-pw",
            )
            StrengthMeter(strength, testTag = "cu-meter")
            Text(
                text = AuthCopy.CREATE_PW_HINT,
                color = WzTheme.colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp),
            )
            WzTextField(
                value = password2,
                onValueChange = { password2 = it; pw2Error = null; onTyping() },
                label = "确认密码",
                placeholder = AuthCopy.CREATE_PW2_PLACEHOLDER,
                error = pw2Error,
                isPassword = true,
                testTag = "cu-pw2",
            )
            RememberRow(AuthCopy.CREATE_REMEMBER_LABEL, rememberMe, { rememberMe = it }, testTag = "cu-remember")
            WzButton(
                text = busyText ?: AuthCopy.CREATE_BUTTON,
                onClick = {
                    var valid = true
                    if (username.isBlank()) {
                        userError = AuthCopy.CREATE_USER_ERROR_EMPTY
                        valid = false
                    }
                    if (!AccountPolicy.meetsMinimum(password)) {
                        pwError = AuthCopy.CREATE_PW_ERROR_WEAK
                        valid = false
                    }
                    if (password.isEmpty() || password2.isEmpty() || password != password2) {
                        pw2Error = AuthCopy.CREATE_PW2_ERROR_MISMATCH
                        valid = false
                    }
                    if (valid) openRiskConfirm(username.trim(), password, rememberMe)
                },
                enabled = busyText == null,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                testTag = "cu-btn",
            )
            FormError(formError)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Glink(AuthCopy.CREATE_LINK_BACK, onBack, testTag = "cu-back")
            }
        }
    }
}

/** 初始化向导（原型 gateWizard：2x2 卡片 + 稍后再说）。 */
@Composable
fun WizardPage(
    username: String,
    onPick: (WizardKind) -> Unit,
    onLater: () -> Unit,
) {
    val colors = WzTheme.colors
    GateCard(width = 660) {
        GateHeading(AuthCopy.WIZARD_TITLE, String.format(AuthCopy.WIZARD_SUBTITLE, username), testTag = "wizard-title")
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            WizardOption(
                title = "手动添加交易",
                description = "逐条录入买入 / 卖出记录，构建初始持仓",
                recommended = false,
                onClick = { onPick(WizardKind.MANUAL) },
                testTag = "wizard-manual",
            )
            WizardOption(
                title = "CSV 导入",
                description = "导入交易所导出的 CSV 批量历史（可先下载标准模板）",
                recommended = false,
                onClick = { onPick(WizardKind.CSV) },
                testTag = "wizard-csv",
            )
            WizardOption(
                title = "关联交易所 API",
                description = "只读密钥自动同步 · 保存后立即执行首次同步",
                recommended = true,
                onClick = { onPick(WizardKind.API) },
                testTag = "wizard-api",
            )
            WizardOption(
                title = "从备份恢复",
                description = "从 .cpro 备份文件恢复全部数据（已创建目标账户）",
                recommended = false,
                onClick = { onPick(WizardKind.RESTORE) },
                testTag = "wizard-restore",
            )
            Glink(
                AuthCopy.WIZARD_LATER,
                onLater,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                testTag = "wizard-later",
            )
        }
    }
}

@Composable
private fun WizardOption(title: String, description: String, recommended: Boolean, onClick: () -> Unit,
            testTag: String) {
    val colors = WzTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .testTag(testTag)
            .then(Modifier.clickable(onClick = onClick))
            .then(
                Modifier
                    .padding(0.dp)
                    .padding(12.dp),
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, color = colors.ink, style = WzTheme.typography.bodyStrong)
            if (recommended) {
                Text(
                    text = AuthCopy.WIZARD_RECOMMEND,
                    color = colors.warn,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Text(
            text = description,
            color = colors.ink2,
            style = WzTheme.typography.caption,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 忘记密码页（纯告知；A4 整段）。 */
@Composable
fun ForgotPage(onBack: () -> Unit) {
    GateCard(width = 400) {
        GateHeading(AuthCopy.FORGOT_TITLE, AuthCopy.FORGOT_SUBTITLE, testTag = "forgot-title")
        BannerBox(AuthCopy.FORGOT_BANNER_TITLE, AuthCopy.FORGOT_BODY, testTag = "forgot-banner")
        Text(
            text = AuthCopy.FORGOT_HINT,
            color = WzTheme.colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.padding(top = 14.dp),
        )
        Glink(AuthCopy.FORGOT_BACK, onBack, modifier = Modifier.padding(top = 16.dp), testTag = "forgot-back")
    }
}
