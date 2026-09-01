package com.wuzhufolio.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzColumn
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzTable
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.components.WzToastKind
import com.wuzhufolio.ui.shell.ShellViewModel
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 组件走查页（T0.6 验收载体）：核心组件库在双主题下的渲染走查。
 * 顶栏主题切换即时重渲染；对比度数值由 ContrastTest 守护。
 */
@Composable
fun ComponentGallery(viewModel: ShellViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("component-gallery"),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        TypographySection()
        ButtonsSection()
        TextFieldsSection()
        TableSection()
        ModalSection()
        ToastSection(viewModel)
        SemanticColorsSection(viewModel)
    }
}

@Composable
private fun GallerySection(title: String, content: @Composable () -> Unit) {
    val colors = WzTheme.colors
    Column {
        Text(text = title, color = colors.ink2, style = WzTheme.typography.tableHeader)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(colors.surface, RoundedCornerShape(10.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun TypographySection() {
    val colors = WzTheme.colors
    GallerySection("字体层级（design-tokens §3）") {
        Text(text = "$120,464.77", color = colors.ink, style = WzTheme.typography.display)
        Text(text = "页面标题 20/600", color = colors.ink, style = WzTheme.typography.pageTitle)
        Text(text = "正文 14：隐私、本地、可信、数据优先。", color = colors.ink, style = WzTheme.typography.body)
        Text(
            text = "表格数字（等宽 tnum）：0.05432100  +31.26%",
            color = colors.ink,
            style = WzTheme.typography.tableNumber,
        )
        Text(text = "注释/时间戳 11：2026-08-31 12:00 UTC", color = colors.ink3, style = WzTheme.typography.caption)
    }
}

@Composable
private fun ButtonsSection() {
    GallerySection("按钮（主/次/危险 + 禁用）") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WzButton(text = "主按钮", onClick = {}, testTag = "gallery-btn-primary")
            WzButton(text = "次按钮", onClick = {}, variant = WzButtonVariant.Secondary)
            WzButton(text = "危险按钮", onClick = {}, variant = WzButtonVariant.Danger)
            WzButton(text = "禁用", onClick = {}, enabled = false)
        }
    }
}

@Composable
private fun TextFieldsSection() {
    var normal by remember { mutableStateOf("") }
    var withError by remember { mutableStateOf("0.00") }
    GallerySection("输入框（正常 / 错误态）") {
        WzTextField(
            value = normal,
            onValueChange = { normal = it },
            label = "账户名称",
            placeholder = "例如：主账户",
            testTag = "gallery-input",
        )
        WzTextField(
            value = withError,
            onValueChange = { withError = it },
            label = "数量",
            error = "数量必须大于 0",
        )
    }
}

@Composable
private fun TableSection() {
    val colors = WzTheme.colors
    var selected by remember { mutableStateOf(-1) }
    val rows = listOf(
        listOf("BTC", "0.50000000", "+$1,234.56"),
        listOf("ETH", "3.21000000", "-$45.20"),
        listOf("USDT", "46,811.14", "+$0.00"),
    )
    GallerySection("数据表（表头 / hover / 选中行）") {
        WzTable(
            columns = listOf(WzColumn("币种"), WzColumn("数量"), WzColumn("24h 盈亏")),
            rowCount = rows.size,
            selectedRow = selected,
            onRowClick = { selected = it },
            testTag = "gallery-table",
            maxHeight = 280.dp,
        ) { row, column ->
            val amountColored = column == 2
            Text(
                text = rows[row][column],
                color = when {
                    amountColored && rows[row][column].startsWith("+") -> colors.gain
                    amountColored && rows[row][column].startsWith("-") -> colors.loss
                    else -> colors.ink
                },
                style = if (column == 0) WzTheme.typography.body else WzTheme.typography.tableNumber,
            )
        }
    }
}

@Composable
private fun ModalSection() {
    var open by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf("") }
    GallerySection("Modal（esc / 遮罩点击 / 关闭按钮可关）") {
        WzButton(text = "打开 Modal", onClick = { open = true }, testTag = "gallery-open-modal")
        if (open) {
            WzModal(title = "示例 Modal", onDismiss = { open = false }, testTag = "gallery-modal") {
                WzTextField(value = field, onValueChange = { field = it }, label = "字段", placeholder = "输入内容")
            }
        }
    }
}

@Composable
private fun ToastSection(viewModel: ShellViewModel) {
    GallerySection("Toast（成功 / 失败，3s 自动消失）") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WzButton(
                text = "成功 Toast",
                onClick = { viewModel.showToast(WzToastKind.Success, "已保存（示例）") },
                testTag = "gallery-toast-success",
            )
            WzButton(
                text = "失败 Toast",
                onClick = { viewModel.showToast(WzToastKind.Failure, "同步失败（示例）") },
                variant = WzButtonVariant.Danger,
                testTag = "gallery-toast-failure",
            )
        }
    }
}

@Composable
private fun SemanticColorsSection(viewModel: ShellViewModel) {
    val colors = WzTheme.colors
    GallerySection("语义色与盈亏配色方案（design-tokens §2.3，强制 +/- 符号）") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PnlColorScheme.entries.forEach { scheme ->
                WzButton(
                    text = when (scheme) {
                        PnlColorScheme.GREEN_UP -> "绿涨红跌"
                        PnlColorScheme.RED_UP -> "红涨绿跌"
                        PnlColorScheme.COLORBLIND -> "色盲友好"
                    },
                    onClick = { viewModel.setPnlScheme(scheme) },
                    variant = WzButtonVariant.Secondary,
                    testTag = "gallery-pnl-" + scheme.name,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = "+31.26%", color = colors.gain, style = WzTheme.typography.display)
            Text(text = "-$4,915.00", color = colors.loss, style = WzTheme.typography.display)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                "gain" to colors.gain,
                "loss" to colors.loss,
                "warn" to colors.warn,
                "accent" to colors.accent,
            ).forEach { (name, color) ->
                Column {
                    Box(modifier = Modifier.size(40.dp).background(color, RoundedCornerShape(7.dp)))
                    Text(text = name, color = colors.ink3, style = WzTheme.typography.caption)
                }
            }
        }
    }
}
