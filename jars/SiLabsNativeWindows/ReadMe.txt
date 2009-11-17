Tobi updated to USBXPress 3.1.1 on 17.11.2009. Replaced DLL, lib and header here.
Developing on Windows 7 platform.



This folder holds the java native code to support the Silicon Labs C8051F320 microcontroller
usb interface to monitor AEs and control onchip biasgenerators.

makeJNIHeader.cmd creates the JNI header file based on the 
compiled java class for the SiLabsC8051F320. Then this header must be included in the project
and all the function calls in the SiLabsC8051F320.c must match the header functions.

The produced SiLabsC8051F320.dll and the SiUSBXp.dll must be accessible to the java virtual machine.
The SiLabsC8051F320.dll is loaded by the System.loadLibrary() method but the associated SiUSBXp.dll
must also be on the Windows PATH, or there will be an error.


========================================================================
       DYNAMIC LINK LIBRARY : usbaemon
========================================================================


AppWizard has created this usbaemon DLL for you.  

This file contains a summary of what you will find in each of the files that
make up your usbaemon application.

usbaemon.dsp
    This file (the project file) contains information at the project level and
    is used to build a single project or subproject. Other users can share the
    project (.dsp) file, but they should export the makefiles locally.

usbaemon.cpp
    This is the main DLL source file.

	When created, this DLL does not export any symbols. As a result, it 
	will not produce a .lib file when it is built. If you wish this project
	to be a project dependency of some other project, you will either need to 
	add code to export some symbols from the DLL so that an export library 
	will be produced, or you can check the "doesn't produce lib" checkbox in 
	the Linker settings page for this project. 

/////////////////////////////////////////////////////////////////////////////
Other standard files:

StdAfx.h, StdAfx.cpp
    These files are used to build a precompiled header (PCH) file
    named usbaemon.pch and a precompiled types file named StdAfx.obj.


/////////////////////////////////////////////////////////////////////////////
Other notes:

AppWizard uses "TODO:" to indicate parts of the source code you
should add to or customize.


/////////////////////////////////////////////////////////////////////////////
