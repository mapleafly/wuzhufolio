package com.wuzhufolio.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * design-tokens.md §3 的单源 Compose 映射。
 * Display 用衬线 + tabular-nums（账本感、数字对齐）；数据表数字用等宽 + tnum；
 * 正文 14sp 默认；11sp 仅限 hint/表头/时间戳等辅助信息。
 * 字体回退：Serif/Monospace 系统族（打包内嵌 Noto Serif SC 为 P4/M12 事项）。
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
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        fontFeatureSettings = "tnum",
    ),
    pageTitle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    body = TextStyle(fontSize = 14.sp),
    bodyStrong = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    tableNumber = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.5.sp,
        fontFeatureSettings = "tnum",
    ),
    tableHeader = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.sp),
    caption = TextStyle(fontSize = 11.sp),
)
