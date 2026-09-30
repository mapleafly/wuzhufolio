# 本地开发环境搭建（dev-setup.md）

> P3 T0.3 产物。开发基准环境：**WSL2 + Ubuntu 24.04**（2026-08-31 人工指令）。
> SDK 管理原则（2026-08-31 人工指令，**2026-09-24 修订**）：**Gradle Wrapper 为唯一真源，Kotlin 由 Gradle 插件驱动；
> JDK 用 17 或 21 均可（发布/CI 基线为 Temurin 17）；mise 只是「可选建议」，不是硬性规定**
> （见 `STATUS.md` 已决策事项 19 / 34）。

## 1. 工具链安装

| 工具 | 安装方式 | 说明 |
|------|----------|------|
| JDK **17 或 21** | 任选其一：`mise install`（读 `.mise.toml`，**可选**）／发行版包管理／sdkman／直接解压 | 编译/运行/打包（jpackage 要求 17+）。构建产物字节码**恒为 17**（`jvmTarget`/`sourceCompatibility` 显式锁定，与运行 Gradle 的 JDK 版本无关）；**发布/CI 基线 = Temurin 17**（保证与官方 SHA256 口径一致） |
| Gradle | **不安装** | 仓库内 `./gradlew` 为唯一真源，版本锁 `gradle/wrapper/gradle-wrapper.properties`（8.14.4） |
| Kotlin | **不单独安装** | 由 version catalog 的 Kotlin Gradle 插件驱动（`gradle/libs.versions.toml`，2.4.10） |
| detekt | **不需单独安装** | 由 Gradle 插件执行（`./gradlew detekt`） |

首次克隆后（**方式 A：mise（建议）**）：

```bash
mise install                 # 安装 temurin-17（.mise.toml）
export JAVA_HOME=$(mise where java)   # 或让 shell 激活 mise shims
./gradlew build              # 全量构建（编译 + 测试 + detekt）
```

**方式 B：不用 mise（同样受支持）**：

```bash
export JAVA_HOME=/path/to/jdk-17        # 或 jdk-21，两者均可
./gradlew build
```

> 说明：`.mise.toml` 只保留 `java = "temurin-17"`，是**给用 mise 的人的便利配置**，项目不要求必须用它。
> Gradle 曾以 mise 临时安装用于首次生成 Wrapper，生成后即按口径移除——日常开发一律用 `./gradlew`，不装全局 gradle。
> **JDK 选型边界（2026-09-24 核验）**：① 必须 **≥17**（低于 17 无法编译）；② 用 JDK 21 跑 Gradle 已实测通过
> （本机 JDK 21 全量 `./gradlew build` 绿：722 用例 / 718 执行 0 失败 / 4 跳过）；③ `jpackage` 的随包运行时由
> **执行构建的那个 JDK** 生成（模块集显式列出，`jdk.accessibility` 在 17/21 均存在）；④ 官方发布产物一律由 **CI（Temurin 17）**
> 产出，用别的 JDK 自编译不保证与 `SHA256SUMS` 清单一致。

> **中文字体已内嵌**（Noto Sans/Serif SC + JetBrains Mono，OFL-1.1，见 `docs/tech/dependency-licenses.md` §2b）：无 CJK 系统字体的 Linux/WSL 也能正确渲染中文，无需安装系统字体。

## 2. 环境检查清单（新环境验收）

- [ ] `java -version` 显示 **17.0.x 或 21.0.x**（Temurin 为发布基线；其他发行版同样可用）
- [ ] 用 mise 的话：`mise ls` 输出含 `java temurin-17`（**可选项**，未用 mise 时跳过）
- [ ] `./gradlew build` 通过（全量用例 0 失败 + detekt 零问题）
- [ ] `./gradlew :app:run` 启动 GUI，日志出现 `hello-chain ok | schema_version=2 | ... | market_api_key=****`（脱敏）
- [ ] 主壳可见侧边栏六页（含 D21 行情页）；顶栏 ☾/☀ 切换主题即时重渲染
      （**组件走查页仅在开发构建开启，默认不可见**——见 DEF-47）

## 3. WSL2 注记（重要）

- **GUI 冒烟走 WSLg**：`DISPLAY=:0` / `WAYLAND_DISPLAY=wayland-0` 默认就绪。
- **Skiko 渲染**：WSLg 无 GL 加速时 `./gradlew :app:run` 会抛 `skiko.RenderException: Cannot create Linux GL context`。
  解决（M0 实测）：以软件渲染启动——
  ```bash
  JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE_FAST" ./gradlew :app:run
  ```
  （或安装 mesa GL 驱动后走默认 OpenGL。）
- **托盘/通知**：WSLg 下行为不完整（无真实托盘协议）；最终以 CI 三平台 runner + 实机验证为准（M11 验收口径）。
- **钥匙串（M1 起）**：WSL2 常有 dbus 会话但**无 Secret Service 提供方**（gnome-keyring 等未运行）→ java-keyring 报 "No available keyring backend found"，应用按设计走「0600 本地密钥文件」降级、每次启动弹「安全提示」（预期行为，M1 T1.1 验收口径）；如需验证真实钥匙串路径：`sudo apt install gnome-keyring` 后在会话中启动再跑应用。
- **数据目录**：默认 `~/.wuzhufolio`；开发隔离用环境变量覆盖：`WUZHUFOLIO_DATA_DIR=/tmp/wzf ./gradlew :app:run`。

## 4. 密钥与安全配置

- M0 无真实密钥：数据库为明文 SQLite（`~/.wuzhufolio/wuzhufolio.db`）。
- M1 起：DB 密钥/设备密钥自动生成并写入 OS 钥匙串（javakeyring；Linux = Secret Service，ADR-002）；
  行情/交易所 API Key 在应用内设置页录入后，以**设备密钥**（OS 钥匙串，与 DB 密钥同级）加密，**密文存 settings 表全局行**（key=market.coingecko_key / market.cmc_key；ADR-002 §2.1 方案甲）；**不落明文、不进 .cpro 备份、不出设备**（PRD §1.1；设置页 M5/M6 落地）。
- 日志文件 `~/.wuzhufolio/logs/wuzhufolio.log` 已经 LogRedactor 脱敏；诊断导出前仍需人工复核（PRD §6）。

## 5. 常用命令

见根 `README.md`「构建 / 运行 / 测试」。

## 5.1 UI 热重载（Compose Hot Reload，ADR-007 §2.4，2026-09-28 接入）

界面改造期（M14）反复微调样式时**不必重启应用**：

```bash
# 一次性：JetBrains Runtime（JBR）自动下载（实验特性，仅首次）
./gradlew :app:hotRun -Pcompose.reload.jbr.autoProvisioningEnabled=true

# 日常：改 UI 代码 → 保存 → 界面即时刷新
./gradlew :app:hotRun            # 显式模式（按需触发 reload）
./gradlew :app:hotRunAsync       # 异步启动
# 其他任务：hotReloadMain / hotMcpServer（供 AI Agent 直连运行中的应用）
```

**边界（重要）**：

- 任务名是 **`hotRun`**（本项目 `:app` 是**纯 Kotlin/JVM** 模块；KMP 模块才叫 `hotRunJvm`）；
- 要求 **Java 21 或更低**（本项目 JVM target = 17 ✅）；JBR 未安装时命令会提示，按上面加属性自动获取；
- **只创建开发任务**：`build` / `createDistributable` / 打包链路与发布产物**完全不受影响**（版本目录新增 `compose-hot-reload = "1.2.0"`，与 CMP 1.12 捆绑版本一致）；
- 已知限制见官方 `docs/Known_limitations.md`（如 `@Composable` 签名变更、结构变更后需重启）。

## 5.2 组件走查与 M3 对照区（ADR-007 §2.3）

```bash
# 开发构建才会出现「组件走查」入口（DEF-47 的 DEV_UI 开关）
./gradlew :app:run -Pwuzhufolio.devUi=true
```

页面底部 **「Material 3 框架对照区」**：官方按钮/输入框（含粘贴自动清洗的数值框）/选择控件/容器/反馈/
日期时间选择器，与现有自绘组件**同主题并排**，用于目视拍板风格方向（T14.3 的前置人工门）。

## 5.3 换品牌色 / 重新生成 M3 配色（D38）

M3 配色是**算法产物**，不手改 hex：

```bash
mkdir -p /tmp/mcu && cd /tmp/mcu
npm i @material/material-color-utilities && npm i -D esbuild
cp <repo>/scripts/generate-m3-color-scheme.mjs .
npx esbuild generate-m3-color-scheme.mjs --bundle --platform=node --format=cjs --outfile=gen.cjs
node gen.cjs > <repo>/ui/src/main/kotlin/com/wuzhufolio/ui/theme/M3ColorRoles.kt
```

- 换色：改脚本里的 `SEED`（默认 `#1F5A48` 墨绿）后重跑；**浅色与深色共用同一个种子**（M3 规范口径）。
- 脚本内置 **WCAG 对比度自检**：任一正文/语义色对底色低于 4.5:1 会**直接报错退出**，不会生成不可读的配色。
- 依赖仅生成期使用（Google 官方包，Apache-2.0），**不进构建产物**。

## 6. 故障排查

| 症状 | 处理 |
|------|------|
| `java: command not found` | `mise install` 后激活 shims 或 `export JAVA_HOME=$(mise where java)` |
| Gradle 版本警告（<8.14.4 deprecated） | 已锁 8.14.4（Kotlin 2.5 起最低要求）；升级 Gradle 用 `./gradlew wrapper --gradle-version <v>` |
| WSLg 黑窗/GL 异常 | 见 §3 SOFTWARE_FAST |
| 测试库冲突 | 删 `~/.wuzhufolio` 或用 `WUZHUFOLIO_DATA_DIR` 换目录 |
