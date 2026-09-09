package com.wuzhufolio.ui.ledger

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.CoinResolutionException
import com.wuzhufolio.domain.ledger.CsvPreview
import com.wuzhufolio.domain.ledger.FeeQuoteRequest
import com.wuzhufolio.domain.ledger.LedgerValidationException
import com.wuzhufolio.domain.ledger.TransactionInput
import com.wuzhufolio.domain.ledger.TransactionLedgerService
import com.wuzhufolio.domain.ledger.TransactionRow
import com.wuzhufolio.domain.ledger.TxFilter
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
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
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 手续费币种三态（PRD §9.7：默认计价币种，可选基础币种或自定义）。 */
enum class FeeRole { QUOTE, BASE, CUSTOM }

/** 交易表单状态（新增/编辑共用；timeText 本地时区形态，保存转 UTC——PRD 时间与时区规则）。 */
data class TxFormState(
    val id: Long? = null,
    val exchange: String = "BINANCE",
    val baseSymbol: String = "",
    val quoteSymbol: String = "",
    val side: Side = Side.BUY,
    val price: String = "",
    val quantity: String = "",
    val fee: String = "",
    val feeRole: FeeRole = FeeRole.QUOTE,
    val feeCustom: String = "",
    val timeText: String = "",
    val notes: String = "",
    val errors: Map<String, String> = emptyMap(),
    val formError: String? = null,
    val baseCandidates: List<CatalogCoin> = emptyList(),
    val quoteCandidates: List<CatalogCoin> = emptyList(),
    val feeCandidates: List<CatalogCoin> = emptyList(),
    val busy: Boolean = false,
    val total: String = "0.00",
) {
    /** 手续费币种符号（保存输入；CUSTOM 用自定义输入）。 */
    val feeSymbol: String
        get() = when (feeRole) {
            FeeRole.QUOTE -> quoteSymbol.trim()
            FeeRole.BASE -> baseSymbol.trim()
            FeeRole.CUSTOM -> feeCustom.trim()
        }
}

/** CSV 导入向导状态（T7.3）。 */
data class CsvUiState(
    val fileName: String? = null,
    val preview: CsvPreview? = null,
    /** 用户勾选要导入的疑似重复行（rowKey 集）。 */
    val includeKeys: Set<String> = emptySet(),
    /** 歧义 ticker 选择（key = "EXCHANGE|ASSET" -> 候选 cg_id）。 */
    val choices: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    val importing: Boolean = false,
)

/** 交易管理页状态（T7.4）。 */
data class TxUiState(
    val rows: List<TransactionRow> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val query: String = "",
    val sideFilter: Side? = null,
    val form: TxFormState? = null,
    val csv: CsvUiState? = null,
    /** 待确认删除的行 id 集（删除确认框）。 */
    val deleteIds: List<Long> = emptyList(),
    val busy: Boolean = false,
    val toast: WzToast? = null,
)

/**
 * 交易管理页 VM（M7 · T7.1–T7.4）：列表/筛选/选择 -> 表单（实时总价、手续费三态、自动计算、
 * 重放校验违例映射 V1–V5/V9）-> CSV 导入向导（模板下载、解析预览、消歧选择、确认导入）。
 * 文件选择（CSV 上传/模板保存）经注入 lambda（Main 接线 AWT FileDialog；测试用 stub）。
 */
@Suppress("TooManyFunctions") // 页面动作面（列表/筛选/选择/表单×N/CSV×N/toast），小而直白
class TransactionsViewModel(
    private val service: TransactionLedgerService,
    private val pickCsvFile: () -> String?,
    private val pickTemplatePath: () -> String?,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(TxUiState())
    val state: StateFlow<TxUiState> = _state.asStateFlow()

    init {
        reload()
    }

    // ---- 列表/筛选 ----

    fun reload() {
        scope.launch {
            val s = _state.value
            val rows = runCatching {
                service.listTransactions(
                    TxFilter(
                        query = s.query.trim().takeIf { it.isNotEmpty() },
                        side = s.sideFilter,
                    ),
                )
            }.getOrDefault(emptyList())
            _state.update { it.copy(rows = rows, busy = false) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        reload()
    }

    fun onSideFilterChange(side: Side?) {
        _state.update { it.copy(sideFilter = side) }
        reload()
    }

    fun toggleSelect(id: Long) {
        _state.update { st ->
            val sel = st.selected.toMutableSet()
            if (!sel.add(id)) sel.remove(id)
            st.copy(selected = sel)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    // ---- 表单 ----

    fun openAdd() {
        val now = LocalDateTime.now()
        _state.update {
            it.copy(
                form = TxFormState(
                    timeText = now.format(FORM_LOCAL),
                    exchange = "BINANCE",
                ),
            )
        }
    }

    fun openEdit(id: Long) {
        val row = _state.value.rows.firstOrNull { it.id == id } ?: return
        val feeCurrency = row.feeCurrency
        _state.update {
            it.copy(
                form = TxFormState(
                    id = row.id,
                    exchange = row.exchange,
                    baseSymbol = row.baseSymbol,
                    quoteSymbol = row.quoteSymbol,
                    side = row.side,
                    price = row.price.stripTrailingZeros().toPlainString(),
                    quantity = row.quantity.stripTrailingZeros().toPlainString(),
                    fee = row.fee.stripTrailingZeros().toPlainString(),
                    feeRole = feeRoleOf(row),
                    feeCustom = if (feeCurrency != null &&
                        feeCurrency != row.baseSymbol && feeCurrency != row.quoteSymbol
                    ) feeCurrency else "",
                    timeText = row.time.atZone(ZoneId.systemDefault()).format(FORM_LOCAL),
                    notes = row.notes ?: "",
                    total = row.total.stripTrailingZeros().toPlainString(),
                ),
            )
        }
    }

    private fun feeRoleOf(row: TransactionRow): FeeRole = when {
        row.feeCurrency != null && row.feeCurrency.equals(row.quoteSymbol, ignoreCase = true) -> FeeRole.QUOTE
        row.feeCurrency != null && row.feeCurrency.equals(row.baseSymbol, ignoreCase = true) -> FeeRole.BASE
        else -> FeeRole.CUSTOM
    }

    fun closeForm() {
        _state.update { it.copy(form = null) }
    }

    fun patchForm(transform: (TxFormState) -> TxFormState) {
        _state.update { st ->
            val form = st.form
            if (form == null) st else st.copy(form = transform(form))
        }
    }

    fun onBaseSymbolChange(value: String) {
        patchForm {
            it.copy(baseSymbol = value, baseCandidates = emptyList(), formError = null)
        }
        searchPair(value)
    }

    fun onQuoteSymbolChange(value: String) {
        patchForm { it.copy(quoteSymbol = value, quoteCandidates = emptyList(), formError = null) }
        searchQuote(value)
    }

    fun onFeeCustomChange(value: String) {
        patchForm { it.copy(feeCustom = value, feeCandidates = emptyList()) }
        searchFee(value)
    }

    /** 交易对基础币自动补全（PRD §9.7：基于币种目录自动补全）。 */
    fun searchPair(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            val found = runCatching { service.searchCoins(q) }.getOrDefault(emptyList())
            patchForm { it.copy(baseCandidates = found) }
        }
    }

    fun searchFee(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            val found = runCatching { service.searchCoins(q) }.getOrDefault(emptyList())
            patchForm { it.copy(feeCandidates = found) }
        }
    }

    fun pickBaseCandidate(coin: CatalogCoin) {
        patchForm {
            it.copy(
                baseSymbol = coin.symbol,
                baseCandidates = emptyList(),
                quoteSymbol = it.quoteSymbol.ifBlank { "USDT" },
            )
        }
    }

    /** 计价币目录检索（与基础币同口径：输入即出候选，点击选中——GUI 走查修复轮）。 */
    fun searchQuote(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            val found = runCatching { service.searchCoins(q) }.getOrDefault(emptyList())
            patchForm { it.copy(quoteCandidates = found) }
        }
    }

    fun pickQuoteCandidate(coin: CatalogCoin) {
        patchForm { it.copy(quoteSymbol = coin.symbol, quoteCandidates = emptyList()) }
    }

    fun pickFeeCandidate(coin: CatalogCoin) {
        patchForm { it.copy(feeCustom = coin.symbol, feeCandidates = emptyList()) }
    }

    fun setSide(side: Side) {
        patchForm { it.copy(side = side) }
    }

    fun setFeeRole(role: FeeRole) {
        patchForm { it.copy(feeRole = role) }
    }

    fun onFieldChange(field: TxField, value: String) {
        patchForm { f ->
            val cleared = f.copy(errors = f.errors - field.key, formError = null)
            when (field) {
                TxField.PRICE -> cleared.copy(price = value, total = computeTotal(value, cleared.quantity))
                TxField.QUANTITY -> cleared.copy(quantity = value, total = computeTotal(cleared.price, value))
                TxField.FEE -> cleared.copy(fee = value)
                TxField.TIME -> cleared.copy(timeText = value)
                TxField.NOTES -> cleared.copy(notes = value)
                TxField.EXCHANGE -> cleared.copy(exchange = value)
                // 交易对/自定义手续费币种走独立处理器（onBaseSymbolChange/onFeeCustomChange）
                TxField.PAIR, TxField.FEE_CUSTOM -> cleared
            }
        }
    }

    /** 自动计算手续费（T7.2）：交易所 > 全局费率 + 三币种基数（FeeCalculator）。 */
    @Suppress("ReturnCount") // 价格/数量/手续费币种三前置早退
    fun autoCalcFee() {
        val form = _state.value.form ?: return
        val price = form.price.toBigDecimalOrNull()
        val quantity = form.quantity.toBigDecimalOrNull()
        if (price == null || price.signum() <= 0) {
            toast(WzToastKind.Failure, TransactionCopy.V1_PRICE)
            return
        }
        if (quantity == null || quantity.signum() <= 0) {
            toast(WzToastKind.Failure, TransactionCopy.V1_QTY)
            return
        }
        if (form.feeSymbol.isBlank()) {
            toast(WzToastKind.Failure, TransactionCopy.V3_FEE_CURRENCY)
            return
        }
        scope.launch {
            val result = runCatching {
                service.feeQuote(
                    FeeQuoteRequest(
                        exchange = form.exchange,
                        side = form.side,
                        quantity = quantity,
                        price = price,
                        feeCoinSymbol = form.feeSymbol,
                        baseSymbol = form.baseSymbol,
                        quoteSymbol = form.quoteSymbol,
                    ),
                )
            }
            when (val r = result.getOrNull()) {
                null -> when {
                    result.exceptionOrNull() is CoinResolutionException -> toast(
                        WzToastKind.Failure,
                        result.exceptionOrNull()?.message ?: TransactionCopy.FEE_CALC_FAILED,
                    )
                    else -> toast(WzToastKind.Failure, TransactionCopy.FEE_NO_RULE)
                }
                else -> patchForm {
                    it.copy(fee = r.coinQty.stripTrailingZeros().toPlainString())
                }.also {
                    toast(
                        WzToastKind.Success,
                        "已按 " + form.exchange.uppercase() + " " +
                            (if (form.side == Side.BUY) "买入" else "卖出") +
                            " 费率 " + r.ratePercent.stripTrailingZeros().toPlainString() +
                            "% 计算：" + r.coinQty.stripTrailingZeros().toPlainString() + " " + form.feeSymbol.uppercase(),
                    )
                }
            }
        }
    }

    /**
     * 保存/更新（表单校验 V1–V4 + 重放校验 V5/V9；违例文案映射）。
     * 失败反馈双通道（GUI 走查修复轮）：内联错误 + toast——表单在窗口较小时可能滚动，
     * 内联错误可能不在可视区，toast 保证「点了保存没反应」不再发生。
     */
    @Suppress("ReturnCount") // 表单/占用/构建输入三前置早退
    fun saveForm() {
        val form = _state.value.form ?: return
        if (form.busy) return
        val input = buildInput(form)
        if (input == null) {
            toast(WzToastKind.Failure, TransactionCopy.VALIDATION_FAILED)
            return
        }
        patchForm { it.copy(busy = true) }
        scope.launch {
            val result = if (form.id == null) {
                runCatching { service.saveTransaction(input) }
            } else {
                runCatching { service.updateTransaction(form.id, input) }
            }
            result.onSuccess {
                _state.update { st ->
                    st.copy(
                        form = null,
                        busy = false,
                        toast = WzToast(
                            WzToastKind.Success,
                            if (form.id == null) TransactionCopy.SAVE_SUCCESS else TransactionCopy.UPDATE_SUCCESS,
                        ),
                    )
                }
                reload()
            }.onFailure { t ->
                val message = when (t) {
                    is LedgerValidationException -> TransactionCopy.validationCopy(t)
                    is CoinResolutionException -> t.message ?: TransactionCopy.SAVE_FAILED
                    else -> t.message ?: TransactionCopy.SAVE_FAILED
                }
                patchForm { f -> f.copy(busy = false, formError = message) }
                toast(WzToastKind.Failure, message)
            }
        }
    }

    // ---- 删除（PRD §9.6 多选删除 + 确认框） ----

    fun requestEditSelected() {
        val selected = _state.value.selected
        if (selected.size != 1) {
            toast(WzToastKind.Failure, TransactionCopy.EDIT_SELECT_ONE)
            return
        }
        openEdit(selected.first())
    }

    fun requestDeleteSelected() {
        val selected = _state.value.selected.toList()
        if (selected.isEmpty()) {
            toast(WzToastKind.Failure, TransactionCopy.DELETE_SELECT_HINT)
            return
        }
        _state.update { it.copy(deleteIds = selected) }
    }

    fun requestDeleteRow(id: Long) {
        _state.update { it.copy(deleteIds = listOf(id)) }
    }

    fun cancelDelete() {
        _state.update { it.copy(deleteIds = emptyList()) }
    }

    fun confirmDelete() {
        val ids = _state.value.deleteIds
        if (ids.isEmpty()) return
        scope.launch {
            runCatching { service.deleteTransactions(ids) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            deleteIds = emptyList(),
                            selected = it.selected - ids.toSet(),
                            toast = WzToast(WzToastKind.Success, TransactionCopy.DELETE_SUCCESS),
                        )
                    }
                    reload()
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(
                            deleteIds = emptyList(),
                            toast = WzToast(
                                WzToastKind.Failure,
                                (t as? LedgerValidationException)?.let { e ->
                                    TransactionCopy.validationCopy(e)
                                } ?: t.message ?: "删除失败",
                            ),
                        )
                    }
                }
        }
    }

    // ---- CSV 导入向导（T7.3） ----

    fun openCsv() {
        _state.update { it.copy(csv = CsvUiState()) }
    }

    fun closeCsv() {
        _state.update { it.copy(csv = null) }
    }

    /** 选择文件并解析（文件选择器须在协程内调用：AWT FileDialog 需 EDT，且不得阻塞 UI 线程）。 */
    fun pickAndParseCsv() {
        if (_state.value.csv?.busy == true) return
        _state.update { st -> st.copy(csv = st.csv?.copy(busy = true)) }
        scope.launch {
            val path = runCatching { pickCsvFile() }.getOrNull()
            if (path == null) {
                _state.update { st -> st.copy(csv = st.csv?.copy(busy = false)) }
                return@launch
            }
            _state.update { st ->
                st.copy(csv = st.csv?.copy(fileName = Path.of(path).fileName.toString(), busy = true))
            }
            runCatching { service.parseCsv(Files.readAllBytes(Path.of(path))) }
                .onSuccess { preview ->
                    _state.update { st -> st.copy(csv = st.csv?.copy(preview = preview, busy = false)) }
                }
                .onFailure { t ->
                    _state.update { st ->
                        st.copy(
                            csv = st.csv?.copy(busy = false),
                            toast = WzToast(WzToastKind.Failure, "CSV 解析失败：" + (t.message ?: "")),
                        )
                    }
                }
        }
    }

    /** 下载标准模板（文件选择器须在协程内调用：AWT FileDialog 需 EDT）。 */
    fun downloadTemplate() {
        scope.launch {
            val path = runCatching { pickTemplatePath() }.getOrNull()
            if (path == null) return@launch
            runCatching {
                Files.writeString(Path.of(path), service.csvTemplateCsv())
            }.onSuccess {
                toast(WzToastKind.Success, "标准模板已下载：" + Path.of(path).fileName)
            }.onFailure { t ->
                toast(WzToastKind.Failure, "模板下载失败：" + (t.message ?: ""))
            }
        }
    }

    fun toggleInclude(rowKey: String) {
        _state.update { st ->
            val csv = st.csv ?: return@update st
            val set = csv.includeKeys.toMutableSet()
            if (!set.add(rowKey)) set.remove(rowKey)
            st.copy(csv = csv.copy(includeKeys = set))
        }
    }

    fun setAmbiguityChoice(key: String, cgId: String) {
        _state.update { st ->
            val csv = st.csv ?: return@update st
            st.copy(csv = csv.copy(choices = csv.choices + (key to cgId)))
        }
    }

    @Suppress("ReturnCount") // 会话/预览/占用三前置早退
    fun confirmImport() {
        val csv = _state.value.csv ?: return
        val preview = csv.preview ?: return
        if (csv.importing) return
        _state.update { st -> st.copy(csv = st.csv?.copy(importing = true)) }
        scope.launch {
            runCatching { service.confirmCsvImport(preview.sessionId, csv.choices, csv.includeKeys) }
                .onSuccess { summary ->
                    val message = buildString {
                        append("导入完成 · 新增 ").append(summary.imported)
                        if (summary.duplicatesSkipped > 0) append(" · 跳过重复 ").append(summary.duplicatesSkipped)
                        if (summary.unresolvedSkipped > 0) append(" · 未解析跳过 ").append(summary.unresolvedSkipped)
                    }
                    val toastMsg = if (summary.anomalousCoins.isNotEmpty()) {
                        message + " · 持仓异常币种：" + summary.anomalousCoins.joinToString("、") + "（请补录增资或校准）"
                    } else {
                        message
                    }
                    _state.update { st ->
                        st.copy(csv = null, toast = WzToast(WzToastKind.Success, toastMsg))
                    }
                    reload()
                }
                .onFailure { t ->
                    _state.update { st ->
                        st.copy(
                            csv = st.csv?.copy(importing = false),
                            toast = WzToast(WzToastKind.Failure, "导入失败：" + (t.message ?: "")),
                        )
                    }
                }
        }
    }

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun toast(kind: WzToastKind, message: String) {
        _state.update { it.copy(toast = WzToast(kind, message)) }
    }

    // ---- 内部 ----

    /** 构建保存输入（V1–V4 表单校验 + 时间解析；失败置位并返回 null）。 */
    @Suppress("ReturnCount") // 校验/数值/时间三段早退（null = 校验失败已置位）
    private fun buildInput(form: TxFormState): TransactionInput? {
        val errors = validateForm(form)
        if (errors.isNotEmpty()) {
            patchForm { it.copy(errors = errors) }
            return null
        }
        val price = form.price.toBigDecimalOrNull()
        val quantity = form.quantity.toBigDecimalOrNull()
        if (price == null || quantity == null) return null
        val fee = form.fee.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val time = parseLocalTime(form.timeText)
        if (time == null) {
            patchForm { it.copy(errors = it.errors + (TxField.TIME.key to TransactionCopy.V4_REQUIRED)) }
            return null
        }
        return TransactionInput(
            exchange = form.exchange,
            baseSymbol = form.baseSymbol,
            quoteSymbol = form.quoteSymbol,
            side = form.side,
            price = price,
            quantity = quantity,
            fee = fee,
            feeCoinSymbol = form.feeSymbol,
            time = time,
            notes = form.notes,
        )
    }

    private fun validateForm(form: TxFormState): Map<String, String> {
        val errors = LinkedHashMap<String, String>()
        if (form.baseSymbol.isBlank() || form.quoteSymbol.isBlank()) {
            errors[TxField.PAIR.key] = TransactionCopy.V4_REQUIRED
        }
        val price = form.price.toBigDecimalOrNull()
        if (price == null || price.signum() <= 0) errors[TxField.PRICE.key] = TransactionCopy.V1_PRICE
        val qty = form.quantity.toBigDecimalOrNull()
        if (qty == null || qty.signum() <= 0) errors[TxField.QUANTITY.key] = TransactionCopy.V1_QTY
        val fee = form.fee.toBigDecimalOrNull()
        if (form.fee.isNotBlank() && (fee == null || fee.signum() < 0)) {
            errors[TxField.FEE.key] = TransactionCopy.V2_FEE
        }
        if (form.feeRole == FeeRole.CUSTOM && form.feeCustom.isBlank()) {
            errors[TxField.FEE_CUSTOM.key] = TransactionCopy.V3_FEE_CURRENCY
        }
        return errors
    }

    private fun computeTotal(price: String, qty: String): String {
        val p = price.toBigDecimalOrNull()
        val q = qty.toBigDecimalOrNull()
        return if (p == null || q == null) {
            "0.00"
        } else {
            p.multiply(q).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
        }
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

/** 表单字段键（校验错误定位）。 */
enum class TxField(val key: String) {
    PAIR("pair"),
    PRICE("price"),
    QUANTITY("quantity"),
    FEE("fee"),
    FEE_CUSTOM("fee_custom"),
    TIME("time"),
    NOTES("notes"),
    EXCHANGE("exchange"),
}
