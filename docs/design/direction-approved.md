# 设计方向选择记录（direction-approved.md）

> P1 原型方向门 Gate 文件（对应 huashu-design「direction-approved.md」协议 + AGENTS.md §7.2 决策 1）。

---

## 1. 三方向初稿简化依据（AGENTS.md §7.2 决策 1，2026-08-30 人工拍板）

> “P1 三方向初稿可简化：PRD 已定稿、设计方向较明确，可先出信息架构文字稿、原型出 1 主版 + 变体（不强制三版并排）；简化/豁免须记入 docs/design/direction-approved.md。”

- 豁免类型：huashu-design Fallback「三方向硬门」→ 降级为「1 主版 + 1 变体」。
- 豁免来源：AGENTS.md §7.2（项目级人工拍板决策），非本会话临时跳过。
- 信息架构文字稿已先行产出：docs/design/ia.md（页面清单先行，原型据此展开）。

---

## 2. 展示的方向（1 主版 + 1 变体）

| 文件 | 方向 | 气质定位 | 差异点 |
|------|------|----------|--------|
| docs/design/prototype/wuzhufolio-light.html | 主版 · 私人账本（暖纸浅色） | 可信的私人账本 / 公证簿，温暖、克制、数据主权感 | 暖纸底 #F6F4EF、墨绿 accent #1F5A48、衬线大数字 |
| docs/design/prototype/wuzhufolio-dark.html | 变体 · 安全控制台（墨炭深色） | 硬件钱包管理器 / 安全控制台，冷峻、专注、夜间使用 | 墨炭底 #151A18、黄铜 accent #D0A85C、等宽数字强化* |

---

> \* 更正（V1 评审 F6）：「等宽数字强化」实际未实现（两版字体栈完全相同），差异仅为默认配色；已随方向选定闭环。
> 2026-08-31 更新：变体文件 `wuzhufolio-dark.html` 已按「单一真源 + 双主题」原则**删除**（见第 7 节）；本表仅作历史归档记录。

## 3. 设计依据（form 来自内容）

- 母题：「账本/安全」——产品本质是“只属于你的、本地的、可审计的资产账本”，用账本式衬线大数字 + 等宽数据列 + 单 accent（墨绿/黄铜），而非加密行业常见的霓虹紫/荧光绿。
- 信息密度：产品卖点是数据/追踪/隐私（Tracker 类），按 huashu-design「高密度型」——每屏保留 ≥3 处差异化数据，不做装饰性 icon。
- 反 slop：禁用紫渐变、emoji 图标、圆角卡片左彩条、GitHub-dark 霓虹；深色版用墨炭+黄铜（作者意图的暗色）。

---

## 4. 人工选择（已选定）

**人工选择原话：**「选主版。」（2026-08-31；N1 订正：原误记为 08-30）

- [x] 选 **主版（暖纸浅色）** 深化 ← **已选定**
- [ ] 变体（墨炭深色）→ 归档保留，作为后续可选参考，不再深化
- [ ] 混合 / 重来 → 未选

---

## 5. 选定后按人工反馈的深化记录（2026-08-31；N1 订正：原误记为 08-30）

主版（wuzhufolio-light.html）按人工三条反馈深化：

1. 行情 Key 配置流程补全：设置页「行情数据源（CoinGecko API Key）」与「行情兜底（CoinMarketCap API Key）」由“未配置”占位按钮改为可点击的配置 Modal —— 输入 Key → 保存（不填=保持无 Key 公共 API / 无兜底）→ 行内显示「已配置 · ····末4位」+ 顶栏徽章切换为「专属额度」；支持移除 Key。密钥仅存本地、不上传。
2. 资金管理「币种」改单选：增资/撤资表单币种由文本框改为下拉单选（USDT/USDC/DAI/TUSD/BTC/ETH/SOL/BNB，默认基础法币对应稳定币 USDT）；生产环境为「可检索单选下拉」接 coins 目录。
3. 交易表单补全：交易对改为「基于 pair 注册表自动补全」的输入 + 候选下拉（输入 BTC 提示 BTC/USDT、BTC/USDC…，可手输并标「无行情」）；手续费币种选「自定义」时展开第三币种下拉（BNB/CRO/KCS/OKB 等，说明=既非计价也非基础的第三币种，如 BNB 抵扣）。

已验证（Playwright）：pageerror=0；CG/CMC 配置流程、币种单选、交易对自动补全、手续费自定义币种联动均通过。

---

## 6. 截图路径（2026-08-31 登录链路补齐后更新）

- docs/design/prototype/wuzhufolio-light-login.png（登录页 · F1）
- docs/design/prototype/wuzhufolio-light-create.png（账户创建 + 风险确认弹层 · F1）
- docs/design/prototype/wuzhufolio-light-wizard.png（初始化向导 · 四方式 · F1）
- docs/design/prototype/wuzhufolio-light-forgot.png（忘记密码 · F1）
- docs/design/prototype/wuzhufolio-light.png（仪表盘 · 明亮主题）
- docs/design/prototype/wuzhufolio-light-dark-theme.png（仪表盘 · 暗色主题 · 同一文件内置档位）
- docs/design/prototype/wuzhufolio-light-settings.png（设置页 · 含 网络 / 日志与诊断 分组）

（原 `wuzhufolio-dark.png` 已随变体删除）

---

## 7. 「单一真源 + 双主题」原则（2026-08-31 人工确立）

**人工指令原话（2026-08-31）**：「确立『单一真源 + 双主题』原则——wuzhufolio-light.html 是唯一原型真源，暗色主题是其内置档位、随深化自动维护；dark.html 放行前标注『已归档·请勿作为基准引用』或直接删除（避免 P4 被误当暗色基准引用）；F4 对比度修正须两套主题分别验证（暗色 ink3 3.51:1 同样不达标）。」

- **唯一真源**：`docs/design/prototype/wuzhufolio-light.html`。暗色主题 = 该文件 `data-theme="dark"` 内置档位；所有深化组件一律以 CSS 变量取色、随深化自动获得双主题适配；主题切换时环形图即时重渲染（N5 修复）。
- **处置**：`wuzhufolio-dark.html` 与 `wuzhufolio-dark.png` 已**删除**（归档件冻结于深化前状态、交互基准失效，仅剩配色参考价值且存在被 P4 误引风险；处置方式在「标注归档」与「删除」中取删除）。
- **P4 基准引用规则**：UI 视觉基准一律引用 `wuzhufolio-light.html`；暗色视觉基准 = 该文件切换至暗色主题的状态（截图 `wuzhufolio-light-dark-theme.png`）；**禁止引用已删除的 dark.html**。
- **对比度验证规则**：token 修正与新增颜色须**两套主题分别**按 WCAG AA 验证（本轮 F4 已执行，实测值见 design-tokens.md §2.4）。
