#!/bin/sh

# If you get "UnsatisfiedLinkError: /tmp/usb4java... failed to map segment from shared object: Operation not permitted",
# add the following line to the command below:
# -Djava.io.tmpdir=~/tmpdir/

java \
-classpath "dist/jAER.jar:jars/*:jars/javacv/*:jars/jogl/*:jars/usb4java/*" \
-splash:SplashScreen.gif \
-Djava.util.logging.config.file=conf/Logging.properties -Dsun.java2d.noddraw=true -Dsun.java2d.opengl=false \
net.sf.jaer.JAERViewer
