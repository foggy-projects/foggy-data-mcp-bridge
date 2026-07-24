#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: V933_BATCH7_SQLITE_EXPECTED_TESTS=N \
       scripts/verify-v933-batch7-regression.sh [RUN_ID]

Run the complete 9.3.3 Batch 7 compatibility/regression replay strictly in
series. Every lane writes fresh, run-scoped reports and a two-layer SHA-256
manifest. Failed runs are retained and never update latest-run-id.

The SQLite full-suite count is deliberately not discovered by this acceptance
runner. Diagnose it once in a fresh report directory, review the result, then
freeze that exact positive integer in V933_BATCH7_SQLITE_EXPECTED_TESTS. The
runner always requires exactly three SQLite skips and zero failures/errors.
USAGE
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
SQLITE_EXPECTED_TESTS="${V933_BATCH7_SQLITE_EXPECTED_TESTS:-}"
SQLITE_FROZEN_TESTS=3449
SQLITE_EXPECTED_REPORTS=470
TARGET_ROOT="$ROOT_DIR/target/v933-batch7-regression"
RUN_ROOT="$TARGET_ROOT/runs/$RUN_ID"
LOCK_FILE="$TARGET_ROOT/.aggregate.lock"
LATEST_RUN_ID="$TARGET_ROOT/latest-run-id"
LATEST_TMP="$TARGET_ROOT/.latest-run-id.$RUN_ID.tmp"
REPORT_ASSERTION="$ROOT_DIR/scripts/assert-v933-test-report.sh"
REAL_QUERY_SCRIPT="$ROOT_DIR/scripts/verify-v933-batch6-real-query.sh"

CURRENT_STEP="preflight"
LAST_COMPLETED_STEP="none"
FAIL_REASON="unexpected-command-failure"
SUCCESS_FINALIZED=0

TOTAL_TESTS=0
TOTAL_REPORTS=0
TOTAL_FAILURES=0
TOTAL_ERRORS=0
TOTAL_SKIPPED=0
SQLITE_ACTUAL_REPORTS=0

fail() {
  FAIL_REASON="$*"
  echo "[v933-batch7] ERROR: $*" >&2
  exit 1
}

[[ "$#" -le 1 ]] || {
  usage >&2
  exit 2
}
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
[[ "${#RUN_ID}" -le 80 ]] || fail "run id is longer than 80 characters"
[[ "$SQLITE_EXPECTED_TESTS" =~ ^[1-9][0-9]*$ ]] || {
  usage >&2
  fail "V933_BATCH7_SQLITE_EXPECTED_TESTS must be a reviewed positive integer"
}
[[ "$SQLITE_EXPECTED_TESTS" -eq "$SQLITE_FROZEN_TESTS" ]] || \
  fail "reviewed SQLite count=$SQLITE_EXPECTED_TESTS, frozen Batch 7 count=$SQLITE_FROZEN_TESTS"

mkdir -p "$TARGET_ROOT/runs"
exec 9>"$LOCK_FILE"
flock -n 9 || fail "another Batch 7 aggregate replay holds $LOCK_FILE"
[[ ! -e "$RUN_ROOT" ]] || fail "run directory already exists: $RUN_ROOT"
mkdir -p \
  "$RUN_ROOT/environment" \
  "$RUN_ROOT/integrity" \
  "$RUN_ROOT/lanes" \
  "$RUN_ROOT/source-audit"
: > "$RUN_ROOT/.run-start"

LANES_TSV="$RUN_ROOT/lanes.tsv"
printf '%s\n' $'ordinal\tlane\ttype\ttests\treports\tfailures\terrors\tskipped\tevidence' \
  > "$LANES_TSV"

on_exit() {
  local status="$?"
  set +e
  rm -f "$LATEST_TMP"
  if [[ "$status" -ne 0 || "$SUCCESS_FINALIZED" -ne 1 ]]; then
    if [[ -d "$RUN_ROOT" ]]; then
      printf '%s\n' "$FAIL_REASON" > "$RUN_ROOT/failure-reason.txt"
      cat > "$RUN_ROOT/failure.env" <<FAILURE
run_id=$RUN_ID
status=failed
exit_status=$status
current_step=$CURRENT_STEP
last_completed_step=$LAST_COMPLETED_STEP
latest_run_id_updated=false
FAILURE
    fi
    echo "[v933-batch7] FAILED run=$RUN_ID step=$CURRENT_STEP evidence=$RUN_ROOT" >&2
    if [[ "$status" -eq 0 ]]; then
      exit 1
    fi
  else
    echo "[v933-batch7] PASS run=$RUN_ID evidence=$RUN_ROOT"
  fi
}
trap on_exit EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

required_commands=(
  awk bash basename cmp comm cp cut date diff docker find flock git grep java
  jar mkdir mv mvn pgrep readlink rg rm sed sha256sum sort tee tr unzip wc xargs
)
for command_name in "${required_commands[@]}"; do
  command -v "$command_name" >/dev/null 2>&1 || \
    fail "required command is missing: $command_name"
done
[[ -x "$REPORT_ASSERTION" ]] || fail "report assertion is not executable: $REPORT_ASSERTION"
[[ -x "$REAL_QUERY_SCRIPT" ]] || fail "real-query runner is not executable: $REAL_QUERY_SCRIPT"
docker info >/dev/null 2>&1 || fail "Docker daemon is unavailable"

EXTERNAL_MAVEN_FLAGS="${MAVEN_ARGS:-} ${MAVEN_OPTS:-} ${JAVA_TOOL_OPTIONS:-}"
if [[ "$EXTERNAL_MAVEN_FLAGS" =~ (^|[[:space:]])-D(skipTests|maven\.test\.skip|skipITs|skipUnitTests)($|=|[[:space:]]) ]]; then
  fail "external Maven test-skip properties are forbidden for Batch 7"
fi
if [[ "$EXTERNAL_MAVEN_FLAGS" =~ (^|[[:space:]])-P[^[:space:]]*multi-db ]]; then
  fail "external multi-db profile activation is forbidden for Batch 7"
fi

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

env_value() {
  local file="$1"
  local key="$2"
  awk -v key="$key" '
    index($0, key "=") == 1 {
      if (found) { duplicate = 1; next }
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
  actual="$(env_value "$file" "$key")" || \
    fail "missing or duplicate summary key '$key' in $file"
  [[ "$actual" == "$expected" ]] || \
    fail "summary key $key=$actual, expected=$expected in $file"
}

finalize_directory_manifest() {
  local directory="$1"
  (
    cd "$directory"
    find . -type f \
      ! -name SHA256SUMS \
      ! -name SHA256SUMS.sha256 \
      ! -name manifest-check.txt \
      ! -name outer-manifest-check.txt \
      -print0 | LC_ALL=C sort -z | xargs -0 sha256sum > SHA256SUMS
    sha256sum -c SHA256SUMS > manifest-check.txt
    sha256sum SHA256SUMS > SHA256SUMS.sha256
    sha256sum -c SHA256SUMS.sha256 > outer-manifest-check.txt
  )
}

register_lane() {
  local ordinal="$1"
  local lane="$2"
  local type="$3"
  local tests="$4"
  local reports="$5"
  local failures="$6"
  local errors="$7"
  local skipped="$8"
  local lane_dir="$RUN_ROOT/lanes/$lane"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$ordinal" "$lane" "$type" "$tests" "$reports" "$failures" \
    "$errors" "$skipped" "$lane_dir" >> "$LANES_TSV"
  TOTAL_TESTS=$((TOTAL_TESTS + tests))
  TOTAL_REPORTS=$((TOTAL_REPORTS + reports))
  TOTAL_FAILURES=$((TOTAL_FAILURES + failures))
  TOTAL_ERRORS=$((TOTAL_ERRORS + errors))
  TOTAL_SKIPPED=$((TOTAL_SKIPPED + skipped))
  LAST_COMPLETED_STEP="$ordinal-$lane"
}

assert_maven_success() {
  local log_file="$1"
  local build_success build_failure
  build_success="$(grep -c '^\[INFO\] BUILD SUCCESS' "$log_file" || true)"
  build_failure="$(grep -c '^\[INFO\] BUILD FAILURE' "$log_file" || true)"
  [[ "$build_success" -eq 1 ]] || fail "BUILD SUCCESS count=$build_success in $log_file"
  [[ "$build_failure" -eq 0 ]] || fail "BUILD FAILURE appears in $log_file"
}

assert_reactor_success_count() {
  local log_file="$1"
  local expected="$2"
  local actual
  actual="$(awk '
    /^\[INFO\] Reactor Summary for / { in_summary = 1; next }
    in_summary && /^\[INFO\] BUILD SUCCESS/ { in_summary = 0 }
    in_summary && /^\[INFO\].* SUCCESS \[[^]]+\][[:space:]]*$/ { count++ }
    END { print count + 0 }
  ' "$log_file")"
  [[ "$actual" -eq "$expected" ]] || \
    fail "reactor success count=$actual, expected=$expected in $log_file"
}

xml_attribute() {
  local suite_tag="$1"
  local name="$2"
  sed -n "s/.* ${name}=\"\([^\"]*\)\".*/\1/p" <<< "$suite_tag"
}

assert_fresh_reports() {
  local lane_dir="$1"
  local expected_reports="$2"
  local expected_tests="$3"
  local expected_skipped="$4"
  shift 4
  local marker="$lane_dir/.run-start"
  local reports_dir="$lane_dir/reports"
  local metrics="$lane_dir/report-metrics.tsv"
  local testcases="$lane_dir/testcases.tsv"
  local report suite_tag tests failures errors skipped relative fqcn testcase_tag name classname
  local total_tests=0 total_failures=0 total_errors=0 total_skipped=0 testcase_count=0
  local -a reports=()

  [[ -d "$reports_dir" ]] || fail "report directory is missing: $reports_dir"
  mapfile -d '' reports < <(
    find "$reports_dir" -maxdepth 1 -type f -name 'TEST-*.xml' -print0 | LC_ALL=C sort -z
  )
  [[ "${#reports[@]}" -gt 0 ]] || fail "no TEST-*.xml exists in $reports_dir"
  if [[ "$expected_reports" -gt 0 ]]; then
    [[ "${#reports[@]}" -eq "$expected_reports" ]] || \
      fail "lane $(basename "$lane_dir") reports=${#reports[@]}, expected=$expected_reports"
  fi
  [[ "$#" -eq 0 || "$#" -eq "$expected_reports" ]] || \
    fail "expected suite inventory count=$#, expected reports=$expected_reports"
  for fqcn in "$@"; do
    [[ -f "$reports_dir/TEST-$fqcn.xml" ]] || \
      fail "expected suite report is missing: TEST-$fqcn.xml"
  done

  printf '%s\n' $'report\ttests\tfailures\terrors\tskipped\tsha256' > "$metrics"
  printf '%s\n' $'report\tclassname\tname' > "$testcases"
  for report in "${reports[@]}"; do
    [[ "$report" -nt "$marker" ]] || fail "stale report can satisfy lane: $report"
    suite_tag="$(grep -o -m1 '<testsuite[^>]*>' "$report" || true)"
    [[ -n "$suite_tag" ]] || fail "testsuite element missing: $report"
    tests="$(xml_attribute "$suite_tag" tests)"
    failures="$(xml_attribute "$suite_tag" failures)"
    errors="$(xml_attribute "$suite_tag" errors)"
    skipped="$(xml_attribute "$suite_tag" skipped)"
    [[ "$tests" =~ ^[0-9]+$ && "$failures" =~ ^[0-9]+$ && \
       "$errors" =~ ^[0-9]+$ && "$skipped" =~ ^[0-9]+$ ]] || \
      fail "invalid report metrics: $report"
    relative="${report#$lane_dir/}"
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$relative" "$tests" "$failures" "$errors" "$skipped" \
      "$(sha256sum "$report" | awk '{print $1}')" >> "$metrics"
    while IFS= read -r testcase_tag; do
      name="$(sed -n 's/.* name="\([^"]*\)".*/\1/p' <<< "$testcase_tag")"
      classname="$(sed -n 's/.* classname="\([^"]*\)".*/\1/p' <<< "$testcase_tag")"
      printf '%s\t%s\t%s\n' "$relative" "$classname" "$name" >> "$testcases"
      testcase_count=$((testcase_count + 1))
    done < <(grep -o '<testcase[^>]*>' "$report" || true)
    total_tests=$((total_tests + tests))
    total_failures=$((total_failures + failures))
    total_errors=$((total_errors + errors))
    total_skipped=$((total_skipped + skipped))
  done
  [[ "$total_tests" -eq "$expected_tests" ]] || \
    fail "lane $(basename "$lane_dir") tests=$total_tests, expected=$expected_tests"
  [[ "$total_failures" -eq 0 ]] || fail "lane failures=$total_failures"
  [[ "$total_errors" -eq 0 ]] || fail "lane errors=$total_errors"
  [[ "$total_skipped" -eq "$expected_skipped" ]] || \
    fail "lane skipped=$total_skipped, expected=$expected_skipped"
  [[ "$testcase_count" -eq "$total_tests" ]] || \
    fail "testcase elements=$testcase_count, suite tests=$total_tests"
  printf '%s\n' "${#reports[@]}"
}

collect_suffixed_reports() {
  local lane_dir="$1"
  local suffix="$2"
  local source_root="$3"
  local expected_reports="$4"
  local source_report base canonical
  local -a source_reports=()

  mapfile -d '' source_reports < <(
    find "$source_root" -type f \
      -path '*/target/surefire-reports/TEST-*.xml' \
      -name "TEST-*-$suffix.xml" \
      -newer "$lane_dir/.run-start" -print0 | LC_ALL=C sort -z
  )
  [[ "${#source_reports[@]}" -eq "$expected_reports" ]] || \
    fail "lane $(basename "$lane_dir") suffixed reports=${#source_reports[@]}, expected=$expected_reports"
  for source_report in "${source_reports[@]}"; do
    base="$(basename "$source_report")"
    canonical="${base%-$suffix.xml}.xml"
    [[ "$canonical" != "$base" ]] || fail "could not normalize suffixed report: $base"
    [[ ! -e "$lane_dir/reports/$canonical" ]] || \
      fail "duplicate canonical report in lane $(basename "$lane_dir"): $canonical"
    cp -p "$source_report" "$lane_dir/reports/$canonical"
  done
}

assert_sqlite_skip_allowlist() {
  local lane_dir="$1"
  local observed="$lane_dir/skipped-testcases.txt"
  local expected="$lane_dir/expected-skipped-testcases.txt"
  local report fqcn line current_name

  : > "$observed"
  while IFS= read -r -d '' report; do
    fqcn="$(basename "$report")"
    fqcn="${fqcn#TEST-}"
    fqcn="${fqcn%.xml}"
    current_name=""
    while IFS= read -r line; do
      if [[ "$line" == *'<testcase '* ]]; then
        current_name="$(sed -n 's/.* name="\([^"]*\)".*/\1/p' <<< "$line")"
      fi
      if [[ "$line" == *'<skipped'* ]]; then
        [[ -n "$current_name" ]] || fail "skipped element without testcase name in $report"
        printf '%s#%s\n' "$fqcn" "$current_name" >> "$observed"
      fi
    done < "$report"
  done < <(find "$lane_dir/reports" -maxdepth 1 -type f -name 'TEST-*.xml' \
    -print0 | LC_ALL=C sort -z)
  LC_ALL=C sort -o "$observed" "$observed"
  cat > "$expected" <<'SKIPS'
com.foggyframework.dataset.model.ecommerce.CalculateMvpIntegrationTest#calculateFailsClosedForRuntimeUnsupportedDatabase
com.foggyframework.dataset.model.engine.pivot.PivotCascadeGenerateSqlParityIntegrationTest#testMysql57RowsCascadeFailsClosedWithoutMemoryFallback
com.foggyframework.dataset.model.parity.JavaQueryModelAggregateJoinSnapshotTest#shouldProduceSnapshot
SKIPS
  LC_ALL=C sort -o "$expected" "$expected"
  diff -u "$expected" "$observed" > "$lane_dir/skipped-allowlist.diff" || \
    fail "SQLite skipped testcase allowlist drifted"
}

run_surefire_lane() {
  local ordinal="$1"
  local lane="$2"
  local modules="$3"
  local tests_csv="$4"
  local expected_reports="$5"
  local expected_tests="$6"
  local expected_skipped="$7"
  shift 7
  local -a expected_fqcns=("$@")
  local lane_dir="$RUN_ROOT/lanes/$lane"
  local log_file="$lane_dir/maven.log"
  local actual_reports
  local suffix="batch7-$RUN_ID-$ordinal"

  CURRENT_STEP="$ordinal-$lane"
  assert_no_repository_maven
  mkdir -p "$lane_dir/reports"
  : > "$lane_dir/.run-start"
  echo "[v933-batch7] running lane=$lane tests=$expected_tests reports=$expected_reports"
  if ! (cd "$ROOT_DIR" && mvn -B -pl "$modules" -am '-P!multi-db' \
      -Dtest="$tests_csv" \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -Dsurefire.reportNameSuffix="$suffix" \
      test -l "$log_file"); then
    fail "Maven lane failed: $lane; log=$log_file"
  fi
  assert_maven_success "$log_file"
  collect_suffixed_reports "$lane_dir" "$suffix" "$ROOT_DIR" "$expected_reports"
  actual_reports="$(assert_fresh_reports "$lane_dir" "$expected_reports" \
    "$expected_tests" "$expected_skipped" "${expected_fqcns[@]}")"
  cat > "$lane_dir/summary.env" <<SUMMARY
lane=$lane
status=passed
tests=$expected_tests
reports=$actual_reports
failures=0
errors=0
skipped=$expected_skipped
SUMMARY
  finalize_directory_manifest "$lane_dir"
  register_lane "$ordinal" "$lane" surefire "$expected_tests" "$actual_reports" \
    0 0 "$expected_skipped"
}

run_compile_lane() {
  local ordinal="01"
  local lane="root-compile"
  local lane_dir="$RUN_ROOT/lanes/$lane"
  local log_file="$lane_dir/maven.log"
  CURRENT_STEP="$ordinal-$lane"
  assert_no_repository_maven
  mkdir -p "$lane_dir"
  : > "$lane_dir/.run-start"
  echo "[v933-batch7] running root compile (25 reactor modules)"
  if ! (cd "$ROOT_DIR" && mvn -B '-P!multi-db' -DskipTests compile -l "$log_file"); then
    fail "root compile failed; log=$log_file"
  fi
  assert_maven_success "$log_file"
  assert_reactor_success_count "$log_file" 25
  [[ "$(grep -c '^\[INFO\] Running ' "$log_file" || true)" -eq 0 ]] || \
    fail "compile lane unexpectedly ran tests"
  cat > "$lane_dir/summary.env" <<SUMMARY
lane=$lane
status=passed
reactor_modules=25
tests=0
reports=0
SUMMARY
  finalize_directory_manifest "$lane_dir"
  register_lane "$ordinal" "$lane" compile 0 0 0 0 0
}

run_real_query_lane() {
  local ordinal="05"
  local lane="real-query"
  local lane_dir="$RUN_ROOT/lanes/$lane"
  local log_file="$lane_dir/child.log"
  local child_id="$RUN_ID-$ordinal-real-query"
  local child_root="$ROOT_DIR/target/v933-batch6-real-query/runs/$child_id"
  local summary="$child_root/summary.env"
  local -a pipeline_status
  local child_rc tee_rc

  CURRENT_STEP="$ordinal-$lane"
  assert_no_repository_maven
  mkdir -p "$lane_dir"
  : > "$lane_dir/.run-start"
  [[ ! -e "$child_root" ]] || fail "real-query child run already exists: $child_root"
  echo "[v933-batch7] running Batch 6 REAL-QUERY child (11 tests / 6 reports)"
  set +e
  (cd "$ROOT_DIR" && "$REAL_QUERY_SCRIPT" "$child_id") 2>&1 | tee "$log_file"
  pipeline_status=("${PIPESTATUS[@]}")
  set -e
  child_rc="${pipeline_status[0]:-1}"
  tee_rc="${pipeline_status[1]:-1}"
  [[ "$child_rc" -eq 0 ]] || fail "REAL-QUERY child failed: exit=$child_rc"
  [[ "$tee_rc" -eq 0 ]] || fail "could not retain REAL-QUERY child output"
  [[ -f "$summary" ]] || fail "REAL-QUERY child summary is missing"
  expect_env "$summary" run_id "$child_id"
  expect_env "$summary" status passed
  expect_env "$summary" criterion REAL-QUERY
  expect_env "$summary" total_tests 11
  expect_env "$summary" owning_reports 6
  expect_env "$summary" failures 0
  expect_env "$summary" errors 0
  expect_env "$summary" skipped 0
  (cd "$child_root" && sha256sum -c SHA256SUMS) \
    > "$lane_dir/child-manifest-check.txt"
  (cd "$child_root" && sha256sum -c SHA256SUMS.sha256) \
    > "$lane_dir/child-outer-manifest-check.txt"
  cp -p "$summary" "$lane_dir/child-summary.env"
  cp -p "$child_root/SHA256SUMS" "$lane_dir/child-SHA256SUMS"
  cp -p "$child_root/SHA256SUMS.sha256" "$lane_dir/child-SHA256SUMS.sha256"
  printf '%s\n' "$child_root" > "$lane_dir/child-run-root.txt"
  printf '%s\n' $'report\tclassname\tname' > "$lane_dir/testcases.tsv"
  while IFS= read -r -d '' report; do
    while IFS= read -r testcase_tag; do
      printf '%s\t%s\t%s\n' \
        "${report#$child_root/}" \
        "$(sed -n 's/.* classname="\([^"]*\)".*/\1/p' <<< "$testcase_tag")" \
        "$(sed -n 's/.* name="\([^"]*\)".*/\1/p' <<< "$testcase_tag")" \
        >> "$lane_dir/testcases.tsv"
    done < <(grep -o '<testcase[^>]*>' "$report" || true)
  done < <(find "$child_root/lanes" -type f -path '*/failsafe-reports/TEST-*.xml' \
    -print0 | LC_ALL=C sort -z)
  cat > "$lane_dir/summary.env" <<SUMMARY
lane=$lane
status=passed
tests=11
reports=6
failures=0
errors=0
skipped=0
child_run_id=$child_id
child_run_root=$child_root
SUMMARY
  finalize_directory_manifest "$lane_dir"
  register_lane "$ordinal" "$lane" failsafe-child 11 6 0 0 0
}

run_sqlite_full_lane() {
  local ordinal="06"
  local lane="sqlite-full"
  local lane_dir="$RUN_ROOT/lanes/$lane"
  local log_file="$lane_dir/maven.log"
  local actual_reports
  local suffix="batch7-$RUN_ID-$ordinal"
  CURRENT_STEP="$ordinal-$lane"
  assert_no_repository_maven
  mkdir -p "$lane_dir/reports"
  : > "$lane_dir/.run-start"
  echo "[v933-batch7] running reviewed SQLite full suite tests=$SQLITE_EXPECTED_TESTS skips=3"
  if ! (cd "$ROOT_DIR" && mvn -B -pl foggy-dataset-model-engine '-P!multi-db' \
      -Dspring.profiles.active=sqlite \
      -Dsurefire.reportNameSuffix="$suffix" \
      test -l "$log_file"); then
    fail "SQLite full suite failed; log=$log_file"
  fi
  assert_maven_success "$log_file"
  collect_suffixed_reports "$lane_dir" "$suffix" \
    "$ROOT_DIR/foggy-dataset-model-engine" "$SQLITE_EXPECTED_REPORTS"
  actual_reports="$(assert_fresh_reports "$lane_dir" "$SQLITE_EXPECTED_REPORTS" \
    "$SQLITE_EXPECTED_TESTS" 3)"
  assert_sqlite_skip_allowlist "$lane_dir"
  SQLITE_ACTUAL_REPORTS="$actual_reports"
  cat > "$lane_dir/summary.env" <<SUMMARY
lane=$lane
status=passed
profile=sqlite
expected_tests=$SQLITE_EXPECTED_TESTS
tests=$SQLITE_EXPECTED_TESTS
reports=$actual_reports
failures=0
errors=0
skipped=3
count_policy=reviewed-and-frozen-before-run
SUMMARY
  finalize_directory_manifest "$lane_dir"
  register_lane "$ordinal" "$lane" surefire-full "$SQLITE_EXPECTED_TESTS" \
    "$actual_reports" 0 0 3
}

assert_database_container() {
  local kind="$1"
  local lane_dir="$2"
  local name image container_port host_port profile catalog schema version_output
  case "$kind" in
    mysql57)
      name="foggy-demo-mysql"; image="mysql:5.7"; container_port=3306; host_port=13306
      profile="docker"; catalog="foggy_test"; schema="<none>"
      version_output="$(docker exec "$name" mysql --version 2>&1)" || \
        fail "could not probe MySQL version"
      [[ "$version_output" == *"5.7"* ]] || fail "MySQL version probe mismatch: $version_output"
      ;;
    postgres15)
      name="foggy-demo-postgres"; image="postgres:15-alpine"; container_port=5432; host_port=15432
      profile="postgres"; catalog="foggy_test"; schema="public"
      version_output="$(docker exec "$name" postgres --version 2>&1)" || \
        fail "could not probe PostgreSQL version"
      [[ "$version_output" == *"15."* ]] || fail "PostgreSQL version probe mismatch: $version_output"
      ;;
    sqlserver2022)
      name="foggy-demo-sqlserver"; image="mcr.microsoft.com/mssql/server:2022-latest"
      container_port=1433; host_port=11433; profile="sqlserver"; catalog="foggy_test"; schema="dbo"
      version_output="$(docker exec "$name" bash -lc \
        'SQLCMDPASSWORD="$MSSQL_SA_PASSWORD" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -d foggy_test -h -1 -W -Q "SET NOCOUNT ON; SELECT CAST(SERVERPROPERTY('\''ProductVersion'\'') AS nvarchar(128));"')" || \
        fail "could not probe SQL Server version"
      [[ "$version_output" == *"16."* || "$version_output" == *"2022"* ]] || \
        fail "SQL Server version probe mismatch: $version_output"
      ;;
    *) fail "unknown database kind: $kind" ;;
  esac
  docker inspect "$name" >/dev/null 2>&1 || fail "required container is missing: $name"
  local actual_image health port container_id image_id
  actual_image="$(docker inspect -f '{{.Config.Image}}' "$name")"
  health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$name")"
  port="$(docker port "$name" "$container_port/tcp" | head -n 1)"
  container_id="$(docker inspect -f '{{.Id}}' "$name")"
  image_id="$(docker inspect -f '{{.Image}}' "$name")"
  [[ "$actual_image" == "$image" ]] || fail "$name image=$actual_image, expected=$image"
  [[ "$health" == "healthy" ]] || fail "$name health=$health"
  [[ "$port" == *":$host_port" ]] || fail "$name mapped port=$port, expected=$host_port"
  version_output="$(tr '\n\r\t' '   ' <<< "$version_output" | sed 's/[[:space:]][[:space:]]*/ /g')"
  cat > "$lane_dir/database.env" <<DATABASE
kind=$kind
product_image=$actual_image
product_version=$version_output
container=$name
container_id=$container_id
image_id=$image_id
health=$health
mapped_port=$port
spring_profile=$profile
catalog=$catalog
schema=$schema
config_sha256=$(sha256sum "$ROOT_DIR/foggy-dataset-model-engine/src/test/resources/application-$profile.yml" | awk '{print $1}')
DATABASE
}

capture_database_fixture_probe() {
  local kind="$1"
  local output="$2"
  local raw version catalog schema fact_sales dim_product dict_status expected
  case "$kind" in
    mysql57)
      raw="$(docker exec foggy-demo-mysql sh -lc \
        'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B "$MYSQL_DATABASE" -e "SELECT VERSION(), DATABASE(), (SELECT COUNT(*) FROM fact_sales), (SELECT COUNT(*) FROM dim_product), (SELECT COUNT(*) FROM dict_status);" 2>/dev/null')" || \
        fail "could not query MySQL fixture identity"
      raw="$(tr '\t\r\n' '|| ' <<< "$raw" | sed 's/[[:space:]]*$//')"
      IFS='|' read -r version catalog fact_sales dim_product dict_status <<< "$raw"
      schema="<none>"
      expected='5.7.44-log|foggy_test|<none>|110317|500|48'
      ;;
    postgres15)
      version="$(docker exec foggy-demo-postgres postgres --version \
        | sed -n 's/.* \([0-9][0-9.]*\)$/\1/p')" || \
        fail "could not query PostgreSQL version"
      raw="$(docker exec foggy-demo-postgres sh -lc \
        'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -F "|" -c "SELECT current_database(), current_schema(), (SELECT COUNT(*) FROM fact_sales), (SELECT COUNT(*) FROM dim_product), (SELECT COUNT(*) FROM dict_status);"')" || \
        fail "could not query PostgreSQL fixture identity"
      raw="$(tr -d '\r\n' <<< "$raw")"
      IFS='|' read -r catalog schema fact_sales dim_product dict_status <<< "$raw"
      expected='15.17|foggy_test|public|17384|500|48'
      ;;
    sqlserver2022)
      raw="$(docker exec foggy-demo-sqlserver bash -lc \
        'SQLCMDPASSWORD="$MSSQL_SA_PASSWORD" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -d foggy_test -h -1 -W -s "|" -Q "SET NOCOUNT ON; SELECT CAST(SERVERPROPERTY('\''ProductVersion'\'') AS nvarchar(128)), DB_NAME(), SCHEMA_NAME(), (SELECT COUNT_BIG(*) FROM dbo.fact_sales), (SELECT COUNT_BIG(*) FROM dbo.dim_product), (SELECT COUNT_BIG(*) FROM dbo.dict_status);"')" || \
        fail "could not query SQL Server fixture identity"
      raw="$(tr -d '\r' <<< "$raw" | sed '/^[[:space:]]*$/d' | tail -n 1)"
      IFS='|' read -r version catalog schema fact_sales dim_product dict_status <<< "$raw"
      expected='16.0.4236.2|foggy_test|dbo|5940|500|48'
      ;;
    *) fail "unknown database fixture kind: $kind" ;;
  esac
  local observed="$version|$catalog|$schema|$fact_sales|$dim_product|$dict_status"
  [[ "$observed" == "$expected" ]] || \
    fail "$kind fixture probe=$observed, expected=$expected"
  cat > "$output" <<PROBE
kind|version|catalog|schema|fact_sales|dim_product|dict_status
$kind|$version|$catalog|$schema|$fact_sales|$dim_product|$dict_status
PROBE
}

run_multidb_lane() {
  local ordinal="$1"
  local kind="$2"
  local profile="$3"
  local lane="multidb-$kind"
  local lane_dir="$RUN_ROOT/lanes/$lane"
  local log_file="$lane_dir/maven.log"
  local fqcn="com.foggyframework.dataset.model.multidb.MultiDatabaseQueryTest"
  local container_id_before image_id_before container_id_after image_id_after container
  local suffix="batch7-$RUN_ID-$ordinal"
  CURRENT_STEP="$ordinal-$lane"
  assert_no_repository_maven
  mkdir -p "$lane_dir/reports"
  : > "$lane_dir/.run-start"
  assert_database_container "$kind" "$lane_dir"
  capture_database_fixture_probe "$kind" "$lane_dir/fixture-before.tsv"
  container_id_before="$(env_value "$lane_dir/database.env" container_id)"
  image_id_before="$(env_value "$lane_dir/database.env" image_id)"
  echo "[v933-batch7] running MultiDatabaseQueryTest kind=$kind tests=18"
  if ! (cd "$ROOT_DIR" && mvn -B test -pl foggy-dataset-model-engine '-P!multi-db' \
      -Dtest=MultiDatabaseQueryTest \
      -Dspring.profiles.active="$profile" \
      -Dsurefire.reportNameSuffix="$suffix" \
      -l "$log_file"); then
    fail "MultiDatabaseQueryTest failed for $kind; log=$log_file"
  fi
  assert_maven_success "$log_file"
  collect_suffixed_reports "$lane_dir" "$suffix" \
    "$ROOT_DIR/foggy-dataset-model-engine" 1
  assert_fresh_reports "$lane_dir" 1 18 0 "$fqcn" >/dev/null
  case "$kind" in
    mysql57) container="foggy-demo-mysql" ;;
    postgres15) container="foggy-demo-postgres" ;;
    sqlserver2022) container="foggy-demo-sqlserver" ;;
  esac
  container_id_after="$(docker inspect -f '{{.Id}}' "$container")"
  image_id_after="$(docker inspect -f '{{.Image}}' "$container")"
  [[ "$container_id_after" == "$container_id_before" ]] || fail "$kind container changed during lane"
  [[ "$image_id_after" == "$image_id_before" ]] || fail "$kind image changed during lane"
  capture_database_fixture_probe "$kind" "$lane_dir/fixture-after.tsv"
  cmp -s "$lane_dir/fixture-before.tsv" "$lane_dir/fixture-after.tsv" || \
    fail "$kind fixture identity/counts changed during lane"
  cat > "$lane_dir/summary.env" <<SUMMARY
lane=$lane
status=passed
tests=18
reports=1
failures=0
errors=0
skipped=0
database_kind=$kind
spring_profile=$profile
SUMMARY
  finalize_directory_manifest "$lane_dir"
  register_lane "$ordinal" "$lane" real-database-surefire 18 1 0 0 0
}

run_package_and_artifact_audit() {
  local ordinal="12"
  local lane="root-package-artifacts"
  local lane_dir="$RUN_ROOT/lanes/$lane"
  local log_file="$lane_dir/maven.log"
  local marker="$lane_dir/.run-start"
  local module artifact source_imports source_factories imports_entry factories_entry jar_path
  local imports_jar_count=0 imports_entry_count=0 forbidden_count=0
  local launcher_jar launcher_manifest nested_entry nested_base nested_hash local_path local_hash
  local -a candidates=() nested_entries=() local_matches=()
  local -a modules=(
    foggy-bean-copy foggy-core foggy-mcp-spi foggy-dataset foggy-dataset-demo
    foggy-dataset-mcp foggy-dataset-model-engine foggy-runtime-api
    foggy-dataset-memory-grid-bridge foggy-dataset-memory-grid-duckdb
    foggy-fsscript foggy-mcp-launcher addons/foggy-fsscript-client
    addons/foggy-dataset-client addons/foggy-dataset-mongo
    addons/foggy-dataset-model-mongo addons/foggy-dataset-model-cache
    addons/foggy-dataset-model-vector addons/foggy-data-viewer
    addons/foggy-chart-storage-cloud addons/foggy-dataset-vector
    addons/foggy-dataset-graphql addons/foggy-dataset-model-preagg
    addons/foggy-odoo-bridge-java
  )

  CURRENT_STEP="$ordinal-$lane"
  assert_no_repository_maven
  mkdir -p "$lane_dir/artifacts/metadata" "$lane_dir/artifacts/nested"
  : > "$marker"
  echo "[v933-batch7] running root package and packaged metadata audit"
  if ! (cd "$ROOT_DIR" && mvn -B '-P!multi-db' -DskipUnitTests=true -DskipTests \
      -Dmaven.jar.forceCreation=true package -l "$log_file"); then
    fail "root package failed; log=$log_file"
  fi
  assert_maven_success "$log_file"
  assert_reactor_success_count "$log_file" 25
  [[ "$(grep -c '^\[INFO\] Running ' "$log_file" || true)" -eq 0 ]] || \
    fail "package lane unexpectedly ran tests"
  printf '%s\n' $'module\tjar\tsha256' > "$lane_dir/artifacts/main-jars.tsv"
  : > "$lane_dir/artifacts/auto-configuration-imports.txt"
  : > "$lane_dir/artifacts/forbidden-enable-auto-configuration.txt"

  for module in "${modules[@]}"; do
    artifact="$(basename "$module")"
    mapfile -t candidates < <(
      find "$ROOT_DIR/$module/target" -maxdepth 1 -type f \
        -name "$artifact-*.jar" \
        ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*-tests.jar' \
        ! -name '*.original' -newer "$marker" | LC_ALL=C sort
    )
    [[ "${#candidates[@]}" -eq 1 ]] || \
      fail "$module fresh main JAR count=${#candidates[@]}, expected=1"
    jar_path="${candidates[0]}"
    printf '%s\t%s\t%s\n' "$module" "$jar_path" \
      "$(sha256sum "$jar_path" | awk '{print $1}')" \
      >> "$lane_dir/artifacts/main-jars.tsv"

    source_imports="$ROOT_DIR/$module/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
    imports_entry="META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
    if [[ -f "$source_imports" ]]; then
      unzip -Z1 "$jar_path" | grep -Fx "$imports_entry" >/dev/null || \
        fail "$module packaged AutoConfiguration.imports is missing"
      unzip -p "$jar_path" "$imports_entry" \
        > "$lane_dir/artifacts/metadata/$artifact.AutoConfiguration.imports"
      cmp -s "$source_imports" "$lane_dir/artifacts/metadata/$artifact.AutoConfiguration.imports" || \
        fail "$module packaged AutoConfiguration.imports differs from source"
      awk -v module="$module" \
        '!/^[[:space:]]*#/ && !/^[[:space:]]*$/ { print module "\t" $0 }' \
        "$source_imports" >> "$lane_dir/artifacts/auto-configuration-imports.txt"
      imports_jar_count=$((imports_jar_count + 1))
      imports_entry_count=$((imports_entry_count + $(awk \
        '!/^[[:space:]]*#/ && !/^[[:space:]]*$/ { count++ } \
         END { print count + 0 }' "$source_imports")))
    elif unzip -Z1 "$jar_path" | grep -Fx "$imports_entry" >/dev/null; then
      fail "$module packages AutoConfiguration.imports without a source resource"
    fi

    source_factories="$ROOT_DIR/$module/src/main/resources/META-INF/spring.factories"
    factories_entry="META-INF/spring.factories"
    if [[ -f "$source_factories" ]]; then
      unzip -Z1 "$jar_path" | grep -Fx "$factories_entry" >/dev/null || \
        fail "$module packaged spring.factories is missing"
      unzip -p "$jar_path" "$factories_entry" \
        > "$lane_dir/artifacts/metadata/$artifact.spring.factories"
      cmp -s "$source_factories" "$lane_dir/artifacts/metadata/$artifact.spring.factories" || \
        fail "$module packaged spring.factories differs from source"
      if rg -n 'org\.springframework\.boot\.autoconfigure\.EnableAutoConfiguration' \
          "$lane_dir/artifacts/metadata/$artifact.spring.factories" \
          >> "$lane_dir/artifacts/forbidden-enable-auto-configuration.txt"; then
        forbidden_count=$((forbidden_count + 1))
      fi
    elif unzip -Z1 "$jar_path" | grep -Fx "$factories_entry" >/dev/null; then
      fail "$module packages spring.factories without a source resource"
    fi
  done
  [[ "${#modules[@]}" -eq 24 ]] || fail "internal module inventory drift"
  [[ "$imports_jar_count" -eq 17 ]] || \
    fail "packaged imports JAR count=$imports_jar_count, expected=17"
  [[ "$imports_entry_count" -eq 21 ]] || \
    fail "packaged auto-configuration entry count=$imports_entry_count, expected=21"
  [[ "$(wc -l < "$lane_dir/artifacts/auto-configuration-imports.txt")" -eq 21 ]] || \
    fail "packaged auto-configuration evidence row count drifted"
  cut -f2 "$lane_dir/artifacts/auto-configuration-imports.txt" \
    | LC_ALL=C sort | uniq -d \
    > "$lane_dir/artifacts/duplicate-auto-configuration-imports.txt"
  [[ ! -s "$lane_dir/artifacts/duplicate-auto-configuration-imports.txt" ]] || \
    fail "packaged AutoConfiguration.imports contains duplicate entries"
  [[ "$forbidden_count" -eq 0 ]] || \
    fail "legacy EnableAutoConfiguration remains in packaged spring.factories"

  launcher_jar="$(awk -F '\t' '$1 == "foggy-mcp-launcher" { print $2 }' \
    "$lane_dir/artifacts/main-jars.tsv")"
  [[ -f "$launcher_jar" ]] || fail "fresh Launcher fat JAR is missing"
  unzip -p "$launcher_jar" META-INF/MANIFEST.MF > "$lane_dir/artifacts/launcher-manifest.txt"
  grep -F 'Main-Class: org.springframework.boot.loader.launch.JarLauncher' \
    "$lane_dir/artifacts/launcher-manifest.txt" >/dev/null || \
    fail "Launcher Boot Main-Class is missing"
  grep -F 'Start-Class: com.foggyframework.mcp.launcher.McpLauncherApplication' \
    "$lane_dir/artifacts/launcher-manifest.txt" >/dev/null || \
    fail "Launcher Start-Class is missing"
  mapfile -t nested_entries < <(
    unzip -Z1 "$launcher_jar" | grep '^BOOT-INF/lib/foggy-.*\.jar$' | LC_ALL=C sort
  )
  [[ "${#nested_entries[@]}" -eq 12 ]] || \
    fail "Launcher local nested JAR count=${#nested_entries[@]}, expected=12"
  printf '%s\n' $'nested_entry\tlocal_jar\tnested_sha256\tlocal_sha256\tmatch' \
    > "$lane_dir/artifacts/nested-checksums.tsv"
  for nested_entry in "${nested_entries[@]}"; do
    nested_base="$(basename "$nested_entry")"
    mapfile -t local_matches < <(
      awk -F '\t' -v base="$nested_base" 'NR > 1 && $2 ~ ("/" base "$") { print $2 }' \
        "$lane_dir/artifacts/main-jars.tsv"
    )
    [[ "${#local_matches[@]}" -eq 1 ]] || \
      fail "nested $nested_base local artifact matches=${#local_matches[@]}, expected=1"
    local_path="${local_matches[0]}"
    unzip -p "$launcher_jar" "$nested_entry" > "$lane_dir/artifacts/nested/$nested_base"
    nested_hash="$(sha256sum "$lane_dir/artifacts/nested/$nested_base" | awk '{print $1}')"
    local_hash="$(sha256sum "$local_path" | awk '{print $1}')"
    [[ "$nested_hash" == "$local_hash" ]] || \
      fail "Launcher nested checksum mismatch: $nested_base"
    printf '%s\t%s\t%s\t%s\ttrue\n' \
      "$nested_entry" "$local_path" "$nested_hash" "$local_hash" \
      >> "$lane_dir/artifacts/nested-checksums.tsv"
  done
  cat > "$lane_dir/summary.env" <<SUMMARY
lane=$lane
status=passed
reactor_modules=25
tests=0
reports=0
main_jars=24
auto_configuration_import_jars=17
auto_configuration_entries=21
legacy_enable_auto_configuration_entries=0
launcher_nested_local_jars=12
launcher_nested_checksum_matches=12
launcher_sha256=$(sha256sum "$launcher_jar" | awk '{print $1}')
SUMMARY
  finalize_directory_manifest "$lane_dir"
  register_lane "$ordinal" "$lane" package-artifact-audit 0 0 0 0 0
}

capture_source_state() {
  local phase="$1"
  git -C "$ROOT_DIR" rev-parse HEAD > "$RUN_ROOT/source-audit/git-head-$phase.txt"
  git -C "$ROOT_DIR" status --short --untracked-files=all \
    > "$RUN_ROOT/source-audit/git-status-$phase.txt"
  git -C "$ROOT_DIR" diff --binary --no-ext-diff HEAD | sha256sum | awk '{print $1}' \
    > "$RUN_ROOT/source-audit/git-diff-$phase.sha256"
  local list_file="$RUN_ROOT/source-audit/.untracked-$phase.list0"
  git -C "$ROOT_DIR" ls-files --others --exclude-standard -z | LC_ALL=C sort -z \
    > "$list_file"
  sha256sum "$list_file" | awk '{print $1}' \
    > "$RUN_ROOT/source-audit/untracked-list-$phase.sha256"
  (
    cd "$ROOT_DIR"
    while IFS= read -r -d '' path; do
      printf '%s\0' "$path"
      if [[ -L "$path" ]]; then
        printf 'symlink\0'; readlink -n -- "$path"; printf '\0'
      elif [[ -f "$path" ]]; then
        printf 'file\0%s\0' "$(sha256sum -- "$path" | awk '{print $1}')"
      else
        printf 'missing\0'
      fi
    done < "$list_file"
  ) | sha256sum | awk '{print $1}' \
    > "$RUN_ROOT/source-audit/untracked-content-$phase.sha256"
  rm -f "$list_file"
}

capture_source_manifest() {
  local list_file="$RUN_ROOT/source-audit/source-files.list0"
  (
    cd "$ROOT_DIR"
    {
      find scripts -maxdepth 1 -type f -name '*.sh' -print0
      find . -type d \( -name .git -o -name target \) -prune -o \
        -type f -name pom.xml -print0
      find foggy-* addons -type d -name target -prune -o \
        -type f \( -path '*/src/*' -o -name pom.xml \) -print0
      find docs/9.3.1 docs/9.3.2 docs/9.3.3 -type f -print0
      find .github/workflows -maxdepth 1 -type f -print0 2>/dev/null || true
    } | LC_ALL=C sort -zu > "$list_file"
    xargs -0 sha256sum < "$list_file"
  ) > "$RUN_ROOT/source-audit/source-files.sha256"
}

capture_fixed_containers() {
  local phase="$1"
  local output="$RUN_ROOT/environment/fixed-containers-$phase.tsv"
  local name
  printf '%s\n' $'name\timage\tcontainer_id\timage_id\tstate\thealth' > "$output"
  for name in foggy-demo-mysql foggy-demo-postgres foggy-demo-sqlserver; do
    docker inspect "$name" >/dev/null 2>&1 || fail "required fixed container is missing: $name"
    docker inspect -f \
      '{{.Name}}|{{.Config.Image}}|{{.Id}}|{{.Image}}|{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
      "$name" | sed 's#^/##;s/|/\t/g' >> "$output"
  done
}

# Frozen owning-suite inventories.
API_FQCNS=(
  com.foggyframework.runtime.api.RuntimeCapabilitiesControllerEnabledTest
  com.foggyframework.runtime.api.controller.RuntimeModelsControllerCompatibilityTest
  com.foggyframework.runtime.api.dto.RuntimeLifecycleDtoContractTest
  com.foggyframework.runtime.api.dto.RuntimeLifecycleSanitizerTest
  com.foggyframework.runtime.api.dto.RuntimeLifecycleSafetyContractTest
  com.foggyframework.runtime.api.service.RuntimeLifecycleErrorMappingTest
)
WATCHER_FQCNS=(
  com.foggyframework.core.utils.file.WatchServiceFileTracerTest
  com.foggyframework.bundle.dynamic.DynamicBundleLifecycleTest
  com.foggyframework.bundle.dynamic.DynamicBundleManagementTest
  com.foggyframework.fsscript.loadder.FsscriptFileChangeHandlerAuthorityTest
)
BINDING_FQCNS=(
  com.foggyframework.runtime.api.service.RuntimeDatasourceBindingLifecycleTest
  com.foggyframework.runtime.api.service.RuntimeNamedDataSourceResolverBindingTest
)
ISOLATION_FQCNS=(
  com.foggyframework.dataset.model.cache.fingerprint.QueryFingerprintBuilderTest
  com.foggyframework.dataset.model.cache.fingerprint.QueryFingerprintTest
  com.foggyframework.dataset.model.cache.fingerprint.StableCanonicalEncoderTest
  com.foggyframework.dataset.model.cache.provider.CaffeineQueryCacheProviderTest
  com.foggyframework.dataset.model.cache.provider.CaffeineQueryCacheProviderDataSourceIsolationIntegrationTest
  com.foggyframework.dataset.model.cache.provider.RedisQueryCacheProviderTest
  com.foggyframework.dataset.model.impl.loader.TableModelLoaderManagerImplDataSourceResolutionTest
  com.foggyframework.dataset.model.config.DbModelAutoConfigurationTest
  com.foggyframework.dataset.model.plugins.result_set_filter.DataSetResultStepExecutorOrderingTest
  com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionStepOrderingTest
  com.foggyframework.dataset.model.preagg.PreAggregationL2CacheIntegrationTest
  com.foggyframework.dataset.mcp.controller.DevToolsControllerIsolationTest
  com.foggyframework.dataset.model.semantic.controller.SemanticServiceV3TestControllerIsolationTest
)
AUTOCONFIG_FQCNS=(
  io.foggytest.autoconfigure.mongo.DataSetMongoAutoConfigurationContextTest
  io.foggytest.autoconfigure.modelmongo.MongoModelAutoConfigurationContractTest
  io.foggytest.autoconfigure.vector.DataSetVectorAutoConfigurationContextTest
  io.foggytest.autoconfigure.modelvector.VectorModelAutoConfigurationContractTest
  io.foggytest.autoconfigure.cache.QueryCacheAutoConfigurationContextTest
  com.foggyframework.dataset.graphql.GraphqlAddonAutoConfigurationTest
  com.foggyframework.dataset.mcp.storage.cloud.CloudStorageAutoConfigurationTest
  com.foggyframework.dataviewer.config.DataViewerAutoConfigurationContextTest
  com.foggyframework.dataset.model.config.GlobalNamespaceFallbackRiskDiagnosticTest
  io.foggytest.autoconfigure.AutoConfigurationBoundaryContractTest
  io.foggytest.autoconfigure.AutoConfigurationRegistrationUniquenessTest
  io.foggytest.autoconfigure.OutsidePackageCoreAutoConfigurationSmokeTest
  io.foggytest.autoconfigure.FullAddonAutoConfigurationAssemblyTest
  io.foggytest.launcher.LauncherDefaultRouteIsolationSmokeTest
  io.foggytest.launcher.LauncherExplicitTestRoutesSmokeTest
)

join_csv() {
  local IFS=,
  printf '%s' "$*"
}

assert_frozen_source_test_count() {
  local label="$1"
  local expected="$2"
  shift 2
  local fqcn source_path count
  local actual=0
  for fqcn in "$@"; do
    source_path="$(rg -l "(class|interface|record)[[:space:]]+${fqcn##*.}([[:space:]]|$)" \
      "$ROOT_DIR" -g '*.java' | head -n 1 || true)"
    [[ -n "$source_path" ]] || fail "frozen test source is missing: $fqcn"
    count="$(rg -c '^[[:space:]]*@(Test|ParameterizedTest|RepeatedTest)(\(|[[:space:]]*$)' \
      "$source_path" || true)"
    actual=$((actual + ${count:-0}))
  done
  [[ "$actual" -eq "$expected" ]] || \
    fail "$label source test annotations=$actual, expected=$expected"
}

for fqcn in "${API_FQCNS[@]}" "${WATCHER_FQCNS[@]}" "${BINDING_FQCNS[@]}" \
    "${ISOLATION_FQCNS[@]}" "${AUTOCONFIG_FQCNS[@]}"; do
  source_path="$(rg -l "(class|interface|record)[[:space:]]+${fqcn##*.}([[:space:]]|$)" \
    "$ROOT_DIR" -g '*.java' | head -n 1 || true)"
  [[ -n "$source_path" ]] || fail "frozen test source is missing: $fqcn"
done
assert_frozen_source_test_count api-compat 62 "${API_FQCNS[@]}"
assert_frozen_source_test_count watcher-source-management 36 "${WATCHER_FQCNS[@]}"
assert_frozen_source_test_count binding-publication-lock 16 "${BINDING_FQCNS[@]}"
assert_frozen_source_test_count isolation 132 "${ISOLATION_FQCNS[@]}"
assert_frozen_source_test_count autoconfig-launcher 64 "${AUTOCONFIG_FQCNS[@]}"

capture_source_manifest
capture_source_state before
capture_fixed_containers before
{
  printf 'captured_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  java -version 2>&1
  mvn -version 2>&1
  docker version 2>&1
} > "$RUN_ROOT/environment/tool-versions.txt"

run_compile_lane
run_surefire_lane 02 api-compat foggy-runtime-api \
  "$(join_csv "${API_FQCNS[@]}")" 6 62 0 "${API_FQCNS[@]}"
run_surefire_lane 03 watcher-source-management foggy-core,foggy-fsscript \
  "$(join_csv "${WATCHER_FQCNS[@]}")" 4 36 0 "${WATCHER_FQCNS[@]}"
run_surefire_lane 04 binding-publication-lock foggy-runtime-api \
  "$(join_csv "${BINDING_FQCNS[@]}")" 2 16 0 "${BINDING_FQCNS[@]}"
run_real_query_lane
run_sqlite_full_lane
run_multidb_lane 07 mysql57 docker
run_multidb_lane 08 postgres15 postgres
run_multidb_lane 09 sqlserver2022 sqlserver
run_surefire_lane 10 isolation-main \
  addons/foggy-dataset-model-cache,foggy-dataset-model-engine,foggy-dataset-mcp \
  "$(join_csv "${ISOLATION_FQCNS[@]}")" 13 132 0 "${ISOLATION_FQCNS[@]}"
run_surefire_lane 11 autoconfig-launcher \
  addons/foggy-dataset-mongo,addons/foggy-dataset-model-mongo,addons/foggy-dataset-vector,addons/foggy-dataset-model-vector,addons/foggy-dataset-model-cache,addons/foggy-dataset-graphql,addons/foggy-chart-storage-cloud,addons/foggy-data-viewer,foggy-dataset-model-engine,foggy-mcp-launcher \
  "$(join_csv "${AUTOCONFIG_FQCNS[@]}")" 15 64 0 "${AUTOCONFIG_FQCNS[@]}"
run_package_and_artifact_audit

CURRENT_STEP="final-integrity"
(cd "$ROOT_DIR" && sha256sum -c "$RUN_ROOT/source-audit/source-files.sha256") \
  > "$RUN_ROOT/source-audit/source-hash-check.txt" || \
  fail "source files changed during Batch 7 replay"
capture_source_state after
capture_fixed_containers after
cmp -s "$RUN_ROOT/source-audit/git-head-before.txt" \
  "$RUN_ROOT/source-audit/git-head-after.txt" || fail "git HEAD changed during replay"
cmp -s "$RUN_ROOT/source-audit/git-diff-before.sha256" \
  "$RUN_ROOT/source-audit/git-diff-after.sha256" || fail "tracked diff changed during replay"
cmp -s "$RUN_ROOT/source-audit/untracked-list-before.sha256" \
  "$RUN_ROOT/source-audit/untracked-list-after.sha256" || fail "untracked file list changed during replay"
cmp -s "$RUN_ROOT/source-audit/untracked-content-before.sha256" \
  "$RUN_ROOT/source-audit/untracked-content-after.sha256" || fail "untracked content changed during replay"
cmp -s "$RUN_ROOT/environment/fixed-containers-before.tsv" \
  "$RUN_ROOT/environment/fixed-containers-after.tsv" || fail "fixed database containers changed during replay"

[[ "$TOTAL_FAILURES" -eq 0 && "$TOTAL_ERRORS" -eq 0 ]] || \
  fail "aggregate failures/errors are nonzero"
[[ "$TOTAL_SKIPPED" -eq 3 ]] || fail "aggregate skipped=$TOTAL_SKIPPED, expected=3"
EXPECTED_TOTAL_TESTS=$((SQLITE_EXPECTED_TESTS + 375))
[[ "$TOTAL_TESTS" -eq "$EXPECTED_TOTAL_TESTS" ]] || \
  fail "aggregate tests=$TOTAL_TESTS, expected=$EXPECTED_TOTAL_TESTS"
EXPECTED_TOTAL_REPORTS=$((SQLITE_ACTUAL_REPORTS + 49))
[[ "$TOTAL_REPORTS" -eq "$EXPECTED_TOTAL_REPORTS" ]] || \
  fail "aggregate reports=$TOTAL_REPORTS, expected=$EXPECTED_TOTAL_REPORTS"

printf '%s\n' $'lane\treport\tclassname\tname' > "$RUN_ROOT/concurrency-generation-observations.tsv"
for testcase_file in "$RUN_ROOT"/lanes/*/testcases.tsv; do
  [[ -f "$testcase_file" ]] || continue
  lane_name="$(basename "$(dirname "$testcase_file")")"
  tail -n +2 "$testcase_file" | while IFS= read -r line; do
    [[ "$line" =~ (generation|Generation|binding|Binding|watch|Watch|namespace|Namespace|catalog|Catalog|refresh|Refresh|single|Single|lock|Lock) ]] || continue
    printf '%s\t%s\n' "$lane_name" "$line"
  done >> "$RUN_ROOT/concurrency-generation-observations.tsv"
done
[[ "$(wc -l < "$RUN_ROOT/concurrency-generation-observations.tsv")" -gt 1 ]] || \
  fail "no concurrency/generation testcase observations were captured"

date -u +%Y-%m-%dT%H:%M:%SZ > "$RUN_ROOT/finished-at.txt"
cat > "$RUN_ROOT/summary.env" <<SUMMARY
run_id=$RUN_ID
status=passed
criterion=API-COMPAT,REGRESSION
compile_reactor_modules=25
api_compat_tests=62
api_compat_reports=6
watcher_source_management_tests=36
watcher_source_management_reports=4
binding_publication_lock_tests=16
binding_publication_lock_reports=2
real_query_tests=11
real_query_reports=6
sqlite_tests=$SQLITE_EXPECTED_TESTS
sqlite_reports=$SQLITE_ACTUAL_REPORTS
sqlite_skipped=3
required_external_database_tests=54
required_external_database_reports=3
isolation_tests=132
isolation_reports=13
autoconfig_launcher_tests=64
autoconfig_launcher_reports=15
total_tests=$TOTAL_TESTS
total_reports=$TOTAL_REPORTS
failures=0
errors=0
skipped=3
package_reactor_modules=25
packaged_main_jars=24
launcher_nested_checksum_matches=12
source_unchanged=true
fixed_database_containers_unchanged=true
SUMMARY

finalize_directory_manifest "$RUN_ROOT"
printf '%s\n' "$RUN_ID" > "$LATEST_TMP"
mv "$LATEST_TMP" "$LATEST_RUN_ID"
SUCCESS_FINALIZED=1
LAST_COMPLETED_STEP="final-integrity"
echo "[v933-batch7] COMPLETE run=$RUN_ID tests=$TOTAL_TESTS reports=$TOTAL_REPORTS failures=0 errors=0 skipped=3"
