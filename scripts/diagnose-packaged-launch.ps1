<#
    WuZhuFolio packaged-build launch diagnosis (DEF-42, TC-MAN-02 step 3).

    Symptom under investigation: double-clicking the installed WuZhuFolio shortcut
    shows a dialog "Failed to launch JVM" on Windows.

    That dialog is produced by the jpackage NATIVE LAUNCHER (WuZhuFolio.exe), not by
    Java code: it means the launcher could not start the BUNDLED runtime
    (runtime\bin\server\jvm.dll). The JDK installed on the machine (e.g. Temurin 21)
    is irrelevant -- the packaged app ships its own trimmed runtime.

    This script is READ-ONLY except for the optional launch probe (step 9), which
    starts the app with a throw-away data dir under %TEMP% and then stops it.
    NOTE: ASCII-only on purpose -- Windows PowerShell 5.1 decodes BOM-less files with
    the ANSI code page, so non-ASCII text here would be garbled.

    Usage (from the repo root or anywhere):
        powershell -ExecutionPolicy Bypass -File .\scripts\diagnose-packaged-launch.ps1
        powershell -ExecutionPolicy Bypass -File .\scripts\diagnose-packaged-launch.ps1 -InstallDir "D:\WuZhuFolio"
        powershell -ExecutionPolicy Bypass -File .\scripts\diagnose-packaged-launch.ps1 -SkipLaunch
#>
[CmdletBinding()]
param(
    [string]$InstallDir = '',
    [switch]$SkipLaunch,
    [int]$LaunchWaitSeconds = 40
)

$ErrorActionPreference = 'Continue'
$script:Findings = New-Object System.Collections.ArrayList

function Write-Section([string]$Title) {
    Write-Output ''
    Write-Output ('=' * 74)
    Write-Output ('== ' + $Title)
    Write-Output ('=' * 74)
}

function Write-Finding([string]$Text) {
    [void]$script:Findings.Add($Text)
    Write-Output ('  [findings] ' + $Text)
}

function Test-NonAscii([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return $false }
    foreach ($ch in $Text.ToCharArray()) { if ([int]$ch -gt 127) { return $true } }
    return $false
}

function Invoke-Capture([string]$Exe, [string[]]$Arguments) {
    try {
        $out = & $Exe @Arguments 2>&1 | Out-String
        return @{ ExitCode = $LASTEXITCODE; Output = $out.Trim() }
    } catch {
        return @{ ExitCode = -1; Output = ('EXCEPTION: ' + $_.Exception.Message) }
    }
}

Write-Output 'WuZhuFolio packaged-launch diagnosis -- paste the WHOLE output back to the agent.'
Write-Output ('generated: ' + (Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))

# ---------------------------------------------------------------- 1. environment
Write-Section '1. Environment'
try {
    $os = Get-CimInstance Win32_OperatingSystem -ErrorAction Stop
    Write-Output ('OS            : ' + $os.Caption + '  build ' + $os.BuildNumber + ' (' + $os.OSArchitecture + ')')
    Write-Output ('Locale        : ' + (Get-Culture).Name + ' / UI ' + (Get-UICulture).Name)
    Write-Output ('PSVersion     : ' + $PSVersionTable.PSVersion.ToString())
    Write-Output ('User          : ' + $env:USERNAME)
    Write-Output ('USERPROFILE   : ' + $env:USERPROFILE)
    Write-Output ('LOCALAPPDATA  : ' + $env:LOCALAPPDATA)
    Write-Output ('TEMP          : ' + $env:TEMP)
    try { Write-Output ('Console CP    : ' + ([Console]::OutputEncoding.WebName) + ' / ' + (chcp)) } catch { }
    $acp = (Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control\Nls\CodePage' -ErrorAction SilentlyContinue).ACP
    Write-Output ('System ANSI CP: ' + $acp + '   (65001 = "Beta: UTF-8 worldwide" enabled)')
    if (Test-NonAscii $env:USERPROFILE) {
        Write-Finding 'USERPROFILE contains NON-ASCII characters -> per-user install path is non-ASCII (top suspect).'
    } else {
        Write-Finding 'USERPROFILE is pure ASCII.'
    }
    if ("$acp" -eq '65001') { Write-Finding 'System ANSI code page is 65001 (UTF-8 beta).' }
} catch {
    Write-Output ('environment probe failed: ' + $_.Exception.Message)
}

# ------------------------------------------------------------- 2. install records
Write-Section '2. Install records (registry Uninstall keys)'
$uninstallRoots = @(
    'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
    'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
    'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*'
)
$registryDirs = New-Object System.Collections.ArrayList
foreach ($root in $uninstallRoots) {
    try {
        Get-ItemProperty $root -ErrorAction SilentlyContinue |
            Where-Object { $_.DisplayName -like '*WuZhuFolio*' } |
            ForEach-Object {
                Write-Output ('DisplayName    : ' + $_.DisplayName)
                Write-Output ('DisplayVersion : ' + $_.DisplayVersion)
                Write-Output ('InstallLocation: ' + $_.InstallLocation)
                Write-Output ('InstallDate    : ' + $_.InstallDate)
                Write-Output ('UninstallString: ' + $_.UninstallString)
                Write-Output ('  key          : ' + $_.PSPath)
                Write-Output ''
                if ($_.InstallLocation) { [void]$registryDirs.Add($_.InstallLocation) }
            }
    } catch { }
}
if ($registryDirs.Count -eq 0) { Write-Finding 'No WuZhuFolio entry in the Uninstall registry keys.' }
if ($registryDirs.Count -gt 1) { Write-Finding 'MORE THAN ONE install record -> stale/parallel installs are likely (mixed shortcut/runtime).' }

# --------------------------------------------------------- 3. candidate app dirs
Write-Section '3. Candidate install directories'
$candidates = New-Object System.Collections.ArrayList
if ($InstallDir) { [void]$candidates.Add($InstallDir) }
foreach ($d in $registryDirs) { [void]$candidates.Add($d) }
$baseDirs = @($env:LOCALAPPDATA, $env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:USERPROFILE)
foreach ($base in $baseDirs) {
    if ($base) { [void]$candidates.Add((Join-Path $base 'WuZhuFolio')) }
}
$candidates = $candidates | Select-Object -Unique
$appDirs = New-Object System.Collections.ArrayList
foreach ($dir in $candidates) {
    Write-Output ('--- ' + $dir)
    if (-not (Test-Path -LiteralPath $dir)) { Write-Output '    (not present)'; continue }
    [void]$appDirs.Add($dir)
    if (Test-NonAscii $dir) { Write-Finding ('install dir is NON-ASCII: ' + $dir) }
    foreach ($rel in @('WuZhuFolio.exe', 'app\WuZhuFolio.cfg', 'runtime\bin\java.exe', 'runtime\bin\server\jvm.dll', 'runtime\release')) {
        $p = Join-Path $dir $rel
        if (Test-Path -LiteralPath $p) {
            $item = Get-Item -LiteralPath $p
            Write-Output ('    OK   ' + $rel + '  (' + $item.Length + ' bytes, ' + $item.LastWriteTime + ')')
        } else {
            Write-Output ('    MISS ' + $rel)
            if ($rel -ne 'runtime\release') {
                Write-Finding ('missing file in ' + $dir + ' : ' + $rel + ' -> incomplete install or antivirus removed it.')
            }
        }
    }
    $jarDir = Join-Path $dir 'app'
    if (Test-Path -LiteralPath $jarDir) {
        $jars = @(Get-ChildItem -LiteralPath $jarDir -Filter '*.jar' -ErrorAction SilentlyContinue)
        Write-Output ('    app\*.jar     : ' + $jars.Count + ' jars')
        $skiko = @($jars | Where-Object { $_.Name -like 'skiko-awt-runtime-*' })
        Write-Output ('    skiko natives : ' + (($skiko | ForEach-Object { $_.Name }) -join ', '))
        if ($skiko.Count -eq 0) { Write-Finding 'no skiko native runtime jar in app\ (window creation would fail).' }
    }
    $exe = Join-Path $dir 'WuZhuFolio.exe'
    if (Test-Path -LiteralPath $exe) {
        try {
            $zone = Get-Item -LiteralPath $exe -Stream Zone.Identifier -ErrorAction Stop
            Write-Output ('    MOTW         : ' + ((Get-Content -LiteralPath $exe -Stream Zone.Identifier) -join ' | '))
        } catch { Write-Output '    MOTW         : none' }
    }
}

# ------------------------------------------------------------- 4. shortcuts
Write-Section '4. Shortcuts (.lnk) pointing at WuZhuFolio'
$shell = New-Object -ComObject WScript.Shell
$lnkRoots = @(
    (Join-Path $env:USERPROFILE 'Desktop'),
    (Join-Path $env:PUBLIC 'Desktop'),
    (Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs'),
    (Join-Path $env:ProgramData 'Microsoft\Windows\Start Menu\Programs')
)
$lnkCount = 0
foreach ($root in $lnkRoots) {
    if (-not (Test-Path -LiteralPath $root)) { continue }
    $links = @(Get-ChildItem -LiteralPath $root -Recurse -Filter '*.lnk' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like '*WuZhuFolio*' })
    foreach ($lnk in $links) {
        $lnkCount++
        try {
            $sc = $shell.CreateShortcut($lnk.FullName)
            $targetExists = Test-Path -LiteralPath $sc.TargetPath
            Write-Output ('lnk        : ' + $lnk.FullName)
            Write-Output ('  target   : ' + $sc.TargetPath + '   exists=' + $targetExists)
            Write-Output ('  workdir  : ' + $sc.WorkingDirectory)
            Write-Output ('  args     : ' + $sc.Arguments)
            if (-not $targetExists) {
                Write-Finding ('shortcut target does NOT exist: ' + $sc.TargetPath + ' -> stale shortcut from an older/other install dir.')
            }
        } catch { Write-Output ('lnk        : ' + $lnk.FullName + '  (unreadable: ' + $_.Exception.Message + ')') }
    }
}
if ($lnkCount -eq 0) { Write-Finding 'no WuZhuFolio shortcut found (Desktop/Start Menu).' }

# ------------------------------------------------------- 5. bundled runtime test
Write-Section '5. Bundled runtime self-test (runtime\bin\java.exe -version)'
foreach ($dir in $appDirs) {
    $javaExe = Join-Path $dir 'runtime\bin\java.exe'
    if (-not (Test-Path -LiteralPath $javaExe)) { continue }
    Write-Output ('--- ' + $javaExe)
    $r = Invoke-Capture $javaExe @('-version')
    Write-Output ('exit code: ' + $r.ExitCode)
    Write-Output $r.Output
    if ($r.ExitCode -ne 0) {
        Write-Finding ('bundled runtime FAILED to run from ' + $dir + ' -> runtime broken/blocked there (antivirus, incomplete copy, path issue).')
    } else {
        Write-Finding ('bundled runtime runs fine from ' + $dir + '.')
    }
}

# ------------------------------------------------------- 6. cfg sanity check
Write-Section '6. Launcher config (app\WuZhuFolio.cfg)'
foreach ($dir in $appDirs) {
    $cfg = Join-Path $dir 'app\WuZhuFolio.cfg'
    if (-not (Test-Path -LiteralPath $cfg)) { continue }
    Write-Output ('--- ' + $cfg)
    $lines = Get-Content -LiteralPath $cfg -ErrorAction SilentlyContinue
    Write-Output ('lines: ' + @($lines).Count)
    Write-Output (($lines | Where-Object { $_ -match '^\[|^app\.mainclass|java-options' }) -join "`n")
    $missing = @()
    foreach ($line in ($lines | Where-Object { $_ -match '^app\.classpath=' })) {
        $rel = ($line -replace '^app\.classpath=\$APPDIR[\\/]?', '')
        $p = Join-Path (Join-Path $dir 'app') $rel
        if (-not (Test-Path -LiteralPath $p)) { $missing += $rel }
    }
    if ($missing.Count -gt 0) {
        Write-Finding ('cfg references ' + $missing.Count + ' jar(s) that are missing on disk (first: ' + $missing[0] + ').')
    } else {
        Write-Output ('all ' + @($lines | Where-Object { $_ -match '^app\.classpath=' }).Count + ' classpath entries exist')
    }
}

# ------------------------------------------------------------- 7. app own log
Write-Section '7. Application log (bootstrap marker)'
$dataDirs = @((Join-Path $env:USERPROFILE '.wuzhufolio'))
if ($env:WUZHUFOLIO_DATA_DIR) { $dataDirs += $env:WUZHUFOLIO_DATA_DIR }
foreach ($dataDir in $dataDirs) {
    $logDir = Join-Path $dataDir 'logs'
    Write-Output ('--- ' + $logDir)
    if (-not (Test-Path -LiteralPath $logDir)) { Write-Output '    (no log dir -> the JVM never reached bootstrap on this data dir)'; continue }
    $log = Get-ChildItem -LiteralPath $logDir -Filter '*.log' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $log) { Write-Output '    (no *.log file)'; continue }
    Write-Output ('newest: ' + $log.Name + '  ' + $log.LastWriteTime + '  ' + $log.Length + ' bytes')
    $boot = Select-String -LiteralPath $log.FullName -Pattern 'bootstrap ok' -ErrorAction SilentlyContinue |
        Select-Object -Last 1
    if ($boot) { Write-Output ('last bootstrap ok: ' + $boot.Line) } else { Write-Output 'no "bootstrap ok" line in this log' }
    Write-Output '--- tail ---'
    Get-Content -LiteralPath $log.FullName -Tail 25 -ErrorAction SilentlyContinue
    Write-Output '--- end tail ---'
    $ageMinutes = ((Get-Date) - $log.LastWriteTime).TotalMinutes
    if ($boot -and $ageMinutes -lt 10) {
        Write-Finding 'a FRESH "bootstrap ok" exists -> the JVM DID start recently. The failure is NOT the launcher; report this and the tail above.'
    }
}

# ----------------------------------------------------- 8. security software
Write-Section '8. Security software'
try {
    $mp = Get-MpComputerStatus -ErrorAction Stop
    Write-Output ('Defender realtime : ' + $mp.RealTimeProtectionEnabled + ' / antispyware ' + $mp.AntispywareEnabled)
    $threats = Get-MpThreatDetection -ErrorAction SilentlyContinue |
        Where-Object { $_.Resources -like '*WuZhuFolio*' -or $_.Resources -like '*wuzhufolio*' }
    if ($threats) {
        Write-Output 'Defender detections mentioning WuZhuFolio:'
        $threats | Select-Object -First 10 | ForEach-Object { Write-Output ('  ' + $_.InitialDetectionTime + ' ' + ($_.Resources -join ',')) }
        Write-Finding 'Windows Defender logged a detection touching WuZhuFolio files.'
    } else {
        Write-Output 'Defender detections mentioning WuZhuFolio: none'
    }
} catch { Write-Output 'Get-MpComputerStatus unavailable (Defender disabled or third-party AV).' }
try {
    Get-CimInstance -Namespace 'root\SecurityCenter2' -ClassName AntiVirusProduct -ErrorAction Stop |
        ForEach-Object { Write-Output ('AV product: ' + $_.displayName + '  state=' + $_.productState) }
} catch { Write-Output 'SecurityCenter2 AV list unavailable.' }

# --------------------------------------------------------- 9. launch probe
Write-Section '9. Launch probe (fresh data dir under %TEMP%)'
if ($SkipLaunch) {
    Write-Output 'skipped (-SkipLaunch)'
} else {
    $probeDir = Join-Path $env:TEMP ('wzf-launch-probe-' + (Get-Date).ToString('yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $probeDir -Force | Out-Null
    $exe = $null
    foreach ($dir in $appDirs) {
        $candidate = Join-Path $dir 'WuZhuFolio.exe'
        if (Test-Path -LiteralPath $candidate) { $exe = $candidate; break }
    }
    if (-not $exe) {
        Write-Output 'no WuZhuFolio.exe found to probe'
    } else {
        Write-Output ('exe        : ' + $exe)
        Write-Output ('data dir   : ' + $probeDir)
        $env:WUZHUFOLIO_DATA_DIR = $probeDir
        $env:JAVA_TOOL_OPTIONS = '-Dskiko.renderApi=SOFTWARE_FAST'
        $sw = [Diagnostics.Stopwatch]::StartNew()
        try {
            $proc = Start-Process -FilePath $exe -WorkingDirectory (Split-Path -Parent $exe) -PassThru
        } catch {
            Write-Output ('Start-Process failed: ' + $_.Exception.Message)
            $proc = $null
            Write-Finding 'Start-Process could not even start WuZhuFolio.exe.'
        }
        if ($proc) {
            Write-Output ('pid        : ' + $proc.Id + '  waiting ' + $LaunchWaitSeconds + 's ...')
            Start-Sleep -Seconds $LaunchWaitSeconds
            $sw.Stop()
            $alive = -not $proc.HasExited
            $aliveText = 'after ' + [int]$sw.Elapsed.TotalSeconds + 's: alive=' + $alive
            if (-not $alive) { $aliveText = $aliveText + '  exitCode=' + $proc.ExitCode }
            Write-Output $aliveText
            $probeLog = Get-ChildItem -LiteralPath (Join-Path $probeDir 'logs') -Filter '*.log' -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending | Select-Object -First 1
            if ($probeLog) {
                Write-Output ('probe log  : ' + $probeLog.FullName)
                $ok = Select-String -LiteralPath $probeLog.FullName -Pattern 'bootstrap ok' -Quiet
                Write-Output ('bootstrap ok present: ' + $ok)
                Get-Content -LiteralPath $probeLog.FullName -Tail 15 -ErrorAction SilentlyContinue
                if ($ok) { Write-Finding 'PROBE PASSED: the packaged app started and bootstrapped on this machine.' }
            } else {
                Write-Output 'probe log  : (none -> the JVM never ran under this data dir)'
                Write-Finding 'PROBE FAILED: no application log was produced -> the launcher/JVM did not start.'
            }
            if ($alive) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
            Write-Output ('probe data dir kept for inspection: ' + $probeDir)
        }
    }
}

# --------------------------------------------------------------- 10. summary
Write-Section '10. Findings summary'
if ($script:Findings.Count -eq 0) { Write-Output '  (none)' }
foreach ($f in $script:Findings) { Write-Output ('  - ' + $f) }
Write-Output ''
Write-Output 'Paste this whole output back to the agent (plus the full text of the error dialog).'
