# WuZhuFolio 数据模型（data-model.md）

> P2 产物 · 桌面端 · 依据 PRD V1.9 §10 数据结构 + 跨端共享规范 V1.0 §6/§7/§8。
> 本文件是 P4 各模块建表与 SQL 的唯一基准；表结构以 PRD §10 为准，此处补充 ER 关系、索引、约束与派生口径。

---

## 1. 实体总览与 ER 图

11 张表分两类：
- **账户级业务表**（按 account_id 隔离，备份导出/恢复范围）：accounts、api_keys、settings（账户级）、fee_rules、transactions、capital_flows、reconciliation_records、sync_logs。
- **全局公共表**（各账户共享，不随账户备份导出、全量覆盖不清空）：coins、exchange_coin_map、price_snapshots。

派生值（不落表）：持仓、平均成本、累计增资/撤资、投入本金（净）、总收益、ROI、已实现盈亏、可用现金余额、24h 盈亏 —— 全部由 ReplayEngine/PortfolioCalculator 从三类事件（transactions、capital_flows、reconciliation_records）推导（PRD §10 注）。

```mermaid
erDiagram
  ACCOUNTS ||--o{ API_KEYS : owns
  ACCOUNTS ||--o{ SETTINGS : has
  ACCOUNTS ||--o{ FEE_RULES : has
  ACCOUNTS ||--o{ TRANSACTIONS : has
  ACCOUNTS ||--o{ CAPITAL_FLOWS : has
  ACCOUNTS ||--o{ RECONCILIATION_RECORDS : has
  ACCOUNTS ||--o{ SYNC_LOGS : has
  API_KEYS ||--o{ SYNC_LOGS : produces
  COINS ||--o{ TRANSACTIONS : base_coin
  COINS ||--o{ TRANSACTIONS : quote_coin
  COINS ||--o{ CAPITAL_FLOWS : coin
  COINS ||--o{ RECONCILIATION_RECORDS : coin
  COINS ||--o{ EXCHANGE_COIN_MAP : maps
  COINS ||--o{ PRICE_SNAPSHOTS : priced
```

## 2. 表结构（含索引与约束）

> 类型口径：Integer 主键自增；Decimal 用 Kotlin `BigDecimal`（或 `Long` 按 1e-8 缩放定点）映射 SQLite NUMERIC，精度满足「金额/价格 8 位小数」（PRD 全局说明「数据精度」）；时间戳一律 UTC（共享规范 §4）；uuid 为 UUID v4。访问层用 Exposed（JDBC + SQLCipher，见 ADR-002）。

### 2.1 accounts（账户表）—— PRD §10-4

| 字段 | 类型 | 约束/说明 |
|------|------|-----------|
| id | Integer | PK 自增 |
| username | String | UNIQUE，非空 |
| password_hash | String | 不可逆哈希（仅登录校验） |
| kdf_salt | String | KDF 盐 |
| kdf_params | String | KDF 算法与参数 JSON（Argon2id m/t/p） |
| wrapped_dek | String | KEK 包裹的账户 DEK（AES-256-GCM） |
| created_at | Timestamp | 默认 UTC 当前 |

索引：UNIQUE(username)。备注：不含明文密码（PRD 故事 5.1-4）。

### 2.2 api_keys（API 密钥表）—— PRD §10-2

| 字段 | 类型 | 约束/说明 |
|------|------|-----------|
| id | Integer | PK 自增 |
| account_id | Integer | FK accounts.id，非空 |
| name | String | 别名 |
| exchange_name | String | 交易所标识（MVP=BINANCE） |
| api_key | String | **DEK 字段级加密** |
| secret_key | String | **DEK 字段级加密** |
| passphrase | String | 可选，**DEK 字段级加密**（MVP 空） |
| extra | JSON | 可选，**DEK 字段级加密**（后续交易所） |
| last_sync_time | Timestamp | 最后成功同步 |
| status | String | OK / FAILED |

唯一约束：UNIQUE(account_id, exchange_name, name)。去重键（备份）= (exchange_name, name)（PRD 故事 5.2-6）。加密边界见 ADR-002。

### 2.3 settings（设置表）—— PRD §10-3

| 字段 | 类型 | 约束/说明 |
|------|------|-----------|
| id | Integer | PK 自增 |
| account_id | Integer | 可空（NULL=全局设置） |
| key | String | 设置项名 |
| value | String | 设置项值 |

唯一约束：表达式唯一索引 UNIQUE(COALESCE(account_id, 0), key)——SQLite 中 NULL 参与 UNIQUE 视为互不相等，普通 UNIQUE(account_id, key) 对全局设置（account_id NULL）不生效（评审 N1）。账户级备份按 key 覆盖（备份优先）。**行情平台 Key（CG/CMC）**：存本表**全局行**（account_id NULL，key=market.coingecko_key / market.cmc_key），按**设备密钥**加密（非账户 DEK，ADR-002 §2.1 方案甲）；**不随 .cpro 备份导出**，恢复后在设置中重新配置（ADR-005 §3）。

### 2.4 fee_rules（手续费费率规则表）—— PRD §10-7

| 字段 | 类型 | 约束/说明 |
|------|------|-----------|
| id | Integer | PK 自增 |
| account_id | Integer | FK accounts.id |
| exchange | String | 空=全局默认 |
| buy_rate | Decimal | 买入费率（%） |
| sell_rate | Decimal | 卖出费率（%） |
| created_at | Timestamp | — |

匹配优先级：交易所 > 全局（PRD §10-7 注）；备份去重键 (account_id, exchange)。

### 2.5 transactions（交易记录表）—— PRD §10-1

| 字段 | 类型 | 约束/说明 |
|------|------|-----------|
| id | Integer | PK 自增 |
| account_id | Integer | FK accounts.id |
| exchange | String | 交易所名称 |
| exchange_order_id | String | 可空；手动录入空；API/CSV 去重键 |
| pair | String | 交易对（展示） |
| base_coin_id | Integer | FK coins.id，保存时冻结 |
| quote_coin_id | Integer | FK coins.id，保存时冻结 |
| type | Enum | BUY / SELL |
| price | Decimal | 成交价 |
| quantity | Decimal | 数量（base） |
| fee | Decimal | 手续费（以 fee_currency 计价） |
| fee_currency | String | 手续费币种 |
| transaction_time | Timestamp | 交易时间（UTC） |
| notes | String | 备注 |
| created_at | Timestamp | — |
| source | String | Manual / CSV / BINANCE API |
| uuid | String | UUID v4，备份去重 |
| price_status | String | OK / PENDING |

索引：account_id、transaction_time。去重键落为部分唯一索引 UNIQUE(account_id, exchange, exchange_order_id) WHERE exchange_order_id IS NOT NULL（数据库层防并发漏重，评审 N1）；无订单号走模糊匹配（时间+交易对+类型+数量+价格）。

### 2.6 capital_flows（资金流水表）—— PRD §10-5

| 字段 | 类型 | 约束/说明 |
|------|------|-----------|
| id | Integer | PK 自增 |
| account_id | Integer | FK accounts.id |
| type | Enum | DEPOSIT / WITHDRAWAL |
| amount | Decimal | 数量（原币种） |
| base_amount | Decimal | 折算基础法币金额 |
| currency | String | 币种（展示） |
| coin_id | Integer | FK coins.id，保存时冻结 |
| flow_time | Timestamp | 流水时间（UTC） |
| source_dest | String | 来源/去向 |
| notes | String | 备注 |
| created_at | Timestamp | — |
| uuid | String | UUID v4 |
| price_status | String | OK / PENDING |

### 2.7 reconciliation_records（持仓校准记录表）—— PRD §10-8

| 字段 | 类型 | 约束/说明 |
|------|------|-----------|
| id | Integer | PK 自增 |
| account_id | Integer | FK accounts.id |
| symbol | String | 币种代码（展示） |
| coin_id | Integer | FK coins.id，冻结 |
| exchange | String | 校准依据交易所 |
| local_quantity | Decimal | 校准前本地持仓 |
| exchange_quantity | Decimal | 交易所余额 |
| delta | Decimal | exchange_quantity − local_quantity |
| base_amount | Decimal | 差额折算基础法币金额 |
| uuid | String | UUID v4 |
| created_at | Timestamp | 校准时间 |

锚点语义参与全量重放；不可编辑、仅可删除（PRD §10-8 注）。

### 2.8 sync_logs（同步日志表）—— PRD §10-9

| 字段 | 类型 | 约束/说明 |
|------|------|-----------|
| id | Integer | PK 自增 |
| account_id | Integer | FK accounts.id |
| api_key_id | Integer | 可空（行情刷新记录时为空） |
| sync_time | Timestamp | — |
| status | String | OK / FAILED |
| new_trades_count | Integer | 本次新增交易数 |
| message | String | 脱敏结果信息（禁密钥/完整响应体） |

轮转：与本地日志各保留 1 万条或 90 天先到为准（PRD §6）。

### 2.9 coins（币种目录表，全局）—— PRD §10-10

| 字段 | 类型 | 约束/说明 |
|------|------|-----------|
| id | Integer | PK 自增 |
| cg_id | String | UNIQUE，CoinGecko id = 内部唯一标识 |
| cmc_id | String | 可空，CMC id（/cryptocurrency/map 每日缓存） |
| symbol | String | 展示 ticker（大小写归一） |
| name | String | 名称 |
| status | String | ACTIVE / DELISTED / UNTRACKED |
| display_precision | Integer | 价格展示精度 |
| updated_at | Timestamp | 目录刷新时间 |

来源 CoinGecko /coins/list（每日）+ CMC /cryptocurrency/map；全局公共、不随备份导出。

### 2.10 exchange_coin_map（交易所资产映射表，全局）—— PRD §10-11

| 字段 | 类型 | 约束/说明 |
|------|------|-----------|
| id | Integer | PK 自增 |
| exchange | String | 交易所 |
| exchange_asset | String | 交易所资产标识 |
| coin_id | Integer | FK coins.id |
| source | String | AUTO / MANUAL |
| created_at / updated_at | Timestamp | — |

唯一约束 (exchange, exchange_asset)；pair 注册表另作缓存；全局公共、不随备份导出（MANUAL 丢失后下次导入重确认）。

### 2.11 price_snapshots（价格快照表，全局）—— PRD §10-6

| 字段 | 类型 | 约束/说明 |
|------|------|-----------|
| id | Integer | PK 自增 |
| coin_id | Integer | FK coins.id |
| fiat | String | 计价法币 |
| price | Decimal | 价格 |
| price_source | String | COINGECKO / COINMARKETCAP |
| recorded_at | Timestamp | UTC |

每 (coin_id, fiat) 每小时最多一条（同小时取末条；应用层 upsert 实现：按 (coin_id, fiat, 小时桶) 先查后写，写入经单写队列串行保证，评审 N1）；永久保存 + 降采样（近 90 天小时级、更早日级）；仅记录持仓涉及币种、现金类币种与使用中法币；随备份打包降采样数据。

## 3. 派生口径（不落表，由引擎推导）

| 派生值 | 公式/来源 | 需求依据 |
|--------|-----------|----------|
| 持仓数量/平均成本 | 三类事件按时间升序重放 | 共享规范 §2 |
| 累计增资 / 累计撤资 | 增资 base_amount 之和 / 撤资 base_amount 之和（含校准差额） | PRD 名词解释 |
| 投入本金（净） | 累计增资 − 累计撤资 | PRD 名词解释 |
| 总收益 | 当前资产净值 + 累计撤资 − 累计增资 | PRD 名词解释 |
| ROI | 总收益 / 累计增资 × 100%（累计增资=0 显示 "--"） | PRD 名词解释 |
| 已实现盈亏 | 卖出净收入 − 卖出数量 × 当时平均成本（逐笔） | 共享规范 §2 |
| 可用现金余额 | 稳定币白名单持仓按现价折算基础法币之和 | PRD 名词解释 |
| 24h 盈亏 | Σ(持仓×现价) − Σ(持仓×24h 前价)，同源/覆盖 N/M 口径 | PRD 名词解释 |

## 4. 需求回溯

| 表 | PRD 章节 | 共享规范 |
|----|----------|----------|
| accounts / api_keys / settings / fee_rules | §10-2/3/4/7 | §7（密钥/加密边界） |
| transactions / capital_flows | §10-1/5 | §2/§3/§6 |
| reconciliation_records | §10-8 | §2（校准） |
| sync_logs | §10-9 | §1（交易数据同步） |
| coins / exchange_coin_map | §10-10/11 | §6（币种标识与主数据） |
| price_snapshots | §10-6 | §5（行情/时间分辨率） |
| 派生口径 | 全局说明/附录 A | §2 |

## 5. 迁移与版本

- `schema_version` + 迁移脚本管理（PRD §10 末尾）；`.cpro` 内 format_version 独立（ADR-005）。
- P4 建表严格按本节字段名与约束；字段级加密仅限 api_keys 四列（ADR-002），其余列可索引/排序。
