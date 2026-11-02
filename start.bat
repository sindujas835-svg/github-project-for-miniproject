@echo off
TITLE AI Intelligent Traffic Management System Launcher
COLOR 0A

echo ===============================================================================
echo       AI-POWERED INTELLIGENT TRAFFIC MANAGEMENT & EMERGENCY ROUTING
echo                          Spring Boot & Java 21 Backend
echo ===============================================================================
echo.

:: 1. Check for Java 21
java -version >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    COLOR 0C
    echo [ERROR] Java is not installed or not in your PATH.
    echo Please install Java 21 LTS and try again.
    pause
    exit /b 1
)

:: 2. Check if MySQL is running on port 3306
echo [1/3] Checking MySQL connection on port 3306...
netstat -ano | findstr :3306 >nul 2>&1
IF %ERRORLEVEL% EQU 0 (
    echo [OK] MySQL service detected on port 3306.
) ELSE (
    echo [INFO] Attempting to start MySQL Windows service...
    net start MySQL >nul 2>&1
    IF %ERRORLEVEL% NEQ 0 (
        net start MySQL80 >nul 2>&1
    )
    netstat -ano | findstr :3306 >nul 2>&1
    IF %ERRORLEVEL% EQU 0 (
        echo [OK] MySQL service successfully started on port 3306.
    ) ELSE (
        COLOR 0E
        echo [WARNING] MySQL does not appear to be listening on port 3306.
        echo Please ensure MySQL Server service is started (net start mysql or net start MySQL80).
        COLOR 0A
    )
)

:: 3. Launch Frontend Dashboard Server and open in Default Web Browser
echo [2/3] Launching Web Dashboard at http://localhost:3000 ...
start "AI Traffic Frontend Server" /min node "%~dp0serve.js"
timeout /t 2 /nobreak >nul
start "" "http://localhost:3000"

:: 4. Start Spring Boot Backend
echo [3/3] Starting Spring Boot Traffic Management Backend...
echo -------------------------------------------------------------------------------
cd /d "%~dp0traffic-backend\traffic-backend"
call .\mvnw.cmd spring-boot:run

pause
