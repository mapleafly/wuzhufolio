package com.wuzhufolio.ui.ledger

import com.wuzhufolio.domain.ledger.LedgerErrorCode

/**
 * 资金管理页文案（M8 · T8.1–T8.3 · 对齐 PRD 故事 6.1/6.2/6.3、§9.8、全局说明「持仓校准规则」、
 * interaction V6/V7、原型 wuzhufolio-light.html 资金页/增资撤资表单逐字口径）。
 */
object FundsCopy {

    const val PAGE_TITLE = "资金管理"
    const val PAGE_SUB = "增资 / 撤资 / 校准（校准差额视同系统生成的资金操作）"

    // 总览卡（PRD §9.8 页面上部）
    const val CARD_CASH = "可用现金余额"
    const val CARD_CASH_HINT = "现金类币种（稳定币白名单）"
    const val CARD_PRINCIPAL = "投入本金（净）"
    const val CARD_PRINCIPAL_HINT_PREFIX = "累计增资 "
    const val CARD_PRINCIPAL_HINT_MID = " − 累计撤资 "

    // 命令区（PRD §9.8 操作区 + 校准入口，见模块记录 M8 §5 GUI 偏差登记）
    const val BTN_DEPOSIT = "＋ 记录增资"
    const val BTN_WITHDRAW = "－ 记录撤资"
    const val BTN_EDIT = "编辑"
    const val BTN_DELETE = "删除"
    const val BTN_CALIBRATE = "校准持仓"
    const val SEARCH_PLACEHOLDER = "搜索币种 / 来源 / 备注"
    const val FILTER_TYPE_LABEL = "按类型筛选："
    const val FILTER_ALL = "全部类型"
    const val FILTER_DEPOSIT = "增资"
    const val FILTER_WITHDRAW = "撤资"
    const val FILTER_RECON = "校准"
    const val FILTER_DATE_LABEL = "按日期筛选："
    const val DATE_ALL = "全部时间"
    const val DATE_30 = "近 30 天"
    const val DATE_90 = "近 90 天"
    const val DATE_OLDER = "更早"

    // 列头（PRD §9.8 列表字段）
    const val COL_TYPE = "类型"
    const val COL_COIN = "币种"
    const val COL_QTY = "数量"
    const val COL_FIAT = "折算金额"
    const val COL_TIME = "日期"
    const val COL_SOURCE = "来源 / 去向"
    const val COL_NOTES = "备注"
    const val COL_ACTIONS = "操作"
    const val TYPE_RECON = "校准"
    const val EMPTY = "暂无记录"
    const val EMPTY_FILTERED = "暂无符合条件的记录"
    const val RECON_NO_EDIT = "校准记录不可编辑，仅可删除（PRD §10-8）"

    // 增资/撤资表单（PRD §9.8 交互逻辑）
    const val FORM_TITLE_DEPOSIT = "记录增资"
    const val FORM_TITLE_WITHDRAW = "记录撤资"
    const val LABEL_COIN = "币种"
    const val COIN_PLACEHOLDER = "USDT / BTC"
    const val COIN_HINT = "基于币种目录自动补全；限稳定币或其他加密货币；默认取基础法币对应稳定币"
    const val LABEL_QTY = "数量"
    const val LABEL_TIME = "日期时间（本地时区）"
    const val LABEL_SOURCE = "来源（增资）"
    const val LABEL_DEST = "去向（撤资）"
    const val LABEL_NOTES = "备注"
    const val FIAT_PREVIEW_LABEL = "折算基础法币（记录时行情价）"
    const val FIAT_PREVIEW_PENDING = "待定价（暂无行情价，保存后联网自动补算）"
    const val FIAT_PREVIEW_ESTIMATED = "（估算）"
    const val FIAT_INPUT_HINT_PREFIX = "法币不入账：请改为记录兑换后实际到账的稳定币"
    const val BTN_CANCEL = "取消"
    const val BTN_SAVE = "保存"

    // 校验（interaction V6 + 服务校验反馈）
    const val V6_QTY = "数量必须大于 0"
    const val V6_COIN_REQUIRED = "币种必填"
    const val V6_TIME_REQUIRED = "日期必填"
    const val VALIDATION_FAILED = "请检查表单中的错误提示（红色文字）后再保存"
    const val SAVE_SUCCESS = "资金记录已保存 · 持仓与指标已重算"
    const val UPDATE_SUCCESS = "资金记录已更新 · 投入本金与 ROI 已重算"
    const val SAVE_FAILED = "保存失败（详情见日志）"
    const val EDIT_SELECT_ONE = "编辑请先勾选一条资金记录"
    const val EDIT_SELECT_RECON = "校准记录不可编辑，仅可删除"
    const val DELETE_SELECT_HINT = "请先勾选记录"
    const val DELETE_CONFIRM = "将删除 N 条资金记录并重算投入本金与 ROI，是否继续？"
    const val DELETE_SUCCESS = "已删除资金记录 · 投入本金与 ROI 已重算"
    const val DELETE_FAILED = "删除失败："
    const val NOT_FOUND = "资金记录不存在或已删除"

    // 校准（PRD 故事 4.1-5 / 全局说明「持仓校准规则」）
    const val CAL_TITLE = "以交易所余额校准持仓"
    const val CAL_COIN_LABEL = "币种"
    const val CAL_COIN_PLACEHOLDER = "输入币种（如 BTC）"
    const val CAL_PREPARE = "生成校准预览"
    const val CAL_EXECUTE = "确认校准"
    const val CAL_ROW_EXCHANGE = "依据交易所"
    const val CAL_ROW_KEY = "依据密钥"
    const val CAL_ROW_LOCAL = "本地持仓"
    const val CAL_ROW_EXCHANGE_QTY = "交易所余额"
    const val CAL_ROW_DELTA = "差额"
    const val CAL_ROW_FIAT = "差额折算"
    const val CAL_ROW_PRICE = "校准时市价"
    const val CAL_DIRECTION_POSITIVE = "正差额（视同系统增资，按校准时市价计入成本与累计增资）"
    const val CAL_DIRECTION_NEGATIVE = "负差额（视同系统撤资，按当时平均成本移出并计入累计撤资）"
    const val CAL_DIRECTION_ZERO = "无差额：本地持仓已与交易所一致，无需校准"
    const val CAL_NO_DIFF = "本地持仓已与交易所余额一致，未生成校准记录"
    const val CAL_SUCCESS = "校准完成 · 已生成校准记录并留痕同步日志"
    const val CAL_EXECUTING = "执行中…"

    /** 违例分类 -> 用户文案（interaction V7 / V9；api-contracts §4 错误码映射）。 */
    fun validationCopy(error: com.wuzhufolio.domain.ledger.LedgerValidationException): String = when (error.code) {
        LedgerErrorCode.INSUFFICIENT_POSITION -> error.coinSymbol + " 持仓不足，无法撤资"
        LedgerErrorCode.INSUFFICIENT_BALANCE -> error.coinSymbol + " 余额不足（请先记录增资）"
        LedgerErrorCode.REPLAY_CONFLICT ->
            "该操作将导致 " + error.coinSymbol + " 持仓为负（该笔记录可能已被后续记录依赖，" +
                "如增资已被买入消耗），请先调整相关记录"
    }
}
