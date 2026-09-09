package com.wuzhufolio.data.catalog

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import com.wuzhufolio.domain.catalog.CoinNames
import com.wuzhufolio.domain.catalog.CoinResolver
import com.wuzhufolio.domain.catalog.CoinStatus
import com.wuzhufolio.domain.catalog.DirectoryRefreshSummary
import com.wuzhufolio.domain.catalog.FiatLeg
import com.wuzhufolio.domain.catalog.FiatNormalizer
import com.wuzhufolio.domain.catalog.MappingSource
import com.wuzhufolio.domain.catalog.MarketRankProvider
import com.wuzhufolio.domain.catalog.NoopMarketRankProvider
import com.wuzhufolio.domain.catalog.ResolveContext
import com.wuzhufolio.domain.catalog.ResolveMethod
import com.wuzhufolio.domain.catalog.Resolution
import com.wuzhufolio.domain.catalog.UnknownCoinException
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * CoinCatalog 数据实现（M3 T3.1–T3.3；契约 = domain.catalog.CoinCatalog，api-contracts §3 补录）。
 *
 * 规则分工：CoinResolver/FiatNormalizer 为 domain 纯规则（无 IO）；本类只做 目录/映射 持久化、
 * 候选检索与规则编排，全部经 DbGate 单写队列（写）/WAL（读）。
 *
 * 键规范化：cg_id 小写 slug、symbol/exchange/asset 大写（CoinNames）——检索、消歧与映射键一致；
 * exchange_coin_map 的 (exchange, exchange_asset) UNIQUE 以归一化键维护（数据库层唯一约束守护）。
 */
@Suppress("TooManyFunctions") // CoinCatalog 契约 10 动作 + 内部帮助函数（契约见 api-contracts §3 补录登记，模块记录 M3.md §5）
class SqlCoinCatalog(
    private val gate: DbGate,
    private val rankProvider: MarketRankProvider = NoopMarketRankProvider,
    private val fiatNormalizer: FiatNormalizer = FiatNormalizer(),
    private val logger: Logger = LoggerFactory.getLogger(SqlCoinCatalog::class.java),
) : CoinCatalog {

    override suspend fun search(query: String, limit: Int): List<CatalogCoin> {
        val q = CoinNames.normalizeTicker(query)
        if (q.isEmpty()) return emptyList()
        val capped = limit.coerceIn(1, MAX_SEARCH_RESULTS)
        // 目录为纯内存可承载的小表（CG /coins/list 约 1.5–2 万行）：一次轻量扫描 + Kotlin 过滤，
        // 规避 Exposed 1.5 对 LIKE 的 DSL 可用性差异，Unicode 大小写语义也更正确。
        // 仅对命中的 ≤capped 行做 contracts 解码；性能与 FTS/索引优化见模块记录 M3.md 遗留。
        val rows = gate.read { CoinsTable.selectAll().toList() }
        return rows.asSequence()
            .filter { row ->
                row[CoinsTable.symbol].contains(q, ignoreCase = true) ||
                    row[CoinsTable.name].contains(q, ignoreCase = true)
            }
            .sortedWith(
                compareBy(
                    { relevance(rowSymbol(it), q) },
                    { if (rowStatus(it) == CoinStatus.ACTIVE) 0 else 1 },
                    { rowSymbol(it) },
                    { rowName(it) },
                ),
            )
            .take(capped)
            .map { it.toCatalogCoin() }
            .toList()
    }

    private fun rowSymbol(row: ResultRow): String = row[CoinsTable.symbol]

    private fun rowName(row: ResultRow): String = row[CoinsTable.name]

    private fun rowStatus(row: ResultRow): CoinStatus =
        CoinStatus.fromStorage(row[CoinsTable.status])

    private fun relevance(symbol: String, q: String): Int = when {
        symbol == q -> 0
        symbol.startsWith(q) -> 1
        else -> 2
    }

    override suspend fun getBySymbol(symbol: String): List<CatalogCoin> {
        val q = CoinNames.normalizeTicker(symbol)
        if (q.isEmpty()) return emptyList()
        return gate.read {
            CoinsTable.selectAll().where { CoinsTable.symbol eq q }
                .toList().map { it.toCatalogCoin() }
                .sortedWith(compareBy({ if (it.status == CoinStatus.ACTIVE) 0 else 1 }, { it.name }))
        }
    }

    override suspend fun getByCgId(cgId: String): CatalogCoin? {
        val key = CoinNames.normalizeCgId(cgId)
        if (key.isEmpty()) return null
        return gate.read {
            CoinsTable.selectAll().where { CoinsTable.cgId eq key }.singleOrNull()?.toCatalogCoin()
        }
    }

    override suspend fun getById(id: Long): CatalogCoin? = gate.read {
        CoinsTable.selectAll().where { CoinsTable.id eq id.toInt() }.singleOrNull()?.toCatalogCoin()
    }

    override suspend fun refreshDirectory(entries: List<CoinDirectoryEntry>): DirectoryRefreshSummary =
        gate.write {
            // 同批内 cg_id 去重（后者覆盖；UNIQUE(cg_id) 由 M004 守护，此处防同批重复误报约束冲突）
            val byCg = LinkedHashMap<String, CoinDirectoryEntry>()
            for (raw in entries) {
                val key = CoinNames.normalizeCgId(raw.cgId)
                if (key.isNotEmpty()) {
                    byCg[key] = raw.copy(
                        cgId = key,
                        symbol = CoinNames.normalizeTicker(raw.symbol),
                        name = raw.name.trim(),
                    )
                }
            }
            var added = 0
            var updated = 0
            val now = Instant.now().toString()
            for (entry in byCg.values) {
                val existing = CoinsTable.selectAll()
                    .where { CoinsTable.cgId eq entry.cgId }.singleOrNull()
                if (existing == null) {
                    CoinsTable.insert {
                        it[CoinsTable.cgId] = entry.cgId
                        it[cmcId] = null
                        it[symbol] = entry.symbol
                        it[name] = entry.name
                        it[status] = CoinStatus.ACTIVE.storageValue
                        it[displayPrecision] = DEFAULT_DISPLAY_PRECISION
                        it[contracts] = CoinContractsJson.encode(entry.contracts)
                        it[updatedAt] = now
                    }
                    added++
                } else {
                    val changed = existing[CoinsTable.symbol] != entry.symbol ||
                        existing[CoinsTable.name] != entry.name ||
                        CoinContractsJson.decode(existing[CoinsTable.contracts]) != entry.contracts
                    if (changed) {
                        CoinsTable.update({ CoinsTable.cgId eq entry.cgId }) {
                            it[symbol] = entry.symbol
                            it[name] = entry.name
                            it[contracts] = CoinContractsJson.encode(entry.contracts)
                            it[updatedAt] = now
                        }
                        updated++
                    }
                }
            }
            DirectoryRefreshSummary(added, updated)
        }

    override suspend fun refreshCmcIds(cmcByCgId: Map<String, String>): Int = gate.write {
        val now = Instant.now().toString()
        var changed = 0
        val pending = cmcByCgId
            .map { (rawCgId, rawCmcId) -> CoinNames.normalizeCgId(rawCgId) to rawCmcId.trim() }
            .filter { (cgId, cmcId) -> cgId.isNotEmpty() && cmcId.isNotEmpty() }
        for ((cgId, cmcId) in pending) {
            val existing = CoinsTable.selectAll()
                .where { CoinsTable.cgId eq cgId }.singleOrNull() ?: continue
            if (existing[CoinsTable.cmcId] != cmcId) {
                CoinsTable.update({ CoinsTable.cgId eq cgId }) {
                    it[CoinsTable.cmcId] = cmcId
                    it[updatedAt] = now
                }
                changed++
            }
        }
        changed
    }

    @Suppress("ReturnCount") // 复用/规则链逐级出口（NotFound/Unique 分支），与 CoinResolver 规则链一一对应
    override suspend fun resolve(exchange: String?, asset: String, context: ResolveContext): Resolution {
        val assetKey = CoinNames.normalizeTicker(asset)
        if (assetKey.isEmpty()) return Resolution.NotFound
        val exchangeKey = exchange?.let(CoinNames::normalizeCode)?.takeIf { it.isNotEmpty() }

        // 复用既有映射（AUTO/MANUAL 均一次性决策、后续自动复用；共享规范 §6 映射冻结）
        if (exchangeKey != null) {
            val frozen = mappingRow(exchangeKey, assetKey)
            if (frozen != null) {
                val coin = getById(frozen.coinId)
                return if (coin != null) {
                    Resolution.Unique(coin, ResolveMethod.FROZEN_MAP)
                } else {
                    // 孤儿映射行（coins 行缺失的异常数据）：视同未解析，交由规则链
                    Resolution.NotFound
                }
            }
        }

        val candidates = getBySymbol(assetKey)
        val outcome = CoinResolver.resolve(
            CoinResolver.RuleInput(
                candidates = candidates,
                quoteCoinId = context.quoteCoinId,
                contractChain = context.contractChain,
                contractAddress = context.contractAddress,
                rankOf = { coin -> rankProvider.rankOf(coin.cgId) },
            ),
        )
        // 自动消歧成功且上下文提供了交易所 -> 固化 AUTO，后续同资产直接复用
        if (outcome is Resolution.Unique && exchangeKey != null) {
            persistAutoMapping(exchangeKey, assetKey, outcome.coin.id)
        }
        return outcome
    }

    override suspend fun freezeMapping(exchange: String, asset: String, coinId: Long) {
        val exchangeKey = CoinNames.normalizeCode(exchange)
        val assetKey = CoinNames.normalizeTicker(asset)
        require(exchangeKey.isNotEmpty() && assetKey.isNotEmpty()) { "exchange and asset must not be blank" }
        if (getById(coinId) == null) throw UnknownCoinException(coinId)
        gate.write {
            val existing = ExchangeCoinMapTable.selectAll()
                .where {
                    (ExchangeCoinMapTable.exchange eq exchangeKey) and
                        (ExchangeCoinMapTable.exchangeAsset eq assetKey)
                }
                .singleOrNull()
            val now = Instant.now().toString()
            if (existing == null) {
                ExchangeCoinMapTable.insert {
                    it[ExchangeCoinMapTable.exchange] = exchangeKey
                    it[ExchangeCoinMapTable.exchangeAsset] = assetKey
                    it[ExchangeCoinMapTable.coinId] = coinId.toInt()
                    it[ExchangeCoinMapTable.mappingSource] = MappingSource.MANUAL.storageValue
                    it[ExchangeCoinMapTable.createdAt] = now
                    it[ExchangeCoinMapTable.updatedAt] = now
                }
            } else {
                // 已存在（AUTO 或 MANUAL）：改绑为 MANUAL + 用户所选币种（一次性决策覆盖，防后续自动改写）
                ExchangeCoinMapTable.update(
                    {
                        (ExchangeCoinMapTable.exchange eq exchangeKey) and
                            (ExchangeCoinMapTable.exchangeAsset eq assetKey)
                    },
                ) {
                    it[ExchangeCoinMapTable.coinId] = coinId.toInt()
                    it[ExchangeCoinMapTable.mappingSource] = MappingSource.MANUAL.storageValue
                    it[ExchangeCoinMapTable.updatedAt] = now
                }
            }
        }
    }

    override suspend fun mappingFor(exchange: String, asset: String): CatalogCoin? {
        val exchangeKey = CoinNames.normalizeCode(exchange)
        val assetKey = CoinNames.normalizeTicker(asset)
        return mappingRowOrNull(exchangeKey, assetKey)?.let { getById(it.coinId) }
    }

    override suspend fun exchangeAssetFor(exchange: String, coinId: Long): String? {
        val exchangeKey = CoinNames.normalizeCode(exchange)
        if (exchangeKey.isEmpty()) return null
        return gate.read {
            ExchangeCoinMapTable.selectAll()
                .where {
                    (ExchangeCoinMapTable.exchange eq exchangeKey) and
                        (ExchangeCoinMapTable.coinId eq coinId.toInt())
                }
                .toList()
        }.maxByOrNull { it[ExchangeCoinMapTable.updatedAt] }
            ?.let { it[ExchangeCoinMapTable.exchangeAsset] }
    }

    private suspend fun mappingRowOrNull(exchangeKey: String, assetKey: String): MappingRow? =
        if (exchangeKey.isEmpty() || assetKey.isEmpty()) null else mappingRow(exchangeKey, assetKey)

    override suspend fun fiatQuoteLeg(quoteSymbol: String): FiatLeg? {
        val q = CoinNames.normalizeTicker(quoteSymbol)
        if (q.isEmpty()) return null
        return when (val cls = fiatNormalizer.classify(q)) {
            is FiatNormalizer.Classification.NotFiat -> null
            is FiatNormalizer.Classification.TwinMapped -> {
                val candidates = getBySymbol(cls.stableSymbol)
                val stable = candidates.firstOrNull { it.status == CoinStatus.ACTIVE }
                    ?: candidates.firstOrNull()
                if (stable != null) {
                    FiatLeg.TwinStable(cls.fiatCode, stable)
                } else {
                    // 孪生稳定币暂不在目录（首次目录刷新前导入等极端场景）：退化为第三币种语义，仅计基础币种
                    logger.warn(
                        "twin stable {} for fiat {} not in coin catalog; treated as third-currency leg",
                        cls.stableSymbol,
                        cls.fiatCode,
                    )
                    FiatLeg.ThirdCurrency(cls.fiatCode)
                }
            }
            is FiatNormalizer.Classification.ThirdCurrencyFiat -> FiatLeg.ThirdCurrency(cls.fiatCode)
        }
    }

    // ---------- internals ----------

    private data class MappingRow(val coinId: Long, val source: String)

    private suspend fun mappingRow(exchange: String, asset: String): MappingRow? = gate.read {
        ExchangeCoinMapTable.selectAll()
            .where {
                (ExchangeCoinMapTable.exchange eq exchange) and
                    (ExchangeCoinMapTable.exchangeAsset eq asset)
            }
            .singleOrNull()
            ?.let { MappingRow(it[ExchangeCoinMapTable.coinId].toLong(), it[ExchangeCoinMapTable.mappingSource]) }
    }

    private suspend fun persistAutoMapping(exchange: String, asset: String, coinId: Long) = gate.write {
        val existing = ExchangeCoinMapTable.selectAll()
            .where {
                (ExchangeCoinMapTable.exchange eq exchange) and
                    (ExchangeCoinMapTable.exchangeAsset eq asset)
            }
            .singleOrNull()
        if (existing == null) {
            val now = Instant.now().toString()
            ExchangeCoinMapTable.insert {
                it[ExchangeCoinMapTable.exchange] = exchange
                it[ExchangeCoinMapTable.exchangeAsset] = asset
                it[ExchangeCoinMapTable.coinId] = coinId.toInt()
                it[ExchangeCoinMapTable.mappingSource] = MappingSource.AUTO.storageValue
                it[ExchangeCoinMapTable.createdAt] = now
                it[ExchangeCoinMapTable.updatedAt] = now
            }
        }
        // 已存在（MANUAL/AUTO）不覆盖：MANUAL 为一次性决策结果，AUTO 等价复用，保持稳定
    }

    private fun ResultRow.toCatalogCoin(): CatalogCoin = CatalogCoin(
        id = this[CoinsTable.id].toLong(),
        cgId = this[CoinsTable.cgId],
        cmcId = this[CoinsTable.cmcId],
        symbol = this[CoinsTable.symbol],
        name = this[CoinsTable.name],
        status = CoinStatus.fromStorage(this[CoinsTable.status]),
        displayPrecision = this[CoinsTable.displayPrecision],
        contracts = CoinContractsJson.decode(this[CoinsTable.contracts]),
    )


    companion object {
        const val DEFAULT_DISPLAY_PRECISION = 8

        const val MAX_SEARCH_RESULTS = 100
    }
}
