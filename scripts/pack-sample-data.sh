#!/usr/bin/env bash
# Zip curated sampleData recordings and write SIZE.txt (zip + unpacked bytes).
# Usage (repo root): bash scripts/pack-sample-data.sh [--force]
# Skip zipping when jaer-sample-data.zip exists and a name+size stamp of recordings matches.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

FORCE=0
for arg in "$@"; do
  case "$arg" in
    --force) FORCE=1 ;;
  esac
done

SAMPLE="$ROOT/sampleData"
if [ ! -d "$SAMPLE" ]; then
  echo "Missing $SAMPLE — create it and add recordings" >&2
  exit 1
fi

is_meta() {
  case "$1" in
    README.md|SIZE.txt|.gitignore|.gitattributes) return 0 ;;
    *) return 1 ;;
  esac
}

FILES=()
for f in "$SAMPLE"/*; do
  [ -f "$f" ] || continue
  n="${f##*/}"
  if is_meta "$n"; then
    continue
  fi
  FILES+=("$n")
done
if [ ${#FILES[@]} -eq 0 ]; then
  echo "No recordings in $SAMPLE (only README/SIZE). Drop files there first." >&2
  exit 1
fi

VERSION="$(tr -d '[:space:]' < VERSION.txt)"
if [ -z "$VERSION" ]; then
  echo "VERSION.txt is empty" >&2
  exit 1
fi
OUT="$ROOT/currentInstallers/$VERSION"
mkdir -p "$OUT"
ZIP="$OUT/jaer-sample-data.zip"

file_bytes() {
  wc -c < "$1" | tr -d '[:space:]'
}

contents_stamp() {
  printf 'store\n'
  for n in "${FILES[@]}"; do
    printf '%s\t%s\n' "$n" "$(file_bytes "$SAMPLE/$n")"
  done | LC_ALL=C sort
}

STAMP="$ZIP.contents"
STAMP_NOW=$(contents_stamp)
if [ "$FORCE" -eq 0 ] && [ -f "$ZIP" ] && [ -f "$STAMP" ]; then
  STAMP_PREV=$(tr -d '\r' < "$STAMP")
  if [ "$STAMP_PREV" = "$STAMP_NOW" ]; then
    echo "sampleData unchanged (name+size); keeping $ZIP"
    exit 0
  fi
fi

unpacked=0
for n in "${FILES[@]}"; do
  unpacked=$((unpacked + $(file_bytes "$SAMPLE/$n")))
done
if [ -f "$SAMPLE/README.md" ]; then
  unpacked=$((unpacked + $(file_bytes "$SAMPLE/README.md")))
fi

mib() {
  awk -v b="$1" 'BEGIN { printf "%d", (b / 1024 / 1024) + 0.5 }'
}

STAGING=$(mktemp -d "${TMPDIR:-/tmp}/jaer-sample-data.XXXXXX")
cleanup() { rm -rf "$STAGING"; }
trap cleanup EXIT
if [ -f "$SAMPLE/README.md" ]; then
  cp "$SAMPLE/README.md" "$STAGING/"
fi
for n in "${FILES[@]}"; do
  cp "$SAMPLE/$n" "$STAGING/"
done
rm -f "$ZIP"
(cd "$STAGING" && zip -0 -q -r "$ZIP" .)

zip_bytes=$(file_bytes "$ZIP")
zip_mib=$(mib "$zip_bytes")
unpacked_mib=$(mib "$unpacked")

{
  echo "zipBytes=$zip_bytes"
  echo "zipMiB=$zip_mib"
  echo "unpackedBytes=$unpacked"
  echo "unpackedMiB=$unpacked_mib"
} > "$SAMPLE/SIZE.txt"

TABLE=$'| File | Size |\n|------|------|\n'
for n in "${FILES[@]}"; do
  b=$(file_bytes "$SAMPLE/$n")
  mb=$(awk -v b="$b" 'BEGIN { printf "%.1f MB", b / 1024 / 1024 }')
  TABLE+="| \`$n\` | $mb |"$'\n'
done

README="$SAMPLE/README.md"
START='<!-- SAMPLE-DATA-CONTENTS -->'
END='<!-- /SAMPLE-DATA-CONTENTS -->'
if grep -q "$START" "$README" && grep -q "$END" "$README"; then
  BLOCK=$(printf '\n\nDownload **%s MB**, about **%s MB** on disk.\n\n%s\n' "$zip_mib" "$unpacked_mib" "$TABLE")
  awk -v start="$START" -v end="$END" -v block="$BLOCK" '
    $0 ~ start { print; printf "%s", block; skip=1; next }
    $0 ~ end { skip=0 }
    skip { next }
    { print }
  ' "$README" > "$README.tmp"
  mv "$README.tmp" "$README"
fi

printf '%s\n' "$STAMP_NOW" > "$STAMP"

echo "Packed ${#FILES[@]} recording(s): ${zip_mib} MB download, ${unpacked_mib} MB unpacked -> $ZIP"
