@echo off
chcp 65001 >nul
echo ========================================
echo   医院小程序 - 本地环境一键启动
echo ========================================
echo.

REM ---------- 1. 启动 MySQL ----------
echo [1/2] 检查并启动 MySQL (D:\mysql)...
tasklist /fi "imagename eq mysqld.exe" 2>nul | findstr /i "mysqld.exe" >nul
if %errorlevel%==0 (
    echo       MySQL 已在运行
) else (
    echo       正在后台启动 mysqld...
    start "MySQL" /min "D:\mysql\bin\mysqld.exe" --defaults-file=D:\mysql\my.ini --console
    echo       等待 MySQL 就绪...
    timeout /t 5 /nobreak >nul
)
echo.

REM ---------- 2. 加载 DeepSeek 本地密钥（可选）----------
if exist "%~dp0.deepseek-env" (
    echo       检测到 .deepseek-env，加载 DeepSeek 配置...
    for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%~dp0.deepseek-env") do set "%%A=%%B"
) else (
    echo       未找到 .deepseek-env，AI 初筛使用 mock 演示应答
)
echo.

REM ---------- 3. 启动后端 ----------
echo [2/2] 启动 Spring Boot 后端 (local profile)...
cd /d "%~dp0backend"
call mvn spring-boot:run "-Dspring-boot.run.profiles=local" "-Dfile.encoding=UTF-8"

pause
