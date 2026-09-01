package com.wuzhufolio.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.shell.MainShell
import com.wuzhufolio.ui.shell.ShellPage
import com.wuzhufolio.ui.shell.ShellViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 主壳 UI 冒烟（T0.6）：五页导航可达、主题切换生效、组件走查页渲染。
 * Compose Desktop UI 测试离屏渲染（ImageComposeScene），CI 无显示环境可跑。
 */
@OptIn(ExperimentalTestApi::class)
class ShellUiTest {

    private fun newViewModel() = ShellViewModel(ThemeMode.LIGHT, PnlColorScheme.GREEN_UP)

    @Test
    fun `sidebar shows five primary entries and navigates`() = runComposeUiTest {
        val vm = newViewModel()
        setContent { MainShell(vm) }

        ShellPage.sidebarPages.forEach { page ->
            onNodeWithTag("nav-" + page.name).assertIsDisplayed()
        }
        onNodeWithTag("nav-SETTINGS").performClick()
        onNodeWithTag("page-SETTINGS").assertExists()
        assertEquals(ShellPage.SETTINGS, vm.page.value)
    }

    @Test
    fun `theme toggle switches between light and dark`() = runComposeUiTest {
        val vm = newViewModel()
        setContent { MainShell(vm) }

        onNodeWithTag("theme-toggle").performClick()
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
        onNodeWithTag("theme-toggle").performClick()
        assertEquals(ThemeMode.LIGHT, vm.themeMode.value)
        onNodeWithTag("main-shell").assertExists()
    }

    @Test
    fun `component gallery renders all sections`() = runComposeUiTest {
        val vm = newViewModel()
        setContent { MainShell(vm) }

        onNodeWithTag("nav-GALLERY").performClick()
        onNodeWithTag("component-gallery").assertIsDisplayed()
        onNodeWithTag("gallery-btn-primary").assertExists()
        onNodeWithTag("gallery-input").assertExists()
        onNodeWithTag("gallery-table").assertExists()
    }

    @Test
    fun `modal opens from gallery and dismisses`() = runComposeUiTest {
        val vm = newViewModel()
        setContent { MainShell(vm) }

        onNodeWithTag("nav-GALLERY").performClick()
        // 走查页可滚动，按钮在首屏视口外：先滚动到位再点击（performClick 不自动滚动）
        onNodeWithTag("gallery-open-modal").performScrollTo().performClick()
        // Dialog 内容位于独立语义子树，需要 useUnmergedTree 才能检索
        onNodeWithTag("gallery-modal", useUnmergedTree = true).assertExists()
        onNodeWithTag("gallery-modal-close", useUnmergedTree = true).performClick()
        onNodeWithTag("gallery-modal", useUnmergedTree = true).assertDoesNotExist()
    }
}
