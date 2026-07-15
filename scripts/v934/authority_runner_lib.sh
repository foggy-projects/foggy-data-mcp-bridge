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
    echo "[$log_prefix] ERROR E_AUTHORITY_LOCK: another Step 2 authority runner owns $V934_AUTHORITY_LOCK_PATH" >&2
    return 1
  fi
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
