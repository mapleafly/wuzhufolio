package com.wuzhufolio.domain.ledger

import com.wuzhufolio.domain.engine.NegativeViolation
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.engine.TradeEvent

/**
 * 交易账本用例契约（M7 · api-contracts §3 LedgerService 交易半边；资金半边随 M8 增补）。
 *
 * 编排语义：
 * - 手动新增/编辑/删除（[saveTransaction]/[updateTransaction]/[deleteTransactions]）：
 *   事件构造层构建「含操作/不含操作」双事件列表调 ReplayEngine.validateMutation（M4 §5-5 相对校验——
 *   仅拦「操作新制造」的负边界），违例经 [LedgerValidationException] 上浮（V5/V7 同构/V9 文案映射在 UI 层）；
 * - CSV（[parseCsv]/[confirmCsvImport]）：解析预览（去重/消歧/影响摘要）-> 确认后 LENIENT 导入
 *   （不做余额校验，PRD 全局说明「导入路径例外」；负持仓按「持仓异常」处理）；
 * - 费率自动计算（[feeQuote]）：FeeRateResolver（交易所 > 全局）+ FeeCalculator 三币种基数（T7.2）。
 */
interface TransactionLedgerService {

    /** 交易列表（按交易时间降序，PRD 列表默认排序；筛选见 [TxFilter]）。 */
    suspend fun listTransactions(filter: TxFilter): List<TransactionRow>

    /** 手动新增交易（STRICT 校验：V1-V4 表单规则在 UI 层，V5/V9 重放校验在此；成功返回行 id）。 */
    suspend fun saveTransaction(input: TransactionInput): Long

    /** 编辑交易（同 [saveTransaction] 校验口径；编辑既有记录不改变其来源）。 */
    suspend fun updateTransaction(id: Long, input: TransactionInput)

    /** 批量删除（PRD §9.6 多选删除；每批整体做相对校验，任何新制造负边界 -> 全批阻止）。 */
    suspend fun deleteTransactions(ids: List<Long>)

    /**
     * 自动计算手续费（T7.2）：交易所费率 > 全局默认；无任何规则返回 null
     * （UI 提示「暂无适用费率规则，可手动填写」）；折算所需行情价缺失亦返回 null。
     */
    suspend fun feeQuote(request: FeeQuoteRequest): FeeQuoteResult?

    /** 币种目录检索（表单交易对/自定义手续费币种自动补全，PRD §9.7；归一化输入）。 */
    suspend fun searchCoins(
        query: String,
        limit: Int = 10,
    ): List<com.wuzhufolio.domain.catalog.CatalogCoin>

    /** 解析 CSV 并产出预览（会话暂存，确认前不落库；重复解析生成新会话）。 */
    suspend fun parseCsv(bytes: ByteArray): CsvPreview

    /**
     * 确认导入：[ambiguityChoices] = 用户对歧义 ticker 的选择（键 = "EXCHANGE|ASSET"，值 = 候选 cg_id；
     * 选择即固化 MANUAL 映射，后续自动复用——共享规范 §6 消歧规则④）；[includeRowKeys] = 用户勾选要
     * 导入的疑似重复行（新行默认全导，无需重复传入）。未选择的疑似重复行跳过；
     * 仍未解析（未收录/歧义未选）的行跳过并计数。
     */
    suspend fun confirmCsvImport(
        sessionId: String,
        ambiguityChoices: Map<String, String> = emptyMap(),
        includeRowKeys: Set<String> = emptySet(),
    ): CsvImportSummary

    /** 标准 CSV 模板内容（含表头与字段说明注释行；UI 落到用户选择路径）。 */
    fun csvTemplateCsv(): String
}

/**
 * 重放违例分类（纯规则 · V5/V7 同构/V9 文案路由输入；api-contracts §4 错误码映射）。
 *
 * 相对校验（M4 §5-5）拦截的是「操作新制造」的负边界：
 * - 违例币种 = 本笔买入的计价腿 且 违例位点 = 本笔事件 -> 买入余额不足（V5）；
 * - 违例币种 = 本笔卖出的基础腿 且 违例位点 = 本笔事件 -> 持仓不足（V7 同构文案）；
 * - 其余（编辑/删除/批量操作使**其他**记录位点转负）-> 重放冲突（V9，提示冲突原因）。
 */
object ReplayConflictClassifier {

    fun classify(violation: NegativeViolation, operated: TradeEvent?): LedgerErrorCode = when {
        operated != null && violation.eventId == operated.id ->
            when {
                operated.side == Side.BUY && violation.coinId == operated.quoteCoin ->
                    LedgerErrorCode.INSUFFICIENT_BALANCE
                operated.side == Side.SELL && violation.coinId == operated.baseCoin ->
                    LedgerErrorCode.INSUFFICIENT_POSITION
                else -> LedgerErrorCode.REPLAY_CONFLICT
            }
        else -> LedgerErrorCode.REPLAY_CONFLICT
    }
}
