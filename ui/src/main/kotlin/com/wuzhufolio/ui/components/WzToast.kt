package com.wuzhufolio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.theme.WzTheme
import kotlinx.coroutines.delay

/** Toast 类型（成功/失败，design-tokens §4.2 轻提示）。 */
enum class WzToastKind { Success, Failure }

data class WzToast(val kind: WzToastKind, val message: String)

/**
 * Toast 宿主：右下角轻提示，3s 自动消失。
 * 语义用色点 + 文字双重表达（PRD §6：颜色不作为唯一信息载体）。
 */
@Composable
fun BoxScope.WzToastHost(
    toast: WzToast?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (toast == null) return
    LaunchedEffect(toast) {
        delay(3000)
        onDismiss()
    }
    val colors = WzTheme.colors
    val dotColor = if (toast.kind == WzToastKind.Success) colors.gain else colors.loss
    Surface(
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(20.dp)
            .testTag("wz-toast"),
        shape = RoundedCornerShape(7.dp),
        color = colors.surface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, colors.line, RoundedCornerShape(7.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape),
            )
            Text(
                text = toast.message,
                color = colors.ink,
                style = WzTheme.typography.body,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
