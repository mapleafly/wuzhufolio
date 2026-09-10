package com.wuzhufolio.ui.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.CalibrationPreparation
import com.wuzhufolio.domain.ledger.CalibrationUseCase
import com.wuzhufolio.domain.ledger.TransactionLedgerService
import com.wuzhufolio.domain.ledger.TransactionRow
import com.wuzhufolio.domain.ledger.TxFilter
import com.wuzhufolio.domain.portfolio.CalibrationRecord
import com.wuzhufolio.domain.portfolio.CoinDetail
import com.wuzhufolio.domain.portfolio.PortfolioService
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzSelect
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.i18n.WzFormat
import com.wuzhufolio.ui.i18n.portfolioStrings
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 币种资产详情（M12 T12.1 · ia.md §2.6 · PRD §7.2 模块 2.2）。
 *
 * 入口：资产列表单击行（[AssetsPage] → `onOpenCoin`），页内「返回资产列表」回到列表。
 * **入口形态说明**：P1 原型把币种详情实现为弹窗（`openCoin`），而 ia.md §2.6 定义为页面且
 * task-breakdown T12.1 明确「币种详情（三重筛选/校准入口）」为聚合页之一——本模块按 **ia.md 页面**实现
 * （弹窗无法承载交易列表 + 校准历史的并排浏览），偏差登记模块记录 M12 §5。
 *
 * 覆盖内容：汇总（持有数量/当前持有价值/占比/平均成本/浮动盈亏/累计已实现）+ 该币交易记录
 * （交易所/类型/搜索三重筛选，卖出行显示该笔已实现盈亏）+ 校准入口（仅单一数据来源可见）+ 校准历史。
 */
@Composable
fun CoinDetailPage(
    cgId: String,
    portfolioService: PortfolioService,
    ledgerService: TransactionLedgerService,
    calibrationUseCase: CalibrationUseCase,
    catalog: CoinCatalog,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = remember(cgId) {
        CoinDetailViewModel(cgId, portfolioService, ledgerService, calibrationUseCase, catalog)
    }
    DisposableEffect(vm) {
        onDispose { vm.dispose() }
    }
    val state by vm.state.collectAsState()
    val colors = WzTheme.colors
    val detail = state.detail

    Box(modifier = modifier.fillMaxSize().testTag("page-COIN-DETAIL")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                text = portfolioStrings.coinDetailBack,
                color = colors.ink2,
                style = WzTheme.typography.caption,
                modifier = Modifier
                    .wzTextClickable(label = portfolioStrings.coinDetailBack, onClick = onBack)
                    .padding(bottom = 8.dp)
                    .testTag("coin-detail-back"),
            )
            Text(
                text = if (detail == null) portfolioStrings.coinDetailTitle else detail.symbol + " · " + detail.name,
                color = colors.ink,
                style = WzTheme.typography.pageTitle,
            )

            if (state.loading) {
                Text(
                    text = portfolioStrings.loading,
                    color = colors.ink3,
                    style = WzTheme.typography.body,
                    modifier = Modifier.padding(top = 12.dp).testTag("coin-detail-loading"),
                )
            }
            CoinSummary(detail = detail, state = state, modifier = Modifier.padding(top = 16.dp))

            // 校准入口（仅单一数据来源可见；PRD 故事 4.1-5）
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (detail?.calibratable == true) {
                    WzButton(
                        text = portfolioStrings.calibrateAction,
                        onClick = vm::openCalibration,
                        variant = WzButtonVariant.Primary,
                        testTag = "calibrate-open",
                    )
                } else if (detail != null) {
                    Text(
                        text = calibrateHint(detail),
                        color = colors.ink3,
                        style = WzTheme.typography.caption,
                        modifier = Modifier.testTag("calibrate-hint"),
                    )
                }
            }

            // 交易记录（三重筛选）
            PanelTitle(portfolioStrings.coinTxTitle, modifier = Modifier.padding(top = 20.dp))
            TransactionFilters(
                exchanges = state.exchanges,
                exchangeFilter = state.exchangeFilter,
                sideFilter = state.sideFilter,
                query = state.query,
                onExchange = vm::setExchangeFilter,
                onSide = vm::setSideFilter,
                onQuery = vm::setQuery,
            )
            CoinTransactionTable(rows = state.transactions)

            // 校准历史
            PanelTitle(portfolioStrings.calibrationsTitle, modifier = Modifier.padding(top = 20.dp))
            CalibrationHistory(records = detail?.calibrations.orEmpty())
        }

        if (state.calibration != null) {
            CalibrationModal(state = state, vm = vm)
        }
        WzToastHost(toast = state.toast, onDismiss = vm::dismissToast)
    }
}

private fun calibrateHint(detail: CoinDetail): String =
    if (detail.row?.sources.isNullOrEmpty()) {
        portfolioStrings.calibrateHintNoRecords
    } else {
        portfolioStrings.calibrateHintMulti
    }

@Composable
private fun CoinSummary(
    detail: CoinDetail?,
    state: CoinDetailUiState,
    modifier: Modifier = Modifier,
) {
    val row = detail?.row
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatCard(
            label = portfolioStrings.colQuantity,
            value = WzFormat.quantity(row?.quantity),
            modifier = Modifier.weight(1f),
            small = true,
            testTag = "coin-qty",
        )
        StatCard(
            label = portfolioStrings.coinHoldingValue,
            value = WzFormat.money(row?.marketValueFiat, state.fiat),
            modifier = Modifier.weight(1f),
            delta = if (row?.sharePercent == null) null else {
                portfolioStrings.coinShare(WzFormat.percent(row.sharePercent))
            },
            small = true,
            testTag = "coin-value",
        )
        StatCard(
            label = portfolioStrings.colFloatPnl,
            value = WzFormat.signedAmount(row?.floatPnlFiat),
            modifier = Modifier.weight(1f),
            delta = portfolioStrings.coinAvgCost + " " + WzFormat.price(row?.avgCostFiat),
            deltaColor = pnlColor(row?.floatPnlFiat),
            small = true,
            testTag = "coin-float-pnl",
        )
        StatCard(
            label = portfolioStrings.cardRealizedPnl,
            value = WzFormat.signedAmount(row?.realizedPnlFiat),
            modifier = Modifier.weight(1f),
            deltaColor = pnlColor(row?.realizedPnlFiat),
            small = true,
            testTag = "coin-realized",
        )
    }
}

@Composable
private fun TransactionFilters(
    exchanges: List<String>,
    exchangeFilter: String?,
    sideFilter: Side?,
    query: String,
    onExchange: (String?) -> Unit,
    onSide: (Side?) -> Unit,
    onQuery: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("coin-tx-filters"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WzSelect(
            options = listOf<String?>(null) + exchanges,
            selected = exchangeFilter,
            labelOf = { it ?: portfolioStrings.filterExchangeAll },
            onSelect = onExchange,
            modifier = Modifier.width(180.dp),
            testTag = "coin-filter-exchange",
        )
        WzSelect(
            options = listOf<Side?>(null, Side.BUY, Side.SELL),
            selected = sideFilter,
            labelOf = {
                when (it) {
                    Side.BUY -> portfolioStrings.filterBuy
                    Side.SELL -> portfolioStrings.filterSell
                    else -> portfolioStrings.filterTypeAll
                }
            },
            onSelect = onSide,
            modifier = Modifier.width(150.dp),
            testTag = "coin-filter-side",
        )
        WzTextField(
            value = query,
            onValueChange = onQuery,
            label = portfolioStrings.searchTxPlaceholder,
            modifier = Modifier.weight(1f),
            testTag = "coin-tx-search",
        )
    }
}

@Composable
private fun CoinTransactionTable(rows: List<TransactionRow>) {
    val colors = WzTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("coin-tx-table"),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            PlainHeader(portfolioStrings.colPair, Modifier.weight(1.3f))
            PlainHeader(portfolioStrings.colSide, Modifier.weight(0.8f))
            PlainHeader(portfolioStrings.colPrice, Modifier.weight(1.1f))
            PlainHeader(portfolioStrings.colQuantity, Modifier.weight(1f))
            PlainHeader(portfolioStrings.colFee, Modifier.weight(1.1f))
            PlainHeader(portfolioStrings.colExchange, Modifier.weight(1f))
            PlainHeader(portfolioStrings.colTime, Modifier.weight(1.2f))
            PlainHeader(portfolioStrings.colRealizedPnl, Modifier.weight(1.1f))
        }
        if (rows.isEmpty()) {
            Text(
                text = portfolioStrings.txEmpty,
                color = colors.ink3,
                style = WzTheme.typography.body,
                modifier = Modifier.padding(vertical = 16.dp).testTag("coin-tx-empty"),
            )
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("coin-tx-" + row.id),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.pair, color = colors.ink, style = WzTheme.typography.body, modifier = Modifier.weight(1.3f))
                Text(
                    text = if (row.side == Side.BUY) portfolioStrings.buyLabel else portfolioStrings.sellLabel,
                    color = if (row.side == Side.BUY) colors.gain else colors.loss,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.weight(0.8f),
                )
                Text(
                    text = WzFormat.price(row.price),
                    color = colors.ink,
                    style = WzTheme.typography.body,
                    modifier = Modifier.weight(1.1f),
                )
                Text(
                    text = WzFormat.quantity(row.quantity),
                    color = colors.ink,
                    style = WzTheme.typography.body,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = WzFormat.quantity(row.fee) + " " + (row.feeCurrency ?: ""),
                    color = colors.ink2,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.weight(1.1f),
                )
                Text(
                    text = row.exchange,
                    color = colors.ink2,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = WzFormat.dateTime(row.time),
                    color = colors.ink3,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    text = WzFormat.signedAmount(row.realizedPnlFiat),
                    color = pnlColor(row.realizedPnlFiat),
                    style = WzTheme.typography.body,
                    modifier = Modifier.weight(1.1f).testTag("coin-tx-realized-" + row.id),
                )
            }
        }
    }
}

@Composable
private fun CalibrationHistory(records: List<CalibrationRecord>) {
    val colors = WzTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("coin-calibrations"),
    ) {
        if (records.isEmpty()) {
            Text(
                text = portfolioStrings.calibrationsEmpty,
                color = colors.ink3,
                style = WzTheme.typography.body,
                modifier = Modifier.testTag("coin-calibrations-empty"),
            )
        }
        records.forEach { record ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("calibration-" + record.id),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = WzFormat.dateTime(record.at),
                    color = colors.ink2,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    text = portfolioStrings.calExchangeQty + " " + WzFormat.quantity(record.exchangeQuantity),
                    color = colors.ink,
                    style = WzTheme.typography.body,
                    modifier = Modifier.weight(1.4f),
                )
                Text(
                    text = portfolioStrings.calDelta + " " + WzFormat.signedQuantity(record.delta),
                    color = pnlColor(record.delta),
                    style = WzTheme.typography.body,
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    text = WzFormat.amount(record.deltaFiat),
                    color = colors.ink,
                    style = WzTheme.typography.body,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = record.exchange ?: WzFormat.DASH,
                    color = colors.ink3,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 校准弹窗（币种详情入口；预览 → 执行；阻断原因以文案呈现，PRD 故事 4.1-5）。 */
@Composable
private fun CalibrationModal(state: CoinDetailUiState, vm: CoinDetailViewModel) {
    val colors = WzTheme.colors
    val calibration = state.calibration ?: return
    WzModal(
        title = portfolioStrings.calTitle,
        onDismiss = vm::closeCalibration,
        width = 560.dp,
        testTag = "coin-calibration-modal",
    ) {
        Text(
            text = portfolioStrings.calPreviewHint,
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        when (val phase = calibration.phase) {
            CalibrationPhase.Preparing -> Text(
                text = portfolioStrings.loading,
                color = colors.ink2,
                style = WzTheme.typography.body,
            )
            is CalibrationPhase.Blocked -> Text(
                text = phase.message,
                color = colors.loss,
                style = WzTheme.typography.body,
                modifier = Modifier.testTag("calibration-blocked"),
            )
            is CalibrationPhase.Ready -> {
                CalibrationRow(portfolioStrings.calExchange, phase.prep.exchangeName + " · " + phase.prep.apiKeyName)
                CalibrationRow(portfolioStrings.calLocal, WzFormat.quantity(phase.prep.localQuantity))
                CalibrationRow(portfolioStrings.calExchangeQty, WzFormat.quantity(phase.prep.exchangeQuantity))
                CalibrationRow(
                    portfolioStrings.calDelta,
                    WzFormat.signedQuantity(phase.prep.delta) + directionSuffix(phase.prep),
                )
                CalibrationRow(portfolioStrings.calDeltaFiat, WzFormat.amount(phase.prep.deltaFiat))
                CalibrationRow(portfolioStrings.calMarketPrice, WzFormat.price(phase.prep.marketPrice))
            }
            CalibrationPhase.Executing -> Text(
                text = portfolioStrings.loading,
                color = colors.ink2,
                style = WzTheme.typography.body,
            )
            is CalibrationPhase.Done -> Text(
                text = if (phase.recorded) portfolioStrings.calDone else portfolioStrings.calNoDelta,
                color = colors.ink,
                style = WzTheme.typography.body,
                modifier = Modifier.testTag("calibration-done"),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            WzButton(
                text = portfolioStrings.calExecute,
                onClick = vm::executeCalibration,
                variant = WzButtonVariant.Primary,
                enabled = calibration.phase is CalibrationPhase.Ready,
                testTag = "calibration-execute",
            )
        }
    }
}

private fun directionSuffix(prep: CalibrationPreparation): String = when (prep.direction) {
    FlowKind.DEPOSIT -> "（" + portfolioStrings.calDepositSemantics + "）"
    FlowKind.WITHDRAWAL -> "（" + portfolioStrings.calWithdrawalSemantics + "）"
    null -> ""
}

@Composable
private fun CalibrationRow(label: String, value: String) {
    val colors = WzTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(text = label, color = colors.ink2, style = WzTheme.typography.caption, modifier = Modifier.weight(1f))
        Text(text = value, color = colors.ink, style = WzTheme.typography.body)
    }
}
