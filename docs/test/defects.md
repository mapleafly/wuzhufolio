# WuZhuFolio P6 缺陷与问题清单（docs/test/defects.md）

> **阶段**：P6 系统测试与质量 · 启动指令：人工「执行P6」（2026-09-14）
> **有效需求基线**：PRD V2.0 + Δ{D21, D24, D25, D26, D27, D28, D29, D30, D31}
> **级别口径**：P0 数据/密钥/启动/主流程阻断 · P1 主要功能错误或验收标准未满足 · P2 次要偏差/体验/可诊断性 · P3 文案细节
> **变更控制**：凡触及已通过模块的行为/数据/接口，均给出 `AGENTS.md §8.1` 分级建议 + 影响面扫描，
> **由人工在 P6 门拍板**（Agent 不自行定级 C1/C2；纯实现偏差按 C0 处理并留痕）。
> **DoD 关系**：P0/P1 必须清零（本清单 P0=0 / P1=0）；P2 必须给出明确处理结论（修复或登记 + 到期检查点）。

---

## 0. 结论汇总

| 级别 | 数量 | 状态 |
|------|------|------|
| **P0** | **1** | **DEF-17**（Windows 跨零点启动被日志轮转竞争打挂）→ **已修复并加回归**，见 §1.6 |
| **P1** | **0** | — |
| **P2** | 6 | **4 项已修复**（DEF-01/02/03/06）· **2 项已按人工裁决处置**（DEF-04 登记 P8；DEF-05 按 C0 文档澄清并已回写） |
| **P1（人工门新增）** | 3 | **均已修复**：**DEF-13**（Tab 焦点链重复目标 → 页面内容键盘不可达）、**DEF-15**（Windows 托盘菜单中文乱码）、**DEF-20**（表单候选选中后焦点掉出弹窗）——见 §1.5/§1.7 |
| **P2（人工门新增）** | 4 | **均已修复**：**DEF-14**（登录页回车不提交）、**DEF-18**（托盘菜单不随界面语言）、**DEF-19**（托盘菜单不随语言**即时**切换，需重启）、**DEF-21**（走查提案 A：焦点入页面 + 外壳退出键）；另 **DEF-16** 为口径确认（非缺陷） |
| 测试缺陷（CI 暴露） | 1 | **DEF-12** 已修复（见 §3） |
| **P3 / 观察项** | 6 | 登记（DEF-07…DEF-12），详见 §3 |
| 合计 | 20 | P0 曾出现 1 项（DEF-17）· P1 曾出现 3 项（DEF-13/15/20）——**均已修复闭环**（人工门实测暴露）；P2 全部有明确结论 ✅ |

> 结论：**P0 = 0**；**P1 三项（DEF-13/DEF-15/DEF-20）由人工门实测暴露并已修复闭环**（修复即回归，见 §1.5/§1.7），
> 当前无未修复 P1；P2 各项在人工 P6 门全部裁决完毕或已登记（见 §0.1），**无遗留未决项**。
> 2026-09-15 第四轮 Windows 人工门新增 **DEF-20（P1，已修复）** 与 **DEF-21（P2，焦点流改进落地，分级待裁决）**。

### 0.1 人工裁决记录（2026-09-14 · 原话「裁决：5项都按建议来处理」）

| # | 裁决事项 | 人工裁决 | 落地状态 |
|---|----------|----------|----------|
| ① | 行情请求币种集合隐私最小化 | **接受现状 + 记入用户指南/隐私声明** | ✅ 已登记为 **P7 用户指南/隐私声明** 内容项（见 `test-report.md §5.3/§6`、STATUS P7 携带项）；评估结论与量化数据见 `test-report.md §5.3` |
| ② | DEF-03 币种详情缺「时间」筛选 | **本轮补做（C1 mini 闭环）** | ✅ **已实施**：决策档 `docs/dev/decisions/D30-币种详情时间筛选.md` + 台账 D30 行 + 索引 + task-breakdown **T12.5** + `ia.md §2.6` + 原型/verify 同步 + 代码与 UI 回归（详见 §2 DEF-03） |
| ③ | DEF-04 CMC 兜底计入 CG 额度账本 | **登记 P8** | ✅ 已登记（P8 立项输入：账本增 provider 维度 + 旧载荷兼容；影响面见 §2 DEF-04） |
| ④ | DEF-05 interaction「列表滚动加载」口径 | **按 C0 文档澄清** | ✅ **已回写**：`interaction.md §2.1` 增「列表装载口径」注 + §3-2 措辞订正（本地库单次装载 + `LazyColumn` 虚拟化，不适用分页）；大数据量装载耗时登记 P8 观察项 |
| ⑤ | DEF-01 / DEF-02 / DEF-06 定级 | **维持 C0** | ✅ 已确认（三项均为实现偏差/失败模式补全，未改产品语义、未改数据模型与格式；回写见各自条目） |
| ⑥ | **第四轮人工门两项焦点问题定级**（2026-09-15） | **按建议变更分级**（原话）→ **DEF-20 = C0**、**DEF-21 = C1** | ✅ **DEF-20**：C0 勘误（模块记录 `M7.md`/`M8.md` §勘误 + 交互文档回写，不建档不进台账）；**DEF-21**：C1 完整落盘 —— 决策档 **D31** + 台账 D31 行（有效需求串 `Δ{…, D30, D31}`）+ 决策索引 + `task-breakdown **T12.6**` + `ia.md §1.1` + `interaction.md §3-9` + `M12.md §1.7`；验收标准 A1–A6 见 D31 §6 |

---

---

## 1. 本轮修复（C0 实现偏差 / 失败模式补全）

### DEF-01 ✅ 已修复（建议 C0）· 备份导出遇到不可解密 `api_keys` 密文时抛原始加密异常

| 项 | 内容 |
|----|------|
| **来源** | P5 登记转 P6（`integration-report.md` P5-4：备份导出失败模式） |
| **现象** | 库内 `api_keys` 凭证列与当前账户 DEK/AAD 不匹配时，`exportBackup` 直接上浮 `AuthenticationFailedException: AES-GCM authentication failed`（无类型化错误、无用户可读文案）；若走**全量覆盖恢复**，该异常从「覆盖前临时备份」路径冒出，UI 只显示「恢复失败：api_keys credential unreadable…」这类内部英文信息 |
| **触发面** | DB 被外部改动 / 位翻转损坏 / 跨库误拷。正常路径不可能出现（明文永不落盘、密文只由本服务写入） |
| **根因** | 导出侧**没有定义任何错误面**（`BackupService` 只声明了导入侧 `CproDecodeException` 三态），`apiKeyRowsToPayload` 未包类型化边界 |
| **处置** | ① 新增 `domain/backup/BackupExportException`（`Reason.CREDENTIAL_UNREADABLE` + `keyName`）；② `DefaultBackupService.apiKeyRowsToPayload` 捕获 `AuthenticationFailedException` / `IllegalArgumentException` → 类型化上浮（**导出整体中止、不落文件**）；③ UI：`BackupCopy.exportErrorCopy`（导出）/ `restoreErrorCopy`（恢复）→ 中英双档整句；④ `interaction.md §2.9` 补异常态表；⑤ `api-contracts.md` §3 M9 补录 + §4 新增错误码 `BACKUP_EXPORT_FAILED` |
| **回归测试** | `DefaultBackupServiceTest::export aborts with typed error when a stored credential cannot be decrypted`（含「不留 .cpro/临时文件」+「错误对象不含密钥材料」断言）<br>`DefaultBackupServiceTest::export aborts when only the passphrase column is unreadable`<br>`DefaultBackupServiceTest::full overwrite aborts before clearing data when the safety backup cannot be created`（**数据完整性**：清库在临时备份之后，失败时业务行数与密钥行数不变）<br>`ui/backup/BackupExportErrorCopyTest`（中英双档含密钥别名、不含原始加密异常文案） |
| **影响面扫描** | 代码：`DefaultBackupService.kt`（导出装配）、`BackupViewModel.kt`（两侧错误映射）、`BackupCopy.kt`、`BackupStrings.kt`（zh/en 各 +2 条）；文档：`interaction.md §2.9`（新增）、`api-contracts.md` §3/§4；测试：上述 4 处。**无数据模型/格式/加密链变更**（失败即不产文件，旧 `.cpro` 兼容性不受影响） |
| **未覆盖边界（登记 DEF-11）** | `keyName = null` 分支（仅当上游遗漏 keyName 时可达）与 `extra` 列损坏未单独造例——同一代码路径已由 passphrase 用例覆盖 |

### DEF-02 ✅ 已修复（建议 C0）· Binance 签名请求未发送 `recvWindow`（文档契约漂移）

| 项 | 内容 |
|----|------|
| **来源** | P6 出站面复核（隐私/契约联合复核） |
| **现象** | `ADR-004 §2` 与 `api-contracts.md §2.1` 明文规定认证 = `X-MBX-APIKEY` + `timestamp` + **`recvWindow`（默认 5000）** + 签名；实现只发 `timestamp`。`ExchangeConfig.RECV_WINDOW_PARAM` / `DEFAULT_RECV_WINDOW` 两个常量**声明后零消费**（死代码 + 契约漂移） |
| **影响** | 行为等价（Binance 未显式传 `recvWindow` 时默认 5000ms），但契约与实现不一致；一旦后续调整该常量不会生效 |
| **处置** | `BinanceAdapter.buildSignedQuery` 显式补发 `recvWindow=5000`（与文档逐字对齐，常量消除死代码）；KDoc 注明 P6 勘误 |
| **回归测试** | `BinanceAdapterTest::validate credentials hits signed account endpoint with headers and signature`（新增 `recvWindow=5000` 断言） |
| **影响面扫描** | 代码：`BinanceAdapter.kt`（1 处签名构造）；文档：无需改（原本即如此规定）；测试：1 处断言。无数据/接口/语义变化 |

### DEF-06 ✅ 已修复（伴随项，建议 C0）· 恢复向导未映射导出侧类型化错误

| 项 | 内容 |
|----|------|
| **来源** | P6 代码勘查（DEF-01 修复后的遗留缺口 G1） |
| **现象** | `BackupViewModel.decodeCopy` 只识别 `CproDecodeException`；全量覆盖路径的临时备份失败会落到「恢复失败：+ 原始英文信息」分支 |
| **处置** | 抽出 `BackupCopy.restoreErrorCopy(t)`（导入三态 + `BackupExportException` → 恢复语境整句），`decodeCopy` 委托之；配套测试见 DEF-01 |
| **影响面** | `BackupCopy.kt` / `BackupViewModel.kt` / `BackupStrings.kt`；无契约变化 |

---

### DEF-13 ✅ 已修复（**P1** · 人工门实测暴露 · 建议 C0）· Tab 焦点链重复目标导致页面内容键盘不可达

| 项 | 内容 |
|----|------|
| **来源** | P6 人工门 Windows 11 走查（2026-09-14）：①「按 Tab 只能在左侧功能项移动焦点」；②「进入资金页后无法用键盘执行增资，焦点进不到页面组件」 |
| **复现** | Compose UI 探针（`:ui:test`）实测序列 = `nav-DASHBOARD → … → nav-GALLERY → theme-toggle → (无焦点) → …`：页面内按钮（`dashboard-refresh` / `fund-add-deposit`）**完全不可达** |
| **根因** | `WzButton`/`WzSelect` 同时挂了 `clickable`（自身即焦点目标）与**显式 `.focusable()`** → **同一节点两个焦点目标**；`WzModal`/认证弹层卡片同样如此（`clickable` 吞点击 + `focusable` 承接 Esc）。Tab 会在「有语义、有焦点环的目标」与「无标识的隐形目标」之间交替，隐形目标上按 Enter/Space 无任何反应 → 用户感知为「焦点动不了 / 按键没反应」 |
| **修复** | ① `WzButton`/`WzSelect` 去掉重复的显式 `.focusable()`（保留 `clickable` 自带焦点与 `onFocusChanged` 焦点环）；② `WzModal`/`GateWidgets` 卡片把「吞点击」的 `clickable` 换成 `pointerInput { detectTapGestures {} }`（不产生焦点目标），保留唯一 `focusable()` 承接无输入框弹窗的 Esc，并显式 `semantics(mergeDescendants = true)` 维持原有语义边界（4 个弹层用例靠它取节点） |
| **回归** | 新增 `ui/KeyboardA11yUiTest`：**Tab 每一步必须恰好一个可聚焦且带标识的节点**（隐形目标会让断言红）+ 页面内按钮可达（`probe-funds-btn`/`probe-withdraw-btn`）+ 侧边栏/顶栏仍可达；修复后实测序列 = `nav-* → topbar-refresh-quotes → topbar-sync → theme-toggle → 页面按钮 → 循环`，**无空焦点步进** |
| **影响面扫描** | 代码：`WzButton`/`WzSelect`/`WzModal`/`GateWidgets`（4 文件，仅焦点/指针修饰链）；不涉数据、接口、schema、备份格式；既有 UI 测试全量复跑绿（其中 4 个弹层用例因语义边界写法变化同步暴露并已修复） |
| **PRD 回溯** | PRD §6「无障碍基线：桌面端支持全键盘导航（Tab 焦点顺序合理、核心操作可达）」；`interaction.md §3-9` |

### DEF-14 ✅ 已修复（P2 · 建议 C0）· 登录页输入密码后回车不提交

| 项 | 内容 |
|----|------|
| **来源** | P6 人工门 Windows 走查：「填完密码按回车没反应，需 Tab 到『登录』按钮再回车」 |
| **根因** | 登录表单只在按钮 `onClick` 里做提交，输入框未接 Enter 通路（桌面端物理回车不触发 IME action） |
| **修复** | `WzTextField` 新增 `onSubmit`：物理回车走 `onPreviewKeyEvent`（`Enter`/`NumPadEnter`），并同时声明 `ImeAction.Done` + `KeyboardActions(onDone)`（软键盘/无障碍路径一致）；登录页把提交逻辑抽为局部函数，密码框回车与按钮**共用同一路径**（空密码回车 → 内联错误，不提交） |
| **回归** | `ui/KeyboardA11yUiTest`：`enter in password field submits the login form`（断言提交参数三元组）+ `enter with empty password shows the inline error instead of submitting` |
| **影响面** | `WzTextField`（新增可选参数，既有调用点零改动）/ `GatePages` 登录页；不涉数据与接口 |

### DEF-15 🔁 **二次修复后仍复现 → 三次修复（Skia 自绘菜单）**（**P1** · 人工门实测 · 建议 C0）· Windows 托盘菜单中文乱码

| 项 | 内容 |
|----|------|
| **来源** | P6 人工门 Windows 11 走查：「托盘有三行菜单，文字全是乱码」 |
| **根因** | Compose Desktop 的 `Tray` 用 **AWT `PopupMenu`/`MenuItem`** 承载菜单（`ui-desktop-1.12.0.jar` 的 `Tray_desktopKt` 反汇编实证：`java.awt.SystemTray` + `java.awt.PopupMenu`），菜单文字因此**不由应用内嵌字体渲染**，而由目标机 AWT 逻辑字体交给系统绘制——该路径缺 CJK 覆盖即乱码；且 Compose 的 `Item(text)` 无法注入字体。附带缺陷：三个菜单项**硬编码中文**，英文界面下不跟随 |
| **修复（第 2 版，2026-09-14）** | 自建 AWT 托盘宿主 + 每个 `MenuItem` 显式挂**内嵌 Noto Sans SC**（`TrayFont`，`canDisplayUpTo` 校验覆盖）+ 文案入 i18n + 通知改 `displayMessage` |
| **第 2 版结果** | ❌ **人工复验仍乱码** → **推翻字体假设**：不是「系统缺字形」，而是 **AWT 菜单文本的渲染/转码路径本身**（Windows 上由 AWT→native 菜单绘制，应用无法干预）。这也解释了为何换字体无效 |
| **修复（第 3 版，2026-09-15）** | **彻底绕开 AWT 文本**：AWT 只负责**托盘图标与点击事件**（图像/坐标与文本无关），右键回调屏幕坐标 → 由 **Compose/Skia 自绘菜单窗口**渲染三项（`ui/tray/TrayMenuContent` + `app/tray/TrayMenuWindow`）。字体/渲染链与应用界面完全一致（界面中文已实证正常）。交互贴合原生：无边框置顶、**失焦即关**、Esc 关闭、点选执行并关闭；菜单容器自取焦点保证 Esc 可达。`TrayFont`（AWT 字体方案）随第 2 版一并删除 |
| **回归（第 3 版）** | `ui/tray/TrayMenuContentUiTest`（3 项）：三项按当前语言渲染 / 点选各自触发动作并关闭 / Esc 关闭；字体链路 = 应用同一 Compose 主题（界面中文正常即此路径可信） |
| **构建标识（配套）** | 人工反馈需能确认「跑的是哪一版」：`BuildInfo.COMMIT` 由构建期注入 git short SHA，启动日志首行输出 `bootstrap ok \| build=0.1.0+<sha> \| …`（`AppBootstrap`）——复验时请以此确认已装新版 |
| **待办** | 本机（WSLg）无系统托盘，**无法目视复验** → 请人工用**新构建**重走 TC-MAN-01：① 托盘右键三项为可读中文；② 切换 English 后为英文；③ 三项动作分别生效；④ Esc/点别处可关闭菜单。若仍乱码，请提供截图 + 启动日志 `build=` 行 + Windows 显示语言/区域设置（届时可判定为更深层的系统级文本路径问题） |
| **影响面** | 代码：新增 `app/tray/AwtTrayHost.kt`、`app/tray/TrayFont.kt`，`AppHost.kt` 托盘装配与通知路径改写，`ui/i18n/ShellStrings.kt` +3 键 ×2 档；不涉数据/接口/schema；托盘能力探测与降级口径不变 |

### DEF-18 ✅ 已修复（P2 · 人工门二轮复验暴露 · 建议 C0）· 托盘菜单不随界面语言切换（重启亦不变）

| 项 | 内容 |
|----|------|
| **现象** | 托盘菜单改自绘后中文正常，但**界面切英文后托盘仍为中文，重启后仍是中文** |
| **根因** | `WuzhuTheme` 会把**全局** `I18n` 设成它收到的 `language` 参数（默认 `AppLanguage.ZH`）。托盘菜单是**独立窗口**，其主题调用未传 language → 每次打开菜单都把全局语言重置为中文；而菜单文案又通过全局读取器 `shellStrings` 取值 → **恒为中文**（与「重启不变」一致：菜单窗口每次都把自己置回 ZH） |
| **修复** | ① `ui/tray/TrayLabels.kt` 新增 `trayLabels(language)`：按 `AppLanguage` **显式**从 `ShellStringsZh/En` 取词，不依赖全局状态；② `TrayMenuWindow` 新增 `language` 参数，主题（`WuzhuTheme(themeMode, language)`）与文案用**同一语言**；③ `AppHost` 传入 `runtime.uiState.language`（与主界面同源） |
| **回归** | `ui/tray/TrayLabelsTest`（2 例）：**全局 I18n 被重置为中文时，按 EN 取词仍须英文**（正是本缺陷的复现条件）+ 反向（全局英文时按 ZH 取词仍中文）；`TrayMenuContentUiTest` 改用 `trayLabels` 取词 |
| **影响面** | 代码：`ui/tray/TrayLabels.kt`（新增）、`app/tray/TrayMenuWindow.kt`、`app/AppHost.kt`；app 模块原先的 `TrayLabels` 定义移入 ui 模块；不涉数据/接口/schema |
| **教训** | 凡**独立窗口/独立组合树**，不得依赖「由主题设置的全局状态」取值；语言、精度等全局读取器必须由调用方显式传入。同类风险点：今后若新增二级窗口（如独立面板），沿用同一口径 |

### DEF-19 ✅ 已修复（P2 · 人工门三轮复验暴露 · C0 实现补全）· 托盘菜单不随语言**即时**切换（切英文后要重启才变）

| 项 | 内容 |
|----|------|
| **现象** | DEF-18 修复后：切 English → 托盘菜单仍中文；**重启后变英文**。再切回中文 → 托盘仍英文，**再重启才变中文** |
| **根因** | 菜单读的是 `Runtime.uiState`——它是**启动时快照**（构造后不再变化）。`ShellViewModel` 的语言切换只做两件事：更新自己持有的状态 + 通过 `onShellPreferenceChange` 写 settings；主壳之外的组件（托盘菜单窗口）**没有任何可观察来源**，只能看到启动值 → 重启才生效 |
| **修复** | 新增 `app/UiPreferenceState`：把主题/盈亏配色/语言做成 `StateFlow<UiState>`（键与 `ShellViewModel` 持久化键一致 `theme`/`pnl_scheme`/`locale`，非法值/无关键忽略不抛）；`Runtime` 暴露 `uiPreferences`；`Main.onShellPreferenceChange` 在写库后同步调用 `apply(key, value)`；`AppHost` 用 `collectAsState()` 订阅并把 `theme`/`language` 传给托盘菜单窗口 → **菜单即时跟随**（主题同样即时，不再等重启） |
| **回归** | `app/UiPreferenceStateTest`（3 例）：语言键即时生效（切英/切回中）、主题与盈亏配色键生效、无关键与非法值不影响状态 |
| **影响面** | 代码：新增 `app/UiPreferenceState.kt`、`AppBootstrap.Runtime`（+1 字段与新构造）、`Main.kt`（偏好钩子 +2 行）、`AppHost.kt`（订阅 + 传参）；不涉数据/接口/schema；`Runtime.uiState` 保留为「主壳初始值」语义 |
| **口径沉淀** | 与 DEF-18 同源：**跨组合树共享的界面偏好必须有可观察状态**（快照只可用于「初始值」）。后续新增二级窗口/托盘类组件一律订阅 `uiPreferences` |

### DEF-20 ✅ 已修复（**P1** · 人工门四轮实测暴露 · 建议 C0）· 表单候选选中后焦点掉出弹窗（下次 Tab 从侧边栏重来）

| 项 | 内容 |
|----|------|
| **现象** | 「记录增资」的**币种**候选、「添加交易」的**交易对**候选，用键盘选中（Tab 到候选行 + Enter）后候选行消失，**焦点掉出弹窗**；下一次 Tab 从**侧边栏第一项**重新开始，键盘用户被迫重走整条路径（交易表单里甚至要重走 3 次：基础币、计价币、自定义手续费币种） |
| **复现** | 纯键盘：弹窗内输入 `USDT` → 候选出现 → Tab 到候选行 → Enter；观察焦点框消失、再按 Tab 落在侧边栏 |
| **根因** | 候选行由 `clickable` 提供**唯一焦点目标**；选中后该行立即从组合中移除（`candidates = emptyList()`），Compose 焦点系统**无处可恢复**（触发候选的输入框并未保存/恢复焦点）→ 焦点回落到窗口根，即 Tab 序第一个节点（侧边栏首项） |
| **修复** | ① `FundFormModal`：币种候选选中 → 焦点交「数量」（`qtyFocus`）；② `TransactionFormModal`：基础币候选 → 「计价币」（`quoteFocus`）、计价币候选 → 「价格」（`priceFocus`）、自定义手续费币种 → 原字段（`feeFocus`）；③ `WzSelect`：下拉候选选中 → 焦点收回触发框（`triggerFocus`），避免同类「浮层选项消失」路径再次掉焦点；④ 交易候选行补 `tx-suggestion-<id>` 标签（与资金页 `fund-suggestion-<id>` 同口径），供自动化与人工走查定位 |
| **回归** | `FundsPageUiTest::coinPickHandsFocusToQuantityFieldInsideModal`（选中 → 数量聚焦 → 继续 Tab 到「日期时间」仍在弹窗内）<br>`TransactionsPageUiTest::pickingBaseCandidateHandsFocusToQuoteField`（基础币 → 计价币 → Tab 到价格）<br>`TransactionsPageUiTest::pickingQuoteCandidateHandsFocusToPriceField`（计价币 → 价格） |
| **影响面扫描** | 代码：`ui/ledger/FundFormModal.kt`、`ui/ledger/TransactionFormModal.kt`、`ui/components/WzSelect.kt`；测试：上述 3 例 + 新增候选行标签。**不涉数据模型/schema/加密/接口/持久化格式**（纯焦点编排） |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（实现偏差纠正：`AGENTS.md §7.3` 要求弹窗键盘可用，候选消失导致焦点链断裂属实现未达约束；不改产品语义、不新增需求）。模块勘误：`M7.md`/`M8.md` §勘误；不建决策档、不进台账（`AGENTS.md §8.1`） |

### DEF-21 ✅ 已实施（**P2** · 走查提案 A 落地 · 人工拍板 2026-09-15 **C1**）· 焦点流：回车进页面内容 + 外壳退出键

| 项 | 内容 |
|----|------|
| **来源** | 第四轮 Windows 人工门反馈 + `docs/test/keyboard-walkthrough.md §6 改进提案 A`（人工已给方向：「回车选中后直接进页面内容，并能用方向键/Esc 回到侧边栏/顶栏循环」） |
| **诉求** | ① 回车选中侧边栏项后焦点**直接进入页面内容**，不要再逐个 Tab 穿过侧边栏余项与顶栏；② 焦点在页面内时要有**回到外壳循环**的出口；③ 侧边栏内方向键应能上下移动 |
| **实现** | ① 切页 / 进入币种详情子页 / 对**当前项再次回车** → 焦点交页面内容（页面槽挂 `focusRequester`，Compose 语义：请求挂在**非可聚焦容器**上时焦点落到子树内第一个可聚焦控件；容器**不加** `focusable`，避免多出无焦点环的 Tab 停靠点 = DEF-13 教训）；② 页面内**未被页面控件消费**的 Esc / ↑ / ↓ → 焦点回侧边栏当前项（**冒泡阶段** `onKeyEvent`：输入框方向键/下拉导航等已消费的键不受影响）；③ 侧边栏 ↑/↓ 在导航项间移动（`NAV_FOCUS_ORDER` = 六个一级页 + 组件走查页） |
| **三条护栏** | ① **弹层打开时不接管**：`WzModal` 新增 `WzOverlayRegistry.openModalCount` 计数登记，弹层存续期主壳让出 Esc/方向键（否则焦点会跑到弹层背后，弹层开着而键盘已无法操作）；② **组合键不接管**（Ctrl/Alt/Meta 留给 P8 全局快捷键，提案 B）；③ 无页面内容可聚焦时（如仪表盘只读卡/环形图）请求自然失败，焦点留在侧边栏，不产生报错 |
| **回归** | `ShellFocusFlowUiTest`（5 例）：回车进页面 / Esc 与 ↑ 退回侧边栏 / 侧边栏 ↑↓ 移动 / **弹层打开时退出键不接管**（含 `openModalCount` 打开=1、关闭=0）/ 页面进入不引入隐形焦点停靠点；`KeyboardA11yUiTest` 改为断言**外壳 10 步固定顺序**（侧边栏 7 + 顶栏 3）+ 回车进页面后 Tab 到页面第二个控件 |
| **影响面扫描** | 代码：`ui/shell/MainShell.kt`（焦点编排 + 侧边栏项 `focusRequester`/`onFocusChanged`；helper 拆到新文件以满足 detekt 文件函数上限）、新增 `ui/shell/ShellFocusNavigation.kt`、`ui/components/WzModal.kt`（弹层计数）；文档：`keyboard-walkthrough.md`（键位语义/焦点顺序/走查脚本/判定表/提案状态）、`manual-test-guide.md` TC-MAN-06、`docs/dev/modules/M12.md` §勘误；**不涉数据模型/schema/加密/接口/持久化格式**，不改变页面内容与业务行为（纯焦点编排） |
| **分级（人工拍板 2026-09-15）** | ✅ **C1**：新增焦点行为、不改数据/格式/加密边界、不返工已通过模块的接口（§8.1 红线 1–5 均未命中）。**C1 最小落盘清单已补齐**：决策档 `docs/dev/decisions/D31-键盘焦点流.md`（背景/结论/需求回溯/影响面扫描/验收标准 A1–A6/关联文档）+ `增量台账.md` D31 行与有效需求串 + `决策索引.md` + `task-breakdown **T12.6**` + `ia.md §1.1` + `interaction.md §3-9` + `M12.md §1.7` + STATUS 已决策事项 28 |

### DEF-16 ➖ 非缺陷（口径确认）· 断网后状态栏不是「立即」变为网络断开

| 项 | 内容 |
|----|------|
| **来源** | P6 人工门 Windows 走查：「启动时连通良好显示『直连 同步：空闲』；拔网后短时间内仍显示连接状态，手动刷新行情后才显示『网络断开』；恢复网络后点刷新即恢复」 |
| **核实结论** | **与设计一致**：状态栏断链指示的输入是**最近一次行情刷新的结果**（`ShellStatusViewModel.marketOffline = market?.error is MarketRefreshError.Network`），即「请求失败即提示、保留上次价格与时间戳、点击可重试」（PRD 故事 3.2-3 / `interaction.md §1.1 N1`）。拔网本身不触发探测，故最长需等到下一轮自动刷新（默认 5 分钟，可设 15/30/60）。手动刷新即刻反映，恢复网络后点刷新即回到「直连」，均符合预期 |
| **可选增强（登记 P8）** | 若希望「拔网即刻提示」，可加**轻量连通性探测**（在调度 tick 上做一次 HEAD/连接探测，或监听 OS 网络事件）——属新增行为（PRD 未要求），登记 P8 评估，不在 P6 实施 |
| **手册更新** | `docs/test/manual-test-guide.md` TC-MAN-05 已写明该预期，避免复验时误判为缺陷 |

### DEF-17 ✅ 已修复（**P0** · 人工门实测暴露 · C0 实现健壮性补全）· Windows 跨零点启动失败（日志轮转与 logback 滚动竞争）

| 项 | 内容 |
|----|------|
| **现象** | Windows 11 上应用**启动即失败**：`bootstrap failed / java.nio.file.NoSuchFileException: …\logs\wuzhufolio.2026-09-14.0.log`，栈顶 `LogRotator.rotate`（`Files.getLastModifiedTime`）→ 应用完全起不来 |
| **触发条件** | 启动时刻跨零点（人工日志时间 2026-09-15 00:04）：logback 做**跨日滚动 + maxHistory 清理**，与启动期 `LogRotator.rotate` 的「列举目录 → 逐条 stat」竞争——条目在列举后已被 logback 删除 |
| **根因** | ① `LogRotator` 对单条目 IO 无容错：`Files.list` 与 `getLastModifiedTime` 之间存在 TOCTOU 窗口；② `AppBootstrap` 把**维护性**的轮转失败当成致命错误（`runCatching` 之外）→ 直接终止启动 |
| **修复** | ① `LogRotator` 抽出单条目入口 `rotateEntry`（internal，可测）：任何单条目 IO 失败/条目消失/非普通文件 → `EntryOutcome.Skipped`，**绝不上抛**；② `AppBootstrap` 启动期与运行期（调度 6 小时轮转）两处 `LogRotator.rotate` 都包 `runCatching` + WARN，失败降级为摘要 `files:0/0/0`，**维护性工作不阻断启动**；③ 运行期 `rotateLogsNow` 同步线程化 logger 参数 |
| **回归** | `data/logging/LogRotatorTest` 新增 2 例：`rotate skips entries that vanished after listing`（直接走 `rotateEntry` 模拟竞争窗口；同时验证正常条目仍被裁剪）+ `rotate tolerates unreadable entries`（目录名以 `.log` 结尾等非普通文件） |
| **影响面扫描** | 代码：`data/logging/LogRotator.kt`（单条目容错 + 新 internal 结果类型）、`app/AppBootstrap.kt`（两处调用点 + logger 参数）；不涉数据模型/接口/备份格式；`LogRotationPolicy`（条数/天数口径）不变 |
| **教训** | 「维护性后台任务」与「启动关键路径」必须分离失败语义：前者的任何失败都只能是日志噪声。凡「列举目录再逐条 stat/删除」的代码都要假设**条目随时会消失**（Windows 上尤其明显，文件被占用/删除的语义与 POSIX 不同） |

## 2. 待人工定级 / 登记（P2，不阻断发布）

### DEF-03 ✅ 已修复（人工裁决 2026-09-14：本轮补做 · **C1** · 决策档 D30）

| 项 | 内容 |
|----|------|
| **来源** | P6 用例覆盖扫描（TC-A3.4-4 / TC-F2.2-2） |
| **现象** | PRD 故事 3.4-4 要求「交易记录列表支持按**交易所、交易类型、时间**进行筛选和搜索」；实现为交易所 + 类型 + 搜索三维，**无时间维度** |
| **处置（C1 mini 闭环）** | 币种详情交易记录区新增**时间档位筛选**，复用资金页既有四档口径 `domain/ledger/FundDateRange`（全部时间 / 近 30 天 / 30–90 天 / 90 天以上）与纯规则 `contains(time, now)`；`CoinDetailViewModel` 增 `dateRange` 状态 + `setDateRange()`；`CoinDetailPage` 筛选区增下拉（testTag `coin-filter-date`）；`PortfolioStrings.dateRangeLabel` zh/en 双档。**不新增查询参数、不改 `TransactionLedgerService`/`TxFilter`、无 schema 变化** |
| **DoD（§8.2 五件套）** | ① 决策档 `D30-币种详情时间筛选.md`；② 台账 D30 行 + 决策索引；③ 下游回写：`ia.md §2.6` / `task-breakdown **T12.5**` / 原型 `wuzhufolio-light.html`（时间档位下拉 + `DEMO_NOW`）+ `prototype-verify.js`（`errors=[]`，`coinTxFilters=4`、`coinDateOptions=4`、`coinDateFilteredEmpty=true`、`coinDateResetEmpty=false`）；④ 验收标准进 T12.5；⑤ `M12.md §9` 模块记录 + STATUS 决策号 |
| **回归** | `PortfolioPagesUiTest::coin detail filters transactions by time range`（近 30 天 → 远期行消失；90 天以上 → 只剩远期行）；**去掉过滤实现该用例必红（已实证）** |

### DEF-04 ⬜ 登记 P8（人工裁决 2026-09-14）· CMC 兜底调用计入 CG 月度额度账本

| 项 | 内容 |
|----|------|
| **来源** | P6 隐私/额度复核（TC-X-2.5-4） |
| **现象** | `DefaultMarketRefreshService` 的 CMC 兜底分支与 CMC 目录同步**无条件** `quota.record(...)`（`:182` / `:284`），而计数口径是按 **CoinGecko 个人 Key 月额（10,000）** 计算的百分比（`QuotaPolicy.percentUsed`）；CG 侧调用则只在 `cgKey != null` 时计数（`:156`/`:238`） |
| **影响** | ① 同时配置 CG+CMC Key 时，CMC 调用会抬高「CG 额度已用 %」，**可能提前触发 80% 降档**（降频属用户可感知行为）；② 未配 CG Key 时 CMC 计数被记录但 `quotaPercentUsed()=null`，不展示也不降档（无副作用，但账本含无消费者数据） |
| **分级建议** | **C1**：为账本增加 provider 维度（CMC 独立计数 / `percentUsed` 只统计 CG 类）属**行为修正**，落在已通过模块 M5 → 按 §8.4 由人工定级 |
| **影响面扫描** | `domain/market/QuotaPolicy.kt`（计数维度）· `data/market/SettingsQuotaLedger.kt`（JSON 载荷兼容：新增键需容错读旧载荷）· `DefaultMarketRefreshService.kt`（4 处 record）· `data-model.md §2.3`（`market.quota` 键口径）· `M5.md` 规格裁决 · `test-cases.md` TC-X-2.5-4 |
| **人工裁决与处置** | **登记 P8**（2026-09-14）：本轮**不动代码**（避免在 P6 引入 M5 语义返工）；P8 立项输入 = 账本增 provider 维度（CMC 独立计数 / `percentUsed` 只统计 CG 类）+ 旧 JSON 载荷容错兼容；届时按 C1 走决策档 + 台账。**当前影响仅「同时配置 CG+CMC Key 时可能提前触发 80% 降档」**，不涉及数据正确性与安全 |

### DEF-05 ✅ 已按 C0 口径澄清（人工裁决 2026-09-14）· interaction §2.1「列表滚动加载」在本地库语境不适用

| 项 | 内容 |
|----|------|
| **来源** | P6 用例覆盖扫描（TC-X-2.1-6 / TC-X-3-2） |
| **现象** | `interaction.md §2.1` 列有「列表滚动加载 → 底部 loading 占位」、§3-2「列表滚动加载（资产、交易、资金、币种详情）」；实现为**本地 SQLite 一次装载 + Compose `LazyColumn` 虚拟化渲染**，无分页与 loading 占位 |
| **影响** | 无功能缺失：数据全在本地，读一次比翻页更快；渲染层已虚拟化（仅组合可视项），不因列表长而卡顿。差异仅在**文档措辞**——若未来数据量级导致装载 I/O 变慢，需要另行评估增量读取 |
| **处置（已执行）** | ✅ **C0 文档澄清已回写**：`interaction.md §2.1` 增「列表装载口径」注（本地 SQLite 单次装载 + Compose `LazyColumn` 虚拟化 → 不适用分页与底部 loading 占位）+ §2.1 表格行与 §3-2 措辞订正；「大数据量（>10 万行）装载耗时」登记 **P8 观察项**。不改代码 |
| **影响面** | `docs/design/interaction.md` §2.1/§3-2 措辞；`test-cases.md` 两条用例转「设计澄清」 |

---

## 3. P3 / 观察项（登记，不影响发布）

| # | 事项 | 性质 | 处置 |
|---|------|------|------|
| DEF-07 | 导出时目录缺失的币种行被 `mapNotNull` 静默跳过（仅 debug 语义，无计数回传） | 可观测性 | 现状：`coins` 表不删行，理论不触发；登记 P8（若将来支持删币，需在导出摘要里报数） |
| DEF-08 | `backupMetadata()` 读失败被 `runCatching{}.getOrNull()` 吞掉 → UI 显示「从未备份」而非错误 | 可观测性 | 登记 P8（读取失败极罕见；错误方向不危险——不会误报成备份成功） |
| DEF-09 | `BackgroundScheduler.marketLoop` 的 `onTick()` 在 `runCatching` 之外 | 健壮性 | 当前 lambda 不抛（`proxyRuntime.refresh()`）；登记 P8 一并纳入异常隔离 |
| DEF-10 | 英文界面下，服务层硬编码中文异常文案仍可能经 `t.message` 直达 UI（如备份密码强度：UI 侧已本地校验，属第二道防线） | i18n 完整性 | 本轮已收敛导出/恢复两条主要路径（DEF-01/06）；其余路径登记 P8 统一为类型化错误码 |
| DEF-11 | DEF-01 的 `keyName = null` 分支与 `extra` 列坏值无独立用例 | 测试完整性 | 同一代码路径已被 passphrase 用例覆盖；登记 P8 补齐 |
| **DEF-12** | ✅ **已修复（2026-09-14，CI 三平台复跑暴露，测试缺陷）** `SettingsKeyNamespaceGuardTest` 的符号索引**依赖文件遍历顺序**：早期实现用 `HashMap<裸名, 表达式>`，同名常量（`AppLanguage.SETTINGS_KEY="locale"` 与 `MarketWatchService.SETTINGS_KEY="watch.coins"`）互相覆盖 → **限定名 `MarketWatchService.SETTINGS_KEY` 未解析**，Windows（NTFS 目录顺序）上 `watch.coins` 丢失而 ubuntu/macos 通过。**修复** = 改为「限定名 → 表达式集合」索引（限定名唯一命中即用；裸名要求跨全部限定符唯一，否则 fail-closed）+ 限定符跟踪覆盖 `interface`/`enum class`/`data class` 等全部类型声明。**验证** = 本地把文件遍历顺序反转为降序后复跑仍绿（顺序无关性实证）；CI 复跑见 `test-report.md`/STATUS「CI 留痕」 | 测试基础设施 | 已修复并已推送复跑；教训：凡「跨文件符号解析」的守护测试必须与遍历顺序无关，且**限定名优先于裸名** |

---

## 4. 复跑命令（供人工复核缺陷修复）

```bash
export JAVA_HOME=$(mise where java)

# 全量（含本轮全部新增回归）
./gradlew clean build detekt --no-build-cache

# DEF-01/06（备份导出失败模式 + 恢复不清库）
./gradlew :data:test --tests "com.wuzhufolio.data.backup.DefaultBackupServiceTest" \
                     --tests "com.wuzhufolio.data.backup.BackupBoundaryGuardTest"
./gradlew :ui:test   --tests "com.wuzhufolio.ui.backup.*"

# DEF-02（recvWindow 契约）
./gradlew :data:test --tests "com.wuzhufolio.data.exchange.BinanceAdapterTest"

# DEF-20（候选选中后焦点交接）· DEF-21（焦点流：进页面 / 回到外壳）
./gradlew :ui:test --tests "com.wuzhufolio.ui.ledger.FundsPageUiTest" \
                   --tests "com.wuzhufolio.ui.ledger.TransactionsPageUiTest" \
                   --tests "com.wuzhufolio.ui.shell.ShellFocusFlowUiTest" \
                   --tests "com.wuzhufolio.ui.KeyboardA11yUiTest"
```

---

## 5. 需求回溯

| 缺陷 | 需求锚点 |
|------|----------|
| DEF-01 / DEF-06 | PRD 全局说明「统一异常处理」（清晰错误提示，不显示泛化的「请求失败」）、故事 5.2、`api-contracts.md §4` |
| DEF-02 | ADR-004 §2、`api-contracts.md §2.1`（认证参数契约） |
| DEF-03 | PRD 故事 3.4 验收 4（交易所/类型/时间筛选与搜索） |
| DEF-04 | PRD 故事 3.2 验收 6、共享规范「额度治理」、ADR-003 §4 |
| DEF-05 | `docs/design/interaction.md` §2.1/§3-2 |
| DEF-07…DEF-11 | P5 交接项、M13 安全清单 §7、P6 勘查观察项 |
| DEF-12 | CI 三平台一致性（`SettingsKeyNamespaceGuardTest` 符号索引）；见 §3 |
| DEF-13 / DEF-14 / DEF-20 | PRD §6「无障碍基线：桌面端支持全键盘导航（Tab 焦点顺序合理、核心操作可达）」；`AGENTS.md §7.3` GUI 共性约束（弹窗打开即聚焦、键盘可用为验收强制项） |
| DEF-15 / DEF-18 / DEF-19 | 设计规范「托盘/通知规范」（`design-tokens.md`）、`interaction.md` 语言切换即时生效条款 |
| DEF-16 | PRD 故事 3.2-3、`interaction.md §1.1 N1`（失败即提示、保留上次价格） |
| DEF-17 | PRD「启动可靠性」（应用可启动为前提）；`AGENTS.md §1.1` 本地数据约束下的日志维护 |
| DEF-21 | PRD §6 无障碍基线；`docs/design/ia.md` 导航条款、`interaction.md` 键盘交互；`keyboard-walkthrough.md §6 提案 A` |
