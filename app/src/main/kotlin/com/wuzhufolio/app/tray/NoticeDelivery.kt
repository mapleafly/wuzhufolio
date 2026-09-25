package com.wuzhufolio.app.tray

/**
 * 通知投递通道选择（**DEF-49**，2026-09-24 人工实测 Linux 后确立）。
 *
 * 背景：0.1.0 的桌面通知只有一条通路 —— AWT `TrayIcon.displayMessage`（系统气泡）。
 * 该 API 在 Linux 上走 **XEmbed**，而 Ubuntu 24.04 的 GNOME 已改 **StatusNotifierItem**，
 * 托盘图标本身要经 `ubuntu-appindicators` 扩展转发，**气泡通知则完全没有承载**
 * （人工实测：托盘点「立即同步」后什么都看不到 → 误判为「没执行同步」）。
 *
 * 口径：
 * - **Linux → 应用内 Compose 提示窗**（[com.wuzhufolio.app.tray.DesktopToastWindow]，等价可见、三平台一致）；
 * - **Windows / macOS → 保持原生系统气泡**（0.1.0 行为不变，进系统通知中心，侵入性最小）。
 *
 * 纯函数（入参 = os.name），便于单测；不在生产代码里散落平台判断。
 */
object NoticeDelivery {

    /**
     * 是否用应用内提示窗投递通知。
     * @param osName 操作系统名（默认取 `os.name`；测试可注入）
     */
    fun useInAppToast(osName: String = System.getProperty("os.name").orEmpty()): Boolean =
        osName.lowercase().contains("linux")
}
