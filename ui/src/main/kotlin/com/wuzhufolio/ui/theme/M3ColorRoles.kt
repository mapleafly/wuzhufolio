package com.wuzhufolio.ui.theme

import androidx.compose.ui.graphics.Color

// ⚠️ 本文件由 scripts/generate-m3-color-scheme.mjs 生成（种子色 #1F5A48）——**不要手改**。
// 改色 = 改脚本里的 SEED 并重跑；语义层映射（WzColors 的用途）见 ColorTokens.kt。
// 算法：Google material-color-utilities 0.4.0 · SchemeTonalSpot（M3 基线方案）+ TonalPalette（语义扩展色）。
//
// 生成期对比度自检（WCAG 2.1，AA 正文 ≥ 4.5:1）：
//   ✅ light onSurface/surface: 16.31:1
//   ✅ light onSurfaceVariant/surface: 8.88:1
//   ✅ light gain/surface: 6.17:1
//   ✅ light loss/surface: 6.18:1
//   ✅ light warn/surface: 6.15:1
//   ✅ light onPrimary/primary: 6.43:1
//   ✅ dark onSurface/surface: 14.32:1
//   ✅ dark onSurfaceVariant/surface: 10.88:1
//   ✅ dark gain/surface: 10.81:1
//   ✅ dark loss/surface: 10.83:1
//   ✅ dark warn/surface: 10.88:1
//   ✅ dark onPrimary/primary: 7.74:1
//   ✅ light ink3/surface: 5.37:1
//   ✅ light ink3/surface: 5.11:1
//   ✅ dark ink3/surface: 5.40:1
//   ✅ dark ink3/surface: 5.83:1
//   ✅ light gain/card: 6.47:1
//   ✅ light loss/card: 6.48:1
//   ✅ light warn/card: 6.45:1
//   ✅ dark gain/card: 10.01:1
//   ✅ dark loss/card: 10.03:1
//   ✅ dark warn/card: 10.07:1

/** M3LightRoles。 */
internal object M3LightRoles {
    val primary: Color = Color(0xFF166B54)
    val onPrimary: Color = Color(0xFFFFFFFF)
    val primaryContainer: Color = Color(0xFFA4F2D5)
    val onPrimaryContainer: Color = Color(0xFF00513E)
    val inversePrimary: Color = Color(0xFF89D6B9)
    val secondary: Color = Color(0xFF4C635A)
    val onSecondary: Color = Color(0xFFFFFFFF)
    val secondaryContainer: Color = Color(0xFFCEE9DC)
    val onSecondaryContainer: Color = Color(0xFF344C42)
    val tertiary: Color = Color(0xFF406375)
    val onTertiary: Color = Color(0xFFFFFFFF)
    val tertiaryContainer: Color = Color(0xFFC3E8FE)
    val onTertiaryContainer: Color = Color(0xFF274B5D)
    val error: Color = Color(0xFFBA1A1A)
    val onError: Color = Color(0xFFFFFFFF)
    val errorContainer: Color = Color(0xFFFFDAD6)
    val onErrorContainer: Color = Color(0xFF93000A)
    val background: Color = Color(0xFFF5FBF6)
    val onBackground: Color = Color(0xFF171D1A)
    val surface: Color = Color(0xFFF5FBF6)
    val onSurface: Color = Color(0xFF171D1A)
    val surfaceVariant: Color = Color(0xFFDBE5DF)
    val onSurfaceVariant: Color = Color(0xFF404944)
    val surfaceContainerLowest: Color = Color(0xFFFFFFFF)
    val surfaceContainerLow: Color = Color(0xFFEFF5F0)
    val surfaceContainer: Color = Color(0xFFE9EFEA)
    val surfaceContainerHigh: Color = Color(0xFFE4EAE5)
    val surfaceContainerHighest: Color = Color(0xFFDEE4DF)
    val surfaceDim: Color = Color(0xFFD5DBD7)
    val surfaceBright: Color = Color(0xFFF5FBF6)
    val outline: Color = Color(0xFF707974)
    val outlineVariant: Color = Color(0xFFBFC9C3)
    val inverseSurface: Color = Color(0xFF2C322F)
    val inverseOnSurface: Color = Color(0xFFECF2ED)
    val scrim: Color = Color(0xFF000000)
    val gain: Color = Color(0xFF1E6B45)
    val loss: Color = Color(0xFFA63835)
    val warn: Color = Color(0xFF7C5807)
    val ink3: Color = Color(0xFF636D67)
}

/** M3DarkRoles。 */
internal object M3DarkRoles {
    val primary: Color = Color(0xFF89D6B9)
    val onPrimary: Color = Color(0xFF00382A)
    val primaryContainer: Color = Color(0xFF00513E)
    val onPrimaryContainer: Color = Color(0xFFA4F2D5)
    val inversePrimary: Color = Color(0xFF166B54)
    val secondary: Color = Color(0xFFB2CCC0)
    val onSecondary: Color = Color(0xFF1E352C)
    val secondaryContainer: Color = Color(0xFF344C42)
    val onSecondaryContainer: Color = Color(0xFFCEE9DC)
    val tertiary: Color = Color(0xFFA7CCE1)
    val onTertiary: Color = Color(0xFF0B3445)
    val tertiaryContainer: Color = Color(0xFF274B5D)
    val onTertiaryContainer: Color = Color(0xFFC3E8FE)
    val error: Color = Color(0xFFFFB4AB)
    val onError: Color = Color(0xFF690005)
    val errorContainer: Color = Color(0xFF93000A)
    val onErrorContainer: Color = Color(0xFFFFDAD6)
    val background: Color = Color(0xFF0F1512)
    val onBackground: Color = Color(0xFFDEE4DF)
    val surface: Color = Color(0xFF0F1512)
    val onSurface: Color = Color(0xFFDEE4DF)
    val surfaceVariant: Color = Color(0xFF404944)
    val onSurfaceVariant: Color = Color(0xFFBFC9C3)
    val surfaceContainerLowest: Color = Color(0xFF0A0F0D)
    val surfaceContainerLow: Color = Color(0xFF171D1A)
    val surfaceContainer: Color = Color(0xFF1B211E)
    val surfaceContainerHigh: Color = Color(0xFF252B28)
    val surfaceContainerHighest: Color = Color(0xFF303633)
    val surfaceDim: Color = Color(0xFF0F1512)
    val surfaceBright: Color = Color(0xFF343B37)
    val outline: Color = Color(0xFF89938E)
    val outlineVariant: Color = Color(0xFF404944)
    val inverseSurface: Color = Color(0xFFDEE4DF)
    val inverseOnSurface: Color = Color(0xFF2C322F)
    val scrim: Color = Color(0xFF000000)
    val gain: Color = Color(0xFF8BD6A8)
    val loss: Color = Color(0xFFFFB3AD)
    val warn: Color = Color(0xFFF0BF69)
    val ink3: Color = Color(0xFF89938E)
}

