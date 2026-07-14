#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$ROOT_DIR/target/v933-batch6-cache-cross-jvm/runs/$RUN_ID"
REPORT_ASSERTION="$ROOT_DIR/scripts/assert-v933-test-report.sh"
REDIS_IMAGE="${V933_REDIS_IMAGE:-redis:7-alpine}"
CONTAINER_NAME="v933-cache-cross-jvm-$RUN_ID"
CONTAINER_ID=""

fail() {
  echo "[v933-cache-cross-jvm] ERROR: $*" >&2
  exit 1
}

assert_cache_key() {
  local key="$1"
  local layer_prefix="$2"
  local model_segment="$3"
  local expected_prefix="${layer_prefix}${model_segment}:"
  local digest
  [[ "$key" == "$expected_prefix"* ]] || \
    fail "cache key has unexpected layer/model prefix: $key"
  digest="${key#"$expected_prefix"}"
  [[ "$digest" =~ ^[0-9a-f]{64}$ ]] || \
    fail "cache key does not end in one full SHA-256 digest: $key"
  [[ "$key" != *"@"* ]] || fail "cache key exposes an object-address form: $key"
}

xml_count() {
  local testsuite_tag="$1"
  local attribute="$2"
  local pair
  pair="$(printf '%s\n' "$testsuite_tag" | rg -o "$attribute=\"[0-9]+\"" || true)"
  [[ -n "$pair" ]] || fail "owning report is missing $attribute count"
  pair="${pair#*=\"}"
  printf '%s' "${pair%\"}"
}

on_exit() {
  local status="$?"
  if [[ -n "$CONTAINER_ID" ]]; then
    docker rm -f "$CONTAINER_ID" >/dev/null 2>&1 || true
  else
    docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  fi
  if [[ "$status" -eq 0 ]]; then
    echo "[v933-cache-cross-jvm] PASS run=$RUN_ID evidence=$RUN_ROOT"
  else
    echo "[v933-cache-cross-jvm] FAILED run=$RUN_ID evidence=$RUN_ROOT" >&2
  fi
}
trap on_exit EXIT

[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
[[ ! -e "$RUN_ROOT" ]] || fail "run directory already exists: $RUN_ROOT"
[[ -x "$REPORT_ASSERTION" ]] || fail "report assertion is not executable"
for command_name in cmp diff docker git mvn rg sha256sum; do
  command -v "$command_name" >/dev/null 2>&1 || \
    fail "required command is missing: $command_name"
done
docker info >/dev/null 2>&1 || fail "Docker daemon is unavailable"
docker image inspect "$REDIS_IMAGE" > /dev/null 2>&1 || \
  fail "required local Redis image is unavailable: $REDIS_IMAGE"
mkdir -p "$RUN_ROOT/source-audit" "$RUN_ROOT/surefire-reports"

TEST_SOURCE="$ROOT_DIR/addons/foggy-dataset-model-cache/src/test/java/com/foggyframework/dataset/db/model/cache/provider/RedisCrossJvmCacheIT.java"
KEY_BUILDER="$ROOT_DIR/addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/provider/QueryCacheKeyBuilder.java"
REDIS_PROVIDER="$ROOT_DIR/addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/provider/RedisQueryCacheProvider.java"
AUTO_CONFIGURATION="$ROOT_DIR/addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/config/QueryCacheAutoConfiguration.java"
CATALOG_STORE="$ROOT_DIR/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/catalog/CatalogSnapshotStore.java"
CATALOG_SNAPSHOT="$ROOT_DIR/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/catalog/CatalogSnapshot.java"
CATALOG_CANDIDATE="$ROOT_DIR/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/catalog/CatalogCandidate.java"
MODEL_PROVENANCE="$ROOT_DIR/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/catalog/ModelProvenance.java"
MODEL_RESULT_CONTEXT="$ROOT_DIR/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/ModelResultContext.java"
OWNING_REPORT="$ROOT_DIR/addons/foggy-dataset-model-cache/target/surefire-reports/TEST-com.foggyframework.dataset.db.model.cache.provider.RedisCrossJvmCacheIT.xml"
RUN_MARKER="$RUN_ROOT/.run-start"
MAVEN_LOG="$RUN_ROOT/maven.log"
KEY_PREFIX="v933:$RUN_ID:"

SOURCE_FILES=(
  "scripts/verify-v933-batch6-cache-cross-jvm.sh"
  "addons/foggy-dataset-model-cache/pom.xml"
  "addons/foggy-dataset-model-cache/src/test/java/com/foggyframework/dataset/db/model/cache/provider/RedisCrossJvmCacheIT.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/config/QueryCacheAutoConfiguration.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/provider/QueryCacheKeyBuilder.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/provider/RedisQueryCacheProvider.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/catalog/CatalogSnapshotStore.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/catalog/CatalogSnapshot.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/catalog/CatalogCandidate.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/lifecycle/catalog/ModelProvenance.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/ModelResultContext.java"
)
for source_file in "${SOURCE_FILES[@]}"; do
  [[ -f "$ROOT_DIR/$source_file" ]] || fail "evidence source is missing: $source_file"
done
(cd "$ROOT_DIR" && sha256sum "${SOURCE_FILES[@]}") \
  > "$RUN_ROOT/source-audit/source-files.sha256"
git -C "$ROOT_DIR" rev-parse HEAD > "$RUN_ROOT/source-audit/git-head.txt"
git -C "$ROOT_DIR" status --short --untracked-files=all \
  > "$RUN_ROOT/source-audit/git-status.txt"

if rg -n 'InstanceIdentityRegistry|System\.identityHashCode|UUID\.randomUUID|resolveDataSourceIdentity' \
    "$KEY_BUILDER" > "$RUN_ROOT/source-audit/forbidden-key-fallbacks.txt"; then
  fail "process-local/object-address cache-key fallback remains"
fi
if rg -n '@Disabled|Assumptions|Thread\.sleep|TimeUnit\.sleep' \
    "$TEST_SOURCE" > "$RUN_ROOT/source-audit/skip-or-sleep.txt"; then
  fail "cross-JVM proof contains skip or sleep shortcuts"
fi
if rg -n 'new GenericJackson2JsonRedisSerializer|new RedisQueryCacheProvider' \
    "$TEST_SOURCE" > "$RUN_ROOT/source-audit/test-only-production-duplicates.txt"; then
  fail "cross-JVM proof duplicates production Redis provider/serializer setup"
fi

for contract in \
    'new ProcessBuilder\(' \
    'ProcessHandle\.current\(\)\.pid\(\)' \
    'new CatalogSnapshotStore\(\)' \
    'resetForNamespaceRefresh\(List\.of\(CANONICAL_MODEL\)\)' \
    'candidate\.putQueryModel\(' \
    'snapshot\.queryModelProvenance\(canonical\)' \
    'withProductionRedis\(' \
    'previous_l1_hit' \
    'previous_l2_hit' \
    'current_l1_miss' \
    'current_l2_miss' \
    'GenericJackson2JsonRedisSerializer' \
    'RedisQueryCacheProvider'; do
  file_name="$(printf '%s' "$contract" | sha256sum | cut -c1-12)"
  rg -n "$contract" "$TEST_SOURCE" \
    > "$RUN_ROOT/source-audit/contract-$file_name.txt" || \
    fail "cross-JVM source contract is missing: $contract"
done

for contract in \
    'foggyQueryCacheRedisTemplate' \
    'GenericJackson2JsonRedisSerializer' \
    '@Qualifier\(CACHE_REDIS_TEMPLATE_BEAN\)' \
    '@ConditionalOnBean\(RedisConnectionFactory\.class\)'; do
  file_name="$(printf '%s' "$contract" | sha256sum | cut -c1-12)"
  rg -n "$contract" "$AUTO_CONFIGURATION" \
    > "$RUN_ROOT/source-audit/auto-config-contract-$file_name.txt" || \
    fail "production Redis auto-configuration contract is missing: $contract"
done

TEST_COUNT="$(rg -c '^[[:space:]]*@Test\b' "$TEST_SOURCE" || true)"
[[ "${TEST_COUNT:-0}" -eq 1 ]] || fail "cross-JVM test count drift: ${TEST_COUNT:-0}"
printf '%s\n' \
  'child JVMs required: 2' \
  'production auto-configured Redis provider/template: required' \
  'snapshot-derived exact current resolution: required' \
  'previous physical keys readable control: required' \
  'restart-current boot-scoped catalog/source identity cold miss: required' \
  'expected owning tests: 1' \
  > "$RUN_ROOT/source-audit/summary.txt"

docker image inspect --format \
  'reference='"$REDIS_IMAGE"' image_id={{.Id}} repo_digests={{json .RepoDigests}}' \
  "$REDIS_IMAGE" > "$RUN_ROOT/redis-image.txt"
CONTAINER_ID="$(docker run -d \
  --name "$CONTAINER_NAME" \
  -p 127.0.0.1::6379 \
  "$REDIS_IMAGE" \
  redis-server --save '' --appendonly no)"
printf 'container_id=%s\ncontainer_name=%s\n' \
  "$CONTAINER_ID" "$CONTAINER_NAME" > "$RUN_ROOT/redis-container.txt"

REDIS_ENDPOINT="$(docker port "$CONTAINER_ID" 6379/tcp | head -n 1)"
[[ "$REDIS_ENDPOINT" == 127.0.0.1:* ]] || \
  fail "unexpected Redis endpoint: $REDIS_ENDPOINT"
REDIS_PORT="${REDIS_ENDPOINT##*:}"
[[ "$REDIS_PORT" =~ ^[0-9]+$ ]] || fail "invalid Redis port: $REDIS_PORT"
printf 'host=127.0.0.1\nport=%s\n' "$REDIS_PORT" > "$RUN_ROOT/redis-endpoint.txt"

READY=0
for _ in $(seq 1 50); do
  if [[ "$(docker exec "$CONTAINER_ID" redis-cli ping 2>/dev/null | tr -d '\r')" == "PONG" ]]; then
    READY=1
    break
  fi
  sleep 0.2
done
[[ "$READY" -eq 1 ]] || fail "Redis did not become ready"
docker exec "$CONTAINER_ID" redis-cli INFO server \
  | tr -d '\r' \
  | rg '^(redis_version|redis_git_sha1|redis_mode|os|arch_bits)[:=]' \
  > "$RUN_ROOT/redis-server.txt"

rm -f "$OWNING_REPORT"
: > "$RUN_MARKER"
echo "[v933-cache-cross-jvm] running two-process Redis restart proof"
if ! (cd "$ROOT_DIR" && mvn -B \
    -pl addons/foggy-dataset-model-cache -am \
    -P'!multi-db' \
    -DskipITs=true \
    -Dtest=RedisCrossJvmCacheIT \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -DfailIfNoTests=false \
    -Dv933.redis.host=127.0.0.1 \
    -Dv933.redis.port="$REDIS_PORT" \
    -Dv933.redis.key-prefix="$KEY_PREFIX" \
    test -l "$MAVEN_LOG"); then
  fail "cross-JVM Maven lane failed; log=$MAVEN_LOG"
fi

[[ -f "$OWNING_REPORT" ]] || fail "cross-JVM owning report is missing"
[[ "$OWNING_REPORT" -nt "$RUN_MARKER" ]] || fail "cross-JVM owning report is stale"
cp "$OWNING_REPORT" "$RUN_ROOT/surefire-reports/"
V933_RUN_MARKER="$RUN_MARKER" \
  "$REPORT_ASSERTION" \
  "$RUN_ROOT/surefire-reports" \
  'com.foggyframework.dataset.db.model.cache.provider.RedisCrossJvmCacheIT' \
  1

PROBE_RAW="$RUN_ROOT/child-probes.raw"
rg -o 'V933_PROBE [a-z0-9_]+=[^<[:space:]]+' "$OWNING_REPORT" \
  | sed 's/^V933_PROBE //' > "$PROBE_RAW" || \
  fail "owning report does not retain child-process evidence"
[[ "$(wc -l < "$PROBE_RAW")" -eq 27 ]] || \
  fail "probe record count drifted from 27"

declare -A WRITE_PROBE=()
declare -A RESTART_PROBE=()
CURRENT_MODE=""
while IFS='=' read -r probe_key probe_value; do
  [[ "$probe_key" =~ ^[a-z0-9_]+$ && -n "$probe_value" ]] || \
    fail "malformed child probe record: $probe_key"
  if [[ "$probe_key" == "mode" ]]; then
    [[ "$probe_value" == "write" || "$probe_value" == "restart" ]] || \
      fail "unknown child probe mode: $probe_value"
    CURRENT_MODE="$probe_value"
  fi
  [[ -n "$CURRENT_MODE" ]] || fail "probe record precedes its mode: $probe_key"
  if [[ "$CURRENT_MODE" == "write" ]]; then
    [[ ! ${WRITE_PROBE[$probe_key]+present} ]] || \
      fail "duplicate writer probe key: $probe_key"
    WRITE_PROBE["$probe_key"]="$probe_value"
  else
    [[ ! ${RESTART_PROBE[$probe_key]+present} ]] || \
      fail "duplicate restart probe key: $probe_key"
    RESTART_PROBE["$probe_key"]="$probe_value"
  fi
done < "$PROBE_RAW"

WRITE_KEYS=(
  mode pid catalog_generation source_revision
  binding_key binding_backend binding_generation
  l1_key l2_key l1_hit_after_write l2_hit_after_write
)
RESTART_KEYS=(
  mode pid catalog_generation source_revision
  binding_key binding_backend binding_generation
  l1_key l2_key previous_l1_hit previous_l2_hit
  current_l1_miss current_l2_miss
  current_l1_hit_after_write current_l2_hit_after_write key_count
)
[[ "${#WRITE_PROBE[@]}" -eq "${#WRITE_KEYS[@]}" ]] || \
  fail "writer probe key count drifted"
[[ "${#RESTART_PROBE[@]}" -eq "${#RESTART_KEYS[@]}" ]] || \
  fail "restart probe key count drifted"
for probe_key in "${WRITE_KEYS[@]}"; do
  [[ ${WRITE_PROBE[$probe_key]+present} ]] || \
    fail "writer probe is missing: $probe_key"
done
for probe_key in "${RESTART_KEYS[@]}"; do
  [[ ${RESTART_PROBE[$probe_key]+present} ]] || \
    fail "restart probe is missing: $probe_key"
done
[[ "${WRITE_PROBE[mode]}" == "write" ]] || fail "writer mode mismatch"
[[ "${RESTART_PROBE[mode]}" == "restart" ]] || fail "restart mode mismatch"
[[ "${WRITE_PROBE[pid]}" =~ ^[0-9]+$ ]] || fail "writer PID is invalid"
[[ "${RESTART_PROBE[pid]}" =~ ^[0-9]+$ ]] || fail "restart PID is invalid"
[[ "${WRITE_PROBE[pid]}" != "${RESTART_PROBE[pid]}" ]] || \
  fail "writer and restart probes reused one PID"

for probe_key in l1_hit_after_write l2_hit_after_write; do
  [[ "${WRITE_PROBE[$probe_key]}" == "true" ]] || \
    fail "writer boolean probe failed: $probe_key"
done
for probe_key in previous_l1_hit previous_l2_hit current_l1_miss \
    current_l2_miss current_l1_hit_after_write current_l2_hit_after_write; do
  [[ "${RESTART_PROBE[$probe_key]}" == "true" ]] || \
    fail "restart boolean probe failed: $probe_key"
done
[[ "${WRITE_PROBE[catalog_generation]}" != \
   "${RESTART_PROBE[catalog_generation]}" ]] || \
  fail "restart reused writer catalog generation"
[[ "${WRITE_PROBE[source_revision]}" != \
   "${RESTART_PROBE[source_revision]}" ]] || \
  fail "restart reused writer source revision"
for probe_key in binding_key binding_backend binding_generation; do
  [[ "${WRITE_PROBE[$probe_key]}" == "${RESTART_PROBE[$probe_key]}" ]] || \
    fail "binding identity changed across restart: $probe_key"
done
[[ "${WRITE_PROBE[binding_key]}" == "primary" ]] || fail "binding key drifted"
[[ "${WRITE_PROBE[binding_backend]}" == "runtime-registry" ]] || \
  fail "binding backend drifted"
[[ "${WRITE_PROBE[binding_generation]}" == "binding:persisted:1" ]] || \
  fail "binding generation drifted"
[[ "${RESTART_PROBE[key_count]}" == "4" ]] || \
  fail "restart probe did not observe four keys"

assert_cache_key "${WRITE_PROBE[l1_key]}" "${KEY_PREFIX}l1:" "O"
assert_cache_key "${WRITE_PROBE[l2_key]}" "${KEY_PREFIX}l2:" "OrderModel"
assert_cache_key "${RESTART_PROBE[l1_key]}" "${KEY_PREFIX}l1:" "O"
assert_cache_key "${RESTART_PROBE[l2_key]}" "${KEY_PREFIX}l2:" "OrderModel"
printf '%s\n' \
  "${WRITE_PROBE[l1_key]}" \
  "${WRITE_PROBE[l2_key]}" \
  "${RESTART_PROBE[l1_key]}" \
  "${RESTART_PROBE[l2_key]}" \
  | sort -u > "$RUN_ROOT/expected-redis-keys.txt"
[[ "$(wc -l < "$RUN_ROOT/expected-redis-keys.txt")" -eq 4 ]] || \
  fail "emitted cache keys are not four distinct values"

: > "$RUN_ROOT/child-probes.txt"
for probe_key in "${WRITE_KEYS[@]}"; do
  printf 'write.%s=%s\n' "$probe_key" "${WRITE_PROBE[$probe_key]}" \
    >> "$RUN_ROOT/child-probes.txt"
done
for probe_key in "${RESTART_KEYS[@]}"; do
  printf 'restart.%s=%s\n' "$probe_key" "${RESTART_PROBE[$probe_key]}" \
    >> "$RUN_ROOT/child-probes.txt"
done

TESTSUITE_TAG="$(rg -o '<testsuite[^>]*>' "$OWNING_REPORT" | head -n 1)"
[[ -n "$TESTSUITE_TAG" ]] || fail "owning testsuite element is missing"
REPORT_TESTS="$(xml_count "$TESTSUITE_TAG" tests)"
REPORT_FAILURES="$(xml_count "$TESTSUITE_TAG" failures)"
REPORT_ERRORS="$(xml_count "$TESTSUITE_TAG" errors)"
REPORT_SKIPPED="$(xml_count "$TESTSUITE_TAG" skipped)"
REPORT_COUNT="$(find "$RUN_ROOT/surefire-reports" -maxdepth 1 \
  -type f -name 'TEST-*.xml' | wc -l)"
[[ "$REPORT_TESTS" -eq 1 && "$REPORT_FAILURES" -eq 0 \
   && "$REPORT_ERRORS" -eq 0 && "$REPORT_SKIPPED" -eq 0 \
   && "$REPORT_COUNT" -eq 1 ]] || fail "owning report counts are not exact"

docker exec "$CONTAINER_ID" redis-cli DBSIZE \
  | tr -d '\r' > "$RUN_ROOT/redis-dbsize.txt"
[[ "$(<"$RUN_ROOT/redis-dbsize.txt")" -eq 4 ]] || \
  fail "shared Redis key count is not 4"
docker exec "$CONTAINER_ID" redis-cli --scan --pattern "$KEY_PREFIX*" \
  | tr -d '\r' | sort > "$RUN_ROOT/redis-keys.txt"
[[ "$(wc -l < "$RUN_ROOT/redis-keys.txt")" -eq 4 ]] || \
  fail "shared Redis scan did not return four generation-separated keys"
if ! cmp -s "$RUN_ROOT/expected-redis-keys.txt" "$RUN_ROOT/redis-keys.txt"; then
  diff -u "$RUN_ROOT/expected-redis-keys.txt" "$RUN_ROOT/redis-keys.txt" >&2 || true
  fail "Redis scan does not exactly equal the four emitted keys"
fi

cat > "$RUN_ROOT/summary.env" <<SUMMARY
run_id=$RUN_ID
status=passed
redis_image=$REDIS_IMAGE
redis_host=127.0.0.1
redis_port=$REDIS_PORT
child_jvms=2
writer_pid=${WRITE_PROBE[pid]}
restart_pid=${RESTART_PROBE[pid]}
tests=$REPORT_TESTS
owning_reports=$REPORT_COUNT
failures=$REPORT_FAILURES
errors=$REPORT_ERRORS
skipped=$REPORT_SKIPPED
previous_identity_control_hits=2
restart_current_identity_misses=2
redis_keys=${RESTART_PROBE[key_count]}
binding_key=${WRITE_PROBE[binding_key]}
binding_backend=${WRITE_PROBE[binding_backend]}
binding_generation=${WRITE_PROBE[binding_generation]}
identity_rotation=boot_scoped_catalog_and_source
SUMMARY

(cd "$RUN_ROOT" && find . -type f ! -name SHA256SUMS -print0 \
  | sort -z | xargs -0 sha256sum > SHA256SUMS)
(cd "$RUN_ROOT" && sha256sum -c SHA256SUMS >/dev/null)
printf '%s\n' "$RUN_ID" \
  > "$ROOT_DIR/target/v933-batch6-cache-cross-jvm/latest-run-id"
echo "[v933-cache-cross-jvm] COMPLETE run=$RUN_ID tests=1 reports=1 child_jvms=2 redis_keys=4"
