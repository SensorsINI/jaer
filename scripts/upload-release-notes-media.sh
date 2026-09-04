#!/usr/bin/env bash
# Upload <!-- webp: relpath --> files to GitHub user-attachments and insert <img> lines.
# Unofficial endpoint (needs gh auth with push on this repo). Skips tags that already
# have a following user-attachments URL. Does not push the GitHub Release body;
# that is ant upload-release-notes.
# Usage (repo root):
#   bash scripts/upload-release-notes-media.sh
#   bash scripts/upload-release-notes-media.sh --tag 3.4.0
#   bash scripts/upload-release-notes-media.sh --what-if
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MAX_BYTES=$((10 * 1024 * 1024))

usage() {
  echo "Usage: $0 [--what-if|-WhatIf|-n] [--tag TAG | TAG]" >&2
}

WHATIF=0
TAG=""
while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    -n|--dry-run|--what-if|-WhatIf|--WhatIf)
      WHATIF=1
      shift
      ;;
    --tag)
      TAG="${2:?--tag requires a value}"
      shift 2
      ;;
    --tag=*)
      TAG="${1#--tag=}"
      shift
      ;;
    -*)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
    *)
      if [ -n "$TAG" ]; then
        echo "Unexpected argument: $1" >&2
        usage
        exit 1
      fi
      TAG="$1"
      shift
      ;;
  esac
done

if [ -z "$TAG" ]; then
  TAG="$(tr -d '[:space:]' < VERSION.txt)"
fi
if [ -z "$TAG" ]; then
  echo "VERSION.txt is empty and no tag was given" >&2
  exit 1
fi

NOTES="$ROOT/release-notes/jaer-${TAG}-release-notes.md"
if [ ! -f "$NOTES" ]; then
  echo "Missing $NOTES" >&2
  exit 1
fi

is_webp_tag() {
  [[ "$1" == '<!-- webp: '* && "$1" == *' -->' ]]
}

webp_relpath() {
  local line="$1"
  line="${line#<!-- webp: }"
  line="${line% -->}"
  printf '%s' "$line"
}

next_nonempty() {
  local -n _lines=$1
  local i="$2" n="${#_lines[@]}"
  local j=$((i + 1))
  while [ "$j" -lt "$n" ]; do
    if [[ "${_lines[$j]}" =~ [^[:space:]] ]]; then
      printf '%s' "${_lines[$j]}"
      return 0
    fi
    j=$((j + 1))
  done
  return 1
}

upload_webp() {
  local file="$1"
  local name enc repo_id tmp code url
  name="$(basename "$file")"
  enc="$(python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))' "$name")"
  repo_id="$(gh api "repos/$(gh repo view --json nameWithOwner --jq .nameWithOwner)" --jq .id)"
  tmp="$(mktemp)"
  code="$(
    curl -sS -o "$tmp" -w '%{http_code}' \
      -X POST \
      -H "Authorization: Bearer $(gh auth token)" \
      -H "Content-Type: image/webp" \
      --data-binary @"$file" \
      "https://uploads.github.com/user-attachments/assets?name=${enc}&content_type=image/webp&repository_id=${repo_id}"
  )" || true
  url="$(python3 -c 'import json,sys
p=sys.argv[1]
try:
  d=json.load(open(p, encoding="utf-8"))
except Exception:
  d={}
print((d.get("url") or d.get("href") or "").strip())' "$tmp")"
  if [ "$code" != "201" ] && [ "$code" != "200" ]; then
    echo "Upload failed for $name (HTTP $code)" >&2
    cat "$tmp" >&2 || true
    rm -f "$tmp"
    return 1
  fi
  rm -f "$tmp"
  if [ -z "$url" ]; then
    echo "Upload of $name returned no url/href" >&2
    return 1
  fi
  printf '%s' "$url"
}

mapfile -t LINES < "$NOTES"
n=${#LINES[@]}
OUT=()
uploaded=0
skipped=0
pending=0

echo "Release notes: $NOTES"

i=0
while [ "$i" -lt "$n" ]; do
  line="${LINES[$i]}"
  line="${line%$'\r'}"
  if ! is_webp_tag "$line"; then
    OUT+=("$line")
    i=$((i + 1))
    continue
  fi
  rel="$(webp_relpath "$line")"
  local_file="$ROOT/release-notes/$rel"
  OUT+=("$line")
  nxt=""
  if nxt="$(next_nonempty LINES "$i")"; then
    nxt="${nxt%$'\r'}"
  else
    nxt=""
  fi
  if [[ "$nxt" == *user-attachments* ]]; then
    echo "skip $rel (already has user-attachments)"
    skipped=$((skipped + 1))
    i=$((i + 1))
    continue
  fi
  if [ ! -f "$local_file" ]; then
    echo "Missing $local_file for <!-- webp: $rel -->" >&2
    exit 1
  fi
  size="$(wc -c < "$local_file" | tr -d ' ')"
  if [ "$size" -gt "$MAX_BYTES" ]; then
    echo "$local_file is $size bytes; GitHub free image limit is $MAX_BYTES" >&2
    exit 1
  fi
  mb="$(awk -v b="$size" 'BEGIN { printf "%.1f", b / 1024 / 1024 }')"
  alt="$(basename "$rel" .webp)"
  if [ "$WHATIF" -eq 1 ]; then
    echo "WhatIf: would upload $rel ($mb MB)"
    pending=$((pending + 1))
    i=$((i + 1))
    continue
  fi
  echo "Uploading $rel ($mb MB) ..."
  url="$(upload_webp "$local_file")"
  OUT+=("<img src=\"${url}\" alt=\"${alt}\" width=\"80%\" />")
  echo "  $url"
  uploaded=$((uploaded + 1))
  i=$((i + 1))
done

if [ "$WHATIF" -eq 1 ]; then
  echo "WhatIf: would upload $pending WebP(s); skip $skipped already attached"
  exit 0
fi

if [ "$uploaded" -gt 0 ]; then
  printf '%s\n' "${OUT[@]}" > "$NOTES"
  echo "Wrote $uploaded <img> line(s) into $NOTES (skipped $skipped)"
else
  echo "No new WebP uploads (skipped $skipped already attached)"
fi
