package com.wuzhufolio.ui.ledger

import com.wuzhufolio.domain.ledger.CoinResolutionException
import com.wuzhufolio.domain.ledger.LedgerValidationException
import com.wuzhufolio.ui.i18n.ledgerStrings

/**
 * 资金管理页文案（M8 · T8.1–T8.3 · 对齐 PRD 故事 6.1/6.2/6.3、§9.8、全局说明「持仓校准规则」、
 * interaction V6/V7、原型 wuzhufolio-light.html 资金页/增资撤资表单逐字口径）。
 *
 * M12 T12.4 i18n：原 `const val` 常量一律改写为**动态取值属性**（成员名与签名不变，调用点零改动），
 * 文案实体在 [com.wuzhufolio.ui.i18n.LedgerStrings]；zh 档与改造前逐字一致。
 */
object FundsCopy {

    val PAGE_TITLE: String get() = ledgerStrings.fundsTitle
    val PAGE_SUB: String get() = ledgerStrings.fundsSubtitle

    // 总览卡（PRD §9.8 页面上部）
    val CARD_CASH: String get() = ledgerStrings.cardCash
    val CARD_CASH_HINT: String get() = ledgerStrings.cardCashHint
    val CARD_PRINCIPAL: String get() = ledgerStrings.cardPrincipal
    val CARD_PRINCIPAL_HINT_PREFIX: String get() = ledgerStrings.cardPrincipalHintPrefix
    val CARD_PRINCIPAL_HINT_MID: String get() = ledgerStrings.cardPrincipalHintMid

    // 命令区（PRD §9.8 操作区 + 校准入口，见模块记录 M8 §5 GUI 偏差登记）
    val BTN_DEPOSIT: String get() = ledgerStrings.btnDeposit
    val BTN_WITHDRAW: String get() = ledgerStrings.btnWithdraw
    val BTN_EDIT: String get() = ledgerStrings.btnEdit
    val BTN_DELETE: String get() = ledgerStrings.btnDelete
    val BTN_CALIBRATE: String get() = ledgerStrings.btnCalibrate
    val SEARCH_PLACEHOLDER: String get() = ledgerStrings.searchFundsPlaceholder
    val FILTER_TYPE_LABEL: String get() = ledgerStrings.filterByType
    val FILTER_ALL: String get() = ledgerStrings.filterAllTypes
    val FILTER_DEPOSIT: String get() = ledgerStrings.filterDeposit
    val FILTER_WITHDRAW: String get() = ledgerStrings.filterWithdraw
    val FILTER_RECON: String get() = ledgerStrings.filterRecon
    val FILTER_DATE_LABEL: String get() = ledgerStrings.filterByDate
    val DATE_ALL: String get() = ledgerStrings.dateAll
    val DATE_30: String get() = ledgerStrings.date30
    val DATE_90: String get() = ledgerStrings.date90
    val DATE_OLDER: String get() = ledgerStrings.dateOlder

    // 列头（PRD §9.8 列表字段）
    val COL_TYPE: String get() = ledgerStrings.colFundType
    val COL_COIN: String get() = ledgerStrings.colCoin
    val COL_QTY: String get() = ledgerStrings.colQty
    val COL_FIAT: String get() = ledgerStrings.colFiatValue
    val COL_TIME: String get() = ledgerStrings.colDate
    val COL_SOURCE: String get() = ledgerStrings.colSourceDest
    val COL_NOTES: String get() = ledgerStrings.colNotes
    val COL_ACTIONS: String get() = ledgerStrings.colActions
    val TYPE_RECON: String get() = ledgerStrings.filterRecon
    val EMPTY: String get() = ledgerStrings.emptyRecords
    val EMPTY_FILTERED: String get() = ledgerStrings.emptyFiltered
    val RECON_NO_EDIT: String get() = ledgerStrings.reconNoEdit

    /** 「估算中」角标（行情价缺失时的估算标注）。 */
    val ESTIMATING: String get() = ledgerStrings.estimating

    /** 删除确认框确认按钮。 */
    val CONFIRM_DELETE: String get() = ledgerStrings.confirmDelete

    // 增资/撤资表单（PRD §9.8 交互逻辑）
    val FORM_TITLE_DEPOSIT: String get() = ledgerStrings.formTitleDeposit
    val FORM_TITLE_WITHDRAW: String get() = ledgerStrings.formTitleWithdraw
    val LABEL_COIN: String get() = ledgerStrings.labelCoin
    val COIN_PLACEHOLDER: String get() = ledgerStrings.coinPlaceholder
    val COIN_HINT: String get() = ledgerStrings.coinHint
    val LABEL_QTY: String get() = ledgerStrings.labelQty
    val LABEL_TIME: String get() = ledgerStrings.labelDateLocal
    val LABEL_SOURCE: String get() = ledgerStrings.labelSource
    val LABEL_DEST: String get() = ledgerStrings.labelDest
    val LABEL_NOTES: String get() = ledgerStrings.labelNotes

    /** 可选字段占位（来源/去向、备注）。 */
    val PLACEHOLDER_OPTIONAL: String get() = ledgerStrings.placeholderOptional
    val FIAT_PREVIEW_LABEL: String get() = ledgerStrings.fiatPreviewLabel
    val FIAT_PREVIEW_PENDING: String get() = ledgerStrings.fiatPreviewPending
    val FIAT_PREVIEW_ESTIMATED: String get() = ledgerStrings.fiatPreviewEstimated
    val FIAT_INPUT_HINT_PREFIX: String get() = ledgerStrings.fiatInputHint
    val BTN_CANCEL: String get() = ledgerStrings.btnCancel
    val BTN_SAVE: String get() = ledgerStrings.btnSave

    // 校验（interaction V6 + 服务校验反馈）
    val V6_QTY: String get() = ledgerStrings.v6Qty
    val V6_COIN_REQUIRED: String get() = ledgerStrings.v6CoinRequired
    val V6_TIME_REQUIRED: String get() = ledgerStrings.v6TimeRequired
    val VALIDATION_FAILED: String get() = ledgerStrings.validationFailed
    val SAVE_SUCCESS: String get() = ledgerStrings.fundsSaveSuccess
    val UPDATE_SUCCESS: String get() = ledgerStrings.fundsUpdateSuccess
    val SAVE_FAILED: String get() = ledgerStrings.txSaveFailed
    val EDIT_SELECT_ONE: String get() = ledgerStrings.fundsEditSelectOne
    val EDIT_SELECT_RECON: String get() = ledgerStrings.fundsEditSelectRecon
    val DELETE_SELECT_HINT: String get() = ledgerStrings.deleteSelectHint

    /** 删除确认文案（N = 条数占位；调用点 `replace("N", count)`）。 */
    val DELETE_CONFIRM: String get() = ledgerStrings.fundsDeleteConfirmTemplate
    val DELETE_SUCCESS: String get() = ledgerStrings.fundsDeleteSuccess
    val DELETE_FAILED: String get() = ledgerStrings.deleteFailedPrefix
    val NOT_FOUND: String get() = ledgerStrings.fundsNotFound

    // 校准（PRD 故事 4.1-5 / 全局说明「持仓校准规则」）
    val CAL_TITLE: String get() = ledgerStrings.calTitle
    val CAL_COIN_LABEL: String get() = ledgerStrings.labelCoin
    val CAL_COIN_PLACEHOLDER: String get() = ledgerStrings.calCoinPlaceholder
    val CAL_PREPARE: String get() = ledgerStrings.calPrepare
    val CAL_EXECUTE: String get() = ledgerStrings.calExecute
    val CAL_ROW_EXCHANGE: String get() = ledgerStrings.calRowExchange
    val CAL_ROW_KEY: String get() = ledgerStrings.calRowKey
    val CAL_ROW_LOCAL: String get() = ledgerStrings.calRowLocal
    val CAL_ROW_EXCHANGE_QTY: String get() = ledgerStrings.calRowExchangeQty
    val CAL_ROW_DELTA: String get() = ledgerStrings.calRowDelta
    val CAL_ROW_FIAT: String get() = ledgerStrings.calRowFiat
    val CAL_ROW_PRICE: String get() = ledgerStrings.calRowPrice
    val CAL_DIRECTION_POSITIVE: String get() = ledgerStrings.calDirectionPositive
    val CAL_DIRECTION_NEGATIVE: String get() = ledgerStrings.calDirectionNegative
    val CAL_DIRECTION_ZERO: String get() = ledgerStrings.calDirectionZero
    val CAL_NO_DIFF: String get() = ledgerStrings.calNoDiff
    val CAL_SUCCESS: String get() = ledgerStrings.calSuccess
    val CAL_EXECUTING: String get() = ledgerStrings.calExecuting

    /** 校准失败兜底文案（异常无 message 时）。 */
    val CAL_FAILED: String get() = ledgerStrings.calFailed

    /** 候选列表最多展示条数（可滚动；同名资产靠 cg_id 区分）。 */
    const val MAX_CANDIDATES = 12

    /** 已选候选展示前缀（表单内「已选择」行；同名资产靠 cg_id 区分）。 */
    val PICKED_PREFIX: String get() = ledgerStrings.pickedPrefix
    val PICKED_HINT: String get() = ledgerStrings.pickedHint

    /** 候选/已选币种展示标签（同名资产靠 cg_id 区分）。 */
    fun coinLabel(symbol: String, name: String, cgId: String): String =
        ledgerStrings.coinLabel(symbol, name, cgId)

    /** 法币孪生映射提示（如 USD → USDT；M8 修复轮 §8-1 同口径）。 */
    fun fiatHintTwin(fiatCode: String, stableSymbol: String): String =
        ledgerStrings.fiatHintTwin(fiatCode, stableSymbol)

    /** 校准不可执行原因（类型化 Reason -> 本地化文案，不消费数据层中文正文）。 */
    fun calibrationBlocked(reason: com.wuzhufolio.domain.ledger.CalibrationBlockedException.Reason): String =
        ledgerStrings.calibrationBlocked(reason)

    /** 币种解析失败 -> 可操作文案（替代英文异常原文——2026-09-09 GUI 走查修复轮 §8-1）。 */
    fun coinResolutionCopy(error: CoinResolutionException): String = ledgerStrings.coinResolutionFailed(
        symbol = error.symbol,
        reason = error.reason,
        kind = error.kind,
    )

    /** 违例分类 -> 用户文案（interaction V7 / V9；api-contracts §4 错误码映射）。 */
    fun validationCopy(error: LedgerValidationException): String = ledgerStrings.fundsValidation(error)
}
