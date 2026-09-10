package com.wuzhufolio.ui.ledger

import com.wuzhufolio.domain.ledger.LedgerErrorCode
import com.wuzhufolio.domain.ledger.LedgerValidationException
import com.wuzhufolio.ui.i18n.ledgerStrings

/**
 * 交易管理页文案（M7 · T7.1–T7.4 · 对齐 PRD §9.6/9.7、故事 2.1/2.3/7.1、interaction V1–V5/V9、
 * 原型 wuzhufolio-light.html 交易页/表单/CSV 弹窗逐字口径）。
 *
 * M12 T12.4 i18n：原 `const val` 常量一律改写为**动态取值属性**（成员名与签名不变，调用点零改动），
 * 文案实体在 [com.wuzhufolio.ui.i18n.LedgerStrings]；zh 档与改造前逐字一致。
 */
@Suppress("TooManyFunctions") // 交易域文案门面（原 const 成员一一保留，调用点零改动）
object TransactionCopy {

    val PAGE_TITLE: String get() = ledgerStrings.transactionTitle
    val PAGE_SUB: String get() = ledgerStrings.transactionSubtitle

    /** 已实现盈亏为空（"--"）的口径说明（GUI 走查修复轮：回答「为何部分卖出无盈亏」）。 */
    val PAGE_SUB_HINT: String get() = ledgerStrings.transactionSubtitleHint

    // 命令区（PRD §9.6）
    val BTN_ADD: String get() = ledgerStrings.btnAddTransaction
    val BTN_EDIT: String get() = ledgerStrings.btnEditTransaction
    val BTN_DELETE: String get() = ledgerStrings.btnDeleteTransaction
    val BTN_IMPORT: String get() = ledgerStrings.btnImportTransactions
    val SEARCH_PLACEHOLDER: String get() = ledgerStrings.searchTransactionPlaceholder
    val FILTER_ALL: String get() = ledgerStrings.filterAllTypes
    val FILTER_BUY: String get() = ledgerStrings.sideBuy
    val FILTER_SELL: String get() = ledgerStrings.sideSell

    /** 类型筛选标题（交易页与资金页同形）。 */
    val FILTER_TYPE_LABEL: String get() = ledgerStrings.filterByType

    // 列头
    val COL_PAIR: String get() = ledgerStrings.colPair
    val COL_SIDE: String get() = ledgerStrings.colSide
    val COL_PRICE: String get() = ledgerStrings.colPrice
    val COL_QTY: String get() = ledgerStrings.colQty
    val COL_FEE: String get() = ledgerStrings.colFee
    val COL_TOTAL: String get() = ledgerStrings.colTotal
    val COL_EXCHANGE: String get() = ledgerStrings.colExchange
    val COL_TIME: String get() = ledgerStrings.colTime
    val COL_REALIZED: String get() = ledgerStrings.colRealizedPnl
    val COL_ACTIONS: String get() = ledgerStrings.colActions

    val EMPTY: String get() = ledgerStrings.emptyRecords
    val EMPTY_FILTERED: String get() = ledgerStrings.emptyFiltered

    /** 「估算中」角标（行情价缺失时的估算标注）。 */
    val ESTIMATING: String get() = ledgerStrings.estimating

    /** 删除确认框确认按钮。 */
    val CONFIRM_DELETE: String get() = ledgerStrings.confirmDelete

    // 交易表单（PRD §9.7）
    val FORM_TITLE_ADD: String get() = ledgerStrings.formTitleAddTx
    val FORM_TITLE_EDIT: String get() = ledgerStrings.formTitleEditTx
    val LABEL_EXCHANGE: String get() = ledgerStrings.labelExchange
    val LABEL_PAIR: String get() = ledgerStrings.labelPair

    /** 「交易对（基础币）」括号后缀（与 [LABEL_PAIR] 拼接）。 */
    val LABEL_PAIR_BASE: String get() = ledgerStrings.labelPairBaseSuffix

    /** 计价币（目录可检索）标签。 */
    val LABEL_QUOTE: String get() = ledgerStrings.labelQuoteSearchable
    val LABEL_SIDE: String get() = ledgerStrings.labelSide
    val LABEL_PRICE: String get() = ledgerStrings.labelPrice
    val LABEL_QTY: String get() = ledgerStrings.labelQty
    val LABEL_FEE: String get() = ledgerStrings.labelFee

    /** 「手续费（可为 0）」括号后缀（与 [LABEL_FEE] 拼接）。 */
    val LABEL_FEE_OPTIONAL: String get() = ledgerStrings.labelFeeOptionalSuffix
    val LABEL_FEE_CURRENCY: String get() = ledgerStrings.labelFeeCurrency

    /** 自定义手续费币种标签。 */
    val LABEL_FEE_CUSTOM: String get() = ledgerStrings.labelCustomFeeCurrency
    val LABEL_TIME: String get() = ledgerStrings.labelTxTime
    val LABEL_NOTES: String get() = ledgerStrings.labelNotes

    /** 可选字段占位（备注等）。 */
    val PLACEHOLDER_OPTIONAL: String get() = ledgerStrings.placeholderOptional
    val PAIR_PLACEHOLDER: String get() = ledgerStrings.pairPlaceholder
    val PAIR_HINT: String get() = ledgerStrings.pairHint
    val SIDE_BUY: String get() = ledgerStrings.sideBuy
    val SIDE_SELL: String get() = ledgerStrings.sideSell
    val FEE_ROLE_QUOTE: String get() = ledgerStrings.feeRoleQuote
    val FEE_ROLE_BASE: String get() = ledgerStrings.feeRoleBase
    val FEE_ROLE_CUSTOM: String get() = ledgerStrings.feeRoleCustom
    val CUSTOM_FEE_HINT: String get() = ledgerStrings.customFeeHint
    val AUTO_FEE_BTN: String get() = ledgerStrings.autoFeeButton
    val TOTAL_LABEL: String get() = ledgerStrings.totalLabel
    val BTN_CANCEL: String get() = ledgerStrings.btnCancel
    val BTN_SAVE: String get() = ledgerStrings.btnSave
    val BTN_CLOSE: String get() = ledgerStrings.btnClose
    val BTN_EDIT_ROW: String get() = ledgerStrings.btnEdit
    val BTN_DELETE_ROW: String get() = ledgerStrings.btnDelete

    /** 「当前：<手续费币种>」行。 */
    fun currentFee(symbol: String): String = ledgerStrings.currentFee(symbol)

    // 校验（interaction V1–V4）
    val V1_PRICE: String get() = ledgerStrings.v1Price
    val V1_QTY: String get() = ledgerStrings.v1Qty
    val V2_FEE: String get() = ledgerStrings.v2Fee
    val V3_FEE_CURRENCY: String get() = ledgerStrings.v3FeeCurrency
    val V4_REQUIRED: String get() = ledgerStrings.v4Required

    // 结果与提示
    val SAVE_SUCCESS: String get() = ledgerStrings.txSaveSuccess
    val UPDATE_SUCCESS: String get() = ledgerStrings.txUpdateSuccess
    val SAVE_FAILED: String get() = ledgerStrings.txSaveFailed

    /** 表单校验未通过时的 toast（内联错误可能因窗口较小不在可视区，双通道反馈）。 */
    val VALIDATION_FAILED: String get() = ledgerStrings.validationFailed
    val EDIT_SELECT_ONE: String get() = ledgerStrings.txEditSelectOne
    val DELETE_SELECT_HINT: String get() = ledgerStrings.deleteSelectHint

    /** 删除确认文案（N = 条数占位；调用点 `replace("N", count)`）。 */
    val DELETE_CONFIRM: String get() = ledgerStrings.txDeleteConfirmTemplate
    val DELETE_SUCCESS: String get() = ledgerStrings.txDeleteSuccess
    val DELETE_FAILED: String get() = ledgerStrings.txDeleteFailed
    val NOT_FOUND: String get() = ledgerStrings.txNotFound

    // CSV 导入（PRD 故事 2.3）
    val CSV_TITLE: String get() = ledgerStrings.csvTitle
    val CSV_FILE_LABEL: String get() = ledgerStrings.csvFileLabel

    /** 未选择文件占位。 */
    val CSV_NO_FILE: String get() = ledgerStrings.csvNoFile
    val CSV_DOWNLOAD_TEMPLATE: String get() = ledgerStrings.csvDownloadTemplate
    val CSV_FILE_HINT: String get() = ledgerStrings.csvFileHint
    val CSV_PARSE: String get() = ledgerStrings.csvParse
    val CSV_IMPACT: String get() = ledgerStrings.csvImpact

    /** 影响摘要计数行（errors = 0 时省略「格式错误」段）。 */
    fun csvImpactLine(added: Int, duplicates: Int, unresolved: Int, errors: Int): String =
        ledgerStrings.csvImpactLine(added, duplicates, unresolved, errors)

    /** 「持仓变化：…」。 */
    fun csvHoldingsChange(changes: List<String>): String = ledgerStrings.csvHoldingsChange(changes)

    /** 解析预览的持仓异常提示（N = 币种数占位）。 */
    val CSV_ANOMALY_HINT: String get() = ledgerStrings.csvAnomalyHintTemplate

    /** 解析预览的持仓异常提示（带币种列表与语言相关分隔符）。 */
    fun csvAnomalyLine(count: Int, coins: List<String>): String = ledgerStrings.csvAnomalyLine(count, coins)

    val CSV_CHOOSE: String get() = ledgerStrings.csvChoose
    val CSV_NEW: String get() = ledgerStrings.csvNew
    val CSV_DUPLICATE: String get() = ledgerStrings.csvDuplicate
    val CSV_UNRESOLVED: String get() = ledgerStrings.csvUnresolved
    val CSV_AMBIGUOUS_TITLE: String get() = ledgerStrings.csvAmbigTitle

    /** 歧义 ticker 消歧提示（N = 歧义 symbol 数占位）。 */
    val CSV_AMBIGUOUS_HINT: String get() = ledgerStrings.csvAmbigHintTemplate
    val CSV_NO_AMBIGUOUS: String get() = ledgerStrings.csvNoAmbiguous
    val CSV_CONFIRM: String get() = ledgerStrings.csvConfirmImport
    val CSV_SUCCESS: String get() = ledgerStrings.csvSuccessTemplate
    val CSV_ANOMALY_TOAST: String get() = ledgerStrings.csvAnomalyToastTemplate
    val CSV_DUP_HINT: String get() = ledgerStrings.csvDupHint

    /** CSV 预览表头（时间/交易对/类型/价格/数量/去重）。 */
    val CSV_COL_TIME: String get() = ledgerStrings.csvColumnTime
    val CSV_COL_PAIR: String get() = ledgerStrings.csvColumnPair
    val CSV_COL_SIDE: String get() = ledgerStrings.csvColumnSide
    val CSV_COL_PRICE: String get() = ledgerStrings.csvColumnPrice
    val CSV_COL_QTY: String get() = ledgerStrings.csvColumnQty
    val CSV_COL_DEDUP: String get() = ledgerStrings.csvColumnDedup

    /** 解析中/导入中按钮文案（选择文件与确认导入的忙碌态）。 */
    val CSV_PARSING: String get() = ledgerStrings.csvParsing
    val CSV_IMPORTING: String get() = ledgerStrings.csvImporting

    fun csvParseFailed(reason: String): String = ledgerStrings.csvParseFailed(reason)
    fun csvTemplateDownloaded(fileName: String): String = ledgerStrings.csvTemplateDownloaded(fileName)
    fun csvTemplateFailed(reason: String): String = ledgerStrings.csvTemplateFailed(reason)
    fun csvImportFailed(reason: String): String = ledgerStrings.csvImportFailed(reason)

    /** 导入完成 toast（计数为 0 的段自动省略）。 */
    fun csvImported(
        imported: Int,
        duplicatesSkipped: Int,
        unresolvedSkipped: Int,
        anomalousCoins: List<String>,
    ): String = ledgerStrings.csvImported(imported, duplicatesSkipped, unresolvedSkipped, anomalousCoins)

    // 费率自动计算（T7.2）
    val FEE_NO_RULE: String get() = ledgerStrings.feeNoRule
    val FEE_MISSING_PRICE: String get() = ledgerStrings.feeMissingPrice
    val FEE_CALC_FAILED: String get() = ledgerStrings.feeCalcFailed

    /** 自动计算手续费成功 toast（含交易所/方向/费率/币种）。 */
    fun feeCalculated(
        exchange: String,
        side: com.wuzhufolio.domain.engine.Side,
        rate: String,
        quantity: String,
        feeSymbol: String,
    ): String = ledgerStrings.feeCalculated(exchange, side, rate, quantity, feeSymbol)

    /** 违例分类 -> 用户文案（interaction V5 / V7 同构 / V9；api-contracts §4 错误码映射）。 */
    fun validationCopy(code: LedgerErrorCode, coinSymbol: String): String =
        ledgerStrings.txValidation(code, coinSymbol)

    /**
     * 违例分类 -> 用户文案（含冲突记录定位；2026-09-08 修复轮）。
     * REPLAY_CONFLICT 场景（如删除一笔买入、其后的卖出无货可卖）把冲突记录的时间/方向/数量/交易对
     * 带入提示，用户可直接定位要调整的那条记录。
     */
    fun validationCopy(error: LedgerValidationException): String = when (error.code) {
        LedgerErrorCode.INSUFFICIENT_BALANCE,
        LedgerErrorCode.INSUFFICIENT_POSITION,
        -> validationCopy(error.code, error.coinSymbol)
        LedgerErrorCode.REPLAY_CONFLICT -> ledgerStrings.txReplayConflictDetail(
            coinSymbol = error.coinSymbol,
            at = error.conflictAt,
            side = error.conflictSide,
            quantity = error.conflictQuantity,
            pair = error.conflictPair,
        )
    }
}
