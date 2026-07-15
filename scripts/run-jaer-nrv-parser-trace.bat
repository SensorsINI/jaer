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

set "PARSER_FILE=C:/temp/jaer-nrv-parser.csv"
if not "%~1"=="" set "PARSER_FILE=%~1"

set "JAER_CP=build\classes;lib\*;jars\*"

echo NRV parser trace (live USB): per-ms buckets + per-frame-end events
echo   CSV file=%PARSER_FILE%
echo   INFO summary every 2s only (no per-bucket INFO — that stalls USB at 1 kHz)
echo   Optional: -Djaer.nrv.trace.parser.sampleLog=true for a few examples per interval

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
  -Djaer.nrv.trace.parser=true ^
  -Djaer.nrv.trace.parser.file=%PARSER_FILE% ^
  -Djaer.nrv.trace.parser.intervalMs=2000 ^
  -Xmx10g ^
  -Xrs ^
  -splash:SplashScreen.png ^
  -cp "%JAER_CP%" ^
  net.sf.jaer.JAERViewer

exit /b %ERRORLEVEL%
