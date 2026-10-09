@echo off
setlocal
echo [LiteMiner Farming Addon] Building mod jar...
call gradlew.bat build

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed!
    pause
    exit /b %ERRORLEVEL%
)

echo [SUCCESS] Mod built successfully! Output located in build\libs\
pause
