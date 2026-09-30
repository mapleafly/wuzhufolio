package com.wuzhufolio.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Material 3 `ColorScheme` 装配（ADR-007 §2.2 / D38）。
 *
 * **D38 变更（2026-09-28）**：配色不再由自建 token "翻译" 成 M3 role，而是**直接用 M3 官方算法生成的 role**
 * （[M3LightRoles] / [M3DarkRoles]，种子色 = 品牌墨绿 `#1F5A48`，算法 = `SchemeTonalSpot`）——
 * 这是「采用 M3 配色规范」的实质：主色/容器色/表面色阶/描边/反色容器全部落在 M3 色调板上，
 * 而不是"看起来像 M3 的手写色"。
 *
 * 两处**产品语义扩展**（M3 规范之外，显式声明）：
 * - `gain` / `loss` / `warn`：盈亏与警示语义（M3 只有 `error`）。取同一套色调板算法（浅色 tone 40 / 暗色 tone 80），
 *   与主色同族质感；对比度由生成期自检（`M3ColorRoles.kt` 头注）+ `ContrastTest` 双重守护。
 * - `error` role **不再**随盈亏配色方案翻转（此前 `error = loss`，切到「红涨绿跌」时表单校验红字会变绿——语义错误）。
 */
internal fun wzColorScheme(colors: WzColors, dark: Boolean): ColorScheme {
    // 两套 scheme 仅在「哪一组 role」上不同，故用取值器表达，避免 30 行 if/else
    fun role(light: androidx.compose.ui.graphics.Color, darkRole: androidx.compose.ui.graphics.Color) =
        if (dark) darkRole else light

    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        // 主色与「主色上的文字」来自语义层（可被盈亏配色方案等上层口径影响）
        primary = colors.accent,
        onPrimary = colors.accentInk,
        primaryContainer = role(M3LightRoles.primaryContainer, M3DarkRoles.primaryContainer),
        onPrimaryContainer = role(M3LightRoles.onPrimaryContainer, M3DarkRoles.onPrimaryContainer),
        inversePrimary = role(M3LightRoles.inversePrimary, M3DarkRoles.inversePrimary),
        secondary = role(M3LightRoles.secondary, M3DarkRoles.secondary),
        onSecondary = role(M3LightRoles.onSecondary, M3DarkRoles.onSecondary),
        secondaryContainer = role(M3LightRoles.secondaryContainer, M3DarkRoles.secondaryContainer),
        onSecondaryContainer = role(M3LightRoles.onSecondaryContainer, M3DarkRoles.onSecondaryContainer),
        tertiary = role(M3LightRoles.tertiary, M3DarkRoles.tertiary),
        onTertiary = role(M3LightRoles.onTertiary, M3DarkRoles.onTertiary),
        tertiaryContainer = role(M3LightRoles.tertiaryContainer, M3DarkRoles.tertiaryContainer),
        onTertiaryContainer = role(M3LightRoles.onTertiaryContainer, M3DarkRoles.onTertiaryContainer),
        background = role(M3LightRoles.background, M3DarkRoles.background),
        onBackground = role(M3LightRoles.onBackground, M3DarkRoles.onBackground),
        surface = role(M3LightRoles.surface, M3DarkRoles.surface),
        onSurface = role(M3LightRoles.onSurface, M3DarkRoles.onSurface),
        surfaceVariant = role(M3LightRoles.surfaceVariant, M3DarkRoles.surfaceVariant),
        onSurfaceVariant = role(M3LightRoles.onSurfaceVariant, M3DarkRoles.onSurfaceVariant),
        // 表面色阶（M3 用容器色阶表达层级，不再靠阴影）
        surfaceContainerLowest = role(M3LightRoles.surfaceContainerLowest, M3DarkRoles.surfaceContainerLowest),
        surfaceContainerLow = role(M3LightRoles.surfaceContainerLow, M3DarkRoles.surfaceContainerLow),
        surfaceContainer = role(M3LightRoles.surfaceContainer, M3DarkRoles.surfaceContainer),
        surfaceContainerHigh = role(M3LightRoles.surfaceContainerHigh, M3DarkRoles.surfaceContainerHigh),
        surfaceContainerHighest = role(M3LightRoles.surfaceContainerHighest, M3DarkRoles.surfaceContainerHighest),
        surfaceDim = role(M3LightRoles.surfaceDim, M3DarkRoles.surfaceDim),
        surfaceBright = role(M3LightRoles.surfaceBright, M3DarkRoles.surfaceBright),
        inverseSurface = role(M3LightRoles.inverseSurface, M3DarkRoles.inverseSurface),
        inverseOnSurface = role(M3LightRoles.inverseOnSurface, M3DarkRoles.inverseOnSurface),
        // 错误语义固定取 M3 error role（不随盈亏配色方案翻转）
        error = role(M3LightRoles.error, M3DarkRoles.error),
        onError = role(M3LightRoles.onError, M3DarkRoles.onError),
        errorContainer = role(M3LightRoles.errorContainer, M3DarkRoles.errorContainer),
        onErrorContainer = role(M3LightRoles.onErrorContainer, M3DarkRoles.onErrorContainer),
        outline = role(M3LightRoles.outline, M3DarkRoles.outline),
        outlineVariant = role(M3LightRoles.outlineVariant, M3DarkRoles.outlineVariant),
        scrim = role(M3LightRoles.scrim, M3DarkRoles.scrim),
    )
}
