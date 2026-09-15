package com.wuzhufolio.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 页面级叠加槽（P6 人工门第五轮 · DEF-22 修复）。
 *
 * **为什么需要它**：`WzModal` 是**就地叠加层**（`AGENTS.md §7.3`：桌面端弹层一律同窗口叠加，不用 Popup），
 * 它靠根 `Box(Modifier.fillMaxSize())` 覆盖整个页面。但若把它放在 `verticalScroll` 容器**内部**的子组件里，
 * 该处的**高度约束是无限的**——`fillMaxSize()`/`fillMaxHeight()` 在无限约束下无法撑开，只能退化为内容高度，
 * 于是弹层变成滚动列里的**一个普通块**：表现为「撑开页面 / 把后面内容挤下去 / 浮在某个区域上看着错位」
 * （2026-09-15 人工门第五轮：API 添加弹窗、行情数据源弹窗、恢复数据弹窗）。
 *
 * **用法**：页面根放 [PageOverlayHost] 包住滚动内容，深层子组件用 [PageOverlay] 提交弹层——
 * 弹层由宿主在**页面根**渲染（有限约束，铺满页面、居中、盖住全部内容）。
 * 无宿主时（独立组件预览/单测直接渲染某个 section）[PageOverlay] 退化为**就地渲染**，保持旧行为可用。
 */
private val LocalPageOverlay = staticCompositionLocalOf<MutableList<OverlaySlot>?> { null }

/** 弹层槽位：内容用可变状态承载，宿主读取时随内容更新重组（弹层的内部状态变化能即时反映）。 */
class OverlaySlot internal constructor() {
    internal var content: (@Composable () -> Unit)? by mutableStateOf(null)

    override fun toString(): String = "OverlaySlot@" + hashCode()
}

/**
 * 页面级叠加宿主：渲染 [content]（页面正文），并把它内部通过 [PageOverlay] 提交的弹层**叠加在页面之上**。
 *
 * @param modifier 宿主自身尺寸（页面级用法传 `Modifier.fillMaxSize()`，使弹层铺满页面）。
 */
@Composable
fun PageOverlayHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val slots = remember { mutableStateListOf<OverlaySlot>() }
    Box(modifier = modifier) {
        CompositionLocalProvider(LocalPageOverlay provides slots) { content() }
        // 注册顺序即绘制顺序：后提交的弹层在更上层（同时最多只有一个交互弹层）
        slots.forEach { slot ->
            key(slot) { slot.content?.invoke() }
        }
    }
}

/**
 * 提交一个页面级弹层。有 [PageOverlayHost] 时挂到页面根（不挤占滚动内容）；无宿主时就地渲染（兼容单测/独立预览）。
 */
@Composable
fun PageOverlay(content: @Composable () -> Unit) {
    val slots = LocalPageOverlay.current
    if (slots == null) {
        content()
        return
    }
    val slot = remember { OverlaySlot() }
    SideEffect { slot.content = content }
    DisposableEffect(slots, slot) {
        slots.add(slot)
        onDispose {
            slot.content = null
            slots.remove(slot)
        }
    }
}
