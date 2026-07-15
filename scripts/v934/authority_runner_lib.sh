#!/usr/bin/env bash

# Shared process-level controls for the versioned 9.3.4 authority runners.
# This file is sourced by the runners and intentionally does not change shell options.

v934_acquire_authority_lock() {
  local root_dir="$1"
  local log_prefix="$2"
  local git_dir

  git_dir="$(git -C "$root_dir" rev-parse --absolute-git-dir)" || {
    echo "[$log_prefix] ERROR E_AUTHORITY_LOCK: cannot resolve the workspace git directory" >&2
    return 1
  }
  V934_AUTHORITY_LOCK_PATH="$git_dir/v934-step2-authority.lock"
  if ! exec {V934_AUTHORITY_LOCK_FD}>>"$V934_AUTHORITY_LOCK_PATH"; then
    echo "[$log_prefix] ERROR E_AUTHORITY_LOCK: cannot open $V934_AUTHORITY_LOCK_PATH" >&2
    return 1
  fi
  if ! flock -n "$V934_AUTHORITY_LOCK_FD"; then
    exec {V934_AUTHORITY_LOCK_FD}>&-
    unset V934_AUTHORITY_LOCK_FD
    echo "[$log_prefix] ERROR E_AUTHORITY_LOCK: another versioned authority runner owns $V934_AUTHORITY_LOCK_PATH" >&2
    return 1
  fi
}

v934_validate_inherited_authority_lock() {
  local root_dir="$1"
  local log_prefix="$2"
  local lock_fd="${V934_AUTHORITY_LOCK_FD:-}"
  local marker_path="${V934_PARENT_OUTER_MARKER_PATH:-}"
  local marker_sha="${V934_PARENT_OUTER_MARKER_SHA256:-}"
  local current_head git_dir expected_lock expected_marker

  if [[ ! "$lock_fd" =~ ^[0-9]+$ || "$lock_fd" -le 2 ]]; then
    echo "[$log_prefix] ERROR E_AUTHORITY_LOCK: inherited lock descriptor is invalid" >&2
    return 1
  fi
  if [[ "${V934_PARENT_AUTHORITY_KIND:-}" != "step3-required-matrix" ]]; then
    echo "[$log_prefix] ERROR E_PARENT_CONTEXT: inherited authority kind differs" >&2
    return 1
  fi
  if [[ -z "${RUN_ID:-}" || "${V934_PARENT_RUN_ID:-}" != "$RUN_ID" ]]; then
    echo "[$log_prefix] ERROR E_PARENT_CONTEXT: parent run id differs" >&2
    return 1
  fi
  current_head="$(git -C "$root_dir" rev-parse HEAD)" || {
    echo "[$log_prefix] ERROR E_PARENT_CONTEXT: cannot resolve current Git HEAD" >&2
    return 1
  }
  if [[ "${V934_PARENT_GIT_HEAD:-}" != "$current_head" ]]; then
    echo "[$log_prefix] ERROR E_PARENT_CONTEXT: parent Git HEAD differs" >&2
    return 1
  fi
  if [[ ! "${V934_PARENT_CONTRACT_SHA256:-}" =~ ^[0-9a-f]{64}$ ||
        ! "${V934_PARENT_SOURCE_SHA256:-}" =~ ^[0-9a-f]{64}$ ||
        ! "$marker_sha" =~ ^[0-9a-f]{64}$ ]]; then
    echo "[$log_prefix] ERROR E_PARENT_CONTEXT: parent digest metadata is invalid" >&2
    return 1
  fi
  if [[ "$marker_path" != /* || ! -f "$marker_path" || -L "$marker_path" ]]; then
    echo "[$log_prefix] ERROR E_PARENT_CONTEXT: parent outer marker is not a canonical regular file" >&2
    return 1
  fi
  expected_marker="$root_dir/target/v934-step3-required-matrix/runs/$RUN_ID/run-context.json"
  if [[ "$(readlink -f -- "$marker_path")" != "$(readlink -f -- "$expected_marker")" ]]; then
    echo "[$log_prefix] ERROR E_PARENT_CONTEXT: parent outer marker path differs" >&2
    return 1
  fi
  if [[ "$(sha256sum "$marker_path" | cut -d' ' -f1)" != "$marker_sha" ]]; then
    echo "[$log_prefix] ERROR E_PARENT_CONTEXT: parent outer marker digest differs" >&2
    return 1
  fi
  git_dir="$(git -C "$root_dir" rev-parse --absolute-git-dir)" || {
    echo "[$log_prefix] ERROR E_AUTHORITY_LOCK: cannot resolve the workspace git directory" >&2
    return 1
  }
  expected_lock="$git_dir/v934-step2-authority.lock"
  if [[ ! -f "$expected_lock" || -L "$expected_lock" ]]; then
    echo "[$log_prefix] ERROR E_AUTHORITY_LOCK: expected authority lock is not a regular file" >&2
    return 1
  fi
  if ! python3 - "$lock_fd" "$expected_lock" <<'PY'
import fcntl
import os
from pathlib import Path
import stat
import sys

fd = int(sys.argv[1])
expected = Path(sys.argv[2])
try:
    descriptor_stat = os.fstat(fd)
    expected_stat = expected.stat()
    if not stat.S_ISREG(descriptor_stat.st_mode) or not stat.S_ISREG(expected_stat.st_mode):
        raise OSError("lock is not a regular file")
    if (descriptor_stat.st_dev, descriptor_stat.st_ino) != (
        expected_stat.st_dev,
        expected_stat.st_ino,
    ):
        raise OSError("descriptor does not reference the expected authority lock")
    if not os.get_inheritable(fd):
        raise OSError("descriptor is not inheritable")
    # Prove that the canonical lock was already held before this validator
    # ran.  Otherwise an unlocked inherited descriptor could acquire it here
    # and masquerade as parent-owned authority.
    independent_fd = os.open(expected, os.O_RDWR | os.O_APPEND)
    try:
        try:
            fcntl.flock(independent_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            pass
        else:
            fcntl.flock(independent_fd, fcntl.LOCK_UN)
            raise OSError("outer lock was not already held")
    finally:
        os.close(independent_fd)
    # flock ownership follows the open-file description. Re-locking the
    # inherited description succeeds while a separately opened descriptor is
    # blocked by the top-level exclusive lock.
    fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
except (BlockingIOError, OSError) as error:
    print(error, file=sys.stderr)
    raise SystemExit(1)
PY
  then
    echo "[$log_prefix] ERROR E_AUTHORITY_LOCK: inherited descriptor does not own the outer lock" >&2
    return 1
  fi
}

v934_acquire_or_validate_authority_lock() {
  local root_dir="$1"
  local log_prefix="$2"
  local mode="${V934_AUTHORITY_LOCK_MODE:-standalone}"

  case "$mode" in
    standalone)
      if [[ -n "${V934_PARENT_AUTHORITY_KIND:-}${V934_PARENT_RUN_ID:-}${V934_PARENT_GIT_HEAD:-}${V934_PARENT_CONTRACT_SHA256:-}${V934_PARENT_SOURCE_SHA256:-}${V934_PARENT_OUTER_MARKER_SHA256:-}${V934_PARENT_OUTER_MARKER_PATH:-}" ]]; then
        echo "[$log_prefix] ERROR E_PARENT_CONTEXT: standalone runner received partial parent context" >&2
        return 1
      fi
      v934_acquire_authority_lock "$root_dir" "$log_prefix" || return 1
      export V934_AUTHORITY_LOCK_FD
      ;;
    inherited)
      v934_validate_inherited_authority_lock "$root_dir" "$log_prefix" || return 1
      export V934_AUTHORITY_LOCK_FD
      ;;
    *)
      echo "[$log_prefix] ERROR E_AUTHORITY_LOCK: unsupported authority lock mode: $mode" >&2
      return 1
      ;;
  esac
}

v934_write_run_status() {
  local exit_code="$1"
  local finished_at status temporary_status

  finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)" || return 1
  status="failed"
  if [[ "$exit_code" -eq 0 && "${PHASE:-}" == "completed" ]]; then
    status="passed"
  fi
  temporary_status="$RUN_ROOT/.run-status.env.$$.$RANDOM.tmp"
  if ! {
    printf 'run_id=%s\n' "$RUN_ID"
    printf 'runner=%s\n' "${RUNNER_NAME:-}"
    printf 'git_head=%s\n' "${GIT_HEAD:-}"
    printf 'started_at=%s\n' "${STARTED_AT:-}"
    printf 'finished_at=%s\n' "$finished_at"
    printf 'last_phase=%s\n' "${PHASE:-}"
    printf 'exit_code=%s\n' "$exit_code"
    printf 'source_before_sha256=%s\n' "${SOURCE_BEFORE:-}"
    printf 'source_after_sha256=%s\n' "${SOURCE_AFTER:-}"
    printf 'outer_marker_sha256=%s\n' "${OUTER_MARKER_SHA256:-}"
    printf 'successor_manifest_sha256=%s\n' "${SUCCESSOR_MANIFEST_SHA256:-}"
    printf 'final_report_manifest_sha256=%s\n' "${FINAL_REPORT_MANIFEST_SHA256:-}"
    printf 'status=%s\n' "$status"
  } > "$temporary_status"; then
    rm -f -- "$temporary_status"
    return 1
  fi
  if ! mv -f -- "$temporary_status" "$RUN_ROOT/run-status.env"; then
    rm -f -- "$temporary_status"
    return 1
  fi
}

v934_exit_on_signal() {
  trap '' INT TERM HUP
  exit "$1"
}

v934_install_run_status_traps() {
  trap 'v934_record_run_status "$?"' EXIT
  trap 'v934_exit_on_signal 130' INT
  trap 'v934_exit_on_signal 143' TERM
  trap 'v934_exit_on_signal 129' HUP
}

v934_disarm_run_status_traps() {
  # Ignore signals before removing EXIT so a completed run cannot retain a
  # stale green status in the disarm window.
  trap '' INT TERM HUP
  trap - EXIT
}

v934_record_run_status() {
  local exit_code="$1"
  local status_exit_code=0

  # Status finalization is one non-reentrant critical section. A signal that
  # arrived before this point has already been mapped to its POSIX exit code.
  trap '' INT TERM HUP
  trap - EXIT
  set +e
  if [[ "$exit_code" -eq 0 && "${PHASE:-}" != "completed" ]]; then
    exit_code=1
  fi
  if [[ "$exit_code" -ne 0 ]]; then
    rm -f -- "$RUN_ROOT/summary.env"
  fi
  v934_write_run_status "$exit_code"
  status_exit_code="$?"
  if [[ "$exit_code" -eq 0 && "$status_exit_code" -ne 0 ]]; then
    exit_code="$status_exit_code"
  fi
  exit "$exit_code"
}
