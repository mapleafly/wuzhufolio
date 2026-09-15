package com.wuzhufolio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 统一卡片 + 自适应指标数字（P6 人工门第七轮 · DEF-32/34）。
 *
 * **问题**：总资产净值/投入本金等大号数字在窄窗（1024×768）里**换行变形**（`display` 32sp 放不下就折行）；
 * 各页卡片各写各的 `background + border + padding`，视觉口径分散。
 *
 * **口径**：卡片一律用 [WzCard]；卡片里的**关键指标数字**一律用 [WzMetric]——**单行显示**，
 * 放不下时按可用宽度**自动缩字号**（下限 [MIN_SCALE]），仍放不下才省略号。数字绝不换行
 * （换行会同时破坏数值可读性与卡片高度一致性）。
 */
@Composable
fun WzCard(
    modifier: Modifier = Modifier,
    testTag: String = "",
    /** 紧凑内边距（窄窗或弹窗内使用）。 */
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WzTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(10.dp))
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 10.dp else 14.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** 卡片标签（次要文字，单行）。 */
@Composable
fun WzCardLabel(text: String, modifier: Modifier = Modifier) {
    val colors = WzTheme.colors
    Text(
        text = text,
        color = colors.ink2,
        style = WzTheme.typography.caption,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** 最小缩放比例（再小就不缩了，改省略号——保证可读性下限）。 */
private const val MIN_SCALE = 0.68f

/**
 * 指标数字：**单行 + 自动缩字号**。按可用宽度逐档缩放（1.0 → 0.9 → 0.82 → 0.74 → [MIN_SCALE]），
 * 仍放不下时省略号。用于净值/本金/盈亏等大号金额。
 */
@Composable
fun WzMetric(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color? = null,
    testTag: String = "",
) {
    val colors = WzTheme.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val scales = listOf(1f, 0.9f, 0.82f, 0.74f, MIN_SCALE)
        var chosen by remember(text, maxWidthPx) { mutableStateOf(1f) }
        // 选第一个能放下的档位（全部放不下 → 最小档 + 省略号）
        val fitted = scales.firstOrNull { scale ->
            val measured = measurer.measure(
                text = text,
                style = style.copy(fontSize = style.fontSize * scale),
                maxLines = 1,
                softWrap = false,
            )
            measured.size.width <= maxWidthPx
        } ?: MIN_SCALE
        if (chosen != fitted) chosen = fitted
        Text(
            text = text,
            color = color ?: colors.ink,
            style = style.copy(fontSize = style.fontSize * chosen),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier,
        )
    }
}

/** 单行文本（表格/图例/列表通用）：超宽省略号 + **悬停显示完整内容**（DEF-35）。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SingleLineText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = WzTheme.typography.body,
    color: Color? = null,
    testTag: String = "",
) {
    val colors = WzTheme.colors
    var truncated by remember(text) { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        Text(
            text = text,
            color = color ?: colors.ink,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> truncated = result.didOverflowWidth },
            modifier = modifier.then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        )
    }
    // 仅在确实被截断时提供悬停提示，避免无意义的气泡
    if (truncated) {
        androidx.compose.foundation.TooltipArea(tooltip = { HoverTooltip(text) }, content = content)
    } else {
        content()
    }
}

/** 悬停提示气泡（统一外观）。 */
@Composable
fun HoverTooltip(text: String) {
    val colors = WzTheme.colors
    Text(
        text = text,
        color = colors.ink,
        style = WzTheme.typography.caption,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface2)
            .border(1.dp, colors.line, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("hover-tooltip"),
    )
}

/** 列表单元线（DEF-38：各页列表统一加分隔线；1px `line` 色，克制使用）。 */
@Composable
fun rowDivider(modifier: Modifier = Modifier) {
    val colors = WzTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.line),
    )
}
