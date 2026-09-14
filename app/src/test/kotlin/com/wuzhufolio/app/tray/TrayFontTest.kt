package com.wuzhufolio.app.tray

import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.ui.i18n.I18n
import com.wuzhufolio.ui.i18n.shellStrings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 托盘字体与文案（P6 人工门 DEF-15：Windows 11 托盘菜单中文乱码）。
 *
 * 根因：Compose `Tray` 的菜单由 **AWT PopupMenu** 渲染（`ui-desktop` 的 `Tray_desktopKt` 实证），
 * 菜单文字不由应用内嵌字体决定 → 目标机 AWT 逻辑字体缺 CJK 覆盖时中文乱码。
 * 修法：自建 AWT 托盘并把**内嵌 Noto Sans SC** 显式挂到菜单项上。
 *
 * 本测试钉住两件事：① 托盘字体确实覆盖菜单用字（含中文，不依赖系统字体）；
 * ② 托盘文案随界面语言切换（此前硬编码中文，英文界面下不跟随）。
 */
class TrayFontTest {

    @AfterTest
    fun restoreLanguage() {
        I18n.set(AppLanguage.ZH)
    }

    @Test
    fun `tray menu font covers chinese menu labels without system fallback`() {
        val font = TrayFont.menuFont()
        val probe = "打开主界面立即同步退出"
        assertTrue(
            font.canDisplayUpTo(probe) < 0,
            "托盘字体必须覆盖中文菜单文字（实际字体：${font.fontName} / ${font.family}）",
        )
        assertTrue(font.size >= 10, "菜单字号过小会影响可读性：${font.size}")
        // 内嵌字体族名应命中 Noto（若走了兜底说明资源未随应用分发）
        assertTrue(
            font.family.contains("Noto", ignoreCase = true) || font.fontName.contains("Noto", ignoreCase = true),
            "托盘字体应来自内嵌 Noto Sans SC（实际：${font.family}）——否则仍依赖目标机字体",
        )
    }

    @Test
    fun `tray labels follow the active language`() {
        I18n.set(AppLanguage.ZH)
        val zh = TrayLabels(shellStrings.trayOpen, shellStrings.traySyncNow, shellStrings.trayQuit)
        assertTrue(zh.open.contains("打开") && zh.quit.contains("退出"), "中文托盘文案：$zh")

        I18n.set(AppLanguage.EN)
        val en = TrayLabels(shellStrings.trayOpen, shellStrings.traySyncNow, shellStrings.trayQuit)
        assertTrue(
            en.open.none { it.code in 0x4E00..0x9FFF } && en.quit.none { it.code in 0x4E00..0x9FFF },
            "英文界面下托盘文案不得残留中文：$en",
        )
    }
}
