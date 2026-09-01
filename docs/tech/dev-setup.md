# 本地开发环境搭建（dev-setup.md）

> P3 T0.3 产物。开发基准环境：**WSL2 + Ubuntu 24.04**（2026-08-31 人工指令）。
> SDK 管理原则（同指令）：**mise 管 JDK，Gradle Wrapper 为唯一真源，Kotlin 由 Gradle 插件驱动**。

## 1. 工具链安装

| 工具 | 安装方式 | 说明 |
|------|----------|------|
| JDK 17（Temurin） | `mise install`（读 `.mise.toml`） | 编译/运行/打包（jpackage 要求 17+） |
| Gradle | **不安装** | 仓库内 `./gradlew` 为唯一真源，版本锁 `gradle/wrapper/gradle-wrapper.properties`（8.14.3） |
| Kotlin | **不单独安装** | 由 version catalog 的 Kotlin Gradle 插件驱动（`gradle/libs.versions.toml`，2.4.10） |
| detekt | **不需单独安装** | 由 Gradle 插件执行（`./gradlew detekt`） |

首次克隆后：

```bash
mise install                 # 安装 temurin-17（.mise.toml）
export JAVA_HOME=$(mise where java)   # 或让 shell 激活 mise shims
./gradlew build              # 全量构建（编译 + 测试 + detekt）
```

> 说明：`.mise.toml` 只保留 `java = "temurin-17"`。Gradle 曾以 mise 临时安装用于首次生成 Wrapper，
> 生成后即按口径移除——日常开发一律用 `./gradlew`，不装全局 gradle。

> **中文字体已内嵌**（Noto Sans/Serif SC + JetBrains Mono，OFL-1.1，见 `docs/tech/dependency-licenses.md` §2b）：无 CJK 系统字体的 Linux/WSL 也能正确渲染中文，无需安装系统字体。

## 2. 环境检查清单（新环境验收）

- [ ] `mise ls` 输出含 `java temurin-17`
- [ ] `java -version` 显示 `17.0.x` Temurin（注意 java 在 mise shims/JAVA_HOME 下解析）
- [ ] `./gradlew build` 通过（21 项测试全绿 + detekt 零问题）
- [ ] `./gradlew :app:run` 启动 GUI，日志出现 `hello-chain ok | schema_version=2 | ... | market_api_key=****`（脱敏）
- [ ] 主壳可见侧边栏五页 + 组件走查（DEV）；顶栏 ☾/☀ 切换主题即时重渲染

## 3. WSL2 注记（重要）

- **GUI 冒烟走 WSLg**：`DISPLAY=:0` / `WAYLAND_DISPLAY=wayland-0` 默认就绪。
- **Skiko 渲染**：WSLg 无 GL 加速时 `./gradlew :app:run` 会抛 `skiko.RenderException: Cannot create Linux GL context`。
  解决（M0 实测）：以软件渲染启动——
  ```bash
  JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE_FAST" ./gradlew :app:run
  ```
  （或安装 mesa GL 驱动后走默认 OpenGL。）
- **托盘/通知**：WSLg 下行为不完整（无真实托盘协议）；最终以 CI 三平台 runner + 实机验证为准（M11 验收口径）。
- **数据目录**：默认 `~/.wuzhufolio`；开发隔离用环境变量覆盖：`WUZHUFOLIO_DATA_DIR=/tmp/wzf ./gradlew :app:run`。

## 4. 密钥与安全配置

- M0 无真实密钥：数据库为明文 SQLite（`~/.wuzhufolio/wuzhufolio.db`）。
- M1 起：DB 密钥/设备密钥自动生成并写入 OS 钥匙串（javakeyring；Linux = Secret Service，ADR-002）；
  行情/交易所 API Key 在应用内设置页录入，**绝不入库、不入库、不入库**（PRD §1.1；设置页 M5/M6 落地）。
- 日志文件 `~/.wuzhufolio/logs/wuzhufolio.log` 已经 LogRedactor 脱敏；诊断导出前仍需人工复核（PRD §6）。

## 5. 常用命令

见根 `README.md`「构建 / 运行 / 测试」。

## 6. 故障排查

| 症状 | 处理 |
|------|------|
| `java: command not found` | `mise install` 后激活 shims 或 `export JAVA_HOME=$(mise where java)` |
| Gradle 版本警告（<8.14.4 deprecated） | 已锁 8.14.4（Kotlin 2.5 起最低要求）；升级 Gradle 用 §BT§./gradlew wrapper --gradle-version <v>§BT§ |
| WSLg 黑窗/GL 异常 | 见 §3 SOFTWARE_FAST |
| 测试库冲突 | 删 `~/.wuzhufolio` 或用 `WUZHUFOLIO_DATA_DIR` 换目录 |
