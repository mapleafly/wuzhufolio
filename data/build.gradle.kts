plugins {
    alias(libs.plugins.kotlin.jvm)
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

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.logback.classic)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
