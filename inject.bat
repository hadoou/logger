@echo off
chcp 65001 >nul
echo ============================================
echo ModKaLogger Multi-Injector
echo ============================================

if "%~1"=="" (
    echo Usage: inject.bat ^<input.jar^> [output.jar]
    echo Example: inject.bat ias.jar ias_injected.jar
    pause
    exit /b 1
)

set INPUT=%~1
if "%~2"=="" (
    set OUTPUT=%~n1_injected%~x1
) else (
    set OUTPUT=%~2
)

echo.
echo Input: %INPUT%
echo Output: %OUTPUT%
echo.

java -cp ".;libs/javassist.jar" EnhancedInjector "%INPUT%" "%OUTPUT%"

if %errorlevel% neq 0 (
    echo.
    echo [!] Injection failed!
    pause
    exit /b 1
)

echo.
pause
