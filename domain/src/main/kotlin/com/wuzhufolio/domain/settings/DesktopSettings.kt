package com.wuzhufolio.domain.settings

import com.wuzhufolio.domain.autostart.AutostartStatus

/**
 * 桌面集成设置契约（M11 · T11.1/T11.2 · PRD §7.2 模块 6.1「托盘与后台」+ 6.1「备份提醒」+ §9.1/9.3/9.4；
 * ia.md §2.12 该分组四行；design-tokens §5）。
 *
 * 存储口径（settings 全局行 key-value，与 [GeneralSettingsService] 同表同惯例，"on"/"off"，缺省按默认值）：
 * - `tray.minimize_on_close`  = 关闭窗口最小化到托盘（默认 **on**；PRD 6.1「关闭窗口最小化到托盘」，
 *                               design-tokens §5「关闭窗口默认最小化到托盘（设置可改为直接退出）」）；
 * - `autostart.enabled`       = 开机自动启动（驻留托盘，默认 **off**；PRD §9.3）；
 * - `tray.sync_notification`  = 同步完成/失败桌面通知（默认 **on**；PRD §9.4「可在设置中关闭」）；
 * - `backup.reminder`         = 备份提醒（默认 **on**；PRD 6.1「距上次备份超过 30 天时在状态栏/通知提示」）。
 *
 * 本模块只落这四项「设备级偏好」；托盘/自启/通知的行为载体分别在 app 组合根与 data 平台注册层。
 * 与 M10 的 `network.proxy.enabled` 同属设备级偏好，但分属不同分组，故不并入 [GeneralSettingsService]。
 */

/** 「托盘与后台」设置视图（ui 直接消费）。 */
data class DesktopSettingsView(
    val minimizeOnClose: Boolean = true,
    val autostartEnabled: Boolean = false,
    val syncNotification: Boolean = true,
    val backupReminder: Boolean = true,
)

/** 托盘与后台用例（T11.1/T11.2）：视图读取 + 单项写入（写后下次读取生效）。 */
interface DesktopSettingsService {

    /** 当前视图（含自启实际注册态校正，见实现层说明）。 */
    suspend fun view(): DesktopSettingsView

    /**
     * 开机自启的平台能力与真实注册态（T11.2；设置页据此置灰开关并给出原因）。
     * 与 [view]`.autostartEnabled` 的区别：本方法额外暴露「是否支持」与「用哪个可执行文件注册」。
     */
    fun autostartStatus(): AutostartStatus

    /** 关闭窗口最小化到托盘（false = 直接退出应用）。 */
    suspend fun setMinimizeOnClose(on: Boolean)

    /** 同步完成/失败桌面通知开关。 */
    suspend fun setSyncNotification(on: Boolean)

    /** 备份提醒开关（距上次备份 > 30 天提示，PRD 6.1）。 */
    suspend fun setBackupReminder(on: Boolean)

    /**
     * 开机自启（PRD §9.3：默认关闭）。
     *
     * 语义 = **先平台注册成功、后落设置键**：注册失败返回 [Result.failure]（附中文原因）
     * 且不写键，避免出现「设置显示已开启、系统实际未注册」的假象；
     * 关闭方向同理（注销失败不写键）。幂等。
     */
    suspend fun setAutostartEnabled(on: Boolean): Result<Unit>

    /**
     * 启动自愈（引导期调用一次）：设置键 = 开启但平台注册缺失（如重装/移动安装目录后注册项被清理）
     * 时按当前可执行路径补注册；设置键 = 关闭但注册项残留时清理。失败只记日志、不阻断启动。
     * 返回本次修正动作的中文摘要（无需修正 = 空串）。
     */
    suspend fun reconcileAutostart(): String
}
