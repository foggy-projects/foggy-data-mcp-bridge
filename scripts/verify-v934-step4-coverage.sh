#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SCRIPT_PATH="$ROOT_DIR/scripts/verify-v934-step4-coverage.sh"
STEP4_DIR="$ROOT_DIR/scripts/v934/step4"
STEP4_MANIFEST="$STEP4_DIR/SHA256SUMS"
AUTHORITY_LIB="$ROOT_DIR/scripts/v934/authority_runner_lib.sh"
STEP4_AUTHORITY_LIB="$STEP4_DIR/authority_parent_lib.sh"
COVERAGE_TOOL="$STEP4_DIR/coverage_tool.py"
REPORT_VIEW_TOOL="$STEP4_DIR/step2_report_view_tool.py"
REPORT_INVENTORY_TOOL="$STEP4_DIR/report_inventory_tool.py"
COVERAGE_REPORT_RUNNER="$STEP4_DIR/coverage_report_runner.sh"
COVERAGE_EXEC_TOOL="$STEP4_DIR/coverage_exec_tool.py"
COVERAGE_XML_TOOL="$STEP4_DIR/coverage_xml_tool.py"
COVERAGE_XML_NEGATIVE_TOOL="$STEP4_DIR/coverage_xml_negative_tool.py"
COVERAGE_CONTRACT_NEGATIVE_TOOL="$STEP4_DIR/coverage_contract_negative_tool.py"
UNIT_FIXTURE_TOOL="$STEP4_DIR/unit_mysql_fixture_tool.py"
RUN_LOG_LIFECYCLE_NEGATIVE="$STEP4_DIR/run_log_lifecycle_negative_test.sh"
RUN_LOG_LIB="$STEP4_DIR/run_log_lifecycle_lib.sh"
TOOLCHAIN_RECEIPT_TOOL="$STEP4_DIR/toolchain_receipt_tool.py"
COVERAGE_CONTRACT="$STEP4_DIR/coverage-contract.json"
COVERAGE_THRESHOLDS="$STEP4_DIR/coverage-thresholds.json"
SUCCESSOR_OVERLAY_TOOL="$STEP4_DIR/successor/overlay_tool.py"
AUTHORITY_NEGATIVE="$STEP4_DIR/authority_parent_negative_test.sh"
STEP1_FREEZE="$ROOT_DIR/scripts/v934/contract-freeze.json"
JACOCO_AGENT_JAR="$HOME/.m2/repository/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar"
JACOCO_AGENT_SHA256="115e8e6e6593ca3a9892dfef695df4d487c706e59e71e64dc0ab95716ee02622"
CALCULATE_PARITY_CATALOG_REL="docs/v1.5.1/P1-CALCULATE-restricted-mvp-parity-catalog.json"
CALCULATE_PARITY_CATALOG="$ROOT_DIR/$CALCULATE_PARITY_CATALOG_REL"
CALCULATE_PARITY_CATALOG_BLOB="d7879a6a0c3ac3846719911a0c3b87b3e2ad9f11"
CALCULATE_PARITY_CATALOG_SHA256="f52eba376e3b2e94c2d03c8f01fcc6d9c3b98623d82938608aeacb90dd03ef60"
SENSITIVE_PATTERNS=(
  '(?i)(?:MYSQL_PWD|SQLCMDPASSWORD|REDIS_PASSWORD|REDIS_USERNAME|REDIS_URI|MONGO(?:DB)?_(?:URI|PASSWORD|USERNAME)|MYSQL_(?:PASSWORD|ROOT_PASSWORD)|MINIO_ROOT_(?:USER|PASSWORD)|AWS_(?:ACCESS_KEY_ID|SECRET_ACCESS_KEY))'
  '(?i)"?(?:password|passwd|pwd|credential|credentials|api[-_]?key|access[-_]?token|refresh[-_]?token|auth[-_]?token|secret|authorization)"?[[:space:]]*[:=][[:space:]]*(?!"?null"?(?:[[:space:],}\]]|$))"?[^"[:space:],}\]]+'
  '(?i)(?:authorization[[:space:]]*[:=][[:space:]]*)?bearer[[:space:]]+[A-Za-z0-9._~+/-]{8,}'
  '(?i)(?:redis|mongodb(?:\+srv)?|mysql|postgres(?:ql)?|sqlserver|s3)://[^/@:[:space:]]+:[^/@[:space:]]+@'
  '(?i)(?:--password|--passwd|--pwd)(?:=|[[:space:]])[^[:space:]]+'
)

usage() {
  cat <<'EOF'
Usage:
  scripts/verify-v934-step4-coverage.sh [diagnostic] [RUN_ID]
  scripts/verify-v934-step4-coverage.sh formal [RUN_ID]
  scripts/verify-v934-step4-coverage.sh release [RUN_ID]

Diagnostic requires the exact diagnostic-ready/diagnostic-pending workflow
state and records an observed baseline without an acceptance artifact. Formal
requires the exact formal-ready/confirmed successor, a direct single-parent
threshold-freeze commit, and publishes the canonical gate/candidate/final chain.
Release is a post-Step-4 successor replay: it consumes the already confirmed
thresholds and publishes the same fully checked artifact chain, but does not
pretend that its current commit is the historical diagnostic's Cfreeze child.
EOF
}

fail() {
  echo "[v934-step4-coverage] ERROR: $*" >&2
  exit 1
}

run_sensitive_pattern_regression_probes() {
  local fixture probe_rc positive_passed=0 safe_passed=0
  local expected_positive=7 expected_safe=3
  local -a scan_args=() positive_fixtures safe_fixtures
  positive_fixtures=(
    'MYSQL_PWD=fixture-only-value'
    'Resolved identity from authorization: userId=user_fixture, deptId=dept_fixture, tenantId=tenant_fixture'
    'Authorization: Bearer fixture.token.12345678'
    'password=fixture-only-value'
    'api_key: fixture-only-value'
    'mongodb://fixture-user:fixture-password@localhost:27017/example'
    '--password fixture-only-value'
  )
  safe_fixtures=(
    'Resolved demo identity: userId=user_fixture, deptId=dept_fixture, tenantId=tenant_fixture'
    'MongoClientSettings{credential=null, applicationName=null}'
    '{"password": null, "authorization": null}'
  )
  [[ "${#positive_fixtures[@]}" -eq "$expected_positive" ]] || \
    fail "sensitive positive probe cardinality changed: expected=$expected_positive actual=${#positive_fixtures[@]}"
  [[ "${#safe_fixtures[@]}" -eq "$expected_safe" ]] || \
    fail "sensitive safe probe cardinality changed: expected=$expected_safe actual=${#safe_fixtures[@]}"
  for fixture in "${SENSITIVE_PATTERNS[@]}"; do
    scan_args+=(-e "$fixture")
  done
  for fixture in "${positive_fixtures[@]}"; do
    if rg --pcre2 -q "${scan_args[@]}" - <<< "$fixture" >/dev/null 2>&1; then
      positive_passed=$((positive_passed + 1))
    else
      probe_rc=$?
      [[ "$probe_rc" -eq 1 ]] || fail "sensitive positive probe scan failed: rc=$probe_rc"
      fail "sensitive positive probe did not match: index=$((positive_passed + 1))"
    fi
  done
  for fixture in "${safe_fixtures[@]}"; do
    if rg --pcre2 -q "${scan_args[@]}" - <<< "$fixture" >/dev/null 2>&1; then
      fail "sensitive safe probe matched: index=$((safe_passed + 1))"
    else
      probe_rc=$?
      [[ "$probe_rc" -eq 1 ]] || fail "sensitive safe probe scan failed: rc=$probe_rc"
      safe_passed=$((safe_passed + 1))
    fi
  done
  [[ "$positive_passed" -eq "$expected_positive" ]] || \
    fail "sensitive positive probe pass count changed: expected=$expected_positive actual=$positive_passed"
  [[ "$safe_passed" -eq "$expected_safe" ]] || \
    fail "sensitive safe probe pass count changed: expected=$expected_safe actual=$safe_passed"
  echo "[v934-step4-coverage] sensitive-pattern PASS positive=$positive_passed/$expected_positive safe=$safe_passed/$expected_safe probes=$((positive_passed + safe_passed))/$((expected_positive + expected_safe))"
}

# The authority lock, HEAD, index, shallow boundary, grafts, and object store
# must all resolve from the canonical worktree.  Git exposes a broad ambient
# GIT_* override namespace, including internal shallow/graft knobs, so clear
# the whole namespace once before any Git command and restore only fixed
# safety controls owned by this runner.  Children inherit the same hermetic
# environment.
sanitize_git_environment() {
  local variable_name
  while IFS= read -r variable_name; do
    unset "$variable_name"
  done < <(compgen -A variable GIT_)
  export GIT_CONFIG_GLOBAL=/dev/null
  export GIT_CONFIG_NOSYSTEM=1
  export GIT_NO_REPLACE_OBJECTS=1
  export GIT_OPTIONAL_LOCKS=0
}

sanitize_git_environment

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

verify_calculate_parity_catalog() {
  local expected_index_entry actual_index_entry actual_sha256
  expected_index_entry=$'100644 '"$CALCULATE_PARITY_CATALOG_BLOB"$' 0\t'"$CALCULATE_PARITY_CATALOG_REL"
  actual_index_entry="$(git -c core.fsmonitor=false -c core.untrackedCache=false \
    -c core.hooksPath=/dev/null -C "$ROOT_DIR" ls-files --stage -- \
    "$CALCULATE_PARITY_CATALOG_REL")" || \
    fail "cannot inspect the tracked CALCULATE parity catalog"
  [[ "$actual_index_entry" == "$expected_index_entry" ]] || \
    fail "CALCULATE parity catalog is not the exact tracked 100644 input"
  actual_sha256="$(sha256_file "$CALCULATE_PARITY_CATALOG")"
  [[ "$actual_sha256" == "$CALCULATE_PARITY_CATALOG_SHA256" ]] || \
    fail "CALCULATE parity catalog SHA-256 differs"
  echo "[v934-step4-coverage] CALCULATE parity catalog PASS blob=$CALCULATE_PARITY_CATALOG_BLOB sha256=$actual_sha256"
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
  local attempt code=0 state=1 signal_code=1 wait_code=0 timed_out=false
  if [[ "$RUN_LOG_OPEN" == true ]]; then
    if ! exec 1>&3; then
      code=1
    fi
    if ! exec 2>&4; then
      code=1
    fi
    RUN_LOG_OPEN=false
    if ! exec 3>&-; then
      code=1
    fi
    if ! exec 4>&-; then
      code=1
    fi
  fi
  if [[ -n "$RUN_LOG_TEE_PID" ]]; then
    if [[ ! "$RUN_LOG_TEE_STARTTIME_TICKS" =~ ^[1-9][0-9]*$ ]]; then
      if ! v934_run_log_release_unsealed_fifo_logger \
        "$RUN_LOG_FIFO" "$RUN_LOG_TEE_PID"; then
        code=1
      fi
      RUN_LOG_TEE_PID=""
    fi
  fi
  if [[ -n "$RUN_LOG_TEE_PID" ]]; then
    V934_RUN_LOG_LOGGER_PID="$RUN_LOG_TEE_PID"
    V934_RUN_LOG_LOGGER_STARTTIME_TICKS="$RUN_LOG_TEE_STARTTIME_TICKS"
    V934_RUN_LOG_LOGGER_BOOT_ID="$RUN_LOG_TEE_BOOT_ID"
    V934_RUN_LOG_LOGGER_EXPECTED_PPID="$RUN_LOG_TEE_EXPECTED_PPID"
    for ((attempt = 0; attempt < 100; attempt++)); do
      if v934_run_log_logger_state; then
        state=0
        sleep 0.05
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
      for ((attempt = 0; attempt < 20; attempt++)); do
        if v934_run_log_logger_state; then
          state=0
          sleep 0.05
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
        wait "$RUN_LOG_TEE_PID" >/dev/null 2>&1 || true
    elif [[ "$state" -eq 2 ]]; then
      code=1
    elif wait "$RUN_LOG_TEE_PID"; then
      wait_code=0
    else
      wait_code=$?
      code=1
    fi
    RUN_LOG_TEE_PID=""
    RUN_LOG_TEE_STARTTIME_TICKS=""
    RUN_LOG_TEE_BOOT_ID=""
    RUN_LOG_TEE_EXPECTED_PPID=""
    V934_RUN_LOG_LOGGER_PID=""
    V934_RUN_LOG_LOGGER_STARTTIME_TICKS=""
    V934_RUN_LOG_LOGGER_BOOT_ID=""
    V934_RUN_LOG_LOGGER_EXPECTED_PPID=""
  fi
  [[ -z "$RUN_LOG_FIFO" ]] || rm -f -- "$RUN_LOG_FIFO"
  RUN_LOG_FIFO=""
  if [[ -n "$RUN_LOG_FILE_FD" ]]; then
    validate_run_log_binding || code=1
    if ! exec {RUN_LOG_FILE_FD}>&-; then
      code=1
    fi
    RUN_LOG_FILE_FD=""
  fi
  if [[ "$timed_out" == true ]]; then
    echo "[v934-step4-coverage] ERROR: run logger exceeded the bounded flush deadline" >&2
  elif [[ "$wait_code" -ne 0 ]]; then
    echo "[v934-step4-coverage] ERROR: run logger exited non-zero rc=$wait_code" >&2
  fi
  return "$code"
}

read_process_identity() {
  python3 - "$1" <<'PY'
from pathlib import Path
import sys


pid = int(sys.argv[1])
try:
    raw = Path(f"/proc/{pid}/stat").read_bytes()
    boot_id = Path("/proc/sys/kernel/random/boot_id").read_text(encoding="ascii").strip()
except (FileNotFoundError, ProcessLookupError):
    raise SystemExit(1)
except (OSError, UnicodeError) as error:
    print(f"cannot read process identity: {error.__class__.__name__}", file=sys.stderr)
    raise SystemExit(2)
try:
    right = raw.rindex(b")")
    fields = raw[right + 2 :].split()
    if len(fields) < 20:
        raise ValueError("truncated stat")
    state = fields[0].decode("ascii")
    ppid = int(fields[1])
    pgid = int(fields[2])
    sid = int(fields[3])
    starttime = int(fields[19])
except (UnicodeError, ValueError) as error:
    print(f"malformed process identity: {error.__class__.__name__}", file=sys.stderr)
    raise SystemExit(2)
print(f"{pid}\t{state}\t{ppid}\t{pgid}\t{sid}\t{starttime}\t{boot_id}")
PY
}

capture_active_child_identity() {
  local identity pid state ppid pgid sid starttime boot_id
  [[ -n "$ACTIVE_CHILD_PID" && "$ACTIVE_CHILD_EXPECTED_PPID" =~ ^[1-9][0-9]*$ ]] || return 2
  identity="$(read_process_identity "$ACTIVE_CHILD_PID")" || return $?
  IFS=$'\t' read -r pid state ppid pgid sid starttime boot_id <<< "$identity"
  [[ "$pid" == "$ACTIVE_CHILD_PID" && "$state" != Z ]] || return 2
  [[ "$ppid" == "$ACTIVE_CHILD_EXPECTED_PPID" ]] || return 2
  [[ "$starttime" =~ ^[1-9][0-9]*$ && "$boot_id" =~ ^[0-9a-f-]{36}$ ]] || return 2
  ACTIVE_CHILD_STARTTIME_TICKS="$starttime"
  ACTIVE_CHILD_BOOT_ID="$boot_id"
}

active_child_leader_identity_matches() {
  local identity code pid state ppid pgid sid starttime boot_id
  [[ -n "$ACTIVE_CHILD_PID" && "$ACTIVE_CHILD_STARTTIME_TICKS" =~ ^[1-9][0-9]*$ ]] || return 2
  [[ "$ACTIVE_CHILD_EXPECTED_PPID" =~ ^[1-9][0-9]*$ && "$ACTIVE_CHILD_BOOT_ID" =~ ^[0-9a-f-]{36}$ ]] || return 2
  if identity="$(read_process_identity "$ACTIVE_CHILD_PID")"; then
    :
  else
    code=$?
    [[ "$code" -eq 1 ]] && return 1
    return 2
  fi
  IFS=$'\t' read -r pid state ppid pgid sid starttime boot_id <<< "$identity"
  # starttime is the immutable PID identity; compare it before trusting any
  # other numeric process attribute.
  [[ "$starttime" == "$ACTIVE_CHILD_STARTTIME_TICKS" ]] || return 2
  [[ "$pid" == "$ACTIVE_CHILD_PID" ]] || return 2
  [[ "$ppid" == "$ACTIVE_CHILD_EXPECTED_PPID" && "$boot_id" == "$ACTIVE_CHILD_BOOT_ID" ]] || return 2
  [[ "$state" != Z ]] || return 1
  if [[ "$ACTIVE_CHILD_GROUP_ESTABLISHED" == true ]]; then
    [[ "$pgid" == "$ACTIVE_CHILD_PID" && "$sid" == "$ACTIVE_CHILD_SID" ]] || return 2
  fi
  return 0
}

active_child_group_alive() {
  [[ -n "$ACTIVE_CHILD_PID" && "$ACTIVE_CHILD_GROUP_ESTABLISHED" == true ]] || return 1
  python3 - \
    "$ACTIVE_CHILD_PID" "$ACTIVE_CHILD_SID" \
    "$ACTIVE_CHILD_STARTTIME_TICKS" "$ACTIVE_CHILD_EXPECTED_PPID" \
    "$ACTIVE_CHILD_BOOT_ID" <<'PY'
from pathlib import Path
import sys


pgid = int(sys.argv[1])
expected_sid = int(sys.argv[2])
expected_starttime = int(sys.argv[3])
expected_ppid = int(sys.argv[4])
expected_boot_id = sys.argv[5]
try:
    current_boot_id = Path("/proc/sys/kernel/random/boot_id").read_text(encoding="ascii").strip()
except (OSError, UnicodeError):
    raise SystemExit(2)
if current_boot_id != expected_boot_id:
    raise SystemExit(2)

live = []
for entry in Path("/proc").iterdir():
    if not entry.name.isdigit():
        continue
    try:
        raw = (entry / "stat").read_bytes()
    except (FileNotFoundError, ProcessLookupError):
        continue
    except OSError:
        raise SystemExit(2)
    try:
        right = raw.rindex(b")")
        fields = raw[right + 2 :].split()
        if len(fields) < 20:
            raise ValueError("truncated stat")
        pid = int(entry.name)
        state = fields[0]
        ppid = int(fields[1])
        process_group = int(fields[2])
        session = int(fields[3])
        starttime = int(fields[19])
    except ValueError:
        raise SystemExit(2)
    if process_group != pgid:
        continue
    if session != expected_sid:
        raise SystemExit(2)
    if pid == pgid and (starttime != expected_starttime or ppid != expected_ppid):
        raise SystemExit(2)
    if state != b"Z":
        live.append(pid)
raise SystemExit(0 if live else 1)
PY
}

# Successful completion requires two consecutive complete /proc scans with no
# live member in the frozen process group.  This closes the one-scan race where
# a just-forked descendant is not yet visible to the first observer.
confirm_active_child_group_absent() {
  local scan state
  for scan in 1 2; do
    if active_child_group_alive; then
      return 1
    else
      state=$?
    fi
    [[ "$state" -eq 1 ]] || return 2
    [[ "$scan" -eq 2 ]] || sleep 0.05
  done
  return 0
}

signal_active_child() {
  local signal_name="$1" scope="$2" expected_sid=-
  [[ -n "$ACTIVE_CHILD_PID" && "$ACTIVE_CHILD_STARTTIME_TICKS" =~ ^[1-9][0-9]*$ ]] || return 2
  if [[ "$scope" == group ]]; then
    [[ "$ACTIVE_CHILD_GROUP_ESTABLISHED" == true ]] || return 2
    expected_sid="$ACTIVE_CHILD_SID"
  elif [[ "$scope" != leader ]]; then
    return 2
  fi
  python3 - \
    "$ACTIVE_CHILD_PID" "$ACTIVE_CHILD_STARTTIME_TICKS" \
    "$ACTIVE_CHILD_EXPECTED_PPID" "$expected_sid" "$ACTIVE_CHILD_BOOT_ID" \
    "$signal_name" "$scope" <<'PY'
import os
from pathlib import Path
import signal
import sys


class IdentityConflict(RuntimeError):
    pass


leader = int(sys.argv[1])
expected_leader_starttime = int(sys.argv[2])
expected_leader_ppid = int(sys.argv[3])
expected_sid_text = sys.argv[4]
expected_sid = None if expected_sid_text == "-" else int(expected_sid_text)
expected_boot_id = sys.argv[5]
signal_name = sys.argv[6]
scope = sys.argv[7]
signal_number = getattr(signal, f"SIG{signal_name}", None)
if signal_number is None or scope not in {"leader", "group"}:
    raise SystemExit(2)
try:
    current_boot_id = Path("/proc/sys/kernel/random/boot_id").read_text(encoding="ascii").strip()
except (OSError, UnicodeError) as error:
    print(f"cannot verify boot identity: {error.__class__.__name__}", file=sys.stderr)
    raise SystemExit(2)
if expected_boot_id and current_boot_id != expected_boot_id:
    print("boot identity changed before signal", file=sys.stderr)
    raise SystemExit(2)


def read_identity(pid: int):
    try:
        raw = Path(f"/proc/{pid}/stat").read_bytes()
    except (FileNotFoundError, ProcessLookupError):
        return None
    except OSError as error:
        raise IdentityConflict(f"cannot read PID {pid}: {error.__class__.__name__}") from error
    try:
        right = raw.rindex(b")")
        fields = raw[right + 2 :].split()
        if len(fields) < 20:
            raise ValueError("truncated stat")
        return (
            pid,
            fields[0],
            int(fields[1]),
            int(fields[2]),
            int(fields[3]),
            int(fields[19]),
        )
    except ValueError as error:
        raise IdentityConflict(f"malformed identity for PID {pid}") from error


try:
    identities = []
    if scope == "leader":
        identity = read_identity(leader)
        if identity is not None:
            # Always compare the immutable starttime before using the PID.
            if identity[5] != expected_leader_starttime or identity[2] != expected_leader_ppid:
                raise IdentityConflict("leader PID was reused")
            if identity[1] != b"Z":
                identities.append(identity)
    else:
        if expected_sid != leader:
            raise IdentityConflict("group SID is not canonical")
        for entry in Path("/proc").iterdir():
            if not entry.name.isdigit():
                continue
            identity = read_identity(int(entry.name))
            if identity is None or identity[3] != leader:
                continue
            if identity[4] != expected_sid:
                raise IdentityConflict("numeric process group was reused")
            if identity[0] == leader and (
                identity[5] != expected_leader_starttime
                or identity[2] != expected_leader_ppid
            ):
                raise IdentityConflict("group leader PID was reused or reparented")
            if identity[1] != b"Z":
                identities.append(identity)

    # Pin every target before signaling any of them. Re-read starttime and the
    # group/session identity after pidfd_open, so PID reuse can never redirect
    # a numeric signal to an unrelated process.
    pinned = []
    try:
        for identity in sorted(identities):
            try:
                descriptor = os.pidfd_open(identity[0], 0)
            except ProcessLookupError:
                continue
            except OSError as error:
                raise IdentityConflict(
                    f"cannot pin PID {identity[0]}: {error.__class__.__name__}"
                ) from error
            current = read_identity(identity[0])
            if current is None:
                os.close(descriptor)
                continue
            if current != identity:
                os.close(descriptor)
                raise IdentityConflict(f"PID {identity[0]} changed while being pinned")
            pinned.append((descriptor, identity[0]))
        sent = 0
        for descriptor, _pid in pinned:
            try:
                signal.pidfd_send_signal(descriptor, signal_number)
                sent += 1
            except ProcessLookupError:
                pass
            except OSError as error:
                raise IdentityConflict(
                    f"pidfd signal failed: {error.__class__.__name__}"
                ) from error
    finally:
        for descriptor, _pid in pinned:
            os.close(descriptor)
except IdentityConflict as error:
    print(str(error), file=sys.stderr)
    raise SystemExit(2)
raise SystemExit(0 if sent else 1)
PY
}

record_active_child_error() {
  local reason="$1" detail="$2" error_sha256 output
  [[ -n "$ACTIVE_CHILD_NAME" && "$reason" =~ ^[a-z0-9-]+$ ]] || return 1
  error_sha256="$(printf '%s' "$detail" | sha256sum | cut -d' ' -f1)" || return 1
  output="$CHILD_LIFECYCLE_ROOT/${ACTIVE_CHILD_NAME}-${reason}-error.env"
  atomic_env "$output" \
    "run_id=$RUN_ID" \
    "child=$ACTIVE_CHILD_NAME" \
    "leader_pid=${ACTIVE_CHILD_PID:-unsealed}" \
    "leader_sid=${ACTIVE_CHILD_SID:-unsealed}" \
    "leader_starttime_ticks=${ACTIVE_CHILD_STARTTIME_TICKS:-unsealed}" \
    "boot_id=${ACTIVE_CHILD_BOOT_ID:-unsealed}" \
    "reason=$reason" \
    "detail_sha256=$error_sha256" \
    "status=failed"
}

snapshot_active_child_group() {
  local reason="$1" output diagnostic
  [[ -n "$ACTIVE_CHILD_PID" && -n "$ACTIVE_CHILD_NAME" ]] || return 1
  [[ "$ACTIVE_CHILD_GROUP_ESTABLISHED" == true ]] || return 1
  [[ "$reason" =~ ^[a-z0-9-]+$ ]] || return 1
  output="$CHILD_LIFECYCLE_ROOT/${ACTIVE_CHILD_NAME}-${reason}-residue.tsv"
  if diagnostic="$(python3 - \
    "$ACTIVE_CHILD_PID" "$ACTIVE_CHILD_SID" \
    "$ACTIVE_CHILD_STARTTIME_TICKS" "$ACTIVE_CHILD_BOOT_ID" "$output" 2>&1 <<'PY'
import hashlib
import os
from pathlib import Path
import stat
import sys


pgid = int(sys.argv[1])
expected_sid = int(sys.argv[2])
expected_starttime = int(sys.argv[3])
expected_boot_id = sys.argv[4]
output = Path(sys.argv[5])
if pgid <= 1 or expected_sid != pgid or not output.is_absolute() or output.exists() or output.is_symlink():
    raise SystemExit("unsafe process-group snapshot request")
try:
    current_boot_id = Path("/proc/sys/kernel/random/boot_id").read_text(encoding="ascii").strip()
except (OSError, UnicodeError) as error:
    raise SystemExit(f"cannot verify snapshot boot identity: {error.__class__.__name__}")
if current_boot_id != expected_boot_id:
    raise SystemExit("snapshot boot identity changed")
parent = output.parent
parent_stat = parent.lstat()
if (
    stat.S_ISLNK(parent_stat.st_mode)
    or not stat.S_ISDIR(parent_stat.st_mode)
    or parent.resolve(strict=True) != parent
):
    raise SystemExit("unsafe process-group snapshot parent")

rows = []
for entry in Path("/proc").iterdir():
    if not entry.name.isdigit():
        continue
    try:
        raw = (entry / "stat").read_bytes()
    except (FileNotFoundError, ProcessLookupError):
        continue
    except OSError as error:
        raise SystemExit(f"cannot read process stat: {error.__class__.__name__}")
    try:
        left = raw.index(b"(")
        right = raw.rindex(b")")
        fields = raw[right + 2 :].split()
        if len(fields) < 20:
            raise ValueError("truncated stat")
        pid = int(raw[:left].strip())
        state = fields[0].decode("ascii")
        ppid = int(fields[1])
        process_group = int(fields[2])
        session = int(fields[3])
        starttime = int(fields[19])
    except (UnicodeError, ValueError) as error:
        raise SystemExit(f"malformed process stat: {error.__class__.__name__}")
    if process_group != pgid:
        continue
    if session != expected_sid:
        raise SystemExit("numeric process group was reused before snapshot")
    if pid == pgid and starttime != expected_starttime:
        raise SystemExit("group leader PID was reused before snapshot")
    try:
        command_bytes = (entry / "cmdline").read_bytes()
    except (FileNotFoundError, ProcessLookupError):
        continue
    except OSError as error:
        raise SystemExit(f"cannot read process cmdline: {error.__class__.__name__}")
    arguments = [part for part in command_bytes.split(b"\0") if part]
    argv0 = arguments[0].rsplit(b"/", 1)[-1] if arguments else b""
    rows.append(
        (
            pid,
            ppid,
            process_group,
            session,
            state,
            starttime,
            raw[left + 1 : right].hex(),
            argv0.hex(),
            len(arguments),
            hashlib.sha256(command_bytes).hexdigest(),
        )
    )

rows.sort()
payload = [
    "pid\tppid\tpgid\tsid\tstat\tstarttime_ticks\tcomm_hex\targv0_hex\targc\tcmdline_sha256\n"
]
for row in rows:
    payload.append("\t".join(str(value) for value in row) + "\n")
data = "".join(payload).encode("ascii")
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
published = False
try:
    descriptor = os.open(
        temporary,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
        0o644,
    )
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())
    os.link(temporary, output, follow_symlinks=False)
    published = True
    temporary.unlink()
    directory_fd = os.open(
        parent,
        os.O_RDONLY
        | getattr(os, "O_DIRECTORY", 0)
        | getattr(os, "O_CLOEXEC", 0)
        | getattr(os, "O_NOFOLLOW", 0),
    )
    try:
        os.fsync(directory_fd)
    finally:
        os.close(directory_fd)
except OSError:
    temporary.unlink(missing_ok=True)
    if published:
        output.unlink(missing_ok=True)
    raise

print(
    f"[v934-step4-coverage] process-group snapshot pgid={pgid} "
    f"members={len(rows)} output={output}"
)
PY
  )"; then
    printf '%s\n' "$diagnostic"
    return 0
  fi
  record_active_child_error "${reason}-snapshot" "$diagnostic" || \
    echo "[v934-step4-coverage] ERROR: cannot publish snapshot error receipt" >&2
  echo "[v934-step4-coverage] ERROR: process-group snapshot failed: $diagnostic" >&2
  return 1
}

validate_child_ready_receipt() {
  local child="$1" path="$2" pid="$3" expected_starttime="$4" expected_ppid="$5"
  python3 - \
    "$path" "$CHILD_READY_ROOT" "$RUN_ID" "$child" \
    "$pid" "$expected_starttime" "$expected_ppid" <<'PY'
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise SystemExit(f"duplicate child-ready key: {key}")
        result[key] = value
    return result


def reject_constant(value):
    raise SystemExit(f"non-finite child-ready number: {value}")


path = Path(sys.argv[1])
root = Path(sys.argv[2])
run_id = sys.argv[3]
child = sys.argv[4]
pid = int(sys.argv[5])
expected_starttime = int(sys.argv[6])
expected_ppid = int(sys.argv[7])
directory_fd = -1
descriptor = -1
try:
    root_before = root.lstat()
    if (
        not path.is_absolute()
        or not root.is_absolute()
        or path.parent != root
        or path.name != f"{child}.json"
        or stat.S_ISLNK(root_before.st_mode)
        or not stat.S_ISDIR(root_before.st_mode)
        or root.resolve(strict=True) != root
    ):
        raise SystemExit("child-ready receipt parent is unsafe")
    directory_fd = os.open(
        root,
        os.O_RDONLY
        | getattr(os, "O_DIRECTORY", 0)
        | getattr(os, "O_CLOEXEC", 0)
        | getattr(os, "O_NOFOLLOW", 0),
    )
    bound_root = os.fstat(directory_fd)
    if (root_before.st_dev, root_before.st_ino) != (bound_root.st_dev, bound_root.st_ino):
        raise SystemExit("child-ready receipt parent changed while opening")
    descriptor = os.open(
        path.name,
        os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0),
        dir_fd=directory_fd,
    )
    file_stat = os.fstat(descriptor)
    if (
        not stat.S_ISREG(file_stat.st_mode)
        or file_stat.st_uid != os.getuid()
        or stat.S_IMODE(file_stat.st_mode) != 0o600
        or file_stat.st_nlink != 1
        or file_stat.st_size > 4096
    ):
        raise SystemExit("child-ready receipt identity is unsafe")
    chunks = []
    size = 0
    while True:
        chunk = os.read(descriptor, 4097 - size)
        if not chunk:
            break
        chunks.append(chunk)
        size += len(chunk)
        if size > 4096:
            raise SystemExit("child-ready receipt exceeds size limit")
    data = b"".join(chunks)
    path_after = os.stat(path.name, dir_fd=directory_fd, follow_symlinks=False)
    root_after = root.lstat()
    if (
        (path_after.st_dev, path_after.st_ino) != (file_stat.st_dev, file_stat.st_ino)
        or (root_after.st_dev, root_after.st_ino) != (bound_root.st_dev, bound_root.st_ino)
    ):
        raise SystemExit("child-ready receipt changed while reading")
except OSError as error:
    raise SystemExit(f"cannot strictly read child-ready receipt: {error.__class__.__name__}")
finally:
    if descriptor >= 0:
        os.close(descriptor)
    if directory_fd >= 0:
        os.close(directory_fd)

try:
    payload = json.loads(
        data.decode("utf-8", errors="strict"),
        object_pairs_hook=unique,
        parse_constant=reject_constant,
    )
except (UnicodeError, json.JSONDecodeError) as error:
    raise SystemExit(f"malformed child-ready receipt: {error.__class__.__name__}")
expected_keys = {
    "schema_version",
    "kind",
    "run_id",
    "child",
    "pid",
    "pgid",
    "sid",
    "starttime_ticks",
    "boot_id",
    "status",
}
if not isinstance(payload, dict) or set(payload) != expected_keys:
    raise SystemExit("child-ready receipt schema differs")
boot_id = payload["boot_id"]
if (
    type(payload["schema_version"]) is not int
    or payload["schema_version"] != 1
    or payload["kind"] != "v934-step4-child-ready"
    or payload["run_id"] != run_id
    or payload["child"] != child
    or type(payload["pid"]) is not int
    or payload["pid"] != pid
    or type(payload["pgid"]) is not int
    or payload["pgid"] != pid
    or type(payload["sid"]) is not int
    or payload["sid"] != pid
    or type(payload["starttime_ticks"]) is not int
    or payload["starttime_ticks"] != expected_starttime
    or type(boot_id) is not str
    or re.fullmatch(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}", boot_id) is None
    or payload["status"] != "ready"
):
    raise SystemExit("child-ready receipt fields differ")
try:
    current_boot_id = Path("/proc/sys/kernel/random/boot_id").read_text(encoding="ascii").strip()
except (OSError, UnicodeError) as error:
    raise SystemExit(f"cannot verify child-ready boot identity: {error.__class__.__name__}")
if boot_id != current_boot_id:
    raise SystemExit("child-ready receipt boot identity differs")
try:
    raw = Path(f"/proc/{pid}/stat").read_bytes()
except (FileNotFoundError, ProcessLookupError):
    raw = None
except OSError as error:
    raise SystemExit(f"cannot verify live child identity: {error.__class__.__name__}")
if raw is not None:
    try:
        right = raw.rindex(b")")
        fields = raw[right + 2 :].split()
        if len(fields) < 20:
            raise ValueError("truncated stat")
        live_ppid = int(fields[1])
        live_pgid = int(fields[2])
        live_sid = int(fields[3])
        live_starttime = int(fields[19])
    except ValueError as error:
        raise SystemExit(f"malformed live child identity: {error.__class__.__name__}")
    # Check starttime first: a match on PID alone is never trusted.
    if (
        live_starttime != expected_starttime
        or live_ppid != expected_ppid
        or live_pgid != pid
        or live_sid != pid
    ):
        raise SystemExit("live child identity differs from ready receipt")
print(
    f"{hashlib.sha256(data).hexdigest()}\t{file_stat.st_dev}\t{file_stat.st_ino}"
    f"\t{payload['sid']}\t{payload['starttime_ticks']}\t{boot_id}"
)
PY
}

publish_child_lifecycle_manifest() {
  python3 - \
    "$CHILD_READY_ROOT" "$CHILD_LIFECYCLE_ROOT" \
    "$RUN_ROOT/child-lifecycle.json" "$RUN_ID" <<'PY'
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys


ready_root = Path(sys.argv[1])
root = Path(sys.argv[2])
output = Path(sys.argv[3])
run_id = sys.argv[4]
children = ("unit", "integration", "step3-required")
if (
    not ready_root.is_absolute()
    or not root.is_absolute()
    or not output.is_absolute()
    or ready_root.is_symlink()
    or not ready_root.is_dir()
    or ready_root.resolve(strict=True) != ready_root
    or root.is_symlink()
    or not root.is_dir()
    or root.resolve(strict=True) != root
    or output.exists()
    or output.is_symlink()
):
    raise SystemExit("unsafe child lifecycle manifest path")
actual_ready = sorted(path.name for path in ready_root.iterdir())
expected_ready = sorted(f"{child}.json" for child in children)
actual = sorted(path.name for path in root.iterdir())
expected = sorted(f"{child}-complete.env" for child in children)
if actual_ready != expected_ready or actual != expected:
    raise SystemExit(
        "child lifecycle evidence set differs: "
        f"ready={actual_ready!r} completion={actual!r}"
    )


def strict_read(path: Path, expected_root: Path, expected_mode: int, label: str):
    directory_fd = -1
    descriptor = -1
    try:
        root_before = expected_root.lstat()
        if (
            path.parent != expected_root
            or path.name in {"", ".", ".."}
            or stat.S_ISLNK(root_before.st_mode)
            or not stat.S_ISDIR(root_before.st_mode)
            or expected_root.resolve(strict=True) != expected_root
        ):
            raise SystemExit(f"{label}: unsafe evidence parent")
        directory_fd = os.open(
            expected_root,
            os.O_RDONLY
            | getattr(os, "O_DIRECTORY", 0)
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
        )
        bound_root = os.fstat(directory_fd)
        if (root_before.st_dev, root_before.st_ino) != (
            bound_root.st_dev,
            bound_root.st_ino,
        ):
            raise SystemExit(f"{label}: evidence parent changed while opening")
        descriptor = os.open(
            path.name,
            os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0),
            dir_fd=directory_fd,
        )
        file_stat = os.fstat(descriptor)
        if (
            not stat.S_ISREG(file_stat.st_mode)
            or file_stat.st_uid != os.getuid()
            or stat.S_IMODE(file_stat.st_mode) != expected_mode
            or file_stat.st_nlink != 1
            or file_stat.st_size > 4096
        ):
            raise SystemExit(f"{label}: evidence identity is unsafe")
        chunks = []
        size = 0
        while True:
            chunk = os.read(descriptor, 4097 - size)
            if not chunk:
                break
            chunks.append(chunk)
            size += len(chunk)
            if size > 4096:
                raise SystemExit(f"{label}: evidence exceeds size limit")
        data = b"".join(chunks)
        path_after = os.stat(path.name, dir_fd=directory_fd, follow_symlinks=False)
        root_after = expected_root.lstat()
        if (
            (path_after.st_dev, path_after.st_ino) != (file_stat.st_dev, file_stat.st_ino)
            or (root_after.st_dev, root_after.st_ino) != (bound_root.st_dev, bound_root.st_ino)
        ):
            raise SystemExit(f"{label}: evidence changed while reading")
        return data, hashlib.sha256(data).hexdigest()
    except OSError as error:
        raise SystemExit(f"{label}: strict read failed: {error.__class__.__name__}")
    finally:
        if descriptor >= 0:
            os.close(descriptor)
        if directory_fd >= 0:
            os.close(directory_fd)


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise SystemExit(f"duplicate child-ready key: {key}")
        result[key] = value
    return result


def reject_constant(value):
    raise SystemExit(f"non-finite child-ready number: {value}")


def read_ready(data: bytes, child: str) -> dict:
    try:
        result = json.loads(
            data.decode("utf-8", errors="strict"),
            object_pairs_hook=unique,
            parse_constant=reject_constant,
        )
    except (UnicodeError, json.JSONDecodeError) as error:
        raise SystemExit(f"malformed child-ready receipt: {child}:{error.__class__.__name__}")
    expected_keys = {
        "schema_version",
        "kind",
        "run_id",
        "child",
        "pid",
        "pgid",
        "sid",
        "starttime_ticks",
        "boot_id",
        "status",
    }
    if not isinstance(result, dict) or set(result) != expected_keys:
        raise SystemExit(f"child-ready schema differs: {child}")
    if (
        type(result["schema_version"]) is not int
        or result["schema_version"] != 1
        or result["kind"] != "v934-step4-child-ready"
        or result["run_id"] != run_id
        or result["child"] != child
        or type(result["pid"]) is not int
        or result["pid"] <= 1
        or type(result["pgid"]) is not int
        or result["pgid"] != result["pid"]
        or type(result["sid"]) is not int
        or result["sid"] != result["pid"]
        or type(result["starttime_ticks"]) is not int
        or result["starttime_ticks"] <= 0
        or type(result["boot_id"]) is not str
        or re.fullmatch(
            r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}",
            result["boot_id"],
        )
        is None
        or result["status"] != "ready"
    ):
        raise SystemExit(f"child-ready fields differ: {child}")
    return result


def read_env(data: bytes, child: str) -> dict[str, str]:
    try:
        lines = data.decode("utf-8", errors="strict").splitlines()
    except UnicodeError as error:
        raise SystemExit(f"malformed child lifecycle encoding: {child}") from error
    result = {}
    for line in lines:
        if not line or "=" not in line:
            raise SystemExit(f"malformed child lifecycle row: {child}")
        key, value = line.split("=", 1)
        if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key) is None:
            raise SystemExit(f"unsafe child lifecycle key: {child}:{key!r}")
        if key in result:
            raise SystemExit(f"duplicate child lifecycle key: {child}:{key}")
        result[key] = value
    return result


entries = []
for child in children:
    ready_path = ready_root / f"{child}.json"
    complete_path = root / f"{child}-complete.env"
    ready_data, ready_sha = strict_read(
        ready_path,
        ready_root,
        0o600,
        f"child-ready {child}",
    )
    complete_data, complete_sha = strict_read(
        complete_path,
        root,
        0o644,
        f"child completion {child}",
    )
    ready = read_ready(ready_data, child)
    complete = read_env(complete_data, child)
    expected_complete_keys = {
        "run_id",
        "child",
        "leader_pid",
        "leader_sid",
        "leader_starttime_ticks",
        "boot_id",
        "leader_exit_code",
        "leader_reaped",
        "ready_receipt_sha256",
        "process_group_residue",
        "status",
    }
    if set(complete) != expected_complete_keys:
        raise SystemExit(f"child completion schema differs: {child}")
    if (
        complete
        != {
            "run_id": run_id,
            "child": child,
            "leader_pid": str(ready["pid"]),
            "leader_sid": str(ready["sid"]),
            "leader_starttime_ticks": str(ready["starttime_ticks"]),
            "boot_id": ready["boot_id"],
            "leader_exit_code": "0",
            "leader_reaped": "1",
            "ready_receipt_sha256": ready_sha,
            "process_group_residue": "0",
            "status": "passed",
        }
    ):
        raise SystemExit(f"child lifecycle evidence differs: {child}")
    entries.append(
        {
            "child": child,
            "complete_sha256": complete_sha,
            "leader_pid": ready["pid"],
            "leader_sid": ready["sid"],
            "leader_starttime_ticks": ready["starttime_ticks"],
            "boot_id": ready["boot_id"],
            "leader_reaped": 1,
            "process_group_residue": 0,
            "ready_receipt_sha256": ready_sha,
            "status": "passed",
        }
    )

payload = {
    "schema_version": 1,
    "kind": "v934-step4-child-lifecycle",
    "run_id": run_id,
    "child_count": len(entries),
    "children": entries,
    "status": "passed",
}
data = (json.dumps(payload, indent=2, sort_keys=True) + "\n").encode("utf-8")
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
published = False
try:
    descriptor = os.open(
        temporary,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
        0o644,
    )
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())
    os.link(temporary, output, follow_symlinks=False)
    published = True
    temporary.unlink()
    directory_fd = os.open(output.parent, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
    try:
        os.fsync(directory_fd)
    finally:
        os.close(directory_fd)
except OSError:
    temporary.unlink(missing_ok=True)
    if published:
        output.unlink(missing_ok=True)
    raise
PY
}

terminate_active_child() {
  local signal_name="${1:-TERM}" attempt code=0 leader_state=1 group_state=1 signal_state=1
  [[ -n "$ACTIVE_CHILD_PID" ]] || return 0
  if [[ ! "$ACTIVE_CHILD_STARTTIME_TICKS" =~ ^[1-9][0-9]*$ ]]; then
    echo "[v934-step4-coverage] ERROR: refusing to signal an unsealed child PID" >&2
    return 1
  fi

  if [[ "$ACTIVE_CHILD_GROUP_ESTABLISHED" == true ]]; then
    if active_child_group_alive; then
      group_state=0
    else
      group_state=$?
    fi
    case "$group_state" in
      0)
        if signal_active_child "$signal_name" group; then
          signal_state=0
        else
          signal_state=$?
          [[ "$signal_state" -eq 1 ]] || code=1
        fi
        ;;
      1) ;;
      *)
        echo "[v934-step4-coverage] ERROR: refusing to signal a reused process group" >&2
        code=1
        ;;
    esac
  else
    if [[ "$ACTIVE_CHILD_REAPED" == true ]]; then
      leader_state=1
    elif active_child_leader_identity_matches; then
      leader_state=0
    else
      leader_state=$?
    fi
    case "$leader_state" in
      0)
        if signal_active_child "$signal_name" leader; then
          signal_state=0
        else
          signal_state=$?
          [[ "$signal_state" -eq 1 ]] || code=1
        fi
        ;;
      1) ;;
      *)
        echo "[v934-step4-coverage] ERROR: refusing to signal a reused child PID" >&2
        code=1
        ;;
    esac
  fi

  for ((attempt = 0; attempt < 200; attempt++)); do
    if [[ "$ACTIVE_CHILD_REAPED" == true ]]; then
      leader_state=1
    elif active_child_leader_identity_matches; then
      leader_state=0
    else
      leader_state=$?
    fi
    if [[ "$ACTIVE_CHILD_GROUP_ESTABLISHED" == true ]]; then
      if active_child_group_alive; then
        group_state=0
      else
        group_state=$?
      fi
    else
      group_state=1
    fi
    if [[ "$leader_state" -eq 2 || "$group_state" -eq 2 ]]; then
      code=1
      break
    fi
    if [[ "$leader_state" -eq 1 && "$ACTIVE_CHILD_REAPED" != true ]]; then
      wait "$ACTIVE_CHILD_PID" >/dev/null 2>&1 || true
      ACTIVE_CHILD_REAPED=true
    fi
    [[ "$leader_state" -eq 1 && "$group_state" -eq 1 ]] && break
    sleep 0.1
  done

  for ((attempt = 0; attempt < 100; attempt++)); do
    if [[ "$ACTIVE_CHILD_REAPED" == true ]]; then
      leader_state=1
    elif active_child_leader_identity_matches; then
      leader_state=0
    else
      leader_state=$?
    fi
    if [[ "$ACTIVE_CHILD_GROUP_ESTABLISHED" == true ]]; then
      if active_child_group_alive; then
        group_state=0
      else
        group_state=$?
      fi
    else
      group_state=1
    fi
    if [[ "$leader_state" -eq 2 || "$group_state" -eq 2 ]]; then
      code=1
      break
    fi
    if [[ "$leader_state" -eq 1 && "$ACTIVE_CHILD_REAPED" != true ]]; then
      wait "$ACTIVE_CHILD_PID" >/dev/null 2>&1 || true
      ACTIVE_CHILD_REAPED=true
    fi
    [[ "$leader_state" -eq 1 && "$group_state" -eq 1 ]] && break
    if ((attempt % 20 == 0)); then
      if [[ "$group_state" -eq 0 ]]; then
        if signal_active_child KILL group; then
          signal_state=0
        else
          signal_state=$?
          [[ "$signal_state" -eq 1 ]] || code=1
        fi
      elif [[ "$leader_state" -eq 0 ]]; then
        if signal_active_child KILL leader; then
          signal_state=0
        else
          signal_state=$?
          [[ "$signal_state" -eq 1 ]] || code=1
        fi
      fi
    fi
    sleep 0.05
  done

  if [[ "$ACTIVE_CHILD_REAPED" == true ]]; then
    leader_state=1
  elif active_child_leader_identity_matches; then
    leader_state=0
  else
    leader_state=$?
  fi
  if [[ "$ACTIVE_CHILD_GROUP_ESTABLISHED" == true ]]; then
    if active_child_group_alive; then
      group_state=0
    else
      group_state=$?
    fi
  else
    group_state=1
  fi
  if [[ "$leader_state" -eq 1 && "$ACTIVE_CHILD_REAPED" != true ]]; then
    wait "$ACTIVE_CHILD_PID" >/dev/null 2>&1 || true
    ACTIVE_CHILD_REAPED=true
  fi
  if [[ "$leader_state" -ne 1 || "$group_state" -ne 1 ]]; then
    echo "[v934-step4-coverage] ERROR: child identity/group survived or became ambiguous: $ACTIVE_CHILD_PID" >&2
    code=1
  fi
  if [[ "$code" -eq 0 ]]; then
    ACTIVE_CHILD_PID=""
    ACTIVE_CHILD_NAME=""
    ACTIVE_CHILD_GROUP_ESTABLISHED=false
    ACTIVE_CHILD_REAPED=false
    ACTIVE_CHILD_STARTTIME_TICKS=""
    ACTIVE_CHILD_SID=""
    ACTIVE_CHILD_BOOT_ID=""
    ACTIVE_CHILD_READY_SHA256=""
    ACTIVE_CHILD_READY_DEV=""
    ACTIVE_CHILD_READY_INO=""
    ACTIVE_CHILD_EXPECTED_PPID=""
  fi
  return "$code"
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
  local code=0 database scope_hash project label count unit_child_scope unit_child_id unit_project_scope unit_project
  local containers=0 volumes=0 networks=0
  docker info >/dev/null 2>&1 || return 1
  remove_labeled_resources "com.foggy.v934.external-run=$RUN_ID" || code=1
  remove_labeled_resources "com.foggy.v934.preagg-run=$RUN_ID" || code=1
  unit_child_scope="$(printf '%s\n' "$RUN_ID|unit-mysql57" | sha256sum | cut -c1-16)"
  unit_child_id="unit-mysql57-$unit_child_scope"
  unit_project_scope="$(printf '%s\n' "$unit_child_id|mysql57" | sha256sum | cut -c1-12)"
  unit_project="v934db-mysql57-$unit_project_scope"
  python3 "$UNIT_FIXTURE_TOOL" cleanup --repo-root "$ROOT_DIR" --run-id "$RUN_ID" || code=1
  for database in mysql57 mysql8 postgres15 sqlserver2022; do
    scope_hash="$(printf '%s\n' "$RUN_ID|$database" | sha256sum | cut -c1-12)"
    project="v934db-${database}-${scope_hash}"
    remove_labeled_resources "com.docker.compose.project=$project" || code=1
  done
  for label in \
    "com.foggy.v934.external-run=$RUN_ID" \
    "com.foggy.v934.preagg-run=$RUN_ID" \
    "com.docker.compose.project=$unit_project"; do
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

write_failed_run_status() {
  local exit_code="$1" finished_at
  [[ "$exit_code" -ne 0 ]] || return 1
  finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)" || return 1
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
    "summary_sha256=absent" \
    "status=failed"
}

finalize_run() {
  local exit_code="$1" finalizer_code=0 sealed=false
  trap '' INT TERM HUP
  trap - EXIT
  set +e
  terminate_active_child TERM || finalizer_code=1
  if [[ "$CLEANUP_DONE" != true ]]; then
    cleanup_all_resources || finalizer_code=1
  fi
  if [[ "$RUN_LOG_OPEN" == true || -n "$RUN_LOG_TEE_PID" || -n "$RUN_LOG_FILE_FD" ]]; then
    close_run_log || finalizer_code=1
  fi
  [[ "$exit_code" -ne 0 || "$PHASE" == completed ]] || exit_code=1
  if [[ "$finalizer_code" -ne 0 && "$exit_code" -eq 0 ]]; then
    exit_code=1
  fi

  if [[ "$exit_code" -eq 0 ]]; then
    if python3 "$COVERAGE_XML_TOOL" seal-run \
      --mode "$MODE" \
      --repo-root "$ROOT_DIR" \
      --run-id "$RUN_ID"; then
      sealed=true
    else
      exit_code=1
    fi
  fi

  if [[ "$sealed" == true ]]; then
    if [[ "$MODE" == diagnostic ]]; then
      echo "[v934-step4-coverage] DIAGNOSTIC PASS run=$RUN_ID exec=23/48 reports=774/59/5709 addon=2/6 acceptance=not-generated"
    elif [[ "$MODE" == release ]]; then
      echo "[v934-step4-coverage] RELEASE PASS run=$RUN_ID exec=23/48 reports=774/59/5709 addon=2/6 acceptance=successor-final"
    else
      echo "[v934-step4-coverage] FORMAL PASS run=$RUN_ID exec=23/48 reports=774/59/5709 addon=2/6 acceptance=final"
    fi
  else
    # A failed run cannot retain artifacts that could be mistaken for a
    # successful summary/final.  The canonical green marker is no-clobber; if
    # it somehow exists after a failing seal command, preserve it and refuse
    # to manufacture a contradictory failed marker.
    rm -f -- "$RUN_ROOT/summary.env" "$RUN_ROOT/final-manifest.json" || finalizer_code=1
    [[ ! -e "$RUN_ROOT/summary.env" && ! -L "$RUN_ROOT/summary.env" ]] || finalizer_code=1
    [[ ! -e "$RUN_ROOT/final-manifest.json" && ! -L "$RUN_ROOT/final-manifest.json" ]] || finalizer_code=1
    if [[ -e "$RUN_ROOT/run-status.env" || -L "$RUN_ROOT/run-status.env" ]]; then
      finalizer_code=1
      echo "[v934-step4-coverage] ERROR: refusing to overwrite an existing run status after seal failure" >&2
    elif ! write_failed_run_status "$exit_code"; then
      finalizer_code=1
    fi
    [[ "$exit_code" -ne 0 ]] || exit_code=1
    echo "[v934-step4-coverage] FAILED run=$RUN_ID phase=$PHASE" >&2
  fi
  exit "$exit_code"
}

handle_signal() {
  local signal_name="$1" exit_code="$2"
  if [[ "$CHILD_SIGNAL_CRITICAL" == true ]]; then
    if [[ -z "$PENDING_SIGNAL_CODE" ]]; then
      PENDING_SIGNAL_NAME="$signal_name"
      PENDING_SIGNAL_CODE="$exit_code"
    fi
    return 0
  fi
  trap '' INT TERM HUP
  # Cleanup failure must not replace the shell-compatible code for the
  # original signal. The EXIT finalizer will retry while the active identity
  # remains frozen.
  terminate_active_child "$signal_name" || :
  exit "$exit_code"
}

drain_pending_signal() {
  local signal_name="$PENDING_SIGNAL_NAME" exit_code="$PENDING_SIGNAL_CODE"
  PENDING_SIGNAL_NAME=""
  PENDING_SIGNAL_CODE=""
  [[ -z "$exit_code" ]] || handle_signal "$signal_name" "$exit_code"
}

run_child() {
  local child="$1" exit_code=0 attempt capture_code=0 group_state=1
  local ready_path ready_identity ready_recheck
  local ready_sha256 ready_dev ready_ino ready_sid ready_starttime ready_boot_id
  PHASE="child-$child"
  echo "[v934-step4-coverage] running child=$child"
  [[ -z "$ACTIVE_CHILD_PID" ]] || fail "previous child identity remains active"
  ready_path="$CHILD_READY_ROOT/$child.json"
  ACTIVE_CHILD_NAME="$child"
  ACTIVE_CHILD_GROUP_ESTABLISHED=false
  ACTIVE_CHILD_REAPED=false
  ACTIVE_CHILD_STARTTIME_TICKS=""
  ACTIVE_CHILD_SID=""
  ACTIVE_CHILD_BOOT_ID=""
  ACTIVE_CHILD_READY_SHA256=""
  ACTIVE_CHILD_READY_DEV=""
  ACTIVE_CHILD_READY_INO=""
  ACTIVE_CHILD_EXPECTED_PPID="$BASHPID"
  PENDING_SIGNAL_NAME=""
  PENDING_SIGNAL_CODE=""
  CHILD_SIGNAL_CRITICAL=true
  python3 "$COVERAGE_TOOL" launch-child \
    --repo-root "$ROOT_DIR" \
    --child "$child" \
    --run-id "$RUN_ID" \
    --lock-fd "$V934_AUTHORITY_LOCK_FD" \
    --ready-path "$ready_path" &
  ACTIVE_CHILD_PID=$!
  # A numeric group is never trusted before the O_NOFOLLOW ready receipt has
  # been validated and its immutable identity frozen.
  if capture_active_child_identity; then
    :
  else
    capture_code=$?
    if [[ "$capture_code" -eq 1 ]]; then
      wait "$ACTIVE_CHILD_PID" >/dev/null 2>&1 || true
      ACTIVE_CHILD_REAPED=true
    fi
    CHILD_SIGNAL_CRITICAL=false
    drain_pending_signal
    fail "cannot seal initial child process identity: $child"
  fi
  for ((attempt = 0; attempt < 100; attempt++)); do
    [[ -f "$ready_path" || -L "$ready_path" ]] && break
    if active_child_leader_identity_matches; then
      :
    else
      break
    fi
    sleep 0.05
  done
  if [[ ! -f "$ready_path" || -L "$ready_path" ]]; then
    terminate_active_child TERM || :
    CHILD_SIGNAL_CRITICAL=false
    drain_pending_signal
    fail "child failed to publish its canonical process-group receipt: $child"
  fi
  if ready_identity="$(validate_child_ready_receipt \
    "$child" "$ready_path" "$ACTIVE_CHILD_PID" \
    "$ACTIVE_CHILD_STARTTIME_TICKS" "$ACTIVE_CHILD_EXPECTED_PPID")"; then
    :
  else
    terminate_active_child TERM || :
    CHILD_SIGNAL_CRITICAL=false
    drain_pending_signal
    fail "child process-group receipt is invalid: $child"
  fi
  IFS=$'\t' read -r \
    ready_sha256 ready_dev ready_ino ready_sid ready_starttime ready_boot_id \
    <<< "$ready_identity"
  if (
    [[ ! "$ready_sha256" =~ ^[0-9a-f]{64}$ ]] ||
    [[ ! "$ready_dev" =~ ^[0-9]+$ ]] ||
    [[ ! "$ready_ino" =~ ^[1-9][0-9]*$ ]] ||
    [[ "$ready_sid" != "$ACTIVE_CHILD_PID" ]] ||
    [[ "$ready_starttime" != "$ACTIVE_CHILD_STARTTIME_TICKS" ]] ||
    [[ "$ready_boot_id" != "$ACTIVE_CHILD_BOOT_ID" ]] ||
    [[ ! "$ready_boot_id" =~ ^[0-9a-f-]{36}$ ]]
  ); then
    terminate_active_child TERM || :
    CHILD_SIGNAL_CRITICAL=false
    drain_pending_signal
    fail "child process-group identity cannot be frozen: $child"
  fi
  ACTIVE_CHILD_READY_SHA256="$ready_sha256"
  ACTIVE_CHILD_READY_DEV="$ready_dev"
  ACTIVE_CHILD_READY_INO="$ready_ino"
  ACTIVE_CHILD_SID="$ready_sid"
  ACTIVE_CHILD_BOOT_ID="$ready_boot_id"
  ACTIVE_CHILD_GROUP_ESTABLISHED=true
  CHILD_SIGNAL_CRITICAL=false
  drain_pending_signal

  if wait "$ACTIVE_CHILD_PID"; then
    exit_code=0
  else
    exit_code=$?
  fi
  ACTIVE_CHILD_REAPED=true
  if [[ "$exit_code" -ne 0 ]]; then
    if active_child_group_alive; then
      group_state=0
      if ! snapshot_active_child_group nonzero; then
        terminate_active_child TERM || :
        fail "child failed and live-residue snapshot failed: $child rc=$exit_code"
      fi
    else
      group_state=$?
    fi
    if [[ "$group_state" -eq 2 ]]; then
      record_active_child_error identity-conflict "numeric process group identity changed" || true
    fi
    terminate_active_child TERM || :
    fail "child failed: $child rc=$exit_code"
  fi
  if confirm_active_child_group_absent; then
    group_state=1
  else
    group_state=$?
    [[ "$group_state" -ne 1 ]] || group_state=0
  fi
  if [[ "$group_state" -eq 0 ]] && ! snapshot_active_child_group success; then
    terminate_active_child TERM || :
    fail "child returned with live residue and snapshot failed: $child"
  fi
  if [[ "$group_state" -eq 0 ]]; then
    terminate_active_child TERM || :
    fail "child returned with live process-group residue: $child"
  fi
  if [[ "$group_state" -eq 2 ]]; then
    record_active_child_error identity-conflict "numeric process group identity changed" || true
    terminate_active_child TERM || :
    fail "child process-group identity became ambiguous: $child"
  fi

  if ready_recheck="$(validate_child_ready_receipt \
    "$child" "$ready_path" "$ACTIVE_CHILD_PID" \
    "$ACTIVE_CHILD_STARTTIME_TICKS" "$ACTIVE_CHILD_EXPECTED_PPID")"; then
    :
  else
    fail "child-ready receipt failed completion revalidation: $child"
  fi
  if [[ "$ready_recheck" != "$ACTIVE_CHILD_READY_SHA256"$'\t'"$ACTIVE_CHILD_READY_DEV"$'\t'"$ACTIVE_CHILD_READY_INO"$'\t'"$ACTIVE_CHILD_SID"$'\t'"$ACTIVE_CHILD_STARTTIME_TICKS"$'\t'"$ACTIVE_CHILD_BOOT_ID" ]]; then
    fail "child-ready receipt identity/hash changed before completion: $child"
  fi
  atomic_env "$CHILD_LIFECYCLE_ROOT/$child-complete.env" \
    "run_id=$RUN_ID" \
    "child=$child" \
    "leader_pid=$ACTIVE_CHILD_PID" \
    "leader_sid=$ACTIVE_CHILD_SID" \
    "leader_starttime_ticks=$ACTIVE_CHILD_STARTTIME_TICKS" \
    "boot_id=$ACTIVE_CHILD_BOOT_ID" \
    "leader_exit_code=0" \
    "leader_reaped=1" \
    "ready_receipt_sha256=$ACTIVE_CHILD_READY_SHA256" \
    "process_group_residue=0" \
    "status=passed"
  ACTIVE_CHILD_PID=""
  ACTIVE_CHILD_NAME=""
  ACTIVE_CHILD_GROUP_ESTABLISHED=false
  ACTIVE_CHILD_REAPED=false
  ACTIVE_CHILD_STARTTIME_TICKS=""
  ACTIVE_CHILD_SID=""
  ACTIVE_CHILD_BOOT_ID=""
  ACTIVE_CHILD_READY_SHA256=""
  ACTIVE_CHILD_READY_DEV=""
  ACTIVE_CHILD_READY_INO=""
  ACTIVE_CHILD_EXPECTED_PPID=""
}

MODE=diagnostic
RUN_ID=""
case "$#" in
  0) ;;
  1)
    case "$1" in
      diagnostic|formal|release) MODE="$1" ;;
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
[[ "$MODE" == diagnostic || "$MODE" == formal || "$MODE" == release ]] || \
  fail "mode must be diagnostic, formal, or release"
RUN_ID="${RUN_ID:-step4-coverage-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
[[ "$RUN_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ && "$RUN_ID" != . && "$RUN_ID" != .. && "${#RUN_ID}" -le 128 ]] || \
  fail "unsafe run id: $RUN_ID"

for command_name in cmp cut date docker env find flock git grep mkfifo mvn ps python3 rg sed sha256sum sleep ss tee tr wc; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done
for required_file in \
  "$SCRIPT_PATH" "$AUTHORITY_LIB" "$STEP4_AUTHORITY_LIB" "$COVERAGE_TOOL" \
  "$REPORT_VIEW_TOOL" "$REPORT_INVENTORY_TOOL" "$COVERAGE_REPORT_RUNNER" \
  "$COVERAGE_EXEC_TOOL" "$COVERAGE_XML_TOOL" "$COVERAGE_XML_NEGATIVE_TOOL" \
  "$COVERAGE_CONTRACT_NEGATIVE_TOOL" \
  "$UNIT_FIXTURE_TOOL" \
  "$RUN_LOG_LIFECYCLE_NEGATIVE" "$RUN_LOG_LIB" \
  "$TOOLCHAIN_RECEIPT_TOOL" \
  "$COVERAGE_CONTRACT" \
  "$COVERAGE_THRESHOLDS" "$STEP4_MANIFEST" "$CALCULATE_PARITY_CATALOG" \
  "$SUCCESSOR_OVERLAY_TOOL" "$AUTHORITY_NEGATIVE" \
  "$STEP1_FREEZE" "$JACOCO_AGENT_JAR"; do
  require_real_file "$required_file"
done
"$RUN_LOG_LIFECYCLE_NEGATIVE" --verify-runner-seal-bindings \
  "$ROOT_DIR" "$SCRIPT_PATH" "$STEP4_MANIFEST" || \
  fail "runner raw seal binding preflight failed"
[[ "$(sha256_file "$JACOCO_AGENT_JAR")" == "$JACOCO_AGENT_SHA256" ]] || \
  fail "JaCoCo 0.8.12 runtime agent hash differs"
[[ "$(git -c core.fsmonitor=false -c core.untrackedCache=false -c core.hooksPath=/dev/null -C "$ROOT_DIR" rev-parse --show-toplevel)" == "$ROOT_DIR" ]] || \
  fail "repository root differs from the canonical Git worktree root"
verify_calculate_parity_catalog
if SOURCE_PREFLIGHT_RESULT="$(python3 "$COVERAGE_TOOL" source-hash \
  --repo-root "$ROOT_DIR")"; then
  :
else
  source_preflight_code=$?
  printf '%s\n' "$SOURCE_PREFLIGHT_RESULT" >&2
  fail "source preflight failed: rc=$source_preflight_code"
fi
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
  [[ -z "${!variable_name:-}" ]] || fail "$variable_name must be empty for a Step 4 coverage run"
done
if [[ "${MAVEN_OPTS:-}" =~ (^|[[:space:],])(-T|--threads)([[:space:]=,0-9C]|$) ]] ||
   [[ "${MAVEN_OPTS:-}" =~ (^|[[:space:],])!?coverage([[:space:],]|$) ]] ||
   [[ "${MAVEN_OPTS:-}" =~ (v934-coverage|jacoco\.|v934\.coverage\.|[Ss][Pp][Rr][Ii][Nn][Gg][._]|(^|[[:space:]])-D(argLine|test|it\.test|failIfNoTests|surefire\.|failsafe\.)) ]]; then
  fail "MAVEN_OPTS contains a forbidden coverage, selector, or parallel override"
fi
while IFS='=' read -r environment_key _; do
  [[ "$environment_key" != SPRING_* ]] || fail "ambient Spring environment is forbidden: $environment_key"
done < <(env)
for variable_name in JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS; do
  [[ ! "${!variable_name:-}" =~ (-javaagent|jacoco|[Ss][Pp][Rr][Ii][Nn][Gg][._]) ]] || \
    fail "$variable_name contains an external Java agent or Spring override"
done
for config_file in "$ROOT_DIR/.mvn/maven.config" "$ROOT_DIR/.mvn/jvm.config"; do
  if [[ -e "$config_file" || -L "$config_file" ]]; then
    [[ -f "$config_file" && ! -L "$config_file" ]] || fail "Maven config is not a real file: $config_file"
    ! grep -Eq '[Ss][Pp][Rr][Ii][Nn][Gg][._]' "$config_file" || \
      fail "Maven config contains a Spring override: $config_file"
  fi
done

if CONTRACT_VALIDATION_JSON="$(python3 "$COVERAGE_TOOL" validate-contract \
  --repo-root "$ROOT_DIR")"; then
  :
else
  contract_validation_code=$?
  printf '%s\n' "$CONTRACT_VALIDATION_JSON" >&2
  fail "Step 4 contract preflight failed: rc=$contract_validation_code"
fi
mapfile -t CONTRACT_STATE_FIELDS < <(python3 - "$CONTRACT_VALIDATION_JSON" <<'PY'
import json
import sys

def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise SystemExit(f"duplicate threshold key: {key}")
        result[key] = value
    return result

def reject_constant(value):
    raise SystemExit(f"non-finite contract result number: {value}")

payload = json.loads(
    sys.argv[1],
    object_pairs_hook=unique,
    parse_constant=reject_constant,
)
if (
    not isinstance(payload, dict)
    or payload.get("command") != "validate-contract"
    or payload.get("status") != "passed"
    or payload.get("workflow_state") not in {"diagnostic", "formal"}
    or payload.get("threshold_status") not in {"diagnostic-pending", "confirmed"}
):
    raise SystemExit("contract validation result state differs")
print(payload["workflow_state"])
print(payload["threshold_status"])
PY
)
[[ "${#CONTRACT_STATE_FIELDS[@]}" -eq 2 ]] || fail "cannot parse Step 4 workflow state"
WORKFLOW_STATE="${CONTRACT_STATE_FIELDS[0]}"
THRESHOLD_STATUS="${CONTRACT_STATE_FIELDS[1]}"
if [[ "$MODE" == diagnostic ]]; then
  [[ "$WORKFLOW_STATE" == diagnostic && "$THRESHOLD_STATUS" == diagnostic-pending ]] || \
    fail "diagnostic requires diagnostic-ready/diagnostic-pending, got $WORKFLOW_STATE/$THRESHOLD_STATUS"
else
  [[ "$WORKFLOW_STATE" == formal && "$THRESHOLD_STATUS" == confirmed ]] || \
    fail "$MODE requires formal-ready/confirmed, got $WORKFLOW_STATE/$THRESHOLD_STATUS"
fi

# The top-level runner is the only owner of the canonical authority lock.
# Every child receives this same inheritable open-file description.
# shellcheck source=scripts/v934/authority_runner_lib.sh
source "$AUTHORITY_LIB"
# shellcheck source=scripts/v934/step4/authority_parent_lib.sh
source "$STEP4_AUTHORITY_LIB"
# shellcheck source=scripts/v934/step4/run_log_lifecycle_lib.sh
source "$RUN_LOG_LIB"
v934_acquire_authority_lock "$ROOT_DIR" "v934-step4-coverage" || exit 1
export V934_AUTHORITY_LOCK_FD

PHASE=bootstrap
ACTIVE_CHILD_PID=""
ACTIVE_CHILD_NAME=""
ACTIVE_CHILD_GROUP_ESTABLISHED=false
ACTIVE_CHILD_REAPED=false
ACTIVE_CHILD_STARTTIME_TICKS=""
ACTIVE_CHILD_SID=""
ACTIVE_CHILD_BOOT_ID=""
ACTIVE_CHILD_READY_SHA256=""
ACTIVE_CHILD_READY_DEV=""
ACTIVE_CHILD_READY_INO=""
ACTIVE_CHILD_EXPECTED_PPID=""
CHILD_SIGNAL_CRITICAL=false
PENDING_SIGNAL_NAME=""
PENDING_SIGNAL_CODE=""
RUN_LOG_FIFO=""
RUN_LOG_TEE_PID=""
RUN_LOG_TEE_STARTTIME_TICKS=""
RUN_LOG_TEE_BOOT_ID=""
RUN_LOG_TEE_EXPECTED_PPID=""
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
COVERAGE_XML_GENERIC_NEGATIVE_ROOT="$NEGATIVE_ROOT/coverage-xml-generic"
CHILD_LIFECYCLE_ROOT="$RUN_ROOT/child-lifecycle"
CHILD_READY_ROOT="$RUN_ROOT/child-ready"
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

mkdir -- \
  "$EXEC_ROOT" "$NEGATIVE_ROOT" "$CHILD_READY_ROOT" \
  "$CHILD_LIFECYCLE_ROOT" "$TOOLCHAIN_REPLAY_ROOT" \
  "$COVERAGE_XML_GENERIC_NEGATIVE_ROOT"
ensure_real_directory "$EXEC_ROOT"
ensure_real_directory "$NEGATIVE_ROOT"
ensure_real_directory "$CHILD_READY_ROOT"
ensure_real_directory "$CHILD_LIFECYCLE_ROOT"
ensure_real_directory "$TOOLCHAIN_REPLAY_ROOT"
ensure_real_directory "$COVERAGE_XML_GENERIC_NEGATIVE_ROOT"

exec 3>&1 4>&2
open_run_log_file || fail "cannot create a no-clobber run log"
RUN_LOG_FIFO="$RUN_ROOT/.run-log.fifo"
mkfifo -- "$RUN_LOG_FIFO"
v934_run_log_begin_signal_critical || fail "cannot enter the run-logger spawn critical section"
(
  trap - INT TERM HUP PIPE
  # Close the inherited authority before the potentially blocking FIFO open.
  # Keeping the redirection outside this body would acquire the FIFO first.
  v934_run_log_close_fd_number "$V934_AUTHORITY_LOCK_FD" || exit 93
  exec 0< "$RUN_LOG_FIFO"
  exec 1>&3 2>&4
  exec 3>&- 4>&-
  exec tee -a -- "/proc/self/fd/$RUN_LOG_FILE_FD"
) &
RUN_LOG_TEE_PID=$!
V934_RUN_LOG_LOGGER_PID="$RUN_LOG_TEE_PID"
if ! v934_run_log_capture_logger_identity "$BASHPID"; then
  if v934_run_log_release_unsealed_fifo_logger \
    "$RUN_LOG_FIFO" "$RUN_LOG_TEE_PID"; then
    RUN_LOG_TEE_PID=""
    rm -f -- "$RUN_LOG_FIFO" || true
    RUN_LOG_FIFO=""
  else
    echo "[v934-step4-coverage] ERROR: unsealed run logger did not reap after FIFO release" >&2
  fi
  pending_signal_code="$V934_RUN_LOG_PENDING_SIGNAL_CODE"
  v934_run_log_restore_signal_traps
  [[ -z "$pending_signal_code" ]] || exit "$pending_signal_code"
  fail "cannot seal the run-logger process identity"
fi
RUN_LOG_TEE_STARTTIME_TICKS="$V934_RUN_LOG_LOGGER_STARTTIME_TICKS"
RUN_LOG_TEE_BOOT_ID="$V934_RUN_LOG_LOGGER_BOOT_ID"
RUN_LOG_TEE_EXPECTED_PPID="$V934_RUN_LOG_LOGGER_EXPECTED_PPID"
if ! exec > "$RUN_LOG_FIFO" 2>&1; then
  pending_signal_code="$V934_RUN_LOG_PENDING_SIGNAL_CODE"
  v934_run_log_restore_signal_traps
  [[ -z "$pending_signal_code" ]] || exit "$pending_signal_code"
  fail "cannot connect the run logger FIFO writer"
fi
RUN_LOG_OPEN=true
if ! rm -f -- "$RUN_LOG_FIFO"; then
  pending_signal_code="$V934_RUN_LOG_PENDING_SIGNAL_CODE"
  v934_run_log_restore_signal_traps
  [[ -z "$pending_signal_code" ]] || exit "$pending_signal_code"
  fail "cannot unlink the connected run logger FIFO"
fi
RUN_LOG_FIFO=""
pending_signal_name="$V934_RUN_LOG_PENDING_SIGNAL_NAME"
pending_signal_code="$V934_RUN_LOG_PENDING_SIGNAL_CODE"
v934_run_log_restore_signal_traps
[[ -z "$pending_signal_code" ]] || handle_signal "$pending_signal_name" "$pending_signal_code"

PHASE=contract-validate
CURRENT_CONTRACT_VALIDATION_JSON="$(python3 "$COVERAGE_TOOL" validate-contract \
  --repo-root "$ROOT_DIR")" || fail "Step 4 contract validation failed inside the run authority"
[[ "$CURRENT_CONTRACT_VALIDATION_JSON" == "$CONTRACT_VALIDATION_JSON" ]] || \
  fail "Step 4 contract validation result changed after preflight"
printf '%s\n' "$CURRENT_CONTRACT_VALIDATION_JSON"
python3 "$SUCCESSOR_OVERLAY_TOOL" validate

if [[ "$MODE" == formal ]]; then
  PHASE=formalization-delta
  python3 "$COVERAGE_TOOL" validate-formal-delta \
    --repo-root "$ROOT_DIR" \
    --output "$RUN_ROOT/formalization-delta.json"
  require_real_file "$RUN_ROOT/formalization-delta.json"
else
  [[ ! -e "$RUN_ROOT/formalization-delta.json" && ! -L "$RUN_ROOT/formalization-delta.json" ]] || \
    fail "non-Cfreeze run contains a formalization delta"
fi

PHASE=bootstrap-negative
run_sensitive_pattern_regression_probes
"$AUTHORITY_NEGATIVE"
python3 "$COVERAGE_CONTRACT_NEGATIVE_TOOL" \
  --repo-root "$ROOT_DIR" \
  --output "$NEGATIVE_ROOT/coverage-contract.json"
"$RUN_LOG_LIFECYCLE_NEGATIVE" | tee "$NEGATIVE_ROOT/run-log-lifecycle.txt"
python3 "$COVERAGE_XML_NEGATIVE_TOOL" \
  --repo-root "$ROOT_DIR" \
  --output "$COVERAGE_XML_GENERIC_NEGATIVE_ROOT/negative-result.json"
python3 "$SUCCESSOR_OVERLAY_TOOL" negative \
  --output "$NEGATIVE_ROOT/successor-overlay-probes.tsv"

PHASE=source-before
NOT_BEFORE_NS="$(python3 -c 'import time; print(time.time_ns())')"
[[ "$NOT_BEFORE_NS" =~ ^[1-9][0-9]*$ ]] || fail "cannot establish the coverage not-before boundary"
if SOURCE_RESULT="$(python3 "$COVERAGE_TOOL" source-hash \
  --repo-root "$ROOT_DIR" --output "$RUN_ROOT/source-before.tsv")"; then
  :
else
  source_hash_code=$?
  printf '%s\n' "$SOURCE_RESULT" >&2
  fail "source-before seal failed: rc=$source_hash_code"
fi
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
PHASE=unit-mysql57-post-unit-boundary
python3 "$UNIT_FIXTURE_TOOL" verify \
  --repo-root "$ROOT_DIR" \
  --run-id "$RUN_ID" \
  --manifest "$ROOT_DIR/target/v934-step2-unit/runs/$RUN_ID/mysql57-fixture-manifest.json"
run_child integration
PHASE=unit-mysql57-pre-step3-boundary
python3 "$UNIT_FIXTURE_TOOL" verify \
  --repo-root "$ROOT_DIR" \
  --run-id "$RUN_ID" \
  --manifest "$ROOT_DIR/target/v934-step2-unit/runs/$RUN_ID/mysql57-fixture-manifest.json"
run_child step3-required

PHASE=child-lifecycle-verify
publish_child_lifecycle_manifest
require_real_file "$RUN_ROOT/child-lifecycle.json"

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
if SOURCE_RESULT="$(python3 "$COVERAGE_TOOL" source-hash \
  --repo-root "$ROOT_DIR" --output "$RUN_ROOT/source-after.tsv")"; then
  :
else
  source_hash_code=$?
  printf '%s\n' "$SOURCE_RESULT" >&2
  fail "source-after seal failed: rc=$source_hash_code"
fi
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
[[ "$(git -c core.fsmonitor=false -c core.untrackedCache=false -c core.hooksPath=/dev/null -C "$ROOT_DIR" rev-parse --verify 'HEAD^{commit}')" == "$GIT_HEAD" ]] || \
  fail "Git HEAD changed during diagnostic execution"

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

PHASE="${MODE}-summary"
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
SUMMARY_REQUIRED_FILES=( \
  "$RUN_ROOT/report-inventory.json" "$RUN_ROOT/coverage-observation.json" \
  "$RUN_ROOT/class-universe.json" "$RUN_ROOT/child-lifecycle.json" \
  "$RUN_ROOT/toolchain-receipt.json" \
  "$TOOLCHAIN_REPLAY_ROOT/pre-compile-seal.env" \
  "$TOOLCHAIN_REPLAY_ROOT/post-children.env" \
  "$TOOLCHAIN_REPLAY_ROOT/post-reporter.env" \
  "$TOOLCHAIN_REPLAY_ROOT/post-model.env" \
  "$RUN_ROOT/report/toolchain-replay-pre.json" \
  "$RUN_ROOT/report/toolchain-replay-post.json" \
  "$NEGATIVE_ROOT/successor-overlay-probes.tsv" \
  "$NEGATIVE_ROOT/coverage-contract.json" \
  "$NEGATIVE_ROOT/run-log-lifecycle.txt" \
  "$NEGATIVE_ROOT/toolchain-receipt.json" \
  "$NEGATIVE_ROOT/effective-reporter-pom.json" \
  "$NEGATIVE_ROOT/report-inventory-probes.tsv" \
  "$NEGATIVE_ROOT/coverage-exec/negative-result.json" \
  "$NEGATIVE_ROOT/coverage-xml/negative-result.json" \
  "$COVERAGE_XML_GENERIC_NEGATIVE_ROOT/negative-result.json" \
  "$RUN_ROOT/model-gate.env" "$RUN_ROOT/cleanup.env" "$RUN_ROOT/sensitive-scan.env"
)
if [[ "$MODE" == formal ]]; then
  SUMMARY_REQUIRED_FILES+=("$RUN_ROOT/formalization-delta.json")
fi
for required_file in "${SUMMARY_REQUIRED_FILES[@]}"; do
  require_real_file "$required_file"
done
SUMMARY_ROWS=( \
  "run_id=$RUN_ID" \
  "mode=$MODE" \
  "git_head=$GIT_HEAD" \
  "threshold_status=$THRESHOLD_STATUS" \
  "source_before_sha256=$SOURCE_BEFORE" \
  "source_after_sha256=$SOURCE_AFTER" \
  "coverage_contract_sha256=$CONTRACT_SHA256" \
  "outer_marker_sha256=$OUTER_MARKER_SHA256" \
  "class_universe_sha256=$(sha256_file "$RUN_ROOT/class-universe.json")" \
  "child_lifecycle_sha256=$(sha256_file "$RUN_ROOT/child-lifecycle.json")"
)
if [[ "$MODE" == formal ]]; then
  SUMMARY_ROWS+=("formalization_delta_sha256=$(sha256_file "$RUN_ROOT/formalization-delta.json")")
elif [[ "$MODE" == release ]]; then
  SUMMARY_ROWS+=("release_successor=confirmed-threshold-post-step4-replay")
fi
SUMMARY_ROWS+=( \
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
  "coverage_xml_generic_negative_sha256=$(sha256_file "$COVERAGE_XML_GENERIC_NEGATIVE_ROOT/negative-result.json")" \
  "run_log_lifecycle_negative_sha256=$(sha256_file "$NEGATIVE_ROOT/run-log-lifecycle.txt")" \
  "model_gate_sha256=$(sha256_file "$RUN_ROOT/model-gate.env")" \
  "cleanup_sha256=$(sha256_file "$RUN_ROOT/cleanup.env")" \
  "sensitive_scan_sha256=$(sha256_file "$RUN_ROOT/sensitive-scan.env")" \
  "exec_files=23" \
  "sessions=48" \
  "required_reports=774" \
  "required_structural_reports=59" \
  "required_testcase_nodes=5709" \
  "addon_reports=2" \
  "addon_testcase_nodes=6" \
  "model_external_gate=passed" \
  "acceptance_candidate=$([[ "$MODE" == diagnostic ]] && printf not-generated || printf required)" \
  "status=$([[ "$MODE" == diagnostic ]] && printf diagnostic-observed || { [[ "$MODE" == formal ]] && printf formal-candidate-ready || printf release-candidate-ready; })"
)
atomic_env "$RUN_ROOT/summary.env" "${SUMMARY_ROWS[@]}"

if [[ "$MODE" != diagnostic ]]; then
  PHASE="${MODE}-coverage-gate"
  python3 "$COVERAGE_XML_TOOL" formal-check \
    --mode "$MODE" \
    --repo-root "$ROOT_DIR" \
    --run-id "$RUN_ID" \
    --output "$RUN_ROOT/coverage-gate.json"
  PHASE="${MODE}-candidate"
  python3 "$COVERAGE_XML_TOOL" build-artifact \
    --mode "$MODE" \
    --repo-root "$ROOT_DIR" \
    --stage candidate \
    --run-id "$RUN_ID" \
    --coverage-gate "$RUN_ROOT/coverage-gate.json" \
    --output "$RUN_ROOT/candidate-manifest.json"
  python3 "$COVERAGE_XML_TOOL" verify-artifact \
    --mode "$MODE" \
    --repo-root "$ROOT_DIR" \
    --artifact "$RUN_ROOT/candidate-manifest.json"
  PHASE="${MODE}-final-preseal"
  python3 "$COVERAGE_XML_TOOL" build-artifact \
    --mode "$MODE" \
    --repo-root "$ROOT_DIR" \
    --stage final \
    --candidate "$RUN_ROOT/candidate-manifest.json" \
    --output "$RUN_ROOT/final-manifest.json"
else
  for forbidden_formal_artifact in \
    coverage-gate.json candidate-manifest.json final-manifest.json; do
    [[ ! -e "$RUN_ROOT/$forbidden_formal_artifact" && ! -L "$RUN_ROOT/$forbidden_formal_artifact" ]] || \
      fail "diagnostic run contains formal artifact: $forbidden_formal_artifact"
  done
fi

PHASE=completed
