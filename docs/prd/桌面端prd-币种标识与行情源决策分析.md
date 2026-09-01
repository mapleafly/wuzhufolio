# 桌面端 PRD 币种标识与行情源决策分析

**背景:** 《桌面端prd评审报告V3》T9（币种主数据缺失）与 T2（兜底切换规则未定义）的延伸。产品负责人提出：CoinGecko / CoinMarketCap / 交易所对币种的记录是否一致？软件应采用单一平台 API 还是多平台混合？本文基于三家官方文档整理对比，并给出设计建议。
**日期:** 2026-08-26
**状态:** 已决策并落地（见文末 H 节决策记录；对应修订已写入《桌面端prd.md》V1.5）

---

## A. 结论摘要

先直接回答三个问题：

1. **不一致。** 三家各自维护独立的标识体系，且其中两家官方明确表示 ticker/symbol **不保证唯一**：
   - CoinGecko：唯一标识是字符串 id（如 `bitcoin`）；官方说明 symbol 不可保留、可被其他代币重复使用；
   - CoinMarketCap：唯一标识是数字 id；官方专文建议“用 ID 而非 symbol”，因为 symbol 存在重复；
   - Binance：唯一标识是交易对字符串（如 `BTCUSDT`）与 baseAsset/quoteAsset，仅在**本交易所内**唯一，与外部体系无对应保证。
2. **无论单一还是多源，都必须自建“币种主数据 + 映射层”。** 这是绕不开的基础设施（连成熟的加密税务工具 BittyTax 也为此维护了专门的 Binance 资产映射脚本，并因 CoinGecko symbol 非唯一踩过坑）。
3. **推荐“单一主源（CoinGecko）+ 故障兜底（Binance K 线）”，不采用按币种混合计价。** 混合使用会导致同一组合内价格方法论不一致（聚合价 vs 单所价并存）、24h 盈亏跨源混算、历史折算口径分裂，且对非 USD 基础法币并没有省掉 CoinGecko 依赖。

## B. 三平台标识体系对比（基于官方文档整理）

| 维度 | CoinGecko | CoinMarketCap | Binance |
| --- | --- | --- | --- |
| 唯一标识 | 字符串 id（slug，如 `bitcoin`、`render-token`） | 数字 id（如 Bitcoin=1） | 交易对字符串（如 `BTCUSDT`）+ baseAsset / quoteAsset |
| symbol/ticker 唯一性 | ❌ 不唯一（官方：不可保留、可重复） | ❌ 不唯一（官方建议用 ID） | 交易所内唯一，但仅限本所资产 |
| 币种目录获取 | `/coins/list`：免费公共端点，单次调用返回全量（id/symbol/name，可选 `include_platform=true` 返回各链合约地址） | `/cryptocurrency/map`：需 API key、消耗额度 | `/api/v3/exchangeInfo`：公共、免费、无需 key，含每个交易对的 baseAsset/quoteAsset 与精度过滤器（tickSize/stepSize） |
| 覆盖范围 | 1 万+ 币种，含多链代币、包装资产 | 数千主流 + 长尾 | 仅本所上架资产（含 BTCB 等本所专有资产） |
| 多法币报价 | ✅ vs_currencies（USD/CNY/EUR 等） | ✅ | ❌ 以 USDT 等稳定币计价为主，少量法币对（如 BTCEUR） |
| 历史数据（免费档） | ✅ 近 90 天小时级 / 全历史日线 | ❌ 需付费档 | ✅ K 线可回溯至交易对上市 |
| id 稳定性 | 更名通常不改 id（见 C-2），但文档未承诺永久不变 | id 稳定 | 随上架/下架变化 |

来源：[CoinGecko /coins/list 官方文档](https://docs.coingecko.com/reference/coins-list)、[CoinGecko 官方：能否保留/独占一个 symbol](https://support.coingecko.com/hc/en-us/articles/4498962550681-Can-I-reserve-a-token-symbol-or-use-another-token-s-symbol)、[CoinMarketCap 官方：为什么应该用 ID 而非 symbol](https://coinmarketcap.com/api/resources/why-you-should-use-coinmarketcap-ids-instead-of-symbols/)、[CoinGecko 免费公共 API](https://docs.coingecko.com/docs/keyless-public-api)、[Binance exchangeInfo 官方文档](https://developers.binance.com/legacy-docs/alpha/market-data/rest-api/get-exchange-info)、[CoinMarketCap Cryptocurrency API 文档](https://coinmarketcap.com/api/documentation/pro-api-reference/cryptocurrency)

## C. 四类不一致的具体风险（含实例）

1. **同名 ticker 冲突。** CoinGecko 与 CMC 目录中都存在多个币共用同一 symbol 的情况。真实案例：加密税务工具 BittyTax 在 GitHub 上先后提出 [issue #34 "symbol is not unique in CoinGecko API"](https://github.com/BittyTax/BittyTax/issues/34) 与 [#238 "CoinGecko symbol matching is unintuitive"](https://github.com/BittyTax/BittyTax/issues/238)——只按 symbol 匹配会命中错误资产，直接算错税务成本。
2. **更名与迁移。** Render 由 RNDR 更名 RENDER（CoinGecko 的 id `render-token` 保持不变）、VeChain 由 VEN 迁移为 VET、Aragon 由 ANT 迁移为 SI。**交易所 ticker 会变、CoinGecko id 通常不变**——这直接决定了“历史记录该存什么标识”：存 ticker 则更名即断裂，存 id 则可平滑延续。
3. **交易所专有/包装资产。** BTCB（Binance-Peg BTC）、BETH/WBETH（质押衍生品）是独立资产，不能与 BTC/ETH 静默合并；Kraken 用 XBT 表示 BTC。每家交易所都可能有自己的命名习惯，**交易所资产 → 通用资产**必须显式映射，无法按字符串想当然。
4. **交易对字符串本身不可靠切分。** `ETHBTC` 这类拼接对无法从字符串本身判断 base/quote 边界，必须依赖交易所的 pair 注册表（exchangeInfo 中每个交易对显式给出 baseAsset/quoteAsset）。Binance API 返回的成交记录只有 `BTCUSDT` 形式的 symbol，切分错误则币种映射全错。

另有一层“覆盖与生命周期”差异：新币上架时差（Binance 先上、CoinGecko 后收录或相反）、下架后 CoinGecko 可能转为不追踪状态、Binance 停止交易后 K 线冻结。

## D. 单一主源 vs 多平台混合：方案对比

| | 方案一：单一主源 + 故障兜底（**推荐**） | 方案二：按币种混合计价 | 方案三：CG 主 + CMC 双备 |
| --- | --- | --- | --- |
| 做法 | 全部计价统一走 CoinGecko；Binance K 线仅在主源失败（网络错误/未收录/额度耗尽）时临时兜底，恢复后自动回落 | Binance 在架币直接用 Binance 价，其余用 CG | CMC 作为常态第二源 |
| 价格方法论一致性 | ✅ 同一时刻全组合统一（CoinGecko 聚合价） | ❌ 聚合价与单所价并存，总资产内跨币种不可比 | ⚠️ 两套聚合价并存，切换即跳变 |
| 24h 盈亏 | ✅ 快照同源可比 | ❌ t-24h 与 t0 可能异源，混入方法论差异 | ⚠️ 同左 |
| 历史折算/成本 | ✅ 单一口径 | ❌ CG 日线与 Binance K 线混用 | ⚠️ CMC 免费档无历史，折算仍全靠 CG |
| 法币折算 | ✅ vs_currencies 一次到位 | ❌ Binance 几乎只有 USDT 计价，非 USD 基础法币仍须回 CG 换算——混合并没有省掉 CG | ⚠️ 两套多法币体系、双份映射 |
| 额度与依赖 | 主源一份 + 兜底免费 | 两份常态消耗 | 两份常态消耗 |
| 结论 | **推荐**（与 V1.4 现有“主源+兜底”定位一致，补全规则） | 不推荐 | 不推荐（CMC 免费档无历史数据，作为备源边际价值低） |

**兜底的正确姿势（方案一细则，现状 PRD 未定义，属 T2 遗留）：**

- 触发条件：CoinGecko 请求失败（网络/超时）、429 额度耗尽、币种未收录（且 Binance 有在架交易对）。
- 折算链：Binance 兜底价 = K线(币/USDT) × CoinGecko 的 USDT/基础法币报价（两步，法币腿仍走 CG）。
- UI 标注：兜底期间价格旁标注“数据源：Binance”，状态栏可见。
- 快照记录 source（见 E-2 的 price_snapshots.source 字段）；24h 盈亏计算优先取同源快照对，异源配对时结果标注“混合数据源”。
- 主源恢复后自动回落，不产生持久状态。

## E. 币种主数据（coins 目录）设计方案

**E.1 内部唯一标识直接采用 CoinGecko id（不自造 ID）。** 理由：① 主源即 CoinGecko，用其 id 定价零转换；② id 是公开 slug、更名通常不变（C-2）；③ `/coins/list` 免费单次可得全量目录；④ 自造 ID 需维护双向映射、纯属额外成本。对策：id 变更属罕见事件，映射表本地缓存 + 出现断链时按迁移脚本处理。未来若接入 CMC，只需增加 cg_id ↔ cmc_id 的对照列，不影响账本。

**E.2 数据模型（第 10 章新增两表 + 快照语义升级）：**

| 表 | 字段要点 | 说明 |
| --- | --- | --- |
| `coins`（币种目录） | id（内部自增）、cg_id（唯一索引）、symbol、name、status（ACTIVE/DELISTED/UNTRACKED）、display_precision、updated_at | 目录数据，随 /coins/list 每日刷新；本地缓存 |
| `exchange_coin_map`（交易所资产映射） | exchange、exchange_asset（如 Binance 的 baseAsset）、coin_id（外键）、source（AUTO/MANUAL）、唯一约束 (exchange, exchange_asset) | 用户消歧选择后固化（MANUAL），自动匹配为 AUTO；一次性决策、后续自动复用 |
| `price_snapshots`（升级） | symbol 字段语义改为内部币种 id；**新增 source 字段**（CG/BINANCE） | 消除 "usdt"/"USDT" 大小写分裂；source 支撑 24h 同源原则（回收三大决策分析 B.4-6 中被丢弃的 price_source 建议） |
| `transactions` / `capital_flows`（升级） | 新增 base_coin_id / quote_coin_id（或 coin_id），保存时冻结解析结果 | **映射冻结原则**：后续目录变化不回溯改写历史记录 |

交易对切分所需的 pair 注册表（每个 Binance 交易对的 baseAsset/quoteAsset）随 exchangeCoinInfo 一次性拉取缓存，用于 CSV/API 的 `BTCUSDT` 解析（C-4）。

**E.3 消歧规则（按优先级）：** ① 交易对上下文约束（quote 侧已知时排除冲突项）→ ② 合约地址精确匹配（`include_platform=true` 数据）→ ③ 市值排名最高（预缓存 /coins/markets 前 1000）→ ④ 仍歧义时 UI 列出候选（名称 + 市值 + 图标）由用户选择，结果写入 exchange_coin_map 固化。

**E.4 目录初始化与刷新成本：** 首次启动 `/coins/list` 一次调用（全量）；每日刷新一次；消歧辅助预取 /coins/markets 前 1000 名（4 页 4 次调用）。合计每月额度消耗 < 200 次，对 1 万次/月额度占比 < 2%，可忽略。

**E.5 输入归一化复用：** 增资/撤资币种输入、手续费币种、9.7 交易对自动补全，全部基于 coins 目录做大小写/别名归一（V3 T9 的 "usdt"/"USDT" 分裂问题一并解决）。

**E.6 交易所专有资产策略：** BTCB、BETH 等映射到其自身的 CoinGecko 资产（不与 BTC/ETH 静默合并）；交易所有、CoinGecko 无收录的资产标记“无行情”，按 T9 的无行情估值规则处理（不计入市值或计入 0，显示“无行情”）。

## F. 对 PRD 的修订清单（待决策后落地）

1. **全局说明**新增“币种标识与主数据规则”小节：内部标识 = CoinGecko id；消歧规则四级；映射冻结原则；兜底切换触发条件/折算链/UI 标注/自动回落；24h 同源原则。
2. **第 10 章**新增 coins、exchange_coin_map 两表；price_snapshots 升级（symbol→内部 id、新增 source）；transactions/capital_flows 增加币种 id 冻结字段。
3. **故事 2.3 / 4.1 验收**补充：导入遇歧义 ticker 时弹出消歧选择（候选列表含名称/市值/图标）；Binance 兜底期间的价格源标注。
4. **9.7 交易对自动补全、增资/撤资与手续费币种输入**改为基于 coins 目录（归一化 + 自动补全候选）。
5. **评审联动**：V3 的 T9 关闭主体；T2 的“兜底切换规则”部分闭环；T14（交易所清单与凭证模型）建议与本文 E 节合并落地。

## G. 决策项

| # | 问题 | 选项 | 建议 |
| --- | --- | --- | --- |
| D7 | 行情源策略 | 方案一：单一主源 + 故障兜底（推荐）/ 方案二：按币种混合 / 方案三：CG + CMC 双备 | 方案一 |
| D7a | 内部唯一标识 | 直接采用 CoinGecko id（推荐）/ 自造内部 ID | 用 CG id |
| D7b | CoinMarketCap 角色 | MVP 不集成，仅在关于页保留“备选数据源”说明（推荐）/ 集成为第三源 | 不集成 |
| D7c | 歧义 ticker 处理 | 消歧 UI + 映射固化（推荐）/ 一律自动取市值最高 | 消歧 UI（映射错误直接污染成本与盈亏，值得一次人工确认） |

决策后按 F 节清单修订《桌面端prd.md》并在版本历史登记（V1.5），同步更新 V3 报告 T9 状态。

---

## H. 决策记录（2026-08-26）

| # | 决策 | 落地情况 |
| --- | --- | --- |
| D7 | **方案一：单一主源（CoinGecko）+ 故障兜底（Binance 公共 K 线）** | 全局说明“行情数据与时间分辨率规则”新增兜底切换细则（触发条件、Binance 价 × CoinGecko USDT/法币折算链、UI“数据源：Binance”标注、主源恢复自动回落）与 24h 盈亏同源原则；故事 3.2/9.3 同步 |
| D7a | **内部唯一标识直接采用 CoinGecko id** | 全局说明新增“币种标识与主数据规则”一节；第 10 章新增 coins（目录缓存，每日 /coins/list 刷新）与 exchange_coin_map（含唯一约束与 AUTO/MANUAL 来源）两表；transactions/capital_flows/reconciliation_records 新增 coin_id 冻结字段，price_snapshots 升级（coin_id + price_source） |
| D7b | **CoinMarketCap 不集成，关于页保留备选说明** | 行情规则与故事 3.2 描述改为“主源 CoinGecko、兜底 Binance”；Out of Scope 新增 CMC 条目；6.4 关于页增加行情数据源说明 |
| D7c | **歧义 ticker 采用消歧 UI + 映射固化** | 全局说明四级消歧规则（上下文 -> 合约地址 -> 市值 -> 用户选择）；故事 2.3 验收 7、4.1 验收 7、9.7/9.8 表单接入目录归一 |

> 附带修复：9.7 交易对自动补全示例 “BTC/BUSD” 改为 “BTC/USDC”（V3 T11 的 BUSD 残留）；price_snapshots 落地 price_source（三大决策分析 B.4-6 遗留）。V3 报告 T9 关闭、T2 兜底部分闭环、T11 部分修复。移动端同步并入 T3 跨端对齐清单。

---

## I. 修订记录（2026-08-26，V1.6：D7 兜底方案修订）

D7 落地当日，产品负责人对兜底源提出新方向（“即使需要为 CoinGecko 的 api 兜底，也优先采用 CMC 的免费档 api”），经再调研后修订如下。**D7a（内部标识 = CG id）与 D7c（消歧 UI）不变**：

| 项 | 原决策（D7，V1.5） | 修订后（V1.6） |
| --- | --- | --- |
| D7 兜底源 | Binance 公共 K 线（K线 × CG 的 USDT/法币折算链） | **CoinMarketCap 免费档**（/cryptocurrency/quotes/latest，convert 直出多法币，无链式换算） |
| Binance 行情角色 | 兜底与校验源 | **完全退出**（仅保留交易所交易数据同步职能；“校验源”概念一并删除） |
| CMC 角色 | 不集成（关于页备选说明） | **兜底源**（仅当前价；历史数据仍不做兜底，回填失败走“待定价”延后重试） |

**修订理由（调研结论）：**

1. **Binance 兜底的折算链在 CG 故障时断裂**：兜底价 = K线(币/USDT) × CG 的 USDT/基础法币报价，法币腿仍依赖 CG--CG 网络故障时兜底不可用，恰在最需要时失效。
2. **CMC 是更完整的兜底形态**：quotes/latest 支持 90+ 法币（含 CNY）直出报价、单请求批量多币种；CG 状态页存在真实事件史（status.coingecko.com/incidents），无 Key 公共 API 按 IP 共享限流（NAT/共享出口会遭遇非自身原因的 429），兜底有真实保险价值。
3. **Key 管理**：CMC 必须携带 API Key（CG 无 Key 是其核心优势）。开源应用内置共享 Key 存在集体限流（CG 故障时全体用户同时挤兑同一 Key）与 Key 滥用风险，与本产品安全姿态不符，故兜底为**用户可选配置自己的免费 CMC Key**，未配置时降级为“保持上次价格 + 显示上次成功时间戳”。

**同步落地（V1.6）**：行情刷新频率按 CG 免费档额度调整（5/15/30 分钟，默认 5，移除 1 分钟档）；托盘驻留期间行情刷新统一按 API 同步间隔降频（消除桌面 PRD 6.1 与 9.2 表述冲突）；新增额度治理（月度计数、80% 自动降档、429 指数退避）；coins 表新增 cmc_id 列（/cryptocurrency/map 每日缓存）；名词解释新增“交易数据同步”“行情数据刷新”两术语（厘清用户指出的概念混淆：交易所 API 同步的是交易数据，行情刷新调用的是行情平台 API）。V3 报告 T2 关闭。

**后续修订（同日，随 V1.7/V1.8 落地）：Key 政策统一与两级模式。** 应产品负责人方向（“CoinGecko 和 CMC 都不提供公共内置 Key，设置中需填写自己的 Key 才能使用”），V1.7 先统一为“两个行情平台均不使用内置共享 Key、由用户在设置中配置个人 Key（CoinGecko 必填、CoinMarketCap 可选启用兜底）”；随后产品负责人采纳“CG 无 Key 公共 API 作为初始内置”的建议，V1.8 优化为**两级模式**：**未配置 Key 时默认使用 CoinGecko 无 Key 公共 API（开箱即用，按 IP 共享限流约 30 次/分钟，NAT/共享出口可能遭遇非自身 429）；填写个人 Key（免费 Demo 计划注册）后自动切换为专属额度（约 30 次/分钟、1 万次/月）并启用月度额度治理，切换即时生效。** 应用不内置任何 API Key（Keyless 公共 API 无需 Key，与“不内置共享 Key”原则不冲突）；CoinMarketCap 无 Keyless 模式，兜底仍需配置个人 Key。上文 I 节理由 3 中“CG 无 Key 是其核心优势”的表述由此恢复成立（Keyless 模式即该优势的默认形态，个人 Key 为额度升级路径）。

---

## 参考来源

- [CoinGecko /coins/list 官方文档](https://docs.coingecko.com/reference/coins-list)
- [CoinGecko 官方支持：能否保留或使用其他代币的 symbol](https://support.coingecko.com/hc/en-us/articles/4498962550681-Can-I-reserve-a-token-symbol-or-use-another-token-s-symbol)
- [CoinGecko 免费公共（Demo）API 说明](https://docs.coingecko.com/docs/keyless-public-api)
- [CoinMarketCap 官方：为什么应该用 ID 而非 symbol](https://coinmarketcap.com/api/resources/why-you-should-use-coinmarketcap-ids-instead-of-symbols/)
- [CoinMarketCap Cryptocurrency API 文档](https://coinmarketcap.com/api/documentation/pro-api-reference/cryptocurrency)
- [Binance exchangeInfo 官方文档](https://developers.binance.com/legacy-docs/alpha/market-data/rest-api/get-exchange-info)
- [BittyTax issue #34：symbol 在 CoinGecko API 中不唯一](https://github.com/BittyTax/BittyTax/issues/34)
- [BittyTax issue #238：CoinGecko symbol 匹配反直觉](https://github.com/BittyTax/BittyTax/issues/238)
- [BittyTax 维护的 Binance 资产映射脚本](https://github.com/BittyTax/BittyTax/blob/ac41faf45ce0c9fb565e033c43665cd93b4c9558/src/bittytax/conv/parsers/scripts/binance_assets.py)

> 说明：平台能力与额度以三大决策分析 B.1 的调研为基准；本文引用的官方文档链接为 2026-08-26 检索所得，开发期需以最新文档复核（尤其 CMC 的 v2 端点与免费档口径）。
