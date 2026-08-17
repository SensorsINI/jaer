# Delete jAER installer assets from GitHub Releases older than the last -Keep tags.
# Keeps release notes and git tags. Does not touch the newest -Keep releases.
# Usage (repo root):
#   powershell -File scripts/prune-old-release-assets.ps1
#   powershell -File scripts/prune-old-release-assets.ps1 -Keep 3 -WhatIf

param(
    [int]$Keep = 3,
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"
if ($Keep -lt 1) { throw "-Keep must be >= 1" }

$json = gh release list --limit 50 --json tagName,publishedAt,isDraft,isPrerelease | ConvertFrom-Json
$releases = $json | Where-Object { -not $_.isDraft } | Sort-Object { [datetime]$_.publishedAt } -Descending
if ($releases.Count -le $Keep) {
    Write-Host "Only $($releases.Count) release(s); nothing to prune (keep $Keep)."
    return
}

$toPrune = $releases | Select-Object -Skip $Keep
Write-Host "Keeping newest $Keep tag(s): $(($releases | Select-Object -First $Keep | ForEach-Object { $_.tagName }) -join ', ')"
foreach ($rel in $toPrune) {
    $tag = $rel.tagName
    $assets = gh release view $tag --json assets | ConvertFrom-Json
    $installers = @($assets.assets | Where-Object { $_.name -match '^jAER_.*\.(exe|dmg|sh)$' })
    if (-not $installers) {
        Write-Host "No installer assets on $tag"
        continue
    }
    foreach ($a in $installers) {
        if ($WhatIf) {
            Write-Host "WhatIf: would delete $($a.name) from $tag"
        } else {
            Write-Host "Deleting $($a.name) from $tag"
            gh release delete-asset $tag $a.name --yes
        }
    }
}
Write-Host "Done. Historical installers (if any) remain on Dropbox; GitHub keeps notes/tags."
