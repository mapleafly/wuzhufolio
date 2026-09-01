package com.wuzhufolio.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.wuzhufolio.ui.theme.WzTheme

/**
 * Modal（design-tokens §4.2）：居中 380-680dp 宽、圆角 10dp、半透明遮罩、esc/遮罩点击/关闭按钮可关。
 *
 * 实现要点（M0 实测）：Popup 必须 focusable=false——桌面端 focusable=true 的 Popup/Dialog 会创建
 * 独立 AWT 窗口，脱离 Compose 测试语义树；非 focusable Popup 留在窗口内 composition，语义可测。
 * esc 关闭经卡片焦点 + onPreviewKeyEvent 实现；打开时卡片自动请求焦点（聚焦管理雏形，P4 聚焦首字段）。
 */
@Composable
fun WzModal(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 480.dp,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WzTheme.colors
    val focusRequester = remember { FocusRequester() }
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        // 遮罩（点击可关）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.ink.copy(alpha = SCRIM_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Surface(
                modifier = modifier
                    .align(Alignment.Center)
                    .width(width)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
                    // 卡片区域吞掉点击，避免穿透到遮罩触发关闭
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
                shape = RoundedCornerShape(10.dp),
                color = colors.surface,
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
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

private const val SCRIM_ALPHA = 0.28f
