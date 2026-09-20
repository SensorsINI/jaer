# Bake website/latest.json, serve website/, open the default browser.
# Usage (repo root):
#   ant website
#   ant website -Djaer.website.port=8081
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/website-preview.ps1

param(
    [string]$Port = ""
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
Set-Location $repoRoot

if (-not $Port) {
    if ($env:JAER_WEBSITE_PORT) {
        $Port = $env:JAER_WEBSITE_PORT
    } else {
        $Port = "8080"
    }
}

if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    throw "python is not on PATH (needed for website/bake-latest.py and http.server)"
}

function Get-PidsListeningOnPort([int]$ListenPort) {
    $found = New-Object System.Collections.Generic.List[int]
    $pattern = ":" + $ListenPort + "\s+\S+\s+LISTENING\s+(\d+)\s*$"
    foreach ($line in (netstat -ano 2>$null)) {
        if ($line -match $pattern) {
            $id = [int]$Matches[1]
            if (-not $found.Contains($id)) {
                $found.Add($id)
            }
        }
    }
    return $found
}

function Get-ListenerCommand([int]$ProcessId) {
    $cim = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $ProcessId) -ErrorAction SilentlyContinue
    if ($cim -and $cim.CommandLine) {
        return $cim.CommandLine
    }
    $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($proc -and $proc.Path) {
        return $proc.Path
    }
    return ""
}

function Test-WebsitePreviewProcess([int]$ProcessId, [int]$ListenPort) {
    $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $proc) {
        return $false
    }
    if ($proc.ProcessName -notmatch '^python(\d|w)?$') {
        return $false
    }
    $cmd = Get-ListenerCommand $ProcessId
    if (-not $cmd) {
        return $false
    }
    if ($cmd -notmatch 'http\.server') {
        return $false
    }
    if ($cmd -notmatch [regex]::Escape([string]$ListenPort)) {
        return $false
    }
    return $true
}

function Write-ListenerInfo([int]$ProcessId) {
    $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    $name = if ($proc) { $proc.ProcessName } else { "?" }
    $started = ""
    if ($proc -and $proc.StartTime) {
        $started = "  started " + $proc.StartTime.ToString("yyyy-MM-dd HH:mm")
    }
    Write-Host ("  PID {0}  {1}{2}" -f $ProcessId, $name, $started)
    $cmd = Get-ListenerCommand $ProcessId
    if ($cmd) {
        Write-Host ("    {0}" -f $cmd)
    }
}

$listenPids = Get-PidsListeningOnPort ([int]$Port)
if ($listenPids.Count -gt 0) {
    $previewPids = New-Object System.Collections.Generic.List[int]
    $otherPids = New-Object System.Collections.Generic.List[int]
    foreach ($id in $listenPids) {
        if (Test-WebsitePreviewProcess $id ([int]$Port)) {
            $previewPids.Add($id)
        } else {
            $otherPids.Add($id)
        }
    }
    if ($otherPids.Count -gt 0) {
        Write-Host ("Port {0} is already in use (not an ant website preview):" -f $Port)
        Write-Host ""
        foreach ($id in $listenPids) {
            Write-ListenerInfo $id
        }
        Write-Host ""
        Write-Host ("Or another port:  ant website -Djaer.website.port=8081")
        exit 1
    }
    Write-Host ("Stopping leftover ant website preview on port {0}:" -f $Port)
    foreach ($id in $previewPids) {
        Write-ListenerInfo $id
        Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
    }
    $deadline = (Get-Date).AddSeconds(5)
    while ((Get-Date) -lt $deadline) {
        $left = Get-PidsListeningOnPort ([int]$Port)
        if ($left.Count -eq 0) {
            break
        }
        Start-Sleep -Milliseconds 200
    }
    $left = Get-PidsListeningOnPort ([int]$Port)
    if ($left.Count -gt 0) {
        Write-Host ("Port {0} is still in use after Stop-Process." -f $Port)
        foreach ($id in $left) {
            Write-ListenerInfo $id
        }
        exit 1
    }
    Write-Host ""
}

& python website\bake-latest.py
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$url = "http://127.0.0.1:$Port/"
Write-Host "Serving website/ at $url  (Ctrl+C to stop)"

$delayCmd = "Start-Sleep -Seconds 1; Start-Process '$url'"
Start-Process -FilePath "powershell.exe" -WindowStyle Hidden -ArgumentList @(
    "-NoProfile",
    "-Command",
    $delayCmd
) | Out-Null

& python -m http.server $Port --bind 127.0.0.1 --directory website
exit $LASTEXITCODE
