#!/usr/bin/env bash
set -euo pipefail

DRY_RUN=0
START_DOCKER=1
DOWN_AFTER=0
RUN_POSTGRES=1
RUN_SQLSERVER=1
RUN_P2_DSL=1
RUN_PIVOT=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/foggy-dataset-demo/docker/docker-compose.yml"

POSTGRES_P2_DSL_UNIT_TESTS="RelationResultExpressionCompilerTest,DslCteAcceptanceSampleTest"
POSTGRES_P2_DSL_IT_TESTS="DslCteRelationMetricFixtureIT,DslCteResultStageWindowIT"
POSTGRES_PIVOT_UNIT_TESTS="PivotCascadeGenerateValidationTest"
POSTGRES_PIVOT_IT_TESTS="PivotIT"

usage() {
  cat <<'USAGE'
Usage: scripts/verify-v38-engine-evidence.sh [options]

Runs the v3.8 environment-gated engine evidence checks:
  - PostgreSQL P2 DSL_CTE alias/ranking/bucket targeted tests
  - PostgreSQL pivot tree/drilldown and weekday regression targeted tests
  - SQL Server dim_date weekday parity data check

Options:
  --dry-run             Print commands without executing them.
  --skip-docker-start   Do not start or initialize Docker services.
  --down-after          Stop Docker services with volumes after the run.
  --skip-postgres       Skip PostgreSQL checks.
  --skip-sqlserver      Skip SQL Server checks.
  --skip-p2-dsl         Skip PostgreSQL P2 DSL_CTE checks.
  --skip-pivot          Skip PostgreSQL pivot checks.
  -h, --help            Show this help.
USAGE
}

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    --skip-docker-start) START_DOCKER=0 ;;
    --down-after) DOWN_AFTER=1 ;;
    --skip-postgres) RUN_POSTGRES=0 ;;
    --skip-sqlserver) RUN_SQLSERVER=0 ;;
    --skip-p2-dsl) RUN_P2_DSL=0 ;;
    --skip-pivot) RUN_PIVOT=0 ;;
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

reset_model_reports() {
  local runner="$1"
  local selectors="$2"
  local dir="$REPO_ROOT/foggy-dataset-model-engine/target/${runner}-reports"
  [[ "$DRY_RUN" -eq 1 || ! -d "$dir" ]] && return 0
  local selector
  IFS=',' read -r -a selected <<< "$selectors"
  for selector in "${selected[@]}"; do
    find "$dir" -maxdepth 1 -type f -name "TEST-*.${selector}*.xml" -delete
  done
}

assert_model_reports() {
  local runner="$1"
  local selectors="$2"
  [[ "$DRY_RUN" -eq 1 ]] && return 0
  local dir="$REPO_ROOT/foggy-dataset-model-engine/target/${runner}-reports"
  local selector report found
  IFS=',' read -r -a selected <<< "$selectors"
  for selector in "${selected[@]}"; do
    found=0
    while IFS= read -r report; do
      if [[ -s "$report" ]] && grep -q '<testcase' "$report"; then
        found=1
        break
      fi
    done < <(find "$dir" -maxdepth 1 -type f -name "TEST-*.${selector}*.xml" 2>/dev/null)
    [[ "$found" -eq 1 ]] || {
      echo "Expected fresh $runner report with testcases for $selector" >&2
      exit 1
    }
  done
}

run_model_units() {
  local name="$1"
  local tests="$2"
  reset_model_reports surefire "$tests"
  run_step "$name" \
    mvn -pl foggy-dataset-model-engine -am test \
      -Dtest="$tests" \
      -Dspring.profiles.active=postgres \
      -DskipITs=true \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -P!multi-db
  assert_model_reports surefire "$tests"
}

run_model_its() {
  local name="$1"
  local tests="$2"
  reset_model_reports failsafe "$tests"
  run_step "$name" \
    mvn -pl foggy-dataset-model-engine -am verify \
      -Dit.test="$tests" \
      -Dspring.profiles.active=postgres \
      -DskipUnitTests=true \
      -DskipITs=false \
      -Dfailsafe.failIfNoSpecifiedTests=false \
      -P!multi-db
  assert_model_reports failsafe "$tests"
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
    echo "Docker CLI is required for external DB evidence checks." >&2
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

start_and_init_services() {
  local services=()
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

  if [[ "$RUN_POSTGRES" -eq 1 ]]; then
    wait_container foggy-demo-postgres
    run_step "Initialize PostgreSQL fixture" bash foggy-dataset-demo/docker/init-db.sh postgres
  fi
  if [[ "$RUN_SQLSERVER" -eq 1 ]]; then
    wait_container foggy-demo-sqlserver
    run_step "Initialize SQL Server fixture" bash foggy-dataset-demo/docker/init-db.sh sqlserver
  fi
}

verify_postgres_p2_dsl() {
  if [[ "$RUN_POSTGRES" -eq 0 || "$RUN_P2_DSL" -eq 0 ]]; then
    return 0
  fi

  run_model_units "PostgreSQL v3.8 P2 DSL_CTE unit evidence" "$POSTGRES_P2_DSL_UNIT_TESTS"
  run_model_its "PostgreSQL v3.8 P2 DSL_CTE integration evidence" "$POSTGRES_P2_DSL_IT_TESTS"
}

verify_postgres_pivot() {
  if [[ "$RUN_POSTGRES" -eq 0 || "$RUN_PIVOT" -eq 0 ]]; then
    return 0
  fi

  run_model_units "PostgreSQL v3.8 pivot unit evidence" "$POSTGRES_PIVOT_UNIT_TESTS"
  run_model_its "PostgreSQL v3.8 pivot integration evidence" "$POSTGRES_PIVOT_IT_TESTS"
}

verify_sqlserver_weekday() {
  if [[ "$RUN_SQLSERVER" -eq 0 ]]; then
    return 0
  fi

  local query
  query="SET NOCOUNT ON;
WITH expected(date_key, day_of_week, day_name, is_weekend) AS (
    SELECT 20240101, 1, N'周一', 0 UNION ALL
    SELECT 20240102, 2, N'周二', 0 UNION ALL
    SELECT 20240103, 3, N'周三', 0 UNION ALL
    SELECT 20240104, 4, N'周四', 0
)
IF EXISTS (
    SELECT 1
    FROM expected e
    LEFT JOIN dim_date d ON d.date_key = e.date_key
    WHERE d.date_key IS NULL
       OR d.day_of_week <> e.day_of_week
       OR d.day_name <> e.day_name
       OR d.is_weekend <> e.is_weekend
)
BEGIN
    SELECT e.date_key, e.day_of_week AS expected_day_of_week, d.day_of_week AS actual_day_of_week,
           e.day_name AS expected_day_name, d.day_name AS actual_day_name,
           e.is_weekend AS expected_is_weekend, d.is_weekend AS actual_is_weekend
    FROM expected e
    LEFT JOIN dim_date d ON d.date_key = e.date_key;
    THROW 51038, 'SQL Server weekday parity failed', 1;
END
SELECT d.date_key, d.day_of_week, d.day_name, d.is_weekend
FROM dim_date d
WHERE d.date_key IN (20240101, 20240102, 20240103, 20240104)
ORDER BY d.date_key;"

  run_step "SQL Server v3.8 weekday parity evidence" \
    docker exec -i foggy-demo-sqlserver /opt/mssql-tools18/bin/sqlcmd \
      -S localhost -U sa -P "Foggy_Test_123!" -C -d foggy_test \
      -Q "$query"
}

main() {
  if [[ "$DOWN_AFTER" -eq 1 ]]; then
    trap cleanup EXIT
  fi

  if [[ "$START_DOCKER" -eq 1 ]]; then
    start_and_init_services
  else
    require_docker
  fi

  verify_postgres_p2_dsl
  verify_postgres_pivot
  verify_sqlserver_weekday

  echo
  echo "v3.8 engine environment-gated evidence verification passed."
}

main
