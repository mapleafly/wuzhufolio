package com.wuzhufolio.data.exchange

import com.wuzhufolio.data.exchange.ExchangeConfig.ACCOUNT_ENDPOINT
import com.wuzhufolio.data.exchange.ExchangeConfig.API_KEY_HEADER
import com.wuzhufolio.data.exchange.ExchangeConfig.BINANCE_BASE_URL
import com.wuzhufolio.data.exchange.ExchangeConfig.EXCHANGE_INFO_ENDPOINT
import com.wuzhufolio.data.exchange.ExchangeConfig.MAX_TIMESTAMP_RETRY
import com.wuzhufolio.data.exchange.ExchangeConfig.MY_TRADES_ENDPOINT
import com.wuzhufolio.data.exchange.ExchangeConfig.MY_TRADES_LIMIT
import com.wuzhufolio.data.exchange.ExchangeConfig.SERVER_TIME_ENDPOINT
import com.wuzhufolio.data.exchange.ExchangeConfig.SIGNATURE_PARAM
import com.wuzhufolio.data.exchange.ExchangeConfig.TIMESTAMP_PARAM
import com.wuzhufolio.data.exchange.ExchangeConfig.USER_AGENT
import com.wuzhufolio.domain.exchange.Balance
import com.wuzhufolio.domain.exchange.ExchangeApiException
import com.wuzhufolio.domain.exchange.ExchangeCredentials
import com.wuzhufolio.domain.exchange.ExchangeError
import com.wuzhufolio.domain.exchange.ExchangeTrade
import com.wuzhufolio.domain.exchange.ExchangeAdapter
import com.wuzhufolio.domain.exchange.PairInfo
import com.wuzhufolio.domain.exchange.TradeSide
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Binance 现货只读适配器（T6.1 · ADR-004 §2 表 / api-contracts §2.1）。
 *
 * 认证：请求头 [API_KEY_HEADER] + 查询参数 timestamp(ms)/recvWindow + HMAC-SHA256(queryString, secret) 签名
 * （queryString = 参数名排序拼接——Binance 签名对顺序敏感，示例与文档均按字母序）。
 * 时间戳偏差（-1021）：首次自动取服务器时间（/time）校准后重试一次（[MAX_TIMESTAMP_RETRY]）。
 * 错误映射：业务码（JSON body code）-2015/-2014 → InvalidKey（B2）；-1003/HTTP 429/418 → RateLimited；
 * -1021 → TimestampSkew；-1022 → SignatureInvalid；网络 → Network；其余 → Http(code/status)。
 *
 * 解析策略同行情客户端（M5 先例）：kotlinx JsonElement 手工导航，不建逐端点 DTO；形状破坏抛 ExchangeApiException。
 * HTTP 层走 Ktor + OkHttp 引擎（系统代理），与行情链路各自独立客户端（两类 API 隔离）。
 */
@Suppress("TooManyFunctions", "SwallowedException") // 适配器动作面（4 端点 + 签名/解析/错误映射辅助）固有；解析辅助的小 catch 按「吞并归一」设计
class BinanceAdapter(
    private val http: HttpClient,
    private val credentials: ExchangeCredentials,
    private val baseUrl: String = BINANCE_BASE_URL,
) : ExchangeAdapter {

    override val exchangeName: String = EXCHANGE_NAME

    override suspend fun validateCredentials() {
        signedGet(ACCOUNT_ENDPOINT, emptyMap()) { }
    }

    override suspend fun fetchBalances(): List<Balance> = guarded {
        val body = signedGet(ACCOUNT_ENDPOINT, emptyMap()) { it.jsonObject }
        val out = mutableListOf<Balance>()
        for (element in body["balances"] as? JsonArray ?: return@guarded emptyList()) {
            val item = element.jsonObject
            val asset = item["asset"]?.jsonPrimitive?.content.orEmpty()
            val free = item["free"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
                ?.toBigDecimalOrNull()
            val locked = item["locked"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
                ?.toBigDecimalOrNull()
            if (asset.isNotEmpty() && free != null && locked != null) {
                out += Balance(asset, free, locked)
            }
        }
        out
    }

    override suspend fun fetchTrades(symbol: String, sinceId: Long?, limit: Int): List<ExchangeTrade> {
        require(limit in 1..MY_TRADES_LIMIT) { "myTrades limit must be 1..$MY_TRADES_LIMIT" }
        val params = buildMap {
            put("symbol", symbol)
            put("limit", limit.toString())
            if (sinceId != null) put("fromId", (sinceId + 1).toString())
        }
        val trades = guarded {
            signedGet(MY_TRADES_ENDPOINT, params) { parseTrades(it) }
        }
        return trades.sortedBy { it.id }
    }

    override suspend fun fetchPairs(): List<PairInfo> = guarded {
        val body = publicGet(EXCHANGE_INFO_ENDPOINT, emptyMap()) { it.jsonObject }
        val out = mutableListOf<PairInfo>()
        for (element in body["symbols"] as? JsonArray ?: return@guarded emptyList()) {
            val item = element.jsonObject
            val symbol = item["symbol"]?.jsonPrimitive?.content.orEmpty()
            val base = item["baseAsset"]?.jsonPrimitive?.content.orEmpty()
            val quote = item["quoteAsset"]?.jsonPrimitive?.content.orEmpty()
            val status = item["status"]?.jsonPrimitive?.content.orEmpty()
            if (symbol.isNotEmpty() && base.isNotEmpty() && quote.isNotEmpty()) {
                out += PairInfo(symbol, base, quote, status)
            }
        }
        out
    }

    // ---- 签名请求（-1021 服务器时间校准后重试一次） ----

    @Suppress("SwallowedException") // 业务码/网络全部归一为类型化 ExchangeApiException（脱敏口径，原始异常不入模型）
    private suspend fun <T> signedGet(endpoint: String, params: Map<String, String>, parse: (JsonElement) -> T): T {
        var attempt = 0
        while (true) {
            try {
                val query = buildSignedQuery(params)
                val body = http.get(baseUrl + endpoint + query) {
                    header(API_KEY_HEADER, credentials.apiKey)
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }.bodyOrThrow()
                return parse(body)
            } catch (e: ExchangeApiException) {
                if (e.kind is ExchangeError.TimestampSkew && attempt < MAX_TIMESTAMP_RETRY) {
                    attempt++
                    syncServerTime()
                    continue
                }
                throw e
            }
        }
    }

    /** 公开端点（exchangeInfo / server time；无签名头）。 */
    @Suppress("SwallowedException")
    private suspend fun <T> publicGet(
        endpoint: String,
        params: Map<String, String>,
        parse: (JsonElement) -> T,
    ): T = try {
        val suffix = params.entries.sortedBy { it.key }.joinToString("&", prefix = "?") { (k, v) -> k + "=" + v }
        val body = http.get(baseUrl + endpoint + suffix) {
            header(HttpHeaders.UserAgent, USER_AGENT)
        }.bodyOrThrow()
        parse(body)
    } catch (e: ExchangeApiException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ExchangeApiException(ExchangeError.Network)
    }

    private var timeOffsetMillis: Long = 0L

    private suspend fun syncServerTime() {
        timeOffsetMillis = try {
            val body = publicGet(SERVER_TIME_ENDPOINT, emptyMap()) { it.jsonObject }
            val server = body["serverTime"]?.jsonPrimitive?.content?.toLongOrNull()
            server?.minus(System.currentTimeMillis()) ?: 0L
        } catch (e: ExchangeApiException) {
            0L
        }
    }

    /** 参数名排序拼接 → HMAC-SHA256(secret) 签名 → "?…&signature=…"（参数值均为 ASCII 无需 URL 编码）。 */
    private fun buildSignedQuery(params: Map<String, String>): String {
        val all = buildMap {
            putAll(params)
            put(TIMESTAMP_PARAM, (System.currentTimeMillis() + timeOffsetMillis).toString())
        }
        val query = all.entries.sortedBy { it.key }
            .joinToString("&") { (k, v) -> k + "=" + v }
        val signature = hmacSha256(query, credentials.secretKey)
        return "?" + query + "&" + SIGNATURE_PARAM + "=" + signature
    }

    private fun hmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    // ---- 响应解析 + 错误映射 ----

    /**
     * 响应体 → JsonElement；非 2xx 解析 JSON body 中的业务码（code/-xxxx）映射类型化错误；
     * body 不可解析按 HTTP 状态映射。
     */
    @Suppress("SwallowedException")
    private suspend fun HttpResponse.bodyOrThrow(): JsonElement {
        if (!status.isSuccess()) {
            val text = try {
                bodyAsText()
            } catch (e: Exception) {
                ""
            }
            val code = try {
                (Json.parseToJsonElement(text).jsonObject["code"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content?.toLongOrNull()
            } catch (e: Exception) {
                null
            }
            throw ExchangeApiException(mapError(code, status.value))
        }
        return try {
            Json.parseToJsonElement(bodyAsText())
        } catch (e: Exception) {
            throw ExchangeApiException(ExchangeError.Http(status.value))
        }
    }

    /** Binance 业务码 + HTTP 状态 → 类型化错误（ADR-004 §2 / api-contracts §2.1 映射表）。 */
    private fun mapError(code: Long?, httpStatus: Int): ExchangeError = when (code) {
        -2015L, -2014L -> ExchangeError.InvalidKey
        -1003L -> ExchangeError.RateLimited
        -1021L -> ExchangeError.TimestampSkew
        -1022L -> ExchangeError.SignatureInvalid
        else -> when (httpStatus) {
            429, 418 -> ExchangeError.RateLimited
            401, 403 -> ExchangeError.InvalidKey
            else -> ExchangeError.Http(httpStatus)
        }
    }

    private fun parseTrades(body: JsonElement): List<ExchangeTrade> {
        val out = mutableListOf<ExchangeTrade>()
        for (element in body.jsonArray) {
            val trade = parseTrade(element.jsonObject)
            if (trade != null) out += trade
        }
        return out
    }

    /** 单条成交装配（形状缺项 → null 跳过，不中断整批）。 */
    private fun parseTrade(item: JsonObject): ExchangeTrade? {
        val id = item["id"]?.jsonPrimitive?.content?.toLongOrNull()
        val orderId = item["orderId"]?.jsonPrimitive?.content?.toLongOrNull()
        val symbol = item["symbol"]?.jsonPrimitive?.content.orEmpty()
        val price = item["price"]?.jsonPrimitive?.content?.trim()
            ?.takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
        val qty = item["qty"]?.jsonPrimitive?.content?.trim()
            ?.takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
        val fee = item["commission"]?.jsonPrimitive?.content?.trim()
            ?.takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
        val timeMs = item["time"]?.jsonPrimitive?.content?.toLongOrNull()
        val isBuyer = item["isBuyer"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
        val complete = id != null && symbol.isNotEmpty() && price != null &&
            qty != null && fee != null && timeMs != null
        if (!complete) return null
        return ExchangeTrade(
            id = id,
            orderId = orderId ?: id,
            symbol = symbol,
            side = if (isBuyer == true) TradeSide.BUY else TradeSide.SELL,
            price = price,
            qty = qty,
            fee = fee,
            feeAsset = item["commissionAsset"]?.jsonPrimitive?.content.orEmpty(),
            time = Instant.ofEpochMilli(timeMs),
        )
    }

    /** 非业务异常统一归一为类型化错误（网络/解析；取消透传）。 */
    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (e: ExchangeApiException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ExchangeApiException(ExchangeError.Network)
    }

    companion object {
        const val EXCHANGE_NAME = "BINANCE"
    }
}

/** 生产客户端：OkHttp 引擎 + 超时 + UA（同行情 M5 口径；两类 API 各自独立客户端实例——隔离验证项）。 */
fun newOkHttpExchangeClient(): HttpClient = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
    }
    defaultRequest { header(HttpHeaders.UserAgent, ExchangeConfig.USER_AGENT) }
}
