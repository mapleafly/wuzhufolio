package com.wuzhufolio.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.theme.WzTheme
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 删除确认框（PRD 故事 6.3：将删除 N 条资金记录并重算投入本金与 ROI，是否继续？）。 */
@Composable
internal fun FundDeleteConfirmModal(count: Int, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val colors = WzTheme.colors
    WzModal(
        title = FundsCopy.BTN_DELETE,
        onDismiss = onCancel,
        testTag = "fund-delete-confirm",
    ) {
        Text(
            text = FundsCopy.DELETE_CONFIRM.replace("N", count.toString()),
            color = colors.ink2,
            style = WzTheme.typography.body,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            WzButton(
                text = FundsCopy.BTN_CANCEL,
                onClick = onCancel,
                variant = WzButtonVariant.Secondary,
                testTag = "fund-delete-cancel",
            )
            WzButton(
                text = "确认删除",
                onClick = onConfirm,
                variant = WzButtonVariant.Danger,
                modifier = Modifier.padding(start = 8.dp),
                testTag = "fund-delete-confirm-btn",
            )
        }
    }
}

internal fun fundQty(v: BigDecimal): String = v.stripTrailingZeros().toPlainString()

internal fun fiatMoney(v: BigDecimal): String = "$" + v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()

internal fun fundsTimeText(at: java.time.Instant): String =
    at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
