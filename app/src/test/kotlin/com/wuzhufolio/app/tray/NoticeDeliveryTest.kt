package com.wuzhufolio.app.tray

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 通知投递通道选择（**DEF-49** 确立；**DEF-64** 修订）。
 *
 * 依据：① 人工在 Ubuntu 24.04 实测「托盘菜单点『立即同步』后什么都没发生」——同步**确实执行了**
 * （`scheduler.syncNow()`），但唯一反馈通道 AWT `TrayIcon.displayMessage` 在 GNOME（XEmbed → SNI 代理）
 * 下没有承载 ⇒ Linux 改投应用内提示窗；② **2026-10-13 Windows 打包版走查**实测「未登录时点托盘
 * 『立即同步交易』/『立即刷新行情』无任何提示」——根因是托盘手动动作的提示**只在 Linux 被渲染**，
 * Win/mac 上根本没有投递 ⇒ Windows 一并改投应用内提示窗（macOS 保持原生气泡）。
 */
class NoticeDeliveryTest {

    @Test
    fun `linux uses the in-app toast`() {
        assertTrue(NoticeDelivery.useInAppToast("Linux"))
        assertTrue(NoticeDelivery.useInAppToast("linux"))
    }

    /** **DEF-64（2026-10-13）**：Windows 改走应用内提示窗——原「保持原生气泡」实测无任何投递。 */
    @Test
    fun `windows also uses the in-app toast`() {
        assertTrue(NoticeDelivery.useInAppToast("Windows 11"))
        assertTrue(NoticeDelivery.useInAppToast("Windows 10"))
        assertTrue(NoticeDelivery.useInAppToast("windows"))
    }

    /** macOS 仍保持原生气泡（本次未实测出异常，侵入性最小）。 */
    @Test
    fun `macos keeps the native balloon`() {
        assertFalse(NoticeDelivery.useInAppToast("Mac OS X"))
        assertFalse(NoticeDelivery.useInAppToast("Mac OS X 14.5"))
        assertFalse(NoticeDelivery.useInAppToast("Darwin"))
    }
}
