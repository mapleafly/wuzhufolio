@file:Suppress("TooManyFunctions") // 聚合页公共视觉件集合（卡片/环形图/徽标/表头），非单一逻辑单元

package com.wuzhufolio.ui.portfolio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.i18n.WzFormat
import com.wuzhufolio.ui.i18n.portfolioStrings
import com.wuzhufolio.ui.theme.WzTheme
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 聚合页公共视觉件（M12 T12.1）。色值与几何**取自 P1 原型唯一真源**
 * `docs/design/prototype/wuzhufolio-light.html`（环形图 palette / 半径 / 中心文案；卡片层级），
 * 保证「聚合页与原型截图走查一致」可核对。
 */

/** 统计卡（原型 `.card`：label + big + delta）。 */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    deltaColor: Color? = null,
    small: Boolean = false,
    testTag: String = "",
) {
    val colors = WzTheme.colors
    Column(
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
    ) {
        Text(text = label, color = colors.ink2, style = WzTheme.typography.caption)
        Text(
            text = value,
            color = colors.ink,
            style = if (small) WzTheme.typography.bodyStrong else WzTheme.typography.display,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (delta != null) {
            Text(
                text = delta,
                color = deltaColor ?: colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 盈亏颜色（PRD 无障碍基线：颜色仅辅助语义，数值恒带 +/- 符号；由调用方同时给出符号）。 */
@Composable
fun pnlColor(value: BigDecimal?): Color {
    val colors = WzTheme.colors
    return when {
        value == null -> colors.ink3
        value.signum() > 0 -> colors.gain
        value.signum() < 0 -> colors.loss
        else -> colors.ink3
    }
}

/**
 * 资产分布环形图（ia.md §2.4 / PRD §7.2-1：低于小额阈值合计归「其他」，点击扇区高亮 + 浮窗）。
 *
 * 交互口径：
 * - 单击扇区/图例 → 选中（其余分区降透明度 0.35，选中项 1.0，与原型一致）；再次点击同一项取消；
 * - 选中后浮窗展示持有数量 / 总资产占比 / 持有量市值（原型 `.donut-pop` 三行）；
 * - 「其他」段为多币种合计，数量行显示「多币种合计」。
 *
 * 可访问性：Canvas 不可读，外层挂 [contentDescription]（分区数与总资产），图例为可点按文本项。
 */
@Composable
fun AssetDonut(
    slices: List<DistributionSlice>,
    totalValue: BigDecimal,
    fiat: String,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WzTheme.colors
    val dark = WzTheme.colors.bg.luminanceIsDark()
    val palette = if (dark) DARK_PALETTE else LIGHT_PALETTE
    val total = slices.fold(BigDecimal.ZERO) { acc, s -> acc + s.value }
    val description = portfolioStrings.donutAria + " · " + slices.size +
        " · " + WzFormat.amount(totalValue) + " " + fiat

    Row(
        modifier = modifier.fillMaxWidth().testTag("asset-donut"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .semantics { contentDescription = description }
                .pointerInput(slices, total) {
                    detectTapGestures { offset ->
                        val hit = hitSlice(offset, size.width.toFloat(), size.height.toFloat(), slices, total)
                        onSelect(if (hit == null || hit == selectedId) null else hit)
                    }
                }
                .testTag("donut-canvas"),
        ) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val strokePx = (OUTER_RADIUS - INNER_RADIUS) / VIEWBOX * size.minDimension
                val radius = (OUTER_RADIUS + INNER_RADIUS) / 2f / VIEWBOX * size.minDimension
                var start = -90f
                slices.forEachIndexed { index, slice ->
                    val sweep = if (total.signum() == 0) 0f else {
                        slice.value.divide(total, 8, RoundingMode.HALF_UP).toFloat() * 360f
                    }
                    val color = palette[index % palette.size]
                    val alpha = when {
                        selectedId == null -> 0.92f
                        selectedId == slice.cgId -> 1f
                        else -> 0.35f
                    }
                    drawArc(
                        color = color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(
                            (size.width - radius * 2) / 2f,
                            (size.height - radius * 2) / 2f,
                        ),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokePx),
                        alpha = alpha,
                    )
                    start += sweep
                }
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = portfolioStrings.totalAssetsInner, color = colors.ink2, style = WzTheme.typography.caption)
                Text(
                    text = WzFormat.amount(totalValue),
                    color = colors.ink,
                    style = WzTheme.typography.bodyStrong,
                    modifier = Modifier.testTag("donut-total"),
                )
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val selected = slices.firstOrNull { it.cgId == selectedId }
            if (selected != null) {
                DonutDetailCard(slice = selected, total = total, onClose = { onSelect(null) })
            }
            slices.forEachIndexed { index, slice ->
                DonutLegendRow(
                    slice = slice,
                    color = palette[index % palette.size],
                    share = if (total.signum() == 0) null else {
                        slice.value.multiply(BigDecimal(100)).divide(total, 8, RoundingMode.HALF_UP)
                    },
                    dimmed = selectedId != null && selectedId != slice.cgId,
                    onClick = { onSelect(if (selectedId == slice.cgId) null else slice.cgId) },
                )
            }
        }
    }
}

/** 环形图浮窗（原型 `.donut-pop`：持有数量 / 总资产占比 / 持有量市值 + 关闭钮）。 */
@Composable
private fun DonutDetailCard(slice: DistributionSlice, total: BigDecimal, onClose: () -> Unit) {
    val colors = WzTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface2, RoundedCornerShape(9.dp))
            .border(1.dp, colors.line, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("donut-pop"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = slice.detail ?: slice.label,
                color = colors.ink,
                style = WzTheme.typography.bodyStrong,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "×",
                color = colors.ink2,
                style = WzTheme.typography.body,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(horizontal = 6.dp)
                    .testTag("donut-pop-close"),
            )
        }
        DonutDetailRow(
            portfolioStrings.popHoldingQty,
            slice.quantity?.let { WzFormat.quantity(it) } ?: portfolioStrings.multiCoinTotal,
        )
        DonutDetailRow(
            portfolioStrings.popShare,
            if (total.signum() == 0) WzFormat.DASH else {
                WzFormat.percent(slice.value.multiply(BigDecimal(100)).divide(total, 8, RoundingMode.HALF_UP))
            },
        )
        DonutDetailRow(portfolioStrings.popMarketValue, WzFormat.amount(slice.value))
    }
}

@Composable
private fun DonutDetailRow(label: String, value: String) {
    val colors = WzTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(text = label, color = colors.ink2, style = WzTheme.typography.caption, modifier = Modifier.weight(1f))
        Text(text = value, color = colors.ink, style = WzTheme.typography.caption)
    }
}

@Composable
private fun DonutLegendRow(
    slice: DistributionSlice,
    color: Color,
    share: BigDecimal?,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    val colors = WzTheme.colors
    val alpha = if (dimmed) 0.5f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wzTextClickable(label = slice.label, onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag("donut-legend-" + slice.cgId),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(10.dp).background(color.copy(alpha = alpha), RoundedCornerShape(3.dp)))
        Text(
            text = slice.label,
            color = colors.ink.copy(alpha = alpha),
            style = WzTheme.typography.body,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        Text(
            text = WzFormat.amount(slice.value),
            color = colors.ink.copy(alpha = alpha),
            style = WzTheme.typography.caption,
        )
        Text(
            text = WzFormat.percent(share),
            color = colors.ink3.copy(alpha = alpha),
            style = WzTheme.typography.caption,
            modifier = Modifier.padding(start = 10.dp).width(56.dp),
        )
    }
}

/**
 * 文本型可点元素的统一交互修饰（M12 T12.3 a11y 基线）：
 * 键盘可达（clickable 自带 focusable）+ 焦点可见（accent 2dp 描边，等价 Web 的 `:focus-visible`）
 * + 按钮语义与无障碍点击标签（读屏可读「按 X 排序」一类动作说明）。
 */
@Composable
internal fun Modifier.wzTextClickable(label: String, onClick: () -> Unit): Modifier {
    val colors = WzTheme.colors
    var focused by remember { mutableStateOf(false) }
    return this
        .border(
            width = if (focused) 2.dp else 0.dp,
            color = if (focused) colors.accent else Color.Transparent,
            shape = RoundedCornerShape(4.dp),
        )
        .clickable(onClickLabel = label, onClick = onClick)
        .onFocusChanged { focused = it.isFocused }
        .semantics { role = Role.Button }
}

/** 空态占位（interaction.md §2.2：占位图 + 「暂无记录」）。 */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier, testTag: String = "empty-hint") {
    val colors = WzTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .padding(vertical = 32.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = colors.ink3, style = WzTheme.typography.body)
    }
}

/** 区块标题（原型 `.panel h3`）。 */
@Composable
fun PanelTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = WzTheme.colors.ink,
        style = WzTheme.typography.bodyStrong,
        modifier = modifier.padding(bottom = 10.dp),
    )
}

/** 数据表头单元（可排序；点击切换方向，title 提示）。 */
@Composable
fun SortableHeader(
    label: String,
    active: Boolean,
    ascending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "",
) {
    val colors = WzTheme.colors
    val arrow = if (!active) "" else if (ascending) " ▲" else " ▼"
    Text(
        text = label + arrow,
        color = if (active) colors.ink else colors.ink2,
        style = WzTheme.typography.caption,
        modifier = modifier
            .wzTextClickable(label = portfolioStrings.sortBy(label), onClick = onClick)
            .semantics { if (active) stateDescription = if (ascending) ASC_LABEL else DESC_LABEL }
            .padding(vertical = 8.dp)
            .testTag(testTag),
    )
}

/** 数据表头单元（不可排序）。 */
@Composable
fun PlainHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        color = WzTheme.colors.ink2,
        style = WzTheme.typography.caption,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

/** 徽标（持仓异常 / 无行情 / 估算中；色仅辅助，文字为载体）。 */
@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier, testTag: String = "") {
    Text(
        text = text,
        color = color,
        style = WzTheme.typography.caption,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag(testTag),
    )
}

/** 命中测试：返回被点中的分区 cgId（点在环带之外 / 无分区 → null）。 */
private fun hitSlice(
    offset: Offset,
    width: Float,
    height: Float,
    slices: List<DistributionSlice>,
    total: BigDecimal,
): String? = run {
    if (slices.isEmpty() || total.signum() == 0) return@run null
    val cx = width / 2f
    val cy = height / 2f
    val dx = offset.x - cx
    val dy = offset.y - cy
    val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    val scale = minOf(width, height) / VIEWBOX
    val inner = INNER_RADIUS * scale
    val outer = OUTER_RADIUS * scale
    if (distance < inner || distance > outer) return@run null
    var degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
    if (degrees < 0f) degrees += 360f
    var cursor = 0f
    slices.firstOrNull { slice ->
        val sweep = slice.value.divide(total, 8, RoundingMode.HALF_UP).toFloat() * 360f
        val hit = degrees >= cursor && degrees < cursor + sweep
        cursor += sweep
        hit
    }?.cgId
}

/** 亮度判定（选择环形图调色板；不引入色彩库，按相对亮度公式）。 */
private fun Color.luminanceIsDark(): Boolean {
    fun channel(v: Float): Double {
        val d = v.toDouble()
        return if (d <= 0.03928) d / 12.92 else sqrt(((d + 0.055) / 1.055).coerceAtLeast(0.0) * ((d + 0.055) / 1.055))
    }
    val luminance = 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    return luminance < 0.35
}

/** 排序状态的无障碍描述（读屏播报「升序/降序」）。 */
private const val ASC_LABEL = "ascending"

/** 排序状态的无障碍描述（降序）。 */
private const val DESC_LABEL = "descending"

/** 原型环形图几何（viewBox 200×200）。 */
private const val VIEWBOX = 200f
private const val OUTER_RADIUS = 92f
private const val INNER_RADIUS = 62f

/** 原型调色板（明/暗两档，逐字取自 `wuzhufolio-light.html` donut()）。 */
private val LIGHT_PALETTE = listOf(
    Color(0xFF1F5A48),
    Color(0xFF8A9A5B),
    Color(0xFFB08A3E),
    Color(0xFF9AA59C),
    Color(0xFF5B8A72),
    Color(0xFFC8C2B4),
)
private val DARK_PALETTE = listOf(
    Color(0xFFD0A85C),
    Color(0xFF6F8F6A),
    Color(0xFF9A7B4F),
    Color(0xFF7C8B80),
    Color(0xFF4CBF82),
    Color(0xFFB0895A),
)

/** 主题模式（供调用方按明暗取调色板做视觉断言；保留导出以便测试复算）。 */
internal fun paletteFor(themeMode: ThemeMode): List<Color> =
    if (themeMode == ThemeMode.DARK) DARK_PALETTE else LIGHT_PALETTE
