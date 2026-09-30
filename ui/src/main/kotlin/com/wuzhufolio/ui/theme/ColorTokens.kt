package com.wuzhufolio.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.wuzhufolio.domain.settings.PnlColorScheme

/**
 * 语义色层（组件取色的**唯一入口**，`design-tokens.md §2`）。
 *
 * **D38 变更（2026-09-28）**：本层不再手写 hex，而是**投影 Material 3 配色方案的 role**
 * （生成物见 [M3LightRoles] / [M3DarkRoles]，由 `scripts/generate-m3-color-scheme.mjs`
 * 用 Google 官方 `material-color-utilities` 从品牌种子色 `#1F5A48` 生成）。
 *
 * 为什么保留这一层而不是让组件直接用 `MaterialTheme.colorScheme`：
 * ① 12 个语义槽位（bg/surface/ink/gain/loss/…）比 M3 的 30+ role 更贴合业务表达，
 *    100+ 处既有调用点零改动；
 * ② `gain/loss/warn` 是 M3 **没有**的语义 role（盈亏/警示），必须自建并与主色同族质感；
 * ③ 单一真源收敛在此：M3 role → 语义槽位的映射只有这一张表。
 *
 * 对比度由 `ContrastTest`（双主题 × 多背景）守护；生成期另有自检（见 M3ColorRoles.kt 头注）。
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

/**
 * 主版（M3 浅色方案）：应用底 = `surface`；卡片/表格底 = `surfaceContainerLowest`（更亮，形成层级）；
 * 次表面 = `surfaceContainerHigh`。
 */
fun lightWzColors(): WzColors = WzColors(
    bg = M3LightRoles.surface,
    surface = M3LightRoles.surfaceContainerLowest,
    surface2 = M3LightRoles.surfaceContainerHigh,
    ink = M3LightRoles.onSurface,
    ink2 = M3LightRoles.onSurfaceVariant,
    ink3 = M3LightRoles.ink3,
    accent = M3LightRoles.primary,
    accentInk = M3LightRoles.onPrimary,
    gain = M3LightRoles.gain,
    loss = M3LightRoles.loss,
    warn = M3LightRoles.warn,
    line = M3LightRoles.outlineVariant,
)

/** 深色档（M3 深色方案）：暗色下卡片比应用底**更亮**（`surfaceContainerLow` > `surface`），层级方向相反。 */
fun darkWzColors(): WzColors = WzColors(
    bg = M3DarkRoles.surface,
    surface = M3DarkRoles.surfaceContainerLow,
    surface2 = M3DarkRoles.surfaceContainerHigh,
    ink = M3DarkRoles.onSurface,
    ink2 = M3DarkRoles.onSurfaceVariant,
    ink3 = M3DarkRoles.ink3,
    accent = M3DarkRoles.primary,
    accentInk = M3DarkRoles.onPrimary,
    gain = M3DarkRoles.gain,
    loss = M3DarkRoles.loss,
    warn = M3DarkRoles.warn,
    line = M3DarkRoles.outlineVariant,
)

/**
 * 盈亏配色方案（PRD §6 无障碍基线 · design-tokens §2.3）：返回 gain/loss 调整后的色板。
 * 三档均为**语义色扩展**（M3 无 gain/loss role），色盲档取蓝/橙。
 */
fun WzColors.withPnlScheme(scheme: PnlColorScheme): WzColors = when (scheme) {
    PnlColorScheme.GREEN_UP -> this
    PnlColorScheme.RED_UP -> copy(gain = loss, loss = gain)
    PnlColorScheme.COLORBLIND -> copy(gain = Color(0xFF3B6FD4), loss = Color(0xFFC77B28))
}
