#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/verify-v933-batch6-exit.sh [RUN_ID]

Run the complete 9.3.3 Batch 6 exit replay. The runner executes eleven child
gates strictly in order, verifies every child manifest twice, and writes a new
target/v933-batch6-exit/runs directory. Failed runs are retained as diagnostic
evidence and never update latest-run-id.
USAGE
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
EXIT_TARGET="$ROOT_DIR/target/v933-batch6-exit"
RUN_ROOT="$EXIT_TARGET/runs/$RUN_ID"
LOCK_FILE="$EXIT_TARGET/.aggregate.lock"
LATEST_RUN_ID="$EXIT_TARGET/latest-run-id"
LATEST_TMP="$EXIT_TARGET/.latest-run-id.$RUN_ID.tmp"

CURRENT_STEP="preflight"
LAST_COMPLETED_STEP="none"
FAIL_REASON="unexpected-command-failure"
AFTER_CAPTURED=0
SUCCESS_FINALIZED=0
STARTED_AT=""

fail() {
  FAIL_REASON="$*"
  echo "[v933-batch6-exit] ERROR: $*" >&2
  exit 1
}

[[ "$#" -le 1 ]] || {
  usage >&2
  exit 2
}
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
[[ "${#RUN_ID}" -le 80 ]] || fail "run id is longer than 80 characters"

mkdir -p "$EXIT_TARGET/runs"
exec 9>"$LOCK_FILE"
flock -n 9 || fail "another Batch 6 aggregate replay holds $LOCK_FILE"
[[ ! -e "$RUN_ROOT" ]] || fail "run directory already exists: $RUN_ROOT"
mkdir -p \
  "$RUN_ROOT/child-logs" \
  "$RUN_ROOT/child-records" \
  "$RUN_ROOT/environment" \
  "$RUN_ROOT/integrity" \
  "$RUN_ROOT/source-audit"
: > "$RUN_ROOT/.run-start"

CHILDREN_TSV="$RUN_ROOT/children.tsv"
printf '%s\n' \
  $'ordinal\tgate\tscript\tchild_run_id\tchild_run_root\tprocess_exit\tsemantic_status\tsummary_path\tsummary_sha256\tmanifest_path\tmanifest_sha256\tmanifest_verified\tcriteria_tests\tasserted_reports\tfailures\terrors\tskipped\texpected_negative\tremaining_red' \
  > "$CHILDREN_TSV"

declare -a CHILD_ROOTS=()
declare -a CHILD_MANIFESTS=()
declare -a CHILD_SUMMARIES=()
declare -a CHILD_SUMMARY_HASHES=()
declare -a CHILD_MANIFEST_HASHES=()
declare -a CHILD_RECORD_DIRS=()

CHILD_COUNT=0
TOTAL_CRITERIA_TESTS=0
TOTAL_ASSERTED_TESTCASES=0
TOTAL_ASSERTED_REPORTS=0
TOTAL_FAILURES=0
TOTAL_ERRORS=0
TOTAL_SKIPPED=0
TOTAL_EXPECTED_NEGATIVE=0
TOTAL_REMAINING_RED=0

CATALOG_AUTHORITY_STATUS="pending"
CACHE_IDENTITY_STATUS="pending"
CACHE_CROSS_JVM_STATUS="pending"
CACHE_GEN_STATUS="pending"
REAL_QUERY_STATUS="pending"

CATALOG_RUN_ID=""
CACHE_IDENTITY_RUN_ID=""
CACHE_CROSS_JVM_RUN_ID=""
PIVOT_RUN_ID=""
REAL_QUERY_RUN_ID=""
BATCH5_RUN_ID=""
BATCH4_RUN_ID=""
BATCH3_RUN_ID=""
ENTRY_RUN_ID=""
BATCH2_RUN_ID=""
REMAINING_RED_RUN_ID=""

capture_containers() {
  local output_file="$1"
  local name details container_names
  docker info >/dev/null 2>&1 || return 1
  container_names="$(docker ps -a --format '{{.Names}}')" || return 1
  printf '%s\n' 'name|presence|container_id|configured_image|image_id|state|health' \
    > "$output_file"
  for name in foggy-demo-mysql foggy-demo-postgres foggy-demo-redis; do
    if grep -Fxq -- "$name" <<< "$container_names"; then
      details="$(docker inspect --format \
        '{{.Id}}|{{.Config.Image}}|{{.Image}}|{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
        "$name")" || return 1
      printf '%s|present|%s\n' "$name" "$details" >> "$output_file"
    else
      printf '%s|absent|||||\n' "$name" >> "$output_file"
    fi
  done
}

capture_run_owned_containers() {
  local output_file="$1"
  local container_names
  docker info >/dev/null 2>&1 || return 1
  container_names="$(docker ps -a --format '{{.Names}}')" || return 1
  printf '%s\n' "$container_names" \
    | LC_ALL=C sort \
    | awk -v needle="$RUN_ID-" 'index($0, needle) > 0' \
    > "$output_file"
}

capture_tracked_diff_digest() {
  local output_file="$1"
  local digest
  digest="$(git -C "$ROOT_DIR" diff --binary --no-ext-diff HEAD \
    | sha256sum | awk '{print $1}')" || return 1
  printf '%s\n' "$digest" > "$output_file"
}

capture_untracked_digests() {
  local phase="$1"
  local list_file="$RUN_ROOT/source-audit/.untracked-$phase.list0"
  local list_digest_file="$RUN_ROOT/source-audit/untracked-list-$phase.sha256"
  local content_digest_file="$RUN_ROOT/source-audit/untracked-content-$phase.sha256"
  local path file_hash

  git -C "$ROOT_DIR" ls-files --others --exclude-standard -z \
    | LC_ALL=C sort -z > "$list_file" || return 1
  sha256sum "$list_file" | awk '{print $1}' > "$list_digest_file" || return 1
  (
    cd "$ROOT_DIR" || exit 1
    while IFS= read -r -d '' path; do
      printf '%s\0' "$path"
      if [[ -L "$path" ]]; then
        printf 'symlink\0'
        readlink -n -- "$path" || exit 1
        printf '\0'
      elif [[ -f "$path" ]]; then
        file_hash="$(sha256sum -- "$path" | awk '{print $1}')" || exit 1
        printf 'file\0%s\0' "$file_hash"
      else
        printf 'missing\0'
      fi
    done < "$list_file"
  ) | sha256sum | awk '{print $1}' > "$content_digest_file" || return 1
  rm -f "$list_file"
}

capture_after_state() {
  [[ "$AFTER_CAPTURED" -eq 0 ]] || return 0
  git -C "$ROOT_DIR" rev-parse HEAD \
    > "$RUN_ROOT/source-audit/git-head-after.txt"
  git -C "$ROOT_DIR" status --short --untracked-files=all \
    > "$RUN_ROOT/source-audit/git-status-after.txt"
  capture_tracked_diff_digest \
    "$RUN_ROOT/source-audit/git-diff-after.sha256" || return 1
  capture_untracked_digests after || return 1
  capture_containers "$RUN_ROOT/environment/containers-after.tsv" || return 1
  capture_run_owned_containers \
    "$RUN_ROOT/environment/run-owned-containers-after.txt" || return 1
  diff -u \
    "$RUN_ROOT/environment/containers-before.tsv" \
    "$RUN_ROOT/environment/containers-after.tsv" \
    > "$RUN_ROOT/environment/container-delta.diff" || true
  AFTER_CAPTURED=1
}

on_exit() {
  local status="$?"
  set +e
  rm -f "$LATEST_TMP"
  if [[ -d "$RUN_ROOT" ]]; then
    capture_after_state >/dev/null 2>&1 || true
  fi
  if [[ "$status" -ne 0 || "$SUCCESS_FINALIZED" -ne 1 ]]; then
    if [[ -d "$RUN_ROOT" ]]; then
      printf '%s\n' "$FAIL_REASON" > "$RUN_ROOT/failure-reason.txt"
      cat > "$RUN_ROOT/failure.env" <<FAILURE
run_id=$RUN_ID
status=failed
exit_status=$status
current_step=$CURRENT_STEP
last_completed_step=$LAST_COMPLETED_STEP
failure_reason_file=failure-reason.txt
latest_run_id_updated=false
FAILURE
      if [[ -f "$RUN_ROOT/source-audit/source-files.sha256" ]]; then
        (cd "$ROOT_DIR" && \
          sha256sum -c "$RUN_ROOT/source-audit/source-files.sha256") \
          > "$RUN_ROOT/source-audit/source-hash-check-on-failure.txt" 2>&1 || true
      fi
    fi
    echo "[v933-batch6-exit] FAILED run=$RUN_ID step=$CURRENT_STEP evidence=$RUN_ROOT" >&2
    if [[ "$status" -eq 0 ]]; then
      exit 1
    fi
  else
    echo "[v933-batch6-exit] PASS run=$RUN_ID evidence=$RUN_ROOT"
  fi
}
trap on_exit EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

required_commands=(
  awk basename bash cat cmp comm cp cut date diff docker find flock git grep java mkdir
  mv mvn pgrep readlink rg rm sed sha256sum sort tee wc xargs
)
for command_name in "${required_commands[@]}"; do
  command -v "$command_name" >/dev/null 2>&1 || \
    fail "required command is missing: $command_name"
done
docker info >/dev/null 2>&1 || fail "Docker daemon is unavailable"
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is required"

EXTERNAL_MAVEN_FLAGS="${MAVEN_ARGS:-} ${MAVEN_OPTS:-} ${JAVA_TOOL_OPTIONS:-}"
if [[ "$EXTERNAL_MAVEN_FLAGS" =~ (^|[[:space:]])-D(skipTests|maven\.test\.skip|skipITs|skipUnitTests)($|=|[[:space:]]) ]]; then
  fail "external Maven test-skip properties are forbidden for Batch 6 exit"
fi
if [[ "$EXTERNAL_MAVEN_FLAGS" =~ (^|[[:space:]])-P[^[:space:]]*multi-db ]]; then
  fail "external multi-db profile activation is forbidden for Batch 6 exit"
fi

CATALOG_SCRIPT="$ROOT_DIR/scripts/verify-v933-batch6-catalog-authority.sh"
CACHE_IDENTITY_SCRIPT="$ROOT_DIR/scripts/verify-v933-batch6-cache-identity.sh"
CACHE_CROSS_JVM_SCRIPT="$ROOT_DIR/scripts/verify-v933-batch6-cache-cross-jvm.sh"
PIVOT_SCRIPT="$ROOT_DIR/scripts/verify-v933-batch6-pivot-identity.sh"
REAL_QUERY_SCRIPT="$ROOT_DIR/scripts/verify-v933-batch6-real-query.sh"
BATCH5_SCRIPT="$ROOT_DIR/scripts/verify-v933-batch5-refresh.sh"
BATCH4_SCRIPT="$ROOT_DIR/scripts/verify-v933-batch4-single-flight.sh"
BATCH3_SCRIPT="$ROOT_DIR/scripts/verify-v933-batch3-catalog-binding.sh"
ENTRY_SCRIPT="$ROOT_DIR/scripts/verify-v933-entry-gate.sh"
BATCH2_SCRIPT="$ROOT_DIR/scripts/verify-v933-batch2-namespace.sh"
REMAINING_RED_SCRIPT="$ROOT_DIR/scripts/verify-v933-batch1-red-baselines.sh"

CHILD_SCRIPTS=(
  "$CATALOG_SCRIPT"
  "$CACHE_IDENTITY_SCRIPT"
  "$CACHE_CROSS_JVM_SCRIPT"
  "$PIVOT_SCRIPT"
  "$REAL_QUERY_SCRIPT"
  "$BATCH5_SCRIPT"
  "$BATCH4_SCRIPT"
  "$BATCH3_SCRIPT"
  "$ENTRY_SCRIPT"
  "$BATCH2_SCRIPT"
  "$REMAINING_RED_SCRIPT"
)
for child_script in "${CHILD_SCRIPTS[@]}"; do
  [[ -x "$child_script" ]] || fail "child runner is missing or not executable: $child_script"
done

assert_no_repository_maven() {
  local pid cwd
  while IFS= read -r pid; do
    [[ -n "$pid" && "$pid" != "$$" ]] || continue
    cwd="$(readlink -f "/proc/$pid/cwd" 2>/dev/null || true)"
    if [[ "$cwd" == "$ROOT_DIR" || "$cwd" == "$ROOT_DIR/"* ]]; then
      fail "another Maven/Surefire process is active in this repository: pid=$pid cwd=$cwd"
    fi
  done < <(pgrep -f \
    '[o]rg\.codehaus\.plexus\.classworlds\.launcher\.Launcher|[s]urefirebooter' || true)
}

assert_no_repository_maven

SOURCE_LIST="$RUN_ROOT/source-audit/source-files.list0"
SOURCE_MANIFEST="$RUN_ROOT/source-audit/source-files.sha256"
SOURCE_ROOTS=(
  foggy-dataset-model-engine/src
  foggy-runtime-api/src
  foggy-fsscript/src
  foggy-dataset-mcp/src
  addons/foggy-dataset-model-cache/src
)
for source_root in "${SOURCE_ROOTS[@]}"; do
  [[ -d "$ROOT_DIR/$source_root" ]] || fail "source root is missing: $source_root"
done
(
  cd "$ROOT_DIR"
  {
    find scripts -maxdepth 1 -type f -name '*.sh' -print0
    find "${SOURCE_ROOTS[@]}" -type f -print0
    find . -type d -name target -prune -o -type f -name pom.xml -print0
    find docs/9.3.3 -type f -print0
    printf '%s\0' docs/9.3.1/roadmap-9.3.1-to-9.4.0.md
    printf '%s\0' foggy-dataset-demo/docker/docker-compose.yml
  } | LC_ALL=C sort -zu > "$SOURCE_LIST"
)
[[ -s "$SOURCE_LIST" ]] || fail "source inventory is empty"
(cd "$ROOT_DIR" && xargs -0 sha256sum < "$SOURCE_LIST") > "$SOURCE_MANIFEST"

git -C "$ROOT_DIR" rev-parse HEAD > "$RUN_ROOT/source-audit/git-head-before.txt"
git -C "$ROOT_DIR" status --short --untracked-files=all \
  > "$RUN_ROOT/source-audit/git-status-before.txt"
capture_tracked_diff_digest \
  "$RUN_ROOT/source-audit/git-diff-before.sha256" || \
  fail "could not capture tracked diff digest"
capture_untracked_digests before || fail "could not capture untracked digests"

{
  printf 'captured_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  java -version 2>&1
  mvn -version 2>&1
  docker version 2>&1
  docker compose version 2>&1
} > "$RUN_ROOT/environment/tool-versions.txt"
capture_containers "$RUN_ROOT/environment/containers-before.tsv" || \
  fail "could not capture fixed container state"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
capture_run_owned_containers \
  "$RUN_ROOT/environment/run-owned-containers-before.txt" || \
  fail "could not capture pre-run container names"
[[ ! -s "$RUN_ROOT/environment/run-owned-containers-before.txt" ]] || \
  fail "a pre-existing container contains the fresh aggregate run id"
EXPECTED_RUN_CONTAINERS=(
  "v933-cache-cross-jvm-$RUN_ID-03-cache-cross-jvm"
  "v933-real-query-redis-$RUN_ID-05-real-query"
)
printf '%s\n' "${EXPECTED_RUN_CONTAINERS[@]}" \
  > "$RUN_ROOT/environment/expected-run-owned-containers.txt"

env_value() {
  local file="$1"
  local key="$2"
  awk -v key="$key" '
    index($0, key "=") == 1 {
      if (found) {
        duplicate = 1
        next
      }
      print substr($0, length(key) + 2)
      found = 1
    }
    END {
      if (duplicate) exit 3
      if (!found) exit 2
    }
  ' "$file"
}

expect_env() {
  local file="$1"
  local key="$2"
  local expected="$3"
  local actual
  if ! actual="$(env_value "$file" "$key")"; then
    fail "missing or duplicate summary key '$key' in $file"
  fi
  [[ "$actual" == "$expected" ]] || \
    fail "summary key $key=$actual, expected=$expected in $file"
}

expect_env_integer_at_least() {
  local file="$1"
  local key="$2"
  local minimum="$3"
  local actual
  if ! actual="$(env_value "$file" "$key")"; then
    fail "missing or duplicate summary key '$key' in $file"
  fi
  [[ "$actual" =~ ^[0-9]+$ && "$actual" -ge "$minimum" ]] || \
    fail "summary key $key=$actual, expected integer >= $minimum in $file"
}

expect_env_contains() {
  local file="$1"
  local key="$2"
  local expected_fragment="$3"
  local actual
  if ! actual="$(env_value "$file" "$key")"; then
    fail "missing or duplicate summary key '$key' in $file"
  fi
  [[ "$actual" == *"$expected_fragment"* ]] || \
    fail "summary key $key does not contain '$expected_fragment' in $file"
}

assert_xml_report_metrics() {
  local gate="$1"
  local root="$2"
  local mode="$3"
  local expected_reports="$4"
  local expected_tests="$5"
  local list_file="$RUN_ROOT/integrity/$gate-reports.list0"
  local metrics_file="$RUN_ROOT/integrity/$gate-report-metrics.tsv"
  local report suite_tag tests failures errors skipped relative_path
  local total_tests=0
  local -a reports=()

  case "$mode" in
    copied)
      find "$root" -type f \
        \( -path '*/surefire-reports/TEST-*.xml' \
           -o -path '*/failsafe-reports/TEST-*.xml' \) \
        -print0 | LC_ALL=C sort -z > "$list_file"
      ;;
    asserted)
      find "$root" -type f -path '*/asserted/*/TEST-*.xml' \
        -print0 | LC_ALL=C sort -z > "$list_file"
      ;;
    entry-positive)
      printf '%s\0' \
        "$root/probe-pair/surefire-reports/TEST-com.foggyframework.dataset.model.lifecycle.gate.DeterministicConcurrencyHarnessProbeTest.xml" \
        "$root/probe-pair/failsafe-reports/TEST-com.foggyframework.dataset.model.lifecycle.gate.DeterministicConcurrencyHarnessProbeIT.xml" \
        "$root/sqlite-preflight/failsafe-reports/TEST-com.foggyframework.dataset.model.lifecycle.gate.RequiredDatabasePreflightIT.xml" \
        "$root/mysql57-preflight/failsafe-reports/TEST-com.foggyframework.dataset.model.lifecycle.gate.RequiredDatabasePreflightIT.xml" \
        "$root/postgres15-preflight/failsafe-reports/TEST-com.foggyframework.dataset.model.lifecycle.gate.RequiredDatabasePreflightIT.xml" \
        > "$list_file"
      ;;
    *)
      fail "unknown report metric mode: $mode"
      ;;
  esac

  mapfile -d '' reports < "$list_file"
  [[ "${#reports[@]}" -eq "$expected_reports" ]] || \
    fail "$gate asserted reports=${#reports[@]}, expected=$expected_reports"
  printf '%s\n' $'report\ttests\tfailures\terrors\tskipped' > "$metrics_file"
  for report in "${reports[@]}"; do
    [[ -f "$report" ]] || fail "$gate asserted report is missing: $report"
    suite_tag="$(grep -o -m1 '<testsuite[^>]*>' "$report" || true)"
    [[ -n "$suite_tag" ]] || fail "$gate testsuite tag is missing: $report"
    tests="$(sed -n 's/.* tests="\([0-9][0-9]*\)".*/\1/p' <<< "$suite_tag")"
    failures="$(sed -n 's/.* failures="\([0-9][0-9]*\)".*/\1/p' <<< "$suite_tag")"
    errors="$(sed -n 's/.* errors="\([0-9][0-9]*\)".*/\1/p' <<< "$suite_tag")"
    skipped="$(sed -n 's/.* skipped="\([0-9][0-9]*\)".*/\1/p' <<< "$suite_tag")"
    [[ "$tests" =~ ^[0-9]+$ && "$failures" == 0 && "$errors" == 0 && "$skipped" == 0 ]] || \
      fail "$gate report has invalid/non-green metrics: $report"
    total_tests=$((total_tests + tests))
    relative_path="${report#$root/}"
    printf '%s\t%s\t0\t0\t0\n' "$relative_path" "$tests" >> "$metrics_file"
  done
  [[ "$total_tests" -eq "$expected_tests" ]] || \
    fail "$gate asserted tests=$total_tests, expected=$expected_tests"
  TOTAL_ASSERTED_TESTCASES=$((TOTAL_ASSERTED_TESTCASES + total_tests))
}

invoke_child_with_id() {
  local ordinal="$1"
  local gate="$2"
  local script="$3"
  local child_id="$4"
  local log_file="$RUN_ROOT/child-logs/$ordinal-$gate.log"
  local pipeline_status child_rc tee_rc

  CURRENT_STEP="$ordinal-$gate"
  assert_no_repository_maven
  echo "[v933-batch6-exit] starting step=$CURRENT_STEP child_run=$child_id"
  set +e
  (cd "$ROOT_DIR" && "$script" "$child_id") 2>&1 | tee "$log_file"
  pipeline_status=("${PIPESTATUS[@]}")
  set -e
  child_rc="${pipeline_status[0]:-1}"
  tee_rc="${pipeline_status[1]:-1}"
  printf 'child_exit=%s\ntee_exit=%s\n' "$child_rc" "$tee_rc" \
    > "$RUN_ROOT/child-logs/$ordinal-$gate.status"
  [[ "$child_rc" -eq 0 ]] || fail "child failed: step=$CURRENT_STEP exit=$child_rc"
  [[ "$tee_rc" -eq 0 ]] || fail "could not retain child output: step=$CURRENT_STEP"
}

invoke_entry_child() {
  local ordinal="09"
  local gate="entry-gate"
  local entry_runs="$ROOT_DIR/target/v933-entry-gate/runs"
  local record_dir="$RUN_ROOT/child-records/$ordinal-$gate"
  local before="$record_dir/run-dirs-before.txt"
  local after="$record_dir/run-dirs-after.txt"
  local added="$record_dir/run-dirs-added.txt"
  local log_file="$RUN_ROOT/child-logs/$ordinal-$gate.log"
  local pipeline_status child_rc tee_rc pass_line pass_id pass_evidence

  mkdir -p "$entry_runs" "$record_dir"
  find "$entry_runs" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' \
    | LC_ALL=C sort > "$before"

  CURRENT_STEP="$ordinal-$gate"
  assert_no_repository_maven
  echo "[v933-batch6-exit] starting step=$CURRENT_STEP child_run=generated-by-entry-gate"
  set +e
  (cd "$ROOT_DIR" && "$ENTRY_SCRIPT") 2>&1 | tee "$log_file"
  pipeline_status=("${PIPESTATUS[@]}")
  set -e
  child_rc="${pipeline_status[0]:-1}"
  tee_rc="${pipeline_status[1]:-1}"
  printf 'child_exit=%s\ntee_exit=%s\n' "$child_rc" "$tee_rc" \
    > "$RUN_ROOT/child-logs/$ordinal-$gate.status"
  [[ "$child_rc" -eq 0 ]] || fail "child failed: step=$CURRENT_STEP exit=$child_rc"
  [[ "$tee_rc" -eq 0 ]] || fail "could not retain child output: step=$CURRENT_STEP"

  find "$entry_runs" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' \
    | LC_ALL=C sort > "$after"
  comm -13 "$before" "$after" > "$added"
  [[ "$(wc -l < "$added")" -eq 1 ]] || \
    fail "entry gate must create exactly one run root; see $added"

  mapfile -t pass_lines < <(
    grep -E '^\[v933-gate\] PASS run=[A-Za-z0-9._-]+ evidence=.+$' "$log_file" || true
  )
  [[ "${#pass_lines[@]}" -eq 1 ]] || \
    fail "entry gate must emit exactly one PASS run line"
  pass_line="${pass_lines[0]}"
  pass_id="$(sed -E 's/^\[v933-gate\] PASS run=([A-Za-z0-9._-]+) evidence=.*/\1/' <<< "$pass_line")"
  pass_evidence="${pass_line#* evidence=}"
  ENTRY_RUN_ID="$(<"$added")"
  [[ "$pass_id" == "$ENTRY_RUN_ID" ]] || \
    fail "entry PASS id=$pass_id differs from uniquely added id=$ENTRY_RUN_ID"
  [[ "$(readlink -f "$pass_evidence")" == "$(readlink -f "$entry_runs/$ENTRY_RUN_ID")" ]] || \
    fail "entry PASS evidence path does not identify the unique new run root"
}

verify_child_manifest() {
  local child_root="$1"
  local manifest="$2"
  local output_file="$3"
  [[ -f "$manifest" ]] || fail "child manifest is missing: $manifest"
  if ! (cd "$child_root" && sha256sum -c "$manifest") \
      > "$output_file" 2>&1; then
    fail "child manifest verification failed: $manifest"
  fi
}

register_child() {
  local ordinal="$1"
  local gate="$2"
  local script="$3"
  local child_id="$4"
  local child_root="$5"
  local summary="$6"
  local manifest="$7"
  local tests="$8"
  local reports="$9"
  shift 9
  local failures="$1"
  local errors="$2"
  local skipped="$3"
  local expected_negative="$4"
  local remaining_red="$5"
  local record_dir="$RUN_ROOT/child-records/$ordinal-$gate"
  local summary_hash manifest_hash

  [[ -d "$child_root" ]] || fail "child run root is missing: $child_root"
  [[ -f "$summary" ]] || fail "child summary is missing: $summary"
  mkdir -p "$record_dir"
  verify_child_manifest "$child_root" "$manifest" "$record_dir/manifest-check-initial.txt"
  summary_hash="$(sha256sum "$summary" | awk '{print $1}')"
  manifest_hash="$(sha256sum "$manifest" | awk '{print $1}')"
  cp -p "$summary" "$record_dir/"
  cp -p "$manifest" "$record_dir/"
  printf '%s\n' "$child_root" > "$record_dir/child-run-root.txt"

  printf '%s\t%s\t%s\t%s\t%s\t0\tpassed\t%s\t%s\t%s\t%s\ttrue\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$ordinal" \
    "$gate" \
    "${script#$ROOT_DIR/}" \
    "$child_id" \
    "$child_root" \
    "$summary" \
    "$summary_hash" \
    "$manifest" \
    "$manifest_hash" \
    "$tests" \
    "$reports" \
    "$failures" \
    "$errors" \
    "$skipped" \
    "$expected_negative" \
    "$remaining_red" \
    >> "$CHILDREN_TSV"

  CHILD_ROOTS+=("$child_root")
  CHILD_MANIFESTS+=("$manifest")
  CHILD_SUMMARIES+=("$summary")
  CHILD_SUMMARY_HASHES+=("$summary_hash")
  CHILD_MANIFEST_HASHES+=("$manifest_hash")
  CHILD_RECORD_DIRS+=("$record_dir")
  CHILD_COUNT=$((CHILD_COUNT + 1))
  TOTAL_CRITERIA_TESTS=$((TOTAL_CRITERIA_TESTS + tests))
  TOTAL_ASSERTED_REPORTS=$((TOTAL_ASSERTED_REPORTS + reports))
  TOTAL_FAILURES=$((TOTAL_FAILURES + failures))
  TOTAL_ERRORS=$((TOTAL_ERRORS + errors))
  TOTAL_SKIPPED=$((TOTAL_SKIPPED + skipped))
  TOTAL_EXPECTED_NEGATIVE=$((TOTAL_EXPECTED_NEGATIVE + expected_negative))
  TOTAL_REMAINING_RED=$((TOTAL_REMAINING_RED + remaining_red))
  LAST_COMPLETED_STEP="$ordinal-$gate"
}

parse_catalog() {
  local root="$1"
  local summary="$root/summary.env"
  expect_env "$summary" run_id "$CATALOG_RUN_ID"
  expect_env "$summary" status passed
  expect_env "$summary" total_tests 53
  expect_env "$summary" owning_reports 7
  expect_env "$summary" failures 0
  expect_env "$summary" errors 0
  expect_env "$summary" skipped 0
  expect_env "$summary" remaining_red_suites 0
  expect_env "$summary" remaining_red_tests 0
  assert_xml_report_metrics batch6-catalog "$root" copied 7 53
  register_child 01 batch6-catalog "$CATALOG_SCRIPT" "$CATALOG_RUN_ID" \
    "$root" "$summary" "$root/SHA256SUMS" 53 7 0 0 0 0 0
  CATALOG_AUTHORITY_STATUS="passed"
}

parse_cache_identity() {
  local root="$1"
  local summary="$root/summary.env"
  expect_env "$summary" run_id "$CACHE_IDENTITY_RUN_ID"
  expect_env "$summary" status passed
  expect_env "$summary" criterion CACHE-IDENTITY
  expect_env "$summary" model_pin_tests 6
  expect_env "$summary" step2_addon_tests 68
  expect_env "$summary" step2_total_tests 74
  expect_env "$summary" step3_context_tests 1
  expect_env "$summary" total_tests 75
  expect_env "$summary" owning_reports 6
  expect_env "$summary" failures 0
  expect_env "$summary" errors 0
  expect_env "$summary" skipped 0
  expect_env "$summary" strong_identity passed
  expect_env "$summary" cross_application_context passed
  assert_xml_report_metrics batch6-cache-identity "$root" copied 6 75
  register_child 02 batch6-cache-identity "$CACHE_IDENTITY_SCRIPT" \
    "$CACHE_IDENTITY_RUN_ID" "$root" "$summary" "$root/SHA256SUMS" \
    75 6 0 0 0 0 0
  CACHE_IDENTITY_STATUS="passed"
}

parse_cache_cross_jvm() {
  local root="$1"
  local summary="$root/summary.env"
  expect_env "$summary" run_id "$CACHE_CROSS_JVM_RUN_ID"
  expect_env "$summary" status passed
  expect_env "$summary" tests 1
  expect_env "$summary" owning_reports 1
  expect_env "$summary" failures 0
  expect_env "$summary" errors 0
  expect_env "$summary" skipped 0
  expect_env "$summary" child_jvms 2
  expect_env "$summary" previous_identity_control_hits 2
  expect_env "$summary" restart_current_identity_misses 2
  expect_env "$summary" redis_keys 4
  expect_env "$summary" binding_key primary
  expect_env "$summary" binding_backend runtime-registry
  expect_env_contains "$summary" binding_generation 'binding:'
  expect_env "$summary" identity_rotation boot_scoped_catalog_and_source
  assert_xml_report_metrics batch6-cache-cross-jvm "$root" copied 1 1
  register_child 03 batch6-cache-cross-jvm "$CACHE_CROSS_JVM_SCRIPT" \
    "$CACHE_CROSS_JVM_RUN_ID" "$root" "$summary" "$root/SHA256SUMS" \
    1 1 0 0 0 0 0
  CACHE_CROSS_JVM_STATUS="passed"
}

parse_pivot() {
  local root="$1"
  local summary="$root/summary.txt"
  expect_env "$summary" run_id "$PIVOT_RUN_ID"
  expect_env "$summary" criterion CACHE-GEN/Pivot
  expect_env "$summary" direct_tests 38
  expect_env "$summary" direct_reports 4
  expect_env "$summary" supporting_tests 61
  expect_env "$summary" supporting_reports 3
  expect_env "$summary" total_tests 99
  expect_env "$summary" total_reports 7
  expect_env "$summary" failures 0
  expect_env "$summary" errors 0
  expect_env "$summary" skipped 0
  expect_env "$summary" provider_failure_fail_closed true
  expect_env "$summary" catalog_pin_preserved true
  expect_env "$summary" full_sha256_identity true
  expect_env "$summary" manual_tokens_additive_only true
  assert_xml_report_metrics batch6-pivot "$root" copied 7 99
  register_child 04 batch6-pivot "$PIVOT_SCRIPT" "$PIVOT_RUN_ID" \
    "$root" "$summary" "$root/SHA256SUMS" 99 7 0 0 0 0 0
  CACHE_GEN_STATUS="passed"
}

parse_real_query() {
  local root="$1"
  local summary="$root/summary.env"
  expect_env "$summary" run_id "$REAL_QUERY_RUN_ID"
  expect_env "$summary" status passed
  expect_env "$summary" criterion REAL-QUERY
  expect_env "$summary" model_lifecycle_tests 4
  expect_env "$summary" required_database_tests 3
  expect_env "$summary" cache_provider_tests 4
  expect_env "$summary" total_tests 11
  expect_env "$summary" owning_reports 6
  expect_env "$summary" failures 0
  expect_env "$summary" errors 0
  expect_env "$summary" skipped 0
  expect_env_contains "$summary" sqlite_probe 'kind=sqlite physical_role=embedded-shared-memory product=SQLite version=3.42'
  expect_env_contains "$summary" sqlite_probe 'rows_sentinel_1=8 rows_sentinel_2=2'
  expect_env_contains "$summary" mysql57_probe 'kind=mysql57 physical_role=required-mysql57-container product=MySQL version=5.7'
  expect_env_contains "$summary" mysql57_probe 'rows_sentinel_1=25 rows_sentinel_2=25'
  expect_env_contains "$summary" postgres15_probe 'kind=postgres15 physical_role=required-postgresql15-container product=PostgreSQL version=15.'
  expect_env_contains "$summary" postgres15_probe 'rows_sentinel_1=25 rows_sentinel_2=25'
  for probe_key in sqlite_probe mysql57_probe postgres15_probe; do
    expect_env_contains "$summary" "$probe_key" 'catalog_generation=catalog:'
    expect_env_contains "$summary" "$probe_key" 'source_revision=source:'
    expect_env_contains "$summary" "$probe_key" 'binding_generation=fixture:'
  done
  expect_env "$summary" redis_image redis:7-alpine
  expect_env "$summary" redis_initial_keys 0
  expect_env "$summary" redis_final_keys 0
  expect_env "$summary" redis_cleanup removed
  assert_xml_report_metrics batch6-real-query "$root" copied 6 11
  register_child 05 batch6-real-query "$REAL_QUERY_SCRIPT" "$REAL_QUERY_RUN_ID" \
    "$root" "$summary" "$root/SHA256SUMS" 11 6 0 0 0 0 0
  REAL_QUERY_STATUS="passed"
}

parse_batch5() {
  local root="$1"
  local summary="$root/summary.env"
  expect_env "$summary" run_id "$BATCH5_RUN_ID"
  expect_env "$summary" status passed
  expect_env "$summary" total_tests 90
  expect_env "$summary" owning_reports 19
  expect_env "$summary" failures 0
  expect_env "$summary" errors 0
  expect_env "$summary" skipped 0
  assert_xml_report_metrics batch5-refresh "$root" copied 19 90
  register_child 06 batch5-refresh "$BATCH5_SCRIPT" "$BATCH5_RUN_ID" \
    "$root" "$summary" "$root/SHA256SUMS" 90 19 0 0 0 0 0
}

parse_batch4() {
  local root="$1"
  local summary="$root/summary.env"
  expect_env "$summary" run_id "$BATCH4_RUN_ID"
  expect_env "$summary" status passed
  expect_env "$summary" catalog_regression_tests 36
  expect_env "$summary" total_tests 142
  expect_env "$summary" failures 0
  expect_env "$summary" errors 0
  expect_env "$summary" skipped 0
  expect_env "$summary" same_key_callers 100
  expect_env "$summary" same_key_waiters 99
  expect_env "$summary" single_flight_residual_entries 0
  assert_xml_report_metrics batch4-single-flight "$root" copied 22 142
  register_child 07 batch4-single-flight "$BATCH4_SCRIPT" "$BATCH4_RUN_ID" \
    "$root" "$summary" "$root/SHA256SUMS" 142 22 0 0 0 0 0
}

parse_batch3() {
  local root="$1"
  local summary="$root/summary.env"
  expect_env "$summary" run_id "$BATCH3_RUN_ID"
  expect_env "$summary" status passed
  expect_env "$summary" catalog_authority_tests 57
  expect_env "$summary" total_tests 168
  expect_env "$summary" failures 0
  expect_env "$summary" errors 0
  expect_env "$summary" skipped 0
  expect_env "$summary" legacy_mutable_authority 0
  expect_env "$summary" sleep_driven_tests 0
  assert_xml_report_metrics batch3-catalog-binding "$root" copied 20 168
  register_child 08 batch3-catalog-binding "$BATCH3_SCRIPT" "$BATCH3_RUN_ID" \
    "$root" "$summary" "$root/SHA256SUMS" 168 20 0 0 0 0 0
}

parse_entry() {
  local root="$1"
  local summary="$root/summary.env"
  expect_env "$summary" RUN_ID "$ENTRY_RUN_ID"
  expect_env "$summary" RESULT passed
  expect_env "$summary" PROBE_UNIT_TESTS 1
  expect_env "$summary" PROBE_IT_TESTS 1
  expect_env "$summary" SQLITE_PREFLIGHT_TESTS 1
  expect_env "$summary" MYSQL57_PREFLIGHT_TESTS 1
  expect_env "$summary" POSTGRES15_PREFLIGHT_TESTS 1
  expect_env "$summary" EXPECTED_NEGATIVE_CASES 4
  for negative_lane in \
      negative-missing-unit \
      negative-missing-it \
      negative-wrong-db \
      negative-missing-report; do
    [[ -d "$root/$negative_lane" ]] || \
      fail "entry expected-negative lane is missing: $negative_lane"
  done
  assert_xml_report_metrics entry-gate "$root" entry-positive 5 5
  register_child 09 entry-gate "$ENTRY_SCRIPT" "$ENTRY_RUN_ID" \
    "$root" "$summary" "$root/SHA256SUMS" 5 5 0 0 0 4 0
}

parse_batch2() {
  local root="$1"
  local summary="$root/summary.env"
  expect_env "$summary" run_id "$BATCH2_RUN_ID"
  expect_env "$summary" status passed
  expect_env "$summary" reactor_prep passed
  expect_env_integer_at_least "$summary" default_discovery_reports 5
  expect_env "$summary" default_discovery_owning_suites 5
  expect_env "$summary" default_discovery_owning_tests 26
  expect_env "$summary" namespace_product_tests 25
  expect_env "$summary" legacy_compatibility_tests 7
  expect_env "$summary" failures 0
  expect_env "$summary" errors 0
  expect_env "$summary" skipped 0
  expect_env "$summary" production_legacy_mutations 0
  expect_env "$summary" sleep_driven_tests 0
  # The Batch 2 report set also contains the one-test deterministic harness
  # probe. It is executed/asserted but is not counted as a Namespace criterion.
  assert_xml_report_metrics batch2-namespace "$root" asserted 6 33
  register_child 10 batch2-namespace "$BATCH2_SCRIPT" "$BATCH2_RUN_ID" \
    "$root" "$summary" "$root/SHA256SUMS" 32 6 0 0 0 0 0
}

parse_remaining_red() {
  local root="$1"
  local summary="$root/summary.tsv"
  local non_blank_sources
  if ! awk -F '\t' '
      NR == 1 {
        if ($0 != "step\tcase\tmodule\tclass\ttests\tresult") exit 2
      }
      NR == 2 {
        if ($1 != "0" || $2 != "none" || $3 != "n/a" ||
            $4 != "n/a" || $5 != "0" || $6 != "NONE_REMAINING") exit 3
      }
      END {
        if (NR != 2) exit 4
      }
    ' "$summary"; then
    fail "remaining-red summary is not the exact zero-source contract: $summary"
  fi
  non_blank_sources="$(grep -cve '^[[:space:]]*$' \
    "$root/remaining-red-sources.txt" || true)"
  [[ "$non_blank_sources" -eq 0 ]] || \
    fail "remaining RedBaseline source inventory is non-zero"
  register_child 11 remaining-red "$REMAINING_RED_SCRIPT" "$REMAINING_RED_RUN_ID" \
    "$root" "$summary" "$root/sha256sum.txt" 0 0 0 0 0 0 0
}

CATALOG_RUN_ID="$RUN_ID-01-catalog"
invoke_child_with_id 01 batch6-catalog "$CATALOG_SCRIPT" "$CATALOG_RUN_ID"
parse_catalog "$ROOT_DIR/target/v933-batch6-catalog/runs/$CATALOG_RUN_ID"

CACHE_IDENTITY_RUN_ID="$RUN_ID-02-cache-identity"
invoke_child_with_id 02 batch6-cache-identity \
  "$CACHE_IDENTITY_SCRIPT" "$CACHE_IDENTITY_RUN_ID"
parse_cache_identity \
  "$ROOT_DIR/target/v933-batch6-cache-identity/runs/$CACHE_IDENTITY_RUN_ID"

CACHE_CROSS_JVM_RUN_ID="$RUN_ID-03-cache-cross-jvm"
invoke_child_with_id 03 batch6-cache-cross-jvm \
  "$CACHE_CROSS_JVM_SCRIPT" "$CACHE_CROSS_JVM_RUN_ID"
parse_cache_cross_jvm \
  "$ROOT_DIR/target/v933-batch6-cache-cross-jvm/runs/$CACHE_CROSS_JVM_RUN_ID"

PIVOT_RUN_ID="$RUN_ID-04-pivot"
invoke_child_with_id 04 batch6-pivot "$PIVOT_SCRIPT" "$PIVOT_RUN_ID"
parse_pivot "$ROOT_DIR/target/v933-batch6-pivot/runs/$PIVOT_RUN_ID"

REAL_QUERY_RUN_ID="$RUN_ID-05-real-query"
invoke_child_with_id 05 batch6-real-query "$REAL_QUERY_SCRIPT" "$REAL_QUERY_RUN_ID"
parse_real_query "$ROOT_DIR/target/v933-batch6-real-query/runs/$REAL_QUERY_RUN_ID"

BATCH5_RUN_ID="$RUN_ID-06-batch5"
invoke_child_with_id 06 batch5-refresh "$BATCH5_SCRIPT" "$BATCH5_RUN_ID"
parse_batch5 "$ROOT_DIR/target/v933-batch5-refresh/runs/$BATCH5_RUN_ID"

BATCH4_RUN_ID="$RUN_ID-07-batch4"
invoke_child_with_id 07 batch4-single-flight "$BATCH4_SCRIPT" "$BATCH4_RUN_ID"
parse_batch4 "$ROOT_DIR/target/v933-batch4-single-flight/runs/$BATCH4_RUN_ID"

BATCH3_RUN_ID="$RUN_ID-08-batch3"
invoke_child_with_id 08 batch3-catalog-binding "$BATCH3_SCRIPT" "$BATCH3_RUN_ID"
parse_batch3 "$ROOT_DIR/target/v933-batch3-catalog-binding/runs/$BATCH3_RUN_ID"

invoke_entry_child
parse_entry "$ROOT_DIR/target/v933-entry-gate/runs/$ENTRY_RUN_ID"

BATCH2_RUN_ID="$RUN_ID-10-batch2"
invoke_child_with_id 10 batch2-namespace "$BATCH2_SCRIPT" "$BATCH2_RUN_ID"
parse_batch2 "$ROOT_DIR/target/v933-batch2-namespace/runs/$BATCH2_RUN_ID"

REMAINING_RED_RUN_ID="$RUN_ID-11-remaining-red"
invoke_child_with_id 11 remaining-red "$REMAINING_RED_SCRIPT" "$REMAINING_RED_RUN_ID"
parse_remaining_red "$ROOT_DIR/target/v933-batch1-red/runs/$REMAINING_RED_RUN_ID"

CURRENT_STEP="final-integrity"
[[ "$CHILD_COUNT" -eq 11 ]] || fail "child count=$CHILD_COUNT, expected=11"
[[ "$TOTAL_CRITERIA_TESTS" -eq 676 ]] || \
  fail "criteria-accounted tests=$TOTAL_CRITERIA_TESTS, expected=676"
[[ "$TOTAL_ASSERTED_TESTCASES" -eq 677 ]] || \
  fail "asserted report testcases=$TOTAL_ASSERTED_TESTCASES, expected=677"
[[ "$TOTAL_ASSERTED_REPORTS" -eq 99 ]] || \
  fail "fixed asserted reports=$TOTAL_ASSERTED_REPORTS, expected=99"
[[ "$TOTAL_FAILURES" -eq 0 && "$TOTAL_ERRORS" -eq 0 && "$TOTAL_SKIPPED" -eq 0 ]] || \
  fail "aggregate failures/errors/skipped are non-zero"
[[ "$TOTAL_EXPECTED_NEGATIVE" -eq 4 ]] || \
  fail "expected-negative cases=$TOTAL_EXPECTED_NEGATIVE, expected=4"
[[ "$TOTAL_REMAINING_RED" -eq 0 ]] || \
  fail "remaining-red count=$TOTAL_REMAINING_RED, expected=0"
[[ "$CATALOG_AUTHORITY_STATUS" == passed ]] || fail "CATALOG-AUTHORITY is not passed"
[[ "$CACHE_IDENTITY_STATUS" == passed ]] || fail "CACHE-IDENTITY is not passed"
[[ "$CACHE_CROSS_JVM_STATUS" == passed ]] || fail "CACHE-CROSS-JVM is not passed"
[[ "$CACHE_GEN_STATUS" == passed ]] || fail "CACHE-GEN is not passed"
[[ "$REAL_QUERY_STATUS" == passed ]] || fail "REAL-QUERY is not passed"

for index in "${!CHILD_ROOTS[@]}"; do
  current_summary_hash="$(sha256sum "${CHILD_SUMMARIES[$index]}" | awk '{print $1}')"
  [[ "$current_summary_hash" == "${CHILD_SUMMARY_HASHES[$index]}" ]] || \
    fail "child summary changed after registration: ${CHILD_SUMMARIES[$index]}"
  current_manifest_hash="$(sha256sum "${CHILD_MANIFESTS[$index]}" | awk '{print $1}')"
  [[ "$current_manifest_hash" == "${CHILD_MANIFEST_HASHES[$index]}" ]] || \
    fail "child manifest changed after registration: ${CHILD_MANIFESTS[$index]}"
  recorded_summary="${CHILD_RECORD_DIRS[$index]}/$(basename "${CHILD_SUMMARIES[$index]}")"
  recorded_manifest="${CHILD_RECORD_DIRS[$index]}/$(basename "${CHILD_MANIFESTS[$index]}")"
  [[ "$(sha256sum "$recorded_summary" | awk '{print $1}')" == "${CHILD_SUMMARY_HASHES[$index]}" ]] || \
    fail "recorded child summary differs from its initial hash: $recorded_summary"
  [[ "$(sha256sum "$recorded_manifest" | awk '{print $1}')" == "${CHILD_MANIFEST_HASHES[$index]}" ]] || \
    fail "recorded child manifest differs from its initial hash: $recorded_manifest"
  verify_child_manifest \
    "${CHILD_ROOTS[$index]}" \
    "$recorded_manifest" \
    "${CHILD_RECORD_DIRS[$index]}/manifest-check-final.txt"
done

capture_after_state || fail "could not capture final workspace/container state"
[[ ! -s "$RUN_ROOT/environment/run-owned-containers-after.txt" ]] || \
  fail "run-owned container cleanup is incomplete; see environment/run-owned-containers-after.txt"
for expected_container in "${EXPECTED_RUN_CONTAINERS[@]}"; do
  if grep -Fxq -- "$expected_container" \
      "$RUN_ROOT/environment/run-owned-containers-after.txt"; then
    fail "expected run-owned container still exists: $expected_container"
  fi
done
cmp -s \
  "$RUN_ROOT/environment/containers-before.tsv" \
  "$RUN_ROOT/environment/containers-after.tsv" || \
  fail "fixed container metadata changed during the aggregate replay"

cmp -s \
  "$RUN_ROOT/source-audit/git-head-before.txt" \
  "$RUN_ROOT/source-audit/git-head-after.txt" || \
  fail "git HEAD changed during the aggregate replay"
cmp -s \
  "$RUN_ROOT/source-audit/git-status-before.txt" \
  "$RUN_ROOT/source-audit/git-status-after.txt" || \
  fail "git worktree status changed during the aggregate replay"
cmp -s \
  "$RUN_ROOT/source-audit/git-diff-before.sha256" \
  "$RUN_ROOT/source-audit/git-diff-after.sha256" || \
  fail "tracked worktree content changed during the aggregate replay"
cmp -s \
  "$RUN_ROOT/source-audit/untracked-list-before.sha256" \
  "$RUN_ROOT/source-audit/untracked-list-after.sha256" || \
  fail "untracked path inventory changed during the aggregate replay"
cmp -s \
  "$RUN_ROOT/source-audit/untracked-content-before.sha256" \
  "$RUN_ROOT/source-audit/untracked-content-after.sha256" || \
  fail "untracked content changed during the aggregate replay"
(cd "$ROOT_DIR" && sha256sum -c "$SOURCE_MANIFEST") \
  > "$RUN_ROOT/source-audit/source-hash-check.txt" || \
  fail "relevant source files changed during the aggregate replay"

CHILD_INDEX_SHA256="$(sha256sum "$CHILDREN_TSV" | awk '{print $1}')"
SOURCE_MANIFEST_SHA256="$(sha256sum "$SOURCE_MANIFEST" | awk '{print $1}')"
GIT_HEAD="$(<"$RUN_ROOT/source-audit/git-head-before.txt")"
GIT_STATUS_BEFORE_SHA256="$(sha256sum \
  "$RUN_ROOT/source-audit/git-status-before.txt" | awk '{print $1}')"
GIT_STATUS_AFTER_SHA256="$(sha256sum \
  "$RUN_ROOT/source-audit/git-status-after.txt" | awk '{print $1}')"
CONTAINERS_BEFORE_SHA256="$(sha256sum \
  "$RUN_ROOT/environment/containers-before.tsv" | awk '{print $1}')"
CONTAINERS_AFTER_SHA256="$(sha256sum \
  "$RUN_ROOT/environment/containers-after.tsv" | awk '{print $1}')"
TRACKED_DIFF_SHA256="$(<"$RUN_ROOT/source-audit/git-diff-before.sha256")"
UNTRACKED_LIST_SHA256="$(<"$RUN_ROOT/source-audit/untracked-list-before.sha256")"
UNTRACKED_CONTENT_SHA256="$(<"$RUN_ROOT/source-audit/untracked-content-before.sha256")"
FINISHED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

cat > "$RUN_ROOT/summary.env" <<SUMMARY
run_id=$RUN_ID
status=passed
version=9.3.3
batch=6
started_at=$STARTED_AT
finished_at=$FINISHED_AT
ordered_children=11
criteria_tests=676
asserted_report_testcases=677
asserted_reports=99
failures=0
errors=0
skipped=0
gate0_expected_negative=4
gate0_expected_negative_passed=4
remaining_red_sources=0
remaining_red_tests=0
catalog_authority=passed
cache_identity=passed
cache_cross_jvm=passed
cache_gen=passed
real_query=passed
catalog_run_id=$CATALOG_RUN_ID
cache_identity_run_id=$CACHE_IDENTITY_RUN_ID
cache_cross_jvm_run_id=$CACHE_CROSS_JVM_RUN_ID
pivot_run_id=$PIVOT_RUN_ID
real_query_run_id=$REAL_QUERY_RUN_ID
batch5_run_id=$BATCH5_RUN_ID
batch4_run_id=$BATCH4_RUN_ID
batch3_run_id=$BATCH3_RUN_ID
entry_run_id=$ENTRY_RUN_ID
batch2_run_id=$BATCH2_RUN_ID
remaining_red_run_id=$REMAINING_RED_RUN_ID
git_head=$GIT_HEAD
git_status_before_sha256=$GIT_STATUS_BEFORE_SHA256
git_status_after_sha256=$GIT_STATUS_AFTER_SHA256
source_manifest_sha256=$SOURCE_MANIFEST_SHA256
child_index_sha256=$CHILD_INDEX_SHA256
containers_before_sha256=$CONTAINERS_BEFORE_SHA256
containers_after_sha256=$CONTAINERS_AFTER_SHA256
tracked_diff_sha256=$TRACKED_DIFF_SHA256
untracked_list_sha256=$UNTRACKED_LIST_SHA256
untracked_content_sha256=$UNTRACKED_CONTENT_SHA256
child_manifests_verified_initial=true
child_manifests_verified_final=true
child_manifests_unchanged=true
source_unchanged=true
dirty_worktree_unchanged=true
fixed_containers_unchanged=true
run_owned_containers_after=0
inner_manifest_verified=true
outer_manifest_verified=true
batch7_state=ready-not-started
deferred=API-COMPAT,REGRESSION,QUALITY,COVERAGE,ACCEPTANCE
SUMMARY

INNER_MANIFEST="$RUN_ROOT/INNER_SHA256SUMS"
(
  cd "$RUN_ROOT"
  find . -type f \
    ! -name INNER_SHA256SUMS \
    ! -name SHA256SUMS \
    ! -name SHA256SUMS.sha256 \
    ! -name outer-manifest-check.txt \
    -print0 \
    | LC_ALL=C sort -z \
    | xargs -0 sha256sum
) > "$INNER_MANIFEST"
(cd "$RUN_ROOT" && sha256sum -c INNER_SHA256SUMS) \
  > "$RUN_ROOT/integrity/inner-manifest-check.txt"

OUTER_MANIFEST="$RUN_ROOT/SHA256SUMS"
(
  cd "$RUN_ROOT"
  find . -type f \
    ! -name SHA256SUMS \
    ! -name SHA256SUMS.sha256 \
    ! -name outer-manifest-check.txt \
    -print0 \
    | LC_ALL=C sort -z \
    | xargs -0 sha256sum
) > "$OUTER_MANIFEST"
(cd "$RUN_ROOT" && sha256sum -c SHA256SUMS >/dev/null)
(cd "$RUN_ROOT" && sha256sum SHA256SUMS > SHA256SUMS.sha256)
(cd "$RUN_ROOT" && sha256sum -c SHA256SUMS.sha256 > outer-manifest-check.txt)

printf '%s\n' "$RUN_ID" > "$LATEST_TMP"
trap '' HUP INT TERM
mv -f "$LATEST_TMP" "$LATEST_RUN_ID"
LAST_COMPLETED_STEP="final-integrity"
CURRENT_STEP="complete"
SUCCESS_FINALIZED=1
