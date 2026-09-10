package com.wuzhufolio.ui.i18n

import com.wuzhufolio.domain.settings.AppLanguage

/**
 * 界面语言运行期持有器（M12 T12.4）。
 *
 * 设计口径（为什么是全局持有器而不是 CompositionLocal 参数）：
 * - 文案消费点既在 Composable（`Text(MarketCopy.WATCH_TITLE)`）也在 ViewModel/纯函数
 *   （toast/错误文案在协程里拼装），后者拿不到 CompositionLocal；
 * - 全部 `*Copy` 对象与 UI 文案均写成**动态取值属性**（`val X: String get() = ...`），
 *   每次读取按当前语言解析，因此不存在「VM 缓存了旧语言文案」的类问题；
 * - 切换语言时的重组由 `WuzhuTheme` 的 `key(language.code)` 强制整棵子树重建保证
 *   （见 theme/Theme.kt），不依赖各调用点自行订阅。
 *
 * 线程可见性：语言只在组合（EDT）侧写入，协程侧读取；`@Volatile` 保证可见性。
 */
object I18n {

    @Volatile
    var current: AppLanguage = AppLanguage.ZH
        private set

    /** 当前是否中文（文案对象的分支判据）。 */
    val isZh: Boolean get() = current == AppLanguage.ZH

    /** 切档（由 `WuzhuTheme` 在每次组合时同步；测试可直接调用）。 */
    fun set(language: AppLanguage) {
        current = language
    }

    /** 双档取一（少量零散文案的就地分支，避免为单条文案开接口）。 */
    fun <T> pick(zh: T, en: T): T = if (isZh) zh else en
}
