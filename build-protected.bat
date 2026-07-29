@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-protected.ps1"
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" (
    echo.
    echo Ошибка защищённой сборки. Код: %EXIT_CODE%
    pause
    exit /b %EXIT_CODE%
)
echo.
pause
exit /b 0
