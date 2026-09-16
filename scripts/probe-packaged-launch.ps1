<#
    WuZhuFolio packaged-build launch probe (DEF-42).

    Runs a jpackage app-image executable with a throw-away data dir and reports whether the
    application reached its bootstrap marker (`bootstrap ok`) in its own log.
    Works for the installed tree as well (point -ExePath at the installed WuZhuFolio.exe).

    Exit codes: 0 = PASS（写出 bootstrap ok）· 3 = FAIL（进程退出/超时且无 bootstrap ok）· 2 = 用法/路径错误
    ASCII-only on purpose (Windows PowerShell 5.1 decodes BOM-less files with the ANSI code page).

    Usage:
        pwsh -File scripts/probe-packaged-launch.ps1 -ExePath C:\WuZhuFolio\WuZhuFolio.exe
        pwsh -File scripts/probe-packaged-launch.ps1 -ExePath "D:\中文目录\WuZhuFolio\WuZhuFolio.exe" -Label cjk
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ExePath,
    [string]$Label = 'probe',
    [int]$TimeoutSeconds = 90,
    # 模拟「系统开启了 Java Access Bridge / 辅助技术」（DEF-43）：Windows 上通常来自
    # %USERPROFILE%\.accessibility.properties（jabswitch -enable / 读屏软件写入）。
    # 开启后仍必须能启动 —— 曾因运行时缺 jdk.accessibility 而在 AWT 初始化抛 AWTError。
    [switch]$AssistiveTech
)

$ErrorActionPreference = 'Continue'

if (-not (Test-Path -LiteralPath $ExePath)) {
    Write-Host "== [$Label] EXE not found: $ExePath"
    exit 2
}
$full = (Resolve-Path -LiteralPath $ExePath).Path
$dataDir = Join-Path ([System.IO.Path]::GetTempPath()) ('wzf-probe-' + $Label + '-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
$env:WUZHUFOLIO_DATA_DIR = $dataDir
$baseOptions = '-Dskiko.renderApi=SOFTWARE_FAST'
if ($AssistiveTech) {
    $baseOptions = $baseOptions + ' -Djavax.accessibility.assistive_technologies=com.sun.java.accessibility.AccessBridge'
    Write-Host "== [$Label] assistive technology ON (javax.accessibility.assistive_technologies=AccessBridge)"
}
if (-not $env:JAVA_TOOL_OPTIONS) { $env:JAVA_TOOL_OPTIONS = $baseOptions } else { $env:JAVA_TOOL_OPTIONS = $env:JAVA_TOOL_OPTIONS + ' ' + $baseOptions }

Write-Host "== [$Label] exe        : $full"
Write-Host "== [$Label] data dir   : $dataDir"
$proc = Start-Process -FilePath $full -WorkingDirectory (Split-Path -Parent $full) -PassThru
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$ok = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    $log = Get-ChildItem (Join-Path $dataDir 'logs') -Filter '*.log' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($log -and (Select-String -LiteralPath $log.FullName -Pattern 'bootstrap ok' -Quiet)) {
        $ok = $true
        Select-String -LiteralPath $log.FullName -Pattern 'bootstrap ok' | Select-Object -Last 1 |
            ForEach-Object { Write-Host ('== [' + $Label + '] ' + $_.Line.Trim()) }
        break
    }
    if ($proc.HasExited) {
        Write-Host "== [$Label] process exited early: exitCode=$($proc.ExitCode) (no bootstrap ok written)"
        break
    }
}
if ($ok) {
    Write-Host "LAUNCH_PROBE[$Label]=PASS"
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    exit 0
}

if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
$log = Get-ChildItem (Join-Path $dataDir 'logs') -Filter '*.log' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($log) {
    Write-Host "== [$Label] app log tail:"
    Get-Content -LiteralPath $log.FullName -Tail 30
} else {
    Write-Host "== [$Label] no application log was produced at all (JVM never started)"
}
Write-Host "== [$Label] app dir listing:"
Get-ChildItem (Split-Path -Parent $full) | Select-Object -First 20 | Format-Table -AutoSize | Out-String | Write-Host
Write-Host "LAUNCH_PROBE[$Label]=FAIL"
exit 3
