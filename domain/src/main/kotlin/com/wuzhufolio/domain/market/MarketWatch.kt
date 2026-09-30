package com.wuzhufolio.domain.market

import com.wuzhufolio.domain.catalog.CatalogCoin
import java.math.BigDecimal
import java.time.Instant

/**
 * D21 行情浏览页契约（决策：docs/dev/decisions/D21-行情浏览页-范围增量.md）。
 *
 * **D41 修订（2026-09-29 人工拍板，C2 / 账户隔离 `§1.1-5`）**：自选为**账户级**偏好——
 * `settings(key='watch.coins', account_id=<当前账户>)`（JSON 数组 [cg_id]，**无数量上限**，数组序即展示序）。
 * 账户之间互不可见；**未登录返回空集**（不展示任何账户的自选，也不可写）。
 *
 * 取值口径：账户**未写入过** → 默认种子（= 仅 USDT，与现金白名单默认值 D28 对齐）；
 * 已写入（可为空数组）→ 按存储返回。币展示信息（symbol/name）每次经 coins 目录解析——目录缺行显示时跳过
 * （解析期清理，不静默改写存储；目录恢复后重新出现）。
 *
 * 升级迁移：旧全局行 `watch.coins` 由**首个访问自选的账户一次性认领**（`D41 §3.2`）。
 */
interface MarketWatchService {

    /** 当前**账户**的展示币集（目录解析后的 CatalogCoin 行，顺序 = 存储序；未登录 = 空集）。 */
    suspend fun watchCoins(): List<CatalogCoin>

    /** 当前账户是否已写入过自选（未写入 = 默认种子态；未登录 = false）。 */
    suspend fun hasCustomList(): Boolean

    /** 加入**当前账户**的自选（已存在幂等；未登录 = 无操作）。 */
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

    /** 从**当前账户**自选移除（未登录 = 无操作）。 */
    suspend fun removeCoin(cgId: String)

    /** coins 目录搜索候选（大小写归一，复用 CoinCatalog.search 消歧口径）。 */
    suspend fun searchCandidates(query: String, limit: Int = 8): List<CatalogCoin>

    companion object {
        /**
         * 自选存储键。**D41 起为账户级行**（`account_id` 非空）；同名的**历史全局行**仅在迁移期被
         * 一次性认领时**只读**（见 `D41 §3.2` 与 `SettingsKeyNamespaceGuardTest.legacyGlobalKeys`）。
         */
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
