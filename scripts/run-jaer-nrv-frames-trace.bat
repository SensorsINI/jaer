@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0.."

if not exist "build\classes\net\sf\jaer\JAERViewer.class" (
    echo build\classes is missing. Run once:  ant compile
    exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
    echo java not found in PATH.
    exit /b 1
)

set "TRACE_FILE=C:/temp/jaer-nrv-frames.csv"
if not "%~1"=="" set "TRACE_FILE=%~1"

set "JAER_CP=build\classes;lib\*;jars\*"

echo NRV frame/playback trace file=%TRACE_FILE%
echo   live: usb_frame_end, usb_commit, viewer_packet
echo   playback: playback_slice, playback_skip_render

java ^
  --add-exports java.base/java.lang=ALL-UNNAMED ^
  --add-exports java.desktop/sun.awt=ALL-UNNAMED ^
  --add-exports java.desktop/sun.java2d=ALL-UNNAMED ^
  -Djava.library.path=jars ^
  -Djava.util.logging.config.file=conf/Logging.properties ^
  -Djogl.disable.openglcore ^
  -Djogl.disable.opengles=false ^
  -Dsun.java2d.dpiaware=true ^
  -Dsun.java2d.noddraw=true ^
  -Dsun.java2d.opengl=false ^
  -Djaer.nrv.trace.frames=true ^
  -Djaer.nrv.trace.playback=true ^
  -Djaer.nrv.trace.frames.file=%TRACE_FILE% ^
  -Djaer.nrv.trace.frames.intervalMs=2000 ^
  -Xmx10g ^
  -Xrs ^
  -splash:SplashScreen.png ^
  -cp "%JAER_CP%" ^
  net.sf.jaer.JAERViewer

exit /b %ERRORLEVEL%
