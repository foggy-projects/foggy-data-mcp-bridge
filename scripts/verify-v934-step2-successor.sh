#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$ROOT_DIR/target/v934-step2-successor/runs/$RUN_ID"
STAGING_DIR="$RUN_ROOT/candidate"
OUTPUT_DIR="$ROOT_DIR/scripts/v934/successor/step2"
SUPERSEDES="${V934_STEP2_SUPERSEDES:-}"
SUPERSEDED_RUN_ID="step2-candidate-r7c-20260715"
SUPERSEDED_FREEZE_SHA256="3fea72715f651755897cee4464fd6075d5ea2187672e9d0fe66c97bf6d02a5d6"
SUPERSEDED_MANIFEST_SHA256="d0dfc94e1aa9bb8018d6ac6b5ae4b5c73ae25f6f6f81778a64e3f7787e2a3ca2"
SUPERSEDED_SUMMARY_SHA256="69a94475ca4c9d7162e9e6b217f637026cc790d3de4b654d9d12eb4c0dbe4f61"
CORRECTIVE_PLAN_SHA256="8c9c73c801efa786a71f07541a1003f456405525aeb7ae7583b2b938445ded09"
TEST_SOURCE_AMENDMENT_SHA256="771b08b3825778f5aab6f656e15de6dc568b8070811451e5b5c010f44ee69368"
R5_SOURCE_AMENDMENT_SHA256="3a4c6d424a6a0568f2818ecf36337515904a51d5b54a5f88c9f04beead615391"
R6_SOURCE_AMENDMENT_SHA256="0751dcd49d20f9722f9aab8d532db3ca105b20cd7970dc3a9d6169b614189c7d"
R7_RUNNER_AMENDMENT_SHA256="11ff594bf3689112ae0c0cd8be8e68bc6a5be3bfe0150adb8a80485ba3b10ac2"
SUCCESSOR_SEMANTICS="corrective-lane-and-authority-remediation-v6"
STEP1_TOOL="$ROOT_DIR/scripts/v934/inventory_tool.py"
STEP2_TOOL="$ROOT_DIR/scripts/v934/step2_successor_tool.py"
AUTHORITY_LIB="$ROOT_DIR/scripts/v934/authority_runner_lib.sh"
CORRECTIVE_PLAN="$ROOT_DIR/scripts/v934/step2-corrective-rename-plan.tsv"
TEST_SOURCE_AMENDMENT="$ROOT_DIR/scripts/v934/step2-test-source-amendment.tsv"
R5_SOURCE_AMENDMENT="$ROOT_DIR/scripts/v934/step2-r5-source-amendment.tsv"
R6_SOURCE_AMENDMENT="$ROOT_DIR/scripts/v934/step2-r6-source-amendment.tsv"
R7_RUNNER_AMENDMENT="$ROOT_DIR/scripts/v934/step2-r7-runner-amendment.tsv"
R8_AUTHORITY_AMENDMENT="$ROOT_DIR/scripts/v934/step2-r8-authority-amendment.tsv"
DISCOVERY_SOURCE="$ROOT_DIR/scripts/v934/JUnitDiscoveryInventory.java"
PLATFORM_VERSION="1.11.4"
LAUNCHER_JAR="$HOME/.m2/repository/org/junit/platform/junit-platform-launcher/$PLATFORM_VERSION/junit-platform-launcher-$PLATFORM_VERSION.jar"

fail() {
  echo "[v934-step2-successor] ERROR: $*" >&2
  exit 1
}

assert_superseded_identity() {
  local directory="$1"
  local summary_path="$ROOT_DIR/target/v934-step2-successor/runs/$SUPERSEDED_RUN_ID/summary.env"

  [[ -d "$directory" ]] || {
    echo "[v934-step2-successor] ERROR E_PUBLISH_CAS: superseded successor is not a directory: $directory" >&2
    return 1
  }
  for provenance_file in "$directory/contract-freeze.json" "$directory/SHA256SUMS" "$summary_path"; do
    [[ -f "$provenance_file" ]] || {
      echo "[v934-step2-successor] ERROR E_PUBLISH_CAS: superseded provenance file is missing: $provenance_file" >&2
      return 1
    }
  done
  [[ "$(sha256sum "$directory/contract-freeze.json" | awk '{print $1}')" == "$SUPERSEDED_FREEZE_SHA256" ]] || {
    echo "[v934-step2-successor] ERROR E_PUBLISH_CAS: superseded freeze identity changed" >&2
    return 1
  }
  [[ "$(sha256sum "$directory/SHA256SUMS" | awk '{print $1}')" == "$SUPERSEDED_MANIFEST_SHA256" ]] || {
    echo "[v934-step2-successor] ERROR E_PUBLISH_CAS: superseded manifest identity changed" >&2
    return 1
  }
  [[ "$(sha256sum "$summary_path" | awk '{print $1}')" == "$SUPERSEDED_SUMMARY_SHA256" ]] || {
    echo "[v934-step2-successor] ERROR E_PUBLISH_CAS: superseded summary identity changed" >&2
    return 1
  }
  python3 - "$directory" "$summary_path" "$SUPERSEDED_RUN_ID" \
    "$SUPERSEDED_FREEZE_SHA256" "$SUPERSEDED_MANIFEST_SHA256" <<'PY'
import hashlib
import json
import re
import sys
from pathlib import Path

directory = Path(sys.argv[1])
summary_path = Path(sys.argv[2])
run_id, freeze_sha, manifest_sha = sys.argv[3:]
freeze = json.loads((directory / "contract-freeze.json").read_text(encoding="utf-8"))
if freeze.get("status") != "confirmed" or freeze.get("decision") != "passed":
    raise SystemExit("superseded successor is not confirmed/passed")
summary_lines = summary_path.read_text(encoding="utf-8").splitlines()
if any("=" not in line for line in summary_lines):
    raise SystemExit("superseded summary is malformed")
summary = dict(line.split("=", 1) for line in summary_lines)
expected = {
    "run_id": run_id,
    "contract_freeze_sha256": freeze_sha,
    "contract_manifest_sha256": manifest_sha,
    "evidence_status": "confirmed",
    "status": "passed",
    "decision": "passed",
}
if any(summary.get(key) != value for key, value in expected.items()):
    raise SystemExit("superseded summary provenance fields differ")
manifest_lines = (directory / "SHA256SUMS").read_text(encoding="utf-8").splitlines()
if not manifest_lines:
    raise SystemExit("superseded manifest is empty")
seen = set()
for line in manifest_lines:
    match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._-]+)", line)
    if not match or match.group(2) in seen:
        raise SystemExit("superseded manifest contains an invalid entry")
    digest, name = match.groups()
    seen.add(name)
    path = directory / name
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
        raise SystemExit(f"superseded successor contains a stale hash: {name}")
PY
}

restore_superseded_after_publish() {
  local rejected="$RUN_ROOT/rejected-candidate-after-publish"

  if [[ -e "$OUTPUT_DIR" ]] && assert_superseded_identity "$OUTPUT_DIR"; then
    PUBLISH_STATE="rolled-back"
    ROLLBACK_OUTCOME="restored-exact-r7"
    return 0
  fi
  if [[ -e "$OUTPUT_DIR" ]]; then
    [[ ! -e "$rejected" ]] || {
      echo "[v934-step2-successor] ERROR: cannot restore superseded successor; rejected candidate path exists: $rejected" >&2
      PUBLISH_STATE="rollback-failed"
      ROLLBACK_OUTCOME="failed"
      return 1
    }
    mv "$OUTPUT_DIR" "$rejected" || {
      echo "[v934-step2-successor] ERROR: cannot quarantine rejected candidate during rollback" >&2
      PUBLISH_STATE="rollback-failed"
      ROLLBACK_OUTCOME="failed"
      return 1
    }
  fi
  [[ -d "$SUPERSEDED_ARCHIVE" ]] || {
    echo "[v934-step2-successor] ERROR: exact r7 archive is unavailable during rollback: $SUPERSEDED_ARCHIVE" >&2
    PUBLISH_STATE="rollback-failed"
    ROLLBACK_OUTCOME="failed"
    return 1
  }
  mv "$SUPERSEDED_ARCHIVE" "$OUTPUT_DIR" || {
    echo "[v934-step2-successor] ERROR: cannot restore superseded successor after publish failure" >&2
    PUBLISH_STATE="rollback-failed"
    ROLLBACK_OUTCOME="failed"
    return 1
  }
  if ! assert_superseded_identity "$OUTPUT_DIR"; then
    echo "[v934-step2-successor] ERROR: rollback did not restore exact r7 provenance" >&2
    PUBLISH_STATE="rollback-failed"
    ROLLBACK_OUTCOME="failed"
    return 1
  fi
  PUBLISH_STATE="rolled-back"
  ROLLBACK_OUTCOME="restored-exact-r7"
}

[[ "$#" -le 1 ]] || fail "usage: scripts/verify-v934-step2-successor.sh [RUN_ID]"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
for command_name in bash flock git java javac mv mvn python3 sha256sum tee; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done
for required_file in "$STEP1_TOOL" "$STEP2_TOOL" "$AUTHORITY_LIB" "$CORRECTIVE_PLAN" "$TEST_SOURCE_AMENDMENT" "$R5_SOURCE_AMENDMENT" "$R6_SOURCE_AMENDMENT" "$R7_RUNNER_AMENDMENT" "$R8_AUTHORITY_AMENDMENT" "$DISCOVERY_SOURCE" "$LAUNCHER_JAR"; do
  [[ -f "$required_file" ]] || fail "required file missing: $required_file"
done
# shellcheck source=scripts/v934/authority_runner_lib.sh
source "$AUTHORITY_LIB"
v934_acquire_authority_lock "$ROOT_DIR" "v934-step2-successor" || exit 1

[[ ! -e "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
python3 "$STEP2_TOOL" validate-amendment --root "$ROOT_DIR"
R8_AUTHORITY_AMENDMENT_SHA256="$(sha256sum "$R8_AUTHORITY_AMENDMENT" | awk '{print $1}')"
[[ "$(sha256sum "$CORRECTIVE_PLAN" | awk '{print $1}')" == "$CORRECTIVE_PLAN_SHA256" ]] || \
  fail "Step 2 corrective rename plan digest differs"
[[ "$(sha256sum "$TEST_SOURCE_AMENDMENT" | awk '{print $1}')" == "$TEST_SOURCE_AMENDMENT_SHA256" ]] || \
  fail "Step 2 test source amendment digest differs"
[[ "$(sha256sum "$R5_SOURCE_AMENDMENT" | awk '{print $1}')" == "$R5_SOURCE_AMENDMENT_SHA256" ]] || \
  fail "Step 2 r5 source amendment digest differs"
[[ "$(sha256sum "$R6_SOURCE_AMENDMENT" | awk '{print $1}')" == "$R6_SOURCE_AMENDMENT_SHA256" ]] || \
  fail "Step 2 r6 source amendment digest differs"
[[ "$(sha256sum "$R7_RUNNER_AMENDMENT" | awk '{print $1}')" == "$R7_RUNNER_AMENDMENT_SHA256" ]] || \
  fail "Step 2 r7 runner amendment digest differs"

[[ -e "$OUTPUT_DIR" ]] || fail "confirmed r7 successor is required before publishing r8"
[[ "$SUPERSEDES" == "$SUPERSEDED_RUN_ID" ]] || \
  fail "set V934_STEP2_SUPERSEDES=$SUPERSEDED_RUN_ID to supersede its confirmed provenance"
SUPERSEDED_RUN_ROOT="$ROOT_DIR/target/v934-step2-successor/runs/$SUPERSEDES"
SUPERSEDED_SUMMARY="$SUPERSEDED_RUN_ROOT/summary.env"
assert_superseded_identity "$OUTPUT_DIR" || fail "current successor failed the initial r7 provenance CAS"

mkdir -p "$RUN_ROOT/classpaths" "$RUN_ROOT/discovery" "$RUN_ROOT/tool-classes"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
PHASE="bootstrap"
PUBLISH_STATE="pre-archive"
ROLLBACK_OUTCOME="not-required"
SUPERSEDED_ARCHIVE="$RUN_ROOT/superseded-successor"
record_run_status() {
  local exit_code="$?"
  local finished_at
  local status_file="$RUN_ROOT/run-status.env"
  local status_tmp="$RUN_ROOT/.run-status.env.$$"
  trap - EXIT
  trap '' INT TERM HUP
  set +e
  if [[ -n "$SUPERSEDES" && "$PUBLISH_STATE" != "pre-archive" && "$PUBLISH_STATE" != "rolled-back" && "$PUBLISH_STATE" != "rollback-failed" ]]; then
    if [[ "$exit_code" -ne 0 || "$PHASE" != "completed" || "$PUBLISH_STATE" != "publish-validated" ]]; then
      echo "[v934-step2-successor] abnormal exit in publish state=$PUBLISH_STATE; restoring exact r7"
      if ! restore_superseded_after_publish; then
        exit_code=1
      fi
    fi
  fi
  if [[ "$exit_code" -eq 0 && ( "$PHASE" != "completed" || "$PUBLISH_STATE" != "publish-validated" ) ]]; then
    echo "[v934-step2-successor] ERROR: zero exit without a completed validated publish" >&2
    exit_code=1
  fi
  finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  if ! {
    printf 'run_id=%s\n' "$RUN_ID"
    printf 'git_head=%s\n' "$(git -C "$ROOT_DIR" rev-parse HEAD)"
    printf 'started_at=%s\n' "$STARTED_AT"
    printf 'finished_at=%s\n' "$finished_at"
    printf 'last_phase=%s\n' "$PHASE"
    printf 'exit_code=%s\n' "$exit_code"
    if [[ "$exit_code" -eq 0 && "$PHASE" == "completed" ]]; then
      printf 'status=passed\n'
    else
      printf 'status=failed\n'
    fi
    printf 'publish_state=%s\n' "$PUBLISH_STATE"
    printf 'rollback_outcome=%s\n' "$ROLLBACK_OUTCOME"
  } > "$status_tmp"; then
    echo "[v934-step2-successor] ERROR: cannot write outer run status" >&2
    exit_code=1
  elif ! mv "$status_tmp" "$status_file"; then
    echo "[v934-step2-successor] ERROR: cannot publish outer run status" >&2
    exit_code=1
  fi
  if [[ "$exit_code" -ne 0 && -n "$SUPERSEDES" && "$PUBLISH_STATE" == "publish-validated" ]]; then
    echo "[v934-step2-successor] outer status seal failed; restoring exact r7"
    restore_superseded_after_publish || true
  fi
  rm -f "$status_tmp"
  exit "$exit_code"
}
exit_on_signal() {
  local exit_code="$1"
  trap - INT TERM HUP
  exit "$exit_code"
}
exec > >(tee -a "$RUN_ROOT/run.log") 2>&1
trap record_run_status EXIT
trap 'exit_on_signal 130' INT
trap 'exit_on_signal 143' TERM
trap 'exit_on_signal 129' HUP

PHASE="source-baseline"
SOURCE_BEFORE="$(python3 "$STEP2_TOOL" source-hash --root "$ROOT_DIR")"

echo "[v934-step2-successor] clean test bytecode + test-compile only; no tests or external fixtures"
PHASE="test-compile"
mapfile -t REACTOR_MODULES < <(python3 - "$STEP1_TOOL" "$ROOT_DIR" <<'PY'
import runpy
import sys
from pathlib import Path

namespace = runpy.run_path(sys.argv[1])
print("\n".join(namespace["active_reactor_modules"](Path(sys.argv[2]))))
PY
)
[[ "${#REACTOR_MODULES[@]}" -eq 24 ]] || fail "active reactor must contain 24 modules"
for module in "${REACTOR_MODULES[@]}"; do
  rm -rf \
    "$ROOT_DIR/$module/target/test-classes" \
    "$ROOT_DIR/$module/target/generated-test-sources" \
    "$ROOT_DIR/$module/target/maven-status/maven-compiler-plugin/testCompile"
done
(cd "$ROOT_DIR" && mvn -q -P\!multi-db,\!model-lifecycle -DskipUnitTests=true -DskipITs=true test-compile)
PHASE="source-scan"
python3 "$STEP1_TOOL" scan --root "$ROOT_DIR" --run-dir "$RUN_ROOT"

mapfile -t MODULE_ROWS < <(python3 - "$RUN_ROOT/source-scan.json" <<'PY'
import json
import sys

for row in json.load(open(sys.argv[1], encoding="utf-8"))["selector_index"]:
    print("\t".join((row["module"], row["selectors"], row["classpath"], row["discovery"])))
PY
)
[[ "${#MODULE_ROWS[@]}" -gt 0 ]] || fail "discovery module index is empty"

HELPER_COMPILED=false
PHASE="discovery"
for module_row in "${MODULE_ROWS[@]}"; do
  IFS=$'\t' read -r module selectors_relative classpath_relative discovery_relative <<< "$module_row"
  classpath_file="$RUN_ROOT/$classpath_relative"
  raw_classpath_file="$classpath_file.raw"
  discovery_file="$RUN_ROOT/$discovery_relative"
  (cd "$ROOT_DIR" && mvn -q -P\!multi-db,\!model-lifecycle -pl "$module" dependency:build-classpath \
    -Dmdep.includeScope=test -Dmdep.outputFile="$raw_classpath_file")
  python3 "$STEP1_TOOL" classpath \
    --root "$ROOT_DIR" \
    --module "$module" \
    --input "$raw_classpath_file" \
    --output "$classpath_file"
  [[ -s "$classpath_file" ]] || fail "empty test classpath: $module"
  module_classpath="$(tr -d '\r\n' < "$classpath_file")"
  [[ "$module_classpath" == *"junit-jupiter-engine"* ]] || fail "Jupiter engine missing: $module"
  if [[ "$HELPER_COMPILED" == false ]]; then
    javac -encoding UTF-8 \
      -cp "$module_classpath:$LAUNCHER_JAR" \
      -d "$RUN_ROOT/tool-classes" \
      "$DISCOVERY_SOURCE"
    HELPER_COMPILED=true
  fi
  java -Dfile.encoding=UTF-8 \
    -cp "$RUN_ROOT/tool-classes:$ROOT_DIR/$module/target/test-classes:$ROOT_DIR/$module/target/classes:$module_classpath:$LAUNCHER_JAR" \
    JUnitDiscoveryInventory \
    "$module" "$RUN_ROOT/$selectors_relative" "$discovery_file"
done

PHASE="candidate-generate"
python3 "$STEP2_TOOL" generate \
  --root "$ROOT_DIR" \
  --run-dir "$RUN_ROOT" \
  --output-dir "$STAGING_DIR"
PHASE="negative-probes"
python3 "$STEP2_TOOL" negative --root "$ROOT_DIR" --directory "$STAGING_DIR"
PHASE="candidate-validate"
python3 "$STEP2_TOOL" validate --root "$ROOT_DIR" --directory "$STAGING_DIR"

PHASE="source-after"
SOURCE_AFTER="$(python3 "$STEP2_TOOL" source-hash --root "$ROOT_DIR")"
[[ "$SOURCE_BEFORE" == "$SOURCE_AFTER" ]] || \
  fail "protected source changed during discovery: before=$SOURCE_BEFORE after=$SOURCE_AFTER"

FREEZE_SHA="$(sha256sum "$STAGING_DIR/contract-freeze.json" | awk '{print $1}')"
MANIFEST_SHA="$(sha256sum "$STAGING_DIR/SHA256SUMS" | awk '{print $1}')"
PHASE="candidate-summary"
{
  printf 'run_id=%s\n' "$RUN_ID"
  printf 'git_head=%s\n' "$(git -C "$ROOT_DIR" rev-parse HEAD)"
  printf 'source_before=%s\n' "$SOURCE_BEFORE"
  printf 'source_after=%s\n' "$SOURCE_AFTER"
  printf 'parent_manifest_sha256=%s\n' 'e601c6c70ff02e9e50b86fd2b14b14aba9cfede096b42c93ff6e5968a918640f'
  printf 'rename_plan_sha256=%s\n' 'acba7a9dc22c9c0fdbaa0438fe91018b61eee8a457a36f1918995f39aa74cfe2'
  printf 'corrective_rename_plan_sha256=%s\n' "$CORRECTIVE_PLAN_SHA256"
  printf 'test_source_amendment_sha256=%s\n' "$TEST_SOURCE_AMENDMENT_SHA256"
  printf 'r5_source_amendment_sha256=%s\n' "$R5_SOURCE_AMENDMENT_SHA256"
  printf 'r6_source_amendment_sha256=%s\n' "$R6_SOURCE_AMENDMENT_SHA256"
  printf 'r7_runner_amendment_sha256=%s\n' "$R7_RUNNER_AMENDMENT_SHA256"
  printf 'r8_authority_amendment_sha256=%s\n' "$R8_AUTHORITY_AMENDMENT_SHA256"
  printf 'successor_semantics=%s\n' "$SUCCESSOR_SEMANTICS"
  printf 'supersedes_run_id=%s\n' "$SUPERSEDED_RUN_ID"
  printf 'superseded_contract_freeze_sha256=%s\n' "$SUPERSEDED_FREEZE_SHA256"
  printf 'superseded_contract_manifest_sha256=%s\n' "$SUPERSEDED_MANIFEST_SHA256"
  printf 'superseded_confirmed_summary_sha256=%s\n' "$SUPERSEDED_SUMMARY_SHA256"
  printf 'workspace_sources=532\n'
  printf 'discovery_rows=820\n'
  printf 'structural_reports=59\n'
  printf 'execution_keys=770\n'
  printf 'required_step2=724\n'
  printf 'deferred_step3=46\n'
  printf 'predecessor_edges=519\n'
  printf 'predecessor_execution_refs=480\n'
  printf 'predecessor_structural_refs=39\n'
  printf 'planned_rename_reports=64\n'
  printf 'applied_positive_rename_reports=60\n'
  printf 'structural_rename_reports=4\n'
  printf 'planned_rename_execution_keys=76\n'
  printf 'applied_positive_rename_execution_keys=72\n'
  printf 'contract_freeze_sha256=%s\n' "$FREEZE_SHA"
  printf 'contract_manifest_sha256=%s\n' "$MANIFEST_SHA"
  printf 'evidence_status=candidate\n'
  printf 'status=passed\n'
  printf 'decision=pending-independent-review\n'
} > "$RUN_ROOT/summary.env"
python3 "$STEP2_TOOL" validate-summary \
  --root "$ROOT_DIR" \
  --directory "$STAGING_DIR" \
  --summary "$RUN_ROOT/summary.env"

PHASE="candidate-publish"
mkdir -p "$(dirname "$OUTPUT_DIR")"
assert_superseded_identity "$OUTPUT_DIR" || fail "superseded successor changed before publish CAS"
PHASE="supersede-archive"
[[ ! -e "$SUPERSEDED_ARCHIVE" ]] || fail "superseded archive already exists: $SUPERSEDED_ARCHIVE"
{
  printf 'run_id=%s\n' "$SUPERSEDES"
  printf 'contract_freeze_sha256=%s\n' "$SUPERSEDED_FREEZE_SHA256"
  printf 'contract_manifest_sha256=%s\n' "$SUPERSEDED_MANIFEST_SHA256"
  printf 'confirmed_summary_sha256=%s\n' "$SUPERSEDED_SUMMARY_SHA256"
  printf 'original_summary=target/v934-step2-successor/runs/%s/summary.env\n' "$SUPERSEDED_RUN_ID"
} > "$RUN_ROOT/superseded-provenance.env"
PUBLISH_STATE="archive-started"
mv "$OUTPUT_DIR" "$SUPERSEDED_ARCHIVE"
PUBLISH_STATE="r7-archived"
if ! assert_superseded_identity "$SUPERSEDED_ARCHIVE"; then
  if restore_superseded_after_publish; then
    fail "archived superseded successor failed the r7 provenance CAS; restored exact r7"
  fi
  fail "archived superseded successor failed the r7 provenance CAS and exact rollback failed"
fi
if ! mv "$STAGING_DIR" "$OUTPUT_DIR"; then
  if restore_superseded_after_publish; then
    fail "candidate publish failed; restored exact r7"
  fi
  fail "candidate publish failed and exact r7 rollback failed"
fi
PUBLISH_STATE="candidate-published"

PHASE="post-publish-validate"
if ! python3 "$STEP2_TOOL" validate --root "$ROOT_DIR" --directory "$OUTPUT_DIR" || \
   ! python3 "$STEP2_TOOL" validate-summary \
     --root "$ROOT_DIR" \
     --directory "$OUTPUT_DIR" \
     --summary "$RUN_ROOT/summary.env"; then
  restore_superseded_after_publish || fail "post-publish validation failed and exact r7 rollback failed"
  fail "post-publish validation failed; restored exact r7"
fi

PUBLISH_STATE="publish-validated"
PHASE="completed"
echo "[v934-step2-successor] PASS candidate=$OUTPUT_DIR run=$RUN_ID"
