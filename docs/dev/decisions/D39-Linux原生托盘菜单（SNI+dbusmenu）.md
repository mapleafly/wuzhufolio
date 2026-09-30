# D39 · Linux 原生托盘菜单（StatusNotifierItem + dbusmenu）

> **决策号**：D39 · **日期**：2026-09-29 · **级别**：**C1**（人工拍板，2026-09-29）
> **来源**：DEF-57（托盘菜单在 GNOME 下"看得见、点不到"）的**五轮实测定位**结论 + 人工选择「A 方案」
> **关联**：`docs/test/defects.md` DEF-57 · `docs/dev/STATUS.md` · `task-breakdown M14/T14.8` · 用户指南 §5.8
> **状态**：✅ **已完成并端到端验证（2026-09-29）**——系统渲染菜单可弹出、点选真实生效。

---

## 1. 背景（为什么必须改实现，而不是继续修窗口）

DEF-57 五轮实测（Ubuntu 24.04 / GNOME / X11 / 缩放 2，Agent 本机用 `java.awt.Robot` 走**真实托盘右键**路径复现）：

| 观测 | 结论 |
|---|---|
| `Robot` 截图：菜单**完整画在主窗口之上**（四项 / M3 样式 / 中文正常） | 渲染与视觉栈序无问题 |
| AWT `mouseEntered=0`、Compose `input: pointer=0`；**键盘 ↑/↓/Enter/Esc 正常** | 鼠标事件确实没进来 |
| `XQueryPointer`：**指针下最顶层窗口就是菜单窗** | 不是"被主窗口压住" |
| 外部 `XGrabPointer` 返回 **Success** | **不存在任何 X 层指针抓取** |
| 菜单打开时**主窗口仍可拖动** | 输入未被全局冻结 |
| **同一窗口、同一位置**，程序化（DEV 钩子）弹出时鼠标**完全正常** | 决定因素是**触发路径**（真实托盘点击） |
| 应用内自持指针抓取（`owner_events` 真/假）**均无效** | 不是"谁抓指针"的问题 |
| 延迟 1.2s 再弹**无效** | 不是 Shell 的瞬时状态 |

**结论**：真实点击 GNOME Shell 的 **XEmbed 托盘图标**后，**Shell 在合成器层面吞掉指针事件**（X 工具链不可见），
任何**自绘 X11 弹窗**在该桌面上都拿不到鼠标。这是 GNOME Shell + XEmbed 托盘图标的交互机制限制，**非本应用代码缺陷**。

## 2. 拍板结论（A 方案）

**Linux 改用桌面原生托盘协议：StatusNotifierItem（SNI）+ `com.canonical.dbusmenu`**——
**菜单由 Shell 自己渲染**，输入由 Shell 自己拥有，从根上绕开上述限制；且中文由 Shell 绘制（DEF-15 的 AWT 菜单文本乱码问题在 Linux 侧一并消失）。
**Windows / macOS 保持现有 AWT 托盘 + Compose 自绘菜单**（该路径在两端均实测正常）。

### 2.1 交互口径（与现有菜单**逐项一致**，不得漂移）

| 项 | 行为 |
|---|---|
| 图标 | 复用现有 `TrayIcon.awtImage()` 渲染的 PNG（写入临时主题目录，`IconThemePath` + `IconName`） |
| **左键单击** | `ItemIsMenu=false` ⇒ Shell 调 `Activate` ⇒ **打开主界面**（与现状一致） |
| **右键单击** | Shell 拉取 `GetLayout` 并渲染菜单：**打开主界面 / 立即同步交易 / 立即刷新行情 / ─── / 退出** |
| 点选回调 | `Event(id, "clicked")` ⇒ 派发到既有 `onOpen/onSync/onRefresh/onQuit`（**同一套回调，不新增业务逻辑**） |
| 键盘 | 由 Shell 菜单承担（原生体验） |
| 降级 | SNI 注册失败（无 watcher / 无会话总线）⇒ **回退到现有 AWT 托盘 + Compose 菜单**，并写日志 |

## 3. 影响面扫描（§8.5）

| 类别 | 位置 | 状态 |
|---|---|---|
| 新增（模型/接口） | `app/.../tray/linux/DbusMenuLayout.kt`（布局模型 + dbus-java 类型映射）· `StatusNotifierInterfaces.kt`（三个 D-Bus 接口） | ✅ 已落地并编译通过 |
| 新增（服务/接线） | `app/.../tray/linux/StatusNotifierService.kt`（导出对象 + 注册 + 动作派发）· `AppHost` 平台分支 | ⏳ 进行中（下一轮） |
| 新增（测试） | `DbusMenuLayoutTest`（菜单项一致性 / 递归结构 / 分隔线） | ✅ 已落地 |
| 依赖 | `libs.versions.toml` + `app/build.gradle.kts`：`dbus-java-core` / `dbus-java-transport-native-unixsocket` 4.2.1（**已在依赖图中**，随 java-keyring 传递；此处显式声明） | ✅ |
| 平台边界 | **仅 Linux**；Windows/macOS 代码路径不变 | ✅ |
| 设计侧 | 用户指南 §5.8（托盘操作说明：GNOME 下由系统渲染菜单） | ⏳ 随实现完成回写 |
| 技术侧 | 本档 + `task-breakdown` **T14.8** + `M14` 模块记录 | ✅ |
| 需求侧 | 不改 PRD（菜单项/动作与既有口径一致，属实现路径变更） | ➖ |
| 数据/加密 | 零改动（不触 §1.1 任何硬约束；D-Bus 仅本机会话总线，无网络） | ➖ |

## 4. 需求回溯

| 项 | 锚点 |
|---|---|
| 托盘菜单四项动作 | PRD 故事 4.3（两类 API 独立：交易同步 / 行情刷新分列）+ 用户指南 §5.8 |
| 隐私与零遥测 | `§1.1-1/2`：仅连本机 session bus，不引入任何网络/上报 |
| 平台一致性 | 同一套菜单项与回调，Linux 走原生协议、Windows/macOS 走自绘菜单 |

## 5. 验证与遗留

### 5.1 已通过（2026-09-29 本机会话总线实测，无需人工）

| 验证项 | 结果 |
|---|---|
| 注册 | `tray sni registered \| bus=org.kde.StatusNotifierItem-<pid>-1`；GNOME watcher 的 `RegisteredStatusNotifierItems` 含本项 ✓ |
| **图标上屏** | `Robot` 截图：GNOME 顶栏出现绿色 W 图标；**杀掉应用后图标消失**（证明该图标确为本应用的 SNI 指示器）✓ |
| 属性 `GetAll` | `Category/Id/Title/Status/IconName/IconThemePath/ItemIsMenu/Menu/WindowId` 全部返回且类型正确（`WindowId` 必须 `i`、`IconThemePath` 必须带 `as` 签名——两处均已按实测修正）✓ |
| **菜单结构 `GetLayout`** | 返回协议要求的 `(u(ia{sv}av))`：根 `id=0` + 五项（打开主界面 / 立即同步交易 / 立即刷新行情 / `type=separator` 分隔线 / 退出），中文标签正确 ✓ |
| **点选派发 `Event`** | `gdbus … Event 3 clicked` → 应用日志 `tray sni menu event \| id=3` 且**真实执行**了行情刷新（`market refresh started manual=true`）✓ |
| 单测 | `DbusMenuLayoutTest`（菜单项一致性 / 递归结构 / 分隔线）· `StatusNotifierExportTest`（导出对象可被 dbus-java 内省）✓ |
| 构建 | `./gradlew build detekt` 绿 ✓ |

> **踩坑留痕（供后续维护）**：① dbus-java 4.2.1 **无法导出实现自带泛型 `Properties` 接口的对象**（签名推导抛 `ClassCastException`）→ 改用非泛型的等价接口 `RawProperties`；② **非泛型 `Tuple` 子类**同样导出失败 → 必须写成泛型（对照 `secret-service` 的 `Pair<A,B>`）；③ `Variant` 包 `List` 必须给显式签名（`"as"`），否则 `Collections$SingletonList` 无法包装；④ SNI 的 `WindowId` 是 `i` 而非 `u`。

### 5.2 已解决：点击不弹菜单 ⇒ **属性类型错两处**（GNOME Shell 日志直接点名）

**最终根因**（`journalctl` 原文，Shell 在注册时校验类型并**拒绝**不符的属性）：

```
Received property Menu with type s does not match expected type o in the expected interface
Received property IconThemePath with type as does not match expected type s in the expected interface
Trying to set property Menu of type s but according to the expected interface the type is o
```

⇒ `org.kde.StatusNotifierItem.Menu` 的 D-Bus 类型是**对象路径 `o`**（我发成了字符串 `s`），被拒后**指示器根本没有菜单**（图标照常显示、点击无反应，与现象完全吻合）；
另外 SNI 的 `IconThemePath` 是**单字符串 `s`**（`com.canonical.dbusmenu` 的 `IconThemePath` 才是 `as`，两者不可混用）。

**修法**：dbus-java 无对象路径类型 ⇒ 用 `Variant(value, "o")` **显式签名**强制（`Variant` 的 `(T, String)` 构造）；SNI 的 `IconThemePath` 改回 `s`，dbusmenu 的保持 `as`。

**端到端验证（2026-09-29 13:00，本机 GNOME/X11）**：右键图标 → **系统渲染的四项菜单**（打开主界面 / 立即同步交易 / 立即刷新行情 / ─── / 退出，中文正常，截图留痕）；
点选「立即刷新行情」→ 应用日志 `tray sni menu event | id=3 event=clicked` → **真实执行** `market refresh started manual=true`；菜单随后 `event=closed` ✓。

> 排查过程中的中间假设（已全部排除，留痕供后续）：① 扩展未收到点击（日志显示收到了）；② 属性白名单类型不符（`visible/enabled/label/type/children-display` 均正确）；③ 陈旧指示器（`Id` 加 pid 无效）；④ 扩展 actor 被销毁（重启 Shell 后该报错不再出现，但仍不弹菜单 ⇒ 非根因）。

### 5.3 历史记录：曾误判为「扩展侧 actor 生命周期」问题（已排除）

**已排除的假设**（逐条实测）：
- ❌ 不是"扩展没收到点击"：GNOME Shell 日志显示点击后扩展**确实调用了 `menu.toggle()`**；
- ❌ 不是属性类型不符：扩展 `PropertyStore.MandatedTypes` 白名单里的 5 个属性（`visible/enabled/label/type/children-display`）我们类型全对；
- ❌ 不是"陈旧指示器（同 `Id`）"：把 SNI `Id` 临时改成带 pid 的全新值后现象不变。

**根因（GNOME Shell 日志原文）**：

```
Object St.BoxLayout (0x…) has been already disposed — impossible to access it.
  popupMenu.js:828 ← indicatorStatusIcon.js:387 (menu.toggle) ← panelMenu.js:90/193
  ← indicatorStatusIcon.js:112/260 ← appIndicator.js:722 ← statusNotifierWatcher.js:105
```

即：扩展把我们的指示器建起来后，其**菜单 actor 已被销毁**（`disposed`），点击时 `menu.toggle()` 访问它即报错 ⇒ 菜单永远弹不出来。
**这是扩展/Shell 侧的 actor 生命周期问题**（应用侧无法直接修复）；诱因很可能是本次排查期间**反复启停应用、同一 `Id` 的指示器被反复创建/销毁**，使扩展内部状态错乱。

**下一步（需人工，一次即可）**：
1. **重启 GNOME Shell**（X11 会话：`Alt`+`F2` → 输入 `r` → 回车；或注销重新登录）——`org.gnome.Shell.Eval` 在 Ubuntu 被锁定，Agent 无法代劳；
2. 重启后运行应用，点顶栏图标 → 应弹出**系统渲染**的四项菜单（中文）；
3. 若仍不弹：改提供 **`IconPixmap`**（当前走 `IconThemePath` + `IconName`）并复查扩展日志（`journalctl -b | grep -A 12 "has been already disposed"`）。

**人工验收口径**：图标出现 → 点击 → 四项菜单（打开主界面 / 立即同步交易 / 立即刷新行情 / 退出，系统渲染、中文）→ 四项动作各自生效。
无 watcher 环境（精简 WM）的降级路径仍需人工确认。
