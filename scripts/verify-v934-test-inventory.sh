#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$ROOT_DIR/target/v934-step1-inventory/runs/$RUN_ID"
OUTPUT_DIR="$ROOT_DIR/scripts/v934"
TOOL="$OUTPUT_DIR/inventory_tool.py"
DISCOVERY_SOURCE="$OUTPUT_DIR/JUnitDiscoveryInventory.java"
OVERRIDES="$OUTPUT_DIR/inventory-overrides.json"
AUTHORITY_ROOT="${V934_PREDECESSOR_AUTHORITY_ROOT:-$ROOT_DIR/target/v933-batch7-regression/runs/20260714T084351Z-3271604}"
SUPERSEDES="${V934_SUPERSEDES:-none}"
PLATFORM_VERSION="1.11.4"
LAUNCHER_JAR="$HOME/.m2/repository/org/junit/platform/junit-platform-launcher/$PLATFORM_VERSION/junit-platform-launcher-$PLATFORM_VERSION.jar"

fail() {
  echo "[v934-inventory] ERROR: $*" >&2
  exit 1
}

[[ "$#" -le 1 ]] || fail "usage: scripts/verify-v934-test-inventory.sh [RUN_ID]"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
[[ ! -e "$RUN_ROOT" ]] || fail "run root already exists: $RUN_ROOT"
for command_name in bash git java javac mvn python3 sha256sum; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command missing: $command_name"
done
for required_file in "$TOOL" "$DISCOVERY_SOURCE" "$OVERRIDES" "$LAUNCHER_JAR"; do
  [[ -f "$required_file" ]] || fail "required file missing: $required_file"
done
[[ -d "$AUTHORITY_ROOT" ]] || fail "predecessor authority root missing: $AUTHORITY_ROOT"

mkdir -p "$RUN_ROOT/classpaths" "$RUN_ROOT/discovery" "$RUN_ROOT/tool-classes"
SOURCE_BEFORE="$(python3 "$TOOL" source-hash --root "$ROOT_DIR")"

echo "[v934-inventory] test-compile only; tests and external fixtures are not executed"
(cd "$ROOT_DIR" && mvn -q -P\!multi-db -DskipTests=true -DskipITs=true test-compile)
python3 "$TOOL" scan --root "$ROOT_DIR" --run-dir "$RUN_ROOT"

mapfile -t MODULE_ROWS < <(python3 - "$RUN_ROOT/source-scan.json" <<'PY'
import json
import sys

for row in json.load(open(sys.argv[1], encoding="utf-8"))["selector_index"]:
    print("\t".join((row["module"], row["selectors"], row["classpath"], row["discovery"])))
PY
)
[[ "${#MODULE_ROWS[@]}" -gt 0 ]] || fail "discovery module index is empty"

HELPER_COMPILED=false
for module_row in "${MODULE_ROWS[@]}"; do
  IFS=$'\t' read -r module selectors_relative classpath_relative discovery_relative <<< "$module_row"
  classpath_file="$RUN_ROOT/$classpath_relative"
  raw_classpath_file="$classpath_file.raw"
  discovery_file="$RUN_ROOT/$discovery_relative"
  mkdir -p "$(dirname "$classpath_file")" "$(dirname "$discovery_file")"
  (cd "$ROOT_DIR" && mvn -q -P\!multi-db -pl "$module" dependency:build-classpath \
    -Dmdep.includeScope=test -Dmdep.outputFile="$raw_classpath_file")
  python3 "$TOOL" classpath \
    --root "$ROOT_DIR" \
    --module "$module" \
    --input "$raw_classpath_file" \
    --output "$classpath_file"
  [[ -s "$classpath_file" ]] || fail "empty test classpath: $module"
  module_classpath="$(tr -d '\r\n' < "$classpath_file")"
  [[ "$module_classpath" == *"junit-jupiter-engine"* ]] || \
    fail "Jupiter engine missing from classpath: $module"
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

python3 "$TOOL" generate \
  --root "$ROOT_DIR" \
  --run-dir "$RUN_ROOT" \
  --output-dir "$OUTPUT_DIR" \
  --overrides "$OVERRIDES" \
  --authority-root "$AUTHORITY_ROOT"
python3 "$TOOL" negative --root "$ROOT_DIR" --directory "$OUTPUT_DIR"
python3 "$TOOL" validate --root "$ROOT_DIR" --directory "$OUTPUT_DIR"

SOURCE_AFTER="$(python3 "$TOOL" source-hash --root "$ROOT_DIR")"
[[ "$SOURCE_BEFORE" == "$SOURCE_AFTER" ]] || \
  fail "protected source state changed during discovery: before=$SOURCE_BEFORE after=$SOURCE_AFTER"
CLASSPATH_ENTRY_COUNT="$(awk 'FNR > 1 { count++ } END { print count + 0 }' "$RUN_ROOT"/classpaths/*.sha256.tsv)"
GENERATOR_CLASSES_SHA256="$(python3 - "$OUTPUT_DIR/contract-freeze.json" <<'PY'
import json
import sys

print(json.load(open(sys.argv[1], encoding="utf-8"))["toolchain"]["generator_classes_sha256"])
PY
)"
GENERATOR_CLASS_FILES="$(python3 - "$OUTPUT_DIR/contract-freeze.json" <<'PY'
import json
import sys

print(",".join(json.load(open(sys.argv[1], encoding="utf-8"))["toolchain"]["generator_class_files"]))
PY
)"
mapfile -t RENAME_SUMMARY < <(python3 - "$OUTPUT_DIR/contract-freeze.json" <<'PY'
import json
import sys

freeze = json.load(open(sys.argv[1], encoding="utf-8"))
for key in ("rename_sources", "rename_reports", "rename_execution_keys", "rename_predecessor_edges"):
    print(freeze["counts"][key])
print(freeze["successor_policy"]["rename_plan_sha256"])
PY
)
[[ "${#RENAME_SUMMARY[@]}" -eq 5 ]] || fail "rename summary fields are incomplete"

{
  printf 'run_id=%s\n' "$RUN_ID"
  printf 'supersedes=%s\n' "$SUPERSEDES"
  printf 'git_head=%s\n' "$(git -C "$ROOT_DIR" rev-parse HEAD)"
  printf 'source_before=%s\n' "$SOURCE_BEFORE"
  printf 'source_after=%s\n' "$SOURCE_AFTER"
  printf 'discovery_modules=%s\n' "${#MODULE_ROWS[@]}"
  printf 'classpath_entries=%s\n' "$CLASSPATH_ENTRY_COUNT"
  printf 'rename_sources=%s\n' "${RENAME_SUMMARY[0]}"
  printf 'rename_reports=%s\n' "${RENAME_SUMMARY[1]}"
  printf 'rename_execution_keys=%s\n' "${RENAME_SUMMARY[2]}"
  printf 'rename_predecessor_edges=%s\n' "${RENAME_SUMMARY[3]}"
  printf 'rename_plan_sha256=%s\n' "${RENAME_SUMMARY[4]}"
  printf 'launcher_sha256=%s\n' "$(sha256sum "$LAUNCHER_JAR" | awk '{print $1}')"
  printf 'wrapper_source_sha256=%s\n' "$(sha256sum "$ROOT_DIR/scripts/verify-v934-test-inventory.sh" | awk '{print $1}')"
  printf 'tool_source_sha256=%s\n' "$(sha256sum "$TOOL" | awk '{print $1}')"
  printf 'discovery_source_sha256=%s\n' "$(sha256sum "$DISCOVERY_SOURCE" | awk '{print $1}')"
  printf 'generator_class_files=%s\n' "$GENERATOR_CLASS_FILES"
  printf 'generator_classes_sha256=%s\n' "$GENERATOR_CLASSES_SHA256"
  printf 'java_version=%s\n' "$(java -version 2>&1 | sed -n '1p')"
  printf 'javac_version=%s\n' "$(javac -version 2>&1 | sed -n '1p')"
  printf 'maven_version=%s\n' "$(mvn -version 2>&1 | sed -n '1p')"
  printf 'contract_freeze_sha256=%s\n' "$(sha256sum "$OUTPUT_DIR/contract-freeze.json" | awk '{print $1}')"
  printf 'contract_manifest_sha256=%s\n' "$(sha256sum "$OUTPUT_DIR/SHA256SUMS" | awk '{print $1}')"
  printf 'evidence_status=candidate\n'
  printf 'status=passed\n'
} > "$RUN_ROOT/summary.env"

echo "[v934-inventory] PASS run=$RUN_ID evidence=$RUN_ROOT"
