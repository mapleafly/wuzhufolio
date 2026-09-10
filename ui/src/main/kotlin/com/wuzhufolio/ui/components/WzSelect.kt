package com.wuzhufolio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 下拉选择（M10 设置页统一组件；原型 select 口径 + M5 §5-6-②「统一组件化时换下拉」落点）：
 * 描边框 + 当前值 + DropdownMenu 候选；testTag 作用于触发框，候选项 testTag = tag + "-opt-" + 序号。
 */
@Composable
fun <T> WzSelect(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelOf: (T) -> String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val colors = WzTheme.colors
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .widthIn(min = 132.dp)
            .clip(shape)
            .background(colors.surface)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.accent else colors.line,
                shape = shape,
            )
            .clickable { expanded = true }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = labelOf(selected) + "  ▾",
            color = colors.ink,
            style = WzTheme.typography.body,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(colors.surface).testTag((testTag ?: "select") + "-menu"),
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = labelOf(option),
                            color = if (option == selected) colors.accent else colors.ink,
                            style = WzTheme.typography.body,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier = Modifier.testTag((testTag ?: "select") + "-opt-" + index),
                )
            }
        }
    }
}

/**
 * 开关（原型 switch：role=switch 语义；开 = accent 实底，关 = surface2）。
 * 状态与回调由调用方持有（无内部状态）；testTag 与 clickable 同节点（点击命中整轨）。
 */
@Composable
fun WzSwitch(
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val colors = WzTheme.colors
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .width(44.dp)
            .height(26.dp)
            .clip(shape)
            .background(if (on) colors.accent else colors.surface2)
            .border(1.dp, if (on) colors.accent else colors.line, shape)
            .clickable(enabled = enabled, onClick = onToggle, role = Role.Switch)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .width(20.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface),
        )
    }
}

/** 分段选择（原型 seg：●/○ 强调当前档；设置页主题等双档位）。 */
@Composable
fun <T> WzSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelOf: (T) -> String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Row(modifier = modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, option ->
            val active = option == selected
            WzButton(
                text = (if (active) "● " else "○ ") + labelOf(option),
                onClick = { onSelect(option) },
                variant = if (active) WzButtonVariant.Primary else WzButtonVariant.Secondary,
                testTag = testTag?.let { it + "-" + index },
            )
        }
    }
}
