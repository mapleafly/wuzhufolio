package com.wuzhufolio.ui.auth

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * M2 T2.5 登录链路 UI 冒烟（假服务 + 离屏渲染）：首启创建 → 风险确认硬门控 → 向导 → 稍后进主壳 →
 * 登出回登录 → 登录进主壳 → 账户菜单 切换（严格模式）/改密/登出 回环。
 */
@OptIn(ExperimentalTestApi::class)
class AuthFlowUiTest {

    private fun ComposeUiTest.setGate(
        service: FakeAuthService,
        onSessionActive: ((Int) -> Unit)? = null,
    ) {
        setContent {
            AuthGate(
                authService = service,
                themeMode = ThemeMode.LIGHT,
                pnlScheme = PnlColorScheme.GREEN_UP,
                usernameEnumEnabled = { true },
                onSessionActive = onSessionActive,
            )
        }
    }

    /**
     * 等待节点出现。
     *
     * **超时口径（DEF-59，2026-10-11）**：原为 4000 ms，在 CI Windows 慢机（Skiko 离屏渲染 + 多轮门禁迁移）
     * 实测越界——`account menu switch requires password and logout roundtrip works` 的**登出回登录页**一步
     * （`waitFor:login-title`）在 run `38094654750` 超时失败（同一用例在 B2/B1/B5 三次 Windows CI 均通过
     * ⇒ 环境性超时，非逻辑缺陷）。放宽到 15000 ms：仅放宽容忍度，不改变断言语义（失败时多等 11 s）。
     */
    private fun ComposeUiTest.waitFor(tag: String, timeout: Long = 15000) {
        waitUntil(conditionDescription = "waitFor:" + tag, timeoutMillis = timeout) {
            onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 等待文本出现（超时口径同 [waitFor]，DEF-59）。 */
    private fun ComposeUiTest.waitForText(text: String, timeout: Long = 15000) {
        waitUntil(conditionDescription = "waitForText:" + text, timeoutMillis = timeout) {
            onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `first launch without accounts goes to create page`() = runComposeUiTest {
        setGate(FakeAuthService())
        waitFor("create-title")
        onNodeWithTag("cu-btn").assertIsDisplayed()
    }

    @Test
    fun `create requires risk confirm hard gate then wizard then shell via later`() = runComposeUiTest {
        val service = FakeAuthService()
        setGate(service)
        waitFor("create-title")

        onNodeWithTag("cu-user").performTextInput("Alex")
        onNodeWithTag("cu-pw").performTextInput("password1A")
        onNodeWithTag("cu-pw2").performTextInput("password1A")
        onNodeWithTag("cu-btn").performClick()
        waitFor("risk-modal")
        onNodeWithTag("risk-confirm", useUnmergedTree = true).assertIsNotEnabled()
        onNodeWithTag("rc-agree").performClick()
        onNodeWithTag("risk-confirm", useUnmergedTree = true).assertIsEnabled()
        onNodeWithTag("risk-confirm", useUnmergedTree = true).performClick()
        waitFor("wizard-title")
        onNodeWithTag("wizard-later").performClick()
        waitFor("main-shell")
        onNodeWithTag("acct-chip").assertExists()
    }

    @Test
    fun `login empty password shows field error then login enters shell`() = runComposeUiTest {
        val service = FakeAuthService(listOf("Alex" to "password1A"))
        setGate(service)
        waitFor("login-title")

        onNodeWithTag("lg-btn").performClick()
        waitForText("请输入密码")
        onNodeWithTag("lg-pw").performTextInput("password1A")
        onNodeWithTag("lg-btn").performClick()
        waitFor("main-shell")
    }

    /**
     * P5 修复轮（2026-09-13 人工拍板 C1）：会话激活即回调一次 —— 组合根据此触发
     * 「登录后立即同步交易数据 + 刷新行情」（PRD 故事 4.2）；登出（无会话）不得触发。
     */
    @Test
    fun `session activation invokes the auto-sync callback exactly once per account`() = runComposeUiTest {
        val service = FakeAuthService(listOf("Alex" to "password1A"))
        val activated = mutableListOf<Int>()
        setGate(service, onSessionActive = { activated += it })
        waitFor("login-title")
        assertEquals(emptyList(), activated, "登录前不得触发")

        onNodeWithTag("lg-pw").performTextInput("password1A")
        onNodeWithTag("lg-btn").performClick()
        waitFor("main-shell")
        assertEquals(1, activated.size, "登录成功后应回调一次")

        onNodeWithTag("acct-chip").performClick()
        waitFor("acct-menu")
        onNodeWithTag("acct-logout", useUnmergedTree = true).performClick()
        waitFor("login-title")
        assertEquals(1, activated.size, "登出（无会话）不触发回调")
    }

    @Test
    fun `forgot page shows A4 copy and returns to login`() = runComposeUiTest {
        val service = FakeAuthService(listOf("Alex" to "password1A"))
        setGate(service)
        waitFor("login-title")
        onNodeWithTag("lg-forgot").performClick()
        waitFor("forgot-banner")
        onNodeWithTag("forgot-back").performClick()
        waitFor("login-title")
    }

    @Test
    fun `account menu switch requires password and logout roundtrip works`() = runComposeUiTest {
        val service = FakeAuthService(listOf("Alex" to "password1A", "Family" to "password2B"))
        setGate(service)
        waitFor("login-title")
        onNodeWithTag("lg-pw").performTextInput("password1A")
        onNodeWithTag("lg-btn").performClick()
        waitFor("main-shell")

        // 菜单 → 切换 Family：错密码拒绝（严格模式）→ 正确通过
        onNodeWithTag("acct-chip").performClick()
        waitFor("acct-menu")
        onNodeWithTag("acct-item-2", useUnmergedTree = true).performClick()
        waitFor("switch-modal")
        // B2（D45）：切换确认须显示「当前账户」（避免误判自己在哪个账户）
        waitForText("当前账户：Alex")
        onNodeWithTag("switch-pw", useUnmergedTree = true).performTextInput("wrong1A")
        onNodeWithTag("switch-confirm", useUnmergedTree = true).performClick()
        waitForText("密码错误")
        onNodeWithTag("switch-pw", useUnmergedTree = true).performTextClearance()
        onNodeWithTag("switch-pw", useUnmergedTree = true).performTextInput("password2B")
        onNodeWithTag("switch-confirm", useUnmergedTree = true).performClick()
        waitForText("已切换到账户「Family」")

        // 修改密码 → 回登录页（PRD：改密后需重新登录）
        onNodeWithTag("acct-chip").performClick()
        waitFor("acct-menu")
        onNodeWithTag("acct-change-pw", useUnmergedTree = true).performClick()
        waitFor("change-pw-modal")
        onNodeWithTag("change-pw-old", useUnmergedTree = true).performTextInput("password2B")
        onNodeWithTag("change-pw-new", useUnmergedTree = true).performTextInput("newPassword3C")
        onNodeWithTag("change-pw-new2", useUnmergedTree = true).performTextInput("newPassword3C")
        onNodeWithTag("change-pw-save", useUnmergedTree = true).performClick()
        waitFor("login-title")

        // 用户名枚举切换到 Family → 新密码登录 → 登出回登录页
        onNodeWithTag("lg-user").performClick()
        onNodeWithTag("lg-user-opt-Family").performClick()
        onNodeWithTag("lg-pw").performTextClearance()
        onNodeWithTag("lg-pw").performTextInput("newPassword3C")
        onNodeWithTag("lg-btn").performClick()
        waitFor("main-shell")
        onNodeWithTag("acct-chip").performClick()
        waitFor("acct-menu")
        // DEF-59：点击前先等菜单项就位（慢机上菜单节点存在但尚未完成布局时点击会丢事件）
        waitFor("acct-logout")
        onNodeWithTag("acct-logout", useUnmergedTree = true).performClick()
        waitFor("login-title")
        onNodeWithTag("lg-btn").assertIsDisplayed()
   }
}
