package com.wuzhufolio.data.market

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
 * 自选实现（D21）：settings 全局行 watch.coins（JSON [cg_id]）；未写入 → 默认种子（稳定币白名单）。
 * 目录解析在每次读取时进行（缺行跳过）；目录行顺序 = 存储序。
 */
class SettingsMarketWatchService(
    private val settings: SettingsRepository,
    private val catalog: CoinCatalog,
    private val logger: Logger = LoggerFactory.getLogger(SettingsMarketWatchService::class.java),
) : MarketWatchService {

    override suspend fun hasCustomList(): Boolean = settings.getGlobal(MarketWatchService.SETTINGS_KEY) != null

    override suspend fun watchCoins(): List<CatalogCoin> {
        val stored = readStoredIds()
        val ids = stored ?: PortfolioCalculator.DEFAULT_CASH_COIN_IDS.toList()
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

    override suspend fun addCoin(cgId: String) {
        val current = readStoredIds() ?: PortfolioCalculator.DEFAULT_CASH_COIN_IDS.toList()
        if (cgId in current) return
        require(current.size < MarketWatchService.WATCH_LIMIT) {
            "watch list is full (${MarketWatchService.WATCH_LIMIT})"
        }
        persist(current + cgId)
    }

    override suspend fun removeCoin(cgId: String) {
        val current = readStoredIds() ?: PortfolioCalculator.DEFAULT_CASH_COIN_IDS.toList()
        persist(current.filterNot { it == cgId })
    }

    override suspend fun searchCandidates(query: String, limit: Int): List<CatalogCoin> =
        catalog.search(query, limit)

    private fun readStoredIds(): List<String>? {
        val raw = settings.getGlobal(MarketWatchService.SETTINGS_KEY) ?: return null
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrElse {
            logger.warn("watch.coins payload corrupt ({}); treat as default seed", it.message)
            null
        }
    }

    private fun persist(ids: List<String>) {
        settings.putGlobal(MarketWatchService.SETTINGS_KEY, json.encodeToString(ids))
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
