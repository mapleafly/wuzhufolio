package com.wuzhufolio.ui.tray

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.i18n.I18n
import com.wuzhufolio.ui.i18n.shellStrings
import com.wuzhufolio.ui.theme.WuzhuTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 托盘菜单（P6 人工门 DEF-15 三次修复）：菜单改由 Compose/Skia 自绘，**不再经过 AWT 菜单文本路径**。
 *
 * 本测试钉住：① 三个菜单项按当前语言正确渲染；② 点选分别触发对应动作并关闭；③ Esc 关闭。
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class TrayMenuContentUiTest {

    @AfterTest
    fun restoreLanguage() {
        I18n.set(AppLanguage.ZH)
    }

    private fun content(
        onOpen: () -> Unit = {},
        onSync: () -> Unit = {},
        onQuit: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ): @Composable () -> Unit = {
        WuzhuTheme(themeMode = ThemeMode.LIGHT, pnlScheme = PnlColorScheme.GREEN_UP) {
            TrayMenuContent(
                open = shellStrings.trayOpen,
                syncNow = shellStrings.traySyncNow,
                quit = shellStrings.trayQuit,
                onOpen = onOpen,
                onSync = onSync,
                onQuit = onQuit,
                onDismiss = onDismiss,
            )
        }
    }

    @Test
    fun `tray menu renders three items in the active language`() = runComposeUiTest {
        setContent(content())
        onNodeWithTag("tray-menu").assertIsDisplayed()
        onNodeWithText(shellStrings.trayOpen).assertIsDisplayed()
        onNodeWithText(shellStrings.traySyncNow).assertIsDisplayed()
        onNodeWithText(shellStrings.trayQuit).assertIsDisplayed()
    }

    @Test
    fun `tray menu items invoke their actions and dismiss`() = runComposeUiTest {
        var opened = 0
        var synced = 0
        var quit = 0
        var dismissed = 0
        setContent(content(onOpen = { opened++ }, onSync = { synced++ }, onQuit = { quit++ }, onDismiss = { dismissed++ }))

        onNodeWithTag("tray-menu-open").performClick()
        onNodeWithTag("tray-menu-sync").performClick()
        onNodeWithTag("tray-menu-quit").performClick()
        waitForIdle()
        assertEquals(1, opened, "「打开主界面」应触发一次")
        assertEquals(1, synced, "「立即同步」应触发一次")
        assertEquals(1, quit, "「退出」应触发一次")
        assertEquals(3, dismissed, "每次点选后应关闭菜单")
    }

    @Test
    fun `escape closes the tray menu`() = runComposeUiTest {
        var dismissed = 0
        setContent(content(onDismiss = { dismissed++ }))
        // 菜单容器打开即自取焦点（TrayMenuContent 内 LaunchedEffect），Esc 由容器处理
        onNodeWithTag("tray-menu").assertIsFocused()
        onNodeWithTag("tray-menu").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(1, dismissed, "Esc 应关闭托盘菜单")
    }
}
