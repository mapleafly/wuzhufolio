package com.wuzhufolio.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.theme.WuzhuTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **DEF-55 回归**：日期时间输入的「弹出选择」路径（官方 `DatePicker`/`TimePicker` 内联于 `WzModal`）。
 *
 * 覆盖：打开选择器 → 日期/时间页签切换 → 「现在」快捷写回 → 取消不写回 → 确定写回可解析值；
 * 并守护时间口径（`yyyy-MM-dd'T'HH:mm`，本地时区）。
 */
@OptIn(ExperimentalTestApi::class)
class WzDateTimeFieldUiTest {

    private val format = WZ_LOCAL_DATE_TIME

    @Test
    fun pickerOpensWithCalendarAndSwitchesToClock() = runComposeUiTest {
        setContent {
            WuzhuTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ZH) {
                var value by remember { mutableStateOf("2026-09-28T14:30") }
                WzDateTimeField(
                    label = "交易时间",
                    value = value,
                    onChange = { value = it },
                    testTag = "dt",
                )
            }
        }
        onNodeWithTag("dt").assertIsDisplayed()
        onNodeWithTag("dt-picker", useUnmergedTree = true).assertDoesNotExist()

        onNodeWithTag("dt-pick", useUnmergedTree = true).performClick()
        // 默认在「日期」页签 → 官方日历内联显示（不是弹独立窗口）
        onNodeWithTag("dt-picker", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("dt-calendar", useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag("dt-tab-1", useUnmergedTree = true).performClick()
        onNodeWithTag("dt-clock", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("dt-calendar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun confirmWritesParseableLocalDateTimeAndCancelKeepsValue() = runComposeUiTest {
        var current = "2026-09-28T14:30"
        setContent {
            WuzhuTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ZH) {
                var value by remember { mutableStateOf(current) }
                WzDateTimeField(
                    label = "交易时间",
                    value = value,
                    onChange = {
                        value = it
                        current = it
                    },
                    testTag = "dt",
                )
            }
        }

        // ① 取消：不改值、关闭选择器
        onNodeWithTag("dt-pick", useUnmergedTree = true).performClick()
        onNodeWithTag("dt-cancel", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals("2026-09-28T14:30", current, "取消不得写回")
        onNodeWithTag("dt-picker", useUnmergedTree = true).assertDoesNotExist()

        // ② 确定：写回可被账本解析的本地时间文本（日期取选择器当前选中，时间取时间选择器当前值）
        onNodeWithTag("dt-pick", useUnmergedTree = true).performClick()
        onNodeWithTag("dt-confirm", useUnmergedTree = true).performClick()
        waitForIdle()
        val parsed = runCatching { LocalDateTime.parse(current, format) }.getOrNull()
        assertNotNull(parsed, "确定后写回的文本必须是 yyyy-MM-dd'T'HH:mm（实际：$current）")
        assertEquals(LocalDate.of(2026, 9, 28), parsed.toLocalDate(), "日期应沿用初始选中日期")
        assertEquals(14, parsed.hour)
        assertEquals(30, parsed.minute)
    }

    @Test
    fun nowShortcutWritesCurrentLocalTime() = runComposeUiTest {
        var current = "2020-01-01T00:00"
        setContent {
            WuzhuTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ZH) {
                var value by remember { mutableStateOf(current) }
                WzDateTimeField(
                    label = "交易时间",
                    value = value,
                    onChange = {
                        value = it
                        current = it
                    },
                    testTag = "dt",
                )
            }
        }
        onNodeWithTag("dt-pick", useUnmergedTree = true).performClick()
        onNodeWithTag("dt-now", useUnmergedTree = true).performClick()
        waitForIdle()
        val parsed = runCatching { LocalDateTime.parse(current, format) }.getOrNull()
        assertNotNull(parsed)
        val minutesAway = kotlin.math.abs(
            java.time.Duration.between(parsed, LocalDateTime.now()).toMinutes(),
        )
        assertTrue(minutesAway <= 1, "「现在」应写入当前本地时间（差 $minutesAway 分钟）")
    }

    @Test
    fun composeLocalDateTimeUsesUtcPickerMillisAndLocalTime() {
        // DatePickerState.selectedDateMillis 语义 = 所选日期的 UTC 零点
        val millis = LocalDate.of(2026, 9, 28).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("2026-09-28T14:30", composeLocalDateTime(millis, hour = 14, minute = 30))
        // 未选日期 → 回退今天（不抛异常）
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        assertEquals("${today}T09:05", composeLocalDateTime(null, hour = 9, minute = 5))
    }
}
