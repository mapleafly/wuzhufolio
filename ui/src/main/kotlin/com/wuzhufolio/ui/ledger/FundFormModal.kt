package com.wuzhufolio.ui.ledger

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 增资/撤资表单（PRD §9.8 · 原型资金表单逐字口径）：币种（目录补全 + 法币提示 + 默认稳定币）、
 * 数量（V6 正数）、日期时间（本地时区）、来源/去向、备注、折算基础法币预览（记录时行情价）。
 * 共性约束 7.3：打开即聚焦首输入框（币种）。
 */
@Composable
fun FundFormModal(
    state: FundFormState,
    vm: FundsViewModel,
    onDismiss: () -> Unit,
) {
    val colors = WzTheme.colors
    val coinFocus = remember { FocusRequester() }
    WzModal(
        title = if (state.kind == FlowKind.DEPOSIT) FundsCopy.FORM_TITLE_DEPOSIT else FundsCopy.FORM_TITLE_WITHDRAW,
        onDismiss = onDismiss,
        width = 560.dp,
        initialFocusRequester = coinFocus,
        testTag = "fund-modal",
    ) {
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(end = 12.dp)
                    .testTag("fund-form-scroll"),
            ) {
                // 币种（目录自动补全 + 归一；法币实时提示）
                WzTextField(
                    value = state.coinSymbol,
                    onValueChange = vm::onCoinChange,
                    label = FundsCopy.LABEL_COIN,
                    placeholder = FundsCopy.COIN_PLACEHOLDER,
                    error = state.errors[FundField.COIN.key],
                    fieldFocusRequester = coinFocus,
                    testTag = "fund-coin-input",
                )
                FundSuggestionList(state.candidates) { vm.pickCandidate(it) }
                Text(
                    text = state.fiatHint ?: FundsCopy.COIN_HINT,
                    color = if (state.fiatHint != null) colors.warn else colors.ink3,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.padding(top = 4.dp).testTag("fund-coin-hint"),
                )

                // 数量 + 日期时间
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WzTextField(
                        value = state.quantity,
                        onValueChange = vm::onQuantityChange,
                        label = FundsCopy.LABEL_QTY,
                        placeholder = "0.00",
                        error = state.errors[FundField.QTY.key],
                        modifier = Modifier.weight(1f),
                        testTag = "fund-qty-input",
                    )
                    WzTextField(
                        value = state.timeText,
                        onValueChange = { vm.onFieldChange(FundField.TIME, it) },
                        label = FundsCopy.LABEL_TIME,
                        placeholder = "yyyy-MM-ddTHH:mm",
                        error = state.errors[FundField.TIME.key],
                        modifier = Modifier.weight(1.3f),
                        testTag = "fund-time-input",
                    )
                }

                // 来源/去向 + 备注
                WzTextField(
                    value = state.sourceDest,
                    onValueChange = { vm.onFieldChange(FundField.SOURCE_DEST, it) },
                    label = if (state.kind == FlowKind.DEPOSIT) FundsCopy.LABEL_SOURCE else FundsCopy.LABEL_DEST,
                    placeholder = "可选",
                    modifier = Modifier.padding(top = 10.dp),
                    testTag = "fund-source-input",
                )
                WzTextField(
                    value = state.notes,
                    onValueChange = { vm.onFieldChange(FundField.NOTES, it) },
                    label = FundsCopy.LABEL_NOTES,
                    placeholder = "可选",
                    modifier = Modifier.padding(top = 10.dp),
                    testTag = "fund-notes-input",
                )

                // 折算预览（记录时行情价；PENDING = 待定价）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(colors.surface2)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("fund-fiat-preview"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = FundsCopy.FIAT_PREVIEW_LABEL,
                        color = colors.ink2,
                        style = WzTheme.typography.body,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = fiatPreviewText(state),
                        color = colors.ink,
                        style = WzTheme.typography.bodyStrong,
                    )
                }

                // 表单级错误（V7/V9 服务校验反馈）
                if (state.formError != null) {
                    Text(
                        text = state.formError,
                        color = colors.loss,
                        style = WzTheme.typography.body,
                        modifier = Modifier.padding(top = 10.dp).testTag("fund-form-error"),
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }

        // 底部动作（固定可见）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            WzButton(
                text = FundsCopy.BTN_CANCEL,
                onClick = onDismiss,
                variant = WzButtonVariant.Secondary,
                testTag = "fund-cancel",
            )
            WzButton(
                text = FundsCopy.BTN_SAVE,
                onClick = vm::saveForm,
                enabled = !state.busy,
                modifier = Modifier.padding(start = 8.dp),
                testTag = "fund-save",
            )
        }
    }
}

private fun fiatPreviewText(state: FundFormState): String {
    val preview = state.fiatPreview
    val value = preview?.fiatValue
    return when {
        value == null -> FundsCopy.FIAT_PREVIEW_PENDING
        else -> "$" + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() +
            if (preview.estimated) FundsCopy.FIAT_PREVIEW_ESTIMATED else ""
    }
}

/** 目录候选列表（币种自动补全；点击选中即收起）。 */
@Composable
private fun FundSuggestionList(candidates: List<CatalogCoin>, onPick: (CatalogCoin) -> Unit) {
    if (candidates.isEmpty()) return
    val colors = WzTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(colors.surface)
            .testTag("fund-suggestion-list"),
    ) {
        candidates.take(6).forEach { coin ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(coin) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = coin.symbol + " · " + coin.name,
                    color = colors.ink,
                    style = WzTheme.typography.body,
                )
            }
        }
    }
}
