#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$ROOT_DIR/target/v933-batch2-namespace/runs/$RUN_ID"
REPORT_ASSERTION="$ROOT_DIR/scripts/assert-v933-test-report.sh"

fail() {
  echo "[v933-batch2-namespace] ERROR: $*" >&2
  exit 1
}

[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
[[ ! -e "$RUN_ROOT" ]] || fail "run directory already exists: $RUN_ROOT"
[[ -x "$REPORT_ASSERTION" ]] || fail "report assertion is not executable: $REPORT_ASSERTION"
command -v mvn >/dev/null 2>&1 || fail "required command is missing: mvn"
command -v sha256sum >/dev/null 2>&1 || fail "required command is missing: sha256sum"
command -v rg >/dev/null 2>&1 || fail "required command is missing: rg"

mkdir -p "$RUN_ROOT"

on_exit() {
  local status="$?"
  if [[ "$status" -eq 0 ]]; then
    echo "[v933-batch2-namespace] PASS run=$RUN_ID evidence=$RUN_ROOT"
  else
    echo "[v933-batch2-namespace] FAILED run=$RUN_ID evidence=$RUN_ROOT" >&2
  fi
}
trap on_exit EXIT

assert_report_set() {
  local lane_dir="$1"
  shift
  local report_dir="$lane_dir/surefire-reports"
  local marker="$lane_dir/.run-start"
  local -a expected=("$@")
  local -a reports=()

  mapfile -d '' reports < <(find "$report_dir" -maxdepth 1 -type f -name 'TEST-*.xml' -print0 | sort -z)
  if [[ "${V933_ALLOW_EXTRA_REPORTS:-0}" == "1" ]]; then
    [[ "${#reports[@]}" -ge "${#expected[@]}" ]] || \
      fail "report count=${#reports[@]}, expected at least ${#expected[@]} in $report_dir"
    local discovered_report
    for discovered_report in "${reports[@]}"; do
      [[ "$discovered_report" -nt "$marker" ]] || \
        fail "stale discovered report: $discovered_report"
    done
  else
    [[ "${#reports[@]}" -eq "${#expected[@]}" ]] || \
      fail "report count=${#reports[@]}, expected=${#expected[@]} in $report_dir"
  fi

  local spec fqcn tests report isolated
  for spec in "${expected[@]}"; do
    fqcn="${spec%|*}"
    tests="${spec##*|}"
    report="$report_dir/TEST-${fqcn}.xml"
    [[ -f "$report" ]] || fail "missing owning report: $report"
    [[ "$report" -nt "$marker" ]] || fail "stale owning report: $report"

    isolated="$lane_dir/asserted/${fqcn##*.}"
    mkdir -p "$isolated"
    cp "$report" "$isolated/"
    V933_RUN_MARKER="$marker" \
      "$REPORT_ASSERTION" "$isolated" "$fqcn" "$tests"
  done
}

DEFAULT_DISCOVERY_REPORTS=0

prepare_current_reactor() {
  local lane_dir="$RUN_ROOT/reactor-prep"
  local log_file="$lane_dir/maven.log"
  mkdir -p "$lane_dir"

  echo "[v933-batch2-namespace] installing current reactor dependencies"
  if ! (cd "$ROOT_DIR" && mvn -B -pl foggy-dataset-model -am \
      -P'!multi-db,model-lifecycle' \
      -DskipTests \
      install -l "$log_file"); then
    fail "current reactor dependency preparation failed; log=$log_file"
  fi
}

run_default_discovery() {
  local lane_dir="$RUN_ROOT/default-discovery"
  local log_file="$lane_dir/maven.log"
  mkdir -p "$lane_dir"
  : > "$lane_dir/.run-start"

  echo "[v933-batch2-namespace] running default lifecycle discovery"
  if ! (cd "$ROOT_DIR" && mvn -B -pl foggy-dataset-model \
      -P'!multi-db,model-lifecycle' \
      -DskipITs=true \
      -Dv933.reportsDirectory="$lane_dir" \
      test -l "$log_file"); then
    fail "default lifecycle discovery failed; log=$log_file"
  fi

  V933_ALLOW_EXTRA_REPORTS=1 assert_report_set "$lane_dir" \
    'com.foggyframework.dataset.db.model.lifecycle.gate.DeterministicConcurrencyHarnessProbeTest|1' \
    'com.foggyframework.dataset.db.model.lifecycle.namespace.NamespaceScopeTest|9' \
    'com.foggyframework.dataset.db.model.lifecycle.namespace.NamespaceProductionEntryRestorationTest|11' \
    'com.foggyframework.dataset.db.model.lifecycle.namespace.SemanticServiceNamespaceScopeTest|2' \
    'com.foggyframework.dataset.db.model.lifecycle.namespace.QueryModelLoaderNamespaceScopeTest|3'
  DEFAULT_DISCOVERY_REPORTS="$(find "$lane_dir/surefire-reports" -maxdepth 1 \
    -type f -name 'TEST-*.xml' | wc -l)"
}

run_compatibility() {
  local lane_dir="$RUN_ROOT/legacy-compatibility"
  local log_file="$lane_dir/maven.log"
  local fqcn='com.foggyframework.dataset.db.model.namespace.NamespaceContextTest'
  mkdir -p "$lane_dir"
  : > "$lane_dir/.run-start"

  echo "[v933-batch2-namespace] running legacy NamespaceContext compatibility"
  if ! (cd "$ROOT_DIR" && mvn -B -pl foggy-dataset-model \
      -P'!multi-db,model-lifecycle' \
      -DskipITs=true \
      -Dtest="$fqcn" \
      -Dv933.reportsDirectory="$lane_dir" \
      test -l "$log_file"); then
    fail "legacy compatibility run failed; log=$log_file"
  fi

  assert_report_set "$lane_dir" "$fqcn|7"
}

audit_sources() {
  local audit_dir="$RUN_ROOT/source-audit"
  mkdir -p "$audit_dir"

  local status
  set +e
  rg -n 'NamespaceContext\.(setNamespace|clear)\(' \
    "$ROOT_DIR/foggy-dataset-model/src/main/java" --glob '*.java' \
    > "$audit_dir/production-legacy-mutations.txt"
  status=$?
  set -e
  case "$status" in
    0) fail "production legacy NamespaceContext mutation remains" ;;
    1) ;;
    *) fail "production NamespaceContext source audit failed with rg status $status" ;;
  esac

  set +e
  rg -n 'Thread\.sleep|TimeUnit\.sleep' \
    "$ROOT_DIR/foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/lifecycle/namespace" \
    "$ROOT_DIR/foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/namespace/NamespaceContextTest.java" \
    > "$audit_dir/sleep-driven-tests.txt"
  status=$?
  set -e
  case "$status" in
    0) fail "sleep-driven Batch 2 test detected" ;;
    1) ;;
    *) fail "sleep-driven Batch 2 source audit failed with rg status $status" ;;
  esac

  printf '%s\n' 'production legacy NamespaceContext mutations: 0' \
    'sleep-driven Batch 2 tests: 0' > "$audit_dir/summary.txt"
}

prepare_current_reactor
run_default_discovery
run_compatibility
audit_sources

cat > "$RUN_ROOT/summary.env" <<SUMMARY
run_id=$RUN_ID
status=passed
reactor_prep=passed
default_discovery_reports=$DEFAULT_DISCOVERY_REPORTS
default_discovery_owning_suites=5
default_discovery_owning_tests=26
namespace_product_tests=25
legacy_compatibility_tests=7
failures=0
errors=0
skipped=0
production_legacy_mutations=0
sleep_driven_tests=0
SUMMARY

find "$RUN_ROOT" -type f ! -name SHA256SUMS -print0 \
  | sort -z \
  | xargs -0 sha256sum > "$RUN_ROOT/SHA256SUMS"
printf '%s\n' "$RUN_ID" > "$ROOT_DIR/target/v933-batch2-namespace/latest-run-id"

echo "[v933-batch2-namespace] COMPLETE run=$RUN_ID namespace_tests=25 compatibility_tests=7"
