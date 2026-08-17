@echo off
setlocal

cd /d "%~dp0"

set "PAUSE_AT_END=1"
if /I "%~1"=="--no-pause" set "PAUSE_AT_END=0"

if not exist "gradlew.bat" (
    echo [FEHLER] gradlew.bat wurde im Projektordner nicht gefunden.
    set "EXIT_CODE=1"
    goto :finish
)

echo Baue alle PumpeCraft-Plugins...
rem Voller Pfad, damit der Aufruf auch bei gesetztem
rem NoDefaultCurrentDirectoryInExePath funktioniert.
call "%~dp0gradlew.bat" clean build collectPluginJars --warning-mode all
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo [FEHLER] Der Build ist mit Exit-Code %EXIT_CODE% fehlgeschlagen.
    goto :finish
)

echo.
echo [ERFOLG] Alle Plugins wurden gebaut.
echo Ausgabe: "%CD%\build\plugins"

:finish
if "%PAUSE_AT_END%"=="1" pause
exit /b %EXIT_CODE%
