$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = $PSScriptRoot
$jarPath = Join-Path $backendDir "target\fanet-platform-0.1.0-SNAPSHOT.jar"
$backendPort = if ($env:BACKEND_PORT) { $env:BACKEND_PORT } else { "18080" }

$mavenVer = "3.9.6"
$mavenBase = Join-Path $env:TEMP "apache-maven-$mavenVer"
$mvnCmd = Join-Path $mavenBase "bin\mvn.cmd"
$mavenUrl = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVer/apache-maven-$mavenVer-bin.zip"
$zipPath = Join-Path $env:TEMP "apache-maven-$mavenVer-bin.zip"

function Ensure-Maven {
    if (Test-Path $mvnCmd) {
        return
    }

    Write-Host "Downloading Maven $mavenVer ..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri $mavenUrl -OutFile $zipPath
    Expand-Archive -Path $zipPath -DestinationPath $env:TEMP -Force
}

function Ensure-Java {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaCmd) {
        throw "java not found in PATH. Please install JDK 17+ and reopen the terminal."
    }
}

function Stop-ExistingBackend {
    $existing = Get-CimInstance Win32_Process |
        Where-Object {
            $_.Name -eq "java.exe" -and
            $_.CommandLine -like "*fanet-platform-0.1.0-SNAPSHOT.jar*"
        }

    if (-not $existing) {
        return
    }

    foreach ($proc in $existing) {
        Write-Host "Stopping existing FANET backend process PID $($proc.ProcessId) ..." -ForegroundColor Yellow
        Stop-Process -Id $proc.ProcessId -Force
    }

    Start-Sleep -Seconds 2
}

function Assert-PortAvailable {
    $listeners = Get-NetTCPConnection -LocalPort $backendPort -State Listen -ErrorAction SilentlyContinue
    if (-not $listeners) {
        return
    }

    $pids = $listeners | Select-Object -ExpandProperty OwningProcess -Unique
    $details = foreach ($pid in $pids) {
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId = $pid"
        if ($proc) {
            "$pid ($($proc.Name))"
        } else {
            "$pid"
        }
    }

    throw "Port $backendPort is still in use by: $($details -join ', '). Please stop that process and try again."
}

Ensure-Java
Ensure-Maven
Stop-ExistingBackend
Assert-PortAvailable

Write-Host "Building FANET backend ..." -ForegroundColor Cyan
Push-Location $backendDir
try {
    & $mvnCmd clean package -DskipTests
} finally {
    Pop-Location
}

if (-not (Test-Path $jarPath)) {
    throw "Build completed but JAR not found: $jarPath"
}

Write-Host "Starting FANET backend on port $backendPort ..." -ForegroundColor Green
& java -Xmx128m -XX:+UseSerialGC -jar $jarPath --server.port=$backendPort
