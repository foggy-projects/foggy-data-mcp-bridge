#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$ROOT_DIR/target/v934-step2-unit/runs/$RUN_ID"
RUNNER_NAME="surefire"
STEP4_RUN_ROOT="$ROOT_DIR/target/v934-step4-coverage/runs/$RUN_ID"
SUCCESSOR_DIR="$STEP4_RUN_ROOT/step2-report-view"
STEP1_TOOL="$ROOT_DIR/scripts/v934/inventory_tool.py"
REPORT_TOOL="$ROOT_DIR/scripts/v934/step2_report_tool.py"
AUTHORITY_LIB="$ROOT_DIR/scripts/v934/authority_runner_lib.sh"
STEP4_AUTHORITY_LIB="$ROOT_DIR/scripts/v934/step4/authority_parent_lib.sh"
STEP1_FREEZE="$ROOT_DIR/scripts/v934/contract-freeze.json"
COVERAGE_LIB="$ROOT_DIR/scripts/v934/step4/coverage_runner_lib.sh"
RUN_LOG_LIB="$ROOT_DIR/scripts/v934/step4/run_log_lifecycle_lib.sh"
STEP4_TOOL="$ROOT_DIR/scripts/v934/step4/coverage_tool.py"
REPORT_VIEW_TOOL="$ROOT_DIR/scripts/v934/step4/step2_report_view_tool.py"
UNIT_FIXTURE_TOOL="$ROOT_DIR/scripts/v934/step4/unit_mysql_fixture_tool.py"
DATABASE_PROVISIONER="$ROOT_DIR/scripts/v934/step3/provision-database-cell.sh"
OUTER_MARKER="$RUN_ROOT/run-context.json"

fail() {
  echo "[v934-unit] ERROR: $*" >&2
  exit 1
}

unit_source_hash() {
  local payload
  if ! payload="$(python3 "$STEP4_TOOL" source-hash --repo-root "$ROOT_DIR")"; then
    printf '%s\n' "$payload" >&2
    return 1
  fi
  printf '%s\n' "$payload" | python3 -c '
import json
import re
import sys

value = json.load(sys.stdin)
digest = value.get("sha256")
if value.get("status") != "passed" or not isinstance(digest, str) or re.fullmatch(r"[0-9a-f]{64}", digest) is None:
    raise SystemExit("Step 4 source-hash receipt is not passed/typed")
print(digest)
'
}

[[ "$#" -le 1 ]] || fail "usage: scripts/verify-v934-unit.sh [RUN_ID]"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
for command_name in bash docker env flock git mvn python3 sha256sum ss tee; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done
for required_file in \
  "$STEP1_TOOL" "$STEP1_FREEZE" "$REPORT_TOOL" "$AUTHORITY_LIB" \
  "$STEP4_AUTHORITY_LIB" "$COVERAGE_LIB" "$RUN_LOG_LIB" "$STEP4_TOOL" "$REPORT_VIEW_TOOL"; do
  [[ -f "$required_file" ]] || fail "required file missing: $required_file"
done
for required_file in "$UNIT_FIXTURE_TOOL" "$DATABASE_PROVISIONER"; do
  [[ -f "$required_file" && ! -L "$required_file" ]] || \
    fail "required Unit MySQL fixture file missing or symlinked: $required_file"
done
for variable_name in MAVEN_ARGS MAVEN_CONFIG MAVEN_OPTS; do
  variable_value="${!variable_name:-}"
  if [[ "$variable_value" == *"@"* || "$variable_value" =~ -[Xx][Xx]:[Vv][Mm][Oo][Pp][Tt][Ii][Oo][Nn][Ss][Ff][Ii][Ll][Ee] || "$variable_value" =~ -([Jj][Aa][Vv][Aa][Aa][Gg][Ee][Nn][Tt]|[Aa][Gg][Ee][Nn][Tt][Ll][Ii][Bb]|[Aa][Gg][Ee][Nn][Tt][Pp][Aa][Tt][Hh]): ]]; then
    fail "$variable_name contains forbidden option indirection"
  fi
  if [[ "$variable_value" =~ (skipTests|skipITs|skipUnitTests|multi-db|model-lifecycle|query-cache-real-query|failIfNo[A-Za-z0-9._-]*|[Ss][Pp][Rr][Ii][Nn][Gg][._-]|[Vv]934[._-][Uu][Nn][Ii][Tt][._-][Mm][Yy][Ss][Qq][Ll]57) ]]; then
    fail "$variable_name contains a forbidden lane override"
  fi
done
for variable_name in V934_UNIT_MYSQL57_URL V934_UNIT_MYSQL57_USERNAME V934_UNIT_MYSQL57_PASSWORD; do
  [[ -z "${!variable_name+x}" ]] || fail "ambient Unit fixture environment is forbidden: $variable_name"
done
while IFS='=' read -r environment_key _; do
  normalized_environment_key="${environment_key^^}"
  [[ "$normalized_environment_key" != SPRING_* && "$normalized_environment_key" != SPRING.* && "$normalized_environment_key" != SPRING-* ]] || \
    fail "ambient Spring environment is forbidden: $environment_key"
  normalized_fixture_key="${normalized_environment_key//./_}"
  normalized_fixture_key="${normalized_fixture_key//-/_}"
  [[ "$normalized_fixture_key" != V934_UNIT_MYSQL57_* ]] || \
    fail "ambient Unit fixture environment is forbidden: $environment_key"
done < <(env)
for variable_name in JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS; do
  [[ "${!variable_name:-}" != *"@"* && ! "${!variable_name:-}" =~ -[Xx][Xx]:[Vv][Mm][Oo][Pp][Tt][Ii][Oo][Nn][Ss][Ff][Ii][Ll][Ee] && ! "${!variable_name:-}" =~ -([Jj][Aa][Vv][Aa][Aa][Gg][Ee][Nn][Tt]|[Aa][Gg][Ee][Nn][Tt][Ll][Ii][Bb]|[Aa][Gg][Ee][Nn][Tt][Pp][Aa][Tt][Hh]): ]] || \
    fail "$variable_name contains forbidden option indirection"
  [[ ! "${!variable_name:-}" =~ ([Ss][Pp][Rr][Ii][Nn][Gg][._-]|[Vv]934[._-][Uu][Nn][Ii][Tt][._-][Mm][Yy][Ss][Qq][Ll]57) ]] || \
    fail "$variable_name contains a datasource/config override"
done
for config_file in "$ROOT_DIR/.mvn/maven.config" "$ROOT_DIR/.mvn/jvm.config"; do
  if [[ -e "$config_file" || -L "$config_file" ]]; then
    [[ -f "$config_file" && ! -L "$config_file" ]] || fail "Maven config is not a real file: $config_file"
    if grep -Eq '(@|-[Xx][Xx]:[Vv][Mm][Oo][Pp][Tt][Ii][Oo][Nn][Ss][Ff][Ii][Ll][Ee]|-([Jj][Aa][Vv][Aa][Aa][Gg][Ee][Nn][Tt]|[Aa][Gg][Ee][Nn][Tt][Ll][Ii][Bb]|[Aa][Gg][Ee][Nn][Tt][Pp][Aa][Tt][Hh]):|skipTests|skipITs|skipUnitTests|multi-db|model-lifecycle|query-cache-real-query|failIfNo[A-Za-z0-9._-]*|[Ss][Pp][Rr][Ii][Nn][Gg][._-]|[Vv]934[._-][Uu][Nn][Ii][Tt][._-][Mm][Yy][Ss][Qq][Ll]57)' "$config_file"; then
      fail "Maven config contains a forbidden lane or Spring override: $config_file"
    fi
  fi
done

if ! python3 - "$ROOT_DIR" <<'PY'
import subprocess
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
expected = {
    "foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/tools/JavaComposeScriptToolErrorSnapshotTest.java": "java_compose_script_tool_error_snapshot_parity.json",
    "foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/tools/JavaDomainQuestionNeutralRunnerSnapshotTest.java": "java_domain_question_neutral_runner_parity.json",
    "foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/engine/compose/compilation/JavaComposeSnapshotTest.java": "java_compose_snapshot_parity.json",
    "foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/engine/compose/runtime/JavaComposeScriptSnapshotTest.java": "java_compose_script_snapshot_parity.json",
    "foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/engine/compose/security/JavaGovernanceSnapshotTest.java": "java_governance_snapshot_parity.json",
    "foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/engine/pivot/JavaPivotDomainSnapshotTest.java": "java_pivot_domain_snapshot_parity.json",
    "foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/engine/pivot/JavaPivotOutputSnapshotTest.java": "java_pivot_output_snapshot_parity.json",
    "foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/parity/FormulaParitySnapshotTest.java": "_parity_snapshot.json",
    "foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/parity/JavaQueryModelAggregateJoinSnapshotTest.java": "_querymodel_aggregate_join_snapshot.json",
    "foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/parity/JavaSemanticScaleSnapshotTest.java": "java_semantic_scale_snapshot_parity.json",
    "foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/parity/StableRelationOuterAggregateSnapshotTest.java": "_stable_relation_outer_aggregate_snapshot.json",
    "foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/parity/StableRelationOuterWindowSnapshotTest.java": "_stable_relation_outer_window_snapshot.json",
    "foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/parity/StableRelationSnapshotTest.java": "_stable_relation_schema_snapshot.json",
    "foggy-dataset-model-engine/src/test/java/com/foggyframework/dataset/model/parity/TimeWindowParitySnapshotTest.java": "_time_window_parity_snapshot.json",
}
actual = set()
for test_root in (
    root / "foggy-dataset-model-engine/src/test/java",
    root / "foggy-dataset-mcp/src/test/java",
):
    for path in test_root.rglob("*SnapshotTest.java"):
        if path.is_file() and '"target", "parity"' in path.read_text(encoding="utf-8"):
            actual.add(path.relative_to(root).as_posix())
if actual != set(expected):
    print(f"missing snapshot producers: {sorted(set(expected) - actual)}", file=sys.stderr)
    print(f"unexpected snapshot producers: {sorted(actual - set(expected))}", file=sys.stderr)
    raise SystemExit(1)
tracked = set(subprocess.check_output(
    ["git", "-C", str(root), "ls-files", "--", *sorted(expected)], text=True
).splitlines())
if tracked != set(expected):
    print(f"untracked snapshot producers: {sorted(set(expected) - tracked)}", file=sys.stderr)
    raise SystemExit(1)
for relative_path, artifact_name in expected.items():
    path = root / relative_path
    if path.is_symlink() or not path.is_file():
        print(f"snapshot producer is missing or symlinked: {relative_path}", file=sys.stderr)
        raise SystemExit(1)
    source = path.read_text(encoding="utf-8")
    if artifact_name not in source:
        print(f"snapshot artifact mapping drifted: {relative_path} -> {artifact_name}", file=sys.stderr)
        raise SystemExit(1)
    for forbidden in ("foggy-data-mcp-bridge-python", "pythonFixturePath"):
        if forbidden in source:
            print(f"snapshot producer contains forbidden sibling reference: {relative_path}", file=sys.stderr)
            raise SystemExit(1)
print("[v934-unit] snapshot artifact isolation PASS producers=14 external=0")
PY
then
  fail "snapshot artifact isolation preflight failed"
fi

# shellcheck source=scripts/v934/authority_runner_lib.sh
source "$AUTHORITY_LIB"
# shellcheck source=scripts/v934/step4/authority_parent_lib.sh
source "$STEP4_AUTHORITY_LIB"
# shellcheck source=scripts/v934/step4/coverage_runner_lib.sh
source "$COVERAGE_LIB"
# shellcheck source=scripts/v934/step4/run_log_lifecycle_lib.sh
source "$RUN_LOG_LIB"
CURRENT_STEP4_SOURCE=""
if [[ "${V934_AUTHORITY_LOCK_MODE:-standalone}" == inherited ]]; then
  CURRENT_STEP4_SOURCE="$(unit_source_hash)" || fail "Step 4 source hash rejected the current repository state"
  v934_step4_validate_inherited_authority \
    "$ROOT_DIR" "v934-unit" "$CURRENT_STEP4_SOURCE" || exit 1
elif [[ "${V934_AUTHORITY_LOCK_MODE:-standalone}" == standalone ]]; then
  v934_acquire_authority_lock "$ROOT_DIR" "v934-unit" || exit 1
else
  fail "unsupported authority mode: ${V934_AUTHORITY_LOCK_MODE:-}"
fi

[[ ! -e "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
GIT_HEAD="$(git -C "$ROOT_DIR" rev-parse HEAD)"
mkdir -p "$RUN_ROOT"
printf 'preserve-root-authority-evidence\n' > "$RUN_ROOT/cleanup.sentinel"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
PHASE="bootstrap"
SOURCE_BEFORE=""
SOURCE_AFTER=""
OUTER_MARKER_SHA256=""
SUCCESSOR_MANIFEST_SHA256=""
FINAL_REPORT_MANIFEST_SHA256=""
FIXTURE_RUN_ID=""
LIFECYCLE_STARTED=0

v934_unit_exit_trap() {
  local exit_code="${1:-1}" cleanup_code=0
  [[ "$exit_code" =~ ^[0-9]+$ ]] || exit_code=1
  trap '' INT TERM HUP
  trap - EXIT
  set +e
  if [[ "$LIFECYCLE_STARTED" -eq 1 ]]; then
    python3 "$UNIT_FIXTURE_TOOL" cleanup-lifecycle --repo-root "$ROOT_DIR" --run-id "$RUN_ID"
    cleanup_code=$?
    if [[ "$cleanup_code" -ne 0 ]]; then
      PHASE="unit-mysql57-lifecycle-fallback-cleanup-failed"
      [[ "$exit_code" -ne 0 ]] || exit_code=1
    fi
  fi
  if [[ -n "$FIXTURE_RUN_ID" ]]; then
    python3 "$UNIT_FIXTURE_TOOL" cleanup --repo-root "$ROOT_DIR" --run-id "$RUN_ID"
    cleanup_code=$?
    if [[ "$cleanup_code" -ne 0 ]]; then
      PHASE="unit-mysql57-fallback-cleanup-failed"
      [[ "$exit_code" -ne 0 ]] || exit_code=1
    fi
  fi
  v934_run_log_exit_trap "$exit_code" v934_record_run_status
}

v934_install_run_status_traps
trap 'v934_unit_exit_trap "$?"' EXIT
v934_run_log_open "$RUN_ROOT" v934-unit || fail "cannot open the owned run logger"

PHASE="source-baseline"
if [[ -n "$CURRENT_STEP4_SOURCE" ]]; then
  SOURCE_BEFORE="$CURRENT_STEP4_SOURCE"
else
  SOURCE_BEFORE="$(unit_source_hash)" || fail "Step 4 source hash rejected the current repository state"
fi
[[ "$SOURCE_BEFORE" =~ ^[0-9a-f]{64}$ ]] || fail "Step 4 source seal is invalid"
if [[ -e "$STEP4_RUN_ROOT" ]]; then
  [[ -d "$STEP4_RUN_ROOT" && ! -L "$STEP4_RUN_ROOT" ]] || \
    fail "Step 4 run root is not a real directory: $STEP4_RUN_ROOT"
else
  mkdir -p "$STEP4_RUN_ROOT"
fi
mapfile -t REACTOR_MODULES < <(python3 - "$STEP1_TOOL" "$ROOT_DIR" "$STEP1_FREEZE" <<'PY'
import json
import runpy
import sys
from pathlib import Path

namespace = runpy.run_path(sys.argv[1])
active = namespace["active_reactor_modules"](Path(sys.argv[2]))
freeze = json.loads(Path(sys.argv[3]).read_text(encoding="utf-8"))
reactor = freeze.get("reactor", {})
production = reactor.get("modules", [])
reporter = "build-support/foggy-coverage-report"
if (
    reactor.get("module_count") != 24
    or len(production) != 24
    or len(set(production)) != 24
    or reporter in production
):
    raise SystemExit("Step 1 frozen production reactor is not an exact 24-module set")
expected = sorted([*production, reporter])
if active != expected:
    missing = sorted(set(expected) - set(active))
    unexpected = sorted(set(active) - set(expected))
    raise SystemExit(
        f"active reactor differs from frozen24+reporter: missing={missing} unexpected={unexpected}"
    )
print("\n".join(sorted(production)))
PY
)
[[ "${#REACTOR_MODULES[@]}" -eq 24 ]] || \
  fail "active reactor must equal the frozen 24 production modules plus the coverage reporter"
REACTOR_MODULE_CSV="$(IFS=,; printf '%s' "${REACTOR_MODULES[*]}")"
FIXTURE_RUN_ID="$(python3 "$UNIT_FIXTURE_TOOL" derive --run-id "$RUN_ID" | \
  python3 -c 'import json,sys; print(json.load(sys.stdin)["fixture_run_id"])')"
[[ "$FIXTURE_RUN_ID" =~ ^unit-mysql57-[0-9a-f]{16}$ ]] || \
  fail "derived Unit MySQL fixture run id differs"
FIXTURE_RUN_ROOT="$ROOT_DIR/target/v934-step3-database-matrix/runs/$FIXTURE_RUN_ID"
FIXTURE_CELL_ROOT="$FIXTURE_RUN_ROOT/cells/mysql57"
UNIT_FIXTURE_MANIFEST="$RUN_ROOT/mysql57-fixture-manifest.json"
UNIT_FIXTURE_NEGATIVE="$RUN_ROOT/mysql57-fixture-negative.json"
UNIT_FIXTURE_LIFECYCLE_NEGATIVE="$RUN_ROOT/mysql57-fixture-lifecycle-negative.json"
[[ ! -e "$FIXTURE_RUN_ROOT" && ! -L "$FIXTURE_RUN_ROOT" ]] || \
  fail "Unit MySQL fixture run root already exists: $FIXTURE_RUN_ROOT"
mkdir -p "$FIXTURE_RUN_ROOT/cells"
[[ -d "$FIXTURE_RUN_ROOT/cells" && ! -L "$FIXTURE_RUN_ROOT/cells" ]] || \
  fail "Unit MySQL fixture cell parent is unsafe"
PHASE="test-bytecode-cleanup"
for module in "${REACTOR_MODULES[@]}"; do
  [[ -d "$ROOT_DIR/$module" ]] || fail "reactor module is missing: $module"
  rm -rf \
    "$ROOT_DIR/$module/target/surefire-reports" \
    "$ROOT_DIR/$module/target/test-classes" \
    "$ROOT_DIR/$module/target/generated-test-sources" \
    "$ROOT_DIR/$module/target/maven-status/maven-compiler-plugin/testCompile"
done
[[ -f "$RUN_ROOT/cleanup.sentinel" ]] || fail "test-bytecode cleanup removed root authority evidence"

echo "[v934-unit] bootstrap clean reactor test bytecode"
PHASE="test-compile"
(cd "$ROOT_DIR" && mvn -q \
  -P\!multi-db,\!model-lifecycle,\!query-cache-real-query \
  -pl "$REACTOR_MODULE_CSV" -am \
  -DskipUnitTests=true \
  -DskipITs=true \
  test-compile)
PHASE="successor-validate"
if [[ -e "$SUCCESSOR_DIR" || -L "$SUCCESSOR_DIR" ]]; then
  python3 "$REPORT_VIEW_TOOL" validate --repo-root "$ROOT_DIR" --run-id "$RUN_ID"
else
  python3 "$REPORT_VIEW_TOOL" generate --repo-root "$ROOT_DIR" --run-id "$RUN_ID"
fi
python3 - "$SUCCESSOR_DIR/contract-freeze.json" <<'PY'
import json
import sys

freeze = json.load(open(sys.argv[1], encoding="utf-8"))
if freeze.get("status") != "confirmed" or freeze.get("decision") != "passed":
    raise SystemExit("Step 2 successor is not confirmed/passed")
PY

PHASE="outer-marker"
mapfile -t OUTER_HASHES < <(python3 - \
  "$OUTER_MARKER" "$RUN_ID" "$RUNNER_NAME" "$GIT_HEAD" \
  "$SOURCE_BEFORE" "$STARTED_AT" "$SUCCESSOR_DIR" <<'PY'
import hashlib
import json
import os
from pathlib import Path
import sys

output = Path(sys.argv[1])
successor_dir = Path(sys.argv[7])

def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

successor = {
    "required_execution_sha256": sha256_file(successor_dir / "step2-required-execution.tsv"),
    "structural_report_inventory_sha256": sha256_file(successor_dir / "structural-report-inventory.tsv"),
    "discovery_inventory_sha256": sha256_file(successor_dir / "discovery-inventory.tsv"),
    "runner_contract_sha256": sha256_file(successor_dir / "runner-contract.json"),
    "contract_freeze_sha256": sha256_file(successor_dir / "contract-freeze.json"),
    "hash_manifest_sha256": sha256_file(successor_dir / "SHA256SUMS"),
}
payload = {
    "schema_version": 1,
    "kind": "v934-step2-outer-run",
    "run_id": sys.argv[2],
    "runner": sys.argv[3],
    "git_head": sys.argv[4],
    "source_before_sha256": sys.argv[5],
    "started_at": sys.argv[6],
    "status": "started",
    "successor": successor,
}
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
os.replace(temporary, output)
print(sha256_file(output))
print(successor["hash_manifest_sha256"])
PY
)
[[ "${#OUTER_HASHES[@]}" -eq 2 ]] || fail "cannot create immutable outer run marker"
OUTER_MARKER_SHA256="${OUTER_HASHES[0]}"
SUCCESSOR_MANIFEST_SHA256="${OUTER_HASHES[1]}"

for module in "${REACTOR_MODULES[@]}"; do
  rm -rf "$ROOT_DIR/$module/target/surefire-reports"
done

MARKER="$RUN_ROOT/run-marker.json"
python3 - "$MARKER" "$RUN_ID" "$RUNNER_NAME" "unit" "$OUTER_MARKER_SHA256" <<'PY'
import datetime as dt
import json
import os
from pathlib import Path
import sys

output = Path(sys.argv[1])
payload = {
    "schema_version": 1,
    "kind": "v934-step2-variant-run",
    "run_id": sys.argv[2],
    "runner": sys.argv[3],
    "variant_key": sys.argv[4],
    "outer_marker_sha256": sys.argv[5],
    "started_at": dt.datetime.now(dt.timezone.utc).isoformat(),
    "status": "started",
}
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
os.replace(temporary, output)
PY

echo "[v934-unit] running frozen production-reactor Surefire unit authority"
PHASE="unit-mysql57-lifecycle-negative"
LIFECYCLE_STARTED=1
python3 "$UNIT_FIXTURE_TOOL" lifecycle-negative \
  --repo-root "$ROOT_DIR" \
  --run-id "$RUN_ID" \
  --output "$UNIT_FIXTURE_LIFECYCLE_NEGATIVE"
python3 "$UNIT_FIXTURE_TOOL" verify-lifecycle-negative \
  --path "$UNIT_FIXTURE_LIFECYCLE_NEGATIVE"
PHASE="unit-mysql57-provision"
v934_coverage_configure ut unit
"$DATABASE_PROVISIONER" run mysql57 "$FIXTURE_RUN_ID" "$FIXTURE_CELL_ROOT" -- \
  python3 "$UNIT_FIXTURE_TOOL" callback \
    --repo-root "$ROOT_DIR" \
    --run-id "$RUN_ID" \
    --fixture-run-id "$FIXTURE_RUN_ID" \
    --reactor-modules "$REACTOR_MODULE_CSV" \
    --coverage-exec "${V934_COVERAGE_EXEC_FILE:-disabled}" \
    --session-id "${V934_COVERAGE_SESSION_ID_BASE:-disabled}"
PHASE="unit-mysql57-evidence"
python3 "$UNIT_FIXTURE_TOOL" build \
  --repo-root "$ROOT_DIR" \
  --run-id "$RUN_ID" \
  --output "$UNIT_FIXTURE_MANIFEST"
python3 "$UNIT_FIXTURE_TOOL" negative --output "$UNIT_FIXTURE_NEGATIVE"
python3 "$UNIT_FIXTURE_TOOL" verify \
  --repo-root "$ROOT_DIR" \
  --run-id "$RUN_ID" \
  --manifest "$UNIT_FIXTURE_MANIFEST"
python3 "$UNIT_FIXTURE_TOOL" verify-negative --path "$UNIT_FIXTURE_NEGATIVE"
v934_coverage_verify_exec

PHASE="report-verify"
python3 "$REPORT_TOOL" verify \
  --root "$ROOT_DIR" \
  --successor-dir "$SUCCESSOR_DIR" \
  --runner surefire \
  --variant-key unit \
  --outer-marker "$OUTER_MARKER" \
  --marker "$MARKER" \
  --raw-root "$ROOT_DIR" \
  --output-dir "$RUN_ROOT/unit"
PHASE="negative-probes"
python3 "$REPORT_TOOL" negative \
  --root "$ROOT_DIR" \
  --successor-dir "$SUCCESSOR_DIR" \
  --output-dir "$RUN_ROOT/negative"

PHASE="source-after"
SOURCE_AFTER="$(unit_source_hash)" || fail "Step 4 source hash rejected the post-Unit repository state"
[[ "$SOURCE_BEFORE" == "$SOURCE_AFTER" ]] || \
  fail "protected source changed during unit execution"
PHASE="finalize"
python3 "$REPORT_TOOL" finalize \
  --successor-dir "$SUCCESSOR_DIR" \
  --runner surefire \
  --outer-marker "$OUTER_MARKER" \
  --manifest "$RUN_ROOT/unit/report-manifest.json" \
  --output-dir "$RUN_ROOT/final"
FINAL_REPORT_MANIFEST_SHA256="$(python3 - "$RUN_ROOT/final/report-manifest.json" <<'PY'
import hashlib
from pathlib import Path
import sys

print(hashlib.sha256(Path(sys.argv[1]).read_bytes()).hexdigest())
PY
)"

PHASE="run-log-flush"
echo "[v934-unit] evidence prepared; flushing owned run logger"
v934_run_log_close || fail "owned run logger did not flush and exit cleanly"

# Green status and its hash-bound summary are published only after the logger
# has flushed and been reaped. This keeps the durable evidence order acyclic.
PHASE="completed"
v934_write_run_status 0
RUN_STATUS_SHA256="$(python3 - "$RUN_ROOT/run-status.env" <<'PY'
import hashlib
from pathlib import Path
import sys

print(hashlib.sha256(Path(sys.argv[1]).read_bytes()).hexdigest())
PY
)"
python3 - \
  "$RUN_ROOT/final/report-manifest.json" "$RUN_ROOT/summary.env" "$RUN_ID" \
  "$SOURCE_BEFORE" "$SOURCE_AFTER" "$GIT_HEAD" "$OUTER_MARKER_SHA256" \
  "$SUCCESSOR_MANIFEST_SHA256" "$FINAL_REPORT_MANIFEST_SHA256" "$RUN_STATUS_SHA256" \
  "$(sha256sum "$UNIT_FIXTURE_MANIFEST" | cut -d' ' -f1)" \
  "$(sha256sum "$UNIT_FIXTURE_NEGATIVE" | cut -d' ' -f1)" \
  "$(sha256sum "$UNIT_FIXTURE_LIFECYCLE_NEGATIVE" | cut -d' ' -f1)" <<'PY'
import json
import os
from pathlib import Path
import sys

manifest = json.load(open(sys.argv[1], encoding="utf-8"))
totals = manifest["totals"]
values = {
    "run_id": sys.argv[3],
    "runner": manifest["runner"],
    "git_head": sys.argv[6],
    "execution_keys": str(manifest["expected_execution_count"]),
    "execution_reports": str(manifest["report_count"]),
    "structural_reports": str(manifest["structural_report_count"]),
    "raw_reports": str(manifest["raw_report_count"]),
    "tests": str(totals["tests"]),
    "failures": str(totals["failures"]),
    "errors": str(totals["errors"]),
    "skipped": str(totals["skipped"]),
    "testcase_nodes": str(totals["testcase_nodes"]),
    "source_before": sys.argv[4],
    "source_after": sys.argv[5],
    "outer_marker_sha256": sys.argv[7],
    "successor_manifest_sha256": sys.argv[8],
    "final_report_manifest_sha256": sys.argv[9],
    "run_status_sha256": sys.argv[10],
    "mysql57_fixture_manifest_sha256": sys.argv[11],
    "mysql57_fixture_negative_sha256": sys.argv[12],
    "mysql57_fixture_lifecycle_negative_sha256": sys.argv[13],
    "status": "passed",
}
output = Path(sys.argv[2])
temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
temporary.write_text(
    "".join(f"{key}={value}\n" for key, value in values.items()),
    encoding="utf-8",
)
os.replace(temporary, output)
PY

v934_disarm_run_status_traps
echo "[v934-unit] PASS run=$RUN_ID evidence=$RUN_ROOT"
