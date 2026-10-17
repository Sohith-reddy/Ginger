@echo off
setlocal
set "GINGER_JAR=%~dp0target\ginger-0.1.0.jar"

if not exist "%GINGER_JAR%" (
    echo Error: Ginger JAR not found at "%GINGER_JAR%". 1>&2
    echo Build it first by running "mvn clean package" in "%~dp0". 1>&2
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    echo Error: java was not found on PATH. Install JDK 21 or later. 1>&2
    exit /b 1
)

java -jar "%GINGER_JAR%" %*
exit /b %ERRORLEVEL%
