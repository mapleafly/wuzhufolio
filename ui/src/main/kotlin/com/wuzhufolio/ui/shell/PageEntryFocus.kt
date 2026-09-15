package com.wuzhufolio.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * 页面入口焦点（P6 人工门第六轮 · DEF-27）。
 *
 * **为什么需要**：切页时主壳要把焦点送进页面内容（DEF-21），原实现是在「页面容器」上挂 `FocusRequester`，
 * 依赖 Compose 的**子树遍历**去猜第一个可聚焦控件。实测该遍历**不保证按 Tab 序**：
 * 设置页 Tab 序首项是「基础法币」（`fiat-select`），但遍历落到了第 22 个停靠点「手续费 → 全局默认费率 → 买入费率」
 * ——表现即人工反馈的「打开设置页焦点跑到中间的手续费输入框，页面滚动到中部」。
 * 另外页面自身也要抢焦点时（如行情页打开即聚焦搜索框）会与主壳兜底请求互相打架。
 *
 * **口径**：页面把 [Modifier.pageEntryFocus] 附到**首个可聚焦控件**上，主壳切页时**优先**请求它；
 * 页面未声明（或控件已随页面销毁）时回落到容器遍历（短页面可用）。声明用普通字段记录（非快照状态），
 * 组合期同步登记、不触发额外重组；主壳在副作用阶段读取。
 */
@Stable
class PageEntryFocusState {
    internal val requester = FocusRequester()
    private var claimed = false

    /** 页面首个可聚焦控件调用：声明「本页自管入口焦点」。 */
    internal fun claim(): FocusRequester {
        claimed = true
        return requester
    }

    /** 是否有页面声明过入口焦点（声明后长期有效；页面销毁后请求会自然失败并回落容器）。 */
    internal fun hasClaim(): Boolean = claimed
}

/** 主壳在页面槽外层提供的入口焦点宿主；独立渲染页面（单测/预览）时为 null。 */
val LocalPageEntryFocus = staticCompositionLocalOf<PageEntryFocusState?> { null }

/**
 * 把「页面入口焦点」附加到页面的**首个可聚焦控件**上（无宿主时为空操作，不影响独立使用）。
 *
 * 用法：`WzTextField(..., modifier = Modifier.pageEntryFocus())` / `WzSelect(modifier = Modifier.pageEntryFocus())`。
 * 注意必须加在该控件的**其它焦点修饰符之前**（Compose 焦点事件只向链上更外层上报）。
 */
@Composable
fun Modifier.pageEntryFocus(): Modifier {
    val state = LocalPageEntryFocus.current ?: return this
    return focusRequester(state.claim())
}
