# Create bare stub files under signpath/ if missing (does not overwrite).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $root 'signpath'
New-Item -ItemType Directory -Force -Path $dir | Out-Null

$stubs = @{
    'signpath-organization-id.txt'     = ""
    'signpath-api-token.txt'           = ""
    'install4j-license.txt'            = ""
    'signpath-project-slug.txt'        = "jaer`n"
    'signpath-signing-policy-slug.txt' = "test-signing2`n"
}

foreach ($name in $stubs.Keys) {
    $path = Join-Path $dir $name
    if (-not (Test-Path $path)) {
        Set-Content -Path $path -Value $stubs[$name] -Encoding utf8
        Write-Host "Created $path"
    } else {
        Write-Host "Exists  $path"
    }
}

Write-Host "Fill in signpath/*.txt then run scripts/sync-signpath-secrets-to-github.ps1"
