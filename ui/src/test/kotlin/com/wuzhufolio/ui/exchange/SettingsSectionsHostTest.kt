package com.wuzhufolio.ui.exchange

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * M6 设置页占位宿主切换（M10 前临时分组合流）：默认行情数据源分组，可切到 API 管理分组。
 */
@OptIn(ExperimentalTestApi::class)
class SettingsSectionsHostTest {

    @Test
    fun `default shows market section and switch reaches api section`() = runComposeUiTest {
        setContent {
            SettingsSectionsHost(
                marketContent = {
                    Box(Modifier.fillMaxSize().testTag("host-market")) {
                        Text("行情数据源分组")
                    }
                },
                apiContent = {
                    Box(Modifier.fillMaxSize().testTag("host-api")) {
                        Text("API 管理分组")
                    }
                },
            )
        }
        onNodeWithTag("settings-sections-host").assertIsDisplayed()
        onNodeWithTag("host-market").assertIsDisplayed()
        onNodeWithTag("settings-section-api").performClick()
        onNodeWithTag("host-api").assertIsDisplayed()
    }
}
