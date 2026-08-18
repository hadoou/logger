@echo off
echo Compiling EnhancedInjector for Java 8...
javac -source 1.8 -target 1.8 EnhancedInjector.java
if %errorlevel% neq 0 (
    echo.
    echo If error, try with bootclasspath:
    javac -source 1.8 -target 1.8 -bootclasspath "C:\Program Files\Java\jre1.8.0_*\lib\rt.jar" EnhancedInjector.java
)
echo Done.
pause
