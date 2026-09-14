package com.wuzhufolio.app

import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 界面偏好可观察状态（P6 人工门 **DEF-19**：界面切英文后托盘菜单不变、需重启才生效）。
 *
 * 背景：`Runtime.uiState` 是启动快照，主壳之外的组件（托盘菜单窗口）读它会过期。
 * 本测试钉住「偏好写入 → 可观察状态即时更新」的映射（键名与 `ShellViewModel` 持久化键一致）。
 */
class UiPreferenceStateTest {

    private fun initial() = AppBootstrap.UiState(
        theme = ThemeMode.LIGHT,
        pnlScheme = PnlColorScheme.GREEN_UP,
        language = AppLanguage.ZH,
        securityNotice = null,
    )

    @Test
    fun `language preference updates the observable state immediately`() {
        val state = UiPreferenceState(initial())
        assertEquals(AppLanguage.ZH, state.state.value.language, "初始语言来自启动快照")

        state.apply(UiPreferenceState.LANGUAGE_KEY, AppLanguage.EN.storageValue)
        assertEquals(AppLanguage.EN, state.state.value.language, "切英文后应即时反映（托盘菜单据此渲染）")

        state.apply(UiPreferenceState.LANGUAGE_KEY, AppLanguage.ZH.storageValue)
        assertEquals(AppLanguage.ZH, state.state.value.language, "切回中文同样即时生效")
    }

    @Test
    fun `theme and pnl scheme preferences update the observable state`() {
        val state = UiPreferenceState(initial())
        state.apply(UiPreferenceState.THEME_KEY, ThemeMode.DARK.storageValue)
        state.apply(UiPreferenceState.PNL_SCHEME_KEY, PnlColorScheme.COLORBLIND.storageValue)
        assertEquals(ThemeMode.DARK, state.state.value.theme)
        assertEquals(PnlColorScheme.COLORBLIND, state.state.value.pnlScheme)
    }

    @Test
    fun `unknown or corrupt values do not break the state`() {
        val state = UiPreferenceState(initial())
        state.apply("display.precision", "8/2/2") // 与界面主题无关的键 → 忽略
        state.apply(UiPreferenceState.THEME_KEY, "not-a-theme") // 非法值 → 回落默认（light）
        state.apply(UiPreferenceState.LANGUAGE_KEY, "") // 空值 → 容错回中文（AppLanguage.fromStorage 语义）
        assertEquals(ThemeMode.LIGHT, state.state.value.theme)
        assertEquals(AppLanguage.ZH, state.state.value.language)
    }
}
