package com.wuzhufolio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
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
                (runCatching { it.config[SemanticsProperties.TestTag] }.getOrNull() ?: "?").toString()
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
        val seen = mutableListOf<String>()
        repeat(20) {
            onRoot().performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
            val tags = focusedTags()
            // ① 每一步恰好一个焦点目标，且必须是有标识的真实节点（DEF-13 的隐形目标会让这里变空）
            assertEquals(1, tags.size, "Tab 后应恰好一个焦点目标，实际：$tags（第 ${seen.size + 1} 步）")
            assertTrue(tags.single() != "?", "Tab 落在无标识的隐形焦点目标上：$tags")
            seen += tags.single()
            if (tags.single() == "nav-FUNDS") {
                onNodeWithTag("nav-FUNDS").performKeyInput { pressKey(Key.Enter) }
                waitForIdle()
            }
        }
        // ② 页面内容可达（修复前 focus 只在侧边栏 + 顶栏循环）
        assertTrue("probe-funds-btn" in seen, "Tab 应能进入页面内容，实际序列：$seen")
        assertTrue("probe-withdraw-btn" in seen, "页面内第二个按钮也应可达，实际序列：$seen")
        // ③ 侧边栏与顶栏控件同样保持可达（回归）
        assertTrue("nav-DASHBOARD" in seen && "topbar-sync" in seen, "侧边栏/顶栏应保持可达：$seen")
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
