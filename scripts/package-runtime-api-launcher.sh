#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RELEASE_VERSION="0.1.0"
OUT_DIR="$REPO_ROOT/target/runtime-api-launcher-release"
SKIP_TESTS=0
SKIP_BUILD=0
CLEAN=0

usage() {
  cat <<'EOF'
Usage: package-runtime-api-launcher.sh [options]

Options:
  --release-version VERSION  Release version written to the manifest.
  --out-dir DIR              Output directory. Defaults to target/runtime-api-launcher-release.
  --skip-tests               Skip targeted Runtime API tests.
  --skip-build               Skip Maven launcher packaging.
  --clean                    Remove the output directory before packaging.
  -h, --help                 Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release-version)
      RELEASE_VERSION="$2"
      shift 2
      ;;
    --out-dir)
      OUT_DIR="$2"
      shift 2
      ;;
    --skip-tests)
      SKIP_TESTS=1
      shift
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --clean)
      CLEAN=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

OUT_DIR="$(python3 - "$OUT_DIR" <<'PY'
import os
import sys
print(os.path.abspath(sys.argv[1]))
PY
)"

case "$OUT_DIR" in
  "$REPO_ROOT"/*) ;;
  *)
    echo "OutDir must be inside the repository: $OUT_DIR" >&2
    exit 1
    ;;
esac

cd "$REPO_ROOT"

if [[ "$CLEAN" -eq 1 && -d "$OUT_DIR" ]]; then
  rm -rf "$OUT_DIR"
fi

if [[ "$SKIP_TESTS" -eq 0 ]]; then
  echo "==> Runtime API targeted tests"
  mvn -pl foggy-runtime-api -am -Dtest=RuntimeCapabilitiesControllerEnabledTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
fi

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  echo "==> Runtime API launcher package"
  mvn clean package -B -pl foggy-mcp-launcher -am -Pruntime-api -DskipTests
fi

JAVA_PROJECT_VERSION="$(python3 - <<'PY'
import xml.etree.ElementTree as ET
root = ET.parse("pom.xml").getroot()
ns = {"m": root.tag.split("}")[0].strip("{")} if root.tag.startswith("{") else {}
if ns:
    version = root.findtext("m:version", namespaces=ns)
else:
    version = root.findtext("version")
if not version:
    raise SystemExit("Unable to read project version from pom.xml")
print(version)
PY
)"

BUILT_JAR="$(python3 - <<'PY'
from pathlib import Path

target = Path("foggy-mcp-launcher/target")
candidates = [
    path
    for path in target.glob("foggy-mcp-launcher-*.jar")
    if not path.name.startswith("original-")
]
if candidates:
    print(max(candidates, key=lambda path: path.stat().st_mtime).resolve())
PY
)"
if [[ -z "$BUILT_JAR" ]]; then
  echo "No launcher jar found under foggy-mcp-launcher/target" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"

RELEASE_JAR_NAME="foggy-mcp-launcher-$JAVA_PROJECT_VERSION-runtime-api.jar"
RELEASE_JAR_PATH="$OUT_DIR/$RELEASE_JAR_NAME"
cp "$BUILT_JAR" "$RELEASE_JAR_PATH"

START_PS1_NAME="start-foggy-runtime.ps1"
START_SH_NAME="start-foggy-runtime.sh"
README_NAME="README-runtime-api-launcher.md"
MANIFEST_NAME="runtime-launcher-manifest.json"
CHECKSUMS_NAME="SHA256SUMS"

cat > "$OUT_DIR/$START_PS1_NAME" <<EOF
param(
    [int]\$Port = 18066,
    [string]\$WorkDir = "",
    [string]\$SqlitePath = "",
    [string]\$BundleRegistryPath = "",
    [string]\$DatasourceRegistryPath = "",
    [string]\$JavaExe = "java"
)

Set-StrictMode -Version Latest
\$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace(\$WorkDir)) {
    \$WorkDir = Join-Path \$PSScriptRoot ".foggy-runtime"
}
New-Item -ItemType Directory -Force -Path \$WorkDir | Out-Null

if ([string]::IsNullOrWhiteSpace(\$SqlitePath)) {
    \$SqlitePath = Join-Path \$WorkDir "foggy-runtime.sqlite"
}
if ([string]::IsNullOrWhiteSpace(\$BundleRegistryPath)) {
    \$BundleRegistryPath = Join-Path \$WorkDir "runtime-bundle-registry.json"
}
if ([string]::IsNullOrWhiteSpace(\$DatasourceRegistryPath)) {
    \$DatasourceRegistryPath = Join-Path \$WorkDir "runtime-datasource-registry.json"
}
\$LegacyDatasourceConfigDir = Join-Path \$WorkDir "legacy-datasources"
New-Item -ItemType Directory -Force -Path \$LegacyDatasourceConfigDir | Out-Null

\$jar = Join-Path \$PSScriptRoot "$RELEASE_JAR_NAME"
if (-not (Test-Path -LiteralPath \$jar)) {
    throw "Launcher jar not found: \$jar"
}

\$stdoutLog = Join-Path \$WorkDir "runtime.out.log"
\$stderrLog = Join-Path \$WorkDir "runtime.err.log"
\$javaArgs = @(
    "-Dfile.encoding=UTF-8",
    "-jar", \$jar,
    "--server.port=\$Port",
    "--spring.profiles.active=lite",
    "--foggy.runtime-api.enabled=true",
    "--foggy.runtime-api.bundle-registry.path=\$BundleRegistryPath",
    "--foggy.runtime-api.datasource-registry.path=\$DatasourceRegistryPath",
    "--foggy.datasource.config.dir=\$LegacyDatasourceConfigDir",
    "--foggy.data-viewer.enabled=false",
    "--foggy.mcp.audit.enabled=false",
    "--foggy.demo.enabled=false",
    "--spring.autoconfigure.exclude=com.foggyframework.odoo.bridge.OdooBridgeAutoConfiguration",
    "--spring.datasource.url=jdbc:sqlite:\$SqlitePath",
    "--spring.ai.openai.api-key=sk-runtime-demo",
    "--spring.ai.openai.base-url=http://127.0.0.1:9",
    "--spring.ai.openai.chat.options.model=runtime-demo",
    "--logging.level.org.springframework.ai=INFO",
    "--logging.level.com.foggyframework.core.spring.proxy=WARN"
)

\$process = Start-Process \`
    -FilePath \$JavaExe \`
    -ArgumentList \$javaArgs \`
    -WorkingDirectory \$WorkDir \`
    -RedirectStandardOutput \$stdoutLog \`
    -RedirectStandardError \$stderrLog \`
    -WindowStyle Hidden \`
    -PassThru

[ordered]@{
    runtimeUrl = "http://127.0.0.1:\$Port"
    pid = \$process.Id
    workDir = \$WorkDir
    sqlitePath = \$SqlitePath
    bundleRegistryPath = \$BundleRegistryPath
    datasourceRegistryPath = \$DatasourceRegistryPath
    legacyDatasourceConfigDir = \$LegacyDatasourceConfigDir
    stdoutLog = \$stdoutLog
    stderrLog = \$stderrLog
    securityMode = "none-dev-test-only"
} | ConvertTo-Json -Depth 4
EOF

cat > "$OUT_DIR/$START_SH_NAME" <<EOF
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="\$(cd "\$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
PORT="\${PORT:-18066}"
WORK_DIR="\${WORK_DIR:-"\$SCRIPT_DIR/.foggy-runtime"}"
SQLITE_PATH="\${SQLITE_PATH:-"\$WORK_DIR/foggy-runtime.sqlite"}"
BUNDLE_REGISTRY_PATH="\${BUNDLE_REGISTRY_PATH:-"\$WORK_DIR/runtime-bundle-registry.json"}"
DATASOURCE_REGISTRY_PATH="\${DATASOURCE_REGISTRY_PATH:-"\$WORK_DIR/runtime-datasource-registry.json"}"
LEGACY_DATASOURCE_CONFIG_DIR="\${LEGACY_DATASOURCE_CONFIG_DIR:-"\$WORK_DIR/legacy-datasources"}"
JAVA_EXE="\${JAVA_EXE:-java}"
JAR="\$SCRIPT_DIR/$RELEASE_JAR_NAME"

if [[ ! -f "\$JAR" ]]; then
  echo "Launcher jar not found: \$JAR" >&2
  exit 1
fi

mkdir -p "\$WORK_DIR"
mkdir -p "\$LEGACY_DATASOURCE_CONFIG_DIR"
STDOUT_LOG="\$WORK_DIR/runtime.out.log"
STDERR_LOG="\$WORK_DIR/runtime.err.log"

nohup "\$JAVA_EXE" \\
  -Dfile.encoding=UTF-8 \\
  -jar "\$JAR" \\
  --server.port="\$PORT" \\
  --spring.profiles.active=lite \\
  --foggy.runtime-api.enabled=true \\
  --foggy.runtime-api.bundle-registry.path="\$BUNDLE_REGISTRY_PATH" \\
  --foggy.runtime-api.datasource-registry.path="\$DATASOURCE_REGISTRY_PATH" \\
  --foggy.datasource.config.dir="\$LEGACY_DATASOURCE_CONFIG_DIR" \\
  --foggy.data-viewer.enabled=false \\
  --foggy.mcp.audit.enabled=false \\
  --foggy.demo.enabled=false \\
  --spring.autoconfigure.exclude=com.foggyframework.odoo.bridge.OdooBridgeAutoConfiguration \\
  --spring.datasource.url="jdbc:sqlite:\$SQLITE_PATH" \\
  --spring.ai.openai.api-key=sk-runtime-demo \\
  --spring.ai.openai.base-url=http://127.0.0.1:9 \\
  --spring.ai.openai.chat.options.model=runtime-demo \\
  --logging.level.org.springframework.ai=INFO \\
  --logging.level.com.foggyframework.core.spring.proxy=WARN \\
  > "\$STDOUT_LOG" 2> "\$STDERR_LOG" &
PID="\$!"

cat <<JSON
{
  "runtimeUrl": "http://127.0.0.1:\$PORT",
  "pid": \$PID,
  "workDir": "\$WORK_DIR",
  "sqlitePath": "\$SQLITE_PATH",
  "bundleRegistryPath": "\$BUNDLE_REGISTRY_PATH",
  "datasourceRegistryPath": "\$DATASOURCE_REGISTRY_PATH",
  "legacyDatasourceConfigDir": "\$LEGACY_DATASOURCE_CONFIG_DIR",
  "stdoutLog": "\$STDOUT_LOG",
  "stderrLog": "\$STDERR_LOG",
  "securityMode": "none-dev-test-only"
}
JSON
EOF
chmod +x "$OUT_DIR/$START_SH_NAME"

cat > "$OUT_DIR/$README_NAME" <<EOF
# Foggy Runtime API Launcher

This package starts the Foggy lite Java runtime with the Runtime API profile enabled.

The launcher is for local development and AI-assisted analysis demos. It does not enable production authentication, authorization, RBAC, audit hardening, or tenant governance.

## Windows quick start

\`\`\`powershell
.\start-foggy-runtime.ps1 -Port 18066
\`\`\`

## Linux/macOS quick start

\`\`\`bash
chmod +x ./start-foggy-runtime.sh
./start-foggy-runtime.sh
\`\`\`

The default runtime URL is \`http://127.0.0.1:18066\`. Runtime state is written under \`.foggy-runtime\` next to this README unless a custom work directory is supplied.

## Runtime flags

- Spring profile: \`lite\`
- Runtime API: enabled
- Default SQLite database: \`.foggy-runtime/foggy-runtime.sqlite\`
- Bundle registry: \`.foggy-runtime/runtime-bundle-registry.json\`
- Datasource registry: \`.foggy-runtime/runtime-datasource-registry.json\`
- Legacy datasource config directory: \`.foggy-runtime/legacy-datasources\`
- Built-in ecommerce demo bundle: disabled for public sales-drop onboarding
- Odoo bridge auto-configuration: excluded for public sales-drop onboarding
- Security mode: \`none-dev-test-only\`

## Release contents

- \`$RELEASE_JAR_NAME\`
- \`start-foggy-runtime.ps1\`
- \`start-foggy-runtime.sh\`
- \`runtime-launcher-manifest.json\`
- \`SHA256SUMS\`
EOF

JAR_SHA256="$(sha256sum "$RELEASE_JAR_PATH" | awk '{print $1}')"
JAR_BYTES="$(wc -c < "$RELEASE_JAR_PATH" | tr -d ' ')"
GIT_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
GIT_COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
GENERATED_AT="$(python3 - <<'PY'
from datetime import datetime, timezone
print(datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"))
PY
)"

python3 - "$OUT_DIR/$MANIFEST_NAME" <<PY
import json
import os
import sys

manifest = {
    "schemaVersion": "foggy-runtime-api-launcher/v1",
    "releaseVersion": "$RELEASE_VERSION",
    "javaProjectVersion": "$JAVA_PROJECT_VERSION",
    "artifactId": "foggy-mcp-launcher",
    "jar": {
        "file": "$RELEASE_JAR_NAME",
        "sha256": "$JAR_SHA256",
        "bytes": int("$JAR_BYTES"),
    },
    "runtimeApiProfile": True,
    "defaultProfile": "lite",
    "defaultPort": 18066,
    "securityMode": "none-dev-test-only",
    "generatedAt": "$GENERATED_AT",
    "source": {
        "repository": "https://github.com/foggy-projects/foggy-data-mcp-bridge",
        "branch": "$GIT_BRANCH",
        "commit": "$GIT_COMMIT",
    },
    "assets": [
        "$RELEASE_JAR_NAME",
        "$START_PS1_NAME",
        "$START_SH_NAME",
        "$README_NAME",
        "$MANIFEST_NAME",
        "$CHECKSUMS_NAME",
    ],
}

with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as fh:
    json.dump(manifest, fh, indent=2)
    fh.write("\n")
PY

(
  cd "$OUT_DIR"
  sha256sum "$RELEASE_JAR_NAME" "$START_PS1_NAME" "$START_SH_NAME" "$README_NAME" "$MANIFEST_NAME" > "$CHECKSUMS_NAME"
)

python3 - <<PY
import json
print(json.dumps({
    "outDir": "$OUT_DIR",
    "releaseVersion": "$RELEASE_VERSION",
    "javaProjectVersion": "$JAVA_PROJECT_VERSION",
    "jar": "$RELEASE_JAR_NAME",
    "jarSha256": "$JAR_SHA256",
    "assets": [
        "$RELEASE_JAR_NAME",
        "$START_PS1_NAME",
        "$START_SH_NAME",
        "$README_NAME",
        "$MANIFEST_NAME",
        "$CHECKSUMS_NAME",
    ],
}, indent=2))
PY
