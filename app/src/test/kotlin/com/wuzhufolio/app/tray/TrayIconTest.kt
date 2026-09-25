package com.wuzhufolio.app.tray

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 应用标记的位图口径（**DEF-48**：托盘图标显示不全 / **DEF-52①**：窗口图标缺失）。
 *
 * 人工实测（Ubuntu 24.04）：托盘图标四角被裁（满幅绘制 + 宿主缩放）；窗口 `_NET_WM_ICON` 完全缺失
 * → GNOME 顶栏/Dock 显示通用齿轮。本测试钉住两条几何约束：
 * ① **透明安全边距**（四角像素 alpha = 0，图标不贴边）；
 * ② **标记存在**（中心十字区域有纸色折线/accent 底色，不是空图）；
 * ③ **尺寸参数生效**（托盘按宿主尺寸、窗口按 WINDOW_SIZE 出图）。
 */
class TrayIconTest {

    private fun bitmap(sizePx: Int, padding: Float = TrayIcon.PADDING): ImageBitmap = run {
        val painter = TrayIcon.painter(sizePx, padding)
        // BitmapPainter 的 intrinsicSize 与绘制尺寸一致；取像素图做几何断言
        val image = ImageBitmap(sizePx, sizePx)
        val canvas = androidx.compose.ui.graphics.Canvas(image)
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
            density = androidx.compose.ui.unit.Density(1f),
            layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
            canvas = canvas,
            size = androidx.compose.ui.geometry.Size(sizePx.toFloat(), sizePx.toFloat()),
        ) {
            with(painter) { draw(androidx.compose.ui.geometry.Size(sizePx.toFloat(), sizePx.toFloat())) }
        }
        image
    }

    @Test
    fun `corners are transparent thanks to the safe padding`() {
        val px = bitmap(64).toPixelMap()
        val last = 63
        assertEquals(0f, px[0, 0].alpha, "左上角应透明（安全边距）")
        assertEquals(0f, px[last, 0].alpha, "右上角应透明（安全边距）")
        assertEquals(0f, px[0, last].alpha, "左下角应透明（安全边距）")
        assertEquals(0f, px[last, last].alpha, "右下角应透明（安全边距）")
    }

    @Test
    fun `mark is drawn at the center`() {
        val px = bitmap(64).toPixelMap()
        // 中心竖线附近必须有 accent 底色（标记盒子内），且整体不是全透明
        val center = px[32, 32]
        assertTrue(center.alpha > 0.9f, "中心应被图标覆盖（alpha=${center.alpha}）")
        var opaque = 0
        for (x in 0 until 64) for (y in 0 until 64) if (px[x, y].alpha > 0.5f) opaque++
        assertTrue(opaque > 64 * 64 / 2, "图标应占据大部分画布（不透明像素 $opaque / 4096）")
    }


    /**
     * **DEF-48 二轮**（2026-09-24 真机矩阵实测后补）：交给 AWT 的必须是**实体位图**且尺寸可控。
     *
     * 背景：Compose 的 `Painter.toAwtImage(...)` 返回惰性 `PainterImage`，AWT 在缩放 2 的屏幕上会
     * 再乘一次缩放栅格化（24 → 48 物理像素）后 1:1 画进 32 像素的 XEmbed 窗口 → 右下被裁。
     * 现在改为 `TrayIcon.awtImage(px)`（ImageBitmap → BufferedImage），尺寸由我们掌握。
     */
    @Test
    fun `awt image is a concrete bitmap with the requested size and safe corners`() {
        val side = 24
        val img = TrayIcon.awtImage(side)
        assertEquals(side, img.width, "AWT 位图宽度必须等于请求值（不能再被缩放）")
        assertEquals(side, img.height)
        val alpha = { x: Int, y: Int -> (img.getRGB(x, y) ushr 24) and 0xFF }
        assertEquals(0, alpha(0, 0), "四角应透明（安全边距）")
        assertEquals(0, alpha(side - 1, side - 1), "四角应透明（安全边距）")
        assertTrue(alpha(side / 2, side / 2) > 200, "中心应被标记覆盖")
    }

    /**
     * **托盘策略平台分叉**（DEF-48 二轮）：Linux/X11 给「逻辑尺寸 + autoSize=false」；
     * Windows/macOS 维持「大图 + autoSize=true」（由系统按 DPI 缩放，避免高 DPI 下发糊偏小）。
     */
    @Test
    fun `tray policy is platform specific`() {
        // Linux：AWT 报告值 ÷ 屏幕缩放，夹取 16..64，且关掉 autoSize（否则 AWT 再乘一次缩放 → 被裁）
        assertEquals(TrayIcon.Policy(16, false), TrayIcon.policy("Linux", hintPx = 24, scale = 2.0))
        assertEquals(TrayIcon.Policy(22, false), TrayIcon.policy("Linux", hintPx = 22, scale = 1.0))
        assertEquals(TrayIcon.Policy(64, false), TrayIcon.policy("Linux", hintPx = 128, scale = 1.0))
        assertEquals(TrayIcon.Policy(16, false), TrayIcon.policy("Linux", hintPx = 12, scale = 1.0))
        // Windows / macOS：固定 64 + autoSize（0.1.0 行为不变，由系统按 DPI 缩放）
        assertEquals(TrayIcon.Policy(TrayIcon.DEFAULT_SIZE, true), TrayIcon.policy("Windows 11", 16, 1.5))
        assertEquals(TrayIcon.Policy(TrayIcon.DEFAULT_SIZE, true), TrayIcon.policy("Mac OS X", 22, 2.0))
    }

    @Test
    fun `window icon size constant is larger than the tray default`() {
        assertTrue(TrayIcon.WINDOW_SIZE > TrayIcon.DEFAULT_SIZE, "窗口图标应比托盘默认尺寸大")
        val px = bitmap(TrayIcon.WINDOW_SIZE).toPixelMap()
        assertEquals(0f, px[0, 0].alpha, "窗口图标同样保留安全边距")
    }
}
