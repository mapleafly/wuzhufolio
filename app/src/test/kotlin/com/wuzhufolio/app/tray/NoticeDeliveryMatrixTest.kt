package com.wuzhufolio.app.tray

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 「平台 × 场景 × 通道」投递矩阵守护用例（**`T16.1`** 建立；**`T16.2`** 收窄版扩到窗口内通道；
 * 规约 `AGENTS.md §7.3-13`；来源 **`DEF-49`** / **`DEF-64`** / **`DEF-65`**）。
 *
 * **矩阵文档 = `docs/design/desktop-notifications.md`**（记录每个「平台 × 场景」由**谁**渲染及代码锚点）；
 * 本用例是其**可执行部分**，钉死四条不变量：
 * 1. **每格恰好一条通道**（不得为空 ⇒ 不得静默不投递；不得为两条 ⇒ 不得重复打扰）；
 * 2. **按平台路由的场景（S1–S4）**：已发布平台族（Windows / Linux / macOS）一律走**应用内提示窗**——
 *    不依赖任何平台通知 API；未支持平台走**原生气泡**兜底（任何平台都不得静默）；
 * 3. **窗口内场景（S5–S9）**：通道**与平台无关**且与矩阵文档**逐格一致**（③ 窗口内 toast / ④ 页内文案）；
 * 4. **文档 ↔ 模型不漂移**：模型登记的 `docIds` 集合必须与矩阵文档主表的场景行一一对应。
 *
 * **为什么需要它**：通道按平台分叉却无对照表 ⇒ 静默失败**三次**——`DEF-49`（Linux 下 AWT 气泡无承载）、
 * `DEF-64`（非 Linux 平台**根本没投递**：`showNotice()` 只设状态，而渲染它的提示窗仅在 `inAppToast` 时组合）、
 * `DEF-65`（`T16.1` 复核：macOS 仍落在同一缺口，而 `D51` 后 macOS 已进发布范围）。
 * 断言一律**精确**（`§7.3-11`：`assertEquals(1, size)` / 精确集合相等 / 正则匹配整行，不用宽松判定）。
 *
 * **`T16.2` 的勘误留痕**：矩阵文档初版把 **S7**（设置页行级未登录提示）记为 ④ 页内文案，实测代码路径为
 * `ApiManagementViewModel.syncLoginGate`（`:182` / `:208`）→ `toast(...)` → `WzToastHost`
 * （`ui/.../exchange/ApiManagementSection.kt:105`）⇒ **③ 窗口内 toast**；本用例按实测口径断言，文档已同步勘误。
 *
 * **诚实边界**：本用例验的是**投递决策函数**（代码路径），**不是真机可见性**；且**无法**用单测证明
 * 「调用点确实声明了 `scene`」（无反射覆盖调用点）——该半段由 `AppHost.showNotice` 的**单一判定点**设计
 * （调用点只声明 `scene`，路由不分散）+ `manual-test-guide.md §23` 人工复验共同保证。
 * macOS 目前**无真机**（`D51` 已声明该残差风险；矩阵文档 §4 `G3` 登记）。
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

    /** 经 `AppHost.showNotice` **单一判定点**路由的场景（通道随平台变化）。 */
    private val platformRoutedScenes: List<NoticeScene> get() = NoticeScene.entries.filter { it.platformRouted }

    /** 由**主窗口内容层**自行渲染的场景（通道与平台无关）。 */
    private val windowScenes: List<NoticeScene> get() = NoticeScene.entries.filter { !it.platformRouted }

    /**
     * 窗口内场景的**精确期望通道**（与矩阵文档主表逐格一致；`§7.3-11` 禁近似）。
     *
     * 若某场景换通道，**必须**同时改本表、矩阵文档与 `NoticeDelivery.channelsFor` —— 三者不一致即红灯。
     */
    private val expectedWindowChannels: Map<NoticeScene, NoticeChannel> = mapOf(
        NoticeScene.TOPBAR_MANUAL_SYNC_TOAST to NoticeChannel.WINDOW_TOAST,
        NoticeScene.TOPBAR_QUOTE_REFRESH_TOAST to NoticeChannel.WINDOW_TOAST,
        NoticeScene.SETTINGS_SYNC_LOGIN_HINT to NoticeChannel.WINDOW_TOAST,
        NoticeScene.STARTUP_SECURITY_NOTICE to NoticeChannel.IN_PAGE_TEXT,
        NoticeScene.PROXY_STATUS_HINT to NoticeChannel.IN_PAGE_TEXT,
    )

    /** 不变量 1：**每格恰好一条通道**（场景 × 平台全遍历，含窗口内场景）。 */
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

    /** 不变量 2a：已发布平台族（Windows / Linux / macOS）× **按平台路由的场景** → **应用内提示窗**。 */
    @Test
    fun `shipped platforms deliver platform-routed scenes through the in-app toast`() {
        for ((family, names) in shippedPlatforms) {
            for (os in names) {
                for (scene in platformRoutedScenes) {
                    assertEquals(
                        setOf(NoticeChannel.IN_APP_TOAST),
                        NoticeDelivery.channelsFor(scene, os),
                        "$family 平台「$os」（$scene）必须经应用内提示窗投递" +
                            "（否则与 DEF-64 / DEF-65 同型：设了状态但没人渲染 ⇒ 静默无提示）",
                    )
                }
            }
        }
    }

    /** 不变量 2b：未列入支持的平台 × **按平台路由的场景** → **原生气泡兜底**（任何平台都不静默）。 */
    @Test
    fun `unknown platforms fall back to the native balloon on platform-routed scenes`() {
        for (os in unknownPlatforms) {
            for (scene in platformRoutedScenes) {
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
     * 场景不变性（**按平台路由的两场景现口径同路由**）：
     * `TRAY_MANUAL_ACTION`（S1–S3）与 `SCHEDULER_EVENT`（S4）必须解析一致。
     *
     * 若将来二者需要分叉，必须**同时**改 `NoticeDelivery.channelsFor` 的分叉、矩阵文档与本断言——本用例即该变更的闸门。
     */
    @Test
    fun `both platform-routed scenes resolve identically under the current routing policy`() {
        for (os in allPlatforms) {
            assertEquals(
                NoticeDelivery.channelsFor(NoticeScene.TRAY_MANUAL_ACTION, os),
                NoticeDelivery.channelsFor(NoticeScene.SCHEDULER_EVENT, os),
                "现口径下两种按平台路由的场景同路由；「$os」出现分叉时必须同步矩阵文档与本断言",
            )
        }
    }

    /**
     * 不变量 3a：**窗口内场景与平台无关**——同一场景在**所有**平台取值下通道必须完全相同。
     *
     * 这条不变量把「窗口内通道」钉死在主窗口内容层：它们**不应**因平台而异（也不应退化为气泡兜底）。
     */
    @Test
    fun `window scenes are platform independent`() {
        for (scene in windowScenes) {
            val baseline = NoticeDelivery.channelsFor(scene, allPlatforms.first())
            for (os in allPlatforms) {
                assertEquals(
                    baseline,
                    NoticeDelivery.channelsFor(scene, os),
                    "$scene 是窗口内场景，通道不得随平台变化（「$os」与「${allPlatforms.first()}」不一致）",
                )
            }
        }
    }

    /** 不变量 3b：窗口内场景的通道**与矩阵文档逐格一致**（精确期望表，`§7.3-11`）。 */
    @Test
    fun `window scenes resolve to the documented channel exactly`() {
        assertEquals(
            NoticeScene.entries.filter { !it.platformRouted }.toSet(),
            expectedWindowChannels.keys,
            "窗口内场景集合与期望表必须一一对应（新增/删除场景时同步本表与矩阵文档）",
        )
        for ((scene, expected) in expectedWindowChannels) {
            assertEquals(
                setOf(expected),
                NoticeDelivery.channelsFor(scene, allPlatforms.first()),
                "$scene 的通道必须与矩阵文档一致：期望 $expected",
            )
        }
    }

    /** 无死枚举：**每一种通道类型**都必须至少被一个「平台 × 场景」格使用（防枚举值被架空）。 */
    @Test
    fun `every channel type is reachable from at least one matrix cell`() {
        val reachable = buildSet {
            for (os in allPlatforms) {
                for (scene in NoticeScene.entries) {
                    addAll(NoticeDelivery.channelsFor(scene, os))
                }
            }
        }
        assertEquals(
            NoticeChannel.entries.toSet(),
            reachable,
            "存在从未被任何「平台 × 场景」使用的通道枚举值（死枚举）：差集 = ${NoticeChannel.entries.toSet() - reachable}",
        )
    }

    /**
     * 不变量 4：**文档 ↔ 模型不漂移**——模型登记的 `docIds` 必须与矩阵文档主表**逐行**对应，
     * 且 `S10` / `S11`（有意不建模：托盘不可用只记日志 / 非运行时工程提示）必须仍在文档中登记为 `—`。
     *
     * 说明：本用例**读文档**（`§7.3-13` 要求矩阵与实现同步，只有模型侧断言无法防「文档写漏」）。
     */
    @Test
    fun `matrix document declares every modeled scene`() {
        val doc = matrixDocText()
        val modeled = NoticeScene.entries.flatMap { it.docIds }.toSet()
        assertEquals(
            setOf("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9"),
            modeled,
            "模型登记的场景编号集合必须为 S1–S9（S10/S11 为有意不建模：见 NoticeScene KDoc）",
        )
        for (id in modeled.sorted()) {
            assertTrue(
                sceneRowPattern(id).containsMatchIn(doc),
                "矩阵文档主表缺少场景行 **$id** —— 模型已登记该场景，文档必须同步（§7.3-13①）",
            )
        }
        for (id in listOf("S10", "S11")) {
            assertTrue(
                sceneRowPattern(id).containsMatchIn(doc),
                "矩阵文档必须保留 **$id** 行（有意不建模，登记为「—」），不得因未建模而删除",
            )
        }
    }

    /**
     * 不变量 3c：**窗口内场景的文档单元格与模型通道一致**——逐行取整行，断言期望通道的序号字形
     * 在该行**恰好出现三次**（= 三个平台列各一格；少于三次 = 文档漏格，多于三次 = 混入其它通道）。
     *
     * 这条断言是 `T16.2` 的直接成果：文档初版把 **S7** 记为 ④，而代码实为 ③（`WzToastHost`）——
     * 「文档写一套、代码走另一套」正是 `§7.3-13` 要防的漂移，故用精确计数把它钉住。
     */
    @Test
    fun `window scene rows document the same channel as the model`() {
        val doc = matrixDocText()
        for ((scene, expected) in expectedWindowChannels) {
            for (id in scene.docIds) {
                val row = sceneRow(doc, id)
                val glyph = channelGlyph(expected)
                assertEquals(
                    3,
                    Regex.fromLiteral(glyph).findAll(row).count(),
                    "矩阵文档 **$id** 行必须为三个平台列各标一次「$glyph」（$expected）；实测行：${row.trim()}",
                )
            }
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

    /** 矩阵文档主表行的精确形态：`| **S1** | …`（整行锚定，非子串命中）。 */
    private fun sceneRowPattern(docId: String): Regex = Regex("""\|\s*\*\*$docId\*\*\s*\|""")

    /** 取矩阵文档中某场景的**整行**（找不到即失败：文档漏行属矩阵漂移，不得静默跳过）。 */
    private fun sceneRow(doc: String, docId: String): String =
        doc.lineSequence().firstOrNull { sceneRowPattern(docId).containsMatchIn(it) }
            ?: fail("矩阵文档缺少场景行 **$docId**")

    /** 通道 → 矩阵文档 §0/§1 使用的序号字形（① 应用内提示窗 / ② 原生气泡 / ③ 窗口内 toast / ④ 页内文案）。 */
    private fun channelGlyph(channel: NoticeChannel): String = when (channel) {
        NoticeChannel.IN_APP_TOAST -> "①"
        NoticeChannel.NATIVE_BALLOON -> "②"
        NoticeChannel.WINDOW_TOAST -> "③"
        NoticeChannel.IN_PAGE_TEXT -> "④"
    }

    /**
     * 读取矩阵文档正文（测试工作目录在不同调用方式下可能是仓库根或 `app/`，故按候选路径探测）。
     * 找不到即**失败**（不得静默跳过——跳过会让「文档 ↔ 模型」守护形同虚设）。
     */
    private fun matrixDocText(): String {
        val rel = "docs/design/desktop-notifications.md"
        val candidates = listOf(Path.of(rel), Path.of("..", rel), Path.of("..", "..", rel))
        val found = candidates.firstOrNull { Files.isRegularFile(it) }
            ?: fail("矩阵文档定位失败（已试：${candidates.joinToString()}）—— 守护用例必须能读到文档，否则矩阵与模型会静默漂移")
        return Files.readString(found)
    }
}
