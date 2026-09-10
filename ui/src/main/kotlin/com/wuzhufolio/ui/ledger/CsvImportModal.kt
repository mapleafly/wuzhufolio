package com.wuzhufolio.ui.ledger

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.ledger.AmbiguousTicker
import com.wuzhufolio.domain.ledger.CsvPreview
import com.wuzhufolio.domain.ledger.CsvPreviewRow
import com.wuzhufolio.domain.ledger.CsvRowStatus
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.i18n.WzFormat
import com.wuzhufolio.ui.theme.WzTheme

/**
 * CSV 导入向导（PRD 故事 2.3 / §9.6「导入交易」）：文件选择 -> 模板下载 -> 解析预览
 * （影响摘要：新增/疑似重复/未解析/格式错误）-> 消歧 ticker 选择 -> 确认导入（LENIENT）。
 */
@Composable
fun CsvImportModal(
    state: CsvUiState,
    vm: TransactionsViewModel,
    onDismiss: () -> Unit,
) {
    val colors = WzTheme.colors
    WzModal(
        title = TransactionCopy.CSV_TITLE,
        onDismiss = onDismiss,
        width = 760.dp,
        testTag = "csv-modal",
    ) {
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(end = 12.dp),
        ) {
            // 文件 + 模板下载
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WzTextField(
                    value = state.fileName ?: "",
                    onValueChange = {},
                    label = TransactionCopy.CSV_FILE_LABEL,
                    placeholder = TransactionCopy.CSV_NO_FILE,
                    modifier = Modifier.weight(1f),
                    testTag = "csv-file-name",
                )
                WzButton(
                    text = if (state.busy) TransactionCopy.CSV_PARSING else TransactionCopy.CSV_CHOOSE,
                    onClick = vm::pickAndParseCsv,
                    enabled = !state.busy && !state.importing,
                    testTag = "csv-pick",
                )
                WzButton(
                    text = TransactionCopy.CSV_DOWNLOAD_TEMPLATE,
                    onClick = vm::downloadTemplate,
                    variant = WzButtonVariant.Secondary,
                    testTag = "csv-template",
                )
            }
            Text(
                text = TransactionCopy.CSV_FILE_HINT,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp),
            )

            val preview = state.preview
            if (preview != null) {
                // 影响摘要（PRD 故事 2.3-3）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .background(colors.surface2)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("csv-impact"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = TransactionCopy.CSV_IMPACT,
                        color = colors.ink2,
                        style = WzTheme.typography.body,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = TransactionCopy.csvImpactLine(
                            added = preview.newRows,
                            duplicates = preview.duplicateRows,
                            unresolved = preview.unresolvedRows,
                            errors = preview.errorRows.size,
                        ),
                        color = colors.ink,
                        style = WzTheme.typography.bodyStrong,
                    )
                }

                // 涉及币种持仓变化概览
                if (preview.affectedCoins.isNotEmpty()) {
                    Text(
                        text = TransactionCopy.csvHoldingsChange(
                            preview.affectedCoins.take(8)
                                .map { it.symbol + " " + signedQty(it.deltaQuantity) },
                        ),
                        color = colors.ink2,
                        style = WzTheme.typography.caption,
                        modifier = Modifier.padding(top = 8.dp).testTag("csv-affected"),
                    )
                }
                if (preview.anomalousCoins.isNotEmpty()) {
                    Text(
                        text = TransactionCopy.csvAnomalyLine(preview.anomalousCoins.size, preview.anomalousCoins),
                        color = colors.warn,
                        style = WzTheme.typography.caption,
                        modifier = Modifier.padding(top = 6.dp).testTag("csv-anomaly"),
                    )
                }

                // 预览表（时间/交易对/类型/价格/数量/去重状态）
                Column(modifier = Modifier.padding(top = 10.dp).testTag("csv-preview")) {
                    CsvPreviewHeader()
                    preview.rows.forEach { row -> CsvPreviewLine(row, state.includeKeys, vm::toggleInclude) }
                }
                if (preview.duplicateRows > 0) {
                    Text(
                        text = TransactionCopy.CSV_DUP_HINT,
                        color = colors.ink3,
                        style = WzTheme.typography.caption,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // 歧义 ticker 消歧（PRD 故事 2.3-4 / 消歧规则④）
                Column(modifier = Modifier.padding(top = 12.dp).testTag("csv-ambiguity")) {
                    Text(
                        text = TransactionCopy.CSV_AMBIGUOUS_TITLE,
                        color = colors.ink,
                        style = WzTheme.typography.bodyStrong,
                    )
                    if (preview.ambiguous.isEmpty()) {
                        Text(
                            text = TransactionCopy.CSV_NO_AMBIGUOUS,
                            color = colors.ink3,
                            style = WzTheme.typography.caption,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        Text(
                            text = TransactionCopy.CSV_AMBIGUOUS_HINT.replace("N", preview.ambiguous.size.toString()),
                            color = colors.ink3,
                            style = WzTheme.typography.caption,
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                        )
                        preview.ambiguous.forEach { ticker ->
                            AmbiguityPicker(
                                ticker = ticker,
                                selected = state.choices[ticker.exchange + "|" + ticker.asset],
                                onSelect = { vm.setAmbiguityChoice(ticker.exchange + "|" + ticker.asset, it) },
                            )
                        }
                    }
                }
            }
        }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }

        // 底部动作
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            WzButton(
                text = TransactionCopy.BTN_CANCEL,
                onClick = onDismiss,
                variant = WzButtonVariant.Secondary,
                testTag = "csv-cancel",
            )
            WzButton(
                text = if (state.importing) TransactionCopy.CSV_IMPORTING else TransactionCopy.CSV_CONFIRM,
                onClick = vm::confirmImport,
                enabled = state.preview != null && !state.importing,
                modifier = Modifier.padding(start = 8.dp),
                testTag = "csv-confirm",
            )
        }
    }
}

@Composable
private fun CsvPreviewHeader() {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("csv-preview-header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            TransactionCopy.CSV_COL_TIME,
            color = colors.ink2,
            style = WzTheme.typography.caption,
            modifier = Modifier.weight(1.4f),
        )
        Text(
            TransactionCopy.CSV_COL_PAIR,
            color = colors.ink2,
            style = WzTheme.typography.caption,
            modifier = Modifier.weight(1.6f),
        )
        Text(
            TransactionCopy.CSV_COL_SIDE,
            color = colors.ink2,
            style = WzTheme.typography.caption,
            modifier = Modifier.weight(0.8f),
        )
        Text(
            TransactionCopy.CSV_COL_PRICE,
            color = colors.ink2,
            style = WzTheme.typography.caption,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            TransactionCopy.CSV_COL_QTY,
            color = colors.ink2,
            style = WzTheme.typography.caption,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            TransactionCopy.CSV_COL_DEDUP,
            color = colors.ink2,
            style = WzTheme.typography.caption,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun CsvPreviewLine(row: CsvPreviewRow, includeKeys: Set<String>, onToggleInclude: (String) -> Unit) {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .testTag("csv-row-" + row.rowKey),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            timeText(row.time),
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.weight(1.4f),
        )
        Text(row.pair, color = colors.ink, style = WzTheme.typography.body, modifier = Modifier.weight(1.6f))
        Text(
            if (row.side.name == "BUY") TransactionCopy.FILTER_BUY else TransactionCopy.FILTER_SELL,
            color = if (row.side.name == "BUY") colors.gain else colors.loss,
            style = WzTheme.typography.body,
            modifier = Modifier.weight(0.8f),
        )
        Text(row.price.stripTrailingZeros().toPlainString(), color = colors.ink, style = WzTheme.typography.body,
            modifier = Modifier.weight(1.2f))
        Text(row.quantity.stripTrailingZeros().toPlainString(), color = colors.ink, style = WzTheme.typography.body,
            modifier = Modifier.weight(1.2f))
        when (row.status) {
            CsvRowStatus.IMPORT -> Text(
                TransactionCopy.CSV_NEW, color = colors.gain, style = WzTheme.typography.caption,
                modifier = Modifier.weight(1.2f),
            )
            CsvRowStatus.DUPLICATE -> {
                val included = row.rowKey in includeKeys
                Row(
                    modifier = Modifier
                        .weight(1.2f)
                        .clickable { onToggleInclude(row.rowKey) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (included) "☑" else "☐",
                        color = colors.accent,
                        style = WzTheme.typography.body,
                    )
                    Text(
                        TransactionCopy.CSV_DUPLICATE,
                        color = colors.warn,
                        style = WzTheme.typography.caption,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            CsvRowStatus.UNRESOLVED -> Text(
                TransactionCopy.CSV_UNRESOLVED, color = colors.ink3, style = WzTheme.typography.caption,
                modifier = Modifier.weight(1.2f),
            )
        }
    }
}

/** 歧义 ticker 候选选择（点击选中，固化 MANUAL 映射）。 */
@Composable
private fun AmbiguityPicker(
    ticker: AmbiguousTicker,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    val colors = WzTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("csv-ambig-" + ticker.exchange + "-" + ticker.asset),
    ) {
        Text(
            text = ticker.exchange + " / " + ticker.asset,
            color = colors.ink,
            style = WzTheme.typography.bodyStrong,
        )
        ticker.candidates.forEach { (cgId, label) ->
            val isSelected = cgId == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(cgId) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isSelected) "●" else "○",
                    color = if (isSelected) colors.accent else colors.ink3,
                    style = WzTheme.typography.body,
                )
                Text(
                    text = label,
                    color = colors.ink,
                    style = WzTheme.typography.body,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

private fun signedQty(q: java.math.BigDecimal): String =
    (if (q.signum() >= 0) "+" else "") + q.stripTrailingZeros().toPlainString()

private fun timeText(at: java.time.Instant): String = WzFormat.dateTime(at)
