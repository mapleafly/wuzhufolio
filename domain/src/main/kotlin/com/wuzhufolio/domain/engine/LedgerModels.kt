package com.wuzhufolio.domain.engine

import java.math.BigDecimal
import java.time.Instant

/**
 * M4 重放与计算引擎领域模型（task-breakdown T4.1–T4.4 · architecture §2.3/§3.2 · data-model §3 派生口径）。
 *
 * 记账口径（M4 规格裁决，登记 docs/dev/modules/M4.md §5）：
 * - 币键 = CoinGecko id（PRD「币种标识与主数据规则」内部唯一标识；data-model 记录冻结的是 coins 行 FK id，
 *   引擎消费事件前由 M7/M8 事件构造层做 FK → cg_id 解析——读侧本就 join 目录，见模块记录 §5-3）；
 * - 记账单位 = 基础法币：净值/累计增资/撤资/已实现盈亏/成本均为基础法币金额；
 * - 法币折算值（[TradeEvent.legFiat]/[TradeEvent.feeFiat]/[FundEvent.fiatValue]/[AnchorEvent.deltaFiat]）
 *   由上层「记录时行情价解析层」（快照 → 行情 API 回填 → 最近可得价估算，PRD 全局说明「折算价格解析」）
 *   在事件构造时解析并随事件传入——引擎为纯确定性计算，不做价格 IO；
 *   估算态经 [LedgerEvent.estimated] 传播（PENDING → 派生指标标「估算中」，联网回填后重建事件自动消除，黄金用例 9）；
 * - 交易对计价币种归一（USD→USDT 等孪生映射）发生在事件构造层（M3 FiatNormalizer + M7 归一），
 *   引擎只见到归一化后的币键（黄金用例 12）。
 */
sealed interface LedgerEvent {
    /** 记录标识（transactions/capital_flows/reconciliation_records.uuid，测试可用任意唯一串）。 */
    val id: String

    /** 事件时间（UTC；transactions.transaction_time / capital_flows.flow_time / reconciliation_records.created_at）。 */
    val at: Instant

    /**
     * 同刻稳定排序键（跨三类事件无天然全局序；调用方传入稳定键——建议同一账户内按记录持久化序派生，
     * 缺省 0 时引擎按输入列表原序作最终 tie-break，调用方需保证输入序确定性）。
     */
    val seq: Long

    /** 本记录折算价格 PENDING（估算中）标记（data-model transactions/capital_flows.price_status）。 */
    val estimated: Boolean
}

/** 交易方向（data-model §2.5 transactions.type）。 */
enum class Side { BUY, SELL }

/** 资金流水类型（data-model §2.6 capital_flows.type）。 */
enum class FlowKind { DEPOSIT, WITHDRAWAL }

/**
 * 记录来源（data-model §2.5 transactions.source：Manual / CSV / BINANCE API；MVP 交易所=BINANCE）。
 *
 * 仅**交易事件**携带来源并参与币种「单一数据来源」判定（PRD 故事 4.1-5 持仓校准适用条件）：
 * 手动增资/撤资（资金事件）与校准锚点不计入——校准对照的是交易所余额，用户为交易所内未同步到账的
 * 转入补录增资属常态路径（M4 规格裁决，登记模块记录 §5）。
 */
sealed interface RecordSource {
    /** 稳定描述串（多来源提示/诊断用；i18n 展示映射由 UI 层承担）。 */
    val describe: String

    /** 手动录入。 */
    data object Manual : RecordSource {
        override val describe: String = "manual"
    }

    /** CSV 导入。 */
    data object Csv : RecordSource {
        override val describe: String = "csv"
    }

    /** 交易所 API 同步（源 = 交易所名；MVP BINANCE）。 */
    data class Exchange(val exchangeName: String) : RecordSource {
        override val describe: String = "exchange:" + exchangeName
    }
}

/**
 * 交易事件（transactions 表事件化）。
 *
 * 单位语义：
 * - [price] = 成交价（计价币/基础币）；[quantity] = 数量（base 币）；[fee] = 手续费数量（feeCoin 计价）；
 * - [legFiat] = 交易腿总价折算基础法币（= quantity×price×px(quote)，解析层折算）；
 * - [feeFiat] = 手续费折算基础法币（= fee×px(feeCoin)，解析层折算）；
 * 手续费币种角色由币键关系判定：== [quoteCoin] → quote 侧（从 quote 持仓扣减/总价内扣）；
 * == [baseCoin] → base 侧（买入实际到账 = quantity − fee；卖出按「从转出数量扣除」）；其余 → 第三币种
 * （联动扣减 feeCoin 持仓 + 折算值计入成本，PRD 故事 7.1 / 黄金用例 5——故事正文「不联动」与黄金用例 5
 * 「BNB 持仓联动扣减」冲突，M4 裁决取黄金用例口径，登记模块记录 §5）。
 */
data class TradeEvent(
    override val id: String,
    override val at: Instant,
    val baseCoin: String,
    val quoteCoin: String,
    val side: Side,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val fee: BigDecimal,
    val feeCoin: String,
    val legFiat: BigDecimal,
    val feeFiat: BigDecimal,
    val source: RecordSource,
    override val seq: Long = 0L,
    override val estimated: Boolean = false,
) : LedgerEvent {
    init {
        require(price.signum() > 0) { "trade price must be positive" }
        require(quantity.signum() > 0) { "trade quantity must be positive" }
        require(fee.signum() >= 0) { "trade fee must be non-negative" }
        require(legFiat.signum() > 0) { "trade leg fiat value must be positive" }
        require(feeFiat.signum() >= 0) { "trade fee fiat value must be non-negative" }
        require(baseCoin != quoteCoin) { "base and quote must differ" }
    }

    val isQuoteFee: Boolean get() = feeCoin == quoteCoin
    val isBaseFee: Boolean get() = feeCoin == baseCoin
    val isThirdFee: Boolean get() = !isQuoteFee && !isBaseFee
}

/**
 * 资金事件（capital_flows 表事件化）。
 * [kind] DEPOSIT → 持仓与累计增资增加 [fiatValue]；WITHDRAWAL → 按当时平均成本移出数量、累计撤资增加 [fiatValue]
 * （不产生盈亏，PRD 全局说明「成本计算规范」/故事 6.1/6.2）。
 */
data class FundEvent(
    override val id: String,
    override val at: Instant,
    val coin: String,
    val kind: FlowKind,
    val quantity: BigDecimal,
    val fiatValue: BigDecimal,
    override val seq: Long = 0L,
    override val estimated: Boolean = false,
) : LedgerEvent {
    init {
        require(quantity.signum() > 0) { "fund quantity must be positive" }
        require(fiatValue.signum() >= 0) { "fund fiat value must be non-negative" }
    }
}

/**
 * 持仓校准锚点事件（reconciliation_records 表事件化，PRD 全局说明「持仓校准规则」）。
 *
 * 锚点语义：重放到本事件时刻将该币种持仓**强制对齐**为 [exchangeQuantity]（校准前历史记录的编辑/删除
 * 不回滚校准效果）；[delta]（= 交易所余额 − 校准前本地持仓）方向决定差额账务去向——正差额视同系统增资
 * （累计增资 += [deltaFiat]，成本按校准时市价计入）、负差额视同系统撤资（累计撤资 += [deltaFiat]，
 * 成本按当时平均成本移出），不产生盈亏（恒等式「总收益 = 净值 + 撤资 − 增资」保持，黄金用例 8）。
 * [deltaFiat] = |delta| × 校准时市价（reconciliation_records.base_amount，>0）。
 */
data class AnchorEvent(
    override val id: String,
    override val at: Instant,
    val coin: String,
    val exchangeQuantity: BigDecimal,
    val delta: BigDecimal,
    val deltaFiat: BigDecimal,
    override val seq: Long = 0L,
    override val estimated: Boolean = false,
) : LedgerEvent {
    init {
        require(exchangeQuantity.signum() >= 0) { "exchange quantity must be non-negative" }
        require(deltaFiat.signum() >= 0) { "delta fiat value must be non-negative" }
        require(delta.signum() != 0) { "zero-delta anchor is a no-op and must not be recorded" }
    }
}

/** 重放后单币种持仓/盈亏状态（派生值，不落表——data-model §3）。 */
data class CoinHolding(
    val coinId: String,
    /** 期末持仓数量。 */
    val quantity: BigDecimal,
    /** 总成本（基础法币，含手续费折算；平均成本 = cost/quantity 或 null（无持仓））。 */
    val costFiat: BigDecimal,
    /** 已实现盈亏（基础法币，仅卖出逐笔累计；异常区段不推导，见 [ReplayEngine]）。 */
    val realizedPnlFiat: BigDecimal,
    /** 涉及本币种的全部交易来源（仅交易事件；单一数据来源判定输入，T4.4）。 */
    val sources: Set<RecordSource>,
    /** 本币种任一事件 PENDING（折算估算中）。 */
    val estimated: Boolean,
    /** 宽松模式下是否出现过负持仓边界（手动严格校验拦截的制造方除外；入库口径见模块记录）。 */
    val hadNegativeBoundary: Boolean,
) {
    /** 持仓异常标记：期末持仓为负（导入路径例外，PRD 全局说明「重放校验规则」；黄金用例 6）。 */
    val anomalous: Boolean get() = quantity.signum() < 0

    /** 平均成本（基础法币；无持仓时为 null，展示 "--"）。 */
    val avgCostFiat: BigDecimal?
        get() = if (quantity.signum() > 0) LedgerMath.divide(costFiat, quantity) else null
}

/** 负持仓边界（宽松模式记录/严格模式首违例；shortage = 违例时刻余额，<= 0）。 */
data class NegativeViolation(
    val coinId: String,
    val eventId: String,
    val at: Instant,
    val seq: Long,
    val shortage: BigDecimal,
)

/** 重放汇总（账户级派生口径，data-model §3）。 */
data class ReplayOutcome(
    val holdings: Map<String, CoinHolding>,
    /** 累计增资（含正差额校准，基础法币）。 */
    val cumulativeDepositsFiat: BigDecimal,
    /** 累计撤资（含负差额校准，基础法币）。 */
    val cumulativeWithdrawalsFiat: BigDecimal,
    /** 已实现盈亏合计（全部卖出逐笔，基础法币）。 */
    val realizedPnlFiat: BigDecimal,
    /** 宽松模式的负持仓边界清单（严格模式遇首违例抛异常、不产出结果）。 */
    val violations: List<NegativeViolation>,
    /** 任一事件 PENDING（估算中）。 */
    val estimated: Boolean,
) {
    /** 投入本金（净）= 累计增资 − 累计撤资（PRD 名词解释；ROI 分母仍为累计增资）。 */
    val investedNetFiat: BigDecimal get() = cumulativeDepositsFiat - cumulativeWithdrawalsFiat

    /** 持仓异常币种（期末持仓为负）。 */
    val anomalousCoins: Set<String>
        get() = holdings.values.filter { it.anomalous }.map { it.coinId }.toSet()
}

/** 手动记录变更（新增/编辑/删除）校验结论（PRD「重放校验规则」；拦截文案由调用层映射）。 */
sealed interface MutationVerdict {
    /** 重放全程无**新制造**的负持仓边界（既有宽松导入造成的负边界不因本操作新增则不拦截，见 [ReplayEngine.validateMutation]）。 */
    data object Ok : MutationVerdict

    /** 本操作制造了负持仓边界（含首违例明细），阻止保存并提示冲突原因。 */
    data class Blocked(val violation: NegativeViolation) : MutationVerdict
}

/** 重放负持仓策略（PRD 全局说明「重放校验规则」）。 */
enum class NegativePolicy {
    /** 手动路径（新增/编辑/删除交易或资金记录）：遇任一时刻任一币种持仓为负即抛 [NegativePositionException]。 */
    STRICT,

    /** 导入路径（CSV 导入 / API 同步 / 备份恢复）：不拦截，负边界记入 [ReplayOutcome.violations]，持仓标记异常。 */
    LENIENT,
}
