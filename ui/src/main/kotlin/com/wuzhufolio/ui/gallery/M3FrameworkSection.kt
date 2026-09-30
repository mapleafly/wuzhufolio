package com.wuzhufolio.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.components.AmountSanitizer
import com.wuzhufolio.ui.i18n.galleryStrings
import com.wuzhufolio.ui.theme.WzSpacing

/**
 * **Material 3 框架对照区**（ADR-007 §2.3）：把「官方组件」与「既有自绘组件」放在**同一主题、同一页**
 * 并排展示，供人工目视拍板风格方向——这是「界面体系统一」改造的第一道人工门所需的材料。
 *
 * 只出现在开发构建的组件走查页（`BuildInfo.DEV_UI=false` 时整页不进侧边栏，见 DEF-47），
 * 因此**对发布产物零影响**。
 *
 * 两处「问题形态演示」：
 * - **数值输入**：状态版 `OutlinedTextField` + `InputTransformation`，粘贴 `1,234.56 ` / 全角数字 / NBSP
 *   会被自动清洗（DEF-54 的目标形态，解析口径见 `AmountSanitizer`）；
 * - **日期时间**：`DatePicker` / `TimePicker` **内联**渲染（不用官方 `DatePickerDialog` 外壳，
 *   守住 `AGENTS.md §7.3-①`「弹层一律同窗口就地叠加」，DEF-55 的目标形态）。
 */
@OptIn(ExperimentalMaterial3Api::class) // TimePicker / TooltipBox 在 material3 1.9.0 仍为实验 API
@Composable
fun M3FrameworkSection() {
    val strings = galleryStrings
    var selectedSegment by remember { mutableIntStateOf(0) }
    var switchOn by remember { mutableStateOf(true) }
    var checked by remember { mutableStateOf(true) }
    var chipOn by remember { mutableStateOf(false) }
    val priceState = rememberTextFieldState()

    GallerySection(strings.m3Section) {
        // ① 按钮
        Column(verticalArrangement = Arrangement.spacedBy(WzSpacing.sm)) {
            Text(strings.m3ButtonsLabel, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(WzSpacing.sm)) {
                Button(onClick = {}, modifier = Modifier.testTag("m3-btn-filled")) { Text(strings.m3Confirm) }
                FilledTonalButton(onClick = {}) { Text(strings.m3Confirm) }
                OutlinedButton(onClick = {}) { Text(strings.m3Confirm) }
                TextButton(onClick = {}) { Text(strings.m3Confirm) }
            }
        }

        // ② 输入框（普通 + 粘贴自动清洗的数值框）
        Column(
            modifier = Modifier.padding(top = WzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WzSpacing.sm),
        ) {
            Text(strings.m3InputsLabel, style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                state = priceState,
                label = { Text(strings.m3NumericDemoField) },
                lineLimits = TextFieldLineLimits.SingleLine,
                // 粘贴/拖放会自动经过 InputTransformation（官方语义），因此**不需要**拦截剪贴板
                inputTransformation = SanitizeAmountInput,
                modifier = Modifier.fillMaxWidth().testTag("m3-numeric-field"),
            )
            Text(strings.m3NumericDemoLabel, style = MaterialTheme.typography.bodySmall)
        }

        // ③ 选择控件
        Column(
            modifier = Modifier.padding(top = WzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WzSpacing.sm),
        ) {
            Text(strings.m3SelectionLabel, style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow {
                repeat(3) { index ->
                    SegmentedButton(
                        selected = selectedSegment == index,
                        onClick = { selectedSegment = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        label = { Text("${index + 1}") },
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WzSpacing.md),
            ) {
                Switch(checked = switchOn, onCheckedChange = { switchOn = it })
                Checkbox(checked = checked, onCheckedChange = { checked = it })
                FilterChip(
                    selected = chipOn,
                    onClick = { chipOn = !chipOn },
                    label = { Text(strings.m3SampleItem) },
                )
            }
        }

        // ④ 容器
        Column(
            modifier = Modifier.padding(top = WzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WzSpacing.sm),
        ) {
            Text(strings.m3ContainersLabel, style = MaterialTheme.typography.labelLarge)
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.m3SampleItem,
                    modifier = Modifier.padding(WzSpacing.lg),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        // ⑤ 反馈
        Column(
            modifier = Modifier.padding(top = WzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WzSpacing.sm),
        ) {
            Text(strings.m3FeedbackLabel, style = MaterialTheme.typography.labelLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WzSpacing.md),
            ) {
                TooltipBox(
                    positionProvider = androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider(
                        androidx.compose.material3.TooltipAnchorPosition.Above,
                    ),
                    tooltip = { PlainTooltip { Text(strings.m3TooltipSample) } },
                    state = rememberTooltipState(),
                ) {
                    OutlinedButton(onClick = {}, modifier = Modifier.testTag("m3-tooltip-anchor")) {
                        Text(strings.m3TooltipSample)
                    }
                }
                LinearProgressIndicator(
                    progress = { 0.4f },
                    modifier = Modifier.fillMaxWidth(0.4f),
                )
            }
            Snackbar(modifier = Modifier.fillMaxWidth()) { Text(strings.m3SampleItem) }
        }

        // ⑥ 日期与时间（内联，不用官方 Dialog 外壳）
        Column(
            modifier = Modifier.padding(top = WzSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(WzSpacing.sm),
        ) {
            Text(strings.m3DateTimeLabel, style = MaterialTheme.typography.labelLarge)
            DatePicker(state = rememberDatePickerState(), modifier = Modifier.testTag("m3-date-picker"))
            TimePicker(
                state = rememberTimePickerState(initialHour = 14, initialMinute = 30, is24Hour = true),
                modifier = Modifier.testTag("m3-time-picker"),
            )
        }
    }
}

/**
 * 数值输入清洗：NFKC 全角→半角、去各类空白与货币符号（口径见 [AmountSanitizer.sanitize]）。
 *
 * 注意：`InputTransformation` 是**普通接口**（非 `fun interface`），必须用对象表达式；
 * 且**程序化赋值（`state.edit {}` / `rememberTextFieldState(initialText=)`）不经过本变换**——
 * 从 `.cpro` 恢复或表单预填时须自行再清洗一次（ADR-007 §2.5 的已知坑）。
 */
private val SanitizeAmountInput = object : InputTransformation {
    // 注意：抽象成员是**接收者式**声明 `fun TextFieldBuffer.transformInput()`（该接口不是 fun interface）
    override fun TextFieldBuffer.transformInput() {
        val raw = asCharSequence().toString()
        val cleaned = AmountSanitizer.sanitize(raw)
        if (cleaned != raw) {
            replace(0, length, cleaned)
        }
    }
}
