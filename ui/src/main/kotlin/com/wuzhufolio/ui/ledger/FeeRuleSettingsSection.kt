package com.wuzhufolio.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.ledger.FeeRuleRow
import com.wuzhufolio.domain.ledger.FeeRulePolicy
import com.wuzhufolio.domain.ledger.FeeRuleService
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.components.WzToastKind
import com.wuzhufolio.ui.theme.WzTheme
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

/** 费率设置状态（M10 T10.1 完整 CRUD：全局 + 交易所规则增删改；嵌入完整设置页「手续费」分组）。 */
data class FeeRuleUiState(
    val rules: List<FeeRuleRow> = emptyList(),
    val globalBuy: String = "",
    val globalSell: String = "",
    val exchangeName: String = "",
    val exchangeBuy: String = "",
    val exchangeSell: String = "",
    /** 编辑态：非空 = 正在编辑该行（表单预填；交易所名可改 = 键迁移，见 FeeRuleService.saveExchangeEdit）。 */
    val editingRuleId: Long? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val toast: WzToast? = null,
)

/**
 * 费率设置 VM（M7 最小 CRUD → M10 T10.1 扩展）：全局默认费率 + 交易所费率增/改/删。
 * 表单「自动计算手续费」按 交易所 > 全局 匹配（FeeRateResolver）。
 */
@Suppress("TooManyFunctions") // 页面动作面（加载/五个输入/两个保存/编辑/删除/toast）固有
class FeeRuleSettingsViewModel(private val service: FeeRuleService) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(FeeRuleUiState())
    val state: StateFlow<FeeRuleUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        scope.launch {
            val rules = runCatching { service.listRules() }.getOrDefault(emptyList())
            val global = rules.firstOrNull { it.exchange == null }
            _state.update {
                it.copy(
                    rules = rules,
                    globalBuy = global?.buyPercent?.stripTrailingZeros()?.toPlainString() ?: it.globalBuy,
                    globalSell = global?.sellPercent?.stripTrailingZeros()?.toPlainString() ?: it.globalSell,
                )
            }
        }
    }

    fun onGlobalBuy(value: String) = _state.update { it.copy(globalBuy = value, error = null) }
    fun onGlobalSell(value: String) = _state.update { it.copy(globalSell = value, error = null) }
    fun onExchangeName(value: String) = _state.update { it.copy(exchangeName = value, error = null) }
    fun onExchangeBuy(value: String) = _state.update { it.copy(exchangeBuy = value, error = null) }
    fun onExchangeSell(value: String) = _state.update { it.copy(exchangeSell = value, error = null) }

    /** 进入编辑态（表单预填该行）。 */
    fun beginEdit(rule: FeeRuleRow) {
        _state.update {
            it.copy(
                editingRuleId = rule.id,
                exchangeName = rule.exchange ?: "",
                exchangeBuy = rule.buyPercent.stripTrailingZeros().toPlainString(),
                exchangeSell = rule.sellPercent.stripTrailingZeros().toPlainString(),
                error = null,
            )
        }
    }

    fun cancelEdit() {
        _state.update {
            it.copy(
                editingRuleId = null,
                exchangeName = "",
                exchangeBuy = "",
                exchangeSell = "",
                error = null,
            )
        }
    }

    @Suppress("ReturnCount") // 两个费率输入的校验早退
    fun saveGlobal() {
        val buyInput = _state.value.globalBuy.trim().toBigDecimalOrNull()
        val sellInput = _state.value.globalSell.trim().toBigDecimalOrNull()
        FeeRulePolicy.validate(buyInput)?.let { return fail(it) }
        FeeRulePolicy.validate(sellInput)?.let { return fail(it) }
        val buy: BigDecimal = buyInput ?: return fail("请输入买入费率")
        val sell: BigDecimal = sellInput ?: return fail("请输入卖出费率")
        scope.launch {
            runCatching { service.saveGlobal(buy, sell) }
                .onSuccess {
                    toast(
                        WzToastKind.Success,
                        "全局默认费率已保存（买入 " + buy.toPlainString() +
                            "% / 卖出 " + sell.toPlainString() + "%）",
                    )
                }
                .onFailure { fail(it.message ?: "保存失败") }
            reload()
        }
    }

    /** 添加（编辑态 = 保存修改）：交易所名 + 两个费率校验早退。 */
    @Suppress("ReturnCount")
    fun saveExchange() {
        val editingId = _state.value.editingRuleId
        val name = _state.value.exchangeName.trim()
        if (name.isEmpty()) return fail("请填写交易所名称（如 BINANCE）")
        val buyInput = _state.value.exchangeBuy.trim().toBigDecimalOrNull()
        val sellInput = _state.value.exchangeSell.trim().toBigDecimalOrNull()
        FeeRulePolicy.validate(buyInput)?.let { return fail(it) }
        FeeRulePolicy.validate(sellInput)?.let { return fail(it) }
        val buy: BigDecimal = buyInput ?: return fail("请输入买入费率")
        val sell: BigDecimal = sellInput ?: return fail("请输入卖出费率")
        scope.launch {
            runCatching {
                if (editingId != null) {
                    service.saveExchangeEdit(editingId, name, buy, sell)
                } else {
                    service.saveExchange(name, buy, sell)
                }
            }
                .onSuccess {
                    val renamed = editingId != null
                    _state.update {
                        it.copy(exchangeName = "", exchangeBuy = "", exchangeSell = "", editingRuleId = null)
                    }
                    toast(
                        WzToastKind.Success,
                        (if (renamed) "已更新 " else "已保存 ") + name.uppercase() + " 费率（买入 " +
                            buy.toPlainString() + "% / 卖出 " + sell.toPlainString() + "%）",
                    )
                }
                .onFailure { fail(it.message ?: "保存失败") }
            reload()
        }
    }

    fun removeRule(id: Long) {
        scope.launch {
            runCatching { service.removeRule(id) }
                .onSuccess {
                    if (_state.value.editingRuleId == id) cancelEdit()
                    toast(WzToastKind.Success, "费率规则已删除")
                }
                .onFailure { fail(it.message ?: "删除失败") }
            reload()
        }
    }

    fun dismissToast() = _state.update { it.copy(toast = null) }

    private fun fail(message: String) = _state.update { it.copy(error = message) }

    private fun toast(kind: WzToastKind, message: String) = _state.update { it.copy(toast = WzToast(kind, message)) }

    override fun onCleared() {
        scope.cancel()
    }

    fun dispose() {
        scope.cancel()
    }
}

/**
 * 设置页 · 手续费分组（M10 T10.1 完整 CRUD；PRD §9.11 / §7.2-6.3；嵌入完整设置页，页面滚动由宿主提供）：
 * 全局默认费率（买入/卖出）+ 交易所费率（添加/编辑/删除，「交易所 > 全局」生效）。
 */
@Composable
fun FeeRuleSettingsSection(service: FeeRuleService, modifier: Modifier = Modifier) {
    val vm = remember { FeeRuleSettingsViewModel(service) }
    DisposableEffect(vm) {
        onDispose { vm.dispose() }
    }
    val state by vm.state.collectAsState()
    val colors = WzTheme.colors
    val editing = state.editingRuleId != null

    Box(modifier = modifier.testTag("fee-rule-settings")) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 全局默认
            Text(text = "全局默认费率", color = colors.ink, style = WzTheme.typography.bodyStrong)
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WzTextField(
                    value = state.globalBuy,
                    onValueChange = vm::onGlobalBuy,
                    label = "买入费率 %",
                    placeholder = "0.1",
                    modifier = Modifier.width(160.dp),
                    testTag = "fee-global-buy",
                )
                WzTextField(
                    value = state.globalSell,
                    onValueChange = vm::onGlobalSell,
                    label = "卖出费率 %",
                    placeholder = "0.1",
                    modifier = Modifier.width(160.dp),
                    testTag = "fee-global-sell",
                )
                WzButton(text = "保存全局费率", onClick = vm::saveGlobal, testTag = "fee-global-save")
            }

            // 交易所费率
            Text(
                text = "交易所费率（覆盖全局）",
                color = colors.ink,
                style = WzTheme.typography.bodyStrong,
                modifier = Modifier.padding(top = 20.dp),
            )
            if (state.rules.none { it.exchange != null }) {
                Text(
                    text = "尚未添加交易所费率（可选）",
                    color = colors.ink3,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.padding(top = 6.dp).testTag("fee-exchange-empty"),
                )
            } else {
                state.rules.filter { it.exchange != null }.forEach { rule ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("fee-rule-" + rule.exchange),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = rule.exchange ?: "",
                            color = colors.ink,
                            style = WzTheme.typography.body,
                            modifier = Modifier.width(140.dp),
                        )
                        Text(
                            text = "买入 " + rule.buyPercent.stripTrailingZeros().toPlainString() + "%" +
                                " · 卖出 " + rule.sellPercent.stripTrailingZeros().toPlainString() + "%",
                            color = colors.ink2,
                            style = WzTheme.typography.body,
                            modifier = Modifier.weight(1f),
                        )
                        WzButton(
                            text = "编辑",
                            onClick = { vm.beginEdit(rule) },
                            variant = WzButtonVariant.Secondary,
                            testTag = "fee-rule-edit-" + rule.exchange,
                        )
                        WzButton(
                            text = "删除",
                            onClick = { vm.removeRule(rule.id) },
                            variant = WzButtonVariant.Danger,
                            modifier = Modifier.padding(start = 8.dp),
                            testTag = "fee-rule-remove-" + rule.exchange,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WzTextField(
                    value = state.exchangeName,
                    onValueChange = vm::onExchangeName,
                    label = "交易所",
                    placeholder = "BINANCE",
                    modifier = Modifier.width(160.dp),
                    testTag = "fee-exchange-name",
                )
                WzTextField(
                    value = state.exchangeBuy,
                    onValueChange = vm::onExchangeBuy,
                    label = "买入费率 %",
                    placeholder = "0.1",
                    modifier = Modifier.width(140.dp),
                    testTag = "fee-exchange-buy",
                )
                WzTextField(
                    value = state.exchangeSell,
                    onValueChange = vm::onExchangeSell,
                    label = "卖出费率 %",
                    placeholder = "0.1",
                    modifier = Modifier.width(140.dp),
                    testTag = "fee-exchange-sell",
                )
                if (editing) {
                    WzButton(
                        text = "保存修改",
                        onClick = vm::saveExchange,
                        variant = WzButtonVariant.Primary,
                        testTag = "fee-exchange-save",
                    )
                    WzButton(
                        text = "取消",
                        onClick = vm::cancelEdit,
                        variant = WzButtonVariant.Secondary,
                        testTag = "fee-exchange-cancel",
                    )
                } else {
                    WzButton(
                        text = "添加 / 覆盖交易所费率",
                        onClick = vm::saveExchange,
                        variant = WzButtonVariant.Secondary,
                        testTag = "fee-exchange-save",
                    )
                }
            }

            // M10 走查反馈修复轮：交易所 = 可搜索选择（候选建议点击填入，保留自由输入以支持后续交易所）
            ExchangeSuggestions(
                query = state.exchangeName,
                existingRules = state.rules.mapNotNull { it.exchange },
                onPick = vm::onExchangeName,
            )

            val errorText = state.error
            if (errorText != null) {
                Text(
                    text = errorText,
                    color = colors.loss,
                    style = WzTheme.typography.body,
                    modifier = Modifier.padding(top = 10.dp).testTag("fee-rule-error"),
                )
            }
        }
        WzToastHost(toast = state.toast, onDismiss = vm::dismissToast)
    }
}

/** MVP 已知交易所（PRD §7.2-4.1 仅 Binance；后续交易所随 api_keys 扩展——FeeRateResolver 匹配大小写不敏感）。 */
private val KNOWN_EXCHANGES: List<String> = listOf("BINANCE")

/** 交易所候选建议（已知 ∪ 现有规则，按输入包含过滤、忽略大小写；点击填入输入框——不自动保存）。 */
@Composable
private fun ExchangeSuggestions(
    query: String,
    existingRules: List<String>,
    onPick: (String) -> Unit,
) {
    val colors = WzTheme.colors
    val candidates = (KNOWN_EXCHANGES + existingRules).distinct()
    val trimmed = query.trim()
    val suggestions = candidates.filter {
        (trimmed.isEmpty() || it.contains(trimmed, ignoreCase = true)) && !it.equals(trimmed, ignoreCase = true)
    }
    if (suggestions.isEmpty()) return
    Column(modifier = Modifier.padding(top = 6.dp).testTag("fee-exchange-suggestions")) {
        Text(
            text = if (trimmed.isEmpty()) "候选交易所（点击填入，也可直接输入其他交易所）：" else "候选（按输入过滤）：",
            color = colors.ink3,
            style = WzTheme.typography.caption,
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            suggestions.forEach { name ->
                WzButton(
                    text = name,
                    onClick = { onPick(name) },
                    variant = WzButtonVariant.Secondary,
                    testTag = "fee-exchange-suggest-" + name,
                )
            }
        }
    }
}
