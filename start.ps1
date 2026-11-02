# PowerShell Startup Script for AI Traffic Management Prototype
Write-Host "===============================================================================" -ForegroundColor Green
Write-Host "      AI-POWERED INTELLIGENT TRAFFIC MANAGEMENT & EMERGENCY ROUTING" -ForegroundColor Cyan
Write-Host "                         Spring Boot & Java 21 Backend" -ForegroundColor White
Write-Host "===============================================================================" -ForegroundColor Green
Write-Host ""

# 1. Verify Java 21
try {
    $javaVer = java -version 2>&1 | Select-String "version"
    Write-Host "[OK] Java runtime detected: $javaVer" -ForegroundColor Green
}
catch {
    Write-Host "[ERROR] Java is not installed or not in your PATH. Please install Java 21." -ForegroundColor Red
    exit 1
}

# 2. Check MySQL Port 3306
$portOpen = Test-NetConnection -ComputerName localhost -Port 3306 -InformationLevel Quiet -WarningAction SilentlyContinue
if (-not $portOpen) {
    Write-Host "[1/3] [INFO] Attempting to start MySQL Windows service..." -ForegroundColor Cyan
    Start-Service -Name "MySQL" -ErrorAction SilentlyContinue
    if (-not $?) {
        Start-Service -Name "MySQL80" -ErrorAction SilentlyContinue
    }
    $portOpen = Test-NetConnection -ComputerName localhost -Port 3306 -InformationLevel Quiet -WarningAction SilentlyContinue
}

if ($portOpen) {
    Write-Host "[1/3] [OK] MySQL server active on port 3306." -ForegroundColor Green
}
else {
    Write-Host "[1/3] [WARN] MySQL is not responding on port 3306. Ensure MySQL service is running." -ForegroundColor Yellow
}

# 3. Launch Frontend Dashboard Server
$serveScript = Join-Path $PSScriptRoot "serve.js"
Write-Host "[2/3] Launching Web Dashboard at http://localhost:3000 ..." -ForegroundColor Cyan
Start-Process -FilePath "node" -ArgumentList "`"$serveScript`"" -WindowStyle Hidden
Start-Sleep -Seconds 2
Start-Process "http://localhost:3000"

# 4. Start Spring Boot Backend
$backendDir = Join-Path $PSScriptRoot "traffic-backend\traffic-backend"
Write-Host "[3/3] Starting Spring Boot Server from $backendDir..." -ForegroundColor Cyan
Write-Host "-------------------------------------------------------------------------------" -ForegroundColor Gray
Set-Location $backendDir
& .\mvnw.cmd spring-boot:run
