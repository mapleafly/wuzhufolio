package com.wuzhufolio.data.smoke

import com.wuzhufolio.data.catalog.SqlCoinCatalog
import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.exchange.BinanceAdapter
import com.wuzhufolio.data.exchange.newOkHttpExchangeClient
import com.wuzhufolio.data.market.CoingeckoMarketClient
import com.wuzhufolio.data.market.PriceSnapshotRepository
import com.wuzhufolio.data.market.SnapshotWrite
import com.wuzhufolio.data.market.newOkHttpMarketClient
import com.wuzhufolio.data.randomDbKey
import com.wuzhufolio.domain.exchange.ExchangeCredentials
import com.wuzhufolio.domain.market.PriceSource
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * **P5 真实网络冒烟（M5/M6 遗留「真实网络端到端冒烟」）**——默认为**跳过**，只在显式开启时联网：
 *
 * ```bash
 * WZF_LIVE_SMOKE=1 ./gradlew :data:test --tests "com.wuzhufolio.data.smoke.LiveNetworkSmokeTest" --rerun-tasks
 * ```
 *
 * 为什么门控：CI 与日常回归必须**离线可重复**（外部 API 波动/限流不得染红构建）。本用例的作用是
 * 在真实端点可用时，用**生产客户端**（真 OkHttp + 真 Ktor + 真 URL/请求头）验证契约未漂移：
 * ① CoinGecko `/simple/price` + `/coins/market` 榜（主源契约、市值消歧规则③输入）；
 * ② 真实价格写入 **SQLCipher 库**的快照表（证明真实数据能走完落库路径）；
 * ③ Binance 公开端点 `/api/v3/exchangeInfo`（无 Key，验证交易所适配器真实链路与 pair 注册表解析）。
 *
 * 运行期**不使用任何 API Key**（CG 匿名公共档、Binance 公开端点）——不消耗用户额度，也不落任何凭据。
 */
internal class LiveNetworkSmokeTest {

    @Test
    fun `CoinGecko 真实端点 报价 与 目录落库`() {
        assumeTrue(System.getenv("WZF_LIVE_SMOKE") == "1", "live smoke 默认跳过（设 WZF_LIVE_SMOKE=1 开启）")
        val dir = Files.createTempDirectory("wuzhufolio-live")
        val db = WzDatabase(dir.resolve("live.db"), randomDbKey())
        try {
            db.migrateToLatest()
            val gate = DbGate(db)
            val catalog = SqlCoinCatalog(gate)
            val snapshots = PriceSnapshotRepository(gate)
            val client = CoingeckoMarketClient(newOkHttpMarketClient())

            // ① 当前价（CG /simple/price）
            val quotes = runBlocking { client.fetchCurrent(listOf("bitcoin", "tether"), listOf("usd"), null) }
            println("[live] CG quotes = " + quotes.map { it.coin + "=" + it.price + " " + it.fiat })
            assertTrue(quotes.any { it.coin == "bitcoin" && it.fiat.equals("usd", ignoreCase = true) }, "应取到 BTC/USD 报价")
            assertTrue(
                quotes.filter { it.coin == "tether" }
                    .all { it.price.compareTo(java.math.BigDecimal("0.90")) > 0 },
                "USDT 价格应接近 1（>0.90）",
            )

            // ② 币目录落库（CG /coins/list 子集 → coins 表）——真实数据走完真实存储路径
            val directory = runBlocking { client.fetchDirectory(null) }
            println("[live] CG directory size = " + directory.size)
            assertTrue(directory.size > 1000, "CG 目录应为全量（>1000 条）")
            val summary = runBlocking {
                catalog.refreshDirectory(
                    directory.filter { it.cgId in setOf("bitcoin", "ethereum", "tether", "usd-coin") },
                )
            }
            assertEqualsInt(4, summary.added, "四个种子币应落入 coins 表")

            // ③ 真实报价 → 快照表（M006，真实库）
            val btc = runBlocking { catalog.getByCgId("bitcoin") }!!
            val quote = quotes.first { it.coin == "bitcoin" }
            runBlocking {
                snapshots.upsert(
                    SnapshotWrite(
                        coinId = btc.id.toInt(),
                        fiat = quote.fiat.uppercase(),
                        price = quote.price,
                        source = PriceSource.COINGECKO,
                        at = java.time.Instant.now(),
                    ),
                )
            }
            val stored = runBlocking { snapshots.latest(btc.id.toInt(), "USD") }
            println("[live] stored snapshot = " + stored?.price + " @ " + stored?.recordedAt)
            assertTrue(stored != null, "真实报价应写入快照表并可读回")
        } finally {
            db.close()
        }
    }

    @Test
    fun `Binance 公开端点 交易对注册表（无密钥）`() {
        assumeTrue(System.getenv("WZF_LIVE_SMOKE") == "1", "live smoke 默认跳过（设 WZF_LIVE_SMOKE=1 开启）")
        val client = newOkHttpExchangeClient()
        try {
            // fetchPairs = /api/v3/exchangeInfo（公开端点，无需凭据）——验证真实链路与响应形状未漂移
            val adapter = BinanceAdapter(client, ExchangeCredentials(apiKey = "", secretKey = ""))
            val pairs = runBlocking { adapter.fetchPairs() }
            println("[live] binance pairs = " + pairs.size)
            assertTrue(pairs.size > 1000, "Binance 现货交易对应为千级")
            val btcUsdt = pairs.firstOrNull { it.symbol == "BTCUSDT" }
            assertTrue(btcUsdt != null, "应含 BTCUSDT")
            assertTrue(btcUsdt.baseAsset == "BTC" && btcUsdt.quoteAsset == "USDT", "注册表应给出 base/quote 资产")
        } finally {
            client.close()
        }
    }

    private fun assertEqualsInt(expected: Int, actual: Int, message: String) {
        assertTrue(expected == actual, "$message（expected=$expected, actual=$actual）")
    }
}
