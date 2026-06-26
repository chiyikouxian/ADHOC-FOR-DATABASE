@echo off
setlocal

if "%BACKEND_PORT%"=="" set BACKEND_PORT=18080

echo Starting FANET backend bootstrap on port %BACKEND_PORT%...
powershell -ExecutionPolicy Bypass -File "%~dp0start.ps1"

endlocal
