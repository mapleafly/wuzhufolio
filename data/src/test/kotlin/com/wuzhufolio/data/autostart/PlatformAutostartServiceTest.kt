package com.wuzhufolio.data.autostart

import com.wuzhufolio.domain.autostart.AutostartPlatform
import com.wuzhufolio.domain.autostart.AutostartRules
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 开机自启平台实现（M11 T11.2）：三平台分支注入式验证。
 * 临时主目录 + 伪 reg 命令/读值 lambda——不碰真机注册表、不写真机主目录，可在 Linux 上跑通 Windows 分支。
 */
class PlatformAutostartServiceTest {

    private fun tempHome(): Path = Files.createTempDirectory("wuzhufolio-autostart")

    /** 测试夹具：五项即被测构造器的注入缝（平台/主目录/路径/命令/读注册表）。 */
    private fun newService(
        platform: AutostartPlatform,
        home: Path,
        executable: String?,
        runCommand: (List<String>) -> Int = { error("本用例不应执行外部命令") },
        readRegistryValue: (String) -> String? = { null },
    ): PlatformAutostartService = PlatformAutostartService(
        platform = platform,
        homeDir = home,
        executablePath = executable,
        runCommand = runCommand,
        readRegistryValue = readRegistryValue,
    )

    private fun linuxEntry(home: Path): Path =
        home.resolve(AutostartRules.relativeEntryPath(AutostartPlatform.LINUX))

    private fun macEntry(home: Path): Path =
        home.resolve(AutostartRules.relativeEntryPath(AutostartPlatform.MACOS))

    private fun fileCount(dir: Path): Long = Files.list(dir).use { it.count() }

    @Test
    fun `linux enable writes xdg desktop entry and disable removes it`() {
        val home = tempHome()
        val service = newService(AutostartPlatform.LINUX, home, EXE)
        assertTrue(service.enable().isSuccess, "注册应成功")

        val entry = linuxEntry(home)
        assertTrue(Files.isRegularFile(entry), "应写入 ~/.config/autostart/wuzhufolio.desktop")
        val text = Files.readString(entry)
        assertTrue("Type=Application" in text)
        assertTrue("Exec=" + EXE in text)
        assertTrue("X-GNOME-Autostart-enabled=true" in text)

        val status = service.status()
        assertTrue(status.supported)
        assertTrue(status.enabled)
        assertEquals(EXE, status.executablePath)
        assertNull(status.unsupportedReason, "支持注册时不应给不可用原因")

        assertTrue(service.disable().isSuccess)
        assertFalse(Files.exists(entry), "取消后文件应删除")
        assertFalse(service.status().enabled)
        assertTrue(service.disable().isSuccess, "未注册时取消自启也应成功（幂等）")
    }

    @Test
    fun `linux entry file restricted to owner read write on posix filesystems`() {
        val home = tempHome()
        assertTrue(newService(AutostartPlatform.LINUX, home, EXE).enable().isSuccess)
        // 非 POSIX 文件系统（Windows/NTFS）不支持该视图：按平台能力跳过（与 MasterKeyStoreTest 同口径）
        val perms = runCatching { Files.getPosixFilePermissions(linuxEntry(home)) }.getOrNull()
        if (perms != null) {
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                perms,
            )
        }
    }

    @Test
    fun `mac enable writes launch agent plist with executable and disable removes it`() {
        val home = tempHome()
        val service = newService(AutostartPlatform.MACOS, home, MAC_EXE)
        assertTrue(service.enable().isSuccess)

        val plist = macEntry(home)
        assertEquals(
            home.resolve("Library/LaunchAgents/com.wuzhufolio.app.plist"),
            plist,
            "LaunchAgent 相对主目录的既定落点",
        )
        assertTrue(Files.isRegularFile(plist))
        val text = Files.readString(plist)
        assertTrue(MAC_EXE in text, "可执行文件绝对路径应写入 ProgramArguments")
        assertTrue("<key>RunAtLoad</key>" in text)
        assertTrue(service.status().enabled)

        assertTrue(service.disable().isSuccess)
        assertFalse(Files.exists(plist))
        assertTrue(service.disable().isSuccess, "未注册时取消自启也应成功（幂等）")
    }

    @Test
    fun `windows enable invokes reg add with expected arguments`() {
        val home = tempHome()
        val calls = mutableListOf<List<String>>()
        val service = newService(AutostartPlatform.WINDOWS, home, WIN_EXE, runCommand = { calls += it; 0 })
        assertTrue(service.enable().isSuccess)
        assertEquals(
            listOf(
                "reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v", "WuZhuFolio", "/t", "REG_SZ",
                "/d", "\"" + WIN_EXE + "\"", "/f",
            ),
            calls.single(),
            "含空格路径按 Windows Run 值标准写法加引号",
        )
        assertEquals(0L, fileCount(home), "Windows 分支不落文件")
    }

    @Test
    fun `windows disable invokes reg delete`() {
        val home = tempHome()
        val calls = mutableListOf<List<String>>()
        val service = newService(AutostartPlatform.WINDOWS, home, WIN_EXE, runCommand = { calls += it; 0 })
        assertTrue(service.disable().isSuccess)
        assertEquals(
            listOf("reg", "delete", AutostartRules.windowsRunKey(), "/v", "WuZhuFolio", "/f"),
            calls.single(),
        )
    }

    @Test
    fun `windows status reads back registry value`() {
        val home = tempHome()
        var value: String? = null
        val service = newService(AutostartPlatform.WINDOWS, home, WIN_EXE, readRegistryValue = { value })
        val off = service.status()
        assertTrue(off.supported)
        assertFalse(off.enabled, "读值缺失 = 未注册")

        value = "\"" + WIN_EXE + "\""
        assertTrue(service.status().enabled, "读值存在 = 已注册")
        assertEquals(0L, fileCount(home), "读状态不得落文件")
    }

    @Test
    fun `windows disable treats already absent value as success`() {
        val calls = mutableListOf<List<String>>()
        // reg delete 对「值不存在」同样返回非 0：读回无残留即按幂等成功处理
        val service = newService(
            AutostartPlatform.WINDOWS,
            tempHome(),
            WIN_EXE,
            runCommand = { calls += it; 1 },
            readRegistryValue = { null },
        )
        assertTrue(service.disable().isSuccess)
        assertEquals(1, calls.size)
    }

    @Test
    fun `windows enable failure returns chinese failure`() {
        val service = newService(AutostartPlatform.WINDOWS, tempHome(), WIN_EXE, runCommand = { 1 })
        val result = service.enable()
        assertTrue(result.isFailure)
        assertTrue("注册开机自启失败" in result.exceptionOrNull()?.message.orEmpty())
    }

    @Test
    fun `unsupported status never throws when registry read fails`() {
        val service = newService(
            AutostartPlatform.WINDOWS,
            tempHome(),
            WIN_EXE,
            readRegistryValue = { throw IllegalStateException("reg 不可用") },
        )
        val status = service.status()
        assertTrue(status.supported)
        assertFalse(status.enabled, "读失败按未启用兜底，不得抛出")
    }

    @Test
    fun `unresolved executable path is unsupported and enable has no side effect`() {
        for (platform in AutostartPlatform.entries) {
            val home = tempHome()
            val calls = mutableListOf<List<String>>()
            val service = newService(platform, home, null, runCommand = { calls += it; 0 })

            val status = service.status()
            assertFalse(status.supported, platform.name + " 无路径即不可注册")
            assertFalse(status.enabled)
            assertNull(status.executablePath)
            assertNotNull(status.unsupportedReason)
            assertTrue("无法推断可执行文件路径" in status.unsupportedReason.orEmpty(), "原因须是中文可读句")

            val failure = service.enable()
            assertTrue(failure.isFailure)
            assertTrue("无法推断可执行文件路径" in failure.exceptionOrNull()?.message.orEmpty())
            assertTrue(calls.isEmpty(), "不应执行任何外部命令")
            assertEquals(0L, fileCount(home), "不应落任何注册文件")
        }
    }

    @Test
    fun `write failure returns chinese failure instead of throwing`() {
        val home = tempHome()
        Files.writeString(home.resolve(".config"), "不是目录") // 阻断 createDirectories
        val service = newService(AutostartPlatform.LINUX, home, EXE)
        val result = service.enable()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException, "失败一律包成中文原因的 IllegalStateException")
        assertTrue("注册开机自启失败" in result.exceptionOrNull()?.message.orEmpty())
    }

    private companion object {
        /** 无空格路径（Linux/macOS 与 Windows 通用）。 */
        const val EXE = "/opt/wuzhufolio/bin/WuZhuFolio"

        /** 打包后的 macOS 启动器。 */
        const val MAC_EXE = "/Applications/WuZhuFolio.app/Contents/MacOS/WuZhuFolio"

        /** 含空格的 Windows 安装路径（验证 Run 值加引号）。 */
        const val WIN_EXE = "C:\\Program Files\\WuZhuFolio\\WuZhuFolio.exe"
    }
}
