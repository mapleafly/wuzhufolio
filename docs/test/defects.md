# WuZhuFolio P6 缺陷与问题清单（docs/test/defects.md）

> **阶段**：P6 系统测试与质量 · 启动指令：人工「执行P6」（2026-09-14）
> **有效需求基线**：PRD V2.0 + Δ{D21, D24, D25, D26, D27, D28, D29, D30, D31, D32, D33}
> **级别口径**：P0 数据/密钥/启动/主流程阻断 · P1 主要功能错误或验收标准未满足 · P2 次要偏差/体验/可诊断性 · P3 文案细节
> **变更控制**：凡触及已通过模块的行为/数据/接口，均给出 `AGENTS.md §8.1` 分级建议 + 影响面扫描，
> **由人工在 P6 门拍板**（Agent 不自行定级 C1/C2；纯实现偏差按 C0 处理并留痕）。
> **DoD 关系**：P0/P1 必须清零（本清单 P0=0 / P1=0）；P2 必须给出明确处理结论（修复或登记 + 到期检查点）。

---

## 0. 结论汇总

| 级别 | 数量 | 状态 |
|------|------|------|
| **P0** | **1** | **DEF-17**（Windows 跨零点启动被日志轮转竞争打挂）→ **已修复并加回归**，见 §1.6 |
| **P1** | **0** | — |
| **P2** | 6 | **4 项已修复**（DEF-01/02/03/06）· **2 项已按人工裁决处置**（DEF-04 登记 P8；DEF-05 按 C0 文档澄清并已回写） |
| **P1（人工门新增）** | 3 | **均已修复**：**DEF-13**（Tab 焦点链重复目标 → 页面内容键盘不可达）、**DEF-15**（Windows 托盘菜单中文乱码）、**DEF-20**（表单候选选中后焦点掉出弹窗）——见 §1.5/§1.7 |
| **P2（人工门新增）** | 4 | **均已修复**：**DEF-14**（登录页回车不提交）、**DEF-18**（托盘菜单不随界面语言）、**DEF-19**（托盘菜单不随语言**即时**切换，需重启）、**DEF-21**（走查提案 A：焦点入页面 + 外壳退出键）；另 **DEF-16** 为口径确认（非缺陷） |
| **P1（第五轮 · 真实只读 Key 冒烟）** | 2 | **均已修复**：**DEF-22**（设置页弹层落在滚动容器内 → 撑开页面/挤占内容）、**DEF-25**（添加 API 密钥首次同步失败 → 弹窗不关但密钥已保存）；**DEF-23** 为同根因的恢复弹窗错位（已修复） |
| **P2（第五轮）** | 1 | **已修复**：**DEF-24**（设置页层级字号/字重不统一）；另 **DEF-26** 为**核实结论（非缺陷）**：同步只追加交易所成交、不覆盖手写交易（已加回归） |
| **P1（第六轮 · GUI 全流程 × 三档分辨率）** | 1 | **已修复**：**DEF-27**（切页后焦点被子树遍历随机落到页面中部字段 → 设置页一打开就滚到「手续费→买入费率」） |
| **P2（第六轮）** | 3 | **均已修复**：**DEF-28**（窄窗表格列被压到内容宽度以下 → 标签竖排/数字换行/行高参差）、**DEF-29**（窄窗过滤按钮与操作列被压成竖排）、**DEF-30**（行情页搜索候选浮层不随清空/Esc 收起——在途搜索结果把浮层顶回来） |
| **P2（第八轮 · GUI 全流程复验）** | 3 | **均已修复**：**DEF-39**（仪表盘卡片指标两级口径与原型不符 → 第一行明显偏大）、**DEF-40**（表格只有横线，补**纵向**列线）、**DEF-41**（币种详情成交表未接入统一表格 → 无单元线） |
| **P1（第十轮 · 打包版启动）** | **1** | ✅ **已闭环（2026-09-21 人工实机复验通过）**：**DEF-42**（Windows 安装版双击启动 → 弹窗「Failed to launch JVM」→ TC-MAN-02 步骤 3 阻断）→ 根因 = **jpackage Windows 启动器无法处理「系统 ANSI 代码页不可表示的安装路径」**；修复 = **D34**（per-machine 装到 `C:\Program Files\WuZhuFolio` + 关闭目录选择页 + 三平台便携版 + CI 启动冒烟）；**该机症状最终归属 DEF-43**（见下行）。**第十一轮复验**：装 DEF-43 修复版后 **TC-MAN-02 开机自启 ✅ / TC-MAN-11 便携版 ✅** |
| **P1（第十轮续 · 打包版启动／无障碍）** | **1** | ✅ **已闭环（CI + 人工实机复验通过）**：**DEF-43**（开启 Java Access Bridge / 辅助技术时，打包版在 AWT 初始化抛 `AWTError` → 启动器只显示无细节的 `Failed to launch JVM`）→ 根因 = 裁剪运行时的模块集**缺 `jdk.accessibility`**；修复 = 模块集补齐（正向）+ `AssistiveTech` 兜底校验（防御），CI 增「开启辅助技术」冒烟变体 |
| **P2（第七轮 · 真实桌面 GUI 全流程 × 三档分辨率）** | 8 | **均按统一方案修复**：**DEF-31**（高 DPI 下币种列第三枚徽标被裁）、**DEF-32**（卡片大数字换行变形）、**DEF-33**（表格滚动条压住操作列 / 长数字换行）、**DEF-34**（环形图图例币种名换行）、**DEF-35**（截断数据无悬停全值）、**DEF-36**（表单弹窗多一条「不到一行」的滚动条）、**DEF-37**（列宽分配不保证最小宽）、**DEF-38**（列表缺单元线）—— 总体方案见 `docs/design/responsive-components.md` |
| 测试缺陷（CI 暴露） | 1 | **DEF-12** 已修复（见 §3） |
| **P2（P7 发布后 · 0.1.0 正式版人工验收 · Windows）** | **1** | ✅ **已修复 + 人工复验通过（2026-09-25 Windows 走查）**：**DEF-47** 组件走查（DEV）页泄漏到正式发布版侧边栏（人工拍板 **C0 + 方案甲「加构建期开关」** → `BuildInfo.DEV_UI`，默认 false） |
| **P2（P7 发布后 · 0.1.0 Linux 真机验收 2026-09-24）** | **5** | ✅ **均已修复 + 人工复验通过（2026-09-25：「托盘功能通过，windows走查通过」）**：：**DEF-48** 托盘图标显示不全（**C1** · D36）、**DEF-49** 托盘缺「刷新行情」+ 同步无可见反馈（**C1** · D36）、**DEF-50** 「打开主界面」不置前（**C0**）、**DEF-51** 候选排序/上限/滚动不一致（**C1** · D36，合并人工第 4/8/9/10 条）、**DEF-52** Ubuntu 图标为通用齿轮（**C1** · D36）；另 **1 项增量提案已立项实施**（成交币自动进行情自选 → **D35**）与 **1 项文档口径**（JDK 17/21 与 mise，非缺陷，已按指令修订） |
| **P3 / 观察项** | 7 | 登记（DEF-07…DEF-12 + **DEF-53** ✅ 已收口），详见 §3 |
| 合计 | **53** | 编号连续至 **DEF-53**（**本轮 7 项（DEF-47…DEF-53）已全部收口，且经人工复验通过（2026-09-25）**；其余均闭环/已登记）（其中 DEF-44/45/46 为测试文档/口径类，C0）。P0 曾出现 1 项（DEF-17）· P1 曾出现 6 项（DEF-13/15/20/22/25/27）——**均已修复闭环**（人工门实测暴露）；**P1 全部闭环（DEF-43 人工实机复验通过；DEF-42 经第十一轮 TC-MAN-02/TC-MAN-11 复验通过；上游限制仍按裁决登记 P8）**；P2 全部有明确结论 ✅ |

> 结论：**P0 = 0**；**P1 三项（DEF-13/DEF-15/DEF-20）由人工门实测暴露并已修复闭环**（修复即回归，见 §1.5/§1.7），
> P2 各项在人工 P6 门全部裁决完毕或已登记（见 §0.1），**无遗留未决项**；**P1 已全部闭环**（见下）。
> ✅ **2026-09-21 第十一轮更新（人工复验通过）**：人工原话「**P6的开机自启、便捷版运行，两项都通过**」——
> **TC-MAN-02 开机自启 ✅ / TC-MAN-11 便携版解压即用 ✅** 两项判定通过，被测形态 = 打包安装版与 D34 便携包
> （`manual-test-guide.md §18.1` 的 DEF-43 修复版，`build=0.1.0+6f2c77b`）。据此：
> **DEF-42 与 DEF-43 一并闭环 → P1 = 0，P6 DoD「P0/P1 清零」达成**（复验留痕见 `manual-test-guide.md §19`、
> `test-cases.md §7.0`、`test-report.md §0/§6.1`）。**人工门累计通过 10/11**——**TC-MAN-01 托盘走查已于同日（第十一轮续）
> 在 Windows 11 真实桌面判定通过 ✅**（乱码 DEF-15 与语言跟随 DEF-18/19 均未复现），该用例仅剩 **Linux / macOS 托盘未测**；
> 唯一未执行项 **TC-MAN-09**（出站抓包 + 权限实证）**已按人工拍板（同日「先关闭 P6 门并把两项登记为 P7 携带」）显式延期至 P7**
> （到期检查点 = P7 发布前）。
> ✅ **P6 门已关闭（2026-09-21）**：DoD 三项全部达成（P0/P1 = 0 + P2 明确结论 + 安全清单全部通过）→ **P7 发布解锁**。
> ⚠️ **2026-09-15 第十轮更新**：**DEF-42（P1）** = Windows 安装版启动器弹「Failed to launch JVM」（TC-MAN-02 步骤 3 阻断）——
> **根因已由 CI 对照实验实证**（启动器无法处理「系统 ANSI 代码页不可表示的安装路径」），并已按人工拍板实施 **D34 修复**
> （per-machine `C:\Program Files\WuZhuFolio` + 关闭目录选择页 + 三平台便携版 + CI 启动冒烟）。
> **P6 DoD「P0/P1 清零」的判定口径**：修复已落地并通过 CI 冒烟，**尚欠人工在 Windows 复验 A2（非 ASCII 用户名机器可启动）与 TC-MAN-02 步骤 3–5**；
> 复验通过即闭环，届时方可关闭 P6 门。
> **第十轮续（同一台人工机的第二次定位）**：控制台调试包给出真实错误 —— 该机开启了 **Java Access Bridge**，而打包运行时缺 **`jdk.accessibility`** 模块 → AWT 初始化抛 `AWTError: Assistive Technology not found` → 新增 **DEF-43（P1）**；修复已实施并在本机复现/验证（见 DEF-43 条目）。
> 2026-09-15 **第十轮（TC-MAN-08 通过 + TC-MAN-02 打包版启动失败）**：**TC-MAN-08 真实桌面 GUI 全流程人工判定通过 ✅**；
> TC-MAN-02 步骤 1–2 正常，步骤 3 双击安装版图标报错 → 新增 **DEF-42（P1）**；同轮根因实证并实施 **D34** 修复。
> 2026-09-15 第四轮 Windows 人工门新增 **DEF-20（P1，已修复）** 与 **DEF-21（P2，焦点流改进，C1 已建档 D31）**。
> 2026-09-15 **第五轮（真实只读 Key 冒烟）**新增 **DEF-22/23（P1，弹层撑开页面/错位，已修复）**、**DEF-24（P2，设置页层级统一，已修复）**、
> **DEF-25（P1，添加密钥保存后弹窗不关，已修复）**、**DEF-26（核实非缺陷：同步不覆盖手写交易，已加回归）**。
> 2026-09-15 **第八轮（GUI 全流程复验）**新增 **DEF-39/40/41（P2，卡片指标口径 / 表格纵线 / 币种详情表接入统一组件）**；
> 同轮 **TC-MAN-05 断网态 ✅** 人工判定通过。
> 2026-09-15 **第七轮（真实桌面 GUI 全流程 × 三档分辨率，含 2560×1600 高 DPI）**新增 **DEF-31…DEF-38（P2，八条同源问题，按 `docs/design/responsive-components.md` 统一方案修复）**；
> 同轮 **TC-MAN-03 读屏（NVDA）✅ / TC-MAN-04 目标机性能 ✅** 人工判定通过。
> 2026-09-15 **第六轮（真实桌面 GUI 全流程 × 1280×800 / 1024×768）**新增 **DEF-27（P1，切页入口焦点不可靠，已修复）**、
> **DEF-28（P2，窄窗表格换行/竖排，已修复）**、**DEF-29（P2，窄窗过滤按钮/操作列竖排，已修复）**、
> **DEF-30（P2，行情搜索候选浮层不收起，已修复）**；该轮 TC-MAN-06（纯键盘全流程）、TC-MAN-07（真实只读 Key 冒烟）、
> TC-MAN-10（外链与关于页）**人工判定通过** ✅。

### 0.1 人工裁决记录（2026-09-14 · 原话「裁决：5项都按建议来处理」）

| # | 裁决事项 | 人工裁决 | 落地状态 |
|---|----------|----------|----------|
| ① | 行情请求币种集合隐私最小化 | **接受现状 + 记入用户指南/隐私声明** | ✅ 已登记为 **P7 用户指南/隐私声明** 内容项（见 `test-report.md §5.3/§6`、STATUS P7 携带项）；评估结论与量化数据见 `test-report.md §5.3` |
| ② | DEF-03 币种详情缺「时间」筛选 | **本轮补做（C1 mini 闭环）** | ✅ **已实施**：决策档 `docs/dev/decisions/D30-币种详情时间筛选.md` + 台账 D30 行 + 索引 + task-breakdown **T12.5** + `ia.md §2.6` + 原型/verify 同步 + 代码与 UI 回归（详见 §2 DEF-03） |
| ③ | DEF-04 CMC 兜底计入 CG 额度账本 | **登记 P8** | ✅ 已登记（P8 立项输入：账本增 provider 维度 + 旧载荷兼容；影响面见 §2 DEF-04） |
| ④ | DEF-05 interaction「列表滚动加载」口径 | **按 C0 文档澄清** | ✅ **已回写**：`interaction.md §2.1` 增「列表装载口径」注 + §3-2 措辞订正（本地库单次装载 + `LazyColumn` 虚拟化，不适用分页）；大数据量装载耗时登记 P8 观察项 |
| ⑤ | DEF-01 / DEF-02 / DEF-06 定级 | **维持 C0** | ✅ 已确认（三项均为实现偏差/失败模式补全，未改产品语义、未改数据模型与格式；回写见各自条目） |
| ⑥ | **第四轮人工门两项焦点问题定级**（2026-09-15） | **按建议变更分级**（原话）→ **DEF-20 = C0**、**DEF-21 = C1** | ✅ **DEF-20**：C0 勘误（`M7.md`/`M8.md` §勘误 + `interaction.md §3-9` 回写，不建档不进台账）；**DEF-21**：C1 完整落盘 —— 决策档 **D31** + 台账 D31 行 + 决策索引 + `task-breakdown **T12.6**` + `ia.md §1.1` + `interaction.md §3-9` + `M12.md §1.7`（验收 A1–A6 见 D31 §6） |
| ⑦ | **第五轮人工门（真实只读 Key 冒烟）四项定级**（2026-09-15） | **拍板，按建议变更分级**（原话）→ **DEF-22/23 = C0**、**DEF-25 = C0**、**DEF-24 = C1** | ✅ **C0 三项**：`M6/M9/M10` §勘误 + `api-contracts.md §3`（`addAndSync` 落库后失败不得上抛 + 去重口径）+ `design-tokens.md §4.2`（弹层承载位置）+ `AGENTS.md §7.3-5/6`；**C1 一项（D32）**：决策档 + 台账 D32 行 + 索引 + `task-breakdown **T10.5**` + `design-tokens.md §3` 层级标准（验收 B1–B5）；**DEF-26** 为核实非缺陷（同步不覆盖手写交易，已加回归）
| ⑧ | **第六轮人工门（GUI 全流程 × 三档分辨率）四项定级**（2026-09-15） | **按建议**（原话）→ **DEF-27 / DEF-28 / DEF-29 / DEF-30 全部 C0** | ✅ 四项均按 C0 勘误登记：`M5.md`（候选浮层收起 + 入口焦点声明）、`M7.md`（窄窗表格/按钮 + 入口焦点）、`M12.md`（入口焦点契约 + 资产表窄窗）§勘误；技术/设计回写 `design-tokens.md §4.3-1`（窄窗适配口径）、`interaction.md §2.7/§3-9/§3-13`、`ia.md §1.1`；均**不建档、不进台账**（`AGENTS.md §8.1`）。同轮 TC-MAN-06 / TC-MAN-07 / TC-MAN-10 **人工判定通过** ✅ | ✅ 四项均按 C0 勘误登记：`M5.md`（候选浮层收起 + 入口焦点声明）、`M7.md`（窄窗表格/按钮 + 入口焦点）、`M12.md`（入口焦点契约 + 资产表窄窗）§勘误；技术/设计回写 `design-tokens.md §4.3-1`（窄窗适配口径）、`interaction.md §2.7/§3-9/§3-13`、`ia.md §1.1`；均**不建档、不进台账**（`AGENTS.md §8.1`）。同轮 TC-MAN-06 / TC-MAN-07 / TC-MAN-10 **人工判定通过** ✅ |
| ⑨ | **第七轮人工门（GUI 全流程 × 三档分辨率，含 2560×1600 高 DPI）八项定级 + 总体方案**（2026-09-15） | **按建议**（原话）→ **DEF-31/32/33/34/36/37 = C0**；**DEF-35/38 与「响应式与组件统一」= C1** | ✅ **C0 六项**：`M5/M7/M12` §勘误 + `design-tokens.md §4.3-1` + `interaction.md §3-13`（不建档不进台账）；**C1（D33）**：决策档 `docs/dev/decisions/D33-响应式与组件统一.md`（含影响面扫描 + 验收 C1-1…C1-8）+ 总体方案 `docs/design/responsive-components.md` + 台账 D33 行（有效需求串 `PRD V2.0 + Δ{…, D32, D33}`）+ 决策索引 + `task-breakdown **T12.7**` + `M12.md §1.7` |
| ⑬ | **第十一轮：打包版复验判定**（2026-09-21） | **两项均判定通过**（原话「**P6的开机自启、便捷版运行，两项都通过**」） | ✅ **TC-MAN-02 开机自启**（打包安装版，DEF-43 修复版 run 35115439554）与 **TC-MAN-11 便携版解压即用**（D34 `portable\WuZhuFolio-portable-windows-x64.zip`）双双通过 → **DEF-42/DEF-43 闭环、P1 归零**，P6 DoD「P0/P1 清零」达成；D34 §6 的 A2/A5/A6 三项人工验收补齐；`manual-test-guide.md §19` 为留痕 |
| ⑫ | **第十轮续：DEF-43 定级 + DEF-42 上游限制处置**（2026-09-16） | **按建议**（原话「按建议」/「按建议两条」）→ **DEF-43 = C0**（实现/打包偏差：PRD §6 无障碍基线已要求读屏可用，打包漏装 `jdk.accessibility`）；**DEF-42 的 jpackage 非 ASCII 路径限制 = 登记 P8 观察项 + 写入 P7 用户指南提示** | ✅ **DEF-43（C0）**：模块勘误 `M13.md §7` ③ + `ADR-006 §1.1`（无障碍模块为必需模块）+ 代码/测试/CI 已落地，**不建档、不进台账**；**DEF-42（P8）**：登记项见下（修正方案 = 上游修复前以「per-machine ASCII 默认目录 + 关闭目录选择页 + 便携版」缓解，D34 已实施）；**P7 携带**：`user-guide.md` 增「安装/解压路径请使用纯英文（ASCII）目录」提示 + 便携版说明 |
| ⑪ | **第十轮 DEF-42（打包版启动失败）两项处置**（2026-09-15） | **A 维持 C0（追认）+ B 采纳 C1（D34），下一步实施 B**（原话） | ✅ **A（C0）**：CI「打包版启动冒烟」保留观察期非阻断形态（`continue-on-error` + 判定串），稳定数轮后再按拍板改阻断式；**B（C1 · D34）**：安装形态改 per-machine（`C:\Program Files\WuZhuFolio`）+ 关闭目录选择页 + 三平台便携版产物 + 安装版实跑冒烟，落盘清单见决策档 D34 §5（台账 D34 行 / 决策索引 / `T12.8` / ADR-006 §1.1 / `M13.md §9`） |
| ⑩ | **第八轮人工门（GUI 全流程复验）三项定级**（2026-09-15） | **按建议**（原话）→ **DEF-39 / DEF-40 / DEF-41 全部 C0** | ✅ 三项均按 C0 勘误登记：`M12.md` §1.7（卡片指标两级 `metricPrimary`/`metricSecondary` + 表格纵线 + 币种详情表接入统一组件）、`M7.md` §5.z（交易/资金表纵线）；回写 `design-tokens.md §3`（指标两级 27/21，对齐原型）/ §4.3-1（表格网格线）、`responsive-components.md §2/§3`；均**不建档、不进台账**。同轮 **TC-MAN-05 断网态人工判定通过** ✅ |

---

---

## 1. 本轮修复（C0 实现偏差 / 失败模式补全）

### DEF-01 ✅ 已修复（建议 C0）· 备份导出遇到不可解密 `api_keys` 密文时抛原始加密异常

| 项 | 内容 |
|----|------|
| **来源** | P5 登记转 P6（`integration-report.md` P5-4：备份导出失败模式） |
| **现象** | 库内 `api_keys` 凭证列与当前账户 DEK/AAD 不匹配时，`exportBackup` 直接上浮 `AuthenticationFailedException: AES-GCM authentication failed`（无类型化错误、无用户可读文案）；若走**全量覆盖恢复**，该异常从「覆盖前临时备份」路径冒出，UI 只显示「恢复失败：api_keys credential unreadable…」这类内部英文信息 |
| **触发面** | DB 被外部改动 / 位翻转损坏 / 跨库误拷。正常路径不可能出现（明文永不落盘、密文只由本服务写入） |
| **根因** | 导出侧**没有定义任何错误面**（`BackupService` 只声明了导入侧 `CproDecodeException` 三态），`apiKeyRowsToPayload` 未包类型化边界 |
| **处置** | ① 新增 `domain/backup/BackupExportException`（`Reason.CREDENTIAL_UNREADABLE` + `keyName`）；② `DefaultBackupService.apiKeyRowsToPayload` 捕获 `AuthenticationFailedException` / `IllegalArgumentException` → 类型化上浮（**导出整体中止、不落文件**）；③ UI：`BackupCopy.exportErrorCopy`（导出）/ `restoreErrorCopy`（恢复）→ 中英双档整句；④ `interaction.md §2.9` 补异常态表；⑤ `api-contracts.md` §3 M9 补录 + §4 新增错误码 `BACKUP_EXPORT_FAILED` |
| **回归测试** | `DefaultBackupServiceTest::export aborts with typed error when a stored credential cannot be decrypted`（含「不留 .cpro/临时文件」+「错误对象不含密钥材料」断言）<br>`DefaultBackupServiceTest::export aborts when only the passphrase column is unreadable`<br>`DefaultBackupServiceTest::full overwrite aborts before clearing data when the safety backup cannot be created`（**数据完整性**：清库在临时备份之后，失败时业务行数与密钥行数不变）<br>`ui/backup/BackupExportErrorCopyTest`（中英双档含密钥别名、不含原始加密异常文案） |
| **影响面扫描** | 代码：`DefaultBackupService.kt`（导出装配）、`BackupViewModel.kt`（两侧错误映射）、`BackupCopy.kt`、`BackupStrings.kt`（zh/en 各 +2 条）；文档：`interaction.md §2.9`（新增）、`api-contracts.md` §3/§4；测试：上述 4 处。**无数据模型/格式/加密链变更**（失败即不产文件，旧 `.cpro` 兼容性不受影响） |
| **未覆盖边界（登记 DEF-11）** | `keyName = null` 分支（仅当上游遗漏 keyName 时可达）与 `extra` 列损坏未单独造例——同一代码路径已由 passphrase 用例覆盖 |

### DEF-02 ✅ 已修复（建议 C0）· Binance 签名请求未发送 `recvWindow`（文档契约漂移）

| 项 | 内容 |
|----|------|
| **来源** | P6 出站面复核（隐私/契约联合复核） |
| **现象** | `ADR-004 §2` 与 `api-contracts.md §2.1` 明文规定认证 = `X-MBX-APIKEY` + `timestamp` + **`recvWindow`（默认 5000）** + 签名；实现只发 `timestamp`。`ExchangeConfig.RECV_WINDOW_PARAM` / `DEFAULT_RECV_WINDOW` 两个常量**声明后零消费**（死代码 + 契约漂移） |
| **影响** | 行为等价（Binance 未显式传 `recvWindow` 时默认 5000ms），但契约与实现不一致；一旦后续调整该常量不会生效 |
| **处置** | `BinanceAdapter.buildSignedQuery` 显式补发 `recvWindow=5000`（与文档逐字对齐，常量消除死代码）；KDoc 注明 P6 勘误 |
| **回归测试** | `BinanceAdapterTest::validate credentials hits signed account endpoint with headers and signature`（新增 `recvWindow=5000` 断言） |
| **影响面扫描** | 代码：`BinanceAdapter.kt`（1 处签名构造）；文档：无需改（原本即如此规定）；测试：1 处断言。无数据/接口/语义变化 |

### DEF-06 ✅ 已修复（伴随项，建议 C0）· 恢复向导未映射导出侧类型化错误

| 项 | 内容 |
|----|------|
| **来源** | P6 代码勘查（DEF-01 修复后的遗留缺口 G1） |
| **现象** | `BackupViewModel.decodeCopy` 只识别 `CproDecodeException`；全量覆盖路径的临时备份失败会落到「恢复失败：+ 原始英文信息」分支 |
| **处置** | 抽出 `BackupCopy.restoreErrorCopy(t)`（导入三态 + `BackupExportException` → 恢复语境整句），`decodeCopy` 委托之；配套测试见 DEF-01 |
| **影响面** | `BackupCopy.kt` / `BackupViewModel.kt` / `BackupStrings.kt`；无契约变化 |

---

### DEF-13 ✅ 已修复（**P1** · 人工门实测暴露 · 建议 C0）· Tab 焦点链重复目标导致页面内容键盘不可达

| 项 | 内容 |
|----|------|
| **来源** | P6 人工门 Windows 11 走查（2026-09-14）：①「按 Tab 只能在左侧功能项移动焦点」；②「进入资金页后无法用键盘执行增资，焦点进不到页面组件」 |
| **复现** | Compose UI 探针（`:ui:test`）实测序列 = `nav-DASHBOARD → … → nav-GALLERY → theme-toggle → (无焦点) → …`：页面内按钮（`dashboard-refresh` / `fund-add-deposit`）**完全不可达** |
| **根因** | `WzButton`/`WzSelect` 同时挂了 `clickable`（自身即焦点目标）与**显式 `.focusable()`** → **同一节点两个焦点目标**；`WzModal`/认证弹层卡片同样如此（`clickable` 吞点击 + `focusable` 承接 Esc）。Tab 会在「有语义、有焦点环的目标」与「无标识的隐形目标」之间交替，隐形目标上按 Enter/Space 无任何反应 → 用户感知为「焦点动不了 / 按键没反应」 |
| **修复** | ① `WzButton`/`WzSelect` 去掉重复的显式 `.focusable()`（保留 `clickable` 自带焦点与 `onFocusChanged` 焦点环）；② `WzModal`/`GateWidgets` 卡片把「吞点击」的 `clickable` 换成 `pointerInput { detectTapGestures {} }`（不产生焦点目标），保留唯一 `focusable()` 承接无输入框弹窗的 Esc，并显式 `semantics(mergeDescendants = true)` 维持原有语义边界（4 个弹层用例靠它取节点） |
| **回归** | 新增 `ui/KeyboardA11yUiTest`：**Tab 每一步必须恰好一个可聚焦且带标识的节点**（隐形目标会让断言红）+ 页面内按钮可达（`probe-funds-btn`/`probe-withdraw-btn`）+ 侧边栏/顶栏仍可达；修复后实测序列 = `nav-* → topbar-refresh-quotes → topbar-sync → theme-toggle → 页面按钮 → 循环`，**无空焦点步进** |
| **影响面扫描** | 代码：`WzButton`/`WzSelect`/`WzModal`/`GateWidgets`（4 文件，仅焦点/指针修饰链）；不涉数据、接口、schema、备份格式；既有 UI 测试全量复跑绿（其中 4 个弹层用例因语义边界写法变化同步暴露并已修复） |
| **PRD 回溯** | PRD §6「无障碍基线：桌面端支持全键盘导航（Tab 焦点顺序合理、核心操作可达）」；`interaction.md §3-9` |

### DEF-14 ✅ 已修复（P2 · 建议 C0）· 登录页输入密码后回车不提交

| 项 | 内容 |
|----|------|
| **来源** | P6 人工门 Windows 走查：「填完密码按回车没反应，需 Tab 到『登录』按钮再回车」 |
| **根因** | 登录表单只在按钮 `onClick` 里做提交，输入框未接 Enter 通路（桌面端物理回车不触发 IME action） |
| **修复** | `WzTextField` 新增 `onSubmit`：物理回车走 `onPreviewKeyEvent`（`Enter`/`NumPadEnter`），并同时声明 `ImeAction.Done` + `KeyboardActions(onDone)`（软键盘/无障碍路径一致）；登录页把提交逻辑抽为局部函数，密码框回车与按钮**共用同一路径**（空密码回车 → 内联错误，不提交） |
| **回归** | `ui/KeyboardA11yUiTest`：`enter in password field submits the login form`（断言提交参数三元组）+ `enter with empty password shows the inline error instead of submitting` |
| **影响面** | `WzTextField`（新增可选参数，既有调用点零改动）/ `GatePages` 登录页；不涉数据与接口 |

### DEF-15 🔁 **二次修复后仍复现 → 三次修复（Skia 自绘菜单）**（**P1** · 人工门实测 · 建议 C0）· Windows 托盘菜单中文乱码

| 项 | 内容 |
|----|------|
| **来源** | P6 人工门 Windows 11 走查：「托盘有三行菜单，文字全是乱码」 |
| **根因** | Compose Desktop 的 `Tray` 用 **AWT `PopupMenu`/`MenuItem`** 承载菜单（`ui-desktop-1.12.0.jar` 的 `Tray_desktopKt` 反汇编实证：`java.awt.SystemTray` + `java.awt.PopupMenu`），菜单文字因此**不由应用内嵌字体渲染**，而由目标机 AWT 逻辑字体交给系统绘制——该路径缺 CJK 覆盖即乱码；且 Compose 的 `Item(text)` 无法注入字体。附带缺陷：三个菜单项**硬编码中文**，英文界面下不跟随 |
| **修复（第 2 版，2026-09-14）** | 自建 AWT 托盘宿主 + 每个 `MenuItem` 显式挂**内嵌 Noto Sans SC**（`TrayFont`，`canDisplayUpTo` 校验覆盖）+ 文案入 i18n + 通知改 `displayMessage` |
| **第 2 版结果** | ❌ **人工复验仍乱码** → **推翻字体假设**：不是「系统缺字形」，而是 **AWT 菜单文本的渲染/转码路径本身**（Windows 上由 AWT→native 菜单绘制，应用无法干预）。这也解释了为何换字体无效 |
| **修复（第 3 版，2026-09-15）** | **彻底绕开 AWT 文本**：AWT 只负责**托盘图标与点击事件**（图像/坐标与文本无关），右键回调屏幕坐标 → 由 **Compose/Skia 自绘菜单窗口**渲染三项（`ui/tray/TrayMenuContent` + `app/tray/TrayMenuWindow`）。字体/渲染链与应用界面完全一致（界面中文已实证正常）。交互贴合原生：无边框置顶、**失焦即关**、Esc 关闭、点选执行并关闭；菜单容器自取焦点保证 Esc 可达。`TrayFont`（AWT 字体方案）随第 2 版一并删除 |
| **回归（第 3 版）** | `ui/tray/TrayMenuContentUiTest`（3 项）：三项按当前语言渲染 / 点选各自触发动作并关闭 / Esc 关闭；字体链路 = 应用同一 Compose 主题（界面中文正常即此路径可信） |
| **构建标识（配套）** | 人工反馈需能确认「跑的是哪一版」：`BuildInfo.COMMIT` 由构建期注入 git short SHA，启动日志首行输出 `bootstrap ok \| build=0.1.0+<sha> \| …`（`AppBootstrap`）——复验时请以此确认已装新版 |
| **待办** | 本机（WSLg）无系统托盘，**无法目视复验** → 请人工用**新构建**重走 TC-MAN-01：① 托盘右键三项为可读中文；② 切换 English 后为英文；③ 三项动作分别生效；④ Esc/点别处可关闭菜单。若仍乱码，请提供截图 + 启动日志 `build=` 行 + Windows 显示语言/区域设置（届时可判定为更深层的系统级文本路径问题） |
| **影响面** | 代码：新增 `app/tray/AwtTrayHost.kt`、`app/tray/TrayFont.kt`，`AppHost.kt` 托盘装配与通知路径改写，`ui/i18n/ShellStrings.kt` +3 键 ×2 档；不涉数据/接口/schema；托盘能力探测与降级口径不变 |

### DEF-18 ✅ 已修复（P2 · 人工门二轮复验暴露 · 建议 C0）· 托盘菜单不随界面语言切换（重启亦不变）

| 项 | 内容 |
|----|------|
| **现象** | 托盘菜单改自绘后中文正常，但**界面切英文后托盘仍为中文，重启后仍是中文** |
| **根因** | `WuzhuTheme` 会把**全局** `I18n` 设成它收到的 `language` 参数（默认 `AppLanguage.ZH`）。托盘菜单是**独立窗口**，其主题调用未传 language → 每次打开菜单都把全局语言重置为中文；而菜单文案又通过全局读取器 `shellStrings` 取值 → **恒为中文**（与「重启不变」一致：菜单窗口每次都把自己置回 ZH） |
| **修复** | ① `ui/tray/TrayLabels.kt` 新增 `trayLabels(language)`：按 `AppLanguage` **显式**从 `ShellStringsZh/En` 取词，不依赖全局状态；② `TrayMenuWindow` 新增 `language` 参数，主题（`WuzhuTheme(themeMode, language)`）与文案用**同一语言**；③ `AppHost` 传入 `runtime.uiState.language`（与主界面同源） |
| **回归** | `ui/tray/TrayLabelsTest`（2 例）：**全局 I18n 被重置为中文时，按 EN 取词仍须英文**（正是本缺陷的复现条件）+ 反向（全局英文时按 ZH 取词仍中文）；`TrayMenuContentUiTest` 改用 `trayLabels` 取词 |
| **影响面** | 代码：`ui/tray/TrayLabels.kt`（新增）、`app/tray/TrayMenuWindow.kt`、`app/AppHost.kt`；app 模块原先的 `TrayLabels` 定义移入 ui 模块；不涉数据/接口/schema |
| **教训** | 凡**独立窗口/独立组合树**，不得依赖「由主题设置的全局状态」取值；语言、精度等全局读取器必须由调用方显式传入。同类风险点：今后若新增二级窗口（如独立面板），沿用同一口径 |

### DEF-19 ✅ 已修复（P2 · 人工门三轮复验暴露 · C0 实现补全）· 托盘菜单不随语言**即时**切换（切英文后要重启才变）

| 项 | 内容 |
|----|------|
| **现象** | DEF-18 修复后：切 English → 托盘菜单仍中文；**重启后变英文**。再切回中文 → 托盘仍英文，**再重启才变中文** |
| **根因** | 菜单读的是 `Runtime.uiState`——它是**启动时快照**（构造后不再变化）。`ShellViewModel` 的语言切换只做两件事：更新自己持有的状态 + 通过 `onShellPreferenceChange` 写 settings；主壳之外的组件（托盘菜单窗口）**没有任何可观察来源**，只能看到启动值 → 重启才生效 |
| **修复** | 新增 `app/UiPreferenceState`：把主题/盈亏配色/语言做成 `StateFlow<UiState>`（键与 `ShellViewModel` 持久化键一致 `theme`/`pnl_scheme`/`locale`，非法值/无关键忽略不抛）；`Runtime` 暴露 `uiPreferences`；`Main.onShellPreferenceChange` 在写库后同步调用 `apply(key, value)`；`AppHost` 用 `collectAsState()` 订阅并把 `theme`/`language` 传给托盘菜单窗口 → **菜单即时跟随**（主题同样即时，不再等重启） |
| **回归** | `app/UiPreferenceStateTest`（3 例）：语言键即时生效（切英/切回中）、主题与盈亏配色键生效、无关键与非法值不影响状态 |
| **影响面** | 代码：新增 `app/UiPreferenceState.kt`、`AppBootstrap.Runtime`（+1 字段与新构造）、`Main.kt`（偏好钩子 +2 行）、`AppHost.kt`（订阅 + 传参）；不涉数据/接口/schema；`Runtime.uiState` 保留为「主壳初始值」语义 |
| **口径沉淀** | 与 DEF-18 同源：**跨组合树共享的界面偏好必须有可观察状态**（快照只可用于「初始值」）。后续新增二级窗口/托盘类组件一律订阅 `uiPreferences` |

### DEF-20 ✅ 已修复（**P1** · 人工门四轮实测暴露 · 建议 C0）· 表单候选选中后焦点掉出弹窗（下次 Tab 从侧边栏重来）

| 项 | 内容 |
|----|------|
| **现象** | 「记录增资」的**币种**候选、「添加交易」的**交易对**候选，用键盘选中（Tab 到候选行 + Enter）后候选行消失，**焦点掉出弹窗**；下一次 Tab 从**侧边栏第一项**重新开始，键盘用户被迫重走整条路径（交易表单里甚至要重走 3 次：基础币、计价币、自定义手续费币种） |
| **复现** | 纯键盘：弹窗内输入 `USDT` → 候选出现 → Tab 到候选行 → Enter；观察焦点框消失、再按 Tab 落在侧边栏 |
| **根因** | 候选行由 `clickable` 提供**唯一焦点目标**；选中后该行立即从组合中移除（`candidates = emptyList()`），Compose 焦点系统**无处可恢复**（触发候选的输入框并未保存/恢复焦点）→ 焦点回落到窗口根，即 Tab 序第一个节点（侧边栏首项） |
| **修复** | ① `FundFormModal`：币种候选选中 → 焦点交「数量」（`qtyFocus`）；② `TransactionFormModal`：基础币候选 → 「计价币」（`quoteFocus`）、计价币候选 → 「价格」（`priceFocus`）、自定义手续费币种 → 原字段（`feeFocus`）；③ `WzSelect`：下拉候选选中 → 焦点收回触发框（`triggerFocus`），避免同类「浮层选项消失」路径再次掉焦点；④ 交易候选行补 `tx-suggestion-<id>` 标签（与资金页 `fund-suggestion-<id>` 同口径），供自动化与人工走查定位 |
| **回归** | `FundsPageUiTest::coinPickHandsFocusToQuantityFieldInsideModal`（选中 → 数量聚焦 → 继续 Tab 到「日期时间」仍在弹窗内）<br>`TransactionsPageUiTest::pickingBaseCandidateHandsFocusToQuoteField`（基础币 → 计价币 → Tab 到价格）<br>`TransactionsPageUiTest::pickingQuoteCandidateHandsFocusToPriceField`（计价币 → 价格） |
| **影响面扫描** | 代码：`ui/ledger/FundFormModal.kt`、`ui/ledger/TransactionFormModal.kt`、`ui/components/WzSelect.kt`；测试：上述 3 例 + 新增候选行标签。**不涉数据模型/schema/加密/接口/持久化格式**（纯焦点编排） |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（实现偏差纠正：`AGENTS.md §7.3` 要求弹窗键盘可用，候选消失导致焦点链断裂属实现未达约束；不改产品语义、不新增需求）。模块勘误：`M7.md`/`M8.md` §勘误；不建决策档、不进台账（`AGENTS.md §8.1`） |

### DEF-21 ✅ 已实施（**P2** · 走查提案 A 落地 · 人工拍板 2026-09-15 **C1**）· 焦点流：回车进页面内容 + 外壳退出键

| 项 | 内容 |
|----|------|
| **来源** | 第四轮 Windows 人工门反馈 + `docs/test/keyboard-walkthrough.md §6 改进提案 A`（人工已给方向：「回车选中后直接进页面内容，并能用方向键/Esc 回到侧边栏/顶栏循环」） |
| **诉求** | ① 回车选中侧边栏项后焦点**直接进入页面内容**，不要再逐个 Tab 穿过侧边栏余项与顶栏；② 焦点在页面内时要有**回到外壳循环**的出口；③ 侧边栏内方向键应能上下移动 |
| **实现** | ① 切页 / 进入币种详情子页 / 对**当前项再次回车** → 焦点交页面内容（页面槽挂 `focusRequester`，Compose 语义：请求挂在**非可聚焦容器**上时焦点落到子树内第一个可聚焦控件；容器**不加** `focusable`，避免多出无焦点环的 Tab 停靠点 = DEF-13 教训）；② 页面内**未被页面控件消费**的 Esc / ↑ / ↓ → 焦点回侧边栏当前项（**冒泡阶段** `onKeyEvent`：输入框方向键/下拉导航等已消费的键不受影响）；③ 侧边栏 ↑/↓ 在导航项间移动（`NAV_FOCUS_ORDER` = 六个一级页 + 组件走查页） |
| **三条护栏** | ① **弹层打开时不接管**：`WzModal` 新增 `WzOverlayRegistry.openModalCount` 计数登记，弹层存续期主壳让出 Esc/方向键（否则焦点会跑到弹层背后，弹层开着而键盘已无法操作）；② **组合键不接管**（Ctrl/Alt/Meta 留给 P8 全局快捷键，提案 B）；③ 无页面内容可聚焦时（如仪表盘只读卡/环形图）请求自然失败，焦点留在侧边栏，不产生报错 |
| **回归** | `ShellFocusFlowUiTest`（5 例）：回车进页面 / Esc 与 ↑ 退回侧边栏 / 侧边栏 ↑↓ 移动 / **弹层打开时退出键不接管**（含 `openModalCount` 打开=1、关闭=0）/ 页面进入不引入隐形焦点停靠点；`KeyboardA11yUiTest` 改为断言**外壳 10 步固定顺序**（侧边栏 7 + 顶栏 3）+ 回车进页面后 Tab 到页面第二个控件 |
| **影响面扫描** | 代码：`ui/shell/MainShell.kt`（焦点编排 + 侧边栏项 `focusRequester`/`onFocusChanged`；helper 拆到新文件以满足 detekt 文件函数上限）、新增 `ui/shell/ShellFocusNavigation.kt`、`ui/components/WzModal.kt`（弹层计数）；文档：`keyboard-walkthrough.md`（键位语义/焦点顺序/走查脚本/判定表/提案状态）、`manual-test-guide.md` TC-MAN-06、`docs/dev/modules/M12.md` §勘误；**不涉数据模型/schema/加密/接口/持久化格式**，不改变页面内容与业务行为（纯焦点编排） |
| **分级（人工拍板 2026-09-15）** | ✅ **C1**：新增焦点行为、不改数据/格式/加密边界、不返工已通过模块的接口（§8.1 红线 1–5 均未命中）。**C1 最小落盘清单已补齐**：决策档 `docs/dev/decisions/D31-键盘焦点流.md`（背景/结论/需求回溯/影响面扫描/验收标准 A1–A6/关联文档）+ `增量台账.md` D31 行与有效需求串 + `决策索引.md` + `task-breakdown **T12.6**` + `ia.md §1.1` + `interaction.md §3-9` + `M12.md §1.7` + STATUS 已决策事项 28 |

### DEF-22 ✅ 已修复（**P1** · 人工门第五轮实测 · 建议 C0）· 设置页弹层落在滚动容器内 → 撑开页面、挤占后续内容

| 项 | 内容 |
|----|------|
| **现象** | 设置 → API 管理 →「添加 API」打开的弹窗**像是插进了页面**（把后面的内容挤下去）；设置 → 行情与同步 → 行情数据源/兜底右侧「已配置/未配置」弹窗**撑开了页面**（人工原话：「好像把窗口内容插入了现在的页面一样」） |
| **根因** | `WzModal` 是**就地叠加层**（`AGENTS.md §7.3`：不用 Popup），靠根 `Box(Modifier.fillMaxSize())` 铺满页面。但设置页是「一个 `verticalScroll` 长列 + 各组组件内联」结构，上述弹层挂在**分组组件自己的 `Box(fillMaxWidth())`** 里 → 该处**高度约束无限**，`fillMaxSize()/fillMaxHeight()` 无法撑开、退化为内容高度 → 弹层变成滚动列里的**普通块**（撑开页面/挤占后续内容）。台账页（交易/资金）弹层挂在页面根 `Box(fillMaxSize())` 下，所以没有该症状 |
| **修复** | 新增**页面级叠加槽** `ui/components/PageOverlay.kt`（`PageOverlayHost` + `PageOverlay`）：页面根用 `PageOverlayHost(Modifier.fillMaxSize())` 包住滚动内容，深层组件用 `PageOverlay { WzModal(...) }` 把弹层提交到**页面根**渲染（有限约束 → 铺满页面、整页居中）；**无宿主时就地渲染**（单测/独立预览兼容）。已接入设置页三处：`ApiManagementSection`、`MarketSettingsSection`、`DataManagementSection`（备份导出 + 恢复向导） |
| **回归** | `ui/settings/PageOverlayUiTest`（2 例）：弹层由页面根承载（卡片**整页**居中）且**不改变**页面元素位置（不再撑开/挤占）；无宿主时退化为就地渲染仍可用；`SettingsPageUiTest::settings modals are hosted by the page root and do not push content`（真实设置页：打开「添加 API」后 `group-api` 位置不变 + 卡片在设置页居中） |
| **影响面扫描** | 代码：新增 `ui/components/PageOverlay.kt`；`ui/settings/SettingsPage.kt`（宿主）、`ui/market/MarketSettingsSection.kt`、`ui/exchange/ApiManagementSection.kt`、`ui/backup/DataManagementSection.kt`（改为提交弹层）。**不涉数据模型/schema/加密/接口**；弹层视觉与交互（遮罩、Esc、首输入框聚焦）不变 |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（实现偏差纠正：`AGENTS.md §7.3` 要求弹层同窗口**覆盖页面**，落在滚动容器内属实现未达约束；不改产品语义）。回写：`design-tokens.md §4.2`（Modal 承载位置）+ `AGENTS.md §7.3-5` + `M10.md`/`M9.md` §勘误；不建档、不进台账（§8.1） |

### DEF-23 ✅ 已修复（**P1** · 人工门第五轮实测 · 建议 C0）· 恢复数据弹窗「浮在备份区域上，感觉有点错位」

| 项 | 内容 |
|----|------|
| **现象** | 设置 → 数据管理 →「恢复数据」弹窗浮在备份区域位置、看起来错位（人工原话） |
| **根因** | 与 DEF-22 **同一根因**（`DataManagementSection` 的两个弹层挂在分区 `Box` 内，处于设置页滚动列中）：卡片只在自己的内容块内居中，纵向位置随该分组在长页中的位置漂移 → 「浮在备份区域」；同时整页遮罩无法覆盖 |
| **修复** | 同 DEF-22（`PageOverlay` 提交到页面根）；备份导出弹窗与恢复向导一并接入 |
| **回归** | 同 DEF-22 三项；`DataManagementSectionUiTest`（7 例）在无宿主场景下继续通过（就地渲染兜底） |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（同 DEF-22）。回写：`M9.md` §勘误 + `design-tokens.md §4.2` |

### DEF-24 ✅ 已修复（**P2** · 人工门第五轮实测 · 建议 C1）· 设置页层级字号/字重不统一

| 项 | 内容 |
|----|------|
| **现象** | ① 分组标题（「通用」「网络」「托盘与后台」…）**比其下二级标签（基础法币/主题/界面语言）还小**；② 手续费卡片标题（全局默认费率/交易所费率）是**加重黑体**，其它块一级标题不加黑；③ 数据管理卡片标题（备份/恢复/明文导出）**明显比其它块标题大** |
| **根因** | 三个组件各自取值：`SettingsPage.SettingsGroup` 用 `caption`（11sp/400 + ink3）、`DataManagementSection.SectionCard` 用 `pageTitle`（20sp/600）、`FeeRuleSettingsSection` 用 `bodyStrong`（14sp/600）→ 同级标题三种字号 |
| **修复** | 定死**设置页层级标准**（写入 `design-tokens.md §3`）：页面标题 20/600 → **分组一级标题 15/600**（新增排版令牌 `WzTypography.sectionTitle`，`colors.ink`）→ **卡内二级标题 14/600**（`bodyStrong`，`colors.ink`）→ 行标签 14/400（`body`）→ 说明 11/400（`caption`，`ink3`）。三处组件全部归位：分组标题用 `sectionTitle`；数据管理卡片标题由 20sp 降为 14/600（与手续费卡片同层）；行情与同步/API 管理的二级标题由「14/400 + ink2」升为「14/600 + ink」 |
| **回归** | `SettingsPageUiTest::all settings first level titles share one typography level`：9 个分组一级标题高度完全一致、5 个卡内二级标题（数据管理 3 + 手续费 2）完全一致，且**二级 < 一级**、一级 > 行标签「基础法币」（= 人工反馈 ① 的反向断言）。标题节点加 `group-title` / `card-title` tag 供跨组件守护 |
| **影响面扫描** | 代码：`ui/theme/Typography.kt`（+`sectionTitle`）、`ui/settings/SettingsPage.kt`、`ui/backup/DataManagementSection.kt`、`ui/ledger/FeeRuleSettingsSection.kt`、`ui/exchange/ApiManagementSection.kt`、`ui/market/MarketSettingsSection.kt`；设计：`design-tokens.md §3`（层级标准行）；**不涉数据/接口/行为语义**（纯视觉层级） |
| **分级（人工拍板 2026-09-15）** | ✅ **C1**：**C1 最小落盘清单已齐全** —— 决策档 `docs/dev/decisions/D32-设置页层级标准.md`（含影响面扫描 + 验收 B1–B5）+ `增量台账.md` D32 行（有效需求串 = `PRD V2.0 + Δ{…, D31, D32}`）+ `决策索引.md` + `task-breakdown **T10.5**` + `design-tokens.md §3` + `M10.md` §5.x；未触 §8.1 红线 1–5 |

### DEF-25 ✅ 已修复（**P1** · 人工门第五轮实测 · 建议 C0）· 添加 API 密钥：首次同步失败 → 弹窗不关，但密钥其实已保存

| 项 | 内容 |
|----|------|
| **现象** | 设置 → API 管理 → 添加 API，填写后点「保存」：**编辑窗口不关闭**，而密钥**实际已保存**（列表里能看到该行）；行内「编辑」→ 保存则会正常关闭（人工原话） |
| **根因** | `ExchangeSyncService.addAndSync` = 校验 → **建行（落库）** → 立即 `syncNow`（网络，可能耗时数十秒或失败）；任何**落库之后**的问题（同步异常/超时/未返回结果）都会以异常上抛 → VM 走 catch 分支：`dialogBusy=false + dialogError=...`、**不关弹窗**、不刷新列表。于是「已保存」被渲染成「保存失败且弹窗不关」；用户重填再点保存只会撞「别名已存在」（`DuplicateApiKeyNameException`），观感即「保存没反应但内容已保存」。对比编辑路径（仅改别名、无网络）所以立刻关闭 |
| **修复** | ① **数据层契约收紧**：`addAndSync` 在密钥已落库后**不再抛异常**，把「已保存 + 首次同步未成功」收敛为 `ApiKeySyncResult(status=FAILED, error=…, message="首次同步未完成，可稍后点「立即同步」重试")`（新增 `savedButSyncFailed`）；② **VM**：新增路径保存成功后**一律关弹窗 + 刷新列表**，失败只作为 toast 提示；未知异常在新增路径上也按「已保存、同步未成功」收尾（避免用户重复提交）；③ **文案（zh/en）**：新增 `savedButSyncFailed(reason)`（明确「密钥已保存」+ 指明可点「立即同步」重试）、`savingBusy`（保存中…）、`savingBusyHint`（首次同步可能数十秒，请勿关闭窗口）；④ 忙碌态显示说明行，避免「点了没反应」的观感 |
| **回归** | `data/DefaultExchangeSyncServiceTest::add and sync keeps the saved key and reports failure when the first sync cannot complete`（不抛异常 + status=FAILED + **密钥确实已落库** + 一笔交易未入账 + 修好后 `syncNow` 可正常补同步）<br>`ApiManagementSectionUiTest::save closes the dialog and reports saved when the first sync fails`（弹窗消失 + 「密钥已保存」提示）<br>`ApiManagementSectionUiTest::save closes the dialog when the service throws after persisting the key`（落库后抛异常的极端路径同样关弹窗 + 刷新列表） |
| **影响面扫描** | 代码：`data/exchange/DefaultExchangeSyncService.kt`（+`savedButSyncFailed`）、`ui/exchange/ApiManagementViewModel.kt`、`ui/exchange/ApiManagementSection.kt`（忙碌文案/提示行）、`ui/i18n/ExchangeStrings.kt`（zh/en 各 +3 词条）、`ui/exchange/ApiCopy.kt`；**接口签名不变**（`addAndSync` 仍返回 `ApiKeySyncResult`），无 schema/加密/格式变更 |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（失败模式补全 + 实现偏差纠正，先例 = DEF-01：导出失败模式类型化；不改产品语义）。回写：`api-contracts.md §3`（`addAndSync` 失败语义）+ `interaction.md §3-12` + `AGENTS.md §7.3-6` + `M6.md` §勘误；不建档、不进台账 |
| **残留体验项（登记 P8 可选增强）** | 首次同步仍**内联执行**（PRD 流程图 3「保存后立即首次同步」；预算 ≤120 次 `myTrades` 调用，大账户可能数十秒），期间弹窗保持打开并显示「保存中…」+「首次同步可能需要数十秒，请勿关闭窗口」。若人工复验后认为等待仍偏长，可选增强 = **保存与首次同步解耦**（落库即关窗 + 后台同步 + 完成 toast）——需在 `ExchangeSyncService` 增加「仅落库不首次同步」的入口，属接口新增（**C1**），登记 P8 |

### DEF-26 ➖ 非缺陷（核实结论 + 回归）· 同步交易数据不会覆盖手写录入的交易

| 项 | 内容 |
|----|------|
| **人工问询** | 「添加只读 key 后可以同步到交易数据，请检查同步交易数据时，是否覆盖了原来手动填写的交易？」 |
| **核实结论** | **不覆盖、不修改、不删除**。同步链路只做一件事：把交易所返回的成交**追加**为新行（`ExchangeTransactionRepository.insertIfAbsent`）。去重键 = `(account_id, exchange, exchange_order_id)` **且 `exchange_order_id IS NOT NULL`**（部分唯一索引 `idx_transactions_dedup`）；手动行 `exchange_order_id = NULL`，既不参与去重也不会被判重丢弃（SQL `= NULL` 不成立）。手写行的 `source='Manual'`、备注、订单号均无任何 UPDATE 路径 |
| **回归（新增）** | `data/DefaultExchangeSyncServiceTest::sync appends exchange trades without touching manually entered rows`：手写一笔与交易所成交**同 pair/同时间/同价量**的交易（最易被误判重复的场景）→ 断言 ① 交易所成交仍作为新行导入（`newTrades=1`，不被手写行吞掉）；② 手写行内容**逐字段不变**（`findById` 前后相等）；③ 库内两行并存（1 Manual + 1 BINANCE 带订单号）；④ 再同步一轮仍为两行且手写行不变（幂等） |
| **口径提示（写入手册）** | 若用户**先手写、后开启只读 Key 同步**，同一笔真实交易会**各存一行 → 重复计入持仓/盈亏**；这是「手动录入 + 交易所同步并存」的固有语义（PRD 未要求自动合并），处理办法 = 删除手写那一行后重新同步，或先同步再补录差异。已记入 `manual-test-guide.md §12` 与 P8 观察项（可选的「手动/同步疑似重复提示」增强） |

### DEF-27 ✅ 已修复（**P1** · 人工门第六轮实测 · 建议 C0）· 切页后焦点落到页面中部字段（设置页一打开就滚到「手续费 → 买入费率」）

| 项 | 内容 |
|----|------|
| **现象** | 从侧边栏进入设置页时，**焦点定位在页面中部的「手续费 → 全局默认费率 → 买入费率」输入框**，长页被连带滚到中部；人工要求「初始打开在页面开始部分」 |
| **根因** | DEF-21 的「进入页面把焦点送进页面内容」由**页面容器上的 `FocusRequester` + Compose 子树遍历**实现，该遍历**不保证按 Tab 序**：实测设置页 Tab 序首项是 `fiat-select`（基础法币），而遍历落到第 22 个停靠点 `fee-global-buy`；`bringIntoView` 随即把滚动容器滚到该字段（= 人工看到的「定位在中间」）。另：行情页自身 `LaunchedEffect` 抢焦点会被主壳兜底请求覆盖，两处互相打架 |
| **修复** | 新增**页面入口焦点契约** `ui/shell/PageEntryFocus.kt`：主壳通过 `LocalPageEntryFocus` 提供 `PageEntryFocusState`；页面在**首个可聚焦控件**上写 `Modifier.pageEntryFocus()` 声明入口焦点；主壳切页时**优先请求声明目标**，未声明（或页面已销毁）时回落容器遍历。已声明：设置（基础法币）、交易（搜索框）、资金（搜索框）、行情（搜索框，替换页面自行抢焦点）、资产（首列排序按钮）、币种详情（返回链接） |
| **回归** | `SettingsPageUiTest::page entry focus lands on the first settings control`（走**真实主壳路径**：Enter 进设置 → 断言 `fiat-select` 聚焦 + 断言 `fee-global-buy` **未**聚焦）；`ShellFocusFlowUiTest`（5 例）继续覆盖未声明页面的回落路径 |
| **影响面扫描** | 代码：新增 `ui/shell/PageEntryFocus.kt`；`ui/shell/MainShell.kt`（提供宿主 + 优先请求声明）、`ui/settings/SettingsPage.kt`、`ui/ledger/TransactionsPage.kt`、`ui/ledger/FundsPage.kt`、`ui/market/MarketWatchPage.kt`、`ui/portfolio/AssetsPage.kt`、`ui/portfolio/CoinDetailPage.kt`；**不涉数据/接口/schema**（纯焦点编排） |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（实现偏差纠正：DEF-21 的落地方式未达「焦点顺序合理」要求；不改产品语义）。回写：`M12.md` §1.7 + `ia.md §1.1` + `interaction.md §3-9`；不建档、不进台账 |

### DEF-28 ✅ 已修复（**P2** · 人工门第六轮实测 · 建议 C0）· 窄窗（1280×800 / 1024×768）表格列被压窄 → 标签竖排、数字换行、行高参差

| 项 | 内容 |
|----|------|
| **现象** | ① 资产列表：币种列出现三枚标签（持仓异常/估算中/成本不可靠）时放不下，**最后一枚标签文字竖排**并把整行撑高；8 位小数的持有数量/当前价格/平均成本互相挤占，排列不齐；② 交易管理：手续费「0.00022336BNB」**断成两行**、「交易对 + 估算中」占两行 → 行高不齐；1024×768 下更严重，更多列换行 |
| **根因** | 两张表都用 `Modifier.weight(...)` 分配列宽：窗口变窄时每列被等比压到**低于内容宽度**，`Text` 默认换行 → 文字折行/逐字竖排，行高随内容变化。标签（`Badge`）同样无单行约束 |
| **修复** | 新增 **`ui/components/AdaptiveTable.kt`**（`AdaptiveTable` + `TableColumn` + `tableCell` + `TableWidths`）：每列声明**最小宽度**+宽窗权重；可用宽度 ≥ 各列最小宽之和 → 按权重铺满（1280×800 及以上与原观感一致）；否则**整表横向滚动**（列取最小宽、底部横向滚动条），行高一致、**不做省略号截断**（财务数据截断比滚动更糟）。两张表接入；所有单元格 `maxLines = 1 / softWrap = false`；`Badge` 一律单行；「估算中」由**另起一行改为与交易对内联** |
| **回归** | `TransactionsPageUiTest`（既有 17 例，其中 2 例改为 `performScrollTo()` 后点击——1024×768 下操作列在横向滚动区右侧）；`PortfolioPagesUiTest` / `AssetsPageUiTest` 全绿；人工按三档分辨率复验（`manual-test-guide.md §13`） |
| **影响面扫描** | 代码：新增 `ui/components/AdaptiveTable.kt`；`ui/portfolio/AssetsPage.kt`、`ui/ledger/TransactionsPage.kt`、`ui/portfolio/PortfolioParts.kt`（Badge 单行）。**不涉数据/接口/schema**；宽窗布局不变 |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（实现偏差纠正：窄窗可读性属 PRD §6「一致性/无障碍」实现要求；人工确认「1024 下表格横向滚动」为修复手段而非交互口径变化）。回写：`design-tokens.md §4.3-1` + `interaction.md §3-13` + `M7.md`/`M12.md` §勘误；不建档、不进台账 |

### DEF-29 ✅ 已修复（**P2** · 人工门第六轮实测 · 建议 C0）· 窄窗过滤按钮与表格操作列被压成竖排

| 项 | 内容 |
|----|------|
| **现象** | ① 1024×768 下交易管理表格右侧「删除」按钮显示不全、按钮变成竖条、「删除」二字竖排；② 资金管理的「近90天」等过滤按钮变成竖条（文字竖排） |
| **根因** | 同 DEF-28 的列宽压缩（操作列被压到 ~100dp → 两个按钮各自被压到文字宽以下）；资金/交易的**过滤按钮行是单行 `Row`**，窄窗下按钮被等比压窄 |
| **修复** | ① 操作列为自适应表的一等列（最小宽 120dp，两枚按钮成对显示）；② 过滤按钮行改 **`FlowRow`**（窄窗自动换行，按钮保持自然宽度）；③ `WzButton` 文案统一 `maxLines = 1 / softWrap = false`（任何按钮都不再竖排） |
| **回归** | 同 DEF-28（两张表的 UI 用例 + 人工三档分辨率复验） |
| **影响面扫描** | 代码：`ui/components/WzButton.kt`（单行文案）、`ui/ledger/FundsPage.kt`、`ui/ledger/TransactionsPage.kt`（过滤行 FlowRow）；**不涉数据/接口** |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（同 DEF-28）。回写：`design-tokens.md §4.3-1`（按钮单行 + 过滤行换行）+ `M7.md` §勘误 |

### DEF-30 ✅ 已修复（**P2** · 人工门第六轮实测 · 建议 C0）· 行情页搜索候选浮层不会自行收起（只有点「添加」才消失）

| 项 | 内容 |
|----|------|
| **现象** | 行情页搜索框输入字母后出现候选浮层；**不点候选行的「添加」就一直留着**，**把输入字母全部删掉也不收起**（人工原话） |
| **根因** | ① `onQueryChange` 里有 `if (searchBusy) return` —— 输入被清空时**没有取消在途搜索**，该请求返回后又把 `candidates` 写回，浮层被顶回来（`showCandidates` 含 `candidates.isNotEmpty()`）；② 没有 Esc/失焦等主动收起路径 |
| **修复** | ① ViewModel 持有 `searchJob`：**每次输入变化/清空/添加成功都取消在途搜索**；结果落地前**复核输入仍是发起时的关键词**（不一致就丢弃）；② 搜索失败不再静默（toast 提示 + 复位忙碌态，且不吞 `CancellationException`）；③ 搜索框加 **Esc = 取消搜索**（清空输入 + 收起候选），`clearSearch` 同时复位忙碌态 |
| **回归** | `MarketWatchPageUiTest::candidate panel closes on cleared input, escape and focus loss`（清空输入 → 浮层消失；重新输入 → Esc → 浮层消失且输入清空）+ 既有 5 例（含「候选可添加」「候选浮层不挤占列表」） |
| **影响面扫描** | 代码：`ui/market/MarketWatchViewModel.kt`（取消 + 结果复核 + 失败提示）、`ui/market/MarketWatchPage.kt`（Esc 取消）；**不涉数据/接口/schema**（搜索服务签名不变） |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（实现偏差纠正：`interaction.md §2.7` 候选浮层的既有语义未正确落地）。回写：`interaction.md §2.7`（候选浮层收起行）+ `M5.md` §勘误；不建档、不进台账 |

### DEF-31 ✅ 已修复（**P2** · 人工门第七轮 · 人工拍板 **C0**）· 高 DPI/宽窗下资产列表币种列第三枚徽标被裁

| 项 | 内容 |
|----|------|
| **现象** | 2560×1600 下资产列表币种列有三枚标签（持仓异常/估算中/成本不可靠）时，**第三枚不能完全显示** |
| **根因** | 表格宽窗模式按**固定 weight** 分配列宽（币种列 1.6f、其余 1.0–1.3f），窄列（数量/价格）与宽列（币种+徽标）抢同一份空间 → 币种列实际宽度可能低于其内容宽（三枚徽标 ≈ 210dp）；2560×1600 在 Windows 200% 缩放下有效宽度 ≈ 1280dp，币种列仅得 ~200dp。**关键**：宽窗模式此前**不保证**每列 ≥ 其最小宽 |
| **修复** | 列宽分配改**按最小宽比例**（`TableColumn.flex` 默认 = `minWidth.value`）→ 可用宽 ≥ Σ最小宽时每列必然 ≥ 自身最小宽；币种列最小宽 240→**260dp**（三枚徽标 + 代码实测可容纳） |
| **回归** | `PortfolioPagesUiTest::assets table keeps badges and numeric cells intact at narrow width`（三枚徽标右边界均 ≤ 表格右边界且宽度 > 20dp） |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（随 DEF-37，实现偏差纠正）。回写：`M12.md` §1.7 + `design-tokens.md §4.3-1` + `interaction.md §3-13`；不建档、不进台账 |

### DEF-32 ✅ 已修复（**P2** · 人工门第七轮 · 建议 C0）· 1024×768 下卡片大数字换行变形

| 项 | 内容 |
|----|------|
| **现象** | 1024×768 下仪表盘「总资产净值」「投入本金」卡片的数据**换行**导致卡片变形 |
| **根因** | 卡片数字用 `display`（32sp 衬线）且无 `maxLines`；窄卡片放不下即折行（两行大数字同时破坏可读性与卡片高度一致性） |
| **修复** | 新增统一卡片组件 `WzCard` + 指标数字 `WzMetric`：**单行 + 按可用宽度自动缩字号**（1.0→0.68），仍放不下才省略号；`StatCard` 内部改用（15 处调用点零改动）；`delta` 行同样单行 |
| **回归** | `PortfolioPagesUiTest`（仪表盘/资产卡片用例全绿）+ 人工按 `manual-test-guide.md §14` 复验 1024×768 |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（实现偏差：窄窗可读性属 PRD §6 一致性要求）。回写：`M12.md` §1.7 + `design-tokens.md §4.3-1`；不建档、不进台账 |

### DEF-33 ✅ 已修复（**P2** · 人工门第七轮 · 人工拍板 **C0**）· 表格滚动条压住操作列、手续费长数字换行

| 项 | 内容 |
|----|------|
| **现象** | ① 交易表横向滚到最右，**删除按钮仍显示不全、文字只有一半**；② 手续费「0.00022336 BNB」等长数据**换行**撑高行 |
| **根因** | ① 纵向滚动条 `VerticalScrollbar` 用 `align(CenterEnd)` 画在**列表容器最右缘**，正好压住最后一列（操作列）；② 手续费/总额/交易所/时间等单元格在改写时**漏加** `maxLines = 1`（只有交易对与表头加了） |
| **修复** | ① 列表区右侧预留 **12dp 滚动条槽**（`padding(end = 12.dp)`，滚动条画在留白内）；② 全部数字/文本单元格改用 `SingleLineText`（单行 + 省略号 + 截断悬停全值）；资金表同口径 |
| **回归** | `TransactionsPageUiTest::transactionTableKeepsFeeSingleLineAndActionsFullyVisible`（手续费单元格高 ≤ 26dp；滚到最右后删除按钮右边界 ≤ 表头右边界、按钮宽 ≥ 36dp） |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**。回写：`M7.md` §5.y + `design-tokens.md §4.3-1` + `interaction.md §3-13`；不建档、不进台账 |

### DEF-34 ✅ 已修复（**P2** · 人工门第七轮 · 人工拍板 **C0**）· 环形图图例币种名换行

| 项 | 内容 |
|----|------|
| **现象** | 1024×768 下环形图卡片的币种说明行里，**币种名称换行**显示 |
| **根因** | 图例名称 `Text` 无单行约束，`weight(1f)` 被右侧金额/占比压窄即折行 |
| **修复** | 图例名称改 `SingleLineText`（单行 + 截断悬停全值） |
| **回归** | `PortfolioPagesUiTest::dashboard renders overview cards donut and popup on slice click`（图例标签节点仍在、点击命中不变） |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（实现偏差）。回写：`M12.md` §1.7 + `design-tokens.md §4.3-1` |

### DEF-35 ✅ 已修复（**P2** · 人工门第七轮 · 人工拍板 **C1（D33）**）· 截断的数据无悬停全值

| 项 | 内容 |
|----|------|
| **现象** | 1024×768 下资产表长字段被截断（省略号），**鼠标悬停也不显示完整数据** |
| **根因** | 截断用 `TextOverflow.Ellipsis`，无任何补充查看途径（财务数据截断后无法核对） |
| **修复** | 新增 `SingleLineText`：用 `onTextLayout { it.didOverflowWidth }` **只在确实被截断时**包一层 `TooltipArea`，悬停显示完整值（统一气泡样式 `HoverTooltip`，tag = `hover-tooltip`）；已用于资产/交易/资金三表与环形图图例 |
| **回归** | 见 DEF-28/31/33 用例（节点可定位、文本完整、`assertIsDisplayed` 不回归）；悬停效果按手册人工复验 |
| **分级（人工拍板 2026-09-15）** | ✅ **C1**：并入决策档 **D33**（响应式与组件统一）+ `task-breakdown **T12.7**` + 台账 D33 行 + 索引；`SingleLineText` 的悬停全值口径写入 `responsive-components.md §2/§3` 与 `design-tokens.md §4.3-1` |

### DEF-36 ✅ 已修复（**P2** · 人工门第七轮 · 人工拍板 **C0**）· 表单弹窗多出一条「不到一行」的滚动条

| 项 | 内容 |
|----|------|
| **现象** | 添加交易 / 记录增资弹窗**总是**带一点侧边滚动条，「能滚动的范围还不到一行」（1024×768 与 2560×1600 均出现） |
| **根因** | 弹窗内容高度上限**写死**（交易 470dp / 增资 420dp / 日志 380dp），与实际窗口无关——内容只要略超上限就出现滚动条，而窗口本身还有富余空间 |
| **修复** | 新增 `modalContentMaxHeight()` = **窗口高 × 0.66（下限 320dp）**（`LocalWindowInfo` 换算），交易/增资表单改用它；同时紧凑化内边距（字段 10→8dp、按钮区 16→12dp） |
| **回归** | `TransactionsPageUiTest::transactionModalFitsWithoutScrolling`（打开弹窗后「保存」与「时间」字段**无需滚动**即处于可视区） |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（实现偏差：弹窗尺寸与窗口无关）。回写：`M7.md` §5.y + `responsive-components.md §4` |

### DEF-37 ✅ 已修复（**P2** · 人工门第七轮 · 人工拍板 **C0**）· 列宽分配不保证「每列 ≥ 最小宽」

| 项 | 内容 |
|----|------|
| **现象** | 宽窗档（≥ 各行最小宽之和）下仍出现列内容被裁/换行（DEF-31 的根因面） |
| **根因** | 宽窗模式用固定 `weight` 分配：权重与「内容最小需求」无关，宽列（币种+徽标）可能拿不到自己需要的宽度 |
| **修复** | `TableColumn.flex` 默认 = `minWidth.value`，宽窗**按最小宽比例**分配 → 每列 ≥ 最小宽；需要特别弹性的列仍可显式覆盖 `flex` |
| **回归** | 同 DEF-31 用例 + 既有三张表用例（宽窗观感不变：列宽比例与原权重接近） |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（实现口径纠正）。回写：`M7.md` §5.y + `design-tokens.md §4.3-1` + `responsive-components.md §2` |

### DEF-38 ✅ 已实现（**P3/体验** · 人工门第七轮 · 人工拍板 **C1（D33）**）· 列表缺少单元线

| 项 | 内容 |
|----|------|
| **人工诉求** | 「各个页面中的列表是否可以加上单元线？」 |
| **实现** | `AdaptiveTable(divider = true)` 默认给每行画 1px `line` 色单元线（`drawBehind`，不额外产生布局节点）；非表格列表（行情自选等）用 `rowDivider()` 逐行插入；三张表 + 行情自选已生效 |
| **口径** | 单元线用**最低强调**的 `line` 色、1px、不加圆角与阴影（保持 design-tokens §4.1「克制使用边框」）；表头与首行之间不加线（沿用表头下边距） |
| **回归** | 视觉项，按 `manual-test-guide.md §14` 人工复验（双主题） |
| **分级（人工拍板 2026-09-15）** | ✅ **C1**：并入决策档 **D33** + `task-breakdown **T12.7**` + 台账 D33 行；单元线口径写入 `responsive-components.md §5` 与 `design-tokens.md §4.3-1` |

### DEF-39 ✅ 已修复（**P2** · 人工门第八轮 · 人工拍板 **C0**）· 仪表盘卡片指标两级口径与原型不符（第一行文字明显偏大）

| 项 | 内容 |
|----|------|
| **现象** | 仪表盘第一行四个卡片的数字**明显大于**下面几行卡片，人工问「为何不一致」 |
| **根因** | `StatCard` 的两级实现**误映射**：`small=false` → `display`（**32sp**）、`small=true` → `bodyStrong`（**14sp**），两级相差 **2.3×**；而 P1 视觉基准（原型 `wuzhufolio-light.html`）为 `.card .big` = **27px** / `.big.sm` = **21px**（≈1.29×）。即：既偏离原型，又让两级差异过大看起来像「同一行不同字号」 |
| **修复** | 排版令牌新增**指标两级** `metricPrimary`（27sp/600 衬线 + tnum）与 `metricSecondary`（21sp/600 + tnum），逐项对齐原型；`StatCard` 改用它；`WzMetric` 的自动缩字号继续在其上生效 |
| **回归** | `PortfolioPagesUiTest::dashboard card metric tiers stay within one scale`（一级 > 次级，且比值 ≤ 1.6——原型 1.29；旧实现 2.3 必红） |
| **影响面扫描** | 代码：`ui/theme/Typography.kt`（+2 令牌）、`ui/portfolio/PortfolioParts.kt`（`StatCard` 映射）。15 处 `StatCard` 调用点零改动；`display` 仍用于品牌字/组件走查页示例（非卡片指标） |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（实现偏差纠正：与原型视觉基准不一致）。回写：`M12.md` §1.7 + `design-tokens.md §3`；不建档、不进台账 |

### DEF-40 ✅ 已修复（**P2** · 人工门第八轮 · 人工拍板 **C0**）· 表格只有横线，需补纵向列线

| 项 | 内容 |
|----|------|
| **人工诉求** | 「表格只增加了横线，再增加竖线」 |
| **实现** | `AdaptiveTable` 新增 `verticalDivider`（默认开）：在**列边界**画 1px `line` 色纵线，贯穿表头与数据行；列边界由纯函数 `columnBoundariesOf(tableWidth, columns, scrollable)` 计算（宽窗按 `flex` 比例、窄窗按最小宽累加），**最后一列右边界不画**（避免与表格右边框重复成双线） |
| **口径** | 网格线一律 `line` 色、1px、不加圆角/阴影；表头行只画纵线（横线由表头下边距承担），数据行横纵都画 → 形成「一列一格的表格网格」而不喧宾夺主 |
| **回归** | `ui/components/AdaptiveTableGridTest`（3 例：窄窗按最小宽累加、宽窗按 flex 比例且每段 ≥ 最小宽、空列退化）；三张表既有用例全绿 |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（D33 已确立统一表格组件应提供网格线，属视觉口径补齐）。回写：`M7.md` §5.z + `M12.md` §1.7 + `design-tokens.md §4.3-1` + `responsive-components.md §2` |

### DEF-41 ✅ 已修复（**P2** · 人工门第八轮 · 人工拍板 **C0**）· 币种详情成交表无单元格线

| 项 | 内容 |
|----|------|
| **现象** | 资产列表 → 点行进币种资产详情页，该页成交表**没有单元格线** |
| **根因** | 该表是**另一套手写实现**（`coin-tx-table`：`Row + PlainHeader + Modifier.weight(...)`），未接入 D33 的统一表格组件，因此既没有横/纵单元线，也没有单行约束、截断悬停全值、窄窗横向滚动等统一行为 |
| **修复** | 迁移到 `AdaptiveTable`（列规格 `COIN_TX_COLUMNS`：交易对/类型/价格/数量/手续费/交易所/时间/已实现盈亏，均含最小宽）→ 自动获得横线 + 纵线 + 单行 + 悬停全值 + 窄窗滚动 |
| **回归** | `PortfolioPagesUiTest::coin detail transaction table uses the shared table component`（表格存在、单元格单行 ≤ 26dp、行高 ≤ 40dp）+ 既有币种详情用例（筛选/校准入口/时间档位）全绿 |
| **影响面扫描** | 代码：`ui/portfolio/CoinDetailPage.kt`（成交表迁移）。**不涉数据/接口**；表头/列序与文案不变（测试与读屏标签不变） |
| **分级（人工拍板 2026-09-15）** | ✅ **C0**（D33 已要求「既有数据表一律用统一组件」）。回写：`M12.md` §1.7 + `responsive-components.md §2`；不建档、不进台账 |

### DEF-42 ✅ 已闭环（**P1** · 人工门第十轮实测 → **第十一轮人工复验通过 2026-09-21** · 人工拍板 **B=C1（D34）** + 上游限制 **登记 P8**）· Windows 安装版双击启动 → 弹窗「Failed to launch JVM」（TC-MAN-02 步骤 3 阻断）

| 项 | 内容 |
|----|------|
| **来源** | P6 人工门第十轮（2026-09-15，Windows 11）TC-MAN-02 开机自启：步骤 1–2（打包版安装、设置页开关可见）正常；**步骤 3「双击桌面 wuzhufolio 启动图标」→ 弹出报错窗口，提示 `Failed to launch JVM`**。人工补充环境信息：机器上已装 **Temurin 21.0.8+9** |
| **现象定位（先澄清一个常见误解）** | 该弹窗由 **jpackage 生成的 Windows 原生启动器**（安装目录下的 `WuZhuFolio.exe`）弹出，**不是 Java 异常、也不是本应用代码抛的错**。它的语义是：**启动器没能把随包捆绑的私有运行时拉起来**（`runtime\bin\server\jvm.dll` 加载/启动失败）。按 ADR-006 §1.1，打包版**自带 jlink 裁剪的私有 JRE，不依赖机器上的 Java** —— 因此**机器上的 Temurin 21 与本缺陷无关**（既不需要它，装它也不会修好；反之机器完全不装 Java 也应能跑） |
| **Agent 侧对照证据（2026-09-16，本机）** | 用**同一套** `app/build.gradle.kts` jpackage 配置产出 app-image 并直接运行：`app/build/compose/binaries/main/app/WuZhuFolio/bin/WuZhuFolio` **启动成功**（进程持续运行 45s 未退出；应用日志 `bootstrap ok \| build=0.1.0+8ccdee7 \| db=… \| schema=12`；并完成一次行情刷新）→ 说明**打包配置（模块集、启动器 cfg、classpath）本身可启动**。查证：`runtime/release` 的 `MODULES` 含 `java.base … jdk.unsupported` 共 20 个模块（`java.sql`/`jdk.crypto.ec`/`jdk.localedata`/`jdk.charsets` 均在）；`app/WuZhuFolio.cfg` **不含 `--add-modules`**（故不存在「模块清单与 jlink 镜像不一致导致 boot layer 初始化失败」这一类原因）；classpath 全部指向存在的 jar。**结论：问题落在 Windows 启动器 × 本机安装环境侧，而非打包配置** |
| **根因已实证（2026-09-16 · CI 三组对照实验，run 35069934049 / 35070557526）** | CI 在**干净 Windows runner** 上对同一份 app-image 跑三组路径对照：① ASCII 路径（`D:\a\wuzhufolio\…`）→ **PASS**（`bootstrap ok \| build=0.1.0+fe6a795 \| schema=12`）；② 路径含 `é`（U+00E9，**在 runner 的 ANSI 代码页 CP1252 可表示**）→ **PASS**；③ 路径含中文（**CP1252 不可表示**）→ **FAIL：进程 2 秒内 `exitCode=2`，完全没有写出应用日志**——与人工门 TC-MAN-02 步骤 3 的弹窗现象同型。**结论：jpackage 的 Windows 原生启动器在「安装路径含系统 ANSI 代码页无法表示的字符」时无法启动随包 JVM**（ASCII 路径与可表示的非 ASCII 字符均正常）。**推论（用以解释本机现象）**：若人工机器的安装路径含中文，则当且仅当其**系统 ANSI 代码页无法表示这些字符**（典型：英文/其他区域设置的系统 + 中文用户名目录，或开启了「Beta: 使用 Unicode UTF-8」的 ACP=65001 配置）时命中本根因；**纯中文 Windows（ACP=936）下中文路径可表示，不命中此机制** —— 故本机根因仍需按下方取证确认（②③④⑤ 亦可能）。**可判定的替代做法**：把安装目录换成纯 ASCII（工作区 `C:\WZF` 拷贝法）后若能启动，即坐实路径类根因 |
| **候选根因（按可能性排序，均给出可判定证据）** | ① **安装路径含非 ASCII 字符**（`perUserInstall = true` → 默认装到 `C:\Users\<用户名>\AppData\Local\WuZhuFolio`；若用户名为中文/其他非 ASCII，启动器的路径编码环节可能加载不到 `jvm.dll`）→ 证据：取证脚本 §1/§3 的 `NON-ASCII` findings；② **安全软件拦截/隔离了 `runtime` 内的 `jvm.dll` 或安装不完整** → 证据：§3 的 `MISS` 行 + §5 `runtime\bin\java.exe -version` 失败；③ **陈旧/并存安装**（第七、八轮曾下载安装过旧 MSI；快捷方式指向已变更或被升级移除的目录）→ 证据：§2 出现**多条** Uninstall 记录、§4 快捷方式 target `exists=False`；④ **安装时应用仍在运行/文件被锁**，部分文件未更新 → 证据：§3 的 `app\*.jar` 计数异常、§6 cfg 引用的 jar 缺失；⑤ 系统开启了「Beta: 使用 Unicode UTF-8」或 ANSI 代码页异常（ACP=65001）→ 证据：§1 的 `System ANSI CP` |
| **现场取证（人工执行一次，约 1 分钟）** | 跑取证脚本 `scripts/diagnose-packaged-launch.ps1`（只读取证 + 末尾可选启动探针），把输出全文与**报错弹窗全文**贴回 Agent；脚本按 ①–⑤ 逐项打判定行与 `[findings]` 汇总。不跑脚本的**手工等价快速版**见本表下方代码块（A–E 五步） |
| **立即绕行（人工可先做完 TC-MAN-02，不必等修复版）** | **W1** 步骤 D（复制整目录到 `C:\WZF` 直接运行 exe）——装好的目录本身就是完整 app-image，拷到 ASCII 短路径即可脱离启动器路径问题；**W2** 用 MSI 重装并在安装向导把目录改为 `C:\WuZhuFolio`（`dirChooser = true` 已开启，可自选）；**W3** 若 B 步 `java -version` 失败：把安装目录加入安全软件白名单后重装。以上任一跑通后，**TC-MAN-02 的自启注册与注销验证即可继续**（自启注册的是 exe 路径，与安装目录位置无关） |
| **根治建议（待人工定级）** | **A. C0（建议）**：CI 增「**打包版启动冒烟**」——三平台打完包后，直接运行产出的 app-image（Windows：`app\WuZhuFolio\WuZhuFolio.exe`；Linux：`bin/wuzhufolio` + xvfb），以独立数据目录断言应用日志出现 `bootstrap ok`，失败即阻断产物上传。**本缺陷正是「产物从未被启动过就被交付到人工门」的直接后果**，冒烟可把这一类问题挡在 CI。<br>**升级打包 JDK 已被实验排除**（2026-09-16 · CI 实验 job `probe-jdk21-nonascii`，run 35071126927）：改用 **Temurin 21** 打包后同样 `ascii=PASS / cjk=FAIL` ⇒ **换新 JDK 修不掉该崩溃**，故修复必须落在「安装路径/产物形态」而非工具链版本。<br>**B. C1（建议，决策档编号顺延 D34）**：① Windows 安装策略调整——`perUserInstall = false`（装到 `C:\Program Files\WuZhuFolio`，规避非 ASCII 用户名的 per-user 路径；代价：安装需管理员确认）**或**保留 per-user、**不需要管理员**地显式指定 ASCII 安装目录——已查证 Compose DSL `AbstractPlatformSettings.installationPath` 可用（映射 jpackage `--install-dir`），例如设为 `C:\WuZhuFolio`；② 增「**便携版 zip**」产物（app-image 压缩包，解压即用，绕开安装器与用户目录路径）。影响面：ADR-006 §1 产物清单（+1）、`manual-test-guide.md` §1 路径 B、`.github/workflows/ci.yml` 上传清单、`M13.md` §勘误。红线检查：不触 §1.1 硬约束、不改数据模型、不改已通过模块行为（属分发口径）→ 建议 **C1**，但仍需人工拍板。<br>**A 项已先行落地（2026-09-15）**：`.github/workflows/ci.yml` package job 末尾新增「打包版启动冒烟（Windows app-image 实跑）」——置于**产物上传之后**、`continue-on-error: true` 的**观察期非阻断**形态（日志判定串 `PACKAGED_LAUNCH_SMOKE=PASS/FAIL`），故不改变任何产物口径、不阻断产物下载；**请人工追认（维持 C0）或否决**，稳定数轮后再按拍板改为阻断式。**B 项（C1）仍待拍板后方可实施** |
| **修复（D34，人工拍板 2026-09-15）** | ① `app/build.gradle.kts` → `windows { perUserInstall = false; dirChooser = false }`：默认安装目录 = `C:\Program Files\WuZhuFolio`（任何区域设置下均为纯 ASCII）；② 三平台新增**便携版产物**（app-image 压缩包，解压即用、不写注册表、数据仍在 `~/.wuzhufolio`）；③ CI 新增「打包版启动冒烟」（ascii/latin1/cjk）与「安装版实跑冒烟」（MSI 静默安装 → 断言落在 `C:\Program Files\WuZhuFolio` → 实跑 → 卸载），探针 `scripts/probe-packaged-launch.ps1`（观察期非阻断，判定串 `PACKAGED_LAUNCH_SMOKE*` / `INSTALLED_LAUNCH_SMOKE`）；④ 现场取证脚本 `scripts/diagnose-packaged-launch.ps1`。**决策档 `D34`** + 台账 D34 行 + 决策索引 + `task-breakdown **T12.8**` + `ADR-006 §1/§1.1/§6` + `M13.md §9` 全部回写 |
| **本机症状的最终归属（2026-09-16 定案）** | 人工机「D34 新版仍弹 `Failed to launch JVM`」的**真实根因是 DEF-43**（开启 Java Access Bridge + 运行时缺 `jdk.accessibility` → AWT 初始化抛 `AWTError`，业务日志前终止）。**即 DEF-42 本身未在该机发生**；DEF-42 描述的是 CI 实证的**上游 jpackage 行为限制**（安装路径含「系统 ANSI 代码页无法表示的字符」时启动器无法加载随包 JVM） |
| **P8 登记（人工裁决 2026-09-16「按建议」）** | **立项输入 = 彻底消除 jpackage 启动器的 ANSI 路径限制**。**当前缓解（D34 已实施）**：`perUserInstall = false`（默认 `C:\Program Files\WuZhuFolio`，任何区域设置下纯 ASCII）+ `dirChooser = false`（用户无法选到非 ASCII 目录）+ 三平台便携版（用户自选路径，README/手册提示用英文路径）。**后续可选方案（P8 评估）**：① 自建启动器/自定义 WiX 引导替代 jpackage 启动器；② 跟踪上游 jpackage 修复后升级打包 JDK；③ 便携包内含「以短路径启动」的批处理兜底。**影响面**：仅影响「用户坚持把程序放在非 ASCII 路径」的场景，其余路径（含所有默认安装路径）不受影响；已在 CI 冒烟留档为已知限制（`PACKAGED_LAUNCH_SMOKE_CJK=FAIL`） |
| **P7 携带（人工裁决 2026-09-16）** | `docs/release/user-guide.md` 增两条：① **安装/解压路径请使用纯英文（ASCII）目录**（默认安装路径已是 `C:\Program Files\WuZhuFolio`，无需干预）；② 便携版用法与「解压到英文目录」提示 |
| **第二轮取证（2026-09-16 · 人工实测，D34 安装后仍复现）** | 人工按 D34 安装新版：**安装成功且目录已是纯 ASCII**（`C:\Program Files\WuZhuFolio`：`WuZhuFolio.exe` + `app\`（含 `WuZhuFolio.cfg`、skiko 原生库、97 个 jar）+ `runtime\bin\server\jvm.dll` 12.6MB + `runtime\lib\modules` 73MB，**文件清单完整**）；**但双击启动仍弹 `Failed to launch JVM`**，且 `%USERPROFILE%\.wuzhufolio\logs` 下 `wuzhufolio.log` 为空。⇒ **在本机上「非 ASCII 安装路径」不是根因**（与 CI 实验推论一致：ACP=936 的中文 Windows 可表示中文路径，该机制不成立）。**新的取证方向**：① 是否仍在启动**旧 per-user 安装**的 exe（快捷方式指向 `%LOCALAPPDATA%\WuZhuFolio`，该目录可能已被升级移除 → 启动器找不到运行时）；② **JVM 初始化失败**（`JAVA_TOOL_OPTIONS`/`_JAVA_OPTIONS`/`JDK_JAVA_OPTIONS` 被设了无效值；安全软件拦截 `jvm.dll`/注入；AppLocker·WDAC·Smart App Control 策略）；③ 日志目录里真正的日志是**轮转后的 `wuzhufolio.<日期>.<序号>.log`**（`wuzhufolio.log` 为空不代表没写日志）。**已追加取证手段**：诊断脚本新增 「env 变量」「用捆绑 `java.exe` 直跑 `com.wuzhufolio.app.MainKt`（绕开启动器，拿真实 Java 错误）」「Windows 事件日志崩溃记录」三节；CI 新增按需 job `console-debug-windows`（`[probe-console]` 触发）产出**控制台版**调试包——控制台启动器会打印 JVM 初始化失败的真实原因。**A2 仍为 ⏳ 待人工** |
| **回归与验收（修复后）** | ✅ **CI 已验收（run [35091303719](https://github.com/mapleafly/wuzhufolio/actions/runs/35091303719)，commit `6e04f08`）**：`msiexec /i exitCode=0` → 安装目录 = `C:\Program Files\WuZhuFolio`；`INSTALLED_LAUNCH_SMOKE=True`（**安装版实跑写出 `bootstrap ok`**）；`PACKAGED_LAUNCH_SMOKE=PASS` / `_LATIN1=PASS` / `_CJK=FAIL`（已知限制留档）；`msiexec /x exitCode=0`；三平台便携包齐备。原始验收项：① CI 冒烟在 Windows/macOS/Linux 三平台绿；② 人工在**非 ASCII 用户名**或**含空格/中文路径**场景安装并能启动（或按 W2 选中英文目录后启动）；③ TC-MAN-02 步骤 3–5 走通（开关 → `reg query` 有项 → 注销重登驻留 → 关闭后注册项消失） |
| ✅ **人工复验（2026-09-21 · 第十一轮，A2 闭环）** | 人工原话「**P6的开机自启、便捷版运行，两项都通过**」。被测形态 = `manual-test-guide.md §18.1` 的 **DEF-43 修复版**（run 35115439554，msi `5d2ec322…`，`build=0.1.0+6f2c77b`）：**① 安装版（per-machine，`C:\Program Files\WuZhuFolio`）双击可正常启动 → A2 ✅、原「Failed to launch JVM」不再出现；② TC-MAN-02 步骤 3–5 全链路走通（开关 → `reg query HKCU\…\Run` 有项 → 注销重登驻留托盘 → 关开关后注册项消失）→ 验收项 ③ ✅；③ D34 便携包解压到纯 ASCII 路径可直接运行（TC-MAN-11 ✅）**。留痕见 `manual-test-guide.md §19`、`test-cases.md §7.0`。**DEF-42 由此闭环**（本机症状归属 DEF-43；上游 ANSI 路径限制仍按 P8 登记跟踪） |
| **影响面扫描** | 无代码/数据/接口影响（打包与分发口径）；文档：`defects.md`（本条）、`manual-test-guide.md §16`（新增排查附录）、`test-cases.md §7.0`/`manual-test-guide.md §1.1-1`（TC-MAN-02 进展改「阻塞」）、`STATUS.md`（P6 阻塞点）。若采纳根治建议 B 则改 ADR-006 §1/§1.1 + `ci.yml` + `M13.md` |
| **需求回溯** | PRD §12（分发与安装，用户侧「安装即用」）、ADR-006 §1.1（捆绑私有 JRE、目标机无需预装 Java）、TC-MAN-02（开机自启需打包版真实注册） |

**DEF-42 手工等价快速版**（不跑脚本也能定性，PowerShell 直接粘贴）：

```powershell
# 安装目录（默认 per-user 安装；向导里改过请替换为实际目录）
$d = "$env:LOCALAPPDATA\WuZhuFolio"; $d
# A. 路径是否含非 ASCII 字符（True = 命中「中文用户名」这一最常见原因）
[bool]($d.ToCharArray() | Where-Object { [int]$_ -gt 127 })
# B. 捆绑运行时是否完好（应打印 17.0.x；报错 = 运行时被安全软件删改/安装不完整）
& "$d\runtime\bin\java.exe" -version
# C. 绕过快捷方式，直接跑主程序
& "$d\WuZhuFolio.exe"
# D. 复制到 ASCII 短路径再跑（能跑通 = 根因 ①：安装路径/启动器路径问题）
Copy-Item $d C:\WZF -Recurse -Force; & C:\WZF\WuZhuFolio.exe
# E. 应用日志：若出现新的 bootstrap ok，说明 JVM 其实起来了，问题不在启动器
Get-ChildItem "$env:USERPROFILE\.wuzhufolio\logs" | Sort-Object LastWriteTime -Descending |
  Select-Object -First 1 | Get-Content -Tail 20
```


### DEF-43 ✅ 已闭环（**P1** · 人工门第十轮续实测 → 人工实机复验通过 · 人工拍板 **C0**）· 开启辅助技术（Java Access Bridge）时打包版启动失败

| 项 | 内容 |
|----|------|
| **来源** | P6 人工门第十轮续（2026-09-16）：D34 新版安装成功（目录 `C:\Program Files\WuZhuFolio`，文件清单完整）后**仍弹 `Failed to launch JVM`**，`wuzhufolio.log` 为空。用 CI 产出的**控制台版调试包**（`console-debug-windows`）运行，拿到启动器背后的真实错误 |
| **真实错误（人工机控制台输出）** | `Exception in thread "main" java.awt.AWTError: Assistive Technology not found: com.sun.java.accessibility.AccessBridge`；栈：`java.awt.Toolkit.loadAssistiveTechnologies` → `javax.swing.UIManager.<clinit>` → `org.jetbrains.skiko.Setup.init` → `MainKt.main(Main.kt:73)`；`Caused by: java.lang.ClassNotFoundException: com.sun.java.accessibility.AccessBridge`；末行 `Failed to launch JVM` |
| **根因** | AWT 首次 `Toolkit.getDefaultToolkit()` 会按系统属性 `javax.accessibility.assistive_technologies` **反射加载辅助技术类**（Windows 上常见于 `%USERPROFILE%\.accessibility.properties`，由 `jabswitch -enable` 或读屏软件写入）。**打包运行时是 jlink 裁剪集，未包含 `jdk.accessibility`** → 反射失败 → AWT 初始化抛 `AWTError`。该异常发生在 `application { }` 内部，**晚于日志文件创建、早于任何业务日志**，故表现为「弹窗 + 空日志」。（人工机开启该属性，与同机 TC-MAN-03 读屏走查一致） |
| **本地复现（Agent 侧，修复前）** | `JAVA_TOOL_OPTIONS="-Djavax.accessibility.assistive_technologies=com.sun.java.accessibility.AccessBridge"` 跑打包 app-image → `RC=1`、输出同型 `AWTError`；`runtime/release` 的 `MODULES` 中 `jdk.accessibility` **缺失**（计数 0） |
| **修复** | ① **正向**：`app/build.gradle.kts` 运行时模块集补 **`jdk.accessibility`**（含 Windows 原生 Access Bridge DLL，随 jlink 入镜像）——读屏属 PRD §6 无障碍基线，**不能靠「让用户关掉辅助技术」绕过**；② **兜底（防御）**：新增 `app/.../AssistiveTech.kt`，在 AWT 初始化**之前**校验属性中的类可加载性（`Class.forName(..., initialize=false)`），不可用则清空属性并 `warn`——宁可「辅助技术降级 + 明确日志」，也不要「应用完全无法启动」；可用时不动属性 |
| **本地验证（修复后）** | 同一 AT 探针 → **`bootstrap ok \| build=0.1.0+6e3dbbe \| schema=12`**，进程持续运行（`RC=124`）；`MODULES` 含 `jdk.accessibility` |
| **回归（自动化）** | ① `app/.../AssistiveTechTest`（4 例）；② 探针脚本新增 `-AssistiveTech` 开关；③ CI `package` job 冒烟新增**「开启辅助技术」变体**：app-image 侧 `PACKAGED_LAUNCH_SMOKE_AT`、**安装版侧 `INSTALLED_LAUNCH_SMOKE_AT`**（人工机真实组合；Windows 原生 DLL 也在此实证） |
| **人工实机复验（2026-09-16）** | ✅ **通过**：人工安装本修复版 MSI（`5d2ec322…`）后**应用可直接打开**（未关闭 Access Bridge）→ 该机症状消除、用例可继续。安装路径 `C:\Program Files\WuZhuFolio`（D34 口径）。|
| **验收** | ✅ **CI 已验收（run [35115439554](https://github.com/mapleafly/wuzhufolio/actions/runs/35115439554)，commit `6f2c77b`，六 job 全绿）**：`LAUNCH_PROBE[at]=PASS` → **`PACKAGED_LAUNCH_SMOKE_AT=PASS`**；`LAUNCH_PROBE[installed-at]=PASS` → **`INSTALLED_LAUNCH_SMOKE_AT=True`**（**安装版 + 开启辅助技术 = 人工机的真实组合**，同时实证 Windows 原生 Access Bridge DLL 随模块入镜像）；同轮 `INSTALLED_LAUNCH_SMOKE=True`、`PACKAGED_LAUNCH_SMOKE=PASS`。⏳ 待人工装新版复验（**无需关闭 Access Bridge**）。CI 两处 `*_AT=PASS`（Windows）；人工装新版后（**无需关闭 Access Bridge**）正常启动；TC-MAN-03 读屏用例增加自动化守护证据 |
| **影响面扫描** | 代码：`app/build.gradle.kts`（模块集 +1）、`AssistiveTech.kt`（新增）、`Main.kt`（启动序列插入校验）、新增测试 1 文件；分发口径：`ADR-006 §1.1`（体积代价 = 模块及其原生库，可忽略）；**不触**数据/schema/加密/接口/UI；`AGENTS.md §1.1` 五条硬约束**不触及** |
| **分级（人工拍板 2026-09-16「按建议」）** | ✅ **C0（实现偏差纠正）**：PRD §6 已要求无障碍基线（读屏可用），打包漏装 `jdk.accessibility` 属实现/打包偏差 → 模块勘误 `M13.md §7` ③ + 技术文档回写 `ADR-006 §1.1`（无障碍模块为必需模块），**不建档、不进台账**；裁决记录见 §0.1 ⑫ |
| **需求回溯** | PRD §6（无障碍基线：读屏 / 全键盘导航）、PRD §12（安装即用）；TC-MAN-03（读屏 NVDA 走查） |


### DEF-16 ➖ 非缺陷（口径确认）· 断网后状态栏不是「立即」变为网络断开

| 项 | 内容 |
|----|------|
| **来源** | P6 人工门 Windows 走查：「启动时连通良好显示『直连 同步：空闲』；拔网后短时间内仍显示连接状态，手动刷新行情后才显示『网络断开』；恢复网络后点刷新即恢复」 |
| **核实结论** | **与设计一致**：状态栏断链指示的输入是**最近一次行情刷新的结果**（`ShellStatusViewModel.marketOffline = market?.error is MarketRefreshError.Network`），即「请求失败即提示、保留上次价格与时间戳、点击可重试」（PRD 故事 3.2-3 / `interaction.md §1.1 N1`）。拔网本身不触发探测，故最长需等到下一轮自动刷新（默认 5 分钟，可设 15/30/60）。手动刷新即刻反映，恢复网络后点刷新即回到「直连」，均符合预期 |
| **可选增强（登记 P8）** | 若希望「拔网即刻提示」，可加**轻量连通性探测**（在调度 tick 上做一次 HEAD/连接探测，或监听 OS 网络事件）——属新增行为（PRD 未要求），登记 P8 评估，不在 P6 实施 |
| **手册更新** | `docs/test/manual-test-guide.md` TC-MAN-05 已写明该预期，避免复验时误判为缺陷 |

### DEF-17 ✅ 已修复（**P0** · 人工门实测暴露 · C0 实现健壮性补全）· Windows 跨零点启动失败（日志轮转与 logback 滚动竞争）

| 项 | 内容 |
|----|------|
| **现象** | Windows 11 上应用**启动即失败**：`bootstrap failed / java.nio.file.NoSuchFileException: …\logs\wuzhufolio.2026-09-14.0.log`，栈顶 `LogRotator.rotate`（`Files.getLastModifiedTime`）→ 应用完全起不来 |
| **触发条件** | 启动时刻跨零点（人工日志时间 2026-09-15 00:04）：logback 做**跨日滚动 + maxHistory 清理**，与启动期 `LogRotator.rotate` 的「列举目录 → 逐条 stat」竞争——条目在列举后已被 logback 删除 |
| **根因** | ① `LogRotator` 对单条目 IO 无容错：`Files.list` 与 `getLastModifiedTime` 之间存在 TOCTOU 窗口；② `AppBootstrap` 把**维护性**的轮转失败当成致命错误（`runCatching` 之外）→ 直接终止启动 |
| **修复** | ① `LogRotator` 抽出单条目入口 `rotateEntry`（internal，可测）：任何单条目 IO 失败/条目消失/非普通文件 → `EntryOutcome.Skipped`，**绝不上抛**；② `AppBootstrap` 启动期与运行期（调度 6 小时轮转）两处 `LogRotator.rotate` 都包 `runCatching` + WARN，失败降级为摘要 `files:0/0/0`，**维护性工作不阻断启动**；③ 运行期 `rotateLogsNow` 同步线程化 logger 参数 |
| **回归** | `data/logging/LogRotatorTest` 新增 2 例：`rotate skips entries that vanished after listing`（直接走 `rotateEntry` 模拟竞争窗口；同时验证正常条目仍被裁剪）+ `rotate tolerates unreadable entries`（目录名以 `.log` 结尾等非普通文件） |
| **影响面扫描** | 代码：`data/logging/LogRotator.kt`（单条目容错 + 新 internal 结果类型）、`app/AppBootstrap.kt`（两处调用点 + logger 参数）；不涉数据模型/接口/备份格式；`LogRotationPolicy`（条数/天数口径）不变 |
| **教训** | 「维护性后台任务」与「启动关键路径」必须分离失败语义：前者的任何失败都只能是日志噪声。凡「列举目录再逐条 stat/删除」的代码都要假设**条目随时会消失**（Windows 上尤其明显，文件被占用/删除的语义与 POSIX 不同） |

## 2. 待人工定级 / 登记（P2，不阻断发布）

### DEF-03 ✅ 已修复（人工裁决 2026-09-14：本轮补做 · **C1** · 决策档 D30）

| 项 | 内容 |
|----|------|
| **来源** | P6 用例覆盖扫描（TC-A3.4-4 / TC-F2.2-2） |
| **现象** | PRD 故事 3.4-4 要求「交易记录列表支持按**交易所、交易类型、时间**进行筛选和搜索」；实现为交易所 + 类型 + 搜索三维，**无时间维度** |
| **处置（C1 mini 闭环）** | 币种详情交易记录区新增**时间档位筛选**，复用资金页既有四档口径 `domain/ledger/FundDateRange`（全部时间 / 近 30 天 / 30–90 天 / 90 天以上）与纯规则 `contains(time, now)`；`CoinDetailViewModel` 增 `dateRange` 状态 + `setDateRange()`；`CoinDetailPage` 筛选区增下拉（testTag `coin-filter-date`）；`PortfolioStrings.dateRangeLabel` zh/en 双档。**不新增查询参数、不改 `TransactionLedgerService`/`TxFilter`、无 schema 变化** |
| **DoD（§8.2 五件套）** | ① 决策档 `D30-币种详情时间筛选.md`；② 台账 D30 行 + 决策索引；③ 下游回写：`ia.md §2.6` / `task-breakdown **T12.5**` / 原型 `wuzhufolio-light.html`（时间档位下拉 + `DEMO_NOW`）+ `prototype-verify.js`（`errors=[]`，`coinTxFilters=4`、`coinDateOptions=4`、`coinDateFilteredEmpty=true`、`coinDateResetEmpty=false`）；④ 验收标准进 T12.5；⑤ `M12.md §9` 模块记录 + STATUS 决策号 |
| **回归** | `PortfolioPagesUiTest::coin detail filters transactions by time range`（近 30 天 → 远期行消失；90 天以上 → 只剩远期行）；**去掉过滤实现该用例必红（已实证）** |

### DEF-04 ⬜ 登记 P8（人工裁决 2026-09-14）· CMC 兜底调用计入 CG 月度额度账本

| 项 | 内容 |
|----|------|
| **来源** | P6 隐私/额度复核（TC-X-2.5-4） |
| **现象** | `DefaultMarketRefreshService` 的 CMC 兜底分支与 CMC 目录同步**无条件** `quota.record(...)`（`:182` / `:284`），而计数口径是按 **CoinGecko 个人 Key 月额（10,000）** 计算的百分比（`QuotaPolicy.percentUsed`）；CG 侧调用则只在 `cgKey != null` 时计数（`:156`/`:238`） |
| **影响** | ① 同时配置 CG+CMC Key 时，CMC 调用会抬高「CG 额度已用 %」，**可能提前触发 80% 降档**（降频属用户可感知行为）；② 未配 CG Key 时 CMC 计数被记录但 `quotaPercentUsed()=null`，不展示也不降档（无副作用，但账本含无消费者数据） |
| **分级建议** | **C1**：为账本增加 provider 维度（CMC 独立计数 / `percentUsed` 只统计 CG 类）属**行为修正**，落在已通过模块 M5 → 按 §8.4 由人工定级 |
| **影响面扫描** | `domain/market/QuotaPolicy.kt`（计数维度）· `data/market/SettingsQuotaLedger.kt`（JSON 载荷兼容：新增键需容错读旧载荷）· `DefaultMarketRefreshService.kt`（4 处 record）· `data-model.md §2.3`（`market.quota` 键口径）· `M5.md` 规格裁决 · `test-cases.md` TC-X-2.5-4 |
| **人工裁决与处置** | **登记 P8**（2026-09-14）：本轮**不动代码**（避免在 P6 引入 M5 语义返工）；P8 立项输入 = 账本增 provider 维度（CMC 独立计数 / `percentUsed` 只统计 CG 类）+ 旧 JSON 载荷容错兼容；届时按 C1 走决策档 + 台账。**当前影响仅「同时配置 CG+CMC Key 时可能提前触发 80% 降档」**，不涉及数据正确性与安全 |

### DEF-05 ✅ 已按 C0 口径澄清（人工裁决 2026-09-14）· interaction §2.1「列表滚动加载」在本地库语境不适用

| 项 | 内容 |
|----|------|
| **来源** | P6 用例覆盖扫描（TC-X-2.1-6 / TC-X-3-2） |
| **现象** | `interaction.md §2.1` 列有「列表滚动加载 → 底部 loading 占位」、§3-2「列表滚动加载（资产、交易、资金、币种详情）」；实现为**本地 SQLite 一次装载 + Compose `LazyColumn` 虚拟化渲染**，无分页与 loading 占位 |
| **影响** | 无功能缺失：数据全在本地，读一次比翻页更快；渲染层已虚拟化（仅组合可视项），不因列表长而卡顿。差异仅在**文档措辞**——若未来数据量级导致装载 I/O 变慢，需要另行评估增量读取 |
| **处置（已执行）** | ✅ **C0 文档澄清已回写**：`interaction.md §2.1` 增「列表装载口径」注（本地 SQLite 单次装载 + Compose `LazyColumn` 虚拟化 → 不适用分页与底部 loading 占位）+ §2.1 表格行与 §3-2 措辞订正；「大数据量（>10 万行）装载耗时」登记 **P8 观察项**。不改代码 |
| **影响面** | `docs/design/interaction.md` §2.1/§3-2 措辞；`test-cases.md` 两条用例转「设计澄清」 |

---

## 2.1 P7 发布后人工验收（2026-09-22 · 0.1.1 修复轮）

### DEF-47 ✅ **已修复（0.1.1 修复轮 2026-09-24）**（**P2** · P7 发布后人工验收暴露 · **人工拍板 C0（实现偏差）** + 方案甲「加构建期开关」）· 组件走查（DEV）页泄漏到正式发布版侧边栏

| 项 | 内容 |
|----|------|
| **现象** | 人工原话：「windows下安装运行问题：1. **组件走查功能还在发布版本里**。估计其他版本的也是这样」（0.1.0 正式 Release 的 Windows 安装版） |
| **根因** | `ui/src/main/kotlin/com/wuzhufolio/ui/shell/MainShell.kt` 侧边栏在 `ShellPage.sidebarPages.forEach{}` **之后无条件渲染** `ShellPage.GALLERY` 项（其上用 `Box(Modifier.weight(1f))` 撑开，把它压到账户区之上）。全仓库 `ui/`、`app/` 主源码中**不存在任何 dev/构建期开关**（grep `isDebug\|BuildConfig\|DEV_UI\|devUi\|systemProperty` **零命中**）→ **与操作系统、构建形态均无关**：Windows 安装版/便携版、Linux deb/rpm/AppImage/便携版**全部可见**（人工「估计其他版本也是这样」的判断成立）。另 `ShellFocusNavigation.kt` 的 `NAV_FOCUS_ORDER = sidebarPages + GALLERY` 使键盘 Tab/↓ 同样可达 |
| **声明与实现不符** | `ShellViewModel.kt` 枚举注释写「M0 组件走查页（T0.6 验收载体；**P4 起仅开发构建可见**）」，但**该门控从未实现**；`docs/design/ia.md` 侧边栏口径为**六页**（不含组件走查） |
| **危害** | **无数据/安全影响** —— 该页只调用 `viewModel.showToast(...)` 等 UI 演示动作，**不读写账户、交易、密钥或任何真实数据**。属**内部开发页面泄漏到正式版**（专业度与信任观感问题）。加重因素：P6 键盘走查把「侧边栏 7 项」当正常状态记录，`ShellUiTest.kt`（2 处 `nav-GALLERY` 点击）与 `KeyboardA11yUiTest.kt`（Tab 序含 `nav-GALLERY`）**主动断言其可达**，把错误行为固化成了回归基线 → CI 全绿也照不出来 |
| **影响面扫描** | **代码**：`MainShell.kt`（侧边栏渲染 + `ShellPage.GALLERY -> ComponentGallery(viewModel)` 页面分支）、`ShellViewModel.kt`（枚举 / `label` / `sidebarPages`）、`ShellFocusNavigation.kt`（焦点序）、`ComponentGallery.kt` 与 `GalleryStrings.kt`（页面与文案，随门控转为开发构建专用）。**测试**：`ShellUiTest.kt`、`KeyboardA11yUiTest.kt`。**文档**：`user-guide.md §5`（本轮曾误写「正式发布版不含任何开发或调试页面」，**已按 0.1.0 事实更正**）、`docs/test/keyboard-walkthrough.md`（7 项记录需注明第 7 项为 DEV、发布构建不可见）、`M0.md`（T0.6 载体定位）、`M12.md`（UI 收尾未清理该入口） |
| **人工拍板** | ① **修复方案 = 甲：加构建期开关**（`generateBuildInfo` 注入 `DEV_UI`，默认 `false`；`-Pwuzhufolio.devUi=true` 才开 —— `MainShell` 侧边栏项与 `NAV_FOCUS_ORDER` 均按它决定是否包含 GALLERY，**开发构建保留走查载体**）；② **修复节奏 = 攒着**：等本轮 Windows/Linux 验收问题齐了一起修，随后出 **0.1.1 补丁版**（按 `rollback.md §1.1` 触发阈值，本项**不回滚 0.1.0** —— 非阻断、非数据/安全问题） |
| **同源排查**（Agent，2026-09-22） | 全量搜索用户可见文案中的 `DEV / 开发 / 演示 / demo / 示例 / sample` → **仅命中 `GalleryStrings.kt`**（组件走查页自身的示例文案）；设置页**无**调试分组；主源码**无**硬编码演示数据；Release 附件**不含** `console-debug` 构建。**结论：同类「内部内容泄漏到发布版」仅此一处** |
| **验收标准（0.1.1 修复轮）** | ① 正式构建（默认参数）侧边栏**无**「组件走查」项、键盘焦点序不含 GALLERY，且 `ShellUiTest` **新增「正式构建断言 `nav-GALLERY` 不存在」**；② `-Pwuzhufolio.devUi=true` 构建下走查页与原断言仍可用；③ 全量用例 + detekt 绿、编译警告 0；④ 打包版人工复核侧边栏为六页；⑤ 修复随 **0.1.1** 发布并在 `CHANGELOG` 记录 |
| **✅ 修复留痕（2026-09-24）** | ① **构建期开关**：`app/build.gradle.kts` 新增 `-Pwuzhufolio.devUi`（默认 false）→ 注入 `BuildInfo.DEV_UI`；② **下发**：app 装配层 `CompositionLocalProvider(LocalDevUi provides BuildInfo.DEV_UI)`（`ui/shell/DevUi.kt` 新增 `LocalDevUi` + `visibleShellPages(devUi)`）；③ **主壳**：侧边栏与焦点序（`navFocusOrder`）按 `devUi` 决定是否含 `GALLERY`，正式构建不渲染该入口；④ **回归**：`ShellUiTest` 新增「正式构建不可见」用例、原两例改为 `LocalDevUi provides true`（**两分支都可测**）；`KeyboardA11yUiTest` Tab 序 7+3 → **6+3** 并显式断言 `nav-GALLERY` 不存在；⑤ 验收标准 ①②③ 已达成（④ 打包版目视由人工复验，⑤ 随 0.1.1 发布记录） |

---

### 2.2 Linux 真机验收回执（2026-09-24 · 人工在 Ubuntu 24.04 实机走查 0.1.0）

> **来源**：人工原话（10 条，编号照录）：「Linux 真机补测：1. 托盘图标显示不全整个图标。2. 托盘菜单中的『立即同步』，
> 好像只是打开窗口的功能，没有执行同步？为神m菜单中没有『刷新行情』菜单项？…3. 桌面窗口重叠后…点击托盘菜单的『打开界面』，
> 是否可以实现将窗口提到桌面最前面？4. 交易管理-添加交易界面，计价币窗口输入 usdt…真正的 usdt 反而显示不出来，
> 而且检索内容只显示六条…5. 当用户手动添加一个交易对时…是否可以自动在行情列表添加这个已经有过交易的币种？…6. 在 ubuntu24.04
> 桌面下，wuzhufolio 在状态栏的图标是一个环形齿轮…7. 检查 java17 和 java21 在本项目中的使用时的差异…8. 资金管理-记录增资、
> 记录辙资、编辑、校准持仓…『币种』输入框…能不能根据币种的总市值或 rank 来排序？…9. 行情-搜索币种框，能够滚动显示多条数据，
> 但是好像也只是按首字母排序？…10. 交易管理-添加交易、修改交易、编辑，窗口中的交易对框，计价币框都按 8 中的设想执行。」
> **分级状态**：以下 DEF-48…DEF-52 为 **Agent 只读定位后的分级建议，等人工拍板**（`AGENTS.md §8.4`：Agent 不自定级、不自行实现）；
> 第 5 条为**增量提案**（非缺陷）；第 7 条为**文档口径**（非缺陷，已按人工指令修订 `dev-setup.md`/`README.md`）。

#### DEF-48 ✅ **已修复（0.1.1 修复轮，人工拍板 C1，D36）**（**P2** · Linux 真机验收）· 托盘图标显示不全

| 项 | 内容 |
|----|------|
| **现象** | 「托盘图标显示不全整个图标」（Ubuntu 24.04 真机，0.1.0） |
| **根因（只读定位）** | `app/src/main/kotlin/com/wuzhufolio/app/tray/TrayIcon.kt` 仍是 **M11 期的「程序化徽章占位」**：在 64×64 位图上**满幅**绘制圆角方块（`drawRoundRect` 覆盖 `0..side`，圆角仅 22%），W 折线（`folioMark`）也贴近边缘，**四角与笔画均无透明安全边距**；`AwtTrayHost.install()` 用 `TrayIcon.setImageAutoSize(true)` 交给面板自行缩放（GNOME 一般 16–22px）。满幅图形被缩到小尺寸、再被面板做圆角/方形裁剪时，四角与笔画边缘被切 → 「显示不全」。**另一层**：P7 只把**打包图标**统一到正式资产（`app/icons/wuzhufolio.{png,ico,icns}`，由 `scripts/generate-icons.mjs` 生成），**托盘仍走程序化绘制**，两条路径未统一（M11 §5 遗留正是「正式图标待 P7」） |
| **建议分级** | **C1**（图标资产统一 + 尺寸/边距策略 → 影响托盘/窗口/打包三处消费口径）；若只做「加安全边距」的最小修复，可按 **C0** 走 |
| **建议修法** | ① 以 `app/icons/wuzhufolio.png`（512×512，已在仓库）为**唯一图标真源**，托盘改为加载该资产并按 16/22/24/32/64 出多尺寸；② 图形保留 ≥12% 透明安全边距（与 macOS 图标网格 10% 口径呼应）；③ 真机复验 Windows + Ubuntu 两平台的面板显示；④ 顺带补主窗口图标（`Window {}` 现未设 `icon`，见 DEF-52 影响面） |
| **✅ 修复留痕（2026-09-24）** | `TrayIcon` 增 `PADDING = 0.08`（透明安全边距）与 `painter(sizePx, padding)`；`AppHost` 不再把 64px 满幅图交给面板缩放。回归 `TrayIconTest`。与打包图标仍同源（同一几何）。 |
| **⚠️ 一轮修复未解决 → 二轮真机取证（2026-09-24 晚）** | 人工复测仍报「托盘图标显示不全」。Agent 在本机做**像素级取证**（`xwininfo` 找 XEmbed 图标窗口 → `xwd` 抓窗口内容 → 逐像素分析 + 放大目视），得到矩阵结论：<br>① AWT `SystemTray.trayIconSize` 报告 **24（逻辑）**，而 XEmbed 图标窗口实际是 **32 物理像素**（GNOME 面板 16 逻辑 × 屏幕缩放 2）；<br>② **AWT 画图时会再乘一次屏幕缩放**：按 24 给图 → 实际按 ~48 物理像素栅格化后 1:1 画进 32 像素窗口 → **右下被裁**；按 32 给图 → 按 ~64 画 → 裁得更狠；**按 16 给图 → 画满 32 且完整**；<br>③ 关键坑：Compose 的 `Painter.toAwtImage(...)` 返回的是**惰性 `PainterImage`**（非实体位图），栅格化尺寸不受我们控制 —— 必须先用 `ImageBitmap` 画好再 `toAwtImage()` 转**实体 BufferedImage**。<br>取证命令与矩阵见 §2.3。 |
| **✅ 二轮修复（2026-09-24）** | ① `TrayIcon.awtImage(px)`：Compose 侧画成确定尺寸位图 → `ImageBitmap.toAwtImage()` → **实体 BufferedImage**；② `preferredTraySizePx()` 改为 **`AWT 报告值 ÷ 屏幕缩放`**（夹取 **16–64**）→ 本机 24/2 = 16（**实拍验证：图标完整落在 32×32 窗口内，四边留边距，W 清晰**）；③ `AwtTrayHost` 的 `autoSize` 改由 **`TrayIcon.policy(osName, hint, scale)`** 决定：**Linux/X11 → 逻辑尺寸 + `autoSize=false`**；**Windows/macOS → 维持 0.1.0 行为（64px + `autoSize=true`，系统按 DPI 缩放）**（原生托盘无 XEmbed 的二次缩放问题，套用 Linux 口径会让高 DPI 图标偏小发糊）；④ 保留两个诊断开关：`-Dwuzhufolio.trayIconPx=<N>`、`-Dwuzhufolio.trayAutoSize=<bool>`；⑤ 回归 `TrayIconTest` 增两例（实体位图尺寸/四角透明、尺寸夹取范围）；⑥ **取证截图**（放大目视）：修复前 `docs/test/evidence/DEF-48-tray-broken-zoom12.png`（只露出图标左上角一块）→ 修复后 `docs/test/evidence/DEF-48-tray-fixed-zoom14.png`（完整绿底 + 白 W + 四边留边距）。 |
| **✅ 人工复验通过（2026-09-25）** | 人工原话「**托盘功能通过，windows走查通过**」——托盘图标在 Ubuntu 24.04 真机**完整显示**（连同托盘菜单四项与执行反馈一并判定通过）；Windows 走查同轮通过（含高 DPI 下的托盘图标与窗口/任务栏图标）。 |

#### DEF-49 ✅ **已修复（0.1.1 修复轮，人工拍板 C1，D36）**（**P2** · Linux 真机验收）· 托盘菜单「立即同步」无可见反馈 + 缺「刷新行情」项

| 项 | 内容 |
|----|------|
| **现象** | 「立即同步，好像只是打开窗口的功能，没有执行同步？为神m菜单中没有『刷新行情』菜单项？」 |
| **代码事实（只读核验——它确实执行了同步）** | `app/src/main/kotlin/com/wuzhufolio/app/AppHost.kt:133`：`onSync = { scope.launch { runCatching { runtime.scheduler.syncNow() } }; trayMenuAt = null }` → **确实调用了 `BackgroundScheduler.syncNow()`**（交易所只读 API 同步），**不是**「只打开窗口」。真正的问题是**反馈不可见**：同步结果只经 `SchedulerEvent` → `AwtTrayHost.notify()` → AWT `TrayIcon.displayMessage`（`AppHost.kt:110-117`）透出，而 **Linux/GNOME 侧 AWT 走 XEmbed，气泡通知不被 SNI 环境显示** → 用户点完菜单「什么都没发生」。另：菜单固定三项（`ui/src/main/kotlin/com/wuzhufolio/ui/tray/TrayMenuContent.kt:78-87`：打开主界面 / 立即同步 / 退出），**没有「刷新行情」**；但 `scheduler.refreshMarketNow(manual)` **已存在**（`BackgroundScheduler` 契约），加该项属纯接线 |
| **真机链路事实（2026-09-24）** | 托盘承载扩展 = `ubuntu-appindicators@ubuntu.com`（包 `gnome-shell-extension-appindicator 58-1ubuntu24.04.1`）→ AWT 的 **XEmbed** 图标由该扩展转发；**AWT `displayMessage` 气泡在 GNOME 下无承载** → 「同步完成/失败」通知天然不可见（与上面的推断一致） |
| **建议分级** | **C1**（新增菜单项 + 新增反馈通道 → 改 `docs/design/design-tokens.md §5` 的托盘菜单清单，属设计基线增量） |
| **建议修法** | ① 菜单改为**四项**：打开主界面 / 立即同步交易 / 立即刷新行情 / ─── / 退出（行情与交易是**两类独立 API**，PRD §1.1-4，故分列两项）；② 补**可见反馈**：执行中 → 托盘 tooltip + 状态栏指示；完成后 → 有窗口则主界面 toast，无窗口则系统通知（不支持通知的桌面降级为「下次打开窗口时 toast + 状态栏摘要」）；文案复用 `DesktopNoticeText`（脱敏口径不变）；③ 菜单项文案与快捷键在 `design-tokens.md §5` + `interaction.md` 补条款 |
| **✅ 修复留痕（2026-09-24）** | 菜单四项（`TrayMenuContent` + `TrayLabels.refreshQuotes` + zh/en 文案「立即刷新行情 / Refresh quotes now」，`TrayMenuWindow` 高度 124→156dp）；`AppHost` 新增 `syncFromTray()/refreshFromTray()`（开始提示 → 结果提示，异常上浮原因）；新增 `NoticeDelivery`（Linux → 应用内提示窗）、`DesktopToastWindow` + `TrayNoticeCard`（置顶/不抢焦点/3.2s 自动消失）。回归：`TrayMenuContentUiTest`（四项 + 四回调）、`NoticeDeliveryTest`、`DesktopNoticeTextTest` +5 例。 |

#### DEF-50 ✅ **已修复（0.1.1 修复轮，人工拍板 C0）**（**P2** · Linux 真机验收）· 托盘「打开主界面」不把窗口提到最前

| 项 | 内容 |
|----|------|
| **现象** | 「桌面窗口重叠后，wuzhufolio 窗口被遮挡到后面看不到时，点击托盘菜单的『打开界面』，是否可以实现将窗口提到桌面最前面？」 |
| **代码事实** | `AppHost.kt:63-66`：`fun showWindow() { windowVisible = true; windowState.isMinimized = false }` —— **只做「显示 + 取消最小化」，没有任何置前请求**（全仓 grep `toFront` **零命中**）→ 窗口 Z 序不变，被别的窗口压住时用户依然看不到。**可行性 = 可以**：Compose Desktop 在 `Window { }` 内容里可拿到 `ComposeWindow`，调用 `window.toFront()` + `window.requestFocus()`；X11 有「防抢焦点」策略，必要时用 `isAlwaysOnTop = true → toFront() → isAlwaysOnTop = false` 的经典绕行；**Wayland/GNOME 下 WM 可能只让任务栏闪烁**，需真机判定并写入已知限制 |
| **真机会话事实（2026-09-24）** | 该机 `XDG_SESSION_TYPE=`**`x11`**、GNOME Shell 46 → **X11 下 `toFront()`/`requestFocus()` 可正常工作**（Wayland 的防抢焦点限制在本机不适用；仍需为 Wayland 用户写降级说明） |
| **建议分级** | **C0**（「打开界面」应有的实现补全）／若人工认为「置前」属新增行为 → **C1** |
| **建议修法** | `showWindow()` 增加置前请求（三平台同路径），Wayland 降级行为写入 `user-guide`/`M11`；人工门在 Ubuntu + Windows 各复验「窗口被遮挡 → 托盘打开 → 是否到最前」 |
| **✅ 修复留痕（2026-09-24）** | `AppHost.showWindow()` 增 `mainWindow?.toFront()` + `requestFocus()`（`LaunchedEffect` 里留住 `ComposeWindow`）；本机 X11 实测可行；Wayland 的 WM 策略差异写入用户指南/M11 已知限制。 |

#### DEF-51 ✅ **已修复（0.1.1 修复轮，人工拍板 C1，D36）**（**P2** · Linux 真机验收）· 币种候选三处不一致：排序未用市值排名、上限 6/12/20 不一、交易表单不可滚动（**合并人工反馈第 4/8/9/10 条**）

| 项 | 内容 |
|----|------|
| **现象（人工原话摘要）** | ④ 交易表单「计价币」输入 usdt → 「检索出来的都是不常用的信息，真正的 usdt 反而显示不出来，而且检索内容只显示六条」（交易对输入框同）；⑧ 资金管理「记录增资/记录撤资/编辑/校准持仓」四窗「币种」框 → 希望**按总市值或 rank 排序**、并且**候选可滚动**；⑨ 行情页搜索框「能滚动，但好像只按首字母排序，很多重要币种反而在后面」 |
| **根因（只读实测）** | ① **排序键缺市值排名**：`data/src/main/kotlin/com/wuzhufolio/data/catalog/SqlCoinCatalog.kt:60-67` 的 `search()` 排序 = 相关性（`symbol == q` / `startsWith` / 其它）→ `ACTIVE` 优先 → **symbol 字母序** → name 字母序；**全程未使用 `rankProvider`**（该 provider 只在同文件 `:205` 的**消歧**路径被消费）。因此**同名 symbol 的一批条目**（symbol 恰为 `USDT` 的 tether 与各链桥接/包装 USDT）只能按**名称字母序**排队 → 真正的 Tether 被字母序靠前的条目挤出前 N → 人工观察到的「都是不常用的、真正的 usdt 不显示」。② **行情搜索同源**：`SettingsMarketWatchService.searchCandidates()` → 同一个 `catalog.search()`，故 ⑨ 的「重要币种在后面」是同一根因。③ **上限与滚动三处不一致**：交易表单 `ui/src/main/kotlin/com/wuzhufolio/ui/ledger/TransactionFormModal.kt:379` `candidates.take(6)` 且外层 `Column` **没有 `verticalScroll`（不可滚动）**；资金/校准 `FundFormModal.kt:231`、`CalibrationModal.kt:62` 用 `FundsCopy.MAX_CANDIDATES = 12` + `heightIn(max=168.dp)` + `verticalScroll`；行情页 `MarketWatchViewModel.CANDIDATE_LIMIT = 20` + 240dp 滚动面板。④ **默认币置顶只有资金路径有**：`DefaultFundService.kt:252-256` 会把 `defaultCoin()`（基础法币孪生，如 USD→USDT）置顶，而交易路径 `DefaultTransactionLedgerService.kt:413-416` 直接透传 `catalog.search`，无置顶 |
| **Agent 真机复核（2026-09-24 · 用应用同一数据源复现排序）** | 拉 CoinGecko 全量目录（**21,549 条**，与应用 `refreshDirectory` 同源）按 `SqlCoinCatalog.search` 的**现状排序键**模拟，结果与人工反馈完全吻合：**`usdt` 命中 131 条、其中 49 条 symbol 恰为 `USDT`** → 现状前 6 = `Abstract Bridged USDT` / `Alcor IBC Bridged USDT` / `Anubis Bridged USDT` …，**真正的 Tether 排第 42 位**（6 条列表里根本看不到）；`btc` 现状**第 1 名是 `Big Tom Coin`**（Bitcoin 第 2）；`eth` 现状 **Ethereum 被挤到第 6**；`sol` 现状前 6 **没有 Solana**。**按建议修法复算**（同层内插入 `rankOf` 升序、未入前 1000 名排后；排名缓存按应用口径实拉 4×250 页 = 前 1000）：**`usdt→Tether`、`btc→Bitcoin`、`eth→Ethereum`、`sol→Solana` 四个查询全部回到第 1 位** → **修法有效性已用真实数据验证**（非纸面推演） |
| **建议分级** | **C1**：改的是 M3/M7/M8/M12 **已通过模块的展示行为**，但**无数据表/接口签名/加密/备份格式变更**（红线 1/2/4 未触），与先例 D31（键盘焦点流）、D33（响应式与组件统一）同类 → C1；**若人工认为「候选排序」属需求口径变更**，则升 **C2**（走 mini 闭环） |
| **建议修法** | ① `SqlCoinCatalog.search()` 排序键插入**市值排名**：相关性 → ACTIVE → **`rankOf(cgId)` 升序（未入前 1000 = 末位）** → symbol → name（排名缓存由 `RefreshableRankProvider` 提供，行情目录刷新时已预热，离线未热时自动退化为现状，**无新网络请求**）；② 抽**统一候选组件**（`CoinSuggestionList`）：同一上限（建议 20）、**一律可滚动**、行内展示 `symbol · name · cgId`（同名资产可分辨，沿用 M8 口径），四处（交易表单 3 个字段 / 资金+校准 / 行情搜索）全部接入；③ 交易路径也接 `defaultCoin` 置顶；④ 回归：同名 symbol 场景断言「tether 在首屏」、滚动可达、三处条数一致；⑤ 下游回写 `ia.md`/`interaction.md`/`data-model`（无）/`api-contracts`（无）与 `M3/M7/M8/M12` 模块记录 |
| **✅ 修复留痕（2026-09-24）** | ① `SqlCoinCatalog.search` 排序键插入 `rankProvider.rankOf(cgId)` 升序（未入榜排后）；② 新增 `PinnedCoinSearch`（资金与交易共用：目录检索 + 默认币命中置顶 + 放大 50 再裁剪）；③ 新增统一组件 `CoinSuggestionList`（上限 **20**、一律可滚动 + 可见滚动条、行 `symbol · name · cgId`），交易三字段/资金/校准全部接入，`FundsCopy.MAX_CANDIDATES`（12）删除；④ 回归 `SqlCoinCatalogTest`（排名打破同名并列 / 未入榜排后）、`PinnedCoinSearchTest`（6 例）、`TransactionsPageUiTest`（第 7 条存在 + 第 20 条滚动可达 + 行含 cg_id）。**修法有效性已用真实数据验证**：`usdt→Tether`、`btc→Bitcoin`、`eth→Ethereum`、`sol→Solana` 全部回到第 1 位。 |

#### DEF-52 ✅ **已修复（0.1.1 修复轮，人工拍板 C1，D36）**（**P2** · Linux 真机验收）· Ubuntu 下状态栏/应用图标显示为通用齿轮（图标未进图标主题 + `Categories=Unknown`）

| 项 | 内容 |
|----|------|
| **现象** | 「在 ubuntu24.04 桌面下，wuzhufolio 在状态栏的图标是一个环形齿轮，大概是默认图标…能不能和托盘图标、桌面图标统一起来？」 |
| **已实证（Agent 解包线上 0.1.0 `.deb` 核对，非推测）** | 包内**没有** `usr/share/icons/hicolor/**`（grep 计数 0）；512×512 正式图标只落在 `/opt/wuzhufolio/lib/WuZhuFolio.png`；`.desktop` = `/opt/wuzhufolio/lib/wuzhufolio-WuZhuFolio.desktop`（`Icon=/opt/wuzhufolio/lib/WuZhuFolio.png` **绝对路径**、**`Categories=Unknown`**、`MimeType=` 空）；`postinst` **仅**执行 `xdg-desktop-menu install <该绝对路径>`，**没有** `gtk-update-icon-cache`、**没有** `update-desktop-database`、**没有**把图标装进主题目录。**注**：构建脚本里写了 `linux { appCategory = "Office" }`，但产物里是 `Unknown` → 需进一步确认是 jpackage 模板行为还是 DSL 未透传（**待修项之一**） |
| **机制说明（为何是「齿轮」）** | 「桌面条目图标」与「托盘图标」是**两条独立链路**：① 桌面/应用列表图标走 `.desktop` + 图标主题（当前靠绝对路径 PNG，且缺缓存刷新 → GNOME 在索引/缓存未更新时显示通用占位，社区已知「装完图标异常、注销重登后正常」）；② 托盘走 AWT `TrayIcon`，**Linux 上仍是 XEmbed**，而 Ubuntu 24.04 的 GNOME 已改用 **StatusNotifierItem/SNI**，需 `xembed-sni-proxy` 之类的代理转发（OpenJDK 已登记该平台差异：JDK-8341144「Java AWT TrayIcon uses the old xembed while most Linux distros switched to SNI」） |
| **Agent 真机复核（2026-09-24 · 本机 = 该 Ubuntu 24.04 桌面，已装 `wuzhufolio 0.1.0-1`）** | ① 会话 = **X11**（GNOME Shell 46）；② **已安装**的 `/usr/share/applications/wuzhufolio-WuZhuFolio.desktop` 全文实测：`Icon=/opt/wuzhufolio/lib/WuZhuFolio.png`（绝对路径）、**`Categories=Unknown`**、**无 `StartupWMClass=`**；③ `ls /usr/share/icons/hicolor/*/apps/ \| grep -i wuzhu` = **空**（图标未进主题；`icon-theme.cache` 存在但无本应用条目）；④ 已启用扩展 = `ubuntu-appindicators@ubuntu.com` + `ubuntu-dock@ubuntu.com`；⑤ **窗口图标从未设置**：jpackage 生成的 `/opt/wuzhufolio/lib/app/WuZhuFolio.cfg` 的 `[JavaOptions]` **无** `-Dsun.awt.application.icon`，且 `AppHost.kt` 的 `Window(...)` **未传 `icon=`** → 窗口没有 `_NET_WM_ICON`。**②+③+⑤ 叠加 ⇒ GNOME 既无窗口图标、也无法经图标主题/`StartupWMClass` 把窗口关联到桌面条目 → 顶部栏 / Dock / 应用网格回落为通用齿轮**（与人工观察一致）。**结论：不是「某个图标没装」，而是「窗口图标 + 主题图标 + 桌面条目关联」三处都缺** |
| **Agent 实跑取证（2026-09-24 · 启动已安装的 0.1.0 做窗口取证，隔离数据目录，跑完即杀）** | 以 `WUZHUFOLIO_DATA_DIR=/tmp/wzf-probe` 启动 `/opt/wuzhufolio/bin/WuZhuFolio` 后 `xprop` 取证：**`WM_CLASS(STRING) = "WuZhuFolio", "WuZhuFolio"`**（→ `StartupWMClass=WuZhuFolio` 即可对齐，**无需**自定义 `-Dsun.awt.X11.XWMClass`）；**`_NET_WM_ICON: not found`** ⇒ **窗口图标确实完全没有** —— 这是 GNOME 顶部栏/Dock 显示通用齿轮的**直接原因**（已从推断升级为实测）。同轮 `busctl --user list`：`org.kde.StatusNotifierWatcher` 由 gnome-shell 持有（SNI 宿主就绪），但**本应用未注册任何 SNI item**（同时段仅 WPS 的 `StatusNotifierItem-3802-1`）→ 托盘走的是 `ubuntu-appindicators` 扩展的 **XEmbed 转发**路径。**数据隔离已核实**：真实 `~/.wuzhufolio/wuzhufolio.db` mtime 未被触碰（探针只写 `/tmp/wzf-probe`）。**注意：本次仅取证，未改任何代码/配置** |
| **建议分级** | **C1**（分发包的桌面集成口径：图标主题安装 + 分类 + 缓存刷新；AppImage 另需集成步骤） |
| **建议修法** | ① **窗口图标**（Dock/顶栏立即见效的那一处）：`AppHost.kt` 的 `Window(...)` 传 `icon = TrayIcon.painter()`（或独立的 `AppIcon.painter()`，与托盘同一枚资产）；② 打包后处理：PNG 安装到 `/usr/share/icons/hicolor/{16,32,48,64,128,256,512}x*/apps/wuzhufolio.png`，`.desktop` 改 `Icon=wuzhufolio` + `Categories=Office;Finance;`；③ **桌面条目关联**：给 `.desktop` 加 `StartupWMClass=`，并与启动参数 `-Dsun.awt.X11.XWMClass=wuzhufolio-WuZhuFolio` **同名固定**（不依赖 AWT 默认值）；④ `postinst` 追加 `update-desktop-database` / `gtk-update-icon-cache -f -t /usr/share/icons/hicolor`（或改用 `xdg-icon-resource install --novendor --size 512`）；⑤ 托盘在 GNOME 下走 SNI 的路径单独评估（扩展已装时 AWT XEmbed 可经 `ubuntu-appindicators` 转发；ADR-001 已把 dorkbox AppIndicator 列为备选；若不做，则用户指南明示「GNOME 需 AppIndicator 扩展」+ 无扩展时的降级行为）；⑥ 真机复验清单：**注销/重登前后 × 顶部栏 / Dock / 应用网格 / 托盘** 四处图标是否一致；⑦ 顺带修 `Categories=Unknown`（确认 `linux { appCategory }` 是否透传；未透传则改用 jpackage 资源覆盖） |
| **✅ 修复留痕（2026-09-24）** | **StartupWMClass 取值订正（2026-09-24 复核）**：首版误取 `WuZhuFolio`（用 `xprop -name WuZhuFolio` 命中了**窗口框架** mutter-x11-frames，不是客户端窗口）；正确取法 = `_NET_CLIENT_LIST` 遍历 + `_NET_WM_PID` 反查， 实测打包版 **WM_CLASS = `com-wuzhufolio-app-MainKt`**（AWT 取主类名、`.`→`-`；app-image 与 .deb 安装版一致）。 尝试用 `-Dsun.awt.X11.XWMClass=WuZhuFolio` 固定 WM_CLASS **实测无效**（JDK 21），故不保留该配置， `.desktop` 的 `StartupWMClass` 直接写实测值。<br>① **窗口图标**：`AppHost` 的 `Window(icon = TrayIcon.painter(WINDOW_SIZE))` → 提供 `_NET_WM_ICON`；② **桌面集成后处理**：新增 `scripts/patch-linux-desktop-integration.sh`（`.desktop` → `Icon=wuzhufolio` + `Categories=Office;Finance;` + `StartupWMClass=WuZhuFolio`；8 档图标装进 `hicolor/*/apps/`；`postinst` 追加 `update-desktop-database`/`gtk-update-icon-cache`；`dpkg-deb --build` 重打包）——**已用线上 0.1.0 `.deb` 本地实测通过**（三字段 + 8 尺寸全部落地）；③ **CI**：Linux package job 打包后执行该脚本并校验三字段 + `hicolor/512x512/apps/wuzhufolio.png`（缺一即失败）。**未纳入**：rpm/AppImage 同类补齐、GNOME 原生 SNI 托盘 → P8。 |

#### 增量提案 ⏳ **待定级（建议 C1 · D35 候选）**· 成交/持仓币自动进入「行情」自选（人工反馈第 5 条）

| 项 | 内容 |
|----|------|
| **人工诉求** | 「手动添加一个交易对时，如果交易的币种没有在行情列表里，是否可以自动在行情列表添加这个已经有过交易的币种？同样，当同步交易所交易数据时…也可以把行情列表中缺少、但有实际交易的币种，自动列入行情列表，以便后续刷新这些币种的行情」 |
| **现状（只读核验）** | `watch.coins`（settings 全局行，上限 `MarketWatchService.WATCH_LIMIT = 50`）目前**只由用户在行情页手动增删**（`SettingsMarketWatchService.addCoin/removeCoin`）；账本写入路径（`DefaultTransactionLedgerService`）与交易所同步路径（`DefaultExchangeSyncService`）**都不碰自选列表**，故成交币不会自动出现在行情页，也不会被自动刷新（只能靠「持仓 ∪ 自选」的聚合页自动补价路径） |
| **建议分级** | **C1**（新增行为、写已有 settings 键、无新表/无加密变更；需新增决策档 D35 + 台账 + `ia.md §2.19`/`interaction.md §2.7` 回写 + task-breakdown 新任务） |
| **需要人工拍板的三个口径** | ① **是否会「复活」用户删掉的币**：建议只对**新出现**的成交币自动加入，并记住「用户已移除」集合（或加设置开关「成交币自动加入行情自选」，默认开）；② **上限**：自选上限 50，超出时不自动加（只在行情页提示）；③ **触发点**：手动交易保存成功后 + 交易所同步新增交易后（`ApiKeySyncResult` 现只带计数、不带币种 → 需在结果里新增**本次新增成交涉及的 cg_id 集合**，属**进程内契约**扩展，`api-contracts.md §2/§3` 回写） |

> **第 7 条（JDK 17/21 与 mise 口径）**：非缺陷 —— 见 `docs/tech/dev-setup.md §1/§2` 与 `STATUS.md` 已决策事项 34 的修订口径
> （**JDK 17 与 21 均可**；发布/CI 基线维持 Temurin 17；**mise 为建议而非硬性规定**）。

---

### 2.3 0.1.1 修复轮结论（2026-09-24 · 人工「按建议定级并实施」）

**范围**：本轮 7 项全部收口 —— DEF-47（C0）、DEF-48（C1）、DEF-49（C1）、DEF-50（C0）、DEF-51（C1）、DEF-52（C1）、DEF-53（C0），
外加人工批准的增量 **D35**（成交币自动进入行情自选）。**无新增缺陷**（修复轮未引入回归）。

**分级与建档**：C1 五项按 `AGENTS.md §8.2` 落最小清单 —— 决策档 **D35** / **D36** + `增量台账` 两行 +
`决策索引` 两行 + `task-breakdown **T12.9**` + 设计/技术文档回写（`design-tokens §5`、`interaction §2.7`、`ia §2.19`、
`data-model §2.3`、`api-contracts §3`、`ADR-006 §1.1.1`）+ 模块记录（`M3/M5/M6/M7/M8/M11/M12/M13`）。
C0 三项按 §8.1 不建档、不进台账（索引见 D36 §5）。

**本机实测证据（Agent，2026-09-24）**：
- **无缓存全量验证（JDK 21，交付前最终一轮）**：`./gradlew clean build --rerun-tasks --no-build-cache` →
  **BUILD SUCCESSFUL（33/33 任务真实执行）** → **748 用例 / 744 执行 / 0 失败 / 0 错误 / 4 跳过**
  （4 条 = env 门控的 live 网络冒烟：`LiveMarketRefreshWiringTest` 1 + `LiveNetworkSmokeTest` 2 + `ProxyRoutingSmokeTest` 1）
  + **detekt 0** + **编译警告 0**（DEF-53 的 2 条冗余 `?.` 清掉后恢复为 0）；分模块：app 50 / domain 222 / data 300 / ui 176；
- **候选排序修法验证**（真实 CoinGecko 全量目录 21,549 条）：修法后 `usdt→Tether`、`btc→Bitcoin`、`eth→Ethereum`、`sol→Solana` 全部第 1 位；
- **Linux 桌面集成补丁实测**：对**线上 0.1.0 `.deb`** 跑 `scripts/patch-linux-desktop-integration.sh` →
  重打包后 `.desktop` 三字段齐（`Icon=wuzhufolio` / `Categories=Office;Finance;` / `StartupWMClass=WuZhuFolio`）、
  `hicolor` 下 **8 档**图标（16/24/32/48/64/128/256/512）、`postinst` 含缓存刷新；包元数据（Package/Version/Installed-Size）不变；
- **CI 接线**：Linux package job 打包后执行该脚本并校验（缺字段/缺图标即 job 失败）；
- **托盘图标像素级取证（DEF-48 二轮，可复现）**：
  ```bash
  # ① 找 XEmbed 图标窗口（AWT 的 canvas peer）
  wid=$(xwininfo -root -tree | awk '/sun-awt-X11-XCanvasPeer/ {print $1; exit}')
  # ② 抓窗口内容并逐像素分析（绿=accent，浅=纸色）
  xwd -id "$wid" -silent -out /tmp/tray.xwd   # 配合 PIL 解析 XWD（>25I 头 + RGBA stride）
  ```
  实测矩阵（Ubuntu 24.04 / GNOME 46 / 缩放 2 / JDK 21；窗口恒为 32×32）：
  | 给 AWT 的图 | autoSize | 窗口内实际绘制 | 结果 |
  |---|---|---|---|
  | 24px（AWT 报告值） | true / false | ≈48px | **右下被裁**（人工所见） |
  | 32px | false | ≈64px | 裁得更狠 |
  | **16px（24 ÷ 缩放 2）** | false | ≈32px | **完整、四边留边距** ✅ |
- **窗口图标端到端实证（GUI 冒烟，隔离数据目录 `/tmp/wzf-smoke`）**：`./gradlew :app:run` 启动后对**真实窗口** `xprop` ——
  **修复前**（0.1.0 安装版）：`_NET_WM_ICON: not found`；**修复后**：`_NET_WM_ICON(CARDINAL) = Icon (192 x 192)` ✅；
  同轮实测 **打包版 WM_CLASS = `WuZhuFolio`**（开发运行 `:app:run` 为 `com-wuzhufolio-app-MainKt`），
  故 `.desktop` 的 `StartupWMClass=WuZhuFolio` 与已安装产物一致；目录刷新 `added=21545`、**排名缓存预热 `entries=492`**
  （DEF-51 新排序在生产路径上的前提成立）；bootstrap `schema=12`、托盘 `supported=true`、无异常栈。

**待人工复验（0.1.1 出包后）**：D36 §6 B1–B8 与 D35 §6 A1–A7 —— 重点是
**B2/B6（托盘与桌面图标在真机完整显示）**、**B3（托盘四项 + 点完可见反馈）**、**B4（遮挡时置前）**、
**B5（四处候选查 usdt 首屏即 Tether、可滚动）**、**B7（成交币自动入自选）**。


### 2.4 0.1.1 人工复验结论（2026-09-25 · 人工门通过）

**人工原话**：「**托盘功能通过，windows走查通过**」。

| 验收面 | 覆盖项 | 结论 |
|--------|--------|------|
| **Linux 真机（Ubuntu 24.04）托盘功能** | DEF-48 托盘图标完整显示（二轮修复后）· DEF-49 菜单四项 + 点选后有可见反馈 · DEF-50「打开主界面」置前 | ✅ 通过 |
| **Windows 走查** | DEF-47 侧边栏无组件走查项 · DEF-49 托盘菜单与气泡反馈 · DEF-51 候选排序（查 usdt 首屏为 Tether、可滚动）· DEF-52① 窗口/任务栏图标（此前为 Java 默认图标） | ✅ 通过 |
| 其余（不依赖平台） | DEF-51 排序与候选统一（Linux 已随托盘复验一并走查）· DEF-53 编译警告 0 · D35 成交币自动入自选 | ✅ 自动化回归覆盖（748 用例 0 失败）+ Linux 走查覆盖 |

**结论**：**0.1.1 修复轮的全部验收项通过**（D36 §6 B1–B8 与 D35 §6 A1–A7 的可人工判定部分均通过），
无新增缺陷、无回退项。**下一步 = 人工批准发布 0.1.1**（版本号 bump → 提交推送 → CI 出包 → 打 tag → 建 Release）。

## 3. P3 / 观察项（登记，不影响发布）

| # | 事项 | 性质 | 处置 |
|---|------|------|------|
| DEF-07 | 导出时目录缺失的币种行被 `mapNotNull` 静默跳过（仅 debug 语义，无计数回传） | 可观测性 | 现状：`coins` 表不删行，理论不触发；登记 P8（若将来支持删币，需在导出摘要里报数） |
| DEF-08 | `backupMetadata()` 读失败被 `runCatching{}.getOrNull()` 吞掉 → UI 显示「从未备份」而非错误 | 可观测性 | 登记 P8（读取失败极罕见；错误方向不危险——不会误报成备份成功） |
| DEF-09 | `BackgroundScheduler.marketLoop` 的 `onTick()` 在 `runCatching` 之外 | 健壮性 | 当前 lambda 不抛（`proxyRuntime.refresh()`）；登记 P8 一并纳入异常隔离 |
| DEF-10 | 英文界面下，服务层硬编码中文异常文案仍可能经 `t.message` 直达 UI（如备份密码强度：UI 侧已本地校验，属第二道防线） | i18n 完整性 | 本轮已收敛导出/恢复两条主要路径（DEF-01/06）；其余路径登记 P8 统一为类型化错误码 |
| DEF-11 | DEF-01 的 `keyName = null` 分支与 `extra` 列坏值无独立用例 | 测试完整性 | 同一代码路径已被 passphrase 用例覆盖；登记 P8 补齐 |
| **DEF-45** | ➖ **非缺陷（口径澄清，2026-09-16）**：人工问「先退出登录再关应用，开机自启是否还有效」——核实为**有效**：自启是设备级偏好（全局行）+ `logout()` 只清会话与「记住我」令牌 + 启动时 `reconcileAutostart()` 自愈；已把判定口径写入 `manual-test-guide.md` TC-MAN-02 卡（避免人工门把「登出后停登录页」误判为自启失效） | 口径 | ✅ 已回写手册 |
| **DEF-44** | ✅ **已修正（2026-09-16，人工门实测，测试文档勘误 · C0）**：手册中的 `msiexec /i .\wzf-windows\msi\WuZhuFolio-0.1.0.msi` **相对路径写法在 PowerShell 下必然失败**（报「无法打开此安装程序包。请确认该程序包存在，并且你有权访问它」），而文件管理器双击可正常安装。**根因**：`msiexec` 是独立进程，相对路径按它自己的工作目录解析；per-machine 安装触发 UAC 提权后工作目录变为 `C:\Windows\System32`，相对路径即失效（双击由资源管理器传**绝对路径**故正常）。**修正**：`manual-test-guide.md §5.3/§15/§18/§18.1` 与 `keyboard-walkthrough.md` 的全部安装命令改为 `$msi = (Resolve-Path .\…).Path` + `Start-Process msiexec -ArgumentList "/i `"$msi`"" -Verb RunAs -Wait`，并在 §5.3 增「为什么必须绝对路径」说明；同处说明「双击时先闪一下的窗口 = UAC 提权/引导，属正常现象」 | 文档可用性 | ✅ 已修正（不涉产品代码） |
| **DEF-46** | ✅ **已修正（2026-09-21，Agent 记录第十一轮复验时发现，测试文档勘误 · C0）**：`manual-test-guide.md` 自 **§16.4 起整段重复**——同一份内容出现两次（§16.4 / §16.5 / §17 TC-MAN-11 / §18 第十轮产物 / §16.6），系 2026-09-16 追加 §18.1 时**在文件尾部整段复粘**所致。**危害**：① 同一用例两份卡片、证据更新容易只改一处（本轮即需同时改两处）；② 两版 §16.6 的探针命令**互相矛盾**——保留版注明「随包运行时不含 `java.exe`（`--strip-native-commands` 裁剪），不能用它做探针」，而复粘版给出 `& "$d\runtime\bin\java.exe" …` 直跑命令；`scripts/diagnose-packaged-launch.ps1` 对该文件是 `Test-Path` 后 `continue`（缺失即跳过），故以「缺 `java.exe`」口径为准。**修正**：删除**复粘的第二份**（115 行），保留首份（含 `§18.1` 第十轮续产物留痕）；文件 1015 → 900 行，标题结构恢复单份。**不涉产品代码、不改任何用例判据**。 | 文档可用性 | ✅ 已修正（保留「§16.6 结论：随包运行时无 `java.exe`」口径；如后续确认产物确实带 `java.exe`，按 C0 再回写该注） |
| **DEF-12** | ✅ **已修复（2026-09-14，CI 三平台复跑暴露，测试缺陷）** `SettingsKeyNamespaceGuardTest` 的符号索引**依赖文件遍历顺序**：早期实现用 `HashMap<裸名, 表达式>`，同名常量（`AppLanguage.SETTINGS_KEY="locale"` 与 `MarketWatchService.SETTINGS_KEY="watch.coins"`）互相覆盖 → **限定名 `MarketWatchService.SETTINGS_KEY` 未解析**，Windows（NTFS 目录顺序）上 `watch.coins` 丢失而 ubuntu/macos 通过。**修复** = 改为「限定名 → 表达式集合」索引（限定名唯一命中即用；裸名要求跨全部限定符唯一，否则 fail-closed）+ 限定符跟踪覆盖 `interface`/`enum class`/`data class` 等全部类型声明。**验证** = 本地把文件遍历顺序反转为降序后复跑仍绿（顺序无关性实证）；CI 复跑见 `test-report.md`/STATUS「CI 留痕」 | 测试基础设施 | 已修复并已推送复跑；教训：凡「跨文件符号解析」的守护测试必须与遍历顺序无关，且**限定名优先于裸名** |
| **DEF-53** | ✅ **已收口（0.1.1 修复轮 2026-09-24 · 人工拍板 C0）**：**① 用例总数口径偏低 4 条**——文档多处写「**718 用例（710 执行 0 失败 + 8 跳过）**」，实测两侧均非此数：**CI（ubuntu / JDK 17）= 722 用例，714 执行 0 失败 + 8 跳过**；**本机 WSL2（JDK 21）= 722 用例，718 执行 0 失败 + 4 跳过**（差异 = `KeychainMasterKeyStoreTest`×2 与 `KeyringRememberMeStoreTest` 2/3 这 4 条钥匙串真实后端用例在 CI-ubuntu 无 Secret Service 而跳过、在本机 WSL2 实际执行并通过）。**② 「编译警告 0」表述与当前代码不符**：本机干净全量构建实测 **2 条 Kotlin 警告**（`app/src/main/kotlin/com/wuzhufolio/app/UiPreferenceState.kt:33:54` 与 `:36:64`「Unnecessary safe call on a non-null receiver」）——根因 = `ThemeMode.fromStorage()/PnlColorScheme.fromStorage()` **声明返回非空类型**（`?: LIGHT` / `?: GREEN_UP`），调用处却写了 `?.let {}`；属**类型层面即可判定，与 JDK 版本无关**（该文件自 P6 DEF-19 修复提交 `79d4069` 起未再改动）。**处置建议**：把用例数与跳过数的**双环境口径**写清（0.1.1 修复轮会再次变动，届时统一回填 `test-report.md`/`test-cases.md`/STATUS），并顺手删掉两处冗余 `?.`（1 行级改动）后重跑全量确认「警告 0」恢复成立；**均不涉产品行为** | 测试文档 / 质量口径 | ✅ 已收口：① 冗余 `?.` 已删（`UiPreferenceState.kt`，全量构建 **0 警告**）；② 用例/警告口径在 0.1.1 修复轮按实测回填（**746 用例 = 742 执行 0 失败 + 4 跳过**，本机 JDK 21） |

---

## 4. 复跑命令（供人工复核缺陷修复）

```bash
export JAVA_HOME=$(mise where java)

# 全量（含本轮全部新增回归）
./gradlew clean build detekt --no-build-cache

# DEF-01/06（备份导出失败模式 + 恢复不清库）
./gradlew :data:test --tests "com.wuzhufolio.data.backup.DefaultBackupServiceTest" \
                     --tests "com.wuzhufolio.data.backup.BackupBoundaryGuardTest"
./gradlew :ui:test   --tests "com.wuzhufolio.ui.backup.*"

# DEF-02（recvWindow 契约）
./gradlew :data:test --tests "com.wuzhufolio.data.exchange.BinanceAdapterTest"

# DEF-22/23/24（设置页弹层宿主 + 层级统一）· DEF-25（保存即关弹窗）
./gradlew :ui:test --tests "com.wuzhufolio.ui.settings.PageOverlayUiTest" \
                   --tests "com.wuzhufolio.ui.settings.SettingsPageUiTest" \
                   --tests "com.wuzhufolio.ui.exchange.ApiManagementSectionUiTest"

# DEF-25/26（同步契约：失败不上抛 / 不覆盖手写交易）
./gradlew :data:test --tests "com.wuzhufolio.data.exchange.DefaultExchangeSyncServiceTest"

# DEF-39…DEF-41（第八轮：卡片指标两级 / 表格纵线 / 币种详情表接入）
./gradlew :ui:test --tests "com.wuzhufolio.ui.components.AdaptiveTableGridTest" \
                   --tests "com.wuzhufolio.ui.portfolio.PortfolioPagesUiTest"

# DEF-31…DEF-38（第七轮：响应式与组件统一）
./gradlew :ui:test --tests "com.wuzhufolio.ui.portfolio.PortfolioPagesUiTest" \
                   --tests "com.wuzhufolio.ui.ledger.TransactionsPageUiTest" \
                   --tests "com.wuzhufolio.ui.ledger.FundsPageUiTest"

# DEF-27…DEF-30（第六轮：入口焦点 / 窄窗表格 / 过滤按钮 / 行情候选浮层）
./gradlew :ui:test --tests "com.wuzhufolio.ui.settings.SettingsPageUiTest" \
                   --tests "com.wuzhufolio.ui.ledger.TransactionsPageUiTest" \
                   --tests "com.wuzhufolio.ui.ledger.FundsPageUiTest" \
                   --tests "com.wuzhufolio.ui.market.MarketWatchPageUiTest" \
                   --tests "com.wuzhufolio.ui.portfolio.*"

# DEF-20（候选选中后焦点交接）· DEF-21（焦点流：进页面 / 回到外壳）
./gradlew :ui:test --tests "com.wuzhufolio.ui.ledger.FundsPageUiTest" \
                   --tests "com.wuzhufolio.ui.ledger.TransactionsPageUiTest" \
                   --tests "com.wuzhufolio.ui.shell.ShellFocusFlowUiTest" \
                   --tests "com.wuzhufolio.ui.KeyboardA11yUiTest"
```

---

## 5. 需求回溯

| 缺陷 | 需求锚点 |
|------|----------|
| **DEF-47** 侧边栏不得出现开发页面 | `docs/design/ia.md`（侧边栏六页口径）、`ShellViewModel.kt` 枚举注释「P4 起仅开发构建可见」、`AGENTS.md §7.3`（GUI 共性约束） |
| DEF-01 / DEF-06 | PRD 全局说明「统一异常处理」（清晰错误提示，不显示泛化的「请求失败」）、故事 5.2、`api-contracts.md §4` |
| DEF-02 | ADR-004 §2、`api-contracts.md §2.1`（认证参数契约） |
| DEF-03 | PRD 故事 3.4 验收 4（交易所/类型/时间筛选与搜索） |
| DEF-04 | PRD 故事 3.2 验收 6、共享规范「额度治理」、ADR-003 §4 |
| DEF-05 | `docs/design/interaction.md` §2.1/§3-2 |
| DEF-07…DEF-11 | P5 交接项、M13 安全清单 §7、P6 勘查观察项 |
| DEF-12 | CI 三平台一致性（`SettingsKeyNamespaceGuardTest` 符号索引）；见 §3 |
| DEF-13 / DEF-14 / DEF-20 | PRD §6「无障碍基线：桌面端支持全键盘导航（Tab 焦点顺序合理、核心操作可达）」；`AGENTS.md §7.3` GUI 共性约束（弹窗打开即聚焦、键盘可用为验收强制项） |
| DEF-15 / DEF-18 / DEF-19 | 设计规范「托盘/通知规范」（`design-tokens.md`）、`interaction.md` 语言切换即时生效条款 |
| DEF-16 | PRD 故事 3.2-3、`interaction.md §1.1 N1`（失败即提示、保留上次价格） |
| DEF-17 | PRD「启动可靠性」（应用可启动为前提）；`AGENTS.md §1.1` 本地数据约束下的日志维护 |
| DEF-21 | PRD §6 无障碍基线；`docs/design/ia.md` 导航条款、`interaction.md` 键盘交互；`keyboard-walkthrough.md §6 提案 A` |
| DEF-22 / DEF-23 | `AGENTS.md §7.3` GUI 共性约束（弹层一律同窗口**就地叠加并覆盖页面**）；`design-tokens.md §4.2` Modal |
| DEF-24 | `design-tokens.md §3` 字体层级（P6 补「设置页层级标准」）；PRD §6 一致性要求 |
| DEF-25 | PRD 流程图 3（保存后立即首次同步）、故事 4.1/4.3、`interaction.md` 异常态；`api-contracts.md §3`（M6 调用面） |
| DEF-26 | PRD 故事 4.2（增量去重「不覆盖」语义）、`data-model.md §2.5`（transactions 去重键与 source）、M7 §5-9 手动/CSV 写入口径 |
| DEF-27 | PRD §6 无障碍基线（焦点顺序合理）；`docs/design/ia.md §1.1` 主壳、`interaction.md §3-9` 键盘导航；`keyboard-walkthrough.md` |
| DEF-28 / DEF-29 | PRD §6（界面一致性）与「核心操作可达」；`design-tokens.md §3`（字号/不换行）、§4.1 布局间距；`interaction.md §3-2`（列表虚拟化） |
| DEF-30 | `interaction.md §2.7`（行情页候选浮层语义）、PRD 故事 3.2（搜索添加自选） |
| DEF-31 / DEF-37 | PRD §6（一致性）、`design-tokens.md §4.3-1`（窄窗适配）、`responsive-components.md §2`（统一表格组件） |
| DEF-32 / DEF-34 | PRD §6（卡片与图表信息可读）、`responsive-components.md §3`（统一卡片组件 + 指标数字自适应） |
| DEF-33 / DEF-36 | `interaction.md §3-13`（窄窗表格口径）、`responsive-components.md §4`（弹窗尺寸策略）；PRD 故事 4.x 表单可用性 |
| DEF-35 | PRD §6（数值可核对：截断必须可查看全值）、`responsive-components.md §2/§3`（`SingleLineText`） |
| DEF-38 | `design-tokens.md §4.1`（边框克制使用）、`responsive-components.md §5`（列表可读性） |
| DEF-39 | 原型 `wuzhufolio-light.html` `.card .big` / `.big.sm`（P1 视觉基准）；`design-tokens.md §3` 指标两级 |
| DEF-40 / DEF-41 | `responsive-components.md §2`（统一表格组件应含网格线）、D33（既有数据表一律接入统一组件） |
| DEF-42 | PRD §12（分发与安装：安装即用、三平台原生包）、ADR-006 §1.1（jpackage 捆绑私有 JRE、目标机无需 Java）、TC-MAN-02（自启需打包版） |
