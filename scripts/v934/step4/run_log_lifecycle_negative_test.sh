#!/usr/bin/env bash

if [[ "${V934_TEST_LOGGER_ENTRY:-}" == 1 ]]; then
  set -u
  mode="${V934_TEST_LOGGER_MODE:-pass}"
  authority_fd="${V934_AUTHORITY_LOCK_FD:-}"
  if [[ "$authority_fd" =~ ^[1-9][0-9]*$ && -e "/proc/self/fd/$authority_fd" ]]; then
    exit 91
  fi
  if [[ "$mode" == timeout ]]; then
    trap '' TERM
  fi
  /usr/bin/tee "$@"
  tee_code=$?
  case "$mode" in
    pass) ;;
    slow) sleep 0.20 ;;
    nonzero) exit 7 ;;
    timeout)
      while :; do :; done
      ;;
    *) exit 92 ;;
  esac
  exit "$tee_code"
fi

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd -P)"
SELF="$ROOT_DIR/scripts/v934/step4/run_log_lifecycle_negative_test.sh"
LIB="$ROOT_DIR/scripts/v934/step4/run_log_lifecycle_lib.sh"

fail() {
  printf '[v934-run-log-test] FAIL: %s\n' "$*" >&2
  exit 1
}

if [[ "${1:-}" == --exit-case ]]; then
  [[ "$#" -eq 7 ]] || exit 96
  run_root="$2"
  logger_mode="$3"
  requested_exit="$4"
  marker="$5"
  expected_callback="$6"
  payload="$7"
  # shellcheck source=scripts/v934/step4/run_log_lifecycle_lib.sh
  source "$LIB"
  export V934_TEST_LOGGER_ENTRY=1
  export V934_TEST_LOGGER_MODE="$logger_mode"
  v934_run_log_open "$run_root" exit-case "$SELF"
  owned_logger_pid="$V934_RUN_LOG_LOGGER_PID"
  record_status() {
    local recorded_exit="$1"
    if kill -0 "$owned_logger_pid" >/dev/null 2>&1; then
      exit 97
    fi
    printf '%s\n' "$recorded_exit" > "$marker"
    [[ "$recorded_exit" -eq "$expected_callback" ]] || exit 98
    exit "$recorded_exit"
  }
  trap 'v934_run_log_exit_trap "$?" record_status' EXIT
  printf '%s\n' "$payload"
  exit "$requested_exit"
fi

if [[ "${1:-}" == --persistent-child ]]; then
  [[ "$#" -eq 2 ]] || exit 96
  run_root="$2"
  # shellcheck source=scripts/v934/step4/run_log_lifecycle_lib.sh
  source "$LIB"
  export V934_TEST_LOGGER_ENTRY=1
  export V934_TEST_LOGGER_MODE=pass
  v934_run_log_open "$run_root" persistent-child "$SELF"
  echo persistent-child-log
  v934_run_log_close
  sleep 30 >/dev/null 2>&1 &
  exit 0
fi

if [[ "${1:-}" == --clean-child ]]; then
  [[ "$#" -eq 2 ]] || exit 96
  run_root="$2"
  # shellcheck source=scripts/v934/step4/run_log_lifecycle_lib.sh
  source "$LIB"
  export V934_TEST_LOGGER_ENTRY=1
  export V934_TEST_LOGGER_MODE=slow
  v934_run_log_open "$run_root" clean-child "$SELF"
  echo clean-child-log
  v934_run_log_close
  exit 0
fi

if [[ "${1:-}" == --signal-open-case ]]; then
  [[ "$#" -eq 8 ]] || exit 96
  run_root="$2"
  ready_fd="$3"
  release_fd="$4"
  authority_closed_fd="$5"
  authority_path="$6"
  expected_signal="$7"
  expected_code="$8"
  # shellcheck source=scripts/v934/step4/run_log_lifecycle_lib.sh
  source "$LIB"
  exec 19> "$authority_path"
  flock -n 19 || exit 94
  V934_AUTHORITY_LOCK_FD=19
  export V934_AUTHORITY_LOCK_FD
  export V934_TEST_LOGGER_ENTRY=1
  export V934_TEST_LOGGER_MODE=pass
  open_case_parent_pid="$BASHPID"
  original_close_definition="$(declare -f v934_run_log_close_fd_number)"
  original_capture_definition="$(declare -f v934_run_log_capture_logger_identity)"
  eval "${original_close_definition/v934_run_log_close_fd_number/v934_run_log_close_fd_number_original}"
  eval "${original_capture_definition/v934_run_log_capture_logger_identity/v934_run_log_capture_logger_identity_original}"
  v934_run_log_close_fd_number() {
    local descriptor="$1" code=0
    v934_run_log_close_fd_number_original "$descriptor" || code=$?
    if [[ "$code" -eq 0 && "$descriptor" == 19 && "$BASHPID" != "$open_case_parent_pid" ]]; then
      printf '%s\n' "$BASHPID" >&"$authority_closed_fd" || return 1
    fi
    return "$code"
  }
  v934_run_log_capture_logger_identity() {
    printf '%s\n' "$V934_RUN_LOG_LOGGER_PID" >&"$ready_fd" || return 2
    while ! IFS= read -r -t 5 -u "$release_fd" release; do
      [[ -n "$V934_RUN_LOG_PENDING_SIGNAL_CODE" ]] || return 2
    done
    [[ "$release" == release ]] || return 2
    v934_run_log_capture_logger_identity_original "$@"
  }
  v934_run_log_open "$run_root" "signal-$expected_signal" "$SELF"
  # A queued signal must be drained inside open(), never reach this line.
  exit "$expected_code"
fi

if [[ "${1:-}" == --capture-failure-case ]]; then
  [[ "$#" -eq 7 ]] || exit 96
  run_root="$2"
  ready_fd="$3"
  release_fd="$4"
  authority_closed_fd="$5"
  authority_path="$6"
  marker="$7"
  # shellcheck source=scripts/v934/step4/run_log_lifecycle_lib.sh
  source "$LIB"
  exec 19> "$authority_path"
  flock -n 19 || exit 94
  V934_AUTHORITY_LOCK_FD=19
  export V934_AUTHORITY_LOCK_FD
  export V934_TEST_LOGGER_ENTRY=1
  export V934_TEST_LOGGER_MODE=pass
  open_case_parent_pid="$BASHPID"
  original_close_definition="$(declare -f v934_run_log_close_fd_number)"
  eval "${original_close_definition/v934_run_log_close_fd_number/v934_run_log_close_fd_number_original}"
  v934_run_log_close_fd_number() {
    local descriptor="$1" code=0
    v934_run_log_close_fd_number_original "$descriptor" || code=$?
    if [[ "$code" -eq 0 && "$descriptor" == 19 && "$BASHPID" != "$open_case_parent_pid" ]]; then
      printf '%s\n' "$BASHPID" >&"$authority_closed_fd" || return 1
    fi
    return "$code"
  }
  v934_run_log_capture_logger_identity() {
    printf '%s\n' "$V934_RUN_LOG_LOGGER_PID" >&"$ready_fd" || return 2
    IFS= read -r -t 5 -u "$release_fd" release || return 2
    [[ "$release" == release ]] || return 2
    return 2
  }
  set +e
  v934_run_log_open "$run_root" capture-failure "$SELF"
  open_code=$?
  set -e
  [[ "$open_code" -eq 1 && "$V934_RUN_LOG_STATE" == closed ]] || exit 95
  printf 'capture-failure-reaped\n' > "$marker"
  exit 0
fi

[[ "$#" -eq 0 ]] || fail "usage: $SELF"
[[ -f "$LIB" && ! -L "$LIB" ]] || fail "missing lifecycle library"
[[ -x "$SELF" && ! -L "$SELF" ]] || fail "test helper must be a real executable"
[[ -x /usr/bin/tee ]] || fail "missing /usr/bin/tee"
for command_name in bash date kill mkfifo setsid sleep; do
  command -v "$command_name" >/dev/null 2>&1 || fail "missing command: $command_name"
done

TMP_ROOT="$(mktemp -d)"
PERSISTENT_GROUP=""
cleanup() {
  if [[ "$PERSISTENT_GROUP" =~ ^[1-9][0-9]*$ ]]; then
    kill -TERM -- "-$PERSISTENT_GROUP" >/dev/null 2>&1 || true
    sleep 0.05
    kill -KILL -- "-$PERSISTENT_GROUP" >/dev/null 2>&1 || true
  fi
  rm -rf -- "$TMP_ROOT"
}
trap cleanup EXIT

process_starttime() {
  python3 - "$1" <<'PY'
from pathlib import Path
import sys

raw = Path(f"/proc/{int(sys.argv[1])}/stat").read_bytes()
right = raw.rindex(b")")
fields = raw[right + 2 :].split()
if len(fields) < 20:
    raise SystemExit(1)
print(int(fields[19]))
PY
}

exact_process_is_live() {
  python3 - "$1" "$2" <<'PY'
from pathlib import Path
import sys

pid = int(sys.argv[1])
expected = int(sys.argv[2])
try:
    raw = Path(f"/proc/{pid}/stat").read_bytes()
except (FileNotFoundError, ProcessLookupError):
    raise SystemExit(1)
right = raw.rindex(b")")
fields = raw[right + 2 :].split()
if len(fields) < 20:
    raise SystemExit(2)
raise SystemExit(0 if fields[0] != b"Z" and int(fields[19]) == expected else 1)
PY
}

run_signal_open_barrier_case() {
  local signal_name="$1" expected_code="$2" label="${signal_name,,}"
  local root="$TMP_ROOT/signal-$label" ready="$TMP_ROOT/signal-$label.ready"
  local release="$TMP_ROOT/signal-$label.release"
  local authority_closed="$TMP_ROOT/signal-$label.authority-closed"
  local authority="$TMP_ROOT/signal-$label.lock"
  local ready_fd release_fd authority_closed_fd case_pid logger_pid logger_starttime
  local closed_pid case_code
  mkdir -p "$root"
  mkfifo -- "$ready" "$release" "$authority_closed"
  exec {ready_fd}<> "$ready"
  exec {release_fd}<> "$release"
  exec {authority_closed_fd}<> "$authority_closed"
  bash "$SELF" --signal-open-case \
    "$root" "$ready_fd" "$release_fd" "$authority_closed_fd" \
    "$authority" "$signal_name" "$expected_code" &
  case_pid=$!
  IFS= read -r -t 5 -u "$ready_fd" logger_pid || fail "$signal_name spawn barrier timed out"
  IFS= read -r -t 5 -u "$authority_closed_fd" closed_pid || \
    fail "$signal_name authority-close barrier timed out"
  [[ "$logger_pid" =~ ^[1-9][0-9]*$ && "$closed_pid" == "$logger_pid" ]] || \
    fail "$signal_name barrier identities differ"
  logger_starttime="$(process_starttime "$logger_pid")" || fail "$signal_name logger identity vanished before signal"
  kill -s "$signal_name" "$case_pid" || fail "cannot deliver early $signal_name"
  printf 'release\n' >&"$release_fd"
  set +e
  wait "$case_pid"
  case_code=$?
  set -e
  [[ "$case_code" -eq "$expected_code" ]] || \
    fail "early $signal_name changed exit code: expected=$expected_code actual=$case_code"
  ! exact_process_is_live "$logger_pid" "$logger_starttime" || \
    fail "early $signal_name left the exact logger alive"
  flock -n "$authority" -c : || fail "early $signal_name left the authority lock held"
  exec {ready_fd}>&-
  exec {release_fd}>&-
  exec {authority_closed_fd}>&-
}

PASS_ROOT="$TMP_ROOT/pass"
mkdir -p "$PASS_ROOT"
(
  # shellcheck source=scripts/v934/step4/run_log_lifecycle_lib.sh
  source "$LIB"
  exec 19> "$TMP_ROOT/authority.lock"
  V934_AUTHORITY_LOCK_FD=19
  export V934_AUTHORITY_LOCK_FD
  export V934_TEST_LOGGER_ENTRY=1
  export V934_TEST_LOGGER_MODE=slow
  v934_run_log_open "$PASS_ROOT" pass "$SELF"
  logger_pid="$V934_RUN_LOG_LOGGER_PID"
  echo slow-flush-payload
  started_ms="$(date +%s%3N)"
  v934_run_log_close
  finished_ms="$(date +%s%3N)"
  elapsed_ms=$((finished_ms - started_ms))
  [[ "$elapsed_ms" -ge 100 && "$elapsed_ms" -lt 5000 ]] || exit 101
  ! kill -0 "$logger_pid" >/dev/null 2>&1 || exit 102
  grep -Fx 'slow-flush-payload' "$PASS_ROOT/run.log" >/dev/null || exit 103
  exec 19>&-
) || fail "slow logger was not flushed, reaped, or stripped of the authority FD"

NONZERO_ROOT="$TMP_ROOT/nonzero"
mkdir -p "$NONZERO_ROOT"
(
  # shellcheck source=scripts/v934/step4/run_log_lifecycle_lib.sh
  source "$LIB"
  export V934_TEST_LOGGER_ENTRY=1
  export V934_TEST_LOGGER_MODE=nonzero
  v934_run_log_open "$NONZERO_ROOT" nonzero "$SELF"
  echo nonzero-payload
  set +e
  v934_run_log_close
  close_code=$?
  set -e
  [[ "$close_code" -eq 1 ]] || exit 104
  grep -Fx 'nonzero-payload' "$NONZERO_ROOT/run.log" >/dev/null || exit 105
) || fail "non-zero logger did not fail closed"

TIMEOUT_ROOT="$TMP_ROOT/timeout"
mkdir -p "$TIMEOUT_ROOT"
(
  # shellcheck source=scripts/v934/step4/run_log_lifecycle_lib.sh
  source "$LIB"
  V934_RUN_LOG_WAIT_ATTEMPTS=2
  V934_RUN_LOG_WAIT_INTERVAL=0.02
  V934_RUN_LOG_TERM_ATTEMPTS=2
  V934_RUN_LOG_TERM_INTERVAL=0.02
  export V934_TEST_LOGGER_ENTRY=1
  export V934_TEST_LOGGER_MODE=timeout
  v934_run_log_open "$TIMEOUT_ROOT" timeout "$SELF"
  logger_pid="$V934_RUN_LOG_LOGGER_PID"
  echo timeout-payload
  started_ms="$(date +%s%3N)"
  set +e
  v934_run_log_close
  close_code=$?
  set -e
  finished_ms="$(date +%s%3N)"
  [[ "$close_code" -eq 124 ]] || exit 106
  [[ $((finished_ms - started_ms)) -lt 2000 ]] || exit 107
  ! kill -0 "$logger_pid" >/dev/null 2>&1 || exit 108
) || fail "logger timeout was not bounded through TERM/KILL"

PID_REUSE_ROOT="$TMP_ROOT/pid-reuse"
mkdir -p "$PID_REUSE_ROOT"
(
  # shellcheck source=scripts/v934/step4/run_log_lifecycle_lib.sh
  source "$LIB"
  export V934_TEST_LOGGER_ENTRY=1
  export V934_TEST_LOGGER_MODE=slow
  v934_run_log_open "$PID_REUSE_ROOT" pid-reuse "$SELF"
  echo pid-reuse-payload
  sealed_starttime="$V934_RUN_LOG_LOGGER_STARTTIME_TICKS"
  V934_RUN_LOG_LOGGER_STARTTIME_TICKS=$((sealed_starttime + 1))
  set +e
  v934_run_log_signal_logger TERM
  signal_code=$?
  set -e
  [[ "$signal_code" -eq 2 ]] || exit 109
  V934_RUN_LOG_LOGGER_STARTTIME_TICKS="$sealed_starttime"
  v934_run_log_logger_state || exit 110
  v934_run_log_close || exit 111
) || fail "logger PID-reuse conflict did not refuse the signal and preserve the exact process"

for signal_case in 'INT 130' 'TERM 143' 'HUP 129'; do
  read -r signal_name signal_code <<< "$signal_case"
  run_signal_open_barrier_case "$signal_name" "$signal_code"
done

CAPTURE_FAILURE_ROOT="$TMP_ROOT/capture-failure"
CAPTURE_FAILURE_READY="$TMP_ROOT/capture-failure.ready"
CAPTURE_FAILURE_RELEASE="$TMP_ROOT/capture-failure.release"
CAPTURE_FAILURE_AUTHORITY_CLOSED="$TMP_ROOT/capture-failure.authority-closed"
CAPTURE_FAILURE_AUTHORITY="$TMP_ROOT/capture-failure.lock"
CAPTURE_FAILURE_MARKER="$TMP_ROOT/capture-failure.marker"
mkdir -p "$CAPTURE_FAILURE_ROOT"
mkfifo -- "$CAPTURE_FAILURE_READY" "$CAPTURE_FAILURE_RELEASE" "$CAPTURE_FAILURE_AUTHORITY_CLOSED"
exec {capture_ready_fd}<> "$CAPTURE_FAILURE_READY"
exec {capture_release_fd}<> "$CAPTURE_FAILURE_RELEASE"
exec {capture_authority_closed_fd}<> "$CAPTURE_FAILURE_AUTHORITY_CLOSED"
bash "$SELF" --capture-failure-case \
  "$CAPTURE_FAILURE_ROOT" "$capture_ready_fd" "$capture_release_fd" \
  "$capture_authority_closed_fd" "$CAPTURE_FAILURE_AUTHORITY" \
  "$CAPTURE_FAILURE_MARKER" &
capture_case_pid=$!
IFS= read -r -t 5 -u "$capture_ready_fd" capture_logger_pid || \
  fail "capture-failure spawn barrier timed out"
IFS= read -r -t 5 -u "$capture_authority_closed_fd" capture_closed_pid || \
  fail "capture-failure authority-close barrier timed out"
[[ "$capture_logger_pid" == "$capture_closed_pid" ]] || fail "capture-failure barrier identities differ"
capture_logger_starttime="$(process_starttime "$capture_logger_pid")" || \
  fail "capture-failure logger identity vanished before release"
printf 'release\n' >&"$capture_release_fd"
wait "$capture_case_pid" || fail "capture-failure case did not fail open() safely"
[[ -f "$CAPTURE_FAILURE_MARKER" ]] || fail "capture-failure case did not reach bounded reap"
! exact_process_is_live "$capture_logger_pid" "$capture_logger_starttime" || \
  fail "capture-failure left the exact logger alive"
flock -n "$CAPTURE_FAILURE_AUTHORITY" -c : || fail "capture-failure left the authority lock held"
exec {capture_ready_fd}>&-
exec {capture_release_fd}>&-
exec {capture_authority_closed_fd}>&-

EXIT_FAILURE_ROOT="$TMP_ROOT/exit-failure"
EXIT_FAILURE_MARKER="$TMP_ROOT/exit-failure.status"
mkdir -p "$EXIT_FAILURE_ROOT"
set +e
bash "$SELF" --exit-case \
  "$EXIT_FAILURE_ROOT" slow 23 "$EXIT_FAILURE_MARKER" 23 exit-failure-payload
exit_failure_code=$?
set -e
[[ "$exit_failure_code" -eq 23 ]] || fail "failure EXIT path changed the original exit code"
[[ "$(cat "$EXIT_FAILURE_MARKER")" == 23 ]] || fail "failure status callback ran before logger reap"
grep -Fx 'exit-failure-payload' "$EXIT_FAILURE_ROOT/run.log" >/dev/null || \
  fail "failure EXIT path lost buffered log output"

EXIT_LOGGER_ROOT="$TMP_ROOT/exit-logger"
EXIT_LOGGER_MARKER="$TMP_ROOT/exit-logger.status"
mkdir -p "$EXIT_LOGGER_ROOT"
set +e
bash "$SELF" --exit-case \
  "$EXIT_LOGGER_ROOT" nonzero 0 "$EXIT_LOGGER_MARKER" 1 exit-logger-payload
exit_logger_code=$?
set -e
[[ "$exit_logger_code" -eq 1 ]] || fail "non-zero logger did not turn a green EXIT into failure"
[[ "$(cat "$EXIT_LOGGER_MARKER")" == 1 ]] || fail "logger failure was not propagated to status callback"

CLEAN_CHILD_ROOT="$TMP_ROOT/clean-child"
mkdir -p "$CLEAN_CHILD_ROOT"
setsid bash "$SELF" --clean-child "$CLEAN_CHILD_ROOT" &
clean_child_group=$!
for _attempt in $(seq 1 100); do
  clean_child_pgid="$(ps -o pgid= -p "$clean_child_group" 2>/dev/null | tr -d '[:space:]')"
  [[ "$clean_child_pgid" == "$clean_child_group" ]] && break
  sleep 0.01
done
[[ "${clean_child_pgid:-}" == "$clean_child_group" ]] || fail "clean probe did not establish a process group"
wait "$clean_child_group" || fail "clean probe leader failed"
! kill -0 -- "-$clean_child_group" >/dev/null 2>&1 || \
  fail "owned logger outlived a successful child leader"

PERSISTENT_ROOT="$TMP_ROOT/persistent"
mkdir -p "$PERSISTENT_ROOT"
setsid bash "$SELF" --persistent-child "$PERSISTENT_ROOT" &
PERSISTENT_GROUP=$!
for _attempt in $(seq 1 100); do
  child_pgid="$(ps -o pgid= -p "$PERSISTENT_GROUP" 2>/dev/null | tr -d '[:space:]')"
  [[ "$child_pgid" == "$PERSISTENT_GROUP" ]] && break
  sleep 0.01
done
[[ "${child_pgid:-}" == "$PERSISTENT_GROUP" ]] || fail "persistent probe did not establish a process group"
wait "$PERSISTENT_GROUP" || fail "persistent probe leader failed"
kill -0 -- "-$PERSISTENT_GROUP" >/dev/null 2>&1 || \
  fail "real process-group residue was hidden by logger cleanup"
kill -TERM -- "-$PERSISTENT_GROUP" >/dev/null 2>&1 || true
for _attempt in $(seq 1 100); do
  kill -0 -- "-$PERSISTENT_GROUP" >/dev/null 2>&1 || break
  sleep 0.01
done
kill -0 -- "-$PERSISTENT_GROUP" >/dev/null 2>&1 && \
  kill -KILL -- "-$PERSISTENT_GROUP" >/dev/null 2>&1 || true
PERSISTENT_GROUP=""

python3 - \
  "$ROOT_DIR/scripts/verify-v934-unit.sh" \
  "$ROOT_DIR/scripts/verify-v934-integration.sh" \
  "$ROOT_DIR/scripts/verify-v934-step4-coverage.sh" "$LIB" <<'PY'
from pathlib import Path
import sys

for text_path in sys.argv[1:3]:
    path = Path(text_path)
    text = path.read_text(encoding="utf-8")
    if "exec > >(tee" in text:
        raise SystemExit(f"{path}: unowned process-substitution logger remains")
    required = [
        'source "$RUN_LOG_LIB"',
        "v934_install_run_status_traps",
        'v934_run_log_exit_trap "$?" v934_record_run_status',
        'v934_run_log_open "$RUN_ROOT"',
        'v934_run_log_close || fail',
        'PHASE="completed"',
        "v934_write_run_status 0",
    ]
    positions = []
    for token in required:
        position = text.find(token)
        if position < 0:
            raise SystemExit(f"{path}: missing lifecycle token {token!r}")
        positions.append(position)
    if positions != sorted(positions) or len(set(positions)) != len(positions):
        raise SystemExit(f"{path}: logger flush is not ordered before green evidence")

outer_path = Path(sys.argv[3])
outer = outer_path.read_text(encoding="utf-8")
outer_required = [
    'v934_run_log_begin_signal_critical || fail',
    'v934_run_log_close_fd_number "$V934_AUTHORITY_LOCK_FD"',
    'exec 0< "$RUN_LOG_FIFO"',
    'RUN_LOG_TEE_PID=$!',
    'v934_run_log_capture_logger_identity "$BASHPID"',
    'v934_run_log_release_unsealed_fifo_logger',
    'CHILD_SIGNAL_CRITICAL=true',
    'ACTIVE_CHILD_PID=$!',
    'capture_active_child_identity',
    'ACTIVE_CHILD_GROUP_ESTABLISHED=true',
    'CHILD_SIGNAL_CRITICAL=false',
    'confirm_active_child_group_absent',
]
for token in outer_required:
    if token not in outer:
        raise SystemExit(f"{outer_path}: missing critical lifecycle token {token!r}")
if outer.index('v934_run_log_close_fd_number "$V934_AUTHORITY_LOCK_FD"') > outer.index('exec 0< "$RUN_LOG_FIFO"'):
    raise SystemExit(f"{outer_path}: authority FD is closed after the FIFO open")
if 'kill -TERM "$RUN_LOG_TEE_PID"' in outer or 'kill -KILL "$RUN_LOG_TEE_PID"' in outer:
    raise SystemExit(f"{outer_path}: naked logger PID signal remains")
child_spawn = outer.index('python3 "$COVERAGE_TOOL" launch-child')
group_established = outer.index('ACTIVE_CHILD_GROUP_ESTABLISHED=true', child_spawn)
if not (
    outer.rfind('CHILD_SIGNAL_CRITICAL=true', 0, child_spawn) >= 0
    and outer.index('ACTIVE_CHILD_PID=$!', child_spawn) < outer.index('capture_active_child_identity', child_spawn)
    and group_established < outer.index('CHILD_SIGNAL_CRITICAL=false', group_established)
):
    raise SystemExit(f"{outer_path}: child spawn/ready critical-section order differs")

library_path = Path(sys.argv[4])
library = library_path.read_text(encoding="utf-8")
logger_spawn = library.index("  (\n    trap - INT TERM HUP PIPE")
close_index = library.index('v934_run_log_close_fd_number "$authority_fd"', logger_spawn)
fifo_index = library.index('exec 0< "$V934_RUN_LOG_FIFO"', logger_spawn)
if close_index > fifo_index:
    raise SystemExit(f"{library_path}: authority FD is closed after the FIFO open")
for forbidden in ('kill -TERM "$logger_pid"', 'kill -KILL "$logger_pid"'):
    if forbidden in library:
        raise SystemExit(f"{library_path}: naked logger PID signal remains")
if library.count("v934_run_log_abort_open || true") != 3:
    raise SystemExit(
        f"{library_path}: abort-open failure can escape before signal-trap restoration"
    )
PY

printf '[v934-run-log-test] PASS slow=2 nonzero=2 timeout=1 pid-reuse=1 early-signal=3 capture-failure=1 exit=2 clean-group=1 persistent-residue=1\n'
