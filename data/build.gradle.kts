plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization) // coins.contracts JSON 载荷（M3；M9 .cpro 亦将使用）
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc.crypt)   // Willena SQLCipher（M1 T1.1，整库加密）
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.java.keyring)   // OS 钥匙串（T1.1，DB 密钥入钥匙串）
    implementation(libs.slf4j.api)
    implementation(libs.kotlinx.serialization.json)
    // 行情 HTTP（ADR-003 §1：Ktor client + OkHttp 引擎——OkHttp 原生支持 JVM ProxySelector，M5 起用）
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.logback.classic)
    testImplementation(libs.ktor.client.mock) // MockEngine：T5.1 429/额度/未收录/回落分支验收
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
