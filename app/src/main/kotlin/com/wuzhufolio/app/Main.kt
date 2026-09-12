package com.wuzhufolio.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wuzhufolio.data.db.DatabaseKeyMismatchException
import com.wuzhufolio.data.db.DatabaseOpenException
import com.wuzhufolio.data.proxy.JdkSystemProxy
import com.wuzhufolio.data.security.MasterKeyFileException
import com.wuzhufolio.domain.proxy.ProxyStatus
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.auth.AuthGate
import com.wuzhufolio.ui.backup.BackupFileNames
import com.wuzhufolio.ui.backup.DataManagementSection
import com.wuzhufolio.ui.exchange.TopBarSyncViewModel
import com.wuzhufolio.ui.i18n.WzFormat
import com.wuzhufolio.ui.ledger.FundsPage
import com.wuzhufolio.ui.ledger.TransactionsPage
import com.wuzhufolio.ui.market.MarketWatchPage
import com.wuzhufolio.ui.portfolio.AssetsPage
import com.wuzhufolio.ui.portfolio.CoinDetailPage
import com.wuzhufolio.ui.portfolio.DashboardPage
import com.wuzhufolio.ui.shell.ShellStatusViewModel
import com.wuzhufolio.ui.shell.TopBarRefreshViewModel
import com.wuzhufolio.ui.settings.SettingsFilePickers
import com.wuzhufolio.ui.settings.SettingsPage
import com.wuzhufolio.ui.theme.WuzhuTheme
import com.wuzhufolio.ui.theme.WzTheme
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/**
 * 应用入口（M1 起）：引导（AppBootstrap）→ 主壳；引导失败渲染致命提示窗口
 * （错钥/密钥文件损坏/钥匙串异常等）。正式 B1「从 .cpro 恢复」页面随 M2/M9 提供（interaction.md §2.1）。
 *
 * 2026-09-03 修复：引导结果显式收敛到密封 Outcome 后再分支——此前 runCatching.getOrElse 把
 * 返回类型推断为 Any（Runtime 与 Outcome.Fatal 的 LUB），when 的 is Outcome.Ready 永不命中，
 * 成功路径不组合任何窗口即静默退出（失败路径恰命中 Fatal 分支，故未暴露）。
 */
fun main() {
    // M11 T11.3：必须在任何网络类加载前开启 JDK 系统代理探测
    // （sun.net.spi.DefaultProxySelector 在类初始化时读取 java.net.useSystemProxies 一次；
    //  打包版另有 app/build.gradle.kts 的 jvmArgs 兜底）
    JdkSystemProxy.enable()
    System.setProperty("wuzhufolio.logdir", AppDirs.logDir().toString())
    val logger = LoggerFactory.getLogger("wuzhufolio.bootstrap")

    application {
        val outcome: Outcome = remember {
            try {
                Outcome.Ready(AppBootstrap.run(logger))
            } catch (t: Throwable) {
                FatalOutcome.of(t, logger)
            }
        }
        when (outcome) {
            is Outcome.Ready -> {
                val runtime = outcome.runtime
                // M11：窗口/托盘/调度宿主由 AppHost 统一持有（可见性真源单一——见 AppHost 头注）
                AppHost(runtime, onExit = { runtime.close(); exitApplication() })
            }
            is Outcome.Fatal -> FatalWindow(outcome, onExit = { exitApplication() })
        }
    }
}

private sealed interface Outcome {
    data class Ready(val runtime: AppBootstrap.Runtime) : Outcome
    data class Fatal(val title: String, val message: String) : Outcome
}

/**
 * 主窗口内容（M11 起由 [AppHost] 的 `Window` 承载——窗口本身与托盘/可见性同源，故窗口包装留在 AppHost）。
 *
 * M12 增补：聚合页槽位（仪表盘/资产列表/币种详情）+ 顶栏手动刷新行情 + 状态栏数据源与额度提示 +
 * 界面语言/精度档透传（T12.4）。
 */
@Composable
internal fun MainWindowContent(
    runtime: AppBootstrap.Runtime,
    proxyStatus: ProxyStatus,
    trayAvailable: Boolean,
) {
    // 顶栏手动同步（PRD 故事 4.3）：VM 持有同步状态与结果 toast
    val syncViewModel = remember { TopBarSyncViewModel(runtime.exchangeSyncService) }
    // M12：状态栏数据源（同步状态/数据源徽章/额度提示/备份提醒）+ 顶栏手动刷新行情
    val statusViewModel = remember {
        ShellStatusViewModel(
            marketRefreshService = runtime.marketRefreshService,
            exchangeSyncService = runtime.exchangeSyncService,
            backupReminderDays = runtime.backupReminderDaysProvider,
            appVersion = com.wuzhufolio.data.backup.DefaultBackupService.APP_VERSION,
        )
    }
    val refreshViewModel = remember {
        TopBarRefreshViewModel(
            refreshService = runtime.marketRefreshService,
            marketSettingsService = runtime.marketSettingsService,
            watchService = runtime.marketWatchService,
            portfolioService = runtime.portfolioService,
            onRefreshed = statusViewModel::refresh,
        )
    }
    DisposableEffect(syncViewModel, statusViewModel, refreshViewModel) {
        onDispose {
            syncViewModel.dispose()
            statusViewModel.dispose()
            refreshViewModel.dispose()
        }
    }
    val manualSyncing by syncViewModel.syncing.collectAsState()
    val manualSyncToast by syncViewModel.toast.collectAsState()
    val refreshBusy by refreshViewModel.refreshing.collectAsState()
    val refreshToast by refreshViewModel.toast.collectAsState()
    val status by statusViewModel.state.collectAsState()
    // 同步结束后刷新状态栏（否则要等下一次轮询才更新）
    LaunchedEffect(manualSyncing) { statusViewModel.refresh() }
    // M12 T12.4：启动期载入默认精度档（设置页内改动由 SettingsPage 即时回写）
    LaunchedEffect(Unit) {
        runCatching { runtime.generalSettingsService.view() }.getOrNull()?.let { WzFormat.precision = it.precision }
    }

    AuthGate(
        authService = runtime.session.authService,
        themeMode = runtime.uiState.theme,
        pnlScheme = runtime.uiState.pnlScheme,
        // M12 T12.4：界面语言（设置页「通用 → 界面语言」切换，持久化于 settings 全局行）
        language = runtime.uiState.language,
        // M11 T11.3：状态栏代理指示（直连 / 系统代理，PRD 4.2 验收 3）
        proxyStatus = proxyStatus,
        // M10：登录页每次进入重读（设置页枚举开关关闭后无需重启即生效）
        usernameEnumEnabled = { usernameEnumEnabled(runtime) },
        startupNotice = runtime.uiState.securityNotice,
        // M10：主题/盈亏配色持久化（顶栏 ☾ 与设置页「主题（明/暗）」双向同步，PRD 6.1）
        onShellPreferenceChange = { key, value -> runtime.settings.putGlobal(key, value) },
        settingsPageContent = { shellViewModel ->
            SettingsPage(
                shellViewModel = shellViewModel,
                generalSettings = runtime.generalSettingsService,
                marketSettingsService = runtime.marketSettingsService,
                marketRefreshService = runtime.marketRefreshService,
                syncService = runtime.exchangeSyncService,
                feeRuleService = runtime.feeRuleService,
                diagnosticsService = runtime.diagnosticsService,
                logAccess = runtime.logAccess,
                backupService = runtime.backupService,
                desktopSettings = runtime.desktopSettingsService,
                trayAvailable = trayAvailable,
                appVersion = com.wuzhufolio.data.backup.DefaultBackupService.APP_VERSION,
                pickers = SettingsFilePickers(
                    pickLogSave = { title -> FilePicker.pickSave(title) },
                    pickReportSave = { title -> FilePicker.pickSave(title) },
                    writeTextFile = ::writeTextFile,
                    pickCproSave = {
                        FilePicker.pickSave("保存 .cpro 备份")
                            ?.let { BackupFileNames.withExtension(it, ".cpro") }
                    },
                    pickCproLoad = { FilePicker.pickLoad("选择 .cpro 备份") },
                    pickCsvSave = { kind ->
                        FilePicker.pickSave("导出 CSV（" + kind.fileNameHint + "）")
                            ?.let { BackupFileNames.withExtension(it, ".csv") }
                    },
                ),
                onProxyEnabledChange = { runtime.proxyRuntime.setEnabled(it) },
            )
        },
        watchPageContent = {
            MarketWatchPage(
                watchService = runtime.marketWatchService,
                quotesService = runtime.marketQuotesService,
                refreshService = runtime.marketRefreshService,
                settingsService = runtime.marketSettingsService,
            )
        },
        // M12 T12.1：聚合页（仪表盘 / 资产列表 / 币种详情）
        dashboardPageContent = { accountName ->
            var lastBackup by remember { mutableStateOf<java.time.Instant?>(null) }
            LaunchedEffect(runtime) { lastBackup = runtime.lastBackupAtProvider() }
            DashboardPage(
                portfolioService = runtime.portfolioService,
                refreshService = runtime.marketRefreshService,
                generalSettings = runtime.generalSettingsService,
                accountName = accountName,
                lastBackupAt = lastBackup,
            )
        },
        assetsPageContent = { onOpenCoin ->
            AssetsPage(
                portfolioService = runtime.portfolioService,
                refreshService = runtime.marketRefreshService,
                generalSettings = runtime.generalSettingsService,
                onOpenCoin = onOpenCoin,
            )
        },
        coinDetailPageContent = { cgId, onBack ->
            CoinDetailPage(
                cgId = cgId,
                portfolioService = runtime.portfolioService,
                ledgerService = runtime.transactionLedgerService,
                calibrationUseCase = runtime.calibrationService,
                catalog = runtime.coinCatalog,
                onBack = onBack,
            )
        },
        transactionsPageContent = {
            TransactionsPage(
                service = runtime.transactionLedgerService,
                pickCsvFile = { FilePicker.pickLoad("选择 CSV 文件") },
                pickTemplatePath = { FilePicker.pickSave("保存 CSV 模板") },
            )
        },
        fundsPageContent = {
            FundsPage(
                service = runtime.fundService,
                calibration = runtime.calibrationService,
            )
        },
        onManualSync = syncViewModel::syncNow,
        manualSyncing = manualSyncing,
        manualSyncToast = manualSyncToast,
        onManualSyncToastDismiss = syncViewModel::dismissToast,
        onRefreshQuotes = refreshViewModel::refreshNow,
        refreshQuotesBusy = refreshBusy,
        refreshQuotesToast = refreshToast,
        onRefreshQuotesToastDismiss = refreshViewModel::dismissToast,
        shellStatus = status,
    )
}

/**
 * 原生文件选择（M7 CSV 导入/模板下载）：AWT FileDialog 必须在 EDT 线程执行。
 *
 * 线程口径（2026-09-07 GUI 走查修复轮）：调用方可能在协程线程（默认 Dispatcher）也可能在 EDT——
 * 直接 invokeAndWait 在 EDT 上会抛 IllegalStateException（"cannot call invokeAndWait from the event
 * dispatcher thread"），此前「下载标准模板」即因此崩溃。此处按当前线程分流：EDT 直接显示，
 * 否则 invokeAndWait 同步等待；返回所选文件绝对路径，取消返回 null。
 */
private object FilePicker {
    fun pickLoad(title: String): String? = pick(title, java.awt.FileDialog.LOAD)
    fun pickSave(title: String): String? = pick(title, java.awt.FileDialog.SAVE)

    private fun pick(title: String, mode: Int): String? {
        var result: String? = null
        val show = {
            val chooser = java.awt.FileDialog(null as java.awt.Frame?, title, mode)
            chooser.isVisible = true
            val file = chooser.file
            if (file != null) result = java.io.File(chooser.directory, file).absolutePath
        }
        if (java.awt.EventQueue.isDispatchThread()) show() else java.awt.EventQueue.invokeAndWait(show)
        return result
    }
}

/** 登录页用户名枚举开关（设置 通用，默认开；M10 起设置页可切换，登录页每次进入重读）。 */
private fun usernameEnumEnabled(runtime: AppBootstrap.Runtime): Boolean =
    runtime.settings.getGlobal("login.username.enum")?.let { it != "off" } ?: true

/** 诊断报告文本写盘（M10 T10.3；目录不存在自动创建）。 */
private fun writeTextFile(path: String, content: String) {
    val target = java.nio.file.Path.of(path)
    target.parent?.let { Files.createDirectories(it) }
    Files.write(
        target,
        content.toByteArray(Charsets.UTF_8),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
    )
}

@Composable
private fun FatalWindow(outcome: Outcome.Fatal, onExit: () -> Unit) {
    Window(
        onCloseRequest = onExit,
        title = "WuZhuFolio",
        state = rememberWindowState(width = 640.dp, height = 460.dp),
    ) {
        WuzhuTheme(themeMode = ThemeMode.LIGHT) {
            val colors = WzTheme.colors
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Text(
                    text = outcome.title,
                    color = colors.ink,
                    style = WzTheme.typography.pageTitle,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = outcome.message,
                    color = colors.ink2,
                    style = WzTheme.typography.body,
                )
                Spacer(Modifier.weight(1f))
                WzButton(
                    text = "退出",
                    onClick = onExit,
                    variant = WzButtonVariant.Primary,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

private object FatalOutcome {
    fun of(error: Throwable, logger: org.slf4j.Logger): Outcome.Fatal {
        logger.error("bootstrap failed", error)
        val (title, body) = when (error) {
            is DatabaseKeyMismatchException ->
                "无法解锁本地数据库" to
                    "本地数据库无法以当前主密钥解密——密钥与数据库不匹配（可能更换了机器/密钥文件，或数据目录被替换）。\n\n" +
                    "请保留数据目录（" + error.dbPath.parent + "）不要删除，退出后：\n" +
                    "① 确认 OS 钥匙串/密钥文件未变动；\n" +
                    "② 需要时从 .cpro 备份恢复（恢复向导随后续模块提供）。"
            is DatabaseOpenException ->
                "数据库打开失败" to
                    "无法打开本地数据库文件（" + error.message + "）。请检查数据目录权限与磁盘状态。"
            is MasterKeyFileException ->
                "本地密钥文件异常" to
                    error.message +
                    "\n\n请检查该文件是否完整（64 位 hex）。若已损坏且无备份，可移走该文件后重启——" +
                    "将生成新密钥，但旧数据库将无法解锁，需从 .cpro 备份恢复。"
            else ->
                "启动失败" to
                    "未预期错误：" + (error.message ?: error.javaClass.simpleName) +
                    "\n\n日志位置：" + AppDirs.logDir()
        }
        return Outcome.Fatal(title, body)
    }
}
