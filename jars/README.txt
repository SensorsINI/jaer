This folder of jAER (host/java/jars) holds the java archive libraries that cannot be sourced from maven central via ivy.
These jars are on the jAER project library paths.

Some of these libraries need Java Native Interface libraries (e.g. JOGL, USBIO).

For linux platforms, there are linux32 and linux64 folders. These folders hold the native libraries for a platform.
The jAER runtime configurations linux32 and linux64 change the java.library.path to specify these subfolders for running
on these platforms.
