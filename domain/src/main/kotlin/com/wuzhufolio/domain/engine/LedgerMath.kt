package com.wuzhufolio.domain.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 引擎算术约定（M4 规格裁决）：
 * - 输入金额/价格精度 <= 8 位小数（PRD 全局说明「数据精度」）；加/减/乘保持 BigDecimal 全精度不预先舍入；
 * - 除法（平均成本等不循环不舍情况）统一舍入到 [COST_SCALE]（12 位）HALF_UP，避免浮点与无限小数；
 * - 展示层舍入（市值/盈亏 2 位等）不在引擎内做（PRD 列表默认数据规则），UI 层负责。
 */
object LedgerMath {

    /** 内部除法舍入位（成本/均价精度，12 位 HALF_UP；高于展示精度的防护性余量）。 */
    const val COST_SCALE: Int = 12

    private val DIVISION_MODE: RoundingMode = RoundingMode.HALF_UP

    /** a/b 舍入 12 位（b 须非零；引擎内均先判零再调用）。 */
    fun divide(a: BigDecimal, b: BigDecimal): BigDecimal = a.divide(b, COST_SCALE, DIVISION_MODE)

    /** 按数量占比移出成本：removed = cost × out / before（保持均价不变；before 须 > 0）。 */
    fun proportionalRemoval(cost: BigDecimal, out: BigDecimal, before: BigDecimal): BigDecimal =
        divide(cost.multiply(out), before)

    /**
     * 百分比口径（ROI/浮动盈亏率）：pct = 部分/基数×100，基数 0 → null（展示 "--"）。
     */
    fun percentOf(part: BigDecimal, base: BigDecimal): BigDecimal? =
        if (base.signum() == 0) null else divide(part.multiply(BigDecimal(100)), base)

    /** 精确零比较（signum，避免 scale 差异干扰）。 */
    fun isZero(x: BigDecimal): Boolean = x.signum() == 0
}
