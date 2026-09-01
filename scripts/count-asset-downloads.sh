#!/usr/bin/env bash
# Summarize GitHub Release asset download counts (needs gh auth).
# Usage (repo root):
#   bash scripts/count-asset-downloads.sh
#   bash scripts/count-asset-downloads.sh --tag 3.3.0
#   bash scripts/count-asset-downloads.sh --limit 20 --all
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

usage() {
  echo "Usage: $0 [--tag TAG] [--limit N] [--all]" >&2
}

TAG=""
LIMIT=50
ALL=0
while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --tag)
      TAG="${2:-}"
      shift 2
      ;;
    --tag=*)
      TAG="${1#--tag=}"
      shift
      ;;
    --limit)
      LIMIT="${2:?--limit requires a value}"
      shift 2
      ;;
    --limit=*)
      LIMIT="${1#--limit=}"
      shift
      ;;
    --all|--all=true)
      ALL=1
      shift
      ;;
    --all=false)
      ALL=0
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

if ! [[ "$LIMIT" =~ ^[1-9][0-9]*$ ]]; then
  echo "LIMIT must be >= 1" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "gh is not authenticated. Run: gh auth login" >&2
  exit 1
fi

REPO="$(gh api repos/:owner/:repo --jq .full_name)"

ASSET_SEL='select(.name | test("^jAER_.*\\.(exe|dmg|sh)$"))'
if [ "$ALL" -eq 1 ]; then
  ASSET_SEL='select(true)'
fi

ROWS_JQ="select(.draft|not) | . as \$r | ([\$r.tag_name, (\$r.published_at // \"\"), \"\", \"0\", \"0\"], (\$r.assets[]? | ${ASSET_SEL} | [\$r.tag_name, (\$r.published_at // \"\"), .name, (.download_count|tostring), (.size|tostring)])) | @tsv"

if [ -n "$TAG" ]; then
  TSV="$(gh api "repos/:owner/:repo/releases/tags/${TAG}" --jq "${ROWS_JQ}")" || {
    echo "No GitHub Release for tag $TAG" >&2
    exit 1
  }
else
  LIST_JQ=".[] | ${ROWS_JQ}"
  TSV="$(gh api --paginate repos/:owner/:repo/releases --jq "${LIST_JQ}")"
fi

echo "GitHub release asset downloads  ${REPO}"
if [ "$ALL" -eq 1 ]; then
  echo "Assets: all files  Limit: ${LIMIT}"
else
  echo "Assets: jAER installer media (exe/dmg/sh)  Limit: ${LIMIT}"
fi
echo

printf '%s\n' "$TSV" | awk -v limit="$LIMIT" -v single_tag="$TAG" '
BEGIN { FS = "\t" }
NF < 5 { next }
{
  tag = $1
  if (single_tag == "" && !(tag in seen_tag)) {
    if (nrel >= limit) next
    seen_tag[tag] = 1
    tag_order[++nrel] = tag
    pub[tag] = substr($2, 1, 10)
  } else if (single_tag != "") {
    if (!(tag in seen_tag)) {
      seen_tag[tag] = 1
      tag_order[++nrel] = tag
      pub[tag] = substr($2, 1, 10)
    }
  } else if (!(tag in seen_tag)) {
    next
  }
  if ($3 == "") next
  key = tag SUBSEP ++n[tag]
  name[key] = $3
  dl[key] = $4 + 0
  size[key] = $5 + 0
  rel[tag] += $4 + 0
}
function platform(n) {
  if (n ~ /windows/) return "windows-x64"
  if (n ~ /macos_aarch64/) return "macos-aarch64"
  if (n ~ /macos/) return "macos-intel"
  if (n ~ /unix/) return "linux"
  return "other"
}
END {
  if (nrel == 0) {
    print "No matching published assets."
    exit 0
  }
  for (i = 1; i <= nrel; i++) {
    t = tag_order[i]
    printf "%s  %s  release total: %d\n", t, pub[t], rel[t] + 0
    if (n[t] + 0 == 0) {
      print "  (no matching assets)"
      print ""
      continue
    }
    # insertion sort by download count desc
    m = n[t]
    for (a = 1; a <= m; a++) idx[a] = a
    for (a = 2; a <= m; a++) {
      hold = idx[a]
      b = a - 1
      while (b >= 1 && dl[t SUBSEP idx[b]] < dl[t SUBSEP hold]) {
        idx[b + 1] = idx[b]
        b--
      }
      idx[b + 1] = hold
    }
    for (a = 1; a <= m; a++) {
      k = t SUBSEP idx[a]
      mb = size[k] / (1024 * 1024)
      printf "  %8d  %-42s  %7.1f MB\n", dl[k], name[k], mb
      p = platform(name[k])
      plat[p] += dl[k]
      grand += dl[k]
      rows++
    }
    print ""
  }
  print "Totals"
  nplat = split("windows-x64 macos-intel macos-aarch64 linux other", order, " ")
  for (i = 1; i <= nplat; i++) {
    p = order[i]
    if (p in plat) printf "  %-16s %8d\n", p, plat[p]
  }
  printf "  %-16s %8d  (%d asset(s))\n", "GRAND TOTAL", grand + 0, rows + 0
  print ""
  print "Counts are GitHub'\''s per-asset download_count (each completed download of that file)."
  print "Re-uploads keep the same asset id when possible; replacing an asset may reset its count."
}
'
