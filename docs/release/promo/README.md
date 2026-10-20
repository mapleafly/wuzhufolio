# WuZhuFolio 产品宣传动画（P7 必做项）

> **方向**：A · 账簿 The Ledger（人工拍板 2026-09-22，Gate 记录见 `direction-approved.md`）
> **本版迭代**：**0.2.0（2026-09-30）**——方向不变（属 `direction-approved.md` 的「已选定方向后的迭代」，不重过三方向门）：
> ① UI 素材改为**真实应用整窗 2× 渲染**（`MainShell` + 页面槽离屏渲染，2480×1640；不再取自已退役的 P1 原型）；
> ② 色板改为产品**真实 M3 token**（`design-tokens.md`，D38/D42）；③ 渲染器可移植性加固（系统 Chrome 回退 + 静态 ffmpeg）；
> ④ 新增一键脚本 `tools/render-0.2.0.sh` / `tools/mix-0.2.0.sh`。**分镜结构、hero、能量骨架、镜头预算全部沿用方向 A**。
> **规格**：1920×1080 · 30.0 s · 60 fps · H.264 + AAC（BGM + 14 个 SFX cue）
> **技能链**：`huashu-design` Step 9 + `storyboard-basics.md` + `camera-language.md` + `gsap-recipes.md §9` + `animation-pitfalls.md` + `audio-design-rules.md` + `sfx-library.md`

## 交付物

| 文件 | 说明 |
|------|------|
| `wuzhufolio-promo-30s.mp4` | **成品（0.3.0 版，2026-10-12 重出）**：1920×1080 · 30.00 s · **60 fps** · H.264 · **AAC 48 kHz 立体声**（15.36 MB） |
| `wuzhufolio-promo-30s.gif` | 派生 GIF：560×315 · 12 fps · 96 色 palette 优化（**6.70 MB**） |
| `wuzhufolio-promo-30s-0.4.0.mp4` | **成品（0.4.0 版，2026-10-20 重出）**：1920×1080 · 30.00 s · 60 fps · H.264 · AAC 48 kHz 立体声（**15.42 MB**）——差异见「0.4.0 重出记录」 |
| `wuzhufolio-promo-30s-0.4.0.gif` | 0.4.0 版派生 GIF：560×315 · 12 fps · 96 色（**6.72 MB**） |
| `wuzhufolio-promo.html` | 动画源码（自包含单文件，真实 UI 截图 base64 内联） |
| `storyboard.md` | **分镜卡**（11 镜 · hero element · 能量骨架 · hold/rest 预算 · 镜头预算 · 12 项音频 cue · 逐帧复核清单） |
| `boards/direction-{a,b,c}.png` | 三方向方向板（A 中标；B/C 归档备用） |
| `assets/ui/*.png` | 影片用**整窗** UI 图（真实应用 2× 离屏渲染，2480×1640；`MainShell` + 页面槽采集）。其中 4 张（`dashboard-light` / `portfolio-light` / `quotes-light` / `transactions-light`）由 `VisualRegression.captureFilmAsset` 生成 —— **写回受 B5⑤ 门控**（默认不写仓库，见下「素材写回门控」） |
| `tools/render-0.2.0.sh` · `tools/mix-0.2.0.sh` | **一键复现**：重建 → 自检 → 逐帧渲染；BGM + 14 SFX 混音 → MP4 + GIF |
| `assets/icon/*.png` | 应用图标评审预览（1024 / 16px×8 / 32px×8） |

## 0.2.0 迭代状态（2026-09-30）——✅ **已出片**

0.2.0 需要更新本片（旧片的界面是 M3 之前的 ✗）。**方向不变**（属 `direction-approved.md` 的「已选定方向后的迭代」，不重过三方向门）。
本轮已完成与已发现：

| 项 | 状态 |
|---|---|
| 色板 → 产品真实 M3 token（`design-tokens.md`：底 `#F5FBF6` / 强调 `#166B54` / 盈 `#1E6B45` / 亏 `#A63835`） | ✅ 已改（模板，随下轮重渲一并生效） |
| UI 素材 → 真实应用 2× 离屏渲染（`ui` 模块 `VisualRegression.captureFilmAsset`，2480×1640） | ⚠️ **几何不匹配，已回退** |
| 渲染器可移植性（skill 无 node_modules / 无 Playwright 自带 Chromium 时回退**系统 Chrome**；ffmpeg 用静态构建） | ✅ 已加固（`tools/render-promo-seek.mjs`） |
| 一键复现脚本 | ✅ `tools/render-0.2.0.sh`（重建→自检→渲染）+ `tools/mix-0.2.0.sh`（BGM+14 SFX→MP4+GIF） |
| 出片 | ✅ **已出片（2026-10-12，0.3.0 重出）**：`wuzhufolio-promo-30s.mp4`（15.36 MB · 60 fps · AAC 立体声 · 30.000 s）+ `.gif`（**6.70 MB**）；抽帧核验通过（S5 t=10.5 s 净值 / S7 t=15.5 s 已实现盈亏，与 `storyboard.md` 同步值一致） |

### 过程留痕：素材几何与模板合成不匹配（**2026-09-30 验片发现 → 已修正**）

- **现象**：用「单页渲染」素材重渲后，13.5 s 等帧出现**多图版叠加、文字互相压印**（对照 0.1.0 成片同帧：整窗截图、构图干净）。
- **根因**：模板的图版合成按 **整窗截图**（含侧边栏/顶栏的应用窗口，旧素材即如此）的几何设计；
  新素材只渲染了**页面本体**（无外壳）⇒ 内容铺满整幅 ⇒ 多图版叠加时文字互相压印。
- **正确修法**：素材改采**整窗**渲染——用 `MainShell(viewModel, dashboardPageContent=…, assetsPageContent=…, transactionsPageContent=…, watchPageContent=…)`
  在测试中装配外壳 + 真实页面（需为页面服务提供最小 fakes），2× 密度截图（1240×820 逻辑 → 2480×1640）；
  随后 `build-promo.mjs` → `render-0.2.0.sh` → `mix-0.2.0.sh` → **验片**（抽帧目视，`storyboard.md §7` 逐帧复核清单）。
- **修正结果**：改用 `MainShell` + 页面槽采**整窗**素材后，构图恢复正常（同一帧位对比 0.1.0 成片：构图风格一致）；
  期间还修掉两处：素材密度参数（`runSkikoComposeUiTest` 的 `size` 是**像素**，需 2480×1640 + density 2 才得 2× 图）、
  影片夹具（仪表盘指标来自交易重放，需 `metricsOverride` 填充，且避免异常币 ⚠️ 横幅出现在宣传片里）。
- **当前仓库状态**：✅ 影片产物与素材均为 **0.3.0 版**（真实 M3 界面 + M3 色板 + **产品示例数据集数值**；2026-10-12 重出）。

## 素材写回门控（B5⑤，2026-10-10 · 全面评审 F6）

**问题**：`ui` 模块的 `VisualRegression.captureFilmAsset` 原先**直接写仓库** `docs/release/promo/assets/ui/`，
于是**每次全量测试**都会重写其中 4 张 PNG（`dashboard-light` / `portfolio-light` / `quotes-light` /
`transactions-light`），且**跨平台渲染字节不同** ⇒ 工作树被污染、易被误提交，并使「发布素材 ↔ 发布影片」失去一一对应。

**口径（`D47 §2.5`）**：

| 场景 | 素材落点 |
|------|----------|
| **默认**（普通 `./gradlew test` / `:ui:test`） | `ui/build/promo-assets/`（**不写仓库**；用例照常渲染与断言，并打印一行提示） |
| **显式开启**（P7 出片链路） | `docs/release/promo/assets/ui/`（仓库内，供 `build-promo.mjs` 内联） |

开启方式（二选一，两者都支持）：
```bash
WUZHUFOLIO_FILM_ASSETS=1 ./gradlew :ui:test --tests "*film asset*" --console=plain
./gradlew :ui:test --tests "*film asset*" -Pwuzhufolio.filmAssets=true --console=plain
```
> Windows PowerShell 下 `-P…` 必须**加引号**（PowerShell 会把 `-Pfoo.bar=true` 拆成两个参数）：
> `.\gradlew.bat :ui:test --tests "*film asset*" "-Pwuzhufolio.filmAssets=true"`。

**出片链路已自动开启**：`tools/render-0.2.0.sh` 的**步骤 0** 会 `export WUZHUFOLIO_FILM_ASSETS=1` 后采集素材
（按本 README 执行出片流程即会更新 `assets/ui/`，不会读到旧素材）。

> 历史说明：0.1.0 版素材取自 P1 原型（工具 `tools/capture-ui.mjs`，**已随 D42 原型退役转为历史归档**）；
> 0.2.0 起素材真源 = 真实应用整窗 2× 离屏渲染。

## 素材数据来源（2026-10-11，开放项 #8）

4 张 UI 素材（`dashboard-light` / `portfolio-light` / `quotes-light` / `transactions-light`）的**数值真源**
已由「测试内手写的黄金数值夹具」改为**产品自身的示例数据集**——即影片里看到的界面 = 用户点「加载示例数据」
（B 迭代 **B1** · `D46`）后看到的界面。实现落在测试侧共享助手
`ui/src/test/kotlin/com/wuzhufolio/ui/regression/FilmDemoData.kt`：从 `DemoPortfolio` 出发经**既有 domain 引擎**
（`ReplayEngine` 全量重放 + `PortfolioCalculator`）推导指标 / 币种行 / 交易行 / 自选清单，
**素材内不手写任何金额**（唯一固化的表是币种**身份** ticker → cg_id，与 `DemoPortfolioTest` 同源；
新增示例币种时助手会 fail-fast 提示补表）。

| 口径 | 取值 |
|------|------|
| **示例价格** | **数据集自身最后成交价**（BTC 60,000 / ETH 3,000 / SOL 150；计价腿 USDT 按 `PortfolioCalculator.ANCHORED_COIN_IDS` 的 1:1 锚定 = 1.0）。**示例价格 = 数据集最后成交价，非行情数据，故影片画面不随行情变化**——素材因此可确定性重渲（不依赖网络、不随行情快照漂移） |
| 24h 盈亏 | 数据集不含 24h 前价 ⇒ 交引擎 `TwentyFourHour.compute` 空输入 ⇒ 覆盖 0/M、金额与百分比为 null（卡片显示 "--"）。**不臆造** 24h 前价（那等于宣称行情覆盖） |
| 行情页报价 | 自选清单 = 示例币种（默认种子 + D35「成交币自动入自选」两腿）；示例价没有行情快照行 ⇒ **数据源列「无行情」、更新列 "--"**（与运行期「无快照行即无来源」口径一致） |
| 交易行 | 数据集 4 笔（3 买 + 1 卖），行序 = 运行期列表默认的**交易时间降序**（首行 = BTC/USDT 卖出）；卖出行已实现盈亏由引擎逐笔推导（**+1,983.00**，口径同 M7 `SellRealizedTracer`，但由引擎产出） |
| 素材画面 | 仪表盘净值 109,952.50 / ROI +9.95% / 已实现 +1,983.00；资产列表顶部三卡不再为 `$0.00`（0.2.0 素材的已知缺陷，随本项修正） |

> **✅ 素材与影片已一并重生成（2026-10-12，0.3.0）**：`assets/ui/` 的 4 张 PNG、`wuzhufolio-promo.html`、
> `wuzhufolio-promo-30s.mp4` 与 `.gif` **同一会话生成**（满足 `D47`/`F6` 口径：发布素材 ↔ 发布影片一一对应）。
> 原约束（**不得单独替换**素材）**依然适用**：任何单独改动素材都必须连同影片一并重出（见下「Windows 原生出片链」）；
> `storyboard.md` 的 S5/S7 锚点数值已同步为 **`$109,952.50`** / **`+$1,983.00`**。
> 另：`storyboard.md` 的 S7 锚点仍写首行「BTC/USDT 卖出 **+$4,915.00**」（0.2.0 期的黄金数值口径），
> 0.3.0 出片时须按新素材数值（**+1,983.00**）同步；本次**不改** `storyboard.md`（它记录的是 0.2.0 期分镜）。

## 设计约束（方向 A）

- **色板只用产品真实 design token**：纸底 `#EDE8DF` · 面 `#FFFFFF` · 墨 `#1E2A24` · 次级墨 `#5A635C` · 黄铜 `#B08A3E` · 盈 `#2E7D5B` · 亏 `#A8453F`。
- **字体**：正文 Noto Sans SC / Display Noto Serif SC / 数字 JetBrains Mono（取自 `ui/src/main/resources/fonts/`，OFL-1.1）。
- **画面主角是真实界面**：UI 一律取自**真实应用整窗 2× 离屏渲染**（`VisualRegression.captureFilmAsset`，
  2480×1640；见上「素材写回门控」）——**不手画 UI**，数值由**产品自身的示例数据集**（`DemoPortfolio`）
  经既有引擎推导（见上「素材数据来源」，开放项 #8）。
  （0.1.0 版曾取自 P1 原型 `docs/design/prototype/wuzhufolio-light.html`；该原型已按 **D42** 退役，工具
  `tools/capture-ui.mjs` 转为历史归档。）
- **一条连续的运动叙事**：hero element（那扇应用窗口）全程在场，段与段之间靠位移/缩放/翻页接续，**无整页 opacity 切换**。
- 动画产出带「Created by Huashu-Design」水印（技能默认）。

## 复现管线（三步，均在本机无 CJK 系统字体的前提下可用）

```bash
cd <repo>
export PATH=$HOME/.local/bin:$PATH        # 完整 ffmpeg（libx264 / gif / aac / mp3）
python3 -m http.server 8791 --bind 127.0.0.1 &   # 字体需经 HTTP 供给（file:// 会被跨源拦截）
export LD_LIBRARY_PATH=$HOME/.local/plibs/root/usr/lib/x86_64-linux-gnu

# 0) 采集 UI 素材（0.2.0 起 = 真实应用整窗 2× 离屏渲染；**写回受 B5⑤ 门控**——此处必须开启）
WUZHUFOLIO_FILM_ASSETS=1 ./gradlew :ui:test --tests "*film asset*" --console=plain
#    默认（不开开关）素材落 ui/build/promo-assets/，仓库 assets/ui/ 不被改写；详见「素材写回门控」节
#    （一键脚本 tools/render-0.2.0.sh 的步骤 0 已包含本步，通常无需手工执行）

# 1) 由模板 + 素材构建自包含动画 HTML
node docs/release/promo/tools/build-promo.mjs

# 2) 逐帧 seek 渲染（确定性、真 60 fps、无黑帧；1800 帧 ≈ 15 分钟）
NODE_PATH=.agents/skills/huashu-design/node_modules \
  node docs/release/promo/tools/render-promo-seek.mjs \
  docs/release/promo/wuzhufolio-promo.html --duration=30 --fps=60 --width=1920 --height=1080
#    仅自检不渲染：追加 --check（输出 pageerror/console error/字体/豆腐块/NaN 结论）

# 3) 混音（BGM + 14 个 SFX cue，cue 表见 storyboard.md §5）→ 成品
#    见下方 ffmpeg 命令；随后派生 GIF
```

**混音要点**（双轨制，`audio-design-rules.md` 配方 A/C 之间）：BGM `bgm-educational.mp3`
`volume=0.45,lowpass=f=4000,afade in 0.3s,afade out st=28.5 d=1.5`；SFX 逐个 `highpass=f=800,volume=1.0,adelay=<ms>`
后 `amix=inputs=15:normalize=0`，末端 `alimiter=limit=0.95,atrim=0:30`；视频流 `-c:v copy` 不重编码。
**交付前必须**确认存在音频流（`ffmpeg -i <mp4>` 输出中应有 `Audio: aac`）。

**GIF 参数**：`fps=12,scale=560:-1:flags=lanczos,hqdn3d=1.5:1.5:6:6,palettegen=max_colors=96:stats_mode=diff`
→ `paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle`（本片文字密集，960 px/15 fps 会到 37 MB，故收敛到 560 px）。

---

## 0.3.0 出片（2026-10-12 · ✅ 已完成，Windows 原生链）

**结论**：**已出片**——`assets/ui/` 4 张素材、`wuzhufolio-promo.html`、`wuzhufolio-promo-30s.mp4`
（**15.36 MB** · 1920×1080 · 60 fps · H.264 + **AAC 48 kHz 立体声** · **30.000 s**）与
`wuzhufolio-promo-30s.gif`（**6.70 MB** · 560×315 · 12 fps · 96 色）**同会话一并重生成**
（满足 D47/F6「发布素材 ↔ 发布影片一一对应」）。过程中暴露并修复 **DEF-61**。

**本次已完成的实证（步骤 0）**：`WUZHUFOLIO_FILM_ASSETS=1` + 3 个测试类（`PortfolioPagesUiTest` /
`MarketWatchPageUiTest` / `TransactionsPageUiTest`）→ **恰好 4 张**素材被更新（其余 8 张逐字节不变，git 视角 4 文件）；
新素材**视觉核验通过**：仪表盘 **$109,952.50 / ROI +9.95% / 累计已实现 +$1,983.00**，资产分布
BTC 48,000 / USDT 40,952.50 / ETH 15,000 / SOL 6,000，且顶栏显示 **「账户 · Alex」**（D45 新功能本身已入画）。

### Windows 原生出片链（2026-10-12 实测可用 · C0 工装适配）

> 背景：本机 **WSL2 内 node / npm / ffmpeg 均已不在**（上次出片后环境未保留；`~/.cache/ms-playwright` 的
> `chromium-1234` / `ffmpeg-1011` 仍在但宿主二进制缺失）、**WSL 无免密 sudo**、**Docker 守护未运行** ⇒
> 改用 **Windows 原生链**（不改影片内容与叙事，仅把「浏览器解析 + 运行环境」跨平台化）。

**前置（一次性）**
1. `node`（实测 v22）+ `python3`（字体经 HTTP 供给）+ `ffmpeg`/`ffprobe` 在 PATH
   （本机用 BtbN 静态构建解包到 `%TEMP%\promo-tools\ffmpeg-master-latest-win64-gpl\bin`；下载需 `curl --ssl-no-revoke`，
   否则本机会因**吊销检查**失败）；
2. `npm i playwright-core`（装到**仓库外**如 `%TEMP%\promo-tools`，设 `NODE_PATH`）——
   **无需下载 Chromium**：脚本会回退到**系统 Chrome / Edge**（`findChromium()` 已跨平台化）。

**执行（六步，与 bash 版同构）**
```powershell
$env:NODE_PATH = "$env:TEMP\promo-tools\node_modules"      # playwright-core
$env:WUZHUFOLIO_FILM_ASSETS = '1'                          # 步骤 0 素材写回门控（DEF-61 起纳入任务输入）
.\gradlew.bat :ui:test --tests "*PortfolioPagesUiTest*" --tests "*MarketWatchPageUiTest*" --tests "*TransactionsPageUiTest*"
node docs/release/promo/tools/build-promo.mjs              # 步骤 1 素材 → base64 自包含 HTML
python -m http.server 8791 --bind 127.0.0.1                # 步骤 2 字体供给（仓库根启动）
node docs/release/promo/tools/render-promo-seek.mjs docs/release/promo/wuzhufolio-promo.html `
     --duration=30 --fps=60 --width=1920 --height=1080 --check     # 步骤 3 自检（pageerror/字体/豆腐块/NaN）
node docs/release/promo/tools/render-promo-seek.mjs docs/release/promo/wuzhufolio-promo.html `
     --duration=30 --fps=60 --width=1920 --height=1080 --out="$env:TEMP\promo-silent.mp4"   # 步骤 4 渲染
pwsh -File docs/release/promo/tools/mix-0.3.0.ps1 -Silent "$env:TEMP\promo-silent.mp4"      # 步骤 5+6 混音 + GIF
```
**实测**：`--check` → 三字体 loaded / 无豆腐块 / **pageerror 0**（唯一 console 错误 = `/favicon.ico` 404，无害）；
渲染 **1800/1800 帧 · 710 s**（`browser = C:/Program Files/Google/Chrome/Application/chrome.exe`）；
混音 15 路（1 BGM + 14 SFX）→ 成片含 `Audio: aac, 48000 Hz, 2 ch`、时长 **30.000 s**。

**两条已知约束（务必遵守）**
1. **素材与影片必须同会话生成**：D46 示例数据用 `clock()` 生成 **now 相对日期**，故 `transactions-light` 截图
   **随出片日期变化**（实测两次出片哈希不同）——**不得跨日混用素材与影片**；
2. **门控必须真跑**：`-Pwuzhufolio.filmAssets=true` 或环境变量 `WUZHUFOLIO_FILM_ASSETS=1` 均会触发写回，
   且 `DEF-61` 起门控值进任务输入、开启时禁用缓存（否则 `:ui:test` 命中缓存会静默不写回）。

> **Linux 分支**（原文档化管线）仍可用：`bash docs/release/promo/tools/render-0.3.0.sh`（含前置自检与修复命令）。

**已同步的数值**：`storyboard.md` 的 S5 净值锚点（`$120,464.77` → **`$109,952.50`**）与 S7 已实现盈亏
（`+$4,915.00` → **`+$1,983.00`**），以及 GIF 体积口径（6.3 MB → **6.70 MB**，见 `release-plan.md §7-⑦`）。

> ✅ **成片已按 DEF-62 修复后的界面重出（2026-10-12）**：交易表「已实现盈亏」正收益单元格此前显示 `++1,983.00`（双加号），
> 经人工裁决「C0 修 + 补用例 + 重出片」修复（`defects.md` **DEF-62**），**素材与影片同会话重生成**，
> 本目录内的 4 张素材 / HTML / MP4 / GIF 均为**修复后版本**。

---

## 0.4.0 重出记录（2026-10-20）

> **人工裁决（2026-10-20）**：0.4.0 **重出宣传片**（不复用 0.3.0 成片）；**0.3.0 成片原样保留**（`wuzhufolio-promo-30s.{mp4,gif}` 字节不变：sha256 `16466f96214695ce…` / `79c6bff6e4042533…`）。

| 项 | 值 |
|---|---|
| 产物 | `wuzhufolio-promo-30s-0.4.0.mp4`（**15.42 MB**）· `.gif`（**6.72 MB**） |
| 规格 | 1920×1080 · **60 fps** · **30.000 s** · H.264 + **AAC 48 kHz 立体声** · GIF 560×315 / 12 fps / 96 色 / 360 帧 |
| 渲染 | **1800/1800 帧 · 764 s**（4 worker）· **pageerror 0**（consoleError 1 = 已知 `/favicon.ico` 404） |
| 音轨 | **15 路**（1 BGM `bgm-educational` + 14 SFX；cue 表与配比**逐字沿用** `mix-0.3.0.ps1`） |
| 新工具（0.4.0 专用；0.3 链路未改） | `tools/promo-0.4.0.template.html` · `tools/build-promo-0.4.0.mjs` · `tools/mix-0.4.0.ps1` |
| 素材 | **复用现有** `assets/ui/*.png` 4 张（**未重新抓取**，避免跨会话日期漂移） |

**与 0.3.0 版的可辨识差异（3 处；均在既有镜头之上做「屏幕空间增量覆盖」，不改原时间轴）**

1. **版本标识**：页眉 `AGPL-3.0 · Windows / macOS / Linux · **v0.4.0**`（3.2 s 起）+ 片尾右下 `v0.4.0 · 通知投递统一 · macOS 加入`（24.9 s 起）+ `<title>`。
2. **通知投递统一卡**（17.2–19.6 s）：白卡「● NOTICE · 统一投递 / **同步完成** / 三平台一致的应用内提示；「请先登录」等门禁提示不再静默」——对应 `T16.1` / `DEF-65`。
3. **三平台可用卡**（21.2–23.8 s）：底部居中「**0.4.0 起 ｜ Windows ｜ Linux ｜ macOS ｜〔首次进发布〕**」——对应 `D51` / `T16.5`。

**踩坑留痕（供下次复用）**：① 覆盖元素必须挂在 **`#viewport` 的屏幕空间**——放在 `#world` 内会被 `#camera` 的镜头变换拉出视口/压扁（首版即因此完全不可见，探针实测 `box=[-379,-399,2344,221]`）；② 样式必须真正落在 `<style>` 内——首版 CSS 因锚点**缩进不匹配而静默未插入**（表现为 `box=[0,0,1920,96]`，即默认块级布局），"token 出现 3 次"这类弱检查抓不住；③ 验证用**探针读 `opacity/box/vis`** 比只看截帧可靠。