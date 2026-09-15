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
 * 层级（P6 人工门第五轮统一，见 design-tokens §3）：页面标题 20/600 > 分组一级标题 15/600（[sectionTitle]）
 * > 子区/行标签 14（600 = 子区标题 `bodyStrong`，400 = 行标签 `body`）> 说明 11（`caption`）。
 * 字体族定义见 WzFonts.kt（内嵌可变字体，OFL-1.1）。
 */
@Immutable
data class WzTypography(
    val display: TextStyle,
    /**
     * 卡片一级指标数字（原型 `.card .big` = 27px/600 衬线 + tnum；DEF-39 归一）。
     * 与 [metricSecondary] 构成**卡片指标两级**：一级/次级 = 27/21（≈1.29×），
     * 此前实现误映射为 display 32sp / bodyStrong 14sp（2.3×），人工反馈「第一行卡片文字明显偏大」。
     */
    val metricPrimary: TextStyle,
    /** 卡片次级指标数字（原型 `.card .big.sm` = 21px/600 衬线 + tnum）。 */
    val metricSecondary: TextStyle,
    val pageTitle: TextStyle,
    val sectionTitle: TextStyle,
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
    metricPrimary = TextStyle(
        fontFamily = WzFontFamilyDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        fontFeatureSettings = "tnum",
    ),
    metricSecondary = TextStyle(
        fontFamily = WzFontFamilyDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        fontFeatureSettings = "tnum",
    ),
    pageTitle = TextStyle(fontFamily = WzFontFamilyBody, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    // 分组/区段一级标题（设置页「通用」「数据管理」等；P6 人工门第五轮 DEF-24 统一口径）
    sectionTitle = TextStyle(fontFamily = WzFontFamilyBody, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
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
