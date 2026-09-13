#!/usr/bin/env bash
# Concatenate a jAER VCR deck folder to one AEDAT-4. Does not delete sources.
set -eu
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -d "$ROOT/build/classes" ]]; then
  CP="$ROOT/build/classes:$ROOT/lib/*:$ROOT/jars/*"
elif [[ -f "$ROOT/jAER.jar" ]]; then
  CP="$ROOT/jAER.jar:$ROOT/lib/*:$ROOT/jars/*"
else
  echo "jAER classes not found. Run ant compile, or use an installed jAER.jar." >&2
  exit 1
fi
exec java -cp "$CP" net.sf.jaer.eventio.aedat4.Aedat4Concat "$@"
