package com.wuzhufolio.ui.tray

import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.ui.i18n.ShellStringsEn
import com.wuzhufolio.ui.i18n.ShellStringsZh

/**
 * 托盘菜单文案（P6 人工门 DEF-15 / DEF-18）。
 *
 * **为什么按 [AppLanguage] 显式取词，而不是读全局 `shellStrings`**：
 * `WuzhuTheme` 会把全局 `I18n` 设成它收到的 `language`（默认中文）。托盘菜单是**独立窗口**，
 * 它自己的主题调用会重置这个全局值——若菜单文案再走全局读取器，就会恒为中文
 * （实测：界面切英文后托盘仍中文，重启亦不变，因为菜单窗口每次都把自己置回 ZH）。
 * 因此菜单文案必须由**当前语言**显式推导，不依赖全局状态。
 */
data class TrayLabels(
    val open: String,
    val syncNow: String,
    val quit: String,
)

/** 按当前界面语言取托盘文案（zh/en 双档，与设置页语言开关同源）。 */
fun trayLabels(language: AppLanguage): TrayLabels = when (language) {
    AppLanguage.ZH -> TrayLabels(
        open = ShellStringsZh.trayOpen,
        syncNow = ShellStringsZh.traySyncNow,
        quit = ShellStringsZh.trayQuit,
    )
    AppLanguage.EN -> TrayLabels(
        open = ShellStringsEn.trayOpen,
        syncNow = ShellStringsEn.traySyncNow,
        quit = ShellStringsEn.trayQuit,
    )
}
