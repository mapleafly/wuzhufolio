package com.wuzhufolio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.i18n.commonStrings
import com.wuzhufolio.ui.theme.WzSpacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 日期时间文本格式（与账本既有口径一致：`yyyy-MM-dd'T'HH:mm`，本地时区，保存时转 UTC）。 */
val WZ_LOCAL_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

/**
 * 日期时间输入（**DEF-55 / ADR-007 §2.3**）：文本输入 + 「选择…」弹出官方 `DatePicker`/`TimePicker`。
 *
 * 设计要点：
 * 1. **手打入口保留**（熟练用户更快，且既有测试/口径不变），选择器与文本双向同步；
 * 2. 选择器**内联**在项目自有的 [WzModal] 里，**不用官方 `DatePickerDialog`**——
 *    后者在 desktop 走 `Dialog`（`ComposeSceneLayer`）且仍需 `@OptIn`，与 `AGENTS.md §7.3-①` 的既有结论相悖；
 * 3. 时区口径：`DatePickerState.selectedDateMillis` 是**所选日期的 UTC 零点**，因此取日期用
 *    `ZoneOffset.UTC`，再与时间在**本地时区**组合；`TimePicker` 24 小时制。
 */
@OptIn(ExperimentalMaterial3Api::class) // TimePicker / rememberTimePickerState 在 material3 1.9.0 仍为实验 API
@Composable
fun WzDateTimeField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    testTag: String? = null,
    fieldFocusRequester: FocusRequester? = null,
) {
    val strings = commonStrings
    val baseTag = testTag ?: "wz-datetime"
    var pickerOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(WzSpacing.sm),
    ) {
        WzTextField(
            value = value,
            onValueChange = onChange,
            label = label,
            placeholder = "2026-09-28T14:30",
            error = error,
            modifier = Modifier.weight(1f),
            testTag = testTag,
            fieldFocusRequester = fieldFocusRequester,
        )
        WzButton(
            text = strings.pickOpen,
            onClick = { pickerOpen = true },
            variant = WzButtonVariant.Secondary,
            testTag = baseTag + "-pick",
        )
    }

    if (!pickerOpen) return

    val initial = remember(value) { parseLocalDateTimeOrNull(value) ?: LocalDateTime.now() }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    val timeState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    var tab by remember { mutableIntStateOf(0) }

    WzModal(
        title = strings.pickTitle,
        onDismiss = { pickerOpen = false },
        width = 420.dp,
        testTag = baseTag + "-picker",
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(strings.pickDate, strings.pickTime).forEachIndexed { index, text ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        label = { Text(text) },
                        modifier = Modifier.testTag(baseTag + "-tab-" + index),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = modalContentMaxHeight())
                    .verticalScroll(rememberScrollState())
                    .padding(top = WzSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                if (tab == 0) {
                    DatePicker(
                        state = dateState,
                        modifier = Modifier.testTag(baseTag + "-calendar"),
                    )
                } else {
                    TimePicker(
                        state = timeState,
                        modifier = Modifier.testTag(baseTag + "-clock"),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = WzSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(WzSpacing.sm, Alignment.End),
            ) {
                WzButton(
                    text = strings.pickNow,
                    onClick = {
                        onChange(LocalDateTime.now().format(WZ_LOCAL_DATE_TIME))
                        pickerOpen = false
                    },
                    variant = WzButtonVariant.Secondary,
                    testTag = baseTag + "-now",
                )
                WzButton(
                    text = strings.cancel,
                    onClick = { pickerOpen = false },
                    variant = WzButtonVariant.Secondary,
                    testTag = baseTag + "-cancel",
                )
                WzButton(
                    text = strings.confirm,
                    onClick = {
                        onChange(composeLocalDateTime(dateState.selectedDateMillis, timeState.hour, timeState.minute))
                        pickerOpen = false
                    },
                    testTag = baseTag + "-confirm",
                )
            }
        }
    }
}

/** 组合日期与时间为本地时区文本；日期为 null（未选）时回退今天。 */
internal fun composeLocalDateTime(dateMillis: Long?, hour: Int, minute: Int): String {
    val date = dateMillis
        ?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        ?: LocalDate.now()
    return LocalDateTime.of(date, LocalTime.of(hour, minute)).format(WZ_LOCAL_DATE_TIME)
}

/** 解析既有文本（失败返回 null，由调用方回退「现在」）；`remember(value)` 缓存，避免每次重组重解析。 */
internal fun parseLocalDateTimeOrNull(text: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(text, WZ_LOCAL_DATE_TIME) }.getOrNull()
