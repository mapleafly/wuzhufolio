package com.wuzhufolio.data.schedule

import com.wuzhufolio.domain.exchange.ApiKeySyncResult
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.exchange.SyncStatus
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.MarketRefreshResult
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.MarketSettingsService
import com.wuzhufolio.domain.market.RateBackoff
import com.wuzhufolio.domain.market.RefreshCadence
import com.wuzhufolio.domain.market.RefreshFrequency
import com.wuzhufolio.domain.schedule.LogRotationCadence
import com.wuzhufolio.domain.schedule.ScheduleJitter
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 调度事件（宿主 → 组合根：托盘通知 / 状态栏 / 日志）。宿主不直接产文案——
 * 展示口径（开关、措辞）由 app 层按设置决定，保持本层可测。
 */
sealed interface SchedulerEvent {

    /** 一次行情刷新结束（成功与失败都发；[MarketRefreshResult.error] 区分）。 */
    data class MarketRefreshed(val result: MarketRefreshResult) : SchedulerEvent

    /** 一次交易数据同步结束（无 API 密钥时 results 为空，组合根按空不发通知）。 */
    data class SyncFinished(val results: List<ApiKeySyncResult>) : SchedulerEvent

    /** 备份提醒到期（PRD 6.1：距上次备份 > 30 天）。 */
    data class BackupReminderDue(val daysSinceBase: Long) : SchedulerEvent

    /** 运行期日志轮转完成（[summary] 为脱敏摘要）。 */
    data class LogRotated(val summary: String) : SchedulerEvent

    /** 429 连续频发（PRD 6.1 无 Key 模式提示口径；文案展示归 M12 状态栏）。 */
    data class RateLimitFrequent(val strike: Int, val keylessHint: Boolean) : SchedulerEvent
}

/**
 * 调度宿主所需的最小事实来源（**窄接口**：宿主只需这 9 项，不需要两个用例服务的完整面）。
 *
 * 拆出来的收益：① 宿主与「行情/交易所用例」解耦，测试用单只读假实现即可覆盖全部循环逻辑；
 * ② 组合根显式声明调度到底在调什么（见 `DefaultSchedulerSources`）。
 */
interface SchedulerSources {

    /** 行情刷新频率档位（分钟；5/15/30）。 */
    suspend fun marketFrequencyMinutes(): Int

    /** API 同步间隔（分钟；15/30/60）。 */
    suspend fun syncIntervalMinutes(): Int

    /** 月度额度使用百分比（无个人 Key 模式 = null）。 */
    suspend fun quotaPercentUsed(): Int?

    /** 是否已配置个人 CoinGecko Key（429 频次提示的文案分流依据）。 */
    suspend fun cgConfigured(): Boolean

    /** 立即刷新行情（[manual] 透传用例；true = 手动/唤醒触发）。 */
    suspend fun refreshMarket(manual: Boolean): MarketRefreshResult

    /** 立即同步交易数据（无密钥 = 空列表）。 */
    suspend fun syncNow(): List<ApiKeySyncResult>

    /** 运行期日志轮转，返回脱敏摘要。 */
    suspend fun rotateLogs(): String

    /**
     * 历史价格快照降采样（M5 T5.2「降采样正确」的运行期执行点；M13 闭环 M5 §5-4 登记遗留）：
     * 删除 90 天保留界之前的非整点小时桶，返回删除行数（幂等，无删除返回 0）。
     */
    suspend fun compactSnapshots(): Int

    /** 备份提醒判定：到期返回距基线天数，未到期返回 null。 */
    suspend fun backupReminderDays(): Long?
}

/**
 * 后台调度循环宿主（M11 · T11.1/T11.3 载体 · PRD §7.2 模块 9.2）。
 *
 * 挂三条循环 + 一个唤醒通道：
 * 1. **行情刷新**：间隔 = [RefreshCadence]（窗口可见按刷新频率 5/15/30；托盘驻留按 API 同步间隔降频；
 *    额度 80% 自动降一档），叠加 [ScheduleJitter] ±20% 抖动；
 * 2. **交易数据同步**：间隔 = API 同步间隔（15/30/60，默认 30），前台后台一致（PRD 9.2）；
 * 3. **运行期日志轮转**：间隔 = [LogRotationCadence] 6 小时（M10 §5-5 遗留闭环），同 tick 顺带复查备份提醒；
 * 4. **唤醒通道**：窗口从托盘恢复可见 → 立即刷新一次行情（PRD 9.2「窗口恢复可见时立即刷新一次」）；
 *    429 退避期内忽略唤醒（[RateBackoff]，避免恢复可见即撞限流）。
 *
 * 错误隔离：[SchedulerSources] 实现层各自持锁（单飞）；本层每个循环用 `runCatching` 包住一次 tick，
 * 异常只记日志不终止循环——长驻托盘进程不得因单次网络异常而死。
 */
// 装配袋参数（源 + 两回调 + 时钟/随机注入）；函数多属循环宿主固有：3 条循环 + 启停/可见性 3 +
// 单次执行 3 + 间隔计算/退避/事件 6——拆开会割裂共享状态（strike/可见性/唤醒通道）
@Suppress("LongParameterList", "TooManyFunctions")
class BackgroundScheduler(
    private val sources: SchedulerSources,
    /** 每次 tick 的旁路（代理复检、状态刷新；组合根注入）。 */
    private val onTick: () -> Unit = {},
    /** 当前时刻（注入便于测试）。 */
    private val now: () -> Instant = Instant::now,
    /** 随机源（抖动；注入便于测试）。 */
    private val random: () -> Double = { Random.nextDouble() },
    private val logger: Logger = LoggerFactory.getLogger("wuzhufolio.schedule"),
) {

    /** 窗口是否可见（主窗口显示且未最小化；托盘驻留 = false）。由组合根在窗口状态变化时写入。 */
    @Volatile
    var windowVisible: Boolean = true
        private set

    private val _events = MutableSharedFlow<SchedulerEvent>(extraBufferCapacity = EVENT_BUFFER)

    /**
     * 调度事件流（托盘通知 / 状态栏消费；无订阅者时事件进缓冲、溢出即丢弃——
     * 通知是尽力而为的旁路，不得反压调度循环）。
     */
    val events: SharedFlow<SchedulerEvent> = _events.asSharedFlow()

    /**
     * 事件订阅者数（诊断 + 测试同步点）。
     * [events] 为无重放 SharedFlow——订阅建立前发出的事件不补发，故「触发动作」前需先确认已有订阅者。
     */
    val eventSubscribers: StateFlow<Int> = _events.subscriptionCount

    /** 行情循环唤醒通道（容量 1 且合并——连点/多次恢复只补一次）。 */
    private val marketKick = Channel<Unit>(Channel.CONFLATED)

    private var jobs: List<Job> = emptyList()

    /** 429 连续命中次数（成功归零；用于频发提示与退避窗口）。 */
    private var rateLimitStrike: Int = 0

    /** 上次行情尝试时刻（退避窗口判定基准）。 */
    private var lastMarketAttemptAt: Instant? = null

    /** 窗口可见性变更（组合根写入）；false → true 触发一次立即刷新（PRD 9.2）。 */
    fun setWindowVisible(visible: Boolean) {
        val was = windowVisible
        windowVisible = visible
        if (!was && visible) {
            logger.info("window restored visible | immediate market refresh scheduled")
            marketKick.trySend(Unit)
        }
    }

    /** 启动三条循环（scope 由组合根持有；重复调用无副作用）。 */
    fun start(scope: CoroutineScope) {
        if (jobs.isNotEmpty()) return
        jobs = listOf(
            scope.launch { marketLoop() },
            scope.launch { syncLoop() },
            scope.launch { maintenanceLoop() },
        )
        // 间隔读取是 suspend（走设置库）——放到协程里记，避免 start 变成 suspend/阻塞调用方
        scope.launch {
            logger.info(
                "scheduler started | syncInterval=" +
                    runCatching { sources.syncIntervalMinutes() }.getOrDefault(30) + "min",
            )
        }
    }

    /** 停止全部循环（关窗退出/登出时调用）。 */
    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
    }

    // ---- 单次执行（同时是托盘「立即同步」入口与测试入口） ----

    /** 托盘菜单「立即同步」（PRD §9.1）；返回 per-key 结果（无密钥 = 空列表）。 */
    suspend fun syncNow(): List<ApiKeySyncResult> = syncOnce()

    /** 立即刷新行情（手动/托盘触发；[manual] 透传用例）。 */
    suspend fun refreshMarketNow(manual: Boolean = true): MarketRefreshResult = refreshMarketOnce(manual)

    /** 立即执行一次运行期轮转 + 备份提醒复查。 */
    suspend fun runMaintenanceNow() = runMaintenance()

    // ---- 循环 ----

    private suspend fun marketLoop() {
        while (currentCoroutineContext().isActive) {
            val waitMillis = marketDelayMillis()
            val kicked = withTimeoutOrNull(waitMillis) { marketKick.receive() } != null
            if (kicked && inBackoffWindow()) {
                logger.info("market refresh kick ignored | within 429 backoff window")
                continue
            }
            onTick()
            runCatching { refreshMarketOnce(manual = kicked) }
                .onFailure { logger.warn("market refresh tick failed: " + it.javaClass.simpleName) }
        }
    }

    private suspend fun syncLoop() {
        while (currentCoroutineContext().isActive) {
            delay(syncDelayMillis())
            onTick()
            runCatching { syncOnce() }
                .onFailure { logger.warn("sync tick failed: " + it.javaClass.simpleName) }
        }
    }

    private suspend fun maintenanceLoop() {
        while (currentCoroutineContext().isActive) {
            delay(maintenanceDelayMillis())
            runCatching { runMaintenance() }
                .onFailure { logger.warn("maintenance tick failed: " + it.javaClass.simpleName) }
        }
    }

    // ---- 内部 ----

    private suspend fun refreshMarketOnce(manual: Boolean): MarketRefreshResult {
        lastMarketAttemptAt = now()
        val result = sources.refreshMarket(manual)
        val limited = result.error is MarketRefreshError.RateLimited
        rateLimitStrike = if (limited) rateLimitStrike + 1 else 0
        if (limited && rateLimitStrike >= RATE_LIMIT_HINT_STRIKES) {
            emit(SchedulerEvent.RateLimitFrequent(rateLimitStrike, keylessHint()))
        }
        emit(SchedulerEvent.MarketRefreshed(result))
        return result
    }

    private suspend fun syncOnce(): List<ApiKeySyncResult> {
        val results = sources.syncNow()
        // 无密钥 = 用户尚未配置交易所：不发事件（避免每次 tick 空通知）
        if (results.isNotEmpty()) emit(SchedulerEvent.SyncFinished(results))
        if (results.any { it.status == SyncStatus.FAILED }) {
            logger.warn("sync tick finished with failures | keys=" + results.size)
        }
        return results
    }

    private suspend fun runMaintenance() {
        val summary = sources.rotateLogs()
        emit(SchedulerEvent.LogRotated(summary))
        // M13：历史快照降采样（ADR-005 §3 / data-model §2.11「近 90 天小时级、更早日级」的运行期执行点；
        // M5 §5-4 登记「压缩任务执行点接入调度宿主」此前未接线——本处闭环）
        val compacted = sources.compactSnapshots()
        if (compacted > 0) logger.info("price snapshot compaction removed " + compacted + " hourly rows")
        val days = sources.backupReminderDays()
        if (days != null) emit(SchedulerEvent.BackupReminderDue(days))
        onTick()
    }

    /** 429 退避窗口内？（唤醒路径用：连续命中后退避期内不提前刷新） */
    internal fun inBackoffWindow(): Boolean {
        val last = lastMarketAttemptAt
        if (rateLimitStrike <= 0 || last == null) return false
        val backoffMillis = RateBackoff.delaySeconds(rateLimitStrike) * 1000L
        return now().toEpochMilli() - last.toEpochMilli() < backoffMillis
    }

    /** 下次行情刷新等待毫秒（口径 = [RefreshCadence] + ±20% 抖动）。 */
    internal suspend fun marketDelayMillis(): Long {
        val frequency = runCatching { sources.marketFrequencyMinutes() }.getOrDefault(5)
        val syncInterval = runCatching { sources.syncIntervalMinutes() }.getOrDefault(30)
        val quotaPercent = runCatching { sources.quotaPercentUsed() }.getOrNull()
        val downgraded = quotaPercent != null && quotaPercent >= QUOTA_DOWNGRADE_PERCENT
        val minutes = RefreshCadence(
            frequency = RefreshFrequency.fromMinutes(frequency),
            syncIntervalMinutes = syncInterval,
        ).nextIntervalMinutes(windowVisible = windowVisible, quotaDowngraded = downgraded)
        return ScheduleJitter.apply(minutes * MILLIS_PER_MINUTE, random())
    }

    /** 下次同步等待毫秒（15/30/60 分钟 + 抖动）；限幅防止脏设置导致忙轮询。 */
    internal suspend fun syncDelayMillis(): Long {
        val minutes = runCatching { sources.syncIntervalMinutes() }.getOrDefault(30).coerceIn(1, 24 * 60)
        return ScheduleJitter.apply(minutes * MILLIS_PER_MINUTE, random())
    }

    /** 下次维护等待毫秒（6 小时 + 抖动）。 */
    internal fun maintenanceDelayMillis(): Long =
        ScheduleJitter.apply(LogRotationCadence.INTERVAL_HOURS * 3_600_000L, random())

    private suspend fun keylessHint(): Boolean =
        runCatching { !sources.cgConfigured() }.getOrDefault(false)

    /** 事件出口（无订阅者/缓冲满时静默丢弃——通知是旁路，不得影响调度）。 */
    private fun emit(event: SchedulerEvent) {
        if (!_events.tryEmit(event)) {
            logger.debug("scheduler event dropped | kind=" + event.javaClass.simpleName)
        }
    }

    companion object {
        /** 额度使用率达此百分比 → 刷新频率自动降一档（PRD 6.1）。 */
        const val QUOTA_DOWNGRADE_PERCENT: Int = 80

        /** 连续 429 达此次数 → 发频发提示（PRD 6.1「429 频发时提示」）。 */
        const val RATE_LIMIT_HINT_STRIKES: Int = 3

        /** 事件缓冲容量。 */
        const val EVENT_BUFFER: Int = 16

        private const val MILLIS_PER_MINUTE: Long = 60_000L
    }
}

/**
 * 生产装配：把三个用例服务 + 三个引导层动作（日志轮转/快照降采样/备份提醒）适配成 [SchedulerSources]
 * （组合根唯一装配点，见 `AppBootstrap.run`）。
 */
class DefaultSchedulerSources(
    private val marketRefreshService: MarketRefreshService,
    private val marketSettingsService: MarketSettingsService,
    private val syncService: ExchangeSyncService,
    private val rotateLogs: suspend () -> String,
    private val compactSnapshots: suspend () -> Int,
    private val backupReminderDays: suspend () -> Long?,
) : SchedulerSources {

    override suspend fun marketFrequencyMinutes(): Int = marketSettingsService.refreshFrequencyMinutes()

    override suspend fun syncIntervalMinutes(): Int = syncService.syncIntervalMinutes()

    override suspend fun quotaPercentUsed(): Int? = marketRefreshService.quotaPercentUsed()

    override suspend fun cgConfigured(): Boolean = marketSettingsService.keyStatus().cgConfigured

    override suspend fun refreshMarket(manual: Boolean): MarketRefreshResult =
        marketRefreshService.refresh(manual = manual)

    override suspend fun syncNow(): List<ApiKeySyncResult> = syncService.syncNow()

    override suspend fun rotateLogs(): String = rotateLogs.invoke()

    override suspend fun compactSnapshots(): Int = compactSnapshots.invoke()

    override suspend fun backupReminderDays(): Long? = backupReminderDays.invoke()
}
