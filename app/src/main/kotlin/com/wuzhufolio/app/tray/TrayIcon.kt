package com.wuzhufolio.app.tray

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * 托盘图标（M11 · T11.1 · design-tokens §5「托盘菜单」）。
 *
 * 现状 = **程序化徽章占位**：品牌在原型中是纯文字字标（`WuZhuFolio` 衬线 wordmark，无图形标识资产，
 * 见 design-tokens §1/§2 与 prototype 的 `.brand .logo`），P1 未定义图标资产。此处以
 * 「accent 底色圆角方 + 纸色衬线 W 折线」绘制：自带底色，明暗两套系统托盘下均可见
 * （纯 accent 单色描边在深色托盘上会失对比）。**正式图标资产待 P1 补/随 P7 打包图标一并定稿**
 * （模块记录 M11 §5 规格落档）。
 *
 * 程序化而非二进制资产的取舍：不向仓库引入未经设计评审的位图；绘制确定性、跨平台一致、
 * 可随 design token 变更同步（accent 值取自 design-tokens §2.1）。
 */
object TrayIcon {

    /** design-tokens §2.1 主版 accent（墨绿）。 */
    private val ACCENT = Color(0xFF1F5A48)

    /** design-tokens §2.1 主版 --accent-ink（暖纸）。 */
    private val PAPER = Color(0xFFF6F4EF)

    /** 默认位图边长（px）；平台托盘自行缩放到 16–22px，取 64 保证缩放后仍锐利。 */
    const val DEFAULT_SIZE: Int = 64

    /** 绘制托盘图标 painter（每次调用新建，调用方 remember 缓存）。 */
    fun painter(sizePx: Int = DEFAULT_SIZE): Painter {
        val side = sizePx.toFloat()
        val bitmap = ImageBitmap(sizePx, sizePx)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap),
            size = Size(side, side),
        ) {
            drawRoundRect(
                color = ACCENT,
                topLeft = Offset.Zero,
                size = Size(side, side),
                cornerRadius = CornerRadius(side * 0.22f),
            )
            drawPath(path = folioMark(side), color = PAPER, style = markStroke(side))
        }
        return BitmapPainter(bitmap)
    }

    /** 「W」折线（两个 V 相连——字标的几何抽象；4 段 5 点）。 */
    private fun folioMark(side: Float): Path = Path().apply {
        moveTo(side * 0.26f, side * 0.31f)
        lineTo(side * 0.375f, side * 0.70f)
        lineTo(side * 0.50f, side * 0.50f)
        lineTo(side * 0.625f, side * 0.70f)
        lineTo(side * 0.74f, side * 0.31f)
    }

    private fun markStroke(side: Float): Stroke = Stroke(
        width = side * 0.085f,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
}
