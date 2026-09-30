# ADR-007 · 桌面端 UI 设计系统：Material 3 打底 + token 全量映射 + 组件收口

> **状态**：**人工拍板采纳（2026-09-28，人工指令「按你的建议顺序执行」＋「先改大问题，再改小问题」）**
> **决策号**：ADR-007（`docs/tech/adr/` 第 7 篇；前 6 篇见 ADR-001…ADR-006）
> **级别**：**C2**（命中 `AGENTS.md §8.1` 红线 3「改已通过模块的行为（返工级）」与设计基线变更）
> **来源**：0.1.1 发布后人工反馈「界面笨拙，各个界面组件都像手工制作，没有总体风格和特点」→
> 调研报告 [`docs/tech/UI框架与组件统一调研.md`](UI框架与组件统一调研.md)（16 候选硬数据对比）
> **关联**：`AGENTS.md §7.3`（GUI 共性约束）· `docs/design/design-tokens.md`·`prototype/wuzhufolio-light.html`（视觉基准）·
> `docs/dev/decisions/D33-响应式与组件统一.md`· `docs/dev/0.1.1-使用反馈.md`（DEF-54/55/56）· task-breakdown **M14 / T14.1–T14.6**

---

## 1. 背景与问题

0.1.1 人工反馈的核心不是「某个控件坏了」，而是**组件体系缺位**：

| 事实（一手实测） | 出处 |
|---|---|
| `ui/` 共 110 个 Kotlin 文件，`ui/components/` 只有 **11 个自研组件**（1412 行） | 代码统计 |
| 官方 Material 3 **已在依赖树**（解析为稳定版 `1.9.0`，63 个组件族，含 `DatePicker`/`TimePicker`/`SegmentedButton`/`Tooltip`/`Snackbar`…） | `./gradlew :ui:dependencies` + jar 反查 |
| 但代码里 **M3 只用了 3 个控件**（`OutlinedTextField`/`Checkbox`/`DropdownMenu`），33 处引用只是 `Text` | grep 统计 |
| `MaterialTheme` 虽已单点包裹，但**只映射 11 个 color role**、**未传 `Typography`/`Shapes`** | `theme/Theme.kt`（改造前） |
| 按钮/弹窗/下拉/表格/卡片全部 **Foundation 手绘**（`WzButton` = `Box`+`background`+`border`+`clickable`+`Text`），无 hover/pressed/涟漪/动效 | `components/WzButton.kt` |

⇒ 结论：**不是没选对框架，而是没把框架当唯一真源**。因此本 ADR 的决策不是「换框架」，而是
**把已有的官方框架变成唯一真源，并建齐它不提供的那几类 token**。

## 2. 决策

### 2.1 三条候选路线（调研报告 §6 详列）与取舍

| 路线 | 内容 | 判定 |
|---|---|---|
| **A（采纳）** | **M3 打底 + token 全量映射 + 组件收口**（`Wz*` 对外 API 与 §7.3 约束不变，内部实现换成官方组件） | ✅ 零新增依赖、零许可风险、与后续移动端（PRD 已定 Material 3）天然一致 |
| B | Jewel 外壳 + M3 内容（桌面 IDE 质感） | ❌ 主仓已归档/迁入 intellij-community；**无数据表格、无日期选择器**；版本强耦合 IntelliJ 构建号；官方不支持 ProGuard |
| C | 整体引入第三方风格库（Compose Fluent UI / Miuix / Carbon / SaltUI） | ❌ 成熟度不足（Fluent 19 个月仅 1 版且自述实验性）或风格绑定（HyperOS / IBM）与本产品「隐私账本」定位冲突；**同样没有表格** |

**腾讯专项**：`Tencent-TDS/ovCompose`（腾讯视频团队，补 HarmonyOS 与 iOS 混排）目标平台为
`android/ohosArm64/uikit`，**无桌面 JVM**，基于 `compose-1.6.1-dev`（本项目为 1.12.0），构件仅发布到 local maven，
且需 DevEco-Studio + Ninja 工具链；`Tencent-TDS/KuiklyUI` 是自有 DSL 的 KMP 框架（桌面仅 macOS Alpha）。
⇒ **两者均不适用于本项目 Windows/Linux 桌面端**，不构成对本决策的冲突（详见调研报告 §5.9）。

### 2.2 主题层：**以 M3 规范为准**（D38 修订，2026-09-28）

> **口径升级（D38，amends D37）**：人工 2026-09-28 明确「界面尽量采用 Material 3 视觉规范（完整组件/配色/字体/动效）」，
> 因此本 ADR 的主题层口径由「自建 token 翻译成 M3 role」升级为「**直接用 M3 规范**」：
> **配色** = Google 官方算法从品牌种子色生成（`SchemeTonalSpot`，浅/深共用种子，生成物 `M3ColorRoles.kt`）；
> **字体** = M3 15 档 type scale 规范值（业务槽位投影）；**圆角** = M3 阶梯 4/8/12/16/28（全仓归一）；
> **动效** = M3 motion tokens（16 档时长 + 6 曲线）。保留项与例外清单见 `D38 §1/§3`。
> 具体实现见 §2.2.1。

### 2.2.1 实现（原 D37 版 + D38 升级）

- **ColorScheme 全量**（`theme/ColorSchemeMapping.kt`）：12 个自建 token 铺满 M3 语义位
  （`primary*/secondary*/tertiary*/error*/surface*/inverse*/outline*/scrim`），
  `surfaceContainer*` 由 `surface ↔ surface2` 插值得到（M3 现行「容器色阶」口径，不引入第 13 个色 token）。
  **不得回落到 M3 默认紫**——由 `ThemeMappingTest` 守护。
- **Typography 全量**（`theme/TypographyMapping.kt`）：M3 的 15 个槽位按语义就近映射到产品层级，
  正文维持 **14sp**（桌面密度，而非 M3 默认 16sp）。
- **Shapes 全量**（`theme/Shapes.kt`）：全应用圆角收敛为 5 档（4/7/10/14/20dp），
  与既有控件（7dp）与容器（10dp）口径一致。
- **M3 不提供的两类 token 必须自建**（这是本决策里最容易被低估的一半）：
  - `WzSpacing`（`theme/Spacing.kt`）：2/4/6/8/12/16/20/24/32dp 阶梯；
  - `WzMotion`（`theme/Motion.kt`）：120/200/320ms + 三条曲线（标准/减速/强调）。

### 2.3 组件层：自绘 → 官方（分批判定，逐步替换）

原则：**对外 API 与平台约束不变，内部实现换成 M3 + token**。

| 组件 | 处置 | 说明 |
|---|---|---|
| `WzModal` / `PageOverlayHost` | **保留自研**（`AGENTS.md §7.3-①`/5 的硬约束资产） | 遮罩缺陷 DEF-56 在同批修（遮罩改 `pointerInput`，不再可聚焦/不再吃空格） |
| `AdaptiveTable`（D33） | **保留自研** | ✅ 已核实：M3/Jewel/Carbon/Fluent **全都没有 DataTable**，自研是必然 |
| `WzButton` | 换 M3 `Button`/`FilledTonalButton`/`OutlinedButton`/`TextButton` | 保留 `WzButtonVariant` 三个变体作为薄封装，保住调用点 |
| `WzTextField` | 换 M3 `OutlinedTextField`，数值框迁 `TextFieldState` + `InputTransformation` | 同时落地 DEF-54（粘贴清洗）|
| `WzSelect` | 换 M3 `ExposedDropdownMenu` | — |
| 日期时间 | 新增组件：M3 `DatePicker`/`TimePicker` **内联**塞进 `WzModal` | 落地 DEF-55；**禁用** `DatePickerDialog`（其 skiko 实现走 `Dialog`，且仍需 `@OptIn`） |
| `WzToast` | 评估换 M3 `Snackbar`/`SnackbarHost` | 需保持 3s 自动消失与位置口径 |
| 新增能力 | `SegmentedButton`/`Tooltip`/`ProgressIndicator`/`Chip`/`ListItem`/`Card` 等按需接入 | 缺口清单见调研报告 §1 |

### 2.4 开发流程：接入 Compose Hot Reload（已实施）

`gradle/libs.versions.toml` 增 `compose-hot-reload = "1.2.0"`（与 CMP 1.12 捆绑版本一致），
根脚本 `apply false`、`:app` 应用插件；开发期用 **`./gradlew :app:hotRun`**（JVM 项目任务名；另有
`hotRunAsync`/`hotReloadMain`/`hotMcpServer`）。**只创建开发任务，不进发布产物、不改打包链路**。
JetBrains Runtime 可用 `-Pcompose.reload.jbr.autoProvisioningEnabled=true` 自动下载（实验特性）。

### 2.5 数值输入口径（DEF-54 修法，已实施基础件）

`components/AmountSanitizer.kt`（纯函数、可单测）：

1. **清洗**：NFKC 全角→半角、去各类空白（含 NBSP/窄 NBSP/全角空格）、去货币符号、统一 Unicode 减号；
2. **解析**：规范输入 = 小数点 `.`、千分位 `,`；
3. **拒绝歧义**：`1.234,56`（欧陆格式）、`1,5`、`12,34` 一律返回 `null`（由表单给针对性错误），
   **绝不猜测**——猜错会静默产生 100 倍误差；
4. 输入框侧用 `InputTransformation`（官方语义：**粘贴/拖放同样经过它**，无需拦截剪贴板）；
   ⚠️ 已知坑：**程序化赋值不经过**该变换（`.cpro` 恢复、表单预填须自行再清洗一次）。

## 3. 影响面扫描（`AGENTS.md §8.5`）

| 类别 | 位置 | 说明 |
|---|---|---|
| 代码（主题） | `ui/theme/{Theme,ColorSchemeMapping,TypographyMapping,Shapes,Spacing,Motion}.kt` | 已实施并通过测试 |
| 代码（组件） | `ui/components/*`（11 个）、各页面调用点（含 `gallery/`） | 分批替换，逐批回归 |
| 代码（开发页） | `ui/gallery/M3FrameworkSection.kt`（新增） | 「官方 vs 自绘」并排对照，供电人工目视拍板 |
| 构建 | `gradle/libs.versions.toml`、`build.gradle.kts`、`app/build.gradle.kts` | Hot Reload 接入 |
| 设计侧 | `design-tokens.md`（新增 §4.5 映射表、§4.6 spacing/motion）· `interaction.md`（日期时间选择器交互与异常态）· `ia.md`（新增选择器弹层）· `prototype/wuzhufolio-light.html`（视觉基准，**显式延期**到组件层替换完成后同步） | 见 §4 |
| 技术侧 | 本 ADR · `task-breakdown.md`（**M14 / T14.1–T14.6**）· `docs/tech/dependency-licenses.md`（Hot Reload 为 Apache-2.0，无需新增第三方依赖条目） | — |
| 测试侧 | `ThemeMappingTest`/`AmountSanitizerTest`/`M3FrameworkSectionUiTest`（新增）· `ContrastTest`（扩展到全量 role）· 各页 UI 测试（替换组件时同步） | — |
| 发布侧 | `CHANGELOG.md`（**0.2.0** 节）· `user-guide.md`（日期时间选择器、数值输入口径） | 版本口径见 §5 |

## 4. 需求回溯

| 决策点 | 锚点 |
|---|---|
| 界面一致性 / 组件规范 | PRD §6（界面一致性、无障碍基线）· `design-tokens.md`（唯一视觉真源）· P1 原型（视觉基准） |
| 日期时间选择器 | `interaction.md`（表单交互与异常态）· PRD 故事 4.x（交易录入）· DEF-55 |
| 数值输入健壮性 | PRD「统一异常处理」（给明确文案，不给笼统失败）· DEF-54 |
| 弹层键盘可用 | `AGENTS.md §7.3`（弹窗一律同窗口就地叠加 + 输入框打开即聚焦 + 验收强制项）· DEF-56 |
| 与移动端一致 | PRD（移动端已定 Material 3）· `跨端共享规范` |

## 5. 版本与阶段口径（人工 2026-09-28 提问的答复，随本 ADR 固化）

1. **P7 尚未关闭**：v0.1.1 已发布（2026-09-25 人工复验通过），但 `STATUS.md` 中 **P7 仍为「进行中」**，
   需人工一句话关闭（可顺带勾选：jlink 裁剪/字体子集化维持现状、Linux 开机自启补测是否要做）。
2. **大的界面体系改动进 0.2.0**：属 minor 版本（0.x 语义下可含视觉/交互的破坏性变化）。
   **DEF-54/55/56 不再单独出 0.1.2**——按人工「先改大问题」的指令，**随本改造一并吸收**
   （三者恰好都落在组件层：数值输入框、日期时间组件、`WzModal` 遮罩）。
3. **推荐路径**：**关闭 P7 → 进 P8 复盘（`retrospective.md`）+ 定下一迭代范围 → 0.2.0 按 C2 mini 闭环实施**
   （需求增量 = 0.1.1 反馈；设计增量 = design-tokens §4.5/§4.6 + interaction；技术 = 本 ADR；实施 = M14/T14.x）→ P6 复测 → P7 发布 0.2.0。
4. **不建议**：把界面体系改造挂在 P7 里做（P7 是「发布」阶段，且 0.1.1 已上线，继续挂会污染发布口径）。

## 6. 风险与缓解

| 风险 | 缓解 |
|---|---|
| M3 组件默认观感偏「Google」 | 全量 token 映射 + Typography/Shapes 下发（已实施）；DEV 对照区供人工目视拍板后再批量替换 |
| 替换组件引入键盘/焦点回归（历史 DEF-13/20/22/25 同源） | 逐批替换 + `AGENTS.md §7.3-③` 强制验收（Compose UI 测试 + 人工键盘复验）；**新增真实按键用例**（`performKeyInput`，`performTextInput` 抓不到 DEF-56 这类缺陷） |
| 表格需自研 | 保留 `AdaptiveTable`（D33）并 token 化；虚拟化性能另立任务实测 |
| `compose.material3` DSL 访问器在 CMP 1.12 已弃用（配置期有我方既有警告） | 后续按 C0 改为显式坐标 `org.jetbrains.compose.material3:material3:1.9.0`（**不要**照抄 CHANGELOG 的 `1.12.0-alpha03`，那是 alpha 线） |
| M3 官方缺陷 | `CMP-10038`（UTC 以西月份标签错一月，Open）、`CMP-10319`（OpenGL 下 TimePicker 配色，Submitted）→ 落地前在 `TZ=America/Denver/Asia/Shanghai` × 两种渲染后端实测 |
| 中文社区/第三方库适配未知 | 不引入（本决策零新增第三方依赖） |

## 7. 已完成 / 待办

**已完成（2026-09-28）**：
- 主题层全量映射（Color + Typography + Shapes）+ `WzSpacing`/`WzMotion` 自建 token；
- `AmountSanitizer`（DEF-54 修法基础件）+ 单测 6 例；
- DEV 组件走查页 **M3 框架对照区**（含数值框粘贴清洗演示与官方日期/时间选择器内联演示）+ UI 测试 2 例；
- Compose Hot Reload 接入（`:app:hotRun` 任务实测存在）；
- 回归：`./gradlew build detekt` 见 `STATUS.md`「本轮实测」。

**待办（按人工「先大后小」排序）**：
1. **T14.3 组件层替换**（`WzButton`/`WzTextField`/`WzSelect` + `WzModal` 遮罩修复 → 一并关闭 DEF-56）；
2. **T14.4 日期时间选择器组件**（→ 关闭 DEF-55）与**数值输入接入 `AmountSanitizer`**（→ 关闭 DEF-54，9 个解析点）；
3. **T14.5 逐页视觉回归**（三档分辨率 × 双主题 + 键盘/IME 强制验收）；
4. **T14.5 原型与文档同步**（`prototype/wuzhufolio-light.html`、`design-tokens`、`interaction`、`ia`）。
