#!/usr/bin/env bash

# Shared-child bootstrap for the Step 3 required external lanes. The outer
# matrix runner owns the authority lock and canonical run context; lane runners
# only validate and inherit that authority here.

v934_external_prepare_shared_child() {
  local root_dir="$1"
  local run_id="$2"
  local lane="$3"
  local git_dir canonical_lock inherited_fd inherited_target lock_probe_code
  local parent_root resolved_parent parent_marker lanes_root resolved_lanes
  local lane_root lane_marker

  case "$lane" in
    external-redis|external-mongo|external-mysql|external-vector) ;;
    *)
      echo "[v934-external-shared] ERROR: unsupported shared-child lane: $lane" >&2
      return 1
      ;;
  esac
  if [[ ! "$run_id" =~ ^[A-Za-z0-9._-]+$ || "$run_id" == . || "$run_id" == .. ]]; then
    echo "[v934-external-shared] ERROR: unsafe shared-child run id: $run_id" >&2
    return 1
  fi

  git_dir="$(git -C "$root_dir" rev-parse --absolute-git-dir)" || {
    echo "[v934-external-shared] ERROR: cannot resolve the workspace git directory" >&2
    return 1
  }
  canonical_lock="$(readlink -f "$git_dir/v934-step2-authority.lock")" || {
    echo "[v934-external-shared] ERROR: cannot resolve the canonical authority lock" >&2
    return 1
  }
  inherited_fd="${V934_AUTHORITY_LOCK_FD:-}"
  if [[ ! "$inherited_fd" =~ ^[0-9]+$ || ! -e "/proc/$$/fd/$inherited_fd" ]]; then
    echo "[v934-external-shared] ERROR: inherited authority lock descriptor is missing" >&2
    return 1
  fi
  inherited_target="$(readlink -f "/proc/$$/fd/$inherited_fd")" || {
    echo "[v934-external-shared] ERROR: inherited authority lock descriptor is unreadable" >&2
    return 1
  }
  if [[ "$inherited_target" != "$canonical_lock" ]]; then
    echo "[v934-external-shared] ERROR: inherited authority lock descriptor targets a different file" >&2
    return 1
  fi
  # A separately opened descriptor must be unable to acquire the lock. On the
  # valid path this is a read-only ownership probe; an unlocked descriptor is
  # rejected before the lane run root is created.
  if flock -n -E 75 "$canonical_lock" -c true >/dev/null 2>&1; then
    echo "[v934-external-shared] ERROR: inherited authority descriptor does not hold the lock" >&2
    return 1
  else
    lock_probe_code=$?
    if [[ "$lock_probe_code" -ne 75 ]]; then
      echo "[v934-external-shared] ERROR: canonical authority lock ownership probe failed" >&2
      return 1
    fi
  fi

  parent_root="$root_dir/target/v934-step3-external-matrix/runs/$run_id"
  [[ -d "$parent_root" && ! -L "$parent_root" ]] || {
    echo "[v934-external-shared] ERROR: canonical parent run root is missing" >&2
    return 1
  }
  resolved_parent="$(cd "$parent_root" && pwd -P)" || return 1
  if [[ "$resolved_parent" != "$parent_root" ]]; then
    echo "[v934-external-shared] ERROR: canonical parent run root resolves elsewhere" >&2
    return 1
  fi
  parent_marker="$parent_root/run-context.json"
  [[ -f "$parent_marker" && ! -L "$parent_marker" ]] || {
    echo "[v934-external-shared] ERROR: canonical parent outer marker is not regular" >&2
    return 1
  }

  lanes_root="$parent_root/lanes"
  if [[ -L "$lanes_root" ]]; then
    echo "[v934-external-shared] ERROR: shared-child lanes root is a symlink" >&2
    return 1
  fi
  mkdir -p "$lanes_root"
  [[ -d "$lanes_root" && ! -L "$lanes_root" ]] || {
    echo "[v934-external-shared] ERROR: shared-child lanes root is not a directory" >&2
    return 1
  }
  resolved_lanes="$(cd "$lanes_root" && pwd -P)" || return 1
  if [[ "$resolved_lanes" != "$lanes_root" ]]; then
    echo "[v934-external-shared] ERROR: shared-child lanes root resolves elsewhere" >&2
    return 1
  fi

  lane_root="$lanes_root/$lane"
  if [[ -e "$lane_root" || -L "$lane_root" ]]; then
    echo "[v934-external-shared] ERROR: shared-child lane root already exists: $lane_root" >&2
    return 1
  fi
  mkdir -p "$lane_root"
  lane_marker="$lane_root/run-context.json"
  if ! python3 - "$parent_marker" "$lane_marker" <<'PY'
from pathlib import Path
import shutil
import sys

source = Path(sys.argv[1])
target = Path(sys.argv[2])
if source.is_symlink() or not source.is_file():
    raise SystemExit("parent outer marker is not a regular file")
shutil.copy2(source, target)
if target.is_symlink() or not target.is_file() or target.read_bytes() != source.read_bytes():
    raise SystemExit("shared-child outer marker copy differs")
PY
  then
    rmdir "$lane_root" >/dev/null 2>&1 || true
    echo "[v934-external-shared] ERROR: shared-child outer marker copy failed" >&2
    return 1
  fi
  if ! cmp -s -- "$parent_marker" "$lane_marker"; then
    echo "[v934-external-shared] ERROR: shared-child outer marker bytes differ" >&2
    return 1
  fi

  V934_EXTERNAL_SHARED_PARENT_ROOT="$parent_root"
  V934_EXTERNAL_SHARED_PARENT_MARKER="$parent_marker"
  V934_EXTERNAL_SHARED_LANE_ROOT="$lane_root"
  V934_EXTERNAL_SHARED_LANE_MARKER="$lane_marker"
}
