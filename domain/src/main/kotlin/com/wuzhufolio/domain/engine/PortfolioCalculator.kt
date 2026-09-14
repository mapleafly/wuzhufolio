package com.wuzhufolio.domain.engine

import java.math.BigDecimal

/**
 * 组合指标计算器（T4.2，architecture §2.3；PRD 名词解释 / data-model §3 派生口径）。
 *
 * 输入 [ReplayOutcome]（重放结果）+ 现价表（币键 → 基础法币现价，缺价币不计入净值、单列 [missingPricedCoins]，
 * 「无行情」币种不计入总资产市值——PRD 全局说明「币种标识与主数据规则」）+ 现金类币种集合
 * （稳定币白名单；**D28 起默认仅 USDT**，其余稳定币由用户在设置中增删）。
 *
 * **负持仓口径（D29，2026-09-13 人工拍板方案 B）**：持仓数量 < 0 的币种（只可能来自导入路径的数据缺口，
 * 见 PRD「导入路径例外」）其市值**不计入净值与可用现金**（与「缺价币不计入」同源），
 * 合计值单列 [PortfolioMetrics.anomalousExcludedFiat] 供界面提示；币种行自身仍展示负市值与「持仓异常」标记。
 *
 * 派生公式（PRD 名词解释，均为基础法币）：
 * - 净值 = Σ 持仓数量×现价（缺价币不计；**负持仓币不计**，D29）；
 * - 可用现金余额 = 白名单币种持仓×现价之和（**负持仓币不计**，D29）；
 * - 投入本金（净）= 累计增资 − 累计撤资；ROI 分母 = 累计增资（撤资不抵扣，故事 6.4）；
 * - 总收益 = 净值 + 累计撤资 − 累计增资；ROI = 总收益/累计增资×100%；
 * - 累计增资 = 0 时总收益与 ROI 均返回 null（展示 "--"，故事 6.4 / T4.2 验收）；
 * - 币种级浮动盈亏 = 市值 − 总成本（占比/率按需由 UI 层由本结构派生或直接取 [CoinHolding]）。
 */
class PortfolioCalculator(
    private val cashCoinIds: Set<String> = DEFAULT_CASH_COIN_IDS,
) {

    fun compute(outcome: ReplayOutcome, currentPrices: Map<String, BigDecimal>): PortfolioMetrics {
        var netValue = BigDecimal.ZERO
        var cashValue = BigDecimal.ZERO
        var totalCost = BigDecimal.ZERO
        var anomalousExcluded = BigDecimal.ZERO
        val missingPriced = mutableListOf<String>()
        val holdings = LinkedHashMap<String, HoldingMetrics>()

        for (holding in outcome.holdings.values.sortedBy { it.coinId }) {
            val price = currentPrices[holding.coinId]
            val marketValue = price?.multiply(holding.quantity)
            val floatPnl = marketValue?.minus(holding.costFiat)
            when {
                marketValue == null -> missingPriced += holding.coinId
                // D29：负持仓 = 导入路径的数据缺口信号（非真实头寸），其市值不计入净值与可用现金，
                // 只累计到 anomalousExcluded 供界面显式提示（避免按「缺失的入金」重复扣减用户资产）
                holding.quantity.signum() < 0 -> anomalousExcluded += marketValue
                else -> {
                    netValue += marketValue
                    totalCost += holding.costFiat
                    if (holding.coinId in cashCoinIds) cashValue += marketValue
                }
            }
            holdings[holding.coinId] = HoldingMetrics(
                coinId = holding.coinId,
                quantity = holding.quantity,
                avgCostFiat = holding.avgCostFiat,
                costFiat = holding.costFiat,
                realizedPnlFiat = holding.realizedPnlFiat,
                marketValue = marketValue,
                floatPnlFiat = floatPnl,
                floatPnlPercent = LedgerMath.percentOf(floatPnl ?: BigDecimal.ZERO, holding.costFiat),
                estimated = holding.estimated,
                anomalous = holding.anomalous,
                priced = marketValue != null,
                sources = holding.sources,
            )
        }

        val deposits = outcome.cumulativeDepositsFiat
        val withdrawals = outcome.cumulativeWithdrawalsFiat
        val hasPrincipal = deposits.signum() > 0
        val totalReturn = if (hasPrincipal) netValue + withdrawals - deposits else null
        val roiPercent = if (hasPrincipal) LedgerMath.percentOf(totalReturn!!, deposits) else null

        return PortfolioMetrics(
            netValueFiat = netValue,
            availableCashFiat = cashValue,
            investedNetFiat = deposits - withdrawals,
            cumulativeDepositsFiat = deposits,
            cumulativeWithdrawalsFiat = withdrawals,
            totalReturnFiat = totalReturn,
            roiPercent = roiPercent,
            realizedPnlFiat = outcome.realizedPnlFiat,
            unrealizedPnlFiat = netValue - totalCost,
            totalCostFiat = totalCost,
            holdings = holdings,
            missingPricedCoins = missingPriced,
            estimated = outcome.estimated,
            anomalousExcludedFiat = anomalousExcluded,
        )
    }

    companion object {
        /**
         * 现金类币种默认集合（可用现金口径；CoinGecko id）。**D28（2026-09-13 人工拍板）**：
         * 默认仅 **USDT（tether）**——固定不可移除；其余稳定币由用户在设置「稳定币白名单」中增删
         * （扩展项计入现金口径，但**按市价折算**，见 [ANCHORED_COIN_IDS]）。
         */
        val DEFAULT_CASH_COIN_IDS: Set<String> = setOf("tether")

        /**
         * **1:1 锚定折算集合（D28）**：仅 **USDT**，固定不可调整（不可通过设置扩展）。
         * 其余稳定币（含白名单扩展项）一律按市价快照折算——与「白名单 = 用户可增删的现金类币种」
         * 是两个不同概念，不要混用。
         */
        val ANCHORED_COIN_IDS: Set<String> = setOf("tether")
    }
}

/** 账户级派生指标（全部基础法币口径；"--" 语义以 null 表达，由 UI 层格式化）。 */
data class PortfolioMetrics(
    /** 资产净值 = Σ 持仓市值（缺价币不计）。 */
    val netValueFiat: BigDecimal,
    /** 可用现金余额 = 稳定币白名单持仓市值之和。 */
    val availableCashFiat: BigDecimal,
    /** 投入本金（净）= 累计增资 − 累计撤资。 */
    val investedNetFiat: BigDecimal,
    /** 累计增资（ROI 分母，含正差额校准）。 */
    val cumulativeDepositsFiat: BigDecimal,
    /** 累计撤资（含负差额校准）。 */
    val cumulativeWithdrawalsFiat: BigDecimal,
    /** 总收益 = 净值 + 累计撤资 − 累计增资；累计增资 = 0 时为 null（展示 "--"）。 */
    val totalReturnFiat: BigDecimal?,
    /** ROI %；累计增资 = 0 时为 null（展示 "--"）。 */
    val roiPercent: BigDecimal?,
    /** 累计已实现盈亏（卖出逐笔合计）。 */
    val realizedPnlFiat: BigDecimal,
    /** 未实现盈亏 = 净值 − 有价持仓总成本（含缺价币成本不含，口径见 KDoc）。 */
    val unrealizedPnlFiat: BigDecimal,
    /** 有价持仓总成本。 */
    val totalCostFiat: BigDecimal,
    /** 币种级明细（含零持仓/异常币）。 */
    val holdings: Map<String, HoldingMetrics>,
    /** 现价缺失币种（不计入净值；「无行情」/展示 "--"）。 */
    val missingPricedCoins: List<String>,
    /** 任一事件 PENDING（估算中标记）。 */
    val estimated: Boolean,
    /**
     * 被排除在净值/可用现金之外的**持仓异常币市值合计**（负值；D29）。
     * 界面据此显式提示「已排除 X 个异常币种的市值」——不靠净值数字隐式传达。
     */
    val anomalousExcludedFiat: BigDecimal = BigDecimal.ZERO,
)

/** 币种级指标（基础法币口径；null = 展示 "--"）。 */
data class HoldingMetrics(
    val coinId: String,
    val quantity: BigDecimal,
    val avgCostFiat: BigDecimal?,
    val costFiat: BigDecimal,
    val realizedPnlFiat: BigDecimal,
    /** 现价市值（缺价时 null）。 */
    val marketValue: BigDecimal?,
    /** 浮动盈亏 = 市值 − 总成本（缺价时 null）。 */
    val floatPnlFiat: BigDecimal?,
    /** 浮动盈亏 % = 浮动盈亏/总成本（成本 = 0 或缺价时 null）。 */
    val floatPnlPercent: BigDecimal?,
    val estimated: Boolean,
    val anomalous: Boolean,
    val priced: Boolean,
    val sources: Set<RecordSource>,
)
