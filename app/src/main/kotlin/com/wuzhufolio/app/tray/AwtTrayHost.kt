package com.wuzhufolio.app.tray

import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import org.slf4j.Logger

/**
 * 自建 AWT 系统托盘宿主（P6 人工门 **DEF-15**；替代 Compose `Tray` 的默认实现）。
 *
 * 为什么要自己建：
 * 1. **字体可控**——Compose `Tray` 的菜单项由 AWT `PopupMenu` 承载，无法注入字体；菜单文字走
 *    目标机 AWT 逻辑字体，Windows 上出现中文乱码（DEF-15）。自建后每个 `MenuItem` 显式挂
 *    [TrayFont]（内嵌 Noto Sans SC），不再依赖系统字体回退链；
 * 2. **文案可控**——菜单项此前硬编码中文（英文界面下仍是中文），现由调用方按当前语言传入；
 * 3. **通知同源**——`TrayIcon.displayMessage` 与 Compose `TrayState.sendNotification` 等价，
 *    少一层桥接。
 *
 * 语义保持与 M11 一致：
 * - 托盘不可用（无头/无 StatusNotifier）→ [install] 返回 false，调用方走「关窗即退出」降级；
 * - 左键单击/双击 = 打开主界面（与 Compose `Tray.onAction` 一致）；
 * - 右键 = 平台弹出菜单（打开主界面 / 立即同步 / 退出）；
 * - 关闭（[close]）= 从系统托盘移除图标（窗口隐藏态由调用方管理）。
 *
 * 本类**不触网、不落盘**；菜单动作由调用方注入（组合根持有调度与窗口引用）。
 */
class AwtTrayHost(
    private val icon: Image,
    private val labels: TrayLabels,
    private val logger: Logger,
    private val onOpen: () -> Unit,
    private val onSync: () -> Unit,
    private val onExit: () -> Unit,
) : AutoCloseable {

    private var trayIcon: TrayIcon? = null

    /** 注册托盘图标；不支持/失败返回 false（调用方据此降级，绝不抛异常到界面层）。 */
    fun install(): Boolean {
        if (!TraySupport.isSupported()) return false
        return runCatching {
            val menuFont = TrayFont.menuFont()
            val menu = PopupMenu()
            menu.font = menuFont // 显式注入字体（DEF-15 核心：不依赖目标机字体回退链）
            menu.add(menuItem(labels.open, menuFont, onOpen))
            menu.add(menuItem(labels.syncNow, menuFont, onSync))
            menu.addSeparator()
            menu.add(menuItem(labels.quit, menuFont, onExit))

            val awtIcon = TrayIcon(icon, TOOLTIP).apply {
                setImageAutoSize(true)
                popupMenu = menu
                addActionListener(ActionListener { onOpen() }) // Windows 双击 / 部分 Linux 单击
                addMouseListener(
                    object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            if (e.button == MouseEvent.BUTTON1) onOpen()
                        }
                    },
                )
            }
            SystemTray.getSystemTray().add(awtIcon)
            trayIcon = awtIcon
            true
        }.getOrElse {
            logger.warn("tray registration failed ({}); falling back to close-to-exit", it.javaClass.simpleName)
            false
        }
    }

    /** 桌面通知（同步完成/失败、备份提醒）；托盘未注册时静默忽略。 */
    fun notify(title: String, body: String, error: Boolean = false) {
        val icon = trayIcon ?: return
        runCatching {
            icon.displayMessage(
                title,
                body,
                if (error) TrayIcon.MessageType.ERROR else TrayIcon.MessageType.INFO,
            )
        }.onFailure { logger.debug("tray notification failed ({})", it.javaClass.simpleName) }
    }

    override fun close() {
        val icon = trayIcon ?: return
        runCatching { SystemTray.getSystemTray().remove(icon) }
        trayIcon = null
    }

    private fun menuItem(text: String, font: java.awt.Font, action: () -> Unit): MenuItem =
        MenuItem(text).apply {
            this.font = font
            addActionListener { action() }
        }

    private companion object {
        const val TOOLTIP = "WuZhuFolio"
    }
}

/** 托盘菜单文案（按当前界面语言传入；zh/en 由 ui/i18n 提供）。 */
data class TrayLabels(
    val open: String,
    val syncNow: String,
    val quit: String,
)
