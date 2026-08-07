# Read local signpath/*.txt and set GitHub Actions secrets/variables via gh.
# Nothing is written into git. Requires: gh auth login, repo write access.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $root 'signpath'

function Read-FirstMeaningfulLine([string]$path) {
    if (-not (Test-Path $path)) { throw "Missing file: $path" }
    Get-Content -Path $path | ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and ($_ -notmatch '^\s*#') } |
        Select-Object -First 1
}

function Read-Uuid([string]$path) {
    if (-not (Test-Path $path)) { throw "Missing file: $path" }
    $text = Get-Content -Raw -Path $path
    $m = [regex]::Match($text, '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}')
    if (-not $m.Success) { throw "No UUID found in $path" }
    return $m.Value
}

$orgId = Read-Uuid (Join-Path $dir 'signpath-organization-id.txt')
$token = Read-FirstMeaningfulLine (Join-Path $dir 'signpath-api-token.txt')
$license = Read-FirstMeaningfulLine (Join-Path $dir 'install4j-license.txt')
$project = Read-FirstMeaningfulLine (Join-Path $dir 'signpath-project-slug.txt')
$policy = Read-FirstMeaningfulLine (Join-Path $dir 'signpath-signing-policy-slug.txt')

if (-not $token) { throw 'signpath-api-token.txt is empty' }
if (-not $license) { throw 'install4j-license.txt is empty' }
if (-not $project) { $project = 'jaer' }
if (-not $policy) { $policy = 'test-signing' }

Push-Location $root
try {
    Write-Host "Setting GitHub Actions secrets/variables from signpath/ ..."
    $token | gh secret set SIGNPATH_API_TOKEN
    $license | gh secret set INSTALL4J_LICENSE
    gh variable set SIGNPATH_ORGANIZATION_ID --body $orgId
    # Workflow hardcodes project/policy; variables kept for documentation / future use.
    gh variable set SIGNPATH_PROJECT_SLUG --body $project
    gh variable set SIGNPATH_SIGNING_POLICY_SLUG --body $policy
    Write-Host "Done."
    Write-Host "  SIGNPATH_ORGANIZATION_ID=$orgId"
    Write-Host "  SIGNPATH_PROJECT_SLUG=$project"
    Write-Host "  SIGNPATH_SIGNING_POLICY_SLUG=$policy"
    Write-Host "  SIGNPATH_API_TOKEN / INSTALL4J_LICENSE set (values not printed)"
} finally {
    Pop-Location
}
