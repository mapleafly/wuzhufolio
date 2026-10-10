package com.wuzhufolio.ui.regression

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.theme.WuzhuTheme
import java.io.File
import javax.imageio.ImageIO

/**
 * T14.5 逐页视觉回归工具（2026-09-29，D38 收口轮）。
 *
 * 目的：把「逐页 × 主题 × 分辨率」的走查从**人工点一遍**变成**可复现的自动化渲染**——
 * 每页在 3 档窗口尺寸 × 明/暗两主题下渲染，断言页面根已显示（崩溃/布局异常即测试红），
 * 并把渲染结果落盘为 PNG 供人工目视复核（产物目录 `ui/build/visual-regression/`，不入库）。
 *
 * 用法（在既有页面测试类内，复用其 fakes）：
 * ```
 * VisualRegression.capture("dashboard", "page-DASHBOARD") { DashboardPage(...) }
 * ```
 * 产物命名：`<page>-<light|dark>-<width>x<height>.png`。
 *
 * **B5①（2026-10-10，`D47`）**：`capture` 结束时会调 [VisualDiff.report] 出**基线比对摘要**
 * （`diff-<page>.md`，报告型不阻断；CI 归档 `ui/build/visual-regression/` 全部产物）。
 * **B5⑤（2026-10-10，`D47 §2.5`）**：[captureFilmAsset] 默认**不写仓库**（落 `build/promo-assets/`），
 * 仅 `WUZHUFOLIO_FILM_ASSETS=1` / `-Pwuzhufolio.filmAssets=true` 才写回 `docs/release/promo/assets/ui/`。
 */
@OptIn(ExperimentalTestApi::class)
internal object VisualRegression {

    /** 三档窗口尺寸（紧凑 / 默认 / 宽屏；与 D33 响应式口径一致）。 */
    val SIZES: List<DpSize> = listOf(
        DpSize(1024.dp, 720.dp),
        DpSize(1280.dp, 800.dp),
        DpSize(1600.dp, 1000.dp),
    )

    val THEMES: List<ThemeMode> = listOf(ThemeMode.LIGHT, ThemeMode.DARK)

    private fun outputDir(): File = File("build/visual-regression").apply { mkdirs() }

    /** 影片素材写回仓库的目标目录（P7 宣传动画真源：`docs/release/promo/assets/ui/`）。 */
    private fun filmAssetRepoDir(): File = File("../docs/release/promo/assets/ui")

    /** 门控关闭时的素材产物目录（`build/` 下，**不污染工作树**）。 */
    private fun filmAssetBuildDir(): File = File("build/promo-assets")

    /**
     * **B5⑤ 影片素材写回门控**（2026-10-10，`D47 §2.5`；全面评审 **F6**）。
     *
     * 默认 **关闭**：全量测试不再改写仓库内 `docs/release/promo/assets/ui/` 的 PNG（此前每次 `:ui:test`
     * 都重写 4 张，且**跨平台字节不同** ⇒ 工作树污染 + 「发布素材 ↔ 发布影片」失去一一对应）。
     * 仅显式开启时才写回仓库，供 P7 出片链路使用：
     * - 环境变量 `WUZHUFOLIO_FILM_ASSETS=1|true|yes|on`；或
     * - Gradle 属性 `-Pwuzhufolio.filmAssets=true`（由 `ui/build.gradle.kts` 转成同名系统属性）。
     *
     * 关闭时素材仍会渲染（用例断言不变），只是落到 `ui/build/promo-assets/` 并打印提示。
     */
    private fun filmAssetsEnabled(): Boolean {
        val env = System.getenv("WUZHUFOLIO_FILM_ASSETS")?.trim()?.lowercase()
        if (env != null && env in VisualDiff.TRUTHY) return true
        val prop = System.getProperty("wuzhufolio.filmAssets")?.trim()?.lowercase()
        return prop != null && prop in VisualDiff.TRUTHY
    }

    /** 影片素材目录（门控：默认 `build/promo-assets/`，显式开启才写仓库）。 */
    private fun filmAssetDir(): File {
        val enabled = filmAssetsEnabled()
        val dir = if (enabled) filmAssetRepoDir() else filmAssetBuildDir()
        if (!enabled) {
            println(
                "[film-assets] 写回仓库已关闭（B5⑤ 门控）：素材落 ${dir.path}；" +
                    "需更新 docs/release/promo/assets/ui/ 时用 WUZHUFOLIO_FILM_ASSETS=1 " +
                    "或 -Pwuzhufolio.filmAssets=true 开启",
            )
        }
        return dir.apply { mkdirs() }
    }

    /**
     * **P7 影片素材采集**（2026-09-30 起，替代早期「从 P1 原型截图」的做法）：
     * 以 **2× 密度**渲染真实应用页面并写入影片素材目录，文件名与 `tools/build-promo.mjs` 的 MAP 对齐。
     *
     * 为什么用密度 2：影片把 UI 图版放大到接近全屏，1× 会糊；2×（1240×820 逻辑 → 2480×1640 像素）
     * 与 0.1.0 那版素材规格一致，可直接替换而不改模板尺寸。
     *
     * **写回门控（B5⑤）**：默认写 `ui/build/promo-assets/`；仅显式开启（见 [filmAssetsEnabled]）
     * 才写回仓库 `docs/release/promo/assets/ui/`——出片链路 `tools/render-0.2.0.sh` 已自动开启。
     */
    fun captureFilmAsset(
        fileName: String,
        rootTag: String,
        content: @Composable () -> Unit,
    ): File {
        val logical = DpSize(1240.dp, 820.dp)
        var written: File? = null
        // 注意：`runSkikoComposeUiTest(size=…)` 的单位是**像素**，density = 像素/dp ⇒
        // 要得到 1240×820 逻辑窗口的 2× 图（2480×1640），size 必须传 2480×1640。
        runSkikoComposeUiTest(size = Size(2480f, 1640f), density = Density(2f)) {
            setContent {
                WuzhuTheme(themeMode = ThemeMode.LIGHT) {
                    Box(Modifier.size(logical)) { content() }
                }
            }
            onNodeWithTag(rootTag).assertIsDisplayed()
            val file = File(filmAssetDir(), fileName)
            ImageIO.write(onNodeWithTag(rootTag).captureToImage().toAwtImage(), "png", file)
            written = file
        }
        return requireNotNull(written) { "影片素材未写出：$fileName" }
    }

    /**
     * 在 [THEMES] × [SIZES] 下渲染 [content]：断言 [rootTag] 与 [alsoAssert] 各标签均已显示
     * （崩溃 / 关键元素缺失即测试红），并落盘截图。返回产物文件列表。
     */
    fun capture(
        page: String,
        rootTag: String,
        alsoAssert: List<String> = emptyList(),
        content: @Composable () -> Unit,
    ): List<File> {
        val files = mutableListOf<File>()
        for (theme in THEMES) {
            for (size in SIZES) {
                val name = "$page-${theme.name.lowercase()}-${size.width.value.toInt()}x${size.height.value.toInt()}"
                // runSkikoComposeUiTest 可指定渲染尺寸 ⇒ 截图即目标分辨率的真实渲染（非裁剪）
                // 尺寸单位为 px（density 默认 1 ⇒ px = dp，与页面断点口径一致）
                runSkikoComposeUiTest(size = Size(size.width.value, size.height.value)) {
                    setContent {
                        WuzhuTheme(themeMode = theme) {
                            Box(Modifier.size(size)) { content() }
                        }
                    }
                    val node = onNodeWithTag(rootTag)
                    node.assertIsDisplayed()
                    alsoAssert.forEach { onNodeWithTag(it).assertIsDisplayed() }
                    val file = File(outputDir(), "$name.png")
                    ImageIO.write(node.captureToImage().toAwtImage(), "png", file)
                    files += file
                }
            }
        }
        // B5① 基线比对（报告型，不阻断）：与 docs/design/baseline/ 同名基线逐像素比对，
        // 写 build/visual-regression/diff-<page>.md 并打到测试日志；缺基线的图如实记 NO_BASELINE。
        VisualDiff.report(page, files, outputDir())
        return files
    }
}
