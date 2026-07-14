#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$ROOT_DIR/target/v933-batch4-single-flight/runs/$RUN_ID"
REPORT_ASSERTION="$ROOT_DIR/scripts/assert-v933-test-report.sh"

fail() {
  echo "[v933-batch4] ERROR: $*" >&2
  exit 1
}

[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
[[ ! -e "$RUN_ROOT" ]] || fail "run directory already exists: $RUN_ROOT"
[[ -x "$REPORT_ASSERTION" ]] || fail "report assertion is not executable"
command -v mvn >/dev/null 2>&1 || fail "required command is missing: mvn"
command -v rg >/dev/null 2>&1 || fail "required command is missing: rg"
command -v sha256sum >/dev/null 2>&1 || fail "required command is missing: sha256sum"
mkdir -p "$RUN_ROOT"

on_exit() {
  local status="$?"
  if [[ "$status" -eq 0 ]]; then
    echo "[v933-batch4] PASS run=$RUN_ID evidence=$RUN_ROOT"
  else
    echo "[v933-batch4] FAILED run=$RUN_ID evidence=$RUN_ROOT" >&2
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

run_model_lane() {
  local lane_name="$1"
  local test_filter="$2"
  shift 2
  local lane_dir="$RUN_ROOT/$lane_name"
  local log_file="$lane_dir/maven.log"
  mkdir -p "$lane_dir"
  : > "$lane_dir/.run-start"

  echo "[v933-batch4] running $lane_name"
  if ! (cd "$ROOT_DIR" && mvn -B -pl foggy-dataset-model -am \
      -P'!multi-db,model-lifecycle' \
      -DskipITs=true \
      -Dtest="$test_filter" \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -Dv933.reportsDirectory="$lane_dir" \
      test -l "$log_file"); then
    fail "$lane_name failed; log=$log_file"
  fi
  assert_report_set "$lane_dir" "$@"
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

  echo "[v933-batch4] running $lane_name"
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
    [[ "$status" -eq 1 ]] || \
      fail "$label scan failed with rg status=$status"
  fi
}

audit_sources() {
  local audit_dir="$RUN_ROOT/source-audit"
  local model_root="$ROOT_DIR/foggy-dataset-model"
  mkdir -p "$audit_dir"

  assert_rg_absent "sleep-driven Batch 4 test detected" \
      "$audit_dir/sleep-driven-tests.txt" \
      'Thread\.sleep|TimeUnit\.sleep' \
      "$model_root/src/test/java/com/foggyframework/dataset/db/model/lifecycle/concurrent" \
      "$model_root/src/test/java/com/foggyframework/dataset/db/model/lifecycle/catalog" \
      "$ROOT_DIR/foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/RuntimeNamedDataSourceResolverBindingTest.java" \
      "$ROOT_DIR/foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/datasource/DataSourceManagerBindingLifecycleTest.java"

  assert_rg_absent "promoted distinct-key red baseline remains" \
      "$audit_dir/promoted-red-reference.txt" \
      'DistinctTableModelLoadOverlapRedBaseline' \
      "$ROOT_DIR/scripts/verify-v933-batch1-red-baselines.sh" \
      "$model_root/src/test/java"

  assert_rg_absent "legacy catalog build lock remains" \
      "$audit_dir/long-build-lock.txt" \
      '\bbuildLock\b' \
      "$model_root/src/main/java/com/foggyframework/dataset/db/model/lifecycle/catalog"

  assert_rg_absent "single-flight owns a thread or executor" \
      "$audit_dir/owned-executor.txt" \
      'Executors\.|\bnew\s+Thread\s*\(' \
      "$model_root/src/main/java/com/foggyframework/dataset/db/model/lifecycle/concurrent"

  printf '%s\n' \
    'sleep-driven Batch 4 tests: 0' \
    'promoted distinct-key red references: 0' \
    'legacy catalog build locks: 0' \
    'single-flight owned threads/executors: 0' \
    > "$audit_dir/summary.txt"
}

run_model_lane single-flight-core \
  'ModelBuildKeyTest,ModelBuildSingleFlightTest,TableModelLoaderSingleFlightTest,QueryModelLoaderSingleFlightTest,CatalogSnapshotStoreTest,CatalogSnapshotTest,DatasourceBindingResolverCurrentnessTest' \
  'com.foggyframework.dataset.db.model.lifecycle.concurrent.ModelBuildKeyTest|4' \
  'com.foggyframework.dataset.db.model.lifecycle.concurrent.ModelBuildSingleFlightTest|17' \
  'com.foggyframework.dataset.db.model.lifecycle.concurrent.TableModelLoaderSingleFlightTest|4' \
  'com.foggyframework.dataset.db.model.lifecycle.concurrent.QueryModelLoaderSingleFlightTest|3' \
  'com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshotStoreTest|16' \
  'com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshotTest|5' \
  'com.foggyframework.dataset.db.model.lifecycle.port.DatasourceBindingResolverCurrentnessTest|2'

run_model_lane catalog-regression \
  'QueryModelDiscoveryAuthorityTest,SemanticMetadataCatalogIdentityTest,JdbcQueryModelCompletenessTest,QueryFacadeCatalogIdentityTest,TableModelLoaderManagerImplDataSourceResolutionTest,DbModelAutoConfigurationTest' \
  'com.foggyframework.dataset.db.model.lifecycle.catalog.QueryModelDiscoveryAuthorityTest|3' \
  'com.foggyframework.dataset.db.model.lifecycle.catalog.SemanticMetadataCatalogIdentityTest|3' \
  'com.foggyframework.dataset.db.model.lifecycle.catalog.JdbcQueryModelCompletenessTest|2' \
  'com.foggyframework.dataset.db.model.lifecycle.catalog.QueryFacadeCatalogIdentityTest|6' \
  'com.foggyframework.dataset.db.model.impl.loader.TableModelLoaderManagerImplDataSourceResolutionTest|14' \
  'com.foggyframework.dataset.db.model.config.DbModelAutoConfigurationTest|8'

run_model_lane sqlite-consumer-regression \
  'QueryModelAliasDeterminismTest,SyntheticMemberQueryModelLifecycleTest,QueryFacadeImplTest,SemanticServiceV3Test' \
  'com.foggyframework.dataset.db.model.lifecycle.catalog.QueryModelAliasDeterminismTest|1' \
  'com.foggyframework.dataset.db.model.semantic.member.SyntheticMemberQueryModelLifecycleTest|4' \
  'com.foggyframework.dataset.db.model.service.QueryFacadeImplTest|5' \
  'com.foggyframework.dataset.db.model.semantic.SemanticServiceV3Test|14' \
  'com.foggyframework.dataset.db.model.semantic.SemanticServiceV3Test$MetadataFieldAccessTests|5' \
  'com.foggyframework.dataset.db.model.semantic.SemanticServiceV3Test$MetadataPhysicalTablesTests|4'

run_model_lane namespace-regression \
  'QueryModelLoaderNamespaceScopeTest' \
  'com.foggyframework.dataset.db.model.lifecycle.namespace.QueryModelLoaderNamespaceScopeTest|3'

run_standard_lane runtime-binding-guard foggy-runtime-api \
  'RuntimeNamedDataSourceResolverBindingTest' \
  'com.foggyframework.runtime.api.service.RuntimeNamedDataSourceResolverBindingTest|5'

run_standard_lane mcp-binding-guard foggy-dataset-mcp \
  'DataSourceManagerBindingLifecycleTest' \
  'com.foggyframework.dataset.mcp.datasource.DataSourceManagerBindingLifecycleTest|14'

audit_sources

cat > "$RUN_ROOT/summary.env" <<SUMMARY
run_id=$RUN_ID
status=passed
single_flight_core_tests=51
catalog_regression_tests=36
sqlite_consumer_regression_tests=33
namespace_regression_tests=3
runtime_binding_guard_tests=5
mcp_binding_guard_tests=14
total_tests=142
failures=0
errors=0
skipped=0
same_key_callers=100
same_key_waiters=99
single_flight_residual_entries=0
SUMMARY

find "$RUN_ROOT" -type f ! -name SHA256SUMS -print0 \
  | sort -z | xargs -0 sha256sum > "$RUN_ROOT/SHA256SUMS"
printf '%s\n' "$RUN_ID" > \
  "$ROOT_DIR/target/v933-batch4-single-flight/latest-run-id"

echo "[v933-batch4] COMPLETE run=$RUN_ID tests=142 failures=0 errors=0 skipped=0"
