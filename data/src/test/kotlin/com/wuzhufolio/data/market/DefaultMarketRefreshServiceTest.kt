package com.wuzhufolio.data.market

import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.domain.market.QuotaCallKind
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T5.1/T5.4 编排验收（真实库 + 假客户端）：主源成功落快照、主源失败兜底切换（429）、
 * 无兜底保持上次价格语义（error）、缺席币 untracked、额度计数（仅个人 Key 模式）、
 * 目录每日一次（cmc_id/市值榜/目录喂入）、单飞（并发一轮）。
 */
class DefaultMarketRefreshServiceTest {

    private lateinit var env: MarketTestEnv
    private lateinit var cg: FakeMarketClient
    private lateinit var cmc: FakeMarketClient
    private lateinit var quota: SettingsQuotaLedger
    private lateinit var rankCache: RefreshableRankProvider
    private lateinit var service: DefaultMarketRefreshService

    @BeforeTest
    fun setUp() {
        env = MarketTestEnv()
        cg = FakeMarketClient("cg")
        cmc = FakeMarketClient("cmc")
        quota = SettingsQuotaLedger(env.settings)
        rankCache = RefreshableRankProvider()
        cg.directoryResult = MarketTestEnv.DEFAULT_ENTRIES
        cg.rankResult = listOf(
            com.wuzhufolio.domain.market.MarketRank("bitcoin", 1),
            com.wuzhufolio.domain.market.MarketRank("ethereum", 2),
        )
        cmc.cmcMapResult = listOf(
            com.wuzhufolio.domain.market.CmcMapCoin(1, "BTC", "Bitcoin"),
            com.wuzhufolio.domain.market.CmcMapCoin(1027, "ETH", "Ethereum"),
        )
        service = DefaultMarketRefreshService(
            cgClient = cg,
            cmcClient = cmc,
            catalog = env.catalog,
            snapshots = env.snapshots,
            keyStore = env.deviceStore,
            settings = env.settings,
            quota = quota,
            rankCache = rankCache,
        )
    }

    @AfterTest
    fun tearDown() = env.close()

    private fun withKeys(cgKey: String = "cg-demo-key", cmcKey: String = "cmc-key") {
        env.deviceStore.put(MarketConfig.KEY_CG, MarketConfig.PURPOSE_CG, cgKey)
        env.deviceStore.put(MarketConfig.KEY_CMC, MarketConfig.PURPOSE_CMC, cmcKey)
    }

    // ---- 主源成功 ----

    @Test
    fun `keyless primary success writes snapshots and reports cg source`() = runBlocking {
        cg.currentResult = { coins, _, _ ->
            coins.map { quoteOf(it, price = if (it == "bitcoin") "61000" else "1") }
        }
        val result = service.refresh(manual = true, coins = listOf("bitcoin", "tether"), fiats = listOf("USD"))
        assertNull(result.error)
        assertEquals(PriceSource.COINGECKO, result.source)
        assertEquals(2, result.refreshedCoins)
        assertTrue(result.untracked.isEmpty())
        assertNull(result.quotaPercentUsed, "无 Key 模式无月度额度计数")
        val btcRow = assertNotNull(env.snapshots.latest(env.coinIdOf("bitcoin"), "USD"))
        assertEquals(0, "61000".bd().compareTo(btcRow.price))
        assertEquals(PriceSource.COINGECKO, btcRow.source)
        assertTrue(result.at != null)
        assertTrue(env.snapshots.count() >= 2)
    }

    // ---- 目录每日一次 ----

    @Test
    fun `directory refresh runs once per day and feeds catalog rank and cmc ids`() = runBlocking {
        withKeys(cmcKey = "cmc-key")
        cg.currentResult = { coins, _, _ -> coins.map { quoteOf(it, price = "1") } }
        service.refresh(manual = false, coins = listOf("bitcoin"), fiats = listOf("USD"))
        service.refresh(manual = false, coins = listOf("bitcoin"), fiats = listOf("USD"))
        assertEquals(1, cg.directoryCalls, "24h TTL 内只刷一次目录")
        assertEquals(1, cg.rankCalls)
        assertEquals(1, cmc.cmcMapCalls)
        assertEquals(1, rankCache.rankOf("bitcoin"))
        assertEquals(2, rankCache.size())
        val bitcoin = env.catalog.getByCgId("bitcoin")!!
        assertEquals("1", bitcoin.cmcId, "CMC map 对齐落库")
    }

    // ---- 兜底切换（429 → CMC） ----

    @Test
    fun `cg rate limited falls back to cmc and reports cmc source`() = runBlocking {
        withKeys(cmcKey = "cmc-key")
        cg.currentError = MarketRefreshError.RateLimited(PriceSource.COINGECKO, keylessHint = false)
        cmc.cmcCurrentResult = { ids, _, _ -> ids.map { cmcQuoteOf(it, price = "61050") } }
        val result = service.refresh(manual = true, coins = listOf("bitcoin", "tether"), fiats = listOf("USD"))
        assertNull(result.error, "兜底成功不算失败")
        assertEquals(PriceSource.COINMARKETCAP, result.source)
        assertEquals(2, result.refreshedCoins)
        val row = assertNotNull(env.snapshots.latest(env.coinIdOf("bitcoin"), "USD"))
        assertEquals(PriceSource.COINMARKETCAP, row.source)
        assertNotNull(result.quotaPercentUsed)
        assertTrue(quota.counts()[QuotaCallKind.CURRENT] ?: 0 >= 2, "CG 失败请求也计数 + CMC 请求计数")
    }

    // ---- 无兜底：保持上次价格语义 ----

    @Test
    fun `cg failure without cmc key reports error and keeps last-price semantics`() = runBlocking {
        cg.currentError = MarketRefreshError.RateLimited(PriceSource.COINGECKO, keylessHint = true)
        val result = service.refresh(manual = true, coins = listOf("bitcoin"), fiats = listOf("USD"))
        val error = assertIs<MarketRefreshError.RateLimited>(result.error)
        assertTrue(error.keylessHint, "无 Key 共享限流 → 提示注册个人 Key")
        assertNull(result.at, "无成功刷新 → 无时间戳（保留上次价格展示）")
        assertEquals(listOf("bitcoin"), result.untracked)
        assertEquals(0, result.refreshedCoins)
        assertEquals(0, env.snapshots.count())
    }

    // ---- 缺席币（主源返回子集） ----

    @Test
    fun `absent coins go untracked when no fallback covers them`() = runBlocking {
        withKeys(cmcKey = "cmc-key")
        cg.currentResult = { coins, _, _ ->
            coins.filter { it == "bitcoin" }.map { quoteOf(it, price = "61000") }
        }
        // CMC 只回 bitcoin（1）→ tether 仍未覆盖
        cmc.cmcCurrentResult = { ids, _, _ -> ids.filter { it == 1L }.map { cmcQuoteOf(it, price = "61050") } }
        val result = service.refresh(manual = true, coins = listOf("bitcoin", "tether"), fiats = listOf("USD"))
        assertNull(result.error)
        assertEquals(PriceSource.COINGECKO, result.source)
        assertEquals(listOf("tether"), result.untracked, "两源均无价 → 无行情")
        assertEquals(1, result.refreshedCoins)
    }

    // ---- 单飞（Mutex：并发刷新串行、不重叠） ----

    @Test
    fun `concurrent refresh rounds never overlap`() = runBlocking {
        var gateOpen = true
        var running = 0
        var overlapped = false
        cg.currentResult = { coins, _, _ ->
            running++
            try {
                while (gateOpen) kotlinx.coroutines.delay(20)
                if (running > 1) overlapped = true
                coins.map { quoteOf(it, price = "1") }
            } finally {
                running--
            }
        }
        val first = async { service.refresh(manual = false, coins = listOf("bitcoin"), fiats = listOf("USD")) }
        kotlinx.coroutines.delay(100) // 第一轮已持锁
        val second = async { service.refresh(manual = true, coins = listOf("bitcoin"), fiats = listOf("USD")) }
        kotlinx.coroutines.delay(100)
        gateOpen = false
        first.await()
        second.await()
        assertEquals(2, cg.currentCalls, "排队轮仍会执行（Mutex 只串行不丢弃）")
        assertFalse(overlapped, "单飞：任意时刻至多一轮在执行")
    }

    // ---- 币不在目录（无行情目录缺位） ----

    @Test
    fun `coins missing from catalog are reported untracked without io`() = runBlocking {
        cg.currentResult = { _, _, _ -> error("should not be called") }
        val result = service.refresh(manual = true, coins = listOf("never-listed-coin"), fiats = listOf("USD"))
        assertEquals(listOf("never-listed-coin"), result.untracked)
        assertEquals(0, cg.currentCalls)
        assertNull(result.error)
    }

    // ---- 目录失败 + 币集不可解析：上浮真实原因（GUI 验收 5/6 反馈修复，避免误报「暂无行情」） ----

    @Test
    fun `directory failure with unresolvable coins surfaces the cause`() = runBlocking {
        MarketTestEnv(seed = false).use { env ->
            val cgFake = FakeMarketClient("cg")
            cgFake.directoryError = MarketRefreshError.RateLimited(PriceSource.COINGECKO, keylessHint = true)
            val service = DefaultMarketRefreshService(
                cgClient = cgFake,
                cmcClient = FakeMarketClient("cmc"),
                catalog = env.catalog,
                snapshots = env.snapshots,
                keyStore = env.deviceStore,
                settings = env.settings,
                quota = SettingsQuotaLedger(env.settings),
                rankCache = RefreshableRankProvider(),
            )
            val result = service.refresh(manual = true, coins = listOf("tether"), fiats = listOf("USD"))
            val error = assertIs<MarketRefreshError.RateLimited>(result.error)
            assertTrue(error.keylessHint, "上浮目录失败原因为限流提示（含注册个人 Key 建议）")
            assertEquals(listOf("tether"), result.untracked)
            assertEquals(0, result.refreshedCoins)
            assertNull(result.at)
            assertTrue(cgFake.directoryCalls == 1)
        }
    }

    @Test
    fun `generic directory failure is classified internal and not network`() = runBlocking {
        MarketTestEnv(seed = false).use { env ->
            val cgFake = FakeMarketClient("cg")
            cgFake.directoryThrowable = IllegalStateException("column too long (boom)")
            val service = DefaultMarketRefreshService(
                cgClient = cgFake,
                cmcClient = FakeMarketClient("cmc"),
                catalog = env.catalog,
                snapshots = env.snapshots,
                keyStore = env.deviceStore,
                settings = env.settings,
                quota = SettingsQuotaLedger(env.settings),
                rankCache = RefreshableRankProvider(),
            )
            val result = service.refresh(manual = true, coins = listOf("tether"), fiats = listOf("USD"))
            val error = assertIs<MarketRefreshError.Internal>(result.error)
            assertTrue(error.detail.contains("column too long"), "修复轮：内部异常不得误报网络不可达")
        }
    }
}
