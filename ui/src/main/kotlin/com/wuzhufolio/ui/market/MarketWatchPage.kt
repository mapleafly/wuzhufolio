package com.wuzhufolio.ui.market

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.market.MarketQuotesService
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.MarketSettingsService
import com.wuzhufolio.domain.market.MarketWatchService
import com.wuzhufolio.domain.market.WatchQuoteRow
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.theme.WzTheme
import java.math.BigDecimal

/**
 * 行情页（D21 · docs/dev/decisions/D21-行情浏览页-范围增量.md）：只读现价列表 + 持久化自选 +
 * 目录搜索添加；手动/自动刷新（页面可见期轮询）；无图表/交易（Out of Scope 维持）。
 */
@Composable
fun MarketWatchPage(
    watchService: MarketWatchService,
    quotesService: MarketQuotesService,
    refreshService: MarketRefreshService,
    settingsService: MarketSettingsService,
    modifier: Modifier = Modifier,
) {
    val vm = remember {
        MarketWatchViewModel(watchService, quotesService, refreshService, settingsService)
    }
    DisposableEffect(vm) {
        onDispose { vm.dispose() }
    }
    val state by vm.state.collectAsState()
    val colors = WzTheme.colors

    Box(modifier = modifier.fillMaxSize().testTag("market-watch")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(text = MarketCopy.WATCH_TITLE, color = colors.ink, style = WzTheme.typography.pageTitle)
            Text(
                text = if (state.defaultSeed) MarketCopy.WATCH_SUB_DEFAULT else MarketCopy.WATCH_SUB_CUSTOM,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // 搜索添加行
            WatchSearchBar(
                query = state.query,
                candidates = state.candidates,
                searchBusy = state.searchBusy,
                watchCgIds = state.rows.map { it.cgId }.toSet(),
                onQueryChange = vm::onQueryChange,
                onAdd = vm::addCoin,
            )

            // 报价表
            if (state.rows.isEmpty()) {
                Text(
                    text = MarketCopy.WATCH_EMPTY,
                    color = colors.ink2,
                    style = WzTheme.typography.body,
                    modifier = Modifier.padding(top = 16.dp).testTag("watch-empty"),
                )
            } else {
                WatchQuoteHeader(fiat = state.fiat)
                state.rows.forEach { row ->
                    WatchQuoteRowLine(row = row, onRemove = { vm.removeCoin(row.cgId) })
                }
            }

            // 刷新动作（手动）
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WzButton(
                    text = if (state.refreshBusy) MarketCopy.REFRESHING else MarketCopy.REFRESH_BUTTON,
                    onClick = vm::refreshNow,
                    enabled = !state.refreshBusy,
                    testTag = "watch-refresh-now",
                )
            }
        }
        WzToastHost(toast = state.toast, onDismiss = vm::dismissToast)
    }
}

@Composable
private fun WatchSearchBar(
    query: String,
    candidates: List<CatalogCoin>,
    searchBusy: Boolean,
    watchCgIds: Set<String>,
    onQueryChange: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    val colors = WzTheme.colors
    Column(modifier = Modifier.fillMaxWidth().testTag("watch-search")) {
        WzTextField(
            value = query,
            onValueChange = onQueryChange,
            label = MarketCopy.WATCH_SEARCH_LABEL,
            placeholder = MarketCopy.WATCH_SEARCH_PLACEHOLDER,
            modifier = Modifier.fillMaxWidth(),
            testTag = "watch-search-input",
        )
        if (candidates.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .testTag("watch-candidates"),
            ) {
                candidates.forEach { coin ->
                    val added = coin.cgId in watchCgIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = coin.symbol + " · " + coin.name,
                                color = colors.ink,
                                style = WzTheme.typography.body,
                            )
                        }
                        if (added) {
                            Text(
                                text = MarketCopy.WATCH_ADDED_HINT,
                                color = colors.ink3,
                                style = WzTheme.typography.caption,
                            )
                        } else {
                            WzButton(
                                text = MarketCopy.WATCH_ADD_ACTION,
                                onClick = { onAdd(coin.cgId) },
                                variant = WzButtonVariant.Secondary,
                                testTag = "watch-add-" + coin.cgId,
                            )
                        }
                    }
                }
            }
        } else if (query.isNotBlank() && !searchBusy) {
            Text(
                text = MarketCopy.WATCH_NO_RESULT,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun WatchQuoteHeader(fiat: String) {
    val colors = WzTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).testTag("watch-quote-header")) {
        Column(modifier = Modifier.weight(2.2f)) {
            Text(MarketCopy.WATCH_COL_COIN, color = colors.ink2, style = WzTheme.typography.caption)
        }
        Column(modifier = Modifier.weight(1.4f)) {
            Text("现价（$fiat）", color = colors.ink2, style = WzTheme.typography.caption)
        }
        Column(modifier = Modifier.weight(1.2f)) {
            Text(MarketCopy.WATCH_COL_SOURCE, color = colors.ink2, style = WzTheme.typography.caption)
        }
        Column(modifier = Modifier.weight(1.0f)) {
            Text(MarketCopy.WATCH_COL_UPDATED, color = colors.ink2, style = WzTheme.typography.caption)
        }
        Column(modifier = Modifier.width(64.dp)) {
        }
    }
}

@Composable
private fun WatchQuoteRowLine(row: WatchQuoteRow, onRemove: () -> Unit) {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("watch-row-" + row.cgId),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(2.2f)) {
            Text(text = row.symbol, color = colors.ink, style = WzTheme.typography.body)
            Text(text = row.name, color = colors.ink3, style = WzTheme.typography.caption)
        }
        Column(modifier = Modifier.weight(1.4f)) {
            Text(
                text = formatPrice(row.price),
                color = if (row.priced) colors.ink else colors.ink3,
                style = WzTheme.typography.body,
                modifier = Modifier.testTag("watch-price-" + row.cgId),
            )
        }
        Column(modifier = Modifier.weight(1.2f)) {
            Text(
                text = MarketCopy.sourceText(row.source),
                color = if (row.source != null) colors.ink else colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.testTag("watch-source-" + row.cgId),
            )
        }
        Column(modifier = Modifier.weight(1.0f)) {
            Text(text = timeText(row.at), color = colors.ink3, style = WzTheme.typography.caption)
        }
        WzButton(
            text = MarketCopy.WATCH_REMOVE_ACTION,
            onClick = onRemove,
            variant = WzButtonVariant.Secondary,
            modifier = Modifier.width(64.dp),
            testTag = "watch-remove-" + row.cgId,
        )
    }
}

private fun formatPrice(price: BigDecimal?): String =
    price?.stripTrailingZeros()?.toPlainString() ?: DASH

private fun timeText(at: java.time.Instant?): String {
    if (at == null) return DASH
    val zoned = at.atZone(java.time.ZoneId.systemDefault())
    return "%02d:%02d".format(zoned.hour, zoned.minute)
}

private const val DASH = "--"
