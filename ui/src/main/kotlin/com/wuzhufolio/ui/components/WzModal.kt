package com.wuzhufolio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.theme.WzTheme

/**
 * Modal（design-tokens §4.2）：居中 380-680dp 宽、圆角 10dp、半透明遮罩、esc/遮罩点击/关闭按钮可关。
 *
 * 实现要点（**2026-09-04 修复轮，GUI 共性约束见 AGENTS.md §7.3**）：统一为**就地叠加层**（同窗口
 * Box 覆盖，不用 Popup）——桌面端 Popup（含 focusable=false）无法可靠接收键盘输入（M2 验收实测记录；
 * M5 Key 弹窗复现：输入框不可键入、仅右键粘贴可用）。就地叠加层与主场景同一焦点体系：键盘输入正常、
 * 语义树与 Compose 测试仍在同一场景；Esc/遮罩/关闭按钮可关，打开自动聚焦卡片；含输入框的弹窗请对
 * 首输入框再请求焦点（参见 MarketSettingsPage.KeyModal 示例，约束 7.3-②）。
 */
@Composable
fun WzModal(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 480.dp,
    testTag: String? = null,
    /** 首输入框聚焦器（共性约束 7.3-②）：传入时弹窗打开聚焦该输入框而非卡片（含输入框弹窗必传）。 */
    initialFocusRequester: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WzTheme.colors
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { (initialFocusRequester ?: focusRequester).requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ink.copy(alpha = SCRIM_ALPHA))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 卡片：焦点容器（Esc）+ 吞点击（不透传到遮罩）
        Box(
            modifier = modifier
                .width(width)
                .background(colors.surface, RoundedCornerShape(10.dp))
                .border(1.dp, colors.line, RoundedCornerShape(10.dp))
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        color = colors.ink,
                        style = WzTheme.typography.pageTitle,
                        modifier = Modifier.weight(1f),
                    )
                    WzButton(
                        text = "关闭",
                        onClick = onDismiss,
                        variant = WzButtonVariant.Secondary,
                        testTag = if (testTag != null) testTag + "-close" else null,
                    )
                }
                Column(modifier = Modifier.padding(top = 16.dp), content = content)
            }
        }
    }
}

private const val SCRIM_ALPHA = 0.28f
