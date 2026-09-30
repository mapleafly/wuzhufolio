package com.wuzhufolio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体层级（ADR-007 §2.2 / D38）。
 *
 * **D38 变更（2026-09-28）**：字号/行高/字距/字重**全部改用 Material 3 的 15 档 type scale**
 * （`TypeScaleTokens.kt`，M3 14_1_0 实测值：display 57/45/36 · headline 32/28/24 · title 22/16/14 ·
 * body 16/14/12 · label 14/12/11）。**字体族保留产品身份**：
 * - 数字/指标用**衬线**（Noto Serif SC）——账本感、数字对齐；
 * - 表格数字用**等宽**（JetBrains Mono + CJK 回退）+ `tnum`；
 * - 正文用无衬线（Noto Sans SC）。
 * M3 规范约束的是**层级（size/line-height/tracking/weight）**，字体族可由产品自定，故本口径既合规又保留识别度。
 *
 * [WzTypography] 是**业务语义槽位**（100+ 处调用点使用），从 M3 层级投影而来（见 [wzTypography]）——
 * 不新增字号档，避免"两套层级并存"（那正是改造前"界面不统一"的成因之一）。
 * 桌面数据密集场景取 M3 的 **medium/small 档**（正文 bodyMedium 14、卡内标题 titleSmall 14 Medium），
 * 层级由 headline/title 档拉开——**每一档都是 M3 规范值**，不是自定字号。
 */
@Immutable
data class WzTypography(
    /** 最大号数字（M3 `displaySmall` 36/44）。 */
    val display: TextStyle,
    /** 卡片一级指标（M3 `headlineMedium` 28/36）。 */
    val metricPrimary: TextStyle,
    /** 卡片次级指标（M3 `titleLarge` 22/28）。 */
    val metricSecondary: TextStyle,
    /** 页面标题（M3 `headlineSmall` 24/32）。 */
    val pageTitle: TextStyle,
    /** 分组/区段一级标题（M3 `titleMedium` 16/24 Medium）。 */
    val sectionTitle: TextStyle,
    /** 正文（M3 `bodyMedium` 14/20：桌面数据密集型界面的信息密度档）。 */
    val body: TextStyle,
    /** 强调正文/卡内二级标题（M3 `titleSmall` 14/20 Medium）。 */
    val bodyStrong: TextStyle,
    /** 表格数字（M3 `bodyMedium` 14/20 + 等宽 + tabular-nums；表格密度所需）。 */
    val tableNumber: TextStyle,
    /** 表头/标签（M3 `labelMedium` 12/16 Medium）。 */
    val tableHeader: TextStyle,
    /** 说明/时间戳（M3 `labelSmall` 11/16 Medium）。 */
    val caption: TextStyle,
)

/**
 * 由 M3 `Typography` 投影出业务语义槽位（[WzTypography]）；**每个槽位都对应一个 M3 层级**，
 * 唯一的产品化处理是 `tableNumber` 换等宽字族（表格数字对齐）。
 */
fun wzTypography(m3: Typography): WzTypography = WzTypography(
    display = m3.displaySmall.copy(fontFeatureSettings = "tnum"),
    metricPrimary = m3.headlineMedium.copy(fontFeatureSettings = "tnum"),
    metricSecondary = m3.titleLarge.copy(fontFeatureSettings = "tnum"),
    pageTitle = m3.headlineSmall,
    sectionTitle = m3.titleMedium,
    body = m3.bodyMedium,
    bodyStrong = m3.titleSmall,
    tableNumber = m3.bodyMedium.copy(
        fontFamily = WzFontFamilyTableNumber,
        fontFeatureSettings = "tnum",
    ),
    tableHeader = m3.labelMedium,
    caption = m3.labelSmall,
)

/** 字号常量（M3 type scale，供测试与文档引用；单位 sp）。 */
internal object M3TypeScale {
    const val DISPLAY_SMALL = 36
    const val HEADLINE_MEDIUM = 28
    const val TITLE_LARGE = 22
    const val HEADLINE_SMALL = 24
    const val TITLE_MEDIUM = 16
    const val BODY_LARGE = 16
    const val BODY_MEDIUM = 14
    const val LABEL_MEDIUM = 12
    const val LABEL_SMALL = 11
    val BODY_MEDIUM_LINE_HEIGHT = 20.sp
    val TITLE_MEDIUM_LINE_HEIGHT = 24.sp
}
