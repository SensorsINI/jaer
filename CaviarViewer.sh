# this script starts CaviarViewer under linux if it is run from this folder
java -DJOGL_SINGLE_THREADED_WORKAROUND=false -Djava.library.path=JNI -Dsun.java2d.opengl=false -Dsun.java2d.noddraw=true -cp jars/spread.jar:dist/usb2aemon.jar:jars/UsbIoJava.jar:jars/swing-layout-0.9.jar:jars/jogl.jar ch.unizh.ini.caviar.CaviarViewer 
