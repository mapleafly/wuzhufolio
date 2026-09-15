package com.wuzhufolio.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 表格列边界计算单测（DEF-40：纵向单元线画在列边界上）。
 *
 * 口径：宽窗按 `flex` 比例分配、窄窗按最小宽累加；最后一项 = 表格宽度（**不画**线，由调用方 dropLast(1)）。
 */
class AdaptiveTableGridTest {

    private val columns = listOf(
        TableColumn(100.dp),
        TableColumn(50.dp),
        TableColumn(50.dp),
    )

    @Test
    fun `boundaries follow min widths when scrolling`() {
        val boundaries = columnBoundariesOf(tableWidth = 200.dp, columns = columns, scrollable = true)
        assertEquals(listOf(100.dp, 150.dp, 200.dp), boundaries)
    }

    @Test
    fun `boundaries follow flex ratio when fitting`() {
        // flex 默认 = 最小宽数值（100/50/50）→ 按 2:1:1 比例分配 400dp
        val boundaries = columnBoundariesOf(tableWidth = 400.dp, columns = columns, scrollable = false)
        assertEquals(3, boundaries.size)
        assertEquals(200f, boundaries[0].value, 0.5f)
        assertEquals(300f, boundaries[1].value, 0.5f)
        assertEquals(400f, boundaries[2].value, 0.5f)
        // 每段宽度 ≥ 该列最小宽（宽窗档下不裁列内容的保证）
        assertTrue(boundaries[0].value >= 100f)
        assertTrue(boundaries[1].value - boundaries[0].value >= 50f)
        assertTrue(boundaries[2].value - boundaries[1].value >= 50f)
    }

    @Test
    fun `empty columns degrade to a single boundary`() {
        assertEquals(listOf(120.dp), columnBoundariesOf(tableWidth = 120.dp, columns = emptyList(), scrollable = true))
    }
}
