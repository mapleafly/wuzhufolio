package com.wuzhufolio.ui.shell

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.exchange.SyncLogRow
import com.wuzhufolio.domain.exchange.SyncStatus
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.MarketRefreshResult
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.ui.i18n.WzFormat
import com.wuzhufolio.ui.i18n.shellStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/** 状态栏展示信息（M12 T12.1 + T12.2；M11 §6 遗留「状态栏数据源/额度/429 文案」闭环）。 */
data class ShellStatus(
    /** 同步状态文案（空闲 / 同步中 / 成功 N 条 / 失败原因）。 */
    val syncText: String = "",
    /** 数据源徽章（CoinGecko / CoinMarketCap 兜底 + 上次成功时刻）。 */
    val dataSourceText: String = "",
    /** 额度/限流提示（额度 ≥ 80% 或共享限流频发；interaction.md §2.5），null = 不提示。 */
    val quotaText: String? = null,
    /** 备份提醒（距上次备份 > 30 天；PRD 6.1），null = 不提醒。 */
    val backupText: String? = null,
    /** 网络断开（行情源不可达；interaction.md §1.1 N1）。 */
    val offline: Boolean = false,
    /** 现价快照时刻（右对齐「上次价格更新」）。 */
    val priceAsOf: Instant? = null,
    /** 版本号（状态栏右端）。 */
    val version: String = "",
    /** 提示文案（额度/备份提醒；null = 无提示）。 */
    val notice: String? = null,
    /** 提示是否为警示级（额度/断链 = 警示色；备份提醒 = 常规色）。 */
    val noticeWarn: Boolean = false,
)

/**
 * 状态栏数据源 VM（M12）：把行情刷新结果（数据源/时刻/额度/错误）与最近交易同步记录
 * 汇总为状态栏文案。轮询间隔默认 30s（状态栏为弱实时指示，不需要高频查询；页面销毁即停）。
 *
 * 文案口径：全部经 `shellStrings`（zh/en），不在本层写字面量（i18n 约定见 ui/i18n/CommonStrings.kt）。
 */
class ShellStatusViewModel(
    private val marketRefreshService: MarketRefreshService,
    private val exchangeSyncService: ExchangeSyncService,
    /** 备份提醒天数提供者（null = 未到期/不可判定；app 层注入 M11 的 BackupReminder 口径）。 */
    private val backupReminderDays: suspend () -> Long? = { null },
    /** 顶栏手动同步进行中判定（同步中优先于最近一次结果展示）。 */
    private val syncingProvider: () -> Boolean = { false },
    /** 状态栏版本号（app 组合根注入应用版本常量）。 */
    private val appVersion: String = "",
    private val pollIntervalMs: Long = DEFAULT_POLL_MS,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ShellStatus(version = appVersion))
    val state: StateFlow<ShellStatus> = _state.asStateFlow()

    init {
        refresh()
        scope.launch {
            while (isActive) {
                delay(pollIntervalMs)
                refresh()
            }
        }
    }

    /** 立即重算（行情刷新/同步完成后由调用方触发，保证指示及时）。 */
    fun refresh() {
        scope.launch {
            val market = runCatching { marketRefreshService.lastResult() }.getOrNull()
            val quota = runCatching { marketRefreshService.quotaPercentUsed() }.getOrNull()
            val sync = runCatching { exchangeSyncService.recentSyncLogs(1).firstOrNull() }.getOrNull()
            val backup = runCatching { backupReminderDays() }.getOrNull()
            val quotaText = quotaText(quota, market)
            val backupText = backup?.let { shellStrings.backupReminder(it) }
            _state.update { current ->
                current.copy(
                    syncText = syncText(sync),
                    dataSourceText = dataSourceText(market),
                    quotaText = quotaText,
                    backupText = backupText,
                    offline = isOffline(market),
                    priceAsOf = market?.at,
                    version = appVersion,
                    // 额度提示优先于备份提醒（interaction.md §2.5 为异常级；备份提醒为常规建议）
                    notice = quotaText ?: backupText,
                    noticeWarn = quotaText != null,
                )
            }
        }
    }

    private fun syncText(sync: SyncLogRow?): String = when {
        syncingProvider() -> shellStrings.syncRunning()
        sync == null -> shellStrings.syncIdle()
        else -> {
            val base = when (sync.status) {
                SyncStatus.OK -> shellStrings.syncOk(sync.newTradesCount)
                SyncStatus.FAILED -> shellStrings.syncFailed(sync.message)
            }
            base + " · " + WzFormat.dateTime(sync.syncTime)
        }
    }

    private fun dataSourceText(market: MarketRefreshResult?): String =
        if (market?.source == null) {
            shellStrings.dataSourceBadge(null, null)
        } else {
            shellStrings.dataSourceBadge(market.source, market.at?.let { WzFormat.dateTime(it) })
        }

    private fun quotaText(quotaPercent: Int?, market: MarketRefreshResult?): String? = when {
        quotaPercent != null && quotaPercent >= QUOTA_WARN_PERCENT -> shellStrings.quotaWarning(quotaPercent)
        market?.error is MarketRefreshError.RateLimited -> shellStrings.sharedRateLimitHint()
        else -> null
    }

    private fun isOffline(market: MarketRefreshResult?): Boolean = market?.error is MarketRefreshError.Network

    override fun onCleared() {
        scope.cancel()
    }

    /** 页面卸载显式释放（Compose remember VM 无宿主时调用）。 */
    fun dispose() {
        scope.cancel()
    }

    companion object {
        const val DEFAULT_POLL_MS: Long = 30_000L

        /** 额度提示线（PRD：月度额度达 80% 自动降一档并在状态栏提示）。 */
        const val QUOTA_WARN_PERCENT: Int = 80

        /** 数据源是否为兜底（测试与展示判定用）。 */
        internal fun isFallback(source: PriceSource?): Boolean = source == PriceSource.COINMARKETCAP
    }
}
