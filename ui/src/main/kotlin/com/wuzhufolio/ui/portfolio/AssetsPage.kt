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
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.portfolio.PortfolioRow
import com.wuzhufolio.domain.portfolio.PortfolioService
import com.wuzhufolio.domain.settings.GeneralSettingsService
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.i18n.WzFormat
import com.wuzhufolio.ui.i18n.portfolioStrings
import androidx.compose.ui.text.style.TextOverflow
import com.wuzhufolio.ui.components.AdaptiveTable
import com.wuzhufolio.ui.components.AdaptiveTableScope
import com.wuzhufolio.ui.components.TableColumn
import com.wuzhufolio.ui.components.TableWidths
import com.wuzhufolio.ui.components.tableCell
import com.wuzhufolio.ui.shell.pageEntryFocus
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 资产列表（M12 T12.1 · ia.md §2.5 · PRD §7.2 模块 2.1 · 原型 `pagePortfolio`）。
 *
 * 上部分总览卡（净值 / 24h 盈亏 / ROI）+ 持仓表：币种（名称/代码）、持有数量、平均成本价、当前价、
 * 总市值、总浮动盈亏（金额 + %）、累计已实现盈亏；支持按市值/名称/盈亏/数量/成本排序；
 * 「持仓异常」与「无行情」以文字徽标标注（颜色仅辅助）；单击行进入币种资产详情。
 */
@Composable
fun AssetsPage(
    portfolioService: PortfolioService,
    refreshService: MarketRefreshService,
    generalSettings: GeneralSettingsService,
    onOpenCoin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = remember { PortfolioViewModel(portfolioService, refreshService, generalSettings) }
    DisposableEffect(vm) {
        onDispose { vm.dispose() }
    }
    val state by vm.state.collectAsState()
    val colors = WzTheme.colors

    Box(modifier = modifier.fillMaxSize().testTag("page-ASSETS")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(text = portfolioStrings.assetsTitle, color = colors.ink, style = WzTheme.typography.pageTitle)
            Text(
                text = portfolioStrings.assetsSubtitle,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            val unreliable = state.rows.count { !it.costReliable }
            if (unreliable > 0) {
                Text(
                    text = portfolioStrings.costUnreliableNotice(unreliable),
                    color = colors.warn,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.padding(bottom = 10.dp).testTag("assets-cost-notice"),
                )
            }
            if (state.loading) {
                Text(
                    text = portfolioStrings.loading,
                    color = colors.ink3,
                    style = WzTheme.typography.body,
                    modifier = Modifier.padding(bottom = 8.dp).testTag("assets-loading"),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatCard(
                    label = portfolioStrings.cardNetValue,
                    value = WzFormat.money(state.snapshot?.metrics?.netValueFiat, state.fiat),
                    modifier = Modifier.weight(1f),
                    small = true,
                    testTag = "assets-card-net",
                )
                StatCard(
                    label = portfolioStrings.card24h,
                    value = signedMoneyOrDash(state.snapshot?.change24h?.pnlFiat, state.fiat),
                    modifier = Modifier.weight(1f),
                    delta = change24hSubtitle(state.snapshot?.change24h),
                    deltaColor = pnlColor(state.snapshot?.change24h?.pnlFiat),
                    small = true,
                    testTag = "assets-card-24h",
                )
                StatCard(
                    label = portfolioStrings.cardRoi,
                    value = WzFormat.signedPercent(state.snapshot?.metrics?.roiPercent),
                    modifier = Modifier.weight(1f),
                    deltaColor = pnlColor(state.snapshot?.metrics?.roiPercent),
                    small = true,
                    testTag = "assets-card-roi",
                )
            }

            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WzButton(
                    text = if (state.refreshing) portfolioStrings.refreshing else portfolioStrings.refreshQuotes,
                    onClick = vm::refreshQuotes,
                    variant = WzButtonVariant.Secondary,
                    enabled = !state.refreshing,
                    testTag = "assets-refresh",
                )
            }

            if (state.rows.isEmpty() && !state.loading) {
                Box(modifier = Modifier.padding(top = 16.dp)) {
                    EmptyHint(portfolioStrings.emptyPortfolio, testTag = "assets-empty")
                }
            } else {
                HoldingTable(
                    rows = state.rows,
                    sortKey = state.sortKey,
                    ascending = state.sortAscending,
                    onSort = vm::toggleSort,
                    onOpen = onOpenCoin,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        WzToastHost(toast = state.toast, onDismiss = vm::dismissToast)
    }
}

/** 资产表列（DEF-28）：最小宽度保底，窄窗整表横向滚动；宽窗按权重铺满。 */
private val ASSET_COLUMNS: List<TableColumn> = listOf(
    TableColumn(TableWidths.COIN, 1.6f),           // 币种 + 标签（持仓异常/估算中/成本不可靠）
    TableColumn(TableWidths.NUMBER, 1f),           // 持有数量
    TableColumn(TableWidths.NUMBER, 1f),           // 平均成本
    TableColumn(TableWidths.NUMBER, 1f),           // 当前价格
    TableColumn(TableWidths.AMOUNT, 1.1f),         // 市值
    TableColumn(TableWidths.AMOUNT, 1.3f),         // 浮动盈亏
    TableColumn(TableWidths.AMOUNT, 1.1f),         // 已实现盈亏
)  // 合计最小宽 ≈ 240 + 96×3 + 104×3 = 840dp：1280×800 铺满，1024×768 横向滚动（DEF-28）

@Composable
private fun HoldingTable(
    rows: List<PortfolioRow>,
    sortKey: AssetSortKey,
    ascending: Boolean,
    onSort: (AssetSortKey) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WzTheme.colors
    // DEF-28：窄窗（1280×800 / 1024×768）下整表横向滚动，列取最小宽度——避免「成本不可靠」标签竖排、
    // 8 位小数被挤成多行导致行高参差（原实现各列 weight 分配，窗口一窄就压到内容宽度以下）
    AdaptiveTable(
        columns = ASSET_COLUMNS,
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("assets-table"),
    ) { table ->
        Row(modifier = table.row(), verticalAlignment = Alignment.CenterVertically) {
            SortableHeader(
                label = portfolioStrings.colCoin,
                active = sortKey == AssetSortKey.COIN,
                ascending = ascending,
                onClick = { onSort(AssetSortKey.COIN) },
                // DEF-27：本页入口焦点 = 表格首列排序按钮（页面首个可聚焦控件）
                modifier = tableCell(table, 0).pageEntryFocus(),
                testTag = "sort-coin",
            )
            SortableHeader(
                label = portfolioStrings.colQuantity,
                active = sortKey == AssetSortKey.QUANTITY,
                ascending = ascending,
                onClick = { onSort(AssetSortKey.QUANTITY) },
                modifier = tableCell(table, 1),
                testTag = "sort-quantity",
            )
            SortableHeader(
                label = portfolioStrings.colAvgCost,
                active = sortKey == AssetSortKey.AVG_COST,
                ascending = ascending,
                onClick = { onSort(AssetSortKey.AVG_COST) },
                modifier = tableCell(table, 2),
                testTag = "sort-avg-cost",
            )
            SortableHeader(
                label = portfolioStrings.colPrice,
                active = sortKey == AssetSortKey.PRICE,
                ascending = ascending,
                onClick = { onSort(AssetSortKey.PRICE) },
                modifier = tableCell(table, 3),
                testTag = "sort-price",
            )
            SortableHeader(
                label = portfolioStrings.colMarketValue,
                active = sortKey == AssetSortKey.MARKET_VALUE,
                ascending = ascending,
                onClick = { onSort(AssetSortKey.MARKET_VALUE) },
                modifier = tableCell(table, 4),
                testTag = "sort-market-value",
            )
            SortableHeader(
                label = portfolioStrings.colFloatPnl,
                active = sortKey == AssetSortKey.FLOAT_PNL,
                ascending = ascending,
                onClick = { onSort(AssetSortKey.FLOAT_PNL) },
                modifier = tableCell(table, 5),
                testTag = "sort-float-pnl",
            )
            SortableHeader(
                label = portfolioStrings.colRealizedPnl,
                active = sortKey == AssetSortKey.REALIZED_PNL,
                ascending = ascending,
                onClick = { onSort(AssetSortKey.REALIZED_PNL) },
                modifier = tableCell(table, 6),
                testTag = "sort-realized-pnl",
            )
        }
        rows.forEach { row -> HoldingRowLine(row = row, onOpen = onOpen, table = table) }
    }
}

@Composable
private fun HoldingRowLine(row: PortfolioRow, onOpen: (String) -> Unit, table: AdaptiveTableScope) {
    val colors = WzTheme.colors
    Row(
        modifier = table.row()
            .wzTextClickable(label = row.symbol + " · " + row.name) { onOpen(row.cgId) }
            .padding(vertical = 8.dp)
            .testTag("holding-row-" + row.cgId),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 币种 + 标签（持仓异常/估算中/成本不可靠）：**不换行**——列宽已按「三枚标签 + 代码」保底，
        // 标签被压窄时会竖排（DEF-28 人工症状）
        Row(
            modifier = tableCell(table, 0),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.symbol,
                color = colors.ink,
                style = WzTheme.typography.bodyStrong,
                maxLines = 1,
                softWrap = false,
            )
            if (row.anomalous) {
                Badge(
                    text = portfolioStrings.anomalyBadge,
                    color = colors.loss,
                    modifier = Modifier.padding(start = 6.dp),
                    testTag = "anomaly-" + row.cgId,
                )
            }
            if (row.estimated) {
                Badge(
                    text = portfolioStrings.estimatedBadge,
                    color = colors.warn,
                    modifier = Modifier.padding(start = 6.dp),
                    testTag = "estimated-" + row.cgId,
                )
            }
            if (!row.costReliable) {
                Badge(
                    text = portfolioStrings.costUnreliableBadge,
                    color = colors.loss,
                    modifier = Modifier.padding(start = 6.dp),
                    testTag = "cost-unreliable-" + row.cgId,
                )
            }
        }
        Text(
            text = WzFormat.quantity(row.quantity),
            color = colors.ink,
            style = WzTheme.typography.body,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = tableCell(table, 1),
        )
        Text(
            text = WzFormat.price(row.avgCostFiat),
            color = colors.ink,
            style = WzTheme.typography.body,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = tableCell(table, 2),
        )
        Column(modifier = tableCell(table, 3)) {
            Text(
                text = WzFormat.price(row.priceFiat),
                color = if (row.priced) colors.ink else colors.ink3,
                style = WzTheme.typography.body,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("price-" + row.cgId),
            )
            if (!row.priced) {
                Text(
                    text = portfolioStrings.noMarketData,
                    color = colors.ink3,
                    style = WzTheme.typography.caption,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag("noprice-" + row.cgId),
                )
            }
        }
        Text(
            text = WzFormat.amount(row.marketValueFiat),
            color = colors.ink,
            style = WzTheme.typography.body,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = tableCell(table, 4),
        )
        Column(modifier = tableCell(table, 5)) {
            Text(
                text = WzFormat.signedAmount(row.floatPnlFiat),
                color = pnlColor(row.floatPnlFiat),
                style = WzTheme.typography.body,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("float-pnl-" + row.cgId),
            )
            Text(
                text = WzFormat.signedPercent(row.floatPnlPercent),
                color = pnlColor(row.floatPnlFiat),
                style = WzTheme.typography.caption,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            text = WzFormat.signedAmount(row.realizedPnlFiat),
            color = pnlColor(row.realizedPnlFiat),
            style = WzTheme.typography.body,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = tableCell(table, 6),
        )
    }
}

