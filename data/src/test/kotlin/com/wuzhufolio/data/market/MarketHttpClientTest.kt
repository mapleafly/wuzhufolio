package com.wuzhufolio.data.market

import com.wuzhufolio.domain.market.MarketApiException
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.PriceSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * T5.1 客户端验收（MockEngine）：真实端点路径/请求头/参数 + 429/401/402/404/网络分支 + 载荷解析。
 * 端点契约对齐 ADR-003 §2/§3 与 api-contracts §1。
 */
class MarketHttpClientTest {

    private var lastUrl: String = ""
    private var lastHeaders: Map<String, String> = emptyMap()

    private fun decodedUrl(): String = java.net.URLDecoder.decode(lastUrl, Charsets.UTF_8)

    private fun mockClient(handler: io.ktor.client.engine.mock.MockRequestHandler): HttpClient =
        HttpClient(MockEngine { request ->
            lastUrl = request.url.toString()
            lastHeaders = request.headers.entries().associate { it.key to it.value.joinToString(",") }
            handler(request)
        }) {
            expectSuccess = false
        }

    @AfterTest
    fun reset() {
        lastUrl = ""
        lastHeaders = emptyMap()
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.jsonOf(text: String) = respond(
        content = text,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    // ---- CG 当前价 ----

    @Test
    fun `cg current parses quotes and sends batch params`() = runBlocking {
        val http = mockClient {
            jsonOf("""{"bitcoin":{"usd":61000.5,"eur":56000},"tether":{"usd":1.0},"unknown-coin":{}}""")
        }
        val client = CoingeckoMarketClient(http)
        val quotes = client.fetchCurrent(listOf("bitcoin", "tether"), listOf("USD", "EUR"), apiKey = null)
        assertEquals(3, quotes.size)
        assertTrue(quotes.all { it.source == PriceSource.COINGECKO })
        val btcUsd = quotes.first { it.coin == "bitcoin" && it.fiat == "USD" }
        assertEquals(0, "61000.5".bd().compareTo(btcUsd.price))
        assertTrue(decodedUrl().contains("/simple/price"))
        assertTrue(decodedUrl().contains("ids=bitcoin,tether"))
        assertTrue(decodedUrl().contains("vs_currencies=USD,EUR"))
    }

    @Test
    fun `cg current attaches key header when provided and omits when keyless`() = runBlocking {
        val client = CoingeckoMarketClient(mockClient { jsonOf("""{"bitcoin":{"usd":1}}""") })
        runBlocking { client.fetchCurrent(listOf("bitcoin"), listOf("USD"), apiKey = "demo-key-1") }
        assertEquals("demo-key-1", lastHeaders["x-cg-demo-api-key"])
        runBlocking { client.fetchCurrent(listOf("bitcoin"), listOf("USD"), apiKey = null) }
        assertTrue("x-cg-demo-api-key" !in lastHeaders)
    }

    @Test
    fun `cg rate limited maps with keyless hint`() = runBlocking {
        val client = CoingeckoMarketClient(
            mockClient { respond("", HttpStatusCode.TooManyRequests) },
        )
        val keyless = assertFailsWith<MarketApiException> {
            client.fetchCurrent(listOf("bitcoin"), listOf("USD"), apiKey = null)
        }
        val rateLimited = assertIs<MarketRefreshError.RateLimited>(keyless.kind)
        assertTrue(rateLimited.keylessHint, "无 Key 公共 API 共享限流提示注册个人 Key")
        val withKey = assertFailsWith<MarketApiException> {
            client.fetchCurrent(listOf("bitcoin"), listOf("USD"), apiKey = "k")
        }
        assertIs<MarketRefreshError.RateLimited>(withKey.kind).let {
            assertEquals(false, it.keylessHint)
        }
    }

    @Test
    fun `cg invalid key and generic http map respectively`() = runBlocking {
        val client401 = CoingeckoMarketClient(mockClient { respond("", HttpStatusCode.Unauthorized) })
        val e1 = assertFailsWith<MarketApiException> {
            client401.fetchCurrent(listOf("bitcoin"), listOf("USD"), apiKey = "bad")
        }
        assertIs<MarketRefreshError.InvalidKey>(e1.kind)
        val client500 = CoingeckoMarketClient(mockClient { respond("", HttpStatusCode.InternalServerError) })
        val e2 = assertFailsWith<MarketApiException> {
            client500.fetchCurrent(listOf("bitcoin"), listOf("USD"), apiKey = null)
        }
        assertIs<MarketRefreshError.Http>(e2.kind)
    }

    @Test
    fun `network failure maps to network error`() = runBlocking {
        val client = CoingeckoMarketClient(
            mockClient { throw java.io.IOException("connection refused") },
        )
        val e = assertFailsWith<MarketApiException> {
            client.fetchCurrent(listOf("bitcoin"), listOf("USD"), apiKey = null)
        }
        assertIs<MarketRefreshError.Network>(e.kind)
    }

    // ---- CG 历史 / 目录 / 排名 ----

    @Test
    fun `cg history parses price points sorted and 404 maps to untracked`() = runBlocking {
        val http = mockClient {
            jsonOf("""{"prices":[[1736870400000,60100],[1736874000000,60200],[1736866800000,60000]]}""")
        }
        val client = CoingeckoMarketClient(http)
        val candles = client.fetchHistory(
            "bitcoin", "USD",
            Instant.ofEpochMilli(1736866800000), Instant.ofEpochMilli(1736874000000),
            apiKey = null,
        )
        assertEquals(3, candles.size)
        assertTrue(candles[0].at.isBefore(candles[1].at))
        assertEquals(0, "60000".bd().compareTo(candles[0].price))

        val client404 = CoingeckoMarketClient(mockClient { respond("", HttpStatusCode.NotFound) })
        val e = assertFailsWith<MarketApiException> {
            client404.fetchHistory("dead-coin", "USD", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), null)
        }
        assertIs<MarketRefreshError.Untracked>(e.kind)
    }

    @Test
    fun `cg directory parses entries with platforms`() = runBlocking {
        val http = mockClient {
            jsonOf(
                """
                [{"id":"bitcoin","symbol":"btc","name":"Bitcoin","platforms":{}},
                 {"id":"ethereum","symbol":"eth","name":"Ethereum",
                  "platforms":{"ethereum":"0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2","bsc":""}}]
                """.trimIndent(),
            )
        }
        val entries = CoingeckoMarketClient(http).fetchDirectory(apiKey = null)
        assertEquals(2, entries.size)
        val eth = entries.first { it.cgId == "ethereum" }
        assertEquals("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2", eth.contracts["ethereum"])
        assertEquals(1, eth.contracts.size, "空合约地址剔除")
        assertTrue(decodedUrl().contains("include_platform=true"))
    }

    @Test
    fun `cg ranking parses market cap ranks and skips nulls`() = runBlocking {
        val http = mockClient {
            jsonOf(
                """[{"id":"bitcoin","market_cap_rank":1},{"id":"ethereum","market_cap_rank":2},
                    {"id":"no-rank-coin","market_cap_rank":null}]""".trimIndent(),
            )
        }
        val ranked = CoingeckoMarketClient(http).fetchMarketRanking(page = 1, perPage = 250, apiKey = null)
        assertEquals(2, ranked.size)
        assertEquals(1, ranked.first { it.cgId == "bitcoin" }.rank)
        assertTrue(decodedUrl().contains("order=market_cap_desc"))
    }

    // ---- CMC ----

    @Test
    fun `cmc current parses quotes by cmc id with key header`() = runBlocking {
        val http = mockClient {
            jsonOf(
                """{"data":{"825":{"quote":{"USD":{"price":1.001},"EUR":{"price":0.92}}},
                   "1":{"quote":{"USD":{"price":61234.56}}}}}""".trimIndent(),
            )
        }
        val client = CmcMarketClient(http)
        val quotes = client.fetchCmcCurrent(listOf(825L, 1L), listOf("USD", "EUR"), apiKey = "cmc-key")
        assertEquals(3, quotes.size)
        assertEquals("cmc-key", lastHeaders["X-CMC_PRO_API_KEY"])
        val usdtUsd = quotes.first { it.cmcId == 825L && it.fiat == "USD" }
        assertEquals(0, "1.001".bd().compareTo(usdtUsd.price))
        assertEquals(PriceSource.COINMARKETCAP, usdtUsd.source)
        assertTrue(decodedUrl().contains("/cryptocurrency/quotes/latest"))
    }

    @Test
    fun `cmc maps 402 to quota exceeded and 429 to rate limited`() = runBlocking {
        val client = CmcMarketClient(mockClient { respond("", HttpStatusCode.PaymentRequired) })
        val e = assertFailsWith<MarketApiException> {
            client.fetchCmcCurrent(listOf(1L), listOf("USD"), apiKey = "k")
        }
        assertIs<MarketRefreshError.QuotaExceeded>(e.kind)
        val client429 = CmcMarketClient(mockClient { respond("", HttpStatusCode.TooManyRequests) })
        val e2 = assertFailsWith<MarketApiException> {
            client429.fetchCmcCurrent(listOf(1L), listOf("USD"), apiKey = "k")
        }
        assertIs<MarketRefreshError.RateLimited>(e2.kind)
    }

    @Test
    fun `cmc map page parses and passes start limit params`() = runBlocking {
        val http = mockClient {
            jsonOf("""{"data":[{"id":1,"symbol":"BTC","name":"Bitcoin"},{"id":825,"symbol":"USDT","name":"Tether"}]}""")
        }
        val page = CmcMarketClient(http).fetchCmcMap(apiKey = "k", start = 1)
        assertEquals(2, page.size)
        assertEquals(825L, page[1].cmcId)
        assertTrue(decodedUrl().contains("start=1"))
        assertTrue(decodedUrl().contains("limit=5000"))
    }

    private fun String.bd(): BigDecimal = BigDecimal(this)
}
