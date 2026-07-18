#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SCRIPT_PATH="$ROOT_DIR/scripts/verify-v934-release-gate.sh"
STEP4_RUNNER="$ROOT_DIR/scripts/verify-v934-step4-coverage.sh"
STEP4_TOOL="$ROOT_DIR/scripts/v934/step4/coverage_tool.py"
STEP4_ARTIFACT_TOOL="$ROOT_DIR/scripts/v934/step4/coverage_xml_tool.py"
CONTRACT_FREEZE="$ROOT_DIR/scripts/v934/contract-freeze.json"
ARTIFACT_TOOL="$ROOT_DIR/scripts/v934/step5/release_artifact_tool.py"
PACKAGE_TOOL="$ROOT_DIR/scripts/v934/step5/release_package_tool.py"
POINTER_TOOL="$ROOT_DIR/scripts/v934/step5/pointer_tool.py"
TOOLING_MANIFEST="$ROOT_DIR/scripts/v934/step5/SHA256SUMS"
TARGET_ROOT="$ROOT_DIR/target/v934-release-gate"
RUNS_ROOT="$TARGET_ROOT/runs"

usage() {
  cat <<'EOF'
Usage:
  scripts/verify-v934-release-gate.sh rehearsal [RUN_ID]
  scripts/verify-v934-release-gate.sh authority [RUN_ID]

Both modes execute one fresh Step 4 release-successor replay, package the exact
tested class tree without a test-skip lifecycle, verify the runtime image JAR,
and publish a portable deterministic evidence candidate. Rehearsal may run on
a protected tracked dirty baseline. Authority requires a clean pushed HEAD.
Neither mode publishes the final version-authority pointer.
EOF
}

fail() {
  echo "[v934-release] ERROR: $*" >&2
  exit 1
}

sha256_file() {
  sha256sum "$1" | cut -d' ' -f1
}

optional_regular_file_state() {
  local path="$1"
  [[ ! -L "$path" ]] || fail "optional state file is symlinked: $path"
  if [[ -e "$path" ]]; then
    [[ -f "$path" ]] || fail "optional state path is not a regular file: $path"
    printf 'file:%s\n' "$(sha256_file "$path")"
  else
    printf 'absent\n'
  fi
}

require_real_directory() {
  local path="$1" label="$2"
  [[ -d "$path" && ! -L "$path" ]] || fail "$label is missing, symlinked, or not a directory: $path"
  [[ "$(cd "$path" && pwd -P)" == "$path" ]] || fail "$label is non-canonical: $path"
}

ensure_real_child_directory() {
  local parent="$1" name="$2" path
  path="$parent/$name"
  require_real_directory "$parent" "directory parent"
  [[ ! -L "$path" ]] || fail "directory target is symlinked: $path"
  if [[ ! -e "$path" ]]; then
    mkdir -- "$path"
  fi
  require_real_directory "$path" "directory target"
}

env_value() {
  local file="$1" key="$2"
  awk -v key="$key" '
    index($0, key "=") == 1 {
      if (found) exit 3
      print substr($0, length(key) + 2)
      found = 1
    }
    END { if (!found) exit 2 }
  ' "$file"
}

require_env() {
  local file="$1" key="$2" expected="$3" actual
  actual="$(env_value "$file" "$key")" || fail "missing/duplicate $key in $file"
  [[ "$actual" == "$expected" ]] || \
    fail "$key=$actual, expected=$expected in $file"
}

require_clean_build_environment() {
  local variable_name value
  for variable_name in \
    MAVEN_ARGS MAVEN_BASEDIR MAVEN_CONFIG MAVEN_OPTS MAVEN_SKIP_RC \
    JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS; do
    value="${!variable_name-}"
    [[ -z "$value" ]] || fail "ambient Maven/JVM control is forbidden: $variable_name"
  done
}

run_ambient_build_control_negative_matrix() {
  local row variable_name value rc passed=0
  local -a rows=(
    'MAVEN_ARGS|-Dmaven.ext.class.path=/tmp/v934-forbidden-extension.jar'
    'MAVEN_ARGS|@/tmp/v934-forbidden-maven.args'
    'MAVEN_BASEDIR|/tmp/v934-forbidden-project'
    'MAVEN_CONFIG|--settings /tmp/v934-forbidden-settings.xml'
    'MAVEN_OPTS|-Dmaven.repo.local=/tmp/v934-forbidden-repository'
    'MAVEN_SKIP_RC|1'
    'JAVA_TOOL_OPTIONS|-javaagent:/tmp/v934-forbidden-agent.jar'
    'JDK_JAVA_OPTIONS|@/tmp/v934-forbidden-java.options'
    '_JAVA_OPTIONS|-Duser.home=/tmp/v934-forbidden-home'
  )
  for row in "${rows[@]}"; do
    variable_name="${row%%|*}"
    value="${row#*|}"
    set +e
    (
      export "$variable_name=$value"
      require_clean_build_environment
    ) >/dev/null 2>&1
    rc=$?
    set -e
    [[ "$rc" -ne 0 ]] || fail "ambient build-control negative passed: $variable_name"
    passed=$((passed + 1))
  done
  printf 'cases=%s\nexpected=fail\nstatus=passed\n' "$passed"
}

copy_evidence_tree() {
  local label="$1" source="$2" destination
  destination="$STAGING_ROOT/evidence/$label"
  [[ -d "$source" && ! -L "$source" ]] || fail "evidence tree is missing/unsafe: $source"
  [[ "$(cd "$source" && pwd -P)" == "$source" ]] || fail "evidence tree is non-canonical: $source"
  [[ ! -e "$destination" && ! -L "$destination" ]] || fail "duplicate evidence destination: $destination"
  cp -a -- "$source" "$destination"
  if [[ "$label" == step4 ]]; then
    python3 "$ARTIFACT_TOOL" verify-step4-transport \
      --source "$source" --destination "$destination" \
      > "$RUN_ROOT/step4-transport-safety.json"
    jq -e --slurp '.[0] == .[1]' \
      "$RUN_ROOT/step4-transport-safety.json" \
      "$destination/transport-safety.json" >/dev/null || \
      fail "Step 4 transport safety receipt differs"
  fi
}

copy_tested_class_trees() {
  local destination="$STAGING_ROOT/tested-classes"
  python3 - "$ROOT_DIR" "$CONTRACT_FREEZE" "$destination" <<'PY'
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import sys

root = Path(sys.argv[1])
contract_path = Path(sys.argv[2])
destination = Path(sys.argv[3])
if destination.exists() or destination.is_symlink():
    raise SystemExit("tested class staging destination already exists")
contract = json.loads(contract_path.read_text(encoding="utf-8"))
modules = contract.get("reactor", {}).get("modules")
if not isinstance(modules, list) or len(modules) != 24 or len(set(modules)) != 24:
    raise SystemExit("contract freeze does not contain exact 24 modules")
destination.mkdir(mode=0o755)
rows = []
for module in modules:
    if not isinstance(module, str) or not module or module.startswith("/") or ".." in module.split("/"):
        raise SystemExit("unsafe frozen module")
    source = root / module / "target/classes"
    observed = os.lstat(source)
    if not stat.S_ISDIR(observed.st_mode) or source.is_symlink() or source.resolve() != source:
        raise SystemExit(f"unsafe tested classes root: {module}")
    module_count = 0
    for current, directory_names, file_names in os.walk(source, followlinks=False):
        current_path = Path(current)
        for name in directory_names:
            child = current_path / name
            metadata = os.lstat(child)
            if not stat.S_ISDIR(metadata.st_mode) or child.is_symlink():
                raise SystemExit(f"unsafe tested classes directory: {child}")
        for name in file_names:
            child = current_path / name
            metadata = os.lstat(child)
            if child.is_symlink() or not stat.S_ISREG(metadata.st_mode):
                raise SystemExit(f"unsafe tested classes file: {child}")
            if child.suffix != ".class":
                continue
            relative = child.relative_to(root)
            target = destination / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(child, target, follow_symlinks=False)
            digest = hashlib.sha256(target.read_bytes()).hexdigest()
            rows.append((relative.as_posix(), digest, metadata.st_size, metadata.st_mtime_ns))
            module_count += 1
    if module_count == 0:
        raise SystemExit(f"tested module has no class files: {module}")
rows.sort(key=lambda row: row[0].encode("utf-8"))
payload = json.dumps(rows, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
print(json.dumps({
    "class_files": len(rows),
    "kind": "v934-portable-tested-classes",
    "modules": len(modules),
    "set_sha256": hashlib.sha256(payload).hexdigest(),
    "status": "passed",
}, separators=(",", ":"), sort_keys=True))
PY
}

run_release_sensitive_scan() {
  local root="$1" receipt policy_sha files bytes
  receipt="$root/release/sensitive-scan.env"
  [[ ! -e "$receipt" && ! -L "$receipt" ]] || \
    fail "release sensitive receipt target already exists or is symlinked: $receipt"
  python3 "$ARTIFACT_TOOL" scan-root --root "$root" --env-output "$receipt"
  [[ -f "$receipt" && ! -L "$receipt" ]] || fail "release sensitive receipt is missing/unsafe"
  require_env "$receipt" schema_version 2
  require_env "$receipt" kind v934-release-sensitive-scan
  require_env "$receipt" contract_sha256 "$(sha256_file "$ROOT_DIR/scripts/v934/step5/release-artifact-contract.json")"
  require_env "$receipt" scope payload-text-and-recursive-zip-archives
  require_env "$receipt" pattern_count 8
  require_env "$receipt" text_extension_count 46
  require_env "$receipt" archive_extension_count 2
  require_env "$receipt" status passed
  policy_sha="$(env_value "$receipt" policy_sha256)" || fail "sensitive policy digest is absent/duplicate"
  files="$(env_value "$receipt" files)" || fail "sensitive file count is absent/duplicate"
  bytes="$(env_value "$receipt" bytes)" || fail "sensitive byte count is absent/duplicate"
  [[ "$policy_sha" =~ ^[0-9a-f]{64}$ ]] || fail "sensitive policy digest is invalid"
  [[ "$files" =~ ^[0-9]+$ && "$bytes" =~ ^[0-9]+$ ]] || \
    fail "sensitive scan counts are invalid"
}

durably_seal_run_root() {
  python3 - "$RUN_ROOT" "$RUNS_ROOT" "$TARGET_ROOT" <<'PY'
import os
from pathlib import Path
import stat
import sys

run_root, runs_root, target_root = (Path(value) for value in sys.argv[1:])
for path, label in (
    (run_root, "run root"),
    (runs_root, "runs root"),
    (target_root, "release target root"),
):
    observed = os.lstat(path)
    if not stat.S_ISDIR(observed.st_mode) or path.is_symlink() or path.resolve() != path:
        raise SystemExit(f"unsafe {label}: {path}")
if run_root.parent != runs_root or runs_root.parent != target_root:
    raise SystemExit("release run directory chain differs")

directories = [run_root]
files = []
for current, directory_names, file_names in os.walk(run_root, followlinks=False):
    current_path = Path(current)
    for name in directory_names:
        child = current_path / name
        observed = os.lstat(child)
        if not stat.S_ISDIR(observed.st_mode) or child.is_symlink():
            raise SystemExit(f"unsafe run directory: {child}")
        directories.append(child)
    for name in file_names:
        child = current_path / name
        observed = os.lstat(child)
        if not stat.S_ISREG(observed.st_mode) or child.is_symlink():
            raise SystemExit(f"unsafe run file: {child}")
        files.append(child)

file_flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
directory_flags = file_flags | getattr(os, "O_DIRECTORY", 0)
for path in sorted(files, key=lambda value: os.fsencode(value)):
    descriptor = os.open(path, file_flags)
    try:
        before = os.fstat(descriptor)
        if not stat.S_ISREG(before.st_mode):
            raise SystemExit(f"run file changed type: {path}")
        os.fsync(descriptor)
        after = os.fstat(descriptor)
        current = os.lstat(path)
        identity = lambda row: (
            row.st_dev,
            row.st_ino,
            row.st_size,
            row.st_mtime_ns,
            row.st_ctime_ns,
        )
        if identity(before) != identity(after) or identity(after) != identity(current):
            raise SystemExit(f"run file changed while durably sealed: {path}")
    finally:
        os.close(descriptor)
for path in sorted(
    directories,
    key=lambda value: (-len(value.parts), os.fsencode(value)),
):
    descriptor = os.open(path, directory_flags)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
for path in (runs_root, target_root):
    descriptor = os.open(path, directory_flags)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
print(f"files={len(files)} directories={len(directories)} status=passed")
PY
}

MODE="${1:-}"
case "$MODE" in
  rehearsal|authority) ;;
  -h|--help) usage; exit 0 ;;
  *) usage >&2; exit 2 ;;
esac
[[ "$#" -le 2 ]] || { usage >&2; exit 2; }
RUN_ID="${2:-v934-release-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
[[ "$RUN_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ && "${#RUN_ID}" -le 128 ]] || \
  fail "unsafe run id: $RUN_ID"

for command_name in awk cmp cp cut date docker flock git jq mkdir mvn python3 rg rm rmdir sha256sum; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done

# Git identity is a release authority input, not an ambient caller override.
# Remove the presentation-only pager and reject every other exported Git
# control before the first Git subprocess so repository/config/object/
# transport indirection cannot redirect the authority checks or verifiers.
while IFS= read -r variable_name; do
  [[ "$variable_name" == GIT_* ]] || continue
  if [[ "$variable_name" == GIT_PAGER ]]; then
    unset GIT_PAGER
    continue
  fi
  fail "ambient Git control is forbidden: $variable_name"
done < <(compgen -e)
require_clean_build_environment
for required_file in \
  "$SCRIPT_PATH" "$STEP4_RUNNER" "$STEP4_TOOL" "$STEP4_ARTIFACT_TOOL" \
  "$CONTRACT_FREEZE" "$ARTIFACT_TOOL" "$PACKAGE_TOOL" "$POINTER_TOOL" \
  "$TOOLING_MANIFEST"; do
  [[ -f "$required_file" && ! -L "$required_file" ]] || fail "required file missing/unsafe: $required_file"
done

ensure_real_child_directory "$ROOT_DIR" target
ensure_real_child_directory "$ROOT_DIR/target" v934-release-gate
ensure_real_child_directory "$TARGET_ROOT" runs
# Lock the already validated directory itself.  This avoids creating or
# truncating a lock path and therefore cannot follow a pre-planted symlink.
exec 9<"$TARGET_ROOT"
flock -n 9 || fail "another release gate holds the validated target directory lock"
RUN_ROOT="$RUNS_ROOT/$RUN_ID"
[[ ! -e "$RUN_ROOT" && ! -L "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
mkdir "$RUN_ROOT"
FINAL_AUTHORITY_POINTER="$TARGET_ROOT/final-authority-run.env"
PHASE=preflight
SUCCESS=0

on_exit() {
  local exit_code="$?"
  trap - EXIT
  set +e
  if [[ "$exit_code" -ne 0 || "$SUCCESS" -ne 1 ]]; then
    rm -f -- "$RUN_ROOT/summary.env"
    if [[ ! -e "$RUN_ROOT/failure.env" && ! -L "$RUN_ROOT/failure.env" ]]; then
      printf 'run_id=%s\nmode=%s\nphase=%s\nexit_code=%s\nstatus=failed\n' \
        "$RUN_ID" "$MODE" "$PHASE" "${exit_code:-1}" > "$RUN_ROOT/failure.env"
    fi
    echo "[v934-release] FAILED run=$RUN_ID phase=$PHASE evidence=$RUN_ROOT" >&2
    [[ "$exit_code" -ne 0 ]] || exit_code=1
  else
    echo "[v934-release] PASS mode=$MODE run=$RUN_ID evidence=$RUN_ROOT"
  fi
  exit "$exit_code"
}
trap on_exit EXIT
FINAL_AUTHORITY_POINTER_BEFORE="$(optional_regular_file_state "$FINAL_AUTHORITY_POINTER")"

PHASE=tooling-manifest
(cd "$ROOT_DIR" && sha256sum -c "$TOOLING_MANIFEST")

GIT_HEAD="$(git -C "$ROOT_DIR" rev-parse --verify 'HEAD^{commit}')"
[[ "$GIT_HEAD" =~ ^[0-9a-f]{40}$ ]] || fail "invalid Git HEAD"
if [[ "$MODE" == authority ]]; then
  [[ -z "$(git -C "$ROOT_DIR" status --porcelain --untracked-files=normal)" ]] || \
    fail "authority requires a clean worktree"
  ORIGIN_URL="$(git -C "$ROOT_DIR" remote get-url origin)" || \
    fail "authority cannot resolve origin URL"
  case "$ORIGIN_URL" in
    git@github.com:foggy-projects/foggy-data-mcp-bridge.git|\
    https://github.com/foggy-projects/foggy-data-mcp-bridge|\
    https://github.com/foggy-projects/foggy-data-mcp-bridge.git) ;;
    *) fail "authority origin URL is not the canonical repository" ;;
  esac
  ORIGIN_MAIN="$(git -C "$ROOT_DIR" rev-parse --verify 'origin/main^{commit}')"
  [[ "$GIT_HEAD" == "$ORIGIN_MAIN" ]] || fail "authority requires HEAD == origin/main"
  mapfile -t REMOTE_MAIN_ROWS < <(
    git -C "$ROOT_DIR" ls-remote --exit-code origin refs/heads/main
  )
  [[ "${#REMOTE_MAIN_ROWS[@]}" -eq 1 ]] || \
    fail "authority requires one exact remote main ref"
  [[ "${REMOTE_MAIN_ROWS[0]}" == "$GIT_HEAD"$'\trefs/heads/main' ]] || \
    fail "authority requires HEAD == pushed remote main"
fi

PHASE=source-before
python3 "$STEP4_TOOL" source-hash --repo-root "$ROOT_DIR" \
  --output "$RUN_ROOT/source-before.tsv" > "$RUN_ROOT/source-before.json"
SOURCE_BEFORE="$(jq -er '.sha256' "$RUN_ROOT/source-before.json")"
[[ "$SOURCE_BEFORE" =~ ^[0-9a-f]{64}$ ]] || fail "source-before hash is invalid"

PHASE=runtime-source-before
python3 "$ARTIFACT_TOOL" scan-runtime-source --repo-root "$ROOT_DIR" \
  > "$RUN_ROOT/runtime-source-before.json"
[[ "$(jq -er '.command' "$RUN_ROOT/runtime-source-before.json")" == scan-runtime-source ]] || \
  fail "runtime source command differs"
[[ "$(jq -er '.status' "$RUN_ROOT/runtime-source-before.json")" == passed ]] || \
  fail "runtime source scan did not pass"
[[ "$(jq -er '.git_head' "$RUN_ROOT/runtime-source-before.json")" == "$GIT_HEAD" ]] || \
  fail "runtime source HEAD differs"
[[ "$(jq -er '.module_count' "$RUN_ROOT/runtime-source-before.json")" == 13 ]] || \
  fail "runtime source module closure differs"

PHASE=negative-self-tests
mkdir "$RUN_ROOT/negative"
python3 "$ARTIFACT_TOOL" self-test > "$RUN_ROOT/negative/artifact-self-test.json"
python3 "$PACKAGE_TOOL" negative --repo-root "$ROOT_DIR" \
  --output-dir "$RUN_ROOT/negative/package" > "$RUN_ROOT/negative/package-negative.json"
python3 "$POINTER_TOOL" negative \
  --output-dir "$RUN_ROOT/negative/pointer" > "$RUN_ROOT/negative/pointer-negative.json"
run_ambient_build_control_negative_matrix \
  > "$RUN_ROOT/negative/ambient-build-control.env"
SENSITIVE_NEGATIVE_ROOT="$RUN_ROOT/negative/sensitive-hit-probe"
mkdir "$SENSITIVE_NEGATIVE_ROOT" "$SENSITIVE_NEGATIVE_ROOT/release"
printf 'password=fixture-only-value\n' > "$SENSITIVE_NEGATIVE_ROOT/probe.log"
set +e
(run_release_sensitive_scan "$SENSITIVE_NEGATIVE_ROOT") \
  > "$RUN_ROOT/negative/sensitive-hit.stdout" \
  2> "$RUN_ROOT/negative/sensitive-hit.stderr"
SENSITIVE_NEGATIVE_RC=$?
set -e
rm -f -- "$SENSITIVE_NEGATIVE_ROOT/probe.log" \
  "$SENSITIVE_NEGATIVE_ROOT/release/sensitive-scan.env"
rmdir -- "$SENSITIVE_NEGATIVE_ROOT/release"
rmdir -- "$SENSITIVE_NEGATIVE_ROOT"
[[ "$SENSITIVE_NEGATIVE_RC" -ne 0 ]] || fail "release sensitive hit negative unexpectedly passed"
printf 'expected=fail\nexit_code=%s\nstatus=passed\n' "$SENSITIVE_NEGATIVE_RC" \
  > "$RUN_ROOT/negative/sensitive-hit.env"

PHASE=step4-release-successor
"$STEP4_RUNNER" release "$RUN_ID"
STEP4_ROOT="$ROOT_DIR/target/v934-step4-coverage/runs/$RUN_ID"
STEP4_SUMMARY="$STEP4_ROOT/summary.env"
STEP4_STATUS="$STEP4_ROOT/run-status.env"
for required_file in "$STEP4_SUMMARY" "$STEP4_STATUS" "$STEP4_ROOT/final-manifest.json"; do
  [[ -f "$required_file" && ! -L "$required_file" ]] || fail "Step 4 output missing/unsafe: $required_file"
done
require_env "$STEP4_SUMMARY" run_id "$RUN_ID"
require_env "$STEP4_SUMMARY" mode release
require_env "$STEP4_SUMMARY" threshold_status confirmed
require_env "$STEP4_SUMMARY" source_before_sha256 "$SOURCE_BEFORE"
require_env "$STEP4_SUMMARY" source_after_sha256 "$SOURCE_BEFORE"
require_env "$STEP4_SUMMARY" status release-candidate-ready
require_env "$STEP4_STATUS" run_id "$RUN_ID"
require_env "$STEP4_STATUS" mode release
require_env "$STEP4_STATUS" exit_code 0
require_env "$STEP4_STATUS" status release-passed
python3 "$STEP4_ARTIFACT_TOOL" verify-artifact \
  --repo-root "$ROOT_DIR" --artifact "$STEP4_ROOT/final-manifest.json" \
  > "$RUN_ROOT/step4-final-verify.json"

PHASE=package-tested-tree
PACKAGE_ROOT="$RUN_ROOT/package"
python3 "$PACKAGE_TOOL" package \
  --repo-root "$ROOT_DIR" \
  --run-id "$RUN_ID" \
  --step4-run-root "$STEP4_ROOT" \
  --output-dir "$PACKAGE_ROOT" > "$RUN_ROOT/package-result.json"
python3 "$PACKAGE_TOOL" verify \
  --repo-root "$ROOT_DIR" \
  --manifest "$PACKAGE_ROOT/package-manifest.json" \
  --jar "$PACKAGE_ROOT/app.jar" > "$RUN_ROOT/package-verify.json"

PHASE=source-after
python3 "$STEP4_TOOL" source-hash --repo-root "$ROOT_DIR" \
  --output "$RUN_ROOT/source-after.tsv" > "$RUN_ROOT/source-after.json"
SOURCE_AFTER="$(jq -er '.sha256' "$RUN_ROOT/source-after.json")"
[[ "$SOURCE_AFTER" == "$SOURCE_BEFORE" ]] || fail "tracked source changed during release gate"
cmp -s "$RUN_ROOT/source-before.tsv" "$RUN_ROOT/source-after.tsv" || \
  fail "tracked source inventory changed during release gate"
[[ "$(git -C "$ROOT_DIR" rev-parse --verify 'HEAD^{commit}')" == "$GIT_HEAD" ]] || \
  fail "Git HEAD changed during release gate"
python3 "$ARTIFACT_TOOL" scan-runtime-source --repo-root "$ROOT_DIR" \
  > "$RUN_ROOT/runtime-source-after.json"
cmp -s "$RUN_ROOT/runtime-source-before.json" "$RUN_ROOT/runtime-source-after.json" || \
  fail "exact runtime source closure changed during release gate"

PHASE=portable-staging
STAGING_ROOT="$RUN_ROOT/staging"
mkdir -p "$STAGING_ROOT/evidence"
copy_evidence_tree step4 "$STEP4_ROOT"
copy_evidence_tree unit "$ROOT_DIR/target/v934-step2-unit/runs/$RUN_ID"
copy_evidence_tree integration "$ROOT_DIR/target/v934-step2-integration/runs/$RUN_ID"
copy_evidence_tree step3-required "$ROOT_DIR/target/v934-step3-required-matrix/runs/$RUN_ID"
copy_evidence_tree database "$ROOT_DIR/target/v934-step3-database-matrix/runs/$RUN_ID"
copy_evidence_tree external "$ROOT_DIR/target/v934-step3-external-matrix/runs/$RUN_ID"
copy_evidence_tree addon "$ROOT_DIR/target/v934-step3-preagg-addon/runs/$RUN_ID"
UNIT_FIXTURE_RUN_ID="$(jq -er '.fixture_run_id' "$ROOT_DIR/target/v934-step2-unit/runs/$RUN_ID/mysql57-fixture-manifest.json")"
[[ "$UNIT_FIXTURE_RUN_ID" =~ ^unit-mysql57-[0-9a-f]{16}$ ]] || fail "Unit fixture run id is invalid"
copy_evidence_tree unit-database "$ROOT_DIR/target/v934-step3-database-matrix/runs/$UNIT_FIXTURE_RUN_ID"
cp -a -- "$PACKAGE_ROOT" "$STAGING_ROOT/package"
mkdir "$STAGING_ROOT/release"
cp -- "$RUN_ROOT/source-before.json" "$RUN_ROOT/source-after.json" \
  "$RUN_ROOT/step4-final-verify.json" "$RUN_ROOT/package-result.json" \
  "$RUN_ROOT/package-verify.json" "$STAGING_ROOT/release/"
cp -- "$RUN_ROOT/runtime-source-before.json" \
  "$STAGING_ROOT/release/runtime-source-scan.json"
printf 'schema_version=1\nkind=v934-release-candidate\nrun_id=%s\nmode=%s\ngit_head=%s\nsource_sha256=%s\nstep4_run_id=%s\nstatus=candidate\n' \
  "$RUN_ID" "$MODE" "$GIT_HEAD" "$SOURCE_BEFORE" "$RUN_ID" \
  > "$STAGING_ROOT/release/context.env"

PHASE=portable-tested-classes
copy_tested_class_trees > "$RUN_ROOT/tested-class-staging.json"

PHASE=release-sensitive-scan
run_release_sensitive_scan "$STAGING_ROOT"

PHASE=portable-archive
BUNDLE_ROOT="$RUN_ROOT/bundle"
python3 "$ARTIFACT_TOOL" build \
  --staging-root "$STAGING_ROOT" \
  --output-dir "$BUNDLE_ROOT" \
  --jar-relative-path package/app.jar > "$RUN_ROOT/artifact-build.json"
ARCHIVE="$BUNDLE_ROOT/v934-release-evidence.tar.gz"
ARCHIVE_MANIFEST="$BUNDLE_ROOT/v934-release-evidence.archive.json"
ARCHIVE_DIGEST="$BUNDLE_ROOT/v934-release-evidence.tar.gz.sha256"
python3 "$ARTIFACT_TOOL" verify-archive \
  --archive "$ARCHIVE" --archive-manifest "$ARCHIVE_MANIFEST" \
  > "$RUN_ROOT/artifact-verify.json"
python3 "$ARTIFACT_TOOL" extract-verify \
  --archive "$ARCHIVE" --archive-manifest "$ARCHIVE_MANIFEST" \
  --destination "$RUN_ROOT/download-verify" > "$RUN_ROOT/download-verify.json"
(cd "$BUNDLE_ROOT" && sha256sum -c "${ARCHIVE_DIGEST##*/}") \
  > "$RUN_ROOT/archive-digest-verify.log"

PHASE=summary
ARCHIVE_SHA256="$(sha256_file "$ARCHIVE")"
JAR_SHA256="$(sha256_file "$PACKAGE_ROOT/app.jar")"
printf 'run_id=%s\nmode=%s\ngit_head=%s\nsource_before_sha256=%s\nsource_after_sha256=%s\nstep4_run_id=%s\nlauncher_jar_sha256=%s\npackage_manifest_sha256=%s\nimage_manifest_sha256=%s\narchive_sha256=%s\narchive_manifest_sha256=%s\narchive_digest_sha256=%s\nportable_byte_verify=passed\nportable_semantic_replay=required-downstream\nfinal_authority_pointer_updated=false\nstatus=candidate-passed\n' \
  "$RUN_ID" "$MODE" "$GIT_HEAD" "$SOURCE_BEFORE" "$SOURCE_AFTER" "$RUN_ID" \
  "$JAR_SHA256" "$(sha256_file "$PACKAGE_ROOT/package-manifest.json")" \
  "$(sha256_file "$PACKAGE_ROOT/image-manifest.json")" "$ARCHIVE_SHA256" \
  "$(sha256_file "$ARCHIVE_MANIFEST")" "$(sha256_file "$ARCHIVE_DIGEST")" \
  > "$RUN_ROOT/summary.env"

PHASE=candidate-pointer
[[ "$(optional_regular_file_state "$FINAL_AUTHORITY_POINTER")" == "$FINAL_AUTHORITY_POINTER_BEFORE" ]] || \
  fail "final authority pointer changed before candidate publication"
durably_seal_run_root > "$RUN_ROOT/durable-seal.log"
# The durability log itself is part of the uploaded run, so seal once more
# after writing it and before publishing the global success pointer.
durably_seal_run_root >/dev/null
# Pointer replacement is the final irreversible evidence action.  Ignore
# catchable termination signals across the tiny publication/return window so
# a successfully published candidate can never be relabelled as a failed run.
trap '' INT TERM HUP
if ! python3 "$POINTER_TOOL" publish-candidate \
  --target-root "$TARGET_ROOT" --run-root "$RUN_ROOT" \
  --run-id "$RUN_ID" --git-head "$GIT_HEAD" --mode "$MODE" >/dev/null; then
  # The publisher may be terminated after its atomic commit point but before
  # returning.  Treat an independently recomputed exact pointer as committed;
  # otherwise restore normal signal handling and fail the run.
  if ! python3 "$POINTER_TOOL" verify-candidate \
    --target-root "$TARGET_ROOT" --run-root "$RUN_ROOT" \
    --run-id "$RUN_ID" --git-head "$GIT_HEAD" --mode "$MODE" >/dev/null; then
    trap - INT TERM HUP
    fail "$MODE candidate pointer publication failed"
  fi
fi

PHASE=completed
SUCCESS=1
