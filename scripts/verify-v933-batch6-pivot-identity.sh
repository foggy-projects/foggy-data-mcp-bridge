#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$ROOT_DIR/target/v933-batch6-pivot/runs/$RUN_ID"
REPORT_ASSERTION="$ROOT_DIR/scripts/assert-v933-test-report.sh"
MODULE_DIR="$ROOT_DIR/foggy-dataset-model"
REPORT_DIR="$MODULE_DIR/target/surefire-reports"

fail() {
  echo "[v933-pivot-identity] ERROR: $*" >&2
  exit 1
}

require_pattern() {
  local source_file="$1"
  local pattern="$2"
  local audit_name="$3"
  rg -n "$pattern" "$source_file" \
    > "$RUN_ROOT/source-audit/contract-$audit_name.txt" || \
    fail "required source contract is missing: $audit_name"
}

assert_test_count() {
  local source_file="$1"
  local expected="$2"
  local actual
  actual="$(rg -c '^[[:space:]]*@Test\b' "$source_file" || true)"
  [[ "${actual:-0}" -eq "$expected" ]] || \
    fail "test count drift in ${source_file#$ROOT_DIR/}: ${actual:-0}, expected $expected"
}

collect_report() {
  local lane="$1"
  local marker="$2"
  local fqcn="$3"
  local expected="$4"
  local source_report="$REPORT_DIR/TEST-$fqcn.xml"
  local assertion_dir="$RUN_ROOT/$lane/report-assertion"

  [[ -f "$source_report" ]] || fail "$lane owning report is missing: $fqcn"
  [[ "$source_report" -nt "$marker" ]] || fail "$lane owning report is stale: $fqcn"
  rm -rf "$assertion_dir"
  mkdir -p "$assertion_dir"
  ln "$source_report" "$assertion_dir/TEST-$fqcn.xml"
  V933_RUN_MARKER="$marker" "$REPORT_ASSERTION" "$assertion_dir" "$fqcn" "$expected"
  cp -p "$source_report" "$RUN_ROOT/$lane/surefire-reports/"
  rm -rf "$assertion_dir"
}

on_exit() {
  local status="$?"
  if [[ "$status" -eq 0 ]]; then
    echo "[v933-pivot-identity] PASS run=$RUN_ID evidence=$RUN_ROOT"
  else
    echo "[v933-pivot-identity] FAILED run=$RUN_ID evidence=$RUN_ROOT" >&2
  fi
}
trap on_exit EXIT

[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
[[ ! -e "$RUN_ROOT" ]] || fail "run directory already exists: $RUN_ROOT"
[[ -x "$REPORT_ASSERTION" ]] || fail "report assertion is not executable"
for command_name in cp find git grep ln mkdir mvn pgrep readlink rg rm sed sha256sum sort wc xargs; do
  command -v "$command_name" >/dev/null 2>&1 || \
    fail "required command is missing: $command_name"
done
while IFS= read -r process_id; do
  [[ -n "$process_id" ]] || continue
  process_cwd="$(readlink -f "/proc/$process_id/cwd" 2>/dev/null || true)"
  if [[ "$process_cwd" == "$ROOT_DIR" || "$process_cwd" == "$ROOT_DIR/"* ]]; then
    fail "another Maven/Surefire process is already running in this workspace: pid=$process_id"
  fi
done < <(pgrep -f '[o]rg.codehaus.plexus.classworlds.launcher.Launcher|[s]urefirebooter' || true)

mkdir -p \
  "$RUN_ROOT/direct/surefire-reports" \
  "$RUN_ROOT/supporting/surefire-reports" \
  "$RUN_ROOT/source-audit"

PIVOT_PIPELINE="$MODULE_DIR/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotPipeline.java"
PIVOT_STRONG_IDENTITY="$MODULE_DIR/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheStrongIdentity.java"
PIVOT_TELEMETRY="$MODULE_DIR/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheTelemetry.java"
PIVOT_DIAGNOSTICS="$MODULE_DIR/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotDiagnosticCollector.java"
PIVOT_PUBLIC_IDENTITY="$MODULE_DIR/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheModelIdentity.java"
PIVOT_PUBLIC_PROVIDER="$MODULE_DIR/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheModelIdentityProvider.java"
SEMANTIC_CONTEXT="$MODULE_DIR/src/main/java/com/foggyframework/dataset/db/model/semantic/domain/SemanticRequestContext.java"
SEMANTIC_SERVICE="$MODULE_DIR/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticQueryServiceV3Impl.java"
QUERY_FACADE="$MODULE_DIR/src/main/java/com/foggyframework/dataset/db/model/service/impl/QueryFacadeImpl.java"
MODEL_RESULT_CONTEXT="$MODULE_DIR/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/ModelResultContext.java"

DIRECT_STRONG="$MODULE_DIR/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheStrongIdentityTest.java"
DIRECT_PIPELINE="$MODULE_DIR/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotPipelineCatalogIdentityTest.java"
DIRECT_FACADE="$MODULE_DIR/src/test/java/com/foggyframework/dataset/db/model/lifecycle/catalog/QueryFacadeCatalogIdentityTest.java"
DIRECT_CONTEXT="$MODULE_DIR/src/test/java/com/foggyframework/dataset/db/model/semantic/domain/SemanticRequestContextTest.java"
SUPPORT_TELEMETRY="$MODULE_DIR/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheTelemetryTest.java"
SUPPORT_OPERATIONAL="$MODULE_DIR/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheOperationalSpiTest.java"
SUPPORT_INTEGRATION="$MODULE_DIR/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotIntegrationTest.java"

SOURCE_FILES=(
  "scripts/verify-v933-batch6-pivot-identity.sh"
  "scripts/assert-v933-test-report.sh"
  "foggy-dataset-model/pom.xml"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotPipeline.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheStrongIdentity.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheTelemetry.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotDiagnosticCollector.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheModelIdentity.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheModelIdentityProvider.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/domain/SemanticRequestContext.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticQueryServiceV3Impl.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/service/impl/QueryFacadeImpl.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/ModelResultContext.java"
  "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheStrongIdentityTest.java"
  "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotPipelineCatalogIdentityTest.java"
  "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/lifecycle/catalog/QueryFacadeCatalogIdentityTest.java"
  "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/semantic/domain/SemanticRequestContextTest.java"
  "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheTelemetryTest.java"
  "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotOuterCacheOperationalSpiTest.java"
  "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotIntegrationTest.java"
)
for source_file in "${SOURCE_FILES[@]}"; do
  [[ -f "$ROOT_DIR/$source_file" ]] || fail "evidence source is missing: $source_file"
done

(cd "$ROOT_DIR" && sha256sum "${SOURCE_FILES[@]}") \
  > "$RUN_ROOT/source-audit/source-files.sha256"
git -C "$ROOT_DIR" rev-parse HEAD > "$RUN_ROOT/source-audit/git-head.txt"
git -C "$ROOT_DIR" status --short --untracked-files=all \
  > "$RUN_ROOT/source-audit/git-status.txt"

assert_test_count "$DIRECT_STRONG" 4
assert_test_count "$DIRECT_PIPELINE" 8
assert_test_count "$DIRECT_FACADE" 6
assert_test_count "$DIRECT_CONTEXT" 20
assert_test_count "$SUPPORT_TELEMETRY" 3
assert_test_count "$SUPPORT_OPERATIONAL" 3
assert_test_count "$SUPPORT_INTEGRATION" 55

if rg -n '@Disabled|Assumptions|Thread\.sleep|TimeUnit\.sleep' \
    "$DIRECT_STRONG" "$DIRECT_PIPELINE" "$DIRECT_FACADE" "$DIRECT_CONTEXT" \
    > "$RUN_ROOT/source-audit/direct-skip-or-sleep.txt"; then
  fail "direct Pivot identity proof contains skip or sleep shortcuts"
fi
if rg -n 'substring\([[:space:]]*0[[:space:]]*,[[:space:]]*16|firstNonBlank|System\.identityHashCode|UUID\.randomUUID' \
    "$PIVOT_PIPELINE" "$PIVOT_STRONG_IDENTITY" "$PIVOT_TELEMETRY" \
    > "$RUN_ROOT/source-audit/forbidden-identity-fallbacks.txt"; then
  fail "truncated/process-local/fallback Pivot cache identity remains"
fi

require_pattern "$PIVOT_PIPELINE" 'resolveJdbcQueryModel\(model, context\.getNamespace\(\)\)' "pipeline-resolves-catalog"
require_pattern "$PIVOT_PIPELINE" 'PivotOuterCacheStrongIdentity\.assess\(catalogResolution' "pipeline-assesses-strong-identity"
require_pattern "$PIVOT_PIPELINE" 'context = context\.withCatalogResolution\(catalogResolution\)' "pipeline-pins-catalog"
require_pattern "$PIVOT_PIPELINE" 'resultContext\.pinCatalogResolution\(' "managed-relation-pre-pin"
require_pattern "$PIVOT_PIPELINE" 'if \(!cacheEvaluation\.refused\(\)\)' "lookup-refused-gate"
require_pattern "$PIVOT_PIPELINE" 'cacheEvaluation\.refused\(\)' "store-refused-gate"
require_pattern "$PIVOT_PIPELINE" 'providerFailureType = e\.getClass\(\)' "provider-failure-class-only"
require_pattern "$PIVOT_PIPELINE" 'PivotOuterCacheTelemetry\.ModelIdentity\.from\(' "provider-additive-identity"
require_pattern "$PIVOT_STRONG_IDENTITY" 'sorted\.sort\(DatasourceBindingIdentity::compareTo\)' "sorted-exact-bindings"
require_pattern "$PIVOT_STRONG_IDENTITY" 'append\(encoded, "catalogGeneration"' "catalog-generation-identity"
require_pattern "$PIVOT_STRONG_IDENTITY" 'append\(encoded, "sourceRevision"' "source-revision-identity"
require_pattern "$PIVOT_STRONG_IDENTITY" 'append\(encoded, "bindingGeneration"' "binding-generation-identity"
require_pattern "$PIVOT_STRONG_IDENTITY" 'MessageDigest\.getInstance\("SHA-256"\)' "full-sha256-identity"
require_pattern "$PIVOT_TELEMETRY" 'return "v2:" \+ sha256\(' "v2-full-key"
require_pattern "$PIVOT_TELEMETRY" 'SUPPLEMENTARY_IDENTITY_PROVIDER_FAILED_REASON' "provider-failure-refusal"
require_pattern "$PIVOT_TELEMETRY" 'return supplementaryProviderFailed' "provider-failure-explicit-refused"
require_pattern "$PIVOT_DIAGNOSTICS" 'supplementaryProviderFailureClass' "provider-failure-safe-diagnostic"
require_pattern "$SEMANTIC_CONTEXT" 'withCatalogResolution\(CatalogResolution<QueryModel> resolution\)' "semantic-context-catalog-pin"
require_pattern "$SEMANTIC_SERVICE" 'resultContext\.pinCatalogResolution\(' "semantic-service-pre-pin"
require_pattern "$QUERY_FACADE" 'context\.pinCatalogResolution\(resolution, namespace\)' "query-facade-pre-pin"
require_pattern "$MODEL_RESULT_CONTEXT" 'pinCatalogResolution\(' "result-context-atomic-pin"
require_pattern "$DIRECT_PIPELINE" 'completeLifecycleStillRefusesCacheWhenSupplementaryProviderThrows' "provider-failure-direct-case"
require_pattern "$DIRECT_PIPELINE" 'directManagedRelationContextCarriesTheSameCatalogPin' "managed-relation-direct-pin-case"
require_pattern "$DIRECT_PIPELINE" 'semanticServiceBuildsAResultContextWithTheExactCatalogPin' "semantic-service-direct-pin-case"
require_pattern "$DIRECT_PIPELINE" 'verify\(cache, never\(\)\)\.lookup' "provider-failure-no-lookup"
require_pattern "$DIRECT_PIPELINE" 'verify\(cache, never\(\)\)\.store' "provider-failure-no-store"
require_pattern "$PIVOT_PUBLIC_IDENTITY" 'public record PivotOuterCacheModelIdentity\(String bundleFingerprint' "public-record-components"
require_pattern "$PIVOT_PUBLIC_PROVIDER" 'PivotOuterCacheModelIdentity resolve\(String namespace, String model, QueryModel queryModel\)' "public-provider-sam"
require_pattern "$PIVOT_PIPELINE" 'public OuterCacheOptions\(boolean enabled, long ttlMillis, int maximumSize\)' "legacy-options-constructor"

printf '%s\n' \
  'direct expected: 38 tests / 4 owning reports' \
  'supporting expected: 61 tests / 3 owning reports' \
  'total expected: 99 tests / 7 owning reports' \
  'provider exception: complete lifecycle identity still no lookup/no store' \
  'supporting exception: PivotIntegrationTest has one bounded 20ms TTL-expiry sleep' \
  > "$RUN_ROOT/source-audit/summary.txt"

DIRECT_FQCNS=(
  'com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheStrongIdentityTest:4'
  'com.foggyframework.dataset.db.model.engine.pivot.PivotPipelineCatalogIdentityTest:8'
  'com.foggyframework.dataset.db.model.lifecycle.catalog.QueryFacadeCatalogIdentityTest:6'
  'com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContextTest:20'
)
SUPPORT_FQCNS=(
  'com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheTelemetryTest:3'
  'com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheOperationalSpiTest:3'
  'com.foggyframework.dataset.db.model.engine.pivot.PivotIntegrationTest:55'
)

for specification in "${DIRECT_FQCNS[@]}"; do
  fqcn="${specification%:*}"
  rm -f "$REPORT_DIR/TEST-$fqcn.xml"
done
DIRECT_MARKER="$RUN_ROOT/direct/.run-start"
: > "$DIRECT_MARKER"
echo "[v933-pivot-identity] running direct Pivot/catalog identity lane"
(cd "$ROOT_DIR" && mvn -B \
  -pl foggy-dataset-model \
  -P'!multi-db' \
  -DskipITs=true \
  -Dtest=PivotOuterCacheStrongIdentityTest,PivotPipelineCatalogIdentityTest,QueryFacadeCatalogIdentityTest,SemanticRequestContextTest \
  -Dsurefire.failIfNoSpecifiedTests=true \
  -DfailIfNoTests=true \
  test -l "$RUN_ROOT/direct/maven.log") || \
  fail "direct Pivot identity Maven lane failed"
for specification in "${DIRECT_FQCNS[@]}"; do
  collect_report direct "$DIRECT_MARKER" "${specification%:*}" "${specification#*:}"
done
[[ "$(find "$RUN_ROOT/direct/surefire-reports" -maxdepth 1 -type f -name 'TEST-*.xml' | wc -l)" -eq 4 ]] || \
  fail "direct copied report count drifted"

for specification in "${SUPPORT_FQCNS[@]}"; do
  fqcn="${specification%:*}"
  rm -f "$REPORT_DIR/TEST-$fqcn.xml"
done
SUPPORT_MARKER="$RUN_ROOT/supporting/.run-start"
: > "$SUPPORT_MARKER"
echo "[v933-pivot-identity] running supporting Pivot behavior lane"
(cd "$ROOT_DIR" && mvn -B \
  -pl foggy-dataset-model \
  -P'!multi-db' \
  -DskipITs=true \
  -Dtest=PivotOuterCacheTelemetryTest,PivotOuterCacheOperationalSpiTest,PivotIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=true \
  -DfailIfNoTests=true \
  test -l "$RUN_ROOT/supporting/maven.log") || \
  fail "supporting Pivot Maven lane failed"
for specification in "${SUPPORT_FQCNS[@]}"; do
  collect_report supporting "$SUPPORT_MARKER" "${specification%:*}" "${specification#*:}"
done
[[ "$(find "$RUN_ROOT/supporting/surefire-reports" -maxdepth 1 -type f -name 'TEST-*.xml' | wc -l)" -eq 3 ]] || \
  fail "supporting copied report count drifted"

if rg -n 'sensitive-provider-token|manual-sensitive-token|provider-bundle|manual-bundle|binding:sales|backend:sales' \
    "$RUN_ROOT/direct/maven.log" "$RUN_ROOT/supporting/maven.log" \
    "$RUN_ROOT/direct/surefire-reports" "$RUN_ROOT/supporting/surefire-reports" \
    > "$RUN_ROOT/source-audit/sensitive-output.txt"; then
  fail "Pivot evidence output exposes raw supplementary/manual/binding identity material"
fi

(cd "$ROOT_DIR" && sha256sum -c "$RUN_ROOT/source-audit/source-files.sha256") \
  > "$RUN_ROOT/source-audit/source-hash-check.txt" || \
  fail "source files changed during the authoritative run"

cat > "$RUN_ROOT/summary.txt" <<SUMMARY
run_id=$RUN_ID
criterion=CACHE-GEN/Pivot
direct_tests=38
direct_reports=4
supporting_tests=61
supporting_reports=3
total_tests=99
total_reports=7
failures=0
errors=0
skipped=0
provider_failure_fail_closed=true
catalog_pin_preserved=true
full_sha256_identity=true
manual_tokens_additive_only=true
SUMMARY

(cd "$RUN_ROOT" && \
  find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)
(cd "$RUN_ROOT" && sha256sum -c SHA256SUMS)
