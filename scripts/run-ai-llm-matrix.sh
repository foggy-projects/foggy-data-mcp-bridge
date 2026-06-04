#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

MODELS="${AI_TEST_OPENAI_MODELS:-gemini-pro-agent,gemini-3-flash}"
BASE_URL="${AI_TEST_OPENAI_BASE_URL:-https://codex2.qlfloor.com:7443}"
PROFILE="${AI_TEST_MATRIX_PROFILE:-smoke}"
CASE_IDS="${AI_TEST_CASE_IDS:-}"
CATEGORIES="${AI_TEST_CATEGORIES:-}"
MAX_CASES="${AI_TEST_MAX_CASES:-0}"
FAIL_ON_MISMATCH="${AI_TEST_LLM_FAIL_ON_MISMATCH:-false}"
CONTINUE_ON_ERROR=0
PRINT_SELECTION=0
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
TEST_CASE_FILE="foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json"

usage() {
  cat <<'USAGE'
Usage: scripts/run-ai-llm-matrix.sh [options]

Runs the opt-in AI question-bank comparison across multiple LLM models.
Each model writes a standalone snapshot under:
  foggy-dataset-mcp/target/ai-test-reports/<run-id>/

Environment:
  AI_TEST_OPENAI_API_KEY    Required by scripts/run-ai-llm-comparison.sh.
  AI_TEST_OPENAI_MODELS     Default: gemini-pro-agent,gemini-3-flash
  AI_TEST_OPENAI_BASE_URL   Default: https://codex2.qlfloor.com:7443
                            For Spring AI, do not append /v1 here.
  AI_TEST_MATRIX_PROFILE    Default: smoke. One of: smoke, broad, all.
  AI_TEST_CASE_IDS          Optional comma-separated case IDs.
  AI_TEST_CATEGORIES        Optional comma-separated categories.
  AI_TEST_MAX_CASES         Optional maximum selected cases.

Options:
  --models NAMES            Comma-separated model names.
  --base-url URL            Override AI_TEST_OPENAI_BASE_URL. Do not append /v1.
  --profile NAME            Case selection profile: smoke, broad, all.
  --case-ids IDS            Comma-separated case IDs.
  --categories NAMES        Comma-separated category names.
  --max-cases N             Limit selected cases.
  --fail-on-mismatch        Fail when any selected LLM case fails validation.
  --continue-on-error       Continue with the next model after a runner error.
  --print-selection         Print resolved case selection without running tests.
  --run-id ID               Override report directory name.
  -h, --help                Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --models)
      shift
      MODELS="${1:-}"
      ;;
    --base-url)
      shift
      BASE_URL="${1:-}"
      ;;
    --profile)
      shift
      PROFILE="${1:-}"
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
    --continue-on-error)
      CONTINUE_ON_ERROR=1
      ;;
    --print-selection)
      PRINT_SELECTION=1
      ;;
    --run-id)
      shift
      RUN_ID="${1:-}"
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

if [[ -z "$MODELS" ]]; then
  echo "At least one model is required." >&2
  exit 1
fi
if [[ "$BASE_URL" == */v1 ]]; then
  echo "Spring AI base URL should not include /v1: $BASE_URL" >&2
  exit 1
fi

cd "$REPO_ROOT"

case "$PROFILE" in
  smoke|broad|all)
    ;;
  *)
    echo "Unknown AI test matrix profile: $PROFILE" >&2
    exit 2
    ;;
esac

if [[ -z "$CASE_IDS" && -z "$CATEGORIES" ]]; then
  case "$PROFILE" in
    smoke)
      CASE_IDS="META-001,QUERY-001"
      ;;
    broad)
      CASE_IDS="$(
        jq -r '
          reduce (.testCases[] | select(.enabled != false)) as $case ({seen: {}, ids: []};
            if .seen[$case.category] then
              .
            else
              .seen[$case.category] = true | .ids += [$case.id]
            end
          )
          | .ids
          | join(",")
        ' "$TEST_CASE_FILE"
      )"
      ;;
    all)
      CASE_IDS=""
      ;;
  esac
fi

if [[ "$PRINT_SELECTION" -eq 1 ]]; then
  jq -n \
    --arg profile "$PROFILE" \
    --arg caseIds "$CASE_IDS" \
    --arg categories "$CATEGORIES" \
    --arg maxCases "$MAX_CASES" \
    '{
      profile: $profile,
      caseIds: $caseIds,
      categories: $categories,
      maxCases: ($maxCases | tonumber)
    }'
  exit 0
fi

scripts/ensure-ai-test-mysql.sh

REPORT_DIR="foggy-dataset-mcp/target/ai-test-reports/$RUN_ID"
mkdir -p "$REPORT_DIR"

IFS=',' read -r -a MODEL_ARRAY <<< "$MODELS"

echo "[ai-matrix] runId=$RUN_ID"
echo "[ai-matrix] models=$MODELS"
echo "[ai-matrix] baseUrl=$BASE_URL"
echo "[ai-matrix] profile=$PROFILE"
echo "[ai-matrix] caseIds=${CASE_IDS:-<all>}"
echo "[ai-matrix] categories=${CATEGORIES:-<all>}"
echo "[ai-matrix] maxCases=$MAX_CASES"
echo "[ai-matrix] failOnMismatch=$FAIL_ON_MISMATCH"
echo "[ai-matrix] reportDir=$REPORT_DIR"

failed_models=()

for raw_model in "${MODEL_ARRAY[@]}"; do
  model="$(echo "$raw_model" | xargs)"
  if [[ -z "$model" ]]; then
    continue
  fi

  safe_model="$(printf '%s' "$model" | tr -c '[:alnum:]_.-' '_')"
  report_file="$REPORT_DIR/${safe_model}.json"
  runner_args=(
    --skip-db-check
    --model "$model"
    --base-url "$BASE_URL"
    --case-ids "$CASE_IDS"
    --categories "$CATEGORIES"
    --max-cases "$MAX_CASES"
  )
  if [[ "$FAIL_ON_MISMATCH" == "true" ]]; then
    runner_args+=(--fail-on-mismatch)
  fi

  echo "[ai-matrix] running model=$model"
  if scripts/run-ai-llm-comparison.sh "${runner_args[@]}"; then
    cp foggy-dataset-mcp/target/ai-test-report-summary.json "$report_file"
    echo "[ai-matrix] report=$report_file"
  else
    status=$?
    failed_models+=("$model:$status")
    if [[ -f foggy-dataset-mcp/target/ai-test-report-summary.json ]]; then
      cp foggy-dataset-mcp/target/ai-test-report-summary.json "$report_file"
      echo "[ai-matrix] report=$report_file"
    fi
    echo "[ai-matrix] model failed: $model status=$status" >&2
    if [[ "$CONTINUE_ON_ERROR" -eq 0 ]]; then
      exit "$status"
    fi
  fi
done

report_files=()
for file in "$REPORT_DIR"/*.json; do
  [[ -e "$file" ]] || continue
  [[ "$(basename "$file")" == "matrix-summary.json" ]] && continue
  report_files+=("$file")
done

if [[ "${#report_files[@]}" -gt 0 ]]; then
  jq -s \
    --arg runId "$RUN_ID" \
    --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg profile "$PROFILE" \
    --arg caseIds "$CASE_IDS" \
    --arg categories "$CATEGORIES" \
    --arg maxCases "$MAX_CASES" \
  '
    {
      runId: $runId,
      generatedAt: $generatedAt,
      selection: {
        profile: $profile,
        caseIds: $caseIds,
        categories: $categories,
        maxCases: ($maxCases | tonumber)
      },
      reportCount: length,
      resultCount: ([.[].resultCount] | add // 0),
      passedCount: ([.[].passedCount] | add // 0),
      failedCount: ([.[].failedCount] | add // 0),
      warningCount: ([.[].warningCount? // 0] | add // 0),
      warningCaseCount: (
        [.[].warnings[]? | "\(.testCaseId // "")|\(.provider // "")|\(.modelName // "")"]
        | unique
        | length
      ),
      toolBusinessErrorCount: ([.[].toolBusinessErrorCount? // 0] | add // 0),
      toolBusinessErrorWarningCount: ([.[].toolBusinessErrorWarningCount? // 0] | add // 0),
      directBaseline: ([.[].models[]? | select(.model == "direct/tool-execution")] as $direct | {
        model: "direct/tool-execution",
        reportCount: ($direct | length),
        resultCount: ($direct | map(.resultCount) | add // 0),
        passedCount: ($direct | map(.passedCount) | add // 0),
        failedCount: ($direct | map(.failedCount) | add // 0),
        avgDurationMs: (if ($direct | length) == 0 then 0 else (($direct | map(.avgDurationMs) | add // 0) / ($direct | length)) end)
      }),
      llmModels: ([.[].models[]? | select(.model != "direct/tool-execution")] | sort_by(.model)),
      models: ([.[].models[]?] | sort_by(.model)),
      failureCategories: (
        reduce .[].failureCategories? as $categories ({};
          reduce ($categories | to_entries[]) as $entry (.;
            .[$entry.key] = ((.[$entry.key] // 0) + $entry.value)
          )
        )
      ),
      warningCategories: (
        reduce .[].warningCategories? as $categories ({};
          reduce ($categories | to_entries[]) as $entry (.;
            .[$entry.key] = ((.[$entry.key] // 0) + $entry.value)
          )
        )
      ),
      warnings: ([.[].warnings[]?]),
      cases: ([.[].cases[]? | {
        testCaseId,
        provider,
        modelName,
        success,
        durationMs,
        errorCategory,
        calledTools,
        warningCount: (.warningCount // 0),
        toolBusinessErrorCount: (.toolBusinessErrorCount // 0),
        warningTypes: ([.warnings[]?.warningType] | unique)
      }])
    }
  ' "${report_files[@]}" > "$REPORT_DIR/matrix-summary.json"
  echo "[ai-matrix] summary=$REPORT_DIR/matrix-summary.json"
  jq -r '
    "[ai-matrix] totals resultCount=\(.resultCount) passed=\(.passedCount) failed=\(.failedCount) warnings=\(.warningCount) toolBusinessErrors=\(.toolBusinessErrorCount)",
    (if (.warningCount // 0) == 0 then
      "[ai-matrix] warningCases=<none>"
    else
      "[ai-matrix] warningCases=" + ([.warnings[]? | "\(.testCaseId // "?"):\(.provider // "?")/\(.modelName // "?")"] | unique | join(","))
    end),
    (.warnings[]? | "[ai-matrix] warning type=\(.warningType // "unknown") case=\(.testCaseId // "?") model=\(.provider // "?")/\(.modelName // "?") tool=\(.toolName // "?") code=\(.code // "?") argumentModel=\(.argumentModel // "-")")
  ' "$REPORT_DIR/matrix-summary.json"
fi

if [[ "${#failed_models[@]}" -gt 0 ]]; then
  printf '[ai-matrix] failedModels=%s\n' "${failed_models[*]}" >&2
  exit 1
fi
