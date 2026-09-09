package com.wuzhufolio.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.ledger.CalibrationUseCase
import com.wuzhufolio.domain.ledger.FundDateRange
import com.wuzhufolio.domain.ledger.FundEntryRow
import com.wuzhufolio.domain.ledger.FundEntryType
import com.wuzhufolio.domain.ledger.FundService
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.theme.WzTheme
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 资金管理页（M8 · T8.3 · PRD §9.8 + 原型资金页逐字口径）：总览卡（可用现金余额/投入本金（净））+
 * 命令区（记录增资/记录撤资/编辑/删除/校准持仓 + 搜索 + 类型/日期筛选）+ 混合列表（增资/撤资/校准，
 * 校准行不可编辑仅可删除）。表单与校准弹窗为同窗口就地叠加（共性约束 7.3）。
 */
@Composable
fun FundsPage(
    service: FundService,
    calibration: CalibrationUseCase,
    modifier: Modifier = Modifier,
) {
    val vm = remember { FundsViewModel(service, calibration) }
    DisposableEffect(vm) {
        onDispose { vm.dispose() }
    }
    val state by vm.state.collectAsState()
    val colors = WzTheme.colors

    Box(modifier = modifier.fillMaxSize().testTag("funds-page")) {
        // 标题/总览卡/命令区/表头固定；仅数据行区域滚动
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(text = FundsCopy.PAGE_TITLE, color = colors.ink, style = WzTheme.typography.pageTitle)
            Text(
                text = FundsCopy.PAGE_SUB,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // 总览卡（PRD §9.8 页面上部）
            OverviewCards(state)

            // 命令区（PRD §9.8 操作区；校准入口见模块记录 M8 §5 偏差登记）
            Toolbar(state, vm)

            // 混合列表（仅本区域滚动）
            if (state.rows.isEmpty()) {
                Box(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.query.isBlank() && state.typeFilter == null &&
                            state.dateRange == FundDateRange.ALL
                        ) {
                            FundsCopy.EMPTY
                        } else {
                            FundsCopy.EMPTY_FILTERED
                        },
                        color = colors.ink2,
                        style = WzTheme.typography.body,
                        modifier = Modifier.padding(top = 24.dp).testTag("fund-empty"),
                    )
                }
            } else {
                FundTableHeader()
                val listScroll = rememberScrollState()
                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(listScroll)
                            .padding(end = 12.dp)
                            .testTag("fund-list"),
                    ) {
                        state.rows.forEach { row ->
                            FundRow(
                                row = row,
                                selected = row.uuid in state.selected,
                                onToggleSelect = { vm.toggleSelect(row.uuid) },
                                onEdit = { vm.openEdit(row.uuid) },
                                onDelete = { vm.requestDeleteRow(row.uuid) },
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
            FundFormModal(state = form, vm = vm, onDismiss = vm::closeForm)
        }
        state.calibration?.let { cal ->
            CalibrationModal(state = cal, vm = vm, onDismiss = vm::closeCalibration)
        }
        if (state.deleteUuids.isNotEmpty()) {
            FundDeleteConfirmModal(
                count = state.deleteUuids.size,
                onCancel = vm::cancelDelete,
                onConfirm = vm::confirmDelete,
            )
        }
        WzToastHost(toast = state.toast, onDismiss = vm::dismissToast)
    }
}

@Composable
private fun OverviewCards(state: FundsUiState) {
    val colors = WzTheme.colors
    val overview = state.overview
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OverviewCard(
            title = FundsCopy.CARD_CASH,
            value = overview?.let { fiatMoney(it.availableCashFiat) } ?: "--",
            hint = FundsCopy.CARD_CASH_HINT,
            testTag = "fund-card-cash",
            modifier = Modifier.weight(1f),
        )
        OverviewCard(
            title = FundsCopy.CARD_PRINCIPAL,
            value = overview?.let { fiatMoney(it.investedNetFiat) } ?: "--",
            hint = overview?.let {
                FundsCopy.CARD_PRINCIPAL_HINT_PREFIX + fiatMoney(it.cumulativeDepositsFiat) +
                    FundsCopy.CARD_PRINCIPAL_HINT_MID + fiatMoney(it.cumulativeWithdrawalsFiat)
            } ?: "",
            testTag = "fund-card-principal",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OverviewCard(title: String, value: String, hint: String, testTag: String, modifier: Modifier = Modifier) {
    val colors = WzTheme.colors
    Column(
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
    ) {
        Text(text = title, color = colors.ink2, style = WzTheme.typography.caption)
        Text(text = value, color = colors.ink, style = WzTheme.typography.pageTitle)
        if (hint.isNotEmpty()) {
            Text(text = hint, color = colors.ink3, style = WzTheme.typography.caption)
        }
    }
}

@Composable
private fun Toolbar(state: FundsUiState, vm: FundsViewModel) {
    val colors = WzTheme.colors
    Column(modifier = Modifier.fillMaxWidth().testTag("fund-toolbar")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WzButton(
                text = FundsCopy.BTN_DEPOSIT,
                onClick = { vm.openAdd(FlowKind.DEPOSIT) },
                testTag = "fund-add-deposit",
            )
            WzButton(
                text = FundsCopy.BTN_WITHDRAW,
                onClick = { vm.openAdd(FlowKind.WITHDRAWAL) },
                variant = WzButtonVariant.Secondary,
                testTag = "fund-add-withdraw",
            )
            WzButton(
                text = FundsCopy.BTN_EDIT,
                onClick = vm::requestEditSelected,
                variant = WzButtonVariant.Secondary,
                testTag = "fund-edit-batch",
            )
            WzButton(
                text = FundsCopy.BTN_DELETE,
                onClick = vm::requestDeleteSelected,
                variant = WzButtonVariant.Danger,
                testTag = "fund-delete-batch",
            )
            WzButton(
                text = FundsCopy.BTN_CALIBRATE,
                onClick = vm::openCalibration,
                variant = WzButtonVariant.Secondary,
                testTag = "fund-calibrate",
            )
            Box(modifier = Modifier.weight(1f))
            WzTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                label = "",
                placeholder = FundsCopy.SEARCH_PLACEHOLDER,
                modifier = Modifier.width(220.dp),
                testTag = "fund-search",
            )
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = FundsCopy.FILTER_TYPE_LABEL, color = colors.ink3, style = WzTheme.typography.caption)
            TypeButton(FundsCopy.FILTER_ALL, state.typeFilter == null, "fund-type-all") { vm.onTypeFilterChange(null) }
            TypeButton(FundsCopy.FILTER_DEPOSIT, state.typeFilter == FundEntryType.DEPOSIT, "fund-type-deposit") {
                vm.onTypeFilterChange(FundEntryType.DEPOSIT)
            }
            TypeButton(FundsCopy.FILTER_WITHDRAW, state.typeFilter == FundEntryType.WITHDRAWAL, "fund-type-withdraw") {
                vm.onTypeFilterChange(FundEntryType.WITHDRAWAL)
            }
            TypeButton(FundsCopy.FILTER_RECON, state.typeFilter == FundEntryType.RECONCILIATION, "fund-type-recon") {
                vm.onTypeFilterChange(FundEntryType.RECONCILIATION)
            }
            Box(modifier = Modifier.width(16.dp))
            Text(text = FundsCopy.FILTER_DATE_LABEL, color = colors.ink3, style = WzTheme.typography.caption)
            TypeButton(FundsCopy.DATE_ALL, state.dateRange == FundDateRange.ALL, "fund-date-all") {
                vm.onDateRangeChange(FundDateRange.ALL)
            }
            TypeButton(FundsCopy.DATE_30, state.dateRange == FundDateRange.LAST_30, "fund-date-30") {
                vm.onDateRangeChange(FundDateRange.LAST_30)
            }
            TypeButton(FundsCopy.DATE_90, state.dateRange == FundDateRange.LAST_90, "fund-date-90") {
                vm.onDateRangeChange(FundDateRange.LAST_90)
            }
            TypeButton(FundsCopy.DATE_OLDER, state.dateRange == FundDateRange.OLDER, "fund-date-older") {
                vm.onDateRangeChange(FundDateRange.OLDER)
            }
        }
    }
}

@Composable
private fun TypeButton(text: String, selected: Boolean, testTag: String, onClick: () -> Unit) {
    WzButton(
        text = (if (selected) "● " else "○ ") + text,
        onClick = onClick,
        variant = if (selected) WzButtonVariant.Primary else WzButtonVariant.Secondary,
        testTag = testTag,
    )
}

@Composable
private fun FundTableHeader() {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp)
            .testTag("fund-table-header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(32.dp))
        HeaderCell(FundsCopy.COL_TYPE, 0.8f)
        HeaderCell(FundsCopy.COL_COIN, 0.8f)
        HeaderCell(FundsCopy.COL_QTY, 0.9f)
        HeaderCell(FundsCopy.COL_FIAT, 1.0f)
        HeaderCell(FundsCopy.COL_TIME, 1.0f)
        HeaderCell(FundsCopy.COL_SOURCE, 0.9f)
        HeaderCell(FundsCopy.COL_NOTES, 0.9f)
        HeaderCell(FundsCopy.COL_ACTIONS, 1.4f)
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
private fun FundRow(
    row: FundEntryRow,
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
            .testTag("fund-row-" + row.uuid.takeLast(6)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 选择框（配合 编辑/删除 批量操作）
        Text(
            text = if (selected) "☑" else "☐",
            color = colors.accent,
            style = WzTheme.typography.body,
            modifier = Modifier
                .width(32.dp)
                .clickable(onClick = onToggleSelect)
                .testTag("fund-check-" + row.uuid.takeLast(6)),
        )
        // 类型（增资/撤资/校准 pill）
        Text(
            text = typeLabel(row),
            color = when (row.entryType) {
                FundEntryType.DEPOSIT -> colors.gain
                FundEntryType.WITHDRAWAL -> colors.loss
                FundEntryType.RECONCILIATION -> colors.accent
            },
            style = WzTheme.typography.body,
            modifier = Modifier.weight(0.8f).testTag("fund-type-" + row.uuid.takeLast(6)),
        )
        // 币种（估算中标注）
        Column(modifier = Modifier.weight(0.8f)) {
            Text(text = row.coinSymbol, color = colors.ink, style = WzTheme.typography.bodyStrong)
            if (row.estimated) {
                Text(
                    text = "估算中",
                    color = colors.warn,
                    style = WzTheme.typography.caption,
                )
            }
        }
        Text(
            text = fundQty(row.quantity),
            color = colors.ink,
            style = WzTheme.typography.body,
            modifier = Modifier.weight(0.9f),
        )
        Text(
            text = fiatMoney(row.baseAmount),
            color = colors.ink,
            style = WzTheme.typography.body,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            text = fundsTimeText(row.time),
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            text = row.sourceDest ?: "--",
            color = colors.ink2,
            style = WzTheme.typography.body,
            modifier = Modifier.weight(0.9f),
        )
        Text(
            text = row.notes ?: "",
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.weight(0.9f),
        )
        Row(
            modifier = Modifier.weight(1.4f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (row.entryType != FundEntryType.RECONCILIATION) {
                WzButton(
                    text = FundsCopy.BTN_EDIT,
                    onClick = onEdit,
                    variant = WzButtonVariant.Secondary,
                    testTag = "fund-edit-" + row.uuid.takeLast(6),
                )
            }
            // 校准记录不可编辑仅可删除（PRD §10-8 注）：不渲染编辑入口
            WzButton(
                text = FundsCopy.BTN_DELETE,
                onClick = onDelete,
                variant = WzButtonVariant.Danger,
                testTag = "fund-delete-" + row.uuid.takeLast(6),
            )
        }
    }
}

private fun typeLabel(row: FundEntryRow): String = when (row.entryType) {
    FundEntryType.DEPOSIT -> FundsCopy.FILTER_DEPOSIT
    FundEntryType.WITHDRAWAL -> FundsCopy.FILTER_WITHDRAW
    FundEntryType.RECONCILIATION -> FundsCopy.TYPE_RECON
}
