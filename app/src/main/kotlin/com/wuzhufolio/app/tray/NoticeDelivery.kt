package com.wuzhufolio.app.tray

/** 通知投递**通道**（`AGENTS.md §7.3-13` 矩阵的「通道」维）。 */
enum class NoticeChannel {
    /** 应用内 Compose 提示窗（`DesktopToastWindow`）：独立窗口、托盘驻留态亦可见、**无平台通知 API 依赖**。 */
    IN_APP_TOAST,

    /** 原生系统气泡（AWT `TrayIcon.displayMessage`，经 `AwtTrayHost.notify`）。 */
    NATIVE_BALLOON,
}

/** 通知**场景**（`§7.3-13` 矩阵的「场景」维；由调用点在 `AppHost.showNotice` 声明）。 */
enum class NoticeScene {
    /** 托盘手动动作：「立即同步交易」/「立即刷新行情」的门禁提示、开始、结果。 */
    TRAY_MANUAL_ACTION,

    /** 后台调度事件（同步完成 / 备份提醒；经 `NoticePolicy` 两级开关过滤）。 */
    SCHEDULER_EVENT,
}

/**
 * 通知投递通道选择（**`DEF-49`** 2026-09-24 确立 → **`DEF-64`** 2026-10-13 修订 → **`T16.1`** 2026-10-14 三平台统一）。
 *
 * **矩阵文档 = `docs/design/desktop-notifications.md`**（记录每个「平台 × 场景」由**谁**渲染及代码锚点）；
 * 本对象是其**可执行部分**（守护用例 `NoticeDeliveryMatrixTest`）。
 *
 * ### 演进（两次同型缺陷的代价）
 * 0.1.0 只有一条通路 —— AWT `TrayIcon.displayMessage`（系统气泡）。
 * - **`DEF-49`（Linux）**：该 API 走 XEmbed，而 Ubuntu 24.04 的 GNOME 已改 StatusNotifierItem，气泡**无承载**
 *   （人工实测：托盘点「立即同步」后什么都看不到 → 误判为「没执行同步」）⇒ 引入应用内提示窗。
 * - **`DEF-64`（Windows，2026-10-13 走查 §22.5）**：`AppHost.showNotice()` **只设状态**，而渲染它的
 *   `DesktopToastWindow` 原先**仅在 Linux 组合** ⇒ Win/mac 的托盘手动动作提示**根本没投递**（连气泡也没走）。
 * - **`T16.1`（macOS，2026-10-14）**：`DEF-64` 的修复把口径写成 `linux || windows`，**macOS 仍落在缺口里**
 *   （`D51` 后 macOS 已进发布范围 ⇒ 该缺口是发布前置）。本对象据此定为：
 *
 * ### 现口径
 * - **通道优先**：**三个已发布平台族（Linux / Windows / macOS）统一走应用内提示窗** —— 不依赖任何平台通知 API，
 *   行为三平台一致（该窗是**独立窗口**，托盘驻留态亦可见 —— `DEF-49` 已在 Linux 实机验证、`DEF-64` 修复后
 *   已在 Windows 打包版实机验证）。
 * - **兜底不静默**：**未列出的平台**一律走**原生气泡**兜底；`channelsFor` 因此**恒非空**——
 *   「设了状态但没人渲染」按**缺陷**对待（`§7.3-13②`），不得出现空投递。
 *
 * 纯函数（入参 = `os.name`），便于单测；不在生产代码里散落平台判断。
 */
object NoticeDelivery {

    /**
     * 是否用应用内提示窗投递通知。
     * @param osName 操作系统名（默认取 `os.name`；测试可注入）
     */
    fun useInAppToast(osName: String = System.getProperty("os.name").orEmpty()): Boolean {
        val normalized = osName.lowercase()
        // 注意：Windows 判据用 `windows` 而**不是** `win`——macOS 内核名 "Darwin" 含子串 "win"，
        // 用 `win` 会把 macOS 误判为 Windows（`NoticeDeliveryTest` / `NoticeDeliveryMatrixTest` 均设该反例）。
        // macOS 判据同时接受 `mac`（"Mac OS X"）与 `darwin`（内核名）。
        return normalized.contains("linux") ||
            normalized.contains("windows") ||
            normalized.contains("mac") ||
            normalized.contains("darwin")
    }

    /**
     * 是否需要**原生气泡兜底**（= 应用内提示窗不是本平台的通道）。
     *
     * 反过来读即「恰好一条主通道」：`useInAppToast(os) == !needsNativeBalloonFallback(os)` 恒成立
     * （守护用例逐平台断言），因此不存在两个通道都不走的平台。
     */
    fun needsNativeBalloonFallback(osName: String = System.getProperty("os.name").orEmpty()): Boolean =
        !useInAppToast(osName)

    /**
     * 「平台 × 场景」的投递通道集合（**恒非空**）。
     *
     * 现口径：两种场景**同路由**（应用内提示窗优先，否则原生气泡兜底）——场景维度由调用点声明并进入本判定，
     * 若将来某场景需要专用通道，**在此分叉**，并同步更新矩阵文档与守护用例。
     */
    fun channelsFor(
        scene: NoticeScene,
        osName: String = System.getProperty("os.name").orEmpty(),
    ): Set<NoticeChannel> = when (scene) {
        NoticeScene.TRAY_MANUAL_ACTION,
        NoticeScene.SCHEDULER_EVENT,
        -> setOf(primaryChannel(osName))
    }

    private fun primaryChannel(osName: String): NoticeChannel =
        if (useInAppToast(osName)) NoticeChannel.IN_APP_TOAST else NoticeChannel.NATIVE_BALLOON
}
