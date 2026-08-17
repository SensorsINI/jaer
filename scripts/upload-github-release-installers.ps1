# Upload install4j media from installers/<VERSION.txt>/ to the GitHub Release for that tag.
# Requires: gh auth, VERSION.txt, media built by `ant release`.
# Usage (repo root):
#   powershell -File scripts/upload-github-release-installers.ps1
#   powershell -File scripts/upload-github-release-installers.ps1 -Tag 3.2.0
#   powershell -File scripts/upload-github-release-installers.ps1 -WhatIf

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

$dir = Join-Path $root "installers\$Tag"
if (-not (Test-Path $dir)) { throw "Missing $dir -- run ant release first" }

$files = Get-ChildItem -Path $dir -File | Where-Object {
    $_.Name -match '^jAER_(windows-x64|macos|unix)_.*\.(exe|dmg|sh)$'
}
if (-not $files) { throw "No jAER_windows-x64_*.exe / jAER_macos_*.dmg / jAER_unix_*.sh under $dir" }

Write-Host "Release tag: $Tag"
$files | ForEach-Object { Write-Host ("  {0} ({1:N1} MB)" -f $_.Name, ($_.Length / 1MB)) }

$paths = @($files | ForEach-Object { $_.FullName })
if ($WhatIf) {
    Write-Host "WhatIf: would upload $($paths.Count) file(s) to release $Tag"
    $paths | ForEach-Object { Write-Host "  $_" }
    return
}

# PS 5.1 + ErrorActionPreference Stop treats native stderr as terminating;
# "release not found" is expected when creating the first 3.x GitHub release.
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
gh release view $Tag --json tagName 2>$null | Out-Null
$releaseMissing = ($LASTEXITCODE -ne 0)
$ErrorActionPreference = $prevEap
if ($releaseMissing) {
    Write-Host "Creating GitHub release $Tag"
    gh release create $Tag --title "jaer-$Tag" --notes "jAER $Tag installers. See release-notes/."
}
gh release upload $Tag @paths --clobber
Write-Host "Uploaded $($files.Count) installer(s) to https://github.com/SensorsINI/jaer/releases/tag/$Tag"
Write-Host "Then: commit updates.xml (ant copy-updates-xml) and prune older assets (scripts/prune-old-release-assets.ps1)."
