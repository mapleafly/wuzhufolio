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
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 添加/编辑交易表单（PRD §9.7 · 原型交易表单逐字口径）：交易所/交易对（基础币与计价币均支持目录
 * 自动补全）/类型/价格/数量/手续费（手填或自动计算）/手续费币种三态/实时总价/交易时间/备注。
 *
 * 布局口径（2026-09-07 GUI 走查修复轮）：紧凑两列 + 内容区限高滚动 + 可见滚动条——
 * 此前单列超高导致交易时间/备注/错误提示被裁出可视区（表现为「保存无反应」）。
 * 共性约束 7.3：打开即聚焦首输入框（baseSymbol）。
 */
@Composable
fun TransactionFormModal(
    state: TxFormState,
    vm: TransactionsViewModel,
    onDismiss: () -> Unit,
) {
    val colors = WzTheme.colors
    val baseFocus = remember { FocusRequester() }
    WzModal(
        title = if (state.id == null) TransactionCopy.FORM_TITLE_ADD else TransactionCopy.FORM_TITLE_EDIT,
        onDismiss = onDismiss,
        width = 760.dp,
        initialFocusRequester = baseFocus,
        testTag = "tx-modal",
    ) {
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 470.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(end = 12.dp)
                    .testTag("tx-form-scroll"),
            ) {
                // 交易所 + 类型
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WzTextField(
                        value = state.exchange,
                        onValueChange = { vm.onFieldChange(TxField.EXCHANGE, it) },
                        label = TransactionCopy.LABEL_EXCHANGE,
                        placeholder = "BINANCE",
                        modifier = Modifier.weight(1f),
                        testTag = "tx-exchange-input",
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = TransactionCopy.LABEL_SIDE,
                            color = colors.ink2,
                            style = WzTheme.typography.caption,
                        )
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            WzButton(
                                text = if (state.side == Side.BUY) "● " + TransactionCopy.SIDE_BUY
                                else "○ " + TransactionCopy.SIDE_BUY,
                                onClick = { vm.setSide(Side.BUY) },
                                variant = if (state.side == Side.BUY) WzButtonVariant.Primary
                                else WzButtonVariant.Secondary,
                                testTag = "tx-side-buy",
                            )
                            WzButton(
                                text = if (state.side == Side.SELL) "● " + TransactionCopy.SIDE_SELL
                                else "○ " + TransactionCopy.SIDE_SELL,
                                onClick = { vm.setSide(Side.SELL) },
                                variant = if (state.side == Side.SELL) WzButtonVariant.Primary
                                else WzButtonVariant.Secondary,
                                testTag = "tx-side-sell",
                            )
                        }
                    }
                }

                // 交易对（基础币 + 计价币均支持目录自动补全）
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        WzTextField(
                            value = state.baseSymbol,
                            onValueChange = vm::onBaseSymbolChange,
                            label = TransactionCopy.LABEL_PAIR + TransactionCopy.LABEL_PAIR_BASE,
                            placeholder = "BTC",
                            error = state.errors[TxField.PAIR.key],
                            fieldFocusRequester = baseFocus,
                            testTag = "tx-base-input",
                        )
                        SuggestionList(state.baseCandidates) { vm.pickBaseCandidate(it) }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        WzTextField(
                            value = state.quoteSymbol,
                            onValueChange = vm::onQuoteSymbolChange,
                            label = TransactionCopy.LABEL_QUOTE,
                            placeholder = "USDT",
                            error = state.errors[TxField.PAIR.key],
                            testTag = "tx-quote-input",
                        )
                        SuggestionList(state.quoteCandidates) { vm.pickQuoteCandidate(it) }
                    }
                }
                Text(
                    text = TransactionCopy.PAIR_HINT,
                    color = colors.ink3,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.padding(top = 4.dp),
                )

                // 价格 / 数量
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WzTextField(
                        value = state.price,
                        onValueChange = { vm.onFieldChange(TxField.PRICE, it) },
                        label = TransactionCopy.LABEL_PRICE,
                        placeholder = "0.00",
                        error = state.errors[TxField.PRICE.key],
                        modifier = Modifier.weight(1f),
                        testTag = "tx-price-input",
                    )
                    WzTextField(
                        value = state.quantity,
                        onValueChange = { vm.onFieldChange(TxField.QUANTITY, it) },
                        label = TransactionCopy.LABEL_QTY,
                        placeholder = "0.00",
                        error = state.errors[TxField.QUANTITY.key],
                        modifier = Modifier.weight(1f),
                        testTag = "tx-qty-input",
                    )
                }

                // 手续费 + 手续费币种三态
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WzTextField(
                        value = state.fee,
                        onValueChange = { vm.onFieldChange(TxField.FEE, it) },
                        label = TransactionCopy.LABEL_FEE + TransactionCopy.LABEL_FEE_OPTIONAL,
                        placeholder = "0",
                        error = state.errors[TxField.FEE.key],
                        modifier = Modifier.weight(1f),
                        testTag = "tx-fee-input",
                    )
                    Column(modifier = Modifier.weight(1.6f)) {
                        Text(
                            text = TransactionCopy.LABEL_FEE_CURRENCY,
                            color = colors.ink2,
                            style = WzTheme.typography.caption,
                        )
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FeeRoleButton(
                                text = TransactionCopy.FEE_ROLE_QUOTE,
                                selected = state.feeRole == FeeRole.QUOTE,
                                testTag = "tx-fee-role-quote",
                            ) { vm.setFeeRole(FeeRole.QUOTE) }
                            FeeRoleButton(
                                text = TransactionCopy.FEE_ROLE_BASE,
                                selected = state.feeRole == FeeRole.BASE,
                                testTag = "tx-fee-role-base",
                            ) { vm.setFeeRole(FeeRole.BASE) }
                            FeeRoleButton(
                                text = TransactionCopy.FEE_ROLE_CUSTOM,
                                selected = state.feeRole == FeeRole.CUSTOM,
                                testTag = "tx-fee-role-custom",
                            ) { vm.setFeeRole(FeeRole.CUSTOM) }
                        }
                        Text(
                            text = TransactionCopy.currentFee(
                                state.feeSymbol.ifBlank { TransactionCopy.FEE_ROLE_QUOTE },
                            ),
                            color = colors.ink3,
                            style = WzTheme.typography.caption,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (state.feeRole == FeeRole.CUSTOM) {
                    WzTextField(
                        value = state.feeCustom,
                        onValueChange = vm::onFeeCustomChange,
                        label = TransactionCopy.LABEL_FEE_CUSTOM,
                        placeholder = "BNB",
                        error = state.errors[TxField.FEE_CUSTOM.key],
                        modifier = Modifier.padding(top = 10.dp),
                        testTag = "tx-fee-custom-input",
                    )
                    SuggestionList(state.feeCandidates) { vm.pickFeeCandidate(it) }
                    Text(
                        text = TransactionCopy.CUSTOM_FEE_HINT,
                        color = colors.ink3,
                        style = WzTheme.typography.caption,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // 自动计算 + 实时总价（同一行，压缩纵向空间）
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WzButton(
                        text = TransactionCopy.AUTO_FEE_BTN,
                        onClick = vm::autoCalcFee,
                        variant = WzButtonVariant.Secondary,
                        testTag = "tx-auto-fee",
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(colors.surface2)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("tx-total"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = TransactionCopy.TOTAL_LABEL,
                            color = colors.ink2,
                            style = WzTheme.typography.body,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = state.total,
                            color = colors.ink,
                            style = WzTheme.typography.bodyStrong,
                        )
                    }
                }

                // 交易时间（本地输入，保存转 UTC）/ 备注
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WzTextField(
                        value = state.timeText,
                        onValueChange = { vm.onFieldChange(TxField.TIME, it) },
                        label = TransactionCopy.LABEL_TIME,
                        placeholder = "yyyy-MM-ddTHH:mm",
                        error = state.errors[TxField.TIME.key],
                        modifier = Modifier.weight(1.1f),
                        testTag = "tx-time-input",
                    )
                    WzTextField(
                        value = state.notes,
                        onValueChange = { vm.onFieldChange(TxField.NOTES, it) },
                        label = TransactionCopy.LABEL_NOTES,
                        placeholder = TransactionCopy.PLACEHOLDER_OPTIONAL,
                        modifier = Modifier.weight(1.4f),
                        testTag = "tx-notes-input",
                    )
                }

                // 表单级错误（V5/V9：余额不足 / 重放冲突）
                if (state.formError != null) {
                    Text(
                        text = state.formError,
                        color = colors.loss,
                        style = WzTheme.typography.body,
                        modifier = Modifier.padding(top = 10.dp).testTag("tx-form-error"),
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
                text = TransactionCopy.BTN_CANCEL,
                onClick = onDismiss,
                variant = WzButtonVariant.Secondary,
                testTag = "tx-cancel",
            )
            WzButton(
                text = TransactionCopy.BTN_SAVE,
                onClick = vm::saveForm,
                enabled = !state.busy,
                modifier = Modifier.padding(start = 8.dp),
                testTag = "tx-save",
            )
        }
    }
}

/** 手续费币种角色按钮（计价/基础/自定义）。 */
@Composable
private fun FeeRoleButton(text: String, selected: Boolean, testTag: String, onClick: () -> Unit) {
    WzButton(
        text = (if (selected) "● " else "○ ") + text,
        onClick = onClick,
        variant = if (selected) WzButtonVariant.Primary else WzButtonVariant.Secondary,
        testTag = testTag,
    )
}

/** 目录候选列表（基础币/计价币/自定义手续费币种自动补全；点击选中即收起）。 */
@Composable
private fun SuggestionList(candidates: List<CatalogCoin>, onPick: (CatalogCoin) -> Unit) {
    if (candidates.isEmpty()) return
    val colors = WzTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(colors.surface)
            .testTag("suggestion-list"),
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
