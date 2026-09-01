package com.wuzhufolio.ui

import androidx.compose.ui.graphics.Color
import com.wuzhufolio.ui.theme.WzColors
import com.wuzhufolio.ui.theme.darkWzColors
import com.wuzhufolio.ui.theme.lightWzColors
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * T0.6 验收守护：design-tokens §2.4——两主题分别验证，正文对比度 >= 4.5:1（WCAG AA）。
 * 相对亮度按 WCAG 公式计算；token 改动若跌破阈值本测试即红。
 */
class ContrastTest {

    private fun channelLuminance(v: Float): Double {
        val x = v.toDouble()
        return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(c: Color): Double =
        0.2126 * channelLuminance(c.red) + 0.7152 * channelLuminance(c.green) + 0.0722 * channelLuminance(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertAa(colors: WzColors, themeName: String) {
        val pairs = listOf(
            "ink" to colors.ink,
            "ink2" to colors.ink2,
            "ink3" to colors.ink3,
            "gain" to colors.gain,
            "loss" to colors.loss,
            "warn" to colors.warn,
            "accent" to colors.accent,
        )
        for ((name, fg) in pairs) {
            for ((bgName, bg) in listOf("bg" to colors.bg, "surface" to colors.surface)) {
                val ratio = contrast(fg, bg)
                assertTrue(
                    ratio >= WCAG_AA_NORMAL,
                    themeName + ": " + name + " on " + bgName + " = " + ratio + " < 4.5:1",
                )
            }
        }
    }

    @Test
    fun `light theme tokens meet WCAG AA on both backgrounds`() {
        assertAa(lightWzColors(), "light")
    }

    @Test
    fun `dark theme tokens meet WCAG AA on both backgrounds`() {
        assertAa(darkWzColors(), "dark")
    }

    companion object {
        const val WCAG_AA_NORMAL = 4.5
    }
}
