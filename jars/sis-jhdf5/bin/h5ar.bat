@echo off

java -Dnative.libpath=%~dp0..\lib\native -jar %~dp0..\lib\sis-jhdf5-tools.jar %1 %2 %3 %4 %5 %6 %7 %8 %9
