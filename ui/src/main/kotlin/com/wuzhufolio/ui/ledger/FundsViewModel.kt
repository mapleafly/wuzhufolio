package com.wuzhufolio.ui.ledger

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.FiatNormalizer
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.ledger.CalibrationBlockedException
import com.wuzhufolio.domain.ledger.CalibrationPreparation
import com.wuzhufolio.domain.ledger.CalibrationUseCase
import com.wuzhufolio.domain.ledger.CoinResolutionException
import com.wuzhufolio.domain.ledger.FiatValuePreview
import com.wuzhufolio.domain.ledger.FundDateRange
import com.wuzhufolio.domain.ledger.FundEntryRow
import com.wuzhufolio.domain.ledger.FundEntryType
import com.wuzhufolio.domain.ledger.FundInput
import com.wuzhufolio.domain.ledger.FundPage
import com.wuzhufolio.domain.ledger.FundService
import com.wuzhufolio.domain.ledger.LedgerValidationException
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 资金表单状态（新增/编辑共用；kind 编辑时锁定为原行类型；timeText 本地时区形态，保存转 UTC）。 */
data class FundFormState(
    val id: Long? = null,
    val kind: FlowKind = FlowKind.DEPOSIT,
    val coinSymbol: String = "",
    val quantity: String = "",
    val timeText: String = "",
    val sourceDest: String = "",
    val notes: String = "",
    val errors: Map<String, String> = emptyMap(),
    val formError: String? = null,
    val candidates: List<CatalogCoin> = emptyList(),
    /** 候选点选冻结（coins.id + 展示标签）——同名符号歧义下直达保存（M8 修复轮 §8-1）。 */
    val pickedCoinId: Long? = null,
    val pickedLabel: String? = null,
    val busy: Boolean = false,
    val fiatPreview: FiatValuePreview? = null,
    /** 法币输入实时提示（PRD §9.8「输入法币代码时提示改为记录兑换后到账的稳定币」）。 */
    val fiatHint: String? = null,
)

/** 校准弹窗状态（T8.2：预览 -> 确认执行；候选点选直达——M8 修复轮 §8-1）。 */
data class CalibrationUiState(
    val coinInput: String = "",
    val candidates: List<CatalogCoin> = emptyList(),
    val pickedCoinId: Long? = null,
    val pickedLabel: String? = null,
    val busy: Boolean = false,
    val executing: Boolean = false,
    val preparation: CalibrationPreparation? = null,
    val error: String? = null,
)

/** 资金管理页状态（M8 · T8.3）。 */
data class FundsUiState(
    val rows: List<FundEntryRow> = emptyList(),
    val overview: com.wuzhufolio.domain.ledger.FundsOverview? = null,
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val typeFilter: FundEntryType? = null,
    val dateRange: FundDateRange = FundDateRange.ALL,
    val form: FundFormState? = null,
    val deleteUuids: List<String> = emptyList(),
    val calibration: CalibrationUiState? = null,
    val busy: Boolean = false,
    val toast: WzToast? = null,
)

/**
 * 资金管理页 VM（M8）：列表/筛选/选择 -> 增资/撤资表单（目录补全 + 法币提示 + 折算预览 + V6/V7/V9）
 * -> 删除确认（重算投入本金与 ROI）-> 校准向导（单一来源门 + 预览 + 执行）。
 */
@Suppress("TooManyFunctions") // 页面动作面（列表/筛选/选择/表单/删除/校准/toast），小而直白
class FundsViewModel(
    private val service: FundService,
    private val calibration: CalibrationUseCase,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(FundsUiState())
    val state: StateFlow<FundsUiState> = _state.asStateFlow()

    init {
        reload()
    }

    // ---- 列表/筛选 ----

    fun reload() {
        scope.launch {
            val s = _state.value
            val page = runCatching {
                service.listFunds(
                    com.wuzhufolio.domain.ledger.FundFilter(
                        query = s.query.trim().takeIf { it.isNotEmpty() },
                        type = s.typeFilter,
                        dateRange = s.dateRange,
                    ),
                )
            }.getOrNull() ?: FundPage(emptyList(), null)
            _state.update { it.copy(rows = page.rows, overview = page.overview, busy = false) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        reload()
    }

    fun onTypeFilterChange(type: FundEntryType?) {
        _state.update { it.copy(typeFilter = type) }
        reload()
    }

    fun onDateRangeChange(range: FundDateRange) {
        _state.update { it.copy(dateRange = range) }
        reload()
    }

    fun toggleSelect(uuid: String) {
        _state.update { st ->
            val sel = st.selected.toMutableSet()
            if (!sel.add(uuid)) sel.remove(uuid)
            st.copy(selected = sel)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    // ---- 表单（增资/撤资） ----

    fun openAdd(kind: FlowKind) {
        _state.update {
            it.copy(
                form = FundFormState(
                    kind = kind,
                    timeText = LocalDateTime.now().format(FORM_LOCAL),
                ),
            )
        }
        scope.launch {
            val default = runCatching { service.defaultCoin() }.getOrNull()
            if (default != null) {
                patchForm { f ->
                    if (f.coinSymbol.isBlank()) {
                        f.copy(
                            coinSymbol = default.symbol,
                            pickedCoinId = default.id,
                            pickedLabel = pickedLabelOf(default),
                        )
                    } else {
                        f
                    }
                }
            }
        }
    }

    fun openEdit(uuid: String) {
        val row = _state.value.rows.firstOrNull { it.uuid == uuid } ?: return
        if (row.entryType == FundEntryType.RECONCILIATION) {
            toast(WzToastKind.Failure, FundsCopy.EDIT_SELECT_RECON)
            return
        }
        _state.update {
            it.copy(
                form = FundFormState(
                    id = row.id,
                    kind = row.kind ?: FlowKind.DEPOSIT,
                    coinSymbol = row.coinSymbol,
                    pickedCoinId = row.coinId,
                    pickedLabel = row.coinSymbol,
                    quantity = row.quantity.stripTrailingZeros().toPlainString(),
                    timeText = row.time.atZone(ZoneId.systemDefault()).format(FORM_LOCAL),
                    sourceDest = row.sourceDest ?: "",
                    notes = row.notes ?: "",
                    fiatPreview = FiatValuePreview(row.baseAmount, row.estimated),
                ),
            )
        }
    }

    fun requestEditSelected() {
        val selected = _state.value.selected
        if (selected.size != 1) {
            toast(WzToastKind.Failure, FundsCopy.EDIT_SELECT_ONE)
            return
        }
        openEdit(selected.first())
    }

    fun closeForm() {
        _state.update { it.copy(form = null) }
    }

    fun patchForm(transform: (FundFormState) -> FundFormState) {
        _state.update { st ->
            val form = st.form
            if (form == null) st else st.copy(form = transform(form))
        }
    }

    fun onCoinChange(value: String) {
        patchForm {
            it.copy(
                coinSymbol = value,
                candidates = emptyList(),
                pickedCoinId = null,
                pickedLabel = null,
                formError = null,
                fiatHint = fiatHintOf(value),
            )
        }
        refreshPreview()
        searchCoins(value)
    }

    fun onQuantityChange(value: String) {
        patchForm { it.copy(quantity = value, errors = it.errors - FundField.QTY.key, formError = null) }
        refreshPreview()
    }

    fun onFieldChange(field: FundField, value: String) {
        patchForm { f ->
            val cleared = f.copy(errors = f.errors - field.key, formError = null)
            when (field) {
                FundField.TIME -> cleared.copy(timeText = value)
                FundField.SOURCE_DEST -> cleared.copy(sourceDest = value)
                FundField.NOTES -> cleared.copy(notes = value)
                // 币种/数量走独立处理器（onCoinChange/onQuantityChange）
                FundField.COIN, FundField.QTY -> cleared
            }
        }
    }

    fun pickCandidate(coin: CatalogCoin) {
        patchForm {
            it.copy(
                coinSymbol = coin.symbol,
                pickedCoinId = coin.id,
                pickedLabel = pickedLabelOf(coin),
                candidates = emptyList(),
                fiatHint = null,
                errors = it.errors - FundField.COIN.key,
            )
        }
        refreshPreview()
    }

    /** 候选展示标签（含 cg_id——同名资产靠 CoinGecko id 区分，M8 修复轮 §8-1）。 */
    private fun pickedLabelOf(coin: CatalogCoin): String =
        coin.symbol + " · " + coin.name + "（" + coin.cgId + "）"

    private fun searchCoins(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            val found = runCatching { service.searchCoins(q) }.getOrDefault(emptyList())
            patchForm { it.copy(candidates = found) }
        }
    }

    private fun refreshPreview() {
        val form = _state.value.form ?: return
        val coin = form.coinSymbol.trim()
        val quantity = form.quantity.toBigDecimalOrNull()
        if (coin.isEmpty() || quantity == null || quantity.signum() <= 0) {
            patchForm { it.copy(fiatPreview = null) }
            return
        }
        val at = parseLocalTime(form.timeText) ?: Instant.now()
        scope.launch {
            val preview = runCatching { service.fiatValuePreview(coin, quantity, at, form.pickedCoinId) }.getOrNull()
            // 只把「当前仍在编辑同一币种/数量」的结果写回（避免慢响应错配）
            patchForm { f ->
                if (f.coinSymbol.trim().equals(coin, ignoreCase = true) && f.quantity == form.quantity) {
                    f.copy(fiatPreview = preview)
                } else {
                    f
                }
            }
        }
    }

    /**
     * 保存/更新（V6 表单校验 + V7/V9 服务校验；违例文案映射）。
     * 失败反馈双通道：内联错误 + toast（与 M7 交易表单同口径）。
     */
    @Suppress("ReturnCount") // 表单/占用/校验/数值/时间五段早退（与 M7 保存同构）
    fun saveForm() {
        val form = _state.value.form ?: return
        if (form.busy) return
        val errors = validateForm(form)
        if (errors.isNotEmpty()) {
            patchForm { it.copy(errors = errors) }
            toast(WzToastKind.Failure, FundsCopy.VALIDATION_FAILED)
            return
        }
        val quantity = form.quantity.toBigDecimalOrNull() ?: return
        val time = parseLocalTime(form.timeText)
        if (time == null) {
            patchForm { it.copy(errors = it.errors + (FundField.TIME.key to FundsCopy.V6_TIME_REQUIRED)) }
            toast(WzToastKind.Failure, FundsCopy.V6_TIME_REQUIRED)
            return
        }
        val input = FundInput(
            kind = form.kind,
            coinSymbol = form.coinSymbol,
            pickedCoinId = form.pickedCoinId,
            quantity = quantity,
            time = time,
            sourceDest = form.sourceDest.trim().takeIf { it.isNotEmpty() },
            notes = form.notes.trim().takeIf { it.isNotEmpty() },
        )
        patchForm { it.copy(busy = true) }
        scope.launch {
            val result = if (form.id == null) {
                runCatching { service.saveFund(input) }
            } else {
                runCatching { service.updateFund(form.id, input) }
            }
            result.onSuccess {
                _state.update { st ->
                    st.copy(
                        form = null,
                        busy = false,
                        toast = WzToast(
                            WzToastKind.Success,
                            if (form.id == null) FundsCopy.SAVE_SUCCESS else FundsCopy.UPDATE_SUCCESS,
                        ),
                    )
                }
                reload()
            }.onFailure { t ->
                val message = when (t) {
                    is LedgerValidationException -> FundsCopy.validationCopy(t)
                    is CoinResolutionException -> t.message ?: FundsCopy.SAVE_FAILED
                    else -> t.message ?: FundsCopy.SAVE_FAILED
                }
                patchForm { f -> f.copy(busy = false, formError = message) }
                toast(WzToastKind.Failure, message)
            }
        }
    }

    // ---- 删除（PRD 故事 6.3：删除需二次确认并提示重算投入本金与 ROI） ----

    fun requestDeleteSelected() {
        val selected = _state.value.selected.toList()
        if (selected.isEmpty()) {
            toast(WzToastKind.Failure, FundsCopy.DELETE_SELECT_HINT)
            return
        }
        _state.update { it.copy(deleteUuids = selected) }
    }

    fun requestDeleteRow(uuid: String) {
        _state.update { it.copy(deleteUuids = listOf(uuid)) }
    }

    fun cancelDelete() {
        _state.update { it.copy(deleteUuids = emptyList()) }
    }

    fun confirmDelete() {
        val uuids = _state.value.deleteUuids
        if (uuids.isEmpty()) return
        scope.launch {
            runCatching { service.deleteFunds(uuids) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            deleteUuids = emptyList(),
                            selected = it.selected - uuids.toSet(),
                            toast = WzToast(WzToastKind.Success, FundsCopy.DELETE_SUCCESS),
                        )
                    }
                    reload()
                }
                .onFailure { t ->
                    val message = when (t) {
                        is LedgerValidationException -> FundsCopy.validationCopy(t)
                        else -> FundsCopy.DELETE_FAILED + (t.message ?: "")
                    }
                    _state.update {
                        it.copy(deleteUuids = emptyList(), toast = WzToast(WzToastKind.Failure, message))
                    }
                }
        }
    }

    // ---- 校准（T8.2） ----

    fun openCalibration() {
        _state.update { it.copy(calibration = CalibrationUiState()) }
    }

    fun closeCalibration() {
        _state.update { it.copy(calibration = null) }
    }

    fun onCalibrationCoinChange(value: String) {
        _state.update { st ->
            st.copy(
                calibration = st.calibration?.copy(
                    coinInput = value,
                    candidates = emptyList(),
                    pickedCoinId = null,
                    pickedLabel = null,
                    preparation = null,
                    error = null,
                ),
            )
        }
        val q = value.trim()
        if (q.isNotEmpty()) {
            scope.launch {
                val found = runCatching { service.searchCoins(q) }.getOrDefault(emptyList())
                _state.update { st ->
                    st.copy(calibration = st.calibration?.copy(candidates = found))
                }
            }
        }
    }

    fun pickCalibrationCandidate(coin: CatalogCoin) {
        _state.update { st ->
            st.copy(
                calibration = st.calibration?.copy(
                    coinInput = coin.symbol,
                    candidates = emptyList(),
                    pickedCoinId = coin.id,
                    pickedLabel = coin.symbol + " · " + coin.name + "（" + coin.cgId + "）",
                    error = null,
                ),
            )
        }
    }

    /** 生成校准预览（单一来源门/密钥/市价任一前提不满足 -> 错误文案）。 */
    fun prepareCalibration() {
        val cal = _state.value.calibration ?: return
        val coin = cal.coinInput.trim()
        if (coin.isEmpty() || cal.busy) return
        _state.update { st -> st.copy(calibration = st.calibration?.copy(busy = true, error = null)) }
        scope.launch {
            runCatching { calibration.prepare(coin, cal.pickedCoinId) }
                .onSuccess { prep ->
                    _state.update { st ->
                        st.copy(calibration = st.calibration?.copy(busy = false, preparation = prep))
                    }
                }
                .onFailure { t ->
                    _state.update { st ->
                        st.copy(
                            calibration = st.calibration?.copy(busy = false, error = calibrationErrorCopy(t)),
                        )
                    }
                }
        }
    }

    /** 执行校准（锚点入库 + 同步日志留痕；其后记录冲突 -> V9 文案）。 */
    fun executeCalibration() {
        val cal = _state.value.calibration ?: return
        val coin = cal.coinInput.trim()
        if (coin.isEmpty() || cal.executing) return
        _state.update { st -> st.copy(calibration = st.calibration?.copy(executing = true, error = null)) }
        scope.launch {
            runCatching { calibration.execute(coin, cal.pickedCoinId) }
                .onSuccess { result ->
                    val message = if (result.recorded) {
                        FundsCopy.CAL_SUCCESS
                    } else {
                        FundsCopy.CAL_NO_DIFF
                    }
                    _state.update { st ->
                        st.copy(
                            calibration = null,
                            toast = WzToast(WzToastKind.Success, message),
                        )
                    }
                    reload()
                }
                .onFailure { t ->
                    _state.update { st ->
                        st.copy(
                            calibration = st.calibration?.copy(executing = false, error = calibrationErrorCopy(t)),
                        )
                    }
                }
        }
    }

    private fun calibrationErrorCopy(t: Throwable): String = when (t) {
        is CalibrationBlockedException -> t.message
        is CoinResolutionException -> FundsCopy.coinResolutionCopy(t)
        is LedgerValidationException -> FundsCopy.validationCopy(t)
        else -> t.message ?: "校准失败"
    }

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun toast(kind: WzToastKind, message: String) {
        _state.update { it.copy(toast = WzToast(kind, message)) }
    }

    // ---- 内部 ----

    private fun validateForm(form: FundFormState): Map<String, String> {
        val errors = LinkedHashMap<String, String>()
        if (form.coinSymbol.isBlank()) errors[FundField.COIN.key] = FundsCopy.V6_COIN_REQUIRED
        val qty = form.quantity.toBigDecimalOrNull()
        if (qty == null || qty.signum() <= 0) errors[FundField.QTY.key] = FundsCopy.V6_QTY
        if (form.timeText.isBlank()) errors[FundField.TIME.key] = FundsCopy.V6_TIME_REQUIRED
        return errors
    }

    /** 法币输入实时提示（FiatNormalizer 与账本交易半边共用同一张孪生映射表——法币方案决策 D.2）。 */
    private fun fiatHintOf(value: String): String? = when (val cls = FiatNormalizer().classify(value.trim())) {
        is FiatNormalizer.Classification.TwinMapped ->
            FundsCopy.FIAT_INPUT_HINT_PREFIX + "（" + cls.fiatCode + " → " + cls.stableSymbol + "）"
        is FiatNormalizer.Classification.ThirdCurrencyFiat -> FundsCopy.FIAT_INPUT_HINT_PREFIX
        FiatNormalizer.Classification.NotFiat -> null
    }

    private fun parseLocalTime(text: String): Instant? {
        if (text.isBlank()) return null
        return runCatching {
            LocalDateTime.parse(text, FORM_LOCAL).atZone(ZoneId.systemDefault()).toInstant()
        }.getOrNull() ?: runCatching {
            LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant()
        }.getOrNull()
    }

    override fun onCleared() {
        scope.cancel()
    }

    /** 页面卸载显式释放（Compose remember VM 无宿主时调用）。 */
    fun dispose() {
        scope.cancel()
    }

    companion object {
        private val FORM_LOCAL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    }
}

/** 资金表单字段键（校验错误定位）。 */
enum class FundField(val key: String) {
    COIN("coin"),
    QTY("quantity"),
    TIME("time"),
    SOURCE_DEST("source_dest"),
    NOTES("notes"),
}
