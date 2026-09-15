#!/usr/bin/env bash
# Bake website/latest.json, serve website/, open the default browser.
# Usage (repo root):
#   ant website
#   ant website -Djaer.website.port=8081
#   bash scripts/website-preview.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

PORT="${JAER_WEBSITE_PORT:-8080}"
URL="http://127.0.0.1:${PORT}/"

if command -v python3 >/dev/null 2>&1; then
    PY=python3
elif command -v python >/dev/null 2>&1; then
    PY=python
else
    echo "python3/python is not on PATH (needed for website/bake-latest.py and http.server)" >&2
    exit 1
fi

"$PY" website/bake-latest.py
echo "Serving website/ at ${URL}  (Ctrl+C to stop)"

(
    sleep 1
    if command -v open >/dev/null 2>&1; then
        open "$URL"
    elif command -v xdg-open >/dev/null 2>&1; then
        xdg-open "$URL" >/dev/null 2>&1 || true
    fi
) &

exec "$PY" -m http.server "$PORT" --bind 127.0.0.1 --directory website
