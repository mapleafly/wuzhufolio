import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

// ---------------------------------------------------------------------------
// M13 T13.2 打包与签名（ADR-006 §1/§2/§3）
// 三平台产物格式（按当前主机选择；跨平台出包由 CI 矩阵负责）：
//   Windows = msi（主）+ exe · macOS = dmg + pkg · Linux = deb + rpm
// AppImage 不由 jpackage 直接产出（ADR-006 §1：用 appimagetool 包 jpackage app-image），
// 由 `scripts/package-appimage.sh` 在 createDistributable 产物上追加生成。
// 签名/公证凭据只从 Gradle 属性或环境变量注入（CI = GitHub Secrets），仓库与本地不含凭据；
// 本地未注入时 = 不签名（ADR-006 §2：未签名产物用于内部验收，正式签名/公证在 P7 用发布凭据执行）。
// ---------------------------------------------------------------------------
val hostOsName = System.getProperty("os.name").lowercase()
val packagingFormats: List<TargetFormat> = when {
    hostOsName.contains("win") -> listOf(TargetFormat.Msi, TargetFormat.Exe)
    hostOsName.contains("mac") -> listOf(TargetFormat.Dmg, TargetFormat.Pkg)
    else -> listOf(TargetFormat.Deb, TargetFormat.Rpm)
}

/** 应用版本单一真源（M13 T13.2）：同时注入 jpackage packageVersion 与运行期 BuildInfo.VERSION。 */
val appVersion = "0.1.0"

// ---------------------------------------------------------------------------
// 构建期版本注入：生成 com.wuzhufolio.app.BuildInfo（.cpro 头部 app_version / 关于页 / 诊断报告同源）。
// 此前为模块内开发常量 "0.1.0-dev"（M9 登记「构建注入随 M13 发布链落地」——本任务闭环）。
// ---------------------------------------------------------------------------
val buildInfoDir = layout.buildDirectory.dir("generated/buildinfo/kotlin")
val generateBuildInfo by tasks.registering {
    val versionValue = appVersion
    // 构建标识（P6 人工门：需要能区分「测的是哪一版」，否则修没修都无法确认）
    val commitValue = providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.map { it.trim() }.orElse("unknown").get()
    val outputDir = buildInfoDir
    inputs.property("appVersion", versionValue)
    inputs.property("buildCommit", commitValue)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().asFile.resolve("com/wuzhufolio/app/BuildInfo.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.wuzhufolio.app
            |
            |/**
            | * 构建期注入的应用版本（M13 T13.2，由 app/build.gradle.kts 的 appVersion 生成，请勿手改）。
            | * 单一真源 = jpackage packageVersion；消费方：.cpro 头部 app_version、关于页、诊断报告。
            | */
            |object BuildInfo {
            |    const val VERSION: String = "$versionValue"
            |
            |    /** 构建提交（short SHA）——人工验收/缺陷复现时用于确认「跑的是哪一版」。 */
            |    const val COMMIT: String = "$commitValue"
            |}
            |
            """.trimMargin(),
        )
    }
}
kotlin.sourceSets.named("main") { kotlin.srcDir(generateBuildInfo) }

/**
 * 读取签名/公证凭据：Gradle 属性（wuzhufolio.xxx）优先，其次环境变量（WUZHUFOLIO_XXX）。
 * **空串视为未配置**：CI 中 `${{ secrets.UNSET }}` 会展开为空字符串环境变量（存在但为空），
 * 若按「非 null」判定会误开签名（M13 CI 首跑实测：macOS 打包因 identity="" 触发配置缓存错误）。
 */
fun signingSecret(key: String): String? =
    (providers.gradleProperty("wuzhufolio.$key").orNull
        ?: providers.environmentVariable("WUZHUFOLIO_" + key.uppercase().replace('.', '_')).orNull)
        ?.takeIf { it.isNotBlank() }

val macSigningIdentity = signingSecret("macos.signing.identity")
val macSigningKeychain = signingSecret("macos.signing.keychain")
val macNotaryAppleId = signingSecret("macos.notarization.appleId")
val macNotaryPassword = signingSecret("macos.notarization.password")
val macNotaryTeamId = signingSecret("macos.notarization.teamId")

dependencies {
    implementation(project(":ui"))
    implementation(project(":data"))
    implementation(project(":domain"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.koin.core)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.slf4j.api)
    implementation(libs.ktor.client.core) // M5：MarketHttp 返回类型的直接引用（应用组装层）
    implementation(libs.logback.classic)

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

compose.desktop {
    application {
        mainClass = "com.wuzhufolio.app.MainKt"
        // M11 T11.3：开箱即启用 JDK 系统代理探测（Windows Internet 选项 / macOS 网络设置 / GNOME gsettings）。
        // Main.main 首行也会设置（幂等）——此处是打包版的兜底，保证属性在任何网络类加载前生效。
        jvmArgs += listOf("-Djava.net.useSystemProxies=true")

        nativeDistributions {
            // 产物口径见 ADR-006 §1（M13 T13.2 补齐 rpm/AppImage/exe/pkg）
            targetFormats(*packagingFormats.toTypedArray())
            // 运行期镜像模块集（jpackage + jlink）：Compose 插件默认建议集只有 java.desktop 等 6 个模块，
            // **缺 java.sql 等** —— M13 打包版冒烟实测「启动即 NoClassDefFoundError: java/sql/SQLException」。
            // 依据：jdeps --multi-release 17 --print-module-deps（uber jar）实测 + 反射/本地化补充。
            // 进一步裁剪（体积优化）按 ADR-006 风险表留 P7；此处优先「打包版可运行」。
            modules(
                "java.base", "java.desktop", "java.datatransfer", "java.xml", "java.prefs", "java.logging",
                "java.sql", "java.management", "java.instrument", "java.naming",
                "jdk.crypto.ec", "jdk.net", "jdk.security.auth", "jdk.unsupported",
                // 中文/本地化日期与字符集支持（i18n 与 CSV 导入）
                "jdk.localedata", "jdk.charsets",
            )
            includeAllModules = false
            packageName = "WuZhuFolio"
            packageVersion = appVersion
            // 安装器元数据保持 **ASCII**：CI 实测 Windows jpackage 读取参数文件（@args.txt）时按 UTF-8 解码，
            // 非 ASCII（中文 description / 全角点）在 Windows 默认代码页下写盘即触发 `Input length = 1`
            // （M13 CI 三跑实证）。本地化描述改由应用内「关于」页与 README 承载。
            description = "WuZhuFolio - local-first encrypted portfolio tracker"
            vendor = "WuZhuFolio"
            copyright = "Copyright (C) 2026 WuZhuFolio contributors. AGPL-3.0."
            licenseFile.set(rootProject.file("LICENSE"))

            linux {
                // jpackage 的 Linux 包名须为小写、无空格的 Debian 合法名
                packageName = "wuzhufolio"
                appCategory = "Office"          // freedesktop 主分类（.desktop Categories）
                appRelease = "1"
                // jpackage 生成 `Maintainer: <vendor> <debMaintainer>`，故此处只填邮箱
                debMaintainer = "noreply@users.noreply.github.com"
                // ADR-006 §2：Linux 包许可标识（deb = AGPL-3.0 自由文本；rpm = 许可证标签）
                rpmLicenseType = "AGPL-3.0-only"
            }
            macOS {
                bundleID = "com.wuzhufolio.app"
                appCategory = "public.app-category.finance"
                dockName = "WuZhuFolio"
                // macOS bundle 版本：Apple 规定 CFBundleVersion 首段不得为 0（jpackage 实测报
                // "The first number in an app-version cannot be zero or negative"），故 bundle 版本
                // 与内部应用版本（appVersion=0.1.0，.cpro 头部/关于页）解耦；P7 定稿发布号后两者对齐。
                packageVersion = "1.0.0"
                packageBuildVersion = "1"
                // M13 T13.2：Developer ID 签名 + notarytool 公证（凭据经 Secrets 注入；缺省不签名）
                signing {
                    sign.set(macSigningIdentity != null)
                    if (macSigningIdentity != null) {
                        identity.set(macSigningIdentity)
                        macSigningKeychain?.let { keychain.set(it) }
                    }
                }
                if (macNotaryAppleId != null && macNotaryPassword != null && macNotaryTeamId != null) {
                    notarization {
                        appleID.set(macNotaryAppleId)
                        password.set(macNotaryPassword)
                        teamID.set(macNotaryTeamId)
                    }
                }
            }
            windows {
                menuGroup = "WuZhuFolio"
                shortcut = true
                menu = true
                // DEF-42 / D34（2026-09-15 人工拍板 C1）：安装形态改为 **per-machine**，默认装到
                // `C:\Program Files\WuZhuFolio` —— 该路径在任何区域设置下都是纯 ASCII。
                //
                // 依据：jpackage 的 Windows 原生启动器在「安装路径含系统 ANSI 代码页无法表示的字符」时
                // 无法加载随包 jvm.dll（CI 对照实证：ASCII 路径 PASS、含 é（可表示）PASS、含中文（不可表示）
                // FAIL —— 2 秒内 exitCode=2 且零应用日志 = 人工门看到的 `Failed to launch JVM`；
                // 换 Temurin 21 打包同样 FAIL，故与 JDK 版本无关）。
                // `man jpackage` 明确：`--install-dir` 在 Windows 只接受「安装根下的**相对子路径**」，
                // 因此 per-user 安装无法摆脱 `%LOCALAPPDATA%\<用户名>\...`——用户名非 ASCII 时必然踩坑。
                perUserInstall = false
                // 关闭安装目录选择页：避免用户选到非 ASCII 目录再次触发同一崩溃。
                // 需要自定义安装位置（或无管理员权限）的用户改用 **便携版 zip**（CI 产物 `portable/*-portable-*.zip`）。
                dirChooser = false
                // 诊断开关（默认关闭，仅 CI 出「控制台版」调试产物用）：
                //   ./gradlew :app:createDistributable -Pwuzhufolio.windowsConsole=true
                // 打开后启动器为 console 子系统——`Failed to launch JVM` 这类启动器级错误会直接打印真实原因
                // （JVM 初始化失败的具体信息），而 GUI 启动器只弹一个无细节的对话框。
                console = (findProperty("wuzhufolio.windowsConsole") == "true")
                // 固定 upgradeUuid：同 product 的后续 MSI 走升级而非并存安装（P7 起沿用，不可再改）。
                // 注意：per-user → per-machine 的安装范围变更不属同一升级路径，旧 per-user 版需先卸载（D34 §4）。
                upgradeUuid = "6f2a1c9e-8b74-4d3a-9c1f-2e5b7a0d4c88"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
