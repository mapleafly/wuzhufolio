package com.wuzhufolio.app.tray.linux

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/**
 * Linux 原生托盘集成所需的 D-Bus 接口定义（**DEF-57 / D39，A 方案**）。
 *
 * 三个接口：
 * 1. [StatusNotifierItem]（`org.kde.StatusNotifierItem`）—— 托盘图标本体：图标、提示、左右键动作；
 * 2. [DbusMenu]（`com.canonical.dbusmenu`）—— 菜单结构：Shell 调 `GetLayout` 取结构、点选后回调 `Event`；
 * 3. [StatusNotifierWatcher]（`org.kde.StatusNotifierWatcher`）—— 注册入口（GNOME 的 AppIndicator 扩展即此服务的实现）。
 *
 * **属性统一走 [Properties] 接口**（`Get`/`GetAll`/`Set`）自实现，而不是依赖 dbus-java 的注解推导：
 * 实测 dbus-java 4.2.1 的 `@DBusProperty` 只用于生成 introspection XML（TYPE 级、可重复注解），
 * 由对象自行实现 `Properties` 最直接、最可控。
 */
// 方法名沿用 D-Bus 协议原名（首字母大写）——dbus-java 默认以 Java 方法名作为线上 member 名，
// 改名即协议不匹配，故此处显式豁免命名规则。
@Suppress("FunctionNaming")
@DBusInterfaceName("org.kde.StatusNotifierItem")
internal interface StatusNotifierItem : DBusInterface {
    /** 左键单击（`ItemIsMenu=false` 时由 Shell 调用）。 */
    fun Activate(x: Int, y: Int)

    /** 中键单击（本项目不区分，等同 [Activate]）。 */
    fun SecondaryActivate(x: Int, y: Int)

    /** 右键请求菜单（部分 Shell 会直接调它而不是自行取 dbusmenu 布局）。 */
    fun ContextMenu(x: Int, y: Int)

    /** 滚轮（本项目忽略）。 */
    fun Scroll(delta: Int, orientation: String)
}

/**
 * `org.freedesktop.DBus.Properties` 的**非泛型**等价接口。
 *
 * 为什么不直接用 dbus-java 自带的 [Properties]：其实测在 4.2.1 下**无法导出**——
 * 导出时它要对方法做 D-Bus 签名推导，遇到 `<A> A Get(...)` 这类类型变量会抛
 * `Class cannot be cast to ParameterizedType`。这里用返回/入参明确的等价接口即可正常工作。
 */
@Suppress("FunctionNaming")
@DBusInterfaceName("org.freedesktop.DBus.Properties")
internal interface RawProperties : DBusInterface {
    fun Get(interfaceName: String, propertyName: String): Variant<*>

    fun GetAll(interfaceName: String): Map<String, Variant<*>>

    fun Set(interfaceName: String, propertyName: String, value: Variant<*>)
}

@Suppress("FunctionNaming")
@DBusInterfaceName("com.canonical.dbusmenu")
internal interface DbusMenu : DBusInterface, RawProperties {
    /** 取菜单结构：返回 `(u(ia{sv}av))`（revision + 递归布局）。 */
    fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): GetLayoutResult<UInt32, LayoutNode>

    fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<PropertyGroup>

    fun GetProperty(id: Int, name: String): Variant<*>

    /** 用户点选/菜单关闭等事件；本项目只关心 `clicked`。 */
    fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)

    fun EventGroup(events: List<EventEntry>): List<Int>

    fun AboutToShow(id: Int): Boolean

    fun AboutToShowGroup(ids: List<Int>): AboutToShowGroupResult<List<Int>, List<Int>>
}

@Suppress("FunctionNaming")
@DBusInterfaceName("org.kde.StatusNotifierWatcher")
internal interface StatusNotifierWatcher : DBusInterface {
    /** 注册托盘项（传本应用的 bus name）。 */
    fun RegisterStatusNotifierItem(service: String)

    fun RegisterStatusNotifierHost(service: String)
}
