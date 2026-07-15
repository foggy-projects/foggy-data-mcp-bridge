#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$ROOT_DIR/target/v934-step2-unit/runs/$RUN_ID"
RUNNER_NAME="surefire"
SUCCESSOR_DIR="$ROOT_DIR/scripts/v934/successor/step2"
STEP1_TOOL="$ROOT_DIR/scripts/v934/inventory_tool.py"
SUCCESSOR_TOOL="$ROOT_DIR/scripts/v934/step2_successor_tool.py"
REPORT_TOOL="$ROOT_DIR/scripts/v934/step2_report_tool.py"
AUTHORITY_LIB="$ROOT_DIR/scripts/v934/authority_runner_lib.sh"
OUTER_MARKER="$RUN_ROOT/run-context.json"

fail() {
  echo "[v934-unit] ERROR: $*" >&2
  exit 1
}

[[ "$#" -le 1 ]] || fail "usage: scripts/verify-v934-unit.sh [RUN_ID]"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
for command_name in bash flock git mvn python3 tee; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done
for required_file in "$STEP1_TOOL" "$SUCCESSOR_TOOL" "$REPORT_TOOL" "$AUTHORITY_LIB"; do
  [[ -f "$required_file" ]] || fail "required file missing: $required_file"
done
for variable_name in MAVEN_ARGS MAVEN_CONFIG MAVEN_OPTS; do
  variable_value="${!variable_name:-}"
  if [[ "$variable_value" =~ (skipTests|skipITs|skipUnitTests|multi-db|model-lifecycle|query-cache-real-query|failIfNo[A-Za-z0-9._-]*) ]]; then
    fail "$variable_name contains a forbidden lane override"
  fi
done
if [[ -f "$ROOT_DIR/.mvn/maven.config" ]] && \
   grep -Eq '(skipTests|skipITs|skipUnitTests|multi-db|model-lifecycle|query-cache-real-query|failIfNo[A-Za-z0-9._-]*)' "$ROOT_DIR/.mvn/maven.config"; then
  fail ".mvn/maven.config contains a forbidden lane override"
fi

# shellcheck source=scripts/v934/authority_runner_lib.sh
source "$AUTHORITY_LIB"
v934_acquire_authority_lock "$ROOT_DIR" "v934-unit" || exit 1

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
exec > >(tee -a "$RUN_ROOT/run.log") 2>&1
v934_install_run_status_traps

PHASE="source-baseline"
SOURCE_BEFORE="$(python3 "$SUCCESSOR_TOOL" source-hash --root "$ROOT_DIR")"
mapfile -t REACTOR_MODULES < <(python3 - "$STEP1_TOOL" "$ROOT_DIR" <<'PY'
import runpy
import sys
from pathlib import Path

namespace = runpy.run_path(sys.argv[1])
print("\n".join(namespace["active_reactor_modules"](Path(sys.argv[2]))))
PY
)
[[ "${#REACTOR_MODULES[@]}" -eq 24 ]] || fail "active reactor must contain 24 modules"
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
  -DskipUnitTests=true \
  -DskipITs=true \
  test-compile)
PHASE="successor-validate"
python3 "$SUCCESSOR_TOOL" validate --root "$ROOT_DIR" --directory "$SUCCESSOR_DIR"
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

echo "[v934-unit] running all-reactor Surefire unit authority"
PHASE="maven-unit"
(cd "$ROOT_DIR" && mvn -q \
  -P\!multi-db,\!model-lifecycle,\!query-cache-real-query \
  -DskipUnitTests=false \
  -DskipITs=true \
  -Dsurefire.failIfNoTests=false \
  test)

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
SOURCE_AFTER="$(python3 "$SUCCESSOR_TOOL" source-hash --root "$ROOT_DIR")"
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
  "$SUCCESSOR_MANIFEST_SHA256" "$FINAL_REPORT_MANIFEST_SHA256" "$RUN_STATUS_SHA256" <<'PY'
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
