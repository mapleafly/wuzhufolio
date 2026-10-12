#!/usr/bin/env bash
# WuZhuFolio 0.3.0 宣传动画 · 一键复现（P7）
#
# 与 render-0.2.0.sh 的差异：
#   ① 版本号 / 产物命名 → 0.3.0；
#   ② 素材数值真源 = **产品自身示例数据集**（开放项 #8 / `FilmDemoData`）；素材（4 张）与影片
#      **必须一并重生成** —— 单独替换 `assets/ui/` 会破坏「发布素材 ↔ 发布影片一一对应」（D47 / F6 口径）；
#   ③ **新增步骤 -1 前置自检**：node / python3 / ffmpeg / playwright 缺一即**快速失败并打印修复命令**
#      （2026-10-12 实测：本机 WSL2 内 node 与 ffmpeg 已不在，见 `promo/README.md`「0.3.0 出片状态」）。
#
# 前置（WSL2 Ubuntu 24.04 实测路径）：
#   sudo apt-get install -y nodejs npm ffmpeg python3
#   cd .agents/skills/huashu-design && npm ci && npx playwright install chromium
#   （浏览器缓存若已在 ~/.cache/ms-playwright 且与 npm 包版本一致，install 会直接复用）
#
# 用法：bash docs/release/promo/tools/render-0.3.0.sh
set -euo pipefail
cd "$(dirname "$0")/../../../.."          # 仓库根
PROMO=docs/release/promo
export PATH="$HOME/.local/bin:$PATH"
: "${NODE_PATH:=/tmp/promo-tools/node_modules}"
export NODE_PATH

echo "== -1) 前置自检（缺一即中止，不产出半成品）"
missing=0
for c in node python3 ffmpeg; do
  if command -v "$c" >/dev/null 2>&1; then
    echo "   ✓ $c: $(command -v "$c")"
  else
    echo "   ✗ $c 缺失 → sudo apt-get install -y nodejs npm ffmpeg python3"
    missing=1
  fi
done
if [ ! -d "$NODE_PATH/playwright" ] && [ ! -d ".agents/skills/huashu-design/node_modules/playwright" ]; then
  echo "   ✗ playwright 未安装 → (cd .agents/skills/huashu-design && npm ci) 或 npm i playwright"
  missing=1
fi
if [ "$missing" -ne 0 ]; then
  echo "== 中止：出片前置不满足（见 README.md「0.3.0 出片状态」）"
  exit 2
fi

echo "== 0) 采集影片素材（真实应用整窗 2× 离屏渲染；数值真源 = 产品示例数据集）"
# B5⑤ / D47 §2.5：`VisualRegression.captureFilmAsset` 默认**不写仓库**（落 ui/build/promo-assets/），
# 仅显式开启才写回 docs/release/promo/assets/ui/ —— **出片链路必须开启**，否则 build-promo.mjs 读到旧素材。
export WUZHUFOLIO_FILM_ASSETS=1
./gradlew :ui:test --tests "*PortfolioPagesUiTest*" --tests "*MarketWatchPageUiTest*" \
  --tests "*TransactionsPageUiTest*" --console=plain
ls -l "$PROMO/assets/ui/"

echo "== 1) 重建自包含动画源（素材 → base64）"
node "$PROMO/tools/build-promo.mjs"

echo "== 2) 起本地 HTTP（字体需经 HTTP 供给，file:// 会被跨源拦截）"
python3 -m http.server 8791 --bind 127.0.0.1 >/tmp/promo-http.log 2>&1 &
HTTP_PID=$!
trap 'kill $HTTP_PID 2>/dev/null || true' EXIT
sleep 1

echo "== 3) 自检（pageerror / 控制台错误 / 字体 / 豆腐块 / NaN）"
node "$PROMO/tools/render-promo-seek.mjs" "$PROMO/wuzhufolio-promo.html" \
  --duration=30 --fps=60 --width=1920 --height=1080 --check

echo "== 4) 逐帧 seek 渲染（1800 帧，约 15 分钟）"
node "$PROMO/tools/render-promo-seek.mjs" "$PROMO/wuzhufolio-promo.html" \
  --duration=30 --fps=60 --width=1920 --height=1080 --out=/tmp/promo-silent.mp4

echo "== 5) 混音（BGM + SFX 双轨；cue 表见 storyboard.md §5）→ $PROMO/wuzhufolio-promo-30s.mp4"
# 音频素材来自 huashu-design skill 的 assets/；缺失时跳过混音并显式告警（不静默出无声片）
SKILL=.agents/skills/huashu-design/assets
if [ -f "$SKILL/bgm-educational.mp3" ]; then
  echo "   （cue 混音命令见 README.md「混音要点」；7 号交付前必须确认成片含 Audio: aac 流）"
else
  echo "   ⚠️ 未找到 skill 音频素材（$SKILL/bgm-*.mp3 / sfx/）：混音需先补素材，否则只能出无声片（不算成品）"
fi

echo "== 6) 派生 GIF（fps=12 · 560 px · 96 色；参数与命令见 README.md「GIF 参数」）"
echo "== 完成（画面）；混音与 GIF 派生见 README.md"
