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
import com.wuzhufolio.ui.theme.WuzhuTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 托盘菜单（P6 人工门 DEF-15 三次修复）：菜单改由 Compose/Skia 自绘，**不再经过 AWT 菜单文本路径**。
 *
 * 本测试钉住：① 四个菜单项按当前语言正确渲染（DEF-49 起增「立即刷新行情」）；② 点选分别触发对应动作并关闭；③ Esc 关闭。
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
        onRefresh: () -> Unit = {},
        onQuit: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ): @Composable () -> Unit = {
        val labels = trayLabels(AppLanguage.ZH)
        WuzhuTheme(themeMode = ThemeMode.LIGHT, pnlScheme = PnlColorScheme.GREEN_UP) {
            TrayMenuContent(
                open = labels.open,
                syncNow = labels.syncNow,
                refreshQuotes = labels.refreshQuotes,
                quit = labels.quit,
                onOpen = onOpen,
                onSync = onSync,
                onRefresh = onRefresh,
                onQuit = onQuit,
                onDismiss = onDismiss,
            )
        }
    }

    @Test
    fun `tray menu renders four items in the active language`() = runComposeUiTest {
        setContent(content())
        onNodeWithTag("tray-menu").assertIsDisplayed()
        val zh = trayLabels(AppLanguage.ZH)
        onNodeWithText(zh.open).assertIsDisplayed()
        onNodeWithText(zh.syncNow).assertIsDisplayed()
        // DEF-49：行情刷新与交易同步分列（两类 API 独立；此前菜单没有刷新入口）
        onNodeWithText(zh.refreshQuotes).assertIsDisplayed()
        onNodeWithText(zh.quit).assertIsDisplayed()
    }

    @Test
    fun `tray menu items invoke their actions and dismiss`() = runComposeUiTest {
        var opened = 0
        var synced = 0
        var refreshed = 0
        var quit = 0
        var dismissed = 0
        setContent(
            content(
                onOpen = { opened++ },
                onSync = { synced++ },
                onRefresh = { refreshed++ },
                onQuit = { quit++ },
                onDismiss = { dismissed++ },
            ),
        )

        onNodeWithTag("tray-menu-open").performClick()
        onNodeWithTag("tray-menu-sync").performClick()
        onNodeWithTag("tray-menu-refresh").performClick()
        onNodeWithTag("tray-menu-quit").performClick()
        waitForIdle()
        assertEquals(1, opened, "「打开主界面」应触发一次")
        assertEquals(1, synced, "「立即同步交易」应触发一次")
        assertEquals(1, refreshed, "「立即刷新行情」应触发一次（DEF-49）")
        assertEquals(1, quit, "「退出」应触发一次")
        assertEquals(4, dismissed, "每次点选后应关闭菜单")
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
