package com.wuzhufolio.domain.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 手续费自动计算（T4.3，PRD 故事 7.1/7.2 / §7.2 模块 6.3 / data-model §2.4 fee_rules）。
 *
 * 费率匹配：交易所特定规则 > 全局默认规则（同一交易所多条取首条；规则按交易所+买卖向存储为百分比
 * 数值，见 [FeeRule]）；无规则返回 null（不自动计算，用户可手动填手续费）。
 *
 * 三币种计费基数（故事 7.1 字面口径，金额公式与买卖方向无关——方向差异只体现于所选费率，
 * 由 [FeeRateResolver] 按 side 选好费率后传入）：
 * - 计价币（quote）：手续费数量 = 费率 × 总价（数量×价格），quote 计价；
 * - 基础币（base）：手续费数量 = 费率 × 成交数量，base 计价；
 * - 第三币种：基数 = 总价（quote 单位），再按记录时行情价折算为该币种数量
 *   （数量 = 费率×总价×px(quote)/px(fee)）。
 *
 * 折算价值 = 数量 × px(手续费币种)（基础法币口径）。所需现价参数（[quoteFiatPx]/[feeFiatPx]）
 * 由调用层以「记录时行情价解析」提供（快照 → API → 最近可得估算，PENDING 语义在事件构造层，
 * 见 [LedgerEvent]）；必需价格缺失返回 null（调用层转「暂不可自动计算」提示或 PENDING 处理）。
 */
object FeeCalculator {

    /** 手续费币种相对交易对的角色（口径：feeCoin == quote → QUOTE；== base → BASE；其余 → THIRD）。 */
    fun roleOf(baseCoin: String, quoteCoin: String, feeCoin: String): FeeCoinRole =
        when (feeCoin) {
            quoteCoin -> FeeCoinRole.QUOTE
            baseCoin -> FeeCoinRole.BASE
            else -> FeeCoinRole.THIRD
        }

    /**
     * 自动计算手续费。
     * @param ratePercent 费率（百分比数值，如 0.1 表示 0.1%——与 fee_rules.buy_rate/sell_rate 存储口径一致）
     * @param prices 折算所需现价（基础法币；QUOTE 需 quoteFiat、BASE 需 feeFiat、THIRD 需两者）
     * @return null = 所需行情价缺失（无法折算），调用层按 PENDING/不可计算处理；其余返回折算后数量与价值。
     */
    fun feeFor(
        ratePercent: BigDecimal,
        quantity: BigDecimal,
        price: BigDecimal,
        role: FeeCoinRole,
        prices: ConversionPx = ConversionPx(),
    ): FeeQuote? {
        require(ratePercent.signum() > 0) { "fee rate must be positive" }
        val rate = ratePercent.divide(HUNDRED, LedgerMath.COST_SCALE, RoundingMode.HALF_UP)
        return when (role) {
            FeeCoinRole.QUOTE -> {
                val coinQty = rate.multiply(quantity).multiply(price)
                prices.quoteFiat?.let { FeeQuote(coinQty, coinQty.multiply(it)) }
            }
            FeeCoinRole.BASE -> {
                val coinQty = rate.multiply(quantity)
                prices.feeFiat?.let { FeeQuote(coinQty, coinQty.multiply(it)) }
            }
            FeeCoinRole.THIRD -> {
                if (prices.quoteFiat == null || prices.feeFiat == null) {
                    null
                } else {
                    val fiatValue = rate.multiply(quantity)
                        .multiply(price).multiply(prices.quoteFiat)
                    FeeQuote(coinQty = LedgerMath.divide(fiatValue, prices.feeFiat), fiatValue = fiatValue)
                }
            }
        }
    }

    private val HUNDRED: BigDecimal = BigDecimal(100)
}

/** 自动计算所需的折算现价（基础法币口径；[quoteFiat] = 计价币法币价、[feeFiat] = 手续费币法币价）。 */
data class ConversionPx(
    val quoteFiat: BigDecimal? = null,
    val feeFiat: BigDecimal? = null,
)

/** 手续费币种角色（计价/基础/第三币种，故事 7.1 三态）。 */
enum class FeeCoinRole { QUOTE, BASE, THIRD }

/** 自动计算产物：feeCoin 数量 + 折算基础法币价值。 */
data class FeeQuote(
    /** 以手续费币种计的数量（买入/卖出联动扣减与成本折算的输入）。 */
    val coinQty: BigDecimal,
    /** 折算基础法币价值（= 数量 × 记录时行情价）。 */
    val fiatValue: BigDecimal,
)

/**
 * 手续费费率规则（data-model §2.4 fee_rules 行：exchange 空 = 全局默认；费率百分比）。
 * 交易对级费率不在 MVP 范围（故事 7.2 注）。
 */
data class FeeRule(
    /** 交易所（null = 全局默认）。 */
    val exchange: String?,
    val buyRatePercent: BigDecimal,
    val sellRatePercent: BigDecimal,
) {
    init {
        require(buyRatePercent.signum() >= 0 && sellRatePercent.signum() >= 0) {
            "fee rates must be non-negative"
        }
    }
}

/** 费率解析（优先级：交易所 > 全局，故事 7.2；多条同 exchange 时取列表首条——CRUD 层保证唯一）。 */
class FeeRateResolver(private val rules: List<FeeRule> = emptyList()) {

    /** 返回适用的百分比费率；无任何规则 → null（自动计算不可用）。 */
    fun ratePercentFor(exchange: String, side: Side): BigDecimal? {
        val matched = rules.firstOrNull { rule ->
            rule.exchange != null && rule.exchange.equals(exchange, ignoreCase = true)
        } ?: rules.firstOrNull { it.exchange == null } ?: return null
        return if (side == Side.BUY) matched.buyRatePercent else matched.sellRatePercent
    }
}
