@echo off
setlocal
set "MAVEN_PROJECTBASEDIR=%~dp0"
"%MAVEN_PROJECTBASEDIR%.mvn\wrapper\apache-maven-3.9.11\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
