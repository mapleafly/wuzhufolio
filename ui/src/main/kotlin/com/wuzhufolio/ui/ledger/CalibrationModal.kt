package com.wuzhufolio.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 持仓校准弹窗（M8 · T8.2 · PRD 故事 4.1-5「以交易所余额校准持仓」）：币种输入 -> 校准预览
 * （依据交易所/密钥/前后数量/差额/折算金额/方向与账务语义）-> 确认执行（锚点入库 + 同步日志留痕）。
 * 前提不满足（多来源/无密钥/无行情）以错误文案呈现（PRD「按钮隐藏并显示相应提示」的弹窗形态）。
 *
 * 入口偏差登记（模块记录 M8 §5）：PRD 入口在币种详情页（M12 聚合页），本模块先挂资金管理页命令区。
 */
@Composable
fun CalibrationModal(
    state: CalibrationUiState,
    vm: FundsViewModel,
    onDismiss: () -> Unit,
) {
    val colors = WzTheme.colors
    val coinFocus = remember { FocusRequester() }
    WzModal(
        title = FundsCopy.CAL_TITLE,
        onDismiss = onDismiss,
        width = 560.dp,
        initialFocusRequester = coinFocus,
        testTag = "calibration-modal",
    ) {
        WzTextField(
            value = state.coinInput,
            onValueChange = vm::onCalibrationCoinChange,
            label = FundsCopy.CAL_COIN_LABEL,
            placeholder = FundsCopy.CAL_COIN_PLACEHOLDER,
            fieldFocusRequester = coinFocus,
            testTag = "cal-coin-input",
        )
        if (state.candidates.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(colors.surface)
                    .testTag("cal-suggestion-list"),
            ) {
                state.candidates.take(FundsCopy.MAX_CANDIDATES).forEach { coin ->
                    Text(
                        text = coin.symbol + " · " + coin.name + "（" + coin.cgId + "）",
                        color = colors.ink,
                        style = WzTheme.typography.body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.pickCalibrationCandidate(coin) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (state.pickedLabel != null) {
            Text(
                text = FundsCopy.PICKED_PREFIX + state.pickedLabel,
                color = colors.accent,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp).testTag("cal-picked"),
            )
        }
        WzButton(
            text = FundsCopy.CAL_PREPARE,
            onClick = vm::prepareCalibration,
            variant = WzButtonVariant.Secondary,
            enabled = !state.busy && state.coinInput.isNotBlank(),
            modifier = Modifier.padding(top = 10.dp),
            testTag = "cal-prepare",
        )
        state.preparation?.let { prep ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(colors.surface2)
                    .padding(12.dp)
                    .testTag("cal-preview"),
            ) {
                CalRow(FundsCopy.CAL_ROW_EXCHANGE, prep.exchangeName)
                CalRow(FundsCopy.CAL_ROW_KEY, prep.apiKeyName)
                CalRow(FundsCopy.CAL_ROW_LOCAL, qty(prep.localQuantity))
                CalRow(FundsCopy.CAL_ROW_EXCHANGE_QTY, qty(prep.exchangeQuantity))
                CalRow(FundsCopy.CAL_ROW_DELTA, signed(prep.delta) + " " + prep.coinSymbol)
                CalRow(FundsCopy.CAL_ROW_FIAT, "$" + money(prep.deltaFiat))
                CalRow(FundsCopy.CAL_ROW_PRICE, "$" + money(prep.marketPrice))
                Text(
                    text = directionCopy(prep.direction),
                    color = colors.ink2,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            WzButton(
                text = if (state.executing) FundsCopy.CAL_EXECUTING else FundsCopy.CAL_EXECUTE,
                onClick = vm::executeCalibration,
                enabled = !state.executing && prep.delta.signum() != 0,
                modifier = Modifier.padding(top = 12.dp),
                testTag = "cal-execute",
            )
        }
        if (state.error != null) {
            Text(
                text = state.error,
                color = colors.loss,
                style = WzTheme.typography.body,
                modifier = Modifier.padding(top = 10.dp).testTag("cal-error"),
            )
        }
    }
}

@Composable
private fun CalRow(label: String, value: String) {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, color = colors.ink, style = WzTheme.typography.body)
    }
}

private fun directionCopy(direction: FlowKind?): String = when (direction) {
    FlowKind.DEPOSIT -> FundsCopy.CAL_DIRECTION_POSITIVE
    FlowKind.WITHDRAWAL -> FundsCopy.CAL_DIRECTION_NEGATIVE
    null -> FundsCopy.CAL_DIRECTION_ZERO
}

private fun qty(v: java.math.BigDecimal): String = v.stripTrailingZeros().toPlainString()

private fun signed(v: java.math.BigDecimal): String =
    (if (v.signum() > 0) "+" else "") + v.stripTrailingZeros().toPlainString()

private fun money(v: java.math.BigDecimal): String = v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
