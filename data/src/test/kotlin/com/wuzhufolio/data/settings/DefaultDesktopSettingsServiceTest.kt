package com.wuzhufolio.data.settings

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.randomDbKey
import com.wuzhufolio.domain.autostart.AutostartService
import com.wuzhufolio.domain.autostart.AutostartStatus
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 托盘与后台用例（M11 T11.1/T11.2）：默认值 / 持久化 / 自启先注册后落键 / 启动自愈 / 运行期镜像。 */
class DefaultDesktopSettingsServiceTest {

    private class FakeAutostart(
        var supported: Boolean = true,
        var registered: Boolean = false,
        var failEnable: Boolean = false,
        var failDisable: Boolean = false,
    ) : AutostartService {
        var enableCalls = 0
        var disableCalls = 0

        override fun status(): AutostartStatus = AutostartStatus(
            supported = supported,
            enabled = registered,
            executablePath = if (supported) "/opt/wuzhufolio/bin/WuZhuFolio" else null,
            unsupportedReason = if (supported) null else "无法推断可执行文件路径（未打包运行）——打包安装后可用",
        )

        override fun enable(): Result<Unit> {
            enableCalls++
            if (failEnable) return Result.failure(IllegalStateException("注册开机自启失败：权限不足"))
            registered = true
            return Result.success(Unit)
        }

        override fun disable(): Result<Unit> {
            disableCalls++
            if (failDisable) return Result.failure(IllegalStateException("取消开机自启失败：权限不足"))
            registered = false
            return Result.success(Unit)
        }
    }

    private class Env : AutoCloseable {
        val db: WzDatabase = WzDatabase(
            Files.createTempDirectory("wuzhufolio-desktop-settings").resolve("desktop.db"),
            randomDbKey(),
        )
        val gate: DbGate = DbGate(db)
        val settings = SettingsRepository(gate)

        init {
            db.migrateToLatest()
        }

        override fun close() {
            db.close()
        }
    }

    @Test
    fun `defaults follow prd before any write`() = runBlocking {
        Env().use { env ->
            val autostart = FakeAutostart(registered = false)
            val service = DefaultDesktopSettingsService(env.settings, autostart)
            val view = service.view()
            assertTrue(view.minimizeOnClose, "PRD 6.1：关闭窗口默认最小化到托盘")
            assertEquals(false, view.autostartEnabled, "PRD 9.3：开机自启默认关闭")
            assertTrue(view.syncNotification, "PRD 9.4：同步通知默认开")
            assertTrue(view.backupReminder, "PRD 6.1：备份提醒默认开启")
        }
    }

    @Test
    fun `switches persist and drive the runtime mirror`() = runBlocking {
        Env().use { env ->
            val prefs = DesktopPreferences()
            val service = DefaultDesktopSettingsService(env.settings, FakeAutostart(), prefs)
            service.setMinimizeOnClose(false)
            assertEquals(false, prefs.minimizeOnClose, "关窗判定走内存镜像（EDT 不查库）")
            assertTrue(prefs.syncNotification, "单项写入不得重置其他项")
            service.setSyncNotification(false)
            service.setBackupReminder(false)

            // 重新构造服务（模拟重启）：值从 settings 全局行读回
            val reloaded = DefaultDesktopSettingsService(env.settings, FakeAutostart(), DesktopPreferences()).view()
            assertEquals(false, reloaded.minimizeOnClose)
            assertEquals(false, reloaded.syncNotification)
            assertEquals(false, reloaded.backupReminder)
        }
    }

    @Test
    fun `autostart registers before persisting the key`() = runBlocking {
        Env().use { env ->
            val autostart = FakeAutostart()
            val service = DefaultDesktopSettingsService(env.settings, autostart)
            assertTrue(service.setAutostartEnabled(true).isSuccess)
            assertEquals(1, autostart.enableCalls)
            assertEquals("on", env.settings.getGlobal(DesktopSettingsKeys.AUTOSTART_ENABLED))
            assertTrue(service.view().autostartEnabled, "视图取平台真实注册态")

            assertTrue(service.setAutostartEnabled(false).isSuccess)
            assertEquals(1, autostart.disableCalls)
            assertEquals("off", env.settings.getGlobal(DesktopSettingsKeys.AUTOSTART_ENABLED))
        }
    }

    @Test
    fun `failed registration leaves the key untouched`() = runBlocking {
        Env().use { env ->
            val autostart = FakeAutostart(failEnable = true)
            val service = DefaultDesktopSettingsService(env.settings, autostart)
            val result = service.setAutostartEnabled(true)
            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull()?.message?.contains("权限不足") == true,
                "失败原因原样透出给设置页提示",
            )
            assertEquals(null, env.settings.getGlobal(DesktopSettingsKeys.AUTOSTART_ENABLED), "失败不落键")
            assertEquals(false, service.view().autostartEnabled, "视图不得谎报已开启")
        }
    }

    @Test
    fun `reconcile re-registers when key on but registration missing`() = runBlocking {
        Env().use { env ->
            env.settings.putGlobal(DesktopSettingsKeys.AUTOSTART_ENABLED, "on")
            val autostart = FakeAutostart(registered = false)
            val note = DefaultDesktopSettingsService(env.settings, autostart).reconcileAutostart()
            assertEquals(1, autostart.enableCalls)
            assertTrue(note.contains("补注册"), "返回中文修正摘要：" + note)
        }
    }

    @Test
    fun `reconcile removes stale registration when key off`() = runBlocking {
        Env().use { env ->
            // 键缺省 = off，但平台仍有残留注册项
            val autostart = FakeAutostart(registered = true)
            val note = DefaultDesktopSettingsService(env.settings, autostart).reconcileAutostart()
            assertEquals(1, autostart.disableCalls)
            assertTrue(note.contains("残留"), "返回中文修正摘要：" + note)
        }
    }

    @Test
    fun `reconcile is a no-op when state already agrees`() = runBlocking {
        Env().use { env ->
            env.settings.putGlobal(DesktopSettingsKeys.AUTOSTART_ENABLED, "on")
            val autostart = FakeAutostart(registered = true)
            val note = DefaultDesktopSettingsService(env.settings, autostart).reconcileAutostart()
            assertEquals("", note)
            assertEquals(0, autostart.enableCalls)
            assertEquals(0, autostart.disableCalls)
        }
    }

    @Test
    fun `reconcile skips unsupported platform`() = runBlocking {
        Env().use { env ->
            env.settings.putGlobal(DesktopSettingsKeys.AUTOSTART_ENABLED, "on")
            val autostart = FakeAutostart(supported = false)
            val note = DefaultDesktopSettingsService(env.settings, autostart).reconcileAutostart()
            assertEquals("", note)
            assertEquals(0, autostart.enableCalls, "能力不可用时不尝试注册（开发态不写坏注册项）")
            assertEquals(false, DefaultDesktopSettingsService(env.settings, autostart).autostartStatus().supported)
        }
    }
}
