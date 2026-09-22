# WuZhuFolio 发布计划（release-plan.md）

> **阶段**：P7 发布 · **版本**：0.1.0（首个公开发布）· **文档日期**：2026-09-22
> **输入**：`docs/test/test-report.md`（P6 结论：达到发布标准）+ `docs/release/`（本目录四份材料 + 宣传动画）
> **DoD 对照**（`AGENTS.md §4 P7`）：① 发布与回滚步骤可执行 ② 产物签名合规 ③ 用户文档与版本一致
> **状态**：Agent 已完成可执行部分并停人工门 —— **发布动作（打 tag / 建 Release / 上传产物）须人工批准后执行**。

---

## 1. 发布概览

| 项 | 值 |
|----|-----|
| 产品 | WuZhuFolio —— 本地优先、隐私与安全为核心的加密资产组合追踪桌面应用 |
| 版本 | **0.1.0**（单一真源 = `app/build.gradle.kts` 的 `val appVersion`） |
| 许可 | AGPL-3.0（`LICENSE`） |
| 发布渠道 | GitHub Releases（`https://github.com/mapleafly/wuzhufolio/releases`）；**不内置检查更新**（PRD §12） |
| 支持平台（**本轮发布范围**） | **Windows 10/11 x64** · **Linux x64**（deb / rpm / AppImage / 便携）—— **人工拍板（2026-09-22）：0.1.0 只发布 Windows 与 Linux 两个平台**（维护者可在本机实测验收）；**macOS 版本轮不发布**，待签名证书采购与真机实测就绪后随 0.1.x 提供 |
| 运行时分发 | jpackage 捆绑 jlink 裁剪私有 JRE，**用户无需安装 Java**（ADR-006 §1.1） |
| 质量基线 | P6：**718 用例（710 执行 0 失败 + 8 跳过）+ detekt 0 + 编译警告 0**；**P0/P1 缺陷 = 0**；安全清单五条硬约束逐条通过 |
| 有效需求 | **PRD V2.0 + Δ{D21, D24, D25, D26, D27, D28, D29, D30, D31, D32, D33, D34}**（`docs/dev/增量台账.md`） |

### 1.1 发布物清单（本阶段 P7 交付）

| 文件 | 作用 |
|------|------|
| `docs/release/release-plan.md` | 本文件：发布清单与步骤 |
| `docs/release/rollback.md` | 回滚方案（分级 + 触发条件 + 逐步操作） |
| `docs/release/CHANGELOG.md` | 0.1.0 变更日志（Keep a Changelog 1.1.0） |
| `docs/release/user-guide.md` | 用户使用说明（含隐私声明与已知限制） |
| `docs/release/signing-notarization.md` | 三平台签名/公证执行手册（凭据清单 + 逐条命令 + 验证） |
| `docs/release/certificate-procurement.md` | **证书采购决策材料**（Windows 五条路径对比 / 地区可用性判定 / 免费 OSS 方案 / 硬件令牌对 CI 的影响 / macOS 与 Linux 口径 / 待用户回答的问题） |
| `docs/release/promo/` | **产品宣传动画（已完成）**：`wuzhufolio-promo-30s.mp4`（成品，带 BGM + SFX）+ `.gif` + 源码 + 分镜卡 + 三方向方向板 + 真实 UI 素材 + 复现管线 README |

---

## 2. 版本号与产物命名口径

- **应用版本 = 0.1.0**：`.cpro` 备份头部 `app_version`、应用内「关于」页、诊断报告三处同源（构建期由 `BuildInfo` 注入），不得手改。
- **Git tag 命名 = `v0.1.0`**（带 `v` 前缀）；Release 标题 = `WuZhuFolio 0.1.0`。
- **⚠️ macOS 包版本差异（已知且不可对齐，需在发布说明中保留一句）**：Apple 规定 `CFBundleVersion` 首段不得为 0，故 `macOS { packageVersion = "1.0.0" }`（`app/build.gradle.kts`，本轮无法取 0.1.0）。表现为 **macOS 产物文件名与「关于本机」bundle 版本显示 1.0.0**，而应用内版本为 0.1.0。`1.0.0` 为 bundle 版本占位、**不代表功能成熟度**；待正式 1.0 发布时两者自然对齐。
- 各平台产物名（实测口径，供下载页核对）：

| 平台 | 产物 | 文件名（示例） |
|------|------|----------------|
| Windows | MSI（主） | `WuZhuFolio-0.1.0.msi` |
| Windows | EXE（备） | `WuZhuFolio-0.1.0.exe` |
| Windows | 便携版 | `WuZhuFolio-portable-windows-x64.zip` |
| macOS | ~~DMG / PKG / 便携版~~ | **⛔ 本轮不发布**（证书与真机实测就绪后随 0.1.x 提供；CI 仍会产出未签名 macOS 构件，但**不进 Release**） |
| Linux | DEB | `wuzhufolio_0.1.0-1_amd64.deb`（jpackage 小写包名） |
| Linux | RPM | `wuzhufolio-0.1.0-1.x86_64.rpm` |
| Linux | AppImage | `wuzhufolio-0.1.0-x86_64.AppImage` |
| Linux | 便携版 | `WuZhuFolio-portable-linux-x64.tar.gz` |
| 全平台 | 校验和 | `SHA256SUMS`（Release 附件） |

---

## 3. 发布前检查清单（Pre-release Gate）

### 3.1 代码与版本

- [ ] 工作区干净：`git status --porcelain` 为空；`main` 与远端一致。
- [ ] 版本号确认：`grep 'val appVersion' app/build.gradle.kts` → `0.1.0`；`docs/release/CHANGELOG.md` 主节为 `## [0.1.0]`。
- [ ] 有效需求串与 `docs/dev/增量台账.md` 表头一致（见 §1 表）。
- [ ] `docs/dev/STATUS.md` 的 P7 段为「待审核/已批准」，人工门记录在案。

### 3.2 质量与安全（P6 结论复用，改动即失效）

- [ ] `./gradlew clean build detekt --no-build-cache` → **718 用例（710 执行 0 失败 + 8 跳过）+ detekt 0 + 警告 0**。
      > ⚠️ 判定：**P6 之后任何代码改动都必须重跑本项**；纯文档改动不需要。
- [ ] 安全清单复跑：`./gradlew :data:test --tests "com.wuzhufolio.data.security.*"`（§1.1 五条硬约束守护）。
- [ ] 出站白名单实证（TC-MAN-09，本次 P7 已在本机 Ubuntu 实跑，证据见 §7.3）。
- [ ] 仓库无凭据：`git grep -nE "BEGIN (RSA|EC|OPENSSH|PRIVATE) KEY|\.p12|\.pfx"` 无命中；Secrets 仅存 GitHub。

### 3.3 签名/公证（合规硬门槛，PRD §12）

- [ ] **macOS：本轮不发布 → 本项不适用**。证书采购与公证流程见 `certificate-procurement.md §4` 与 `signing-notarization.md §2`，待启动 macOS 发布时执行。
- [x] **Windows：本轮不签名**（人工拍板 2026-09-22「先发未签名 0.1.0，证书到位后随 0.1.1 起签名」）→ 发布说明须显式标注「未签名」并给出 SmartScreen 放行说明；证书采购选项见 `certificate-procurement.md`。
- [x] **Linux：GPG 签名脚本已交付并实测**（`scripts/sign-linux-artifacts.sh`）；0.1.0 首次发布可先出未签名包 + SHA256SUMS，GPG 密钥生成后在 0.1.1 起对 Linux 产物签名。
- [ ] Windows：OV/EV 代码签名证书签名 msi/exe（含时间戳） → 见 §3。
- [ ] Linux：`.deb`/`.rpm` GPG 签名 + `SHA256SUMS` 的 GPG 分离签名 → 见 §4。
- [ ] **凭据未配置时 CI 自动跳过签名并产出未签名产物**（ADR-006 §2.1）—— 未签名产物**不得**作为正式发布件。

### 3.4 文档与素材

- [ ] `CHANGELOG.md` / `user-guide.md` 版本号与 `appVersion` 一致（0.1.0）。
- [ ] 用户指南含两条强制提示：**安装/解压路径请用纯英文**、**备份文件密码强度 = 凭证保护强度**。
- [ ] 隐私声明已写明：不收集任何数据；出站仅三白名单主机；行情请求仅发送币种标识、不含金额与交易数量。
- [ ] 产品宣传动画已出片（MP4/GIF）并归档 `docs/release/promo/`。
- [x] 应用图标为正式资产：`app/icons/wuzhufolio.{png,ico,icns}` 已接入三平台 `iconFile`（生成器 `scripts/generate-icons.mjs`，与托盘图标同一枚标记）；托盘图标由同一标记在运行期程序化绘制（设计如此，非占位）。

---

## 4. 构建步骤

### 4.1 本机（Linux）可复现构建 —— 本次已实测 ✅

```bash
export JAVA_HOME=$(mise where java)          # temurin-17（.mise.toml 锁定）

# 1) 全量校验（发布前必跑；纯文档改动可跳过）
./gradlew clean build detekt --console=plain

# 2) jpackage 原生格式（Linux：deb + rpm）+ app-image（便携版/AppImage 的母体）
./gradlew :app:packageDeb :app:packageRpm :app:createDistributable --console=plain
#    注：packageRpm 需要 rpmbuild（本机无、CI 已安装：ci.yml「安装 Linux 打包依赖」步骤）

# 3) AppImage（ADR-006 §1：appimagetool 包 app-image；工具自动下载到 ~/.cache/wuzhufolio）
scripts/package-appimage.sh

# 4) 便携版（解压即用；不写注册表、数据仍在 ~/.wuzhufolio）
tar -czf app/build/compose/binaries/main/portable/WuZhuFolio-portable-linux-x64.tar.gz \
  -C app/build/compose/binaries/main/app WuZhuFolio

# 5) 校验和清单
find app/build/compose/binaries/main \( -name '*.deb' -o -name '*.rpm' -o -name '*.AppImage' \
  -o -name '*-portable-*.tar.gz' \) -exec sha256sum {} \; | tee SHA256SUMS
```

**本机实测记录（2026-09-22，WSL2 Ubuntu 24.04 + temurin-17.0.20）**：`createDistributable` + `packageDeb` 构建成功（57 s）；`scripts/package-appimage.sh` 成功；便携版 tar.gz 生成成功；三者 SHA256 见 §7.2。

### 4.2 CI 三平台出包（正式发布件的唯一来源）

- 触发：推送 `main` 或 `workflow_dispatch`（`.github/workflows/ci.yml`）。
- `build` job（三平台矩阵）：`test` → 报告归档 → `detekt` → uber jar 冒烟 → 构件上传。
- `package` job（三平台矩阵）：安装打包依赖 → `packageDistributionForCurrentOS` → AppImage（Linux）→ 便携版（三平台）→ 签名/公证（凭据存在时）→ **上传原生产物 + `artifacts.txt`（含 SHA256）** → 打包产物启动冒烟（Windows 三路径 + 辅助技术；DEF-42/DEF-43 回归）。
- 产物保留 14 天；**正式发布须在 Release 附件中长期留存**（CI 构件会过期）。

### 4.3 凭据注入（GitHub Secrets，仓库与本地不含凭据）

| 平台 | Secrets |
|------|---------|
| macOS 签名 | `MACOS_CERT_P12` / `MACOS_CERT_PASSWORD` / `MACOS_KEYCHAIN_PASSWORD` / `MACOS_SIGNING_IDENTITY` / `MACOS_SIGNING_KEYCHAIN` |
| macOS 公证 | `MACOS_NOTARIZATION_APPLEID` / `MACOS_NOTARIZATION_PASSWORD` / `MACOS_NOTARIZATION_TEAMID` |
| Windows 签名 | `WIN_CERT_P12` / `WIN_CERT_PASSWORD` |
| Linux 包签名 | `LINUX_GPG_PRIVATE_KEY` / `LINUX_GPG_PASSPHRASE`（**待采购/生成，见 `signing-notarization.md §4`**） |

---

## 5. GitHub Release 发布步骤（人工批准后执行）

```bash
# 0) 前置：§3 清单全部勾选；CI package job 三平台全绿且产物已签名
git checkout main && git pull --ff-only
git status --porcelain                     # 必须为空

# 1) 打标签（附注标签，写明版本与依据）
git tag -a v0.1.0 -m "WuZhuFolio 0.1.0 —— 首个公开发布（P6 系统测试通过版）"
git push origin v0.1.0

# 2) 从 CI 产物生成校验和清单（artifacts.txt 即 CI 已产出；本地可复核）
#    ⚠️ 本轮发布范围 = Windows + Linux：**只列这两类产物**，macOS 构件一律不上传
sha256sum WuZhuFolio-0.1.0.msi WuZhuFolio-0.1.0.exe WuZhuFolio-portable-windows-x64.zip \
          wuzhufolio_0.1.0-1_amd64.deb wuzhufolio-0.1.0-1.x86_64.rpm \
          wuzhufolio-0.1.0-x86_64.AppImage WuZhuFolio-portable-linux-x64.tar.gz \
          > SHA256SUMS
# （GPG 私钥就绪后追加）gpg --armor --detach-sign SHA256SUMS

# 3) 建 Release（草稿 → 复核 → 发布）
gh release create v0.1.0 --title "WuZhuFolio 0.1.0" --draft \
  --notes-file docs/release/CHANGELOG.md \
  WuZhuFolio-0.1.0.msi WuZhuFolio-0.1.0.exe WuZhuFolio-portable-windows-x64.zip \
  wuzhufolio_0.1.0-1_amd64.deb wuzhufolio-0.1.0-1.x86_64.rpm \
  wuzhufolio-0.1.0-x86_64.AppImage WuZhuFolio-portable-linux-x64.tar.gz \
  SHA256SUMS
gh release view v0.1.0 --web                # 人工复核附件与说明后，点 Publish
```

**发布说明正文**至少包含：① 版本与日期 ② CHANGELOG 要点 ③ 各平台该下哪个文件 ④ **签名/公证状态**（已签名则注明证书主体；未签名则如实写明并给 SmartScreen / Gatekeeper 绕过说明）⑤ 两条强制提示（ASCII 路径、备份密码）⑥ 校验和核对方法 ⑦ 已知限制链接（用户指南 FAQ）。

---

## 6. 发布后验证（Post-release）

> **✅ 2026-09-22 已执行的验证（发布后即时）**：从 GitHub Release 公开页面下载 `SHA256SUMS` 与代表产物
> `wuzhufolio_0.1.0-1_amd64.deb` → `sha256sum -c --ignore-missing SHA256SUMS` → **OK**。
> Release 附件清单核对：**7 个产物 + `SHA256SUMS`，无 macOS 构件、无调试产物**。
> 发布地址：<https://github.com/mapleafly/wuzhufolio/releases/tag/v0.1.0>

- [ ] 从 Release 页面**真实下载**代表产物（Windows `msi` / Linux `deb` / Linux `AppImage` / Linux 便携版），逐个核对 `sha256sum -c SHA256SUMS`。
      ✅ 首项（Linux `deb`）已执行通过；其余待人工按需复核。
- [ ] **Linux 真机补测（人工拍板：发布后执行）**：从 GitHub Release 下载包 → 实测托盘菜单（GNOME 需 AppIndicator 扩展）与开机自启（`~/.config/autostart/*.desktop`），结果回填 `manual-test-guide.md` 与 STATUS。
- [ ] **Windows 安装包人工验收**：下载 `WuZhuFolio-0.1.0.msi` 走查安装 → 首启 → 核心旅程 → 卸载。
- [ ] 全新机器/干净账户安装并走查核心旅程：**创建账户 → 增资 → 记录交易 → 仪表盘与 ROI → 导出 .cpro → 恢复**（无阻断）。
- [ ] 冒烟：启动无「Failed to launch JVM」；日志只含脱敏信息；`~/.wuzhufolio` 权限 700、`master.key` 600。
- [ ] 官方渠道链接（关于页）可打开；Release 附件齐全且**不含**调试产物（console-debug zip 不得上传）。
- [ ] 记录下载量与首日问题（P8 复盘输入）。

---

## 7. P7 携带项处置（到期检查点 = P7 发布前）

> 来自 `STATUS.md`「P7 携带项」；本表为**逐项收口结论**。凡未在本机/CI 落地者，均已写明**去向**，不得当作已完成。

| # | 携带项 | 本次结论 | 证据 / 去向 |
|---|--------|----------|-------------|
| ① | **托盘走查**：Linux 实测（Windows 侧 2026-09-21 已 ✅） | **Linux 降级分支 ✅ 已实证**（WSLg 无托盘宿主 → `tray support \| supported=false`，应用正常驻留无异常）；**完整托盘菜单 = 发布后真机补测**（人工拍板 2026-09-22：从 GitHub Release 下载安装包在真机测试）；**macOS 本轮不发布**，不再列入 | §7.3 实测日志；`user-guide.md` FAQ 已写明降级行为；补测结果回填 `manual-test-guide.md` 与 STATUS |
| ② | **TC-MAN-09 出站抓包 + 权限实证（Ubuntu）** | ✅ **已执行并闭环**：① **GUI 运行期**（便携版，未登录会话）出站**仅 `api.coingecko.com` 一个主机**；② **live smoke 全链路**（行情主源 + CMC 兜底 + Binance 同步）出站**恰好三主机**：`api.coingecko.com` / `pro-api.coinmarketcap.com` / `api.binance.com`，**无第四个主机**；③ 数据目录权限 **700**、`master.key` 与 `wuzhufolio.db` **600** | release-plan 附录 A.4（本次 P7 实跑，脚本 `scripts/outbound-capture-proxy.py`） |
| ③ | 便携包与打包版开机自启实测 | **Linux 便携包解压即用 ✅ 已实证**（解压到纯 ASCII 路径 → 启动、真实网络刷新、驻留 60 s）；**Linux 打包版开机自启 = 发布后真机补测**（同①口径）；**macOS 本轮不发布** | §7.3；补测结果回填 `manual-test-guide.md` 与 STATUS |
| ④ | 签名/公证合规实证 + Linux 包 GPG 签名 + 证书采购 | **Linux 包 GPG 签名脚本已交付并实测**（`scripts/sign-linux-artifacts.sh`：签名 → 校验 → 篡改检测全通）；**Windows 证书采购 = 待人工决策**（选项/成本/地区可用性见 **`certificate-procurement.md`**）；**macOS 证书随 macOS 发布一并决策** | **人工拍板（2026-09-22）：先发未签名 0.1.0（Windows + Linux），证书到位后随 0.1.1 起签名** |
| ⑤ | 托盘正式图标与打包图标、jlink 裁剪、字体子集化 | **图标 ✅ 已定稿并接入打包**：`scripts/generate-icons.mjs` 由**与托盘图标同源的几何**（accent `#1F5A48` + 纸色 `#F6F4EF` 折线，design-tokens §2.1）生成 `app/icons/wuzhufolio.{png,ico,icns}`，已接入 jpackage 三平台 `iconFile`；**jlink 裁剪**：建议 **P8 评估**；**字体子集化**：**建议不做**（用户可输入任意 CJK，子集化会造成缺字） | 本表 + `signing-notarization.md §5`；**待人工批准**「⑤ 的两项优化维持现状」 |
| ⑥ | 发布产物 SHA256 / CHANGELOG / 用户指南 | ✅ CHANGELOG 与用户指南已交付；SHA256 清单模板与命令见 §5；**正式 SHA256 由 CI 产物生成** | 本目录四份文档 |
| ⑦ | **产品宣传动画（P7 必做项）** | ✅ **已出片**：方向 = **A · 账簿 The Ledger**（2026-09-22 人工拍板，Gate 见 `promo/direction-approved.md`）；`storyboard.md`（11 镜分镜卡）→ `wuzhufolio-promo.html` → 逐帧 seek 渲染 1800 帧 → BGM + 14 SFX cue → **`wuzhufolio-promo-30s.mp4`**（1920×1080 · 30.00 s · 60 fps · H.264 + AAC 立体声）+ **`wuzhufolio-promo-30s.gif`**（560×315 · 12.5 fps · 7.0 MB） | `docs/release/promo/`（含 `README.md` 复现管线） |

---

## 8. 已知限制（发布说明与用户指南须一致表述）

1. **jpackage 非 ASCII 路径限制（上游）**：安装/解压路径含中文等 ANSI 代码页无法表示的字符时启动器无法加载 `jvm.dll`，表现为无细节的 `Failed to launch JVM`。缓解：Windows 默认装到 `C:\Program Files\WuZhuFolio` 且不可改目录；无管理员权限/需自定义位置者用便携版。
2. **Windows 安装为 per-machine**：需要管理员确认（UAC）。
3. **备份密码不可找回**：本地无后门；忘记密码只能靠记得密码的旧备份恢复。
4. **无内置自动更新**（设计如此）：需手动下载新版。
5. **CMC 额度账本**：多账户/多 Key 的额度治理为已知待改进项（P8）。
6. **`.cpro` 为非流式读写**：超大备份包（≈10× 典型数据量）内存占用升高（P6 实测：典型/重度 ≤483 MiB 通过）。
7. **读屏**：无障碍为基线覆盖（Windows 侧已完成走查 TC-MAN-03 ✅），未做全平台全读屏软件矩阵。
8. **Linux tray**：无 AppIndicator 宿主（如未装扩展的 GNOME）时托盘不可用，应用按降级分支运行（不崩溃）。

---

## 9. 决策与批准（人工门）

> **人工门结论（2026-09-22）：✅ 已批准发布**。原话要点：「批准发布：**只发布 windows 版和 ubuntu 类适用的版本**（这两个我可以本机测试，其他暂时还不能测试发布版本），其他按照建议进行。」

| 项 | Agent 建议 | 人工裁决 |
|----|-----------|----------|
| **发布范围** | 三平台 | ✅ **已拍板（2026-09-22）**：**只发 Windows + Linux**（维护者可本机实测）；**macOS 本轮不发布** → CI 仍产出 macOS 构件但不进 Release，`CHANGELOG` / `user-guide` 已按此口径改写 |
| 发布号 = 0.1.0 | 采纳 | ✅ **已拍板**：维持 **0.1.0**；macOS bundle 版本差异（1.0.0）本轮不涉及（不发布 macOS） |
| 携带项 ⑤：jlink 裁剪与字体子集化**维持现状** | 采纳（理由见 §7-⑤） | ✅ **已拍板：按建议**（图标部分已执行：`app/icons/` 三平台资产已接入打包；jlink 裁剪 → P8 评估；字体子集化 → 不做） |
| 携带项 ①③ 的 Linux 托盘与开机自启补测 | 建议真机执行 | ✅ **已拍板：放在发布后** —— **从 GitHub Release 下载安装包在真机测试**，结果回填 `manual-test-guide.md` 与 STATUS |
| **签名路径** | 建议「先发未签名 0.1.0」 | ✅ **已拍板（2026-09-22）**：**先发未签名 0.1.0（Windows + Linux），证书到位后随 0.1.1 起签名** → 发布说明必须显式标注「未签名」并给出 SmartScreen 放行说明；Linux 侧可即刻用 `scripts/sign-linux-artifacts.sh` 签名 |
| **Windows 证书采购** | 提供选项与成本对比 | ⏳ **待用户决策** → 见 **`certificate-procurement.md`**（含地区可用性判定、免费 OSS 方案、硬件令牌对 CI 的影响、推荐路径） |
| **产品宣传动画方向** | 三方向板 A/B/C 供选 | ✅ **已拍板（2026-09-22）**：**A · 账簿 The Ledger**（记录见 `promo/direction-approved.md §3`）；**成片已交付** |
| **批准发布 0.1.0** | —— | ✅ **已批准（2026-09-22）** → 执行：提交 → 推送触发 CI → 取 Windows/Linux 产物 → 打 `v0.1.0` → 建 Release |

---

## 附录 A · 本次 Agent 实测证据（2026-09-22，WSL2 Ubuntu 24.04）

### A.1 构建

```
> Task :app:packageDeb
The distribution is written to .../main/deb/wuzhufolio_0.1.0-1_amd64.deb
BUILD SUCCESSFUL in 57s
scripts/package-appimage.sh → app/build/compose/binaries/main/appimage/wuzhufolio-0.1.0-x86_64.AppImage
便携版 → app/build/compose/binaries/main/portable/WuZhuFolio-portable-linux-x64.tar.gz
```

### A.2 产物体积与 SHA256（本机验证构建，正式件以 CI 为准）

> **本轮为接入正式应用图标后的重构建**（P7 携带项 ⑤：`app/icons/wuzhufolio.{png,ico,icns}` 接入 jpackage 三平台 `iconFile`）；
> 重构建后 `./gradlew build detekt` 绿（代码零改动，`test` 任务因输入未变 UP-TO-DATE → P6 的 718 用例结论继续有效）。

| 产物 | 大小 | SHA256 |
|------|------|--------|
| `wuzhufolio_0.1.0-1_amd64.deb` | 122.7 MiB | `8390c278f8116b4f737395ed40ff718f6bc45ab499094d109c1ef3bceae76136` |
| `wuzhufolio-0.1.0-x86_64.AppImage` | 132.7 MiB | `4cd4a567f0d1a21655c559c578f4b640b5f7a2cb6968f264e5608f8931386223` |
| `WuZhuFolio-portable-linux-x64.tar.gz` | 137.4 MiB | `2d7643da9c677608087c25ef17a916b674fe66eb31eed00ecd861cde65fdb0b1` |

> app-image 目录展开约 228 MB（含 43 MB 内嵌 CJK 字体 + jlink 私有运行时）。
> 图标接入实证：`app/build/compose/binaries/main/app/WuZhuFolio/lib/WuZhuFolio.png`（12153 B）与 `.deb` 内 `./opt/wuzhufolio/lib/WuZhuFolio.png` 均为正式图标。

### A.3 Linux 便携版解压即用 + 托盘降级 + 实时行情（实跑）

```
tar -xzf WuZhuFolio-portable-linux-x64.tar.gz -C /tmp/wz-portable
timeout 60 /tmp/wz-portable/WuZhuFolio/bin/WuZhuFolio          # exit=124（窗口驻留至超时 = 未崩溃）
日志：tray support | supported=false | minimizeOnClose=true     # 无托盘宿主 → 降级分支
      coin directory refreshed added=1638 updated=45
      market refresh finished source=COINGECKO coins=11 error=null
```

### A.4 出站抓包实证（TC-MAN-09，本次 P7 已闭环）

```bash
# ① GUI 运行期（便携版，未登录 → 只触发行情链路）
python3 scripts/outbound-capture-proxy.py --port 8899 --log /tmp/wzf-p7-outbound.log &
timeout 75 <便携版>/bin/WuZhuFolio \
  -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=8899 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=8899

# ② 全链路（行情主源 + CMC 兜底 + Binance 同步）
WZF_LIVE_SMOKE=1 https_proxy=http://127.0.0.1:8899 http_proxy=http://127.0.0.1:8899 \
  ./gradlew --no-daemon :data:test --tests "com.wuzhufolio.data.smoke.*" --rerun-tasks
```

**实测结果（2026-09-22）**：

| 场景 | 出站主机（去重） | 结论 |
|------|------------------|------|
| GUI 运行期（未登录、无交易所 Key） | `api.coingecko.com` | 无第四主机 |
| live smoke（三链路全触发） | `api.coingecko.com` · `pro-api.coinmarketcap.com` · `api.binance.com` | **恰好 = 白名单**，无第四主机；`BUILD SUCCESSFUL` |

**文件权限实证**：`700 ~/.wuzhufolio` · `700 ~/.wuzhufolio/logs` · `600 ~/.wuzhufolio/master.key` · `600 ~/.wuzhufolio/wuzhufolio.db` —— 与 `security-checklist.md §1.2` 判据一致。

---

## 附录 B · 需求回溯

| 本文件条目 | 需求锚点 |
|-----------|----------|
| 发布渠道 / 无自动更新 / 三平台签名公证 | PRD §12 |
| 产物矩阵（msi/dmg/deb/rpm/AppImage/便携版） | ADR-006 §1/§1.1/§1.2、D34 |
| 凭据只经 Secrets、仓库不含凭据 | ADR-006 §2.1、`AGENTS.md §1.1-3` |
| 用户指南两条强制提示 | DEF-42/DEF-43、P6 人工裁决 ⑬ |
| 隐私声明（不收集数据、三白名单、仅发送币种标识） | PRD §1.1-1/1.1-2、P6 人工裁决 ① |
| 已知限制 1/2 | DEF-42、D34 |
| 携带项 ①②③④⑤⑥⑦ | `STATUS.md`「P7 携带项」、`AGENTS.md §7.2-3` |
