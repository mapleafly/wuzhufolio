package com.wuzhufolio.ui.i18n

/**
 * 聚合页文案（M12 T12.1：仪表盘 / 资产列表 / 币种资产详情；zh/en 双档）。
 * 词条来源 = ia.md §2.4–§2.6 + interaction.md §2 异常态 + 原型 `wuzhufolio-light.html`
 * 对应页面（仪表盘卡片/环形图浮窗/资产表头/币种详情汇总）。
 */
interface PortfolioStrings {
    // ---- 仪表盘 ----
    val dashboardTitle: String
    fun dashboardSubtitle(account: String, fiat: String): String
    val cardNetValue: String
    val card24h: String
    val cardRoi: String
    val cardInvestedNet: String
    val cardAvailableCash: String
    val cardTotalReturn: String
    val cardRealizedPnl: String
    fun cumulativeDeposits(value: String): String
    fun cumulativeWithdrawals(value: String): String
    val cashSubLabel: String
    val totalReturnFormula: String
    val realizedSubLabel: String
    val distributionTitle: String
    val totalAssetsInner: String
    val otherSegment: String
    fun otherSmallCoins(names: String): String
    val popHoldingQty: String
    val popShare: String
    val popMarketValue: String
    val multiCoinTotal: String
    val donutAria: String
    val donutEmpty: String
    val securityTitle: String
    val securityLine1: String
    val securityLine2: String
    val securityLine3: String
    val securityLine4: String
    fun lastBackup(at: String): String
    val refreshQuotes: String
    val refreshing: String

    // ---- 资产列表 ----
    val assetsTitle: String
    val assetsSubtitle: String
    val colCoin: String
    val colQuantity: String
    val colAvgCost: String
    val colPrice: String
    val colMarketValue: String
    val colFloatPnl: String
    val colRealizedPnl: String
    fun sortBy(column: String): String
    val noMarketData: String
    val anomalyBadge: String

    /** 成本基数异常徽标（负持仓历史 → 平均成本/已实现盈亏不可靠；M12 走查反馈修复轮）。 */
    val costUnreliableBadge: String

    /** 成本基数异常的原因说明（资产列表/币种详情悬停与副行文案）。 */
    val costUnreliableHint: String

    /** 仪表盘/资产列表顶部警示（N 个币种的历史负持仓使成本口径不可靠）。 */
    fun costUnreliableNotice(count: Int): String
    val estimatedBadge: String
    fun coverage(covered: Int, total: Int): String
    val mixedSource: String

    // ---- 币种资产详情 ----
    val coinDetailBack: String
    val coinDetailTitle: String
    val coinHoldingValue: String
    fun coinShare(percent: String): String
    val coinAvgCost: String
    val calibrateAction: String
    val calibrateHintMulti: String
    val calibrateHintNoRecords: String
    val calibrationsTitle: String
    val calibrationsEmpty: String
    val coinTxTitle: String
    val filterExchangeAll: String
    val filterTypeAll: String
    val filterBuy: String
    val filterSell: String
    val searchTxPlaceholder: String
    val txEmpty: String
    val colPair: String
    val colSide: String
    val colFee: String
    val colExchange: String
    val colTime: String
    val colNotes: String
    val buyLabel: String
    val sellLabel: String

    // ---- 校准弹窗（币种详情入口） ----
    val calTitle: String
    val calPreviewHint: String
    val calExchange: String
    val calLocal: String
    val calExchangeQty: String
    val calDelta: String
    val calDeltaFiat: String
    val calMarketPrice: String
    val calExecute: String
    val calDone: String
    val calNoDelta: String
    val calFailed: String
    val calDepositSemantics: String
    val calWithdrawalSemantics: String

    // ---- 异常态（interaction.md §2） ----
    val loading: String
    val emptyPortfolio: String
    val offlineKeepLast: String
    val refreshFailed: String
}

object PortfolioStringsZh : PortfolioStrings {
    override val dashboardTitle = "仪表盘"
    override fun dashboardSubtitle(account: String, fiat: String) =
        "账户 " + account + " · 全部币种持仓按现价折算 " + fiat
    override val cardNetValue = "总资产净值"
    override val card24h = "24 小时盈亏"
    override val cardRoi = "总投资回报率 ROI"
    override val cardInvestedNet = "投入本金（净）"
    override val cardAvailableCash = "可用现金余额"
    override val cardTotalReturn = "总收益"
    override val cardRealizedPnl = "累计已实现盈亏"
    override fun cumulativeDeposits(value: String) = "累计增资 " + value
    override fun cumulativeWithdrawals(value: String) = "累计撤资 " + value
    override val cashSubLabel = "现金类币种（稳定币白名单）"
    override val totalReturnFormula = "净值 + 撤资 − 增资"
    override val realizedSubLabel = "卖出逐笔累计"
    override val distributionTitle = "资产分布"
    override val totalAssetsInner = "总资产"
    override val otherSegment = "其他"
    override fun otherSmallCoins(names: String) = "其他小额币种（" + names + "）"
    override val popHoldingQty = "持有数量"
    override val popShare = "总资产占比"
    override val popMarketValue = "持有量市值"
    override val multiCoinTotal = "多币种合计"
    override val donutAria = "资产分布环形图"
    override val donutEmpty = "暂无持仓，记录交易后展示资产分布"
    override val securityTitle = "安全与隐私"
    override val securityLine1 = "✓ 数据 100% 本地存储，不上传任何服务器"
    override val securityLine2 = "✓ 零遥测 · 无第三方分析 SDK"
    override val securityLine3 = "✓ 分层密钥（DEK/KEK）+ AES-256-GCM"
    override val securityLine4 = "✓ 交易所 API 仅需只读权限"
    override fun lastBackup(at: String) = "✓ 最近备份：" + at
    override val refreshQuotes = "立即刷新行情"
    override val refreshing = "刷新中…"

    override val assetsTitle = "资产列表"
    override val assetsSubtitle = "默认按市值降序 · 单击表头排序 · 单击行查看币种详情"
    override val colCoin = "币种"
    override val colQuantity = "持有数量"
    override val colAvgCost = "平均成本"
    override val colPrice = "当前价"
    override val colMarketValue = "总市值"
    override val colFloatPnl = "总浮动盈亏"
    override val colRealizedPnl = "累计已实现"
    override fun sortBy(column: String) = "按" + column + "排序"
    override val noMarketData = "无行情"
    override val anomalyBadge = "持仓异常"
    override val costUnreliableBadge = "成本不可靠"
    override val costUnreliableHint =
        "该币历史出现过负持仓（账本缺少入金或早期持仓记录），引擎无法给出可靠的成本基数——" +
            "平均成本与已实现盈亏仅供参考，补录增资/早期持仓或做一次持仓校准后可恢复"
    override fun costUnreliableNotice(count: Int) =
        "⚠ " + count + " 个币种的历史持仓曾为负（账本缺少入金或早期记录），其中平均成本与已实现盈亏不可靠"
    override val estimatedBadge = "估算中"
    override fun coverage(covered: Int, total: Int) = "覆盖 " + covered + "/" + total + " 个币种"
    override val mixedSource = "混合数据源"

    override val coinDetailBack = "← 返回资产列表"
    override val coinDetailTitle = "币种资产详情"
    override val coinHoldingValue = "当前持有价值"
    override fun coinShare(percent: String) = "占总资产 " + percent
    override val coinAvgCost = "平均成本"
    override val calibrateAction = "以交易所余额校准持仓"
    override val calibrateHintMulti = "该币种存在多个数据来源，已隐藏校准入口（PRD 故事 4.1-5）"
    override val calibrateHintNoRecords = "该币种暂无交易记录，无可校准依据"
    override val calibrationsTitle = "校准历史"
    override val calibrationsEmpty = "暂无校准记录"
    override val coinTxTitle = "该币种交易记录"
    override val filterExchangeAll = "全部交易所"
    override val filterTypeAll = "全部类型"
    override val filterBuy = "买入"
    override val filterSell = "卖出"
    override val searchTxPlaceholder = "搜索交易对 / 备注"
    override val txEmpty = "暂无交易记录"
    override val colPair = "交易对"
    override val colSide = "类型"
    override val colFee = "手续费"
    override val colExchange = "交易所"
    override val colTime = "时间"
    override val colNotes = "备注"
    override val buyLabel = "买入"
    override val sellLabel = "卖出"

    override val calTitle = "以交易所余额校准持仓"
    override val calPreviewHint = "校准差额视同系统资金操作入列（正差额 = 增资 / 负差额 = 撤资），不产生盈亏"
    override val calExchange = "依据交易所"
    override val calLocal = "本地持仓"
    override val calExchangeQty = "交易所余额"
    override val calDelta = "差额"
    override val calDeltaFiat = "差额折算"
    override val calMarketPrice = "校准时市价"
    override val calExecute = "确认执行校准"
    override val calDone = "校准完成，已重算持仓"
    override val calNoDelta = "本地持仓与交易所余额一致，无需校准"
    override val calFailed = "校准失败"
    override val calDepositSemantics = "视同增资"
    override val calWithdrawalSemantics = "视同撤资"

    override val loading = "加载中…"
    override val emptyPortfolio = "暂无持仓资产"
    override val offlineKeepLast = "保持上次价格（离线）"
    override val refreshFailed = "行情刷新失败"
}

object PortfolioStringsEn : PortfolioStrings {
    override val dashboardTitle = "Dashboard"
    override fun dashboardSubtitle(account: String, fiat: String) =
        "Account " + account + " · all positions valued at current price in " + fiat
    override val cardNetValue = "Net asset value"
    override val card24h = "24-hour P&L"
    override val cardRoi = "Return on investment"
    override val cardInvestedNet = "Invested principal (net)"
    override val cardAvailableCash = "Available cash"
    override val cardTotalReturn = "Total return"
    override val cardRealizedPnl = "Realized P&L (cumulative)"
    override fun cumulativeDeposits(value: String) = "Total deposits " + value
    override fun cumulativeWithdrawals(value: String) = "Total withdrawals " + value
    override val cashSubLabel = "Cash-like coins (stablecoin whitelist)"
    override val totalReturnFormula = "NAV + withdrawals − deposits"
    override val realizedSubLabel = "Sum over sell trades"
    override val distributionTitle = "Asset allocation"
    override val totalAssetsInner = "Total"
    override val otherSegment = "Other"
    override fun otherSmallCoins(names: String) = "Other small holdings (" + names + ")"
    override val popHoldingQty = "Quantity held"
    override val popShare = "Share of assets"
    override val popMarketValue = "Market value"
    override val multiCoinTotal = "Multiple coins"
    override val donutAria = "Asset allocation donut chart"
    override val donutEmpty = "No positions yet — record a trade to see the allocation"
    override val securityTitle = "Security & privacy"
    override val securityLine1 = "✓ Data stays 100% on this device — nothing is uploaded"
    override val securityLine2 = "✓ Zero telemetry · no third-party analytics SDK"
    override val securityLine3 = "✓ Layered keys (DEK/KEK) + AES-256-GCM"
    override val securityLine4 = "✓ Exchange API keys only need read-only permission"
    override fun lastBackup(at: String) = "✓ Last backup: " + at
    override val refreshQuotes = "Refresh prices"
    override val refreshing = "Refreshing…"

    override val assetsTitle = "Portfolio"
    override val assetsSubtitle = "Sorted by market value by default · click a header to sort · click a row for details"
    override val colCoin = "Coin"
    override val colQuantity = "Quantity"
    override val colAvgCost = "Avg. cost"
    override val colPrice = "Price"
    override val colMarketValue = "Market value"
    override val colFloatPnl = "Unrealized P&L"
    override val colRealizedPnl = "Realized P&L"
    override fun sortBy(column: String) = "Sort by " + column
    override val noMarketData = "No price"
    override val anomalyBadge = "Position anomaly"
    override val costUnreliableBadge = "Cost unreliable"
    override val costUnreliableHint =
        "This coin went negative at some point (the ledger is missing deposits or earlier holdings), " +
            "so the engine cannot derive a reliable cost basis — average cost and realized P&L are indicative only. " +
            "Record the missing deposits/earlier trades, or run a position calibration, to restore it"
    override fun costUnreliableNotice(count: Int) =
        "⚠ " + count + " coins went negative at some point (missing deposits or earlier records); " +
            "their average cost and realized P&L are not reliable"
    override val estimatedBadge = "Estimated"
    override fun coverage(covered: Int, total: Int) = "Covers " + covered + "/" + total + " coins"
    override val mixedSource = "Mixed sources"

    override val coinDetailBack = "← Back to portfolio"
    override val coinDetailTitle = "Coin detail"
    override val coinHoldingValue = "Current value"
    override fun coinShare(percent: String) = "Share of assets " + percent
    override val coinAvgCost = "Avg. cost"
    override val calibrateAction = "Calibrate with exchange balance"
    override val calibrateHintMulti = "This coin has multiple data sources, so calibration is hidden (PRD story 4.1-5)"
    override val calibrateHintNoRecords = "No trades for this coin yet — nothing to calibrate against"
    override val calibrationsTitle = "Calibration history"
    override val calibrationsEmpty = "No calibration records"
    override val coinTxTitle = "Transactions for this coin"
    override val filterExchangeAll = "All exchanges"
    override val filterTypeAll = "All types"
    override val filterBuy = "Buy"
    override val filterSell = "Sell"
    override val searchTxPlaceholder = "Search pair / notes"
    override val txEmpty = "No transactions"
    override val colPair = "Pair"
    override val colSide = "Type"
    override val colFee = "Fee"
    override val colExchange = "Exchange"
    override val colTime = "Time"
    override val colNotes = "Notes"
    override val buyLabel = "Buy"
    override val sellLabel = "Sell"

    override val calTitle = "Calibrate with exchange balance"
    override val calPreviewHint =
        "The difference is booked as a system fund flow (positive = deposit, negative = withdrawal) and creates no P&L"
    override val calExchange = "Exchange"
    override val calLocal = "Local position"
    override val calExchangeQty = "Exchange balance"
    override val calDelta = "Difference"
    override val calDeltaFiat = "Difference value"
    override val calMarketPrice = "Price at calibration"
    override val calExecute = "Run calibration"
    override val calDone = "Calibration complete, positions recalculated"
    override val calNoDelta = "Local position already matches the exchange balance"
    override val calFailed = "Calibration failed"
    override val calDepositSemantics = "booked as deposit"
    override val calWithdrawalSemantics = "booked as withdrawal"

    override val loading = "Loading…"
    override val emptyPortfolio = "No positions"
    override val offlineKeepLast = "Keeping last known prices (offline)"
    override val refreshFailed = "Price refresh failed"
}

val portfolioStrings: PortfolioStrings get() = if (I18n.isZh) PortfolioStringsZh else PortfolioStringsEn
