#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$ROOT_DIR/target/v933-batch5-refresh/runs/$RUN_ID"
REPORT_ASSERTION="$ROOT_DIR/scripts/assert-v933-test-report.sh"

fail() {
  echo "[v933-batch5] ERROR: $*" >&2
  exit 1
}

[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
[[ ! -e "$RUN_ROOT" ]] || fail "run directory already exists: $RUN_ROOT"
[[ -x "$REPORT_ASSERTION" ]] || fail "report assertion is not executable"
command -v mvn >/dev/null 2>&1 || fail "required command is missing: mvn"
command -v rg >/dev/null 2>&1 || fail "required command is missing: rg"
command -v sha256sum >/dev/null 2>&1 || fail "required command is missing: sha256sum"
mkdir -p "$RUN_ROOT"

MODEL_SPECS=(
  'com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinatorContractTest|4'
  'com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCandidateContractTest|4'
  'com.foggyframework.dataset.model.lifecycle.refresh.CatalogAdmissionContractTest|4'
  'com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinatorBehaviorTest|11'
  'com.foggyframework.dataset.model.lifecycle.refresh.FileChangeRefreshScopeTest|4'
  'com.foggyframework.dataset.model.lifecycle.refresh.BundleLifecycleRefreshTest|3'
)
MODEL_IT_SPECS=(
  'com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshQueryIT|2'
)
FSSCRIPT_SPECS=(
  'com.foggyframework.bundle.lifecycle.BundleSourceCommitOrderingTest|3'
  'com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistryTest|4'
)
RUNTIME_SPECS=(
  'com.foggyframework.runtime.api.dto.RuntimeLifecycleDtoContractTest|2'
  'com.foggyframework.runtime.api.dto.RuntimeLifecycleSafetyContractTest|3'
  'com.foggyframework.runtime.api.service.RuntimeLifecycleErrorMappingTest|3'
  'com.foggyframework.runtime.api.service.RuntimeModelRefreshLifecycleTest|4'
  'com.foggyframework.runtime.api.service.RuntimeModelValidationIsolationTest|2'
  'com.foggyframework.runtime.api.service.RuntimeDatasourceCatalogConvergenceTest|8'
  'com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryGenerationTest|7'
  'com.foggyframework.runtime.api.service.RuntimeNamedDataSourceResolverBindingTest|5'
)
MCP_SPECS=(
  'com.foggyframework.dataset.mcp.datasource.DataSourceManagerCatalogConvergenceTest|3'
  'com.foggyframework.dataset.mcp.datasource.DataSourceManagerBindingLifecycleTest|14'
)

on_exit() {
  local status="$?"
  if [[ "$status" -eq 0 ]]; then
    echo "[v933-batch5] PASS run=$RUN_ID evidence=$RUN_ROOT"
  else
    echo "[v933-batch5] FAILED run=$RUN_ID evidence=$RUN_ROOT" >&2
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
  local spec fqcn tests report isolated

  mapfile -d '' reports < <(find "$report_dir" -maxdepth 1 -type f \
    -name 'TEST-*.xml' -print0 | sort -z)
  [[ "${#reports[@]}" -eq "${#expected[@]}" ]] || \
    fail "report count=${#reports[@]}, expected=${#expected[@]} in $report_dir"

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

assert_failsafe_report_set() {
  local lane_dir="$1"
  shift
  local report_dir="$lane_dir/failsafe-reports"
  local marker="$lane_dir/.run-start"
  local summary="$report_dir/failsafe-summary.xml"
  local -a expected=("$@")
  local -a reports=()
  local spec fqcn tests report isolated total=0

  mapfile -d '' reports < <(find "$report_dir" -maxdepth 1 -type f \
    -name 'TEST-*.xml' -print0 | sort -z)
  [[ "${#reports[@]}" -eq "${#expected[@]}" ]] || \
    fail "Failsafe report count=${#reports[@]}, expected=${#expected[@]} in $report_dir"

  for spec in "${expected[@]}"; do
    fqcn="${spec%|*}"
    tests="${spec##*|}"
    (( total += tests ))
    report="$report_dir/TEST-${fqcn}.xml"
    [[ -f "$report" ]] || fail "missing owning Failsafe report: $report"
    [[ "$report" -nt "$marker" ]] || fail "stale owning Failsafe report: $report"
    isolated="$lane_dir/asserted/${fqcn##*.}"
    mkdir -p "$isolated"
    cp "$report" "$isolated/"
    V933_RUN_MARKER="$marker" \
      "$REPORT_ASSERTION" "$isolated" "$fqcn" "$tests"
  done

  [[ -f "$summary" && "$summary" -nt "$marker" ]] || \
    fail "missing or stale Failsafe summary: $summary"
  rg -q "<completed>${total}</completed>" "$summary" || \
    fail "Failsafe completed count drift: $summary"
  rg -q '<errors>0</errors>' "$summary" || fail "Failsafe errors are non-zero"
  rg -q '<failures>0</failures>' "$summary" || fail "Failsafe failures are non-zero"
  rg -q '<skipped>0</skipped>' "$summary" || fail "Failsafe skipped count is non-zero"
}

assert_test_source_counts() {
  local output_file="$1"
  local module="$2"
  shift 2
  local spec fqcn expected source actual
  : > "$output_file"

  for spec in "$@"; do
    fqcn="${spec%|*}"
    expected="${spec##*|}"
    source="$ROOT_DIR/$module/src/test/java/${fqcn//./\/}.java"
    [[ -f "$source" ]] || fail "frozen test source is missing: $source"
    actual="$(rg -c '^[[:space:]]*@Test\b' "$source" || true)"
    actual="${actual:-0}"
    [[ "$actual" -eq "$expected" ]] || \
      fail "@Test count=$actual, expected=$expected in $source"
    printf '%s\t%s\t%s\n' "$fqcn" "$expected" "$source" >> "$output_file"
  done
}

assert_inventory_totals() {
  local output_file="$1"
  local spec
  local model_tests=0
  local model_it_tests=0
  local fsscript_tests=0
  local runtime_tests=0
  local mcp_tests=0

  for spec in "${MODEL_SPECS[@]}"; do
    (( model_tests += ${spec##*|} ))
  done
  for spec in "${MODEL_IT_SPECS[@]}"; do
    (( model_it_tests += ${spec##*|} ))
  done
  for spec in "${FSSCRIPT_SPECS[@]}"; do
    (( fsscript_tests += ${spec##*|} ))
  done
  for spec in "${RUNTIME_SPECS[@]}"; do
    (( runtime_tests += ${spec##*|} ))
  done
  for spec in "${MCP_SPECS[@]}"; do
    (( mcp_tests += ${spec##*|} ))
  done

  [[ "${#MODEL_SPECS[@]}" -eq 6 && "$model_tests" -eq 30 ]] || \
    fail "model inventory drift: reports=${#MODEL_SPECS[@]} tests=$model_tests"
  [[ "${#MODEL_IT_SPECS[@]}" -eq 1 && "$model_it_tests" -eq 2 ]] || \
    fail "model IT inventory drift: reports=${#MODEL_IT_SPECS[@]} tests=$model_it_tests"
  [[ "${#FSSCRIPT_SPECS[@]}" -eq 2 && "$fsscript_tests" -eq 7 ]] || \
    fail "fsscript inventory drift: reports=${#FSSCRIPT_SPECS[@]} tests=$fsscript_tests"
  [[ "${#RUNTIME_SPECS[@]}" -eq 8 && "$runtime_tests" -eq 34 ]] || \
    fail "runtime inventory drift: reports=${#RUNTIME_SPECS[@]} tests=$runtime_tests"
  [[ "${#MCP_SPECS[@]}" -eq 2 && "$mcp_tests" -eq 17 ]] || \
    fail "MCP inventory drift: reports=${#MCP_SPECS[@]} tests=$mcp_tests"
  [[ "$((model_tests + model_it_tests + fsscript_tests + runtime_tests + mcp_tests))" -eq 90 ]] || \
    fail "Batch 5 total test inventory drift"
  [[ "$((${#MODEL_SPECS[@]} + ${#MODEL_IT_SPECS[@]} + ${#FSSCRIPT_SPECS[@]} \
      + ${#RUNTIME_SPECS[@]} + ${#MCP_SPECS[@]}))" -eq 19 ]] || \
    fail "Batch 5 owning report inventory drift"

  printf 'module\treports\ttests\n' > "$output_file"
  printf 'model\t%s\t%s\n' "${#MODEL_SPECS[@]}" "$model_tests" >> "$output_file"
  printf 'model-it\t%s\t%s\n' "${#MODEL_IT_SPECS[@]}" "$model_it_tests" >> "$output_file"
  printf 'fsscript\t%s\t%s\n' "${#FSSCRIPT_SPECS[@]}" "$fsscript_tests" >> "$output_file"
  printf 'runtime\t%s\t%s\n' "${#RUNTIME_SPECS[@]}" "$runtime_tests" >> "$output_file"
  printf 'mcp\t%s\t%s\n' "${#MCP_SPECS[@]}" "$mcp_tests" >> "$output_file"
  printf 'total\t19\t90\n' >> "$output_file"
}

run_model_lane() {
  local lane_dir="$RUN_ROOT/model-refresh-contract"
  local log_file="$lane_dir/maven.log"
  mkdir -p "$lane_dir"
  : > "$lane_dir/.run-start"

  echo "[v933-batch5] running model refresh/admission contract"
  if ! (cd "$ROOT_DIR" && mvn -B -pl foggy-dataset-model-engine -am \
      -P'!multi-db,model-lifecycle' \
      -DskipITs=true \
      -Dtest='CatalogRefreshCoordinatorContractTest,CatalogRefreshCandidateContractTest,CatalogAdmissionContractTest,CatalogRefreshCoordinatorBehaviorTest,FileChangeRefreshScopeTest,BundleLifecycleRefreshTest' \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -Dv933.reportsDirectory="$lane_dir" \
      test -l "$log_file"); then
    fail "model refresh contract failed; log=$log_file"
  fi

  assert_report_set "$lane_dir" "${MODEL_SPECS[@]}"
}

run_model_it_lane() {
  local lane_dir="$RUN_ROOT/model-refresh-sqlite-it"
  local log_file="$lane_dir/maven.log"
  mkdir -p "$lane_dir"
  : > "$lane_dir/.run-start"

  echo "[v933-batch5] running real SQLite atomic refresh IT"
  if ! (cd "$ROOT_DIR" && mvn -B -pl foggy-dataset-model-engine -am \
      -P'!multi-db,model-lifecycle' \
      -DskipUnitTests=true \
      -DskipITs=false \
      -Dit.test=com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshQueryIT \
      -Dfailsafe.failIfNoSpecifiedTests=false \
      -Dspring.profiles.active=sqlite \
      -Dv933.reportsDirectory="$lane_dir" \
      verify -l "$log_file"); then
    fail "real SQLite atomic refresh IT failed; log=$log_file"
  fi

  assert_failsafe_report_set "$lane_dir" "${MODEL_IT_SPECS[@]}"
}

run_standard_lane() {
  local lane_name="$1"
  local module="$2"
  local test_filter="$3"
  shift 3
  local lane_dir="$RUN_ROOT/$lane_name"
  local report_dir="$lane_dir/surefire-reports"
  local owning_reports="$ROOT_DIR/$module/target/surefire-reports"
  local log_file="$lane_dir/maven.log"
  local spec fqcn source text_report
  mkdir -p "$report_dir"
  : > "$lane_dir/.run-start"

  for spec in "$@"; do
    fqcn="${spec%|*}"
    rm -f "$owning_reports/TEST-${fqcn}.xml" "$owning_reports/${fqcn}.txt"
  done

  echo "[v933-batch5] running $lane_name"
  if ! (cd "$ROOT_DIR" && mvn -B -pl "$module" -am \
      -P'!multi-db' \
      -DskipITs=true \
      -Dtest="$test_filter" \
      -Dsurefire.failIfNoSpecifiedTests=false \
      test -l "$log_file"); then
    fail "$lane_name failed; log=$log_file"
  fi

  for spec in "$@"; do
    fqcn="${spec%|*}"
    source="$owning_reports/TEST-${fqcn}.xml"
    text_report="$owning_reports/${fqcn}.txt"
    [[ -f "$source" ]] || fail "missing owning report after $lane_name: $source"
    [[ "$source" -nt "$lane_dir/.run-start" ]] || \
      fail "stale owning report after $lane_name: $source"
    cp "$source" "$report_dir/"
    if [[ -f "$text_report" && "$text_report" -nt "$lane_dir/.run-start" ]]; then
      cp "$text_report" "$report_dir/"
    fi
  done
  assert_report_set "$lane_dir" "$@"
}

assert_rg_absent() {
  local label="$1"
  local output_file="$2"
  local pattern="$3"
  shift 3
  local status

  if rg -n "$pattern" "$@" > "$output_file"; then
    fail "$label"
  else
    status="$?"
    [[ "$status" -eq 1 ]] || fail "$label scan failed with rg status=$status"
  fi
}

audit_sources() {
  local audit_dir="$RUN_ROOT/source-audit"
  local refresh_main="$ROOT_DIR/foggy-dataset-model-engine/src/main/java/com/foggyframework/dataset/model/lifecycle/refresh"
  local batch6_green="$ROOT_DIR/foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/spi/impl/CatalogNamespaceAuthorityTest.java"
  local spec fqcn
  local -a frozen_test_sources=()
  mkdir -p "$audit_dir"

  [[ -d "$refresh_main" ]] || fail "refresh production package is missing: $refresh_main"
  [[ -f "$batch6_green" ]] || fail "Batch 6 catalog-authority green suite is missing"
  assert_rg_absent "a promoted RedBaseline source remains" \
    "$audit_dir/remaining-red-sources.txt" \
    'class[[:space:]]+[A-Za-z0-9_]*RedBaseline' \
    "$ROOT_DIR/foggy-dataset-model-engine/src/test/java" \
    "$ROOT_DIR/foggy-runtime-api/src/test/java" \
    "$ROOT_DIR/foggy-fsscript/src/test/java" \
    "$ROOT_DIR/foggy-dataset-mcp/src/test/java"

  assert_inventory_totals "$audit_dir/frozen-inventory.tsv"

  assert_test_source_counts \
    "$audit_dir/model-test-inventory.tsv" \
    foggy-dataset-model-engine "${MODEL_SPECS[@]}"
  assert_test_source_counts \
    "$audit_dir/model-it-inventory.tsv" \
    foggy-dataset-model-engine "${MODEL_IT_SPECS[@]}"
  assert_test_source_counts \
    "$audit_dir/fsscript-test-inventory.tsv" \
    foggy-fsscript "${FSSCRIPT_SPECS[@]}"
  assert_test_source_counts \
    "$audit_dir/runtime-test-inventory.tsv" \
    foggy-runtime-api "${RUNTIME_SPECS[@]}"
  assert_test_source_counts \
    "$audit_dir/mcp-test-inventory.tsv" \
    foggy-dataset-mcp "${MCP_SPECS[@]}"

  for spec in "${MODEL_SPECS[@]}"; do
    fqcn="${spec%|*}"
    frozen_test_sources+=(
      "$ROOT_DIR/foggy-dataset-model-engine/src/test/java/${fqcn//./\/}.java")
  done
  for spec in "${MODEL_IT_SPECS[@]}"; do
    fqcn="${spec%|*}"
    frozen_test_sources+=(
      "$ROOT_DIR/foggy-dataset-model-engine/src/test/java/${fqcn//./\/}.java")
  done
  for spec in "${FSSCRIPT_SPECS[@]}"; do
    fqcn="${spec%|*}"
    frozen_test_sources+=(
      "$ROOT_DIR/foggy-fsscript/src/test/java/${fqcn//./\/}.java")
  done
  for spec in "${RUNTIME_SPECS[@]}"; do
    fqcn="${spec%|*}"
    frozen_test_sources+=(
      "$ROOT_DIR/foggy-runtime-api/src/test/java/${fqcn//./\/}.java")
  done
  for spec in "${MCP_SPECS[@]}"; do
    fqcn="${spec%|*}"
    frozen_test_sources+=(
      "$ROOT_DIR/foggy-dataset-mcp/src/test/java/${fqcn//./\/}.java")
  done

  assert_rg_absent "sleep-driven Batch 5 contract test detected" \
    "$audit_dir/sleep-driven-tests.txt" \
    'Thread\.sleep|TimeUnit\.sleep' \
    "${frozen_test_sources[@]}"

  assert_rg_absent "production Runtime/event clear-first path remains" \
    "$audit_dir/production-runtime-event-clear-first.txt" \
    '\.(clearAll|clearByNamespace|clearNamespace)\(' \
    "$ROOT_DIR/foggy-runtime-api/src/main/java" \
    "$ROOT_DIR/foggy-dataset-model-engine/src/main/java/com/foggyframework/dataset/model/engine/query_model/DbModelFileChangeHandler.java" \
    "$ROOT_DIR/foggy-dataset-model-engine/src/main/java/com/foggyframework/dataset/model/event/BundleLifecycleListener.java" \
    "$ROOT_DIR/foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/datasource/DataSourceManager.java"

  assert_rg_absent "promoted Batch 5 red baseline remains" \
    "$audit_dir/promoted-batch5-red.txt" \
    'RuntimeLifecycleDtoContractRedBaseline|RuntimeModelLifecycleRedBaseline|BundleSourceCommitOrderingRedBaseline|FileChangeNamespaceScopeRedBaseline' \
    "$ROOT_DIR/scripts/verify-v933-batch1-red-baselines.sh" \
    "$ROOT_DIR/foggy-fsscript/src/test/java" \
    "$ROOT_DIR/foggy-dataset-model-engine/src/test/java" \
    "$ROOT_DIR/foggy-runtime-api/src/test/java" \
    "$ROOT_DIR/foggy-dataset-mcp/src/test/java"

  assert_rg_absent "refresh authority owns a thread or executor" \
    "$audit_dir/owned-refresh-executor.txt" \
    'Executors\.|\bnew\s+Thread\s*\(' \
    "$refresh_main"

  printf '%s\n' \
    'frozen Batch 5 tests: 90 in 19 owning reports' \
    'sleep-driven frozen Batch 5 tests: 0' \
    'production Runtime/event clear-first paths: 0' \
    'promoted Batch 5 red references: 0' \
    'refresh-owned threads/executors: 0' \
    'remaining RedBaseline sources: 0' \
    'Batch 6 catalog-authority green suite: present' \
    > "$audit_dir/summary.txt"
}

audit_sources

run_model_lane

run_model_it_lane

run_standard_lane fsscript-source-convergence foggy-fsscript \
  'BundleSourceCommitOrderingTest,CommittedSourceRevisionRegistryTest' \
  "${FSSCRIPT_SPECS[@]}"

run_standard_lane runtime-lifecycle foggy-runtime-api \
  'RuntimeLifecycleDtoContractTest,RuntimeLifecycleSafetyContractTest,RuntimeLifecycleErrorMappingTest,RuntimeModelRefreshLifecycleTest,RuntimeModelValidationIsolationTest,RuntimeDatasourceCatalogConvergenceTest,RuntimeDatasourceRegistryGenerationTest,RuntimeNamedDataSourceResolverBindingTest' \
  "${RUNTIME_SPECS[@]}"

run_standard_lane mcp-datasource-convergence foggy-dataset-mcp \
  'DataSourceManagerCatalogConvergenceTest,DataSourceManagerBindingLifecycleTest' \
  "${MCP_SPECS[@]}"

cat > "$RUN_ROOT/summary.env" <<SUMMARY
run_id=$RUN_ID
status=passed
model_refresh_contract_tests=12
model_refresh_behavior_tests=11
model_file_scope_tests=4
model_bundle_lifecycle_tests=3
model_unit_tests=30
model_sqlite_refresh_it_tests=2
model_tests=32
fsscript_bundle_source_commit_tests=3
fsscript_revision_registry_tests=4
fsscript_tests=7
runtime_dto_contract_tests=2
runtime_safety_contract_tests=3
runtime_error_mapping_tests=3
runtime_model_refresh_tests=4
runtime_model_validation_tests=2
runtime_datasource_convergence_tests=8
runtime_registry_generation_tests=7
runtime_named_resolver_tests=5
runtime_tests=34
mcp_datasource_convergence_tests=3
mcp_binding_lifecycle_tests=14
mcp_tests=17
total_tests=90
owning_reports=19
failures=0
errors=0
skipped=0
batch6_expected_red_suites=0
batch6_expected_red_tests=0
batch6_catalog_authority_green_suites=1
batch6_catalog_authority_green_tests=2
SUMMARY

find "$RUN_ROOT" -type f ! -name SHA256SUMS -print0 \
  | sort -z | xargs -0 sha256sum > "$RUN_ROOT/SHA256SUMS"
printf '%s\n' "$RUN_ID" > \
  "$ROOT_DIR/target/v933-batch5-refresh/latest-run-id"

echo "[v933-batch5] COMPLETE run=$RUN_ID tests=90 reports=19 failures=0 errors=0 skipped=0"
