@echo off
chcp 65001 >nul
echo ============================================
echo Minecraft 1.16.5 Injector
echo ============================================

if "%~1"=="" (
    echo Usage: inject_mc.bat ^<forge.jar^> [output.jar]
    echo Example: inject_mc.bat forge-1.16.5-36.2.42.jar forge_injected.jar
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

java -cp ".;../libs/javassist.jar" MinecraftInjector "%INPUT%" "%OUTPUT%"

if %errorlevel% neq 0 (
    echo.
    echo [!] Injection failed!
    pause
    exit /b 1
)

echo.
pause
