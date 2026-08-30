$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path $PSScriptRoot).Path
$backendScript = Join-Path $projectRoot "scripts\start-backend.ps1"
$frontendScript = Join-Path $projectRoot "scripts\start-frontend.ps1"
$powerShell = (Get-Command powershell.exe -ErrorAction Stop).Source

if (-not (Test-Path -LiteralPath $backendScript)) {
    throw "Missing backend start script: $backendScript"
}

if (-not (Test-Path -LiteralPath $frontendScript)) {
    throw "Missing frontend start script: $frontendScript"
}

Start-Process -FilePath $powerShell -ArgumentList @(
    "-NoProfile",
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$backendScript`"",
    "-ProjectRoot", "`"$projectRoot`""
) -WorkingDirectory $projectRoot

Start-Process -FilePath $powerShell -ArgumentList @(
    "-NoProfile",
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$frontendScript`"",
    "-ProjectRoot", "`"$projectRoot`""
) -WorkingDirectory $projectRoot

Write-Host "Backend and frontend have been started in separate PowerShell windows." -ForegroundColor Cyan
Write-Host "Frontend URL: http://localhost:5173" -ForegroundColor Cyan
