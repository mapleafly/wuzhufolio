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
| `wuzhufolio-promo-30s.mp4` | **成品（0.2.0 版）**：1920×1080 · 30.00 s · **60 fps** · H.264 · **AAC 48 kHz 立体声**（15.5 MB） |
| `wuzhufolio-promo-30s.gif` | 派生 GIF：560×315 · 12 fps · 96 色 palette 优化（6.3 MB） |
| `wuzhufolio-promo.html` | 动画源码（自包含单文件，真实 UI 截图 base64 内联） |
| `storyboard.md` | **分镜卡**（11 镜 · hero element · 能量骨架 · hold/rest 预算 · 镜头预算 · 12 项音频 cue · 逐帧复核清单） |
| `boards/direction-{a,b,c}.png` | 三方向方向板（A 中标；B/C 归档备用） |
| `assets/ui/*.png` | 影片用 4 张**整窗** UI 图（真实应用 2× 离屏渲染，2480×1640；`MainShell` + 页面槽采集） |
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
| 出片 | ✅ **已出片**：`wuzhufolio-promo-30s.mp4`（15.5 MB · 60 fps · AAC 立体声 · 30.00 s）+ `.gif`（6.3 MB）；抽帧验片通过（2.5 / 6.5 / 10 / 13.5 / 23 s） |

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
- **当前仓库状态**：✅ 影片产物与素材均为 **0.2.0 版**（真实 M3 界面 + M3 色板）。

## 设计约束（方向 A）

- **色板只用产品真实 design token**：纸底 `#EDE8DF` · 面 `#FFFFFF` · 墨 `#1E2A24` · 次级墨 `#5A635C` · 黄铜 `#B08A3E` · 盈 `#2E7D5B` · 亏 `#A8453F`。
- **字体**：正文 Noto Sans SC / Display Noto Serif SC / 数字 JetBrains Mono（取自 `ui/src/main/resources/fonts/`，OFL-1.1）。
- **画面主角是真实界面**：UI 一律取自 P1 唯一原型真源 `docs/design/prototype/wuzhufolio-light.html` 的 2× 截图，**不手画 UI**；数值与 PRD 附录 A 黄金用例一致。
- **一条连续的运动叙事**：hero element（那扇应用窗口）全程在场，段与段之间靠位移/缩放/翻页接续，**无整页 opacity 切换**。
- 动画产出带「Created by Huashu-Design」水印（技能默认）。

## 复现管线（三步，均在本机无 CJK 系统字体的前提下可用）

```bash
cd <repo>
export PATH=$HOME/.local/bin:$PATH        # 完整 ffmpeg（libx264 / gif / aac / mp3）
python3 -m http.server 8791 --bind 127.0.0.1 &   # 字体需经 HTTP 供给（file:// 会被跨源拦截）
export LD_LIBRARY_PATH=$HOME/.local/plibs/root/usr/lib/x86_64-linux-gnu

# 0) 采集 UI 素材（素材真源变更时重跑；产出 assets/ui/*.png）
node docs/release/promo/tools/capture-ui.mjs

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
