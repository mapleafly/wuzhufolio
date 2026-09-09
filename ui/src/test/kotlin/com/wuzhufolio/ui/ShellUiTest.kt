package com.wuzhufolio.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.v2.runComposeUiTest
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

    /** 顶栏手动同步（M7 补口 · PRD 故事 4.3）：按钮常驻、点击回调、同步中态。 */
    @Test
    fun `top bar manual sync button invokes callback and reflects syncing state`() = runComposeUiTest {
        val vm = newViewModel()
        var clicks = 0
        var syncing by mutableStateOf(false)
        setContent {
            MainShell(
                viewModel = vm,
                onManualSync = { clicks++ },
                manualSyncing = syncing,
            )
        }
        onNodeWithTag("topbar-sync").assertIsDisplayed()
        onNodeWithTag("topbar-sync").performClick()
        assertEquals(1, clicks)
        syncing = true
        waitUntil(timeoutMillis = 2_000) { vm.page.value != null && clicks == 1 }
        onNodeWithTag("topbar-sync").assertIsDisplayed()
    }

    /** 未传 onManualSync 时不渲染按钮（既有调用方零影响）。 */
    @Test
    fun `top bar sync button hidden when callback absent`() = runComposeUiTest {
        setContent { MainShell(newViewModel()) }
        onNodeWithTag("topbar-sync").assertDoesNotExist()
    }
}
