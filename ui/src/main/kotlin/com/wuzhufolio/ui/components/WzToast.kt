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
 * **不吞点击（M11 §6 遗留闭环，2026-09-11）**：此前用 Material3 `Surface` 承载——M3 Surface 内部
 * 带 `pointerInput` 消费指针事件以阻止穿透，导致 toast 存活期内右下角区域的点击被静默吞掉
 * （M11 UI 测试实测复现「连点开关只生效第一个」）。改用无指针输入的 `Box` + 阴影/描边自绘，
 * 点击直接穿透到下层控件；toast 自身无交互（仅展示），不违反 AGENTS.md §7.3（该约束针对交互弹层）。
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
    Box(
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(20.dp)
            .shadow(6.dp, RoundedCornerShape(7.dp))
            .background(colors.surface, RoundedCornerShape(7.dp))
            .border(1.dp, colors.line, RoundedCornerShape(7.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
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
                color = colors.ink,
                style = WzTheme.typography.body,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** toast 存活时长（毫秒；3s = design-tokens §4.2 轻提示口径）。 */
const val VISIBLE_MS: Long = 3000L
