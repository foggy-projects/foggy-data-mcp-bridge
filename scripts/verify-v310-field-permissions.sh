#!/usr/bin/env bash
set -euo pipefail

DRY_RUN=0
START_DOCKER=1
DOWN_AFTER=0
RUN_CLEAN=1
RUN_MYSQL8=1
RUN_POSTGRES=1
RUN_FSSCRIPT=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/foggy-dataset-demo/docker/docker-compose.yml"
DEMO_DOCKER_DIR="$REPO_ROOT/foggy-dataset-demo/docker"

FIELD_PERMISSION_UNIT_TESTS="${V310_FIELD_PERMISSION_UNIT_TESTS:-FieldPermissionResolverTest,FieldAccessPermissionStepTest,SemanticServiceV3Test}"
FIELD_PERMISSION_IT_TESTS="${V310_FIELD_PERMISSION_IT_TESTS:-FieldAccessPermissionIT}"

if [[ -n "${V310_FIELD_PERMISSION_TESTS:-}" ]]; then
  echo "V310_FIELD_PERMISSION_TESTS is no longer accepted; use V310_FIELD_PERMISSION_UNIT_TESTS and V310_FIELD_PERMISSION_IT_TESTS." >&2
  exit 2
fi

usage() {
  cat <<'USAGE'
Usage: scripts/verify-v310-field-permissions.sh [options]

Runs the v3.10 dynamic TM/QM field-permission evidence checks:
  - clean reactor focused suite
  - Docker MySQL8 focused suite on 127.0.0.1:13308
  - Docker PostgreSQL focused suite on 127.0.0.1:15432
  - foggy-fsscript module regression

External DB startup uses docker compose when available and falls back to direct
docker run for the two required demo containers.

Options:
  --dry-run             Print commands without executing them.
  --skip-docker-start   Do not start Docker services.
  --down-after          Stop Docker services with volumes after the run.
  --skip-clean          Skip the clean reactor focused suite.
  --skip-mysql8         Skip MySQL8 checks.
  --skip-postgres       Skip PostgreSQL checks.
  --skip-fsscript       Skip foggy-fsscript module regression.
  -h, --help            Show this help.
USAGE
}

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    --skip-docker-start) START_DOCKER=0 ;;
    --down-after) DOWN_AFTER=1 ;;
    --skip-clean) RUN_CLEAN=0 ;;
    --skip-mysql8) RUN_MYSQL8=0 ;;
    --skip-postgres) RUN_POSTGRES=0 ;;
    --skip-fsscript) RUN_FSSCRIPT=0 ;;
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
  local runner="$1"
  local selectors="$2"
  python3 - "$REPO_ROOT/scripts/v934/successor/step2/source-inventory.tsv" \
    "$REPO_ROOT/scripts/v934/successor/step2/execution-inventory.tsv" \
    "$REPO_ROOT/scripts/v934/successor/step2/structural-report-inventory.tsv" \
    "$REPO_ROOT/foggy-dataset-model/target/${runner}-reports" "$runner" "$selectors" <<'PY'
import csv
import pathlib
import sys

sources_path, execution_path, structural_path, report_dir, runner, raw_selectors = sys.argv[1:]
selected = {value.split("#", 1)[0].split("$", 1)[0].rsplit(".", 1)[-1] for value in raw_selectors.split(",") if value}
with open(sources_path, encoding="utf-8", newline="") as stream:
    source_by_id = {row["source_id"]: row for row in csv.DictReader(stream, delimiter="\t")}
with open(execution_path, encoding="utf-8", newline="") as stream:
    positive = [row for row in csv.DictReader(stream, delimiter="\t") if row["owner"] == "foggy-dataset-model" and row["runner"] == runner]
with open(structural_path, encoding="utf-8", newline="") as stream:
    structural = [row for row in csv.DictReader(stream, delimiter="\t") if row["module"] == "foggy-dataset-model" and row["runner"] == runner]
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
  local runner="$1"
  local selectors="$2"
  [[ "$DRY_RUN" -eq 1 ]] && return 0
  local expected_output report
  expected_output="$(expected_report_paths "$runner" "$selectors")" || exit 1
  [[ -n "$expected_output" ]] || { echo "No expected reports for $selectors" >&2; exit 1; }
  while IFS=$'\t' read -r _ report; do rm -f "$report"; done <<< "$expected_output"
}

assert_reports() {
  local runner="$1"
  local selectors="$2"
  [[ "$DRY_RUN" -eq 1 ]] && return 0
  local expected_output report
  expected_output="$(expected_report_paths "$runner" "$selectors")" || exit 1
  [[ -n "$expected_output" ]] || { echo "No expected reports for $selectors" >&2; exit 1; }
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

run_field_permission_units() {
  local name="$1"
  local profile="$2"
  shift 2
  reset_reports surefire "$FIELD_PERMISSION_UNIT_TESTS"
  run_step "$name" \
    mvn -pl foggy-dataset-model -am "$@" test \
      -Dspring.profiles.active="$profile" \
      -Dtest="$FIELD_PERMISSION_UNIT_TESTS" \
      -DskipITs=true \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -P!multi-db
  assert_reports surefire "$FIELD_PERMISSION_UNIT_TESTS"
}

run_field_permission_it() {
  local name="$1"
  local profile="$2"
  reset_reports failsafe "$FIELD_PERMISSION_IT_TESTS"
  run_step "$name" \
    mvn -pl foggy-dataset-model -am verify \
      -Dspring.profiles.active="$profile" \
      -Dit.test="$FIELD_PERMISSION_IT_TESTS" \
      -DskipUnitTests=true \
      -DskipITs=false \
      -Dfailsafe.failIfNoSpecifiedTests=false \
      -P!multi-db
  assert_reports failsafe "$FIELD_PERMISSION_IT_TESTS"
}

compose_cmd() {
  if docker compose version >/dev/null 2>&1; then
    docker compose -f "$COMPOSE_FILE" "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose -f "$COMPOSE_FILE" "$@"
  else
    return 127
  fi
}

has_compose() {
  docker compose version >/dev/null 2>&1 || command -v docker-compose >/dev/null 2>&1
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
    echo "Docker CLI is required for MySQL8/PostgreSQL field-permission checks." >&2
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
    if has_compose; then
      run_compose down -v
    elif command -v docker >/dev/null 2>&1; then
      if [[ "$RUN_MYSQL8" -eq 1 ]]; then
        run_cmd docker rm -f foggy-demo-mysql8
      fi
      if [[ "$RUN_POSTGRES" -eq 1 ]]; then
        run_cmd docker rm -f foggy-demo-postgres
      fi
    else
      echo "Skip Docker cleanup: docker command is not available." >&2
    fi
  fi
}

external_services_selected() {
  [[ "$RUN_MYSQL8" -eq 1 || "$RUN_POSTGRES" -eq 1 ]]
}

start_services() {
  local services=()
  if [[ "$RUN_MYSQL8" -eq 1 ]]; then
    services+=(mysql8)
  fi
  if [[ "$RUN_POSTGRES" -eq 1 ]]; then
    services+=(postgres)
  fi
  if [[ "${#services[@]}" -eq 0 ]]; then
    return 0
  fi

  require_docker
  echo
  echo "==> Start external DB services"
  if has_compose; then
    run_compose up -d "${services[@]}"
  else
    start_services_without_compose "${services[@]}"
  fi

  if [[ "$RUN_MYSQL8" -eq 1 ]]; then
    wait_container foggy-demo-mysql8
  fi
  if [[ "$RUN_POSTGRES" -eq 1 ]]; then
    wait_container foggy-demo-postgres
  fi
}

container_exists() {
  if [[ "$DRY_RUN" -eq 1 ]]; then
    return 1
  fi
  docker inspect "$1" >/dev/null 2>&1
}

container_running() {
  if [[ "$DRY_RUN" -eq 1 ]]; then
    return 1
  fi
  [[ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null || true)" == "true" ]]
}

ensure_mysql8_container() {
  if container_running foggy-demo-mysql8; then
    echo "Container already running: foggy-demo-mysql8"
    return 0
  fi
  if container_exists foggy-demo-mysql8; then
    run_step "Start existing MySQL8 container" docker start foggy-demo-mysql8
    return 0
  fi

  run_step "Create MySQL8 container" \
    docker run -d \
      --name foggy-demo-mysql8 \
      -e MYSQL_ROOT_PASSWORD=foggy_root_123 \
      -e MYSQL_DATABASE=foggy_test \
      -e MYSQL_USER=foggy \
      -e MYSQL_PASSWORD=foggy_test_123 \
      -e TZ=Asia/Shanghai \
      -p 13308:3306 \
      -v "$DEMO_DOCKER_DIR/mysql/init:/docker-entrypoint-initdb.d:ro" \
      -v "$DEMO_DOCKER_DIR/mysql/conf/my.cnf:/etc/mysql/conf.d/custom.cnf:ro" \
      mysql:8.0 \
      --character-set-server=utf8mb4 \
      --collation-server=utf8mb4_unicode_ci \
      --default-time-zone='+08:00' \
      --lower-case-table-names=1 \
      --max-connections=500 \
      --innodb-buffer-pool-size=256M \
      --innodb-log-file-size=64M \
      --innodb-flush-log-at-trx-commit=2 \
      --sync-binlog=0
}

ensure_postgres_container() {
  if container_running foggy-demo-postgres; then
    echo "Container already running: foggy-demo-postgres"
    return 0
  fi
  if container_exists foggy-demo-postgres; then
    run_step "Start existing PostgreSQL container" docker start foggy-demo-postgres
    return 0
  fi

  run_step "Create PostgreSQL container" \
    docker run -d \
      --name foggy-demo-postgres \
      -e POSTGRES_DB=foggy_test \
      -e POSTGRES_USER=foggy \
      -e POSTGRES_PASSWORD=foggy_test_123 \
      -e TZ=Asia/Shanghai \
      -e PGTZ=Asia/Shanghai \
      -p 15432:5432 \
      -v "$DEMO_DOCKER_DIR/postgres/init:/docker-entrypoint-initdb.d:ro" \
      postgres:15-alpine
}

start_services_without_compose() {
  echo "docker compose is unavailable; using direct docker run fallback."
  for service in "$@"; do
    case "$service" in
      mysql8)
        ensure_mysql8_container
        ;;
      postgres)
        ensure_postgres_container
        ;;
      *)
        echo "Unsupported fallback service: $service" >&2
        exit 1
        ;;
    esac
  done
}

verify_clean_reactor() {
  if [[ "$RUN_CLEAN" -eq 0 ]]; then
    return 0
  fi
  run_field_permission_units "v3.10 clean reactor field-permission unit suite" sqlite clean
  run_field_permission_it "v3.10 clean reactor field-permission integration suite" sqlite
}

verify_mysql8() {
  if [[ "$RUN_MYSQL8" -eq 0 ]]; then
    return 0
  fi
  run_field_permission_units "v3.10 MySQL8 field-permission unit suite" mysql8
  run_field_permission_it "v3.10 MySQL8 field-permission integration suite" mysql8
}

verify_postgres() {
  if [[ "$RUN_POSTGRES" -eq 0 ]]; then
    return 0
  fi
  run_field_permission_units "v3.10 PostgreSQL field-permission unit suite" postgres
  run_field_permission_it "v3.10 PostgreSQL field-permission integration suite" postgres
}

verify_fsscript() {
  if [[ "$RUN_FSSCRIPT" -eq 0 ]]; then
    return 0
  fi
  run_step "foggy-fsscript module regression" \
    mvn -pl foggy-fsscript test
}

main() {
  if [[ "$DOWN_AFTER" -eq 1 ]]; then
    trap cleanup EXIT
  fi

  if [[ "$START_DOCKER" -eq 1 ]]; then
    start_services
  elif external_services_selected; then
    require_docker
  fi

  verify_clean_reactor
  verify_mysql8
  verify_postgres
  verify_fsscript

  echo
  echo "v3.10 dynamic TM/QM field-permission verification passed."
}

main
