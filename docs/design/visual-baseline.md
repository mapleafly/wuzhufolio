# 视觉基准（Visual Baseline）

> **生效**：2026-09-29（人工拍板 **D42**，选「甲」）· **取代**：P1 交付的 `prototype/wuzhufolio-light.html`（**已退役**，见 D42）
> **规范源**：[`design-tokens.md`](design-tokens.md)（M3 token 表：配色 role / 字号层级 / 圆角阶梯 / 间距 / 动效）
> **结构升级**：2026-10-16（`T16.3`，0.4.0）—— 基线**按平台分目录** + 视觉回归由「只报告」升级为「**同平台超阈值阻断**」

## 1. 基准由两份东西构成

| 组成 | 内容 | 作用 |
|---|---|---|
| **规范（唯一源）** | [`design-tokens.md`](design-tokens.md) —— 配色（M3 官方算法从品牌种子 `#1F5A48` 生成）、M3 15 档字号层级、圆角 4/8/12/16/28、间距、M3 动效 token | 「应该是什么样」的**判据**；评审/开发/测试都引它 |
| **实况（真实渲染）** | `baseline/<platform>/` —— **按平台**存放的真实渲染截图（**每平台 24 张** = 4 主页面 × 明/暗 × 3 档尺寸） | 「实际长什么样」的**对照**；改版后与新渲染比对即可发现漂移 |

> 之所以不用 HTML 原型作基准：D38 起视觉以 M3 规范为准，手绘原型与实现必然漂移（原型改一次要同步一次，长期腐化）；
> 而「token 表 + 自动渲染截图」**永不与实现脱节**——截图就是从真实组件渲染出来的。

## 2. 基线布局与来源（`T16.3`，2026-10-16）

| 目录 | 内容 | 说明 |
|---|---|---|
| `baseline/win/` | 24 张 **CI Windows 渲染** | 与 `os.name` 含 `windows` 的机器比对 |
| `baseline/linux/` | 24 张 **CI Linux 渲染** | 与含 `linux` 的机器比对 |
| `baseline/mac/` | 24 张 **CI macOS（arm64）渲染** | 与含 `mac`/`darwin` 的机器比对 |
| `baseline/*.png`（根下 6 张） | **D42 历史集**（人工挑选的代表性截图，**实为 Linux 渲染**——与今日 `linux/` 集 5/6 字节相同） | 保留作历史交付物；**比对不再使用**（解析优先走平台目录） |

- **来源与可追溯性**：三平台 24 张均为 **CI 真实渲染**，取自 CI run **`38850945718`**（提交 **`6045ef7`**，
  工件 `visual-regression-{windows,ubuntu,macos}-latest`）——**不是本机伪造**：三套之间的哈希互不相同，
  且与旧 6 张的跨平台差异量级一致（见 §3）。
- **为什么必须按平台**：同一份代码在三个平台渲染出的像素**本就不同**（字体栅格化/次像素定位/AA 实现差异），
  实测跨平台差异 **0.51%–1.83%** 差异像素，是同平台噪声（≤0.0046%）的 **100 倍以上** ⇒ 共享一套基线**必然**全判漂移，
  无法作为判据（这正是 `D47` 当时只能「只报告」的原因）。

## 3. 阈值标定证据（`T16.3` 实测，2026-10-16）

**方法**：24 张 × 4 组（本机 Win / CI Win / CI Linux / CI macOS）逐像素比对；「差异像素」= 最大通道差 > `PIXEL_TOLERANCE(8)`。

| 对比 | 字节完全相同 | >8 差异像素比（中位 / 最大） |
|---|---|---|
| **本机 Win vs CI Win**（同平台） | **18 / 24** | 0.0000% / **0.0046%** |
| CI Win vs CI Linux（跨平台） | 0 / 24 | 0.5119% / **1.5567%** |
| CI Win vs CI macOS（跨平台） | 0 / 24 | 0.5365% / **1.7102%** |
| CI Linux vs CI macOS（跨平台） | 0 / 24 | 0.6116% / **1.8285%** |
| **旧 6 张基线 vs CI Linux** | **5 / 6** | 0.0000% / 0.0693% |
| 旧 6 张基线 vs 本机 Win | 0 / 6 | 0.6930% / 1.1208% |

**据此定阈值**（`VisualDiff`）：

| 分级 | 判据（差异像素占比） | 行为 |
|---|---|---|
| `MATCH` | 最大通道差 ≤ 8 | 放行 |
| `WITHIN_NOISE` | ≤ **0.05%**（`PLATFORM_NOISE_RATIO`） | 放行（同平台噪声，留 10 倍余量） |
| `REVIEW` | ≤ **0.50%**（`BLOCKING_RATIO`） | **仅报告**（写 `diff-<page>.md`，人工看 artifact） |
| `BLOCKING` | **> 0.50%** | **阻断**（测试失败，报文含最差图与刷新步骤） |
| `NO_BASELINE` / `SIZE_MISMATCH` / `UNREADABLE` | — | 如实成状态；**不假装比对过**（不缩放、不猜测） |

**为什么暂不收紧到 0.05% 阻断**（`D47 §2.1` 转阻断前置条件 ③「连续多轮 CI 无平台性抖动」尚未全满足，如实登记）：
目前只有 **Linux** 有跨运行证据（旧基线 vs 今日 CI Linux 5/6 字节相同 ⇒ 6 天内稳定）；Windows/macOS 尚无多轮数据。
**收紧条件**：同一平台**连续两轮** CI 的 `max(diffRatio) ≤ 0.01%` ⇒ 可把 `BLOCKING_RATIO` 降至 `PLATFORM_NOISE_RATIO`。

## 4. 改版后如何刷新基线（有意改界面时）

```bash
# 推荐（来源最干净：三平台都取 CI 真实渲染）
# 1) 让 CI 跑一次（push 或 workflow_dispatch），等测试作业完成
# 2) 下载三平台渲染工件（每平台 24 张）
gh run download <run-id> -n visual-regression-windows-latest -D /tmp/base-win
gh run download <run-id> -n visual-regression-ubuntu-latest  -D /tmp/base-linux
gh run download <run-id> -n visual-regression-macos-latest   -D /tmp/base-mac
# 3) 覆盖对应平台目录（保持文件名 <page>-<light|dark>-<WxH>.png）
cp /tmp/base-win/*.png   docs/design/baseline/win/
cp /tmp/base-linux/*.png docs/design/baseline/linux/
cp /tmp/base-mac/*.png   docs/design/baseline/mac/
# 4) 提交（随当次改版的决策档一起），并在提交信息里写明 run-id 与提交号（可追溯）
```

- **单平台快路径**（只有 Windows 机器时）：跑 `./gradlew :ui:test` 生成本机 24 张 → 覆盖 `baseline/win/`。
  允许这么做，因为同平台噪声实测 ≤0.0046%，远低于阻断线；但**必须在提交信息里注明「本机渲染」**以区别于 CI 来源。
- **不要让三个目录用同一套图**（跨平台差异 0.5%–1.8% 会立刻触发阻断，且掩盖真实问题）。
- **`WUZHUFOLIO_VISUAL_BASELINE_DIR`** 环境变量可覆盖基线目录（用于试验/历史对照）——**覆盖时只报告、不阻断**。

**渲染矩阵**：`1024×720` / `1280×800` / `1600×1000` × 明/暗 —— 与 D33 响应式断点口径一致；
**断言**：每张渲染都断言「页面根 + 关键元素已显示」（崩溃/关键元素缺失即测试红）。
详见 [`docs/test/visual-regression-M14.md`](../test/visual-regression-M14.md)。

## 5. 人工走查清单（自动化覆盖不到的部分）

| 项 | 为什么必须人工 |
|---|---|
| 设置页长页、币种详情页、组件走查页 | 依赖具体数据/开发开关；自动化成本高 |
| 各弹层观感（添加交易 / API / 恢复数据 …） | 交互态已被 UI 测试覆盖，但**观感**需目视 |
| 托盘菜单（Linux 系统渲染 / Win+mac 自绘） | 平台相关，见 D39 与用户指南 §5.8 |
| **IME / 键盘逐字录入** | `AGENTS.md §7.3` 强制人工项（真实输入法行为无法离屏复现） |
| **macOS 全部界面**（`D51` 起 macOS 进发布范围，但**无真机**） | CI 只覆盖离屏渲染与打包；真机观感/托盘需用户或志愿者反馈 |

## 6. 与其他文档的关系

- **冲突时以 [`design-tokens.md`](design-tokens.md) 为准**；截图只反映"当前实况"，规范才是判据；
- 历史评审文档（`P1评审报告*.md`、`direction-approved.md`）保留原样，记录当时的决策，不随本基准更新；
- 需要「可点击的 HTML 演示」时属 P7 宣传材料范畴，与本基准无关（见 D42 §5）。
