@echo off
echo ================================================
echo Compiling EnhancedInjector with ASM...
echo ================================================

set ASM_JAR=
for %%f in ("%USERPROFILE%\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\*\*.jar") do set ASM_JAR=%%f

if "%ASM_JAR%"=="" (
    echo [!] ASM jar not found in Gradle cache
    echo [*] Trying to find in build/libs...
    
    rem Попробуем найти в build
    for %%f in ("build\libs\*.jar") do set ASM_JAR=%%f
)

echo [*] Using classpath: %ASM_JAR%

rem Компилируем для Java 8
javac -source 1.8 -target 1.8 -cp ".;%ASM_JAR%" EnhancedInjector.java

if %errorlevel% neq 0 (
    echo.
    echo [!] Compilation failed. Trying without ASM (will need Gradle)...
    echo [*] Run: gradlew build
    pause
    exit /b 1
)

echo.
echo [+] Compilation successful!
echo [+] Now run: java EnhancedInjector ias.jar ias_injected.jar
pause
