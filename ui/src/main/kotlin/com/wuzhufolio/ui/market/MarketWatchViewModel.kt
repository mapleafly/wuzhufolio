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
import com.wuzhufolio.domain.market.AccountWatchSummary
import com.wuzhufolio.domain.market.WatchImportFailReason
import com.wuzhufolio.domain.market.WatchImportResult
import com.wuzhufolio.domain.market.WatchImportService
import com.wuzhufolio.domain.market.WatchQuoteRow
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
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
    // ---- B3（D48）：自选跨账户导入 ----
    /** 其它账户及其自选**只读**摘要（空 = 无其它账户 / 未登录 / 未注入服务 ⇒ 入口不渲染）。 */
    val importSources: List<AccountWatchSummary> = emptyList(),
    /** 导入弹层是否打开。 */
    val importOpen: Boolean = false,
    /** 已选择的来源账户 id（null = 尚未选择）。 */
    val importSourceId: Int? = null,
    /** true = 已进入**二次确认**步骤（确认前零服务调用）。 */
    val importConfirming: Boolean = false,
    /** 导入执行中（防重复点击）。 */
    val importBusy: Boolean = false,
) {
    /** 已选择的来源账户摘要（未选择 = null）。 */
    val importSelected: AccountWatchSummary? get() = importSources.firstOrNull { it.accountId == importSourceId }
}

/**
 * 行情页 VM（D21）：加载自选/默认种子 → 报价组装；搜索添加/移除（持久化自选）；手动刷新 +
 * 页面存活期按刷新频率自动轮询（M11 调度宿主接入前的最小页面内轮询，离开页面即停止）。
 *
 * B3（D48）追加：**自选跨账户显式导入**——只读摘要（[WatchImportService.listOtherAccounts]）+
 * 弹层状态（选择来源 → 二次确认）+ 结果 toast；**不注入 [importService] 时行为与改造前完全一致**。
 */
@Suppress("TooManyFunctions") // 行情页动作面（加载/搜索/增删/刷新×2/轮询/导入/toast），小而直白
class MarketWatchViewModel(
    private val watchService: MarketWatchService,
    private val quotesService: MarketQuotesService,
    private val refreshService: MarketRefreshService,
    private val settingsService: MarketSettingsService,
    /** B3（D48）：自选跨账户导入用例（可空——不注入 ⇒ 无导入入口，页面行为与改造前一致）。 */
    private val importService: WatchImportService? = null,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 在途搜索（DEF-30：输入变化/清空时取消，避免旧结果把候选浮层顶回来）。 */
    private var searchJob: Job? = null
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
            // B3（D48）：来源账户**只读**摘要——空列表（未登录 / 无其它账户 / 未注入）⇒ 入口不渲染
            val sources = runCatching { importService?.listOtherAccounts().orEmpty() }.getOrDefault(emptyList())
            _state.update {
                it.copy(fiat = fiat, defaultSeed = !hasCustom, rows = rows, importSources = sources)
            }
        }
    }

    // ---- B3（D48）：自选跨账户显式导入（选择来源 → 二次确认 → 执行） ----

    /** 打开导入弹层（回到第一步；来源摘要已在 [reloadAll] 取过，此处不重复查询）。 */
    fun openImport() {
        _state.update { it.copy(importOpen = true, importSourceId = null, importConfirming = false) }
    }

    /** 选择来源账户（仅记录选择，**不触发任何写入**）。 */
    fun selectImportSource(accountId: Int) {
        _state.update { it.copy(importSourceId = accountId, importConfirming = false) }
    }

    /** 进入**二次确认**步骤（仍未调用服务）。 */
    fun requestImportConfirm() {
        if (_state.value.importSelected != null) {
            _state.update { it.copy(importConfirming = true) }
        }
    }

    /** 返回第一步（取消二次确认）。 */
    fun backToImportPick() {
        _state.update { it.copy(importConfirming = false) }
    }

    /** 关闭导入弹层（**不执行任何写入**）。 */
    fun closeImport() {
        _state.update { it.copy(importOpen = false, importSourceId = null, importConfirming = false) }
    }

    /**
     * **执行导入**（唯一调用点；用户已在二次确认步骤点「确认导入」）。
     * 结果逐态映射 toast；成功/幂等后刷新列表（来源为空 / 失败不改动任何数据）。
     */
    fun confirmImport() {
        val service = importService
        val selected = _state.value.importSelected
        // 未注入服务 / 未选来源 / 已在执行 ⇒ 不动作（detekt ReturnCount ≤2：守卫合并为单出口）
        if (service == null || selected == null || _state.value.importBusy) return
        _state.update { it.copy(importBusy = true) }
        scope.launch {
            val outcome = runCatching { service.importFrom(selected.accountId) }
            _state.update { it.copy(importOpen = false, importSourceId = null, importConfirming = false) }
            outcome.fold(
                onSuccess = { result ->
                    toast(importToastKind(result), importToastText(result))
                    if (result is WatchImportResult.Imported) reloadAll()
                },
                // 服务契约要求返回类型化结果；万一上抛，按「未做改动」提示（不静默）
                onFailure = { toast(WzToastKind.Failure, MarketCopy.WATCH_IMPORT_FAILED) },
            )
            _state.update { it.copy(importBusy = false) }
        }
    }

    /** 导入结果的 toast 类型（导入成功 / 幂等 = 成功态；其余 = 失败态）。 */
    private fun importToastKind(result: WatchImportResult): WzToastKind = when (result) {
        is WatchImportResult.Imported, is WatchImportResult.AlreadyComplete -> WzToastKind.Success
        else -> WzToastKind.Failure
    }

    /** 导入结果的 toast 文案（逐态；见 interaction.md §1.9）。 */
    private fun importToastText(result: WatchImportResult): String = when (result) {
        is WatchImportResult.Imported ->
            if (result.skipped > 0) {
                MarketCopy.watchImportDoneSkipped(result.sourceUsername, result.added, result.skipped)
            } else {
                MarketCopy.watchImportDone(result.sourceUsername, result.added)
            }
        is WatchImportResult.AlreadyComplete -> MarketCopy.watchImportAlreadyComplete(result.sourceUsername)
        is WatchImportResult.NoSource -> MarketCopy.watchImportNoSource(result.sourceUsername)
        is WatchImportResult.Failed -> when (result.reason) {
            WatchImportFailReason.NO_SESSION -> MarketCopy.WATCH_IMPORT_LOGIN_REQUIRED
            WatchImportFailReason.SOURCE_NOT_FOUND -> MarketCopy.WATCH_IMPORT_SOURCE_GONE
            WatchImportFailReason.WRITE_FAILED -> MarketCopy.WATCH_IMPORT_FAILED
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        val q = query.trim()
        // DEF-30：每次输入都取消上一轮搜索——否则「清空输入后，先前在途的搜索结果回来又把候选浮层顶出来」
        // （人工症状：候选只有点「添加」才会消失，删光字母也不消失）
        searchJob?.cancel()
        searchJob = null
        if (q.isEmpty()) {
            _state.update { it.copy(candidates = emptyList(), searchBusy = false) }
            return
        }
        _state.update { it.copy(searchBusy = true) }
        searchJob = scope.launch {
            try {
                val found = watchService.searchCandidates(q, CANDIDATE_LIMIT)
                // 结果落地前复核输入仍是发起时的关键词（改词/清空后旧结果不得把候选浮层顶回来）
                if (_state.value.query.trim() == q) {
                    _state.update { it.copy(candidates = found, searchBusy = false) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // 取消失控不吞（结构化并发）
            } catch (t: Throwable) {
                // 搜索失败：不打断输入（候选留空），仅复位忙碌态；原因经 toast 提示
                if (_state.value.query.trim() == q) {
                    _state.update { it.copy(candidates = emptyList(), searchBusy = false) }
                }
                onSearchFailure(t)
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        _state.update { it.copy(query = "", candidates = emptyList(), searchBusy = false) }
    }

    fun addCoin(cgId: String) {
        scope.launch {
            runCatching { watchService.addCoin(cgId) }
                .onSuccess { clearSearch(); reloadAll() }
                .onFailure { error -> toast(WzToastKind.Failure, addFailureText(error)) }
        }
    }

    /** 搜索失败提示（interaction.md §1.1：请求失败即提示，不静默）。 */
    private fun onSearchFailure(error: Throwable) {
        toast(WzToastKind.Failure, error.message ?: MarketCopy.WATCH_NO_RESULT)
    }

    /**
     * 添加失败文案：异常文本或通用兜底。
     *
     * 注：**自选数量上限已取消**（D35，2026-09-24 人工口径），故「自选已满」分支随之移除；
     * 数据层不再因数量抛 [IllegalArgumentException]。
     */
    private fun addFailureText(error: Throwable): String = error.message ?: MarketCopy.ADD_FAILED

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
                toast(WzToastKind.Failure, t.message ?: MarketCopy.REFRESH_FAILED)
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
            PriceSource.COINMARKETCAP -> MarketCopy.TOAST_REFRESH_CMC
            else -> if (result.cgConfigured) {
                MarketCopy.TOAST_REFRESH_CG_KEYED
            } else {
                MarketCopy.TOAST_REFRESH_CG_KEYLESS
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

    private companion object {
        /** 候选浮层上限（走查反馈：原 8 条偏少且不可滚动；浮层可滚动，放宽到 20）。 */
        const val CANDIDATE_LIMIT: Int = 20
    }
}
