#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

RUNTIME_DIR="${FOGGY_RUNTIME_CONSOLE_DIR:-$REPO_ROOT/.foggy-runtime/runtime-console}"
LEGACY_RUNTIME_DIR="$REPO_ROOT/foggy-mcp-launcher/target/runtime-console-manual"
JAR_PATH="${FOGGY_RUNTIME_CONSOLE_JAR:-$REPO_ROOT/foggy-mcp-launcher/target/foggy-mcp-launcher-9.2.0-SNAPSHOT.jar}"
PORT="${FOGGY_RUNTIME_CONSOLE_PORT:-18066}"

TOKEN_FILE="$RUNTIME_DIR/runtime-auth-code.txt"
DATABASE_FILE="$RUNTIME_DIR/runtime-console.db"
BUNDLE_REGISTRY="$RUNTIME_DIR/runtime-bundles.json"
DATASOURCE_REGISTRY="$RUNTIME_DIR/runtime-datasources.json"
PID_FILE="$RUNTIME_DIR/runtime.pid"
LOG_FILE="$RUNTIME_DIR/runtime.log"

usage() {
  cat <<USAGE
Usage: scripts/runtime-console.sh <command>

Commands:
  start       Start Runtime Console in a detached session.
  stop        Stop the Runtime Console process started by this script.
  restart     Stop and start Runtime Console.
  status      Show process, URL, state directory, token path, and log path.
  foreground  Start in the current terminal (mainly for diagnostics).

Environment overrides:
  FOGGY_RUNTIME_CONSOLE_DIR   Runtime state directory
                              (default: .foggy-runtime/runtime-console)
  FOGGY_RUNTIME_CONSOLE_JAR   Launcher JAR path
  FOGGY_RUNTIME_CONSOLE_PORT  HTTP port (default: 18066)

The script never prints the Runtime API token. On the first run it migrates
existing local state from foggy-mcp-launcher/target/runtime-console-manual
when present; otherwise it creates a new local token.
USAGE
}

log() {
  printf '[runtime-console] %s\n' "$*"
}

fail() {
  printf '[runtime-console] ERROR: %s\n' "$*" >&2
  exit 1
}

copy_legacy_file() {
  local name="$1"
  if [[ ! -e "$RUNTIME_DIR/$name" && -e "$LEGACY_RUNTIME_DIR/$name" ]]; then
    cp -p -- "$LEGACY_RUNTIME_DIR/$name" "$RUNTIME_DIR/$name"
  fi
}

normalize_legacy_registry_paths() {
  local registry contents temporary
  for registry in "$BUNDLE_REGISTRY" "$DATASOURCE_REGISTRY"; do
    [[ -f "$registry" ]] || continue
    contents="$(<"$registry")"
    [[ "$contents" == *"$LEGACY_RUNTIME_DIR"* ]] || continue

    contents="${contents//"$LEGACY_RUNTIME_DIR"/"$RUNTIME_DIR"}"
    temporary="$(mktemp "$RUNTIME_DIR/.registry.XXXXXX")"
    printf '%s\n' "$contents" > "$temporary"
    chmod --reference="$registry" "$temporary"
    mv -- "$temporary" "$registry"
  done
}

create_token() {
  [[ -s "$TOKEN_FILE" ]] && return

  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 24 > "$TOKEN_FILE"
  elif [[ -r /proc/sys/kernel/random/uuid ]]; then
    tr -d '-' < /proc/sys/kernel/random/uuid > "$TOKEN_FILE"
  else
    fail "cannot create a token: install openssl or provide $TOKEN_FILE"
  fi
  chmod 600 "$TOKEN_FILE"
  log "Created a local Runtime API token at $TOKEN_FILE"
}

prepare_state() {
  umask 077
  mkdir -p "$RUNTIME_DIR"

  copy_legacy_file runtime-auth-code.txt
  copy_legacy_file runtime-console.db
  copy_legacy_file runtime-bundles.json
  copy_legacy_file runtime-datasources.json

  if [[ ! -d "$RUNTIME_DIR/sample-models" && -d "$LEGACY_RUNTIME_DIR/sample-models" ]]; then
    cp -a -- "$LEGACY_RUNTIME_DIR/sample-models" "$RUNTIME_DIR/sample-models"
  fi

  normalize_legacy_registry_paths
  create_token
}

require_launcher() {
  command -v java >/dev/null 2>&1 || fail "java is required on PATH"
  command -v curl >/dev/null 2>&1 || fail "curl is required on PATH"
  [[ -f "$JAR_PATH" ]] || fail \
    "launcher JAR not found: $JAR_PATH
Build it with:
  mvn -pl foggy-mcp-launcher -am -Pruntime-console -DskipTests package"
}

read_runtime_pid() {
  local candidate
  [[ -f "$PID_FILE" ]] || return 1
  IFS= read -r candidate < "$PID_FILE"
  [[ "$candidate" =~ ^[0-9]+$ ]] || return 1
  printf '%s\n' "$candidate"
}

is_runtime_process() {
  local pid="$1"
  [[ -r "/proc/$pid/cmdline" ]] || return 1
  tr '\0' ' ' < "/proc/$pid/cmdline" | grep -Fq -- "$JAR_PATH"
}

running_pid() {
  local pid
  pid="$(read_runtime_pid)" || return 1
  kill -0 "$pid" 2>/dev/null || return 1
  is_runtime_process "$pid" || return 1
  printf '%s\n' "$pid"
}

port_is_listening() {
  command -v ss >/dev/null 2>&1 || return 1
  ss -ltn "sport = :$PORT" | tail -n +2 | grep -q .
}

run_foreground() {
  local runtime_auth_code
  prepare_state
  require_launcher

  IFS= read -r runtime_auth_code < "$TOKEN_FILE"
  export FOGGY_RUNTIME_API_AUTH_CODE="$runtime_auth_code"
  export SERVER_PORT="$PORT"
  export MCP_LITE_SQLITE_PATH="$DATABASE_FILE"
  export OPENAI_API_KEY="${OPENAI_API_KEY:-runtime-console-manual-placeholder}"

  printf '%s\n' "$$" > "$PID_FILE"
  exec >> "$LOG_FILE" 2>&1

  exec java -jar "$JAR_PATH" \
    --server.address=0.0.0.0 \
    --spring.profiles.active=lite \
    --foggy.runtime-api.enabled=true \
    --foggy.runtime-api.security-mode=auth-code \
    --foggy.runtime-api.auth-scope=management-all \
    --foggy.runtime-console.enabled=true \
    --foggy.runtime-api.bundle-registry.path="$BUNDLE_REGISTRY" \
    --foggy.runtime-api.datasource-registry.path="$DATASOURCE_REGISTRY"
}

start_runtime() {
  local pid
  prepare_state
  require_launcher

  if pid="$(running_pid)"; then
    log "Already running (PID $pid): http://127.0.0.1:$PORT/console/"
    return
  fi

  if port_is_listening; then
    fail "port $PORT is already in use by another process"
  fi

  command -v setsid >/dev/null 2>&1 || fail "setsid is required for detached startup"
  setsid -f "$SCRIPT_DIR/runtime-console.sh" foreground </dev/null

  for _ in $(seq 1 45); do
    if curl -fsS "http://127.0.0.1:$PORT/console/" >/dev/null 2>&1; then
      pid="$(running_pid)" || fail "HTTP is ready but the managed PID cannot be verified"
      log "Started (PID $pid): http://127.0.0.1:$PORT/console/"
      log "Token file: $TOKEN_FILE"
      log "Log file: $LOG_FILE"
      return
    fi
    sleep 1
  done

  fail "startup timed out; inspect $LOG_FILE"
}

stop_runtime() {
  local pid
  if ! pid="$(running_pid)"; then
    log "Not running"
    return
  fi

  kill "$pid"
  for _ in $(seq 1 30); do
    if ! kill -0 "$pid" 2>/dev/null; then
      log "Stopped PID $pid"
      return
    fi
    sleep 1
  done

  fail "PID $pid did not stop within 30 seconds"
}

show_status() {
  local pid
  if pid="$(running_pid)"; then
    log "RUNNING (PID $pid)"
    printf 'URL:        http://127.0.0.1:%s/console/\n' "$PORT"
  else
    log "STOPPED"
  fi
  printf 'State:      %s\n' "$RUNTIME_DIR"
  printf 'Token file: %s\n' "$TOKEN_FILE"
  printf 'Log file:   %s\n' "$LOG_FILE"
}

command="${1:-}"
case "$command" in
  start)
    start_runtime
    ;;
  stop)
    stop_runtime
    ;;
  restart)
    stop_runtime
    start_runtime
    ;;
  status)
    show_status
    ;;
  foreground)
    run_foreground
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
