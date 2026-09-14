package com.wuzhufolio.ui.tray

import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * 为什么不用系统菜单：
 * 1. 第一版实现沿用 Compose `Tray` 的 `PopupMenu` → 菜单文字由 **AWT/宿主系统**绘制，在 Windows 上中文乱码；
 * 2. 第二版改为自建 AWT 菜单并显式挂内嵌 Noto Sans SC 字体 → **仍然乱码**（说明问题不在字形覆盖，
 *    而在 AWT 菜单文本的渲染/转码路径本身）；
 * 3. 因此第三版**彻底绕开 AWT 文本**：托盘只保留 AWT 图标（图像 + 点击事件），菜单内容由
 *    **Compose/Skia 自绘**——与应用界面同一条字体渲染链（界面中文显示正常，即此路径可信）。
 *
 * 交互：鼠标点击项执行动作；Esc 关闭；失焦关闭由宿主窗口负责（见 `TrayMenuWindow`）。
 * 纯 UI、无平台依赖，可在离屏 Compose 测试中验证。
 */
@Composable
fun TrayMenuContent(
    open: String,
    syncNow: String,
    quit: String,
    onOpen: () -> Unit,
    onSync: () -> Unit,
    onQuit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WzTheme.colors
    // 菜单容器自身可聚焦并主动取焦：保证 Esc 一定被本层收到（菜单窗口内可能没有其它可聚焦子项）
    val menuFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { menuFocus.requestFocus() } }
    Column(
        modifier = modifier
            .focusRequester(menuFocus)
            .focusable()
            .testTag("tray-menu")
            .background(colors.surface, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
    ) {
        TrayMenuItem(open, "tray-menu-open", colors.ink) { onOpen(); onDismiss() }
        TrayMenuItem(syncNow, "tray-menu-sync", colors.ink) { onSync(); onDismiss() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .height(1.dp)
                .background(colors.line),
        )
        TrayMenuItem(quit, "tray-menu-quit", colors.ink) { onQuit(); onDismiss() }
    }
}

@Composable
private fun TrayMenuItem(
    label: String,
    testTag: String,
    textColor: Color,
    onClick: () -> Unit,
) {
    val colors = WzTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(if (hovered) colors.surface2 else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, color = textColor, style = WzTheme.typography.body)
    }
}
