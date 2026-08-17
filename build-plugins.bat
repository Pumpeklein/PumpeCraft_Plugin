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

set "INVSEE_CACHE=%CD%\.gradle\external-plugins\InvSeePlusPlus-0.31.1.jar"
set "INVSEE_URL=https://repo.repsy.io/mvn/jannyboy11/minecraft/com/janboerman/invsee/invsee-plus-plus_plugin/0.31.1-SNAPSHOT/invsee-plus-plus_plugin-0.31.1-20251215.233604-1.jar"

if not exist "%INVSEE_CACHE%" (
    echo Lade InvSeePlusPlus fuer Offline-Inventare...
    if not exist "%CD%\.gradle\external-plugins" mkdir "%CD%\.gradle\external-plugins"
    powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command ^
        "Invoke-WebRequest -UseBasicParsing -Uri '%INVSEE_URL%' -OutFile '%INVSEE_CACHE%'"
    if errorlevel 1 (
        echo.
        echo [FEHLER] InvSeePlusPlus konnte nicht geladen werden.
        set "EXIT_CODE=1"
        goto :finish
    )
)

copy /Y "%INVSEE_CACHE%" "%CD%\build\plugins\InvSeePlusPlus-0.31.1.jar" >nul
if errorlevel 1 (
    echo.
    echo [FEHLER] InvSeePlusPlus konnte nicht in den Ausgabeordner kopiert werden.
    set "EXIT_CODE=1"
    goto :finish
)

echo.
echo [ERFOLG] Alle Plugins wurden gebaut.
echo Ausgabe: "%CD%\build\plugins"

:finish
if "%PAUSE_AT_END%"=="1" pause
exit /b %EXIT_CODE%
