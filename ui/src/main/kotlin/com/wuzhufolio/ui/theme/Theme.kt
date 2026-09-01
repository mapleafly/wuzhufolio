package com.wuzhufolio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode

val LocalWzColors = compositionLocalOf { lightWzColors() }
val LocalWzTypography = compositionLocalOf { wzTypography() }

/** 组件取 token 的唯一入口（单一真源原则：组件一律经主题取色，不写死色值）。 */
object WzTheme {
    val colors: WzColors
        @Composable get() = LocalWzColors.current
    val typography: WzTypography
        @Composable get() = LocalWzTypography.current
}

/**
 * WuZhuFolio 主题（T0.6）：明/暗双主题 + 盈亏配色方案。
 * MaterialTheme 仅作 M3 控件基座映射，业务组件一律取 [WzTheme]。
 */
@Composable
fun WuzhuTheme(
    themeMode: ThemeMode,
    pnlScheme: PnlColorScheme = PnlColorScheme.GREEN_UP,
    content: @Composable () -> Unit,
) {
    val base = if (themeMode == ThemeMode.DARK) darkWzColors() else lightWzColors()
    val colors = base.withPnlScheme(pnlScheme)
    val typography = wzTypography()

    val m3Colors = if (themeMode == ThemeMode.DARK) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentInk,
            background = colors.bg,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.ink2,
            outline = colors.line,
            error = colors.loss,
            onError = colors.accentInk,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentInk,
            background = colors.bg,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.ink2,
            outline = colors.line,
            error = colors.loss,
            onError = colors.accentInk,
        )
    }

    CompositionLocalProvider(
        LocalWzColors provides colors,
        LocalWzTypography provides typography,
    ) {
        MaterialTheme(colorScheme = m3Colors, content = content)
    }
}
