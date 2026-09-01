// WuZhuFolio 根构建脚本（T0.1）
// 模块：app（组装/入口）、ui（Compose 主题/组件/主壳）、data（存储/迁移/设置）、domain（纯 Kotlin）
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.detekt) apply false
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}

subprojects {
    // 统一 JVM 工具链：temurin-17（.mise.toml / CI setup-java 对齐，ADR-001）
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(17)
        }
    }
    // 静态检查：detekt（T0.2 CI 同步执行）
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(files("${rootDir}/config/detekt/detekt.yml"))
    }
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            txt.required.set(false)
            xml.required.set(false)
            sarif.required.set(false)
        }
    }
}
