# WuZhuFolio 视觉与组件规范（design-tokens.md）

> P1 产物 · **仅桌面端** · 移动端 Material 3 归入下一版本（AGENTS.md §4 P1）。
> **本文档是视觉基准的规范源（唯一判据）**，也是 P4/P6 模块 UI 的 token 来源。
> **D42（2026-09-29 人工拍板）**：P1 的 `prototype/*.html` **已退役**（不再作视觉基准）；
> 基准改为「**本文档（规范）+ [`baseline/`](baseline/) 真实渲染截图（实况）**」，
> 说明与再生成方式见 [`visual-baseline.md`](visual-baseline.md)。

---

## 1. 设计方向（Design Direction）

> **⚠️ D38 口径（2026-09-28 人工指令，现行）**：**界面尽量采用 Material 3 视觉规范**（参考 <https://m3.material.io/>）——
> 配色/字体/圆角/动效/组件**以 M3 规范为准**（规范数值取自 M3 官方 token 源：`TypeScaleTokens` / `ShapeTokens` / `MotionTokens`）。
> 本文件 §2–§4 已按此重写；**保留的产品身份**只有四项：① 品牌**种子色**（墨绿 `#1F5A48`，由它生成全部 M3 role）；
> ② 字体族（数字用衬线、表格数字用等宽、正文用无衬线）；③ 盈亏/警示**语义扩展色**（M3 无 gain/loss/warn role）；
> ④ 桌面数据密集档（正文取 M3 `bodyMedium` 14sp，而非触屏默认 bodyLarge 16sp）。
> 例外清单（数据表格 / 应用内弹层 / Expressive motion scheme）见 `docs/dev/decisions/D38-全面采用M3视觉规范.md §3`。

- **核心气质**：隐私、本地、可信、数据优先 —— 像一本「私人账本 / 安全控制台」，而非典型加密交易所的霓虹风。
- **反 AI slop 约束**（huashu-design）：禁用激进紫渐变、emoji 图标、圆角卡片+左彩条、GitHub-dark 霓虹偷懒解；用单一 accent 贯穿；数字优先用衬线/等宽而非系统默认。
- **单一真源 + 双主题（2026-08-31 人工确立）**：`docs/design/prototype/wuzhufolio-light.html` 为**唯一原型真源**；暗色主题是其内置档位（`data-theme="dark"`），随深化自动维护（组件一律以 CSS 变量取色；主题切换时环形图即时重渲染）。原变体文件 `wuzhufolio-dark.html` 已删除（避免 P4 误当暗色基准引用）；**P4 暗色视觉基准 = light.html 切至暗色主题的状态**。

---

## 2. 色彩（Color · Material 3）

**配色由算法生成，不手写 hex**：品牌种子色 `#1F5A48`（墨绿）→ HCT 色彩空间 → 色调板（tonal palette）→ M3 语义 role。
生成器 = `scripts/generate-m3-color-scheme.mjs`（Google 官方 `material-color-utilities`，Apache-2.0，**仅生成期使用**），
产物 = `ui/src/main/kotlin/com/wuzhufolio/ui/theme/M3ColorRoles.kt`（**生成物，勿手改**；换色 = 改脚本 `SEED` 重跑）。
**浅色与深色共用同一个种子**——这是 M3 规范口径（深色不是另一套配色，而是同一色调板的不同 tone 档位）。

**生成期自检**：脚本内置 WCAG 计算，正文/语义色对底色低于 4.5:1 时**直接生成失败**（当前 18 项全绿，见生成物头注）。

### 2.1 核心 role（浅色 / 深色）

| M3 role | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `primary` / `onPrimary` | `#166B54` / `#FFFFFF` | `#89D6B9` / `#00382A` | 主操作、选中态（应用强调色） |
| `primaryContainer` / `onPrimaryContainer` | `#A4F2D5` / `#00513E` | `#00513E` / `#A4F2D5` | 低强调容器（Chip、选中行底色） |
| `secondary` / `tertiary` | `#4C635A` / `#406375` | `#B2CCC0` / `#A7CCE1` | 次级/第三强调（M3 自动派生） |
| `surface` | `#F5FBF6` | `#0F1512` | 应用底 |
| `onSurface` / `onSurfaceVariant` | `#171D1A` / `#404944` | `#DEE4DF` / `#BFC9C3` | 主文字 / 次文字 |
| `surfaceContainerLowest → Highest` | `#FFFFFF → #DEE4DF` | `#0A0F0D → #353B38` | **表面色阶**（M3 用容器色阶表达层级，不靠阴影） |
| `outline` / `outlineVariant` | `#707974` / `#BFC9C3` | `#89938E` / `#404944` | 描边 / 分隔线 |
| `error` / `onError` | `#BA1A1A` / `#FFFFFF` | `#FFB4AB` / `#690005` | 错误（**固定**，不随盈亏方案翻转） |
| `inverseSurface` / `inverseOnSurface` | `#2C322F` / `#ECF2ED` | 反色容器（Snackbar） |
| `scrim` | `#000000` | 遮罩保底色（`WzModal` 自绘遮罩另有 28% 口径） |

> 完整 35 个 role 的取值见生成物；本表只列组件最常消费的部分。

### 2.2 语义扩展色（M3 规范之外，产品必需）

| 扩展 role | 浅色 | 深色 | 说明 |
|---|---|---|---|
| `gain`（涨/盈利） | `#1E6B45` | `#8BD6A8` | 按 M3 色调板算法从语义种子派生（浅色 tone 40 / 深色 tone 80） |
| `loss`（跌/亏损） | `#A63835` | `#FFB3AD` | 同上 |
| `warn`（提示/待定价） | `#7C5807` | `#F0BF69` | 同上 |
| `ink3`（三级文字） | `#636D67` | `#89938E` | M3 无对应 role：取中性色调板中**仍满足 AA 4.5:1 的最浅一档**（浅色 tone 45 / 深色 = `outline`） |

### 2.3 盈亏配色方案（PRD §6 无障碍基线）

| 方案 | 上涨/盈利 | 下跌/亏损 |
|------|-----------|-----------|
| 绿涨红跌（默认，国际惯例） | 绿 `gain` | 红 `loss` |
| 红涨绿跌（中文习惯） | 红（= `loss`） | 绿（= `gain`） |
| 色盲友好（蓝涨橙跌） | 蓝 `#3B6FD4` | 橙 `#C77B28` |

> 所有盈亏数值**强制显示 +/- 符号**，颜色仅辅助语义，不作为唯一信息载体。
> **注意（D38）**：`error` role **不参与**该切换（此前 `error = loss`，切红涨绿跌会让表单校验红字变绿——语义错误，已修）。

### 2.4 对比度（WCAG AA）

两重守护：① **生成期自检**（脚本内，18 项组合，低于 4.5:1 直接失败）；
② **运行期回归** `ContrastTest`（两主题：`ink/ink2/ink3/gain/loss/warn/accent` × `bg/surface` 全部 ≥ 4.5:1）。

---

## 3. 字体（Typography · Material 3 type scale）

**字号/行高/字距/字重全部取 M3 规范值**（`TypeScaleTokens`）；**字体族保留产品身份**（M3 规范约束层级而非字族）：

- 数字/指标 → **衬线**（Noto Serif SC）+ `tnum`（账本感 + 数字对齐）；
- 表格数字 → **等宽**（JetBrains Mono，CJK 回退 Noto Sans SC）+ `tnum`；
- 正文/标题/标签 → 无衬线（Noto Sans SC）。

| M3 层级 | size / lineHeight / tracking / weight |
|---|---|
| displayLarge / Medium / Small | 57/64 · 45/52 · 36/44（Regular） |
| headlineLarge / Medium / Small | 32/40 · 28/36 · 24/32（Regular） |
| titleLarge / Medium / Small | 22/28 · 16/24 · 14/20（Medium；tracking 0 / 0.2 / 0.1） |
| bodyLarge / Medium / Small | 16/24 · 14/20 · 12/16（Regular；tracking 0.5 / 0.2 / 0.4） |
| labelLarge / Medium / Small | 14/20 · 12/16 · 11/16（Medium；tracking 0.1 / 0.5 / 0.5） |

**业务语义槽位（`WzTypography`）= M3 层级的投影**（100+ 处调用点使用，不另立字号）：

| 槽位 | 取自 M3 | 用途 |
|---|---|---|
| `display` | displaySmall 36 | 最大号数字 |
| `metricPrimary` | headlineMedium 28 | 卡片一级指标 |
| `metricSecondary` | titleLarge 22 | 卡片次级指标 |
| `pageTitle` | headlineSmall 24 | 页面标题 |
| `sectionTitle` | titleMedium 16 Medium | 分组一级标题（设置页口径，D32） |
| `body` | bodyMedium 14 | 正文/行标签（**桌面数据密集档**） |
| `bodyStrong` | titleSmall 14 Medium | 卡内二级标题、按钮文字、强调正文 |
| `tableNumber` | bodyMedium 14 + 等宽 | 表格数字 |
| `tableHeader` | labelMedium 12 Medium | 表头/标签 |
| `caption` | labelSmall 11 Medium | 说明/时间戳/图例 |

> **层级自证**：卡内二级标题（14）≤ 分组一级标题（16）≤ 页面标题（24）——守护测试
> `SettingsPageUiTest::all settings first level titles share one typography level` 与 `ThemeMappingTest`。

---

## 4. 组件（Components）

### 4.1 布局
- 主壳：左窄侧边栏（约 220px，图标+文字入口）+ 内容区；底部状态栏（约 28px）。
- 间距节奏：4px 基数；卡片内边距 16–20px；区段间距 24px。
- 圆角：卡片 10px、控件 7px（08-31 与实现对齐，原型按钮实际 7px）、窗口 10px（macos_window 提供）。
- 边框：1px `--line`，克制使用（少一层容器/少一个 border）。

### 4.2 控件
- **按钮（D38：内部用官方 M3 `Button`）**：主按钮（`primary` 实底）/ 次按钮（`primary` 描边）/ 危险按钮（`loss` 实底）；
  高度 34dp、圆角 **`Shapes.small` 8dp**、内边距 14/6dp、**无阴影**（`elevation = null`，保持扁平）；
  文字取 M3 `labelLarge`（14/20 Medium）；焦点/悬停/按下由 M3 状态层提供（改造前手绘组件只有两态）。
- **输入框（官方 M3 `OutlinedTextField`）**：描边式，聚焦 `primary` 高亮；错误态 `error` 描边 + 下方错误文案；
  数值输入框启用 `numeric` 即时清洗（口径见 `interaction.md §1.5`）。
- **下拉**：`WzSelect`（M3 `DropdownMenu`）；**日期时间**：`WzDateTimeField`（官方 `DatePicker`/`TimePicker` 内联于 `WzModal`，D38/DEF-55）。
- **开关**：Switch；**下拉**：Select；**日期时间**：Datetime 选择器。
- **表格**：斑马纹可选；表头 sticky；行 hover 高亮；选中行高亮；行内「编辑/删除」文字按钮。
- **Modal**：居中 380–680px 宽（表单类 380–560px；数据密集型如币种详情 680px，08-31 与实现对齐），遮罩半透明，esc/关闭按钮/遮罩点击可关；打开时聚焦首个字段，`role="dialog"` + `aria-modal`。
  **承载位置（DEF-22/23，2026-09-15 确立）**：弹层必须由**页面根**承载并覆盖整页、在整页内居中——
  不得落在**滚动容器内部**（`verticalScroll`/`LazyColumn` 的子项）。滚动容器给子项的高度约束是无限的，
  就地叠加层的 `fillMaxSize()` 会退化为内容高度，弹层随即变成流内块（撑开页面、挤占后续内容、位置随分组漂移）。
  Compose 实现：页面根 `PageOverlayHost(Modifier.fillMaxSize())` + 深层组件 `PageOverlay { WzModal(...) }`
  （见 `ui/components/PageOverlay.kt`）。
- **Toast/提示**：状态栏或右上角轻提示（成功/失败）。

### 4.3 图表
- 资产分布：环形图（Donut），低于小额阈值合计“其他”；扇区/图例**点击**高亮 + 浮窗（币种名称/持有数量/总资产占比/持有量市值，PRD 9.3），hover 高亮；扇区配色随明暗主题即时切换（单一真源内置双主题）。
- 配色按币种区分但整体克制，不用多色聚类（除图例外）；「其他」用中性灰。

### 4.3-1 窄窗口与高 DPI 适配（DEF-28/29/31…38，2026-09-15 人工门第六/七轮确立）

> **总体方案见 `docs/design/responsive-components.md`**（断点 → 组件 → 页面三层：`WzWindowClass` 断点、
> 统一表格 `AdaptiveTable`、统一卡片 `WzCard`/`WzMetric`、弹窗尺寸策略、列表单元线）。本节只记视觉口径。

- **数据表**：列声明**最小宽度**（币种+徽标 240 / 数字 96 / 金额 104 / 短标签 56 / 交易所 84 / 时间 96 / 操作 120，
  见 `ui/components/AdaptiveTable.kt` 的 `TableWidths`）；可用宽度 ≥ 各列之和 → 按权重铺满（宽窗观感不变）；
  否则**整表横向滚动**（底部横向滚动条）。单元格文字一律**单行**（`maxLines = 1, softWrap = false`），
  **不用省略号截断财务数值**——列宽不足时宁可滚动。
- **徽标/标签**：一律单行，且与主文本**内联**（同一行），不得另起一行（会把行高撑高、破坏表格节奏）。
- **按钮**：文案一律单行（`maxLines = 1, softWrap = false`）；按钮组所在行在窄窗**自动换行**（`FlowRow`），
  不得把按钮压到文字宽度以下（竖排文字）。
- 目标窗口：**1280×800**（不出现横向滚动）/ **1024×768**（允许表格横向滚动，行高与列宽保持规整）/
  **2560×1600**（含 Windows 200% 缩放，等效 ~1280dp，按同一断点规则处理）。
- **指标数字**（净值/本金/盈亏）：单行 + 按可用宽度**自动缩字号**（下限 0.68×），绝不换行（DEF-32）。
- **截断数据**：一律提供**悬停显示全值**（`SingleLineText` + 气泡），财务数值不得「截断了也看不到」（DEF-35）。
- **弹窗内容高度上限**：窗口高 × 0.66（下限 320dp）——窄窗下尽量不出现滚动条（DEF-36）。
- **列表单元线**（DEF-38）与**表格网格线**（DEF-40）：表格由 `AdaptiveTable(divider = true, verticalDivider = true)`
  提供横线（行底）+ 纵线（列边界，最后一列右边界不画；表头行只画纵线）；非表格列表用 `rowDivider()`；
  一律 1px `line` 色、最低强调、不加圆角/阴影。

### 4.4 数据呈现
- 数值精度：金额/价格 8 位、市值/盈亏/百分比 2 位；微小价格自动加有效位。
- 无数据统一“--”；「待定价」旁标“估算中”；「持仓异常」行标；「无行情」标。
- 数据源徽章：CoinGecko / CoinMarketCap / 上次成功时间戳。

### 4.5 Material 3 语义位映射（D38：**直接使用 M3 生成的 role**，不再"翻译"手写色）

> **为什么需要这一节**：改造前 `WuzhuTheme` 只把 11 个 color role 喂给 `MaterialTheme`，M3 组件一旦用到
> 未映射的 role（`primaryContainer` / `surfaceContainer*` / `outlineVariant` / `tertiary`…）就回落到
> **Material 默认紫**——于是「不敢用官方组件、只能手绘」，这正是 0.1.1 反馈「组件像手工制作」的根因之一。
> 本表是**唯一映射口径**，实现见 `ui/theme/ColorSchemeMapping.kt`（不写字面色值，只做 §2 token 的语义投影）。

| M3 role | 来源 | 用途 |
|---|---|---|
| `primary` / `onPrimary` | `accent` / `accentInk` | 主操作、选中态、进度 |
| `primaryContainer` / `onPrimaryContainer` | `surface2` / `ink` | 低强调容器（Chip、选中行底色） |
| `inversePrimary` | `accent` | 反色容器上的强调（Snackbar 动作） |
| `secondary` / `onSecondary` | `ink2` / `surface` | 次级控件 |
| `secondaryContainer` / `onSecondaryContainer` | `surface2` / `ink` | 次级容器 |
| `tertiary` / `onTertiary` | `warn` / `surface` | 第三强调（提示、待定价、估算） |
| `tertiaryContainer` / `onTertiaryContainer` | `surface2` / `ink` | 第三强调容器 |
| `background` / `onBackground` | `bg` / `ink` | 窗口底 |
| `surface` / `onSurface` | `surface` / `ink` | 卡片/表格底 |
| `surfaceVariant` / `onSurfaceVariant` | `surface2` / `ink2` | 次表面 |
| `surfaceContainerLowest/Low/ /High/Highest` | `surface` ↔ `surface2` **插值** | 层级改用**容器色阶**（M3 现行口径，不靠阴影） |
| `surfaceBright` / `surfaceDim` | `surface` / `bg` | 亮/暗面 |
| `inverseSurface` / `inverseOnSurface` | `ink` / `surface` | Snackbar 等反色容器 |
| `error` / `onError` | `loss` / `accentInk` | 错误与亏损 |
| `errorContainer` / `onErrorContainer` | `surface2` / `loss` | 错误容器 |
| `outline` / `outlineVariant` | `line` / `line` | 描边与分隔（§4.1「边框克制」不变） |
| `scrim` | 固定 `0x47000000` | 遮罩保底色（`WzModal` 仍用自绘遮罩） |

**字体槽位**（`ui/theme/TypographyMapping.kt`）：M3 的 15 个槽位按语义就近映射到 §3 层级——
`display*` = `display`（衬线 + tnum）· `headlineLarge/Medium` = 卡片一级/次级指标 ·
`headlineSmall`/`titleLarge` = 页面标题 · `titleMedium` = 分组标题 · `titleSmall`/`labelLarge` = `bodyStrong` ·
`bodyLarge`/`bodyMedium` = 正文 **14sp（桌面密度，非 M3 默认 16sp）** · `bodySmall`/`labelMedium`/`labelSmall` = `caption`。

**圆角槽位**（`ui/theme/Shapes.kt`，**M3 规范阶梯**）：`extraSmall` 4dp（徽标/标签）· `small` 8dp（按钮/输入框）·
`medium` 12dp（卡片/弹窗）· `large` 16dp · `extraLarge` 28dp。**全仓 60+ 处硬编码圆角已归一到本阶梯**（D38）。

### 4.6 间距与动效 token（M3 不提供，必须自建）

> M3 只给 color role 与 shape，**没有 spacing token、也没有暴露完整 motion token**。
> 不建这两层，组件里就会各自写 `.padding(13.dp)`、各自用 150/400ms —— 页面之间永远对不齐。

**间距阶梯**（`ui/theme/Spacing.kt`，4 的倍数，桌面紧凑档）：

| token | 值 | 用途 |
|---|---|---|
| `hairline` | 2dp | 描边内缩、图标视觉修正 |
| `xxs` | 4dp | 图标与文字、徽标内边距 |
| `xs` | 6dp | 按钮/输入框内部纵向内边距 |
| `sm` | 8dp | 同级控件之间、表单行之间 |
| `md` | 12dp | 同一行列间距、列表项左右内边距 |
| `lg` | 16dp | 卡片/区块内边距 |
| `xl` | 20dp | 弹窗卡片内边距 |
| `xxl` | 24dp | 页面内区块之间 |
| `xxxl` | 32dp | 页面外边距（宽窗） |

**动效口径**（`ui/theme/Motion.kt`，**M3 motion tokens**）：

| token 组 | 值（ms） | 用途 |
|---|---|---|
| `SHORT_1..4` | 50 / 100 / 150 / **200** | hover / pressed / focus 即时反馈（SHORT_4 最常用） |
| `MEDIUM_1..4` | 250 / **300** / 350 / 400 | 控件状态切换、内容淡入淡出 |
| `LONG_1..4` | 450 / **500** / 550 / 600 | 弹层/页面进出、展开收起 |
| `EXTRA_LONG_1..4` | 700 / 800 / 900 / 1000 | 大范围布局变化 |

曲线（M3）：`emphasized (0.2,0,0,1)` · `emphasizedAccelerate (0.3,0,0.8,0.15)` · `emphasizedDecelerate (0.05,0.7,0.1,1)` ·
`standard (0.2,0,0,1)` · `standardAccelerate (0.3,0,1,1)` · `standardDecelerate (0,0,0,1)`。
**禁止裸写 `tween(200)`**——统一走本对象（否则又是"每处节奏都不一样"）。

> M3 Expressive 的 `MotionScheme` 与 `MaterialTheme(motionScheme=)` 在 material3 **1.9.0 仍为 internal**（实测），
> 故当前不显式指定 motion scheme；上游公开后补一行接线即可。

> **对照区**：开发构建的组件走查页含「Material 3 框架对照区」（`ui/gallery/M3FrameworkSection.kt`），
> 把官方组件与自绘组件放在同一主题下并排展示，供人工目视拍板（进入方式：`./gradlew :app:run -Pwuzhufolio.devUi=true`）。

---

## 5. 托盘与通知（Desktop Tray & Notification）

- 托盘菜单（D36，2026-09-24）：**打开主界面 / 立即同步交易 / 立即刷新行情 / 退出**——
  行情与交易**分列两项**（两类 API 独立，PRD §1.1-4）；「立即同步」文案改为「立即同步交易」以消歧。
  执行后必须有**可见反馈**：开始与结果各一条提示（Linux 用应用内提示窗、Win/mac 用原生气泡），
  不允许「点完没有任何反应」（DEF-49）。
- 关闭窗口默认最小化到托盘（设置可改为直接退出）。
- 桌面通知：同步完成 / 同步失败（可在设置关闭）。
- 备份提醒：距上次备份 > 30 天，在状态栏/通知提示“建议备份”（默认开启）。
- 开机自启（驻留托盘）默认关闭。

---

## 6. 无障碍（Accessibility，PRD 6/T12）

- 盈亏数值强制 +/- 符号；颜色仅辅助。
- 提供「色盲友好（蓝涨橙跌）」配色。
- 全键盘导航：Tab 焦点顺序合理，核心操作可达；焦点可见（`:focus-visible` accent 2px 描边）。
- **原型 a11y 基线（2026-08-31 落地，F5 闭环）**：侧边导航/菜单项/开关/图例行/补全候选项均为 `<button>` 语义（`div[onclick]` = 0）；开关 `role="switch"` + `aria-checked`；资产列表行 `tabindex="0"` + Enter/Space 激活；Modal `role="dialog"` + `aria-modal` + 打开聚焦首字段；Toast `role="status"` + `aria-live="polite"`；13 处 `aria-label`（图标按钮/筛选控件/图表等）。
- 读屏兼容：关键数据提供可读文本标签（aria-label / 语义化）；完整读屏走查（NVDA/JAWS 全流程）在 P4 实现中验证。
- 文本对比度满足 WCAG AA（**两主题分别验证**，见 §2.4）。

---

## 7. 国际化（I18N，PRD 6）

- 语言：初期英文、中文。
- 法币：USD/EUR/CNY 等多法币计价展示。
- 时间：UTC 存储、本地时区显示；格式随语言。
