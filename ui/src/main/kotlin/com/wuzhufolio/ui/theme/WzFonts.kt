package com.wuzhufolio.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font as ResourceFont

/**
 * 内嵌字体族（design-tokens §3 落地，P3 验收修复轮）。
 *
 * 背景：Linux/WSL 普遍无 CJK 系统字体（实测 fc-list :lang=zh 为 0），不内嵌则中文渲染为豆腐块；
 * 分发到三平台均须自含字体。字库均为可变字体（variable font），按 FontVariation 实例化字重：
 * - Noto Sans SC（OFL-1.1）：正文/界面，对应 design-tokens Body 堆栈；
 * - Noto Serif SC（OFL-1.1）：Display 大数字，对应衬线堆栈；
 * - JetBrains Mono（OFL-1.1）：表格数字等宽，CJK 由 Noto Sans SC 兜底（回退链）。
 *
 * 体积注记：三字体共 ~43MB（app-image ~195MB）；P7 可用 pyftsubset 按字符集裁剪瘦身（登记 P7 优化项）。
 * 若某平台 FontVariation 不生效，渲染退化为默认字重（仍可读），P4 可换 fonttools 静态实例。
 */
private fun notoSans(weight: FontWeight) = ResourceFont(
    "fonts/NotoSansSC.ttf",
    weight,
    FontStyle.Normal,
    FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private fun notoSerif(weight: FontWeight) = ResourceFont(
    "fonts/NotoSerifSC.ttf",
    weight,
    FontStyle.Normal,
    FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private fun jetBrainsMono(weight: FontWeight) = ResourceFont(
    "fonts/JetBrainsMono.ttf",
    weight,
    FontStyle.Normal,
    FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** 正文/界面字体（Noto Sans SC 全字重族）。 */
val WzFontFamilyBody: FontFamily = FontFamily(
    notoSans(FontWeight.Normal),
    notoSans(FontWeight.Medium),
    notoSans(FontWeight.SemiBold),
    notoSans(FontWeight.Bold),
)

/** Display 衬线（Noto Serif SC）。 */
val WzFontFamilyDisplay: FontFamily = FontFamily(
    notoSerif(FontWeight.Normal),
    notoSerif(FontWeight.Medium),
    notoSerif(FontWeight.SemiBold),
)

/** 表格数字：JetBrains Mono 优先，CJK 回退 Noto Sans SC。 */
val WzFontFamilyTableNumber: FontFamily = FontFamily(
    jetBrainsMono(FontWeight.Normal),
    jetBrainsMono(FontWeight.Medium),
    notoSans(FontWeight.Normal),
    notoSans(FontWeight.Medium),
)
