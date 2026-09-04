package com.wuzhufolio.domain.market

import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import java.time.Instant

/**
 * 行情平台客户端契约（T5.1 · ADR-003 §1–3 统一抽象；端点在 data 实现内对齐契约表）。
 *
 * - [fetchCurrent]：CG 批量当前价（/simple/price，币键 = cg_id）——主源；
 * - [fetchCmcCurrent]：CMC 批量当前价（/cryptocurrency/quotes/latest，币键 = **CMC id**，单次 ≤100）——兜底源；
 *   两者返回币键语义不同（cg_id vs cmc_id），编排层经 coins.cmc_id 桥接；
 * - 未收录币直接缺席（不回传占位），由编排层比对请求集得到「无行情」集；
 * - [fetchHistory]（CG /market_chart/range）、[fetchDirectory]（CG /coins/list）仅主源；
 *   [fetchCmcMap]（CMC /cryptocurrency/map）仅兜底源；非所属实现的默认实现抛
 *   [UnsupportedOperationException]（CMC 无免费历史端点——历史回填不做兜底，PRD 共享规范 §5）；
 * - [fetchMarketRanking]：CG /coins/markets 市值榜页（预缓存前 1000 名，共享规范 §6 消歧规则③）——主源；
 * - 调用失败抛 [MarketApiException]（kind = 429/401/402/404/网络/HTTP）。
 *
 * Key 语义：个人 Key 仅作每次请求的入参（不进对象状态）；无 Key = null（公共 API 模式）。
 */
interface MarketDataClient {

    /** CG 批量当前价；coins/fiats 为请求子集（单批 ≤100 币，编排层分批）。 */
    suspend fun fetchCurrent(
        coins: List<String>,
        fiats: List<String>,
        apiKey: String?,
    ): List<MarketQuote> = throw UnsupportedOperationException("fetchCurrent is CoinGecko-only")

    /** CMC 批量当前价；cmcIds 为 CMC 内部 id（单批 ≤100，编排层分批）；未收录币缺席。 */
    suspend fun fetchCmcCurrent(
        cmcIds: List<Long>,
        fiats: List<String>,
        apiKey: String,
    ): List<CmcQuote> = throw UnsupportedOperationException("fetchCmcCurrent is CoinMarketCap-only")

    /** 历史价区间（CG；from/to UTC；跨度 ≤90 天小时级、更早日级——CG 服务端口径）。 */
    suspend fun fetchHistory(
        coin: String,
        fiat: String,
        from: Instant,
        to: Instant,
        apiKey: String?,
    ): List<MarketCandle> = throw UnsupportedOperationException("fetchHistory is CoinGecko-only")

    /** 全量目录（CG /coins/list，include_platform=true，每日 1 次）。 */
    suspend fun fetchDirectory(apiKey: String?): List<CoinDirectoryEntry> =
        throw UnsupportedOperationException("fetchDirectory is CoinGecko-only")

    /** CMC 币种映射页（/cryptocurrency/map；[start] = 起始位，单页 [CMC_PAGE_LIMIT] 条，空页 = 结束；cmc_id 对齐 coins）。 */
    suspend fun fetchCmcMap(apiKey: String, start: Long = 1): List<CmcMapCoin> =
        throw UnsupportedOperationException("fetchCmcMap is CoinMarketCap-only")

    companion object {
        /** CMC map 单页条数上限（平台参数上限 5000）。 */
        const val CMC_PAGE_LIMIT: Int = 5000
    }

    /** CG 市值榜页（/coins/markets，order=market_cap_desc；前 1000 名 = 4 × 250）。 */
    suspend fun fetchMarketRanking(page: Int, perPage: Int, apiKey: String?): List<MarketRank> =
        throw UnsupportedOperationException("fetchMarketRanking is CoinGecko-only")
}

/** 市值榜条目（cg_id + 榜内名次；消歧规则③「市值排名最高」输入）。 */
data class MarketRank(val cgId: String, val rank: Int)
