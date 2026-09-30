# D42 · 视觉基准改为「M3 token 表 + 应用截图」，P1 原型退役（**C1**）

> **决策号**：D42 · **日期**：2026-09-29 · **级别**：**C1**（人工拍板，2026-09-29；属 D38 §6 遗留项的收口）
> **来源**：人工在 T14.5 收尾时于 D38 §6 给出的三个选项中**选「甲」**
> **关联**：`D38 §6`（M3 规范取代 P1 暖纸基准）· `docs/test/visual-regression-M14.md §4` · `task-breakdown M14/T14.5`

---

## 1. 背景

D38 起界面以 **Material 3 视觉规范**为准（配色由品牌种子色经官方算法生成、M3 15 档字号、M3 圆角阶梯、M3 动效 token），
因此 **P1 交付的 `docs/design/prototype/wuzhufolio-light.html`（暖纸手绘基准）与实现不再一致**，
继续把它当视觉基准会导致「评审/开发照着过期基准走」。

D38 §6 给出三选项，人工拍板选**甲**。

## 2. 拍板结论

1. **P1 原型 HTML 退役**：保留文件作为历史交付物，但**在文件头与页面顶部标注「已退役」**，不再作为视觉基准；
2. **新的视觉基准 = 两份可执行来源**：
   - **规范**：`docs/design/design-tokens.md`（M3 token 表：配色 role / 字号层级 / 圆角 / 间距 / 动效；**唯一规范源**）；
   - **实况**：`docs/design/baseline/*.png`（应用**真实渲染**的代表性截图，由 T14.5 回归产出并入库）+ `docs/design/visual-baseline.md`（说明与再生成方式）；
3. **截图再生成**：`./gradlew :ui:test --tests "*visual regression*"` → 产物 `ui/build/visual-regression/`（24 张：4 页 × 2 主题 × 3 尺寸），
   需要更新基准时人工挑选代表性几张覆盖 `docs/design/baseline/`。

## 3. 影响面扫描（§8.5）

| 类别 | 位置 | 处置 |
|---|---|---|
| 设计产物（退役） | `docs/design/prototype/wuzhufolio-light.html`（+ 同名 png） | 加**退役横幅**（文件头注释 + 页面固定角标），**不删除**（历史留痕） |
| 设计规范 | `docs/design/design-tokens.md` 头注（原写「本文档是 prototype 的视觉基准」） | 改为「本文档 = 视觉基准规范源；实况基准见 `baseline/`」 |
| 新增（基准说明） | `docs/design/visual-baseline.md` | 新增：基准构成、再生成命令、人工走查清单 |
| 新增（基准实况） | `docs/design/baseline/*.png`（6 张代表性截图） | 入库（约 300KB） |
| 流程文件 | `AGENTS.md` §4 P1 衔接行、§7.1 表 P1/P4 行 | 标注原型**已退役**、P4/P6 视觉基准改为 `design-tokens.md` + `baseline/` |
| 任务 | `task-breakdown` **T14.5** | 标 ✅（自动化回归 + 基准同步均完成） |
| 模块记录 | `docs/dev/modules/M14.md` | T14.5 由「部分完成」→ ✅ |
| 代码 | — | **零改动**（仅文档/产物） |
| 测试 | `VisualRegression`（T14.5 已落地） | 复用；无新增 |

## 4. 需求回溯

| 项 | 锚点 |
|---|---|
| 界面一致性 / 视觉基准 | PRD §6（界面一致性）；D38（M3 为准） |
| P1 交付物处置 | `AGENTS.md` §4 P1（原型为 P4 视觉基准）——本档按人工拍板修订该衔接口径 |

## 5. 遗留

- `docs/design/P1评审报告*.md`、`direction-approved.md` 等**历史评审文档**保留原样（记录当时决策，不改写历史）；
- 若将来需要「可点击的 HTML 原型」用于演示/宣传，另立任务（属 P7 宣传材料范畴，与本基准无关）。
