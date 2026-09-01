# Summarize GitHub Release asset download counts (needs gh auth).
# Usage (repo root):
#   powershell -File scripts/count-asset-downloads.ps1
#   powershell -File scripts/count-asset-downloads.ps1 -Tag 3.3.0
#   powershell -File scripts/count-asset-downloads.ps1 -Limit 20 -AllAssets

param(
    [string]$Tag = "",
    [int]$Limit = 50,
    [string]$AllAssets = "false"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

if ($Limit -lt 1) { throw "Limit must be >= 1" }
$includeAll = ($AllAssets -eq "true" -or $AllAssets -eq "1")

function Test-InstallerName([string]$name) {
    return $name -match '^jAER_.*\.(exe|dmg|sh)$'
}

function Get-Platform([string]$name) {
    if ($name -match 'windows') { return "windows-x64" }
    if ($name -match 'macos_aarch64') { return "macos-aarch64" }
    if ($name -match 'macos') { return "macos-intel" }
    if ($name -match 'unix') { return "linux" }
    return "other"
}

$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
gh auth status 2>&1 | Out-Null
$authOk = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = $prevEap
if (-not $authOk) {
    throw "gh is not authenticated. Run: gh auth login"
}

$repo = (gh api repos/:owner/:repo --jq .full_name).Trim()
if (-not $repo) { throw "Could not resolve GitHub repo from gh" }

$releases = @()
if ($Tag) {
    $ErrorActionPreference = "Continue"
    $one = gh api "repos/:owner/:repo/releases/tags/$Tag" 2>&1
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($code -ne 0) { throw "No GitHub Release for tag $Tag" }
    $releases = @($one | ConvertFrom-Json)
} else {
    $ErrorActionPreference = "Continue"
    $ndjson = @(gh api --paginate repos/:owner/:repo/releases --jq ".[] | {tag_name, draft, prerelease, published_at, assets: [.assets[]? | {name, download_count, size}]}")
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($code -ne 0) { throw "gh api releases failed" }
    foreach ($line in $ndjson) {
        $line = [string]$line
        $line = $line.Trim()
        if (-not $line) { continue }
        $releases += ($line | ConvertFrom-Json)
    }
}

$shown = 0
$byPlatform = @{}
$grand = 0
$assetRows = 0

Write-Host "GitHub release asset downloads  $repo"
if ($includeAll) {
    Write-Host "Assets: all files  Limit: $Limit"
} else {
    Write-Host "Assets: jAER installer media (exe/dmg/sh)  Limit: $Limit"
}
Write-Host ""

foreach ($r in $releases) {
    if ($r.draft) { continue }
    if (-not $Tag) {
        if ($shown -ge $Limit) { break }
        $shown++
    }

    $assets = @($r.assets)
    if (-not $includeAll) {
        $assets = @($assets | Where-Object { Test-InstallerName $_.name })
    }
    $assets = @($assets | Sort-Object { - [int]$_.download_count }, name)

    $pub = $r.published_at
    if ($pub) { $pub = ([string]$pub).Substring(0, [Math]::Min(10, ([string]$pub).Length)) }
    $flags = @()
    if ($r.prerelease) { $flags += "prerelease" }
    $flagText = ""
    if ($flags.Count -gt 0) { $flagText = "  (" + ($flags -join ", ") + ")" }

    $relTotal = 0
    foreach ($a in $assets) { $relTotal += [int]$a.download_count }

    Write-Host ("{0}  {1}{2}  release total: {3}" -f $r.tag_name, $pub, $flagText, $relTotal)
    if ($assets.Count -eq 0) {
        Write-Host "  (no matching assets)"
        Write-Host ""
        continue
    }
    foreach ($a in $assets) {
        $n = [int]$a.download_count
        $mb = [double]$a.size / 1MB
        Write-Host ("  {0,8}  {1,-42}  {2,7:N1} MB" -f $n, $a.name, $mb)
        $p = Get-Platform $a.name
        if (-not $byPlatform.ContainsKey($p)) { $byPlatform[$p] = 0 }
        $byPlatform[$p] += $n
        $grand += $n
        $assetRows++
    }
    Write-Host ""
}

if (-not $Tag -and $shown -eq 0) {
    Write-Host "No published releases found."
    return
}

Write-Host "Totals"
$order = @("windows-x64", "macos-intel", "macos-aarch64", "linux", "other")
foreach ($p in $order) {
    if ($byPlatform.ContainsKey($p)) {
        Write-Host ("  {0,-16} {1,8}" -f $p, $byPlatform[$p])
    }
}
Write-Host ("  {0,-16} {1,8}  ({2} asset(s))" -f "GRAND TOTAL", $grand, $assetRows)
Write-Host ""
Write-Host "Counts are GitHub's per-asset download_count (each completed download of that file)."
Write-Host "Re-uploads keep the same asset id when possible; replacing an asset may reset its count."
