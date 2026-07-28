@echo off
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (mvn %* & exit /b %ERRORLEVEL%)
set JAR=%~dp0.mvn\wrapper\maven-wrapper.jar
if not exist "%JAR%" (echo Maven and Maven Wrapper JAR are unavailable. & exit /b 1)
java -jar "%JAR%" %*
