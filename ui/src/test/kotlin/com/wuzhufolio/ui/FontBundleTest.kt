package com.wuzhufolio.ui

import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontVariation
import org.jetbrains.skia.Typeface
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 内嵌字体守护（P3 验收修复轮）：字体文件随 ui jar 分发，须加载成功且覆盖 CJK。
 * 防止字体漏打包/损坏导致线上豆腐块回归。
 */
class FontBundleTest {

    private fun loadTypeface(path: String): Typeface {
        val bytes = javaClass.getResourceAsStream(path)?.readBytes()
            ?: error("font resource missing: " + path)
        return FontMgr.default.makeFromData(Data.makeFromBytes(bytes), 0)
            ?: error("cannot load typeface: " + path)
    }

    private fun assertCjkCovered(typeface: Typeface, fontName: String) {
        for (ch in listOf('中', '文', '账', '资')) {
            val glyph = typeface.getUTF32Glyph(ch.code)
            assertTrue(glyph.toInt() != 0, fontName + " missing glyph for " + ch)
        }
    }

    private fun assertWeightAxis(typeface: Typeface, fontName: String) {
        val axes = typeface.variationAxes
        assertNotNull(axes, fontName + " should be a variable font")
        assertTrue(axes.any { it.tag == "wght" }, fontName + " needs wght axis")
    }

    @Test
    fun `noto sans sc loads and covers CJK with weight axis`() {
        val tf = loadTypeface("/fonts/NotoSansSC.ttf")
        assertCjkCovered(tf, "NotoSansSC")
        assertWeightAxis(tf, "NotoSansSC")
    }

    @Test
    fun `noto serif sc loads and covers CJK with weight axis`() {
        val tf = loadTypeface("/fonts/NotoSerifSC.ttf")
        assertCjkCovered(tf, "NotoSerifSC")
        assertWeightAxis(tf, "NotoSerifSC")
    }

    @Test
    fun `jetbrains mono loads with digits and weight axis`() {
        val tf = loadTypeface("/fonts/JetBrainsMono.ttf")
        assertTrue(tf.getUTF32Glyph('8'.code).toInt() != 0, "JetBrainsMono missing digits")
        assertWeightAxis(tf, "JetBrainsMono")
    }

    @Test
    fun `variable instance clone with weight 600 works`() {
        val tf = loadTypeface("/fonts/NotoSansSC.ttf")
        val clone = tf.makeClone(arrayOf(FontVariation("wght", 600f)), 0)
        assertCjkCovered(clone, "NotoSansSC@600")
    }
}
