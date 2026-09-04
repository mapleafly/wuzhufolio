package com.wuzhufolio.domain.market

import com.wuzhufolio.domain.catalog.CatalogCoin
import java.math.BigDecimal
import java.time.Instant

/**
 * D21 行情浏览页契约（决策：docs/dev/decisions/D21-行情浏览页-范围增量.md）。
 *
 * 自选为展示偏好：settings 全局行 `watch.coins`（JSON 数组 [cg_id]，上限 [WATCH_LIMIT]）持久化；
 * **未写入过**时返回默认种子（稳定币白名单——引擎口径 PortfolioCalculator.DEFAULT_CASH_COIN_IDS 同源）；
 * 已写入（可为空数组）按存储返回。币展示信息（symbol/name）每次经 coins 目录解析——目录缺行显示时跳过
 * （解析期清理，不静默改写存储；目录恢复后重新出现）。
 */
interface MarketWatchService {

    /** 当前展示币集（目录解析后的 CatalogCoin 行，顺序 = 存储序）。 */
    suspend fun watchCoins(): List<CatalogCoin>

    /** 是否已写入过自选（未写入 = 默认种子态；UI 提示种子来源用）。 */
    suspend fun hasCustomList(): Boolean

    /** 加入自选（已存在幂等；超过 [WATCH_LIMIT] 抛 [IllegalArgumentException]）。 */
    suspend fun addCoin(cgId: String)

    suspend fun removeCoin(cgId: String)

    /** coins 目录搜索候选（大小写归一，复用 CoinCatalog.search 消歧口径）。 */
    suspend fun searchCandidates(query: String, limit: Int = 8): List<CatalogCoin>

    companion object {
        const val WATCH_LIMIT: Int = 50
        const val SETTINGS_KEY = "watch.coins"
    }
}

/** 行情页行（现价口径：price_snapshots.latest(coin, fiat)——缺行 = 「无行情」（price/source/at 均为 null）。 */
data class WatchQuoteRow(
    val cgId: String,
    val symbol: String,
    val name: String,
    val fiat: String,
    val price: BigDecimal?,
    val source: PriceSource?,
    val at: Instant?,
) {
    /** 无行情/未定价展示 "--"。 */
    val priced: Boolean get() = price != null
}

/** 报价组装（UI 行数据源：目录行 + 快照 latest）。 */
interface MarketQuotesService {

    /** 为 [coins] 逐币取基础法币 [fiat] 的最近快照行。 */
    suspend fun quotesFor(coins: List<CatalogCoin>, fiat: String): List<WatchQuoteRow>
}
