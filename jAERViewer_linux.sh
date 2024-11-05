#!/bin/bash

# If you get "UnsatisfiedLinkError: /tmp/usb4java... failed to map segment from shared object: Operation not permitted",
# add the following line to the command below:
# -Djava.io.tmpdir=~/tmpdir/

# If you're using AMD graphics cards on Linux with the latest open-source Mesa drivers, you might get an
# error about not finding a supported OpenGL profile, to fix please change the classpath line below to:
# -classpath "dist/jAER.jar:jars/*:jars/jogl_mesa_amd_fix/*:lib/*" \
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
#DIR=`dirname "$0`

java \
-classpath "$DIR/dist/jAER.jar:$DIR/jars/*:$DIR/lib/*" \
-splash:images/SplashScreen.png -Dsun.java2d.uiScale=2.0 -Djogl.disable.openglcore \
-Djava.util.logging.config.file="$DIR/conf/Logging.properties" \
-Dsun.java2d.noddraw=true \
-Dsun.java2d.opengl=false \
-D-Jsun.java2d.dpiaware=true \
-Djava.library.path=/native/linux-86_64/libjavacan-core.so \
--add-exports java.base/java.lang=ALL-UNNAMED --add-exports java.desktop/sun.awt=ALL-UNNAMED --add-exports java.desktop/sun.java2d=ALL-UNNAMED net.sf.jaer.JAERViewer "$@"
