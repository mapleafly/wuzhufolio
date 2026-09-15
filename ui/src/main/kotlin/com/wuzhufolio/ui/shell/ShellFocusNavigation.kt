package com.wuzhufolio.ui.shell

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.wuzhufolio.ui.components.WzOverlayRegistry

/** 侧边栏导航项顺序（与渲染顺序一致：六个一级页 + 组件走查页）；↑/↓ 与「焦点回外壳」按此顺序。 */
/**
 * 主壳键盘焦点编排（2026-09-15 Windows 人工走查第 4 轮反馈，DEF-20 / 走查改进项 A）。
 *
 * 目标：让「纯键盘」在主壳里一次 Enter 到位，而不是每换一页都要 Tab 穿过侧边栏与顶栏：
 * 1. 回车选中导航项 → 焦点**直接进入页面内容**（由 [MainShell] 的页面槽 `focusRequester` 请求，
 *    Compose 语义：请求挂在非可聚焦容器上时，焦点落到子树内**第一个可聚焦控件**）；
 * 2. 页面内未被控件消费的 Esc / ↑ / ↓ → 焦点交回侧边栏当前页项，回到「侧边栏→顶栏→页面」外壳循环；
 * 3. 侧边栏内 ↑ / ↓ 在导航项之间移动。
 *
 * 三条护栏（缺一不可）：
 * - 页面退出用**冒泡阶段**（`onKeyEvent`）而非 preview：输入框光标移动、下拉/列表导航等已消费的方向键不受影响；
 * - **弹层打开时不接管**（[WzOverlayRegistry]）：否则焦点会跑到弹层背后，弹层还开着而键盘已无法操作；
 * - 带 Ctrl/Alt/Meta 的组合键不接管（留给 P8 全局快捷键，见键盘走查改进项 B）。
 */
internal val NAV_FOCUS_ORDER: List<ShellPage> = ShellPage.sidebarPages + ShellPage.GALLERY

/**
 * 页面内容区键盘「退出」：把未被页面控件消费的 Esc / ↑ / ↓ 交回侧边栏当前页项。
 *
 * 用 ↑/↓ 而非只认 Esc：方向键在外壳语义里就是「上下移动焦点」，与侧边栏内的 ↑/↓ 一致；
 * 回到侧边栏后继续按 ↑/↓ 即可在导航项间走（见 [handleSidebarArrowKey]）。
 */
internal fun handlePageExitKey(
    event: KeyEvent,
    page: ShellPage,
    navFocusRequesters: Map<ShellPage, FocusRequester>,
): Boolean {
    val isPlainExitKey = event.type == KeyEventType.KeyDown &&
        !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed &&
        (event.key == Key.Escape || event.key == Key.DirectionUp || event.key == Key.DirectionDown)
    val target = navFocusRequesters[page].takeIf { isPlainExitKey && WzOverlayRegistry.openModalCount == 0 }
    if (target == null) return false
    runCatching { target.requestFocus() }
    return true
}

/** 侧边栏 ↑/↓：在导航项之间移动焦点（焦点不在导航项上时返回 false，事件继续冒泡）。 */
internal fun handleSidebarArrowKey(
    event: KeyEvent,
    focusedPage: ShellPage?,
    navFocusRequesters: Map<ShellPage, FocusRequester>,
): Boolean {
    val step = sidebarArrowStep(event)
    val index = focusedPage?.let(NAV_FOCUS_ORDER::indexOf) ?: -1
    val target = if (step != null && index >= 0) {
        navFocusRequesters[NAV_FOCUS_ORDER.getOrNull(index + step)]
    } else {
        null
    }
    if (target == null) return false
    runCatching { target.requestFocus() }
    return true
}

private fun sidebarArrowStep(event: KeyEvent): Int? = if (event.type == KeyEventType.KeyDown) {
    when (event.key) {
        Key.DirectionDown -> 1
        Key.DirectionUp -> -1
        else -> null
    }
} else {
    null
}
