# Compose Desktop 界面框架与组件统一调研（0.1.1 反馈专项）

> **调研日期**：2026-09-28 · **触发**：0.1.1 发布后人工反馈「界面笨拙，各个界面组件都像手工制作，没有总体风格和特点」
> **调研问题**：GitHub / 技术社区里有没有「基于 Compose Desktop 的框架或脚手架」，能**统一风格**并**现成提供各类组件**？
> **结论地位**：本文是**决策输入**，不是决策本身。选型与分级按 `AGENTS.md §8.4` **停在人工门**，由人拍板。
> **证据口径**：✅ = 本次一手实测（GitHub API / Maven Central 元数据 / 本地构件反查 / Gradle 解析报告 / Compose 源码）；
> 🟡 = 二手（README/官方文档自述）；❓ = 未核实。**凡未实测者一律不写成结论。**

---

## 0. 一句话结论

**有现成资产，但没有「一个库解决全部」的方案；而本项目其实已经依赖了最合适的那个框架。**

三条硬事实（均为一手实测）：

1. **本项目已经在用 Compose Multiplatform 官方 Material 3，且解析到的是稳定版 `1.9.0`**（✅ `./gradlew :ui:dependencies` 实测
   `org.jetbrains.compose.material3:material3:1.9.0`；`compose-gradle-plugin:1.12.0` 的 `ComposeBuildConfig` 中
   `composeMaterial3Version = 1.9.0`）。
2. **这个版本里已经有 63 个组件族**，包括反馈中想要的日期/时间选择器（✅ 本地 jar 反查：`DatePicker`、`DatePickerDialog`（skiko 实现）、
   `TimePicker`、`TimePickerDialog`、`DateRangePicker`、`SegmentedButton`、`SearchBar`、`Tooltip`、`Snackbar`、`NavigationRail` …）。
3. **但代码里几乎没用**：`ui/` 110 个 Kotlin 文件中，Material 3 只被用到 3 个控件（`OutlinedTextField` / `Checkbox` / `DropdownMenu`），
   其余 33 处引用只是 `material3.Text`；全部按钮、弹窗、下拉、表格、状态栏、卡片都是 **Foundation 手绘**
   （`WzButton` = `Box` + `background` + `border` + `clickable` + `Text`）。

⇒ **「手工感」的根因不是「没选对框架」，而是「没有把框架当作唯一真源」**：
主题层**其实已经有了 `MaterialTheme` 单点包裹**（`ui/theme/Theme.kt` 的 `WuzhuTheme`），但——
① 它**只映射了 11 个 color role**（`primary`/`onPrimary`/`background`/`onBackground`/`surface`/`onSurface`/`surfaceVariant`/`onSurfaceVariant`/`outline`/`error`/`onError`），
`primaryContainer`/`secondary`/`tertiary`/`surfaceContainer*`/`outlineVariant`/`inverse*`/`scrim` 等**大量 role 未映射**，
**也没有传 `Typography` 与 `Shapes`**；
② **组件层完全绕开了它**——自绘组件一律读 `WzTheme` 自建 token，M3 控件只用了 3 个；
③ 于是每个页面各自手搓一套微设计，交互态（hover/pressed/disabled/focus）与动效规范也不统一。
**换框架能解决一部分，但不做「token 全量映射 + 组件收口」，换任何框架都会重新长出同样的手工感。**

---

## 1. 现状诊断（代码事实，可复核）

| 维度 | 实测事实 | 影响 |
|------|----------|------|
| 组件数量 | `ui/src/main/kotlin/com/wuzhufolio/ui/components/` 共 **11 个文件**（Button/TextField/Modal/Select/Table/AdaptiveTable/Card/Toast/StatusBar/PageOverlay/CoinSuggestionList），合计 1412 行 | 一个 18 页应用只有 **11 个**自研组件文件 |
| 组件实现 | `WzButton`：`Box` + `.clip` + `.background` + `.border` + `.clickable` + `Text`，**无涟漪、无悬停态、无按下态、无动效、无 loading/icon 变体** | 「手工感」最直观的来源：交互四态不全 |
| 弹层 | `WzModal`：同窗口 `Box` 叠加（`AGENTS.md §7.3-①` 的既定方案，✅ 正确且必须保留） | 平台约束资产，**不能因换框架而丢** |
| 表格 | `AdaptiveTable`（D33 自研，含最小宽/单行/截断悬停/单元线） | 见 §5.5：**所有第三方库都没有数据表格**，自研是必然 |
| Material 3 使用面 | 34 文件引用，其中 33 个只用 `Text`；控件仅 3 个（`OutlinedTextField`/`Checkbox`/`DropdownMenu`）；`MaterialTheme` 全局仅 1 处（`WuzhuTheme`，见下行） | 官方组件库基本闲置 |
| 主题 | `ui/theme/`：`ColorTokens`/`Typography`/`Theme`/`Breakpoints`/`WzFonts`。✅ `WuzhuTheme` 内**确有 `MaterialTheme` 单点包裹**，但**只映射 11 个 color role**、**未传 `Typography`/`Shapes`** | 有主题层、缺「全量 role 映射 + 组件消费面」 |
| 交互态与动效 | 自绘组件只有「常规/禁用」两态；`WzButton` 仅有焦点描边，**无 hover/pressed/涟漪/过渡** | 「手工感」的第二来源 |

**可见缺口清单（对照 M3 1.9.0 已有能力）**：日期/时间选择、分段控件、下拉选择（ExposedDropdownMenu）、
工具提示、Snackbar/Toast 一致性、搜索框、进度指示、底部弹层、导航栏/导航轨、Chip、Badge、轮播、下拉刷新。
**这些全部可以零新增依赖拿到**——因为 `material3:1.9.0` 已经在依赖树里。

---

## 2. 候选全景（四类，共 16 个候选）

| 类别 | 候选 | 定位 |
|------|------|------|
| **A 官方/平台** | Material 3、Material 3 Adaptive、Material 3 Expressive（1.9.0 已含 `MaterialExpressiveTheme`/`MotionScheme`） | 打底与统一 |
| **B 桌面风格设计系统** | Jewel、Compose Fluent UI、Miuix（HyperOS 风）、SaltUI、Carbon（IBM Carbon）、compose-cupertino、serene/Kore/kepko（早期） | 直接给「总体风格」 |
| **C 行为/工具件** | compose-unstyled（renderless 行为层）、Calf（自适应 + 平台 API + 日期/时间选择）、material-motion-compose、compose-icons、KoalaPlot（图表） | 补充件 |
| **D 脚手架/工具链** | 官方桌面模板（⛔ 已归档）、Kotlin/KMP-App-Template、kmp.jetbrains.com 向导、terrakok Wizard、**Compose Hot Reload**（含 MCP server） | 起步与迭代效率 |

---

## 3. 候选对比表（硬数据）

> star / 归档 / 最后提交 = ✅ GitHub API 实读（2026-09-28）；版本与日期 = ✅ Maven Central `maven-metadata.xml` / POM 实读。

| 名称 | 类型 | Maven 坐标 | 最新版本（发布） | 许可 | ★ | 最后提交 | 桌面 | 组件覆盖 | 表格 | 日期/时间 | 结论 |
|------|------|-----------|------------------|------|---|----------|------|----------|------|-----------|------|
| **Material 3**（CMP 官方） | 设计系统 | `org.jetbrains.compose.material3:material3` | **1.9.0**（CMP 1.12 插件默认绑定，本项目已解析） | Apache-2.0 | 随 CMP 19,389 | 2026-09-26（CMP 主仓） | ✅ | **63 组件族**（实测 jar） | ❌ | ✅ `DatePicker`/`TimePicker`/`DateRangePicker` | ✅ **首选打底** |
| Material 3 Adaptive | 布局适配 | `org.jetbrains.compose.material3.adaptive:adaptive*` | 1.3.0-rc01 | Apache-2.0 | 同上 | 同上 | ✅ | 窗口尺寸类 / ListDetail / NavigationSuite | ❌ | ❌ | 🟡 可选 |
| **Jewel** | 桌面设计系统（IDE 风） | `org.jetbrains.jewel:jewel-int-ui-standalone` | **0.41.0-262.10968.63**（2026-09-16） | Apache-2.0（POM 实读） | 861（归档镜像仓；开发已迁至 `intellij-community/platform/jewel`） | 2026-09-27（迁移后仓库） | ✅ **仅桌面** | ~58 个组件（TabStrip/LazyTree/SplitLayout/ContextMenu/DecoratedWindow…） | ❌ | ❌ | ⚠️ **高风险备选**（见 §5.1） |
| Compose Fluent UI | Windows 11 Fluent 风 | `io.github.compose-fluent:fluent` | **v0.1.0**（2025-08-10） | Apache-2.0 | 736 | 2026-08-18 | ✅ | 覆盖最广（CommandBar/InfoBar/CalendarView/DateTimePicker…） | ❓ | 🟡 自述有、TODO 矛盾 | ❌ 不建议主用（见 §5.3） |
| Miuix | HyperOS 风 | `top.yukonga.miuix.kmp:miuix-ui` | 0.9.4 | ❓ | ~1.3k | 2026-09 | ✅（含 desktop JVM） | 组件 + preference/nav/icons/blur | ❓ | ❓ | 🟡 风格绑定过强 |
| SaltUI | 通用组件 | `io.github.moriafly:salt-ui` | 3.0.0-beta01（2026-09-06） | Apache-2.0 | 425 | 2026-09-23 | ✅ | 组件集（Salt Player 在用） | ❓ | ❓ | 🟡 beta；依赖对齐 CMP 1.12 |
| Carbon（IBM） | 企业设计系统 | `io.github.gabrieldrn:carbon` | 0.7.1（2026-03-04） | Apache-2.0 | 334 | 2026-07-01 | ✅ | 中（含 datepicker/uishell） | ❌ | ✅ | 🟡 基线落后（CMP 1.9.3） |
| Calf | 自适应 + 平台 API | `com.mohamedrejeb.calf:calf-ui` | 0.14.0（2026-09-13） | Apache-2.0 | 1722 | 2026-09-20 | ✅ | button/dropdown/sheet/datepicker/timepicker（✅ 源码目录实测） | ❌ | ✅ | 🟡 按需取件 |
| compose-cupertino | iOS 风 | `io.github.alexzhirkevich:cupertino*` | 0.1.0-alpha04（**2024-04-07**） | Apache-2.0 | 1656 | 2025-10-05 | ❌ 与 Win/Linux 目标无关 | — | — | — | ❌ 实质停更 |
| compose-unstyled | renderless 行为层 | ❓ 未核实 | ❓ | MIT | 1234 | 2026-09-19 | ✅ | 只给行为/无障碍，**不给视觉** | ❌ | ❌ | 🟡 行为层参考 |
| material-motion-compose | 动效 | `com.github.fornewid:*` ❓ | ❓ | Apache-2.0 | 661 | 2026-05-28 | ✅ | Material Motion | — | — | 🟡 可选 |
| KoalaPlot | 图表 | `io.github.koalaplot:koalaplot-core` | 0.12.1（2026-08-08） | MIT | 785 | 2026-09-19 | ✅ | 折线/柱/饼/轴/图例 | — | — | 🟡 补充件 |
| compose-icons | 图标 | `br.com.devsrsouza.compose.icons:*` | 1.1.1 等 | 各自（多为 MIT/Apache） | — | — | ✅ | 图标集 | — | — | 🟡 补充件 |
| 官方桌面模板 | 脚手架 | — | — | Apache-2.0 | 500 | 2025-07-14 | ✅ | ⛔ **已归档，README 标 obsolete** | — | — | ❌ 不可用 |
| Kotlin/KMP-App-Template | 脚手架 | — | — | Apache-2.0 | 695 | 2026-09-18 | ✅ | 共享 UI + Navigation + Ktor + Koin + desktop 模块 | — | — | 🟡 仅借鉴主题组织 |
| terrakok/Wizard | 脚手架 | — | — | MIT | 708 | 2026-09-18 | ✅ | Web 向导 | — | — | 🟡 本项目 P3 已完成，价值有限 |
| **Compose Hot Reload** | 工具链 | 随 CMP 捆绑（`composeHotReloadVersion = 1.2.0` ✅ 实测） | 1.2.0（CMP 1.12 捆绑；独立仓 1.3.0-alpha01） | Apache-2.0 | 1423 | 2026-09-27 | ✅ | 改 UI 代码→实时生效；**含 MCP server 供 AI Agent 直连运行中的应用** | — | — | ✅ **建议接入**（见 §5.8） |

---

## 4. 官方组件到底有什么（✅ 本地构件反查，非文档转述）

对 `~/.gradle/.../material3-desktop-1.9.0.jar` 逐类反查，共 **63 个组件族**：

```
AlertDialog AppBar(AppBarColumn/Row/Dsl) Badge BottomSheetScaffold Button Card Checkbox Chip ColorScheme
DateInput DatePicker DateRangeInput DateRangePicker Divider DragHandle ExposedDropdownMenu FloatingActionButton
HorizontalCenterOptically Icon IconButton InteractiveComponentSize Label ListItem MaterialTheme Menu
ModalBottomSheet MotionScheme NavigationBar NavigationDrawer NavigationItem NavigationRail OutlinedTextField
ProgressIndicator RadioButton Ripple Scaffold SearchBar SecureTextField SegmentedButton Shapes SheetDefaults
ShortNavigationBar Slider Snackbar SnackbarHost Surface SwipeToDismissBox Switch Tab TabRow Text TextField
TimePicker TimePickerDialog TonalPalette Tooltip Typography WideNavigationRail WideNavigationRailState
```

- **Expressive 能力也已在稳定版里**：`MaterialExpressiveTheme`、`MotionScheme`、`LoadingIndicator`、`ButtonGroup`、
  `FloatingToolbar`（✅ jar 反查命中）。⇒ 想要「有特点、不土」的观感，不必等 alpha。
- **`DatePickerDialog` 在桌面端有 skiko 实现**（✅ 源码 `material3/src/skikoMain/.../DatePickerDialog.skiko.kt`），
  它委托给 `BasicAlertDialog` → 公共 `Dialog`；而桌面公共 `Dialog` 的实现是
  `compose/ui/ui/src/skikoMain/.../Dialog.skiko.kt` → **`ComposeSceneLayer`（同窗口场景层）**。
  > ⚠️ **纠正常见误解**：桌面端公共 `Dialog` **不是**独立 AWT 窗口（那是已废弃的 `Dialog(onCloseRequest, …)`→`DialogWindow`）。
  > 但 `AGENTS.md §7.3-①` 的既有结论是「Popup/Dialog 承载交互在本项目实测不可靠」——因此**建议绕开 `*Dialog` 壳**，
  > 直接用**无壳的 `DatePicker` / `TimePicker` 内容**装进现有 `WzModal`（既守住平台约束资产，又拿到官方控件）。

---

## 5. 关键结论（逐条）

### 5.1 Jewel —— 质感最强，风险也最高（建议只作备选）

- ✅ **主仓已归档**（`archived=true`，最后 push 2025-04-02），README 明写 `[MOVED TO IJ PLATFORM]`；
  开发迁至 `JetBrains/intellij-community/platform/jewel`，**仍在高频迭代**（✅ 最近提交 2026-09-27；
  2026-09-24 提交为 `[JEWEL-1446] Bump CMP to 1.12.1`）。
- ✅ **坐标与许可已核实**：`org.jetbrains.jewel:jewel-ui:0.41.0-262.10968.63`，POM `<licenses>` = Apache-2.0
  （AGPL-3.0 兼容无碍），版本号后缀 `262.10968.63` 是 **IntelliJ Platform 构建号**（强耦合信号）。
- ✅ **两个致命缺口**：**没有数据表格**、**没有日期/时间选择器**（组件清单 ~58 项里两者皆无）。
- 🟡 **官方自述风险**：CMP 版本不保证与 Jewel 版本兼容；打包后若编译期用到 coroutines ≥1.11 存在已知崩溃；
  **不支持 ProGuard/混淆**；0.40→0.41 曾误删 deprecated API 造成二进制兼容事故。
- **判断**：它解决的是「IDE 级桌面质感」，但本项目最核心的「持仓表 + 交易流水表 + 日期时间录入」它一个都不覆盖，
  且版本耦合与升级节奏会把维护成本长期挂在我们身上。**除非人工明确要「IDE 风」外观，否则不建议主用。**

### 5.2 Material 3 —— 覆盖最全、零新增依赖（建议打底）

- ✅ **已在本项目依赖树且为稳定版 1.9.0**（非 alpha）。
- ✅ 组件覆盖 = 63 族，含本项目缺的绝大多数控件。
- ✅ Apache-2.0（JetBrains 维护、与 CMP 主仓同步发布）。
- ⚠️ 代价：**观感默认偏「Google/Material」**——但颜色/字体/圆角/密度全部由 token 决定，
  把现有 `design-tokens.md`（暖纸 + 墨炭 + 墨绿/黄铜 accent）映射进 `ColorScheme` 后，
  视觉仍是「私人账本」，**并不会变成 Android 应用**。
- ⚠️ 表格仍需自研（本项目已有 `AdaptiveTable`，D33）。

**版本口径（两个容易混淆的数字，已实测定版）**：

| 说法 | 事实 | 证据 |
|------|------|------|
| 「CMP 1.12 绑定 material3 `1.12.0-alpha03`」 | 那是 CMP **CHANGELOG 表格**里列的 1.12 线 M3 构件（基于 Jetpack M3 1.5.0-alpha22）；**`compose.material3` DSL 实际解析到的是稳定版 `1.9.0`** | ✅ `./gradlew :ui:dependencies` = `org.jetbrains.compose.material3:material3:1.9.0`；✅ 插件 jar 内 `ComposeBuildConfig.composeMaterial3Version = 1.9.0` |
| 「`compose.material3` 访问器可用」 | ✅ **在 CMP 1.12.0 已被 `@Deprecated`**，弃用文案 `Specify dependency directly`，建议值就是 `org.jetbrains.compose.material3:material3:1.9.0` | ✅ 插件 jar `ComposePlugin$Dependencies.class` 常量池实测 |

> **给后续任务的两条硬提醒**：① 若要显式写坐标，**不要**照抄 CHANGELOG 里的 `1.12.0-alpha03`（那是 alpha 线）；
> ② M3 版本与 CMP 主版本**不同步**（1.12.0 ↔ 1.9.0），评审时别误判为版本写错。

**opt-in 现状（✅ javap 实测 `material3-desktop-1.9.0.jar`，**以 1.9.0 为准**）**：

| API | 是否需要 `@OptIn(ExperimentalMaterial3Api::class)` |
|-----|---------------------------------------------------|
| `DatePicker` / `DateRangePicker` / `TimePickerDialog` / `OutlinedTextField` | **不需要** |
| `TimePicker` / `TimeInput` / `rememberTimePickerState` / `TimePickerState` | **需要** |
| `DatePickerDialog`（skiko 实现） | **需要** |

> 注意：网上/第三方调研常引用 **1.12.0-alpha03** 的结论（说 `TimePicker` 也不再需要 opt-in）——**对本项目不适用**，
> 因为本项目解析到的是 1.9.0。

**桌面端已知缺陷（✅ 经 YouTrack 公开 API 复核状态）**：

| 票号 | 影响 | 状态 |
|------|------|------|
| [CMP-10038](https://youtrack.jetbrains.com/issue/CMP-10038) | `DatePicker` 在 JVM/JS 上时区处理错误，**UTC 以西时区月份下拉标签会显示上一个月**（日历网格正确） | **Open**（2026-09-28 复核） |
| [CMP-10319](https://youtrack.jetbrains.com/issue/CMP-10319) | `TimePicker` 在 desktop（OpenGL 渲染）下选中色渲染错误、数字不显示 | **Submitted**（2026-09-28 复核） |

⇒ 落地前必须在 **UTC 以西时区**（如 `TZ=America/Denver`）与 **本项目实际渲染后端**（WSL2 用 `SOFTWARE_FAST`，
Windows 用默认 D3D/OpenGL）各实测一次。

### 5.3 Compose Fluent UI —— 最贴 Windows，但成熟度不够（不建议主用）

- 🟡 官方自述：**experimental，API 可无通知变更**；README 未勾选 Accessibility Semantics；Mica 只有近似实现；
  README 与 TODO 关于 DatePicker 的勾选状态**自相矛盾**（❓ 需实测）。
- ✅ **19 个月只发布过 1 个版本**（v0.1.0，2025-08-10；后续仅 dev 提交）。
- **判断**：对一个要长期维护、要出安装包的开源财务工具，**稳定性风险高于收益**；若人工偏好 Windows 原生观感，
  可只借鉴其**排版与间距口径**，不引入依赖。

### 5.4 其他第三方风格库

- **Miuix**（HyperOS 风）、**Carbon**（IBM 风）、**SaltUI**：都是认真实现，但分别带来「小米风 / IBM 风 / 播放器风」，
  与本项目「私人账本 / 安全控制台」的设计方向（`design-tokens.md §1`）冲突；Carbon 还落后 3 个 CMP 小版本。
- **Calf**：定位是「按平台切换 Material/Cupertino」，与「统一风格」目标**方向相反**；但其
  `datepicker`/`timepicker`/`file picker` 单件质量不错（✅ 源码目录实测），可作为**按需取件**备选。
- **compose-unstyled**：只给行为与无障碍、不给视觉——对「减少手搓外观」帮助有限。

### 5.5 共同硬缺口：**数据表格**（换任何库都躲不掉）

M3 ❌、Jewel ❌、Carbon ❌、Fluent ❓。而本应用的核心页面（资产、交易、资金、币种详情）**全是数据表**。
⇒ **表格必须自研或深改**，本项目已有 `AdaptiveTable`（D33，含最小宽/单行/截断悬停/单元线/滚动条槽），
**这是既有资产而不是负债**；统一改造时应把它升级为「token 驱动的组件层一员」，而不是推倒重来。

### 5.6 日期/时间选择（对应反馈 ②）——**零新增依赖即可解决**

- ✅ `DatePicker` / `TimePicker` / `DateRangePicker` 都在 `material3-desktop:1.9.0` 里，本项目**已经依赖**。
- ✅ 桌面公共 `Dialog` 默认走 **同窗口场景层**（`compose.layers.type` 默认为 `COMPOSE`；只有显式设成实验性的
  `WINDOW` 才会变成独立平台窗口，CMP CHANGELOG 里相关修复都标注为「with `compose.layers.type=WINDOW`」）。
  ⇒ 用官方 `DatePickerDialog` 并**不会**自动违反「不用独立窗口」这条，但**仍建议**把 `DatePicker`/`TimePicker`
  内容嵌进 `WzModal`：① 守住 `AGENTS.md §7.3-①` 既有实测结论；② 外观/间距/按钮与全应用一致；
  ③ `DatePickerDialog` 还需 `@OptIn`（见 §5.2）。
- ⚠️ 需实测两项：**UTC 以西时区的月份标签**（CMP-10038，仍未修）与**渲染后端下的 TimePicker 配色**（CMP-10319）。

> ✅ **本项目栈内渲染实证（2026-09-28 一次性 spike，临时测试已删）**：在 `WuzhuTheme` 内并排渲染
> `DatePicker(rememberDatePickerState())` + `TimePicker(rememberTimePickerState(14, 30, true))`，
> Compose UI 测试实测 **无异常、产生 60 个可交互（clickable）语义节点**，`14`/`30` 文本节点存在
> ⇒ 已从「jar 里有类」升级为「**本项目主题/字体/依赖组合下确实能渲染、可交互**」。
> 仍未验证：**真实 GUI 下的键盘与 IME 行为**（§7.3-③ 强制项）与上面两个官方缺陷。

### 5.6.1 数值输入健壮性（对应反馈 ①）——官方有现成机制

- ✅ Compose Foundation 的 **`InputTransformation`**（稳定自 1.7.0-alpha05，CMP 1.12 远超此线）**天然作用于粘贴/拖放**：
  官方原文——transformation 会应用于「键盘事件、**粘贴或拖放文本**、无障碍服务与测试」。
  ⇒ **清洗粘贴内容不需要拦截剪贴板**（社区常见的 `onPreviewKeyEvent` 拦 Ctrl+V 是 workaround，不推荐：会绕过右键粘贴菜单）。
- ✅ `InputTransformation` 的接收者 `TextFieldBuffer` 提供 `asCharSequence()` / `replace(...)` / `delete(...)` / `revertAllChanges()`。
- ✅ 显示层格式化用 **`outputTransformation`**，**不再需要写 `OffsetMapping`**（旧的 `visualTransformation` 必须自己映射光标，
  是最易出 bug 的地方）。
- ⚠️ **两个坑**：① **程序化赋值（`state.edit {}`/`rememberTextFieldState(initialText=)`）不经过 transformation**
  ——从 `.cpro` 恢复、表单预填时必须自己再清洗一次；② `InputTransformation`/`outputTransformation` **只存在于
  `OutlinedTextField(state: TextFieldState, …)` 重载**，与现有 `value/onValueChange` 重载**不能混用**
  ⇒ 这是 `WzTextField` 的内部实现替换，属**公共 API 变更**（需先定级）。
- ⚠️ **locale 歧义警告**：`1,234.56`（en-US）与 `1.234,56`（de-DE）语义相反——**「把 `,` 一律当千分位删掉」会在欧陆格式下
  静默产生 100 倍误差**。记账应用应**定义唯一规范输入**（如只接受 `1234.56`），对歧义串**报错而不是猜**。
- ❌ **不存在成熟的第三方「金额输入框」库**（候选全是 0.x alpha / 0 star / Android-only），自写约百行（清洗 + `DecimalFormat`）更安全。


### 5.9 腾讯专项：ovCompose 与 KuiklyUI（人工 2026-09-28 点名核查）

> 人工问「腾讯好像有一个基于 Compose Desktop 的项目？看看是否可用」——答案是**有两个，但都不适用**。
> 下表均为 ✅ 一手实测（GitHub 搜索 API + raw README + Maven 元数据，2026-09-28）。

| 项目 | 是什么 | 目标平台 | 版本基线 | 构件发布 | 最后活动 | 许可 | 对本项目 |
|------|--------|----------|----------|----------|----------|------|----------|
| **ovCompose**<br>`Tencent-TDS/ovCompose-multiplatform-core`（348★）+ `ovCompose-sample`（254★） | 腾讯**视频团队**（Oteam）基于 Compose Multiplatform 生态推出的跨平台框架，**目的是补 JetBrains CMP 不支持鸿蒙、以及 iOS 混排受限** | `android` / `ohosArm64`（鸿蒙）/ `uikit`（iOS）——**无桌面 JVM** | fork 自 **compose-1.6.1-dev**（本项目为 CMP 1.12.0） | 官方文档给的是 `publishComposeJbToMavenLocal`（**只发本地 maven**），鸿蒙侧还需 DevEco-Studio + Ninja 编译 C++ 产出 `compose.har` | core 2026-06-04、sample 2025-08-26 | Apache-2.0（core 仓库） | ❌ **不可用**：无桌面目标 + 版本落后 6 个小版本 + 无公共构件 |
| **KuiklyUI**<br>`Tencent-TDS/KuiklyUI`（3,544★） | 腾讯 TDS 的 KMP 跨端 UI 框架，**自有 DSL 与渲染管线**（非 Compose UI） | Android / iOS / **macOS（Alpha）** / HarmonyOS / Web / MiniApp——**Windows/Linux 均无** | 自有 runtime（`core-render-*`） | Maven Central | 2026-09-24（活跃） | 仓库 NOASSERTION（❓ 未逐条核许可） | ❌ **不可用**：无 Windows/Linux 目标，且换它等于重写全部 UI |

**结论**：腾讯这条线服务的是**鸿蒙/移动全端**，与本项目「Windows/Linux 桌面 + Kotlin JVM」正交；
**不构成对路线 A 的任何冲突**（本决策仍为零新增第三方依赖）。
> 另：`Tencent/TDesign`（4,076★ MIT）是**企业设计系统**，但 ✅ 检索确认**没有 Compose 实现**（`tdesign compose` 命中 0），
> 只能作为视觉参考，不能作为组件库引入。

### 5.7 脚手架/模板：本项目已过此阶段

- ✅ 官方 `compose-multiplatform-desktop-template` 已归档且 README 标 obsolete；
  `Kotlin/KMP-App-Template`（695★）与 `terrakok/Compose-Multiplatform-Wizard`（708★）仍活跃。
- 本项目 P3 已完成（四模块 + CI + version catalog），**不需要换脚手架**；值得借鉴的只有它们的
  `ui/theme` 组织方式与「资源（字体/图标/文案）统一走 compose resources」的口径。

### 5.8 最被低估的一条：**Compose Hot Reload**（工具链，建议接入）

- ✅ CMP 1.12 的 Gradle 插件已捆绑 `composeHotReloadVersion = 1.2.0`（本地插件类实测），独立仓库 1423★、Apache-2.0、活跃。
- ✅ CHANGELOG 记录：**为 Compose Hot Reload 引入了 MCP server，使 AI Agent 能实时与运行中的应用交互**（CMP #5671）。
- **意义**：界面统一改造是「反复目视微调」的活，当前每改一处要重启应用（jpackage/JVM 冷启动），
  迭代成本是「界面笨拙」的隐性成因之一。接入后改一处样式即时可见——**这是本次调研中性价比最高的单点改进**。

---

## 6. 三条路线（✅ 人工已于 2026-09-28 拍板 **路线 A**，见 ADR-007）

### 路线 A（**推荐**）：M3 打底 + token 单源 + 保留平台约束封装

- **做法**：① `MaterialTheme` 单点包裹（现有 token → `ColorScheme`/`Typography`/`Shapes`）；
  ② 补 M3 不提供的 `Spacing` / `Motion` token；③ `Wz*` 组件**内部实现**换成 M3 官方组件（外观/四态/动效由官方给），
  **对外 API 与 `AGENTS.md §7.3` 约束不变**；④ 用 M3 现成件补齐缺口（日期时间、下拉、Tooltip、Snackbar、分段控件…）；
  ⑤ 自研表格升级为 token 驱动。
- **工作量**：中（组件层重写 ~9 个文件 + 页面逐页回归；含 §7.3 强制键盘/IME 验收）。
- **风险**：低（无新增依赖、无新许可、无版本耦合）；主要成本是**逐页视觉回归**。
- **对设计方向**：保持「暖纸账本」身份不变。

### 路线 B：Jewel 外壳 + M3 内容（混合）

- **做法**：窗口装饰/标签页/树/右键菜单/滚动条用 Jewel，业务控件用 M3，自有 token 统一。
- **代价**：**两套主题体系并存**（`JewelTheme` vs `MaterialTheme` 双向映射），长期维护负担；
  无表格无日期时间；版本耦合 IJP 构建号；仍需自研表格。**风格割裂风险恰与「统一风格」目标相悖。**

### 路线 C：整体引入第三方风格库（Fluent / Miuix / Carbon）

- **收益**：一次到位拿到「总体风格」。
- **代价**：风格绑定（Windows/HyperOS/IBM）与本产品「隐私账本」定位冲突；成熟度与升级风险（§5.3/§5.4）；
  同样没有表格。**不建议作为主方案。**

### 无论选哪条，都建议同时做

1. **接入 Compose Hot Reload**（§5.8）——否则任何路线的回归成本都会被放大。
2. **保留 `WzModal`/`PageOverlay`/`AdaptiveTable`**——它们是用真实缺陷换来的平台资产（`AGENTS.md §7.3`、D33），不是重复造轮子。

---

## 7. 需要人工拍板的选项（✅ 已拍板：2026-09-28 人工「按你的建议顺序执行」→ **路线 A 采纳**，见 `adr/ADR-007-UI设计系统与组件收口.md`）

| # | 选项 | 说明 | Agent 的分级建议 |
|---|------|------|------------------|
| ① | **是否走路线 A**（M3 打底 + token 单源） | 零新增依赖、风险最低 | 整体属 **C2 建议**（改已通过模块的视觉/交互基线、触及 P1 设计基线），但**可拆成 C1 批次**分步走（见 ②③） |
| ② | 先做「组件补齐批次」：日期时间选择器 + 数字输入健壮性 + 弹窗空格缺陷（见 `docs/dev/0.1.1-使用反馈.md`） | 直接对应用户已反馈的 3 个问题，收益立竿见影 | **C1**（新增组件 + 缺陷修复；缺陷部分可按 C0 走） |
| ③ | 是否接入 Compose Hot Reload | 只动构建配置与开发流程，**不影响发布产物** | **C1**（工程效率，不涉产品行为） |
| ④ | 是否要先做 **Jewel spike**（单分支试用）再决定路线 B | 用最小成本看清「IDE 质感」是否符合预期 | **C1**（探索性，产出结论后丢弃分支） |

> **建议的最小验证顺序**（每一步都可回滚、都不改发布行为）：
> **Step 1** 接入 Hot Reload → **Step 2** 在 DEV 组件走查页加一块「M3 对照区」（按钮/输入/下拉/分段/日期时间/Tooltip/Snackbar 与现有组件并排）
> → 人工目视拍板风格方向 → **Step 3** 按拍板结果做组件层替换（分批、逐页回归）。

---

## 8. 待验证项（写入 ADR 风险项）

| # | 待验证 | 验证方式 | 状态 |
|---|--------|----------|------|
| 1 | M3 `DatePicker`/`TimePicker` 嵌入 `WzModal` 后键盘/IME 是否可用 | Compose UI 测试（`performTextInput` + `assertIsFocused`）+ 人工 GUI 逐字录入（§7.3-③） | 🟡 **部分已验证**：渲染与可交互 ✅ 已 spike 实证（§5.6 引用块）；键盘/IME ⏳ 待做 |
| 2 | Jewel 0.41 × Kotlin 2.4.10 是否可编译/打包 | 独立分支最小 spike | ⏳ 待做（仅当人工要看路线 B） |
| 3 | 表格虚拟化（大列表性能） | 现有 `AdaptiveTable` 在 2000+ 行下的滚动帧率实测 | ⏳ 待做 |
| 4 | M3 全套替换后的对比度是否仍满足 WCAG AA | 复用 `ContrastTest` 扩展至 `ColorScheme` 全量 role | ⏳ 待做 |
| 5 | Hot Reload 与现有 CI/jpackage 流程是否冲突 | 本地跑通 `./gradlew :app:hotRunJvm`（或官方任务名）后核对打包产物不变 | ⏳ 待做 |

---

## 9. 证据清单（可复核）

```bash
# ① 本项目实际解析到的 Material 3 版本（稳定版 1.9.0）
./gradlew :ui:dependencies --configuration runtimeClasspath | grep material3

# ② CMP 插件内置版本常量（material3 1.9.0 / hot reload 1.2.0 / 插件 1.12.0）
unzip -p ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose/compose-gradle-plugin/1.12.0/*/compose-gradle-plugin-1.12.0.jar \
  org/jetbrains/compose/ComposeBuildConfig.class | strings | head -20

# ③ M3 1.9.0 组件族清单（63 个）
unzip -l ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.material3/material3-desktop/1.9.0/*/material3-desktop-1.9.0.jar \
  | grep -oE "androidx/compose/material3/[A-Za-z]+Kt\.class" | sed 's#.*/##;s#Kt.class##' | sort -u

# ④ Jewel 版本与许可（Maven Central 实读）
curl -s https://repo1.maven.org/maven2/org/jetbrains/jewel/jewel-ui/maven-metadata.xml | tail -5
curl -s https://repo1.maven.org/maven2/org/jetbrains/jewel/jewel-ui/0.41.0-262.10968.63/jewel-ui-0.41.0-262.10968.63.pom | grep -A4 licenses

# ⑤ 各候选库活跃度/许可（GitHub API）
for r in JetBrains/jewel compose-fluent/compose-fluent-ui MohamedRejeb/Calf gabrieldrn/carbon-compose \
         miuix-kotlin-multiplatform/miuix Moriafly/SaltUI composablehorizons/compose-unstyled \
         JetBrains/compose-hot-reload Kotlin/KMP-App-Template terrakok/Compose-Multiplatform-Wizard; do
  curl -s "https://api.github.com/repos/$r" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d['full_name'],d['stargazers_count'],(d.get('license') or {}).get('spdx_id'),d['archived'],d['pushed_at'][:10])"
done
```

**关键链接**：
- CMP CHANGELOG（版本绑定表 / Desktop 变更）：<https://github.com/JetBrains/compose-multiplatform/blob/master/CHANGELOG.md>
- M3 DatePickerDialog（skiko 实现）：`compose-multiplatform-core/compose/material3/material3/src/skikoMain/kotlin/androidx/compose/material3/DatePickerDialog.skiko.kt`
- 桌面公共 `Dialog` 实现（`ComposeSceneLayer`，**同窗口场景层**）：`compose-multiplatform-core/compose/ui/ui/src/skikoMain/kotlin/androidx/compose/ui/window/Dialog.skiko.kt`
- Jewel 新家：<https://github.com/JetBrains/intellij-community/tree/master/platform/jewel>
- Compose Hot Reload：<https://github.com/JetBrains/compose-hot-reload>
- KMP 库总目（持续维护）：<https://github.com/terrakok/kmp-awesome> · <https://github.com/mahozad/awesome-compose-multiplatform>

---

## 10. 未能核实的部分（诚实声明）

- 各第三方库在 **CMP 1.12.0 + Kotlin 2.4.10** 下的**实际运行**未实测（只核对了各自的兼容声明）。
- Miuix / SaltUI 的**许可证与组件明细**未逐一核实（表中标 ❓）。
- Fluent UI 的 DatePicker 到底是否可用（README 与 TODO 矛盾）未实测。
- Jewel 与 Kotlin 2.4.10 的兼容性未实测。
- 中文社区文章类证据（如掘金/知乎相关实践）本次未逐篇核实，故未写入结论。
