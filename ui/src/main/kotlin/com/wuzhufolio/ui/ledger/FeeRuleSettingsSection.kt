package com.wuzhufolio.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
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

/** 费率设置页状态（M7 GUI 走查修复轮 · 最小 CRUD；M10 整页接管时扩展）。 */
data class FeeRuleUiState(
    val rules: List<FeeRuleRow> = emptyList(),
    val globalBuy: String = "",
    val globalSell: String = "",
    val exchangeName: String = "",
    val exchangeBuy: String = "",
    val exchangeSell: String = "",
    val error: String? = null,
    val busy: Boolean = false,
    val toast: WzToast? = null,
)

/**
 * 费率设置 VM（M7 T7.2 端到端补口）：全局默认费率 + 交易所费率增删。
 * 表单「自动计算手续费」按 交易所 > 全局 匹配（FeeRateResolver）。
 */
@Suppress("TooManyFunctions") // 页面动作面（加载/五个输入/两个保存/删除/toast）固有
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

    @Suppress("ReturnCount") // 交易所名 + 两个费率输入的校验早退
    fun addExchange() {
        val name = _state.value.exchangeName.trim()
        if (name.isEmpty()) return fail("请填写交易所名称（如 BINANCE）")
        val buyInput = _state.value.exchangeBuy.trim().toBigDecimalOrNull()
        val sellInput = _state.value.exchangeSell.trim().toBigDecimalOrNull()
        FeeRulePolicy.validate(buyInput)?.let { return fail(it) }
        FeeRulePolicy.validate(sellInput)?.let { return fail(it) }
        val buy: BigDecimal = buyInput ?: return fail("请输入买入费率")
        val sell: BigDecimal = sellInput ?: return fail("请输入卖出费率")
        scope.launch {
            runCatching { service.saveExchange(name, buy, sell) }
                .onSuccess {
                    _state.update { it.copy(exchangeName = "", exchangeBuy = "", exchangeSell = "") }
                    toast(
                        WzToastKind.Success,
                        "已保存 " + name.uppercase() + " 费率（买入 " + buy.toPlainString() +
                            "% / 卖出 " + sell.toPlainString() + "%）",
                    )
                }
                .onFailure { fail(it.message ?: "保存失败") }
            reload()
        }
    }

    fun removeRule(id: Long) {
        scope.launch {
            runCatching { service.removeRule(id) }
                .onSuccess { toast(WzToastKind.Success, "费率规则已删除") }
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
 * 设置页 · 手续费费率分组（M7 T7.2 端到端补口；PRD §9.11 / §7.2-6.3）。
 * 完整设置页与费率 CRUD 归 M10（T10.1）——本分组为最小实现，M10 整页接管时迁移/扩展。
 */
@Composable
fun FeeRuleSettingsSection(service: FeeRuleService, modifier: Modifier = Modifier) {
    val vm = remember { FeeRuleSettingsViewModel(service) }
    DisposableEffect(vm) {
        onDispose { vm.dispose() }
    }
    val state by vm.state.collectAsState()
    val colors = WzTheme.colors

    Box(modifier = modifier.fillMaxSize().testTag("fee-rule-settings")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(text = "手续费费率", color = colors.ink, style = WzTheme.typography.pageTitle)
            Text(
                text = "交易表单「自动计算手续费」按 交易所 > 全局 匹配；费率 = 百分比（如 0.1 = 0.1%）",
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

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
                            text = "删除",
                            onClick = { vm.removeRule(rule.id) },
                            variant = WzButtonVariant.Danger,
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
                WzButton(
                    text = "添加 / 覆盖交易所费率",
                    onClick = vm::addExchange,
                    variant = WzButtonVariant.Secondary,
                    testTag = "fee-exchange-save",
                )
            }

            val errorText = state.error
            if (errorText != null) {
                Text(
                    text = errorText,
                    color = colors.loss,
                    style = WzTheme.typography.body,
                    modifier = Modifier.padding(top = 10.dp).testTag("fee-rule-error"),
                )
            }
            Text(
                text = "注：完整设置页与费率管理归 M10；本分组为 M7 端到端验证提供最小 CRUD。",
                color = colors.ink3,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        WzToastHost(toast = state.toast, onDismiss = vm::dismissToast)
    }
}
