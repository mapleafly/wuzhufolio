package com.wuzhufolio.ui.regression

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * B5① 视觉回归**基线比对**（截图 diff，2026-10-10，`D47`；**`T16.3` 升级为按平台基线 + 分级判定**，2026-10-16）。
 *
 * 口径演进：
 * - **`D47`（2026-10-10）报告型**：只产出差异摘要（写 `build/visual-regression/diff-<page>.md` + 打日志），不阻断；
 *   原因是**基线只有 6 张、且是另一平台渲染的** ⇒ Windows 本机 6 张全判 `DIFF`（0.34%–1.15%），无法作判据。
 * - **`T16.3`（2026-10-16）按平台基线 + 分级判定**（`AGENTS.md §7.3-10` 的「门禁不得依赖未声明副产物」与
 *   「断言必须精确」精神）：基线改为**按平台分目录**（`docs/design/baseline/<win|linux|mac>/`，各 **24 张真实 CI 渲染**），
 *   并在**同平台**比对超过 [BLOCKING_RATIO] 时**阻断**（抛异常，测试红）。
 *
 * **阈值标定证据（2026-10-16 实测，24 张 × 3 平台，见 `docs/design/visual-baseline.md §3`）**：
 * | 对比 | 字节相同 | >8 差异像素比（中位 / 最大） |
 * |---|---|---|
 * | 本机 Win vs CI Win（**同平台**） | 18/24 | 0.0000% / **0.0046%** |
 * | CI Win vs CI Linux / CI macOS / Linux vs macOS（**跨平台**） | 0/24 | 0.51%–0.61% / **1.56%–1.83%** |
 * | 仓库旧 6 张基线 vs CI Linux | **5/6** | 0.0000% / 0.0693% |
 * ⇒ 同平台噪声 ≤ **0.0046%**，跨平台地板 ≥ **0.51%** —— 两者相差 **100 倍以上**，故：
 * [PLATFORM_NOISE_RATIO] = **0.05%**（同平台噪声的 10 倍余量，放行）；
 * [BLOCKING_RATIO] = **0.5%**（跨平台地板的约 1/3，仅为此量级的漂移才阻断，避免误伤同平台抖动）；
 * 介于两者之间 ⇒ `REVIEW`（**仅报告**，供人工看 artifact）。
 *
 * **未收紧到 0.05% 阻断的原因（如实登记）**：`D47 §2.1` 的转阻断前置条件 ③「连续多轮 CI 无平台性抖动」
 * 目前只有 **Linux** 有跨运行证据（旧基线 vs 今日 CI Linux 5/6 字节相同）；Windows/macOS 尚未积累多轮数据。
 * 待「同一平台连续两轮 CI 的 `max(diffRatio) ≤ 0.01%`」成立后，可把 [BLOCKING_RATIO] 收紧至 [PLATFORM_NOISE_RATIO]。
 *
 * 其它不变量（沿用 `D47`）：
 * - **零新增依赖**：只用 JDK 自带 `javax.imageio` + `java.awt.image`；
 * - **匹配规则 = 文件名精确匹配**（`<page>-<light|dark>-<WxH>.png`）；缺基线记 `NO_BASELINE`（**如实报告**，不假装比对过）；
 * - **尺寸不一致记 `SIZE_MISMATCH`**（**不缩放、不猜测**——尺寸本身就是断言的一部分）；
 * - 环境变量 `WUZHUFOLIO_VISUAL_BASELINE_DIR` 覆盖时**只做报告不阻断**（覆盖目录可能是历史/试验基线）。
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
     * **同平台噪声上限**（`T16.3` 实测标定）：差异像素占比 ≤ 该值视为同平台渲染抖动，**放行**。
     * 依据：本机 Windows 与 CI Windows 在同代码状态下的 24 张对比，最大 0.0046% ⇒ 本值留 **10 倍余量**。
     */
    const val PLATFORM_NOISE_RATIO: Double = 0.0005

    /**
     * **阻断阈值**（`T16.3` 实测标定）：同平台比对差异像素占比 **>** 该值 ⇒ 判定 `BLOCKING`，测试失败。
     * 依据：跨平台差异地板实测 0.51%（CI Win vs CI Linux 的最小值）⇒ 取约 1/3 作为阻断线，避免误伤同平台抖动，
     * 同时能抓住「基线拿错平台 / 渲染大改 / 配色或布局漂移」这类真实回归。
     */
    const val BLOCKING_RATIO: Double = 0.005

    /**
     * **漂移量级**判据（`D47`，2026-10-10 标定）：差异像素占比 ≥ 该值 ⇒ 记「结构级」（疑似真实布局/配色漂移，
     * 需人工看 artifact）；低于该值 ⇒ 记「AA 级」（文字抗锯齿/次像素定位差异）。
     */
    const val STRUCTURAL_DRIFT_RATIO: Double = 0.05

    /** 允许写回仓库内影片素材的真值集合（B5⑤ 开关口径，与 [VisualRegression] 共用）。 */
    val TRUTHY: Set<String> = setOf("1", "true", "yes", "on")

    /** 平台标识（与基线目录名一致）：`win` / `linux` / `mac`；未知平台 ⇒ null。 */
    fun platformId(osName: String = System.getProperty("os.name").orEmpty()): String? {
        val normalized = osName.lowercase()
        return when {
            normalized.contains("windows") -> "win"
            normalized.contains("linux") -> "linux"
            // 注意：macOS 内核名 "Darwin" 含子串 "win" ⇒ 必须先判 mac，且判据用 "windows"（非 "win"）
            normalized.contains("mac") || normalized.contains("darwin") -> "mac"
            else -> null
        }
    }

    /** 按平台基线目录（`docs/design/baseline/<platform>`；测试 JVM 工作目录 = `ui/`）；未知平台 ⇒ null。 */
    fun platformBaselineDir(osName: String = System.getProperty("os.name").orEmpty()): File? =
        platformId(osName)?.let { File("../docs/design/baseline/$it") }

    /** 环境变量覆盖目录（给出时**不阻断**——覆盖目录可能是历史/试验基线）。 */
    fun overrideDir(): File? =
        System.getenv("WUZHUFOLIO_VISUAL_BASELINE_DIR")?.trim()?.takeIf { it.isNotEmpty() }?.let { File(it) }

    /** 实际使用的基线目录：环境覆盖 > 平台目录 > 历史扁平目录（`D42` 的 6 张）。 */
    fun baselineDir(): File = overrideDir() ?: platformBaselineDir() ?: File("../docs/design/baseline")

    /**
     * 是否启用**阻断**：未使用环境覆盖 **且** 平台基线目录存在（存在即视为「本平台基线已入库」）。
     * 未知平台 / 无平台目录 / 使用覆盖 ⇒ 退回 `D47` 的报告型口径。
     */
    fun blockingEnabled(osName: String = System.getProperty("os.name").orEmpty()): Boolean =
        overrideDir() == null && (platformBaselineDir(osName)?.isDirectory ?: false)

    /** 单张图的分级判定（`T16.3` 新增；阈值依据见类头）。 */
    enum class Verdict { MATCH, WITHIN_NOISE, REVIEW, BLOCKING, NO_BASELINE, SIZE_MISMATCH, UNREADABLE }

    /** 分级判定：`MATCH`/`WITHIN_NOISE` 放行 · `REVIEW` 仅报告 · `BLOCKING` 在启用阻断时使测试失败。 */
    fun verdict(row: Row): Verdict = when (row.status) {
        "MATCH" -> Verdict.MATCH
        "NO_BASELINE" -> Verdict.NO_BASELINE
        "SIZE_MISMATCH" -> Verdict.SIZE_MISMATCH
        "UNREADABLE" -> Verdict.UNREADABLE
        else -> when (val ratio = row.diffRatio ?: 0.0) {
            in 0.0..PLATFORM_NOISE_RATIO -> Verdict.WITHIN_NOISE
            in 0.0..BLOCKING_RATIO -> Verdict.REVIEW
            else -> {
                // 显式消掉未使用变量告警的冗余分支：ratio > BLOCKING_RATIO
                check(ratio > BLOCKING_RATIO)
                Verdict.BLOCKING
            }
        }
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
     * 对一页的 6 张渲染产物出比对报告：写 `diff-<page>.md`（覆盖式，避免跨页追加语义），把摘要打到 stdout；
     * **[blocking] 为真且存在 `BLOCKING` 行时抛 [AssertionError]**（`T16.3` 起：按平台基线阻断）。
     *
     * @param blocking 默认取 [blockingEnabled]（平台基线已入库且未被环境变量覆盖）
     */
    fun report(
        page: String,
        files: List<File>,
        outDir: File,
        baselineDir: File = baselineDir(),
        blocking: Boolean = blockingEnabled(),
    ) {
        val rows = files.map { compare(it.name, it, baselineDir) }
        val blockingRows = rows.filter { verdict(it) == Verdict.BLOCKING }
        File(outDir, "diff-$page.md").writeText(render(page, rows, baselineDir, blocking))
        val withBaseline = rows.count { it.status != "NO_BASELINE" }
        println(
            "[visual-diff] page=$page 有基线 $withBaseline/${rows.size} 张 · 阻断=${blocking} " +
                "· BLOCKING ${blockingRows.size} 张（报告 diff-$page.md）",
        )
        rows.forEach { println("[visual-diff] ${summary(it)}") }
        if (blocking && blockingRows.isNotEmpty()) {
            val worst = blockingRows.maxByOrNull { it.diffRatio ?: 0.0 }
            throw AssertionError(
                "视觉回归阻断（page=$page）：${blockingRows.size}/${rows.size} 张与**本平台**基线差异超阈值 " +
                    "(>${"%.2f".format(BLOCKING_RATIO * 100)}% 差异像素)。最差：${worst?.name} " +
                    "= ${"%.4f".format((worst?.diffRatio ?: 0.0) * 100)}%。\n" +
                    "  若为**有意的界面改动**：按 docs/design/visual-baseline.md §4 刷新本平台基线" +
                    "（跑 CI → 下载 visual-regression-<os> 工件 → 提交到 docs/design/baseline/<platform>/）。\n" +
                    "  若为**非预期漂移**：看 ui/build/visual-regression/diff-$page.md 与 CI 工件定位。",
            )
        }
    }

    /** 单行摘要（CI 日志用）。 */
    fun summary(row: Row): String {
        val verdictTag = "[${verdict(row)}]"
        return when {
            row.maxChannelDiff == null -> "$verdictTag ${row.name} ${row.status} | ${row.detail}"
            else -> "$verdictTag ${row.name} ${row.status} | 最大通道差=${row.maxChannelDiff} " +
                "平均=${"%.3f".format(row.meanAbsDiff)} 差异像素=${row.diffPixels}/${row.totalPixels} " +
                "(>${PIXEL_TOLERANCE} 占 ${"%.3f".format(row.diffRatio ?: 0.0)}) 显著差异(>${STRONG_TOLERANCE})=${row.strongPixels} " +
                "量级=${row.level}"
        }
    }

    private fun render(page: String, rows: List<Row>, baselineDir: File, blocking: Boolean): String {
        val withBaseline = rows.count { it.status != "NO_BASELINE" }
        val drifted = rows.count { it.status == "DIFF" }
        val blockingCount = rows.count { verdict(it) == Verdict.BLOCKING }
        return buildString {
            appendLine("# 视觉回归基线比对 · page=$page（B5①：报告型 + 按平台基线分级判定 · `T16.3`）")
            appendLine()
            appendLine("- 基线目录：`${baselineDir.path}`（`WUZHUFOLIO_VISUAL_BASELINE_DIR` 可覆盖；覆盖时**不阻断**）")
            appendLine("- 容差：同一像素 = 最大通道差 ≤ $PIXEL_TOLERANCE；显著差异 = 最大通道差 > $STRONG_TOLERANCE")
            appendLine(
                "- 分级：差异像素占比 ≤ ${"%.2f".format(PLATFORM_NOISE_RATIO * 100)}% 放行（同平台噪声）· " +
                    "≤ ${"%.2f".format(BLOCKING_RATIO * 100)}% 仅报告（REVIEW）· " +
                    "> ${"%.2f".format(BLOCKING_RATIO * 100)}% **阻断**（BLOCKING）",
            )
            appendLine("- 漂移量级：差异像素占比 ≥ ${"%.0f".format(STRUCTURAL_DRIFT_RATIO * 100)}% 记「结构级」（需人工看 artifact），否则记「AA 级」")
            appendLine("- 本页：有基线 $withBaseline/${rows.size} 张 · 判定漂移（DIFF）$drifted 张 · **阻断 $blockingCount 张**（本页阻断开关=$blocking）")
            appendLine("- 结论口径：**报告型 + 平台基线阻断**（`T16.3`）——同平台超阈值即红；基线刷新步骤见 `docs/design/visual-baseline.md §4`")
            appendLine()
            appendLine("| 图 | 结论 | 分级 | 漂移量级 | 最大通道差 | 平均通道差 | 差异像素(>$PIXEL_TOLERANCE) | 显著差异(>$STRONG_TOLERANCE) | 说明 |")
            appendLine("|----|------|------|---------|-----------|-----------|--------------------------|----------------------------|------|")
            rows.forEach { row ->
                appendLine(
                    "| ${row.name} | ${row.status} | ${verdict(row)} | ${row.level} | ${row.maxChannelDiff ?: "-"} | " +
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
