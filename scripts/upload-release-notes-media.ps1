# Upload <!-- webp: relpath --> files to GitHub user-attachments and insert <img> lines.
# Unofficial endpoint (needs gh auth with push on this repo). Skips tags that already
# have a following user-attachments URL. Does not push the GitHub Release body;
# that is ant upload-release-notes.
# Usage (repo root):
#   powershell -File scripts/upload-release-notes-media.ps1
#   powershell -File scripts/upload-release-notes-media.ps1 -Tag 3.4.0
#   powershell -File scripts/upload-release-notes-media.ps1 -WhatIf

param(
    [string]$Tag = "",
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$MaxBytes = 10 * 1024 * 1024

if (-not $Tag) {
    $Tag = (Get-Content -Raw VERSION.txt).Trim()
}
if (-not $Tag) { throw "VERSION.txt is empty and -Tag was not set" }

$notes = Join-Path $root "release-notes\jaer-$Tag-release-notes.md"
if (-not (Test-Path -LiteralPath $notes)) { throw "Missing $notes" }

function Get-UrlEncodedName([string]$Name) {
    return [uri]::EscapeDataString($Name)
}

function Send-ReleaseNotesWebp([string]$FilePath) {
    $name = [IO.Path]::GetFileName($FilePath)
    $enc = Get-UrlEncodedName $name
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $repoName = (gh repo view --json nameWithOwner --jq .nameWithOwner)
    if ($LASTEXITCODE -ne 0 -or -not $repoName) {
        $ErrorActionPreference = $prevEap
        throw "gh repo view failed (need gh auth)"
    }
    $repoId = (gh api "repos/$repoName" --jq .id)
    if ($LASTEXITCODE -ne 0 -or -not $repoId) {
        $ErrorActionPreference = $prevEap
        throw "gh api repos/$repoName failed"
    }
    $token = (gh auth token)
    if ($LASTEXITCODE -ne 0 -or -not $token) {
        $ErrorActionPreference = $prevEap
        throw "gh auth token failed"
    }
    $url = "https://uploads.github.com/user-attachments/assets?name=$enc&content_type=image/webp&repository_id=$repoId"
    $tmp = [IO.Path]::GetTempFileName()
    $code = & curl.exe -sS -o $tmp -w "%{http_code}" -X POST `
        -H "Authorization: Bearer $token" `
        -H "Content-Type: image/webp" `
        --data-binary "@$FilePath" `
        $url
    $curlExit = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    $token = $null
    $body = Get-Content -Raw -LiteralPath $tmp
    Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
    if ($curlExit -ne 0) { throw "curl.exe failed for $name (exit $curlExit)" }
    if ($code -ne "201" -and $code -ne "200") {
        throw "Upload failed for $name (HTTP $code): $body"
    }
    $json = $body | ConvertFrom-Json
    $href = $json.url
    if (-not $href) { $href = $json.href }
    if (-not $href) { throw "Upload of $name returned no url/href" }
    return [string]$href
}

$lines = @(Get-Content -LiteralPath $notes)
Write-Host "Release notes: $notes"

$out = New-Object System.Collections.Generic.List[string]
$uploaded = 0
$skipped = 0
$pending = 0
$tagRe = [regex]'^<!-- webp: (\S+) -->$'

for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    $m = $tagRe.Match($line)
    if (-not $m.Success) {
        [void]$out.Add($line)
        continue
    }
    $rel = $m.Groups[1].Value
    $local = Join-Path $root (Join-Path "release-notes" ($rel -replace '/', '\'))
    [void]$out.Add($line)
    $nxt = $null
    for ($j = $i + 1; $j -lt $lines.Count; $j++) {
        if ($lines[$j] -match '\S') {
            $nxt = $lines[$j]
            break
        }
    }
    if ($nxt -and ($nxt -match 'user-attachments')) {
        Write-Host "skip $rel (already has user-attachments)"
        $skipped++
        continue
    }
    if (-not (Test-Path -LiteralPath $local)) {
        throw "Missing $local for <!-- webp: $rel -->"
    }
    $item = Get-Item -LiteralPath $local
    if ($item.Length -gt $MaxBytes) {
        throw "$local is $($item.Length) bytes; GitHub free image limit is $MaxBytes"
    }
    $mb = [math]::Round($item.Length / 1MB, 1)
    $alt = [IO.Path]::GetFileNameWithoutExtension($rel)
    if ($WhatIf) {
        Write-Host "WhatIf: would upload $rel ($mb MB)"
        $pending++
        continue
    }
    Write-Host "Uploading $rel ($mb MB) ..."
    $href = Send-ReleaseNotesWebp $item.FullName
    [void]$out.Add(('<img src="{0}" alt="{1}" width="80%" />' -f $href, $alt))
    Write-Host "  $href"
    $uploaded++
}

if ($WhatIf) {
    Write-Host "WhatIf: would upload $pending WebP(s); skip $skipped already attached"
    return
}

if ($uploaded -gt 0) {
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [IO.File]::WriteAllLines($notes, $out.ToArray(), $utf8)
    Write-Host "Wrote $uploaded <img> line(s) into $notes (skipped $skipped)"
} else {
    Write-Host "No new WebP uploads (skipped $skipped already attached)"
}
