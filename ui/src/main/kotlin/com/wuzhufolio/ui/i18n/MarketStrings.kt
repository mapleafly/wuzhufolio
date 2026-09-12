package com.wuzhufolio.ui.i18n

/**
 * 行情模块文案（设置页行情数据源段 + Key 弹窗 + 行情页/刷新结果；M12 T12.4 zh/en 双档）。
 *
 * zh 档与改造前 `MarketCopy` 的常量**逐字节一致**（UI 测试断言中文原文；含 interaction.md §2.5
 * 429/额度文案与 api-contracts §4 B3/B4/B5/N1 错误映射逐字口径）；en 档为对等译文，
 * 服务名 CoinGecko / CoinMarketCap 与注册链接不译（见 [MarketStringsEn]）。
 *
 * 目录约定同 [CommonStrings]：接口 + Zh/En 两实现 + [marketStrings] 访问器；
 * 调用点一律经 `MarketCopy` 的动态属性，不直接引用本表。
 */
interface MarketStrings {

    // ---- 设置页行（原型 settingsRow） ----

    val groupTitle: String
    val cgRowLabel: String
    val cgRowSubConfigured: String
    val cgRowSubKeyless: String
    val cmcRowLabel: String
    val cmcRowSubConfigured: String
    val cmcRowSubKeyless: String
    val statusConfigured: String
    val statusUnconfigured: String
    val freqRowLabel: String
    val freqRowSub: String

    // ---- Key 弹窗（原型 openCgKey/openCmcKey） ----

    val cgModalTitle: String
    val cgModalConfigured: String
    val cgModalKeyless: String
    val cgRegisterHint: String
    val cmcModalTitle: String
    val cmcModalConfigured: String
    val cmcModalKeyless: String
    val cmcRegisterHint: String

    val btnRemoveKey: String
    val btnCancel: String
    val btnSaveCg: String
    val btnSaveCmc: String
    val inputLabel: String
    val emptyKeyErrorCg: String
    val emptyKeyErrorCmc: String
    val toastCgSaved: String
    val toastCgRemoved: String
    val toastCmcSaved: String
    val toastCmcRemoved: String
    val toastKeylessSaveNoop: String

    // ---- 数据源指示 / 刷新 ----

    val refreshButton: String
    val refreshing: String
    val sourcePrefix: String
    val sourceCgKeyless: String
    val sourceCgKeyed: String
    val sourceCmc: String
    val lastRefreshPrefix: String

    /** 无刷新占位（PRD 列表默认数据规则，与 [WzFormat.DASH] 同值）。 */
    val noRefreshYet: String

    /** 刷新注解（设置页数据源指示后缀）。 */
    val noRefreshNote: String
    val noSuccessRefreshNote: String

    /** 额度提示（前缀 + 百分比 + 后缀；[quotaHint] 组装）。 */
    val quotaHintPrefix: String
    val quotaHintSuffix: String

    /** 刷新频率档位标签（如「5 分钟」）。 */
    fun freqOptionLabel(minutes: Int): String

    /** 上次价格行（`上次价格：15:24`）。 */
    fun lastRefreshTime(at: String): String

    /** 额度降档提示（`额度已达 85%，已自动降档刷新频率`）。 */
    fun quotaHint(percent: Int): String

    // ---- 错误映射（api-contracts §4 B3/B4/B5/N1） ----

    fun networkUnreachable(source: String): String
    val rateLimitedShared: String
    val rateLimitedBackoff: String
    val invalidKeyCg: String
    val invalidKeyCmc: String
    val quotaExceededCmc: String
    fun httpFailed(code: Int): String
    fun untracked(coin: String): String
    val catalogInitFailed: String

    // ---- 刷新结果 / 动作失败（VM toast） ----

    val toastRefreshCmc: String
    val toastRefreshCgKeyed: String
    val toastRefreshCgKeyless: String
    val refreshFailed: String
    val addFailed: String
    val saveFailed: String
    fun saveFrequencyFailed(reason: String): String

    // ---- 行情页（D21 增补） ----

    val watchTitle: String
    val watchSubDefault: String
    val watchSubCustom: String
    val watchEmpty: String
    val watchSearchLabel: String
    val watchSearchPlaceholder: String
    val watchAddAction: String
    val watchAddedHint: String
    val watchRemoveAction: String
    val watchColCoin: String
    val watchColSource: String
    val watchColUpdated: String
    val watchNoResult: String

    /** 候选检索中（浮层占位文案）。 */
    val watchSearching: String

    /** 现价列头（含计价法币，如「现价（USD）」/「Price (USD)」）。 */
    fun watchColPrice(fiat: String): String

    /** 自选已达上限（interaction.md §2.7「自选已达上限（50）」；上限取自数据层自选服务）。 */
    fun watchLimitReached(limit: Int): String

    /** 行情行数据源列的「无行情」档（[MarketCopy.sourceText] 的 null 分支；zh 逐字）。 */
    val sourceNoQuote: String

    // ---- 行情页命令区（M12 T12.1 原型第六页收尾：数据源徽章 + 自动轮询说明） ----

    /** 行情页数据源徽章（原型「数据源：CoinGecko · 专属额度 / · 无 Key 公共 API」）。 */
    fun watchSourceBadge(configured: Boolean): String

    /** 行情页自动轮询说明（interaction.md §2.7）。 */
    val watchAutoHint: String
}

object MarketStringsZh : MarketStrings {
    override val groupTitle = "行情数据源"
    override val cgRowLabel = "行情数据源（CoinGecko API Key）"
    override val cgRowSubConfigured = "已配置个人 Key · 专属额度约 30 次/分钟、1 万次/月"
    override val cgRowSubKeyless = "可选 · 未配置时使用无 Key 公共 API（约 30 次/分钟共享限流）"
    override val cmcRowLabel = "行情兜底（CoinMarketCap API Key）"
    override val cmcRowSubConfigured = "已配置 · 主源失败时兜底"
    override val cmcRowSubKeyless = "可选 · 未配置时无兜底，保持上次价格 + 时间戳"
    override val statusConfigured = "已配置"
    override val statusUnconfigured = "未配置"
    override val freqRowLabel = "行情刷新频率"
    override val freqRowSub = "窗口可见时生效（托盘按同步间隔降频）"

    override val cgModalTitle = "行情数据源 · CoinGecko API Key"
    override val cgModalConfigured =
        "当前已配置个人 Key（专属额度约 30 次/分钟、1 万次/月；月度额度达 80% 自动降一档并在状态栏提示）。"
    override val cgModalKeyless =
        "不填 = 使用无 Key 公共 API（开箱即用，按 IP 共享限流约 30 次/分钟，429 频发建议注册个人 Key）。"
    override val cgRegisterHint = "免费注册：CoinGecko Docs（Demo API 计划）（浏览器打开）。密钥仅存本地、不上传。"
    override val cmcModalTitle = "行情兜底 · CoinMarketCap API Key"
    override val cmcModalConfigured =
        "已配置兜底 Key：主源 CoinGecko 失败（网络/超时/429/额度耗尽/币种未收录）时，" +
            "按 CMC id 批量查询 quotes/latest 兜底，价格旁标注「数据源：CoinMarketCap」，主源恢复自动回落。"
    override val cmcModalKeyless = "可选 · 不填 = 无兜底，主源失败时保持上次价格并显示上次成功时间戳。"
    override val cmcRegisterHint = "免费注册：coinmarketcap.com/api（浏览器打开）。密钥仅存本地、不上传。"

    override val btnRemoveKey = "移除 Key"
    override val btnCancel = "取消"
    override val btnSaveCg = "保存并切换专属额度"
    override val btnSaveCmc = "保存"
    override val inputLabel = "API Key"
    override val emptyKeyErrorCg = "未填写 Key · 保持无 Key 公共 API"
    override val emptyKeyErrorCmc = "未填写 Key · 无兜底"
    override val toastCgSaved = "已切换 CoinGecko 专属额度（约 30 次/分钟、1 万次/月）"
    override val toastCgRemoved = "已切换回无 Key 公共 API"
    override val toastCmcSaved = "已配置 CoinMarketCap 兜底"
    override val toastCmcRemoved = "已移除 CoinMarketCap 兜底"
    override val toastKeylessSaveNoop = "Key 为空未修改"

    override val refreshButton = "立即刷新行情"
    override val refreshing = "刷新中…"
    override val sourcePrefix = "数据源"
    override val sourceCgKeyless = "CoinGecko · 无 Key 公共 API"
    override val sourceCgKeyed = "CoinGecko · 专属额度"
    override val sourceCmc = "CoinMarketCap（兜底）"
    override val lastRefreshPrefix = "上次价格"
    override val noRefreshYet = WzFormat.DASH
    override val noRefreshNote = "（尚无刷新）"
    override val noSuccessRefreshNote = "（尚无成功刷新）"
    override val quotaHintPrefix = "额度已达"
    override val quotaHintSuffix = "，已自动降档刷新频率"

    override fun freqOptionLabel(minutes: Int) = minutes.toString() + " 分钟"
    override fun lastRefreshTime(at: String) = lastRefreshPrefix + "：" + at
    override fun quotaHint(percent: Int) = quotaHintPrefix + " " + percent + "%" + quotaHintSuffix

    override fun networkUnreachable(source: String) = "网络不可达（" + source + "），已保持上次价格"
    override val rateLimitedShared = "CoinGecko 共享限流触发，已自动退避；建议注册免费个人 Key 获取专属额度"
    override val rateLimitedBackoff = "行情接口限流（429），已自动退避"
    override val invalidKeyCg = "CoinGecko API Key 已失效，请检查或更新"
    override val invalidKeyCmc = "CoinMarketCap API Key 已失效，请检查或更新"
    override val quotaExceededCmc = "CoinMarketCap 额度已达上限，已保持上次价格"
    override fun httpFailed(code: Int) = "行情请求失败（HTTP " + code + "），已保持上次价格"
    override fun untracked(coin: String) = "「" + coin + "」暂无行情数据"
    override val catalogInitFailed = "行情目录初始化失败，请稍后重试（详情见日志）"

    override val toastRefreshCmc = "行情已刷新（数据源：CoinMarketCap 兜底）"
    override val toastRefreshCgKeyed = "行情已刷新（CoinGecko 专属额度）"
    override val toastRefreshCgKeyless = "行情已刷新（CoinGecko 无 Key 公共 API）"
    override val refreshFailed = "行情刷新失败"
    override val addFailed = "添加失败"
    override val saveFailed = "保存失败"
    override fun saveFrequencyFailed(reason: String) = "保存刷新频率失败：" + reason

    override val watchTitle = "行情"
    override val watchSubDefault = "默认显示稳定币白名单（现金类）· 搜索可添加更多自选（重启保留）"
    override val watchSubCustom = "我的自选（settings 全局行持久化）· 搜索可添加 · 页面可见时按刷新频率自动更新"
    override val watchEmpty = "暂无自选，搜索添加币种"
    override val watchSearchLabel = "搜索币种（coins 目录）"
    override val watchSearchPlaceholder = "如 BTC / usdt / ethereum…"
    override val watchAddAction = "添加"
    override val watchAddedHint = "已在自选中"
    override val watchRemoveAction = "移除"
    override val watchColCoin = "币种"
    override val watchColSource = "数据源"
    override val watchColUpdated = "更新"
    override val watchNoResult = "无匹配币种"
    override val watchSearching = "检索中…"

    override fun watchColPrice(fiat: String) = "现价（" + fiat + "）"

    override fun watchLimitReached(limit: Int) = "自选已达上限（" + limit + "）"

    override fun watchSourceBadge(configured: Boolean) =
        "数据源：CoinGecko" + if (configured) " · 专属额度" else " · 无 Key 公共 API"

    override val watchAutoHint =
        "页面可见期间按行情刷新频率自动轮询（离开页面即停）· 自选为全局展示偏好，多账户共享"

    override val sourceNoQuote = "无行情"
}

object MarketStringsEn : MarketStrings {
    override val groupTitle = "Market data sources"
    override val cgRowLabel = "Market data source (CoinGecko API key)"
    override val cgRowSubConfigured = "Personal key configured · dedicated quota ≈ 30 calls/min, 10k/month"
    override val cgRowSubKeyless =
        "Optional · without a key the keyless public API is used (shared limit ≈ 30 calls/min)"
    override val cmcRowLabel = "Market fallback (CoinMarketCap API key)"
    override val cmcRowSubConfigured = "Configured · fallback when the primary source fails"
    override val cmcRowSubKeyless = "Optional · without it there is no fallback; last price + timestamp are kept"
    override val statusConfigured = "Configured"
    override val statusUnconfigured = "Not configured"
    override val freqRowLabel = "Refresh frequency"
    override val freqRowSub = "Applies while the window is visible (the tray slows down to the sync interval)"

    override val cgModalTitle = "Market source · CoinGecko API key"
    override val cgModalConfigured =
        "A personal key is configured (dedicated quota ≈ 30 calls/min, 10k/month; at 80% of the monthly " +
            "quota the frequency drops one step and the status bar shows a notice)."
    override val cgModalKeyless =
        "Leave empty = use the keyless public API (works out of the box, shared per-IP limit of about " +
            "30 calls/min; if 429s are frequent, register a personal key)."
    override val cgRegisterHint =
        "Free registration: CoinGecko Docs (Demo API plan) (opens in browser). The key is stored " +
            "locally only, never uploaded."
    override val cmcModalTitle = "Market fallback · CoinMarketCap API key"
    override val cmcModalConfigured =
        "Fallback key configured: when the primary source CoinGecko fails (network / timeout / 429 / quota " +
            "exhausted / coin not listed), quotes/latest is queried in batches by CMC id as a fallback, the price is " +
            "labelled “Source: CoinMarketCap”, and the app falls back automatically once the primary source recovers."
    override val cmcModalKeyless =
        "Optional · leave empty = no fallback; when the primary source fails the last price and its " +
            "last successful timestamp are kept."
    override val cmcRegisterHint =
        "Free registration: coinmarketcap.com/api (opens in browser). The key is stored locally only, never uploaded."

    override val btnRemoveKey = "Remove key"
    override val btnCancel = "Cancel"
    override val btnSaveCg = "Save and switch to dedicated quota"
    override val btnSaveCmc = "Save"
    override val inputLabel = "API key"
    override val emptyKeyErrorCg = "No key entered · keeping the keyless public API"
    override val emptyKeyErrorCmc = "No key entered · no fallback"
    override val toastCgSaved = "Switched to the CoinGecko dedicated quota (≈ 30 calls/min, 10k/month)"
    override val toastCgRemoved = "Switched back to the keyless public API"
    override val toastCmcSaved = "CoinMarketCap fallback configured"
    override val toastCmcRemoved = "CoinMarketCap fallback removed"
    override val toastKeylessSaveNoop = "Empty key — nothing changed"

    override val refreshButton = "Refresh quotes now"
    override val refreshing = "Refreshing…"
    override val sourcePrefix = "Source"
    override val sourceCgKeyless = "CoinGecko · keyless public API"
    override val sourceCgKeyed = "CoinGecko · dedicated quota"
    override val sourceCmc = "CoinMarketCap (fallback)"
    override val lastRefreshPrefix = "Last price"
    override val noRefreshYet = WzFormat.DASH
    override val noRefreshNote = "(not refreshed yet)"
    override val noSuccessRefreshNote = "(no successful refresh yet)"
    override val quotaHintPrefix = "Quota at"
    override val quotaHintSuffix = ", refresh frequency lowered"

    override fun freqOptionLabel(minutes: Int) = minutes.toString() + " min"
    override fun lastRefreshTime(at: String) = lastRefreshPrefix + ": " + at
    override fun quotaHint(percent: Int) = quotaHintPrefix + " " + percent + "%" + quotaHintSuffix

    override fun networkUnreachable(source: String) = "Network unreachable (" + source + "), keeping the last price"
    override val rateLimitedShared =
        "CoinGecko shared rate limit hit, backing off; register a free personal key for a dedicated quota"
    override val rateLimitedBackoff = "Quote API rate limited (429), backing off automatically"
    override val invalidKeyCg = "CoinGecko API key is no longer valid, please check or update it"
    override val invalidKeyCmc = "CoinMarketCap API key is no longer valid, please check or update it"
    override val quotaExceededCmc = "CoinMarketCap quota exhausted, keeping the last price"
    override fun httpFailed(code: Int) = "Quote request failed (HTTP " + code + "), keeping the last price"
    override fun untracked(coin: String) = "No quote data for “" + coin + "”"
    override val catalogInitFailed =
        "Failed to initialise the coin catalog, please try again later (see logs for details)"

    override val toastRefreshCmc = "Quotes refreshed (source: CoinMarketCap fallback)"
    override val toastRefreshCgKeyed = "Quotes refreshed (CoinGecko dedicated quota)"
    override val toastRefreshCgKeyless = "Quotes refreshed (CoinGecko keyless public API)"
    override val refreshFailed = "Quote refresh failed"
    override val addFailed = "Add failed"
    override val saveFailed = "Save failed"
    override fun saveFrequencyFailed(reason: String) = "Failed to save refresh frequency: " + reason

    override val watchTitle = "Markets"
    override val watchSubDefault =
        "Showing the stablecoin whitelist by default (cash-like) · search to add more to your " +
            "watchlist (kept after restart)"
    override val watchSubCustom =
        "My watchlist (persisted in the global settings row) · search to add · auto-refreshes at " +
            "the configured frequency while the page is visible"
    override val watchEmpty = "No watchlist coins yet — search above to add"
    override val watchSearchLabel = "Search coins (coins catalog)"
    override val watchSearchPlaceholder = "e.g. BTC / usdt / ethereum…"
    override val watchAddAction = "Add"
    override val watchAddedHint = "Already in the watchlist"
    override val watchRemoveAction = "Remove"
    override val watchColCoin = "Coin"
    override val watchColSource = "Source"
    override val watchColUpdated = "Updated"
    override val watchNoResult = "No matching coin"
    override val watchSearching = "Searching…"

    override fun watchColPrice(fiat: String) = "Price (" + fiat + ")"

    override fun watchLimitReached(limit: Int) = "Watchlist limit reached (" + limit + ")"

    override fun watchSourceBadge(configured: Boolean) =
        "Source: CoinGecko" + if (configured) " · personal quota" else " · public API (no key)"

    override val watchAutoHint =
        "Prices poll automatically at the configured refresh rate while this page is open " +
            "(stops when you leave) · the watchlist is a global display preference shared by all accounts"

    override val sourceNoQuote = "No quote"
}

val marketStrings: MarketStrings get() = if (I18n.isZh) MarketStringsZh else MarketStringsEn
