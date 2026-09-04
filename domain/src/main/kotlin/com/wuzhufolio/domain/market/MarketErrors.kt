package com.wuzhufolio.domain.market

/**
 * 行情刷新错误（ADR-003 错误映射 / PRD 统一异常处理 B3/B4/B5/N1/N2；
 * UI 文案映射表见 ui/market/MarketCopy（M10 状态栏/提示汇合时复用）。
 *
 * 提示语义：主源（CG）失败 → 可切 CMC 兜底（已配置）；无兜底 → 保持上次价格并显示上次成功时间戳
 * （[MarketRefreshResult.at]）；429 频发且未配置个人 Key → 提示注册免费个人 Key（keylessHint）。
 */
sealed interface MarketRefreshError {

    /** 网络不可达/超时（PRD N1/N2：状态栏断链 + 保留上次数据）。 */
    data class Network(val source: PriceSource) : MarketRefreshError

    /** 429 限流（退避后仍失败）。[keylessHint] = 无 Key 公共 API 被共享限流误伤（提示注册个人 Key）。 */
    data class RateLimited(val source: PriceSource, val keylessHint: Boolean) : MarketRefreshError

    /** 401 Key 失效（CG 个人 Key / CMC Key）。 */
    data class InvalidKey(val source: PriceSource) : MarketRefreshError

    /** 额度耗尽（CMC 402；CG 月额度的 100% 由本地计数治理）。 */
    data class QuotaExceeded(val source: PriceSource) : MarketRefreshError

    /** 其他 HTTP 错误。 */
    data class Http(val code: Int, val source: PriceSource) : MarketRefreshError

    /** 目标币在任何可用来源均无价（「无行情」提示；不出现在 refresh 结果 error——见 untracked 语义）。 */
    data class Untracked(val coin: String) : MarketRefreshError
}

/** 平台级 HTTP 异常（数据层内部/边缘使用；用例层一律经 [MarketRefreshResult.error] 呈现）。 */
class MarketApiException(val kind: MarketRefreshError) :
    RuntimeException(kind.toString())
