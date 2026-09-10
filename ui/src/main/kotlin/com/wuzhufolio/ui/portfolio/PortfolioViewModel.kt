package com.wuzhufolio.ui.portfolio

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.portfolio.PortfolioRow
import com.wuzhufolio.domain.portfolio.PortfolioService
import com.wuzhufolio.domain.portfolio.PortfolioSnapshot
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.domain.settings.GeneralSettingsService
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import com.wuzhufolio.ui.i18n.portfolioStrings
import com.wuzhufolio.ui.i18n.shellStrings
import com.wuzhufolio.ui.market.MarketCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** 资产列表排序键（ia.md §2.5：支持按市值、名称、盈亏等字段排序）。 */
enum class AssetSortKey {
    COIN,
    QUANTITY,
    AVG_COST,
    PRICE,
    MARKET_VALUE,
    FLOAT_PNL,
    REALIZED_PNL,
    ;

    /** 排序首次点击的默认方向（数值列降序、名称列升序——原型 `portSort` 口径）。 */
    val defaultAscending: Boolean get() = this == COIN
}

/** 聚合页 UI 状态（仪表盘与资产列表共用；M12 T12.1）。 */
data class PortfolioUiState(
    val loading: Boolean = true,
    val fiat: String = "USD",
    val snapshot: PortfolioSnapshot? = null,
    /** 小额币种阈值（基础法币；0 = 不启用归并——仪表盘环形图「其他」口径）。 */
    val threshold: BigDecimal = BigDecimal.ZERO,
    val sortKey: AssetSortKey = AssetSortKey.MARKET_VALUE,
    val sortAscending: Boolean = false,
    val refreshing: Boolean = false,
    val toast: WzToast? = null,
) {
    /** 当前排序下的持仓行（排序就地完成，不重查服务）。 */
    val rows: List<PortfolioRow> get() = snapshot?.rows.orEmpty().sortedWith(comparator())

    /** 环形图分区输入（阈值归并「其他」；缺价币无市值不计入分布）。 */
    val distribution: List<DistributionSlice>
        get() {
            val priced = rows.filter { (it.marketValueFiat?.signum() ?: 0) > 0 }
            if (priced.isEmpty()) return emptyList()
            val thresholdValue = threshold
            val major = ArrayList<DistributionSlice>()
            val smallNames = ArrayList<String>()
            var smallTotal = BigDecimal.ZERO
            for (row in priced) {
                val value = row.marketValueFiat!!
                if (thresholdValue.signum() > 0 && value < thresholdValue) {
                    smallTotal += value
                    smallNames += row.symbol
                } else {
                    major += DistributionSlice(
                        cgId = row.cgId,
                        label = row.symbol,
                        value = value,
                        quantity = row.quantity,
                    )
                }
            }
            if (smallTotal.signum() > 0) {
                major += DistributionSlice(
                    cgId = OTHER_SLICE_ID,
                    label = portfolioStrings.otherSegment,
                    detail = portfolioStrings.otherSmallCoins(smallNames.joinToString(" · ")),
                    value = smallTotal,
                    quantity = null,
                )
            }
            return major
        }

    private fun comparator(): Comparator<PortfolioRow> {
        val base = when (sortKey) {
            AssetSortKey.COIN -> compareBy<PortfolioRow> { it.symbol.lowercase() }
            AssetSortKey.QUANTITY -> compareBy<PortfolioRow> { it.quantity }
            AssetSortKey.AVG_COST -> compareBy<PortfolioRow> { it.avgCostFiat ?: BigDecimal.valueOf(-1) }
            AssetSortKey.PRICE -> compareBy<PortfolioRow> { it.priceFiat ?: BigDecimal.valueOf(-1) }
            AssetSortKey.MARKET_VALUE -> compareBy<PortfolioRow> { it.marketValueFiat ?: BigDecimal.valueOf(-1) }
            AssetSortKey.FLOAT_PNL -> compareBy<PortfolioRow> { it.floatPnlFiat ?: BigDecimal.valueOf(Long.MIN_VALUE) }
            AssetSortKey.REALIZED_PNL -> compareBy<PortfolioRow> { it.realizedPnlFiat }
        }
        return if (sortAscending) base else base.reversed()
    }

    companion object {
        /** 环形图「其他」分区标识（非真实 cg_id，点击不跳转详情）。 */
        const val OTHER_SLICE_ID: String = "__other__"

        /** 分布每区最多着色数（原型调色板长度；超出循环复用颜色）。 */
        const val PALETTE_SIZE: Int = 6
    }
}

/** 环形图分区（[quantity] = null 表示「其他」合计段）。 */
data class DistributionSlice(
    val cgId: String,
    val label: String,
    val detail: String? = null,
    val value: BigDecimal,
    val quantity: BigDecimal?,
)

/**
 * 聚合页 VM（M12 T12.1）：加载账户快照（全量重放派生）→ 排序/分布派生；手动刷新行情后重载；
 * 币种详情由 [CoinDetailViewModel] 单页承载。
 *
 * 刷新口径与行情页一致（M5 T5.4）：只刷新当前持仓币种 + 基础法币，不轮询（轮询由 M11 调度宿主与
 * 行情页自动轮询承担）；刷新失败保持上次数据并 toast（interaction.md §2.4 离线态）。
 */
class PortfolioViewModel(
    private val portfolioService: PortfolioService,
    private val refreshService: MarketRefreshService,
    private val generalSettings: GeneralSettingsService,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(PortfolioUiState())
    val state: StateFlow<PortfolioUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        scope.launch {
            try {
                val settings = generalSettings.view()
                val snapshot = portfolioService.snapshot()
                _state.update {
                    it.copy(
                        loading = false,
                        fiat = snapshot.fiat,
                        snapshot = snapshot,
                        threshold = settings.smallThreshold,
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false) }
                toast(WzToastKind.Failure, t.message ?: portfolioStrings.loading)
            }
        }
    }

    fun toggleSort(key: AssetSortKey) {
        _state.update {
            if (it.sortKey == key) {
                it.copy(sortAscending = !it.sortAscending)
            } else {
                it.copy(sortKey = key, sortAscending = key.defaultAscending)
            }
        }
    }

    /** 手动刷新行情（PRD 故事 3.2-6）：刷新当前持仓币种 → 重载快照。 */
    fun refreshQuotes() {
        if (_state.value.refreshing) return
        val coins = _state.value.snapshot?.rows?.map { it.cgId }.orEmpty()
        if (coins.isEmpty()) {
            reload()
            return
        }
        _state.update { it.copy(refreshing = true) }
        scope.launch {
            try {
                val result = refreshService.refresh(
                    manual = true,
                    coins = coins,
                    fiats = listOf(_state.value.fiat),
                )
                reload()
                val error: MarketRefreshError? = result.error
                if (error != null) {
                    toast(WzToastKind.Failure, MarketCopy.errorText(error))
                } else {
                    toast(
                        WzToastKind.Success,
                        shellStrings.refreshOk(
                            cgConfigured = result.cgConfigured,
                            fallback = result.source == PriceSource.COINMARKETCAP,
                        ),
                    )
                }
            } catch (t: Throwable) {
                toast(WzToastKind.Failure, t.message ?: portfolioStrings.refreshFailed)
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
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
