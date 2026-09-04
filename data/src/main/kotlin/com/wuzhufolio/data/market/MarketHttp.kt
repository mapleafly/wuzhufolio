package com.wuzhufolio.data.market

import com.wuzhufolio.data.market.MarketConfig.CG_BASE_URL
import com.wuzhufolio.data.market.MarketConfig.CG_KEY_HEADER
import com.wuzhufolio.data.market.MarketConfig.CMC_BASE_URL
import com.wuzhufolio.data.market.MarketConfig.CMC_KEY_HEADER
import com.wuzhufolio.data.market.MarketConfig.USER_AGENT
import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import com.wuzhufolio.domain.market.CmcMapCoin
import com.wuzhufolio.domain.market.CmcQuote
import com.wuzhufolio.domain.market.MarketApiException
import com.wuzhufolio.domain.market.MarketCandle
import com.wuzhufolio.domain.market.MarketDataClient
import com.wuzhufolio.domain.market.MarketQuote
import com.wuzhufolio.domain.market.MarketRank
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.PriceSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.Instant

/**
 * 行情 HTTP 基础件（T5.1 · ADR-003 §1–3 真实端点；Ktor client + OkHttp 引擎——
 * [newOkHttpClient] 供 app 装配；测试用 ktor MockEngine 直注入）。
 *
 * 解析策略：不引入逐端点 DTO（平台返回字段随版本漂移），以 kotlinx JsonElement 手工导航——
 * 字段缺失按缺席处理（未收录币直接缺席），形状破坏抛 [MarketApiException]。
 */

/** 生产客户端：OkHttp 引擎 + 超时 + UA（OkHttp 原生遵循 JVM ProxySelector——ADR-001/003）。 */
fun newOkHttpMarketClient(): HttpClient = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
    }
    defaultRequest { header(HttpHeaders.UserAgent, USER_AGENT) }
}

/** 网络层异常 → Network 错误（429 等业务状态映射在各 provider）。 */
@Suppress("SwallowedException") // 全部非业务异常归一为 Network 错误（原始异常不入错误模型——脱敏口径）
internal suspend fun <T> guardedHttp(source: PriceSource, block: suspend () -> T): T = try {
    block()
} catch (e: MarketApiException) {
    throw e
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw MarketApiException(MarketRefreshError.Network(source))
}

/** 状态码 → 平台错误（429/401/402/404(历史币)/其他 HTTP）。 */
internal fun httpError(
    status: HttpStatusCode,
    source: PriceSource,
    keylessHint: Boolean = false,
    coin: String? = null,
): MarketRefreshError {
    val code = status.value
    return when {
        code == 429 -> MarketRefreshError.RateLimited(source, keylessHint)
        code == 401 -> MarketRefreshError.InvalidKey(source)
        code == 402 -> MarketRefreshError.QuotaExceeded(source)
        code == 404 && coin != null -> MarketRefreshError.Untracked(coin)
        else -> MarketRefreshError.Http(code, source)
    }
}

/** 响应体解析；非 2xx 抛映射错误；体不可解析按 Http 错误。 */
internal suspend fun HttpResponse.bodyJsonOrThrow(
    source: PriceSource,
    keylessHint: Boolean,
    coin: String? = null,
): JsonElement {
    if (!status.isSuccess()) {
        throw MarketApiException(httpError(status, source, keylessHint, coin))
    }
    return parseBody(source)
}

/** 响应体 JSON 解析（形状破坏归一为 Http 错误，避免解析细节污染错误模型）。 */
@Suppress("SwallowedException")
private suspend fun HttpResponse.parseBody(source: PriceSource): JsonElement = try {
    Json.parseToJsonElement(bodyAsText())
} catch (e: Exception) {
    throw MarketApiException(MarketRefreshError.Http(status.value, source))
}

internal fun JsonObject.priceOrNull(key: String): BigDecimal? =
    (this[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

internal fun JsonPrimitive.bigDecimalOrNull(): BigDecimal? =
    content.trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

/** CG /market_chart 的 prices 序列：[[epochMillis, price], ...]（形状非法点丢弃）。 */
internal fun JsonArray.pricePoints(): List<Pair<Instant, BigDecimal>> = mapNotNull { element ->
    val point = element.jsonArray
    if (point.size < 2) return@mapNotNull null
    val ms = (point[0] as? JsonPrimitive)?.content?.toLongOrNull() ?: return@mapNotNull null
    val price = (point[1] as? JsonPrimitive)?.bigDecimalOrNull() ?: return@mapNotNull null
    Instant.ofEpochMilli(ms) to price
}

/** CoinGecko 主源（ADR-003 §2：/simple/price、/coins/list、/market_chart/range、/coins/markets）。 */
class CoingeckoMarketClient(private val http: HttpClient) : MarketDataClient {

    override suspend fun fetchCurrent(
        coins: List<String>,
        fiats: List<String>,
        apiKey: String?,
    ): List<MarketQuote> = guardedHttp(PriceSource.COINGECKO) {
        require(coins.size <= MarketConfig.CG_CURRENT_BATCH) { "cg batch exceeds ${MarketConfig.CG_CURRENT_BATCH}" }
        val body = http.get("$CG_BASE_URL/simple/price") {
            parameter("ids", coins.joinToString(","))
            parameter("vs_currencies", fiats.joinToString(","))
            apiKey?.let { header(CG_KEY_HEADER, it) }
        }.bodyJsonOrThrow(PriceSource.COINGECKO, keylessHint = apiKey == null)
        val now = Instant.now()
        val out = mutableListOf<MarketQuote>()
        for ((coin, values) in body.jsonObject) {
            for ((fiat, priceEl) in values.jsonObject) {
                val price = priceEl.jsonPrimitive.bigDecimalOrNull()
                if (price != null && price.signum() > 0) {
                    out += MarketQuote(coin, fiat.uppercase(), price, PriceSource.COINGECKO, now)
                }
            }
        }
        out
    }

    override suspend fun fetchHistory(
        coin: String,
        fiat: String,
        from: Instant,
        to: Instant,
        apiKey: String?,
    ): List<MarketCandle> = guardedHttp(PriceSource.COINGECKO) {
        val body = http.get("$CG_BASE_URL/coins/$coin/market_chart/range") {
            parameter("vs_currency", fiat)
            parameter("from", from.epochSecond)
            parameter("to", to.epochSecond)
            apiKey?.let { header(CG_KEY_HEADER, it) }
        }.bodyJsonOrThrow(PriceSource.COINGECKO, keylessHint = apiKey == null, coin = coin)
        (body.jsonObject["prices"] as? JsonArray)
            ?.pricePoints()
            .orEmpty()
            .map { (at, price) -> MarketCandle(at, price) }
            .sortedBy { it.at }
    }

    override suspend fun fetchDirectory(apiKey: String?): List<CoinDirectoryEntry> =
        guardedHttp(PriceSource.COINGECKO) {
            val body = http.get("$CG_BASE_URL/coins/list") {
                parameter("include_platform", true)
                apiKey?.let { header(CG_KEY_HEADER, it) }
            }.bodyJsonOrThrow(PriceSource.COINGECKO, keylessHint = apiKey == null)
            val out = mutableListOf<CoinDirectoryEntry>()
            for (element in body.jsonArray) {
                val item = element.jsonObject
                val cgId = item["id"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                val symbol = item["symbol"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                if (cgId != null && symbol != null) {
                    val name = item["name"]?.jsonPrimitive?.content.orEmpty()
                    val contracts = (item["platforms"] as? JsonObject)
                        ?.mapValues { (_, v) -> v.jsonPrimitive.content }
                        ?.filterValues { it.isNotEmpty() } ?: emptyMap()
                    out += CoinDirectoryEntry(cgId, symbol, name, contracts)
                }
            }
            out
        }

    override suspend fun fetchMarketRanking(page: Int, perPage: Int, apiKey: String?): List<MarketRank> =
        guardedHttp(PriceSource.COINGECKO) {
            val body = http.get("$CG_BASE_URL/coins/markets") {
                parameter("vs_currency", "usd")
                parameter("order", "market_cap_desc")
                parameter("per_page", perPage)
                parameter("page", page)
                parameter("sparkline", false)
                apiKey?.let { header(CG_KEY_HEADER, it) }
            }.bodyJsonOrThrow(PriceSource.COINGECKO, keylessHint = apiKey == null)
            val out = mutableListOf<MarketRank>()
            for (element in body.jsonArray) {
                val item = element.jsonObject
                val cgId = item["id"]?.jsonPrimitive?.content
                val rank = item["market_cap_rank"]?.jsonPrimitive?.content?.toIntOrNull()
                if (cgId != null && rank != null && rank > 0) out += MarketRank(cgId, rank)
            }
            out
        }
}

/** CoinMarketCap 兜底源（ADR-003 §3：/quotes/latest、/cryptocurrency/map；无免费历史端点）。 */
class CmcMarketClient(private val http: HttpClient) : MarketDataClient {

    override suspend fun fetchCmcCurrent(
        cmcIds: List<Long>,
        fiats: List<String>,
        apiKey: String,
    ): List<CmcQuote> = guardedHttp(PriceSource.COINMARKETCAP) {
        require(cmcIds.size <= MarketConfig.CMC_BATCH_LIMIT) { "cmc batch exceeds ${MarketConfig.CMC_BATCH_LIMIT}" }
        require(cmcIds.isNotEmpty())
        val body = http.get("$CMC_BASE_URL/cryptocurrency/quotes/latest") {
            parameter("id", cmcIds.joinToString(","))
            parameter("convert", fiats.joinToString(","))
            header(CMC_KEY_HEADER, apiKey)
        }.bodyJsonOrThrow(PriceSource.COINMARKETCAP, keylessHint = false)
        val now = Instant.now()
        val out = mutableListOf<CmcQuote>()
        for ((cmcIdText, entry) in body.jsonObject["data"]?.jsonObject ?: emptyMap()) {
            val cmcId = cmcIdText.toLongOrNull()
            val quotes = entry.jsonObject["quote"]?.jsonObject
            if (cmcId != null && quotes != null) {
                for ((fiat, quoteObj) in quotes) {
                    val price = quoteObj.jsonObject.priceOrNull("price")
                    if (price != null && price.signum() > 0) {
                        out += CmcQuote(cmcId, fiat.uppercase(), price, PriceSource.COINMARKETCAP, now)
                    }
                }
            }
        }
        out
    }

    override suspend fun fetchCmcMap(apiKey: String, start: Long): List<CmcMapCoin> =
        guardedHttp(PriceSource.COINMARKETCAP) {
            val body = http.get("$CMC_BASE_URL/cryptocurrency/map") {
                parameter("start", start)
                parameter("limit", MarketDataClient.CMC_PAGE_LIMIT)
                header(CMC_KEY_HEADER, apiKey)
            }.bodyJsonOrThrow(PriceSource.COINMARKETCAP, keylessHint = false)
            val out = mutableListOf<CmcMapCoin>()
            for (element in body.jsonObject["data"]?.jsonArray ?: emptyList<JsonElement>()) {
                val item = element.jsonObject
                val cmcId = item["id"]?.jsonPrimitive?.content?.toLongOrNull()
                val symbol = item["symbol"]?.jsonPrimitive?.content.orEmpty()
                if (cmcId != null && symbol.isNotEmpty()) {
                    val name = item["name"]?.jsonPrimitive?.content.orEmpty()
                    out += CmcMapCoin(cmcId, symbol, name)
                }
            }
            out
        }
}
