#!/usr/bin/env bash
# Upload installer media from currentInstallers/<VERSION>/ to an existing GitHub Release.
# Does not create a tag or draft (ant create-draft-release first).
# Default: skip Windows .exe (keeps Azure-signed GitHub asset) and skip macOS .dmg
# unless this host is Darwin. Linux .sh from any OS.
# Sample zip is ant upload-sample-data, not this script.
# Usage (repo root):
#   bash scripts/upload-github-release-installers.sh
#   bash scripts/upload-github-release-installers.sh --tag 3.5.0
#   bash scripts/upload-github-release-installers.sh --what-if
#   bash scripts/upload-github-release-installers.sh --clobber-windows
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

usage() {
  echo "Usage: $0 [--what-if|-WhatIf|-n] [--clobber-windows] [--tag TAG | TAG]" >&2
}

WHATIF=0
CLOBBER_WINDOWS=0
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
    --clobber-windows|-ClobberWindows)
      CLOBBER_WINDOWS=1
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
  echo "Missing $DIR — build media first (ant macos-build-notarize / release-linux / azure-sign-ci)" >&2
  exit 1
fi

ON_MAC=0
if [ "$(uname -s 2>/dev/null || true)" = Darwin ]; then
  ON_MAC=1
fi

shopt -s nullglob
candidates=("$DIR"/jAER_windows-x64_*.exe "$DIR"/jAER_macos_*.dmg "$DIR"/jAER_unix_*.sh)
if [ ${#candidates[@]} -eq 0 ]; then
  echo "No installer media under $DIR" >&2
  exit 1
fi

files=()
for f in "${candidates[@]}"; do
  base="$(basename "$f")"
  case "$base" in
    jAER_windows-x64_*.exe)
      if [ "$CLOBBER_WINDOWS" -eq 1 ]; then
        echo "WARNING: uploading local unsigned $base --clobber over the Azure-signed GitHub exe"
        files+=("$f")
      else
        echo "Skipping $base to keep the Azure-signed GitHub asset. Do not ant upload-installers-clobber-windows after Azure."
      fi
      ;;
    jAER_macos_*.dmg)
      if [ "$ON_MAC" -eq 0 ]; then
        echo "Skipping $base (not macOS). Mac DMGs must be uploaded from the Mini after ant macos-build-notarize."
      else
        files+=("$f")
      fi
      ;;
    *)
      files+=("$f")
      ;;
  esac
done

if [ -f "$DIR/jaer-sample-data.zip" ]; then
  echo "Not attaching jaer-sample-data.zip here. Use: ant upload-sample-data"
fi

if [ ${#files[@]} -eq 0 ]; then
  echo "Nothing to upload. Windows exe is skipped unless --clobber-windows. Mac DMGs only from macOS. Linux: ant release-linux then re-run." >&2
  exit 1
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

if ! gh release view "$TAG" >/dev/null 2>&1; then
  echo "GitHub Release $TAG does not exist. Create it first: ant create-draft-release" >&2
  exit 1
fi
echo "Leaving GitHub release body unchanged (use ant upload-release-notes to push notes)."

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
echo "Sample zip: ant upload-sample-data. Then ant copy-updates-xml after publish."
