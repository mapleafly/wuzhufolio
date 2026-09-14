# WuZhuFolio P6 系统测试用例（test-cases.md）

> **阶段**：P6 系统测试与质量（`AGENTS.md §4 P6`）
> **输入**：P5 集成版（`docs/test/integration-report.md`）+ PRD V2.0 验收标准（`docs/prd/桌面端prd.md`）+ 异常态清单（`docs/design/interaction.md`）+ 安全硬约束（`docs/test/security-checklist.md`）
> **有效需求基线**：**PRD V2.0 + Δ{D21, D24, D25, D26, D27, D28, D29}**（`docs/dev/增量台账.md` 表头串；P6/P8/移动端对齐统一引用此串）
> **日期**：2026-09-14 · **编写依据**：全量测试盘点（扫描基线 100 个 `*Test.kt` / 676 `@Test`，逐条 grep + 阅读，未运行 gradle；扫描后新增 `CproLargePayloadTest.kt`、`BackupExportErrorCopyTest.kt` 已并入本文件的 DEF-01 回归项）
> **文档关系**：本文件是 P6 的**用例真源**，与 `test-plan.md`（范围与策略）、`security-checklist.md`（安全逐条核验）、`defects.md`（缺陷台账）、`test-report.md`（结论）配套。

---

## 0. 约定

### 0.1 状态图例

| 图例 | 含义 | 处置 |
|---|---|---|
| ✅ | **自动化通过**：已有自动化断言直接覆盖该验收点 | P6 复跑即可，无需补测 |
| 🟡 | **部分覆盖**：实现已具备，但缺某一层断言（数据层有 UI 无 / UI 有数据层无 / 只覆盖相邻分支） | 补测（注明补哪一层） |
| ⬜ | **未覆盖**：行为缺失或完全无断言 | 必须给出去向：补测 / 人工门 / 登记 P8 / `defects.md` DEF-xx |
| 🔵 | **人工门用例**：需真人真机执行，Agent 不可替代 | 按 §7 步骤执行，人工签字 |

### 0.2 用例 ID 约定

- `TC-A<故事号>.<子号>-<条目号>`：PRD §5 用户故事验收标准（与盘点清单 `C-A*` 一一对应）。
- `TC-F<模块号>-<条目号>` / `TC-F<模块号>.<子号>-<条目号>`：PRD §7.2 核心功能模块（对应 `C-F*`）。
- `TC-X-<状态号>`：interaction.md 异常态（`N1–N3` / `B1–B5` / `A1–A4` / `V1–V9` / `§2.x` / `§3.x`，对应 `C-X*`）。
- `TC-GC-<n>`：PRD 附录 A 黄金用例 n（对应 `C-GC-n`）。
- `TC-UX-<n>`：PRD §6 设计与体验原则（对应 `C-UX-n`）。
- `TC-SEC-<nn>`：P6 安全与隐私专项（对应 `security-checklist.md` 五条硬约束）。
- `TC-MAN-<nn>`：人工门用例（🔵）。
- `TC-X-2.5-4`（P6 新增）：DEF-04 专项用例。

### 0.3 用例来源说明（逐条对应，无遗漏）

| 来源 | 对应章节 | 条数 |
|---|---|---|
| PRD §5 用户故事 1.1–7.2 全部验收标准 | §1 | 109 |
| PRD §7.2 核心功能模块 1–9（含 4.1–4.5 / 6.1–6.4 / 7.1–7.5 / 8.1–8.5 / 9.1–9.4 + P6 新增回归 §2.10） | §2 | 60 |
| interaction §1.1–1.4 异常态（N1–N3 / B1–B5 / A1–A4 / V1–V9） | §3 | 21 |
| interaction §2.1–2.8（加载/空/错误/离线/限流/日志/行情页 D21/持仓异常 D29） | §4.1–4.8 | 41 |
| interaction §3 交互细节要点（11 条） | §4.9 | 11 |
| PRD §6 设计与用户体验原则（含 a11y / i18n / 日志） | §4.10 | 19 |
| PRD 附录 A 黄金用例 1–12 | §5 | 12 |
| `AGENTS.md §1.1` 五条硬约束 + P6 新增抓包与大载荷内存曲线 | §6 | 16 |
| 人工门（托盘/读屏/真实 Key/目标机/GUI 全流程） | §7 | 10 |
| **合计** | | **299** |

### 0.4 结论摘要表

| 分组 | 用例数 | ✅ | 🟡 | ⬜ | 🔵 |
|---|---:|---:|---:|---:|---:|
| §1 PRD §5 用户故事 | 109 | 74 | 33 | 2 | 0 |
| §2 PRD §7.2 核心功能模块 | 60 | 38 | 21 | 0 | 1 |
| §3 interaction §1 异常态 | 21 | 12 | 8 | 1 | 0 |
| §4 interaction §2/§3 + PRD §6 UX | 71 | 40 | 25 | 3 | 3 |
| §5 附录 A 黄金用例 | 12 | 12 | 0 | 0 | 0 |
| §6 安全与隐私专项 | 16 | 14 | 0 | 0 | 2 |
| §7 人工门用例 | 10 | 0 | 0 | 0 | 10 |
| **合计** | **299** | **190** | **87** | **6** | **16** |

> **结论**：计算口径（黄金用例 1–12）与安全硬约束自动化面**全部 ✅**；需补强的三处集中在
> ①表单/列表的 UI 逐项断言（🟡 补 Compose UI）、②异常态与加载态呈现（🟡/⬜）、③需真人真机的 16 条人工门用例（🔵，§7）。
> 行为缺失项共 4 类：DEF-03（币种详情缺时间筛选）、DEF-04（CMC 兜底调用计入 CG 月度额度）、DEF-05（列表滚动加载口径待澄清）、
> 以及 3 项无实现支撑的呈现项（备份/恢复进度条、行情刷新 loading、DB 损坏全屏错误框）——后者登记 P8 或转实现待办。
> DEF-01（备份导出遇不可解密凭证）与 DEF-02（Binance `recvWindow` 契约漂移）**已修复并回归**，对应用例 ✅（TC-F5-3、TC-F4.2-4、TC-SEC-07、TC-SEC-12）。
> 编写期间工作树处于 P6 并发改动中（新增 `ProxyRoutingSmokeTest` + `scripts/outbound-capture-proxy.py`、登出调度加固、V1/V2/V3 边界补测，均已并入本文件的 ✅ 判定）；
> 若后续再有补测落地，以 `test-report.md` 的复跑结果为准更新本表。

---

## §1 功能用例 · PRD §5 用户故事

> 前置共性：自动化用例均在离屏/临时数据目录执行（Compose 用例走 `runComposeUiTest` 离屏渲染；数据层用例走临时 SQLCipher 库），**不需要真实网络与真实交易所凭据**。测试引用记法：`[APP]`=`app/src/test/kotlin/com/wuzhufolio/app/`、`[D]`=`data/src/test/kotlin/com/wuzhufolio/data/`、`[DOM]`=`domain/src/test/kotlin/com/wuzhufolio/domain/`、`[UI]`=`ui/src/test/kotlin/com/wuzhufolio/ui/`。

### 1.1 史诗故事 1：账户创建与登录（1.1）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-A1.1-1 | 故事1.1-① | 首启无账户→引导创建并设密码（二次确认） | 空账户库启动 AuthGate；填用户名+两次密码 | 进入创建页；两次不一致时字段报错并阻止提交 | `[UI]auth/AuthFlowUiTest.kt::first launch without accounts goes to create page`、`::create requires risk confirm hard gate then wizard then shell via later`（引导+双框已覆盖；**「两次不一致」文案 `AuthCopy.CREATE_PW2_ERROR_MISMATCH` 无断言 → 补 Compose UI**） | 🟡 |
| TC-A1.1-2 | 故事1.1-② | 已有账户显示登录界面，凭正确口令进入 | 预置账户后启动；空密码点登录→再输正确密码 | 空密码显示字段错误；正确密码进入主壳 | `[UI]auth/AuthFlowUiTest.kt::login empty password shows field error then login enters shell`；`[D]accounts/DefaultAccountServiceTest.kt::create then login with correct password yields session` | ✅ |
| TC-A1.1-3 | 故事1.1-③ | 密码强度校验（≥8 且含字母与数字） | 依次输入 `short1A`/`abcdefgh`/`12345678`/`password1` | 前三者拒绝、后者通过；强度分档 WEAK/MEDIUM/STRONG | `[DOM]accounts/AccountPolicyTest.kt::minimum gate requires 8 plus letter and digit`、`::strength tiers follow prototype pwScore rules`；`[D]accounts/DefaultAccountServiceTest.kt::duplicate username rejected and weak password rejected at service layer` | ✅ |
| TC-A1.1-4 | 故事1.1-④ | 账户信息（含密码哈希）加密后本地存储 | 建账后直接读库文件字节 + 读 accounts 行 | 库文件无明文；`accounts` 仅存 username/hash/kdf_salt/kdf_params/wrapped_dek | `[D]SqlCipherDatabaseTest.kt::fresh database is encrypted at rest and migrates to latest`；`[D]accounts/AccountRepositoryTest.kt::createWrapped inserts then wraps with real id and roundtrips`；`[DOM]security/KeyWrapTest.kt::wrap unwrap roundtrip restores dek` | ✅ |
| TC-A1.1-5 | 故事1.1-⑤ | 记住我：仅会话令牌入 OS 钥匙串，密码不落盘 | 勾选记住我建账→清内存会话→restoreSession；另测未勾选登录 | 勾选可免密恢复；未勾选清除旧令牌；钥匙串条目不含口令 | `[D]security/KeyringRememberMeStoreTest.kt::save load clear roundtrip through real os keyring`；`[D]accounts/DefaultAccountServiceTest.kt::remember me roundtrip saves restores and logout clears`、`::login without remember clears stale entry`；`[APP]integration/CoreJourneyIntegrationTest.kt::核心旅程 登录 增资 交易 看板ROI 备份恢复` | ✅ |
| TC-A1.1-6 | 故事1.1-⑥ | 创建时必须勾选「我已了解风险」才能完成 | 创建页填表→点创建→风险弹窗不勾选点确认 | 未勾选时确认按钮 `assertIsNotEnabled`；勾选后放行并进向导 | `[UI]auth/AuthFlowUiTest.kt::create requires risk confirm hard gate then wizard then shell via later` | ✅ |
| TC-A1.1-7 | 故事1.1-⑦ | 登录页「忘记密码」入口与不可恢复提示 | 登录页点「忘记密码」→点返回 | 显示 A4 文案；返回登录页 | `[UI]auth/AuthFlowUiTest.kt::forgot page shows A4 copy and returns to login` | ✅ |

### 1.2 史诗故事 1：登出与切换（1.2 / 1.3）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-A1.2-1 | 故事1.2-① | 提供明确的登出/切换账户入口 | 登录后点账户 chip | 弹出账户菜单，含切换/改密/登出 | `[UI]auth/AuthFlowUiTest.kt::account menu switch requires password and logout roundtrip works` | ✅ |
| TC-A1.2-2 | 故事1.2-② | 登出清除会话并回登录界面 | 账户菜单点登出 | 回登录页；`hasRememberMe()` 为 false | `[UI]auth/AuthFlowUiTest.kt::account menu switch requires password and logout roundtrip works`；`[D]accounts/DefaultAccountServiceTest.kt::remember me roundtrip saves restores and logout clears` | ✅ |
| TC-A1.3-1 | 故事1.3-① | 主界面提供「切换账户/管理」入口 | 见 TC-A1.2-1 | 菜单项可达 | `[UI]auth/AuthFlowUiTest.kt::account menu switch requires password and logout roundtrip works` | ✅ |
| TC-A1.3-2 | 故事1.3-② | 列出已创建账户列表 | 建 alpha/beta 两账户→打开菜单 | 两项均列出且可点选 | 同上（`acct-item-2`）；`[D]accounts/DefaultAccountServiceTest.kt::switch requires target password and wipes old session remember entry` | ✅ |
| TC-A1.3-3 | 故事1.3-③ | 选账户后必须输密码验证通过（严格模式） | 选 beta→输错口令→再输正确口令 | 错口令提示「密码错误」不放行；正确后切换成功 | 同上；`[D]accounts/DefaultAccountServiceTest.kt::switch requires target password and wipes old session remember entry`（`PasswordMismatchException`） | ✅ |
| TC-A1.3-4 | 故事1.3-④ | 锁定当前账户数据、加载并解密新账户数据 | 账户 A 建账→账户 B 登录后读账本/本金 | B 看不到 A 的账本与本金（隔离成立） | `[APP]integration/CoreJourneyIntegrationTest.kt::多账户隔离与跨账户恢复`（**「切换瞬间锁定旧账户」无直接断言 → 补 integration**） | 🟡 |
| TC-A1.3-5 | 故事1.3-⑤ | 切换流畅、无需重启 | 同一进程内 切换→改密→重登 | 全流程在同一会话完成，无重启 | `[UI]auth/AuthFlowUiTest.kt::account menu switch requires password and logout roundtrip works` | ✅ |
| TC-A1.3-6 | 故事1.3-⑥ | 清晰的当前账户标识 | 登录后看顶栏/仪表盘 | 显示当前账户名（`acct-chip`、"账户 Alex · …"） | 同上；`[UI]portfolio/PortfolioPagesUiTest.kt::dashboard renders overview cards donut and popup on slice click` | ✅ |

### 1.3 史诗故事 2：手动添加交易 / 增资 / CSV 导入（2.1 / 2.2 / 2.3）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-A2.1-1 | 故事2.1-① | 提供清晰的添加交易表单 | 交易页点「添加交易」 | 弹窗打开、首输入框聚焦、键盘可录入 | `[UI]ledger/TransactionsPageUiTest.kt::emptyStateShowsAddButtonAndEmptyText`、`::addModalFocusesBaseInputAndSavesViaKeyboard` | ✅ |
| TC-A2.1-2 | 故事2.1-② | 字段齐全：平台/交易对/类型/价格/数量/手续费/手续费币种/时间 | 打开表单逐个检查字段 | 8 类字段均可见可编辑 | `[UI]ledger/TransactionsPageUiTest.kt::formShowsTimeAndNotesInputs`（time/notes/total/auto-fee）、`::quoteInputShowsCandidatesAndPicks`（**交易所字段 `tx-exchange-input`、手续费币种三选一 `tx-fee-role-*` 无断言 → 补 Compose UI**） | 🟡 |
| TC-A2.1-3 | 故事2.1-③ | 价格和数量只接受正数输入 | 空表单直接保存；再输入 0 与 −1 | 空值报必填；0/负数报「必须大于 0」并阻止提交 | `[UI]ledger/TransactionsPageUiTest.kt::emptyFormShowsValidationErrorsAndDoesNotTouchService`（空值）、**`::zeroAndNegativeAmountsAreRejectedByV1V2Rules`**（0/−1/−0.5 逐字段红字 + 不触达服务，P6 补测） | ✅ |
| TC-A2.1-4 | 故事2.1-④ | 保存后立即出现在交易历史列表 | 保存一笔买入 | 弹窗关闭、列表出现该行、成功 toast | `[UI]ledger/TransactionsPageUiTest.kt::addModalFocusesBaseInputAndSavesViaKeyboard`（**未断言新增行渲染 → 补 Compose UI**） | 🟡 |
| TC-A2.2-1 | 故事2.2-① | 提供「记录增资」功能 | 资金页点「记录增资」 | 弹窗打开、币种输入框聚焦、键盘可录入、保存成功 | `[UI]ledger/FundsPageUiTest.kt::emptyStateShowsCommandAreaAndOverview`、`::depositModalFocusesCoinInputAndSavesViaKeyboard` | ✅ |
| TC-A2.2-2 | 故事2.2-② | 币种限币/法币拦截/默认稳定币、正数、日期、来源、备注 | 输入 `USD` 保存；空币种保存；检查日期/来源/备注字段 | 法币输入提示改记稳定币；空币种报 V6；日期默认当前可改 | `[D]ledger/DefaultFundServiceTest.kt::fiatInputRejectedWithStablecoinHint`、`::defaultCoinResolvesByWhitelistCgId`；`[UI]ledger/FundsPageUiTest.kt::emptyFormShowsV6ErrorsAndDoesNotTouchService`、`::coinPickCarriesPickedCoinIdToService`（**日期/来源/备注字段无断言 → 补 Compose UI**） | 🟡 |
| TC-A2.2-3 | 故事2.2-③ | 持仓增加（成本=转入市价）、金额计入累计增资与投入本金 | 增资 100 USDT（有快照价 1） | 持仓 +100、可用现金 +100、投入本金 +100 | `[D]ledger/DefaultFundServiceTest.kt::depositWithUsdAnchoredStablecoinSavesAndFeedsOverview`、`::depositUsesRecordTimeSnapshotPrice`；`[DOM]engine/GoldenCasesTest.kt::golden 2 - deposit then withdrawal produces no pnl and net principal returns to zero`；`[APP]integration/CoreJourneyIntegrationTest.kt::核心旅程 登录 增资 交易 看板ROI 备份恢复` | ✅ |
| TC-A2.3-1 | 故事2.3-① | 提供标准 CSV 模板下载 | CSV 弹窗点「下载模板」并选路径 | 文件落盘且内容=模板（含表头与注释） | `[UI]ledger/TransactionsPageUiTest.kt::templateDownloadWritesFileAndToasts`；`[D]ledger/DefaultTransactionLedgerServiceTest.kt::csvTemplateContainsHeaderAndComments` | ✅ |
| TC-A2.3-2 | 故事2.3-② | 提供导入接口上传 CSV | CSV 弹窗点「选择文件」 | 选择器在非 EDT 线程调用；解析后进入预览 | `[UI]ledger/TransactionsPageUiTest.kt::csvWizardParsesPreviewsAndConfirms`、`::csvFilePickerRunsOffEventDispatchThread` | ✅ |
| TC-A2.3-3 | 故事2.3-③ | UTC 解析 + 预览影响摘要（新增/疑似重复/币种持仓变化） | 导入含 UTC 时间的 CSV | 时间按 UTC 解析；预览显示新增 N/重复 M/涉及币种变化 | `[D]ledger/CsvTradeParserTest.kt::parsesTemplateWithAliasHeadersAndUtc`、`::tolerantTimeFormatsAllParse`；`[UI]ledger/TransactionsPageUiTest.kt::csvWizardParsesPreviewsAndConfirms`（**疑似重复数与 `csv-affected` 概览无断言 → 补 Compose UI**） | 🟡 |
| TC-A2.3-4 | 故事2.3-④ | 去重：订单号精确 + 无订单号模糊匹配并逐条确认 | 重复导入同一 CSV；无订单号行重复 | 有订单号按「交易所+订单号」跳过；无订单号模糊匹配并可逐条确认 | `[D]ledger/LedgerTransactionRepositoryTest.kt::exactAndFuzzyDedup`；`[D]ledger/DefaultTransactionLedgerServiceTest.kt::csvPreviewAndConfirmImportWithDedupAndAnomaly`（**逐条确认的 UI 交互无断言 → 补 Compose UI**） | 🟡 |
| TC-A2.3-5 | 故事2.3-⑤ | 确认后批量导入并更新持仓成本；不校验余额，负持仓标异常 | 导入卖出 1 BTC（本地无 BTC） | 导入成功、持仓 −1、标记「持仓异常」、市值不计入净值 | `[D]ledger/DefaultTransactionLedgerServiceTest.kt::csvPreviewAndConfirmImportWithDedupAndAnomaly`；`[DOM]engine/GoldenCasesTest.kt::golden 6 - csv import negative position is anomalous then backdated deposit clears it`；`[D]portfolio/DefaultPortfolioServiceTest.kt::anomalousHoldingsAreSortedAndFlagged` | ✅ |
| TC-A2.3-6 | 故事2.3-⑥ | 法币计价交易对归一化 | 记录/导入 BTC/USD 买入 | 计价腿 1:1 映射 USDT 联动与余额校验 | `[DOM]engine/GoldenCasesTest.kt::golden 12 - fiat quoted pair is normalized to twin stablecoin before replay`；`[DOM]catalog/FiatNormalizerTest.kt::default twins map USD to USDT and EUR to EURC`；`[D]catalog/SqlCoinCatalogTest.kt::fiat quote legs map USD and EUR to twin stables` | ✅ |
| TC-A2.3-7 | 故事2.3-⑦ | 歧义 ticker 消歧、候选列表选择、映射固化复用 | 导入 symbol 歧义行 | 自动消歧优先；仍歧义弹候选；选择后固化复用 | `[D]catalog/SqlCoinCatalogTest.kt::ambiguous ticker returns candidates and manual freeze persists`、`::unique symbol resolves and freezes AUTO mapping for reuse`；`[D]ledger/CsvTradeParserTest.kt::ambiguousTickerSurfacesCandidates`；`[D]ledger/DefaultTransactionLedgerServiceTest.kt::csvAmbiguityChoiceFreezesMappingAndImports`（**`csv-ambiguity` 候选 UI 用例固定 `ambiguous=emptyList()` → 补 Compose UI**） | 🟡 |

### 1.4 史诗故事 3：日常投资组合跟踪（3.1 / 3.2 / 3.3 / 3.4）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-A3.1-1 | 故事3.1-① | 仪表盘为登录后默认页面/首个 Tab | 登录成功进入主壳 | 当前页 = DASHBOARD | `[UI]ShellUiTest.kt::sidebar shows five primary entries and navigates`（**无「登录后落在仪表盘」断言 → 补 Compose UI/integration**） | 🟡 |
| TC-A3.1-2 | 故事3.1-② | 显著显示总净值（负持仓不计；现金合计=可用现金） | 三币持仓含 1 个负持仓币 | 净值=有价且非负持仓之和；可用现金=白名单币合计 | `[UI]portfolio/PortfolioPagesUiTest.kt::dashboard renders overview cards donut and popup on slice click`；`[DOM]engine/PortfolioCalculatorTest.kt::net value equals sum of priced holdings`、`::anomalous negative holding is excluded from net value and reported separately`、`::anomalous negative cash coin is excluded from available cash too` | ✅ |
| TC-A3.1-3 | 故事3.1-③ | 24h 盈亏金额+百分比（回算法、两位小数、正负色） | 构造部分缺价/异源快照 | 剔除缺失并标「覆盖 N/M」；异源标「混合数据源」；符号强制 +/- | `[DOM]market/TwentyFourHourTest.kt::golden 11 - partial missing 24h prices are excluded and coverage is 8 of 10`；`[UI]portfolio/PortfolioPagesUiTest.kt::twenty four hour subtitle carries coverage and mixed source notes`；`[UI]i18n/WzFormatTest.kt::pnl values always carry an explicit sign`（**卡片随配色方案变色无断言 → 见 TC-UX-16**） | 🟡 |
| TC-A3.1-4 | 故事3.1-④ | 显示投入本金与基于它的 ROI | 增资 100→涨到 150→撤资 150 | 投入本金(净)/总收益 50/ROI 50% | `[DOM]engine/PortfolioCalculatorTest.kt::no deposits means total return and roi are null for dash display`、`::roi uses cumulative deposits as denominator regardless of withdrawals`；`[UI]portfolio/PortfolioPagesUiTest.kt::dashboard renders overview cards donut and popup on slice click` | ✅ |
| TC-A3.1-5 | 故事3.1-⑤ | 交互式饼图，低于阈值归「其他」 | 设阈值 10000；点扇区 | 小额合并且标注「其他」；点击浮窗显示名称/数量/占比/市值 | `[UI]portfolio/PortfolioPagesUiTest.kt::distribution merges slices below the small threshold into other`、`::dashboard renders overview cards donut and popup on slice click` | ✅ |
| TC-A3.2-1 | 故事3.2-① | CG 主源 keyless/keyed 两级 + CMC 兜底标注与回落 | 无 Key 刷新→配 Key 刷新→CG 429→CG 失败且无 CMC Key | 三种路径数据源标注正确；无 CMC Key 保持上次价 | `[D]market/DefaultMarketRefreshServiceTest.kt::keyless primary success writes snapshots and reports cg source`、`::cg rate limited falls back to cmc and reports cmc source`、`::cg failure without cmc key reports error and keeps last-price semantics`；`[UI]shell/ShellStatusViewModelTest.kt::fallback source is labelled in the badge`；`[UI]market/MarketSettingsSectionUiTest.kt::cmc fallback source indicator is displayed after refresh` | ✅ |
| TC-A3.2-2 | 故事3.2-② | 自动刷新+频率 5/15/30（默认5）+托盘降频+手动刷新 | 设频率 15；窗口可见/托盘驻留两态；点手动刷新 | 可见按频率、托盘按同步间隔；手动刷新立即触发 | `[DOM]market/RefreshCadenceTest.kt::visible window follows configured frequency`、`::tray mode degrades to the api sync interval`；`[D]schedule/BackgroundSchedulerTest.kt::market delay follows refresh frequency when window visible`、`::market delay degrades to sync interval when tray resident`；`[UI]ShellUiTest.kt::top bar manual sync button invokes callback and reflects syncing state`（**看板/资产页「刷新行情」按钮无断言 → 补 Compose UI**） | ✅ |
| TC-A3.2-3 | 故事3.2-③ | 请求失败/断网明显提示 + 上次成功时间戳 | 让行情源返回网络错误 | 状态栏标记离线；展示上次成功时间戳；图标可点刷新 | `[UI]shell/ShellStatusViewModelTest.kt::network error marks the status bar as offline`；`[D]market/MarketHttpClientTest.kt::network failure maps to network error`（**时间戳展示与点击刷新无断言 → 补 Compose UI**） | 🟡 |
| TC-A3.2-4 | 故事3.2-④ | 快照每币每法币每小时一条；永久保存+90天降采样 | 同一小时写两次；写 91 天前非整点行 | 同小时仅 1 行取末条；降采样保留 00:00 日级行 | `[D]market/PriceSnapshotRepositoryTest.kt::same hour upsert keeps single row with latest price`、`::compaction drops old non-midnight rows and keeps midnight daily rows`、`::compaction is idempotent`；`[DOM]market/PriceResolutionTest.kt::compaction keeps only midnight rows older than 90 days` | ✅ |
| TC-A3.2-5 | 故事3.2-⑤ | 历史回填（90天小时级/更早日级）+「待定价」联网回算 | 制造快照空洞；离线记录一笔含折算的记录后联网 | 回填补齐空洞；PENDING 记录回填后重算并消除标注 | `[D]market/DefaultMarketHistoryBackfillServiceTest.kt::backfill fills missing hourly buckets and resolves previously empty moments`、`::backfill retries once on rate limit and records history quota`；`[DOM]engine/GoldenCasesTest.kt::golden 9 - pending fee price marks estimates then backfill corrects and clears markers`；`[D]ledger/DefaultFundServiceTest.kt::offlineDepositMarkedPendingAndBackfillCorrectsValue` | ✅ |
| TC-A3.2-6 | 故事3.2-⑥ | 开箱即用 + 429 提示 + Key 配置/注册入口可达 | 无 Key 点刷新触发 429 | 提示「注册免费个人 Key」；设置页可达 Key 配置 | `[UI]market/MarketSettingsSectionUiTest.kt::page renders rows with keyless defaults and frequency markers`、`::manual refresh with rate limit error surfaces keyless hint toast`；`[UI]shell/ShellStatusViewModelTest.kt::shared rate limit hint appears when the keyless api is throttled`；`[D]market/MarketHttpClientTest.kt::cg current attaches key header when provided and omits when keyless`（**注册链接点击打开无断言 → 见 TC-MAN-10**） | 🟡 |
| TC-A3.3-1 | 故事3.3-① | 持仓情况页面以列表展示所有持有币种 | 打开资产列表页 | 全部持仓行渲染（含零持仓行） | `[UI]portfolio/PortfolioPagesUiTest.kt::assets page marks unpriced rows and opens coin detail on row click`；`[D]portfolio/DefaultPortfolioServiceTest.kt::fullyExitedCoinKeepsZeroQuantityRow` | ✅ |
| TC-A3.3-2 | 故事3.3-② | 每行含名称/数量/均价/现价/市值/浮盈(额+%)/累计已实现 | 打开资产列表页逐行核对 | 7 类字段齐全且口径正确 | `[D]portfolio/DefaultPortfolioServiceTest.kt::rowsAreMarketValueDescendingWithCatalogFieldsAndShares`；`[DOM]engine/PortfolioCalculatorTest.kt::holding metrics expose float pnl and percentages`（**UI 未逐列断言 → 补 Compose UI**） | 🟡 |
| TC-A3.3-3 | 故事3.3-③ | 列表支持按不同列排序 | 点表头切币种/浮盈/市值 | 排序方向切换、行序正确 | `[UI]portfolio/PortfolioPagesUiTest.kt::default sort is market value descending and header click flips direction` | ✅ |
| TC-A3.4-1 | 故事3.4-① | 点击资产行进入币种详情 | 点 BTC 行 | 打开币种详情页 | `[UI]portfolio/PortfolioPagesUiTest.kt::assets page marks unpriced rows and opens coin detail on row click` | ✅ |
| TC-A3.4-2 | 故事3.4-② | 详情顶部：数量/价值/占比/均价/浮盈(额+%)/累计已实现 | 打开 BTC 详情 | 6 类汇总字段正确 | `[UI]portfolio/PortfolioPagesUiTest.kt::coin detail shows summary filters realised pnl and calibration entry`；`[D]portfolio/DefaultPortfolioServiceTest.kt::coinDetailReusesSnapshotRowAndListsCalibrationsDescending`（**占比/均价/浮盈% 未断言 → 补 Compose UI**） | 🟡 |
| TC-A3.4-3 | 故事3.4-③ | 详情下方列出该币种全部交易记录 | 打开 BTC 详情 | 交易行渲染（含卖出行） | `[UI]portfolio/PortfolioPagesUiTest.kt::coin detail shows summary filters realised pnl and calibration entry` | ✅ |
| TC-A3.4-4 | 故事3.4-④ | 交易记录按交易所/类型/**时间**筛选与搜索 | 用交易所+类型+搜索过滤；切换时间档位 | 三维筛选 + 搜索齐备；切档位即时过滤 | ✅ **DEF-03 已修复（C1 · D30，2026-09-14）**：`[UI]portfolio/PortfolioPagesUiTest.kt::coin detail filters transactions by time range`（近 30 天 → 远期行消失；90 天以上 → 只剩远期行；去掉实现必红）；档位复用资金页 `FundDateRange`，文案 `PortfolioStrings.dateRangeLabel` | ✅ |
| TC-A3.4-5 | 故事3.4-⑤ | 交易列表字段齐全；卖出行额外显示已实现盈亏 | 打开含卖出记录的详情 | 卖出行显示逐笔已实现盈亏 | 同上（`coin-tx-realized-2`）；`[UI]ledger/TransactionsPageUiTest.kt::listRendersSellRealizedAndDeleteConfirmFlow`（**其余列未逐列断言 → 补 Compose UI**） | 🟡 |

### 1.5 史诗故事 4：网络与自动化功能（4.1 / 4.2）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-A4.1-1 | 故事4.1-① | 设置中提供添加 API 密钥入口 | 打开设置→API 管理 | 空态含「添加 API」按钮 | `[UI]exchange/ApiManagementSectionUiTest.kt::empty state shows add button and no key rows` | ✅ |
| TC-A4.1-2 | 故事4.1-② | 明确提示只读权限 + 创建教程链接 | 打开添加密钥弹窗 | 显示「仅需要只读权限」与教程说明 | 邻接覆盖：`[UI]exchange/ApiManagementSectionUiTest.kt::add modal focuses alias input and save triggers first sync toast`（同一弹窗，未断言文案）；实现已有（`ui/i18n/ExchangeStrings.kt` `hintReadOnly`）**但零断言 → 补 Compose UI 文案断言**；外链打开见 TC-MAN-10 | 🟡 |
| TC-A4.1-3 | 故事4.1-③ | API 密钥在本地高强度加密存储 | 保存密钥后读库列 + 用错 DEK 解 | 列为密文；仅账户 DEK 可解；AAD 绑定账户/列名 | `[D]exchange/ExchangeRepositoriesTest.kt::api key save roundtrips ciphertext and decrypts with account dek`；`[DOM]security/FieldCipherTest.kt::aad isolates account apiKey and column`、`::wrong dek fails`；`[D]SqlCipherDatabaseTest.kt::fresh database is encrypted at rest and migrates to latest` | ✅ |
| TC-A4.1-4 | 故事4.1-④ | 定期自动同步、按去重键增量、不覆盖本地持仓 | 首次同步→重复同步 | 首次导入 N 笔；二次 0 新增；重复同步不产生重复行 | `[D]exchange/DefaultExchangeSyncServiceTest.kt::add and sync imports new trades with dedupe then repeat sync does not duplicate`；`[DOM]exchange/ExchangeSyncPolicyTest.kt::plan caps at myTrades call budget and queues the rest`；`[D]schedule/BackgroundSchedulerTest.kt::sync with keys emits result event` | ✅ |
| TC-A4.1-5 | 故事4.1-⑤ | 手动校准（单一来源；多来源隐藏）、锚点重放、差额视同资金操作、留痕 | 导入同步记录→详情点校准；另用多来源币试 | 生成校准记录并写 sync_logs；多来源时入口隐藏并提示 | `[D]ledger/CalibrationServiceTest.kt::prepareReturnsPlanAndExecuteWritesAnchorAndSyncLog`、`::multiSourceCoinBlockedWithGuidance`；`[DOM]engine/ReconciliationServiceTest.kt::anchor from plan keeps identity under a positive delta`；`[APP]integration/ExchangeLoopbackIntegrationTest.kt::同步到账本到校准到看板 回环链路`；`[UI]portfolio/PortfolioPagesUiTest.kt::coin detail hides the calibration entry for multi source coins` | ✅ |
| TC-A4.1-6 | 故事4.1-⑥ | 文档明确 Binance 仅返回最近 500 条，更早须 CSV | 查看 API 管理页说明文案 | 页面说明含 500 条与 CSV 补录提示 | 邻接覆盖：`[UI]exchange/ApiManagementSectionUiTest.kt::key list renders status and sync logs`（同页）；实现已有（`ExchangeStrings.pageSub` 含 500 条说明）**但零断言 → 补 Compose UI 文案断言** | 🟡 |
| TC-A4.1-7 | 故事4.1-⑦ | 解析基于 exchange_coin_map；歧义消歧；结果冻结不回溯 | 同步含歧义资产；再改目录 | AUTO/MANUAL 映射固化复用；历史记录不回溯改写 | `[D]catalog/SqlCoinCatalogTest.kt::unique symbol resolves and freezes AUTO mapping for reuse`、`::ambiguous ticker returns candidates and manual freeze persists`；`[D]exchange/DefaultExchangeSyncServiceTest.kt::ambiguous ticker resolves via rank warm-up and imports trades` | ✅ |
| TC-A4.2-1 | 故事4.2-① | 启动时自动检测操作系统代理 | 有 JDK 代理/仅环境变量/两者皆无三种环境启动 | 依次取 JDK → 环境变量 → 直连 | `[D]proxy/SystemProxyDetectorTest.kt::jdk system proxy wins over environment`、`::environment used when jdk reports direct`、`::direct when neither jdk nor environment configures a proxy`；`[DOM]proxy/SystemProxyTest.kt::parses http and socks urls` | ✅ |
| TC-A4.2-2 | 故事4.2-② | 所有对外请求必须经检测到的代理 | 关闭开关→请求；开启→请求 | 两类客户端共用同一可开关 selector；关闭时强制直连 | `[D]security/ApiIsolationGuardTest.kt::clients never share a mutable proxy selector by construction`；`[D]proxy/SystemProxyDetectorTest.kt::toggleable selector delegates when on and forces direct when off`、`::runtime holds switch copy and publishes status`（结构守护；**运行期抓包实证见 TC-SEC-09 🔵**） | 🟡 |
| TC-A4.2-3 | 故事4.2-③ | 状态栏指示当前是否通过代理连接 | 切换直连/系统代理/开关开但无代理 | 分别显示「直连」「代理：系统代理」「直连」 | `[UI]ShellUiTest.kt::status bar proxy indicator follows proxy status` | ✅ |

### 1.6 史诗故事 5：数据安全与管理（5.1 / 5.2）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-A5.1-1 | 故事5.1-① | 分层存储与加密（整库加密+凭证字段级 DEK；公共表不字段级加密） | 打开库校验 WAL/busy_timeout/FK；读 api_keys 列；读 coins 表 | 连接参数生效；凭证列为密文；公共表无账户级字段加密 | `[D]SqlCipherDatabaseTest.kt::wal busy_timeout and foreign_keys are effective on every connection`、`::exposed connections carry the key on fresh connections`；`[D]exchange/ExchangeRepositoriesTest.kt::api key save roundtrips ciphertext and decrypts with account dek`；`[D]catalog/SqlCoinCatalogTest.kt::map and catalog are global tables without account scoping` | ✅ |
| TC-A5.1-2 | 故事5.1-② | 分层密钥 DEK/KEK/KDF（Argon2id 或 PBKDF2≥600k） | 派生 KEK→包裹 DEK→解包；读取 KDF 参数串 | 同盐同口令确定；错口令无法解包；低于 OWASP 基线的参数被拒 | `[DOM]security/Argon2KdfTest.kt::derives 32 bytes deterministically with same salt and password`、`::different salt or different password yield different kek`；`[DOM]security/KdfParamsTest.kt::default matches frozen argon2id values and storage roundtrip`、`::downgraded storage strings below the owasp baseline are rejected`；`[DOM]security/KeyWrapTest.kt::wrap unwrap roundtrip restores dek` | ✅ |
| TC-A5.1-3 | 故事5.1-③ | 防篡改用认证加密（GCM/HMAC）而非校验和 | 篡改密文/头部/AAD | 一律认证失败并报类型化错误 | `[DOM]security/KeyWrapTest.kt::tampered ciphertext fails authentication`、`::wrong account id aad fails authentication`；`[DOM]security/DeviceSecretCipherTest.kt::tampered ciphertext fails authentication`、`::purpose mismatch fails authentication (cross placement protection)`；`[DOM]backup/CproCodecTest.kt::header tampering is rejected via AAD binding` | ✅ |
| TC-A5.1-4 | 故事5.1-④ | 密码与登录凭据不落盘；仅记住我令牌入 OS 钥匙串 | 建账后全库扫描口令明文；读钥匙串条目 | 无口令明文；条目仅令牌 + 包裹会话 DEK | `[D]security/KeyringRememberMeStoreTest.kt::save load clear roundtrip through real os keyring`；`[D]accounts/AccountRepositoryTest.kt::createWrapped inserts then wraps with real id and roundtrips`；`[D]accounts/DefaultAccountServiceTest.kt::remember me roundtrip saves restores and logout clears` | ✅ |
| TC-A5.1-5 | 故事5.1-⑤ | 切换账户必须重新输入密码；登出即清会话令牌 | 切换→错密码→正确密码；登出 | 错密码拒绝；成功后旧令牌作废；登出后 restore 返回 null | `[D]accounts/DefaultAccountServiceTest.kt::switch requires target password and wipes old session remember entry`；`[UI]auth/AuthFlowUiTest.kt::account menu switch requires password and logout roundtrip works` | ✅ |
| TC-A5.1-6 | 故事5.1-⑥ | 改密验原密码；仅重包 KEK；旧备份仍可用；令牌立即失效 | 改密（先错原密码再正确）→重登→用旧备份恢复 | 错原密码拒绝；DEK 不变（仅重包）；旧口令失效；旧备份仍可用备份密码恢复 | `[D]accounts/DefaultAccountServiceTest.kt::change password invalidates token and old password and rewraps same dek`（**「改密后旧备份仍可恢复」无端到端用例 → 补 integration**） | 🟡 |
| TC-A5.2-1 | 故事5.2-① | 备份当前账户业务数据（不含账户信息）+ 可选路径 | 导出 .cpro→枚举头部与载荷表 | 载荷=7 张账户级表；无 accounts/全局设置/coins/行情 Key | `[D]backup/BackupBoundaryGuardTest.kt::backup payload model carries exactly the seven account scoped tables`、`::account rows coins and global settings stay out of the backup`、`::market api key never enters the backup file`；`[D]backup/DefaultBackupServiceTest.kt::export and restore roundtrip is lossless across accounts`；`[UI]backup/DataManagementSectionUiTest.kt::backupModalFocusesPasswordAndExportsViaKeyboard` | ✅ |
| TC-A5.2-2 | 故事5.2-② | 明文头部（版本/应用版本/时间/条数/范围）+GCM 加密 JSON 载荷 | 解析 .cpro 头部与载荷；篡改头部 | 头部单行明文 JSON 含 counts/range；载荷 JSON 往返无损；篡改即拒 | `[DOM]backup/CproCodecTest.kt::header json is single line plaintext`、`::roundtrip is lossless`、`::newer format version is rejected as unsupported`、`::malformed file is rejected without password attempt`；`[UI]backup/DataManagementSectionUiTest.kt::restoreWizardFullFlowWithOverwriteConfirm` | ✅ |
| TC-A5.2-3 | 故事5.2-③ | 备份密码独立设置、强度校验、**不回填**（D24 裁决） | 打开备份弹窗检查密码框初值；输弱密码导出 | 密码框为空（不回填账户密码）；弱密码被拒且不落文件 | `[D]backup/DefaultBackupServiceTest.kt::weak export password is rejected`；`[UI]backup/DataManagementSectionUiTest.kt::backupModalRejectsWeakPasswordWithoutTouchingService`（**「不回填」为 D24 裁决后的正确行为，缺显式断言 → 补 Compose UI**） | 🟡 |
| TC-A5.2-4 | 故事5.2-④ | 导出前提示「含 API 密钥等敏感数据」 | 打开备份弹窗 | 显示敏感数据警告行 | `[UI]backup/DataManagementSectionUiTest.kt::backupModalFocusesPasswordAndExportsViaKeyboard`（`backup-sensitive-warning`） | ✅ |
| TC-A5.2-5 | 故事5.2-⑤ | 恢复：先显示明文摘要，再输密码验证 | 选 .cpro→看摘要→输错密码→输正确密码 | 摘要先于密码出现；错密码报类型化错误且不执行 | `[UI]backup/DataManagementSectionUiTest.kt::restoreWizardFullFlowWithOverwriteConfirm`、`::restoreWizardShowsTypedErrorOnWrongPassword`；`[D]backup/DefaultBackupServiceTest.kt::wrong password is rejected with typed reason` | ✅ |
| TC-A5.2-6 | 故事5.2-⑥ | 增量合并三级去重 + 各表规则；全量覆盖二次确认+临时备份+公共表不清空 | 同文件两次合并；切全量覆盖执行 | 二次合并不重复；api_keys/fee_rules/settings 备份优先；覆盖前生成临时备份；price_snapshots 不清空 | `[DOM]backup/BackupMergePlannerTest.kt::uuid match skips duplicate`、`::exchange plus order id match skips duplicate`、`::api keys skip on exchange plus name`、`::fee rules and settings always upsert backup wins`、`::snapshot idempotent by coin fiat hour bucket local wins`、`::full overwrite ignores local keys but keeps snapshot idempotency`、`::golden case 10 - re-planning the same payload is a no-op`；`[D]backup/DefaultBackupServiceTest.kt::full overwrite creates temp backup and replaces account data`、`::snapshots are restored when the global table lacks them`、`::rows referencing missing coins are skipped with reasons` | ✅ |
| TC-A5.2-7 | 故事5.2-⑦ | 导入完成后触发全量重放重算并刷新/提示重启 | 恢复前后对比净值/总收益/已实现 | 三者一致（重放无漂移）；界面刷新到恢复后数据 | `[APP]integration/CoreJourneyIntegrationTest.kt::核心旅程 登录 增资 交易 看板ROI 备份恢复`；`[D]backup/DefaultBackupServiceTest.kt::export and restore roundtrip is lossless across accounts`（**「刷新所有页面/提示重启」的 UI 行为无断言 → 补 Compose UI**） | 🟡 |
| TC-A5.2-8 | 故事5.2-⑧ | 全新安装提供「从备份恢复」入口，恢复前先建账 | 空库启动向导→点「从备份恢复」→建账→恢复 | 入口可达；恢复数据归入新建账户 | 邻接覆盖：`[UI]auth/AuthFlowUiTest.kt::create requires risk confirm hard gate then wizard then shell via later`（只走 `wizard-later`）；实现已有（`ui/auth/GatePages.kt` `wizard-restore`）**→ 补 Compose UI + integration** | 🟡 |
| TC-A5.2-9 | 故事5.2-⑨ | 加密语义：导出先解密、导入用目标账户 DEK 重加密 | 账户 A 导出→账户 B 恢复→用恢复的 Key 触发同步 | 仅凭备份密码可恢复；凭证可解密并继续驱动同步 | `[D]backup/DefaultBackupServiceTest.kt::export and restore roundtrip is lossless across accounts`；`[APP]integration/ExchangeLoopbackIntegrationTest.kt::同步到账本到校准到看板 回环链路` | ✅ |

### 1.7 史诗故事 6：资金管理（6.1–6.4）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-A6.1-1 | 故事6.1-① | 提供「资金操作/资金管理」页面 | 侧边栏进入资金管理 | 页面含命令区与总览卡 | `[UI]ledger/FundsPageUiTest.kt::emptyStateShowsCommandAreaAndOverview` | ✅ |
| TC-A6.1-2 | 故事6.1-② | 记录增资表单：币种/数量/日期/来源/备注 | 打开增资弹窗逐个检查字段 | 5 类字段可见可编辑；币种必填 | `[UI]ledger/FundsPageUiTest.kt::depositModalFocusesCoinInputAndSavesViaKeyboard`、`::emptyFormShowsV6ErrorsAndDoesNotTouchService`（**日期/来源/备注无断言 → 补 Compose UI**） | 🟡 |
| TC-A6.1-3 | 故事6.1-③ | 增资记录单独列表，可与交易区分 | 记增资+交易后看两列表 | 资金列表含类型列（增资/撤资/校准） | `[UI]ledger/FundsPageUiTest.kt::listRendersMixedRowsAndReconRowHasNoEdit` | ✅ |
| TC-A6.1-4 | 故事6.1-④ | 增资增加累计增资与投入本金（折算基础法币） | 增资 1000 USDT | 累计增资 +1000、投入本金(净) +1000 | `[D]ledger/DefaultFundServiceTest.kt::depositWithUsdAnchoredStablecoinSavesAndFeedsOverview`；`[DOM]engine/GoldenCasesTest.kt::golden 2 - deposit then withdrawal produces no pnl and net principal returns to zero`；`[D]portfolio/DefaultPortfolioServiceTest.kt::d27DepositTradeAndMetricsStayAtParDespiteMarketSnapshot` | ✅ |
| TC-A6.1-5 | 故事6.1-⑤ | 持仓增加（成本=转入市价）；现金类反映到可用现金 | 增资 1000 USDT 后看资产与资金页 | 持仓 +1000、成本按记录时快照价、可用现金 +1000 | `[D]ledger/DefaultFundServiceTest.kt::depositUsesRecordTimeSnapshotPrice`；`[D]settings/CashWhitelistWiringTest.kt::extended whitelist coin counts into available cash`、`::default wiring preserves usd anchored stablecoin flows` | ✅ |
| TC-A6.2-1 | 故事6.2-① | 与增资共用「资金操作」页面 | 资金页点「记录撤资」 | 同一页面弹出撤资表单 | `[UI]ledger/FundsPageUiTest.kt::withdrawModalUsesWithdrawTitleAndKind` | ✅ |
| TC-A6.2-2 | 故事6.2-② | 记录撤资表单：币种/数量/日期/去向/备注 | 打开撤资弹窗检查字段 | 5 类字段可见；标签为「去向」 | `[UI]ledger/FundsPageUiTest.kt::withdrawModalUsesWithdrawTitleAndKind`（**去向/日期字段无断言 → 补 Compose UI**） | 🟡 |
| TC-A6.2-3 | 故事6.2-③ | 撤资记录单独列表，可与交易区分 | 记撤资后看资金列表 | 类型列显示「撤资」 | `[UI]ledger/FundsPageUiTest.kt::listRendersMixedRowsAndReconRowHasNoEdit` | ✅ |
| TC-A6.2-4 | 故事6.2-④ | 撤资减少投入本金（折算基础法币金额） | 增资 100→撤资 100 | 投入本金(净) 回 0，不产生盈亏 | `[D]ledger/DefaultFundServiceTest.kt::goldenCase2ShapeDepositThenWithdrawPrincipalBackToZero`；`[DOM]engine/GoldenCasesTest.kt::golden 2 - deposit then withdrawal produces no pnl and net principal returns to zero` | ✅ |
| TC-A6.2-5 | 故事6.2-⑤ | 按平均成本移出；持仓不足阻止保存并提示 | 持仓 100 时撤资 150 | 阻止保存并提示持仓不足 | `[D]ledger/DefaultFundServiceTest.kt::withdrawalBeyondPositionBlockedV7`、`::withdrawalDeepeningImportedNegativeStillBlockedV7`；`[DOM]engine/ReplayEngineTest.kt::strict replay blocks withdrawal beyond holdings` | ✅ |
| TC-A6.2-6 | 故事6.2-⑥ | 现金类撤资同时反映在可用现金 | 撤资 100 USDT | 可用现金 −100 | `[D]ledger/DefaultFundServiceTest.kt::goldenCase2ShapeDepositThenWithdrawPrincipalBackToZero`；`[D]settings/CashWhitelistWiringTest.kt::extended whitelist coin counts into available cash` | ✅ |
| TC-A6.3-1 | 故事6.3-① | 资金页显示全部资金操作记录列表 | 造增资/撤资/校准三类记录 | 三类均列出 | `[UI]ledger/FundsPageUiTest.kt::listRendersMixedRowsAndReconRowHasNoEdit`、`::typeAndDateFiltersToggleWithoutCrash` | ✅ |
| TC-A6.3-2 | 故事6.3-② | 列表按时间倒序排列 | 造多条不同时间记录 | 最新在前 | `[D]ledger/DefaultFundServiceTest.kt::fundsListMergesReconciliationRowsAndFiltersApply`（**UI 列表顺序无断言 → 补 Compose UI**） | 🟡 |
| TC-A6.3-3 | 故事6.3-③ | 列表字段：类型/金额/日期时间/来源去向/备注 | 逐列核对 | 5 类字段齐全 | `[UI]ledger/FundsPageUiTest.kt::listRendersMixedRowsAndReconRowHasNoEdit`（**未逐列断言 → 补 Compose UI**） | 🟡 |
| TC-A6.3-4 | 故事6.3-④ | 支持编辑与删除；删除二次确认并提示重算；触发全量重放 | 选中记录删除→确认框→确认 | 确认框提示「重算投入本金与 ROI」；删除后重放生效；校准行不可编辑 | `[UI]ledger/FundsPageUiTest.kt::deleteConfirmFlowReachesService`、`::listRendersMixedRowsAndReconRowHasNoEdit`；`[D]ledger/DefaultFundServiceTest.kt::deletingDepositBlockedV9`、`::shrinkingDepositBreaksLaterBuyBlockedV9` | ✅ |
| TC-A6.4-1 | 故事6.4-① | 总收益=净值+累计撤资−累计增资；ROI=总收益/累计增资 | 黄金用例 3/4 数值轨迹 | 总收益 50；ROI 50%/25% | `[DOM]engine/GoldenCasesTest.kt::golden 3 - profitable withdrawal leaves return 50 and roi 50 percent`、`::golden 4 - deposit again after profitable withdrawal keeps return 50 and roi 25 percent`；`[DOM]engine/PortfolioCalculatorTest.kt::roi uses cumulative deposits as denominator regardless of withdrawals` | ✅ |
| TC-A6.4-2 | 故事6.4-② | 累计增资为 0 时显示"--"并提示先记录增资 | 空账本看仪表盘 ROI 卡 | 显示「--」；卡片旁提示「请先记录增资」 | 「--」已覆盖：`[DOM]engine/PortfolioCalculatorTest.kt::no deposits means total return and roi are null for dash display`、`[UI]i18n/WzFormatTest.kt::missing values render as dash`；**「请先记录增资」提示在 `PortfolioStrings` 中不存在（未实现）→ 登记 P8（产品或文案待定）** | ⬜ |
| TC-A6.4-3 | 故事6.4-③ | 仪表盘与资产列表顶部显示投入本金(净)/总收益/ROI | 分别打开仪表盘与资产列表 | 两页顶部总览均含三项 | `[UI]portfolio/PortfolioPagesUiTest.kt::dashboard renders overview cards donut and popup on slice click`（仪表盘三项已断言；**资产列表 `assets-card-net/24h/roi` 无断言 → 补 Compose UI**） | 🟡 |
| TC-A6.4-4 | 故事6.4-④ | ROI 数值清晰展示，正数绿、负数红 | 盈利/亏损两账户对比 | 颜色随配色方案变化，符号强制 +/- | `[UI]i18n/WzFormatTest.kt::pnl values always carry an explicit sign`（符号已覆盖；**颜色无断言 → 见 TC-UX-16**） | 🟡 |
| TC-A6.4-5 | 故事6.4-⑤ | ROI 计入所有增资撤资（撤资计入总收益，不抵扣分母） | 黄金用例 3/4 + 多次撤资 | 分母恒为累计增资 | `[DOM]engine/GoldenCasesTest.kt::golden 3 …`、`::golden 4 …`；`[DOM]engine/PortfolioCalculatorTest.kt::roi uses cumulative deposits as denominator regardless of withdrawals` | ✅ |

### 1.8 史诗故事 7：交易管理（7.1 / 7.2）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-A7.1-1 | 故事7.1-① | 「设置」中新增「手续费设置」 | 打开设置看分组 | 存在「手续费」分组与费率行 | `[UI]ledger/FeeRuleSettingsUiTest.kt::globalRatesSaveAndPersist`；`[UI]settings/SettingsPageUiTest.kt::all groups render on single page`（`group-fee`） | ✅ |
| TC-A7.1-2 | 故事7.1-② | 可为交易所（或全局）预设买卖费率 | 添加 Binance 买 0.1%/卖 0.1% | 规则保存；买卖费独立选取 | `[UI]ledger/FeeRuleSettingsUiTest.kt::exchangeRuleAddAndRemove`；`[DOM]engine/FeeCalculatorTest.kt::buy and sell rates are selected independently` | ✅ |
| TC-A7.1-3 | 故事7.1-③ | 输入价格数量后自动计算并显示「总价」 | 表单输 50000 × 0.1 | 「总价」实时显示 5000 并随输入更新 | `[UI]ledger/TransactionsPageUiTest.kt::formShowsTimeAndNotesInputs`（仅 `tx-total` 可见性；**计算值与实时联动无断言 → 补 unit(VM) + Compose UI**） | 🟡 |
| TC-A7.1-4 | 故事7.1-④ | 提供「手续费」输入框，可手动输入 | 表单填 0 与 5 分别保存 | 均允许保存（≥0 合法） | `[UI]ledger/TransactionsPageUiTest.kt::feeZeroIsAllowedAndSaveReachesService` | ✅ |
| TC-A7.1-5 | 故事7.1-⑤ | 「自动计算手续费」按「交易所>全局」与计费基数规则填充 | 建 Binance 费率→表单点自动算费 | 填充 = 费率×基数（quote=总价/base=数量/第三币折算） | `[DOM]engine/FeeCalculatorTest.kt::quote fee base is total price`、`::base fee base is quantity`、`::third currency fee is converted into fee coin quantity at market price`、`::exchange rule wins over global rule for matching exchange`；`[D]ledger/DefaultTransactionLedgerServiceTest.kt::feeQuotePrefersExchangeRateOverGlobalAndHandlesThirdRole`（**UI 点击 `tx-auto-fee` 填充路径无断言 → 补 Compose UI**） | 🟡 |
| TC-A7.1-6 | 故事7.1-⑥ | 手续费计算考虑手续费币种（计价/基础/第三） | 三种角色各算一次 | 角色由交易对推导；基数与币种正确 | `[DOM]engine/FeeCalculatorTest.kt::fee coin role is derived from the pair`；`[D]ledger/TransactionEventBuilderTest.kt::thirdCoinFeeDeductsFeeCoinAndFeePriceResolution` | ✅ |
| TC-A7.1-7 | 故事7.1-⑦ | 手续费与总价实时更新 | 改动价格/数量/手续费观察显示 | 总价与手续费随输入即时刷新 | 邻接覆盖：`[UI]ledger/TransactionsPageUiTest.kt::formShowsTimeAndNotesInputs`（仅 `tx-total` 可见性）；实现已有（表单派生显示）**但零断言 → 补 unit(VM) + Compose UI** | 🟡 |
| TC-A7.1-8 | 故事7.1-⑧ | 保存时按统一规则联动持仓（含手续费口径） | 买入/卖出各一笔含费 | 买入扣 quote 增 base（成本含费）；卖出相反（净收入扣费） | `[DOM]engine/ReplayEngineTest.kt::base currency fee on buy reduces received quantity and includes fee value in cost`、`::quote currency fee on sell reduces proceeds units and cash cost accordingly`、`::base currency fee on sell is netted from proceeds for realized pnl`；`[D]ledger/DefaultTransactionLedgerServiceTest.kt::manualBuySucceedsAfterUsdtBalanceFromCsv` | ✅ |
| TC-A7.1-9 | 故事7.1-⑨ | 买入计价币不足阻止保存并提示「XX 余额不足，请先记录转入（增资）」 | 无本金直接买入 | 阻止保存；文案含币种与「请先记录增资」 | `[UI]ledger/ValidationCopyTest.kt::insufficientBalanceCopyIsActionable`；`[D]ledger/DefaultTransactionLedgerServiceTest.kt::manualBuyWithoutQuoteBalanceBlockedV5`；`[APP]integration/ErrorPathIntegrationTest.kt::买入余额不足 撤资超额 删除增资 三类重放校验在真实库上类型化上浮` | ✅ |
| TC-A7.1-10 | 故事7.1-⑩ | 非 quote 计费按记录时行情折算计入成本/净收入 | BTC/USDT 买入用 BNB 计费 | 折算入成本；BNB 持仓扣减；卖出净收入扣折算费 | `[DOM]engine/ReplayEngineTest.kt::third currency fee on sell deducts fee coin holding and fees net from realized`；`[DOM]engine/GoldenCasesTest.kt::golden 5 - third currency fee is converted into cost and deducts bnb holdings` | ✅ |
| TC-A7.2-1 | 故事7.2-① | 设置页增加「手续费设置」 | 见 TC-A7.1-1 | 分组可见 | `[UI]settings/SettingsPageUiTest.kt::all groups render on single page`；`[UI]ledger/FeeRuleSettingsUiTest.kt::globalRatesSaveAndPersist` | ✅ |
| TC-A7.2-2 | 故事7.2-② | 增删改交易所特定费率规则 | 添加→编辑改名→删除 | 三步均生效并持久化 | `[UI]ledger/FeeRuleSettingsUiTest.kt::exchangeRuleAddAndRemove`、`::exchangeRuleEditPrefillsAndRoutesToEdit`、`::invalidRateShowsError`；`[D]ledger/FeeRuleServiceTest.kt::globalAndExchangeRulesCrudAndResolverPriority` | ✅ |
| TC-A7.2-3 | 故事7.2-③ | 设置全局默认买卖费率 | 设全局买 0.1/卖 0.2 | 保存并驱动解析器 | `[UI]ledger/FeeRuleSettingsUiTest.kt::globalRatesSaveAndPersist`；`[D]ledger/FeeRuleServiceTest.kt::sellRateIsUsedForSellSide` | ✅ |
| TC-A7.2-4 | 故事7.2-④ | 自动计算按「交易所 > 全局」优先级匹配 | 同存 Binance 规则与全局规则 | 取 Binance；无交易所规则回落全局；无规则返回空 | `[DOM]engine/FeeCalculatorTest.kt::exchange rule wins over global rule for matching exchange`、`::global rule is used when no exchange rule exists and null when no rules at all` | ✅ |
| TC-A7.2-5 | 故事7.2-⑤ | 表单自动计算时优先最高匹配度费率 | 表单点自动算费（存在多条规则） | 使用交易所级费率 | `[D]ledger/DefaultTransactionLedgerServiceTest.kt::feeQuotePrefersExchangeRateOverGlobalAndHandlesThirdRole`（**UI 触发路径无断言 → 补 Compose UI**） | 🟡 |
| TC-A7.2-6 | 故事7.2-⑥ | 交易对级费率不在 MVP 范围 | 检查费率表结构/设置入口 | 无 pair 级字段与入口 | 邻接覆盖：`[D]ledger/FeeRuleServiceTest.kt::globalAndExchangeRulesCrudAndResolverPriority`；无 pair 级专项断言 → **补 data-layer 结构断言（fee_rules 无 pair 列）或人工核对**（防回归项） | 🟡 |

## §2 功能用例 · PRD §7.2 核心功能模块

### 2.1 模块 1：仪表盘（Dashboard）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-F1-1 | §7.2-1 总览卡片 | 净值/24h/投入本金(净)/总收益/累计已实现/ROI 六项 | 打开仪表盘核对六张卡 | 六项均渲染且口径正确 | `[UI]portfolio/PortfolioPagesUiTest.kt::dashboard renders overview cards donut and popup on slice click`（net/24h/roi 已断言；**`card-invested-net`/`card-total-return`/`card-realized`/`card-cash` 无断言 → 补 Compose UI**）；`[D]portfolio/DefaultPortfolioServiceTest.kt::emptyLedgerYieldsNeutralSnapshot` | 🟡 |
| TC-F1-2 | §7.2-1 资产分布图 | 环形图占比 + 小额合计归「其他」 | 见 TC-A3.1-5 | 分片占比正确、小额合并、点击浮窗 | `[UI]portfolio/PortfolioPagesUiTest.kt::distribution merges slices below the small threshold into other`、`::dashboard renders overview cards donut and popup on slice click` | ✅ |

### 2.2 模块 2：资产列表（Portfolio）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-F2.1-1 | §7.2-2.1 页面上部 | 总净值（可切换法币）+24h（无 24h 前价显示"--"） | 切基础法币为 EUR；造缺 24h 前价币 | 折算基准随法币切换；缺价显示「--」 | `[D]portfolio/DefaultPortfolioServiceTest.kt::baseFiatSettingDrivesQuoteLookup`；`[DOM]market/TwentyFourHourTest.kt::golden 11 - all coins without 24h price yield nulls (display dash)`；`[UI]portfolio/PortfolioPagesUiTest.kt::twenty four hour subtitle carries coverage and mixed source notes`（**`assets-card-*` 总览卡无 UI 断言 → 补 Compose UI**） | 🟡 |
| TC-F2.1-2 | §7.2-2.1 页面下部 | 列表展示所有持仓资产 | 见 TC-A3.3-1 | 全部行渲染 | `[UI]portfolio/PortfolioPagesUiTest.kt::assets page marks unpriced rows and opens coin detail on row click` | ✅ |
| TC-F2.1-3 | §7.2-2.1 列表字段 | 名称/数量/均价/现价/市值/浮盈(额+%)/累计已实现 | 逐列核对含零成本边界 | 字段齐全；成本为 0 时浮盈% 为空 | `[D]portfolio/DefaultPortfolioServiceTest.kt::rowsAreMarketValueDescendingWithCatalogFieldsAndShares`；`[DOM]engine/PortfolioCalculatorTest.kt::holding metrics expose float pnl and percentages`、`::float pnl percent is null when cost basis is zero`（**UI 未逐列断言 → 补 Compose UI**） | 🟡 |
| TC-F2.1-4 | §7.2-2.1 排序 | 支持按市值/名称/盈亏等字段排序 | 点各表头 | 排序切换且行序正确 | `[UI]portfolio/PortfolioPagesUiTest.kt::default sort is market value descending and header click flips direction` | ✅ |
| TC-F2.2-1 | §7.2-2.2 详情概要 | 数量/价值/占比/浮盈/累计已实现 | 打开详情核对 | 汇总字段正确 | `[UI]portfolio/PortfolioPagesUiTest.kt::coin detail shows summary filters realised pnl and calibration entry`；`[D]portfolio/DefaultPortfolioServiceTest.kt::coinDetailReusesSnapshotRowAndListsCalibrationsDescending`（**占比/浮盈% 未断言 → 补 Compose UI**） | 🟡 |
| TC-F2.2-2 | §7.2-2.2 详情列表 | 单币种交易记录，按交易所/类型/**时间**筛选与搜索 | 同 TC-A3.4-4 | 三维筛选 + 搜索齐备 | ✅ **DEF-03 已修复（C1 · D30）**：覆盖同 TC-A3.4-4（`PortfolioPagesUiTest::coin detail filters transactions by time range`）；`ia.md §2.6` 与 `task-breakdown T12.5` 已回写 | ✅ |
| TC-F2.2-3 | §7.2-2.2 列表字段 | 交易对/类型/价格/数量/手续费/交易所/时间/备注 + 卖出已实现 | 逐列核对 | 字段齐全；卖出行含已实现 | `[UI]portfolio/PortfolioPagesUiTest.kt::coin detail shows summary filters realised pnl and calibration entry`、`[UI]ledger/TransactionsPageUiTest.kt::listRendersSellRealizedAndDeleteConfirmFlow`（**仅卖出已实现徽标有断言 → 补 Compose UI 逐列断言**） | 🟡 |

### 2.3 模块 3：交易管理（Transactions）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-F3-1 | §7.2-3 手动添加 | 表单字段齐 + 自动算总额与手续费 + 保存联动持仓 | 见 TC-A2.1-2/3/4、TC-A7.1-3/5 | 字段齐、总额/手续费自动、保存后持仓联动 | `[UI]ledger/TransactionsPageUiTest.kt::addModalFocusesBaseInputAndSavesViaKeyboard`、`::formShowsTimeAndNotesInputs`；`[D]ledger/DefaultTransactionLedgerServiceTest.kt::manualBuySucceedsAfterUsdtBalanceFromCsv`（**交易所字段/手续费币种/自动算费 UI 无断言 → 补 Compose UI**） | 🟡 |
| TC-F3-2 | §7.2-3 CSV 导入 | 解析 Binance CSV + 预览确认 + 手续费入成本 + 法币归一 | 导入含手续费与法币计价对的 CSV | 解析正确、手续费入成本、USD→USDT 归一 | `[D]ledger/CsvTradeParserTest.kt::parsesTemplateWithAliasHeadersAndUtc`、`::formatErrorsReportedPerLine`、`::commentLinesAreSkipped`、`::excelStyleTimesImportWithoutErrors`；`[D]ledger/DefaultTransactionLedgerServiceTest.kt::csvPreviewAndConfirmImportWithDedupAndAnomaly`；`[UI]ledger/TransactionsPageUiTest.kt::csvWizardParsesPreviewsAndConfirms`；`[DOM]engine/GoldenCasesTest.kt::golden 12 …` | ✅ |
| TC-F3-3 | §7.2-3 交易列表 | 列表字段齐 + 按币种/交易所/类型筛选与搜索 | 输入搜索词、切类型筛选 | 列表按条件过滤且字段正确 | `[UI]ledger/TransactionsPageUiTest.kt::listRendersSellRealizedAndDeleteConfirmFlow`、`::filterButtonsPersistQueryAndSideFilter`（**假服务不过滤，筛选结果正确性无断言 → 补 Compose UI + data-layer**） | 🟡 |

### 2.4 模块 4：API 同步管理

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-F4.1-1 | §7.2-4.1 管理密钥 | 增/改/删 API 密钥（MVP 仅 Binance） | 添加→编辑别名→删除 | 三步均生效；编辑不回显密钥 | `[UI]exchange/ApiManagementSectionUiTest.kt::empty state shows add button and no key rows`、`::remove key triggers removal`、`::edit modal keeps secret blank and alias-only save routes to update not add`、`::edit with only one credential filled is rejected`；`[D]exchange/ExchangeRepositoriesTest.kt::update alias keeps ciphertext and update credentials re-encrypts in place` | ✅ |
| TC-F4.1-2 | §7.2-4.1 帮助链接 | 跳转交易所创建 API 密钥页帮助链接 | 弹窗内点教程链接 | 系统浏览器打开交易所密钥管理页 | 邻接覆盖：`[UI]exchange/ApiManagementSectionUiTest.kt::add modal focuses alias input and save triggers first sync toast`；实现已有（`ExchangeStrings.hintReadOnly` 含教程入口）**但零断言 → 补 Compose UI 断言入口存在；外链打开见 TC-MAN-10** | 🟡 |
| TC-F4.1-3 | §7.2-4.1 加密保存 | 密钥加密后保存在本地 | 见 TC-A4.1-3 | 库内密文、可解 | `[D]exchange/ExchangeRepositoriesTest.kt::api key save roundtrips ciphertext and decrypts with account dek`；`[DOM]security/FieldCipherTest.kt::roundtrip preserves plaintext` | ✅ |
| TC-F4.1-4 | §7.2-4.1 只读提示 | 输入密钥时明确提示「仅需要只读权限」 | 打开弹窗看提示行 | 显示只读权限提示 + 加密提示 + 关联当前账户提示 | 邻接覆盖：`[UI]exchange/ApiManagementSectionUiTest.kt::empty fields show validation errors`（同弹窗校验路径）；实现已有（`hintReadOnly`/`hintEncrypted`/`hintAccount`）**但零断言 → 补 Compose UI 文案断言** | 🟡 |
| TC-F4.2-1 | §7.2-4.2 自动同步 | 启动时与固定间隔（默认30，可调15/30/60）自动同步 | 登录触发一次；改间隔为 15 | 会话激活即同步一次；调度按间隔执行 | `[UI]auth/AuthFlowUiTest.kt::session activation invokes the auto-sync callback exactly once per account`；`[DOM]exchange/ExchangeSyncPolicyTest.kt::cadence allows 15 30 60 and defaults 30`；`[D]schedule/BackgroundSchedulerTest.kt::sync delay uses configured interval with clamp`；`[UI]settings/SettingsPageUiTest.kt::api sync interval row lives in market sync group` | ✅ |
| TC-F4.2-2 | §7.2-4.2 增量策略 | 按「交易所+订单号」去重，不覆盖本地持仓 | 见 TC-A4.1-4 | 重复同步 0 新增 | `[D]exchange/DefaultExchangeSyncServiceTest.kt::add and sync imports new trades with dedupe then repeat sync does not duplicate`；`[D]exchange/ExchangeRepositoriesTest.kt::transactions dedupe on exchange and order id and enumerates synced pairs` | ✅ |
| TC-F4.2-3 | §7.2-4.2 500 条说明 | 文档明确仅返回最近 500 条，历史须 CSV | 见 TC-A4.1-6 | 页面说明含 500 条与 CSV 补录 | 邻接覆盖：`[D]exchange/BinanceAdapterTest.kt::fetch trades parses fills with side fee and since cursor`（500 条窗口的契约侧）；实现已有（`ExchangeStrings.pageSub`）**但零断言 → 补 Compose UI 文案断言** | 🟡 |
| TC-F4.3-1 | §7.2-4.3 手动同步 | 提供手动同步按钮（页面级/顶栏） | 无 Key 点同步→有 Key 点同步→顶栏点同步 | 无 Key 给引导 toast；有 Key 全量同步并汇总；顶栏按钮反映同步中 | `[UI]exchange/ApiManagementSectionUiTest.kt::sync all with keys syncs every key and toasts summary`、`::sync all with no keys shows guidance toast`；`[UI]ShellUiTest.kt::top bar manual sync button invokes callback and reflects syncing state`；`[UI]exchange/TopBarSyncViewModelTest.kt::keysAggregateNewTradesAndPassNullForAll` | ✅ |
| TC-F4.4-1 | §7.2-4.4 状态管理 | 显示每个 API 的最后同步时间与状态（成功/失败） | 造一条 OK 与一条 FAILED 同步日志 | 列表状态列与日志可见；失败含具体原因 | `[UI]exchange/ApiManagementSectionUiTest.kt::key list renders status and sync logs`；`[D]exchange/DefaultExchangeSyncServiceTest.kt::invalid credentials marks failed and sync log message is redacted` | ✅ |
| TC-F4.5-1 | §7.2-4.5 存储要求 | API 密钥本地加密存储 | 同 TC-F4.1-3 | 密文存储 | `[D]exchange/ExchangeRepositoriesTest.kt::api key save roundtrips ciphertext and decrypts with account dek`（同 TC-F4.1-3） | ✅ |

### 2.5 模块 5：数据管理（备份/恢复）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-F5-1 | §7.2-5 备份 | 导出 .cpro（明文头部+加密载荷）、独立密码、可选保存位置 | 见 TC-A5.2-1/2/3/4 | 文件生成、路径透传、头部可读、载荷加密 | `[D]backup/DefaultBackupServiceTest.kt::export and restore roundtrip is lossless across accounts`；`[DOM]backup/CproCodecTest.kt::roundtrip is lossless`；`[UI]backup/DataManagementSectionUiTest.kt::backupModalFocusesPasswordAndExportsViaKeyboard` | ✅ |
| TC-F5-2 | §7.2-5 恢复 | 摘要→密码→方式（合并/覆盖）→重放；向导入口 | 见 TC-A5.2-5/6/7/8 | 向导三步可见；覆盖需二次确认；恢复后指标重算 | `[UI]backup/DataManagementSectionUiTest.kt::restoreWizardFullFlowWithOverwriteConfirm`、`::restoreWizardShowsTypedErrorOnWrongPassword`、`::restoreResultDistinguishesPreExistingAnomalies`；`[APP]integration/ErrorPathIntegrationTest.kt::备份密码错误 与 非cpro文件 的类型化上浮（BACKUP_INVALID）`（**全新安装向导入口未走查 → 补 Compose UI；恢复后刷新 UI 无断言**） | 🟡 |

### 2.6 模块 6：设置（Settings）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-F6.1-1 | §7.2-6.1 基础法币 | 可选 USD/EUR/CNY，切换即换计价基准 | 设置页切 fiat-select；看板/列表更新 | 折算基准与展示符号随切换变化 | `[D]settings/GeneralSettingsServiceTest.kt::base fiat restricted to candidates`；`[D]portfolio/DefaultPortfolioServiceTest.kt::baseFiatSettingDrivesQuoteLookup`（**设置页 `fiat-select` 无 UI 断言 → 补 Compose UI**） | 🟡 |
| TC-F6.1-2 | §7.2-6.1 稳定币白名单 | 默认仅 USDT 不可移除；其他可扩展/移除 | 看默认项；添加 usdd；移除扩展项 | USDT 无移除按钮；扩展项可增删并持久化 | `[D]settings/GeneralSettingsServiceTest.kt::cash whitelist extend remove and default protection`；`[DOM]engine/PortfolioCalculatorTest.kt::default cash whitelist is usdt only and anchor set is fixed to usdt`；`[UI]settings/SettingsPageUiTest.kt::whitelist add and remove flow` | ✅ |
| TC-F6.1-3 | §7.2-6.1 主题切换 | 明亮/黑暗一键切换 | 顶栏主题按钮与设置页主题段各切一次 | 主题状态切换且渲染不变形 | `[UI]ShellUiTest.kt::theme toggle switches between light and dark`；`[UI]settings/SettingsPageUiTest.kt::theme segment updates shell viewmodel state`；`[UI]ContrastTest.kt::light theme tokens meet WCAG AA on both backgrounds` | ✅ |
| TC-F6.1-4 | §7.2-6.1 盈亏颜色方案 | 绿涨红跌/红涨绿跌/色盲友好（蓝涨橙跌）+ 强制 +/- | 设置页 `pnl-select` 依次选三档，观察盈亏数值颜色 | 三档分别生效（色盲档 gain=#3B6FD4 / loss=#C77B28） | 邻接覆盖：`[UI]settings/SettingsPageUiTest.kt::all groups render on single page`（选择器所在页）、`[UI]ShellUiTest.kt::theme toggle switches between light and dark`（同类 shell 级方案切换）；实现已有（`PnlColorScheme` + `ColorTokens.withPnlScheme`）**但零断言 → 补 Compose UI（切档断言颜色 token）+ unit（映射）** | 🟡 |
| TC-F6.1-5 | §7.2-6.1 默认精度 | 金额/价格 8 位、市值/盈亏/百分比 2 位，可调 | 切精度预设为简化档；看微价币显示 | 预设驱动全局格式化；微价自适应有效位 | `[UI]settings/SettingsPageUiTest.kt::precision preset drives the shared formatter`；`[UI]i18n/WzFormatTest.kt::price keeps default eight decimals and trims trailing zeros`、`::simplified preset narrows prices to two decimals`、`::tiny price auto-increases significant digits instead of showing zero` | ✅ |
| TC-F6.1-6 | §7.2-6.1 用户名枚举开关 | 默认开；关闭后登录页改纯手动输入 | 关开关→回登录页；开启→回登录页 | 关闭后无用户名下拉、仅手动输入；开启时可选已有账户 | `[D]settings/GeneralSettingsServiceTest.kt::defaults are returned before any write`；`[UI]settings/SettingsPageUiTest.kt::username enum and proxy switches persist via service`（**关闭后的登录页行为无断言 → 补 Compose UI**） | 🟡 |
| TC-F6.1-7 | §7.2-6.1 托盘与后台 | 关窗最小化到托盘/开机自启(默认关)/同步通知开关 | 三开关各切一次；关窗 | 写入持久化；托盘不可用时置灰并说明 | `[APP]tray/DesktopBehaviorTest.kt::close hides to tray only when supported and switch on`、`::sync notification honours its switch`；`[D]settings/DefaultDesktopSettingsServiceTest.kt::defaults follow prd before any write`、`::autostart registers before persisting the key`；`[UI]settings/SettingsPageUiTest.kt::tray minimize switch writes through`、`::autostart toggle registers through service`、`::unavailable platform greys out the row and explains why` | ✅ |
| TC-F6.1-8 | §7.2-6.1 备份提醒 | 开关默认开；超 30 天提示「建议备份」 | 造 31 天未备份；造 20 天 | 31 天提示且含天数；20 天不提示 | `[DOM]schedule/BackgroundScheduleTest.kt::backup reminder fires only past 30 days`；`[UI]shell/ShellStatusViewModelTest.kt::backup reminder is shown when due`；`[APP]tray/DesktopNoticeTextTest.kt::backup reminder states the elapsed days`；`[UI]settings/SettingsPageUiTest.kt::tray backup reminder switch writes through` | ✅ |
| TC-F6.1-9 | §7.2-6.1 API 同步间隔 | 15/30/60（默认 30） | 设置页选 15 | 持久化并驱动调度 | `[DOM]exchange/ExchangeSyncPolicyTest.kt::cadence allows 15 30 60 and defaults 30`；`[UI]settings/SettingsPageUiTest.kt::api sync interval row lives in market sync group`；`[D]schedule/BackgroundSchedulerTest.kt::sync delay uses configured interval with clamp` | ✅ |
| TC-F6.1-10 | §7.2-6.1 行情刷新频率 | 5/15/30（默认 5）+ 托盘降频 + 额度 80% 自动降档 | 设 15；托盘驻留；额度 80% | 可见按 15；托盘按同步间隔；80% 自动降一档并提示 | `[DOM]market/RefreshCadenceTest.kt::quota downgrade bumps visible frequency one step`；`[UI]market/MarketSettingsSectionUiTest.kt::frequency selection persists via settings service`；`[D]schedule/BackgroundSchedulerTest.kt::quota at 80 percent bumps frequency down one notch` | ✅ |
| TC-F6.1-11 | §7.2-6.1 CG API Key | 可选；默认 keyless 开箱即用，填写即切专属额度并即时生效 | 无 Key 刷新→保存 Key→移除 | 默认 keyless；保存后立即切专属；移除回 keyless | `[UI]market/MarketSettingsSectionUiTest.kt::page renders rows with keyless defaults and frequency markers`、`::cg key save applies immediately and remove returns to keyless`；`[D]market/MarketSettingsTest.kt::cg key save roundtrips encrypted and removal clears`、`::wrong device key cannot open the secret` | ✅ |
| TC-F6.1-12 | §7.2-6.1 CMC API Key（兜底） | 可选；未配置则无兜底，保持上次价格+时间戳 | 保存 CMC Key→移除；无 Key 时 CG 失败 | 保存/移除生效；无 Key 时保持上次价 | `[D]market/DefaultMarketRefreshServiceTest.kt::cg failure without cmc key reports error and keeps last-price semantics`（**CMC Key 的保存/移除路径无测试 → 补 data-layer + Compose UI**） | 🟡 |
| TC-F6.1-13 | §7.2-6.1 小额币种阈值 | 设置阈值，低于阈值合计归「其他」 | 设阈值 10000 | 低于阈值的分片合并为「其他」 | `[UI]settings/SettingsPageUiTest.kt::small threshold free input saves custom value`；`[UI]portfolio/PortfolioPagesUiTest.kt::distribution merges slices below the small threshold into other` | ✅ |
| TC-F6.2-1 | §7.2-6.2 网络设置 | 默认自动检测并使用系统代理 | 切代理开关；看状态栏 | 开关即时驱动请求走向；状态栏同步更新 | `[D]proxy/SystemProxyDetectorTest.kt::jdk system proxy wins over environment`、`::disabled switch skips detection entirely`；`[UI]settings/SettingsPageUiTest.kt::proxy switch drives runtime hook`；`[UI]ShellUiTest.kt::status bar proxy indicator follows proxy status` | ✅ |
| TC-F6.3-1 | §7.2-6.3 手续费设置 | 全局默认 + 交易所特定买卖费率 | 见 TC-A7.2-2/3 | 规则增删改与优先级正确 | `[UI]ledger/FeeRuleSettingsUiTest.kt::globalRatesSaveAndPersist`、`::exchangeRuleAddAndRemove`、`::invalidRateShowsError`；`[D]ledger/FeeRuleServiceTest.kt::globalAndExchangeRulesCrudAndResolverPriority` | ✅ |
| TC-F6.4-1 | §7.2-6.4 关于-版本/开发者/许可证 | 应用版本号、开发者信息、AGPL-3.0 | 打开设置→关于分组 | 版本串含 `· AGPL-3.0`；开发者信息可见 | `[UI]settings/SettingsPageUiTest.kt::all groups render on single page`（**仅断言「版本」二字；开发者/许可证文案无断言 → 补 Compose UI**） | 🟡 |
| TC-F6.4-2 | §7.2-6.4 关于-隐私政策/无遥测 | 隐私政策链接 + 「无遥测」声明 | 打开关于分组 | 显示无遥测声明与隐私政策入口 | 同上（"无遥测声明" 有断言）；`[D]security/SecurityGuardTest.kt::no telemetry analytics or crash reporting dependency is declared`（链接打开见 TC-MAN-10） | ✅ |
| TC-F6.4-3 | §7.2-6.4 关于-行情数据源说明 | 主源/兜底/不内置 Key/时间分辨率边界说明 | 打开关于分组看数据源说明 | 四项说明文案齐全 | 邻接覆盖：`[UI]settings/SettingsPageUiTest.kt::all groups render on single page`（`group-about` 可见性）；实现已有（`SettingsCopy.ABOUT_SOURCE_*`）**但零断言 → 补 Compose UI 文案断言** | 🟡 |
| TC-F6.4-4 | §12 更新策略 | 不内置「检查更新」（网络白名单不变） | 全仓扫描出站主机与依赖 | 仅 3 个业务主机；无更新检查端点；无遥测依赖 | `[D]security/SecurityGuardTest.kt::all outbound http hosts stay inside the allow list`、`::no plaintext http egress literal is present`、`::no telemetry analytics or crash reporting dependency is declared` | ✅ |

### 2.7 模块 7：账户管理

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-F7-1 | §7.2-7.1 账户创建 | 首启无账户引导创建（用户名/密码） | 空库启动 | 进入创建页 | `[UI]auth/AuthFlowUiTest.kt::first launch without accounts goes to create page`；`[D]accounts/AccountRepositoryTest.kt::migration creates accounts table and starts empty` | ✅ |
| TC-F7-2 | §7.2-7.2 账户登录 | 提供登录界面并校验用户名密码 | 错口令/不存在用户/正确口令 | 前两者同一错误（不暴露存在性）；正确进入主壳 | `[UI]auth/AuthFlowUiTest.kt::login empty password shows field error then login enters shell`；`[D]accounts/DefaultAccountServiceTest.kt::login failure is unified and does not leak existence` | ✅ |
| TC-F7-3 | §7.2-7.3 账户登出 | 提供登出并清除会话 | 见 TC-A1.2-2 | 会话与记住我清除 | `[UI]auth/AuthFlowUiTest.kt::account menu switch requires password and logout roundtrip works`；`[D]accounts/DefaultAccountServiceTest.kt::remember me roundtrip saves restores and logout clears` | ✅ |
| TC-F7-4 | §7.2-7.4 账户切换 | 列表切换 + 必须重输密码（严格模式） | 见 TC-A1.3-2/3 | 严格模式生效 | `[UI]auth/AuthFlowUiTest.kt::account menu switch requires password and logout roundtrip works`；`[D]accounts/DefaultAccountServiceTest.kt::switch requires target password and wipes old session remember entry` | ✅ |
| TC-F7-5 | §7.2-7.5 账户存储 | 用户名/哈希/KDF 参数/包裹 DEK 本地存储；密码不落盘 | 建账后读 accounts 行 | 字段齐备且无口令明文 | `[D]accounts/AccountRepositoryTest.kt::createWrapped inserts then wraps with real id and roundtrips`、`::updateCredentials rewrites only credential fields`；`[DOM]security/KdfParamsTest.kt::default matches frozen argon2id values and storage roundtrip` | ✅ |

### 2.8 模块 8：资金管理

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-F8-1 | §7.2-8.1 增资记录 | 表单记录增资（金额/日期/来源/备注） | 见 TC-A6.1-2 | 字段齐、保存成功 | `[UI]ledger/FundsPageUiTest.kt::depositModalFocusesCoinInputAndSavesViaKeyboard`；`[D]ledger/DefaultFundServiceTest.kt::depositWithUsdAnchoredStablecoinSavesAndFeedsOverview`（**日期/来源/备注无断言**） | 🟡 |
| TC-F8-2 | §7.2-8.2 撤资记录 | 表单记录撤资（金额/日期/去向/备注） | 见 TC-A6.2-2 | 字段齐、保存成功 | `[UI]ledger/FundsPageUiTest.kt::withdrawModalUsesWithdrawTitleAndKind`；`[D]ledger/DefaultFundServiceTest.kt::withdrawalBeyondPositionBlockedV7`（**去向/日期无断言**） | 🟡 |
| TC-F8-3 | §7.2-8.3 增资/撤资列表 | 显示全部记录，支持筛选与搜索 | 切类型/日期筛选并搜索 | 列表按条件过滤且字段齐全 | `[UI]ledger/FundsPageUiTest.kt::listRendersMixedRowsAndReconRowHasNoEdit`、`::typeAndDateFiltersToggleWithoutCrash`；`[D]ledger/DefaultFundServiceTest.kt::fundsListMergesReconciliationRowsAndFiltersApply`（**UI 只断言「切换不崩溃」→ 补 Compose UI 结果断言**） | 🟡 |
| TC-F8-4 | §7.2-8.4 投入本金计算 | 投入本金(净)=累计增资−累计撤资；另维护累计增资 | 见 TC-A6.1-4/TC-A6.2-4 | 两指标口径正确 | `[DOM]engine/GoldenCasesTest.kt::golden 2 …`、`::golden 4 …`；`[UI]ledger/FundsPageUiTest.kt::emptyStateShowsCommandAreaAndOverview` | ✅ |
| TC-F8-5 | §7.2-8.5 总收益与 ROI 展示 | 仪表盘与资产列表展示总收益与 ROI；0 增资显示"--" | 见 TC-A6.4-1/3 | 两页展示且口径一致 | `[DOM]engine/PortfolioCalculatorTest.kt::no deposits means total return and roi are null for dash display`；`[UI]portfolio/PortfolioPagesUiTest.kt::dashboard renders overview cards donut and popup on slice click`（**资产列表页 ROI 卡未断言**） | 🟡 |

### 2.9 模块 9：桌面体验（Tray & Background）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-F9.1-1 | §7.2-9.1 托盘驻留 | 关窗默认最小化到托盘，设置可改为退出；菜单含打开/同步/退出 | 关窗（开关开/关）；右键托盘图标看菜单 | 开关开→隐藏到托盘；开关关→退出；菜单三项可用 | 策略层 ✅：`[APP]tray/DesktopBehaviorTest.kt::close hides to tray only when supported and switch on`、`::close exits when tray is unavailable regardless of the switch`；**菜单三项与真实驻留需实机 → TC-MAN-01 🔵** | 🔵 |
| TC-F9.2-1 | §7.2-9.2 后台同步 | 托盘驻留期间按同步间隔执行；行情按同间隔降频 | 托盘驻留→等一个间隔 | 同步按间隔触发；行情按同步间隔降频 | `[D]schedule/BackgroundSchedulerTest.kt::market delay degrades to sync interval when tray resident`、`::window visibility transition is recorded`；`[DOM]market/RefreshCadenceTest.kt::tray mode degrades to the api sync interval`、`::tray cadence keeps the slower of downgraded frequency and sync interval` | ✅ |
| TC-F9.3-1 | §7.2-9.3 开机自启 | 提供开关（默认关闭），三平台注册/注销 | 开开关→查三平台自启项→关开关 | 三平台入口/参数正确；失败有中文提示且不写坏配置 | `[DOM]autostart/AutostartRulesTest.kt::relative entry path per platform`、`::windows run key targets hkcu to avoid admin rights`、`::mac plist declares label program arguments and run at load`、`::linux desktop entry declares xdg autostart keys`、`::resolve prefers jpackage app path`；`[D]autostart/PlatformAutostartServiceTest.kt::linux enable writes xdg desktop entry and disable removes it`、`::mac enable writes launch agent plist with executable and disable removes it`、`::windows enable invokes reg add with expected arguments`、`::unresolved executable path is unsupported and enable has no side effect`；`[D]settings/DefaultDesktopSettingsServiceTest.kt::reconcile re-registers when key on but registration missing`（**真实注册动作需实机 → TC-MAN-02**） | ✅ |
| TC-F9.4-1 | §7.2-9.4 同步通知 | 后台同步完成/失败发桌面通知，可关闭 | 同步成功/失败；关闭开关再试 | 按开关发/不发；行情与维护事件不发 | `[APP]tray/DesktopBehaviorTest.kt::sync notification honours its switch`、`::market and maintenance events never notify`、`::sync event with no keys yields no notice even when enabled`；`[APP]tray/DesktopNoticeTextTest.kt::success notice reports new trades and dedup`、`::success notice with nothing new stays informative`、`::partial failure is distinguished and counted`、`::all failed reports failure with reason`、`::overlong messages are truncated to protect the bubble` | ✅ |

### 2.10 P6 新增回归用例（DEF-01 / DEF-02 修复验证 + 登出调度加固）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-F5-3 | §7.2-5 备份 + 故事5.2（**DEF-01 已修复 · C0**） | 备份导出遇到不可解密 `api_keys` 凭证时**整体中止且不落文件**，并给出双语可操作文案 | 破坏库内某条密钥密文 → 执行导出 | 抛 `BackupExportException(CREDENTIAL_UNREADABLE)`；不生成 .cpro；文案点名问题密钥并引导编辑/移除后重试（zh/en 双档） | `[D]backup/DefaultBackupServiceTest.kt::export aborts with typed error when a stored credential cannot be decrypted`；`[UI]backup/BackupExportErrorCopyTest.kt::credential unreadable maps to actionable copy in both languages`、`::unknown export failure falls back to the original message`；实现：`domain/backup/BackupExportException.kt`（P5-4 登记项闭环） | ✅ |
| TC-F4.2-4 | §7.2-4.2 API 契约（**DEF-02 已修复 · C0**） | Binance 签名请求必须携带 `recvWindow` 契约参数（与 timestamp/signature 同级） | 触发一次 `validateCredentials` 签名请求，检查 URL 参数 | URL 含 `recvWindow=DEFAULT_RECV_WINDOW`、`timestamp`、`signature`；API Key 请求头正确 | `[D]exchange/BinanceAdapterTest.kt::validate credentials hits signed account endpoint with headers and signature`（含 `recvWindow` 断言）；实现：`data/exchange/BinanceAdapter.kt:177-179`（此前两常量声明未接线） | ✅ |
| TC-F9.2-2 | §7.2-9.2 后台同步（P6 加固） | 登出（无活动会话）后同步 tick 短路：不调用用例层、不发同步事件；行情循环不受影响 | 会话置空 → 触发同步 tick → 触发行情刷新 | `syncNow()` 返回空、用例层零调用、无 `SyncFinished` 事件；行情照常刷新 | `[D]schedule/BackgroundSchedulerTest.kt::sync tick is skipped without an active session`、`::sync tick runs normally with an active session`（P6 补测） | ✅ |

## §3 异常态用例 · interaction §1.1–1.4

### 3.1 网络异常（N1–N3）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-X-N1 | interaction N1 | 行情源不可连：状态栏红色断链+「网络断开」+上次成功时间戳；可点击手动刷新；保留上次数据 | 断开网络（或注入网络错误）后触发刷新 | 状态栏显示离线标记与上次成功时间戳；保留上次价格；点击图标可重试 | `[UI]shell/ShellStatusViewModelTest.kt::network error marks the status bar as offline`；`[D]market/MarketHttpClientTest.kt::network failure maps to network error`、`::network failure preserves the underlying cause for diagnostics`（**图标/文案/时间戳/点击刷新无断言 → 补 Compose UI；真机断网见 TC-MAN-05**） | 🟡 |
| TC-X-N2 | interaction N2 | 交易所 API 不可连：状态栏+API 列表「同步失败」+具体原因；可手动同步/编辑密钥；保留本地账本 | 用失效 Key 触发同步 | 状态栏与列表显示「同步失败：Binance API 密钥已失效，请检查或更新」；本地账本不变 | `[UI]shell/ShellStatusViewModelTest.kt::failed sync surfaces the redacted reason`；`[D]exchange/DefaultExchangeSyncServiceTest.kt::invalid credentials marks failed and sync log message is redacted`；`[UI]exchange/ApiManagementSectionUiTest.kt::invalid credentials test surfaces B2 copy and does not save`；`[UI]exchange/TopBarSyncViewModelTest.kt::failedKeySurfacesPartialFailure` | ✅ |
| TC-X-N3 | interaction N3 | 离线记录折算：标「待定价」+「估算中」，联网自动回填重算 | 离线保存一笔增资/买入→联网触发回填 | 记录标 PENDING 且行显示「估算中」；回填后重算并消除标注 | `[D]ledger/DefaultFundServiceTest.kt::offlineDepositMarkedPendingAndBackfillCorrectsValue`；`[DOM]engine/GoldenCasesTest.kt::golden 9 - pending fee price marks estimates then backfill corrects and clears markers`；`[D]portfolio/DefaultPortfolioServiceTest.kt::pendingConversionMarksSnapshotAndRowEstimated`；`[D]ledger/TransactionEventBuilderTest.kt::usesNearestBeforeSnapshotAndMarksAfterFallbackEstimated` | ✅ |

### 3.2 后台服务异常（B1–B5）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-X-B1 | interaction B1 | 数据文件损坏/不可读：全屏错误框，引导从备份恢复 | 人为破坏库文件头后启动应用 | 显示「数据文件损坏或无法读取，请从备份恢复或联系技术支持」并提供恢复入口 | **行为缺失**：现仅有密钥文件损坏的启动提示（`app/Main.kt`）与备份文件损坏提示；无「库损坏 → 全屏错误框」实现与用例 → **登记 P8**（或转实现待办，归 `defects.md` 之外的 P8 项） | ⬜ |
| TC-X-B2 | interaction B2 | 交易所 Key 失效：状态栏/API 列表/同步结果给具体文案 | 见 TC-X-N2 | 三处一致的具体失效提示 | `[UI]shell/ShellStatusViewModelTest.kt::failed sync surfaces the redacted reason`；`[UI]exchange/ApiManagementSectionUiTest.kt::invalid credentials test surfaces B2 copy and does not save`；`[D]exchange/BinanceAdapterTest.kt::invalid key maps to InvalidKey` | ✅ |
| TC-X-B3 | interaction B3 | CoinGecko 额度耗尽/达月上限：状态栏提示+自动降频 | 配额计到 85% 并触发刷新 | 状态栏提示含 80% 与「已自动降频」；刷新频率降一档 | `[UI]shell/ShellStatusViewModelTest.kt::quota at eighty percent warns and wins over the backup reminder`；`[D]schedule/BackgroundSchedulerTest.kt::quota at 80 percent bumps frequency down one notch`（**100% 耗尽分支与逐字文案无断言 → 补 unit + Compose UI；额度口径问题见 TC-X-2.5-4/DEF-04**） | 🟡 |
| TC-X-B4 | interaction B4 | 无 Key 模式共享限流：提示注册个人 Key + 自动退避 | 无 Key 连续触发 429 | 提示「注册免费个人 Key 可获专属额度」；退避窗口内不再打请求 | `[UI]shell/ShellStatusViewModelTest.kt::shared rate limit hint appears when the keyless api is throttled`；`[UI]market/MarketSettingsSectionUiTest.kt::manual refresh with rate limit error surfaces keyless hint toast`；`[D]market/MarketHttpClientTest.kt::cg rate limited maps with keyless hint`；`[D]schedule/BackgroundSchedulerTest.kt::repeated 429 emits frequent hint and keeps backoff window`、`::backoff window blocks premature kick and expires`；`[D]market/DefaultMarketHistoryBackfillServiceTest.kt::backfill retries once on rate limit and records history quota` | ✅ |
| TC-X-B5 | interaction B5 | 兜底 CMC 失败：保持上次价格 + 上次成功时间戳 | CG 429 → CMC 也 429（两级都失败） | 保持上次价格、显示上次成功时间戳、不写入坏快照 | `[D]market/MarketHttpClientTest.kt::cmc maps 402 to quota exceeded and 429 to rate limited`；`[D]market/DefaultMarketRefreshServiceTest.kt::cg failure without cmc key reports error and keeps last-price semantics`（**「CMC 已配置但兜底也失败」组合分支无用例 → 补 data-layer**） | 🟡 |

### 3.3 账户操作异常（A1–A4）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-X-A1 | interaction A1 | 登录失败「用户名或密码错误」，不暴露用户是否存在 | 错口令登录 + 不存在用户名登录 | 两者同一类型化错误与同一文案 | `[D]accounts/DefaultAccountServiceTest.kt::login failure is unified and does not leak existence`；`[APP]integration/ErrorPathIntegrationTest.kt::错误口令 登录失败不暴露账户存在性（A1）`；`[UI]auth/AuthFlowUiTest.kt::login empty password shows field error then login enters shell` | ✅ |
| TC-X-A2 | interaction A2 | 改密原密码错误提示「原密码不正确」 | 改密弹窗输错原密码 | 显示「原密码不正确」，不改密、不作废令牌 | `[D]accounts/DefaultAccountServiceTest.kt::change password invalidates token and old password and rewraps same dek`（服务层 `OldPasswordMismatchException` 已覆盖；**UI 文案 `AuthCopy.errA2OldPassword` 无断言 → 补 Compose UI**） | 🟡 |
| TC-X-A3 | interaction A3 | 切换账户加载失败提示「账户数据加载失败，请重试」 | 构造目标账户数据不可解密后切换 | 提示「账户数据加载失败，请重试」，停留原账户 | 仅有密码错误分支：`[D]accounts/DefaultAccountServiceTest.kt::switch requires target password and wipes old session remember entry`（**「切换后加载/解密失败」分支无实现级用例，UI 文案 `errA3AccountLoad` 无断言 → 补 data-layer + Compose UI**） | 🟡 |
| TC-X-A4 | interaction A4 | 忘记密码入口弹层与不可恢复文案 | 见 TC-A1.1-7 | 显示不可恢复提示 | `[UI]auth/AuthFlowUiTest.kt::forgot page shows A4 copy and returns to login` | ✅ |

### 3.4 数据校验异常（V1–V9）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-X-V1 | interaction V1 | 价格/数量必须 > 0，红色错误并阻止提交 | 空值、0、−1 三种输入分别保存 | 空值报必填；0/−1 报「必须大于 0」；均不落库 | `[UI]ledger/TransactionsPageUiTest.kt::emptyFormShowsValidationErrorsAndDoesNotTouchService`（空值）、`::zeroAndNegativeAmountsAreRejectedByV1V2Rules`（`V1_PRICE`/`V1_QTY` 覆盖 0 与负数；P6 补测） | ✅ |
| TC-X-V2 | interaction V2 | 手续费 ≥ 0，否则红色错误 | 手续费填 0、5、−1 | 0/5 可保存；−1 报错 | `[UI]ledger/TransactionsPageUiTest.kt::feeZeroIsAllowedAndSaveReachesService`（0 合法）、`::zeroAndNegativeAmountsAreRejectedByV1V2Rules`（−0.5 报 `V2_FEE`；P6 补测） | ✅ |
| TC-X-V3 | interaction V3 | 手续费币种选「自定义」时必须填币种代码 | 选自定义币种但不填代码保存 | 字段下方红色错误、阻止提交 | `[UI]ledger/TransactionsPageUiTest.kt::customFeeRoleRequiresFeeCurrency`（选自定义币种留空 → `V3_FEE_CURRENCY` 且不触达服务；P6 补测）；实现：`TransactionFormModal` `FEE_CUSTOM` 错误位 + `CUSTOM_FEE_HINT` | ✅ |
| TC-X-V4 | interaction V4 | 交易对/交易时间/必填项非空 | 空表单保存 | 逐字段红色错误、不触达服务 | `[UI]ledger/TransactionsPageUiTest.kt::emptyFormShowsValidationErrorsAndDoesNotTouchService`（`V4_REQUIRED`）；`[UI]ledger/FundsPageUiTest.kt::emptyFormShowsV6ErrorsAndDoesNotTouchService`（`V6_COIN_REQUIRED`） | ✅ |
| TC-X-V5 | interaction V5 | 买入余额不足阻止保存并给可操作文案 | 无本金买入 | 表单下方提示「USDT 余额不足…请先记录转入（增资）」 | `[D]ledger/DefaultTransactionLedgerServiceTest.kt::manualBuyWithoutQuoteBalanceBlockedV5`；`[UI]ledger/ValidationCopyTest.kt::insufficientBalanceCopyIsActionable`；`[APP]integration/ErrorPathIntegrationTest.kt::买入余额不足 撤资超额 删除增资 三类重放校验在真实库上类型化上浮` | ✅ |
| TC-X-V6 | interaction V6 | 增资/撤资数量为正数 | 资金表单填 0/−1 保存 | 报错并阻止提交 | `[UI]ledger/FundsPageUiTest.kt::emptyFormShowsV6ErrorsAndDoesNotTouchService`（**仅必填/格式；0/负数无用例 → 补 Compose UI**） | 🟡 |
| TC-X-V7 | interaction V7 | 撤资持仓不足提示「XX 持仓不足，无法撤资」 | 持仓 100 撤 150 | 阻止保存并给持仓不足文案 | `[D]ledger/DefaultFundServiceTest.kt::withdrawalBeyondPositionBlockedV7`、`::withdrawalDeepeningImportedNegativeStillBlockedV7`；`[DOM]engine/ReplayEngineTest.kt::strict replay blocks withdrawal beyond holdings`（**UI 文案逐字与临界值（恰好等于）无断言 → 补 Compose UI**） | 🟡 |
| TC-X-V8 | interaction V8 | 创建账户：用户名非空唯一、密码强度、两次一致 | 重名/弱密码/两次不一致各试一次 | 三项均报错并阻止创建 | `[DOM]accounts/AccountPolicyTest.kt::username validity`、`::minimum gate requires 8 plus letter and digit`；`[D]accounts/DefaultAccountServiceTest.kt::duplicate username rejected and weak password rejected at service layer`；`[UI]auth/AuthFlowUiTest.kt::create requires risk confirm hard gate then wizard then shell via later`（**「两次一致」UI 校验无断言 → 补 Compose UI**） | 🟡 |
| TC-X-V9 | interaction V9 | 编辑/删除导致任一时刻负持仓 → 阻止并提示冲突原因 | 增资 100→买 1 BTC→把增资改 50；删除该增资 | 均被阻止，提示定位到具体冲突记录 | `[DOM]engine/ReplayEngineTest.kt::validateMutation blocks a new trade that would create a new negative boundary`；`[DOM]ledger/ReplayConflictClassifierTest.kt::buyViolatingQuoteCoinAtOwnEventMapsToInsufficientBalance`、`::sellViolatingBaseCoinAtOwnEventMapsToInsufficientPosition`、`::violationAtAnotherEventOrNonOwnRoleCoinMapsToReplayConflict`；`[D]ledger/DefaultFundServiceTest.kt::shrinkingDepositBreaksLaterBuyBlockedV9`、`::deletingDepositBlockedV9`；`[UI]ledger/ValidationCopyTest.kt::replayConflictCopyNamesTheConflictingRecord`；`[APP]integration/ErrorPathIntegrationTest.kt::买入余额不足 撤资超额 删除增资 三类重放校验在真实库上类型化上浮` | ✅ |

## §4 加载 / 空 / 错误 / 离线 / 限流态用例 · interaction §2.1–2.8

### 4.1 §2.1 加载态

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-X-2.1-1 | §2.1 登录解密 | 登录按钮 loading +「正在解密…」，预算 ≤2 秒 | 输入口令点登录；低配机（4G/双核）计时 | 登录期间按钮 loading 且文案「正在解密…」；解密 ≤2 秒 | 邻接覆盖：`[UI]auth/AuthFlowUiTest.kt::login empty password shows field error then login enters shell`（登录路径，未断言 loading）；实现已有（`AuthStrings.loginLoading`）**但零断言 → 补 Compose UI；耗时预算见 TC-MAN-04 🔵** | 🟡 |
| TC-X-2.1-2 | §2.1 行情刷新 | 价格旁轻微 loading，不阻塞 UI | 触发刷新，同时操作界面 | 价格旁出现轻 loading；界面可继续交互 | **行为缺失**：主源码无 `ProgressIndicator` 类实现 → **登记 P8（或转实现待办）** | ⬜ |
| TC-X-2.1-3 | §2.1 API 同步 | 状态栏「同步中」+列表 loading，后台执行 | 点顶栏同步；同步期间编辑 | 状态栏显示「同步中…」；前台编辑不被阻塞 | `[UI]shell/ShellStatusViewModelTest.kt::manual sync in flight takes precedence over the last result`；`[UI]ShellUiTest.kt::top bar manual sync button invokes callback and reflects syncing state`（**同步列表 loading 与并发编辑无断言 → 补 Compose UI + integration**） | 🟡 |
| TC-X-2.1-4 | §2.1 CSV 解析 | 进度条 + 影响摘要预览 | 选大 CSV 导入 | 解析过程有进度反馈；完成后显示影响摘要 | `[UI]ledger/TransactionsPageUiTest.kt::csvWizardParsesPreviewsAndConfirms`（预览已覆盖；**进度条无断言/未实现 → 补 Compose UI 或登记 P8**） | 🟡 |
| TC-X-2.1-5 | §2.1 备份/恢复 | 进度条 | 导出/恢复大备份 | 过程有进度提示 | **行为缺失**：无进度组件实现 → **登记 P8（或转实现待办）** | ⬜ |
| TC-X-2.1-6 | §2.1 列表滚动加载 | 底部 loading 占位 | 滚动交易/资金列表至底部 | 无分页/loading 占位（本地库单次装载 + `LazyColumn` 虚拟化） | ✅ **DEF-05 已按 C0 口径澄清**（人工裁决 2026-09-14）：`interaction.md §2.1/§3-2` 已注明本地库单次装载 + 虚拟化渲染、不适用分页；大数据量装载耗时为 P8 观察项 | ✅ |

### 4.2 §2.2 空态

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-X-2.2-1 | §2.2 无持仓资产 | 占位图 +「暂无记录」 | 空账户打开资产列表 | 显示空态占位与文案 | 邻接覆盖：`[D]portfolio/DefaultPortfolioServiceTest.kt::emptyLedgerYieldsNeutralSnapshot`、`[UI]portfolio/PortfolioPagesUiTest.kt::assets page marks unpriced rows and opens coin detail on row click`（均为非空数据）；实现已有（`AssetsPage` `assets-empty`）**→ 补 Compose UI 空态用例** | 🟡 |
| TC-X-2.2-2 | §2.2 无交易记录 | 占位图 +「暂无记录」 | 空账户打开交易页 | 显示空态文案与添加入口 | `[UI]ledger/TransactionsPageUiTest.kt::emptyStateShowsAddButtonAndEmptyText` | ✅ |
| TC-X-2.2-3 | §2.2 无资金流水 | 占位图 +「暂无记录」 | 空账户打开资金页 | 显示空态与命令区 | `[UI]ledger/FundsPageUiTest.kt::emptyStateShowsCommandAreaAndOverview` | ✅ |
| TC-X-2.2-4 | §2.2 无 API 密钥 | 「尚未添加 API 密钥」+添加引导 | 空账户打开 API 管理 | 空态 + 添加按钮 + 同步引导 | `[UI]exchange/ApiManagementSectionUiTest.kt::empty state shows add button and no key rows`、`::sync all with no keys shows guidance toast` | ✅ |
| TC-X-2.2-5 | §2.2 累计增资为 0 | ROI 显示"--"+「请先记录增资」 | 空账户看仪表盘 ROI 卡 | 「--」显示；卡片旁提示「请先记录增资」 | 「--」已覆盖：`[DOM]engine/PortfolioCalculatorTest.kt::no deposits means total return and roi are null for dash display`、`[UI]i18n/WzFormatTest.kt::missing values render as dash`；**提示文案未实现 → 登记 P8** | ⬜ |
| TC-X-2.2-6 | §2.2 24h 前价格全缺失 | 24h 盈亏显示"--" | 全部持仓币无 24h 前价 | 显示「--」 | `[DOM]market/TwentyFourHourTest.kt::golden 11 - all coins without 24h price yield nulls (display dash)`；`[UI]portfolio/PortfolioPagesUiTest.kt::twenty four hour subtitle carries coverage and mixed source notes` | ✅ |

### 4.3 §2.3 错误态 / 降级态

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-X-2.3-1 | §2.3 无数据字段 | 统一显示"--" | 造无均价/无现价/无盈亏数据 | 字段均显示「--」 | `[UI]i18n/WzFormatTest.kt::missing values render as dash` | ✅ |
| TC-X-2.3-2 | §2.3 无行情币种 | 不计入总市值，列表显示「无行情」 | 造目录未收录/无快照币 | 该行显示「无行情」且不计入净值 | `[D]portfolio/DefaultPortfolioServiceTest.kt::unpricedCoinsGoLastOrderedByNameAndStayOutOfNetValue`；`[UI]portfolio/PortfolioPagesUiTest.kt::assets page marks unpriced rows and opens coin detail on row click`；`[UI]market/MarketWatchPageUiTest.kt::default seed renders four cash rows and price dash for unpriced` | ✅ |
| TC-X-2.3-3 | §2.3 24h 部分缺价 | 剔除缺失并标注「覆盖 N/M」「混合数据源」 | 10 币中 8 有 24h 前价；异源配对 | 标注「覆盖 8/10 个币种」与「混合数据源」 | `[DOM]market/TwentyFourHourTest.kt::golden 11 - partial missing 24h prices are excluded and coverage is 8 of 10`、`::mixed source pairs are annotated`；`[UI]portfolio/PortfolioPagesUiTest.kt::twenty four hour subtitle carries coverage and mixed source notes` | ✅ |
| TC-X-2.3-4 | §2.3 CMC 兜底标注 | 价格旁标注「数据源：CoinMarketCap」 | CG 失败走 CMC 兜底 | 状态栏/设置页与价格旁标注 CMC | `[UI]market/MarketSettingsSectionUiTest.kt::cmc fallback source indicator is displayed after refresh`；`[UI]shell/ShellStatusViewModelTest.kt::fallback source is labelled in the badge`（**看板价格旁的逐行标注无断言 → 补 Compose UI**） | ✅ |

### 4.4 §2.4 离线态

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-X-2.4-1 | §2.4 离线价格 | 保持上次价格 + 上次成功时间戳 | 断网后刷新 | 价格不变；显示上次成功时间戳 | `[D]market/DefaultMarketRefreshServiceTest.kt::cg failure without cmc key reports error and keeps last-price semantics`（**时间戳展示无断言 → 补 Compose UI**） | 🟡 |
| TC-X-2.4-2 | §2.4 离线记录折算 | 「待定价」估算 + 联网回填重算消除标注 | 见 TC-X-N3 | PENDING→回填→标注消除 | `[D]ledger/DefaultFundServiceTest.kt::offlineDepositMarkedPendingAndBackfillCorrectsValue`、`[DOM]engine/GoldenCasesTest.kt::golden 9 - pending fee price marks estimates then backfill corrects and clears markers`（同 TC-X-N3） | ✅ |
| TC-X-2.4-3 | §2.4 离线校准 | 行情不可用时禁止校准并提示 | 无价格快照且行情失败时点校准 | 阻止执行并提示原因 | `[D]ledger/CalibrationServiceTest.kt::missingMarketPriceBlocked`；`[DOM]engine/ReconciliationServiceTest.kt::price unavailable blocks reconciliation when delta is not zero`、`::zero delta plan needs no price`；`[UI]ledger/FundsPageUiTest.kt::calibrationBlockedReasonSurfacesError` | ✅ |

### 4.5 §2.5 429 限流提示

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-X-2.5-1 | §2.5 无 Key 429 | 「注册免费个人 Key 可获专属额度」 | 无 Key 频发 429 | 提示注册个人 Key | `[UI]shell/ShellStatusViewModelTest.kt::shared rate limit hint appears when the keyless api is throttled`、`[D]schedule/BackgroundSchedulerTest.kt::repeated 429 emits frequent hint and keeps backoff window`（同 TC-X-B4） | ✅ |
| TC-X-2.5-2 | §2.5 个人 Key 额度 80% | 「额度已达本月上限，已自动降频」+自动降一档 | 配额 85% 后刷新 | 提示 + 降一档 | `[UI]shell/ShellStatusViewModelTest.kt::quota at eighty percent warns and wins over the backup reminder`、`[D]schedule/BackgroundSchedulerTest.kt::quota at 80 percent bumps frequency down one notch`（同 TC-X-B3；**逐字文案与 100% 分支缺 → 补 Compose UI + unit**） | 🟡 |
| TC-X-2.5-3 | §2.5 CMC 429 | 保持上次价格 + 时间戳 | CMC 返回 429 | 保持上次价格并给时间戳 | `[D]market/MarketHttpClientTest.kt::cmc maps 402 to quota exceeded and 429 to rate limited`（**「保持上次价格+时间戳」组合无断言 → 补 data-layer**） | 🟡 |
| TC-X-2.5-4 | interaction §2.5 + 全局说明额度治理（**P6 新增/DEF-04**） | CMC 兜底调用不得计入 CoinGecko 月度额度账本 | 用尽 CG 配额后走 CMC 兜底若干次，观察 CG 月度计数与降档 | CG 月度计数不因 CMC 调用增长；不因兜底提前触发 80% 降档 | ⬜ **登记 P8**（人工裁决 2026-09-14：建议「登记 P8」获准——影响面小，仅同时配置两 Key 时可能提前降档；修正方案 = 账本增 provider 维度 + 旧载荷兼容，按 C1 在 P8 立项） | ⬜ |

### 4.6 §2.6 日志与诊断

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-X-2.6-1 | §2.6 查看日志 | 设置→日志与诊断可查看（账户名/金额脱敏） | 打开日志弹窗看内容 | 显示时间戳/类型/结果的脱敏日志 | `[UI]settings/SettingsPageUiTest.kt::view logs modal shows tail and export confirm gates export`；`[DOM]redaction/LogRedactorTest.kt::masks api key value in key-pair form`、`::masks provider-style key names`、`::masks long bare secrets such as hex db keys and signatures`、`::full hello style line contains no raw secret` | ✅ |
| TC-X-2.6-2 | §2.6 导出日志 | 导出前提示检查敏感信息 | 点导出→看提示→确认 | 弹出「导出前请检查是否包含敏感信息」；确认后才导出脱敏副本 | `[UI]settings/SettingsPageUiTest.kt::view logs modal shows tail and export confirm gates export`；`[D]logging/LogRotatorTest.kt::tailLines reads last N lines and exportTo writes redacted copy` | ✅ |
| TC-X-2.6-3 | §2.6 生成诊断报告 | 受限内容清单（版本/schema/脱敏日志/调用计数） | 点「生成诊断报告」 | 报告含应用/OS 版本、schema 版本、脱敏日志片段、同步与行情计数；不含密钥 | `[D]settings/DiagnosticsServiceTest.kt::report aggregates version schema counts and redacted log tail`、`::sync counts reflect sync_logs rows`；`[DOM]settings/DiagnosticsReportTextTest.kt::renders restricted content list fields`、`::empty log tail renders placeholder`；`[UI]settings/SettingsPageUiTest.kt::diagnostics report generates preview` | ✅ |
| TC-X-2.6-4 | §2.6 日志轮转 | 日志与 sync_logs 各留 1 万条或 90 天（先到为准） | 写入超限日志与超期行 | 按上限裁剪、按 90 天过期删除 | `[DOM]redaction/LogRotationPolicyTest.kt::keep last lines trims beyond limit`、`::keep last lines is no-op under limit`、`::expiry honours 90 days boundary`；`[D]logging/LogRotatorTest.kt::active log trimmed to last max lines`、`::expired files deleted by 90 days rule`、`::missing directory is a no-op`；`[D]settings/GeneralSettingsServiceTest.kt::rotate trims beyond max rows keeping newest`、`::rotate deletes rows older than 90 days` | ✅ |

### 4.7 §2.7 行情页（D21）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-X-2.7-1 | §2.7 加载态 | 手动/自动刷新期间行内轻 loading，不阻塞 | 点「立即刷新」 | 行内轻 loading；界面不阻塞 | `[UI]market/MarketWatchPageUiTest.kt::manual refresh triggers refresh service with visible coins`（刷新调用有断言；**loading 视觉无断言 → 补 Compose UI**） | 🟡 |
| TC-X-2.7-2 | §2.7 空态 | 自选被全部移除 →「暂无自选，搜索添加币种」 | 逐行移除全部自选 | 显示空态文案 | `[UI]market/MarketWatchPageUiTest.kt::remove action drops row and persists`（**空态文案未断言（实现已有 `watchEmpty`）→ 补 Compose UI**） | 🟡 |
| TC-X-2.7-3 | §2.7 无行情 | 无该 (coin,fiat) 快照 → 现价「--」+ 数据源「无行情」 | 造无快照币 | 现价列「--」，数据源标注「无行情」 | `[UI]market/MarketWatchPageUiTest.kt::default seed renders four cash rows and price dash for unpriced`（**「--」有断言；「无行情」数据源标识无断言 → 补 Compose UI**） | 🟡 |
| TC-X-2.7-4 | §2.7 数据源标注 | 现价行标注 COINGECKO / COINMARKETCAP | 分别用主源与兜底刷新 | 行内标注正确数据源 | `[UI]market/MarketSettingsSectionUiTest.kt::cmc fallback source indicator is displayed after refresh`（设置页徽章已覆盖；**行情页行级标注无断言 → 补 Compose UI**） | 🟡 |
| TC-X-2.7-5 | §2.7 离线态 | 保持上次快照价 + 显示上次成功时间戳 | 断网后打开行情页 | 价格为上次快照值 + 行内时间戳 | 邻接覆盖：`[D]market/DefaultMarketRefreshServiceTest.kt::cg failure without cmc key reports error and keeps last-price semantics`（同上层保价语义）；实现已有（`MarketWatchPage` 行内 `timeText(row.at)`）**但零断言 → 补 Compose UI** | 🟡 |
| TC-X-2.7-6 | §2.7 429 限流 | 复用 §2.5；额度 80% 自动降档提示随刷新结果可达 | 行情页触发 429 / 80% 配额 | 提示与 §2.5 一致且在行情页可达 | `[UI]market/MarketSettingsSectionUiTest.kt::manual refresh with rate limit error surfaces keyless hint toast`（设置页分组；**行情页内提示无断言 → 补 Compose UI**） | 🟡 |
| TC-X-2.7-7 | §2.7 搜索无结果 | 候选空 →「未找到匹配币种」 | 搜索不存在的币种 | 显示「未找到匹配币种」 | 邻接覆盖：`[D]catalog/SqlCoinCatalogTest.kt::search normalizes case and finds by symbol prefix and name`（搜索侧数据层）；实现已有候选空态文案（`MarketStrings`）**但零断言 → 补 Compose UI** | 🟡 |
| TC-X-2.7-8 | §2.7 自选上限 | 已达 50 → 阻止添加并提示「自选已达上限（50）」 | 加满 50 个后再添加 | 阻止并提示上限文案 | `[D]market/MarketWatchServicesTest.kt::add duplicate is idempotent and limit is enforced`（数据层上限已覆盖；**UI 阻止与提示文案无断言 → 补 Compose UI**） | 🟡 |
| TC-X-2.7-9 | §2.7 输入聚焦 | 搜索输入框打开即聚焦（AGENTS §7.3-②） | 进入行情页 | 搜索框自动获得焦点，键盘可直接录入 | 邻接覆盖：`[UI]market/MarketWatchPageUiTest.kt::search candidate can be added into the watch list`（同搜索框，未断言聚焦）；实现已有（`MarketWatchPage` `searchFocus` FocusRequester）**→ 补 Compose UI（`assertIsFocused`）** | 🟡 |

### 4.8 §2.8 持仓异常币的净值口径（D29）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-X-2.8-1 | §2.8 净值/可用现金 | 负持仓币市值不计入净值与可用现金 | CSV 导入卖出无持仓币 | 净值与可用现金均排除该币负市值 | `[DOM]engine/PortfolioCalculatorTest.kt::anomalous negative holding is excluded from net value and reported separately`、`::anomalous negative cash coin is excluded from available cash too`；`[D]portfolio/DefaultPortfolioServiceTest.kt::anomalousHoldingsAreSortedAndFlagged` | ✅ |
| TC-X-2.8-2 | §2.8 显式提示 | 仪表盘警示行 + 被排除金额 | 看仪表盘顶部 | 显示 `dashboard-anomaly-notice` 与「−$X 未计入净值与可用现金」 | `[UI]portfolio/PortfolioPagesUiTest.kt::dashboard shows the excluded anomalous market value notice` | ✅ |
| TC-X-2.8-3 | §2.8 行内标记 | 资产列表/详情展示负市值 +「持仓异常」徽标 | 打开资产列表 | 该行显示负市值与「持仓异常」；曾转负币标「成本不可靠」 | `[UI]portfolio/PortfolioPagesUiTest.kt::assets page marks unpriced rows and opens coin detail on row click`（`anomaly-tether`）；`[D]portfolio/DefaultPortfolioServiceTest.kt::coins with a negative history are flagged as unreliable cost basis` | ✅ |
| TC-X-2.8-4 | §2.8 恢复路径 | 补录增资/补导入/校准后自动重新计入 | 导入负持仓→补录增资 | 异常消除、市值自动重新计入 | `[DOM]engine/GoldenCasesTest.kt::golden 6 - csv import negative position is anomalous then backdated deposit clears it`、`::golden 6b - deposit recorded after the csv sell is not blocked by the pre-existing dip`；`[DOM]engine/ReplayEngineTest.kt::d26 negative position is fully repaid without building cost`、`::d26 crossing zero splits the inflow by quantity`、`::d26 deposit into a negative position also splits at the crossing point`、`::d26 anchored quantity zero forces cost to zero`、`::d26 walkthrough scenario rebuids a clean basis after the short` | ✅ |
| TC-X-2.8-5 | §2.8 手动路径 | 手动编辑被 V5/V7/V9 阻止，不会新造负持仓 | 手动新增/编辑/删除触发负持仓 | 一律阻止；已有负持仓时的无关操作放行 | `[DOM]engine/ReplayEngineTest.kt::validateMutation allows unrelated operations while a dip exists earlier`、`::worsening an already negative boundary at the same event position stays allowed permissive`、`::validateMutation allows deleting an imported sell that caused the dip` | ✅ |

### 4.9 交互细节要点（interaction §3）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-X-3-1 | §3-① 列表默认排序 | 资产按市值降序；交易/资金按时间降序 | 打开三个列表看首行 | 资产首行=最大市值；交易/资金首行=最新 | `[UI]portfolio/PortfolioPagesUiTest.kt::default sort is market value descending and header click flips direction`；`[D]portfolio/DefaultPortfolioServiceTest.kt::rowsAreMarketValueDescendingWithCatalogFieldsAndShares`（**交易/资金列表时间倒序无 UI 断言 → 补 Compose UI**） | 🟡 |
| TC-X-3-2 | §3-② 滚动加载 | 资产/交易/资金/详情列表滚动加载 | 滚动列表到底 | 虚拟化滚动（无分页占位） | ✅ **DEF-05 已按 C0 口径澄清**（同 TC-X-2.1-6，`interaction.md §3-2` 已回写） | ✅ |
| TC-X-3-3 | §3-③ 数值精度 | 金额价格 8 位、市值盈亏 2 位、百分比 2 位；微价自适应 | 输入 1.2345e-8 等极端值 | 微价提升有效位不显示 0；金额两位分组 | `[UI]i18n/WzFormatTest.kt::price keeps default eight decimals and trims trailing zeros`、`::amounts use two decimals with grouping`、`::tiny price auto-increases significant digits instead of showing zero` | ✅ |
| TC-X-3-4 | §3-④ 正负号 | 盈亏强制 +/- 符号，颜色仅辅助 | 看盈利/亏损数值 | 均带显式 +/- 符号 | `[UI]i18n/WzFormatTest.kt::pnl values always carry an explicit sign`；`[UI]portfolio/PortfolioPagesUiTest.kt::twenty four hour subtitle carries coverage and mixed source notes`；`[UI]ledger/TransactionsPageUiTest.kt::listRendersSellRealizedAndDeleteConfirmFlow` | ✅ |
| TC-X-3-5 | §3-⑤ 单击与浮窗 | 单行→详情；饼图扇区→高亮+浮窗 | 点资产行；点扇区 | 打开详情；浮窗显示名称/数量/占比/市值 | `[UI]portfolio/PortfolioPagesUiTest.kt::assets page marks unpriced rows and opens coin detail on row click`、`::dashboard renders overview cards donut and popup on slice click` | ✅ |
| TC-X-3-6 | §3-⑥ 删除确认 | 删除交易/资金需确认框，删除后全量重放且不可撤销 | 多选→删除→确认 | 确认框含「将删除 N 条…并重算…」；确认后执行 | `[UI]ledger/TransactionsPageUiTest.kt::listRendersSellRealizedAndDeleteConfirmFlow`；`[UI]ledger/FundsPageUiTest.kt::deleteConfirmFlowReachesService` | ✅ |
| TC-X-3-7 | §3-⑦ 剪贴板提示 | API 保存后提示清理系统剪贴板；提示关联当前账户 | 保存 API 密钥看 toast | toast 含「请清理系统剪贴板」与「关联当前登录账户」 | 邻接覆盖：`[UI]exchange/ApiManagementSectionUiTest.kt::add modal focuses alias input and save triggers first sync toast`（保存 toast 已断言触发，未断言文案内容）；实现已有（`ui/i18n/ExchangeStrings.kt:179`（zh）/`:248`（en）`saveAndSyncToast` 含「请清理系统剪贴板」双语文案；`hintAccount` 关联提示）**→ 补 Compose UI 文案断言** | 🟡 |
| TC-X-3-8 | §3-⑧ 时区 | 本地时区输入、UTC 存储、显示转本地 | 录入本地时间→读库；看显示格式 | 库内 UTC；显示为本地时区且随语言格式 | `[D]ledger/CsvTradeParserTest.kt::parsesTemplateWithAliasHeadersAndUtc`；`[UI]i18n/WzFormatTest.kt::date time format follows the language while the zone stays local`、`::form date time is stable and parseable`（**手动录入本地时区→UTC 无显式断言 → 补 data-layer**） | ✅ |
| TC-X-3-9 | §3-⑨ 全键盘导航 | Tab 顺序合理、核心操作可达、读屏可读 | 仅用键盘完成：登录→增资→买币→导出备份 | 全流程无需鼠标；焦点顺序合理；读屏可朗读关键数据 | 部分自动化：`[UI]ledger/TransactionsPageUiTest.kt::addModalFocusesBaseInputAndSavesViaKeyboard`；`[UI]ledger/FundsPageUiTest.kt::depositModalFocusesCoinInputAndSavesViaKeyboard`；`[UI]backup/DataManagementSectionUiTest.kt::backupModalFocusesPasswordAndExportsViaKeyboard`；`[UI]exchange/ApiManagementSectionUiTest.kt::add modal focuses alias input and save triggers first sync toast`；`[UI]market/MarketSettingsSectionUiTest.kt::cg key save applies immediately and remove returns to keyless`（**仅「弹窗首输入框聚焦」；Tab 顺序/Esc/焦点陷阱无断言 → 补 Compose UI；纯键盘全流程见 TC-MAN-06 🔵**） | 🟡 |
| TC-X-3-10 | §3-⑩ 代理指示 | 状态栏常驻直连/系统代理 | 见 TC-A4.2-3 | 两种指示正确 | `[UI]ShellUiTest.kt::status bar proxy indicator follows proxy status` | ✅ |
| TC-X-3-11 | §3-⑪ 日志入口 | 设置→日志与诊断（脱敏与轮转） | 见 TC-X-2.6-1/2 | 入口可达、弹窗脱敏 | `[UI]settings/SettingsPageUiTest.kt::view logs modal shows tail and export confirm gates export` | ✅ |

### 4.10 设计与体验原则（PRD §6）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-UX-1 | §6 数据优先 | 关键数据（总资产/盈亏/ROI）显眼且易理解 | 打开仪表盘人工观察 | 关键指标在首屏显眼位置、层级清晰 | `[UI]portfolio/PortfolioPagesUiTest.kt::dashboard renders overview cards donut and popup on slice click`（结构存在性已断言；**「显眼」为视觉主观项 → TC-MAN-08 🔵**） | 🟡 |
| TC-UX-2 | §6 简洁直观 | 核心操作路径不超过三层 | 从主界面数：增资/买币/导出备份各自点击层数 | 每个核心操作 ≤3 层可达 | **无法自动化 → TC-MAN-08（IA 走查）🔵** | 🔵 |
| TC-UX-3 | §6 安全感与信任 | 敏感操作区（API Key）明确安全提示 | 打开密钥弹窗看提示 | 显示只读/加密/关联账户/剪贴板四类提示 | `[UI]exchange/ApiManagementSectionUiTest.kt::invalid credentials test surfaces B2 copy and does not save`（仅错误文案；**安全提示文案无断言 → 补 Compose UI**） | 🟡 |
| TC-UX-4 | §6 主题定制 | 明亮/黑暗两主题，可一键切换 | 见 TC-F6.1-3 | 切换生效且对比度达标 | `[UI]ShellUiTest.kt::theme toggle switches between light and dark`；`[UI]settings/SettingsPageUiTest.kt::theme segment updates shell viewmodel state` | ✅ |
| TC-UX-5 | §6 响应时间 | 核心数据加载/刷新 ≤2 秒；后台任务异步不阻塞 | 低配机打开仪表盘/资产列表计时 | 首屏 ≤2 秒；刷新期间界面可交互 | **无性能断言 → TC-MAN-04（目标机）🔵 + 可选 integration 计时段言** | 🔵 |
| TC-UX-6 | §6 日志-记录 | 本地记录关键操作与错误日志（时间戳/类型/结果） | 操作后查看日志文件 | 日志含时间戳、操作类型、结果 | `[D]logging/LogRotatorTest.kt::tailLines reads last N lines and exportTo writes redacted copy`；`[D]HelloChainTest.kt::hello chain migrates reads settings and logs masked line`（**「操作类型/结果」字段结构无断言 → 补 unit**） | 🟡 |
| TC-UX-7 | §6 日志-导出 | 用户可查看/导出日志，导出前提示 | 见 TC-X-2.6-2 | 提示 + 脱敏导出 | `[UI]settings/SettingsPageUiTest.kt::view logs modal shows tail and export confirm gates export` | ✅ |
| TC-UX-8 | §6 日志-脱敏 | 禁止密钥明文/哈希/完整响应体；账户名与金额部分遮蔽 | 造含密钥/长串的日志行 | 输出全部遮蔽；普通文本不受影响 | `[DOM]redaction/LogRedactorTest.kt::masks api key value in key-pair form`、`::masks provider-style key names`、`::masks long bare secrets such as hex db keys and signatures`、`::keeps ordinary text intact`、`::long file paths are not mistaken for bare secrets`、`::full hello style line contains no raw secret`；`[APP]logging/RedactingMessageConverterTest.kt::key value pairs are masked without an explicit call site redaction`、`::bare secret shaped tokens are masked`、`::already redacted lines stay stable`、`::ordinary log lines pass through unchanged`；`[APP]logging/LogbackRedactionFunnelTest.kt::configured logback pattern routes every message through the redactor`；`[D]security/SecurityGuardTest.kt::logging has no network appenders` | ✅ |
| TC-UX-9 | §6 日志-轮转 | 日志与 sync_logs 各留 1 万条或 90 天 | 见 TC-X-2.6-4 | 超限裁剪、超期删除 | `[D]logging/LogRotatorTest.kt::active log trimmed to last max lines`、`[D]settings/GeneralSettingsServiceTest.kt::rotate trims beyond max rows keeping newest`（同 TC-X-2.6-4） | ✅ |
| TC-UX-10 | §6 诊断报告 | 内容限于版本/schema/脱敏日志/调用计数 | 见 TC-X-2.6-3 | 清单受限、无密钥 | `[D]settings/DiagnosticsServiceTest.kt::report aggregates version schema counts and redacted log tail`、`[DOM]settings/DiagnosticsReportTextTest.kt::renders restricted content list fields`（同 TC-X-2.6-3） | ✅ |
| TC-UX-11 | §6 账户清晰与隔离 | 明示当前账户；账户间数据完全隔离 | 见 TC-A1.3-6 / TC-A1.3-4 | 明示账户名；隔离成立 | `[UI]portfolio/PortfolioPagesUiTest.kt::dashboard renders overview cards donut and popup on slice click`；`[APP]integration/CoreJourneyIntegrationTest.kt::多账户隔离与跨账户恢复`；`[D]portfolio/DefaultPortfolioServiceTest.kt::snapshotIsScopedToActiveAccount` | ✅ |
| TC-UX-12 | §6 资金透明可审计 | 资金流动影响可查可追溯 | 打开资金列表与币种详情校准历史 | 增资/撤资/校准三类可查、含依据与金额 | `[UI]ledger/FundsPageUiTest.kt::listRendersMixedRowsAndReconRowHasNoEdit`；`[D]ledger/DefaultFundServiceTest.kt::fundsListMergesReconciliationRowsAndFiltersApply` | ✅ |
| TC-UX-13 | §6 I18N-多语言 | 中英双语可切换、即时生效、无漏译 | 切英文后浏览各页 | 全量英文、无中文残留、即时生效 | `[UI]i18n/I18nCatalogTest.kt::every catalogue entry is non blank in both languages`、`::english catalogue never contains cjk characters`、`::chinese catalogue is used when the language is chinese`、`::ui sources keep no inline chinese copy outside the i18n package`；`[UI]shell/ShellLanguageSwitchUiTest.kt::switching the language re-renders the shell immediately`、`::status bar and top bar copy switch language without waiting for a poll`、`::persisted language is applied on the next start`；`[UI]portfolio/PortfolioPagesUiTest.kt::language switch rerenders the aggregator pages in english` | ✅ |
| TC-UX-14 | §6 I18N-多法币 | 多法币单位显示 | 切 EUR/CNY 看金额展示 | 金额带对应法币符号/代码 | `[UI]i18n/WzFormatTest.kt::money prefixes the fiat currency`；`[D]portfolio/DefaultPortfolioServiceTest.kt::baseFiatSettingDrivesQuoteLookup`（**EUR/CNY 端到端展示无断言 → 补 Compose UI**） | 🟡 |
| TC-UX-15 | §6 a11y-符号 | 盈亏/涨跌强制 +/- 符号，颜色仅辅助 | 见 TC-X-3-4 | 符号恒存在 | `[UI]i18n/WzFormatTest.kt::pnl values always carry an explicit sign`（同 TC-X-3-4） | ✅ |
| TC-UX-16 | §6 a11y-色盲配色 | 提供「色盲友好（蓝涨橙跌）」配色选项 | 设置页切色盲档→看盈亏颜色 | 涨=蓝 #3B6FD4、跌=橙 #C77B28；对比度仍达 AA | 邻接覆盖：`[UI]settings/SettingsPageUiTest.kt::all groups render on single page`、`[UI]ShellUiTest.kt::theme toggle switches between light and dark`；实现已有（`ColorTokens.withPnlScheme` COLORBLIND）**但零断言 → 补 Compose UI（切档断言颜色）+ unit；对比度扩展见 TC-UX-19** | 🟡 |
| TC-UX-17 | §6 a11y-键盘导航 | 全键盘导航，Tab 顺序合理、核心操作可达 | 见 TC-X-3-9 | 见 TC-X-3-9 | `[UI]ledger/TransactionsPageUiTest.kt::addModalFocusesBaseInputAndSavesViaKeyboard`、`[UI]ledger/FundsPageUiTest.kt::depositModalFocusesCoinInputAndSavesViaKeyboard`、`[UI]backup/DataManagementSectionUiTest.kt::backupModalFocusesPasswordAndExportsViaKeyboard`（同 TC-X-3-9；**Tab 顺序/Esc/焦点陷阱无断言 → 补 Compose UI `performKeyInput`**） | 🟡 |
| TC-UX-18 | §6 a11y-读屏 | 关键数据提供可读文本标签，兼容 NVDA/JAWS | 用 NVDA（Windows）/JAWS 或 VoiceOver 朗读仪表盘、资产列表、弹窗 | 关键数据可被朗读且语义正确（环形图/状态/按钮角色） | 实现已有（`PortfolioParts.kt` 环形图 `contentDescription`+`stateDescription`、`WzToast` liveRegion、`WzSelect` role）**测试 0 引用语义树 → 补 Compose UI（semantics 断言）+ TC-MAN-03（NVDA/JAWS 实测）🔵** | 🔵 |
| TC-UX-19 | §6 a11y-对比度 | 文本与背景对比度满足 WCAG AA | 明暗两主题各取 token 计算对比度 | 正文对比度 ≥4.5:1 | `[UI]ContrastTest.kt::light theme tokens meet WCAG AA on both backgrounds`、`::dark theme tokens meet WCAG AA on both backgrounds`（**色盲档配色未纳入对比度测试 → 补 unit**） | ✅ |

## §5 计算口径黄金用例（PRD 附录 A）

> 全部 12 例已有自动化覆盖，基线口径 = 基础法币 USD、USDT 1:1 锚定。复跑即验，无需补测。

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-GC-1 | 附录A-1 建仓与已实现盈亏 | 买 1 BTC@50000 费 50 → 均价 50050；卖 0.5@60000 费 60 → 已实现 4915 | 引擎重放三条事件 | 持仓 0.5 BTC；均价 50050；已实现 4915；剩余成本 25025 | `[DOM]engine/GoldenCasesTest.kt::golden 1 - buy then sell realizes 4915 and keeps avg cost 50050`；`[D]ledger/SellRealizedTracerTest.kt::goldenCaseOneRealizedMatchesEngineTotal`、`::sumOfPerEventRealizedEqualsEngineForMixedSequence` | ✅ |
| TC-GC-2 | 附录A-2 增资撤资不产生盈亏 | 增资 100→撤资 100：本金回 0、无盈亏 | 重放增资+撤资 | 投入本金(净) 0；已实现/浮动盈亏 0 | `[DOM]engine/GoldenCasesTest.kt::golden 2 - deposit then withdrawal produces no pnl and net principal returns to zero`；`[D]ledger/DefaultFundServiceTest.kt::goldenCase2ShapeDepositThenWithdrawPrincipalBackToZero` | ✅ |
| TC-GC-3 | 附录A-3 撤资后 ROI | 增资 100→150→撤资 150→净值 0：总收益 50、ROI 50% | 重放含价格快照 | 总收益 50；ROI 50% | `[DOM]engine/GoldenCasesTest.kt::golden 3 - profitable withdrawal leaves return 50 and roi 50 percent`；`[D]ledger/DefaultFundServiceTest.kt::goldenCase3ShapeWithdrawalAfterProfit` | ✅ |
| TC-GC-4 | 附录A-4 盈利撤资再增资 | 总收益 50、ROI 25%（分母 200） | 重放 增资→撤资→再增资买入 | 总收益 50；ROI 25% | `[DOM]engine/GoldenCasesTest.kt::golden 4 - deposit again after profitable withdrawal keeps return 50 and roi 25 percent` | ✅ |
| TC-GC-5 | 附录A-5 第三币种手续费 | BNB 计费折算入成本并扣减 BNB 持仓 | BTC/USDT 买入用 BNB 计费 | 成本含折算费；BNB 持仓减少；不产生盈亏 | `[DOM]engine/GoldenCasesTest.kt::golden 5 - third currency fee is converted into cost and deducts bnb holdings`；`[DOM]engine/ReplayEngineTest.kt::third currency fee on sell deducts fee coin holding and fees net from realized` | ✅ |
| TC-GC-6 | 附录A-6 CSV 负持仓与补增资 | 导入卖出 1 BTC → −1 标异常、市值不计入；补增资后恢复 | CSV 导入 → 看板 → 补增资 → 再看板 | 异常标记与排除金额出现；补录后消除并重新计入 | `[DOM]engine/GoldenCasesTest.kt::golden 6 - csv import negative position is anomalous then backdated deposit clears it`、`::golden 6b - deposit recorded after the csv sell is not blocked by the pre-existing dip`；`[UI]portfolio/PortfolioPagesUiTest.kt::dashboard shows the excluded anomalous market value notice` | ✅ |
| TC-GC-7 | 附录A-7 编辑历史交易重放校验失败 | 增资 100→买入 1 BTC→改增资 50 → 阻止并提示 | 编辑增资为 50 保存 | 阻止保存；提示该笔增资已被后续买入消耗 | `[DOM]engine/GoldenCasesTest.kt::golden 7 - shrinking the deposit is blocked because the buy would go negative`；`[D]ledger/DefaultFundServiceTest.kt::shrinkingDepositBreaksLaterBuyBlockedV9`；`[D]ledger/DefaultTransactionLedgerServiceTest.kt::editingEarlierTradeThatBreaksLaterOneBlockedAsReplayConflict` | ✅ |
| TC-GC-8 | 附录A-8 校准负差额锚点 | 本地 1.0/交易所 0.8 → delta −0.2：按均价移出、按市价计撤资；恒等式成立；前序编辑不回滚 | 执行校准 → 校验恒等式 → 编辑校准前记录 | 总收益=净值+撤资−增资；锚点效果保留 | `[DOM]engine/GoldenCasesTest.kt::golden 8 - negative reconciliation keeps identity and anchor survives pre-anchor edits`；`[DOM]engine/ReconciliationServiceTest.kt::anchor from plan keeps identity under a positive delta`、`::plan computes delta and fiat value with direction`；`[D]ledger/CalibrationServiceTest.kt::prepareReturnsPlanAndExecuteWritesAnchorAndSyncLog` | ✅ |
| TC-GC-9 | 附录A-9 PENDING 回填前后 | 离线记录标待定价并估算；联网回填后重算并消除标注 | 离线保存买入 → 联网回填 | 估算标记出现→消失；成本与指标更新 | `[DOM]engine/GoldenCasesTest.kt::golden 9 - pending fee price marks estimates then backfill corrects and clears markers`；`[D]ledger/DefaultFundServiceTest.kt::offlineDepositMarkedPendingAndBackfillCorrectsValue`；`[D]ledger/TransactionEventBuilderTest.kt::usesNearestBeforeSnapshotAndMarksAfterFallbackEstimated` | ✅ |
| TC-GC-10 | 附录A-10 备份增量合并幂等 | 同一 .cpro 导入两次不产生重复，持仓与指标不变 | 导出→恢复→再恢复 | 第二次 0 新增；净值/收益/已实现不变 | `[DOM]backup/BackupMergePlannerTest.kt::golden case 10 - re-planning the same payload is a no-op`；`[D]backup/DefaultBackupServiceTest.kt::golden case 10 - restoring the same cpro twice is idempotent`；`[APP]integration/CoreJourneyIntegrationTest.kt::核心旅程 登录 增资 交易 看板ROI 备份恢复` | ✅ |
| TC-GC-11 | 附录A-11 24h 部分缺价 | 8/10 有价 → 剔除 2 个并标「覆盖 8/10」；全缺显示"--" | 构造 10 币输入 | 覆盖数 8/10；全缺返回 null（显示 --） | `[DOM]market/TwentyFourHourTest.kt::golden 11 - partial missing 24h prices are excluded and coverage is 8 of 10`、`::golden 11 - all coins without 24h price yield nulls (display dash)`；`[UI]portfolio/PortfolioPagesUiTest.kt::twenty four hour subtitle carries coverage and mixed source notes` | ✅ |
| TC-GC-12 | 附录A-12 法币交易对归一化 | BTC/USD → 计价腿映射 USDT 联动与余额校验；手续费 USD 同映射 | 记录/重放 BTC/USD 买入 | USD 腿按 1:1 映射 USDT；余额校验与扣减走 USDT | `[DOM]engine/GoldenCasesTest.kt::golden 12 - fiat quoted pair is normalized to twin stablecoin before replay`；`[DOM]catalog/FiatNormalizerTest.kt::default twins map USD to USDT and EUR to EURC`、`::fiat without twin mapping is third currency semantics`；`[D]catalog/SqlCoinCatalogTest.kt::fiat quote legs map USD and EUR to twin stables` | ✅ |

---

## §6 安全与隐私专项用例（AGENTS §1.1 五条硬约束 · 对应 security-checklist.md）

| 用例 ID | 需求锚点 | 验收要点 | 步骤/前置（简） | 期望结果 | 自动化映射（file::testFun）或执行方式 | 状态 |
|---|---|---|---|---|---|---|
| TC-SEC-01 | 硬约束1 数据本地化（出站白名单） | 主源码出站主机仅 3 个业务域名 | 静态扫描全部主源码 URL 字面量 | 命中集合 ⊆ {api.coingecko.com, pro-api.coinmarketcap.com, api.binance.com}（+2 个用户点击打开的文档域名） | `[D]security/SecurityGuardTest.kt::all outbound http hosts stay inside the allow list` | ✅ |
| TC-SEC-02 | 硬约束1 数据本地化（禁明文 http） | 无明文 http 出站字面量 | 同上扫描 `http://` 用法 | 除注释/示例外无明文 http 出站 | `[D]security/SecurityGuardTest.kt::no plaintext http egress literal is present` | ✅ |
| TC-SEC-03 | 硬约束1/3（日志不出网） | 日志配置无网络 appender | 扫描 logback 配置与依赖 | 无 socket/http appender | `[D]security/SecurityGuardTest.kt::logging has no network appenders` | ✅ |
| TC-SEC-04 | 硬约束2 零遥测 | 无遥测/分析/崩溃上报依赖 | 扫描 version catalog 与模块 build 脚本 | 命中 0 条（firebase/sentry/amplitude/… 全表） | `[D]security/SecurityGuardTest.kt::no telemetry analytics or crash reporting dependency is declared` | ✅ |
| TC-SEC-05 | 硬约束2/4（不内置 Key） | 应用不内置任何行情 Key：无 Key 时不带 Key 头，配 Key 后才带 | 构造无 Key/有 Key 两次请求 | 无 Key 请求不含 Key 头；配置后含用户 Key | `[D]market/MarketHttpClientTest.kt::cg current attaches key header when provided and omits when keyless`；`[UI]market/MarketSettingsSectionUiTest.kt::page renders rows with keyless defaults and frequency markers` | ✅ |
| TC-SEC-06 | 硬约束3 分层密钥链 | DEK/KEK/KDF 全链路：生成→派生→包裹→解包→擦除 | 建账后派生 KEK、包裹/解包 DEK、擦除密钥材料 | 同盐同口令确定；错口令无法解包；擦除后内存为零 | `[DOM]security/Argon2KdfTest.kt::derives 32 bytes deterministically with same salt and password`、`::rejects wrong salt size and empty password`；`[DOM]security/KdfParamsTest.kt::default matches frozen argon2id values and storage roundtrip`；`[DOM]security/KeyWrapTest.kt::wrap unwrap roundtrip restores dek`、`::wrong kek fails authentication`；`[DOM]security/CryptoServiceTest.kt::full account-key lifecycle`、`::wipe zeroes key material` | ✅ |
| TC-SEC-07 | 硬约束3 字段级加密 + **DEF-01 修复回归** | 凭证字段 AES-256-GCM 字段级加密（AAD 绑定账户/表/列）；**导出遇不可解密凭证时整体中止且不落文件** | 保存密钥→读密文→错 DEK 解；再把库内凭证密文改坏后导出 | 密文可解且 AAD 隔离；破坏后导出抛 `BackupExportException(CREDENTIAL_UNREADABLE)`、不生成 .cpro、文案点名问题密钥 | `[DOM]security/FieldCipherTest.kt::roundtrip preserves plaintext`、`::aad isolates account apiKey and column`、`::ciphertexts differ per encryption of same plaintext`、`::rejects malformed payloads`；`[D]exchange/ExchangeRepositoriesTest.kt::api key save roundtrips ciphertext and decrypts with account dek`；**DEF-01**：`[D]backup/DefaultBackupServiceTest.kt::export aborts with typed error when a stored credential cannot be decrypted`；`[UI]backup/BackupExportErrorCopyTest.kt::credential unreadable maps to actionable copy in both languages`、`::unknown export failure falls back to the original message`；实现：`domain/backup/BackupExportException.kt` | ✅ |
| TC-SEC-08 | 硬约束3 整库加密 | SQLCipher 整库加密 + WAL/busy_timeout/FK 生效 | 建库→读文件头→错钥打开→查连接参数 | 无明文页；错钥类型化失败；每连接参数生效 | `[D]SqlCipherDatabaseTest.kt::fresh database is encrypted at rest and migrates to latest`、`::restart with the same key unlocks and preserves data`、`::wrong key open fails with typed mismatch error`、`::wal busy_timeout and foreign_keys are effective on every connection`、`::exposed connections carry the key on fresh connections` | ✅ |
| TC-SEC-09 | 硬约束3 会话与改密语义 | 会话令牌包裹/校验；改密后旧令牌与旧口令失效、DEK 不变 | 建会话→改密→验旧令牌与旧口令 | 旧令牌解包失败；旧口令登录失败；DEK 相同（仅重包） | `[DOM]security/SessionAndVerifyTest.kt::session wrap unwrap roundtrip with token`、`::wrong token or wrong account fails session unwrap`、`::kek verify hash roundtrip and mismatch`、`::crypto facade session helpers`；`[D]accounts/DefaultAccountServiceTest.kt::change password invalidates token and old password and rewraps same dek` | ✅ |
| TC-SEC-10 | 硬约束3 文件权限 | 数据目录 700、密钥/库文件 600；旧文件被收紧 | 建库与密钥文件→查权限位；读旧权限文件 | 新建即 600/700；旧文件被收紧；缺失路径 no-op | `[DOM]security/FilePermissionsTest.kt::owner only write creates the file with 0600 from the start`、`::owner only write tightens an existing file`、`::ensure owner only directory creates and tightens to 0700`、`::restrict is a no-op for missing paths`；`[D]security/MasterKeyStoreTest.kt::posix permissions restricted to owner rw on posix filesystems`、`::loading a legacy key file tightens its permissions to 0600`、`::corrupt key file is rejected not overwritten` | ✅ |
| TC-SEC-11 | 硬约束3 日志脱敏 | 日志/诊断不含密钥与完整响应体 | 见 TC-UX-8 / TC-X-2.6-3 | 全量遮蔽、诊断清单受限 | 同 TC-UX-8；`[D]settings/DiagnosticsServiceTest.kt::report aggregates version schema counts and redacted log tail` | ✅ |
| TC-SEC-12 | 硬约束4 两类 API 隔离 + **DEF-02 修复回归** | 行情与交易同步各自独立客户端/凭据/额度账本；交易所签名请求携带 `recvWindow` 契约参数 | 构造两类客户端；触发一次签名请求 | 两客户端非同一实例；共享的只有代理 selector；签名 URL 含 `recvWindow=DEFAULT_RECV_WINDOW`、`timestamp`、`signature` | `[D]security/ApiIsolationGuardTest.kt::market and exchange clients are distinct instances`、`::clients never share a mutable proxy selector by construction`；**DEF-02**：`[D]exchange/BinanceAdapterTest.kt::validate credentials hits signed account endpoint with headers and signature`（含 recvWindow 断言）；实现：`data/exchange/BinanceAdapter.kt:177-179` | ✅ |
| TC-SEC-13 | 硬约束5 账户隔离 + .cpro 边界 | 账户数据按 account_id 隔离；备份载荷仅 7 张账户级表且不含账户信息/全局表/行情 Key | 建两账户互查；导出后枚举载荷 | 跨账户不可见；载荷边界精确 | `[APP]integration/CoreJourneyIntegrationTest.kt::多账户隔离与跨账户恢复`；`[D]portfolio/DefaultPortfolioServiceTest.kt::snapshotIsScopedToActiveAccount`；`[D]backup/BackupBoundaryGuardTest.kt::backup payload model carries exactly the seven account scoped tables`、`::account rows coins and global settings stay out of the backup`、`::market api key never enters the backup file`；`[D]backup/DefaultBackupServiceTest.kt::csv exports contain no api key material` | ✅ |
| TC-SEC-14 | 硬约束5 备份载荷健壮性（P6 新增） | 大载荷 .cpro 往返无损且默认测试堆内可编解码 | 构造大载荷（多账户多表）编解码往返 | 往返无损、无 OOM（默认 `-Xmx` 测试堆） | `[DOM]backup/CproLargePayloadTest.kt::large cpro payload round trips losslessly within the default test heap` | ✅ |
| TC-SEC-15 | 硬约束1 运行期出站面实证（P6 新增） | 运行期真实出站仅三个业务域名 | 起抓包代理 → 走代理跑「刷新行情 + 同步交易」→ 核对日志主机集合 | 代理日志主机集合 ⊆ {api.coingecko.com, pro-api.coinmarketcap.com, api.binance.com} | 工具与用例已就绪（P6 新增）：`scripts/outbound-capture-proxy.py`（不解密 TLS 的 CONNECT 记录代理）+ `[D]smoke/ProxyRoutingSmokeTest.kt::both api classes route real requests through the configured proxy`（默认跳过，`WZF_LIVE_SMOKE=1` + `https_proxy` 开启）→ 主机集合核对仍由执行者完成（**TC-MAN-09 🔵**） | 🔵 |
| TC-SEC-16 | 硬约束3 运行期文件权限实证（人工） | 真实数据目录与文件权限为 700/600 | `stat -c '%a %n' ~/.wuzhufolio ~/.wuzhufolio/logs ~/.wuzhufolio/master.key ~/.wuzhufolio/*.db`（Linux；macOS 用 `stat -f '%A %N'`） | 目录 700、密钥与库文件 600 | **TC-MAN-09（🔵 人工）**：命令见 `security-checklist.md §8-4` | 🔵 |

---

## §7 人工门用例（🔵 · Agent 不可替代）

> 执行人：项目负责人/人工验收者。执行前请先完成 §8「复跑命令汇总」第 1 条（全量自动化绿）。
> 每条须记录：执行日期、平台与版本、结果、证据（截图/录屏/抓包文件/benchmark 输出）、结论（通过/不通过）。

### TC-MAN-01 真实桌面托盘走查（Windows / macOS / Linux）

- **前置**：打包版或 `./gradlew :app:run` 运行中的桌面环境（**非** WSLg/无头环境——托盘不可用时按设计关窗即退出）。
- **步骤**：
  1. 登录进入主界面 → 关闭主窗口（点窗口关闭按钮）→ 观察窗口消失但进程仍在（系统托盘出现图标）。
  2. 右键托盘图标 → 依次点「打开主界面」「立即同步」「退出」。
  3. 设置 → 托盘与后台 → 关闭「关闭窗口最小化到托盘」→ 再关窗。
  4. 在托盘驻留状态等待一个同步间隔（默认 30 分钟，可先改为 15 分钟）观察通知。
- **通过判据**：① 开关开→关窗仅隐藏到托盘，进程存活；② 菜单三项分别生效（恢复主界面/触发同步并出状态/退出进程）；③ 开关关→关窗直接退出；④ 托盘不可用平台关窗即退出且不静默藏窗口；⑤ 后台同步完成/失败按通知开关弹出或静默。
- **对应验收点**：TC-F9.1-1、TC-F9.4-1、TC-F6.1-7。

### TC-MAN-02 开机自启实机验证（三平台）

- **前置**：打包安装后的可执行文件路径（未打包运行时会提示「无法推断可执行文件路径」——此为预期）。
- **步骤**：① 设置 → 托盘与后台 → 打开「开机自动启动」；② 按平台检查注册项：Windows `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`、macOS `~/Library/LaunchAgents/*.plist`、Linux `~/.config/autostart/*.desktop`；③ 注销并重新登录系统；④ 关闭开关后复查注册项已移除。
- **通过判据**：① 打开后注册项存在且命令行指向正确可执行文件（路径含空格时被正确引用）；② 重启登录后应用自动驻留托盘；③ 关闭后注册项消失；④ 注册失败时界面给出中文原因且不写坏键值。
- **对应验收点**：TC-F9.3-1。

### TC-MAN-03 读屏 NVDA / JAWS 实测

- **前置**：Windows + NVDA（或 JAWS）；macOS 可用 VoiceOver 观察。
- **步骤**：① 开启读屏，Tab 遍历登录页（用户名/密码/记住我/创建/忘记密码）；② 登录后朗读仪表盘六张卡与环形图；③ 打开资产列表逐行朗读；④ 打开交易/资金/备份三个弹窗并朗读字段与按钮；⑤ 触发一次 toast（如保存成功）。
- **通过判据**：① 每个可交互元素有可读名称与角色（按钮/输入框/下拉）；② 环形图朗读出分区数与总资产（`contentDescription`）；③ 排序表头朗读升/降序状态（`stateDescription`）；④ toast 被自动朗读（liveRegion=Polite）；⑤ 无「空白可聚焦元素」。
- **对应验收点**：TC-UX-18、TC-X-3-9。

### TC-MAN-04 目标机性能（4GB 双核）

- **前置**：目标低配机（4 GB 内存/双核）或等效受限容器；Java 21 运行时。
- **步骤**：① `./gradlew :domain:kdfBenchmark`（输出候选 Argon2id 参数耗时）；② 在该机运行应用，冷启动到登录页后输入口令并计时「解密→进入主壳」；③ 进入仪表盘与资产列表分别计时首屏渲染；④ 触发一次行情刷新，观察 UI 是否可继续交互；⑤ `./gradlew :domain:backupBenchmark`（可加 `-PbenchXmx=512m`）记录峰值堆与耗时。
- **通过判据**：① 登录解密 ≤2 秒（PRD §12）；② 仪表盘/资产列表首屏 ≤2 秒（PRD §6）；③ 刷新期间界面不卡死（可滚动/切页）；④ `backupBenchmark` 四档均在给定堆内完成、无 OOM，峰值堆与耗时记录留档供 P7 参考。
- **对应验收点**：TC-X-2.1-1、TC-UX-5、TC-SEC-16（内存曲线部分）。

### TC-MAN-05 断网 / 代理异常场景

- **前置**：可切换网络的环境；推荐用系统代理开关或防火墙规则模拟。
- **步骤**：① 断开网络 → 打开应用（或触发刷新）→ 观察状态栏与行情页；② 恢复网络 → 点状态栏刷新图标或手动刷新；③ 断开网络后录入一笔增资/买入并保存 → 恢复网络后等待回填；④ 断开网络时在币种详情点「以交易所余额校准持仓」。
- **通过判据**：① 状态栏出现断链图标与「网络断开」文案、价格旁显示上次成功时间戳、保留上次价格；② 点击刷新可重试并恢复；③ 离线记录标「待定价」+「估算中」，联网后自动回填重算并消除标注；④ 行情不可用时校准被阻止并给出提示。
- **对应验收点**：TC-X-N1、TC-X-N3、TC-X-2.4-1、TC-X-2.4-3、TC-X-2.7-5。

### TC-MAN-06 纯键盘全流程走查（含 Esc 关闭）

- **前置**：收起鼠标（或只记录键盘路径）。
- **步骤**：① Tab 从登录页开始遍历，回车创建账户；② 进入交易页 → 添加交易（Tab 到各字段、输入、保存）；③ 资金页 → 记录增资；④ Esc 关闭各弹窗；⑤ 全程不触碰鼠标完成「登录→增资→买币→看仪表盘→导出备份」。
- **通过判据**：① 每个核心入口 Tab 可达且焦点可见；② 弹窗打开即聚焦首输入框，键盘可直接录入；③ Esc 可关闭弹窗且焦点回到触发点；④ 全流程无需鼠标；⑤ 无 Tab 焦点陷阱（无法跳出）。
- **对应验收点**：TC-X-3-9、TC-UX-17、TC-X-2.7-9。

### TC-MAN-07 真实交易所只读 Key 线上同步冒烟（Binance）

- **前置**：真实 Binance 账户的**只读** API Key/Secret；测试期建议小额账户；`WZF_LIVE_SMOKE=1` 可先跑网络冒烟确认连通。
- **步骤**：① 设置 → API 管理 → 添加密钥（别名任意，交易所 Binance）→ 观察「测试请求」与保存后首次同步；② 查看同步结果（新增 N 条/去重跳过/未解析）与同步日志；③ 再点一次「立即同步」验证增量幂等（应为 0 新增）；④ 在币种详情对单一来源币执行一次「以交易所余额校准持仓」；⑤ 导出 .cpro 后删除本地交易再恢复，核对持仓与指标。
- **通过判据**：① 密钥校验通过并加密落库；② 首次同步导入新成交，签名请求成功（含 recvWindow）；③ 二次同步 0 新增；④ 校准生成记录、留痕 sync_logs、指标随锚点联动且恒等式成立；⑤ 恢复后指标与恢复前一致；⑥ 全过程中日志/通知/诊断报告不出现密钥明文与完整响应体。
- **对应验收点**：TC-A4.1-4、TC-A4.1-5、TC-SEC-12、TC-X-N2。

### TC-MAN-08 真实桌面 GUI 全流程走查（含 IA 层级与视觉）

- **前置**：打包版或 `:app:run`，1280×800 与 1024×768 两种窗口尺寸。
- **步骤**：① 首启建账（含风险确认）→ 向导任选初始化方式；② 记录增资 → 手动买入 → CSV 导入 → 看仪表盘与资产列表 → 打开币种详情；③ 设置页逐组走查（通用/网络/托盘/行情与同步/日志/手续费/API/数据/关于）；④ 备份 → 删除数据 → 恢复；⑤ 切换账户与登出；⑥ 分别按「三层路径」数各核心操作的点击层级；⑦ 两窗口尺寸下核对无裁切/错位。
- **通过判据**：① 全流程无阻断性缺陷、无异常弹窗；② 每个核心操作 ≤3 层可达（PRD §6）；③ 关键数据在首屏显眼位置；④ 小窗口下无字段被裁切（交易/资金表单的时间/备注字段可见或可滚动到）；⑤ 各页面文案与 PRD/interaction 一致（含空态/错误态/标注）。
- **对应验收点**：TC-UX-1、TC-UX-2、TC-A2.1-2、TC-A2.2-2。

### TC-MAN-09 运行期出站抓包 + 文件权限实证

- **前置**：mitmproxy/Wireshark（或系统代理日志）+ Linux/macOS 终端。
- **步骤**：① 起 P6 抓包代理并让应用走它（推荐用仓库内工具，不解密 TLS、不接触业务数据）：
  ```bash
  python3 scripts/outbound-capture-proxy.py --port 8899 --log /tmp/wzf-outbound.log &
  # 方式 A（自动化证据）：WZF_LIVE_SMOKE=1 https_proxy=http://127.0.0.1:8899 \
  #   ./gradlew --no-daemon :data:test --tests "com.wuzhufolio.data.smoke.ProxyRoutingSmokeTest" --rerun-tasks
  # 方式 B（GUI 走查）：WUZHUFOLIO_DATA_DIR=/tmp/wzf-p6-capture https_proxy=http://127.0.0.1:8899 \
  #   http_proxy=http://127.0.0.1:8899 ./gradlew :app:run   # 登录→刷新行情→同步交易
  ```
  ② 结束运行后列出抓包日志中的目标主机：`cut -d' ' -f3 /tmp/wzf-outbound.log | sort -u`；③ 终端执行 `stat -c '%a %n' ~/.wuzhufolio ~/.wuzhufolio/logs ~/.wuzhufolio/master.key ~/.wuzhufolio/*.db`；④ 抽查安装包与 .cpro：`grep -a` 搜索已知密钥串。
- **通过判据**：① 出站主机仅 `api.coingecko.com` / `pro-api.coinmarketcap.com` / `api.binance.com`（HTTP 与 HTTPS 同理，无其他域）；② 目录 700、密钥与库文件 600；③ .cpro 与 CSV 导出中 grep 不到任何密钥明文；④ 安装包内不含 `master.key`/`device.key`/`.db`。
- **对应验收点**：TC-SEC-15、TC-SEC-16（口径见 `security-checklist.md §8-4/§8-5/§8-6`）。

### TC-MAN-10 外链与关于页走查

- **前置**：桌面环境 + 系统浏览器。
- **步骤**：① 关于分组点隐私政策/发布渠道链接；② API 弹窗点「创建教程」链接；③ 行情设置页点 CoinGecko/CoinMarketCap 注册链接；④ 429 提示中的注册引导。
- **通过判据**：① 链接在系统浏览器打开且指向正确页面（不内置任何 Key 申请页自动填充）；② 应用自身不发起该域名请求（外链由用户点击触发）；③ 关于页显示版本号 + AGPL-3.0 + 无遥测声明 + 行情数据源说明 + 时间分辨率边界说明。
- **对应验收点**：TC-A4.1-2、TC-F4.1-2、TC-A3.2-6、TC-F6.4-1、TC-F6.4-3。

---

## §8 未覆盖 / 部分覆盖清单（缺口 → 影响 → 处置）

> 编号沿用 P6 盘点清单（G-01…G-52）。处置类型：**补测**（注明补哪一层）/ **人工门**（🔵 TC-MAN-xx）/ **登记 P8** / **defects.md DEF-xx**。
> 说明：凡「实现已有、仅缺断言」均记 **🟡 补测**；凡「行为缺失 / 未实现」记 **⬜ + 去向**。

| 编号 | 涉及用例 | 缺口（没覆盖什么） | 影响 | 处置 |
|---|---|---|---|---|
| G-01 | TC-A4.1-2、TC-F4.1-4、TC-UX-3 | API 弹窗「仅需要只读权限 / 字段级加密 / 关联当前账户」提示无断言 | 只读权限是安全承诺，文案被改无人拦 | 🟡 补测：Compose UI 文案断言 |
| G-02 | TC-F4.1-2、TC-F6.4-2、TC-F6.4-1 | 教程/隐私政策/发布渠道外链打开无验证 | 用户无法确认引导可达 | 🔵 人工门 TC-MAN-10 |
| G-03 | TC-X-3-7 | 「请清理系统剪贴板」提示（zh/en 已有实现）无断言 | 剪贴板云同步是真实泄露面（PRD §9.10） | 🟡 补测：Compose UI 断言 `saveAndSyncToast` 文案 |
| G-04 | TC-A4.1-6、TC-F4.2-3 | 「Binance 仅返回最近 500 条、更早须 CSV」说明无断言 | 用户误判数据完整性 | 🟡 补测：Compose UI 文案断言 |
| G-05 | TC-F6.4-3 | 关于页行情数据源说明（主源/兜底/不内置 Key/时间分辨率边界）无断言 | PRD §6.4 明列；时间分辨率局限需主动披露 | 🟡 补测：Compose UI 文案断言 |
| G-06 | TC-A5.1-6、TC-A5.2-3 | 「改密后旧备份仍可用备份密码恢复」无端到端；备份密码「不回填」无断言（D24） | 密码策略连锁规则的核心承诺 | 🟡 补测：integration（改密→导出→恢复）+ Compose UI（密码框为空） |
| G-07 | TC-A5.2-7、TC-F5-2 | 恢复后「刷新所有页面 / 提示重启」的 UI 行为无断言 | 恢复后停留旧数据是高风险体验缺陷 | 🟡 补测：Compose UI + integration |
| G-08 | TC-X-B1 | 库文件损坏 → 全屏错误框与恢复引导未实现、零用例 | PRD 统一异常处理明列；真实事故场景 | ⬜ **登记 P8**（含实现待办；或按 §8 变更控制判定后补） |
| G-09 | TC-A1.3-4 | 「切换瞬间锁定旧账户、解密新账户数据」无直接断言 | 严格模式隔离承诺 | 🟡 补测：integration |
| G-10 | TC-F6.1-4、TC-UX-16、TC-A3.1-3、TC-A6.4-4 | 盈亏配色三方案（含色盲友好蓝涨橙跌）选择与生效零断言 | PRD §6 无障碍硬指标，色彩改动无护栏 | 🟡 补测：Compose UI（切档断言颜色）+ unit（`withPnlScheme`） |
| G-11 | TC-UX-18、TC-X-3-9 | 读屏语义标签（环形图/toast/select role）零断言 | PRD 要求兼容 NVDA/JAWS | 🟡 补测：Compose UI（semantics）+ 🔵 人工门 TC-MAN-03 |
| G-12 | TC-UX-17、TC-X-3-9、TC-X-3-11 | Tab 顺序/方向键/Esc/焦点陷阱无断言（仅「打开即聚焦」） | 全键盘可用性无回归护栏 | 🟡 补测：Compose UI（`performKeyInput`）+ 🔵 人工门 TC-MAN-06 |
| G-13 | TC-X-2.7-9 | 行情页搜索框「打开即聚焦」无断言（实现已有 FocusRequester） | AGENTS §7.3 强制验收项 | 🟡 补测：Compose UI `assertIsFocused` |
| G-14 | TC-UX-14 | EUR/CNY 多法币端到端展示无断言 | PRD §6 I18N 多法币要求 | 🟡 补测：Compose UI + data-layer |
| G-15 | TC-UX-1、TC-UX-3、TC-UX-6 | 「关键数据显眼」「敏感区安全提示」「日志含操作类型/结果」仅间接证据 | 主观/结构性要求易被当成已覆盖 | 🔵 人工门 TC-MAN-08 + 🟡 补测 unit（日志字段结构） |
| G-16 | TC-UX-2 | 「操作路径不超过三层」无任何验证 | 无法自动化，易漏 | 🔵 人工门 TC-MAN-08 |
| G-17 | TC-X-2.1-1 | 登录 loading「正在解密…」与 ≤2 秒预算无断言 | PRD §12 发布门槛项 | 🟡 补测 Compose UI（文案）+ 🔵 人工门 TC-MAN-04（计时） |
| G-18 | TC-X-2.1-2、TC-X-2.1-4、TC-X-2.1-5、TC-X-2.1-6、TC-X-3-2 | 行情 loading、CSV 进度条、备份/恢复进度条、列表滚动加载占位 | interaction §2.1 六项中四项无覆盖 | ⬜ **登记 P8**（进度条/loading 无实现）；滚动加载 → **DEF-05 已按 C0 口径澄清（2026-09-14）** |
| G-19 | TC-X-N1、TC-X-2.4-1 | 断链图标/「网络断开」文案/上次成功时间戳/点击刷新无断言 | 离线第一条异常态呈现无护栏 | 🟡 补测 Compose UI + 🔵 人工门 TC-MAN-05 |
| G-20 | TC-X-B3、TC-X-2.5-2、TC-X-2.5-4 | 额度 100% 耗尽分支与逐字文案；CMC 调用计入 CG 账本（DEF-04） | 免费档用户常见路径；降档可能被兜底提前触发 | 🟡 补测 unit+Compose UI（登记 P8）；⬜ **DEF-04 → 人工裁决「登记 P8」（2026-09-14）** |
| G-21 | TC-X-B5、TC-X-2.5-3 | 「CG 失败→CMC 也失败→保持上次价+时间戳」组合分支 | 兜底链末端行为决定用户看到旧价还是空白 | 🟡 补测：data-layer（`DefaultMarketRefreshServiceTest` 扩例） |
| G-22 | TC-X-2.7-5、TC-X-2.7-6、TC-X-2.7-4、TC-X-2.7-3 | 行情页离线态/429 提示/行级数据源标注/「无行情」标识 | D21 新页异常语义在 UI 层几乎空白 | 🟡 补测：Compose UI |
| G-23 | TC-X-2.7-7、TC-X-2.7-2、TC-X-2.7-8 | 搜索无结果、自选全移除空态、上限阻止与提示文案 | interaction §2.7 明文条目，空态/上限文案无护栏 | 🟡 补测：Compose UI |
| G-24 | TC-X-2.2-1、TC-X-2.2-5 | 资产列表空账本空态；ROI=0 时「请先记录增资」提示 | 首批用户路径；提示未实现 | 🟡 补测 Compose UI（空态）；⬜ 提示 → **登记 P8** |
| G-25 | TC-X-2.1-3 | 「同步中」有断言，但同步列表 loading 与后台不阻塞前台无断言 | §10 并发写模型要求前台可编辑 | 🟡 补测：Compose UI + integration |
| G-26 | TC-X-A2、TC-X-A3 | 「原密码不正确」「账户数据加载失败，请重试」UI 文案零断言；A3 分支无实现级用例 | 统一异常处理要求明确提示 | 🟡 补测：Compose UI + data-layer |
| G-27 | TC-A1.1-1、TC-X-V8 | 创建账户「两次密码不一致」校验零断言 | 创建流程硬门禁之一 | 🟡 补测：Compose UI |
| G-28 | TC-X-V3 | ~~手续费币种=自定义必填零断言~~ **已闭环**（P6 补测 `customFeeRoleRequiresFeeCurrency`） | interaction V3 明文规则 | ✅ 已补测（TC-X-V3 转 ✅） |
| G-29 | TC-A2.1-3、TC-X-V1、TC-X-V2、TC-X-V6 | 交易侧 0/负数边界**已闭环**（P6 补测 `zeroAndNegativeAmountsAreRejectedByV1V2Rules`）；**资金表单（V6）0/负数仍无用例** | 负数入账会直接制造异常数据 | ✅ 交易侧已补测（TC-A2.1-3/TC-X-V1/TC-X-V2 转 ✅）；🟡 资金侧待补 Compose UI |
| G-30 | TC-X-V7、TC-X-A2 | V7 撤资不足 UI 文案逐字、临界值（恰好等于）无断言 | 文案决定自助解决；临界值是精度 bug 源 | 🟡 补测：Compose UI + unit |
| G-31 | TC-A2.1-2、TC-F3-1、TC-A2.1-4 | 交易表单交易所字段、手续费币种三选一无断言；保存后列表新增行未断言 | 字段缺失/错位是历史高频缺陷 | 🟡 补测：Compose UI |
| G-32 | TC-A2.2-2、TC-A6.1-2、TC-F8-1、TC-A6.2-2、TC-F8-2 | 增资/撤资表单的日期、来源/去向、备注无断言 | 日期决定重放顺序 | 🟡 补测：Compose UI |
| G-33 | TC-A3.3-2、TC-A3.4-2、TC-A3.4-5、TC-F2.1-3、TC-F2.2-1、TC-F2.2-3、TC-A6.3-3 | 资产/详情/资金列表未逐列断言 | 列缺失或串列难以发现 | 🟡 补测：Compose UI（逐字段断言） |
| G-34 | TC-A6.3-2、TC-X-3-1 | 交易/资金列表按时间倒序无 UI 断言 | PRD 列表默认规则 | 🟡 补测：Compose UI + data-layer |
| G-35 | TC-A3.4-4、TC-F2.2-2 | 币种详情**缺时间筛选**（交易所/类型/搜索已实现） | PRD 故事 3.4-4 明文要求三项 | ✅ **已修复（C1 · D30，2026-09-14）**：时间档位下拉 + UI 回归 + 原型/verify 同步 |
| G-36 | TC-F3-3、TC-F8-3 | 交易/资金列表筛选与搜索的结果正确性无断言（假服务不过滤） | 筛选失效会误导审计 | 🟡 补测：Compose UI（断言传入 filter）+ data-layer |
| G-37 | TC-A2.3-3、TC-A2.3-4、TC-A2.3-7 | CSV 预览「疑似重复 M 条/币种变化」未断言；歧义候选 UI 用例固定 `ambiguous=emptyList()`；逐条确认交互无断言 | CSV 导入成功率是内测质量指标 | 🟡 补测：Compose UI + data-layer |
| G-38 | TC-A3.1-1 | 「登录成功后默认落在仪表盘」无断言 | 首屏落点基线 | 🟡 补测：Compose UI + integration |
| G-39 | TC-A6.4-3、TC-F1-1、TC-F2.1-1、TC-F8-5、TC-A6.4-2 | 仪表盘 4 张卡（投入本金/总收益/已实现/可用现金）与资产列表顶部总览卡无断言 | PRD §7.2-2.1/8.5 要求两页均展示 | 🟡 补测：Compose UI |
| G-40 | TC-A7.1-3、TC-A7.1-5、TC-A7.1-7、TC-A7.2-5 | 「实时总价」「实时手续费」「自动算费」按钮填充路径零断言 | 自动算费（P2 兴奋需求）UI 断链等于功能不存在 | 🟡 补测：Compose UI + unit(VM) |
| G-41 | TC-F6.1-1、TC-F6.1-6 | 基础法币选择器、用户名枚举关闭后的登录页行为无断言 | 影响全局可见行为 | 🟡 补测：Compose UI |
| G-42 | TC-F6.1-12 | CMC API Key 保存/移除路径无测试（CG Key 已完整） | 兜底能力依赖该 Key | 🟡 补测：data-layer + Compose UI |
| G-43 | TC-F6.4-1 | 关于页仅断言「版本」二字；开发者与 AGPL-3.0 无断言 | D1 开源决策的合规展示 | 🟡 补测：Compose UI |
| G-44 | TC-A7.2-6 | 「交易对级费率不在 MVP」无结构断言 | 防回归（后续引入 pair 级字段需被提醒） | 🟡 补测：data-layer 结构断言（fee_rules 无 pair 列） |
| G-45 | TC-F9.1-1 | 托盘菜单三项（打开主界面/立即同步/退出）无自动化测试 | 桌面端核心体验（PRD §7.2-9.1） | 🔵 人工门 TC-MAN-01（如需回归可先抽出可测策略再补 unit） |
| G-46 | TC-A4.2-2、TC-SEC-15 | 「所有对外请求确实经代理」只有结构守护 | PRD 故事 4.2-2 为 P0 需求 | 🔵 人工门 TC-MAN-09（抓包实证） |
| G-47 | TC-A5.2-8 | 全新安装向导「从备份恢复」入口无走查 | 新机迁移主场景 | 🟡 补测：Compose UI（实现已有） |
| G-48 | TC-UX-5 | 核心数据加载/刷新 ≤2 秒无度量 | PRD §6 明文性能要求 | 🔵 人工门 TC-MAN-04（+ 可选 integration 计时段言） |
| G-49 | TC-F6.1-4、TC-UX-16 | 盈亏配色三方案为「实现已有、零测试」黑箱 | 同 G-10 | 🟡 补测（与 G-10 合并执行） |
| G-50 | TC-X-B1 | DB 损坏全屏错误框无实现级与 UI 级测试 | 同 G-08 | ⬜ **登记 P8**（与 G-08 合并） |
| G-51 | TC-X-3-7 | 剪贴板提示「实现已有、零引用」 | 同 G-03 | 🟡 补测（与 G-03 合并） |
| G-52 | TC-X-2.1-6、TC-X-3-2 | 「列表滚动加载」实现层无分页/占位 | interaction 措辞与实现不一致 | ✅ **C0 口径澄清已回写**（`interaction.md §2.1/§3-2`，2026-09-14） |

---

## 需求回溯

| 需求锚点 | 对应用例段 | 覆盖结论 |
|---|---|---|
| PRD §5 故事 1.1–1.3（账户创建/登录/登出/切换） | §1.1、§1.2 | 除「两次一致校验」「切换锁定」外均 ✅（见 G-27/G-09） |
| PRD §5 故事 2.1–2.3（手动交易/增资/CSV） | §1.3 | 核心数值 ✅；表单字段与预览细节待补（G-31/G-32/G-37） |
| PRD §5 故事 3.1–3.4（看板/行情/持仓/详情） | §1.4 | 资产侧 ✅；详情时间筛选缺失（DEF-03） |
| PRD §5 故事 4.1–4.2（API 同步/代理） | §1.5 | 同步与校准 ✅；只读提示与代理实证待补（G-01/G-46） |
| PRD §5 故事 5.1–5.2（加密存储/备份恢复） | §1.6 | ✅（含 DEF-01 修复回归）；改密×旧备份、向导入口待补（G-06/G-47） |
| PRD §5 故事 6.1–6.4（资金管理/ROI） | §1.7 | 数值口径 ✅；表单字段与列表展示待补（G-32/G-33） |
| PRD §5 故事 7.1–7.2（手续费/费率设置） | §1.8 | 计算与优先级 ✅；UI 自动算费链路待补（G-40） |
| PRD §7.2 模块 1–9 | §2 | 模块 4/5/6/7 主体 ✅；设置项效果类断言待补（G-41/G-42/G-43） |
| PRD §6 设计与体验原则 | §4.10 | i18n/日志/对比度 ✅；配色/读屏/键盘/性能待补（G-10/G-11/G-12） |
| interaction §1.1–1.4 异常态 | §3 | N/B/A 系主体 ✅；A2/A3 文案与 V1/V2/V3/V6 边界待补 |
| interaction §2.1–2.8 加载/空/错误/离线/限流 | §4.1–4.8 | ✅ 空态/降级/异常口径；加载态与行情页呈现待补（G-18/G-22/G-23） |
| PRD 附录 A 黄金用例 1–12 | §5 | **全部 ✅** |
| AGENTS §1.1 五条硬约束 | §6 | **全部 ✅（自动化面）**；运行期出站/权限实证与内存曲线为 🔵 |
| D21 行情页 | §4.7 | 数据层 ✅；UI 行级标注/空态/上限/聚焦待补 |
| D24 备份密码独立 | §1.6 TC-A5.2-3 | 口径已按「不回填、禁空」编写；缺显式断言（G-06） |
| D25 界面语言 | §4.10 TC-UX-13 | ✅ |
| D26 负持仓成本口径 | §4.8 TC-X-2.8-4 | ✅ |
| D27/D28 1:1 锚定与白名单 | §1.3 TC-A2.2-3、§1.7 TC-A6.1-4、§4.8 | ✅（`d27DepositTradeAndMetricsStayAtParDespiteMarketSnapshot`、`default cash whitelist is usdt only and anchor set is fixed to usdt`） |
| D29 负持仓不计入净值 | §4.8 | ✅（含仪表盘显式提示） |

---

## 复跑命令汇总

> 约定：`export JAVA_HOME=$(mise where java)`（或按 `docs/tech/dev-setup.md`）；首次复跑建议 `--no-build-cache`。
> **执行顺序建议**：1 → 2 → 3 → 4（可选真实网络）→ 5（GUI）→ 6（域基准）→ §7 人工门。

```bash
# 1) 全量构建 + 静态检查（P6 主门槛；测试总数以本次运行为准，历史上限为 636）
./gradlew clean build detekt --no-build-cache

# 2) 聚焦各模块（按需复跑；--tests 支持通配）
./gradlew :domain:test --tests "com.wuzhufolio.domain.engine.*"       # 引擎/重放/黄金用例/费率/校准
./gradlew :domain:test --tests "com.wuzhufolio.domain.market.*"       # 24h 口径/快照分辨率/额度/刷新频率
./gradlew :domain:test --tests "com.wuzhufolio.domain.security.*" \
                       --tests "com.wuzhufolio.domain.redaction.*"    # 密钥链与脱敏
./gradlew :data:test   --tests "com.wuzhufolio.data.ledger.*" \
                       --tests "com.wuzhufolio.data.portfolio.*"      # 账本/资金/校准/聚合
./gradlew :data:test   --tests "com.wuzhufolio.data.market.*" \
                       --tests "com.wuzhufolio.data.exchange.*"       # 行情客户端/兜底/同步适配
./gradlew :data:test   --tests "com.wuzhufolio.data.backup.*" \
                       --tests "com.wuzhufolio.data.security.*"       # 备份边界/导出错误（DEF-01）/安全守护
./gradlew :ui:test     --tests "com.wuzhufolio.ui.portfolio.*" \
                       --tests "com.wuzhufolio.ui.ledger.*" \
                       --tests "com.wuzhufolio.ui.market.*" \
                       --tests "com.wuzhufolio.ui.exchange.*" \
                       --tests "com.wuzhufolio.ui.backup.*" \
                       --tests "com.wuzhufolio.ui.settings.*" \
                       --tests "com.wuzhufolio.ui.shell.*" \
                       --tests "com.wuzhufolio.ui.auth.*"             # Compose 离屏 UI 全量走查

# 3) 集成联调（真实组合根）：核心旅程 / 异常态 / 交易所回环
./gradlew :app:test --tests "com.wuzhufolio.app.integration.*"
./gradlew :app:test --tests "com.wuzhufolio.data.security.SecurityGuardTest" 2>/dev/null || true

# 4) 真实网络冒烟（默认跳过；需外网与真实端点）
WZF_LIVE_SMOKE=1 ./gradlew :data:test --tests "com.wuzhufolio.data.smoke.LiveNetworkSmokeTest" --rerun-tasks -i
WZF_LIVE_SMOKE=1 ./gradlew :app:test  --tests "com.wuzhufolio.app.integration.LiveMarketRefreshWiringTest" --rerun-tasks -i

# 5) GUI 冒烟（真实窗口；Linux 无头环境用 SOFTWARE_FAST）
WUZHUFOLIO_DATA_DIR=/tmp/wzf-p6-gui JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE_FAST" timeout 75 ./gradlew :app:run
#   观察点：登录页→建账→仪表盘→增资→交易→备份（对应 §7 TC-MAN-01/08）

# 6) 域基准（P6 新增：.cpro 大载荷内存曲线；默认 -Xmx1g 模拟 4GB 目标机打包版堆）
./gradlew :domain:kdfBenchmark                      # 登录 KDF ≤2s 预算（TC-MAN-04）
./gradlew :domain:backupBenchmark                   # 轻量/典型/重度/压力四档：峰值堆+耗时+体积
./gradlew :domain:backupBenchmark -PbenchXmx=512m   # 更严格档（可选）
```

**结果归档**：全量输出、基准输出与抓包文件随 `test-report.md` 一并归档；缺陷记入 `defects.md`（DEF-01/DEF-02 已修复并回归；DEF-03/DEF-04 待人工定级，建议 C1；DEF-05 为设计口径澄清）。
