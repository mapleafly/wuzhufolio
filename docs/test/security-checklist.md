# WuZhuFolio 安全自查清单（P6 复跑版 · T13.1 清单 × P6 系统测试）

> **范围**：`AGENTS.md §1.1` 五条硬约束**逐条复跑核验**（回溯 PRD §1.1 / 共享规范 §8 / 安全与隐私原则）。
> **方法**：① 源码静态取证（read/grep，逐条给 `文件:行号`）；② 依赖与构建面核验（version catalog / 模块构建脚本 / 运行期依赖树）；
> ③ 行为测试清单（既有守护测试 + P6 新增守护）；④ **运行期实证**（真实进程出站抓包、真实库权限、真实网络冒烟）；
> ⑤ P6 新增专项（大载荷内存曲线、settings 键命名空间、登出调度会话门、导出失败模式）。
> **产物关系**：本文件由 P4-M13（T13.1）建立，**P6 对其逐条复跑并打勾**（`AGENTS.md §4 P6`：输入含本清单）。
> **日期**：M13 版 2026-09-12 → **P6 复跑版 2026-09-14** · **有效需求基线**：**PRD V2.0 + Δ{D21, D24, D25, D26, D27, D28, D29, D30, D31, D32, D33}**

---

## 0. 结论汇总（P6 复跑）

| # | 硬约束 | M13 结论 | **P6 复跑结论** | P6 新增证据 |
|---|--------|----------|-----------------|-------------|
| 1 | 数据本地化（交易/密钥/流水仅存本地，禁云端上传） | ✅ 通过 | ✅ **通过** | **运行期出站抓包**：真实进程 + 真实客户端仅命中 3 个白名单主机（§1.4）；结构守护 `SecurityGuardTest` 复跑绿 |
| 2 | 零遥测（无遥测/统计上报/第三方分析 SDK） | ✅ 通过 | ✅ **通过** | 依赖面/日志 appender 守护复跑绿；运行期抓包未出现任何非白名单主机（§1.4） |
| 3 | 密钥与加密（DEK/KEK 分层、AES-256-GCM、凭据不落盘、日志脱敏） | ✅ 通过 | ✅ **通过** | 权限实证复跑（目录 700 / 文件 600）；导出失败模式类型化（§3.6，DEF-01）；大载荷内存曲线（§3.7） |
| 4 | 两类独立 API（行情刷新 ⊥ 交易同步） | ✅ 通过 | ✅ **通过** | **两类客户端均实证走代理**（`ProxyRoutingSmokeTest`，§4）；实例隔离守护复跑绿 |
| 5 | 多账户隔离 + `.cpro` 备份（明文头部 + AES-256-GCM 载荷） | ✅ 通过 | ✅ **通过** | 跨账户恢复不清库断言（DEF-01 回归）；`CproLargePayloadTest`（2 万交易 + 6 万快照无损往返）；备份边界守护复跑绿 |

**自动化证据（P6 全量，2026-09-14）**：
`./gradlew clean build detekt --no-build-cache` → **677 用例（669 执行 0 失败 0 错误 + 8 跳过）**
= domain 222 / data 284 / ui 140 / app 31；detekt **0**；编译警告 **0**。
跳过 8 = 4 钥匙串真实后端（CI win/mac 实证）+ 3 live smoke（env 门控：CG/Binance/代理路由）+ 1 首启真实网络链路（env 门控）。

**P6 发现与处置**：P0 = 0、P1 = 0；P2 = 6（3 项已修复 · 3 项待人工定级/登记，见 `defects.md`）。
本清单范围内**无未登记的安全悬空项**。

---

## 1. 硬约束 1：数据本地化

> PRD §1.1-1：交易记录、API 密钥、资金流水只存用户设备本地，禁止任何云端上传/传输。

### 1.1 静态取证（M13 建立，P6 复核未变化）

| 核验点 | 证据 | P6 复核 |
|--------|------|---------|
| 出站主机白名单（穷举） | 运行期仅 3 个主机常量：`data/.../market/MarketConfig.kt:22`（`api.coingecko.com`）、`:27`（`pro-api.coinmarketcap.com`）、`data/.../exchange/ExchangeConfig.kt:11`（`api.binance.com`）；用户可见注册链接 2 条（`ui/.../market/MarketCopy.kt:44-45`）经 `Desktop.browse` 交系统浏览器，不携带数据 | ✅ 未变化 |
| 无其它网络原语 | 主源码 grep `Socket(`/`URLConnection`/`openConnection`/`java.net.http`/`WebSocket`/`ServerSocket` 零命中（唯一 `java.net.SocketAddress` 为类型导入）；`SystemProxyDetector` 不触网（`ProxySelector.select` 只读本机配置） | ✅ 未变化 |
| 数据目录单一真源 | `app/.../AppDirs.kt:11`（`WUZHUFOLIO_DATA_DIR` → `-Dwuzhufolio.dataDir` → `~/.wuzhufolio`）；`ensureDataDirs():25` 创建并收紧 0700 | ✅ 未变化 |
| 落盘物清单（全部本机） | DB / 日志 / 备份与恢复临时备份 / 降级密钥文件 / 导出物（`.cpro`、CSV、日志、诊断报告）均在本机路径 | ✅ 未变化 |
| 无上传/同步语义代码 | grep `upload`/`report`/`telemetry`/`analytics`/`beacon`/`crash`：命中项仅为 i18n 文案与本机诊断报告 | ✅ 未变化 |
| 结构守护 | `data/src/test/.../security/SecurityGuardTest.kt`：出站主机白名单 / 禁明文 `http://` 出站 / 数据目录单一真源 | ✅ **P6 复跑绿** |

### 1.2 权限与落盘实证（P6 复跑，2026-09-14）

```
stat -c '%a %n' /tmp/wzf-p6-gui /tmp/wzf-p6-gui/logs /tmp/wzf-p6-gui/master.key /tmp/wzf-p6-gui/wuzhufolio.db
→ 700 数据目录 · 700 日志目录 · 600 master.key · 600 wuzhufolio.db
```
✅ 与 M13 修复后的口径一致（F2/F3 修复未回归）。

### 1.3 隐私最小化观察项（P6 评估完成）

发送给行情源的载荷 = **币种 id 集合**（`ids=`/`id=`，来自「持仓 ∪ 自选」）+ 计价法币；
**不含**金额、数量、交易记录、流水、密钥、账户身份。交易所侧 `symbol=` 为 `myTrades` 必传参数，
且该请求由用户**自己的只读 Key** 签名（交易所本就知道账户余额），**增量隐私损失 ≈ 0**。

- **风险面**：第三方行情源可据 `ids=` 集合与刷新节奏推断「持仓/自选集合」与在线时间线（5/15/30 分钟粒度）；
- **量化结论**：最小化改造的**边际额度成本 ≈ 0**（额度按请求计费，与 id 数无关），代价在带宽/覆盖，不在额度；
- **处置**：**交人工定级**（`test-report.md` §6 待裁决项：接受现状 / 开关缺省关（建议 C1）/ 改默认路径（建议 C2））。
  本轮**不实施**（`AGENTS.md §8.4`：Agent 不自行定级自行实现）。

### 1.4 运行期出站抓包实证（P6 新增 ✅）

方法：`scripts/outbound-capture-proxy.py`（本地记录型 CONNECT/HTTP 代理，**不解密 TLS**）→ 把真实应用进程与
真实客户端测试的流量指向它 → 统计目标主机。

```
# 应用真实运行（P5 修复后的启动即刷新链路）
WUZHUFOLIO_DATA_DIR=/tmp/wzf-p6-gui https_proxy=http://127.0.0.1:8899 ./gradlew --no-daemon :app:run --offline
# 真实客户端（CG 匿名档 + Binance 公开端点 + CMC 占位 Key 探测）
WZF_LIVE_SMOKE=1 https_proxy=http://127.0.0.1:8899 ./gradlew --no-daemon :data:test \
  --tests "com.wuzhufolio.data.smoke.*" --rerun-tasks
```

| 捕获到的主机 | 次数 | 来源 |
|--------------|------|------|
| `api.coingecko.com:443` | 3 | 应用启动即刷新（真实进程）+ live smoke 报价/目录 |
| `api.binance.com:443` | 2 | `ProxyRoutingSmokeTest` 公开端点 `/api/v3/exchangeInfo`（无 Key） |
| `pro-api.coinmarketcap.com:443` | 1 | `ProxyRoutingSmokeTest` 兜底端点探测（**占位 Key** → 预期被拒 401） |

**结论**：运行期出站主机集合 = 白名单三主机，**无第四方**；且证明**两类 API 的客户端都真的走代理**
（PRD 故事 4.2-2「所有对外网络请求都必须通过检测到的代理」——M11 此前只用状态栏指示验证过）。

---

## 2. 硬约束 2：零遥测

> PRD §1.1-2：不内置任何遥测、统计上报或第三方分析 SDK。

| 核验点 | 证据 | P6 复核 |
|--------|------|---------|
| 直接依赖面 | `gradle/libs.versions.toml` + 四模块 `build.gradle.kts`：无 firebase/sentry/amplitude/mixpanel/bugsnag/datadog/crashlytics/opentelemetry/umami/matomo/countly/newrelic/elastic-apm/segment/appcenter/instabug | ✅ 未变化 |
| 传递依赖面 | `:app:dependencies --configuration runtimeClasspath`：Compose/Skiko、Ktor/OkHttp、Exposed/SQLCipher、BouncyCastle、javakeyring、Koin、slf4j/logback、coroutines/serialization——**无遥测/分析/崩溃上报类库** | ✅ 未变化（P6 未新增依赖） |
| 无自动更新检查 | 无 Sparkle/WinSparkle/appcast/update4j；关于页仅版本号 + 官方渠道链接 | ✅ 未变化 |
| 日志不外发 | `logback.xml` 仅 Console + RollingFile appender，无 Socket/SMTP/JMS/DB/Syslog | ✅ 未变化 |
| 界面声明一致性 | 设置→关于「无遥测声明」+ 仪表盘「安全与隐私」面板 | ✅ 未变化 |
| 结构守护 | `SecurityGuardTest`：遥测依赖词表扫描 + 网络 appender 扫描 | ✅ **P6 复跑绿** |
| **运行期反证** | 抓包（§1.4）未出现任何非白名单主机 —— 无遥测上报面 | ✅ **P6 新增** |

---

## 3. 硬约束 3：密钥与加密

> PRD §1.1-3：分层密钥（DEK/KEK）、AES-256-GCM、密码与登录凭据永不落盘、日志脱敏。

### 3.1 分层密钥链（M13 建立，P6 复核未变化）

| 环节 | 证据 | P6 复核 |
|------|------|---------|
| DEK 随机 32B | `domain/.../security/CryptoService.kt:19`（`SecureRandom`） | ✅ |
| KEK = Argon2id(密码, salt) | `DefaultAccountService.kt:52-54` → `CryptoService.kt:39-44` → `Argon2Kdf.kt:20-42`（ARGON2_id / v13 / 32B / 16B salt） | ✅ |
| 参数冻结与基线 | `KdfParams.DEFAULT = m=64MiB,t=3,p=1`；`OWASP_MINIMUM = 19MiB,t=2,p=1`；**降级拒绝装载**（M13 F6） | ✅ |
| DEK 被 KEK 包裹落库 | `AccountRepository.kt:61-94`（单事务先插后包，AAD = `account_id+"wrapped_dek"`） | ✅ |
| 解包即认证 | `AesGcm.kt:34-47`（tag 失败 → 认证失败）；`DefaultAccountService.kt:204-226` `finally` 擦 KEK | ✅ |
| DEK 仅内存 + 全域擦除 | `ActiveSessionStore`（登出/切换/改密）+ **进程退出**（M13 F1：`SessionRuntime.close()`） | ✅ |
| 密码不落盘 | 全程 `CharArray`；落库仅 `SHA-256(KEK‖"verify")`；KDF 内部拷贝 `finally` 清零 | ✅ |
| 记住我令牌 | 随机 256-bit + `wrapped_dek_session`（AAD=`+"session"`）入 OS 钥匙串；登出/改密/切换清除 | ✅ |

### 3.2 算法与参数（P6 复核未变化）

| 项 | 值 | 证据 |
|----|----|------|
| 对称算法 | AES-256-GCM（`AES/GCM/NoPadding`，key 32B 强制校验） | `AesGcm.kt:16,25,26,38` |
| nonce / tag | 12B 随机 / 128-bit | `AesGcm.kt:17,27,19,39` |
| AAD 隔离面 | DEK 包裹 / 会话 / 凭证四列 / 设备秘密 / `.cpro` 头部 | `KeyWrap.kt:15-16`、`FieldCipher.kt:41-42`、`DeviceSecretCipher.kt:41-42`、`CproCodec.kt:60/145` |
| 整库加密 | SQLCipher（Willena fork，raw-key 注入，WAL/busy_timeout/foreign_keys 每连接生效） | `WzDatabase.kt:70-90` |
| 行情 Key 边界 | 设备密钥（`device.key`）+ `DeviceSecretCipher` → settings **全局行**；**不进 `.cpro`** | `AppBootstrap.kt:448`、`DeviceSecretStore.kt:27-41` |
| 交易所 Key 边界 | 账户 DEK + `FieldCipher` 四列字段级加密（编辑不回显） | `DefaultExchangeSyncService.kt:81-83/185-189` |
| 备份密钥派生 | Argon2id(备份密码, 头部 salt/参数) → AES-256-GCM；用后擦除 | `CproCodec.kt:49-65/128-165` |
| KDF 降级防线 | 存量参数低于 OWASP 基线（`m<19MiB` 或 `t<2`）拒绝装载 | `KdfParams.kt:42-60` + `KdfParamsTest` |

### 3.3 凭据存储面（逐类核验「是否会落明文」）

| 凭据 | 位置 | 加密 | 明文落盘 |
|------|------|------|----------|
| 用户账户密码 | 不落盘 | 仅 KDF 输入（CharArray，用后擦） | ❌ 否 |
| 记住我令牌 | OS 钥匙串（降级 0600 文件） | 条目自身即密钥材料 | ❌ 否 |
| 交易所 API Key/Secret/passphrase/extra | `api_keys` 表（整库加密） | 账户 DEK 字段级 GCM | ❌ 否 |
| 行情 CG/CMC Key | settings 全局行（整库加密） | 设备密钥 GCM（purpose AAD） | ❌ 否 |
| DB 主密钥 / 设备密钥 | OS 钥匙串（降级 0600 文件） | — | ❌ 否 |
| DEK | 仅内存（全域擦除） | — | ❌ 否 |
| 备份密码 | 不落盘（用户输入） | 仅 KDF 输入 | ❌ 否 |
| `.cpro` 载荷内交易所凭证 | 备份文件 | Argon2id(备份密码) + AES-256-GCM | ❌ 否（强度=备份密码，见 §7-3） |

### 3.4 文件权限（P6 复跑实证）

| 对象 | 权限 | P6 实证 |
|------|------|---------|
| 数据目录 / 日志目录 / backups | **0700** | ✅ 实测 700 |
| `master.key` / `device.key` / `remember-me.dat` | **0600**（创建即受限 + 读路径自愈） | ✅ 实测 600 |
| DB 文件（含 WAL/SHM） | **0600** | ✅ 实测 600 |
| `.cpro` / CSV / 日志导出 / 诊断报告 | **0600** | ✅ 沿用 M13 证据（`FilePermissionsTest` 复跑绿） |

### 3.5 日志脱敏（P6 复核）

| 核验点 | 证据 | P6 复核 |
|--------|------|---------|
| 脱敏规则 | `LogRedactor`：敏感键名整值遮蔽 + ≥32 位疑似密钥/签名/令牌裸串遮蔽；纯函数、幂等 | ✅ |
| 统一漏斗 | `RedactingMessageConverter` + logback `<conversionRule>` 覆盖 `%msg/%message/%m` | ✅ 真实日志实测 `market_api_key=****`、`key_backend=****` |
| 请求/响应体不整段落日志 | `MarketHttp.kt` 与 `BinanceAdapter.kt` 全文无 logger；无日志拦截器 | ✅ |
| `sync_logs` 只存计数摘要 | `DefaultExchangeSyncService.kt:446-472` | ✅ |
| 守护测试 | `LogRedactorTest` / `RedactingMessageConverterTest` / `LogbackRedactionFunnelTest` / `LogRotatorTest` / `DiagnosticsServiceTest` | ✅ **P6 复跑绿** |

### 3.6 凭证不可解密时的失败模式（P6 新增 ✅ · DEF-01）

| 项 | 结论 |
|----|------|
| 行为 | 导出遇到不可解密 `api_keys` 密文 → `BackupExportException(CREDENTIAL_UNREADABLE)`，**整体中止且不落文件**；全量覆盖恢复的临时备份同源失败 → **恢复中止且不清库** |
| 文案 | 中英双档整句（点名密钥别名；不含明文/密文/DEK/原始加密异常文案） |
| 回归 | `DefaultBackupServiceTest`（3 例，含数据完整性断言）+ `ui/BackupExportErrorCopyTest`（2 例） |
| 契约 | `interaction.md §2.9` + `api-contracts.md §3/§4`（`BACKUP_EXPORT_FAILED`） |

### 3.7 `.cpro` 大载荷内存曲线（P6 新增 ✅ · M13 §7-1 到期项）

`./gradlew :domain:backupBenchmark`（纯内存编解码，峰值 = 2ms 采样线程观测的堆 used 最大值 − GC 基线）：

| 规模 | 记录总数 | 文件 | 编码 ms | 解码 ms | 编码峰值 | 解码峰值 | 峰值/文件 |
|------|----------|------|---------|---------|----------|----------|-----------|
| 轻量（1 年 · 1 币） | 6,185 | 1.1 MiB | 879 | 257 | 77.5 MiB | 100.3 MiB | 95× |
| 典型（3 年 · 8 币） | 35,415 | 5.9 MiB | 269 | 298 | 109 MiB | 142 MiB | 27× |
| 重度（5 年 · 20 币 + 高频） | 201,265 | 40.8 MiB | 996 | 1,090 | 321 MiB | 483 MiB | 13× |
| 压力（10 年 · 50 币） | 706,065 | 152.0 MiB | 4,116 | 3,679 | 1,195 MiB | 1,424 MiB | 10× |

- `-Xmx512m`：轻量/典型通过，**重度起 OOM**；`-Xmx1g`（≈4 GB 目标机打包版默认堆）：轻量/典型/重度通过，压力档 OOM；
  `-Xmx2g`：压力档通过（峰值 ≈1.4 GiB）。
- **可复现性**：同一档两次实测（2026-09-14）峰值波动 <5%（重度解码峰值 483 → 461 MiB），表内为首次实测值。
- **结论**：PRD 规模（典型/重度区间）内存余量充足（≤483 MiB），**维持非流式实现**；
  **流式/分块化 = 改变 GCM 认证与文件格式 → C2**，仅在用户数据量接近 10× 典型时由 P8 立项评估。
- 同时新增常驻回归 `CproLargePayloadTest`（2 万交易 + 6 万快照无损往返 + 头部摘要一致），保证格式行为有基线。

---

## 4. 硬约束 4：两类独立 API

> PRD §1.1-4：行情数据刷新（CoinGecko 主源 / CoinMarketCap 兜底）与交易数据同步（交易所只读 API）相互独立。

| 维度 | 行情链路 | 交易所链路 | P6 复核 |
|------|----------|------------|---------|
| 客户端实例 | `newOkHttpMarketClient(...)` → CG / CMC | `newOkHttpExchangeClient(...)` → `BinanceAdapter` | ✅ `ApiIsolationGuardTest` 复跑绿 |
| 凭据来源 | 设备密钥加密的 settings 全局行 | 账户 DEK 字段级加密的 `api_keys` 行 | ✅ 零交叉引用 |
| 用例与错误模型 | `MarketRefreshService` / `MarketRefreshError` | `ExchangeSyncService` / `ExchangeError` | ✅ |
| 额度账本 | `SettingsQuotaLedger` 仅行情链路读写 | 交易所代码零引用 | ✅ |
| 调度循环 | `BackgroundScheduler` 三条独立循环，各自异常隔离 | 同左 | ✅ + **P6 §7-6 增补会话门** |
| 代理 | 两客户端共用同一「开关生效 ProxySelector」 | 同左 | ✅ **P6 新增端到端实证**（下表） |
| **P6 代理路由实证** | `ProxyRoutingSmokeTest`（env 门控）：CG 匿名档报价经代理成功返回 + Binance 公开端点经代理返回 >100 对 + CMC 兜底端点探测（占位 Key → 期望被拒）；抓包日志确认三主机（§1.4） | ✅ **P6 新增** |
| **P6 契约勘误** | — | Binance 签名请求补发 `recvWindow`（DEF-02；文档原本如此规定，实现漂移已修） | ✅ |

---

## 5. 硬约束 5：多账户隔离 + `.cpro` 备份

### 5.1 账户隔离（P6 复核）

| 核验点 | 证据 | P6 复核 |
|--------|------|---------|
| 账户级表查询带 `account_id` | 抽查 settings / api_keys / transactions / sync_logs / 账本仓库 / fee_rules / 资金表 / 备份读写 | ✅ 未变化 |
| PK-only 写入点 | 5 处均为「同事务紧邻 insert 的主键回写/回读」或「先按 account 过滤再按主键更新」 | ✅ 未变化 |
| 账户级 vs 全局键空间 | `settings.account_id IS NULL` = 全局；唯一索引 `UNIQUE(key, COALESCE(account_id,''))` | ✅ **P6 新增守护**（§5.3） |
| 会话/内存态清理 | 登出/切换/改密擦 DEK 与令牌；进程退出补齐；UI 以 `sessionKey` 重建主壳 VM | ✅ |
| 守护测试 | `DefaultPortfolioServiceTest`（新账户不见他人账本）/ `FieldCipherTest`（AAD 含 account_id）/ `DefaultAccountServiceTest` / `SqlCoinCatalogTest` | ✅ 复跑绿 |

### 5.2 `.cpro` 格式与边界（P6 复核）

| 规范要求 | 实现 | P6 复核 |
|----------|------|---------|
| 明文头部（单行 JSON）+ 头部字段齐全 | `CproCodec.kt`；`app_version` 构建注入（M13 F13 闭环） | ✅ |
| 载荷 = AES-256-GCM(JSON)，头部作 AAD | `CproCodec.kt:59-60/145` + 篡改拒绝测试 | ✅ |
| KDF = Argon2id（参数随头部）+ 用后擦除 | `CproCodec.kt:51-57/134-140` | ✅ |
| 币种引用 = `cg_id`（跨设备键） | `BackupModels.kt:13-15` | ✅ |
| 内容范围：不含 accounts / 全局设置 / coins / exchange_coin_map / sync_logs；**行情 Key 不进备份** | `CproRecords` 恰 7 字段（反射守护）+ 功能守护 | ✅ `BackupBoundaryGuardTest` 复跑绿 |
| 交易所 Key 导出先解密、恢复按目标账户 DEK 重加密 | `DefaultBackupService.kt:370-390` / `BackupRestoreStore.kt:194-226` | ✅ 集成测试（P5 回环）复跑绿 |
| CSV 明文导出不含密钥 | `CsvExportWriter.kt` 三类列集 | ✅ |
| 原子写 | 临时文件 + `ATOMIC_MOVE` | ✅ |
| 备份密码独立设置（D24） | 不回填、禁空 | ✅ |
| **导出失败模式** | 类型化 + 不落文件 + 恢复不清库（DEF-01） | ✅ **P6 新增** |
| **大载荷正确性** | `CproLargePayloadTest`（2 万交易 + 6 万快照） | ✅ **P6 新增** |

### 5.3 settings 键命名空间守护（P6 新增 ✅ · M13 §7-5 到期项）

- **新增** `data/src/test/.../settings/SettingsKeyNamespaceGuardTest.kt`：fail-closed 源码扫描（字面量 + 常量符号 + 别名解析 +
  种子键 + UI 偏好透传键）+ 登记表双向校验 + **`全局键 ∩ 账户级键 == ∅`** 核心断言；
- **现状**：全局键 20 个、账户级键 2 个（`backup.last_at` / `restore.last_at`），交集为空；
- **有效性验证**：临时把 `SETTING_LAST_BACKUP` 改成全局键名 `backup.reminder` → 两条断言**立即变红**，改回即绿（fail-closed 成立）；
- **不加前缀、不做迁移**的理由：重命名既有账户级键需 M013 迁移 + 旧 `.cpro` 兼容 → 命中 C2 红线（§8.1-2/5），收益不抵成本。

---

## 6. M13 修复项回归状态（P6 复跑）

| # | M13 发现 | 修复 | P6 回归 |
|---|----------|------|---------|
| F1 | 进程退出不擦内存 DEK | `SessionRuntime.close()` 补 `sessions.clear()` | ✅ 相关用例复跑绿 |
| F2 | 数据目录/DB/日志/导出物权限依赖 umask | `FilePermissions` + 0700/0600 | ✅ **运行期实测 700/600**（§1.2） |
| F3 | 密钥文件「先写入后 chmod」窗口 | 创建即 0600 + 读路径自愈 | ✅ `MasterKeyStoreTest` 复跑绿 |
| F4 | 改密路径字符串比较（时序面） | 统一 `crypto.kekVerify`（常量时间） | ✅ `DefaultAccountServiceTest` 复跑绿 |
| F5 | 日志脱敏无统一漏斗 | logback `<conversionRule>` 覆盖 `%msg` | ✅ 日志实测 `****`（§3.5） |
| F6 | KDF 参数装载无下限 | `KdfParams.fromStorageString` 拒绝低于 OWASP 基线 | ✅ `KdfParamsTest` 复跑绿 |
| F7 | 快照降采样未接线 | 接入调度宿主维护循环 | ✅ `BackgroundSchedulerTest` 复跑绿 |
| F8 | 依赖清单误列 dorkbox | 文档就地更正 | ✅ 未变化 |
| F9–F12 | 备份边界/两类 API/遥测结构守护缺失 | 4 个守护测试类 | ✅ 全部复跑绿 |
| F13 | `.cpro` 头部 `app_version` 为开发常量 | 构建注入 `BuildInfo.VERSION` | ✅ 未变化 |

---

## 7. 残留风险与到期项（P6 更新后状态）

| # | 事项 | M13 状态 | **P6 状态** | 到期检查点 / 结论 |
|---|------|----------|-------------|-------------------|
| 1 | `.cpro` 非流式读写（内存峰值随载荷线性上升） | 登记转 P6 | ✅ **已实测闭环**（§3.7）：PRD 规模余量充足 → **维持非流式**；流式化 = C2，仅 10× 典型规模时由 P8 立项 | 人工确认结论 |
| 2 | 行情请求外发用户币种 id 集合 | 登记转 P6 | ✅ **已裁决：接受现状**（§1.3；人工 P6 门「按建议处理」）→ 由 P7 用户指南/隐私声明明示；最小化边际额度成本 ≈0 | 已闭环（裁决记录见 `test-report.md §6`） |
| 3 | `.cpro` 载荷内凭证仅由备份密码保护 | 保留 | ➖ **保留**（ADR-005 §3 设计口径） | P7 用户指南写明「备份文件密码强度 = 凭证保护强度」 |
| 4 | 界面/VM 以不可擦除 `String` 承载密码 | 登记转 P6 | ➖ **P6 复核：保留**（Compose 生态既有约束；不落盘、非阻断） | P8 视情况改 `CharArray` + 显式擦除 |
| 5 | 账户级/全局 settings 键名无前缀约定 | 登记转 P6 | ✅ **已闭环**（§5.3 守护测试 + 登记表 + 互斥断言） | — |
| 6 | 登出后调度循环不暂停（同步 tick 抛错被吞） | 登记转 P6 | ✅ **已闭环**：`SchedulerSources.hasActiveSession()` + `syncOnce()` 短路 + 组合根注入会话持有器 + 2 项回归 | — |
| 7 | 读屏（NVDA/JAWS）实测、完整 a11y 走查 | 登记转 P6 | ✅ **已闭环**（人工门第七轮 2026-09-15：**TC-MAN-03 通过 ✅**，NVDA 实测；DEF-13 隐形焦点目标已修） | `test-cases.md` §7 用例 3 |
| 8 | 三平台托盘实测 / 打包版开机自启端到端 | 转 P7 | 🟢 **Windows 侧全部闭环（2026-09-21）**：**托盘走查 ✅**（TC-MAN-01：菜单三项 + 关窗行为 + 驻留 + 通知）· **打包版开机自启端到端 ✅**（TC-MAN-02）· **便携版解压即用 ✅**（TC-MAN-11）；**剩余按人工拍板转 P7 携带（到期检查点 = P7 发布前）**：**Linux / macOS 托盘与打包版自启**（GNOME 需 AppIndicator 扩展，无宿主时验证降级分支） | `test-cases.md` §7 用例 1/2/11·`manual-test-guide.md §19/§19.1`·`M11.md §5-2/§5-3` |
| 13 | 出站抓包 + 文件权限人工实证（TC-MAN-09，Ubuntu） | — | 🔵 **按人工拍板（2026-09-21）转 P7 携带**（到期检查点 = P7 发布前）：Agent 侧已有等效证据（运行期仅三白名单主机 §2.3/§5；打包版权限 700/600 §1.2），人工按 §8-4 复跑即闭环 | `test-cases.md §7` TC-MAN-09 |
| 9 | 签名/公证合规 | 转 P7 | ➖ **P7**（流水线与 Secrets 清单已交付） | ADR-006 §2.1 |
| 10 | 目标机（4GB 双核）KDF ≤2s 复核 | 转 P7 前 | ✅ **已闭环**（人工门第七轮 2026-09-15：**TC-MAN-04 通过 ✅**，含 2 核受限等效模拟 172.6 ms ≪ 2 s） | `test-cases.md` §7 用例 5；超标预案 = `KdfParams.OWASP_MINIMUM`（未启用） |
| 11 | P5-4 备份导出失败模式 | 转 P6 | ✅ **已修复**（DEF-01/06） | — |
| 12 | CMC 兜底计入 CG 额度账本（P6 新发现） | — | ✅ **已裁决：登记 P8**（DEF-04；人工 P6 门「按建议处理」）→ 账本增 provider 维度 + 旧载荷兼容 | 已闭环（P8 立项输入；影响仅可能提前降档） |

---

## 8. 复核方法（复跑指引）

```bash
export JAVA_HOME=$(mise where java)

# 1) 全量构建 + 静检（期望：718 用例 = 710 执行 0 失败 + 8 跳过，detekt 0，编译警告 0）
./gradlew clean build detekt --no-build-cache

# 2) 硬约束结构守护（§1/§2/§4/§5）
./gradlew :data:test --tests "com.wuzhufolio.data.security.SecurityGuardTest" \
                     --tests "com.wuzhufolio.data.security.ApiIsolationGuardTest" \
                     --tests "com.wuzhufolio.data.backup.BackupBoundaryGuardTest" \
                     --tests "com.wuzhufolio.data.settings.SettingsKeyNamespaceGuardTest"

# 3) 加密与脱敏守护
./gradlew :domain:test --tests "com.wuzhufolio.domain.security.*" \
                       --tests "com.wuzhufolio.domain.redaction.*" \
                       --tests "com.wuzhufolio.domain.backup.*"
./gradlew :app:test    --tests "com.wuzhufolio.app.logging.*"
./gradlew :ui:test     --tests "com.wuzhufolio.ui.backup.*"

# 4) 运行期权限实证（Linux/macOS）
stat -c '%a %n' ~/.wuzhufolio ~/.wuzhufolio/logs ~/.wuzhufolio/master.key ~/.wuzhufolio/wuzhufolio.db
#   期望：700 目录 · 600 密钥/库文件

# 5) 运行期出站抓包实证（§1.4；仅应出现 api.coingecko.com / pro-api.coinmarketcap.com / api.binance.com）
python3 scripts/outbound-capture-proxy.py --port 8899 --log /tmp/wzf-outbound.log &
WZF_LIVE_SMOKE=1 https_proxy=http://127.0.0.1:8899 ./gradlew --no-daemon \
  :data:test --tests "com.wuzhufolio.data.smoke.*" --rerun-tasks
awk '{print $3}' /tmp/wzf-outbound.log | sort -u

# 6) .cpro 大载荷内存曲线（§3.7）
./gradlew :domain:backupBenchmark                     # 默认 -Xmx1g（≈4GB 目标机打包版默认堆）
./gradlew :domain:backupBenchmark -PbenchXmx=512m     # 收紧堆找容量边界

# 7) 打包产物内不含凭据（抽查）
#   - .cpro 文件 grep 不到任何密钥明文；CSV 三类无凭证列
#   - 安装包内无 master.key/device.key/.db（用户数据只在本机数据目录）
```
