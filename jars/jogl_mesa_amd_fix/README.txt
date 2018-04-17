JOGL (Java OpenGL) fails on recent Linux systems that use the latest Mesa (open-source) drivers with AMD graphics cards.
More information here:
  http://forum.jogamp.org/Mesa-17-2-0-renderer-driver-name-change-error-causes-GLProfile-not-mapped-initialization-error-td4038176.html
  https://jogamp.org/bugzilla/show_bug.cgi?id=1357

This directory contains a patched version of JOGL for 64-bit Linux that fixes the problem.
If you are experiencing this failure, please edit the 'jAERViewer_linux.sh' file to load these JARs.
An exact description of how to do so is included in the .sh file.
