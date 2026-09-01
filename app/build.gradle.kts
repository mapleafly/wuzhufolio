import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(projects.ui)
    implementation(projects.data)
    implementation(projects.domain)

    implementation(compose.desktop.currentOs)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

compose.desktop {
    application {
        mainClass = "com.wuzhufolio.app.MainKt"

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
