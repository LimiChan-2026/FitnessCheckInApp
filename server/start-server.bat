@echo off
REM ============================================================
REM  Fitness Check-in Server Launcher (Windows)
REM  1. Maps X: to the project root (avoids Chinese-path issues
REM     in JDK toolchain on Chinese Windows, JDK-8272597)
REM  2. Compiles and starts the Javalin REST API on port 8080
REM  Emulator URL : http://10.0.2.2:8080
REM  Real device  : http://PC-LAN-IP:8080 (same Wi-Fi)
REM ============================================================

setlocal
echo ==============================================
echo  Fitness Check-in Server Launcher
echo ==============================================

REM Map X: to project root (parent of this script's folder)
if exist "X:\gradlew.bat" goto mapped
subst X: "%~dp0.."
if errorlevel 1 (
    echo [ERROR] Cannot map drive X:. Try running as administrator,
    echo         or execute manually:  subst X: "%~dp0.."
    pause
    exit /b 1
)
:mapped

echo [1/2] Compiling server...
cd /d X:\server
call X:\gradlew.bat :server:compileJava --console=plain -q
if errorlevel 1 (
    echo [ERROR] Compile failed. Check messages above.
    pause
    exit /b 1
)

echo [2/2] Starting server on port 8080 ... (press Ctrl+C to stop)
echo.
call X:\gradlew.bat :server:run --console=plain -q

pause
