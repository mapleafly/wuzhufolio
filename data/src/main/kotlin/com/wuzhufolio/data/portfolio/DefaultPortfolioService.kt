package com.wuzhufolio.data.portfolio

import com.wuzhufolio.data.accounts.ActiveSession
import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.data.ledger.DefaultTransactionLedgerService
import com.wuzhufolio.data.ledger.FundFlowRepository
import com.wuzhufolio.data.ledger.LedgerEventAssembler
import com.wuzhufolio.data.ledger.LedgerRowsSource
import com.wuzhufolio.data.ledger.LedgerTransactionRepository
import com.wuzhufolio.data.ledger.ReconciliationRepository
import com.wuzhufolio.data.ledger.TransactionEventBuilder
import com.wuzhufolio.data.market.PriceSnapshotRepository
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.engine.HoldingMetrics
import com.wuzhufolio.domain.engine.LedgerMath
import com.wuzhufolio.domain.engine.NegativePolicy
import com.wuzhufolio.domain.engine.PortfolioCalculator
import com.wuzhufolio.domain.engine.PortfolioMetrics
import com.wuzhufolio.domain.engine.ReconciliationService
import com.wuzhufolio.domain.engine.ReplayEngine
import com.wuzhufolio.domain.engine.ReplayOutcome
import com.wuzhufolio.domain.ledger.TxFilter
import com.wuzhufolio.domain.market.PriceResolution
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.domain.market.TwentyFourHour
import com.wuzhufolio.domain.portfolio.CalibrationRecord
import com.wuzhufolio.domain.portfolio.CoinDetail
import com.wuzhufolio.domain.portfolio.PortfolioRow
import com.wuzhufolio.domain.portfolio.PortfolioService
import com.wuzhufolio.domain.portfolio.PortfolioSnapshot
import java.math.BigDecimal
import java.time.Instant

/**
 * 聚合页用例实现（M12 T12.1 · 契约 = [PortfolioService]，冻结不改）。
 *
 * 编排口径（**全只读**：仪表盘/资产列表/币种详情均无写入路径）：
 * - 账户 = [ActiveSessionStore] 当前会话；基础法币 = settings 全局键 fiat（与 M7/M8 同源）；
 * - 事件全集 = [LedgerEventAssembler]（transactions + capital_flows + reconciliation_records 三表合并）
 *   -> `ReplayEngine.replay(LENIENT)`：聚合页沿用导入口径（既存负持仓按「持仓异常」展示，不阻断页面）；
 * - 账户级指标一律取自 [PortfolioCalculator]（净值/可用现金/投入本金/ROI/已实现/未实现），本层不重算；
 * - 现价解析链（**刻意与 `DefaultFundService.fundsOverview` 同源**：首次行情刷新成功前 / 离线时，
 *   仪表盘净值与资金页「可用现金」必须给出一致结论）：① 现价快照
 *   `price_snapshots.latest(coins 行 id, 基础法币)`（带数据源与快照时刻，D21 行情页同源口径）
 *   → ② 快照缺失时回落 [TransactionEventBuilder.currentPrice]（快照 → USD 锚定稳定币 1:1 → 最近可得
 *   估算）。两路皆无 = 目录未收录或无任何可得价的「无行情」（不计入净值，[PortfolioRow.priced] = false）；
 *   兜底价没有快照行 → 不计入 [PortfolioSnapshot.priceAsOf]（不臆造时刻）；
 * - 24h = 固定数量回算法 [TwentyFourHour]：现价快照 + (now − 24h) 小时桶行配对（[PriceResolution]，
 *   配对仍只认快照——兜底价无来源/无历史行，priceAgo 记 null 由覆盖数体现），只统计持仓 > 0 的币，
 *   异源配对标「混合数据源」；
 * - 行序 = 市值降序、缺价币置末按名称升序、cg_id 决胜（interaction.md §3-1）。
 *
 * 与 M7/M8 的分工（避免重复实现）：
 * - 币种详情的交易列表按契约不在 [CoinDetail] 内——UI 层直接用 M7 `TransactionLedgerService`
 *   （`TxFilter(coinSymbol = symbol)`：交易对任一腿命中即算本币交易，卖出行已实现盈亏由 M7 卖出轨迹产出）；
 * - 校准历史按 M8 `reconciliation_records` 展示（记录值固定，不随行情重解析）；
 * - 校准入口可见性按 M4 [ReconciliationService.classifySources]（仅单一交易所 API 来源可见）。
 */
@Suppress("LongParameterList") // 依赖注入袋（会话/三类仓库/目录/快照/设置/装配器/现价兜底/白名单/时钟）固有
class DefaultPortfolioService(
    private val sessions: ActiveSessionStore,
    private val transactions: LedgerTransactionRepository,
    private val funds: FundFlowRepository,
    private val recons: ReconciliationRepository,
    private val catalog: CoinCatalog,
    private val snapshots: PriceSnapshotRepository,
    private val settings: SettingsRepository,
    private val assembler: LedgerEventAssembler,
    /** 现价兜底链（快照缺失时的 USD 锚定/最近可得；与资金页 fundsOverview 同源，见类 KDoc）。 */
    private val eventBuilder: TransactionEventBuilder,
    /** 稳定币白名单运行期读取（M10 T10.1 设置扩展「可用现金」口径；默认 = 引擎常量，行为不变）。 */
    private val cashCoinIds: () -> Set<String> = { PortfolioCalculator.DEFAULT_CASH_COIN_IDS },
    /** 当前时刻读取（24h 配对基准；测试注入固定时钟，先例 = DefaultMarketRefreshService.clock）。 */
    private val clock: () -> Instant = Instant::now,
) : PortfolioService {

    private val rowSource = LedgerRowsSource(transactions, funds, recons)
    private val reconciliation = ReconciliationService()

    override suspend fun snapshot(): PortfolioSnapshot = compute().snapshot

    /**
     * 币种详情：`symbol`/`name` 取 coins 目录（未收录 → 回落 cgId 最小结构），`row` 复用 `snapshot()`
     * 的同一次计算产物（不二次重放），`calibrations` 取 M8 校准记录（时间降序）。
     */
    @Suppress("UnusedParameter") // 契约参数：交易列表由 UI 层直取 M7 服务（CoinDetail 无 transactions 字段）
    override suspend fun coinDetail(cgId: String, filter: TxFilter): CoinDetail {
        val accountId = sessions.requireActive().account.id
        val computed = compute()
        val coin = catalog.getByCgId(cgId)
            ?: return CoinDetail(cgId = cgId, symbol = cgId, name = cgId, row = null, calibrations = emptyList())
        return CoinDetail(
            cgId = coin.cgId,
            symbol = coin.symbol,
            name = coin.name,
            row = computed.rowsByCgId[coin.cgId],
            calibrations = calibrations(accountId, coin.id),
        )
    }

    // ---- 内部：一次计算（快照与详情共用） ----

    /** 一次计算产物：快照 + 行索引（详情页按 cg_id 复用，保证与列表同一口径）。 */
    private data class Computed(val snapshot: PortfolioSnapshot, val rowsByCgId: Map<String, PortfolioRow>)

    /**
     * 现价（现价 + 来源 + 快照时刻）。
     * [source]/[at] 为 null = 该价来自兜底链（USD 锚定/最近可得估算），没有快照行可归属：
     * 24h 的「混合数据源」判定按无来源处理，priceAsOf 不计入。
     */
    private data class Quote(val price: BigDecimal, val source: PriceSource?, val at: Instant?)

    /** 行情装配产物（目录行 / 现价 / 现价表：一次取数供指标、行、24h 三处共用）。 */
    private data class MarketData(
        val coins: Map<String, CatalogCoin>,
        val quotes: Map<String, Quote>,
        val prices: Map<String, BigDecimal>,
    )

    private suspend fun compute(): Computed {
        val accountId = sessions.requireActive().account.id
        val fiat = baseFiat()
        val rows = rowSource.load(accountId)
        val build = assembler.build(rows.tx, rows.funds, rows.recons, fiat)
        val outcome = ReplayEngine.replay(build.events, NegativePolicy.LENIENT)
        val market = loadMarket(outcome, fiat)
        val metrics = PortfolioCalculator(cashCoinIds = cashCoinIds()).compute(outcome, market.prices)
        val portfolioRows = buildRows(metrics, market)
        val snapshot = PortfolioSnapshot(
            fiat = fiat,
            metrics = metrics,
            rows = portfolioRows,
            change24h = compute24h(metrics, market, fiat),
            priceAsOf = market.quotes.values.mapNotNull { it.at }.maxOrNull(),
            estimated = outcome.estimated,
            anomalousCoins = outcome.anomalousCoins.sorted(),
        )
        return Computed(snapshot, portfolioRows.associateBy { it.cgId })
    }

    /**
     * 目录行 + 现价（cg_id 键；目录未收录 = 该币行不可得）。现价取数：快照优先，快照缺失时回落
     * 现价兜底链（USD 锚定 1:1 / 最近可得估算）——口径与资金页 fundsOverview 一致，见类 KDoc。
     */
    private suspend fun loadMarket(outcome: ReplayOutcome, fiat: String): MarketData {
        val coins = LinkedHashMap<String, CatalogCoin>()
        val quotes = LinkedHashMap<String, Quote>()
        for (cgId in outcome.holdings.keys) {
            val coin = catalog.getByCgId(cgId) ?: continue // 仅目录命中项出行（契约口径）
            coins[cgId] = coin
            val row = snapshots.latest(coin.id.toInt(), fiat)
            if (row != null) {
                quotes[cgId] = Quote(row.price, row.source, row.recordedAt)
            } else {
                eventBuilder.currentPrice(coin.id, fiat)?.let { quotes[cgId] = Quote(it, null, null) }
            }
        }
        return MarketData(coins, quotes, quotes.mapValues { it.value.price })
    }

    private fun buildRows(metrics: PortfolioMetrics, market: MarketData): List<PortfolioRow> =
        metrics.holdings.values
            .mapNotNull { holding ->
                market.coins[holding.coinId]?.let { coin ->
                    toRow(holding, coin, market.quotes[holding.coinId], metrics.netValueFiat)
                }
            }
            .sortedWith(ROW_ORDER)

    private fun toRow(
        holding: HoldingMetrics,
        coin: CatalogCoin,
        quote: Quote?,
        netValue: BigDecimal,
    ): PortfolioRow = PortfolioRow(
        cgId = coin.cgId,
        symbol = coin.symbol,
        name = coin.name,
        quantity = holding.quantity,
        avgCostFiat = holding.avgCostFiat,
        priceFiat = quote?.price,
        marketValueFiat = holding.marketValue,
        floatPnlFiat = holding.floatPnlFiat,
        floatPnlPercent = holding.floatPnlPercent,
        realizedPnlFiat = holding.realizedPnlFiat,
        // 占比 = 市值/净值（引擎口径 LedgerMath.percentOf）；缺价（无市值）或净值 ≤ 0 → null（"--"）
        sharePercent = holding.marketValue
            ?.takeIf { netValue.signum() > 0 }
            ?.let { LedgerMath.percentOf(it, netValue) },
        priced = holding.priced,
        anomalous = holding.anomalous,
        estimated = holding.estimated,
        sources = holding.sources,
        sourceClassification = reconciliation.classifySources(holding.sources),
    )

    /**
     * 24h 盈亏输入（固定数量回算法）：只喂持仓 > 0 的币；缺现价或缺 24h 前价的币由 [TwentyFourHour]
     * 内部折算覆盖数（计入 total、不计入 covered），异源配对经 mixed 标注。兜底现价无来源信息 →
     * sourceNow 记 null（不计混合源），且配对只认快照行（priceAgo 记 null）。
     */
    private suspend fun compute24h(
        metrics: PortfolioMetrics,
        market: MarketData,
        fiat: String,
    ): TwentyFourHour.Result {
        val pairBucket = PriceResolution.resolutionKey(PriceResolution.pairInstant(clock()))
        val inputs = ArrayList<TwentyFourHour.CoinInput>()
        for (holding in metrics.holdings.values.filter { it.quantity.signum() > 0 }) {
            val coin = market.coins[holding.coinId] ?: continue
            val now = market.quotes[holding.coinId]
            val sourceNow = now?.source
            val ago = snapshots.atBucket(coin.id.toInt(), fiat, pairBucket)
            val sourceAgo = ago?.source
            inputs += TwentyFourHour.CoinInput(
                coinId = holding.coinId,
                quantity = holding.quantity,
                priceNow = now?.price,
                sourceNow = sourceNow,
                priceAgo = ago?.price,
                sourceAgo = sourceAgo,
                mixed = sourceNow != null && sourceAgo != null && sourceNow != sourceAgo,
            )
        }
        return TwentyFourHour.compute(inputs)
    }

    /** 校准历史（M8 reconciliation_records 展示口径；记录值固定，exchange/notes 行上无值即 null）。 */
    private suspend fun calibrations(accountId: Int, coinRowId: Long): List<CalibrationRecord> =
        recons.listForCoin(accountId, coinRowId)
            .map { row ->
                CalibrationRecord(
                    id = row.id,
                    at = row.createdAt,
                    exchangeQuantity = row.exchangeQuantity,
                    delta = row.delta,
                    deltaFiat = row.baseAmount,
                    exchange = row.exchange.takeIf { it.isNotBlank() },
                    notes = null,
                )
            }
            .sortedByDescending { it.at }

    private fun baseFiat(): String =
        settings.getGlobal(DefaultTransactionLedgerService.SETTING_FIAT)?.takeIf { it.isNotBlank() } ?: DEFAULT_FIAT

    private fun ActiveSessionStore.requireActive(): ActiveSession = get() ?: error("未登录：请先登录账户")

    companion object {
        /** 基础法币缺省（settings 全局键 fiat 缺失/空串时；与 M7/M8 同源）。 */
        const val DEFAULT_FIAT = "USD"

        /**
         * 行序（interaction.md §3-1）：有现价的币在前按市值降序，缺价币置末按名称升序，
         * 末位以 cg_id 决胜（重放结果来自 HashMap，保证输出完全确定）。
         */
        private val ROW_ORDER: Comparator<PortfolioRow> =
            compareByDescending<PortfolioRow> { it.marketValueFiat != null }
                .thenByDescending { it.marketValueFiat ?: BigDecimal.ZERO }
                .thenBy { it.name }
                .thenBy { it.cgId }
    }
}
