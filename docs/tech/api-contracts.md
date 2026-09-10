# WuZhuFolio 接口契约（api-contracts.md）

> P2 产物 · 桌面端 · 依据 PRD 全局说明「行情数据与时间分辨率规则」/「币种标识与主数据规则」、故事 3.2/4.1、§10 注、共享规范 §5/§6。
> 三类契约：① 行情客户端（CG/CMC 真实 API）② 交易所适配（Binance 真实 API）③ 内部服务（Kotlin 用例接口）。错误码统一 §4。
> 本轮按人工指令：行情基于 CG/CMC 真实 API；内部契约改为 Kotlin 进程内服务接口（Compose 无跨进程 IPC）。

---

## 1. 行情客户端契约（MarketDataClient · 真实 API）

> 行情 Key 存储：设备密钥加密 + settings 全局行 + 不进 .cpro 备份（ADR-002 §2.1 方案甲 / ADR-005 §3）。

类型（Kotlin data class / kotlinx.serialization）：

```kotlin
typealias CoinId = String   // CoinGecko id（内部唯一标识）
typealias Fiat = String     // USD / EUR / CNY / ...
data class Quote(val coin: CoinId, val fiat: Fiat, val price: String, val source: PriceSource, val at: String)
data class Candle(val t: Long, val price: String)
data class Coin(val cgId: CoinId, val cmcId: String?, val symbol: String, val name: String, val status: CoinStatus)
enum class PriceSource { COINGECKO, COINMARKETCAP }
```

### 1.1 主源 CoinGecko（Base：`https://api.coingecko.com/api/v3`，个人 Key 经头 `x-cg-demo-api-key`）

| 方法 | 端点 | 参数 | 返回 |
|------|------|------|------|
| `fetchCurrent(coins, fiats)` | `GET /simple/price` | `ids`（逗号分隔 cg_id）、`vs_currencies`（逗号分隔法币） | 每 (id, fiat) 价格 |
| `fetchDirectory()` | `GET /coins/list` | `include_platform=true` | id/symbol/name/platforms |
| `fetchHistory(coin, fiat, from, to)` | `GET /coins/{id}/market_chart` 或 `/market_chart/range` | `vs_currency`、`days` 或 `from/to` | 时间序列（≤90 天小时级、>90 天日级） |
| 单日价 | `GET /coins/{id}/history` | `date=dd-mm-yyyy` | 当日日线 |

限额：Demo 档约 30 次/分钟、10,000 次/月；无 Key 公共 API 按 IP 共享限流（约 30 次/分钟）。
错误：429（限流）、401（Key 失效）、404（币种未收录）、超时。

### 1.2 兜底 CoinMarketCap（Base：`https://pro-api.coinmarketcap.com/v1`，Key 经头 `X-CMC_PRO_API_KEY`）

| 方法 | 端点 | 参数 | 返回 |
|------|------|------|------|
| `fetchQuotes(cmcIds, fiats)` | `GET /cryptocurrency/quotes/latest` | `id`（逗号分隔，**单次 ≤100**）、`convert` | 每 id 多法币报价 |
| `fetchMap()` | `GET /cryptocurrency/map` | — | id/symbol/name（每日缓存，回填 coins.cmc_id） |

限额：Basic 档 10,000 credits/月；**无免费历史端点**（历史回填不做兜底）。
错误：429、401（Key 失效）、402（额度耗尽）。

### 1.3 限流/退避/额度语义（ADR-003）

| 规则 | 值 |
|------|-----|
| 429 退避 | 指数退避 + 抖动：1s→…上限 60s，超限转失败/保持上次价格 |
| 月度额度治理 | 本地计数（当前价+历史回填+目录）；达 80% 自动降一档刷新频率并提示 |
| 无 Key 模式 | 无月度额度，仅退避 + 降频；429 频发提示注册个人 Key |
| 兜底切换 | 仅主源失败（网络/超时/429/额度耗尽/币种未收录）→ CMC；主源恢复自动回落 |
| 批量 | CMC 单次 ≤100；CG 当前价按需分块 |

## 2. 交易所适配契约（ExchangeAdapter · 真实 API）

类型：

```kotlin
data class TradePage(val trades: List<Trade>, val cursor: String?)
data class Trade(
  val exchange: String, val orderId: String, val pair: String,
  val baseAsset: String, val quoteAsset: String, val side: Side,
  val price: String, val qty: String, val fee: String, val feeAsset: String,
  val time: String, val raw: JsonElement
)
data class Balance(val asset: String, val free: String, val locked: String)
data class PairInfo(val symbol: String, val baseAsset: String, val quoteAsset: String, val status: String)
```

### 2.1 BinanceAdapter（Base：`https://api.binance.com`）

| 方法 | 端点 | 说明 |
|------|------|------|
| `validateCredentials()` | `GET /api/v3/account` | 签名验证密钥（PRD §9.10） |
| `fetchBalances()` | `GET /api/v3/account` | 余额（校准数据源） |
| `fetchTrades(symbol, sinceId?, limit)` | `GET /api/v3/myTrades` | **逐 symbol**（必传）+ `limit≤500` + `fromId` 增量游标（sinceId=已同步最大成交 id）；symbol 集合与权重预算见 ADR-004 §3.1（M6 实现细化） |
| `fetchPairs()` | `GET /api/v3/exchangeInfo` | pair 注册表（公开） |

认证：头 `X-MBX-APIKEY` + 查询 `timestamp`（毫秒）+ `recvWindow`（默认 5000）→ `signature=HMAC-SHA256(queryString, secret)`。
错误映射：-2015/-2014 → 密钥失效（B2）；-1003/429 → 退避；-1021 → 时间戳重试；-1022 → 签名错误（提示密钥错误）。

### 2.2 同步语义（ADR-004）

- 增量去重键 `(exchange, exchange_order_id)`；仅写本地不存在的新交易；**不覆盖本地持仓余额**。
- 币种解析：pair 经注册表切分 → 经 exchange_coin_map 映射 coin_id，消歧四级（共享规范 §6），冻结写入。
- 同步范围收敛：候选 symbol = 余额推导 pair ∪ 历史已同步 pair（∪ 手动指定）；单次同步权重预算 ≤1200，超额排队下轮续传（ADR-004 §3.1）。
- 结果写 sync_logs（脱敏 message）；失败标 status=FAILED。

## 3. 内部服务契约（Kotlin 用例接口）

> Compose 桌面端同进程，无 IPC；UI 经 ViewModel 调 suspend 用例。命名 `域.动作`；错误统一 `DomainError(code, message)`（§4）。

```kotlin
// 账户会话
interface AccountService {
  suspend fun createAccount(req: CreateAccountReq): AccountId
  suspend fun login(req: LoginReq): Session
  suspend fun logout()
  suspend fun switchAccount(req: SwitchReq): Session
  suspend fun changePassword(req: ChangePwdReq)
  suspend fun listAccounts(): List<AccountSummary>
  fun hasRememberMe(): Boolean
}

// 交易/资金
interface LedgerService {
  suspend fun saveTransaction(t: TransactionInput): TransactionId
  suspend fun updateTransaction(t: TransactionInput)
  suspend fun deleteTransactions(ids: List<Long>)
  suspend fun listTransactions(f: TxFilter): List<TransactionRow>
  suspend fun saveCapitalFlow(f: CapitalFlowInput): CapitalFlowId
  suspend fun updateCapitalFlow(f: CapitalFlowInput)
  suspend fun deleteCapitalFlows(ids: List<Long>)
  suspend fun listCapitalFlows(f: FlowFilter): List<CapitalFlowRow>
  suspend fun parseCsv(bytes: ByteArray): CsvPreview
  suspend fun confirmCsvImport(sessionId: String): CsvSummary
  fun csvTemplatePath(): Path
}

// 行情/同步
interface MarketService {
  suspend fun refreshPrices(manual: Boolean): MarketSnapshot
  suspend fun getMarketSnapshot(): MarketSnapshot
}
interface SyncService {
  suspend fun syncNow(apiKeyId: Long?): SyncSummary
  suspend fun reconcilePosition(coinId: Long): ReconciliationRecord
}

// 备份恢复
interface BackupService {
  suspend fun exportBackup(req: ExportReq): BackupSummary
  suspend fun previewBackup(path: Path): BackupHeader
  suspend fun restoreBackup(req: RestoreReq): RestoreSummary
}

// 设置/日志
interface SettingsService {
  suspend fun getSettings(): Settings
  suspend fun patchSettings(patch: SettingsPatch)
  suspend fun getLogs(f: LogFilter): List<LogEntry>
  suspend fun exportLogs(): Path
  suspend fun generateDiagnostics(): DiagnosticsReport
}
```

> **M4 补录（计算引擎 · domain/engine，2026-09-04，模块记录 M4.md）**：重放/指标/费率/校准为纯领域服务——
> `ReplayEngine.replay/validateMutation`、`PortfolioCalculator.compute`、`FeeCalculator.feeFor + FeeRateResolver`、
> `ReconciliationService.classifySources/plan`（类型与语义见 domain/engine 包 KDoc）。**事件构造层**（DB 行 →
> LedgerEvent：币 FK → cg_id 解析、leg/fee/flow 折算价解析与 PENDING 标记）及「保存/编辑/删除 → 构造双列表
> 调 validateMutation + 违例文案映射（REPLAY_CONFLICT / INSUFFICIENT_BALANCE / INSUFFICIENT_POSITION）」归 M7/M8；
> 引擎数值基准 = PRD 附录 A 黄金用例 1–9、12（全绿，见模块记录 M4.md §3）。
>
> **M5 补录（行情链路实现，2026-09-04，模块记录 M5.md）**：§1 真实端点已实现——Ktor + OkHttp 引擎（ProxySelector），
> CoingeckoMarketClient / CmcMarketClient + DefaultMarketRefreshService（两级编排/单飞/目录每日一次/额度账本）
> + DefaultMarketHistoryBackfillService + PriceSnapshotRepository（M006 schema v6）+ DefaultMarketSettingsService
> （设备密钥方案甲，market.coingecko_key/market.cmc_key 全局行）；错误模型 MarketRefreshError 已落地
> （MockEngine 全分支绿）。消费方接线见模块记录 M5.md §6：M7 折算解析、M11 调度宿主、M12 状态栏。
>
> **D21 补录（行情浏览页 · C1 范围增量，2026-09-04，决策 docs/dev/decisions/D21-行情浏览页-范围增量.md）**：
> `MarketWatchService`（`listWatch`/`add`/`remove`/`seed`——settings 全局行 `watch.coins` JSON [cg_id]，上限 50，
> 未写入=默认种子=稳定币白名单；目录解析读时清理不改写存储、损坏自愈回默认）+ `MarketQuotesService`（行 = coins 目录
> + `price_snapshots.latest(coin, fiat)`，缺行 = 「无行情」）+ `MarketSettingsService.baseFiat()`（行情页计价）。
> 消费方 `MarketWatchPage`（第六页 QUOTES），复用 `MarketRefreshService.refresh`（币集 = 当前列表）。回溯：D21、ia.md §2.19。
> **M6 补录（交易所同步实现，2026-09-04，模块记录 M6.md）**：§2 契约已落地并细化为——
> `ExchangeAdapter`（domain/exchange，凭证绑定实例；validateCredentials/fetchBalances/fetchTrades(symbol,sinceId,limit)/fetchPairs）、
> `ExchangeError` 类型化错误（InvalidKey/RateLimited/TimestampSkew/SignatureInvalid/Network/Http/Internal）、
> `ExchangeSyncService`（listKeys/addAndSync/testCredentials/syncNow/recentSyncLogs/syncIntervalMinutes/saveSyncIntervalMinutes）、
> `ExchangeSyncPolicy`（余额推导 ∪ 已同步 pair 收敛 + ≤120 次调用预算分批）。数据表 M007 api_keys / M008 sync_logs /
> M009 transactions（schema 6→9）；同步编排见 DefaultExchangeSyncService（每 key 一轮：余额→枚举→增量拉取→币解析冻结→
> 去重写账本→sync_logs/状态）；真实端点/签名/错误映射对齐 ADR-004 §2（MockEngine 全分支绿）。
> 实现注：sync 只写交易行（source='BINANCE API'），不覆盖本地持仓；`fetchTrades` 以已同步最大成交 id 为增量游标；
> 币解析未命中（NotFound/Ambiguous/未收录）跳过并计数（sync_logs message 注明，目录更新/CSV 补录）。回溯：ADR-004、PRD 故事 4.1。

> 币解析未命中（NotFound/Ambiguous/未收录）跳过并计数（sync_logs message 注明，目录更新/CSV 补录）。回溯：ADR-004、PRD 故事 4.1。
>
> **M7 补录（交易账本实现 · 2026-09-07，模块记录 M7.md）**：§3 LedgerService 交易半边已落地——
> TransactionLedgerService（domain/ledger，api-contracts §3 细化）：
> listTransactions / saveTransaction / updateTransaction / deleteTransactions / feeQuote / searchCoins /
> parseCsv / confirmCsvImport / csvTemplateCsv。实现要点：
> - 手动增删改 = 事件构造层（DB 行 -> LedgerEvent：FK -> cg_id 解析 + 折算价解析 + PENDING 标记，
>   M4 §5 补录「事件构造层归 M7/M8」交易半边）-> ReplayEngine.validateMutation 相对校验（M4 §5-5）
>   -> 违例分类（ReplayConflictClassifier：买入计价腿不足 = V5 INSUFFICIENT_BALANCE / 卖空 = V7 同构
>   INSUFFICIENT_POSITION / 其余 = V9 REPLAY_CONFLICT）-> LedgerValidationException(code, coinSymbol) 上浮，
>   文案映射在 ui/ledger/TransactionCopy（api-contracts §4 错误码）；
> - 离线保存（PRD N3）：候选事件折算价缺失以名义折算参与数量校验，行照常落库、动态估算态由构造层承载；
> - 费率自动计算（T7.2）= FeeRateResolver（交易所 > 全局）+ FeeCalculator 三币种基数 + 现价折算；
>   fee_rules 表 = M010（schema 9->10，data-model §2.4，M7 只读 + 最小写入面，CRUD UI 归 M10）；
> - CSV（T7.3）= 标准模板/别名表头解析（UTC）-> 预览（新增/疑似重复[精确+模糊]/未解析/影响摘要）-> 确认
>   （歧义选择固化 MANUAL -> 去重复核 -> LENIENT 导入 -> 负持仓「持仓异常」清单）；
> - 卖出逐笔已实现盈亏（T7.4 卖出行）= SellRealizedTracer（展示口径，单一真源 = 引擎，
>   黄金用例交叉校验守护，模块记录 M7 §5）。回溯：PRD 故事 2.1/2.3/7.1、§9.6/9.7、interaction V1-V5/V9。
>
> **M7 修复轮补录（2026-09-07，模块记录 M7 §7）**：① 折算价完全缺失的行改**名义折算**（quote≈1，
> estimated=true）参与重放，不再排除（原口径会使后续卖出失去成本基数）；② 新增 FeeRuleService
> （listRules/saveGlobal/saveExchange/removeRule）——设置页「手续费」分组最小 CRUD，M10 T10.1 整页接管时扩展。
> ③ 顶栏常驻手动同步入口（TopBarSyncViewModel，复用 syncNow(null) 同步全部密钥；PRD 故事 4.3 + ia.md 顶栏规范；
> C0 实现补全，见模块记录 M7 §7.8）。
>
> **M8 补录（资金管理实现 · 2026-09-09，模块记录 M8.md）**：§3 LedgerService 资金半边 + 校准执行流落地——
> `FundService`（domain/ledger，FundModels.kt）：listFunds（FundPage = 混合列表 + 总览卡同源重放）/
> saveFund / updateFund / deleteFunds（按 uuid，资金行与校准行可混选）/ fundsOverview / fiatValuePreview /
> searchCoins / defaultCoinSymbol（PRD §9.8：USD→USDT、其余法币→USDC）。实现要点：
> - 手动增删改 = **事件构造层资金半边**（LedgerEventAssembler：三类记录 → FundEvent/AnchorEvent +
>   交易半边委托，确定性决胜序 (at, seq, 类型序[资金0/交易1/锚点2], uuid)）→ 双列表
>   ReplayEngine.validateMutation 相对校验（M4 §5-5）→ FundConflictClassifier 分类
>  （撤资本位点 = V7 / 其余 = V9）→ LedgerValidationException 上浮（文案映射 ui/ledger/FundsCopy）；
> - **V7 撤资同点绝对校验**（M4 §5-5「宽松口径复核归 M8」的收紧结论）：撤资本位点任何负边界
>  （含导入既存异常位点被继续加深）都阻止——「XX 持仓不足，无法撤资」（模块记录 M8 §5）；
> - 折算 = 记录时行情价链（TransactionEventBuilder.resolveFiatValue，与交易折算同源）；
>   完全缺失 = **名义零折算**（fiatValue=0 + estimated）参与重放（保数量链，黄金用例 9 回填自动纠正）；
> - **CalibrationUseCase**（domain/ledger，api-contracts §3 SyncService.reconcilePosition 细化）：
>   prepare（单一来源门 → 只读密钥 → fetchBalances 实时余额（exchange_coin_map 反向映射 + symbol 兜底）→
>   校准时市价 → ReconciliationService.plan 差额规划）→ execute（相对校验（锚点后记录冲突 = V9）→
>   reconciliation_records 入库 → **sync_logs 留痕**（PRD 故事 4.1-5））；任一前提不满足抛
>   CalibrationBlockedException（NO_RECORDS/MULTI_SOURCE/NO_EXCHANGE_KEY/NO_MARKET_PRICE/
>   BALANCE_FETCH_FAILED——PRD「按钮隐藏并显示相应提示」）；
> - 交易半边接线（M7 服务不改契约）：DefaultTransactionLedgerService 重放/校验输入改为三类事件全集
>  （增资建立的持仓即时解锁手动买入 V5 路径——M7 §6 遗留 1 落地）。
> 回溯：PRD 故事 6.1/6.2/6.3、4.1-5、§9.8、§10-8、全局说明「持仓校准规则」「成本计算规范」、
> interaction V6/V7、黄金用例 2/3/4/7/8（引擎数值 M4 已守护，接线形状随 M8 数据层测试）。
>
> **M8 修复轮补录（2026-09-09，模块记录 M8 §8）**：GUI 走查暴露同名符号歧义阻断——候选点选未直达
> 保存。修复：`FundInput.pickedCoinId` / `FundEntryRow.coinId` / `fiatValuePreview(pickedCoinId)` /
> `CalibrationUseCase.prepare/execute/history(pickedCoinId)`（点选冻结 coins.id，服务按行 id 直取 +
> 符号一致性校验，绕过符号歧义）；`FundService.defaultCoinSymbol()` 改 **`defaultCoin(): CatalogCoin?`**
>（按白名单 cg_id 直取 USD→tether / 其余→usd-coin）；候选行展示 cg_id；`CoinResolutionException`
> UI 文案中文化（coinResolutionCopy）。
>
> **M8 修复轮二补录（2026-09-09，模块记录 M8 §8-2）**：候选检索 `FundService.searchCoins` 上限
> 10→20，且**命中查询的默认币种置顶**（同名符号在目录检索按名称字母序并列，canonical 资产会被
> 挤出头部——M8 层 pin，M3 目录检索口径不动）。
>
> **M9 补录（备份恢复实现 · 2026-09-10，模块记录 M9.md）**：§3 BackupService 已落地并细化——
> `BackupService`（domain/backup）：`backupMetadata` / `exportBackup(path, password)` /
> `previewBackup(path)`（明文头部，无需密码）/ `prepareRestore(path, password)`（验证+解密+合并规划
> 预览，不落库）/ `restoreBackup(path, password, mode[MERGE|FULL_OVERWRITE])` /
> `exportCsv(kind[TRANSACTIONS|FUNDS|HOLDINGS], path)`。实现要点：
> - **CproCodec**（ADR-005 §1/§2）：头部 JSON 单行 + '\n' + nonce‖密文；AAD = 头部完整字节；
>   Argon2id(备份密码, 头部 salt/m/t/p) + AES-256-GCM；载荷币种引用统一 **cg_id**（跨设备键），
>   导入经本地目录解析回行主键，缺失币种行跳过并计数；`CproDecodeException` 三态
>   （MALFORMED_FILE / UNSUPPORTED_FORMAT / WRONG_PASSWORD_OR_CORRUPTED——§4 BACKUP_INVALID 映射）；
> - **合并规划**（BackupMergePlanner 纯规则）：业务记录 uuid → 交易所+订单号（v1 模糊级无输入面）；
>   api_keys (exchange+别名) 跳过；fee_rules/settings 备份优先覆盖；快照 币种+法币+小时桶 幂等
>  （本地同桶行保留）；全量覆盖 = 单写事务清账户业务表后全量插入（全局公共表不动）+ 临时备份；
> - **恢复编排**（DefaultBackupService）：规划 → 单写事务应用（BackupRestoreStore：api_keys 先插后包
>   目标账户 DEK 重加密）→ 恢复后全量重放（LENIENT）出「持仓异常」清单；全量覆盖前临时备份
>   `~/.wuzhufolio/backups/pre-restore-*.cpro`（同备份密码加密，不记 backup.last_at）；
> - **CSV 明文导出**（PRD §9.9）：交易/资金流水/持仓汇总，不含任何 API 密钥（§5 回溯行见 M9）；
> - **备份文件密码 = 用户设置独立密码（D24，2026-09-10 人工裁决）**：不回填当前账户密码（密码不留存
>   架构下不可回填）、禁止空密码（导出侧 AccountPolicy 同账户门槛校验，恢复侧空密码不可提交）；
>   恢复摘要异常口径 = 「本次导入新产生」与「账户既存」分开列示（RestoreSummary.anomalousCoins /
>   preExistingAnomalousCoins，避免同账户回导误读为导入造成）。
> 回溯：PRD 故事 5.2（9 条）、§9.9、§10-1/2/5/7/8 去重键、附录 A 黄金用例 10、共享规范 §8、ADR-005（含 D24 修订）。

> **M10 补录（设置与日志诊断实现 · 2026-09-10，模块记录 M10.md）**：以下契约随 T10.1–T10.4 落地——
> - **GeneralSettingsService**（domain/settings）：通用设置用例（PRD §7.2-6.1）——`view()` 视图 +
>   `setBaseFiat`（候选 USD/EUR/CNY）/ `setPrecision` / `setUsernameEnum` / `addCashCoin` /
>   `removeCashCoin`（默认白名单 tether/usd-coin/dai/true-usd 固定不可移除，仅扩展可移除）/
>   `setSmallAmountThreshold`（**自由数值 ≥ 0，0 = 不启用**——走查反馈修复轮废弃预设档，按用户
>   规模自定） / `setProxyEnabled`。存储 = settings 全局行（键：fiat〔既有〕、
>   display.precision、login.username.enum〔既有〕、cash.coins〔JSON 扩展项〕、
>   small.threshold〔基础法币数值串，0 = 不启用〕、network.proxy.enabled）；
>   扩展白名单经注入 provider 生效（TransactionEventBuilder USD 锚定集合 +
>   DefaultFundService 可用现金，默认 = 引擎常量，行为不变）。主题/盈亏配色沿用 theme/pnl_scheme
>   既有键，经 ShellViewModel 写入持久化（顶栏与设置双向同步，PRD 6.1）；
> - **LogAccess**（domain/settings）+ FileLogAccess（data/logging）：`path()` / `tailLines(max)` /
>   `exportTo(targetPath)`——查看与导出均逐行经 LogRedactor 兜底（写入侧已脱敏，双保险）；
> - **DiagnosticsService**（domain/settings）：`generate()` → DiagnosticsReport（应用/OS 版本、
>   schema 版本〔boot 迁移结果〕、行情调用计数〔额度账本〕、同步调用计数〔sync_logs 累计条数 +
>   最近同步时刻〕、最近日志片段〔尾部读取+再脱敏〕）；`DiagnosticsReportText.render` 纯渲染
>   （预览与导出同源）；内容受限清单 = PRD §6，不含密钥与完整响应体；
> - **FeeRuleService.saveExchangeEdit(id, exchange, buy, sell)**（T10.1 完整 CRUD）：按行 id 编辑
>   交易所费率；交易所名变更 = 旧键删除 + 新键落库（fee_rules 备份去重键 account+exchange 联动迁移）；
> - **日志轮转**（T10.2）：LogRotationPolicy 纯规则（1 万条或 90 天先到为准）+ LogRotator（本地日志
>   启动裁剪/删档，maxHistory 对齐 90 天）+ SyncLogRepository.rotate（sync_logs 同口径，先删过期
>   再裁 newest N）；SyncLogRepository 增 `countAll()` / `lastSyncAt()`（诊断报告取数）。
> 回溯：PRD §7.2 模块 6.1/6.3/6.4、§6「日志管理与可追溯性」、§9.11、interaction.md §2.6、ia.md §2.12。



## 4. 错误码与提示文案映射（统一异常处理）

| code | 场景 | UI 文案（PRD 统一异常处理） |
|------|------|------------------------------|
| `AUTH_INVALID` | 登录失败 | 「用户名或密码错误」（不暴露用户是否存在） |
| `AUTH_OLD_PWD` | 改密原密码错 | 「原密码不正确」 |
| `ACCOUNT_LOAD_FAILED` | 切换失败 | 「账户数据加载失败，请重试」 |
| `NETWORK_DOWN` | 行情/交易所不可达 | 状态栏断链 + 保留上次数据 + 时间戳（N1/N2） |
| `DB_CORRUPTED` | 数据文件损坏 | 全屏错误框「从备份恢复或联系技术支持」（B1） |
| `API_KEY_INVALID` | 交易所密钥失效 | 「Binance API 密钥已失效，请检查或更新」（B2） |
| `CG_QUOTA` | CG 月度额度达 80% | 「CoinGecko 额度已达本月上限，已自动降频」（B3） |
| `CG_RATE_LIMIT` | CG 共享限流 | 「CoinGecko 共享限流触发，已自动退避；建议注册免费个人 Key…」（B4） |
| `CMC_FAILED` | 兜底失败 | 保持上次价格 + 时间戳（B5） |
| `VALIDATION` | 输入校验失败 | 输入框下方红色提示，阻止提交（V1–V9） |
| `INSUFFICIENT_BALANCE` | 买入余额不足 | 「XX 余额不足，请先记录转入（增资）」（V5） |
| `INSUFFICIENT_POSITION` | 撤资持仓不足 | 「XX 持仓不足，无法撤资」（V7） |
| `REPLAY_CONFLICT` | 重放负持仓 | 冲突原因提示（V9） |
| `BACKUP_INVALID` | 备份密码错/文件损坏 | 「无效的备份文件或密码错误」 |

## 5. 需求回溯

| 契约 | PRD 章节 | 共享规范 |
|------|----------|----------|
| 行情请求/退避/额度（真实端点） | 全局说明「行情数据与时间分辨率规则」 | §5 |
| 交易所端点/去重/500 条 | 故事 4.1、§10 注 | §1/§6 |
| 错误码→文案 | 全局说明「统一异常处理」 | — |
| 引擎事件化/重放/指标/费率/校准（M4） | 全局说明「成本计算规范」「持仓校准规则」、故事 7.1/6.4、附录 A | §2 |
| 资金/校准用例（M8） | 故事 6.1/6.2/6.3、4.1-5、§9.8、全局说明「持仓校准规则」 | §2/§3 |
| 行情客户端/快照/24h/回填/额度（M5） | 故事 3.2、全局说明「行情数据与时间分辨率规则」、§7.2 模块 6.1 | §5 |
| 备份格式/合并/恢复/CSV（M9） | 故事 5.2、§9.9、附录 A 用例 10 | §8 |
| 内部服务接口 | ia.md 页面清单、flows.md 状态机 | — |