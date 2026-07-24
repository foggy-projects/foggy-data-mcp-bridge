param(
    [switch]$SkipFullRegression,
    [switch]$SkipExternalDb,
    [switch]$SkipMcp
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Invoke-ReleaseStep {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    Write-Host ""
    Write-Host "==> $Name" -ForegroundColor Cyan
    Write-Host "mvn $($Arguments -join ' ')" -ForegroundColor DarkGray
    & mvn @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Step failed: $Name"
    }
}

function Invoke-PivotParityIT {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Profile
    )

    $report = Join-Path $repoRoot "foggy-dataset-model-engine/target/failsafe-reports/TEST-com.foggyframework.dataset.model.engine.pivot.PivotSqlParityIT.xml"
    Remove-Item -Force -ErrorAction SilentlyContinue $report
    Invoke-ReleaseStep $Name @(
        "verify",
        "-pl", "foggy-dataset-model-engine",
        "-am",
        "-Dit.test=PivotSqlParityIT",
        "-Dspring.profiles.active=$Profile",
        "-DskipUnitTests=true",
        "-DskipITs=false",
        "-Dfailsafe.failIfNoSpecifiedTests=false",
        "-P!multi-db"
    )
    if (-not (Test-Path $report) -or (Get-Item $report).Length -eq 0 -or -not (Select-String -Quiet -Path $report -Pattern '<testcase')) {
        throw "Expected fresh Failsafe report is missing or empty: $report"
    }
}

function Assert-DockerContainer {
    param(
        [Parameter(Mandatory = $true)][string]$Name
    )

    $status = ""
    for ($i = 0; $i -lt 60; $i++) {
        $status = docker inspect -f "{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}" $Name 2>$null
        if ($LASTEXITCODE -ne 0) {
            throw "Required container '$Name' is not available. Start foggy-dataset-demo docker services before external DB parity."
        }
        if ($status -eq "running healthy" -or $status -eq "running " -or $status -eq "running") {
            Write-Host "Container OK: $Name ($status)" -ForegroundColor Green
            return
        }
        Start-Sleep -Seconds 5
    }

    throw "Required container '$Name' did not become ready. Current status: $status"
}

if (-not $SkipFullRegression) {
    Invoke-ReleaseStep "Full module regression" @("test", "-P!multi-db")
}

Invoke-PivotParityIT "SQLite pivot SQL parity" "sqlite"

if (-not $SkipMcp) {
    Invoke-ReleaseStep "MCP schema and JSON-RPC guardrail" @(
        "test",
        "-pl", "foggy-dataset-mcp",
        "-Dtest=PivotSchemaValidationTest,AnalystMcpControllerTest",
        "-P!multi-db"
    )
}

if (-not $SkipExternalDb) {
    Assert-DockerContainer "foggy-demo-mysql8"
    Assert-DockerContainer "foggy-demo-postgres"

    Invoke-PivotParityIT "MySQL8 pivot SQL parity" "mysql8"
    Invoke-PivotParityIT "PostgreSQL pivot SQL parity" "postgres"
}

Write-Host ""
Write-Host "Pivot V9 release readiness verification passed." -ForegroundColor Green
