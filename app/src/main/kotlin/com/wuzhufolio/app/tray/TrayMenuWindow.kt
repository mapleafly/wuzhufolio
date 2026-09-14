package com.wuzhufolio.app.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.wuzhufolio.ui.i18n.shellStrings
import com.wuzhufolio.ui.theme.WuzhuTheme
import com.wuzhufolio.ui.tray.TrayMenuContent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * 托盘菜单窗口（Compose 自绘；P6 人工门 **DEF-15** 三次修复）。
 *
 * 为什么是「自绘窗口」而不是系统菜单：AWT `PopupMenu` 的菜单文本在 Windows 上乱码，
 * 且**显式挂内嵌字体后仍乱码**（实测）——说明问题在 AWT 菜单文本的渲染/转码路径本身。
 * 因此菜单改为 Compose/Skia 绘制（与应用界面同一字体链，界面中文显示正常）。
 *
 * 交互约定（贴近原生菜单）：
 * - 无边框、置顶、不可缩放、获得焦点；
 * - **失焦即关闭**（点击别处关闭，避免残留浮层）；
 * - Esc 关闭（`TrayMenuContent` 内处理）；
 * - 点选任一项 → 执行动作并关闭。
 *
 * 尺寸为固定值（三项 + 分隔线），不随内容变化；定位由调用方按右键屏幕坐标换算传入。
 */
@Composable
fun ApplicationScope.TrayMenuWindow(
    position: WindowPosition,
    themeMode: com.wuzhufolio.domain.settings.ThemeMode,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSync: () -> Unit,
    onQuit: () -> Unit,
) {
    val state = rememberWindowState(position = position, size = DpSize(208.dp, 124.dp))
    Window(
        onCloseRequest = onDismiss,
        state = state,
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = true,
        title = "WuZhuFolioTrayMenu",
    ) {
        // 失焦关闭：首次获得焦点后才生效（避免窗口刚创建即被判定失焦而闪退）
        DisposableEffect(Unit) {
            var everFocused = false
            val listener = object : WindowAdapter() {
                override fun windowGainedFocus(e: WindowEvent?) {
                    everFocused = true
                }

                override fun windowLostFocus(e: WindowEvent?) {
                    if (everFocused) onDismiss()
                }
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }
        WuzhuTheme(themeMode = themeMode) {
            TrayMenuContent(
                open = shellStrings.trayOpen,
                syncNow = shellStrings.traySyncNow,
                quit = shellStrings.trayQuit,
                onOpen = onOpen,
                onSync = onSync,
                onQuit = onQuit,
                onDismiss = onDismiss,
            )
        }
    }
}
