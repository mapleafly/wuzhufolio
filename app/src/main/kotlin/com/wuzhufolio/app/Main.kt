package com.wuzhufolio.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wuzhufolio.data.db.DatabaseKeyMismatchException
import com.wuzhufolio.data.db.DatabaseOpenException
import com.wuzhufolio.data.security.MasterKeyFileException
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.shell.MainShell
import com.wuzhufolio.ui.shell.ShellViewModel
import com.wuzhufolio.ui.theme.WuzhuTheme
import com.wuzhufolio.ui.theme.WzTheme
import org.slf4j.LoggerFactory

/**
 * 应用入口（M1 起）：引导（AppBootstrap）→ 主壳；引导失败渲染致命提示窗口
 * （错钥/密钥文件损坏/钥匙串异常等）。正式 B1「从 .cpro 恢复」页面随 M2/M9 提供（interaction.md §2.1）。
 *
 * 2026-09-03 修复：引导结果显式收敛到密封 Outcome 后再分支——此前 runCatching.getOrElse 把
 * 返回类型推断为 Any（Runtime 与 Outcome.Fatal 的 LUB），when 的 is Outcome.Ready 永不命中，
 * 成功路径不组合任何窗口即静默退出（失败路径恰命中 Fatal 分支，故未暴露）。
 */
fun main() {
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
                MainWindow(runtime, onExit = { runtime.close(); exitApplication() })
            }
            is Outcome.Fatal -> FatalWindow(outcome, onExit = { exitApplication() })
        }
    }
}

private sealed interface Outcome {
    data class Ready(val runtime: AppBootstrap.Runtime) : Outcome
    data class Fatal(val title: String, val message: String) : Outcome
}

@Composable
private fun MainWindow(runtime: AppBootstrap.Runtime, onExit: () -> Unit) {
    val viewModel = remember { ShellViewModel(runtime.uiState.theme, runtime.uiState.pnlScheme) }
    Window(
        onCloseRequest = onExit,
        title = "WuZhuFolio",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        MainShell(viewModel = viewModel, startupNotice = runtime.uiState.securityNotice)
    }
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
