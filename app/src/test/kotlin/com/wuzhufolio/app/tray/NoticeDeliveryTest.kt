package com.wuzhufolio.app.tray

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 通知投递通道选择（**DEF-49**）。
 *
 * 依据：人工在 Ubuntu 24.04 实测「托盘菜单点『立即同步』后什么都没发生」——
 * 同步**确实执行了**（`scheduler.syncNow()`），但唯一反馈通道 AWT `TrayIcon.displayMessage`
 * 在 GNOME（XEmbed → SNI 代理）下没有承载。故 Linux 改投应用内提示窗，Win/mac 保持原生气泡。
 */
class NoticeDeliveryTest {

    @Test
    fun `linux uses the in-app toast`() {
        assertTrue(NoticeDelivery.useInAppToast("Linux"))
        assertTrue(NoticeDelivery.useInAppToast("linux"))
    }

    @Test
    fun `windows and macos keep the native balloon`() {
        assertFalse(NoticeDelivery.useInAppToast("Windows 11"))
        assertFalse(NoticeDelivery.useInAppToast("Windows 10"))
        assertFalse(NoticeDelivery.useInAppToast("Mac OS X"))
        assertFalse(NoticeDelivery.useInAppToast("Mac OS X 14.5"))
    }
}
