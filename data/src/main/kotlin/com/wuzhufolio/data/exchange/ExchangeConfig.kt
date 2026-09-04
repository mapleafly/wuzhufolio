package com.wuzhufolio.data.exchange

/**
 * 交易所（Binance）端点/权重/限额常量（T6.1 · ADR-004 §2 / api-contracts §2.1；MVP 收敛 Binance）。
 * 权重/限额值取自 Binance REST 官方文档（现货），P5 联调按端点表核对。
 */
object ExchangeConfig {

    // ---- 端点（ADR-004 §2 表） ----

    const val BINANCE_BASE_URL = "https://api.binance.com"
    const val ACCOUNT_ENDPOINT = "/api/v3/account"
    const val MY_TRADES_ENDPOINT = "/api/v3/myTrades"
    const val EXCHANGE_INFO_ENDPOINT = "/api/v3/exchangeInfo"
    const val SERVER_TIME_ENDPOINT = "/api/v3/time"

    // ---- 认证（X-MBX-APIKEY + HMAC-SHA256 签名） ----

    const val API_KEY_HEADER = "X-MBX-APIKEY"
    const val SIGNATURE_PARAM = "signature"
    const val TIMESTAMP_PARAM = "timestamp"
    const val RECV_WINDOW_PARAM = "recvWindow"
    const val DEFAULT_RECV_WINDOW = 5000L

    // ---- 请求限额/权重 ----

    /** myTrades 单页条数上限（ADR-004 §2：limit ≤ 500；更早历史需 CSV 补录）。 */
    const val MY_TRADES_LIMIT = 500

    /** 时间戳偏差补偿开关：-1021 首次重试使用服务器时间偏移。 */
    const val MAX_TIMESTAMP_RETRY = 1

    const val USER_AGENT = "WuZhuFolio/0.1.0"
}
