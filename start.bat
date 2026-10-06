@echo off
rem ======================================================================
rem  NBAManager 一键启动脚本 (Windows)
rem
rem  用法:  start.bat [命令]
rem     start.bat            启动全部服务（后端 + 前端，Python 由后端托管）
rem     start.bat backend    只启动后端
rem     start.bat frontend   只启动前端
rem     start.bat stop       停止全部服务
rem     start.bat restart    重启
rem     start.bat status     查看运行状态
rem     start.bat build      重新构建（后端 jar + 前端 dist）
rem     start.bat logs       查看各服务日志尾部
rem
rem  服务构成:
rem     * MySQL(3306) / Redis(6379)  Windows 服务，随系统自启，脚本只检查
rem     * 后端 API (8080)            java -jar，Windows 原生
rem     * 前端 Vite (5173)           npm run dev，Windows 原生
rem     * RAG(18999) / Copilot(18998) 后端启动时用 venv312 自动拉起
rem
rem  可选环境变量:
rem     NBA_PROFILE        Spring profile（默认 local，含 JWT_SECRET 等）
rem     NBA_BACKEND_PORT   后端端口（默认 8080）
rem     NBA_FRONTEND_PORT  前端端口（默认 5173）
rem ======================================================================

setlocal enabledelayedexpansion
pushd "%~dp0"
set "ROOT=%CD%"
popd

if not defined NBA_PROFILE       set "NBA_PROFILE=local"
if not defined NBA_BACKEND_PORT  set "NBA_BACKEND_PORT=8080"
if not defined NBA_FRONTEND_PORT set "NBA_FRONTEND_PORT=5173"

set "JAR="
set "LOGDIR=%ROOT%\logs"
set "BACKEND_LOG=%LOGDIR%\backend.log"
set "FRONTEND_LOG=%LOGDIR%\frontend.log"
set "PYTHON=%ROOT%\backend\scripts\venv312\Scripts\python.exe"
if not exist "%LOGDIR%" mkdir "%LOGDIR%" >nul 2>&1

set "ACTION=%~1"
if "%ACTION%"=="" set "ACTION=start"
if /i "%ACTION%"=="all" set "ACTION=start"

if /i "%ACTION%"=="status"   goto :do_status
if /i "%ACTION%"=="stop"     goto :do_stop
if /i "%ACTION%"=="restart"  goto :do_restart
if /i "%ACTION%"=="build"    goto :do_build
if /i "%ACTION%"=="logs"     goto :do_logs
if /i "%ACTION%"=="backend"  goto :do_backend_only
if /i "%ACTION%"=="frontend" goto :do_frontend_only
if /i "%ACTION%"=="start"    goto :do_start

echo [ERROR] 未知参数: %ACTION%
echo         可用: start ^| backend ^| frontend ^| stop ^| restart ^| status ^| build ^| logs
goto :end_fail


rem ==================== 启动全部 ====================
:do_start
call :preflight
if errorlevel 1 goto :end_fail
call :ensure_backend
if errorlevel 1 goto :end_fail
call :ensure_frontend
if errorlevel 1 goto :end_fail
call :print_summary
goto :end_ok

:do_backend_only
call :preflight
if errorlevel 1 goto :end_fail
call :ensure_backend
if errorlevel 1 goto :end_fail
call :print_summary
goto :end_ok

:do_frontend_only
call :ensure_frontend
if errorlevel 1 goto :end_fail
call :print_summary
goto :end_ok


rem ==================== 环境检查 ====================
:preflight
echo.
echo [1/3] 环境检查
set "JAVA_BIN="
for /f "delims=" %%J in ('where java 2^>nul') do if not defined JAVA_BIN set "JAVA_BIN=%%J"
if not defined JAVA_BIN (
    echo    [ERROR] 未找到 java，请安装 JDK 17+ 并加入 PATH
    exit /b 1
)
echo    [OK] Java: %JAVA_BIN%

call :check_port 3306 "MySQL"
if errorlevel 1 exit /b 1
call :check_port 6379 "Redis"
if errorlevel 1 exit /b 1
exit /b 0


rem ==================== 后端 ====================
:ensure_backend
echo.
echo [2/3] 后端
call :port_pid %NBA_BACKEND_PORT% BPID
if defined BPID (
    call :http_ok "http://127.0.0.1:%NBA_BACKEND_PORT%/api/teams" BOK
    if "!BOK!"=="1" (
        echo    [SKIP] 已在运行且健康 ^(PID %BPID%^)
        exit /b 0
    )
    echo    [WARN] 端口被 PID %BPID% 占用但无响应，先停止旧进程
    call :stop_port %NBA_BACKEND_PORT% "旧后端进程"
    call :sleep 2
)
call :resolve_backend_jar
if errorlevel 1 exit /b 1
if not exist "%JAR%" (
    echo    [WARN] 未找到 jar，先构建...
    call :build_backend
    if errorlevel 1 exit /b 1
)
echo    启动中 ^(profile=%NBA_PROFILE%^)
echo    日志: %BACKEND_LOG%
echo    [CACHE] JAR: %JAR%
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\start-service.ps1" -Service Backend -Root "%ROOT%" -Port %NBA_BACKEND_PORT% -SpringProfile "%NBA_PROFILE%" -Jar "%JAR%" -TimeoutSeconds 150
if errorlevel 1 (
    echo    [ERROR] 后端启动失败，请查看上述错误和日志
    exit /b 1
)
exit /b 0


rem ==================== 前端 ====================
:ensure_frontend
echo.
echo [3/3] 前端
call :port_pid %NBA_FRONTEND_PORT% FPID
if defined FPID (
    call :http_ok "http://127.0.0.1:%NBA_FRONTEND_PORT%/" FOK
    if "!FOK!"=="1" (
        echo    [SKIP] 已在运行且健康 ^(PID %FPID%^)
        exit /b 0
    )
    echo    [WARN] 端口被占用但无响应，先停止旧进程
    call :stop_port %NBA_FRONTEND_PORT% "旧前端进程"
    call :sleep 2
)
where node.exe >nul 2>&1
if errorlevel 1 (echo    [ERROR] 未找到 Node.js，请安装并加入 PATH & exit /b 1)
where npm.cmd >nul 2>&1
if errorlevel 1 (echo    [ERROR] 未找到 npm.cmd，请检查 Node.js 安装 & exit /b 1)
if not exist "%ROOT%\frontend\node_modules\.bin\vite.cmd" (
    echo    [INFO] 未找到前端依赖，正在 npm install...
    pushd "%ROOT%\frontend"
    call npm.cmd install
    if errorlevel 1 (popd & echo    [ERROR] npm install 失败 & exit /b 1)
    popd
)
echo    启动中
echo    日志: %FRONTEND_LOG%
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\start-service.ps1" -Service Frontend -Root "%ROOT%" -Port %NBA_FRONTEND_PORT% -TimeoutSeconds 60
if errorlevel 1 (
    echo    [ERROR] 前端启动失败，请查看上述错误和日志
    exit /b 1
)
exit /b 0


rem ==================== 状态 ====================
:do_status
echo.
echo ============ NBAManager 运行状态 ============
call :report 3306 "MySQL"
call :report 6379 "Redis"
call :report %NBA_BACKEND_PORT% "后端 API" "http://127.0.0.1:%NBA_BACKEND_PORT%/api/teams"
call :report %NBA_FRONTEND_PORT% "前端" "http://127.0.0.1:%NBA_FRONTEND_PORT%/"
call :report 18999 "RAG" "http://127.0.0.1:18999/health"
call :report 18998 "Copilot" "http://127.0.0.1:18998/health"
echo ----------------------------------------------
echo   前端: http://localhost:%NBA_FRONTEND_PORT%/
echo   后端: http://localhost:%NBA_BACKEND_PORT%/
echo   日志: %LOGDIR%
echo ==============================================
goto :end_ok


rem ==================== 停止 ====================
:do_stop
echo.
echo ============ 停止 NBAManager ============
call :stop_port %NBA_FRONTEND_PORT% "前端"
call :stop_port %NBA_BACKEND_PORT% "后端"
call :stop_port 18999 "RAG"
call :stop_port 18998 "Copilot"
rem 兜底：后端退出后它拉起的 python 可能成为孤儿并继续占用端口，按命令行再清一遍
call :kill_python_by_cmd "rag_app" "RAG残留"
call :kill_python_by_cmd "nba_copilot_app" "Copilot残留"
echo   [OK] 已停止 ^(MySQL/Redis 为系统服务，未改动^)
goto :end_ok

:do_restart
call :do_stop
echo.
echo 等待端口释放...
call :sleep 3
goto :do_start


rem ==================== 构建 ====================
:do_build
echo.
echo ============ 构建 ============
call :build_backend
if errorlevel 1 goto :end_fail
echo   构建前端...
pushd "%ROOT%\frontend"
if not exist "node_modules" (
    call npm.cmd install
    if errorlevel 1 (popd & echo    [ERROR] npm install 失败 & goto :end_fail)
)
call npm.cmd run build
if errorlevel 1 (popd & echo    [ERROR] 前端构建失败 & goto :end_fail)
popd
echo   [OK] 前端构建完成: %ROOT%\frontend\dist
goto :end_ok

:build_backend
echo   构建后端 ^(mvnw.cmd -DskipTests package^)...
pushd "%ROOT%\backend"
call mvnw.cmd -q -DskipTests package
if errorlevel 1 (popd & echo    [ERROR] 后端构建失败 & exit /b 1)
popd
call :resolve_backend_jar
if errorlevel 1 exit /b 1
if not exist "%JAR%" (
    echo    [ERROR] 构建结束但未生成预期 JAR: %JAR%
    exit /b 1
)
echo   [OK] 后端构建完成: %JAR%
exit /b 0

:resolve_backend_jar
set "JAR="
for /f "usebackq delims=" %%J in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\resolve-backend-jar.ps1" -Root "%ROOT%"`) do set "JAR=%%J"
if not defined JAR (
    echo    [ERROR] 无法从 backend\pom.xml 确定 JAR 文件名
    exit /b 1
)
exit /b 0


rem ==================== 日志 ====================
:do_logs
echo.
echo 日志目录: %LOGDIR%
echo.
echo ---- backend.log ^(末 20 行^) ----
call :tail_log "%BACKEND_LOG%" 20
echo.
echo ---- frontend.log ^(末 20 行^) ----
call :tail_log "%FRONTEND_LOG%" 20
goto :end_ok


rem ==================== 工具函数 ====================
:port_pid  <port> <outVar>
set "%~2="
set "_PP="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":%~1 " ^| findstr "LISTENING" 2^>nul') do if not defined _PP set "_PP=%%P"
set "%~2=%_PP%"
exit /b 0

:check_port  <port> <label>
call :port_pid %1 _CP
if defined _CP (
    echo    [OK] %~2 ^(端口 %1, PID %_CP%^)
    exit /b 0
)
echo    [ERROR] %~2 未监听端口 %1
echo            以管理员身份执行: net start %~2
exit /b 1

:http_ok  <url> <outVar>
set "%~2=0"
set "_HC=000"
for /f %%C in ('curl --noproxy "*" -s -o nul -w "%%{http_code}" --max-time 5 "%~1" 2^>nul') do set "_HC=%%C"
if "%_HC%"=="200" set "%~2=1"
exit /b 0

:sleep  <seconds>
set /a _SL=%~1+1
ping -n %_SL% 127.0.0.1 >nul 2>&1
exit /b 0

:tail_log  <file> <lines>
if not exist "%~1" (
    echo      ^(暂无日志^)
    exit /b 0
)
powershell -NoProfile -Command "Get-Content -LiteralPath '%~f1' -Tail %2" 2>nul
exit /b 0

:report  <port> <label> [url]
call :port_pid %1 _RP
if not defined _RP (
    echo    [--]  %~2 未运行
    exit /b 0
)
if "%~3"=="" (
    echo    [OK] %~2 ^(端口 %1, PID %_RP%^)
    exit /b 0
)
set "_RC=000"
for /f %%C in ('curl --noproxy "*" -s -o nul -w "%%{http_code}" --max-time 5 "%~3" 2^>nul') do set "_RC=%%C"
echo    [OK] %~2 ^(端口 %1, PID %_RP%, HTTP %_RC%^)
exit /b 0

rem 按命令行特征清理本项目 python 进程（兜底，处理后端退出后的孤儿进程）
rem 仅当命令行同时含 NBAManager 与特征串才杀，避免误伤其他程序
:kill_python_by_cmd  <feature> <label>
set "_FOUND="
for /f "usebackq tokens=*" %%L in (`powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='python.exe'\" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like '*NBAManager*' -and $_.CommandLine -like '*%~1*' } | ForEach-Object { $_.ProcessId }" 2^>nul`) do set "_FOUND=!_FOUND! %%L"
if not defined _FOUND exit /b 0
for %%P in (!_FOUND!) do taskkill /PID %%P /T /F >nul 2>&1
echo    [OK] 已清理 %~2 ^(PID!_FOUND!^)
exit /b 0

echo    [OK] %~2 ^(端口 %1, PID %_RP%, HTTP %_RC%^)
exit /b 0

:stop_port  <port> <label>
call :port_pid %1 _KP
if not defined _KP (
    echo    [--]  %~2 未运行
    exit /b 0
)
taskkill /PID %_KP% /T /F >nul 2>&1
if errorlevel 1 (
    echo    [WARN] %~2 停止失败 ^(PID %_KP%^)
    exit /b 0
)
echo    [OK] 已停止 %~2 ^(PID %_KP%^)
exit /b 0

:print_summary
echo.
echo ==============================================
echo   NBAManager 已启动
echo ----------------------------------------------
echo   前端:      http://localhost:%NBA_FRONTEND_PORT%/
echo   后端 API:  http://localhost:%NBA_BACKEND_PORT%/
echo   RAG:       http://localhost:18999/  ^(后端自动拉起^)
echo   Copilot:   http://localhost:18998/  ^(后端自动拉起^)
echo   日志:      %LOGDIR%
echo ----------------------------------------------
echo   停止: start.bat stop    状态: start.bat status
echo ==============================================
exit /b 0

:end_ok
endlocal
exit /b 0

:end_fail
echo.
echo [FAILED] 未完成，请按上面提示排查。
endlocal
exit /b 1
