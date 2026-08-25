param(
    [int]$Port = 18066,
    [string]$WorkDir = "",
    [string]$SqlitePath = "",
    [string]$BundleRegistryPath = "",
    [string]$DatasourceRegistryPath = "",
    [string]$AnalyticsConsoleCatalogPath = "",
    [string]$AnalyticsConsoleFunctionTracePath = "",
    [switch]$AnalyticsConsole,
    [string]$JavaExe = "java"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($WorkDir)) {
    $WorkDir = Join-Path $PSScriptRoot ".foggy-runtime"
}
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null

if ([string]::IsNullOrWhiteSpace($SqlitePath)) {
    $SqlitePath = Join-Path $WorkDir "foggy-runtime.sqlite"
}
if ([string]::IsNullOrWhiteSpace($BundleRegistryPath)) {
    $BundleRegistryPath = Join-Path $WorkDir "runtime-bundle-registry.json"
}
if ([string]::IsNullOrWhiteSpace($DatasourceRegistryPath)) {
    $DatasourceRegistryPath = Join-Path $WorkDir "runtime-datasource-registry.json"
}
$LegacyDatasourceConfigDir = Join-Path $WorkDir "legacy-datasources"
New-Item -ItemType Directory -Force -Path $LegacyDatasourceConfigDir | Out-Null

$jar = Join-Path $PSScriptRoot "foggy-runtime-launcher-@RELEASE_VERSION@.jar"
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Launcher jar not found: $jar"
}

$stdoutLog = Join-Path $WorkDir "runtime.out.log"
$stderrLog = Join-Path $WorkDir "runtime.err.log"
$springProfiles = "lite"
$analyticsConsoleUrl = $null

$javaArgs = @(
    "-Dfile.encoding=UTF-8",
    "-jar", $jar,
    "--server.port=$Port",
    "--foggy.runtime-api.enabled=true",
    "--foggy.runtime-api.bundle-registry.path=$BundleRegistryPath",
    "--foggy.runtime-api.datasource-registry.path=$DatasourceRegistryPath",
    "--foggy.datasource.config.dir=$LegacyDatasourceConfigDir",
    "--foggy.data-viewer.enabled=false",
    "--foggy.mcp.audit.enabled=false",
    "--foggy.demo.enabled=false",
    "--spring.autoconfigure.exclude=com.foggyframework.odoo.bridge.OdooBridgeAutoConfiguration",
    "--spring.datasource.url=jdbc:sqlite:$SqlitePath",
    "--logging.level.org.springframework.ai=INFO",
    "--logging.level.com.foggyframework.core.spring.proxy=WARN"
)

if ($AnalyticsConsole) {
    $springProfiles = "lite,analytics-console"
    if ([string]::IsNullOrWhiteSpace($AnalyticsConsoleCatalogPath)) {
        $AnalyticsConsoleCatalogPath = Join-Path $WorkDir "analytics-console/catalog.json"
    }
    if ([string]::IsNullOrWhiteSpace($AnalyticsConsoleFunctionTracePath)) {
        $AnalyticsConsoleFunctionTracePath = Join-Path $WorkDir "analytics-console/function-traces"
    }
    $analyticsConsoleCatalogDirectory = Split-Path -Parent $AnalyticsConsoleCatalogPath
    if (-not [string]::IsNullOrWhiteSpace($analyticsConsoleCatalogDirectory)) {
        New-Item -ItemType Directory -Force -Path $analyticsConsoleCatalogDirectory | Out-Null
    }
    New-Item -ItemType Directory -Force -Path $AnalyticsConsoleFunctionTracePath | Out-Null
    $javaArgs += "--foggy.analytics-console.catalog-path=$AnalyticsConsoleCatalogPath"
    $javaArgs += "--foggy.analytics-console.function-trace-path=$AnalyticsConsoleFunctionTracePath"
    $analyticsConsoleUrl = "http://127.0.0.1:$Port/analytics-console/"
}

$javaArgs += "--spring.profiles.active=$springProfiles"

$process = Start-Process `
    -FilePath $JavaExe `
    -ArgumentList $javaArgs `
    -WorkingDirectory $WorkDir `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -WindowStyle Hidden `
    -PassThru

[ordered]@{
    runtimeUrl = "http://127.0.0.1:$Port"
    analyticsConsoleEnabled = [bool]$AnalyticsConsole
    analyticsConsoleUrl = $analyticsConsoleUrl
    pid = $process.Id
    workDir = $WorkDir
    sqlitePath = $SqlitePath
    bundleRegistryPath = $BundleRegistryPath
    datasourceRegistryPath = $DatasourceRegistryPath
    legacyDatasourceConfigDir = $LegacyDatasourceConfigDir
    stdoutLog = $stdoutLog
    stderrLog = $stderrLog
    securityMode = "none-dev-test-only"
} | ConvertTo-Json -Depth 4
