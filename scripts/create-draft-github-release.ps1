# Annotated git tag VERSION.txt at HEAD, push it, and create a GitHub *draft* Release.
# Does not publish (not Latest). Publish later in the GitHub UI or:
#   gh release edit <tag> --draft=false
# Requires: git, gh auth. Does not upload installer assets.
# Usage (repo root):
#   powershell -File scripts/create-draft-github-release.ps1
#   powershell -File scripts/create-draft-github-release.ps1 -Tag 3.4.0
#   powershell -File scripts/create-draft-github-release.ps1 -WhatIf

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

$head = (git rev-parse HEAD).Trim()
if (-not $head) { throw "git rev-parse HEAD failed" }

$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
gh auth status 2>&1 | Out-Null
$authOk = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = $prevEap
if (-not $authOk) {
    throw "gh is not authenticated. Run: gh auth login"
}

$notesFile = Join-Path $root "release-notes\jaer-$Tag-release-notes.md"
Write-Host "HEAD: $head"
Write-Host "Tag:  $Tag (draft GitHub Release; not Latest until you publish)"

$ErrorActionPreference = "Continue"
git rev-parse -q --verify "refs/tags/$Tag" 2>$null | Out-Null
$tagExists = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = $prevEap

if ($tagExists) {
    $tagged = (git rev-parse "refs/tags/${Tag}^{commit}").Trim()
    if ($tagged -ne $head) {
        throw "Tag $Tag already points at $tagged, not HEAD $head. Move or delete the tag first (see docs/README-releasing-tagging.md)."
    }
    Write-Host "Local tag $Tag already on HEAD"
} else {
    if ($WhatIf) {
        Write-Host "WhatIf: would git tag -a $Tag -m `"jAER $Tag`""
    } else {
        $ErrorActionPreference = "Continue"
        git tag -a $Tag -m "jAER $Tag"
        $code = $LASTEXITCODE
        $ErrorActionPreference = $prevEap
        if ($code -ne 0) { throw "git tag failed" }
        Write-Host "Created annotated tag $Tag"
    }
}

if ($WhatIf) {
    Write-Host "WhatIf: would git push origin refs/tags/$Tag"
} else {
    $ErrorActionPreference = "Continue"
    git push origin "refs/tags/$Tag"
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($code -ne 0) { throw "git push origin refs/tags/$Tag failed" }
    Write-Host "Pushed tag $Tag to origin"
}

$ErrorActionPreference = "Continue"
gh release view $Tag --json isDraft,isPrerelease,url 2>$null | Out-Null
$releaseMissing = ($LASTEXITCODE -ne 0)
$ErrorActionPreference = $prevEap

if ($WhatIf) {
    if ($releaseMissing) {
        Write-Host "WhatIf: would gh release create $Tag --draft --latest=false"
    } else {
        Write-Host "WhatIf: GitHub Release $Tag already exists (would leave draft/published state as-is; update notes if present)"
    }
    return
}

if ($releaseMissing) {
    Write-Host "Creating GitHub draft release $Tag"
    $ErrorActionPreference = "Continue"
    if (Test-Path $notesFile) {
        gh release create $Tag --draft --latest=false --title "jaer-$Tag" --notes-file $notesFile --target $head
    } else {
        gh release create $Tag --draft --latest=false --title "jaer-$Tag" --notes "jAER $Tag (draft). See release-notes/." --target $head
    }
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($code -ne 0) { throw "gh release create failed" }
} else {
    $meta = gh release view $Tag --json isDraft,url | ConvertFrom-Json
    if ($meta.isDraft) {
        Write-Host "GitHub Release $Tag is already a draft: $($meta.url)"
        if (Test-Path $notesFile) {
            gh release edit $Tag --notes-file $notesFile
        }
    } else {
        Write-Host "WARNING: GitHub Release $Tag already exists and is published (not converting to draft): $($meta.url)"
        if (Test-Path $notesFile) {
            gh release edit $Tag --notes-file $notesFile
        }
    }
}

Write-Host "Draft: https://github.com/SensorsINI/jaer/releases (publish there when ready)"
Write-Host "Or:    gh release edit $Tag --draft=false"
Write-Host "Upload installers after media build: scripts/upload-github-release-installers.ps1"
