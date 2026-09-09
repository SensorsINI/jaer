# Clip source MP4/AVI (or mov/mkv/webm) to looping 240px-wide WebP previews for
# sampleData/README.md. Name each source like the .aedat4 (same stem).
#
# Usage (repo root):
#   powershell -File scripts/make-sample-data-previews.ps1
#   powershell -File scripts/make-sample-data-previews.ps1 -Src $env:USERPROFILE\exports
#   powershell -File scripts/make-sample-data-previews.ps1 -Force
#
# Looks for sources in -Src, then sampleData/preview-src/, then sampleData/.
# Optional start times: sampleData/previews/offsets.txt
#   <aedat4 stem> <seconds>
# (stem may contain spaces; last token is the start time.)
param(
    [string]$Src = "",
    [string]$Out = "",
    [int]$Width = 240,
    [double]$Duration = 5,
    [int]$Fps = 12,
    [int]$Quality = 50,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$ffmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue
if (-not $ffmpeg) {
    $ffmpeg = Get-Command ffmpeg.exe -ErrorAction SilentlyContinue
}
if (-not $ffmpeg) {
    throw "ffmpeg not found on PATH"
}

$sampleDir = Join-Path $root "sampleData"
if (-not (Test-Path -LiteralPath $sampleDir -PathType Container)) {
    throw "Missing $sampleDir"
}

if (-not $Out) {
    $Out = Join-Path $sampleDir "previews"
}
$offsetsPath = Join-Path $Out "offsets.txt"
if (-not (Test-Path -LiteralPath $Out)) {
    New-Item -ItemType Directory -Path $Out | Out-Null
}

function Get-OffsetSeconds([string]$stem) {
    $start = 0
    if (-not (Test-Path -LiteralPath $offsetsPath -PathType Leaf)) {
        return $start
    }
    foreach ($raw in Get-Content -LiteralPath $offsetsPath) {
        $line = $raw.Trim()
        if (-not $line -or $line.StartsWith("#")) { continue }
        $parts = $line -split '\s+'
        if ($parts.Length -lt 2) { continue }
        $secTok = $parts[$parts.Length - 1]
        $sec = 0.0
        if (-not [double]::TryParse($secTok, [ref]$sec)) { continue }
        $name = ($parts[0..($parts.Length - 2)] -join " ")
        if ($name -eq $stem) {
            return $sec
        }
    }
    return $start
}

function Find-Source([string]$stem) {
    $dirs = New-Object System.Collections.Generic.List[string]
    if ($Src) { [void]$dirs.Add($Src) }
    [void]$dirs.Add((Join-Path $sampleDir "preview-src"))
    [void]$dirs.Add($sampleDir)
    $exts = @("mp4", "avi", "mov", "mkv", "webm")
    foreach ($dir in $dirs) {
        if (-not (Test-Path -LiteralPath $dir -PathType Container)) { continue }
        foreach ($ext in $exts) {
            $candidate = Join-Path $dir ($stem + "." + $ext)
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                return $candidate
            }
        }
    }
    return $null
}

$recordings = @(Get-ChildItem -LiteralPath $sampleDir -File -Filter "*.aedat4" -ErrorAction SilentlyContinue)
if (-not $recordings) {
    throw "No .aedat4 files in $sampleDir. Name sources like the stems in sampleData/README.md and put them in preview-src."
}

$ok = 0
$skip = 0
$miss = 0
$fail = 0
foreach ($rec in $recordings) {
    $stem = [IO.Path]::GetFileNameWithoutExtension($rec.Name)
    $dst = Join-Path $Out ($stem + ".webp")
    $srcPath = Find-Source $stem
    if (-not $srcPath) {
        Write-Host ("missing source for: {0}  (looked for .mp4/.avi/.mov/.mkv/.webm)" -f $stem)
        $miss++
        continue
    }
    if (-not $Force -and (Test-Path -LiteralPath $dst -PathType Leaf)) {
        $dstItem = Get-Item -LiteralPath $dst
        $srcItem = Get-Item -LiteralPath $srcPath
        if ($dstItem.LastWriteTimeUtc -ge $srcItem.LastWriteTimeUtc) {
            Write-Host ("skip (up to date): {0}" -f (Split-Path -Leaf $dst))
            $skip++
            continue
        }
    }
    $start = Get-OffsetSeconds $stem
    Write-Host ("encode  {0}  ->  {1}  ({2}s from {3}s, {4}px wide)" -f (Split-Path -Leaf $srcPath), (Split-Path -Leaf $dst), $Duration, $start, $Width)
    $vf = "fps=${Fps},scale=${Width}:-2:flags=lanczos"
    $ffArgs = @("-hide_banner", "-y", "-ss", "$start", "-t", "$Duration")
    if ([IO.Path]::GetExtension($srcPath).Equals(".avi", [StringComparison]::OrdinalIgnoreCase)) {
        $ffArgs += @("-f", "avi")
    }
    $ffArgs += @("-i", $srcPath, "-vf", $vf, "-an", "-loop", "0", "-c:v", "libwebp", "-quality", "$Quality", "-compression_level", "6", $dst)
    & $ffmpeg.Source @ffArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  ffmpeg failed"
        $fail++
        continue
    }
    $bytes = (Get-Item -LiteralPath $dst).Length
    Write-Host ("  wrote {0} bytes" -f $bytes)
    $ok++
}

Write-Host ("done: encoded={0} skipped={1} missing={2} failed={3} -> {4}" -f $ok, $skip, $miss, $fail, $Out)
if ($ok -eq 0 -and $skip -eq 0) {
    throw ("Drop MP4/AVI files named like the .aedat4 stems into {0} and re-run." -f (Join-Path $sampleDir "preview-src"))
}
if ($fail -gt 0) {
    throw "ffmpeg failed for one or more files"
}
