# Copy repo dist/jAER.jar over the install4j-installed jAER.jar.
# Windows Program Files needs elevation; this re-launches with -Verb RunAs (UAC).
#
# Usage (repo root):
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/replace-installed-jaer-jar.ps1
#   ant replace-installed-jar
#   ant replace-installed-jar -Djaer.install.dir="C:\Program Files\jAER"

param(
    [string]$SourceJar = "",
    [string]$Install4jProject = "",
    [string]$InstallDir = "",
    [string]$DestJar = "",
    [string]$LogFile = "",
    [switch]$AlreadyElevated
)

$ErrorActionPreference = "Stop"

function Write-Log {
    param([string]$Message)
    Write-Host $Message
    if ($LogFile) {
        Add-Content -LiteralPath $LogFile -Value $Message
    }
}

function ConvertTo-ProcessArgList {
    param([string[]]$Items)
    $parts = foreach ($item in $Items) {
        $escaped = [string]$item
        $escaped = $escaped -replace '"', '\"'
        if ($escaped -match '[\s"]') {
            '"' + $escaped + '"'
        } else {
            $escaped
        }
    }
    return ($parts -join ' ')
}

function Test-IsAdministrator {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($id)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-Install4jMetadata {
    param([string]$ProjectPath)
    if (-not (Test-Path -LiteralPath $ProjectPath)) {
        throw "install4j project not found: $ProjectPath"
    }
    $raw = Get-Content -LiteralPath $ProjectPath -Raw
    $appId = $null
    $shortName = $null
    $exeName = "jaer"
    if ($raw -match 'applicationId="([^"]+)"') { $appId = $Matches[1] }
    if ($raw -match 'shortName="([^"]+)"') { $shortName = $Matches[1] }
    if ($raw -match '<executable name="([^"]+)"') { $exeName = $Matches[1] }
    if (-not $appId) { throw "Could not read applicationId from $ProjectPath" }
    if (-not $shortName) { throw "Could not read shortName from $ProjectPath" }
    return @{
        ApplicationId = $appId
        ShortName     = $shortName
        ExeName       = $exeName
    }
}

function Get-RegistryInstallDir {
    param([string]$AppId)
    $keys = @(
        "HKLM:\SOFTWARE\ej-technologies\install4j\installations",
        "HKLM:\SOFTWARE\WOW6432Node\ej-technologies\install4j\installations",
        "HKCU:\SOFTWARE\ej-technologies\install4j\installations",
        "HKCU:\SOFTWARE\WOW6432Node\ej-technologies\install4j\installations"
    )
    $valueName = "instdir$AppId"
    foreach ($key in $keys) {
        if (-not (Test-Path -LiteralPath $key)) { continue }
        try {
            $item = Get-Item -LiteralPath $key
            $val = $item.GetValue($valueName)
            if ($val) { return [string]$val }
        } catch {
            # ignore missing value
        }
    }

    $uninstallKeys = @(
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\$AppId",
        "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\$AppId",
        "HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\$AppId"
    )
    foreach ($key in $uninstallKeys) {
        if (-not (Test-Path -LiteralPath $key)) { continue }
        try {
            $props = Get-ItemProperty -LiteralPath $key
            if ($props.InstallLocation) { return [string]$props.InstallLocation }
        } catch {
            # ignore
        }
    }
    return $null
}

function Find-InstalledJaerJar {
    param([string]$Root)
    $candidates = @(
        (Join-Path $Root "dist\jAER.jar"),
        (Join-Path $Root "jaer\dist\jAER.jar"),
        (Join-Path $Root "Contents\Resources\app\dist\jAER.jar")
    )
    foreach ($c in $candidates) {
        if (Test-Path -LiteralPath $c) { return $c }
    }
    return $null
}

function Test-JaerInstallRoot {
    param(
        [string]$Root,
        [string]$ExeName
    )
    if (-not $Root) { return $false }
    if (-not (Test-Path -LiteralPath $Root)) { return $false }
    $jar = Find-InstalledJaerJar -Root $Root
    if (-not $jar) { return $false }
    $marker = @(
        (Join-Path $Root ".install4j"),
        (Join-Path $Root ($ExeName + ".exe")),
        (Join-Path $Root ($ExeName + ".app"))
    )
    foreach ($m in $marker) {
        if (Test-Path -LiteralPath $m) { return $true }
    }
    return $false
}

function Resolve-InstallRoot {
    param(
        [string]$Requested,
        [hashtable]$Meta
    )
    if ($Requested) {
        $resolved = [System.IO.Path]::GetFullPath($Requested)
        if (Test-Path -LiteralPath $resolved -PathType Leaf) {
            return [System.IO.Path]::GetDirectoryName($resolved)
        }
        return $resolved
    }

    $fromReg = Get-RegistryInstallDir -AppId $Meta.ApplicationId
    if ($fromReg -and (Test-JaerInstallRoot -Root $fromReg -ExeName $Meta.ExeName)) {
        return $fromReg
    }

    $guesses = @(
        (Join-Path $env:ProgramFiles $Meta.ShortName),
        (Join-Path ${env:ProgramFiles(x86)} $Meta.ShortName),
        (Join-Path $env:LOCALAPPDATA $Meta.ShortName),
        (Join-Path $env:USERPROFILE $Meta.ShortName)
    )
    foreach ($g in $guesses) {
        if (Test-JaerInstallRoot -Root $g -ExeName $Meta.ExeName) { return $g }
    }
    if ($fromReg) { return $fromReg }
    return $null
}

function Test-FileWritable {
    param([string]$Path)
    try {
        $fs = [System.IO.File]::Open($Path, 'Open', 'ReadWrite', 'None')
        $fs.Close()
        return $true
    } catch {
        return $false
    }
}

function Invoke-ElevatedCopy {
    param(
        [string]$ScriptPath,
        [string]$Source,
        [string]$Dest,
        [string]$Log
    )
    $psExe = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"
    $argString = ConvertTo-ProcessArgList @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", $ScriptPath,
        "-SourceJar", $Source,
        "-DestJar", $Dest,
        "-LogFile", $Log,
        "-AlreadyElevated"
    )
    Write-Log "Requesting administrator permission (Windows UAC) to replace:"
    Write-Log "  $Dest"
    Write-Log "Approve the prompt; this command waits until the copy finishes."
    try {
        $proc = Start-Process -FilePath $psExe -Verb RunAs -Wait -PassThru -ArgumentList $argString
    } catch {
        throw "UAC elevation was cancelled or failed: $($_.Exception.Message)"
    }
    if ($Log -and (Test-Path -LiteralPath $Log)) {
        Get-Content -LiteralPath $Log | ForEach-Object { Write-Host $_ }
    }
    if ($proc.ExitCode -ne 0) {
        throw "Elevated copy failed with exit code $($proc.ExitCode)"
    }
}

function Copy-JaerJar {
    param(
        [string]$Source,
        [string]$Dest
    )
    $running = @(Get-Process -Name "jaer" -ErrorAction SilentlyContinue)
    if ($running.Count -gt 0) {
        throw "jAER is running (jaer.exe). Close it so Windows can replace the jar, then re-run."
    }
    $destItem = Get-Item -LiteralPath $Dest
    if ($destItem.IsReadOnly) {
        $destItem.IsReadOnly = $false
    }
    $bak = "$Dest.bak"
    Copy-Item -LiteralPath $Dest -Destination $bak -Force
    Copy-Item -LiteralPath $Source -Destination $Dest -Force
    $srcLen = (Get-Item -LiteralPath $Source).Length
    $dstLen = (Get-Item -LiteralPath $Dest).Length
    Write-Log "Replaced $Dest"
    Write-Log ("  source {0:N1} KB, dest {1:N1} KB, backup {2}" -f ($srcLen / 1KB), ($dstLen / 1KB), $bak)
}

# --- main ---
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir

if (-not $InstallDir) {
    $InstallDir = $env:JAER_INSTALL_DIR
}
if (-not $SourceJar) {
    $SourceJar = Join-Path $repoRoot "dist\jAER.jar"
}
if (-not $Install4jProject) {
    $Install4jProject = Join-Path $repoRoot "install4j\jaer.install4j"
}
$SourceJar = [System.IO.Path]::GetFullPath($SourceJar)
$Install4jProject = [System.IO.Path]::GetFullPath($Install4jProject)

if ($DestJar) {
    $DestJar = [System.IO.Path]::GetFullPath($DestJar)
}

if (-not (Test-Path -LiteralPath $SourceJar)) {
    throw "Missing $SourceJar. Build it first: ant jar   or   ant jar-fast"
}

if ($AlreadyElevated -and $DestJar) {
    try {
        Copy-JaerJar -Source $SourceJar -Dest $DestJar
        exit 0
    } catch {
        Write-Log $_.Exception.Message
        exit 1
    }
}

$meta = Get-Install4jMetadata -ProjectPath $Install4jProject
Write-Log ("install4j applicationId={0} shortName={1} launcher={2}.exe" -f $meta.ApplicationId, $meta.ShortName, $meta.ExeName)

$root = Resolve-InstallRoot -Requested $InstallDir -Meta $meta
if (-not $root) {
    throw "Could not find an install4j jAER install. Pass -InstallDir or ant -Djaer.install.dir=..."
}
if (-not (Test-Path -LiteralPath $root)) {
    throw "Install directory does not exist: $root"
}

if (-not $DestJar) {
    $DestJar = Find-InstalledJaerJar -Root $root
}
if (-not $DestJar) {
    $DestJar = Join-Path $root "dist\jAER.jar"
}
if (-not (Test-Path -LiteralPath $DestJar)) {
    throw "Installed jAER.jar not found under $root (expected dist\jAER.jar)"
}

$srcFull = (Get-Item -LiteralPath $SourceJar).FullName
$dstFull = (Get-Item -LiteralPath $DestJar).FullName
if ($srcFull -eq $dstFull) {
    throw "Source and installed jar are the same file: $srcFull"
}

Write-Log "Source: $srcFull"
Write-Log "Install: $root"
Write-Log "Dest:    $dstFull"

$running = @(Get-Process -Name "jaer" -ErrorAction SilentlyContinue)
if ($running.Count -gt 0) {
    throw "jAER is running (jaer.exe). Close it so Windows can replace the jar, then re-run."
}

if ((Test-FileWritable -Path $DestJar) -or (Test-IsAdministrator)) {
    Copy-JaerJar -Source $SourceJar -Dest $DestJar
    exit 0
}

if ($AlreadyElevated) {
    throw "Still cannot write $DestJar after elevation (close jAER if it is running)."
}

$log = Join-Path $env:TEMP ("jaer-replace-jar-{0}.log" -f [guid]::NewGuid().ToString("N"))
Set-Content -LiteralPath $log -Value ""
$scriptPath = $MyInvocation.MyCommand.Path
if (-not [IO.Path]::IsPathRooted($scriptPath)) {
    $scriptPath = Join-Path (Get-Location).Path $scriptPath
}
$scriptPath = [IO.Path]::GetFullPath($scriptPath)
Invoke-ElevatedCopy -ScriptPath $scriptPath -Source $SourceJar -Dest $DestJar -Log $log
Remove-Item -LiteralPath $log -Force -ErrorAction SilentlyContinue
exit 0
