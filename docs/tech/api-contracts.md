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
| `fetchTrades(symbols, since?, cursor?)` | `GET /api/v3/myTrades` | **逐 symbol**（必传）+ `limit≤500` + `fromId`/时间分页；symbol 集合与权重预算见 ADR-004 §3.1 |
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
| 内部服务接口 | ia.md 页面清单、flows.md 状态机 | — |
