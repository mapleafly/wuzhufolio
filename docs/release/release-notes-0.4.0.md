# WuZhuFolio 0.4.0 发布说明

> **版本**：`0.4.0`（minor，2026-10-20）· **tag** `v0.4.0` · **发布状态**：**发布准备完成，待人工批准发布**
> **相关决策**：`D50`（迭代范围 · C1）· `D51`（macOS 进发布范围 · C1，反转 2026-09-22 与 2026-10-13 口径）· `D52`（`D41` 改「不认领」· C2，`amends D41`）
> **完整变更**：`CHANGELOG.md [0.4.0]` · **实施记录**：`docs/dev/modules/M16.md`

---

## 亮点

1. **三平台提示一致**：托盘手动动作（同步 / 刷新，含未登录门禁）的提示在三平台**统一走应用内提示窗**；
   修掉 `DEF-65`（macOS 上「设了状态但没人渲染」⇒ 点托盘毫无反应，`DEF-64` 的同型第三处），并补 **11 例**投递矩阵守护。
2. **macOS 首个发行包**（仅 Apple Silicon）：`dmg` / `pkg` / 便携版 zip。
3. **视觉回归升级为「按平台基线 + 分级阻断」**：三平台各 24 张真实渲染基线入库；同平台漂移 >0.5% 即阻断。
4. **工程纪律固化**：`scripts/doc-edit.ps1`（白名单 / 命中断言 / 回读复核）+ `AGENTS.md §7.3` 新增 10–13 条。

## 升级须知

1. **旧全局自选不再自动认领**（`D52`）：0.4.0 起，**升级后不再把旧版全局自选清单认领给第一个访问行情页的账户**。
   旧清单**仍保留在你的本地数据库中（不会被删除）**，但不再展示；如需继续使用，请在**行情页重新添加**（或从你自己的备份恢复）。
   **多账户用户尤其注意**：此前该行为可能把旧清单给了**非**原主账户。
   **降级回 0.3.0 会恢复自动认领行为**（若不希望，请先手动添加任意一个币；见 `rollback.md §4.1.3`）。
2. **macOS 包未签名、未公证、仅 arm64**：首次打开需**右键「打开」**放行（`user-guide.md §10.11`）；**Intel Mac 无法运行**（需自行编译）。
   **本项目没有 macOS 真机 ⇒ 该包未经真机验证**，欢迎在使用中反馈问题。
3. **schema 与 `.cpro` 格式零变更** ⇒ 跨版本备份可互导、可安全降级（`rollback.md §4.1.3`）。

## 平台与产物（Release 附件 **11 件** = 10 产物 + `SHA256SUMS`）

| 平台 | 产物 | 说明 |
|---|---|---|
| Windows 10/11 | `WuZhuFolio-0.4.0.msi` · `WuZhuFolio-0.4.0.exe` · `WuZhuFolio-portable-windows-x64.zip` | **未签名**（SmartScreen 放行见 `user-guide.md §10.9`） |
| Linux | `wuzhufolio_0.4.0-1_amd64.deb` · `wuzhufolio-0.4.0-1.x86_64.rpm` · `wuzhufolio-0.4.0-x86_64.AppImage` · `WuZhuFolio-portable-linux-x64.tar.gz` | — |
| **macOS（0.4.0 新增）** | `WuZhuFolio-1.0.0.dmg` · `WuZhuFolio-1.0.0.pkg` · `WuZhuFolio-portable-macos-arm64.zip` | **仅 Apple Silicon（arm64）** · **未签名 · 未公证** · **无真机验证** |
| 校验 | `SHA256SUMS` | Linux/macOS：`sha256sum -c --ignore-missing SHA256SUMS`；Windows：`certutil -hashfile <文件> SHA256` 比对 |

> **macOS 命名说明**：`dmg`/`pkg` 的文件名版本段为 **`1.0.0`**（Apple `CFBundleVersion` 规定首段非 0，与应用版本 `0.4.0` **解耦**）→ 请以**实际文件名**为准。

## 已知限制

- **macOS 无真机验证**（本项目无 macOS 设备）；**未签名、未公证**（`D51`：不采购证书）⇒ Gatekeeper 会拦截，需右键放行。
- **Intel（x86_64）Mac 不支持**本版二进制（arm64-only）；可自行编译。
- 视觉回归阻断线暂为 **0.5%**（收紧到 0.05% 的条件：同平台连续两轮 CI `max ≤ 0.01%`）。
- 无自动更新；升级请手动下载安装包覆盖安装（数据不受影响）。

**校验与安装**：见 `user-guide.md §2`（安装）· `§10.9`（Windows SmartScreen）· `§10.11`（macOS Gatekeeper）· `§3.5.0`（本版升级须知）。