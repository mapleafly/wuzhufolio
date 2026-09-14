plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization) // M9 .cpro 头部/载荷 JSON（ADR-005：kotlinx.serialization，跨平台跨版本可读）
}

dependencies {
    // Argon2id 纯 JVM 实现（ADR-002 §2 KDF：BouncyCastle Argon2BytesGenerator；M1 T1.3 基准校准后冻结参数。
    // argon2-jvm（JNA 原生绑定）评估后未采用——纯 JVM 免去三平台原生库分发风险，速度实测见 docs/dev/modules/M1.md）
    implementation(libs.bcprov)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// T1.3 KDF 基准校准夹具：./gradlew :domain:kdfBenchmark（目标机复核口径见 docs/dev/modules/M1.md）
val kdfBenchmark by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "T1.3 Argon2id 基准：候选参数实测耗时（登录 KDF <=2s 预算，PRD §12）"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.wuzhufolio.domain.security.Argon2BenchmarkKt")
}

// P6 .cpro 大载荷内存曲线夹具：./gradlew :domain:backupBenchmark
// （M13 安全清单 §7-1 到期检查点；默认 -Xmx1g 模拟 4GB 目标机打包版默认堆，可 -PbenchXmx=512m 覆盖）
val backupBenchmark by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "P6 .cpro 非流式编解码内存曲线：轻量/典型/重度/压力四档（峰值堆 + 耗时 + 文件体积）"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.wuzhufolio.domain.backup.BackupLoadBenchmarkKt")
    jvmArgs("-Xmx" + (project.findProperty("benchXmx") as String? ?: "1g"))
}
