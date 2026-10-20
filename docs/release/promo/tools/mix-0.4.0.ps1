# WuZhuFolio 0.4.0 宣传动画 · 混音 + GIF 派生（Windows 原生版）
#
# 与 `mix-0.2.0.sh` **同一 cue 表与同一配比**（cue 表 = `storyboard.md §5`：12 组 / 14 次发声，误差 ±1 帧；
# 配比 = BGM 0.45 + lowpass 4k / SFX 1.0 + highpass 800，频段隔离 + 响度差 −6.9 dB；画面 `-c:v copy` 直通）。
# 本脚本是**跨平台出片链的 Windows 分支**（C0，2026-10-12）——Linux 分支仍用 `mix-0.2.0.sh`（bash）。
#
# 前置：`ffmpeg` / `ffprobe` 在 PATH（本机用法见 `promo/README.md`「Windows 原生出片链」）。
#
# 用法：
#   pwsh -File docs/release/promo/tools/mix-0.3.0.ps1 -Silent "$env:TEMP\promo-silent.mp4"
param(
    [string]$Silent = (Join-Path $env:TEMP 'promo-silent.mp4'),
    [string]$Ffmpeg = 'ffmpeg',
    [string]$Ffprobe = 'ffprobe'
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$skill = Join-Path $repo '.agents\skills\huashu-design\assets'
$promo = Join-Path $repo 'docs\release\promo'
$out = Join-Path $promo 'wuzhufolio-promo-30s-0.4.0.mp4'
$gif = Join-Path $promo 'wuzhufolio-promo-30s-0.4.0.gif'

# cue → SFX 相对路径 + 起始毫秒（值逐字对齐 mix-0.2.0.sh，含 storyboard 的 ±帧对齐修正）
$cues = @(
    @{ ms = 550;   rel = 'impact/drop-thud' },        # ① 图版落页（后置 1 帧）
    @{ ms = 1350;  rel = 'impact/brand-stamp' },      # ② 书名落定
    @{ ms = 5150;  rel = 'keyboard/type' },           # ③ 追问第一行
    @{ ms = 5750;  rel = 'keyboard/type' },           # ④ 追问第二行
    @{ ms = 7550;  rel = 'ui/click-soft' },           # ⑤ 三条卖点（1/3）
    @{ ms = 7770;  rel = 'ui/click-soft' },           # ⑤ （2/3）
    @{ ms = 7990;  rel = 'ui/click-soft' },           # ⑤ （3/3）
    @{ ms = 11550; rel = 'container/card-flip' },     # ⑥ 翻页①
    @{ ms = 14300; rel = 'container/card-flip' },     # ⑦ 翻页②
    @{ ms = 17050; rel = 'container/card-flip' },     # ⑧ 翻页③
    @{ ms = 21733; rel = 'feedback/success-chime' },  # ⑨ 安全四行点亮（后置 2 帧）
    @{ ms = 24600; rel = 'ui/click' },                # ⑩ 备份按钮按下
    @{ ms = 25483; rel = 'keyboard/type-fast' },      # ⑪ 白名单滑入（前置 1 帧）
    @{ ms = 27700; rel = 'impact/logo-reveal-v2' }    # ⑫ 品牌签名落定
)

if (-not (Test-Path $Silent)) { throw "✗ 缺少纯画面片：$Silent" }
$bgm = Join-Path $skill 'bgm-educational.mp3'
if (-not (Test-Path $bgm)) { throw "✗ 缺少 BGM：$bgm" }

$ffArgs = @('-y', '-hide_banner', '-loglevel', 'error', '-i', $Silent, '-i', $bgm)
$filter = '[1:a]volume=0.45,lowpass=f=4000,afade=t=in:st=0:d=0.3,afade=t=out:st=28.5:d=1.5[bgm]'
$labels = '[bgm]'
$i = 2
foreach ($cue in $cues) {
    $f = Join-Path $skill ('sfx/' + $cue.rel + '.mp3')
    if (-not (Test-Path $f)) { throw "✗ 缺少 SFX：$f" }
    $ffArgs += @('-i', $f)
    $filter += ";[$i`:a]highpass=f=800,volume=1.0,adelay=$($cue.ms)|$($cue.ms)[s$i]"
    $labels += "[s$i]"
    $i += 1
}
$filter += ";$labels" + "amix=inputs=$($i - 1):normalize=0,alimiter=limit=0.95,atrim=0:30[aout]"

Write-Host "== 混音（$($i - 1) 路音频：1 BGM + $($i - 2) SFX；画面直通）"
& $Ffmpeg @ffArgs -filter_complex $filter -map 0:v -map '[aout]' `
    -c:v copy -c:a aac -b:a 192k -ar 48000 -ac 2 -movflags +faststart $out
if ($LASTEXITCODE -ne 0) { throw "✗ ffmpeg 混音失败（exit $LASTEXITCODE）" }

Write-Host '== 校验音频流（必须存在 Audio: aac）'
& $Ffprobe -v error -select_streams a -show_entries stream=codec_name,channels,sample_rate -of csv=p=0 $out
& $Ffprobe -v error -show_entries format=duration -of csv=p=0 $out
'{0:N1} MB  {1}' -f ((Get-Item $out).Length / 1MB), $out

Write-Host '== 派生 GIF（560×315 · 12 fps · 96 色 palette）'
$pal = Join-Path $env:TEMP 'promo-pal.png'
& $Ffmpeg -y -hide_banner -loglevel error -i $out `
    -vf 'fps=12,scale=560:-1:flags=lanczos,hqdn3d=1.5:1.5:6:6,palettegen=max_colors=96:stats_mode=diff' $pal
if ($LASTEXITCODE -ne 0) { throw "✗ palettegen 失败" }
& $Ffmpeg -y -hide_banner -loglevel error -i $out -i $pal `
    -lavfi 'fps=12,scale=560:-1:flags=lanczos,hqdn3d=1.5:1.5:6:6[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle' $gif
if ($LASTEXITCODE -ne 0) { throw "✗ paletteuse 失败" }
'{0:N2} MB  {1}' -f ((Get-Item $gif).Length / 1MB), $gif
Write-Host '✓ 完成'
