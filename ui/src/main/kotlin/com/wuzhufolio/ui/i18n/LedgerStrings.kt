package com.wuzhufolio.ui.i18n

import com.wuzhufolio.domain.ledger.CalibrationBlockedException
import com.wuzhufolio.domain.ledger.CoinResolutionKind
import com.wuzhufolio.domain.ledger.FeeRulePolicy

import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.LedgerErrorCode
import com.wuzhufolio.domain.ledger.LedgerValidationException
import java.math.BigDecimal
import java.time.Instant

/**
 * 交易与资金模块文案（M7 交易管理 / M8 资金管理 / M10 设置页·手续费；M12 T12.4 zh/en 双档）。
 *
 * 目录约定同 [CommonStrings] 头注释：接口 + `Zh`/`En` 两个实现 + `ledgerStrings` 访问器；
 * 既有 `TransactionCopy` / `FundsCopy` 的常量成员就地改写为**动态取值属性**（成员名与签名不变，
 * 调用点零改动），带参数的文案一律写成接口函数。
 *
 * 词条来源 = PRD §9.6/§9.7/§9.8/§9.11 + interaction V1–V9 + 原型 `wuzhufolio-light.html`
 * 交易页/交易表单/CSV 弹窗/资金页/增资撤资表单/校准弹窗逐字口径。
 *
 * - zh 档与改造前**逐字一致**（既有 UI 测试以中文文案为断言锚，不得改动）；
 * - en 档用 PRD 术语（portfolio / position / cost basis / realized P&L / deposit / withdrawal /
 *   calibration / fee rate / transaction pair / quote currency / base currency），标签从简；
 * - 带 `Template` 后缀的词条保留 `N` 占位（调用点 `replace("N", n)`，测试同名成员为锚）；
 * - 数值与时间一律走 [WzFormat]（本表不自行拼装时间格式）。
 */
interface LedgerStrings {

    // ---------- 交易管理页（PRD §9.6） ----------

    val transactionTitle: String
    val transactionSubtitle: String
    val transactionSubtitleHint: String
    val btnAddTransaction: String
    val btnEditTransaction: String
    val btnDeleteTransaction: String
    val btnImportTransactions: String
    val searchTransactionPlaceholder: String

    /** 列表类型筛选标题（交易页与资金页同形）。 */
    val filterByType: String
    val filterAllTypes: String
    val sideBuy: String
    val sideSell: String

    val colPair: String
    val colSide: String
    val colPrice: String
    val colQty: String
    val colFee: String
    val colTotal: String
    val colExchange: String
    val colTime: String
    val colRealizedPnl: String
    val colActions: String
    val colNotes: String

    val emptyRecords: String
    val emptyFiltered: String

    /** 「估算中」角标（交易页/资金页列表行；行情价缺失时为估算值）。 */
    val estimating: String

    /** 删除确认框确认按钮。 */
    val confirmDelete: String

    // ---------- 交易表单（PRD §9.7） ----------

    val formTitleAddTx: String
    val formTitleEditTx: String
    val labelExchange: String
    val labelPair: String

    /** 「交易对（基础币）」括号后缀（与 [labelPair] 拼接，故含前导空格差异由各档自持）。 */
    val labelPairBaseSuffix: String
    val labelQuoteSearchable: String
    val labelSide: String
    val labelPrice: String
    val labelQty: String
    val labelFee: String

    /** 「手续费（可为 0）」括号后缀。 */
    val labelFeeOptionalSuffix: String
    val labelFeeCurrency: String
    val labelCustomFeeCurrency: String
    val labelTxTime: String
    val labelNotes: String

    /** 可选字段占位（备注/来源去向）。 */
    val placeholderOptional: String
    val pairPlaceholder: String
    val pairHint: String
    val feeRoleQuote: String
    val feeRoleBase: String
    val feeRoleCustom: String
    val customFeeHint: String
    val autoFeeButton: String
    val totalLabel: String

    /** 「当前：<手续费币种>」行（空输入回落计价币种）。 */
    fun currentFee(symbol: String): String

    val btnCancel: String
    val btnSave: String
    val btnClose: String
    val btnEdit: String
    val btnDelete: String

    // ---------- 交易校验 / 结果提示（interaction V1–V5/V9） ----------

    val v1Price: String
    val v1Qty: String
    val v2Fee: String
    val v3FeeCurrency: String
    val v4Required: String
    val txSaveSuccess: String
    val txUpdateSuccess: String
    val txSaveFailed: String
    val validationFailed: String
    val txEditSelectOne: String
    val deleteSelectHint: String

    /** 交易删除确认（N = 条数占位）。 */
    val txDeleteConfirmTemplate: String
    val txDeleteSuccess: String
    val txDeleteFailed: String
    val txNotFound: String

    // ---------- 费率自动计算（T7.2） ----------

    val feeNoRule: String
    val feeMissingPrice: String
    val feeCalcFailed: String

    /** 「已按 <交易所> <买入/卖出> 费率 <x>% 计算：<数量> <币种>」。 */
    fun feeCalculated(exchange: String, side: Side, rate: String, quantity: String, feeSymbol: String): String

    // ---------- 违例分类 -> 用户文案 ----------

    /** 违例分类 -> 用户文案（interaction V5 / V7 同构 / V9；api-contracts §4 错误码映射）。 */
    fun txValidation(code: LedgerErrorCode, coinSymbol: String): String

    /**
     * REPLAY_CONFLICT 明细文案（含冲突记录定位；2026-09-08 修复轮）：
     * 把冲突记录的时间/方向/数量/交易对带入提示，用户可直接定位要调整的那条记录。
     */
    fun txReplayConflictDetail(
        coinSymbol: String,
        at: Instant?,
        side: Side?,
        quantity: BigDecimal?,
        pair: String?,
    ): String

    // ---------- CSV 导入（PRD 故事 2.3） ----------

    val csvTitle: String
    val csvFileLabel: String
    val csvNoFile: String
    val csvParsing: String
    val csvImporting: String
    val csvDownloadTemplate: String
    val csvFileHint: String
    val csvParse: String
    val csvImpact: String
    val csvChoose: String

    /** 影响摘要计数行（errors = 0 时省略「格式错误」段）。 */
    fun csvImpactLine(added: Int, duplicates: Int, unresolved: Int, errors: Int): String

    /** 「持仓变化：<币种 +数量 · …>」。 */
    fun csvHoldingsChange(changes: List<String>): String

    /** 解析预览的持仓异常提示（N = 币种数占位）。 */
    val csvAnomalyHintTemplate: String

    /** 导入后持仓异常提示（N = 币种数；币种列表分隔符随语言）。 */
    fun csvAnomalyLine(count: Int, coins: List<String>): String

    val csvAmbigTitle: String

    /** 歧义 ticker 消歧提示（N = 歧义 symbol 数占位）。 */
    val csvAmbigHintTemplate: String
    val csvNoAmbiguous: String
    val csvConfirmImport: String
    val csvDupHint: String
    val csvColumnTime: String
    val csvColumnPair: String
    val csvColumnSide: String
    val csvColumnPrice: String
    val csvColumnQty: String
    val csvColumnDedup: String
    val csvNew: String
    val csvDuplicate: String
    val csvUnresolved: String

    /** 导入成功提示（N = 条数占位；历史存量成员，新调用点走 [csvImported]）。 */
    val csvSuccessTemplate: String
    val csvAnomalyToastTemplate: String

    fun csvParseFailed(reason: String): String
    fun csvTemplateDownloaded(fileName: String): String
    fun csvTemplateFailed(reason: String): String
    fun csvImportFailed(reason: String): String

    /** 导入完成 toast（重复/未解析计数为 0 时省略对应段；异常币种列表分隔符随语言）。 */
    fun csvImported(
        imported: Int,
        duplicatesSkipped: Int,
        unresolvedSkipped: Int,
        anomalousCoins: List<String>,
    ): String

    // ---------- 费率设置（M10 T10.1 · PRD §9.11 / §7.2-6.3） ----------

    val feeGlobalTitle: String
    val feeBuyRateLabel: String
    val feeSellRateLabel: String
    val feeSaveGlobalButton: String
    val feeExchangeTitle: String
    val feeExchangeEmpty: String
    val feeSaveExchangeButton: String
    val feeSaveEditButton: String

    /** 交易所规则行「买入 <x>% · 卖出 <y>%」。 */
    fun feeExchangeLine(buy: String, sell: String): String

    val feeEnterBuyRate: String
    val feeEnterSellRate: String
    val feeEnterExchangeName: String

    /** 全局费率保存成功 toast。 */
    fun feeGlobalSaved(buy: String, sell: String): String

    /** 交易所费率新增/覆盖成功 toast（name 已大写）。 */
    fun feeExchangeAdded(name: String, buy: String, sell: String): String

    /** 交易所费率改名保存成功 toast（name 已大写）。 */
    fun feeExchangeUpdated(name: String, buy: String, sell: String): String

    val feeRuleDeleted: String
    val feeSaveFailed: String
    val feeDeleteFailed: String
    val feeCandidateAll: String
    val feeCandidateFiltered: String

    // ---------- 资金管理页（PRD §9.8） ----------

    val fundsTitle: String
    val fundsSubtitle: String
    val cardCash: String
    val cardCashHint: String
    val cardPrincipal: String

    /** 「累计增资 <金额>」前缀与「 − 累计撤资 <金额>」中缀（两段拼接为卡片副标题）。 */
    val cardPrincipalHintPrefix: String
    val cardPrincipalHintMid: String

    val btnDeposit: String
    val btnWithdraw: String
    val btnCalibrate: String
    val searchFundsPlaceholder: String
    val filterByDate: String
    val dateAll: String
    val date30: String
    val date90: String
    val dateOlder: String
    val filterDeposit: String
    val filterWithdraw: String
    val filterRecon: String
    val colFundType: String
    val colCoin: String
    val colFiatValue: String
    val colDate: String
    val colSourceDest: String
    val reconNoEdit: String

    // ---------- 增资/撤资表单（PRD §9.8 交互逻辑） ----------

    val formTitleDeposit: String
    val formTitleWithdraw: String
    val labelCoin: String
    val coinPlaceholder: String
    val coinHint: String
    val labelDateLocal: String
    val labelSource: String
    val labelDest: String
    val fiatPreviewLabel: String
    val fiatPreviewPending: String
    val fiatPreviewEstimated: String
    val fiatInputHint: String

    /** 法币孪生映射提示（如 USDT 白名单内的 USD → USDT）。 */
    fun fiatHintTwin(fiatCode: String, stableSymbol: String): String

    val v6Qty: String
    val v6CoinRequired: String
    val v6TimeRequired: String
    val fundsSaveSuccess: String
    val fundsUpdateSuccess: String
    val fundsEditSelectOne: String
    val fundsEditSelectRecon: String

    /** 资金删除确认（N = 条数占位）。 */
    val fundsDeleteConfirmTemplate: String
    val fundsDeleteSuccess: String

    /** 「删除失败：<原因>」前缀。 */
    val deleteFailedPrefix: String
    val fundsNotFound: String

    /** 违例分类 -> 用户文案（interaction V7 / V9；api-contracts §4 错误码映射）。 */
    fun fundsValidation(error: LedgerValidationException): String

    // ---------- 币种解析（M8 修复轮 §8-1） ----------

    /** 候选/已选币种展示标签（同名资产靠 cg_id 区分）。 */
    fun coinLabel(symbol: String, name: String, cgId: String): String

    /**
     * 币种解析失败 -> 可操作文案（替代英文异常原文——2026-09-09 GUI 走查修复轮 §8-1）。
     * [hasCandidates] = 存在候选列表（领域层 `reason` 含歧义标记时由调用点判定）。
     */
    /**
     * 币种无法唯一解析的提示。[kind] 决定分支与 en 档成句；zh 档沿用数据层 [reason] 正文
     *（含候选数等动态信息，逐字保持 M7/M8 已验收文案）。
     */
    fun coinResolutionFailed(symbol: String, reason: String, kind: CoinResolutionKind): String

    /**
     * 校准不可执行原因（PRD 全局说明「持仓校准规则」；PRD 故事 4.1-5）。
     * 入参为领域层类型化原因——不消费 `CalibrationBlockedException.message`（数据层中文正文），
     * 否则 en 档会漏出中文（M12 T12.4）。
     */
    fun calibrationBlocked(reason: CalibrationBlockedException.Reason): String

    /** 费率输入校验失败文案（入参 = 领域层类型化错误码，见 FeeRulePolicy.Error）。 */
    fun feeRuleInvalid(error: FeeRulePolicy.Error): String

    // ---------- 持仓校准（PRD 故事 4.1-5 / 全局说明「持仓校准规则」） ----------

    val calTitle: String
    val calCoinPlaceholder: String
    val calPrepare: String
    val calExecute: String
    val calExecuting: String
    val calRowExchange: String
    val calRowKey: String
    val calRowLocal: String
    val calRowExchangeQty: String
    val calRowDelta: String
    val calRowFiat: String
    val calRowPrice: String
    val calDirectionPositive: String
    val calDirectionNegative: String
    val calDirectionZero: String
    val calNoDiff: String
    val calSuccess: String
    val calFailed: String

    /** 「已选择：<币种标签>」前缀。 */
    val pickedPrefix: String
    val pickedHint: String
}

object LedgerStringsZh : LedgerStrings {

    // ---------- 交易管理页 ----------
    override val transactionTitle = "交易管理"
    override val transactionSubtitle = "卖出记录行显示该笔已实现盈亏（本地全量重放推导，非交易所原始数据）"
    override val transactionSubtitleHint =
        "「--」= 该笔卖出时无成本基数：更早的买入未导入（API 仅回最近 500 笔，更早历史需 CSV 补录），" +
            "或该币种持仓为负（异常区段）。"
    override val btnAddTransaction = "＋ 添加交易"
    override val btnEditTransaction = "修改交易"
    override val btnDeleteTransaction = "删除交易"
    override val btnImportTransactions = "导入交易 (CSV)"
    override val searchTransactionPlaceholder = "搜索币种 / 交易所"
    override val filterByType = "按类型筛选："
    override val filterAllTypes = "全部类型"
    override val sideBuy = "买入"
    override val sideSell = "卖出"
    override val colPair = "交易对"
    override val colSide = "类型"
    override val colPrice = "价格"
    override val colQty = "数量"
    override val colFee = "手续费"
    override val colTotal = "总价"
    override val colExchange = "交易所"
    override val colTime = "时间"
    override val colRealizedPnl = "已实现盈亏"
    override val colActions = "操作"
    override val colNotes = "备注"
    override val emptyRecords = "暂无记录"
    override val emptyFiltered = "暂无符合条件的记录"
    override val estimating = "估算中"
    override val confirmDelete = "确认删除"

    // ---------- 交易表单 ----------
    override val formTitleAddTx = "添加交易"
    override val formTitleEditTx = "编辑交易"
    override val labelExchange = "交易所"
    override val labelPair = "交易对"
    override val labelPairBaseSuffix = "（基础币）"
    override val labelQuoteSearchable = "计价币（可检索）"
    override val labelSide = "类型"
    override val labelPrice = "价格"
    override val labelQty = "数量"
    override val labelFee = "手续费"
    override val labelFeeOptionalSuffix = "（可为 0）"
    override val labelFeeCurrency = "手续费币种"
    override val labelCustomFeeCurrency = "自定义手续费币种"
    override val labelTxTime = "交易时间（本地时区）"
    override val labelNotes = "备注"
    override val placeholderOptional = "可选"
    override val pairPlaceholder = "输入 BTC / ETH 自动补全"
    override val pairHint = "基于币种目录自动补全；目录未收录可手输并标「无行情」"
    override val feeRoleQuote = "计价币种"
    override val feeRoleBase = "基础币种"
    override val feeRoleCustom = "自定义币种"
    override val customFeeHint = "自定义 = 既非计价币种也非基础币种的第三币种（如 BNB 抵扣手续费）"
    override val autoFeeButton = "自动计算手续费（交易所 > 全局费率）"
    override val totalLabel = "总价（实时）"
    override fun currentFee(symbol: String) = "当前：" + symbol
    override val btnCancel = "取消"
    override val btnSave = "保存"
    override val btnClose = "关闭"
    override val btnEdit = "编辑"
    override val btnDelete = "删除"

    // ---------- 交易校验 / 结果提示 ----------
    override val v1Price = "价格必须大于 0"
    override val v1Qty = "数量必须大于 0"
    override val v2Fee = "手续费必须 ≥ 0"
    override val v3FeeCurrency = "手续费币种不能为空"
    override val v4Required = "必填项不能为空"
    override val txSaveSuccess = "交易已保存 · 已联动持仓并重算成本"
    override val txUpdateSuccess = "交易已更新 · 持仓与成本已重算"
    override val txSaveFailed = "保存失败（详情见日志）"
    override val validationFailed = "请检查表单中的错误提示（红色文字）后再保存"
    override val txEditSelectOne = "修改交易请先勾选一条记录"
    override val deleteSelectHint = "请先勾选记录"
    override val txDeleteConfirmTemplate = "将删除 N 条交易记录并重算持仓成本与盈亏，是否继续？"
    override val txDeleteSuccess = "已删除交易记录 · 持仓与成本已重算"
    override val txDeleteFailed = "删除失败"
    override val txNotFound = "交易记录不存在或已删除"

    // ---------- 费率自动计算 ----------
    override val feeNoRule = "暂无适用费率规则：可在 设置 → 手续费 配置，或手动填写手续费"
    override val feeMissingPrice = "当前行情价缺失，暂无法自动计算手续费"
    override val feeCalcFailed = "自动计算手续费失败"
    override fun feeCalculated(exchange: String, side: Side, rate: String, quantity: String, feeSymbol: String) =
        "已按 " + exchange + " " + (if (side == Side.BUY) sideBuy else sideSell) +
            " 费率 " + rate + "% 计算：" + quantity + " " + feeSymbol

    // ---------- 违例分类 -> 用户文案 ----------
    override fun txValidation(code: LedgerErrorCode, coinSymbol: String): String = when (code) {
        LedgerErrorCode.INSUFFICIENT_BALANCE ->
            coinSymbol + " 余额不足，请先记录转入（增资），或用「导入交易 (CSV)」导入历史记录后再记"
        LedgerErrorCode.INSUFFICIENT_POSITION ->
            coinSymbol + " 持仓不足，无法卖出"
        LedgerErrorCode.REPLAY_CONFLICT ->
            "该操作将导致 " + coinSymbol + " 持仓为负（与已有记录冲突），请先调整相关记录"
    }

    override fun txReplayConflictDetail(
        coinSymbol: String,
        at: Instant?,
        side: Side?,
        quantity: BigDecimal?,
        pair: String?,
    ): String = buildString {
        append("该操作将导致 ").append(coinSymbol).append(" 持仓为负")
        if (at != null) {
            append("：与 ").append(WzFormat.dateTime(at)).append(" 的")
            side?.let { append(if (it == Side.BUY) "买入 " else "卖出 ") }
            quantity?.let { append(it.stripTrailingZeros().toPlainString()).append(" ") }
            pair?.let { append(it).append(" ") }
            append("记录冲突")
        } else {
            append("（与已有记录冲突）")
        }
        append("，请先调整或删除该冲突记录")
    }

    // ---------- CSV 导入 ----------
    override val csvTitle = "导入交易 (CSV)"
    override val csvFileLabel = "CSV 文件"
    override val csvNoFile = "未选择文件"
    override val csvParsing = "解析中…"
    override val csvImporting = "导入中…"
    override val csvDownloadTemplate = "下载标准模板"
    override val csvFileHint = "日期按 UTC 解析 · 交易对以法币计价时按 1:1 归一化（USD→USDT）"
    override val csvParse = "解析并预览"
    override val csvImpact = "影响摘要（解析预览）"
    override val csvChoose = "选择文件"
    override fun csvImpactLine(added: Int, duplicates: Int, unresolved: Int, errors: Int): String =
        "新增 " + added + " 条 · 疑似重复 " + duplicates +
            " 条 · 未解析 " + unresolved + " 条" +
            if (errors > 0) " · 格式错误 " + errors + " 条" else ""
    override fun csvHoldingsChange(changes: List<String>) = "持仓变化：" + changes.joinToString(" · ")
    override val csvAnomalyHintTemplate = "导入后 N 个币种持仓为负（持仓异常），将标记并引导补录增资或校准"
    override fun csvAnomalyLine(count: Int, coins: List<String>): String =
        csvAnomalyHintTemplate.replace("N", count.toString()) + "：" + coins.joinToString("、")
    override val csvAmbigTitle = "歧义 ticker 消歧"
    override val csvAmbigHintTemplate =
        "发现 N 个歧义 symbol：请确认候选（名称 / 市值），选择结果固化到映射表、后续自动复用"
    override val csvNoAmbiguous = "无歧义 symbol"
    override val csvConfirmImport = "确认导入"
    override val csvDupHint = "疑似重复行默认排除，勾选「导入」可按需补录"
    override val csvColumnTime = "时间"
    override val csvColumnPair = "交易对"
    override val csvColumnSide = "类型"
    override val csvColumnPrice = "价格"
    override val csvColumnQty = "数量"
    override val csvColumnDedup = "去重"
    override val csvNew = "新增"
    override val csvDuplicate = "疑似重复"
    override val csvUnresolved = "未解析"
    override val csvSuccessTemplate = "导入完成 · 新增 N 条 · 持仓与成本已重算"
    override val csvAnomalyToastTemplate = "导入完成 · 新增 N 条 · 持仓异常币种："
    override fun csvParseFailed(reason: String) = "CSV 解析失败：" + reason
    override fun csvTemplateDownloaded(fileName: String) = "标准模板已下载：" + fileName
    override fun csvTemplateFailed(reason: String) = "模板下载失败：" + reason
    override fun csvImportFailed(reason: String) = "导入失败：" + reason
    override fun csvImported(
        imported: Int,
        duplicatesSkipped: Int,
        unresolvedSkipped: Int,
        anomalousCoins: List<String>,
    ): String {
        val message = buildString {
            append("导入完成 · 新增 ").append(imported)
            if (duplicatesSkipped > 0) append(" · 跳过重复 ").append(duplicatesSkipped)
            if (unresolvedSkipped > 0) append(" · 未解析跳过 ").append(unresolvedSkipped)
        }
        return if (anomalousCoins.isNotEmpty()) {
            message + " · 持仓异常币种：" + anomalousCoins.joinToString("、") + "（请补录增资或校准）"
        } else {
            message
        }
    }

    // ---------- 费率设置 ----------
    override val feeGlobalTitle = "全局默认费率"
    override val feeBuyRateLabel = "买入费率 %"
    override val feeSellRateLabel = "卖出费率 %"
    override val feeSaveGlobalButton = "保存全局费率"
    override val feeExchangeTitle = "交易所费率（覆盖全局）"
    override val feeExchangeEmpty = "尚未添加交易所费率（可选）"
    override val feeSaveExchangeButton = "添加 / 覆盖交易所费率"
    override val feeSaveEditButton = "保存修改"
    override fun feeExchangeLine(buy: String, sell: String) = "买入 " + buy + "% · 卖出 " + sell + "%"
    override val feeEnterBuyRate = "请输入买入费率"
    override val feeEnterSellRate = "请输入卖出费率"
    override val feeEnterExchangeName = "请填写交易所名称（如 BINANCE）"
    override fun feeGlobalSaved(buy: String, sell: String) =
        "全局默认费率已保存（买入 " + buy + "% / 卖出 " + sell + "%）"
    override fun feeExchangeAdded(name: String, buy: String, sell: String) =
        "已保存 " + name + " 费率（买入 " + buy + "% / 卖出 " + sell + "%）"
    override fun feeExchangeUpdated(name: String, buy: String, sell: String) =
        "已更新 " + name + " 费率（买入 " + buy + "% / 卖出 " + sell + "%）"
    override val feeRuleDeleted = "费率规则已删除"
    override val feeSaveFailed = "保存失败"
    override val feeDeleteFailed = "删除失败"
    override val feeCandidateAll = "候选交易所（点击填入，也可直接输入其他交易所）："
    override val feeCandidateFiltered = "候选（按输入过滤）："

    // ---------- 资金管理页 ----------
    override val fundsTitle = "资金管理"
    override val fundsSubtitle = "增资 / 撤资 / 校准（校准差额视同系统生成的资金操作）"
    override val cardCash = "可用现金余额"
    override val cardCashHint = "现金类币种（稳定币白名单）"
    override val cardPrincipal = "投入本金（净）"
    override val cardPrincipalHintPrefix = "累计增资 "
    override val cardPrincipalHintMid = " − 累计撤资 "
    override val btnDeposit = "＋ 记录增资"
    override val btnWithdraw = "－ 记录撤资"
    override val btnCalibrate = "校准持仓"
    override val searchFundsPlaceholder = "搜索币种 / 来源 / 备注"
    override val filterByDate = "按日期筛选："
    override val dateAll = "全部时间"
    override val date30 = "近 30 天"
    override val date90 = "近 90 天"
    override val dateOlder = "更早"
    override val filterDeposit = "增资"
    override val filterWithdraw = "撤资"
    override val filterRecon = "校准"
    override val colFundType = "类型"
    override val colCoin = "币种"
    override val colFiatValue = "折算金额"
    override val colDate = "日期"
    override val colSourceDest = "来源 / 去向"
    override val reconNoEdit = "校准记录不可编辑，仅可删除（PRD §10-8）"

    // ---------- 增资/撤资表单 ----------
    override val formTitleDeposit = "记录增资"
    override val formTitleWithdraw = "记录撤资"
    override val labelCoin = "币种"
    override val coinPlaceholder = "USDT / BTC"
    override val coinHint = "基于币种目录自动补全；限稳定币或其他加密货币；默认取基础法币对应稳定币"
    override val labelDateLocal = "日期时间（本地时区）"
    override val labelSource = "来源（增资）"
    override val labelDest = "去向（撤资）"
    override val fiatPreviewLabel = "折算基础法币（记录时行情价）"
    override val fiatPreviewPending = "待定价（暂无行情价，保存后联网自动补算）"
    override val fiatPreviewEstimated = "（估算）"
    override val fiatInputHint = "法币不入账：请改为记录兑换后实际到账的稳定币"
    override fun fiatHintTwin(fiatCode: String, stableSymbol: String) =
        fiatInputHint + "（" + fiatCode + " → " + stableSymbol + "）"
    override val v6Qty = "数量必须大于 0"
    override val v6CoinRequired = "币种必填"
    override val v6TimeRequired = "日期必填"
    override val fundsSaveSuccess = "资金记录已保存 · 持仓与指标已重算"
    override val fundsUpdateSuccess = "资金记录已更新 · 投入本金与 ROI 已重算"
    override val fundsEditSelectOne = "编辑请先勾选一条资金记录"
    override val fundsEditSelectRecon = "校准记录不可编辑，仅可删除"
    override val fundsDeleteConfirmTemplate = "将删除 N 条资金记录并重算投入本金与 ROI，是否继续？"
    override val fundsDeleteSuccess = "已删除资金记录 · 投入本金与 ROI 已重算"
    override val deleteFailedPrefix = "删除失败："
    override val fundsNotFound = "资金记录不存在或已删除"
    override fun fundsValidation(error: LedgerValidationException): String = when (error.code) {
        LedgerErrorCode.INSUFFICIENT_POSITION -> error.coinSymbol + " 持仓不足，无法撤资"
        LedgerErrorCode.INSUFFICIENT_BALANCE -> error.coinSymbol + " 余额不足（请先记录增资）"
        LedgerErrorCode.REPLAY_CONFLICT ->
            "该操作将导致 " + error.coinSymbol + " 持仓为负（该笔记录可能已被后续记录依赖，" +
                "如增资已被买入消耗），请先调整相关记录"
    }

    // ---------- 币种解析 ----------
    override fun coinLabel(symbol: String, name: String, cgId: String) =
        symbol + " · " + name + "（" + cgId + "）"

    override fun coinResolutionFailed(symbol: String, reason: String, kind: CoinResolutionKind): String =
        "币种「" + symbol + "」无法唯一确定：" + reason +
            if (kind == CoinResolutionKind.AMBIGUOUS) "——请从输入框下方候选列表点选后再保存" else ""

    override fun feeRuleInvalid(error: FeeRulePolicy.Error): String = when (error) {
        FeeRulePolicy.Error.EMPTY -> "请输入费率（百分比，如 0.1）"
        FeeRulePolicy.Error.NEGATIVE -> "费率不能为负"
        FeeRulePolicy.Error.TOO_LARGE -> "费率不能超过 100%"
    }

    override fun calibrationBlocked(reason: CalibrationBlockedException.Reason): String = when (reason) {
        CalibrationBlockedException.Reason.NO_RECORDS -> "该币种尚无交易记录，暂无可校准依据"
        CalibrationBlockedException.Reason.MULTI_SOURCE -> "该币种存在多个数据来源，无法以单一交易所余额校准"
        CalibrationBlockedException.Reason.NO_EXCHANGE_KEY -> "尚未保存该交易所的只读 API 密钥，无法读取交易所余额"
        CalibrationBlockedException.Reason.NO_MARKET_PRICE -> "行情完全不可用，暂不允许执行校准"
        CalibrationBlockedException.Reason.BALANCE_FETCH_FAILED -> "交易所余额获取失败，请检查网络或密钥后重试"
    }

    // ---------- 持仓校准 ----------
    override val calTitle = "以交易所余额校准持仓"
    override val calCoinPlaceholder = "输入币种（如 BTC）"
    override val calPrepare = "生成校准预览"
    override val calExecute = "确认校准"
    override val calExecuting = "执行中…"
    override val calRowExchange = "依据交易所"
    override val calRowKey = "依据密钥"
    override val calRowLocal = "本地持仓"
    override val calRowExchangeQty = "交易所余额"
    override val calRowDelta = "差额"
    override val calRowFiat = "差额折算"
    override val calRowPrice = "校准时市价"
    override val calDirectionPositive = "正差额（视同系统增资，按校准时市价计入成本与累计增资）"
    override val calDirectionNegative = "负差额（视同系统撤资，按当时平均成本移出并计入累计撤资）"
    override val calDirectionZero = "无差额：本地持仓已与交易所一致，无需校准"
    override val calNoDiff = "本地持仓已与交易所余额一致，未生成校准记录"
    override val calSuccess = "校准完成 · 已生成校准记录并留痕同步日志"
    override val calFailed = "校准失败"
    override val pickedPrefix = "已选择："
    override val pickedHint = "点选候选后保存将使用所选币种（同名资产请以括号内的 CoinGecko id 区分）"
}

object LedgerStringsEn : LedgerStrings {

    // ---------- Transactions page ----------
    override val transactionTitle = "Transactions"
    override val transactionSubtitle = "Sell rows show realized P&L (derived by full local replay, not exchange data)"
    override val transactionSubtitleHint =
        "\"--\" means no cost basis for that sell: earlier buys were not imported (the API returns only the " +
            "latest 500, backfill older history via CSV), or the position is negative (anomaly range)."
    override val btnAddTransaction = "+ Add transaction"
    override val btnEditTransaction = "Edit transaction"
    override val btnDeleteTransaction = "Delete transaction"
    override val btnImportTransactions = "Import transactions (CSV)"
    override val searchTransactionPlaceholder = "Search coin / exchange"
    override val filterByType = "Filter by type:"
    override val filterAllTypes = "All types"
    override val sideBuy = "Buy"
    override val sideSell = "Sell"
    override val colPair = "Pair"
    override val colSide = "Side"
    override val colPrice = "Price"
    override val colQty = "Quantity"
    override val colFee = "Fee"
    override val colTotal = "Total"
    override val colExchange = "Exchange"
    override val colTime = "Time"
    override val colRealizedPnl = "Realized P&L"
    override val colActions = "Actions"
    override val colNotes = "Notes"
    override val emptyRecords = "No records"
    override val emptyFiltered = "No matching records"
    override val estimating = "Estimated"
    override val confirmDelete = "Delete"

    // ---------- Transaction form ----------
    override val formTitleAddTx = "Add transaction"
    override val formTitleEditTx = "Edit transaction"
    override val labelExchange = "Exchange"
    override val labelPair = "Pair"
    override val labelPairBaseSuffix = " (base)"
    override val labelQuoteSearchable = "Quote currency (searchable)"
    override val labelSide = "Side"
    override val labelPrice = "Price"
    override val labelQty = "Quantity"
    override val labelFee = "Fee"
    override val labelFeeOptionalSuffix = " (may be 0)"
    override val labelFeeCurrency = "Fee currency"
    override val labelCustomFeeCurrency = "Custom fee currency"
    override val labelTxTime = "Time (local zone)"
    override val labelNotes = "Notes"
    override val placeholderOptional = "Optional"
    override val pairPlaceholder = "Type BTC / ETH to autocomplete"
    override val pairHint = "Autocompleted from the coin catalog; unlisted coins can be typed in and are flagged as \"no market data\""
    override val feeRoleQuote = "Quote currency"
    override val feeRoleBase = "Base currency"
    override val feeRoleCustom = "Custom currency"
    override val customFeeHint = "Custom = a third currency that is neither the quote nor the base currency (e.g. BNB paying the fee)"
    override val autoFeeButton = "Auto-calculate fee (exchange > global rate)"
    override val totalLabel = "Total (live)"
    override fun currentFee(symbol: String) = "Current: " + symbol
    override val btnCancel = "Cancel"
    override val btnSave = "Save"
    override val btnClose = "Close"
    override val btnEdit = "Edit"
    override val btnDelete = "Delete"

    // ---------- Validation / result toasts ----------
    override val v1Price = "Price must be greater than 0"
    override val v1Qty = "Quantity must be greater than 0"
    override val v2Fee = "Fee must be ≥ 0"
    override val v3FeeCurrency = "Fee currency is required"
    override val v4Required = "Required"
    override val txSaveSuccess = "Transaction saved · position and cost basis recomputed"
    override val txUpdateSuccess = "Transaction updated · position and cost basis recomputed"
    override val txSaveFailed = "Save failed (see logs for details)"
    override val validationFailed = "Fix the red field errors before saving"
    override val txEditSelectOne = "Select one transaction to edit"
    override val deleteSelectHint = "Select a record first"
    override val txDeleteConfirmTemplate = "Delete N transaction record(s) and recompute cost basis and P&L. Continue?"
    override val txDeleteSuccess = "Transaction deleted · position and cost basis recomputed"
    override val txDeleteFailed = "Delete failed"
    override val txNotFound = "Transaction not found or already deleted"

    // ---------- Fee auto-calculation ----------
    override val feeNoRule = "No applicable fee rule: configure one in Settings → Fees, or enter the fee manually"
    override val feeMissingPrice = "Market price unavailable — cannot auto-calculate the fee"
    override val feeCalcFailed = "Fee auto-calculation failed"
    override fun feeCalculated(exchange: String, side: Side, rate: String, quantity: String, feeSymbol: String) =
        "Calculated at " + exchange + " " + (if (side == Side.BUY) "buy" else "sell") +
            " fee rate " + rate + "%: " + quantity + " " + feeSymbol

    // ---------- Error code -> user copy ----------
    override fun txValidation(code: LedgerErrorCode, coinSymbol: String): String = when (code) {
        LedgerErrorCode.INSUFFICIENT_BALANCE ->
            coinSymbol + " balance is insufficient: record a deposit first, or import history via " +
                "\"Import transactions (CSV)\""
        LedgerErrorCode.INSUFFICIENT_POSITION ->
            coinSymbol + " position is insufficient — cannot sell"
        LedgerErrorCode.REPLAY_CONFLICT ->
            "This would make the " + coinSymbol + " position negative (conflicts with existing records); " +
                "adjust the related records first"
    }

    override fun txReplayConflictDetail(
        coinSymbol: String,
        at: Instant?,
        side: Side?,
        quantity: BigDecimal?,
        pair: String?,
    ): String = buildString {
        append("This would make the ").append(coinSymbol).append(" position negative")
        if (at != null) {
            append(": conflicts with the")
            side?.let { append(if (it == Side.BUY) " buy" else " sell") }
            append(" record")
            val amount = quantity?.stripTrailingZeros()?.toPlainString()
            val pairText = pair
            if (amount != null || pairText != null) {
                append(" of")
                amount?.let { append(" ").append(it) }
                pairText?.let { append(" ").append(it) }
            }
            append(" at ").append(WzFormat.dateTime(at))
        } else {
            append(" (conflicts with existing records)")
        }
        append("; adjust or delete that conflicting record first")
    }

    // ---------- CSV import ----------
    override val csvTitle = "Import transactions (CSV)"
    override val csvFileLabel = "CSV file"
    override val csvNoFile = "No file selected"
    override val csvParsing = "Parsing…"
    override val csvImporting = "Importing…"
    override val csvDownloadTemplate = "Download template"
    override val csvFileHint = "Dates are parsed as UTC · fiat-quoted pairs are normalized 1:1 (USD→USDT)"
    override val csvParse = "Parse and preview"
    override val csvImpact = "Impact summary (preview)"
    override val csvChoose = "Choose file"
    override fun csvImpactLine(added: Int, duplicates: Int, unresolved: Int, errors: Int): String =
        "New " + added + " · Duplicates " + duplicates +
            " · Unresolved " + unresolved +
            if (errors > 0) " · Malformed " + errors else ""
    override fun csvHoldingsChange(changes: List<String>) = "Holdings change: " + changes.joinToString(" · ")
    override val csvAnomalyHintTemplate =
        "N coins have a negative position after import (position anomaly); they will be flagged and you " +
            "will be prompted to backfill deposits or run a calibration"
    override fun csvAnomalyLine(count: Int, coins: List<String>): String =
        csvAnomalyHintTemplate.replace("N", count.toString()) + ": " + coins.joinToString(", ")
    override val csvAmbigTitle = "Resolve ambiguous tickers"
    override val csvAmbigHintTemplate =
        "Found N ambiguous symbols: confirm the candidate (name / market cap); your choice is written to " +
            "the mapping table and reused"
    override val csvNoAmbiguous = "No ambiguous symbols"
    override val csvConfirmImport = "Import"
    override val csvDupHint = "Suspected duplicates are excluded by default; tick \"import\" to backfill them"
    override val csvColumnTime = "Time"
    override val csvColumnPair = "Pair"
    override val csvColumnSide = "Side"
    override val csvColumnPrice = "Price"
    override val csvColumnQty = "Quantity"
    override val csvColumnDedup = "Dedup"
    override val csvNew = "New"
    override val csvDuplicate = "Suspected duplicate"
    override val csvUnresolved = "Unresolved"
    override val csvSuccessTemplate = "Import complete · N added · position and cost basis recomputed"
    override val csvAnomalyToastTemplate = "Import complete · N added · anomalous coins: "
    override fun csvParseFailed(reason: String) = "CSV parse failed: " + reason
    override fun csvTemplateDownloaded(fileName: String) = "Template downloaded: " + fileName
    override fun csvTemplateFailed(reason: String) = "Template download failed: " + reason
    override fun csvImportFailed(reason: String) = "Import failed: " + reason
    override fun csvImported(
        imported: Int,
        duplicatesSkipped: Int,
        unresolvedSkipped: Int,
        anomalousCoins: List<String>,
    ): String {
        val message = buildString {
            append("Import complete · ").append(imported).append(" added")
            if (duplicatesSkipped > 0) append(" · ").append(duplicatesSkipped).append(" duplicates skipped")
            if (unresolvedSkipped > 0) append(" · ").append(unresolvedSkipped).append(" unresolved skipped")
        }
        return if (anomalousCoins.isNotEmpty()) {
            message + " · anomalous coins: " + anomalousCoins.joinToString(", ") +
                " (backfill deposits or run a calibration)"
        } else {
            message
        }
    }

    // ---------- Fee rate settings ----------
    override val feeGlobalTitle = "Global default fee rates"
    override val feeBuyRateLabel = "Buy fee %"
    override val feeSellRateLabel = "Sell fee %"
    override val feeSaveGlobalButton = "Save global rates"
    override val feeExchangeTitle = "Exchange fee rates (override global)"
    override val feeExchangeEmpty = "No exchange fee rates yet (optional)"
    override val feeSaveExchangeButton = "Add / override exchange rate"
    override val feeSaveEditButton = "Save changes"
    override fun feeExchangeLine(buy: String, sell: String) = "Buy " + buy + "% · Sell " + sell + "%"
    override val feeEnterBuyRate = "Enter the buy fee rate"
    override val feeEnterSellRate = "Enter the sell fee rate"
    override val feeEnterExchangeName = "Enter an exchange name (e.g. BINANCE)"
    override fun feeGlobalSaved(buy: String, sell: String) =
        "Global fee rates saved (buy " + buy + "% / sell " + sell + "%)"
    override fun feeExchangeAdded(name: String, buy: String, sell: String) =
        "Saved " + name + " fee rates (buy " + buy + "% / sell " + sell + "%)"
    override fun feeExchangeUpdated(name: String, buy: String, sell: String) =
        "Updated " + name + " fee rates (buy " + buy + "% / sell " + sell + "%)"
    override val feeRuleDeleted = "Fee rule deleted"
    override val feeSaveFailed = "Save failed"
    override val feeDeleteFailed = "Delete failed"
    override val feeCandidateAll = "Candidate exchanges (click to fill in, or type another exchange):"
    override val feeCandidateFiltered = "Candidates (filtered by input):"

    // ---------- Funds page ----------
    override val fundsTitle = "Funds"
    override val fundsSubtitle = "Deposits / withdrawals / calibration (a calibration delta is booked as a system-generated fund entry)"
    override val cardCash = "Available cash"
    override val cardCashHint = "Cash-type coins (stablecoin whitelist)"
    override val cardPrincipal = "Invested principal (net)"
    override val cardPrincipalHintPrefix = "Total deposits "
    override val cardPrincipalHintMid = " − Total withdrawals "
    override val btnDeposit = "+ Record deposit"
    override val btnWithdraw = "− Record withdrawal"
    override val btnCalibrate = "Calibrate position"
    override val searchFundsPlaceholder = "Search coin / source / notes"
    override val filterByDate = "Filter by date:"
    override val dateAll = "All time"
    override val date30 = "Last 30 days"
    override val date90 = "Last 90 days"
    override val dateOlder = "Older"
    override val filterDeposit = "Deposit"
    override val filterWithdraw = "Withdrawal"
    override val filterRecon = "Calibration"
    override val colFundType = "Type"
    override val colCoin = "Coin"
    override val colFiatValue = "Fiat value"
    override val colDate = "Date"
    override val colSourceDest = "Source / destination"
    override val reconNoEdit = "Calibration entries cannot be edited, only deleted (PRD §10-8)"

    // ---------- Deposit / withdrawal form ----------
    override val formTitleDeposit = "Record deposit"
    override val formTitleWithdraw = "Record withdrawal"
    override val labelCoin = "Coin"
    override val coinPlaceholder = "USDT / BTC"
    override val coinHint = "Autocompleted from the coin catalog; stablecoins or other crypto only; defaults to the stablecoin of the base fiat"
    override val labelDateLocal = "Date & time (local zone)"
    override val labelSource = "Source (deposit)"
    override val labelDest = "Destination (withdrawal)"
    override val fiatPreviewLabel = "Fiat value (market price at entry)"
    override val fiatPreviewPending = "Price pending (no market price yet; recalculated online after saving)"
    override val fiatPreviewEstimated = " (estimated)"
    override val fiatInputHint = "Fiat cannot be booked: record the stablecoin actually received after conversion instead"
    override fun fiatHintTwin(fiatCode: String, stableSymbol: String) =
        fiatInputHint + " (" + fiatCode + " → " + stableSymbol + ")"
    override val v6Qty = "Quantity must be greater than 0"
    override val v6CoinRequired = "Coin is required"
    override val v6TimeRequired = "Date is required"
    override val fundsSaveSuccess = "Fund entry saved · positions and metrics recomputed"
    override val fundsUpdateSuccess = "Fund entry updated · invested principal and ROI recomputed"
    override val fundsEditSelectOne = "Select one fund entry to edit"
    override val fundsEditSelectRecon = "Calibration entries cannot be edited, only deleted"
    override val fundsDeleteConfirmTemplate = "Delete N fund record(s) and recompute invested principal and ROI. Continue?"
    override val fundsDeleteSuccess = "Fund entry deleted · invested principal and ROI recomputed"
    override val deleteFailedPrefix = "Delete failed: "
    override val fundsNotFound = "Fund entry not found or already deleted"
    override fun fundsValidation(error: LedgerValidationException): String = when (error.code) {
        LedgerErrorCode.INSUFFICIENT_POSITION -> error.coinSymbol + " position is insufficient for a withdrawal"
        LedgerErrorCode.INSUFFICIENT_BALANCE -> error.coinSymbol + " balance is insufficient (record a deposit first)"
        LedgerErrorCode.REPLAY_CONFLICT ->
            "This would make the " + error.coinSymbol + " position negative (this entry may already be " +
                "relied on by later records, e.g. a deposit consumed by a buy); adjust the related records first"
    }

    // ---------- Coin resolution ----------
    override fun coinLabel(symbol: String, name: String, cgId: String) = symbol + " · " + name + " (" + cgId + ")"

    override fun coinResolutionFailed(symbol: String, reason: String, kind: CoinResolutionKind): String =
        "Coin \"" + symbol + "\" cannot be resolved uniquely: " + kindText(kind) +
            if (kind == CoinResolutionKind.AMBIGUOUS) {
                " — pick one from the candidate list below and save again"
            } else {
                ""
            }

    // ---------- Position calibration ----------
    override val calTitle = "Calibrate positions with exchange balance"
    override val calCoinPlaceholder = "Enter a coin (e.g. BTC)"
    override val calPrepare = "Generate preview"
    override val calExecute = "Confirm calibration"
    override val calExecuting = "Running…"
    override val calRowExchange = "Exchange"
    override val calRowKey = "API key"
    override val calRowLocal = "Local position"
    override val calRowExchangeQty = "Exchange balance"
    override val calRowDelta = "Delta"
    override val calRowFiat = "Delta (fiat)"
    override val calRowPrice = "Market price"
    override val calDirectionPositive = "Positive delta (booked as a system deposit; added to cost basis and cumulative deposits at the market price)"
    override val calDirectionNegative = "Negative delta (booked as a system withdrawal; removed at the average cost then in effect and added to cumulative withdrawals)"
    override val calDirectionZero = "No delta: the local position already matches the exchange"
    override val calNoDiff = "Local position already matches the exchange balance; no calibration entry created"
    override val calSuccess = "Calibration complete · entry created and sync log recorded"
    override val calFailed = "Calibration failed"
    override val pickedPrefix = "Selected: "
    override val pickedHint = "Pick a candidate and it will be used on save (for duplicate symbols, distinguish by the CoinGecko id in parentheses)"
    /** 解析失败原因的英文成句（zh 档用数据层 reason 正文，见接口 KDoc）。 */
    private fun kindText(kind: CoinResolutionKind): String = when (kind) {
        CoinResolutionKind.AMBIGUOUS -> "ambiguous (several coins share this symbol)"
        CoinResolutionKind.NOT_FOUND -> "not in the coin catalogue (update the catalogue or check the spelling)"
        CoinResolutionKind.FIAT_UNSUPPORTED -> "no stablecoin twin for this fiat quote currency"
        CoinResolutionKind.CANDIDATE_GONE -> "the selected coin no longer exists, please pick again"
        CoinResolutionKind.UNKNOWN -> "unresolved"
    }

    override fun calibrationBlocked(reason: CalibrationBlockedException.Reason): String = when (reason) {
        CalibrationBlockedException.Reason.NO_RECORDS -> "No trades for this coin yet, nothing to calibrate against"
        CalibrationBlockedException.Reason.MULTI_SOURCE ->
            "This coin has multiple data sources, so a single exchange balance cannot be used"
        CalibrationBlockedException.Reason.NO_EXCHANGE_KEY ->
            "No read-only API key stored for that exchange, so its balance cannot be read"
        CalibrationBlockedException.Reason.NO_MARKET_PRICE -> "Market prices are unavailable, calibration is not allowed"
        CalibrationBlockedException.Reason.BALANCE_FETCH_FAILED ->
            "Could not read the exchange balance, check the network or the key and retry"
    }

    override fun feeRuleInvalid(error: FeeRulePolicy.Error): String = when (error) {
        FeeRulePolicy.Error.EMPTY -> "Enter a fee rate (percentage, e.g. 0.1)"
        FeeRulePolicy.Error.NEGATIVE -> "The fee rate cannot be negative"
        FeeRulePolicy.Error.TOO_LARGE -> "The fee rate cannot exceed 100%"
    }

}

val ledgerStrings: LedgerStrings get() = if (I18n.isZh) LedgerStringsZh else LedgerStringsEn
