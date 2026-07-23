#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMPOSE_DIR="$ROOT_DIR/foggy-dataset-demo/docker"
BASE_COMPOSE="$COMPOSE_DIR/docker-compose.yml"
DIGEST_COMPOSE="$COMPOSE_DIR/docker-compose-v934.yml"
AUTHORITY_COMPOSE="$COMPOSE_DIR/docker-compose-v934-authority.yml"
FIXTURE_ROOT="$COMPOSE_DIR/v934"

ACTION="${1:-}"
DATABASE="${2:-}"
RUN_ID="${3:-}"
CELL_ROOT="${4:-}"
shift $(( $# >= 4 ? 4 : $# ))

usage() {
  cat <<'USAGE'
Usage:
  provision-database-cell.sh check <database> <run-id> <cell-root>
  provision-database-cell.sh run   <database> <run-id> <cell-root> -- <callback> [args...]

database: mysql57 | mysql8 | postgres15 | sqlserver2022

The run action creates one fresh Compose project on the frozen host port,
applies the V934 fixture twice, snapshots it, invokes the callback, verifies
the after snapshot, and removes the container, volume, and network on every
exit path. It never stops an existing process that owns the frozen port.
USAGE
}

input_fail() {
  echo "[v934-db-provision] ERROR $1: ${2:-invalid input}" >&2
  exit 2
}

[[ "$ACTION" == check || "$ACTION" == run ]] || {
  usage >&2
  input_fail E_USAGE "unsupported action: ${ACTION:-<empty>}"
}
[[ "$DATABASE" =~ ^(mysql57|mysql8|postgres15|sqlserver2022)$ ]] || {
  usage >&2
  input_fail E_DATABASE "unsupported database: ${DATABASE:-<empty>}"
}
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ && "$RUN_ID" != . && "$RUN_ID" != .. ]] || \
  input_fail E_RUN_ID "unsafe run id"
[[ -n "$CELL_ROOT" ]] || input_fail E_CELL_ROOT "cell root is required"
RUN_ROOT="$ROOT_DIR/target/v934-step3-database-matrix/runs/$RUN_ID"
[[ -d "$RUN_ROOT" && ! -L "$RUN_ROOT" ]] || \
  input_fail E_CELL_ROOT "run root must be an existing regular directory: $RUN_ROOT"
if [[ "$ACTION" == run ]]; then
  EXPECTED_CELL_ROOT="$RUN_ROOT/cells/$DATABASE"
  [[ "${1:-}" == -- ]] || input_fail E_USAGE "run action requires -- before callback"
  shift
  [[ "$#" -gt 0 ]] || input_fail E_USAGE "run action requires a callback"
else
  EXPECTED_CELL_ROOT="$RUN_ROOT/preflight/$DATABASE"
  [[ "$#" -eq 0 ]] || input_fail E_USAGE "check action does not accept a callback"
fi
[[ "$CELL_ROOT" == "$EXPECTED_CELL_ROOT" ]] || \
  input_fail E_CELL_ROOT "cell root must be exactly $EXPECTED_CELL_ROOT"
CELL_PARENT="${CELL_ROOT%/*}"
[[ -d "$CELL_PARENT" && ! -L "$CELL_PARENT" ]] || \
  input_fail E_CELL_ROOT "cell parent must be an existing regular directory"
[[ ! -e "$CELL_ROOT" && ! -L "$CELL_ROOT" ]] || \
  input_fail E_CELL_ROOT "cell root already exists: $CELL_ROOT"

STATE_PROBE_AUTH="${V934_DB_STATE_AUTH:-}"
STATE_PROBE="${V934_DB_STATE_PROBE:-}"
if [[ -n "$STATE_PROBE_AUTH" || -n "$STATE_PROBE" ]]; then
  [[ "$STATE_PROBE_AUTH" == v934-database-state-negative-v1 ]] || \
    input_fail E_PROBE_AUTH "database-state probe authorization differs"
  [[ "$RUN_ID" == state-* && "$DATABASE" == mysql57 ]] || \
    input_fail E_PROBE_SCOPE "database-state probes require a state-* mysql57 child run"
fi
if [[ -n "$STATE_PROBE_AUTH" && "$ACTION" == run && -z "$STATE_PROBE" ]]; then
  input_fail E_PROBE_SCOPE "authorized state run requires an exact dynamic probe"
fi
if [[ -n "$STATE_PROBE" ]]; then
  [[ "$ACTION" == run ]] || input_fail E_PROBE_SCOPE "dynamic probes require the run action"
  [[ "$#" -eq 2 && "$1" == "$ROOT_DIR/scripts/v934/step3/database_state_probe_callback.sh" ]] || \
    input_fail E_PROBE_CALLBACK "dynamic probes require the exact versioned callback"
  [[ -f "$1" && ! -L "$1" ]] || \
    input_fail E_PROBE_CALLBACK "dynamic probe callback must be a regular non-symlink file"
  case "$STATE_PROBE:$2" in
    unavailable:noop|forced-cleanup-failure:noop|fixture-mutation:mutate-fixture|\
signal-int:wait-signal|signal-term:wait-signal|signal-hup:wait-signal)
      ;;
    *)
      input_fail E_PROBE_CALLBACK "probe/callback mode pair differs: $STATE_PROBE:${2:-<empty>}"
      ;;
  esac
fi

for command_name in cmp cut date docker grep mkdir mv python3 sed seq sha256sum sleep ss tail tr; do
  command -v "$command_name" >/dev/null 2>&1 || \
    input_fail E_TOOL "required command is missing: $command_name"
done
docker compose version >/dev/null 2>&1 || input_fail E_TOOL "docker compose v2 is required"
for required_file in "$BASE_COMPOSE" "$DIGEST_COMPOSE" "$AUTHORITY_COMPOSE"; do
  [[ -f "$required_file" ]] || input_fail E_INPUT "required file is missing: $required_file"
done

case "$DATABASE" in
  mysql57)
    SERVICE=mysql
    VOLUME_KEY=mysql-demo-data
    VOLUME_TARGET=/var/lib/mysql
    HOST_PORT=13306
    CONTAINER_PORT=3306
    PROFILE=docker
    EXPECTED_IMAGE_REF='mysql@sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb'
    EXPECTED_IMAGE_ID='sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb'
    ;;
  mysql8)
    SERVICE=mysql8
    VOLUME_KEY=mysql8-demo-data
    VOLUME_TARGET=/var/lib/mysql
    HOST_PORT=13308
    CONTAINER_PORT=3306
    PROFILE=mysql8
    EXPECTED_IMAGE_REF='mysql@sha256:f37951fc3753a6a22d6c7bf6978c5e5fefcf6f31814d98c582524f98eae52b21'
    EXPECTED_IMAGE_ID='sha256:f37951fc3753a6a22d6c7bf6978c5e5fefcf6f31814d98c582524f98eae52b21'
    ;;
  postgres15)
    SERVICE=postgres
    VOLUME_KEY=postgres-demo-data
    VOLUME_TARGET=/var/lib/postgresql/data
    HOST_PORT=15432
    CONTAINER_PORT=5432
    PROFILE=postgres
    EXPECTED_IMAGE_REF='postgres@sha256:fceb6f86328c36f2438fae3b851b0cc57c4a7e69a58c866d9ce24281f2cf0c9c'
    EXPECTED_IMAGE_ID='sha256:fceb6f86328c36f2438fae3b851b0cc57c4a7e69a58c866d9ce24281f2cf0c9c'
    ;;
  sqlserver2022)
    SERVICE=sqlserver
    VOLUME_KEY=sqlserver-demo-data
    VOLUME_TARGET=/var/opt/mssql
    HOST_PORT=11433
    CONTAINER_PORT=1433
    PROFILE=sqlserver
    EXPECTED_IMAGE_REF='mcr.microsoft.com/mssql/server@sha256:0ec7739e1c5ec2f57861facbe1f2b74f1d3e147c7c97edf91eeea920c5944d9c'
    EXPECTED_IMAGE_ID='sha256:0ec7739e1c5ec2f57861facbe1f2b74f1d3e147c7c97edf91eeea920c5944d9c'
    ;;
esac

SCOPE_HASH="$(printf '%s\n' "$RUN_ID|$DATABASE" | sha256sum | cut -c1-12)"
PROJECT="v934db-${DATABASE}-${SCOPE_HASH}"
export V934_AUTH_PROJECT="$PROJECT"
export V934_AUTH_MYSQL57_CONTAINER="${PROJECT}-mysql57"
export V934_AUTH_MYSQL8_CONTAINER="${PROJECT}-mysql8"
export V934_AUTH_POSTGRES15_CONTAINER="${PROJECT}-postgres15"
export V934_AUTH_SQLSERVER2022_CONTAINER="${PROJECT}-sqlserver2022"
export V934_AUTH_MYSQL57_VOLUME="${PROJECT}-mysql57-data"
export V934_AUTH_MYSQL8_VOLUME="${PROJECT}-mysql8-data"
export V934_AUTH_POSTGRES15_VOLUME="${PROJECT}-postgres15-data"
export V934_AUTH_SQLSERVER2022_VOLUME="${PROJECT}-sqlserver2022-data"
export V934_AUTH_NETWORK="${PROJECT}-network"

case "$DATABASE" in
  mysql57)
    CONTAINER="$V934_AUTH_MYSQL57_CONTAINER"
    VOLUME="$V934_AUTH_MYSQL57_VOLUME"
    ;;
  mysql8)
    CONTAINER="$V934_AUTH_MYSQL8_CONTAINER"
    VOLUME="$V934_AUTH_MYSQL8_VOLUME"
    ;;
  postgres15)
    CONTAINER="$V934_AUTH_POSTGRES15_CONTAINER"
    VOLUME="$V934_AUTH_POSTGRES15_VOLUME"
    ;;
  sqlserver2022)
    CONTAINER="$V934_AUTH_SQLSERVER2022_CONTAINER"
    VOLUME="$V934_AUTH_SQLSERVER2022_VOLUME"
    ;;
esac
NETWORK="$V934_AUTH_NETWORK"
COMPOSE=(docker compose --project-name "$PROJECT"
  -f "$BASE_COMPOSE" -f "$DIGEST_COMPOSE" -f "$AUTHORITY_COMPOSE")

mkdir -p "$CELL_ROOT"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
START_EPOCH="$(date -u +%s)"
PHASE=bootstrap
COMPOSE_ARMED=0
CLEANUP_STATUS=not-started
FIXTURE_BEFORE_SHA256=""
FIXTURE_AFTER_SHA256=""

atomic_env() {
  local output="$1"
  shift
  local temporary="${output}.$$.$RANDOM.tmp"
  printf '%s\n' "$@" > "$temporary"
  mv -f -- "$temporary" "$output"
}

write_status() {
  local exit_code="$1"
  local status=failed
  if [[ "$exit_code" -eq 0 && "$PHASE" == completed && "$CLEANUP_STATUS" == passed ]]; then
    status=passed
  fi
  atomic_env "$CELL_ROOT/status.env" \
    "run_id=$RUN_ID" \
    "database=$DATABASE" \
    "project=$PROJECT" \
    "started_at=$STARTED_AT" \
    "finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    "last_phase=$PHASE" \
    "exit_code=$exit_code" \
    "cleanup_status=$CLEANUP_STATUS" \
    "fixture_before_sha256=$FIXTURE_BEFORE_SHA256" \
    "fixture_after_sha256=$FIXTURE_AFTER_SHA256" \
    "status=$status"
}

resource_absent() {
  local container_names volume_names network_names
  local project_containers project_volumes project_networks

  docker info >/dev/null 2>&1 || return 2
  container_names="$(docker ps -a --format '{{.Names}}')" || return 2
  volume_names="$(docker volume ls -q)" || return 2
  network_names="$(docker network ls --format '{{.Name}}')" || return 2
  project_containers="$(
    docker ps -aq --filter "label=com.docker.compose.project=$PROJECT"
  )" || return 2
  project_volumes="$(
    docker volume ls -q --filter "label=com.docker.compose.project=$PROJECT"
  )" || return 2
  project_networks="$(
    docker network ls -q --filter "label=com.docker.compose.project=$PROJECT"
  )" || return 2

  ! grep -Fxq "$CONTAINER" <<< "$container_names" \
    && ! grep -Fxq "$VOLUME" <<< "$volume_names" \
    && ! grep -Fxq "$NETWORK" <<< "$network_names" \
    && [[ -z "$project_containers" ]] \
    && [[ -z "$project_volumes" ]] \
    && [[ -z "$project_networks" ]]
}

cleanup() {
  local cleanup_code=0
  local project_container_output=""
  local project_volume_output=""
  local project_network_output=""
  local -a project_containers=()
  local -a project_volumes=()
  local -a project_networks=()
  if [[ "$COMPOSE_ARMED" -eq 1 ]]; then
    # Never call `compose down -v` here: the shared base file declares other
    # explicitly named demo volumes. Remove only resources whose exact name or
    # Compose project label belongs to this run-scoped cell.
    project_container_output="$(
      docker ps -aq --filter "label=com.docker.compose.project=$PROJECT"
    )" || cleanup_code=1
    if [[ -n "${project_container_output:-}" ]]; then
      mapfile -t project_containers <<< "$project_container_output"
      docker rm -fv -- "${project_containers[@]}" >/dev/null 2>&1 || cleanup_code=1
    fi
    project_volume_output="$(
      docker volume ls -q --filter "label=com.docker.compose.project=$PROJECT"
    )" || cleanup_code=1
    if [[ -n "${project_volume_output:-}" ]]; then
      mapfile -t project_volumes <<< "$project_volume_output"
      docker volume rm -- "${project_volumes[@]}" >/dev/null 2>&1 || cleanup_code=1
    fi
    project_network_output="$(
      docker network ls -q --filter "label=com.docker.compose.project=$PROJECT"
    )" || cleanup_code=1
    if [[ -n "${project_network_output:-}" ]]; then
      mapfile -t project_networks <<< "$project_network_output"
      docker network rm -- "${project_networks[@]}" >/dev/null 2>&1 || cleanup_code=1
    fi
  fi
  resource_absent || cleanup_code=1
  if [[ "$STATE_PROBE" == forced-cleanup-failure && "$cleanup_code" -eq 0 ]]; then
    echo "[v934-db-provision] ERROR E_CLEANUP_FORCED: injected cleanup result after zero-residue removal" >&2
    cleanup_code=1
  fi
  if [[ "$cleanup_code" -eq 0 ]]; then
    CLEANUP_STATUS=passed
  else
    CLEANUP_STATUS=failed
  fi
  if ! atomic_env "$CELL_ROOT/cleanup.env" \
    "database=$DATABASE" \
    "project=$PROJECT" \
    "container=$CONTAINER" \
    "volume=$VOLUME" \
    "network=$NETWORK" \
    "status=$CLEANUP_STATUS"; then
    CLEANUP_STATUS=failed
    cleanup_code=1
  fi
  return "$cleanup_code"
}

on_exit() {
  local exit_code="$?"
  # Cleanup/status publication is one non-reentrant critical section. A
  # repeated signal must not interrupt it and strand run-owned resources.
  trap '' INT TERM HUP
  trap - EXIT
  set +e
  if ! cleanup; then
    PHASE=cleanup-failed
    [[ "$exit_code" -ne 0 ]] || exit_code=1
  fi
  if ! write_status "$exit_code"; then
    echo "[v934-db-provision] ERROR E_STATUS: cannot publish durable cell status" >&2
    exit_code=1
  fi
  exit "$exit_code"
}

trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

fail() {
  local code="$1"
  shift
  echo "[v934-db-provision] ERROR $code: $*" >&2
  exit 1
}

PHASE=preflight
resource_absent || fail E_RESOURCE_STALE "run-scoped Docker resource already exists"

PHASE=compose-contract
compose_json="$("${COMPOSE[@]}" config --format json)" || \
  fail E_COMPOSE_CONTRACT "cannot render the merged Compose configuration"
python3 -c '
import json
import sys

(
    project,
    service_name,
    image,
    container,
    host_port,
    container_port,
    volume_key,
    volume_name,
    volume_target,
    network_name,
) = sys.argv[1:]
config = json.load(sys.stdin)
service = config.get("services", {}).get(service_name)
expected_port = [{
    "mode": "ingress",
    "host_ip": "127.0.0.1",
    "target": int(container_port),
    "published": host_port,
    "protocol": "tcp",
}]
expected_volume = {
    "type": "volume",
    "source": volume_key,
    "target": volume_target,
}
volume_mounts = [
    {key: mount.get(key) for key in expected_volume}
    for mount in (service or {}).get("volumes", [])
    if mount.get("type") == "volume"
]
if (
    config.get("name") != project
    or not isinstance(service, dict)
    or service.get("image") != image
    or service.get("container_name") != container
    or service.get("restart") != "no"
    or service.get("ports") != expected_port
    or set((service.get("networks") or {})) != {"foggy-demo-net"}
    or config.get("volumes", {}).get(volume_key, {}).get("name") != volume_name
    or volume_mounts != [expected_volume]
    or config.get("networks", {}).get("foggy-demo-net", {}).get("name") != network_name
):
    raise SystemExit("merged Compose identity differs")
' "$PROJECT" "$SERVICE" "$EXPECTED_IMAGE_REF" "$CONTAINER" \
  "$HOST_PORT" "$CONTAINER_PORT" "$VOLUME_KEY" "$VOLUME" \
  "$VOLUME_TARGET" "$NETWORK" <<< "$compose_json" || \
  fail E_COMPOSE_CONTRACT "merged Compose identity differs for $DATABASE"
unset compose_json

PHASE=preflight
if ss -H -ltn "sport = :$HOST_PORT" | grep -q .; then
  fail E_PORT_OWNED "frozen host port $HOST_PORT is already occupied; external state was not changed"
fi

atomic_env "$CELL_ROOT/resource.env" \
  "run_id=$RUN_ID" \
  "database=$DATABASE" \
  "service=$SERVICE" \
  "project=$PROJECT" \
  "container=$CONTAINER" \
  "volume=$VOLUME" \
  "network=$NETWORK" \
  "host_port=$HOST_PORT" \
  "container_port=$CONTAINER_PORT" \
  "profile=$PROFILE" \
  "expected_image_ref=$EXPECTED_IMAGE_REF" \
  "expected_image_id=$EXPECTED_IMAGE_ID"

if [[ "$ACTION" == check ]]; then
  PHASE=completed
  exit 0
fi

PHASE=compose-up
COMPOSE_ARMED=1
"${COMPOSE[@]}" up -d --no-build --no-deps "$SERVICE" >/dev/null
if [[ "$STATE_PROBE" == unavailable ]]; then
  docker stop --timeout 1 "$CONTAINER" >/dev/null || \
    fail E_PROBE_INJECTION "could not stop the run-owned unavailable probe container"
fi

PHASE=health
health=""
for _ in $(seq 1 120); do
  health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
    "$CONTAINER" 2>/dev/null || true)"
  [[ "$health" == healthy ]] && break
  [[ "$health" == unhealthy || "$health" == exited || "$health" == dead ]] && \
    fail E_CONTAINER_HEALTH "$CONTAINER reached terminal state $health"
  sleep 2
done
[[ "$health" == healthy ]] || fail E_CONTAINER_HEALTH "$CONTAINER did not become healthy"

PHASE=resource-identity
actual_image_id="$(docker inspect -f '{{.Image}}' "$CONTAINER")"
actual_image_ref="$(docker inspect -f '{{.Config.Image}}' "$CONTAINER")"
actual_project="$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$CONTAINER")"
actual_service="$(docker inspect -f '{{index .Config.Labels "com.docker.compose.service"}}' "$CONTAINER")"
mapped_port="$(docker port "$CONTAINER" "$CONTAINER_PORT/tcp" | tail -n 1)"
[[ "$actual_image_id" == "$EXPECTED_IMAGE_ID" ]] || \
  fail E_IMAGE_ID "actual=$actual_image_id expected=$EXPECTED_IMAGE_ID"
[[ "$actual_image_ref" == "$EXPECTED_IMAGE_REF" ]] || \
  fail E_IMAGE_REF "actual=$actual_image_ref expected=$EXPECTED_IMAGE_REF"
[[ "$actual_project" == "$PROJECT" && "$actual_service" == "$SERVICE" ]] || \
  fail E_COMPOSE_IDENTITY "actual=$actual_project/$actual_service expected=$PROJECT/$SERVICE"
[[ "$mapped_port" == "127.0.0.1:$HOST_PORT" ]] || \
  fail E_PORT_COORDINATE "actual=$mapped_port expected=127.0.0.1:$HOST_PORT"
actual_repo_digests="$(docker image inspect "$EXPECTED_IMAGE_REF" -f '{{json .RepoDigests}}')" || \
  fail E_IMAGE_DIGEST "cannot inspect RepoDigests for frozen reference"
grep -Fq "\"$EXPECTED_IMAGE_REF\"" <<< "$actual_repo_digests" || \
  fail E_IMAGE_DIGEST "RepoDigests lacks frozen reference"

volume_project="$(docker volume inspect -f '{{index .Labels "com.docker.compose.project"}}' "$VOLUME")"
network_project="$(docker network inspect -f '{{index .Labels "com.docker.compose.project"}}' "$NETWORK")"
[[ "$volume_project" == "$PROJECT" && "$network_project" == "$PROJECT" ]] || \
  fail E_RESOURCE_OWNER "volume/network ownership label mismatch"
volume_created="$(docker volume inspect -f '{{.CreatedAt}}' "$VOLUME")"
volume_epoch="$(date -d "$volume_created" +%s)"
[[ "$volume_epoch" -ge "$START_EPOCH" ]] || \
  fail E_VOLUME_STALE "volume predates the cell marker: $volume_created"

mysql_query() {
  docker exec -e MYSQL_PWD=foggy_test_123 "$CONTAINER" \
    mysql --batch --raw --skip-column-names -ufoggy foggy_test -e "$1"
}

wait_for_mysql_initialization() {
  local marker_count=""
  identity=""
  for _ in $(seq 1 120); do
    if identity="$(mysql_query "SELECT CONCAT(DATABASE(), '|', VERSION());" 2>/dev/null)" \
      && marker_count="$(mysql_query \
        "SELECT COUNT(DISTINCT preagg_name) FROM preagg_watermark
         WHERE preagg_name IN ('daily_product_sales', 'monthly_category_sales',
           'daily_customer_channel_sales', 'daily_return');" 2>/dev/null)" \
      && [[ "$marker_count" == 4 ]]; then
      return 0
    fi
    sleep 0.5
  done
  return 1
}

postgres_query() {
  docker exec "$CONTAINER" psql -v ON_ERROR_STOP=1 -At -U foggy -d foggy_test -c "$1"
}

sqlserver_query() {
  docker exec -e SQLCMDPASSWORD='Foggy_Test_123!' "$CONTAINER" \
    /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -h -1 -W -d foggy_test -Q "$1" \
    | tr -d '\r' | sed '/^[[:space:]]*$/d'
}

PHASE=base-initialization
if [[ "$DATABASE" == sqlserver2022 ]]; then
  for script_name in 01-schema.sql 02-dict-data.sql 03-test-data.sql; do
    docker exec -i -e SQLCMDPASSWORD='Foggy_Test_123!' "$CONTAINER" \
      /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b \
      < "$COMPOSE_DIR/sqlserver/init/$script_name" >/dev/null
  done
fi

case "$DATABASE" in
  mysql57)
    # The official MySQL entrypoint starts a temporary server while it creates
    # the configured database/user before it finishes every init script. A
    # root ping or successful business login can therefore be premature. The
    # four watermark rows are written by the final init script and prove that
    # both identity and the complete base fixture are ready.
    wait_for_mysql_initialization || \
      fail E_DATABASE_READINESS "foggy_test identity/base fixture did not become fully initialized"
    [[ "$identity" == 'foggy_test|5.7.44-log' ]] || \
      fail E_DATABASE_IDENTITY "actual=$identity expected=foggy_test|5.7.44-log"
    ;;
  mysql8)
    wait_for_mysql_initialization || \
      fail E_DATABASE_READINESS "foggy_test identity/base fixture did not become fully initialized"
    [[ "$identity" == foggy_test\|8.0.* ]] || \
      fail E_DATABASE_IDENTITY "actual=$identity expected=foggy_test|8.0.x"
    ;;
  postgres15)
    identity="$(postgres_query "SELECT current_database() || '|' || current_schema() || '|' || current_setting('server_version');")"
    [[ "$identity" == foggy_test\|public\|15.* ]] || \
      fail E_DATABASE_IDENTITY "actual=$identity expected=foggy_test|public|15.x"
    ;;
  sqlserver2022)
    identity="$(sqlserver_query "SET NOCOUNT ON; SELECT DB_NAME() + '|' + SCHEMA_NAME() + '|' + CAST(SERVERPROPERTY('ProductVersion') AS varchar(30));")"
    [[ "$identity" == foggy_test\|dbo\|16.0.* ]] || \
      fail E_DATABASE_IDENTITY "actual=$identity expected=foggy_test|dbo|16.0.x"
    ;;
esac
printf '%s\n' "$identity" > "$CELL_ROOT/database-identity.txt"
atomic_env "$CELL_ROOT/runtime.env" \
  "database=$DATABASE" \
  "actual_image_id=$actual_image_id" \
  "actual_image_ref=$actual_image_ref" \
  "actual_repo_digest=$EXPECTED_IMAGE_REF" \
  "actual_project=$actual_project" \
  "actual_service=$actual_service" \
  "actual_mapped_port=$mapped_port" \
  "volume_project=$volume_project" \
  "network_project=$network_project" \
  "volume_created=$volume_created" \
  "database_identity=$identity" \
  "status=verified"

apply_fixture() {
  case "$DATABASE" in
    mysql57|mysql8)
      for script_name in 12-v934-sentinel.sql 13-v934-parity-fixture.sql 14-v934-preagg-fixture.sql; do
        docker exec -i -e MYSQL_PWD=foggy_test_123 "$CONTAINER" \
          mysql -ufoggy foggy_test < "$FIXTURE_ROOT/mysql/$script_name"
      done
      ;;
    postgres15)
      for script_name in 12-v934-sentinel.sql 13-v934-parity-fixture.sql 14-v934-preagg-fixture.sql; do
        docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U foggy -d foggy_test \
          < "$FIXTURE_ROOT/postgres/$script_name" >/dev/null
      done
      ;;
    sqlserver2022)
      for script_name in 12-v934-sentinel.sql 13-v934-parity-fixture.sql 14-v934-preagg-fixture.sql; do
        docker exec -i -e SQLCMDPASSWORD='Foggy_Test_123!' "$CONTAINER" \
          /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -d foggy_test \
          < "$FIXTURE_ROOT/sqlserver/$script_name" >/dev/null
      done
      ;;
  esac
}

snapshot_to() {
  local output="$1"
  local temporary="${output}.$$.$RANDOM.tmp"
  case "$DATABASE" in
    mysql57|mysql8)
      mysql_query \
        "SELECT CONCAT('sentinel|', sentinel_key, '|', sentinel_value) FROM v934_test_sentinel ORDER BY sentinel_key;
         SELECT CONCAT('parity|', order_id, '|', order_line_no) FROM fact_sales WHERE order_id = 'V934_PARITY_SENTINEL' ORDER BY order_line_no;
         SELECT CONCAT('preagg|', category_name, '|', CAST(SUM(sales_amount_sum) AS CHAR)) FROM v934_preagg_daily_product_sales GROUP BY category_name ORDER BY category_name;" \
        > "$temporary"
      ;;
    postgres15)
      postgres_query \
        "SELECT 'sentinel|' || sentinel_key || '|' || sentinel_value FROM public.v934_test_sentinel ORDER BY sentinel_key;
         SELECT 'parity|' || order_id || '|' || order_line_no FROM public.fact_sales WHERE order_id = 'V934_PARITY_SENTINEL' ORDER BY order_line_no;
         SELECT 'preagg|' || category_name || '|' || SUM(sales_amount_sum)::text FROM public.v934_preagg_daily_product_sales GROUP BY category_name ORDER BY category_name;" \
        > "$temporary"
      ;;
    sqlserver2022)
      sqlserver_query \
        "SET NOCOUNT ON;
         SELECT 'sentinel|' + sentinel_key + '|' + sentinel_value FROM dbo.v934_test_sentinel ORDER BY sentinel_key;
         SELECT 'parity|' + order_id + '|' + CONVERT(varchar(10), order_line_no) FROM dbo.fact_sales WHERE order_id = 'V934_PARITY_SENTINEL' ORDER BY order_line_no;
         SELECT 'preagg|' + category_name + '|' + CONVERT(varchar(40), CAST(SUM(sales_amount_sum) AS decimal(20,4))) FROM dbo.v934_preagg_daily_product_sales GROUP BY category_name ORDER BY category_name;" \
        > "$temporary"
      ;;
  esac
  mv -f -- "$temporary" "$output"
}

assert_snapshot() {
  local snapshot="$1"
  local expected=$'sentinel|contract_version|9.3.4\nparity|V934_PARITY_SENTINEL|1\nparity|V934_PARITY_SENTINEL|2\npreagg|V934_ALPHA|50.0000\npreagg|V934_BETA|40.0000\npreagg|V934_GAMMA|10.0000'
  local actual
  actual="$(<"$snapshot")"
  [[ "$actual" == "$expected" ]] || \
    fail E_FIXTURE_SNAPSHOT "unexpected canonical fixture snapshot for $DATABASE"
}

PHASE=fixture-first-apply
apply_fixture
snapshot_to "$CELL_ROOT/fixture-first.txt"
assert_snapshot "$CELL_ROOT/fixture-first.txt"

PHASE=fixture-second-apply
apply_fixture
snapshot_to "$CELL_ROOT/fixture-before.txt"
assert_snapshot "$CELL_ROOT/fixture-before.txt"
cmp -s "$CELL_ROOT/fixture-first.txt" "$CELL_ROOT/fixture-before.txt" || \
  fail E_FIXTURE_IDEMPOTENCY "first and second apply snapshots differ"
FIXTURE_BEFORE_SHA256="$(sha256sum "$CELL_ROOT/fixture-before.txt" | cut -d' ' -f1)"

export V934_DB_KIND="$DATABASE"
export V934_DB_CONTAINER="$CONTAINER"
export V934_DB_CELL_ROOT="$CELL_ROOT"
export V934_DB_PROFILE="$PROFILE"
export V934_DB_EXPECTED_DATABASE="$DATABASE"

PHASE=callback
"$@"

PHASE=fixture-after
snapshot_to "$CELL_ROOT/fixture-after.txt"
FIXTURE_AFTER_SHA256="$(sha256sum "$CELL_ROOT/fixture-after.txt" | cut -d' ' -f1)"
[[ "$FIXTURE_BEFORE_SHA256" == "$FIXTURE_AFTER_SHA256" ]] || \
  fail E_FIXTURE_MUTATION "before/after fixture SHA-256 differs"
cmp -s "$CELL_ROOT/fixture-before.txt" "$CELL_ROOT/fixture-after.txt" || \
  fail E_FIXTURE_MUTATION "before/after canonical fixture differs"
assert_snapshot "$CELL_ROOT/fixture-after.txt"

PHASE=completed
