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
    providers.gradleProperty("wuzhufolio.filmAssets").orNull?.let {
        systemProperty("wuzhufolio.filmAssets", it)
    }
}
