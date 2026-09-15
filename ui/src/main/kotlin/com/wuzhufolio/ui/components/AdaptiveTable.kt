package com.wuzhufolio.ui.components

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 自适应数据表（P6 人工门第六轮 · DEF-28/29）。
 *
 * **问题**：资产列表/交易管理的表格用 `Modifier.weight(...)` 分配列宽，窗口一窄（1280×800，尤其 1024×768）
 * 每列被压到低于内容宽度 → 单元格文字**换行/竖排**（「0.00022336BNB」断成两行、「成本不可靠」竖着写、
 * 删除按钮被压成竖排），行高参差、表格失去规整。
 *
 * **口径**：每列声明**最小宽度**与**（宽窗下的）权重**：
 * - 可用宽度 ≥ 各列最小宽度之和 → 按权重铺满（保持现有视觉，无滚动条）；
 * - 否则 → **整表横向滚动**、列宽取最小宽度，行高一致、信息不丢（不做省略号截断：财务数据截断比滚动更糟）。
 *
 * 用法：
 * ```
 * AdaptiveTable(columns = ASSET_COLUMNS) { table ->
 *     Row(modifier = table.row()) { cells.forEachIndexed { i, _ -> Cell(modifier = tableCell(table, i)) } }
 *     rows.forEach { ... }                                  // 行同样用 table.row()/tableCell(table, i)
 * }
 * ```
 * 单元格文字**必须** `maxLines = 1`（`softWrap = false`）——表宽已保底，再换行说明列宽声明需要调整。
 */
@Immutable
data class TableColumn(
    /** 该列最小宽度（窄窗滚动模式下即列宽）。 */
    val minWidth: Dp,
    /**
     * 宽窗模式下的相对弹性。**默认 = 最小宽的比例**（`minWidth.value`）——
     * 这样「可用宽度 ≥ 各列最小宽之和」时，每列分到的宽度比例 ≥ 其最小宽占比，
     * 即**每列都必然不小于自己的最小宽**（此前固定 weight 会让窄列抢走宽列空间，
     * 高 DPI 下币种列的第三枚徽标因此被裁掉，DEF-31）。
     */
    val flex: Float = minWidth.value,
)

/** 表格作用域：给出行与单元格的尺寸修饰符（窄窗滚动 / 宽窗铺满两态）。 */
@Stable
class AdaptiveTableScope internal constructor(
    private val scrollable: Boolean,
    val tableWidth: Dp,
    private val columns: List<TableColumn>,
    private val divider: Boolean,
    private val verticalDivider: Boolean,
) {
    /** 各列右边界（x，dp）——纵向单元线画在这些位置（最后一列右边界不画）。 */
    internal val columnBoundaries: List<Dp> = columnBoundariesOf(tableWidth, columns, scrollable)
    /** 行容器修饰符（表头行与数据行都要用）。数据行请用 [dataRow] 以带上单元线。 */
    fun row(): Modifier = if (scrollable) Modifier.width(tableWidth) else Modifier.fillMaxWidth()

    /** 数据行修饰符：行宽 + 底部横向单元线（DEF-38）+ 列间纵向单元线（DEF-40）。 */
    @Composable
    fun dataRow(): Modifier {
        val colors = com.wuzhufolio.ui.theme.WzTheme.colors
        return row().drawBehind {
            drawTableGrid(colors.line, divider, verticalDivider, columnBoundaries, bottomLine = true)
        }
    }

    /** 表头行修饰符：行宽 + 列间纵向单元线（表头不画底部线，交由表头自身下边距处理）。 */
    @Composable
    fun headerRow(): Modifier {
        val colors = com.wuzhufolio.ui.theme.WzTheme.colors
        return row().drawBehind {
            drawTableGrid(colors.line, divider = false, verticalDivider, columnBoundaries, bottomLine = false)
        }
    }

    /**
     * 第 [index] 个单元格的修饰符计算（宽窗模式用 `weight` 分配剩余宽度，需要 [row] 作用域）。
     * 调用方用 [tableCell]（`RowScope` 扩展）更顺手。
     */
    fun cellModifier(row: RowScope, index: Int): Modifier = if (scrollable) {
        Modifier.width(columns[index].minWidth)
    } else {
        with(row) { Modifier.weight(columns[index].flex) }
    }
}

@Composable
fun AdaptiveTable(
    columns: List<TableColumn>,
    modifier: Modifier = Modifier,
    /** 是否给每行加**横向**单元线（DEF-38：列表可读性；默认开）。 */
    divider: Boolean = true,
    /** 是否给列之间加**纵向**单元线（DEF-40：表格网格；默认开）。 */
    verticalDivider: Boolean = true,
    content: @Composable ColumnScope.(AdaptiveTableScope) -> Unit,
) {
    val hScroll = rememberScrollState()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val minTotal = columns.fold(0.dp) { acc, column -> acc + column.minWidth }
        val scrollable = maxWidth < minTotal
        val scope = AdaptiveTableScope(
            scrollable = scrollable,
            tableWidth = if (scrollable) minTotal else maxWidth,
            columns = columns,
            divider = divider,
            verticalDivider = verticalDivider,
        )
        Column(
            modifier = if (scrollable) {
                Modifier
                    .width(minTotal)
                    .fillMaxHeight()
                    .horizontalScroll(hScroll)
            } else {
                Modifier.fillMaxSize()
            },
            verticalArrangement = Arrangement.Top,
        ) {
            content(scope)
        }
        if (scrollable) {
            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(hScroll),
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            )
        }
    }
}

/**
 * 各列右边界（dp）：宽窗按 `flex` 比例、窄窗按最小宽累加；最后一项 = 表格宽度（不画线）。
 * 抽成纯函数以便单测（DEF-40）。
 */
internal fun columnBoundariesOf(tableWidth: Dp, columns: List<TableColumn>, scrollable: Boolean): List<Dp> {
    if (columns.isEmpty()) return listOf(tableWidth)
    val widths = if (scrollable) {
        columns.map { it.minWidth }
    } else {
        val totalFlex = columns.sumOf { it.flex.toDouble() }.takeIf { it > 0.0 } ?: 1.0
        columns.map { tableWidth * (it.flex / totalFlex).toFloat() }
    }
    var acc = 0.dp
    return widths.map { width ->
        acc += width
        acc
    }
}

/** 画表格网格线：横向（行底）+ 纵向（列边界，跳过最后一列右边界）。 */
private fun DrawScope.drawTableGrid(
    color: androidx.compose.ui.graphics.Color,
    divider: Boolean,
    verticalDivider: Boolean,
    boundaries: List<Dp>,
    bottomLine: Boolean,
) {
    val stroke = 1.dp.toPx()
    if (divider && bottomLine) {
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, size.height - stroke / 2f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height - stroke / 2f),
            strokeWidth = stroke,
        )
    }
    if (verticalDivider && boundaries.size > 1) {
        boundaries.dropLast(1).forEach { boundary ->
            val x = boundary.toPx() - stroke / 2f
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, size.height),
                strokeWidth = stroke,
            )
        }
    }
}

/** 第 [index] 列单元格修饰符（在 `Row` 内调用，例如 `modifier = tableCell(table, 0)`）。 */
fun RowScope.tableCell(table: AdaptiveTableScope, index: Int): Modifier = table.cellModifier(this, index)

/** 常用最小宽度（各表共享，改口径只改这里）。 */
object TableWidths {
    /** 交易对/币种 + 徽标（如「BTC/USDT」+「估算中」或「BTC」+ 持仓异常/估算中/成本不可靠 三枚标签）。 */
    val COIN: Dp = 260.dp

    /** 数量/价格/成本/手续费/已实现等数字列（8 位小数 + 千分位不换行；12.5sp 等宽字下实测 ~90dp）。 */
    val NUMBER: Dp = 96.dp

    /** 金额列（市值/浮盈：含符号与两位小数）。 */
    val AMOUNT: Dp = 104.dp

    /** 方向/类型等短标签列。 */
    val TAG: Dp = 56.dp

    /** 交易所列。 */
    val EXCHANGE: Dp = 84.dp

    /** 时间列（MM-dd HH:mm）。 */
    val TIME: Dp = 96.dp

    /** 操作列（编辑 + 删除按钮成对；低于此宽度按钮文字会竖排）。 */
    val ACTIONS: Dp = 120.dp
}
