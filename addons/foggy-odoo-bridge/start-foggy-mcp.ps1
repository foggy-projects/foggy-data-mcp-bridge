# ═══════════════════════════════════════════════════════════════
# Foggy MCP Server - Odoo Integration Launcher (PowerShell)
#
# Starts the Java MCP server in lite mode, connected to the Odoo
# PostgreSQL database, with Odoo TM/QM models loaded.
#
# Usage:
#   .\start-foggy-mcp.ps1              # default port 8080
#   .\start-foggy-mcp.ps1 -Port 9090   # custom port
#   .\start-foggy-mcp.ps1 -Stop        # stop only
#
# Prerequisites:
#   - Java 17+
#   - PostgreSQL (Odoo DB) accessible at localhost:5432
#   - foggy-mcp-launcher JAR built (mvn package -DskipTests)
# ═══════════════════════════════════════════════════════════════

[CmdletBinding()]
param(
    [int]$Port = 8080,
    [switch]$Stop
)

$ErrorActionPreference = "Stop"
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# ─── Configuration ──────────────────────────────────────────
$ScriptDir   = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = (Resolve-Path "$ScriptDir\..\..").Path
$ConfigFile  = Join-Path $ScriptDir "application-odoo.yml"
$ModelsDir   = Join-Path $ScriptDir "foggy-models"
$LogFile     = Join-Path $ScriptDir "foggy-mcp.log"
$PidFile     = Join-Path $ScriptDir ".foggy-mcp.pid"

# ─── Color helpers ──────────────────────────────────────────
function Write-Info  { param([string]$Msg) Write-Host "[INFO]  $Msg" -ForegroundColor Cyan }
function Write-Ok    { param([string]$Msg) Write-Host "[OK]    $Msg" -ForegroundColor Green }
function Write-Warn  { param([string]$Msg) Write-Host "[WARN]  $Msg" -ForegroundColor Yellow }
function Write-Err   { param([string]$Msg) Write-Host "[ERROR] $Msg" -ForegroundColor Red }

# ─── Kill process on port ──────────────────────────────────
function Stop-ProcessOnPort {
    param([int]$TargetPort)

    $found = $false
    $connections = netstat -ano 2>$null | Select-String ":$TargetPort\s" | Select-String "LISTENING"
    foreach ($line in $connections) {
        if ($line -match '\s(\d+)\s*$') {
            $pid = [int]$Matches[1]
            if ($pid -gt 0) {
                $found = $true
                Write-Info "Killing PID $pid on port $TargetPort..."
                try {
                    Stop-Process -Id $pid -Force -ErrorAction Stop
                    Write-Ok "Killed $pid"
                } catch {
                    Write-Warn "Failed to kill $pid : $_"
                }
            }
        }
    }
    if ($found) { Start-Sleep -Seconds 2 }
    return $found
}

# ─── Stop mode ──────────────────────────────────────────────
if ($Stop) {
    Write-Info "Stopping Foggy MCP Server..."
    if (Test-Path $PidFile) {
        $oldPid = [int](Get-Content $PidFile -Raw).Trim()
        try {
            $proc = Get-Process -Id $oldPid -ErrorAction Stop
            Stop-Process -Id $oldPid -Force
            Write-Ok "Process $oldPid stopped."
        } catch {
            Write-Warn "PID $oldPid not running."
        }
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    } else {
        Write-Warn "No PID file found. Trying port-based kill..."
        if (-not (Stop-ProcessOnPort -TargetPort $Port)) {
            Write-Warn "No process found on port $Port."
        }
    }
    exit 0
}

# ─── Find JAR ──────────────────────────────────────────────
function Find-Jar {
    $jarDir = Join-Path $ProjectRoot "foggy-mcp-launcher\target"
    $jars = Get-ChildItem -Path $jarDir -Filter "foggy-mcp-launcher-*.jar" -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch "sources" } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1

    if (-not $jars) {
        Write-Err "JAR not found in $jarDir"
        Write-Err "Run: cd $ProjectRoot; mvn package -pl foggy-mcp-launcher -am -DskipTests"
        exit 1
    }
    return $jars.FullName
}

# ─── Pre-flight checks ─────────────────────────────────────
function Test-Preflight {
    # Java version check
    # Note: java -version writes to stderr; must capture with 2>&1
    # and temporarily relax ErrorAction to avoid false "not found".
    try {
        $prevEA = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $javaVerOutput = (& java -version 2>&1) | Out-String
        $ErrorActionPreference = $prevEA

        if ($javaVerOutput -match '"(\d+)') {
            $javaVer = [int]$Matches[1]
        } else {
            $javaVer = 0
        }
    } catch {
        $ErrorActionPreference = $prevEA
        Write-Err "Java not found. Install JDK 17+."
        exit 1
    }

    if ($javaVer -lt 17) {
        Write-Err "Java 17+ required, found: $javaVer"
        exit 1
    }
    Write-Ok "Java $javaVer"

    # Config file
    if (-not (Test-Path $ConfigFile)) {
        Write-Err "Config not found: $ConfigFile"
        exit 1
    }
    Write-Ok "Config: $ConfigFile"

    # Models directory
    if (-not (Test-Path $ModelsDir)) {
        Write-Err "Models not found: $ModelsDir"
        exit 1
    }
    $tmCount = (Get-ChildItem "$ModelsDir\model\*.tm" -ErrorAction SilentlyContinue).Count
    $qmCount = (Get-ChildItem "$ModelsDir\query\*.qm" -ErrorAction SilentlyContinue).Count
    Write-Ok "Models: $tmCount TM + $qmCount QM"

    # PostgreSQL connectivity (optional)
    if (Get-Command pg_isready -ErrorAction SilentlyContinue) {
        $prevEA = $ErrorActionPreference; $ErrorActionPreference = "Continue"
        & pg_isready -h localhost -p 5432 -U odoo -q 2>$null | Out-Null
        $ErrorActionPreference = $prevEA
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "PostgreSQL: localhost:5432"
        } else {
            Write-Warn "PostgreSQL not reachable at localhost:5432 (may be in Docker)"
        }
    }
}

# ─── Main ───────────────────────────────────────────────────
function Start-Main {
    Write-Host ""
    Write-Host "+================================================+" -ForegroundColor Cyan
    Write-Host "|  Foggy MCP Server - Odoo Integration            |" -ForegroundColor Cyan
    Write-Host "+================================================+" -ForegroundColor Cyan
    Write-Host ""

    Test-Preflight

    $jar = Find-Jar
    Write-Ok "JAR: $(Split-Path $jar -Leaf)"

    # Kill existing process on the port
    Write-Info "Checking port $Port..."
    if (Stop-ProcessOnPort -TargetPort $Port) {
        Write-Ok "Port $Port cleared."
    } else {
        Write-Ok "Port $Port is free."
    }

    Write-Info "Starting on port $Port..."
    Write-Info "Log: $LogFile"
    Write-Host ""

    # Launch in background
    # Note: Spring Boot list properties must be fully specified on command line
    # (partial override of bundles[0] loses other fields from YAML)
    # Quote args that contain brackets or spaces so they survive Start-Process
    $javaArgs = @(
        "-Dfile.encoding=UTF-8",
        "-Dconsole.encoding=UTF-8",
        "-jar", "`"$jar`"",
        "`"--spring.config.additional-location=file:$ConfigFile`"",
        "--server.port=$Port",
        "`"--foggy.bundle.external.bundles[0].name=odoo-models`"",
        "`"--foggy.bundle.external.bundles[0].path=$ModelsDir`"",
        "`"--foggy.bundle.external.bundles[0].namespace=odoo`""
    )

    $ErrLog = Join-Path $ScriptDir "foggy-mcp-err.log"
    $process = Start-Process -FilePath "java" -ArgumentList $javaArgs `
        -RedirectStandardOutput $LogFile -RedirectStandardError $ErrLog `
        -WindowStyle Hidden -PassThru

    $process.Id | Out-File -FilePath $PidFile -Encoding ascii -NoNewline
    Write-Ok "Started with PID $($process.Id)"

    # Wait for health check
    Write-Info "Waiting for health check..."
    $maxWait = 40
    for ($i = 1; $i -le $maxWait; $i++) {
        Start-Sleep -Seconds 1

        # Check if process is still alive
        if ($process.HasExited) {
            Write-Err "Process exited unexpectedly (exit code: $($process.ExitCode)). Last 30 lines:"
            Write-Host ""
            if (Test-Path $LogFile) {
                Get-Content $LogFile -Tail 30
            }
            $ErrLogCheck = Join-Path $ScriptDir "foggy-mcp-err.log"
            if ((Test-Path $ErrLogCheck) -and (Get-Item $ErrLogCheck).Length -gt 0) {
                Write-Host "--- stderr ---" -ForegroundColor Yellow
                Get-Content $ErrLogCheck -Tail 30
            }
            exit 1
        }

        # Health check
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:$Port/actuator/health" `
                -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Write-Host ""
                Write-Ok "Foggy MCP Server is UP!"
                Write-Host ""

                # Verify tools loaded
                $toolCount = "?"
                try {
                    $body = '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
                    $toolsResp = Invoke-RestMethod -Uri "http://localhost:$Port/mcp/admin/rpc" `
                        -Method POST -ContentType "application/json" -Body $body `
                        -TimeoutSec 5 -ErrorAction Stop
                    if ($toolsResp.result -and $toolsResp.result.tools) {
                        $toolCount = $toolsResp.result.tools.Count
                    }
                } catch {
                    # tools/list failed, not critical
                }

                Write-Host "  URL:       http://localhost:$Port" -ForegroundColor Cyan
                Write-Host "  Health:    http://localhost:$Port/actuator/health" -ForegroundColor Cyan
                Write-Host "  Admin RPC: http://localhost:$Port/mcp/admin/rpc" -ForegroundColor Cyan
                Write-Host "  Tools:     $toolCount loaded" -ForegroundColor Cyan
                Write-Host "  PID:       $($process.Id)" -ForegroundColor Cyan
                Write-Host "  Log:       $LogFile" -ForegroundColor Cyan
                Write-Host ""
                Write-Host "  Stop: " -NoNewline
                Write-Host ".\start-foggy-mcp.ps1 -Stop" -ForegroundColor Yellow
                Write-Host ""
                return
            }
        } catch {
            # Not ready yet
        }

        Write-Host "`r  waiting... $i/${maxWait}s" -NoNewline
    }

    Write-Host ""
    Write-Warn "Health check timeout after ${maxWait}s. Server may still be starting."
    Write-Warn "Check log: Get-Content $LogFile -Tail 50 -Wait"
}

Start-Main
