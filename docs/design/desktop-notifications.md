# 桌面通知投递矩阵（平台 × 场景 × 通道）

> **用途**：新增或修改任何「通知 / 反馈」通道前，**先读本表**并按最末「变更流程」逐格确认——本表是
> `AGENTS.md §7.3-13`（反馈通道投递矩阵）的**落地文档**，其**可执行部分**是守护用例
> `app/src/test/kotlin/com/wuzhufolio/app/tray/NoticeDeliveryMatrixTest.kt`。
> **为什么需要它**：通道按平台分叉却无对照表 ⇒ **静默失败**。本项目已被同类问题击中**两次**
> （`DEF-49` Linux 气泡无承载；`DEF-64` 非 Linux 平台**根本没投递**），`T16.1`（2026-10-14）复核又发现
> **macOS 仍在缺口里**（第三处，`D51` 后 macOS 已进发布范围 ⇒ 属发布前置）。

## 0. 通道定义

| 通道 | 实现 | 依赖 | 备注 |
|---|---|---|---|
| **① 应用内提示窗** `IN_APP_TOAST` | `DesktopToastWindow`（Compose 独立窗口，右下角、无边框、置顶、**不抢焦点**、3.2 s 自动消失） | 无平台通知 API | 三平台一致；**托盘驻留态亦可见**；`DEF-49`（Linux 实机）+ `DEF-64`（Windows 打包版实机）已验证 |
| **② 原生系统气泡** `NATIVE_BALLOON` | `AwtTrayHost.notify` → AWT `TrayIcon.displayMessage` | 平台托盘/通知中心 | **Linux 无承载**（`DEF-49`）；macOS 走通知中心（未签名应用可靠性未知）⇒ **仅作未支持平台的兜底** |
| **③ 窗口内 toast** | `WzToast`（主窗口内容层） | 主窗口可见 | `Main.kt` 顶栏两条 |
| **④ 页内文案 / 弹层** | 静态文案、`WzModal` 弹层 | 主窗口可见 | 设置页行级提示、启动安全提示 |

**投递路由（单一判定点）**：`AppHost.showNotice(next, scene)`（`AppHost.kt:98-108`）——
按「平台 × `scene`」取 `NoticeDelivery.channelsFor(...)`（**恒非空**）：已发布平台族 → ①；未支持平台 → ② 兜底。

## 1. 矩阵主表

通道标注：**①** = 应用内提示窗 · **②** = 原生系统气泡 · **③** = 窗口内 toast · **④** = 页内文案/弹层 ·
**—** = 不适用。锚点见 §2。

| # | 场景 | Windows | Linux | macOS | 产生点（谁发起） |
|---|------|---------|-------|-------|------------------|
| **S1** | 托盘手动动作·**未登录门禁**（「请先登录」） | **①** ⁽ᴿ¹⁾ | **①** ⁽ᴿ¹⁾ | **①** ⁽ᴿ¹⁾ | `AppHost.syncFromTray` `:126-130` · `refreshFromTray` `:145-149` |
| **S2** | 托盘手动动作·**开始**（「正在同步…」/「正在刷新…」） | **①** | **①** | **①** | `AppHost` `:131` · `:150` |
| **S3** | 托盘手动动作·**结果**（成功 / 无 Key / 失败） | **①** | **①** | **①** | `AppHost` `:138` · `:156` |
| **S4** | **后台调度事件**（同步完成 / 备份提醒） | **①** | **①** | **①** | `AppHost` `:231-241` + `NoticePolicy.noticeFor`（`DesktopBehavior.kt:46-59`） |
| **S5** | **顶栏手动同步 toast** | **③** | **③** | **③** | `Main.kt:141`（`syncViewModel.toast`） |
| **S6** | **顶栏行情刷新 toast** | **③** | **③** | **③** | `Main.kt:143`（`refreshViewModel.toast`） |
| **S7** | **设置页行级同步提示**（「请先登录后再同步交易」） | **④** | **④** | **④** | `ui/.../ExchangeStrings.kt:170`（zh）/ `:242`（en）· `ui/.../ApiCopy.kt:41` |
| **S8** | **启动安全提示**（钥匙串/密钥文件异常） | **④**（弹层） | **④** | **④** | `AppBootstrap.kt:783` → `Main.kt:173` → `AuthGate.kt:255-263` |
| **S9** | **代理状态提示** | **④**（状态位） | **④** | **④** | `AppHost.kt:104` → `AuthGate.kt:83-84 / :207` |
| **S10** | **托盘不可用降级**（无提示、只记日志） | **—**（设计） | **—**（设计） | **—**（设计） | `AppHost.kt:235`（`if (!traySupported) return@collect`） |
| **S11** | 工程提示（视觉回归 / 出片写回） | **—** | **—** | **—** | 非运行时通道：CI 摘要与构建产物，不属桌面通知 |

⁽ᴿ¹⁾ 三个平台同格同通道：**由 `AppHost.kt:268-280` 组合 `DesktopToastWindow` 渲染**（见 §2 锚点表）。

**读法**：S1–S4 走 §0 的**单一投递判定点**（故三平台同列）；S5–S9 是**主窗口内容**（窗口可见才可见，
与平台无关，但**驻留托盘时不可见**——需提示用户时优先用 ①）；S10 是**有意静默**（托盘不可用时不该弹气泡）。

## 2. 锚点表（「由谁渲染」）

| 环节 | 代码锚点 | 说明 |
|------|----------|------|
| 产生（S1–S3） | `app/.../AppHost.kt:126-130` · `:131` · `:138`（同步侧）/ `:145-149` · `:150` · `:156`（行情侧） | 门禁用 `TrayActionGate.lockedNotice`（`:21`）；文案工厂 `tray/DesktopNoticeText.kt:48`（门禁）/ `:51` / `:59`（开始）/ `:53` / `:63` / `:69` / `:73`（结果、失败） |
| 产生（S4） | `AppHost.kt:231-241`（`scheduler.events`）→ `noticeFor`（`:314`）→ `NoticePolicy.noticeFor`（`tray/DesktopBehavior.kt:46-59`） | 两级开关：`syncNotification` / `backupReminder`；行情刷新与日志轮转**不发通知**（`NoticePolicy` 注释：避免噪音） |
| **路由（唯一判定点）** | `AppHost.kt:98-108`（`showNotice`）→ `NoticeDelivery.channelsFor(scene, os)`（`tray/NoticeDelivery.kt`） | 已发布平台 → ①；未支持平台 → ② 兜底（`trayHost?.notify`）。**任何平台都不得空投递** |
| 渲染（① ） | `AppHost.kt:268-280` → `tray/DesktopToastWindow.kt:33-71` → `ui/.../tray/TrayNoticeCard.kt:39`（文案/配色）· 时长 `TRAY_NOTICE_VISIBLE_MS = 3200 ms`（`:77`） | 独立窗口：`undecorated` / `alwaysOnTop` / `focusable = false` / `BottomEnd`；`noticeKey`（序号）变化即重新计时 |
| 渲染（② ） | `tray/AwtTrayHost.kt:97-106`（`notify` → `displayMessage`；托盘未注册时静默忽略） | 仅兜底路径使用；`DEF-49` 已证 Linux 无承载 |
| 渲染（③ ） | `Main.kt:141` / `:143` → `:269-276` → `AuthGate.kt:75-81` → `MainShell`（`WzToast`） | 主窗口内容层 |
| 渲染（④ ） | `AuthGate.kt:255-263`（启动提示弹层）· `ExchangeStrings.kt:170` / `:242`（行级文案）· `AuthGate.kt:207`（代理状态） | 主窗口内容层 |
| 守护用例 | `app/src/test/.../tray/NoticeDeliveryMatrixTest.kt`（矩阵不变量）· `NoticeDeliveryTest.kt`（平台→主通道基础映射）· `DesktopNoticeTextTest.kt`（文案与截断） | 运行：`.\gradlew.bat :app:test --tests "*Notice*" --no-build-cache --console=plain` |

## 3. 变更流程（**强制**，`§7.3-13①`）

1. 新增/修改任何通知或反馈通道 ⇒ **先更新本表**（新场景加行、新平台加列、单元格填「通道 + 代码锚点」）；
2. 若路由需按平台/场景分叉 ⇒ 改 `NoticeDelivery.channelsFor`，并在 `NoticeDeliveryMatrixTest` 中**同步断言**
   （现口径两种场景同路由，该不变量有用例守护：`both scenes resolve identically…`）；
3. **任一格出现「设了状态但没人渲染」⇒ 按缺陷处理**（`§7.3-13②`），不得作为已知限制放行；
4. 新增平台进入发布范围（如 `D51` 的 macOS）⇒ 必须**逐格复验**并在 §4 更新缺口状态；
5. 真机可见性一律以 `manual-test-guide.md §23`（发布后真机补测）为准——**单测只能验代码路径**。

## 4. 已知缺口与残差风险

| # | 项 | 状态 |
|---|----|------|
| **G1** | **macOS 托盘手动动作原先完全不投递**（`useInAppToast` 口径为 `linux \|\| windows`，而渲染仅在 `inAppToast` 时组合；`DEF-64` 只修了 Windows） | ✅ **已闭环（`T16.1`，2026-10-14）**：三平台统一走 ① + 未支持平台 ② 兜底 + 6 条矩阵守护用例 |
| **G2** | 未支持平台（FreeBSD / SunOS 等）依赖 ② 原生气泡兜底 | ⚠️ **无真机验证**（设计上保证「不静默」，但可见性未验；登记） |
| **G3** | **macOS 全通道无真机验证**（本机无 macOS；CI 仅有测试作业与打包作业） | ⚠️ **残差风险已由人工接受**（`D51`，2026-10-14）；用户反馈渠道收集实况 |
| **G4** | ② 在 Linux 无承载（`DEF-49`） | ℹ️ 既知；当前**仅兜底路径**使用，已发布平台不走 ② |
| **G5** | 托盘不可用时 S1–S4 全部只记日志（`AppHost.kt:235`） | ℹ️ **设计如此**（不该给不存在的托盘弹提示）；登记为观察项：用户可能不知情 ⇒ 由用户指南说明 |
| **G6** | S5–S9 在主窗口不可见（最小化到托盘时） | ℹ️ 既知；需要「驻留时也可见」的反馈请用 ①（`§7.3-13` 变更流程第 2 步） |

## 5. 相关文档

- `AGENTS.md §7.3-13`（本条规约的条文与来源）
- `docs/dev/modules/M16.md`（`T16.1` 实施与验收记录）· `docs/dev/decisions/D51-*.md`（macOS 进发布范围）
- `docs/test/manual-test-guide.md §23`（发布后真机补测，含托盘提示复验项）· `docs/test/defects.md`（`DEF-49` / `DEF-64`）
- `docs/design/interaction.md`（异常态与提示文案口径）· `docs/design/design-tokens.md`（提示卡视觉）
