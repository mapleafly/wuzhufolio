package com.wuzhufolio.app.tray

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 通知投递通道选择（**`DEF-49`** 确立；**`DEF-64`** 修订；**`T16.1`** 三平台统一）。
 *
 * 依据：① 人工在 Ubuntu 24.04 实测「托盘菜单点『立即同步』后什么都没发生」——同步**确实执行了**
 * （`scheduler.syncNow()`），但唯一反馈通道 AWT `TrayIcon.displayMessage` 在 GNOME（XEmbed → SNI 代理）
 * 下没有承载 ⇒ Linux 改投应用内提示窗；② **2026-10-13 Windows 打包版走查**实测「未登录时点托盘
 * 『立即同步交易』/『立即刷新行情』无任何提示」——根因是托盘手动动作的提示**只在一个平台被渲染**，
 * 其余平台根本没有投递 ⇒ Windows 一并改投；③ **2026-10-14（`T16.1`；`D51` 后 macOS 进发布范围）**复核发现
 * **macOS 仍落在同一缺口**（原口径 `linux || windows`）⇒ 三平台**统一**走应用内提示窗，
 * **未列出的平台由原生气泡兜底**（`channelsFor` 恒非空）。
 *
 * 本用例只钉「**平台 → 主通道**」这一基础映射；「平台 × 场景」的完整矩阵守护见
 * [NoticeDeliveryMatrixTest]（矩阵文档 = `docs/design/desktop-notifications.md`）。
 */
class NoticeDeliveryTest {

    @Test
    fun `linux uses the in-app toast`() {
        assertTrue(NoticeDelivery.useInAppToast("Linux"))
        assertTrue(NoticeDelivery.useInAppToast("linux"))
    }

    /** **`DEF-64`（2026-10-13）**：Windows 改走应用内提示窗——原「保持原生气泡」实测无任何投递。 */
    @Test
    fun `windows also uses the in-app toast`() {
        assertTrue(NoticeDelivery.useInAppToast("Windows 11"))
        assertTrue(NoticeDelivery.useInAppToast("Windows 10"))
        assertTrue(NoticeDelivery.useInAppToast("windows"))
    }

    /**
     * **`T16.1`（2026-10-14）**：macOS 同样走应用内提示窗（`D51` 后 macOS 进发布范围 ⇒ 该缺口是发布前置）。
     *
     * `Darwin` 是 macOS **内核名**——它含子串 `win`，若判据误用 `win` 会被当成 Windows（本用例保留该反例）。
     */
    @Test
    fun `macos uses the in-app toast too`() {
        assertTrue(NoticeDelivery.useInAppToast("Mac OS X"))
        assertTrue(NoticeDelivery.useInAppToast("Mac OS X 14.5"))
        assertTrue(NoticeDelivery.useInAppToast("Darwin"))
    }

    /** 未列出的平台：不用应用内提示窗，改由**原生气泡兜底**（不得出现「两个通道都不走」）。 */
    @Test
    fun `unknown platforms fall back to the native balloon`() {
        for (os in listOf("FreeBSD 14", "SunOS", "OpenBSD 7.5", "")) {
            assertFalse(NoticeDelivery.useInAppToast(os), "「$os」不应走应用内提示窗")
            assertTrue(NoticeDelivery.needsNativeBalloonFallback(os), "「$os」必须走原生气泡兜底，否则静默无提示")
        }
    }
}
