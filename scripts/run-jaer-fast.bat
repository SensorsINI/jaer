@echo off
setlocal EnableExtensions
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
  -Xmx10g ^
  -Xrs ^
  -splash:SplashScreen.png ^
  -cp "%JAER_CP%" ^
  net.sf.jaer.JAERViewer %*

exit /b %ERRORLEVEL%
