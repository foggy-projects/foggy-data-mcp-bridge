#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$ROOT_DIR/target/v933-batch6-real-query/runs/$RUN_ID"
REPORT_ASSERTION="$ROOT_DIR/scripts/assert-v933-test-report.sh"
REDIS_CONTAINER="v933-real-query-redis-$RUN_ID"
REDIS_IMAGE="redis:7-alpine"
REDIS_PREFIX="v933:real-query:$RUN_ID:"
REDIS_STARTED=false

MODEL_LIFECYCLE_FQCN="com.foggyframework.dataset.db.model.lifecycle.realquery.ModelLifecycleRealQueryIT"
DB_PARITY_FQCN="com.foggyframework.dataset.db.model.lifecycle.realquery.RequiredDatabaseQueryFacadeParityIT"
CACHE_FQCN="com.foggyframework.dataset.db.model.cache.lifecycle.realquery.QueryCacheLifecycleRealQueryIT"

fail() {
  echo "[v933-real-query] ERROR: $*" >&2
  exit 1
}

redis_key_count() {
  docker exec "$REDIS_CONTAINER" redis-cli --scan --pattern "${REDIS_PREFIX}*" \
    | wc -l | tr -d '[:space:]'
}

stop_owned_redis() {
  if [[ "$REDIS_STARTED" == true ]]; then
    docker rm -f "$REDIS_CONTAINER" >/dev/null 2>&1 || return 1
    if docker inspect "$REDIS_CONTAINER" >/dev/null 2>&1; then
      return 1
    fi
    REDIS_STARTED=false
  fi
}

on_exit() {
  local status="$?"
  stop_owned_redis || true
  if [[ "$status" -eq 0 ]]; then
    echo "[v933-real-query] PASS run=$RUN_ID evidence=$RUN_ROOT"
  else
    echo "[v933-real-query] FAILED run=$RUN_ID evidence=$RUN_ROOT" >&2
  fi
}
trap on_exit EXIT

assert_exact_test_count() {
  local source_file="$1"
  local expected="$2"
  local actual
  actual="$(rg -c '^[[:space:]]*@Test[[:space:]]*$' "$source_file" || true)"
  [[ "${actual:-0}" -eq "$expected" ]] || \
    fail "@Test count drift in ${source_file#$ROOT_DIR/}: ${actual:-0}, expected $expected"
}

require_source_pattern() {
  local source_file="$1"
  local pattern="$2"
  local audit_name="$3"
  rg -n "$pattern" "$source_file" \
    > "$RUN_ROOT/source-audit/required-$audit_name.txt" || \
    fail "required source contract is missing: $audit_name"
}

forbid_source_pattern() {
  local pattern="$1"
  local audit_name="$2"
  shift 2
  set +e
  rg -n "$pattern" "$@" > "$RUN_ROOT/source-audit/forbidden-$audit_name.txt"
  local status="$?"
  set -e
  case "$status" in
    0) fail "forbidden source construct found: $audit_name" ;;
    1) ;;
    *) fail "source audit failed: $audit_name" ;;
  esac
}

assert_fixed_container() {
  local name="$1"
  local expected_image="$2"
  local container_port="$3"
  local expected_host_port="$4"
  local phase="$5"
  local actual_image health port image_id

  docker inspect "$name" >/dev/null 2>&1 || fail "required container is missing: $name"
  actual_image="$(docker inspect -f '{{.Config.Image}}' "$name")"
  health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$name")"
  port="$(docker port "$name" "$container_port/tcp" | tail -n 1)"
  image_id="$(docker inspect -f '{{.Image}}' "$name")"
  [[ "$actual_image" == "$expected_image" ]] || \
    fail "$name image=$actual_image, expected $expected_image"
  [[ "$health" == "healthy" ]] || fail "$name health=$health"
  [[ "$port" == *":$expected_host_port" ]] || \
    fail "$name mapped port=$port, expected host port $expected_host_port"
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$phase" "$name" "$actual_image" "$image_id" "$health" "$port" \
    >> "$RUN_ROOT/environment/fixed-containers.tsv"
}

assert_lane() {
  local lane_dir="$1"
  local marker="$2"
  local fqcn="$3"
  local expected_tests="$4"
  local log_file="$lane_dir/maven.log"
  local reports="$lane_dir/failsafe-reports"
  local failsafe_summary="$reports/failsafe-summary.xml"
  local success_count running_count

  V933_RUN_MARKER="$marker" "$REPORT_ASSERTION" \
    "$reports" "$fqcn" "$expected_tests" \
    > "$lane_dir/report-assertion.txt"
  [[ -f "$failsafe_summary" && "$failsafe_summary" -nt "$marker" ]] || \
    fail "lane $(basename "$lane_dir") Failsafe summary is missing or stale"
  grep -F "<completed>$expected_tests</completed>" "$failsafe_summary" >/dev/null || \
    fail "lane $(basename "$lane_dir") Failsafe completed count drifted"
  for zero_field in errors failures skipped; do
    grep -F "<$zero_field>0</$zero_field>" "$failsafe_summary" >/dev/null || \
      fail "lane $(basename "$lane_dir") Failsafe $zero_field is nonzero"
  done
  success_count="$(grep -c '^\[INFO\] BUILD SUCCESS' "$log_file" || true)"
  [[ "$success_count" -eq 1 ]] || \
    fail "lane $(basename "$lane_dir") BUILD SUCCESS count=$success_count"
  if rg -n '^\[INFO\] BUILD FAILURE' "$log_file" > "$lane_dir/build-failure-audit.txt"; then
    fail "lane $(basename "$lane_dir") contains BUILD FAILURE"
  fi
  running_count="$(grep -c '^\[INFO\] Running ' "$log_file" || true)"
  [[ "$running_count" -eq 1 ]] || \
    fail "lane $(basename "$lane_dir") executed $running_count test classes"
  grep -F "[INFO] Running $fqcn" "$log_file" \
    > "$lane_dir/running-class.txt" || \
    fail "lane $(basename "$lane_dir") did not run $fqcn"
}

run_lane() {
  local lane="$1"
  local fqcn="$2"
  local expected_tests="$3"
  shift 3
  local lane_dir="$RUN_ROOT/lanes/$lane"
  local marker="$lane_dir/.run-start"
  local log_file="$lane_dir/maven.log"

  mkdir -p "$lane_dir"
  : > "$marker"
  echo "[v933-real-query] running lane=$lane class=$fqcn tests=$expected_tests"
  if ! (cd "$ROOT_DIR" && mvn -B "$@" \
      -DskipUnitTests=true \
      -DskipITs=false \
      -Dit.test="$fqcn" \
      -Dv933.reportsDirectory="$lane_dir" \
      verify -l "$log_file"); then
    fail "Maven lane failed: $lane; log=$log_file"
  fi
  assert_lane "$lane_dir" "$marker" "$fqcn" "$expected_tests"
}

capture_database_probe() {
  local lane="$1"
  local expected_kind="$2"
  local expected_product="$3"
  local expected_version_pattern="$4"
  local expected_catalog="$5"
  local expected_schema="$6"
  local log_file="$RUN_ROOT/lanes/$lane/maven.log"
  local probe

  probe="$(grep '^V933_REAL_QUERY_DB ' "$log_file" || true)"
  [[ "$(grep -c '^V933_REAL_QUERY_DB ' "$log_file" || true)" -eq 1 ]] || \
    fail "lane $lane must emit exactly one machine database probe"
  [[ "$probe" == *"kind=$expected_kind "* ]] || fail "lane $lane kind probe mismatch"
  [[ "$probe" == *"product=$expected_product "* ]] || fail "lane $lane product probe mismatch"
  [[ "$probe" =~ version=$expected_version_pattern([[:space:]]|$) ]] || \
    fail "lane $lane version probe mismatch: $probe"
  [[ "$probe" == *"catalog=$expected_catalog "* ]] || fail "lane $lane catalog probe mismatch"
  [[ "$probe" == *"schema=$expected_schema "* ]] || fail "lane $lane schema probe mismatch"
  printf '%s\t%s\n' "$lane" "$probe" >> "$RUN_ROOT/environment/database-probes.tsv"
}

[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
[[ ! -e "$RUN_ROOT" ]] || fail "run directory already exists: $RUN_ROOT"
[[ -x "$REPORT_ASSERTION" ]] || fail "report assertion is not executable"
for command_name in cat date docker find git grep mkdir mvn pgrep readlink rg sed seq sha256sum sleep sort tail tr wc xargs; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command is missing: $command_name"
done

EXTERNAL_MAVEN_FLAGS="${MAVEN_ARGS:-} ${MAVEN_OPTS:-} ${JAVA_TOOL_OPTIONS:-}"
if [[ "$EXTERNAL_MAVEN_FLAGS" =~ (^|[[:space:]])-D(skipTests|maven\.test\.skip|skipITs|skipUnitTests)($|=|[[:space:]]) ]]; then
  fail "external Maven test-skip properties are forbidden"
fi
if [[ "$EXTERNAL_MAVEN_FLAGS" =~ (^|[[:space:]])-P[^[:space:]]*multi-db ]]; then
  fail "external multi-db profile activation is forbidden"
fi

while IFS= read -r process_id; do
  [[ -n "$process_id" ]] || continue
  process_cwd="$(readlink -f "/proc/$process_id/cwd" 2>/dev/null || true)"
  if [[ "$process_cwd" == "$ROOT_DIR" || "$process_cwd" == "$ROOT_DIR/"* ]]; then
    fail "another Maven/Surefire process is running in this workspace: pid=$process_id"
  fi
done < <(pgrep -f '[o]rg.codehaus.plexus.classworlds.launcher.Launcher|[s]urefirebooter' || true)

mkdir -p "$RUN_ROOT/lanes" "$RUN_ROOT/environment" "$RUN_ROOT/source-audit"
date -u +%Y-%m-%dT%H:%M:%SZ > "$RUN_ROOT/started-at.txt"
printf 'phase\tcontainer\timage\timage_id\thealth\tmapped_port\n' \
  > "$RUN_ROOT/environment/fixed-containers.tsv"
printf 'lane\tprobe\n' > "$RUN_ROOT/environment/database-probes.tsv"
git -C "$ROOT_DIR" rev-parse HEAD > "$RUN_ROOT/source-audit/git-head.txt"
git -C "$ROOT_DIR" status --short > "$RUN_ROOT/source-audit/git-status.txt"

MODEL_LIFECYCLE_SOURCE="foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/lifecycle/realquery/ModelLifecycleRealQueryIT.java"
DB_PARITY_SOURCE="foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/lifecycle/realquery/RequiredDatabaseQueryFacadeParityIT.java"
CACHE_SOURCE="addons/foggy-dataset-model-cache/src/test/java/com/foggyframework/dataset/db/model/cache/lifecycle/realquery/QueryCacheLifecycleRealQueryIT.java"
CACHE_POM="addons/foggy-dataset-model-cache/pom.xml"
SOURCE_FILES=(
  "$MODEL_LIFECYCLE_SOURCE"
  "$DB_PARITY_SOURCE"
  "$CACHE_SOURCE"
  "$CACHE_POM"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/service/impl/QueryFacadeImpl.java"
  "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/pivot/PivotPipeline.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/config/QueryCacheAutoConfiguration.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/provider/CaffeineQueryCacheProvider.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/provider/RedisQueryCacheProvider.java"
  "addons/foggy-dataset-model-cache/src/main/java/com/foggyframework/dataset/db/model/cache/provider/QueryCacheKeyBuilder.java"
  "pom.xml"
  "foggy-core/pom.xml"
  "foggy-bean-copy/pom.xml"
  "foggy-fsscript/pom.xml"
  "foggy-dataset/pom.xml"
  "foggy-dataset-demo/pom.xml"
  "foggy-dataset-model/pom.xml"
  "scripts/assert-v933-test-report.sh"
  "scripts/verify-v933-batch6-real-query.sh"
)
while IFS= read -r -d '' resource_file; do
  SOURCE_FILES+=("${resource_file#$ROOT_DIR/}")
done < <(find \
  "$ROOT_DIR/foggy-dataset-model/src/test/resources" \
  "$ROOT_DIR/foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce" \
  "$ROOT_DIR/foggy-dataset-demo/docker/mysql/init" \
  "$ROOT_DIR/foggy-dataset-demo/docker/postgres/init" \
  -type f -print0 | sort -z)
SOURCE_FILES+=("foggy-dataset-demo/docker/docker-compose.yml")
for source_file in "${SOURCE_FILES[@]}"; do
  [[ -f "$ROOT_DIR/$source_file" ]] || fail "source file is missing: $source_file"
done
(cd "$ROOT_DIR" && sha256sum "${SOURCE_FILES[@]}") \
  > "$RUN_ROOT/source-audit/source-files.sha256"

assert_exact_test_count "$ROOT_DIR/$MODEL_LIFECYCLE_SOURCE" 4
assert_exact_test_count "$ROOT_DIR/$DB_PARITY_SOURCE" 1
assert_exact_test_count "$ROOT_DIR/$CACHE_SOURCE" 2
TEST_SOURCES=(
  "$ROOT_DIR/$MODEL_LIFECYCLE_SOURCE"
  "$ROOT_DIR/$DB_PARITY_SOURCE"
  "$ROOT_DIR/$CACHE_SOURCE"
)
forbid_source_pattern '@Disabled|Assumptions\.|Thread\.sleep|TimeUnit\.[A-Za-z]+\.sleep|Mockito|@Mock|mock\(|spy\(' \
  shortcuts "${TEST_SOURCES[@]}"
forbid_source_pattern 'Collections\.sort|\.sort\(' actual-sort "${TEST_SOURCES[@]}"
forbid_source_pattern 'buildSqlOnly|return[[:space:]]*;' false-green-return "${TEST_SOURCES[@]}"
forbid_source_pattern 'new[[:space:]]+(CaffeineQueryCacheProvider|RedisQueryCacheProvider|RedisTemplate)' \
  hand-built-cache "$ROOT_DIR/$CACHE_SOURCE"
require_source_pattern "$ROOT_DIR/$MODEL_LIFECYCLE_SOURCE" \
  'queryFacade\.queryModelResult|semanticQueryService\.queryModel' model-real-entry
require_source_pattern "$ROOT_DIR/$MODEL_LIFECYCLE_SOURCE" \
  'nativeDetailRows|nativeAggregateRows|ORDER BY' model-native-parity
require_source_pattern "$ROOT_DIR/$DB_PARITY_SOURCE" \
  'v933\.expectedDatabase|V933_REAL_QUERY_DB' required-database-fail-closed
require_source_pattern "$ROOT_DIR/$DB_PARITY_SOURCE" \
  'assertEquals\(COLUMNS, new ArrayList<>\(facadeRow\.keySet\(\)\)' database-column-order
require_source_pattern "$ROOT_DIR/$CACHE_SOURCE" \
  'caffeineQueryCacheProvider|redisQueryCacheProvider|foggyQueryCacheRedisTemplate' cache-auto-configuration
require_source_pattern "$ROOT_DIR/$CACHE_SOURCE" \
  'StringRedisSerializer|GenericJackson2JsonRedisSerializer' redis-production-serializers
require_source_pattern "$ROOT_DIR/$CACHE_POM" '<id>query-cache-real-query</id>' cache-profile-isolation

assert_fixed_container foggy-demo-mysql mysql:5.7 3306 13306 before
assert_fixed_container foggy-demo-postgres postgres:15-alpine 5432 15432 before
MYSQL_IMAGE_ID_BEFORE="$(docker inspect -f '{{.Image}}' foggy-demo-mysql)"
POSTGRES_IMAGE_ID_BEFORE="$(docker inspect -f '{{.Image}}' foggy-demo-postgres)"

docker run -d --rm --name "$REDIS_CONTAINER" -p 127.0.0.1::6379 "$REDIS_IMAGE" \
  > "$RUN_ROOT/environment/redis-container-id.txt"
REDIS_STARTED=true
for attempt in $(seq 1 30); do
  if [[ "$(docker exec "$REDIS_CONTAINER" redis-cli ping 2>/dev/null || true)" == "PONG" ]]; then
    break
  fi
  [[ "$attempt" -lt 30 ]] || fail "run-scoped Redis did not become ready"
  sleep 1
done
REDIS_PORT_MAPPING="$(docker port "$REDIS_CONTAINER" 6379/tcp | tail -n 1)"
REDIS_PORT="${REDIS_PORT_MAPPING##*:}"
[[ "$REDIS_PORT" =~ ^[1-9][0-9]*$ ]] || fail "invalid Redis host port: $REDIS_PORT_MAPPING"
REDIS_VERSION="$(docker exec "$REDIS_CONTAINER" redis-server --version \
  | sed -n 's/.* v=\([^ ]*\).*/\1/p')"
REDIS_IMAGE_ID="$(docker inspect -f '{{.Image}}' "$REDIS_CONTAINER")"
[[ -n "$REDIS_VERSION" ]] || fail "could not determine Redis version"
[[ "$(redis_key_count)" -eq 0 ]] || fail "run-scoped Redis prefix is not initially empty"
cat > "$RUN_ROOT/environment/redis.env" <<REDIS
container=$REDIS_CONTAINER
image=$REDIS_IMAGE
image_id=$REDIS_IMAGE_ID
version=$REDIS_VERSION
host=127.0.0.1
port=$REDIS_PORT
key_prefix=$REDIS_PREFIX
initial_key_count=0
REDIS

run_lane model-lifecycle-sqlite "$MODEL_LIFECYCLE_FQCN" 4 \
  -pl foggy-dataset-model -am -P'!multi-db,model-lifecycle' \
  -Dfailsafe.failIfNoSpecifiedTests=false -Dspring.profiles.active=sqlite
run_lane database-sqlite "$DB_PARITY_FQCN" 1 \
  -pl foggy-dataset-model -am -P'!multi-db,model-lifecycle' \
  -Dfailsafe.failIfNoSpecifiedTests=false -Dspring.profiles.active=sqlite \
  -Dv933.expectedDatabase=sqlite
capture_database_probe database-sqlite sqlite SQLite '3\.[0-9]+' '<none>' '<none>'
run_lane database-mysql57 "$DB_PARITY_FQCN" 1 \
  -pl foggy-dataset-model -am -P'!multi-db,model-lifecycle' \
  -Dfailsafe.failIfNoSpecifiedTests=false -Dspring.profiles.active=docker \
  -Dv933.expectedDatabase=mysql57
capture_database_probe database-mysql57 mysql57 MySQL '5\.7' foggy_test '<none>'
run_lane database-postgres15 "$DB_PARITY_FQCN" 1 \
  -pl foggy-dataset-model -am -P'!multi-db,model-lifecycle' \
  -Dfailsafe.failIfNoSpecifiedTests=false -Dspring.profiles.active=postgres \
  -Dv933.expectedDatabase=postgres15
capture_database_probe database-postgres15 postgres15 PostgreSQL '15\.[0-9]+' foggy_test public
run_lane cache-caffeine "$CACHE_FQCN" 2 \
  -pl addons/foggy-dataset-model-cache -am -P'!multi-db,query-cache-real-query' \
  -Dv933.cache.provider=caffeine
grep -F 'Initializing Caffeine query cache provider' \
  "$RUN_ROOT/lanes/cache-caffeine/maven.log" \
  > "$RUN_ROOT/lanes/cache-caffeine/provider-proof.txt" || \
  fail "Caffeine production auto-configuration proof is missing"
run_lane cache-redis "$CACHE_FQCN" 2 \
  -pl addons/foggy-dataset-model-cache -am -P'!multi-db,query-cache-real-query' \
  -Dv933.cache.provider=redis -Dv933.redis.host=127.0.0.1 \
  -Dv933.redis.port="$REDIS_PORT" -Dv933.redis.key-prefix="$REDIS_PREFIX"
grep -F 'Initializing Redis query cache provider' \
  "$RUN_ROOT/lanes/cache-redis/maven.log" \
  > "$RUN_ROOT/lanes/cache-redis/provider-proof.txt" || \
  fail "Redis production auto-configuration proof is missing"

FINAL_REDIS_KEYS="$(redis_key_count)"
[[ "$FINAL_REDIS_KEYS" -eq 0 ]] || \
  fail "Redis key prefix leaked $FINAL_REDIS_KEYS key(s) after cleanup"
printf 'final_key_count=%s\n' "$FINAL_REDIS_KEYS" \
  >> "$RUN_ROOT/environment/redis.env"
assert_fixed_container foggy-demo-mysql mysql:5.7 3306 13306 after
assert_fixed_container foggy-demo-postgres postgres:15-alpine 5432 15432 after
[[ "$(docker inspect -f '{{.Image}}' foggy-demo-mysql)" == "$MYSQL_IMAGE_ID_BEFORE" ]] || \
  fail "MySQL container image ID changed during the run"
[[ "$(docker inspect -f '{{.Image}}' foggy-demo-postgres)" == "$POSTGRES_IMAGE_ID_BEFORE" ]] || \
  fail "PostgreSQL container image ID changed during the run"
stop_owned_redis || fail "could not remove run-scoped Redis container"
printf 'cleanup=removed\n' >> "$RUN_ROOT/environment/redis.env"

REPORT_COUNT="$(find "$RUN_ROOT/lanes" -type f -path '*/failsafe-reports/TEST-*.xml' | wc -l | tr -d '[:space:]')"
[[ "$REPORT_COUNT" -eq 6 ]] || fail "owning report count=$REPORT_COUNT, expected 6"
(cd "$ROOT_DIR" && sha256sum -c "$RUN_ROOT/source-audit/source-files.sha256") \
  > "$RUN_ROOT/source-audit/source-hash-check.txt" || \
  fail "source files changed during the authoritative run"

SQLITE_PROBE="$(grep '^database-sqlite' "$RUN_ROOT/environment/database-probes.tsv")"
MYSQL_PROBE="$(grep '^database-mysql57' "$RUN_ROOT/environment/database-probes.tsv")"
POSTGRES_PROBE="$(grep '^database-postgres15' "$RUN_ROOT/environment/database-probes.tsv")"
date -u +%Y-%m-%dT%H:%M:%SZ > "$RUN_ROOT/finished-at.txt"
cat > "$RUN_ROOT/summary.env" <<SUMMARY
run_id=$RUN_ID
status=passed
criterion=REAL-QUERY
model_lifecycle_tests=4
required_database_tests=3
cache_provider_tests=4
total_tests=11
owning_reports=6
failures=0
errors=0
skipped=0
sqlite_probe=$SQLITE_PROBE
mysql57_probe=$MYSQL_PROBE
postgres15_probe=$POSTGRES_PROBE
redis_image=$REDIS_IMAGE
redis_image_id=$REDIS_IMAGE_ID
redis_version=$REDIS_VERSION
redis_initial_keys=0
redis_final_keys=$FINAL_REDIS_KEYS
redis_cleanup=removed
source_git_head=$(cat "$RUN_ROOT/source-audit/git-head.txt")
independent_review=pending-authoritative-run-review
SUMMARY

(cd "$RUN_ROOT" && find . -type f ! -name SHA256SUMS ! -name SHA256SUMS.sha256 \
  -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)
(cd "$RUN_ROOT" && sha256sum -c SHA256SUMS > manifest-check.txt)
# manifest-check.txt is deliberately outside the inner manifest; the outer
# digest pins the completed inner manifest without creating a self-reference.
(cd "$RUN_ROOT" && sha256sum SHA256SUMS > SHA256SUMS.sha256)
(cd "$RUN_ROOT" && sha256sum -c SHA256SUMS.sha256 > outer-manifest-check.txt)
printf '%s\n' "$RUN_ID" > "$ROOT_DIR/target/v933-batch6-real-query/latest-run-id"

echo "[v933-real-query] COMPLETE run=$RUN_ID tests=11 reports=6 failures=0 errors=0 skipped=0"
