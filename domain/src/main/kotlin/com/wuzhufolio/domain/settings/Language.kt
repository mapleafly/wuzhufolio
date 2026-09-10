package com.wuzhufolio.domain.settings

/**
 * 界面语言（M12 T12.4 · PRD §6「国际化 (I18N)：支持多语言（初期支持英文、中文）」）。
 *
 * 存储于 settings 全局行 **`locale`**（[GeneralSettingsService]；键与 M002 迁移的默认值 `zh-CN` 同源——
 * P3 已种下该键但一直无消费方，M12 起由设置页「界面语言」读写；设备级偏好，与 theme/pnl_scheme 同类）。
 * 影响面：① 全部界面文案（ui/i18n 目录 zh/en 双档）；② 日期时间显示格式（跨端共享规范 §4
 * 「界面显示统一转换为系统本地时区；日期时间格式随 I18N 语言设置」——时区恒为系统本地，仅格式随语言）。
 *
 * [nativeLabel] 为语言选择项自身的展示名（语言名用本语言书写，不随当前语言翻译——业界惯例）。
 */
enum class AppLanguage(val code: String, val storageValue: String, val nativeLabel: String) {
    ZH("zh", "zh-CN", "中文"),
    EN("en", "en-US", "English"),
    ;

    companion object {
        /** settings 全局行的键名（与 M002 迁移默认值同源；ui/app 侧写偏好时引用此常量）。 */
        const val SETTINGS_KEY: String = "locale"

        /**
         * 存储串解析（按语言前缀匹配，兼容 M002 迁移种下的 `zh-CN` 与简写 `zh`/`en`；
         * 未知/空 → 默认中文，与 PRD「初期支持英文、中文」的默认口径一致）。
         */
        fun fromStorage(value: String?): AppLanguage {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { normalized.startsWith(it.code) } ?: ZH
        }
    }
}
