package com.wuzhufolio.ui.components

import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.i18n.commonStrings
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 当前打开的 [WzModal] 数量（0 = 无弹层）。
 *
 * 用途：主壳判断「页面内容区键盘退出（Esc/↑/↓）是否安全」——弹层打开时键盘输入归弹层，
 * 绝不允许把焦点移回侧边栏（否则弹层还开着、焦点却在弹层背后，键盘用户被卡住）。
 * 弹层作为就地叠加层没有独立窗口，主壳无法从布局上感知，故用计数登记（DisposableEffect 配对增减）。
 */
internal object WzOverlayRegistry {
    var openModalCount by mutableStateOf(0)
        private set

    fun onModalOpened() {
        openModalCount++
    }

    fun onModalClosed() {
        openModalCount = (openModalCount - 1).coerceAtLeast(0)
    }
}

/**
 * 弹窗**内容区**高度上限（DEF-36，2026-09-15 人工门第七轮）：窗口高 × 0.66，下限 320dp。
 *
 * 口径：弹窗内容尽量在一屏内放下（人工实测 1024×768 下交易/增资表单「滚动范围不到一行」，
 * 属内容比窗口只高一点点——把上限从写死的 470/420dp 改为按窗口高度计算即可基本消除滚动条）；
 * 窗口确实过小时仍保留内部滚动，避免内容被裁切。
 */
@Composable
fun modalContentMaxHeight(): Dp {
    val containerHeight = LocalWindowInfo.current.containerSize.height
    val windowHeight = with(LocalDensity.current) { containerHeight.toDp() }
    return (windowHeight * 0.66f).coerceAtLeast(320.dp)
}

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
    // 弹层存续期登记（主壳据 openModalCount 决定是否接管 Esc/方向键）
    DisposableEffect(Unit) {
        WzOverlayRegistry.onModalOpened()
        onDispose { WzOverlayRegistry.onModalClosed() }
    }
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
                // DEF-13：吞点击改 pointerInput（clickable 会再建一个焦点目标 → Tab 落隐形目标）；
                // 卡片保留唯一焦点目标（上面的 focusable）以承接无输入框弹窗的 Esc。
                .pointerInput(Unit) { detectTapGestures { } }
                // 弹层作为单一语义边界（与改前 clickable 合并行为一致，测试与读屏按卡片粒度取节点）
                .semantics(mergeDescendants = true) {}
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
                        text = commonStrings.close,
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
