package com.wuzhufolio.ui.tray

import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.ui.i18n.I18n
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 托盘菜单文案语言（P6 人工门 **DEF-18**：界面切英文后托盘仍中文、重启也不变）。
 *
 * 根因：托盘菜单是**独立窗口**，其 `WuzhuTheme(themeMode = …)` 未传 language → 默认 ZH →
 * `WuzhuTheme` 会把全局 `I18n` 重置为中文；菜单文案若走全局读取器（`shellStrings`）便恒为中文。
 * 修法：菜单文案由 `trayLabels(当前语言)` 显式推导，主题也传入同一语言。
 *
 * 因此本测试的关键断言是：**即使全局 I18n 被重置为中文，按 EN 取词也必须得到英文**。
 */
class TrayLabelsTest {

    @AfterTest
    fun restoreLanguage() {
        I18n.set(AppLanguage.ZH)
    }

    private fun String.hasCjk(): Boolean = any { it.code in 0x4E00..0x9FFF }

    @Test
    fun `english tray labels stay english even after the global i18n was reset to chinese`() {
        I18n.set(AppLanguage.ZH) // 模拟托盘菜单窗口的主题调用把全局语言重置为中文
        val en = trayLabels(AppLanguage.EN)
        assertTrue(!en.open.hasCjk(), "英文档托盘「打开」不得为中文：${en.open}")
        assertTrue(!en.syncNow.hasCjk(), "英文档托盘「同步」不得为中文：${en.syncNow}")
        assertTrue(!en.quit.hasCjk(), "英文档托盘「退出」不得为中文：${en.quit}")
    }

    @Test
    fun `chinese tray labels stay chinese even after the global i18n was set to english`() {
        I18n.set(AppLanguage.EN) // 反向：全局英文时按 ZH 取词仍须中文
        val zh = trayLabels(AppLanguage.ZH)
        assertTrue(zh.open.contains("打开"), "中文档托盘应为中文：${zh.open}")
        assertTrue(zh.quit.contains("退出"), "中文档托盘应为中文：${zh.quit}")
    }
}
