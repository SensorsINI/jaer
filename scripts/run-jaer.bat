@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

call :try_ant ant
if not errorlevel 1 exit /b 0

if defined NETBEANS_HOME call :try_ant "%NETBEANS_HOME%\extide\ant\bin\ant.bat"
if not errorlevel 1 exit /b 0

call :try_ant "C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
if not errorlevel 1 exit /b 0

for /d %%D in ("C:\Program Files\NetBeans*") do call :try_ant "%%D\extide\ant\bin\ant.bat"
if not errorlevel 1 exit /b 0

for /d %%D in ("C:\Program Files\apache-ant*") do call :try_ant "%%D\bin\ant.bat"
if not errorlevel 1 exit /b 0

echo ant not found in PATH.
echo Install Apache Ant, add it to PATH, or set NETBEANS_HOME.
exit /b 1

:try_ant
if "%~1"=="" exit /b 1
if "%~1"=="ant" (
    where ant >nul 2>&1
    if errorlevel 1 exit /b 1
    ant run
    exit /b %ERRORLEVEL%
)
if not exist "%~1" exit /b 1
call "%~1" run
exit /b %ERRORLEVEL%
