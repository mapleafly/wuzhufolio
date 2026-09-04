package com.wuzhufolio.data.market

/**
 * 行情设置键与端点常量（T5.1/T5.4 · ADR-003 / ADR-002 §2.1 方案甲 / api-contracts §1）。
 * settings 全局行键名；端点/请求头/参数对齐 ADR-003 契约表（真实 API）。
 */
object MarketConfig {

    // ---- settings 全局行键（ADR-002 §2.1：CG/CMC Key 设备密钥加密存全局行、不进 .cpro 备份） ----

    const val KEY_CG = "market.coingecko_key"
    const val KEY_CMC = "market.cmc_key"
    const val KEY_REFRESH_MINUTES = "market.refresh_minutes"
    const val KEY_QUOTA = "market.quota"

    /** DeviceSecretCipher purpose（AAD 条目隔离）。 */
    const val PURPOSE_CG = "market.coingecko"
    const val PURPOSE_CMC = "market.cmc"

    // ---- CoinGecko（主源，免费 Demo 档；无 Key 公共模式不带认证头） ----

    const val CG_BASE_URL = "https://api.coingecko.com/api/v3"
    const val CG_KEY_HEADER = "x-cg-demo-api-key"

    // ---- CoinMarketCap（兜底，免费 Basic 档；Key 必须） ----

    const val CMC_BASE_URL = "https://pro-api.coinmarketcap.com/v1"
    const val CMC_KEY_HEADER = "X-CMC_PRO_API_KEY"

    /** CMC quotes/latest 单次 ≤100 币（ADR-003 §1.2）。 */
    const val CMC_BATCH_LIMIT = 100

    /** CG 当前价单请求上限（URL 长度保护，分批请求）。 */
    const val CG_CURRENT_BATCH = 100

    /** 持仓候选币集缺省（调用方未给币集时：现金白名单 4 币，开箱验证刷新链路；持仓币集注入点归 M7/M12）。 */
    val DEFAULT_FALLBACK_COINS: List<String> =
        listOf("tether", "usd-coin", "dai", "true-usd")

    const val USER_AGENT = "WuZhuFolio/0.1.0"
}
