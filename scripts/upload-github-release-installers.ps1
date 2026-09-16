# Upload installer media from currentInstallers/<VERSION>/ to an existing GitHub Release.
# Does not create a tag or draft (ant create-draft-release first).
# Default: skip Windows .exe (keeps Azure-signed GitHub asset) and skip macOS .dmg
# unless this host is macOS (Mini notarized builds). Linux .sh from any OS.
# Sample zip is ant upload-sample-data, not this script.
# Usage (repo root):
#   powershell -File scripts/upload-github-release-installers.ps1
#   powershell -File scripts/upload-github-release-installers.ps1 -Tag 3.5.0
#   powershell -File scripts/upload-github-release-installers.ps1 -WhatIf
#   powershell -File scripts/upload-github-release-installers.ps1 -ClobberWindows

param(
    [string]$Tag = "",
    [switch]$WhatIf,
    [switch]$ClobberWindows
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

if (-not $Tag) {
    $Tag = (Get-Content -Raw VERSION.txt).Trim()
}
if (-not $Tag) { throw "VERSION.txt is empty and -Tag was not set" }

$dir = Join-Path $root "currentInstallers\$Tag"
if (-not (Test-Path $dir)) { throw "Missing $dir -- build media first (ant macos-build-notarize / release-linux / azure-sign-ci)" }

$onMac = Test-Path -LiteralPath "/System/Library/CoreServices/SystemVersion.plist"

$installers = @(Get-ChildItem -Path $dir -File | Where-Object {
    $_.Name -match '^jAER_(windows-x64|macos|unix)_.*\.(exe|dmg|sh)$'
})
if (-not $installers) { throw "No jAER_windows-x64_*.exe / jAER_macos_*.dmg / jAER_unix_*.sh under $dir" }

$files = New-Object System.Collections.Generic.List[object]
foreach ($f in $installers) {
    if ($f.Name -like 'jAER_windows-x64_*.exe') {
        if ($ClobberWindows) {
            Write-Host ("WARNING: uploading local unsigned $($f.Name) --clobber over the Azure-signed GitHub exe")
            [void]$files.Add($f)
        } else {
            Write-Host ("Skipping $($f.Name) to keep the Azure-signed GitHub asset. Do not ant upload-installers-clobber-windows after Azure.")
        }
        continue
    }
    if ($f.Name -like 'jAER_macos_*.dmg') {
        if (-not $onMac) {
            Write-Host ("Skipping $($f.Name) (not macOS). Mac DMGs must be uploaded from the Mini after ant macos-build-notarize.")
            continue
        }
        [void]$files.Add($f)
        continue
    }
    [void]$files.Add($f)
}

$sampleZip = Join-Path $dir "jaer-sample-data.zip"
if (Test-Path -LiteralPath $sampleZip) {
    Write-Host "Not attaching jaer-sample-data.zip here. Use: ant upload-sample-data"
}

if ($files.Count -eq 0) {
    throw 'Nothing to upload. Windows exe is skipped unless -ClobberWindows. Mac DMGs only from macOS. Linux: ant release-linux then re-run.'
}

Write-Host "Release tag: $Tag"
$files | ForEach-Object { Write-Host ("  {0} ({1:N1} MB)" -f $_.Name, ($_.Length / 1MB)) }

$paths = @($files | ForEach-Object { $_.FullName })
if ($WhatIf) {
    Write-Host "WhatIf: would upload $($paths.Count) file(s) to release $Tag"
    $paths | ForEach-Object { Write-Host "  $_" }
    return
}

$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
gh release view $Tag --json tagName 2>$null | Out-Null
$releaseMissing = ($LASTEXITCODE -ne 0)
$ErrorActionPreference = $prevEap
if ($releaseMissing) {
    throw "GitHub Release $Tag does not exist. Create it first: ant create-draft-release"
}
Write-Host "Leaving GitHub release body unchanged (use ant upload-release-notes to push notes)."

$env:GH_SPINNER_DISABLED = "yes"
$items = @($files)
$total = $items.Count
$n = 0
$ErrorActionPreference = "Continue"
foreach ($f in $items) {
    $n++
    $mb = [math]::Round($f.Length / 1MB, 1)
    Write-Host ("[{0}/{1}] Uploading {2} ({3} MB) ..." -f $n, $total, $f.Name, $mb)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    gh release upload $Tag $f.FullName --clobber
    $code = $LASTEXITCODE
    $sw.Stop()
    if ($code -ne 0) {
        $ErrorActionPreference = $prevEap
        throw "gh release upload failed for $($f.Name) (exit $code)"
    }
    Write-Host ("[{0}/{1}] Uploaded {2} in {3:N0}s" -f $n, $total, $f.Name, $sw.Elapsed.TotalSeconds)
}
$ErrorActionPreference = $prevEap
Write-Host "Uploaded $total installer(s) to https://github.com/SensorsINI/jaer/releases/tag/$Tag"
Write-Host "Sample zip: ant upload-sample-data. Then ant copy-updates-xml after publish."
