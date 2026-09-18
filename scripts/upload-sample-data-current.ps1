# Pack output currentInstallers/<VERSION.txt>/jaer-sample-data.zip onto GitHub
# Release tag sample-data-current. Creates that Release if missing (prerelease,
# never Latest). Clobbers a previous zip. Does not attach to VERSION.txt.
# Usage (repo root):
#   powershell -File scripts/upload-sample-data-current.ps1
#   powershell -File scripts/upload-sample-data-current.ps1 -WhatIf
# ASCII only: PS 5.1 treats UTF-8 em-dash bytes as a string terminator.

param(
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$version = (Get-Content -Raw VERSION.txt).Trim()
if (-not $version) { throw "VERSION.txt is empty" }

$releaseTag = "sample-data-current"
$zip = Join-Path $root "currentInstallers\$version\jaer-sample-data.zip"
if (-not (Test-Path -LiteralPath $zip -PathType Leaf)) {
    throw ("Missing {0} - run ant pack-sample-data first (needs recordings in sampleData/)" -f $zip)
}

$item = Get-Item -LiteralPath $zip
$mb = [math]::Round($item.Length / 1MB, 1)
Write-Host ("Zip from VERSION.txt {0}: {1} ({2} MB)" -f $version, $item.FullName, $mb)
Write-Host ("GitHub Release tag: {0}" -f $releaseTag)

$notes = "Durable jaer-sample-data.zip copied onto each product/rc Release by release.yml. Not a product version. Never mark this Release Latest."

if ($WhatIf) {
    Write-Host ("WhatIf: would create-or-update Release {0} with {1}" -f $releaseTag, $item.FullName)
    return
}

$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
gh release view $releaseTag --json tagName 2>$null | Out-Null
$releaseMissing = ($LASTEXITCODE -ne 0)
$ErrorActionPreference = $prevEap

$env:GH_SPINNER_DISABLED = "yes"
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$ErrorActionPreference = "Continue"
if ($releaseMissing) {
    Write-Host ("Creating GitHub Release {0} (prerelease, --latest=false) ..." -f $releaseTag)
    gh release create $releaseTag $item.FullName --title "jAER sample recordings" --notes $notes --prerelease --latest=false
    $code = $LASTEXITCODE
} else {
    Write-Host ("Uploading {0} ({1} MB) to existing {2} ..." -f $item.Name, $mb, $releaseTag)
    gh release upload $releaseTag $item.FullName --clobber
    $code = $LASTEXITCODE
}
$ErrorActionPreference = $prevEap
$sw.Stop()
if ($code -ne 0) {
    throw ("gh failed for Release {0} (exit {1})" -f $releaseTag, $code)
}
Write-Host ("Done in {0:N0}s" -f $sw.Elapsed.TotalSeconds)
Write-Host ("Release: https://github.com/SensorsINI/jaer/releases/tag/{0}" -f $releaseTag)
Write-Host "GitHub Latest is still the product Release (e.g. 3.5.0). This tag is only a zip bucket for release.yml; do not mark it Latest."
