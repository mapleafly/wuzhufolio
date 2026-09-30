package com.wuzhufolio.app.tray

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D40 + D43 守护：托盘联网动作（刷新行情 / 同步交易）在**会话锁定**时必须只给「请先登录」提示、
 * 不进入任何联网路径；已登录时放行（返回 null 由调用方继续执行）。
 */
class TrayActionGateTest {

    @Test
    fun `locked session yields a login-required notice for both tray actions`() {
        for (action in listOf("刷新行情", "同步交易数据")) {
            val n = assertNotNull(
                TrayActionGate.lockedNotice(hasSession = false, action = action),
                "会话锁定时 $action 必须给出提示而非静默跳过",
            )
            assertEquals("请先登录", n.title)
            assertTrue(n.message.contains(action), "提示应点名具体动作：${n.message}")
            assertTrue(n.message.contains("登录后才能"), "提示应说明原因：${n.message}")
        }
    }

    @Test
    fun `active session passes through without a notice`() {
        assertNull(TrayActionGate.lockedNotice(hasSession = true, action = "同步交易数据"))
        assertNull(TrayActionGate.lockedNotice(hasSession = true, action = "刷新行情"))
    }
}
