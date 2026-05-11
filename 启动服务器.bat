@echo off
chcp 65001 >nul
echo ═══════════════════════════════════════════════
echo   飞机大战 - 多人对战服务器启动脚本
echo ═══════════════════════════════════════════════
echo.

cd /d "%~dp0"

echo 检查Java环境...
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ✗ 错误：未找到Java环境
    echo 请安装JDK 11或更高版本
    pause
    exit /b 1
)
echo ✓ Java环境正常
echo.

echo 检查端口8080是否被占用...
netstat -ano | findstr ":8080" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo ⚠ 警告：端口8080已被占用
    echo 请关闭占用8080端口的程序，或修改服务器端口
    echo.
    pause
)

echo 正在编译服务器...
call gradlew.bat :server:build --quiet
if %ERRORLEVEL% NEQ 0 (
    echo ✗ 服务器编译失败
    pause
    exit /b 1
)
echo ✓ 编译成功
echo.

echo 正在启动服务器...
echo ═══════════════════════════════════════════════
echo.

REM 使用 Gradle 运行服务器
call gradlew.bat :server:run --console=plain

echo.
echo 服务器已停止
pause
