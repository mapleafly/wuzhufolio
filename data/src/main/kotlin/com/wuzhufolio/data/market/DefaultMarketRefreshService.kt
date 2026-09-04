package com.wuzhufolio.data.market

import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.market.CmcMapCoin
import com.wuzhufolio.domain.market.MarketApiException
import com.wuzhufolio.domain.market.MarketDataClient
import com.wuzhufolio.domain.market.MarketKeyStatus
import com.wuzhufolio.domain.market.MarketQuote
import com.wuzhufolio.domain.market.MarketRank
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.MarketRefreshResult
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.domain.market.QuotaCallKind
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * 行情刷新编排（T5.1/T5.4 · ADR-003 §4 两级模式落地）：
 *
 * 1. 目录维护（>24h 过期才刷新）：CG /coins/list → coins 目录；/coins/markets 4 页 → 市值榜缓存；
 *    CMC /cryptocurrency/map（配置兜底 Key 时）→ cmc_id 对齐落库；
 * 2. 当前价：CG 主源分批拉取（无 Key = 公共 API；个人 Key 模式计数）；缺席/失败币 → 兜底面；
 * 3. 兜底：主源失败或币缺席，且已配置 CMC Key、目标币有 cmc_id → CMC /quotes/latest（≤100/批）；
 *    仍无价 → 「无行情」（untracked）；兜底期间写快照 source=COINMARKETCAP；
 * 4. 主源成功默认回落：每次刷新重新尝试主源，无持久状态（PRD 共享规范 §5）；
 * 5. 全部失败（或无兜底配置）→ [MarketRefreshResult.error]（保持上次价格 + 时间戳语义）；
 *    429 由调度宿主按 RateBackoff 退避（本编排不做重试风暴）。
 *
 * 计数（月度额度治理）：仅配置个人 CG Key 后记录（无 Key 模式无月度专属额度，只退避 + 降频）。
 * 单飞（Mutex）：调度与手动并发时只执行一轮。
 */
@Suppress("LongParameterList", "TooManyFunctions") // 编排依赖面广（构造注入、无服务定位器）+ 动作面固有（目录/刷新/兜底/账本/键），边界编排类
class DefaultMarketRefreshService(
    private val cgClient: MarketDataClient,
    private val cmcClient: MarketDataClient,
    private val catalog: CoinCatalog,
    private val snapshots: PriceSnapshotRepository,
    private val keyStore: DeviceSecretStore,
    private val settings: SettingsRepository,
    private val quota: SettingsQuotaLedger,
    private val rankCache: RefreshableRankProvider,
    private val clock: () -> Instant = Instant::now,
    private val logger: Logger = LoggerFactory.getLogger(DefaultMarketRefreshService::class.java),
) : MarketRefreshService {

    private val flight = Mutex()

    @Volatile
    private var last: MarketRefreshResult? = null

    override fun lastResult(): MarketRefreshResult? = last

    override suspend fun refresh(
        manual: Boolean,
        coins: List<String>,
        fiats: List<String>,
    ): MarketRefreshResult = flight.withLock {
        logger.info("market refresh started manual={}", manual)
        val keys = keyStatusOf()
        if (!directoryFresh()) {
            runCatching { refreshDirectory(keys) }
                .onFailure { logger.warn("directory refresh failed: {}", it.message) }
        }
        val targets = (coins.ifEmpty { MarketConfig.DEFAULT_FALLBACK_COINS }).distinct()
        val result = fetchCurrentWithFallback(targets, fiats.ifEmpty { defaultFiats() }, keys)
        last = result
        logger.info(
            "market refresh finished source={} coins={} untracked={} error={}",
            result.source, result.refreshedCoins, result.untracked.size, result.error,
        )
        result
    }

    override suspend fun quotaPercentUsed(): Int? =
        if (keyStore.configured(MarketConfig.KEY_CG)) quota.percentUsed() else null

    override suspend fun directoryFresh(): Boolean {
        val staleOrUnknown = settings.getGlobal(DIRECTORY_LAST_KEY)
            ?.let { raw -> runCatching { Instant.parse(raw) }.getOrNull() }
            ?.let { at -> at.isBefore(clock().minusSeconds(DIRECTORY_TTL_SECONDS)) }
            ?: true
        return !staleOrUnknown
    }

    // ---- 当前价：主源 CG → 兜底 CMC ----

    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
    // 编排边界（主源分批 + 兜底 + 缺席/错误归并），分支即语义；细拆会碎片化单飞与计费顺序保证
    private suspend fun fetchCurrentWithFallback(
        targets: List<String>,
        fiats: List<String>,
        keys: MarketKeyStatus,
    ): MarketRefreshResult {
        val cgKey = if (keys.cgConfigured) keyStore.get(MarketConfig.KEY_CG, MarketConfig.PURPOSE_CG) else null
        val cmcKey = if (keys.cmcConfigured) keyStore.get(MarketConfig.KEY_CMC, MarketConfig.PURPOSE_CMC) else null

        val written = LinkedHashSet<String>()
        var wroteCg = false
        var wroteCmc = false
        var primaryError: MarketRefreshError? = null
        var fallbackError: MarketRefreshError? = null

        val byCg = resolveCoinIds(targets)
        if (byCg.isEmpty()) {
            return emptyResult(targets, keys)
        }

        // 主源 CG（分批；首错即停——源级故障直接转兜底）
        val pending = LinkedHashMap(byCg)
        val chunks = byCg.keys.chunked(MarketConfig.CG_CURRENT_BATCH)
        var chunkIndex = 0
        while (chunkIndex < chunks.size && primaryError == null) {
            val chunk = chunks[chunkIndex++]
            try {
                val quotes = cgClient.fetchCurrent(chunk, fiats, cgKey)
                persistQuotes(quotes, byCg, written)
                if (quotes.isNotEmpty()) wroteCg = true
                quotes.forEach { pending.remove(it.coin) }
            } catch (e: MarketApiException) {
                primaryError = e.kind
            } finally {
                // 月度计数按「发起请求」口径（失败也计——PRD：含当前价刷新的月度调用计数）
                if (cgKey != null) quota.record(QuotaCallKind.CURRENT)
            }
        }

        // 兜底 CMC：主源失败或仍有缺席币
        val needFallback = (primaryError != null || pending.isNotEmpty()) && cmcKey != null
        if (needFallback) {
            val plan = cmcIdsOf(pending.keys.toList())
            for (chunk in plan.chunked(MarketConfig.CMC_BATCH_LIMIT)) {
                try {
                    val idToCg = chunk.toMap()
                    val quotes = cmcClient.fetchCmcCurrent(chunk.map { it.first }, fiats, cmcKey)
                    val mapped = quotes.mapNotNull { q ->
                        val cgId = idToCg[q.cmcId] ?: return@mapNotNull null
                        MarketQuote(cgId, q.fiat, q.price, PriceSource.COINMARKETCAP, q.at)
                    }
                    persistQuotes(mapped, byCg, written)
                    if (mapped.isNotEmpty()) {
                        wroteCmc = true
                        mapped.forEach { pending.remove(it.coin) }
                    }
                } catch (e: MarketApiException) {
                    fallbackError = e.kind
                } finally {
                    // needFallback 分支已保证 cmcKey 非空（智能推导）；月度计数按发起请求口径
                    quota.record(QuotaCallKind.CURRENT)
                }
            }
        }

        val source = when {
            wroteCg -> PriceSource.COINGECKO
            wroteCmc -> PriceSource.COINMARKETCAP
            else -> null
        }
        val error = if (written.isEmpty()) {
            primaryError ?: fallbackError
        } else {
            null
        }
        return MarketRefreshResult(
            at = if (written.isEmpty()) null else clock(),
            source = source,
            refreshedCoins = written.size,
            untracked = pending.keys.toList(),
            quotaPercentUsed = quotaPercentOf(keys),
            error = error,
            cgConfigured = keys.cgConfigured,
            cmcConfigured = keys.cmcConfigured,
        )
    }

    private fun emptyResult(targets: List<String>, keys: MarketKeyStatus) = MarketRefreshResult(
        at = null, source = null, refreshedCoins = 0,
        untracked = targets, quotaPercentUsed = quotaPercentOf(keys), error = null,
        cgConfigured = keys.cgConfigured, cmcConfigured = keys.cmcConfigured,
    )

    private suspend fun persistQuotes(
        quotes: List<MarketQuote>,
        byCg: Map<String, Int>,
        written: MutableSet<String>,
    ) {
        val rows = quotes.mapNotNull { q ->
            val coinId = byCg[q.coin] ?: return@mapNotNull null
            written += q.coin
            SnapshotWrite(coinId, q.fiat, q.price, q.source, q.at)
        }
        if (rows.isNotEmpty()) snapshots.upsertBatch(rows)
    }

    // ---- 目录 / 市值榜 / cmc_id 每日维护 ----

    private suspend fun refreshDirectory(keys: MarketKeyStatus) {
        val cgKey = rawCgKey(keys)
        val entries = cgClient.fetchDirectory(cgKey)
        if (cgKey != null) quota.record(QuotaCallKind.DIRECTORY) // 目录请求发起即计数
        val summary = catalog.refreshDirectory(entries)
        logger.info("coin directory refreshed added={} updated={}", summary.added, summary.updated)

        val ranked = mutableListOf<MarketRank>()
        var page = 1
        var morePages = true
        while (page <= RANK_PAGES && morePages) {
            val part = cgClient.fetchMarketRanking(page, RANK_PAGE_SIZE, cgKey)
            if (cgKey != null) quota.record(QuotaCallKind.DIRECTORY)
            ranked += part
            morePages = part.size >= RANK_PAGE_SIZE
            page++
        }
        rankCache.update(ranked)

        val cmcKey = if (keys.cmcConfigured) keyStore.get(MarketConfig.KEY_CMC, MarketConfig.PURPOSE_CMC) else null
        if (cmcKey != null) syncCmcMap(cmcKey)
        settings.putGlobal(DIRECTORY_LAST_KEY, clock().toString())
    }

    private suspend fun syncCmcMap(cmcKey: String) {
        val aligner = CmcIdAligner(catalog)
        val collected = mutableListOf<CmcMapCoin>()
        var start = 1L
        var morePages = true
        while (morePages && collected.size < CMC_MAP_MAX_PAGE * MarketDataClient.CMC_PAGE_LIMIT) {
            val page = cmcClient.fetchCmcMap(cmcKey, start)
            quota.record(QuotaCallKind.DIRECTORY)
            if (page.isEmpty()) {
                morePages = false
            } else {
                collected += page
                morePages = page.size >= MarketDataClient.CMC_PAGE_LIMIT
                start += page.size
            }
        }
        val aligned = aligner.align(collected)
        val changed = catalog.refreshCmcIds(aligned)
        logger.info("cmc map synced pages={} aligned={} changed={}", collected.size, aligned.size, changed)
    }

    // ---- 小工具 ----

    private suspend fun resolveCoinIds(cgIds: List<String>): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for (cgId in cgIds) {
            val coin = catalog.getByCgId(cgId) ?: continue
            out[cgId] = coin.id.toInt()
        }
        return out
    }

    private suspend fun cmcIdsOf(cgIds: List<String>): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        for (cgId in cgIds) {
            val coin = catalog.getByCgId(cgId)
            val cmcId = coin?.cmcId?.toLongOrNull()
            if (coin != null && cmcId != null) out += cmcId to cgId
        }
        return out
    }

    private fun rawCgKey(keys: MarketKeyStatus): String? =
        if (keys.cgConfigured) keyStore.get(MarketConfig.KEY_CG, MarketConfig.PURPOSE_CG) else null

    private fun keyStatusOf(): MarketKeyStatus = MarketKeyStatus(
        cgConfigured = keyStore.configured(MarketConfig.KEY_CG),
        cmcConfigured = keyStore.configured(MarketConfig.KEY_CMC),
    )

    private fun defaultFiats(): List<String> =
        listOf(settings.getGlobal("fiat")?.takeIf { it.isNotBlank() } ?: "USD")

    private fun quotaPercentOf(keys: MarketKeyStatus): Int? =
        if (keys.cgConfigured) quota.percentUsed() else null

    private companion object {
        const val DIRECTORY_LAST_KEY = "market.directory.last"
        const val DIRECTORY_TTL_SECONDS: Long = 24 * 3600
        const val RANK_PAGES = 4
        const val RANK_PAGE_SIZE = 250
        const val CMC_MAP_MAX_PAGE = 4
    }
}

