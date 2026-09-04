# Zip curated sampleData recordings and write SIZE.txt (zip + unpacked bytes).
# Usage (repo root): powershell -File scripts/pack-sample-data.ps1 [-Force] [-WhatIf]
# Skip zipping when jaer-sample-data.zip exists and a name+size stamp of recordings matches.
param(
    [switch]$WhatIf,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$sampleDir = Join-Path $root "sampleData"
if (-not (Test-Path -LiteralPath $sampleDir -PathType Container)) {
    throw "Missing $sampleDir - create it and add recordings"
}

$meta = @("README.md", "SIZE.txt", ".gitignore", ".gitattributes")
$recordings = Get-ChildItem -LiteralPath $sampleDir -File -Force | Where-Object {
    $meta -notcontains $_.Name
}
if (-not $recordings) {
    throw "No recordings in $sampleDir (only README/SIZE). Drop files there first."
}

$version = (Get-Content -Raw (Join-Path $root "VERSION.txt")).Trim()
if (-not $version) { throw "VERSION.txt is empty" }
$outDir = Join-Path $root "currentInstallers\$version"
if (-not (Test-Path -LiteralPath $outDir)) {
    New-Item -ItemType Directory -Path $outDir | Out-Null
}
$zipPath = Join-Path $outDir "jaer-sample-data.zip"

$unpackedBytes = 0L
foreach ($f in $recordings) { $unpackedBytes += $f.Length }
$readme = Join-Path $sampleDir "README.md"
if (Test-Path -LiteralPath $readme) {
    $unpackedBytes += (Get-Item -LiteralPath $readme).Length
}

function Format-Mib([long]$bytes) {
    return [int][Math]::Round($bytes / 1MB)
}

function Get-ContentsStamp {
    $names = New-Object System.Collections.Generic.List[string]
    foreach ($f in $recordings) {
        [void]$names.Add(("{0}`t{1}" -f $f.Name, $f.Length))
    }
    $arr = $names.ToArray()
    if ($arr.Length -gt 1) {
        [Array]::Sort($arr, [StringComparer]::Ordinal)
    }
    return ("store`n" + ($arr -join "`n") + "`n")
}

function Read-ContentsStamp([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    $t = [IO.File]::ReadAllText($path)
    return ($t -replace "`r`n", "`n" -replace "`r", "`n")
}

$stampPath = $zipPath + ".contents"
$stamp = Get-ContentsStamp
$zipExists = Test-Path -LiteralPath $zipPath
if (-not $Force -and $zipExists -and ((Read-ContentsStamp $stampPath) -eq $stamp)) {
    if ($WhatIf) {
        Write-Host ("WhatIf: would skip zip; sampleData name+size unchanged -> {0}" -f $zipPath)
        return
    }
    Write-Host ("sampleData unchanged (name+size); keeping {0}" -f $zipPath)
    return
}

$staging = Join-Path $root ("build\sample-data-zip-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $staging | Out-Null
try {
    Copy-Item -LiteralPath $readme -Destination (Join-Path $staging "README.md") -ErrorAction SilentlyContinue
    foreach ($f in $recordings) {
        Copy-Item -LiteralPath $f.FullName -Destination (Join-Path $staging $f.Name)
    }
    if ($WhatIf) {
        Write-Host ("WhatIf: would zip {0} recording(s), unpacked {1} bytes -> {2}" -f $recordings.Count, $unpackedBytes, $zipPath)
        return
    }
    if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::CreateFromDirectory(
        $staging,
        $zipPath,
        [System.IO.Compression.CompressionLevel]::NoCompression,
        $false)
} finally {
    Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
}

$zipBytes = (Get-Item -LiteralPath $zipPath).Length
$zipMiB = Format-Mib $zipBytes
$unpackedMiB = Format-Mib $unpackedBytes

$sizePath = Join-Path $sampleDir "SIZE.txt"
@"
zipBytes=$zipBytes
zipMiB=$zipMiB
unpackedBytes=$unpackedBytes
unpackedMiB=$unpackedMiB
"@ | Set-Content -LiteralPath $sizePath -Encoding ASCII

$nl = [Environment]::NewLine
$table = '| File | Size |' + $nl + '|------|------|'
foreach ($f in ($recordings | Sort-Object Name)) {
    $mb = '{0:N1} MB' -f ($f.Length / 1MB)
    $table = $table + $nl + '| `' + $f.Name + '` | ' + $mb + ' |'
}
$readmeText = Get-Content -LiteralPath $readme -Raw
$start = '<!-- SAMPLE-DATA-CONTENTS -->'
$end = '<!-- /SAMPLE-DATA-CONTENTS -->'
$i0 = $readmeText.IndexOf($start)
$i1 = $readmeText.IndexOf($end)
if ($i0 -ge 0 -and $i1 -gt $i0) {
    $before = $readmeText.Substring(0, $i0 + $start.Length)
    $after = $readmeText.Substring($i1)
    $intro = $nl + $nl + 'Download **' + $zipMiB + ' MB**, about **' + $unpackedMiB + ' MB** on disk.' + $nl + $nl
    $newReadme = $before + $intro + $table + $nl + $nl + $after
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($readme, $newReadme.TrimEnd() + $nl, $utf8)
}

$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[IO.File]::WriteAllText($stampPath, $stamp, $utf8NoBom)

Write-Host ("Packed {0} recording(s): {1} MB download, {2} MB unpacked -> {3}" -f $recordings.Count, $zipMiB, $unpackedMiB, $zipPath)
