package com.wuzhufolio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.theme.WzTheme
import kotlinx.coroutines.delay

/** Toast 类型（成功/失败，design-tokens §4.2 轻提示）。 */
enum class WzToastKind { Success, Failure }

data class WzToast(val kind: WzToastKind, val message: String)

/**
 * Toast 宿主：右下角轻提示，[VISIBLE_MS] 后自动消失。
 * 语义用色点 + 文字双重表达（PRD §6：颜色不作为唯一信息载体）。
 *
 * **视觉对齐 M3 Snackbar**（T14.3 尾项 / D38）：容器色 `inverseSurface`、文字 `inverseOnSurface`、
 * 圆角 `Shapes.small`、阴影 6dp、排版 `bodyMedium` —— 与 M3 Snackbar 规范一致。
 *
 * **但不使用 M3 `Snackbar` 组合项**（**有意偏离，见 D38 例外清单**）：M3 `Snackbar` 内部由 `Surface`
 * 承载，会**消费指针事件**，导致轻提示存活期内其覆盖区域的点击被静默吞掉
 * （M11 §6 实测「连点开关只生效第一个」；T14.3 尾项再次实测确认 Snackbar 同样吞点击，
 * 守护测试 `SnackbarPointerProbeTest`）。故保留无指针输入的 `Box` 自绘（点击穿透下层），
 * 仅取 M3 的视觉 token。toast 自身无交互（仅展示），不违反 AGENTS.md §7.3（该约束针对交互弹层）。
 *
 * 可访问性：以 `liveRegion = Polite` 声明，读屏在内容变化时朗读，不抢焦点。
 */
@Composable
fun BoxScope.WzToastHost(
    toast: WzToast?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (toast == null) return
    LaunchedEffect(toast) {
        delay(VISIBLE_MS)
        onDismiss()
    }
    val colors = WzTheme.colors
    val dotColor = if (toast.kind == WzToastKind.Success) colors.gain else colors.loss
    // M3 Snackbar 视觉 token（容器/文字取反色容器；形状取 Shapes.small）
    val container = MaterialTheme.colorScheme.inverseSurface
    val content = MaterialTheme.colorScheme.inverseOnSurface
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(20.dp)
            .shadow(6.dp, shape)
            .background(container, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("wz-toast"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape),
            )
            Text(
                text = toast.message,
                color = content,
                style = WzTheme.typography.body,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** toast 存活时长（毫秒；3s = design-tokens §4.2 轻提示口径）。 */
const val VISIBLE_MS: Long = 3000L
