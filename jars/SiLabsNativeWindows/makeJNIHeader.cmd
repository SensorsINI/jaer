rem makes the JNI header for the native methods for the SiLabs C8051F320 interface
c:
pushd ..\..\build\classes
"C:\Program Files\Java\jdk1.5.0_01\bin\javah" ch.unizh.ini.caviar.hardwareinterface.usb.SiLabsC8051F320
move ch_unizh_ini_caviar_hardwareinterface_usb_SiLabsC8051F320.h ..\..\JNI\SiLabsNativeWindows
popd 
pause