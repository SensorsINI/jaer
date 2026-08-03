#!/usr/bin/env bash
# Dev launch: build/classes + lib/*.jar + jars/*.jar (skips ant ivy/compile on every start)
set -eu

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ ! -f "build/classes/net/sf/jaer/JAERViewer.class" ]]; then
    echo "build/classes is missing. Run once:  ant compile" >&2
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "java not found in PATH." >&2
    exit 1
fi

# Classpath separator is ':' on Unix
JAER_CP="build/classes:lib/*:jars/*"

# Split args: -D* / -X* / --* go to the JVM; everything else to JAERViewer (e.g. data files).
JVM_EXTRA=()
APP_ARGS=()
for arg in "$@"; do
    case "$arg" in
        -D*|-X*|--*)
            JVM_EXTRA+=("$arg")
            ;;
        *)
            APP_ARGS+=("$arg")
            ;;
    esac
done

# OOM debug (uncomment as needed):
# -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=oom.hprof
# -XX:NativeMemoryTracking=summary
# -Djaer.memory.trace.intervalMs=60000

exec java \
  --add-exports java.base/java.lang=ALL-UNNAMED \
  --add-exports java.desktop/sun.awt=ALL-UNNAMED \
  --add-exports java.desktop/sun.java2d=ALL-UNNAMED \
  -Djava.library.path=jars \
  -Djava.util.logging.config.file=conf/Logging.properties \
  -Djogl.disable.openglcore \
  -Djogl.disable.opengles=false \
  -Dsun.java2d.dpiaware=true \
  -Dsun.java2d.noddraw=true \
  -Dsun.java2d.opengl=false \
  -Xmx10g \
  -Xrs \
  -splash:SplashScreen.png \
  "${JVM_EXTRA[@]}" \
  -cp "$JAER_CP" \
  net.sf.jaer.JAERViewer \
  "${APP_ARGS[@]}"
