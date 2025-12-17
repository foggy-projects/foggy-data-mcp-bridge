@echo off
chcp 65001 >nul
REM ============================================
REM Foggy Dataset Demo - 数据库初始化脚本 (Windows)
REM ============================================
REM
REM 用法:
REM   init-db.cmd [mysql|postgres|sqlserver|all]
REM
REM 示例:
REM   init-db.cmd mysql      # 仅初始化 MySQL
REM   init-db.cmd all        # 初始化所有数据库
REM   init-db.cmd            # 默认初始化 MySQL
REM
REM ============================================

setlocal

cd /d "%~dp0"

set "TARGET=%~1"
if "%TARGET%"=="" set "TARGET=mysql"

if "%TARGET%"=="mysql" goto :init_mysql
if "%TARGET%"=="postgres" goto :init_postgres
if "%TARGET%"=="sqlserver" goto :init_sqlserver
if "%TARGET%"=="all" goto :init_all
if "%TARGET%"=="-h" goto :show_help
if "%TARGET%"=="--help" goto :show_help
if "%TARGET%"=="help" goto :show_help

echo [ERROR] Unknown option: %TARGET%
goto :show_help

REM ==========================================
REM MySQL 初始化
REM ==========================================
:init_mysql
echo [INFO] Initializing MySQL database...

REM 检查容器是否运行
docker ps | findstr "foggy-demo-mysql" >nul
if errorlevel 1 goto :start_mysql
goto :exec_mysql

:start_mysql
echo [WARN] MySQL container not running. Starting...
docker-compose up -d mysql
echo [INFO] Waiting for MySQL to be ready...
timeout /t 30 /nobreak >nul

:exec_mysql
echo [INFO] Executing MySQL init scripts...

docker exec -i foggy-demo-mysql mysql -ufoggy -pfoggy_test_123 foggy_test < mysql\init\01-schema.sql
if errorlevel 1 goto :error_mysql_schema
echo   - 01-schema.sql executed

docker exec -i foggy-demo-mysql mysql -ufoggy -pfoggy_test_123 foggy_test < mysql\init\02-dict-data.sql
if errorlevel 1 goto :error_mysql_dict
echo   - 02-dict-data.sql executed

docker exec -i foggy-demo-mysql mysql -ufoggy -pfoggy_test_123 foggy_test < mysql\init\03-test-data.sql
if errorlevel 1 goto :error_mysql_data
echo   - 03-test-data.sql executed

echo [INFO] MySQL initialization completed!
goto :eof

:error_mysql_schema
echo [ERROR] Failed to execute 01-schema.sql
exit /b 1

:error_mysql_dict
echo [ERROR] Failed to execute 02-dict-data.sql
exit /b 1

:error_mysql_data
echo [ERROR] Failed to execute 03-test-data.sql
exit /b 1

REM ==========================================
REM PostgreSQL 初始化
REM ==========================================
:init_postgres
echo [INFO] Initializing PostgreSQL database...

REM 检查容器是否运行
docker ps | findstr "foggy-demo-postgres" >nul
if errorlevel 1 goto :start_postgres
goto :exec_postgres

:start_postgres
echo [WARN] PostgreSQL container not running. Starting...
docker-compose up -d postgres
echo [INFO] Waiting for PostgreSQL to be ready...
timeout /t 15 /nobreak >nul

:exec_postgres
echo [INFO] Executing PostgreSQL init scripts...

docker exec -i foggy-demo-postgres psql -U foggy -d foggy_test < postgres\init\01-schema.sql
if errorlevel 1 goto :error_pg_schema
echo   - 01-schema.sql executed

docker exec -i foggy-demo-postgres psql -U foggy -d foggy_test < postgres\init\02-dict-data.sql
if errorlevel 1 goto :error_pg_dict
echo   - 02-dict-data.sql executed

docker exec -i foggy-demo-postgres psql -U foggy -d foggy_test < postgres\init\03-test-data.sql
if errorlevel 1 goto :error_pg_data
echo   - 03-test-data.sql executed

echo [INFO] PostgreSQL initialization completed!
goto :eof

:error_pg_schema
echo [ERROR] Failed to execute 01-schema.sql
exit /b 1

:error_pg_dict
echo [ERROR] Failed to execute 02-dict-data.sql
exit /b 1

:error_pg_data
echo [ERROR] Failed to execute 03-test-data.sql
exit /b 1

REM ==========================================
REM SQL Server 初始化
REM ==========================================
:init_sqlserver
echo [INFO] Initializing SQL Server database...

REM 检查容器是否运行
docker ps | findstr "foggy-demo-sqlserver" >nul
if errorlevel 1 goto :start_sqlserver
goto :exec_sqlserver

:start_sqlserver
echo [WARN] SQL Server container not running. Starting...
docker-compose up -d sqlserver
echo [INFO] Waiting for SQL Server to be ready...
timeout /t 60 /nobreak >nul

:exec_sqlserver
echo [INFO] Creating database foggy_test...
docker exec foggy-demo-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "Foggy_Test_123!" -C -Q "IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'foggy_test') CREATE DATABASE foggy_test"

echo [INFO] Executing SQL Server init scripts...

docker exec foggy-demo-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "Foggy_Test_123!" -C -d foggy_test -i /scripts/01-schema.sql
if errorlevel 1 goto :error_ss_schema
echo   - 01-schema.sql executed

docker exec foggy-demo-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "Foggy_Test_123!" -C -d foggy_test -i /scripts/02-dict-data.sql
if errorlevel 1 goto :error_ss_dict
echo   - 02-dict-data.sql executed

docker exec foggy-demo-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "Foggy_Test_123!" -C -d foggy_test -i /scripts/03-test-data.sql
if errorlevel 1 goto :error_ss_data
echo   - 03-test-data.sql executed

echo [INFO] SQL Server initialization completed!
goto :eof

:error_ss_schema
echo [ERROR] Failed to execute 01-schema.sql
exit /b 1

:error_ss_dict
echo [ERROR] Failed to execute 02-dict-data.sql
exit /b 1

:error_ss_data
echo [ERROR] Failed to execute 03-test-data.sql
exit /b 1

REM ==========================================
REM 初始化所有数据库
REM ==========================================
:init_all
call :init_mysql
call :init_postgres
call :init_sqlserver
goto :eof

REM ==========================================
REM 显示帮助
REM ==========================================
:show_help
echo Usage: %~nx0 [mysql^|postgres^|sqlserver^|all]
echo.
echo Options:
echo   mysql      Initialize MySQL database
echo   postgres   Initialize PostgreSQL database
echo   sqlserver  Initialize SQL Server database
echo   all        Initialize all databases
echo.
echo If no option is provided, defaults to 'mysql'
goto :eof
