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

Invoke-ReleaseStep "SQLite pivot SQL parity" @(
    "test",
    "-pl", "foggy-dataset-model",
    "-am",
    "-Dtest=PivotSqlParityIntegrationTest",
    "-Dspring.profiles.active=sqlite",
    "-Dsurefire.failIfNoSpecifiedTests=false",
    "-P!multi-db"
)

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

    Invoke-ReleaseStep "MySQL8 pivot SQL parity" @(
        "test",
        "-pl", "foggy-dataset-model",
        "-am",
        "-Dtest=PivotSqlParityIntegrationTest",
        "-Dspring.profiles.active=mysql8",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        "-P!multi-db"
    )

    Invoke-ReleaseStep "PostgreSQL pivot SQL parity" @(
        "test",
        "-pl", "foggy-dataset-model",
        "-am",
        "-Dtest=PivotSqlParityIntegrationTest",
        "-Dspring.profiles.active=postgres",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        "-P!multi-db"
    )
}

Write-Host ""
Write-Host "Pivot V9 release readiness verification passed." -ForegroundColor Green
