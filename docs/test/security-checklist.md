# WuZhuFolio 安全自查清单（M13 · T13.1）

> **范围**：`AGENTS.md §1.1` 五条硬约束**逐条核验**（回溯 PRD §1.1 / 共享规范 §8）。
> **方法**：① 源码静态取证（read/grep，逐条给 `文件:行号`）；② 依赖与构建面核验（version catalog / 模块构建脚本 / 运行期依赖树）；
> ③ 行为测试清单（既有守护测试 + M13 新增结构守护）；④ 本次自查发现项的**处置或登记**。
> **产物关系**：本清单是 P4-M13 的 T13.1 交付物；P6 系统测试以本清单为输入复跑并逐条打勾（`AGENTS.md §4 P6`）。
> **日期**：2026-09-12 · **基线**：有效需求 = PRD V1.9 + Δ{D21, D24, D25, D26}（`docs/dev/增量台账.md`）

---

## 0. 结论汇总

| # | 硬约束 | 结论 | 阻断项 |
|---|--------|------|--------|
| 1 | 数据本地化（交易/密钥/流水仅存本地，禁云端上传） | ✅ 通过（出站面收敛为 3 主机白名单，全部落盘路径在本机数据目录） | 0 |
| 2 | 零遥测（无遥测/统计上报/第三方分析 SDK） | ✅ 通过（依赖面 0 命中 + 日志 0 网络 appender + 新增结构守护测试） | 0 |
| 3 | 密钥与加密（DEK/KEK 分层、AES-256-GCM、凭据不落盘、日志脱敏） | ✅ 通过（本轮补齐 5 项实现偏差：退出擦除/权限收紧/统一脱敏漏斗/常量时间比较/KDF 下限） | 0 |
| 4 | 两类独立 API（行情刷新 ⊥ 交易同步） | ✅ 通过（客户端/凭据/调度/额度四维独立 + 新增实例隔离守护测试） | 0 |
| 5 | 多账户隔离 + `.cpro` 备份（明文头部 + AES-256-GCM 载荷） | ✅ 通过（账户级查询全部带 `account_id`；备份范围与格式逐字段对齐 ADR-005） | 0 |

> **CI 复核（2026-09-12）**：三平台 CI（`build` ×3 + `package` ×3）**六 job 全绿**
> （[run 34698502287](https://github.com/mapleafly/wuzhufolio/actions/runs/34698502287)）——
> 本清单的守护测试在 ubuntu/macos/windows 均通过；4 项「钥匙串真实后端」用例的执行结果由
> `test-results-*` 构件逐项可核验（CI 改进登记项闭环）。

**本次自查发现与处置**：13 项发现（F1–F13）——**7 项已在本轮修复并加守护测试**（§6），**6 项为设计已知面 / 需人工或 P6 决策**（§7 登记，其中 2 项转 P6 用例、4 项为已接受设计口径）。
**残留风险**：§7 全部条目均有到期检查点，**无未登记的悬空项**。

**自动化证据（本地）**：`./gradlew clean build detekt --no-build-cache` → **636 用例（632 执行 0 失败 + 4 钥匙串真实后端跳过）** + detekt 0 + 编译警告 0（M13 新增 22 项：结构守护 7 + API 隔离 2 + 权限 4 + 脱敏漏斗 5 + KDF 下限 1 + 备份边界 3 + 密钥文件自愈 1 + 路径误伤回归 1）。

---

## 1. 硬约束 1：数据本地化

> PRD §1.1-1：交易记录、API 密钥、资金流水只存用户设备本地，禁止任何云端上传/传输。

| 核验点 | 证据 | 结论 |
|--------|------|------|
| 出站主机白名单（穷举） | 运行期仅 3 个主机常量：`data/.../market/MarketConfig.kt:22`（`api.coingecko.com`）、`:27`（`pro-api.coinmarketcap.com`）、`data/.../exchange/ExchangeConfig.kt:11`（`api.binance.com`）；用户可见注册链接 2 条（`ui/.../market/MarketCopy.kt:44-45`）经 `Desktop.browse` 交系统浏览器，不携带数据 | ✅ |
| 无其它网络原语 | 主源码 grep `Socket(`/`URLConnection`/`openConnection`/`java.net.http`/`WebSocket`/`ServerSocket` **零命中**（唯一 `java.net.SocketAddress` 为类型导入，`data/.../proxy/SystemProxyDetector.kt:11`）；`SystemProxyDetector` 的 CoinGecko `PROBE_URI`（`:92`）只作为 `ProxySelector.select(uri)` 的匹配输入，**不发请求**（本类不触网） | ✅ |
| 数据目录单一真源 | `app/.../AppDirs.kt:11`（`WUZHUFOLIO_DATA_DIR` → `-Dwuzhufolio.dataDir` → `~/.wuzhufolio`）；`ensureDataDirs():25` 创建并收紧 0700 | ✅ |
| 落盘物清单（全部本机） | DB `AppDirs.kt:15` + `WzDatabase.kt:50`（`jdbc:sqlite:` 本地路径）；日志 `Main.kt:68` + `logback.xml:5,13`；备份/恢复临时备份 `AppBootstrap.kt:264`；降级密钥文件 `AppBootstrap.kt:194/448`；导出物（`.cpro`/CSV/日志/诊断）均走用户选择的本地路径 | ✅ |
| 无上传/同步语义代码 | grep `upload`/`report`/`telemetry`/`analytics`/`beacon`/`crash`：命中项仅为 i18n 文案（"Local data · Zero telemetry"）与设置页「无遥测声明」行 | ✅ |
| 新增结构守护 | `data/src/test/.../security/SecurityGuardTest.kt`：出站主机白名单 / 禁明文 `http://` 出站 / 数据目录单一真源（3 项） | ✅ |

**隐私最小化观察项（不违反约束，转 P6）**：行情请求会把用户**持仓/自选币种 id 集合**发往行情源（`data/.../market/MarketHttp.kt:142` `ids=`、`:233` `id=`；交易所侧 `BinanceAdapter.kt:88` `symbol=`）。载荷**不含**交易记录、金额、流水、密钥，属行情 API 的功能必需参数；如 P6 认为需进一步最小化，可改为服务端无关的批量匿名取价（需评估额度成本）。

---

## 2. 硬约束 2：零遥测

> PRD §1.1-2：不内置任何遥测、统计上报或第三方分析 SDK。

| 核验点 | 证据 | 结论 |
|--------|------|------|
| 直接依赖面 | `gradle/libs.versions.toml`（56 行）与四个模块 `build.gradle.kts`：无 firebase/sentry/amplitude/mixpanel/bugsnag/datadog/crashlytics/opentelemetry/umami/matomo/countly/newrelic/elastic-apm/segment/appcenter/instabug（见守护测试词表） | ✅ |
| 传递依赖面 | `./gradlew :app:dependencies --configuration runtimeClasspath` 实测解析：全部为 Compose/Skiko、Ktor/OkHttp、Exposed/SQLCipher、BouncyCastle、javakeyring（+dbus/JNA）、Koin、slf4j/logback、coroutines/serialization —— **无遥测/分析/崩溃上报类库** | ✅ |
| 无自动更新检查 | 无 Sparkle/WinSparkle/appcast/update4j 类依赖；关于页仅显示版本号与官方渠道链接（PRD §12 口径） | ✅ |
| 日志不外发 | `app/src/main/resources/logback.xml` 仅 ConsoleAppender + RollingFileAppender，**无 Socket/SMTP/JMS/DB/Syslog appender** | ✅ |
| 许可清单一致性 | M13 核实 `docs/tech/dependency-licenses.md:28` 的 `com.dorkbox:SystemTray`（M11 备选）**未进构建**（version catalog 与四模块脚本均无该坐标）→ 已就地更正标注 | ✅ |
| 新增结构守护 | `SecurityGuardTest`：遥测依赖词表扫描（构建脚本）+ 网络 appender 扫描（logback 配置） | ✅ |
| 界面声明一致性 | 设置→关于「无遥测声明」行（`ui/.../settings/SettingsPage.kt` + `ui/i18n/SettingsStrings.kt:270-271`）与实现一致；仪表盘「安全与隐私」面板同口径 | ✅ |

---

## 3. 硬约束 3：密钥与加密

> PRD §1.1-3：分层密钥（DEK/KEK）、AES-256-GCM、密码与登录凭据永不落盘、日志脱敏。

### 3.1 分层密钥链（生成 → 派生 → 包裹 → 落库 → 解包 → 擦除）

| 环节 | 证据 | 结论 |
|------|------|------|
| DEK 随机 32B | `domain/.../security/CryptoService.kt:19`（`newDek()`，`SecureRandom`） | ✅ |
| KEK = Argon2id(密码, salt) | `data/.../accounts/DefaultAccountService.kt:52-54` → `CryptoService.kt:39-44` → `domain/.../security/Argon2Kdf.kt:20-42`（`ARGON2_id` / `ARGON2_VERSION_13` / 32B 输出 / 16B 盐） | ✅ |
| 参数冻结与基线 | `domain/.../security/KdfParams.kt:36` `DEFAULT = m=64MiB,t=3,p=1`；`:33` `OWASP_MINIMUM = 19MiB,t=2,p=1`；仓库/备份同源存储串 | ✅ |
| DEK 被 KEK 包裹落库 | `DefaultAccountService.kt:56-61` + `AccountRepository.kt:61-94`（**单事务先插后包**，AAD = `account_id+"wrapped_dek"`）；`KeyWrap.kt:19-25`；DDL `Migrations.kt:82` | ✅ |
| 解包即认证 | `DefaultAccountService.kt:204-226`（快速判错 → GCM 解包认证 → `finally` 擦 KEK）；`AesGcm.kt:34-47`（tag 失败 → 认证失败） | ✅ |
| DEK 仅内存 + 会话结束擦除 | `data/.../accounts/ActiveSessionStore.kt:7-28`（`set` 先擦旧、`clear` 全擦）；调用点：登出/切换/改密（`DefaultAccountService.kt:125/141/177-178`）+ **进程退出（M13 修复：`app/.../AppBootstrap.kt:113-124` `SessionRuntime.close()` → `sessions.clear()`）** | ✅（本轮补齐退出路径） |
| 密码不落盘 | 密码全程 `CharArray`（`AccountService.kt:28-30`），仅用于 KDF；落库只有 `password_hash = SHA-256(KEK‖"verify")`（`KekVerify.kt:16-20`，不可反推密码）；`Argon2Kdf.kt:39-41` 内部拷贝 `finally` 清零 | ✅ |
| 记住我令牌 | 随机 256-bit（`CryptoService.kt:22`）+ `wrapped_dek_session`（AAD=`account_id+"session"`）入 OS 钥匙串（`RememberMeStore.kt:15-23`）；登出/改密/切换清除 | ✅ |

### 3.2 算法与参数

| 项 | 实测值 | 证据 |
|----|--------|------|
| 对称算法 | AES-256-GCM（`AES/GCM/NoPadding`，key 32B 强制校验） | `domain/.../security/AesGcm.kt:16,25,26,38` |
| nonce / tag | 12B 随机（每次加密）/ 128-bit tag | `AesGcm.kt:17,27,19,39` |
| AAD 隔离面 | DEK 包裹 = `account_id+"wrapped_dek"`；会话 = `+"session"`；凭证四列 = `account_id\|api_key_id\|column`；设备秘密 = `device-secret:<purpose>`；`.cpro` = 头部完整字节 | `KeyWrap.kt:15-16`、`FieldCipher.kt:41-42`、`DeviceSecretCipher.kt:41-42`、`CproCodec.kt:60/145` |
| 整库加密 | SQLCipher（Willena fork，raw-key 注入，WAL/busy_timeout/foreign_keys 每连接生效） | `data/.../db/WzDatabase.kt:70-90` |
| 行情 Key 加密边界 | 设备密钥（`KeychainAccounts.DEVICE_KEY`）+ `DeviceSecretCipher` → settings **全局行**；**不进 `.cpro`** | `AppBootstrap.kt:448`、`DeviceSecretStore.kt:27-41`、`MarketConfig.kt:11-18` |
| 交易所 Key 加密边界 | 账户 DEK + `FieldCipher` 字段级加密 `api_keys` 四列（编辑不回显） | `DefaultExchangeSyncService.kt:81-83/185-189`、`ApiKeysTable.kt:27-30` |
| 备份密钥派生 | Argon2id(备份密码, 头部 salt/参数) → AES-256-GCM 载荷；用后擦除 | `domain/.../backup/CproCodec.kt:49-65/128-165` |
| **KDF 降级防线（本轮新增）** | 存量参数低于 OWASP 基线（`m<19MiB` 或 `t<2`）**拒绝装载** | `KdfParams.kt:42-60` + 测试 `KdfParamsTest`（downgrade rejected） |

### 3.3 凭据存储面（逐类核验「是否会落明文」）

| 凭据 | 位置 | 加密 | 明文落盘 |
|------|------|------|----------|
| 用户账户密码 | 不落盘 | 仅 KDF 输入（CharArray，用后擦） | ❌ 否 |
| 记住我令牌 | OS 钥匙串（降级 0600 文件） | 条目自身即密钥材料 | ❌ 否 |
| 交易所 API Key/Secret/passphrase/extra | `api_keys` 表（整库加密） | 账户 DEK 字段级 GCM | ❌ 否 |
| 行情 CG/CMC Key | settings 全局行（整库加密） | 设备密钥 GCM（purpose AAD） | ❌ 否 |
| DB 主密钥 / 设备密钥 | OS 钥匙串（降级 0600 文件） | — | ❌ 否 |
| DEK | 仅内存（进程退出即擦） | — | ❌ 否 |
| 备份密码 | 不落盘（用户输入） | 仅 KDF 输入 | ❌ 否 |
| `.cpro` 载荷内交易所凭证 | 备份文件 | Argon2id(备份密码) + AES-256-GCM | ❌ 否（密文；强度=备份密码，见 §7-3） |

### 3.4 文件权限（M13 加固）

| 对象 | 权限 | 证据 |
|------|------|------|
| 数据目录 / 日志目录 / backups | **0700**（创建即受限 + 每次启动自愈） | `app/.../AppDirs.kt:25-34`、`domain/.../security/FilePermissions.kt` |
| `master.key` / `device.key` / `remember-me.dat` | **0600**（**创建即 0600**，消除先写后 chmod 窗口；读路径自愈历史文件） | `MasterKeyStore.kt:157-180`、`RememberMeStore.kt:149-174` |
| DB 文件（含 WAL/SHM） | **0600** | `WzDatabase.kt:59-62` |
| `.cpro` 备份 / CSV 明文导出 / 日志导出 / 诊断报告 | **0600** | `DefaultBackupService.kt:638-649`、`FileLogAccess.kt:37`、`Main.kt:283-293` |

### 3.5 日志脱敏

| 核验点 | 证据 | 结论 |
|--------|------|------|
| 脱敏规则 | `domain/.../redaction/LogRedactor.kt:13-31`：敏感键名（key/secret/passphrase/password/token/session）整值遮蔽 + ≥32 位疑似密钥/签名/令牌裸串遮蔽；纯函数、幂等 | ✅ |
| **统一漏斗（本轮新增）** | `app/.../logging/RedactingMessageConverter.kt` + `logback.xml:7-13` 以 `<conversionRule>` 覆盖 `%msg`/`%message`/`%m` —— 所有 appender 的每条消息渲染前统一过 `LogRedactor`（此前仅靠调用点纪律：80 处 logger 调用中 10 处显式脱敏） | ✅ |
| 显式调用点（第二道） | `AppBootstrap.kt:219/234/301/321`、`HelloChain.kt:33-38`、`DefaultExchangeSyncService.kt:240/371-379`（sync_logs 计数摘要）、`FileLogAccess.kt:24-36`、`DefaultDiagnosticsService.kt:36` | ✅ |
| 请求/响应体不整段落日志 | `MarketHttp.kt` 与 `BinanceAdapter.kt` 全文无 logger；HTTP 客户端无日志拦截器；错误模型只带类型化 `kind`；行情 Key 走请求头、Binance 签名只在局部 URL 字符串 | ✅ |
| `sync_logs` 入库脱敏 | `SyncLogsTable.kt:16-19` 口径注 + `DefaultExchangeSyncService.kt:446-472` 只拼计数摘要 | ✅ |
| 守护测试 | `LogRedactorTest`（5）/ `HelloChainTest`（日志行断言）/ `DiagnosticsServiceTest`（报告片段脱敏）/ `LogRotatorTest`（导出脱敏）/ **`RedactingMessageConverterTest`（4）+ `LogbackRedactionFunnelTest`（真实 logback 配置端到端，M13 新增）** | ✅ |

---

## 4. 硬约束 4：两类独立 API

> PRD §1.1-4：行情数据刷新（CoinGecko 主源 / CoinMarketCap 兜底）与交易数据同步（交易所只读 API）相互独立。

| 维度 | 行情链路 | 交易所链路 | 结论 |
|------|----------|------------|------|
| 客户端实例 | `AppBootstrap.kt:455` `newOkHttpMarketClient(...)` → `CoingeckoMarketClient` / `CmcMarketClient` | `AppBootstrap.kt:512` `newOkHttpExchangeClient(...)` → `BinanceAdapter` | ✅ 两实例（`ApiIsolationGuardTest` 守护） |
| 凭据来源 | 设备密钥加密的 settings 全局行（`market.coingecko_key` / `market.cmc_key`） | 账户 DEK 字段级加密的 `api_keys` 行 | ✅ 零交叉引用（exchange 包对 market 包 import 数 = 0） |
| 用例与错误模型 | `MarketRefreshService` / `MarketRefreshError` | `ExchangeSyncService` / `ExchangeError` | ✅ |
| 额度账本 | `SettingsQuotaLedger`（`market.quota`）仅行情链路读写 | 交易所代码零引用 | ✅ |
| 调度循环 | `BackgroundScheduler` 三条独立循环（行情 / 同步 / 维护），各自 `runCatching` 隔离异常 | 同左 | ✅ |
| 唯一耦合点 | 市值榜预热回调（`rankWarmUp`）——行情目录服务喂交易所消歧缓存，经接口注入且失败吞并，不构成数据耦合 | — | ✅ 已登记 |
| 代理 | 两客户端共用同一「开关感知 ProxySelector」实例（PRD §7.2-6.2 要求全请求经代理），非数据耦合 | — | ✅ |
| 守护测试 | `ApiIsolationGuardTest`（实例隔离 2 项，M13 新增）；既有：`DefaultMarketRefreshServiceTest`（主源→兜底/单飞）、`DefaultExchangeSyncServiceTest`（key 级错误只中止本轮）、`BackgroundSchedulerTest`（间隔独立）、`DeviceSecretCipherTest`（purpose 交叉放置即认证失败） | ✅ |

> 说明：`SecurityGuardTest` 的出站白名单同时约束两类 API 的目标主机集合——新增任何第三方出站调用会被 CI 拦红。

---

## 5. 硬约束 5：多账户隔离 + `.cpro` 备份

### 5.1 账户隔离

| 核验点 | 证据 | 结论 |
|--------|------|------|
| 账户级表查询带 `account_id` | 抽查 `settings/SettingsRepository.kt`（账户级读写）、`exchange/ApiKeysTable.kt:71-179`、`exchange/TransactionsTable.kt:77-132`、`exchange/SyncLogsTable.kt:57-76`、`ledger/LedgerTransactionRepository.kt:30-141`、`ledger/FeeRulesTable.kt:48-96`、`ledger/FundsTables.kt:139-272`、`backup/BackupStores.kt`、`backup/BackupRestoreStore.kt` —— 用户可控路径**全部带账户过滤** | ✅ |
| PK-only 写入点（复核） | 5 处（`ApiKeysTable.kt:106/112`、`FeeRulesTable.kt:86`、`BackupRestoreStore.kt:183/213`）均为「同事务紧邻 insert 的主键回写/回读」或「先按 account 过滤再按主键更新」，`id` 非外部输入 → 不构成越权路径 | ✅（登记 §7-5） |
| 账户级 vs 全局键空间 | 结构性区分：`settings.account_id IS NULL` = 全局（`SettingsRepository.kt:31-62`），唯一索引 `UNIQUE(key, COALESCE(account_id,''))`（`Migrations.kt:27`）；现有全局键与账户级键（`backup.last_at`/`restore.last_at`）无重名 | ✅（命名空间约定建议见 §7-5） |
| 会话/内存态清理 | 登出/切换/改密擦 DEK 与令牌（§3.1）；**进程退出补齐**；UI 侧以 `sessionKey` 重建主壳 VM，页面级 VM 随 SHELL 阶段销毁 | ✅ |
| 守护测试 | `DefaultPortfolioServiceTest`（新账户不见他人账本，切回恢复）、`FieldCipherTest`（AAD 含 account_id）、`DefaultAccountServiceTest`（登出/切换/改密）、`SqlCoinCatalogTest`（全局表无 account_id 列） | ✅ |

### 5.2 `.cpro` 格式（ADR-005 / PRD 5.2）

| 规范要求 | 实现 | 结论 |
|----------|------|------|
| 明文头部（单行 JSON） | `CproCodec.kt:50-51,61`（头部字节直接拼接）+ 测试 `header json is single line plaintext` | ✅ |
| 头部字段：`format_version` / `app_version` / `exported_at` / `counts`(7 表) / `range` / `kdf{alg,salt,m,t,p}` / `cipher` | `BackupModels.kt:26-68`、`DefaultBackupService.kt:126-146`；**app_version 已改构建注入**（`BuildInfo.VERSION` ← `app/build.gradle.kts` `appVersion`，与本轮修复的包版本同源） | ✅ |
| 载荷 = AES-256-GCM(JSON) | `CproCodec.kt:59-60`（`AesGcm.encrypt(key, payloadBytes, headerBytes)`） | ✅ |
| 头部作为 AAD（防篡改） | `CproCodec.kt:60/145` + 测试 `header tampering is rejected via AAD binding` | ✅ |
| KDF = Argon2id（参数随头部） | `CproCodec.kt:51-57/134-140`；派生密钥用后 `Zeroization.wipe` | ✅ |
| 币种引用 = `cg_id`（跨设备键） | `BackupModels.kt:13-15`、`DefaultBackupService.kt:177-194/275-280` | ✅ |
| 内容范围：**不含** accounts / 全局设置 / coins / exchange_coin_map / sync_logs | 模型无对应字段（`CproRecords` 恰 7 字段，M13 新增反射守护）；装配依赖不含 `AccountRepository`/`CoinsTable`；账户级设置查询带 `account_id eq`（`BackupStores.kt:41-45`） | ✅ |
| **行情 Key 不进备份**（ADR-002 §2.1 方案甲） | 行情 Key 只在 settings 全局行 → 结构上进不了备份；M13 新增功能守护（写入设备秘密 → 导出 → 断言文件与载荷均无明文/键名） | ✅ |
| 交易所 Key「先解密入载荷」+ 恢复用目标账户 DEK 重加密 | `DefaultBackupService.kt:370-390`、`BackupRestoreStore.kt:194-226`；测试 `DefaultBackupServiceTest`（跨账户 alice→bob 用 bob DEK 解回明文） | ✅ |
| TSV/CSV 明文导出不含密钥 | `CsvExportWriter.kt`（三类列集不含凭证列）+ 测试 `csv exports contain no api key material` | ✅ |
| 诊断报告内容受限 | `domain/.../settings/Diagnostics.kt`（版本/OS/schema/计数/脱敏日志尾部）+ `DefaultDiagnosticsService.kt`（不触密钥来源） | ✅ |
| 原子写 | `DefaultBackupService.kt:638-649`（临时文件 + `ATOMIC_MOVE`，降级普通 rename） | ✅ |
| 备份密码独立设置（D24） | 不回填账户密码、禁空密码（`DefaultBackupService.kt:114` 强度门槛 + UI 本地校验） | ✅（对齐 D24，PRD 5.2-3 偏差已登记） |
| 守护测试 | `CproCodecTest`（6）、`BackupMergePlannerTest`（9，含黄金用例 10）、`DefaultBackupServiceTest`（16）、**`BackupBoundaryGuardTest`（3，M13 新增：载荷字段集 / 行情 Key 不进备份 / accounts·coins·全局设置排除）** | ✅ |

---

## 6. 本次自查发现与处置（C0 类加固；不建决策档、不进台账）

> 定级依据 `AGENTS.md §8.1`：均为**实现偏差修复**（既有硬约束/既有验收口径的落地补齐），不改产品语义、不改数据模型/表结构、不改接口契约、不反转已通过模块行为 → **C0 勘误**；逐条记入 `docs/dev/modules/M13.md §勘误`。

| # | 发现（严重度） | 处置 | 守护测试 |
|---|----------------|------|----------|
| F1 | 进程退出路径不擦内存 DEK（中）——登出/切换/改密均擦，唯退出缺失 | `SessionRuntime.close()` 补 `sessions.clear()`（`AppBootstrap.kt`） | `ActiveSessionStore` 既有用例 + `DefaultAccountServiceTest` |
| F2 | 数据目录/DB/日志/明文 CSV 导出依赖用户 umask（同机他人可读，中） | 新增 `domain/security/FilePermissions.kt`；`AppDirs.ensureDataDirs()` 0700；DB 0600；导出物（`.cpro`/CSV/日志/诊断）0600 | `FilePermissionsTest`（4） |
| F3 | 密钥文件「先写入后 chmod」存在 umask 窗口（低-中）；历史文件不收敛 | 创建即 0600（`FilePermissions.writeOwnerOnlyString`）+ **读路径自愈** | `MasterKeyStoreTest`（新增 legacy 0644 → 0600） |
| F4 | 改密路径 `password_hash` 用 `!=` 字符串比较（低，时序面） | 统一走 `crypto.kekVerify`（`MessageDigest.isEqual` 常量时间） | `DefaultAccountServiceTest` 改密用例 |
| F5 | 日志脱敏无统一漏斗（中）——新增调用点携带密钥即落盘 | logback `<conversionRule>` 覆盖 `%msg`/`%message`/`%m` → `RedactingMessageConverter` | `RedactingMessageConverterTest`（4）+ `LogbackRedactionFunnelTest`（真实配置端到端） |
| F6 | KDF 参数装载无下限，篡改行可静默削弱（低） | `KdfParams.fromStorageString` 拒绝低于 OWASP 基线的参数 | `KdfParamsTest`（新增 downgrade 用例） |
| F7 | **快照降采样未接线**（实质）：`compactPreservingDaily` 有实现与单测，但全仓无运行期调用点 → ADR-005 §3 / data-model §2.11「近 90 天小时级、更早日级」不生效，备份携带全部小时级历史 | 接入调度宿主维护循环（`SchedulerSources.compactSnapshots()` + `BackgroundScheduler.runMaintenance`，`AppBootstrap` 装配 `PriceSnapshotRepository`） | `BackgroundSchedulerTest`（假源覆盖）+ 既有 `PriceSnapshotRepositoryTest`（降采样幂等） |
| F8 | 依赖清单把未采用的 dorkbox SystemTray 列入「随产品分发」（文档失真，低） | 就地更正 `docs/tech/dependency-licenses.md` 标注 | — |
| F9 | 「行情 Key 不进备份」无守护测试（测试缺口） | 新增 `BackupBoundaryGuardTest`（功能 + 结构） | 同左 |
| F10 | 备份范围（不含 accounts/coins/全局设置）无守护测试 | 同上（`CproRecords` 字段集反射断言 + 端到端载荷断言） | 同左 |
| F11 | 两类 API 客户端实例隔离无守护测试 | `ApiIsolationGuardTest`（2 项） | 同左 |
| F12 | 「无遥测依赖 / 出站白名单 / 无网络 appender」无自动化反证 | `SecurityGuardTest`（5 项结构守护） | 同左 |
| F13 | `.cpro` 头部 `app_version` 为开发常量 `0.1.0-dev`，构建版本未注入（M9 登记「随 M13 发布链落地」） | `app/build.gradle.kts` 生成 `BuildInfo.VERSION`（单一真源 = `appVersion` = jpackage packageVersion），装配层注入 `.cpro`/关于页/诊断报告 | — （打包冒烟核对头部字段） |

**未修复但已登记的发现**：F14 备份读写全量入内存（偏离 ADR-005 风险表的流式缓解项）——见 §7-1。

---

## 7. 残留风险与转 P6 观察项（均有到期检查点）

| # | 事项 | 性质 | 到期检查点 / 建议 |
|---|------|------|-------------------|
| 1 | **`.cpro` 非流式读写**（`Files.readAllBytes` 全量入内存；数据量增长后内存峰值随载荷线性上升） | 偏离 ADR-005 风险表缓解项 | 属**格式级变更**（GCM 单 tag 全载荷认证，分块流式需引入分块帧 → 改格式 → C2）：P6 先测大载荷（万级交易 + 数年快照）内存曲线，再决定是否 P8 立项 |
| 2 | 行情请求外发用户币种 id 集合 | 隐私最小化 | P6 评估是否需进一步最小化（§1 观察项） |
| 3 | `.cpro` 载荷内交易所凭证仅由备份密码保护（弱于设备绑定 DEK） | ADR-005 §3 已明示的设计口径 | 保留；P7 用户指南中明确「备份文件密码强度 = 凭证保护强度」 |
| 4 | 界面/ViewModel 以不可擦除 `String` 承载密码（`AuthGateViewModel.PendingCreate.password`、`GatePages` 的 `remember { mutableStateOf("") }`、`BackupViewModel.password`） | 内存态，不落盘；Compose 生态既有约束 | P6 复核；如需收紧可改 `CharArray` + 显式擦除（非阻断） |
| 5 | 账户级/全局 settings 键名无前缀约定（同名字符串可并存于两命名空间，`MigratorTest` 固化该行为）；5 处 PK-only 写入点 | 现状无冲突、无越权路径 | P6 建议补「键名命名空间清单」守护测试（新增账户级键不得与全局键同名） |
| 6 | 登出后调度循环不暂停：同步 tick 在无会话时抛错并被吞（仅噪声日志，无数据串账户） | 体验/日志噪声 | P6 观测；可在调度层加「无活动会话则跳过同步 tick」 |
| 7 | 读屏实测（NVDA/JAWS）、完整 a11y 走查 | ADR-001 既有风险 | P6（M12 遗留） |
| 8 | 三平台托盘实测 / 打包版开机自启端到端 | 环境相关 | P7 打包版（M11 §5-2/§5-3 在册） |
| 9 | 签名/公证合规 | 需证书（成本/周期） | P7（本轮已交付可执行流水线 + Secrets 清单，见 `docs/tech/adr/ADR-006` §2.1 与 `M13.md` §5） |
| 10 | 目标机（4GB 双核）KDF ≤2s 复核 | M1/M2 在册开放待办 | M13 前无可得目标机 → 随 P7 发布前复核（超标预案 = `KdfParams.OWASP_MINIMUM`） |

---

## 8. 复核方法（P6 复跑指引）

```bash
# 1) 全量构建 + 静检（应 636 用例 = 632 执行 0 失败 + 4 跳过 + detekt 0 + 警告 0）
export JAVA_HOME=$(mise where java) && ./gradlew clean build detekt --no-build-cache

# 2) 硬约束结构守护（本清单 §1/§2/§4 的机器可验证部分）
./gradlew :data:test --tests "com.wuzhufolio.data.security.SecurityGuardTest" \
                     --tests "com.wuzhufolio.data.security.ApiIsolationGuardTest"

# 3) 加密与脱敏守护
./gradlew :domain:test --tests "com.wuzhufolio.domain.security.*" \
                       --tests "com.wuzhufolio.domain.redaction.*"
./gradlew :app:test    --tests "com.wuzhufolio.app.logging.*"
./gradlew :data:test   --tests "com.wuzhufolio.data.security.*" \
                       --tests "com.wuzhufolio.data.backup.BackupBoundaryGuardTest"

# 4) 运行期权限实证（Linux/macOS）
stat -c '%a %n' ~/.wuzhufolio ~/.wuzhufolio/logs ~/.wuzhufolio/master.key ~/.wuzhufolio/wuzhufolio.db
#   期望：700 数据目录/日志目录 · 600 密钥文件与库文件

# 5) 运行期出站面实证（P6 抓包/代理日志）：仅应出现 api.coingecko.com / pro-api.coinmarketcap.com / api.binance.com

# 6) 打包产物内不含凭据（P6 抽查）
#   - .cpro 文件 grep 不到任何密钥明文；CSV 三类无凭证列
#   - 安装包内无 master.key/device.key/.db（用户数据只在本机数据目录）
```
