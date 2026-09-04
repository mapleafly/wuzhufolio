package com.wuzhufolio.data.market

import com.wuzhufolio.domain.market.BackfillReport
import com.wuzhufolio.domain.market.PriceSource
import java.time.Instant
import com.wuzhufolio.domain.market.MarketApiException
import com.wuzhufolio.domain.market.MarketCandle
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.QuotaCallKind
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T5.3 历史回填验收：区间拉取落快照、空洞消除（此前不可解析时刻回填后可解析）、
 * 已有桶不膨胀（幂等）、429 退避重试一次。
 */
class DefaultMarketHistoryBackfillServiceTest {

    private lateinit var env: MarketTestEnv
    private lateinit var cg: FakeMarketClient
    private lateinit var quota: SettingsQuotaLedger
    private lateinit var service: DefaultMarketHistoryBackfillService

    @BeforeTest
    fun setUp() {
        env = MarketTestEnv()
        cg = FakeMarketClient("cg")
        quota = SettingsQuotaLedger(env.settings)
        service = DefaultMarketHistoryBackfillService(
            cgClient = cg,
            catalog = env.catalog,
            snapshots = env.snapshots,
            keyStore = env.deviceStore,
            quota = quota,
        )
    }

    @AfterTest
    fun tearDown() = env.close()

    // 用例时刻取「近 90 天小时级保留区」内（快照降采样边界之外），保证小时桶语义
    private val from: Instant = Instant.now()
        .minus(3, java.time.temporal.ChronoUnit.DAYS)
        .truncatedTo(java.time.temporal.ChronoUnit.DAYS)
        .plus(8, java.time.temporal.ChronoUnit.HOURS)
    private val to: Instant = from.plus(3, java.time.temporal.ChronoUnit.HOURS)

    @Test
    fun `backfill fills missing hourly buckets and resolves previously empty moments`() = runBlocking {
        // 预置 09 点桶已存在 → 回填应只新增 08/10 两个桶（幂等不膨胀）
        env.snapshots.upsert(
            SnapshotWrite(
                env.coinIdOf("bitcoin"), "USD", "60000".bd(), PriceSource.COINGECKO,
                from.plusSeconds(3600).plusSeconds(1800), // 09 点桶内（09:30）
            ),
        )
        assertNull(env.snapshots.atBucket(env.coinIdOf("bitcoin"), "USD", from), "08 点桶为空（空洞）")
        cg.historyResult = {
            listOf(
                MarketCandle(from, "59000".bd()),
                MarketCandle(from.plusSeconds(3600), "60000".bd()),
                MarketCandle(from.plusSeconds(7200), "60500".bd()),
            )
        }
        val report: BackfillReport = service.backfillRange("bitcoin", "USD", from, to)
        assertEquals(3, report.requestedBuckets)
        assertEquals(2, report.newRows, "09 点桶已存在不新增")
        val filled = assertNotNull(env.snapshots.atBucket(env.coinIdOf("bitcoin"), "USD", from))
        assertEquals(0, "59000".bd().compareTo(filled.price), "空洞消除：08 点可解析")
        // 重复回填幂等：不再新增
        val again = service.backfillRange("bitcoin", "USD", from, to)
        assertEquals(0, again.newRows)
    }

    @Test
    fun `backfill on coin absent from catalog is a no-op report`() = runBlocking {
        val report = service.backfillRange("ghost-coin", "USD", from, to)
        assertEquals(0, report.requestedBuckets)
        assertEquals(0, report.newRows)
        assertEquals(0, cg.historyCalls)
    }

    @Test
    fun `backfill retries once on rate limit and records history quota`() = runBlocking {
        env.deviceStore.put(MarketConfig.KEY_CG, MarketConfig.PURPOSE_CG, "cg-key")
        var calls = 0
        cg.historyResult = {
            calls++
            if (calls == 1) throw MarketApiException(MarketRefreshError.RateLimited(PriceSource.COINGECKO, false))
            listOf(MarketCandle(from, "59000".bd()))
        }
        val report = service.backfillRange("bitcoin", "USD", from, to)
        assertEquals(2, cg.historyCalls, "429 退避后重试一次")
        assertEquals(1, report.newRows)
        assertTrue((quota.counts()[QuotaCallKind.HISTORY] ?: 0) >= 2, "两次请求均计数")
    }
}
