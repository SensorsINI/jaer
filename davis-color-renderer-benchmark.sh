#!/bin/bash

# Benchmark DavisColorRenderer endFrame/demosaic on a CDAVIS .aedat recording.
# Pattern follows dvs-slice-avi-writer.sh

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
JAERHOME="$DIR"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/apache-netbeans/jdk}"
export PATH="$JAVA_HOME/bin:$PATH"

cd "$JAERHOME" || exit 1
ant compile -q || exit 1

java -classpath "${JAERHOME}/build/classes:${JAERHOME}/dist/jAER.jar:${JAERHOME}/jars/*:${JAERHOME}/lib/*" \
  -Dsun.java2d.noddraw=true -Dsun.java2d.opengl=false \
  eu.seebetter.ini.chips.davis.DavisColorRendererBenchmark "$@"
