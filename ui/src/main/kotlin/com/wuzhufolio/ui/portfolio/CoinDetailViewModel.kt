package com.wuzhufolio.ui.portfolio

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.CalibrationBlockedException
import com.wuzhufolio.domain.ledger.CalibrationPreparation
import com.wuzhufolio.domain.ledger.CalibrationUseCase
import com.wuzhufolio.domain.ledger.TransactionLedgerService
import com.wuzhufolio.domain.ledger.TransactionRow
import com.wuzhufolio.domain.ledger.TxFilter
import com.wuzhufolio.domain.portfolio.CoinDetail
import com.wuzhufolio.domain.portfolio.PortfolioService
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import com.wuzhufolio.ui.i18n.ledgerStrings
import com.wuzhufolio.ui.i18n.portfolioStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 校准弹窗阶段（币种详情入口；预览 → 执行 → 结果）。 */
sealed interface CalibrationPhase {
    /** 准备中（读取交易所余额 + 校准时市价）。 */
    data object Preparing : CalibrationPhase

    /** 前提不满足（多来源/无密钥/无行情/余额获取失败）——展示原因，不提供执行。 */
    data class Blocked(val message: String) : CalibrationPhase

    /** 预览就绪（可执行）。 */
    data class Ready(val prep: CalibrationPreparation) : CalibrationPhase

    data object Executing : CalibrationPhase

    /** 执行完成（[recorded] = false 表示本地与交易所一致、未生成记录）。 */
    data class Done(val recorded: Boolean) : CalibrationPhase
}

/** 校准弹窗状态。 */
data class CalibrationUiState(val phase: CalibrationPhase)

/** 币种详情 UI 状态（M12 T12.1）。 */
data class CoinDetailUiState(
    val loading: Boolean = true,
    val fiat: String = "USD",
    val detail: CoinDetail? = null,
    val transactions: List<TransactionRow> = emptyList(),
    val exchangeFilter: String? = null,
    val sideFilter: Side? = null,
    val query: String = "",
    val calibration: CalibrationUiState? = null,
    val toast: WzToast? = null,
) {
    /** 交易记录中出现过的交易所（筛选下拉候选，去重保序）。 */
    val exchanges: List<String> get() = transactions.map { it.exchange }.distinct().sorted()
}

/**
 * 币种详情 VM（M12 T12.1 · ia.md §2.6）：
 * 汇总行与校准历史来自 [PortfolioService.coinDetail]；交易记录复用 M7 [TransactionLedgerService]
 * （卖出行已实现盈亏与交易管理页同口径，不另算一套）；校准走 M8 [CalibrationUseCase]（单一来源门 →
 * 实时余额 → 市价 → 差额规划 → 锚点入库 → 留痕），执行后重载汇总与校准历史。
 */
@Suppress("TooManyFunctions") // 页面动作面（加载/三重筛选/校准开启-执行-关闭/toast），各自独立且短小
class CoinDetailViewModel(
    private val cgId: String,
    private val portfolioService: PortfolioService,
    private val ledgerService: TransactionLedgerService,
    private val calibrationUseCase: CalibrationUseCase,
    private val catalog: CoinCatalog,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(CoinDetailUiState())
    val state: StateFlow<CoinDetailUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        scope.launch {
            try {
                val detail = portfolioService.coinDetail(cgId)
                val current = _state.value
                val rows = ledgerService.listTransactions(
                    TxFilter(
                        coinSymbol = detail.symbol,
                        exchange = current.exchangeFilter,
                        side = current.sideFilter,
                        query = current.query.ifBlank { null },
                    ),
                ).filter { it.touches(detail.symbol) }
                _state.update {
                    it.copy(
                        loading = false,
                        fiat = snapshotFiat(),
                        detail = detail,
                        transactions = rows,
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false) }
                toast(WzToastKind.Failure, t.message ?: portfolioStrings.loading)
            }
        }
    }

    private suspend fun snapshotFiat(): String =
        _state.value.fiat.takeIf { it.isNotBlank() } ?: runCatching { portfolioService.snapshot().fiat }
            .getOrDefault("USD")

    fun setExchangeFilter(exchange: String?) {
        _state.update { it.copy(exchangeFilter = exchange) }
        reload()
    }

    fun setSideFilter(side: Side?) {
        _state.update { it.copy(sideFilter = side) }
        reload()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        reload()
    }

    /** 校准被阻止的状态（原因由领域层类型化枚举映射为本地化文案）。 */
    private fun blockedState(reason: CalibrationBlockedException.Reason): CalibrationUiState =
        CalibrationUiState(CalibrationPhase.Blocked(ledgerStrings.calibrationBlocked(reason)))

    // ---- 校准 ----

    fun openCalibration() {
        _state.update { it.copy(calibration = CalibrationUiState(CalibrationPhase.Preparing)) }
        val detail = _state.value.detail ?: return
        scope.launch {
            try {
                val picked = catalog.getByCgId(cgId)?.id
                val prep = calibrationUseCase.prepare(detail.symbol, picked)
                _state.update { it.copy(calibration = CalibrationUiState(CalibrationPhase.Ready(prep))) }
            } catch (e: CalibrationBlockedException) {
                _state.update { current ->
                    current.copy(calibration = blockedState(e.reason))
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        calibration = CalibrationUiState(
                            CalibrationPhase.Blocked(t.message ?: portfolioStrings.calFailed),
                        ),
                    )
                }
            }
        }
    }

    fun executeCalibration() {
        val detail = _state.value.detail ?: return
        val phase = _state.value.calibration?.phase
        if (phase !is CalibrationPhase.Ready) return
        _state.update { it.copy(calibration = CalibrationUiState(CalibrationPhase.Executing)) }
        scope.launch {
            try {
                val picked = catalog.getByCgId(cgId)?.id
                val result = calibrationUseCase.execute(detail.symbol, picked)
                _state.update { it.copy(calibration = CalibrationUiState(CalibrationPhase.Done(result.recorded))) }
                toast(
                    WzToastKind.Success,
                    if (result.recorded) portfolioStrings.calDone else portfolioStrings.calNoDelta,
                )
                reload()
            } catch (e: CalibrationBlockedException) {
                _state.update { current ->
                    current.copy(calibration = blockedState(e.reason))
                }
            } catch (t: Throwable) {
                toast(WzToastKind.Failure, t.message ?: portfolioStrings.calFailed)
                _state.update { it.copy(calibration = null) }
            }
        }
    }

    fun closeCalibration() {
        _state.update { it.copy(calibration = null) }
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

    /**
     * 该交易是否真的涉及本币种（两腿精确匹配）。
     *
     * 必要性：M7 [TxFilter.coinSymbol] 的实现是 `pair.contains(symbol)` 子串匹配，BTC 会连带命中
     * WBTC/USDT 一类仅**包含**该符号的交易对，直接展示会让币种详情的交易列表多带无关行
     * （后端复核发现，登记 M12 模块记录 §5）。此处按列出的两腿符号精确过滤，不改 M7 既有口径。
     */
    private fun TransactionRow.touches(symbol: String): Boolean =
        baseSymbol.equals(symbol, ignoreCase = true) || quoteSymbol.equals(symbol, ignoreCase = true)
}
