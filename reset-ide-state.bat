@echo off
REM ============================================================
REM  Android Studio project-import state reset (safe, reversible)
REM  Fixes IDE-side sync corruption (e.g. "Cause: index == 7")
REM
REM  What it does:
REM   1. Checks Android Studio is CLOSED (required)
REM   2. Renames .idea  ->  .idea.bak  (your old IDE settings, reversible)
REM   3. Deletes .gradle project-local cache (auto-regenerated)
REM   4. Stops Gradle daemons
REM
REM  After running: open the project again in Android Studio,
REM  it will re-import from scratch like a fresh checkout.
REM ============================================================

setlocal
cd /d "%~dp0"

echo ==============================================
echo  Resetting Android Studio project-import state
echo ==============================================

tasklist /FI "IMAGENAME eq studio64.exe" 2>nul | find /I "studio64.exe" >nul
if not errorlevel 1 (
    echo [ERROR] Android Studio is still running.
    echo Please close Android Studio completely first, then run this script again.
    pause
    exit /b 1
)

if exist ".idea.bak" rmdir /s /q ".idea.bak"
if exist ".idea" (
    ren ".idea" ".idea.bak"
    echo [OK] .idea  -^> .idea.bak   (old IDE settings backed up)
) else (
    echo [SKIP] .idea not found
)

if exist ".gradle" (
    rmdir /s /q ".gradle"
    echo [OK] .gradle cache deleted  (auto-regenerated on next sync)
)

call gradlew.bat --stop >nul 2>&1
echo [OK] Gradle daemons stopped

echo.
echo ==============================================
echo  Done! Next steps:
echo   1. Open Android Studio
echo   2. File - Open -^> D:\实习\课程设计\FitnessCheckInApp
echo   3. Trust project, wait for Gradle sync
echo  If sync still fails: File - Invalidate Caches,
echo  check ALL boxes, Invalidate and RESTART.
echo ==============================================
pause
