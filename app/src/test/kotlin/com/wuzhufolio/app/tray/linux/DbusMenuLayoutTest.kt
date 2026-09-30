package com.wuzhufolio.app.tray.linux

import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.ui.tray.trayLabels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * dbusmenu 布局模型单测（D39 / DEF-57 A 方案）。
 *
 * 钉住三件事：① 菜单项与 Compose 自绘菜单**逐项一致**（两端口径不得漂移）；
 * ② 布局是协议要求的递归结构 `(ia{sv}av)`（根 id=0、子项为 variant 包裹的同一结构）；
 * ③ 分隔线用 `type=separator` 表达且不带 label。
 */
class DbusMenuLayoutTest {

    private val labels = trayLabels(AppLanguage.ZH)

    @Test
    fun `菜单项与 Compose 菜单逐项一致`() {
        val items = DbusMenuModel.items(labels)
        assertEquals(
            listOf(
                DbusMenuModel.ACTION_OPEN,
                DbusMenuModel.ACTION_SYNC,
                DbusMenuModel.ACTION_REFRESH,
                DbusMenuModel.SEPARATOR_ID,
                DbusMenuModel.ACTION_QUIT,
            ),
            items.map { it.id },
        )
        assertEquals(labels.open, items[0].label)
        assertEquals(labels.syncNow, items[1].label)
        assertEquals(labels.refreshQuotes, items[2].label)
        assertEquals(labels.quit, items[4].label)
    }

    @Test
    fun `布局为协议要求的递归结构`() {
        val layout = DbusMenuModel.layout(DbusMenuModel.items(labels))
        assertEquals(DbusMenuModel.ROOT_ID, layout.id, "根节点 id 必须为 0")
        assertEquals("submenu", layout.props.getValue("children-display").value)
        assertEquals(5, layout.children.size, "四项动作 + 一条分隔线")

        // 子项必须是 variant 包裹的 LayoutNode（协议：av）
        val first = layout.children.first().value
        val node = first as? LayoutNode
        assertTrue(node != null, "子项应为 LayoutNode，实际 ${first?.javaClass?.simpleName}")
        assertEquals(DbusMenuModel.ACTION_OPEN, node.id)
        assertEquals(labels.open, node.props.getValue("label").value)
        assertEquals(true, node.props.getValue("enabled").value)
        assertEquals(true, node.props.getValue("visible").value)
    }

    @Test
    fun `分隔线用 type separator 表达且无 label`() {
        val layout = DbusMenuModel.layout(DbusMenuModel.items(labels))
        val separator = layout.children
            .map { it.value as LayoutNode }
            .single { it.id == DbusMenuModel.SEPARATOR_ID }
        assertEquals("separator", separator.props.getValue("type").value)
        assertTrue(separator.props["label"] == null, "分隔线不应带 label")
        assertTrue(separator.children.isEmpty())
    }
}
