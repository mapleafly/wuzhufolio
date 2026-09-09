package com.wuzhufolio.domain.ledger

import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.engine.NegativeViolation
import java.math.BigDecimal
import java.time.Instant

/**
 * 资金管理用例模型（M8 · task-breakdown T8.1–T8.3 · api-contracts §3 LedgerService 资金半边 +
 * 校准执行流（M4 遗留 4）· data-model §2.6 capital_flows / §2.7 reconciliation_records ·
 * PRD 故事 6.1/6.2/6.3、4.1-5、§9.8、全局说明「持仓校准规则」、interaction V6/V7）。
 *
 * 分层口径（ADR-001）：本包只含契约与纯规则；事件构造层资金半边（DB 行 -> FundEvent/AnchorEvent：
 * FK -> cg_id 解析、折算价解析与 PENDING 标记）与编排实现在 data/ledger（M4 §5 补录「事件构造层
 * 归 M7/M8」的资金半边随本模块落地）。
 */

/** 资金列表条目类型（校准差额视同系统生成的资金操作入列——PRD §9.8/§10-8 注）。 */
enum class FundEntryType {
    /** 增资（capital_flows.type = DEPOSIT）。 */
    DEPOSIT,

    /** 撤资（capital_flows.type = WITHDRAWAL）。 */
    WITHDRAWAL,

    /** 校准（reconciliation_records；锚点语义，不可编辑仅可删除）。 */
    RECONCILIATION,
}

/** 手动新增/编辑资金记录输入（PRD §9.8 表单字段；币种符号在服务内经 coins 目录归一并冻结 cg id）。 */
data class FundInput(
    /** 增资（DEPOSIT）/ 撤资（WITHDRAWAL）。 */
    val kind: FlowKind,
    /** 币种符号（限稳定币或其他加密货币；法币代码拒绝并提示改记兑换后到账的稳定币）。 */
    val coinSymbol: String,
    /** 数量（正数，V6）。 */
    val quantity: BigDecimal,
    /** 流水时间（UTC；表单本地时区输入、保存转 UTC）。 */
    val time: Instant,
    /** 来源（增资）/ 去向（撤资），可选。 */
    val sourceDest: String? = null,
    val notes: String? = null,
)

/**
 * 资金列表行（PRD §9.8 列字段：类型/币种/数量/折算基础法币金额/日期/来源去向/备注；混合列表 =
 * capital_flows + reconciliation_records 按时间倒序合并）。
 *
 * 校准行（[entryType] = RECONCILIATION）：[quantity] = 差额（exchange − local，可负）、[baseAmount] =
 * base_amount（差额折算）、[sourceDest] = 依据交易所、不可编辑仅可删除。
 */
data class FundEntryRow(
    val id: Long,
    /** 全局唯一事件标识（两表 uuid；删除操作键）。 */
    val uuid: String,
    val entryType: FundEntryType,
    /** DEPOSIT/WITHDRAWAL；校准行为 null（方向由差额符号承载）。 */
    val kind: FlowKind?,
    val coinSymbol: String,
    /** 增资/撤资 = 数量；校准行 = 差额（可负）。 */
    val quantity: BigDecimal,
    /** 折算基础法币金额（动态重建口径；待定价/估算中时展示配合 [estimated]）。 */
    val baseAmount: BigDecimal,
    /** 增资/撤资 = flow_time；校准行 = created_at。 */
    val time: Instant,
    val sourceDest: String?,
    val notes: String?,
    /** 任一折算价走了估算路径（「估算中」标注，PRD N3）。 */
    val estimated: Boolean,
)

/** 列表筛选（PRD §9.8：按类型、日期进行筛选和搜索）。 */
data class FundFilter(
    /** 自由文本（币种/来源去向/备注包含，大小写不敏感）。 */
    val query: String? = null,
    val type: FundEntryType? = null,
    val dateRange: FundDateRange = FundDateRange.ALL,
)

/** 日期筛选档位（原型资金页：全部时间 / 近 30 天 / 近 90 天 / 更早）。 */
enum class FundDateRange(internal val minDaysAgo: Long?, internal val maxDaysAgo: Long?) {
    ALL(null, null),
    LAST_30(0, 30),
    LAST_90(30, 90),
    OLDER(90, null),
    ;

    /** 行时间（UTC）是否落在档位内（[now] 为筛选时刻）。 */
    fun contains(time: Instant, now: Instant): Boolean {
        if (minDaysAgo == null && maxDaysAgo == null) return true
        val days = java.time.Duration.between(time, now).toDays()
        val minOk = minDaysAgo?.let { days >= it } ?: true
        val maxOk = maxDaysAgo?.let { days < it } ?: true
        return minOk && maxOk
    }
}

/** 资金页总览卡（PRD §9.8：可用现金余额 + 投入本金（净），副行 = 累计增资 − 累计撤资）。 */
data class FundsOverview(
    /** 可用现金余额 = 稳定币白名单持仓市值之和（现价缺失币不计）。 */
    val availableCashFiat: BigDecimal,
    /** 投入本金（净）= 累计增资 − 累计撤资。 */
    val investedNetFiat: BigDecimal,
    val cumulativeDepositsFiat: BigDecimal,
    val cumulativeWithdrawalsFiat: BigDecimal,
)

/** 折算预览（表单「折算基础法币（记录时行情价）」行；null = 暂无行情价）。 */
data class FiatValuePreview(val fiatValue: BigDecimal?, val estimated: Boolean)

/**
 * 资金管理用例契约（M8 · api-contracts §3 LedgerService 资金半边）。
 *
 * 编排语义：
 * - 手动新增/编辑/删除 = 事件构造层资金半边（含操作/不含操作双事件列表）调
 *   ReplayEngine.validateMutation 相对校验（M4 §5-5）+ [FundConflictClassifier] 违例分类
 *  （撤资本位点 -> V7 / 其余 -> V9）；撤资另做**同点绝对校验**（M4 §5-5「宽松口径复核归 M8」
 *   的收紧结论——导入异常位点不允许被撤资继续加深，见模块记录 M8 §5）；
 * - 折算 = 记录时行情价链（快照 nearestBefore -> USD 锚定 1:1 -> 最近可得估算）；完全缺失时
 *   以 0 参与重放并标估算（保数量链，黄金用例 9 回填后自动纠正）；
 * - 删除资金记录触发全量重放重算（确认框在 UI 层；PRD 故事 6.3）。
 */
interface FundService {

    /** 混合列表（capital_flows + 校准行，时间倒序；筛选见 [FundFilter]）。 */
    suspend fun listFunds(filter: FundFilter): FundPage

    /** 手动记录增资/撤资（V6 表单规则在 UI 层；V7/V9 重放校验在此；成功返回 capital_flows 行 id）。 */
    suspend fun saveFund(input: FundInput): Long

    /** 编辑资金记录（仅 capital_flows 行；校准行不可编辑——PRD §10-8 注）。 */
    suspend fun updateFund(id: Long, input: FundInput)

    /** 批量删除（资金记录与校准记录可混选；按 uuid 定位，批次整体做相对校验）。 */
    suspend fun deleteFunds(uuids: List<String>)

    /** 总览卡（可用现金余额 / 投入本金（净）+ 累计增资/撤资副行）。 */
    suspend fun fundsOverview(): FundsOverview

    /** 表单折算预览（记录时行情价；null = 暂无行情价）。 */
    suspend fun fiatValuePreview(coinSymbol: String, quantity: BigDecimal, at: Instant): FiatValuePreview

    /** 币种目录检索（表单币种自动补全，PRD §9.8「基于币种目录自动补全与归一」）。 */
    suspend fun searchCoins(query: String, limit: Int = 10): List<com.wuzhufolio.domain.catalog.CatalogCoin>

    /** 基础法币对应的默认币种（PRD §9.8：USD -> USDT，其余法币 -> USDC）。 */
    suspend fun defaultCoinSymbol(): String
}

/** 资金页数据（列表 + 总览卡同源重放；总览在重放异常等极端场景为 null，UI 展示 "--"）。 */
data class FundPage(val rows: List<FundEntryRow>, val overview: FundsOverview?)

/**
 * 撤资/校准违例分类（纯规则 · V7/V9 文案路由输入；api-contracts §4 错误码映射）。
 *
 * 相对校验（M4 §5-5）拦截的是「操作新制造」的负边界：
 * - 违例币种 = 本笔撤资币种 且 违例位点 = 本笔事件 -> 撤资持仓不足（V7：「XX 持仓不足，无法撤资」）；
 * - 其余（编辑/删除资金记录使**其他**记录位点转负）-> 重放冲突（V9）。
 */
object FundConflictClassifier {

    fun classify(violation: NegativeViolation, operated: com.wuzhufolio.domain.engine.FundEvent?): LedgerErrorCode =
        when {
            operated != null &&
                violation.eventId == operated.id &&
                operated.kind == FlowKind.WITHDRAWAL &&
                violation.coinId == operated.coin -> LedgerErrorCode.INSUFFICIENT_POSITION
            else -> LedgerErrorCode.REPLAY_CONFLICT
        }
}

// ---------------------------------------------------------------------------
// 持仓校准（M8 · M4 T4.4 编排落地：单一来源门 + 差额规划 + 市价前提 + 锚点入库 + 日志留痕）
// ---------------------------------------------------------------------------

/** 校准记录行（reconciliation_records；币种详情页校准历史 + 资金列表「校准」行的数据源）。 */
data class ReconciliationRow(
    val id: Long,
    val uuid: String,
    val symbol: String,
    val exchange: String,
    val localQuantity: BigDecimal,
    val exchangeQuantity: BigDecimal,
    /** 差额 = exchange_quantity − local_quantity（正 = 补增资语义 / 负 = 补撤资语义）。 */
    val delta: BigDecimal,
    /** 差额折算基础法币金额（|delta| × 校准时市价）。 */
    val baseAmount: BigDecimal,
    val createdAt: Instant,
)

/**
 * 校准准备结果（执行前预览：依据交易所 / 前后数量 / 差额 / 折算金额 / 方向）。
 * [direction] 正差额 = DEPOSIT（系统增资语义）、负差额 = WITHDRAWAL（系统撤资语义）、null = 无差额。
 */
data class CalibrationPreparation(
    val coinSymbol: String,
    val coinName: String,
    val exchangeName: String,
    /** 依据密钥行 id（执行留痕 sync_logs.api_key_id 口径）。 */
    val apiKeyId: Long,
    val apiKeyName: String,
    val localQuantity: BigDecimal,
    val exchangeQuantity: BigDecimal,
    val delta: BigDecimal,
    val deltaFiat: BigDecimal,
    /** 校准时市价（基础法币/1 币）。 */
    val marketPrice: BigDecimal,
    val direction: FlowKind?,
)

/** 校准执行结果（[recorded] = false 表示无差额、未生成记录）。 */
data class CalibrationResult(val recorded: Boolean, val row: ReconciliationRow?)

/** 校准不可执行原因（PRD 全局说明「持仓校准规则」；文案随异常给出）。 */
class CalibrationBlockedException(
    val reason: Reason,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    /** 校准入口被阻止的原因（UI 提示映射；PRD 故事 4.1-5「多来源/无行情时按钮隐藏并显示相应提示」）。 */
    enum class Reason {
        /** 该币种无任何交易记录（无可校准依据）。 */
        NO_RECORDS,

        /** 多来源（手动/CSV/多交易所/API+手动 混合）——入口隐藏并提示。 */
        MULTI_SOURCE,

        /** 依据交易所无已存只读密钥。 */
        NO_EXCHANGE_KEY,

        /** 行情不可用（无校准时市价）——「行情完全不可用时暂不允许执行校准并提示」。 */
        NO_MARKET_PRICE,

        /** 交易所余额获取失败（密钥失效/限流/网络等）。 */
        BALANCE_FETCH_FAILED,
    }
}

/**
 * 持仓校准用例契约（M8 · api-contracts §3 SyncService.reconcilePosition 细化落地）。
 *
 * 执行语义（PRD 全局说明「持仓校准规则」）：
 * - 单一来源门：仅交易事件来源唯一（资金/锚点不计，M4 §5-6）才可校准，多来源提示；
 * - 执行前提：校准时市价可得；交易所余额经已存只读密钥实时获取；
 * - 差额规划 = ReconciliationService.plan（正 -> 系统增资 / 负 -> 系统撤资语义）；
 * - 落库 = reconciliation_records（锚点语义参与全量重放）+ sync_logs 留痕（PRD 故事 4.1-5）；
 * - 锚点其后记录可能转负（锚点把持仓对齐到较小余额）-> 相对校验阻止（模块记录 M8 §5）。
 */
interface CalibrationUseCase {

    /** 校准准备（预览；任一前提不满足抛 [CalibrationBlockedException]）。 */
    suspend fun prepare(coinSymbol: String): CalibrationPreparation

    /** 执行校准：规划 -> 锚点入库 -> sync_logs 留痕 ->（UI 层触发刷新后）全量重放。 */
    suspend fun execute(coinSymbol: String): CalibrationResult

    /** 校准历史（币种详情页列表输入；M12 聚合页接线，本模块先落服务面）。 */
    suspend fun history(coinSymbol: String): List<ReconciliationRow>
}
