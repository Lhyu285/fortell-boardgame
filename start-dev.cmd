@echo off
setlocal
chcp 65001 >nul

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "BACKEND_SCRIPT=%ROOT%\scripts\start-backend.ps1"
set "FRONTEND_SCRIPT=%ROOT%\scripts\start-frontend.ps1"

pushd "%ROOT%" >nul 2>nul
if errorlevel 1 (
  echo Failed to enter project directory: %ROOT%
  pause
  exit /b 1
)

where powershell.exe >nul 2>nul
if errorlevel 1 (
  echo powershell.exe was not found in PATH.
  pause
  exit /b 1
)

if not exist "%BACKEND_SCRIPT%" (
  echo Missing backend start script: %BACKEND_SCRIPT%
  pause
  exit /b 1
)

if not exist "%FRONTEND_SCRIPT%" (
  echo Missing frontend start script: %FRONTEND_SCRIPT%
  pause
  exit /b 1
)

start "Boardgame Backend" /D "%ROOT%" powershell.exe -NoProfile -NoExit -ExecutionPolicy Bypass -File "%BACKEND_SCRIPT%" -ProjectRoot "%ROOT%"
start "Boardgame Frontend" /D "%ROOT%" powershell.exe -NoProfile -NoExit -ExecutionPolicy Bypass -File "%FRONTEND_SCRIPT%" -ProjectRoot "%ROOT%"

echo Backend and frontend have been started in separate PowerShell windows.
echo Frontend URL: http://localhost:5173
echo Close the Backend and Frontend windows to stop the project.
popd >nul
pause
