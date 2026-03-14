# ═══════════════════════════════════════════════════════════════
# Foggy MCP Server - Stop Script (PowerShell)
#
# Usage:
#   .\stop-foggy-mcp.ps1          # stop by PID file or port 8080
#   .\stop-foggy-mcp.ps1 -Port 9090   # stop by specific port
# ═══════════════════════════════════════════════════════════════

[CmdletBinding()]
param(
    [int]$Port = 8080
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PidFile   = Join-Path $ScriptDir ".foggy-mcp.pid"

function Write-Info { param([string]$Msg) Write-Host "[INFO]  $Msg" -ForegroundColor Cyan }
function Write-Ok   { param([string]$Msg) Write-Host "[OK]    $Msg" -ForegroundColor Green }
function Write-Warn { param([string]$Msg) Write-Host "[WARN]  $Msg" -ForegroundColor Yellow }

Write-Info "Stopping Foggy MCP Server..."

$stopped = $false

# 1) Try PID file
if (Test-Path $PidFile) {
    $oldPid = (Get-Content $PidFile -Raw).Trim()
    if ($oldPid -match '^\d+$') {
        $oldPid = [int]$oldPid
        try {
            Get-Process -Id $oldPid -ErrorAction Stop | Out-Null
            Stop-Process -Id $oldPid -Force
            Write-Ok "Process $oldPid stopped (from PID file)."
            $stopped = $true
        } catch {
            Write-Warn "PID $oldPid not running."
        }
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

# 2) Kill by port (catches processes not tracked by PID file)
$connections = netstat -ano 2>$null | Select-String ":$Port\s" | Select-String "LISTENING"
foreach ($line in $connections) {
    if ($line -match '\s(\d+)\s*$') {
        $pid = [int]$Matches[1]
        if ($pid -gt 0) {
            try {
                Stop-Process -Id $pid -Force -ErrorAction Stop
                Write-Ok "Killed PID $pid on port $Port."
                $stopped = $true
            } catch {
                Write-Warn "Failed to kill PID $pid : $_"
            }
        }
    }
}

# 3) Also kill any stray foggy-mcp java processes
$javaProcs = Get-Process -Name java -ErrorAction SilentlyContinue |
    Where-Object {
        try {
            $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)" -ErrorAction Stop).CommandLine
            $cmdLine -match 'foggy-mcp-launcher'
        } catch { $false }
    }

foreach ($proc in $javaProcs) {
    try {
        Stop-Process -Id $proc.Id -Force
        Write-Ok "Killed foggy-mcp java process PID $($proc.Id)."
        $stopped = $true
    } catch {
        Write-Warn "Failed to kill PID $($proc.Id) : $_"
    }
}

if ($stopped) {
    Write-Ok "Foggy MCP Server stopped."
} else {
    Write-Warn "No Foggy MCP process found."
}
