#!/bin/bash

# If you get "UnsatisfiedLinkError: /tmp/usb4java... failed to map segment from shared object: Operation not permitted",
# add the following line to the command below:
# -Djava.io.tmpdir=~/tmpdir/

# from https://stackoverflow.com/questions/59895/get-the-source-directory-of-a-bash-script-from-within-the-script-itself
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# change JAERHOME to your jAER top folder, i.e. the one that holds this script, if DIR doesn't work
JAERHOME="$DIR"
java -classpath "${JAERHOME}/dist/jAER.jar:${JAERHOME}/jars/*:${JAERHOME}/lib/*" -Dsun.java2d.noddraw=true -Dsun.java2d.opengl=false net.sf.jaer.util.avioutput.DvsSliceAviWriter "$@"
