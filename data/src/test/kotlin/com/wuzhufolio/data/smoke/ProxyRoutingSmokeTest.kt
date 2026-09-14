package com.wuzhufolio.data.smoke

import com.wuzhufolio.data.exchange.BinanceAdapter
import com.wuzhufolio.data.exchange.newOkHttpExchangeClient
import com.wuzhufolio.data.market.CmcMarketClient
import com.wuzhufolio.data.market.CoingeckoMarketClient
import com.wuzhufolio.data.market.newOkHttpMarketClient
import com.wuzhufolio.domain.exchange.ExchangeCredentials
import com.wuzhufolio.domain.market.MarketApiException
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.proxy.ProxyEnvironment
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * **P6 出站面实证（security-checklist §8 复核命令 5）**——默认为**跳过**，只在「配置了代理 + 显式开启」
 * 时运行；配合 `scripts/outbound-capture-proxy.py` 使用：
 *
 * ```bash
 * python3 scripts/outbound-capture-proxy.py --port 8899 --log /tmp/wzf-outbound.log &
 * WZF_LIVE_SMOKE=1 https_proxy=http://127.0.0.1:8899 ./gradlew --no-daemon \
 *   :data:test --tests "com.wuzhufolio.data.smoke.ProxyRoutingSmokeTest" --rerun-tasks
 * ```
 *
 * 本用例回答两个问题（其余靠静态守护 `SecurityGuardTest` + 运行期抓包）：
 * ① **两类 API 的客户端都真的走代理**（PRD 故事 4.2-2「所有对外网络请求都必须通过检测到的代理」）——
 *   行情客户端与交易所客户端各自注入同一个开关感知 ProxySelector 后，真实请求必须成功抵达真实端点；
 * ② 抓包代理日志中出现的**主机集合** ⊆ 出站白名单（api.coingecko.com / pro-api.coinmarketcap.com /
 *   api.binance.com），由执行者核对日志（本用例只负责把流量真的推过代理）。
 *
 * 不使用任何 API Key（CG 匿名档 + Binance 公开端点），不消耗用户额度、不落凭据。
 */
internal class ProxyRoutingSmokeTest {

    private fun selectorFor(host: String, port: Int): ProxySelector = object : ProxySelector() {
        override fun select(uri: URI): List<Proxy> =
            listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))

        override fun connectFailed(uri: URI, sa: java.net.SocketAddress, ioe: java.io.IOException) = Unit
    }

    @Test
    fun `both api classes route real requests through the configured proxy`() {
        assumeTrue(System.getenv("WZF_LIVE_SMOKE") == "1", "live smoke 默认跳过（设 WZF_LIVE_SMOKE=1 开启）")
        val endpoint = ProxyEnvironment.parse(System.getenv())
        assumeTrue(endpoint != null, "未配置 http(s)_proxy —— 抓包实证需要先把流量指向 capture proxy")

        val selector = selectorFor(endpoint!!.host, endpoint.port)

        // ① 行情链路（CoinGecko 匿名档）
        val market = CoingeckoMarketClient(newOkHttpMarketClient(selector))
        val quotes = runBlocking { market.fetchCurrent(listOf("bitcoin"), listOf("usd"), null) }
        println("[proxy-smoke] CG quotes via proxy = " + quotes.map { it.coin + "=" + it.price })
        assertTrue(quotes.any { it.coin == "bitcoin" }, "行情请求应经代理成功返回")

        // ② 交易所链路（Binance 公开端点；凭证不使用）
        val exchange = BinanceAdapter(
            newOkHttpExchangeClient(selector),
            ExchangeCredentials("proxy-smoke-key", "proxy-smoke-secret"),
        )
        val pairs = runBlocking { exchange.fetchPairs() }
        println("[proxy-smoke] Binance pairs via proxy = " + pairs.size)
        assertTrue(pairs.size > 100, "交易所公开端点应经代理成功返回（现货 pair 全量）")

        // ③ 兜底行情源（CoinMarketCap）：用**无效占位 Key** 触发一次真实出站（预期 401 → InvalidKey），
        //    目的是让抓包日志出现第三个白名单主机（pro-api.coinmarketcap.com）而不是「未被访问即无证据」。
        //    不消耗任何真实额度、不落任何凭据。
        val cmc = CmcMarketClient(newOkHttpMarketClient(selector))
        val failure = assertFailsWith<MarketApiException> {
            runBlocking { cmc.fetchCmcCurrent(listOf(1L), listOf("USD"), "proxy-smoke-invalid-key") }
        }
        println("[proxy-smoke] CMC probe error = " + failure.kind)
        assertTrue(
            failure.kind is MarketRefreshError.InvalidKey || failure.kind is MarketRefreshError.Http,
            "CMC 占位 Key 应被端点拒绝（401/其它 HTTP 错误），而不是网络不可达：" + failure.kind,
        )
    }
}
