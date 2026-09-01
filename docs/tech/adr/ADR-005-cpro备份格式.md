# ADR-005 .cpro 备份格式（明文头部 + AES-256-GCM 加密 JSON 载荷，Kotlin）

- **状态**：**人工拍板采纳**（2026-08-31，含行情 Key 不进备份的方案甲口径）
- **日期**：2026-08-31（本轮适配 Kotlin + Compose Desktop）
- **决策类型**：架构级
- **需求回溯**：PRD 故事 5.2、PRD §7.2 模块 5、PRD §9.9、共享规范 §8、`桌面端prd-三大决策分析.md`（D3/D4）

---

## 背景

备份需把当前账户全部业务数据导出为单一 `.cpro` 文件，跨设备/跨账户/未来移动端可恢复；不含任何账户信息；独立备份文件密码；增量合并默认、全量覆盖可选；明文头部供摘要展示。格式契约与技术栈无关，本 ADR 在 Kotlin/JVM 下落地实现（kotlinx.serialization 编 JSON + JDK JCE 加密）。

## 决策

### 1. 文件结构

`.cpro` = 明文头部 + 分隔符 + 加密载荷（单文件两段式）：

- **明文头部（JSON，未加密）**：`format_version`、`app_version`、`exported_at`（UTC）、`counts{transactions, capital_flows, reconciliation_records, fee_rules, api_keys, settings, price_snapshots}`、`range{min_time, max_time}`、`kdf{alg, salt, m, t, p}`、`cipher=aes-256-gcm`。恢复前仅展示此摘要（PRD 故事 5.2-5）。
- **加密载荷（二进制，AES-256-GCM）**：明文为 JSON（kotlinx.serialization 编码，跨平台跨版本可读），结构 `payload{meta, records{...}}`，记录保留 `uuid`、`exchange_order_id` 等去重键。

### 2. 备份文件密码与加密语义

- 备份密码独立设置，导出默认填当前账户密码、可修改；须满足与账户密码相同最低强度（PRD 故事 5.2-3）。
- 备份密钥 = Argon2id(备份密码, 随机 salt)（参数记入头部 `kdf`）；载荷 = AES-256-GCM(明文=payload JSON, key=备份密钥, nonce=随机 96-bit)（JDK `javax.crypto`）。
- **导出**：先将 api_keys 等 DEK 加密字段**解密**，与业务数据一同入载荷（PRD 故事 5.2-9）。
- **导入**：备份密码解密载荷后，凭证字段以**目标账户 DEK 重新加密**落库（跨设备/跨账户恢复只依赖备份密码，与原账户密码无关）。

### 3. 备份内容范围

- 包含：transactions、capital_flows、reconciliation_records、fee_rules、api_keys（凭证先解密）、账户级 settings、本账户涉及币种与法币的 price_snapshots（降采样后）。
- **不包含**：accounts 表任何字段（用户名/密码哈希/KDF 参数/wrapped_dek）、全局设置、全局公共表 coins/exchange_coin_map（共享规范 §8）。**行情平台 Key（CG/CMC）属全局应用级秘密（设备密钥加密，ADR-002 §2.1 方案甲），不随备份导出**——恢复后用户在设置中重新配置；账户级 settings 中也不含行情 Key 明文。

### 4. 恢复语义

- 增量合并（默认）去重优先级：记录 uuid → 交易所+订单号 → 无标识模糊匹配并逐条确认；api_keys 按 (exchange_name+别名)；fee_rules 按 (account_id+exchange)（备份优先覆盖）；账户级 settings 按 key 逐项覆盖（备份优先）；price_snapshots 按 (coin_id+fiat+recorded_at) 幂等（PRD 故事 5.2-6）。
- 全量覆盖（可选）：二次确认 + 覆盖前自动临时备份；仅作用于当前账户账户级业务数据，**全局公共表不清空**。
- 导入完成触发全量重放重算并重新加载数据（PRD 故事 5.2-7）。
- 全新安装（无账户）：初始化向导「从备份恢复」，先创建/选定目标账户再导入（PRD 故事 5.2-8）。

### 5. 版本与兼容

- `format_version` 递增；导入按版本分支解析；未知更高版本提示升级应用。
- 明文导出（CSV：交易/资金流水/持仓汇总，不含密钥）与 `.cpro` 分离实现（PRD §9.9）。

## 选项

| 选项 | 优点 | 缺点 |
|------|------|------|
| **明文头部 + AES-256-GCM JSON 载荷（选定）** | 与 PRD/共享规范 §8 逐字一致；摘要可读；JSON 跨平台跨版本 | 头部明文暴露条数/时间范围（PRD 已接受） |
| 整库 SQLite 快照 | 最快 | 非 JSON、跨版本/移动端可读性差，违反 PRD 5.2-2 |
| 全量加密（无头部） | 隐私最大化 | 无法先看摘要再输密码，违反恢复流程 |

## 理由

1. 逐字对齐 PRD 故事 5.2 与共享规范 §8。
2. 增量合并的 uuid/订单号/快照幂等键全在数据模型内，可直接测试（黄金用例 10）。
3. 备份密码独立 + 不含账户信息，保证新设备/跨账户/未来移动端导入。

## 风险

| 风险 | 缓解 |
|------|------|
| 备份密码强度不足 | 复用账户密码强度校验 |
| 大体积载荷内存占用 | 流式分块读写（P4）；降采样快照控体积 |
| 旧版本格式兼容 | format_version 分支解析 + 导入前版本提示 |
| 全量覆盖误操作 | 二次确认 + 自动临时备份 |

## 备注

- 备份/恢复全程进度提示；导出前提示「备份文件包含 API 密钥等敏感数据，请妥善保管」（PRD 故事 5.2-4）。
