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

P2_DSL_UNIT_TESTS="${V39_P2_DSL_UNIT_TESTS:-RelationResultExpressionCompilerTest,DslCteAcceptanceSampleTest}"
P2_DSL_IT_TESTS="${V39_P2_DSL_IT_TESTS:-DslCteRelationMetricFixtureIT,DslCteResultStageWindowIT}"
PIVOT_UNIT_TESTS="${V39_PIVOT_UNIT_TESTS:-PivotCascadeGenerateValidationTest}"
PIVOT_IT_TESTS="${V39_PIVOT_IT_TESTS:-PivotSqlParityIT,PivotIT}"
SQLSERVER_IT_TESTS="${V39_SQLSERVER_IT_TESTS:-PivotSqlParityIT}"
MCP_TESTS="${V39_MCP_TESTS:-PivotSchemaValidationTest,AnalystMcpControllerTest}"

if [[ -n "${V39_P2_DSL_TESTS:-}${V39_PIVOT_TESTS:-}${V39_SQLSERVER_TESTS:-}" ]]; then
  echo "Combined V39_*_TESTS selectors are no longer accepted; configure the V39_*_UNIT_TESTS and V39_*_IT_TESTS variables separately." >&2
  exit 2
fi

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

expected_report_paths() {
  local module="$1"
  local runner="$2"
  local selectors="$3"
  python3 - "$REPO_ROOT/scripts/v934/successor/step2/source-inventory.tsv" \
    "$REPO_ROOT/scripts/v934/successor/step2/execution-inventory.tsv" \
    "$REPO_ROOT/scripts/v934/successor/step2/structural-report-inventory.tsv" \
    "$REPO_ROOT/$module/target/${runner}-reports" "$module" "$runner" "$selectors" <<'PY'
import csv
import pathlib
import sys

sources_path, execution_path, structural_path, report_dir, module, runner, raw_selectors = sys.argv[1:]
selected = {value.split("#", 1)[0].split("$", 1)[0].rsplit(".", 1)[-1] for value in raw_selectors.split(",") if value}
with open(sources_path, encoding="utf-8", newline="") as stream:
    source_by_id = {row["source_id"]: row for row in csv.DictReader(stream, delimiter="\t")}
with open(execution_path, encoding="utf-8", newline="") as stream:
    positive = [row for row in csv.DictReader(stream, delimiter="\t") if row["owner"] == module and row["runner"] == runner]
with open(structural_path, encoding="utf-8", newline="") as stream:
    structural = [row for row in csv.DictReader(stream, delimiter="\t") if row["module"] == module and row["runner"] == runner]
rows = [
    ("positive", source_by_id[row["source_id"]]["top_level_fqcn"], row["report_fqcn"])
    for row in positive
] + [("structural", row["source_fqcn"], row["report_fqcn"]) for row in structural]
found = {source_fqcn.rsplit(".", 1)[-1] for _, source_fqcn, _ in rows if source_fqcn.rsplit(".", 1)[-1] in selected}
if found != selected:
    raise SystemExit(f"selector inventory mismatch: missing={sorted(selected - found)}")
for kind, _, report in sorted({row for row in rows if row[1].rsplit(".", 1)[-1] in selected}):
    print(f"{kind}\t{pathlib.Path(report_dir) / f'TEST-{report}.xml'}")
PY
}

reset_reports() {
  local module="$1"
  local runner="$2"
  local selectors="$3"
  [[ "$DRY_RUN" -eq 1 ]] && return 0
  local expected_output report
  expected_output="$(expected_report_paths "$module" "$runner" "$selectors")" || exit 1
  [[ -n "$expected_output" ]] || { echo "No expected reports for $module:$selectors" >&2; exit 1; }
  while IFS=$'\t' read -r _ report; do rm -f "$report"; done <<< "$expected_output"
}

assert_reports() {
  local module="$1"
  local runner="$2"
  local selectors="$3"
  [[ "$DRY_RUN" -eq 1 ]] && return 0
  local expected_output report
  expected_output="$(expected_report_paths "$module" "$runner" "$selectors")" || exit 1
  [[ -n "$expected_output" ]] || { echo "No expected reports for $module:$selectors" >&2; exit 1; }
  while IFS=$'\t' read -r kind report; do
    python3 - "$kind" "$report" "$runner" <<'PY' || exit 1
import pathlib
import sys
import xml.etree.ElementTree as ET

kind, raw_path, runner = sys.argv[1:]
path = pathlib.Path(raw_path)
if not path.is_file() or path.stat().st_size == 0:
    raise SystemExit(f"Expected fresh {kind} {runner} report: {path}")
root = ET.parse(path).getroot()
suites = [node for node in root.iter() if node.tag.rsplit("}", 1)[-1] == "testsuite"]
testcases = [node for node in root.iter() if node.tag.rsplit("}", 1)[-1] == "testcase"]
totals = {name: sum(int(suite.attrib[name]) for suite in suites) for name in ("tests", "failures", "errors", "skipped")}
if totals["tests"] != len(testcases) or any(totals[name] for name in ("failures", "errors", "skipped")):
    raise SystemExit(f"Invalid {kind} {runner} report metrics {totals}: {path}")
if (kind == "positive" and totals["tests"] == 0) or (kind == "structural" and totals["tests"] != 0):
    raise SystemExit(f"Unexpected {kind} testcase count {totals['tests']}: {path}")
PY
  done <<< "$expected_output"
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
  local unit_tests="$2"
  local it_tests="$3"
  local profile="$4"
  if [[ -n "$unit_tests" ]]; then
    reset_reports foggy-dataset-model-engine surefire "$unit_tests"
    run_step "$name — unit" \
      mvn -pl foggy-dataset-model-engine -am test \
        -Dtest="$unit_tests" \
        -Dspring.profiles.active="$profile" \
        -DskipITs=true \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -P!multi-db
    assert_reports foggy-dataset-model-engine surefire "$unit_tests"
  fi
  if [[ -n "$it_tests" ]]; then
    reset_reports foggy-dataset-model-engine failsafe "$it_tests"
    run_step "$name — integration" \
      mvn -pl foggy-dataset-model-engine -am verify \
        -Dit.test="$it_tests" \
        -Dspring.profiles.active="$profile" \
        -DskipUnitTests=true \
        -DskipITs=false \
        -Dfailsafe.failIfNoSpecifiedTests=false \
        -P!multi-db
    assert_reports foggy-dataset-model-engine failsafe "$it_tests"
  fi
}

verify_sqlite() {
  if [[ "$RUN_SQLITE" -eq 0 || "$RUN_PIVOT" -eq 0 ]]; then
    return 0
  fi
  run_model_tests "SQLite v3.9 pivot production-gate fast path" "$PIVOT_UNIT_TESTS" "$PIVOT_IT_TESTS" sqlite
}

verify_mysql8() {
  if [[ "$RUN_MYSQL8" -eq 0 || "$RUN_PIVOT" -eq 0 ]]; then
    return 0
  fi
  run_model_tests "MySQL8 v3.9 pivot production-gate" "$PIVOT_UNIT_TESTS" "$PIVOT_IT_TESTS" mysql8
}

verify_postgres() {
  if [[ "$RUN_POSTGRES" -eq 0 ]]; then
    return 0
  fi
  if [[ "$RUN_P2_DSL" -eq 1 ]]; then
    run_model_tests "PostgreSQL v3.9 P2 DSL_CTE production-gate" "$P2_DSL_UNIT_TESTS" "$P2_DSL_IT_TESTS" postgres
  fi
  if [[ "$RUN_PIVOT" -eq 1 ]]; then
    run_model_tests "PostgreSQL v3.9 pivot production-gate" "$PIVOT_UNIT_TESTS" "$PIVOT_IT_TESTS" postgres
  fi
}

verify_sqlserver() {
  if [[ "$RUN_SQLSERVER" -eq 0 ]]; then
    return 0
  fi
  run_model_tests "SQL Server v3.9 Maven profile production-gate candidate" "" "$SQLSERVER_IT_TESTS" sqlserver
}

verify_mcp() {
  if [[ "$RUN_MCP" -eq 0 ]]; then
    return 0
  fi
  reset_reports foggy-dataset-mcp surefire "$MCP_TESTS"
  run_step "MCP v3.9 schema and JSON-RPC guardrail" \
    mvn -pl foggy-dataset-mcp -am test \
      -Dtest="$MCP_TESTS" \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -P!multi-db
  assert_reports foggy-dataset-mcp surefire "$MCP_TESTS"
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
