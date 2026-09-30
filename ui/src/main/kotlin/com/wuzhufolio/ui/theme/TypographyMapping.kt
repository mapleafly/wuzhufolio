package com.wuzhufolio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 字体层级（15 档）——**数值取 M3 规范**（`TypeScaleTokens.kt`），字体族取产品字族。
 *
 * | M3 槽位 | size / lineHeight / tracking / weight |
 * |---|---|---|
 * | displayLarge / Medium / Small | 57/64 · 45/52 · 36/44（Regular） |
 * | headlineLarge / Medium / Small | 32/40 · 28/36 · 24/32（Regular） |
 * | titleLarge / Medium / Small | 22/28 · 16/24 · 14/20（Medium，tracking 0 / 0.2 / 0.1） |
 * | bodyLarge / Medium / Small | 16/24 · 14/20 · 12/16（Regular，tracking 0.5 / 0.2 / 0.4） |
 * | labelLarge / Medium / Small | 14/20 · 12/16 · 11/16（Medium，tracking 0.1 / 0.5 / 0.5） |
 *
 * **字体族**：数字/指标 = 衬线（`WzFontFamilyDisplay`，账本感）；表格数字 = 等宽（`WzFontFamilyTableNumber`）；
 * 其余 = 无衬线（`WzFontFamilyBody`）。M3 规范约束层级而非字族，故产品识别度得以保留。
 */
internal fun wzM3Typography(): Typography = Typography(
    // Display（衬线 + tabular-nums）
    displayLarge = numberStyle(57, 64, -0.2f),
    displayMedium = numberStyle(45, 52, 0f),
    displaySmall = numberStyle(36, 44, 0f),
    // Headline
    headlineLarge = numberStyle(32, 40, 0f),
    headlineMedium = numberStyle(28, 36, 0f),
    headlineSmall = bodyStyle(24, 32, 0f, FontWeight.Normal),
    // Title
    titleLarge = numberStyle(22, 28, 0f),
    titleMedium = bodyStyle(16, 24, 0.2f, FontWeight.Medium),
    titleSmall = bodyStyle(14, 20, 0.1f, FontWeight.Medium),
    // Body
    bodyLarge = bodyStyle(16, 24, 0.5f, FontWeight.Normal),
    bodyMedium = bodyStyle(14, 20, 0.2f, FontWeight.Normal),
    bodySmall = bodyStyle(12, 16, 0.4f, FontWeight.Normal),
    // Label
    labelLarge = bodyStyle(14, 20, 0.1f, FontWeight.Medium),
    labelMedium = bodyStyle(12, 16, 0.5f, FontWeight.Medium),
    labelSmall = bodyStyle(11, 16, 0.5f, FontWeight.Medium),
)

/** 数字/指标样式：衬线 + tabular-nums（tnum 让数字等宽、便于列对齐）。 */
private fun numberStyle(size: Int, lineHeight: Int, tracking: Float): TextStyle = TextStyle(
    fontFamily = WzFontFamilyDisplay,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = "tnum",
)

/** 正文/标题/标签样式：无衬线。 */
private fun bodyStyle(
    size: Int,
    lineHeight: Int,
    tracking: Float,
    weight: FontWeight,
): TextStyle = TextStyle(
    fontFamily = WzFontFamilyBody,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)
