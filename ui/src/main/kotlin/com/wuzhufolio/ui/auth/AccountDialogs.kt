package com.wuzhufolio.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import com.wuzhufolio.domain.accounts.AccountSummary
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.theme.WzTheme

/** 创建前风险确认弹窗（PRD 1.1-6：勾选「我已了解上述风险」前「确认创建」disabled 硬门控）。 */
@Composable
fun RiskConfirmModal(
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var agreed by remember { mutableStateOf(false) }
    val colors = WzTheme.colors
    InPlaceModal(title = AuthCopy.RISK_TITLE, onDismiss = onCancel, width = 440.dp, testTag = "risk-modal") {
        BannerBox(AuthCopy.RISK_BANNER_TITLE, AuthCopy.RISK_BODY, testTag = "risk-banner")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clickable { agreed = !agreed }
                .testTag("rc-agree"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = agreed,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.accent,
                    uncheckedColor = colors.line,
                    checkmarkColor = colors.accentInk,
                ),
            )
            Text(text = AuthCopy.RISK_AGREE, color = colors.ink2, style = WzTheme.typography.caption)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        ) {
            WzButton(text = AuthCopy.RISK_CANCEL, onClick = onCancel, variant = WzButtonVariant.Secondary,
                        testTag = "risk-cancel")
            WzButton(
                text = AuthCopy.RISK_CONFIRM,
                onClick = onConfirm,
                enabled = agreed && !busy,
                modifier = Modifier.padding(start = 10.dp),
                testTag = "risk-confirm",
            )
        }
    }
}

/** 账户菜单（原型侧栏底部 acctBtn 下拉 → M2 居中模态，偏差记录于模块文档）：账户列表/切换入口/改密/登出。 */
@Composable
fun AccountMenuModal(
    accounts: List<AccountSummary>,
    current: AccountSummary?,
    onSwitchRequest: (AccountSummary) -> Unit,
    onChangePasswordRequest: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WzTheme.colors
    InPlaceModal(title = AuthCopy.ACCOUNT_MENU_TITLE, onDismiss = onDismiss, width = 400.dp, testTag = "acct-menu") {
        Column(modifier = Modifier.fillMaxWidth()) {
            accounts.forEach { account ->
                val isCurrent = account.id == current?.id
                Text(
                    text = if (isCurrent) {
                        String.format(AuthCopy.ACCOUNT_CURRENT, account.username)
                    } else {
                        account.username
                    },
                    color = if (isCurrent) colors.accent else colors.ink,
                    style = WzTheme.typography.body,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isCurrent) { onSwitchRequest(account) }
                        .padding(vertical = 8.dp)
                        .testTag("acct-item-" + account.id),
                )
            }
            Text(
                text = AuthCopy.ACCOUNT_MENU_CHANGE_PW,
                color = colors.ink2,
                style = WzTheme.typography.body,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onChangePasswordRequest)
                    .padding(vertical = 8.dp)
                    .testTag("acct-change-pw"),
            )
            Text(
                text = AuthCopy.ACCOUNT_MENU_LOGOUT,
                color = colors.loss,
                style = WzTheme.typography.bodyStrong,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLogout)
                    .padding(vertical = 8.dp)
                    .testTag("acct-logout"),
            )
        }
    }
}

/** 切换账户（严格模式，Modal 380px：目标密码验证通过才切换）。 */
@Composable
fun SwitchAccountModal(
    target: AccountSummary,
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember(target) { mutableStateOf("") }
    InPlaceModal(
        title = String.format(AuthCopy.SWITCH_TITLE, target.username),
        onDismiss = onDismiss,
        width = 380.dp,
        testTag = "switch-modal",
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            WzTextField(
                value = password,
                onValueChange = { password = it },
                label = AuthCopy.SWITCH_LABEL,
                placeholder = AuthCopy.LOGIN_PASSWORD_PLACEHOLDER,
                error = error,
                isPassword = true,
                testTag = "switch-pw",
            )
            Text(
                text = AuthCopy.SWITCH_HINT,
                color = WzTheme.colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                WzButton(text = AuthCopy.SWITCH_CANCEL, onClick = onDismiss, variant = WzButtonVariant.Secondary,
                            testTag = "switch-cancel")
                WzButton(
                    text = if (busy) AuthCopy.SWITCH_BUSY else AuthCopy.SWITCH_CONFIRM,
                    onClick = { onSubmit(password) },
                    enabled = !busy && password.isNotEmpty(),
                    modifier = Modifier.padding(start = 10.dp),
                    testTag = "switch-confirm",
                )
            }
        }
    }
}

/** 修改密码（Modal 400px；验证原密码 → 新密码强度 + 二次一致 → 仅重包 KEK）。 */
@Composable
fun ChangePasswordModal(
    busy: Boolean,
    error: String?,
    onSubmit: (current: String, newPassword: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var newPw2 by remember { mutableStateOf("") }
    var newError by remember { mutableStateOf<String?>(null) }
    var new2Error by remember { mutableStateOf<String?>(null) }
    val colors = WzTheme.colors

    InPlaceModal(title = AuthCopy.CHANGE_PW_TITLE, onDismiss = onDismiss, width = 400.dp, testTag = "change-pw-modal") {
        Column(modifier = Modifier.fillMaxWidth()) {
            WzTextField(
                value = current,
                onValueChange = { current = it },
                label = AuthCopy.CHANGE_PW_OLD_LABEL,
                placeholder = AuthCopy.LOGIN_PASSWORD_PLACEHOLDER,
                error = error, // A2 原密码不正确（来自服务层）
                isPassword = true,
                testTag = "change-pw-old",
            )
            WzTextField(
                value = newPw,
                onValueChange = { newPw = it; newError = null },
                label = "新密码",
                placeholder = AuthCopy.CHANGE_PW_NEW_PLACEHOLDER,
                error = newError,
                isPassword = true,
                testTag = "change-pw-new",
            )
            WzTextField(
                value = newPw2,
                onValueChange = { newPw2 = it; new2Error = null },
                label = "确认新密码",
                placeholder = AuthCopy.CHANGE_PW_NEW2_PLACEHOLDER,
                error = new2Error,
                isPassword = true,
                testTag = "change-pw-new2",
            )
            Text(
                text = AuthCopy.CHANGE_PW_HINT,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                WzButton(text = AuthCopy.SWITCH_CANCEL, onClick = onDismiss, variant = WzButtonVariant.Secondary,
                            testTag = "change-pw-cancel")
                WzButton(
                    text = if (busy) AuthCopy.CHANGE_PW_BUSY else AuthCopy.CHANGE_PW_SAVE,
                    onClick = {
                        var valid = true
                        if (newPw.isEmpty() || !com.wuzhufolio.domain.accounts.AccountPolicy.meetsMinimum(newPw)) {
                            newError = AuthCopy.CREATE_PW_ERROR_WEAK
                            valid = false
                        }
                        if (newPw != newPw2) {
                            new2Error = AuthCopy.CREATE_PW2_ERROR_MISMATCH
                            valid = false
                        }
                        if (valid) onSubmit(current, newPw)
                    },
                    enabled = !busy,
                    modifier = Modifier.padding(start = 10.dp),
                    testTag = "change-pw-save",
                )
            }
        }
    }
}
