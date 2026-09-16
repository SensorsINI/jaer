#!/usr/bin/env bash
# Upload currentInstallers/<tag>/jaer-sample-data.zip to the GitHub Release for that tag.
# Does not create a draft; the Release must already exist.
# Usage (repo root):
#   bash scripts/upload-github-release-sample-data.sh
#   bash scripts/upload-github-release-sample-data.sh --tag 3.5.0
#   bash scripts/upload-github-release-sample-data.sh --what-if
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

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

ZIP="$ROOT/currentInstallers/$TAG/jaer-sample-data.zip"
if [ ! -f "$ZIP" ]; then
  echo "Missing $ZIP — run ant pack-sample-data (or ant upload-sample-data) first" >&2
  exit 1
fi

size_mb() {
  awk -v b="$(wc -c < "$1")" 'BEGIN { printf "%.1f", b / 1024 / 1024 }'
}

echo "Release tag: $TAG"
echo "  $(basename "$ZIP") ($(size_mb "$ZIP") MB)"

if [ "$WHATIF" -eq 1 ]; then
  echo "WhatIf: would upload $ZIP to release $TAG"
  exit 0
fi

if ! gh release view "$TAG" >/dev/null 2>&1; then
  echo "GitHub Release $TAG does not exist. Create it first: ant create-draft-release" >&2
  exit 1
fi

export GH_SPINNER_DISABLED=yes
echo "Uploading $(basename "$ZIP") ($(size_mb "$ZIP") MB) ..."
start=$(date +%s)
gh release upload "$TAG" "$ZIP" --clobber
echo "Uploaded $(basename "$ZIP") in $(( $(date +%s) - start ))s"
echo "Sample data: https://github.com/SensorsINI/jaer/releases/latest/download/jaer-sample-data.zip"
echo "If WebP thumbs changed, commit sampleData/previews/*.webp (they are not in the zip)."
