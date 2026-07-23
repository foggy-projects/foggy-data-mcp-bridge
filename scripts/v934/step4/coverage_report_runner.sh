#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  coverage_report_runner.sh \
    --run-dir /absolute/repo/target/v934-step4-coverage/runs/<run-id> \
    --session-prefix <run-id> \
    --not-before-ns <positive-integer>

Verify the exact run-owned 23-file JaCoCo exec inventory, stage only that
inventory into the build-only reporter, and publish aggregate exec/XML/HTML
under the same immutable run directory.
EOF
}

fail() {
  echo "[v934-coverage-report] ERROR: $*" >&2
  exit 1
}

require_real_file() {
  local path="$1"
  [[ -f "$path" && ! -L "$path" ]] || fail "missing real regular file: $path"
}

require_nonempty_file() {
  local path="$1"
  require_real_file "$path"
  [[ -s "$path" ]] || fail "empty output file: $path"
}

require_public_receipt_mode() {
  local path="$1"
  local observed_mode
  require_nonempty_file "$path"
  chmod 0644 -- "$path" || fail "cannot set public receipt mode: $path"
  observed_mode="$(stat -c '%a' -- "$path")" ||
    fail "cannot inspect public receipt mode: $path"
  [[ "$observed_mode" == "644" ]] ||
    fail "public receipt mode must be 0644: $path"
}

remove_reporter_output() {
  local path="$1"
  case "$path" in
    "$REPORTER_INPUT"|"$REPORTER_EXEC"|"$REPORTER_SITE") ;;
    *) fail "refusing to remove a non-canonical reporter path: $path" ;;
  esac
  [[ ! -L "$path" ]] || fail "refusing to remove a symlinked reporter path: $path"
  if [[ -e "$path" ]]; then
    rm -rf -- "$path"
  fi
}

cleanup_run_stage() {
  [[ -n "${RUN_REPORT_STAGE:-}" ]] || return 0
  case "$RUN_REPORT_STAGE" in
    "$RUN_DIR"/.report-stage-*) ;;
    *) return 0 ;;
  esac
  if [[ -e "$RUN_REPORT_STAGE" && ! -L "$RUN_REPORT_STAGE" ]]; then
    rm -rf -- "$RUN_REPORT_STAGE"
  fi
}

cleanup_report_replay() {
  [[ -n "${REPORT_REPLAY:-}" ]] || return 0
  case "$REPORT_REPLAY" in
    "$RUN_DIR"/.report-replay-*) ;;
    *) return 0 ;;
  esac
  if [[ -e "$REPORT_REPLAY" && ! -L "$REPORT_REPLAY" ]]; then
    rm -rf -- "$REPORT_REPLAY"
  fi
}

cleanup_effective_pom_probe() {
  local path
  for path in \
    "${REPORT_EFFECTIVE_BEFORE:-}" "${REPORT_EFFECTIVE_AFTER:-}" \
    "${REPORT_EFFECTIVE_RECEIPT_BEFORE:-}" "${REPORT_EFFECTIVE_RECEIPT_AFTER:-}"; do
    [[ -n "$path" ]] || continue
    case "$path" in
      "$RUN_DIR"/.report-effective-*) ;;
      *) continue ;;
    esac
    if [[ -e "$path" || -L "$path" ]]; then
      [[ -f "$path" && ! -L "$path" ]] || fail "unsafe effective-POM probe path: $path"
      rm -f -- "$path"
    fi
  done
}

assert_no_dependency_aggregate_exec() {
  python3 - "$REPO_ROOT" <<'PY'
import json
from pathlib import Path
import stat
import sys

root = Path(sys.argv[1])
freeze = json.loads((root / "scripts/v934/contract-freeze.json").read_text(encoding="utf-8"))
modules = freeze.get("reactor", {}).get("modules")
if not isinstance(modules, list) or len(modules) != 24 or len(set(modules)) != 24:
    raise SystemExit("Step 1 production reactor is not exact 24")
for module in modules:
    candidate = root / module / "target/jacoco-aggregate.exec"
    try:
        candidate_stat = candidate.lstat()
    except FileNotFoundError:
        continue
    if stat.S_ISLNK(candidate_stat.st_mode):
        raise SystemExit(f"dependency aggregate exec is symlinked: {candidate}")
    raise SystemExit(f"dependency aggregate exec could contaminate report-aggregate: {candidate}")
PY
}

run_reporter() {
  (
    cd "$REPO_ROOT"
    command mvn -q \
      -f "$REPO_ROOT/pom.xml" \
      -pl build-support/foggy-coverage-report \
      -am \
      '-P!coverage,!v934-coverage,!release,v934-coverage-report' \
      -Dmaven.test.skip=true \
      -DskipTests=true \
      -DskipUnitTests=true \
      -DskipITs=true \
      verify
  )
}

generate_effective_reporter_pom() {
  local output="$1"
  [[ ! -e "$output" && ! -L "$output" ]] || fail "effective POM probe already exists: $output"
  (
    cd "$REPO_ROOT"
    command mvn -q \
      -f "$REPORTER_DIR/pom.xml" \
      '-P!coverage,!v934-coverage,!release,v934-coverage-report' \
      -Dmaven.test.skip=true \
      -DskipTests=true \
      -DskipUnitTests=true \
      -DskipITs=true \
      org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom \
      "-Doutput=$output"
  )
  require_nonempty_file "$output"
}

RUN_DIR=""
SESSION_PREFIX=""
NOT_BEFORE_NS=""

while (($#)); do
  case "$1" in
    --run-dir)
      (($# >= 2)) || fail "--run-dir requires a value"
      RUN_DIR="$2"
      shift 2
      ;;
    --session-prefix)
      (($# >= 2)) || fail "--session-prefix requires a value"
      SESSION_PREFIX="$2"
      shift 2
      ;;
    --not-before-ns)
      (($# >= 2)) || fail "--not-before-ns requires a value"
      NOT_BEFORE_NS="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "unknown argument: $1"
      ;;
  esac
done

[[ -n "$RUN_DIR" ]] || fail "--run-dir is required"
[[ -n "$SESSION_PREFIX" ]] || fail "--session-prefix is required"
[[ -n "$NOT_BEFORE_NS" ]] || fail "--not-before-ns is required"
[[ "$RUN_DIR" == /* ]] || fail "run directory must be absolute"
[[ "$RUN_DIR" != *$'\n'* && "$RUN_DIR" != *$'\r'* ]] || fail "run directory contains a line break"
[[ "$SESSION_PREFIX" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || fail "unsafe session prefix"
[[ "$NOT_BEFORE_NS" =~ ^[1-9][0-9]*$ ]] || fail "not-before-ns must be a positive integer"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd -P)"
RUNS_ROOT="$REPO_ROOT/target/v934-step4-coverage/runs"
EXPECTED_RUN_DIR="$RUNS_ROOT/$SESSION_PREFIX"
EXEC_ROOT="$RUN_DIR/exec"
EXEC_MANIFEST="$RUN_DIR/exec-manifest.json"
TOOLCHAIN_RECEIPT="$RUN_DIR/toolchain-receipt.json"
RUN_REPORT="$RUN_DIR/report"
AGGREGATE_PROVENANCE="$RUN_REPORT/aggregate-provenance.json"
REPORT_PROVENANCE="$RUN_REPORT/report-provenance.json"
LEDGER="$SCRIPT_DIR/coverage-exec-ledger.tsv"
EXEC_TOOL="$SCRIPT_DIR/coverage_exec_tool.py"
CONTRACT_TOOL="$SCRIPT_DIR/coverage_tool.py"
EFFECTIVE_POM_TOOL="$SCRIPT_DIR/reporter_effective_pom_tool.py"
TOOLCHAIN_RECEIPT_TOOL="$SCRIPT_DIR/toolchain_receipt_tool.py"
REPORTER_DIR="$REPO_ROOT/build-support/foggy-coverage-report"
REPORTER_TARGET="$REPORTER_DIR/target"
REPORTER_INPUT="$REPORTER_TARGET/coverage-input"
REPORTER_EXEC="$REPORTER_TARGET/jacoco-aggregate.exec"
REPORTER_SITE_PARENT="$REPORTER_TARGET/site"
REPORTER_SITE="$REPORTER_TARGET/site/jacoco-aggregate"
REPORT_EFFECTIVE_BEFORE="$RUN_DIR/.report-effective-pom-before.xml"
REPORT_EFFECTIVE_AFTER="$RUN_DIR/.report-effective-pom-after.xml"
REPORT_EFFECTIVE_RECEIPT_BEFORE="$RUN_DIR/.report-effective-receipt-before.json"
REPORT_EFFECTIVE_RECEIPT_AFTER="$RUN_DIR/.report-effective-receipt-after.json"
REPORT_EFFECTIVE_NEGATIVE="$RUN_DIR/negative/effective-reporter-pom.json"

[[ "$RUN_DIR" == "$EXPECTED_RUN_DIR" ]] ||
  fail "run directory must be the canonical session-owned path: $EXPECTED_RUN_DIR"
[[ -d "$RUN_DIR" && ! -L "$RUN_DIR" ]] || fail "run directory must be a real directory: $RUN_DIR"
[[ "$(cd "$RUN_DIR" && pwd -P)" == "$EXPECTED_RUN_DIR" ]] || fail "run directory resolves outside its canonical path"
[[ -d "$EXEC_ROOT" && ! -L "$EXEC_ROOT" ]] || fail "exec directory must be a real directory: $EXEC_ROOT"
[[ "$(cd "$EXEC_ROOT" && pwd -P)" == "$EXPECTED_RUN_DIR/exec" ]] || fail "exec directory resolves outside its canonical path"
require_real_file "$EXEC_TOOL"
require_real_file "$CONTRACT_TOOL"
require_real_file "$EFFECTIVE_POM_TOOL"
require_real_file "$TOOLCHAIN_RECEIPT_TOOL"
require_real_file "$LEDGER"
require_nonempty_file "$TOOLCHAIN_RECEIPT"
[[ -d "$REPORTER_DIR" && ! -L "$REPORTER_DIR" ]] || fail "reporter module must be a real directory"
require_real_file "$REPORTER_DIR/pom.xml"
[[ -d "$RUN_DIR/negative" && ! -L "$RUN_DIR/negative" ]] ||
  fail "run-owned negative evidence directory is missing or unsafe"

JACOCO_PLUGIN_JAR="$HOME/.m2/repository/org/jacoco/jacoco-maven-plugin/0.8.12/jacoco-maven-plugin-0.8.12.jar"
JACOCO_REPORT_JAR="$HOME/.m2/repository/org/jacoco/org.jacoco.report/0.8.12/org.jacoco.report-0.8.12.jar"
JACOCO_CORE_JAR="$HOME/.m2/repository/org/jacoco/org.jacoco.core/0.8.12/org.jacoco.core-0.8.12.jar"
require_nonempty_file "$JACOCO_PLUGIN_JAR"
require_nonempty_file "$JACOCO_REPORT_JAR"
require_nonempty_file "$JACOCO_CORE_JAR"
[[ "$(sha256sum "$JACOCO_PLUGIN_JAR" | cut -d' ' -f1)" == "b305a57535247cff2b7450c4dc1db505c7c246c838cec48c10e52fa71aa423bd" ]] ||
  fail "JaCoCo Maven plugin JAR hash differs"
[[ "$(sha256sum "$JACOCO_REPORT_JAR" | cut -d' ' -f1)" == "f9c79ad66a66a0337c57849ad1287a2ab23b9b232d35314443e5ec49e6e3d20f" ]] ||
  fail "JaCoCo report JAR hash differs"
[[ "$(sha256sum "$JACOCO_CORE_JAR" | cut -d' ' -f1)" == "fca26db37c0c5fbd5dc4985237eb82866df9799d5082af899475a73f91f5b035" ]] ||
  fail "JaCoCo core JAR hash differs"

[[ ! -e "$EXEC_MANIFEST" && ! -L "$EXEC_MANIFEST" ]] || fail "exec manifest already exists: $EXEC_MANIFEST"
[[ ! -e "$RUN_REPORT" && ! -L "$RUN_REPORT" ]] || fail "run report already exists: $RUN_REPORT"

GIT_HEAD="$(git -C "$REPO_ROOT" rev-parse --verify 'HEAD^{commit}')" || fail "HEAD is not a commit"
[[ "$GIT_HEAD" =~ ^[0-9a-f]{40}$ ]] || fail "unexpected committed HEAD identity: $GIT_HEAD"
[[ -z "$(git -C "$REPO_ROOT" status --porcelain=v1 --untracked-files=all)" ]] ||
  fail "formal report aggregation requires an exact clean committed HEAD"

for variable_name in MAVEN_ARGS MAVEN_CONFIG; do
  variable_value="${!variable_name:-}"
  [[ -z "$variable_value" ]] || fail "$variable_name must be empty for a formal coverage run"
done
variable_value="${MAVEN_OPTS:-}"
if [[ "$variable_value" =~ (^|[[:space:],])(-T|--threads)([[:space:]=,0-9C]|$) ]] ||
   [[ "$variable_value" =~ (^|[[:space:],])!?coverage([[:space:],]|$) ]] ||
   [[ "$variable_value" =~ (v934-coverage|jacoco\.|v934\.coverage\.|(^|[[:space:]])-D(argLine|test|it\.test|failIfNoTests|surefire\.|failsafe\.)) ]]; then
  fail "MAVEN_OPTS contains a forbidden profile, selector, coverage, or parallel override"
fi
if [[ -f "$REPO_ROOT/.mvn/maven.config" ]] &&
   grep -Eq '(^|[[:space:],])(-T|--threads)([[:space:]=,0-9C]|$)|(^|[[:space:],])!?coverage([[:space:],]|$)|v934-coverage|jacoco\.|v934\.coverage\.|(^|[[:space:]])-D(argLine|test|it\.test|failIfNoTests|surefire\.|failsafe\.)' \
     "$REPO_ROOT/.mvn/maven.config"; then
  fail ".mvn/maven.config contains a forbidden profile, selector, coverage, or parallel override"
fi

if TOOLCHAIN_REPLAY_PRE_RESULT="$(python3 "$TOOLCHAIN_RECEIPT_TOOL" verify \
  --repo-root "$REPO_ROOT" \
  --run-id "$SESSION_PREFIX" \
  --receipt "$TOOLCHAIN_RECEIPT")"; then
  printf '%s\n' "$TOOLCHAIN_REPLAY_PRE_RESULT"
else
  replay_exit_code=$?
  printf '%s\n' "$TOOLCHAIN_REPLAY_PRE_RESULT" >&2
  exit "$replay_exit_code"
fi

python3 "$EXEC_TOOL" verify \
  --repo-root "$REPO_ROOT" \
  --exec-root "$EXEC_ROOT" \
  --run-id "$SESSION_PREFIX" \
  --session-prefix "$SESSION_PREFIX" \
  --not-before-ns "$NOT_BEFORE_NS" \
  --run-context "$RUN_DIR/run-context.json" \
  --output "$EXEC_MANIFEST"

# Revalidate the reporter POM and all Step 4 parent links after exec provenance
# succeeds and before any reporter-owned staging path is changed.
python3 "$CONTRACT_TOOL" validate-contract --repo-root "$REPO_ROOT"

# Resolve the actual Maven model used by the reporter. The raw POM validator
# alone cannot see inherited/default lifecycle executions or a profile enabled
# outside the repository. A second byte-identical capture after both report
# invocations closes that effective-model gap.
trap cleanup_effective_pom_probe EXIT
generate_effective_reporter_pom "$REPORT_EFFECTIVE_BEFORE"
python3 "$EFFECTIVE_POM_TOOL" \
  --repo-root "$REPO_ROOT" \
  --effective-pom "$REPORT_EFFECTIVE_BEFORE" \
  --output "$REPORT_EFFECTIVE_RECEIPT_BEFORE" \
  --negative-output "$REPORT_EFFECTIVE_NEGATIVE"
require_nonempty_file "$REPORT_EFFECTIVE_RECEIPT_BEFORE"
require_nonempty_file "$REPORT_EFFECTIVE_NEGATIVE"

if [[ -e "$REPORTER_TARGET" ]]; then
  [[ -d "$REPORTER_TARGET" && ! -L "$REPORTER_TARGET" ]] || fail "reporter target must be a real directory"
else
  mkdir -p -- "$REPORTER_TARGET"
fi
[[ "$(cd "$REPORTER_TARGET" && pwd -P)" == "$REPORTER_TARGET" ]] || fail "reporter target resolves outside its canonical path"
if [[ -e "$REPORTER_SITE_PARENT" || -L "$REPORTER_SITE_PARENT" ]]; then
  [[ -d "$REPORTER_SITE_PARENT" && ! -L "$REPORTER_SITE_PARENT" ]] ||
    fail "reporter site parent must be a real directory"
  [[ "$(cd "$REPORTER_SITE_PARENT" && pwd -P)" == "$REPORTER_SITE_PARENT" ]] ||
    fail "reporter site parent resolves outside its canonical path"
fi

remove_reporter_output "$REPORTER_INPUT"
remove_reporter_output "$REPORTER_EXEC"
remove_reporter_output "$REPORTER_SITE"
mkdir -m 0755 -- "$REPORTER_INPUT"

staged_count=0
while IFS=$'\t' read -r exec_file runner lane variant_key expected_session_count expected_session_owners required disposition; do
  if [[ "$exec_file" == "exec_file" ]]; then
    continue
  fi
  [[ "$exec_file" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*\.exec$ && "$exec_file" != */* ]] ||
    fail "unsafe exec filename in frozen ledger: $exec_file"
  require_nonempty_file "$EXEC_ROOT/$exec_file"
  cp -- "$EXEC_ROOT/$exec_file" "$REPORTER_INPUT/$exec_file"
  require_nonempty_file "$REPORTER_INPUT/$exec_file"
  cmp -s -- "$EXEC_ROOT/$exec_file" "$REPORTER_INPUT/$exec_file" ||
    fail "staged exec differs from verified run input: $exec_file"
  ((staged_count += 1))
done < "$LEDGER"
[[ "$staged_count" -eq 23 ]] || fail "expected to stage exactly 23 exec files, staged $staged_count"
[[ "$(find "$REPORTER_INPUT" -mindepth 1 -maxdepth 1 -type f -name '*.exec' -printf '.' | wc -c)" -eq 23 ]] ||
  fail "reporter staging directory does not contain exactly 23 regular exec files"
[[ "$(find "$REPORTER_INPUT" -mindepth 1 -maxdepth 1 -printf '.' | wc -c)" -eq 23 ]] ||
  fail "reporter staging directory contains an unexpected entry"
[[ -z "$(find "$REPORTER_INPUT" -mindepth 1 -maxdepth 1 ! -type f -print -quit)" ]] ||
  fail "reporter staging directory contains a non-file entry"

assert_no_dependency_aggregate_exec
run_reporter

require_nonempty_file "$REPORTER_EXEC"
require_nonempty_file "$REPORTER_SITE/jacoco.xml"
require_nonempty_file "$REPORTER_SITE/index.html"
[[ -z "$(find "$REPORTER_SITE" -type l -print -quit)" ]] || fail "reporter output contains a symlink"

# Rebuild from the same exact staged exec set and require byte-identical binary
# and XML outputs. This binds the report to a fresh trusted JaCoCo invocation
# instead of accepting a stale or pre-seeded reporter target.
REPORT_REPLAY="$RUN_DIR/.report-replay-$$"
[[ ! -e "$REPORT_REPLAY" && ! -L "$REPORT_REPLAY" ]] || fail "report replay path already exists"
trap 'cleanup_report_replay; cleanup_run_stage; cleanup_effective_pom_probe' EXIT
mkdir -m 0755 -- "$REPORT_REPLAY"
cp -- "$REPORTER_EXEC" "$REPORT_REPLAY/jacoco-aggregate.exec"
cp -- "$REPORTER_SITE/jacoco.xml" "$REPORT_REPLAY/jacoco.xml"
remove_reporter_output "$REPORTER_EXEC"
remove_reporter_output "$REPORTER_SITE"
assert_no_dependency_aggregate_exec

generate_effective_reporter_pom "$REPORT_EFFECTIVE_AFTER"
python3 "$EFFECTIVE_POM_TOOL" \
  --repo-root "$REPO_ROOT" \
  --effective-pom "$REPORT_EFFECTIVE_AFTER" \
  --output "$REPORT_EFFECTIVE_RECEIPT_AFTER"
require_nonempty_file "$REPORT_EFFECTIVE_RECEIPT_AFTER"
cmp -s -- "$REPORT_EFFECTIVE_BEFORE" "$REPORT_EFFECTIVE_AFTER" ||
  fail "effective reporter POM changed across deterministic replay"
cmp -s -- "$REPORT_EFFECTIVE_RECEIPT_BEFORE" "$REPORT_EFFECTIVE_RECEIPT_AFTER" ||
  fail "effective reporter POM receipt changed across deterministic replay"
run_reporter
require_nonempty_file "$REPORTER_EXEC"
require_nonempty_file "$REPORTER_SITE/jacoco.xml"
require_nonempty_file "$REPORTER_SITE/index.html"
cmp -s -- "$REPORT_REPLAY/jacoco-aggregate.exec" "$REPORTER_EXEC" ||
  fail "fresh aggregate exec replay is not byte-identical"
cmp -s -- "$REPORT_REPLAY/jacoco.xml" "$REPORTER_SITE/jacoco.xml" ||
  fail "fresh aggregate XML replay is not byte-identical"
for staged_exec in "$REPORTER_INPUT"/*.exec; do
  exec_name="${staged_exec##*/}"
  cmp -s -- "$EXEC_ROOT/$exec_name" "$staged_exec" ||
    fail "reporter input changed during deterministic replay: $exec_name"
done
cleanup_report_replay
REPORT_REPLAY=""
assert_no_dependency_aggregate_exec

if TOOLCHAIN_REPLAY_POST_RESULT="$(python3 "$TOOLCHAIN_RECEIPT_TOOL" verify \
  --repo-root "$REPO_ROOT" \
  --run-id "$SESSION_PREFIX" \
  --receipt "$TOOLCHAIN_RECEIPT")"; then
  printf '%s\n' "$TOOLCHAIN_REPLAY_POST_RESULT"
else
  replay_exit_code=$?
  printf '%s\n' "$TOOLCHAIN_REPLAY_POST_RESULT" >&2
  exit "$replay_exit_code"
fi

RUN_REPORT_STAGE="$RUN_DIR/.report-stage-$$"
[[ ! -e "$RUN_REPORT_STAGE" && ! -L "$RUN_REPORT_STAGE" ]] || fail "temporary run report path already exists"
trap 'cleanup_run_stage; cleanup_effective_pom_probe' EXIT
mkdir -m 0755 -- "$RUN_REPORT_STAGE"
cp -- "$REPORTER_EXEC" "$RUN_REPORT_STAGE/jacoco-aggregate.exec"
cp -R -- "$REPORTER_SITE" "$RUN_REPORT_STAGE/jacoco-aggregate"
cp -- "$REPORT_EFFECTIVE_BEFORE" "$RUN_REPORT_STAGE/effective-reporter-pom.xml"
cp -- "$REPORT_EFFECTIVE_RECEIPT_BEFORE" "$RUN_REPORT_STAGE/effective-reporter-pom-receipt.json"
require_public_receipt_mode "$RUN_REPORT_STAGE/effective-reporter-pom-receipt.json"
python3 - \
  "$RUN_REPORT_STAGE/toolchain-replay-pre.json" \
  "$RUN_REPORT_STAGE/toolchain-replay-post.json" \
  "$TOOLCHAIN_REPLAY_PRE_RESULT" "$TOOLCHAIN_REPLAY_POST_RESULT" \
  "$SESSION_PREFIX" "$TOOLCHAIN_RECEIPT" "$TOOLCHAIN_RECEIPT_TOOL" <<'PY'
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
            raise SystemExit(f"duplicate toolchain result key: {key}")
        result[key] = value
    return result


def regular_bytes(path: Path, label: str) -> bytes:
    path_stat = path.lstat()
    if stat.S_ISLNK(path_stat.st_mode) or not stat.S_ISREG(path_stat.st_mode):
        raise SystemExit(f"{label} is not a real file: {path}")
    data = path.read_bytes()
    if not data:
        raise SystemExit(f"{label} is empty: {path}")
    return data


pre_output = Path(sys.argv[1])
post_output = Path(sys.argv[2])
run_id = sys.argv[5]
receipt_sha = hashlib.sha256(
    regular_bytes(Path(sys.argv[6]), "toolchain receipt")
).hexdigest()
tool_sha = hashlib.sha256(
    regular_bytes(Path(sys.argv[7]), "toolchain receipt tool")
).hexdigest()

for output, stage, raw_result in (
    (pre_output, "reporter-pre", sys.argv[3]),
    (post_output, "reporter-post", sys.argv[4]),
):
    result = json.loads(raw_result, object_pairs_hook=unique)
    expected_result_keys = {
        "command", "run_id", "sha256", "compiler_realm", "jacoco_realm",
        "status",
    }
    if not isinstance(result, dict) or set(result) != expected_result_keys:
        raise SystemExit(f"{stage} toolchain result schema differs")
    if (
        result["command"] != "verify"
        or result["run_id"] != run_id
        or result["sha256"] != receipt_sha
        or re.fullmatch(r"[0-9a-f]{64}", result["sha256"]) is None
        or result["compiler_realm"] != 12
        or type(result["compiler_realm"]) is not int
        or result["jacoco_realm"] != 12
        or type(result["jacoco_realm"]) is not int
        or result["status"] != "passed"
    ):
        raise SystemExit(f"{stage} toolchain result differs from current receipt")
    payload = {
        "schema_version": 1,
        "kind": "v934-step4-toolchain-replay-stage",
        "stage": stage,
        "command": "verify",
        "run_id": run_id,
        "receipt_sha256": receipt_sha,
        "tool_sha256": tool_sha,
        "compiler_realm": 12,
        "jacoco_realm": 12,
        "result": "passed",
    }
    encoded = (json.dumps(payload, indent=2, sort_keys=True) + "\n").encode()
    parent_stat = output.parent.lstat()
    if (
        stat.S_ISLNK(parent_stat.st_mode)
        or not stat.S_ISDIR(parent_stat.st_mode)
        or output.parent.resolve(strict=True) != output.parent
        or output.exists()
        or output.is_symlink()
    ):
        raise SystemExit(f"unsafe replay evidence output: {output}")
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    flags |= getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(output, flags, 0o644)
    try:
        view = memoryview(encoded)
        while view:
            written = os.write(descriptor, view)
            if written <= 0:
                raise SystemExit(f"short write for replay evidence: {output}")
            view = view[written:]
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
PY
require_nonempty_file "$RUN_REPORT_STAGE/jacoco-aggregate.exec"
require_nonempty_file "$RUN_REPORT_STAGE/jacoco-aggregate/jacoco.xml"
require_nonempty_file "$RUN_REPORT_STAGE/jacoco-aggregate/index.html"
require_nonempty_file "$RUN_REPORT_STAGE/effective-reporter-pom.xml"
require_public_receipt_mode "$RUN_REPORT_STAGE/effective-reporter-pom-receipt.json"
require_nonempty_file "$RUN_REPORT_STAGE/toolchain-replay-pre.json"
require_nonempty_file "$RUN_REPORT_STAGE/toolchain-replay-post.json"
[[ -z "$(find "$RUN_REPORT_STAGE" -type l -print -quit)" ]] || fail "run-owned report stage contains a symlink"
python3 - "$RUN_REPORT_STAGE" "$RUN_REPORT" <<'PY'
import ctypes
import os
from pathlib import Path
import stat
import sys

source = Path(sys.argv[1])
destination = Path(sys.argv[2])
if (
    not source.is_absolute()
    or not destination.is_absolute()
    or source.parent != destination.parent
    or not source.is_dir()
    or source.is_symlink()
    or destination.exists()
    or destination.is_symlink()
    or source.parent.is_symlink()
    or source.parent.resolve(strict=True) != source.parent
):
    raise SystemExit("unsafe report directory publication boundary")
for path in sorted(source.rglob("*")):
    path_stat = path.lstat()
    if stat.S_ISLNK(path_stat.st_mode):
        raise SystemExit(f"symlink in report stage: {path}")
    if stat.S_ISREG(path_stat.st_mode):
        descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
for path in sorted(
    [source, *(item for item in source.rglob("*") if item.is_dir())],
    key=lambda item: len(item.parts),
    reverse=True,
):
    descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
libc = ctypes.CDLL(None, use_errno=True)
renameat2 = getattr(libc, "renameat2", None)
if renameat2 is None:
    raise SystemExit("renameat2 no-replace is unavailable")
renameat2.argtypes = (
    ctypes.c_int,
    ctypes.c_char_p,
    ctypes.c_int,
    ctypes.c_char_p,
    ctypes.c_uint,
)
renameat2.restype = ctypes.c_int
if renameat2(-100, os.fsencode(source), -100, os.fsencode(destination), 1) != 0:
    raise SystemExit(
        f"no-clobber report directory publication failed: errno={ctypes.get_errno()}"
    )
parent_fd = os.open(
    destination.parent,
    os.O_RDONLY | getattr(os, "O_DIRECTORY", 0),
)
try:
    os.fsync(parent_fd)
finally:
    os.close(parent_fd)
PY
RUN_REPORT_STAGE=""
cleanup_effective_pom_probe
trap - EXIT

python3 "$EXEC_TOOL" verify-aggregate \
  --repo-root "$REPO_ROOT" \
  --exec-manifest "$EXEC_MANIFEST" \
  --aggregate-exec "$RUN_REPORT/jacoco-aggregate.exec" \
  --output "$AGGREGATE_PROVENANCE"

python3 - \
  "$REPORT_PROVENANCE" "$SESSION_PREFIX" "$GIT_HEAD" "$EXEC_MANIFEST" \
  "$TOOLCHAIN_RECEIPT" \
  "$RUN_REPORT/toolchain-replay-pre.json" \
  "$RUN_REPORT/toolchain-replay-post.json" \
  "$AGGREGATE_PROVENANCE" "$RUN_REPORT/jacoco-aggregate.exec" \
  "$RUN_REPORT/jacoco-aggregate/jacoco.xml" "$RUN_REPORT/jacoco-aggregate/index.html" \
  "$RUN_REPORT/effective-reporter-pom.xml" \
  "$RUN_REPORT/effective-reporter-pom-receipt.json" <<'PY'
import hashlib
import json
import os
from pathlib import Path
import secrets
import stat
import sys

(
    output_text,
    run_id,
    git_head,
    exec_manifest_text,
    toolchain_receipt_text,
    toolchain_replay_pre_text,
    toolchain_replay_post_text,
    aggregate_provenance_text,
    aggregate_exec_text,
    xml_text,
    html_text,
    effective_pom_text,
    effective_receipt_text,
) = sys.argv[1:]
output = Path(output_text)
evidence_cache = {}

def evidence(path_text: str) -> tuple[bytes, dict[str, object]]:
    cached = evidence_cache.get(path_text)
    if cached is not None:
        return cached
    path = Path(path_text)
    before = path.lstat()
    if stat.S_ISLNK(before.st_mode) or not stat.S_ISREG(before.st_mode) or before.st_size <= 0:
        raise SystemExit(f"report provenance input is not a real nonempty file: {path}")
    descriptor = os.open(
        path,
        os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0),
    )
    try:
        opened = os.fstat(descriptor)
        if (before.st_dev, before.st_ino) != (opened.st_dev, opened.st_ino):
            raise SystemExit(f"report provenance input changed while opening: {path}")
        chunks = []
        while True:
            chunk = os.read(descriptor, 1024 * 1024)
            if not chunk:
                break
            chunks.append(chunk)
        after = os.fstat(descriptor)
        if (
            (opened.st_dev, opened.st_ino, opened.st_size, opened.st_mtime_ns)
            != (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns)
        ):
            raise SystemExit(f"report provenance input changed while reading: {path}")
    finally:
        os.close(descriptor)
    data = b"".join(chunks)
    if len(data) != opened.st_size:
        raise SystemExit(f"short read for report provenance input: {path}")
    result = (
        data,
        {"sha256": hashlib.sha256(data).hexdigest(), "size": len(data)},
    )
    evidence_cache[path_text] = result
    return result

exec_manifest_payload = json.loads(evidence(exec_manifest_text)[0])
if (
    exec_manifest_payload.get("run_id") != run_id
    or exec_manifest_payload.get("git_head") != git_head
    or not isinstance(exec_manifest_payload.get("run_context_sha256"), str)
    or not isinstance(exec_manifest_payload.get("source_sha256"), str)
):
    raise SystemExit("exec manifest run/source provenance differs")

def identity(path_text: str) -> dict[str, object]:
    return evidence(path_text)[1]


def public_receipt_identity(path_text: str) -> dict[str, object]:
    path = Path(path_text)
    _, identity_value = evidence(path_text)
    try:
        observed = path.lstat()
    except OSError as exc:
        raise SystemExit(
            f"cannot inspect public receipt mode: {exc.__class__.__name__}"
        ) from exc
    if (
        stat.S_ISLNK(observed.st_mode)
        or not stat.S_ISREG(observed.st_mode)
        or observed.st_size != identity_value["size"]
        or stat.S_IMODE(observed.st_mode) != 0o644
    ):
        raise SystemExit("public receipt must be a nonempty regular 0644 file")
    return {**identity_value, "mode": "0644"}

def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise SystemExit(f"duplicate replay evidence key: {key}")
        result[key] = value
    return result

toolchain_receipt_payload = json.loads(
    evidence(toolchain_receipt_text)[0],
    object_pairs_hook=unique,
)

def replay_identity(path_text: str, expected_stage: str) -> dict[str, object]:
    replay = json.loads(evidence(path_text)[0], object_pairs_hook=unique)
    expected_keys = {
        "schema_version", "kind", "stage", "command", "run_id",
        "receipt_sha256", "tool_sha256", "compiler_realm", "jacoco_realm",
        "result",
    }
    if (
        not isinstance(replay, dict)
        or set(replay) != expected_keys
        or replay["schema_version"] != 1
        or type(replay["schema_version"]) is not int
        or replay["kind"] != "v934-step4-toolchain-replay-stage"
        or replay["stage"] != expected_stage
        or replay["command"] != "verify"
        or replay["run_id"] != run_id
        or replay["receipt_sha256"] != identity(toolchain_receipt_text)["sha256"]
        or replay["tool_sha256"] != toolchain_receipt_payload.get("tool_sha256")
        or replay["compiler_realm"] != 12
        or type(replay["compiler_realm"]) is not int
        or replay["jacoco_realm"] != 12
        or type(replay["jacoco_realm"]) is not int
        or replay["result"] != "passed"
    ):
        raise SystemExit(f"toolchain replay evidence differs: {expected_stage}")
    return identity(path_text)

payload = {
    "schema_version": 1,
    "kind": "v934-step4-deterministic-report-provenance",
    "run_id": run_id,
    "git_head": git_head,
    "run_context_sha256": exec_manifest_payload["run_context_sha256"],
    "source_sha256": exec_manifest_payload["source_sha256"],
    "toolchain_receipt": identity(toolchain_receipt_text),
    "toolchain_replay_pre": replay_identity(
        toolchain_replay_pre_text, "reporter-pre"
    ),
    "toolchain_replay_post": replay_identity(
        toolchain_replay_post_text, "reporter-post"
    ),
    "exec_manifest": identity(exec_manifest_text),
    "aggregate_provenance": identity(aggregate_provenance_text),
    "aggregate_exec": identity(aggregate_exec_text),
    "aggregate_xml": identity(xml_text),
    "aggregate_html_entry": identity(html_text),
    "effective_reporter_pom": identity(effective_pom_text),
    "effective_reporter_pom_receipt": public_receipt_identity(effective_receipt_text),
    "jacoco": {
        "version": "0.8.12",
        "maven_plugin_sha256": "b305a57535247cff2b7450c4dc1db505c7c246c838cec48c10e52fa71aa423bd",
        "report_jar_sha256": "f9c79ad66a66a0337c57849ad1287a2ab23b9b232d35314443e5ec49e6e3d20f",
        "core_jar_sha256": "fca26db37c0c5fbd5dc4985237eb82866df9799d5082af899475a73f91f5b035",
    },
    "deterministic_replay_count": 2,
    "status": "verified",
}
if (
    not isinstance(exec_manifest_payload.get("toolchain_receipt_sha256"), str)
    or payload["toolchain_receipt"]["sha256"]
    != exec_manifest_payload["toolchain_receipt_sha256"]
):
    raise SystemExit("toolchain receipt differs from the exec manifest binding")
effective_receipt = json.loads(evidence(effective_receipt_text)[0])
if (
    set(effective_receipt)
    != {
        "schema_version", "kind", "validator_sha256", "raw_effective_pom_sha256",
        "raw_effective_pom_size", "normalized_effective_pom_sha256",
        "active_project_profiles", "build_plugins", "status",
    }
    or effective_receipt["schema_version"] != 1
    or effective_receipt["kind"] != "v934-step4-effective-reporter-pom-receipt"
    or effective_receipt["status"] != "verified"
    or effective_receipt["raw_effective_pom_sha256"]
    != payload["effective_reporter_pom"]["sha256"]
    or effective_receipt["raw_effective_pom_size"]
    != payload["effective_reporter_pom"]["size"]
):
    raise SystemExit("effective reporter POM receipt differs from the staged effective POM")
payload["normalized_effective_pom_sha256"] = effective_receipt[
    "normalized_effective_pom_sha256"
]
encoded = (json.dumps(payload, indent=2, sort_keys=True) + "\n").encode("utf-8")
parent = output.parent
parent_before = parent.lstat()
if (
    stat.S_ISLNK(parent_before.st_mode)
    or not stat.S_ISDIR(parent_before.st_mode)
    or parent.resolve(strict=True) != parent
):
    raise SystemExit("report provenance parent is unsafe")
directory_fd = os.open(
    parent,
    os.O_RDONLY
    | getattr(os, "O_DIRECTORY", 0)
    | getattr(os, "O_CLOEXEC", 0)
    | getattr(os, "O_NOFOLLOW", 0),
)
bound_parent = os.fstat(directory_fd)
if (parent_before.st_dev, parent_before.st_ino) != (
    bound_parent.st_dev,
    bound_parent.st_ino,
):
    os.close(directory_fd)
    raise SystemExit("report provenance parent changed while opening")
try:
    os.stat(output.name, dir_fd=directory_fd, follow_symlinks=False)
except FileNotFoundError:
    pass
else:
    os.close(directory_fd)
    raise SystemExit("refusing to overwrite report provenance")
temporary_name = f".{output.name}.{os.getpid()}.{secrets.token_hex(12)}.tmp"
published = False
published_identity = None
descriptor = -1
try:
    descriptor = os.open(
        temporary_name,
        os.O_WRONLY
        | os.O_CREAT
        | os.O_EXCL
        | getattr(os, "O_CLOEXEC", 0)
        | getattr(os, "O_NOFOLLOW", 0),
        0o644,
        dir_fd=directory_fd,
    )
    view = memoryview(encoded)
    while view:
        written = os.write(descriptor, view)
        if written <= 0:
            raise RuntimeError("short write while staging report provenance")
        view = view[written:]
    os.fsync(descriptor)
    staged = os.fstat(descriptor)
    published_identity = (staged.st_dev, staged.st_ino)
    os.close(descriptor)
    descriptor = -1
    os.link(
        temporary_name,
        output.name,
        src_dir_fd=directory_fd,
        dst_dir_fd=directory_fd,
        follow_symlinks=False,
    )
    published = True
    current = os.stat(output.name, dir_fd=directory_fd, follow_symlinks=False)
    if (
        not stat.S_ISREG(current.st_mode)
        or (current.st_dev, current.st_ino) != published_identity
    ):
        raise RuntimeError("published report provenance identity differs")
    os.fsync(directory_fd)
    os.unlink(temporary_name, dir_fd=directory_fd)
    os.fsync(directory_fd)
except BaseException:
    if descriptor >= 0:
        os.close(descriptor)
        descriptor = -1
    try:
        os.unlink(temporary_name, dir_fd=directory_fd)
    except FileNotFoundError:
        pass
    if published and published_identity is not None:
        try:
            current = os.stat(output.name, dir_fd=directory_fd, follow_symlinks=False)
            if (current.st_dev, current.st_ino) == published_identity:
                os.unlink(output.name, dir_fd=directory_fd)
        except FileNotFoundError:
            pass
    raise
finally:
    if descriptor >= 0:
        os.close(descriptor)
    os.close(directory_fd)
PY
require_nonempty_file "$REPORT_PROVENANCE"

[[ "$(git -C "$REPO_ROOT" rev-parse --verify 'HEAD^{commit}')" == "$GIT_HEAD" ]] ||
  fail "HEAD changed during report aggregation"
[[ -z "$(git -C "$REPO_ROOT" status --porcelain=v1 --untracked-files=all)" ]] ||
  fail "worktree changed during report aggregation"

echo "[v934-coverage-report] PASS commit=$GIT_HEAD exec=23 report=$RUN_REPORT"
