#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ID="${1:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RUN_ROOT="$ROOT_DIR/target/v933-batch1-red/runs/$RUN_ID"
SUMMARY="$RUN_ROOT/summary.tsv"

fail() {
  echo "[v933-batch1-red] ERROR: $*" >&2
  exit 1
}

[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid run id: $RUN_ID"
[[ ! -e "$RUN_ROOT" ]] || fail "run directory already exists: $RUN_ROOT"
mkdir -p "$RUN_ROOT"
printf 'step\tcase\tmodule\tclass\ttests\tresult\n' > "$SUMMARY"

RED_SOURCE_DIRS=(
  "$ROOT_DIR/foggy-dataset-model-engine/src/test/java"
  "$ROOT_DIR/foggy-runtime-api/src/test/java"
  "$ROOT_DIR/foggy-fsscript/src/test/java"
  "$ROOT_DIR/foggy-dataset-mcp/src/test/java"
)
set +e
rg -n --glob '*RedBaseline.java' \
  'Thread\.sleep|TimeUnit\.sleep' "${RED_SOURCE_DIRS[@]}"
RG_STATUS=$?
set -e
case "$RG_STATUS" in
  0) fail "sleep-based RedBaseline detected" ;;
  1) ;;
  *) fail "RedBaseline sleep source audit failed with rg status $RG_STATUS" ;;
esac

# Every repair-before case has now been promoted to a normal owning green
# suite. Zero executed tests is valid here only because the source inventory is
# also proven empty; a missing/renamed red source cannot silently look green.
mapfile -d '' REMAINING_RED_SOURCES < <(
  find "${RED_SOURCE_DIRS[@]}" -type f -name '*RedBaseline.java' -print0 | sort -z
)
printf '%s\n' "${REMAINING_RED_SOURCES[@]}" > "$RUN_ROOT/remaining-red-sources.txt"
[[ "${#REMAINING_RED_SOURCES[@]}" -eq 0 ]] || \
  fail "remaining RedBaseline sources=${#REMAINING_RED_SOURCES[@]}; see $RUN_ROOT/remaining-red-sources.txt"

printf '0\tnone\tn/a\tn/a\t0\tNONE_REMAINING\n' >> "$SUMMARY"
find "$RUN_ROOT" -type f ! -name 'sha256sum.txt' -print0 \
  | sort -z \
  | xargs -0 -r sha256sum > "$RUN_ROOT/sha256sum.txt"
printf '%s\n' "$RUN_ID" > "$ROOT_DIR/target/v933-batch1-red/latest-run-id"

echo "[v933-batch1-red] COMPLETE run=$RUN_ID cases=0 expected_tests=0 remaining_sources=0"
echo "[v933-batch1-red] summary=$SUMMARY"
