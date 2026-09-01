package com.wuzhufolio.app

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wuzhufolio.app.di.appModule
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.hello.HelloChain
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.redaction.LogRedactor
import com.wuzhufolio.ui.shell.MainShell
import com.wuzhufolio.ui.shell.ShellViewModel
import org.koin.core.context.startKoin
import org.slf4j.LoggerFactory

/**
 * 应用入口（M0）：
 * 1. T0.4 hello 链路——空界面启动 -> 迁移建库 -> 读设置 -> 打一条脱敏日志；
 * 2. T0.6 UI 基座——双主题主壳（侧边栏五页空壳 + 组件走查页）。
 */
fun main() {
    System.setProperty("wuzhufolio.logdir", AppDirs.logDir().toString())
    val logger = LoggerFactory.getLogger("wuzhufolio.bootstrap")

    val db = WzDatabase(AppDirs.dbPath())
    val settings = SettingsRepository(db)
    val hello = HelloChain(db, settings).run()

    val koin = startKoin { modules(appModule(db, settings)) }.koin
    check(koin.get<WzDatabase>() === db) { "koin wiring failed" }

    logger.info(
        LogRedactor.redact(
            "bootstrap ok | db=" + AppDirs.dbPath() +
                " | theme=" + hello.theme.storageValue +
                " | pnl_scheme=" + hello.pnlScheme.storageValue,
        ),
    )

    application {
        val viewModel = remember { ShellViewModel(hello.theme, hello.pnlScheme) }
        Window(
            onCloseRequest = {
                db.close()
                exitApplication()
            },
            title = "WuZhuFolio",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            MainShell(viewModel)
        }
    }
}
