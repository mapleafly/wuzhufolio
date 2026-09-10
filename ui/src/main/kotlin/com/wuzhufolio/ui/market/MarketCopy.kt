package com.wuzhufolio.ui.market

import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.ui.i18n.marketStrings

/**
 * 行情模块 UI 文案门面（T5.5 · 以 P1 原型 wuzhufolio-light.html 设置页「行情数据源」分组为逐字基准；
 * 注册链接取两平台官方文档页，P7 发布前复核）。
 *
 * M12 T12.4：成员一律为**动态取值属性/函数**（`val X: String get() = marketStrings.x`），按当前语言解析；
 * 中文原文逐字节下沉到 `ui/i18n/MarketStrings.kt`（zh 档不变，en 档为对等译文），调用点保持不变。
 * 服务名 CoinGecko / CoinMarketCap 与注册 URL 不译，仍在本文件就地给出。
 */
@Suppress("TooManyFunctions") // 行情域文案门面（原 const 成员一一保留，调用点零改动）
object MarketCopy {

    // ---- 设置页行（原型 settingsRow） ----

    val GROUP_TITLE: String get() = marketStrings.groupTitle

    val CG_ROW_LABEL: String get() = marketStrings.cgRowLabel
    val CG_ROW_SUB_CONFIGURED: String get() = marketStrings.cgRowSubConfigured
    val CG_ROW_SUB_KEYLESS: String get() = marketStrings.cgRowSubKeyless
    val CMC_ROW_LABEL: String get() = marketStrings.cmcRowLabel
    val CMC_ROW_SUB_CONFIGURED: String get() = marketStrings.cmcRowSubConfigured
    val CMC_ROW_SUB_KEYLESS: String get() = marketStrings.cmcRowSubKeyless
    val STATUS_CONFIGURED: String get() = marketStrings.statusConfigured
    val STATUS_UNCONFIGURED: String get() = marketStrings.statusUnconfigured

    val FREQ_ROW_LABEL: String get() = marketStrings.freqRowLabel
    val FREQ_ROW_SUB: String get() = marketStrings.freqRowSub

    // ---- Key 弹窗（原型 openCgKey/openCmcKey 逐字口径） ----

    val CG_MODAL_TITLE: String get() = marketStrings.cgModalTitle
    val CG_MODAL_CONFIGURED: String get() = marketStrings.cgModalConfigured
    val CG_MODAL_KEYLESS: String get() = marketStrings.cgModalKeyless
    val CG_REGISTER_HINT: String get() = marketStrings.cgRegisterHint
    val CMC_MODAL_TITLE: String get() = marketStrings.cmcModalTitle
    val CMC_MODAL_CONFIGURED: String get() = marketStrings.cmcModalConfigured
    val CMC_MODAL_KEYLESS: String get() = marketStrings.cmcModalKeyless
    val CMC_REGISTER_HINT: String get() = marketStrings.cmcRegisterHint
    const val CG_REGISTER_URL = "https://docs.coingecko.com/reference/setting-up-your-api-key"
    const val CMC_REGISTER_URL = "https://coinmarketcap.com/api/"

    val BTN_REMOVE_KEY: String get() = marketStrings.btnRemoveKey
    val BTN_CANCEL: String get() = marketStrings.btnCancel
    val BTN_SAVE_CG: String get() = marketStrings.btnSaveCg
    val BTN_SAVE_CMC: String get() = marketStrings.btnSaveCmc
    val INPUT_LABEL: String get() = marketStrings.inputLabel
    val EMPTY_KEY_ERROR_CG: String get() = marketStrings.emptyKeyErrorCg
    val EMPTY_KEY_ERROR_CMC: String get() = marketStrings.emptyKeyErrorCmc
    val TOAST_CG_SAVED: String get() = marketStrings.toastCgSaved
    val TOAST_CG_REMOVED: String get() = marketStrings.toastCgRemoved
    val TOAST_CMC_SAVED: String get() = marketStrings.toastCmcSaved
    val TOAST_CMC_REMOVED: String get() = marketStrings.toastCmcRemoved
    val TOAST_KEYLESS_SAVE_NOOP: String get() = marketStrings.toastKeylessSaveNoop

    // ---- 数据源指示 / 刷新 ----

    val REFRESH_BUTTON: String get() = marketStrings.refreshButton
    val REFRESHING: String get() = marketStrings.refreshing
    val SOURCE_PREFIX: String get() = marketStrings.sourcePrefix
    val SOURCE_CG_KEYLESS: String get() = marketStrings.sourceCgKeyless
    val SOURCE_CG_KEYED: String get() = marketStrings.sourceCgKeyed
    val SOURCE_CMC: String get() = marketStrings.sourceCmc
    val LAST_REFRESH_PREFIX: String get() = marketStrings.lastRefreshPrefix
    val NO_REFRESH_YET: String get() = marketStrings.noRefreshYet
    val QUOTA_HINT_PREFIX: String get() = marketStrings.quotaHintPrefix
    val QUOTA_HINT_SUFFIX: String get() = marketStrings.quotaHintSuffix

    /** 刷新频率档位标签（如「5 分钟」/「5 min」）。 */
    fun freqOptionLabel(minutes: Int): String = marketStrings.freqOptionLabel(minutes)

    /** 上次价格时刻行（`上次价格：15:24` / `Last price: 15:24`）。 */
    fun lastRefreshTime(at: String): String = marketStrings.lastRefreshTime(at)

    /** 数据源指示的刷新注解（尚无刷新 / 尚无成功刷新）。 */
    fun refreshNote(hasResult: Boolean, hasSuccess: Boolean): String = when {
        !hasResult -> marketStrings.noRefreshNote
        !hasSuccess -> marketStrings.noSuccessRefreshNote
        else -> ""
    }

    /** 额度降档提示（`额度已达 85%，已自动降档刷新频率`）。 */
    fun quotaHint(percent: Int): String = marketStrings.quotaHint(percent)

    /** 行情刷新失败/兜底提示（api-contracts §4 错误码 B3/B4/B5/N1 文案映射）。 */
    fun errorText(error: MarketRefreshError): String = when (error) {
        is MarketRefreshError.Network ->
            marketStrings.networkUnreachable(sourceName(error.source))
        is MarketRefreshError.RateLimited ->
            if (error.keylessHint) {
                marketStrings.rateLimitedShared
            } else {
                marketStrings.rateLimitedBackoff
            }
        is MarketRefreshError.InvalidKey ->
            if (error.source == PriceSource.COINGECKO) {
                marketStrings.invalidKeyCg
            } else {
                marketStrings.invalidKeyCmc
            }
        is MarketRefreshError.QuotaExceeded ->
            marketStrings.quotaExceededCmc
        is MarketRefreshError.Http ->
            marketStrings.httpFailed(error.code)
        is MarketRefreshError.Untracked ->
            marketStrings.untracked(error.coin)
        is MarketRefreshError.Internal ->
            marketStrings.catalogInitFailed
    }

    /** 平台名（服务名，不译；中英同形）。 */
    fun sourceName(source: PriceSource): String =
        if (source == PriceSource.COINGECKO) "CoinGecko" else "CoinMarketCap"

    /** 行情行数据源列（null = 无行情 "--"）。 */
    fun sourceText(source: PriceSource?): String = when (source) {
        PriceSource.COINGECKO -> "CoinGecko"
        PriceSource.COINMARKETCAP -> marketStrings.sourceCmc
        null -> marketStrings.sourceNoQuote
    }

    // ---- 刷新结果 / 动作失败（VM toast） ----

    val TOAST_REFRESH_CMC: String get() = marketStrings.toastRefreshCmc
    val TOAST_REFRESH_CG_KEYED: String get() = marketStrings.toastRefreshCgKeyed
    val TOAST_REFRESH_CG_KEYLESS: String get() = marketStrings.toastRefreshCgKeyless
    val REFRESH_FAILED: String get() = marketStrings.refreshFailed
    val ADD_FAILED: String get() = marketStrings.addFailed
    val SAVE_FAILED: String get() = marketStrings.saveFailed

    /** 刷新频率保存失败提示（`保存刷新频率失败：<原因>`）。 */
    fun saveFrequencyFailed(reason: String): String = marketStrings.saveFrequencyFailed(reason)

    // ---- 行情页（D21 增补） ----

    val WATCH_TITLE: String get() = marketStrings.watchTitle
    val WATCH_SUB_DEFAULT: String get() = marketStrings.watchSubDefault
    val WATCH_SUB_CUSTOM: String get() = marketStrings.watchSubCustom
    val WATCH_EMPTY: String get() = marketStrings.watchEmpty
    val WATCH_SEARCH_LABEL: String get() = marketStrings.watchSearchLabel
    val WATCH_SEARCH_PLACEHOLDER: String get() = marketStrings.watchSearchPlaceholder
    val WATCH_ADD_ACTION: String get() = marketStrings.watchAddAction
    val WATCH_ADDED_HINT: String get() = marketStrings.watchAddedHint
    val WATCH_REMOVE_ACTION: String get() = marketStrings.watchRemoveAction
    val WATCH_COL_COIN: String get() = marketStrings.watchColCoin
    val WATCH_COL_SOURCE: String get() = marketStrings.watchColSource
    val WATCH_COL_UPDATED: String get() = marketStrings.watchColUpdated
    val WATCH_NO_RESULT: String get() = marketStrings.watchNoResult

    /** 现价列头（「现价（USD）」/「Price (USD)」）；调用点在行情页（MarketWatchPage），此处提供统一目录项。 */
    fun watchColPrice(fiat: String): String = marketStrings.watchColPrice(fiat)

    /** 自选已达上限提示（interaction.md §2.7「自选已达上限（50）」）。 */
    fun watchLimitReached(limit: Int): String = marketStrings.watchLimitReached(limit)

    /** 行情页数据源徽章（M12 T12.1）。 */
    fun watchSourceBadge(configured: Boolean): String = marketStrings.watchSourceBadge(configured)

    /** 行情页自动轮询说明（M12 T12.1）。 */
    val WATCH_AUTO_HINT: String get() = marketStrings.watchAutoHint
}
