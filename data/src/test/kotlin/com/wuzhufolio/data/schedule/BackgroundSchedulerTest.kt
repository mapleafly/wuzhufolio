package com.wuzhufolio.data.schedule

import com.wuzhufolio.domain.exchange.ApiKeySyncResult
import com.wuzhufolio.domain.exchange.SyncStatus
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.MarketRefreshResult
import com.wuzhufolio.domain.market.PriceSource
import java.time.Instant
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 后台调度宿主（M11）：间隔口径 / 额度降档 / 429 频发与退避窗口 / 事件出口 / 空密钥不发事件。
 *
 * 只测单次执行与间隔计算，不启动真实循环——循环体本身只有 delay + 调用，逻辑全在这两处，
 * 且真实 delay 会让测试不可控。[SchedulerSources] 是窄接口，一个假实现即可覆盖全部路径。
 */
class BackgroundSchedulerTest {

    /** 可编排的假源。 */
    private class FakeSources : SchedulerSources {
        var frequency = 5
        var syncInterval = 30
        var quota: Int? = null
        var cgConfigured = true
        var marketResult: MarketRefreshResult = okResult()
        var syncResults: List<ApiKeySyncResult> = emptyList()
        var rotateSummary = "files=0"
        var compacted = 0
        var reminderDays: Long? = null
        var marketCalls = 0
        var lastManual: Boolean? = null

        override suspend fun marketFrequencyMinutes(): Int = frequency

        override suspend fun syncIntervalMinutes(): Int = syncInterval

        override suspend fun quotaPercentUsed(): Int? = quota

        override suspend fun cgConfigured(): Boolean = cgConfigured

        override suspend fun refreshMarket(manual: Boolean): MarketRefreshResult {
            marketCalls++
            lastManual = manual
            return marketResult
        }

        override suspend fun syncNow(): List<ApiKeySyncResult> = syncResults

        override suspend fun rotateLogs(): String = rotateSummary

        override suspend fun compactSnapshots(): Int = compacted

        override suspend fun backupReminderDays(): Long? = reminderDays
    }

    private fun scheduler(
        sources: FakeSources,
        now: () -> Instant = { Instant.parse("2026-09-10T00:00:00Z") },
    ) = BackgroundScheduler(
        sources = sources,
        now = now,
        random = { 0.5 }, // 抖动中位 = 原值，断言可精确
    )

    /**
     * 订阅事件流执行 [block]，返回期间收到的事件。
     *
     * 两处时序防护：① 先用 `subscriptionCount` 等到真正订阅（SharedFlow 无重放，订阅前的事件不补发）；
     * ② 收尾留一段静默窗口再取消订阅（事件派发是异步的，立即断言会假红）。
     */
    private fun collectEvents(s: BackgroundScheduler, block: suspend () -> Unit): List<SchedulerEvent> =
        runBlocking {
            val seen = Collections.synchronizedList(mutableListOf<SchedulerEvent>())
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scope.launch { s.events.collect { seen += it } }
            try {
                withTimeout(EVENT_WAIT_MILLIS) { s.eventSubscribers.first { it > 0 } }
                block()
                delay(EVENT_SETTLE_MILLIS) // 事件派发是异步的：留静默窗口再取消订阅
            } finally {
                scope.cancel()
            }
            seen.toList()
        }

    @Test
    fun `market delay follows refresh frequency when window visible`() = runBlocking {
        val sources = FakeSources().apply { frequency = 5; syncInterval = 30 }
        val s = scheduler(sources)
        s.setWindowVisible(true)
        assertEquals(5 * 60_000L, s.marketDelayMillis())
    }

    @Test
    fun `market delay degrades to sync interval when tray resident`() = runBlocking {
        val sources = FakeSources().apply { frequency = 5; syncInterval = 30 }
        val s = scheduler(sources)
        s.setWindowVisible(false) // 托盘驻留（PRD 9.2：统一按 API 同步间隔降频）
        assertEquals(30 * 60_000L, s.marketDelayMillis())
    }

    @Test
    fun `quota at 80 percent bumps frequency down one notch`() = runBlocking {
        val sources = FakeSources().apply { frequency = 5; syncInterval = 30; quota = 80 }
        val s = scheduler(sources)
        s.setWindowVisible(true)
        assertEquals(15 * 60_000L, s.marketDelayMillis(), "5 → 15（降一档）")
        sources.quota = 79
        assertEquals(5 * 60_000L, s.marketDelayMillis(), "未达 80% 不降档")
        sources.quota = 95
        sources.frequency = 15
        assertEquals(30 * 60_000L, s.marketDelayMillis(), "15 → 30")
    }

    @Test
    fun `sync delay uses configured interval with clamp`() = runBlocking {
        val sources = FakeSources().apply { syncInterval = 60 }
        val s = scheduler(sources)
        assertEquals(60 * 60_000L, s.syncDelayMillis())
        sources.syncInterval = 0 // 脏设置不得退化为忙轮询
        assertEquals(60_000L, s.syncDelayMillis())
    }

    @Test
    fun `market refresh passes manual flag and emits event`() {
        val sources = FakeSources()
        val s = scheduler(sources)
        val seen = collectEvents(s) { s.refreshMarketNow(manual = true) }
        assertNotNull(sources.lastManual)
        assertEquals(true, sources.lastManual)
        assertTrue(seen.any { it is SchedulerEvent.MarketRefreshed }, "刷新年事件")
        assertTrue(seen.none { it is SchedulerEvent.RateLimitFrequent }, "成功不提示限流")
    }

    @Test
    fun `repeated 429 emits frequent hint and keeps backoff window`() = runBlocking {
        var clock = Instant.parse("2026-09-10T00:00:00Z")
        val sources = FakeSources().apply {
            cgConfigured = false
            marketResult = okResult(error = MarketRefreshError.RateLimited(PriceSource.COINGECKO, true))
        }
        val s = scheduler(sources, now = { clock })
        val seen = collectEvents(s) {
            repeat(3) {
                s.refreshMarketNow(manual = false)
                clock = clock.plusSeconds(120) // 越过上一轮退避窗口
            }
        }
        val hint = seen.filterIsInstance<SchedulerEvent.RateLimitFrequent>()
        assertEquals(1, hint.size, "第 3 次连续 429 起发频发提示")
        assertEquals(3, hint.first().strike)
        assertTrue(hint.first().keylessHint, "未配置个人 Key → 提示注册免费 Key")
    }

    @Test
    fun `backoff window blocks premature kick and expires`() = runBlocking {
        var clock = Instant.parse("2026-09-10T00:00:00Z")
        val sources = FakeSources().apply {
            marketResult = okResult(error = MarketRefreshError.RateLimited(PriceSource.COINGECKO, false))
        }
        val s = scheduler(sources, now = { clock })
        s.refreshMarketNow(manual = false) // strike = 1 → 退避 1s
        assertTrue(s.inBackoffWindow(), "刚失败即在退避窗口内")
        clock = clock.plusSeconds(2)
        assertTrue(!s.inBackoffWindow(), "退避窗口过后恢复")
        // 成功后 strike 归零 → 无退避窗口
        sources.marketResult = okResult()
        s.refreshMarketNow(manual = false)
        assertTrue(!s.inBackoffWindow(), "成功后窗口关闭")
    }

    @Test
    fun `sync with no keys emits no event`() {
        val sources = FakeSources().apply { syncResults = emptyList() }
        val s = scheduler(sources)
        val seen = collectEvents(s) {
            assertEquals(emptyList(), s.syncNow())
        }
        assertTrue(seen.none { it is SchedulerEvent.SyncFinished }, "无密钥不发空通知")
    }

    @Test
    fun `sync with keys emits result event`() {
        val sources = FakeSources().apply { syncResults = listOf(syncResult(SyncStatus.OK)) }
        val s = scheduler(sources)
        val seen = collectEvents(s) {
            assertEquals(1, s.syncNow().size)
        }
        assertTrue(seen.any { it is SchedulerEvent.SyncFinished })
    }

    @Test
    fun `maintenance rotates logs and reports due backup reminder`() {
        val sources = FakeSources().apply { rotateSummary = "files=2"; reminderDays = 41L }
        val s = scheduler(sources)
        val seen = collectEvents(s) { s.runMaintenanceNow() }
        assertEquals(
            "files=2",
            seen.filterIsInstance<SchedulerEvent.LogRotated>().first().summary,
            "运行期轮转事件（M10 §5-5 遗留闭环）",
        )
        assertEquals(41L, seen.filterIsInstance<SchedulerEvent.BackupReminderDue>().first().daysSinceBase)
    }

    @Test
    fun `maintenance stays silent when backup not due`() {
        val sources = FakeSources().apply { reminderDays = null }
        val s = scheduler(sources)
        val seen = collectEvents(s) {
            s.runMaintenanceNow()
        }
        assertTrue(seen.none { it is SchedulerEvent.BackupReminderDue })
        assertTrue(seen.any { it is SchedulerEvent.LogRotated }, "轮转事件仍在（静默的只是提醒）")
    }

    @Test
    fun `window visibility transition is recorded`() {
        val s = scheduler(FakeSources())
        assertTrue(s.windowVisible, "默认可见")
        s.setWindowVisible(false)
        assertEquals(false, s.windowVisible)
        s.setWindowVisible(true)
        assertTrue(s.windowVisible)
    }

    private companion object {
        const val EVENT_WAIT_MILLIS: Long = 3_000
        const val EVENT_SETTLE_MILLIS: Long = 200

        fun okResult(error: MarketRefreshError? = null) = MarketRefreshResult(
            at = Instant.parse("2026-09-10T00:00:00Z"),
            source = PriceSource.COINGECKO,
            refreshedCoins = 4,
            untracked = emptyList(),
            quotaPercentUsed = null,
            error = error,
            cgConfigured = false,
            cmcConfigured = false,
        )

        fun syncResult(status: SyncStatus) = ApiKeySyncResult(
            apiKeyId = 1,
            apiKeyName = "主账户",
            status = status,
            newTrades = 3,
            duplicatesSkipped = 1,
            unresolvedSkipped = 0,
            partial = false,
            queuedSymbols = 0,
            error = null,
            message = "ok",
            at = Instant.parse("2026-09-10T00:00:00Z"),
        )
    }
}
