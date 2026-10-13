package com.wuzhufolio.data.market

import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.engine.PortfolioCalculator
import com.wuzhufolio.domain.market.MarketQuotesService
import com.wuzhufolio.domain.market.MarketWatchService
import com.wuzhufolio.domain.market.WatchQuoteRow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 自选实现（D21 → **D41 修订**：**账户级**）。
 *
 * **D41（2026-09-29 人工拍板，C2 / 账户隔离 `§1.1-5`）**：自选从「全局行」改为**账户级 settings 行**
 * （`settings(key='watch.coins', account_id=<当前账户>)`，JSON `[cg_id]`）——
 * 每个账户拥有独立且**有序**的自选，账户间互不可见；**未登录不展示任何自选**（返回空集）。
 *
 * 选「账户级 settings 行」而非新建表的原因：`settings` 表本就有 `account_id` 列与
 * `COALESCE(account_id,'')` 唯一索引；`.cpro` 备份**已覆盖账户级 settings**（ADR-005 §3）⇒
 * **零 schema 变更、零备份格式变更**，而账户隔离与排序语义（JSON 数组保序）完全达成。
 *
 * **迁移（升级用户）**：旧全局行 `watch.coins` 无法归属账户 ⇒ 由**升级后首个访问自选的账户一次性"认领"**
 * （复制为该账户的自选）并写入全局标记 `watch.migrated=1`；旧行保留只读，便于回溯。幂等：标记置位后不再认领。
 *
 * 目录解析在每次读取时进行（缺行跳过）；目录行顺序 = 存储序。
 */
@Suppress("TooManyFunctions") // 契约方法 + 账户/迁移/解析私有助手；拆分反而降低可读性
class SettingsMarketWatchService(
    private val settings: SettingsRepository,
    private val catalog: CoinCatalog,
    private val sessions: ActiveSessionStore,
    private val logger: Logger = LoggerFactory.getLogger(SettingsMarketWatchService::class.java),
) : MarketWatchService {

    override suspend fun hasCustomList(): Boolean = currentAccountId()?.let { readStoredIds(it) } != null

    override suspend fun watchCoins(): List<CatalogCoin> {
        // D41：**未登录不展示任何自选**（空集，而非默认种子——种子属于"已登录但尚未写入"的账户）
        val accountId = currentAccountId() ?: return emptyList()
        val stored = readStoredIds(accountId)
        val ids = stored ?: MarketConfig.DEFAULT_WATCH_SEED
        val out = mutableListOf<CatalogCoin>()
        for (cgId in ids) {
            val coin = catalog.getByCgId(cgId)
            if (coin != null) out += coin
        }
        if (stored != null && out.size < stored.size) {
            logger.info("watch coins pruned: {} of {} resolvable (coin rows missing in catalog)", out.size, stored.size)
        }
        return out
    }

    override suspend fun addCoin(cgId: String) = addCoins(listOf(cgId))

    /**
     * 批量加入（**D35**）：幂等、去重、**不设上限**（2026-09-24 人工口径：取消原 50 条上限）。
     *
     * 顺序语义：新币按传入顺序**追加在末尾**，已存在的不动（不打乱用户既有排列）。
     * 未写入过自选时（默认种子态）以种子为基；`cgIds` 里的空串/重复项自动剔除。
     */
    override suspend fun addCoins(cgIds: Collection<String>) {
        // 未登录：无账户可归属，静默跳过（调用方本就容忍失败）
        currentAccountId()?.let { accountId ->
            val incoming = cgIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (incoming.isNotEmpty()) {
                val current = readStoredIds(accountId) ?: MarketConfig.DEFAULT_WATCH_SEED
                val fresh = incoming.filterNot { it in current }
                if (fresh.isNotEmpty()) persist(accountId, current + fresh)
            }
        }
    }

    override suspend fun removeCoin(cgId: String) {
        val accountId = currentAccountId() ?: return
        val current = readStoredIds(accountId) ?: MarketConfig.DEFAULT_WATCH_SEED
        persist(accountId, current.filterNot { it == cgId })
    }

    override suspend fun searchCandidates(query: String, limit: Int): List<CatalogCoin> =
        catalog.search(query, limit)

    /** 当前账户 id（未登录 = null ⇒ 自选不可见、不可写）。 */
    private fun currentAccountId(): Int? = sessions.get()?.account?.id

    /** 读取账户自选；升级用户首访时先做一次性认领（见类头注「迁移」）。 */
    private fun readStoredIds(accountId: Int): List<String>? {
        val existing = settings.getAccount(accountId, MarketWatchService.SETTINGS_KEY)
        if (existing == null) claimLegacyIfFirst(accountId)
        return settings.getAccount(accountId, MarketWatchService.SETTINGS_KEY)?.let(::decode)
    }

    /**
     * 旧全局清单的一次性认领（D41 §3.2）：仅当全局标记未置位、且当前账户尚无自选时执行；
     * 认领后置位 `watch.migrated`（全局行保留只读，不再参与展示）。
     */
    private fun claimLegacyIfFirst(accountId: Int) {
        if (settings.getGlobal(MIGRATED_KEY) != null) return
        val legacy = settings.getGlobal(MarketWatchService.SETTINGS_KEY)
        if (legacy != null) {
            settings.putAccount(accountId, MarketWatchService.SETTINGS_KEY, legacy)
            logger.info("watch list claimed from legacy global row | account={}", accountId)
        }
        settings.putGlobal(MIGRATED_KEY, "1")
    }

    private fun decode(raw: String): List<String>? =
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrElse {
            logger.warn("watch.coins payload corrupt ({}); treat as default seed", it.message)
            null
        }

    private fun persist(accountId: Int, ids: List<String>) {
        settings.putAccount(accountId, MarketWatchService.SETTINGS_KEY, json.encodeToString(ids))
    }

    private val json: Json get() = watchJson

    companion object {
        private val watchJson: Json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * 报价组装实现（D21）：行 = coins 目录（symbol/name）+ price_snapshots.latest(coin, fiat)。
 */
class SnapshotMarketQuotesService(
    private val snapshots: PriceSnapshotRepository,
) : MarketQuotesService {

    override suspend fun quotesFor(coins: List<CatalogCoin>, fiat: String): List<WatchQuoteRow> {
        val out = mutableListOf<WatchQuoteRow>()
        for (coin in coins) {
            val row = snapshots.latest(coin.id.toInt(), fiat)
            out += WatchQuoteRow(
                cgId = coin.cgId,
                symbol = coin.symbol,
                name = coin.name,
                fiat = fiat,
                price = row?.price,
                source = row?.source,
                at = row?.recordedAt,
            )
        }
        return out
    }
}

/** D41 迁移标记（全局行）：置位后不再做旧全局清单的一次性认领。 */
private const val MIGRATED_KEY = "watch.migrated"
