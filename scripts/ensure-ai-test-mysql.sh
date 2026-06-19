#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-13306}"
MYSQL_DATABASE="${MYSQL_DATABASE:-foggy_test}"
MYSQL_USER="${MYSQL_USER:-foggy}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-foggy_test_123}"

COMPOSE_FILE="$REPO_ROOT/foggy-dataset-demo/docker/docker-compose.yml"
INIT_DIR="$REPO_ROOT/foggy-dataset-demo/docker/mysql/init"
LOCAL_DATA_DIR="${LOCAL_DATA_DIR:-$REPO_ROOT/target/foggy-ai-mysql57-data}"
LOCAL_RUN_DIR="${LOCAL_RUN_DIR:-$REPO_ROOT/target/foggy-ai-mysql57-run}"
LOCAL_SOCKET="${LOCAL_SOCKET:-/tmp/foggy-ai-mysql57.sock}"
LOCAL_PID_FILE="${LOCAL_PID_FILE:-/tmp/foggy-ai-mysql57.pid}"

FORCE_INIT=0
NO_START=0
DOCKER_ONLY=0
LOCAL_ONLY=0

usage() {
  cat <<'USAGE'
Usage: scripts/ensure-ai-test-mysql.sh [options]

Ensures the MCP AI/direct integration fixture database is reachable at
127.0.0.1:13306 by validating foggy_test.fact_sales.

Default behavior:
  1. Reuse an already healthy MySQL fixture.
  2. Start foggy-dataset-demo/docker mysql through docker compose when available.
  3. Fall back to a local mysqld under target/foggy-ai-mysql57-data when Docker
     is not available and local mysql/mysqld binaries exist.

Options:
  --init        Reimport demo mysql/init/*.sql into the currently reachable DB.
  --no-start    Only validate the current DB; do not start Docker or local mysqld.
  --docker-only Do not use the local mysqld fallback.
  --local-only  Do not use Docker compose.
  -h, --help    Show this help.
USAGE
}

log() {
  echo "[ai-mysql] $*"
}

fail() {
  echo "[ai-mysql] ERROR: $*" >&2
  exit 1
}

for arg in "$@"; do
  case "$arg" in
    --init) FORCE_INIT=1 ;;
    --no-start) NO_START=1 ;;
    --docker-only) DOCKER_ONLY=1 ;;
    --local-only) LOCAL_ONLY=1 ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ "$DOCKER_ONLY" -eq 1 && "$LOCAL_ONLY" -eq 1 ]]; then
  fail "--docker-only and --local-only cannot be used together"
fi

require_mysql_client() {
  command -v mysql >/dev/null 2>&1 || fail "mysql client is required on PATH"
}

fact_sales_count() {
  MYSQL_PWD="$MYSQL_PASSWORD" mysql \
    --protocol=tcp \
    -h "$MYSQL_HOST" \
    -P "$MYSQL_PORT" \
    -u "$MYSQL_USER" \
    --batch \
    --skip-column-names \
    "$MYSQL_DATABASE" \
    -e "SELECT COUNT(*) FROM fact_sales;" 2>/dev/null
}

validate_fixture_quiet() {
  local count
  count="$(fact_sales_count)" || return 1
  [[ "$count" =~ ^[0-9]+$ && "$count" -gt 0 ]]
}

validate_fixture() {
  local count
  count="$(fact_sales_count)" || return 1
  if [[ ! "$count" =~ ^[0-9]+$ || "$count" -le 0 ]]; then
    return 1
  fi
  log "MySQL fixture OK: $MYSQL_HOST:$MYSQL_PORT/$MYSQL_DATABASE fact_sales=$count"
}

import_fixtures_as_user() {
  require_mysql_client
  [[ -d "$INIT_DIR" ]] || fail "MySQL init directory not found: $INIT_DIR"

  log "Importing MySQL fixture SQL from $INIT_DIR"
  for sql in "$INIT_DIR"/*.sql; do
    [[ -f "$sql" ]] || continue
    log "  import $(basename "$sql")"
    MYSQL_PWD="$MYSQL_PASSWORD" mysql \
      --protocol=tcp \
      -h "$MYSQL_HOST" \
      -P "$MYSQL_PORT" \
      -u "$MYSQL_USER" \
      "$MYSQL_DATABASE" < "$sql"
  done
}

compose_available() {
  command -v docker >/dev/null 2>&1 || return 1
  if docker compose version >/dev/null 2>&1; then
    return 0
  fi
  command -v docker-compose >/dev/null 2>&1
}

compose_cmd() {
  if docker compose version >/dev/null 2>&1; then
    docker compose -f "$COMPOSE_FILE" "$@"
  else
    docker-compose -f "$COMPOSE_FILE" "$@"
  fi
}

try_docker_mysql() {
  [[ "$LOCAL_ONLY" -eq 0 ]] || return 1
  compose_available || return 1
  [[ -f "$COMPOSE_FILE" ]] || fail "Compose file not found: $COMPOSE_FILE"

  log "Starting docker compose mysql fixture"
  if ! compose_cmd up -d mysql; then
    log "Docker compose mysql start failed; local fallback may still be used"
    return 1
  fi

  for _ in {1..90}; do
    if validate_fixture_quiet; then
      validate_fixture
      return 0
    fi
    sleep 2
  done

  log "Docker mysql started but fixture validation did not pass"
  return 1
}

port_in_use() {
  if command -v nc >/dev/null 2>&1; then
    nc -z "$MYSQL_HOST" "$MYSQL_PORT" >/dev/null 2>&1
    return $?
  fi
  return 1
}

wait_for_root_socket() {
  local mysql_bin="$1"
  for _ in {1..90}; do
    if "$mysql_bin" --socket="$LOCAL_SOCKET" -uroot -e "SELECT 1;" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

setup_local_users() {
  local mysql_bin="$1"
  "$mysql_bin" --socket="$LOCAL_SOCKET" -uroot <<SQL
CREATE DATABASE IF NOT EXISTS \`$MYSQL_DATABASE\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$MYSQL_USER'@'%' IDENTIFIED BY '$MYSQL_PASSWORD';
CREATE USER IF NOT EXISTS '$MYSQL_USER'@'localhost' IDENTIFIED BY '$MYSQL_PASSWORD';
CREATE USER IF NOT EXISTS '$MYSQL_USER'@'127.0.0.1' IDENTIFIED BY '$MYSQL_PASSWORD';
GRANT ALL PRIVILEGES ON \`$MYSQL_DATABASE\`.* TO '$MYSQL_USER'@'%';
GRANT ALL PRIVILEGES ON \`$MYSQL_DATABASE\`.* TO '$MYSQL_USER'@'localhost';
GRANT ALL PRIVILEGES ON \`$MYSQL_DATABASE\`.* TO '$MYSQL_USER'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
}

try_local_mysql() {
  [[ "$DOCKER_ONLY" -eq 0 ]] || return 1
  require_mysql_client
  command -v mysqld >/dev/null 2>&1 || return 1

  if port_in_use; then
    log "Port $MYSQL_HOST:$MYSQL_PORT is in use but the fixture is not valid; not starting local mysqld"
    return 1
  fi

  mkdir -p "$LOCAL_DATA_DIR" "$LOCAL_RUN_DIR"

  local mysqld_bin mysql_bin initialized_now
  mysqld_bin="$(command -v mysqld)"
  mysql_bin="$(command -v mysql)"
  initialized_now=0

  if [[ ! -d "$LOCAL_DATA_DIR/mysql" ]]; then
    log "Initializing local MySQL data dir: $LOCAL_DATA_DIR"
    "$mysqld_bin" --initialize-insecure --datadir="$LOCAL_DATA_DIR" \
      --log-error="$LOCAL_RUN_DIR/initialize.err"
    initialized_now=1
  fi

  log "Starting local mysqld on $MYSQL_HOST:$MYSQL_PORT"
  "$mysqld_bin" \
    --no-defaults \
    --datadir="$LOCAL_DATA_DIR" \
    --socket="$LOCAL_SOCKET" \
    --pid-file="$LOCAL_PID_FILE" \
    --port="$MYSQL_PORT" \
    --bind-address="$MYSQL_HOST" \
    --log-error="$LOCAL_RUN_DIR/mysql.err" \
    --skip-name-resolve \
    --character-set-server=utf8mb4 \
    --collation-server=utf8mb4_unicode_ci \
    --default-time-zone=+08:00 \
    --lower-case-table-names=1 \
    --max-connections=200 \
    --explicit_defaults_for_timestamp=ON \
    --daemonize

  wait_for_root_socket "$mysql_bin" || fail "Local mysqld did not become ready; see $LOCAL_RUN_DIR/mysql.err"
  setup_local_users "$mysql_bin"

  if [[ "$initialized_now" -eq 1 || "$FORCE_INIT" -eq 1 ]]; then
    import_fixtures_as_user
  fi

  validate_fixture
}

require_mysql_client

if validate_fixture_quiet; then
  if [[ "$FORCE_INIT" -eq 1 ]]; then
    import_fixtures_as_user
  fi
  validate_fixture
  exit 0
fi

if [[ "$NO_START" -eq 1 ]]; then
  fail "MySQL fixture is not ready at $MYSQL_HOST:$MYSQL_PORT/$MYSQL_DATABASE"
fi

if try_docker_mysql; then
  exit 0
fi

if try_local_mysql; then
  exit 0
fi

fail "Unable to prepare MySQL fixture. Install/start Docker, or provide mysql and mysqld on PATH."
