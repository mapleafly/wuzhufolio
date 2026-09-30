package com.wuzhufolio.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

/**
 * Material 3 动效 token（ADR-007 §2.2 / D38）。
 *
 * **数值直接取 M3 token**（`MotionTokens.kt`，M3 14_1_0）：
 * 时长 16 档（short1–4 / medium1–4 / long1–4 / extraLong1–4）+ 6 条标准曲线。
 * M3 的动效规范是**一套 token + 两个 motion scheme**（standard / expressive），
 * 组件默认已经通过 `MaterialTheme.motionScheme` 消费（见 [wzMotionScheme]），
 * 自绘组件用本对象的常量，保证与官方组件的节奏一致。
 *
 * 用法：`animateFloatAsState(..., animationSpec = tween(WzMotion.SHORT_4, easing = WzMotion.standard))`
 * ——**不要再写裸 `tween(200)`**（那正是"每处节奏都不一样"的来源）。
 */
object WzMotion {
    // ── 时长（毫秒，M3 motion tokens）────────────────────────────────────────
    const val SHORT_1: Int = 50
    const val SHORT_2: Int = 100
    const val SHORT_3: Int = 150
    /** 状态切换（hover/pressed/选中）：最常用的一档。 */
    const val SHORT_4: Int = 200
    const val MEDIUM_1: Int = 250
    const val MEDIUM_2: Int = 300
    const val MEDIUM_3: Int = 350
    const val MEDIUM_4: Int = 400
    const val LONG_1: Int = 450
    const val LONG_2: Int = 500
    const val LONG_3: Int = 550
    const val LONG_4: Int = 600
    const val EXTRA_LONG_1: Int = 700
    const val EXTRA_LONG_2: Int = 800
    const val EXTRA_LONG_3: Int = 900
    const val EXTRA_LONG_4: Int = 1000

    // ── 曲线（M3 motion tokens）─────────────────────────────────────────────
    /** 强调曲线（进入快、收尾缓）：弹层/页面等需要"存在感"的过渡。 */
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 强调-加速：元素**离开**视口。 */
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 强调-减速：元素**进入**视口。 */
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 标准曲线：绝大多数属性过渡。 */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 标准-加速（离场）。 */
    val standardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** 标准-减速（入场）。 */
    val standardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** 短过渡（即时反馈，如悬停/按下）。 */
    fun <T> short(): androidx.compose.animation.core.AnimationSpec<T> =
        tween(SHORT_3, easing = standard)

    /** 中过渡（状态切换、内容淡入淡出）。 */
    fun <T> medium(): androidx.compose.animation.core.AnimationSpec<T> =
        tween(MEDIUM_2, easing = emphasized)

    /** 长过渡（弹层/页面进出、展开收起）。 */
    fun <T> long(): androidx.compose.animation.core.AnimationSpec<T> =
        tween(LONG_2, easing = emphasizedDecelerate)
}

/*
 * 说明（2026-09-28 实测）：M3 Expressive 的 `MotionScheme`（`standard()` / `expressive()`）与
 * `MaterialTheme(motionScheme = …)` 重载在 **material3 1.9.0 中仍是 internal**（编译期不可访问），
 * 因此本应用**不显式指定 motion scheme**：官方组件使用其默认动效，
 * 自绘组件使用本对象的 M3 token 常量——两者数值同源，节奏一致。
 * 待上游转公开后，只需在 `WuzhuTheme` 里补一行 `motionScheme = MotionScheme.expressive()`。
 */
