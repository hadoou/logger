@echo off
echo Building ModKaLogger Fabric 1.21.4...
cd /d "%~dp0"

if not exist "gradlew.bat" (
    echo Downloading Gradle Wrapper...
    curl -o gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar
    if not exist "gradle" mkdir gradle
    if not exist "gradle\wrapper" mkdir gradle\wrapper
    move gradle-wrapper.jar gradle\wrapper\ >nul 2>&1
    curl -o gradlew.bat https://raw.githubusercontent.com/gradle/gradle/master/gradlew.bat
)

call gradlew.bat build --no-daemon
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo.
echo Build complete!
echo Output: build\libs\
dir /b build\libs\*.jar 2>nul
pause
