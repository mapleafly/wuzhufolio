package com.wuzhufolio.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.TransactionLedgerService
import com.wuzhufolio.domain.ledger.TransactionRow
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.i18n.WzFormat
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 交易管理页（M7 · T7.4 · PRD §9.6/§7.2-3 + 原型交易页逐字口径）：
 * 命令区（添加/修改/删除/导入 + 搜索 + 类型筛选）+ 交易列表（选择框/交易对/类型/价格/数量/
 * 手续费/总价/交易所/时间/已实现盈亏 + 行内编辑/删除）；卖出记录行显示该笔已实现盈亏。
 * 表单与 CSV 向导为同窗口就地叠加弹窗（共性约束 7.3）。
 */
@Composable
fun TransactionsPage(
    service: TransactionLedgerService,
    pickCsvFile: () -> String?,
    pickTemplatePath: () -> String?,
    modifier: Modifier = Modifier,
) {
    val vm = remember { TransactionsViewModel(service, pickCsvFile, pickTemplatePath) }
    DisposableEffect(vm) {
        onDispose { vm.dispose() }
    }
    val state by vm.state.collectAsState()
    val colors = WzTheme.colors

    Box(modifier = modifier.fillMaxSize().testTag("transactions-page")) {
        // 标题/命令区/表头固定；仅数据行区域滚动（GUI 走查修复轮：滚动查看数据时操作区始终可见）
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(text = TransactionCopy.PAGE_TITLE, color = colors.ink, style = WzTheme.typography.pageTitle)
            Text(
                text = TransactionCopy.PAGE_SUB,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = TransactionCopy.PAGE_SUB_HINT,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            // 命令区（PRD §9.6）
            Toolbar(
                query = state.query,
                sideFilter = state.sideFilter,
                onQueryChange = vm::onQueryChange,
                onSideFilterChange = vm::onSideFilterChange,
                onAdd = vm::openAdd,
                onEdit = vm::requestEditSelected,
                onDelete = vm::requestDeleteSelected,
                onImport = vm::openCsv,
            )

            // 交易列表（仅本区域滚动）
            if (state.rows.isEmpty()) {
                Box(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.query.isBlank() && state.sideFilter == null) {
                            TransactionCopy.EMPTY
                        } else {
                            TransactionCopy.EMPTY_FILTERED
                        },
                        color = colors.ink2,
                        style = WzTheme.typography.body,
                        modifier = Modifier.padding(top = 24.dp).testTag("tx-empty"),
                    )
                }
            } else {
                TxTableHeader()
                val listScroll = rememberScrollState()
                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(listScroll)
                            .padding(end = 12.dp)
                            .testTag("tx-list"),
                    ) {
                        state.rows.forEach { row ->
                            TxRow(
                                row = row,
                                selected = row.id in state.selected,
                                onToggleSelect = { vm.toggleSelect(row.id) },
                                onEdit = { vm.openEdit(row.id) },
                                onDelete = { vm.requestDeleteRow(row.id) },
                            )
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listScroll),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }

        // 弹窗（就地叠加，共性约束 7.3）
        state.form?.let { form ->
            TransactionFormModal(state = form, vm = vm, onDismiss = vm::closeForm)
        }
        state.csv?.let { csv ->
            CsvImportModal(state = csv, vm = vm, onDismiss = vm::closeCsv)
        }
        if (state.deleteIds.isNotEmpty()) {
            DeleteConfirmModal(count = state.deleteIds.size, onCancel = vm::cancelDelete, onConfirm = vm::confirmDelete)
        }
        WzToastHost(toast = state.toast, onDismiss = vm::dismissToast)
    }
}

@Composable
private fun Toolbar(
    query: String,
    sideFilter: Side?,
    onQueryChange: (String) -> Unit,
    onSideFilterChange: (Side?) -> Unit,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImport: () -> Unit,
) {
    val colors = WzTheme.colors
    Column(modifier = Modifier.fillMaxWidth().testTag("tx-toolbar")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WzButton(text = TransactionCopy.BTN_ADD, onClick = onAdd, testTag = "tx-add")
            WzButton(
                text = TransactionCopy.BTN_EDIT,
                onClick = onEdit,
                variant = WzButtonVariant.Secondary,
                testTag = "tx-edit-batch",
            )
            WzButton(
                text = TransactionCopy.BTN_DELETE,
                onClick = onDelete,
                variant = WzButtonVariant.Danger,
                testTag = "tx-delete-batch",
            )
            WzButton(
                text = TransactionCopy.BTN_IMPORT,
                onClick = onImport,
                variant = WzButtonVariant.Secondary,
                testTag = "tx-import",
            )
            Box(modifier = Modifier.weight(1f))
            WzTextField(
                value = query,
                onValueChange = onQueryChange,
                label = "",
                placeholder = TransactionCopy.SEARCH_PLACEHOLDER,
                modifier = Modifier.width(220.dp),
                testTag = "tx-search",
            )
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = TransactionCopy.FILTER_TYPE_LABEL, color = colors.ink3, style = WzTheme.typography.caption)
            FilterButton(TransactionCopy.FILTER_ALL, sideFilter == null, "tx-filter-all") { onSideFilterChange(null) }
            FilterButton(TransactionCopy.FILTER_BUY, sideFilter == Side.BUY, "tx-filter-buy") {
                onSideFilterChange(if (sideFilter == Side.BUY) null else Side.BUY)
            }
            FilterButton(TransactionCopy.FILTER_SELL, sideFilter == Side.SELL, "tx-filter-sell") {
                onSideFilterChange(if (sideFilter == Side.SELL) null else Side.SELL)
            }
        }
    }
}

@Composable
private fun FilterButton(text: String, selected: Boolean, testTag: String, onClick: () -> Unit) {
    WzButton(
        text = (if (selected) "● " else "○ ") + text,
        onClick = onClick,
        variant = if (selected) WzButtonVariant.Primary else WzButtonVariant.Secondary,
        testTag = testTag,
    )
}

@Composable
private fun TxTableHeader() {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp)
            .testTag("tx-table-header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(32.dp))
        HeaderCell(TransactionCopy.COL_PAIR, 1.6f)
        HeaderCell(TransactionCopy.COL_SIDE, 0.7f)
        HeaderCell(TransactionCopy.COL_PRICE, 1.1f)
        HeaderCell(TransactionCopy.COL_QTY, 1.1f)
        HeaderCell(TransactionCopy.COL_FEE, 1.1f)
        HeaderCell(TransactionCopy.COL_TOTAL, 1.2f)
        HeaderCell(TransactionCopy.COL_EXCHANGE, 1.0f)
        HeaderCell(TransactionCopy.COL_TIME, 1.2f)
        HeaderCell(TransactionCopy.COL_REALIZED, 1.2f)
        HeaderCell(TransactionCopy.COL_ACTIONS, 1.4f)
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    val colors = WzTheme.colors
    Text(
        text = text,
        color = colors.ink2,
        style = WzTheme.typography.caption,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun TxRow(
    row: TransactionRow,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(if (selected) colors.surface2 else colors.surface)
            .testTag("tx-row-" + row.id),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 选择框（PRD §9.6：配合 修改/删除 批量操作）
        Text(
            text = if (selected) "☑" else "☐",
            color = colors.accent,
            style = WzTheme.typography.body,
            modifier = Modifier
                .width(32.dp)
                .clickable(onClick = onToggleSelect)
                .testTag("tx-check-" + row.id),
        )
        // 交易对（估算中标注）
        Column(modifier = Modifier.weight(1.6f)) {
            Text(
                text = row.pair,
                color = colors.ink,
                style = WzTheme.typography.body,
                modifier = Modifier.testTag("tx-pair-" + row.id),
            )
            if (row.estimated) {
                Text(
                    text = TransactionCopy.ESTIMATING,
                    color = colors.warn,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.testTag("tx-est-" + row.id),
                )
            }
        }
        Text(
            text = if (row.side == Side.BUY) TransactionCopy.FILTER_BUY else TransactionCopy.FILTER_SELL,
            color = if (row.side == Side.BUY) colors.gain else colors.loss,
            style = WzTheme.typography.body,
            modifier = Modifier.weight(0.7f),
        )
        Text(dec(row.price), color = colors.ink, style = WzTheme.typography.body, modifier = Modifier.weight(1.1f))
        Text(dec(row.quantity), color = colors.ink, style = WzTheme.typography.body, modifier = Modifier.weight(1.1f))
        Text(
            if (row.fee.signum() == 0) "--" else dec(row.fee) + (row.feeCurrency?.let { " " + it } ?: ""),
            color = colors.ink,
            style = WzTheme.typography.body,
            modifier = Modifier.weight(1.1f),
        )
        Text(dec(row.total), color = colors.ink, style = WzTheme.typography.body, modifier = Modifier.weight(1.2f))
        Text(
            row.exchange,
            color = colors.ink2,
            style = WzTheme.typography.body,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            timeText(row.time),
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.weight(1.2f),
        )
        // 已实现盈亏（卖出记录行显示；异常区段/非卖出行 "--"）
        val realized = row.realizedPnlFiat
        if (row.side == Side.SELL && realized != null) {
            val positive = realized.signum() >= 0
            Text(
                text = (if (positive) "+" else "") +
                    realized.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                color = if (positive) colors.gain else colors.loss,
                style = WzTheme.typography.body,
                modifier = Modifier.weight(1.2f),
            )
        } else {
            Text(text = "--", color = colors.ink3, style = WzTheme.typography.body, modifier = Modifier.weight(1.2f))
        }
        Row(
            modifier = Modifier.weight(1.4f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WzButton(
                text = TransactionCopy.BTN_EDIT_ROW,
                onClick = onEdit,
                variant = WzButtonVariant.Secondary,
                testTag = "tx-edit-" + row.id,
            )
            WzButton(
                text = TransactionCopy.BTN_DELETE_ROW,
                onClick = onDelete,
                variant = WzButtonVariant.Danger,
                testTag = "tx-delete-" + row.id,
            )
        }
    }
}

/** 删除确认框（PRD §9.6：将删除 N 条交易记录并重算持仓成本与盈亏，是否继续？）。 */
@Composable
private fun DeleteConfirmModal(count: Int, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val colors = WzTheme.colors
    WzModal(
        title = TransactionCopy.BTN_DELETE,
        onDismiss = onCancel,
        testTag = "tx-delete-confirm",
    ) {
        Text(
            text = TransactionCopy.DELETE_CONFIRM.replace("N", count.toString()),
            color = colors.ink2,
            style = WzTheme.typography.body,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            WzButton(
                text = TransactionCopy.BTN_CANCEL,
                onClick = onCancel,
                variant = WzButtonVariant.Secondary,
                testTag = "tx-delete-cancel",
            )
            WzButton(
                text = TransactionCopy.CONFIRM_DELETE,
                onClick = onConfirm,
                variant = WzButtonVariant.Danger,
                modifier = Modifier.padding(start = 8.dp),
                testTag = "tx-delete-confirm-btn",
            )
        }
    }
}

private fun dec(v: java.math.BigDecimal): String = v.stripTrailingZeros().toPlainString()

private fun timeText(at: java.time.Instant): String = WzFormat.dateTime(at)
