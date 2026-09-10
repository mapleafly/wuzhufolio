package com.wuzhufolio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.wuzhufolio.app.tray.CloseAction
import com.wuzhufolio.app.tray.DesktopNotice
import com.wuzhufolio.app.tray.DesktopNoticeText
import com.wuzhufolio.app.tray.NoticeLevel
import com.wuzhufolio.app.tray.NoticePolicy
import com.wuzhufolio.app.tray.TrayIcon
import com.wuzhufolio.app.tray.TraySupport
import com.wuzhufolio.app.tray.WindowCloseBehavior
import com.wuzhufolio.data.schedule.SchedulerEvent
import kotlinx.coroutines.launch

/**
 * 桌面宿主（M11 · T11.1）：系统托盘 + 主窗口可见性 + 调度循环宿主 + 通知派发。
 *
 * 三条职责在此汇合，是因为它们共享**同一个可见性真源**：
 * `windowVisible && !windowState.isMinimized` —— 该值既是「关闭窗口最小化到托盘」的结果，
 * 也是调度降频（PRD 9.2：托盘驻留按 API 同步间隔降频）与「窗口恢复可见立即刷新一次」的输入。
 * 分散到多处会导致托盘驻留与降频判定不一致。
 *
 * 托盘不可用的降级（[isTraySupported]，Linux 无 StatusNotifier/AppIndicator、无 X11 托盘时）：
 * 不注册 Tray、关窗即退出（**不静默把窗口藏进不存在的托盘**——那会让用户以为应用已退出却仍在后台），
 * 设置页「最小化到托盘」行同步置灰（见 SettingsPage）。
 */
@Composable
fun ApplicationScope.AppHost(runtime: AppBootstrap.Runtime, onExit: () -> Unit) {
    val traySupported = remember { TraySupport.isSupported() }
    val trayState = rememberTrayState()
    val trayIcon = remember { TrayIcon.painter() }
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    val scope = rememberCoroutineScope()
    var windowVisible by remember { mutableStateOf(true) }

    val proxyStatus by runtime.proxyRuntime.status.collectAsState()

    /** 恢复主界面（托盘单击/菜单「打开主界面」）：显示 + 取消最小化。 */
    fun showWindow() {
        windowVisible = true
        windowState.isMinimized = false
    }

    // 调度循环宿主随宿主组合存活（关窗退出时随之取消）
    DisposableEffect(runtime) {
        runtime.scheduler.start(scope)
        onDispose { runtime.scheduler.stop() }
    }

    // 托盘能力与关窗行为留痕（走查/冒烟可核；托盘不可用时关窗即退出，见头注降级口径）
    LaunchedEffect(traySupported) {
        runtime.logger.info(
            "tray support | supported=" + traySupported +
                " | minimizeOnClose=" + runtime.desktopPreferences.minimizeOnClose,
        )
    }

    // 可见性真源 → 调度宿主（托盘驻留降频；false→true 立即刷新一次行情）
    val effectiveVisible = windowVisible && !windowState.isMinimized
    LaunchedEffect(effectiveVisible) { runtime.scheduler.setWindowVisible(effectiveVisible) }

    // 调度事件 → 桌面通知（开关关闭/托盘不可用 = 只留日志，不打扰）
    LaunchedEffect(runtime, traySupported) {
        runtime.scheduler.events.collect { event ->
            runtime.logger.debug("scheduler event: " + event.javaClass.simpleName)
            val notice = noticeFor(event, runtime) ?: return@collect
            if (!traySupported) return@collect
            trayState.sendNotification(notificationOf(notice))
        }
    }

    if (traySupported) {
        Tray(
            icon = trayIcon,
            state = trayState,
            tooltip = "WuZhuFolio",
            onAction = { showWindow() },
            menu = {
                Item("打开主界面", onClick = { showWindow() })
                Item(
                    "立即同步",
                    onClick = { scope.launch { runCatching { runtime.scheduler.syncNow() } } },
                )
                Separator()
                Item("退出", onClick = onExit)
            },
        )
    }

    Window(
        onCloseRequest = {
            when (WindowCloseBehavior.decide(traySupported, runtime.desktopPreferences.minimizeOnClose)) {
                CloseAction.HIDE_TO_TRAY -> {
                    runtime.logger.info("window hidden to tray")
                    windowVisible = false
                }
                CloseAction.EXIT -> onExit()
            }
        },
        state = windowState,
        visible = windowVisible,
        title = "WuZhuFolio",
    ) {
        MainWindowContent(
            runtime = runtime,
            proxyStatus = proxyStatus,
            trayAvailable = traySupported,
        )
    }
}

/**
 * 事件 → 通知（开关口径见 [NoticePolicy]；托盘不可用 = 只留日志，不打扰）。
 */
private fun noticeFor(event: SchedulerEvent, runtime: AppBootstrap.Runtime): DesktopNotice? =
    NoticePolicy.noticeFor(
        event = event,
        syncNotification = runtime.desktopPreferences.syncNotification,
        backupReminder = runtime.desktopPreferences.backupReminder,
    )

/** 通知级别 → 托盘通知类型（系统托盘决定图标与提示音）。 */
private fun notificationOf(notice: DesktopNotice): Notification = Notification(
    title = notice.title,
    message = notice.message,
    type = when (notice.level) {
        NoticeLevel.INFO -> Notification.Type.Info
        NoticeLevel.WARNING -> Notification.Type.Warning
        NoticeLevel.ERROR -> Notification.Type.Error
    },
)
