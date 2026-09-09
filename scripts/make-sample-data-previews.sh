#!/usr/bin/env bash
# Clip source MP4/AVI (or mov/mkv/webm) to looping 240px-wide WebP previews for
# sampleData/README.md. Name each source like the .aedat4 (same stem).
#
# Usage (repo root):
#   bash scripts/make-sample-data-previews.sh
#   bash scripts/make-sample-data-previews.sh --src ~/exports
#   bash scripts/make-sample-data-previews.sh --force --duration 5 --width 240
#
# Looks for sources in --src, then sampleData/preview-src/, then sampleData/.
# Optional start times: sampleData/previews/offsets.txt
#   <aedat4 stem> <seconds>
# (stem may contain spaces; last token is the start time.)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

WIDTH=240
DURATION=5
FPS=12
QUALITY=50
START_DEFAULT=0
FORCE=0
SRC_DIR=""
SAMPLE="$ROOT/sampleData"
OUT="$SAMPLE/previews"
OFFSETS="$OUT/offsets.txt"

usage() {
  echo "Usage: $0 [--src DIR] [--out DIR] [--width N] [--duration SEC] [--fps N] [--quality 0-100] [--force]" >&2
}

while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --src)
      SRC_DIR="${2:?--src requires a directory}"
      shift 2
      ;;
    --out)
      OUT="${2:?--out requires a directory}"
      shift 2
      ;;
    --width)
      WIDTH="${2:?--width requires a value}"
      shift 2
      ;;
    --duration)
      DURATION="${2:?--duration requires a value}"
      shift 2
      ;;
    --fps)
      FPS="${2:?--fps requires a value}"
      shift 2
      ;;
    --quality)
      QUALITY="${2:?--quality requires a value}"
      shift 2
      ;;
    --force|-f)
      FORCE=1
      shift
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg not found on PATH" >&2
  exit 1
fi

if [ ! -d "$SAMPLE" ]; then
  echo "Missing $SAMPLE" >&2
  exit 1
fi

mkdir -p "$OUT"

offset_for() {
  local stem="$1"
  local start="$START_DEFAULT"
  if [ ! -f "$OFFSETS" ]; then
    printf '%s' "$start"
    return
  fi
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    line="${line//$'\t'/ }"
    case "$line" in
      ''|\#*) continue ;;
    esac
    local rest="${line##* }"
    case "$rest" in
      ''|*[!0-9.]*) continue ;;
    esac
    local name="${line% "$rest"}"
    name="${name%"${name##*[![:space:]]}"}"
    if [ "$name" = "$stem" ]; then
      start="$rest"
      break
    fi
  done < "$OFFSETS"
  printf '%s' "$start"
}

find_source() {
  local stem="$1"
  local ext dir candidate
  local dirs=()
  if [ -n "$SRC_DIR" ]; then
    dirs+=("$SRC_DIR")
  fi
  dirs+=("$SAMPLE/preview-src" "$SAMPLE")
  for dir in "${dirs[@]}"; do
    [ -d "$dir" ] || continue
    for ext in mp4 avi mov mkv webm; do
      candidate="$dir/$stem.$ext"
      if [ -f "$candidate" ]; then
        printf '%s' "$candidate"
        return 0
      fi
    done
  done
  return 1
}

encode_one() {
  local src="$1"
  local dst="$2"
  local start="$3"
  local fmt=()
  case "${src##*.}" in
    avi|AVI) fmt=(-f avi) ;;
  esac
  ffmpeg -hide_banner -y \
    -ss "$start" -t "$DURATION" "${fmt[@]}" -i "$src" \
    -vf "fps=${FPS},scale=${WIDTH}:-2:flags=lanczos" \
    -an -loop 0 \
    -c:v libwebp -quality "$QUALITY" -compression_level 6 \
    "$dst"
}

AEDATS=()
for f in "$SAMPLE"/*.aedat4; do
  [ -f "$f" ] || continue
  AEDATS+=("$f")
done
if [ ${#AEDATS[@]} -eq 0 ]; then
  echo "No .aedat4 files in $SAMPLE — drop recordings (or only the matching source videos in preview-src) first." >&2
  echo "Sources are matched by the .aedat4 stem listed in sampleData/README.md." >&2
  exit 1
fi

ok=0
skip=0
miss=0
fail=0
for rec in "${AEDATS[@]}"; do
  base="${rec##*/}"
  stem="${base%.aedat4}"
  dst="$OUT/$stem.webp"
  src=""
  if src=$(find_source "$stem"); then
    :
  else
    echo "missing source for: $stem  (looked for .mp4/.avi/.mov/.mkv/.webm)"
    miss=$((miss + 1))
    continue
  fi
  if [ "$FORCE" -eq 0 ] && [ -f "$dst" ] && [ "$dst" -nt "$src" ]; then
    echo "skip (up to date): ${dst##*/}"
    skip=$((skip + 1))
    continue
  fi
  start="$(offset_for "$stem")"
  echo "encode  ${src##*/}  ->  ${dst##*/}  (${DURATION}s from ${start}s, ${WIDTH}px wide)"
  if encode_one "$src" "$dst" "$start"; then
    bytes=$(wc -c < "$dst" | tr -d '[:space:]')
    echo "  wrote $bytes bytes"
    ok=$((ok + 1))
  else
    echo "  ffmpeg failed" >&2
    fail=$((fail + 1))
  fi
done

echo "done: encoded=$ok skipped=$skip missing=$miss failed=$fail -> $OUT"
if [ "$ok" -eq 0 ] && [ "$skip" -eq 0 ]; then
  echo "Drop MP4/AVI files named like the .aedat4 stems into $SAMPLE/preview-src/ and re-run." >&2
  exit 1
fi
if [ "$fail" -gt 0 ]; then
  exit 1
fi
