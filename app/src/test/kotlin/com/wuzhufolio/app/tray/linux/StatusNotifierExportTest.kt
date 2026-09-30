package com.wuzhufolio.app.tray.linux

import com.wuzhufolio.app.tray.TrayMenuActions
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.ui.tray.trayLabels
import org.freedesktop.dbus.messages.ExportedObject
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * 导出对象可被 dbus-java 内省（D39 / DEF-57 A 方案）。
 *
 * 为什么需要这条测试：dbus-java 在 `exportObject` 时会对**每个方法**推导 D-Bus 签名，
 * 遇到它无法解析的泛型形态会直接抛 `ClassCastException`（实测：自实现 `Properties` 的泛型方法、
 * 以及部分 `Variant<*>` 组合），而这只在**运行时导出**才暴露——单测把它提前到构建期。
 */
class StatusNotifierExportTest {

    private val labels = { trayLabels(AppLanguage.ZH) }
    private val noop = TrayMenuActions(
        onDismiss = {},
        onOpen = {},
        onSync = {},
        onRefresh = {},
        onQuit = {},
    )
    private val logger = LoggerFactory.getLogger(StatusNotifierExportTest::class.java)

    @Test
    fun `SNI 对象可导出`() {
        val item = StatusNotifierService.Item(labels, noop, "wuzhufolio", null, "/MenuBar", logger)
        assertNotNull(ExportedObject(item, true).introspectiondata)
    }

    @Test
    fun `dbusmenu 对象可导出`() {
        val menu = StatusNotifierService.Menu(labels, noop, null, logger)
        assertNotNull(ExportedObject(menu, true).introspectiondata)
    }
}
