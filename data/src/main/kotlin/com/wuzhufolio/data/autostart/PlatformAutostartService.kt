package com.wuzhufolio.data.autostart

import com.wuzhufolio.domain.autostart.AutostartCommand
import com.wuzhufolio.domain.autostart.AutostartPlatform
import com.wuzhufolio.domain.autostart.AutostartRules
import com.wuzhufolio.domain.autostart.AutostartService
import com.wuzhufolio.domain.autostart.AutostartStatus
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** 注册文件权限（属主读写，0600）；非 POSIX 文件系统由 [PlatformAutostartService] 静默降级。 */
private val OWNER_ONLY: Set<PosixFilePermission> =
    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)

/**
 * 平台开机自启实现（M11 · T11.2 · PRD §7.2 模块 9.3；默认关闭，开启后自启驻留托盘）。
 *
 * - Windows：`reg add/delete/query HKCU\...\Run /v WuZhuFolio`（当前用户级，免管理员权限）；
 * - macOS：写/删 `~/Library/LaunchAgents/com.wuzhufolio.app.plist`（RunAtLoad）；
 * - Linux：写/删 `~/.config/autostart/wuzhufolio.desktop`（XDG autostart）。
 *
 * 全部可注入（平台/主目录/可执行文件路径/命令执行/注册表读取/logger），测试可在 Linux 上跑通三分支且不碰真机。
 * 公开方法不抛异常：`status()` 内部兜底，`enable()`/`disable()` 一律返回 Result；失败原因为中文
 * [IllegalStateException]（`enable()` 失败前缀「注册开机自启失败：」，`disable()` 为「取消开机自启失败：」）。
 *
 * supported 口径（人工可复核的取舍）：
 * - 取 `supported = executablePath != null`（三平台一致）。没有可执行文件绝对路径就**写不出**有效注册项
 *   （macOS/Linux 生成不了 ProgramArguments/Exec，Windows 也无从填 Run 值）；若谎报 supported=true，
 *   设置页开关就会「可点但必失败」——开关可用性必须与实际能力一致，才谈得上诚实。
 * - 备选 `supported = executablePath != null || platform == WINDOWS`（Windows 恒 true）被否：Windows 同样存在
 *   无路径场景（非常规启动、ProcessHandle 信息不可用），此时开关仍必然失败。
 * - `enabled` 与 supported 解耦：只读判定不需要路径，故 supported=false 时照常读回真实注册状态
 *   （Windows 查 HKCU Run 值 / macOS·Linux 查文件是否存在）。这样「打包版注册过、当前又以开发态运行」的用户
 *   看到的是「已注册但当前无法改写」，而不是被强行显示成未启用。
 */
class PlatformAutostartService(
    private val platform: AutostartPlatform = AutostartRules.detect(System.getProperty("os.name")),
    private val homeDir: Path = Path.of(System.getProperty("user.home")),
    private val executablePath: String? = AutostartCommand.resolve(
        System.getProperty("jpackage.app-path"),
        ProcessHandle.current().info().commandLine().orElse(null),
    ),
    private val runCommand: (List<String>) -> Int = ::runProcess,
    private val readRegistryValue: (String) -> String? = ::queryRegistryValue,
    private val logger: Logger = LoggerFactory.getLogger("wuzhufolio.autostart"),
) : AutostartService {

    /** 注册文件绝对路径（Windows 为空串 → 解析结果即主目录，不参与读写）。 */
    private val entryPath: Path get() = homeDir.resolve(AutostartRules.relativeEntryPath(platform))

    private val platformId: String get() = platform.name.lowercase()

    override fun status(): AutostartStatus {
        val path = executablePath?.takeIf { it.isNotBlank() }
        val enabled = runCatching { isRegistered() }
            .onFailure { logger.warn("autostart status read failed | platform=" + platformId) }
            .getOrDefault(false)
        return AutostartStatus(
            supported = path != null,
            enabled = enabled,
            executablePath = path,
            unsupportedReason = if (path == null) AutostartRules.UNRESOLVED_EXECUTABLE_REASON else null,
        )
    }

    /** 幂等：已注册则覆盖刷新路径（Windows `/f` / 文件整写）。 */
    override fun enable(): Result<Unit> = guarded("注册开机自启失败：") {
        val command = executablePath?.takeIf { it.isNotBlank() }
            ?: error(AutostartRules.UNRESOLVED_EXECUTABLE_REASON)
        when (platform) {
            AutostartPlatform.WINDOWS -> registerWindows(command)
            AutostartPlatform.MACOS -> writeEntry(AutostartRules.macPlist(command))
            AutostartPlatform.LINUX -> writeEntry(AutostartRules.linuxDesktopEntry(command))
        }
    }

    /** 幂等：未注册也算成功（文件不存在 = 无操作；注册表值不存在 = 无残留）。 */
    override fun disable(): Result<Unit> = guarded("取消开机自启失败：") {
        when (platform) {
            AutostartPlatform.WINDOWS -> unregisterWindows()
            AutostartPlatform.MACOS, AutostartPlatform.LINUX -> deleteEntry()
        }
    }

    /**
     * 统一失败语义：非 [IllegalStateException] 的底层异常（IO/权限等）包成中文原因的 [IllegalStateException]，
     * 原异常挂 cause 便于诊断；已是 [IllegalStateException] 的（本类自抛的中文原因）原样透出。
     */
    private inline fun <T> guarded(prefix: String, block: () -> T): Result<T> =
        runCatching(block).recoverCatching { cause ->
            if (cause is IllegalStateException) throw cause
            throw IllegalStateException(prefix + (cause.message ?: cause.javaClass.simpleName), cause)
        }

    /**
     * 读回注册状态：Windows = HKCU Run 值存在；macOS/Linux = 注册文件存在。
     *
     * 已知取舍：macOS/Linux 只判文件存在，**不比对文件内的路径**——用户手工放置的 plist/.desktop 同样算已注册
     * （陈旧路径归属「已注册」；刷新路径由用户再点一次开启，enable 覆盖写入）。判「文件在但路径陈旧」为未启用
     * 反而会让开关与磁盘状态不一致。
     */
    private fun isRegistered(): Boolean = when (platform) {
        AutostartPlatform.WINDOWS -> readRegistryValue(AutostartRules.windowsRunKey()) != null
        AutostartPlatform.MACOS, AutostartPlatform.LINUX -> Files.isRegularFile(entryPath)
    }

    /** Windows 注册：`reg add "<key>" /v WuZhuFolio /t REG_SZ /d "<exe>" /f`；exit 0 = 成功。 */
    private fun registerWindows(command: String) {
        val args = listOf(
            "reg", "add", AutostartRules.windowsRunKey(),
            "/v", AutostartRules.DISPLAY_NAME,
            "/t", "REG_SZ",
            "/d", AutostartRules.quoteForPlatform(platform, command),
            "/f",
        )
        val exit = runCommand(args)
        check(exit == 0) { "注册开机自启失败（reg add 退出码 " + exit + "）" }
        logger.info("autostart enabled | platform=windows | exe=" + command)
    }

    /** Windows 注销：`reg delete "<key>" /v WuZhuFolio /f`；值本就不存在时 reg 同样返回非 0，按幂等成功处理。 */
    private fun unregisterWindows() {
        val key = AutostartRules.windowsRunKey()
        val exit = runCommand(listOf("reg", "delete", key, "/v", AutostartRules.DISPLAY_NAME, "/f"))
        if (exit == 0) {
            logger.info("autostart disabled | platform=windows")
            return
        }
        // 读回确认无残留才按成功处理：区分「本来就没注册」与「删除失败（权限等）」
        if (readRegistryValue(key) == null) {
            logger.info("autostart already disabled | platform=windows")
            return
        }
        error("取消开机自启失败（reg delete 退出码 " + exit + "）")
    }

    /** macOS/Linux 写注册文件：父目录自动创建，UTF-8；写完尽力收紧为属主读写。 */
    private fun writeEntry(content: String) {
        val target = entryPath
        target.parent?.let { Files.createDirectories(it) }
        Files.writeString(target, content, StandardCharsets.UTF_8)
        restrictToOwner(target)
        logger.info("autostart enabled | platform=" + platformId + " | entry=" + target + " | exe=" + executablePath)
    }

    /** macOS/Linux 删注册文件：不存在（= 已取消）同样成功。 */
    private fun deleteEntry() {
        val removed = Files.deleteIfExists(entryPath)
        logger.info("autostart disabled | platform=" + platformId + " | removed=" + removed)
    }

    /** 尽力收紧为属主读写（0600）；非 POSIX 文件系统（Windows/NTFS 等）静默降级为用户目录 ACL。 */
    private fun restrictToOwner(file: Path) {
        runCatching { Files.setPosixFilePermissions(file, OWNER_ONLY) }
    }
}

/** 执行外部命令取 exit code（输出丢弃，避免管道阻塞）；启动失败/IO 异常按 -1 处理（调用方按失败判定）。 */
private fun runProcess(command: List<String>): Int = runCatching {
    ProcessBuilder(command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
        .waitFor()
}.getOrDefault(-1)

/** `reg query <key> /v WuZhuFolio` 读回注册值；未注册/命令失败返回 null（status 按未启用处理）。 */
private fun queryRegistryValue(key: String): String? = runCatching {
    val process = ProcessBuilder("reg", "query", key, "/v", AutostartRules.DISPLAY_NAME)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
    if (process.waitFor() == 0) parseRegistryValue(output) else null
}.getOrNull()

/** 解析 `reg query` 输出中的 REG_SZ 值（值行形如 `    WuZhuFolio    REG_SZ    C:\...\WuZhuFolio.exe`）。 */
private fun parseRegistryValue(output: String): String? = output.lineSequence()
    .map { it.trim() }
    .firstOrNull { it.startsWith(AutostartRules.DISPLAY_NAME) && "REG_SZ" in it }
    ?.substringAfter("REG_SZ")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
