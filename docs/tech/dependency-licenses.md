# 依赖许可证清单（dependency-licenses.md）

> P3 T0.1 产物（评审 N5）：随 `gradle/libs.versions.toml` 出具，逐项核验与 **AGPL-3.0**（根 `LICENSE`，决策 D1）的兼容性。
> 核验方法：Maven Central POM `<licenses>` 标签逐一抓取（2026-09-01 实测，脚本见文末），项目主页/仓库 LICENSE 复核。
> 结论：**全部兼容，无阻断项**；需注意项：logback 双许可（EPL-2.0 取用）；java-keyring 传递依赖核验见 §1b。
> M1 修订（2026-09-02）：xerial 明文驱动移除（由 Willena fork 取代）；argon2-jvm **未采用**——KDF 定案 BouncyCastle 纯 JVM（原生绑定分发风险换纯 JVM 稳定性，实测见 docs/dev/modules/M1.md）；java-keyring 启用并引入传递依赖（§1b）。

## 1. 直接依赖（运行时随产品分发）

| 依赖 | 坐标（版本目录 ref） | 版本 | 许可证（证据） | AGPL-3.0 兼容 |
|------|---------------------|------|----------------|----------------|
| Kotlin stdlib | `org.jetbrains.kotlin:kotlin-stdlib` | 2.4.10 | Apache-2.0（POM） | ✅ |
| Compose Multiplatform（runtime/ui/desktop/skiko 等） | `org.jetbrains.compose.*` | 1.12.0 | Apache-2.0（POM） | ✅ |
| kotlinx-coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.11.0 | Apache-2.0（POM） | ✅ |
| kotlinx-serialization-json | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | Apache-2.0（POM） | ✅ |
| Exposed ORM | `org.jetbrains.exposed:exposed-core/dao/jdbc` | 1.5.0 | Apache-2.0（POM） | ✅ |
| ~~SQLite JDBC（xerial，M0 期）~~ | `org.xerial:sqlite-jdbc` | 3.53.4.0 | Apache-2.0（POM） | ✅（M1 起由 Willena fork 取代，同源 API） |
| **SQLCipher JDBC（Willena fork，M1 起用）** | `io.github.willena:sqlite-jdbc` | 3.53.4.0 | Apache-2.0（POM）；捆绑 SQLCipher（BSD 风格）+ OpenSSL 3（Apache-2.0） | ✅（见 §2 注 3） |
| BouncyCastle（Argon2id KDF，M1 起用） | `org.bouncycastle:bcprov-jdk18on` | 1.85.2 | Bouncy Castle Licence（≈MIT，POM） | ✅ |
| **java-keyring（OS 钥匙串，M1 起用）** | `com.github.javakeyring:java-keyring` | 1.0.4 | **BSD-2-Clause**（仓库 LICENSE） | ✅ |
| JNA + jna-platform（java-keyring 传递依赖，Windows/macOS 后端） | `net.java.dev.jna:jna` / `jna-platform` | 5.13.0 | LGPL-2.1-or-later **或** Apache-2.0 双许可（POM） | ✅（按 Apache-2.0 分支分发） |
| Ktor client（M5/M6 起用） | `io.ktor:ktor-client-core/okhttp/...` | 3.5.2 | Apache-2.0（POM） | ✅ |
| OkHttp | `com.squareup.okhttp3:okhttp` | 5.5.0 | Apache-2.0（POM） | ✅ |
| Koin（DI） | `io.insert-koin:koin-core` | 4.2.2 | Apache-2.0（POM） | ✅ |
| lifecycle-viewmodel-compose | `org.jetbrains.androidx.lifecycle` | 2.11.0 | Apache-2.0 | ✅ |
| slf4j-api | `org.slf4j:slf4j-api` | 2.0.17 | MIT（slf4j.org/license.html） | ✅ |
| logback-classic/core | `ch.qos.logback:logback-classic` | 1.6.3 | **EPL-2.0 / LGPL-2.1 双许可**（父 POM） | ✅（见 §2 注 2） |
| **dorkbox SystemTray（M11 备选）** | `com.dorkbox:SystemTray` | 4.4 | Apache-2.0（POM） | ✅ |

## 1b. java-keyring 传递依赖（M1 起运行时引入，随产品分发）

> 来源：./gradlew :data:dependencies --configuration runtimeClasspath 实测解析（2026-09-02）。

| 依赖 | 坐标 | 版本 | 许可证（证据） | AGPL-3.0 兼容 |
|------|------|------|----------------|----------------|
| java-keyring（OS 钥匙串，直接依赖） | com.github.javakeyring:java-keyring | 1.0.4 | BSD-2-Clause（仓库 LICENSE） | ✅ |
| secret-service（Linux Secret Service 客户端） | de.swiesend:secret-service | 1.8.1-jdk17 | MIT（POM） | ✅ |
| dbus-java-core / native-unixsocket（dbus 传输层） | com.github.hypfvieh:dbus-java-* | 4.2.1 | Apache-2.0（仓库 LICENSE；POM 未声明） | ✅ |
| hkdf | at.favre.lib:hkdf | 1.1.0 | Apache-2.0（POM） | ✅ |
| JNA / jna-platform（Windows/macOS 原生绑定） | net.java.dev.jna:* | 5.13.0 | LGPL-2.1-or-later 或 Apache-2.0（POM） | ✅（按 Apache-2.0 分支分发） |
| jkeychain（macOS Keychain 客户端） | pt.davidafsilva.apple:jkeychain | 1.1.0 | MIT（POM） | ✅ |

## 2. 需注意项

1. **logback（EPL-2.0 / LGPL-2.1 双许可）**：双许可任选一，按 EPL-2.0 取用（弱 Copyleft，仅约束 logback 自身文件的修改），不影响 AGPL-3.0 主体。
2. **Willena/sqlite-jdbc-crypt**：JAR Apache-2.0；运行时捆绑的 SQLCipher 为 BSD 风格许可、OpenSSL 3 为 Apache-2.0，均与 AGPL-3.0 兼容；**三平台可用性验证与锁版 = M1 T1.1 验收项**（2026-09-02 落地：org.sqlite.mc fork，SQLCipher 4 兼容默认参数 + raw-key 注入；CI 三平台矩阵复跑与实测见 docs/dev/modules/M1.md）。
3. **java-keyring 传递依赖**（§1b）：dbus-java 系 POM 未声明 licenses，按仓库 LICENSE（Apache-2.0）核验；secret-service（MIT）、jkeychain（MIT）、hkdf（Apache-2.0）均已 POM 实证；随「关于」页第三方许可汇总（P7）。

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
