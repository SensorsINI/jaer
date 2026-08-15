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

rem Dev launch: build\classes + lib/*.jar + jars/*.jar (skips ant ivy/compile on every start)
set "JAER_CP=build\classes;lib\*;jars\*"

rem ---------------------------------------------------------------------------
rem JVM -D / -X flags from PowerShell: unquoted -Dname=value is mangled.
rem Prefer one of:
rem   .\scripts\run-jaer-fast.bat --% -Djaer.live.bench=true -Djaer.live.bench.file=logs/live-bench.csv
rem   .\scripts\run-jaer-fast.bat "-Djaer.live.bench=true" "-Djaer.live.bench.file=logs/live-bench.csv"
rem   $env:JAER_JVM_ARGS='-Djaer.live.bench=true -Djaer.live.bench.file=logs/live-bench.csv'
rem   .\scripts\run-jaer-fast.bat
rem From cmd.exe, unquoted -D flags work as usual.
rem ---------------------------------------------------------------------------
set "JVM_EXTRA=%JAER_JVM_ARGS%"
set "APP_ARGS="

:argloop
if "%~1"=="" goto argdone
set "A=%~1"
if /I "!A:~0,2!"=="-D" (
    set "JVM_EXTRA=!JVM_EXTRA! "%~1""
) else if /I "!A:~0,2!"=="-X" (
    set "JVM_EXTRA=!JVM_EXTRA! "%~1""
) else if /I "!A:~0,2!"=="--" (
    set "JVM_EXTRA=!JVM_EXTRA! "%~1""
) else (
    set "APP_ARGS=!APP_ARGS! "%~1""
)
shift
goto argloop
:argdone

if not "!JVM_EXTRA!"=="" echo run-jaer-fast: JVM extras:!JVM_EXTRA!

rem WIP: compact object headers (JEP 519) until JEP 534 makes them the JVM default.
rem --add-opens jdk.internal.loader: TensorFlowNativeSupport hot-adds the OS native jar (JDK 25+).
java ^
  --add-exports java.base/java.lang=ALL-UNNAMED ^
  --add-exports java.desktop/sun.awt=ALL-UNNAMED ^
  --add-exports java.desktop/sun.java2d=ALL-UNNAMED ^
  --add-opens java.base/jdk.internal.loader=ALL-UNNAMED ^
  -Djava.library.path=jars ^
  -Djava.util.logging.config.file=conf/Logging.properties ^
  -Djogl.disable.openglcore ^
  -Djogl.disable.opengles=false ^
  -Dsun.java2d.dpiaware=true ^
  -Dsun.java2d.noddraw=true ^
  -Dsun.java2d.opengl=false ^
  -XX:+UseCompactObjectHeaders ^
  -Xmx10g ^
  -Xrs ^
  -splash:SplashScreen.png ^
  !JVM_EXTRA! ^
  -cp "%JAER_CP%" ^
  net.sf.jaer.JAERViewer !APP_ARGS!

exit /b %ERRORLEVEL%
