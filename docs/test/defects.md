# WuZhuFolio P6 缺陷与问题清单（docs/test/defects.md）

> **阶段**：P6 系统测试与质量 · 启动指令：人工「执行P6」（2026-09-14）
> **有效需求基线**：PRD V2.0 + Δ{D21, D24, D25, D26, D27, D28, D29}
> **级别口径**：P0 数据/密钥/启动/主流程阻断 · P1 主要功能错误或验收标准未满足 · P2 次要偏差/体验/可诊断性 · P3 文案细节
> **变更控制**：凡触及已通过模块的行为/数据/接口，均给出 `AGENTS.md §8.1` 分级建议 + 影响面扫描，
> **由人工在 P6 门拍板**（Agent 不自行定级 C1/C2；纯实现偏差按 C0 处理并留痕）。
> **DoD 关系**：P0/P1 必须清零（本清单 P0=0 / P1=0）；P2 必须给出明确处理结论（修复或登记 + 到期检查点）。

---

## 0. 结论汇总

| 级别 | 数量 | 状态 |
|------|------|------|
| **P0** | **0** | — |
| **P1** | **0** | — |
| **P2** | 6 | **4 项已修复**（DEF-01/02/03/06）· **2 项已按人工裁决处置**（DEF-04 登记 P8；DEF-05 按 C0 文档澄清并已回写）<br>（另有 **DEF-12** = CI 三平台复跑暴露的**测试缺陷**，已修复，见 §3） |
| **P3 / 观察项** | 6 | 登记（DEF-07…DEF-12），详见 §3 |
| 合计 | 12 | P0/P1 清零 ✅；P2 全部有明确结论 ✅ |

> 结论：**无 P0/P1 缺陷**；P2 六项在人工 P6 门全部裁决完毕（见 §0.1），**无遗留未决项**。

### 0.1 人工裁决记录（2026-09-14 · 原话「裁决：5项都按建议来处理」）

| # | 裁决事项 | 人工裁决 | 落地状态 |
|---|----------|----------|----------|
| ① | 行情请求币种集合隐私最小化 | **接受现状 + 记入用户指南/隐私声明** | ✅ 已登记为 **P7 用户指南/隐私声明** 内容项（见 `test-report.md §5.3/§6`、STATUS P7 携带项）；评估结论与量化数据见 `test-report.md §5.3` |
| ② | DEF-03 币种详情缺「时间」筛选 | **本轮补做（C1 mini 闭环）** | ✅ **已实施**：决策档 `docs/dev/decisions/D30-币种详情时间筛选.md` + 台账 D30 行 + 索引 + task-breakdown **T12.5** + `ia.md §2.6` + 原型/verify 同步 + 代码与 UI 回归（详见 §2 DEF-03） |
| ③ | DEF-04 CMC 兜底计入 CG 额度账本 | **登记 P8** | ✅ 已登记（P8 立项输入：账本增 provider 维度 + 旧载荷兼容；影响面见 §2 DEF-04） |
| ④ | DEF-05 interaction「列表滚动加载」口径 | **按 C0 文档澄清** | ✅ **已回写**：`interaction.md §2.1` 增「列表装载口径」注 + §3-2 措辞订正（本地库单次装载 + `LazyColumn` 虚拟化，不适用分页）；大数据量装载耗时登记 P8 观察项 |
| ⑤ | DEF-01 / DEF-02 / DEF-06 定级 | **维持 C0** | ✅ 已确认（三项均为实现偏差/失败模式补全，未改产品语义、未改数据模型与格式；回写见各自条目） |

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
