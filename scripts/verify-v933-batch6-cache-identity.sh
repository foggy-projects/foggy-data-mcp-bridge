#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
TARGET_ROOT="$ROOT_DIR/target/v933-batch6-cache-identity"
RUN_ROOT="$TARGET_ROOT/runs/$RUN_ID"
LOCK_FILE="$TARGET_ROOT/.authority.lock"
LATEST_RUN_ID="$TARGET_ROOT/latest-run-id"
LATEST_TMP="$TARGET_ROOT/.latest-run-id.$RUN_ID.tmp"
REPORT_ASSERTION="$ROOT_DIR/scripts/assert-v933-test-report.sh"
MODEL_MODULE="$ROOT_DIR/foggy-dataset-model"
CACHE_MODULE="$ROOT_DIR/addons/foggy-dataset-model-cache"
MODEL_REPORT_DIR="$MODEL_MODULE/target/surefire-reports"
CACHE_REPORT_DIR="$CACHE_MODULE/target/surefire-reports"
SUCCESS_FINALIZED=0

fail() {
  echo "[v933-cache-identity] ERROR: $*" >&2
  exit 1
}

on_exit() {
  local status="$?"
  rm -f "$LATEST_TMP"
  if [[ "$status" -eq 0 && "$SUCCESS_FINALIZED" -eq 1 ]]; then
    echo "[v933-cache-identity] PASS run=$RUN_ID evidence=$RUN_ROOT"
  else
    echo "[v933-cache-identity] FAILED run=$RUN_ID evidence=$RUN_ROOT" >&2
  fi
}
trap on_exit EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

[[ "$#" -le 1 ]] || fail "expected at most one RUN_ID argument"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
[[ "${#RUN_ID}" -le 80 ]] || fail "run id is longer than 80 characters"

mkdir -p "$TARGET_ROOT/runs"
exec 9>"$LOCK_FILE"
flock -n 9 || fail "another cache-identity authority run holds $LOCK_FILE"
[[ ! -e "$RUN_ROOT" ]] || fail "run directory already exists: $RUN_ROOT"
[[ -x "$REPORT_ASSERTION" ]] || fail "report assertion helper is not executable"

required_commands=(
  awk basename bash cat cp date find flock git grep ln mkdir mv mvn pgrep readlink
  rg rm sed sha256sum sort tee wc xargs
)
for command_name in "${required_commands[@]}"; do
  command -v "$command_name" >/dev/null 2>&1 || \
    fail "required command is missing: $command_name"
done

EXTERNAL_MAVEN_FLAGS="${MAVEN_ARGS:-} ${MAVEN_OPTS:-} ${JAVA_TOOL_OPTIONS:-}"
if [[ "$EXTERNAL_MAVEN_FLAGS" =~ (^|[[:space:]])-D(skipTests|maven\.test\.skip|skipITs|skipUnitTests)($|=|[[:space:]]) ]]; then
  fail "external Maven test-skip properties are forbidden"
fi

while IFS= read -r process_id; do
  [[ -n "$process_id" && "$process_id" != "$$" ]] || continue
  process_cwd="$(readlink -f "/proc/$process_id/cwd" 2>/dev/null || true)"
  if [[ "$process_cwd" == "$ROOT_DIR" || "$process_cwd" == "$ROOT_DIR/"* ]]; then
    fail "another Maven/Surefire process is active in this workspace: pid=$process_id cwd=$process_cwd"
  fi
done < <(pgrep -f \
  '[o]rg\.codehaus\.plexus\.classworlds\.launcher\.Launcher|[s]urefirebooter' || true)

mkdir -p \
  "$RUN_ROOT/lanes/model-pin/surefire-reports" \
  "$RUN_ROOT/lanes/addon-strong-key/surefire-reports" \
  "$RUN_ROOT/lanes/cross-application-context/surefire-reports" \
  "$RUN_ROOT/source-audit"
printf '%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$RUN_ROOT/started-at.txt"

MODEL_TEST="$MODEL_MODULE/src/test/java/com/foggyframework/dataset/db/model/lifecycle/catalog/QueryFacadeCatalogIdentityTest.java"
CACHE_TEST_ROOT="$CACHE_MODULE/src/test/java/com/foggyframework/dataset/db/model/cache/provider"
STRONG_KEY_TEST="$CACHE_TEST_ROOT/QueryCacheKeyBuilderStrongIdentityTest.java"
CAFFEINE_TEST="$CACHE_TEST_ROOT/CaffeineQueryCacheProviderTest.java"
REDIS_TEST="$CACHE_TEST_ROOT/RedisQueryCacheProviderTest.java"
SQLITE_TEST="$CACHE_TEST_ROOT/CaffeineQueryCacheProviderDataSourceIsolationIntegrationTest.java"
CROSS_CONTEXT_TEST="$CACHE_TEST_ROOT/QueryCacheKeyCrossApplicationContextTest.java"

SOURCE_FILES=(
  "pom.xml"
  "foggy-dataset-model/pom.xml"
  "addons/foggy-dataset-model-cache/pom.xml"
  "scripts/assert-v933-test-report.sh"
  "scripts/verify-v933-batch6-cache-identity.sh"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/service/impl/QueryFacadeImpl.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/ModelResultContext.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/catalog/CatalogResolution.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/identity/CatalogGeneration.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/identity/CatalogIdentity.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/identity/SourceRevision.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/identity/DatasourceBindingGeneration.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/identity/DatasourceBindingIdentity.java"
  "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/lifecycle/catalog/QueryFacadeCatalogIdentityTest.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/config/QueryCacheAutoConfiguration.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/config/QueryCacheProperties.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/fingerprint/QueryFingerprint.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/fingerprint/QueryFingerprintBuilder.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/fingerprint/SecurityPolicyFingerprint.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/fingerprint/StableCanonicalEncoder.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/provider/QueryCacheKeyBuilder.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/provider/CaffeineQueryCacheProvider.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/provider/RedisQueryCacheProvider.java"
  "addons/foggy-dataset-model-cache/src/test/java/com/foggyframework/dataset/db/model/cache/provider/QueryCacheKeyBuilderStrongIdentityTest.java"
  "addons/foggy-dataset-model-cache/src/test/java/com/foggyframework/dataset/db/model/cache/provider/CaffeineQueryCacheProviderTest.java"
  "addons/foggy-dataset-model-cache/src/test/java/com/foggyframework/dataset/db/model/cache/provider/RedisQueryCacheProviderTest.java"
  "addons/foggy-dataset-model-cache/src/test/java/com/foggyframework/dataset/db/model/cache/provider/CaffeineQueryCacheProviderDataSourceIsolationIntegrationTest.java"
  "addons/foggy-dataset-model-cache/src/test/java/com/foggyframework/dataset/db/model/cache/provider/QueryCacheKeyCrossApplicationContextTest.java"
)
for source_file in "${SOURCE_FILES[@]}"; do
  [[ -f "$ROOT_DIR/$source_file" ]] || fail "evidence source is missing: $source_file"
done

SOURCE_LIST="$RUN_ROOT/source-audit/source-files.list0"
(
  cd "$ROOT_DIR"
  {
    printf '%s\0' "${SOURCE_FILES[@]}"
    find . -type d -name target -prune -o -type f -name pom.xml -printf '%P\0'
  } | LC_ALL=C sort -zu > "$SOURCE_LIST"
)
(cd "$ROOT_DIR" && xargs -0 sha256sum < "$SOURCE_LIST") \
  > "$RUN_ROOT/source-audit/source-files.sha256"
git -C "$ROOT_DIR" rev-parse HEAD > "$RUN_ROOT/source-audit/git-head.txt"
git -C "$ROOT_DIR" status --short --untracked-files=all \
  > "$RUN_ROOT/source-audit/git-status.txt"

assert_test_count() {
  local source_file="$1"
  local expected="$2"
  local actual
  actual="$(rg -c '^[[:space:]]*@Test\b' "$source_file" || true)"
  [[ "${actual:-0}" -eq "$expected" ]] || \
    fail "test count drift in ${source_file#$ROOT_DIR/}: ${actual:-0}, expected=$expected"
}

assert_test_count "$MODEL_TEST" 6
assert_test_count "$STRONG_KEY_TEST" 12
assert_test_count "$CAFFEINE_TEST" 30
assert_test_count "$REDIS_TEST" 25
assert_test_count "$SQLITE_TEST" 1
assert_test_count "$CROSS_CONTEXT_TEST" 1

if rg -n '@Disabled|Assumptions|Thread\.sleep|TimeUnit\.[A-Za-z]+\.sleep|\bassume(True|False|That)?[[:space:]]*\(' \
    "$MODEL_TEST" "$STRONG_KEY_TEST" "$CAFFEINE_TEST" "$REDIS_TEST" \
    "$SQLITE_TEST" "$CROSS_CONTEXT_TEST" \
    > "$RUN_ROOT/source-audit/forbidden-skip-or-sleep.txt"; then
  fail "cache identity evidence contains a skip or sleep shortcut"
fi

KEY_BUILDER="$CACHE_MODULE/src/main/java/com/foggyframework/dataset/db/model/cache/provider/QueryCacheKeyBuilder.java"
CAFFEINE_PROVIDER="$CACHE_MODULE/src/main/java/com/foggyframework/dataset/db/model/cache/provider/CaffeineQueryCacheProvider.java"
REDIS_PROVIDER="$CACHE_MODULE/src/main/java/com/foggyframework/dataset/db/model/cache/provider/RedisQueryCacheProvider.java"
STABLE_ENCODER="$CACHE_MODULE/src/main/java/com/foggyframework/dataset/db/model/cache/fingerprint/StableCanonicalEncoder.java"
QUERY_FACADE="$MODEL_MODULE/src/main/java/com/foggyframework/dataset/db/model/service/impl/QueryFacadeImpl.java"
if rg -n 'InstanceIdentityRegistry|System\.identityHashCode|UUID\.randomUUID|resolveDataSourceIdentity' \
    "$KEY_BUILDER" > "$RUN_ROOT/source-audit/forbidden-process-local-key.txt"; then
  fail "cache key builder contains a process-local/object-identity fallback"
fi

require_pattern() {
  local source_file="$1"
  local pattern="$2"
  local audit_name="$3"
  rg -n "$pattern" "$source_file" \
    > "$RUN_ROOT/source-audit/contract-$audit_name.txt" || \
    fail "required cache identity contract is missing: $audit_name"
}

require_pattern "$KEY_BUILDER" 'segment\("namespace"' namespace
require_pattern "$KEY_BUILDER" 'segment\("requestedModel"' requested-model
require_pattern "$KEY_BUILDER" 'segment\("canonicalModel"' canonical-model
require_pattern "$KEY_BUILDER" '"catalogGeneration"' catalog-generation
require_pattern "$KEY_BUILDER" '"sourceRevision"' source-revision
require_pattern "$KEY_BUILDER" 'entries\.sort\(' sorted-bindings
require_pattern "$KEY_BUILDER" 'segment\("bindingKey"' binding-key
require_pattern "$KEY_BUILDER" 'segment\("backendId"' binding-backend
require_pattern "$KEY_BUILDER" '"bindingGeneration"' binding-generation
require_pattern "$KEY_BUILDER" 'segment\("securityPolicy"' security-policy
require_pattern "$KEY_BUILDER" 'StableCanonicalEncoder\.sha256\(payload\)' full-key-digest
require_pattern "$STABLE_ENCODER" 'DigestUtils\.sha256Hex' sha256-implementation
for provider_file in "$CAFFEINE_PROVIDER" "$REDIS_PROVIDER"; do
  provider_name="$(basename "$provider_file" .java)"
  require_pattern "$provider_file" 'new QueryCacheKeyBuilder\(' "$provider_name-builder"
  require_pattern "$provider_file" 'cacheKeyBuilder\.buildL1CacheKey\(' "$provider_name-l1"
  require_pattern "$provider_file" 'cacheKeyBuilder\.buildL2CacheKey\(' "$provider_name-l2"
done
require_pattern "$QUERY_FACADE" 'queryModelLoader\.resolveJdbcQueryModel\(' query-facade-resolve
require_pattern "$QUERY_FACADE" 'context\.pinCatalogResolution\(' query-facade-pin
require_pattern "$CROSS_CONTEXT_TEST" 'new ApplicationContextRunner\(\)' contexts-real
require_pattern "$CROSS_CONTEXT_TEST" 'AutoConfigurations\.of\(QueryCacheAutoConfiguration\.class\)' contexts-production-auto-config
require_pattern "$CROSS_CONTEXT_TEST" 'contextRunner\.run\(firstContext -> contextRunner\.run\(secondContext ->' contexts-two-runs
require_pattern "$CROSS_CONTEXT_TEST" 'assertNotSame\(firstContext, secondContext\)' contexts-distinct-context
require_pattern "$CROSS_CONTEXT_TEST" 'assertNotSame\(firstProvider, secondProvider\)' contexts-distinct-provider
require_pattern "$CROSS_CONTEXT_TEST" 'assertNotSame\(firstBuilder, secondBuilder\)' contexts-distinct-builder
require_pattern "$CROSS_CONTEXT_TEST" 'assertEquals\(firstL1, secondL1\)' contexts-l1-equal
require_pattern "$CROSS_CONTEXT_TEST" 'assertEquals\(firstL2, secondL2\)' contexts-l2-equal
require_pattern "$CROSS_CONTEXT_TEST" 'assertNotEquals\(firstL1, catalogChangedL1\)' contexts-catalog-rotation
require_pattern "$CROSS_CONTEXT_TEST" 'assertNotEquals\(firstL2, catalogChangedL2\)' contexts-catalog-l2-rotation
require_pattern "$CROSS_CONTEXT_TEST" 'assertNotEquals\(firstL1, bindingChangedL1\)' contexts-binding-l1-rotation
require_pattern "$CROSS_CONTEXT_TEST" 'assertNotEquals\(firstL2, bindingChangedL2\)' contexts-binding-rotation
require_pattern "$SQLITE_TEST" 'org\.sqlite\.JDBC' sqlite-driver
require_pattern "$SQLITE_TEST" 'jdbc:sqlite:' sqlite-url
require_pattern "$SQLITE_TEST" 'sentinel-a' sqlite-sentinel-a
require_pattern "$SQLITE_TEST" 'sentinel-b' sqlite-sentinel-b
require_pattern "$SQLITE_TEST" 'DelegatingDataSource' sqlite-routing-boundary
require_pattern "$SQLITE_TEST" 'assertEquals\(0L, cacheProvider\.getStats\(\)\.get\("l2EstimatedSize"\)\)' sqlite-no-cache-write

collect_report() {
  local lane_dir="$1"
  local report_dir="$2"
  local marker="$3"
  local fqcn="$4"
  local expected_tests="$5"
  local source_report="$report_dir/TEST-$fqcn.xml"
  local assertion_dir="$lane_dir/report-assertion"

  [[ -f "$source_report" ]] || fail "owning report is missing: $fqcn"
  [[ "$source_report" -nt "$marker" ]] || fail "owning report is stale: $fqcn"
  rm -rf "$assertion_dir"
  mkdir -p "$assertion_dir"
  ln "$source_report" "$assertion_dir/TEST-$fqcn.xml"
  V933_RUN_MARKER="$marker" \
    "$REPORT_ASSERTION" "$assertion_dir" "$fqcn" "$expected_tests" \
    >> "$lane_dir/report-assertions.txt"
  cp -p "$source_report" "$lane_dir/surefire-reports/"
  rm -rf "$assertion_dir"
}

assert_lane_log() {
  local lane_dir="$1"
  local expected_classes="$2"
  local log_file="$lane_dir/maven.log"
  local success_count running_count

  success_count="$(grep -c '^\[INFO\] BUILD SUCCESS' "$log_file" || true)"
  [[ "$success_count" -eq 1 ]] || \
    fail "lane $(basename "$lane_dir") BUILD SUCCESS count=$success_count"
  if rg -n '^\[INFO\] BUILD FAILURE' "$log_file" > "$lane_dir/build-failure-audit.txt"; then
    fail "lane $(basename "$lane_dir") contains BUILD FAILURE"
  fi
  running_count="$(grep -c '^\[INFO\] Running ' "$log_file" || true)"
  [[ "$running_count" -eq "$expected_classes" ]] || \
    fail "lane $(basename "$lane_dir") running classes=$running_count, expected=$expected_classes"
}

MODEL_FQCN='com.foggyframework.dataset.db.model.lifecycle.catalog.QueryFacadeCatalogIdentityTest'
MODEL_LANE="$RUN_ROOT/lanes/model-pin"
rm -f "$MODEL_REPORT_DIR/TEST-$MODEL_FQCN.xml"
: > "$MODEL_LANE/.run-start"
echo "[v933-cache-identity] running Step 2 model pin lane"
if ! (cd "$ROOT_DIR" && mvn -B \
    -pl foggy-dataset-model -am \
    -P'!multi-db' \
    -DskipITs=true \
    -Dtest=QueryFacadeCatalogIdentityTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -DfailIfNoTests=false \
    test -l "$MODEL_LANE/maven.log"); then
  fail "Step 2 model pin Maven lane failed"
fi
collect_report "$MODEL_LANE" "$MODEL_REPORT_DIR" "$MODEL_LANE/.run-start" \
  "$MODEL_FQCN" 6
assert_lane_log "$MODEL_LANE" 1
[[ "$(find "$MODEL_LANE/surefire-reports" -maxdepth 1 -type f -name 'TEST-*.xml' | wc -l)" -eq 1 ]] || \
  fail "Step 2 model report count drifted"

ADDON_SPECS=(
  'com.foggyframework.dataset.db.model.cache.provider.QueryCacheKeyBuilderStrongIdentityTest:12'
  'com.foggyframework.dataset.db.model.cache.provider.CaffeineQueryCacheProviderTest:30'
  'com.foggyframework.dataset.db.model.cache.provider.RedisQueryCacheProviderTest:25'
  'com.foggyframework.dataset.db.model.cache.provider.CaffeineQueryCacheProviderDataSourceIsolationIntegrationTest:1'
)
ADDON_LANE="$RUN_ROOT/lanes/addon-strong-key"
for specification in "${ADDON_SPECS[@]}"; do
  fqcn="${specification%:*}"
  rm -f "$CACHE_REPORT_DIR/TEST-$fqcn.xml"
done
: > "$ADDON_LANE/.run-start"
echo "[v933-cache-identity] running Step 2 addon strong-key lane"
if ! (cd "$ROOT_DIR" && mvn -B \
    -pl addons/foggy-dataset-model-cache -am \
    -P'!multi-db' \
    -DskipITs=true \
    -Dtest=QueryCacheKeyBuilderStrongIdentityTest,CaffeineQueryCacheProviderTest,RedisQueryCacheProviderTest,CaffeineQueryCacheProviderDataSourceIsolationIntegrationTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -DfailIfNoTests=false \
    test -l "$ADDON_LANE/maven.log"); then
  fail "Step 2 addon strong-key Maven lane failed"
fi
for specification in "${ADDON_SPECS[@]}"; do
  collect_report "$ADDON_LANE" "$CACHE_REPORT_DIR" "$ADDON_LANE/.run-start" \
    "${specification%:*}" "${specification#*:}"
done
assert_lane_log "$ADDON_LANE" 4
[[ "$(find "$ADDON_LANE/surefire-reports" -maxdepth 1 -type f -name 'TEST-*.xml' | wc -l)" -eq 4 ]] || \
  fail "Step 2 addon report count drifted"

CROSS_FQCN='com.foggyframework.dataset.db.model.cache.provider.QueryCacheKeyCrossApplicationContextTest'
CROSS_LANE="$RUN_ROOT/lanes/cross-application-context"
rm -f "$CACHE_REPORT_DIR/TEST-$CROSS_FQCN.xml"
: > "$CROSS_LANE/.run-start"
echo "[v933-cache-identity] running Step 3 cross-ApplicationContext lane"
if ! (cd "$ROOT_DIR" && mvn -B \
    -pl addons/foggy-dataset-model-cache -am \
    -P'!multi-db' \
    -DskipITs=true \
    -Dtest=QueryCacheKeyCrossApplicationContextTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -DfailIfNoTests=false \
    test -l "$CROSS_LANE/maven.log"); then
  fail "Step 3 cross-ApplicationContext Maven lane failed"
fi
collect_report "$CROSS_LANE" "$CACHE_REPORT_DIR" "$CROSS_LANE/.run-start" \
  "$CROSS_FQCN" 1
assert_lane_log "$CROSS_LANE" 1
[[ "$(find "$CROSS_LANE/surefire-reports" -maxdepth 1 -type f -name 'TEST-*.xml' | wc -l)" -eq 1 ]] || \
  fail "Step 3 context report count drifted"

(cd "$ROOT_DIR" && sha256sum -c "$RUN_ROOT/source-audit/source-files.sha256") \
  > "$RUN_ROOT/source-audit/source-hash-check.txt" || \
  fail "evidence source changed during cache identity replay"

printf '%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$RUN_ROOT/finished-at.txt"
cat > "$RUN_ROOT/summary.env" <<SUMMARY
run_id=$RUN_ID
status=passed
criterion=CACHE-IDENTITY
model_pin_tests=6
step2_addon_tests=68
step2_total_tests=74
step3_context_tests=1
total_tests=75
owning_reports=6
failures=0
errors=0
skipped=0
strong_identity=passed
cross_application_context=passed
independent_review=pending-authoritative-run-review
SUMMARY

(cd "$RUN_ROOT" && \
  find . -type f ! -name SHA256SUMS ! -name SHA256SUMS.sha256 \
    ! -name manifest-check.txt ! -name outer-manifest-check.txt \
    -print0 | LC_ALL=C sort -z | xargs -0 sha256sum > SHA256SUMS)
(cd "$RUN_ROOT" && sha256sum -c SHA256SUMS > manifest-check.txt)
(cd "$RUN_ROOT" && sha256sum SHA256SUMS > SHA256SUMS.sha256)
(cd "$RUN_ROOT" && sha256sum -c SHA256SUMS.sha256 > outer-manifest-check.txt)

printf '%s\n' "$RUN_ID" > "$LATEST_TMP"
trap '' HUP INT TERM
mv -f "$LATEST_TMP" "$LATEST_RUN_ID"
SUCCESS_FINALIZED=1
