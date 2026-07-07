#!/usr/bin/env bash
# Run jAER via NetBeans/Ant (compile + launch JAERViewer).
set -eu

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

find_ant() {
    if command -v ant >/dev/null 2>&1; then
        command -v ant
        return 0
    fi
    if [[ -n "${NETBEANS_HOME:-}" && -x "${NETBEANS_HOME}/extide/ant/bin/ant" ]]; then
        echo "${NETBEANS_HOME}/extide/ant/bin/ant"
        return 0
    fi
    if [[ -x "/c/Program Files/Apache NetBeans/extide/ant/bin/ant" ]]; then
        echo "/c/Program Files/Apache NetBeans/extide/ant/bin/ant"
        return 0
    fi
    local candidate
    for candidate in \
        /c/Program\ Files/NetBeans*/extide/ant/bin/ant \
        /c/Program\ Files/Apache/ant/bin/ant \
        /c/apache-ant*/bin/ant; do
        if [[ -x "$candidate" ]]; then
            echo "$candidate"
            return 0
        fi
    done
    return 1
}

ANT="$(find_ant)" || {
    echo "ant not found in PATH." >&2
    echo "Install Apache Ant, add it to PATH, or set NETBEANS_HOME." >&2
    exit 1
}

exec "$ANT" run
