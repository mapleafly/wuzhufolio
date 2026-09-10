package com.wuzhufolio.domain.autostart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 开机自启纯规则（M11 T11.2）：平台判定 / 注册落点 / plist 与 .desktop 内容 / 命令行推断。 */
class AutostartRulesTest {

    @Test
    fun `detect maps windows mac and linux os names`() {
        assertEquals(AutostartPlatform.WINDOWS, AutostartRules.detect("Windows 10"))
        assertEquals(AutostartPlatform.WINDOWS, AutostartRules.detect("Windows 11"))
        assertEquals(AutostartPlatform.MACOS, AutostartRules.detect("Mac OS X"))
        assertEquals(AutostartPlatform.MACOS, AutostartRules.detect("Darwin"))
        assertEquals(AutostartPlatform.LINUX, AutostartRules.detect("Linux"))
    }

    @Test
    fun `detect falls back to linux for unknown or missing os name`() {
        assertEquals(AutostartPlatform.LINUX, AutostartRules.detect("FreeBSD"))
        assertEquals(AutostartPlatform.LINUX, AutostartRules.detect(""))
        assertEquals(AutostartPlatform.LINUX, AutostartRules.detect(null))
    }

    @Test
    fun `relative entry path per platform`() {
        assertEquals("", AutostartRules.relativeEntryPath(AutostartPlatform.WINDOWS), "Windows 走注册表，无文件")
        assertEquals(
            "Library/LaunchAgents/com.wuzhufolio.app.plist",
            AutostartRules.relativeEntryPath(AutostartPlatform.MACOS),
        )
        assertEquals(
            ".config/autostart/wuzhufolio.desktop",
            AutostartRules.relativeEntryPath(AutostartPlatform.LINUX),
        )
    }

    @Test
    fun `windows run key targets hkcu to avoid admin rights`() {
        assertEquals(
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
            AutostartRules.windowsRunKey(),
        )
    }

    @Test
    fun `mac plist declares label program arguments and run at load`() {
        val exe = "/Applications/WuZhuFolio.app/Contents/MacOS/WuZhuFolio"
        val plist = AutostartRules.macPlist(exe)
        assertTrue("<key>Label</key>" in plist)
        assertTrue("<string>" + AutostartRules.MAC_LABEL + "</string>" in plist)
        assertTrue("<key>ProgramArguments</key>" in plist)
        assertTrue("<string>" + exe + "</string>" in plist, "启动器绝对路径应作为唯一参数写入")
        assertTrue("<key>RunAtLoad</key>" in plist && "<true/>" in plist, "登录即加载")
        assertTrue("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" in plist)
    }

    @Test
    fun `mac plist escapes xml sensitive characters`() {
        val plist = AutostartRules.macPlist("/opt/a&b/<c>/App")
        assertTrue("/opt/a&amp;b/&lt;c&gt;/App" in plist)
        assertTrue("/opt/a&b/<c>/App" !in plist, "原始字符不得直接落入 XML 文本")
    }

    @Test
    fun `linux desktop entry declares xdg autostart keys`() {
        val exe = "/opt/wuzhufolio/bin/WuZhuFolio"
        val entry = AutostartRules.linuxDesktopEntry(exe)
        assertTrue("[Desktop Entry]" in entry)
        assertTrue("Type=Application" in entry)
        assertTrue("Name=" + AutostartRules.DISPLAY_NAME in entry)
        assertTrue("Exec=" + exe in entry)
        assertTrue("Terminal=false" in entry)
        assertTrue("X-GNOME-Autostart-enabled=true" in entry, "GNOME 需显式启用位")
    }

    @Test
    fun `quote only wraps windows paths containing spaces`() {
        val spaced = "C:\\Program Files\\WuZhuFolio\\WuZhuFolio.exe"
        assertEquals("\"" + spaced + "\"", AutostartRules.quoteForPlatform(AutostartPlatform.WINDOWS, spaced))
        assertEquals(
            "C:\\WuZhuFolio\\WuZhuFolio.exe",
            AutostartRules.quoteForPlatform(AutostartPlatform.WINDOWS, "C:\\WuZhuFolio\\WuZhuFolio.exe"),
            "无空格不加引号",
        )
        assertEquals(spaced, AutostartRules.quoteForPlatform(AutostartPlatform.LINUX, spaced))
        assertEquals(spaced, AutostartRules.quoteForPlatform(AutostartPlatform.MACOS, spaced))
    }

    @Test
    fun `resolve prefers jpackage app path`() {
        assertEquals(
            "/opt/wuzhufolio/bin/WuZhuFolio",
            AutostartCommand.resolve("/opt/wuzhufolio/bin/WuZhuFolio", "/usr/bin/java -cp app.jar MainKt"),
        )
    }

    @Test
    fun `resolve takes quoted first token from command line`() {
        val line = "\"C:\\Program Files\\Java\\bin\\java.exe\" -cp wuzhufolio.jar MainKt"
        assertEquals("C:\\Program Files\\Java\\bin\\java.exe", AutostartCommand.resolve(null, line))
    }

    @Test
    fun `resolve takes unquoted first token and keeps java exe name as is`() {
        val jvm = "/usr/lib/jvm/temurin-17/bin/java"
        assertEquals(jvm, AutostartCommand.resolve(null, jvm + " -cp app.jar MainKt"))
        assertEquals("javaw.exe", AutostartCommand.resolve(null, "javaw.exe -jar app.jar"), "不做 java/javaw 改写")
        assertEquals("java", AutostartCommand.resolve(null, "java"))
    }

    @Test
    fun `resolve returns null for blank missing or malformed input`() {
        assertNull(AutostartCommand.resolve(null, null), "开发态命令行为空")
        assertNull(AutostartCommand.resolve("", ""))
        assertNull(AutostartCommand.resolve("   ", "   "))
        assertNull(AutostartCommand.resolve(null, "\"unterminated -cp app.jar"), "引号未闭合不可靠，宁可不注册")
        assertNull(AutostartCommand.resolve(null, "\"\""))
    }

    @Test
    fun `resolve falls back to command line when jpackage path is blank`() {
        assertEquals("/usr/bin/java", AutostartCommand.resolve("  ", "/usr/bin/java -cp app.jar MainKt"))
    }

    @Test
    fun `unsupported status carries the shared chinese reason`() {
        val status = AutostartStatus.unsupported()
        assertFalse(status.supported)
        assertFalse(status.enabled)
        assertNull(status.executablePath)
        assertEquals(AutostartRules.UNRESOLVED_EXECUTABLE_REASON, status.unsupportedReason)
    }
}
