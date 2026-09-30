package com.wuzhufolio.app.tray

/**
 * 托盘动作的会话门禁（**D40 + D43**）：未登录（会话锁定）时，托盘上的联网动作**一律不执行**，
 * 只给出「请先登录」提示。
 *
 * 口径（2026-09-29 人工拍板）：
 * - **行情刷新**：行情 Key 虽为设备级，但「登录前不产生任何外联」是本产品的隐私口径（D40）；
 * - **交易同步**：交易所 Key **归属账户**（`api_keys.account_id`），未登录根本没有可同步的账户（D43）。
 *
 * 收口在**入口**（而非只靠数据层）：数据层同样有门禁（`BackgroundScheduler.syncOnce` /
 * `DefaultExchangeSyncService.requireActive`），但只靠它会让用户看到误导文案
 * （「正在同步…」→「没有可同步的密钥」），故入口先判、文案直达原因。
 */
object TrayActionGate {

    /**
     * 会话锁定时返回「请先登录」通知；已登录返回 `null`（调用方继续执行动作）。
     * [action] 为动作名（用于文案：「登录后才能<action>。」）。
     */
    fun lockedNotice(hasSession: Boolean, action: String): DesktopNotice? =
        if (hasSession) null else DesktopNoticeText.loginRequired(action)
}
