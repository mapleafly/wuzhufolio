# WuZhuFolio 项目工作规约（AGENTS.md）

> 本文件是 DeepSeek Harness / opencode 等 Agent 工具的项目级说明书与工作流水线。
> 它规定了：谁干什么、每一步的中间产物落在哪里、每一步的完成标准、以及步骤之间如何衔接。
>
> **分工铁律**：
> - **人（你）**：拍板决策、审核中间产物、人工测试验收。
> - **Agent**：读取上一步产物 → 执行 → 把中间产物写入规定路径 → 更新状态看板 → **停在人工门，等待审核**。
>
> 所有 Agent 在开始任何工作前，必须先读本文件 + `docs/dev/STATUS.md`。

---

## 1. 项目速览

- **产品**：WuZhuFolio —— 以隐私和安全为核心、数据完全本地化的加密资产组合追踪工具。
- **两端**：
  - 桌面端（Windows / macOS / Linux）：系统托盘、OS 钥匙串、`.cpro` 备份，PRD V1.9（定稿）。
  - 移动端（Android 10+）：Kotlin、Jetpack Compose、Room、WorkManager、Material 3，PRD V1.2 / SRD V1.1。
- **推进顺序**：**先桌面端，后移动端**。**P1–P8 只针对桌面端或两端共同部分；移动端相关工作（原型、技术方案、开发）放到下一个版本，本阶段一律不启用。**移动端相关技能、技术方案与开发，待桌面端主线稳定后再启用。
- **需求文档策略**：桌面端**不设独立 SRD**（PRD + 跨端共享规范 + 技术方案即可）；移动端**保留 SRD** 作为工程追溯锚点；跨端规则以《跨端共享规范》为准。
- **当前基线**：两端 PRD/SRD、`跨端共享规范.md`（V1.0）及评审报告已完成并通过评审，见 `docs/prd/`。是否启动 P1 以 `docs/dev/STATUS.md` 的人工门为准。

### 1.1 硬约束（来自 PRD，全局有效，任何阶段任何代码不得违反）

1. **数据本地化**：交易记录、API 密钥、资金流水只存用户设备本地，禁止任何云端上传/传输。
2. **零遥测**：不内置任何遥测、统计上报或第三方分析 SDK。
3. **密钥与加密**：分层密钥（DEK/KEK）、AES-256-GCM、密码与登录凭据永不落盘、日志脱敏。
4. **两类独立 API**：行情数据刷新（CoinGecko 主源 / CoinMarketCap 兜底）与交易数据同步（交易所只读 API）相互独立。
5. **多账户隔离 + 备份**：账户数据互相隔离；`.cpro` 备份格式（明文头部 + AES-256-GCM 加密 JSON 载荷）。

---

## 2. 目录约定（中间产物落盘位置）

```
docs/
  prd/        # 需求基线（已存在，只读基准，不得改动）
  design/     # P1 产品与交互设计
  tech/       # P2 技术方案（含 adr/ 决策记录子目录）
  test/       # P5 集成联调 / P6 系统测试
  release/    # P7 发布
  dev/        # 过程状态：STATUS.md（状态看板）、modules/（模块完成记录）、retrospective.md
src/ 或各端代码目录   # P3 脚手架起产生
```

---

## 3. 状态看板协议（步骤衔接的核心）

`docs/dev/STATUS.md` 是**唯一的状态真源**，所有衔接都通过它完成：

- 每个阶段完成后，Agent 必须更新 `STATUS.md`：
  1. 把该阶段的**产物清单 + 文件链接**写进去；
  2. 把状态标记为 `待审核`；
  3. 写清「本次改了什么 / 怎么验收 / 遗留问题 / 建议的下一步」。
- 人审核通过后，把该阶段改为 `已通过`，并解锁下一阶段为 `进行中`。
- **Agent 只能对 `进行中` 的阶段行动；遇到 `待审核` 的人工门必须停止并提示人。**
- 不经过 `STATUS.md` 的口头/对话结论，视为无效，不算完成。

---

## 4. 阶段流水线（P0–P8）

每一步都按统一格式：**目标 → 输入 → Agent 产出（中间产物）→ 人工门 → 完成标准（DoD）→ 衔接下一步**。

### P0 需求基线 ✅（已完成）

- **目标**：把想法定稿为可执行的 PRD/SRD。
- **输入**：产品想法、市场/竞品调研。
- **产出**：`docs/prd/桌面端prd.md`（V1.9 定稿）、`docs/prd/移动端prd.md`（V1.2）、`docs/prd/移动端SRD.md`（V1.1）、`docs/prd/跨端共享规范.md`（V1.0）、评审报告、决策分析。
- **人工门**：拍板 PRD 定稿。
- **DoD**：需求无歧义、验收标准可测试、硬约束已写明。
- **衔接**：PRD/SRD 是 P1 的唯一输入。

### P1 产品与交互设计

- **目标**：把 PRD 翻译成「长什么样、怎么用」，产出开发与测试都可引用的页面/流程/异常清单。
- **输入**：`docs/prd/*`（PRD/SRD + 评审报告 + 决策分析）。
- **Agent 产出**（写入 `docs/design/`）：
  - `ia.md`：信息架构与页面清单（每个页面：路由/入口、核心元素、数据来源）。
  - `flows.md`：核心用户流程与状态机（登录/记住我、增资、交易、API 同步、行情刷新、持仓校准、备份恢复、切换账户、忘记密码）。
  - `interaction.md`：交互与异常态说明（加载态、空态、错误态、离线态、429 限流提示），**异常态必须与 PRD「统一异常处理」逐条对应**。
  - `design-tokens.md`：视觉/组件规范（**仅桌面端**：配色、组件、托盘/通知规范；移动端 Material 3 归入下一版本）。
  - `prototype/`：**桌面端高保真 HTML 原型图**（新增步骤）——用 huashu-design 产出单文件、可点击、可交互的桌面端原型，落 `docs/design/prototype/*.html`；流程：先信息架构文字稿 → 原型 1 主版 + 变体（三方向初稿**可简化**，见 §7.2）→ 人工选择（`direction-approved.md`）→ 深化 → Playwright 验证；窗框用 `macos_window`/`browser_window`，只用脱敏假数据。
- **人工门**：审核设计稿——是否与 PRD 一致、体验是否合理、异常态是否齐全、原型是否覆盖核心页面。
- **DoD**：每个核心流程有图/文字说明；每个页面有清单；异常态清单覆盖 PRD 全部异常条款；**原型图输出为 HTML、覆盖全部核心页面、可点击可交互**。
- **衔接**：`ia.md` 页面清单 → P2 任务拆解来源；`interaction.md` 异常态 → P6 测试用例来源；`prototype/*.html` → P4 模块 UI 视觉基准。

### P2 技术方案

- **目标**：把设计与需求翻译成「怎么实现」，定死架构、选型、数据模型、接口契约与任务顺序。
- **输入**：`docs/prd/*` + `docs/design/*`。
- **Agent 产出**（写入 `docs/tech/`）：
  - `architecture.md`：分层架构与模块边界（移动端遵循 SRD 的 UI–Domain–Room 分层；桌面端需定架构）。
  - `adr/ADR-001-*.md`…：技术决策记录（ADR 格式：背景 / 选项 / 决策 / 理由 / 风险）。至少覆盖：桌面端技术栈、存储引擎与加密方案、行情客户端（CoinGecko/CMC 两级模式）、交易所同步适配、`.cpro` 备份格式、构建与分发（签名/公证）。
  - `data-model.md`：实体、ER 图、表结构（移动端对齐 SRD 14.2；桌面端对齐 PRD 数据模型章节）。
  - `api-contracts.md`：行情与交易所 API 的封装契约（接口签名、返回结构、错误码、限流/退避语义）。
  - `task-breakdown.md`：WBS 任务拆解 + 里程碑 + **依赖顺序**，每个任务附可测试的验收标准（拆到可独立验收的最小粒度）。
- **人工门**：拍板技术选型（尤其桌面端栈）与任务优先级/顺序。
- **DoD**：每个关键决策有 ADR；数据模型覆盖 PRD 全部实体与字段；任务拆解到模块级、有依赖图；**产物可回溯到需求基线**（PRD 章节号 / 共享规范条款号 / SRD 功能需求编号）。
- **衔接**：`task-breakdown.md` → P3 脚手架与 P4 开发顺序；`data-model.md` + `api-contracts.md` → P4 各模块实现与 P5 联调基准。

### P3 工程脚手架

- **目标**：搭出可运行、可 CI 的最小骨架，先跑通一条「hello」链路。
- **输入**：`docs/tech/architecture.md` + `adr/*` + `task-breakdown.md`。
- **Agent 产出**：
  - 可运行仓库骨架（目录结构、构建配置、依赖锁定、lint/format、CI 配置）；
  - `README.md`（构建/运行/测试命令）；
  - `docs/tech/dev-setup.md`（本地开发环境与密钥配置步骤）；
  - 一条端到端最小链路（如：空界面启动 → 读配置 → 打一条脱敏日志）。
- **人工门**：确认骨架能在本地构建并跑起来。
- **DoD**：本地构建通过；CI 绿；一条 hello 链路端到端可跑。
- **衔接**：骨架 → P4 分模块开发。

### P4 分模块开发（循环，每个模块一个子循环）

- **目标**：按 `task-breakdown.md` 的依赖顺序，逐模块实现，边做边验。
- **输入**：`docs/tech/*`（数据模型、接口契约、任务拆解）+ `docs/design/*`。
- **每个模块的 Agent 产出**：
  - 代码 + 单元测试；
  - `docs/dev/modules/<模块名>.md`：实现摘要、改动文件、验收清单、遗留问题。
- **每个模块的人工门**：人工测试该模块（按任务验收标准点一遍），通过后才进入下一模块。
- **DoD**：模块通过 CI；有单元测试；验收清单逐项打勾；更新 `STATUS.md`。
- **衔接**：模块 A 的接口/数据被模块 B 依赖 → 严格按依赖顺序推进；所有模块完成 → P5。

### P5 集成与联调

- **目标**：把各模块拼起来，打通核心用户旅程。
- **输入**：P4 全部模块 + `api-contracts.md`。
- **Agent 产出**：
  - `docs/test/integration-report.md`：联调报告（跨模块问题清单与修复记录）；
  - 集成测试；
  - 端到端主流程打通（登录 → 增资 → 交易 → 看板/ROI → 备份恢复）。
- **人工门**：人工验收核心用户旅程。
- **DoD**：主流程无阻断性缺陷；跨模块接口与契约一致。
- **衔接**：集成版 → P6。

### P6 系统测试与质量

- **目标**：按 PRD 验收标准做全量验证，重点压安全与隐私项。
- **输入**：P5 集成版 + PRD 验收标准 + `interaction.md` 异常态清单。
- **Agent 产出**（写入 `docs/test/`）：
  - `test-plan.md`：测试计划与范围；
  - `test-cases.md`：测试用例（功能 + 异常态 + 离线 + 限流）；
  - `security-checklist.md`：安全自查清单（§1.1 硬约束逐条核验：本地存储、无遥测、密钥/加密、脱敏、备份格式）；
  - `defects.md`：缺陷清单与修复记录；
  - `test-report.md`：测试报告与结论。
- **人工门**：人工测试（用真实交易所只读 Key、离线、备份恢复、跨账户等场景）+ 拍板是否达到发布标准。
- **DoD**：P0/P1 缺陷清零，P2 有明确处理结论；`security-checklist.md` 全部通过。
- **衔接**：`test-report.md` → P7 发布决策。

### P7 发布

- **目标**：把达标版本安全地发布出去。
- **输入**：P6 达标版本 + `test-report.md`。
- **Agent 产出**（写入 `docs/release/`）：
  - `release-plan.md`：发布清单与步骤；
  - `rollback.md`：回滚方案；
  - `CHANGELOG.md`：变更日志；
  - `user-guide.md`：用户使用说明；
  - 构建产物与签名/公证说明（桌面端 macOS 公证 / Windows 签名；移动端打包）。
- **人工门**：批准发布。
- **DoD**：发布与回滚步骤可执行；产物签名合规；用户文档与版本一致。
- **衔接**：发布 → P8。

### P8 上线后运营与迭代

- **目标**：根据真实使用反馈，决定下一迭代。
- **输入**：线上版本 + 用户反馈/缺陷。
- **Agent 产出**：
  - `docs/dev/retrospective.md`：迭代复盘（目标达成、问题、经验）；
  - 下一迭代 PRD 增量（写回 `docs/prd/`，走新的 P0 流程）。
- **人工门**：复盘确认 + 拍板下一迭代范围。
- **DoD**：复盘完成；下版范围定稿。
- **衔接**：回到 P0/P1 开始下一迭代。

---

## 5. 单步执行协议（Agent 版模板）

任何 Agent 接到一个阶段/模块任务时，按以下顺序执行：

1. **读上下文**：读 `AGENTS.md`（本文件）+ `docs/dev/STATUS.md` + 上一步产物。
2. **查门禁**：若 `STATUS.md` 中存在未关闭的 `待审核` 门，停止并提示人先审核。
3. **执行**：完成被分配的任务（范围严格限于当前阶段，不越界）。
4. **落盘**：把中间产物写入 §2 规定路径（不允许只写在对话里）。
5. **更新看板**：更新 `STATUS.md`——产物清单、状态 `待审核`、验收方式、遗留问题、下一步建议。
6. **汇报**：输出「待人工审核清单」：本次改了什么、怎么验收、遗留问题。
7. **停下等待**：停在人工门，不擅自进入下一阶段。

---

## 6. 评审门禁表

| 阶段 | 人工动作 | 放行条件 |
|------|----------|----------|
| P0 | 拍板 PRD | 需求定稿 ✅（已完成） |
| P1 | 审核设计稿 | 与 PRD 一致、异常态齐全、原型 HTML 可交互 |
| P2 | 拍板选型与顺序 | ADR 齐全、任务有依赖图 |
| P3 | 验证骨架 | 本地能构建跑通 |
| P4 | 逐模块人工测试 | 模块验收清单打勾 |
| P5 | 验收核心旅程 | 主流程无阻断缺陷 |
| P6 | 人工测试 + 拍板 | 测试报告 + 安全清单通过 |
| P7 | 批准发布 | 发布/回滚可执行、签名合规 |
| P8 | 复盘拍板 | 复盘完成、下版定稿 |

---

## 7. 与 DSH Skills 的关系

- 本文件是**全局流水线**；单个阶段的详细操作模板可后续下沉为 `.agents/skills/<phase>/SKILL.md`，由本文件在对应阶段指向它。
- **已挂载技能**：`.agents/skills/huashu-design/`（项目级安装，MIT，2026-08-30）——用 HTML 做高保真原型/幻灯片/动画/信息图/评审。详见 `docs/dev/huashu-design-调研报告.md`。
  - **边界**：只产出设计与演示中间产物，不生成生产级应用代码；本阶段只用于桌面端，`ios_frame`/`android_frame` 等移动端资产不启用；云能力（TTS/AI 评审）按 §1.1 硬约束禁用。

### 7.1 P1–P8 各阶段 huashu-design 使用规格（功能 + 具体 skill 子部分）

> 每个阶段「是否用、用哪些功能、用 skill 的哪些子部分（SKILL.md 章节 / references 文件 / assets 组件 / scripts 脚本）」在此定死；Agent 执行该阶段时按表取用，不临时发挥。

| 阶段 | 是否用 | 使用的功能 | 使用的 skill 子部分 | 决策/备注 |
|------|--------|-----------|--------------------|-----------|
| P1 产品与交互设计 | 🔴 必用 | 桌面端 HTML 原型（交互原型）、设计方向、5 维评审 | SKILL.md「标准流程 Step 1–8 + 反AI slop」；`references/app-prototype.md`（架构选型/单文件 inline/Playwright 点击测试）、`references/design-styles.md`（网页 20 种，Dashboard 走网页区）、`references/typography.md`（字体配对）、`references/verification.md` + `scripts/verify.py`；`assets/macos_window.jsx` / `browser_window.jsx`（桌面窗框） | ① 三方向初稿**可简化**（PRD 已定稿、方向明确）：先出信息架构文字稿、原型出 1 主版 + 变体；简化须记入 `direction-approved.md` ② 只用脱敏假数据 |
| P2 技术方案 | 🟡 推荐 | 架构图 / ER 图 / 任务依赖图（信息图/可视化） | `references/design-styles.md`（信息图 20 种）、`references/scene-templates.md`、`scripts/verify.py` | **图例二选一**：写在 md 内 → 优先 Mermaid；独立文件 → 优先 huashu-design HTML（表达力更丰富） |
| P3 工程脚手架 | 🟢 可选 | README 架构示意图 | `references/design-styles.md`（信息图分区） | 文字+目录通常够，不强求 |
| P4 分模块开发 | 🟢 可选 | 模块 UI 走查对比 | 引用 P1 `prototype/*.html` 截图 | 不生成新设计，仅引用原型截图 |
| P5 集成与联调 | 🟢 可选 | 主流程链路图 | `references/design-styles.md`（信息图） | 配图可选 |
| P6 系统测试与质量 | 🟢 可选 | 测试报告/缺陷分布可视化 | `references/design-styles.md`（信息图/PPT 20）、`assets/deck_index.html`（做 deck 时） | 配图可选 |
| P7 发布 | 🔴 必用（宣传动画）+ 🟡 推荐（指南/材料） | ① 产品宣传动画（MP4/GIF，**必做**）② 用户指南（HTML deck）③ 发布说明 | 动画：SKILL.md Step 9 + `references/storyboard-basics.md`（分镜卡）+ `references/camera-language.md` + `references/gsap-recipes.md` + `references/animation-pitfalls.md` + `references/audio-design-rules.md` + `references/sfx-library.md` + `assets/animations.jsx`（Stage/Sprite）+ `assets/cursor.jsx` + `assets/bgm-*.mp3` + `assets/sfx/` + `scripts/render-video.js`/`render-video-seek.js` + `scripts/convert-formats.sh` + `scripts/add-music.sh`；指南：`references/slide-decks.md` + `assets/deck_index.html` + `scripts/export_deck_pdf.mjs`/`export_deck_pptx.mjs` | **P7 做产品宣传动画（人工拍板，必做）**；动画默认带 BGM+SFX（除非用户明示不要） |
| P8 上线后运营与迭代 | 🟢 可选 | 迭代复盘 deck | `references/slide-decks.md` + `assets/deck_index.html` | 配 deck 可选 |

### 7.2 本次人工拍板决策（2026-08-30）

1. **P1 三方向初稿可简化**：PRD 已定稿、设计方向较明确，可先出信息架构文字稿、原型出 1 主版 + 变体（不强制三版并排）；简化/豁免须记入 `docs/design/direction-approved.md`。
2. **P2 图例选型**：写在 md 文档内 → 优先 Mermaid；独立文件 → 优先 huashu-design HTML（表达力更丰富）。
3. **P7 做产品宣传动画**：作为 P7 必做项（非加分项），用 huashu-design 动画链产出 MP4/GIF。

- **技能按需补充原则**：
  - 桌面端阶段：已有 huashu-design 覆盖「设计/原型/可视化」；待技术栈确定后，再补桌面端专属技能（OS 钥匙串、托盘、`.cpro` 加密备份、签名/公证等）。
  - 移动端阶段：待桌面端主线稳定、启动移动端前，再按需补 Android 相关技能。
  - 项目领域知识（行情/交易所 API、加密备份、ROI 计算等）以 `docs/` 产物为准，必要时再沉淀为项目专属技能。
- Agent 不得假设任何技能存在：需要某项能力时，先查 `.agents/skills/` 是否已挂载，再决定是否提示人补充。
