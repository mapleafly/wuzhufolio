package com.wuzhufolio.domain.ledger

import com.wuzhufolio.domain.engine.Side
import java.math.BigDecimal
import java.time.Instant

/**
 * 交易账本用例模型（M7 · task-breakdown T7.1-T7.4 · api-contracts §3 LedgerService 交易半边 ·
 * data-model §2.5 transactions / §2.4 fee_rules / PRD 故事 2.1/2.3/7.1、§9.6/9.7）。
 *
 * 分层口径（ADR-001）：本包只含契约与纯规则；事件构造层（DB 行 -> LedgerEvent：FK -> cg_id 解析、
 * 折算价解析与 PENDING 标记）与编排实现在 data/ledger——api-contracts §3 M4 补录「事件构造层归 M7/M8」
 * 的交易半边落在本模块，资金半边归 M8。
 */

/**
 * 手动新增/编辑交易输入（PRD §9.7 表单字段；币种符号在服务内经 coins 目录归一并冻结 cg id）。
 *
 * [feeCoinSymbol] 语义 = 原型「手续费币种」三态：与计价币同符号 -> QUOTE 态；与基础币同 -> BASE 态；
 * 其余 -> 第三币种（联动扣减持仓，黄金用例 5 口径——M4 规格裁决 3）。默认计价币种。
 */
data class TransactionInput(
    /** 交易所（下拉/自定义输入；存储统一大写，如 BINANCE——与 API 同步行、费率规则匹配键一致）。 */
    val exchange: String,
    /** 交易对基础币符号（输入归一：usdt->USDT）。 */
    val baseSymbol: String,
    /** 交易对计价币符号；法币计价时经 FiatNormalizer 归一为孪生稳定币（USD->USDT，黄金用例 12）。 */
    val quoteSymbol: String,
    val side: Side,
    val price: BigDecimal,
    val quantity: BigDecimal,
    /** 手续费数量（以 [feeCoinSymbol] 计；非负，可为 0）。 */
    val fee: BigDecimal,
    val feeCoinSymbol: String,
    /** 交易时间（UTC；表单本地时区输入、保存转 UTC——PRD 时间与时区规则）。 */
    val time: Instant,
    val notes: String? = null,
)

/** 交易列表行（PRD §9.6 列字段；卖出行 [realizedPnlFiat] 非空时展示该笔已实现盈亏）。 */
data class TransactionRow(
    val id: Long,
    val exchange: String,
    val exchangeOrderId: String?,
    /** 展示交易对（保存时冻结，如 "BTC/USDT"；法币计价保留录入形态）。 */
    val pair: String,
    val baseSymbol: String,
    val quoteSymbol: String,
    val side: Side,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val fee: BigDecimal,
    val feeCurrency: String?,
    /** 总价 = 价格 × 数量（计价币口径）。 */
    val total: BigDecimal,
    val time: Instant,
    val notes: String?,
    /** Manual / CSV / BINANCE API（data-model §2.5 source）。 */
    val source: String,
    /** 存储折算状态（OK/PENDING；动态估算态见 [TransactionRow.estimated]）。 */
    val priceStatus: String,
    /** 任一折算价缺失（「估算中」/待定价语义，PRD N3；由事件构造层动态判定）。 */
    val estimated: Boolean,
    /** 卖出逐笔已实现盈亏（基础法币；非卖出行 = null）。 */
    val realizedPnlFiat: BigDecimal?,
)

/** 列表筛选（PRD §9.6：按币种/交易所/类型筛选和搜索；默认交易时间降序）。 */
data class TxFilter(
    /** 币种符号（匹配交易对任一腿，大小写不敏感）。 */
    val coinSymbol: String? = null,
    val exchange: String? = null,
    val side: Side? = null,
    /** 自由文本（交易对/交易所/备注包含）。 */
    val query: String? = null,
)

/** 费率自动计算请求（T7.2：交易所 > 全局费率匹配 + 记录时价折算）。 */
data class FeeQuoteRequest(
    val exchange: String,
    val side: Side,
    val quantity: BigDecimal,
    val price: BigDecimal,
    /** 手续费币种符号（QUOTE/BASE/第三币种角色由服务按交易对判定）。 */
    val feeCoinSymbol: String,
    val baseSymbol: String,
    val quoteSymbol: String,
)

/** 费率自动计算结果（FeeCalculator 产物透传 + 匹配到的费率，UI 展示「已按 … 费率 x% 计算」）。 */
data class FeeQuoteResult(
    val ratePercent: BigDecimal,
    /** 以手续费币种计的数量。 */
    val coinQty: BigDecimal,
)

/**
 * CSV 解析预览（PRD 故事 2.3-3/4：影响摘要 = 新增 N 条、疑似重复 M 条、涉及币种及导入后持仓变化概览；
 * 歧义 ticker 列候选由用户选择固化；格式错误行提示）。
 */
data class CsvPreview(
    val sessionId: String,
    /** 解析出的有效数据行数（不含格式错误行）。 */
    val totalRows: Int,
    val newRows: Int,
    val duplicateRows: Int,
    /** 未解析行（币种未收录且无候选）。 */
    val unresolvedRows: Int,
    val errorRows: List<CsvRowError>,
    /** 预览行（时间/交易对/类型/价格/数量/状态；最多 [CsvPreview.MAX_PREVIEW_ROWS] 条）。 */
    val rows: List<CsvPreviewRow>,
    /** 歧义 ticker -> 候选（名称展示；用户选择后固化为 MANUAL 映射）。 */
    val ambiguous: List<AmbiguousTicker>,
    /** 涉及币种及导入后持仓变化概览（symbol -> 变化量，如 "BTC +1.5000"）。 */
    val affectedCoins: List<CoinPositionDelta>,
    /** 导入后将出现/扩大的负持仓币种（「持仓异常」预告，PRD 全局说明「导入路径例外」）。 */
    val anomalousCoins: List<String>,
) {
    companion object {
        const val MAX_PREVIEW_ROWS: Int = 50
    }
}

/** CSV 预览行（[CsvPreview.rows] 元素）。 */
data class CsvPreviewRow(
    val rowKey: String,
    val time: Instant,
    val pair: String,
    val side: Side,
    val price: BigDecimal,
    val quantity: BigDecimal,
    /** IMPORT = 待导入新行；DUPLICATE = 疑似重复（默认排除，用户可勾选导入）；UNRESOLVED = 币种未解析。 */
    val status: CsvRowStatus,
    /** DUPLICATE 行的既有匹配说明（精确=交易所+订单号 / 模糊=时间+交易对+类型+数量+价格）。 */
    val dedupReason: String? = null,
    /** 用户是否选择导入（DUPLICATE 默认 false，UI 可切换）。 */
    val include: Boolean = status == CsvRowStatus.IMPORT,
)

enum class CsvRowStatus { IMPORT, DUPLICATE, UNRESOLVED }

/** CSV 格式错误行（逐条提示，PRD 故事 2.3-4）。 */
data class CsvRowError(val line: Int, val reason: String)

/** 歧义 ticker 及候选（消歧规则④：UI 列出候选由用户选择，固化 MANUAL——共享规范 §6）。 */
data class AmbiguousTicker(
    /** 消歧键：exchange + 资产符号（CSV 内回填选择结果的主键）。 */
    val exchange: String,
    val asset: String,
    /** 候选展示（cg_id -> "symbol · name"）。 */
    val candidates: Map<String, String>,
)

/** 币种持仓变化概览（导入影响摘要）。 */
data class CoinPositionDelta(val symbol: String, val deltaQuantity: BigDecimal)

/** CSV 确认导入汇总（PRD 故事 2.3-5：批量导入并正确更新持仓与成本）。 */
data class CsvImportSummary(
    val imported: Int,
    val duplicatesSkipped: Int,
    val unresolvedSkipped: Int,
    /** 导入后负持仓币种（按「持仓异常」标记处理，引导补录增资/校准）。 */
    val anomalousCoins: List<String>,
)

/** 手动记账校验错误码（api-contracts §4 映射：V5/V7 同构/V9）。 */
enum class LedgerErrorCode {
    /** 买入时计价币种余额不足（V5：「XX 余额不足，请先记录转入（增资）」）。 */
    INSUFFICIENT_BALANCE,

    /** 卖出超出当时持仓（V7 同构：「XX 持仓不足，无法卖出」）。 */
    INSUFFICIENT_POSITION,

    /** 编辑/删除导致任一时刻负持仓（V9：阻止并提示冲突原因）。 */
    REPLAY_CONFLICT,
}

/**
 * 手动记账校验失败（表单下方/弹窗红色提示，阻止提交；[coinSymbol] 已解析为展示符号）。
 *
 * [conflictAt]/[conflictSide]/[conflictPair]/[conflictQuantity] 为 REPLAY_CONFLICT 的定位上下文
 * （冲突记录时间/方向/交易对/数量）——2026-09-08 修复轮：删除一笔买入会被其后的卖出拦住，
 * 只报「持仓为负」用户无法定位是哪条记录冲突，故把冲突记录信息带到 UI。
 */
@Suppress("LongParameterList") // 校验错误 + 冲突定位上下文（时间/方向/交易对/数量），语义内聚
class LedgerValidationException(
    val code: LedgerErrorCode,
    val coinSymbol: String,
    /** 短缺额（买入余额/卖出持仓差；REPLAY_CONFLICT 可为 null）。 */
    val shortage: BigDecimal? = null,
    val conflictAt: java.time.Instant? = null,
    val conflictSide: com.wuzhufolio.domain.engine.Side? = null,
    val conflictPair: String? = null,
    val conflictQuantity: BigDecimal? = null,
) : RuntimeException(code.name + ": " + coinSymbol)

/**
 * 币种符号无法唯一解析（未收录/歧义未选——消歧规则④需用户选择固化；表单红色提示）。
 *
 * [kind] = 类型化原因（M12 T12.4 勘误）：此前 UI 只能靠 `reason` 的中文正文判分支
 * （`reason.contains("歧义")`），既把界面逻辑绑在数据层文案上，也让英文档无法独立成句。
 * [reason] 保留为中文正文（zh 档逐字沿用，含候选数等动态信息）；[kind] 供 UI 判分支与 en 档成句。
 */
class CoinResolutionException(
    val symbol: String,
    val reason: String,
    val kind: CoinResolutionKind = CoinResolutionKind.UNKNOWN,
) : RuntimeException("coin not resolvable: " + symbol + " (" + reason + ")")

/** 币种解析失败原因（UI 分支与 en 档成句输入；zh 档仍用 [CoinResolutionException.reason] 正文）。 */
enum class CoinResolutionKind {
    /** 歧义（同名资产多个，需用户从候选中选择）。 */
    AMBIGUOUS,

    /** 目录未收录。 */
    NOT_FOUND,

    /** 法币计价无孪生稳定币映射（MVP 不支持第三币种）。 */
    FIAT_UNSUPPORTED,

    /** 用户点选的候选已不存在/已删除。 */
    CANDIDATE_GONE,

    /** 未分类（防御默认）。 */
    UNKNOWN,
}
