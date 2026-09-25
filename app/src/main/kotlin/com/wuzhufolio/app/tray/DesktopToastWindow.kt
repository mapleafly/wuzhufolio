package com.wuzhufolio.app.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.theme.WuzhuTheme
import com.wuzhufolio.ui.tray.TRAY_NOTICE_VISIBLE_MS
import com.wuzhufolio.ui.tray.TrayNoticeCard
import com.wuzhufolio.ui.tray.TrayNoticeLevel
import kotlinx.coroutines.delay

/**
 * 应用内桌面提示窗（**DEF-49**）：托盘手动动作与后台事件的**可见反馈**。
 *
 * 为什么自绘而不用系统通知：AWT `TrayIcon.displayMessage` 在 Linux（GNOME + `ubuntu-appindicators`）
 * 下没有承载 —— XEmbed 气泡无人接收，人工实测「托盘点『立即同步』后什么都没发生」。
 * 本窗口三平台一致、无平台 API 依赖，且能承载「正在…」这类**开始**反馈（原生气泡通常只能报结果）。
 *
 * 交互约定：
 * - 右下角、无边框、置顶、**不抢焦点**（`focusable = false`，不打断用户正在做的事）；
 * - [TRAY_NOTICE_VISIBLE_MS] 后自动消失；`noticeKey` 变化即重新计时（同文案连续出现也不糊在一起）；
 * - 仅展示、无交互（不涉及 `AGENTS.md §7.3` 的交互弹层约束）。
 */
@Composable
fun ApplicationScope.DesktopToastWindow(
    notice: DesktopNotice,
    /** 通知序号：同一文案连续出现时用它触发重新计时（数据类实例可能相等）。 */
    noticeKey: Int,
    themeMode: ThemeMode,
    language: AppLanguage,
    onDismiss: () -> Unit,
) {
    val state = rememberWindowState(
        position = WindowPosition(Alignment.BottomEnd),
        size = DpSize(340.dp, 104.dp),
    )
    Window(
        onCloseRequest = onDismiss,
        state = state,
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = false,
        title = "WuZhuFolioNotice",
    ) {
        LaunchedEffect(noticeKey) {
            delay(TRAY_NOTICE_VISIBLE_MS)
            onDismiss()
        }
        // 语言/主题显式传入：提示窗是独立组合树（同 TrayMenuWindow 的 DEF-19 教训）
        WuzhuTheme(themeMode = themeMode, language = language) {
            TrayNoticeCard(
                title = notice.title,
                message = notice.message,
                level = notice.level.toTrayLevel(),
            )
        }
    }
}

/** app 层通知级别 → ui 层展示级别（避免 ui 反向依赖 app）。 */
private fun NoticeLevel.toTrayLevel(): TrayNoticeLevel = when (this) {
    NoticeLevel.INFO -> TrayNoticeLevel.INFO
    NoticeLevel.WARNING -> TrayNoticeLevel.WARNING
    NoticeLevel.ERROR -> TrayNoticeLevel.ERROR
}
