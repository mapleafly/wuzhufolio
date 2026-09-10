package com.wuzhufolio.ui.shell

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import com.wuzhufolio.ui.i18n.shellStrings
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
 * 标签为**动态取值**（M12 T12.4：切换语言即时更新，见 ui/i18n/ShellStrings.kt）。
 */
enum class ShellPage {
    DASHBOARD,
    ASSETS,
    TRANSACTIONS,
    FUNDS,
    /** D21 行情浏览页（2026-09-04 人工拍板增补，docs/dev/decisions/D21）。 */
    QUOTES,
    SETTINGS,

    /** M0 组件走查页（T0.6 验收载体；P4 起仅开发构建可见）。 */
    GALLERY,
    ;

    val label: String
        get() = when (this) {
            DASHBOARD -> shellStrings.navDashboard
            ASSETS -> shellStrings.navAssets
            TRANSACTIONS -> shellStrings.navTransactions
            FUNDS -> shellStrings.navFunds
            QUOTES -> shellStrings.navQuotes
            SETTINGS -> shellStrings.navSettings
            GALLERY -> shellStrings.navGallery
        }

    companion object {
        /** 侧边栏正式六页（顺序 = ia.md；行情页为 D21 增补）。 */
        val sidebarPages: List<ShellPage> = listOf(DASHBOARD, ASSETS, TRANSACTIONS, FUNDS, QUOTES, SETTINGS)
    }
}

/**
 * 主壳状态（ADR-001：ViewModel + StateFlow）。
 * 主题/盈亏配色/界面语言 = 单一状态源：顶栏 ☾ 快捷切换与设置页「主题（明/暗）」双向同步（PRD 6.1），
 * 语言自设置页「通用 → 界面语言」切换（M12 T12.4）；变更经 [onPreferenceChange] 异步持久化
 * （settings 全局行 theme / pnl_scheme / ui.language，键与启动链同源）。
 *
 * 币种资产详情导航（M12 T12.1）：[openCoinDetail] 在「资产列表」页内切换到详情子页，
 * [closeCoinDetail] 返回列表；切走一级页面时自动清空（避免回来时停在旧详情）。
 */
class ShellViewModel(
    initialTheme: ThemeMode,
    initialPnlScheme: PnlColorScheme,
    initialPage: ShellPage = ShellPage.DASHBOARD,
    initialLanguage: AppLanguage = AppLanguage.ZH,
    /** 偏好持久化钩子（key/value；实现 = settings 全局行写入，注入自 app 组合根）。 */
    private val onPreferenceChange: suspend (key: String, value: String) -> Unit = { _, _ -> },
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _themeMode = MutableStateFlow(initialTheme)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _pnlScheme = MutableStateFlow(initialPnlScheme)
    val pnlScheme: StateFlow<PnlColorScheme> = _pnlScheme.asStateFlow()

    private val _language = MutableStateFlow(initialLanguage)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _page = MutableStateFlow(initialPage)
    val page: StateFlow<ShellPage> = _page.asStateFlow()

    /** 资产列表 → 币种详情子页（null = 列表态）。 */
    private val _coinDetailId = MutableStateFlow<String?>(null)
    val coinDetailId: StateFlow<String?> = _coinDetailId.asStateFlow()

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

    /** 设置界面语言（M12 T12.4：即时切换全部文案与日期格式 + 持久化）。 */
    fun setLanguage(language: AppLanguage) {
        _language.value = language
        scope.launch { onPreferenceChange(AppLanguage.SETTINGS_KEY, language.storageValue) }
    }

    fun selectPage(page: ShellPage) {
        _page.value = page
        _coinDetailId.value = null
    }

    /** 打开币种资产详情（ia.md §2.6 入口 = 资产列表单击行）。 */
    fun openCoinDetail(cgId: String) {
        _page.value = ShellPage.ASSETS
        _coinDetailId.value = cgId
    }

    /** 返回资产列表。 */
    fun closeCoinDetail() {
        _coinDetailId.value = null
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
