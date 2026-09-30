package com.wuzhufolio.ui.tray

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 托盘菜单内容（Compose 自绘；P6 人工门 **DEF-15 二次修复**）。
 *
 * 为什么不用系统菜单：AWT `PopupMenu` 的菜单文本在 Windows 上乱码，且显式挂内嵌字体后**仍乱码**，
 * 因此托盘只保留 AWT 图标，菜单内容由 Compose/Skia 自绘（与应用界面同一条字体渲染链）。
 *
 * **输入方式**：
 * - 鼠标：悬停高亮 + 点击执行（`clickable`）；
 * - 键盘：↑/↓ 移动选中项、Enter/Space 执行、Esc 关闭（原生菜单的常规能力）。
 */
@Composable
fun TrayMenuContent(
    open: String,
    syncNow: String,
    refreshQuotes: String,
    quit: String,
    onOpen: () -> Unit,
    onSync: () -> Unit,
    onRefresh: () -> Unit,
    onQuit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WzTheme.colors
    // 菜单容器自身可聚焦并主动取焦：保证 Esc/方向键一定被本层收到
    val menuFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { menuFocus.requestFocus() } }

    // 可执行项（分隔线不计入键盘导航）
    val actions = listOf(
        Triple(open, "tray-menu-open", onOpen),
        Triple(syncNow, "tray-menu-sync", onSync),
        Triple(refreshQuotes, "tray-menu-refresh", onRefresh),
        Triple(quit, "tray-menu-quit", onQuit),
    )
    // -1 = 无选中（原生菜单口径：仅在方向键导航或悬停时高亮）
    var selected by remember { mutableIntStateOf(-1) }

    fun run(index: Int) {
        actions[index].third()
        onDismiss()
    }

    Column(
        modifier = modifier
            .focusRequester(menuFocus)
            .focusable()
            .testTag("tray-menu")
            .background(colors.surface, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.Escape -> {
                            onDismiss()
                            true
                        }
                        Key.DirectionDown -> {
                            selected = if (selected < 0) 0 else (selected + 1) % actions.size
                            true
                        }
                        Key.DirectionUp -> {
                            selected = if (selected < 0) {
                                actions.size - 1
                            } else {
                                (selected - 1 + actions.size) % actions.size
                            }
                            true
                        }
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            run(if (selected < 0) 0 else selected)
                            true
                        }
                        else -> false
                    }
                }
            },
    ) {
        actions.forEachIndexed { index, (label, tag, _) ->
            if (index == QUIT_INDEX) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                        .height(1.dp)
                        .background(colors.line),
                )
            }
            TrayMenuItem(
                label = label,
                testTag = tag,
                textColor = colors.ink,
                selected = selected == index,
                onClick = { run(index) },
            )
        }
    }
}

/** 「退出」项在列表中的下标（其前有分隔线）。 */
private const val QUIT_INDEX = 3

@Composable
private fun TrayMenuItem(
    label: String,
    testTag: String,
    textColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = WzTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(if (hovered || selected) colors.surface2 else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, color = textColor, style = WzTheme.typography.body)
    }
}
