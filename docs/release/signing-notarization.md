# 签名与公证执行手册（signing-notarization.md）

> **阶段**：P7 发布 · **适用版本**：0.1.0 · **文档日期**：2026-09-22
> **依据**：`ADR-006 §2/§2.1`（签名/公证流水线与 Secrets 清单）、PRD §12（签名/公证为发布硬门槛）
> **原则（不可违反）**：**仓库与本地不含任何凭据**；凭据只经 GitHub Secrets 注入 CI。
> 未配置凭据时，CI 的签名/公证步骤 `if: env.X != ''` **自动跳过并产出未签名产物**（= 内部验收口径，
> **未签名产物不得作为正式发布件**）。

---

## 1. 总览

| 平台 | 需要的凭据 | 成本 / 周期 | CI 现状 | **最终状态（2026-09-22 拍板，见 §0）** |
|------|-----------|-------------|---------|--------------|
| macOS | Apple Developer Program + Developer ID Application 证书 | ≈ **$99/年**；实名核验 1–3 周 | ✅ 已实现（Compose DSL → `codesign`；公证 → `notarytool` + `stapler`） | **不采购、不发布 macOS 发行包**（用户自行编译；本手册留作将来复评时的执行步骤） |
| Windows | OV 代码签名证书（EV 需硬件令牌） | ≈ **$200–400/年**；实名核验 1–3 周 | ✅ 已实现（`signtool` + 时间戳，覆盖 msi/exe） | **不签名**（0.1.0 起为既定选择；用户按 `user-guide.md §10.9` 走 SmartScreen 放行） |
| Linux | 发布 GPG 密钥（签名 `.deb`/`.rpm`/AppImage/`SHA256SUMS`） | **0 元** | ⚠️ **待补 CI 步骤**（本手册 §4.4 给出可直接粘贴的片段）；**本地脚本已交付并实测**（`scripts/sign-linux-artifacts.sh`） | **保留为可选零成本增强**（尚未生成正式密钥；未签名 + `SHA256SUMS` 已满足当前发布口径） |

> **⚠️ 决策已更新（2026-09-22 人工拍板）**：Windows **不签名**、macOS **不提供 Release 二进制**、**预算 = 0 元**、不做 Microsoft Store ——
> 因此本手册 §2/§3 的 macOS/Windows 签名流程**当前不执行**，保留为将来复评（触发条件见 `certificate-procurement.md §0`）时的执行步骤。
> 唯一可能启用的是 **§4 Linux GPG 分离签名**（零成本，密钥可由项目自行生成）。
>
> 以下为**本手册编写时（决策前）**的原始决策点，保留作背景：
> **(a)** 先发未签名 0.1.0 并在发布说明中显式标注「未签名」，证书到位后随 0.1.1 起全面签名；
> **(b)** 等证书到位再发 0.1.0。Agent 当时建议 **(a)**（首个版本用户量小，且不内置自动更新，影响面可控）。**实际采纳 = 永久 (a)（不采购证书）**。

---

## 2. macOS：Developer ID 签名 + 公证

### 2.1 申请与准备

1. 加入 **Apple Developer Program**（组织账号需 D-U-N-S 号；个人账号需实名）。
2. 在 Certificates 里创建 **Developer ID Application** 证书，导出为 `.p12`（设强密码）。
3. 生成 **App 专用密码**（appleid.apple.com → 登录与安全 → App 专用密码），用于 `notarytool`。
4. 取 **Team ID**（developer.apple.com 会员详情页，10 位）。

### 2.2 注入 Secrets

```bash
base64 -i DeveloperID.p12 | pbcopy      # macOS；Linux: base64 -w0 DeveloperID.p12
gh secret set MACOS_CERT_P12          < <(base64 -w0 DeveloperID.p12)
gh secret set MACOS_CERT_PASSWORD     --body '<p12 密码>'
gh secret set MACOS_KEYCHAIN_PASSWORD --body '<CI 临时钥匙串密码，任意强随机>'
gh secret set MACOS_SIGNING_IDENTITY  --body 'Developer ID Application: <名称> (<TEAMID>)'
gh secret set MACOS_SIGNING_KEYCHAIN  --body '<CI 中创建的钥匙串名，如 wuzhufolio.keychain>'
gh secret set MACOS_NOTARIZATION_APPLEID   --body '<Apple ID 邮箱>'
gh secret set MACOS_NOTARIZATION_PASSWORD  --body '<App 专用密码>'
gh secret set MACOS_NOTARIZATION_TEAMID    --body '<Team ID>'
```

### 2.3 CI 行为（已实现，无需改代码）

`app/build.gradle.kts` 读取 Gradle 属性 `wuzhufolio.macos.*` → 回退环境变量 `WUZHUFOLIO_MACOS_*`（CI job-level env 由 Secrets 展开）：

- `signing { sign.set(identity != null); identity/keychain }` → Compose 插件调 `codesign`
- `notarization { appleID/password/teamID }` → 插件调 `notarytool submit --wait` + `stapler staple`

> **空串视为未配置**（M13 CI 实测：`${{ secrets.UNSET }}` 会展开为空字符串，按非 null 判断会误开签名并触发配置缓存错误）。

### 2.4 验证（发布前必做）

```bash
# 1) 签名有效性 + 加固运行时 + 证书链
codesign -dv --verbose=4 WuZhuFolio.app
codesign --verify --deep --strict --verbose=2 WuZhuFolio.app
#   期望：Authority=Developer ID Application: ... (TEAMID)；flags 含 runtime（ hardened runtime ）

# 2) 公证票据已钉装（Gatekeeper 离线可判）
xcrun stapler validate WuZhuFolio.app
spctl -a -vvv -t install WuZhuFolio.app      # 期望 accepted / source=Notarized Developer ID

# 3) DMG 本身
codesign --verify --verbose=2 WuZhuFolio-1.0.0.dmg
xcrun stapler validate WuZhuFolio-1.0.0.dmg

# 4) 公证日志（失败时唯一有效排查入口）
xcrun notarytool log <submission-id> --apple-id <id> --team-id <TEAMID> --password <app-专用密码>
```

**常见失败**：① 未用 hardened runtime（Compose 插件默认开启，勿手改）；② 内嵌 JRE 中的 `.dylib`/可执行文件未逐个签名 → 用 `--deep` 复核；③ 公证卡在 `Invalid` 多因缺 `com.apple.security.cs.allow-jit`（Skiko/JVM 需要）或时间戳服务不可达。

---

## 3. Windows：代码签名

### 3.1 申请与注入

- 购买 **OV 代码签名证书**（EV 需硬件令牌，CI 不适用；如需 EV 信誉请走 Azure Trusted Signing 或本地签名）。
- 证书以 `.pfx` 形式交付（含私钥），设强密码。

```bash
gh secret set WIN_CERT_P12      < <(base64 -w0 wuzhufolio-codesign.pfx)
gh secret set WIN_CERT_PASSWORD --body '<pfx 密码>'
```

### 3.2 CI 行为（已实现）

CI 在 jpackage 出包后调用 `signtool`（覆盖 `.msi` / `.exe`，含时间戳）：

```powershell
signtool sign /f $pfx /p $env:WIN_CERT_PASSWORD /fd sha256 `
  /tr http://timestamp.digicert.com /td sha256 $file.FullName
```

> **顺序要求**：**先签名、后打便携版 zip**——便携版包内的二进制必须已带签名（ADR-006 风险表）。

### 3.3 验证

```powershell
Get-AuthenticodeSignature .\WuZhuFolio-0.1.0.msi | Format-List Status, SignerCertificate, TimeStamperCertificate
#   期望 Status=Valid，且 TimeStamperCertificate 非空（有时间戳才能在证书到期后继续有效）
signtool verify /pa /v .\WuZhuFolio-0.1.0.msi
```

**SmartScreen 说明**：OV 证书需要累积下载信誉，**首发仍可能提示「未知发布者」**；发布说明中如实写明「更多信息 → 仍要运行」的路径，不要承诺「绝无提示」。

---

## 4. Linux：GPG 签名

### 4.1 生成发布密钥（离线保管主钥，CI 只用签名子钥）

```bash
# 在**离线/受控**机器上生成（建议 ed25519，主钥仅用于认证与吊销）
gpg --quick-gen-key "WuZhuFolio Releases <releases@wuzhufolio.invalid>" ed25519 cert 2y
gpg --quick-add-key <主钥指纹> ed25519 sign 1y           # 签名子钥（CI 用）
gpg --armor --export <主钥指纹> > wuzhufolio-release-pub.asc   # 公钥：随仓库/Release 分发

# 导出**签名子钥**私钥给 CI（不要导出主钥！）
gpg --armor --export-secret-subkeys <主钥指纹> > wuzhufolio-release-subkey.asc
```

> 主钥离线保存（纸质/硬件）；**一旦泄露走 §6 吊销流程**。公钥指纹须写进 `README`/用户指南，供用户核对。

### 4.2 本地签名（已交付并实测）

```bash
scripts/sign-linux-artifacts.sh <产物目录> --key <签名子钥指纹>
#   → 生成 SHA256SUMS + 每个产物的 .asc + SHA256SUMS.asc
WZF_GPG_KEY=<指纹> WZF_PASSPHRASE=<口令> scripts/sign-linux-artifacts.sh dist
scripts/sign-linux-artifacts.sh dist --verify        # 校验（含篡改检测）
```

**实测证据（2026-09-22，本机 Ubuntu 24.04）**：以一次性钥匙环生成 drill 密钥 → 对 3 个 Linux 产物签名 → `--verify` **全部通过**（`Good signature` + 三个产物 `OK` + 三个 `.asc` 有效）→ 篡改任一产物后校验**正确报 FAILED**。drill 密钥存放于临时目录并已销毁，**不构成官方签名**。

### 4.3 包内嵌签名（可选，CI 上执行）

```bash
apt-get install -y dpkg-sig rpm
dpkg-sig --sign builder -k <指纹> wuzhufolio_0.1.0-1_amd64.deb    # deb 内嵌
rpm --define "_gpg_name <指纹>" --addsign wuzhufolio-0.1.0-1.x86_64.rpm
debsig-verify wuzhufolio_0.1.0-1_amd64.deb                         # 验证
rpm --checksig wuzhufolio-0.1.0-1.x86_64.rpm
```

### 4.4 待补的 CI 步骤（可直接粘贴进 `.github/workflows/ci.yml` 的 package job，Linux 分支）

```yaml
      - name: Linux 产物 GPG 签名（P7；未配置凭据时自动跳过）
        if: matrix.os == 'ubuntu-latest' && env.LINUX_GPG_PRIVATE_KEY != ''
        env:
          LINUX_GPG_PRIVATE_KEY: ${{ secrets.LINUX_GPG_PRIVATE_KEY }}
          LINUX_GPG_PASSPHRASE: ${{ secrets.LINUX_GPG_PASSPHRASE }}
        run: |
          set -euo pipefail
          export GNUPGHOME="$(mktemp -d)"; chmod 700 "$GNUPGHOME"
          echo "$LINUX_GPG_PRIVATE_KEY" | base64 -d | gpg --batch --import
          KEYID=$(gpg --list-secret-keys --with-colons | awk -F: '/^fpr:/{print $10; exit}')
          scripts/sign-linux-artifacts.sh app/build/compose/binaries/main --key "$KEYID"
```

> 注入：`gh secret set LINUX_GPG_PRIVATE_KEY < <(base64 -w0 wuzhufolio-release-subkey.asc)`、`gh secret set LINUX_GPG_PASSPHRASE --body '<口令>'`。
> **注意**：`LINUX_GPG_PRIVATE_KEY` 为空时该步骤整体跳过 —— 与 macOS/Windows 的「未配置即跳过」口径一致。

### 4.5 用户侧校验（写进发布说明）

```bash
gpg --import wuzhufolio-release-pub.asc
gpg --verify SHA256SUMS.asc SHA256SUMS
sha256sum -c SHA256SUMS
```

---

## 5. 携带项 ⑤ 的评估结论（图标 / jlink 裁剪 / 字体子集化）

| 子项 | 结论 | 理由 |
|------|------|------|
| **应用图标 / 托盘图标** | ✅ **本轮定稿并产出正式资产**（随宣传动画方向确定后生成 PNG/ICO/ICNS；替换现「程序化绘制占位资产」） | 图标是用户可见的品牌资产，占位图形在正式发布件中不可接受 |
| **jlink 裁剪（进一步瘦身）** | ⏸ **建议 P8 评估，不在 0.1.0 发布前改动** | 模块集已于 M13 收敛（16 个模块，`includeAllModules=false`）并经 DEF-43 校准 + P6 全量验证；发布前改动 = **换一个未经 P6 验证的产物**，收益（数十 MB）与风险（启动期 `NoClassDefFoundError`）不成比例 |
| **字体子集化（pyftsubset）** | ❌ **建议不做** | 内嵌 Noto Sans SC / Noto Serif SC 共 ≈43 MB；但本产品**允许用户输入任意 CJK**（账户名、备注、CSV 导入的币种名/交易所名）。子集化必然导致生僻字缺字（豆腐块），是**功能正确性问题**而非体积问题。若未来要做，只可在「字符集封闭」前提下按 Unicode 基本区 + 常用字表做，并配套缺字回退方案 |

> 两项「维持现状」需人工在 `release-plan.md §9` 勾选确认。

---

## 6. 凭据轮换、泄露与撤销

| 场景 | 动作 |
|------|------|
| 例行轮换 | 新证书/新子钥 → 更新 Secrets → 重跑 package job → 用新公钥更新用户指南指纹；**旧签名产物不重签**（历史版本保留原签名） |
| 私钥泄露（GPG 子钥） | 立即用**离线主钥**吊销子钥并发布吊销证书：`gpg --gen-revoke` → 上传 keyserver + 写入 Release 公告；重发新子钥签名版本 |
| 私钥泄露（主钥） | 吊销整把密钥并废止该指纹；发布安全公告；历史产物的验签结论作废 |
| 证书泄露（macOS/Windows） | 在 Apple Developer / 证书颁发机构**吊销证书**；重发新证书签名版本；Windows 侧关注 SmartScreen 信誉重置 |
| CI Secret 误提交 | 立即在 GitHub 撤销并轮换该 Secret；检查 `gh api` 审计日志；按上表评估是否需要吊销凭据 |

**硬性要求**：`git grep -nE "BEGIN (RSA|EC|OPENSSH|PRIVATE) KEY|\.p12|\.pfx"` 必须无命中；CI 只读权限（`permissions: contents: read`）已是最小权限口径。

---

## 7. 合规核对表（PRD §12）

- [ ] macOS 产物：`codesign --verify --deep --strict` 通过 + `spctl -a -vvv` = accepted + `stapler validate` 通过。
- [ ] Windows 产物：`Get-AuthenticodeSignature` Status=Valid 且带时间戳。
- [ ] Linux 产物：`SHA256SUMS` + `.asc` 已随 Release 发布；公钥指纹在用户指南可查。
- [ ] Release 附件中**不含**调试产物（`WuZhuFolio-console-debug-*.zip` 仅限 CI 诊断，不上传 Release）。
- [ ] 发布说明写明签名状态与校验方法。
- [ ] 未签名产物**未**出现在正式 Release 中（若走「先发未签名 0.1.0」路径，必须在说明中显式标注）。

---

## 附录 · 需求回溯

| 本文件条目 | 需求锚点 |
|-----------|----------|
| macOS 公证 + stapler 钉装为发布硬门槛 | PRD §12、ADR-006 §2 |
| Windows 代码签名与 SmartScreen 信誉 | PRD §12、ADR-006 §2 风险表 |
| Linux 包签名与 GPG Secrets | ADR-006 §2.1（`LINUX_GPG_PRIVATE_KEY`） |
| 凭据只经 Secrets、仓库不含凭据 | ADR-006 §3、`AGENTS.md §1.1-3` |
| 未配置凭据 → 跳过并标注未签名 | ADR-006 §2.1 |
| 携带项 ⑤ 结论 | `STATUS.md` P7 携带项 ⑤、ADR-006 风险表（JRE 体积） |
| 图标资产 | M11 §5-1（占位资产待人工认可） |
