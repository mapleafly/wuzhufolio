# WuZhuFolio 开发状态看板（STATUS.md）

> 本文件是**唯一的状态真源**。每个阶段完成后由 Agent 更新；人审核通过后解锁下一阶段。
> 状态取值：`待审核` / `进行中` / `已通过` / `未开始`。
> 规则见根目录 `AGENTS.md` 第 3 节。

## 当前阶段

- **当前状态**：**P4 分模块开发 ⏩ 进行中——M7 交易管理 ✅ 已通过（2026-09-08 人工「M7 通过。」关闭，含四轮走查反馈闭环 + 顶栏手动同步补口）**；M1–M7 均已通过。P0–P3 均已关闭。
- **推进顺序**：先桌面端，后移动端。**P1–P8 只针对桌面端或两端共同部分；移动端相关工作放到下一个版本。**（移动端相关技能/技术方案/开发待桌面端主线稳定后再启用。）
- **前序待审核项已关闭（2026-08-30，人工启动指令）**：① 项目级安装 huashu-design；② P1 新增「设计原型图」步骤；③ P1–P8 huashu-design 用途分析--已随人工「开始执行P1」指令一并拍板（固化为 `AGENTS.md` §7.1/§7.2）。
- **下一人工门**：**M8 资金管理（T8.1–T8.3）或并行面 M10 设置与日志诊断（T10.1–T10.4）启动**——M7 已通过解锁；建议 M8（增资/撤资/校准：V5 依赖的持仓建立路径 + M4 遗留资金事件构造层落地）；待人工下达启动指令。

## 阶段总览

| 阶段 | 名称 | 状态 | 产物 | 备注 |
|------|------|------|------|------|
| P0 | 需求基线 | ✅ 已通过 | 见下 | 评审已通过 |
| P1 | 产品与交互设计 | ✅ 已通过 | docs/design/（含 prototype/*.html + 截图 + 验证脚本） | 主版唯一真源（内置双主题）；登录链路已补齐；三轮评审 V1/V2/V3 问题全部闭环；2026-08-31 人工终审通过 |
| P2 | 技术方案 | ✅ 已通过 | `docs/tech/`（architecture + 6 ADR + data-model + api-contracts + task-breakdown + P2评审报告） | 2026-08-31 人工拍板三项关闭；全部 ADR 转人工拍板采纳 |
| P3 | 工程脚手架 | ✅ 已通过 | 代码骨架 + CI + dev-setup + hello 链路 + 迁移框架 + UI 基座（内嵌 CJK 字体）；Gradle 8.14.4；P3评审报告 | 2026-09-01 完成 M0 + 验收修复轮 + 评审闭环；人工「P3 通过」关闭 |
| P4 | 分模块开发 | 进行中 | 代码 + `docs/dev/modules/` | M1 ✅、M2 ✅、M3 ✅、M4 ✅、M5 ✅、M6 ✅（2026-09-07）；**M7 ✅（2026-09-08 人工通过，含四轮走查反馈闭环）**；下一模块 = M8/M10（待启动指令） |
| P5 | 集成与联调 | 未开始 | `docs/test/integration-report.md` | |
| P6 | 系统测试与质量 | 未开始 | `docs/test/` | |
| P7 | 发布 | 未开始 | `docs/release/` | |
| P8 | 上线后运营与迭代 | 未开始 | `docs/dev/retrospective.md` | |

## P1 产品与交互设计（✅ 已通过--2026-08-31 人工终审）

> **终审记录**：人工原话「P1人工终审通过」（2026-08-31）。P1 全部产物验收通过并关闭；原型基准 = `docs/design/prototype/wuzhufolio-light.html`（唯一真源 · 内置明暗双主题）；F4 对比度 token 为 P4 取用基线。以下为关闭时的产物与验收记录（历史归档）：

**产物清单（2026-08-31 修复闭环轮更新）**：

- docs/design/ia.md - 信息架构与页面清单（18 个页面；本轮补：状态栏代理指示、设置「日志与诊断」分组、CSV 模板下载、API 保存后首次同步、恢复前建账）
- docs/design/flows.md - 核心用户流程与状态机（9 组 Mermaid；本轮补：API 首次同步、恢复前建账两条规则）
- docs/design/interaction.md - 交互与异常态说明（本轮补：§1.1 代理常态指示注、§2.6 日志与诊断、§3.10/3.11）
- docs/design/design-tokens.md - 视觉与组件规范（本轮：token 对比度修正两主题回写、字号口径统一、圆角/Modal 口径对齐、a11y 基线声明、单一真源原则）
- docs/design/prototype/wuzhufolio-light.html - **唯一原型真源**（暖纸浅色 + 内置暗色档位，单文件可交互；本轮新增：登录链路 4 页（登录/创建含风险确认/初始化向导/忘记密码）、全量重放引擎、代理指示、日志与诊断、API 添加弹窗、恢复向导、环形图点击浮窗、表头排序、资金页筛选、a11y 基线、主题切换重渲染）
- docs/design/prototype-verify.js - **原型验证脚本（已入库）**：48 项 Playwright 断言（登录链路/双主题对比度/a11y/重放数值/全量回归），运行方式见脚本头注
- docs/design/prototype/wuzhufolio-light-login.png / -create.png / -wizard.png / -forgot.png - 登录链路 4 页截图（本轮新增）
- docs/design/prototype/wuzhufolio-light.png / wuzhufolio-light-dark-theme.png / wuzhufolio-light-settings.png - 仪表盘（明/暗）、设置页截图（本轮更新；**暗色截图 = 同一文件内置档位**）
- docs/design/direction-approved.md - 方向门 Gate 文件（本轮：日期订正、单一真源决策落档第 7 节、截图清单更新）
- docs/design/P1评审报告.md - Agent 评审报告 V1（2026-08-31：「有条件通过」，F1–F13）
- docs/design/P1评审报告V2.md - Agent 评审报告 V2（2026-08-31：深化验收通过，F1 唯一阻断 + N1–N5）
- docs/design/P1评审报告V3.md - **Agent 评审报告 V3（2026-08-31：修复闭环验证，F1–F13 + N1–N5 全部闭环，建议放行）**
- （wuzhufolio-dark.html / wuzhufolio-dark.png 已按「单一真源」原则删除，2026-08-31）

**本次改了什么（2026-08-31 修复闭环轮，人工三项指令）**：

1. **F1（阻断项）**：原型补齐登录链路 4 页（登录/账户创建含风险确认勾选/初始化向导四方式/忘记密码），PRD 逐字文案落地，登出回环、密码强度、字段级校验、创建->向导->各初始化路径全部可点击交互；P1 DoD「原型覆盖全部核心页面」达成。
2. **F2/F3/F4/F5（重要项）**：状态栏代理指示 + 设置「网络」分组；设置「日志与诊断」分组（查看/导出/诊断报告，脱敏与轮转规则注明）；token 对比度两主题修正（ink3/warn/暗色 ink3 全部 ≥4.5:1，gain/loss 加深留余量）；原型 a11y 基线（:focus-visible、全量 button 语义化、role/aria/tabindex、Modal 聚焦管理）。
3. **F7–F12（轻微项）**：字号/圆角/Modal 口径统一（文档回写）；主题入口顶栏+设置双向同步；资产表头排序、资金页筛选、环形图点击浮窗、API 添加弹窗、CSV 模板下载、恢复前建账、API 保存后首次同步；演示数据改**全量重放引擎**推导（与 PRD 附录 A 黄金用例一致）。
4. **单一真源 + 双主题（人工原则指令）**：light.html 为唯一真源，暗色为内置档位随深化自动维护；**dark.html/dark.png 已删除**（避免 P4 误引）；N5 修复（主题切换环形图即时重渲染）；direction-approved.md §7 落档。
5. **N1/N2**：Gate 文件日期订正为 08-31；看板验收口径同步。

**怎么验收**：

1. 浏览器打开 docs/design/prototype/wuzhufolio-light.html（唯一真源）：① 登录页（任意密码登录，空密码看校验；「忘记密码」看逐字文案）；② 「创建新账户」走 用户名/密码/确认 -> 风险确认勾选 -> 初始化向导 -> 任选四方式（API 路径可看到「保存后立即执行首次同步」）；③ 登录后主壳：侧边栏五页 + 顶栏 ☾ 切暗色（注意环形图配色即时切换）+ 交易/资金/币种详情/CSV/API/数据管理/行情 Key/切换账户/改密/登出（登出回登录页）；④ 设置页查看「网络」「日志与诊断」分组；⑤ 资产列表点表头排序、点行开币种详情（含三重筛选）、仪表盘点环形图扇区看浮窗。
2. 核对四份文字稿与 PRD 一致性（本轮新增条款均有 PRD 章节号）。
3. 复跑验证脚本：`NODE_PATH=<playwright> LD_LIBRARY_PATH=<libs> node docs/design/prototype-verify.js`（结果应为 errors=[]，48 项断言全绿；详见 P1评审报告V3.md 第六节）。
4. 双主题对比度：报告 V3 第四节数据表（两主题分别验证全部 ≥4.5:1）。

**已验证（Playwright 1.62.1，2026-08-31，脚本已入库）**：pageerror=0、console error=0；登录链路 4 页全路径走查（含风险确认勾选门控、4 条初始化路径、登出回环）；重放引擎数值与黄金用例一致（总资产 \$120,464.77 / 已实现 +\$4,915 / USDT 46,811.14 / ROI +31.26%）；环形图 4 扇区占比 100.00%；两主题 7 token × 2 底对比度全部 ≥4.5:1；a11y（div[onclick]=0、:focus-visible 规则、Tab 序、13 处 aria-label）；零外部依赖；双视口无溢出；无 undefined/NaN；深化轮全部交互零回归（交易表单总价 \$9,742.05 等）。

**P1 产物评审轨迹**：V1（2026-08-31）「有条件通过」-> 人工选主版 + 3 条反馈深化 -> V2 深化验收通过（F1 唯一阻断）-> **人工三项指令（本报告输入）** -> V3 修复闭环验证：**F1–F13 + N1–N5 全部闭环、单一真源原则落地、48 项断言全绿，建议放行**。

**遗留问题（转 P2/P4 注意清单，不阻断放行）**：

- N3：行情/交易所 Key 原型中以 JS 变量明文保存（演示需要）；P4 须按 DEK 字段级加密。
- N4：手续费币种下拉标签静态；P4 随交易对动态化。
- F13：手写 macOS 窗框（视觉等效）；P4 用原生窗框。
- 「关于」页为设置行 + toast 摘要；P4 扩展为面板。CSV 预览行/日志条目为静态演示数据。
- a11y 为基线（键盘 + 语义 + aria）；完整读屏（NVDA/JAWS）走查在 P4 执行。

**建议的下一步**：人工终审 P1（评审输入：P1评审报告.md + V2 + V3）-> 通过后解锁 P2 技术方案。

## P2 技术方案（✅ 已通过--2026-08-31 人工拍板三项关闭人工门）

> **关闭记录**：人工原话「拍板，按建议来处理 ① SQLCipher 驱动选型（P3 验证锁版）；② Flatpak 口径；③ 任务优先级/顺序」（2026-08-31）。三项均按建议落盘：ADR-002/003/004/005/006 状态全部转为「人工拍板采纳」；Flatpak 口径记入 ADR-006；任务顺序拍板记入 task-breakdown 头注。同期完成：ADR-002 Kotlin 亲和存储栈评审（Room KMP/Realm/SQLDelight 均否决，维持现有方案）+ 加密严苛度评审（结论匹配定位；「记住我」令牌语义欠定义已整改）。P2 关闭，P3 解锁为进行中。

> **2026-08-31 人工指令修订**：① 桌面端改 **Kotlin + Compose Desktop**（ADR-001 已重写，脚手架用官方 compose-multiplatform-desktop-template / KMP 向导）；② 行情客户端基于 **CoinGecko/CoinMarketCap 真实 API**（ADR-003 + api-contracts §1 已落真实端点/请求头/参数/限额）；③ 其余（存储加密/交易所/.cpro/构建分发/架构/契约/任务拆解）全面适配 Kotlin 栈。

**产物清单**（全部写入 `docs/tech/`，图例按已决策事项 14 用 Mermaid 内嵌）：

- docs/tech/architecture.md - 分层架构（UI/应用/领域/基础设施）+ 模块边界 + M0–M13 模块清单 + 关键机制（读写分离、全量重放、两类 API 隔离、安全边界）
- docs/tech/adr/ADR-001-桌面端技术栈.md - **Kotlin + Compose Desktop（2026-08-31 人工拍板采纳）**；脚手架用官方模板 / KMP 向导
- docs/tech/adr/ADR-002-存储引擎与加密方案.md - SQLCipher 整库 + 分层密钥 DEK/KEK（Argon2id 初值 + 校准要求）+ 单写队列
- docs/tech/adr/ADR-003-行情客户端CoinGecko-CMC两级模式.md - CG 主源（无 Key/个人 Key）+ CMC 兜底，**基于两平台真实 API**（端点/请求头/参数/限额）+ 额度治理/退避 + 快照/24h 同源
- docs/tech/adr/ADR-004-交易所同步适配.md - ExchangeAdapter 抽象 + Binance 单实现 + 增量去重
- docs/tech/adr/ADR-005-cpro备份格式.md - 明文头部 + AES-256-GCM JSON 载荷 + 解密→重加密语义 + 增量合并/全量覆盖
- docs/tech/adr/ADR-006-构建与分发.md - AGPL-3.0 + GitHub Releases + 三平台签名/公证 + CI 矩阵
- docs/tech/data-model.md - 11 表 ER 图 + 表结构（含索引/约束）+ 派生口径（重放推导，不落表）+ 迁移版本
- docs/tech/api-contracts.md - 行情/交易所（真实 API）/内部服务（Kotlin 用例接口）三类契约 + 错误码→PRD 文案映射
- docs/tech/task-breakdown.md - WBS（T0.1–T13.2）+ 依赖图 + 每任务可测试验收标准 + 里程碑门槛
- docs/tech/P2评审报告.md - **Agent 独立评审（2026-08-31）：有条件通过**——1 阻断（F1 行情 Key 加密边界三处矛盾）+ 2 重要（F2 Binance symbol 枚举策略缺失、F3 M12 UI 大整合后置）+ 5 轻微（N1–N5）；F1 消解 + F2/F3 处置后建议放行
- docs/tech/P2评审报告.md - **Agent 独立评审（2026-08-31）：有条件通过**——1 阻断（F1 行情 Key 加密边界三处矛盾）+ 2 重要（F2 Binance symbol 枚举策略缺失、F3 M12 UI 大整合后置）+ 5 轻微（N1–N5）；F1 消解 + F2/F3 处置后建议放行

**本次改了什么**：

1. **按人工指令改桌面端技术栈为 Kotlin + Compose Desktop**（ADR-001 已重写：官方模板/KMP 向导脚手架、Compose UI 映射 design-tokens、Koin/ViewModel/StateFlow、jpackage 分发），并显式记录读屏(a11y)支持弱于 Web 的风险。
2. 落地存储与加密（ADR-002）：SQLCipher 整库 + 随机 DB 密钥入 OS 钥匙串 + DEK/KEK 分层 + Argon2id 初值（m=64MiB/t=3/p=1，P3 校准 ≤2s）+ 字段级加密仅限 api_keys 四列 + 单写队列。
3. 落地行情两级模式（ADR-003，**基于 CG/CMC 真实 API**）与交易所适配（ADR-004），严格区分两类独立 API。
4. 落地 .cpro 格式契约（ADR-005）与构建分发（ADR-006）。
5. 数据模型 11 表 + 派生口径（类型映射改 Kotlin BigDecimal/Long）；接口契约 §3 改 Kotlin 进程内服务接口（Compose 无跨进程 IPC）；任务拆解到 40 个可独立验收任务（构建/测试改 Gradle + JUnit5 + Compose UI 测试）并绘依赖图。
6. 全部产物带需求回溯（PRD 章节号 / 共享规范条款号 / 黄金用例编号）。

**评审修订轮（2026-08-31，人工指令：F1 选方案甲；F2/F3 处置采纳并入文档；N1–N5 一并修订）**：

1. **F1 方案甲（行情 Key 加密边界收敛）**：新增 ADR-002 §2.1——CG/CMC Key 为应用级秘密，按设备密钥（OS 钥匙串，与 DB 密钥同级）加密存 settings 全局行，不用账户 DEK、不进 .cpro 备份；联动修订 ADR-003 备注、ADR-005 §3、data-model §2.3、architecture §2.4/§3.4、api-contracts §1 注。
2. **F2（Binance symbol 枚举）**：新增 ADR-004 §3.1——myTrades 必传 symbol，候选集合 = 余额推导 pair ∪ 已同步 pair（∪ 手动指定），单次同步权重预算 ≤1200（约 120 次调用），超额排队续传；api-contracts §2 与 T6.2 验收同步。
3. **F3（垂直切片）**：task-breakdown 重构——M0 增 T0.6 Compose UI 基座；M2/M5/M6/M7/M8/M9/M10 各增页面任务（自带 Compose 页面，P4 人工门有 UI 可验）；M12 退化为「主壳整合 + 聚合页 + 全局收尾」；architecture §4 同步。
4. **N1–N5 一并修订**：data-model 三处唯一性约束（COALESCE 表达式索引/部分唯一索引/快照 upsert）；ADR-002 单次 KDF + GCM tag 认证、密钥连接属性注入、WAL 验证项；ADR-001/003 定 OkHttp 引擎；ADR-006 修订 universal 措辞（差异仅 Flatpak）+ 点名许可证核验清单（T0.1 验收同步）。
5. **托盘与原生分发模型口径（人工问答确认后落盘）**：ADR-001 托盘行改 **Compose Tray/Notification API 首选**（AWT 仅底层实现）+ dorkbox Linux AppIndicator 备选，自启与 AWT 无关；ADR-006 新增 **§1.1 原生运行时分发模型**——「本地 exe 脱离系统 Java」= jpackage 捆绑 jlink 私有 JRE 既定方案，明确排除 GraalVM 原生编译（与 Compose Desktop 渲染栈冲突）；architecture §2.4 同步。

**怎么验收**：

1. 通读 6 份 ADR：ADR-001（Kotlin + Compose Desktop）已按人工拍板落地；重点确认 ADR-002（JVM SQLCipher 驱动选型 + Argon2id 参数初值）与 ADR-006（分发口径两处差异）。
2. 核对 data-model.md 11 表与 PRD §10 逐字段一致（字段名/类型/去重键/加密边界）。
3. 核对 task-breakdown.md 依赖图是否合理、M4 计算引擎是否前置且黄金用例覆盖（1–12）。
4. 抽查产物可回溯性：任取一条验收标准，应能追溯到 PRD 章节号/共享规范条款号。
5. **评审修订轮专项**：核对 F1 方案甲落地（ADR-002 §2.1 / ADR-003 备注 / ADR-005 §3 / data-model §2.3 全局行）、F2（ADR-004 §3.1 + T6.2 权重预算）、F3（T0.6 基座 + 各模块页面任务 + M12 收尾化）。

**遗留问题（不阻断审核，但需人工关注）**：

- **JVM SQLCipher 驱动为社区维护（Willena/sqlite-jdbc-crypt）**，P3 须三平台验证并锁版；失败则回退自绑 JNI（需人工确认，ADR-002 风险表）。
- **Compose Desktop 读屏（NVDA/JAWS）支持弱于 Web**，PRD §6/T12 的完整读屏走查在 P4 实测，若为硬阻断需升级人工（ADR-001 风险表）。
- **与 PRD §12 的口径差异仅 Linux Flatpak 一处待人工确认**（macOS 双架构出包经评审 N3 核实已满足 PRD）：Flatpak 需另写 manifest，本轮先 AppImage/.deb/.rpm，见 ADR-006 风险表。
- Argon2id 参数为初值，P3 在 4GB 双核目标机基准校准后固化（ADR-002 风险表）。
- 签名/公证具体命令与证书申请留 P7；P3 只搭 CI 与未签名构建链路（ADR-006）。

**建议的下一步**（已执行）：P2 于 2026-08-31 拍板通过；**下一步 = 人工下达 P3 启动指令**，按 task-breakdown M0（T0.1–T0.6，含 T0.6 Compose UI 基座）搭建工程脚手架。

## P3 工程脚手架（⏳ 待审核--2026-09-01 完成 M0，停人工门）

> 启动记录：人工原话「执行P3」（2026-09-01）。范围 = task-breakdown M0（T0.1–T0.6，P3 DoD：本地构建通过、CI 绿、hello 链路端到端可跑 + UI 基座双主题、对比度达标）。首次 git 提交 `6ed10fd`（85 文件，P0–P2 文档同期入库）。

**产物清单**：

- 工程骨架：`settings.gradle.kts` + `build.gradle.kts` + 四模块（`app/` 组装入口、`ui/` 主题组件主壳、`data/` 存储迁移设置、`domain/` 纯 Kotlin）+ `gradle/libs.versions.toml`（version catalog，全依赖锁版）+ `gradle/wrapper/`（Gradle 8.14.4 Wrapper 唯一真源）+ `.gitignore/.gitattributes`
- `LICENSE` - AGPL-3.0 全文（决策 D1）
- `docs/tech/dependency-licenses.md` - 依赖许可证清单（T0.1/N5）：19 项直接依赖 POM 实证 + 构建期工具，**全部兼容 AGPL-3.0**；注意项 2 条（argon2-jvm LGPL-3.0 动态链接合规、logback EPL-2.0/LGPL-2.1 双许可取 EPL）
- `.mise.toml` - mise 工具链（java temurin-17；gradle 条目已按口径移除）
- `docs/tech/dev-setup.md` - 本地开发环境搭建（mise 口径、环境检查清单 5 项、WSL2 注记：Skiko GL→SOFTWARE_FAST、托盘以实机为准、WUZHUFOLIO_DATA_DIR 隔离）
- `.github/workflows/ci.yml` - CI 三平台矩阵（ubuntu/windows/macos）：setup-java temurin-17 + setup-gradle（wrapper 校验）→ test → detekt → packageUberJarForCurrentOS（uber jar）冒烟 → 构件上传（createDistributable 仅本地冒烟通过，安装器打包按 ADR-006 留 P7 不进 CI）
- T0.4 hello 链路：`data/.../hello/HelloChain.kt` + `domain/.../redaction/LogRedactor.kt`（脱敏器）+ `app/.../Main.kt`（Koin 组装 + Compose 窗口）
- T0.5 迁移框架：`data/.../db/`（Migration/Migrations/Migrator/WzDatabase）——schema_version 表 + M001 settings 表（COALESCE 唯一索引，N1）+ M002 默认设置，事务化逐条执行、幂等
- T0.6 UI 基座：`ui/.../theme/`（design-tokens 单源映射：明/暗 WzColors + 三套盈亏方案 + WzTypography）、`components/`（WzButton/WzTextField/WzTable/WzModal/WzToast/WzStatusBar）、`shell/MainShell.kt`（侧边栏五页空壳 + 顶栏 ☾/☀ 主题切换 + 状态栏）、`gallery/ComponentGallery.kt`（组件走查页）
- 测试 17 项全绿：LogRedactorTest(5) / MigratorTest(4) / HelloChainTest(2) / ContrastTest(2) / ShellUiTest(4)
- `README.md` - 构建/运行/测试命令与仓库结构
- `docs/tech/P3评审报告.md` - **Agent 独立评审（2026-09-01）：有条件放行**——DoD 三项独立实证（无缓存全量重跑 21 测试 0 失败 / 全新克隆构建验证 CI 根因修复 / CI #33523962735 三平台绿核实 / hello 链路 GUI 实测脱敏日志）；发现项 F1（dev-setup §4 密钥口径与 ADR-002 §2.1 方案甲矛盾，放行附带条件）+ N1–N5（轻微，转 P4 注意清单）

**本次改了什么**：

1. **T0.1**：以官方模板口径手工搭建 Kotlin 2.4.10 + Compose Multiplatform 1.12.0 四模块工程（官方兼容性页确认「最新 CMP 兼容最新 Kotlin」）；全部 P2 选型依赖锁进 version catalog（Exposed 1.5.0、xerial/Willena sqlite-jdbc 3.53.4.0、argon2-jvm 2.12、java-keyring 1.0.4、bcprov 1.85.2、Ktor 3.5.2、OkHttp 5.5.0、Koin 4.2.2、logback 1.6.3 等）；许可证清单 POM 实证核验。
2. **T0.2**：CI 三平台矩阵（test + detekt + 打包冒烟 + 构件上传）；本地实测打包冒烟通过（app-image 151MB 符合 ADR-001 预估、uber jar 58MB）；安装器打包（msi/dmg/deb）与签名公证按 ADR-006 留 P7。
3. **T0.3**：mise 装 temurin-17.0.20+101；Wrapper 8.14.3 生成后移除 mise gradle；dev-setup.md 含环境检查清单 + WSL2 注记（GL 异常→SOFTWARE_FAST 实测有效）。
4. **T0.4**：hello 链路实测——日志行 `hello-chain ok | schema_version=2 | settings(count=4): ... | market_api_key=****`（脱敏生效，控制台+滚动文件双写）。
5. **T0.5**：迁移框架空库初始化到 v2、重复执行幂等、唯一索引行为单测覆盖。
6. **T0.6**：双主题 token 1:1 映射（F4 修正值）；ContrastTest 以 WCAG 公式守护两主题 7 token×2 底 ≥4.5:1；主壳五页空壳 + 走查页；ShellUiTest 覆盖导航/主题切换/走查渲染/Modal 开关。

**已验证（2026-09-01，WSL2 Ubuntu 24.04 + temurin-17）**：`./gradlew clean build` 绿（编译 + 17 测试 + detekt 严格 maxIssues=0）；GUI 冒烟（WSLg + SOFTWARE_FAST）：hello 链路日志两行落盘、窗口无渲染异常驻留至 timeout；打包冒烟（createDistributable + uber jar）通过。

**怎么验收**：

1. `mise install && export JAVA_HOME=$(mise where java) && ./gradlew build` —— 应全绿（17 测试 + detekt）。
2. GUI 走查：`JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE_FAST" ./gradlew :app:run` —— 看主壳五页 + 走查页组件、顶栏 ☾ 切暗色即时重渲染、状态栏/Toast/Modal。
3. hello 链路：查 `~/.wuzhufolio/logs/wuzhufolio.log` 含脱敏日志行（`market_api_key=****`）。
4. 核对 `docs/tech/dependency-licenses.md` 与 `.github/workflows/ci.yml`（CI 绿需推送 GitHub 后观察——本地未建仓远端，见遗留 1）。
5. 对照 dev-setup.md 环境检查清单 5 项逐项打勾。

**遗留问题（不阻断，转 P4 注意清单）**：

1. **CI 未实际跑过**：仓库尚无 GitHub 远端（本地 git 已建仓并首提交）；推送后首跑若失败按日志修（macos-latest 架构、windows bash 行为差异属低风险点）。
2. **Exposed 连接语义踩坑记录**（P4 必读）：Exposed `Database.metadata()` 无事务时会关闭 connector() 给出的连接——WzDatabase 已改用新连接工厂；M1 单写队列落地时保持「Exposed 连接 ≠ 共享写连接」。
3. **桌面端 focusable Popup/Dialog 脱离测试语义树**：WzModal 用 focusable=false + 手动 esc/遮罩关闭；P4 做 Modal 聚焦首字段时勿改回 focusable=true（否则 UI 测试失效）。
4. Kotlin 2.5 起最低 Gradle 8.14.4（当前 8.14.3 有 deprecation 警告）；Exposed 1.5 为 `v1.*` 新包名（`org.jetbrains.exposed.v1`），P4 查文档注意版本匹配。
5. Argon2id 参数初值未校准（T1.3 在 4GB 双核夹落实测，P4 首模块项）；SQLCipher 驱动三平台锁版验证在 T1.1。
6. 侧边栏导航为纯文字骨架（无图标），P4 按原型截图补齐图标与细节视觉。

**建议的下一步**：人工按上节验收 P3 → 通过后 P4 解锁，首个模块 = **M1 存储与加密**（T1.1 SQLCipher 锁版三平台验证 / T1.2 CryptoService / T1.3 KDF 基准校准 / T1.4 单写队列；M4 引擎为并行面硬前置）。


## P3 验收结果与修复轮（2026-09-01，人验收反馈 4+1）

> 人验收（2026-09-01）结论：① build 通过 ✅ ② GUI 弹出窗口但**中文乱码** ❌ ③ hello 链路日志脱敏正确 ✅ ④ 许可证/CI 配置核对无问题 ✅。
> 另交付遗留 1（建 GitHub 仓库推送观察 CI 首跑）。遗留 2/3/5 为 P4 注意清单（已入 P3 遗留）；遗留 4（Kotlin 版本）答复见下。

**修复轮改动**：

1. **中文乱码（验收第 2 项）**：根因 = Linux/WSL 无 CJK 系统字体（`fc-list :lang=zh` 实测 0），Skiko 回退渲染为豆腐块。修复 = 内嵌 Noto Sans SC / Noto Serif SC / JetBrains Mono 可变字体（OFL-1.1，~43MB），`WzFonts.kt` 以 FontVariation 实例化字重、表格数字 Mono+Noto 回退链；`FontBundleTest` 4 项守护（加载 + CJK 字形 + wght 轴 + 克隆实例）。GUI 冒烟（SOFTWARE_FAST）无异常。字体体积瘦身登记 P7（pyftsubset）。
2. **CI 首跑失败（遗留 1 观察项）→ 已修复，CI 三平台全绿**：三平台均 `:app:testRuntimeClasspath → Could not resolve project :data → No variants exist`。**真根因（全新克隆复现 + git ls-files 定位）**：`.gitignore` 中误写 `data/`（本想忽略运行时数据目录），把 `data` 源码模块整个忽略了——**data 模块 9 个文件从未入库**；本地因文件仍在磁盘而能构建（假绿），CI/全新克隆后 data 目录为空 → `:data` 无构建脚本 → 无插件 → 无变体 → "No variants exist"。**修复 = 移除 `data/` 条目（运行时数据在 ~/.wuzhufolio，不在仓库内）+ 补提交 data 模块**。期间曾误判的类型安全访问器/Gradle 版本/parallel/include 顺序/配置缓存/jvmToolchain 均非根因，但 Gradle 8.14.3→8.14.4、去访问器、生产者优先 include、jvmTarget 显式化作为卫生性改进保留。**CI 复跑（`ff9fd6a`）三平台全绿**（test + detekt + uber jar）。另修复 `.agents/skills/huashu-design` 被误当 gitlink（无 .gitmodules）→ 去嵌套 .git 后按普通文件入库（189 文件，node_modules 已排除）。
3. **遗留 4 答复（Kotlin 版本）**：**维持 Kotlin 2.4.10，不升 2.5**——当前最新稳定版为 2.4.10（2.4.20 尚为 RC2，2.5.0 未发布）；CMP 1.12.0 官方口径「最新 CMP 兼容最新稳定 Kotlin」，配对 2.4.10 成立。2.5.0 正式发布且 CMP 出配套版本后，随 P4 里程碑评估升级（届时 Wrapper 最低要求 8.14.4 已满足）。
4. **交付遗留 1**：已 `gh repo create wuzhufolio --public` 建仓并推送（`https://github.com/mapleafly/wuzhufolio`，mapleafly 账号，D1 开源口径公开）。

**待人工复核**：① 重新跑 `JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE_FAST" ./gradlew :app:run` 确认中文已正常（不再乱码）；② 观察 CI 复跑（`gh run watch`）；③ 确认 Kotlin 2.4.10 维持结论可接受。

## P3 评审闭环（2026-09-01，Agent 评审 + 人复核）

> 输入：人指令「评审P3」。Agent 产出 `docs/tech/P3评审报告.md`（有条件放行：DoD 三项独立实证 + F1 + N1–N5）。人复核结论：① 中文渲染通过 ✅ ② Kotlin 维持 2.4.10 ✅。随后按评审发现项修复闭环：

1. **F1（重要）**：dev-setup.md §4 密钥口径订正——「绝不入库」→ **设备密钥加密存 settings 全局行**（key=market.coingecko_key / market.cmc_key）、不落明文、不进 .cpro 备份（对齐 ADR-002 §2.1 方案甲）。
2. **N1**：dev-setup §1 表 Gradle 版本 8.14.3 → 8.14.4（与 wrapper.properties 一致）。
3. **N2**：STATUS 产物清单 CI 描述对齐 ci.yml 现况（仅 uber jar 冒烟；createDistributable 本地冒烟通过、安装器打包按 ADR-006 留 P7 不进 CI）。
4. **N3**：dev-setup §6 与 dependency-licenses §2b 的 §BT§ 转义残留清理（恢复反引号）。
5. **N4**：编译警告清零——① app 显式补 `lifecycle-viewmodel-compose` 依赖（消除 ViewModel 超类型跨模块可见警告）；② ShellUiTest 迁移 v2 测试 API（消除 runComposeUiTest 弃用警告）。
6. **N5**：仅登记不处理（CI 不含 createDistributable 为 ADR-006 既定口径，报告 §三已登记）。
7. **修复验证**：`./gradlew clean build --rerun-tasks --no-build-cache` 全量 30 任务真实执行绿；21 测试 0 失败 0 错误；**编译警告 0 条**。

**关闭记录（2026-09-01）**：人工原话「**P3 通过**」——P3 关闭 ✅；P4 解锁为进行中，待人工下达启动指令（首个模块 = M1 存储与加密：T1.1 SQLCipher 三平台锁版 / T1.2 CryptoService / T1.3 KDF 基准校准 / T1.4 单写队列）。

## P4 · M1 存储与加密（✅ 已通过--2026-09-03 人工「M1 通过」）

> 启动记录：人工原话「执行P4」（2026-09-02）。范围 = task-breakdown M1（T1.1 SQLCipher 锁版实证 / T1.2 CryptoService / T1.3 KDF 基准校准 / T1.4 单写队列）。**模块记录：docs/dev/modules/M1.md**（实现摘要 / 改动文件 / 验收清单 / 实测证据 / 遗留）。
> **关闭记录（2026-09-03）**：人工原话「M1 通过」——M1 关闭 ✅；验收轨迹：步骤 0–4 实测（主窗口+降级模态两遍运行、FatalWindow 两路径 15:26/15:27 触发、master.key 还原复验 19:44）+ 步骤 5 证据核对完成；M2 解锁为进行中，待人工启动指令。

**产物清单（代码 + 测试，全量绿）**：

- **T1.1 SQLCipher 整库加密（锁版 3.53.4.0 实证）**：Willena fork（org.sqlite.mc，SQLCipher 4 兼容）raw-key 连接属性注入（ADR-002/N4 口径）；WzDatabase 重构（错钥/旧库 → DatabaseKeyMismatchException 类型化）；MasterKeyStore（OS 钥匙串 javakeyring + 探针区分缺失/不可用 + 0600 文件降级 + Resolver）；无钥匙串 → MainShell 安全提示模态（startupNotice）；FatalWindow（错钥/密钥文件损坏引导）
- **T1.2 CryptoService（domain 纯 Kotlin）**：Argon2Kdf（BouncyCastle 纯 JVM）/ KdfParams（存储串契约 + OWASP 兜底）/ KeyWrap（DEK/KEK，v1+base64）/ FieldCipher（api_keys 四列，AAD 三重隔离）/ CryptoService 门面 / Zeroization 擦除 —— 单测 17 项覆盖包解包/错钥失败/密文不相等/篡改拒绝
- **T1.3 KDF 校准**：kdfBenchmark 夹具实测（本机 64MiB/t3/p1 ≈ 169ms）；**冻结 KdfParams.DEFAULT = m=64MiB/t=3/p=1**；4GB 双核目标机复核转 M2 门（遗留 1）
- **T1.4 单写队列**：DbGate（Mutex 串行写 + WAL 读并行）；SettingsRepository 全部读写经闸门；并发测试（72 交错 upsert / 60 读改写计数 / 读写并行）无锁冲突
- **接线**：AppBootstrap 启动链（钥匙串→DB→闸门→Koin）+ 双主题 UI 无回归；version catalog / dependency-licenses（新增 §1b 钥匙串传递依赖核验）同步
- 测试合计：domain 31 + data 20（+2 钥匙串真实后端测试在无 Secret Service 环境跳过，CI win/mac 验证）+ ui 12 = **63 执行 0 失败**；detekt 0 issue；无缓存全量 30 任务绿、编译警告 0
- GUI 冒烟实证（WSLg）：master.key 0600、hello/bootstrap 脱敏日志、二次启动同钥解锁幂等、降级提示模态渲染无异常、**主窗口驻留至手动关闭（2026-09-03 修复轮实证 rc=124）**

**2026-09-03 修复轮（评审实测触发，已修复）**：主窗口不出现且进程约 2s 静默退出——根因 = main() 中 `remember { runCatching { AppBootstrap.run(logger) }.getOrElse { FatalOutcome.of(it, logger) } }` 的返回类型被推断为 **Any**（AppBootstrap.Runtime 与 Outcome.Fatal 的 LUB），`when` 的 `is Outcome.Ready` 永不命中 → 成功路径不组合任何窗口即退出（失败路径恰命中 Fatal 分支，故此前 FatalWindow 可见而主壳不可见）。修复 = 引导结果显式收敛为密封 Outcome（try/catch 包 `Outcome.Ready(...)`），分支完备；复测两连启窗口均驻留至超时（rc=124），63 测试 + detekt 绿。

**怎么验收（人工）**：
0. **（仅本开发机）M0 明文库兼容前置**：M0 期明文开发库无法以新主密钥解锁属预期（遗留 3）——旧库已归档为 `~/.wuzhufolio/wuzhufolio.db.m0-plaintext-20260901.bak`（2026-09-03 处置，勿删）；若再次出现「无法解锁本地数据库」且数据目录内有旧明文 wuzhufolio.db，先移走旧库再启动。
1. 按 docs/dev/modules/M1.md §3 验收清单逐项打勾；
2. GUI 复跑 `JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE_FAST" ./gradlew :app:run`：启动见「安全提示」降级模态（本机 dbus 在但无 Secret Service 提供方 → 钥匙串不可用为预期）+ 两行脱敏日志（hello-chain / bootstrap ok）；
3. 二次启动：**降级模态仍会弹出**（FILE_FALLBACK 期间安全提示常驻，属 T1.1 语义；钥匙串恢复后自然消失）——重点核对日志为「master key loaded from degraded key file」（重启可解锁、密钥复用）；
4. FatalWindow 两条路径（任选其一验证，验后恢复）：把 master.key 内容改成非 64 位 hex（如 0 字节/乱码）启动 → 「本地密钥文件异常」；把 master.key 换成**另一个有效的 64 位 hex 密钥**（或把数据目录 db 换成别的加密库）启动 → 「无法解锁本地数据库」；
5. 核对模块记录 §4 实测证据（三组，均给出复跑命令）：
   ① **KDF 耗时表**：`./gradlew :domain:kdfBenchmark`——本机期望 64MiB/t3/p1 ≈ 169ms（best-of-3，允许 ±30% 浮动；该行即「登录 1 次派生」预算基准，4GB 双核目标机复核为 M2 门项）；
   ② **SQLCipher 探针**：探针为一次性临时程序（未入库），库内等价守护 = `./gradlew :data:test --tests "*SqlCipherDatabaseTest*"`（文件头非明文 / 错钥类型化 NOTADB / WAL·busy·foreign_keys / 重启解锁）；CI 三平台同套测试已绿（run 33656880538 ca6e69b、33716830925 7fff251）；
   ③ **冒烟记录**：本验收步骤 2/3 的两次运行即为复现（窗口+降级模态+两行脱敏日志+密钥复用）；
   另可交叉核对 docs/dev/modules/M1评审报告.md「二、独立复跑实证」（无缓存全量 30 任务绿 + 测试计数）。

**人工验收进度（2026-09-03）**：步骤 0–4 已完成——① 两遍 `:app:run` 均弹主窗口 + 「安全提示」降级模态（15:18/15:20，日志 bootstrap ok + 密钥复用）；② 步骤 4 两条 FatalWindow 路径实测触发（15:26 损坏 master.key → 「本地密钥文件异常」；15:27 换有效异密钥 → 「无法解锁本地数据库」，日志见 ~/.wuzhufolio/logs）；③ 步骤 4 验后已还原原密钥 ae3046… 并复验启动健康（19:44 窗口驻留 rc=124）。**步骤 5（§4 证据核对）操作说明已内嵌本段上方步骤 5**；M1 维持待审核，待人工完成步骤 5 后给出通过结论。

**遗留问题（转 M2 注意清单，详见模块记录 §5）**：目标机 KDF 复核（≤2s）/ 三平台 CI 实证待推送观察 / M0 明文开发库不兼容（移走重生成）/ M5 设备密钥条目 / B1 恢复页随 M2/M9 / 降级模式迁回钥匙串入口（登记）。

**建议的下一步**：人工审核 M1 → 通过后解锁 M2 账户与会话（T2.1–T2.5，登录链路页面按原型还原）。

## P4 · M2 账户与会话（✅ 已通过--2026-09-03 人工「M2 通过」）

> 启动记录：人工原话「执行P4-M2」（2026-09-03）。范围 = task-breakdown M2（T2.1 accounts/创建登录 · T2.2 记住我/会话 · T2.3 切换 · T2.4 改密 · T2.5 登录链路页面）。**模块记录：docs/dev/modules/M2.md**（实现摘要 / 文件清单 / 验收清单 / 契约落档 / 原型差异 / 遗留）。
> **关闭记录（2026-09-03）**：人工「M2 通过」——M2 关闭 ✅。验收轨迹：创建/风险门控/向导/记住我重启免密/登出回环/多账户切换（严格模式）/改密/忘记密码全流程实测；KDF 本机实测 151ms（64MiB/t3/p1）——4GB 双核目标机复核调整为「开放待办：M13 发布前若可得目标机再实测（预算 ≤2s，超标降 OWASP_MINIMUM 预案在册）」；原型差异 6 条全部按建议采纳（模块记录 §5）；验收实测 4 缺陷（密码明文/空提交两错/toast 闪现/busy 泄漏/弹窗键盘不可用）经修复轮 ce0b6c9 闭环并复验。

**产物清单（代码 + 测试，全量绿）**：

- **T2.1**：M003 accounts 迁移（schema v3）；AccountRepository.createWrapped 先插后包（AAD=真实 id）；DefaultAccountService（单次 KDF + password_hash=SHA-256(KEK‖verify) 快速判错 + GCM 解包认证；A1 不暴露存在性；DEK 驻 ActiveSessionStore 即擦即清）；UsernameTakenException 上移 domain
- **T2.2**：记住我 = 随机 256-bit 令牌 + wrapped_dek_session（AAD=account_id+"session"）入钥匙串（remember-me 条目，探针缺失语义）；登出/未勾选/切换/改密清条目；降级 0600 文件模式可用；restoreSession 契约补录
- **T2.3/T2.4**：切换严格模式（错密码「密码错误」/A3）；改密 A2 验证 → 重包同 DEK → 令牌作废 → 回登录页（PRD 5.1-6）
- **T2.5**：ui/auth 六件套 + MainShell accountArea + ShellViewModel initialPage + AuthGate 门控宿主；逐字文案/三错并行/强度条/风险确认硬门控/向导四卡占位/忘记 A4/账户菜单/切换 380/改密 400 全部按原型与交互稿
- 测试：domain 38 + data 30（2 钥匙串真实后端跳过待 CI win/mac）+ ui 17（AuthFlowUiTest 5 场景）= **85 执行 0 失败**；detekt 0；GUI 冒烟（schema v3、remember-me 降级提示、窗口驻留 rc=124、0 异常）

**怎么验收（人工）**：① docs/dev/modules/M2.md §3 逐项打勾；② 真实数据目录跑通「创建（风险勾选）→ 向导 → 稍后进主壳 → 登出回登录 → 登录 → 账户菜单切换/改密回环」；③ 对照 docs/design/prototype/wuzhufolio-light-login/-create/-wizard/-forgot.png 走查文案与门控；④ 目标机 KDF 计时 ≤2s 复核；⑤ §5 原型差异 6 条逐条裁决。

**遗留（转后续模块，详见模块记录 §6）**：目标机 KDF 复核（本门）；钥匙串真实后端 CI 实证；向导占位 M6/M7/M9 替换；枚举开关 UI M10；顶栏/下拉/密码可见性 M12。

**建议的下一步**：人工审核 M2 → 通过后解锁 M3 币种主数据（T3.1–T3.3）。

## P4 · M3 币种主数据（✅ 已通过--2026-09-03 人工验收通过并确认关闭）

> 启动记录：人工原话「执行P4-M3」（2026-09-03）。范围 = task-breakdown M3（T3.1 coins 目录 / T3.2 CoinResolver 消歧归一 / T3.3 FiatNormalizer）。**模块记录：docs/dev/modules/M3.md**（实现摘要 / 改动文件 / 验收清单 / 契约落档 / 勘误登记 / 遗留）。
> **关闭记录（2026-09-03）**：人工原话「T3验收通过。1235测试通过。4未测试」+ 确认指令「确认 M3 通过，解锁 M4」——M3 关闭 ✅；验收轨迹：T3 验收标准逐条通过、§4 步骤 1/2/3/5 通过（build detekt 复跑 / 单测聚焦 / §3 清单 / DDL↔data-model 一致性含 contracts 勘误回写 data-model §2.9）、步骤 4（GUI 冒烟·可选）未人工复测不阻断（Agent 开发期冒烟证据在案：真实 v3 库→schema=5、0 异常）；M4 解锁为进行中，待人工启动指令。

**产物清单（代码 + 测试，全量绿）**：

- **M004 coins（schema v4）/ M005 exchange_coin_map（v5）**：全局公共表（无 account_id）；UNIQUE(cg_id)、UNIQUE(exchange, exchange_asset)、symbol/name 索引、contracts（各链合约地址 JSON，桌面 data-model 勘误登记——PRD §10-10 注 + 消歧② 需要，移动端 SRD 同源）
- **T3.1**：domain CoinCatalog 契约（10 动作，api-contracts §3 补录）+ SqlCoinCatalog（检索：symbol 精确/前缀/名称包含 + 大小写归一「usdt→USDT」；refreshDirectory 全量幂等 upsert + 同批去重；refreshCmcIds 每日缓存）
- **T3.2**：CoinResolver 四级消歧纯规则（① quote 上下文排除/退化对 → ② 合约地址精确匹配不猜测降级 → ③ 市值排名唯一最小 MarketRankProvider（Noop 默认，M5 注入前 1000）→ ④ Ambiguous 候选）；resolve 先复用映射（FROZEN_MAP）→ 自动唯一固化 AUTO → 歧义由 freezeMapping 固化 MANUAL（UnknownCoinException 类型化）
- **T3.3**：FiatNormalizer（USD→USDT、EUR→EURC 默认孪生表 + 48 常用法币第三币种集合 + twinMap 可注入预留 M10）；fiatQuoteLeg（TwinStable/ThirdCurrency/null 三分诊，缺目录币安全退化）
- 测试：domain 63 + data 49（4 钥匙串跳过待 CI win/mac）+ ui 17 = **129 全绿 0 失败**；detekt 0；编译警告 0；存量测试 schema 版本 3→5 同步
- GUI 冒烟实证（真实 v3 开发库）：bootstrap 日志 schema=5（M004/M005 自动应用）、历史设置保留、0 异常

**怎么验收（人工）**：① docs/dev/modules/M3.md §3 逐项打勾（Agent 已自动验 7 项，人工走查 T3 验收标准与代码）；② 复跑 ./gradlew build detekt（129 测试 0 失败 + detekt 0）；③ 抽查 DDL 与 data-model §2.9/2.10 一致性（contracts 勘误见模块记录 §5）；④ 可选 GUI 冒烟看 schema=5。

**人工核对进展（2026-09-03）**：验收第②项「抽查 DDL 与 data-model 一致性」通过——逐字段对照无未登记差异；唯一差异 contracts 勘误获人工认可，**已回写 data-model §2.9**（M004 落列；回溯 PRD §10-10 注 / 移动端 SRD §14；登记 M3.md §3-8/§5-1）。M3 维持「待审核」——T3 验收标准逐条/代码走查/整体放行待人工终审。

**遗留（转后续模块，详见模块记录 §6）**：搜索全表扫描优化（FTS/前缀，M5 实测后评估）；目录 status 维护与「无行情」策略归 M6；cmc_id 对齐归 M5；MarketRankProvider 真实实现归 M5；别名/专有资产规则归 M6；CI 三平台复跑待推送；FiatNormalizer 设置化归 M10。

**建议的下一步**：人工审核 M3 → 通过后解锁 **M4 计算引擎**（T4.1–T4.4；依赖 T1；M5/M6/M7 亦依赖本模块 CoinCatalog）。

## P4 · M4 计算引擎（✅ 已通过--2026-09-04 人工裁决关闭）

> 启动记录：人工原话「执行P4-M4」（2026-09-04）。范围 = task-breakdown M4（T4.1 ReplayEngine / T4.2 PortfolioCalculator / T4.3 FeeCalculator / T4.4 ReconciliationService；纯领域模块，无建表/UI 改动）。**模块记录：docs/dev/modules/M4.md**（实现摘要 / 文件清单 / 验收清单 / 规格裁决 / 遗留）。
> **关闭记录（2026-09-04）**：人工原话「M4.md §5 规格裁决 按建议的来执行。其他未决项按建议执行」——§5 规格裁决 10 条全部按建议采纳生效（代码已按裁决实现，无需改动）；M4 验收通过 ✅；未决项（遗留路由 M5–M9、校验宽松口径复核归 M8、下一步解锁）按建议执行；**M5 行情链路解锁为进行中，待人工启动指令**。

**产物清单（代码 + 测试，全量绿）**：

- **T4.1 ReplayEngine**：LedgerEvent 三态事件化（Trade/Fund/Anchor，折算值随事件、引擎无价格 IO）+ 全量重放（持仓/均价/已实现逐笔/累计增资撤资）+ 校准锚点强制对齐；STRICT（手动路径抛 NegativePositionException）/ LENIENT（导入路径记违例 + 持仓异常标记）两策略 + `validateMutation` 相对校验（仅拦「操作新制造」的负边界，黄金 6 修复路径不被误拦）
- **T4.2 PortfolioCalculator**：净值（缺价不计）/可用现金（稳定币白名单，默认 USDT/USDC/DAI/TUSD，可注入）/投入本金（净）/总收益/ROI（累计增资=0 → null 显 "--"）/已实现/未实现/币种级明细
- **T4.3 FeeCalculator**：费率优先级（交易所 > 全局，FeeRateResolver）+ 三币种基数（quote=费率×总价 / base=费率×数量 / 第三币种=总价基数按行情价折算为该币种数量）+ 缺价 null（PENDING 语义）
- **T4.4 ReconciliationService**：单一来源判定（仅交易事件计源：Manual/CSV/Exchange；SingleExchange 可校准、Multi 隐藏入口）+ 校准差额规划（方向分派/折算值/市价前提 CalibrationPriceUnavailableException）
- **黄金用例 1–9、12 全绿**（里程碑门槛）：①建仓/已实现 4,915、均价 50,050 ②增资撤资零盈亏 ③撤资 ROI 50% ④再增资 ROI 25% ⑤第三币种手续费（BNB 联动扣减裁决，见规格裁决 3）⑥CSV 负持仓补增资消除（含晚于卖出的补录路径）⑦调小/删除增资被校验阻止 ⑧负差额校准恒等式（总收益校准前后一致）+ 锚点不回滚 ⑨PENDING 估算→回填重算 ⑫法币交易对归一联动
- 测试：domain 63 → **124**（engine 包新增 61：GoldenCasesTest 11 + ReplayEngineTest 22 + PortfolioCalculatorTest 10 + FeeCalculatorTest 9 + ReconciliationServiceTest 9）+ data 49 + ui 17 = **190 全绿 0 失败**；detekt 0；编译警告 0；无缓存 clean build 实测绿
- docs/tech/api-contracts.md §3 补录引擎契约注 + 回溯行（事件构造层归 M7/M8，先例 M3）

**本次改了什么**：见模块记录 §1（四任务实现摘要 + §5 规格裁决 10 条，重点：记账单位 = 基础法币且折算值事件化、第三币种手续费「联动扣减」勘误裁决、负持仓校验相对语义、单一来源仅计交易事件）。

**怎么验收（人工）**：

1. docs/dev/modules/M4.md §3 验收清单逐项核对（黄金用例关键数值轨迹已内嵌）；重点走读 §5 规格裁决 10 条是否认可。
2. 复跑：`export JAVA_HOME=$(mise where java) && ./gradlew clean build detekt`（190 测试 0 失败 + detekt 0 + 警告 0）。
3. 黄金用例聚焦：`./gradlew :domain:test --tests "com.wuzhufolio.domain.engine.GoldenCasesTest"`（11 项）；引擎全量：`--tests "com.wuzhufolio.domain.engine.*"`（61 项）。
4. GUI 冒烟不适用（M4 纯领域、无 UI/schema 改动，M3 先例口径）。
5. 对照 PRD 附录 A 口算核对引擎断言值。

**遗留问题（转后续模块，详见模块记录 §6）**：事件构造层接线（M6/M7/M8：FK→cg_id、折算价解析、双列表 validateMutation + V5/V7/V9 文案映射）；现价注入（M5/M12）；费率 CRUD 接线（M7/M10）；校准执行流（M8，含多来源提示）；校验宽松口径 UX 复核（M8）；CI 三平台复跑回归待推送。

**建议的下一步**：人工审核 M4 → 通过后解锁下一模块（建议 M5 行情链路：M7/M8 事件构造折算解析依赖 M5 快照/回填；并行面 M10/M11 亦可先行，最终由人工拍板）。

## P4 · M5 行情链路（✅ 已通过--2026-09-04 人工「M5通过」）

> 启动记录：人工原话「执行P4-M5」（2026-09-04）。范围 = task-breakdown M5（T5.1 MarketDataClient（CG/CMC 真实 API）· T5.2 价格快照 · T5.3 24h 盈亏与历史回填 · T5.4 行情调度 · T5.5 行情 Key 设置 UI）。**模块记录：docs/dev/modules/M5.md**（实现摘要 / 文件清单 / 验收清单 / 规格裁决 / 遗留 / **§8 修复轮**）。
> **修复轮（2026-09-04，人工 GUI 验收 3 项反馈）**：① Key 弹窗输入框键盘不可用（与 M2 同根：WzModal 用 Popup）→ WzModal 统一改**就地叠加层** + initialFocusRequester/fieldFocusRequester 首输入框自动聚焦，共性约束固化 AGENTS.md §7.3 + 决策 20；② 未配置/配置后刷新误报「tether 暂无行情数据」→ 根因目录维护失败致币集不可解析且错误被吞 → 编排**上浮目录失败原因**；③ 保存 Key 后仍显示无 Key → 数据源指示改以**当前 Key 配置**为基准 +（尚无刷新）注解。修复后 256 测试 0 失败 + GUI 冒烟无异常；复验步骤 M5.md §8.3。
> **修复轮二（2026-09-04，人工复验：「网络不可达 CoinGecko」误报）**：日志实证根因 = CG 目录 symbol 超 32 字符触发 Exposed varchar 客户端长度校验 → 整批目录回滚 → 空目录；且泛化 catch 误标 Network。修复 = CoinsTable.symbol 改 text（对齐 M004 DDL）+ 内部异常归 `MarketRefreshError.Internal`（不再误报网络）；用户真实 Demo Key 端到端实证全链路 error=null（真实 /coins/list >1 万行落库 + 报价 + 快照，临时测试已删）。258 测试 0 失败（domain 149/data 85/ui 24）+ detekt 0 + 警告 0；复验步骤 M5.md §8.4。

**产物清单（代码 + 测试，全量绿）**：

- **T5.1**：domain/market 契约（MarketDataClient 六端点 + 类型化错误）+ CoingeckoMarketClient/CmcMarketClient（Ktor + OkHttp 引擎，真实端点/头/参数对齐 ADR-003）+ DefaultMarketRefreshService 两级编排（主源失败/缺席 → CMC 兜底 → 保持上次价格；单飞；目录每日一次 + CMC map 对齐 + 市值榜 4×250 预缓存——M3 遗留 3/4 关闭）
- **T5.2**：**M006 price_snapshots（schema 6）**：price TEXT 十进制（SQLite NUMERIC 浮点截断风险勘误，待人工认可回写 data-model §2.11）+ SqlUtc 固定毫秒时间文本 + PriceSnapshotRepository（同小时末条 upsert/批量/桶查询/nearestBefore/90 天降采样保日线，幂等）+ PriceResolution 纯规则
- **T5.3**：TwentyFourHour（固定数量回算 + 覆盖 N/M + "--" + 异源标注，**黄金用例 11 数值通过**）+ DefaultMarketHistoryBackfillService（区间合并 + 桶级幂等 + 429 指数退避单次重试 + HISTORY 计数；回填后空洞可解析 = 「PENDING 消除」后端就绪，事件构造接线归 M7）
- **T5.4**：RefreshCadence（5/15/30 · 托盘降频 · 额度 80% 降一档）+ QuotaPolicy/SettingsQuotaLedger（月额 10,000、仅个人 Key 模式计数、跨月归零、损坏自愈）+ RateBackoff（1s→60s）；**调度循环宿主（窗口可见性/托盘）归 M11**（裁决 §5-5）；状态栏展示归 M12（文案键就绪）
- **T5.5**：方案甲落地——设备密钥条目 device.key（同 DB 密钥降级口径）+ DeviceSecretCipher（AAD purpose）+ 密文存 settings 全局行（market.coingecko_key/market.cmc_key，不进 .cpro）；设置页「行情数据源」分组（原型逐字文案：频率分段选择/CG/CMC 行/Key 弹窗（保存并切换专属额度/移除/注册链接）/数据源指示/上次价格/立即刷新 + 429·额度提示可达）挂载 SETTINGS 页（M10 整页接管）
- 测试：**254 全绿 0 失败**（domain 149 = +25 纯规则含黄金 11/DeviceSecretCipher；data 82 = +33 MockEngine+编排+快照+回填+设置；ui 23 = +6 UI 走查）；detekt 0；编译警告 0；无缓存 clean build 绿
- GUI 冒烟实证：真实 v5 开发库 → **schema=6**（M006 自动应用）、device.key 0600、bootstrap 0 异常、窗口驻留（rc=124）；api-contracts §1/§3 补录实现注

**本次改了什么**：见模块记录 §1（五任务摘要 + §5 规格裁决 8 条，重点：price TEXT 勘误、SqlUtc 时间口径、快照读取口径（24h 桶行配对/nearestBefore 归 M7）、调度宿主拆分、UI 偏差 4 条）。

**怎么验收（人工）**：

1. docs/dev/modules/M5.md §3 验收清单逐项核对；重点走读 §5 规格裁决。
2. 复跑：`export JAVA_HOME=$(mise where java) && ./gradlew clean build detekt`（254 测试 0 失败 + detekt 0 + 警告 0）。
3. 聚焦：`:data:test --tests "com.wuzhufolio.data.market.*"`（33 项）；`:ui:test --tests "com.wuzhufolio.ui.market.*"`（6 项）；domain market（19）+ DeviceSecretCipherTest（6）。
4. GUI 走查（详见模块记录 §4 步骤 4 六项：行状态/弹窗空校验/保存即生效/移除/立即刷新提示/频率持久化）。
5. 可选真实 API 冒烟（联网 + 免费 Demo Key）：刷新后日志「market refresh finished source=…」佐证快照落库。

**遗留问题（转后续模块，详见模块记录 §6）**：事件构造折算解析接线（M7）；调度循环宿主（M11）；状态栏数据源/额度提示（M12）；设置页汇合与下拉/尾号/链接打磨（M10/M12）；真实网络端到端冒烟（P5）；压缩任务执行点接入 M11。

**建议的下一步**：人工审核 M5 → 通过后解锁下一模块（建议按依赖图 **M6 交易所同步** 或并行面 **M10 设置与日志**，由人工拍板）。

## P4 · M6 交易所同步（✅ 已通过--2026-09-07 人工「M6通过」）

> 启动记录：人工原话「执行P4-M6」（2026-09-05）。范围 = task-breakdown M6（T6.1 BinanceAdapter · T6.2 增量同步与去重 · T6.3 sync_logs 与状态 · T6.4 API 管理页）。**模块记录：docs/dev/modules/M6.md**（实现摘要 / 文件清单 / 验收清单 / 规格落档 / 遗留）。
> **关闭记录（2026-09-07）**：人工原话「M6通过」——验收覆盖：① 添加弹窗空校验；② 真实只读 Key 保存即首次同步、**120 笔成交入账**（BNB 歧义根因闭环：排名缓存接入共享目录 + 每轮自动预热）；③ 增量幂等（重复同步「新增 0 ·（增量无新成交）」）；④ 编辑两分支（仅改别名 / 换密钥重包，密钥不回显为安全口径）；⑤ 移除（FK 修复：同事务清 sync_logs，保留账本成交——口径 §8.3）。§8 一轮/二轮/三轮修复全部闭环；298 执行 0 失败（302 报告）+ detekt 0 + GUI schema=9 冒烟无异常。**M6 关闭 ✅。**

**产物清单（代码 + 测试，全量绿）**：

- **T6.1**：domain/exchange 契约（ExchangeAdapter 凭证绑定实例 + ExchangeTrade/Balance/PairInfo + ExchangeError 类型化错误 + ExchangeLimits 预算常量）+ BinanceAdapter（Ktor+OkHttp 独立客户端；X-MBX-APIKEY + 排序参数 HMAC-SHA256；/account 校验+余额、/myTrades 逐 symbol fromId 增量、/exchangeInfo、/time -1021 自动对时重试）——MockEngine 9 项全分支绿（密钥失效/429/-1021/-1022/网络/三端点载荷解析 + 签名参数断言）
- **T6.2**：ExchangeSyncPolicy 纯规则（余额推导 ∪ 已同步 pair 收敛 + ≤120 次调用预算分批）+ DefaultExchangeSyncService（单飞；每 key 一轮：解密→余额→注册表→枚举→增量拉取（sinceId=已同步最大 id）→CoinCatalog.resolve AUTO 冻结→transactions 去重写账本→sync_logs+状态）；**M007 api_keys / M008 sync_logs / M009 transactions（schema 6→9）**，transactions 含部分唯一索引防并发漏重
- **T6.3**：sync_logs 写读（LogRedactor 兜底 + 计数摘要 message，禁密钥/完整响应体）；api_keys.last_sync_time/status OK/FAILED；API 管理页同步中指示 + 最近同步记录
- **T6.4**：ui/exchange ApiManagementPage + VM + ApiCopy（列表/添加弹窗（测试请求→校验→保存即首次同步）/移除/立即同步/间隔 15/30/60/同步记录）；SettingsSectionsHost 挂载（行情数据源 / API 管理 两段，默认行情保持 M5 首屏；M10 接管后移除）；向导「关联交易所 API」由占位预告改真实路径（M2 遗留替换）
- 接线：AppBootstrap 共享 ActiveSessionStore + ExchangeServicesBundle（字段加密用账户 DEK）；Main 设置页由 MarketSettingsPage 单页改为 SettingsSectionsHost 组合
- 测试：domain 156 + data 99 + ui 35 = **294 执行 0 失败（298 报告，4 跳过待 CI）**（exchange：domain 7 / data 17 / ui 7）+ detekt 0 + 编译警告 0；无缓存 clean build 绿
- GUI 冒烟实证：真实 v6 开发库 → **schema=9**（M007/M008/M009 自动应用）、0 异常、窗口驻留（rc=124）；api-contracts §2/§3 + data-model §2.5/2.11/5 补录与回写

**怎么验收（人工）**：① docs/dev/modules/M6.md §3 逐项打勾（重点走读 §5 规格落档 8 条：新增表编号/price TEXT 勘误回写/成交 id 去重口径/增量游标/UI 挂载偏差/别名唯一/未命中处置/两类 API 隔离）；② 复跑 `./gradlew clean build detekt`（294 执行 0 失败（298 报告，4 钥匙串真实后端跳过待 CI win/mac））；③ GUI 走查（详见模块记录 §4 步骤 4：API 管理空态→添加弹窗首输入框聚焦+键盘录入→空校验/B2 文案→保存即首次同步 toast→立即同步/间隔持久化→行情数据源分组回切）；④ 可选真实 Binance 只读 Key 冒烟（P6 联调项）。

**遗留问题（转后续模块，详见模块记录 §6）**：同步定时循环宿主随 M11（15/30/60 间隔读写已备）；设置页汇合（SettingsSectionsHost 移除）随 M10；transactions 表已建（M009），M7 手动/CSV 复用 + 事件构造层接线；真实 Binance 端到端冒烟留 P5；api_keys 备份去重/DEK 重加密随 M9；CI 三平台复跑待推送。

**建议的下一步**：人工审核 M6 → 通过后解锁 M7 交易管理（transactions 表已就绪）或并行面 M10 设置日志，由人工拍板。

## P4 · M7 交易管理（✅ 已通过--2026-09-08 人工「M7 通过。」关闭）

> 启动记录：人工原话「执行P4-M7」（2026-09-07）。范围 = task-breakdown M7（T7.1 手动增删改 · T7.2 手续费自动计算 · T7.3 CSV 导入/模板 · T7.4 交易页+表单+CSV UI）。**模块记录：docs/dev/modules/M7.md**（实现摘要 / 文件清单 / 验收清单 / 规格落档 10 条 / 遗留）。
> **关闭记录（2026-09-08）**：人工原话「这个功能保留下来，文档要同步留痕。M7 通过。」——
> 验收覆盖：GUI 走查四轮反馈全部闭环（§7.1–7.8：模板下载线程崩溃修复 / 弹窗裁切与保存双通道反馈 /
> 手续费 0 / 计价币检索 / 费率最小 CRUD / 名义折算保重放链 / 命令区固定 / API 页面级同步 / **顶栏常驻手动同步（保留）**）；
> 120 笔真实同步成交于交易页可见；全量测试 367 项 0 失败（domain 160 / data 144 / ui 63）+ detekt 0 +
> 无缓存 clean build 全绿 + GUI 冒烟 schema=10 无异常。**M7 关闭 ✅，M8/M10 解锁待人工启动指令**。
> 里程碑门槛（M7–M8）：interaction V 系全过 + 黄金用例 2/3/4/5/6/8（本模块覆盖 5/6，其余归 M8）。

**产物清单（代码 + 测试，全量绿）**：

- **T7.1 手动增删改**：domain/ledger 契约（TransactionLedgerService + 视图模型 + LedgerErrorCode/LedgerValidationException/CoinResolutionException + ReplayConflictClassifier 纯规则）；事件构造层交易半边（DB 行 -> TradeEvent：FK->cg_id、折算价解析、PENDING 标记——M4 遗留 1/7 接线）；相对校验（validateMutation 双列表）+ V5/V7/V9 违例分类映射；LedgerTransactionRepository（全量/筛选/增删改/精确+模糊去重）
- **T7.2 手续费自动计算**：**M010 fee_rules（schema 9→10）** + FeeRuleRepository（rate 存 TEXT 勘误链）；表单「自动计算手续费」= FeeRateResolver（交易所>全局）+ FeeCalculator 三币种基数 + 现价折算；CRUD UI 归 M10
- **T7.3 CSV 导入/模板**：CsvTradeParser（模板/别名表头/UTC/行级 V1/V2/歧义上浮/拼接符号不猜测）；parseCsv 预览（新增/疑似重复[精确+模糊]/未解析/影响摘要+负持仓「持仓异常」预告）-> confirmCsvImport（歧义固化 MANUAL -> 去重复核 -> LENIENT 导入 -> 异常币种清单）；标准模板下载；**黄金用例 6 形状覆盖**
- **T7.4 交易页+表单+CSV UI**：ui/ledger 五文件（Copy/VM/FormModal/CsvModal/Page）+ 卖出逐笔已实现盈亏 SellRealizedTracer（展示口径，引擎交叉校验守护）；MainShell/AuthGate 槽位 + Main FilePicker + AppBootstrap LedgerServicesBundle
- 测试：domain 4 + data 28 + ui 7 = **新增 39 项全绿**；存量回归 schema 断言 9->10（M010）；无缓存 clean build detekt 全绿 + detekt 0 + 警告 0
- GUI 冒烟实证：真实 v9 开发库 -> **schema=10**（M010 自动应用）、0 异常、窗口驻留（RC=124）

**本次改了什么**：见模块记录 §1（四任务摘要 + 事件构造层交易半边 + 卖出轨迹 + §5 规格落档 10 条，重点：M010 fee_rules 表 + rate TEXT 勘误、V5/V7 严格校验与「构建初始持仓」张力待裁决、法币 ThirdCurrency MVP 不支持、折算价解析顺序、卖出轨迹展示口径、手动/CSV 写入口径）。

**怎么验收（人工）**：① docs/dev/modules/M7.md §3 逐项打勾（重点走读 §5 规格落档 1–10）；② 复跑 clean build detekt（应全绿，命令见模块记录 §4）；③ 聚焦 domain/data/ui 的 ledger 测试（4/27/7 项，命令见模块记录 §4）；④ GUI 走查（详见模块记录 §4 步骤 4：**交易管理页应看到 M6 同步入账的 120 笔成交**、卖出行已实现盈亏、添加弹窗首输入框聚焦+键盘录入+实时总价+手续费自动计算、编辑/删除确认/重放校验 V5/V9 文案、CSV 导入向导全流程、搜索/筛选/双主题）。

**遗留问题（转后续模块，详见模块记录 §6）**：手动买入需先有持仓的 UX 引导（M8 增资后自然解决 / M12 空态引导）；费率 CRUD UI（M10 T10.1）；设置页汇合（SettingsSectionsHost 移除，M10）；CSV 逐条确认 UX 打磨（M10/M12）；真实 Binance 端到端冒烟留 P5；CI 三平台复跑待推送；目标机 KDF 复核开放待办（M13 前）。

**修复轮（2026-09-07，人工 GUI 走查 11 项反馈）**：① **问题 8 崩溃修复**——文件选择器在 EDT 上调用
invokeAndWait 抛异常致进程退出（VM 在协程外同步调用 + FilePicker 未判线程）→ VM 移入协程 + FilePicker 按线程分流；
② **问题 3/5/6/10 同根链**——弹窗单列超高被裁切且无滚动条，校验错误不可见 → 表现为「保存无反应/编辑不生效」；
修复 = 表单紧凑两列 + 弹窗可见滚动条 + 保存失败双通道（内联错误 + toast）；③ 计价币接入目录检索（问题 4）；
④ 设置页新增「手续费」分组最小 CRUD（问题 7，domain FeeRuleService + data DefaultFeeRuleService + ui
FeeRuleSettingsSection）；⑤ **折算价缺失改名义折算参与重放**（问题 1：原「排除行」使其后卖出失去成本基数，
已实现盈亏大面积 "--"；名义折算保数量链、标估算中）；⑥ 交易页命令区固定、仅列表滚动（问题 2）；
⑦ V5 文案改可操作引导（问题 9）。新增 13 项回归测试，clean build detekt 全绿 + GUI 冒烟 schema=10 无异常；
详见 docs/dev/modules/M7.md §7。

**第二轮走查反馈（2026-09-08，3 项）**：① API 管理页零改动（核查 git diff）——「变少」实为 M6 验收移除
测试 Key 时同事务清掉了该 key 的 sync_logs（M6 §8.3），重加 Key 即恢复；② 模板 time 列经 Excel 改写后
报格式错误 → parseTime 容错扩展（宽松日期/中文日期/epoch/Excel 序列等 6 类，错误文案列出支持格式）；
③ 删除买入被其后卖出拦住属 PRD V9 正确行为 → 冲突提示升级为定位到具体记录（时间/方向/交易对/数量）。
新增 13 项回归测试（解析 9 形态 + 导入链路 + 冲突定位 + 文案），clean build detekt 全绿；见 M7.md §7.6。

**第三轮走查反馈（2026-09-08，1 项）**：API 管理页「同步按钮不见了」——核查该页零改动，「立即同步」一直在
每个密钥行内（删 Key 后列表为空故不可见）。按 PRD 故事 4.3「提供手动同步按钮」补**页面级「立即同步（全部密钥）」**
（无密钥给引导提示、有密钥汇总新增数、与行级互斥）；**定级 C0 实现补全**（不改语义/数据/接口，不建决策档不进台账）；
新增 2 项 UI 回归测试，clean build detekt 全绿；见 M7.md §7.7。

**第四轮走查反馈（2026-09-08，1 项）**：手动同步入口需随手可用 → 主壳顶栏新增常驻「立即同步」按钮
（PRD 故事 4.3 + ia.md 顶栏规范；同步全部密钥、同步中指示、结果汇总 toast、无密钥引导），与设置页
「立即同步（全部密钥）」并存；**C0 实现补全**（不建决策档/不进台账）；+5 项回归测试，clean build detekt 全绿；
见 M7.md §7.8。

**建议的下一步**：人工按 M7.md §4 重跑 GUI 走查（重点复验修复轮 6 项）→ 通过后解锁 **M8 资金管理**
（增资/撤资/校准，V5 依赖的持仓建立路径 + M4 遗留资金事件构造层）或并行面 **M10 设置日志**
（费率 CRUD 扩展 + 设置页汇合），由人工拍板。

## P0 需求基线（✅ 已通过--两端 + 跨端规范全部定稿）

产物清单（只读基准，不得改动）：

- `docs/prd/桌面端prd.md`（**V1.9 定稿**）
- `docs/prd/跨端共享规范.md`（**V1.0 已通过**）
- `docs/prd/移动端prd.md`（**V1.2 已通过**）
- `docs/prd/移动端SRD.md`（**V1.1 已通过**）
- `docs/prd/桌面端prd评审报告.md` / `V2` / `V3`（**17 项全部闭环**）
- `docs/prd/移动端prd评审报告.md`
- `docs/prd/移动端对齐评审报告.md`（**10 项全部核销**）
- `docs/prd/桌面端prd-三大决策分析.md`
- `docs/prd/桌面端prd-币种标识与行情源决策分析.md`
- `docs/prd/桌面端prd-法币方案决策分析.md`

## 已决策事项（D 系编号；全量索引见 docs/dev/decisions/决策索引.md）

> 编号约定（2026-09-04 确立，AGENTS.md §8.6）：D1 开源为历史既定编号（本列表第 4 条）；D16–D22 按「D-N = 本列表第 N 条」；第 1–3、5–15 条为 P0 期事件型决策（无 D 号，权威见交接记录/评审报告/§7.2）；从 D23 起连续编号。

1. **推进顺序**：先桌面端，后移动端。
2. **P1 暂缓**：P0 需求基线收尾完成前，暂不启动 P1（产品与交互设计）。
3. **桌面端 PRD 定稿（V1.9，2026-08-26）**：`桌面端prd评审报告V3` 全部 17 项闭环（T1–T14 关闭；T15–T17 🟢 落地；T3 剩移动端同步）。
4. **D1 开源决策（2026-08-26）**：开源--源码公开、GitHub Releases 分发、**AGPL-3.0** 许可证。
5. **《跨端共享规范》V1.0（2026-08-26）**：自桌面端 PRD V1.9 抽取 8 节，两端共同引用；**已通过**。
6. **移动端对齐评审（2026-08-26）**：产出《移动端对齐评审报告》（V1.1->V1.2），识别 5 🔴 + 5 🟡/🟢 类差距。
7. **移动端 PRD 修订至 V1.2（2026-08-26）**：按对齐评审报告全部核销--法币退出账本、coin_id 主键 + 新增 4 表、行情两级 Key + CMC 兜底、校准方案甲、.cpro 跨端统一 + 整库加密、指标外部度量、24h 缺价 + 无障碍、Binance 收敛、残留清理、打磨包；顶部声明引用《跨端共享规范》V1.0；**已通过**。
8. **移动端 SRD 修订至 V1.1（2026-08-26）**：第 14 章数据模型同步（coin_id 主键、新增 coins/exchange_coin_map/reconciliation_records/sync_logs/fee_rules/price_snapshots 表、accounts/api_keys 补字段、settings 改 key-value）、加密方案改为 Argon2id 分层密钥 + 整库加密 + 凭证字段级 DEK、.cpro 备份/恢复语义对齐、法币退出账本、行情两级 Key；第 5 章功能需求相关矛盾项一并修正；**已通过**。
9. **需求文档策略（2026-08）**：桌面端**不设独立 SRD**（PRD + 跨端共享规范 + 技术方案即可）；移动端**保留 SRD** 作为工程追溯锚点；跨端规则以《跨端共享规范》为准；P2 技术方案产物必须可回溯到需求基线（PRD 章节号 / 共享规范条款号 / SRD 功能需求编号）。
10. **范围决策（2026-08-30）**：**P1–P8 只针对桌面端或两端共同部分**；移动端相关工作（原型、技术方案、开发）放到下一个版本，本阶段一律不启用。
11. **引入 huashu-design（2026-08-30）**：项目级安装 `.agents/skills/huashu-design/`（MIT），用于 HTML 高保真原型/图例/演示；调研与 P1–P8 用途映射见 `docs/dev/huashu-design-调研报告.md`。
12. **P1 工作内容修订（2026-08-30）**：新增「设计原型图」步骤，**原型输出为 HTML**（单文件、可点击、可交互），落 `docs/design/prototype/`；`design-tokens.md` 仅桌面端，移动端 Material 3 归入下一版本。
13. **P1 三方向初稿可简化（2026-08-30）**：PRD 已定稿、方向明确，先出信息架构文字稿、原型出 1 主版 + 变体（不强制三版并排）；简化/豁免记入 `docs/design/direction-approved.md`。
14. **P2 图例选型（2026-08-30）**：写在 md 文档内 -> 优先 Mermaid；独立文件 -> 优先 huashu-design HTML（表达力更丰富）。
15. **P7 做产品宣传动画（2026-08-30）**：作为 P7 必做项（非加分项），用 huashu-design 动画链产出 MP4/GIF。
16. **单一真源 + 双主题（2026-08-31，人工确立）**：wuzhufolio-light.html 为唯一原型真源，暗色主题为其内置档位、随深化自动维护；原 dark.html/dark.png 已删除；F4 对比度修正须两套主题分别验证；P4 暗色视觉基准 = light.html 暗色档位（详见 direction-approved.md §7）。
17. **桌面端技术栈 = Kotlin + Compose Desktop（2026-08-31，人工拍板）**：废弃 P2 原 Tauri 2 建议；脚手架可采官方 compose-multiplatform-desktop-template / KMP 向导；行情客户端须基于 CoinGecko/CoinMarketCap 真实 API；其余方案全面适配 Kotlin 栈（ADR-001 已按此重写）。
18. **P2 技术方案拍板通过（2026-08-31，人工）**：① SQLCipher 驱动选型 = Willena/sqlite-jdbc-crypt，P3 三平台验证并锁版，失败回退自维护 JNI 绑官方 sqlcipher（回退路径随拍板确认）；② Flatpak 口径 = 本轮先 AppImage/.deb/.rpm，Flathub manifest 并行推进、P7 前评估是否为硬门槛；③ 任务优先级/顺序 = 按 F3 垂直切片依赖图（M4 引擎先行全绿黄金用例为硬前置）。ADR-001~006 全部转为「人工拍板采纳」；ADR-002 增补 Kotlin 亲和存储栈评审（Room KMP 无桌面端 SQLCipher、Realm 违反 SQLite 约束、SQLDelight 不降低驱动风险——均否决，维持现有方案）与加密严苛度评审（匹配定位；「记住我」令牌语义整改）。**P2 关闭，P3 解锁**。
19. **开发环境工具链 = mise（2026-08-31，人工）**：开发基准 = WSL2 + Ubuntu 24.04；SDK 优先用已安装的 mise 管理——JDK 17 = `mise use java@temurin-17`（`.mise.toml` 入库）；Gradle 以仓库 Wrapper 为唯一真源（不装全局 gradle）；Kotlin 由 Gradle 插件驱动（不单独安装）；detekt/ktlint 等 CLI 同入 `.mise.toml`；CI 用 setup-java temurin-17 对齐；GUI 冒烟走 WSLg，托盘以三平台 runner + 实机验证为准。已写入 task-breakdown T0.2/T0.3。
20. **GUI 共性约束（2026-09-04，人工验收确立）**：M2（密码弹窗）与 M5（Key 弹窗）同根缺陷（桌面端 Popup 无法可靠接收键盘输入）→ 固化 `AGENTS.md §7.3`：弹窗一律同窗口就地叠加（WzModal 已统一，禁 Popup 承载交互）、含输入框弹窗传 initialFocusRequester 聚焦首输入框、模块验收必须含 Compose UI 测试 performTextInput+assertIsFocused 与人工键盘录入复验。
21. **V1.9 范围增补：正式「行情」页（2026-09-04，人工拍板）**：行情刷新后无查看途径（M5 验收反馈）→ 侧边栏第六页「行情」；只读现价列表 + **持久化自选**（settings 全局行 watch.coins，默认种子 = 稳定币白名单）；搜索 coins 目录添加、移除、手动/自动刷新；无图表/交易（Out of Scope 维持）。决策档 docs/dev/decisions/D21-行情浏览页-范围增量.md；ia.md 页面清单已同步（2.19）；PRD 正文只读不改。**下游回填（2026-09-04，变更控制流程确立后）**：data-model §2.3（watch.coins）、api-contracts §3（MarketWatch/Quotes 补录）、task-breakdown T5.6 + M12 页数 18→19、interaction §2.7（行情页异常态）；已登记 `docs/dev/增量台账.md`（级别 C1）。prototype 已补齐（2026-09-04：第六页 + verify 断言，M12 债提前还）。
22. **变更控制流程（2026-09-04，人工拍板「全做」）**：PRD 锁定后的变更按 **C0 勘误 / C1 增量 / C2 大修改** 分级处置——C0 不建决策档不进台账；C1 = 决策档 + 台账登记 + 下游文档回写 + 代码（DoD 五件套）；C2 = mini-P0/P1/P2 闭环（升 C2 红线五条）。**有效需求 = PRD 定稿版 + `docs/dev/增量台账.md`**；固化为 `AGENTS.md §8`。
23. **变更控制硬化 + 决策归档（2026-09-04，人工拍板「全做」）**：§8 补判定流程 / 轻量修改 / 删除工作流 / 影响面扫描 / 双向入口 / amend 链 / 编号与有效需求版本（新增 §8.5/§8.6）；建 `docs/dev/decisions/决策索引.md`（D1–D22 全量索引 + 编号约定固化）；回溯式回填第一梯队 5 条活约束决策档（D1/D16/D17/D18/D20）。

## 当前阻塞点

- **P4-M6 人工复验数据点（2026-09-07）**：三轮修复后立即同步实测「同步成功 · 新增 120」——BNB 歧义消解确认、成交已入 transactions（source=BINANCE API，去重幂等）；窗口可见列表随 M7 交易管理页（当前侧边栏「交易管理」仍为占位）+ M12 聚合呈现；幂等自查 = 再点一次立即同步应显「新增 0 · 去重跳过 120」。
- **P4 下一启动点（2026-09-08）**：M7 已通过关闭；下一模块 = **M8 资金管理**（建议：增资/撤资/校准，V5 依赖的持仓建立路径 + M4 遗留资金事件构造层）或并行面 **M10 设置与日志诊断**（费率 CRUD 扩展 + 设置页汇合 + 日志诊断），待人工下达启动指令。

## 技能盘点结论（2026-08-30 更新）

- 已评审：原挂载的 22 个 Android 平台技能对桌面端（当前优先）无用、对移动端仅部分可用、约 1/3 与本产品无关，已全部删除。
- **现状**：已项目级安装 **huashu-design**（`.agents/skills/huashu-design/`），覆盖设计/原型/可视化（P1 必用、P2/P7 推荐、P5/P6/P8 可选）。
- **现状与缺口**：
  - 桌面端阶段：设计/原型由 huashu-design 覆盖；工程技能靠 `AGENTS.md` 流水线 + `docs/tech/` 方案推进；待技术栈确定后按需补专属技能（OS 钥匙串、托盘、`.cpro` 加密备份、签名/公证等）。
  - 移动端阶段：待下一版本启动前再按需补 Android 相关技能。
  - 项目领域知识（行情/交易所 API、加密备份、ROI/成本计算）以 `docs/` 产物为准，必要时再沉淀为项目专属技能。

## 交接记录

| 时间 | 谁 | 动作 | 说明 |
|------|----|------|------|
| 2026-08 | 人 | 通过 P0 | PRD/SRD 及评审定稿 |
| 2026-08-26 | Agent | 桌面端 PRD 修订至 V1.9 | 闭环 V3 报告全部 17 项（含 T1 校准语义、T2/T13 行情 Key 两级模式、T5–T8 备份/加密/并发、T10–T17 边界/无障碍/发布/黄金用例/竞品），待定稿审核 |
| 2026-08-26 | 人 | 拍板定稿 + D1 | 桌面端 PRD V1.9 定稿；D1=开源（GitHub Releases + AGPL-3.0） |
| 2026-08-26 | Agent | 建立《跨端共享规范》V1.0 | 自桌面端 PRD V1.9 抽取 8 节，两端共同引用；待审核 |
| 2026-08-26 | 人 | 通过《跨端共享规范》V1.0 | 授权启动移动端对齐评审 |
| 2026-08-26 | Agent | 产出《移动端对齐评审报告》 | 识别移动端 V1.1 与桌面端 V1.9/共享规范的 5 🔴 + 5 🟡/🟢 差距，待审核后启动 V1.2 修订 |
| 2026-08-26 | 人 | 通过对齐评审报告 | 指令按计划执行移动端 V1.2 修订 |
| 2026-08-26 | Agent | 移动端 PRD 修订至 V1.2 | 对齐评审报告 10 项全部核销（法币退出/coin_id 主键+4 表/两级 Key+CMC 兜底/校准方案甲/.cpro 跨端统一+整库加密/指标外部度量/无障碍/Binance/残留/打磨包），待审核 |
| 2026-08-26 | 人 | 通过移动端 PRD V1.2 | 指令继续执行 SRD 数据模型同步 |
| 2026-08-26 | Agent | 移动端 SRD 修订至 V1.1 | 第 14 章数据模型同步 + 加密/.cpro/法币/行情语义对齐 + 第 5 章矛盾项修正，待审核 |
| 2026-08-26 | 人 | 通过移动端 SRD V1.1 | **P0 全部收尾完成**（两端 PRD/SRD + 共享规范 + 评审报告闭环）；P1 待启动指令 |
| 2026-08-30 | Agent | 引入 huashu-design + 修订 P1 范围 | 项目级安装 huashu-design；P1 新增「设计原型图」步骤（HTML 输出）；完成 P1–P8 用途分析；范围决策（P1–P8 仅桌面端，移动端下一版本），**待审核** |
| 2026-08-30 | 人 | 下达 P1 启动指令 | 「开始执行P1」--关闭前序待审核门，P1 置为「进行中」，授权产出设计稿 |
| 2026-08-30 | Agent | P1 首轮设计稿 | 产出 ia/flows/interaction/design-tokens + 2 个可交互 HTML 原型（主版暖纸浅色 / 变体墨炭深色）+ Playwright 验证（pageerror=0）；停在人工门，待审核 |
| 2026-08-30 | 人 | 选定主版 + 3 条反馈 | 「选主版」；反馈：① 行情 Key 设置流程 ② 资金币种改单选 ③ 交易对/手续费币种选择方式 |
| 2026-08-30 | Agent | 按反馈深化主版 | 补行情 Key 配置 Modal、币种改下拉单选、交易对自动补全 + 手续费自定义币种联动；Playwright 复验 pageerror=0；变体归档 |
| 2026-08-31 | Agent | P1 产物评审 | 产出 docs/design/P1评审报告.md（Playwright 独立复跑 + PRD 覆盖矩阵 + WCAG 对比度实测 + Mermaid 解析校验 + 依赖扫描）；结论「有条件通过」（1 阻断 + 5 重要 + 7 轻微）；P1 维持待审核 |
| 2026-08-31 | Agent | P1 二次评审 | 产出 docs/design/P1评审报告V2.md：深化三项独立复验全部通过、零回归，F6 闭环；F1 仍为唯一阻断；发现 N1–N5（含暗色主题一致性专项） |
| 2026-08-31 | 人 | 下达修复闭环指令 | 三项指令：① 补齐 F1 登录链路 4 页（可一并落 F2/F3/F4）；② 解决 V1/V2 全部问题；③ 确立「单一真源 + 双主题」原则（dark.html 删除或标注、对比度两主题分别验证） |
| 2026-08-31 | Agent | P1 修复闭环轮 | 补齐登录链路 4 页（F1）+ F2–F12 全处置 + N1/N2/N5 订正修复；删除 dark.html/dark.png；演示数据改全量重放引擎（黄金用例对齐）；48 项 Playwright 断言全绿（脚本入库 prototype-verify.js）；产出 P1评审报告V3.md（建议放行）；停在人工门，**待终审** |
| 2026-08-31 | 人 | **终审通过 P1** | 「P1人工终审通过」--P1 关闭（产物全清单见 P1 节）；**P2 技术方案解锁为「进行中」**，待启动指令 |
| 2026-08-31 | Agent | **产出 P2 技术方案** | 人下达「开始执行P2」；产出 architecture + 6 ADR + data-model + api-contracts + task-breakdown（全带需求回溯）；P2 置「待审核」，停在人工门待拍板选型与顺序 |
| 2026-08-31 | 人 | 下达 P2 三条修订指令 | ① 桌面端改 Kotlin + Compose Desktop（有脚手架可采）；② 行情客户端基于 CG/CMC 真实 API；③ 其余全面适配 Kotlin 栈 |
| 2026-08-31 | Agent | P2 按指令修订 | ADR-001 重写为 Kotlin + Compose Desktop；ADR-003/api-contracts 落 CG/CMC 真实 API；ADR-002/004/005/006 + architecture/api-contracts/task-breakdown/data-model 全面适配 Kotlin；P2 保持「待审核」 |
| 2026-08-31 | Agent | P2 独立评审 | 产出 docs/tech/P2评审报告.md：全量通读 + 回溯抽查 15 处全真实 + CG/CMC/Binance 外部事实核验；结论「有条件通过」（F1 阻断 + F2/F3 重要 + N1–N5 轻微）；P2 维持「待审核」，停在人工门 |
| 2026-08-31 | 人 | 拍板评审处置 | F1 选方案甲（设备密钥加密 + 不进备份）；F2/F3 处置结论采纳并入文档；指令修订 |
| 2026-08-31 | Agent | P2 评审修订轮 | F1 方案甲落地（ADR-002 §2.1 等 7 处）、F2（ADR-004 §3.1 + api-contracts + T6.2）、F3（task-breakdown 垂直切片 + T0.6 + architecture §4）、N1–N5 一并修订；P2 维持「待审核」，待人工终验 |
| 2026-08-31 | 人 | 技术口径确认 | 托盘/通知/自启不绑定 AWT（Compose Tray API 首选）；「打包本地 exe 脱离系统 Java」= jpackage 捆绑私有 JRE 既定方案，确认无冲突；指令落盘 |
| 2026-08-31 | Agent | 托盘与分发模型口径落盘 | ADR-001 托盘行/分发行/风险行三处修订；ADR-006 新增 §1.1 原生运行时分发模型（排除 GraalVM）；architecture §2.4 同步；P2 维持「待审核」 |
| 2026-08-31 | 人 | 问询 + 拍板 P2 | 问询：① ADR-002 有无更亲和 Kotlin/Compose 的存储栈 ② 加密严苛度是否匹配；拍板：按建议处理 SQLCipher 驱动选型 / Flatpak 口径 / 任务优先级顺序 |
| 2026-08-31 | Agent | ADR-002 评审增补 + 拍板落盘，P2 关闭 | 亲和栈评审（Room KMP/Realm/SQLDelight 否决，维持现有方案）+ 严苛度评审（匹配定位，「记住我」语义整改）；三项拍板落盘（ADR-002~006 转人工拍板采纳、ADR-006 Flatpak 口径、task-breakdown 顺序拍板）；**P2 ✅ 已通过，P3 解锁进行中（待启动指令）** |
| 2026-08-31 | 人 | 开发环境指令 | 环境 = WSL2 + Ubuntu 24.04 + DSH；安装 SDK 优先用已安装的 mise 管理 Java/Kotlin 等工具 |
| 2026-08-31 | Agent | 环境摸底 + T0.2/T0.3 修订 | 实测：mise 2026.7.13 在用，Java/Kotlin/Gradle 均未安装，WSLg 可用；T0.3 改 mise 工具链 + 环境检查清单 + WSL2 注记，T0.2 CI JDK 对齐 temurin-17；决策 19 落档 |
| 2026-09-01 | Agent | **执行 P3 工程脚手架** | 人下达「执行P3」；完成 M0 全任务：git 建仓首提交 6ed10fd（85 文件）、四模块 Gradle 工程 + version catalog 锁版、CI 三平台、mise/dev-setup、hello 链路脱敏实测、迁移框架 v2、UI 基座双主题 + 组件走查页；17 测试绿 + 打包冒烟通过；许可证清单全兼容 AGPL-3.0；**P3 置待审核，停在人工门** |
| 2026-09-01 | Agent | **P3 独立评审** | 人指令「评审P3」；产出 docs/tech/P3评审报告.md：DoD 三项独立复跑实证达成（无缓存 21 测试、全新克隆构建证实 CI 根因修复、CI 三平台绿、hello 链路 GUI 实测脱敏）；结论「有条件放行」——F1（dev-setup §4 密钥口径订正）+ N1–N5 转 P4 注意清单；人工复核项：GUI 目视中文渲染 + Kotlin 版本答复；**P3 门禁维持待复核，放行权在人工** |
| 2026-09-01 | Agent | **P3 评审修复闭环** | 人复核通过（中文渲染 ✓、Kotlin 2.4.10 维持 ✓）；按评审 F1/N1–N5 修复：dev-setup 密钥口径订正（对齐 ADR-002 §2.1）、版本口径 8.14.4、STATUS CI 描述对齐 ci.yml、§BT§ 转义清理、编译警告清零（app 补 lifecycle 依赖 + ShellUiTest 迁 v2 API）、N5 登记；全量无缓存重跑 21 测试绿 + 警告 0；**P3 具备放行条件，待人工确认通过并解锁 P4** |
| 2026-09-01 | 人 | **通过 P3** | 原话「P3 通过」——P3 ✅ 已通过；P4 解锁为进行中，待人工「执行P4」启动指令（首个模块 = M1 存储与加密） |
| 2026-09-02 | Agent | **执行 P4-M1 存储与加密** | 人指令「执行P4」；完成 T1.1–T1.4：SQLCipher 锁版实证（org.sqlite.mc raw-key 注入 + WAL/busy/foreign_keys 每连接生效 + 重启解锁 + 错钥类型化）、CryptoService（BC Argon2id 纯 JVM + DEK/KEK 包解包 + 字段级 AAD 隔离 + 擦除）、KDF 基准校准（冻结 64MiB/t3/p1，目标机复核转 M2）、DbGate 单写队列（并发测试绿）、钥匙串/文件降级 + 安全提示模态 + FatalWindow；63 测试绿 + detekt 0 + 警告 0 + GUI 冒烟实证；模块记录 docs/dev/modules/M1.md；**P4 维持进行中，M1 置待审核，停人工门** |
| 2026-09-03 | Agent | **M1 修复轮 + 验收辅助** | 评审实测触发两缺陷并修复：① M0 明文开发库不兼容 → 归档处置 + 验收步骤 0；② 成功路径无窗口静默退出（runCatching.getOrElse 推断 Any → when is Ready 永不命中）→ 显式收敛密封 Outcome（7fff251）；CI 三平台绿；master.key 步骤 4 验后还原并复验；STATUS 步骤 5 内嵌复跑命令 | 
| 2026-09-03 | 人 | **通过 M1** | 原话「M1 通过」——M1 ✅ 已通过；M2 账户与会话解锁为进行中，待人工「执行P4-M2」启动指令（T2.1–T2.5，登录链路按原型还原） |
| 2026-09-03 | Agent | **执行 P4-M2 账户与会话** | 人指令「执行P4-M2」；规格提炼（UX/数据双文档）→ T2.1 M003+仓库+用例（单次 KDF/快速判错/GCM 认证/A1）→ T2.2 记住我（令牌+wrapped_dek_session+探针语义+降级文件）→ T2.3/T2.4 切换严格模式/改密重包回登录 → T2.5 登录链路六件套+门控宿主+账户菜单（差异 6 条待裁决）；85 测试绿 + detekt 0 + GUI 冒烟；模块记录 docs/dev/modules/M2.md；**P4 维持进行中，M2 置待审核，停人工门** |
| 2026-09-03 | Agent | **M2 验收修复轮** | 人工实测 4 缺陷（密码明文/空提交两错/toast 闪现/busy 泄漏+弹窗键盘不可用）→ 修复轮 ce0b6c9：WzTextField isPassword 掩码、空提交三错、阶段迁移保 toast 清 busy、InPlaceModal 同窗叠加（键盘/Esc 可用）；复验通过；增 KeyringRememberMeStoreTest |
| 2026-09-03 | 人 | **通过 M2** | 原话「M2 通过」——M2 ✅ 已通过；M3 币种主数据解锁为进行中，待人工「执行P4-M3」启动指令（T3.1–T3.3） |
| 2026-09-03 | Agent | **P4-M1 独立评审** | 人指令「评审P4完成的工作」；全量通读 M1 改动（38 文件）+ 无缓存全量重跑（30 任务绿、63 测试 0 失败 2 跳过、detekt 0、警告 0）+ 需求回溯抽查 8 处全命中；产出 docs/dev/modules/M1评审报告.md——**有条件通过**：0 阻断、1 重要（F1 目标机 KDF ≤2s 未实测转 M2 门）、5 轻微（N1–N5）；**M1 门禁维持待审核，放行权在人工** |
| 2026-09-03 | Agent | **执行 P4-M3 币种主数据** | 人指令「执行P4-M3」；M004 coins（v4）+ M005 exchange_coin_map（v5）+ contracts 勘误列；SqlCoinCatalog（检索/目录每日缓存 upsert/cmc_id）；CoinResolver 四级消歧（上下文/合约/排名/候选）+ AUTO 固化/MANUAL 冻结复用；FiatNormalizer（USD→USDT、EUR→EURC 孪生表 + 第三币种）；129 测试 0 失败 + detekt 0 + 警告 0 + GUI schema=5 迁移实证；模块记录 docs/dev/modules/M3.md；**P4 维持进行中，M3 置待审核，停人工门** |
| 2026-09-03 | 人 + Agent | **M3 核对②通过 + data-model 勘误回写** | 人工核对「抽查 DDL 与 data-model 一致性」通过并认可 contracts 勘误；按指令回写 data-model §2.9（contracts 列 + 溯源注记）；M3.md §3-8/§5-1 落档 |
| 2026-09-03 | 人 | **通过 M3** | 原话「T3验收通过。1235测试通过。4未测试」+ 确认「确认 M3 通过，解锁 M4」——M3 ✅ 已通过（步骤 4 GUI 冒烟·可选未复测，Agent 冒烟证据在案不阻断）；contracts 勘误已回写 data-model §2.9；M4 计算引擎解锁为进行中，待「执行P4-M4」启动指令 |
| 2026-09-04 | Agent | **执行 P4-M4 计算引擎** | 人指令「执行P4-M4」；domain/engine 七文件（LedgerModels/ReplayEngine/PortfolioCalculator/FeeCalculator/ReconciliationService/LedgerMath/EngineErrors）+ api-contracts §3 补录；四任务实现摘要见 docs/dev/modules/M4.md；**黄金用例 1–9、12 全绿** + 引擎语义补充测试 61 项（domain 63→124）；无缓存 clean build 190 测试 0 失败 + detekt 0 + 编译警告 0；§5 规格裁决 10 条（含第三币种手续费联动扣减勘误裁决）待人工走读；模块记录 docs/dev/modules/M4.md；**P4 维持进行中，M4 置待审核，停人工门** |
| 2026-09-04 | 人 | **通过 M4** | 原话「M4.md §5 规格裁决 按建议的来执行。其他未决项按建议执行」——§5 规格裁决 10 条全部按建议采纳生效（代码已按裁决实现）；M4 ✅ 已通过；未决项按建议执行（遗留路由 M5–M9、校验宽松口径复核归 M8、下一步解锁）；M5 行情链路解锁为进行中，待「执行P4-M5」启动指令 |
| 2026-09-04 | Agent | **执行 P4-M5 行情链路** | 人指令「执行P4-M5」；T5.1–T5.5 产物见 docs/dev/modules/M5.md（domain/market 契约与纯规则 + data 客户端/编排/快照 M006/回填/额度/设备密钥方案甲 + ui 行情分组 + AppBootstrap 装配）；**黄金用例 11 通过** + MockEngine 全分支绿 + 两类 API 隔离实证；254 测试 0 失败（domain 149/data 82/ui 23）+ detekt 0 + 编译警告 0；GUI schema=6 + device.key 0600 实证；§5 规格裁决 8 条（含 price TEXT 勘误、调度宿主归 M11）待人工走读；模块记录 docs/dev/modules/M5.md；**P4 维持进行中，M5 置待审核，停人工门** |
| 2026-09-04 | 人 | **M5 GUI 验收反馈 3 项** | ① 流程 1–4 通过；④-1 Key 弹窗输入框键盘不可录（仅右键可用，M2 同根）；⑤ 未配置「立即刷新」toast「tether 暂无行情数据」；⑥ 真实 Demo Key 配置后仍显示无 Key 公共 API（尚无刷新）且刷新同 ⑤ |
| 2026-09-04 | Agent | **M5 修复轮** | ① WzModal 改就地叠加层（禁 Popup）+ initialFocusRequester/fieldFocusRequester 首输入框聚焦；共性约束固化 AGENTS.md §7.3（决策 20）；② 编排上浮目录失败原因（币集不可解析时 error 置位，不再误报「暂无行情」）；③ 数据源指示改按当前 Key 配置 + （尚无刷新）注解；回归 +2 测试（编排失败上浮 / UI 聚焦断言与配置显示）；256 测试 0 失败 + detekt 0 + 警告 0；复验步骤 M5.md §8.3；**M5 维持待审核，停人工门** |
| 2026-09-04 | Agent | **D21 行情页增补（决策 21）** | 人工拍板「正式行情页（窄版+持久化自选）」（修复轮反馈：刷新后无查看途径）；决策档 docs/dev/decisions/D21 + ia.md §2.19 同步；实现 MarketWatchService（watch.coins 全局行 JSON/上限50/默认种子=白名单/读时清理/损坏自愈）+ MarketQuotesService（latest 快照口径）+ MarketWatchPage（第六页 QUOTES：列表/搜索添加/移除/手动刷新/页面内自动轮询）+ 接线（MainShell/AuthGate/Main/Bootstrap）；data+5、ui+4 测试；267 测试 0 失败 + detekt 0 + 警告 0 + GUI 冒烟 0 异常；**M5 维持待审核，停人工门** |
| 2026-09-04 | 人 | **通过 M5** | 原话「M5通过」——M5 ✅ 已通过（关闭）：GUI 走查（键盘录入/Key 流程/刷新 toast/真实 Demo Key）、§8.5 行情页复验（六页导航/默认 4 行/刷新出价/搜索添加持久化/移除/自动轮询）、§5 规格裁决 8 条全部认可（含 price TEXT 勘误待回写 data-model 随 M6 门执行）；M6（建议）/M10 解锁为进行中，待启动指令 |
| 2026-09-04 | 人 + Agent | **确立变更控制流程（§8）+ 建台账 + 回填 D21 下游** | 拍板「全做」；AGENTS.md 新增 §8（C0/C1/C2 分级 + C2 红线 + C1 DoD + mini 闭环 + Agent/DSH 约束）+ §2 目录补 decisions/与增量台账.md；新建 `docs/dev/增量台账.md` 登记 D21（C1）；回填 data-model §2.3（watch.coins）/ api-contracts §3（MarketWatch/Quotes 补录）/ task-breakdown（T5.6 + M12 页数 18→19）/ interaction §2.7（行情页异常态）；STATUS 决策 22 落档 |
| 2026-09-04 | 人 + Agent | **变更控制硬化 + 决策归档** | 拍板「全做」；AGENTS.md §8 硬化（判定流程/轻量修改/删除工作流/影响面扫描/双向入口/amend 链/新增 §8.5/§8.6）+ §2 目录补决策索引 + 台账表头补有效需求版本；建 `docs/dev/decisions/决策索引.md` + 回溯式回填 D1/D16/D17/D18/D20 五档；STATUS 决策 23 落档 |
| 2026-09-04 | 人 + Agent | **行情页原型补齐（提前还 M12 债）+ 文档闭环** | 拍板「提前还掉」；`wuzhufolio-light.html` 加第六页「行情」（只读列表/搜索添加/移除/手动刷新/自选持久化 localStorage）+ `prototype-verify.js` 加行情页断言（默认 4 行/搜索添加/持久化/移除）+ 设置导航索引 nth(4)→nth(5)；回填 D21 §4 / 台账（prototype ⏳→✅）/ task-breakdown（T5.6 走查验收 + T12.1 六页口径 + 行情页 UI 收尾清单） |
| 2026-09-05 | Agent | **执行 P4-M6 交易所同步** | 人指令「执行P4-M6」；T6.1–T6.4 产物见 docs/dev/modules/M6.md（domain/exchange 契约 + BinanceAdapter 真实 API/MockEngine 全分支 + 增量去重编排 + M007 api_keys/M008 sync_logs/M009 transactions schema 6→9 + API 管理页挂载设置分组 + 向导 API 卡真实路径）；294 执行 0 失败（298 报告，4 钥匙串真实后端跳过待 CI win/mac） + detekt 0 + GUI schema=9 冒烟；§5 规格落档 8 条（含 price TEXT 勘误回写 data-model）待人工走读；模块记录 docs/dev/modules/M6.md；**P4 维持进行中，M6 置待审核，停人工门** |
| 2026-09-05 | Agent | **M6 人工门验收修复轮** | 人工 GUI 实测 3 项反馈（立即同步 120 未解析原因被吞 / 编辑等同新增 / 移除外键报错）→ 修复（M6.md §8）：① SyncTally 拆分「请求失败+首因」与「未解析」，关键级错误（密钥/限流/时间戳/网络）立即中止并上浮真实原因；② 新增 updateKey（仅改别名 / 换密钥重包两分支，编辑不再走 addAndSync）；③ removeWithLogs（同事务先清 sync_logs 再删密钥）。回归：数据 +2（中止上浮/更新仓库）、UI +2（编辑路由/只填其一拒绝）；301 执行 0 失败（305 报告）；detekt 0；**M6 维持待审核，停人工门** |
| 2026-09-05 | Agent | **M6 二轮诊断上浮** | 二轮复验仍「新增 0 · 未解析跳过 120」（无请求失败 → 拉取已通、卡币种解析）→ resolveRow 失败原因上浮（Skipped(原因)：未收录/歧义/退化对）+ SyncTally 样本（message 首笔 + 日志 3 条）+ 交易所同步共享市值排名目录（消歧规则③，M3 遗留 4 落地）；build+detekt 绿；**M6 维持待审核，停人工门（待三轮复验带样本）** |
| 2026-09-07 | Agent | **M6 三轮修复（根因闭环）** | 人工带回样本「BNBFDUSD/BNBUSDT：基础币 BNB 歧义（同名资产 2 个）」→ 根因 = BNB 在 CG 目录同名 2 资产 + ① 交易所目录实例未接排名缓存（Noop，规则③失效）② 排名缓存内存态重启即冷；修复 = 排名缓存接入共享目录（market/exchange 同一实例）+ warmUpRankCache()（冷则拉 4×250 页喂缓存，DIRECTORY 计额度；同步每轮自动预热，目录维护同走复用）；回归「ambiguous resolves via rank warm-up」（无预热=歧义跳过 2 / 带预热=裁定 binancecoin 入账 2）；build+detekt 绿；**M6 维持待审核，停人工门（请复验 BNB 成交入账）** |
| 2026-09-07 | 人 + Agent | **M6 人工「M6通过」关闭 ✅** | 验收覆盖：空校验 / B2 文案 / 保存即首次同步 / 真实 Key 入账 120 笔（BNB 歧义根因闭环：排名缓存接入共享目录 + 每轮自动预热）/ 增量幂等（重复同步「新增 0 ·（增量无新成交）」）/ 编辑两分支 / 移除（FK 修复，保留账本成交）；M6.md §8 一/二/三轮全部闭环；**M6 关闭 ✅，M7/M10 解锁待启动指令** |
| 2026-09-07 | Agent | **执行 P4-M7 交易管理** | 人指令「执行P4-M7」；T7.1–T7.4 产物见 docs/dev/modules/M7.md（domain/ledger 契约 + 事件构造层交易半边 + M010 fee_rules schema 9→10 + 账本仓库 + CSV 解析/导入 + 交易页/表单/CSV 向导 + 卖出盈亏轨迹）；39 新增测试全绿 + 存量 schema 断言 9→10 + clean build detekt 0 + GUI schema=10 冒烟无异常；§5 规格落档 10 条（含 V5/V7 张力待裁决）待人工走读；**P4 维持进行中，M7 置待审核，停人工门** |
| 2026-09-07 | Agent | **M7 GUI 走查修复轮** | 人工 11 项反馈：①模板下载线程崩溃（EDT invokeAndWait）修复；②弹窗裁切/无滚动条/保存无反馈（问题 3/5/6/10 同根）→ 紧凑两列 + 可见滚动条 + 失败双通道 toast；③计价币检索（问题 4）；④设置页手续费分组最小 CRUD（问题 7）；⑤折算价缺失改名义折算（问题 1 已实现盈亏链）；⑥命令区固定（问题 2）；⑦V5 文案引导（问题 9）；新增 13 项回归测试全绿 + clean build detekt 0 + GUI 冒烟 schema=10；M7.md §7；**M7 维持待审核，请重跑走查** |
| 2026-09-08 | Agent | **M7 第二轮走查反馈修复** | ① API 管理页核查零改动（变少 = 移除 Key 连带清 sync_logs，重加即恢复）；② CSV 时间解析容错（Excel/中文日期/epoch/序列 6 类，修复「改年份后三条格式错误」）；③ 删除买入冲突提示定位到具体记录（PRD V9 行为不变，提示可操作）；新增 13 项回归测试 + 无缓存 clean build detekt 全绿；M7.md §7.6；**M7 维持待审核，请复验** |
| 2026-09-08 | Agent | **M7 第三轮走查反馈（API 同步按钮）** | 核查 API 管理页零改动（同步按钮一直在密钥行内，删 Key 后列表空故不可见）；按 PRD 故事 4.3 补页面级「立即同步（全部密钥）」（空列表给引导、有密钥汇总、与行级互斥）；C0 实现补全（不建决策档/不进台账）；+2 项 UI 回归测试，clean build detekt 全绿；M7.md §7.7；**M7 维持待审核** |
| 2026-09-08 | Agent | **M7 第四轮走查反馈（手动同步入口）** | 主壳顶栏新增常驻「立即同步」按钮（PRD 故事 4.3 + ia.md 顶栏规范）：同步全部密钥、同步中指示、结果 toast、无密钥引导；与设置页入口并存；C0 实现补全；+5 项回归测试（ShellUiTest 2 + TopBarSyncViewModelTest 3），clean build detekt 全绿；M7.md §7.8；**M7 维持待审核** |
| 2026-09-08 | 人 | **通过 M7** | 原话「这个功能保留下来，文档要同步留痕。M7 通过。 更新git，并远程提交。」——M7 ✅ 已通过关闭（顶栏手动同步入口保留并文档留痕）；M8/M10 解锁待人工启动指令 |
| 2026-09-08 | Agent | **修复 M2 遗留 CI 缺陷** | 推送后核查：上轮 CI macos/windows Test 失败——remember-me parse 形状/账户/长度分支 require/error 抛 IAE，与 KDoc 契约（损坏一律 ISE）不符（Linux 跳过未暴露，win/mac 真实后端首跑暴露）；统一为类型化 ISE + 全平台 parse 契约回归测试（C0 勘误）；M7.md §7.9；复跑又暴露 windows 测试自备文件写 /tmp（平台无关 createTempFile 修复）；**最终 CI run 34324035381 三平台全绿** |
