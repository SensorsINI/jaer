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

list_listeners() {
    if command -v lsof >/dev/null 2>&1; then
        lsof -nP -iTCP:"$PORT" -sTCP:LISTEN 2>/dev/null || true
        return
    fi
    if command -v ss >/dev/null 2>&1; then
        ss -ltnp "sport = :$PORT" 2>/dev/null || true
        return
    fi
    if command -v netstat >/dev/null 2>&1; then
        netstat -ltnp 2>/dev/null | grep -E ":${PORT}\\b" || true
    fi
}

pids_on_port() {
    if command -v lsof >/dev/null 2>&1; then
        lsof -t -nP -iTCP:"$PORT" -sTCP:LISTEN 2>/dev/null | sort -u
        return
    fi
    if command -v ss >/dev/null 2>&1; then
        ss -ltnp "sport = :$PORT" 2>/dev/null | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' | sort -u
        return
    fi
    if command -v netstat >/dev/null 2>&1; then
        netstat -ltnp 2>/dev/null | awk -v p=":$PORT" '$4 ~ p"$" { split($7, a, "/"); if (a[1] ~ /^[0-9]+$/) print a[1] }' | sort -u
    fi
}

cmd_for_pid() {
    local pid="$1"
    if [ -r "/proc/${pid}/cmdline" ]; then
        tr '\0' ' ' < "/proc/${pid}/cmdline"
        return
    fi
    ps -p "$pid" -o args= 2>/dev/null || true
}

is_website_preview() {
    local pid="$1"
    local cmd
    cmd="$(cmd_for_pid "$pid")"
    local comm
    comm="$(ps -p "$pid" -o comm= 2>/dev/null || true)"
    comm="${comm##*/}"
    case "$comm" in
        python|python3|pythonw) ;;
        *) return 1 ;;
    esac
    case "$cmd" in
        *http.server*) ;;
        *) return 1 ;;
    esac
    case "$cmd" in
        *"${PORT}"*) return 0 ;;
        *) return 1 ;;
    esac
}

LISTEN_PIDS="$(pids_on_port || true)"
if [ -n "${LISTEN_PIDS}" ]; then
    OTHER=""
    PREVIEW=""
    for pid in $LISTEN_PIDS; do
        if is_website_preview "$pid"; then
            PREVIEW="${PREVIEW} ${pid}"
        else
            OTHER="${OTHER} ${pid}"
        fi
    done
    if [ -n "${OTHER}" ]; then
        echo "Port ${PORT} is already in use (not an ant website preview):" >&2
        echo >&2
        list_listeners >&2
        echo >&2
        echo "Or another port:  ant website -Djaer.website.port=8081" >&2
        exit 1
    fi
    echo "Stopping leftover ant website preview on port ${PORT}:" >&2
    list_listeners >&2
    # shellcheck disable=SC2086
    kill -TERM $PREVIEW 2>/dev/null || true
    i=0
    while [ "$i" -lt 25 ]; do
        LEFT="$(pids_on_port || true)"
        if [ -z "${LEFT}" ]; then
            break
        fi
        sleep 0.2
        i=$((i + 1))
    done
    LEFT="$(pids_on_port || true)"
    if [ -n "${LEFT}" ]; then
        # shellcheck disable=SC2086
        kill -KILL $LEFT 2>/dev/null || true
        sleep 0.2
    fi
    LEFT="$(pids_on_port || true)"
    if [ -n "${LEFT}" ]; then
        echo "Port ${PORT} is still in use after kill." >&2
        list_listeners >&2
        exit 1
    fi
    echo >&2
}

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
