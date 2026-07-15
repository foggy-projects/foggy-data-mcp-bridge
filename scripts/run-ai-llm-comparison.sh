#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

MODEL="${AI_TEST_OPENAI_MODEL:-gemini-pro-agent}"
BASE_URL="${AI_TEST_OPENAI_BASE_URL:-https://codex2.qlfloor.com:7443}"
API_KEY="${AI_TEST_OPENAI_API_KEY:-${OPENAI_API_KEY:-}}"
CASE_IDS="${AI_TEST_CASE_IDS:-}"
CATEGORIES="${AI_TEST_CATEGORIES:-}"
MAX_CASES="${AI_TEST_MAX_CASES:-0}"
FAIL_ON_MISMATCH="${AI_TEST_LLM_FAIL_ON_MISMATCH:-false}"
SKIP_DB_CHECK=0

usage() {
  cat <<'USAGE'
Usage: scripts/run-ai-llm-comparison.sh [options]

Runs the opt-in MCP AI question-bank comparison:
  - direct tool baseline for selected cases
  - real LLM tool-calling pass for the same cases
  - structured report at foggy-dataset-mcp/target/ai-test-report-summary.json

Environment:
  AI_TEST_OPENAI_API_KEY    Required unless OPENAI_API_KEY is set.
  AI_TEST_OPENAI_MODEL      Default: gemini-pro-agent
  AI_TEST_OPENAI_BASE_URL   Default: https://codex2.qlfloor.com:7443
                            For Spring AI, do not append /v1 here.
  AI_TEST_CASE_IDS          Optional comma-separated case IDs.
  AI_TEST_CATEGORIES        Optional comma-separated categories.
  AI_TEST_MAX_CASES         Optional maximum selected cases.

Options:
  --model NAME              Override AI_TEST_OPENAI_MODEL.
  --base-url URL            Override AI_TEST_OPENAI_BASE_URL. Do not append /v1.
  --case-ids IDS            Comma-separated case IDs, e.g. META-001,QUERY-001.
  --categories NAMES        Comma-separated category names, e.g. METADATA,SIMPLE_QUERY.
  --max-cases N             Limit selected cases.
  --fail-on-mismatch        Fail Maven test when any LLM case fails validation.
  --skip-db-check           Do not run scripts/ensure-ai-test-mysql.sh first.
  -h, --help                Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model)
      shift
      MODEL="${1:-}"
      ;;
    --base-url)
      shift
      BASE_URL="${1:-}"
      ;;
    --case-ids)
      shift
      CASE_IDS="${1:-}"
      ;;
    --categories)
      shift
      CATEGORIES="${1:-}"
      ;;
    --max-cases)
      shift
      MAX_CASES="${1:-}"
      ;;
    --fail-on-mismatch)
      FAIL_ON_MISMATCH=true
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

if [[ -z "$API_KEY" ]]; then
  echo "AI_TEST_OPENAI_API_KEY or OPENAI_API_KEY is required." >&2
  exit 1
fi
if [[ -z "$MODEL" ]]; then
  echo "AI model is required." >&2
  exit 1
fi
if [[ "$BASE_URL" == */v1 ]]; then
  echo "Spring AI base URL should not include /v1: $BASE_URL" >&2
  exit 1
fi

cd "$REPO_ROOT"

if [[ "$SKIP_DB_CHECK" -eq 0 ]]; then
  scripts/ensure-ai-test-mysql.sh
fi

echo "[ai-llm] model=$MODEL"
echo "[ai-llm] baseUrl=$BASE_URL"
echo "[ai-llm] caseIds=${CASE_IDS:-<all>}"
echo "[ai-llm] categories=${CATEGORIES:-<all>}"
echo "[ai-llm] maxCases=$MAX_CASES"
echo "[ai-llm] failOnMismatch=$FAIL_ON_MISMATCH"

UNIT_REPORT_DIR="$REPO_ROOT/foggy-dataset-mcp/target/surefire-reports"
IT_REPORT="$REPO_ROOT/foggy-dataset-mcp/target/failsafe-reports/TEST-com.foggyframework.dataset.mcp.ai.AiToolsIT\$AiModelCallTest.xml"
rm -f \
  "$UNIT_REPORT_DIR/TEST-com.foggyframework.dataset.mcp.ai.AiTestReportSummaryTest.xml" \
  "$UNIT_REPORT_DIR/TEST-com.foggyframework.dataset.mcp.ai.SpringAiTestExecutorTest.xml"

JAVA_HOME="${JAVA_HOME:-/Users/fengjianguang/.jdk/temurin-17/Contents/Home}" \
mvn -pl foggy-dataset-mcp -am -P'!multi-db' \
  -Dtest='AiTestReportSummaryTest,SpringAiTestExecutorTest' \
  -DskipITs=true \
  -Dsurefire.failIfNoSpecifiedTests=false test

for report in \
  "$UNIT_REPORT_DIR/TEST-com.foggyframework.dataset.mcp.ai.AiTestReportSummaryTest.xml" \
  "$UNIT_REPORT_DIR/TEST-com.foggyframework.dataset.mcp.ai.SpringAiTestExecutorTest.xml"; do
  [[ -s "$report" ]] && grep -q '<testcase' "$report" || {
    echo "Expected fresh Surefire report is missing or empty: $report" >&2
    exit 1
  }
done

rm -f "$IT_REPORT"

JAVA_HOME="${JAVA_HOME:-/Users/fengjianguang/.jdk/temurin-17/Contents/Home}" \
AI_TEST_LLM_ENABLED=true \
AI_TEST_OPENAI_API_KEY="$API_KEY" \
AI_TEST_OPENAI_MODEL="$MODEL" \
AI_TEST_OPENAI_BASE_URL="$BASE_URL" \
AI_TEST_CASE_IDS="$CASE_IDS" \
AI_TEST_CATEGORIES="$CATEGORIES" \
AI_TEST_MAX_CASES="$MAX_CASES" \
AI_TEST_LLM_FAIL_ON_MISMATCH="$FAIL_ON_MISMATCH" \
mvn -pl foggy-dataset-mcp -am -P'!multi-db' \
  -Dit.test='AiToolsIT$AiModelCallTest' \
  -DskipUnitTests=true \
  -DskipITs=false \
  -Dfailsafe.failIfNoSpecifiedTests=false verify

[[ -s "$IT_REPORT" ]] && grep -q '<testcase' "$IT_REPORT" || {
  echo "Expected fresh Failsafe report is missing or empty: $IT_REPORT" >&2
  exit 1
}
