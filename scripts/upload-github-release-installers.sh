#!/usr/bin/env bash
# Upload install4j media from installers/<VERSION>/ to the GitHub Release for that tag.
# Usage: scripts/upload-github-release-installers.sh [TAG]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
TAG="${1:-$(tr -d '[:space:]' < VERSION.txt)}"
DIR="$ROOT/installers/$TAG"
if [ ! -d "$DIR" ]; then
  echo "Missing $DIR — run ant release first" >&2
  exit 1
fi
shopt -s nullglob
files=("$DIR"/jAER_windows-x64_*.exe "$DIR"/jAER_macos_*.dmg "$DIR"/jAER_unix_*.sh)
if [ ${#files[@]} -eq 0 ]; then
  echo "No installer media under $DIR" >&2
  exit 1
fi
echo "Release tag: $TAG"
if ! gh release view "$TAG" >/dev/null 2>&1; then
  gh release create "$TAG" --title "jaer-$TAG" --notes "jAER $TAG installers. See release-notes/."
fi
gh release upload "$TAG" "${files[@]}" --clobber
echo "Uploaded ${#files[@]} installer(s) to https://github.com/SensorsINI/jaer/releases/tag/$TAG"
