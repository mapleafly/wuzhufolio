package com.wuzhufolio.app.tray

import com.wuzhufolio.data.schedule.SchedulerEvent
import com.wuzhufolio.domain.exchange.ApiKeySyncResult
import com.wuzhufolio.domain.exchange.SyncStatus
import com.wuzhufolio.domain.market.MarketRefreshResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 桌面集成决策（M11 T11.1）：关窗行为 + 通知派发开关。
 *
 * 为什么这些必须是单测（而非只靠 GUI 走查）：当前开发环境（WSLg）`SystemTray.isSupported()`
 * 为 false，托盘路径无法 GUI 验证；把决策抽成纯函数后，托盘行为的**全部逻辑**在此钉死，
 * 环境相关的只剩机械动作（见模块记录 M11 §5）。
 */
class DesktopBehaviorTest {

    @Test
    fun `close hides to tray only when supported and switch on`() {
        assertEquals(CloseAction.HIDE_TO_TRAY, WindowCloseBehavior.decide(traySupported = true, minimizeOnClose = true))
        assertEquals(CloseAction.EXIT, WindowCloseBehavior.decide(traySupported = true, minimizeOnClose = false))
    }

    @Test
    fun `close exits when tray is unavailable regardless of the switch`() {
        // 关键防呆：托盘不可用时绝不能把窗口藏起来——用户会以为已退出，进程却仍在后台且无入口唤回
        assertEquals(CloseAction.EXIT, WindowCloseBehavior.decide(traySupported = false, minimizeOnClose = true))
        assertEquals(CloseAction.EXIT, WindowCloseBehavior.decide(traySupported = false, minimizeOnClose = false))
    }

    @Test
    fun `sync notification honours its switch`() {
        val event = SchedulerEvent.SyncFinished(listOf(syncResult()))
        val on = NoticePolicy.noticeFor(event, syncNotification = true, backupReminder = true)
        assertEquals("同步完成", on?.title)
        assertNull(
            NoticePolicy.noticeFor(event, syncNotification = false, backupReminder = true),
            "PRD 9.4：同步通知可在设置中关闭",
        )
    }

    @Test
    fun `backup reminder honours its own switch`() {
        val event = SchedulerEvent.BackupReminderDue(daysSinceBase = 45)
        assertEquals(
            "建议备份",
            NoticePolicy.noticeFor(event, syncNotification = true, backupReminder = true)?.title,
        )
        assertNull(
            NoticePolicy.noticeFor(event, syncNotification = true, backupReminder = false),
            "PRD 6.1：备份提醒为独立开关",
        )
        assertNotNull(
            NoticePolicy.noticeFor(event, syncNotification = false, backupReminder = true),
            "同步通知开关不得连带关掉备份提醒（两者独立）",
        )
    }

    @Test
    fun `market and maintenance events never notify`() {
        assertNull(NoticePolicy.noticeFor(marketEvent(), syncNotification = true, backupReminder = true))
        assertNull(
            NoticePolicy.noticeFor(
                SchedulerEvent.LogRotated("files=1"),
                syncNotification = true,
                backupReminder = true,
            ),
            "运行期轮转纯内部维护，不打扰用户",
        )
        assertNull(
            NoticePolicy.noticeFor(
                SchedulerEvent.RateLimitFrequent(strike = 3, keylessHint = true),
                syncNotification = true,
                backupReminder = true,
            ),
            "429 频发文案的界面展示归 M12 状态栏",
        )
    }

    @Test
    fun `sync event with no keys yields no notice even when enabled`() {
        assertNull(
            NoticePolicy.noticeFor(
                SchedulerEvent.SyncFinished(emptyList()),
                syncNotification = true,
                backupReminder = true,
            ),
        )
    }

    private fun marketEvent() = SchedulerEvent.MarketRefreshed(
        MarketRefreshResult(
            at = Instant.parse("2026-09-10T00:00:00Z"),
            source = com.wuzhufolio.domain.market.PriceSource.COINGECKO,
            refreshedCoins = 4,
            untracked = emptyList(),
            quotaPercentUsed = null,
            error = null,
            cgConfigured = false,
            cmcConfigured = false,
        ),
    )

    private fun syncResult() = ApiKeySyncResult(
        apiKeyId = 1,
        apiKeyName = "主账户",
        status = SyncStatus.OK,
        newTrades = 2,
        duplicatesSkipped = 0,
        unresolvedSkipped = 0,
        partial = false,
        queuedSymbols = 0,
        error = null,
        message = "ok",
        at = Instant.parse("2026-09-10T00:00:00Z"),
    )
}
