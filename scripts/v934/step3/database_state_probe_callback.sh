#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-}"

fail() {
  echo "[v934-db-state-callback] ERROR $1: ${2:-probe callback failed}" >&2
  exit 1
}

[[ "${V934_DB_STATE_AUTH:-}" == "v934-database-state-negative-v1" ]] || \
  fail E_PROBE_AUTH "database-state probe authorization is missing"
[[ "${V934_DB_KIND:-}" == "mysql57" ]] || \
  fail E_PROBE_DATABASE "callback requires the mysql57 run-scoped cell"
[[ -n "${V934_DB_CONTAINER:-}" ]] || fail E_PROBE_CONTEXT "container is missing"
[[ -n "${V934_DB_CELL_ROOT:-}" ]] || fail E_PROBE_CONTEXT "cell root is missing"

case "${V934_DB_STATE_PROBE:-}:$MODE" in
  unavailable:noop|forced-cleanup-failure:noop|fixture-mutation:mutate-fixture|\
signal-int:wait-signal|signal-term:wait-signal|signal-hup:wait-signal)
    ;;
  *)
    fail E_PROBE_MODE "probe/callback mode pair differs: ${V934_DB_STATE_PROBE:-<empty>}:$MODE"
    ;;
esac

case "$MODE" in
  noop)
    ;;
  mutate-fixture)
    docker exec -e MYSQL_PWD=foggy_test_123 "$V934_DB_CONTAINER" \
      mysql --batch --raw --skip-column-names -ufoggy foggy_test \
      -e "UPDATE v934_test_sentinel SET sentinel_value='9.3.4-mutated' WHERE sentinel_key='contract_version';"
    ;;
  wait-signal)
    READY_FILE="$V934_DB_CELL_ROOT/probe-ready.env"
    READY_TEMP="$READY_FILE.$$.$RANDOM.tmp"
    trap 'exit 130' INT
    trap 'exit 143' TERM
    trap 'exit 129' HUP
    printf 'pid=%s\nstatus=ready\n' "$$" > "$READY_TEMP"
    mv -f -- "$READY_TEMP" "$READY_FILE"
    while true; do
      sleep 1
    done
    ;;
  *)
    fail E_PROBE_MODE "unsupported callback mode: ${MODE:-<empty>}"
    ;;
esac
