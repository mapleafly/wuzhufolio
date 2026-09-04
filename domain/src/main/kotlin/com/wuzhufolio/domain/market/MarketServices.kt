package com.wuzhufolio.domain.market

import java.math.BigDecimal

/**
 * 行情刷新与 Key 配置用例契约（T5.4/T5.5；api-contracts §3 MarketService 细化 +
 * ADR-002 §2.1 方案甲：Key 设备密钥加密存 settings 全局行、不进 .cpro 备份）。
 *
 * 消费方：M5 设置页（行情数据源分组）、M12 主壳/状态栏；实现层编排 CoinGecko 主源 → CoinMarketCap 兜底、
 * 429 退避、额度计数（80% 降档）与快照落库（见模块记录 M5 §5 边界：持仓币集注入点归 M7/M12）。
 */
interface MarketRefreshService {

    /**
     * 立即刷新行情（手动 [manual]=true 或调度触发）：主源 CG 失败（网络/超时/429/额度耗尽/币种未收录）
     * → 已配置 CMC Key 时按 cmc_id 兜底；兜底失败/未配置 → 保持上次价格语义（error 置位）。
     * [coins]/[fiats] 为空列表 = 使用默认币集（现金白名单）与默认法币（基础法币，调用方设置）。
     */
    suspend fun refresh(manual: Boolean, coins: List<String> = emptyList(), fiats: List<String> = emptyList()):
    MarketRefreshResult

    /** 最近一次结果（含上次成功时刻/数据源；未刷新过 = 空）。 */
    fun lastResult(): MarketRefreshResult?

    /** 当前额度使用百分比（仅配置个人 CG Key 后计数；否则 null）。 */
    suspend fun quotaPercentUsed(): Int?

    /** 目录数据新鲜度（>24h 视为过期，编排刷新前先拉 /coins/list + CMC map）。 */
    suspend fun directoryFresh(): Boolean
}

/** 行情 Key 设置用例（T5.5：保存即生效——保存即由后续刷新使用，无需重启；明文不驻留）。 */
interface MarketSettingsService {

    suspend fun keyStatus(): MarketKeyStatus

    /** 保存/覆盖 CG 个人 Key（空串 = 不修改并保持现状由 UI 先行判断）；返回最新状态。 */
    suspend fun saveCgKey(key: String): MarketKeyStatus

    suspend fun removeCgKey(): MarketKeyStatus

    /** 保存/覆盖 CMC Key（空串语义同上）。 */
    suspend fun saveCmcKey(key: String): MarketKeyStatus

    suspend fun removeCmcKey(): MarketKeyStatus

    /** 基础法币（settings 全局键 fiat，缺省 USD；行情页计价显示用）。 */
    suspend fun baseFiat(): String

    /** 行情刷新频率档位（分钟；settings 全局键 market.refresh_minutes，缺省 5）。 */
    suspend fun refreshFrequencyMinutes(): Int

    /** 设置行情刷新频率（限 5/15/30，其他值拒绝）。 */
    suspend fun saveRefreshFrequencyMinutes(minutes: Int)
}

/** 历史回填报告（T5.3：区间桶请求数/实际新增行数——回填后目标时刻可由快照解析即
 * 「空洞消除」）。 */
data class BackfillReport(
    val coin: String,
    val fiat: String,
    val from: java.time.Instant,
    val to: java.time.Instant,
    /** 本区间应覆盖的时间桶数（≤90 天 = 小时桶；更早 = 日线桶）。 */
    val requestedBuckets: Int,
    /** 本次实际新增快照行数（已存在桶不计）。 */
    val newRows: Int,
)

/**
 * 历史价回填用例（T5.3 · PRD 全局说明「历史价格回填任务」）：CG /market_chart/range 区间批量合并
 * 请求 + 桶去重（空洞才补，不重复计费）；**仅主源**（CMC 无免费历史端点，回填失败按「待定价」延后重试，
 * 语义归 M7 事件构造层）。联网回填后快照可解析 → 上层重放/折算自动纠正（黄金用例 9 后端部分）。
 */
interface MarketHistoryBackfillService {

    /** 回填 [from, to) 区间；from/to 由调用方取整到桶边界。 */
    suspend fun backfillRange(coin: String, fiat: String, from: java.time.Instant, to: java.time.Instant):
    BackfillReport
}

/** 24h 计算输入/结果（黄金用例 11：覆盖 N/M 与全部缺失 "--"；同源标注）。 */
object TwentyFourHour {
    const val SCALE: Int = 8

    /**
     * 单币输入。quantity 为当前持仓数量；priceNow/sourceNow 为当前价；priceAgo/sourceAgo 为 24h 前价
     * （缺失 = null，该币计入 total 但不计 covered）；mixed = 本币配对为异源（标注「混合数据源」）。
     */
    data class CoinInput(
        val coinId: String,
        val quantity: BigDecimal,
        val priceNow: BigDecimal?,
        val sourceNow: PriceSource?,
        val priceAgo: BigDecimal?,
        val sourceAgo: PriceSource?,
        /** 异源配对（now 源 ≠ ago 源且两者都非空）。 */
        val mixed: Boolean = false,
    )

    data class Result(
        /** 24h 盈亏金额（固定数量回算法）；有价币为 0 或全部缺 24h 前价 → null（"--"）。 */
        val pnlFiat: BigDecimal?,
        /** 盈亏百分比 = pnl / Σ(持仓×24h前价) ×100；基数为 0 → null。 */
        val pct: BigDecimal?,
        /** 覆盖（有 24h 前价）币数 / 有当前价币数（24h 部分缺价标注「覆盖 N/M 个币种」）。 */
        val covered: Int,
        val total: Int,
        /** 任一配对为异源（「混合数据源」标注）。 */
        val mixedSource: Boolean,
    )

    /**
     * 固定数量回算法（PRD 名词解释）：24h 盈亏 = Σ(当前持仓 × 当前价) − Σ(当前持仓 × 24h 前价)。
     * 口径：total = 有当前价币数（「无行情」币不计入）；覆盖 = 其中也有 24h 前价的币；
     * 部分缺失 → 剔除缺失币计算（覆盖 N/M）；全部缺失 → pnl/pct = null（显示 "--"）。
     */
    fun compute(inputs: List<CoinInput>): Result {
        val priced = inputs.filter { it.priceNow != null }
        val covered = priced.filter { it.priceAgo != null }
        val base = covered.fold(BigDecimal.ZERO) { acc, c ->
            acc + c.quantity.multiply(c.priceAgo!!).setScale(SCALE, java.math.RoundingMode.HALF_UP)
        }
        val delta = covered.fold(BigDecimal.ZERO) { acc, c ->
            acc + c.quantity.multiply(c.priceNow!! - c.priceAgo!!).setScale(SCALE, java.math.RoundingMode.HALF_UP)
        }
        val mixed = covered.any { it.mixed }
        return Result(
            pnlFiat = if (priced.isEmpty() || covered.isEmpty()) null else delta,
            pct = if (base.signum() == 0 || covered.isEmpty()) null
            else delta.multiply(BigDecimal(100)).divide(base, 12, java.math.RoundingMode.HALF_UP),
            covered = covered.size,
            total = priced.size,
            mixedSource = mixed,
        )
    }
}
