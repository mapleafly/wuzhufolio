# WuZhuFolio 人工门用例执行指南（Windows / Ubuntu 双环境）

> **用途**：P6 人工门的 🔵 用例（`docs/test/test-cases.md §7`：TC-MAN-01…10）在 **Windows** 与 **Ubuntu**
> 两套环境下的**环境要求、构建运行步骤、执行路径与取证方式**。用例判据以 `test-cases.md §7` 为准，
> 本文件解决「在哪台机器、装什么、敲什么命令、怎么留证据」。
> **执行人**：项目负责人 / 人工验收者（Agent 不可替代：托盘、读屏、真实 Key、目标机、视觉走查）。
> **前置**：自动化基线绿（§3.3）；如失败先回到 P6 报告排查，不要带着红基线做人工走查。
> **日期**：2026-09-14 · **有效需求**：PRD V2.0 + Δ{D21, D24, D25, D26, D27, D28, D29, D30}

---

## 1. 执行总览

### 1.1 用例 × 平台 × 路径矩阵

| 用例 | 内容 | Windows | Ubuntu | 推荐路径 | 备注 |
|------|------|---------|--------|----------|------|
| TC-MAN-01 | 真实桌面托盘走查 | **必做** | **必做**（需托盘宿主） | 路径 B（打包态） | WSLg 不可做（无托盘协议）；GNOME 需 AppIndicator 扩展，无宿主时验证**降级分支** |
| TC-MAN-02 | 开机自启实机验证 | **必做** | **必做** | 路径 B（打包态） | 开发态预期置灰（`jpackage.app-path` 为空） |
| TC-MAN-03 | 读屏 NVDA / JAWS | **必做** | 可选（Orca） | 路径 A 或 B | Windows 是主战场 |
| TC-MAN-04 | 目标机性能（4GB 双核） | 任一平台 | 任一平台 | 路径 B（打包版自带 JRE） | 开发态需 JDK 17；打包版**无需预装 Java** |
| TC-MAN-05 | 断网 / 代理异常 | **必做** | **必做** | 路径 A | 用系统代理开关或防火墙模拟 |
| TC-MAN-06 | 纯键盘全流程 | **必做** | 可选 | 路径 A | 只记键盘路径 |
| TC-MAN-07 | 真实交易所只读 Key 冒烟 | 任一平台 | 任一平台 | 路径 A 或 B | 需人工准备币安**只读** Key；Agent 不索取 |
| TC-MAN-08 | 真实桌面 GUI 全流程 | **必做** | **必做** | 路径 A 或 B | 1280×800 与 1024×768 两档窗口 |
| TC-MAN-09 | 出站抓包 + 权限实证 | **必做** | **必做** | 路径 A | 权限判据按平台不同（见 §6 TC-MAN-09） |
| TC-MAN-10 | 外链与关于页走查 | **必做** | **必做** | 路径 A | 需系统浏览器 |

### 1.1-1 人工门执行进展（截至 2026-09-15 · 第十轮）

| 用例 | 平台 | 结果 | 判定轮次/日期 | 证据指向 |
|------|------|------|---------------|----------|
| TC-MAN-01 真实桌面托盘走查 | Windows 11 | ⏳ **待明确判定** | — | 首轮报出菜单乱码（**DEF-15**）与语言不跟随（**DEF-18/19**）均已修复并在第二～四轮复验中未再复现；**尚缺一次正式的「三项菜单动作 + 关窗驻留 + 后台通知」判定** |
| TC-MAN-02 开机自启 | Windows 11 | ⏳ **阻塞（打包版启动失败）** | 第十轮 · 2026-09-15 | 步骤 1–2 正常；**步骤 3 双击安装版图标弹「Failed to launch JVM」→ DEF-42（P1，定性中）**；排查与绕行见 **§16**，取证脚本 `scripts/diagnose-packaged-launch.ps1` |
| TC-MAN-03 读屏 NVDA/JAWS | Windows 11 | ✅ **通过** | 第七轮 · 2026-09-15 | `manual-test-guide.md §14`；缺陷 DEF-13（隐形焦点目标）已修 |
| TC-MAN-04 目标机性能 | Windows 11 | ✅ **通过** | 第七轮 · 2026-09-15 | `manual-test-guide.md §14`（含受限环境等效模拟数据：KDF 172.6 ms ≪2 s） |
| TC-MAN-05 断网/代理异常 | Windows 11 | ✅ **通过** | 第八轮 · 2026-09-15 | 口径见 DEF-16（拔网不即时改状态属设计行为） |
| TC-MAN-06 纯键盘全流程 | Windows 11 | ✅ **通过** | 第六轮 · 2026-09-15 | `keyboard-walkthrough.md`；缺陷 DEF-13/14/20/21/27 已修 |
| TC-MAN-07 真实只读 Key 冒烟 | Windows 11 · Binance | ✅ **通过** | 第六轮 · 2026-09-15 | 缺陷 DEF-20/22/25/26 已修并复验；判定见第八轮汇总 |
| TC-MAN-08 真实桌面 GUI 全流程 | Windows 11 · 三档分辨率 | ✅ **通过** | 第九轮 · 2026-09-15 | 六～八轮累计修复 DEF-27…DEF-41（入口焦点/窄窗表格/卡片层级/网格线/弹窗尺寸）后复验通过 |
| TC-MAN-09 出站抓包 + 权限实证 | Ubuntu（建议） | ⬜ 未执行 | — | 工具 `scripts/outbound-capture-proxy.py`；判据 `security-checklist.md §8-4` |
| TC-MAN-10 外链与关于页 | Windows 11 | ✅ **通过** | 第六轮 · 2026-09-15 | 系统浏览器打开 4 个入口（交易所密钥页/隐私政策/源码/GitHub） |

> **剩余 3 项**：TC-MAN-01（待明确判定）、TC-MAN-02（**阻塞：打包版启动失败 DEF-42**，见 §16）、TC-MAN-09（未执行）。
> 其余 7 项已由人工判定通过（记录日期与轮次见上表；每轮反馈与修复见 `defects.md` DEF-01…DEF-42）。
> **2026-09-15 第十轮**：**TC-MAN-08 真实桌面 GUI 全流程人工判定通过 ✅**；TC-MAN-02 在步骤 3 被 **DEF-42** 阻断。

> **Ubuntu 与 Windows 的分工建议**：托盘/自启/读屏在 Windows 最完整（Credential Manager + SystemTray 原生可用）；
> Ubuntu 覆盖 deb/rpm/AppImage 三种分发形态与 Linux 钥匙串/托盘宿主行为。两平台都做的项见上表「必做」。

### 1.2 建议顺序（约 2–4 小时）

1. §3 通用准备 + 自动化基线（10 分钟）
2. Windows：路径 B 安装打包版 → TC-MAN-02（自启）→ TC-MAN-01（托盘）→ TC-MAN-08（全流程）→ TC-MAN-03（读屏）（60–90 分钟）
3. Ubuntu：路径 A 开发态 → TC-MAN-05/06/09/10 → 路径 B 安装 deb/AppImage → TC-MAN-01/08（60–90 分钟）
4. TC-MAN-04（目标机，单独安排）；TC-MAN-07（有真实只读 Key 时随时可做）
5. §7 汇总证据 → 在 `docs/dev/STATUS.md` P6 门写结论

---

## 2. 环境要求

### 2.1 两平台共同

| 组件 | 要求 | 用途 / 缺失后果 |
|------|------|-----------------|
| Git | 2.30+ | 取源码；`git rev-parse --short HEAD` 记录被测版本 |
| JDK | **Temurin 17**（`JAVA_HOME` 指向它） | Gradle 构建与开发态运行；jpackage 要求 17+ |
| Gradle | **不安装** | 仓库 `./gradlew`（8.14.4）为唯一真源 |
| 图形会话 | 真实桌面（非 SSH 无头） | GUI 走查；无头环境无法执行 TC-MAN-01/08 |
| 磁盘 | ≥ 3 GB 空闲 | 构建缓存 + 打包产物（deb/rpm/AppImage 各 130–145 MB） |
| 内存 | 开发态 ≥ 4 GB（构建期）；目标机用例另见 TC-MAN-04 | JVM + Compose |
| 网络 | 可选（离线路径本身就是用例） | TC-MAN-05 需要能切网 |

### 2.2 Windows 专用

| 项 | 要求 | 说明 |
|----|------|------|
| 系统 | Windows 10/11 x64（桌面版） | Server Core 无桌面 → GUI/托盘不可用 |
| JDK 安装 | `winget install EclipseAdoptium.Temurin.17.JDK` | 装完设 `$env:JAVA_HOME`（见 §4.1） |
| 钥匙串 | **系统自带**（凭据管理器 / Credential Manager） | 无需安装；启动日志应出现 `keyring` 正常路径（无「降级」告警） |
| 托盘 | **系统自带**（通知区域） | `SystemTray.isSupported()=true` |
| Python 3（可选） | 用于 `scripts/outbound-capture-proxy.py` | TC-MAN-09 抓包；不装可改用 Ubuntu 侧执行抓包 |
| WiX（仅本地出 msi） | WiX Toolset 3.14（`dotnet tool install --global wix` 或官方安装包） | 只做「本地出包」时需要；**用 CI 产物可免** |
| 权限 | 安装 msi/exe 需管理员 | `msiexec /i` 或双击安装器 |

### 2.3 Ubuntu 专用

| 项 | 要求 | 说明 |
|----|------|------|
| 系统 | Ubuntu 22.04 / 24.04 x64（**桌面版**） | 无桌面会话 → 无 GUI/托盘 |
| JDK 安装 | `sudo apt install -y openjdk-17-jdk`（或 mise/temurin 仓库） | `java -version` 应显示 17 |
| 钥匙串（真实路径） | `sudo apt install -y gnome-keyring libsecret-1-0` | **缺失时应用按设计降级**为 0600 密钥文件 + 启动「安全提示」（这是 M1 既定语义，不是缺陷；若要验真实钥匙串路径必须装并在**桌面会话内**运行） |
| 托盘宿主 | GNOME：安装「AppIndicator and KStatusNotifierItem Support」扩展；KDE/XFCE 自带 | 无宿主时 `SystemTray.isSupported()=false` → 应用走**降级分支**（关窗即退出，不静默藏窗口）；此时 TC-MAN-01 验的是降级分支，托盘菜单本身需在有宿主的桌面执行 |
| 图形依赖 | 虚拟机无 3D 加速时用软件渲染：`JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE_FAST"` | 否则可能 `skiko.RenderException: Cannot create Linux GL context`（字体已内嵌，无需装 CJK 字体） |
| 打包依赖（仅本地出包） | `sudo apt install -y fakeroot rpm`（rpm 需 `rpmbuild`）；AppImage 需 `libfuse2`（无 FUSE 用 `APPIMAGE_EXTRACT_AND_RUN=1`） | 用 CI 产物可免 |
| 抓包脚本 | `python3`（系统自带） | `scripts/outbound-capture-proxy.py` |

> **WSL2（本仓库开发基准环境）不算 Ubuntu 桌面**：WSLg 无托盘协议、通常无 Secret Service，
> 只适合跑自动化与开发态功能走查；TC-MAN-01/02/03 必须在**真实桌面**或打包版上做。

---

## 3. 通用准备（两平台一致）

### 3.1 取代码并锁定被测版本

```bash
git clone https://github.com/mapleafly/wuzhufolio.git
cd wuzhufolio
git rev-parse --short HEAD          # 记录到证据表（被测版本）
```

### 3.2 JDK 就绪

```bash
java -version        # 期望 17.x（Temurin 或其他 17 发行版）
```

### 3.3 自动化基线（必须先绿）

```bash
# Windows PowerShell
.\gradlew.bat clean build detekt --no-build-cache
# Ubuntu
./gradlew clean build detekt --no-build-cache
```

期望：**678 用例（670 执行 0 失败 + 8 跳过）+ detekt 0 + 编译警告 0**（跳过 = 钥匙串真实后端/真实网络门控，
按平台不同：Linux 跳过 4 项钥匙串、三平台跳过 4 项 live smoke）。

### 3.4 数据隔离与安全（**强烈建议**）

人工走查**不要**用日常库，用独立数据目录；同一条用例可反复重来：

| 平台 | 设置方式 |
|------|----------|
| Windows PowerShell | `$env:WUZHUFOLIO_DATA_DIR="$env:TEMP\wzf-manual"` |
| Ubuntu / WSL | `export WUZHUFOLIO_DATA_DIR=/tmp/wzf-manual` |
| 打包版任意平台 | 同样读该环境变量；也可用 JVM 参数 `-Dwuzhufolio.dataDir=...`（开发态 `:app:run` 用 `JAVA_TOOL_OPTIONS`） |

安全纪律（PRD §1.1）：

1. **只用只读 Key**（TC-MAN-07），测试建议用小额账户；
2. 走查数据可用假数据；若涉真实数据，证据包内**不要**包含 `.cpro`、CSV 导出、`master.key`/`device.key`、完整日志；
3. 日志导出/诊断报告本身已脱敏，但仍建议人工复核后再外发（PRD §6）。

### 3.5 日志与证据位置

| 项 | Windows | Ubuntu |
|----|---------|--------|
| 日志 | `%USERPROFILE%\.wuzhufolio\logs\wuzhufolio.log`（隔离目录下为 `%TEMP%\wzf-manual\logs\...`） | `~/.wuzhufolio/logs/wuzhufolio.log`（隔离目录同理） |
| 数据/密钥/库 | 同目录：`master.key` / `device.key` / `wuzhufolio.db` | 同左 |
| 备份临时目录 | `<数据目录>\backups\` | `<数据目录>/backups/` |

---

## 4. 执行路径 A：开发态（`:app:run`）

> 覆盖：功能走查、异常态、键盘、外链、抓包、性能计时（非目标机）。
> 不覆盖：托盘（WSLg）、开机自启（开发态置灰）、打包版 JRE/安装行为。

### 4.1 Windows（PowerShell）

```powershell
# 1) JDK 17（已装可跳过）
winget install EclipseAdoptium.Temurin.17.JDK
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"   # 按实际安装路径

# 2) 隔离数据目录 + 运行
$env:WUZHUFOLIO_DATA_DIR = "$env:TEMP\wzf-manual"
.\gradlew.bat :app:run
```

### 4.2 Ubuntu（bash）

```bash
sudo apt install -y openjdk-17-jdk            # 已装可跳过
export WUZHUFOLIO_DATA_DIR=/tmp/wzf-manual
export JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE_FAST"   # 无 3D 加速的虚拟机需要
./gradlew :app:run
```

### 4.3 专项命令（两平台对照）

| 用途 | Windows | Ubuntu |
|------|---------|--------|
| KDF 基准（TC-MAN-04） | `.\gradlew.bat :domain:kdfBenchmark` | `./gradlew :domain:kdfBenchmark` |
| `.cpro` 内存曲线（TC-MAN-04） | `.\gradlew.bat :domain:backupBenchmark` / 加 `-PbenchXmx=512m` | `./gradlew :domain:backupBenchmark -PbenchXmx=512m` |
| 真实网络冒烟（TC-MAN-07 前置） | `$env:WZF_LIVE_SMOKE="1"; .\gradlew.bat --no-daemon :data:test --tests "com.wuzhufolio.data.smoke.*" --rerun-tasks` | `WZF_LIVE_SMOKE=1 ./gradlew --no-daemon :data:test --tests "com.wuzhufolio.data.smoke.*" --rerun-tasks` |
| 出站抓包（TC-MAN-09） | 见 §6 TC-MAN-09（Windows 用「系统代理」指向抓包端口） | 见 §6 TC-MAN-09（`https_proxy` 即可） |

---

## 5. 执行路径 B：打包态（托盘 / 自启 / 安装验收必用）

### 5.1 用 CI 产物（最快，推荐）

三平台安装包由 CI 的 `package` job 产出并归档（每次推送后可在 Actions 页面下载）。命令行取最新一次成功产物：

```bash
# 任平台（需 gh CLI 已登录）
RUN=$(gh run list --repo mapleafly/wuzhufolio --workflow CI --status success --limit 1 --json databaseId --jq '.[0].databaseId')
gh run download "$RUN" --repo mapleafly/wuzhufolio -n wuzhufolio-windows-latest-native -D ./ci-native   # Windows 机器上
gh run download "$RUN" --repo mapleafly/wuzhufolio -n wuzhufolio-ubuntu-latest-native  -D ./ci-native   # Ubuntu 机器上
```

产物文件名（版本取自 `app/build.gradle.kts` 的 `appVersion`，当前 `0.1.0`）：

| 平台 | 文件 |
|------|------|
| Windows | `msi/WuZhuFolio-0.1.0.msi`、`exe/WuZhuFolio-0.1.0.exe`（jpackage 安装器） |
| Ubuntu | `deb/wuzhufolio_0.1.0-1_amd64.deb`、`rpm/wuzhufolio-0.1.0-1.x86_64.rpm`、`appimage/wuzhufolio-0.1.0-x86_64.AppImage` |
| 校验 | 同 run 的 `wuzhufolio-<os>-artifacts-manifest` 内含 SHA256，可先核对再安装 |

### 5.2 本地出包（可选）

```powershell
# Windows（需 WiX）
.\gradlew.bat :app:packageDistributionForCurrentOS      # → msi + exe
.\gradlew.bat :app:createDistributable                  # → app-image（免安装目录）
```

```bash
# Ubuntu
sudo apt install -y fakeroot rpm
./gradlew :app:packageDistributionForCurrentOS          # → deb + rpm
./gradlew :app:createDistributable && scripts/package-appimage.sh   # → AppImage
```

### 5.3 安装 / 运行 / 卸载

**Windows**

```powershell
msiexec /i .\ci-native\msi\WuZhuFolio-0.1.0.msi          # 或双击 .exe
# 启动：开始菜单「WuZhuFolio」，或
& "$env:LOCALAPPDATA\WuZhuFolio\WuZhuFolio.exe"          # 路径以安装器实际落点为准
# 卸载：设置 → 应用 → WuZhuFolio → 卸载（或 msiexec /x {ProductCode}）
```

**Ubuntu**

```bash
sudo dpkg -i ./ci-native/deb/wuzhufolio_0.1.0-1_amd64.deb     # 缺依赖时 sudo apt -f install
dpkg -L wuzhufolio | grep -E 'bin/|\.desktop'                 # 查可执行文件与桌面入口（通常 /opt/wuzhufolio/bin/WuZhuFolio）
/opt/wuzhufolio/bin/WuZhuFolio &                              # 或从应用菜单启动
sudo dpkg -r wuzhufolio                                       # 卸载

# 免安装形态（AppImage）
chmod +x ./ci-native/appimage/wuzhufolio-0.1.0-x86_64.AppImage
./ci-native/appimage/wuzhufolio-0.1.0-x86_64.AppImage
# 无 FUSE 环境：APPIMAGE_EXTRACT_AND_RUN=1 ./…AppImage
```

> **打包版数据目录同样受 `WUZHUFOLIO_DATA_DIR` 控制**；不设置则用 `~/.wuzhufolio`（Windows：`%USERPROFILE%\.wuzhufolio`）。
> 走查前建议先设隔离目录，避免污染日常库。

---

## 6. 用例执行卡（关键命令与取证）

> 判据以 `docs/test/test-cases.md §7` 为准；此处只补「在哪跑、怎么起、怎么取证、常见假失败」。

### TC-MAN-01 托盘走查

- **平台**：Windows 必做；Ubuntu 需托盘宿主（无宿主则验降级分支）。
- **起法**：§5.3 安装并启动打包版（或 `:app:run`，Windows 原生桌面可用）。
- **取证**：① 关窗后进程仍在（Windows `Get-Process WuZhuFolio`；Ubuntu `pgrep -af WuZhuFolio`）；
  ② 托盘菜单三项各截一张图；③ 设置页「托盘与后台」四项开关截图；④ 关闭「最小化到托盘」后再关窗 → 进程消失。
- **常见假失败**：Ubuntu 无 AppIndicator 扩展时看不到图标 → 属**预期降级**（此时验「关窗即退出且不藏窗口」，
  并在记录里注明「本机无托盘宿主，托盘菜单项未验」）。

### TC-MAN-02 开机自启

- **平台**：Windows 必做；Ubuntu 必做。**必须打包版**（开发态界面置灰并说明原因，属预期）。
- **步骤与取证**：开启开关后按平台查注册项，把命令输出留档：

```powershell
# Windows
reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v WuZhuFolio
```

```bash
# Ubuntu
cat ~/.config/autostart/*.desktop
```

  然后**注销并重新登录**，确认应用自动驻留托盘；关闭开关后复查注册项已消失（命令同上，应无输出）。
- **常见假失败**：路径含空格时的引号处理；重装/移动安装目录 → 依赖「启动自愈」重建注册项。
- ⚠️ **若双击图标弹「Failed to launch JVM」（DEF-42）**：见 **§16 排查附录**（含 4 步快速判定与绕行办法），
  该弹窗是 **jpackage 原生启动器**报的（表示随包私有运行时没被拉起来），**与机器上装不装 Java、装哪个版本无关**。

### TC-MAN-03 读屏（Windows + NVDA）

- **起法**：安装 NVDA（免费）→ 启动 NVDA → 启动应用 → 全程 `Tab`/方向键。
- **取证**：录屏（30–60 秒/场景）或逐条文字记录「朗读内容」；重点 5 处：登录页控件名、仪表盘六卡与环形图分区、
  资产列表行、弹窗字段与按钮、toast 自动朗读。
- **常见假失败**：NVDA 需要先启动再启动应用才会挂接；Compose 的语义树在首帧后建立，稍等 1–2 秒再 Tab。

### TC-MAN-04 目标机性能（4GB 双核）

- **起法**：优先用**打包版**（自带私有 JRE，**目标机无需预装 Java**）；若用开发态则需 JDK 17。
- **计时**：口令提交 → 主壳出现（秒表或录屏帧差）；仪表盘/资产列表首屏 ≤2 秒；刷新期间仍可滚动/切页。
- **取证**：`kdfBenchmark` 与 `backupBenchmark` 原始输出（贴入证据）+ 手写计时表。
- **常见假失败**：首次启动含建库/迁移会明显更慢 → 以**第二次冷启动**为准并在记录中注明。

### TC-MAN-05 断网 / 代理异常

- **起法（Windows）**：设置 → 网络和 Internet → 代理 → 手动设置代理（或直接禁用网卡）。
- **起法（Ubuntu）**：`nmcli networking off` / 拔网线 / 设一个不存在的代理。
- **取证**：状态栏断链文案截图 + 日志中 `market refresh … error=` 行 + 恢复网络后自动/手动刷新成功的截图；
  离线记录标「待定价」「估算中」，联网后回填并消除标注。
- **常见假失败**：应用启动即刷新（P5 修复）——断网启动时首刷失败属预期，恢复网络后需点刷新或等下一轮。

### TC-MAN-06 纯键盘全流程

> **逐键脚本与键位语义见 `docs/test/keyboard-walkthrough.md`**（含实测焦点顺序、判定表、失败取证方式）。


- **起法**：收起鼠标；`Tab`/`Shift+Tab`/`Enter`/`Esc`/方向键完成：登录 → 增资 → 买入 → 看仪表盘 → 备份导出。
- **取证**：录屏 1 段 + 记录任何「鼠标才能完成」的卡点（即为缺陷）。
- **常见假失败**：
  - 候选浮层里用方向键移动、回车确认（不是 Tab 选行）——两种都可用；**选中候选后焦点应留在表单内**（币种→数量、基础币→计价币、计价币→价格），
    若焦点掉出弹窗（下次 Tab 从侧边栏重来）即为 **DEF-20 回归**，请记录报回；
  - `Esc` 关闭弹窗后焦点应回到触发按钮；**弹层打开时** Esc/方向键归弹层（不会跳到侧边栏背后）；
  - 焦点进入页面内容后，`Esc` 或 `↑/↓` 应把焦点交回侧边栏当前项（本版新增；旧包无此行为，先核对 `build=` 行）；
  - 仪表盘等只读页无可聚焦控件，回车切页后焦点留在侧边栏属**预期**（不是缺陷）。

### TC-MAN-07 真实只读 Key 冒烟（Binance）

- **前置**：币安**只读** API Key（禁用提现/交易权限）；先用 §4.3 的真实网络冒烟确认网络连通。
- **取证**：同步记录页截图（新增/跳过/未解析计数）+ 二次「立即同步」为 0 新增 + 校准前后持仓与指标截图 +
  `.cpro` 恢复前后一致性核对表。
- **常见假失败**：币安只返回最近 500 条成交（更早需 CSV）；首次同步会预热市值排名缓存，耗时略长属预期。

### TC-MAN-08 真实桌面 GUI 全流程

- **起法**：§4 或 §5；窗口调至 1280×800 与 1024×768 各走一遍关键页。
- **取证**：逐页截图（含空态/错误态/标注）+ 一条「核心操作点击层级 ≤3」的记录表。
- **常见假失败**：小窗口下表单需滚动（可滚动到即算通过，判据是「不裁切且可达」）。

### TC-MAN-09 出站抓包 + 权限实证

**抓包（推荐仓库内工具，不解密 TLS、不接触业务数据）**

```bash
# Ubuntu / WSL / macOS：抓包代理 + 让应用走它
python3 scripts/outbound-capture-proxy.py --port 8899 --log /tmp/wzf-outbound.log &
export WUZHUFOLIO_DATA_DIR=/tmp/wzf-capture
https_proxy=http://127.0.0.1:8899 http_proxy=http://127.0.0.1:8899 ./gradlew --no-daemon :app:run
# 走查：登录 → 设置→行情与同步→立即刷新 → （可选）同步交易
awk '{print $3}' /tmp/wzf-outbound.log | sort -u     # 期望仅三个白名单主机
```

**Windows**：`python3 scripts\outbound-capture-proxy.py --port 8899 --log $env:TEMP\wzf-outbound.log`，
然后 **设置 → 网络和 Internet → 代理 → 手动设置代理** 填 `127.0.0.1:8899`（应用在 Windows 走 JDK 系统代理，
优先级高于环境变量），启动应用后查看日志文件；结束时把该代理关掉。
若不想在 Windows 装 Python：改在 Ubuntu 侧执行本用例，Windows 只做权限实证。

**权限判据（按平台不同）**

```bash
# Linux / macOS
stat -c '%a %n' <数据目录> <数据目录>/logs <数据目录>/master.key <数据目录>/*.db   # 期望 700/700/600/600
```

```powershell
# Windows：无 POSIX 权限模型（FilePermissions 按设计跳过），验 ACL 仅当前用户可读写
icacls "$env:TEMP\wzf-manual" | Select-String "BUILTIN|Users"    # 期望不出现 Everyone/Users 的可写授权
```

**另需**：`.cpro` 与 CSV 导出内 `grep` 不到任何密钥明文；安装包内不含 `master.key`/`device.key`/`.db`。

### TC-MAN-10 外链与关于页

- **步骤**：关于页隐私政策/发布渠道、API 弹窗教程链接、行情设置页 CG/CMC 注册链接、429 提示注册引导。
- **取证**：点击后系统浏览器截图（URL 可见）+ 关掉浏览器后确认应用自身未发起该域名请求（抓包日志佐证）。
- **常见假失败**：外链由系统浏览器打开属预期；应用内不内置任何 Key 申请页自动填充。

---

## 7. 证据留痕与提交

### 7.1 目录与命名建议

```
docs/test/manual-evidence/<YYYYMMDD>-<platform>/          # 本地留存；是否入库存疑时勿提交敏感内容
  result-table.md          # 汇总表（模板见下）
  tc-man-01-tray-*.png
  tc-man-05-offline-*.png
  tc-man-09-outbound.log   # 抓包日志（只含主机名，无业务数据）
  logs/wuzhufolio.log      # 脱敏日志（提交前人工复核，勿含真实账户名/金额）
```

### 7.2 结果汇总表模板

| 用例 | 日期 | 平台与版本 | 被测 commit | 结果 | 证据 | 备注/缺陷 |
|------|------|------------|-------------|------|------|-----------|
| TC-MAN-01 | 2026-09-15 | Windows 11 23H2 | `0763b61` | 通过 | `tc-man-01-*.png` | 托盘菜单三项均生效 |
| TC-MAN-03 | 2026-09-15 | Windows 11 + NVDA 2026.1 | `0763b61` | 部分通过 | 录屏 | 环形图朗读缺少占比 → 记为缺陷候选 |

### 7.3 提交方式

1. 把汇总表粘贴进 `docs/dev/STATUS.md` 的 P6 节（人工门结论），**不要**提交含真实数据的日志/备份；
2. 发现缺陷时按 `docs/test/defects.md` 格式追加一行（现象/根因/影响/建议级别），由人工定级；
3. 全部通过后在 STATUS 写「P6 人工门通过」并解锁 P7；有 P0/P1 缺陷则停在 P6 修复后复验。

---

## 8. 故障排查（双平台对照）

| 症状 | Windows | Ubuntu |
|------|---------|--------|
| `java: command not found` / 版本不对 | 检查 `$env:JAVA_HOME` 与 PATH；`java -version` 应 17 | `sudo apt install openjdk-17-jdk` 或 `export JAVA_HOME=$(mise where java)` |
| GUI 起不来 / GL 报错 | 一般无（走 DirectX）；虚拟机可试 `-Dskiko.renderApi=SOFTWARE_FAST` | `JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE_FAST"` |
| 启动弹「安全提示」（钥匙串降级） | 不应出现；出现说明 Credential Manager 访问被拒（检查账户策略） | 预期（未装/未运行 gnome-keyring）；装 `gnome-keyring libsecret-1-0` 并在**桌面会话内**运行 |
| 关窗就退出、托盘无图标 | 检查通知区域折叠区是否隐藏了图标 | 无 AppIndicator 宿主 → 预期降级；装扩展后重启应用 |
| 开机自启开关置灰 | 说明跑的是开发态：用打包版 | 同左 |
| 端口/代理冲突 | 关掉系统里的其他代理或换抓包端口 | 同上 |
| 打包失败（本地） | 装 WiX 3.14；或直接用 CI 产物 | `sudo apt install -y fakeroot rpm`；AppImage 报 FUSE 错 → `APPIMAGE_EXTRACT_AND_RUN=1` |
| 测试/运行污染日常库 | 始终设 `WUZHUFOLIO_DATA_DIR` | 同左 |
| **启动即失败**（日志尾部 `bootstrap failed / NoSuchFileException …logs\wuzhufolio.<日期>.N.log`） | **DEF-17，已修复**（跨零点启动时日志轮转与 logback 滚动竞争）：用新构建即可；旧构建遇此可删除 `logs` 目录后重试 | 同左（Linux 极少触发，但同样已修） |
| 无法确认「跑的是哪一版」 | 启动日志首行含 `build=0.1.0+<git short sha>`（P6 新增构建标识）；用它核对 CI 产物对应的提交 | 同左 |

---

## 9. 需求回溯

| 本指南条目 | 锚点 |
|------------|------|
| 用例判据与覆盖点 | `docs/test/test-cases.md §7`（TC-MAN-01…10）、`docs/test/test-plan.md §7` |
| 托盘/自启 DoD | `docs/dev/modules/M11.md §5-2/§5-3`（延期登记与到期检查点） |
| 安全与隐私判据 | `docs/test/security-checklist.md`（§1 数据本地化 / §3.4 权限 / §8 复核命令） |
| 读屏与键盘 | PRD §6 无障碍基线、ADR-001 风险表（Compose 读屏弱于 Web） |
| 目标机性能门槛 | PRD §12（登录 KDF ≤2s）、PRD §6（核心数据 ≤2s）、M1/M13 开放待办 |
| 打包与分发 | ADR-006 §1/§2（三平台格式、签名公证口径）、`scripts/package-appimage.sh` |
| 开发环境 | `docs/tech/dev-setup.md`（mise / WSL2 注记 / 故障排查） |

---

## 10. 首轮人工走查反馈与复验指引（Windows 11 · 2026-09-14）

> 人工在 Windows 11 完成首轮走查，报出 6 条观察。下表是**逐条核实结论 + 复验指引**（详细定级见 `defects.md`）。

| # | 人工观察 | 核实结论 | 复验指引 |
|---|----------|----------|----------|
| 1 | 托盘三行菜单文字**乱码** | **P1 缺陷 DEF-15**，**第 2 版修复（改用 AWT 菜单 + 内嵌字体）人工复验仍乱码** → 推翻字体假设，确认为 **AWT 菜单文本渲染路径本身**的问题 | **第 3 版修复**：托盘菜单改由 **Compose/Skia 自绘**（AWT 只留图标与点击），字体链与应用界面一致。复验：**用新构建**（启动日志首行 `bootstrap ok \| build=0.1.0+<sha>` 可确认版本）→ 托盘右键应见可读中文三项；切 English 后为英文；三项动作生效；Esc/点别处关闭 |
| 2 | 开机自启**未测** | 待人工执行（需打包版；开发态置灰属预期） | 见 §6 TC-MAN-02：开开关 → `reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v WuZhuFolio` → 注销重登观察驻留 → 关开关复查注册项消失 |
| 3 | Tab 只在左侧功能项移动，方向键无效，焦点进不到页面（六卡/图表） | **P1 缺陷 DEF-13**（焦点链重复目标）+ 一处**预期行为**：仪表盘六卡与环形图为**只读展示**，本就不在 Tab 序（读屏经 hover/浏览模式朗读）；方向键在 Compose 中默认不移动焦点（Tab/Shift+Tab 才是） | **已修复**：复验 Tab 应能到达页面内**可交互**控件（刷新行情、排序表头、行点击、表单按钮等），且不再出现「按一下没反应」的空步进；只读卡片仍不参与 Tab 属预期 |
| 4 | 4GB 双核目标机无法提供 | 已用**等效受限环境模拟**替代：`taskset` 限 2 核 + 限堆跑基准（见下表） | 结果：KDF（m=64MiB/t=3/p=1）**172.6 ms** ≪ 2 s 预算（约 11× 余量）；512 MiB 堆下 `.cpro` 轻量/典型/重度档均可完成（峰值 88/139/434 MiB），压力档 OOM（登记容量边界）。**真机首屏计时仍需人工**（或按人工裁决放过） |
| 5 | 拔网后不立即显示断网，手动刷新才显示；恢复网络点刷新即恢复 | **符合设计（DEF-16，非缺陷）**：断链指示的输入是「最近一次行情刷新结果」，最长需等下一轮自动刷新（默认 5 分钟）；请求失败即提示、保留上次价格与时间戳、点击可重试 = PRD 故事 3.2-3 + interaction §1.1 N1 | 复验：拔网 → 状态栏仍显示「直连」属预期 → 点顶栏「刷新行情」应立即转「网络断开」；恢复网络后点刷新应回到「直连」。若要求「拔网即刻提示」＝新增探测行为，登记 P8 |
| 6 | 登录页回车不提交；进页面后键盘无法操作（同 #3） | **P2 缺陷 DEF-14**（登录回车）+ **P1 缺陷 DEF-13**（焦点链，见 #3） | **已修复**：复验 ① 登录页填完密码**直接回车**应登录（空密码回车给内联错误）；② 进资金页后 Tab 应能到达「记录增资/记录撤资」等按钮，回车/空格触发 |

**受限环境模拟原始输出（2026-09-14，`taskset -c 0,1`）**：

```
KDF: m=65536KiB t=3 p=1 → 172.6 ms（best-of-3）；OWASP 下限 19456/2/1 → 43.7 ms
backupBenchmark -Xmx512m: light 88.4 / typical 139.0 / heavy 434.0 MiB 峰值；stress OOM（容量边界）
```

> 说明：该模拟限制 CPU 亲和与堆上限，**不能替代 4 GB 真机的内存压力与首屏计时**；如无真机，可在记录中注明「以受限环境模拟 + 余量口径替代」并由人工裁决放行。

---

## 11. 第四轮人工走查反馈与复验指引（Windows 11 · 2026-09-15）

> 人工在 Windows 11 完成第四轮走查，报出 2 条焦点相关问题。**两条均已修复/落地**，复验步骤见
> `keyboard-walkthrough.md §4`（步骤 2「焦点环自检」、步骤 3「记录增资」、步骤 4「手动买入」、步骤 7「退出弹窗与退出页面」）。

| # | 人工观察 | 核实结论 | 复验指引 |
|---|----------|----------|----------|
| 1 | 「记录增资」币种 /「添加交易」交易对**选完候选后焦点掉出弹窗**，下次 Tab 从侧边栏重来 | **P1 缺陷 DEF-20**（候选行选中即从组合移除 → 焦点目标消失 → Compose 焦点无处恢复，回落窗口根） | **已修复**：选中候选后焦点应落在表单内的**下一个字段**——资金「币种 → 数量」；交易「基础币 → 计价币」「计价币 → 价格」「自定义手续费币种 → 原字段」；随后 Tab 继续在弹窗内走（不会回到侧边栏） |
| 2 | 回车选中侧边栏项后**仍要 Tab 穿过侧边栏余项与顶栏**才进页面；页面内**没有回外壳的出口** | **P2 缺陷 DEF-21**（走查提案 A 落地；分级待人工拍板：Agent 建议 **C1**） | **已实施**：① 回车切页 → 焦点**直接落到页面内容**；② 页面内未被控件消费的 **Esc / ↑ / ↓ → 焦点回侧边栏当前项**；③ 侧边栏内 **↑/↓** 在导航项间移动。注意：**弹层打开时**按键归弹层（先关弹层，不会把焦点甩到背后）；仪表盘等只读页无控件可聚焦，回车后焦点留在侧边栏属**预期** |

**版本确认（务必先做）**：启动后看日志首行 `bootstrap ok | build=0.1.0+<短 sha> | db=…`，
与本轮修复提交一致才继续（`manual-test-guide.md §3.5` 给日志路径）；旧包不含上述两项行为。

---

## 12. 第五轮人工走查反馈与复验指引（Windows 11 · 2026-09-15 · 真实只读 Key 冒烟）

> 人工用**真实币安只读 Key** 完成冒烟，报出 7 条观察。修复与核实结论见 `defects.md` DEF-22…DEF-26。
> **复验前先确认版本**：日志首行 `bootstrap ok | build=0.1.0+b5c9db5 | db=…`。

**本轮产物（CI run [34931651692](https://github.com/mapleafly/wuzhufolio/actions/runs/34931651692) 六 job 全绿，commit `b5c9db5`）**：

| 产物 | SHA256 |
|------|--------|
| `msi\WuZhuFolio-0.1.0.msi` | `b8f5034333493545bdc8229fb0e80fb1e3bcab6efa46e535aa148ade17de367f` |
| `exe\WuZhuFolio-0.1.0.exe` | `570d0bc02f2e979a138cb7ead9b63a25f86e500cde99cd6e2726be217e1e0b06` |

```powershell
gh run download 34931651692 --repo mapleafly/wuzhufolio -n wuzhufolio-windows-latest-native -D .\wzf-windows
Get-FileHash .\wzf-windows\msi\WuZhuFolio-0.1.0.msi -Algorithm SHA256   # 应等于上表
msiexec /i .\wzf-windows\msi\WuZhuFolio-0.1.0.msi
```

| # | 人工观察 | 核实结论 | 复验指引 |
|---|----------|----------|----------|
| 1 | 设置 → API 管理 →「添加 API」弹窗像是**插进页面**里（挤占后面的空间） | **P1 缺陷 DEF-22**：弹层落在设置页滚动容器内部 → `fillMaxSize()` 退化为内容高度，变成流内块 | **已修复**：打开弹窗后页面内容**位置不动**、弹窗**整页居中**并盖住整页；按 Esc/遮罩可关 |
| 2 | 设置 → 行情与同步 → 数据源/兜底「已配置/未配置」弹窗**撑开了页面** | **P1 缺陷 DEF-22**（同根因） | 同上（三个弹窗一起复验） |
| 3 | 设置 → 数据管理 →「恢复数据」弹窗**浮在备份区域、感觉错位** | **P1 缺陷 DEF-23**（同根因：卡片只在自己的内容块内居中，位置随分组漂移） | **已修复**：恢复向导应**整页居中**并在打开时首输入框自动聚焦 |
| 4 | 「添加 API」填写后点保存：**弹窗不关但内容已保存**；行内「编辑」保存却会关窗 | **P1 缺陷 DEF-25**：`addAndSync` 先落库、后同步；同步失败以异常上抛 → UI 表现为「保存失败且弹窗不关」，而密钥已在库里 | **已修复**：保存后**弹窗关闭 + 列表刷新**；失败时提示「密钥已保存；首次同步未成功（原因）。可点『立即同步』重试」；保存中按钮显示「保存中…」并提示首次同步可能数十秒 |
| 5 | 设置各分组标题（通用/网络/托盘与后台…）**比其下二级标签还小** | **P2 缺陷 DEF-24**：分组标题原用 `caption`（11sp） | **已修复**：分组一级标题 = 15/600，大于行标签（14/400） |
| 6 | 手续费卡片标题加粗、其它分组标题不加粗；数据管理卡片标题**明显更大** | **P2 缺陷 DEF-24**：三处各自取值（`caption` / `pageTitle` 20sp / `bodyStrong`） | **已修复**：统一为「页面标题 20/600 → 分组一级 15/600 → 卡内二级 14/600 → 行标签 14/400 → 说明 11/400」；标准见 `design-tokens.md §3` |
| 7 | 「同步交易数据时，是否覆盖了原来手动填写的交易？」 | **核实为非缺陷（DEF-26）**：同步只**追加**交易所成交，绝不修改/删除手写行（去重键含订单号，手写行订单号为空不参与） | **无需修复**，已加回归守护。**注意口径**：同一笔真实交易若「先手写、后同步」会各存一行 → 重复计入持仓/盈亏（PRD 未要求自动合并）；处理办法 = 删掉手写那一行后重新同步，或先同步再补录差异 |

**只读 Key 冒烟记录模板（TC-MAN-07 补充）**

| 项 | 记录内容 |
|----|----------|
| 密钥权限 | 币安只读（禁用提现/交易），Key 别名、添加时间 |
| 首次同步 | 页脚/弹窗提示的新增笔数、跳过（去重）笔数、未解析笔数（未解析多为目录缺币，见 M6 §5） |
| 二次同步 | 应新增 0（增量游标）；再次点「立即同步」观察 toast |
| 数据核对 | 交易所 App 端某笔成交 ↔ 本地交易列表（时间/价/量/手续费）逐笔对照 ≥3 笔 |
| 手写与同步并存 | 手写 1 笔同 pair 交易 → 同步 → 确认两行并存、手写行内容未变（DEF-26 回归的人工复现） |

---

## 13. 第六轮人工走查反馈与复验指引（Windows 11 · 2026-09-15 · GUI 全流程 × 三档分辨率）

> **本轮通过项** ✅：TC-MAN-06 纯键盘全流程 · TC-MAN-07 真实只读 Key 冒烟（Binance）· TC-MAN-10 外链与关于页。
> **本轮修复项**：DEF-27（切页入口焦点）· DEF-28（窄窗表格）· DEF-29（窄窗按钮）· DEF-30（行情搜索候选浮层）；
> 逐条结论见 `defects.md`，分级待人工拍板（四项均建议 C0）。

| # | 人工观察 | 核实结论 | 复验指引 |
|---|----------|----------|----------|
| 1 | 点「设置」进入设置页时，焦点定位在页面中部的**手续费 → 全局默认费率 → 买入费率**输入框 | **P1 缺陷 DEF-27**：切页「进页面」用容器 `FocusRequester` + Compose 子树遍历实现，该遍历**不按 Tab 序**（设置页 Tab 首项 = 基础法币，遍历落到第 22 项 = 买入费率），`bringIntoView` 随之把长页滚到中部 | **已修复**：设置页入口焦点改为页面声明（首个可聚焦控件 = **通用 → 基础法币**）。复验：**从侧边栏进设置页，焦点应在顶部「基础法币」下拉上、页面停在最上方**；交易/资金/行情（搜索框）、资产列表（首列排序）、币种详情（返回）同样落到页面首个控件 |
| 2 | **1280×800**：资产列表币种列放不下三枚标签（持仓异常/估算中/成本不可靠）→ 最后一枚**竖排**、行高被撑高；8 位小数的数量/价格/成本互挤 | **P2 缺陷 DEF-28**：两表列宽用 `weight` 分配，窄窗压到内容宽度以下 → 文本换行/竖排 | **已修复**：列有**最小宽度**保底；窗口够宽（≥1280×800）按权重铺满，不够则**整表横向滚动**（底部横向滚动条），文字一律单行、不截断 |
| 3 | **1280×800**：交易表「0.00022336BNB」手续费**换行**；「交易对 + 估算中」占两行 → 行高参差 | 同 DEF-28 | **已修复**：「估算中」与交易对**同一行内联**；所有单元格单行；行高一致 |
| 4 | **1024×768**：上述更严重，更多列换行、卡片内容多行 | 同 DEF-28（窗口越窄越明显） | **已修复**：1024×768 下表格横向滚动、行高稳定；卡片内文字仍按既有换行规则（属正常流式排版） |
| 5 | **1024×768**：交易表右侧「删除」按钮显示不全、变成竖条（文字竖排） | **P2 缺陷 DEF-29**：操作列被压到 ~100dp | **已修复**：操作列最小 120dp、两枚按钮成对显示；1024 下需横向滚动到右侧（或直接看到滚动条提示） |
| 6 | **1024×768**：资金管理「近90天」单选框变竖条（文字竖排） | **P2 缺陷 DEF-29**：单行过滤 Row 把按钮等比压窄 | **已修复**：过滤按钮改**自动换行**（`FlowRow`），按钮保持自然宽度；`WzButton` 文案统一单行 |
| 7 | 行情页搜索框输入字母后，候选浮层**只有点「添加」才消失**；删光字母也不收起 | **P2 缺陷 DEF-30**：输入清空时**未取消在途搜索** → 旧结果返回又把候选写回（浮层被顶回）；且无 Esc 收起路径 | **已修复**：清空输入即收起；**Esc** 取消搜索并清空；点候选「添加」后收起；搜索失败有 toast 提示（不再静默） |

**三档分辨率复验清单一（每档都过一遍）**

| 分辨率 | 检查点 |
|--------|--------|
| 1280×800 | ① 进设置页焦点在顶部、页面未滚动；② 资产列表无竖排标签、行高一致；③ 交易表手续费/交易对单行；④ 资金/交易过滤按钮不竖排 |
| 1024×768 | ① 同上四项；② 两张表出现**横向滚动条**且列不换行；③ 交易表「编辑/删除」按钮可读（必要时横向滚动到右侧）；④ 卡片文字换行但不重叠、不裁切 |
| 窗口最大化 | 表格**无横向滚动条**（按权重铺满），观感与修复前一致（回归） |

> 复验前先核对日志首行 `build=0.1.0+191d873`（本轮修复提交）。

**本轮产物（CI run [34972057717](https://github.com/mapleafly/wuzhufolio/actions/runs/34972057717) 六 job 全绿，commit `191d873`）**：

| 产物 | SHA256 |
|------|--------|
| `msi\WuZhuFolio-0.1.0.msi` | `86ae80b10712bfe0ddd652812ae69337daa2d0d9f1482008f25aece1b2c887f0` |
| `exe\WuZhuFolio-0.1.0.exe` | `6f179dbc129df9b090d30b34d80d3da17812e3bf5954d34d0179a56ceeb829ad` |

```powershell
gh run download 34972057717 --repo mapleafly/wuzhufolio -n wuzhufolio-windows-latest-native -D .\wzf-windows
Get-FileHash .\wzf-windows\msi\WuZhuFolio-0.1.0.msi -Algorithm SHA256   # 应等于上表
msiexec /i .\wzf-windows\msi\WuZhuFolio-0.1.0.msi
```

---

## 14. 第七轮人工走查反馈与复验指引（Windows 11 · 2026-09-15 · GUI 全流程 × 三档分辨率）

> **本轮通过项** ✅：TC-MAN-03 读屏（NVDA）· TC-MAN-04 目标机性能。
> **本轮修复**：DEF-31…DEF-38（八条同源问题）——**总体方案见 `docs/design/responsive-components.md`**
> （断点 → 统一表格组件 → 统一卡片组件 → 弹窗尺寸策略 → 列表单元线）。分级待拍板（建议 C0×5 + C1×2）。

| # | 人工观察 | 核实结论 | 复验指引 |
|---|----------|----------|----------|
| 1 | 2560×1600 下资产列表币种列**第三枚标签显示不全** | **DEF-31/37**：宽窗模式按固定 `weight` 分配列宽，**不保证每列 ≥ 其最小宽**；2560×1600 在 Windows 200% 缩放下有效宽度 ≈1280dp，币种列拿不到三枚徽标所需宽度 | **已修复**：列宽**按最小宽比例**分配（每列必然 ≥ 最小宽）+ 币种列最小宽 260dp。复验：三枚标签（持仓异常/估算中/成本不可靠）**完整显示**，不竖排、不被裁 |
| 2 | 2560×1600 / 1024×768：交易表仍需横向滚动、**手续费换行**、滚到最右**删除按钮只有一半** | **DEF-33**：① 纵向滚动条画在最右缘压住最后一列；② 手续费等数字单元格漏加单行约束 | **已修复**：列表右侧预留 12dp 滚动条槽；全部单元格单行 + 截断悬停看全值；操作列最小 120dp。复验：滚到最右时「编辑/删除」**完整可见可点**；手续费单行 |
| 3 | 添加交易 / 记录增资弹窗**总有「不到一行」的滚动条** | **DEF-36**：弹窗内容高度上限写死（470/420dp），与窗口高度无关 | **已修复**：上限改为**窗口高 × 0.66**（下限 320dp）+ 表单内边距紧凑化。复验：1024×768 打开表单**无需滚动**即可看到「保存」；更小窗口才滚动（不裁切内容） |
| 4 | 1024×768：卡片大数字**换行变形**（净值/本金）；环形图图例**币种名换行** | **DEF-32/34**：数字无单行约束；图例名称被右侧金额压窄即折行 | **已修复**：卡片统一 `WzCard` + 指标数字 `WzMetric`（单行 + 自动缩字号，下限 0.68×）；图例改单行 + 悬停全值。复验：卡片数字单行、卡片高度一致；图例单行 |
| 5 | 1024×768：表格长字段被截断，**悬停看不到完整数据** | **DEF-35**：只有省略号，无查看全值途径 | **已修复**：截断时悬停显示完整值（仅在实际截断时出气泡）。复验：把窗口缩到出现省略号 → 鼠标悬停该单元格 → 应显示完整数值 |
| 6 | 「各页列表能否加**单元线**？」 | **DEF-38**：原列表无行分隔 | **已实现**：三张表 + 行情自选列表均有 1px 单元线（`line` 色、最低强调、双主题下均可见但不喧宾夺主） |

**三档分辨率复验清单一（本轮重点：2560×1600 与高 DPI）**

| 分辨率/缩放 | 检查点 |
|-------------|--------|
| 2560×1600 @100%（WIDE） | ① 币种列三枚标签完整；② 表格**无横向滚动条**；③ 卡片留白充裕 |
| 2560×1600 @150%/200%（等效 COMPACT/MEDIUM） | 同上两条（**这是人工上一轮报「宽屏仍有问题」的真实原因**：有效宽度被缩放吃掉）；确认无标签裁切、无换行 |
| 1280×800 | ① 表格无横向滚动；② 卡片数字单行；③ 弹窗无滚动条 |
| 1024×768 | ① 表格横向滚动但行高规整、删除按钮完整可点；② 卡片数字不换行；③ 交易/增资弹窗无需滚动即可保存；④ 截断单元格悬停可见全值；⑤ 列表单元线可见 |

> 复验前先核对日志首行 `build=0.1.0+<本轮短 sha>`。

**本轮产物（CI run [34980698283](https://github.com/mapleafly/wuzhufolio/actions/runs/34980698283) 六 job 全绿，commit `31d5d1f`）**：

| 产物 | SHA256 |
|------|--------|
| `msi\WuZhuFolio-0.1.0.msi` | `b6373ef019e8eafa700f3d0ea6aebf63333861aac32f68c460756916f06394c0` |
| `exe\WuZhuFolio-0.1.0.exe` | `4bd59aaa2ac2d02bd8973f797b578134b112a3e04a117d4358d9aa9783a3ab2a` |

```powershell
gh run download 34980698283 --repo mapleafly/wuzhufolio -n wuzhufolio-windows-latest-native -D .\wzf-windows
Get-FileHash .\wzf-windows\msi\WuZhuFolio-0.1.0.msi -Algorithm SHA256   # 应等于上表
msiexec /i .\wzf-windows\msi\WuZhuFolio-0.1.0.msi
```

> 复验前核对日志首行 `build=0.1.0+31d5d1f`。

---

## 15. 第八轮人工走查反馈与复验指引（Windows 11 · 2026-09-15 · GUI 全流程复验）

> **本轮通过项** ✅：**TC-MAN-05 断网态**。本轮修复：DEF-39/40/41（均建议 C0，待拍板）。

| # | 人工观察 | 核实结论 | 复验指引 |
|---|----------|----------|----------|
| 1 | 仪表盘第一行四个卡片的数字**明显比下面卡片大** | **DEF-39**：`StatCard` 两级误映射为 `display` 32sp vs `bodyStrong` 14sp（**2.3×**）；P1 原型基准是 `.card .big` **27px** / `.big.sm` **21px**（≈1.29×） | **已修复**：新增 `metricPrimary`(27sp)/`metricSecondary`(21sp) 并逐项对齐原型。复验：第一行与下面几行的数字**同一字体族、层级差约 1.3 倍**，不再有「跳两级」的观感 |
| 2 | 表格只有横线，**要加竖线** | **DEF-40**：统一表格组件此前只画行底线 | **已实现**：列边界 1px `line` 色纵线，**贯穿表头与数据行**；最后一列右边界不画（避免与表格边框成双线）。复验：三张表（资产/交易/资金）+ 币种详情成交表均为**完整网格**（横线 + 竖线），双主题下均可见且不喧宾夺主 |
| 3 | 币种资产详情页成交表**没有单元格线** | **DEF-41**：该表是另一套手写实现，未接入统一表格组件（因此也没有单行约束、截断悬停全值、窄窗横向滚动） | **已修复**：迁移到 `AdaptiveTable`。复验：详情页成交表**网格线齐全**；列序与文案不变；窄窗下可横向滚动、单元格截断时悬停看全值 |

**复验清单一**

| 页面 | 检查点 |
|------|--------|
| 仪表盘 | 四行卡片的数字字号层级一致（一级 27sp / 次级 21sp），无「第一行特别大」 |
| 资产列表 / 交易管理 / 资金管理 | 表格横线 + **竖线**齐全；表头与数据行列对齐 |
| 币种资产详情 | 成交表同为网格表；筛选/校准入口/时间档位功能不变 |

> 复验前核对日志首行 `build=0.1.0+<本轮短 sha>`。

**本轮产物（CI run [34987250801](https://github.com/mapleafly/wuzhufolio/actions/runs/34987250801) 六 job 全绿，commit `d365e27`）**：

| 产物 | SHA256 |
|------|--------|
| `msi\WuZhuFolio-0.1.0.msi` | `f832793bc1e4e8abf03f8564538407a9613debb36bbe6d9ce45412bac9d56998` |
| `exe\WuZhuFolio-0.1.0.exe` | `8e4e9b6e3fd6fdde8400f96ca4dfe1b160cde84f3d5d627cefad6e0fde9a957e` |

```powershell
gh run download 34987250801 --repo mapleafly/wuzhufolio -n wuzhufolio-windows-latest-native -D .\wzf-windows
Get-FileHash .\wzf-windows\msi\WuZhuFolio-0.1.0.msi -Algorithm SHA256   # 应等于上表
msiexec /i .\wzf-windows\msi\WuZhuFolio-0.1.0.msi
```

> 复验前核对日志首行 `build=0.1.0+d365e27`。

---

## 16. 排查附录：安装版启动报「Failed to launch JVM」（DEF-42）

> **先记住一句**：这个弹窗是 **jpackage 生成的 Windows 原生启动器**（安装目录里的 `WuZhuFolio.exe`）弹的，
> **不是 Java 异常、也不是应用代码的错**。它的含义是「启动器没能把**随包捆绑的私有运行时**拉起来」。
> 按 ADR-006 §1.1，安装版**自带 jlink 裁剪的私有 JRE**——**机器上装不装 Java、装的是 17 还是 21 都与此无关**。

### 16.1 四步快速判定（复制到 PowerShell 直接跑，约 1 分钟）

```powershell
# 安装目录（默认 per-user 安装落在用户目录下；若向导里改过，请替换为实际目录）
$d = "$env:LOCALAPPDATA\WuZhuFolio"; $d
# ① 路径是否含非 ASCII 字符（True = 命中「中文用户名」这一最常见原因）
[bool]($d.ToCharArray() | Where-Object { [int]$_ -gt 127 })
# ② 捆绑运行时是否完好（应打印 17.0.x；报错 = 运行时被安全软件删改/安装不完整）
& "$d\runtime\bin\java.exe" -version
# ③ 绕过快捷方式，直接跑主程序（看是否仍报错）
& "$d\WuZhuFolio.exe"
# ④ 复制到 ASCII 短路径再跑（这一条能跑通 = 确认是安装路径/启动器路径问题）
Copy-Item $d C:\WZF -Recurse -Force; & C:\WZF\WuZhuFolio.exe
# ⑤ 顺带看一眼应用日志：若出现新的 `bootstrap ok`，说明 JVM 其实起来了，问题在别处
Get-ChildItem "$env:USERPROFILE\.wuzhufolio\logs" | Sort-Object LastWriteTime -Descending |
  Select-Object -First 1 | Get-Content -Tail 20
```

### 16.2 一次跑完的取证脚本（推荐，输出直接贴回 Agent）

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\diagnose-packaged-launch.ps1
# 只要排查、不要启动探针：追加 -SkipLaunch
# 安装目录非默认：追加 -InstallDir "D:\WuZhuFolio"
```

脚本按候选根因逐项打印判定行与 `[findings]` 汇总：环境与代码页 / 安装记录（是否并存多份）/ 目录体检
（`app\*.cfg`、`runtime\bin\server\jvm.dll`、jar 数、MOTW）/ 快捷方式 target 是否存在 / 捆绑运行时自检 /
cfg 引用的 jar 是否齐全 / 应用日志 `bootstrap ok` / 安全软件拦截记录 / **启动探针**（临时数据目录实跑一次）。

### 16.3 判定表

| 观察 | 结论 | 处置 |
|------|------|------|
| ① 为 `True`，且 ④ 能跑通 | **安装路径含非 ASCII**（中文用户名 + per-user 安装默认路径） | 重装时在向导里把目录改成 `C:\WuZhuFolio`（`dirChooser` 已开启）；或直接用 ④ 的拷贝目录运行 |
| ② 报错（`java -version` 失败） | **捆绑运行时被破坏**（安全软件隔离/安装不完整） | 安装目录加白名单后重装；必要时核对安装包 SHA256 |
| ③ 报错但 ④ 能跑通 | 与路径相关（同上 ①） | 同 ① |
| ③④ 都报错，且 ⑤ 无新 `bootstrap ok` | 启动器确实拉不起 JVM | 跑 §16.2 脚本取全文，贴回 Agent |
| ⑤ 有**新的** `bootstrap ok`（且时间接近刚才） | **JVM 其实起来了**，问题不在启动器（可能是窗口/渲染） | 保留日志 + 报错弹窗全文，贴回 Agent |
| 安装记录里出现**多条** WuZhuFolio，或快捷方式 target `exists=False` | **陈旧/并存安装** | 控制面板卸载全部 WuZhuFolio → 重新安装最新产物 |

### 16.4 与「开机自启」的关系

自启注册的是**可执行文件的绝对路径**（`HKCU\...\Run` 下 `WuZhuFolio`），与安装目录位置无关。
因此用 §16.1 的 ④ 或重装到英文目录**只要能启动，TC-MAN-02 的步骤 3–5 就可以继续走完**
（开关 → `reg query` 有项 → 注销重登驻留 → 关开关后注册项消失）。

### 16.5 预防（已提级建议，待人工拍板）

- **C0（建议 · 已先行落地观察期）**：CI 增「打包版启动冒烟」——直接运行产出的 app-image（独立数据目录），
  断言应用日志出现 `bootstrap ok`。当前形态：**Windows 侧 + 产物上传之后 + 非阻断**（`PACKAGED_LAUNCH_SMOKE=PASS/FAIL` 可 grep），
  稳定数轮后可按人工拍板改为阻断式并扩展到 macOS/Linux（Linux 需 xvfb）。
  **本轮缺陷正是「产物从未被启动过就交付到人工门」的后果。**
- **C1（建议，决策档编号顺延 D34）**：① Windows 安装策略调整——`perUserInstall = false`（装到 `C:\Program Files\...`，
  需管理员确认）**或**保留 per-user、免管理员地显式指定 ASCII 安装目录（Compose DSL `installationPath` 已查证可用，
  映射 jpackage `--install-dir`，如 `C:\WuZhuFolio`）；② 增「便携版 zip」产物（解压即用，绕开安装器与用户目录路径）。
