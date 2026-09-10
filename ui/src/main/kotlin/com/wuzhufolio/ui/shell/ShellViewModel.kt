package com.wuzhufolio.ui.shell

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主壳一级页面（ia.md §1：侧边栏六个一级入口 + M0 开发期组件走查页）。
 */
enum class ShellPage(val label: String) {
    DASHBOARD("仪表盘"),
    ASSETS("资产列表"),
    TRANSACTIONS("交易管理"),
    FUNDS("资金管理"),
    /** D21 行情浏览页（2026-09-04 人工拍板增补，docs/dev/decisions/D21）。 */
    QUOTES("行情"),
    SETTINGS("设置"),

    /** M0 组件走查页（T0.6 验收载体；P4 起仅开发构建可见）。 */
    GALLERY("组件走查"),
    ;

    companion object {
        /** 侧边栏正式六页（顺序 = ia.md；行情页为 D21 增补）。 */
        val sidebarPages: List<ShellPage> = listOf(DASHBOARD, ASSETS, TRANSACTIONS, FUNDS, QUOTES, SETTINGS)
    }
}

/**
 * 主壳状态（ADR-001：ViewModel + StateFlow）。
 * 主题/盈亏配色 = 单一状态源：顶栏 ☾ 快捷切换与设置页「主题（明/暗）」双向同步（PRD 6.1）；
 * 变更经 [onPreferenceChange] 异步持久化（M10：settings 全局行 theme / pnl_scheme，键与启动链同源）。
 */
class ShellViewModel(
    initialTheme: ThemeMode,
    initialPnlScheme: PnlColorScheme,
    initialPage: ShellPage = ShellPage.DASHBOARD,
    /** 偏好持久化钩子（key/value；实现 = settings 全局行写入，注入自 app 组合根）。 */
    private val onPreferenceChange: suspend (key: String, value: String) -> Unit = { _, _ -> },
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _themeMode = MutableStateFlow(initialTheme)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _pnlScheme = MutableStateFlow(initialPnlScheme)
    val pnlScheme: StateFlow<PnlColorScheme> = _pnlScheme.asStateFlow()

    private val _page = MutableStateFlow(initialPage)
    val page: StateFlow<ShellPage> = _page.asStateFlow()

    private val _toast = MutableStateFlow<WzToast?>(null)
    val toast: StateFlow<WzToast?> = _toast.asStateFlow()

    fun toggleTheme() {
        setTheme(if (_themeMode.value == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT)
    }

    /** 设置主题（顶栏/设置页共用入口；即时重渲染 + 持久化）。 */
    fun setTheme(mode: ThemeMode) {
        _themeMode.value = mode
        scope.launch { onPreferenceChange("theme", mode.storageValue) }
    }

    /** 设置盈亏配色方案（设置页入口；即时重渲染 + 持久化）。 */
    fun setPnlScheme(scheme: PnlColorScheme) {
        _pnlScheme.value = scheme
        scope.launch { onPreferenceChange("pnl_scheme", scheme.storageValue) }
    }

    fun selectPage(page: ShellPage) {
        _page.value = page
    }

    fun showToast(kind: WzToastKind, message: String) {
        _toast.value = WzToast(kind, message)
    }

    fun dismissToast() {
        _toast.value = null
    }

    override fun onCleared() {
        scope.cancel()
    }
}
