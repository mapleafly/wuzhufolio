package com.wuzhufolio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 圆角阶梯（ADR-007 §2.2 / D38）。
 *
 * **数值直接取 M3 token**（`ShapeTokens.kt`，M3 14_1_0）：extraSmall 4 · small 8 · medium 12 · large 16 · extraLarge 28。
 * D38 变更说明：上一版曾沿用自建的 4/7/10/14/20dp，属"像 M3 但不是 M3"——本轮改为**规范值**，
 * 于是 M3 组件（按钮 8dp 圆角、卡片 12dp、对话框 28dp）与自绘组件（`WzCard`/`WzModal` 等）**同一套阶梯**。
 *
 * 组件取圆角一律走 `MaterialTheme.shapes`（或在自绘组件里显式引用本表），**不得再写字面量**。
 */
internal fun wzShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
