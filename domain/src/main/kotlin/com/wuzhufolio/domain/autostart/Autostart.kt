package com.wuzhufolio.domain.autostart

/**
 * 开机自启平台（M11 · T11.2 · task-breakdown §3；PRD §7.2 模块 9.3）。
 *
 * 注册方式：WINDOWS = HKCU Run 注册表值（当前用户级，免管理员权限）；MACOS = LaunchAgent plist；
 * LINUX = XDG autostart `.desktop`。
 */
enum class AutostartPlatform { WINDOWS, MACOS, LINUX }

/**
 * 开机自启纯规则（M11 · T11.2）：平台判定、注册落点、注册文件内容与命令行引号口径。
 *
 * 默认关闭（PRD §7.2 模块 9.3「默认关闭；开启后自启驻留托盘」）：本对象只给规则，是否注册由用户在设置页
 * 开关拍板。:domain 无文件系统/OS 调用，真正的落盘与注册表执行见 data 模块 `PlatformAutostartService`。
 */
object AutostartRules {

    /** macOS LaunchAgent 标签（= 注册文件名主干）。 */
    const val MAC_LABEL: String = "com.wuzhufolio.app"

    /** 展示名：Windows 注册表值名 / Linux `.desktop` 的 Name。 */
    const val DISPLAY_NAME: String = "WuZhuFolio"

    /** 无法推断可执行文件路径时的统一中文原因（未打包运行；status().unsupportedReason 与 enable() 失败原因同源）。 */
    const val UNRESOLVED_EXECUTABLE_REASON: String = "无法推断可执行文件路径（未打包运行）——打包安装后可用"

    /** Windows 自启注册表键（HKCU = 当前用户，避免管理员权限）。 */
    private const val WINDOWS_RUN_KEY: String = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"

    /**
     * os.name（System.getProperty("os.name")）→ 平台；未知按 LINUX（桌面 Linux 兜底，与既有 os 判定先例一致）。
     *
     * macOS 分支必须先于 Windows 判定：JDK 在 macOS 上写的是 "Mac OS X"/"Darwin"，而 "darwin" 里正好含子串 "win"。
     */
    fun detect(osName: String?): AutostartPlatform {
        val name = osName.orEmpty().lowercase()
        return when {
            name.contains("mac") || name.contains("darwin") -> AutostartPlatform.MACOS
            name.contains("win") -> AutostartPlatform.WINDOWS
            else -> AutostartPlatform.LINUX
        }
    }

    /**
     * 注册文件相对用户主目录的路径：macOS = `Library/LaunchAgents/com.wuzhufolio.app.plist`；
     * Linux = `.config/autostart/wuzhufolio.desktop`；Windows = ""（走注册表，无文件）。
     */
    fun relativeEntryPath(platform: AutostartPlatform): String = when (platform) {
        AutostartPlatform.WINDOWS -> ""
        AutostartPlatform.MACOS -> "Library/LaunchAgents/" + MAC_LABEL + ".plist"
        AutostartPlatform.LINUX -> ".config/autostart/wuzhufolio.desktop"
    }

    /** Windows 自启注册表键（HKCU，避免管理员权限）。 */
    fun windowsRunKey(): String = WINDOWS_RUN_KEY

    /**
     * macOS LaunchAgent plist 内容（登录即加载 = RunAtLoad；[command] 为可执行文件绝对路径）。
     *
     * 不写 DOCTYPE：plist 无外部 DTD 引用同样合法，避免任何解析期外部依赖（PRD §1.1 硬约束「无网络调用」）。
     */
    fun macPlist(command: String): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<plist version=\"1.0\">")
        appendLine("<dict>")
        appendLine("    <key>Label</key>")
        appendLine("    <string>" + MAC_LABEL + "</string>")
        appendLine("    <key>ProgramArguments</key>")
        appendLine("    <array>")
        appendLine("        <string>" + xmlEscape(command) + "</string>")
        appendLine("    </array>")
        appendLine("    <key>RunAtLoad</key>")
        appendLine("    <true/>")
        appendLine("</dict>")
        appendLine("</plist>")
    }

    /**
     * Linux XDG autostart `.desktop` 内容：Type=Application / Name / Exec / Terminal=false /
     * X-GNOME-Autostart-enabled=true（GNOME 显式启用位，缺省时部分桌面环境不生效）。
     */
    fun linuxDesktopEntry(command: String): String = buildString {
        appendLine("[Desktop Entry]")
        appendLine("Type=Application")
        appendLine("Name=" + DISPLAY_NAME)
        appendLine("Comment=" + DISPLAY_NAME + " 开机自启（驻留系统托盘）")
        appendLine("Exec=" + command)
        appendLine("Terminal=false")
        appendLine("X-GNOME-Autostart-enabled=true")
    }

    /**
     * 开机自启命令行 = 可执行文件绝对路径；含空格时按平台加引号（Windows 用双引号包裹，Run 值标准写法；
     * macOS/Linux 的 ProgramArguments/Exec 字段本身就是单个参数，不需要引号）。
     */
    fun quoteForPlatform(platform: AutostartPlatform, executablePath: String): String =
        if (platform == AutostartPlatform.WINDOWS && executablePath.contains(' ')) {
            "\"" + executablePath + "\""
        } else {
            executablePath
        }

    /** XML 文本转义（路径含 & < > 时保证 plist 合法；& 必须最先替换）。 */
    private fun xmlEscape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

/**
 * 自启注册状态（UI 展示用）。
 *
 * [supported] 的口径见 `PlatformAutostartService.status` 的 KDoc：本对象只承载状态，不做判定。
 */
data class AutostartStatus(
    val supported: Boolean,
    /** 当前是否已注册（读回注册表/文件判定）。 */
    val enabled: Boolean,
    /** 注册将使用的可执行文件路径（null = 无法推断，例如开发态未打包运行）。 */
    val executablePath: String?,
    /** 不可注册时的中文原因（supported=true 时 null）；由 [AutostartService.status] 填充。 */
    val unsupportedReason: String? = null,
) {
    companion object {
        /**
         * 无法推断可执行文件路径且**尚未注册**时的统一状态（设置页首帧占位可用）。
         * 注意 [enabled] 固定 false：已由打包版注册过的机器应改用 [AutostartService.status] 的真实读回值。
         */
        fun unsupported(): AutostartStatus = AutostartStatus(
            supported = false,
            enabled = false,
            executablePath = null,
            unsupportedReason = AutostartRules.UNRESOLVED_EXECUTABLE_REASON,
        )
    }
}

/**
 * 开机自启用例（PRD §7.2 模块 9.3：默认关闭；开启后自启驻留托盘）。
 * 实现见 data 模块 PlatformAutostartService（三平台注册；所有方法不抛异常）。
 */
interface AutostartService {

    /** 读取当前状态（含是否支持与可执行文件路径推断）。 */
    fun status(): AutostartStatus

    /** 注册自启（幂等：已注册则覆盖刷新路径）。失败返回 Result.failure（附中文明因）。 */
    fun enable(): Result<Unit>

    /** 取消自启（幂等：未注册也算成功）。 */
    fun disable(): Result<Unit>
}

/**
 * 自启命令行推断纯规则（M11 · T11.2）：把「用哪个可执行文件注册」这一步从 OS 调用里剥出来，便于单测。
 */
object AutostartCommand {

    /**
     * 推断用于自启的可执行文件绝对路径，优先级：
     * 1. [jpackageAppPath]（jpackage 打包后的启动器，Compose Desktop 会写入 jpackage.app-path 系统属性）；
     * 2. [commandLine] 的首个 token（开发态 `java -cp ... MainKt` 时 = java 可执行文件路径）。
     * 两者都不可用时返回 null（调用方据此把开关置为不可用，见 AutostartStatus.unsupportedReason）。
     *
     * 保留 java.exe 原样，不做 java.exe → javaw.exe 之类的改写（不臆测用户意图；打包态第 1 优先级已覆盖）。
     */
    fun resolve(jpackageAppPath: String?, commandLine: String?): String? =
        jpackageAppPath?.trim()?.takeIf { it.isNotEmpty() } ?: commandLine?.let { firstToken(it) }

    /**
     * 取命令行首个 token：命令含空格时按引号解析（如 `"C:\Program Files\Java\bin\java.exe" -cp ...`）。
     * 空白输入、未闭合引号、空引号一律返回 null（无法可靠推断，宁可不注册）。
     */
    private fun firstToken(commandLine: String): String? {
        val line = commandLine.trimStart()
        val token = if (line.startsWith("\"")) {
            val end = line.indexOf('"', startIndex = 1)
            if (end > 1) line.substring(1, end) else null
        } else {
            val end = line.indexOfFirst { it.isWhitespace() }
            if (end < 0) line else line.substring(0, end)
        }
        return token?.takeIf { it.isNotEmpty() }
    }
}
