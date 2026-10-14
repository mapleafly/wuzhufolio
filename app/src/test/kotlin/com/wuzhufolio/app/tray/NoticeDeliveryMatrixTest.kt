package com.wuzhufolio.app.tray

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 「平台 × 场景 × 通道」投递矩阵守护用例（**T16.1**；规约 `AGENTS.md §7.3-13`；来源 **`DEF-49`** / **`DEF-64`**）。
 *
 * **矩阵文档 = `docs/design/desktop-notifications.md`**（记录每个「平台 × 场景」由**谁**渲染及代码锚点）；
 * 本用例是其**可执行部分**，钉死两条不变量：
 * 1. **每格恰好一条主通道**（不得为空 ⇒ 不得静默不投递；不得为两条 ⇒ 不得重复打扰）；
 * 2. **已发布平台族（Windows / Linux / macOS）一律走应用内提示窗**——不依赖任何平台通知 API。
 *
 * **为什么需要它**：通道按平台分叉却无对照表 ⇒ 静默失败**两次**——`DEF-49`（Linux 下 AWT 气泡无承载）与
 * `DEF-64`（非 Linux 平台**根本没投递**：`showNotice()` 只设状态，而渲染它的提示窗仅在 `inAppToast` 时组合）；
 * `T16.1` 复核又发现 **macOS 仍落在同一缺口**（原口径 `linux || windows`，而 `D51` 后 macOS 已进发布范围）。
 * 断言一律**精确**（`§7.3-11`：`assertEquals(1, size)` / 精确集合相等，不用 `> 0` 之类宽松判定）。
 *
 * **诚实边界**：本用例验的是**投递决策函数**（代码路径），**不是真机可见性**；且**无法**用单测证明
 * 「调用点确实把两个场景都传进来了」（无反射覆盖调用点）——该半段由 `AppHost.showNotice` 的**单一判定点**设计
 * （调用点只声明 `scene`，路由不分散）+ `manual-test-guide.md §23` 人工复验共同保证。
 * macOS 目前**无真机**（`D51` 已声明该残差风险）。
 */
class NoticeDeliveryMatrixTest {

    /**
     * 已进发布范围的平台族（`D51`，2026-10-14 起含 macOS）→ 采样 `os.name` 取值。
     *
     * 该表同时是「矩阵覆盖度」的守护对象：断言**三个族都存在且各有取值**，防止被静默删减（见末条用例）。
     */
    private val shippedPlatforms: Map<String, List<String>> = mapOf(
        "Windows" to listOf("Windows 11", "Windows 10", "windows"),
        "Linux" to listOf("Linux", "linux"),
        "macOS" to listOf("Mac OS X", "Mac OS X 14.5", "Darwin"),
    )

    /** 未列入支持的平台：走**原生气泡兜底**（兜底路径的守护；不得出现「两个通道都不走」）。 */
    private val unknownPlatforms: List<String> = listOf("FreeBSD 14", "SunOS", "OpenBSD 7.5", "")

    private val allPlatforms: List<String> get() = shippedPlatforms.values.flatten() + unknownPlatforms

    /** 不变量 1：**每格恰好一条通道**（场景 × 平台全遍历）。 */
    @Test
    fun `every matrix cell has exactly one delivery channel`() {
        for (os in allPlatforms) {
            for (scene in NoticeScene.entries) {
                val channels = NoticeDelivery.channelsFor(scene, os)
                assertEquals(
                    1,
                    channels.size,
                    "「$os」× $scene 必须恰好一条投递通道（0 条 = 静默不投递，2 条 = 重复打扰），实得 $channels",
                )
            }
        }
    }

    /** 不变量 2：已发布平台族（Windows / Linux / macOS）× 全部场景 → **应用内提示窗**。 */
    @Test
    fun `shipped platforms deliver through the in-app toast on every scene`() {
        for ((family, names) in shippedPlatforms) {
            for (os in names) {
                for (scene in NoticeScene.entries) {
                    assertEquals(
                        setOf(NoticeChannel.IN_APP_TOAST),
                        NoticeDelivery.channelsFor(scene, os),
                        "$family 平台「$os」（$scene）必须经应用内提示窗投递" +
                            "（否则与 DEF-64 同型：设了状态但没人渲染 ⇒ 静默无提示）",
                    )
                }
            }
        }
    }

    /** 未列入支持的平台 × 全部场景 → **原生气泡兜底**（保证「任何平台都不静默」）。 */
    @Test
    fun `unknown platforms fall back to the native balloon on every scene`() {
        for (os in unknownPlatforms) {
            for (scene in NoticeScene.entries) {
                assertEquals(
                    setOf(NoticeChannel.NATIVE_BALLOON),
                    NoticeDelivery.channelsFor(scene, os),
                    "未支持平台「$os」（$scene）必须走原生气泡兜底",
                )
            }
        }
    }

    /** 互斥性：应用内提示窗与原生气泡**恰好其一**（不重不漏；该恒等式是「恒非空」的机械保证）。 */
    @Test
    fun `routing modes are mutually exclusive per platform`() {
        for (os in allPlatforms) {
            assertEquals(
                NoticeDelivery.useInAppToast(os),
                !NoticeDelivery.needsNativeBalloonFallback(os),
                "「$os」的两种路由判定必须互补（且仅互补）",
            )
        }
    }

    /**
     * 场景不变性（**现口径**）：两种场景同路由。
     *
     * 若将来某场景需要专用通道，必须**同时**改 `NoticeDelivery.channelsFor` 的分叉、矩阵文档
     * （`docs/design/desktop-notifications.md`）与本断言——本用例即为该变更的闸门。
     */
    @Test
    fun `both scenes resolve identically under the current routing policy`() {
        for (os in allPlatforms) {
            assertEquals(
                NoticeDelivery.channelsFor(NoticeScene.TRAY_MANUAL_ACTION, os),
                NoticeDelivery.channelsFor(NoticeScene.SCHEDULER_EVENT, os),
                "现口径下两种场景同路由；「$os」出现分叉时必须同步矩阵文档与本断言",
            )
        }
    }

    /** 覆盖度守护：**三个已发布平台族**都必须被采样（防止矩阵被静默删减）。 */
    @Test
    fun `matrix covers all three shipped platform families`() {
        assertEquals(setOf("Windows", "Linux", "macOS"), shippedPlatforms.keys, "已发布平台族必须为 Windows/Linux/macOS 三族")
        for ((family, names) in shippedPlatforms) {
            assertTrue(names.isNotEmpty(), "$family 族必须至少有一个采样取值")
        }
        assertTrue(unknownPlatforms.isNotEmpty(), "必须保留至少一个未支持平台以覆盖兜底路径")
    }
}
