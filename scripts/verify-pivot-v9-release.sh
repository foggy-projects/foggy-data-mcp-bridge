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

run_pivot_parity_it() {
  local name="$1"
  local profile="$2"
  local report="$REPO_ROOT/foggy-dataset-model/target/failsafe-reports/TEST-com.foggyframework.dataset.db.model.engine.pivot.PivotSqlParityIT.xml"
  rm -f "$report"
  run_step "$name" \
    verify \
    -pl foggy-dataset-model \
    -am \
    -Dit.test=PivotSqlParityIT \
    -Dspring.profiles.active="$profile" \
    -DskipUnitTests=true \
    -DskipITs=false \
    -Dfailsafe.failIfNoSpecifiedTests=false \
    -P!multi-db
  [[ -s "$report" ]] && grep -q '<testcase' "$report" || {
    echo "Expected fresh Failsafe report is missing or empty: $report" >&2
    exit 1
  }
}

assert_container() {
  local name="$1"
  local status
  for _ in {1..60}; do
    status="$(docker inspect -f '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' "$name" 2>/dev/null || true)"
    case "$status" in
      "running healthy"|"running "|"running")
        echo "Container OK: $name ($status)"
        return 0
        ;;
      running*)
        sleep 5
        ;;
      "")
        echo "Required container '$name' is not available. Start foggy-dataset-demo docker services before external DB parity." >&2
        exit 1
        ;;
      *)
        sleep 5
        ;;
    esac
  done

  echo "Required container '$name' did not become ready. Current status: $status" >&2
  exit 1
}

if [[ "$SKIP_FULL_REGRESSION" -eq 0 ]]; then
  run_step "Full module regression" test -P!multi-db
fi

run_pivot_parity_it "SQLite pivot SQL parity" sqlite

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

  run_pivot_parity_it "MySQL8 pivot SQL parity" mysql8
  run_pivot_parity_it "PostgreSQL pivot SQL parity" postgres
fi

echo
echo "Pivot V9 release readiness verification passed."
