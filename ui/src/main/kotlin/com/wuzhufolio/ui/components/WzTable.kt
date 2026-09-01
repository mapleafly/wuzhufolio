package com.wuzhufolio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.theme.WzTheme

/** 表格列定义。 */
data class WzColumn(val title: String, val weight: Float = 1f)

/**
 * 数据表（design-tokens §4.2）：表头 sticky 观感（次表面底）、行 hover 高亮、选中行高亮。
 * 数值列内容请用 tableNumber 样式渲染（等宽 tnum），由调用方传入单元格 composable。
 */
@Composable
fun WzTable(
    columns: List<WzColumn>,
    rowCount: Int,
    modifier: Modifier = Modifier,
    selectedRow: Int = -1,
    onRowClick: ((Int) -> Unit)? = null,
    testTag: String? = null,
    // 位于可滚动容器内时必须给有限高度（LazyColumn 不能接无限高约束）；页面槽位有界时保持默认即可
    maxHeight: Dp = Dp.Infinity,
    cell: @Composable (row: Int, column: Int) -> Unit,
) {
    val colors = WzTheme.colors
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .border(1.dp, colors.line, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        // 表头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface2)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            columns.forEach { column ->
                Text(
                    text = column.title.uppercase(),
                    color = colors.ink3,
                    style = WzTheme.typography.tableHeader,
                    modifier = Modifier.weight(column.weight),
                )
            }
        }
        // 数据行
        LazyColumn(modifier = Modifier.heightIn(max = maxHeight)) {
            itemsIndexed((0 until rowCount).toList()) { index, _ ->
                val interactionSource = remember { MutableInteractionSource() }
                val hovered by interactionSource.collectIsHoveredAsState()
                val rowBg = when {
                    index == selectedRow -> colors.surface2
                    hovered -> colors.surface2
                    else -> colors.surface
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .hoverable(interactionSource)
                        .then(
                            if (onRowClick != null) {
                                Modifier.clickable { onRowClick(index) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("wz-table-row-" + index),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    columns.forEachIndexed { colIndex, column ->
                        androidx.compose.foundation.layout.Box(Modifier.weight(column.weight)) {
                            cell(index, colIndex)
                        }
                    }
                }
            }
        }
    }
}
