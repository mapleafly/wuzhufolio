package com.wuzhufolio.app.tray

import com.wuzhufolio.data.schedule.SchedulerEvent

/** 关窗动作（T11.1 验收「托盘驻留行为正确」的可判定形式）。 */
enum class CloseAction {
    /** 最小化到托盘驻留（PRD 6.1：关闭窗口默认最小化到托盘）。 */
    HIDE_TO_TRAY,

    /** 直接退出应用。 */
    EXIT,
}

/**
 * 关窗决策（M11 · T11.1）。
 *
 * 抽成纯函数的理由：该判定是托盘行为的**全部逻辑**（其余是 AWT/Compose 的机械动作），
 * 而 WSLg 等开发环境 `SystemTray.isSupported()` 为 false、托盘路径无法 GUI 走查——
 * 把决策独立出来即可用单测钉死，环境无关。
 *
 * 口径：
 * - 托盘**不可用** → 一律 [CloseAction.EXIT]（绝不能把窗口藏进不存在的托盘：用户会以为已退出，
 *   进程却仍在后台跑，且没有任何入口能唤回窗口）；
 * - 托盘可用且开关为开 → [CloseAction.HIDE_TO_TRAY]（PRD 6.1 默认值）；
 * - 用户显式关闭开关（改为直接退出）→ [CloseAction.EXIT]。
 */
object WindowCloseBehavior {

    fun decide(traySupported: Boolean, minimizeOnClose: Boolean): CloseAction =
        if (traySupported && minimizeOnClose) CloseAction.HIDE_TO_TRAY else CloseAction.EXIT
}

/**
 * 通知派发策略（M11 · T11.1 · PRD §9.4「同步通知可在设置中关闭」+ §7.2 模块 6.1「备份提醒开关」）。
 *
 * 事件 → 通知的映射与开关判定独立于托盘通道（托盘不可用时只记日志、不打扰），
 * 故同样抽为纯函数以便单测覆盖两级开关。
 */
object NoticePolicy {

    /**
     * 事件对应通知；不需要通知/开关关闭 → null。
     * 行情刷新与日志轮转**不发通知**（前者默认 5 分钟一轮会成噪音，后者纯内部维护）；
     * 429 频发文案的 UI 展示归 M12 状态栏。
     */
    fun noticeFor(
        event: SchedulerEvent,
        syncNotification: Boolean,
        backupReminder: Boolean,
    ): DesktopNotice? = when (event) {
        is SchedulerEvent.SyncFinished ->
            if (syncNotification) DesktopNoticeText.syncFinished(event.results) else null
        is SchedulerEvent.BackupReminderDue ->
            if (backupReminder) DesktopNoticeText.backupReminder(event.daysSinceBase) else null
        is SchedulerEvent.MarketRefreshed,
        is SchedulerEvent.LogRotated,
        is SchedulerEvent.RateLimitFrequent,
        -> null
    }
}
