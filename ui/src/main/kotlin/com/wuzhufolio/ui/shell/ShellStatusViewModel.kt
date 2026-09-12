package com.wuzhufolio.ui.shell

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.exchange.SyncStatus
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.MarketRefreshResult
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.PriceSource
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

/**
 * 状态栏**原始数据**（M12 T12.1 + T12.2；M11 §6 遗留「状态栏数据源/额度/429 文案」闭环）。
 *
 * **只存数据、不存文案**（2026-09-11 走查修复轮二）：本地化文案必须在渲染期由 [shellStrings] 派生
 * （见 [syncText]/[dataSourceText]/[noticeText]）——把算好的中文字符串塞进 StateFlow 会让语言切换
 * 后顶栏「数据源：未刷新」这类文案停留在旧语言，直到下一次轮询才刷新（走查实测的滞后现象）。
 */
data class ShellStatus(
    /** 最近一次同步记录（null = 尚无同步）。 */
    val syncStatus: SyncStatus? = null,
    val syncNewTrades: Int = 0,
    val syncMessage: String? = null,
    val syncAt: Instant? = null,
    /** 行情最近一次成功刷新的数据源与时刻（null = 从未成功）。 */
    val marketSource: PriceSource? = null,
    val marketAt: Instant? = null,
    /** 行情源网络不可达（interaction.md §1.1 N1 断链）。 */
    val marketOffline: Boolean = false,
    /** 已配置个人 Key 时的月度额度使用率（0–100；无 Key 模式 null）。 */
    val quotaPercent: Int? = null,
    /** 无 Key 公共 API 被共享限流（interaction.md §2.5）。 */
    val rateLimited: Boolean = false,
    /** 距上次备份天数（> 30 才提示；null = 不提醒）。 */
    val backupDays: Long? = null,
    /** 版本号（状态栏右端）。 */
    val version: String = "",
    /** 是否已完成至少一次数据装填（初始态 false——避免把「还没查」误当作「没有同步记录」）。 */
    val loaded: Boolean = false,
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
            _state.update { current ->
                current.copy(
                    syncStatus = sync?.status,
                    syncNewTrades = sync?.newTradesCount ?: 0,
                    syncMessage = sync?.message,
                    syncAt = sync?.syncTime,
                    marketSource = market?.source,
                    marketAt = market?.at,
                    marketOffline = isOffline(market),
                    quotaPercent = quota,
                    rateLimited = market?.error is MarketRefreshError.RateLimited,
                    backupDays = backup,
                    version = appVersion,
                    loaded = true,
                )
            }
        }
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
