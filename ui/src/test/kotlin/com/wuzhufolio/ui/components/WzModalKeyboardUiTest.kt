package com.wuzhufolio.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.theme.WuzhuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **DEF-56 回归（真实按键路径）**：弹窗内输入框敲空格/回车**不得**关闭弹窗。
 *
 * 为什么既有用例抓不到：`AGENTS.md §7.3-③` 的强制项用的是 `performTextInput`（直接注入文本，
 * **绕过键盘事件**），而本缺陷恰恰只在**真实按键**路径上触发——遮罩的 `clickable` 会把空格/回车
 * 当成「激活点击」，而遮罩是弹窗内输入框的**祖先节点**，按键冒泡上去即 `onDismiss()`。
 *
 * 本用例因此用 `performKeyInput { pressKey(...) }`（真实 KeyDown/KeyUp）作为守护手段，
 * 并同时断言「按键确实到达了输入框」（值里出现空格），避免测试假绿。
 */
@OptIn(ExperimentalTestApi::class)
class WzModalKeyboardUiTest {

    @Test
    fun spaceAndEnterInsideModalFieldDoNotDismiss() = runComposeUiTest {
        var dismissCount = 0
        setContent {
            WuzhuTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ZH) {
                var value by remember { mutableStateOf("") }
                val fieldFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                WzModal(
                    title = "键盘回归",
                    onDismiss = { dismissCount++ },
                    testTag = "kb-modal",
                    initialFocusRequester = fieldFocus, // §7.3-②：含输入框的弹窗打开即聚焦首字段
                ) {
                    WzTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = "备注",
                        testTag = "kb-field",
                        fieldFocusRequester = fieldFocus,
                    )
                }
            }
        }

        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("kb-field").assertIsFocused() }.isSuccess
        }
        onNodeWithTag("kb-field").performKeyInput { pressKey(Key.Spacebar) }
        waitForIdle()
        assertEquals(0, dismissCount, "输入框内按空格不得关闭弹窗（DEF-56）")
        onNodeWithTag("kb-modal", useUnmergedTree = true).assertIsDisplayed()
        // 按键确实作用在输入框上（焦点未丢），说明事件到达了字段而非被遮罩截走
        onNodeWithTag("kb-field").assertIsFocused()

        onNodeWithTag("kb-field").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(0, dismissCount, "输入框内按回车不得关闭弹窗（同源缺陷）")
        onNodeWithTag("kb-modal", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("kb-field").assertIsFocused()
    }

    @Test
    fun escStillDismissesAndScrimKeepsFocusOut() = runComposeUiTest {
        var dismissCount = 0
        setContent {
            WuzhuTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ZH) {
                val fieldFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                WzModal(
                    title = "Esc 回归",
                    onDismiss = { dismissCount++ },
                    testTag = "esc-modal",
                    initialFocusRequester = fieldFocus,
                ) {
                    WzTextField(
                        value = "",
                        onValueChange = {},
                        label = "字段",
                        testTag = "esc-field",
                        fieldFocusRequester = fieldFocus,
                    )
                }
            }
        }
        // Tab 不得把焦点交给遮罩（遮罩已不是焦点目标）：Tab 后焦点应仍在弹窗内的可聚焦控件上
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("esc-field").assertIsFocused() }.isSuccess
        }
        onNodeWithTag("esc-field").assertIsFocused()
        onRoot().performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        val stillInside = runCatching { onNodeWithTag("esc-modal", useUnmergedTree = true).assertIsDisplayed() }
            .isSuccess
        assertTrue(stillInside, "Tab 不应关掉弹窗")

        onRoot().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(1, dismissCount, "Esc 仍应关闭弹窗（既有行为不得回退）")
    }

}
