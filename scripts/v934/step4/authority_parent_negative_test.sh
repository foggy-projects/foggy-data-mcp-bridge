#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
LIBRARY="$SCRIPT_DIR/authority_parent_lib.sh"
[[ -f "$LIBRARY" && ! -L "$LIBRARY" ]] || {
  echo "[v934-step4-authority-test] ERROR: authority library missing or symlinked" >&2
  exit 1
}

# shellcheck source=scripts/v934/step4/authority_parent_lib.sh
source "$LIBRARY"

fail() {
  echo "[v934-step4-authority-test] ERROR: $*" >&2
  exit 1
}

sha256_file() {
  python3 - "$1" <<'PY'
import hashlib
from pathlib import Path
import sys

print(hashlib.sha256(Path(sys.argv[1]).read_bytes()).hexdigest())
PY
}

write_marker() {
  local marker="$1" run_id="$2" git_head="$3" contract_sha="$4" source_sha="$5"
  local extra_key="${6:-false}"
  mkdir -p -- "$(dirname "$marker")"
  python3 - "$marker" "$run_id" "$git_head" "$contract_sha" "$source_sha" "$extra_key" <<'PY'
import datetime as dt
import json
import os
from pathlib import Path
import sys
import time

path = Path(sys.argv[1])
marker = {
    "schema_version": 1,
    "kind": "v934-step4-run-context",
    "authority_kind": "step4-coverage",
    "run_id": sys.argv[2],
    "git_head": sys.argv[3],
    "contract_sha256": sys.argv[4],
    "source_sha256": sys.argv[5],
    "not_before_ns": time.time_ns(),
    "started_at": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
}
if sys.argv[6] == "true":
    marker["unexpected"] = "forbidden"
temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
temporary.write_text(json.dumps(marker, sort_keys=True) + "\n", encoding="utf-8")
os.replace(temporary, path)
PY
}

expect_failure() {
  local name="$1"
  shift
  local output="$TMP_ROOT/$name.log"
  if ("$@") >"$output" 2>&1; then
    fail "negative probe unexpectedly passed: $name"
  fi
  grep -q 'ERROR E_' "$output" || fail "negative probe lacked fail-closed error: $name"
  printf '%s\tpassed\n' "$name"
}

probe_wrong_mode() {
  V934_AUTHORITY_LOCK_MODE=" inherited"
  v934_step4_validate_inherited_authority "$REPO" probe "$SOURCE_SHA"
}

probe_stale_source() {
  V934_PARENT_SOURCE_SHA256="$STALE_SHA"
  v934_step4_validate_inherited_authority "$REPO" probe "$SOURCE_SHA"
}

probe_stale_head() {
  V934_PARENT_GIT_HEAD="0000000000000000000000000000000000000000"
  v934_step4_validate_inherited_authority "$REPO" probe "$SOURCE_SHA"
}

probe_padded_contract() {
  V934_PARENT_CONTRACT_SHA256=" $CONTRACT_SHA"
  v934_step4_validate_inherited_authority "$REPO" probe "$SOURCE_SHA"
}

probe_missing_parent_run() {
  unset V934_PARENT_RUN_ID
  v934_step4_validate_inherited_authority "$REPO" probe "$SOURCE_SHA"
}

probe_unsafe_run() {
  RUN_ID=../escape
  V934_PARENT_RUN_ID="$RUN_ID"
  v934_step4_validate_inherited_authority "$REPO" probe "$SOURCE_SHA"
}

probe_untracked_source() {
  local status
  printf 'untracked source\n' > "$REPO/UntrackedSource.java"
  if v934_step4_validate_inherited_authority "$REPO" probe "$SOURCE_SHA"; then
    status=0
  else
    status=$?
  fi
  rm -f -- "$REPO/UntrackedSource.java"
  return "$status"
}

probe_stale_marker_hash() {
  V934_PARENT_OUTER_MARKER_SHA256="$STALE_SHA"
  v934_step4_validate_inherited_authority "$REPO" probe "$SOURCE_SHA"
}

probe_wrong_marker_path() {
  V934_PARENT_OUTER_MARKER_PATH="$REPO/noncanonical-run-context.json"
  v934_step4_validate_inherited_authority "$REPO" probe "$SOURCE_SHA"
}

probe_wrong_descriptor() {
  local wrong_lock="$REPO/.git/not-authority.lock"
  local wrong_fd
  exec {wrong_fd}>>"$wrong_lock"
  flock -n "$wrong_fd"
  V934_AUTHORITY_LOCK_FD="$wrong_fd"
  export V934_AUTHORITY_LOCK_FD
  v934_step4_validate_inherited_authority "$REPO" probe "$SOURCE_SHA"
}

probe_independent_descriptor() {
  local independent_fd
  exec {independent_fd}>>"$LOCK_PATH"
  V934_AUTHORITY_LOCK_FD="$independent_fd"
  export V934_AUTHORITY_LOCK_FD
  v934_step4_validate_inherited_authority "$REPO" probe "$SOURCE_SHA"
}

probe_extra_marker_field() {
  RUN_ID=extra-field
  V934_PARENT_RUN_ID="$RUN_ID"
  V934_PARENT_OUTER_MARKER_PATH="$RUNS_ROOT/$RUN_ID/run-context.json"
  V934_PARENT_OUTER_MARKER_SHA256="$(sha256_file "$V934_PARENT_OUTER_MARKER_PATH")"
  v934_step4_validate_inherited_authority "$REPO" probe "$SOURCE_SHA"
}

probe_symlinked_marker_ancestor() {
  RUN_ID=symlinked-run
  V934_PARENT_RUN_ID="$RUN_ID"
  V934_PARENT_OUTER_MARKER_PATH="$RUNS_ROOT/$RUN_ID/run-context.json"
  V934_PARENT_OUTER_MARKER_SHA256="$(sha256_file "$V934_PARENT_OUTER_MARKER_PATH")"
  v934_step4_validate_inherited_authority "$REPO" probe "$SOURCE_SHA"
}

probe_unlocked_descriptor() {
  v934_step4_validate_inherited_authority "$REPO" probe "$SOURCE_SHA"
}

for command_name in flock git grep mktemp python3; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/v934-step4-authority.XXXXXX")"
trap 'rm -rf -- "$TMP_ROOT"' EXIT
REPO="$TMP_ROOT/repo"
git init -q "$REPO"
git -C "$REPO" config user.name v934-authority-test
git -C "$REPO" config user.email v934-authority-test@example.invalid
printf '/target/\n/noncanonical-run-context.json\n' > "$REPO/.gitignore"
printf 'fixture\n' > "$REPO/tracked.txt"
mkdir -p "$REPO/scripts/v934/step4"
printf '{"kind":"fixture"}\n' > "$REPO/scripts/v934/step4/coverage-contract.json"
git -C "$REPO" add .gitignore tracked.txt scripts/v934/step4/coverage-contract.json
git -C "$REPO" commit -q -m fixture

RUN_ID=positive
RUNS_ROOT="$REPO/target/v934-step4-coverage/runs"
MARKER="$RUNS_ROOT/$RUN_ID/run-context.json"
LOCK_PATH="$REPO/.git/v934-step2-authority.lock"
GIT_HEAD="$(git -C "$REPO" rev-parse --verify 'HEAD^{commit}')"
CONTRACT_SHA="$(sha256_file "$REPO/scripts/v934/step4/coverage-contract.json")"
SOURCE_SHA="$(printf source | python3 -c 'import hashlib,sys; print(hashlib.sha256(sys.stdin.buffer.read()).hexdigest())')"
STALE_SHA="$(printf stale | python3 -c 'import hashlib,sys; print(hashlib.sha256(sys.stdin.buffer.read()).hexdigest())')"

write_marker "$MARKER" "$RUN_ID" "$GIT_HEAD" "$CONTRACT_SHA" "$SOURCE_SHA"
write_marker "$RUNS_ROOT/extra-field/run-context.json" extra-field "$GIT_HEAD" "$CONTRACT_SHA" "$SOURCE_SHA" true
mkdir -p "$TMP_ROOT/symlink-target"
write_marker "$TMP_ROOT/symlink-target/run-context.json" symlinked-run "$GIT_HEAD" "$CONTRACT_SHA" "$SOURCE_SHA"
ln -s "$TMP_ROOT/symlink-target" "$RUNS_ROOT/symlinked-run"
cp -- "$MARKER" "$REPO/noncanonical-run-context.json"

exec {V934_AUTHORITY_LOCK_FD}>>"$LOCK_PATH"
flock -n "$V934_AUTHORITY_LOCK_FD" || fail "cannot acquire fixture authority lock"
export V934_AUTHORITY_LOCK_FD
export V934_AUTHORITY_LOCK_MODE=inherited
export V934_PARENT_AUTHORITY_KIND=step4-coverage
export V934_PARENT_RUN_ID="$RUN_ID"
export V934_PARENT_GIT_HEAD="$GIT_HEAD"
export V934_PARENT_CONTRACT_SHA256="$CONTRACT_SHA"
export V934_PARENT_SOURCE_SHA256="$SOURCE_SHA"
export V934_PARENT_OUTER_MARKER_PATH="$MARKER"
V934_PARENT_OUTER_MARKER_SHA256="$(sha256_file "$MARKER")"
export V934_PARENT_OUTER_MARKER_SHA256

v934_step4_validate_inherited_authority "$REPO" positive "$SOURCE_SHA" || \
  fail "positive inherited-authority proof failed"
printf 'positive\tpassed\n'

exec {INDEPENDENT_FD}>>"$LOCK_PATH"
if flock -n "$INDEPENDENT_FD"; then
  fail "independent descriptor acquired the parent-owned lock"
fi
exec {INDEPENDENT_FD}>&-
printf 'independent-lock-exclusion\tpassed\n'

expect_failure padded-mode probe_wrong_mode
expect_failure stale-source probe_stale_source
expect_failure stale-head probe_stale_head
expect_failure padded-contract probe_padded_contract
expect_failure missing-parent-run probe_missing_parent_run
expect_failure unsafe-run probe_unsafe_run
expect_failure untracked-source probe_untracked_source
expect_failure stale-marker-hash probe_stale_marker_hash
expect_failure wrong-marker-path probe_wrong_marker_path
expect_failure wrong-descriptor probe_wrong_descriptor
expect_failure independent-descriptor probe_independent_descriptor
expect_failure extra-marker-field probe_extra_marker_field
expect_failure symlinked-marker-ancestor probe_symlinked_marker_ancestor

flock -u "$V934_AUTHORITY_LOCK_FD"
expect_failure unlocked-descriptor probe_unlocked_descriptor

echo "[v934-step4-authority-test] PASS positive=2 negative=14"
