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
import com.wuzhufolio.ui.i18n.galleryStrings
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
    val strings = galleryStrings
    GallerySection(strings.typographySection) {
        Text(text = "$120,464.77", color = colors.ink, style = WzTheme.typography.display)
        Text(text = strings.pageTitleSample, color = colors.ink, style = WzTheme.typography.pageTitle)
        Text(text = strings.bodySample, color = colors.ink, style = WzTheme.typography.body)
        Text(
            text = strings.tableNumberSample,
            color = colors.ink,
            style = WzTheme.typography.tableNumber,
        )
        Text(text = strings.captionSample, color = colors.ink3, style = WzTheme.typography.caption)
    }
}

@Composable
private fun ButtonsSection() {
    val strings = galleryStrings
    GallerySection(strings.buttonsSection) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WzButton(text = strings.primaryButton, onClick = {}, testTag = "gallery-btn-primary")
            WzButton(text = strings.secondaryButton, onClick = {}, variant = WzButtonVariant.Secondary)
            WzButton(text = strings.dangerButton, onClick = {}, variant = WzButtonVariant.Danger)
            WzButton(text = strings.disabledButton, onClick = {}, enabled = false)
        }
    }
}

@Composable
private fun TextFieldsSection() {
    var normal by remember { mutableStateOf("") }
    var withError by remember { mutableStateOf("0.00") }
    val strings = galleryStrings
    GallerySection(strings.textFieldsSection) {
        WzTextField(
            value = normal,
            onValueChange = { normal = it },
            label = strings.accountNameLabel,
            placeholder = strings.accountNamePlaceholder,
            testTag = "gallery-input",
        )
        WzTextField(
            value = withError,
            onValueChange = { withError = it },
            label = strings.quantityLabel,
            error = strings.quantityError,
        )
    }
}

@Composable
private fun TableSection() {
    val colors = WzTheme.colors
    val strings = galleryStrings
    var selected by remember { mutableStateOf(-1) }
    val rows = listOf(
        listOf("BTC", "0.50000000", "+$1,234.56"),
        listOf("ETH", "3.21000000", "-$45.20"),
        listOf("USDT", "46,811.14", "+$0.00"),
    )
    GallerySection(strings.tableSection) {
        WzTable(
            columns = listOf(
                WzColumn(strings.symbolColumn),
                WzColumn(strings.quantityLabel),
                WzColumn(strings.pnl24hColumn),
            ),
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
    val strings = galleryStrings
    GallerySection(strings.modalSection) {
        WzButton(text = strings.openModal, onClick = { open = true }, testTag = "gallery-open-modal")
        if (open) {
            WzModal(title = strings.modalTitle, onDismiss = { open = false }, testTag = "gallery-modal") {
                WzTextField(
                    value = field,
                    onValueChange = { field = it },
                    label = strings.fieldLabel,
                    placeholder = strings.fieldPlaceholder,
                )
            }
        }
    }
}

@Composable
private fun ToastSection(viewModel: ShellViewModel) {
    val strings = galleryStrings
    GallerySection(strings.toastSection) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WzButton(
                text = strings.successToast,
                onClick = { viewModel.showToast(WzToastKind.Success, strings.successToastSample) },
                testTag = "gallery-toast-success",
            )
            WzButton(
                text = strings.failureToast,
                onClick = { viewModel.showToast(WzToastKind.Failure, strings.failureToastSample) },
                variant = WzButtonVariant.Danger,
                testTag = "gallery-toast-failure",
            )
        }
    }
}

@Composable
private fun SemanticColorsSection(viewModel: ShellViewModel) {
    val colors = WzTheme.colors
    val strings = galleryStrings
    GallerySection(strings.semanticColorsSection) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PnlColorScheme.entries.forEach { scheme ->
                WzButton(
                    text = when (scheme) {
                        PnlColorScheme.GREEN_UP -> strings.pnlGreenUp
                        PnlColorScheme.RED_UP -> strings.pnlRedUp
                        PnlColorScheme.COLORBLIND -> strings.pnlColorblind
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
