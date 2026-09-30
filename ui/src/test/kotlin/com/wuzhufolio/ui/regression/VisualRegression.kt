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

    /** 影片素材目录（P7 宣传动画真源：`docs/release/promo/assets/ui/`）。 */
    private fun filmAssetDir(): File = File("../docs/release/promo/assets/ui").apply { mkdirs() }

    /**
     * **P7 影片素材采集**（2026-09-30 起，替代早期「从 P1 原型截图」的做法）：
     * 以 **2× 密度**渲染真实应用页面并写入影片素材目录，文件名与 `tools/build-promo.mjs` 的 MAP 对齐。
     *
     * 为什么用密度 2：影片把 UI 图版放大到接近全屏，1× 会糊；2×（1240×820 逻辑 → 2480×1640 像素）
     * 与 0.1.0 那版素材规格一致，可直接替换而不改模板尺寸。
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
        return files
    }
}
