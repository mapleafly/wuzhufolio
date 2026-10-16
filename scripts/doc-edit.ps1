<#
.SYNOPSIS
    受约束的文档替换助手 —— 把「文档改写三件套」固化为可执行资产（AGENTS.md §7.3-10 精神）。

.DESCRIPTION
    背景（2026-10-14 第三轮复盘 §3.2）：本会话的文档批量改写反复踩到四类坑 ——
      ① 反引号被 PowerShell 当转义符吃掉 ⇒ 锚点静默失配；
      ② `[string]::Replace(a, b, $utf8)` 三参重载抛异常 ⇒ **写入未生效，脚本仍打印「✓」**（假阳性成功）；
      ③ 宽匹配（如「待批准发布」）误中标题行/目录行；
      ④ 回填脚本误改「带日期的证据行」（违反 `AGENTS.md §3.1-8`）。
    本脚本把纪律固化为**可执行约束**：白名单 → 命中数断言 → （日期锚点需 -Force）→ 替换 → **写入后回读复核**。
    任何一步不满足即**拒绝改写**并以非零退出码结束，绝不静默成功。

.PARAMETER File
    目标文件路径（相对路径按当前工作目录解析）。

.PARAMETER Anchor
    要被替换的**字面量**片段（非正则）。必须整段出现且出现次数等于 -AllowCount。

.PARAMETER Replacement
    替换文本（字面量；`$` 与反引号不会被解释——脚本内部一律走字面量路径）。

.PARAMETER AllowCount
    允许的命中次数，默认 1。显式允许 N 处时才写 N（防止"以为改了一处、实际改了五处"）。

.PARAMETER Whitelist
    可选：允许改写的路径前缀白名单（相对仓库根或绝对路径均可）。给出时，目标文件不匹配任一前缀即拒绝。

.PARAMETER Force
    锚点**本身含日期**（形如 2026-10-14）时，视为疑似「带日期的证据行」而默认拒绝；
    确需修改时显式加 -Force（`§3.1-8`：证据类文档保留当次数值，新口径应**追加注记**而非覆盖）。

.EXAMPLE
    pwsh -File scripts/doc-edit.ps1 -File docs/dev/STATUS.md -Anchor '旧文本' -Replacement '新文本'

.EXAMPLE
    # 只允许改两个目录下的文件
    pwsh -File scripts/doc-edit.ps1 -File docs/dev/STATUS.md -Anchor 'a' -Replacement 'b' `
        -Whitelist docs/dev/,docs/release/

.NOTES
    退出码：0 = 成功（已回读复核）；2 = 拒绝（命中数不符 / 白名单外 / 日期证据行未加 -Force / 回读失败）；1 = 参数或 IO 错误。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$File,
    [Parameter(Mandatory = $true)][string]$Anchor,
    [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Replacement,
    [int]$AllowCount = 1,
    [string[]]$Whitelist = @(),
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Fail([string]$message, [int]$code) {
    [Console]::Error.WriteLine("REFUSE: $message")
    exit $code
}

if ([string]::IsNullOrEmpty($Anchor)) { Fail '锚点不能为空' 1 }
if ($AllowCount -lt 1) { Fail '-AllowCount 必须 ≥ 1' 1 }

# --- 0) 文件与白名单 ---------------------------------------------------------
$full = [System.IO.Path]::GetFullPath($File)
if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { Fail "文件不存在：$full" 1 }

if ($Whitelist.Count -gt 0) {
    $ok = $false
    foreach ($w in $Whitelist) {
        $wf = [System.IO.Path]::GetFullPath($w)
        if ($full.StartsWith($wf, [System.StringComparison]::OrdinalIgnoreCase)) { $ok = $true; break }
    }
    if (-not $ok) { Fail "文件不在白名单内：$full（白名单：$($Whitelist -join ', ')）" 2 }
}

# --- 1) 日期证据行守卫（§3.1-8）---------------------------------------------
if (-not $Force -and $Anchor -match '\d{4}-\d{2}-\d{2}') {
    Fail ("锚点含日期（疑似带日期的证据行）：{0}…`n        §3.1-8：证据类文档保留当次实测数值，新口径应【追加注记】而非覆盖；`n        确需修改请显式加 -Force。" -f $Anchor.Substring(0, [Math]::Min(40, $Anchor.Length))) 2
}

# --- 2) 读取 + 命中数断言 ----------------------------------------------------
$before = [System.IO.File]::ReadAllText($full)
$hashBefore = (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash
$count = ([regex]::Matches($before, [regex]::Escape($Anchor))).Count
if ($count -ne $AllowCount) {
    Fail "锚点命中 $count 处，期望 $AllowCount 处 ⇒ 拒绝改写（未做任何写入）。锚点：$($Anchor.Substring(0, [Math]::Min(60, $Anchor.Length)))…" 2
}

# --- 3) 替换 ----------------------------------------------------------------
$anchorLinesBefore = ($before -split "`n").Count
$after = $before.Replace($Anchor, $Replacement)
if ($after -eq $before) { Fail '替换后内容与原文完全相同 ⇒ 拒绝（避免"改了等于没改"的假成功）' 2 }

# --- 4) 写入 ----------------------------------------------------------------
[System.IO.File]::WriteAllText($full, $after, $utf8)

# --- 5) 写入后回读复核（防"假阳性成功"）------------------------------------
$readback = [System.IO.File]::ReadAllText($full)
$hashAfter = (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash

if ($readback -ne $after) { Fail "回读复核失败：磁盘内容与预期不符（$full）" 2 }
$anchorLeft = ([regex]::Matches($readback, [regex]::Escape($Anchor))).Count
# 替换文本**自身含锚点**时（典型：锚点后追加一段），每替换一处就会重新引入一处锚点 ⇒
# 期望剩余数 = AllowCount × (替换文本中锚点出现次数)；不含锚点则期望 0。
$anchorInReplacement = ([regex]::Matches($Replacement, [regex]::Escape($Anchor))).Count
$expectedLeft = $AllowCount * $anchorInReplacement
if ($anchorLeft -ne $expectedLeft) {
    Fail "回读复核失败：替换后锚点出现 $anchorLeft 处，期望 $expectedLeft 处（AllowCount=$AllowCount × 替换文本中锚点数=$anchorInReplacement）" 2
}
if ($replacementApplies = ($Replacement.Length -gt 0) -and (-not $readback.Contains($Replacement))) {
    Fail "回读复核失败：替换文本未在磁盘内容中出现" 2
}

$anchorLinesAfter = ($readback -split "`n").Count
"OK file=$($full.Replace((Get-Location).Path + '\', '')) replaced=$count verified=true lines=$anchorLinesBefore->$anchorLinesAfter sha256=$($hashBefore.Substring(0,12))->$($hashAfter.Substring(0,12))"
exit 0
