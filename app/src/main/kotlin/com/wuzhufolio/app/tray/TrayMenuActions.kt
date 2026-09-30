package com.wuzhufolio.app.tray

/**
 * 托盘菜单的动作回调（打包成一组，避免长参数列表）。
 *
 * 两条菜单实现共用同一套回调，保证口径一致：
 * - Linux：桌面 Shell 渲染的菜单（StatusNotifierItem + dbusmenu，见 `linux/StatusNotifierService.kt`）；
 * - Windows / macOS（及 Linux 降级路径）：Compose 自绘菜单（[TrayMenuWindow]）。
 */
data class TrayMenuActions(
    val onDismiss: () -> Unit,
    val onOpen: () -> Unit,
    val onSync: () -> Unit,
    val onRefresh: () -> Unit,
    val onQuit: () -> Unit,
)
