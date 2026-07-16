#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SCRIPT_PATH="$ROOT_DIR/scripts/verify-v934-step4-coverage.sh"
STEP4_DIR="$ROOT_DIR/scripts/v934/step4"
AUTHORITY_LIB="$ROOT_DIR/scripts/v934/authority_runner_lib.sh"
STEP4_AUTHORITY_LIB="$STEP4_DIR/authority_parent_lib.sh"
COVERAGE_TOOL="$STEP4_DIR/coverage_tool.py"
REPORT_VIEW_TOOL="$STEP4_DIR/step2_report_view_tool.py"
REPORT_INVENTORY_TOOL="$STEP4_DIR/report_inventory_tool.py"
COVERAGE_REPORT_RUNNER="$STEP4_DIR/coverage_report_runner.sh"
COVERAGE_EXEC_TOOL="$STEP4_DIR/coverage_exec_tool.py"
COVERAGE_XML_TOOL="$STEP4_DIR/coverage_xml_tool.py"
COVERAGE_CONTRACT_NEGATIVE_TOOL="$STEP4_DIR/coverage_contract_negative_tool.py"
TOOLCHAIN_RECEIPT_TOOL="$STEP4_DIR/toolchain_receipt_tool.py"
COVERAGE_CONTRACT="$STEP4_DIR/coverage-contract.json"
COVERAGE_THRESHOLDS="$STEP4_DIR/coverage-thresholds.json"
SUCCESSOR_OVERLAY_TOOL="$STEP4_DIR/successor/overlay_tool.py"
AUTHORITY_NEGATIVE="$STEP4_DIR/authority_parent_negative_test.sh"
STEP1_FREEZE="$ROOT_DIR/scripts/v934/contract-freeze.json"
JACOCO_AGENT_JAR="$HOME/.m2/repository/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar"
JACOCO_AGENT_SHA256="115e8e6e6593ca3a9892dfef695df4d487c706e59e71e64dc0ab95716ee02622"

usage() {
  cat <<'EOF'
Usage:
  scripts/verify-v934-step4-coverage.sh [diagnostic] [RUN_ID]
  scripts/verify-v934-step4-coverage.sh formal [RUN_ID]

The current threshold successor is diagnostic-pending. Therefore this runner
only executes a fresh all-lane diagnostic and never creates a Step 4 acceptance
candidate. The formal command fails closed until a separate threshold-freeze
change enables a fresh formal workflow.
EOF
}

fail() {
  echo "[v934-step4-coverage] ERROR: $*" >&2
  exit 1
}

sha256_file() {
  sha256sum "$1" | cut -d' ' -f1
}

atomic_env() {
  local output="$1"
  shift
  python3 - "$output" "$@" <<'PY'
import os
from pathlib import Path
import re
import secrets
import stat
import sys


class PublishError(RuntimeError):
    pass


def reject(message: str) -> None:
    raise PublishError(message)


output = Path(sys.argv[1])
rows = sys.argv[2:]
directory_fd = -1
temporary_fd = -1
temporary_name = ""
temporary_exists = False
published = False
published_identity = None

try:
    if not output.is_absolute() or output.name in {"", ".", ".."}:
        reject("output path must be absolute with a safe basename")
    if not rows:
        reject("environment evidence must contain at least one row")
    seen = set()
    for row in rows:
        if "\n" in row or "\r" in row or "=" not in row:
            reject("environment evidence contains a malformed row")
        key, _value = row.split("=", 1)
        if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key) is None:
            reject(f"environment evidence contains an unsafe key: {key!r}")
        if key in seen:
            reject(f"environment evidence contains a duplicate key: {key}")
        seen.add(key)
    payload = "".join(f"{row}\n" for row in rows).encode("utf-8")

    parent = output.parent
    parent_stat = parent.lstat()
    if stat.S_ISLNK(parent_stat.st_mode) or not stat.S_ISDIR(parent_stat.st_mode):
        reject("output parent is not a real directory")
    if parent.resolve(strict=True) != parent:
        reject("output parent contains a symlinked path component")
    directory_flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0)
    directory_flags |= getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    directory_fd = os.open(parent, directory_flags)
    bound_parent_stat = os.fstat(directory_fd)
    if (parent_stat.st_dev, parent_stat.st_ino) != (
        bound_parent_stat.st_dev,
        bound_parent_stat.st_ino,
    ):
        reject("output parent changed while it was opened")
    try:
        os.stat(output.name, dir_fd=directory_fd, follow_symlinks=False)
    except FileNotFoundError:
        pass
    else:
        reject("refusing to overwrite an existing file or symlink")

    temporary_name = (
        f".{output.name}.{os.getpid()}.{secrets.token_hex(12)}.tmp"
    )
    temporary_flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    temporary_flags |= getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    temporary_fd = os.open(
        temporary_name,
        temporary_flags,
        0o600,
        dir_fd=directory_fd,
    )
    temporary_exists = True
    os.fchmod(temporary_fd, 0o644)
    view = memoryview(payload)
    while view:
        written = os.write(temporary_fd, view)
        if written <= 0:
            reject("short write while staging environment evidence")
        view = view[written:]
    os.fsync(temporary_fd)
    temporary_stat = os.fstat(temporary_fd)
    published_identity = (temporary_stat.st_dev, temporary_stat.st_ino)
    os.close(temporary_fd)
    temporary_fd = -1

    os.link(
        temporary_name,
        output.name,
        src_dir_fd=directory_fd,
        dst_dir_fd=directory_fd,
        follow_symlinks=False,
    )
    published = True
    output_stat = os.stat(output.name, dir_fd=directory_fd, follow_symlinks=False)
    if (
        not stat.S_ISREG(output_stat.st_mode)
        or (output_stat.st_dev, output_stat.st_ino) != published_identity
    ):
        reject("published evidence identity differs from its staged inode")
    os.fsync(directory_fd)
    os.unlink(temporary_name, dir_fd=directory_fd)
    temporary_exists = False
    os.fsync(directory_fd)

    current_parent_stat = parent.lstat()
    if (
        stat.S_ISLNK(current_parent_stat.st_mode)
        or (current_parent_stat.st_dev, current_parent_stat.st_ino)
        != (bound_parent_stat.st_dev, bound_parent_stat.st_ino)
    ):
        reject("output parent changed during publication")
    canonical_output_stat = output.lstat()
    if (
        not stat.S_ISREG(canonical_output_stat.st_mode)
        or (canonical_output_stat.st_dev, canonical_output_stat.st_ino)
        != published_identity
    ):
        reject("canonical output changed during publication")
except (OSError, PublishError, UnicodeError) as error:
    if temporary_fd >= 0:
        try:
            os.close(temporary_fd)
        except OSError:
            pass
        temporary_fd = -1
    if directory_fd >= 0 and published and published_identity is not None:
        try:
            current = os.stat(output.name, dir_fd=directory_fd, follow_symlinks=False)
            if (current.st_dev, current.st_ino) == published_identity:
                os.unlink(output.name, dir_fd=directory_fd)
        except OSError:
            pass
    if directory_fd >= 0 and temporary_exists and temporary_name:
        try:
            os.unlink(temporary_name, dir_fd=directory_fd)
        except OSError:
            pass
    if directory_fd >= 0:
        try:
            os.fsync(directory_fd)
        except OSError:
            pass
    print(f"atomic environment publication failed: {error}", file=sys.stderr)
    raise SystemExit(1)
finally:
    if temporary_fd >= 0:
        os.close(temporary_fd)
    if directory_fd >= 0:
        os.close(directory_fd)
PY
}

run_toolchain_stage() {
  local stage="$1" command_name="$2" result exit_code current_receipt_sha tool_sha
  local output="$TOOLCHAIN_REPLAY_ROOT/$stage.env"
  case "$command_name" in
    seal)
      if result="$(python3 "$TOOLCHAIN_RECEIPT_TOOL" seal \
        --repo-root "$ROOT_DIR" \
        --run-id "$RUN_ID" \
        --output "$RUN_ROOT/toolchain-receipt.json")"; then
        :
      else
        exit_code=$?
        printf '%s\n' "$result" >&2
        return "$exit_code"
      fi
      ;;
    verify)
      if result="$(python3 "$TOOLCHAIN_RECEIPT_TOOL" verify \
        --repo-root "$ROOT_DIR" \
        --run-id "$RUN_ID" \
        --receipt "$RUN_ROOT/toolchain-receipt.json")"; then
        :
      else
        exit_code=$?
        printf '%s\n' "$result" >&2
        return "$exit_code"
      fi
      ;;
    *) fail "unsupported toolchain replay command: $command_name" ;;
  esac
  printf '%s\n' "$result"
  require_real_file "$RUN_ROOT/toolchain-receipt.json"
  current_receipt_sha="$(sha256_file "$RUN_ROOT/toolchain-receipt.json")"
  tool_sha="$(sha256_file "$TOOLCHAIN_RECEIPT_TOOL")"
  python3 - "$result" "$command_name" "$RUN_ID" "$current_receipt_sha" <<'PY'
import json
import re
import sys


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise SystemExit(f"duplicate toolchain result key: {key}")
        result[key] = value
    return result


payload = json.loads(sys.argv[1], object_pairs_hook=unique)
expected = {
    "command", "run_id", "sha256", "compiler_realm", "jacoco_realm", "status"
}
if not isinstance(payload, dict) or set(payload) != expected:
    raise SystemExit("toolchain stage result schema differs")
if (
    payload["command"] != sys.argv[2]
    or payload["run_id"] != sys.argv[3]
    or payload["sha256"] != sys.argv[4]
    or re.fullmatch(r"[0-9a-f]{64}", payload["sha256"]) is None
    or payload["compiler_realm"] != 12
    or type(payload["compiler_realm"]) is not int
    or payload["jacoco_realm"] != 12
    or type(payload["jacoco_realm"]) is not int
    or payload["status"] != "passed"
):
    raise SystemExit("toolchain stage result differs from the current receipt")
PY
  if [[ -z "$TOOLCHAIN_RECEIPT_SHA" ]]; then
    [[ "$command_name" == seal && "$stage" == pre-compile-seal ]] || \
      fail "toolchain receipt identity was not established by the pre-compile seal"
    TOOLCHAIN_RECEIPT_SHA="$current_receipt_sha"
  else
    [[ "$current_receipt_sha" == "$TOOLCHAIN_RECEIPT_SHA" ]] || \
      fail "toolchain receipt changed at replay stage: $stage"
  fi
  atomic_env "$output" \
    "schema_version=1" \
    "kind=v934-step4-toolchain-replay-stage" \
    "stage=$stage" \
    "command=$command_name" \
    "run_id=$RUN_ID" \
    "receipt_sha256=$current_receipt_sha" \
    "tool_sha256=$tool_sha" \
    "compiler_realm=12" \
    "jacoco_realm=12" \
    "result=passed"
}

validate_toolchain_stage_evidence() {
  local path="$1" expected_stage="$2" expected_command="$3"
  require_real_file "$path"
  python3 - \
    "$path" "$expected_stage" "$expected_command" "$RUN_ID" \
    "$TOOLCHAIN_RECEIPT_SHA" "$(sha256_file "$TOOLCHAIN_RECEIPT_TOOL")" <<'PY'
from pathlib import Path
import re
import sys


path = Path(sys.argv[1])
rows = {}
for line in path.read_text(encoding="utf-8").splitlines():
    if not line or "=" not in line:
        raise SystemExit(f"malformed toolchain replay evidence: {path}")
    key, value = line.split("=", 1)
    if key in rows:
        raise SystemExit(f"duplicate toolchain replay evidence key: {key}")
    rows[key] = value
expected = {
    "schema_version", "kind", "stage", "command", "run_id",
    "receipt_sha256", "tool_sha256", "compiler_realm", "jacoco_realm",
    "result",
}
if set(rows) != expected:
    raise SystemExit(f"toolchain replay evidence schema differs: {path}")
if (
    rows["schema_version"] != "1"
    or rows["kind"] != "v934-step4-toolchain-replay-stage"
    or rows["stage"] != sys.argv[2]
    or rows["command"] != sys.argv[3]
    or rows["run_id"] != sys.argv[4]
    or rows["receipt_sha256"] != sys.argv[5]
    or rows["tool_sha256"] != sys.argv[6]
    or re.fullmatch(r"[0-9a-f]{64}", rows["receipt_sha256"]) is None
    or re.fullmatch(r"[0-9a-f]{64}", rows["tool_sha256"]) is None
    or rows["compiler_realm"] != "12"
    or rows["jacoco_realm"] != "12"
    or rows["result"] != "passed"
):
    raise SystemExit(f"toolchain replay evidence binding differs: {path}")
PY
}

require_real_file() {
  local path="$1"
  [[ -f "$path" && ! -L "$path" ]] || fail "required real file missing: $path"
}

ensure_real_directory() {
  local path="$1"
  if [[ -e "$path" || -L "$path" ]]; then
    [[ -d "$path" && ! -L "$path" ]] || fail "directory is missing, non-directory, or symlinked: $path"
  else
    mkdir -- "$path"
  fi
  [[ "$(cd "$path" && pwd -P)" == "$path" ]] || fail "directory is not canonical: $path"
}

require_no_symlink_descendants() {
  local root="$1" label="$2" found
  found="$(find "$root" -type l -print -quit)" || fail "cannot inspect symlink descendants: $label"
  [[ -z "$found" ]] || fail "class tree contains a symlink descendant: $label"
}

require_nonempty_class_tree() {
  local root="$1" label="$2" found
  found="$(find "$root" -type f -name '*.class' -print -quit)" || fail "cannot inspect class files: $label"
  [[ -n "$found" ]] || fail "fresh class tree is empty: $label"
}

validate_run_log_binding() {
  python3 - "$RUN_LOG_FILE_FD" "$RUN_ROOT/run.log" <<'PY'
import os
from pathlib import Path
import stat
import sys

fd = int(sys.argv[1])
path = Path(sys.argv[2])
try:
    descriptor_stat = os.fstat(fd)
    path_stat = path.lstat()
    parent_stat = path.parent.lstat()
except OSError as error:
    print(f"run log binding cannot be inspected: {error}", file=sys.stderr)
    raise SystemExit(1)
if (
    not path.is_absolute()
    or stat.S_ISLNK(parent_stat.st_mode)
    or not stat.S_ISDIR(parent_stat.st_mode)
    or path.parent.resolve(strict=True) != path.parent
    or stat.S_ISLNK(path_stat.st_mode)
    or not stat.S_ISREG(path_stat.st_mode)
    or not stat.S_ISREG(descriptor_stat.st_mode)
    or (path_stat.st_dev, path_stat.st_ino)
    != (descriptor_stat.st_dev, descriptor_stat.st_ino)
):
    print("run log path no longer binds to its no-clobber descriptor", file=sys.stderr)
    raise SystemExit(1)
PY
}

open_run_log_file() {
  local noclobber_was_set=false
  if [[ -o noclobber ]]; then
    noclobber_was_set=true
  else
    set -o noclobber
  fi
  if ! exec {RUN_LOG_FILE_FD}> "$RUN_ROOT/run.log"; then
    [[ "$noclobber_was_set" == true ]] || set +o noclobber
    return 1
  fi
  [[ "$noclobber_was_set" == true ]] || set +o noclobber
  validate_run_log_binding
}

close_run_log() {
  local attempt code=0
  if [[ "$RUN_LOG_OPEN" == true ]]; then
    exec 1>&3 2>&4
    RUN_LOG_OPEN=false
    exec 3>&- 4>&-
  fi
  if [[ -n "$RUN_LOG_TEE_PID" ]]; then
    for ((attempt = 0; attempt < 100; attempt++)); do
      kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || break
      sleep 0.05
    done
    if kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1; then
      code=124
      kill -TERM "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
      for ((attempt = 0; attempt < 20; attempt++)); do
        kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || break
        sleep 0.05
      done
      kill -0 "$RUN_LOG_TEE_PID" >/dev/null 2>&1 && \
        kill -KILL "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
      wait "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
    elif ! wait "$RUN_LOG_TEE_PID"; then
      code=1
    fi
    RUN_LOG_TEE_PID=""
  fi
  [[ -z "$RUN_LOG_FIFO" ]] || rm -f -- "$RUN_LOG_FIFO"
  RUN_LOG_FIFO=""
  if [[ -n "$RUN_LOG_FILE_FD" ]]; then
    validate_run_log_binding || code=1
    exec {RUN_LOG_FILE_FD}>&-
    RUN_LOG_FILE_FD=""
  fi
  return "$code"
}

active_child_has_own_group() {
  local pgid
  [[ -n "$ACTIVE_CHILD_PID" ]] || return 1
  pgid="$(ps -o pgid= -p "$ACTIVE_CHILD_PID" 2>/dev/null | tr -d '[:space:]')" || return 1
  [[ "$pgid" == "$ACTIVE_CHILD_PID" ]]
}

active_child_group_alive() {
  [[ -n "$ACTIVE_CHILD_PID" && "$ACTIVE_CHILD_GROUP_ESTABLISHED" == true ]] || return 1
  kill -0 -- "-$ACTIVE_CHILD_PID" >/dev/null 2>&1
}

terminate_active_child() {
  local signal_name="${1:-TERM}" attempt
  [[ -n "$ACTIVE_CHILD_PID" ]] || return 0
  if [[ "$ACTIVE_CHILD_GROUP_ESTABLISHED" == true ]]; then
    kill -s "$signal_name" -- "-$ACTIVE_CHILD_PID" >/dev/null 2>&1 || true
  fi
  if kill -0 "$ACTIVE_CHILD_PID" >/dev/null 2>&1; then
    kill -s "$signal_name" -- "$ACTIVE_CHILD_PID" >/dev/null 2>&1 || true
  fi
  for ((attempt = 0; attempt < 200; attempt++)); do
    if ! kill -0 "$ACTIVE_CHILD_PID" >/dev/null 2>&1 && ! active_child_group_alive; then
      break
    fi
    sleep 0.1
  done
  if active_child_group_alive; then
    kill -KILL -- "-$ACTIVE_CHILD_PID" >/dev/null 2>&1 || true
  fi
  kill -0 "$ACTIVE_CHILD_PID" >/dev/null 2>&1 && \
    kill -KILL -- "$ACTIVE_CHILD_PID" >/dev/null 2>&1 || true
  wait "$ACTIVE_CHILD_PID" >/dev/null 2>&1 || true
  ACTIVE_CHILD_PID=""
  ACTIVE_CHILD_GROUP_ESTABLISHED=false
}

remove_labeled_resources() {
  local label="$1" identifiers identifier code=0
  if ! identifiers="$(docker ps -aq --filter "label=$label")"; then
    code=1
    identifiers=""
  fi
  for identifier in $identifiers; do
    docker rm -fv "$identifier" >/dev/null 2>&1 || code=1
  done
  if ! identifiers="$(docker volume ls -q --filter "label=$label")"; then
    code=1
    identifiers=""
  fi
  for identifier in $identifiers; do
    docker volume rm -f "$identifier" >/dev/null 2>&1 || code=1
  done
  if ! identifiers="$(docker network ls -q --filter "label=$label")"; then
    code=1
    identifiers=""
  fi
  for identifier in $identifiers; do
    docker network rm "$identifier" >/dev/null 2>&1 || code=1
  done
  return "$code"
}

count_labeled_resources() {
  local label="$1" kind="$2" output
  case "$kind" in
    container) output="$(docker ps -aq --filter "label=$label")" || return 1 ;;
    volume) output="$(docker volume ls -q --filter "label=$label")" || return 1 ;;
    network) output="$(docker network ls -q --filter "label=$label")" || return 1 ;;
    *) return 1 ;;
  esac
  sed '/^$/d' <<< "$output" | wc -l | tr -d ' '
}

cleanup_all_resources() {
  local code=0 database scope_hash project label count
  local containers=0 volumes=0 networks=0
  docker info >/dev/null 2>&1 || return 1
  remove_labeled_resources "com.foggy.v934.external-run=$RUN_ID" || code=1
  remove_labeled_resources "com.foggy.v934.preagg-run=$RUN_ID" || code=1
  for database in mysql57 mysql8 postgres15 sqlserver2022; do
    scope_hash="$(printf '%s\n' "$RUN_ID|$database" | sha256sum | cut -c1-12)"
    project="v934db-${database}-${scope_hash}"
    remove_labeled_resources "com.docker.compose.project=$project" || code=1
  done
  for label in \
    "com.foggy.v934.external-run=$RUN_ID" \
    "com.foggy.v934.preagg-run=$RUN_ID"; do
    count="$(count_labeled_resources "$label" container)" || { code=1; count=999; }
    containers=$((containers + count))
    count="$(count_labeled_resources "$label" volume)" || { code=1; count=999; }
    volumes=$((volumes + count))
    count="$(count_labeled_resources "$label" network)" || { code=1; count=999; }
    networks=$((networks + count))
  done
  for database in mysql57 mysql8 postgres15 sqlserver2022; do
    scope_hash="$(printf '%s\n' "$RUN_ID|$database" | sha256sum | cut -c1-12)"
    project="v934db-${database}-${scope_hash}"
    label="com.docker.compose.project=$project"
    count="$(count_labeled_resources "$label" container)" || { code=1; count=999; }
    containers=$((containers + count))
    count="$(count_labeled_resources "$label" volume)" || { code=1; count=999; }
    volumes=$((volumes + count))
    count="$(count_labeled_resources "$label" network)" || { code=1; count=999; }
    networks=$((networks + count))
  done
  [[ "$containers" -eq 0 && "$volumes" -eq 0 && "$networks" -eq 0 ]] || code=1
  atomic_env "$RUN_ROOT/cleanup.env" \
    "container_residue=$containers" \
    "volume_residue=$volumes" \
    "network_residue=$networks" \
    "status=$([[ "$code" -eq 0 ]] && printf passed || printf failed)" || code=1
  return "$code"
}

write_run_status() {
  local exit_code="$1" finished_at status summary_sha256=absent
  finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)" || return 1
  status=failed
  if [[ "$exit_code" -eq 0 && "$PHASE" == completed ]]; then
    status=diagnostic-observed
    [[ -f "$RUN_ROOT/summary.env" && ! -L "$RUN_ROOT/summary.env" ]] || return 1
    [[ "$(sha256_file "$RUN_ROOT/toolchain-receipt.json")" == "$TOOLCHAIN_RECEIPT_SHA" ]] || return 1
    summary_sha256="$(sha256_file "$RUN_ROOT/summary.env")" || return 1
  fi
  atomic_env "$RUN_ROOT/run-status.env" \
    "run_id=$RUN_ID" \
    "mode=$MODE" \
    "git_head=$GIT_HEAD" \
    "started_at=$STARTED_AT" \
    "finished_at=$finished_at" \
    "last_phase=$PHASE" \
    "exit_code=$exit_code" \
    "source_before_sha256=$SOURCE_BEFORE" \
    "source_after_sha256=$SOURCE_AFTER" \
    "outer_marker_sha256=$OUTER_MARKER_SHA256" \
    "toolchain_receipt_sha256=${TOOLCHAIN_RECEIPT_SHA:-absent}" \
    "summary_sha256=$summary_sha256" \
    "status=$status"
}

finalize_run() {
  local exit_code="$1" finalizer_code=0
  trap '' INT TERM HUP
  trap - EXIT
  set +e
  terminate_active_child TERM
  if [[ "$CLEANUP_DONE" != true ]]; then
    cleanup_all_resources || finalizer_code=1
  fi
  if [[ "$RUN_LOG_OPEN" == true || -n "$RUN_LOG_TEE_PID" || -n "$RUN_LOG_FILE_FD" ]]; then
    close_run_log || finalizer_code=1
  fi
  [[ "$exit_code" -ne 0 || "$PHASE" == completed ]] || exit_code=1
  [[ "$finalizer_code" -eq 0 ]] || exit_code=1
  if [[ "$exit_code" -ne 0 ]]; then
    rm -f -- "$RUN_ROOT/summary.env"
  fi
  if ! write_run_status "$exit_code"; then
    exit_code=1
    rm -f -- "$RUN_ROOT/summary.env"
  fi
  if [[ "$exit_code" -eq 0 ]]; then
    echo "[v934-step4-coverage] DIAGNOSTIC PASS run=$RUN_ID exec=23/48 reports=773/59/5707 addon=2/6 acceptance=not-generated"
  else
    echo "[v934-step4-coverage] FAILED run=$RUN_ID phase=$PHASE" >&2
  fi
  exit "$exit_code"
}

handle_signal() {
  local signal_name="$1" exit_code="$2"
  trap '' INT TERM HUP
  terminate_active_child "$signal_name"
  exit "$exit_code"
}

run_child() {
  local child="$1" exit_code=0 attempt
  PHASE="child-$child"
  echo "[v934-step4-coverage] running child=$child"
  python3 "$COVERAGE_TOOL" launch-child \
    --repo-root "$ROOT_DIR" \
    --child "$child" \
    --run-id "$RUN_ID" \
    --lock-fd "$V934_AUTHORITY_LOCK_FD" &
  ACTIVE_CHILD_PID=$!
  for ((attempt = 0; attempt < 100; attempt++)); do
    kill -0 "$ACTIVE_CHILD_PID" >/dev/null 2>&1 || break
    active_child_has_own_group && break
    sleep 0.05
  done
  if active_child_has_own_group; then
    ACTIVE_CHILD_GROUP_ESTABLISHED=true
  fi
  if kill -0 "$ACTIVE_CHILD_PID" >/dev/null 2>&1 && ! active_child_has_own_group; then
    terminate_active_child TERM
    fail "child failed to establish its canonical process group: $child"
  fi
  if wait "$ACTIVE_CHILD_PID"; then
    exit_code=0
  else
    exit_code=$?
  fi
  if [[ "$exit_code" -ne 0 ]]; then
    terminate_active_child TERM
    fail "child failed: $child rc=$exit_code"
  fi
  if active_child_group_alive; then
    terminate_active_child TERM
    fail "child returned with live process-group residue: $child"
  fi
  ACTIVE_CHILD_PID=""
  ACTIVE_CHILD_GROUP_ESTABLISHED=false
}

MODE=diagnostic
RUN_ID=""
case "$#" in
  0) ;;
  1)
    case "$1" in
      diagnostic|formal) MODE="$1" ;;
      -h|--help) usage; exit 0 ;;
      *) RUN_ID="$1" ;;
    esac
    ;;
  2)
    MODE="$1"
    RUN_ID="$2"
    ;;
  *) usage >&2; exit 2 ;;
esac
[[ "$MODE" == diagnostic || "$MODE" == formal ]] || fail "mode must be diagnostic or formal"
RUN_ID="${RUN_ID:-step4-coverage-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
[[ "$RUN_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ && "$RUN_ID" != . && "$RUN_ID" != .. && "${#RUN_ID}" -le 128 ]] || \
  fail "unsafe run id: $RUN_ID"

if [[ "$MODE" == formal ]]; then
  fail "formal mode is disabled while the Step 4 threshold successor is diagnostic-pending"
fi

for command_name in cmp cut date docker find flock git grep mkfifo mvn ps python3 rg sed sha256sum sleep tee tr wc; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done
for required_file in \
  "$SCRIPT_PATH" "$AUTHORITY_LIB" "$STEP4_AUTHORITY_LIB" "$COVERAGE_TOOL" \
  "$REPORT_VIEW_TOOL" "$REPORT_INVENTORY_TOOL" "$COVERAGE_REPORT_RUNNER" \
  "$COVERAGE_EXEC_TOOL" "$COVERAGE_XML_TOOL" "$COVERAGE_CONTRACT_NEGATIVE_TOOL" \
  "$TOOLCHAIN_RECEIPT_TOOL" \
  "$COVERAGE_CONTRACT" \
  "$COVERAGE_THRESHOLDS" "$SUCCESSOR_OVERLAY_TOOL" "$AUTHORITY_NEGATIVE" \
  "$STEP1_FREEZE" "$JACOCO_AGENT_JAR"; do
  require_real_file "$required_file"
done
[[ "$(sha256_file "$JACOCO_AGENT_JAR")" == "$JACOCO_AGENT_SHA256" ]] || \
  fail "JaCoCo 0.8.12 runtime agent hash differs"
[[ "$(git -C "$ROOT_DIR" rev-parse --show-toplevel)" == "$ROOT_DIR" ]] || \
  fail "repository root differs from the canonical Git worktree root"
[[ -z "$(git -C "$ROOT_DIR" status --porcelain=v1 --untracked-files=all)" ]] || \
  fail "diagnostic requires an exact clean committed HEAD"
docker info >/dev/null 2>&1 || fail "Docker daemon is unavailable"
docker compose version >/dev/null 2>&1 || fail "Docker Compose is unavailable"

for variable_name in \
  V934_AUTHORITY_LOCK_FD V934_PARENT_AUTHORITY_KIND V934_PARENT_RUN_ID \
  V934_PARENT_GIT_HEAD V934_PARENT_CONTRACT_SHA256 V934_PARENT_SOURCE_SHA256 \
  V934_PARENT_OUTER_MARKER_SHA256 V934_PARENT_OUTER_MARKER_PATH \
  V934_COVERAGE_EXEC_ROOT V934_COVERAGE_SESSION_PREFIX V934_COVERAGE_AGENT_JAR \
  V934_JACOCO_CHILD_AGENT_JAR V934_JACOCO_CHILD_EXEC_FILE \
  V934_JACOCO_CHILD_SESSION_PREFIX; do
  [[ ! -v "$variable_name" ]] || fail "standalone Step 4 runner received inherited environment: $variable_name"
done
[[ "${V934_AUTHORITY_LOCK_MODE:-standalone}" == standalone ]] || \
  fail "the top-level Step 4 runner only accepts standalone authority mode"
for variable_name in MAVEN_ARGS MAVEN_CONFIG; do
  [[ -z "${!variable_name:-}" ]] || fail "$variable_name must be empty for a diagnostic coverage run"
done
if [[ "${MAVEN_OPTS:-}" =~ (^|[[:space:],])(-T|--threads)([[:space:]=,0-9C]|$) ]] ||
   [[ "${MAVEN_OPTS:-}" =~ (^|[[:space:],])!?coverage([[:space:],]|$) ]] ||
   [[ "${MAVEN_OPTS:-}" =~ (v934-coverage|jacoco\.|v934\.coverage\.|(^|[[:space:]])-D(argLine|test|it\.test|failIfNoTests|surefire\.|failsafe\.)) ]]; then
  fail "MAVEN_OPTS contains a forbidden coverage, selector, or parallel override"
fi
for variable_name in JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS; do
  [[ ! "${!variable_name:-}" =~ (-javaagent|jacoco) ]] || \
    fail "$variable_name contains an external Java agent override"
done

THRESHOLD_STATUS="$(python3 - "$COVERAGE_THRESHOLDS" <<'PY'
import json
from pathlib import Path
import sys

def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise SystemExit(f"duplicate threshold key: {key}")
        result[key] = value
    return result

def reject_constant(value):
    raise SystemExit(f"non-finite threshold number: {value}")

payload = json.loads(
    Path(sys.argv[1]).read_text(encoding="utf-8"),
    object_pairs_hook=unique,
    parse_constant=reject_constant,
)
status = payload.get("status") if isinstance(payload, dict) else None
if status != "diagnostic-pending":
    raise SystemExit("threshold successor is not diagnostic-pending")
print(status)
PY
)" || fail "cannot validate the diagnostic threshold successor"
[[ "$THRESHOLD_STATUS" == diagnostic-pending ]] || fail "unexpected threshold status: $THRESHOLD_STATUS"

# The top-level runner is the only owner of the canonical authority lock.
# Every child receives this same inheritable open-file description.
# shellcheck source=scripts/v934/authority_runner_lib.sh
source "$AUTHORITY_LIB"
# shellcheck source=scripts/v934/step4/authority_parent_lib.sh
source "$STEP4_AUTHORITY_LIB"
v934_acquire_authority_lock "$ROOT_DIR" "v934-step4-coverage" || exit 1
export V934_AUTHORITY_LOCK_FD

PHASE=bootstrap
ACTIVE_CHILD_PID=""
ACTIVE_CHILD_GROUP_ESTABLISHED=false
RUN_LOG_FIFO=""
RUN_LOG_TEE_PID=""
RUN_LOG_OPEN=false
RUN_LOG_FILE_FD=""
CLEANUP_DONE=false
SOURCE_BEFORE=""
SOURCE_AFTER=""
OUTER_MARKER_SHA256=""
TOOLCHAIN_RECEIPT_SHA=""
STARTED_AT=""
GIT_HEAD=""

TARGET_ROOT="$ROOT_DIR/target"
COVERAGE_ROOT="$TARGET_ROOT/v934-step4-coverage"
RUNS_ROOT="$COVERAGE_ROOT/runs"
RUN_ROOT="$RUNS_ROOT/$RUN_ID"
EXEC_ROOT="$RUN_ROOT/exec"
NEGATIVE_ROOT="$RUN_ROOT/negative"
TOOLCHAIN_REPLAY_ROOT="$RUN_ROOT/toolchain-replay"
OUTER_MARKER="$RUN_ROOT/run-context.json"
ensure_real_directory "$TARGET_ROOT"
ensure_real_directory "$COVERAGE_ROOT"
ensure_real_directory "$RUNS_ROOT"
[[ ! -e "$RUN_ROOT" && ! -L "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
mkdir -- "$RUN_ROOT"
ensure_real_directory "$RUN_ROOT"

trap 'finalize_run "$?"' EXIT
trap 'handle_signal INT 130' INT
trap 'handle_signal TERM 143' TERM
trap 'handle_signal HUP 129' HUP

mkdir -- "$EXEC_ROOT" "$NEGATIVE_ROOT" "$TOOLCHAIN_REPLAY_ROOT"
ensure_real_directory "$EXEC_ROOT"
ensure_real_directory "$NEGATIVE_ROOT"
ensure_real_directory "$TOOLCHAIN_REPLAY_ROOT"

exec 3>&1 4>&2
open_run_log_file || fail "cannot create a no-clobber run log"
RUN_LOG_FIFO="$RUN_ROOT/.run-log.fifo"
mkfifo -- "$RUN_LOG_FIFO"
(trap '' INT TERM HUP; exec tee -a -- "/proc/self/fd/$RUN_LOG_FILE_FD") < "$RUN_LOG_FIFO" >&3 2>&4 &
RUN_LOG_TEE_PID=$!
RUN_LOG_OPEN=true
exec > "$RUN_LOG_FIFO" 2>&1
rm -f -- "$RUN_LOG_FIFO"

PHASE=contract-validate
python3 "$COVERAGE_TOOL" validate-contract --repo-root "$ROOT_DIR"
python3 "$SUCCESSOR_OVERLAY_TOOL" validate

PHASE=bootstrap-negative
"$AUTHORITY_NEGATIVE"
python3 "$COVERAGE_CONTRACT_NEGATIVE_TOOL" \
  --repo-root "$ROOT_DIR" \
  --output "$NEGATIVE_ROOT/coverage-contract.json"
python3 "$SUCCESSOR_OVERLAY_TOOL" negative \
  --output "$NEGATIVE_ROOT/successor-overlay-probes.tsv"

PHASE=source-before
NOT_BEFORE_NS="$(python3 -c 'import time; print(time.time_ns())')"
[[ "$NOT_BEFORE_NS" =~ ^[1-9][0-9]*$ ]] || fail "cannot establish the coverage not-before boundary"
SOURCE_RESULT="$(python3 "$COVERAGE_TOOL" source-hash \
  --repo-root "$ROOT_DIR" --output "$RUN_ROOT/source-before.tsv")"
mapfile -t SOURCE_FIELDS < <(python3 - "$SOURCE_RESULT" <<'PY'
import json
import re
import sys

payload = json.loads(sys.argv[1])
if (
    payload.get("command") != "source-hash"
    or payload.get("status") != "passed"
    or re.fullmatch(r"[0-9a-f]{40}", str(payload.get("git_head", ""))) is None
    or re.fullmatch(r"[0-9a-f]{64}", str(payload.get("sha256", ""))) is None
):
    raise SystemExit("invalid source-hash response")
print(payload["git_head"])
print(payload["sha256"])
PY
)
[[ "${#SOURCE_FIELDS[@]}" -eq 2 ]] || fail "cannot parse source seal"
GIT_HEAD="${SOURCE_FIELDS[0]}"
SOURCE_BEFORE="${SOURCE_FIELDS[1]}"
CONTRACT_SHA256="$(sha256_file "$COVERAGE_CONTRACT")"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

PHASE=outer-marker
python3 - \
  "$OUTER_MARKER" "$RUN_ID" "$GIT_HEAD" "$CONTRACT_SHA256" \
  "$SOURCE_BEFORE" "$NOT_BEFORE_NS" "$STARTED_AT" <<'PY'
import json
import os
from pathlib import Path
import secrets
import stat
import sys

output = Path(sys.argv[1])
payload = {
    "schema_version": 1,
    "kind": "v934-step4-run-context",
    "authority_kind": "step4-coverage",
    "run_id": sys.argv[2],
    "git_head": sys.argv[3],
    "contract_sha256": sys.argv[4],
    "source_sha256": sys.argv[5],
    "not_before_ns": int(sys.argv[6]),
    "started_at": sys.argv[7],
}
data = (json.dumps(payload, indent=2, sort_keys=True) + "\n").encode()
directory_fd = -1
temporary_fd = -1
temporary_name = ""
temporary_exists = False
published = False
published_identity = None
try:
    if not output.is_absolute() or output.name != "run-context.json":
        raise RuntimeError("run context output path is not canonical")
    parent = output.parent
    parent_stat = parent.lstat()
    if stat.S_ISLNK(parent_stat.st_mode) or not stat.S_ISDIR(parent_stat.st_mode):
        raise RuntimeError("run context parent is not a real directory")
    if parent.resolve(strict=True) != parent:
        raise RuntimeError("run context parent contains a symlinked component")
    directory_flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0)
    directory_flags |= getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    directory_fd = os.open(parent, directory_flags)
    bound_parent_stat = os.fstat(directory_fd)
    if (parent_stat.st_dev, parent_stat.st_ino) != (
        bound_parent_stat.st_dev,
        bound_parent_stat.st_ino,
    ):
        raise RuntimeError("run context parent changed while it was opened")
    try:
        os.stat(output.name, dir_fd=directory_fd, follow_symlinks=False)
    except FileNotFoundError:
        pass
    else:
        raise RuntimeError("refusing to overwrite the Step 4 run context")

    temporary_name = (
        f".{output.name}.{os.getpid()}.{secrets.token_hex(12)}.tmp"
    )
    temporary_flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    temporary_flags |= getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    temporary_fd = os.open(
        temporary_name,
        temporary_flags,
        0o600,
        dir_fd=directory_fd,
    )
    temporary_exists = True
    os.fchmod(temporary_fd, 0o644)
    view = memoryview(data)
    while view:
        written = os.write(temporary_fd, view)
        if written <= 0:
            raise RuntimeError("short write while staging the run context")
        view = view[written:]
    os.fsync(temporary_fd)
    temporary_stat = os.fstat(temporary_fd)
    published_identity = (temporary_stat.st_dev, temporary_stat.st_ino)
    os.close(temporary_fd)
    temporary_fd = -1

    os.link(
        temporary_name,
        output.name,
        src_dir_fd=directory_fd,
        dst_dir_fd=directory_fd,
        follow_symlinks=False,
    )
    published = True
    output_stat = os.stat(output.name, dir_fd=directory_fd, follow_symlinks=False)
    if (
        not stat.S_ISREG(output_stat.st_mode)
        or (output_stat.st_dev, output_stat.st_ino) != published_identity
    ):
        raise RuntimeError("published run context identity differs")
    if output_stat.st_mtime_ns < payload["not_before_ns"]:
        raise RuntimeError("Step 4 run context predates its not-before boundary")
    os.fsync(directory_fd)
    os.unlink(temporary_name, dir_fd=directory_fd)
    temporary_exists = False
    os.fsync(directory_fd)

    current_parent_stat = parent.lstat()
    if (
        stat.S_ISLNK(current_parent_stat.st_mode)
        or (current_parent_stat.st_dev, current_parent_stat.st_ino)
        != (bound_parent_stat.st_dev, bound_parent_stat.st_ino)
    ):
        raise RuntimeError("run context parent changed during publication")
    canonical_output_stat = output.lstat()
    if (
        not stat.S_ISREG(canonical_output_stat.st_mode)
        or (canonical_output_stat.st_dev, canonical_output_stat.st_ino)
        != published_identity
    ):
        raise RuntimeError("canonical run context changed during publication")
except (OSError, RuntimeError) as error:
    if temporary_fd >= 0:
        try:
            os.close(temporary_fd)
        except OSError:
            pass
        temporary_fd = -1
    if directory_fd >= 0 and published and published_identity is not None:
        try:
            current = os.stat(output.name, dir_fd=directory_fd, follow_symlinks=False)
            if (current.st_dev, current.st_ino) == published_identity:
                os.unlink(output.name, dir_fd=directory_fd)
        except OSError:
            pass
    if directory_fd >= 0 and temporary_exists and temporary_name:
        try:
            os.unlink(temporary_name, dir_fd=directory_fd)
        except OSError:
            pass
    if directory_fd >= 0:
        try:
            os.fsync(directory_fd)
        except OSError:
            pass
    print(f"run context publication failed: {error}", file=sys.stderr)
    raise SystemExit(1)
finally:
    if temporary_fd >= 0:
        os.close(temporary_fd)
    if directory_fd >= 0:
        os.close(directory_fd)
PY
OUTER_MARKER_SHA256="$(sha256_file "$OUTER_MARKER")"

PHASE=toolchain-seal
run_toolchain_stage pre-compile-seal seal
PHASE=toolchain-negative
python3 "$TOOLCHAIN_RECEIPT_TOOL" negative \
  --repo-root "$ROOT_DIR" \
  --run-id "$RUN_ID" \
  --receipt "$RUN_ROOT/toolchain-receipt.json" \
  --output "$NEGATIVE_ROOT/toolchain-receipt.json"

export V934_AUTHORITY_LOCK_MODE=inherited
export V934_PARENT_AUTHORITY_KIND=step4-coverage
export V934_PARENT_RUN_ID="$RUN_ID"
export V934_PARENT_GIT_HEAD="$GIT_HEAD"
export V934_PARENT_CONTRACT_SHA256="$CONTRACT_SHA256"
export V934_PARENT_SOURCE_SHA256="$SOURCE_BEFORE"
export V934_PARENT_OUTER_MARKER_SHA256="$OUTER_MARKER_SHA256"
export V934_PARENT_OUTER_MARKER_PATH="$OUTER_MARKER"
export V934_COVERAGE_EXEC_ROOT="$EXEC_ROOT"
export V934_COVERAGE_SESSION_PREFIX="$RUN_ID"
export V934_COVERAGE_AGENT_JAR="$JACOCO_AGENT_JAR"
v934_step4_validate_inherited_authority \
  "$ROOT_DIR" "v934-step4-coverage-self" "$SOURCE_BEFORE" || \
  fail "self-validation of the outer authority failed"

PHASE=step2-report-view
python3 "$REPORT_VIEW_TOOL" validate-parent --repo-root "$ROOT_DIR"

# Main bytecode belongs to the same diagnostic authority as the exec files.
# Clean only the frozen 24 modules' main-compile outputs, then rebuild them
# without tests or either JaCoCo profile. Never clean the repository root
# target, which already contains this run's immutable authority evidence.
PHASE=main-bytecode-cleanup
mapfile -t PRODUCTION_MODULES < <(python3 - "$ROOT_DIR" "$STEP1_FREEZE" <<'PY'
import json
from pathlib import Path, PurePosixPath
import sys

root = Path(sys.argv[1])
freeze = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
reactor = freeze.get("reactor") if isinstance(freeze, dict) else None
modules = reactor.get("modules") if isinstance(reactor, dict) else None
if (
    freeze.get("schema_version") != 1
    or freeze.get("step") != 1
    or freeze.get("status") != "confirmed"
    or freeze.get("decision") != "passed"
    or reactor.get("module_count") != 24
    or not isinstance(modules, list)
    or len(modules) != 24
    or len(set(modules)) != 24
    or modules != sorted(modules)
):
    raise SystemExit("frozen production reactor is not the exact confirmed 24-module set")
for relative in modules:
    if not isinstance(relative, str):
        raise SystemExit("non-string production module path")
    pure = PurePosixPath(relative)
    if pure.is_absolute() or ".." in pure.parts or "\\" in relative:
        raise SystemExit(f"unsafe production module path: {relative!r}")
    path = root.joinpath(*pure.parts)
    if not path.is_dir() or path.is_symlink() or path.resolve() != path.absolute():
        raise SystemExit(f"production module is missing or unsafe: {relative}")
    # Every existing ancestor that the controlled cleanup traverses must be a
    # real directory. A final-path symlink would be removable without
    # traversal, but rejecting it gives one simpler fail-closed rule.
    for suffix in (
        "target",
        "target/classes",
        "target/generated-sources",
        "target/maven-status",
        "target/maven-status/maven-compiler-plugin",
        "target/maven-status/maven-compiler-plugin/compile",
    ):
        candidate = path / suffix
        if candidate.exists() or candidate.is_symlink():
            if not candidate.is_dir() or candidate.is_symlink():
                raise SystemExit(f"unsafe main-bytecode cleanup path: {candidate}")
    print(relative)
PY
)
[[ "${#PRODUCTION_MODULES[@]}" -eq 24 ]] || fail "main-bytecode cleanup did not resolve exact frozen 24 modules"
for module in "${PRODUCTION_MODULES[@]}"; do
  class_root="$ROOT_DIR/$module/target/classes"
  generated_source_root="$ROOT_DIR/$module/target/generated-sources"
  if [[ -d "$class_root" ]]; then
    require_no_symlink_descendants "$class_root" "pre-clean $module"
  fi
  if [[ -d "$generated_source_root" ]]; then
    require_no_symlink_descendants "$generated_source_root" "pre-clean generated sources $module"
  fi
  rm -rf -- \
    "$class_root" \
    "$generated_source_root" \
    "$ROOT_DIR/$module/target/maven-status/maven-compiler-plugin/compile"
done
[[ -f "$OUTER_MARKER" && -f "$RUN_ROOT/source-before.tsv" ]] || \
  fail "module cleanup removed top-level authority evidence"

PHASE=main-bytecode-compile
PRODUCTION_MODULE_CSV="$(IFS=,; printf '%s' "${PRODUCTION_MODULES[*]}")"
(cd "$ROOT_DIR" && command mvn -q \
  -f "$ROOT_DIR/pom.xml" \
  -pl "$PRODUCTION_MODULE_CSV" -am \
  '-P!coverage,!v934-coverage,!v934-coverage-report,!v934-coverage-model-check,!release,!multi-db,!model-lifecycle,!query-cache-real-query' \
  -Dmaven.test.skip=true \
  -DskipTests=true \
  -DskipUnitTests=true \
  -DskipITs=true \
  compile)

for module in "${PRODUCTION_MODULES[@]}"; do
  class_root="$ROOT_DIR/$module/target/classes"
  [[ -d "$class_root" && ! -L "$class_root" ]] || \
    fail "fresh compile did not produce a real class tree: $module"
  [[ "$(cd "$class_root" && pwd -P)" == "$class_root" ]] || \
    fail "fresh class tree contains a symlinked path component: $module"
  require_no_symlink_descendants "$class_root" "fresh $module"
  require_nonempty_class_tree "$class_root" "$module"
done

PHASE=main-bytecode-seal
python3 "$COVERAGE_EXEC_TOOL" seal-classes \
  --repo-root "$ROOT_DIR" \
  --run-id "$RUN_ID" \
  --not-before-ns "$NOT_BEFORE_NS" \
  --run-context "$OUTER_MARKER" \
  --output "$RUN_ROOT/class-universe.json"
require_real_file "$RUN_ROOT/class-universe.json"

# These are the only direct child authorities. The Step 3 required child owns
# and serially launches database, external-service, and Addon companion lanes.
run_child unit
run_child integration
run_child step3-required

PHASE=toolchain-post-children
run_toolchain_stage post-children verify

PHASE=report-inventory-negative
python3 "$REPORT_VIEW_TOOL" negative --repo-root "$ROOT_DIR"
python3 "$REPORT_INVENTORY_TOOL" negative \
  --output "$NEGATIVE_ROOT/report-inventory-probes.tsv"
PHASE=report-inventory
python3 "$REPORT_INVENTORY_TOOL" verify --repo-root "$ROOT_DIR" --run-id "$RUN_ID"
python3 "$REPORT_INVENTORY_TOOL" validate --repo-root "$ROOT_DIR" --run-id "$RUN_ID"

PHASE=coverage-report
"$COVERAGE_REPORT_RUNNER" \
  --run-dir "$RUN_ROOT" \
  --session-prefix "$RUN_ID" \
  --not-before-ns "$NOT_BEFORE_NS"

PHASE=toolchain-post-reporter
run_toolchain_stage post-reporter verify

AGGREGATE_EXEC="$RUN_ROOT/report/jacoco-aggregate.exec"
AGGREGATE_XML="$RUN_ROOT/report/jacoco-aggregate/jacoco.xml"
AGGREGATE_PROVENANCE="$RUN_ROOT/report/aggregate-provenance.json"
REPORT_PROVENANCE="$RUN_ROOT/report/report-provenance.json"
EXEC_MANIFEST="$RUN_ROOT/exec-manifest.json"
for required_file in \
  "$AGGREGATE_EXEC" "$AGGREGATE_XML" "$AGGREGATE_PROVENANCE" \
  "$REPORT_PROVENANCE" "$EXEC_MANIFEST"; do
  require_real_file "$required_file"
done

PHASE=model-external-gate
(cd "$ROOT_DIR" && command mvn -q \
  -f "$ROOT_DIR/pom.xml" \
  -pl foggy-dataset-model -am \
  '-P!coverage,!v934-coverage,!release,v934-coverage-model-check' \
  -DskipTests=true \
  -DskipUnitTests=true \
  -DskipITs=true \
  "-Dv934.coverage.model.dataFile=$AGGREGATE_EXEC" \
  verify)
atomic_env "$RUN_ROOT/model-gate.env" \
  "profile=v934-coverage-model-check" \
  "aggregate_exec_sha256=$(sha256_file "$AGGREGATE_EXEC")" \
  "bundle_line_minimum=0.77" \
  "bundle_branch_minimum=0.62" \
  "semantic_scale_line_minimum=1.00" \
  "semantic_scale_branch_minimum=1.00" \
  "status=passed"

PHASE=toolchain-final-replay
run_toolchain_stage post-model verify

PHASE=post-model-class-verify
python3 "$COVERAGE_EXEC_TOOL" verify-classes \
  --repo-root "$ROOT_DIR" \
  --run-id "$RUN_ID" \
  --not-before-ns "$NOT_BEFORE_NS" \
  --run-context "$OUTER_MARKER" \
  --class-universe "$RUN_ROOT/class-universe.json"

PHASE=coverage-exec-negative
python3 "$COVERAGE_EXEC_TOOL" negative \
  --repo-root "$ROOT_DIR" \
  --fixture-exec "$AGGREGATE_EXEC" \
  --output-dir "$NEGATIVE_ROOT/coverage-exec"

PHASE=coverage-observe
python3 "$COVERAGE_XML_TOOL" observe \
  --repo-root "$ROOT_DIR" \
  --xml "$AGGREGATE_XML" \
  --exec-manifest "$EXEC_MANIFEST" \
  --aggregate-exec "$AGGREGATE_EXEC" \
  --aggregate-provenance "$AGGREGATE_PROVENANCE" \
  --report-provenance "$REPORT_PROVENANCE" \
  --output "$RUN_ROOT/coverage-observation.json"

PHASE=coverage-xml-negative
python3 "$COVERAGE_XML_TOOL" negative \
  --repo-root "$ROOT_DIR" \
  --xml "$AGGREGATE_XML" \
  --exec-manifest "$EXEC_MANIFEST" \
  --aggregate-exec "$AGGREGATE_EXEC" \
  --aggregate-provenance "$AGGREGATE_PROVENANCE" \
  --report-provenance "$REPORT_PROVENANCE" \
  --output-dir "$NEGATIVE_ROOT/coverage-xml"

PHASE=source-after
SOURCE_RESULT="$(python3 "$COVERAGE_TOOL" source-hash \
  --repo-root "$ROOT_DIR" --output "$RUN_ROOT/source-after.tsv")"
SOURCE_AFTER="$(python3 - "$SOURCE_RESULT" <<'PY'
import json
import re
import sys

payload = json.loads(sys.argv[1])
value = payload.get("sha256") if isinstance(payload, dict) else None
if payload.get("status") != "passed" or re.fullmatch(r"[0-9a-f]{64}", str(value or "")) is None:
    raise SystemExit("invalid source-after response")
print(value)
PY
)" || fail "cannot parse source-after seal"
[[ "$SOURCE_AFTER" == "$SOURCE_BEFORE" ]] || fail "tracked source seal changed during diagnostic execution"
cmp -s -- "$RUN_ROOT/source-before.tsv" "$RUN_ROOT/source-after.tsv" || \
  fail "tracked source inventory changed during diagnostic execution"
[[ "$(git -C "$ROOT_DIR" rev-parse --verify 'HEAD^{commit}')" == "$GIT_HEAD" ]] || \
  fail "Git HEAD changed during diagnostic execution"
[[ -z "$(git -C "$ROOT_DIR" status --porcelain=v1 --untracked-files=all)" ]] || \
  fail "worktree changed during diagnostic execution"

PHASE=final-class-verify
python3 "$COVERAGE_EXEC_TOOL" verify-classes \
  --repo-root "$ROOT_DIR" \
  --run-id "$RUN_ID" \
  --not-before-ns "$NOT_BEFORE_NS" \
  --run-context "$OUTER_MARKER" \
  --class-universe "$RUN_ROOT/class-universe.json"

PHASE=resource-cleanup
cleanup_all_resources || fail "run-owned Docker cleanup or residue check failed"
CLEANUP_DONE=true

PHASE=run-log-flush
close_run_log || fail "run log did not flush"

PHASE=sensitive-scan
SENSITIVE_PATTERNS=(
  '(?i)(?:MYSQL_PWD|SQLCMDPASSWORD|REDIS_PASSWORD|REDIS_USERNAME|REDIS_URI|MONGO(?:DB)?_(?:URI|PASSWORD|USERNAME)|MYSQL_(?:PASSWORD|ROOT_PASSWORD)|MINIO_ROOT_(?:USER|PASSWORD)|AWS_(?:ACCESS_KEY_ID|SECRET_ACCESS_KEY))'
  '(?i)"?(?:password|passwd|pwd|credential|credentials|api[-_]?key|access[-_]?token|refresh[-_]?token|auth[-_]?token|secret|authorization)"?[[:space:]]*[:=][[:space:]]*(?!"?null"?(?:[[:space:],}\]]|$))"?[^"[:space:],}\]]+'
  '(?i)(?:authorization[[:space:]]*[:=][[:space:]]*)?bearer[[:space:]]+[A-Za-z0-9._~+/-]{8,}'
  '(?i)(?:redis|mongodb(?:\+srv)?|mysql|postgres(?:ql)?|sqlserver|s3)://[^/@:[:space:]]+:[^/@[:space:]]+@'
  '(?i)(?:--password|--passwd|--pwd)(?:=|[[:space:]])[^[:space:]]+'
)
SENSITIVE_SCAN_ARGS=()
for pattern in "${SENSITIVE_PATTERNS[@]}"; do
  SENSITIVE_SCAN_ARGS+=(-e "$pattern")
done
if SENSITIVE_MATCHES="$(rg --pcre2 -l --hidden \
  --glob '*.log' --glob '*.env' --glob '*.json' --glob '*.tsv' --glob '*.xml' \
  "${SENSITIVE_SCAN_ARGS[@]}" "$RUN_ROOT")"; then
  printf '%s\n' "$SENSITIVE_MATCHES" >&2
  fail "Step 4 diagnostic evidence contains credential-shaped material"
else
  sensitive_scan_rc=$?
  [[ "$sensitive_scan_rc" -eq 1 ]] || fail "sensitive evidence scan failed: rc=$sensitive_scan_rc"
fi
atomic_env "$RUN_ROOT/sensitive-scan.env" \
  "patterns=${#SENSITIVE_PATTERNS[@]}" \
  "text_extensions=log,env,json,tsv,xml" \
  "status=passed"

PHASE=toolchain-summary-replay
python3 "$TOOLCHAIN_RECEIPT_TOOL" verify \
  --repo-root "$ROOT_DIR" \
  --run-id "$RUN_ID" \
  --receipt "$RUN_ROOT/toolchain-receipt.json"
[[ "$(sha256_file "$RUN_ROOT/toolchain-receipt.json")" == "$TOOLCHAIN_RECEIPT_SHA" ]] || \
  fail "toolchain receipt changed before diagnostic summary"

PHASE=diagnostic-summary
[[ "$(sha256_file "$RUN_ROOT/toolchain-receipt.json")" == "$TOOLCHAIN_RECEIPT_SHA" ]] || \
  fail "toolchain receipt changed after its post-model replay"
validate_toolchain_stage_evidence \
  "$TOOLCHAIN_REPLAY_ROOT/pre-compile-seal.env" pre-compile-seal seal
validate_toolchain_stage_evidence \
  "$TOOLCHAIN_REPLAY_ROOT/post-children.env" post-children verify
validate_toolchain_stage_evidence \
  "$TOOLCHAIN_REPLAY_ROOT/post-reporter.env" post-reporter verify
validate_toolchain_stage_evidence \
  "$TOOLCHAIN_REPLAY_ROOT/post-model.env" post-model verify
for required_file in \
  "$RUN_ROOT/report-inventory.json" "$RUN_ROOT/coverage-observation.json" \
  "$RUN_ROOT/class-universe.json" \
  "$RUN_ROOT/toolchain-receipt.json" \
  "$TOOLCHAIN_REPLAY_ROOT/pre-compile-seal.env" \
  "$TOOLCHAIN_REPLAY_ROOT/post-children.env" \
  "$TOOLCHAIN_REPLAY_ROOT/post-reporter.env" \
  "$TOOLCHAIN_REPLAY_ROOT/post-model.env" \
  "$RUN_ROOT/report/toolchain-replay-pre.json" \
  "$RUN_ROOT/report/toolchain-replay-post.json" \
  "$NEGATIVE_ROOT/successor-overlay-probes.tsv" \
  "$NEGATIVE_ROOT/coverage-contract.json" \
  "$NEGATIVE_ROOT/toolchain-receipt.json" \
  "$NEGATIVE_ROOT/effective-reporter-pom.json" \
  "$NEGATIVE_ROOT/report-inventory-probes.tsv" \
  "$NEGATIVE_ROOT/coverage-exec/negative-result.json" \
  "$NEGATIVE_ROOT/coverage-xml/negative-result.json" \
  "$RUN_ROOT/model-gate.env" "$RUN_ROOT/cleanup.env" "$RUN_ROOT/sensitive-scan.env"; do
  require_real_file "$required_file"
done
atomic_env "$RUN_ROOT/summary.env" \
  "run_id=$RUN_ID" \
  "mode=diagnostic" \
  "git_head=$GIT_HEAD" \
  "threshold_status=$THRESHOLD_STATUS" \
  "source_before_sha256=$SOURCE_BEFORE" \
  "source_after_sha256=$SOURCE_AFTER" \
  "coverage_contract_sha256=$CONTRACT_SHA256" \
  "outer_marker_sha256=$OUTER_MARKER_SHA256" \
  "class_universe_sha256=$(sha256_file "$RUN_ROOT/class-universe.json")" \
  "toolchain_receipt_sha256=$TOOLCHAIN_RECEIPT_SHA" \
  "toolchain_pre_compile_seal_replay_sha256=$(sha256_file "$TOOLCHAIN_REPLAY_ROOT/pre-compile-seal.env")" \
  "toolchain_post_children_replay_sha256=$(sha256_file "$TOOLCHAIN_REPLAY_ROOT/post-children.env")" \
  "toolchain_reporter_pre_replay_sha256=$(sha256_file "$RUN_ROOT/report/toolchain-replay-pre.json")" \
  "toolchain_reporter_post_replay_sha256=$(sha256_file "$RUN_ROOT/report/toolchain-replay-post.json")" \
  "toolchain_post_reporter_replay_sha256=$(sha256_file "$TOOLCHAIN_REPLAY_ROOT/post-reporter.env")" \
  "toolchain_post_model_replay_sha256=$(sha256_file "$TOOLCHAIN_REPLAY_ROOT/post-model.env")" \
  "report_inventory_sha256=$(sha256_file "$RUN_ROOT/report-inventory.json")" \
  "exec_manifest_sha256=$(sha256_file "$EXEC_MANIFEST")" \
  "aggregate_exec_sha256=$(sha256_file "$AGGREGATE_EXEC")" \
  "aggregate_provenance_sha256=$(sha256_file "$AGGREGATE_PROVENANCE")" \
  "report_provenance_sha256=$(sha256_file "$REPORT_PROVENANCE")" \
  "coverage_observation_sha256=$(sha256_file "$RUN_ROOT/coverage-observation.json")" \
  "successor_overlay_negative_sha256=$(sha256_file "$NEGATIVE_ROOT/successor-overlay-probes.tsv")" \
  "coverage_contract_negative_sha256=$(sha256_file "$NEGATIVE_ROOT/coverage-contract.json")" \
  "toolchain_receipt_negative_sha256=$(sha256_file "$NEGATIVE_ROOT/toolchain-receipt.json")" \
  "effective_reporter_pom_negative_sha256=$(sha256_file "$NEGATIVE_ROOT/effective-reporter-pom.json")" \
  "report_inventory_negative_sha256=$(sha256_file "$NEGATIVE_ROOT/report-inventory-probes.tsv")" \
  "coverage_exec_negative_sha256=$(sha256_file "$NEGATIVE_ROOT/coverage-exec/negative-result.json")" \
  "coverage_xml_negative_sha256=$(sha256_file "$NEGATIVE_ROOT/coverage-xml/negative-result.json")" \
  "model_gate_sha256=$(sha256_file "$RUN_ROOT/model-gate.env")" \
  "cleanup_sha256=$(sha256_file "$RUN_ROOT/cleanup.env")" \
  "sensitive_scan_sha256=$(sha256_file "$RUN_ROOT/sensitive-scan.env")" \
  "exec_files=23" \
  "sessions=48" \
  "required_reports=773" \
  "required_structural_reports=59" \
  "required_testcase_nodes=5707" \
  "addon_reports=2" \
  "addon_testcase_nodes=6" \
  "model_external_gate=passed" \
  "acceptance_candidate=not-generated" \
  "status=diagnostic-observed"

PHASE=completed
