package com.wuzhufolio.app.tray

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.io.InputStream

/**
 * 托盘菜单字体（P6 人工门 **DEF-15**：Windows 11 下托盘三行菜单文字乱码）。
 *
 * 背景：Compose Desktop 的 `Tray` 内部用 **AWT `PopupMenu`/`MenuItem`** 承载菜单
 * （已用 `javap` 在 `ui-desktop-1.12.0.jar` 的 `Tray_desktopKt` 中确认），因此托盘菜单文字
 * **不由应用内嵌字体渲染**，而由 AWT 逻辑字体（`Dialog`/`System`）交给 Windows 绘制。
 * 当该逻辑字体在目标机上缺少 CJK 覆盖（或 JDK 字体映射未挂到 CJK 回退族）时，中文即显示为乱码/方块。
 *
 * 修法：应用**自带**一份确定覆盖 CJK 的字体（`fonts/NotoSansSC.ttf`，与界面同源，OFL-1.1），
 * 显式挂到菜单项上（`MenuItem.font = ...`）——不再依赖目标机字体回退链。
 *
 * 纯函数 + 有兜底：加载失败/字体不覆盖菜单文字时回退到 AWT 逻辑字体 `Dialog`，
 * **绝不因字体问题阻断托盘注册**（最坏情况退回旧行为）。
 */
object TrayFont {

    /** 与界面同源的内嵌字体（可变字重 TTF；AWT 载入默认实例）。 */
    private const val BUNDLED_FONT_RESOURCE = "fonts/NotoSansSC.ttf"

    /** 菜单字号（pt）。与 Windows 原生菜单观感接近；DPI 由系统缩放处理。 */
    private const val MENU_SIZE = 12f

    /** 兜底逻辑字体名（AWT 恒定可用）。 */
    private const val FALLBACK_FAMILY = "Dialog"

    /** 探针文本：覆盖托盘菜单用到的字（中英 + 标点）。 */
    private const val PROBE = "打开主界面立即同步退出WuZhuFolio"

    private val cached: Font by lazy { load() }

    /** 菜单字体（进程内缓存）。 */
    fun menuFont(): Font = cached

    private fun load(): Font {
        val bundled = runCatching {
            resourceStream()?.use { stream ->
                Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(MENU_SIZE)
            }
        }.getOrNull()
        if (bundled != null && bundled.canDisplayUpTo(PROBE) < 0) return bundled
        // 兜底：优先找一个能覆盖探针文本的系统字体，否则用逻辑字体 Dialog
        val fallback = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .availableFontFamilyNames
            .asSequence()
            .map { Font(it, Font.PLAIN, MENU_SIZE.toInt()) }
            .firstOrNull { it.canDisplayUpTo(PROBE) < 0 }
        return fallback ?: Font(FALLBACK_FAMILY, Font.PLAIN, MENU_SIZE.toInt())
    }

    private fun resourceStream(): InputStream? =
        TrayFont::class.java.classLoader?.getResourceAsStream(BUNDLED_FONT_RESOURCE)
            ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(BUNDLED_FONT_RESOURCE)
}
