package com.wuzhufolio.domain.portfolio

import com.wuzhufolio.domain.engine.PortfolioMetrics
import com.wuzhufolio.domain.engine.RecordSource
import com.wuzhufolio.domain.engine.SourceClassification
import com.wuzhufolio.domain.ledger.TransactionRow
import com.wuzhufolio.domain.ledger.TxFilter
import com.wuzhufolio.domain.market.TwentyFourHour
import java.math.BigDecimal
import java.time.Instant

/**
 * 聚合页用例契约（M12 T12.1 · ia.md §2.4 仪表盘 / §2.5 资产列表 / §2.6 币种资产详情）。
 *
 * 职责 = **把 M4 引擎派生口径与 M5 快照、M3 目录拼成页面可直接渲染的行**：
 * 全量重放（[com.wuzhufolio.data.ledger.LedgerEventAssembler] + `ReplayEngine`）→ `PortfolioCalculator`
 * → 逐币补齐 coins 目录展示字段（symbol/name）与占比/24h。
 * 本契约只读，不产生任何写入（仪表盘/资产列表/币种详情均为只读页）。
 *
 * 与既有契约的关系：
 * - 账户级指标一律取自 `PortfolioCalculator`（单一真源，PRD 名词解释）；本层不重算；
 * - 币种详情交易列表复用 M7 [TransactionRow]（卖出行已实现盈亏由 M7 的卖出轨迹产出，不另算一套）；
 * - 校准历史复用 M8 reconciliation_records；校准入口可见性复用 M4 `ReconciliationService.classifySources`。
 */
interface PortfolioService {

    /** 账户级聚合（仪表盘与资产列表共用；每次调用重新重放，保证与账本一致）。 */
    suspend fun snapshot(): PortfolioSnapshot

    /**
     * 币种资产详情（ia.md §2.6）：该币持仓指标 + 交易记录 + 校准历史 + 校准入口可见性。
     * [cgId] 为 CoinGecko id；币种不在目录中时返回仅含 [CoinDetail.cgId] 的最小结构。
     */
    suspend fun coinDetail(cgId: String, filter: TxFilter = TxFilter()): CoinDetail
}

/**
 * 账户级聚合快照。
 *
 * [rows] 顺序口径 = 市值降序（PRD interaction.md §3-1「资产按市值降序」；缺价币排在末尾按名称升序）。
 * 页面自行排序（市值/名称/盈亏）时以 [rows] 为全集重排，不再回查服务。
 */
data class PortfolioSnapshot(
    /** 基础法币（settings；页面标题与列头用）。 */
    val fiat: String,
    /** 引擎派生指标（净值/可用现金/投入本金/总收益/ROI/已实现/未实现）。 */
    val metrics: PortfolioMetrics,
    /** 币种行（含零持仓与异常币；仅目录命中项）。 */
    val rows: List<PortfolioRow>,
    /** 24h 盈亏（固定数量回算法；覆盖 N/M 与混合数据源标注）。 */
    val change24h: TwentyFourHour.Result,
    /** 现价快照时刻（最近一次成功刷新的时刻；null = 尚无行情）。 */
    val priceAsOf: Instant?,
    /** 任一事件 PENDING（折算估算中；「估算中」标注）。 */
    val estimated: Boolean,
    /** 持仓异常币种 cg_id（期末持仓为负；PRD 全局说明「重放校验规则」）。 */
    val anomalousCoins: List<String>,
)

/** 资产列表/详情共用的币种行（全部基础法币口径；null = 展示 "--"）。 */
@Suppress("LongParameterList") // 页面列字段一一对应（ia.md §2.5 列表字段 + 详情汇总项），保持扁平
data class PortfolioRow(
    val cgId: String,
    val symbol: String,
    val name: String,
    /** 持有数量。 */
    val quantity: BigDecimal,
    /** 平均成本价（基础法币；无持仓 → null）。 */
    val avgCostFiat: BigDecimal?,
    /** 当前价（基础法币；无快照 → null，列表显示「无行情」）。 */
    val priceFiat: BigDecimal?,
    /** 总市值 = 数量 × 现价（缺价 → null，不计入净值）。 */
    val marketValueFiat: BigDecimal?,
    /** 总浮动盈亏 = 市值 − 总成本（缺价 → null）。 */
    val floatPnlFiat: BigDecimal?,
    /** 浮动盈亏 % （成本为 0 或缺价 → null）。 */
    val floatPnlPercent: BigDecimal?,
    /** 累计已实现盈亏（基础法币）。 */
    val realizedPnlFiat: BigDecimal,
    /** 占总资产占比 %（缺价币无市值 → null；净值 ≤ 0 → null）。 */
    val sharePercent: BigDecimal?,
    /** 是否有现价（false = 「无行情」标记）。 */
    val priced: Boolean,
    /** 持仓异常（期末负持仓）。 */
    val anomalous: Boolean,
    /** 本币任一事件折算估算中。 */
    val estimated: Boolean,
    /** 记录来源集合（详情页展示来源判定）。 */
    val sources: Set<RecordSource>,
    /** 来源分类（单一交易所 → 校准入口可见；多来源/无记录 → 隐藏，PRD 故事 4.1-5）。 */
    val sourceClassification: SourceClassification,
) {
    /** 校准入口是否可见（仅单一交易所 API 来源）。 */
    val calibratable: Boolean get() = sourceClassification is SourceClassification.SingleExchange
}

/**
 * 币种资产详情（ia.md §2.6）。
 *
 * [transactions] 为该币全部交易（两腿任一命中 [cgId] 即算本币交易——买入/卖出都在本币视角下有意义），
 * 按交易时间降序；卖出行 [TransactionRow.realizedPnlFiat] 非空。筛选（交易所/类型/搜索）由页面传
 * [TxFilter] 或就地过滤（[TxFilter.coinSymbol] 由服务补入）。
 */
data class CoinDetail(
    val cgId: String,
    val symbol: String,
    val name: String,
    /** 该币持仓指标行（无持仓但有交易时仍给出，quantity = 0；目录未收录 → 最小行）。 */
    val row: PortfolioRow?,
    /** 该币历史校准记录（按时间降序；可空）。 */
    val calibrations: List<CalibrationRecord>,
) {
    val calibratable: Boolean get() = row?.calibratable == true
}

/** 校准历史行（reconciliation_records 展示口径；ia.md §2.6「校准历史列表」）。 */
data class CalibrationRecord(
    val id: Long,
    val at: Instant,
    /** 校准后交易所余额。 */
    val exchangeQuantity: BigDecimal,
    /** 差额（= 交易所余额 − 校准前本地持仓）。 */
    val delta: BigDecimal,
    /** 差额折算基础法币金额（记录值固定）。 */
    val deltaFiat: BigDecimal,
    val exchange: String?,
    val notes: String?,
)
