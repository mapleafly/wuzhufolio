# WuZhuFolio P6 系统测试计划（docs/test/test-plan.md）

> **阶段**：P6 系统测试与质量（`AGENTS.md §4 P6`）· 启动指令：人工「执行P6」（2026-09-14）
> **输入**：P5 集成版（`P0–P5 全部关闭`）+ PRD **V2.0** 验收标准 + `docs/design/interaction.md` 异常态清单
> + `docs/test/security-checklist.md`（M13 · T13.1）+ `docs/test/integration-report.md`（P5 交接开放项）
> **有效需求基线**：**PRD V2.0 + Δ{D21, D24, D25, D26, D27, D28, D29, D30, D31, D32, D33}**（`docs/dev/增量台账.md`）
> **目标**：按 PRD 验收标准做**全量验证**，重点压安全与隐私；产出 `test-plan / test-cases / security-checklist（复跑）/
> defects / test-report`；DoD = **P0/P1 缺陷清零、P2 有明确处理结论、`security-checklist.md` 全部通过**。
> **状态**：执行中 → 完成后停在人工门（人工测试 + 拍板是否达到发布标准）。

---

## 1. 范围

### 1.1 在范围内

| 维度 | 内容 | 主要锚点 |
|------|------|----------|
| 功能验收 | PRD §5 史诗故事 1.1–7.2 的**每一条验收标准** | `test-cases.md` §1 |
| 功能模块 | PRD §7.2 仪表盘/资产/交易/API 同步/数据管理/设置/账户/资金/桌面体验 | `test-cases.md` §2 |
| 异常态 | interaction §1.1 网络异常 N1–N3、§1.2 后台服务 B1–B5、§1.3 账户 A1–A4、§1.4 校验 V1–V9 | `test-cases.md` §3 |
| 非功能态 | interaction §2.1 加载 / §2.2 空 / §2.3 错误降级 / §2.4 离线 / §2.5 429 限流 / §2.6 日志诊断 / §2.7 行情页 / §2.8 持仓异常 | `test-cases.md` §4 |
| 计算口径 | PRD 附录 A 黄金用例 1–12（含 D26/D27/D28/D29 修订后的口径） | `test-cases.md` §5 |
| 安全与隐私 | `AGENTS.md §1.1` 五条硬约束 + M13 清单逐条复跑 + P6 新增抓包/大载荷/命名空间/会话噪声项 | `security-checklist.md`（P6 版） |
| 决策增量 | Δ{D21, D24, D25, D26, D27, D28, D29, D30, D31, D32, D33} 的已实现口径回归（含 D24 备份密码独立设置） | `test-cases.md` §1/§2/§5 |

### 1.2 不在范围内（及理由）

| 项 | 理由 | 去向 |
|----|------|------|
| 移动端（Android） | `AGENTS.md §1` 范围决策：P1–P8 只做桌面端 | 下一版本 |
| 真实交易所**写**操作 | 产品只读定位 + 无凭据（Agent 不索取密钥） | 永不做（PRD Out of Scope） |
| 真实交易所只读 Key 线上同步冒烟 | 需人工提供只读 Key（Agent 无凭据） | **人工门**（`test-cases.md` §7） |
| 三平台托盘 GUI / 打包版开机自启 | 本机 WSLg 无系统托盘；自启仅打包版生效（M11 §5-2/§5-3 延期在册） | **人工门**（真实桌面/打包版）；自启端到端 → P7 |
| 读屏 NVDA/JAWS 实测 | 需 Windows 桌面 + 读屏软件（ADR-001 既有风险） | **人工门**（`test-cases.md` §7） |
| 4GB 双核目标机 KDF ≤2s | 无可得目标机（M1/M2/M13 在册开放待办） | **人工门** + P7 发布前复核 |
| 签名/公证合规 | 需证书采购（成本/周期） | P7（ADR-006 §2.1） |
| 性能压力测试（长跑/大数据量 GUI 帧率） | MVP 无指标要求；仅对大载荷内存曲线做定点实测 | 定点实测（§6-2）+ P8 视情况 |

---

## 2. 测试策略

### 2.1 分层取证（沿用 P5 分层，向「验收标准可追溯」加一层）

| 层 | 问题 | 手段 | 产物/证据 |
|----|------|------|-----------|
| **L1 单元/模块** | 规则与边界是否正确 | JUnit5 + kotlin.test（domain/data/ui/app 四模块） | 各模块 `build/test-results/test/*.xml` |
| **L2 真实组合根集成** | 真实装配顺序下是否跑通 | `app/src/test/.../integration/*`（真实 `AppBootstrap.run` + 真实 SQLCipher 库 + 真实 `.cpro`） | `integration-report.md` + P6 复跑 |
| **L3 真实网络/真实 HTTP** | 生产客户端契约是否漂移、出站面是否收敛 | `WZF_LIVE_SMOKE=1` 门控冒烟（CG 真实端点 / Binance 公开端点 / 代理路由）+ `scripts/outbound-capture-proxy.py` 抓包 | 抓包日志 + 冒烟输出 |
| **L4 进程级 GUI 冒烟** | 启动链、渲染、权限、日志是否健康 | `:app:run`（WSLg + `SOFTWARE_FAST`）+ 数据目录权限核对 | 日志 + `stat` 输出 |
| **L5 人工门** | 体验、托盘、读屏、真实 Key、目标机 | 人工按 `test-cases.md` §7 执行 | 人工结论 |

### 2.2 用例来源与追溯规则

1. **逐条对应**：`test-cases.md` 的 299 条用例逐条挂在 PRD §5/§7.2/§6、interaction §1/§2、附录 A 或 §1.1 硬约束上；
   每条标注 `需求锚点`，可反向核查「有没有哪条验收标准没落地」。
2. **覆盖度三态 + 人工门**：✅ 自动化已覆盖（给 `file::testFun`）/ 🟡 部分覆盖（说明缺哪一层）/ ⬜ 未覆盖（给去向：
   补测 / 人工门 / 登记 P8 / defects.md）/ 🔵 人工门用例。
3. **不为覆盖率而造数**：UI 展示层断言的必要性以「该展示是否承载验收语义」为准——纯视觉细节由人工门覆盖，
   语义与数值一律自动化（数值断言用 `BigDecimal.compareTo`，不用展示串）。

### 2.3 安全与隐私专项（本阶段重心）

- **清单复跑**：M13 的 `security-checklist.md` 五条硬约束逐条**重新取证**（源码行号 + 测试 + 运行期实证），
  每条给出 P6 复跑结论；原有 6 项登记项逐条更新到期状态（闭环 / 转 P8 / 仍需人工）。
- **新增实证**（本阶段新增，均有可复跑命令）：
  1. **运行期出站抓包**：`scripts/outbound-capture-proxy.py` + 真实应用/真实客户端 → 主机集合必须 ⊆ 三主机白名单；
     同时证明**两类 API 的客户端都真的走代理**（PRD 故事 4.2-2）。
  2. **`.cpro` 大载荷内存曲线**（M13 §7-1 到期项）：`./gradlew :domain:backupBenchmark` 四档规模实测峰值堆，
     给出「是否需要在 P8 改流式/分块格式（C2）」的量化结论。
  3. **settings 键命名空间守护**（M13 §7-5 到期项）：fail-closed 源码扫描 + 登记表 + 全局/账户级键**互斥断言**。
  4. **登出后调度 tick 噪声**（M13 §7-6 到期项）：无活动会话时同步 tick 短路，不再制造被吞的 `IllegalStateException`。
  5. **备份导出失败模式**（P5-4）：不可解密 `api_keys` 密文 → 类型化错误 + 双语可读文案 + 恢复路径**不清库**断言。
- **反证式守护**：白名单/遥测/隔离/边界类断言以「结构守护测试」形式常驻（`SecurityGuardTest`、
  `ApiIsolationGuardTest`、`BackupBoundaryGuardTest`、`FilePermissionsTest`、`RedactingMessageConverterTest`、
  `LogbackRedactionFunnelTest`、`SettingsKeyNamespaceGuardTest`），任何新增出站/依赖/字段都会红。

### 2.4 缺陷处理规则

| 级别 | 定义 | P6 处置要求 |
|------|------|-------------|
| **P0** | 数据丢失/损坏、密钥或凭据泄露、无法启动、主流程阻断 | **必须修复**并回归；不得带出门 |
| **P1** | 主要功能不可用或结果错误、验收标准未满足且无替代路径 | **必须修复**并回归；不得带出门 |
| **P2** | 次要功能偏差、异常文案/引导不佳、体验问题、可诊断性不足 | 修复 或 **给出明确处理结论**（登记 P7/P8 + 到期检查点），并在 `defects.md` 记录 |
| **P3** | 文案/视觉细节 | 登记即可 |

- **分级与变更控制联动**：凡「修复」触及已通过模块的行为/数据/接口，先按 `AGENTS.md §8.1` 给出**分级建议 + 影响面扫描**，
  由人工在 P6 门拍板（Agent 不自行定级 C1/C2；纯实现偏差按 C0 处理并留痕）。
- **修复即留痕**：每个缺陷在 `defects.md` 记「现象 / 根因 / 处置 / 回归测试 / 分级建议」，并在 `test-report.md` 汇总。

---

## 3. 环境与工具

| 项 | 值 |
|----|----|
| 开发/测试环境 | WSL2 Ubuntu 24.04 + WSLg（无系统托盘、无 Secret Service） |
| JDK | temurin-17.0.20+101（`mise where java`） |
| 构建 | Gradle Wrapper 8.14.4（唯一真源）· Kotlin 2.4.10 · Compose Multiplatform 1.12.0 |
| 测试框架 | JUnit5 + kotlin.test（单元/集成）· Compose UI Test（离屏，v2 API）· detekt（静态检查，maxIssues=0） |
| 外部网络 | CoinGecko（匿名公共档）/ Binance（公开端点）**不使用任何 Key**；env 门控 `WZF_LIVE_SMOKE=1` |
| 抓包工具 | `scripts/outbound-capture-proxy.py`（本地 CONNECT/HTTP 记录代理，不解密 TLS） |
| 数据隔离 | `WUZHUFOLIO_DATA_DIR=/tmp/wzf-p6-*`（每轮独立加密库，不碰开发库） |
| 基准夹具 | `:domain:kdfBenchmark`（T1.3）· `:domain:backupBenchmark`（P6 新增） |

---

## 4. 进入 / 退出准则

**进入准则**（已满足）：P5 关闭且 P0–P5 全部通过；`security-checklist.md` 可用；集成版可构建、可运行。

**退出准则（DoD）**：

1. 全量无缓存构建绿：`./gradlew clean build detekt --no-build-cache` → 0 失败、detekt 0、编译警告 0；
2. `security-checklist.md`（P6 版）**五条硬约束逐条打勾通过**；
3. **P0/P1 缺陷清零**；P2 缺陷/问题**每条有明确处理结论**（修复或登记 + 到期检查点 + 责任人 = 人工门拍板）；
4. `test-cases.md` 每条用例有状态（✅/🟡/⬜/🔵）；⬜ 项均有去向，无悬空项；
5. `test-report.md` 给出**是否达到发布标准**的建议结论 + 残留风险清单；
6. 停在人工门：人工按 `test-cases.md` §7 执行人工用例后拍板。

---

## 5. 覆盖矩阵（用例维度）

> 详细用例见 `docs/test/test-cases.md`（299 条）。下表是**分组统计与追踪入口**。

| 分组 | 用例数 | ✅ 自动化 | 🟡 部分 | ⬜ 未覆盖 | 🔵 人工门 | 主要追踪锚点 |
|------|--------|-----------|---------|-----------|-----------|--------------|
| §1 PRD §5 用户故事（1.1–7.2） | 109 | 74 | 33 | 2 | 0 | PRD §5 每条验收标准 |
| §2 PRD §7.2 核心功能模块 | 60 | 37 | 21 | 1 | 1 | PRD §7.2 模块 1–9 |
| §3 interaction §1 异常态（N/B/A/V） | 21 | 12 | 8 | 1 | 0 | interaction §1.1–1.4 |
| §4 interaction §2/§3 + PRD §6 UX | 71 | 37 | 25 | 6 | 3 | interaction §2.1–2.8、§6 |
| §5 附录 A 黄金用例 1–12 | 12 | 12 | 0 | 0 | 0 | PRD 附录 A |
| §6 安全与隐私专项 | 16 | 14 | 0 | 0 | 2 | `AGENTS.md §1.1` 五条 |
| §7 人工门用例 | 10 | — | — | — | 10 | 见 §7 下表 |
| **合计** | **299** | **186** | **87** | **10** | **16** | — |

**⬜ 未覆盖 10 条的去向**（明细见 `test-cases.md` §8 与 `defects.md`）：

| 去向 | 条目 |
|------|------|
| 已修复/已登记缺陷 | DEF-03（币种详情缺时间筛选）、DEF-04（CMC 兜底计入 CG 额度账本）、DEF-05（列表滚动加载口径） |
| 登记 P8（实现增强，非缺陷） | DB 损坏全屏错误框 B1 的端到端演练、累计增资为 0 的 ROI 提示展示断言、行情刷新行内 loading、备份/恢复进度条 |
| 由人工门覆盖 | 见 §7（托盘菜单/读屏/真实 Key/目标机） |

**🟡 部分覆盖 87 条的口径**：绝大多数为「服务层/领域层已自动化，UI 展示层未断言」（例如校验文案存在但未逐条断言节点、
裁切/滚动等视觉行为）；这类由人工门走查覆盖，`test-cases.md` 逐条标明了缺哪一层。

---

## 6. P6 启动须携带的开放项 → 本阶段处置

| # | 开放项（来源） | 处置 | 结论落点 |
|---|----------------|------|----------|
| ① | M11 §5-3 托盘 GUI 走查（真实桌面） | 本机 WSLg 无系统托盘 → **人工门**；Agent 侧已把「关窗决策/通知开关」抽纯函数单测钉死 | `test-cases.md` §7 · `M11.md §5-3` |
| ② | `.cpro` 大载荷内存曲线（M13 §7-1） | ✅ **实测四档**（`domain:backupBenchmark`）：典型规模峰值 <150 MiB、重度 <500 MiB、1 GB 堆内可完成；压力档（70 万条/152 MB 文件）需 ~1.5 GB → **维持非流式，流式化留 P8 评估**（改格式 = C2） | `security-checklist.md` §7-1 · 本文件 §2.3 |
| ③ | 行情请求币种集合隐私最小化（清单 §7-2） | ✅ **评估完成**：载荷不含金额/交易/密钥，属行情 API 功能必需参数；给出 5 个候选方案与额度量化（最小化**边际额度成本≈0**）→ **交人工定级拍板**（Agent 不实施） | `test-report.md` §5 · 待人工裁决 |
| ④ | 读屏（NVDA/JAWS）实测（ADR-001 风险） | 需真实桌面 → **人工门**；自动化侧仅能守护语义标签存在性 | `test-cases.md` §7 |
| ⑤ | settings 键命名空间守护（清单 §7-5） | ✅ **已闭环**：新增 `SettingsKeyNamespaceGuardTest`（fail-closed 扫描 + 登记表 + 互斥断言，已用注入冲突验证会红） | `security-checklist.md` §7-5 |
| ⑤b | 登出后调度 tick 噪声（清单 §7-6） | ✅ **已闭环**：`SchedulerSources.hasActiveSession()` + `syncOnce()` 短路 + 装配注入会话持有器 + 2 项回归 | `security-checklist.md` §7-6 |
| ⑥ | 运行期出站抓包实证（清单 §8-5） | ✅ **已实证**：应用真实运行（代理指向本地记录代理）+ `ProxyRoutingSmokeTest`（两类客户端都走代理）→ 主机集合 ⊆ 白名单 | `security-checklist.md` §1/§8 |
| ⑦ | 真实交易所只读 Key 线上同步冒烟 | Agent 无凭据、不索取 → **人工门**（提供 Key 即可随时验） | `test-cases.md` §7 |
| ⑧ | M13 安全清单逐条复跑打勾 | ✅ **已完成**（见 `security-checklist.md` P6 版：每条带复跑结论与证据） | `security-checklist.md` |
| ⑨ | P5-4 备份导出失败模式 | ✅ **已修复**（类型化 `BackupExportException.CREDENTIAL_UNREADABLE` + 中英双档文案 + 导出/恢复两侧回归 + 恢复失败不清库断言） | `defects.md` DEF-01 |

---

## 7. 人工门用例（Agent 不可替代）

> 完整步骤与通过判据见 `test-cases.md` §7；此处为执行清单。

| # | 用例 | 环境要求 | 通过判据 |
|---|------|----------|----------|
| 1 | 真实桌面托盘走查（菜单三项/驻留/通知/关窗、降级口径） | Windows/macOS/Linux 真实桌面 | M11 §5-3 DoD |
| 2 | 打包版开机自启端到端 | P7 打包产物 | M11 §5-2 DoD（本轮顺延 P7） |
| 3 | 读屏 NVDA/JAWS 走查（关键数据可读、徽标语义、表格/表单） | Windows + 读屏软件 | PRD §6 无障碍基线 |
| 4 | 真实 Binance 只读 Key 线上同步冒烟（同步入账/增量幂等/校准） | 人工提供只读 Key | PRD 故事 4.1 验收 4/5 |
| 5 | 4GB 双核目标机 KDF ≤2s 复核 | 目标机 | 超标预案 = `KdfParams.OWASP_MINIMUM` |
| 6 | 全流程 GUI 走查（登录→增资→交易→看板→备份恢复→异常态→语言/双主题） | WSLg 或真实桌面 | `integration-report.md` §7 九步 |
| 7 | `.cpro` 跨设备/跨账户恢复演练（含错误路径：错密码/非 .cpro/低版本） | 两台设备或两个账户 | PRD 故事 5.2 |
| 8 | 出站抓包复核（按 §2.3 命令自行抓一次） | 本机可运行 + 代理 | 主机集合 ⊆ 三主机 |
| 9 | 权限与脱敏目视复核（数据目录 700 / 密钥 600 / 日志 `****`） | 本机 | M13 §3.4/§3.5 |
| 10 | 发布标准拍板（是否达到 P7 准入） | — | 本文件 §4 退出准则 |

---

## 8. 风险与限制

| 风险 | 影响 | 缓解 |
|------|------|------|
| 本机无真实桌面（WSLg） | 托盘/自启/读屏无法本机验证 | 已登记人工门；相关纯逻辑用单测钉死（M11 §5-3 口径） |
| 外部行情 API 波动 | 真实网络冒烟存在偶发失败 | env 门控不进默认回归；`cause` 链保留（P5-2）便于诊断 |
| 大载荷内存曲线为单机单档实测 | 目标机（4GB）表现可能更紧 | 结论给出 1×/3×/10× 余量口径，P8 视用户数据规模再评估 |
| 87 条 🟡 集中在 UI 展示层 | 视觉/交互细节未被机器守护 | 人工门走查 + 关键展示语义已自动化（数值/文案/状态标记） |
| 隐私最小化需要产品取舍（额度/带宽/覆盖） | 不实施可能保留可推断的持仓集合外发面 | 评估已量化，交人工定级（C0/C1/C2 三档建议） |
| CI 三平台复跑需推送触发 | win/mac 特有行为可能滞后暴露 | 本阶段推送后观察一次（留痕） |

---

## 9. 交付物清单

| 产物 | 说明 |
|------|------|
| `docs/test/test-plan.md` | 本文件（范围/策略/准则/矩阵/开放项处置/人工门） |
| `docs/test/test-cases.md` | 299 条用例（功能 + 异常 + 离线 + 限流 + 安全 + 人工门），逐条可追溯 |
| `docs/test/security-checklist.md` | **P6 复跑版**：五条硬约束逐条复核 + 登记项到期更新 + 新增实证 |
| `docs/test/defects.md` | 缺陷/问题清单与处置（含分级建议与影响面） |
| `docs/test/test-report.md` | 测试报告：执行结果、缺陷汇总、覆盖结论、发布建议、残留风险 |
| 新增测试/夹具 | `CproLargePayloadTest`、`BackupLoadBenchmark`（`:domain:backupBenchmark`）、`SettingsKeyNamespaceGuardTest`、`ProxyRoutingSmokeTest`、`BackupExportErrorCopyTest`、调度/V1-V2-V3 边界回归、recvWindow 契约回归 |
| 工具 | `scripts/outbound-capture-proxy.py`（出站抓包复核，可复跑） |

---

## 10. 需求回溯

| 本计划条目 | 需求锚点 |
|------------|----------|
| 阶段目标/DoD | `AGENTS.md §4 P6` |
| 功能用例范围 | PRD §5 故事 1.1–7.2、§7.2 模块 1–9、§9 交互细节 |
| 异常/离线/限流/加载/空态 | `docs/design/interaction.md` §1.1–1.4、§2.1–2.8 |
| 安全与隐私专项 | `AGENTS.md §1.1`（五条硬约束）、PRD §1.1/§5.1/§5.2/§6、共享规范 §8 |
| 计算口径 | PRD 附录 A 黄金用例 1–12 + D26/D27/D28/D29 |
| 决策增量 | `docs/dev/增量台账.md` D21/D24/D25/D26/D27/D28/D29 |
| 开放项处置 | `docs/test/security-checklist.md` §7、`docs/dev/modules/M11.md §5-2/§5-3`、`docs/test/integration-report.md` §6/§8 |
