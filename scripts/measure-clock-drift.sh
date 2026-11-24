#!/usr/bin/env bash
# Cross-correlate packet activity in a multi-camera AEDAT-4 file and print clock scales.
# Compile first: ant compile
set -eu

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ ! -d build/classes ]]; then
    echo "build/classes is missing. Run ant compile first." >&2
    exit 1
fi

exec java -cp "build/classes:lib/*:jars/*" net.sf.jaer.eventio.aedat4.MulticameraClockDrift "$@"
