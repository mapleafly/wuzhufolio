# WuZhuFolio P5 集成联调报告（docs/test/integration-report.md）

> **阶段**：P5 集成与联调（`AGENTS.md §4 P5`）· 启动指令：人工「执行P5」（2026-09-13）
> **输入**：P4 全部模块（M1–M13，全部已通过）+ `docs/tech/api-contracts.md`
> **目标**：把各模块拼起来，打通核心用户旅程（登录 → 增资 → 交易 → 看板/ROI → 备份恢复）
> **DoD**：① 主流程无阻断性缺陷；② 跨模块接口与契约一致
> **有效需求基线**：PRD V1.9 + Δ{D21, D24, D25, D26}（`docs/dev/增量台账.md`）
> **状态**：**✅ 已通过（2026-09-13 人工「P5通过」关闭）**——核心用户旅程人工验收通过；
> PRD V2.0 / 共享规范 V1.1 回写随本门一并确认。P5 关闭后 **P6 系统测试与质量** 解锁（待启动指令）。

---

## 0. 结论摘要

> **人工验收轮（2026-09-13）**：人工按 §7 实机走查，步骤 1/6/7/8 通过，步骤 2–5 报出 **4 类问题**
> （含 **1 个 P0 级数据完整性缺陷**：跨账户恢复丢交易行）。逐条根因与修复见 **§11**；
> 修复后 §7 的验收数值口径已按**真实行情**修正（原写死的 101,000 / +1.00% 只在「离线固定价」下成立）。

| DoD | 结论 | 依据 |
|-----|------|------|
| 主流程无阻断性缺陷 | ✅ 达成（人工验收暴露的 1 个 P0 + 2 个 P1 已修复并回归；1 项**口径**问题待人工定级） | 核心旅程在**真实组合根 + 真实 SQLCipher 库 + 真实 `.cpro` 文件**上端到端跑通；§11 修复轮后 **10 项集成测试** + 2 项真实网络冒烟全绿（§2/§5） |
| 跨模块接口与契约一致 | ✅ 达成（含 1 处文档回写缺口已补） | 22 个 domain 接口逐签名比对 api-contracts §3；发现 **F-1：M2/M3/M11/M12 四模块的契约补录只在模块记录声明、正文未回写** → 已回写（§3） |

**本次新增产物**：

| 产物 | 说明 |
|------|------|
| `app/src/test/.../integration/IntegrationHarness.kt` | 集成测试基座：**真实组合根**（`AppBootstrap.run`）+ 临时数据目录隔离（每用例独立加密库） |
| `app/src/test/.../integration/CoreJourneyIntegrationTest.kt` | 核心旅程 3 项（主流程 / 多账户隔离与跨账户恢复 / 登出与重新登录） |
| `app/src/test/.../integration/ExchangeLoopbackIntegrationTest.kt` | 交易所回环链路 1 项（真实 HTTP + 真实签名 + 真实服务：同步 → 账本 → 校准 → 看板 → 跨账户恢复后再同步） |
| `app/src/test/.../integration/ErrorPathIntegrationTest.kt` | 跨模块异常态 4 项（A1 / V5 / V7 / V9 / BACKUP_INVALID / 校准三类阻止原因） |
| `data/src/test/.../smoke/LiveNetworkSmokeTest.kt` | 真实网络冒烟 2 项（**默认跳过**，`WZF_LIVE_SMOKE=1` 开启）：CoinGecko 真实报价+目录+落库、Binance 公开端点 |
| `docs/tech/api-contracts.md` §3 | **回写 M2/M3/M11/M12/M13 契约补录 + P5 一致性核对说明**（F-1 处置）+ `MergePlan` 口径澄清 |
| `docs/design/prototype/wuzhufolio-light.html` + `prototype-verify.js` | **D25 到期项闭环**：通用组补「界面语言」行；小额阈值行对齐 M10 自由数值输入；新增 3 条 Playwright 断言 |
| 代码接缝（C0） | `AppBootstrap.SessionRuntime.sessions` 由 private 改公开（组合根之外装配同类服务需同一会话持有器，见 §4-3） |

**结论**：P5 具备人工验收条件，**停在人工门**——请按 §7 逐项走查核心用户旅程。

---

## 1. 联调方法与证据分层

P4 的模块测试用替身（MockEngine/Fake 适配器/内存夹具）验证模块内行为；P5 换一种问法：
**「把真实实现按真实装配顺序接起来，还能不能跑通用户旅程」**。因此本阶段不新增替身，改为分三层取证：

| 层 | 真实度 | 覆盖 | 产物 |
|----|--------|------|------|
| **L1 全栈组合根集成测试** | 真实 `AppBootstrap.run`（密钥链→SQLCipher 库→迁移→Koin→服务束）+ 真实服务实现 + 真实 `.cpro`；行情在**数据边界**注入（目录 + 快照，经真实仓库写真实库） | 登录/会话/增资/交易/CSV/看板/ROI/备份/恢复/多账户隔离/异常态 | `app/src/test/.../integration/*` |
| **L2 回环链路集成测试** | L1 + **真实 HTTP**（JDK HttpServer 回环桩 + 真 OkHttp/Ktor + 真 HMAC 签名） | 交易所同步 → 账本 → 校准 → 看板 → 跨账户恢复后凭证仍可驱动同步 | `ExchangeLoopbackIntegrationTest` |
| **L3 真实外部端点冒烟 + GUI 冒烟** | 真实公网（CoinGecko / Binance 公开端点，**不使用任何 Key**）+ 真实桌面进程（WSLg） | 生产客户端的真实端点契约；进程级启动链 | `LiveNetworkSmokeTest`（env 门控）+ §5 GUI 冒烟 |

> **外部 API 不进默认回归**：L3 的 CoinGecko 用例带 `WZF_LIVE_SMOKE=1` 门控（默认跳过），
> CI 与日常回归必须离线可重复——本次实测中同一用例首跑即遇到一次瞬时失败（详见 §4-2），印证该口径必要。

---

## 2. 核心旅程打通（端到端）

### 2.1 主流程数值轨迹（`CoreJourneyIntegrationTest`，手算可核）

| 步骤 | 模块链路 | 结果 |
|------|----------|------|
| ① 建账户 + 记住我 | M2（`DefaultAccountService`：单次 Argon2id → KEK → 解包 DEK → 会话） | 创建即登录；`restoreSession()` 免密恢复同一账户 |
| ② 增资 100,000 USDT | M8 资金事件 → M4 事件流 | 可用现金 100,000 / 投入本金（净）100,000 |
| ③ 买入 1 BTC @ 50,000；卖出 0.1 BTC @ 60,000 | M7 事件构造层 → M4 相对校验 → 账本 | 现金 56,000 / BTC 0.9 / 已实现 **+1,000** |
| ④ CSV 导入 买入 0.5 ETH @ 3,000 | M7 CSV 半边（模板形状 → 预览 → 确认 → LENIENT 导入） | 新增 1 行（0 解析错误）；共 3 笔交易 |
| ⑤ 看板 / ROI | M12 `PortfolioService` → M4 `PortfolioCalculator` → M5 快照 + M3 目录 | 净值 **101,000**；总收益 +1,000；**ROI +1.00%**；可用现金 54,500；未实现 0；无持仓异常 |
| ⑥ 备份导出 | M9 `CproCodec`（Argon2id + AES-256-GCM，AAD 绑明文头部） | 真实 `.cpro` 落盘；counts：交易 3 / 资金 1 |
| ⑦ 增量合并回导 | M9 `BackupMergePlanner` + `BackupRestoreStore`（单写事务） | 预览 0 待插入 / 3 去重；导入 0 行、**恢复后增量保留**；恢复前后净值·总收益·已实现完全一致（重放口径无漂移） |
| ⑧ 备份元数据 + CSV 明文导出 | M9 + 设置存储 | `backup.last_at` 落盘；CSV 含交易行且**不含任何密钥** |

> 断言全部为数值等价比较（`BigDecimal.compareTo`），不使用展示层字符串。

### 2.2 交易所回环链路（`ExchangeLoopbackIntegrationTest`）

真实 HTTP 打到本地回环桩（`BinanceAdapter(baseUrl = 回环地址)`，**未改任何生产代码**；回环桩同时观测
「签名请求必须带 `X-MBX-APIKEY` 头 + `signature` 参数」）：

| 步骤 | 断言 |
|------|------|
| 保存密钥 = 测试请求 + 首次同步 | `SyncStatus.OK`；2 笔成交入账；`source = BINANCE API`；pair 由 `exchangeInfo` 注册表切分（不猜字符串） |
| 再次同步 | 0 新增（`fromId` 游标 + `(exchange, order_id)` 去重键幂等）；`sync_logs` 有脱敏摘要 |
| 看板 | 现金 6,000 / BTC 0.4 / 已实现 +1,000 / 净值 26,000；BTC `calibratable = true`（单一交易所来源） |
| 校准执行流 | 本地 0.4 vs 交易所 0.5 → 差额 +0.1 → 锚点入库 → 持仓 0.5 / 净值 31,000；校准历史与 `sync_logs` 留痕 |
| 跨账户恢复 | 账户 B 恢复含密钥行的备份 → **用 B 的 DEK 解出的凭证成功调用交易所**（`syncNow` = OK，无密钥失效）→ 凭证重加密闭环成立；切回 A，A 的凭证仍可用 |

### 2.3 多账户隔离与异常态

- **隔离**：账户 B 建后账本为空、投入本金 0；恢复 A 的备份后 B 只见导入数据；切回 A 数据不受影响（双向）。
- **异常态**（`ErrorPathIntegrationTest`，全部在真实库上类型化上浮）：

| 场景 | 期望（interaction/PRD） | 实测 |
|------|------------------------|------|
| 错误口令登录 / 不存在的用户名 | A1 同一异常、不暴露存在性 | ✅ `InvalidCredentialsException`（两种输入同型） |
| 无本金买入 | V5 余额不足 | ✅ `INSUFFICIENT_BALANCE`（coinSymbol = USDT） |
| 撤资超额 | V7 持仓不足 | ✅ `INSUFFICIENT_POSITION` |
| 删除增资致其后交易转负 | V9 重放冲突 | ✅ `REPLAY_CONFLICT`，且失败操作**不留半成品写入**（交易 1 笔、资金 1 条不变） |
| 备份密码错 / 非 `.cpro` 文件 | BACKUP_INVALID | ✅ `WRONG_PASSWORD_OR_CORRUPTED` / `MALFORMED_FILE`；失败恢复不写 `restore.last_at` |
| 校准入口三类阻止 | PRD 4.1-5「按钮隐藏并显示相应提示」 | ✅ `MULTI_SOURCE`（仅手动记录）/ `NO_RECORDS`（无记录）/ `NO_EXCHANGE_KEY`（单一交易所来源但无密钥） |

---

## 3. 跨模块契约一致性核对（DoD ②）

**方法**：以 `docs/tech/api-contracts.md` 为契约真源，对 `domain/**` 全部 **22 个接口**逐个比对
「签名 / 返回类型 / 语义」与实现（Kotlin 源码 + 模块记录 + 决策档），并核对 P2 草稿契约的每一处漂移
是否**已在文档落档**。

| P2 草稿（§3） | 实现 | 漂移是否落档 |
|---------------|------|--------------|
| `AccountService.createAccount(req): AccountId` | `createAccount(CreateAccountReq): Session`（创建即登录）；**新增 `restoreSession()`** | ❌→✅ **F-1**（本次回写 M2 补录） |
| `AccountService` 其余 6 动作 | login/logout/switchAccount/changePassword/listAccounts/hasRememberMe 一致 | ✅ |
| `LedgerService`（交易 + 资金 + CSV 混列） | 拆为 `TransactionLedgerService`（含 `feeQuote`/`searchCoins`/`csvTemplateCsv`）+ `FundService` + `CalibrationUseCase` | ✅ M7/M8 补录 |
| `MarketService.refreshPrices/getMarketSnapshot` | `MarketRefreshService.refresh(manual, coins, fiats)` + `lastResult()` + 额度/目录状态；报价另拆 `MarketQuotesService`/`MarketWatchService` | ✅ M5/D21 补录 |
| `SyncService.syncNow/reconcilePosition` | `ExchangeSyncService`（addAndSync/updateKey/removeKey/testCredentials/syncNow/recentSyncLogs/间隔读写）+ `CalibrationUseCase.prepare/execute/history` | ✅ M6/M8 补录 |
| `BackupService.exportBackup(req)` | `exportBackup(path, password: CharArray)` + `backupMetadata`/`prepareRestore`/`exportCsv` | ✅ M9 补录（含 D24） |
| `SettingsService.get/patch/getLogs/exportLogs/generateDiagnostics` | `GeneralSettingsService` + `LogAccess` + `DiagnosticsService`（`DesktopSettingsService` 独立） | ✅ M10 补录 |
| （草稿未定义）币种主数据 | `CoinCatalog`（11 动作）+ `CoinResolver` + `FiatNormalizer` + `MarketRankProvider` | ❌→✅ **F-1**（本次回写 M3 补录） |
| （草稿未定义）桌面集成 | `DesktopSettingsService` + `AutostartService` + proxy 纯规则 + 调度纯规则/宿主窄接口 | ❌→✅ **F-1**（本次回写 M11 补录） |
| （草稿未定义）聚合页与 i18n | `PortfolioService.snapshot/coinDetail` + `AppLanguage` + `setLanguage` + ShellStatus | ❌→✅ **F-1**（本次回写 M12 补录） |
| （草稿未定义）引擎 | `ReplayEngine` / `PortfolioCalculator` / `FeeCalculator` / `ReconciliationService` | ✅ M4 补录（含 D26 成本口径补注） |
| （草稿未定义）密钥与加密 | `CryptoService`（Argon2Kdf / KeyWrap / FieldCipher） | ➖ 契约锚点 = **ADR-002**（加密口径不在 §3 重复定义；P5 核对确认无缺口） |
| M11/M12 之外 | 事件装配层（`LedgerEventAssembler`/`TransactionEventBuilder`）为 data 实现细节，经用例契约暴露 | ✅ M7/M8 补录说明 |

**F-1 处置（C0 文档回写，无产品语义变化）**：`api-contracts.md` §3 追加 **M2 / M3 / M11 / M12 / M13 补录**
及 P5 一致性核对说明；此前这些模块记录/STATUS 写了「已补录」但契约正文未回写（属**文档回写缺口**，
不是契约行为不一致——同一批接口在 P4 各模块人工门已逐条走查通过，且本次集成测试在真实装配下全部跑通）。

> 口径澄清（顺带回写）：`BackupMergePlanner.MergePlan.transactions/capitalFlows/...` = **待插入行**
> （已命中本地去重键的行计入 `duplicateSkipped`），不等于备份文件的记录全集——P5 首轮断言曾按
> 「全集」误读，已在 M9 补录与模块记录口径内确认无需改代码。

---

## 4. 跨模块问题清单与修复记录

> 级别口径见 `AGENTS.md §8.1`：C0 = 不改产品语义的勘误/实现偏差。

### P5-1 ✅ 已修复（C0）：`api-contracts.md` §3 缺 M2/M3/M11/M12 契约补录

- **现象**：契约真源对 4 个模块的接口形态无记载；后续 P6/P7/P8 或移动端按 §3 对齐时会读到过期的草稿签名（如 `createAccount(...): AccountId`、不存在的 `restoreSession`）。
- **根因**：P2 草稿契约在落地时逐模块细化，仅 M4–M10 回写了 §3 补录；M2/M3 在模块记录里标注「已补录」但正文未写入，M11/M12 未回写。
- **修复**：回写 §3 五条补录（M2/M3/M11/M12/M13）+ P5 核对说明；见 §3 表。
- **守护**：§3 的每个补录条目均指向实现（domain 接口）与模块记录，P6 可按 §3 逐条复查。

### P5-2 ⚠️ 已登记（转 P6，需人工定级）：行情网络异常丢失底层原因，真实联调不可诊断

- **现象**：真实网络冒烟首跑失败，异常仅为 `MarketApiException: Network(source=COINGECKO)`；同一客户端重跑即成功——**无法判断**当时是超时、限流还是 TLS 抖动（`guardedHttp` 把原始异常整体吞掉，cause 不入模型）。
- **证据**：`data/.../market/MarketHttp.kt` `guardedHttp`（`catch (e: Exception) → MarketRefreshError.Network`）；P5 实测记录见 §5-3。
- **为什么不在 P5 直接改**：错误模型的形状牵动用户可见文案与脱敏口径（M5 §5 规格裁决「原始异常不入错误模型」），改动属交互/错误口径调整 → 建议人工定级（倾向 C1：**保留 cause 链但不进用户文案**，供日志与诊断报告定位）。
- **到期检查点**：P6 系统测试（与 M13 登记的「运行期出站抓包实证」合并验收）。

### P5-3 ✅ 已处置（C0，测试接缝）：组合根会话持有器对装配层不可见

- **现象**：`AppBootstrap.SessionRuntime.sessions` 原为 `private`，导致「在真实组合根打开的库上装配 M6/M8 同类服务」做不到（拿不到已登录账户的 DEK 与账户上下文）——L2 回环链路测试因此无法落地。
- **修复**：改为公开属性（KDoc 注明用途与边界：写入路径仍只经 `authService` 的登录/登出/切换/改密，`close()` 统一擦除）。
- **影响面**：无行为变化（仅可见性）；`Main`/`AppHost` 未受影响；`integration/*` 与后续 P6 用例复用。

### P5-4 ⚠️ 已登记（转 P6，低风险）：备份导出对不可解密 `api_keys` 密文的失败模式

- **现象**：集成测试用占位密文造 `api_keys` 行时，`exportBackup` 直接抛 `AuthenticationFailedException: AES-GCM authentication failed`（原始加密异常，无类型化错误、无用户可读文案）。
- **触发面**：仅当库内 `api_keys` 密文与当前账户 DEK/AAD 不匹配（DB 被外部改动、位翻转损坏）——正常路径不可能出现（明文永不落盘、密文只由本服务写入）。
- **建议**：P6 评估是否包一层类型化错误 + interaction 文案（BACKUP_INVALID 家族）；不影响主流程与数据安全（拒绝导出 = 安全侧失败）。

### P5-5 ➖ 无需改动：`MergePlan` 语义误读（口径澄清，已回写说明）

见 §3 末尾口径澄清——P5 首轮断言按「备份全集」理解 `MergePlan.transactions`，实际为「待插入行」；
实现符合 M9 既定口径，仅文档表述不够显式，已在 M9 补录处补一句。

---

## 5. 实测证据

### 5.1 集成测试（本地，真实组合根）

```
./gradlew :app:test --tests "com.wuzhufolio.app.integration.*"
```

| 测试类 | 用例 | 结果 |
|--------|------|------|
| `IntegrationHarnessTest` | 1 | ✅ 真实组合根启动 + 建账户 + 目录种子 |
| `LiveMarketRefreshWiringTest`（env 门控） | 1 | ✅ **首启链路真实网络**：空目录→录入失败→刷新→目录就绪→录入成功；持仓币被默认刷新定价 |
| `CoreJourneyIntegrationTest` | 3 | ✅ 主流程 / 多账户隔离与跨账户恢复 / 登出后重新登录 |
| `ExchangeLoopbackIntegrationTest` | 1 | ✅ 回环链路（真实 HTTP + 真实签名 + 同步/校准/看板/跨账户） |
| `ErrorPathIntegrationTest` | 4 | ✅ 异常态（A1/V5/V7/V9/BACKUP_INVALID/校准三类阻止） |
| **合计** | **10**（+2 项 data 侧真实网络冒烟，门控） | **0 失败** |

**全量回归**（含上述 9 项 + live smoke 的跳过语义）：

```
./gradlew clean build detekt --no-build-cache
# → BUILD SUCCESSFUL；655 用例 = 648 执行 0 失败 0 错误 + 7 跳过（2026-09-13 修复轮后）
#   domain 219 / data 271 / ui 134 / app 31；detekt 0 issue；编译警告 0
#   跳过 7 = 4 项钥匙串真实后端（CI win/mac 实证）+ 3 项 live smoke（env 门控）
```

### 5.2 真实网络冒烟（`WZF_LIVE_SMOKE=1`，2026-09-13 实测）

```
WZF_LIVE_SMOKE=1 ./gradlew :data:test --tests "com.wuzhufolio.data.smoke.LiveNetworkSmokeTest" --rerun-tasks
```

| 端点 | 实测输出 |
|------|----------|
| CoinGecko `/simple/price` | `bitcoin=76569 USD`、`tether=0.999753 USD`（匿名公共档，**未使用 Key**） |
| CoinGecko `/coins/list` | 目录 **21,162** 条 |
| 快照落库（SQLCipher 真实库） | `stored snapshot = 76569 @ 2026-09-13T13:31:13Z` |
| Binance `/api/v3/exchangeInfo`（公开） | **3,698** 个现货交易对；`BTCUSDT` base/quote = BTC/USDT |

> 首跑曾出现一次 `Network(COINGECKO)` 瞬时失败（重跑即通过）→ 记为 **P5-2**（可诊断性）；也是本用例
> 采用 env 门控、不进默认回归的原因。

### 5.3 GUI 冒烟（WSLg，隔离数据目录，2026-09-13）

```
WUZHUFOLIO_DATA_DIR=/tmp/wzf-p5-gui JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE_FAST" \
  timeout 75 ./gradlew :app:run          # GRADLE_RC=124（窗口驻留至超时）
```

| 观测项 | 结果 |
|--------|------|
| 启动链 | `bootstrap ok | schema=12 | settings(count=4) | theme=light`、`scheduler started | syncInterval=30min` |
| 钥匙串降级 | `OS keyring unavailable … fall back to key file` + 安全提示模态（本机无 Secret Service，为 M1 既定语义） |
| 脱敏 | 日志 `market_api_key=****`、`key_backend=****`（存储后端名亦被脱敏——符合"不泄露敏感配置"口径） |
| 权限 | 数据目录/日志 `700`；`master.key`/`device.key`/`.db`(+wal/shm) `600` |
| 托盘 | `tray support | supported=false`（WSLg 无系统托盘，M11 §5-3 延期项，检查点 = P6） |
| 渲染 | 0 异常、0 报错日志 |

> **人工门（本 Agent 不可替代）**：GUI 内逐页点击走查见 §7。

### 5.4 原型视觉基准（D25 到期项，P5 检查点执行）

```
NODE_PATH=<playwright> LD_LIBRARY_PATH=<libs> node docs/design/prototype-verify.js
```

`errors = []`；83 项取值全绿；新增断言：`settingsLanguageRow = 1`、`settingsLanguageSeg = ["中文","English"]`、
`thresholdIsNumericInput = 1`（小额阈值 = 自由数值输入，与 M10 走查结论一致）。

---

## 6. 与 P4 交接开放项的衔接

| 开放项（来源） | 本阶段处置 |
|----------------|-----------|
| M5/M6/M7/M8 遗留「真实网络/交易所/校准端到端冒烟留 P5」 | **部分闭环**：CoinGecko 全链路 + Binance 公开端点已实测（§5.2）；交易所同步/校准以**真实 HTTP 回环**覆盖（§2.2）。**真实只读 Key 的线上同步仍待人工**（§7-3，Agent 无凭据） |
| D25 原型补「界面语言」行（检查点 = P5 联调或 P7 前视觉终审） | ✅ **闭环**（§5.4；台账与决策档已更新为已完成） |
| M12 遗留待定级：负持仓且无行情币的负市值仍计入净值（C2 待定级） | ⏸ **未动代码**（按 `AGENTS.md §8.4` 先提报）：**本报告不代为定级**，见 §8 待人工裁决 |
| M13 登记：`.cpro` 大载荷内存曲线 / 运行期出站抓包 / 读屏实测 / settings 键命名空间 / 登出后调度 tick 噪声 | ➖ 均归 P6（本阶段不展开；§5.2 的 live smoke 为其提供复跑入口） |
| M11 §5-2/§5-3：打包版自启 / 三平台托盘实测 | ➖ 归 P7 / P6（本机 WSLg 无托盘，已如实登记） |
| P5 新增登记（本报告） | P5-2（网络异常 cause 丢失）、P5-4（备份导出失败模式）→ P6 |

---

## 7. 怎么验收（人工门：核心用户旅程）

> 建议顺序与观察点；所有操作均在**隔离数据目录**内进行，不影响既有开发库：
> `WUZHUFOLIO_DATA_DIR=/tmp/wzf-p5-manual JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE_FAST" ./gradlew :app:run`

1. **创建与登录**：创建账户（含风险确认勾选）→ 初始化向导「稍后配置」→ 进主壳；登出 → 用「记住我」路径重启进程 → 应免密回到账户（PRD 5.1）。
2. **增资**：资金管理 → 记录增资 100,000 USDT → 总览卡「可用现金 / 投入本金（净）」即时联动。
   > **首启提示（修复后）**：启动即后台刷新一次行情（§11-2），币种目录通常在数秒内就绪；若仍是空目录
   > （离线首启），币种输入会给出「无法唯一确定」提示——联网后点顶栏「立即刷新行情」再录。
3. **交易**：交易管理 → 添加买入 1 BTC @ 50,000（计价 USDT，手续费 0）→ 再卖出 0.1 BTC @ 60,000 →
   卖出行显示该笔已实现盈利；再走一次 **CSV 导入**（下载模板 → 填 0.5 ETH @ 3,000 → 预览 → 确认）。
4. **看板 / ROI**：仪表盘应显示三行持仓（BTC/ETH/USDT）且**每行都有现价**；点 BTC 行 → 币种详情含两笔交易。
   > **数值口径（2026-09-13 修正，重要）**：净值/总收益/ROI 按**当前行情价**计算，因此**不等于**按成交价
   > 手算的值——买入后 BTC 涨跌会直接改变净值，这是预期行为（P5 首版脚本写死 101,000 / +1.00% 属口径错误）。
   > 另外稳定币按行情价折算（USDT≈0.9996），可用现金会比票面少 0.0x%（**口径问题待裁决，见 §8-1**）。
   > 自洽校验法：`可用现金 + Σ(数量 × 现价) = 净值`；`ROI = (净值 − 投入本金) ÷ 投入本金`。
5. **备份 / 恢复**：设置 → 数据管理 → 备份（自设备份密码，≥8 位含字母数字）→ 生成 `.cpro`；再改一笔交易 →
   恢复该备份（增量合并）→ 结果页「新产生异常 / 账户既存」应分列，且**本次导入新增 0 笔**（幂等）；
   随后做一次**跨账户恢复**（新建账户 → 恢复同一 `.cpro`）→ **新账户应看到全部 3 笔交易（买入 + 卖出 + CSV）
   与 1 条增资**，切回原账户数据不变。（修复前此处只导入第 1 笔——见 §11-1）
6. **异常态抽样**：错误密码登录（文案不暴露存在性）→ 无本金直接买入（V5 引导文案）→ 撤资超额（V7）→ 删除一笔增资（V9 并定位到冲突记录）→ 用错密码恢复（BACKUP_INVALID 文案）。
7. **语言与双主题**：设置 → 通用 → 界面语言切 English（全站文案与日期格式即时变化，重启保持）；顶栏 ☾ 切暗色（环形图配色随主题重渲染）。
8. **（可选，联网）真实行情**：设置 → 行情与同步 → 立即刷新（无 Key 公共档）→ 行情页应出现真实价格与数据源指示；如需验证真实交易所同步，见下条。

---

## 8. 人工裁决记录与剩余待决项

**2026-09-13 人工裁决（3 项，均已实施见 §11）**：

| # | 事项 | 裁决 | 状态 |
|---|------|------|------|
| 1 | 稳定币折算口径 | **方案 B：白名单稳定币优先 1:1 锚定**（非白名单照市价）→ **第二轮细化**：锚定集合**固定 = USDT**；现金白名单默认 {USDT}、其余稳定币可增删且**按市价折算** | ✅ 已实施（D27 · C2 · T4.6；**由 D28 修订**，另见 §11-9） |
| 2 | P5-2 行情网络异常 cause 丢失 | **C1** | ✅ 已实施（`MarketApiException`/`ExchangeApiException` 保 cause；行情编排失败分支记 `cause=` 摘要，只进日志经脱敏漏斗） |
| 3 | 启动时自动同步交易数据（PRD 4.2） | **C1** | ✅ 已实施（`AuthGate.onSessionActive` → 登录/建户/免密恢复/切换账户各触发一次「同步 + 行情刷新」） |
| 4 | 负持仓币的负市值是否计入净值 | **方案 B：不计入** | ✅ 已实施（D29 · C2 · T4.7 · 仪表盘显式提示） |

**剩余待决（2 项后续决定 + 1 项信息）**：

1. ~~是否回写《跨端共享规范》/PRD 正文~~ → ✅ **已按人工指令回写（2026-09-13）**：
   `桌面端prd.md` **V1.9 → V2.0**、`跨端共享规范.md` **V1.0 → V1.1**；评审材料
   `docs/prd/桌面端prd-V2修订说明.md`（差异清单 + 影响面核对 + 评审要点）——**待人工评审确认后生效为新基线**。
2. **是否允许 USDT 之外再增锚定币**（D28 §5-3：现口径锚定集合固定不可配置）。
3. ~~行情页自选开箱种子是否收敛为 USDT~~ → ✅ **已按人工拍板收敛**（2026-09-13）：`DEFAULT_WATCH_SEED` = 仅 USDT；
   原型与 `prototype-verify.js` 断言同步（复跑 `errors=[]`）。
3. **真实交易所只读 Key 冒烟**（Agent 无凭据、且不应索取）：若人工提供只读 Key，可执行
   「设置 → API 管理 → 添加 Binance 只读 Key → 保存即首次同步」并核对同步记录；否则顺延 P6。

---

## 9. 复跑命令汇总

```bash
export JAVA_HOME=$(mise where java)

# 全量回归（含 9 项集成测试；live smoke 默认跳过）
./gradlew clean build detekt --no-build-cache

# 集成测试聚焦
./gradlew :app:test --tests "com.wuzhufolio.app.integration.*"

# 真实网络冒烟（联网，不使用任何 Key）
WZF_LIVE_SMOKE=1 ./gradlew :data:test --tests "com.wuzhufolio.data.smoke.LiveNetworkSmokeTest" --rerun-tasks -i

# GUI 冒烟（隔离数据目录；WSL2 无 GPU 时用 SOFTWARE_FAST）
WUZHUFOLIO_DATA_DIR=/tmp/wzf-p5-gui JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE_FAST" timeout 75 ./gradlew :app:run

# 原型视觉基准（D25 补行守护）
NODE_PATH=$(find ~/.npm/_npx -maxdepth 3 -name playwright -type d | head -1)/.. \
LD_LIBRARY_PATH=$HOME/.local/plibs/root/usr/lib/x86_64-linux-gnu \
node docs/design/prototype-verify.js
```

---

## 10. 需求回溯

| 本报告条目 | 需求锚点 |
|------------|----------|
| 核心旅程（登录→增资→交易→看板/ROI→备份恢复） | PRD 故事 2.1/2.3/3.2/4.1/5.2/6.1/7.1、附录 A 黄金用例 1–12；`AGENTS.md §4 P5` |
| 多账户隔离与跨账户恢复 | PRD §10-1/2/5/7/8、故事 5.2-8、共享规范 §8 |
| 异常态类型化上浮 | PRD 全局说明「统一异常处理」、interaction A1/A3/V5/V7/V9/B1/B2 |
| 契约一致性核对 | `docs/tech/api-contracts.md` §3、`AGENTS.md §8.6`（有效需求版本串） |
| D25 原型补行 | `docs/dev/decisions/D25-界面语言设置与全量zh-en.md` §4（到期检查点 = P5 联调） |
| 真实网络冒烟 | PRD §1.1-1/2（本地化与零遥测：仅 3 主机出站、匿名档不消耗用户额度）、ADR-003 §1 |

---

## 11. 人工验收反馈与修复轮（2026-09-13）

> 人工按 §7 实机走查的结果：步骤 **1 / 6 / 7 / 8 通过**；步骤 **2 / 3 / 4 / 5** 报出问题。
> 逐条根因（均已在真实组合根上复现）与处置如下。

### 11-1 【P0·已修复】跨账户恢复丢交易行（人工问题 5）

- **现象**：账户 A 有 3 笔交易（手动买入 1 BTC、手动卖出 0.1 BTC、CSV 买入 0.5 ETH），备份后在新账户恢复，
  只导入 **1 笔**（第一笔买入）。
- **根因**（`BackupMergePlanner.planTransactions` + `txOrderKey`）：去重键 `交易所|订单号` 对
  订单号为 null 的**手动 / CSV 行**统一退化为 `"BINANCE|"`，导致**同交易所的全部手动交易互相判重**——
  第 2、3 笔被当作「重复」静默计入 `duplicateSkipped`。**增量合并与全量覆盖两条路径都受影响**
  （全量覆盖会先清空账户表再导入 → 丢行即永久丢数据）。
- **为何 P4/P5 首轮测试没抓到**：`BackupMergePlannerTest` 的黄金用例 10 形状里「无订单号行」只有 1 笔；
  P5 集成测试的跨账户用例当时也只有 1 笔。**单笔样本掩盖了批内互判重**。
- **修复**：`txOrderKey` 改为**仅当订单号非空时返回键**（null / 空白 → 不参与判重，只按 uuid 去重）；
  `DefaultBackupService` 的既有键装配同步收紧（空订单号不入键）。
- **回归**：`BackupMergePlannerTest` +4 项（3 笔无订单号全导入 / 全量覆盖不丢行 / 有订单号仍按订单号去重 /
  空订单号不入既有键）；`CoreJourneyIntegrationTest.多账户隔离与跨账户恢复` 扩为 3 笔（买入 + 卖出 + CSV）
  并断言导入 3 笔、`missingCoinSkipped = 0`、时间序齐全——**该用例在修复前必红（3→1）**。
- **影响面**：仅合并规划与既有键装配两个点；备份**文件格式与加密不变**（旧 `.cpro` 无需迁移，
  用修复后的版本重导即可完整恢复）。

### 11-2 【P1·已修复】首次运行目录为空 → 资金/交易录入被阻断（人工问题 2）

- **现象**：新建账户后立即「记录增资」，币种填 usdt 点保存报「未收录」；过一段时间（或手动刷新后）才可录入；
  期间币种输入框也没有候选提示。
- **根因**：账本录入依赖 `coins` 目录（符号→cg_id 解析），而目录只由**行情刷新**维护；调度宿主的行情循环
  **先等满一个刷新间隔**（默认 5 分钟）才首次执行 → 全新安装的头几分钟目录为空，
  `CoinResolver` 返回 NotFound，文案落在「币种无法唯一确定：未收录」（**误导**：该币在上游是存在的）。
- **修复**：行情循环**启动即刷新一次**（`BackgroundScheduler.marketLoop` 首轮不等待；手动/额度/退避口径不变，
  仍按非手动计入）。
- **回归**：`BackgroundSchedulerTest` +1（启动后立刻发生一次刷新，frequency 故意设为 30 分钟）；
  `LiveMarketRefreshWiringTest`（env 门控，真实网络）复现并验证：空目录 → 录入失败 →
  一次刷新 → 目录就绪（`tether` 落库）→ 录入成功。
- **残留**：**离线首启**仍会命中空目录（无法自动获取），此时错误文案仍显示「未收录」。
  建议后续（需定级）：文案区分「未收录」与「本地目录尚未就绪（请先刷新行情）」并给一键刷新入口。

### 11-3 【P1·已修复】持仓币不被自动刷新 → 看板长期「无行情」（人工问题 3 前半）

- **现象**：买入 1 BTC 后仪表盘「资产分布」没有 BTC，总资产只显示现金；**手动点刷新行情后** BTC 才出现。
- **根因**：两处刷新入口（定时刷新、设置页「立即刷新」）**不传币集** → 编排回退到
  `MarketConfig.DEFAULT_FALLBACK_COINS`（**只有 4 个现金白名单币**），用户持仓的新币永远拿不到现价。
  这正是 M5 模块记录 §6 登记、但一直未落地的「**持仓币集注入点归 M7/M12**」遗留。
- **修复**：`DefaultMarketRefreshService` 增注入式 `defaultCoins` 提供者；组合根晚绑定为
  **持仓（聚合页快照行）∪ 行情页自选**；全空（全新账户）仍回退开箱 4 币，行为不变。
  顶栏/行情页/仪表盘既有显式币集路径不受影响。
- **回归**：`DefaultMarketRefreshServiceTest` +2（空币集用注入集合 / 注入为空仍回退 4 币）；
  `LiveMarketRefreshWiringTest` 实测 `coins=4 → coins=5`（买入后被自动纳入）、`bitcoin:0.01@76764` 已定价。

### 11-4 【口径·已裁决并修复：D27】稳定币按市价折算，导致「金额打 0.9996 折」（人工问题 2/3/4 的疑问部分）

- **现象**：增资 100,000 USDT → 可用现金 **99,964.80**；卖出 0.1 BTC@60,000 → 已实现 **999.65** 而非 1,000。
- **根因**（`TransactionEventBuilder.priceOf` 折算链）：`① 事件时刻前最近快照 → ② USD 锚定 1:1 → ③ 最近可得`。
  - 增资发生时**尚无** USDT 快照 → 命中 ② 锚定 → 事件值 = 100,000（所以「投入本金」显示 100,000）；
  - 之后的交易发生时**已有** USDT 快照（真实价 0.999648）→ 命中 ① → 50,000×0.999648 = 49,982.40（成本基数）、
    6,000×0.999648 = 5,997.89（卖出所得）→ 已实现 = 999.648 ≈ **999.65**（与人工实测完全一致）；
  - 「可用现金」= 现金币种**市值**（数量 × 现价）→ 50,000×0.999648 = 49,982.40（人工实测 49,982.40 ✓）。
- **结论**：**这是设计口径而非计算错误**——系统把稳定币当普通资产按真实市价折算。但它带来三个用户体验问题：
  ① 票面金额与展示金额出现 0.0x% 的「莫名损耗」；② 稳定币计价的交易其已实现盈亏偏离手算值；
  ③ 净值随稳定币微小波动而漂移（现金类资产不应如此）。
- **人工裁决（2026-09-13）**：**方案 B —— 白名单稳定币优先 1:1 锚定（非白名单照市价）**；
  白名单 = 默认 USDT/USDC/DAI/TUSD（**固定不可移除**）+ 设置「稳定币白名单」**可扩展项**（共享规范名词表既有口径）。
- **处置（已实施，C2 mini 闭环）**：决策档 `docs/dev/decisions/D27-稳定币1比1折算口径.md` + 台账 + 任务 **T4.6**；
  落点 = `TransactionEventBuilder.priceOf`（锚定判定提前，并公开 `isAnchoredUsdStable`）+
  `DefaultPortfolioService.loadMarket/compute24h`（锚定币现价与 24h 前价恒 1，快照仅用于 `priceAsOf` 展示）。
- **实测（真实网络 + 真实组合根）**：修复前 `tether:99500.00@0.999664` → 修复后 **`tether:99500.00@1`**
  （BTC 仍按市价 `0.01@77767`）；`DefaultPortfolioServiceTest.d27…` 复现人工三个数值
  （可用现金 100,000 / 已实现 1,000 / 净值 101,000 / ROI +1.00%）。
- **边界**：非白名单币照市价、基础法币非 USD 不锚定、扩白名单项即刻生效；`.cpro` 格式未变（历史备份重导后按新口径重算派生值）。

### 11-5 【我的文档缺陷·已修正】§7 验收脚本写死数值（人工问题 4 的疑问部分）

- **现象**：人工「没法看到 101,000 的净值，总收益、ROI 都对不上」。
- **根因**：§7 原写的 101,000 / +1,000 / +1.00% / 54,500 是**集成测试里用固定价（BTC 50,000、ETH 3,000、
  稳定币 1:1）注入快照**算出的轨迹；真实运行时行情刷新会用**当前市价**（实测 BTC≈76,764、USDT≈0.9996），
  净值自然不同。**这不是 bug，是我的验收脚本口径错误**（未标注「数值随行情变化」）。
- **修正**：§7 步骤 2–5 重写为**自洽校验**（`可用现金 + Σ数量×现价 = 净值`、`ROI = (净值 − 投入本金)/投入本金`），
  并明确「净值随行情变化属预期」。**集成测试仍以固定价做确定性断言**（测试与真机口径分离）。

### 11-6 待补充信息：CSV 导入 ETH 时的 toast（人工问题 3 后半）

- 现象描述：「导入 eth 时弹出一个 toast，好像是说币种没在库中，但记录是存在的，重新刷新行情也会显示现价」。
- 已排查：CSV 解析/确认路径中与「币种」相关的提示有三类——预览「未解析 N 条（币种未收录）」、
  行状态「未解析」、以及行情侧「「eth」暂无行情数据」；三者的触发条件互不相同，
  且**在 11-2/11-3 修复前**（目录刚就绪 / 持仓币未定价）出现任一条都属于当时口径。
- **请人工提供该 toast 的原文**（或截图），以便定位是「导入未解析」还是「行情缺失」分支；
  修复 11-2/11-3 后两者的触发面均已显著收窄。

### 11-7 人工裁决实施（2026-09-13，第二轮）

| 裁决 | 实施 | 回归 |
|------|------|------|
| 稳定币 1:1 锚定（D27 / C2 / T4.6） | `TransactionEventBuilder.priceOf` 锚定优先 + 公开 `isAnchoredUsdStable`；`DefaultPortfolioService` 现价与 24h 同源锚定 | `DefaultPortfolioServiceTest` 口径反转 2 项 + 新增 4 项（锚定优先 / 非白名单照市价 / 扩白名单项 / 端到端复现人工数值）；真实网络实测 `tether@1` |
| P5-2 cause 保留（C1） | `MarketApiException`/`ExchangeApiException` 增 `cause`；`guardedHttp`/解析失败/适配器网络分支保留原因链；行情编排失败分支记 `cause=` 摘要（只进日志） | `MarketHttpClientTest` +1（原因链可回溯）、`BinanceAdapterTest` 增断言 |
| 启动时自动同步（C1） | `AuthGate` 增 `onSessionActive`（登录/建户/免密恢复/切换账户各一次）；`Main` 组合根触发一次「同步 + 行情刷新」 | `AuthFlowUiTest` +1（激活回调恰好一次、登出不触发） |

### 11-8 修复轮回归结果

| 检查 | 结果 |
|------|------|
| `./gradlew clean build detekt --no-build-cache` | 见 `STATUS.md` P5 节（13 项集成测试含新增 4 项 + 全量用例 0 失败、detekt 0、警告 0） |
| 新增/更新测试 | 域层 +4（合并规划）、数据层 +3（默认币集 2 / 启动即刷新 1）、app 层 +1（真实网络首启链路；另跨账户用例扩为 3 笔断言） |
| 复现证据 | 11-1 复现测试修复前必红（3→1）；11-2/11-3 由 `LiveMarketRefreshWiringTest` 真实网络实测通过（`coins=4 → coins=5`） |
| **真机首启实证**（隔离数据目录，`:app:run`） | 启动后 **1.5s** 即触发 `market refresh started manual=false`，**5s** 后 `market refresh finished source=COINGECKO coins=4 untracked=0 error=null` —— 目录在窗口出现后数秒内就绪（修复前需等 5 分钟） |

### 11-9 人工裁决实施（2026-09-13，第三轮：D28 / D29）

| 裁决 | 实施 | 回归 |
|------|------|------|
| **D28（amends D27）**：1:1 锚定固定 = USDT；现金白名单默认 {USDT}，其余稳定币可增删且按市价折算 | `PortfolioCalculator.ANCHORED_COIN_IDS` 显式锚定集合 + `DEFAULT_CASH_COIN_IDS` 收敛为 {tether}；`CASH_COIN_DEFAULTS` 同步；`TransactionEventBuilder` 参数改为 `anchoredCoinIds`（锚定与白名单解耦）；行情页自选种子解耦（`MarketConfig.DEFAULT_WATCH_SEED`） | `PortfolioCalculatorTest` 默认集合断言 + 新增「扩展项计入现金但按市价」；`GeneralSettingsServiceTest` 2 处；`DefaultPortfolioServiceTest` 扩展项用例翻转 |
| **D29（方案 B）**：负持仓币市值不计入净值与可用现金 | `PortfolioCalculator.compute` 三分支聚合 + `PortfolioMetrics.anomalousExcludedFiat`；仪表盘 `dashboard-anomaly-notice` 显式提示（中英双档）；`interaction.md §2.8` | `PortfolioCalculatorTest` 负持仓用例改写 + 新增「负持仓现金币不计入可用现金」；`PortfolioPagesUiTest` +1 |

**口径影响提示（人工复验要点）**：
1. 增资 100,000 USDT → 可用现金 = **100,000**（USDT 恒 1:1）；
2. 若账户持有 USDC/DAI/TUSD 等，**默认不计入「可用现金」**（净值和资产列表不受影响）；
   如需计入，请到 设置 → 通用 →「稳定币白名单」添加该币——**添加后按市价折算**（如 USDC 0.9998）；
3. 负持仓币（导入路径产生的数据缺口）市值不再扣减净值，仪表盘顶部会出现
   「⚠ N 个币种持仓为负…其市值 −$X 未计入净值与可用现金」提示；补录/校准后自动恢复。

### 11-10 人工拍板执行（2026-09-13，第四轮：PRD 基线回写 + 自选种子收敛）

| 拍板 | 执行 | 证据 |
|------|------|------|
| **回写《跨端共享规范》/PRD 正文** | `跨端共享规范.md` **V1.1**（新增文档版本历史与移动端对齐提示；名词表：资产净值/可用现金余额/稳定币白名单改写 + 新增「1:1 锚定折算」「持仓异常」；§2 折算价格解析与导入路径例外修订）；`桌面端prd.md` **V2.0**（副标题 + 修订历史行 + 名词 3 处 + 全局说明 2 处 + 故事 6.1/7.2 + 附录 A 用例 6） | 差异清单与评审门：`docs/prd/桌面端prd-V2修订说明.md`；台账 D27/D28/D29 行标注「已回写 + 升版」 |
| **行情页自选种子收敛为仅 USDT** | `MarketConfig.DEFAULT_WATCH_SEED` = `[tether]`；原型 `WATCH_SEED` 同步；`prototype-verify.js` 断言（默认 1 行 / 加 BTC 后 2 行 / 移除后 1 行） | Playwright 复跑 `errors = []`、83 项取值全绿；`MarketWatchServicesTest` 3 处期望更新 |

> **有效需求版本串（P6/P8/移动端对齐统一引用）**：**PRD V2.0 + Δ{D21, D24, D25, D26, D27, D28, D29, D30, D31, D32}**。
> 移动端 PRD/SRD 正文随下一版本启动时按《跨端共享规范》V1.1 对齐（本轮不启用移动端工作）。

### 11-11 人工验收反馈（2026-09-13，第五轮：缺价假亏损 + 锚定币口径确认）

**问题（人工）**：「增资 100,000 后买入 1 BTC@50,000，仪表盘 ROI 卡显示 **−50%**，是否正确？是否是行情刷新还未更新 BTC 的原因？」

**诊断结论：人工判断正确——这是「缺价币不计入净值」口径 + 数据未就绪的叠加，不是计算错误，但属必须修的表达缺陷。**

| 环节 | 事实 |
|------|------|
| 计算 | `净值 = Σ 有价持仓市值`（缺价不计，PRD 全局说明）→ 此刻只有现金 50,000（USDT 1:1） |
| 结果 | `总收益 = 50,000 + 0 − 100,000 = −50,000` → **ROI = −50%** |
| 触发条件 | 买入的 BTC **尚无本地价格快照**（记录成交不会触发行情刷新；刷新时机 = 启动 / 频率间隔 / 窗口恢复 / 手动） |
| 复核 | 新增回归用例 `p5UnpricedHoldingMakesRoiLookLikeALossUntilQuotesArrive`（缺价 → −50.00%；补价后 → 0.00%）——**数值与人工实测一致** |

**处置（两层，C0 实现补全／表达补全，未改任何产品语义）**：

1. **聚合页缺价自动补价**：`PortfolioViewModel.maybeAutoPrice` —— 加载快照时若发现缺价持仓，**静默补价一次**
   （刷新币集 = 持仓 ∪ 自选；60s 冷却 + 服务内单飞，避免「上游确无该币」时形成刷新风暴）。
   覆盖所有录入路径（手动增删改 / CSV 导入 / API 同步 / 恢复），无需逐个页面接线。
2. **仪表盘显式提示**：净值/ROI 卡上方新增 `dashboard-unpriced-notice`
   「⚠ N 个币种暂无行情，其市值未计入净值与 ROI（已自动尝试补价；也可点「刷新行情」重试…）」
   —— 让用户能区分**假亏损**与真实亏损（与 D29 的排除提示同款做法）。

**同轮人工裁定（口径确认）**：「不再允许 USDT 之外再增锚定币；**锚定币与稳定币是两个概念**——
稳定币可以增加，但不是 1:1 锚定，也有实时价格。」→ 与 D28 现行实现一致（锚定集合固定 = {USDT}；
白名单扩展项按市价折算）；已把该结论写进 `D28 §5-3`、台账 D28 行，并在《跨端共享规范》V1.1 与
PRD V2.0 的「1:1 锚定折算」术语中**明文写出「锚定币 ≠ 稳定币」**，避免后续再被混读。
