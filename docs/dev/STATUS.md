# WuZhuFolio 开发状态看板（STATUS.md）

> 本文件是**唯一的状态真源**。每个阶段完成后由 Agent 更新；人审核通过后解锁下一阶段。
> 状态取值：`待审核` / `进行中` / `已通过` / `未开始`。
> 规则见根目录 `AGENTS.md` 第 3 节。

## 当前阶段

- **当前状态**：**P3 工程脚手架 ✅ 已通过（2026-09-01 人工「P3 通过」）**；**P4 分模块开发 ⏩ 进行中——待人工下达启动指令**，首个模块 = M1 存储与加密（T1.1–T1.4）。P0/P1/P2 均已关闭。
- **推进顺序**：先桌面端，后移动端。**P1–P8 只针对桌面端或两端共同部分；移动端相关工作放到下一个版本。**（移动端相关技能/技术方案/开发待桌面端主线稳定后再启用。）
- **前序待审核项已关闭（2026-08-30，人工启动指令）**：① 项目级安装 huashu-design；② P1 新增「设计原型图」步骤；③ P1–P8 huashu-design 用途分析--已随人工「开始执行P1」指令一并拍板（固化为 `AGENTS.md` §7.1/§7.2）。
- **下一人工门**：**P4 分模块开发（AGENTS.md §6「逐模块人工测试」）**——首个模块 M1 存储与加密（T1.1 SQLCipher 三平台锁版 / T1.2 CryptoService / T1.3 Argon2id 基准校准 ≤2s / T1.4 单写队列），验收标准见 docs/tech/task-breakdown.md §3；待人工「执行P4」启动指令。

## 阶段总览

| 阶段 | 名称 | 状态 | 产物 | 备注 |
|------|------|------|------|------|
| P0 | 需求基线 | ✅ 已通过 | 见下 | 评审已通过 |
| P1 | 产品与交互设计 | ✅ 已通过 | docs/design/（含 prototype/*.html + 截图 + 验证脚本） | 主版唯一真源（内置双主题）；登录链路已补齐；三轮评审 V1/V2/V3 问题全部闭环；2026-08-31 人工终审通过 |
| P2 | 技术方案 | ✅ 已通过 | `docs/tech/`（architecture + 6 ADR + data-model + api-contracts + task-breakdown + P2评审报告） | 2026-08-31 人工拍板三项关闭；全部 ADR 转人工拍板采纳 |
| P3 | 工程脚手架 | ✅ 已通过 | 代码骨架 + CI + dev-setup + hello 链路 + 迁移框架 + UI 基座（内嵌 CJK 字体）；Gradle 8.14.4；P3评审报告 | 2026-09-01 完成 M0 + 验收修复轮 + 评审闭环；人工「P3 通过」关闭 |
| P4 | 分模块开发 | 进行中 | 代码 + `docs/dev/modules/` | 2026-09-01 P3 通过后解锁，待人工启动指令（首个模块 M1 存储与加密） |
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

## 已决策事项

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

## 当前阻塞点

- **P4 待启动指令**：P3 已于 2026-09-01 人工「P3 通过」关闭 ✅；P4 解锁为进行中，待人工下达「执行P4」指令。P4 首批验证项：SQLCipher 三平台锁版（T1.1）、Argon2id 基准校准（T1.3，4GB 双核目标机 ≤2s）。

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
