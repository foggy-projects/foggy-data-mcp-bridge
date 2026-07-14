#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/verify-v933-entry-gate.sh

Run the complete 9.3.3 entry gate: deterministic unit/IT probes, SQLite,
MySQL 5.7 and PostgreSQL 15 preflight, plus expected-failure anti-false-green
checks. Every invocation writes to a new target/v933-entry-gate/runs directory.
USAGE
}

fail() {
  echo "[v933-gate] ERROR: $*" >&2
  exit 1
}

[[ "$#" -eq 0 ]] || {
  usage >&2
  exit 2
}

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"
MODEL_MODULE="foggy-dataset-model"
COMPOSE_FILE="$REPO_ROOT/foggy-dataset-demo/docker/docker-compose.yml"
REPORT_ASSERTION="$SCRIPT_DIR/assert-v933-test-report.sh"

UNIT_FQCN="com.foggyframework.dataset.db.model.lifecycle.gate.DeterministicConcurrencyHarnessProbeTest"
IT_FQCN="com.foggyframework.dataset.db.model.lifecycle.gate.DeterministicConcurrencyHarnessProbeIT"
PREFLIGHT_FQCN="com.foggyframework.dataset.db.model.lifecycle.gate.RequiredDatabasePreflightIT"

for command_name in mvn docker sha256sum; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command is missing: $command_name"
done
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is required"
[[ -x "$REPORT_ASSERTION" ]] || fail "report assertion is not executable: $REPORT_ASSERTION"
[[ -f "$COMPOSE_FILE" ]] || fail "compose file is missing: $COMPOSE_FILE"

EXTERNAL_MAVEN_FLAGS="${MAVEN_ARGS:-} ${MAVEN_OPTS:-} ${JAVA_TOOL_OPTIONS:-}"
if [[ "$EXTERNAL_MAVEN_FLAGS" =~ (^|[[:space:]])-D(skipTests|maven\.test\.skip|skipITs|skipUnitTests)($|=|[[:space:]]) ]]; then
  fail "external Maven test-skip properties are forbidden for Gate 0"
fi
if [[ "$EXTERNAL_MAVEN_FLAGS" =~ (^|[[:space:]])-P[^[:space:]]*multi-db ]]; then
  fail "external multi-db profile activation is forbidden for Gate 0"
fi

RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
RUN_ROOT="$REPO_ROOT/target/v933-entry-gate/runs/$RUN_ID"
[[ ! -e "$RUN_ROOT" ]] || fail "run directory already exists: $RUN_ROOT"
mkdir -p "$RUN_ROOT"

on_exit() {
  local status="$?"
  if [[ "$status" -eq 0 ]]; then
    echo "[v933-gate] PASS run=$RUN_ID evidence=$RUN_ROOT"
  else
    echo "[v933-gate] FAILED run=$RUN_ID evidence=$RUN_ROOT" >&2
  fi
}
trap on_exit EXIT

new_lane() {
  local lane="$1"
  local lane_dir="$RUN_ROOT/$lane"
  mkdir "$lane_dir"
  : >"$lane_dir/.run-start"
  printf '%s\n' "$lane_dir"
}

count_log_matches() {
  local log_file="$1"
  local pattern="$2"
  grep -Ec -- "$pattern" "$log_file" || true
}

assert_execution_count() {
  local log_file="$1"
  local pattern="$2"
  local expected="$3"
  local label="$4"
  local actual
  actual="$(count_log_matches "$log_file" "$pattern")"
  [[ "$actual" == "$expected" ]] || \
    fail "$label execution count=$actual, expected=$expected; log=$log_file"
}

assert_no_multi_db_executions() {
  local log_file="$1"
  if grep -Eq -- 'maven-surefire-plugin:[^:]+:test \((test-mysql|test-postgres|test-sqlserver|test-sqlite)\)' "$log_file"; then
    fail "legacy multi-db Surefire execution appeared in $log_file"
  fi
}

assert_unit_runner_skipped() {
  local lane_dir="$1"
  local log_file="$lane_dir/maven.log"
  assert_execution_count "$log_file" \
    'maven-surefire-plugin:[^:]+:test \(default-test\) @ foggy-dataset-model' 1 \
    "owning Surefire skipped lane"
  grep -A4 -E -- \
    'maven-surefire-plugin:[^:]+:test \(default-test\) @ foggy-dataset-model' \
    "$log_file" | grep -q 'Tests are skipped' || \
    fail "owning Surefire did not prove its controlled skip; log=$log_file"
  if find "$lane_dir/surefire-reports" -maxdepth 1 -type f -name 'TEST-*.xml' \
      -print -quit 2>/dev/null | grep -q .; then
    fail "unit report exists in controlled IT-only lane: $lane_dir"
  fi
}

assert_failsafe_summary() {
  local lane_dir="$1"
  local summary="$lane_dir/failsafe-reports/failsafe-summary.xml"
  local marker="$lane_dir/.run-start"
  [[ -f "$summary" ]] || fail "Failsafe summary is missing: $summary"
  [[ "$summary" -nt "$marker" ]] || fail "Failsafe summary is stale: $summary"
  grep -q '<completed>1</completed>' "$summary" || fail "Failsafe completed count is not 1: $summary"
  grep -q '<errors>0</errors>' "$summary" || fail "Failsafe errors are non-zero: $summary"
  grep -q '<failures>0</failures>' "$summary" || fail "Failsafe failures are non-zero: $summary"
  grep -q '<skipped>0</skipped>' "$summary" || fail "Failsafe skipped count is non-zero: $summary"
  grep -q 'timeout="false"' "$summary" || fail "Failsafe summary reports a timeout: $summary"
}

assert_success_report() {
  local lane_dir="$1"
  local runner="$2"
  local fqcn="$3"
  local report_dir="$lane_dir/${runner}-reports"
  V933_RUN_MARKER="$lane_dir/.run-start" \
    "$REPORT_ASSERTION" "$report_dir" "$fqcn" 1
}

run_probe_pair() {
  local lane_dir
  lane_dir="$(new_lane probe-pair)"
  local log_file="$lane_dir/maven.log"

  echo "[v933-gate] running deterministic unit/IT probe pair"
  if ! mvn -B -pl "$MODEL_MODULE" -am \
      -P'!multi-db,model-lifecycle' \
      -Dtest="$UNIT_FQCN" \
      -Dit.test="$IT_FQCN" \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -Dv933.reportsDirectory="$lane_dir" \
      install -l "$log_file"; then
    fail "probe pair Maven run failed; log=$log_file"
  fi

  assert_success_report "$lane_dir" surefire "$UNIT_FQCN"
  assert_success_report "$lane_dir" failsafe "$IT_FQCN"
  assert_failsafe_summary "$lane_dir"
  assert_execution_count "$log_file" \
    'maven-surefire-plugin:[^:]+:test \(default-test\) @ foggy-dataset-model' 1 \
    "owning Surefire"
  assert_execution_count "$log_file" \
    'maven-failsafe-plugin:[^:]+:integration-test \(default\) @ foggy-dataset-model' 1 \
    "owning Failsafe integration-test"
  assert_execution_count "$log_file" \
    'maven-failsafe-plugin:[^:]+:verify \(default\) @ foggy-dataset-model' 1 \
    "owning Failsafe verify"
  assert_no_multi_db_executions "$log_file"
}

run_db_preflight() {
  local lane="$1"
  local spring_profile="$2"
  local expected_database="$3"
  local lane_dir
  lane_dir="$(new_lane "$lane")"
  local log_file="$lane_dir/maven.log"

  echo "[v933-gate] running database preflight lane=$lane"
  if ! mvn -B -pl "$MODEL_MODULE" -am \
      -P'!multi-db,model-lifecycle' \
      -DskipUnitTests=true \
      -DskipITs=false \
      -Dit.test="$PREFLIGHT_FQCN" \
      -Dspring.profiles.active="$spring_profile" \
      -Dv933.expectedDatabase="$expected_database" \
      -Dv933.reportsDirectory="$lane_dir" \
      verify -l "$log_file"; then
    fail "database preflight failed lane=$lane; log=$log_file"
  fi

  assert_success_report "$lane_dir" failsafe "$PREFLIGHT_FQCN"
  assert_failsafe_summary "$lane_dir"
  assert_unit_runner_skipped "$lane_dir"
  assert_execution_count "$log_file" \
    'maven-failsafe-plugin:[^:]+:integration-test \(default\) @ foggy-dataset-model' 1 \
    "owning Failsafe integration-test ($lane)"
  assert_execution_count "$log_file" \
    'maven-failsafe-plugin:[^:]+:verify \(default\) @ foggy-dataset-model' 1 \
    "owning Failsafe verify ($lane)"
  assert_no_multi_db_executions "$log_file"
}

assert_no_test_xml() {
  local lane_dir="$1"
  if find "$lane_dir" -type f -name 'TEST-*.xml' -print -quit | grep -q .; then
    fail "unexpected test XML exists for expected no-test failure: $lane_dir"
  fi
}

run_missing_unit_negative() {
  local lane_dir
  lane_dir="$(new_lane negative-missing-unit)"
  local log_file="$lane_dir/maven.log"

  echo "[v933-gate] proving a missing owning unit class fails closed"
  if mvn -B -pl "$MODEL_MODULE" \
      -P'!multi-db,model-lifecycle' \
      -Dtest=DefinitelyMissingV933UnitTest \
      -Dv933.reportsDirectory="$lane_dir" \
      test -l "$log_file"; then
    fail "missing owning unit class unexpectedly passed; log=$log_file"
  fi
  grep -Eq 'on project foggy-dataset-model: No tests matching pattern "DefinitelyMissingV933UnitTest"' "$log_file" || \
    fail "missing unit failure reason was not observed; log=$log_file"
  assert_no_test_xml "$lane_dir"
}

run_missing_it_negative() {
  local lane_dir
  lane_dir="$(new_lane negative-missing-it)"
  local log_file="$lane_dir/maven.log"

  echo "[v933-gate] proving a missing owning IT class fails closed"
  if mvn -B -pl "$MODEL_MODULE" \
      -P'!multi-db,model-lifecycle' \
      -DskipUnitTests=true \
      -Dit.test=DefinitelyMissingV933IT \
      -Dv933.reportsDirectory="$lane_dir" \
      verify -l "$log_file"; then
    fail "missing owning IT class unexpectedly passed; log=$log_file"
  fi
  grep -Eq 'on project foggy-dataset-model: No tests matching pattern "DefinitelyMissingV933IT"' "$log_file" || \
    fail "missing IT failure reason was not observed; log=$log_file"
  assert_unit_runner_skipped "$lane_dir"
  assert_no_test_xml "$lane_dir"
}

attribute_from_suite() {
  local suite_tag="$1"
  local attribute_name="$2"
  sed -n "s/.* ${attribute_name}=\"\([^\"]*\)\".*/\1/p" <<<"$suite_tag"
}

assert_expected_failed_report() {
  local lane_dir="$1"
  local fqcn="$2"
  local report_dir="$lane_dir/failsafe-reports"
  local marker="$lane_dir/.run-start"
  local -a reports=()
  mapfile -d '' reports < <(find "$report_dir" -maxdepth 1 -type f -name 'TEST-*.xml' -print0 | sort -z)
  [[ "${#reports[@]}" -eq 1 ]] || fail "expected one negative TEST XML in $report_dir"
  local report="${reports[0]}"
  [[ "$(basename "$report")" == "TEST-${fqcn}.xml" ]] || fail "unexpected negative report: $report"
  [[ "$report" -nt "$marker" ]] || fail "negative report is stale: $report"
  local suite_tag
  suite_tag="$(grep -o -m1 '<testsuite[^>]*>' "$report" || true)"
  [[ -n "$suite_tag" ]] || fail "testsuite missing from negative report: $report"
  [[ "$(attribute_from_suite "$suite_tag" tests)" == "1" ]] || fail "negative report tests != 1"
  [[ "$(attribute_from_suite "$suite_tag" failures)" == "1" ]] || fail "negative report failures != 1"
  [[ "$(attribute_from_suite "$suite_tag" errors)" == "0" ]] || fail "negative report errors != 0"
  [[ "$(attribute_from_suite "$suite_tag" skipped)" == "0" ]] || fail "negative report skipped != 0"
}

run_wrong_db_negative() {
  local lane_dir
  lane_dir="$(new_lane negative-wrong-db)"
  local log_file="$lane_dir/maven.log"

  echo "[v933-gate] proving a wrong required database fails closed"
  if mvn -B -pl "$MODEL_MODULE" \
      -P'!multi-db,model-lifecycle' \
      -DskipUnitTests=true \
      -Dit.test="$PREFLIGHT_FQCN" \
      -Dspring.profiles.active=sqlite \
      -Dv933.expectedDatabase=postgres15 \
      -Dv933.reportsDirectory="$lane_dir" \
      verify -l "$log_file"; then
    fail "wrong-database preflight unexpectedly passed; log=$log_file"
  fi
  grep -qi 'unexpected database product: sqlite' "$log_file" || \
    fail "wrong-database failure reason was not observed; log=$log_file"
  assert_unit_runner_skipped "$lane_dir"
  assert_expected_failed_report "$lane_dir" "$PREFLIGHT_FQCN"
}

run_missing_report_negative() {
  local lane_dir
  lane_dir="$(new_lane negative-missing-report)"
  local assertion_log="$lane_dir/assertion.log"

  echo "[v933-gate] proving a missing owning report fails closed"
  if V933_REPORT_WAIT_SECONDS=0 V933_RUN_MARKER="$lane_dir/.run-start" \
      "$REPORT_ASSERTION" "$lane_dir/empty-reports" "$UNIT_FQCN" 1 \
      >"$assertion_log" 2>&1; then
    fail "missing report assertion unexpectedly passed"
  fi
  grep -q 'report directory does not exist' "$assertion_log" || \
    fail "missing report failure reason was not observed; log=$assertion_log"
}

echo "[v933-gate] starting required database services"
docker compose -f "$COMPOSE_FILE" up -d --wait --wait-timeout 180 mysql postgres
for container in foggy-demo-mysql foggy-demo-postgres; do
  state="$(docker inspect \
    --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}missing-healthcheck{{end}}' \
    "$container" 2>/dev/null || true)"
  [[ "$state" == "running healthy" ]] || fail "$container state=$state"
done

run_probe_pair
run_db_preflight sqlite-preflight sqlite sqlite
run_db_preflight mysql57-preflight docker mysql57
run_db_preflight postgres15-preflight postgres postgres15
run_missing_unit_negative
run_missing_it_negative
run_wrong_db_negative
run_missing_report_negative

cat >"$RUN_ROOT/summary.env" <<SUMMARY
RUN_ID=$RUN_ID
RESULT=passed
PROBE_UNIT_TESTS=1
PROBE_IT_TESTS=1
PROBE_REACTOR_PHASE=install-through-verify
SQLITE_PREFLIGHT_TESTS=1
MYSQL57_PREFLIGHT_TESTS=1
POSTGRES15_PREFLIGHT_TESTS=1
EXPECTED_NEGATIVE_CASES=4
SUMMARY

(
  cd "$RUN_ROOT"
  find . -type f ! -name SHA256SUMS -print0 \
    | sort -z \
    | xargs -0 sha256sum
) >"$RUN_ROOT/SHA256SUMS"
