# 依赖许可证清单（dependency-licenses.md）

> P3 T0.1 产物（评审 N5）：随 `gradle/libs.versions.toml` 出具，逐项核验与 **AGPL-3.0**（根 `LICENSE`，决策 D1）的兼容性。
> 核验方法：Maven Central POM `<licenses>` 标签逐一抓取（2026-09-01 实测，脚本见文末），项目主页/仓库 LICENSE 复核。
> 结论：**全部兼容，无阻断项**；需注意项 2 条（argon2-jvm LGPL-3.0 动态链接、logback 双许可）。

## 1. 直接依赖（运行时随产品分发）

| 依赖 | 坐标（版本目录 ref） | 版本 | 许可证（证据） | AGPL-3.0 兼容 |
|------|---------------------|------|----------------|----------------|
| Kotlin stdlib | `org.jetbrains.kotlin:kotlin-stdlib` | 2.4.10 | Apache-2.0（POM） | ✅ |
| Compose Multiplatform（runtime/ui/desktop/skiko 等） | `org.jetbrains.compose.*` | 1.12.0 | Apache-2.0（POM） | ✅ |
| kotlinx-coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.11.0 | Apache-2.0（POM） | ✅ |
| kotlinx-serialization-json | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | Apache-2.0（POM） | ✅ |
| Exposed ORM | `org.jetbrains.exposed:exposed-core/dao/jdbc` | 1.5.0 | Apache-2.0（POM） | ✅ |
| SQLite JDBC（xerial，M0） | `org.xerial:sqlite-jdbc` | 3.53.4.0 | Apache-2.0（POM） | ✅ |
| **SQLCipher JDBC（Willena fork，M1 起用）** | `io.github.willena:sqlite-jdbc` | 3.53.4.0 | Apache-2.0（POM）；捆绑 SQLCipher（BSD 风格）+ OpenSSL 3（Apache-2.0） | ✅（见 §2 注 3） |
| **argon2-jvm（M1 起用）** | `de.mkammerer:argon2-jvm` | 2.12 | **LGPL-3.0**（POM） | ✅（见 §2 注 1） |
| BouncyCastle（备选 KDF 实现） | `org.bouncycastle:bcprov-jdk18on` | 1.85.2 | Bouncy Castle Licence（≈MIT，POM） | ✅ |
| **java-keyring（OS 钥匙串，M1 起用）** | `com.github.javakeyring:java-keyring` | 1.0.4 | **BSD-2-Clause**（仓库 LICENSE） | ✅ |
| JNA（argon2/keyring 传递依赖） | `net.java.dev.jna:jna` | 5.18.1 | LGPL-2.1+ **或** Apache-2.0 双许可（POM） | ✅（选 Apache-2.0 分支亦可） |
| Ktor client（M5/M6 起用） | `io.ktor:ktor-client-core/okhttp/...` | 3.5.2 | Apache-2.0（POM） | ✅ |
| OkHttp | `com.squareup.okhttp3:okhttp` | 5.5.0 | Apache-2.0（POM） | ✅ |
| Koin（DI） | `io.insert-koin:koin-core` | 4.2.2 | Apache-2.0（POM） | ✅ |
| lifecycle-viewmodel-compose | `org.jetbrains.androidx.lifecycle` | 2.11.0 | Apache-2.0 | ✅ |
| slf4j-api | `org.slf4j:slf4j-api` | 2.0.17 | MIT（slf4j.org/license.html） | ✅ |
| logback-classic/core | `ch.qos.logback:logback-classic` | 1.6.3 | **EPL-2.0 / LGPL-2.1 双许可**（父 POM） | ✅（见 §2 注 2） |
| **dorkbox SystemTray（M11 备选）** | `com.dorkbox:SystemTray` | 4.4 | Apache-2.0（POM） | ✅ |

## 2. 需注意项

1. **argon2-jvm（LGPL-3.0）**：LGPL 允许与其他许可作品**动态链接**分发（JVM jar 依赖即动态链接），与 AGPL-3.0 组合分发合规；要求：不得修改 argon2-jvm 本身源码后再闭源分发（本项目不修改）；许可声明随分发（NOTICE/关于页，P7 用户指南落位）。其捆绑原生 argon2 参考实现为 Public Domain / CC0。
2. **logback（EPL-2.0 / LGPL-2.1 双许可）**：双许可任选一，按 EPL-2.0 取用（弱 Copyleft，仅约束 logback 自身文件的修改），不影响 AGPL-3.0 主体。
3. **Willena/sqlite-jdbc-crypt**：JAR Apache-2.0；运行时捆绑的 SQLCipher 为 BSD 风格许可、OpenSSL 3 为 Apache-2.0，均与 AGPL-3.0 兼容；**三平台可用性验证与锁版为 M1 T1.1 验收项**（ADR-002 风险表，P3 登记候选版本 3.53.4.0）。

## 2b. 内嵌字体资源（随产品分发，P3 验收修复轮新增）

> Linux/WSL 普遍无 CJK 系统字体（实测 `fc-list :lang=zh` 为 0），故内嵌字库；三平台分发均自含。

| 字体 | 来源 | 许可证 | AGPL-3.0 兼容 |
|------|------|--------|----------------|
| Noto Sans SC（可变字重 100–900） | google/fonts `ofl/notosanssc` | **SIL OFL 1.1** | ✅（OFL 允许捆绑再分发；未修改字体文件本身，满足保留字体名条款） |
| Noto Serif SC（可变字重） | google/fonts `ofl/notoserifsc` | **SIL OFL 1.1** | ✅ |
| JetBrains Mono（可变字重） | google/fonts `ofl/jetbrainsmono` | **SIL OFL 1.1** | ✅ |

体积注记：三字体共 ~43MB（app-image ~195MB）；P7 可用 pyftsubset 按字符集裁剪瘦身（登记 P7 优化项）。

## 3. 构建期工具（不随产品分发）

| 工具 | 许可证 | 说明 |
|------|--------|------|
| Gradle 8.14.4 | Apache-2.0 | Wrapper 唯一真源 |
| Kotlin Gradle 插件 / Compose 编译器插件 | Apache-2.0 | 构建期 |
| detekt 1.23.8 | Apache-2.0 | 静态检查 |
| JUnit4/5 | EPL-2.0 | 仅测试 |

## 4. 再生成方法（P4 起每里程碑复核）

```bash
# 对 libs.versions.toml 中每个坐标抓 POM 的 <licenses>：
base=https://repo.maven.apache.org/maven2
curl -s "$base/<group>/<artifact>/<version>/<artifact>-<version>.pom" | grep -A3 '<license>'
# 全量依赖树核对：
./gradlew dependencies --configuration runtimeClasspath
```

> P7 发布前在「关于」页/NOTICE 文件汇总第三方许可（ADR-006 发布清单项）。
