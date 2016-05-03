#!/bin/sh

java \
-classpath "dist/jAER.jar:jars/*:jars/javacv/*:jars/jogl/*:jars/usb4java/*" \
-splash:SplashScreen.gif \
-Djava.util.logging.config.file=conf/Logging.properties -Dsun.java2d.noddraw=true -Dsun.java2d.opengl=false \
#-Djava.io.tmpdir=~/tmpdir/ \ # use this if you get "UnsatisfiedLinkError: /tmp/usb4java... failed to map segment from shared object: Operation not permitted"
net.sf.jaer.JAERViewer
