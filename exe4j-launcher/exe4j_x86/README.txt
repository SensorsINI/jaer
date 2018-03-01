-------------------------------------------------------------------------
exe4j readme

version 4.4.6
released on 2012-06-30
-------------------------------------------------------------------------

I. LICENSE

The license agreement (EULA) can be found in license.txt in the same
directory as this readme file.


II. RUNNING exe4j

1. WINDOWS 98/ME/2000/XP

Start exe4j by executing

   [exe4j install directory]\bin\exe4j.exe
   
   
2. LINUX X86, SOLARIS SPARC

Start exe4j by executing the shell script

   [exe4j install directory]/bin/exe4j


3. MAC OS X

Start exe4j with the installed application bundle. Usually, exe4j 
will be installed to:

   /Applications/exe4j



III. Upgrading exe4j

You may install a new version of exe4j on top of an older version.
Your old configuration files are upwards compatible.


IV. DOCUMENTATION

Help is available 

1. from the "Help" button in the exe4j wizard
2. as HTML pages the doc directory of your exe4j installation.
   Start with index.html or open any other HTML file directly.
3. online at http://resources.ej-technologies.com/exe4j/help/doc/
4. by executing exe4jc.exe --help


V. DIRECTORY LAYOUT

An installation of exe4j contains the following directories:

   bin
         contains the executables for exe4j.
   
   config
         contains all configuration files for exe4j
   
   demo
         contains demo configuration files and applications
         to try out exe4j
         
   doc
         contains the documentation for exe4j
   
   lib
         contains external libraries used by exe4j. If the libraries
         come with an different license, it is reproduced in that
         directory.
         
   resource
         contains resources for the exe4j compiler