package com.wuzhufolio.app.tray

/**
 * 通知投递通道选择（**DEF-49**，2026-09-24 人工实测 Linux 后确立；**DEF-64 修订**，2026-10-13）。
 *
 * 背景：0.1.0 的桌面通知只有一条通路 —— AWT `TrayIcon.displayMessage`（系统气泡）。
 * 该 API 在 Linux 上走 **XEmbed**，而 Ubuntu 24.04 的 GNOME 已改 **StatusNotifierItem**，
 * 托盘图标本身要经 `ubuntu-appindicators` 扩展转发，**气泡通知则完全没有承载**
 * （人工实测：托盘点「立即同步」后什么都看不到 → 误判为「没执行同步」）⇒ DEF-49 为 Linux 引入应用内提示窗。
 *
 * **DEF-64（2026-10-13，0.3.0 人工走查 §22.5）**：Windows 打包版走查实测——未登录时点托盘
 * 「立即同步交易」/「立即刷新行情」**没有任何提示**（同步/刷新确实未执行 ✅）。根因是**投递缺口**：
 * 「托盘手动动作」经 `AppHost.showNotice()` 只设置 `notice` 状态，而渲染该状态的 `DesktopToastWindow`
 * 原先**仅在 Linux 组合** ⇒ Win/mac 上这些提示**根本没有投递**（连原生气泡也没走；只有后台调度事件走气泡）。
 * 故本口径修订为：
 * - **Linux + Windows → 应用内 Compose 提示窗**（跨平台一致；该窗是**独立窗口**，托盘驻留态亦可见——DEF-49 已在 Linux 验证）；
 * - **macOS → 保持原生系统气泡**（本次未实测出异常，侵入性最小）。
 */
object NoticeDelivery {

    /**
     * 是否用应用内提示窗投递通知。
     * @param osName 操作系统名（默认取 `os.name`；测试可注入）
     */
    fun useInAppToast(osName: String = System.getProperty("os.name").orEmpty()): Boolean {
        val normalized = osName.lowercase()
        // 注意：匹配 `windows` 而非 `win`——macOS 的 Darwin 内核名（"Darwin"）含子串 "win"，
        // 用 `win` 会把 macOS 误判为 Windows（本用例集内的 `Darwin` 断言即为此设）。
        return normalized.contains("linux") || normalized.contains("windows")
    }
}
