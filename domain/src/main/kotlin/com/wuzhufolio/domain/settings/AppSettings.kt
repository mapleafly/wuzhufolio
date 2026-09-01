package com.wuzhufolio.domain.settings

/** 主题模式（settings.theme 存储值）。暗色为唯一真源的内置档位（已决策事项 16）。 */
enum class ThemeMode(val storageValue: String) {
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: LIGHT
    }
}

/** 盈亏配色方案（design-tokens §2.3，PRD §6 无障碍：颜色仅辅助、强制 +/- 符号）。 */
enum class PnlColorScheme(val storageValue: String) {
    /** 绿涨红跌（默认，国际惯例）。 */
    GREEN_UP("green_up"),

    /** 红涨绿跌（中文习惯）。 */
    RED_UP("red_up"),

    /** 色盲友好（蓝涨橙跌）。 */
    COLORBLIND("colorblind"),
    ;

    companion object {
        fun fromStorage(value: String?): PnlColorScheme =
            entries.firstOrNull { it.storageValue == value } ?: GREEN_UP
    }
}
