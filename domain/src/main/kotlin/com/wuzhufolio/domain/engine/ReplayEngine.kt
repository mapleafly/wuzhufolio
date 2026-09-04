package com.wuzhufolio.domain.engine

import java.math.BigDecimal
import java.time.Instant

/**
 * 全量重放引擎（T4.1，architecture §3.2「全量重放（核心引擎）」）。
 *
 * 输入：账户内三类事件（交易 / 资金流水 / 校准锚点）；按 (时间, seq) 升序稳定重放（同刻同 seq 以输入序
 * 决胜——调用方保证输入序确定，见 [LedgerEvent.seq]）。
 * 输出：期末持仓/平均成本/累计增资/撤资/逐笔卖出已实现盈亏/负持仓边界/来源集合/估算标记。
 * 数值基准：PRD 附录 A 黄金用例 1–9、12（模块记录 M4.md §3）。
 *
 * 校验语义（PRD 全局说明「重放校验规则」）：
 * - [NegativePolicy.STRICT]：任一事件应用后任一受影响币种持仓 < 0 → 立即抛 [NegativePositionException]
 *   （手动新增/编辑/删除记录路径，M7/M8 调用）；
 * - [NegativePolicy.LENIENT]：不拦截，违例记入 [ReplayOutcome.violations]（CSV/API/恢复导入路径）；
 * - [validateMutation]：手动操作级校验——仅当操作**新制造**了负持仓边界（相对不含本操作的重放，同一
 *   币种同一事件位点由非负转负）才拦截；宽松导入早已存在的负边界不因本操作新增则不拦截（黄金用例 6
 *   「补录增资消除负持仓」可成立；拦截文案映射落 M7/M8）。
 *
 * 成本口径（共享规范 §2 / 故事 7.1，字面公式）：
 * - 买入：持仓 += 到账数量（base 手续费时 = 数量 − 手续费），成本 += 腿总值 + 折算手续费；
 * - 卖出：持仓 −= 数量，成本按当时平均成本移出；已实现 =（腿总值 − 折算手续费）− 数量×当时平均成本；
 * - 均价 = 成本/数量 推导（不落表）；数量移出按比例移出成本（均价不变）；除法舍入 [LedgerMath.COST_SCALE]；
 * - 计价侧（quote）成本：买入按比例移出、卖出按净到账价值计入（卖出收益进入现金池成本）；
 * - 持仓异常区段（宽松导入造成的负持仓）不推导已实现盈亏（无成本基数，由 [CoinHolding.anomalous] 承载）；
 * - 第三币种手续费联动扣减对应币种持仓（黄金用例 5 口径，勘误登记见模块记录 M4.md §5）。
 */
object ReplayEngine {

    /** 严格模式重放；违例详情经 [NegativePositionException]。 */
    fun replay(events: List<LedgerEvent>, policy: NegativePolicy = NegativePolicy.STRICT): ReplayOutcome {
        val coins = HashMap<String, CoinState>()
        val acc = Accumulator()
        for (event in events.sortedWith(EVENT_ORDER)) {
            val touched = apply(event, coins, acc)
            checkBoundaries(event, touched, policy, coins, acc)
        }
        val holdings = coins.mapValues { (id, state) -> state.toHolding(id) }.toMap()
        return ReplayOutcome(
            holdings = holdings,
            cumulativeDepositsFiat = acc.deposits,
            cumulativeWithdrawalsFiat = acc.withdrawals,
            realizedPnlFiat = coins.values.fold(BigDecimal.ZERO) { sum, state -> sum + state.realized },
            violations = acc.violations,
            estimated = coins.values.any { it.estimated },
        )
    }

    /**
     * 手动操作（新增/编辑/删除交易或资金记录）负持仓校验。
     * [withOp] = 应用操作后的事件全集；[withoutOp] = 不含本操作（或含旧版本）的事件全集。
     * 两遍宽松重放后对比负边界位点（币种 + 事件时刻 + seq）：仅拦截「操作新制造」的负边界
     * （前缀不变性保证新边界只可能出现在本操作位点及之后；同一位点原有的负值不拦截——
     * 既存宽松异常不被无关手动操作阻塞，黄金用例 6）；[MutationVerdict.Blocked] 携带首违例。
     */
    fun validateMutation(withOp: List<LedgerEvent>, withoutOp: List<LedgerEvent>): MutationVerdict {
        val without = replay(withoutOp, NegativePolicy.LENIENT)
            .violations
            .map { it.tuple() }
            .toSet()
        for (violation in replay(withOp, NegativePolicy.LENIENT).violations) {
            if (violation.tuple() !in without) return MutationVerdict.Blocked(violation)
        }
        return MutationVerdict.Ok
    }

    // ---- 事件应用 ----

    private fun apply(event: LedgerEvent, coins: MutableMap<String, CoinState>, acc: Accumulator): List<String> =
        when (event) {
            is FundEvent -> applyFund(event, coins, acc)
            is TradeEvent -> applyTrade(event, coins)
            is AnchorEvent -> applyAnchor(event, coins, acc)
        }

    /** 事件应用后负持仓边界检查：STRICT 立即抛首违例；LENIENT 记录并标记。 */
    private fun checkBoundaries(
        event: LedgerEvent,
        touched: List<String>,
        policy: NegativePolicy,
        coins: MutableMap<String, CoinState>,
        acc: Accumulator,
    ) {
        for (coinId in touched) {
            val state = coins.getValue(coinId)
            if (state.quantity.signum() >= 0) continue
            if (policy == NegativePolicy.STRICT) {
                throw NegativePositionException(coinId, event.id, event.at, state.quantity)
            }
            state.hadNegative = true
            acc.violations += NegativeViolation(coinId, event.id, event.at, event.seq, state.quantity)
        }
    }

    private fun applyFund(e: FundEvent, coins: MutableMap<String, CoinState>, acc: Accumulator): List<String> {
        val state = stateOf(coins, e.coin)
        state.estimated = state.estimated || e.estimated
        when (e.kind) {
            FlowKind.DEPOSIT -> {
                state.quantity += e.quantity
                state.cost += e.fiatValue
                acc.deposits += e.fiatValue
            }
            FlowKind.WITHDRAWAL -> {
                reducePosition(state, e.quantity)
                acc.withdrawals += e.fiatValue
            }
        }
        return listOf(e.coin)
    }

    private fun applyTrade(e: TradeEvent, coins: MutableMap<String, CoinState>): List<String> {
        val base = stateOf(coins, e.baseCoin)
        val quote = stateOf(coins, e.quoteCoin)
        val feeState = if (e.isThirdFee && e.fee.signum() > 0) stateOf(coins, e.feeCoin) else null
        for (state in listOfNotNull(base, quote, feeState)) {
            state.estimated = state.estimated || e.estimated
        }
        base.sources += e.source
        quote.sources += e.source

        val legQuantity = e.quantity.multiply(e.price) // quote 单位总价（数量腿）
        when (e.side) {
            Side.BUY -> {
                val quoteOut = if (e.isQuoteFee) legQuantity + e.fee else legQuantity
                reducePosition(quote, quoteOut)
                val baseIn = if (e.isBaseFee) e.quantity - e.fee else e.quantity
                base.quantity += baseIn
                base.cost += e.legFiat + e.feeFiat
                feeState?.let { reducePosition(it, e.fee) }
            }
            Side.SELL -> {
                val baseBefore = base.quantity
                if (baseBefore.signum() > 0) {
                    val avgBefore = LedgerMath.divide(base.cost, baseBefore)
                    base.realized += (e.legFiat - e.feeFiat) - e.quantity.multiply(avgBefore)
                }
                // 异常区段（baseBefore <= 0）不推导已实现盈亏（无成本基数，由异常标记承载）
                reducePosition(base, e.quantity)
                val quoteIn = if (e.isQuoteFee) legQuantity - e.fee else legQuantity
                quote.quantity += quoteIn
                quote.cost += if (e.isQuoteFee) e.legFiat - e.feeFiat else e.legFiat
                feeState?.let { reducePosition(it, e.fee) }
            }
        }
        return listOfNotNull(e.baseCoin, e.quoteCoin, feeState?.coinId)
    }

    private fun applyAnchor(e: AnchorEvent, coins: MutableMap<String, CoinState>, acc: Accumulator): List<String> {
        val state = stateOf(coins, e.coin)
        state.estimated = state.estimated || e.estimated
        val before = state.quantity
        if (e.delta.signum() > 0) {
            // 正差额：视同系统增资，按校准时市价计入成本与累计增资（正差额增大 ROI 分母，保守口径）
            state.cost += e.deltaFiat
            acc.deposits += e.deltaFiat
        } else {
            // 负差额：视同系统撤资，按当时平均成本移出、按校准时市价计入累计撤资
            if (before.signum() > 0) {
                val out = e.delta.abs().min(before)
                state.cost -= LedgerMath.proportionalRemoval(state.cost, out, before)
            }
            acc.withdrawals += e.deltaFiat
        }
        state.quantity = e.exchangeQuantity // 锚点强制对齐：校准前历史记录编辑/删除不回滚校准效果
        return listOf(e.coin)
    }

    // ---- 内部状态 ----

    private class CoinState(val coinId: String) {
        var quantity: BigDecimal = BigDecimal.ZERO
        var cost: BigDecimal = BigDecimal.ZERO
        var realized: BigDecimal = BigDecimal.ZERO
        var estimated: Boolean = false
        var hadNegative: Boolean = false
        val sources: MutableSet<RecordSource> = LinkedHashSet()

        fun toHolding(id: String): CoinHolding = CoinHolding(
            coinId = id,
            quantity = quantity,
            costFiat = cost,
            realizedPnlFiat = realized,
            sources = sources.toSet(),
            estimated = estimated,
            hadNegativeBoundary = hadNegative,
        )
    }

    private class Accumulator {
        var deposits: BigDecimal = BigDecimal.ZERO
        var withdrawals: BigDecimal = BigDecimal.ZERO
        val violations: MutableList<NegativeViolation> = mutableListOf()
    }

    private fun stateOf(coins: MutableMap<String, CoinState>, coinId: String): CoinState =
        coins.getOrPut(coinId) { CoinState(coinId) }

    /** 持仓减少/流出：按比例移出成本（均价不变）；超出当前持仓的部分不动成本（宽松异常态不产生负成本）。 */
    private fun reducePosition(state: CoinState, outQuantity: BigDecimal) {
        if (state.quantity.signum() > 0 && outQuantity.signum() > 0) {
            val removed = LedgerMath.proportionalRemoval(
                state.cost,
                outQuantity.min(state.quantity),
                state.quantity,
            )
            state.cost -= removed
        }
        state.quantity -= outQuantity
    }
}

private val EVENT_ORDER: Comparator<LedgerEvent> =
    compareBy<LedgerEvent> { it.at }.thenBy { it.seq }

private fun NegativeViolation.tuple(): Triple<String, Instant, Long> = Triple(coinId, at, seq)
