package com.wuzhufolio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.theme.WzTheme

/** 按钮变体（design-tokens §4.2）：主按钮 accent 实底 / 次按钮 accent 描边 / 危险按钮 loss 实底。 */
enum class WzButtonVariant { Primary, Secondary, Danger }

/**
 * 基础按钮（**ADR-007 §2.3：内部改用官方 Material 3 `Button`，对外 API 与视觉口径不变**）。
 *
 * 改造前是 Foundation 手绘（`Box` + `background` + `border` + `clickable` + `Text`），只有「常规/禁用」两态；
 * 换成 M3 后免费获得 **hover / pressed / focused 状态层**、涟漪指示、正确的 `Role.Button` 语义与禁用态处理。
 *
 * 视觉口径**刻意保持不变**（「换框架 ≠ 换外观」的示范）：
 * 高度 34dp（design-tokens §4.2 的 32–36dp 档）· 圆角 7dp（与 `Shapes.small` 同值）·
 * 内边距 14/6dp · 字号字重取 `typography.bodyStrong` · **无阴影**（`elevation = null`，保持扁平）。
 */
@Composable
fun WzButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: WzButtonVariant = WzButtonVariant.Primary,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val colors = WzTheme.colors
    val container = when (variant) {
        WzButtonVariant.Primary -> colors.accent
        WzButtonVariant.Secondary -> colors.surface
        WzButtonVariant.Danger -> colors.loss
    }
    val content = when (variant) {
        WzButtonVariant.Primary -> colors.accentInk
        WzButtonVariant.Secondary -> colors.accent
        WzButtonVariant.Danger -> colors.accentInk
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .height(BUTTON_HEIGHT)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = colors.surface2,
            disabledContentColor = colors.ink3,
        ),
        // 次按钮 = accent 描边（与改造前 2dp 口径一致）；其余无描边
        border = if (variant == WzButtonVariant.Secondary) BorderStroke(2.dp, colors.accent) else null,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        // 扁平风格：不使用 M3 默认的容器高度色阶/阴影
        elevation = null,
    ) {
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            style = WzTheme.typography.bodyStrong,
        )
    }
}

/** 按钮固定高度（design-tokens §4.2：32–36dp；与改造前 `heightIn(min = 32.dp)` 的实测渲染高度一致）。 */
private val BUTTON_HEIGHT = 34.dp
