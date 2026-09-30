#!/usr/bin/env bash
# WuZhuFolio 0.2.0 宣传动画 · 一键复现（P7）
#
# 与 0.1.0 那版渲染的差异：
#   ① 素材改为**真实应用 2× 渲染**（`ui` 模块的 VisualRegression.captureFilmAsset 采集），不再取自已退役原型；
#   ② 色板改为产品**真实 M3 token**（design-tokens.md，D38/D42）；
#   ③ ffmpeg 用静态构建（~/.local/bin/ffmpeg），不再需要 LD_LIBRARY_PATH 指向解包的系统库。
#
# 前置：
#   - node + playwright（chromium）：`npm i playwright && npx playwright install chromium`
#     （或用 skill 自带的 node_modules：NODE_PATH=.agents/skills/huashu-design/node_modules）
#   - ffmpeg：静态构建放 ~/.local/bin/ffmpeg
#
# 用法：bash docs/release/promo/tools/render-0.2.0.sh
set -euo pipefail
cd "$(dirname "$0")/../../../.."          # 仓库根
PROMO=docs/release/promo
export PATH="$HOME/.local/bin:$PATH"
: "${NODE_PATH:=/tmp/promo-tools/node_modules}"
export NODE_PATH

echo "== 0) 重建自包含动画源（素材 → base64）"
node "$PROMO/tools/build-promo.mjs"

echo "== 1) 起本地 HTTP（字体需经 HTTP 供给，file:// 会被跨源拦截）"
python3 -m http.server 8791 --bind 127.0.0.1 >/tmp/promo-http.log 2>&1 &
HTTP_PID=$!
trap 'kill $HTTP_PID 2>/dev/null || true' EXIT
sleep 1

echo "== 2) 自检（pageerror / 控制台错误 / 字体 / 豆腐块 / NaN）"
node "$PROMO/tools/render-promo-seek.mjs" "$PROMO/wuzhufolio-promo.html" \
  --duration=30 --fps=60 --width=1920 --height=1080 --check

echo "== 3) 逐帧 seek 渲染（1800 帧，约 15 分钟）"
node "$PROMO/tools/render-promo-seek.mjs" "$PROMO/wuzhufolio-promo.html" \
  --duration=30 --fps=60 --width=1920 --height=1080 --out=/tmp/promo-silent.mp4

echo "== 4) 混音（BGM + SFX 双轨；cue 表见 storyboard.md §5）"
# 音频素材来自 huashu-design skill 的 assets/；缺失时跳过混音并显式告警（不静默出无声片）
SKILL=.agents/skills/huashu-design/assets
if [ -f "$SKILL/bgm-educational.mp3" ]; then
  echo "   （cue 混音命令见 README.md「混音要点」；此处留作人工确认后执行）"
else
  echo "   ⚠️ 未找到 skill 音频素材（$SKILL/bgm-*.mp3 / sfx/）：混音步骤需先补素材，否则只能出无声片（不算成品）"
fi

echo "== 完成（画面）；混音与 GIF 派生见 README.md"
