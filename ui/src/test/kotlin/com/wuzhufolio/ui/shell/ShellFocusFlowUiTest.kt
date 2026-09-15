package com.wuzhufolio.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzOverlayRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 主壳键盘焦点流（2026-09-15 Windows 人工走查第 4 轮反馈）：
 *
 * - **DEF-20**：焦点进入页面内容后没有出口——键盘用户想换页或点顶栏，只能一路 Tab 绕完整个页面；
 * - **本次落地**：回车选中导航项后焦点**直接进入页面内容**（不再逐个 Tab 穿过顶栏），页面内未被控件
 *   消费的 Esc / ↑ / ↓ 把焦点交回侧边栏当前项，回到「侧边栏→顶栏→页面」外壳循环；侧边栏内 ↑ / ↓
 *   在导航项之间移动。
 *
 * 测试里一律用 `RequestFocus` 语义动作 + 键事件（**不点鼠标**）：点击本身也会触发页面进入聚焦，
 * 混用会让「键事件到底投递给谁」失真。
 * 护栏：① 弹层打开时页面退出键**不接管**（焦点不得跑到弹层背后）；② 容器自身不是焦点停靠点
 * （不得重现 DEF-13 的隐形焦点目标）。
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class ShellFocusFlowUiTest {

    private fun newViewModel() = ShellViewModel(ThemeMode.LIGHT, PnlColorScheme.GREEN_UP)

    /** 纯键盘聚焦（不含鼠标点击）：语义动作 RequestFocus。 */
    private fun SemanticsNodeInteraction.keyboardFocus(): SemanticsNodeInteraction =
        performSemanticsAction(SemanticsActions.RequestFocus)

    @Test
    fun `enter on a sidebar item moves focus into the page content`() = runComposeUiTest {
        setContent {
            MainShell(
                newViewModel(),
                fundsPageContent = {
                    Column {
                        WzButton(text = "记录增资", onClick = {}, testTag = "probe-funds-btn")
                        WzButton(text = "记录撤资", onClick = {}, testTag = "probe-withdraw-btn")
                    }
                },
            )
        }
        onNodeWithTag("nav-FUNDS").keyboardFocus().assertIsFocused()
        onNodeWithTag("nav-FUNDS").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        // 焦点应落在页面内容内（首个可聚焦控件），而不是留在侧边栏/顶栏
        onNodeWithTag("probe-funds-btn").assertIsFocused()
    }

    @Test
    fun `escape and arrow keys inside the page return focus to the active sidebar item`() = runComposeUiTest {
        setContent {
            MainShell(
                newViewModel(),
                fundsPageContent = {
                    Column {
                        WzButton(text = "记录增资", onClick = {}, testTag = "probe-funds-btn")
                    }
                },
            )
        }
        onNodeWithTag("nav-FUNDS").keyboardFocus()
        onNodeWithTag("nav-FUNDS").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onNodeWithTag("probe-funds-btn").assertIsFocused()

        onNodeWithTag("probe-funds-btn").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithTag("nav-FUNDS").assertIsFocused()

        // 回到侧边栏后再进页面，↑ 同样能退出（外壳语义：方向键 = 上下移动焦点）
        onNodeWithTag("nav-FUNDS").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onNodeWithTag("probe-funds-btn").assertIsFocused()
        onNodeWithTag("probe-funds-btn").performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        onNodeWithTag("nav-FUNDS").assertIsFocused()
    }

    @Test
    fun `arrow keys move focus between sidebar items`() = runComposeUiTest {
        setContent { MainShell(newViewModel()) }
        onNodeWithTag("nav-DASHBOARD").keyboardFocus().assertIsFocused()

        onNodeWithTag("nav-DASHBOARD").performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        onNodeWithTag("nav-ASSETS").assertIsFocused()

        onNodeWithTag("nav-ASSETS").performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        onNodeWithTag("nav-TRANSACTIONS").assertIsFocused()

        onNodeWithTag("nav-TRANSACTIONS").performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        onNodeWithTag("nav-ASSETS").assertIsFocused()
    }

    @Test
    fun `page exit keys stay out of the way while a modal is open`() = runComposeUiTest {
        var open by mutableStateOf(false)
        setContent {
            MainShell(
                newViewModel(),
                fundsPageContent = {
                    Column {
                        WzButton(
                            text = "打开弹层",
                            onClick = { open = true },
                            testTag = "probe-open-modal",
                        )
                        if (open) {
                            WzModal(title = "增资", onDismiss = { open = false }, testTag = "probe-modal") {
                                WzButton(text = "取消", onClick = { open = false }, testTag = "probe-modal-cancel")
                            }
                        }
                    }
                },
            )
        }
        onNodeWithTag("nav-FUNDS").keyboardFocus()
        onNodeWithTag("nav-FUNDS").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onNodeWithTag("probe-open-modal").assertIsFocused()
        onNodeWithTag("probe-open-modal").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(1, WzOverlayRegistry.openModalCount, "弹层打开应登记（主壳据此让出 Esc/方向键）")

        // 弹层打开时方向键不得把焦点移到弹层背后的侧边栏
        onNodeWithTag("probe-modal").performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        onNodeWithTag("nav-FUNDS").assertIsNotFocused()

        // Esc 归弹层：关掉弹层而不是让焦点回侧边栏；关闭后计数归零
        onNodeWithTag("probe-modal").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(0, WzOverlayRegistry.openModalCount, "弹层关闭后应注销登记")
        assertTrue(!open, "Esc 应关闭弹层")
    }

    @Test
    fun `page entry adds no invisible focus stop to the tab order`() = runComposeUiTest {
        setContent {
            MainShell(
                newViewModel(),
                fundsPageContent = {
                    Column {
                        WzButton(text = "记录增资", onClick = {}, testTag = "probe-funds-btn")
                    }
                },
            )
        }
        onNodeWithTag("nav-FUNDS").keyboardFocus()
        onNodeWithTag("nav-FUNDS").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onNodeWithTag("probe-funds-btn").assertIsFocused()

        // 从页面首个控件继续 Tab：每一步仍须落在有标识的真实节点（DEF-13 回归护栏）
        repeat(3) { step ->
            onNodeWithTag("probe-funds-btn").performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
            val tags = onAllNodes(isFocused()).fetchSemanticsNodes().map {
                runCatching { it.config[SemanticsProperties.TestTag] }.getOrNull()
            }
            assertEquals(1, tags.size, "第 ${step + 1} 步 Tab 后应恰好一个焦点目标，实际：$tags")
            assertTrue(tags.single() != null, "Tab 落在无标识的隐形焦点目标上（DEF-13 回归）：$tags")
        }
    }
}
