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
