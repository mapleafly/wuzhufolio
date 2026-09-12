package com.wuzhufolio.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.ui.zIndex
import com.wuzhufolio.ui.i18n.WzFormat

/**
 * 行情页（D21 · docs/dev/decisions/D21-行情浏览页-范围增量.md；M12 T12.1 收尾）：只读现价列表 +
 * 持久化自选 + 目录搜索添加；手动/自动刷新（页面可见期轮询）；无图表/交易（Out of Scope 维持）。
 *
 * M12 收尾对齐原型第六页（`pageQuotes`）：① 命令区置顶（立即刷新行情 + 数据源徽章 + 搜索）；
 * ② 自动轮询说明行；③ 搜索输入框**打开即聚焦**（interaction.md §2.7 输入聚焦 + AGENTS.md §7.3-②）。
 * 自选上限 50 的拒绝提示（interaction.md §2.7「已达 50 → 阻止添加并提示」）由页面捕获服务异常呈现。
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
    // 数据源徽章（原型「数据源：CoinGecko · 专属额度/无 Key 公共 API」）：实时读 Key 配置态
    var cgConfigured by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state.rows.size, state.refreshBusy) {
        cgConfigured = runCatching { settingsService.keyStatus().cgConfigured }.getOrNull()
    }
    val searchFocus = remember { FocusRequester() }
    // 候选浮层锚点（相对页面 Box 的像素坐标；由输入框容器 onGloballyPositioned 回填）
    var anchorLeft by remember { mutableStateOf(24.dp) }
    var anchorBottom by remember { mutableStateOf(0.dp) }
    var anchorWidth by remember { mutableStateOf(320.dp) }
    // 输入聚焦（interaction.md §2.7）：页面打开即聚焦搜索框，键盘可直接录入
    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }

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

            // 命令区（原型 toolbar：刷新 + 数据源徽章 + 搜索）
            Row(
                modifier = Modifier.fillMaxWidth().testTag("watch-toolbar"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WzButton(
                    text = if (state.refreshBusy) MarketCopy.REFRESHING else MarketCopy.REFRESH_BUTTON,
                    onClick = vm::refreshNow,
                    enabled = !state.refreshBusy,
                    testTag = "watch-refresh-now",
                )
                Text(
                    text = MarketCopy.watchSourceBadge(cgConfigured == true),
                    color = colors.ink3,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.padding(start = 12.dp).testTag("watch-source-badge"),
                )
            }

            // 自动轮询说明（原型 hint 行；interaction.md §2.7）
            Text(
                text = MarketCopy.WATCH_AUTO_HINT,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp).testTag("watch-auto-hint"),
            )

            // 2026-09-11 走查反馈修复轮：候选改为**就地浮层**（不再内联撑开列表空间），
            // 锚点 = 输入框的父容器坐标（同窗口叠加，非 Popup——AGENTS.md §7.3-①）
            WatchSearchBar(
                query = state.query,
                onQueryChange = vm::onQueryChange,
                focusRequester = searchFocus,
                onPositioned = { left, bottom, width ->
                    anchorLeft = left
                    anchorBottom = bottom
                    anchorWidth = width
                },
            )
            if (state.searchBusy || state.candidates.isNotEmpty() || state.query.isNotBlank()) {
                WatchCandidatePanel(
                    candidates = state.candidates,
                    searchBusy = state.searchBusy,
                    watchCgIds = state.rows.map { it.cgId }.toSet(),
                    onAdd = vm::addCoin,
                    modifier = Modifier
                        .offset { IntOffset(anchorLeft.roundToPx(), anchorBottom.roundToPx()) }
                        .width(anchorWidth)
                        .zIndex(1f),
                )
            }

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
        }
        WzToastHost(toast = state.toast, onDismiss = vm::dismissToast)
    }
}

@Composable
private fun WatchSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onPositioned: (left: Dp, bottom: Dp, width: Dp) -> Unit,
) {
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInParent()
                with(density) {
                    onPositioned(bounds.left.toDp(), bounds.bottom.toDp(), bounds.width.toDp())
                }
            }
            .testTag("watch-search"),
    ) {
        WzTextField(
            value = query,
            onValueChange = onQueryChange,
            label = MarketCopy.WATCH_SEARCH_LABEL,
            placeholder = MarketCopy.WATCH_SEARCH_PLACEHOLDER,
            modifier = Modifier.fillMaxWidth(),
            testTag = "watch-search-input",
            fieldFocusRequester = focusRequester,
        )
    }
}

/**
 * 候选浮层（interaction.md §2.7：候选点选加入）。
 *
 * 形态（2026-09-11 走查反馈修复轮）：**就地叠加在列表之上**（不挤占列表空间），
 * 最多 [MarketWatchViewModel.CANDIDATE_LIMIT] 条、超出滚动；
 * 无匹配时给「未找到匹配币种」提示（复用 [MarketCopy.WATCH_NO_RESULT]）。
 */
@Composable
private fun WatchCandidatePanel(
    candidates: List<CatalogCoin>,
    searchBusy: Boolean,
    watchCgIds: Set<String>,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WzTheme.colors
    Column(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .background(colors.surface, RoundedCornerShape(8.dp))
            .border(1.dp, colors.line, RoundedCornerShape(8.dp))
            .heightIn(max = CANDIDATE_PANEL_MAX_HEIGHT)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
            .testTag("watch-candidates"),
    ) {
        if (candidates.isEmpty()) {
            Text(
                text = if (searchBusy) MarketCopy.WATCH_SEARCHING else MarketCopy.WATCH_NO_RESULT,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).testTag("watch-no-result"),
            )
        }
        candidates.forEach { coin ->
            val added = coin.cgId in watchCgIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("watch-candidate-" + coin.cgId),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = coin.symbol + " · " + coin.name,
                        color = colors.ink,
                        style = WzTheme.typography.body,
                    )
                    Text(text = coin.cgId, color = colors.ink3, style = WzTheme.typography.caption)
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
}

/** 候选浮层最大高度（超出滚动，避免长列表占满窗口）。 */
private val CANDIDATE_PANEL_MAX_HEIGHT = 240.dp

@Composable
private fun WatchQuoteHeader(fiat: String) {
    val colors = WzTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).testTag("watch-quote-header")) {
        Column(modifier = Modifier.weight(2.2f)) {
            Text(MarketCopy.WATCH_COL_COIN, color = colors.ink2, style = WzTheme.typography.caption)
        }
        Column(modifier = Modifier.weight(1.4f)) {
            Text(MarketCopy.watchColPrice(fiat), color = colors.ink2, style = WzTheme.typography.caption)
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
                text = WzFormat.price(row.price),
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

private fun timeText(at: java.time.Instant?): String {
    if (at == null) return DASH
    val zoned = at.atZone(java.time.ZoneId.systemDefault())
    return "%02d:%02d".format(zoned.hour, zoned.minute)
}

private const val DASH = "--"
