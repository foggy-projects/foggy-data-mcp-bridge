@echo off
REM Odoo Module Upgrade Script for Windows
REM Restarts Odoo container and upgrades a specified module
REM
REM Usage:
REM   upgrade_module.bat [module_name] [database_name] [container_name]
REM
REM Examples:
REM   upgrade_module.bat                    - Upgrade foggy_mcp in odoo database
REM   upgrade_module.bat foggy_mcp          - Upgrade foggy_mcp in odoo database
REM   upgrade_module.bat sale odoo odoo-17  - Upgrade sale module with custom container

setlocal enabledelayedexpansion

REM Default values
if "%~1"=="" (set "MODULE_NAME=foggy_mcp") else (set "MODULE_NAME=%~1")
if "%~2"=="" (set "DATABASE_NAME=odoo") else (set "DATABASE_NAME=%~2")
if "%~3"=="" (set "CONTAINER_NAME=foggy-odoo") else (set "CONTAINER_NAME=%~3")
set "WAIT_SECONDS=5"

echo ==========================================
echo Odoo Module Upgrade Script
echo ==========================================
echo Container:  %CONTAINER_NAME%
echo Database:   %DATABASE_NAME%
echo Module:     %MODULE_NAME%
echo ==========================================

REM Check if container exists
docker ps -a --format "{{.Names}}" 2>nul | findstr /x "%CONTAINER_NAME%" >nul
if errorlevel 1 (
    echo Error: Container '%CONTAINER_NAME%' not found
    echo Available containers:
    docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Image}}"
    exit /b 1
)

REM Restart container
echo.
echo [1/3] Restarting container '%CONTAINER_NAME%'...
docker restart %CONTAINER_NAME%
if errorlevel 1 (
    echo Error: Failed to restart container
    exit /b 1
)

echo.
echo [2/3] Waiting %WAIT_SECONDS% seconds for container to be ready...
timeout /t %WAIT_SECONDS% /nobreak >nul

REM Upgrade module
echo.
echo [3/3] Upgrading module '%MODULE_NAME%' in database '%DATABASE_NAME%'...
docker exec %CONTAINER_NAME% bash -c "odoo -d %DATABASE_NAME% -u %MODULE_NAME% --stop-after-init"
if errorlevel 1 (
    echo Error: Module upgrade failed
    exit /b 1
)

echo.
echo ==========================================
echo Module upgrade completed successfully!
echo ==========================================

endlocal