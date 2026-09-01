plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(project(":domain"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)

    // Compose Desktop UI 测试（ui-test-junit4，ADR-001）；kotlin("test") 默认 JUnit4，与本模块测试框架一致
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.junit4)
    testImplementation(kotlin("test"))
}
