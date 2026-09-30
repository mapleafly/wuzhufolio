package com.wuzhufolio.app.tray.linux

import com.wuzhufolio.app.tray.TrayMenuActions
import com.wuzhufolio.ui.tray.TrayLabels
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.slf4j.Logger
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Linux 原生托盘服务（**D39 / DEF-57 A 方案**）：StatusNotifierItem + `com.canonical.dbusmenu`。
 *
 * 菜单**由桌面 Shell 自己渲染**（GNOME 的 AppIndicator 扩展即此协议的宿主），因此菜单的输入归 Shell 所有，
 * 从根上绕开 DEF-57 实测的「点击托盘图标后 Shell 在合成器层吞掉指针事件、自绘弹窗拿不到鼠标」问题；
 * 菜单文本也由 Shell 绘制（Linux 侧不再有 DEF-15 的 AWT 菜单文本问题）。
 *
 * 交互口径与 Compose 自绘菜单（Windows/macOS）**逐项一致**：
 * 左键 = 打开主界面（`ItemIsMenu=false` ⇒ Shell 调 `Activate`）；右键 = 四项菜单（含一条分隔线）；
 * 点选经 `Event(id, "clicked")` 派发到**同一套** [TrayMenuActions] 回调。
 *
 * 失败即降级：无会话总线 / 无 watcher / 注册异常时返回 null，调用方回退到 AWT 托盘 + Compose 菜单。
 */
internal class StatusNotifierService private constructor(
    private val connection: DBusConnection,
    private val iconDir: Path?,
    private val logger: Logger,
) : AutoCloseable {

    override fun close() {
        runCatching { connection.disconnect() }
        runCatching { iconDir?.toFile()?.deleteRecursively() }
    }

    /** SNI 对象（`/StatusNotifierItem`）。 */
    internal class Item(
        private val labels: () -> TrayLabels,
        private val actions: TrayMenuActions,
        private val iconName: String,
        private val iconDir: Path?,
        private val menuPath: String,
        private val logger: Logger,
    ) : StatusNotifierItem, RawProperties {

        override fun getObjectPath(): String = SNI_PATH

        // ── 动作 ──────────────────────────────────────────────────────────────
        override fun Activate(x: Int, y: Int) {
            logger.debug("tray sni activate (left click)")
            actions.onOpen()
        }

        override fun SecondaryActivate(x: Int, y: Int) = Activate(x, y)

        /** 部分 Shell 直接调它来要菜单；此处只记日志（菜单由 dbusmenu 提供）。 */
        override fun ContextMenu(x: Int, y: Int) {
            logger.debug("tray sni context menu requested at {}x{}", x, y)
        }

        override fun Scroll(delta: Int, orientation: String) = Unit

        // ── 属性（自实现 org.freedesktop.DBus.Properties，避免依赖 dbus-java 注解推导）──
        override fun Get(interfaceName: String, propertyName: String): Variant<*> =
            (property(propertyName) as? Variant<*>) ?: Variant("")

        override fun Set(interfaceName: String, propertyName: String, value: Variant<*>) = Unit

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = mapOf(
            "Category" to Variant("ApplicationStatus"),
            "Id" to Variant("wuzhufolio"),
            "Title" to Variant("WuZhuFolio"),
            "Status" to Variant("Active"),
            "IconName" to Variant(iconName),
            "IconThemePath" to Variant(iconThemePath(iconDir), "s"),
            "ItemIsMenu" to Variant(false),
            // Menu 的 D-Bus 类型是**对象路径 o**（不是 s）——GNOME 扩展会校验类型，发错即整个指示器无菜单
            "Menu" to Variant(menuPath, "o"),
            "WindowId" to Variant(0),
        )

        private fun property(name: String): Any? = when (name) {
            "Category" -> Variant("ApplicationStatus")
            "Id" -> Variant("wuzhufolio")
            "Title" -> Variant("WuZhuFolio")
            "Status" -> Variant("Active")
            "IconName" -> Variant(iconName)
            "IconThemePath" -> Variant(iconThemePath(iconDir), "s")
            "ItemIsMenu" -> Variant(false)
            "Menu" -> Variant(menuPath, "o")
            "WindowId" -> Variant(0)
            else -> throw org.freedesktop.dbus.exceptions.DBusExecutionException("Unknown property $name")
        }
    }

    /** dbusmenu 对象（`/MenuBar`）。 */
    @Suppress("TooManyFunctions") // D-Bus 接口方法数量由协议决定（dbusmenu 7 个方法 + 属性 3 个）
    internal class Menu(
        private val labels: () -> TrayLabels,
        private val actions: TrayMenuActions,
        private val iconDir: Path?,
        private val logger: Logger,
    ) : DbusMenu {

        override fun getObjectPath(): String = MENU_PATH

        private val items: List<DbusMenuItem> get() = DbusMenuModel.items(labels())

        override fun GetLayout(
            parentId: Int,
            recursionDepth: Int,
            propertyNames: List<String>,
        ): GetLayoutResult<UInt32, LayoutNode> {
            val root = DbusMenuModel.layout(items)
            val node = if (parentId == DbusMenuModel.ROOT_ID) {
                root
            } else {
                root.children.map { it.value as LayoutNode }.firstOrNull { it.id == parentId } ?: root
            }
            return GetLayoutResult(UInt32(REVISION.toLong()), node)
        }

        override fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<PropertyGroup> =
            nodes().filter { ids.isEmpty() || it.id in ids }.map { PropertyGroup(it.id, it.props) }

        private fun nodes(): List<LayoutNode> =
            DbusMenuModel.layout(items).children.map { it.value as LayoutNode }

        override fun GetProperty(id: Int, name: String): Variant<*> {
            val node = nodes().firstOrNull { it.id == id }
            return node?.props?.get(name) ?: Variant("")
        }

        override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
            logger.debug("tray sni menu event | id={} event={}", id, eventId)
            if (eventId != "clicked") return
            when (id) {
                DbusMenuModel.ACTION_OPEN -> actions.onOpen()
                DbusMenuModel.ACTION_SYNC -> actions.onSync()
                DbusMenuModel.ACTION_REFRESH -> actions.onRefresh()
                DbusMenuModel.ACTION_QUIT -> actions.onQuit()
                else -> Unit
            }
        }

        override fun EventGroup(events: List<EventEntry>): List<Int> {
            events.forEach { Event(it.id, it.eventId, it.data, it.timestamp) }
            return emptyList()
        }

        override fun AboutToShow(id: Int): Boolean = false

        override fun AboutToShowGroup(ids: List<Int>): AboutToShowGroupResult<List<Int>, List<Int>> =
            AboutToShowGroupResult(emptyList(), emptyList())

        // ── 属性 ──────────────────────────────────────────────────────────────
        override fun Get(interfaceName: String, propertyName: String): Variant<*> =
            (property(propertyName) as? Variant<*>) ?: Variant("")

        override fun Set(interfaceName: String, propertyName: String, value: Variant<*>) = Unit

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = mapOf(
            "Version" to Variant(UInt32(DBUSMENU_VERSION.toLong())),
            "TextDirection" to Variant("ltr"),
            "Status" to Variant("normal"),
            "IconThemePath" to Variant(iconThemePaths(iconDir), "as"),
        )

        private fun property(name: String): Any? = when (name) {
            "Version" -> Variant(UInt32(DBUSMENU_VERSION.toLong()))
            "TextDirection" -> Variant("ltr")
            "Status" -> Variant("normal")
            "IconThemePath" -> Variant(iconThemePath(iconDir), "s")
            else -> Variant("")
        }
    }

    companion object {
        private const val SNI_PATH = "/StatusNotifierItem"
        private const val MENU_PATH = "/MenuBar"
        private const val WATCHER_NAME = "org.kde.StatusNotifierWatcher"
        private const val WATCHER_PATH = "/StatusNotifierWatcher"
        private const val ICON_NAME = "wuzhufolio"
        private const val DBUSMENU_VERSION = 3
        private const val REVISION = 1

        /**
         * 启动服务；失败返回 null（调用方回退 AWT 托盘）。
         *
         * @param icon 托盘图标（复用 `TrayIcon.awtImage()` 的渲染结果，写为 PNG 供 Shell 读取）
         * @param labels 菜单文案提供者（**按需读取**，语言切换后菜单自动跟随）
         */
        fun start(
            icon: BufferedImage?,
            labels: () -> TrayLabels,
            actions: TrayMenuActions,
            logger: Logger,
        ): StatusNotifierService? = runCatching {
            val busName = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
            val iconDir = writeIcon(icon, logger)
            val connection = DBusConnectionBuilder.forSessionBus().build()
            connection.requestBusName(busName)
            connection.exportObject(
                SNI_PATH,
                Item(labels, actions, ICON_NAME, iconDir, MENU_PATH, logger),
            )
            connection.exportObject(MENU_PATH, Menu(labels, actions, iconDir, logger))
            val watcher = connection.getRemoteObject(WATCHER_NAME, WATCHER_PATH, StatusNotifierWatcher::class.java)
            watcher.RegisterStatusNotifierItem(busName)
            logger.info("tray sni registered | bus={} iconDir={}", busName, iconDir)
            StatusNotifierService(connection, iconDir, logger)
        }.getOrElse {
            // 打印完整堆栈：注册失败原因需要可诊断（且失败即回退 AWT 托盘，不影响可用性）
            logger.info("tray sni unavailable: " + it.message, it)
            null
        }

        /** 把图标写成 `<tempDir>/wuzhufolio.png`（`IconThemePath` + `IconName` 供 Shell 读取）。 */
        private fun writeIcon(icon: BufferedImage?, logger: Logger): Path? = runCatching {
            if (icon == null) return@runCatching null
            val dir = Files.createTempDirectory("wuzhufolio-tray-icon")
            val file = dir.resolve("$ICON_NAME.png")
            ImageIO.write(icon, "png", file.toFile())
            dir
        }.getOrElse {
            logger.info("tray sni icon export failed: {}", it.message)
            null
        }
    }
}

/**
 * `IconThemePath`（D-Bus `as`）：dbus-java 需要**显式签名**，否则 `Collections$SingletonList`
 * 无法包装为 Variant（实测报错 "Can't wrap class java.util.Collections$SingletonList"）。
 */
/** SNI 的 `IconThemePath` 是**单个字符串** `s`（与 dbusmenu 的 `as` 不同，实测 Shell 会校验类型）。 */
private fun iconThemePath(iconDir: Path?): String = iconDir?.toAbsolutePath()?.toString() ?: ""

private fun iconThemePaths(iconDir: Path?): List<String> =
    iconDir?.toAbsolutePath()?.toString()?.let { java.util.ArrayList(listOf(it)) } ?: java.util.ArrayList()
