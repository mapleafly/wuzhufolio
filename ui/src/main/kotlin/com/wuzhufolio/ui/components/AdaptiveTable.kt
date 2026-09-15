package com.wuzhufolio.ui.components

import androidx.compose.foundation.HorizontalScrollbar
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
    /** 宽窗模式下的相对权重。 */
    val weight: Float = 1f,
)

/** 表格作用域：给出行与单元格的尺寸修饰符（窄窗滚动 / 宽窗铺满两态）。 */
@Stable
class AdaptiveTableScope internal constructor(
    private val scrollable: Boolean,
    val tableWidth: Dp,
    private val columns: List<TableColumn>,
) {
    /** 行容器修饰符（表头行与数据行都要用）。 */
    fun row(): Modifier = if (scrollable) Modifier.width(tableWidth) else Modifier.fillMaxWidth()

    /**
     * 第 [index] 个单元格的修饰符计算（宽窗模式用 `weight` 分配剩余宽度，需要 [row] 作用域）。
     * 调用方用 [tableCell]（`RowScope` 扩展）更顺手。
     */
    fun cellModifier(row: RowScope, index: Int): Modifier = if (scrollable) {
        Modifier.width(columns[index].minWidth)
    } else {
        with(row) { Modifier.weight(columns[index].weight) }
    }
}

@Composable
fun AdaptiveTable(
    columns: List<TableColumn>,
    modifier: Modifier = Modifier,
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

/** 第 [index] 列单元格修饰符（在 `Row` 内调用，例如 `modifier = tableCell(table, 0)`）。 */
fun RowScope.tableCell(table: AdaptiveTableScope, index: Int): Modifier = table.cellModifier(this, index)

/** 常用最小宽度（各表共享，改口径只改这里）。 */
object TableWidths {
    /** 交易对/币种 + 徽标（如「BTC/USDT」+「估算中」或「BTC」+ 持仓异常/估算中/成本不可靠 三枚标签）。 */
    val COIN: Dp = 240.dp

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
