package com.wuzhufolio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.wuzhufolio.app.tray.AwtTrayHost
import com.wuzhufolio.app.tray.CloseAction
import com.wuzhufolio.app.tray.DesktopNotice
import com.wuzhufolio.app.tray.DesktopNoticeText
import com.wuzhufolio.app.tray.DesktopToastWindow
import com.wuzhufolio.app.tray.NoticeDelivery
import com.wuzhufolio.app.tray.NoticeLevel
import com.wuzhufolio.app.tray.NoticePolicy
import com.wuzhufolio.app.tray.TrayIcon
import com.wuzhufolio.app.tray.TrayMenuWindow
import com.wuzhufolio.app.tray.TraySupport
import com.wuzhufolio.app.tray.WindowCloseBehavior
import com.wuzhufolio.data.schedule.SchedulerEvent
import com.wuzhufolio.ui.shell.LocalDevUi
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
    // DEF-48：按**平台托盘尺寸**出图（GNOME/X11 通常 22px、Windows 16px）——给 64px 让宿主/SNI 代理
    // 自行缩放会裁掉边缘（人工实测「托盘图标显示不全」）；同时统一加了透明安全边距（TrayIcon.PADDING）。
    // 托盘策略（DEF-48 二轮）：Linux/X11 给「逻辑尺寸 + autoSize=false」；Windows/macOS 维持大图 + autoSize。
    // 诊断覆盖：`-Dwuzhufolio.trayIconPx=<N>` / `-Dwuzhufolio.trayAutoSize=<bool>`（真机定位缩放/裁剪类问题用）。
    val trayPolicy = remember {
        val base = TrayIcon.policy()
        val autoSize = System.getProperty("wuzhufolio.trayAutoSize")?.toBooleanStrictOrNull()
        base.copy(
            sizePx = System.getProperty("wuzhufolio.trayIconPx")?.toIntOrNull()?.coerceIn(8, 256) ?: base.sizePx,
            imageAutoSize = autoSize ?: base.imageAutoSize,
        )
    }
    // 临时取证（DEF-48 二轮）：托盘图标的「AWT 报告尺寸」与「我们渲染尺寸」——HiDPI 下两者不一致
    LaunchedEffect(Unit) {
        val awt = runCatching { java.awt.SystemTray.getSystemTray().trayIconSize.toString() }
            .getOrDefault("?")
        runtime.logger.info(
            "tray icon | awtTrayIconSize=" + awt + " | renderPx=" + trayPolicy.sizePx +
                " | autoSize=" + trayPolicy.imageAutoSize,
        )
    }
    // DEF-52①：主窗口图标（0.1.0 从未设置 → `_NET_WM_ICON` 缺失 → GNOME 顶栏/Dock 回落通用齿轮）
    val windowIcon = remember { TrayIcon.painter(TrayIcon.WINDOW_SIZE) }
    var trayHost by remember { mutableStateOf<AwtTrayHost?>(null) }
    // DEF-15 三次修复：菜单不再交给 AWT PopupMenu（Windows 上文本乱码），改为 Compose 自绘窗口，
    // 位置 = 右键时的屏幕坐标（px → dp 需按屏幕缩放换算）
    var trayMenuAt by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    // P6 DEF-19：菜单窗口是独立组合树——语言/主题必须取**可观察**状态（启动快照会在切换后过期）
    val uiPreferences by runtime.uiPreferences.state.collectAsState()
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    val scope = rememberCoroutineScope()
    var windowVisible by remember { mutableStateOf(true) }
    // DEF-50：主窗口句柄（用于「打开主界面」时置前；Compose 的 WindowState 只管可见/最小化，不改 Z 序）
    var mainWindow by remember { mutableStateOf<ComposeWindow?>(null) }
    // DEF-49：可见反馈（Linux 用应用内提示窗；Win/mac 走原生气泡）
    var notice by remember { mutableStateOf<DesktopNotice?>(null) }
    var noticeSeq by remember { mutableStateOf(0) }
    val inAppToast = remember { NoticeDelivery.useInAppToast() }

    /** 显示一条通知（托盘手动动作与后台事件共用；null 忽略）。 */
    fun showNotice(next: DesktopNotice?) {
        if (next == null) return
        notice = next
        noticeSeq++
    }

    val proxyStatus by runtime.proxyRuntime.status.collectAsState()

    /**
     * 恢复主界面（托盘单击/菜单「打开主界面」）：显示 + 取消最小化 + **置前取焦**（DEF-50）。
     * 此前只做前两步，窗口被别的窗口压住时用户依然看不到（人工反馈第 3 条）。
     * 注：X11 下 toFront/requestFocus 生效；Wayland 的 WM 可能只让任务栏闪烁（平台策略，已在用户指南注明）。
     */
    fun showWindow() {
        windowVisible = true
        windowState.isMinimized = false
        runCatching {
            mainWindow?.let { w ->
                w.toFront()
                w.requestFocus()
            }
        }
    }

    /** 托盘「立即同步交易」（PRD 故事 4.3 / 两类 API 独立中的交易侧）。 */
    fun syncFromTray() {
        showNotice(DesktopNoticeText.manualSyncStarted())
        scope.launch {
            val results = runCatching { runtime.scheduler.syncNow() }
            val done = results.fold(
                onSuccess = { DesktopNoticeText.syncFinished(it) ?: DesktopNoticeText.manualSyncNoKeys() },
                onFailure = { DesktopNoticeText.manualActionFailed("同步交易数据", it) },
            )
            showNotice(done)
        }
    }

    /** 托盘「立即刷新行情」（两类 API 独立中的行情侧）。 */
    fun refreshFromTray() {
        showNotice(DesktopNoticeText.manualRefreshStarted())
        scope.launch {
            val done = runCatching { runtime.scheduler.refreshMarketNow(manual = true) }.fold(
                onSuccess = { DesktopNoticeText.manualRefreshFinished(it.refreshedCoins, it.error?.toString()) },
                onFailure = { DesktopNoticeText.manualActionFailed("刷新行情", it) },
            )
            showNotice(done)
        }
    }

    // 调度循环宿主随宿主组合存活（关窗退出时随之取消）
    DisposableEffect(runtime) {
        runtime.scheduler.start(scope)
        onDispose { runtime.scheduler.stop() }
    }

    DisposableEffect(trayCapable) {
        val host = if (trayCapable) {
            AwtTrayHost(
                // DEF-48 二轮：交**实体 BufferedImage**（不能交惰性 PainterImage —— AWT 会按自己的密度
                // 栅格化成约 2× 尺寸再 1:1 画进托盘窗口 → 图标被裁）。见 TrayIcon.awtImage 头注。
                icon = TrayIcon.awtImage(trayPolicy.sizePx),
                imageAutoSize = trayPolicy.imageAutoSize,
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
    // DEF-49：Linux 下 AWT 气泡无承载（GNOME/SNI 代理）→ 改投应用内提示窗；Win/mac 保持原生气泡。
    LaunchedEffect(runtime, traySupported, inAppToast) {
        runtime.scheduler.events.collect { event ->
            runtime.logger.debug("scheduler event: " + event.javaClass.simpleName)
            val next = noticeFor(event, runtime) ?: return@collect
            if (!traySupported) return@collect
            if (inAppToast) {
                showNotice(next)
            } else {
                trayHost?.notify(next.title, next.message, error = next.level == NoticeLevel.ERROR)
            }
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
            themeMode = uiPreferences.theme,
            language = uiPreferences.language,
            onDismiss = { trayMenuAt = null },
            onOpen = { showWindow(); trayMenuAt = null },
            onSync = { trayMenuAt = null; syncFromTray() },
            onRefresh = { trayMenuAt = null; refreshFromTray() },
            onQuit = { trayMenuAt = null; onExit() },
        )
    }

    // DEF-49：应用内提示窗（Linux 通知通道；自动消失，不抢焦点）
    if (inAppToast) {
        val current = notice
        if (current != null) {
            DesktopToastWindow(
                notice = current,
                // key = 序号：同一文案连续出现时也重新计时/重开窗
                noticeKey = noticeSeq,
                themeMode = uiPreferences.theme,
                language = uiPreferences.language,
                onDismiss = { notice = null },
            )
        }
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
        icon = windowIcon,
    ) {
        // DEF-50：留住窗口句柄供「打开主界面」置前使用（Compose 未暴露 ApplicationScope→Window 的反查）
        LaunchedEffect(Unit) { mainWindow = window }
        // DEF-47：开发期 UI 开关（构建期注入，默认 false）。正式构建不含组件走查页；
        // 开发/走查以 `-Pwuzhufolio.devUi=true` 构建（见 app/build.gradle.kts 的 BuildInfo.DEV_UI）。
        CompositionLocalProvider(LocalDevUi provides BuildInfo.DEV_UI) {
            MainWindowContent(
                runtime = runtime,
                proxyStatus = proxyStatus,
                trayAvailable = traySupported,
            )
        }
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

