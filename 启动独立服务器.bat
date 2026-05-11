@echo off
chcp 65001 >nul
echo ═══════════════════════════════════════════════
echo   飞机大战独立服务器启动脚本
echo ═══════════════════════════════════════════════
echo.
echo 正在启动服务器...
echo.

cd /d "%~dp0"
call gradlew.bat :server:run

pause
