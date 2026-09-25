package com.wuzhufolio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.auth.AuthCopy
import com.wuzhufolio.ui.auth.LoginPage
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.shell.MainShell
import com.wuzhufolio.ui.shell.ShellViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 键盘可达性回归（P6 人工门 DEF-13 / DEF-14，2026-09-14）。
 *
 * 背景（Windows 11 人工走查实测）：
 * - **DEF-13**：主壳内 Tab 只在侧边栏项之间循环、进不到页面内容。根因 = `WzButton` / `WzSelect`
 *   同时挂了 `clickable`（自带焦点目标）与显式 `.focusable()` → **同一节点两个焦点目标**，
 *   每两次 Tab 就有一次落在没有语义、没有焦点环的隐形目标上（用户感知为「焦点动不了 / 按键没反应」）。
 * - **DEF-14**：登录页填完密码按回车无反应，必须先 Tab 到「登录」按钮再回车。
 *
 * 本测试把两条都钉死：① Tab 每一步都必须落在**真实可聚焦节点**（不得出现空焦点步进），
 * 且页面内按钮可达；② 密码框回车即提交（与按钮同路径）。
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KeyboardA11yUiTest {

    @Test
    fun `tab traversal reaches page content without landing on invisible focus targets`() = runComposeUiTest {
        fun focusedTags(): List<String> =
            onAllNodes(isFocused()).fetchSemanticsNodes().map {
                runCatching { it.config[SemanticsProperties.TestTag] }.getOrNull() ?: "?"
            }

        setContent {
            MainShell(
                ShellViewModel(ThemeMode.LIGHT, PnlColorScheme.GREEN_UP),
                onManualSync = {},
                onRefreshQuotes = {},
                fundsPageContent = {
                    Column {
                        WzButton(text = "记录增资", onClick = {}, testTag = "probe-funds-btn")
                        WzButton(text = "记录撤资", onClick = {}, testTag = "probe-withdraw-btn")
                    }
                },
            )
        }
        // ① 外壳一轮：侧边栏 6 项（正式构建无 DEV 走查页，DEF-47）→ 顶栏 3 项，每一步恰好一个**有标识的真实**焦点目标（DEF-13 护栏）
        val shellOrder = mutableListOf<String>()
        repeat(9) {
            onRoot().performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
            val tags = focusedTags()
            assertEquals(1, tags.size, "Tab 后应恰好一个焦点目标，实际：$tags（第 ${shellOrder.size + 1} 步）")
            assertTrue(tags.single() != "?", "Tab 落在无标识的隐形焦点目标上：$tags")
            shellOrder += tags.single()
        }
        assertEquals(
            listOf(
                "nav-DASHBOARD", "nav-ASSETS", "nav-TRANSACTIONS", "nav-FUNDS", "nav-QUOTES", "nav-SETTINGS",
                "topbar-refresh-quotes", "topbar-sync", "theme-toggle",
            ),
            shellOrder,
            "外壳 Tab 顺序（侧边栏 6 + 顶栏 3；正式构建不含 nav-GALLERY）",
        )
        // DEF-47：正式构建下 DEV 走查页既不渲染、也就不在 Tab 序里
        onNodeWithTag("nav-GALLERY").assertDoesNotExist()

        // ② 键盘选中「资金管理」→ 焦点**直接进入页面内容**（走查改进项 A / DEF-20 修复后的焦点流）
        onNodeWithTag("nav-FUNDS").performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithTag("nav-FUNDS").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onNodeWithTag("probe-funds-btn").assertIsFocused()

        // ③ 页面内继续 Tab：第二个页面控件可达，且仍是真实节点（修复前 focus 只在侧边栏 + 顶栏循环）
        onRoot().performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        assertEquals(listOf("probe-withdraw-btn"), focusedTags(), "页面内第二个按钮应经 Tab 可达")
    }

    @Test
    fun `enter in password field submits the login form`() = runComposeUiTest {
        var submitted: Triple<String, String, Boolean>? = null
        setContent {
            LoginPage(
                accounts = emptyList(),
                usernameEnumEnabled = false,
                busyText = null,
                formError = null,
                onLogin = { u, p, r -> submitted = Triple(u, p, r) },
                onForgot = {},
                onCreate = {},
                onTyping = {},
            )
        }
        onNodeWithTag("lg-user").performTextInput("alice")
        onNodeWithTag("lg-pw").performTextInput("password1A")
        onNodeWithTag("lg-pw").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(
            Triple("alice", "password1A", true),
            submitted,
            "密码框回车应直接提交登录（与「登录」按钮同路径）",
        )
    }

    @Test
    fun `enter with empty password shows the inline error instead of submitting`() = runComposeUiTest {
        var submitted = false
        setContent {
            LoginPage(
                accounts = emptyList(),
                usernameEnumEnabled = false,
                busyText = null,
                formError = null,
                onLogin = { _, _, _ -> submitted = true },
                onForgot = {},
                onCreate = {},
                onTyping = {},
            )
        }
        onNodeWithTag("lg-pw").performClick() // 先聚焦（键事件只投递给聚焦节点）
        onNodeWithTag("lg-pw").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertTrue(!submitted, "空密码不得提交")
        onAllNodesWithText(AuthCopy.LOGIN_ERROR_PASSWORD_EMPTY).fetchSemanticsNodes().isNotEmpty()
            .let { assertTrue(it, "空密码回车应给出内联错误") }
        onNodeWithTag("lg-pw").assertIsDisplayed()
    }
}
