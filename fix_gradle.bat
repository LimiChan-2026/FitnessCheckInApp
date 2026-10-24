@echo off
echo ========================================
echo Gradle 缓存清理和修复脚本
echo ========================================
echo.

echo [1/5] 清理项目本地缓存...
if exist ".gradle" (
    rmdir /s /q ".gradle"
    echo 已删除 .gradle 文件夹
)
if exist "app\build" (
    rmdir /s /q "app\build"
    echo 已删除 app\build 文件夹
)
if exist "build" (
    rmdir /s /q "build"
    echo 已删除 build 文件夹
)
echo.

echo [2/5] 清理全局 Gradle 缓存...
echo 注意：这将删除 %USERPROFILE%\.gradle\caches
echo 按任意键继续，或 Ctrl+C 取消...
pause > nul
if exist "%USERPROFILE%\.gradle\caches" (
    rmdir /s /q "%USERPROFILE%\.gradle\caches"
    echo 已删除全局 Gradle 缓存
)
echo.

echo [3/5] 清理 Gradle daemon...
call gradlew --stop
echo.

echo [4/5] 重新下载依赖...
call gradlew --refresh-dependencies
echo.

echo [5/5] 执行 clean build...
call gradlew clean
echo.

echo ========================================
echo 清理完成！
echo ========================================
echo.
echo 下一步操作：
echo 1. 在 Android Studio 中设置 Gradle JDK 为 17
echo    File ^> Settings ^> Build Tools ^> Gradle ^> Gradle JDK
echo 2. 点击 "Sync Project with Gradle Files"
echo.
pause
