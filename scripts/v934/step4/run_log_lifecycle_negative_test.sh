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
import hashlib
import re
import shlex
import sys


class ShapeError(RuntimeError):
    pass


UNIT_RUNNER_SHA256 = "45536c0a969731f6b7c87acecdb225b13a8a0fca45a9a04c9cdfb2173fc60c66"
INTEGRATION_RUNNER_SHA256 = "19d5a9e8d58a554f416e269a35666e439b5f611f44a50783482613316cb33639"


def require_source_seal(path: Path, source_bytes: bytes, expected_sha256: str) -> None:
    observed_sha256 = hashlib.sha256(source_bytes).hexdigest()
    if observed_sha256 != expected_sha256:
        raise ShapeError(
            f"{path}: runner source seal differs: expected={expected_sha256} "
            f"observed={observed_sha256}"
        )


def scan_shell_physical(
    line: str, initial_quote: str
) -> tuple[str, str, bool]:
    quote = initial_quote
    index = 0
    comment_index: int | None = None
    continued = False
    while index < len(line):
        character = line[index]
        if quote == "'":
            if character == "'":
                quote = ""
            index += 1
            continue
        if quote == '"':
            if character == "\\":
                if index + 1 == len(line):
                    continued = True
                    break
                index += 2
            else:
                if character == '"':
                    quote = ""
                index += 1
            continue
        if character == "\\":
            if index + 1 == len(line):
                continued = True
                break
            index += 2
            continue
        if character in {"'", '"'}:
            quote = character
            index += 1
            continue
        if character == "#" and (
            index == 0 or line[index - 1] in " \t;|&()"
        ):
            comment_index = index
            break
        index += 1

    executable = line[:comment_index] if comment_index is not None else line
    if comment_index is not None:
        continued = False
    return executable, quote, continued


def heredoc_declarations(path: Path, line: str, line_number: int) -> list[tuple[str, bool]]:
    declarations: list[tuple[str, bool]] = []
    index = 0
    quote = ""
    while index < len(line):
        character = line[index]
        if quote == "'":
            if character == "'":
                quote = ""
            index += 1
            continue
        if quote == '"':
            if character == "\\":
                index += 2
            else:
                if character == '"':
                    quote = ""
                index += 1
            continue
        if character == "\\":
            index += 2
            continue
        if character in {"'", '"'}:
            quote = character
            index += 1
            continue
        if line.startswith("<<<", index):
            index += 3
            continue
        if not line.startswith("<<", index):
            index += 1
            continue

        index += 2
        strip_tabs = index < len(line) and line[index] == "-"
        if strip_tabs:
            index += 1
        while index < len(line) and line[index].isspace():
            index += 1
        if index >= len(line):
            raise ShapeError(f"{path}:{line_number}: missing heredoc delimiter")

        delimiter = ""
        if line[index] in {"'", '"'}:
            delimiter_quote = line[index]
            index += 1
            while index < len(line) and line[index] != delimiter_quote:
                if delimiter_quote == '"' and line[index] == "\\":
                    index += 1
                    if index >= len(line):
                        break
                delimiter += line[index]
                index += 1
            if index >= len(line) or line[index] != delimiter_quote:
                raise ShapeError(f"{path}:{line_number}: unterminated heredoc delimiter")
            index += 1
        else:
            while index < len(line) and line[index] not in " \t;|&()<>\r\n":
                if line[index] == "\\":
                    index += 1
                    if index >= len(line):
                        break
                delimiter += line[index]
                index += 1
        if not delimiter:
            raise ShapeError(f"{path}:{line_number}: empty heredoc delimiter")
        declarations.append((delimiter, strip_tabs))
    return declarations


def executable_streams(path: Path, text: str) -> tuple[list[str], list[str]]:
    """Return executable physical and logical shell lines, excluding heredoc bodies."""
    physical: list[str] = []
    logical: list[str] = []
    logical_buffer = ""
    heredocs: list[tuple[str, bool]] = []
    quote = ""

    for line_number, raw_line in enumerate(text.splitlines(), start=1):
        if heredocs:
            delimiter, strip_tabs = heredocs[0]
            candidate = raw_line.lstrip("\t") if strip_tabs else raw_line
            if candidate == delimiter:
                heredocs.pop(0)
            continue

        initial_quote = quote
        executable_line, quote, continued = scan_shell_physical(raw_line, quote)
        stripped = executable_line.strip()
        if stripped and not initial_quote:
            physical.append(stripped)

        right_stripped = executable_line.rstrip()
        fragment = right_stripped[:-1] if continued else executable_line
        logical_buffer += fragment
        if continued:
            continue
        if quote:
            logical_buffer += "\n"
            continue

        logical_line = logical_buffer.strip()
        logical_buffer = ""
        if logical_line and not logical_line.startswith("#"):
            logical.append(logical_line)

        heredocs.extend(heredoc_declarations(path, logical_line, line_number))

    if heredocs:
        raise ShapeError(f"{path}: unterminated heredoc; refusing open")
    if quote:
        raise ShapeError(f"{path}: unterminated shell quote; refusing open")
    if logical_buffer:
        raise ShapeError(f"{path}: unterminated line continuation; refusing open")
    return physical, logical


def require_exact_ordered(path: Path, lines: list[str], required: list[str]) -> None:
    positions: list[int] = []
    for command in required:
        matches = [index for index, line in enumerate(lines) if line == command]
        if len(matches) != 1:
            raise ShapeError(
                f"{path}: lifecycle command {command!r} count differs: {len(matches)}"
            )
        positions.append(matches[0])
    if positions != sorted(positions) or len(set(positions)) != len(positions):
        raise ShapeError(f"{path}: logger/cleanup order differs from the reviewed lifecycle")


def require_exact_slice(
    path: Path, lines: list[str], expected: list[str], label: str
) -> None:
    starts = [index for index, line in enumerate(lines) if line == expected[0]]
    if len(starts) != 1:
        raise ShapeError(f"{path}: {label} start count differs: {len(starts)}")
    start = starts[0]
    observed = lines[start : start + len(expected)]
    if observed != expected:
        raise ShapeError(
            f"{path}: {label} executable slice differs: expected={expected!r} "
            f"observed={observed!r}"
        )


def reject_unowned_logger(path: Path, text: str) -> None:
    if "exec > >(tee" in text:
        raise ShapeError(f"{path}: unowned process-substitution logger remains")


def require_exact_traps(path: Path, logical_lines: list[str], expected: list[str]) -> None:
    observed: list[str] = []
    for line in logical_lines:
        candidate = line.replace("''", "").replace('\"\"', "")
        candidate = re.sub(r"\\([A-Za-z])", r"\1", candidate)
        if re.search(r"(^|[^A-Za-z0-9_])trap([^A-Za-z0-9_]|$)", candidate) is None:
            continue
        try:
            lexer = shlex.shlex(line, posix=True, punctuation_chars=";&|()")
            lexer.whitespace_split = True
            lexer.commenters = "#"
            tokens = list(lexer)
        except ValueError as error:
            raise ShapeError(f"{path}: cannot parse shell line {line!r}: {error}") from error
        # Inspect every shell word, so `trap : 0`, `builtin trap`, `command trap`,
        # escaped spellings, and chained overrides are all treated as trap commands.
        if "trap" not in tokens:
            raise ShapeError(f"{path}: indirect or unsupported trap reference {line!r}")
        observed.append(line)
    if observed != expected:
        raise ShapeError(
            f"{path}: trap commands differ: expected={expected!r} observed={observed!r}"
        )


def require_canonical_references(
    path: Path, logical_lines: list[str], expected: dict[str, list[str]]
) -> None:
    for name, canonical_lines in expected.items():
        pattern = re.compile(
            rf"(?<![A-Za-z0-9_]){re.escape(name)}(?![A-Za-z0-9_])"
        )
        observed = []
        for line in logical_lines:
            reference_view = line.replace("'", "").replace('"', "")
            reference_view = re.sub(r"\\([A-Za-z])", r"\1", reference_view)
            if pattern.search(reference_view):
                observed.append(line)
        if observed != canonical_lines:
            raise ShapeError(
                f"{path}: {name} references differ: expected={canonical_lines!r} "
                f"observed={observed!r}"
            )


def validate_integration(
    path: Path,
    text: str,
    enforce_source_seal: bool = True,
    source_bytes: bytes | None = None,
) -> None:
    if enforce_source_seal:
        require_source_seal(
            path,
            text.encode("utf-8") if source_bytes is None else source_bytes,
            INTEGRATION_RUNNER_SHA256,
        )
    reject_unowned_logger(path, text)
    physical, logical = executable_streams(path, text)
    require_exact_traps(
        path,
        logical,
        ['trap \'v934_run_log_exit_trap "$?" v934_record_run_status\' EXIT'],
    )
    require_canonical_references(
        path,
        logical,
        {
            "v934_install_run_status_traps": ["v934_install_run_status_traps"],
            "v934_disarm_run_status_traps": ["v934_disarm_run_status_traps"],
            "v934_run_log_open": [
                'v934_run_log_open "$RUN_ROOT" v934-integration || fail "cannot open the owned run logger"'
            ],
            "v934_run_log_close": [
                'v934_run_log_close || fail "owned run logger did not flush and exit cleanly"'
            ],
            "v934_write_run_status": ["v934_write_run_status 0"],
            "v934_run_log_exit_trap": [
                'trap \'v934_run_log_exit_trap "$?" v934_record_run_status\' EXIT'
            ],
        },
    )
    require_exact_ordered(
        path,
        physical,
        [
            'source "$RUN_LOG_LIB"',
            "v934_install_run_status_traps",
            'trap \'v934_run_log_exit_trap "$?" v934_record_run_status\' EXIT',
            'v934_run_log_open "$RUN_ROOT" v934-integration || fail "cannot open the owned run logger"',
            'v934_run_log_close || fail "owned run logger did not flush and exit cleanly"',
            'PHASE="completed"',
            "v934_write_run_status 0",
            '"$RUN_ROOT/final/report-manifest.json" "$RUN_ROOT/summary.env" "$RUN_ID" \\',
            "v934_disarm_run_status_traps",
            'echo "[v934-integration] PASS run=$RUN_ID evidence=$RUN_ROOT"',
        ],
    )
    require_exact_slice(
        path,
        physical,
        [
            'PHASE="run-log-flush"',
            'echo "[v934-integration] evidence prepared; flushing owned run logger"',
            'v934_run_log_close || fail "owned run logger did not flush and exit cleanly"',
            'PHASE="completed"',
            "v934_write_run_status 0",
        ],
        "Integration logger-flush/green",
    )
    if physical[-2:] != [
        "v934_disarm_run_status_traps",
        'echo "[v934-integration] PASS run=$RUN_ID evidence=$RUN_ROOT"',
    ]:
        raise ShapeError(f"{path}: Integration disarm/PASS suffix differs")


def validate_unit(
    path: Path,
    text: str,
    enforce_source_seal: bool = True,
    source_bytes: bytes | None = None,
) -> None:
    if enforce_source_seal:
        require_source_seal(
            path,
            text.encode("utf-8") if source_bytes is None else source_bytes,
            UNIT_RUNNER_SHA256,
        )
    reject_unowned_logger(path, text)
    physical, logical = executable_streams(path, text)
    require_exact_traps(
        path,
        logical,
        [
            "trap '' INT TERM HUP",
            "trap - EXIT",
            'trap \'v934_unit_exit_trap "$?"\' EXIT',
        ],
    )
    require_canonical_references(
        path,
        logical,
        {
            "v934_install_run_status_traps": ["v934_install_run_status_traps"],
            "v934_disarm_run_status_traps": ["v934_disarm_run_status_traps"],
            "v934_run_log_open": [
                'v934_run_log_open "$RUN_ROOT" v934-unit || fail "cannot open the owned run logger"'
            ],
            "v934_run_log_close": [
                'v934_run_log_close || fail "owned run logger did not flush and exit cleanly"'
            ],
            "v934_write_run_status": ["v934_write_run_status 0"],
            "v934_unit_exit_trap": [
                "v934_unit_exit_trap() {",
                'trap \'v934_unit_exit_trap "$?"\' EXIT',
            ],
            "v934_run_log_exit_trap": [
                'v934_run_log_exit_trap "$exit_code" v934_record_run_status'
            ],
        },
    )
    # Unit owns an additional MySQL fixture lifecycle.  Its EXIT trap must
    # clean both fixture scopes before delegating the preserved exit code to
    # the shared run-log/status finalizer; accepting the Integration shortcut
    # here would reintroduce the cleanup gap this wrapper was added to close.
    require_exact_ordered(
        path,
        physical,
        [
            'source "$RUN_LOG_LIB"',
            "v934_unit_exit_trap() {",
            "trap '' INT TERM HUP",
            "trap - EXIT",
            'python3 "$UNIT_FIXTURE_TOOL" cleanup-lifecycle --repo-root "$ROOT_DIR" --run-id "$RUN_ID"',
            'python3 "$UNIT_FIXTURE_TOOL" cleanup --repo-root "$ROOT_DIR" --run-id "$RUN_ID"',
            'v934_run_log_exit_trap "$exit_code" v934_record_run_status',
            "v934_install_run_status_traps",
            'trap \'v934_unit_exit_trap "$?"\' EXIT',
            'v934_run_log_open "$RUN_ROOT" v934-unit || fail "cannot open the owned run logger"',
            'v934_run_log_close || fail "owned run logger did not flush and exit cleanly"',
            'PHASE="completed"',
            "v934_write_run_status 0",
            '"$RUN_ROOT/final/report-manifest.json" "$RUN_ROOT/summary.env" "$RUN_ID" \\',
            "v934_disarm_run_status_traps",
            'echo "[v934-unit] PASS run=$RUN_ID evidence=$RUN_ROOT"',
        ],
    )
    require_exact_slice(
        path,
        physical,
        [
            "v934_unit_exit_trap() {",
            'local exit_code="${1:-1}" cleanup_code=0',
            '[[ "$exit_code" =~ ^[0-9]+$ ]] || exit_code=1',
            "trap '' INT TERM HUP",
            "trap - EXIT",
            "set +e",
            'if [[ "$LIFECYCLE_STARTED" -eq 1 ]]; then',
            'python3 "$UNIT_FIXTURE_TOOL" cleanup-lifecycle --repo-root "$ROOT_DIR" --run-id "$RUN_ID"',
            "cleanup_code=$?",
            'if [[ "$cleanup_code" -ne 0 ]]; then',
            'PHASE="unit-mysql57-lifecycle-fallback-cleanup-failed"',
            '[[ "$exit_code" -ne 0 ]] || exit_code=1',
            "fi",
            "fi",
            'if [[ -n "$FIXTURE_RUN_ID" ]]; then',
            'python3 "$UNIT_FIXTURE_TOOL" cleanup --repo-root "$ROOT_DIR" --run-id "$RUN_ID"',
            "cleanup_code=$?",
            'if [[ "$cleanup_code" -ne 0 ]]; then',
            'PHASE="unit-mysql57-fallback-cleanup-failed"',
            '[[ "$exit_code" -ne 0 ]] || exit_code=1',
            "fi",
            "fi",
            'v934_run_log_exit_trap "$exit_code" v934_record_run_status',
            "}",
        ],
        "Unit fixture-aware EXIT wrapper",
    )
    require_exact_slice(
        path,
        physical,
        [
            'PHASE="run-log-flush"',
            'echo "[v934-unit] evidence prepared; flushing owned run logger"',
            'v934_run_log_close || fail "owned run logger did not flush and exit cleanly"',
            'PHASE="completed"',
            "v934_write_run_status 0",
        ],
        "Unit logger-flush/green",
    )
    if physical[-2:] != [
        "v934_disarm_run_status_traps",
        'echo "[v934-unit] PASS run=$RUN_ID evidence=$RUN_ROOT"',
    ]:
        raise ShapeError(f"{path}: Unit disarm/PASS suffix differs")


unit_path = Path(sys.argv[1])
integration_path = Path(sys.argv[2])
unit_bytes = unit_path.read_bytes()
integration_bytes = integration_path.read_bytes()
try:
    unit_text = unit_bytes.decode("utf-8")
    integration_text = integration_bytes.decode("utf-8")
except UnicodeDecodeError as error:
    raise SystemExit(f"lifecycle runner is not exact UTF-8: {error}") from error
try:
    validate_unit(unit_path, unit_text, source_bytes=unit_bytes)
    validate_integration(
        integration_path,
        integration_text,
        source_bytes=integration_bytes,
    )
except ShapeError as error:
    raise SystemExit(str(error)) from error


def expect_unit_rejected(label: str, mutated: str) -> None:
    try:
        validate_unit(Path(f"unit-mutation:{label}"), mutated, enforce_source_seal=False)
    except ShapeError:
        return
    raise SystemExit(f"Unit lifecycle mutation unexpectedly passed: {label}")


def expect_integration_rejected(label: str, mutated: str) -> None:
    try:
        validate_integration(
            Path(f"integration-mutation:{label}"),
            mutated,
            enforce_source_seal=False,
        )
    except ShapeError:
        return
    raise SystemExit(f"Integration lifecycle mutation unexpectedly passed: {label}")


def expect_unit_seal_rejected(label: str, mutated: str) -> None:
    try:
        validate_unit(Path(f"unit-seal-mutation:{label}"), mutated)
    except ShapeError:
        return
    raise SystemExit(f"Unit source-seal mutation unexpectedly passed: {label}")


def expect_integration_seal_rejected(label: str, mutated: str) -> None:
    try:
        validate_integration(Path(f"integration-seal-mutation:{label}"), mutated)
    except ShapeError:
        return
    raise SystemExit(f"Integration source-seal mutation unexpectedly passed: {label}")


def replace_exact(source: str, old: str, new: str, label: str) -> str:
    if source.count(old) != 1:
        raise SystemExit(
            f"lifecycle mutation {label}: expected one exact anchor, found {source.count(old)}"
        )
    mutated = source.replace(old, new, 1)
    if mutated == source:
        raise SystemExit(f"lifecycle mutation {label}: source bytes did not change")
    return mutated


delegation = '  v934_run_log_exit_trap "$exit_code" v934_record_run_status'
expect_unit_rejected(
    "missing-shared-finalizer",
    replace_exact(
        unit_text,
        delegation,
        f"  # {delegation.strip()}",
        "missing-shared-finalizer",
    ),
)
unit_lifecycle_cleanup = (
    '  python3 "$UNIT_FIXTURE_TOOL" cleanup-lifecycle '
    '--repo-root "$ROOT_DIR" --run-id "$RUN_ID"'
)
expect_unit_rejected(
    "commented-fixture-cleanup-decoy",
    replace_exact(
        unit_text,
        unit_lifecycle_cleanup,
        f"  # {unit_lifecycle_cleanup.strip()}",
        "commented-fixture-cleanup-decoy",
    ),
)
expect_unit_rejected(
    "direct-trap-bypasses-fixture-cleanup",
    replace_exact(
        unit_text,
        'trap \'v934_unit_exit_trap "$?"\' EXIT',
        'trap \'v934_run_log_exit_trap "$?" v934_record_run_status\' EXIT',
        "direct-trap-bypasses-fixture-cleanup",
    ),
)
delegation_before_cleanup = replace_exact(
    unit_text,
    f"{delegation}\n",
    "",
    "delegation-before-fixture-cleanup:remove",
)
delegation_before_cleanup = replace_exact(
    delegation_before_cleanup,
    '  if [[ "$LIFECYCLE_STARTED" -eq 1 ]]; then',
    f"{delegation}\n  if [[ \"$LIFECYCLE_STARTED\" -eq 1 ]]; then",
    "delegation-before-fixture-cleanup:insert",
)
expect_unit_rejected(
    "delegation-before-fixture-cleanup",
    delegation_before_cleanup,
)
expect_unit_rejected(
    "duplicate-shared-finalizer",
    replace_exact(
        unit_text,
        delegation,
        f"{delegation}\n{delegation}",
        "duplicate-shared-finalizer",
    ),
)
unit_wrapper_trap = 'trap \'v934_unit_exit_trap "$?"\' EXIT'
expect_unit_rejected(
    "later-exit-trap-overrides-wrapper",
    replace_exact(
        unit_text,
        f"{unit_wrapper_trap}\n",
        f"{unit_wrapper_trap}\ntrap : EXIT\n",
        "later-exit-trap-overrides-wrapper",
    ),
)
expect_unit_rejected(
    "numeric-zero-trap-overrides-wrapper",
    replace_exact(
        unit_text,
        f"{unit_wrapper_trap}\n",
        f"{unit_wrapper_trap}\ntrap : 0\n",
        "numeric-zero-trap-overrides-wrapper",
    ),
)
expect_unit_rejected(
    "comment-heredoc-cannot-hide-trap-override",
    replace_exact(
        unit_text,
        f"{unit_wrapper_trap}\n",
        f"{unit_wrapper_trap}\n# <<true\ntrap : 0\ntrue\n",
        "comment-heredoc-cannot-hide-trap-override",
    ),
)
expect_unit_rejected(
    "second-install-overrides-wrapper",
    replace_exact(
        unit_text,
        f"{unit_wrapper_trap}\n",
        f"{unit_wrapper_trap}\nv934_install_run_status_traps\n",
        "unit-second-install-overrides-wrapper",
    ),
)
unit_early_disarm = replace_exact(
    unit_text,
    "v934_disarm_run_status_traps\n",
    "",
    "unit-early-disarm:remove",
)
unit_early_disarm = replace_exact(
    unit_early_disarm,
    f"{unit_wrapper_trap}\n",
    f"{unit_wrapper_trap}\nv934_disarm_run_status_traps\n",
    "unit-early-disarm:insert",
)
expect_unit_rejected("early-disarm-clears-wrapper", unit_early_disarm)
expect_unit_rejected(
    "semicolon-second-install-overrides-wrapper",
    replace_exact(
        unit_text,
        f"{unit_wrapper_trap}\n",
        f"{unit_wrapper_trap}\nv934_install_run_status_traps;\n",
        "semicolon-second-install-overrides-wrapper",
    ),
)
expect_unit_rejected(
    "early-return-skips-fixture-cleanup",
    replace_exact(
        unit_text,
        "  set +e\n",
        '  set +e\n  return "$exit_code"\n',
        "early-return-skips-fixture-cleanup",
    ),
)
expect_unit_rejected(
    "later-wrapper-definition-shadows-cleanup",
    replace_exact(
        unit_text,
        f"{unit_wrapper_trap}\n",
        f"{unit_wrapper_trap}\nfunction v934_unit_exit_trap {{ :; }}\n",
        "later-wrapper-definition-shadows-cleanup",
    ),
)

integration_trap = 'trap \'v934_run_log_exit_trap "$?" v934_record_run_status\' EXIT'
expect_integration_rejected(
    "missing-direct-finalizer",
    replace_exact(
        integration_text,
        integration_trap,
        f"# {integration_trap}",
        "missing-direct-finalizer",
    ),
)
integration_close = 'v934_run_log_close || fail "owned run logger did not flush and exit cleanly"'
integration_close_after_green = replace_exact(
    integration_text,
    f"{integration_close}\n",
    f"# {integration_close}\n",
    "close-after-green:remove",
)
integration_close_after_green = replace_exact(
    integration_close_after_green,
    "v934_write_run_status 0",
    f"v934_write_run_status 0\n{integration_close}",
    "close-after-green:insert",
)
expect_integration_rejected("close-after-green", integration_close_after_green)
expect_integration_rejected(
    "later-exit-trap-overrides-finalizer",
    replace_exact(
        integration_text,
        f"{integration_trap}\n",
        f"{integration_trap}\ntrap : EXIT\n",
        "later-exit-trap-overrides-finalizer",
    ),
)
expect_integration_rejected(
    "numeric-zero-trap-overrides-finalizer",
    replace_exact(
        integration_text,
        f"{integration_trap}\n",
        f"{integration_trap}\ntrap : 0\n",
        "numeric-zero-trap-overrides-finalizer",
    ),
)
expect_integration_rejected(
    "quoted-heredoc-cannot-hide-trap-override",
    replace_exact(
        integration_text,
        f"{integration_trap}\n",
        f"{integration_trap}\nprintf '%s\\n' '<<true'\ntrap : 0\ntrue\n",
        "quoted-heredoc-cannot-hide-trap-override",
    ),
)
expect_integration_rejected(
    "second-install-overrides-finalizer",
    replace_exact(
        integration_text,
        f"{integration_trap}\n",
        f"{integration_trap}\nv934_install_run_status_traps\n",
        "integration-second-install-overrides-finalizer",
    ),
)
integration_early_disarm = replace_exact(
    integration_text,
    "v934_disarm_run_status_traps\n",
    "",
    "integration-early-disarm:remove",
)
integration_early_disarm = replace_exact(
    integration_early_disarm,
    f"{integration_trap}\n",
    f"{integration_trap}\nv934_disarm_run_status_traps\n",
    "integration-early-disarm:insert",
)
expect_integration_rejected("early-disarm-clears-finalizer", integration_early_disarm)
expect_integration_rejected(
    "semicolon-early-disarm-clears-finalizer",
    replace_exact(
        integration_text,
        f"{integration_trap}\n",
        f"{integration_trap}\nv934_disarm_run_status_traps;\n",
        "semicolon-early-disarm-clears-finalizer",
    ),
)
expect_integration_rejected(
    "logical-or-skips-logger-close",
    replace_exact(
        integration_text,
        integration_close,
        f"true || \\\n{integration_close}",
        "logical-or-skips-logger-close",
    ),
)
expect_integration_rejected(
    "later-close-definition-shadows-flush",
    replace_exact(
        integration_text,
        'PHASE="run-log-flush"\n',
        'v934_run_log_close() { :; }\nPHASE="run-log-flush"\n',
        "later-close-definition-shadows-flush",
    ),
)
expect_integration_rejected(
    "multiline-quote-trap-decoy",
    replace_exact(
        integration_text,
        integration_trap,
        f'trap_decoy="\n{integration_trap}\n"',
        "multiline-quote-trap-decoy",
    ),
)

unit_wrapper_false_context = replace_exact(
    unit_text,
    "v934_unit_exit_trap() {\n",
    "if false; then\nv934_unit_exit_trap() {\n",
    "unit-wrapper-false-context:open",
)
unit_wrapper_false_context = replace_exact(
    unit_wrapper_false_context,
    "}\n\nv934_install_run_status_traps\n",
    "}\nfi\n\nv934_install_run_status_traps\n",
    "unit-wrapper-false-context:close",
)
expect_unit_seal_rejected("wrapper-false-context", unit_wrapper_false_context)

unit_wrapper_subshell_context = replace_exact(
    unit_text,
    "v934_unit_exit_trap() {\n",
    "(\nv934_unit_exit_trap() {\n",
    "unit-wrapper-subshell-context:open",
)
unit_wrapper_subshell_context = replace_exact(
    unit_wrapper_subshell_context,
    "}\n\nv934_install_run_status_traps\n",
    "}\n)\n\nv934_install_run_status_traps\n",
    "unit-wrapper-subshell-context:close",
)
expect_unit_seal_rejected("wrapper-subshell-context", unit_wrapper_subshell_context)
expect_unit_seal_rejected("crlf-byte-drift", unit_text.replace("\n", "\r\n"))

expect_integration_seal_rejected(
    "trap-false-context",
    replace_exact(
        integration_text,
        integration_trap,
        f"if false; then\n{integration_trap}\nfi",
        "integration-trap-false-context",
    ),
)
expect_integration_seal_rejected(
    "trap-subshell-context",
    replace_exact(
        integration_text,
        integration_trap,
        f"(\n{integration_trap}\n)",
        "integration-trap-subshell-context",
    ),
)
integration_flush_slice = "\n".join(
    [
        'PHASE="run-log-flush"',
        'echo "[v934-integration] evidence prepared; flushing owned run logger"',
        integration_close,
        "",
        "# Green status and its hash-bound summary are published only after the logger",
        "# has flushed and been reaped. This keeps the durable evidence order acyclic.",
        'PHASE="completed"',
        "v934_write_run_status 0",
    ]
)
expect_integration_seal_rejected(
    "flush-green-false-context",
    replace_exact(
        integration_text,
        integration_flush_slice,
        f"if false; then\n{integration_flush_slice}\nfi",
        "integration-flush-green-false-context",
    ),
)
expect_integration_seal_rejected(
    "heredoc-source-shadows-close",
    replace_exact(
        integration_text,
        'PHASE="run-log-flush"\n',
        "source /dev/stdin <<'OVERRIDE'\n"
        "v934_run_log_close() { :; }\n"
        "OVERRIDE\n"
        'PHASE="run-log-flush"\n',
        "integration-heredoc-source-shadows-close",
    ),
)
expect_integration_seal_rejected(
    "eval-shadows-close",
    replace_exact(
        integration_text,
        'PHASE="run-log-flush"\n',
        'eval \'v934_run_log_close() { :; }\'\nPHASE="run-log-flush"\n',
        "integration-eval-shadows-close",
    ),
)

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

printf '[v934-run-log-test] PASS slow=2 nonzero=2 timeout=1 pid-reuse=1 early-signal=3 capture-failure=1 exit=2 clean-group=1 persistent-residue=1 unit-shape-negative=13 integration-shape-negative=11 unit-source-seal-negative=3 integration-source-seal-negative=5\n'
