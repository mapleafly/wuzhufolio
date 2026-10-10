# D47 · B5 工程项（CI 门禁与规约固化）：视觉回归进 CI + 打包冒烟收敛为门禁 + 三条规约入 `AGENTS.md`（C1）

> **级别**：**C1**（人工 2026-10-10 定级「按建议：B1/B2/B4/B5 = C1，B3 = C2」；B 迭代 **B5**；
> 其中 **③④⑥ 属机制类，按 C0 落 `AGENTS.md`**）
> **来源**：`docs/dev/B迭代-分级建议与影响面扫描.md` §1 **B5**（6 子项）+ `docs/dev/项目全面评审报告.md`
> **F4 / F6** + `docs/dev/retrospective.md` 第二轮 §2 问题 #5 + `docs/test/defects.md` **DEF-58**
> **实施**：2026-10-10 · **模块记录**：`docs/dev/modules/M15.md` · **任务**：`docs/tech/task-breakdown.md` **T15.4**

---

## 1. 背景

B5 是「工程质量补强」类增量（不改产品语义），6 个子项各有独立现状问题：

| 子项 | 现状（改动前） | 依据 |
|------|----------------|------|
| ① 视觉回归进 CI（截图 diff） | `VisualRegression` 已在 `:ui:test` 渲染 **24 张**（4 页面 × 2 主题 × 3 尺寸）到 `ui/build/visual-regression/`，但 **CI 不归档**这些产物，也**没有基线比对**（漂移只能靠人工看图） | 全面评审 **F4**；`retrospective §5-C` |
| ② 打包产物冒烟收敛为门禁 | `ci.yml` package job 两个 Windows 步骤**均为 `continue-on-error: true`**（观察期非阻断），近几轮实测均 PASS | `defects.md` DEF-42/D34；`B迭代 §1 B5②` |
| ③ 埋点卫生 | 排障期加的 DEV 埋点（环境变量钩子、逐事件日志、指针轨迹探针）**需要人工要求才回退** | `retrospective` 二轮 §2 问题 **#5** |
| ④ F1 状态对齐 checklist | 全面评审实测 **12+ 处状态位滞后**（README / STATUS / 台账 / 索引 / task-breakdown / 模块记录 / defects §0 / 反馈头部 / 计数口径 / 文档互引）——同一件事多处留痕，单点更新必漏 | 全面评审 **F1** |
| ⑤ 测试素材写回门控 | `VisualRegression.captureFilmAsset` 把 2× 整窗渲染**直接写仓库** `docs/release/promo/assets/ui/`：**每次全量测试**重写 4 张 PNG，且**跨平台字节不同** ⇒ 工作树污染 | 全面评审 **F6**（复跑实证） |
| ⑥ 时间敏感断言规范 | `PortfolioPagesUiTest` 硬编码 `NOW = 2026-09-10T07:24:00Z`，而产品按**真实时钟**筛选 ⇒ 自 **2026-10-10T07:24Z** 起**永久失败** | `defects.md` **DEF-58** |

**不做什么**（延续复盘「明确不做」）：不引入第三方 UI/图像库、不改产品语义与既有服务/接口签名、
不把报告型机制偷偷改成阻断式、不为「门禁好看」删用例。

## 2. 拍板结论与实施口径

### 2.1 ① 视觉回归进 CI + 基线比对（**报告型，不阻断**）

**跑在哪**：24 张渲染**由既有 `./gradlew test` 真实产生**（视觉回归用例本身即断言「页面根 + 关键元素已显示」，
失败即测试红）⇒ CI **不重复跑一遍**测试，只新增两步：**摘要打印** + **artifact 归档 `ui/build/visual-regression/`**。

**比对能力（零新增依赖）**：新增 `ui/src/test/.../regression/VisualDiff.kt`，只用 **JDK 自带** `javax.imageio.ImageIO`
+ `java.awt.image.BufferedImage`（**不引入任何新依赖**）：

| 项 | 口径 |
|----|------|
| 基线来源 | 默认 `docs/design/baseline/`（**D42 视觉基准**，仓库内 6 张真实渲染截图）；`WUZHUFOLIO_VISUAL_BASELINE_DIR` 可覆盖 |
| 匹配规则 | **文件名精确匹配**（`<page>-<light\|dark>-<WxH>.png`）；不缩放、不模糊匹配 |
| 状态 | `MATCH` / `DIFF` / `NO_BASELINE`（基线缺失，**如实计入**）/ `SIZE_MISMATCH`（尺寸不符，不猜测）/ `UNREADABLE` |
| 指标 | 逐像素**最大通道差**、平均通道差、差异像素数（>8）与占比、显著差异像素数（>32） |
| 量级判据 | 差异像素占比 ≥ **5%** ⇒ 「结构级」（疑似真实布局/配色漂移，需人工看 artifact）；否则「AA 级」（文字抗锯齿/次像素） |
| 产物 | `ui/build/visual-regression/diff-<page>.md`（覆盖式）+ 测试日志；CI 打印并归档 |
| **阻断性** | **不阻断**：`VisualDiff` 从不抛异常、不改测试结论 |

**实测标定（本机 Windows，2026-10-10，关键发现）**：24 张渲染中 **6 张**有同名基线，**6/6 判 `DIFF`**——
差异像素占比 **0.34%–1.15%**、平均通道差 **0.22–0.41**、最大通道差 129–255（集中在文字边缘）、全部落在 **AA 级**。
即：**基线由另一平台（Linux/WSL2）渲染，跨平台文字光栅化差异必然超过 8 的逐像素容差**。

**⇒ 转「阻断」的前置条件（三条全满足前不得改判，写在此备查）**：
1. **基线覆盖全部 24 张**（现仅 6 张 ⇒ 18 张只能记 `NO_BASELINE`）；
2. **按平台分别存基线**（如 `baseline/windows-x64/`、`baseline/linux-x64/`）或先在**同平台**重生成基线
   ——否则跨平台基线必然 `DIFF`，阻断会把「平台差异」误判成「视觉回归」；
3. **容差经同平台实测标定**（同平台基线期望 `MATCH`；再定 `PIXEL_TOLERANCE` 与占比阈值），并连续多轮 CI 无抖动。

### 2.2 ② 打包产物自动化冒烟：**app-image 常规路径转阻断，MSI 维持观察期**

| 步骤 | 结论 | 理由 / 回滚 |
|------|------|-------------|
| **打包版启动冒烟**（Windows app-image） | **常规 ASCII 路径 = 阻断式**（**移除 `continue-on-error: true`**） | 该路径是「产物能否启动」的**唯一直接证据**，且近几轮 CI 实测稳定 PASS（例 run `38028372159`）；失败即 job 失败，**回滚 = 加回一行 `continue-on-error: true`** |
| 同上 · **非 ASCII / latin1 变体** | **保持信息性**（只打 `PACKAGED_LAUNCH_SMOKE_<LABEL>=PASS/FAIL`） | 已知**上游限制**：jpackage 启动器在「路径字符无法用系统 ANSI 代码页表示」时崩溃（DEF-42 / D34），不是产品缺陷 ⇒ 不得因此阻断 |
| 同上 · **辅助技术变体（DEF-43 回归）** | **保持信息性告警** | 同上：属平台/运行时属性组合，仅作回归观察 |
| **安装版实跑冒烟**（MSI 静默安装 → 跑 → 卸载） | **维持观察期非阻断**（保留 `continue-on-error: true`） | ① 依赖两个**外部进程**（`msiexec /i`、`/x`）与**机器级共享状态**（固定路径 `C:\Program Files\WuZhuFolio` + 注册表卸载项），前次残留/占用会给出与产品无关的失败；② 「可启动性」已由上面的**阻断式** app-image 冒烟覆盖，本步骤是**安装布局**的补充证据；③ 观察样本仍不足。**转阻断前置**：连续 ≥3 轮 PASS 且非 SKIP + 步骤内补「卸载后目录消失 / `/x` 退出码 0」幂等断言 + 对 `msiexec` 非 0 退出码区分「环境占用」与「产物缺陷」；**回滚 = 加回一行 `continue-on-error`** |

**日志口径不变**（人工门与既有留痕依赖这些串）：`PACKAGED_LAUNCH_SMOKE[_LATIN1|_CJK|_AT]=PASS/FAIL`、
`INSTALLED_LAUNCH_SMOKE[_AT]=…`、`VISUAL_REGRESSION_PNGS=<n>`。

### 2.3 ③ 埋点卫生规范（`AGENTS.md §7.3-9`，C0 机制类）

新增第 9 条：临时埋点**加入即标注** `TEMP-PROBE(清理条件：…)` 或做成**可一键移除的开关**；
**收尾轮逐条清理并在模块记录留痕**（「已清理 N 处 / 剩余 0 处」）；确需长期保留的诊断日志按**正式可观测性**
对待（脱敏/级别/口径）并登记，不得留在 TEMP 状态。

> **本轮清点（B5 执行时）**：全仓 `app/ui/data/domain` 的 `src/main` 已 grep 无 `TEMP` / `WUZHUFOLIO_DEBUG_*` /
> `DEV-ONLY` / 指针探针残留（DEF-57 排查期埋点此前已随 D39 收尾移除）⇒ **剩余 0 处**；本条为**防复发**条款。

### 2.4 ④ 收尾轮状态对齐 checklist（`AGENTS.md §3.1`，C0 机制类）

新增 `§3.1`，把 F1 暴露的落点固化为**9 项**逐项打勾清单：增量台账 ⏳ → 决策索引状态列 → 模块记录四处一致 →
task-breakdown ✅ → `README` 当前阶段与**质量口径（用例总数/分层数/版本号）** → `defects.md §0` 汇总句 →
反馈/复盘文件头部 → **全量用例计数口径（以 `build/test-results` 聚合为准，逐处回填）** → **文档内相互引用（节号/行号）**。
注明：**收尾轮执行、逐项打勾**，勾选结果写进模块记录 §验收清单（缺项即未完成）。

> **本轮即用即验**：按该清单回扫时发现 `README.md` 质量口径仍停在全面评审时的 **788 用例**（B1 完成后已变），
> 已随本轮回填为最新实测值——正是该 checklist 要防的滞后。

### 2.5 ⑤ 测试素材写回门控（F6，本项必做且必须可本地验证）

**口径**：`VisualRegression.captureFilmAsset` 默认**不写仓库**，改写 `ui/build/promo-assets/`（仍渲染、仍断言，
并打印一行提示）；**仅显式开启**才写回 `docs/release/promo/assets/ui/`：

| 开启方式 | 写法 |
|----------|------|
| 环境变量 | `WUZHUFOLIO_FILM_ASSETS=1`（`1/true/yes/on` 均可） |
| Gradle 属性 | `-Pwuzhufolio.filmAssets=true`（`ui/build.gradle.kts` 转成同名系统属性；**两者都支持**） |

**P7 出片链路同步（否则素材不会更新）**：
- `docs/release/promo/tools/render-0.2.0.sh` 新增**步骤 0**：`export WUZHUFOLIO_FILM_ASSETS=1` +
  `./gradlew :ui:test --tests "*film asset*"` 采集素材，再进 `build-promo.mjs`（原步骤号顺延 1→2→3→4→5）；
- `docs/release/promo/README.md` 新增「**素材写回门控**」节（默认/开启落点表 + 两种开启命令 + 出片链路已自动开启），
  并把「复现管线」步骤 0 从旧的 `node tools/capture-ui.mjs`（原型时代工具）改为上面的 Gradle 采集命令；
- 顺手订正同文件「设计约束」里已过时的一句（原写「UI 一律取自 P1 原型」，**D42 后原型已退役**，0.2.0 起为真实应用渲染）。

**本地双向验证（原始输出见 `M15.md §3` 与本文 §6）**：默认跑 `:ui:test` 后 `docs/release/promo/assets/ui/` **零改动**；
开启后 4 张 PNG **确实被写入**（`git status` 可见 4 个 `M`）；随后 `git checkout -- docs/release/promo/assets/ui` 回滚。

### 2.6 ⑥ 时间敏感断言规范（`AGENTS.md §7.3-8`，C0 机制类）

新增第 8 条：被测逻辑按**真实时钟**筛选/比较时，**禁止**把绝对时间基准与真实时钟混用；
统一做法 = ① 以 `Instant.now()` 构造**相对**时间；或 ② 向被测逻辑**注入 `Clock`**；
纯格式化/展示断言可用固定时间但需点明。并**纳入 §3.1 收尾 checklist 与代码评审项**（防 DEF-58 复发）。

## 3. 需求回溯

- `docs/dev/B迭代-分级建议与影响面扫描.md` §1 **B5**（6 子项）+ §2「B5 影响面扫描」+ §3 执行顺序第 4 条（收尾轮跑 checklist）；
- `docs/dev/项目全面评审报告.md` **F1 / F4 / F6** + §7 开放项清单第 10/14 条；
- `docs/dev/retrospective.md` 二轮 §2 问题 #5（埋点卫生）、§5-C（工程质量候选）；
- `docs/test/defects.md` **DEF-58**（时间敏感断言）+ **DEF-42 / D34**（非 ASCII 路径上游限制）；
- `AGENTS.md §7.3`（共性约束落点）、**§3**（看板协议 → 新增 §3.1）、**§8**（C1 五件套）；
- 硬约束 `§1.1-1/2/3`：视觉回归与素材均为**本地文件**、**零网络**、日志脱敏（无新增出站与遥测）。

## 4. 逐子项影响面扫描（`§8.5`）

### ① 视觉回归进 CI + 基线比对
| 面 | 位置 | 处置 |
|----|------|------|
| CI | `.github/workflows/ci.yml`（build job） | ✅ 新增「视觉回归摘要」（`VISUAL_REGRESSION_PNGS=<n>` + 打印 `diff-*.md`）与「上传视觉回归产物与基线比对报告」两步；**不新增测试执行** |
| 代码（test） | `ui/src/test/.../regression/VisualDiff.kt`（**新增**）· `VisualRegression.kt`（`capture` 末尾调 `report`） | ✅ 已加/已改 |
| 代码（test 守护） | `ui/src/test/.../regression/VisualDiffTest.kt`（**新增 5 例**，合成图，跨平台确定性） | ✅ 已加 |
| 基线 | `docs/design/baseline/*.png`（6 张，**不改内容**）· `docs/design/visual-baseline.md`（引用既有 §2 更新步骤） | ➖ 无需改动（本轮只读消费） |
| 依赖 | — | ➖ **零新增依赖**（JDK `ImageIO`） |

### ② 打包冒烟门禁
| 面 | 位置 | 处置 |
|----|------|------|
| CI | `.github/workflows/ci.yml`（package job） | ✅ app-image 冒烟**移除 `continue-on-error`**（转阻断）+ 步骤名/注释改写；MSI 步骤**保留**并写明理由与转阻断前置；**日志串不变** |
| 文档 | `docs/release/release-plan.md §4.2`（CI 出包链路说明）· `docs/test/defects.md`（DEF-42 口径不改，仅引用） | ✅ 已补 §4.2 |

### ③④⑥ 规约（C0 机制类）
| 面 | 位置 | 处置 |
|----|------|------|
| 规约 | `AGENTS.md` §7.3（+8 时间敏感断言 / +9 埋点卫生）· §3.1（**新增** 收尾 checklist） | ✅ 已加 |
| 引用 | `docs/dev/B迭代-分级建议与影响面扫描.md` §3 第 4 条（收尾轮跑 checklist）· `M15.md` §3 | ✅ 已回写 |

### ⑤ 素材写回门控
| 面 | 位置 | 处置 |
|----|------|------|
| 代码（test） | `VisualRegression.kt`（`filmAssetsEnabled` / `filmAssetRepoDir` / `filmAssetBuildDir` / `filmAssetDir`） | ✅ 已改（默认 `build/promo-assets/`） |
| 构建 | `ui/build.gradle.kts`（`-Pwuzhufolio.filmAssets` → 同名系统属性） | ✅ 已加 |
| 出片链路 | `docs/release/promo/tools/render-0.2.0.sh`（步骤 0 自动开启）· `docs/release/promo/README.md`（门控节 + 步骤 0 + 过时句订正） | ✅ 已改 |
| 素材 | `docs/release/promo/assets/ui/*.png` | ➖ **内容零改动**（本轮验证后已 `git checkout` 回滚；发布素材 ↔ 影片对应关系不变） |

## 5. 红线核验（`§8.1`）

- **不触硬约束**：无网络出站（视觉回归/素材/门禁全在本地文件与 CI 内）、零遥测、不涉密钥与加密、不改备份格式；
- **不改数据模型/表结构/加密边界**：零 `data`/`domain` 生产代码改动；
- **不改已通过模块的行为**：改动集中在 **测试代码（test source set）**、`ui/build.gradle.kts` 测试任务配置与 CI 配置；
  产品运行期行为**零变化**（`VisualRegression`/`VisualDiff` 仅存在于测试源集，不进产物）；
- **无跨端影响**（移动端 PRD/SRD 不涉）；
- **不删除/反转已定稿需求**：门控是「默认不写仓库、显式开启才写」的**收紧**，且出片链路已同步，未丢能力；
- ⇒ **C1 成立**（其中 ③④⑥ 为规约固化，按 **C0** 落 `AGENTS.md`，不另建档）。

## 6. 验收结果（2026-10-10）

### 6.1 全量质量门

- `.\gradlew.bat clean build detekt --no-build-cache --console=plain` → **BUILD SUCCESSFUL** + **detekt 0**；
- 用例总数 **810 → 815**（**+5**：`ui` 215 → **220**，即 `VisualDiffTest` 5 例；app 57 · data 311 · domain 227 不变）；
  0 失败 / 0 错误 / 4 跳过（4 条 = env 门控 live 网络冒烟）。

### 6.2 ① 视觉回归 + 基线比对（实跑）

- 渲染产物：`ui/build/visual-regression/` **24 张 PNG**（4 页面 × 2 主题 × 3 尺寸）+ **4 份** `diff-<page>.md`；
- 比对结果：**有基线 6/24**、**DIFF 6/6**、`NO_BASELINE` 18（如实计入），全部 **AA 级**：

| 图 | 状态 | 量级 | 最大通道差 | 平均通道差 | 差异像素(>8) | 显著差异(>32) |
|----|------|------|-----------|-----------|--------------|----------------|
| `dashboard-light-1280x800` | DIFF | AA 级 | 222 | 0.383 | 11,812（1.15%） | 4,157 |
| `dashboard-dark-1600x1000` | DIFF | AA 级 | 255 | 0.227 | 10,737（0.67%） | 3,078 |
| `assets-light-1024x720` | DIFF | AA 级 | 182 | 0.414 | 8,087（1.10%） | 3,376 |
| `assets-dark-1280x800` | DIFF | AA 级 | 255 | 0.308 | 7,636（0.75%） | 2,945 |
| `market-dark-1280x800` | DIFF | AA 级 | 255 | 0.332 | 3,470（0.34%） | 1,936 |
| `transactions-light-1280x800` | DIFF | AA 级 | 129 | 0.225 | 4,559（0.45%） | 2,213 |

- **结论**：差异**集中在文字边缘**、占比均 < 1.2% ⇒ 判定为**跨平台文字光栅化差异**，非布局/配色漂移；
  这正是「本轮只能报告型」的实测依据（§2.1 转阻断前置条件即由此得出）。
- `VisualDiffTest` **5 例 0 失败**：相同图 `MATCH`/差异为零、单像素漂移计数精确（最大通道差 40 / 1 像素 / 6.25% ⇒ 结构级）、
  容差内抖动（通道差 8）不计差异、**尺寸不符**与**缺基线**显式成状态（不缩放不猜测）、报告覆盖式写入且缺基线如实计入。

### 6.3 ⑤ 素材写回门控（双向实跑，原始命令与结论）

| 步骤 | 命令 | 结果 |
|------|------|------|
| 默认（门控关，**全量构建内**） | `.\gradlew.bat clean build detekt --no-build-cache --console=plain` | `git status --porcelain -- docs/release/promo/assets` → **空**（零改动）；素材落在 `ui/build/promo-assets/`（4 张：`dashboard-light` / `portfolio-light` / `quotes-light` / `transactions-light`）✓ |
| 开启（env） | `$env:WUZHUFOLIO_FILM_ASSETS=1; .\gradlew.bat :ui:test --tests "*film asset*"` | `git status --porcelain -- docs/release/promo/assets` 出现 **4 个 `M`**（4 张 PNG 被写入）✓ |
| 开启（Gradle 属性） | `.\gradlew.bat :ui:test --tests "*film asset*" "-Pwuzhufolio.filmAssets=true"` | 同样 **4 个 `M`**（属性通道生效）✓ |
| 回滚 | `git checkout -- docs/release/promo/assets/ui` | `git status --porcelain -- docs/release/promo/assets` → **空** ✓ |

> **实测备注（非代码问题）**：`-P` 首次尝试直接写 `-Pwuzhufolio.filmAssets=true` 时 **PowerShell 把它拆成两个参数**
> （报 `Task '.filmAssets=true' not found`）——与 CI 注释里既有实证同源；**加引号后正常**。该注意事项已写入
> `docs/release/promo/README.md`（Windows 用户）与 CI 既有注释口径。

### 6.4 CI 配置自查

- `git diff .github/workflows/ci.yml` 逐行复核：YAML 缩进/键名正确（新增两步为 `if: always()` + `shell: bash` +
  `actions/upload-artifact@v6`，与既有步骤同构）；步骤顺序 = `Test` → **视觉回归摘要** → **上传视觉回归产物** →
  上传测试报告 → `detekt` → …；package job 中 app-image 冒烟**已无** `continue-on-error`，MSI 冒烟**保留**；
- **本地不跑 CI**（按纪律），门禁生效性由下一步推送后 CI 观察（人工门 / Lead 复核后统一提交）。

## 7. 关联文档更新

`docs/dev/增量台账.md`（D47 行 + 表头有效需求串）· `docs/dev/decisions/决策索引.md`（D47）·
`docs/tech/task-breakdown.md`（M15 概览 + **T15.4**）· `docs/dev/modules/M15.md`（B5 行 ✅ + 改动文件 + 验收清单）·
`docs/dev/STATUS.md`（B 迭代条目追加 B5 ✅）· `docs/dev/B迭代-分级建议与影响面扫描.md`（B5 行标注 ✅ 并逐子项注明结果）·
`AGENTS.md`（§3.1 新增 + §7.3-8/-9）· `README.md`（质量口径回填）·
`docs/release/release-plan.md §4.2`（CI 门禁与视觉回归归档口径）· `docs/release/promo/README.md`（素材写回门控 + 步骤 0 + 过时句订正）·
`docs/release/promo/tools/render-0.2.0.sh`（步骤 0 开启开关）。

## 8. 遗留与未完成（如实登记）

1. **① 的「转阻断」未做**（本轮**按人工口径为报告型**，非未完成）：前置条件见 §2.1 三条；当前 **18/24 无基线**、
   且基线为**跨平台**渲染 ⇒ 现阶段阻断会把平台差异误判为回归。**下一步路径**：按平台重生成 24 张基线 →
   同平台标定容差 → 连续多轮 CI 观察 → 由人工拍板转阻断。
2. **② 的 MSI 冒烟仍为观察期**（人工授权由 Agent 判断；理由与前置条件见 §2.2）——**不是遗漏**，是显式选择。
3. **CI 门禁的线上生效性未验证**（本地不跑 CI）：app-image 冒烟转阻断后需**下一次 CI 实跑**确认；
   若出现与产品无关的 flake，按 §2.2 一行回滚。
4. **① 的 CI 归档在 `build` job（三平台各一份）**：artifact 名 `visual-regression-<os>`；跨平台产物**不互相比对**
   （各平台与自己同名基线比），如需平台矩阵对比属后续增强。
