package com.wuzhufolio.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.theme.WzTheme

/** 登录链路卡片容器（品牌块 + 页面内容；原型 gcard）。 */
@Composable
fun GateCard(
    width: Int,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = WzTheme.colors
    Box(modifier = modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(width.dp)
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                .padding(horizontal = 26.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = AuthCopy.BRAND, color = colors.ink, style = WzTheme.typography.display, modifier = Modifier.
                        testTag("gate-brand"))
            Text(
                text = AuthCopy.BRAND_TAG,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 2.dp),
            )
            content()
        }
    }
}

/** 页题 + 副题。 */
@Composable
fun GateHeading(title: String, subtitle: String, testTag: String) {
    val colors = WzTheme.colors
    Text(
        text = title,
        color = colors.ink,
        style = WzTheme.typography.pageTitle,
        modifier = Modifier.padding(top = 16.dp).testTag(testTag),
    )
    Text(
        text = subtitle,
        color = colors.ink2,
        style = WzTheme.typography.caption,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
    )
}

/** 文字链接（原型 glink）。 */
@Composable
fun Glink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, testTag: String? = null) {
    val colors = WzTheme.colors
    Text(
        text = text,
        color = colors.accent,
        style = WzTheme.typography.bodyStrong,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    )
}

/** 表单级错误（A1/A2/A3 等），红色可见文本。 */
@Composable
fun FormError(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    val colors = WzTheme.colors
    Text(text = text, color = colors.loss, style = WzTheme.typography.caption, modifier = modifier.padding(top = 8.dp).
                testTag("auth-form-error"))
}

/** 提示性警示框（风险确认/忘记密码同款：surface2 底 + 1px 边框 + 圆角 7）。 */
@Composable
fun BannerBox(title: String, body: String, testTag: String) {
    val colors = WzTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(colors.surface2)
            .border(1.dp, colors.line, RoundedCornerShape(7.dp))
            .padding(14.dp)
            .testTag(testTag),
    ) {
        Text(text = title, color = colors.warn, style = WzTheme.typography.caption)
        Text(
            text = body,
            color = colors.ink2,
            style = WzTheme.typography.body,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 记住我行（自定义 checkbox 配色对齐 token）。 */
@Composable
fun RememberRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, testTag: String) {
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
            onCheckedChange = null, // 整行可点（点击冒泡到 Row.clickable），避免双触发
            colors = CheckboxDefaults.colors(
                checkedColor = colors.accent,
                uncheckedColor = colors.line,
                checkmarkColor = colors.accentInk,
            ),
        )
        Text(text = label, color = colors.ink2, style = WzTheme.typography.caption)
    }
}

/** 密码强度条（原型 pwScore：弱 1 段 loss / 中 2 段 warn / 强 3 段 gain；空文本不显示）。 */
@Composable
fun StrengthMeter(strength: com.wuzhufolio.domain.accounts.AccountPolicy.Strength?, testTag: String) {
    if (strength == null) return
    val colors = WzTheme.colors
    val label = when (strength) {
        com.wuzhufolio.domain.accounts.AccountPolicy.Strength.WEAK -> AuthCopy.STRENGTH_WEAK
        com.wuzhufolio.domain.accounts.AccountPolicy.Strength.MEDIUM -> AuthCopy.STRENGTH_MEDIUM
        com.wuzhufolio.domain.accounts.AccountPolicy.Strength.STRONG -> AuthCopy.STRENGTH_STRONG
    }
    val color = when (strength) {
        com.wuzhufolio.domain.accounts.AccountPolicy.Strength.WEAK -> colors.loss
        com.wuzhufolio.domain.accounts.AccountPolicy.Strength.MEDIUM -> colors.warn
        com.wuzhufolio.domain.accounts.AccountPolicy.Strength.STRONG -> colors.gain
    }
    val segments = when (strength) {
        com.wuzhufolio.domain.accounts.AccountPolicy.Strength.WEAK -> 1
        com.wuzhufolio.domain.accounts.AccountPolicy.Strength.MEDIUM -> 2
        com.wuzhufolio.domain.accounts.AccountPolicy.Strength.STRONG -> 3
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < segments) color else colors.surface2),
                )
            }
        }
        Text(text = label, color = color, style = WzTheme.typography.caption, modifier = Modifier.padding(start = 8.dp))
    }
}

/** 圆形账户头像（accent 底 + 首字母，design-tokens 原型 acctAva）。 */
@Composable
fun AccountAvatar(username: String, modifier: Modifier = Modifier, testTag: String? = null) {
    val colors = WzTheme.colors
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(colors.accent)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = username.firstOrNull()?.uppercase() ?: "?",
            color = colors.accentInk,
            style = WzTheme.typography.bodyStrong,
        )
    }
}
