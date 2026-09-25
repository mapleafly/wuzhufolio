package com.wuzhufolio.domain.market

import com.wuzhufolio.domain.catalog.CatalogCoin
import java.math.BigDecimal
import java.time.Instant

/**
 * D21 行情浏览页契约（决策：docs/dev/decisions/D21-行情浏览页-范围增量.md）。
 *
 * 自选为展示偏好：settings 全局行 `watch.coins`（JSON 数组 [cg_id]，**无数量上限**）持久化；
 * **未写入过**时返回默认种子（= 仅 USDT，与现金白名单默认值 D28 对齐；用户可自行添加其他币种）；
 * 已写入（可为空数组）按存储返回。币展示信息（symbol/name）每次经 coins 目录解析——目录缺行显示时跳过
 * （解析期清理，不静默改写存储；目录恢复后重新出现）。
 */
interface MarketWatchService {

    /** 当前展示币集（目录解析后的 CatalogCoin 行，顺序 = 存储序）。 */
    suspend fun watchCoins(): List<CatalogCoin>

    /** 是否已写入过自选（未写入 = 默认种子态；UI 提示种子来源用）。 */
    suspend fun hasCustomList(): Boolean

    /** 加入自选（已存在幂等）。 */
    suspend fun addCoin(cgId: String)

    /**
     * 批量加入自选（**D35**：成交币自动进入行情自选）。
     *
     * 口径（2026-09-24 人工拍板）：
     * - **幂等**：已存在的 cg_id 不重复写入、不改动既有顺序（新币追加在末尾）；
     * - **不设上限**：自选数量不限制（原 50 条上限已取消）；
     * - 调用方（交易写入 / 交易所同步）**必须**容忍失败：自选维护失败不得影响交易落账。
     */
    suspend fun addCoins(cgIds: Collection<String>)

    suspend fun removeCoin(cgId: String)

    /** coins 目录搜索候选（大小写归一，复用 CoinCatalog.search 消歧口径）。 */
    suspend fun searchCandidates(query: String, limit: Int = 8): List<CatalogCoin>

    companion object {
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
