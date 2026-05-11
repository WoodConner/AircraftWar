@echo off
chcp 65001 >nul
echo ═══════════════════════════════════════════════
echo   飞机大战联机对战 - 一键诊断工具
echo ═══════════════════════════════════════════════
echo.

cd /d "%~dp0"

echo [1/5] 检查Java环境...
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ✗ Java未安装或未配置
    echo   请安装JDK 11或更高版本
    goto :end
) else (
    echo ✓ Java环境正常
)
echo.

echo [2/5] 检查端口8080...
netstat -ano | findstr ":8080" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo ⚠ 端口8080已被占用
    echo   正在查找占用进程...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080"') do (
        echo   进程ID: %%a
        tasklist | findstr %%a
    )
    echo.
    echo   解决方案：
    echo   1. 如果是Java进程，可能是服务器已在运行（这是好的）
    echo   2. 如果是其他进程，请关闭它或修改服务器端口
    echo.
) else (
    echo ✓ 端口8080可用
)
echo.

echo [3/5] 测试服务器连接...
echo   正在尝试连接 http://localhost:8080 ...
curl -s -o nul -w "%%{http_code}" http://localhost:8080/battle/status?roomId=test^&playerId=test > temp_status.txt 2>nul
set /p STATUS_CODE=<temp_status.txt
del temp_status.txt 2>nul

if "%STATUS_CODE%"=="404" (
    echo ✓ 服务器正在运行！（返回404是正常的）
    echo   服务器可以接收请求
    goto :server_ok
)
if "%STATUS_CODE%"=="200" (
    echo ✓ 服务器正在运行！
    goto :server_ok
)

echo ✗ 无法连接到服务器
echo   HTTP状态码: %STATUS_CODE%
echo.
echo   可能的原因：
echo   1. 服务器未启动
echo   2. 防火墙阻止连接
echo   3. 端口被其他程序占用
echo.
echo   解决方案：
echo   1. 运行 "启动服务器.bat"
echo   2. 临时关闭防火墙测试
echo.
goto :end

:server_ok
echo.

echo [4/5] 测试创建房间API...
echo   正在测试 POST /battle/create ...
curl -X POST http://localhost:8080/battle/create -H "Content-Type: application/json" -d "{\"playerName\":\"TestPlayer\"}" -s > test_response.txt 2>&1

if exist test_response.txt (
    echo   服务器响应：
    type test_response.txt
    echo.
    
    findstr /C:"success" test_response.txt >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo ✓ 创建房间API正常工作！
    ) else (
        echo ⚠ 服务器返回了响应，但可能有错误
    )
    del test_response.txt
) else (
    echo ✗ 无法获取服务器响应
)
echo.

echo [5/5] 检查Android设备连接...
adb devices > devices.txt 2>&1
findstr /C:"device" devices.txt | findstr /V "List" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo ✓ 检测到Android设备/模拟器
    echo.
    echo   已连接的设备：
    adb devices | findstr /V "List"
) else (
    echo ⚠ 未检测到Android设备/模拟器
    echo   请启动模拟器或连接真机
)
del devices.txt 2>nul
echo.

echo ═══════════════════════════════════════════════
echo   诊断完成
echo ═══════════════════════════════════════════════
echo.
echo 下一步操作：
echo.
echo 1. 如果服务器未运行：
echo    双击 "启动服务器.bat"
echo.
echo 2. 如果服务器正常但游戏无法连接：
echo    - 模拟器使用地址：10.0.2.2
echo    - 真机使用电脑局域网IP
echo    - 重新安装游戏：gradlew.bat installDebug
echo.
echo 3. 查看详细日志：
echo    adb logcat -s BattleClient:D
echo.

:end
pause
