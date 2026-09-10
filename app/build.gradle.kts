import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

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
            // 产物口径见 ADR-006：dmg（macOS）/ msi（Windows）/ deb（Linux）；rpm/AppImage/Flatpak 为 P7 追加
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "WuZhuFolio"
            packageVersion = "0.1.0"
            description = "WuZhuFolio - 本地优先的加密资产组合追踪工具"
            vendor = "WuZhuFolio"
            licenseFile.set(rootProject.file("LICENSE"))
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
