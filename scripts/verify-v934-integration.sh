#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$ROOT_DIR/target/v934-step2-integration/runs/$RUN_ID"
RUNNER_NAME="failsafe"
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
OUTER_MARKER="$RUN_ROOT/run-context.json"

fail() {
  echo "[v934-integration] ERROR: $*" >&2
  exit 1
}

[[ "$#" -le 1 ]] || fail "usage: scripts/verify-v934-integration.sh [RUN_ID]"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
for command_name in bash flock git mvn python3 tee; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done
for required_file in \
  "$STEP1_TOOL" "$STEP1_FREEZE" "$REPORT_TOOL" "$AUTHORITY_LIB" \
  "$STEP4_AUTHORITY_LIB" "$COVERAGE_LIB" "$RUN_LOG_LIB" "$STEP4_TOOL" "$REPORT_VIEW_TOOL"; do
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
# shellcheck source=scripts/v934/step4/authority_parent_lib.sh
source "$STEP4_AUTHORITY_LIB"
# shellcheck source=scripts/v934/step4/coverage_runner_lib.sh
source "$COVERAGE_LIB"
# shellcheck source=scripts/v934/step4/run_log_lifecycle_lib.sh
source "$RUN_LOG_LIB"
CURRENT_STEP4_SOURCE=""
if [[ "${V934_AUTHORITY_LOCK_MODE:-standalone}" == inherited ]]; then
  CURRENT_STEP4_SOURCE="$(python3 "$STEP4_TOOL" source-hash --repo-root "$ROOT_DIR" | \
    python3 -c 'import json,sys; print(json.load(sys.stdin)["sha256"])')"
  v934_step4_validate_inherited_authority \
    "$ROOT_DIR" "v934-integration" "$CURRENT_STEP4_SOURCE" || exit 1
elif [[ "${V934_AUTHORITY_LOCK_MODE:-standalone}" == standalone ]]; then
  v934_acquire_authority_lock "$ROOT_DIR" "v934-integration" || exit 1
else
  fail "unsupported authority mode: ${V934_AUTHORITY_LOCK_MODE:-}"
fi

[[ ! -e "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
GIT_HEAD="$(git -C "$ROOT_DIR" rev-parse HEAD)"
mkdir -p "$RUN_ROOT/variants"
printf 'preserve-root-authority-evidence\n' > "$RUN_ROOT/cleanup.sentinel"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
PHASE="bootstrap"
SOURCE_BEFORE=""
SOURCE_AFTER=""
OUTER_MARKER_SHA256=""
SUCCESSOR_MANIFEST_SHA256=""
FINAL_REPORT_MANIFEST_SHA256=""
v934_install_run_status_traps
trap 'v934_run_log_exit_trap "$?" v934_record_run_status' EXIT
v934_run_log_open "$RUN_ROOT" v934-integration || fail "cannot open the owned run logger"

PHASE="source-baseline"
if [[ -n "$CURRENT_STEP4_SOURCE" ]]; then
  SOURCE_BEFORE="$CURRENT_STEP4_SOURCE"
else
  SOURCE_BEFORE="$(python3 "$STEP4_TOOL" source-hash --repo-root "$ROOT_DIR" | \
    python3 -c 'import json,sys; print(json.load(sys.stdin)["sha256"])')"
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
PHASE="test-bytecode-cleanup"
for module in "${REACTOR_MODULES[@]}"; do
  [[ -d "$ROOT_DIR/$module" ]] || fail "reactor module is missing: $module"
  rm -rf \
    "$ROOT_DIR/$module/target/failsafe-reports" \
    "$ROOT_DIR/$module/target/test-classes" \
    "$ROOT_DIR/$module/target/generated-test-sources" \
    "$ROOT_DIR/$module/target/maven-status/maven-compiler-plugin/testCompile"
done
[[ -f "$RUN_ROOT/cleanup.sentinel" ]] || fail "test-bytecode cleanup removed root authority evidence"

echo "[v934-integration] bootstrap clean reactor test bytecode"
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

mapfile -t VARIANT_ROWS < <(python3 - "$SUCCESSOR_DIR/step2-required-execution.tsv" "$SUCCESSOR_DIR/source-inventory.tsv" <<'PY'
import csv
import sys
from collections import defaultdict

with open(sys.argv[2], encoding="utf-8", newline="") as stream:
    source_by_id = {row["source_id"]: row for row in csv.DictReader(stream, delimiter="\t")}
with open(sys.argv[1], encoding="utf-8", newline="") as stream:
    rows = [row for row in csv.DictReader(stream, delimiter="\t") if row["runner"] == "failsafe"]
groups = defaultdict(list)
for row in rows:
    groups[row["variant_key"]].append(row)
for variant in sorted(groups):
    group = groups[variant]
    modules = ",".join(sorted({row["owner"] for row in group}))
    selectors = ",".join(sorted({source_by_id[row["source_id"]]["top_level_fqcn"] for row in group}))
    print("\t".join((variant, modules, selectors)))
PY
)
[[ "${#VARIANT_ROWS[@]}" -gt 0 ]] || fail "Failsafe variant inventory is empty"

MANIFEST_ARGS=()
for variant_row in "${VARIANT_ROWS[@]}"; do
  IFS=$'\t' read -r variant modules selectors <<< "$variant_row"
  [[ "$variant" =~ ^[A-Za-z0-9._-]+$ ]] || fail "unsafe variant key: $variant"
  for module in "${REACTOR_MODULES[@]}"; do
    rm -rf "$ROOT_DIR/$module/target/failsafe-reports"
  done
  variant_root="$RUN_ROOT/variants/$variant"
  mkdir -p "$variant_root"
  marker="$variant_root/run-marker.json"
  python3 - "$marker" "$RUN_ID" "$RUNNER_NAME" "$variant" "$OUTER_MARKER_SHA256" <<'PY'
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
  echo "[v934-integration] running Failsafe variant=$variant modules=$modules"
  PHASE="variant-$variant"
  v934_coverage_configure it "$variant"
  (cd "$ROOT_DIR" && mvn -q \
    -P\!multi-db,\!model-lifecycle,\!query-cache-real-query \
    -pl "$modules" -am \
    -Dit.test="$selectors" \
    -Dspring.profiles.active=sqlite \
    -Dv933.cache.provider=caffeine \
    -DskipUnitTests=true \
    -DskipITs=false \
    -Dfailsafe.failIfNoTests=false \
    -Dfailsafe.failIfNoSpecifiedTests=false \
    "${V934_COVERAGE_MAVEN_ARGS[@]}" \
    verify)
  v934_coverage_verify_exec
  PHASE="variant-report-$variant"
  python3 "$REPORT_TOOL" verify \
    --root "$ROOT_DIR" \
    --successor-dir "$SUCCESSOR_DIR" \
    --runner failsafe \
    --variant-key "$variant" \
    --outer-marker "$OUTER_MARKER" \
    --marker "$marker" \
    --raw-root "$ROOT_DIR" \
    --output-dir "$variant_root/evidence"
  MANIFEST_ARGS+=(--manifest "$variant_root/evidence/report-manifest.json")
done

PHASE="negative-probes"
python3 "$REPORT_TOOL" negative \
  --root "$ROOT_DIR" \
  --successor-dir "$SUCCESSOR_DIR" \
  --output-dir "$RUN_ROOT/negative"

PHASE="source-after"
SOURCE_AFTER="$(python3 "$STEP4_TOOL" source-hash --repo-root "$ROOT_DIR" | \
  python3 -c 'import json,sys; print(json.load(sys.stdin)["sha256"])')"
[[ "$SOURCE_BEFORE" == "$SOURCE_AFTER" ]] || \
  fail "protected source changed during integration execution"
PHASE="finalize"
python3 "$REPORT_TOOL" finalize \
  --successor-dir "$SUCCESSOR_DIR" \
  --runner failsafe \
  --outer-marker "$OUTER_MARKER" \
  "${MANIFEST_ARGS[@]}" \
  --output-dir "$RUN_ROOT/final"
FINAL_REPORT_MANIFEST_SHA256="$(python3 - "$RUN_ROOT/final/report-manifest.json" <<'PY'
import hashlib
from pathlib import Path
import sys

print(hashlib.sha256(Path(sys.argv[1]).read_bytes()).hexdigest())
PY
)"

PHASE="run-log-flush"
echo "[v934-integration] evidence prepared; flushing owned run logger"
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
    "variants": ",".join(manifest["variant_keys"]),
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
echo "[v934-integration] PASS run=$RUN_ID evidence=$RUN_ROOT"
