package com.wuzhufolio.ui.regression

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B5① 守护：视觉回归**基线比对**的判定数学与「如实报告」语义（2026-10-10，`D47 §2.1`）。
 *
 * 用**合成图**（不依赖仓库基线、不依赖平台渲染）锁定三件事：
 * ① 逐像素最大通道差 / 差异像素 / 显著差异像素的计数口径；
 * ② 缺基线、尺寸不符、不可解码必须**显式成状态**（不静默当"通过"、不缩放猜测）；
 * ③ `report` 覆盖式写 `diff-<page>.md` 且缺基线的图如实计入 `NO_BASELINE`。
 */
class VisualDiffTest {

    private fun solid(width: Int, height: Int, rgb: Int): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
            for (y in 0 until height) for (x in 0 until width) setRGB(x, y, rgb)
        }

    private fun write(dir: File, name: String, image: BufferedImage): File =
        File(dir, name).apply { ImageIO.write(image, "png", this) }

    @Test
    fun `相同图像判为 MATCH 且差异为零`() {
        val dir = File("build/tmp/visual-diff-test/match").apply { mkdirs() }
        val image = solid(4, 3, 0xFF112233.toInt())
        val rendered = write(dir, "rendered.png", image)
        val baselines = File(dir, "baseline").apply { mkdirs() }
        write(baselines, "rendered.png", image)

        val row = VisualDiff.compare("rendered.png", rendered, baselines)

        assertEquals("MATCH", row.status)
        assertEquals(0, row.maxChannelDiff)
        assertEquals(0, row.diffPixels)
        assertEquals(0, row.strongPixels)
        assertEquals(12, row.totalPixels)
        assertEquals(0.0, row.diffRatio)
    }

    @Test
    fun `单像素漂移按最大通道差与占比如实计数`() {
        val dir = File("build/tmp/visual-diff-test/drift").apply { mkdirs() }
        val baselines = File(dir, "baseline").apply { mkdirs() }
        write(baselines, "rendered.png", solid(4, 4, 0xFF000000.toInt()))
        val rendered = solid(4, 4, 0xFF000000.toInt()).apply { setRGB(1, 1, 0xFF280000.toInt()) }
        val renderedFile = write(dir, "rendered.png", rendered)

        val row = VisualDiff.compare("rendered.png", renderedFile, baselines)

        // R 通道差 0x28 = 40 ⇒ 超过 PIXEL_TOLERANCE(8) 与 STRONG_TOLERANCE(32)
        assertEquals("DIFF", row.status)
        assertEquals(40, row.maxChannelDiff)
        assertEquals(1, row.diffPixels)
        assertEquals(1, row.strongPixels)
        assertEquals(16, row.totalPixels)
        assertEquals(1.0 / 16.0, row.diffRatio)
        // 1/16 = 6.25% ≥ 5% ⇒ 结构级（量级判据见 STRUCTURAL_DRIFT_RATIO）
        assertTrue(row.level.startsWith("结构级"), row.level)
    }

    @Test
    fun `容差内抖动不算差异`() {
        val dir = File("build/tmp/visual-diff-test/tolerance").apply { mkdirs() }
        val baselines = File(dir, "baseline").apply { mkdirs() }
        write(baselines, "rendered.png", solid(2, 2, 0xFF808080.toInt()))
        val rendered = write(
            dir,
            "rendered.png",
            solid(2, 2, 0xFF808080.toInt()).apply { setRGB(0, 0, 0xFF888888.toInt()) },
        )

        val row = VisualDiff.compare("rendered.png", rendered, baselines)

        // 通道差 8 = PIXEL_TOLERANCE ⇒ 视为同一像素（不掩盖真实漂移，只吸收舍入）
        assertEquals("MATCH", row.status)
        assertEquals(8, row.maxChannelDiff)
        assertEquals(0, row.diffPixels)
    }

    @Test
    fun `尺寸不符与缺基线都显式成状态而不猜测`() {
        val dir = File("build/tmp/visual-diff-test/states").apply { mkdirs() }
        val baselines = File(dir, "baseline").apply { mkdirs() }
        write(baselines, "sized.png", solid(4, 4, 0xFF000000.toInt()))
        val sized = write(dir, "sized.png", solid(4, 5, 0xFF000000.toInt()))

        val mismatch = VisualDiff.compare("sized.png", sized, baselines)
        assertEquals("SIZE_MISMATCH", mismatch.status)
        assertNull(mismatch.maxChannelDiff)
        assertTrue(mismatch.detail.contains("4x5") && mismatch.detail.contains("4x4"), mismatch.detail)

        val missing = VisualDiff.compare("not-there.png", sized, baselines)
        assertEquals("NO_BASELINE", missing.status)
        assertNull(missing.diffRatio)
    }

    @Test
    fun `report 覆盖式写 diff 报告且缺基线如实计入`() {
        val dir = File("build/tmp/visual-diff-test/report").apply { mkdirs() }
        val baselines = File(dir, "baseline").apply { mkdirs() }
        write(baselines, "with-baseline.png", solid(2, 2, 0xFF000000.toInt()))
        val files = listOf(
            write(dir, "with-baseline.png", solid(2, 2, 0xFF000000.toInt())),
            write(dir, "no-baseline.png", solid(2, 2, 0xFF000000.toInt())),
        )

        VisualDiff.report("unit", files, dir, baselines)

        val report = File(dir, "diff-unit.md")
        assertTrue(report.isFile, "报告应写入 diff-unit.md")
        val text = report.readText()
        assertTrue(text.contains("| with-baseline.png | MATCH |"), text)
        assertTrue(text.contains("| no-baseline.png | NO_BASELINE |"), text)
        assertTrue(text.contains("有基线 1/2 张"), text)
        assertTrue(text.contains("报告型"), text)
        assertTrue(text.contains("漂移量级"), text)
    }

    // --- T16.3（2026-10-16）：按平台基线 + 分级判定 ------------------------------------------------

    @Test
    fun `平台判定先于 Windows 且不吃 Darwin 的 win 子串`() {
        assertEquals("win", VisualDiff.platformId("Windows 11"))
        assertEquals("win", VisualDiff.platformId("Windows 10"))
        assertEquals("linux", VisualDiff.platformId("Linux"))
        // macOS 内核名 Darwin 含子串 "win" ⇒ 必须先判 mac，且 Windows 判据用 "windows"
        assertEquals("mac", VisualDiff.platformId("Mac OS X"))
        assertEquals("mac", VisualDiff.platformId("Darwin"))
        assertNull(VisualDiff.platformId("FreeBSD"))
    }

    @Test
    fun `分级阈值精确锁定（同平台噪声放行 跨平台量级阻断）`() {
        fun row(status: String, diffPixels: Int?, total: Int = 10_000) = VisualDiff.Row(
            name = "probe.png",
            status = status,
            maxChannelDiff = 40,
            meanAbsDiff = 1.0,
            diffPixels = diffPixels,
            strongPixels = 0,
            totalPixels = total,
        )

        // 放行区：≤ 0.05%（PLATFORM_NOISE_RATIO，含边界）
        assertEquals(VisualDiff.Verdict.WITHIN_NOISE, VisualDiff.verdict(row("DIFF", 5)))
        // 报告区：> 0.05% 且 ≤ 0.5%（BLOCKING_RATIO，含边界）
        assertEquals(VisualDiff.Verdict.REVIEW, VisualDiff.verdict(row("DIFF", 6)))
        assertEquals(VisualDiff.Verdict.REVIEW, VisualDiff.verdict(row("DIFF", 50)))
        // 阻断区：> 0.5%
        assertEquals(VisualDiff.Verdict.BLOCKING, VisualDiff.verdict(row("DIFF", 51)))
        assertEquals(VisualDiff.Verdict.BLOCKING, VisualDiff.verdict(row("DIFF", 1_000)))
        // 非 DIFF 状态各自成判定，绝不落入阈值比较
        assertEquals(VisualDiff.Verdict.MATCH, VisualDiff.verdict(row("MATCH", 0)))
        assertEquals(VisualDiff.Verdict.NO_BASELINE, VisualDiff.verdict(row("NO_BASELINE", null)))
        assertEquals(VisualDiff.Verdict.SIZE_MISMATCH, VisualDiff.verdict(row("SIZE_MISMATCH", null)))
        assertEquals(VisualDiff.Verdict.UNREADABLE, VisualDiff.verdict(row("UNREADABLE", null)))
    }

    @Test
    fun `阻断开关打开时超阈值抛 AssertionError 关闭时只报告`() {
        val dir = File("build/tmp/visual-diff-test/blocking").apply { mkdirs() }
        val baselines = File(dir, "baseline").apply { mkdirs() }
        // 8x8 全黑基线 vs 全白渲染 ⇒ 100% 差异像素（远超 BLOCKING_RATIO）
        write(baselines, "probe.png", solid(8, 8, 0xFF000000.toInt()))
        val files = listOf(write(dir, "probe.png", solid(8, 8, 0xFFFFFFFF.toInt())))

        // 关闭阻断：与 D47 口径一致，只写报告、不抛
        VisualDiff.report("off", files, dir, baselines, blocking = false)
        val offReport = File(dir, "diff-off.md").readText()
        assertTrue(offReport.contains("| probe.png | DIFF | BLOCKING |"), offReport)
        assertTrue(offReport.contains("阻断 1 张"), offReport)

        // 打开阻断：必须抛，且报文指明最差图与刷新步骤
        val error = assertFailsWith<AssertionError> {
            VisualDiff.report("on", files, dir, baselines, blocking = true)
        }
        assertTrue(error.message!!.contains("视觉回归阻断"), error.message!!)
        assertTrue(error.message!!.contains("probe.png"), error.message!!)
        assertTrue(error.message!!.contains("visual-baseline.md §4"), error.message!!)
    }

    @Test
    fun `平台基线未入库时不启用阻断（退回报告型）`() {
        // 未知平台 ⇒ 无平台基线目录 ⇒ 不阻断
        assertNull(VisualDiff.platformBaselineDir("FreeBSD"))
    }
}
