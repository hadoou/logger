@echo off
chcp 65001 >nul
echo ==================================================
echo ModKaLogger Fabric 1.21.4 - Build and Inject
echo ==================================================

cd /d "%~dp0"

echo.
echo [1/4] Building ModKaLogger Fabric...
call gradlew.bat build -q --no-daemon
if %errorlevel% neq 0 (
    echo [!] Build failed!
    pause
    exit /b 1
)
echo [+] Build complete!

cd ..

echo.
echo [2/4] Copying JAR to root...
if not exist "fabric-1.21.4\build\libs\modkalogger-fabric-1.0.0.jar" (
    echo [!] JAR not found!
    dir /b fabric-1.21.4\build\libs\*.jar 2>nul
    pause
    exit /b 1
)
copy /y "fabric-1.21.4\build\libs\modkalogger-fabric-1.0.0.jar" "build\libs\" >nul 2>&1
echo [+] Copied!

echo.
echo [3/4] Compiling FabricInjector...

rem Находим javassist.jar
set JAVASSIST_JAR=libs/javassist.jar
if not exist "%JAVASSIST_JAR%" (
    for /r "%USERPROFILE%\.gradle\caches" %%f in (javassist*.jar) do set JAVASSIST_JAR=%%f
)

echo [*] Javassist: %JAVASSIST_JAR%

javac -source 1.8 -target 1.8 -cp ".;%JAVASSIST_JAR%" FabricInjector.java 2>nul
if %errorlevel% neq 0 (
    echo [!] Trying with -release flag...
    javac -release 8 -cp ".;%JAVASSIST_JAR%" FabricInjector.java
)

if %errorlevel% neq 0 (
    echo [!] Compilation failed!
    pause
    exit /b 1
)
echo [+] Injector compiled!

echo.
echo [4/4] Injecting...
if "%~1"=="" (
    echo Usage: build_and_inject.bat ^<input_fabric_mod.jar^> [output.jar]
    echo Example: build_and_inject.bat some_fabric_mod.jar output.jar
    pause
    exit /b 1
)

set INPUT=%~1
if "%~2"=="" (
    set OUTPUT=%~n1_injected%~x1
) else (
    set OUTPUT=%~2
)

java -cp ".;%JAVASSIST_JAR%" FabricInjector "%INPUT%" "%OUTPUT%"
if %errorlevel% neq 0 (
    echo [!] Injection failed!
    pause
    exit /b 1
)

echo.
echo ==================================================
echo [+] ALL DONE!
echo [+] Output: %OUTPUT%
echo ==================================================
pause
