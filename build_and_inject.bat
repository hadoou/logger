@echo off
chcp 65001 >nul
echo ==================================================
echo ModKaLogger Build and Inject
echo ==================================================

echo.
echo [1/3] Building ModKaLogger...
call gradlew.bat build -q
if %errorlevel% neq 0 (
    echo [!] Build failed!
    pause
    exit /b 1
)
echo [+] Build complete!

echo.
echo [2/3] Compiling injector...

rem Находим javassist.jar в кэше Gradle
set JAVASSIST_JAR=
for /r "%USERPROFILE%\.gradle\caches" %%f in (javassist*.jar) do set JAVASSIST_JAR=%%f

if "%JAVASSIST_JAR%"=="" (
    echo [!] Javassist not found in Gradle cache
    echo [*] Downloading javassist...
    
    rem Пробуем скачать через Gradle
    echo. > temp_build.gradle
    echo repositories { mavenCentral() } >> temp_build.gradle
    echo configurations { inj } >> temp_build.gradle
    echo dependencies { inj 'org.javassist:javassist:3.29.2-GA' } >> temp_build.gradle
    echo task copyDeps(type: Copy) { from configurations.inj into 'libs' } >> temp_build.gradle
    
    call gradlew.bat -b temp_build.gradle copyDeps -q
    del temp_build.gradle
    
    for %%f in (libs\javassist*.jar) do set JAVASSIST_JAR=%%f
)

echo [*] Javassist: %JAVASSIST_JAR%

rem Компилируем инжектор
javac -source 1.8 -target 1.8 -cp ".;%JAVASSIST_JAR%" EnhancedInjector.java 2>nul
if %errorlevel% neq 0 (
    echo [!] Trying with -release flag...
    javac -release 8 -cp ".;%JAVASSIST_JAR%" EnhancedInjector.java
)

if %errorlevel% neq 0 (
    echo [!] Compilation failed!
    pause
    exit /b 1
)
echo [+] Injector compiled!

echo.
echo [3/3] Injecting...
java -cp ".;%JAVASSIST_JAR%" EnhancedInjector ias.jar ias_injected.jar
if %errorlevel% neq 0 (
    echo [!] Injection failed!
    pause
    exit /b 1
)

echo.
echo ==================================================
echo [+] ALL DONE!
echo [+] Output: ias_injected.jar
echo ==================================================
pause
