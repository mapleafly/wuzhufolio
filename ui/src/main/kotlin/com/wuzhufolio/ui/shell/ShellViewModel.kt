package com.wuzhufolio.ui.shell

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主壳一级页面（ia.md §1：侧边栏五个一级入口 + M0 开发期组件走查页）。
 */
enum class ShellPage(val label: String) {
    DASHBOARD("仪表盘"),
    ASSETS("资产列表"),
    TRANSACTIONS("交易管理"),
    FUNDS("资金管理"),
    SETTINGS("设置"),

    /** M0 组件走查页（T0.6 验收载体；P4 起仅开发构建可见）。 */
    GALLERY("组件走查"),
    ;

    companion object {
        /** 侧边栏正式五页（顺序 = ia.md）。 */
        val sidebarPages: List<ShellPage> = listOf(DASHBOARD, ASSETS, TRANSACTIONS, FUNDS, SETTINGS)
    }
}

/** 主壳状态（ADR-001：ViewModel + StateFlow）。 */
class ShellViewModel(
    initialTheme: ThemeMode,
    initialPnlScheme: PnlColorScheme,
    initialPage: ShellPage = ShellPage.DASHBOARD,
) : ViewModel() {

    private val _themeMode = MutableStateFlow(initialTheme)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _pnlScheme = MutableStateFlow(initialPnlScheme)
    val pnlScheme: StateFlow<PnlColorScheme> = _pnlScheme.asStateFlow()

    private val _page = MutableStateFlow(initialPage)
    val page: StateFlow<ShellPage> = _page.asStateFlow()

    private val _toast = MutableStateFlow<WzToast?>(null)
    val toast: StateFlow<WzToast?> = _toast.asStateFlow()

    fun toggleTheme() {
        _themeMode.value = if (_themeMode.value == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
    }

    fun setPnlScheme(scheme: PnlColorScheme) {
        _pnlScheme.value = scheme
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
}
