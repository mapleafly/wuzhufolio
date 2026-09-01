# ADR-003 行情客户端（CoinGecko 主源 + CoinMarketCap 兜底 两级模式，真实 API）

- **状态**：**人工拍板采纳**（2026-08-31）
- **日期**：2026-08-31（本轮按人工指令：基于 CG/CMC 真实 API 落契约）
- **决策类型**：架构级
- **需求回溯**：PRD 全局说明「行情数据与时间分辨率规则」「币种标识与主数据规则」、PRD 故事 3.2/4.2、共享规范 §5/§6、`桌面端prd-币种标识与行情源决策分析.md`（D7）

---

## 背景

行情数据刷新与交易数据同步是两类相互独立的 API（PRD 名词解释）。人工指令要求行情客户端**基于 CoinGecko 与 CoinMarketCap 两个平台的真实 API**。本 ADR 将 PRD/共享规范 §5 的两级模式落到两个平台的真实端点、请求头、参数与限额，并固化额度治理与退避语义。

## 决策

### 1. 统一抽象 `MarketDataClient`（Kotlin 接口）

```kotlin
interface MarketDataClient {
  suspend fun fetchCurrent(coins: List<CoinId>, fiats: List<Fiat>): List<Quote>
  suspend fun fetchHistory(coin: CoinId, fiat: Fiat, from: Long, to: Long): List<Candle>
  suspend fun fetchDirectory(): List<Coin>
  suspend fun fetchCmcMap(): List<CmcCoin>
}
```

两个实现 `CoingeckoProvider`（主源）/ `CmcProvider`（兜底），由 `MarketDataOrchestrator` 选择与切换；HTTP 用 Ktor client **OkHttp 引擎**（OkHttp 原生支持 JVM `ProxySelector`，满足 PRD 故事 4.2 系统代理自动检测；CIO/Apache 引擎不自动遵循 ProxySelector，不采用——评审 N4）。

### 2. 主源 CoinGecko（真实 API，免费 Demo 档）

Base：`https://api.coingecko.com/api/v3`。个人 Key 经请求头 `x-cg-demo-api-key: <key>`（无 Key 公共模式不带头）。

| 用途 | 端点 | 关键参数 | 说明 |
|------|------|----------|------|
| 当前价批量 | `GET /simple/price` | `ids=bitcoin,ethereum,…`、`vs_currencies=usd,eur,cny`、`include_24hr_change`（可选） | 多币×多法币一次取回 |
| 全量目录 | `GET /coins/list` | `include_platform=true` | id/symbol/name/各链合约地址；每日刷新（PRD 共享规范 §6） |
| 历史价（小时/日） | `GET /coins/{id}/market_chart` | `vs_currency`、`days`（≤90 小时级、>90 日级）、`interval` | 回填与 24h 前价 |
| 历史价（区间） | `GET /coins/{id}/market_chart/range` | `vs_currency`、`from`、`to`（unix 秒） | 批量合并回填 |
| 单日历史 | `GET /coins/{id}/history` | `date=dd-mm-yyyy` | >90 天当日日线 |

限额：免费 Demo 档约 **30 次/分钟、10,000 次/月**；无 Key 公共 API 按 IP 共享限流约 30 次/分钟（PRD 共享规范 §5）。
错误：429（限流）、401（Key 失效）、404（币种未收录）、超时。

### 3. 兜底 CoinMarketCap（真实 API，免费 Basic 档）

Base：`https://pro-api.coinmarketcap.com/v1`。个人 Key 经请求头 `X-CMC_PRO_API_KEY: <key>`。

| 用途 | 端点 | 关键参数 | 说明 |
|------|------|----------|------|
| 当前价批量 | `GET /cryptocurrency/quotes/latest` | `id=1,2,…`（**单次 ≤100**）、`convert=USD,EUR,CNY` | 直出多法币（无链式换算） |
| 币种 id 映射 | `GET /cryptocurrency/map` | `symbol`（可选）、`limit` | cmc_id 每日缓存，回填 coins.cmc_id |

限额：免费 Basic 档 10,000 call credits/月；429/401/402（额度耗尽）。
能力边界：**无免费历史价格**（历史端点需付费档），故历史回填不做兜底（PRD 共享规范 §5）。

### 4. 两级切换与额度治理（PRD 共享规范 §5）

- 主源成功：写快照 `price_source=COINGECKO`。
- 主源失败（网络/超时/429/额度耗尽/币种未收录）且已配 CMC Key：切 CMC 兜底，写 `price_source=COINMARKETCAP`，UI 标「数据源：CoinMarketCap」；主源恢复自动回落（无持久状态）。
- 未配 CMC Key 或兜底失败：保持上次价格 + 显示上次成功时间戳。
- 429 退避：指数退避 + 抖动（1s→…上限 60s）；无 Key 模式 429 频发提示注册个人 Key（PRD B4 文案）。
- 月度额度治理：本地持久化调用计数（当前价+历史回填+目录），达 80% 自动降一档刷新频率并提示（PRD 故事 3.2-6）。

### 5. 快照与 24h 同源

- 写 `price_snapshots`：每 (coin_id, fiat) 每小时最多一条（同小时取末条），记 `price_source`，永久保存并降采样（近 90 天小时级、更早日级）。
- 24h 盈亏同源：优先同源快照配对；异源配对标「混合数据源」（共享规范 §5）。

## 选项

| 选项 | 优点 | 缺点 |
|------|------|------|
| **CoinGecko 主 + CMC 兜底 两级（选定）** | 与 PRD/共享规范逐字一致；真实端点已核实 | 需维护两 provider + 额度治理 |
| 仅 CoinGecko 单源 | 最简 | 主源故障无兜底，违反 PRD 故事 3.2 |
| Binance K 线兜底 | 免费无限额 | PRD V1.6 已明确 Binance 退出行情角色，违反定稿 |
| CMC 作主源 | — | 无免费历史回填，违反折算/24h 需求 |

## 理由

1. 逐字落地 PRD 共享规范 §5 与故事 3.2；端点/参数/限额按两个平台真实 API 固化。
2. provider 抽象与交易数据同步（ADR-004）完全解耦，两类 API 独立。
3. 兜底/退避语义覆盖 PRD 统一异常处理 B3/B4/B5。

## 风险

| 风险 | 缓解 |
|------|------|
| 免费档额度/端点行为第三方可能变化 | 契约集中在 `MarketDataClient`；P6 用 mock 覆盖 429/额度耗尽/币种未收录 |
| CMC Basic 档 credits 与 ≤100 限制 | 分批 + 仅兜底期使用；主源恢复即回落 |
| 无 Key 公共 API 在 NAT 出口误伤 429 | 退避 + 提示注册个人 Key（PRD 已规定文案） |
| 历史回填额度消耗 | `market_chart/range` 区间批量合并 + 空洞去重 |

## 备注

- 应用不内置任何行情 Key；Key 仅存本地，按**设备密钥**加密后存 settings 全局行（ADR-002 §2.1 方案甲）；**不进 .cpro 备份**，恢复后在设置中重新配置（ADR-005 §3）。
- 端点细节以 P4 实现时两个平台最新文档为准，契约表在 `api-contracts.md` §1 同步维护。
