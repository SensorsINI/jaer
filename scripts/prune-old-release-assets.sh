#!/usr/bin/env bash
# Delete jAER installer assets from GitHub Releases older than the last KEEP tags.
# Usage: scripts/prune-old-release-assets.sh [KEEP]
set -euo pipefail
KEEP="${1:-3}"
if [ "$KEEP" -lt 1 ]; then
  echo "KEEP must be >= 1" >&2
  exit 1
fi
mapfile -t TAGS < <(gh release list --limit 50 --json tagName,publishedAt,isDraft \
  --jq '.[] | select(.isDraft|not) | [.publishedAt,.tagName] | @tsv' \
  | sort -r | awk '{print $2}')
if [ "${#TAGS[@]}" -le "$KEEP" ]; then
  echo "Only ${#TAGS[@]} release(s); nothing to prune (keep $KEEP)."
  exit 0
fi
echo "Keeping newest $KEEP tag(s): ${TAGS[*]:0:KEEP}"
for tag in "${TAGS[@]:KEEP}"; do
  names=$(gh release view "$tag" --json assets --jq '.assets[].name' | grep -E '^jAER_.*\.(exe|dmg|sh)$' || true)
  if [ -z "$names" ]; then
    echo "No installer assets on $tag"
    continue
  fi
  while IFS= read -r name; do
    echo "Deleting $name from $tag"
    gh release delete-asset "$tag" "$name" --yes
  done <<< "$names"
done
echo "Done."
