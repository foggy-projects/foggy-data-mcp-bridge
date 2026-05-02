#!/usr/bin/env bash
set -euo pipefail

SKIP_FULL_REGRESSION=0
SKIP_EXTERNAL_DB=0
SKIP_MCP=0

for arg in "$@"; do
  case "$arg" in
    --skip-full-regression) SKIP_FULL_REGRESSION=1 ;;
    --skip-external-db) SKIP_EXTERNAL_DB=1 ;;
    --skip-mcp) SKIP_MCP=1 ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 2
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

run_step() {
  local name="$1"
  shift
  echo
  echo "==> $name"
  echo "mvn $*"
  mvn "$@"
}

assert_container() {
  local name="$1"
  local status
  if ! status="$(docker inspect -f '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' "$name" 2>/dev/null)"; then
    echo "Required container '$name' is not available. Start foggy-dataset-demo docker services before external DB parity." >&2
    exit 1
  fi
  case "$status" in
    running*) echo "Container OK: $name ($status)" ;;
    *)
      echo "Required container '$name' is not running. Current status: $status" >&2
      exit 1
      ;;
  esac
}

if [[ "$SKIP_FULL_REGRESSION" -eq 0 ]]; then
  run_step "Full module regression" test -P!multi-db
fi

run_step "SQLite pivot SQL parity" \
  test \
  -pl foggy-dataset-model \
  -Dtest=PivotSqlParityIntegrationTest \
  -Dspring.profiles.active=sqlite \
  -P!multi-db

if [[ "$SKIP_MCP" -eq 0 ]]; then
  run_step "MCP schema and JSON-RPC guardrail" \
    test \
    -pl foggy-dataset-mcp \
    -Dtest=PivotSchemaValidationTest,AnalystMcpControllerTest \
    -P!multi-db
fi

if [[ "$SKIP_EXTERNAL_DB" -eq 0 ]]; then
  assert_container foggy-demo-mysql8
  assert_container foggy-demo-postgres

  run_step "MySQL8 pivot SQL parity" \
    test \
    -pl foggy-dataset-model \
    -Dtest=PivotSqlParityIntegrationTest \
    -Dspring.profiles.active=mysql8 \
    -P!multi-db

  run_step "PostgreSQL pivot SQL parity" \
    test \
    -pl foggy-dataset-model \
    -Dtest=PivotSqlParityIntegrationTest \
    -Dspring.profiles.active=postgres \
    -P!multi-db
fi

echo
echo "Pivot V9 release readiness verification passed."
