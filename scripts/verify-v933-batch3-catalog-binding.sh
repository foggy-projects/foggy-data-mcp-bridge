#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$ROOT_DIR/target/v933-batch3-catalog-binding/runs/$RUN_ID"
REPORT_ASSERTION="$ROOT_DIR/scripts/assert-v933-test-report.sh"

fail() {
  echo "[v933-batch3] ERROR: $*" >&2
  exit 1
}

[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
[[ ! -e "$RUN_ROOT" ]] || fail "run directory already exists: $RUN_ROOT"
[[ -x "$REPORT_ASSERTION" ]] || fail "report assertion is not executable: $REPORT_ASSERTION"
command -v mvn >/dev/null 2>&1 || fail "required command is missing: mvn"
command -v sha256sum >/dev/null 2>&1 || fail "required command is missing: sha256sum"
mkdir -p "$RUN_ROOT"

on_exit() {
  local status="$?"
  if [[ "$status" -eq 0 ]]; then
    echo "[v933-batch3] PASS run=$RUN_ID evidence=$RUN_ROOT"
  else
    echo "[v933-batch3] FAILED run=$RUN_ID evidence=$RUN_ROOT" >&2
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

  mapfile -d '' reports < <(find "$report_dir" -maxdepth 1 -type f -name 'TEST-*.xml' -print0 | sort -z)
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

  echo "[v933-batch3] running $lane_name"
  if ! (cd "$ROOT_DIR" && mvn -B -pl foggy-dataset-model-engine -am \
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

  echo "[v933-batch3] running $lane_name"
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
    [[ "$source" -nt "$lane_dir/.run-start" ]] || fail "stale owning report after $lane_name: $source"
    cp "$source" "$report_dir/"
    if [[ -f "$text_report" && "$text_report" -nt "$lane_dir/.run-start" ]]; then
      cp "$text_report" "$report_dir/"
    fi
  done
  assert_report_set "$lane_dir" "$@"
}

audit_sources() {
  local audit_dir="$RUN_ROOT/source-audit"
  local model_main="$ROOT_DIR/foggy-dataset-model-engine/src/main/java/com/foggyframework/dataset/model"
  mkdir -p "$audit_dir"

  if rg -n 'name2JdbcModel|namespaceCaches|\bdimIdx\b|\bmodelIdx\b' \
      "$model_main/impl/loader/TableModelLoaderManagerImpl.java" \
      "$model_main/engine/query_model/QueryModelLoaderImpl.java" \
      "$model_main/lifecycle" > "$audit_dir/legacy-authority.txt"; then
    fail "legacy mutable catalog authority remains"
  fi

  if rg -n 'Thread\.sleep|TimeUnit\.sleep' \
      "$ROOT_DIR/foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/lifecycle/catalog" \
      "$ROOT_DIR/foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/RuntimeDatasourceBindingLifecycleTest.java" \
      "$ROOT_DIR/foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/RuntimeDatasourceRegistryGenerationTest.java" \
      "$ROOT_DIR/foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/RuntimeNamedDataSourceResolverBindingTest.java" \
      "$ROOT_DIR/foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/datasource/DataSourceManagerBindingLifecycleTest.java" \
      > "$audit_dir/sleep-driven-tests.txt"; then
    fail "sleep-driven Batch 3 test detected"
  fi

  if rg -n 'NamespaceProductionEntryRestorationRedBaseline|JdbcQueryModelCompletenessRedBaseline|QueryModelAliasDeterminismRedBaseline|RuntimeDatasourceBindingRedBaseline' \
      "$ROOT_DIR/scripts/verify-v933-batch1-red-baselines.sh" \
      > "$audit_dir/promoted-red-references.txt"; then
    fail "promoted product-green baseline remains in Batch 1 red runner"
  fi

  if rg -n 'new DatasourceBindingGeneration\([^\n]*(url|host|database|username|password)' \
      "$ROOT_DIR/foggy-runtime-api/src/main/java" \
      "$ROOT_DIR/foggy-dataset-mcp/src/main/java" \
      > "$audit_dir/physical-generation-inputs.txt"; then
    fail "physical/credential input detected in binding generation"
  fi

  printf '%s\n' \
    'legacy mutable catalog authority: 0' \
    'sleep-driven Batch 3 tests: 0' \
    'promoted red-runner references: 0' \
    'physical/credential binding-generation inputs: 0' \
    > "$audit_dir/summary.txt"
}

run_model_lane catalog-authority \
  'CatalogSnapshotTest,CatalogSnapshotStoreTest,QueryModelDiscoveryAuthorityTest,SemanticMetadataCatalogIdentityTest,JdbcQueryModelCompletenessTest,QueryFacadeCatalogIdentityTest,TableModelLoaderManagerImplDataSourceResolutionTest,DbModelAutoConfigurationTest' \
  'com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotTest|5' \
  'com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStoreTest|16' \
  'com.foggyframework.dataset.model.lifecycle.catalog.QueryModelDiscoveryAuthorityTest|3' \
  'com.foggyframework.dataset.model.lifecycle.catalog.SemanticMetadataCatalogIdentityTest|3' \
  'com.foggyframework.dataset.model.lifecycle.catalog.JdbcQueryModelCompletenessTest|2' \
  'com.foggyframework.dataset.model.lifecycle.catalog.QueryFacadeCatalogIdentityTest|6' \
  'com.foggyframework.dataset.model.impl.loader.TableModelLoaderManagerImplDataSourceResolutionTest|14' \
  'com.foggyframework.dataset.model.config.DbModelAutoConfigurationTest|8'

run_model_lane sqlite-consumer-regression \
  'QueryModelAliasDeterminismTest,SyntheticMemberQueryModelLifecycleTest,QueryFacadeImplTest,SemanticServiceV3Test' \
  'com.foggyframework.dataset.model.lifecycle.catalog.QueryModelAliasDeterminismTest|1' \
  'com.foggyframework.dataset.model.semantic.member.SyntheticMemberQueryModelLifecycleTest|4' \
  'com.foggyframework.dataset.model.service.QueryFacadeImplTest|5' \
  'com.foggyframework.dataset.model.semantic.SemanticServiceV3Test|14' \
  'com.foggyframework.dataset.model.semantic.SemanticServiceV3Test$MetadataFieldAccessTests|5' \
  'com.foggyframework.dataset.model.semantic.SemanticServiceV3Test$MetadataPhysicalTablesTests|4'

run_standard_lane runtime-binding foggy-runtime-api \
  'RuntimeDatasourceBindingLifecycleTest,RuntimeDatasourceRegistryGenerationTest,RuntimeNamedDataSourceResolverBindingTest,ManagedDataSourcePoolManagerTest' \
  'com.foggyframework.runtime.api.service.RuntimeDatasourceBindingLifecycleTest|10' \
  'com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryGenerationTest|7' \
  'com.foggyframework.runtime.api.service.RuntimeNamedDataSourceResolverBindingTest|5' \
  'com.foggyframework.runtime.api.service.ManagedDataSourcePoolManagerTest|12'

run_standard_lane mcp-binding foggy-dataset-mcp \
  'DataSourceManagerBindingLifecycleTest' \
  'com.foggyframework.dataset.mcp.datasource.DataSourceManagerBindingLifecycleTest|14'

run_standard_lane cache-identity addons/foggy-dataset-model-cache \
  'CaffeineQueryCacheProviderTest' \
  'com.foggyframework.dataset.model.cache.provider.CaffeineQueryCacheProviderTest|30'

audit_sources

cat > "$RUN_ROOT/summary.env" <<SUMMARY
run_id=$RUN_ID
status=passed
catalog_authority_tests=57
sqlite_consumer_regression_tests=33
runtime_binding_tests=34
mcp_binding_tests=14
cache_identity_tests=30
total_tests=168
failures=0
errors=0
skipped=0
legacy_mutable_authority=0
sleep_driven_tests=0
SUMMARY

find "$RUN_ROOT" -type f ! -name SHA256SUMS -print0 \
  | sort -z \
  | xargs -0 sha256sum > "$RUN_ROOT/SHA256SUMS"
printf '%s\n' "$RUN_ID" > "$ROOT_DIR/target/v933-batch3-catalog-binding/latest-run-id"

echo "[v933-batch3] COMPLETE run=$RUN_ID tests=168 failures=0 errors=0 skipped=0"
