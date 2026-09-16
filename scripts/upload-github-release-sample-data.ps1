# Upload currentInstallers/<tag>/jaer-sample-data.zip to the GitHub Release for that tag.
# Does not create a draft; the Release must already exist (ant create-draft-release
# or ant upload-installers). Clobbers a previous zip on the same tag.
# Usage (repo root):
#   powershell -File scripts/upload-github-release-sample-data.ps1
#   powershell -File scripts/upload-github-release-sample-data.ps1 -Tag 3.5.0
#   powershell -File scripts/upload-github-release-sample-data.ps1 -WhatIf
# ASCII only: PS 5.1 treats UTF-8 em-dash bytes as a string terminator.

param(
    [string]$Tag = "",
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

if (-not $Tag) {
    $Tag = (Get-Content -Raw VERSION.txt).Trim()
}
if (-not $Tag) { throw "VERSION.txt is empty and -Tag was not set" }

$zip = Join-Path $root "currentInstallers\$Tag\jaer-sample-data.zip"
if (-not (Test-Path -LiteralPath $zip -PathType Leaf)) {
    throw ("Missing {0} - run ant pack-sample-data (or ant upload-sample-data) first" -f $zip)
}

$item = Get-Item -LiteralPath $zip
$mb = [math]::Round($item.Length / 1MB, 1)
Write-Host ("Release tag: {0}" -f $Tag)
Write-Host ('  {0} ({1} MB)' -f $item.Name, $mb)

if ($WhatIf) {
    Write-Host ("WhatIf: would upload {0} to release {1}" -f $item.FullName, $Tag)
    return
}

$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
gh release view $Tag --json tagName 2>$null | Out-Null
$releaseMissing = ($LASTEXITCODE -ne 0)
$ErrorActionPreference = $prevEap
if ($releaseMissing) {
    throw ("GitHub Release {0} does not exist. Create it first: ant create-draft-release" -f $Tag)
}

$env:GH_SPINNER_DISABLED = "yes"
Write-Host ('Uploading {0} ({1} MB) ...' -f $item.Name, $mb)
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$ErrorActionPreference = "Continue"
gh release upload $Tag $item.FullName --clobber
$code = $LASTEXITCODE
$ErrorActionPreference = $prevEap
$sw.Stop()
if ($code -ne 0) {
    throw ("gh release upload failed for {0} (exit {1})" -f $item.Name, $code)
}
Write-Host ("Uploaded {0} in {1:N0}s" -f $item.Name, $sw.Elapsed.TotalSeconds)
Write-Host "Sample data: https://github.com/SensorsINI/jaer/releases/latest/download/jaer-sample-data.zip"
Write-Host "If WebP thumbs changed, commit sampleData/previews/*.webp (they are not in the zip)."
