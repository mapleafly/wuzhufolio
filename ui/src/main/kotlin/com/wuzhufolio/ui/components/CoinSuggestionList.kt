package com.wuzhufolio.ui.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.ui.i18n.ledgerStrings
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 币种候选列表（**DEF-51** 统一组件，2026-09-24 人工拍板 C1）。
 *
 * 人工反馈第 4/8/9/10 条指向同一个问题：**同一个「输入币种出候选」的交互，四个地方四种表现** ——
 * 交易表单只显示 6 条且**不可滚动**、资金/校准 12 条可滚、行情页 20 条可滚，且排序未用市值排名。
 * 本组件把这四处的**条数、可滚动性、行内容**收敛为一处：
 * - 上限 [COIN_SUGGESTION_LIMIT]（20 条，与行情页候选一致）；
 * - **一律可滚动** + 可见纵向滚动条（[MAX_HEIGHT]，超出即滚）；
 * - 行内容统一为 `symbol · name · cgId`（同名资产靠 cg_id 分辨——M8 修复轮 §8-1 口径）。
 *
 * 排序不在本层：由数据层 `PinnedCoinSearch` + `SqlCoinCatalog.search` 统一保证
 * （相关性 → ACTIVE → **市值排名** → 符号），故四处看到的顺序天然一致。
 *
 * 点选行为仍由调用方决定（选中即收起，`AGENTS.md §7.3` 焦点交接契约不变）。
 */
@Composable
fun CoinSuggestionList(
    candidates: List<CatalogCoin>,
    onPick: (CatalogCoin) -> Unit,
    /** 行 testTag 前缀（如 `tx-suggestion-`）；行标签 = 前缀 + coin.id。 */
    rowTagPrefix: String,
    /** 列表容器 testTag（如 `suggestion-list`）。 */
    listTag: String,
    modifier: Modifier = Modifier,
) {
    if (candidates.isEmpty()) return
    val colors = WzTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(colors.surface)
            .testTag(listTag),
    ) {
        val scroll = rememberScrollState()
        Box(modifier = Modifier.heightIn(max = MAX_HEIGHT)) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(scroll)) {
                candidates.take(COIN_SUGGESTION_LIMIT).forEach { coin ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(coin) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag(rowTagPrefix + coin.id),
                    ) {
                        Text(
                            text = ledgerStrings.coinLabel(coin.symbol, coin.name, coin.cgId),
                            color = colors.ink,
                            style = WzTheme.typography.body,
                        )
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scroll),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

/** 候选上限（四处统一；行情页 `MarketWatchViewModel.CANDIDATE_LIMIT` 与之同值）。 */
const val COIN_SUGGESTION_LIMIT: Int = 20

/** 候选列表最大高度（超出滚动；与资金页原口径 168dp 一致）。 */
private val MAX_HEIGHT = 168.dp
