#!/usr/bin/env bash

# Owned run-log transport for Step 4 child runners.
#
# The caller keeps stdout/stderr connected to a FIFO while one explicitly
# owned logger copies the stream to both the immutable run-log descriptor and
# the caller's original streams.  The logger must be closed and reaped before
# a successful run status or summary is published.

V934_RUN_LOG_STATE=idle
V934_RUN_LOG_CLOSE_STATUS=0
V934_RUN_LOG_FIFO=""
V934_RUN_LOG_PATH=""
V934_RUN_LOG_LOGGER_PID=""
V934_RUN_LOG_LOGGER_STARTTIME_TICKS=""
V934_RUN_LOG_LOGGER_BOOT_ID=""
V934_RUN_LOG_LOGGER_EXPECTED_PPID=""
V934_RUN_LOG_ORIGINAL_STDOUT_FD=""
V934_RUN_LOG_ORIGINAL_STDERR_FD=""
V934_RUN_LOG_FILE_FD=""
V934_RUN_LOG_WAIT_ATTEMPTS=100
V934_RUN_LOG_WAIT_INTERVAL=0.05
V934_RUN_LOG_TERM_ATTEMPTS=20
V934_RUN_LOG_TERM_INTERVAL=0.05
V934_RUN_LOG_SIGNAL_CRITICAL=false
V934_RUN_LOG_PENDING_SIGNAL_NAME=""
V934_RUN_LOG_PENDING_SIGNAL_CODE=""
V934_RUN_LOG_SAVED_INT_TRAP=""
V934_RUN_LOG_SAVED_TERM_TRAP=""
V934_RUN_LOG_SAVED_HUP_TRAP=""

v934_run_log_error() {
  printf '[v934-run-log] ERROR: %s\n' "$*" >&2
}

v934_run_log_valid_fd() {
  [[ "${1:-}" =~ ^[1-9][0-9]*$ && "$1" -gt 2 ]]
}

v934_run_log_close_fd_number() {
  local descriptor="${1:-}"
  v934_run_log_valid_fd "$descriptor" || return 1
  eval "exec ${descriptor}>&-"
}

v934_run_log_defer_signal() {
  local signal_name="$1" exit_code="$2"
  if [[ -z "$V934_RUN_LOG_PENDING_SIGNAL_CODE" ]]; then
    V934_RUN_LOG_PENDING_SIGNAL_NAME="$signal_name"
    V934_RUN_LOG_PENDING_SIGNAL_CODE="$exit_code"
  fi
}

v934_run_log_begin_signal_critical() {
  [[ "$V934_RUN_LOG_SIGNAL_CRITICAL" == false ]] || return 1
  V934_RUN_LOG_SAVED_INT_TRAP="$(trap -p INT)"
  V934_RUN_LOG_SAVED_TERM_TRAP="$(trap -p TERM)"
  V934_RUN_LOG_SAVED_HUP_TRAP="$(trap -p HUP)"
  V934_RUN_LOG_PENDING_SIGNAL_NAME=""
  V934_RUN_LOG_PENDING_SIGNAL_CODE=""
  V934_RUN_LOG_SIGNAL_CRITICAL=true
  trap 'v934_run_log_defer_signal INT 130' INT
  trap 'v934_run_log_defer_signal TERM 143' TERM
  trap 'v934_run_log_defer_signal HUP 129' HUP
}

v934_run_log_restore_signal_traps() {
  local saved signal_name
  for signal_name in INT TERM HUP; do
    case "$signal_name" in
      INT) saved="$V934_RUN_LOG_SAVED_INT_TRAP" ;;
      TERM) saved="$V934_RUN_LOG_SAVED_TERM_TRAP" ;;
      HUP) saved="$V934_RUN_LOG_SAVED_HUP_TRAP" ;;
    esac
    if [[ -n "$saved" ]]; then
      eval "$saved"
    else
      trap - "$signal_name"
    fi
  done
  V934_RUN_LOG_SIGNAL_CRITICAL=false
  V934_RUN_LOG_SAVED_INT_TRAP=""
  V934_RUN_LOG_SAVED_TERM_TRAP=""
  V934_RUN_LOG_SAVED_HUP_TRAP=""
}

v934_run_log_capture_logger_identity() {
  local expected_ppid="$1" identity pid state ppid starttime boot_id
  [[ "$V934_RUN_LOG_LOGGER_PID" =~ ^[1-9][0-9]*$ ]] || return 2
  [[ "$expected_ppid" =~ ^[1-9][0-9]*$ ]] || return 2
  identity="$(python3 - "$V934_RUN_LOG_LOGGER_PID" "$expected_ppid" <<'PY'
from pathlib import Path
import re
import sys

pid = int(sys.argv[1])
expected_ppid = int(sys.argv[2])
try:
    raw = Path(f"/proc/{pid}/stat").read_bytes()
    boot_id = Path("/proc/sys/kernel/random/boot_id").read_text(encoding="ascii").strip()
except (FileNotFoundError, ProcessLookupError):
    raise SystemExit(1)
except (OSError, UnicodeError):
    raise SystemExit(2)
try:
    right = raw.rindex(b")")
    fields = raw[right + 2 :].split()
    if len(fields) < 20:
        raise ValueError("truncated stat")
    state = fields[0].decode("ascii")
    ppid = int(fields[1])
    starttime = int(fields[19])
except (UnicodeError, ValueError):
    raise SystemExit(2)
if (
    state == "Z"
    or ppid != expected_ppid
    or starttime <= 0
    or re.fullmatch(
        r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        boot_id,
    ) is None
):
    raise SystemExit(2)
print(f"{pid}\t{state}\t{ppid}\t{starttime}\t{boot_id}")
PY
  )" || return $?
  IFS=$'\t' read -r pid state ppid starttime boot_id <<< "$identity"
  [[ "$pid" == "$V934_RUN_LOG_LOGGER_PID" && "$state" != Z && "$ppid" == "$expected_ppid" ]] || return 2
  [[ "$starttime" =~ ^[1-9][0-9]*$ && "$boot_id" =~ ^[0-9a-f-]{36}$ ]] || return 2
  V934_RUN_LOG_LOGGER_EXPECTED_PPID="$expected_ppid"
  V934_RUN_LOG_LOGGER_STARTTIME_TICKS="$starttime"
  V934_RUN_LOG_LOGGER_BOOT_ID="$boot_id"
}

# Return 0 for the exact live logger, 1 for exited/zombie, and 2 for an
# identity conflict or unreadable identity.  Callers must never turn 2 into a
# numeric-PID signal.
v934_run_log_logger_state() {
  python3 - \
    "$V934_RUN_LOG_LOGGER_PID" \
    "$V934_RUN_LOG_LOGGER_STARTTIME_TICKS" \
    "$V934_RUN_LOG_LOGGER_EXPECTED_PPID" \
    "$V934_RUN_LOG_LOGGER_BOOT_ID" <<'PY'
from pathlib import Path
import sys

pid = int(sys.argv[1])
expected_starttime = int(sys.argv[2])
expected_ppid = int(sys.argv[3])
expected_boot_id = sys.argv[4]
try:
    boot_id = Path("/proc/sys/kernel/random/boot_id").read_text(encoding="ascii").strip()
except (OSError, UnicodeError):
    raise SystemExit(2)
if boot_id != expected_boot_id:
    raise SystemExit(2)
try:
    raw = Path(f"/proc/{pid}/stat").read_bytes()
except (FileNotFoundError, ProcessLookupError):
    raise SystemExit(1)
except OSError:
    raise SystemExit(2)
try:
    right = raw.rindex(b")")
    fields = raw[right + 2 :].split()
    if len(fields) < 20:
        raise ValueError("truncated stat")
    state = fields[0]
    ppid = int(fields[1])
    starttime = int(fields[19])
except ValueError:
    raise SystemExit(2)
if ppid != expected_ppid or starttime != expected_starttime:
    raise SystemExit(2)
raise SystemExit(1 if state == b"Z" else 0)
PY
}

v934_run_log_signal_logger() {
  local signal_name="$1"
  python3 - \
    "$V934_RUN_LOG_LOGGER_PID" \
    "$V934_RUN_LOG_LOGGER_STARTTIME_TICKS" \
    "$V934_RUN_LOG_LOGGER_EXPECTED_PPID" \
    "$V934_RUN_LOG_LOGGER_BOOT_ID" "$signal_name" <<'PY'
import os
from pathlib import Path
import signal
import sys

pid = int(sys.argv[1])
expected_starttime = int(sys.argv[2])
expected_ppid = int(sys.argv[3])
expected_boot_id = sys.argv[4]
signal_number = getattr(signal, f"SIG{sys.argv[5]}", None)
if signal_number is None:
    raise SystemExit(2)

def identity():
    try:
        raw = Path(f"/proc/{pid}/stat").read_bytes()
    except (FileNotFoundError, ProcessLookupError):
        return None
    except OSError:
        raise SystemExit(2)
    try:
        right = raw.rindex(b")")
        fields = raw[right + 2 :].split()
        if len(fields) < 20:
            raise ValueError("truncated stat")
        return fields[0], int(fields[1]), int(fields[19])
    except ValueError:
        raise SystemExit(2)

try:
    boot_id = Path("/proc/sys/kernel/random/boot_id").read_text(encoding="ascii").strip()
except (OSError, UnicodeError):
    raise SystemExit(2)
if boot_id != expected_boot_id:
    raise SystemExit(2)
before = identity()
if before is None or before[0] == b"Z":
    raise SystemExit(1)
if before[1:] != (expected_ppid, expected_starttime):
    raise SystemExit(2)
try:
    descriptor = os.pidfd_open(pid, 0)
except ProcessLookupError:
    raise SystemExit(1)
except OSError:
    raise SystemExit(2)
try:
    after = identity()
    if after is None or after[0] == b"Z":
        raise SystemExit(1)
    if after[1:] != (expected_ppid, expected_starttime):
        raise SystemExit(2)
    try:
        signal.pidfd_send_signal(descriptor, signal_number)
    except ProcessLookupError:
        raise SystemExit(1)
    except OSError:
        raise SystemExit(2)
finally:
    os.close(descriptor)
PY
}

# Before an identity can be sealed, the only safe way to release the owned
# FIFO reader is to complete its FIFO pairing and close the temporary peer.
# The shell job table (not a numeric process probe) then provides a bounded
# reap decision.  No unsealed PID is ever signalled.
v934_run_log_release_unsealed_fifo_logger() {
  local fifo_path="$1" logger_pid="$2" pair_fd="" attempt job_pid running=false
  [[ "$fifo_path" == /* && -p "$fifo_path" && ! -L "$fifo_path" ]] || return 1
  [[ "$logger_pid" =~ ^[1-9][0-9]*$ ]] || return 1
  if ! exec {pair_fd}<> "$fifo_path"; then
    return 1
  fi
  v934_run_log_close_fd_number "$pair_fd" || return 1
  for ((attempt = 0; attempt < V934_RUN_LOG_WAIT_ATTEMPTS; attempt++)); do
    running=false
    while IFS= read -r job_pid; do
      [[ "$job_pid" == "$logger_pid" ]] && running=true
    done < <(jobs -pr)
    [[ "$running" == true ]] || break
    sleep "$V934_RUN_LOG_WAIT_INTERVAL"
  done
  if [[ "$running" == true ]]; then
    return 1
  fi
  wait "$logger_pid" >/dev/null 2>&1
}

v934_run_log_validate_timing() {
  [[ "$V934_RUN_LOG_WAIT_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || return 1
  [[ "$V934_RUN_LOG_TERM_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || return 1
  [[ "$V934_RUN_LOG_WAIT_INTERVAL" =~ ^[0-9]+([.][0-9]+)?$ ]] || return 1
  [[ "$V934_RUN_LOG_TERM_INTERVAL" =~ ^[0-9]+([.][0-9]+)?$ ]] || return 1
}

v934_run_log_validate_binding() {
  local descriptor="${V934_RUN_LOG_FILE_FD:-}"
  local path="${V934_RUN_LOG_PATH:-}"
  v934_run_log_valid_fd "$descriptor" || return 1
  [[ "$path" == /* && -f "$path" && ! -L "$path" ]] || return 1
  [[ "$path" -ef "/proc/$BASHPID/fd/$descriptor" ]]
}

v934_run_log_release_parent_fds() {
  local code=0 descriptor
  for descriptor in \
    "${V934_RUN_LOG_ORIGINAL_STDOUT_FD:-}" \
    "${V934_RUN_LOG_ORIGINAL_STDERR_FD:-}" \
    "${V934_RUN_LOG_FILE_FD:-}"; do
    if v934_run_log_valid_fd "$descriptor"; then
      v934_run_log_close_fd_number "$descriptor" || code=1
    fi
  done
  V934_RUN_LOG_ORIGINAL_STDOUT_FD=""
  V934_RUN_LOG_ORIGINAL_STDERR_FD=""
  V934_RUN_LOG_FILE_FD=""
  return "$code"
}

v934_run_log_abort_open() {
  local attempt logger_pid="${V934_RUN_LOG_LOGGER_PID:-}" state=1
  if [[ "$V934_RUN_LOG_STATE" == open ]]; then
    if v934_run_log_valid_fd "${V934_RUN_LOG_ORIGINAL_STDOUT_FD:-}" &&
       v934_run_log_valid_fd "${V934_RUN_LOG_ORIGINAL_STDERR_FD:-}"; then
      eval "exec 1>&${V934_RUN_LOG_ORIGINAL_STDOUT_FD} 2>&${V934_RUN_LOG_ORIGINAL_STDERR_FD}" || true
    fi
  fi
  if [[ "$logger_pid" =~ ^[1-9][0-9]*$ && \
        "$V934_RUN_LOG_LOGGER_STARTTIME_TICKS" =~ ^[1-9][0-9]*$ ]]; then
    if v934_run_log_logger_state; then
      state=0
      v934_run_log_signal_logger TERM >/dev/null 2>&1 || true
      for ((attempt = 0; attempt < V934_RUN_LOG_TERM_ATTEMPTS; attempt++)); do
        if v934_run_log_logger_state; then
          sleep "$V934_RUN_LOG_TERM_INTERVAL"
        else
          state=$?
          break
        fi
      done
      if [[ "$state" -eq 0 ]]; then
        v934_run_log_signal_logger KILL >/dev/null 2>&1 || true
      fi
    else
      state=$?
    fi
    [[ "$state" -ne 2 ]] && wait "$logger_pid" >/dev/null 2>&1 || true
  elif [[ "$logger_pid" =~ ^[1-9][0-9]*$ ]]; then
    # Pairing and closing the FIFO releases the reader without signalling an
    # unsealed numeric PID.  The reap remains bounded by the shell job table.
    v934_run_log_release_unsealed_fifo_logger \
      "$V934_RUN_LOG_FIFO" "$logger_pid" >/dev/null 2>&1 || true
  fi
  [[ -z "${V934_RUN_LOG_FIFO:-}" ]] || rm -f -- "$V934_RUN_LOG_FIFO"
  v934_run_log_release_parent_fds || true
  V934_RUN_LOG_FIFO=""
  V934_RUN_LOG_LOGGER_PID=""
  V934_RUN_LOG_LOGGER_STARTTIME_TICKS=""
  V934_RUN_LOG_LOGGER_BOOT_ID=""
  V934_RUN_LOG_LOGGER_EXPECTED_PPID=""
  V934_RUN_LOG_STATE=closed
  V934_RUN_LOG_CLOSE_STATUS=1
  return 1
}

v934_run_log_open() {
  local run_root="${1:-}"
  local label="${2:-v934-child}"
  local logger_path="${3:-}"
  local authority_fd="${V934_AUTHORITY_LOCK_FD:-}"
  local stdout_fd stderr_fd log_fd noclobber_was_set=false
  local parent_pid pending_signal_code=""

  [[ "$#" -ge 1 && "$#" -le 3 ]] || {
    v934_run_log_error "usage: v934_run_log_open RUN_ROOT [LABEL] [LOGGER]"
    return 1
  }
  [[ "$V934_RUN_LOG_STATE" == idle ]] || {
    v934_run_log_error "$label logger lifecycle is already initialized"
    return 1
  }
  v934_run_log_validate_timing || {
    v934_run_log_error "$label logger timing configuration is invalid"
    return 1
  }
  [[ "$run_root" == /* && -d "$run_root" && ! -L "$run_root" ]] || {
    v934_run_log_error "$label run root is not an absolute real directory"
    return 1
  }
  [[ "$(cd -- "$run_root" && pwd -P)" == "$run_root" ]] || {
    v934_run_log_error "$label run root is not canonical"
    return 1
  }
  if [[ -z "$logger_path" ]]; then
    logger_path="$(command -v tee)" || {
      v934_run_log_error "$label cannot resolve tee"
      return 1
    }
  fi
  [[ "$logger_path" == /* && -f "$logger_path" && ! -L "$logger_path" && -x "$logger_path" ]] || {
    v934_run_log_error "$label logger is not an absolute executable regular file"
    return 1
  }
  if [[ -n "$authority_fd" ]] && ! v934_run_log_valid_fd "$authority_fd"; then
    v934_run_log_error "$label authority lock descriptor is malformed"
    return 1
  fi

  V934_RUN_LOG_PATH="$run_root/run.log"
  V934_RUN_LOG_FIFO="$run_root/.run-log.fifo"
  [[ ! -e "$V934_RUN_LOG_PATH" && ! -L "$V934_RUN_LOG_PATH" ]] || {
    v934_run_log_error "$label refuses to overwrite run.log"
    return 1
  }
  [[ ! -e "$V934_RUN_LOG_FIFO" && ! -L "$V934_RUN_LOG_FIFO" ]] || {
    v934_run_log_error "$label refuses to reuse the run-log FIFO"
    return 1
  }

  unset V934_RUN_LOG_ORIGINAL_STDOUT_FD V934_RUN_LOG_ORIGINAL_STDERR_FD V934_RUN_LOG_FILE_FD
  exec {V934_RUN_LOG_ORIGINAL_STDOUT_FD}>&1 || return 1
  exec {V934_RUN_LOG_ORIGINAL_STDERR_FD}>&2 || {
    v934_run_log_release_parent_fds || true
    return 1
  }
  if [[ -o noclobber ]]; then
    noclobber_was_set=true
  else
    set -o noclobber
  fi
  if ! exec {V934_RUN_LOG_FILE_FD}> "$V934_RUN_LOG_PATH"; then
    [[ "$noclobber_was_set" == true ]] || set +o noclobber
    v934_run_log_release_parent_fds || true
    return 1
  fi
  [[ "$noclobber_was_set" == true ]] || set +o noclobber
  v934_run_log_validate_binding || {
    v934_run_log_release_parent_fds || true
    return 1
  }
  if ! mkfifo -- "$V934_RUN_LOG_FIFO"; then
    v934_run_log_release_parent_fds || true
    return 1
  fi

  stdout_fd="$V934_RUN_LOG_ORIGINAL_STDOUT_FD"
  stderr_fd="$V934_RUN_LOG_ORIGINAL_STDERR_FD"
  log_fd="$V934_RUN_LOG_FILE_FD"
  parent_pid="$BASHPID"
  v934_run_log_begin_signal_critical || {
    rm -f -- "$V934_RUN_LOG_FIFO"
    v934_run_log_release_parent_fds || true
    return 1
  }
  (
    trap - INT TERM HUP PIPE
    # The inherited authority descriptor is never held while the logger waits
    # for a FIFO peer.  Redirection must stay inside the subshell body so this
    # close runs before the potentially blocking open(2).
    if [[ -n "$authority_fd" ]]; then
      v934_run_log_close_fd_number "$authority_fd" || exit 93
    fi
    exec 0< "$V934_RUN_LOG_FIFO"
    eval "exec 1>&${stdout_fd} 2>&${stderr_fd}"
    v934_run_log_close_fd_number "$stdout_fd"
    v934_run_log_close_fd_number "$stderr_fd"
    exec "$logger_path" -a -- "/proc/self/fd/$log_fd"
  ) &
  V934_RUN_LOG_LOGGER_PID=$!
  V934_RUN_LOG_STATE=open
  if ! v934_run_log_capture_logger_identity "$parent_pid"; then
    # abort_open intentionally reports failure; keep control here even when a
    # caller invokes open() outside an `if`/`||` errexit-suppression context so
    # the deferred-signal traps are always restored before returning.
    v934_run_log_abort_open || true
    pending_signal_code="$V934_RUN_LOG_PENDING_SIGNAL_CODE"
    v934_run_log_restore_signal_traps
    [[ -z "$pending_signal_code" ]] || exit "$pending_signal_code"
    return 1
  fi
  if ! exec > "$V934_RUN_LOG_FIFO" 2>&1; then
    v934_run_log_abort_open || true
    pending_signal_code="$V934_RUN_LOG_PENDING_SIGNAL_CODE"
    v934_run_log_restore_signal_traps
    [[ -z "$pending_signal_code" ]] || exit "$pending_signal_code"
    return 1
  fi
  rm -f -- "$V934_RUN_LOG_FIFO" || {
    v934_run_log_abort_open || true
    pending_signal_code="$V934_RUN_LOG_PENDING_SIGNAL_CODE"
    v934_run_log_restore_signal_traps
    [[ -z "$pending_signal_code" ]] || exit "$pending_signal_code"
    return 1
  }
  V934_RUN_LOG_FIFO=""
  pending_signal_code="$V934_RUN_LOG_PENDING_SIGNAL_CODE"
  if [[ -n "$pending_signal_code" ]]; then
    v934_run_log_close || true
    v934_run_log_restore_signal_traps
    exit "$pending_signal_code"
  fi
  v934_run_log_restore_signal_traps
  return 0
}

v934_run_log_close() {
  local attempt logger_pid wait_code=0 code=0 timed_out=false state=1 signal_code=1
  if [[ "$V934_RUN_LOG_STATE" == closed ]]; then
    return "$V934_RUN_LOG_CLOSE_STATUS"
  fi
  [[ "$V934_RUN_LOG_STATE" == open ]] || return 0
  v934_run_log_validate_timing || code=1

  if v934_run_log_valid_fd "${V934_RUN_LOG_ORIGINAL_STDOUT_FD:-}" &&
     v934_run_log_valid_fd "${V934_RUN_LOG_ORIGINAL_STDERR_FD:-}"; then
    if ! eval "exec 1>&${V934_RUN_LOG_ORIGINAL_STDOUT_FD} 2>&${V934_RUN_LOG_ORIGINAL_STDERR_FD}"; then
      code=1
    fi
  else
    code=1
  fi
  V934_RUN_LOG_STATE=closing
  logger_pid="${V934_RUN_LOG_LOGGER_PID:-}"

  if [[ "$logger_pid" =~ ^[1-9][0-9]*$ ]]; then
    for ((attempt = 0; attempt < V934_RUN_LOG_WAIT_ATTEMPTS; attempt++)); do
      if v934_run_log_logger_state; then
        state=0
        sleep "$V934_RUN_LOG_WAIT_INTERVAL"
      else
        state=$?
        break
      fi
    done
    if [[ "$state" -eq 0 ]]; then
      timed_out=true
      code=124
      if v934_run_log_signal_logger TERM; then
        signal_code=0
      else
        signal_code=$?
        [[ "$signal_code" -eq 1 ]] || code=1
      fi
      for ((attempt = 0; attempt < V934_RUN_LOG_TERM_ATTEMPTS; attempt++)); do
        if v934_run_log_logger_state; then
          state=0
          sleep "$V934_RUN_LOG_TERM_INTERVAL"
        else
          state=$?
          break
        fi
      done
      if [[ "$state" -eq 0 ]]; then
        if v934_run_log_signal_logger KILL; then
          signal_code=0
        else
          signal_code=$?
          [[ "$signal_code" -eq 1 ]] || code=1
        fi
      fi
      [[ "$state" -ne 2 && "$signal_code" -ne 2 ]] && \
        wait "$logger_pid" >/dev/null 2>&1 || true
    elif [[ "$state" -eq 2 ]]; then
      code=1
    elif wait "$logger_pid"; then
      wait_code=0
    else
      wait_code=$?
      code=1
    fi
  else
    code=1
  fi
  V934_RUN_LOG_LOGGER_PID=""
  V934_RUN_LOG_LOGGER_STARTTIME_TICKS=""
  V934_RUN_LOG_LOGGER_BOOT_ID=""
  V934_RUN_LOG_LOGGER_EXPECTED_PPID=""

  if ! v934_run_log_validate_binding && [[ "$code" -eq 0 ]]; then
    code=1
  fi
  if ! v934_run_log_release_parent_fds && [[ "$code" -eq 0 ]]; then
    code=1
  fi
  [[ -z "${V934_RUN_LOG_FIFO:-}" ]] || rm -f -- "$V934_RUN_LOG_FIFO"
  V934_RUN_LOG_FIFO=""
  V934_RUN_LOG_STATE=closed
  V934_RUN_LOG_CLOSE_STATUS="$code"
  if [[ "$timed_out" == true ]]; then
    v934_run_log_error "logger pid=$logger_pid exceeded the bounded flush deadline"
  elif [[ "$wait_code" -ne 0 ]]; then
    v934_run_log_error "logger pid=$logger_pid exited non-zero rc=$wait_code"
  elif [[ "$code" -ne 0 ]]; then
    v934_run_log_error "logger lifecycle validation failed"
  fi
  return "$code"
}

v934_run_log_exit_trap() {
  local exit_code="${1:-1}"
  local status_callback="${2:-}"
  local close_code=0 callback_code=0
  [[ "$exit_code" =~ ^[0-9]+$ ]] || exit_code=1
  trap '' INT TERM HUP
  trap - EXIT
  set +e
  v934_run_log_close
  close_code=$?
  if [[ "$exit_code" -eq 0 && "$close_code" -ne 0 ]]; then
    exit_code=1
  fi
  if [[ -n "$status_callback" ]] && declare -F "$status_callback" >/dev/null 2>&1; then
    "$status_callback" "$exit_code"
    callback_code=$?
    if [[ "$exit_code" -eq 0 && "$callback_code" -ne 0 ]]; then
      exit_code="$callback_code"
    fi
  elif [[ "$exit_code" -eq 0 ]]; then
    exit_code=1
  fi
  exit "$exit_code"
}
