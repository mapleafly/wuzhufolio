# huashu-design 调研与在 WuZhuFolio 中的应用

> 版本：V1.0 ｜ 日期：2026-08-30 ｜ 状态：待人工审核
> 关联：`AGENTS.md`（§4 P1、§7 技能）· `docs/dev/STATUS.md`（看板）
> 上游：https://github.com/alchaincyf/huashu-design（MIT License，2026-05-14 起商用免费）

---

## 0. 结论摘要（TL;DR）

| 问题 | 结论 |
|------|------|
| 它是什么 | 一个 **markdown 驱动的 agent 技能（skill）**，用纯 HTML/CSS/JS 作为媒介，让 Agent 产出「大厂级」高保真视觉交付物（原型 / 幻灯片 / 动画 / 信息图 / 评审） |
| 装在哪 | 项目级 `.agents/skills/huashu-design/`（git clone，不污染全局），4 个导出依赖已 `npm install` 完成 |
| 在 WuZhuFolio 里干什么 | **P1 桌面端 HTML 原型是核心用途**；P2 架构/ER 图、P5–P8 报告可视化、P7 发布材料/宣传动画为辅助用途 |
| 边界 | 只产出**设计与演示中间产物**（HTML/PDF/PPTX/MP4），**不产出生产级运行时代码**；不碰后端、不做 Figma 级图层编辑 |
| 与硬约束关系 | 设计→渲染→导出链路 100% 本地、零遥测、零 key，与 `AGENTS.md` §1.1 硬约束天然一致；云能力（TTS/看片评审）可选且隔离，本项目按硬约束**禁用** |

---

## 1. 是什么

### 1.1 形态

huashu-design 不是一个 npm 库或 GUI 工具，而是一个 **skill 包**，结构为：

| 部分 | 内容 | 本次安装规模 |
|------|------|-------------|
| `SKILL.md` | 主文档（给 Agent 读的工作流程与规则，579 行） | 1 个 |
| `references/` | 按任务路由加载的子文档（原型/幻灯片/动画/风格库/评审…） | 32 个 |
| `assets/` | 起手组件（设备窗框、动画引擎、幻灯片引擎、音效/BGM、预制样例） | 105 个 |
| `scripts/` | 导出工具链（HTML→PDF/PPTX/MP4/GIF/缩略图、验证） | 19 个 |
| `demos/` | 9 个能力演示（c*/w*，中英双版 HTML） | 23 个 |

官方强调：`references/`、`assets/`、`scripts/`、`demos/` 四个子目录有 99 处被交叉引用的配方/脚本/素材，**缺一不可**。本次克隆已核验四目录齐全。

### 1.2 七种交付能力

| 能力 | 交付物 | 典型耗时 | WuZhuFolio 相关度 |
|------|--------|----------|------------------|
| 交互原型（App / Web） | 单文件 HTML · 可点击 · Playwright 验证 | 10–15 min | ⭐⭐⭐ P1 核心 |
| 演讲幻灯片 | HTML deck + 可编辑 PPTX | 15–25 min | ⭐⭐ P7 用户指南/发布 |
| 时间轴动画 | MP4 + GIF + BGM/SFX | 8–12 min | ⭐ P7 宣传片（加分项） |
| 设计变体 | 3+ 并排对比 · Tweaks 实时调参 | 10 min | ⭐⭐ P1 方向选择 |
| 信息图 / 可视化 | 印刷级排版 · 可导 PDF/PNG/SVG | 10 min | ⭐⭐ P2 架构图/ER 图 |
| 设计方向顾问 | 三套逻辑并行 · 直接出 3 版真实视觉 | 5 min | ⭐⭐⭐ P1 方向选择 |
| 5 维度专家评审 | 雷达图 + Keep/Fix/Quick Wins | 3 min | ⭐⭐ P1 设计自检 |

### 1.3 核心机制（对 WuZhuFolio 有用的几条）

1. **三方向硬门**：任何新视觉设计，100% 先出三版真实初稿给用户「看着选」，选定后才深化——避免「文字盲选风格」。WuZhuFolio P1 原型走这条。
2. **品牌资产协议**：涉及具体品牌时「问 → 搜 → 下载 → grep 色值 → 固化 spec」五步，绝不凭记忆猜品牌色。本项目无外部品牌，但「从真实内容/母题推导色彩」的**色彩推导协议**（采样→收敛→论证）对桌面端设计语言有价值。
3. **Gate 文件协议**：`brand-spec.md`、`direction-approved.md`（记录展示了哪几版 + 用户选择原话）物化检查点，防止长会话中方向确认被「继续」冲掉。本项目 P1 的 gate 文件落在 `docs/design/`。
4. **反 AI slop**：避免紫渐变/emoji 图标/圆角左 accent/Inter 做 display 等「一眼 AI」的最大公约数；正文 ≥14px、对比度 ≥4.5:1 可读性底线。
5. **Playwright 验证**：交付前用 Playwright 截图 + 点击测试，`pageerror` 为 0 再交付。

---

## 2. 安装与依赖（本次项目级安装）

### 2.1 官方安装方式（两条路）

- 全局：`npx skills add alchaincyf/huashu-design`（装到 `~/.claude/skills/` 等全局 skills 目录）
- 项目级：`git clone https://github.com/alchaincyf/huashu-design.git <项目 skills 目录>`（README 明确给出的兜底方式）

### 2.2 本次安装（项目级，仅 WuZhuFolio 项目下）

| 项 | 值 |
|----|----|
| 位置 | `.agents/skills/huashu-design/`（本项目约定 skills 目录，原已清空） |
| 方式 | `git clone --depth 1` |
| 规模 | 63MB（含 BGM/SFX 音频与 demo） |
| 导出依赖 | `pdf-lib` / `playwright` / `pptxgenjs` / `sharp`，已 `npm install`（31 包，均验证可加载） |
| 浏览器 | Playwright 浏览器二进制**未下载**（`~/.cache/ms-playwright` 为空）——P1 截图/验证前按需执行 `npx playwright install chromium` |
| 版本自检 | 已写 `.last-update-check`（2026-08-30），30 天内不再联网查版本 |

> 依赖说明：`pdf-lib/pptxgenjs/sharp` 仅导出链（PDF/PPTX/缩略图）需要；`playwright` 仅截图/验证需要；**P1 原型本身只需「HTML + 浏览器」即可打开预览**。导出依赖非 P1 阻塞项。

---

## 3. 边界与局限（不能做什么 / 注意什么）

| 边界 | 说明 | 对 WuZhuFolio 的影响 |
|------|------|---------------------|
| 不适用生产级 Web App / SEO / 需后端系统 | 产出是静态 HTML 演示，不是可上线的应用 | ✅ 正确分工：huashu-design 做 P1 设计原型，P3/P4 的真实桌面应用代码另走技术栈 |
| 无图层级可编辑 PPTX→Figma | 可导 PDF/PPTX/PNG，但不能拖进 Keynote/Figma 改文字 | 无影响（本项目不用 Figma） |
| 无 Framer Motion 级复杂动画 | 3D/物理模拟/粒子超出边界 | 无影响（P7 宣传动画可选且走时间轴/运镜即可） |
| 完全空白品牌从零设计质量 60–65 分 | 凭空画 hi-fi 是 last resort | ✅ 有桌面端 PRD V1.9 详细内容支撑，「从内容长出来」条件充足 |
| 80 分 skill 而非 100 分产品 | 面向不愿开 GUI 的 Agent 场景 | 与本项目「Agent 流水线 + 人工门」模式匹配 |
| **移动端优先的资产结构** | `references/app-prototype.md` 与 `ios_frame/android_frame` 面向移动端；桌面端仅 `macos_window.jsx`（macOS 窗框+红绿灯）与 `browser_window.jsx`（Chrome 窗框） | ✅ 桌面原型走「网页/Dashboard 原型」路径（网页 20 风格）套 `macos_window`/`browser_window` 窗框；**不用 ios/android 框**（本阶段只做桌面端） |
| 云能力（豆包 TTS、AI 看片评审） | 隔离在 `scripts/cloud/`，可选、自备 key、首次 `--yes` | 🔴 本项目按 §1.1 硬约束（零遥测/数据本地化）**禁用** cloud 能力，只用本地链路 |

### 安全与数据流（重要，与硬约束核对）

huashu-design 官方声明：核心链路（设计→渲染→MP4/PDF/PPTX 导出）**100% 本地运行、零网络、零 key**；无 telemetry，无任何数据发往作者服务器。这与 WuZhuFolio §1.1 硬约束（数据本地化 / 零遥测）**方向一致**。本项目额外约定：P1 原型只使用**脱敏假数据**渲染视觉，不接入真实 API Key、不写入真实资金流水。

---

## 4. 在 WuZhuFolio 中的作用与用法

### 4.1 产品画像匹配

WuZhuFolio 桌面端是**数据密集型**产品（看板、持仓、交易流水、ROI/成本曲线）。按 huashu-design 的信息密度分型，属于**高密度型**（产品卖点是数据/隐私/上下文），原型每屏需 ≥3 处**有内容的**差异化信息，避免「米白占位卡」。

### 4.2 桌面端原型的三条落地规则

1. **窗框用 `assets/macos_window.jsx` / `browser_window.jsx`**（桌面 App / 网页形态），不用 `ios_frame`/`android_frame`。
2. **风格分区选「网页 20 种」**（Dashboard 原型走网页区），不套「PPT 20 / 信息图 20」。
3. **单文件 HTML**，双击 `file://` 可开、可点击、可切换状态；交付前 Playwright 截图 + 点击测试。

### 4.3 职责分离（关键）

| 层 | 谁来做 | 产物 |
|----|--------|------|
| 设计层（长什么样） | huashu-design | P1 HTML 原型、图例、演示 |
| 实现层（怎么跑起来） | P3/P4 真实技术栈（待 P2 定） | 可运行的桌面应用代码 |

huashu-design **只覆盖设计层**，不生成生产代码。两者通过 P1 的 `ia.md`/`flows.md`/`interaction.md`/`design-tokens.md` 衔接。

---

## 5. P1–P8 各阶段可用 huashu-design 的环节（映射表）

> 分析结论：**P1 是主战场（必用）**；P2 图例（推荐）；P7 发布材料（推荐，宣传动画为加分项）；其余为可选可视化，不强制、不阻塞主线。

| 阶段 | 可用环节 | 用哪项能力 | 产出落点 | 必要性 |
|------|----------|-----------|----------|--------|
| **P1 产品与交互设计** | ① 设计方向顾问（三方向硬门）② **桌面端 HTML 原型图** ③ 5 维专家评审 | 交互原型 + 方向顾问 + 评审 | `docs/design/prototype/*.html` + `direction-approved.md` | 🔴 必用（本阶段新增步骤） |
| P2 技术方案 | ① 分层架构图 ② ER 图 ③ 任务依赖图 ④ ADR 决策配图 | 信息图 / 可视化 | `docs/tech/*.html` 或嵌入 md 的图 | 🟡 推荐（图例比纯文字更可读） |
| P3 工程脚手架 | README 架构示意图 | 信息图（轻量） | `README.md` 配图 | 🟢 可选（文字+目录通常够） |
| P4 分模块开发 | 模块 UI 走查对比（原型截图 vs 实现截图） | 原型截图引用 | `docs/dev/modules/*.md` 配图 | 🟢 可选（不生成新设计） |
| P5 集成与联调 | 联调报告可视化（主流程链路图） | 信息图 / 流程图 | `docs/test/integration-report.md` 配图 | 🟢 可选 |
| P6 系统测试与质量 | 测试报告 / 缺陷分布 / 安全清单可视化 | 信息图 / deck | `docs/test/*.md` 配图 | 🟢 可选 |
| P7 发布 | ① 用户使用指南（HTML/deck）② 发布说明/CHANGELOG ③ 产品宣传动画（加分项） | 幻灯片 / 动画 | `docs/release/user-guide` / 宣传片 | 🟡 推荐（指南+发布材料） |
| P8 上线后运营与迭代 | 迭代复盘 deck | 幻灯片 | `docs/dev/retrospective.md` 配 deck | 🟢 可选 |

> 图例优先级说明：P2 架构/ER 图可优先用 huashu-design 出 HTML 图例；但若某类图用 Mermaid/PlantUML 更易维护（如 ADR 内嵌），则**不强制**用 huashu-design——选择标准是「可读性 + 可维护性 + 是否可独立阅读」，见 §6.3。

---

## 6. 使用约定（本项目落地硬规则）

### 6.1 范围铁律（本次新决策）

- **P1–P8 只针对桌面端或两端共同部分**；移动端相关工作（含移动端原型、`ios_frame`/`android_frame` 资产、移动端技术方案与开发）**放到下一个版本**，本阶段一律不启用。
- 任何 Agent 产出不得越界到移动端。

### 6.2 P1 原型步骤约定（新增）

- P1 新增「设计原型图」步骤，**原型输出为 HTML**（单文件、可点击、可交互），落 `docs/design/prototype/`。
- 流程遵循 huashu-design：三方向初稿 → 人工选择（`direction-approved.md`）→ 深化 → Playwright 验证。
- 原型只使用脱敏假数据；窗框用 `macos_window`/`browser_window`。

### 6.3 何时用、何时不用

- **用**：需要「给人看」的高保真视觉（原型、图例、演示、发布材料）。
- **不用**：可用文字/表格/Mermaid 更清晰更易维护的工程图（如 ADR 内嵌时序图）、需要后端/动态数据的页面、生产级应用代码。

### 6.4 维护与更新

- skill 版本更新需人工拍板：`git -C .agents/skills/huashu-design pull --ff-only`（Agent 不主动执行更新）。
- 项目领域知识（行情/交易所 API、加密备份、ROI 计算）仍以 `docs/` 产物为准，huashu-design 只负责「呈现」，不负责「定义业务」。

---

## 7. 遗留问题 → 已人工拍板（2026-08-30）

1. ✅ **P1 三方向初稿可简化**：先出信息架构文字稿、原型出 1 主版 + 变体（不强制三版并排）；简化/豁免记入 `direction-approved.md`。
2. ✅ **P2 图例选型**：写在 md 内 → 优先 Mermaid；独立文件 → 优先 huashu-design HTML（表达力更丰富）。
3. ✅ **P7 做产品宣传动画**：作为 P7 必做项，产出 MP4/GIF。
