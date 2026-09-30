package com.wuzhufolio.app.tray.linux

import com.wuzhufolio.ui.tray.TrayLabels
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/**
 * dbusmenu（`com.canonical.dbusmenu`）布局模型（**DEF-57 / D39，A 方案**）。
 *
 * Linux 上托盘菜单改由 **桌面 Shell 自己渲染**：应用通过 StatusNotifierItem 注册图标，并导出一个
 * `com.canonical.dbusmenu` 对象描述菜单结构；用户点选后 Shell 回调 `Event(id, "clicked")`。
 * 这样菜单的**输入由 Shell 自己拥有**，不再受「Shell 在合成器层面吞掉指针事件」影响（DEF-57 五轮实测）。
 *
 * 本文件是**纯模型 + 类型映射**（不碰总线，可单测）：把菜单项翻译成 dbusmenu 要求的递归结构
 * `(ia{sv}av)` —— id / 属性字典 / 子项数组（子项又是同一结构，故用 [Variant] 包一层）。
 */
internal data class DbusMenuItem(
    /** 菜单项 id（`Event` 回调按它派发动作）。 */
    val id: Int,
    val label: String,
    /** 是否为分隔线（dbusmenu 用 `type=separator` 表达，不显示 label）。 */
    val separator: Boolean = false,
)

internal object DbusMenuModel {
    /** dbusmenu 根节点固定为 id 0。 */
    const val ROOT_ID: Int = 0

    const val ACTION_OPEN: Int = 1
    const val ACTION_SYNC: Int = 2
    const val ACTION_REFRESH: Int = 3
    const val ACTION_QUIT: Int = 4

    /** 分隔线 id（与动作项区分，Shell 不会回调它）。 */
    const val SEPARATOR_ID: Int = 90

    /**
     * 菜单结构（顺序即显示顺序）：打开主界面 / 立即同步交易 / 立即刷新行情 / ─── / 退出。
     * 与 Compose 自绘菜单（Windows/macOS）**完全一致**，保证两端口径相同。
     */
    fun items(labels: TrayLabels): List<DbusMenuItem> = listOf(
        DbusMenuItem(ACTION_OPEN, labels.open),
        DbusMenuItem(ACTION_SYNC, labels.syncNow),
        DbusMenuItem(ACTION_REFRESH, labels.refreshQuotes),
        DbusMenuItem(SEPARATOR_ID, "", separator = true),
        DbusMenuItem(ACTION_QUIT, labels.quit),
    )

    /** 由菜单项构造 dbusmenu 布局根节点。 */
    fun layout(items: List<DbusMenuItem>): LayoutNode = LayoutNode(
        id = ROOT_ID,
        props = linkedMapOf("children-display" to Variant("submenu")),
        children = items.map { Variant(node(it), LayoutNode::class.java) },
    )

    private fun node(item: DbusMenuItem): LayoutNode {
        val props: MutableMap<String, Variant<*>> = linkedMapOf(
            "visible" to Variant(true),
            "enabled" to Variant(true),
        )
        if (item.separator) {
            props["type"] = Variant("separator")
        } else {
            props["label"] = Variant(item.label)
        }
        return LayoutNode(id = item.id, props = props, children = emptyList())
    }
}

/**
 * dbusmenu 布局节点：`(ia{sv}av)`。
 *
 * dbus-java 的 `Struct` 要求**字段**（而非构造参数）标注 `@Position`，故这里用 `@JvmField` 暴露字段。
 */
internal class LayoutNode(
    @Position(0) @JvmField val id: Int,
    @Position(1) @JvmField val props: Map<String, Variant<*>>,
    @Position(2) @JvmField val children: List<Variant<*>>,
) : Struct()

/**
 * `GetLayout` 的出参 `(u(ia{sv}av))`：dbus-java 用 `Tuple` 子类表达**多返回值**。
 *
 * ⚠️ **必须是泛型的**：实测 dbus-java 4.2.1 对**非泛型** `Tuple` 子类做签名推导时抛
 * `ClassCastException: Class cannot be cast to ParameterizedType`（导出即失败）；
 * 泛型形态（对照 `secret-service` 的 `Pair<A,B>`）才能正常内省与编解码。
 * 守护测试：`StatusNotifierExportTest`。
 */
internal class GetLayoutResult<A, B>(
    @Position(0) @JvmField val revision: A,
    @Position(1) @JvmField val layout: B,
) : Tuple()

/** `AboutToShowGroup` 的出参 `(aiai)`（同样必须泛型，理由见 [GetLayoutResult]）。 */
internal class AboutToShowGroupResult<A, B>(
    @Position(0) @JvmField val updatesNeeded: A,
    @Position(1) @JvmField val idErrors: B,
) : Tuple()

/** `a(ia{sv})`：属性分组（`GetGroupProperties`）。 */
internal class PropertyGroup(
    @Position(0) @JvmField val id: Int,
    @Position(1) @JvmField val props: Map<String, Variant<*>>,
) : Struct()

/** `a(isvu)`：批量事件（`EventGroup`）。 */
internal class EventEntry(
    @Position(0) @JvmField val id: Int,
    @Position(1) @JvmField val eventId: String,
    @Position(2) @JvmField val data: Variant<*>,
    @Position(3) @JvmField val timestamp: UInt32,
) : Struct()
