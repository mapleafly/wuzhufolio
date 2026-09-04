package com.wuzhufolio.data.exchange

import com.wuzhufolio.domain.exchange.ExchangeApiException
import com.wuzhufolio.domain.exchange.ExchangeCredentials
import com.wuzhufolio.domain.exchange.ExchangeError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * T6.1 BinanceAdapter 验收（MockEngine）：真实端点/认证头/签名参数 + 错误映射
 * （密钥失效 -2015 / 429 限流 / -1021 时间戳自动对时重试 / -1022 签名错误）+ myTrades/exchangeInfo/balances 载荷解析。
 * 端点契约对齐 ADR-004 §2 与 api-contracts §2.1。
 */
class BinanceAdapterTest {

    private var lastUrl: String = ""
    private var lastHeaders: Map<String, String> = emptyMap()
    private var serverTimeCalls = 0

    private fun decodedUrl(): String = java.net.URLDecoder.decode(lastUrl, Charsets.UTF_8)

    private fun mockClient(handler: io.ktor.client.engine.mock.MockRequestHandler): HttpClient =
        HttpClient(MockEngine { request ->
            lastUrl = request.url.toString()
            lastHeaders = request.headers.entries().associate { it.key to it.value.joinToString(",") }
            handler(request)
        }) { expectSuccess = false }

    @AfterTest
    fun reset() {
        lastUrl = ""
        lastHeaders = emptyMap()
        serverTimeCalls = 0
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.jsonOf(text: String) = respond(
        content = text,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private val creds = ExchangeCredentials("test-api-key", "test-secret-key")

    private val accountBody = """{"makerCommission":10,"balances":[
        {"asset":"BTC","free":"0.5","locked":"0"},
        {"asset":"USDT","free":"1200.0","locked":"50"},
        {"asset":"ETH","free":"0","locked":"0"}
        ]}"""

    @Test
    fun `validate credentials hits signed account endpoint with headers and signature`() = runBlocking {
        val http = mockClient { jsonOf(accountBody) }
        BinanceAdapter(http, creds).validateCredentials()
        assertTrue(decodedUrl().contains("/api/v3/account"), decodedUrl())
        assertTrue(lastUrl.contains(SIGNATURE + "="), "签名参数必须存在: " + lastUrl)
        assertTrue(lastUrl.contains("timestamp="), "时间戳参数必须存在: " + lastUrl)
        assertTrue(lastHeaders["X-MBX-APIKEY"] == "test-api-key")
    }

    @Test
    fun `fetch balances parses nonzero and zero assets`() = runBlocking {
        val http = mockClient { jsonOf(accountBody) }
        val balances = BinanceAdapter(http, creds).fetchBalances()
        assertEquals(3, balances.size)
        val btc = balances.first { it.asset == "BTC" }
        assertEquals(0, "0.5".toBigDecimal().compareTo(btc.free))
        assertEquals(0, "1200.0".toBigDecimal().compareTo(balances.first { it.asset == "USDT" }.free))
    }

    @Test
    fun `invalid key maps to InvalidKey`() = runBlocking {
        val http = mockClient {
            respond(
                content = """{"code":-2015,"msg":"This API KEY is not valid"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val e = assertFailsWith<ExchangeApiException> { BinanceAdapter(http, creds).validateCredentials() }
        assertTrue(e.kind is ExchangeError.InvalidKey, e.kind.toString())
    }

    @Test
    fun `http 429 maps to RateLimited`() = runBlocking {
        val http = mockClient {
            respond(
                content = """{"code":-1003,"msg":"Too many requests"}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val e = assertFailsWith<ExchangeApiException> { BinanceAdapter(http, creds).fetchBalances() }
        assertTrue(e.kind is ExchangeError.RateLimited, e.kind.toString())
    }

    @Test
    fun `timestamp skew syncs server time and retries once`() = runBlocking {
        val http = mockClient { request ->
            val url = request.url.toString()
            when {
                url.contains("/api/v3/time") -> {
                    serverTimeCalls++
                    jsonOf("""{"serverTime":1789999999999}""")
                }
                else -> respond(
                    content = """{"code":-1021,"msg":"Timestamp outside recvWindow"}""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val e = assertFailsWith<ExchangeApiException> { BinanceAdapter(http, creds).fetchBalances() }
        assertTrue(e.kind is ExchangeError.TimestampSkew, e.kind.toString())
        assertTrue(serverTimeCalls >= 1, "-1021 后应自动取服务器时间")
    }

    @Test
    fun `signature invalid maps to SignatureInvalid`() = runBlocking {
        val http = mockClient {
            respond(
                content = """{"code":-1022,"msg":"Signature for this request is not valid"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val e = assertFailsWith<ExchangeApiException> { BinanceAdapter(http, creds).fetchBalances() }
        assertTrue(e.kind is ExchangeError.SignatureInvalid, e.kind.toString())
    }

    @Test
    fun `network failure maps to Network`() = runBlocking {
        val http = mockClient { throw java.io.IOException("boom") }
        val e = assertFailsWith<ExchangeApiException> { BinanceAdapter(http, creds).fetchBalances() }
        assertTrue(e.kind is ExchangeError.Network, e.kind.toString())
    }

    @Test
    fun `fetch trades parses fills with side fee and since cursor`() = runBlocking {
        val http = mockClient { jsonOf("""[
            {"id":102,"orderId":55,"symbol":"BTCUSDT","price":"50000.0","qty":"0.01",
            "quoteQty":"500","commission":"0.00001","commissionAsset":"BTC",
            "time":1700000000000,"isBuyer":true,"isMaker":false},
            {"id":103,"orderId":56,"symbol":"BTCUSDT","price":"51000.0","qty":"0.02",
            "quoteQty":"1020","commission":"0.5","commissionAsset":"USDT",
            "time":1700000001000,"isBuyer":false,"isMaker":true}
            ]""") }
        val trades = BinanceAdapter(http, creds).fetchTrades("BTCUSDT", sinceId = 101, limit = 500)
        assertEquals(2, trades.size)
        assertEquals(listOf(102L, 103L), trades.map { it.id })
        assertEquals(com.wuzhufolio.domain.exchange.TradeSide.BUY, trades[0].side)
        assertEquals("BTC", trades[0].feeAsset)
        assertTrue(decodedUrl().contains("symbol=BTCUSDT"))
        assertTrue(decodedUrl().contains("fromId=102"))
        assertTrue(decodedUrl().contains("limit=500"))
    }

    @Test
    fun `fetch pairs parses exchange info registry`() = runBlocking {
        val http = mockClient { jsonOf("""{"symbols":[
            {"symbol":"BTCUSDT","baseAsset":"BTC","quoteAsset":"USDT","status":"TRADING"},
            {"symbol":"ETHUSDT","baseAsset":"ETH","quoteAsset":"USDT","status":"BREAK"}
            ]}""") }
        val pairs = BinanceAdapter(http, creds).fetchPairs()
        assertEquals(2, pairs.size)
        assertEquals("BTCUSDT", pairs[0].symbol)
        assertEquals("TRADING", pairs[0].status)
    }

    private companion object {
        const val SIGNATURE = "signature"
    }
}
