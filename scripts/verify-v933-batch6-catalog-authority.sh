#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$ROOT_DIR/target/v933-batch6-catalog/runs/$RUN_ID"
REPORT_ASSERTION="$ROOT_DIR/scripts/assert-v933-test-report.sh"

fail() {
  echo "[v933-batch6-catalog] ERROR: $*" >&2
  exit 1
}

[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
[[ ! -e "$RUN_ROOT" ]] || fail "run directory already exists: $RUN_ROOT"
[[ -x "$REPORT_ASSERTION" ]] || fail "report assertion is not executable"
command -v mvn >/dev/null 2>&1 || fail "required command is missing: mvn"
command -v rg >/dev/null 2>&1 || fail "required command is missing: rg"
command -v awk >/dev/null 2>&1 || fail "required command is missing: awk"
command -v sha256sum >/dev/null 2>&1 || fail "required command is missing: sha256sum"
mkdir -p "$RUN_ROOT"

MODEL_SPECS=(
  'com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogServiceTest|11'
)
MCP_SPECS=(
  'com.foggyframework.dataset.mcp.spi.impl.CatalogNamespaceAuthorityTest|5'
  'com.foggyframework.dataset.mcp.spi.impl.CatalogAuthoritySpringWiringTest|1'
  'com.foggyframework.dataset.mcp.spi.impl.SemanticServiceResolverImplTest|11'
  'com.foggyframework.dataset.mcp.tools.ListModelsToolTest$ExecuteSuccessTest|19'
  'com.foggyframework.dataset.mcp.tools.ListModelsToolTest$BasicPropertiesTest|2'
  'com.foggyframework.dataset.mcp.controller.ListModelsCatalogControllerTest|4'
)

on_exit() {
  local status="$?"
  if [[ "$status" -eq 0 ]]; then
    echo "[v933-batch6-catalog] PASS run=$RUN_ID evidence=$RUN_ROOT"
  else
    echo "[v933-batch6-catalog] FAILED run=$RUN_ID evidence=$RUN_ROOT" >&2
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

assert_rg_present() {
  local label="$1"
  local output_file="$2"
  local pattern="$3"
  shift 3

  if ! rg -n "$pattern" "$@" > "$output_file"; then
    fail "$label"
  fi
  [[ -s "$output_file" ]] || fail "$label produced empty evidence"
}

extract_java_method() {
  local label="$1"
  local source="$2"
  local signature="$3"
  local output_file="$4"

  if ! awk -v signature="$signature" '
    $0 ~ signature {
      found = 1
    }
    found {
      print
      line = $0
      opens += gsub(/\{/, "", line)
      line = $0
      closes += gsub(/\}/, "", line)
      if (opens > 0 && closes >= opens) {
        complete = 1
        exit
      }
    }
    END {
      if (!found || !complete) {
        exit 2
      }
    }
  ' "$source" > "$output_file"; then
    fail "$label"
  fi
  [[ -s "$output_file" ]] || fail "$label produced empty evidence"
}

audit_sources() {
  local audit_dir="$RUN_ROOT/source-audit"
  local model_service="$ROOT_DIR/foggy-dataset-model-engine/src/main/java/com/foggyframework/dataset/model/semantic/service/SemanticModelCatalogService.java"
  local mcp_resolver="$ROOT_DIR/foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/spi/impl/SemanticServiceResolverImpl.java"
  local mcp_catalog="$ROOT_DIR/foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/service/ModelCatalogService.java"
  local model_test="$ROOT_DIR/foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/semantic/service/SemanticModelCatalogServiceTest.java"
  local authority_test="$ROOT_DIR/foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/spi/impl/CatalogNamespaceAuthorityTest.java"
  local spring_wiring_test="$ROOT_DIR/foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/spi/impl/CatalogAuthoritySpringWiringTest.java"
  local resolver_test="$ROOT_DIR/foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/spi/impl/SemanticServiceResolverImplTest.java"
  local list_test="$ROOT_DIR/foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/tools/ListModelsToolTest.java"
  local controller_test="$ROOT_DIR/foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/controller/ListModelsCatalogControllerTest.java"
  local actual
  local -a red_sources=()
  local -a setup_occurrences=()
  mkdir -p "$audit_dir"

  for source in "$model_service" "$mcp_resolver" "$mcp_catalog" \
      "$model_test" "$authority_test" "$spring_wiring_test" "$resolver_test" \
      "$list_test" "$controller_test"; do
    [[ -f "$source" ]] || fail "required catalog source is missing: $source"
  done

  assert_rg_absent "consumer-owned cached model names remain" \
    "$audit_dir/consumer-names-cache.txt" \
    '\bcachedModelNames\b' "$model_service" "$mcp_resolver" "$mcp_catalog"

  extract_java_method "could not isolate the MCP @PostConstruct init method" \
    "$mcp_resolver" '^[[:space:]]*@PostConstruct[[:space:]]*\r?$' \
    "$audit_dir/mcp-post-construct-init.java"
  assert_rg_present "MCP @PostConstruct no longer targets init" \
    "$audit_dir/mcp-post-construct-init-signature.txt" \
    'public[[:space:]]+void[[:space:]]+init\(\)' \
    "$audit_dir/mcp-post-construct-init.java"
  assert_rg_present "MCP init no longer declares the shared catalog authority" \
    "$audit_dir/mcp-post-construct-shared-authority.txt" \
    'SemanticServiceResolver initialized with shared namespace catalog authority' \
    "$audit_dir/mcp-post-construct-init.java"
  assert_rg_absent "MCP @PostConstruct init still registers independent directory watching" \
    "$audit_dir/mcp-post-construct-watcher-registration.txt" \
    'setupDirectoryWatching|watchDirectory|WatchServiceFileTracer|QM_EXTENSIONS' \
    "$audit_dir/mcp-post-construct-init.java"
  assert_rg_present "MCP dead watcher method declaration is missing" \
    "$audit_dir/mcp-dead-watcher-declaration.txt" \
    'private[[:space:]]+void[[:space:]]+setupDirectoryWatching\(\)' "$mcp_resolver"
  if ! rg -n -o '\bsetupDirectoryWatching[[:space:]]*\(' "$mcp_resolver" \
      > "$audit_dir/mcp-dead-watcher-occurrences.txt"; then
    fail "could not locate the MCP dead watcher declaration"
  fi
  mapfile -t setup_occurrences < "$audit_dir/mcp-dead-watcher-occurrences.txt"
  printf 'setupDirectoryWatching_occurrences=%s\n' "${#setup_occurrences[@]}" \
    > "$audit_dir/mcp-dead-watcher-occurrence-count.txt"
  [[ "${#setup_occurrences[@]}" -eq 1 ]] || \
    fail "MCP dead watcher must have only its declaration, occurrences=${#setup_occurrences[@]}"

  assert_rg_absent "sleep-driven catalog authority test detected" \
    "$audit_dir/sleep-driven-tests.txt" \
    'Thread\.sleep|TimeUnit\.sleep' \
    "$model_test" "$authority_test" "$spring_wiring_test" "$resolver_test" \
    "$list_test" "$controller_test"

  assert_rg_present "model catalog does not retain the refresh coordinator" \
    "$audit_dir/model-refresh-coordinator-field.txt" \
    'private[[:space:]]+final[[:space:]]+CatalogRefreshCoordinator[[:space:]]+catalogRefreshCoordinator;' \
    "$model_service"
  assert_rg_present "model catalog does not assign the injected refresh coordinator" \
    "$audit_dir/model-refresh-coordinator-assignment.txt" \
    'this\.catalogRefreshCoordinator[[:space:]]*=[[:space:]]*catalogRefreshCoordinator;' \
    "$model_service"
  assert_rg_present "model catalog recovery does not delegate to the coordinator" \
    "$audit_dir/model-refresh-coordinator-live-call.txt" \
    'catalogRefreshCoordinator\.refresh\(CatalogRefreshRequest\.namespace' "$model_service"
  assert_rg_absent "model catalog still invokes candidate lifecycle methods directly" \
    "$audit_dir/model-direct-lifecycle-method-calls.txt" \
    '\b(openCandidate|resolveJdbcQueryModels|resolveJdbcQueryModel|discoverQueryModelNames)[[:space:]]*\(' \
    "$model_service"
  assert_rg_absent "model catalog still owns candidate lifecycle types" \
    "$audit_dir/model-direct-lifecycle-types.txt" \
    '\b(CatalogBuildView|CandidateScope)\b' "$model_service"
  assert_rg_present "model catalog view does not carry binding provenance" \
    "$audit_dir/model-binding-provenance-query.txt" \
    'snapshot\.queryModelProvenance\(' "$model_service"
  assert_rg_present "model catalog view drops datasource binding provenance" \
    "$audit_dir/model-binding-provenance-datasources.txt" \
    'provenance\.datasourceBindings\(\)' "$model_service"
  assert_rg_present "model catalog view drops binding completeness provenance" \
    "$audit_dir/model-binding-provenance-completeness.txt" \
    'provenance\.bindingIdentityComplete\(\)' "$model_service"

  assert_rg_present "MCP catalog does not consume the shared namespace view" \
    "$audit_dir/mcp-shared-view.txt" \
    '\.namespaceCatalogView\(namespace\)' "$mcp_catalog"
  assert_rg_present "MCP catalog does not consume pinned resolutions" \
    "$audit_dir/mcp-pinned-resolutions.txt" \
    'view\.resolutionsByModel\(\)' "$mcp_catalog"
  assert_rg_present "MCP catalog retry budget is not pinned to three attempts" \
    "$audit_dir/mcp-catalog-retry-budget.txt" \
    'MAX_CATALOG_VIEW_ATTEMPTS[[:space:]]*=[[:space:]]*3' "$mcp_catalog"
  assert_rg_present "MCP catalog does not re-read the authority after building metadata" \
    "$audit_dir/mcp-catalog-post-build-observation.txt" \
    'NamespaceCatalogView[[:space:]]+observedAfterBuild[[:space:]]*=[[:space:]]*semanticModelCatalogService' \
    "$mcp_catalog"
  assert_rg_present "MCP catalog does not compare pinned and post-build identities" \
    "$audit_dir/mcp-catalog-identity-comparison.txt" \
    'namespaceView\.identity\(\)\.equals\(observedAfterBuild\.identity\(\)\)' "$mcp_catalog"
  assert_rg_present "MCP catalog does not fail closed after retry exhaustion" \
    "$audit_dir/mcp-catalog-retry-exhausted.txt" \
    'CATALOG_VIEW_STALE_RETRY_EXHAUSTED' "$mcp_catalog"
  assert_rg_present "MCP catalog does not resolve models from pinned catalog resolutions" \
    "$audit_dir/mcp-catalog-resolution-model.txt" \
    'return[[:space:]]+resolution[[:space:]]*==[[:space:]]*null[[:space:]]*\?[[:space:]]*null[[:space:]]*:[[:space:]]*resolution\.model\(\);' \
    "$mcp_catalog"

  assert_rg_present "MCP resolver does not retain the shared catalog authority" \
    "$audit_dir/resolver-shared-authority-field.txt" \
    'private[[:space:]]+final[[:space:]]+SemanticModelCatalogService[[:space:]]+semanticModelCatalogService;' \
    "$mcp_resolver"
  assert_rg_present "MCP resolver does not assign the shared catalog authority" \
    "$audit_dir/resolver-shared-authority-assignment.txt" \
    'this\.semanticModelCatalogService[[:space:]]*=[[:space:]]*semanticModelCatalogService;' \
    "$mcp_resolver"
  assert_rg_present "MCP resolver does not route model names through the shared authority" \
    "$audit_dir/resolver-shared-authority-call.txt" \
    'return[[:space:]]+semanticModelCatalogService\.getAllModelNames\(canonicalNamespace\);' \
    "$mcp_resolver"

  assert_rg_present "Spring wiring proof does not register the catalog authority" \
    "$audit_dir/spring-register-authority.txt" \
    'context\.registerBean\(SemanticModelCatalogService\.class' "$spring_wiring_test"
  assert_rg_present "Spring wiring proof does not register the resolver" \
    "$audit_dir/spring-register-resolver.txt" \
    'context\.registerBean\(SemanticServiceResolverImpl\.class\);' "$spring_wiring_test"
  assert_rg_present "Spring wiring proof does not register the MCP catalog" \
    "$audit_dir/spring-register-catalog.txt" \
    'context\.registerBean\(ModelCatalogService\.class\);' "$spring_wiring_test"
  assert_rg_present "Spring wiring proof does not refresh a real application context" \
    "$audit_dir/spring-context-refresh.txt" \
    'context\.refresh\(\)' "$spring_wiring_test"
  assert_rg_present "Spring wiring proof does not verify resolver authority use" \
    "$audit_dir/spring-verify-resolver-authority.txt" \
    'verify\(authority\)\.getAllModelNames\("tenant-a"\);' "$spring_wiring_test"
  assert_rg_present "Spring wiring proof does not verify MCP catalog authority use" \
    "$audit_dir/spring-verify-catalog-authority.txt" \
    'verify\(authority,[[:space:]]*times\(2\)\)\.namespaceCatalogView\("tenant-a"\);' \
    "$spring_wiring_test"
  assert_rg_present "Spring wiring proof permits legacy loader or bundle interactions" \
    "$audit_dir/spring-no-legacy-interactions.txt" \
    'verifyNoInteractions\(loader,[[:space:]]*bundles\);' "$spring_wiring_test"

  mapfile -d '' red_sources < <(
    find \
      "$ROOT_DIR/foggy-dataset-model-engine/src/test/java" \
      "$ROOT_DIR/foggy-runtime-api/src/test/java" \
      "$ROOT_DIR/foggy-fsscript/src/test/java" \
      "$ROOT_DIR/foggy-dataset-mcp/src/test/java" \
      -type f -name '*RedBaseline.java' -print0 | sort -z
  )
  printf '%s\n' "${red_sources[@]}" > "$audit_dir/remaining-red-sources.txt"
  [[ "${#red_sources[@]}" -eq 0 ]] || \
    fail "remaining RedBaseline sources=${#red_sources[@]}"

  actual="$(rg -c '^[[:space:]]*@Test\b' "$model_test" || true)"
  [[ "${actual:-0}" -eq 11 ]] || fail "model catalog test count drift: ${actual:-0}"
  actual="$(rg -c '^[[:space:]]*@Test\b' "$authority_test" || true)"
  [[ "${actual:-0}" -eq 5 ]] || fail "MCP authority test count drift: ${actual:-0}"
  actual="$(rg -c '^[[:space:]]*@Test\b' "$spring_wiring_test" || true)"
  [[ "${actual:-0}" -eq 1 ]] || fail "Spring wiring test count drift: ${actual:-0}"
  actual="$(rg -c '^[[:space:]]*@Test\b' "$resolver_test" || true)"
  [[ "${actual:-0}" -eq 11 ]] || fail "resolver test count drift: ${actual:-0}"
  actual="$(rg -c '^[[:space:]]*@Test\b' "$list_test" || true)"
  [[ "${actual:-0}" -eq 21 ]] || fail "list-models test count drift: ${actual:-0}"
  actual="$(rg -c '^[[:space:]]*@Test\b' "$controller_test" || true)"
  [[ "${actual:-0}" -eq 4 ]] || fail "controller test count drift: ${actual:-0}"

  printf '%s\n' \
    'direct catalog authority tests: 17 in 3 owning reports' \
    'supporting catalog consumer tests: 36 in 4 owning reports' \
    'total catalog evidence: 53 tests in 7 owning reports' \
    'consumer-owned names caches: 0' \
    'independent MCP watcher registrations: 0' \
    'sleep-driven catalog tests: 0' \
    'remaining RedBaseline sources: 0' \
    > "$audit_dir/summary.txt"
}

run_model_lane() {
  local lane_dir="$RUN_ROOT/model-catalog-authority"
  local log_file="$lane_dir/maven.log"
  mkdir -p "$lane_dir"
  : > "$lane_dir/.run-start"

  echo "[v933-batch6-catalog] running model catalog authority"
  if ! (cd "$ROOT_DIR" && mvn -B -pl foggy-dataset-model-engine -am \
      -P'!multi-db,model-lifecycle' \
      -DskipITs=true \
      -Dtest=SemanticModelCatalogServiceTest \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -Dv933.reportsDirectory="$lane_dir" \
      test -l "$log_file"); then
    fail "model catalog authority failed; log=$log_file"
  fi
  assert_report_set "$lane_dir" "${MODEL_SPECS[@]}"
}

run_mcp_lane() {
  local lane_dir="$RUN_ROOT/mcp-catalog-consumers"
  local report_dir="$lane_dir/surefire-reports"
  local owning_reports="$ROOT_DIR/foggy-dataset-mcp/target/surefire-reports"
  local log_file="$lane_dir/maven.log"
  local spec fqcn source text_report
  mkdir -p "$report_dir"
  : > "$lane_dir/.run-start"

  for spec in "${MCP_SPECS[@]}"; do
    fqcn="${spec%|*}"
    rm -f "$owning_reports/TEST-${fqcn}.xml" "$owning_reports/${fqcn}.txt"
  done

  echo "[v933-batch6-catalog] running MCP catalog consumers"
  if ! (cd "$ROOT_DIR" && mvn -B -pl foggy-dataset-mcp -am \
      -P'!multi-db' \
      -DskipITs=true \
      -Dtest='CatalogNamespaceAuthorityTest,CatalogAuthoritySpringWiringTest,SemanticServiceResolverImplTest,ListModelsToolTest,ListModelsCatalogControllerTest' \
      -Dsurefire.failIfNoSpecifiedTests=false \
      test -l "$log_file"); then
    fail "MCP catalog consumers failed; log=$log_file"
  fi

  for spec in "${MCP_SPECS[@]}"; do
    fqcn="${spec%|*}"
    source="$owning_reports/TEST-${fqcn}.xml"
    text_report="$owning_reports/${fqcn}.txt"
    [[ -f "$source" ]] || fail "missing MCP owning report: $source"
    [[ "$source" -nt "$lane_dir/.run-start" ]] || fail "stale MCP owning report: $source"
    cp "$source" "$report_dir/"
    if [[ -f "$text_report" && "$text_report" -nt "$lane_dir/.run-start" ]]; then
      cp "$text_report" "$report_dir/"
    fi
  done
  assert_report_set "$lane_dir" "${MCP_SPECS[@]}"
}

audit_sources
run_model_lane
run_mcp_lane

cat > "$RUN_ROOT/summary.env" <<SUMMARY
run_id=$RUN_ID
status=passed
model_tests=11
mcp_authority_tests=5
mcp_spring_wiring_tests=1
mcp_resolver_tests=11
mcp_list_models_tests=21
mcp_controller_tests=4
direct_authority_tests=17
supporting_consumer_tests=36
total_tests=53
owning_reports=7
failures=0
errors=0
skipped=0
remaining_red_suites=0
remaining_red_tests=0
SUMMARY

find "$RUN_ROOT" -type f ! -name SHA256SUMS -print0 \
  | sort -z | xargs -0 sha256sum > "$RUN_ROOT/SHA256SUMS"
printf '%s\n' "$RUN_ID" > "$ROOT_DIR/target/v933-batch6-catalog/latest-run-id"

echo "[v933-batch6-catalog] COMPLETE run=$RUN_ID tests=53 reports=7 failures=0 errors=0 skipped=0"
