package com.wuzhufolio.data.ledger

import com.wuzhufolio.data.market.PriceSnapshotRepository
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.engine.FundEvent
import com.wuzhufolio.domain.engine.LedgerEvent
import com.wuzhufolio.domain.engine.PortfolioCalculator
import com.wuzhufolio.domain.engine.RecordSource
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.engine.TradeEvent
import java.math.BigDecimal
import java.time.Instant

/**
 * 事件构造层（M7 · api-contracts §3 M4 补录「DB 行 -> LedgerEvent」交易半边）。
 *
 * 职责（模块记录 M4 §5-1/5-2）：
 * - 币 FK -> cg_id 解析（coins 目录 join，内存缓存）；
 * - 折算价解析（记账单位 = 基础法币）：px(coin, fiat, at) 取数顺序 =
 *   ① 本地快照 nearestBefore（记录时口径）→ ② USD 锚定稳定币 × USD = 1:1（法币归一化锚定语义，
 *   非估算）→ ③ 快照 nearestAfter（「暂按最近可得价估算」，PRD N3/§2.4，标记 estimated）→ ④ 无任何价 =
 *   行暂不入重放（无法折算主腿），行标记待定价；行情到达后重建事件自动消除（黄金用例 9）；
 * - 手续费折算价缺失（fee>0 且 px(feeCoin) 无）→ feeFiat 暂按 0 计入、行标记 estimated（不丢交易主腿）；
 * - 手续费币种解析顺序：与基础/计价符号相等 -> 对应角色；否则目录 symbol 唯一命中 -> 第三币种
 *  （联动扣减，黄金用例 5）；歧义/未命中 -> 按 quote 角色兜底 + estimated（口径登记模块记录 M7 §5）。
 *
 * 引擎为纯确定性重放，本层不引入任何数值语义变更（M4 已通过模块不动）。
 */
class TransactionEventBuilder(
    private val catalog: CoinCatalog,
    private val snapshots: PriceSnapshotRepository,
) {

    /** 事件构造结果：可入重放的事件 + 待定价行（展示「估算中」）+ 无法折算暂排除的行。 */
    data class BuildResult(
        val events: List<TradeEvent>,
        /** estimated = true 的行 id（任一折算价走了估算路径）。 */
        val estimatedRowIds: Set<Long>,
        /** 目录缺失/退化对等无法构造有效事件的行 id（折算价缺失不再排除——见名义折算口径）。 */
        val excludedRowIds: Set<Long>,
    )

    /** 行 -> 事件（[rows] 须为账户全量行；时间升序由仓库保证，seq = 行 id 满足引擎稳定排序契约）。 */
    suspend fun build(rows: List<LedgerTxRow>, baseFiat: String): BuildResult {
        val coinCache = HashMap<Long, CatalogCoin?>()
        suspend fun coinOf(id: Long): CatalogCoin? = coinCache.getOrPut(id) { catalog.getById(id) }

        val events = ArrayList<TradeEvent>(rows.size)
        val estimated = HashSet<Long>()
        val excluded = HashSet<Long>()
        for (row in rows) {
            val base = coinOf(row.baseCoinId)
            val quote = coinOf(row.quoteCoinId)
            if (base == null || quote == null || base.cgId == quote.cgId) {
                excluded += row.id // 目录缺失/退化对：无法构造有效事件（同步链路已在写入前消歧，防御性）
                estimated += row.id
                continue
            }
            val feeCoin = resolveFeeCoinSymbol(base, quote, row.feeCurrency)
            val draft = TradeDraft(
                id = row.uuid,
                at = row.time,
                seq = row.id,
                baseCoin = base,
                quoteCoin = quote,
                side = row.side,
                price = row.price,
                quantity = row.quantity,
                fee = row.fee,
                feeCoin = feeCoin,
                source = sourceOf(row),
            )
            // 折算价缺失 -> 名义折算（quote≈1）参与重放 + 估算标记：保证持仓数量链完整
            //（若排除该行，其后卖出会落入「无成本基数」异常区段，导致已实现盈亏大面积缺失——
            // 2026-09-07 GUI 走查修复轮，登记模块记录 M7 §8）
            val event = buildEvent(draft, baseFiat) ?: nominalEvent(draft)
            if (event.estimated) estimated += row.id
            events += event
        }
        return BuildResult(events, estimated, excluded)
    }

    /** 记录来源（data-model §2.5 source -> 引擎 RecordSource；展示与多来源判定输入）。 */
    fun sourceOf(row: LedgerTxRow): RecordSource = when {
        row.source.equals(SOURCE_BINANCE, ignoreCase = true) -> RecordSource.Exchange("BINANCE")
        row.source.equals(SOURCE_CSV, ignoreCase = true) -> RecordSource.Csv
        else -> RecordSource.Manual
    }

    /**
     * 手续费币种解析（口径见类 KDoc）：与基础/计价符号相等 -> 对应腿；目录 symbol 唯一命中 ->
     * 第三币种（联动扣减）；歧义/未命中 -> quote 兜底（estimated 承载）。
     */
    @Suppress("ReturnCount") // 基础/计价/目录唯一命中三态早退（兜底 quote）
    suspend fun resolveFeeCoinSymbol(base: CatalogCoin, quote: CatalogCoin, feeCurrency: String?): CatalogCoin {
        val symbol = feeCurrency?.trim()?.uppercase()
        if (symbol != null) {
            if (symbol == base.symbol.uppercase()) return base
            if (symbol == quote.symbol.uppercase()) return quote
            val candidates =
                catalog.getBySymbol(symbol).filter { it.status == com.wuzhufolio.domain.catalog.CoinStatus.ACTIVE }
            if (candidates.size == 1) return candidates.first()
        }
        return quote
    }

    /**
     * 单笔草稿 -> 引擎事件（行/CSV 导入共用的构造核）。
     * 返回 null = 主腿无法折算（无任何可得价，暂不入重放）；估算路径（最近可得价）经事件 estimated 传播。
     */
    suspend fun buildEvent(draft: TradeDraft, baseFiat: String): TradeEvent? {
        val quotePx = priceOf(draft.quoteCoin.id, baseFiat, draft.at)
        val legPrice = quotePx?.price ?: return null
        val feePx = if (draft.fee.signum() == 0) PriceHit(BigDecimal.ZERO, exact = true)
        else priceOf(draft.feeCoin.id, baseFiat, draft.at)
        val legFiat = draft.quantity.multiply(draft.price).multiply(legPrice)
        val feeFiat = draft.fee.multiply(feePx?.price ?: BigDecimal.ZERO)
        val estimated = (quotePx.exact == false) ||
            (draft.fee.signum() > 0 && feePx?.price == null) ||
            (draft.fee.signum() > 0 && feePx?.exact == false)
        return TradeEvent(
            id = draft.id,
            at = draft.at,
            baseCoin = draft.baseCoin.cgId,
            quoteCoin = draft.quoteCoin.cgId,
            side = draft.side,
            price = draft.price,
            quantity = draft.quantity,
            fee = draft.fee,
            feeCoin = draft.feeCoin.cgId,
            legFiat = legFiat,
            feeFiat = feeFiat,
            source = draft.source,
            seq = draft.seq,
            estimated = estimated,
        )
    }

    /**
     * 名义折算事件（折算价完全缺失时的兜底）：legFiat = 数量 × 成交价（quote≈1）、feeFiat = 手续费数量，
     * estimated = true。保证重放数量链完整（后续卖出仍可推导已实现盈亏，数值标「估算中」；
     * 行情回填后重建事件自动纠正——黄金用例 9 语义）。
     */
    fun nominalEvent(draft: TradeDraft): TradeEvent = TradeEvent(
        id = draft.id,
        at = draft.at,
        baseCoin = draft.baseCoin.cgId,
        quoteCoin = draft.quoteCoin.cgId,
        side = draft.side,
        price = draft.price,
        quantity = draft.quantity,
        fee = draft.fee,
        feeCoin = draft.feeCoin.cgId,
        legFiat = draft.quantity.multiply(draft.price),
        feeFiat = draft.fee,
        source = draft.source,
        seq = draft.seq,
        estimated = true,
    )

    /** 表单现价查询（费率自动计算折算输入；与事件构造同一取数顺序）。 */
    suspend fun currentPrice(coinId: Long, baseFiat: String): BigDecimal? =
        priceOf(coinId, baseFiat, Instant.now())?.price

    /**
     * 资金折算值解析（M8 资金半边）：fiatValue = 数量 × 记录时行情价，取数链与交易折算同源
     *（① 快照 nearestBefore → ② USD 锚定 1:1 → ③ 快照最近可得估算）。
     * 完全缺失时返回 fiat = 0 + estimated = true（**名义零折算**参与重放——保数量链，
     * 累计增资/撤资暂按 0 计，行情回填后重建事件自动纠正，黄金用例 9 语义；口径登记模块记录 M8 §5）。
     */
    suspend fun resolveFiatValue(
        coin: CatalogCoin,
        quantity: BigDecimal,
        baseFiat: String,
        at: Instant,
    ): FundValue {
        val px = priceOf(coin.id, baseFiat, at)
            ?: return FundValue(fiat = BigDecimal.ZERO, estimated = true)
        return FundValue(fiat = quantity.multiply(px.price), estimated = px.exact == false)
    }

    /** 资金折算解析结果（[fiat] = 折算基础法币金额；[estimated] = 估算路径）。 */
    data class FundValue(val fiat: BigDecimal, val estimated: Boolean)

    /**
     * 折算价解析（① 快照前向 → ② USD 锚定 → ③ 快照后向估算；null = 无任何可得价）。
     * nearestBefore 逐时刻查询、不加缓存：同小时桶内不同 at 的「记录时价」可能不同，
     * 以桶为键缓存会错配（本地 SQLite 点查成本可忽略，先例 = PriceSnapshotRepository 内存过滤口径）。
     */
    @Suppress("ReturnCount") // 折算价取数链早退（前向/锚定/后向）
    private suspend fun priceOf(coinId: Long, fiat: String, at: Instant): PriceHit? {
        snapshots.nearestBefore(coinId.toInt(), fiat, at)?.let { return PriceHit(it.price, exact = true) }
        if (fiat.equals("USD", ignoreCase = true) && isUsdPegged(coinId)) {
            return PriceHit(BigDecimal.ONE, exact = true)
        }
        snapshots.latest(coinId.toInt(), fiat)?.let { return PriceHit(it.price, exact = false) }
        return null
    }

    /** USD 锚定稳定币（与组合现金白名单同源：USDT/USDC/DAI/TUSD——PRD §7.2-6.1 默认白名单）。 */
    private suspend fun isUsdPegged(coinId: Long): Boolean {
        val coin = catalog.getById(coinId) ?: return false
        return coin.cgId in PortfolioCalculator.DEFAULT_CASH_COIN_IDS
    }

    private data class PriceHit(val price: BigDecimal, val exact: Boolean)

    companion object {
        const val SOURCE_BINANCE = "BINANCE API"
        const val SOURCE_CSV = "CSV"
        const val SOURCE_MANUAL = "Manual"
    }
}

/** 单笔交易草稿（事件构造核输入；DB 行与 CSV 预览行统一形态，币种已解析为 coins 行）。 */
data class TradeDraft(
    val id: String,
    val at: Instant,
    val seq: Long,
    val baseCoin: CatalogCoin,
    val quoteCoin: CatalogCoin,
    val side: Side,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val fee: BigDecimal,
    val feeCoin: CatalogCoin,
    val source: RecordSource,
)

/**
 * 卖出逐笔已实现盈亏轨迹（展示口径：交易列表/币种详情卖出行，PRD 名词解释「已实现盈亏」）。
 *
 * 引擎（ReplayEngine）只产出币种级合计；本轨迹按同一成本公式（平均成本法、比例移出、第三币种联动
 * 扣减、锚点对齐）重放事件序列并记录每笔卖出的已实现值。**单一真源仍是引擎**——合计一致性由
 * SellRealizedTracerTest 黄金用例交叉校验守护（发散即测试失败），实现差异登记模块记录 M7 §5。
 */
object SellRealizedTracer {

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    // 事件类型（资金/交易/锚点）×方向重放——与 ReplayEngine 同构的确定性轨迹，跨分支必然嵌套
    /** eventId（= 行 uuid）-> 该笔卖出已实现盈亏（基础法币；异常区段无成本基数 -> 0，与引擎口径一致）。 */
    fun realizedByEvent(events: List<LedgerEvent>): Map<String, BigDecimal> {
        data class Position(var quantity: BigDecimal, var cost: BigDecimal)

        val positions = HashMap<String, Position>()
        val realized = HashMap<String, BigDecimal>()

        fun positionOf(id: String): Position = positions.getOrPut(id) { Position(BigDecimal.ZERO, BigDecimal.ZERO) }

        fun reduce(p: Position, out: BigDecimal) {
            if (p.quantity.signum() > 0 && out.signum() > 0) {
                val removed = p.cost.multiply(out.min(p.quantity))
                    .divide(p.quantity, 12, java.math.RoundingMode.HALF_UP)
                p.cost -= removed
            }
            p.quantity -= out
        }

        for (event in events.sortedWith(compareBy({ it.at }, { it.seq }))) {
            when (event) {
                is FundEvent -> {
                    val p = positionOf(event.coin)
                    when (event.kind) {
                        com.wuzhufolio.domain.engine.FlowKind.DEPOSIT -> {
                            p.quantity += event.quantity
                            p.cost += event.fiatValue
                        }
                        com.wuzhufolio.domain.engine.FlowKind.WITHDRAWAL -> reduce(p, event.quantity)
                    }
                }
                is TradeEvent -> {
                    val base = positionOf(event.baseCoin)
                    when (event.side) {
                        Side.BUY -> {
                            val baseIn = if (event.isBaseFee) event.quantity - event.fee else event.quantity
                            base.quantity += baseIn
                            base.cost += event.legFiat + event.feeFiat
                        }
                        Side.SELL -> {
                            // 异常区段（无成本基数）不推导已实现盈亏——不记录（展示 "--"），与引擎口径一致
                            if (base.quantity.signum() > 0) {
                                val avg = base.cost.divide(base.quantity, 12, java.math.RoundingMode.HALF_UP)
                                realized[event.id] =
                                    (event.legFiat - event.feeFiat) - event.quantity.multiply(avg)
                            }
                            reduce(base, event.quantity)
                        }
                    }
                    if (event.isThirdFee && event.fee.signum() > 0) {
                        reduce(positionOf(event.feeCoin), event.fee)
                    }
                }
                is com.wuzhufolio.domain.engine.AnchorEvent -> {
                    val p = positionOf(event.coin)
                    if (event.delta.signum() > 0) {
                        p.cost += event.deltaFiat
                    } else if (p.quantity.signum() > 0) {
                        val out = event.delta.abs().min(p.quantity)
                        val removed = p.cost.multiply(out).divide(p.quantity, 12, java.math.RoundingMode.HALF_UP)
                        p.cost -= removed
                    }
                    p.quantity = event.exchangeQuantity
                }
            }
        }
        return realized
    }
}
