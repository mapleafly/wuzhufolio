package com.wuzhufolio.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.shell.MainShell
import com.wuzhufolio.ui.shell.ShellViewModel
import kotlin.test.Test

/**
 * M1 T1.1「无钥匙串降级提示」：startupNotice 非空时主壳弹出安全提示模态，可确认关闭；为空时不弹。
 */
@OptIn(ExperimentalTestApi::class)
class StartupNoticeUiTest {

    private fun newViewModel() = ShellViewModel(ThemeMode.LIGHT, PnlColorScheme.GREEN_UP)

    @Test
    fun `startup notice modal shows and dismisses on confirm`() = runComposeUiTest {
        setContent {
            MainShell(
                viewModel = newViewModel(),
                startupNotice = "系统钥匙串不可用，数据库密钥已降级存入本地密钥文件。",
            )
        }
        onNodeWithTag("startup-notice", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("startup-notice-ok", useUnmergedTree = true).performClick()
        onNodeWithTag("startup-notice", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `no notice when startupNotice is null`() = runComposeUiTest {
        setContent { MainShell(viewModel = newViewModel()) }
        onNodeWithTag("startup-notice", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("main-shell").assertExists()
    }
}
