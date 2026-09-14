package com.wuzhufolio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.theme.WzTheme

/** 按钮变体（design-tokens §4.2）：主按钮 accent 实底 / 次按钮描边 / 危险按钮 loss 实底。 */
enum class WzButtonVariant { Primary, Secondary, Danger }

/**
 * 基础按钮：高度 32-36dp、圆角 7dp、焦点态 accent 2px 描边（原型 :focus-visible 口径，a11y 基线）。
 */
@Composable
fun WzButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: WzButtonVariant = WzButtonVariant.Primary,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val colors = WzTheme.colors
    var focused by remember { mutableStateOf(false) }

    val containerColor = when (variant) {
        WzButtonVariant.Primary -> colors.accent
        WzButtonVariant.Secondary -> colors.surface
        WzButtonVariant.Danger -> colors.loss
    }
    val contentColor = when (variant) {
        WzButtonVariant.Primary -> colors.accentInk
        WzButtonVariant.Secondary -> colors.accent
        WzButtonVariant.Danger -> colors.accentInk
    }
    val borderColor = when {
        focused -> colors.accent
        variant == WzButtonVariant.Secondary -> colors.accent
        else -> colors.line
    }
    val borderWidth = if (focused || variant == WzButtonVariant.Secondary) 2.dp else 1.dp

    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .background(if (enabled) containerColor else colors.surface2)
            .border(borderWidth, borderColor, shape)
            // P6 人工门 DEF-13：clickable 本身即焦点目标——此前追加的显式 .focusable() 造成
            // **同一节点两个焦点目标**，Tab 会落在没有语义/没有焦点环的隐形目标上（表现为「焦点进不了页面」
            // 「按键无反应」）。此处只保留 clickable（禁用态自然不可聚焦）。
            .clickable(enabled = enabled, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .alpha(if (enabled) 1f else 0.55f)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) contentColor else colors.ink3,
            style = WzTheme.typography.bodyStrong,
        )
    }
}
