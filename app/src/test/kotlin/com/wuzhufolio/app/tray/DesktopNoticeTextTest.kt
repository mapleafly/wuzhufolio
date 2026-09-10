package com.wuzhufolio.app.tray

import com.wuzhufolio.domain.exchange.ApiKeySyncResult
import com.wuzhufolio.domain.exchange.SyncStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 桌面通知文案（M11 T11.1）：同步完成/失败与备份提醒的措辞与截断口径。 */
class DesktopNoticeTextTest {

    /** 结果行构造器（测试夹具；参数多是为了在用例里只写关心的维度）。 */
    @Suppress("LongParameterList")
    private fun result(
        name: String = "主账户",
        status: SyncStatus = SyncStatus.OK,
        newTrades: Int = 0,
        duplicates: Int = 0,
        unresolved: Int = 0,
        message: String = "ok",
    ) = ApiKeySyncResult(
        apiKeyId = 1,
        apiKeyName = name,
        status = status,
        newTrades = newTrades,
        duplicatesSkipped = duplicates,
        unresolvedSkipped = unresolved,
        partial = false,
        queuedSymbols = 0,
        error = null,
        message = message,
        at = Instant.parse("2026-09-10T00:00:00Z"),
    )

    /** 非空断言（[DesktopNoticeText.syncFinished] 语义上可空：无密钥 = 不发通知）。 */
    private fun notice(results: List<ApiKeySyncResult>): DesktopNotice =
        assertNotNull(DesktopNoticeText.syncFinished(results), "有密钥时必产通知")

    @Test
    fun `no keys means no notification`() {
        assertNull(DesktopNoticeText.syncFinished(emptyList()), "未配置交易所不该收到空同步提示")
    }

    @Test
    fun `success notice reports new trades and dedup`() {
        val notice = notice(listOf(result(newTrades = 12, duplicates = 120)))
        assertEquals("同步完成", notice.title)
        assertEquals(NoticeLevel.INFO, notice.level)
        assertEquals("新增 12 笔交易 · 去重跳过 120 笔", notice.message)
    }

    @Test
    fun `success notice with nothing new stays informative`() {
        val notice = notice(listOf(result(newTrades = 0, duplicates = 5)))
        assertEquals("无新增交易 · 去重跳过 5 笔", notice.message)
    }

    @Test
    fun `partial unresolved is surfaced`() {
        val notice = notice(listOf(result(newTrades = 1, unresolved = 3)))
        assertTrue(notice.message.contains("待解析 3 笔"), notice.message)
    }

    @Test
    fun `totals aggregate across keys`() {
        val notice = notice(
            listOf(result(name = "A", newTrades = 2), result(name = "B", newTrades = 3, duplicates = 4)),
        )
        assertEquals("新增 5 笔交易 · 去重跳过 4 笔", notice.message)
    }

    @Test
    fun `all failed reports failure with reason`() {
        val notice = notice(
            listOf(result(status = SyncStatus.FAILED, message = "API Key 无效")),
        )
        assertEquals("同步失败", notice.title)
        assertEquals(NoticeLevel.ERROR, notice.level)
        assertEquals("主账户：API Key 无效", notice.message)
    }

    @Test
    fun `partial failure is distinguished and counted`() {
        val notice = notice(
            listOf(
                result(name = "A", status = SyncStatus.FAILED, message = "网络不可达"),
                result(name = "B", newTrades = 1),
            ),
        )
        assertEquals("部分密钥同步失败", notice.title)
        assertTrue(notice.message.contains("共 1/2 个密钥失败"), notice.message)
    }

    @Test
    fun `blank reason falls back to a neutral phrase`() {
        val notice = notice(
            listOf(result(status = SyncStatus.FAILED, message = "  ")),
        )
        assertTrue(notice.message.endsWith("原因未明"), notice.message)
    }

    @Test
    fun `overlong messages are truncated to protect the bubble`() {
        val notice = notice(
            listOf(result(status = SyncStatus.FAILED, message = "x".repeat(400))),
        )
        assertEquals(DesktopNoticeText.MESSAGE_LIMIT, notice.message.length)
        assertTrue(notice.message.endsWith("…"))
    }

    @Test
    fun `backup reminder states the elapsed days`() {
        val notice = DesktopNoticeText.backupReminder(41)
        assertEquals("建议备份", notice.title)
        assertEquals(NoticeLevel.WARNING, notice.level)
        assertTrue(notice.message.contains("超过 30 天"), notice.message)
        assertTrue(notice.message.contains("41 天"), notice.message)
        assertTrue(notice.message.contains(".cpro"), "指明导出方式")
    }
}
