# D20 · GUI 共性约束（回溯式建档）

> 2026-09-04 人工验收确立（M2 密码弹窗 + M5 Key 弹窗同根缺陷）。**回溯式建档**：2026-09-04 自 STATUS「已决策事项 20」/ AGENTS.md §7.3 整理。

## 拍板结论

桌面端 `Popup` 无法可靠接收键盘输入（focusable=false 均不可靠、focusable=true 会创建独立 AWT 窗口脱离 Compose 语义树）→ 固化三条强制约束。

## 约束（详见 AGENTS.md §7.3）

1. 弹窗一律**同窗口就地叠加**（WzModal），禁 Popup/Dialog 承载交互。
2. 含输入框弹窗**打开即聚焦首输入框**（initialFocusRequester / fieldFocusRequester）。
3. 验收强制：Compose UI 测试 `performTextInput` + `assertIsFocused` 路径 + 人工 GUI 键盘逐字录入复验。

## 权威落点

- `AGENTS.md §7.3`；STATUS「已决策事项 20」。

## 现状（生效）

- WzModal 已于 2026-09-04 改为就地叠加层实现；M5 修复轮 + M5.md §8.3 复验通过。
