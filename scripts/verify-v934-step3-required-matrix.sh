#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SCRIPT_PATH="$ROOT_DIR/scripts/verify-v934-step3-required-matrix.sh"
STEP3_DIR="$ROOT_DIR/scripts/v934/step3"
STEP4_DIR="$ROOT_DIR/scripts/v934/step4"
CONTRACT="$STEP4_DIR/successor/step3-required-contract.json"
REPORT_TOOL="$STEP3_DIR/step3_required_report_tool.py"
AUTHORITY_LIB="$ROOT_DIR/scripts/v934/authority_runner_lib.sh"
STEP4_AUTHORITY_LIB="$STEP4_DIR/authority_parent_lib.sh"
STEP4_TOOL="$STEP4_DIR/coverage_tool.py"
OVERLAY_TOOL="$STEP4_DIR/successor/overlay_tool.py"
RUNS_ROOT="$ROOT_DIR/target/v934-step3-required-matrix/runs"

RUNNER_NAME="orchestrator"
LANE="step3-required-matrix"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$RUNS_ROOT/$RUN_ID"
OUTER_MARKER="$RUN_ROOT/run-context.json"
ADDON_ROOT="$ROOT_DIR/target/v934-step3-preagg-addon/runs/$RUN_ID"
DATABASE_ROOT="$ROOT_DIR/target/v934-step3-database-matrix/runs/$RUN_ID"
EXTERNAL_ROOT="$ROOT_DIR/target/v934-step3-external-matrix/runs/$RUN_ID"

ACTIVE_CHILD_PID=""
RUN_LOG_FIFO=""
RUN_LOG_TEE_PID=""
RUN_LOG_OPEN=false
PHASE="bootstrap"
SOURCE_BEFORE=""
SOURCE_AFTER=""
OUTER_MARKER_SHA256=""
SUCCESSOR_MANIFEST_SHA256=""
FINAL_REPORT_MANIFEST_SHA256=""

SENSITIVE_PATTERNS=(
  '(?i)(?:MYSQL_PWD|SQLCMDPASSWORD|REDIS_PASSWORD|REDIS_USERNAME|REDIS_URI|MONGO(?:DB)?_(?:URI|PASSWORD|USERNAME)|MYSQL_(?:PASSWORD|ROOT_PASSWORD)|MINIO_ROOT_(?:USER|PASSWORD)|AWS_(?:ACCESS_KEY_ID|SECRET_ACCESS_KEY))'
  '(?i)"?(?:password|passwd|pwd|credential|credentials|api[-_]?key|access[-_]?token|refresh[-_]?token|auth[-_]?token|secret|authorization)"?[[:space:]]*[:=][[:space:]]*(?!"?null"?(?:[[:space:],}\]]|$))"?[^"[:space:],}\]]+'
  '(?i)(?:authorization[[:space:]]*[:=][[:space:]]*)?bearer[[:space:]]+[A-Za-z0-9._~+/-]{8,}'
  '(?i)(?:redis|mongodb(?:\+srv)?|mysql|postgres(?:ql)?|sqlserver|s3)://[^/@:[:space:]]+:[^/@[:space:]]+@'
  '(?i)(?:--password|--passwd|--pwd)(?:=|[[:space:]])[^[:space:]]+'
)

fail() {
  echo "[v934-step3-required] ERROR: $*" >&2
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

close_run_log() {
  local attempt code=0
  if [[ "$RUN_LOG_OPEN" == true ]]; then
    exec 1>&3 2>&4
    RUN_LOG_OPEN=false
    exec 3>&- 4>&-
  fi
  if [[ -n "$RUN_LOG_TEE_PID" ]]; then
    for ((attempt = 0; attempt < 50; attempt++)); do
      kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || break
      sleep 0.1
    done
    if kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1; then
      code=124
      kill -TERM "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
      for ((attempt = 0; attempt < 10; attempt++)); do
        kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || break
        sleep 0.1
      done
      kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1 && \
        kill -KILL "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
      wait "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
    elif ! wait "$RUN_LOG_TEE_PID"; then
      code=1
    fi
    RUN_LOG_TEE_PID=""
  fi
  [[ -z "$RUN_LOG_FIFO" ]] || rm -f -- "$RUN_LOG_FIFO"
  return "$code"
}

terminate_active_child() {
  local signal_name="$1" attempt
  [[ -n "$ACTIVE_CHILD_PID" ]] || return 0
  if kill -0 "$ACTIVE_CHILD_PID" >/dev/null 2>&1; then
    kill -s "$signal_name" -- "-$ACTIVE_CHILD_PID" >/dev/null 2>&1 || true
    for ((attempt = 0; attempt < 200; attempt++)); do
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

remove_labeled_resources() {
  local label="$1" identifiers identifier code=0
  identifiers="$(docker ps -aq --filter "label=$label")" || code=1
  for identifier in $identifiers; do docker rm -fv "$identifier" >/dev/null 2>&1 || code=1; done
  identifiers="$(docker volume ls -q --filter "label=$label")" || code=1
  for identifier in $identifiers; do docker volume rm -f "$identifier" >/dev/null 2>&1 || code=1; done
  identifiers="$(docker network ls -q --filter "label=$label")" || code=1
  for identifier in $identifiers; do docker network rm "$identifier" >/dev/null 2>&1 || code=1; done
  return "$code"
}

count_labeled_resources() {
  local label="$1" kind="$2"
  case "$kind" in
    container) docker ps -aq --filter "label=$label" ;;
    volume) docker volume ls -q --filter "label=$label" ;;
    network) docker network ls -q --filter "label=$label" ;;
    *) return 1 ;;
  esac | sed '/^$/d' | wc -l | tr -d ' '
}

cleanup_all_resources() {
  local code=0 database scope_hash project label
  local containers=0 volumes=0 networks=0 count
  docker info >/dev/null 2>&1 || return 1
  remove_labeled_resources "com.foggy.v934.external-run=$RUN_ID" || code=1
  remove_labeled_resources "com.foggy.v934.preagg-run=$RUN_ID" || code=1
  for database in mysql57 mysql8 postgres15 sqlserver2022; do
    scope_hash="$(printf '%s\n' "$RUN_ID|$database" | sha256sum | cut -c1-12)"
    project="v934db-${database}-${scope_hash}"
    remove_labeled_resources "com.docker.compose.project=$project" || code=1
  done
  for label in \
    "com.foggy.v934.external-run=$RUN_ID" \
    "com.foggy.v934.preagg-run=$RUN_ID"; do
    count="$(count_labeled_resources "$label" container)" || code=1
    containers=$((containers + ${count:-999}))
    count="$(count_labeled_resources "$label" volume)" || code=1
    volumes=$((volumes + ${count:-999}))
    count="$(count_labeled_resources "$label" network)" || code=1
    networks=$((networks + ${count:-999}))
  done
  for database in mysql57 mysql8 postgres15 sqlserver2022; do
    scope_hash="$(printf '%s\n' "$RUN_ID|$database" | sha256sum | cut -c1-12)"
    project="v934db-${database}-${scope_hash}"
    label="com.docker.compose.project=$project"
    count="$(count_labeled_resources "$label" container)" || code=1
    containers=$((containers + ${count:-999}))
    count="$(count_labeled_resources "$label" volume)" || code=1
    volumes=$((volumes + ${count:-999}))
    count="$(count_labeled_resources "$label" network)" || code=1
    networks=$((networks + ${count:-999}))
  done
  [[ "$containers" -eq 0 && "$volumes" -eq 0 && "$networks" -eq 0 ]] || code=1
  mkdir -p "$RUN_ROOT"
  atomic_env "$RUN_ROOT/cleanup.env" \
    "container_residue=$containers" \
    "volume_residue=$volumes" \
    "network_residue=$networks" \
    "status=$([[ "$code" -eq 0 ]] && printf passed || printf failed)" || code=1
  return "$code"
}

record_run_status() {
  local exit_code="$1" finalizer_code=0
  trap '' INT TERM HUP
  trap - EXIT
  set +e
  terminate_active_child TERM
  cleanup_all_resources || finalizer_code=1
  close_run_log || finalizer_code=1
  [[ "$finalizer_code" -eq 0 ]] || exit_code=1
  [[ "$exit_code" -ne 0 || "$PHASE" == completed ]] || exit_code=1
  if [[ "$exit_code" -ne 0 ]]; then
    rm -f -- "$RUN_ROOT/summary.env" "$RUN_ROOT/candidate-manifest.json"
    rm -rf -- "$RUN_ROOT/final"
  fi
  v934_write_run_status "$exit_code" || exit_code=1
  exit "$exit_code"
}

handle_signal() {
  local signal_name="$1" exit_code="$2"
  trap '' INT TERM HUP
  terminate_active_child "$signal_name"
  exit "$exit_code"
}

run_child() {
  local child="$1" exit_code=0
  PHASE="child-$child"
  echo "[v934-step3-required] running child=$child"
  python3 "$REPORT_TOOL" --repo-root "$ROOT_DIR" --contract "$CONTRACT" \
    launch-child --child "$child" --run-id "$RUN_ID" \
    --lock-fd "$V934_AUTHORITY_LOCK_FD" &
  ACTIVE_CHILD_PID=$!
  if wait "$ACTIVE_CHILD_PID"; then exit_code=0; else exit_code=$?; fi
  ACTIVE_CHILD_PID=""
  [[ "$exit_code" -eq 0 ]] || return "$exit_code"
}

[[ "$#" -le 1 ]] || fail "usage: $SCRIPT_PATH [RUN_ID]"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ && "$RUN_ID" != . && "$RUN_ID" != .. ]] || \
  fail "unsafe run id: $RUN_ID"
for command_name in cmp cut date docker flock git jq mkfifo mv python3 readlink rg sed sha256sum sleep tee tr wc; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done
for required_file in \
  "$SCRIPT_PATH" "$CONTRACT" "$REPORT_TOOL" "$AUTHORITY_LIB" \
  "$STEP4_AUTHORITY_LIB" "$STEP4_TOOL" "$OVERLAY_TOOL"; do
  [[ -f "$required_file" && ! -L "$required_file" ]] || fail "required file missing/symlink: $required_file"
done

# shellcheck source=scripts/v934/authority_runner_lib.sh
source "$AUTHORITY_LIB"
# shellcheck source=scripts/v934/step4/authority_parent_lib.sh
source "$STEP4_AUTHORITY_LIB"
STEP4_PARENT_SOURCE=""
if [[ "${V934_AUTHORITY_LOCK_MODE:-standalone}" == inherited ]]; then
  STEP4_PARENT_SOURCE="$(python3 "$STEP4_TOOL" source-hash --repo-root "$ROOT_DIR" | \
    python3 -c 'import json,sys; print(json.load(sys.stdin)["sha256"])')"
  v934_step4_validate_inherited_authority \
    "$ROOT_DIR" "v934-step3-required" "$STEP4_PARENT_SOURCE" || exit 1
elif [[ "${V934_AUTHORITY_LOCK_MODE:-standalone}" == standalone ]]; then
  v934_acquire_or_validate_authority_lock "$ROOT_DIR" "v934-step3-required" || exit 1
else
  fail "unsupported authority mode: ${V934_AUTHORITY_LOCK_MODE:-}"
fi

[[ ! -e "$RUN_ROOT" && ! -L "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
mkdir -p "$RUN_ROOT/negative"
if [[ -n "$STEP4_PARENT_SOURCE" ]]; then
  atomic_env "$RUN_ROOT/step4-parent-context.env" \
    "authority_kind=$V934_PARENT_AUTHORITY_KIND" \
    "run_id=$V934_PARENT_RUN_ID" \
    "git_head=$V934_PARENT_GIT_HEAD" \
    "contract_sha256=$V934_PARENT_CONTRACT_SHA256" \
    "source_sha256=$V934_PARENT_SOURCE_SHA256" \
    "outer_marker_sha256=$V934_PARENT_OUTER_MARKER_SHA256" \
    "outer_marker_path=$V934_PARENT_OUTER_MARKER_PATH" \
    "status=validated"
fi
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
GIT_HEAD="$(git -C "$ROOT_DIR" rev-parse HEAD)"
CONTRACT_SHA256="$(sha256_file "$CONTRACT")"
SUCCESSOR_MANIFEST_SHA256="$(sha256_file "$ROOT_DIR/scripts/v934/successor/step2/contract-freeze.json")"

trap 'record_run_status "$?"' EXIT
trap 'handle_signal INT 130' INT
trap 'handle_signal TERM 143' TERM
trap 'handle_signal HUP 129' HUP

exec 3>&1 4>&2
RUN_LOG_FIFO="$RUN_ROOT/.run-log.fifo"
mkfifo "$RUN_LOG_FIFO"
(trap '' INT TERM HUP; exec tee -a "$RUN_ROOT/run.log") < "$RUN_LOG_FIFO" >&3 2>&4 &
RUN_LOG_TEE_PID=$!
RUN_LOG_OPEN=true
exec > "$RUN_LOG_FIFO" 2>&1
rm -f -- "$RUN_LOG_FIFO"

PHASE="contract-validate"
python3 "$OVERLAY_TOOL" validate
python3 "$REPORT_TOOL" --repo-root "$ROOT_DIR" --contract "$CONTRACT" validate

PHASE="source-before"
SOURCE_BEFORE="$(python3 "$REPORT_TOOL" --repo-root "$ROOT_DIR" --contract "$CONTRACT" \
  seal-source --output "$RUN_ROOT/source-before.tsv" | sed -n 's/.*sha256=//p')"
[[ "$SOURCE_BEFORE" =~ ^[0-9a-f]{64}$ ]] || fail "source-before digest is invalid"

PHASE="outer-marker"
python3 "$REPORT_TOOL" --repo-root "$ROOT_DIR" --contract "$CONTRACT" create-outer \
  --run-id "$RUN_ID" --git-head "$GIT_HEAD" --source-sha256 "$SOURCE_BEFORE" \
  --started-at "$STARTED_AT" --output "$OUTER_MARKER"
OUTER_MARKER_SHA256="$(sha256_file "$OUTER_MARKER")"
export V934_PARENT_AUTHORITY_KIND=step3-required-matrix
export V934_PARENT_RUN_ID="$RUN_ID"
export V934_PARENT_GIT_HEAD="$GIT_HEAD"
export V934_PARENT_CONTRACT_SHA256="$CONTRACT_SHA256"
export V934_PARENT_SOURCE_SHA256="$SOURCE_BEFORE"
export V934_PARENT_OUTER_MARKER_SHA256="$OUTER_MARKER_SHA256"
export V934_PARENT_OUTER_MARKER_PATH="$OUTER_MARKER"

run_child addon-companion
run_child database-matrix
run_child external-matrix

PHASE="source-after"
SOURCE_AFTER="$(python3 "$REPORT_TOOL" --repo-root "$ROOT_DIR" --contract "$CONTRACT" \
  seal-source --output "$RUN_ROOT/source-after.tsv" | sed -n 's/.*sha256=//p')"
[[ "$SOURCE_AFTER" == "$SOURCE_BEFORE" ]] || fail "protected source changed during child execution"
cmp -s "$RUN_ROOT/source-before.tsv" "$RUN_ROOT/source-after.tsv" || \
  fail "protected source inventory changed during child execution"

PHASE="negative-probes"
python3 "$REPORT_TOOL" --repo-root "$ROOT_DIR" --contract "$CONTRACT" \
  negative --output "$RUN_ROOT/negative/probes.tsv"

PHASE="resource-cleanup"
cleanup_all_resources || fail "run-owned resource cleanup or residue check failed"

PHASE="run-log-flush"
close_run_log || fail "run log did not flush"

PHASE="sensitive-scan"
SENSITIVE_SCAN_ARGS=()
for pattern in "${SENSITIVE_PATTERNS[@]}"; do SENSITIVE_SCAN_ARGS+=(-e "$pattern"); done
if rg --pcre2 -l --hidden \
  --glob '!candidate-manifest.json' --glob '!run-status.env' \
  --glob '!sensitive-scan.matches' "${SENSITIVE_SCAN_ARGS[@]}" \
  "$RUN_ROOT" "$ADDON_ROOT" "$DATABASE_ROOT" "$EXTERNAL_ROOT" \
  > "$RUN_ROOT/sensitive-scan.matches"; then
  fail "Step 3 required evidence contains credentials"
else
  sensitive_scan_rc=$?
  [[ "$sensitive_scan_rc" -eq 1 ]] || fail "sensitive scan failed: rc=$sensitive_scan_rc"
fi
rm -f -- "$RUN_ROOT/sensitive-scan.matches"
atomic_env "$RUN_ROOT/sensitive-scan.env" "roots=4" "patterns=${#SENSITIVE_PATTERNS[@]}" "status=passed"

PHASE="finalize"
python3 "$REPORT_TOOL" --repo-root "$ROOT_DIR" --contract "$CONTRACT" finalize \
  --outer-marker "$OUTER_MARKER" --database-root "$DATABASE_ROOT" \
  --external-root "$EXTERNAL_ROOT" --addon-root "$ADDON_ROOT" \
  --source-before "$RUN_ROOT/source-before.tsv" --source-after "$RUN_ROOT/source-after.tsv" \
  --output "$RUN_ROOT/final"
python3 "$REPORT_TOOL" --repo-root "$ROOT_DIR" --contract "$CONTRACT" verify-final \
  --outer-marker "$OUTER_MARKER" --manifest "$RUN_ROOT/final/report-manifest.json"
FINAL_REPORT_MANIFEST_SHA256="$(sha256_file "$RUN_ROOT/final/report-manifest.json")"

PHASE="completed"
v934_write_run_status 0
atomic_env "$RUN_ROOT/summary.env" \
  "run_id=$RUN_ID" "runner=$RUNNER_NAME" "lane=$LANE" "git_head=$GIT_HEAD" \
  "contract_sha256=$CONTRACT_SHA256" "source_before=$SOURCE_BEFORE" \
  "source_after=$SOURCE_AFTER" "outer_marker_sha256=$OUTER_MARKER_SHA256" \
  "final_report_manifest_sha256=$FINAL_REPORT_MANIFEST_SHA256" \
  "database_variants=7" "external_variants=7" "execution_keys=45" \
  "reports=45" "testcase_nodes=446" "failures=0" "errors=0" "skipped=0" \
  "database_keys=29" "external_keys=16" "overlap_keys=0" "gap_keys=0" "extra_keys=0" \
  "addon_variants=2" "addon_reports=2" "addon_testcase_nodes=6" \
  "optional_llm=reviewed-optional-excluded" "negative_probes=17/17" \
  "resource_residue=0/0/0" "status=passed"

PHASE="candidate-manifest"
python3 "$REPORT_TOOL" --repo-root "$ROOT_DIR" --contract "$CONTRACT" create-candidate \
  --outer-marker "$OUTER_MARKER" --run-root "$RUN_ROOT" \
  --output "$RUN_ROOT/candidate-manifest.json"
python3 "$REPORT_TOOL" --repo-root "$ROOT_DIR" --contract "$CONTRACT" verify-candidate \
  --candidate "$RUN_ROOT/candidate-manifest.json"
PHASE="completed"
v934_disarm_run_status_traps
echo "[v934-step3-required] PASS run=$RUN_ID required=45/446 F0/E0/S0 addon=2/6"
