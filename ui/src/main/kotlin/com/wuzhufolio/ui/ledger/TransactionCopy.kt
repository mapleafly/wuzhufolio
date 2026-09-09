package com.wuzhufolio.ui.ledger

import com.wuzhufolio.domain.ledger.LedgerErrorCode

/**
 * 交易管理页文案（M7 · T7.1–T7.4 · 对齐 PRD §9.6/9.7、故事 2.1/2.3/7.1、interaction V1–V5/V9、
 * 原型 wuzhufolio-light.html 交易页/表单/CSV 弹窗逐字口径）。
 */
object TransactionCopy {

    const val PAGE_TITLE = "交易管理"
    const val PAGE_SUB = "卖出记录行显示该笔已实现盈亏（本地全量重放推导，非交易所原始数据）"
    /** 已实现盈亏为空（"--"）的口径说明（GUI 走查修复轮：回答「为何部分卖出无盈亏」）。 */
    const val PAGE_SUB_HINT =
        "「--」= 该笔卖出时无成本基数：更早的买入未导入（API 仅回最近 500 笔，更早历史需 CSV 补录），" +
            "或该币种持仓为负（异常区段）。"

    // 命令区（PRD §9.6）
    const val BTN_ADD = "＋ 添加交易"
    const val BTN_EDIT = "修改交易"
    const val BTN_DELETE = "删除交易"
    const val BTN_IMPORT = "导入交易 (CSV)"
    const val SEARCH_PLACEHOLDER = "搜索币种 / 交易所"
    const val FILTER_ALL = "全部类型"
    const val FILTER_BUY = "买入"
    const val FILTER_SELL = "卖出"

    // 列头
    const val COL_PAIR = "交易对"
    const val COL_SIDE = "类型"
    const val COL_PRICE = "价格"
    const val COL_QTY = "数量"
    const val COL_FEE = "手续费"
    const val COL_TOTAL = "总价"
    const val COL_EXCHANGE = "交易所"
    const val COL_TIME = "时间"
    const val COL_REALIZED = "已实现盈亏"
    const val COL_ACTIONS = "操作"

    const val EMPTY = "暂无记录"
    const val EMPTY_FILTERED = "暂无符合条件的记录"

    // 交易表单（PRD §9.7）
    const val FORM_TITLE_ADD = "添加交易"
    const val FORM_TITLE_EDIT = "编辑交易"
    const val LABEL_EXCHANGE = "交易所"
    const val LABEL_PAIR = "交易对"
    const val LABEL_SIDE = "类型"
    const val LABEL_PRICE = "价格"
    const val LABEL_QTY = "数量"
    const val LABEL_FEE = "手续费"
    const val LABEL_FEE_CURRENCY = "手续费币种"
    const val LABEL_TIME = "交易时间（本地时区）"
    const val LABEL_NOTES = "备注"
    const val PAIR_PLACEHOLDER = "输入 BTC / ETH 自动补全"
    const val PAIR_HINT = "基于币种目录自动补全；目录未收录可手输并标「无行情」"
    const val SIDE_BUY = "买入"
    const val SIDE_SELL = "卖出"
    const val FEE_ROLE_QUOTE = "计价币种"
    const val FEE_ROLE_BASE = "基础币种"
    const val FEE_ROLE_CUSTOM = "自定义币种"
    const val CUSTOM_FEE_HINT = "自定义 = 既非计价币种也非基础币种的第三币种（如 BNB 抵扣手续费）"
    const val AUTO_FEE_BTN = "自动计算手续费（交易所 > 全局费率）"
    const val TOTAL_LABEL = "总价（实时）"
    const val BTN_CANCEL = "取消"
    const val BTN_SAVE = "保存"
    const val BTN_CLOSE = "关闭"
    const val BTN_EDIT_ROW = "编辑"
    const val BTN_DELETE_ROW = "删除"

    // 校验（interaction V1–V4）
    const val V1_PRICE = "价格必须大于 0"
    const val V1_QTY = "数量必须大于 0"
    const val V2_FEE = "手续费必须 ≥ 0"
    const val V3_FEE_CURRENCY = "手续费币种不能为空"
    const val V4_REQUIRED = "必填项不能为空"

    // 结果与提示
    const val SAVE_SUCCESS = "交易已保存 · 已联动持仓并重算成本"
    const val UPDATE_SUCCESS = "交易已更新 · 持仓与成本已重算"
    const val SAVE_FAILED = "保存失败（详情见日志）"
    /** 表单校验未通过时的 toast（内联错误可能因窗口较小不在可视区，双通道反馈）。 */
    const val VALIDATION_FAILED = "请检查表单中的错误提示（红色文字）后再保存"
    const val EDIT_SELECT_ONE = "修改交易请先勾选一条记录"
    const val DELETE_SELECT_HINT = "请先勾选记录"
    const val DELETE_CONFIRM = "将删除 N 条交易记录并重算持仓成本与盈亏，是否继续？"
    const val DELETE_SUCCESS = "已删除交易记录 · 持仓与成本已重算"
    const val NOT_FOUND = "交易记录不存在或已删除"

    // CSV 导入（PRD 故事 2.3）
    const val CSV_TITLE = "导入交易 (CSV)"
    const val CSV_FILE_LABEL = "CSV 文件"
    const val CSV_DOWNLOAD_TEMPLATE = "下载标准模板"
    const val CSV_FILE_HINT = "日期按 UTC 解析 · 交易对以法币计价时按 1:1 归一化（USD→USDT）"
    const val CSV_PARSE = "解析并预览"
    const val CSV_IMPACT = "影响摘要（解析预览）"
    const val CSV_CHOOSE = "选择文件"
    const val CSV_NEW = "新增"
    const val CSV_DUPLICATE = "疑似重复"
    const val CSV_UNRESOLVED = "未解析"
    const val CSV_AMBIGUOUS_TITLE = "歧义 ticker 消歧"
    const val CSV_AMBIGUOUS_HINT = "发现 N 个歧义 symbol：请确认候选（名称 / 市值），选择结果固化到映射表、后续自动复用"
    const val CSV_NO_AMBIGUOUS = "无歧义 symbol"
    const val CSV_ANOMALY_HINT = "导入后 N 个币种持仓为负（持仓异常），将标记并引导补录增资或校准"
    const val CSV_CONFIRM = "确认导入"
    const val CSV_SUCCESS = "导入完成 · 新增 N 条 · 持仓与成本已重算"
    const val CSV_ANOMALY_TOAST = "导入完成 · 新增 N 条 · 持仓异常币种："
    const val CSV_DUP_HINT = "疑似重复行默认排除，勾选「导入」可按需补录"

    // 费率自动计算（T7.2）
    const val FEE_NO_RULE = "暂无适用费率规则：可在 设置 → 手续费 配置，或手动填写手续费"
    const val FEE_MISSING_PRICE = "当前行情价缺失，暂无法自动计算手续费"
    const val FEE_CALC_FAILED = "自动计算手续费失败"

    /** 违例分类 -> 用户文案（interaction V5 / V7 同构 / V9；api-contracts §4 错误码映射）。 */
    fun validationCopy(code: LedgerErrorCode, coinSymbol: String): String = when (code) {
        LedgerErrorCode.INSUFFICIENT_BALANCE ->
            coinSymbol + " 余额不足，请先记录转入（增资），或用「导入交易 (CSV)」导入历史记录后再记"
        LedgerErrorCode.INSUFFICIENT_POSITION ->
            coinSymbol + " 持仓不足，无法卖出"
        LedgerErrorCode.REPLAY_CONFLICT ->
            "该操作将导致 " + coinSymbol + " 持仓为负（与已有记录冲突），请先调整相关记录"
    }

    /**
     * 违例分类 -> 用户文案（含冲突记录定位；2026-09-08 修复轮）。
     * REPLAY_CONFLICT 场景（如删除一笔买入、其后的卖出无货可卖）把冲突记录的时间/方向/数量/交易对
     * 带入提示，用户可直接定位要调整的那条记录。
     */
    fun validationCopy(error: com.wuzhufolio.domain.ledger.LedgerValidationException): String =
        when (error.code) {
            com.wuzhufolio.domain.ledger.LedgerErrorCode.INSUFFICIENT_BALANCE,
            com.wuzhufolio.domain.ledger.LedgerErrorCode.INSUFFICIENT_POSITION,
            -> validationCopy(error.code, error.coinSymbol)
            com.wuzhufolio.domain.ledger.LedgerErrorCode.REPLAY_CONFLICT -> buildString {
                append("该操作将导致 ").append(error.coinSymbol).append(" 持仓为负")
                val at = error.conflictAt
                if (at != null) {
                    append("：与 ").append(localTimeText(at)).append(" 的")
                    error.conflictSide?.let { side ->
                        append(if (side == com.wuzhufolio.domain.engine.Side.BUY) "买入 " else "卖出 ")
                    }
                    error.conflictQuantity?.let { append(it.stripTrailingZeros().toPlainString()).append(" ") }
                    error.conflictPair?.let { append(it).append(" ") }
                    append("记录冲突")
                } else {
                    append("（与已有记录冲突）")
                }
                append("，请先调整或删除该冲突记录")
            }
        }

    private fun localTimeText(at: java.time.Instant): String =
        at.atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
