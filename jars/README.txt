This folder of jAER (host/java/jars) holds the java archive libraries.
These jars are on the jAER project library paths.

Some of these libraries need Java Native Interface libraries (e.g. JOGL, USBIO).

For linux platforms, there are linux32 and linux64 folders. These folders hold the native librarys for a platform.
The jAER runtime configurations linux32 and linux64 change the java.library.path to specify these subfolders for running
on these platforms.

Note that you should always use a 32 bit JVM (Java Virtual Machine) to run jAER even when running on a 64 bit machine running
a 64 bit native OS. E.g. on Windows 7 x64, you should still use a 32 bit JVM.

