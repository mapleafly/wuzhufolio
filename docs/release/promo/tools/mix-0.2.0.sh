#!/usr/bin/env bash
# WuZhuFolio 0.2.0 宣传动画 · 混音（BGM + SFX 双轨制）
#
# 输入：/tmp/promo-silent.mp4（render-promo-seek.mjs 出的纯画面片）
# 输出：docs/release/promo/wuzhufolio-promo-30s.mp4（成品，带音频）+ .gif
#
# cue 表：storyboard.md §5（12 组 / 14 次发声，时间与画面对齐，误差 ±1 帧）
# 配比：BGM 0.45（lowpass 4k）/ SFX 1.0（highpass 800）→ 频段隔离 + 响度差 −6.9 dB
set -euo pipefail
cd "$(dirname "$0")/../../../.."
export PATH="$HOME/.local/bin:$PATH"          # 静态 ffmpeg / ffprobe
SKILL=.agents/skills/huashu-design/assets
PROMO=docs/release/promo
SILENT=${1:-/tmp/promo-silent.mp4}
OUT="$PROMO/wuzhufolio-promo-30s.mp4"

[ -f "$SILENT" ] || { echo "✗ 缺少纯画面片：$SILENT"; exit 1; }
[ -f "$SKILL/bgm-educational.mp3" ] || { echo "✗ 缺少 BGM"; exit 1; }

# cue → SFX 文件 + 起始毫秒（含 storyboard 的 ±帧对齐修正，负值截到 0）
CUES=(
  "550  impact/drop-thud"        # ① 图版落页（后置 1 帧）
  "1350 impact/brand-stamp"      # ② 书名落定
  "5150 keyboard/type"           # ③ 追问第一行
  "5750 keyboard/type"           # ④ 追问第二行
  "7550 ui/click-soft"           # ⑤ 三条卖点（1/3）
  "7770 ui/click-soft"           # ⑤ （2/3）
  "7990 ui/click-soft"           # ⑤ （3/3）
  "11550 container/card-flip"    # ⑥ 翻页①
  "14300 container/card-flip"    # ⑦ 翻页②
  "17050 container/card-flip"    # ⑧ 翻页③
  "21733 feedback/success-chime" # ⑨ 安全四行点亮（后置 2 帧）
  "24600 ui/click"               # ⑩ 备份按钮按下
  "25483 keyboard/type-fast"     # ⑪ 白名单滑入（前置 1 帧）
  "27700 impact/logo-reveal-v2"  # ⑫ 品牌签名落定
)

ARGS=(-i "$SILENT" -i "$SKILL/bgm-educational.mp3")
FILTER="[1:a]volume=0.45,lowpass=f=4000,afade=t=in:st=0:d=0.3,afade=t=out:st=28.5:d=1.5[bgm]"
LABELS="[bgm]"
i=2
for cue in "${CUES[@]}"; do
  ms=${cue%% *}; rel=${cue##* }
  f="$SKILL/sfx/$rel.mp3"
  [ -f "$f" ] || { echo "✗ 缺少 SFX：$f"; exit 1; }
  ARGS+=(-i "$f")
  FILTER="$FILTER;[${i}:a]highpass=f=800,volume=1.0,adelay=${ms}|${ms}[s${i}]"
  LABELS="$LABELS[s${i}]"
  i=$((i+1))
done
FILTER="$FILTER;${LABELS}amix=inputs=$((i-1)):normalize=0,alimiter=limit=0.95,atrim=0:30[aout]"

echo "== 混音（$((i-1)) 路音频：1 BGM + $((i-2)) SFX；画面直通）"
ffmpeg -y -hide_banner -loglevel error "${ARGS[@]}" \
  -filter_complex "$FILTER" -map 0:v -map "[aout]" \
  -c:v copy -c:a aac -b:a 192k -ar 48000 -ac 2 -movflags +faststart "$OUT"

echo "== 校验音频流"
ffprobe -v error -select_streams a -show_entries stream=codec_name,channels,sample_rate -of csv=p=0 "$OUT"
ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT"
ls -la "$OUT"

echo "== 派生 GIF（560×315 · 12 fps · 96 色 palette）"
ffmpeg -y -hide_banner -loglevel error -i "$OUT" \
  -vf "fps=12,scale=560:-1:flags=lanczos,hqdn3d=1.5:1.5:6:6,palettegen=max_colors=96:stats_mode=diff" /tmp/pal.png
ffmpeg -y -hide_banner -loglevel error -i "$OUT" -i /tmp/pal.png \
  -lavfi "fps=12,scale=560:-1:flags=lanczos,hqdn3d=1.5:1.5:6:6[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle" \
  "$PROMO/wuzhufolio-promo-30s.gif"
ls -la "$PROMO/wuzhufolio-promo-30s.gif"
echo "✓ 完成"
