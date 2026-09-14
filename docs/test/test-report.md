# WuZhuFolio P6 系统测试报告（docs/test/test-report.md）

> **阶段**：P6 系统测试与质量（`AGENTS.md §4 P6`）· 启动指令：人工「执行P6」（2026-09-14）
> **测试对象**：P5 集成版（P0–P5 全部关闭）+ P6 修复项
> **有效需求基线**：**PRD V2.0 + Δ{D21, D24, D25, D26, D27, D28, D29}**（`docs/dev/增量台账.md`）
> **输入**：`docs/prd/桌面端prd.md`（V2.0）· `docs/design/interaction.md`（异常态清单）·
> `docs/test/security-checklist.md`（M13 T13.1 清单）· `docs/test/integration-report.md`（P5 交接开放项）
> **产物**：本报告 + `test-plan.md` + `test-cases.md` + `security-checklist.md`（P6 复跑版）+ `defects.md`
> **状态**：**待人工审核**（停在人工门：人工测试 + 拍板是否达到发布标准）

---

## 0. 结论摘要（DoD 对照）

| DoD | 结论 | 依据 |
|-----|------|------|
| **P0/P1 缺陷清零** | ✅ **达成**（P0 = 0，P1 = 0） | `defects.md` §0/§1/§2：本轮发现并修复 3 项 P2 实现偏差（DEF-01/02/06）；P2 待决 3 项（DEF-03/04/05）均不阻断发布，交人工定级 |
| **P2 有明确处理结论** | ✅ **达成**（6 项：3 项本轮修复 + 3 项待人工定级/登记，均带影响面与到期点） | `defects.md`；本报告 §6 待裁决清单 |
| **`security-checklist.md` 全部通过** | ✅ **达成**（五条硬约束逐条复跑打勾） | `security-checklist.md` §0：5/5 ✅；新增 4 项到期实证（抓包、大载荷内存曲线、键命名空间、会话门）全部闭环，2 项转人工门/P7 |
| 全量回归绿 | ✅ **达成** | `./gradlew clean build detekt --no-build-cache` → **677 用例（669 执行 0 失败 0 错误 + 8 跳过）** + detekt 0 + 编译警告 0（33 任务全部真正执行） |

**一句话结论**：P6 全量验证完成，**无 P0/P1 缺陷**，安全与隐私五条硬约束全部通过；
建议**具备进入 P7 发布准备的条件**——前提是人工门完成下述 5 项裁决与不可替代的人工用例（托盘/读屏/真实 Key/目标机）。

---

## 1. 执行情况

### 1.1 全量自动化（P6 终态）

```
./gradlew clean build detekt --no-build-cache
→ BUILD SUCCESSFUL · 33 actionable tasks: 33 executed
→ 677 用例 = 669 执行 0 失败 0 错误 + 8 跳过 · detekt 0 issue · 编译警告 0
```

| 模块 | 用例数 | 跳过 | 失败 | 说明 |
|------|--------|------|------|------|
| domain | 222 | 0 | 0 | 纯领域（引擎/加密/市场规则/备份编解码/设置文案） |
| data | 284 | 7 | 0 | 存储/迁移/仓库/用例实现/守护测试；跳过 = 4 钥匙串真实后端（CI win/mac 实证）+ 3 live smoke（env 门控） |
| ui | 140 | 0 | 0 | Compose 离屏 UI 测试（页面/弹窗/聚焦/i18n/对比度） |
| app | 31 | 1 | 0 | 组合根集成（真实库 + 回环 HTTP）；跳过 = 首启真实网络链路（env 门控） |
| **合计** | **677** | **8** | **0** | 基线（P5 关闭时）为 664 用例；**P6 净增 13 项** |

**P6 新增回归（13 项）**：

| 类别 | 测试 | 数量 |
|------|------|------|
| 备份导出失败模式（DEF-01/06） | `DefaultBackupServiceTest`（坏 key / 仅 passphrase 坏 / 全量覆盖不清库）+ `ui/BackupExportErrorCopyTest` | 5 |
| 大载荷正确性 | `domain/backup/CproLargePayloadTest`（2 万交易 + 6 万快照无损往返） | 1 |
| settings 键命名空间 | `data/settings/SettingsKeyNamespaceGuardTest`（互斥断言 + 登记表双向校验） | 2 |
| 登出调度会话门 | `data/schedule/BackgroundSchedulerTest`（无会话短路 + 有会话正例） | 2 |
| 表单校验边界（V1/V2/V3） | `ui/ledger/TransactionsPageUiTest`（0/负值/自定义手续费币种） | 2 |
| Binance 契约（DEF-02） | `BinanceAdapterTest`（`recvWindow` 断言，并入既有用例） | 0（断言扩展） |
| 出站抓包路由 | `data/smoke/ProxyRoutingSmokeTest`（env 门控） | 1 |
| **合计** | | **13** |

### 1.2 运行期实证

| 实证 | 命令 | 结果 |
|------|------|------|
| GUI 冒烟（真实进程） | `WUZHUFOLIO_DATA_DIR=/tmp/wzf-p6-gui2 JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE_FAST" timeout 60 ./gradlew --no-daemon :app:run` | `GRADLE_RC=124`（窗口驻留至超时）· `bootstrap ok | schema=12` · `scheduler started | syncInterval=30min` · `tray support supported=false`（WSLg 预期）· `market refresh finished source=COINGECKO coins=1 error=null` · 0 渲染异常 |
| 权限实证 | `stat -c '%a %n' <数据目录> …` | 数据目录/日志 **700**；`master.key`/`device.key`/`.db` **600** |
| 出站抓包 | `scripts/outbound-capture-proxy.py` + 真实进程/真实客户端 | 仅 `api.coingecko.com` / `api.binance.com` / `pro-api.coinmarketcap.com` 三主机（见 §5.2） |
| 真实网络冒烟 | `WZF_LIVE_SMOKE=1 … :data:test --tests "…smoke.*"` | CG 报价+目录、Binance 公开端点、CMC 兜底端点探测**全部经代理成功/被拒**（无第四方主机） |
| `.cpro` 内存曲线 | `./gradlew :domain:backupBenchmark`（1g / 512m / 2g） | 见 `security-checklist.md` §3.7 |
| 429 真实路径（顺带实证） | 抓包轮中 CG 匿名档触发 `RateLimited` | 应用保持上次数据、日志记录 `cause=` 链（P5-2 修复有效）、不崩不卡 —— B4/N1 路径真实可用 |

> **说明**：CG 匿名公共档在连续测试中出现 429 属预期（共享限流），这正是 live smoke 采用 env 门控、不进默认回归的原因（`integration-report.md` 既有口径）。

---

## 2. 覆盖结论（299 条用例）

`docs/test/test-cases.md` 逐条挂 PRD/interaction/附录 A/硬约束，共 **299 条**：

| 状态 | 数量 | 占比 | 含义 |
|------|------|------|------|
| ✅ 自动化已覆盖 | 186 | 62.2% | 有可指向的 `file::testFun`（数值/状态/文案/边界） |
| 🟡 部分覆盖 | 87 | 29.1% | 服务层/领域层已自动化，**UI 展示层未断言**（多数为视觉/裁切/滚动类，由人工门走查覆盖） |
| ⬜ 未覆盖 | 10 | 3.3% | 均有去向：3 项 = 缺陷 DEF-03/04/05；其余 6 项登记 P8（DB 损坏全屏框演练、ROI=0 提示展示、行情行内 loading、备份/恢复进度条等）；1 项由人工门覆盖 |
| 🔵 人工门用例 | 16 | 5.4% | 托盘/自启/读屏/真实 Key/目标机 KDF/全流程走查（Agent 不可替代） |

**分组覆盖**：§1 用户故事 109（74/33/2）· §2 §7.2 功能 60（37/21/1/1）· §3 异常态 21（12/8/1）·
§4 加载·空·错误·离线·限流 71（37/25/6/3）· §5 附录 A 黄金用例 12（**12/12 ✅**）· §6 安全专项 16（14 + 2 人工）· §7 人工门 10。

**结论**：
1. **功能验收无缺口**：PRD §5/§7.2 的验收标准全部有用例；未覆盖项均为**展示层增强或已登记缺陷**，不影响功能正确性判定。
2. **计算口径 100% 自动化**：黄金用例 1–12（含 D26/D27/D28/D29 修订后口径）全部有断言，是本次发布最重要的正确性证据。
3. **🟡 集中在 UI 展示层**：本机 Compose 离屏测试可断言语义/文案/状态，但**像素级视觉**（裁切、滚动、间距）不可靠，
   按 `test-plan.md §2.2` 的口径交由人工门走查，不作为机器覆盖缺口。

---

## 3. 缺陷汇总

| 级别 | 数量 | 明细 |
|------|------|------|
| P0 | **0** | — |
| P1 | **0** | — |
| P2 | 6 | ✅ 已修复 3：**DEF-01**（备份导出不可解密凭证 → 类型化 + 双语 + 恢复不清库）、**DEF-02**（Binance `recvWindow` 契约漂移）、**DEF-06**（恢复向导未映射导出错误）<br>⏳ 待人工定级 3：**DEF-03**（币种详情缺时间筛选，建议 C1）、**DEF-04**（CMC 兜底计入 CG 额度账本，建议 C1）、**DEF-05**（interaction「列表滚动加载」口径，建议 C0 文档澄清） |
| P3/观察 | 5 | DEF-07…DEF-11（导出静默跳过计数、备份元数据读失败提示、调度 `onTick` 异常隔离、i18n 兜底路径、`keyName=null` 分支用例）→ 登记 P8 |

**缺陷→回归→验证**：DEF-01/02/06 的回归测试已进全量套件（§1.1），复跑命令见 `defects.md §4`。
**本轮无「先码后补文档」**：三项修复均同步回写 `interaction.md §2.9`、`api-contracts.md §3/§4`。

---

## 4. 安全与隐私专项结论

`docs/test/security-checklist.md`（P6 复跑版）五条硬约束**全部通过**：

| 硬约束 | 结论 | P6 关键新证据 |
|--------|------|----------------|
| 1 数据本地化 | ✅ | 运行期抓包仅 3 白名单主机；权限 700/600 实测；结构守护复跑绿 |
| 2 零遥测 | ✅ | 依赖面/日志 appender 守护 + 抓包反证（无第四方主机） |
| 3 密钥与加密 | ✅ | 导出失败模式类型化（DEF-01）；`.cpro` 大载荷内存曲线实测；脱敏漏斗实测 `****` |
| 4 两类独立 API | ✅ | `ProxyRoutingSmokeTest`：**两类客户端都真的走代理**（PRD 4.2-2 端到端首次实证）；实例隔离守护绿 |
| 5 账户隔离 + `.cpro` | ✅ | settings 键命名空间 fail-closed 守护（注入冲突验证会红）；跨账户恢复不清库断言；大载荷无损往返 |

**到期项闭环**：M13 登记的 6 项中，**4 项本轮闭环**（`.cpro` 非流式内存曲线、settings 键命名空间、
登出后调度 tick 噪声、P5-4 导出失败模式），**2 项转人工门/P7**（读屏实测、签名公证），
**1 项待人工定级**（行情请求币种集合隐私最小化，见 §6）。

---

## 5. P6 专项结论

### 5.1 `.cpro` 非流式内存曲线（结论：维持现状，流式化留 P8 评估）

| 规模 | 记录 | 文件 | 解码峰值堆 | 1 GB 堆（≈4GB 目标机默认） | 512 MiB 堆 |
|------|------|------|-----------|---------------------------|-----------|
| 轻量 | 6.2k | 1.1 MiB | 100 MiB | ✅ | ✅ |
| 典型 | 35k | 5.9 MiB | 142 MiB | ✅ | ✅ |
| 重度 | 201k | 40.8 MiB | 483 MiB | ✅ | ❌ OOM |
| 压力 | 706k | 152 MiB | 1,424 MiB | ❌ OOM（2 GB 下 ✅） | ❌ |

结论：PRD 实际规模（典型～重度）**余量充足**；非流式实现**维持在 v1**。
改为分块/流式须改动 GCM 认证与文件格式 → **C2**，仅当用户数据量逼近「10× 典型」时由 P8 立项（已在 `security-checklist.md §7-1` 登记结论文）。

### 5.2 运行期出站面（结论：收敛）

抓包结果（应用真实运行 + 真实客户端测试）：

| 主机 | 次数 | 来源 |
|------|------|------|
| `api.coingecko.com` | 3 | 应用启动即刷新 + live smoke 报价/目录 |
| `api.binance.com` | 2 | 公开端点 `/api/v3/exchangeInfo`（无 Key） |
| `pro-api.coinmarketcap.com` | 1 | 兜底端点探测（占位 Key → 被拒 401） |

**无第四方主机**；两类 API 的客户端均实证经代理出站。

### 5.3 行情请求隐私最小化（评估完成，待人工定级）

- 现状外发 = **币种 id 集合**（持仓 ∪ 自选）+ 计价法币；**不含**金额/数量/交易/流水/密钥/账户身份；
- 可推断面：持仓/自选集合、集合变动时机、基础法币/地区、5–30 分钟粒度的在线时间线；交易所侧 `symbol=` 增量隐私损失 ≈ 0（用户自有 Key 签名）；
- **量化结论**：最小化改造（如公共批量页 + 域外追加）**边际额度成本 ≈ 0**（额度按请求计，与 id 数无关）；K≥2 批量在 5 分钟档会超月限；
- **不违反 `AGENTS.md §1.1`**，属低-中危可消除面；处置方式见 §6 裁决项 ①。

### 5.4 其它闭环项

| 项 | 结论 |
|----|------|
| settings 键命名空间 | ✅ 新守护测试（fail-closed 扫描 + 登记表 + `全局 ∩ 账户级 = ∅`）；已用注入冲突验证会红 |
| 登出后调度 tick | ✅ 无活动会话时同步 tick 短路（不再制造被吞的 `IllegalStateException`）；行情/维护循环不受影响 |
| 备份导出失败模式 | ✅ 类型化 + 双语可读文案 + **恢复失败不清库**（数据完整性断言） |
| Binance 契约漂移 | ✅ 签名请求补发 `recvWindow`（与 ADR-004 §2 逐字对齐） |
| 表单校验边界 | ✅ 价格/数量/手续费 0 与负值、自定义手续费币种必填 → 逐字段红字且不触达服务 |

---

## 6. 待人工裁决清单（P6 门）

> 以下 5 项按 `AGENTS.md §8.4` 由人工拍板；Agent 未自行定级，未实施（除已按 C0 处理的 DEF-01/02/06，请一并确认定级）。

| # | 事项 | 建议 | 备选 |
|---|------|------|------|
| ① | **行情请求币种集合隐私最小化**是否实施 | **接受现状 + 记入用户指南/隐私声明**（不违反硬约束，零成本消除不可得） | A. 增加「隐私模式」开关（固定集合取价，**缺省关**）→ 建议 **C1**（决策档 + 台账 + 下游回写）<br>B. 直接改默认取价路径 → 建议 **C2**（改已通过模块 M5 行为；需评估覆盖/带宽） |
| ② | **DEF-03** 币种详情交易记录「时间」筛选（PRD 3.4-4） | **本轮补做**（C1 mini 闭环，纯 UI 增量） | 登记 P8 迭代（写入复盘输入） |
| ③ | **DEF-04** CMC 兜底调用计入 CG 额度账本 | **登记 P8**（影响面小：仅同时配置两 Key 时可能提前降档） | 本轮按 C1 修正（账本增加 provider 维度 + 旧载荷兼容） |
| ④ | **DEF-05** interaction §2.1「列表滚动加载」口径 | **按 C0 文档澄清**（本地库单次装载 + LazyColumn 虚拟化，分页/滚动加载不适用；大数据量装载列为 P8 观察项） | 实现分页（不推荐：本地库无收益，且引入复杂度） |
| ⑤ | **DEF-01 / DEF-02 / DEF-06 的定级确认** | 建议维持 **C0**（实现偏差/失败模式补全，不改产品语义、不改数据模型与格式；已回写 `interaction.md`/`api-contracts.md`） | 若认为属 C1：需补决策档（D30）+ 台账登记 |

**另需人工执行的用例**（`test-cases.md §7`）：真实桌面托盘走查、读屏 NVDA/JAWS、真实 Binance 只读 Key 线上同步冒烟、
4GB 目标机 KDF ≤2s、全流程 GUI 走查、跨设备备份演练、发布标准拍板。

---

## 7. 残留风险与去向

| 风险 | 级别 | 去向 / 到期点 |
|------|------|----------------|
| UI 展示层 87 条 🟡（视觉/裁切/滚动类）未机器断言 | 低 | 人工门走查（`test-cases.md §7`） |
| 真实只读 Key 的线上同步未覆盖 | 中 | 人工门（提供 Key 即可随时验）；回环 HTTP 已覆盖同步/校准全链路 |
| 托盘/自启/读屏 环境相关项 | 中 | 人工门 + P7 打包版 |
| 目标机 KDF 未实测 | 低 | 人工门；超标预案在册（`KdfParams.OWASP_MINIMUM`） |
| CI 三平台复跑待推送 | 低 | 本门通过后推送观察（含 P6 新增 13 项；win/mac 钥匙串用例可逐项核验） |
| 隐私最小化未实施（若人工选 A/B） | 低-中 | 按裁决走 C1/C2 闭环 |
| CG 匿名档共享限流 | 低 | 已按 PRD 文案提示注册个人 Key；live smoke 门控不进默认回归 |

---

## 8. 复跑命令汇总

```bash
export JAVA_HOME=$(mise where java)

# 全量（应 677 用例 = 669 执行 0 失败 + 8 跳过、detekt 0、警告 0）
./gradlew clean build detekt --no-build-cache

# 缺陷修复聚焦（DEF-01/02/06）
./gradlew :data:test --tests "com.wuzhufolio.data.backup.*" \
                     --tests "com.wuzhufolio.data.exchange.BinanceAdapterTest" \
                     --tests "com.wuzhufolio.data.settings.SettingsKeyNamespaceGuardTest" \
                     --tests "com.wuzhufolio.data.schedule.*"
./gradlew :ui:test   --tests "com.wuzhufolio.ui.backup.*" --tests "com.wuzhufolio.ui.ledger.*"

# 安全清单复跑（详见 security-checklist.md §8）
./gradlew :data:test --tests "com.wuzhufolio.data.security.*"
./gradlew :domain:backupBenchmark            # .cpro 内存曲线

# 真实网络 + 出站抓包（env 门控）
python3 scripts/outbound-capture-proxy.py --port 8899 --log /tmp/wzf-outbound.log &
WZF_LIVE_SMOKE=1 https_proxy=http://127.0.0.1:8899 ./gradlew --no-daemon \
  :data:test --tests "com.wuzhufolio.data.smoke.*" --rerun-tasks

# GUI 冒烟
WUZHUFOLIO_DATA_DIR=/tmp/wzf-p6-gui JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE_FAST" \
  timeout 60 ./gradlew --no-daemon :app:run          # 期望 GRADLE_RC=124
```

---

## 9. 需求回溯

| 本报告结论 | 需求锚点 |
|------------|----------|
| 功能验收覆盖（299 条用例） | PRD §5 故事 1.1–7.2、§7.2 模块 1–9、§9 交互细节 |
| 异常/离线/限流/加载/空态 | `docs/design/interaction.md` §1.1–1.4、§2.1–2.9 |
| 计算口径 | PRD 附录 A 黄金用例 1–12、D26/D27/D28/D29 |
| 安全与隐私 | `AGENTS.md §1.1` 五条硬约束、PRD §1.1/§5.1/§5.2/§6、共享规范 §8 |
| 缺陷处置 | P5 交接项（`integration-report.md` §6/§8）、M13 清单 §7、P6 勘查发现 |
| DoD | `AGENTS.md §4 P6`（P0/P1 清零、P2 有结论、安全清单通过） |
