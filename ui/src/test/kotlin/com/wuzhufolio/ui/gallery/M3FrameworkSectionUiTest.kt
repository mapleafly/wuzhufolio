package com.wuzhufolio.ui.gallery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.theme.WuzhuTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ADR-007 §2.3 回归：M3 框架对照区在**本项目主题**下可渲染，且数值输入框的粘贴清洗生效。
 *
 * 该区只出现在开发构建（`BuildInfo.DEV_UI=true`）的组件走查页，不影响发布产物；
 * 但它是「框架能力对照 → 人工拍板风格方向」的材料，必须有自动化守护（渲染 + 清洗行为）。
 */
@OptIn(ExperimentalTestApi::class)
class M3FrameworkSectionUiTest {

    /** 与真实走查页一致：外层可滚动（日期/时间选择器位于页面下方）。 */
    private fun content(): @Composable () -> Unit = {
        WuzhuTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ZH) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                M3FrameworkSection()
            }
        }
    }

    @Test
    fun m3SectionRendersUnderProductTheme() = runComposeUiTest {
        setContent(content())
        onNodeWithTag("m3-btn-filled", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("m3-numeric-field", useUnmergedTree = true).assertIsDisplayed()
        // DEF-55 的目标形态：官方日期/时间选择器**内联**渲染（不用其 Dialog 外壳）
        onNodeWithTag("m3-date-picker", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag("m3-time-picker", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun numericFieldSanitizesPastedAmount() = runComposeUiTest {
        setContent(content())
        // 模拟 CSV/网页粘贴：千分位 + 尾随空格 + NBSP
        onNodeWithTag("m3-numeric-field", useUnmergedTree = true).performTextInput("1,234.56 \u00A0")
        waitForIdle()
        assertEquals("1,234.56", editableText(), "粘贴内容应被清洗（去空格/NBSP；千分位交由解析层处理）")

        // 全角数字同样归一为半角
        onNodeWithTag("m3-numeric-field", useUnmergedTree = true).performTextInput("１２３")
        waitForIdle()
        assertEquals("1,234.56123", editableText(), "全角数字应归一为半角并追加到现有内容")
    }

    private fun ComposeUiTest.editableText(): String =
        onNodeWithTag("m3-numeric-field", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text
}
