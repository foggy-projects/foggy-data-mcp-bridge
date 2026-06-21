param(
    [string]$ReleaseVersion = "0.1.0",
    [string]$OutDir = "",
    [switch]$SkipTests,
    [switch]$SkipBuild,
    [switch]$Clean
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$repoRootFull = [System.IO.Path]::GetFullPath($repoRoot)

if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $OutDir = Join-Path $repoRootFull "target\runtime-api-launcher-release"
}

if ([System.IO.Path]::IsPathRooted($OutDir)) {
    $resolvedOutDir = [System.IO.Path]::GetFullPath($OutDir)
} else {
    $resolvedOutDir = [System.IO.Path]::GetFullPath((Join-Path (Get-Location).Path $OutDir))
}

$repoPrefix = $repoRootFull.TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
if (-not $resolvedOutDir.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "OutDir must be inside the repository: $resolvedOutDir"
}

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Content
    )
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Invoke-RepoCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    Write-Host "==> $Name"
    Push-Location $repoRootFull
    try {
        & $Command @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$Name failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

if ($Clean -and (Test-Path -LiteralPath $resolvedOutDir)) {
    Remove-Item -LiteralPath $resolvedOutDir -Recurse -Force
}

if (-not $SkipTests) {
    Invoke-RepoCommand `
        -Name "Runtime API targeted tests" `
        -Command "mvn" `
        -Arguments @("-pl", "foggy-runtime-api", "-am", "-Dtest=RuntimeCapabilitiesControllerEnabledTest", "-DfailIfNoTests=false", "-Dsurefire.failIfNoSpecifiedTests=false", "test")
}

if (-not $SkipBuild) {
    Invoke-RepoCommand `
        -Name "Runtime API launcher package" `
        -Command "mvn" `
        -Arguments @("clean", "package", "-B", "-pl", "foggy-mcp-launcher", "-am", "-Pruntime-api", "-DskipTests")
}

[xml]$pom = Get-Content -LiteralPath (Join-Path $repoRootFull "pom.xml")
$javaProjectVersion = [string]$pom.project.version
if ([string]::IsNullOrWhiteSpace($javaProjectVersion)) {
    throw "Unable to read project version from pom.xml"
}

$launcherTarget = Join-Path $repoRootFull "foggy-mcp-launcher\target"
$builtJar = Get-ChildItem -LiteralPath $launcherTarget -Filter "foggy-mcp-launcher-*.jar" |
    Where-Object { $_.Name -notlike "original-*" } |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1

if ($null -eq $builtJar) {
    throw "No launcher jar found under $launcherTarget"
}

New-Item -ItemType Directory -Force -Path $resolvedOutDir | Out-Null

$releaseJarName = "foggy-mcp-launcher-$javaProjectVersion-runtime-api.jar"
$releaseJarPath = Join-Path $resolvedOutDir $releaseJarName
Copy-Item -LiteralPath $builtJar.FullName -Destination $releaseJarPath -Force

$startPs1Name = "start-foggy-runtime.ps1"
$startShName = "start-foggy-runtime.sh"
$readmeName = "README-runtime-api-launcher.md"
$manifestName = "runtime-launcher-manifest.json"
$checksumsName = "SHA256SUMS"

$startPs1 = @'
param(
    [int]$Port = 18066,
    [string]$WorkDir = "",
    [string]$SqlitePath = "",
    [string]$BundleRegistryPath = "",
    [string]$DatasourceRegistryPath = "",
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

$jar = Join-Path $PSScriptRoot "__RELEASE_JAR_NAME__"
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Launcher jar not found: $jar"
}

$stdoutLog = Join-Path $WorkDir "runtime.out.log"
$stderrLog = Join-Path $WorkDir "runtime.err.log"
$javaArgs = @(
    "-Dfile.encoding=UTF-8",
    "-jar", $jar,
    "--server.port=$Port",
    "--spring.profiles.active=lite",
    "--foggy.runtime-api.enabled=true",
    "--foggy.runtime-api.bundle-registry.path=$BundleRegistryPath",
    "--foggy.runtime-api.datasource-registry.path=$DatasourceRegistryPath",
    "--foggy.data-viewer.enabled=false",
    "--foggy.mcp.audit.enabled=false",
    "--spring.datasource.url=jdbc:sqlite:$SqlitePath",
    "--spring.ai.openai.api-key=sk-runtime-demo",
    "--spring.ai.openai.base-url=http://127.0.0.1:9",
    "--spring.ai.openai.chat.options.model=runtime-demo"
)

$startArgs = @{
    FilePath = $JavaExe
    ArgumentList = $javaArgs
    WorkingDirectory = $WorkDir
    RedirectStandardOutput = $stdoutLog
    RedirectStandardError = $stderrLog
    WindowStyle = "Hidden"
    PassThru = $true
}
$process = Start-Process @startArgs

[ordered]@{
    runtimeUrl = "http://127.0.0.1:$Port"
    pid = $process.Id
    workDir = $WorkDir
    sqlitePath = $SqlitePath
    bundleRegistryPath = $BundleRegistryPath
    datasourceRegistryPath = $DatasourceRegistryPath
    stdoutLog = $stdoutLog
    stderrLog = $stderrLog
    securityMode = "none-dev-test-only"
} | ConvertTo-Json -Depth 4
'@.Replace("__RELEASE_JAR_NAME__", $releaseJarName)

$startSh = @'
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${PORT:-18066}"
WORK_DIR="${WORK_DIR:-"$SCRIPT_DIR/.foggy-runtime"}"
SQLITE_PATH="${SQLITE_PATH:-"$WORK_DIR/foggy-runtime.sqlite"}"
BUNDLE_REGISTRY_PATH="${BUNDLE_REGISTRY_PATH:-"$WORK_DIR/runtime-bundle-registry.json"}"
DATASOURCE_REGISTRY_PATH="${DATASOURCE_REGISTRY_PATH:-"$WORK_DIR/runtime-datasource-registry.json"}"
JAVA_EXE="${JAVA_EXE:-java}"
JAR="$SCRIPT_DIR/__RELEASE_JAR_NAME__"

if [[ ! -f "$JAR" ]]; then
  echo "Launcher jar not found: $JAR" >&2
  exit 1
fi

mkdir -p "$WORK_DIR"
STDOUT_LOG="$WORK_DIR/runtime.out.log"
STDERR_LOG="$WORK_DIR/runtime.err.log"

nohup "$JAVA_EXE" \
  -Dfile.encoding=UTF-8 \
  -jar "$JAR" \
  --server.port="$PORT" \
  --spring.profiles.active=lite \
  --foggy.runtime-api.enabled=true \
  --foggy.runtime-api.bundle-registry.path="$BUNDLE_REGISTRY_PATH" \
  --foggy.runtime-api.datasource-registry.path="$DATASOURCE_REGISTRY_PATH" \
  --foggy.data-viewer.enabled=false \
  --foggy.mcp.audit.enabled=false \
  --spring.datasource.url="jdbc:sqlite:$SQLITE_PATH" \
  --spring.ai.openai.api-key=sk-runtime-demo \
  --spring.ai.openai.base-url=http://127.0.0.1:9 \
  --spring.ai.openai.chat.options.model=runtime-demo \
  > "$STDOUT_LOG" 2> "$STDERR_LOG" &
PID="$!"

cat <<JSON
{
  "runtimeUrl": "http://127.0.0.1:$PORT",
  "pid": $PID,
  "workDir": "$WORK_DIR",
  "sqlitePath": "$SQLITE_PATH",
  "bundleRegistryPath": "$BUNDLE_REGISTRY_PATH",
  "datasourceRegistryPath": "$DATASOURCE_REGISTRY_PATH",
  "stdoutLog": "$STDOUT_LOG",
  "stderrLog": "$STDERR_LOG",
  "securityMode": "none-dev-test-only"
}
JSON
'@.Replace("__RELEASE_JAR_NAME__", $releaseJarName)

$readme = @'
# Foggy Runtime API Launcher

This package starts the Foggy lite Java runtime with the Runtime API profile enabled.

The launcher is for local development and AI-assisted analysis demos. It does not enable production authentication, authorization, RBAC, audit hardening, or tenant governance.

## Windows quick start

```powershell
.\start-foggy-runtime.ps1 -Port 18066
```

## Linux/macOS quick start

```bash
chmod +x ./start-foggy-runtime.sh
./start-foggy-runtime.sh
```

The default runtime URL is `http://127.0.0.1:18066`. Runtime state is written under `.foggy-runtime` next to this README unless a custom work directory is supplied.

## Runtime flags

- Spring profile: `lite`
- Runtime API: enabled
- Default SQLite database: `.foggy-runtime/foggy-runtime.sqlite`
- Bundle registry: `.foggy-runtime/runtime-bundle-registry.json`
- Datasource registry: `.foggy-runtime/runtime-datasource-registry.json`
- Security mode: `none-dev-test-only`

## Release contents

- `__RELEASE_JAR_NAME__`
- `start-foggy-runtime.ps1`
- `start-foggy-runtime.sh`
- `runtime-launcher-manifest.json`
- `SHA256SUMS`
'@.Replace("__RELEASE_JAR_NAME__", $releaseJarName)

Write-Utf8NoBom -Path (Join-Path $resolvedOutDir $startPs1Name) -Content $startPs1
Write-Utf8NoBom -Path (Join-Path $resolvedOutDir $startShName) -Content $startSh
Write-Utf8NoBom -Path (Join-Path $resolvedOutDir $readmeName) -Content $readme

$jarHash = (Get-FileHash -LiteralPath $releaseJarPath -Algorithm SHA256).Hash.ToLowerInvariant()
$jarBytes = (Get-Item -LiteralPath $releaseJarPath).Length
$gitBranch = (& git -C $repoRootFull rev-parse --abbrev-ref HEAD).Trim()
$gitCommit = (& git -C $repoRootFull rev-parse HEAD).Trim()

$manifest = [ordered]@{
    schemaVersion = "foggy-runtime-api-launcher/v1"
    releaseVersion = $ReleaseVersion
    javaProjectVersion = $javaProjectVersion
    artifactId = "foggy-mcp-launcher"
    jar = [ordered]@{
        file = $releaseJarName
        sha256 = $jarHash
        bytes = $jarBytes
    }
    runtimeApiProfile = $true
    defaultProfile = "lite"
    defaultPort = 18066
    securityMode = "none-dev-test-only"
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    source = [ordered]@{
        repository = "https://github.com/foggy-projects/foggy-data-mcp-bridge"
        branch = $gitBranch
        commit = $gitCommit
    }
    assets = @(
        $releaseJarName,
        $startPs1Name,
        $startShName,
        $readmeName,
        $manifestName,
        $checksumsName
    )
}

$manifestJson = ($manifest | ConvertTo-Json -Depth 10)
Write-Utf8NoBom -Path (Join-Path $resolvedOutDir $manifestName) -Content ($manifestJson + "`n")

$checksumFiles = @($releaseJarName, $startPs1Name, $startShName, $readmeName, $manifestName)
$checksumLines = foreach ($fileName in $checksumFiles) {
    $path = Join-Path $resolvedOutDir $fileName
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $fileName"
}
Write-Utf8NoBom -Path (Join-Path $resolvedOutDir $checksumsName) -Content (($checksumLines -join "`n") + "`n")

[ordered]@{
    outDir = $resolvedOutDir
    releaseVersion = $ReleaseVersion
    javaProjectVersion = $javaProjectVersion
    jar = $releaseJarName
    jarSha256 = $jarHash
    assets = @($releaseJarName, $startPs1Name, $startShName, $readmeName, $manifestName, $checksumsName)
} | ConvertTo-Json -Depth 5
