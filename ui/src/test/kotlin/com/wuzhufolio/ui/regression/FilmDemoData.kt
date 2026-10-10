package com.wuzhufolio.ui.regression

import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinStatus
import com.wuzhufolio.domain.demo.DemoPortfolio
import com.wuzhufolio.domain.engine.FundEvent
import com.wuzhufolio.domain.engine.LedgerEvent
import com.wuzhufolio.domain.engine.LedgerMath
import com.wuzhufolio.domain.engine.NegativePolicy
import com.wuzhufolio.domain.engine.PortfolioCalculator
import com.wuzhufolio.domain.engine.PortfolioMetrics
import com.wuzhufolio.domain.engine.ReconciliationService
import com.wuzhufolio.domain.engine.RecordSource
import com.wuzhufolio.domain.engine.ReplayEngine
import com.wuzhufolio.domain.engine.ReplayOutcome
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.engine.TradeEvent
import com.wuzhufolio.domain.ledger.TransactionRow
import com.wuzhufolio.domain.market.TwentyFourHour
import com.wuzhufolio.domain.market.WatchQuoteRow
import com.wuzhufolio.domain.portfolio.PortfolioRow
import java.math.BigDecimal
import java.time.Instant

/**
 * **P7 影片素材的演示数据真源**（全面评审开放项 #8 · 2026-10-11）。
 *
 * 目的：影片素材（仓库内 `docs/release/promo/assets/ui/` 下的 PNG，由 [VisualRegression.captureFilmAsset] 采集）此前由
 * **测试内手写的黄金数值夹具**驱动（如 `PortfolioPagesUiTest.filmRows()` + 手写 `PortfolioMetrics`）——
 * 宣传片里看到的界面与「用户点『加载示例数据』后真实看到的界面」是两套数。本助手把素材改为由**产品自身的
 * 示例数据集** [DemoPortfolio]（B 迭代 B1 · `D46`）经**既有 domain 引擎**推导：
 *
 * 1. **指标**（[metrics]：净值/可用现金/投入本金/成本/已实现/未实现/ROI）= `ReplayEngine` 全量重放
 *    + `PortfolioCalculator`（与运行期 `DefaultPortfolioService` 同一口径，本助手不另算一套公式）；
 * 2. **币种行**（[rows]）= 引擎持仓 + 目录身份拼装，行序同 `DefaultPortfolioService.ROW_ORDER`
 *    （市值降序 → 缺价置末 → 名称 → cg_id，`interaction.md §3-1`）；
 * 3. **交易行**（[transactions]）= 数据集 4 笔交易；卖出行已实现盈亏由引擎逐笔推导（[realizedByEvent]）；
 * 4. **自选清单**（[watchCoins]）= 默认种子（现金白名单默认币，D28/D41 与 `MarketWatchService` 同源）
 *    + D35「成交币自动入自选」两腿。
 *
 * ## 示例价格口径（**非行情数据**）
 *
 * 素材用价 = **数据集自身最后成交价**（每币取 `daysAgo` 最小的那笔成交价；数据集未成交的计价腿按
 * `PortfolioCalculator.ANCHORED_COIN_IDS` 的 **1:1 锚定**口径取 1）。**示例价格 = 数据集最后成交价，
 * 非行情数据，故影片画面不随行情变化**——素材因此可确定性重渲（不依赖网络、不随行情快照漂移），
 * 代价是素材画面**不是**「加载示例数据后尚未刷新行情」的逐字状态（那一状态里 BTC/ETH/SOL 无价、
 * 净值只剩 USDT）：这是影片的**演示口径**，不是产品价链（产品价链见 `TransactionEventBuilder.priceOf`）。
 * 连带口径：
 * - 24h 盈亏（[change24h]）——数据集不含 24h 前价 ⇒ 交引擎空输入 ⇒ 覆盖 0/M、金额与百分比为 null
 *   （界面显示 "--"）；**不臆造** 24h 前价（那等于宣称行情覆盖）；
 * - 行情页报价（[watchQuotes]）——示例价没有行情快照行 ⇒ 数据源/更新两列无值（「无行情」/「--」），
 *   与运行期「无快照行即无来源」口径一致（`DefaultPortfolioService.quoteOf` 的兜底价同样 source=null）。
 *
 * ## 边界（不得违反）
 *
 * - 本文件内**不出现手写黄金数值**：金额/指标/已实现盈亏一律由引擎算出或自 [DemoPortfolio] 读取；
 *   唯一固化的表是币种**身份**（[IDENTITY]：ticker → CoinGecko id/展示名），它不参与任何金额推导，
 *   只为「数据集只携带 ticker、而 ui 测试侧不依赖 data 模块目录」补上身份（与 `DemoPortfolioTest`
 *   同一映射同源）；示例币种增删时 [coins] 会 fail-fast 提示同步。
 * - **只改测试侧**：素材与影片必须在 **0.3.0 发布准备**时**一并**重生成（仓库内 `assets/ui/` 下的 PNG
 *   仍是 0.2.0 版、与已发布的 0.2.0 影片一一对应，**不得单独替换**）——见
 *   `docs/release/promo/README.md`「素材数据来源」节。
 */
internal object FilmDemoData {

    /** 影片素材的基础法币（= 运行期 settings 缺省 `USD`；示例数据计价腿为 USDT，USD 锚定 1:1）。 */
    const val FIAT: String = "USD"

    /** 交易行来源（= data 层 `TransactionEventBuilder.SOURCE_MANUAL`：示例数据经 `saveTransaction` 落库）。 */
    private const val SOURCE_MANUAL: String = "Manual"

    /** 折算状态（= data 层 `DefaultTransactionLedgerService.PRICE_OK`；示例价齐备，无「估算中」行）。 */
    private const val PRICE_OK: String = "OK"

    /**
     * 示例币种的目录身份（ticker → CoinGecko id / 展示名）。
     *
     * 数据集本身只携带 ticker（落库时由 coins 目录解析并冻结 cg_id），而 ui 测试侧只依赖 `:domain`
     * （见 `ui/build.gradle.kts`）⇒ 无法调用运行期目录实现；此处固化示例数据在真实加载路径下解析到的
     * 目录项（与 `DemoPortfolioTest.cgId` 同一映射）。**这不是黄金数值**：不参与金额/指标推导。
     */
    private val IDENTITY: Map<String, CoinIdentity> = mapOf(
        "USDT" to CoinIdentity(id = 1L, cgId = "tether", name = "Tether"),
        "BTC" to CoinIdentity(id = 2L, cgId = "bitcoin", name = "Bitcoin"),
        "ETH" to CoinIdentity(id = 3L, cgId = "ethereum", name = "Ethereum"),
        "SOL" to CoinIdentity(id = 4L, cgId = "solana", name = "Solana"),
    )

    /** 数据集涉及币种（顺序 = [DemoPortfolio.coinSymbols]：增资币在前，再按成交腿首现序）。 */
    val coins: List<CatalogCoin> = run {
        val unknown = DemoPortfolio.coinSymbols.filterNot { it in IDENTITY }
        check(unknown.isEmpty()) { "示例数据集新增了币种 $unknown，请在 FilmDemoData.IDENTITY 补其 cg_id/名称" }
        DemoPortfolio.coinSymbols.map { coin(it) }
    }

    /** 示例价格表（cg_id → 基础法币价）：数据集最后成交价；无成交的计价腿按 1:1 锚定取 1。 */
    val prices: Map<String, BigDecimal> = run {
        val lastTrade = DemoPortfolio.transactions
            .groupBy { it.baseSymbol }
            .mapValues { (_, txs) -> txs.minByOrNull { it.daysAgo }!!.price }
        DemoPortfolio.coinSymbols.associate { symbol ->
            val cgId = coin(symbol).cgId
            cgId to (lastTrade[symbol] ?: anchoredPrice(cgId))
        }
    }

    /**
     * 自选清单（示例币种）：账户未写入自选时的**默认种子**（现金白名单默认币，D28；`MarketWatchService`
     * KDoc「默认种子 = 仅 USDT，与现金白名单默认值对齐」）在前，其后为 D35「成交币自动入自选」的
     * 两腿首现序（示例数据经 `saveTransaction` 落库 ⇒ 与运行期同路径；重复币幂等跳过）。
     */
    val watchCoins: List<CatalogCoin> = run {
        val seed = DemoPortfolio.coinSymbols.filter { coin(it).cgId in PortfolioCalculator.DEFAULT_CASH_COIN_IDS }
        val traded = DemoPortfolio.transactions.flatMap { listOf(it.baseSymbol, it.quoteSymbol) }
        (seed + traded).distinct().map { coin(it) }
    }

    /** 币种身份（按 ticker；未知 ticker 抛错，避免素材静默少一行）。 */
    fun coin(symbol: String): CatalogCoin {
        val identity = IDENTITY[symbol] ?: error("示例币种 $symbol 不在 FilmDemoData.IDENTITY 中")
        return CatalogCoin(
            id = identity.id,
            cgId = identity.cgId,
            cmcId = null,
            symbol = symbol,
            name = identity.name,
            status = CoinStatus.ACTIVE,
        )
    }

    /** 数据集 → 引擎事件（与运行期事件构造层同口径：折算价 = 示例价表，故 USDT 腿恒 1）。 */
    fun events(now: Instant): List<LedgerEvent> {
        val funds = DemoPortfolio.fundInputs(now).mapIndexed { i, f ->
            FundEvent(
                id = FUND_EVENT_PREFIX + i,
                at = f.time,
                coin = coin(f.coinSymbol).cgId,
                kind = f.kind,
                quantity = f.quantity,
                fiatValue = f.quantity.multiply(priceOf(f.coinSymbol)),
                // 同刻稳定排序键：示例数据时间互不相同，seq 仅作决胜（口径同 DemoPortfolioTest）
                seq = i.toLong(),
            )
        }
        val trades = DemoPortfolio.transactionInputs(now).mapIndexed { i, t ->
            TradeEvent(
                id = TRADE_EVENT_PREFIX + i,
                at = t.time,
                baseCoin = coin(t.baseSymbol).cgId,
                quoteCoin = coin(t.quoteSymbol).cgId,
                side = t.side,
                price = t.price,
                quantity = t.quantity,
                fee = t.fee,
                feeCoin = coin(t.feeCoinSymbol).cgId,
                legFiat = t.quantity.multiply(t.price).multiply(priceOf(t.quoteSymbol)),
                feeFiat = t.fee.multiply(priceOf(t.feeCoinSymbol)),
                source = RecordSource.Manual,
                seq = (100 + i).toLong(),
            )
        }
        return funds + trades
    }

    /**
     * 全量重放（STRICT）：示例数据集自洽（`DemoPortfolioTest` 同口径守护「无负持仓边界」），
     * 故 STRICT 与运行期读路径的 LENIENT 对自洽账本逐字等价；用 STRICT 可让「数据集被改坏」在
     * 素材采集时立刻暴露，而不是把 ⚠️ 横幅渲进宣传片。
     */
    fun outcome(now: Instant): ReplayOutcome = ReplayEngine.replay(events(now), NegativePolicy.STRICT)

    /** 账户级指标（净值/可用现金/投入本金/成本/已实现/未实现/ROI）= 引擎 + 计算器。 */
    fun metrics(now: Instant): PortfolioMetrics =
        PortfolioCalculator().compute(outcome(now), prices)

    /** 币种行（口径与行序同运行期 `DefaultPortfolioService`：市值降序 → 缺价置末 → 名称 → cg_id）。 */
    fun rows(now: Instant): List<PortfolioRow> {
        val outcome = outcome(now)
        val metrics = PortfolioCalculator().compute(outcome, prices)
        val reconciliation = ReconciliationService()
        return metrics.holdings.values
            .map { holding ->
                val row = coins.first { it.cgId == holding.coinId }
                PortfolioRow(
                    cgId = row.cgId,
                    symbol = row.symbol,
                    name = row.name,
                    quantity = holding.quantity,
                    avgCostFiat = holding.avgCostFiat,
                    priceFiat = prices[holding.coinId],
                    marketValueFiat = holding.marketValue,
                    floatPnlFiat = holding.floatPnlFiat,
                    floatPnlPercent = holding.floatPnlPercent,
                    realizedPnlFiat = holding.realizedPnlFiat,
                    sharePercent = holding.marketValue
                        ?.takeIf { metrics.netValueFiat.signum() > 0 }
                        ?.let { LedgerMath.percentOf(it, metrics.netValueFiat) },
                    priced = holding.priced,
                    anomalous = holding.anomalous,
                    costReliable = outcome.holdings[holding.coinId]?.hadNegativeBoundary?.not() ?: true,
                    estimated = holding.estimated,
                    sources = holding.sources,
                    sourceClassification = reconciliation.classifySources(holding.sources),
                )
            }
            .sortedWith(ROW_ORDER)
    }

    /**
     * 24h 盈亏：数据集不含 24h 前价（示例价非行情数据）⇒ 交引擎 [TwentyFourHour.compute] 空输入
     * ⇒ 覆盖 0/M、金额与百分比为 null（界面 "--"）。
     */
    fun change24h(): TwentyFourHour.Result = TwentyFourHour.compute(emptyList())

    /**
     * 交易行（数据集 4 笔；卖出行已实现盈亏由引擎逐笔推导，见 [realizedByEvent]）。
     *
     * 行序 = **交易时间降序**（与运行期列表默认展示序同口径：`LedgerTransactionRepository.listFiltered`
     * = 全量升序 `asReversed()`，`TxFilter` KDoc「默认交易时间降序」）；`id` 仍为落库序（数据集首笔 = 1）。
     */
    fun transactions(now: Instant): List<TransactionRow> {
        val realized = realizedByEvent(events(now))
        return DemoPortfolio.transactionInputs(now).mapIndexed { i, t ->
            TransactionRow(
                id = (i + 1).toLong(),
                exchange = t.exchange,
                exchangeOrderId = null,
                pair = t.baseSymbol + "/" + t.quoteSymbol,
                baseSymbol = t.baseSymbol,
                quoteSymbol = t.quoteSymbol,
                side = t.side,
                price = t.price,
                quantity = t.quantity,
                fee = t.fee,
                feeCurrency = t.feeCoinSymbol,
                total = t.price.multiply(t.quantity),
                time = t.time,
                notes = t.notes,
                source = SOURCE_MANUAL,
                priceStatus = PRICE_OK,
                estimated = false,
                realizedPnlFiat = if (t.side == Side.SELL) realized[TRADE_EVENT_PREFIX + i] else null,
            )
        }.sortedWith(compareByDescending<TransactionRow> { it.time }.thenByDescending { it.id })
    }

    /**
     * 行情页报价行（自选清单 + 示例价）：`source`/`at` 均 null——示例价不是行情快照，没有数据源与
     * 行情时刻可归属（界面数据源列显示「无行情」、更新列 "--"）。
     */
    fun watchQuotes(fiat: String = FIAT): List<WatchQuoteRow> = watchCoins.map { coin ->
        WatchQuoteRow(
            cgId = coin.cgId,
            symbol = coin.symbol,
            name = coin.name,
            fiat = fiat,
            price = prices[coin.cgId],
            source = null,
            at = null,
        )
    }

    // ---- 内部 ----

    private fun priceOf(symbol: String): BigDecimal = prices.getValue(coin(symbol).cgId)

    private fun anchoredPrice(cgId: String): BigDecimal = if (cgId in PortfolioCalculator.ANCHORED_COIN_IDS) {
        BigDecimal.ONE
    } else {
        error("示例币种 $cgId 既无成交价、也不在 1:1 锚定集合（${PortfolioCalculator.ANCHORED_COIN_IDS}）中，无法定价")
    }

    /**
     * 每笔卖出的已实现盈亏（展示口径，与运行期 M7 `SellRealizedTracer` 同公式，但**由引擎产出**）：
     * 对每笔卖出取**其之前**的事件前缀做一次 STRICT 重放，用该币**当时的平均成本**
     * （[com.wuzhufolio.domain.engine.CoinHolding.avgCostFiat]，引擎公式 = 成本/数量、12 位 HALF_UP），
     * 再按引擎卖出公式 已实现 =（腿总值 − 折算手续费）− 数量 × 当时均价 计算。
     * 逐笔值之和 = 引擎币种级 `realizedPnlFiat`（本数据集仅 1 笔卖出，二者相等）。
     */
    private fun realizedByEvent(events: List<LedgerEvent>): Map<String, BigDecimal> {
        val ordered = events.sortedWith(compareBy({ it.at }, { it.seq }))
        val realized = LinkedHashMap<String, BigDecimal>()
        ordered.forEachIndexed { index, event ->
            if (event !is TradeEvent || event.side != Side.SELL) return@forEachIndexed
            val before = ReplayEngine.replay(ordered.take(index), NegativePolicy.STRICT)
            val avg = before.holdings[event.baseCoin]?.avgCostFiat ?: return@forEachIndexed
            realized[event.id] = (event.legFiat - event.feeFiat) - event.quantity.multiply(avg)
        }
        return realized
    }

    private const val FUND_EVENT_PREFIX = "film-demo-fund-"
    private const val TRADE_EVENT_PREFIX = "film-demo-tx-"

    /** 币种身份（ticker → coins 行 id / CoinGecko id / 展示名）。 */
    private data class CoinIdentity(val id: Long, val cgId: String, val name: String)

    /** 行序（= `DefaultPortfolioService.ROW_ORDER`，`interaction.md §3-1`）。 */
    private val ROW_ORDER: Comparator<PortfolioRow> =
        compareByDescending<PortfolioRow> { it.marketValueFiat != null }
            .thenByDescending { it.marketValueFiat ?: BigDecimal.ZERO }
            .thenBy { it.name }
            .thenBy { it.cgId }
}
