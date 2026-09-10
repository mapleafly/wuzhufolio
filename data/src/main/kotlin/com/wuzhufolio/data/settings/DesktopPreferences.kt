package com.wuzhufolio.data.settings

import com.wuzhufolio.domain.settings.DesktopSettingsView

/**
 * 桌面集成偏好运行期镜像（M11）。
 *
 * 存在的理由：托盘/关窗/通知三处判定发生在**非 suspend 上下文**（AWT 关窗回调、托盘菜单点击、
 * 通知发送前），逐次查库会阻塞 EDT。故由 [DefaultDesktopSettingsService] 在读取与写入后
 * 同步镜像，判定点只读内存（`@Volatile`，单写者 = 设置服务）。
 *
 * 与 [com.wuzhufolio.data.proxy.ProxyRuntime] 同模式（网络线程读开关同理）。
 */
class DesktopPreferences {

    @Volatile
    var minimizeOnClose: Boolean = true
        private set

    @Volatile
    var syncNotification: Boolean = true
        private set

    @Volatile
    var backupReminder: Boolean = true
        private set

    /** 以最新视图整体刷新镜像（设置页读取后调用）。 */
    fun apply(view: DesktopSettingsView) {
        minimizeOnClose = view.minimizeOnClose
        syncNotification = view.syncNotification
        backupReminder = view.backupReminder
    }

    // 单项写入后同步（不可用 apply(DesktopSettingsView(...))——那会把未涉及的项重置为默认值）

    fun setMinimizeOnClose(on: Boolean) {
        minimizeOnClose = on
    }

    fun setSyncNotification(on: Boolean) {
        syncNotification = on
    }

    fun setBackupReminder(on: Boolean) {
        backupReminder = on
    }
}
