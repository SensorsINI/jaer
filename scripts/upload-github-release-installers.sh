#!/usr/bin/env bash
# Upload install4j media from currentInstallers/<VERSION>/ to the GitHub Release for that tag.
# Usage (repo root):
#   bash scripts/upload-github-release-installers.sh
#   bash scripts/upload-github-release-installers.sh 3.3.0
#   bash scripts/upload-github-release-installers.sh --what-if
#   bash scripts/upload-github-release-installers.sh -WhatIf
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

DIR="$ROOT/currentInstallers/$TAG"
if [ ! -d "$DIR" ]; then
  echo "Missing $DIR — run ant release first" >&2
  exit 1
fi
shopt -s nullglob
installers=("$DIR"/jAER_windows-x64_*.exe "$DIR"/jAER_macos_*.dmg "$DIR"/jAER_unix_*.sh)
if [ ${#installers[@]} -eq 0 ]; then
  echo "No installer media under $DIR" >&2
  exit 1
fi
files=("${installers[@]}")
if [ -f "$DIR/jaer-sample-data.zip" ]; then
  files+=("$DIR/jaer-sample-data.zip")
else
  echo "WARNING: $DIR/jaer-sample-data.zip missing — run ant pack-sample-data (or ant release) before upload so Latest has the sample-data asset." >&2
fi

size_mb() {
  awk -v b="$(wc -c < "$1")" 'BEGIN { printf "%.1f", b / 1024 / 1024 }'
}

echo "Release tag: $TAG"
n=0
for f in "${files[@]}"; do
  n=$((n + 1))
  echo "  [$n/${#files[@]}] $(basename "$f") ($(size_mb "$f") MB)"
done

if [ "$WHATIF" -eq 1 ]; then
  echo "WhatIf: would upload ${#files[@]} file(s) to release $TAG"
  for f in "${files[@]}"; do
    echo "  $f"
  done
  exit 0
fi

NOTES="$ROOT/release-notes/jaer-${TAG}-release-notes.md"
if ! gh release view "$TAG" >/dev/null 2>&1; then
  echo "Creating GitHub draft release $TAG (publish later; not Latest)"
  if [ -f "$NOTES" ]; then
    gh release create "$TAG" --draft --latest=false --title "jaer-$TAG" --notes-file "$NOTES"
  else
    gh release create "$TAG" --draft --latest=false --title "jaer-$TAG" --notes "jAER $TAG installers. See release-notes/."
  fi
elif [ -f "$NOTES" ]; then
  echo "Leaving GitHub release body unchanged (use ant upload-release-notes to push notes)."
fi
export GH_SPINNER_DISABLED=yes
n=0
for f in "${files[@]}"; do
  n=$((n + 1))
  echo "[$n/${#files[@]}] Uploading $(basename "$f") ($(size_mb "$f") MB) ..."
  start=$(date +%s)
  gh release upload "$TAG" "$f" --clobber
  echo "[$n/${#files[@]}] Uploaded $(basename "$f") in $(( $(date +%s) - start ))s"
done
echo "Uploaded ${#files[@]} installer(s) to https://github.com/SensorsINI/jaer/releases/tag/$TAG"
