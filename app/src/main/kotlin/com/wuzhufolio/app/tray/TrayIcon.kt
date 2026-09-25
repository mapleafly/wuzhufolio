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
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * 应用标记（**托盘图标 + 主窗口图标**的单一真源；P7 起打包图标亦由同一几何生成）。
 *
 * 几何 = 「accent 底色圆角方 + 纸色衬线 W 折线」：自带底色，明暗两套系统托盘下均可见
 * （纯 accent 单色描边在深色托盘上会失对比）；色值取自 design-tokens §2.1。
 * 打包资产（`app/icons/wuzhufolio.{png,ico,icns}`）由 `scripts/generate-icons.mjs` 按**同一几何**生成
 * ——改这里的形状/颜色时，脚本里的常量要同步（脚本头注已声明该同源关系）。
 *
 * **2026-09-24 修复（DEF-48 / DEF-52①）**：
 * 1. **加透明安全边距**（[PADDING]）：此前是**满幅**绘制，缩到面板尺寸（GNOME 16–22px）时四角与笔画被裁，
 *    人工实测表现为「托盘图标显示不全整个图标」；
 * 2. **按平台策略出图**（[policy]）：AWT 在 Linux 走 XEmbed，图标经面板/SNI 代理转发，
 *    若给的是 64px 而宿主只要 22px，代理的缩放/裁剪行为不可控 —— 直接按宿主尺寸渲染最稳；
 * 3. **复用于主窗口图标**（`Window(icon = ...)`）：0.1.0 从未设置窗口图标（实测 `_NET_WM_ICON: not found`），
 *    GNOME 顶部栏/Dock 因此回落为通用齿轮图标。
 */
object TrayIcon {

    /** design-tokens §2.1 主版 accent（墨绿）。 */
    private val ACCENT = Color(0xFF1F5A48)

    /** design-tokens §2.1 主版 --accent-ink（暖纸）。 */
    private val PAPER = Color(0xFFF6F4EF)

    /** 默认位图边长（px）；宿主尺寸取不到时用它（平台托盘自行缩放到 16–22px，取 64 保证缩放后仍锐利）。 */
    const val DEFAULT_SIZE: Int = 64

    /** 主窗口图标边长（px）：比托盘大，任务栏/窗口切换器（含高 DPI）下不发糊。 */
    const val WINDOW_SIZE: Int = 128

    /**
     * 四周透明安全边距比例（相对边长；0.08 ≈ 小尺寸下四角不贴边、笔画不被裁）。
     * 打包图标脚本用 4%（Linux/Windows）/ 10%（macOS 网格），托盘/窗口取 8% 居中。
     */
    const val PADDING: Float = 0.08f

    /** 绘制应用标记（每次调用新建位图，调用方 remember 缓存）。 */
    fun painter(sizePx: Int = DEFAULT_SIZE, padding: Float = PADDING): Painter {
        val side = sizePx.toFloat()
        val inset = side * padding.coerceIn(0f, 0.3f)
        val box = side - inset * 2f
        val bitmap = ImageBitmap(sizePx, sizePx)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap),
            size = Size(side, side),
        ) {
            drawRoundRect(
                color = ACCENT,
                topLeft = Offset(inset, inset),
                size = Size(box, box),
                cornerRadius = CornerRadius(box * 0.22f),
            )
            drawPath(path = folioMark(inset, box), color = PAPER, style = markStroke(box))
        }
        return BitmapPainter(bitmap)
    }


    /**
     * **托盘专用实体位图**（DEF-48 二轮根因修复，2026-09-24）。
     *
     * 为什么不能直接把 [painter] 交给 AWT：Compose 的 `Painter.toAwtImage(...)` 返回的是**惰性**
     * `PainterImage`（不是 BufferedImage）。AWT 的 XEmbed 托盘窗口拿到它后按**自己的密度**栅格化，
     * 实测（Ubuntu 24.04 / 缩放假 2 / JDK 21）：AWT 报告 `trayIconSize=24`，XEmbed 窗口 32×32，
     * 但位图被按 ~2× 栅格化成 ~50px 后**按 1:1 画进 32px 窗口** → 只显示图标左上角一块，
     * 人工看到的就是「托盘图标显示不全」。
     *
     * 修法：先在 Compose 侧用 [ImageBitmap] 画成**确定尺寸**的位图，再经 `ImageBitmap.toAwtImage()`
     * 转成**实体 BufferedImage** 交给 AWT —— 尺寸完全由我们掌握，不再受惰性栅格化影响。
     */
    fun awtImage(sizePx: Int = policy().sizePx, padding: Float = PADDING): java.awt.image.BufferedImage {
        val side = sizePx.toFloat()
        val inset = side * padding.coerceIn(0f, 0.3f)
        val box = side - inset * 2f
        val bitmap = ImageBitmap(sizePx, sizePx)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap),
            size = Size(side, side),
        ) {
            drawRoundRect(
                color = ACCENT,
                topLeft = Offset(inset, inset),
                size = Size(box, box),
                cornerRadius = CornerRadius(box * 0.22f),
            )
            drawPath(path = folioMark(inset, box), color = PAPER, style = markStroke(box))
        }
        return bitmap.toAwtImage()
    }

    /**
     * 托盘位图策略：**给 AWT 的绘制像素尺寸** + **是否让 AWT 自动缩放**（含平台差异）。
     *
     * - [sizePx]：绘制的位图边长；-1 不用，取值见 [policy]。
     * - [imageAutoSize]：是否 `TrayIcon.setImageAutoSize(...)`。
     */
    data class Policy(val sizePx: Int, val imageAutoSize: Boolean)

    /**
     * 托盘策略（**平台分叉**，2026-09-24 真机取证后定稿）。
     *
     * **Linux / X11（XEmbed）**：`AWT 报告值 ÷ 屏幕缩放`，并 **关掉 autoSize**。
     * 依据（本机像素级实测矩阵，`docs/test/defects.md §2.2/§2.3`）：AWT 的 `trayIconSize` 是**逻辑**值（24），
     * XEmbed 图标窗口是**物理**像素（32 = 16 逻辑 × 缩放 2），而 AWT 画图时会**再乘一次屏幕缩放**
     * （按 24 给图 → 实画 ≈48 → 画进 32 像素窗口 → 右下被裁，即人工报的「托盘图标显示不全」）。
     * 给 `hint ÷ scale`（本机 16）则正好画满且完整。
     *
     * **Windows / macOS**：维持 0.1.0 的行为 —— 给 [DEFAULT_SIZE] 大图 + `autoSize = true`，
     * 由系统按 DPI 缩放到托盘尺寸（托盘走原生 HICON / NSStatusItem，不存在上面的二次缩放问题）。
     * **不要**把 Linux 的 `÷ 缩放 + autoSize=false` 用到这两个平台：高 DPI（125%/150%）下会得到偏小且发糊的图标。
     */
    fun policy(
        osName: String = System.getProperty("os.name").orEmpty(),
        hintPx: Int = systemTrayIconSizePx(),
        scale: Double = screenScale(),
    ): Policy = if (osName.lowercase().contains("linux")) {
        Policy(sizePx = (hintPx / scale.takeIf { it > 0.0 }!! ).toInt().coerceIn(16, 64), imageAutoSize = false)
    } else {
        Policy(sizePx = DEFAULT_SIZE, imageAutoSize = true)
    }

    /** AWT 报告的系统托盘图标边长（**逻辑**值；取不到回退 [DEFAULT_SIZE]）。 */
    fun systemTrayIconSizePx(): Int = runCatching {
        val size = java.awt.SystemTray.getSystemTray().trayIconSize
        maxOf(size.width, size.height)
    }.getOrDefault(DEFAULT_SIZE)

    /** 主屏缩放（取不到按 1.0）。 */
    fun screenScale(): Double = runCatching {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
    }.getOrDefault(1.0).takeIf { it > 0.0 } ?: 1.0

    /** 「W」折线（两个 V 相连——字标的几何抽象；4 段 5 点，坐标相对**有效绘制框**）。 */
    private fun folioMark(inset: Float, box: Float): Path = Path().apply {
        fun x(fraction: Float) = inset + box * fraction
        fun y(fraction: Float) = inset + box * fraction
        moveTo(x(0.26f), y(0.31f))
        lineTo(x(0.375f), y(0.70f))
        lineTo(x(0.50f), y(0.50f))
        lineTo(x(0.625f), y(0.70f))
        lineTo(x(0.74f), y(0.31f))
    }

    private fun markStroke(box: Float): Stroke = Stroke(
        width = box * 0.085f,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
}
