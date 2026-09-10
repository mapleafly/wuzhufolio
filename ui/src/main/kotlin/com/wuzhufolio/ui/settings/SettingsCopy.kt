package com.wuzhufolio.ui.settings

/**
 * 设置页文案（M10 · T10.4 · 以 P1 原型 wuzhufolio-light.html 设置页逐字基准 + ia.md §2.12/§2.16；
 * PRD §7.2 模块 6 / §6「日志管理与可追溯性」）。
 */
object SettingsCopy {

    // ---- 分组标题（原型 settings-group h3 逐字） ----

    const val GROUP_GENERAL = "通用"
    const val GROUP_NETWORK = "网络"
    const val GROUP_MARKET_SYNC = "行情与同步"
    const val GROUP_LOGS = "日志与诊断"
    const val GROUP_FEE = "手续费"
    const val GROUP_API = "API 管理"
    const val GROUP_DATA = "数据管理"
    const val GROUP_ABOUT = "关于"

    const val PAGE_SUB = "账户级设置与全局设置 · 切换即时生效"

    // ---- 通用分组行（原型 settingsRow 逐字基准） ----

    const val FIAT_LABEL = "基础法币"
    const val FIAT_SUB = "计价与折算基准（默认 USD）"
    const val FIAT_UNSUPPORTED = "不支持的基础法币"

    const val THEME_LABEL = "主题（明 / 暗）"
    const val THEME_SUB = "与顶栏 ☾ 快捷切换同步（PRD 6.1）"

    const val PNL_LABEL = "盈亏颜色方案"
    const val PNL_SUB = "数值强制显示 ± 符号，颜色仅辅助"

    const val PRECISION_LABEL = "默认精度"
    const val PRECISION_SUB = "金额/价格 8 位、市值/盈亏 2 位、百分比 2 位"

    const val USERNAME_ENUM_LABEL = "登录页用户名枚举"
    const val USERNAME_ENUM_SUB = "关闭后用户名改为纯手动输入"

    const val CASH_LABEL = "稳定币白名单"
    const val CASH_SUB = "现金类币种判定（默认 USDT/USDC/DAI/TUSD，可扩展）"
    const val CASH_ADD_BUTTON = "添加"
    const val CASH_INPUT_LABEL = "币种标识（CoinGecko id，如 usd-coin）"
    const val CASH_REMOVE = "移除"
    const val CASH_DEFAULT_MARK = "默认"

    const val THRESHOLD_LABEL = "小额币种阈值"
    const val THRESHOLD_SUB = "低于阈值的币种合计归入「其他」（按基础法币计；0 = 不启用）。" +
        "自由数值，按自身规模设定（参考：总资产净值的 1% 左右，如总额 100 可设 1、总额 200 万可设 2 万）"
    const val THRESHOLD_INPUT_LABEL = "阈值（基础法币，0 = 不启用）"
    const val THRESHOLD_INVALID = "请输入不小于 0 的数值（0 = 不启用）"

    // ---- 网络 / 行情与同步 ----

    const val PROXY_LABEL = "系统代理"
    const val PROXY_SUB = "自动检测并使用操作系统代理（检测与状态栏指示随 M11 接入）"

    const val SYNC_INTERVAL_LABEL = "API 同步间隔（交易数据）"
    const val SYNC_INTERVAL_SUB = "自动增量同步的间隔（默认 30 分钟）"

    // ---- 日志与诊断（interaction §2.6 逐字口径） ----

    const val LOGS_VIEW_LABEL = "查看日志"
    const val LOGS_VIEW_SUB = "本地关键操作与错误日志（账户名与金额默认脱敏）"
    const val LOGS_VIEW_BUTTON = "查看"
    const val LOGS_EXPORT_LABEL = "导出日志"
    const val LOGS_EXPORT_SUB = "导出前提示检查敏感信息；密钥明文/哈希与完整响应体不落日志"
    const val LOGS_EXPORT_BUTTON = "导出"
    const val LOGS_EXPORT_CONFIRM = "导出前请检查是否包含敏感信息（日志已自动脱敏）。继续导出？"
    const val LOGS_EXPORTED_TOAST = "日志已导出："
    const val LOGS_EXPORT_FAILED_TOAST = "日志导出失败："
    const val LOGS_MODAL_TITLE = "日志（已脱敏）"
    const val LOGS_EMPTY = "暂无日志"

    const val DIAG_LABEL = "生成诊断报告"
    const val DIAG_SUB = "应用/OS 版本 · schema 版本 · 最近日志片段（已脱敏）· 调用计数"
    const val DIAG_BUTTON = "生成"
    const val DIAG_MODAL_TITLE = "诊断报告"
    const val DIAG_SAVE_BUTTON = "保存到文件"
    const val DIAG_SAVED_TOAST = "诊断报告已保存："
    const val DIAG_FAILED_TOAST = "诊断报告生成失败："

    // ---- 关于（PRD §7.2-6.4） ----

    const val ABOUT_VERSION_LABEL = "版本"
    const val ABOUT_DEV_LABEL = "开发者信息"
    const val ABOUT_DEV_VALUE = "WuZhuFolio 开源项目（社区维护 · AGPL-3.0）"
    const val ABOUT_PRIVACY_LABEL = "隐私政策"
    const val ABOUT_PRIVACY_VALUE = "数据完全本地化：交易/密钥/资金流水只存本机，禁止云端上传（PRD §1.1）"
    const val ABOUT_SOURCE_LABEL = "行情数据源说明"
    const val ABOUT_SOURCE_VALUE =
        "主源 CoinGecko（默认无 Key 公共 API 开箱即用，可配置个人 Key 获专属额度）；" +
            "兜底 CoinMarketCap（免费档，需配置个人 Key）；应用不内置任何 API Key"
    const val ABOUT_NOTELEMETRY_LABEL = "无遥测声明"
    const val ABOUT_NOTELEMETRY_VALUE = "本应用不收集、不上传任何使用数据"
}
