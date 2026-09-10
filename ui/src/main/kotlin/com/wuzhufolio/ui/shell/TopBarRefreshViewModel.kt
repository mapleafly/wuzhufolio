package com.wuzhufolio.ui.shell

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.MarketSettingsService
import com.wuzhufolio.domain.market.MarketWatchService
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.domain.portfolio.PortfolioService
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import com.wuzhufolio.ui.i18n.shellStrings
import com.wuzhufolio.ui.market.MarketCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 顶栏「立即刷新行情」（M12 T12.1 · ia.md §1.1 顶栏「手动刷新行情按钮」· PRD 故事 3.2-6）。
 *
 * 刷新币集 = 当前持仓币种 ∪ 自选币种（去重；两者皆为页面数据源，用户点一次应看到全量现价更新），
 * 法币 = 基础法币设置。失败保持上次数据并 toast（interaction.md §2.4 离线态）。
 * 刷新完成后经 [onRefreshed] 通知状态栏 VM 立即重算数据源徽章与额度提示。
 */
class TopBarRefreshViewModel(
    private val refreshService: MarketRefreshService,
    private val marketSettingsService: MarketSettingsService,
    private val watchService: MarketWatchService,
    private val portfolioService: PortfolioService,
    private val onRefreshed: () -> Unit = {},
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _toast = MutableStateFlow<WzToast?>(null)
    val toast: StateFlow<WzToast?> = _toast.asStateFlow()

    /** 手动刷新行情（进行中重复点击忽略）。 */
    fun refreshNow() {
        if (_refreshing.value) return
        _refreshing.value = true
        scope.launch {
            try {
                val fiat = runCatching { marketSettingsService.baseFiat() }.getOrDefault("USD")
                val coins = LinkedHashSet<String>()
                runCatching { portfolioService.snapshot().rows.map { it.cgId } }.getOrNull()?.let(coins::addAll)
                runCatching { watchService.watchCoins().map { it.cgId } }.getOrNull()?.let(coins::addAll)
                val result = refreshService.refresh(manual = true, coins = coins.toList(), fiats = listOf(fiat))
                val error = result.error
                _toast.value = if (error != null) {
                    WzToast(WzToastKind.Failure, MarketCopy.errorText(error))
                } else {
                    WzToast(
                        WzToastKind.Success,
                        shellStrings.refreshOk(
                            cgConfigured = result.cgConfigured,
                            fallback = result.source == PriceSource.COINMARKETCAP,
                        ),
                    )
                }
            } catch (t: Throwable) {
                _toast.value = WzToast(WzToastKind.Failure, t.message ?: MarketCopy.REFRESH_FAILED)
            } finally {
                _refreshing.value = false
                onRefreshed()
            }
        }
    }

    fun dismissToast() {
        _toast.value = null
    }

    override fun onCleared() {
        scope.cancel()
    }

    /** 页面卸载显式释放（Compose remember VM 无宿主时调用）。 */
    fun dispose() {
        scope.cancel()
    }
}
