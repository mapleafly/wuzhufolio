package com.wuzhufolio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuzhufolio.domain.settings.PnlColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * ADR-007 回归：token → Material 3 全量映射。
 *
 * 守护点（改造前正是这里缺失，导致 M3 组件回落到默认紫、只能手绘组件）：
 * ① 关键 color role 必须取自产品 token，而不是 M3 默认值；
 * ② `Typography` / `Shapes` 必须下发（此前 `MaterialTheme` 只传 colorScheme）；
 * ③ 容器色阶（`surfaceContainer*`）落在 surface ↔ surface2 之间；
 * ④ spacing / motion token 阶梯单调，避免组件里再写魔数。
 */
class ThemeMappingTest {

    @Test
    fun `浅色与深色主题的关键 color role 均取自产品 token`() {
        listOf(true, false).forEach { dark ->
            val colors = if (dark) darkWzColors() else lightWzColors()
            val scheme = wzColorScheme(colors, dark)
            // 注：两个生成 object 无公共父类型，故逐项按主题取值（避免 `if/else` 推断成 Any）
            fun role(light: Color, darkRole: Color) = if (dark) darkRole else light
            assertEquals(colors.accent, scheme.primary, "primary 应取语义层 accent（dark=$dark）")
            assertEquals(colors.accentInk, scheme.onPrimary)
            assertEquals(colors.ink, scheme.onSurface)
            assertEquals(colors.ink2, scheme.onSurfaceVariant)
            assertEquals(colors.line, scheme.outlineVariant)
            assertEquals(colors.bg, scheme.background)
            assertEquals(role(M3LightRoles.surfaceVariant, M3DarkRoles.surfaceVariant), scheme.surfaceVariant)
            // M3 配色规范：容器/表面/描边/错误全部来自生成的 role（而非手写色）
            assertEquals(role(M3LightRoles.surfaceContainerLowest, M3DarkRoles.surfaceContainerLowest), scheme.surfaceContainerLowest)
            assertEquals(role(M3LightRoles.surfaceContainerHigh, M3DarkRoles.surfaceContainerHigh), scheme.surfaceContainerHigh)
            assertEquals(role(M3LightRoles.outline, M3DarkRoles.outline), scheme.outline)
            assertEquals(
                role(M3LightRoles.error, M3DarkRoles.error),
                scheme.error,
                "error 固定取 M3 error role（D38：不再随盈亏方案翻转）",
            )
            assertEquals(role(M3LightRoles.inverseSurface, M3DarkRoles.inverseSurface), scheme.inverseSurface)
            assertEquals(role(M3LightRoles.primaryContainer, M3DarkRoles.primaryContainer), scheme.primaryContainer)
            assertEquals(role(M3LightRoles.secondary, M3DarkRoles.secondary), scheme.secondary)
            assertEquals(role(M3LightRoles.tertiary, M3DarkRoles.tertiary), scheme.tertiary)
            assertEquals(role(M3LightRoles.scrim, M3DarkRoles.scrim), scheme.scrim)
            // 关键：不得等于 M3 默认紫（改造前的症状）
            assertNotEquals(Color(0xFF6650a4), scheme.primary, "primary 不得回落到 M3 默认紫")
        }
    }

    @Test
    fun `M3 表面色阶单调（浅色逐级变暗、深色逐级变亮）`() {
        fun lum(c: Color) = 0.2126 * c.red + 0.7152 * c.green + 0.0722 * c.blue
        // 浅色：lowest（最亮）→ highest（最暗）
        val light = listOf(
            M3LightRoles.surfaceContainerLowest, M3LightRoles.surfaceContainerLow,
            M3LightRoles.surfaceContainer, M3LightRoles.surfaceContainerHigh,
            M3LightRoles.surfaceContainerHighest,
        ).map(::lum)
        assertTrue(light.zipWithNext().all { (a, b) -> a > b }, "浅色色阶应逐级变暗：$light")
        // 深色：lowest（最暗）→ highest（最亮）
        val dark = listOf(
            M3DarkRoles.surfaceContainerLowest, M3DarkRoles.surfaceContainerLow,
            M3DarkRoles.surfaceContainer, M3DarkRoles.surfaceContainerHigh,
            M3DarkRoles.surfaceContainerHighest,
        ).map(::lum)
        assertTrue(dark.zipWithNext().all { (a, b) -> a < b }, "深色色阶应逐级变亮：$dark")
        // 语义层投影：应用底 / 卡片底 / 次表面各取一档，且深浅方向正确
        assertTrue(lum(lightWzColors().surface) > lum(lightWzColors().bg))
        assertTrue(lum(darkWzColors().surface) > lum(darkWzColors().bg))
    }

    @Test
    fun `盈亏配色方案只翻转 gain_loss，不动主色与错误色`() {
        val green = lightWzColors()
        val red = green.withPnlScheme(PnlColorScheme.RED_UP)
        val schemeGreen = wzColorScheme(green, dark = false)
        val schemeRed = wzColorScheme(red, dark = false)
        // gain/loss 互换
        assertEquals(green.gain, red.loss)
        assertEquals(green.loss, red.gain)
        // 主色与错误色不受盈亏方案影响（D38：此前 error = loss，切红涨绿跌会让校验红字变绿）
        assertEquals(schemeGreen.primary, schemeRed.primary)
        assertEquals(schemeGreen.error, schemeRed.error)
        assertEquals(M3LightRoles.error, schemeRed.error)
    }

    @Test
    fun `Typography 采用 M3 规范层级且业务槽位由其投影`() {
        val m3 = wzM3Typography()
        val t = wzTypography(m3)
        // M3 type scale 数值（TypeScaleTokens，M3 14_1_0）
        assertEquals(16.sp, m3.bodyLarge.fontSize, "正文 = M3 bodyLarge 16sp")
        assertEquals(24.sp, m3.bodyLarge.lineHeight)
        assertEquals(14.sp, m3.bodyMedium.fontSize)
        assertEquals(12.sp, m3.bodySmall.fontSize)
        assertEquals(36.sp, m3.displaySmall.fontSize)
        assertEquals(28.sp, m3.headlineMedium.fontSize)
        assertEquals(11.sp, m3.labelSmall.fontSize)
        // 业务槽位 = M3 层级的投影（不再有第二套字号）
        assertEquals(m3.displaySmall.fontSize, t.display.fontSize)
        assertEquals(m3.headlineMedium.fontSize, t.metricPrimary.fontSize)
        assertEquals(m3.titleLarge.fontSize, t.metricSecondary.fontSize)
        assertEquals(m3.headlineSmall.fontSize, t.pageTitle.fontSize)
        assertEquals(m3.titleMedium.fontSize, t.sectionTitle.fontSize)
        // 桌面数据密集档：正文取 M3 bodyMedium(14)、强调/卡内标题取 titleSmall(14 Medium)
        assertEquals(m3.bodyMedium.fontSize, t.body.fontSize, "正文 = M3 bodyMedium 14sp")
        assertEquals(m3.titleSmall.fontSize, t.bodyStrong.fontSize, "强调正文 = M3 titleSmall 14sp")
        assertEquals(m3.bodyMedium.fontSize, t.tableNumber.fontSize)
        assertEquals(m3.labelMedium.fontSize, t.tableHeader.fontSize)
        assertEquals(m3.labelSmall.fontSize, t.caption.fontSize)
        // 层级自证（D32 口径）：卡内二级标题必须 <= 分组一级标题
        assertTrue(t.bodyStrong.fontSize <= t.sectionTitle.fontSize)
    }

    @Test
    fun `Shapes 采用 M3 规范阶梯`() {
        val shapes = wzShapes()
        // M3 ShapeTokens：4 / 8 / 12 / 16 / 28 dp
        assertEquals(RoundedCornerShape(4.dp), shapes.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), shapes.small)
        assertEquals(RoundedCornerShape(12.dp), shapes.medium)
        assertEquals(RoundedCornerShape(16.dp), shapes.large)
        assertEquals(RoundedCornerShape(28.dp), shapes.extraLarge)
    }

    @Test
    fun `spacing 与 motion token 阶梯有序`() {
        val spacing = listOf(
            WzSpacing.hairline, WzSpacing.xxs, WzSpacing.xs, WzSpacing.sm, WzSpacing.md,
            WzSpacing.lg, WzSpacing.xl, WzSpacing.xxl, WzSpacing.xxxl,
        )
        assertTrue(spacing.zipWithNext().all { (a, b) -> a < b }, "spacing 阶梯必须严格递增：$spacing")
        assertEquals(4.dp, WzSpacing.xxs)
        assertEquals(16.dp, WzSpacing.lg)

        // M3 motion tokens：short1–4 < medium1–4 < long1–4 < extraLong1–4
        assertTrue(WzMotion.SHORT_1 < WzMotion.SHORT_4)
        assertTrue(WzMotion.SHORT_4 < WzMotion.MEDIUM_1)
        assertTrue(WzMotion.MEDIUM_4 < WzMotion.LONG_1)
        assertTrue(WzMotion.LONG_4 < WzMotion.EXTRA_LONG_1)
        assertEquals(50, WzMotion.SHORT_1)
        assertEquals(200, WzMotion.SHORT_4)
        assertEquals(1000, WzMotion.EXTRA_LONG_4)
    }
}
