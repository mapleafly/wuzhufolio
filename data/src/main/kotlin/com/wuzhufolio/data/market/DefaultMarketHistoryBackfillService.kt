package com.wuzhufolio.data.market

import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.market.BackfillReport
import com.wuzhufolio.domain.market.MarketApiException
import com.wuzhufolio.domain.market.MarketCandle
import com.wuzhufolio.domain.market.MarketHistoryBackfillService
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.PriceResolution
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.domain.market.QuotaCallKind
import com.wuzhufolio.domain.market.RateBackoff
import kotlinx.coroutines.delay
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 历史价回填（T5.3 · MarketHistoryBackfillService 实现）：
 * 区间一次 /market_chart/range 请求（≤90 天 = 小时级、更早 = 日级——CG 服务端口径），
 * 落库前按已存在桶去重（upsert 语义天然幂等，重复回填只产生 updated 不膨胀）；
 * 429 时按 [RateBackoff] 指数退避单次重试（上限 60s——按需批量任务内重试一次即返回，超限交调度）。
 */
class DefaultMarketHistoryBackfillService(
    private val cgClient: com.wuzhufolio.domain.market.MarketDataClient,
    private val catalog: CoinCatalog,
    private val snapshots: PriceSnapshotRepository,
    private val keyStore: DeviceSecretStore,
    private val quota: SettingsQuotaLedger,
    private val logger: Logger = LoggerFactory.getLogger(DefaultMarketHistoryBackfillService::class.java),
) : MarketHistoryBackfillService {

    @Suppress("ReturnCount") // 早期退出路径（目录缺币/无数据）为流程语义，每路独立返回更可读
    override suspend fun backfillRange(
        coin: String,
        fiat: String,
        from: Instant,
        to: Instant,
    ): BackfillReport {
        require(!to.isBefore(from)) { "backfill range must be ordered" }
        val catalogCoin = catalog.getByCgId(coin)
        if (catalogCoin == null) {
            logger.warn("backfill skipped: coin not in catalog: {}", coin)
            return BackfillReport(coin, fiat, from, to, requestedBuckets = 0, newRows = 0)
        }
        val coinId = catalogCoin.id.toInt()
        val now = Instant.now()
        val spanIsDaily = !PriceResolution.isWithinHourlyHorizon(from, now)
        val requested = expectedBuckets(from, to, daily = spanIsDaily)

        val cgKey = if (keyStore.configured(MarketConfig.KEY_CG)) {
            keyStore.get(MarketConfig.KEY_CG, MarketConfig.PURPOSE_CG)
        } else {
            null
        }
        val candles = fetchWithOneRetry(coin, fiat, from, to, cgKey)
        if (candles == null) {
            return BackfillReport(coin, fiat, from, to, requestedBuckets = requested, newRows = 0)
        }

        val summary = snapshots.upsertBatch(
            candles.map { SnapshotWrite(coinId, fiat, it.price, PriceSource.COINGECKO, it.at) },
        )
        val existing = snapshots.range(coinId, fiat, from, to).size
        logger.info(
            "backfill coin={} fiat={} from={} to={} candles={} inserted={} updated={} rowsInRange={}",
            coin, fiat, from, to, candles.size, summary.inserted, summary.updated, existing,
        )
        return BackfillReport(
            coin = coin,
            fiat = fiat,
            from = from,
            to = to,
            requestedBuckets = requested,
            newRows = summary.inserted,
        )
    }

    /** 429 指数退避单次重试；其余错误直接透传（调用方按错误处理）。 */
    @Suppress("ReturnCount") // try/retry 各分支返回：首取成功/非限流失败/重试结果
    private suspend fun fetchWithOneRetry(
        coin: String,
        fiat: String,
        from: Instant,
        to: Instant,
        cgKey: String?,
    ): List<MarketCandle>? {
        try {
            if (cgKey != null) quota.record(QuotaCallKind.HISTORY) // 每次请求发起即计数（含重试）
            return cgClient.fetchHistory(coin, fiat, from, to, cgKey)
        } catch (e: MarketApiException) {
            val kind = e.kind
            if (kind !is MarketRefreshError.RateLimited) {
                logger.warn("backfill failed coin={}: {}", coin, kind)
                return null
            }
            val waitMillis = RateBackoff.delaySeconds(1) * 1000
            logger.warn("backfill rate limited (429); retry once after {}ms", waitMillis)
            delay(waitMillis)
            return try {
                if (cgKey != null) quota.record(QuotaCallKind.HISTORY) // 重试请求同样计数
                cgClient.fetchHistory(coin, fiat, from, to, cgKey)
            } catch (e2: MarketApiException) {
                logger.warn("backfill retry failed coin={}: {}", coin, e2.kind)
                null
            }
        }
    }

    private fun expectedBuckets(from: Instant, to: Instant, daily: Boolean): Int {
        if (daily) {
            val startDay = from.truncatedTo(ChronoUnit.DAYS)
            val endDay = to.truncatedTo(ChronoUnit.DAYS)
            return ChronoUnit.DAYS.between(startDay, endDay).toInt().coerceAtLeast(0)
        }
        val startHour = PriceResolution.hourBucketOf(from)
        val endHour = PriceResolution.hourBucketOf(to)
        return (ChronoUnit.HOURS.between(startHour, endHour).toInt()).coerceAtLeast(0)
    }
}
