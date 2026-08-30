param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

$ProjectRoot = $ProjectRoot.Trim().Trim('"')
$ProjectRoot = $ProjectRoot.TrimEnd('\', '/')
$backendPath = Join-Path $ProjectRoot "backend"
$mavenWrapper = Join-Path $backendPath "mvnw.cmd"

function Get-JavaMajorVersion {
    param([string]$JavaExe)

    if (-not (Test-Path -LiteralPath $JavaExe)) {
        return $null
    }

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $JavaExe
    $startInfo.Arguments = "-version"
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = [System.Diagnostics.Process]::Start($startInfo)
    $versionText = $process.StandardError.ReadToEnd() + $process.StandardOutput.ReadToEnd()
    $process.WaitForExit()

    if ($versionText -match 'version "([0-9]+)') {
        return [int]$matches[1]
    }

    return $null
}

function Resolve-JavaHome {
    $candidates = @()

    if ($env:JAVA_HOME) {
        $candidates += $env:JAVA_HOME
    }

    $knownRoots = @(
        "D:\JDK 21",
        "C:\Program Files\Java",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Microsoft",
        "C:\Program Files\Amazon Corretto"
    )

    foreach ($root in $knownRoots) {
        if (Test-Path -LiteralPath $root) {
            if (Test-Path -LiteralPath (Join-Path $root "bin\java.exe")) {
                $candidates += $root
            }
            else {
                $candidates += Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
                    Where-Object { $_.Name -match 'jdk|java|corretto|temurin|microsoft' } |
                    ForEach-Object { $_.FullName }
            }
        }
    }

    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        $javaExe = Join-Path $candidate "bin\java.exe"
        $major = Get-JavaMajorVersion $javaExe
        if ($major -ge 21) {
            return $candidate
        }
    }

    $pathJava = (Get-Command java.exe -ErrorAction SilentlyContinue).Source
    if ($pathJava) {
        $major = Get-JavaMajorVersion $pathJava
        if ($major -ge 21) {
            return (Split-Path (Split-Path $pathJava -Parent) -Parent)
        }
    }

    return $null
}

if (-not (Test-Path -LiteralPath $backendPath)) {
    throw "Backend directory was not found: $backendPath"
}

$resolvedJavaHome = Resolve-JavaHome
if (-not $resolvedJavaHome) {
    throw "JDK 21 or newer was not found. Please install JDK 21+ or set JAVA_HOME to a JDK 21+ directory."
}

$env:JAVA_HOME = $resolvedJavaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw "Maven wrapper was not found: $mavenWrapper"
}

Set-Location -LiteralPath $backendPath
Write-Host "Starting backend from $backendPath" -ForegroundColor Cyan
Write-Host "Using JAVA_HOME=$env:JAVA_HOME" -ForegroundColor Cyan
& $mavenWrapper spring-boot:run
