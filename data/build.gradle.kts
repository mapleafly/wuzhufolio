plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.logback.classic)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
