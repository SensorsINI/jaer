#!/usr/bin/env bash
# Pack output currentInstallers/<VERSION.txt>/jaer-sample-data.zip onto GitHub
# Release tag sample-data-current. Creates that Release if missing (prerelease,
# never Latest). Clobbers a previous zip. Does not attach to VERSION.txt.
# Usage (repo root):
#   bash scripts/upload-sample-data-current.sh
#   bash scripts/upload-sample-data-current.sh --what-if
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

usage() {
  echo "Usage: $0 [--what-if|-WhatIf|-n]" >&2
}

WHATIF=0
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
    -*)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
    *)
      echo "Unexpected argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

VERSION="$(tr -d '[:space:]' < VERSION.txt)"
if [ -z "$VERSION" ]; then
  echo "VERSION.txt is empty" >&2
  exit 1
fi

RELEASE_TAG="sample-data-current"
ZIP="$ROOT/currentInstallers/$VERSION/jaer-sample-data.zip"
if [ ! -f "$ZIP" ]; then
  echo "Missing $ZIP — run ant pack-sample-data first (needs recordings in sampleData/)" >&2
  exit 1
fi

size_mb() {
  awk -v b="$(wc -c < "$1")" 'BEGIN { printf "%.1f", b / 1024 / 1024 }'
}

NOTES="Durable jaer-sample-data.zip copied onto each product/rc Release by release.yml. Not a product version. Never mark this Release Latest."

echo "Zip from VERSION.txt $VERSION: $ZIP ($(size_mb "$ZIP") MB)"
echo "GitHub Release tag: $RELEASE_TAG"

if [ "$WHATIF" -eq 1 ]; then
  echo "WhatIf: would create-or-update Release $RELEASE_TAG with $ZIP"
  exit 0
fi

export GH_SPINNER_DISABLED=yes
start=$(date +%s)
if ! gh release view "$RELEASE_TAG" >/dev/null 2>&1; then
  echo "Creating GitHub Release $RELEASE_TAG (prerelease, --latest=false) ..."
  gh release create "$RELEASE_TAG" "$ZIP" \
    --title "jAER sample recordings" \
    --notes "$NOTES" \
    --prerelease \
    --latest=false
else
  echo "Uploading $(basename "$ZIP") ($(size_mb "$ZIP") MB) to existing $RELEASE_TAG ..."
  gh release upload "$RELEASE_TAG" "$ZIP" --clobber
fi
echo "Done in $(( $(date +%s) - start ))s"
echo "Release: https://github.com/SensorsINI/jaer/releases/tag/$RELEASE_TAG"
echo "GitHub Latest is still the product Release (e.g. 3.5.0). This tag is only a zip bucket for release.yml; do not mark it Latest."
