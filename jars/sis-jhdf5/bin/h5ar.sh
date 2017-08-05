#! /bin/bash

# This script requires the readlink binary. If your system lacks this binary, $JHDFDIR needs to be hard-coded

SCRIPT="$0"
if [ "${SCRIPT}" = "${SCRIPT#/}" ]; then
  SCRIPT="`pwd`/${SCRIPT#./}"
fi
BINDIR="${SCRIPT%/*}"
LINK="`readlink $0`"
while [ -n "${LINK}" ]; do
  if [ "${LINK#/}" = "${LINK}" ]; then
    SCRIPT="${BINDIR}/${LINK}"
  else
    SCRIPT="${LINK}"
  fi
  BINDIR="${SCRIPT%/*}"
  LINK="`readlink ${SCRIPT}`"
done
BINDIR="${SCRIPT%/*}"
JHDFDIR="${BINDIR%/*}"
java -Dnative.libpath="${JHDFDIR}/lib/native" -jar "${JHDFDIR}/lib/sis-jhdf5-tools.jar" "$@"
