@echo off
chcp 65001 >nul
echo ========================================================
echo   Java Blueprints Studio - Windows UTF-8 Launcher
echo ========================================================
echo.

set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dstdin.encoding=UTF-8"
set "MAVEN_OPTS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dstdin.encoding=UTF-8"

if "%1"=="server" goto run_server
if "%1"=="client" goto run_client

echo [*] Запуск в режиме UTF-8...
echo [1/2] Запуск бэкенда Spring Boot (порт 8080)...
start "Java Blueprints - Backend" cmd /c "chcp 65001 >nul && cd /d "%~dp0..\server" && .\mvnw.cmd spring-boot:run"

echo [2/2] Запуск веб-клиента Vite (порт 3000)...
cd /d "%~dp0..\client"
call npm run dev
goto end

:run_server
cd /d "%~dp0..\server"
call .\mvnw.cmd spring-boot:run
goto end

:run_client
cd /d "%~dp0..\client"
call npm run dev
goto end

:end
