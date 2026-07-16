#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/verify-v934-database-matrix.sh"
STEP3_DIR="$ROOT_DIR/scripts/v934/step3"
STEP4_SUCCESSOR_DIR="$ROOT_DIR/scripts/v934/step4/successor"
CONTRACT="$STEP4_SUCCESSOR_DIR/database-matrix-contract.json"
SOURCE_AMENDMENT="$STEP4_SUCCESSOR_DIR/database-matrix-source-amendment.tsv"
REPORT_TOOL="$STEP4_SUCCESSOR_DIR/database_matrix_report_tool.py"
STATE_NEGATIVE_CONTRACT="$STEP3_DIR/database_state_contract.json"
STATE_NEGATIVE_TOOL="$STEP3_DIR/database_state_negative_tool.py"
PROVISIONER="$STEP3_DIR/provision-database-cell.sh"
SQLITE_TOOL="$STEP3_DIR/sqlite_cell_tool.py"
AUTHORITY_LIB="$ROOT_DIR/scripts/v934/authority_runner_lib.sh"
STEP1_TOOL="$ROOT_DIR/scripts/v934/inventory_tool.py"
STEP1_FREEZE="$ROOT_DIR/scripts/v934/contract-freeze.json"
COVERAGE_LIB="$ROOT_DIR/scripts/v934/step4/coverage_runner_lib.sh"
SQLITE_JAR="$HOME/.m2/repository/org/xerial/sqlite-jdbc/3.42.0.0/sqlite-jdbc-3.42.0.0.jar"
RUNNER_NAME="failsafe"
LANE="database-contract-matrix"
REPORTS_DIR="$ROOT_DIR/foggy-dataset-model/target/failsafe-reports"

STANDARD_SELECTORS="com.foggyframework.dataset.db.model.engine.pivot.PivotSqlParityIT,com.foggyframework.dataset.db.model.multidb.MultiDatabaseQueryTest,com.foggyframework.dataset.db.model.lifecycle.gate.RequiredDatabasePreflightIT,com.foggyframework.dataset.db.model.engine.pivot.PivotCascadeGenerateSqlParityIT,com.foggyframework.dataset.db.model.lifecycle.realquery.RequiredDatabaseQueryFacadeParityIT"
MYSQL8_TARGETED_SELECTORS="com.foggyframework.dataset.db.model.engine.pivot.PivotIT"
POSTGRES15_TARGETED_SELECTORS="com.foggyframework.dataset.db.model.engine.pivot.PivotIT,com.foggyframework.dataset.db.model.semantic.DslCteResultStageWindowIT,com.foggyframework.dataset.db.model.semantic.DslCteRelationMetricFixtureIT"

SENSITIVE_PATTERNS=(
  'foggy_test_123'
  'Foggy_Test_123!'
  'foggy_root_123'
  '(?i)(?:MYSQL_PWD|SQLCMDPASSWORD|(?:DB|DATABASE|JDBC|MYSQL|MYSQL_ROOT|POSTGRES|POSTGRESQL|MSSQL|SQLSERVER|SA|MONGO|MONGODB|REDIS)_PASSWORD)'
  '(?i)(?:^|[^A-Za-z0-9_])(?:password|passwd|pwd|credential|credentials)[[:space:]]*[:=][[:space:]]*[^[:space:]]+'
  '(?i)"(?:password|passwd|pwd|credential|credentials)"[[:space:]]*:[[:space:]]*"[^"]+"'
  '(?i)jdbc:[^[:space:]]*[?&;](?:password|passwd|pwd)=[^&;[:space:]]+'
  '(?i)jdbc:[^[:space:]]*//[^/@:[:space:]]+:[^/@[:space:]]+@'
  '(?i)(?:spring[.]datasource[.]password|datasource[.]password|jdbc[.]password)[[:space:]]*[:=][[:space:]]*[^[:space:]]+'
  '(?i)(?:mysql|postgres(?:ql)?|sqlserver|mongodb(?:[+]srv)?|redis)://[^/@:[:space:]]+:[^/@[:space:]]+@'
  '(?i)(?:--password|--passwd|--pwd)(?:=|[[:space:]])[^[:space:]]+'
)

fail() {
  echo "[v934-database-matrix] ERROR: $*" >&2
  exit 1
}

[[ -f "$COVERAGE_LIB" ]] || fail "required file missing: $COVERAGE_LIB"
# shellcheck source=scripts/v934/step4/coverage_runner_lib.sh
source "$COVERAGE_LIB"

sha256_file() {
  sha256sum "$1" | cut -d' ' -f1
}

atomic_env() {
  local output="$1"
  shift
  local temporary="${output}.$$.$RANDOM.tmp"
  printf '%s\n' "$@" > "$temporary"
  mv -f -- "$temporary" "$output"
}

matrix_source_hash() {
  python3 "$REPORT_TOOL" source-hash
}

write_outer_marker() {
  python3 - \
    "$OUTER_MARKER" "$RUN_ID" "$GIT_HEAD" "$CONTRACT_SHA256" \
    "$SOURCE_AMENDMENT_SHA256" "$STARTED_AT" <<'PY'
import json
import os
from pathlib import Path
import sys

output = Path(sys.argv[1])
payload = {
    "schema_version": 1,
    "kind": "v934-step3-database-matrix-outer-run",
    "run_id": sys.argv[2],
    "lane": "database-contract-matrix",
    "runner": "failsafe",
    "git_head": sys.argv[3],
    "contract_sha256": sys.argv[4],
    "source_amendment_sha256": sys.argv[5],
    "started_at": sys.argv[6],
    "status": "started",
}
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
os.replace(temporary, output)
PY
}

write_variant_marker() {
  local variant="$1"
  local database="$2"
  local marker="$3"
  python3 - \
    "$marker" "$RUN_ID" "$GIT_HEAD" "$CONTRACT_SHA256" \
    "$SOURCE_AMENDMENT_SHA256" "$variant" "$database" "$OUTER_MARKER_SHA256" <<'PY'
import datetime as dt
import json
import os
from pathlib import Path
import sys

output = Path(sys.argv[1])
payload = {
    "schema_version": 1,
    "kind": "v934-step3-database-matrix-variant-run",
    "run_id": sys.argv[2],
    "lane": "database-contract-matrix",
    "runner": "failsafe",
    "git_head": sys.argv[3],
    "contract_sha256": sys.argv[4],
    "source_amendment_sha256": sys.argv[5],
    "started_at": dt.datetime.now(dt.timezone.utc).isoformat(),
    "status": "started",
    "variant_key": sys.argv[6],
    "db_kind": sys.argv[7],
    "outer_marker_sha256": sys.argv[8],
}
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
os.replace(temporary, output)
PY
}

run_variant() {
  local variant="$1"
  local database="$2"
  local profile="$3"
  local selectors="$4"
  shift 4
  local variant_root="$RUN_ROOT/variants/$variant"
  local marker="$variant_root/run-marker.json"

  [[ "$variant" =~ ^[A-Za-z0-9._-]+$ ]] || fail "unsafe variant key: $variant"
  [[ ! -e "$variant_root" ]] || fail "variant root already exists: $variant_root"
  rm -rf -- "$REPORTS_DIR"
  mkdir -p "$variant_root"
  write_variant_marker "$variant" "$database" "$marker"

  echo "[v934-database-matrix] running variant=$variant database=$database profile=$profile"
  v934_coverage_configure it "$variant"
  (cd "$ROOT_DIR" && mvn -q \
    -P'!multi-db,!model-lifecycle,!query-cache-real-query' \
    -pl foggy-dataset-model -am \
    -Dit.test="$selectors" \
    -Dspring.profiles.active="$profile" \
    -Dv934.expectedDatabase="$database" \
    -DskipUnitTests=true \
    -DskipITs=false \
    -Dfailsafe.failIfNoTests=false \
    -Dfailsafe.failIfNoSpecifiedTests=false \
    "$@" \
    "${V934_COVERAGE_MAVEN_ARGS[@]}" \
    verify)
  v934_coverage_verify_exec

  python3 "$REPORT_TOOL" collect \
    --variant "$variant" \
    --reports-dir "$REPORTS_DIR" \
    --outer-marker "$OUTER_MARKER" \
    --run-marker "$marker" \
    --output "$variant_root/evidence"
}

run_external_cell() {
  local database="$1"
  local expected_profile expected_cell

  case "$database" in
    mysql57) expected_profile=docker ;;
    mysql8) expected_profile=mysql8 ;;
    postgres15) expected_profile=postgres ;;
    sqlserver2022) expected_profile=sqlserver ;;
    *) fail "unsupported callback database: $database" ;;
  esac
  expected_cell="$RUN_ROOT/cells/$database"
  [[ "${V934_DB_KIND:-}" == "$database" ]] || fail "callback database identity differs"
  [[ "${V934_DB_EXPECTED_DATABASE:-}" == "$database" ]] || fail "callback expected database differs"
  [[ "${V934_DB_PROFILE:-}" == "$expected_profile" ]] || fail "callback profile differs"
  [[ "${V934_DB_CELL_ROOT:-}" == "$expected_cell" ]] || fail "callback cell root differs"
  [[ -n "${V934_DB_CONTAINER:-}" ]] || fail "callback container identity is missing"
  [[ -f "$expected_cell/fixture-before.txt" ]] || fail "callback fixture snapshot is missing"

  python3 "$REPORT_TOOL" validate >/dev/null
  run_variant "db-$database" "$database" "$expected_profile" "$STANDARD_SELECTORS"
  case "$database" in
    mysql8)
      run_variant mysql8-targeted "$database" "$expected_profile" "$MYSQL8_TARGETED_SELECTORS"
      ;;
    postgres15)
      run_variant postgres15-targeted "$database" "$expected_profile" "$POSTGRES15_TARGETED_SELECTORS"
      ;;
  esac
}

INTERNAL_MODE=false
if [[ "${1:-}" == "--external-cell" ]]; then
  INTERNAL_MODE=true
  shift
  [[ "$#" -eq 2 ]] || fail "usage: $SCRIPT_PATH --external-cell <run-id> <database>"
  RUN_ID="$1"
  INTERNAL_DATABASE="$2"
else
  [[ "$#" -le 1 ]] || fail "usage: $SCRIPT_PATH [RUN_ID]"
  RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
fi

[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
[[ "$RUN_ID" != "." && "$RUN_ID" != ".." ]] || fail "invalid run id: $RUN_ID"
RUN_ROOT="$ROOT_DIR/target/v934-step3-database-matrix/runs/$RUN_ID"
OUTER_MARKER="$RUN_ROOT/run-context.json"
CONTRACT_SHA256="$(sha256_file "$CONTRACT")"
SOURCE_AMENDMENT_SHA256="$(sha256_file "$SOURCE_AMENDMENT")"
GIT_HEAD="$(git -C "$ROOT_DIR" rev-parse HEAD)"
OUTER_MARKER_SHA256=""

if [[ "$INTERNAL_MODE" == true ]]; then
  [[ -d "$RUN_ROOT" && -f "$OUTER_MARKER" ]] || fail "outer run is missing: $RUN_ROOT"
  OUTER_MARKER_SHA256="$(sha256_file "$OUTER_MARKER")"
  run_external_cell "$INTERNAL_DATABASE"
  exit 0
fi

for command_name in bash cut docker flock git grep mkfifo mv mvn python3 rg sha256sum sleep tee; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done
for required_file in \
  "$SCRIPT_PATH" "$CONTRACT" "$SOURCE_AMENDMENT" "$REPORT_TOOL" \
  "$STATE_NEGATIVE_CONTRACT" "$STATE_NEGATIVE_TOOL" "$PROVISIONER" \
  "$SQLITE_TOOL" "$AUTHORITY_LIB" "$STEP1_TOOL" "$STEP1_FREEZE" "$COVERAGE_LIB"; do
  [[ -f "$required_file" ]] || fail "required file missing: $required_file"
done
for variable_name in MAVEN_ARGS MAVEN_CONFIG MAVEN_OPTS; do
  variable_value="${!variable_name:-}"
  if [[ "$variable_value" =~ (skipTests|skipITs|skipUnitTests|multi-db|model-lifecycle|query-cache-real-query|failIfNo[A-Za-z0-9._-]*) ]]; then
    fail "$variable_name contains a forbidden lane override"
  fi
done
if [[ -f "$ROOT_DIR/.mvn/maven.config" ]] && \
   grep -Eq '(skipTests|skipITs|skipUnitTests|multi-db|model-lifecycle|query-cache-real-query|failIfNo[A-Za-z0-9._-]*)' "$ROOT_DIR/.mvn/maven.config"; then
  fail ".mvn/maven.config contains a forbidden lane override"
fi

# shellcheck source=scripts/v934/authority_runner_lib.sh
source "$AUTHORITY_LIB"
v934_acquire_or_validate_authority_lock "$ROOT_DIR" "v934-database-matrix" || exit 1

[[ ! -e "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
mkdir -p "$RUN_ROOT/variants" "$RUN_ROOT/preflight" "$RUN_ROOT/cells"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
PHASE="bootstrap"
SOURCE_BEFORE=""
SOURCE_AFTER=""
OUTER_MARKER_SHA256=""
SUCCESSOR_MANIFEST_SHA256="$(sha256_file "$ROOT_DIR/scripts/v934/successor/step2/SHA256SUMS")"
FINAL_REPORT_MANIFEST_SHA256=""
STATE_NEGATIVE_MANIFEST="$RUN_ROOT/state-negative/manifest.json"
STATE_NEGATIVE_MANIFEST_SHA256=""
STATE_NEGATIVE_CONTRACT_SHA256="$(sha256_file "$STATE_NEGATIVE_CONTRACT")"
SQLITE_CELL="$RUN_ROOT/cells/sqlite"
SQLITE_DATABASE="$SQLITE_CELL/database.sqlite"
SQLITE_CLEANUP_REQUIRED=false
SQLITE_PREPARED=false
SQLITE_VERIFIED=false
RUN_LOG_FIFO="$RUN_ROOT/.run-log.fifo"
RUN_LOG_TEE_PID=""
RUN_LOG_OPEN=false

if [[ "${V934_AUTHORITY_LOCK_MODE:-standalone}" == inherited ]]; then
  atomic_env "$RUN_ROOT/parent-context.env" \
    "authority_kind=$V934_PARENT_AUTHORITY_KIND" \
    "run_id=$V934_PARENT_RUN_ID" \
    "git_head=$V934_PARENT_GIT_HEAD" \
    "contract_sha256=$V934_PARENT_CONTRACT_SHA256" \
    "source_sha256=$V934_PARENT_SOURCE_SHA256" \
    "outer_marker_sha256=$V934_PARENT_OUTER_MARKER_SHA256" \
    "outer_marker_path=$V934_PARENT_OUTER_MARKER_PATH"
fi

matrix_close_run_log() {
  local attempt
  local tee_code=0

  if [[ "$RUN_LOG_OPEN" == true ]]; then
    exec 1>&3 2>&4
    RUN_LOG_OPEN=false
    exec 3>&- 4>&-
  fi
  if [[ -n "$RUN_LOG_TEE_PID" ]]; then
    # A descendant could incorrectly outlive its owning command while still
    # holding the inherited FIFO writer. Never let EXIT/signal finalization
    # block forever on that descriptor: wait five seconds, terminate tee, and
    # turn the run red if EOF is not observed in time.
    for ((attempt = 0; attempt < 50; attempt++)); do
      if ! kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1; then
        break
      fi
      sleep 0.1
    done
    if kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1; then
      tee_code=124
      kill -TERM "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
      for ((attempt = 0; attempt < 10; attempt++)); do
        if ! kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1; then
          break
        fi
        sleep 0.1
      done
      if kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1; then
        kill -KILL "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
      fi
      wait "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
    elif wait "$RUN_LOG_TEE_PID"; then
      tee_code=0
    else
      tee_code=$?
    fi
    RUN_LOG_TEE_PID=""
  fi
  rm -f -- "$RUN_LOG_FIFO"
  return "$tee_code"
}

matrix_record_run_status() {
  local exit_code="$1"
  local cleanup_code=0
  trap '' INT TERM HUP
  trap - EXIT
  set +e
  if [[ "$SQLITE_CLEANUP_REQUIRED" == true ]]; then
    if [[ "$SQLITE_PREPARED" == true && "$SQLITE_VERIFIED" != true ]]; then
      python3 "$SQLITE_TOOL" verify \
        --root "$ROOT_DIR" --cell-root "$SQLITE_CELL" --database-file "$SQLITE_DATABASE" \
        --sqlite-jar "$SQLITE_JAR" || \
        cleanup_code=1
    fi
    python3 "$SQLITE_TOOL" cleanup \
      --root "$ROOT_DIR" --cell-root "$SQLITE_CELL" --database-file "$SQLITE_DATABASE" || \
      cleanup_code=1
    SQLITE_CLEANUP_REQUIRED=false
  fi
  if [[ "$cleanup_code" -ne 0 ]]; then
    PHASE="sqlite-cleanup-failed"
    exit_code=1
  fi
  if ! matrix_close_run_log; then
    PHASE="run-log-flush-failed"
    exit_code=1
  fi
  v934_record_run_status "$exit_code"
}

trap 'matrix_record_run_status "$?"' EXIT
trap 'v934_exit_on_signal 130' INT
trap 'v934_exit_on_signal 143' TERM
trap 'v934_exit_on_signal 129' HUP

# A named pipe plus an explicitly waited tee gives the runner a synchronous
# log boundary. Process substitution does not expose the tee PID and can leave
# run.log partially flushed while the credential scan is already reading it.
exec 3>&1 4>&2
mkfifo "$RUN_LOG_FIFO"
(
  # The runner owns logger shutdown. Keep group-delivered INT/TERM/HUP from
  # killing tee before the signal trap can close and synchronously flush it.
  trap '' INT TERM HUP
  exec tee -a "$RUN_ROOT/run.log"
) < "$RUN_LOG_FIFO" >&3 2>&4 &
RUN_LOG_TEE_PID=$!
RUN_LOG_OPEN=true
exec > "$RUN_LOG_FIFO" 2>&1
rm -f -- "$RUN_LOG_FIFO"

PHASE="contract-validate"
python3 "$REPORT_TOOL" validate
SOURCE_BEFORE="$(matrix_source_hash)"

PHASE="outer-marker"
write_outer_marker
OUTER_MARKER_SHA256="$(sha256_file "$OUTER_MARKER")"
python3 "$REPORT_TOOL" validate >/dev/null

PHASE="database-state-negative"
python3 "$STATE_NEGATIVE_TOOL" run --mode all --run-id "$RUN_ID" --companion
python3 "$STATE_NEGATIVE_TOOL" verify --manifest "$STATE_NEGATIVE_MANIFEST"
STATE_NEGATIVE_MANIFEST_SHA256="$(sha256_file "$STATE_NEGATIVE_MANIFEST")"

# Every external cell must be possible before the runner compiles tests, creates
# a container, or executes a positive lane. The provisioner repeats this check
# immediately before Compose up to close the TOCTOU window.
for database in mysql57 mysql8 postgres15 sqlserver2022; do
  PHASE="preflight-$database"
  "$PROVISIONER" check "$database" "$RUN_ID" "$RUN_ROOT/preflight/$database"
done

PHASE="test-bytecode-cleanup"
mapfile -t REACTOR_MODULES < <(python3 - "$STEP1_TOOL" "$ROOT_DIR" "$STEP1_FREEZE" <<'PY'
import json
import runpy
import sys
from pathlib import Path

namespace = runpy.run_path(sys.argv[1])
active = namespace["active_reactor_modules"](Path(sys.argv[2]))
freeze = json.loads(Path(sys.argv[3]).read_text(encoding="utf-8"))
reactor = freeze.get("reactor", {})
production = reactor.get("modules", [])
reporter = "build-support/foggy-coverage-report"
if (
    reactor.get("module_count") != 24
    or len(production) != 24
    or len(set(production)) != 24
    or reporter in production
):
    raise SystemExit("Step 1 frozen production reactor is not an exact 24-module set")
expected = sorted([*production, reporter])
if active != expected:
    missing = sorted(set(expected) - set(active))
    unexpected = sorted(set(active) - set(expected))
    raise SystemExit(
        f"active reactor differs from frozen24+reporter: missing={missing} unexpected={unexpected}"
    )
print("\n".join(sorted(production)))
PY
)
[[ "${#REACTOR_MODULES[@]}" -eq 24 ]] || \
  fail "active reactor must equal the frozen 24 production modules plus the coverage reporter"
for module in "${REACTOR_MODULES[@]}"; do
  [[ -d "$ROOT_DIR/$module" ]] || fail "reactor module is missing: $module"
  if v934_coverage_enabled; then
    # The Step 4 outer authority has already performed and sealed one fresh
    # full-reactor main/test compile.  Preserve those exact bytes: deleting
    # main trees would shrink the coverage denominator, while deleting all test
    # trees and recompiling only the model dependency closure would invalidate
    # the shared Step 2 derived-view class receipt.
    rm -rf "$ROOT_DIR/$module/target/failsafe-reports"
  else
    rm -rf \
      "$ROOT_DIR/$module/target/classes" \
      "$ROOT_DIR/$module/target/failsafe-reports" \
      "$ROOT_DIR/$module/target/generated-sources" \
      "$ROOT_DIR/$module/target/test-classes" \
      "$ROOT_DIR/$module/target/generated-test-sources" \
      "$ROOT_DIR/$module/target/maven-status/maven-compiler-plugin/compile" \
      "$ROOT_DIR/$module/target/maven-status/maven-compiler-plugin/testCompile"
  fi
done

PHASE="test-compile"
(cd "$ROOT_DIR" && mvn -q \
  -P'!multi-db,!model-lifecycle,!query-cache-real-query' \
  -pl foggy-dataset-model -am \
  -DskipUnitTests=true \
  -DskipITs=true \
  test-compile)
[[ -f "$SQLITE_JAR" ]] || fail "SQLite JDBC artifact is missing after test-compile: $SQLITE_JAR"

PHASE="sqlite-prepare"
SQLITE_CLEANUP_REQUIRED=true
python3 "$SQLITE_TOOL" prepare \
  --root "$ROOT_DIR" \
  --cell-root "$SQLITE_CELL" \
  --database-file "$SQLITE_DATABASE" \
  --sqlite-jar "$SQLITE_JAR"
SQLITE_PREPARED=true

PHASE="variant-db-sqlite"
SQLITE_JDBC_URL="jdbc:sqlite:$SQLITE_DATABASE"
run_variant db-sqlite sqlite sqlite "$STANDARD_SELECTORS" \
  -Dspring.datasource.url="$SQLITE_JDBC_URL" \
  -Dspring.sql.init.mode=never \
  -Dv934.sqlite.expectedUrl="$SQLITE_JDBC_URL"

PHASE="sqlite-verify"
python3 "$SQLITE_TOOL" verify \
  --root "$ROOT_DIR" --cell-root "$SQLITE_CELL" --database-file "$SQLITE_DATABASE" \
  --sqlite-jar "$SQLITE_JAR"
SQLITE_VERIFIED=true
PHASE="sqlite-cleanup"
python3 "$SQLITE_TOOL" cleanup \
  --root "$ROOT_DIR" --cell-root "$SQLITE_CELL" --database-file "$SQLITE_DATABASE"
SQLITE_CLEANUP_REQUIRED=false

for database in mysql57 mysql8 postgres15 sqlserver2022; do
  PHASE="cell-$database"
  "$PROVISIONER" run "$database" "$RUN_ID" "$RUN_ROOT/cells/$database" -- \
    "$SCRIPT_PATH" --external-cell "$RUN_ID" "$database"
  grep -Fxq 'status=passed' "$RUN_ROOT/cells/$database/status.env" || \
    fail "$database cell did not publish passed status"
  grep -Fxq 'status=passed' "$RUN_ROOT/cells/$database/cleanup.env" || \
    fail "$database cell did not prove cleanup"
done

PHASE="source-after"
SOURCE_AFTER="$(matrix_source_hash)"
[[ "$SOURCE_BEFORE" == "$SOURCE_AFTER" ]] || \
  fail "protected matrix source changed during execution"

PHASE="negative-probes"
python3 "$REPORT_TOOL" negative --output "$RUN_ROOT/negative/probes.tsv"

PHASE="finalize"
MANIFEST_ARGS=()
for variant in \
  db-sqlite db-mysql57 db-mysql8 mysql8-targeted \
  db-postgres15 postgres15-targeted db-sqlserver2022; do
  MANIFEST_ARGS+=(--manifest "$RUN_ROOT/variants/$variant/evidence/report-manifest.json")
done
python3 "$REPORT_TOOL" finalize \
  --outer-marker "$OUTER_MARKER" \
  "${MANIFEST_ARGS[@]}" \
  --cells-root "$RUN_ROOT/cells" \
  --output "$RUN_ROOT/final"
python3 "$REPORT_TOOL" verify-final \
  --outer-marker "$OUTER_MARKER" \
  --manifest "$RUN_ROOT/final/report-manifest.json"
FINAL_REPORT_MANIFEST_SHA256="$(sha256_file "$RUN_ROOT/final/report-manifest.json")"
python3 "$STATE_NEGATIVE_TOOL" verify --manifest "$STATE_NEGATIVE_MANIFEST"
[[ "$(sha256_file "$STATE_NEGATIVE_MANIFEST")" == "$STATE_NEGATIVE_MANIFEST_SHA256" ]] || \
  fail "database-state negative manifest changed during the positive matrix"

PHASE="run-log-flush"
matrix_close_run_log || fail "run log tee did not flush successfully"

PHASE="sensitive-scan"
SENSITIVE_SCAN_ARGS=()
for pattern in "${SENSITIVE_PATTERNS[@]}"; do
  SENSITIVE_SCAN_ARGS+=(-e "$pattern")
done
if rg -l --hidden \
  --glob '!run-status.env' \
  --glob '!sensitive-scan.matches' \
  "${SENSITIVE_SCAN_ARGS[@]}" \
  "$RUN_ROOT" > "$RUN_ROOT/sensitive-scan.matches"; then
  fail "run-owned evidence contains a database credential or credential variable"
else
  sensitive_scan_rc=$?
  [[ "$sensitive_scan_rc" -eq 1 ]] || \
    fail "sensitive evidence scan failed with rg exit code $sensitive_scan_rc"
fi
rm -f -- "$RUN_ROOT/sensitive-scan.matches"
printf 'patterns=%s\nstatus=passed\n' \
  "${#SENSITIVE_PATTERNS[@]}" > "$RUN_ROOT/sensitive-scan.env"

PHASE="completed"
v934_write_run_status 0
RUN_STATUS_SHA256="$(sha256_file "$RUN_ROOT/run-status.env")"
python3 - \
  "$RUN_ROOT/final/report-manifest.json" "$RUN_ROOT/summary.env" "$RUN_ID" \
  "$GIT_HEAD" "$SOURCE_BEFORE" "$SOURCE_AFTER" "$OUTER_MARKER_SHA256" \
  "$CONTRACT_SHA256" "$SOURCE_AMENDMENT_SHA256" "$FINAL_REPORT_MANIFEST_SHA256" \
  "$RUN_STATUS_SHA256" "$RUN_ROOT/cells" "$CONTRACT" \
  "$RUN_ROOT/negative/probes.tsv" "$RUN_ROOT/sensitive-scan.env" \
  "${#SENSITIVE_PATTERNS[@]}" "$STATE_NEGATIVE_MANIFEST" \
  "$STATE_NEGATIVE_CONTRACT" "$STATE_NEGATIVE_MANIFEST_SHA256" <<'PY'
import csv
import hashlib
import json
import os
from pathlib import Path
import sys

def read_env(path: Path) -> dict[str, str]:
    if path.is_symlink() or not path.is_file():
        raise SystemExit(f"required status file is not regular: {path}")
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or "=" not in line:
            raise SystemExit(f"malformed status line in {path}: {line!r}")
        key, value = line.split("=", 1)
        if not key or key in values:
            raise SystemExit(f"duplicate/blank status key in {path}: {key!r}")
        values[key] = value
    return values

def sha256_file(path: Path) -> str:
    if path.is_symlink() or not path.is_file():
        raise SystemExit(f"required evidence file is not regular: {path}")
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

manifest = json.load(open(sys.argv[1], encoding="utf-8"))
totals = manifest["totals"]
cells_root = Path(sys.argv[12])
contract_path = Path(sys.argv[13])
negative_path = Path(sys.argv[14])
sensitive_path = Path(sys.argv[15])
expected_sensitive_patterns = int(sys.argv[16])
state_manifest_path = Path(sys.argv[17])
state_contract_path = Path(sys.argv[18])
expected_state_manifest_sha256 = sys.argv[19]

sqlite_cleanup = read_env(cells_root / "sqlite" / "cleanup.env")
if sqlite_cleanup.get("database") != "sqlite" or sqlite_cleanup.get("status") != "passed":
    raise SystemExit("SQLite cleanup evidence is not passed")

external_databases = ("mysql57", "mysql8", "postgres15", "sqlserver2022")
external_cleanup_passed = 0
for database in external_databases:
    cleanup = read_env(cells_root / database / "cleanup.env")
    status = read_env(cells_root / database / "status.env")
    if (
        cleanup.get("database") != database
        or cleanup.get("status") != "passed"
        or status.get("database") != database
        or status.get("cleanup_status") != "passed"
        or status.get("status") != "passed"
    ):
        raise SystemExit(f"external cleanup evidence is not passed: {database}")
    external_cleanup_passed += 1

if negative_path.is_symlink() or not negative_path.is_file():
    raise SystemExit(f"negative probe evidence is not regular: {negative_path}")
with negative_path.open(encoding="utf-8", newline="") as stream:
    reader = csv.DictReader(stream, delimiter="\t")
    expected_header = ["probe", "expected_error", "actual_error", "status"]
    if reader.fieldnames != expected_header:
        raise SystemExit(f"negative probe header differs: {reader.fieldnames}")
    negative_rows = list(reader)
if contract_path.is_symlink() or not contract_path.is_file():
    raise SystemExit(f"database matrix contract is not regular: {contract_path}")
contract = json.loads(contract_path.read_text(encoding="utf-8"))
contract_probes = contract.get("negative_probes")
if not isinstance(contract_probes, list) or not contract_probes:
    raise SystemExit("contract negative_probes must be a non-empty list")
expected_negative_rows = []
for index, probe in enumerate(contract_probes):
    if (
        not isinstance(probe, dict)
        or set(probe) != {"probe", "expected_error"}
        or not isinstance(probe["probe"], str)
        or not probe["probe"]
        or not isinstance(probe["expected_error"], str)
        or not probe["expected_error"]
    ):
        raise SystemExit(f"malformed contract negative probe at index {index}")
    expected_negative_rows.append((probe["probe"], probe["expected_error"]))
actual_negative_rows = [
    (row["probe"], row["expected_error"])
    for row in negative_rows
]
if actual_negative_rows != expected_negative_rows:
    raise SystemExit("negative probe evidence differs from the ordered contract probes")
report_negative_passed = sum(
    row["status"] == "passed" and row["actual_error"] == row["expected_error"]
    for row in negative_rows
)
if report_negative_passed != len(negative_rows):
    raise SystemExit("one or more report negative probes did not pass")

sensitive_scan = read_env(sensitive_path)
if (
    sensitive_scan.get("status") != "passed"
    or sensitive_scan.get("patterns") != str(expected_sensitive_patterns)
):
    raise SystemExit("sensitive scan evidence is not passed or has the wrong pattern count")

state_manifest_sha256 = sha256_file(state_manifest_path)
state_contract_sha256 = sha256_file(state_contract_path)
if state_manifest_sha256 != expected_state_manifest_sha256:
    raise SystemExit("database-state negative manifest digest changed")
if state_manifest_path.resolve() != cells_root.parent.resolve() / "state-negative" / "manifest.json":
    raise SystemExit("database-state negative manifest path differs")
state_manifest = json.loads(state_manifest_path.read_text(encoding="utf-8"))
expected_state_totals = {
    "probes": 18,
    "evidence_tamper": 10,
    "runtime_lightweight": 2,
    "runtime_dynamic": 6,
    "signals": 3,
    "failed": 0,
}
if (
    state_manifest.get("kind") != "v934-step3-database-state-negative-manifest"
    or state_manifest.get("run_id") != sys.argv[3]
    or state_manifest.get("mode") != "all"
    or state_manifest.get("scope") != "database-companion"
    or state_manifest.get("lane") != "database-state-negative"
    or state_manifest.get("git_head") != sys.argv[4]
    or state_manifest.get("database_contract_sha256") != sys.argv[8]
    or state_manifest.get("database_outer_marker_sha256") != sys.argv[7]
    or state_manifest.get("state_contract_sha256") != state_contract_sha256
    or state_manifest.get("complete") is not True
    or state_manifest.get("totals") != expected_state_totals
):
    raise SystemExit("database-state negative manifest identity or totals differ")

values = {
    "run_id": sys.argv[3],
    "runner": manifest["runner"],
    "lane": manifest["lane"],
    "git_head": sys.argv[4],
    "database_cells": str(totals["database_cells"]),
    "variants": str(totals["variants"]),
    "reports": str(totals["reports"]),
    "testcase_nodes": str(totals["testcase_nodes"]),
    "failures": str(totals["failures"]),
    "errors": str(totals["errors"]),
    "skipped": str(totals["skipped"]),
    "source_before": sys.argv[5],
    "source_after": sys.argv[6],
    "outer_marker_sha256": sys.argv[7],
    "contract_sha256": sys.argv[8],
    "source_amendment_sha256": sys.argv[9],
    "final_report_manifest_sha256": sys.argv[10],
    "run_status_sha256": sys.argv[11],
    "sqlite_cleanup": sqlite_cleanup["status"],
    "external_cleanup": f"{external_cleanup_passed}/{len(external_databases)}",
    "report_negative_probes": f"{report_negative_passed}/{len(negative_rows)}",
    "report_negative_sha256": sha256_file(negative_path),
    "database_state_probes": "18/18",
    "database_state_complete": "true",
    "database_state_manifest_sha256": state_manifest_sha256,
    "database_state_contract_sha256": state_contract_sha256,
    "sensitive_scan": sensitive_scan["status"],
    "sensitive_scan_sha256": sha256_file(sensitive_path),
    "status": "passed",
}
output = Path(sys.argv[2])
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
temporary.write_text(
    "".join(f"{key}={value}\n" for key, value in values.items()),
    encoding="utf-8",
)
os.replace(temporary, output)
PY

v934_disarm_run_status_traps
echo "[v934-database-matrix] PASS run=$RUN_ID reports=29 testcase_nodes=370 state-negative=18/18 F0/E0/S0 evidence=$RUN_ROOT"
