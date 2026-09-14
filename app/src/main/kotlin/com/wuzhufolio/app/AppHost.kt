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
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.wuzhufolio.app.tray.AwtTrayHost
import com.wuzhufolio.app.tray.CloseAction
import com.wuzhufolio.app.tray.DesktopNotice
import com.wuzhufolio.app.tray.DesktopNoticeText
import com.wuzhufolio.app.tray.NoticeLevel
import com.wuzhufolio.app.tray.NoticePolicy
import com.wuzhufolio.app.tray.TrayIcon
import com.wuzhufolio.app.tray.TrayMenuWindow
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
    // P6 DEF-15：自建 AWT 托盘（字体 = 内嵌 Noto Sans SC，文案随界面语言）——Compose Tray 的菜单
    // 由 AWT 逻辑字体渲染，在 Windows 上中文乱码且无法注入字体。
    val trayCapable = remember { TraySupport.isSupported() }
    val trayIcon = remember { TrayIcon.painter() }
    var trayHost by remember { mutableStateOf<AwtTrayHost?>(null) }
    // DEF-15 三次修复：菜单不再交给 AWT PopupMenu（Windows 上文本乱码），改为 Compose 自绘窗口，
    // 位置 = 右键时的屏幕坐标（px → dp 需按屏幕缩放换算）
    var trayMenuAt by remember { mutableStateOf<Pair<Float, Float>?>(null) }
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

    DisposableEffect(trayCapable) {
        val host = if (trayCapable) {
            AwtTrayHost(
                icon = trayIcon.toAwtImage(
                    Density(1f),
                    LayoutDirection.Ltr,
                    androidx.compose.ui.geometry.Size(TrayIcon.DEFAULT_SIZE.toFloat(), TrayIcon.DEFAULT_SIZE.toFloat()),
                ),
                logger = runtime.logger,
                onOpen = { showWindow() },
                onMenuRequest = { x, y -> trayMenuAt = x.toFloat() to y.toFloat() },
            ).takeIf { it.install() }
        } else {
            null
        }
        trayHost = host
        onDispose {
            host?.close()
            trayHost = null
        }
    }
    val traySupported = trayCapable && trayHost != null

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
            trayHost?.notify(notice.title, notice.message, error = notice.level == NoticeLevel.ERROR)
        }
    }

    // 托盘菜单（Compose 自绘，DEF-15）：定位到右键位置；失焦/Esc/点选后关闭
    trayMenuAt?.let { (xPx, yPx) ->
        val scale = remember {
            runCatching {
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
            }.getOrDefault(1.0)
        }
        TrayMenuWindow(
            position = androidx.compose.ui.window.WindowPosition((xPx / scale).dp, (yPx / scale).dp),
            themeMode = runtime.uiState.theme,
            onDismiss = { trayMenuAt = null },
            onOpen = { showWindow(); trayMenuAt = null },
            onSync = { scope.launch { runCatching { runtime.scheduler.syncNow() } }; trayMenuAt = null },
            onQuit = { trayMenuAt = null; onExit() },
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

