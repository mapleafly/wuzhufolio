package com.wuzhufolio.ui.market

import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.PriceSource

/**
 * 行情设置 UI 文案（T5.5 · 以 P1 原型 wuzhufolio-light.html 设置页「行情数据源」分组为逐字基准；
 * 注册链接取两平台官方文档页，P7 发布前复核）。
 */
object MarketCopy {

    // ---- 设置页行（原型 settingsRow） ----

    const val GROUP_TITLE = "行情数据源"

    const val CG_ROW_LABEL = "行情数据源（CoinGecko API Key）"
    const val CG_ROW_SUB_CONFIGURED = "已配置个人 Key · 专属额度约 30 次/分钟、1 万次/月"
    const val CG_ROW_SUB_KEYLESS = "可选 · 未配置时使用无 Key 公共 API（约 30 次/分钟共享限流）"
    const val CMC_ROW_LABEL = "行情兜底（CoinMarketCap API Key）"
    const val CMC_ROW_SUB_CONFIGURED = "已配置 · 主源失败时兜底"
    const val CMC_ROW_SUB_KEYLESS = "可选 · 未配置时无兜底，保持上次价格 + 时间戳"
    const val STATUS_CONFIGURED = "已配置"
    const val STATUS_UNCONFIGURED = "未配置"

    const val FREQ_ROW_LABEL = "行情刷新频率"
    const val FREQ_ROW_SUB = "窗口可见时生效（托盘按同步间隔降频）"

    // ---- Key 弹窗（原型 openCgKey/openCmcKey 逐字口径） ----

    const val CG_MODAL_TITLE = "行情数据源 · CoinGecko API Key"
    const val CG_MODAL_CONFIGURED = "当前已配置个人 Key（专属额度约 30 次/分钟、1 万次/月；月度额度达 80% 自动降一档并在状态栏提示）。"
    const val CG_MODAL_KEYLESS = "不填 = 使用无 Key 公共 API（开箱即用，按 IP 共享限流约 30 次/分钟，429 频发建议注册个人 Key）。"
    const val CG_REGISTER_HINT = "免费注册：CoinGecko Docs（Demo API 计划）（浏览器打开）。密钥仅存本地、不上传。"
    const val CMC_MODAL_TITLE = "行情兜底 · CoinMarketCap API Key"
    const val CMC_MODAL_CONFIGURED =
        "已配置兜底 Key：主源 CoinGecko 失败（网络/超时/429/额度耗尽/币种未收录）时，" +
            "按 CMC id 批量查询 quotes/latest 兜底，价格旁标注「数据源：CoinMarketCap」，主源恢复自动回落。"
    const val CMC_MODAL_KEYLESS = "可选 · 不填 = 无兜底，主源失败时保持上次价格并显示上次成功时间戳。"
    const val CMC_REGISTER_HINT = "免费注册：coinmarketcap.com/api（浏览器打开）。密钥仅存本地、不上传。"
    const val CG_REGISTER_URL = "https://docs.coingecko.com/reference/setting-up-your-api-key"
    const val CMC_REGISTER_URL = "https://coinmarketcap.com/api/"

    const val BTN_REMOVE_KEY = "移除 Key"
    const val BTN_CANCEL = "取消"
    const val BTN_SAVE_CG = "保存并切换专属额度"
    const val BTN_SAVE_CMC = "保存"
    const val INPUT_LABEL = "API Key"
    const val EMPTY_KEY_ERROR_CG = "未填写 Key · 保持无 Key 公共 API"
    const val EMPTY_KEY_ERROR_CMC = "未填写 Key · 无兜底"
    const val TOAST_CG_SAVED = "已切换 CoinGecko 专属额度（约 30 次/分钟、1 万次/月）"
    const val TOAST_CG_REMOVED = "已切换回无 Key 公共 API"
    const val TOAST_CMC_SAVED = "已配置 CoinMarketCap 兜底"
    const val TOAST_CMC_REMOVED = "已移除 CoinMarketCap 兜底"
    const val TOAST_KEYLESS_SAVE_NOOP = "Key 为空未修改"

    // ---- 数据源指示 / 刷新 ----

    const val REFRESH_BUTTON = "立即刷新行情"
    const val REFRESHING = "刷新中…"
    const val SOURCE_PREFIX = "数据源"
    const val SOURCE_CG_KEYLESS = "CoinGecko · 无 Key 公共 API"
    const val SOURCE_CG_KEYED = "CoinGecko · 专属额度"
    const val SOURCE_CMC = "CoinMarketCap（兜底）"
    const val LAST_REFRESH_PREFIX = "上次价格"
    const val NO_REFRESH_YET = "--"
    const val QUOTA_HINT_PREFIX = "额度已达"
    const val QUOTA_HINT_SUFFIX = "，已自动降档刷新频率"

    /** 行情刷新失败/兜底提示（api-contracts §4 错误码 B3/B4/B5/N1 文案映射）。 */
    fun errorText(error: MarketRefreshError): String = when (error) {
        is MarketRefreshError.Network ->
            "网络不可达（" + sourceName(error.source) + "），已保持上次价格"
        is MarketRefreshError.RateLimited ->
            if (error.keylessHint) {
                "CoinGecko 共享限流触发，已自动退避；建议注册免费个人 Key 获取专属额度"
            } else {
                "行情接口限流（429），已自动退避"
            }
        is MarketRefreshError.InvalidKey ->
            if (error.source == PriceSource.COINGECKO) {
                "CoinGecko API Key 已失效，请检查或更新"
            } else {
                "CoinMarketCap API Key 已失效，请检查或更新"
            }
        is MarketRefreshError.QuotaExceeded ->
            "CoinMarketCap 额度已达上限，已保持上次价格"
        is MarketRefreshError.Http ->
            "行情请求失败（HTTP " + error.code + "），已保持上次价格"
        is MarketRefreshError.Untracked ->
            "「" + error.coin + "」暂无行情数据"
        is MarketRefreshError.Internal ->
            "行情目录初始化失败，请稍后重试（详情见日志）"
    }

    fun sourceName(source: PriceSource): String =
        if (source == PriceSource.COINGECKO) "CoinGecko" else "CoinMarketCap"

    /** 行情行数据源列（null = 无行情 "--"）。 */
    fun sourceText(source: PriceSource?): String = when (source) {
        PriceSource.COINGECKO -> "CoinGecko"
        PriceSource.COINMARKETCAP -> "CoinMarketCap（兜底）"
        null -> "无行情"
    }

    // ---- 行情页（D21 增补） ----

    const val WATCH_TITLE = "行情"
    const val WATCH_SUB_DEFAULT = "默认显示稳定币白名单（现金类）· 搜索可添加更多自选（重启保留）"
    const val WATCH_SUB_CUSTOM = "我的自选（settings 全局行持久化）· 搜索可添加 · 页面可见时按刷新频率自动更新"
    const val WATCH_EMPTY = "自选为空 · 用上方搜索添加币种"
    const val WATCH_SEARCH_LABEL = "搜索币种（coins 目录）"
    const val WATCH_SEARCH_PLACEHOLDER = "如 BTC / usdt / ethereum…"
    const val WATCH_ADD_ACTION = "添加"
    const val WATCH_ADDED_HINT = "已在自选中"
    const val WATCH_REMOVE_ACTION = "移除"
    const val WATCH_COL_COIN = "币种"
    const val WATCH_COL_SOURCE = "数据源"
    const val WATCH_COL_UPDATED = "更新"
    const val WATCH_NO_RESULT = "无匹配币种"
}
