package com.wuzhufolio.ui.regression

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * B5① 视觉回归**基线比对**（截图 diff，2026-10-10，`D47`）。
 *
 * 口径（本轮，人工 2026-10-10 定级 B5 = C1）：
 * - **报告型、不阻断**：本对象**只产出差异摘要**（写入 `build/visual-regression/diff-<page>.md` 并打到测试日志），
 *   从不抛异常、不改测试结论 ⇒ 截图漂移只告警、不让 CI 红；
 * - **零新增依赖**：只用 JDK 自带 `javax.imageio.ImageIO` + `java.awt.image.BufferedImage`
 *   （不引入 Skiko/Skia 之外的任何库；PNG 解码走 JDK）；
 * - **基线来源**：默认 `docs/design/baseline/`（D42 视觉基准，仓库内 6 张真实渲染截图），
 *   可用环境变量 `WUZHUFOLIO_VISUAL_BASELINE_DIR` 覆盖；
 * - **匹配规则 = 文件名精确匹配**（`<page>-<light|dark>-<WxH>.png`）。渲染 24 张、基线 6 张 ⇒
 *   未覆盖的 18 张记 `NO_BASELINE`（**如实计入报告**，不假装比对过）；
 * - **尺寸不一致**记 `SIZE_MISMATCH`（**不缩放、不猜测**——尺寸本身就是断言的一部分）。
 *
 * 转「阻断」的前置条件（未满足前不得改判）：① 基线覆盖全部 24 张（4 页面 × 2 主题 × 3 尺寸）；
 * ② 容差经实测标定（`PIXEL_TOLERANCE` / `STRONG_TOLERANCE` 见下）；③ 连续多轮 CI 无平台性抖动。
 * 详见 `docs/dev/decisions/D47-B5工程项（CI门禁与规约固化）.md §2.1`。
 */
internal object VisualDiff {

    /**
     * 「同一像素」容差：逐像素取 R/G/B **最大通道差**，≤ 该值视为无差异。
     * 取 8 是为吸收 Skia 抗锯齿/次像素定位与不同平台的微小舍入，不掩盖真实布局/配色漂移。
     */
    const val PIXEL_TOLERANCE: Int = 8

    /** 「显著差异」阈值：用于统计**肉眼可见**的差异像素占比（与 [PIXEL_TOLERANCE] 一起给量级）。 */
    const val STRONG_TOLERANCE: Int = 32

    /**
     * **漂移量级**判据（2026-10-10 本机实测标定）：差异像素占比 ≥ 该值 ⇒ 记「结构级」（疑似真实布局/配色漂移，
     * 需人工看 artifact）；低于该值 ⇒ 记「AA 级」（文字抗锯齿/次像素定位差异）。
     *
     * 标定依据（Windows 本机 vs `docs/design/baseline/` 基线，2026-10-10）：6 张有基线图**全部**判 `DIFF`
     * ——差异像素占比 **0.34%–1.15%**、平均通道差 **0.22–0.41**、最大通道差 129–255（集中在文字边缘）
     * ⇒ 基线由**另一平台**渲染，AA 差异必然超过 8 的逐像素容差；**据此本轮只做报告型**，
     * 且「转阻断」前必须先在**同平台**重生成基线（或按平台分目录存基线），详见 `D47 §2.1`。
     */
    const val STRUCTURAL_DRIFT_RATIO: Double = 0.05

    /** 允许写回仓库内影片素材的真值集合（B5⑤ 开关口径，与 [VisualRegression] 共用）。 */
    val TRUTHY: Set<String> = setOf("1", "true", "yes", "on")

    /** 基线目录（默认 `docs/design/baseline`；测试 JVM 工作目录 = `ui/`）。 */
    fun baselineDir(): File {
        val override = System.getenv("WUZHUFOLIO_VISUAL_BASELINE_DIR")?.trim().orEmpty()
        return if (override.isEmpty()) File("../docs/design/baseline") else File(override)
    }

    /** 单张图的比对结论。 */
    data class Row(
        val name: String,
        val status: String,
        val maxChannelDiff: Int? = null,
        val meanAbsDiff: Double? = null,
        val diffPixels: Int? = null,
        val strongPixels: Int? = null,
        val totalPixels: Int? = null,
        val detail: String = "",
    ) {
        /** 差异像素占比（无基线时为 null）。 */
        val diffRatio: Double?
            get() = if (diffPixels == null || totalPixels == null || totalPixels == 0) null
            else diffPixels.toDouble() / totalPixels

        /** 漂移量级（仅对 `DIFF` 有意义；判据见 [STRUCTURAL_DRIFT_RATIO]）。 */
        val level: String
            get() = when {
                status != "DIFF" -> "-"
                (diffRatio ?: 0.0) >= STRUCTURAL_DRIFT_RATIO -> "结构级（≥5%，需人工看 artifact）"
                else -> "AA 级（<5%，文字抗锯齿/次像素）"
            }
    }

    private data class Metrics(
        val maxChannelDiff: Int,
        val meanAbsDiff: Double,
        val diffPixels: Int,
        val strongPixels: Int,
    )

    /**
     * 比对单张渲染产物与其同名基线；**任何情况都返回结论**（缺基线/不可解码/尺寸不符均为显式状态）。
     */
    fun compare(name: String, rendered: File, baselineDir: File = baselineDir()): Row {
        val baseline = File(baselineDir, name)
        val renderedImage = ImageIO.read(rendered)
        val baselineImage = if (baseline.isFile) ImageIO.read(baseline) else null
        return when {
            baselineImage == null -> Row(name, "NO_BASELINE", detail = "基线缺失：${baseline.path}")
            renderedImage == null -> Row(name, "UNREADABLE", detail = "渲染产物不可解码：${rendered.path}")
            baselineImage.width != renderedImage.width || baselineImage.height != renderedImage.height ->
                Row(
                    name = name,
                    status = "SIZE_MISMATCH",
                    detail = "渲染 ${renderedImage.width}x${renderedImage.height} " +
                        "vs 基线 ${baselineImage.width}x${baselineImage.height}",
                )
            else -> {
                val metrics = metrics(renderedImage, baselineImage)
                Row(
                    name = name,
                    status = if (metrics.maxChannelDiff <= PIXEL_TOLERANCE) "MATCH" else "DIFF",
                    maxChannelDiff = metrics.maxChannelDiff,
                    meanAbsDiff = metrics.meanAbsDiff,
                    diffPixels = metrics.diffPixels,
                    strongPixels = metrics.strongPixels,
                    totalPixels = renderedImage.width * renderedImage.height,
                    detail = baseline.path,
                )
            }
        }
    }

    /**
     * 对一页的 6 张渲染产物出比对报告：写 `diff-<page>.md`（覆盖式，避免跨页追加语义），并把摘要打到 stdout。
     */
    fun report(page: String, files: List<File>, outDir: File, baselineDir: File = baselineDir()) {
        val rows = files.map { compare(it.name, it, baselineDir) }
        File(outDir, "diff-$page.md").writeText(render(page, rows, baselineDir))
        val withBaseline = rows.count { it.status != "NO_BASELINE" }
        println("[visual-diff] page=$page 有基线 $withBaseline/${rows.size} 张（报告 diff-$page.md）")
        rows.forEach { println("[visual-diff] ${summary(it)}") }
    }

    /** 单行摘要（CI 日志用）。 */
    fun summary(row: Row): String = when {
        row.maxChannelDiff == null -> "${row.name} ${row.status} | ${row.detail}"
        else -> "${row.name} ${row.status} | 最大通道差=${row.maxChannelDiff} " +
            "平均=${"%.3f".format(row.meanAbsDiff)} 差异像素=${row.diffPixels}/${row.totalPixels} " +
            "(>${PIXEL_TOLERANCE} 占 ${"%.3f".format(row.diffRatio ?: 0.0)}) 显著差异(>${STRONG_TOLERANCE})=${row.strongPixels} " +
            "量级=${row.level}"
    }

    private fun render(page: String, rows: List<Row>, baselineDir: File): String {
        val withBaseline = rows.count { it.status != "NO_BASELINE" }
        val drifted = rows.count { it.status == "DIFF" }
        return buildString {
            appendLine("# 视觉回归基线比对 · page=$page（B5①，报告型不阻断）")
            appendLine()
            appendLine("- 基线目录：`${baselineDir.path}`（`WUZHUFOLIO_VISUAL_BASELINE_DIR` 可覆盖）")
            appendLine("- 容差：同一像素 = 最大通道差 ≤ $PIXEL_TOLERANCE；显著差异 = 最大通道差 > $STRONG_TOLERANCE")
            appendLine("- 漂移量级：差异像素占比 ≥ ${"%.0f".format(STRUCTURAL_DRIFT_RATIO * 100)}% 记「结构级」（需人工看 artifact），否则记「AA 级」")
            appendLine("- 本页：有基线 $withBaseline/${rows.size} 张 · 判定漂移（DIFF）$drifted 张")
            appendLine("- 结论口径：**报告型**——漂移不阻断 CI；基线更新步骤见 `docs/design/visual-baseline.md §2`")
            appendLine()
            appendLine("| 图 | 结论 | 漂移量级 | 最大通道差 | 平均通道差 | 差异像素(>$PIXEL_TOLERANCE) | 显著差异(>$STRONG_TOLERANCE) | 说明 |")
            appendLine("|----|------|---------|-----------|-----------|--------------------------|----------------------------|------|")
            rows.forEach { row ->
                appendLine(
                    "| ${row.name} | ${row.status} | ${row.level} | ${row.maxChannelDiff ?: "-"} | " +
                        "${row.meanAbsDiff?.let { "%.3f".format(it) } ?: "-"} | " +
                        "${row.diffPixels ?: "-"}${row.diffRatio?.let { "（${"%.2f".format(it * 100)}%）" } ?: ""} | " +
                        "${row.strongPixels ?: "-"} | ${row.detail} |",
                )
            }
        }
    }

    private fun metrics(a: BufferedImage, b: BufferedImage): Metrics {
        var maxChannelDiff = 0
        var sum = 0L
        var diffPixels = 0
        var strongPixels = 0
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                val diff = channelDiff(a.getRGB(x, y), b.getRGB(x, y))
                if (diff > maxChannelDiff) maxChannelDiff = diff
                sum += diff
                if (diff > PIXEL_TOLERANCE) diffPixels++
                if (diff > STRONG_TOLERANCE) strongPixels++
            }
        }
        val total = a.width.toLong() * a.height
        return Metrics(maxChannelDiff, sum.toDouble() / total, diffPixels, strongPixels)
    }

    private fun channelDiff(pixelA: Int, pixelB: Int): Int {
        val red = abs((pixelA shr 16 and 0xFF) - (pixelB shr 16 and 0xFF))
        val green = abs((pixelA shr 8 and 0xFF) - (pixelB shr 8 and 0xFF))
        val blue = abs((pixelA and 0xFF) - (pixelB and 0xFF))
        return maxOf(red, green, blue)
    }
}
