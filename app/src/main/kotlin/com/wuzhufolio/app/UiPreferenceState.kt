package com.wuzhufolio.app

import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 界面偏好的**运行期可观察状态**（P6 人工门 **DEF-19**：界面切英文后托盘菜单不变、需重启才生效）。
 *
 * 背景：`Runtime.uiState` 是**启动时快照**（主题/盈亏配色/语言来自设置，构造后不再变化），
 * 主壳自己有 ViewModel 维护实时状态，但**主壳之外的组件**（托盘菜单窗口）若读这个快照，
 * 就只能等下次启动才看到新值。
 *
 * 本类把三个偏好做成 `StateFlow`，由偏好写入路径（`Main.onShellPreferenceChange`）同步更新，
 * 主壳之外的组件（`AppHost` → 托盘菜单窗口）订阅它即可**即时**跟随。
 *
 * 口径：设置项键名与 `ShellViewModel` 持久化键一致（`theme` / `pnl_scheme` / `locale`）；
 * 未知键、非法值一律忽略（保持当前值），不抛异常（偏好写入不得影响主流程）。
 */
class UiPreferenceState(
    initial: AppBootstrap.UiState,
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<AppBootstrap.UiState> = _state.asStateFlow()

    /** 偏好键 → 状态更新（与 `ShellViewModel.onPreferenceChange` 的键一致）。 */
    fun apply(key: String, value: String) {
        when (key) {
            // 注：fromStorage 返回**非空**（未知值回落 LIGHT/GREEN_UP），故不用 `?.let`
            // ——DEF-53：此处原有冗余安全调用，编译期警告「Unnecessary safe call」。
            THEME_KEY -> _state.update { it.copy(theme = ThemeMode.fromStorage(value)) }
            PNL_SCHEME_KEY -> _state.update { it.copy(pnlScheme = PnlColorScheme.fromStorage(value)) }
            LANGUAGE_KEY -> {
                _state.update { it.copy(language = AppLanguage.fromStorage(value)) }
            }
            else -> Unit // 其他偏好与界面主题无关（精度/白名单等由各自消费者读取）
        }
    }

    companion object {
        const val THEME_KEY = "theme"
        const val PNL_SCHEME_KEY = "pnl_scheme"
        const val LANGUAGE_KEY = "locale"
    }
}
