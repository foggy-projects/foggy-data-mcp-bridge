#!/usr/bin/env bash

# Step 4 child-side validation for the single outer coverage authority.
# This file is sourced by Unit, Integration, and the Step 3 parent runner; it
# intentionally does not change the caller's shell options.
#
# Required inherited environment:
#   V934_AUTHORITY_LOCK_MODE=inherited
#   V934_AUTHORITY_LOCK_FD=<inheritable descriptor owned by the outer runner>
#   V934_PARENT_AUTHORITY_KIND=step4-coverage
#   V934_PARENT_RUN_ID=<RUN_ID>
#   V934_PARENT_GIT_HEAD=<current committed HEAD>
#   V934_PARENT_CONTRACT_SHA256=<coverage contract SHA-256>
#   V934_PARENT_SOURCE_SHA256=<outer source seal SHA-256>
#   V934_PARENT_OUTER_MARKER_PATH=<canonical Step 4 run-context.json>
#   V934_PARENT_OUTER_MARKER_SHA256=<run-context.json SHA-256>
#
# Usage:
#   v934_step4_validate_inherited_authority \
#     "$ROOT_DIR" "v934-unit" "$CURRENT_SOURCE_SHA256"
#
# The third argument is mandatory and must be freshly calculated by the child.
# It is deliberately not read from another inherited environment variable.

v934_step4_authority_error() {
  local prefix="${1:-v934-step4-authority}"
  local code="${2:-E_PARENT_CONTEXT}"
  local message="${3:-inherited authority validation failed}"

  printf '[%s] ERROR %s: %s\n' "$prefix" "$code" "$message" >&2
}

v934_step4_validate_inherited_authority() {
  if [[ "$#" -ne 3 ]]; then
    v934_step4_authority_error "v934-step4-authority" E_PARENT_CONTEXT \
      "usage: v934_step4_validate_inherited_authority ROOT PREFIX CURRENT_SOURCE_SHA256"
    return 1
  fi

  local supplied_root="$1"
  local log_prefix="$2"
  local current_source_sha256="$3"
  local root_dir git_root git_dir current_head worktree_status expected_marker expected_lock
  local lock_fd run_id parent_run_id parent_git_head parent_contract_sha256
  local parent_source_sha256 marker_path marker_sha256 current_contract_sha256

  if [[ -z "$log_prefix" || "$log_prefix" == *$'\n'* || "$log_prefix" == *$'\r'* ]]; then
    v934_step4_authority_error v934-step4-authority E_PARENT_CONTEXT \
      "unsafe log prefix"
    return 1
  fi

  if [[ "$supplied_root" != /* || ! -d "$supplied_root" || -L "$supplied_root" ]]; then
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "repository root is not an absolute real directory"
    return 1
  fi
  root_dir="$(cd "$supplied_root" 2>/dev/null && pwd -P)" || {
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "cannot resolve repository root"
    return 1
  }
  if [[ "$supplied_root" != "$root_dir" ]]; then
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "repository root path is not canonical"
    return 1
  fi
  git_root="$(git -C "$root_dir" rev-parse --show-toplevel 2>/dev/null)" || {
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "cannot resolve repository worktree"
    return 1
  }
  if [[ "$git_root" != "$root_dir" ]]; then
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "repository root differs from the Git worktree root"
    return 1
  fi
  worktree_status="$(git -C "$root_dir" status --porcelain=v1 --untracked-files=all 2>/dev/null)" || {
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "cannot inspect repository worktree status"
    return 1
  }
  if [[ -n "$worktree_status" ]]; then
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "formal Step 4 authority requires a clean committed worktree"
    return 1
  fi

  if [[ "${V934_AUTHORITY_LOCK_MODE:-}" != inherited ]]; then
    v934_step4_authority_error "$log_prefix" E_AUTHORITY_LOCK \
      "Step 4 child requires authority mode=inherited"
    return 1
  fi
  if [[ "${V934_PARENT_AUTHORITY_KIND:-}" != step4-coverage ]]; then
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "inherited authority kind differs"
    return 1
  fi

  lock_fd="${V934_AUTHORITY_LOCK_FD:-}"
  run_id="${RUN_ID:-}"
  parent_run_id="${V934_PARENT_RUN_ID:-}"
  parent_git_head="${V934_PARENT_GIT_HEAD:-}"
  parent_contract_sha256="${V934_PARENT_CONTRACT_SHA256:-}"
  parent_source_sha256="${V934_PARENT_SOURCE_SHA256:-}"
  marker_path="${V934_PARENT_OUTER_MARKER_PATH:-}"
  marker_sha256="${V934_PARENT_OUTER_MARKER_SHA256:-}"

  # Numeric descriptors with leading zeroes and standard streams are rejected.
  if [[ ! "$lock_fd" =~ ^[1-9][0-9]*$ || "$lock_fd" -le 2 ]]; then
    v934_step4_authority_error "$log_prefix" E_AUTHORITY_LOCK \
      "inherited lock descriptor is missing or unsafe"
    return 1
  fi
  if [[ ! "$run_id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ||
        "$run_id" == . || "$run_id" == .. || "${#run_id}" -gt 128 ||
        "$parent_run_id" != "$run_id" ]]; then
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "parent run id is missing, unsafe, or differs"
    return 1
  fi
  if [[ ! "$parent_git_head" =~ ^[0-9a-f]{40}$ ||
        ! "$parent_contract_sha256" =~ ^[0-9a-f]{64}$ ||
        ! "$parent_source_sha256" =~ ^[0-9a-f]{64}$ ||
        ! "$marker_sha256" =~ ^[0-9a-f]{64}$ ||
        ! "$current_source_sha256" =~ ^[0-9a-f]{64}$ ]]; then
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "parent Git or digest metadata is missing, padded, or malformed"
    return 1
  fi

  if [[ ! -f "$root_dir/scripts/v934/step4/coverage-contract.json" ||
        -L "$root_dir/scripts/v934/step4/coverage-contract.json" ]]; then
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "current Step 4 coverage contract is missing or symlinked"
    return 1
  fi
  current_contract_sha256="$(sha256sum \
    "$root_dir/scripts/v934/step4/coverage-contract.json" | cut -d' ' -f1)" || {
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "cannot hash the current Step 4 coverage contract"
    return 1
  }
  if [[ "$parent_contract_sha256" != "$current_contract_sha256" ]]; then
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "parent Step 4 coverage contract digest is stale"
    return 1
  fi

  current_head="$(git -C "$root_dir" rev-parse --verify 'HEAD^{commit}' 2>/dev/null)" || {
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "cannot resolve current Git HEAD"
    return 1
  }
  if [[ "$parent_git_head" != "$current_head" ]]; then
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "parent Git HEAD is stale"
    return 1
  fi
  if [[ "$parent_source_sha256" != "$current_source_sha256" ]]; then
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "parent source seal is stale"
    return 1
  fi

  expected_marker="$root_dir/target/v934-step4-coverage/runs/$run_id/run-context.json"
  if [[ "$marker_path" != "$expected_marker" || ! -f "$marker_path" || -L "$marker_path" ]]; then
    v934_step4_authority_error "$log_prefix" E_PARENT_CONTEXT \
      "parent marker is missing, symlinked, or not at its canonical path"
    return 1
  fi

  git_dir="$(git -C "$root_dir" rev-parse --absolute-git-dir 2>/dev/null)" || {
    v934_step4_authority_error "$log_prefix" E_AUTHORITY_LOCK \
      "cannot resolve repository Git directory"
    return 1
  }
  expected_lock="$git_dir/v934-step2-authority.lock"
  if [[ ! -f "$expected_lock" || -L "$expected_lock" ]]; then
    v934_step4_authority_error "$log_prefix" E_AUTHORITY_LOCK \
      "canonical authority lock is missing or symlinked"
    return 1
  fi

  # One Python process validates the marker and descriptor so there is no gap
  # between the independent-OFD exclusion proof and the same-OFD re-lock proof.
  if ! python3 - \
      "$root_dir" "$marker_path" "$marker_sha256" "$run_id" \
      "$parent_git_head" "$parent_contract_sha256" "$parent_source_sha256" \
      "$lock_fd" "$expected_lock" <<'PY'
import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import time


class ValidationError(RuntimeError):
    pass


def reject(message: str) -> None:
    raise ValidationError(message)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            reject(f"duplicate marker key: {key}")
        result[key] = value
    return result


def reject_constant(value):
    reject(f"non-finite JSON number is forbidden: {value}")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def require_real_components(root: Path, path: Path, label: str) -> None:
    try:
        relative = path.relative_to(root)
    except ValueError:
        reject(f"{label} escapes its canonical root")
    current = root
    if current.is_symlink():
        reject(f"{label} root is symlinked")
    for part in relative.parts:
        current = current / part
        try:
            current_stat = current.lstat()
        except OSError as error:
            reject(f"{label} component cannot be inspected: {error.__class__.__name__}")
        if stat.S_ISLNK(current_stat.st_mode):
            reject(f"{label} contains a symlink component")


(
    root_text,
    marker_text,
    expected_marker_sha,
    run_id,
    git_head,
    contract_sha,
    source_sha,
    fd_text,
    lock_text,
) = sys.argv[1:]

root = Path(root_text)
marker_path = Path(marker_text)
lock_path = Path(lock_text)
fd = int(fd_text)

try:
    require_real_components(root, marker_path, "parent marker")
    marker_stat = marker_path.lstat()
    if not stat.S_ISREG(marker_stat.st_mode):
        reject("parent marker is not a real regular file")
    if sha256_file(marker_path) != expected_marker_sha:
        reject("parent marker digest differs")

    try:
        marker = json.loads(
            marker_path.read_text(encoding="utf-8"),
            object_pairs_hook=unique_object,
            parse_constant=reject_constant,
        )
    except (OSError, UnicodeError, json.JSONDecodeError, ValueError) as error:
        reject(f"parent marker is not strict UTF-8 JSON: {error.__class__.__name__}")

    expected_keys = {
        "schema_version",
        "kind",
        "authority_kind",
        "run_id",
        "git_head",
        "contract_sha256",
        "source_sha256",
        "not_before_ns",
        "started_at",
    }
    if type(marker) is not dict or set(marker) != expected_keys:
        reject("parent marker fields differ from the Step 4 schema")
    if type(marker["schema_version"]) is not int or marker["schema_version"] != 1:
        reject("parent marker schema version differs")
    if marker["kind"] != "v934-step4-run-context":
        reject("parent marker kind differs")
    if marker["authority_kind"] != "step4-coverage":
        reject("parent marker authority kind differs")
    if marker["run_id"] != run_id:
        reject("parent marker run id differs")
    if marker["git_head"] != git_head:
        reject("parent marker Git HEAD differs")
    if marker["contract_sha256"] != contract_sha:
        reject("parent marker contract digest differs")
    if marker["source_sha256"] != source_sha:
        reject("parent marker source digest differs")
    if type(marker["not_before_ns"]) is not int or marker["not_before_ns"] <= 0:
        reject("parent marker not-before boundary is invalid")
    if marker["not_before_ns"] > time.time_ns():
        reject("parent marker not-before boundary is in the future")
    started_at = marker["started_at"]
    if type(started_at) is not str or re.fullmatch(
        r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z", started_at
    ) is None:
        reject("parent marker start timestamp is malformed")
    try:
        dt.datetime.strptime(started_at, "%Y-%m-%dT%H:%M:%SZ")
    except ValueError:
        reject("parent marker start timestamp is invalid")

    require_real_components(Path(lock_path.anchor), lock_path, "authority lock")
    lock_stat = lock_path.lstat()
    descriptor_stat = os.fstat(fd)
    if not stat.S_ISREG(lock_stat.st_mode) or not stat.S_ISREG(descriptor_stat.st_mode):
        reject("authority lock or inherited descriptor is not a regular file")
    if (descriptor_stat.st_dev, descriptor_stat.st_ino) != (
        lock_stat.st_dev,
        lock_stat.st_ino,
    ):
        reject("inherited descriptor does not reference the canonical authority lock")
    if not os.get_inheritable(fd):
        reject("inherited authority descriptor has close-on-exec set")
    if (fcntl.fcntl(fd, fcntl.F_GETFL) & os.O_ACCMODE) == os.O_RDONLY:
        reject("inherited authority descriptor is read-only")

    independent_fd = os.open(lock_path, os.O_RDWR | os.O_APPEND)
    try:
        try:
            fcntl.flock(independent_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            pass
        else:
            fcntl.flock(independent_fd, fcntl.LOCK_UN)
            reject("canonical authority lock was not owned before validation")
    finally:
        os.close(independent_fd)

    try:
        # flock is associated with the open-file description. This succeeds
        # only for the inherited parent OFD while independent opens stay out.
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        reject("inherited descriptor is not the parent-owned lock OFD")
except (OSError, ValidationError) as error:
    print(str(error), file=sys.stderr)
    raise SystemExit(1)
PY
  then
    v934_step4_authority_error "$log_prefix" E_AUTHORITY_LOCK \
      "inherited marker or authority-lock proof failed"
    return 1
  fi

  # Retain the export attribute when a Step 4 child is itself a parent runner.
  export V934_AUTHORITY_LOCK_FD
}
