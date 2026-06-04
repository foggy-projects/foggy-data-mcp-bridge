#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SOURCE_DIR="foggy-dataset-mcp/target/ai-test-reports"
OUTPUT_DIR="foggy-dataset-mcp/target/ai-warning-samples"

usage() {
  cat <<'USAGE'
Usage: scripts/collect-ai-warning-samples.sh [options]

Collects warning samples from AI LLM matrix report directories.

Defaults:
  source-dir: foggy-dataset-mcp/target/ai-test-reports
  output-dir: foggy-dataset-mcp/target/ai-warning-samples

Options:
  --source-dir DIR          Matrix report root containing <run-id>/ directories.
  --output-dir DIR          Directory for aggregate warning artifacts.
  -h, --help                Show this help.

Outputs:
  warnings.all.jsonl        One compact warning JSON object per line.
  warning-summary.json      Aggregated warning counts by type, model, case, run.
  warning-review.md         Human-readable warning review table.
  README.md                 Short local artifact note.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source-dir)
      shift
      SOURCE_DIR="${1:-}"
      ;;
    --output-dir)
      shift
      OUTPUT_DIR="${1:-}"
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

if [[ -z "$SOURCE_DIR" || -z "$OUTPUT_DIR" ]]; then
  echo "Both --source-dir and --output-dir are required when provided." >&2
  exit 2
fi

cd "$REPO_ROOT"

if [[ ! -d "$SOURCE_DIR" ]]; then
  echo "AI matrix source directory does not exist: $SOURCE_DIR" >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"
ALL_WARNINGS="$OUTPUT_DIR/warnings.all.jsonl"
SUMMARY_JSON="$OUTPUT_DIR/warning-summary.json"
REVIEW_MD="$OUTPUT_DIR/warning-review.md"
README_FILE="$OUTPUT_DIR/README.md"
: > "$ALL_WARNINGS"

while IFS= read -r -d '' run_dir; do
  run_id="$(basename "$run_dir")"
  warnings_jsonl="$run_dir/warnings.jsonl"
  matrix_summary="$run_dir/matrix-summary.json"

  if [[ -s "$warnings_jsonl" ]]; then
    jq -c \
      --arg runId "$run_id" \
      --arg sourceFile "$warnings_jsonl" \
      '. + {
        runId: (.runId // $runId),
        sourceFile: $sourceFile
      }' "$warnings_jsonl" >> "$ALL_WARNINGS"
  elif [[ -f "$matrix_summary" ]]; then
    jq -c \
      --arg runId "$run_id" \
      --arg sourceFile "$matrix_summary" \
      '. as $root
       | $root.warnings[]?
       | . + {
           runId: (.runId // $root.runId // $runId),
           sourceFile: $sourceFile
         }' "$matrix_summary" >> "$ALL_WARNINGS"
  fi
done < <(find "$SOURCE_DIR" -mindepth 1 -maxdepth 1 -type d -print0 | sort -z)

jq -s \
  --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg sourceDir "$SOURCE_DIR" \
  --arg outputDir "$OUTPUT_DIR" \
  '
    def count_by_key(f):
      reduce .[] as $item ({};
        ($item | f) as $key
        | .[$key] = ((.[$key] // 0) + 1)
      );

    {
      generatedAt: $generatedAt,
      sourceDir: $sourceDir,
      outputDir: $outputDir,
      warningCount: length,
      warningRunCount: ([.[].runId // ""] | map(select(. != "")) | unique | length),
      warningCaseCount: (
        [.[]
          | "\(.testCaseId // "")|\(.provider // "")|\(.modelName // "")"]
        | unique
        | length
      ),
      warningCategories: count_by_key(.warningType // "unknown"),
      modelWarningCounts: count_by_key("\(.provider // "?")/\(.modelName // "?")"),
      caseWarningCounts: count_by_key(.testCaseId // "unknown"),
      runs: (
        sort_by(.runId // "")
        | group_by(.runId // "")
        | map({
            runId: (.[0].runId // ""),
            warningCount: length,
            warningCategories: (
              reduce .[] as $item ({};
                ($item.warningType // "unknown") as $key
                | .[$key] = ((.[$key] // 0) + 1)
              )
            ),
            models: ([.[]
              | "\(.provider // "?")/\(.modelName // "?")"]
              | unique),
            cases: ([.[]
              | {
                  testCaseId: (.testCaseId // ""),
                  provider: (.provider // ""),
                  modelName: (.modelName // ""),
                  warningType: (.warningType // "unknown"),
                  argumentModel: (.argumentModel // null)
                }]
              | sort_by([.testCaseId, .provider, .modelName, .warningType, (.argumentModel // "")]))
          })
      )
    }
  ' "$ALL_WARNINGS" > "$SUMMARY_JSON"

jq -r '
  def count_rows($object):
    ($object // {})
    | to_entries
    | sort_by(.value, .key)
    | reverse
    | .[];

  "# AI Warning Review",
  "",
  "Generated at: `\(.generatedAt)`",
  "",
  "Source directory: `\(.sourceDir)`",
  "",
  "## Totals",
  "",
  "| Metric | Count |",
  "|---|---:|",
  "| Warnings | \(.warningCount) |",
  "| Runs | \(.warningRunCount) |",
  "| Cases | \(.warningCaseCount) |",
  "",
  "## Warning Types",
  "",
  "| Warning Type | Count |",
  "|---|---:|",
  (if ((.warningCategories // {}) | length) == 0 then
    "| none | 0 |"
  else
    count_rows(.warningCategories) | "| \(.key) | \(.value) |"
  end),
  "",
  "## Models",
  "",
  "| Model | Count |",
  "|---|---:|",
  (if ((.modelWarningCounts // {}) | length) == 0 then
    "| none | 0 |"
  else
    count_rows(.modelWarningCounts) | "| \(.key) | \(.value) |"
  end),
  "",
  "## Cases",
  "",
  "| Case | Count |",
  "|---|---:|",
  (if ((.caseWarningCounts // {}) | length) == 0 then
    "| none | 0 |"
  else
    count_rows(.caseWarningCounts) | "| \(.key) | \(.value) |"
  end),
  "",
  "## Runs",
  "",
  "| Run | Count | Types |",
  "|---|---:|---|",
  (if ((.runs // []) | length) == 0 then
    "| none | 0 |  |"
  else
    (.runs | sort_by(.warningCount, .runId) | reverse)[]
    | "| \(.runId) | \(.warningCount) | \((.warningCategories // {}) | to_entries | sort_by(.key) | map("\(.key)=\(.value)") | join(", ")) |"
  end),
  "",
  "## Samples",
  "",
  "| Run | Case | Model | Type | Argument Model |",
  "|---|---|---|---|---|",
  ([
    (.runs // [])[]
    | . as $run
    | (.cases // [])[]
    | {
        runId: $run.runId,
        testCaseId: .testCaseId,
        model: ((.provider // "") + "/" + (.modelName // "")),
        warningType: .warningType,
        argumentModel: (.argumentModel // "")
      }
    ]
    | sort_by(.runId, .testCaseId, .model, .warningType, .argumentModel)
    | .[:50]
    | if length == 0 then
        ["| none |  |  |  |  |"]
      else
        map("| \(.runId) | \(.testCaseId) | \(.model) | \(.warningType) | \(.argumentModel) |")
      end
    | .[])
' "$SUMMARY_JSON" > "$REVIEW_MD"

{
  printf '# AI Warning Samples\n\n'
  printf 'Generated at: `%s`\n\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'Source directory: `%s`\n\n' "$SOURCE_DIR"
  printf 'Artifacts:\n\n'
  printf '%s\n' '- `warnings.all.jsonl`: one warning JSON object per line.'
  printf '%s\n' '- `warning-summary.json`: warning counts grouped by type, model, case, and run.'
  printf '%s\n' '- `warning-review.md`: human-readable warning review tables.'
} > "$README_FILE"

echo "[ai-warning-samples] warnings=$ALL_WARNINGS"
echo "[ai-warning-samples] summary=$SUMMARY_JSON"
echo "[ai-warning-samples] review=$REVIEW_MD"
jq -r '
  "[ai-warning-samples] totals warnings=\(.warningCount) runs=\(.warningRunCount) cases=\(.warningCaseCount)",
  (if (.warningCount // 0) == 0 then
    "[ai-warning-samples] warningCategories=<none>"
  else
    "[ai-warning-samples] warningCategories=" + (.warningCategories | to_entries | map("\(.key)=\(.value)") | join(","))
  end)
' "$SUMMARY_JSON"
