package com.wuzhufolio.data.catalog

import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinCatalog

/**
 * 币种候选检索的统一口径（**DEF-51**，2026-09-24 人工拍板 C1）。
 *
 * 统一点（资金页 / 交易页 / 校准弹窗 / 行情搜索**行为一致**，人工反馈第 4/8/9/10 条）：
 * 1. **目录排序 = 相关性 → ACTIVE → 市值排名**（排名由 [CoinCatalog] 实现消费 `MarketRankProvider`；
 *    未入前 1000 名排后）——真正的 USDT/BTC/ETH 不再被同名桥接币按字母序挤出候选；
 * 2. **默认币置顶**：基础法币的孪生币（USD→`tether`(USDT)、其他法币→`usd-coin`(USDC)，PRD §9.8）
 *    在命中查询时排第一 —— 此前只有资金页这么做（M8 修复轮 §8-2），交易页没有；
 * 3. **检索放大再裁剪**：先取 [SEARCH_WIDEN_LIMIT] 条再置顶裁剪到调用方 `limit`，
 *    避免「置顶币把正常候选挤掉」。
 *
 * 单测：`PinnedCoinSearchTest`（空查询/无结果/置顶命中/置顶不命中/裁剪）。
 */
class PinnedCoinSearch(
    private val catalog: CoinCatalog,
    /** 基础法币读取（settings 全局行 `base.fiat`；由调用方注入，缺省 USD）。 */
    private val baseFiat: suspend () -> String,
) {

    /** 目录检索（含默认币置顶）。 */
    suspend fun search(query: String, limit: Int): List<CatalogCoin> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val widened = catalog.search(q, SEARCH_WIDEN_LIMIT)
        val pinned = defaultCoin()?.takeIf {
            it.symbol.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true)
        }
        val rest = widened.filter { it.id != pinned?.id }
        return (listOfNotNull(pinned) + rest).take(limit)
    }

    /** 基础法币的孪生默认币（PRD §9.8：USD→USDT，其余法币→USDC）。 */
    suspend fun defaultCoin(): CatalogCoin? = catalog.getByCgId(defaultCoinCgId(baseFiat()))

    companion object {
        /** 检索放大条数（置顶裁剪前的候选池）。 */
        const val SEARCH_WIDEN_LIMIT: Int = 50

        /** 默认币 cg_id（同名符号歧义下必须唯一确定——M8 修复轮 §8-1）。 */
        const val USD_TWIN_CG_ID: String = "tether"
        const val OTHER_FIAT_TWIN_CG_ID: String = "usd-coin"

        /** 法币 → 默认币 cg_id。 */
        fun defaultCoinCgId(fiat: String?): String =
            if ((fiat ?: "USD").trim().equals("USD", ignoreCase = true)) {
                USD_TWIN_CG_ID
            } else {
                OTHER_FIAT_TWIN_CG_ID
            }
    }
}
