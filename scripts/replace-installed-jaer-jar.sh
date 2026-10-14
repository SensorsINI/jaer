#!/usr/bin/env bash
# Copy repo dist/jAER.jar over the install4j-installed jAER.jar.
# Uses sudo when the dest is not writable.
#
# Usage (repo root):
#   bash scripts/replace-installed-jaer-jar.sh
#   ant replace-installed-jar
#   ant replace-installed-jar -Djaer.install.dir=/opt/jAER

set -euo pipefail

SOURCE_JAR="${1:-}"
INSTALL4J_PROJECT="${2:-}"
INSTALL_DIR="${3:-${JAER_INSTALL_DIR:-}}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

if [[ -z "$SOURCE_JAR" ]]; then
    SOURCE_JAR="$REPO_ROOT/dist/jAER.jar"
fi
if [[ -z "$INSTALL4J_PROJECT" ]]; then
    INSTALL4J_PROJECT="$REPO_ROOT/install4j/jaer.install4j"
fi

if [[ ! -f "$SOURCE_JAR" ]]; then
    echo "Missing $SOURCE_JAR. Build it first: ant jar   or   ant jar-fast" >&2
    exit 1
fi
if [[ ! -f "$INSTALL4J_PROJECT" ]]; then
    echo "install4j project not found: $INSTALL4J_PROJECT" >&2
    exit 1
fi

SOURCE_JAR="$(cd "$(dirname "$SOURCE_JAR")" && pwd)/$(basename "$SOURCE_JAR")"
APP_ID="$(grep -oE 'applicationId="[^"]+"' "$INSTALL4J_PROJECT" | head -1 | cut -d'"' -f2)"
SHORT_NAME="$(grep -oE 'shortName="[^"]+"' "$INSTALL4J_PROJECT" | head -1 | cut -d'"' -f2)"
APP_NAME="$(grep -oE '<application name="[^"]+"' "$INSTALL4J_PROJECT" | head -1 | cut -d'"' -f2)"
EXE_NAME="$(grep -oE '<executable name="[^"]+"' "$INSTALL4J_PROJECT" | head -1 | cut -d'"' -f2)"
EXE_NAME="${EXE_NAME:-jaer}"

echo "install4j applicationId=$APP_ID shortName=$SHORT_NAME launcher=$EXE_NAME"

find_jar_under() {
    local root="$1"
    local c
    for c in \
        "$root/dist/jAER.jar" \
        "$root/jaer/dist/jAER.jar" \
        "$root/Contents/Resources/app/dist/jAER.jar"
    do
        if [[ -f "$c" ]]; then
            echo "$c"
            return 0
        fi
    done
    return 1
}

is_install_root() {
    local root="$1"
    [[ -d "$root" ]] || return 1
    find_jar_under "$root" >/dev/null || return 1
    [[ -d "$root/.install4j" || -x "$root/$EXE_NAME" || -d "$root/${EXE_NAME}.app" || -x "$root/${EXE_NAME}.exe" ]]
}

# install4j writes instdir<applicationId> here (same id Windows stores in the registry).
read_install4j_instdir() {
    local plist dir
    [[ -n "$APP_ID" ]] || return 1
    for plist in \
        "$HOME/Library/Preferences/com.install4j.installations.plist" \
        "/Library/Preferences/com.install4j.installations.plist"
    do
        [[ -f "$plist" ]] || continue
        dir="$(/usr/libexec/PlistBuddy -c "Print :/com/install4j/installations/:instdir${APP_ID}" "$plist" 2>/dev/null || true)"
        if [[ -n "$dir" ]]; then
            echo "$dir"
            return 0
        fi
    done
    return 1
}

# macosFolder media installs a sibling tree. jAER.jar is not a sealed resource of jaer.app.
# A jar inside Contents/ is sealed; replacing it invalidates Developer ID and Gatekeeper.
enclosing_signed_app() {
    local path="$1" dir base
    dir="$(cd "$(dirname "$path")" && pwd)"
    while [[ "$dir" != "/" ]]; do
        base="$(basename "$dir")"
        if [[ "$base" == *.app && -d "$dir/Contents/_CodeSignature" ]]; then
            echo "$dir"
            return 0
        fi
        dir="$(dirname "$dir")"
    done
    return 1
}

refuse_sealed_macos_jar() {
    local jar="$1" app
    [[ "$(uname -s)" == "Darwin" ]] || return 0
    app="$(enclosing_signed_app "$jar" || true)"
    [[ -n "$app" ]] || return 0
    cat >&2 <<EOF
jAER.jar is inside the signed app bundle:
  $app
Replacing it invalidates the Developer ID signature, and Gatekeeper will not launch the app.
Install a snapshot build (signed installers from current master):
  https://github.com/SensorsINI/jaer/releases/tag/snapshot
EOF
    exit 1
}

ROOT=""
PLIST_DIR=""
if [[ -n "$INSTALL_DIR" ]]; then
    ROOT="$INSTALL_DIR"
else
    PLIST_DIR="$(read_install4j_instdir || true)"
    if [[ -n "$PLIST_DIR" ]] && is_install_root "$PLIST_DIR"; then
        ROOT="$PLIST_DIR"
    elif [[ -n "$APP_NAME" && -d "/Applications/$APP_NAME" ]] && is_install_root "/Applications/$APP_NAME"; then
        ROOT="/Applications/$APP_NAME"
    elif [[ -n "$APP_NAME" && -d "$HOME/Applications/$APP_NAME" ]] && is_install_root "$HOME/Applications/$APP_NAME"; then
        ROOT="$HOME/Applications/$APP_NAME"
    elif [[ -d "$HOME/$SHORT_NAME" ]] && is_install_root "$HOME/$SHORT_NAME"; then
        ROOT="$HOME/$SHORT_NAME"
    elif [[ -d "/opt/$SHORT_NAME" ]] && is_install_root "/opt/$SHORT_NAME"; then
        ROOT="/opt/$SHORT_NAME"
    elif [[ -d "/Applications/$SHORT_NAME" ]] && is_install_root "/Applications/$SHORT_NAME"; then
        ROOT="/Applications/$SHORT_NAME"
    elif [[ -d "/Applications/jAER.app" ]] && is_install_root "/Applications/jAER.app"; then
        ROOT="/Applications/jAER.app"
    fi
fi

if [[ -z "$ROOT" ]]; then
    echo "Could not find an install4j jAER install. Pass the directory as arg 3 or ant -Djaer.install.dir=..." >&2
    exit 1
fi

DEST_JAR="$(find_jar_under "$ROOT" || true)"
if [[ -z "$DEST_JAR" ]]; then
    echo "Installed jAER.jar not found under $ROOT (expected dist/jAER.jar)" >&2
    exit 1
fi

if [[ "$SOURCE_JAR" -ef "$DEST_JAR" ]]; then
    echo "Source and installed jar are the same file: $SOURCE_JAR" >&2
    exit 1
fi

refuse_sealed_macos_jar "$DEST_JAR"

echo "Source: $SOURCE_JAR"
echo "  mtime $(date -r "$SOURCE_JAR" '+%Y-%m-%d %H:%M:%S')  $(wc -c < "$SOURCE_JAR" | tr -d ' ') bytes"
echo "Install: $ROOT"
echo "Dest:    $DEST_JAR"

if pgrep -x "$EXE_NAME" >/dev/null 2>&1; then
    echo "jAER is running ($EXE_NAME). Close it so the jar can be replaced, then re-run." >&2
    exit 1
fi

copy_with_backup() {
    local src="$1" dest="$2"
    cp -f "$dest" "$dest.bak"
    cp -f "$src" "$dest"
}

if [[ -w "$DEST_JAR" ]]; then
    copy_with_backup "$SOURCE_JAR" "$DEST_JAR"
else
    echo "Need root to write $DEST_JAR (sudo prompt follows)"
    sudo bash -c 'cp -f "$2" "$2.bak"; cp -f "$1" "$2"' _ "$SOURCE_JAR" "$DEST_JAR"
fi

SRC_KB=$(wc -c < "$SOURCE_JAR" | tr -d ' ')
DST_KB=$(wc -c < "$DEST_JAR" | tr -d ' ')
echo "Replaced $DEST_JAR"
echo "  source $SRC_KB bytes, dest $DST_KB bytes, backup ${DEST_JAR}.bak"

if [[ "$(uname -s)" == "Darwin" && -d "$ROOT/${EXE_NAME}.app" ]] && command -v codesign >/dev/null 2>&1; then
    if ! codesign --verify --strict "$ROOT/${EXE_NAME}.app" >/dev/null 2>&1; then
        echo "Launcher signature no longer verifies; restoring ${DEST_JAR}.bak" >&2
        cp -f "${DEST_JAR}.bak" "$DEST_JAR"
        exit 1
    fi
    echo "Launcher signature still valid: $ROOT/${EXE_NAME}.app"
fi
