package com.wuzhufolio.app.tray

/** 通知投递**通道**（`AGENTS.md §7.3-13` 矩阵的「通道」维；编号与矩阵文档 §0 一致）。 */
enum class NoticeChannel {
    /** **①** 应用内 Compose 提示窗（`DesktopToastWindow`）：独立窗口、托盘驻留态亦可见、**无平台通知 API 依赖**。 */
    IN_APP_TOAST,

    /** **②** 原生系统气泡（AWT `TrayIcon.displayMessage`，经 `AwtTrayHost.notify`）。 */
    NATIVE_BALLOON,

    /** **③** 窗口内 toast（`WzToast` / `WzToastHost`，主窗口内容层；**主窗口不可见时不可见**）。 */
    WINDOW_TOAST,

    /** **④** 页内文案 / 弹层（静态文本、`WzModal` 弹层、状态位；同样依赖主窗口可见）。 */
    IN_PAGE_TEXT,
}

/**
 * 通知**场景**（`§7.3-13` 矩阵的「场景」维）；[docIds] 与矩阵文档主表行号一一对应。
 *
 * [platformRouted] 区分两类投递路径——这是本模型的关键分野：
 * - **`true`（S1–S4）**：经 `AppHost.showNotice(next, scene)` 的**单一判定点**路由，通道**随平台变化**
 *   （已发布平台族 → ①；未支持平台 → ② 兜底）；
 * - **`false`（S5–S9）**：由**主窗口内容层自行渲染**，通道**与平台无关**（三平台同格同通道）。
 *
 * **有意不建模的场景**：`S10`（托盘不可用 ⇒ 只记日志，**设计如此**，不该弹提示）与 `S11`（工程提示：
 * CI 摘要与构建产物，**非运行时通道**）——二者在矩阵文档中登记为 `—`，不进入本模型；若将来其中任一
 * 需要真实投递通道，**必须**先在本枚举登记（连带 `docIds`）并同步矩阵文档与守护用例。
 */
enum class NoticeScene(
    /** 矩阵文档主表中的场景编号（`TRAY_MANUAL_ACTION` 覆盖三行：门禁 / 开始 / 结果）。 */
    val docIds: List<String>,
    /** 是否按平台路由（`true` = 经 `AppHost.showNotice` 判定点；`false` = 主窗口内容层自行渲染）。 */
    val platformRouted: Boolean,
) {
    /** **S1–S3** 托盘手动动作：「立即同步交易」/「立即刷新行情」的门禁提示、开始、结果。 */
    TRAY_MANUAL_ACTION(listOf("S1", "S2", "S3"), platformRouted = true),

    /** **S4** 后台调度事件（同步完成 / 备份提醒；经 `NoticePolicy` 两级开关过滤）。 */
    SCHEDULER_EVENT(listOf("S4"), platformRouted = true),

    /** **S5** 主窗口顶栏「手动同步」toast（`MainShell` 的 toast 槽位；与 S6 **共用槽位 ⇒ 互斥**）。 */
    TOPBAR_MANUAL_SYNC_TOAST(listOf("S5"), platformRouted = false),

    /** **S6** 主窗口顶栏「立刻刷新行情」toast（与 S5 共用槽位）。 */
    TOPBAR_QUOTE_REFRESH_TOAST(listOf("S6"), platformRouted = false),

    /**
     * **S7** 设置页（密钥区）行级「手动同步」未登录提示。
     *
     * **口径勘误（`T16.2`，2026-10-14）**：矩阵文档初版把本场景记为 ④ 页内文案；实测代码路径为
     * `ApiManagementViewModel.syncNow`（`:182`）/ `syncAll`（`:208`）→ `toast(WzToastKind.Failure, ApiCopy.LOGIN_REQUIRED)`
     * → `state.toast` → **`WzToastHost`（`ApiManagementSection.kt:105`）** ⇒ 实为 **③ 窗口内 toast**。
     */
    SETTINGS_SYNC_LOGIN_HINT(listOf("S7"), platformRouted = false),

    /** **S8** 启动安全提示（钥匙串 / 密钥文件异常）→ `AuthGate` 的 `WzModal` 弹层（页内）。 */
    STARTUP_SECURITY_NOTICE(listOf("S8"), platformRouted = false),

    /** **S9** 代理状态提示 → 主壳状态位（`WzStatusBar` 的 `proxyTooltip`）。 */
    PROXY_STATUS_HINT(listOf("S9"), platformRouted = false),
}

/**
 * 通知投递通道选择（**`DEF-49`** 2026-09-24 确立 → **`DEF-64`** 2026-10-13 修订 → **`T16.1`** 2026-10-14 三平台统一
 * → **`T16.2`** 2026-10-14 收窄版：**窗口内通道（S5–S9）纳入声明式矩阵**）。
 *
 * **矩阵文档 = `docs/design/desktop-notifications.md`**（记录每个「平台 × 场景」由**谁**渲染及代码锚点）；
 * 本对象是其**可执行部分**（守护用例 `NoticeDeliveryMatrixTest`）。
 *
 * ### 演进（三次同型缺陷的代价）
 * 0.1.0 只有一条通路 —— AWT `TrayIcon.displayMessage`（系统气泡）。
 * - **`DEF-49`（Linux）**：该 API 走 XEmbed，而 Ubuntu 24.04 的 GNOME 已改 StatusNotifierItem，气泡**无承载**
 *   （人工实测：托盘点「立即同步」后什么都看不到 → 误判为「没执行同步」）⇒ 引入应用内提示窗。
 * - **`DEF-64`（Windows，2026-10-13 走查 §22.5）**：`AppHost.showNotice()` **只设状态**，而渲染它的
 *   `DesktopToastWindow` 原先**仅在 Linux 组合** ⇒ Win/mac 的托盘手动动作提示**根本没投递**（连气泡也没走）。
 * - **`DEF-65`（macOS，2026-10-14 `T16.1` 复核）**：`DEF-64` 的修复把口径写成 `linux || windows`，**macOS 仍落在缺口里**
 *   （`D51` 后 macOS 已进发布范围 ⇒ 该缺口是发布前置）⇒ 三平台统一走应用内提示窗 + 未支持平台气泡兜底。
 *
 * ### 现口径
 * - **按平台路由的场景（S1–S4）**：**三个已发布平台族（Linux / Windows / macOS）统一走 ① 应用内提示窗** ——
 *   不依赖任何平台通知 API，行为三平台一致（该窗是**独立窗口**，托盘驻留态亦可见 —— `DEF-49` 已在 Linux 实机验证、
 *   `DEF-64` 修复后已在 Windows 打包版实机验证）；**未列出的平台**一律走 **② 原生气泡**兜底。
 * - **窗口内场景（S5–S9）**：通道**与平台无关**，由主窗口内容层自行渲染（③ 窗口内 toast / ④ 页内文案）——
 *   本模型只做**声明 + 断言**，**不参与其渲染**（`T16.2` 明确「不改交互行为」）。
 * - **兜底不静默**：`channelsFor` 因此**恒非空**——「设了状态但没人渲染」按**缺陷**对待（`§7.3-13②`）。
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
     * - `platformRouted` 场景（S1–S4）：应用内提示窗优先，否则原生气泡兜底；
     * - 窗口内场景（S5–S9）：**固定通道、与平台无关**（③ 或 ④；渲染在主窗口内容层）。
     *
     * 若将来某场景需要新通道或分叉，**在此分叉**，并同步更新矩阵文档与守护用例。
     */
    fun channelsFor(
        scene: NoticeScene,
        osName: String = System.getProperty("os.name").orEmpty(),
    ): Set<NoticeChannel> = when (scene) {
        NoticeScene.TRAY_MANUAL_ACTION,
        NoticeScene.SCHEDULER_EVENT,
        -> setOf(primaryChannel(osName))

        // ③ 窗口内 toast（主窗口内容层；`MainShell` 顶栏槽位 / 密钥区 `WzToastHost`）
        NoticeScene.TOPBAR_MANUAL_SYNC_TOAST,
        NoticeScene.TOPBAR_QUOTE_REFRESH_TOAST,
        NoticeScene.SETTINGS_SYNC_LOGIN_HINT,
        -> setOf(NoticeChannel.WINDOW_TOAST)

        // ④ 页内文案 / 弹层 / 状态位
        NoticeScene.STARTUP_SECURITY_NOTICE,
        NoticeScene.PROXY_STATUS_HINT,
        -> setOf(NoticeChannel.IN_PAGE_TEXT)
    }

    private fun primaryChannel(osName: String): NoticeChannel =
        if (useInAppToast(osName)) NoticeChannel.IN_APP_TOAST else NoticeChannel.NATIVE_BALLOON
}
