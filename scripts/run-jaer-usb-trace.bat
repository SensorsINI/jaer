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

set "CAMERA=%~1"
if /i "%CAMERA%"=="nrv" (
    set "TRACE_FILE=C:/temp/jaer-usb-pipeline-nrv.csv"
    goto do_run
)
if /i "%CAMERA%"=="evk4" (
    set "TRACE_FILE=C:/temp/jaer-usb-pipeline-evk4.csv"
    goto do_run
)
if /i "%CAMERA%"=="prophesee" (
    set "TRACE_FILE=C:/temp/jaer-usb-pipeline-evk4.csv"
    goto do_run
)

echo Usage: %~nx0 ^<nrv^|evk4^> [JAERViewer args...]
echo.
echo   nrv       NRV DELTA01  -^> C:/temp/jaer-usb-pipeline-nrv.csv
echo   evk4      Prophesee EVK4 -^> C:/temp/jaer-usb-pipeline-evk4.csv
echo   prophesee same as evk4
echo.
echo Examples:
echo   %~nx0 nrv
echo   %~nx0 evk4
echo.
echo Run each camera separately, then compare the two CSV files.
exit /b 1

:do_run
rem Drop camera token (%1). Do NOT use %%* here — cmd expands %%* at parse time, before shift.
shift
set "JAER_ARGS="
:collect_jaer_args
if "%~1"=="" goto run_jaer
set "JAER_ARGS=!JAER_ARGS! "%~1""
shift
goto collect_jaer_args

:run_jaer
rem Same JVM flags as scripts/run-jaer-fast.bat (required for JOGL on Windows).
rem Do NOT use "ant -Drun.jvmargs=..." with only trace flags — that replaces defaults.
set "JAER_CP=build\classes;lib\*;jars\*"

echo USB pipeline trace: camera=%CAMERA% file=%TRACE_FILE%

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
  -Djaer.usb.trace.pipeline=true ^
  -Djaer.usb.trace.file=%TRACE_FILE% ^
  -Djaer.usb.trace.intervalMs=2000 ^
  -Xmx10g ^
  -Xrs ^
  -splash:SplashScreen.png ^
  -cp "%JAER_CP%" ^
  net.sf.jaer.JAERViewer !JAER_ARGS!

exit /b %ERRORLEVEL%
