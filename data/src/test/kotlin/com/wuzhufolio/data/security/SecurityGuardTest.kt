package com.wuzhufolio.data.security

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 硬约束结构守护测试（M13 T13.1 安全自查的**机器可验证**部分；AGENTS.md §1.1）。
 *
 * 为什么需要：安全自查报告（`docs/test/security-checklist.md`）中的「无遥测」「出站主机白名单」等结论
 * 此前只能靠人工 grep 复核，无回归护栏——新增依赖/新增出站调用/新增网络日志 appender 不会被任何测试拦下。
 * 本测试对**仓库源码**做静态断言（工作目录 = data 模块，仓库根 = `..`）。
 *
 * 覆盖：硬约束 1（数据本地化：出站面收敛）/ 硬约束 2（零遥测：依赖与日志 appender）/ 加密传输（禁明文 http 出站）。
 * 不在本测试覆盖：加密算法与密钥链（由 KeyWrap/FieldCipher/DeviceSecretCipher/SqlCipher 等行为测试守护）、
 * 账户隔离（由各仓库测试与 DefaultPortfolioServiceTest 守护）——见 security-checklist §2/§3 的测试清单。
 */
class SecurityGuardTest {

    private val repoRoot: File = File("..").canonicalFile

    /** 允许的出站主机白名单（硬约束 1/4：只有行情两源与交易所只读 API；注册链接为用户点击后交系统浏览器）。 */
    private val allowedHosts = setOf(
        "api.coingecko.com",
        "pro-api.coinmarketcap.com",
        "api.binance.com",
        "docs.coingecko.com",
        "coinmarketcap.com",
    )

    /** 遥测/分析/崩溃上报类坐标关键词（硬约束 2）。 */
    private val forbiddenDependencyWords = listOf(
        "firebase", "sentry", "amplitude", "mixpanel", "bugsnag", "datadog", "crashlytics",
        "google-analytics", "googletagmanager", "umami", "matomo", "countly", "opentelemetry",
        "newrelic", "elastic-apm", "segment.io", "appcenter", "instabug",
    )

    private val sourceModules = listOf("app", "ui", "data", "domain")

    private fun mainSourceFiles(): List<File> = sourceModules.flatMap { module ->
        File(repoRoot, module + "/src/main").walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .toList()
    }

    /** 去掉注释行（KDoc/行注释中的 `http://host:port` 一类示例不算出站）。 */
    private fun codeLines(file: File): List<String> =
        file.readLines().filterNot { line ->
            val t = line.trimStart()
            t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")
        }

    /** 扫描全部主源码行，返回「文件 → 命中值」清单（越界项在各自测试中断言为空）。 */
    private fun scan(pattern: Regex): List<String> =
        mainSourceFiles().flatMap { file ->
            codeLines(file).flatMap { line ->
                pattern.findAll(line).map { file.relativeTo(repoRoot).path + " → " + it.groupValues[1] }
            }
        }

    @Test
    fun `all outbound http hosts stay inside the allow list`() {
        assertTrue(
            File(repoRoot, "settings.gradle.kts").isFile,
            "工作目录应为模块目录（实际：" + repoRoot.absolutePath + "）",
        )
        val hosts = scan(Regex("""https?://([A-Za-z0-9][A-Za-z0-9.-]*)"""))
        val offenders = hosts.filterNot { it.substringAfter("→ ").lowercase() in allowedHosts }
        assertTrue(
            offenders.isEmpty(),
            "发现白名单外的出站主机（硬约束 1/4：仅行情两源 + 交易所只读 API）：\n" + offenders.joinToString("\n"),
        )
    }

    @Test
    fun `no plaintext http egress literal is present`() {
        val offenders = scan(Regex("""http://([A-Za-z0-9][A-Za-z0-9.-]*)"""))
        assertTrue(offenders.isEmpty(), "出站必须为 https（发现明文 http 字面量）：\n" + offenders.joinToString("\n"))
    }

    @Test
    fun `no telemetry analytics or crash reporting dependency is declared`() {
        val buildFiles = listOf(
            File(repoRoot, "gradle/libs.versions.toml"),
            File(repoRoot, "build.gradle.kts"),
            File(repoRoot, "settings.gradle.kts"),
        ) + sourceModules.map { File(repoRoot, it + "/build.gradle.kts") }
        val offenders = ArrayList<String>()
        for (file in buildFiles) {
            assertTrue(file.isFile, "构建文件缺失：" + file.path)
            val text = file.readText().lowercase()
            for (word in forbiddenDependencyWords) {
                if (text.contains(word)) {
                    offenders += file.relativeTo(repoRoot).path + " → " + word
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "硬约束 2（零遥测）：构建脚本不得声明遥测/分析/崩溃上报依赖：\n" + offenders.joinToString("\n"),
        )
    }

    @Test
    fun `logging has no network appenders`() {
        val logback = File(repoRoot, "app/src/main/resources/logback.xml")
        assertTrue(logback.isFile, "logback 配置缺失：" + logback.path)
        val text = logback.readText()
        val networkAppenders = listOf(
            "SocketAppender", "SSLSocketAppender", "SMTPAppender", "JMSAppender",
            "DBAppender", "SyslogAppender", "WriterAppender",
        )
        val offenders = networkAppenders.filter { text.contains(it) }
        assertTrue(
            offenders.isEmpty(),
            "硬约束 1/2：日志不得经网络 appender 外发（发现：" + offenders.joinToString() + "）",
        )
    }

    @Test
    fun `data directory resolution keeps a single user home based source`() {
        // 数据本地化的唯一真源：AppDirs（app 模块）。其它主源码不得自行拼 ~/.wuzhufolio 用户主目录路径
        // （判据 = 同时出现 user.home 与带引号的 ".wuzhufolio" 字面量；包名 com.wuzhufolio 不计）。
        val allowed = setOf("app/src/main/kotlin/com/wuzhufolio/app/AppDirs.kt")
        val offenders = ArrayList<String>()
        for (file in mainSourceFiles().filter { it.extension == "kt" }) {
            val relative = file.relativeTo(repoRoot).path
            if (relative in allowed) continue
            val text = file.readText()
            if (text.contains("user.home") && text.contains("\".wuzhufolio\"")) {
                offenders += relative
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "数据目录解析应只有 AppDirs 一处（硬约束 1）：\n" + offenders.joinToString("\n"),
        )
    }
}
