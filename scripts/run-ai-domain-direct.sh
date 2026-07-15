#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

CASE_FILES="${AI_TEST_CASE_FILES:-}"
MODEL_LIST="${AI_TEST_MODEL_LIST:-}"
SKIP_DB_CHECK=0

usage() {
  cat <<'USAGE'
Usage: scripts/run-ai-domain-direct.sh --case-files FILES --models MODELS [options]

Runs direct MCP tool baseline for optional AI fixture packs.

Environment:
  AI_TEST_CASE_FILES       Comma-separated fixture resources.
  AI_TEST_MODEL_LIST       Comma-separated QM model names.

Options:
  --case-files FILES       Comma-separated resources, e.g. ai-test-cases/odoo-stock-mrp-tests.json.
  --models MODELS          Comma-separated QM model names.
  --skip-db-check          Do not run scripts/ensure-ai-test-mysql.sh first.
  -h, --help               Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --case-files)
      shift
      CASE_FILES="${1:-}"
      ;;
    --models)
      shift
      MODEL_LIST="${1:-}"
      ;;
    --skip-db-check)
      SKIP_DB_CHECK=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift
done

if [[ -z "$CASE_FILES" ]]; then
  echo "--case-files or AI_TEST_CASE_FILES is required." >&2
  exit 1
fi
if [[ -z "$MODEL_LIST" ]]; then
  echo "--models or AI_TEST_MODEL_LIST is required." >&2
  exit 1
fi

IFS=',' read -r -a MODELS <<< "$MODEL_LIST"
MODEL_PROPS=()
for i in "${!MODELS[@]}"; do
  model="$(echo "${MODELS[$i]}" | xargs)"
  if [[ -n "$model" ]]; then
    MODEL_PROPS+=("-Dfoggy.mcp.semantic.model-list[$i]=$model")
  fi
done

if [[ "${#MODEL_PROPS[@]}" -eq 0 ]]; then
  echo "No valid models were provided." >&2
  exit 1
fi

cd "$REPO_ROOT"

if [[ "$SKIP_DB_CHECK" -eq 0 ]]; then
  scripts/ensure-ai-test-mysql.sh --no-start
fi

echo "[ai-domain-direct] caseFiles=$CASE_FILES"
echo "[ai-domain-direct] models=$MODEL_LIST"

IT_REPORT="$REPO_ROOT/foggy-dataset-mcp/target/failsafe-reports/TEST-com.foggyframework.dataset.mcp.ai.AiToolsIT\$DirectToolCallTest.xml"
rm -f "$IT_REPORT"

JAVA_HOME="${JAVA_HOME:-/Users/fengjianguang/.jdk/temurin-17/Contents/Home}" \
AI_TEST_CASE_FILES="$CASE_FILES" \
mvn -pl foggy-dataset-mcp -am -P'!multi-db' \
  -Dit.test='AiToolsIT$DirectToolCallTest#allDirectCalls_summary' \
  "${MODEL_PROPS[@]}" \
  -Dfoggy.mcp.semantic.use-all-models=false \
  -DskipUnitTests=true \
  -DskipITs=false \
  -Dfailsafe.failIfNoSpecifiedTests=false verify

[[ -s "$IT_REPORT" ]] && grep -q '<testcase' "$IT_REPORT" || {
  echo "Expected fresh Failsafe report is missing or empty: $IT_REPORT" >&2
  exit 1
}
