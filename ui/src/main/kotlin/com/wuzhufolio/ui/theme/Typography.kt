package com.wuzhufolio.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * design-tokens.md §3 的单源 Compose 映射。
 * Display 用衬线（Noto Serif SC）+ tabular-nums（账本感、数字对齐）；
 * 数据表数字用等宽（JetBrains Mono，CJK 回退 Noto Sans SC）+ tnum；
 * 正文 14sp 默认（Noto Sans SC）；11sp 仅限 hint/表头/时间戳等辅助信息。
 * 字体族定义见 WzFonts.kt（内嵌可变字体，OFL-1.1）。
 */
@Immutable
data class WzTypography(
    val display: TextStyle,
    val pageTitle: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val tableNumber: TextStyle,
    val tableHeader: TextStyle,
    val caption: TextStyle,
)

fun wzTypography(): WzTypography = WzTypography(
    display = TextStyle(
        fontFamily = WzFontFamilyDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        fontFeatureSettings = "tnum",
    ),
    pageTitle = TextStyle(fontFamily = WzFontFamilyBody, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    body = TextStyle(fontFamily = WzFontFamilyBody, fontSize = 14.sp),
    bodyStrong = TextStyle(fontFamily = WzFontFamilyBody, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    tableNumber = TextStyle(
        fontFamily = WzFontFamilyTableNumber,
        fontSize = 12.5.sp,
        fontFeatureSettings = "tnum",
    ),
    tableHeader = TextStyle(
        fontFamily = WzFontFamilyBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
    ),
    caption = TextStyle(fontFamily = WzFontFamilyBody, fontSize = 11.sp),
)
