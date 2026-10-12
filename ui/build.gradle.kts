plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(project(":domain"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)

    // Compose Desktop UI 测试（ui-test-junit4，ADR-001）；kotlin("test") 默认 JUnit4，与本模块测试框架一致
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit4)
    testImplementation(kotlin("test"))
}

// B5⑤ 影片素材写回门控（D47 §2.5，2026-10-10）：默认**不写仓库**（`VisualRegression.captureFilmAsset`
// 落 `ui/build/promo-assets/`）；`-Pwuzhufolio.filmAssets=true` 或环境变量 `WUZHUFOLIO_FILM_ASSETS=1`
// 才写回 `docs/release/promo/assets/ui/`（P7 出片链路 `docs/release/promo/tools/render-0.2.0.sh` 已自动开启）。
tasks.withType<Test>().configureEach {
    // B5① 配套 + **DEF-60 修复**（C0，2026-10-12）：视觉回归产物（`ui/build/visual-regression/`，24 张 PNG +
    // `diff-*.md`）是测试的**副产物**，此前**未声明为任务输出** ⇒ Gradle 构建缓存命中时测试不执行、
    // 该目录为空：CI 的视觉回归归档为空，且「24 张」计数断言误报失败（实测 run `38188785360` 三平台全红）。
    // 声明为输出后，缓存命中会**连同产物一起恢复**（缓存条目里含 PNG），归档与计数断言在两种路径下都成立。
    outputs.dir(layout.buildDirectory.dir("visual-regression")).withPropertyName("visualRegressionArtifacts")
    providers.gradleProperty("wuzhufolio.filmAssets").orNull?.let {
        systemProperty("wuzhufolio.filmAssets", it)
    }
}
