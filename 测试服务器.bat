@echo off
chcp 65001 >nul
echo ═══════════════════════════════════════════════
echo   测试服务器连接
echo ═══════════════════════════════════════════════
echo.

echo 正在测试服务器是否运行...
echo.

REM 测试服务器是否在运行
curl -X POST http://localhost:8080/battle/create -H "Content-Type: application/json" -d "{\"playerName\":\"TestPlayer\"}" 2>nul

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✓ 服务器连接成功！
    echo.
) else (
    echo.
    echo ✗ 无法连接到服务器
    echo.
    echo 请确保：
    echo 1. 服务器已启动（运行 启动服务器.bat）
    echo 2. 端口8080未被占用
    echo 3. 防火墙未阻止连接
    echo.
)

pause
