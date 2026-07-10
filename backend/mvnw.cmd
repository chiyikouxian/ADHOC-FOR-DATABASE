@echo off
setlocal enabledelayedexpansion

set "WRAPPER_PROPS=%~dp0.mvn\wrapper\maven-wrapper.properties"
set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists"

:: Read distributionUrl from properties file
for /f "usebackq tokens=1,* delims==" %%a in ("%WRAPPER_PROPS%") do (
    if "%%a"=="distributionUrl" set "DIST_URL=%%b"
)

:: Extract zip filename and folder name
for %%i in ("%DIST_URL%") do set "ZIP_NAME=%%~nxi"
for %%i in ("%DIST_URL%") do set "DIST_NAME=%%~ni"

:: After extract, find mvn.cmd recursively under dists folder
set "MVN_CMD="
for /f "delims=" %%f in ('dir /s /b "%MAVEN_HOME%\mvn.cmd" 2^>nul') do (
    set "MVN_CMD=%%f"
    goto :found
)

:: Not found - need to download
echo Maven not found locally. Downloading %DIST_URL% ...
if not exist "%MAVEN_HOME%" mkdir "%MAVEN_HOME%"
powershell -NoProfile -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%TEMP%\%ZIP_NAME%'"
if errorlevel 1 (
    echo ERROR: Failed to download Maven. Check your network.
    exit /b 1
)
echo Extracting...
powershell -NoProfile -Command "Expand-Archive -Path '%TEMP%\%ZIP_NAME%' -DestinationPath '%MAVEN_HOME%' -Force"
del "%TEMP%\%ZIP_NAME%" 2>nul

:: Search again after extraction
for /f "delims=" %%f in ('dir /s /b "%MAVEN_HOME%\mvn.cmd" 2^>nul') do (
    set "MVN_CMD=%%f"
    goto :found
)

echo ERROR: Could not find mvn.cmd after extraction.
exit /b 1

:found
"%MVN_CMD%" %*
