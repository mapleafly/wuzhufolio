package com.wuzhufolio.ui.shell

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.i18n.I18n
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 语言切换即时生效回归守护（M12 走查反馈修复轮 · 2026-09-11）。
 *
 * 缺陷背景：主壳此前用**启动期常量** `runtime.uiState.language` 驱动主题，设置页切换只落盘 →
 * 「切了没反应，重启才变」。修复后语言真源 = `ShellViewModel.language`（StateFlow），
 * 本用例断言：调用 `setLanguage` 后**不重建页面**即渲染为英文。
 */
@OptIn(ExperimentalTestApi::class)
class ShellLanguageSwitchUiTest {

    @AfterTest
    fun restore() {
        I18n.set(AppLanguage.ZH)
    }

    private fun shell(language: AppLanguage = AppLanguage.ZH) = ShellViewModel(
        initialTheme = ThemeMode.LIGHT,
        initialPnlScheme = PnlColorScheme.GREEN_UP,
        initialPage = ShellPage.DASHBOARD,
        initialLanguage = language,
    )

    private fun androidx.compose.ui.test.ComposeUiTest.texts(text: String): Int =
        onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun `switching the language re-renders the shell immediately`() = runComposeUiTest {
        val vm = shell()
        setContent { MainShell(viewModel = vm) }
        assertTrue(texts("仪表盘") > 0, "初始应为中文")
        assertEquals(0, texts("Dashboard"))

        vm.setLanguage(AppLanguage.EN)
        waitForIdle()

        assertTrue(texts("Dashboard") > 0, "切换后应立刻变为英文（无需重启）")
        assertEquals(0, texts("仪表盘"))
        assertEquals(AppLanguage.EN, I18n.current)
    }

    @Test
    fun `persisted language is applied on the next start`() = runComposeUiTest {
        // 反向：以英文初始档启动 → 首帧即英文（对应「重启后确实变了」的那一半行为）
        val vm = shell(AppLanguage.EN)
        setContent { MainShell(viewModel = vm) }
        assertTrue(texts("Dashboard") > 0)
        assertEquals(0, texts("仪表盘"))
    }
}
