package com.wuzhufolio.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.wuzhufolio.domain.settings.PnlColorScheme

/**
 * design-tokens.md §2 的单源 Compose 映射（F4 修正色值已内置，T0.6）。
 * 改色只改这里；对比度由 ContrastTest 双主题回归守护（两主题 >=4.5:1）。
 */
@Immutable
data class WzColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val accent: Color,
    val accentInk: Color,
    val gain: Color,
    val loss: Color,
    val warn: Color,
    val line: Color,
)

/** 主版 暖纸浅色（design-tokens §2.1）。 */
fun lightWzColors(): WzColors = WzColors(
    bg = Color(0xFFF6F4EF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEDE9E1),
    ink = Color(0xFF1E2622),
    ink2 = Color(0xFF5C655F),
    ink3 = Color(0xFF68716A),
    accent = Color(0xFF1F5A48),
    accentInk = Color(0xFFF6F4EF),
    gain = Color(0xFF17663F),
    loss = Color(0xFFA93A37),
    warn = Color(0xFF8A6416),
    line = Color(0xFFD9D4CA),
)

/** 内置档位 墨炭深色（design-tokens §2.2，同一真源的暗色主题）。 */
fun darkWzColors(): WzColors = WzColors(
    bg = Color(0xFF151A18),
    surface = Color(0xFF1D2421),
    surface2 = Color(0xFF27302C),
    ink = Color(0xFFE8EAE6),
    ink2 = Color(0xFF9AA69F),
    ink3 = Color(0xFF8A938C),
    accent = Color(0xFFD0A85C),
    accentInk = Color(0xFF151A18),
    gain = Color(0xFF4CBF82),
    loss = Color(0xFFE07873),
    warn = Color(0xFFD0A85C),
    line = Color(0xFF2E3733),
)

/** 应用盈亏配色方案（design-tokens §2.3）：返回 gain/loss 调整后的色板。 */
fun WzColors.withPnlScheme(scheme: PnlColorScheme): WzColors = when (scheme) {
    PnlColorScheme.GREEN_UP -> this
    PnlColorScheme.RED_UP -> copy(gain = loss, loss = gain)
    PnlColorScheme.COLORBLIND -> copy(gain = Color(0xFF3B6FD4), loss = Color(0xFFC77B28))
}
