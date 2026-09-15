package com.wuzhufolio.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 窗口宽度档（P6 人工门第七轮 · DEF-31「总体应对分辨率变化」的基础设施）。
 *
 * 桌面端窗口可自由缩放（人工实测覆盖 1024×768 / 1280×800 / 2560×1600 三档，含高 DPI 缩放后的
 * **有效 dp 宽度**可能远小于物理分辨率）。此前各页面各自用 `weight` 硬扛，窗口一窄就压扁文字
 * （换行/竖排/截断），属于「小修小补」。现确立**单一断点来源**：布局按 [WzWindowClass] 决定
 * 列宽策略、字号策略与弹窗尺寸，不再各处自行判断。
 *
 * 断点口径（按**内容区**有效宽度 dp，即扣掉侧边栏后的窗口宽度）：
 * - [COMPACT] < 1200dp（≈1024×768、或 2560×1600 @200% 缩放）：表格横向滚动、卡片数字自动缩字号；
 * - [MEDIUM] < 1760dp（≈1280×800、1920×1080）：表格按最小宽比例铺满，不出现横向滚动；
 * - [WIDE] ≥ 1760dp（≈2560×1600）：同上，卡片与表格留白更充裕。
 */
enum class WzWindowClass {
    COMPACT,
    MEDIUM,
    WIDE,
}

/** 内容区宽度断点（dp）。 */
object WzBreakpoints {
    val COMPACT_MAX: Dp = 1200.dp
    val MEDIUM_MAX: Dp = 1760.dp

    fun of(contentWidth: Dp): WzWindowClass = when {
        contentWidth < COMPACT_MAX -> WzWindowClass.COMPACT
        contentWidth < MEDIUM_MAX -> WzWindowClass.MEDIUM
        else -> WzWindowClass.WIDE
    }
}

val LocalWzWindowClass = staticCompositionLocalOf { WzWindowClass.MEDIUM }

/** 当前窗口档（便于在纯逻辑处读取；无宿主时按 MEDIUM 处理）。 */
@Immutable
data class WzWindowInfo(val windowClass: WzWindowClass)

/**
 * 在**页面容器**外层提供窗口档：以可用宽度换算 [WzWindowClass]，供表格/卡片/弹窗统一读取。
 * 放在 `MainShell` 的页面槽外层，任何页面内都能通过 [LocalWzWindowClass] 拿到同一档位。
 */
@Composable
fun ProvideWzWindowClass(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val windowClass = remember(maxWidth) { WzBreakpoints.of(maxWidth) }
        CompositionLocalProvider(LocalWzWindowClass provides windowClass) { content() }
    }
}
