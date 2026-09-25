package com.wuzhufolio.app.tray

import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import org.slf4j.Logger

/**
 * 系统托盘宿主（P6 人工门 **DEF-15**：Windows 托盘菜单中文乱码）。
 *
 * 设计（第三版，2026-09-15）：
 * - **只让 AWT 负责图标与点击事件**（图像与坐标与文本渲染无关，跨平台可靠）；
 * - **菜单文本一律不交给 AWT**：右键事件回调 [onMenuRequest]（屏幕坐标），由调用方弹出
 *   **Compose 自绘菜单**（`ui/tray/TrayMenuContent`）——与应用界面同一字体渲染链，
 *   中文显示正常（界面已实证），彻底规避 AWT 菜单文本的乱码路径。
 *
 * 历史（为何前两版没解决）：
 * 1. Compose `Tray` 的 `Item(text)` → 底层 AWT `PopupMenu`：菜单文字由系统/AWT 绘制 → 乱码；
 * 2. 自建 AWT 菜单并显式挂内嵌 Noto Sans SC 字体：**仍乱码** → 说明不是字形覆盖问题，
 *    而是 AWT 菜单文本的渲染/转码路径本身；
 * 3. 本版：绕开 AWT 文本，Skia 自绘。
 *
 * 语义与 M11 保持一致：托盘不可用 → [install] 返回 false（调用方降级为「关窗即退出」）；
 * 左键单击/双击 = 打开主界面；右键 = 请求弹出菜单。
 */
class AwtTrayHost(
    private val icon: Image,
    private val logger: Logger,
    private val onOpen: () -> Unit,
    /** 右键菜单请求（屏幕坐标 px）——由调用方弹出 Compose 自绘菜单。 */
    private val onMenuRequest: (x: Int, y: Int) -> Unit,
    /**
     * 是否让 AWT 自动缩放图标到「系统托盘尺寸」。
     *
     * **DEF-48 二轮实测（Ubuntu 24.04 / 缩放 2 / JDK 21）**：AWT 报告的 `trayIconSize=24`（逻辑），
     * 它会把图片按 `24 × 2 = 48` 物理像素栅格化后**按 1:1 画进 32 像素的 XEmbed 窗口** → 图标右下被裁
     * （人工看到的「显示不全」）。因此默认改为 **false**：按给定像素 1:1 绘制，尺寸完全由调用方掌握。
     */
    private val imageAutoSize: Boolean = false,
) : AutoCloseable {

    private var trayIcon: TrayIcon? = null

    /** 注册托盘图标；不支持/失败返回 false（调用方据此降级，绝不抛异常到界面层）。 */
    fun install(): Boolean {
        if (!TraySupport.isSupported()) return false
        return runCatching {
            val awtIcon = TrayIcon(icon, TOOLTIP).apply {
                setImageAutoSize(imageAutoSize)
                addActionListener(ActionListener { onOpen() })
                addMouseListener(
                    object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            if (e.button == MouseEvent.BUTTON1) onOpen()
                        }

                        // 右键：请求 Compose 菜单（不使用 AWT PopupMenu —— 其文本渲染在 Windows 上乱码）
                        override fun mousePressed(e: MouseEvent) {
                            if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                                onMenuRequest(e.xOnScreen, e.yOnScreen)
                            }
                        }

                        override fun mouseReleased(e: MouseEvent) {
                            if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                                onMenuRequest(e.xOnScreen, e.yOnScreen)
                            }
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

    /**
     * 桌面通知（同步完成/失败、备份提醒）；托盘未注册时静默忽略。
     *
     * 注：通知文本由系统气泡绘制（AWT `displayMessage`）——若人工实测通知同样乱码，
     * 需改为应用内自绘 toast（登记模块记录遗留，不在本轮范围）。
     */
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

    private companion object {
        const val TOOLTIP = "WuZhuFolio"
    }
}
