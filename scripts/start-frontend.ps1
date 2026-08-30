param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

$ProjectRoot = $ProjectRoot.Trim().Trim('"')
$ProjectRoot = $ProjectRoot.TrimEnd('\', '/')
$frontendPath = Join-Path $ProjectRoot "frontend"

if (-not (Test-Path -LiteralPath $frontendPath)) {
    throw "Frontend directory was not found: $frontendPath"
}

Set-Location -LiteralPath $frontendPath
$npmCommand = (Get-Command npm.cmd -ErrorAction SilentlyContinue).Source

if (-not $npmCommand) {
    throw "npm.cmd was not found in PATH."
}

if (-not (Test-Path "node_modules")) {
    Write-Host "Installing frontend dependencies..." -ForegroundColor Yellow
    & $npmCommand install
}

Write-Host "Starting frontend from $frontendPath" -ForegroundColor Cyan
& $npmCommand run dev -- --host 0.0.0.0
