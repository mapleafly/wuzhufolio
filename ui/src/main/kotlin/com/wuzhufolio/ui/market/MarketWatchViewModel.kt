package com.wuzhufolio.ui.market

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.market.MarketQuotesService
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.MarketRefreshResult
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.MarketSettingsService
import com.wuzhufolio.domain.market.MarketWatchService
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.domain.market.WatchQuoteRow
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
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

/** 行情页状态（D21）。 */
data class WatchUiState(
    val rows: List<WatchQuoteRow> = emptyList(),
    /** true = 尚未写入自选，当前为默认种子（稳定币白名单）。 */
    val defaultSeed: Boolean = true,
    val fiat: String = "USD",
    val query: String = "",
    val candidates: List<CatalogCoin> = emptyList(),
    val searchBusy: Boolean = false,
    val refreshBusy: Boolean = false,
    val toast: WzToast? = null,
)

/**
 * 行情页 VM（D21）：加载自选/默认种子 → 报价组装；搜索添加/移除（持久化自选）；手动刷新 +
 * 页面存活期按刷新频率自动轮询（M11 调度宿主接入前的最小页面内轮询，离开页面即停止）。
 */
@Suppress("TooManyFunctions") // 行情页动作面（加载/搜索/增删/刷新×2/轮询/toast），小而直白
class MarketWatchViewModel(
    private val watchService: MarketWatchService,
    private val quotesService: MarketQuotesService,
    private val refreshService: MarketRefreshService,
    private val settingsService: MarketSettingsService,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(WatchUiState())
    val state: StateFlow<WatchUiState> = _state.asStateFlow()

    init {
        scope.launch { reloadAll() }
        startAutoRefresh()
    }

    /** 页面存活期自动轮询（按行情刷新频率；离开页面 dispose 即停）。 */
    fun startAutoRefresh() {
        scope.launch {
            while (isActive) {
                val minutes = runCatching { settingsService.refreshFrequencyMinutes() }.getOrDefault(5)
                delay(minutes * 60_000L)
                refreshInternal(manual = false)
            }
        }
    }

    fun reloadAll() {
        scope.launch {
            val fiat = settingsService.baseFiat()
            val hasCustom = watchService.hasCustomList()
            val coins = watchService.watchCoins()
            val rows = quotesService.quotesFor(coins, fiat)
            _state.update { it.copy(fiat = fiat, defaultSeed = !hasCustom, rows = rows) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        val q = query.trim()
        if (q.isEmpty()) {
            _state.update { it.copy(candidates = emptyList(), searchBusy = false) }
            return
        }
        if (_state.value.searchBusy) return
        _state.update { it.copy(searchBusy = true) }
        scope.launch {
            val found = watchService.searchCandidates(q)
            _state.update { it.copy(candidates = found, searchBusy = false) }
        }
    }

    fun clearSearch() {
        _state.update { it.copy(query = "", candidates = emptyList()) }
    }

    fun addCoin(cgId: String) {
        scope.launch {
            runCatching { watchService.addCoin(cgId) }
                .onSuccess { clearSearch(); reloadAll() }
                .onFailure { toast(WzToastKind.Failure, it.message ?: "添加失败") }
        }
    }

    fun removeCoin(cgId: String) {
        scope.launch {
            watchService.removeCoin(cgId)
            reloadAll()
        }
    }

    fun refreshNow() {
        refreshInternal(manual = true)
    }

    private fun refreshInternal(manual: Boolean) {
        if (_state.value.refreshBusy) return
        val coins = _state.value.rows.map { it.cgId }
        if (coins.isEmpty()) {
            reloadAll()
            return
        }
        _state.update { it.copy(refreshBusy = true) }
        scope.launch {
            try {
                val result = refreshService.refresh(manual = manual, coins = coins, fiats = listOf(_state.value.fiat))
                reloadAll()
                onRefreshResult(result)
            } catch (t: Throwable) {
                toast(WzToastKind.Failure, t.message ?: "行情刷新失败")
            } finally {
                _state.update { it.copy(refreshBusy = false) }
            }
        }
    }

    private fun onRefreshResult(result: MarketRefreshResult) {
        val error = result.error
        if (error != null) {
            toast(WzToastKind.Failure, MarketCopy.errorText(error))
            return
        }
        if (result.refreshedCoins == 0 && result.untracked.isNotEmpty()) {
            toast(
                WzToastKind.Failure,
                MarketCopy.errorText(MarketRefreshError.Untracked(result.untracked.first())),
            )
            return
        }
        val text = when (result.source) {
            PriceSource.COINMARKETCAP -> "行情已刷新（数据源：CoinMarketCap 兜底）"
            else -> if (result.cgConfigured) {
                "行情已刷新（CoinGecko 专属额度）"
            } else {
                "行情已刷新（CoinGecko 无 Key 公共 API）"
            }
        }
        toast(WzToastKind.Success, text)
    }

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun toast(kind: WzToastKind, message: String) {
        _state.update { it.copy(toast = WzToast(kind, message)) }
    }

    override fun onCleared() {
        scope.cancel()
    }

    /** 页面卸载显式释放（Compose remember VM 无宿主时调用）。 */
    fun dispose() {
        scope.cancel()
    }
}
