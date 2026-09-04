# Upload install4j media from currentInstallers/<VERSION.txt>/ to the GitHub Release for that tag.
# Requires: gh auth, VERSION.txt, media built by `ant release`.
# Creates a *draft* GitHub Release if the tag has none (not Latest until published).
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

$dir = Join-Path $root "currentInstallers\$Tag"
if (-not (Test-Path $dir)) { throw "Missing $dir -- run ant release first" }

$installers = @(Get-ChildItem -Path $dir -File | Where-Object {
    $_.Name -match '^jAER_(windows-x64|macos|unix)_.*\.(exe|dmg|sh)$'
})
if (-not $installers) { throw "No jAER_windows-x64_*.exe / jAER_macos_*.dmg / jAER_unix_*.sh under $dir" }
$sampleZip = Join-Path $dir "jaer-sample-data.zip"
$files = @($installers)
if (Test-Path -LiteralPath $sampleZip) {
    $files += Get-Item -LiteralPath $sampleZip
} else {
    Write-Host "WARNING: $sampleZip missing — run ant pack-sample-data (or ant release) before upload so Latest has the sample-data asset."
}

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
$notesFile = Join-Path $root "release-notes\jaer-$Tag-release-notes.md"
if ($releaseMissing) {
    Write-Host "Creating GitHub draft release $Tag (publish later; not Latest)"
    if (Test-Path $notesFile) {
        gh release create $Tag --draft --latest=false --title "jaer-$Tag" --notes-file $notesFile
    } else {
        gh release create $Tag --draft --latest=false --title "jaer-$Tag" --notes "jAER $Tag installers. See release-notes/."
    }
} else {
    Write-Host "Leaving GitHub release body unchanged (use ant upload-release-notes to push notes)."
}
# One file at a time so the console shows which asset is in flight.
# GH_SPINNER_DISABLED replaces the clock-hand spinner with a text progress line.
$env:GH_SPINNER_DISABLED = "yes"
$items = @($files)
$total = $items.Count
$n = 0
$prevEap = $ErrorActionPreference
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
Write-Host "Then: commit updates.xml (ant copy-updates-xml) and prune older assets (scripts/prune-old-release-assets.ps1)."
