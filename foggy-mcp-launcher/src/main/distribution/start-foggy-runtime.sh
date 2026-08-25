#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${PORT:-18066}"
WORK_DIR="${WORK_DIR:-"$SCRIPT_DIR/.foggy-runtime"}"
SQLITE_PATH="${SQLITE_PATH:-"$WORK_DIR/foggy-runtime.sqlite"}"
BUNDLE_REGISTRY_PATH="${BUNDLE_REGISTRY_PATH:-"$WORK_DIR/runtime-bundle-registry.json"}"
DATASOURCE_REGISTRY_PATH="${DATASOURCE_REGISTRY_PATH:-"$WORK_DIR/runtime-datasource-registry.json"}"
LEGACY_DATASOURCE_CONFIG_DIR="${LEGACY_DATASOURCE_CONFIG_DIR:-"$WORK_DIR/legacy-datasources"}"
ANALYTICS_CONSOLE_ENABLED="${ANALYTICS_CONSOLE_ENABLED:-false}"
ANALYTICS_CONSOLE_CATALOG_PATH="${ANALYTICS_CONSOLE_CATALOG_PATH:-"$WORK_DIR/analytics-console/catalog.json"}"
ANALYTICS_CONSOLE_FUNCTION_TRACE_PATH="${ANALYTICS_CONSOLE_FUNCTION_TRACE_PATH:-"$WORK_DIR/analytics-console/function-traces"}"
JAVA_EXE="${JAVA_EXE:-java}"
JAR="$SCRIPT_DIR/foggy-runtime-launcher-@RELEASE_VERSION@.jar"

case "$ANALYTICS_CONSOLE_ENABLED" in
  true|false) ;;
  *)
    echo "ANALYTICS_CONSOLE_ENABLED must be true or false; got: $ANALYTICS_CONSOLE_ENABLED" >&2
    exit 2
    ;;
esac

if [[ ! -f "$JAR" ]]; then
  echo "Launcher jar not found: $JAR" >&2
  exit 1
fi

mkdir -p "$WORK_DIR"
mkdir -p "$LEGACY_DATASOURCE_CONFIG_DIR"
STDOUT_LOG="$WORK_DIR/runtime.out.log"
STDERR_LOG="$WORK_DIR/runtime.err.log"
SPRING_PROFILES="lite"
ANALYTICS_CONSOLE_URL_JSON="null"
ANALYTICS_ARGS=()

if [[ "$ANALYTICS_CONSOLE_ENABLED" == "true" ]]; then
  SPRING_PROFILES="lite,analytics-console"
  mkdir -p "$(dirname "$ANALYTICS_CONSOLE_CATALOG_PATH")"
  mkdir -p "$ANALYTICS_CONSOLE_FUNCTION_TRACE_PATH"
  ANALYTICS_ARGS+=(
    "--foggy.analytics-console.catalog-path=$ANALYTICS_CONSOLE_CATALOG_PATH"
    "--foggy.analytics-console.function-trace-path=$ANALYTICS_CONSOLE_FUNCTION_TRACE_PATH"
  )
  ANALYTICS_CONSOLE_URL_JSON="\"http://127.0.0.1:$PORT/analytics-console/\""
fi

nohup "$JAVA_EXE" \
  -Dfile.encoding=UTF-8 \
  -jar "$JAR" \
  --server.port="$PORT" \
  --spring.profiles.active="$SPRING_PROFILES" \
  --foggy.runtime-api.enabled=true \
  --foggy.runtime-api.bundle-registry.path="$BUNDLE_REGISTRY_PATH" \
  --foggy.runtime-api.datasource-registry.path="$DATASOURCE_REGISTRY_PATH" \
  --foggy.datasource.config.dir="$LEGACY_DATASOURCE_CONFIG_DIR" \
  --foggy.data-viewer.enabled=false \
  --foggy.mcp.audit.enabled=false \
  --foggy.demo.enabled=false \
  --spring.autoconfigure.exclude=com.foggyframework.odoo.bridge.OdooBridgeAutoConfiguration \
  --spring.datasource.url="jdbc:sqlite:$SQLITE_PATH" \
  --logging.level.org.springframework.ai=INFO \
  --logging.level.com.foggyframework.core.spring.proxy=WARN \
  "${ANALYTICS_ARGS[@]}" \
  > "$STDOUT_LOG" 2> "$STDERR_LOG" &
PID="$!"

cat <<JSON
{
  "runtimeUrl": "http://127.0.0.1:$PORT",
  "analyticsConsoleEnabled": $ANALYTICS_CONSOLE_ENABLED,
  "analyticsConsoleUrl": $ANALYTICS_CONSOLE_URL_JSON,
  "pid": $PID,
  "workDir": "$WORK_DIR",
  "sqlitePath": "$SQLITE_PATH",
  "bundleRegistryPath": "$BUNDLE_REGISTRY_PATH",
  "datasourceRegistryPath": "$DATASOURCE_REGISTRY_PATH",
  "legacyDatasourceConfigDir": "$LEGACY_DATASOURCE_CONFIG_DIR",
  "stdoutLog": "$STDOUT_LOG",
  "stderrLog": "$STDERR_LOG",
  "securityMode": "none-dev-test-only"
}
JSON
