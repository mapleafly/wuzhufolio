package com.wuzhufolio.data.market

import com.wuzhufolio.domain.market.PriceSource
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T5.2 快照仓库验收（同小时去重 / 末条语义 / 桶查询 / 90 天降采样保日线 / 区间与最近可得）。
 */
class PriceSnapshotRepositoryTest {

    private val t0: Instant = Instant.parse("2026-01-15T10:00:00Z")

    @Test
    fun `same hour upsert keeps single row with latest price`() = runBlocking {
        MarketTestEnv().use { e ->
            val coin = e.coinIdOf("bitcoin")
            assertTrue(e.snapshots.upsert(SnapshotWrite(coin, "USD", "50000".bd(), PriceSource.COINGECKO, t0)))
            assertFalse(
                e.snapshots.upsert(
                    SnapshotWrite(coin, "USD", "50100".bd(), PriceSource.COINGECKO, t0.plusSeconds(1800)),
                ),
                "同小时应更新而非插入",
            )
            assertEquals(1, e.snapshots.count())
            val latest = assertNotNull(e.snapshots.latest(coin, "USD"))
            assertEquals(0, "50100".bd().compareTo(latest.price), "同小时取末条")
            val bucket = assertNotNull(e.snapshots.atBucket(coin, "USD",
            t0.truncatedTo(java.time.temporal.ChronoUnit.HOURS)))
            assertEquals(0, "50100".bd().compareTo(bucket.price))
        }
    }

    @Test
    fun `different hour buckets insert separate rows`() = runBlocking {
        MarketTestEnv().use { e ->
            val coin = e.coinIdOf("bitcoin")
            e.snapshots.upsert(SnapshotWrite(coin, "USD", "50000".bd(), PriceSource.COINGECKO, t0))
            e.snapshots.upsert(SnapshotWrite(coin, "USD", "50100".bd(), PriceSource.COINGECKO,
            t0.plusSeconds(3600)))
            e.snapshots.upsert(SnapshotWrite(coin, "EUR", "45000".bd(), PriceSource.COINGECKO, t0))
            assertEquals(3, e.snapshots.count())
            assertNull(e.snapshots.atBucket(coin, "USD", t0.plusSeconds(7200)))
        }
    }

    @Test
    fun `batch upsert last write of the same bucket wins`() = runBlocking {
        MarketTestEnv().use { e ->
            val coin = e.coinIdOf("bitcoin")
            val summary = e.snapshots.upsertBatch(
                listOf(
                    SnapshotWrite(coin, "USD", "50000".bd(), PriceSource.COINGECKO, t0),
                    SnapshotWrite(coin, "USD", "50500".bd(), PriceSource.COINGECKO, t0.plusSeconds(60)),
                    SnapshotWrite(coin, "USD", "50900".bd(), PriceSource.COINGECKO, t0.plusSeconds(120)),
                    SnapshotWrite(coin, "EUR", "45500".bd(), PriceSource.COINGECKO, t0),
                ),
            )
            assertEquals(2, summary.inserted)
            assertEquals(2, summary.updated)
            assertEquals(2, e.snapshots.count())
            val latest = assertNotNull(e.snapshots.latest(coin, "USD"))
            assertEquals(0, "50900".bd().compareTo(latest.price))
        }
    }

    @Test
    fun `bucket boundary respects utc hour even with millisecond text ordering trap`() = runBlocking {
        MarketTestEnv().use { e ->
            val coin = e.coinIdOf("bitcoin")
            // 前一桶 10:59:59.999（有毫秒）与 11:00:00 整点（无毫秒）——文本比较陷阱（.5 < 整点）
            e.snapshots.upsert(SnapshotWrite(coin, "USD", "100".bd(), PriceSource.COINGECKO, t0.minusMillis(1)))
            e.snapshots.upsert(SnapshotWrite(coin, "USD", "101".bd(), PriceSource.COINGECKO, t0))
            assertEquals(2, e.snapshots.count())
            // 11:00 行不应落入 [10:00,11:00) 桶
            val bucket10 = assertNotNull(e.snapshots.atBucket(coin, "USD", t0.minusSeconds(3600)))
            assertEquals(0, "100".bd().compareTo(bucket10.price))
            val bucket11 = assertNotNull(e.snapshots.atBucket(coin, "USD", t0))
            assertEquals(0, "101".bd().compareTo(bucket11.price))
        }
    }

    @Test
    fun `nearest before returns the closest earlier row`() = runBlocking {
        MarketTestEnv().use { e ->
            val coin = e.coinIdOf("bitcoin")
            e.snapshots.upsert(SnapshotWrite(coin, "USD", "50000".bd(), PriceSource.COINGECKO, t0))
            e.snapshots.upsert(SnapshotWrite(coin, "USD", "50200".bd(), PriceSource.COINGECKO,
            t0.plusSeconds(3600)))
            val near = assertNotNull(e.snapshots.nearestBefore(coin, "USD", t0.plusSeconds(5400)))
            assertEquals(0, "50200".bd().compareTo(near.price))
            assertNull(e.snapshots.nearestBefore(coin, "USD", t0.minusSeconds(1)))
        }
    }

    @Test
    fun `range returns ordered rows inside half-open interval`() = runBlocking {
        MarketTestEnv().use { e ->
            val coin = e.coinIdOf("bitcoin")
            e.snapshots.upsert(SnapshotWrite(coin, "USD", "1".bd(), PriceSource.COINGECKO, t0))
            e.snapshots.upsert(SnapshotWrite(coin, "USD", "2".bd(), PriceSource.COINGECKO, t0.plusSeconds(3600)))
            e.snapshots.upsert(SnapshotWrite(coin, "USD", "3".bd(), PriceSource.COINGECKO, t0.plusSeconds(7200)))
            val rows = e.snapshots.range(coin, "USD", t0.plusSeconds(1), t0.plusSeconds(7201))
            assertEquals(2, rows.size)
            assertTrue(rows[0].recordedAt.isBefore(rows[1].recordedAt))
        }
    }

    @Test
    fun `compaction drops old non-midnight rows and keeps midnight daily rows`() = runBlocking {
        MarketTestEnv().use { e ->
            val coin = e.coinIdOf("bitcoin")
            val now = Instant.parse("2026-05-01T00:00:00Z")
            // 老数据（>90 天）：一个整点行 + 一个非整点行
            e.snapshots.upsert(SnapshotWrite(coin, "USD", "100".bd(), PriceSource.COINGECKO,
            Instant.parse("2026-01-01T00:00:00Z")))
            e.snapshots.upsert(SnapshotWrite(coin, "USD", "110".bd(), PriceSource.COINGECKO,
            Instant.parse("2026-01-01T12:00:00Z")))
            // 新数据（90 天内）
            e.snapshots.upsert(SnapshotWrite(coin, "USD", "900".bd(), PriceSource.COINGECKO,
            Instant.parse("2026-04-01T12:00:00Z")))
            val removed = e.snapshots.compactPreservingDaily(now)
            assertEquals(1, removed, "仅删非整点老行")
            assertEquals(2, e.snapshots.count())
            val daily = assertNotNull(e.snapshots.atBucket(coin, "USD", Instant.parse("2026-01-01T00:00:00Z")))
            assertEquals(0, "100".bd().compareTo(daily.price), "日线行保留可读")
        }
    }

    @Test
    fun `compaction is idempotent`() = runBlocking {
        MarketTestEnv().use { e ->
            val coin = e.coinIdOf("bitcoin")
            e.snapshots.upsert(SnapshotWrite(coin, "USD", "110".bd(), PriceSource.COINGECKO,
            Instant.parse("2026-01-01T12:00:00Z")))
            assertEquals(1, e.snapshots.compactPreservingDaily(Instant.parse("2026-05-01T00:00:00Z")))
            assertEquals(0, e.snapshots.compactPreservingDaily(Instant.parse("2026-05-01T00:00:00Z")))
        }
    }

    private fun String.bd(): BigDecimal = BigDecimal(this)
}
