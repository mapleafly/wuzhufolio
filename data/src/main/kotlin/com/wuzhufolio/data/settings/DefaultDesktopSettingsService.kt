package com.wuzhufolio.data.settings

import com.wuzhufolio.domain.autostart.AutostartService
import com.wuzhufolio.domain.autostart.AutostartStatus
import com.wuzhufolio.domain.settings.DesktopSettingsService
import com.wuzhufolio.domain.settings.DesktopSettingsView
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * settings 键（托盘与后台；全局行 account_id NULL——口径见 domain/settings/DesktopSettings.kt 头注）。
 * 「on"/"off"」编码与 M10 通用设置同惯例（`value != "off"` 且缺省取默认值）。
 */
object DesktopSettingsKeys {
    const val MINIMIZE_ON_CLOSE: String = "tray.minimize_on_close"
    const val AUTOSTART_ENABLED: String = "autostart.enabled"
    const val SYNC_NOTIFICATION: String = "tray.sync_notification"
    const val BACKUP_REMINDER: String = "backup.reminder"
}

/**
 * 托盘与后台用例实现（M11 · T11.1/T11.2）。
 *
 * 两处口径需要留意：
 * 1. **自启「先注册后落键」**：注册失败不写键（避免 UI 显示已开启而系统未注册）；读写都以
 *    平台**真实注册态**为准（设置键 = 用户意图，用于启动自愈 [reconcileAutostart]）；
 * 2. **[prefs] 镜像**：关窗/通知判定发生在非 suspend 上下文（EDT），逐次查库会卡 UI——
 *    故每次读取与写入后同步刷新运行期镜像（与 [com.wuzhufolio.data.proxy.ProxyRuntime] 同模式）。
 */
class DefaultDesktopSettingsService(
    private val settings: SettingsRepository,
    private val autostart: AutostartService,
    private val prefs: DesktopPreferences = DesktopPreferences(),
    private val logger: Logger = LoggerFactory.getLogger("wuzhufolio.settings.desktop"),
) : DesktopSettingsService {

    override suspend fun view(): DesktopSettingsView {
        val status = runCatching { autostart.status() }.getOrNull()
        val view = DesktopSettingsView(
            minimizeOnClose = readFlag(DesktopSettingsKeys.MINIMIZE_ON_CLOSE, default = true),
            // 自启以平台真实注册态为准（能力不可用时退回设置键意图，UI 会同时给出置灰原因）
            autostartEnabled = status?.enabled ?: readFlag(DesktopSettingsKeys.AUTOSTART_ENABLED, false),
            syncNotification = readFlag(DesktopSettingsKeys.SYNC_NOTIFICATION, default = true),
            backupReminder = readFlag(DesktopSettingsKeys.BACKUP_REMINDER, default = true),
        )
        prefs.apply(view)
        return view
    }

    override fun autostartStatus(): AutostartStatus = autostart.status()

    override suspend fun setMinimizeOnClose(on: Boolean) {
        settings.putGlobal(DesktopSettingsKeys.MINIMIZE_ON_CLOSE, flag(on))
        prefs.setMinimizeOnClose(on)
        logger.info("minimize-on-close set | on=" + on)
    }

    override suspend fun setSyncNotification(on: Boolean) {
        settings.putGlobal(DesktopSettingsKeys.SYNC_NOTIFICATION, flag(on))
        prefs.setSyncNotification(on)
        logger.info("sync notification set | on=" + on)
    }

    override suspend fun setBackupReminder(on: Boolean) {
        settings.putGlobal(DesktopSettingsKeys.BACKUP_REMINDER, flag(on))
        prefs.setBackupReminder(on)
        logger.info("backup reminder set | on=" + on)
    }

    override suspend fun setAutostartEnabled(on: Boolean): Result<Unit> {
        val result = runCatching { if (on) autostart.enable() else autostart.disable() }.getOrElse {
            Result.failure(it)
        }
        return result.map {
            settings.putGlobal(DesktopSettingsKeys.AUTOSTART_ENABLED, flag(on))
            logger.info("autostart set | on=" + on)
        }.onFailure {
            logger.warn("autostart toggle failed | on=" + on + " | " + it.message)
        }
    }

    override suspend fun reconcileAutostart(): String {
        val status = runCatching { autostart.status() }.getOrElse {
            logger.warn("autostart status unavailable | " + it.javaClass.simpleName)
            null
        }
        if (status == null || !status.supported) return ""
        val wanted = readFlag(DesktopSettingsKeys.AUTOSTART_ENABLED, default = false)
        return when {
            wanted && !status.enabled -> autostart.enable().fold(
                onSuccess = {
                    logger.info("autostart re-registered at startup")
                    "已按设置补注册开机自启"
                },
                onFailure = { "开机自启补注册失败：" + (it.message ?: "原因未明") },
            )
            !wanted && status.enabled -> autostart.disable().fold(
                onSuccess = {
                    logger.info("stale autostart entry removed at startup")
                    "已清理残留的开机自启注册"
                },
                onFailure = { "开机自启注销失败：" + (it.message ?: "原因未明") },
            )
            else -> ""
        }
    }

    private fun readFlag(key: String, default: Boolean): Boolean =
        settings.getGlobal(key)?.let { it != "off" } ?: default

    private fun flag(on: Boolean): String = if (on) "on" else "off"
}
