package com.wuzhufolio.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距 token（ADR-007 · design-tokens §4.1-1）。
 *
 * **为什么自建**：Material 3 只提供 color role 与 shape，**不提供 spacing token**——
 * 不建这一层，组件里就会各自写 `.padding(13.dp)`，页面之间永远对不齐（这正是 0.1.1 反馈「像手工制作」的成因之一）。
 *
 * 阶梯取 4 的倍数（桌面紧凑档，与既有实现的 4/6/8/12/16/20 实测值一致）；
 * 命名按「相对位置」而非数值，便于后续整体调档。
 */
object WzSpacing {
    /** 2dp：描边内缩、图标视觉修正。 */
    val hairline: Dp = 2.dp

    /** 4dp：图标与文字、徽标内边距、紧凑行内间隙。 */
    val xxs: Dp = 4.dp

    /** 6dp：控件内部纵向内边距（按钮/输入框）。 */
    val xs: Dp = 6.dp

    /** 8dp：同级控件之间、表单行之间。 */
    val sm: Dp = 8.dp

    /** 12dp：表单同一行的列间距、列表项左右内边距。 */
    val md: Dp = 12.dp

    /** 16dp：卡片/区块内边距。 */
    val lg: Dp = 16.dp

    /** 20dp：弹窗卡片内边距。 */
    val xl: Dp = 20.dp

    /** 24dp：页面内区块之间。 */
    val xxl: Dp = 24.dp

    /** 32dp：页面外边距（宽窗）。 */
    val xxxl: Dp = 32.dp
}
