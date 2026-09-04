package com.wuzhufolio.domain.engine

import java.math.BigDecimal

/**
 * 持仓校准服务（T4.4，architecture §2.3；PRD 全局说明「持仓校准规则」/ 故事 4.1-5 / 名词解释「持仓校准」）。
 *
 * 职责：
 * 1. **单一数据来源判定**（校准适用条件）：币种全部记录来自同一交易所 API 同步、无手动录入/CSV/其他
 *    交易所记录时入口可见；多来源时 UI 隐藏入口并提示（文案映射落 M8）。来源集合来自重放结果的交易
 *    事件（[ReplayOutcome.holdings] 的 [CoinHolding.sources]，仅交易事件计入——资金操作与锚点不计，
 *    裁决登记模块记录 M4.md §5）；
 * 2. **校准差额规划**：delta = 交易所余额 − 本地持仓；差额账务按符号分派（正→系统增资、负→系统撤资），
 *    折算值 = |delta| × 校准时市价（基础法币）；**执行前提：校准时市价可得**（快照或行情 API；
 *    行情完全不可用抛 [CalibrationPriceUnavailableException] 并提示）；
 * 3. 规划产物可直接构造 [AnchorEvent] 参与全量重放——锚点语义（强制对齐、不产生盈亏、恒等式
 *    「总收益 = 净值 + 撤资 − 增资」保持）由 [ReplayEngine]/[PortfolioCalculator] 保证（黄金用例 8）。
 */
class ReconciliationService {

    /** 币种来源判定（交易事件来源集合）。 */
    fun classifySources(sources: Set<RecordSource>): SourceClassification {
        val only = sources.singleOrNull()
        return when {
            sources.isEmpty() -> SourceClassification.NoRecords
            only is RecordSource.Exchange ->
                SourceClassification.SingleExchange(only.exchangeName)
            else -> SourceClassification.Multi(
                sources.sortedWith(SOURCE_ORDER).map { it.describe },
            )
        }
    }

    /**
     * 生成校准规划。
     * @param localQuantity 校准前本地持仓（= 对账时刻全量重放推导值）
     * @param exchangeQuantity 交易所余额（API 数据源）
     * @param marketFiatPrice 校准时市价（基础法币/1 币）；null = 行情不可用 → 抛 [CalibrationPriceUnavailableException]
     */
    fun plan(
        coinId: String,
        localQuantity: BigDecimal,
        exchangeQuantity: BigDecimal,
        marketFiatPrice: BigDecimal?,
    ): CalibrationPlan {
        val delta = exchangeQuantity - localQuantity
        if (delta.signum() == 0) {
            return CalibrationPlan(
                coinId = coinId,
                delta = BigDecimal.ZERO,
                deltaFiat = BigDecimal.ZERO,
                direction = null,
            )
        }
        val price = marketFiatPrice ?: throw CalibrationPriceUnavailableException(coinId)
        require(price.signum() > 0) { "market price must be positive" }
        val deltaFiat = delta.abs().multiply(price)
        return CalibrationPlan(
            coinId = coinId,
            delta = delta,
            deltaFiat = deltaFiat,
            direction = if (delta.signum() > 0) FlowKind.DEPOSIT else FlowKind.WITHDRAWAL,
        )
    }

    companion object {
        private val SOURCE_ORDER: Comparator<RecordSource> =
            compareBy<RecordSource> { it.describe }
    }
}

/** 币种来源分类（校准入口可见性输入；多来源 → 入口隐藏并提示，PRD 故事 4.1-5）。 */
sealed interface SourceClassification {
    /** 无任何交易记录（无可校准依据）。 */
    data object NoRecords : SourceClassification

    /** 单一交易所 API 来源（可校准）。 */
    data class SingleExchange(val exchangeName: String) : SourceClassification

    /** 多来源（手动/CSV/多交易所/API+手动 混合；列出来源描述供提示）。 */
    data class Multi(val sources: List<String>) : SourceClassification
}

/** 校准差额规划（delta = 交易所余额 − 本地持仓；deltaFiat = |delta|×校准时市价）。 */
data class CalibrationPlan(
    val coinId: String,
    val delta: BigDecimal,
    val deltaFiat: BigDecimal,
    /** delta 符号方向（正差额视同增资、负差额视同撤资；delta = 0 时为 null——无差额不生成锚点）。 */
    val direction: FlowKind?,
)
