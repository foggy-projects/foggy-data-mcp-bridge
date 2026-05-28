#!/usr/bin/env bash
set -euo pipefail

DRY_RUN=0
START_DOCKER=1
DOWN_AFTER=0
RUN_SQLITE=1
RUN_MYSQL8=1
RUN_POSTGRES=1
RUN_SQLSERVER=1
RUN_P2_DSL=1
RUN_PIVOT=1
RUN_MCP=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/foggy-dataset-demo/docker/docker-compose.yml"

P2_DSL_TESTS="${V39_P2_DSL_TESTS:-RelationResultExpressionCompilerTest,DslCteAcceptanceSampleTest,DslCteRelationMetricFixtureIntegrationTest,DslCteResultStageWindowIntegrationTest}"
PIVOT_TESTS="${V39_PIVOT_TESTS:-PivotSqlParityIntegrationTest,PivotIntegrationTest,PivotCascadeGenerateValidationTest}"
SQLSERVER_TESTS="${V39_SQLSERVER_TESTS:-PivotSqlParityIntegrationTest}"
MCP_TESTS="${V39_MCP_TESTS:-PivotSchemaValidationTest,AnalystMcpControllerTest}"

usage() {
  cat <<'USAGE'
Usage: scripts/verify-v39-engine-production-gate.sh [options]

Runs the v3.9 engine production-gate candidate:
  - SQLite pivot parity fast path
  - MySQL8 pivot parity
  - PostgreSQL P2 DSL_CTE and pivot parity
  - SQL Server Maven profile candidate
  - MCP schema and JSON-RPC guardrail tests

The SQL Server lane is intentionally explicit. It is the v3.9 upgrade target
from v3.8's weekday-only SQL Server evidence and should not be treated as a
default release gate until it passes in CI and the v3.9 docs are refreshed.

Options:
  --dry-run             Print commands without executing them.
  --skip-docker-start   Do not start or initialize Docker services.
  --down-after          Stop Docker services with volumes after the run.
  --skip-sqlite         Skip SQLite checks.
  --skip-mysql8         Skip MySQL8 checks.
  --skip-postgres       Skip PostgreSQL checks.
  --skip-sqlserver      Skip SQL Server checks.
  --skip-p2-dsl         Skip P2 DSL_CTE checks.
  --skip-pivot          Skip pivot checks.
  --skip-mcp            Skip MCP schema / JSON-RPC guardrail checks.
  -h, --help            Show this help.
USAGE
}

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    --skip-docker-start) START_DOCKER=0 ;;
    --down-after) DOWN_AFTER=1 ;;
    --skip-sqlite) RUN_SQLITE=0 ;;
    --skip-mysql8) RUN_MYSQL8=0 ;;
    --skip-postgres) RUN_POSTGRES=0 ;;
    --skip-sqlserver) RUN_SQLSERVER=0 ;;
    --skip-p2-dsl) RUN_P2_DSL=0 ;;
    --skip-pivot) RUN_PIVOT=0 ;;
    --skip-mcp) RUN_MCP=0 ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      usage >&2
      exit 2
      ;;
  esac
done

cd "$REPO_ROOT"

run_cmd() {
  echo "+ $*"
  if [[ "$DRY_RUN" -eq 0 ]]; then
    "$@"
  fi
}

run_step() {
  local name="$1"
  shift
  echo
  echo "==> $name"
  run_cmd "$@"
}

compose_cmd() {
  if docker compose version >/dev/null 2>&1; then
    docker compose -f "$COMPOSE_FILE" "$@"
  else
    docker-compose -f "$COMPOSE_FILE" "$@"
  fi
}

run_compose() {
  echo "+ docker compose -f $COMPOSE_FILE $*"
  if [[ "$DRY_RUN" -eq 0 ]]; then
    compose_cmd "$@"
  fi
}

require_docker() {
  if [[ "$DRY_RUN" -eq 1 ]]; then
    return 0
  fi
  if ! command -v docker >/dev/null 2>&1; then
    echo "Docker CLI is required for external DB production-gate checks." >&2
    exit 1
  fi
  if ! docker compose version >/dev/null 2>&1 && ! command -v docker-compose >/dev/null 2>&1; then
    echo "docker compose or docker-compose is required." >&2
    exit 1
  fi
}

wait_container() {
  local name="$1"
  local status=""
  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "+ wait for container $name"
    return 0
  fi

  for _ in {1..90}; do
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
        echo "Required container '$name' is not available." >&2
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

cleanup() {
  if [[ "$DOWN_AFTER" -eq 1 ]]; then
    if [[ "$DRY_RUN" -eq 1 ]] || command -v docker >/dev/null 2>&1; then
      run_compose down -v
    else
      echo "Skip Docker cleanup: docker command is not available." >&2
    fi
  fi
}

external_services_selected() {
  [[ "$RUN_MYSQL8" -eq 1 || "$RUN_POSTGRES" -eq 1 || "$RUN_SQLSERVER" -eq 1 ]]
}

start_and_init_services() {
  local services=()
  if [[ "$RUN_MYSQL8" -eq 1 ]]; then
    services+=(mysql8)
  fi
  if [[ "$RUN_POSTGRES" -eq 1 ]]; then
    services+=(postgres)
  fi
  if [[ "$RUN_SQLSERVER" -eq 1 ]]; then
    services+=(sqlserver)
  fi
  if [[ "${#services[@]}" -eq 0 ]]; then
    return 0
  fi

  require_docker
  echo
  echo "==> Start external DB services"
  run_compose up -d "${services[@]}"

  if [[ "$RUN_MYSQL8" -eq 1 ]]; then
    wait_container foggy-demo-mysql8
  fi
  if [[ "$RUN_POSTGRES" -eq 1 ]]; then
    wait_container foggy-demo-postgres
    run_step "Initialize PostgreSQL fixture" bash foggy-dataset-demo/docker/init-db.sh postgres
  fi
  if [[ "$RUN_SQLSERVER" -eq 1 ]]; then
    wait_container foggy-demo-sqlserver
    run_step "Initialize SQL Server fixture" bash foggy-dataset-demo/docker/init-db.sh sqlserver
  fi
}

run_model_tests() {
  local name="$1"
  local tests="$2"
  local profile="$3"
  run_step "$name" \
    mvn -pl foggy-dataset-model -am test \
      -Dtest="$tests" \
      -Dspring.profiles.active="$profile" \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -P!multi-db
}

verify_sqlite() {
  if [[ "$RUN_SQLITE" -eq 0 || "$RUN_PIVOT" -eq 0 ]]; then
    return 0
  fi
  run_model_tests "SQLite v3.9 pivot production-gate fast path" "$PIVOT_TESTS" sqlite
}

verify_mysql8() {
  if [[ "$RUN_MYSQL8" -eq 0 || "$RUN_PIVOT" -eq 0 ]]; then
    return 0
  fi
  run_model_tests "MySQL8 v3.9 pivot production-gate" "$PIVOT_TESTS" mysql8
}

verify_postgres() {
  if [[ "$RUN_POSTGRES" -eq 0 ]]; then
    return 0
  fi
  if [[ "$RUN_P2_DSL" -eq 1 ]]; then
    run_model_tests "PostgreSQL v3.9 P2 DSL_CTE production-gate" "$P2_DSL_TESTS" postgres
  fi
  if [[ "$RUN_PIVOT" -eq 1 ]]; then
    run_model_tests "PostgreSQL v3.9 pivot production-gate" "$PIVOT_TESTS" postgres
  fi
}

verify_sqlserver() {
  if [[ "$RUN_SQLSERVER" -eq 0 ]]; then
    return 0
  fi
  run_model_tests "SQL Server v3.9 Maven profile production-gate candidate" "$SQLSERVER_TESTS" sqlserver
}

verify_mcp() {
  if [[ "$RUN_MCP" -eq 0 ]]; then
    return 0
  fi
  run_step "MCP v3.9 schema and JSON-RPC guardrail" \
    mvn -pl foggy-dataset-mcp test \
      -Dtest="$MCP_TESTS" \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -P!multi-db
}

main() {
  if [[ "$DOWN_AFTER" -eq 1 ]]; then
    trap cleanup EXIT
  fi

  if [[ "$START_DOCKER" -eq 1 ]]; then
    start_and_init_services
  elif external_services_selected; then
    require_docker
  fi

  verify_sqlite
  verify_mysql8
  verify_postgres
  verify_sqlserver
  verify_mcp

  echo
  echo "v3.9 engine production-gate candidate verification passed."
}

main
