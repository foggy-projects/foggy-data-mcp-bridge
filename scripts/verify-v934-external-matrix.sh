#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/verify-v934-external-matrix.sh"
STEP3_DIR="$ROOT_DIR/scripts/v934/step3"
CONTRACT="$STEP3_DIR/external-matrix-contract.json"
REPORT_TOOL="$STEP3_DIR/external_matrix_report_tool.py"
AUTHORITY_LIB="$ROOT_DIR/scripts/v934/authority_runner_lib.sh"
SHARED_CONTEXT="$STEP3_DIR/external_shared_context.sh"
LANE_LAUNCHER="$STEP3_DIR/external_lane_launcher.py"
DEFERRED_INVENTORY="$ROOT_DIR/scripts/v934/successor/step2/deferred-step3.tsv"
RUNS_ROOT="$ROOT_DIR/target/v934-step3-external-matrix/runs"

REDIS_RUNNER="$ROOT_DIR/scripts/verify-v934-external-redis.sh"
MONGO_RUNNER="$ROOT_DIR/scripts/verify-v934-external-mongo.sh"
MYSQL_RUNNER="$ROOT_DIR/scripts/verify-v934-external-mysql.sh"
VECTOR_RUNNER="$ROOT_DIR/scripts/verify-v934-external-vector.sh"

RUNNER_NAME="failsafe"
LANE="external-matrix"
SIGNAL_PROBE_MODE="${V934_EXTERNAL_MATRIX_SIGNAL_PROBE:-false}"
RUN_LOG_FIFO=""
RUN_LOG_TEE_PID=""
RUN_LOG_OPEN=false
ACTIVE_CHILD_PID=""

SENSITIVE_PATTERNS=(
  '(?i)(?:REDIS_PASSWORD|REDIS_USERNAME|REDIS_URI|MONGO(?:DB)?_(?:URI|PASSWORD|USERNAME)|MYSQL_(?:PWD|PASSWORD|ROOT_PASSWORD)|MINIO_ROOT_(?:USER|PASSWORD)|AWS_(?:ACCESS_KEY_ID|SECRET_ACCESS_KEY))'
  '(?i)"?(?:password|passwd|pwd|credential|credentials|api[-_]?key|access[-_]?token|refresh[-_]?token|auth[-_]?token|secret|authorization)"?[[:space:]]*[:=][[:space:]]*(?!"?null"?(?:[[:space:],}\]]|$))"?[^"[:space:],}\]]+'
  '(?i)(?:authorization[[:space:]]*[:=][[:space:]]*)?bearer[[:space:]]+[A-Za-z0-9._~+/-]{8,}'
  '(?i)(?:redis|mongodb(?:\+srv)?|mysql|s3)://[^/@:[:space:]]+:[^/@[:space:]]+@'
  '(?i)(?:--password|--passwd|--pwd)(?:=|[[:space:]])[^[:space:]]+'
)

fail() {
  echo "[v934-external-matrix] ERROR: $*" >&2
  exit 1
}

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

write_outer_marker() {
  python3 - "$OUTER_MARKER" "$RUN_ID" "$GIT_HEAD" "$CONTRACT_SHA256" "$STARTED_AT" <<'PY'
import json
import os
from pathlib import Path
import sys

output = Path(sys.argv[1])
payload = {
    "schema_version": 1,
    "kind": "v934-step3-external-matrix-outer-run",
    "run_id": sys.argv[2],
    "lane": "external-matrix",
    "runner": "failsafe",
    "git_head": sys.argv[3],
    "contract_sha256": sys.argv[4],
    "started_at": sys.argv[5],
    "status": "started",
}
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
os.replace(temporary, output)
PY
}

close_run_log() {
  local code=0 attempt
  if [[ "$RUN_LOG_OPEN" == true ]]; then
    exec 1>&3 2>&4
    RUN_LOG_OPEN=false
  fi
  if [[ -n "$RUN_LOG_TEE_PID" ]]; then
    for attempt in $(seq 1 100); do
      kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || break
      sleep 0.05
    done
    if kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1; then
      kill -TERM "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
      for attempt in $(seq 1 20); do
        kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || break
        sleep 0.05
      done
      if kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1; then
        kill -KILL "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
        code=1
      fi
      wait "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
    elif ! wait "$RUN_LOG_TEE_PID"; then
      code=1
    fi
    RUN_LOG_TEE_PID=""
  fi
  [[ -z "$RUN_LOG_FIFO" ]] || rm -f -- "$RUN_LOG_FIFO"
  return "$code"
}

docker_label_count() {
  local kind="$1"
  case "$kind" in
    container)
      docker ps -aq --filter "label=com.foggy.v934.external-run=$RUN_ID" | sed '/^$/d' | wc -l | tr -d ' '
      ;;
    volume)
      docker volume ls -q --filter "label=com.foggy.v934.external-run=$RUN_ID" | sed '/^$/d' | wc -l | tr -d ' '
      ;;
    network)
      docker network ls -q --filter "label=com.foggy.v934.external-run=$RUN_ID" | sed '/^$/d' | wc -l | tr -d ' '
      ;;
    *) return 1 ;;
  esac
}

cleanup_matrix_resources() {
  local cleanup_code=0 identifiers identifier
  mkdir -p "$RUN_ROOT/aggregate"
  identifiers="$(docker ps -aq --filter "label=com.foggy.v934.external-run=$RUN_ID")" || cleanup_code=1
  for identifier in $identifiers; do
    docker rm -f "$identifier" >/dev/null 2>&1 || cleanup_code=1
  done
  identifiers="$(docker volume ls -q --filter "label=com.foggy.v934.external-run=$RUN_ID")" || cleanup_code=1
  for identifier in $identifiers; do
    docker volume rm -f "$identifier" >/dev/null 2>&1 || cleanup_code=1
  done
  identifiers="$(docker network ls -q --filter "label=com.foggy.v934.external-run=$RUN_ID")" || cleanup_code=1
  for identifier in $identifiers; do
    docker network rm "$identifier" >/dev/null 2>&1 || cleanup_code=1
  done
  local container_residue volume_residue network_residue
  container_residue="$(docker_label_count container)" || cleanup_code=1
  volume_residue="$(docker_label_count volume)" || cleanup_code=1
  network_residue="$(docker_label_count network)" || cleanup_code=1
  [[ "$container_residue" == 0 && "$volume_residue" == 0 && "$network_residue" == 0 ]] || cleanup_code=1
  atomic_env "$RUN_ROOT/aggregate/cleanup.env" \
    "container_residue=${container_residue:-unknown}" \
    "volume_residue=${volume_residue:-unknown}" \
    "network_residue=${network_residue:-unknown}" \
    "lane_count=4" \
    "status=$([[ "$cleanup_code" -eq 0 ]] && printf passed || printf failed)" || cleanup_code=1
  return "$cleanup_code"
}

terminate_active_child() {
  local signal_name="$1" attempt
  [[ -n "$ACTIVE_CHILD_PID" ]] || return 0
  if kill -0 "$ACTIVE_CHILD_PID" >/dev/null 2>&1; then
    kill -s "$signal_name" -- "-$ACTIVE_CHILD_PID" >/dev/null 2>&1 || true
    for attempt in $(seq 1 200); do
      kill -0 "$ACTIVE_CHILD_PID" >/dev/null 2>&1 || break
      sleep 0.1
    done
    if kill -0 "$ACTIVE_CHILD_PID" >/dev/null 2>&1; then
      kill -KILL -- "-$ACTIVE_CHILD_PID" >/dev/null 2>&1 || true
    fi
  fi
  wait "$ACTIVE_CHILD_PID" >/dev/null 2>&1 || true
  ACTIVE_CHILD_PID=""
}

handle_signal() {
  local signal_name="$1" exit_code="$2"
  trap '' INT TERM HUP
  terminate_active_child "$signal_name"
  exit "$exit_code"
}

record_run_status() {
  local exit_code="$1" finalizer_code=0
  trap '' INT TERM HUP
  trap - EXIT
  set +e
  terminate_active_child TERM
  if ! cleanup_matrix_resources; then
    PHASE="matrix-cleanup-failed"
    finalizer_code=1
  fi
  if ! close_run_log; then
    PHASE="run-log-flush-failed"
    finalizer_code=1
  fi
  [[ "$finalizer_code" -eq 0 ]] || exit_code=1
  if [[ "$exit_code" -eq 0 && "$PHASE" != completed ]]; then
    exit_code=1
  fi
  if [[ "$exit_code" -ne 0 ]]; then
    rm -f -- "$RUN_ROOT/summary.env" "$RUN_ROOT/candidate-manifest.json"
    rm -rf -- "$RUN_ROOT/final"
  fi
  v934_write_run_status "$exit_code" || exit_code=1
  exit "$exit_code"
}

run_lane() {
  local lane="$1" runner="$2" signal_environment="$3"
  local probe_value=false exit_code=0
  if [[ "$SIGNAL_PROBE_MODE" == true && "$lane" == external-redis ]]; then
    probe_value=true
  fi
  PHASE="lane-$lane"
  echo "[v934-external-matrix] running lane=$lane"
  python3 "$LANE_LAUNCHER" \
    --runner "$runner" \
    --run-id "$RUN_ID" \
    --signal-environment "$signal_environment" \
    --probe-value "$probe_value" \
    --lock-fd "$V934_AUTHORITY_LOCK_FD" &
  ACTIVE_CHILD_PID=$!
  if wait "$ACTIVE_CHILD_PID"; then
    exit_code=0
  else
    exit_code=$?
  fi
  ACTIVE_CHILD_PID=""
  [[ "$exit_code" -eq 0 ]] || return "$exit_code"
  python3 "$REPORT_TOOL" verify-candidate \
    --candidate "$RUN_ROOT/lanes/$lane/candidate-manifest.json"
  local containers volumes networks
  containers="$(docker_label_count container)"
  volumes="$(docker_label_count volume)"
  networks="$(docker_label_count network)"
  [[ "$containers" == 0 && "$volumes" == 0 && "$networks" == 0 ]] || \
    fail "lane $lane left Docker residue: $containers/$volumes/$networks"
}

write_aggregates() {
  python3 - "$RUN_ROOT" <<'PY'
import csv
import hashlib
import os
from pathlib import Path
import sys

root = Path(sys.argv[1])
lanes = (
    ("external-redis", "redis7"),
    ("external-mongo", "mongo6"),
    ("external-mysql", "mysql57"),
    ("external-vector", "milvus24"),
)

def sha(path: Path) -> str:
    if path.is_symlink() or not path.is_file():
        raise SystemExit(f"aggregate input is not regular: {path}")
    return hashlib.sha256(path.read_bytes()).hexdigest()

def atomic(path: Path, content: str) -> None:
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_text(content, encoding="utf-8")
    os.replace(temporary, path)

resources = ["lane\tpath\tsha256\n"]
fixtures = ["lane\tpath\tsha256\n"]
sensitive = ["lane\tprobe\tstatus\n"]
for lane, cell in lanes:
    for name, output in (("resource.env", resources), ("fixture.env", fixtures)):
        relative = f"lanes/{lane}/cells/{cell}/{name}"
        output.append(f"{lane}\t{relative}\t{sha(root / relative)}\n")
    source = root / f"lanes/{lane}/negative/sensitive-probes.tsv"
    with source.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames != ["probe", "status"]:
            raise SystemExit(f"sensitive negative header differs: {source}")
        rows = list(reader)
    if len(rows) != 6 or any(row.get("status") != "passed" for row in rows):
        raise SystemExit(f"sensitive negative rows differ: {source}")
    sensitive.extend(f"{lane}\t{row['probe']}\t{row['status']}\n" for row in rows)
aggregate = root / "aggregate"
negative = root / "negative"
aggregate.mkdir(exist_ok=True)
negative.mkdir(exist_ok=True)
atomic(aggregate / "resources.tsv", "".join(resources))
atomic(aggregate / "fixtures.tsv", "".join(fixtures))
atomic(negative / "sensitive-probes.tsv", "".join(sensitive))
PY
}

[[ "$#" -le 1 ]] || fail "usage: $SCRIPT_PATH [RUN_ID]"
[[ "$SIGNAL_PROBE_MODE" == false || "$SIGNAL_PROBE_MODE" == true ]] || \
  fail "V934_EXTERNAL_MATRIX_SIGNAL_PROBE must be true or false"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ && "$RUN_ID" != . && "$RUN_ID" != .. ]] || \
  fail "unsafe run id: $RUN_ID"

for command_name in cmp cut date docker flock git jq mkfifo mv python3 rg sed seq sha256sum sleep tee tr wc; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done
for required_file in \
  "$SCRIPT_PATH" "$CONTRACT" "$REPORT_TOOL" "$AUTHORITY_LIB" "$SHARED_CONTEXT" \
  "$LANE_LAUNCHER" \
  "$DEFERRED_INVENTORY" "$REDIS_RUNNER" "$MONGO_RUNNER" "$MYSQL_RUNNER" "$VECTOR_RUNNER"; do
  [[ -f "$required_file" ]] || fail "required file missing: $required_file"
done
for variable_name in MAVEN_ARGS MAVEN_CONFIG MAVEN_OPTS; do
  variable_value="${!variable_name:-}"
  if [[ "$variable_value" =~ (skipTests|skipITs|skipUnitTests|multi-db|model-lifecycle|query-cache-real-query|failIfNo[A-Za-z0-9._-]*) ]]; then
    fail "$variable_name contains a forbidden lane override"
  fi
done

# shellcheck source=scripts/v934/authority_runner_lib.sh
source "$AUTHORITY_LIB"
v934_acquire_authority_lock "$ROOT_DIR" "v934-external-matrix" || exit 1
export V934_AUTHORITY_LOCK_FD

RUN_ROOT="$RUNS_ROOT/$RUN_ID"
[[ ! -e "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
mkdir -p "$RUN_ROOT/lanes" "$RUN_ROOT/negative" "$RUN_ROOT/aggregate"
OUTER_MARKER="$RUN_ROOT/run-context.json"
RUN_LOG_FIFO="$RUN_ROOT/.run-log.fifo"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
GIT_HEAD="$(git -C "$ROOT_DIR" rev-parse HEAD)"
CONTRACT_SHA256="$(sha256_file "$CONTRACT")"
SUCCESSOR_MANIFEST_SHA256="$(sha256_file "$DEFERRED_INVENTORY")"
FINAL_REPORT_MANIFEST_SHA256=""
OUTER_MARKER_SHA256=""
SOURCE_BEFORE=""
SOURCE_AFTER=""
PHASE="bootstrap"

trap 'record_run_status "$?"' EXIT
trap 'handle_signal INT 130' INT
trap 'handle_signal TERM 143' TERM
trap 'handle_signal HUP 129' HUP

exec 3>&1 4>&2
mkfifo "$RUN_LOG_FIFO"
(
  trap '' INT TERM HUP
  exec tee -a "$RUN_ROOT/run.log"
) < "$RUN_LOG_FIFO" >&3 2>&4 &
RUN_LOG_TEE_PID=$!
RUN_LOG_OPEN=true
exec > "$RUN_LOG_FIFO" 2>&1
rm -f -- "$RUN_LOG_FIFO"

PHASE="contract-validate"
python3 "$REPORT_TOOL" validate
python3 "$REPORT_TOOL" check-worktree --lane external-matrix
SOURCE_BEFORE="$(python3 "$REPORT_TOOL" seal-source \
  --lane external-matrix --output "$RUN_ROOT/source-before.tsv" | sed -n 's/.*sha256=//p')"
[[ "$SOURCE_BEFORE" =~ ^[0-9a-f]{64}$ ]] || fail "matrix source-before seal is invalid"
atomic_env "$RUN_ROOT/preclean.env" \
  "lanes=external-redis,external-mongo,external-mysql,external-vector" \
  "mode=lane-owned-clean" \
  "status=passed"

PHASE="outer-marker"
write_outer_marker
OUTER_MARKER_SHA256="$(sha256_file "$OUTER_MARKER")"
python3 "$REPORT_TOOL" verify-outer --outer-marker "$OUTER_MARKER"

run_lane external-redis "$REDIS_RUNNER" V934_EXTERNAL_REDIS_SIGNAL_PROBE
run_lane external-mongo "$MONGO_RUNNER" V934_EXTERNAL_MONGO_SIGNAL_PROBE
run_lane external-mysql "$MYSQL_RUNNER" V934_EXTERNAL_MYSQL_SIGNAL_PROBE
run_lane external-vector "$VECTOR_RUNNER" V934_EXTERNAL_VECTOR_SIGNAL_PROBE

PHASE="finalize"
python3 "$REPORT_TOOL" finalize \
  --outer-marker "$OUTER_MARKER" \
  --manifest "$RUN_ROOT/lanes/external-redis/variants/redis7/evidence/report-manifest.json" \
  --manifest "$RUN_ROOT/lanes/external-redis/variants/redis7-sqlite/evidence/report-manifest.json" \
  --manifest "$RUN_ROOT/lanes/external-mongo/variants/mongo6/evidence/report-manifest.json" \
  --manifest "$RUN_ROOT/lanes/external-mysql/variants/mysql57-mcp/evidence/report-manifest.json" \
  --manifest "$RUN_ROOT/lanes/external-mysql/variants/mysql57-direct/evidence/report-manifest.json" \
  --manifest "$RUN_ROOT/lanes/external-mysql/variants/mysql57-compose/evidence/report-manifest.json" \
  --manifest "$RUN_ROOT/lanes/external-vector/variants/milvus24-embedding/evidence/report-manifest.json" \
  --output "$RUN_ROOT/final"
python3 "$REPORT_TOOL" verify-manifest \
  --outer-marker "$OUTER_MARKER" \
  --manifest "$RUN_ROOT/final/report-manifest.json"
FINAL_REPORT_MANIFEST_SHA256="$(sha256_file "$RUN_ROOT/final/report-manifest.json")"

PHASE="negative-probes"
python3 "$REPORT_TOOL" negative --output "$RUN_ROOT/negative/probes.tsv"
write_aggregates
cleanup_matrix_resources || fail "shared matrix resource cleanup failed"

PHASE="source-after"
SOURCE_AFTER="$(python3 "$REPORT_TOOL" seal-source \
  --lane external-matrix --output "$RUN_ROOT/source-after.tsv" | sed -n 's/.*sha256=//p')"
[[ "$SOURCE_BEFORE" == "$SOURCE_AFTER" ]] || fail "protected matrix source changed during execution"
cmp -s "$RUN_ROOT/source-before.tsv" "$RUN_ROOT/source-after.tsv" || \
  fail "protected matrix source manifest changed during execution"

PHASE="run-log-flush"
close_run_log || fail "run log tee did not flush successfully"

PHASE="sensitive-scan"
SENSITIVE_SCAN_ARGS=()
for pattern in "${SENSITIVE_PATTERNS[@]}"; do
  SENSITIVE_SCAN_ARGS+=(-e "$pattern")
done
if rg --pcre2 -l --hidden \
  --glob '!run-status.env' \
  --glob '!candidate-manifest.json' \
  --glob '!sensitive-scan.matches' \
  "${SENSITIVE_SCAN_ARGS[@]}" \
  "$RUN_ROOT" > "$RUN_ROOT/sensitive-scan.matches"; then
  fail "shared external evidence contains credentials"
else
  sensitive_scan_rc=$?
  [[ "$sensitive_scan_rc" -eq 1 ]] || \
    fail "sensitive evidence scan failed with rg exit code $sensitive_scan_rc"
fi
rm -f -- "$RUN_ROOT/sensitive-scan.matches"
atomic_env "$RUN_ROOT/sensitive-scan.env" \
  "lane_scans=4" \
  "status=passed"

PHASE="completed"
v934_write_run_status 0
RUN_STATUS_SHA256="$(sha256_file "$RUN_ROOT/run-status.env")"
NEGATIVE_COUNT="$(jq -r '.negative_probes | length' "$CONTRACT")"
atomic_env "$RUN_ROOT/summary.env" \
  "run_id=$RUN_ID" \
  "runner=failsafe" \
  "lane=$LANE" \
  "git_head=$GIT_HEAD" \
  "variants=7" \
  "reports=16" \
  "testcase_nodes=76" \
  "failures=0" \
  "errors=0" \
  "skipped=0" \
  "source_before=$SOURCE_BEFORE" \
  "source_after=$SOURCE_AFTER" \
  "outer_marker_sha256=$OUTER_MARKER_SHA256" \
  "contract_sha256=$CONTRACT_SHA256" \
  "final_report_manifest_sha256=$FINAL_REPORT_MANIFEST_SHA256" \
  "run_status_sha256=$RUN_STATUS_SHA256" \
  "resource_sha256=$(sha256_file "$RUN_ROOT/aggregate/resources.tsv")" \
  "fixture_sha256=$(sha256_file "$RUN_ROOT/aggregate/fixtures.tsv")" \
  "cleanup_sha256=$(sha256_file "$RUN_ROOT/aggregate/cleanup.env")" \
  "negative_probes=$NEGATIVE_COUNT/$NEGATIVE_COUNT" \
  "negative_sha256=$(sha256_file "$RUN_ROOT/negative/probes.tsv")" \
  "sensitive_negative_probes=24/24" \
  "sensitive_negative_sha256=$(sha256_file "$RUN_ROOT/negative/sensitive-probes.tsv")" \
  "sensitive_scan_sha256=$(sha256_file "$RUN_ROOT/sensitive-scan.env")" \
  "resource_residue=0/0/0" \
  "status=passed"

PHASE="candidate-manifest"
python3 "$REPORT_TOOL" create-candidate \
  --outer-marker "$OUTER_MARKER" \
  --run-root "$RUN_ROOT" \
  --output "$RUN_ROOT/candidate-manifest.json"
python3 "$REPORT_TOOL" verify-candidate \
  --candidate "$RUN_ROOT/candidate-manifest.json"
PHASE="completed"

v934_disarm_run_status_traps
echo "[v934-external-matrix] PASS run=$RUN_ID reports=16 testcase_nodes=76 F0/E0/S0 evidence=$RUN_ROOT"
