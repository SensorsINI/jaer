#!/usr/bin/env bash
# Annotated git tag VERSION.txt at HEAD, push it, and create a GitHub *draft* Release.
# Does not publish (not Latest). Publish later in the GitHub UI or:
#   gh release edit <tag> --draft=false
# Requires: git, gh auth. Does not upload installer assets.
# Usage (repo root):
#   bash scripts/create-draft-github-release.sh
#   bash scripts/create-draft-github-release.sh --tag 3.4.0
#   bash scripts/create-draft-github-release.sh --what-if
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

HEAD="$(git rev-parse HEAD)"
if [ -z "$HEAD" ]; then
  echo "git rev-parse HEAD failed" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "gh is not authenticated. Run: gh auth login" >&2
  exit 1
fi

NOTES="$ROOT/release-notes/jaer-${TAG}-release-notes.md"
echo "HEAD: $HEAD"
echo "Tag:  $TAG (draft GitHub Release; not Latest until you publish)"

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  TAGGED="$(git rev-parse "refs/tags/${TAG}^{commit}")"
  if [ "$TAGGED" != "$HEAD" ]; then
    echo "Tag $TAG already points at $TAGGED, not HEAD $HEAD. Move or delete the tag first (see docs/README-releasing-tagging.md)." >&2
    exit 1
  fi
  echo "Local tag $TAG already on HEAD"
else
  if [ "$WHATIF" -eq 1 ]; then
    echo "WhatIf: would git tag -a $TAG -m \"jAER $TAG\""
  else
    git tag -a "$TAG" -m "jAER $TAG"
    echo "Created annotated tag $TAG"
  fi
fi

if [ "$WHATIF" -eq 1 ]; then
  echo "WhatIf: would git push origin refs/tags/$TAG"
else
  git push origin "refs/tags/$TAG"
  echo "Pushed tag $TAG to origin"
fi

if gh release view "$TAG" >/dev/null 2>&1; then
  RELEASE_MISSING=0
else
  RELEASE_MISSING=1
fi

if [ "$WHATIF" -eq 1 ]; then
  if [ "$RELEASE_MISSING" -eq 1 ]; then
    echo "WhatIf: would gh release create $TAG --draft --latest=false"
  else
    echo "WhatIf: GitHub Release $TAG already exists (would leave draft/published state as-is; update notes if present)"
  fi
  exit 0
fi

if [ "$RELEASE_MISSING" -eq 1 ]; then
  echo "Creating GitHub draft release $TAG"
  if [ -f "$NOTES" ]; then
    gh release create "$TAG" --draft --latest=false --title "jaer-$TAG" --notes-file "$NOTES" --target "$HEAD"
  else
    gh release create "$TAG" --draft --latest=false --title "jaer-$TAG" --notes "jAER $TAG (draft). See release-notes/." --target "$HEAD"
  fi
else
  DRAFT="$(gh release view "$TAG" --json isDraft --jq .isDraft)"
  URL="$(gh release view "$TAG" --json url --jq .url)"
  if [ "$DRAFT" = "true" ]; then
    echo "GitHub Release $TAG is already a draft: $URL"
  else
    echo "WARNING: GitHub Release $TAG already exists and is published (not converting to draft): $URL"
  fi
  if [ -f "$NOTES" ]; then
    gh release edit "$TAG" --notes-file "$NOTES"
  fi
fi

echo "Draft: https://github.com/SensorsINI/jaer/releases (publish there when ready)"
echo "Or:    gh release edit $TAG --draft=false"
echo "Upload installers after media build: scripts/upload-github-release-installers.sh"
