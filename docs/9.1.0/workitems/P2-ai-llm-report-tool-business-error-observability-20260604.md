---
doc_role: workitem
doc_purpose: Record AI LLM matrix report observability for intermediate tool business errors.
version: 9.1.0
status: implemented
created_at: 2026-06-04
---

# P2 AI LLM Report Tool Business Error Observability

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track report observability for intermediate business-error tool calls in AI LLM matrix runs.
- source_type: optimization
- priority: P2
- delivery_mode: single-root-delivery

## Background

During the real LLM all-case matrix run, `gpt-oss-120b-medium` passed `QUERY-002` but made intermediate `dataset.describe_model_internal` calls with a nonexistent `ProductInfoModel`. The tool returned an RX-style business error (`code=600`, `exCode=B600`) and the model later recovered with a valid query.

The previous structured report showed the case as passed and listed called tool names, but it did not expose intermediate business-error tool results. This made pass-with-recovery behavior hard to detect from JSON summaries.

## Target Outcome

AI matrix JSON reports should make intermediate tool business errors visible without changing pass/fail semantics. A final-passed case can still be treated as passed, but non-zero business-error counts should be available for warning-level analysis and sample collection.

## Ownership And Code Areas

| Area | Owner | Scope |
|---|---|---|
| AI test report summary | `foggy-dataset-mcp` | `AiTestReportSummary` JSON aggregation fields |
| AI report unit coverage | `foggy-dataset-mcp` | `AiTestReportSummaryTest` regression cases |
| Versioned tracking | docs | This workitem and execution check-in |

## Implementation

- Added `toolBusinessErrorCount`, `toolBusinessErrorCaseCount`, and `toolBusinessErrors` to `matrix-summary.json`.
- Added `toolBusinessErrorCount` and `toolBusinessErrors` to each case summary.
- Added per-model `toolBusinessErrorCount` and `toolBusinessErrorCaseCount`.
- Added per-case-comparison model `toolBusinessErrorCount`.
- Added warning-layer fields: `warningCount`, `warningCaseCount`, `warningCategories`, `warnings`, and `toolBusinessErrorWarningCount`.
- Added per-model `warningCount`, `warningCaseCount`, `warningRate`, and `toolBusinessErrorWarningCount`.
- Updated `scripts/run-ai-llm-matrix.sh` to aggregate warning fields and print warning totals/cases after matrix summary generation.
- Added warning artifacts beside `matrix-summary.json`: `warnings.json` for structured warning summary and `warnings.jsonl` for line-oriented sample collection.
- Added safe local env loading for matrix runs through `AI_TEST_ENV_FILE`, `--env-file FILE`, or the first existing ignored file among `.ai-test.env` and `.env.local`.
- Added `scripts/collect-ai-warning-samples.sh` to aggregate warning samples across matrix runs into `target/ai-warning-samples/`.
- Added `warning-review.md` for human-readable warning review tables grouped by type, model, case, and run.
- Added warning review details for `query_payload_shape_divergence`, including model groups, slice fields, having fields, groupBy, and orderBy.
- Allowed `scripts/collect-ai-warning-samples.sh --source-dir` to point either at the matrix report root or at a single run directory.
- Added fixture-level `tool_argument_rules` so selected AI cases can validate tool argument predicate scope directly, not only observe it as a report warning.
- Added `COMPLEX-001` predicate-scope validation: `salesAmount > 500` must appear in `slice`, and `totalSales > 500` must not appear in `having`.
- Expanded `tool_argument_rules` to stable existing ecommerce cases for detail filters, grouped dimensions, selected measure columns, and complex year/store grouping.
- Enhanced tool-argument validation failure messages with observed payload path summaries so reviewer can see whether the model misplaced a predicate or omitted a grouping field.
- Reused column-reference matching for tool argument rules so expression columns such as `sum(quantity) as totalQuantity` satisfy rules for the underlying measure field.
- Added semantic classification for query payload shape divergence. Alias, orderBy alias, limit/mode, and redundant dimension `$id` grouping differences are now classified as `benign_query_payload_shape_divergence` when slice/having predicates and normalized groupBy semantics match.
- Business errors are detected from numeric tool result `code` values where `code != 200`.
- String route codes such as `ROUTING_TERMINAL_CLARIFY` are ignored and are not classified as tool business errors.
- Error details are intentionally concise: source, tool names, sequence, duration, code, exCode, message, and the model-like argument value.
- Current warning types:
  - `tool_business_error`: numeric tool result code is not `200`.
  - `unknown_model_probe`: a describe-model tool call probes a model-like argument that returned a business error.
  - `empty_tool_result`: a successful tool call record has no result payload.
  - `tool_result_parse_error`: a failed tool call record contains `JSON_PARSE_ERROR`.
  - `tool_call_failure`: a failed tool call record does not match the JSON parse error class.
  - `model_describe_retry`: the same case repeats describe-model calls for the same model argument.
  - `query_payload_shape_divergence`: the same case produced multiple successful `query_model` argument shape signatures across compared models.
  - `benign_query_payload_shape_divergence`: exact payload shapes differ, but semantic signatures match after ignoring alias/orderBy alias/limit/mode differences and redundant dimension `$id` grouping.

## Acceptance Criteria

- Final-passed LLM cases can expose intermediate business-error tool results.
- `code=200` RX wrappers are not counted as business errors.
- String route codes such as `ROUTING_TERMINAL_CLARIFY` are not counted as business errors.
- Report fields are available at root, per-model, per-case, and case-comparison model levels.
- Warning fields are available without changing pass/fail semantics.
- Matrix script terminal output exposes warning totals and warning case IDs.
- Matrix script emits `warnings.json` and `warnings.jsonl` artifacts.
- Matrix script can load ignored local env files without printing secret values.
- Warning samples can be aggregated across historical matrix runs.
- Warning sample review tables are available without custom jq commands.
- Warning sample review tables show query payload shape details for fast triage.
- Query payload shape divergence is visible as warning-level evidence without changing pass/fail semantics.
- Case fixtures can declare expected tool argument shape rules for high-risk semantic predicates.
- Tool argument shape rules support condition-tree predicates and string field lists such as `groupBy`.
- Tool argument shape rules accept expression references to underlying fields where aliases are expected.
- Tool argument failures include observed payload path details for faster triage.
- Query payload shape divergence is classified into semantic versus benign divergence.
- Existing AI report, validator, and executor tests remain green.

## Constraints And Non-Goals

- Do not make warning-only observations fail CI unless a fixture explicitly promotes the behavior into `tool_argument_rules`.
- Do not record full tool result payloads in summaries.
- Do not make non-zero `toolBusinessErrorCount` fail CI until more real LLM samples are collected.
- No UI or product workflow changes are included.

## Progress Tracking

| Dimension | Status | Evidence |
|---|---|---|
| development | complete | Added report aggregation, concise error extraction, warning-layer fields, matrix script warning output, warning artifacts, safe env-file loading, warning sample aggregation/review, warning classification for unknown model probes, tool call anomalies, repeated describe-model calls, query payload shape divergence, benign shape divergence classification, fixture-level tool argument predicate-scope validation, broader stable fixture rules, and observed-path validation diagnostics. |
| testing | complete | Targeted Maven test command passed with 23 tests; shell syntax, env-file print-selection, synthetic warning sample aggregation/review, focused real LLM sample aggregation/review, focused Gemini tool-argument matrix, fixture JSON parsing, and diff checks passed. |
| experience | N/A | Pure test/report JSON observability change; no UI or user-facing interaction flow. |

## Execution Check-In

Completed work:

- Implemented numeric business-error detection for AI tool call results.
- Added root, model, case, and case-comparison summary counters.
- Added warning-layer JSON fields and matrix terminal summary output.
- Added `warnings.json` and `warnings.jsonl` artifact generation for downstream sample collection.
- Added local env file loading for ignored `.ai-test.env`, `.env.local`, explicit `AI_TEST_ENV_FILE`, and `--env-file FILE`.
- Added cross-run warning sample collector with JSONL and summary artifacts.
- Added human-readable `warning-review.md` output for cross-run warning triage.
- Added warning classification for `unknown_model_probe`, `empty_tool_result`, `tool_result_parse_error`, `tool_call_failure`, and `model_describe_retry`.
- Added concise `query_model` argument-shape observability: per-case and case-comparison summaries now expose model, mode, columns, slice fields/ops, having fields/ops, groupBy, orderBy, limit, offset, and a deterministic signature without storing full tool results.
- Added cross-model query payload shape divergence detection. If the same case produces multiple `query_model` shapes, the report marks `queryPayloadShapeConsensus=mixed` and emits a root warning.
- Added `tool_argument_rules` to AI case expectations and validator support for successful tool calls.
- Added `COMPLEX-001` predicate-scope rules so `salesAmount > 500` is required in `slice` and aggregate `having totalSales > 500` is rejected for that intent.
- Added stable fixture-level tool argument rules for `FILTER-001`, `FILTER-002`, `AGG-001`, `AGG-002`, `DIM-001`, `SORT-001`, and additional `COMPLEX-001` year/grouping constraints.
- Added string-node tool argument matching so list-shaped fields like `groupBy: ["product$caption"]` can be validated without artificial condition objects.
- Added expression-aware tool argument matching so measure rules can accept generated columns such as `sum(quantity) as totalQuantity`.
- Added observed payload path summaries to tool argument validation failures, for example showing `slice=[...]` and `having=[...]` in the failed rule.
- Added semantic signatures for query payload shape comparison.
- Added `queryPayloadShapeDivergenceClass` so exact-shape divergence is classified as `benign` when normalized semantic signatures match, and `semantic` when predicates or grouping semantics differ.
- Added `benign_query_payload_shape_divergence` warning type with `severity=info`.
- Added warning review detail expansion for query payload shape divergence.
- Updated warning sample review details so benign shape divergence still shows class and payload-shape details.
- Added single-run source directory support to the warning sample collector.
- Added regression tests for `code=600` pass-with-recovery and `code=200` wrapper ignore behavior.
- Added regression coverage for repeated describe-model calls.
- Added regression coverage for empty tool results, JSON parse failures, and ordinary tool call failures.
- Added regression coverage for `slice salesAmount > 500` versus `having totalSales > 500` payload-shape divergence.
- Added validator regression coverage for accepting the expected detail-row `slice` predicate and rejecting the same intent moved to aggregate `having`.
- Added validator regression coverage for `groupBy` string field matching and observed payload path diagnostics on failed tool argument rules.
- Added validator regression coverage for expression column references in tool argument rules.
- Added report regression coverage for semantic versus benign query payload shape divergence classification.
- Recorded this workitem under `docs/9.1.0/workitems/`.

Touched code paths:

- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/AiTestReportSummary.java`
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/AiTestReportSummaryTest.java`
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/EcommerceTestCase.java`
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/ResultValidator.java`
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/ResultValidatorTest.java`
- `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
- `scripts/run-ai-llm-matrix.sh`
- `scripts/collect-ai-warning-samples.sh`
- `docs/9.1.0/workitems/P2-ai-llm-report-tool-business-error-observability-20260604.md`

Self-check:

- Scope implemented as intended: pass.
- Non-goals preserved: pass.
- Code paths listed: pass.
- Basic self-review completed: pass.
- Self-check conclusion: self-check-only.
- Formal quality gate required before coverage audit: no.
- Remaining blockers: none for report support; focused real LLM rerun completed with local temporary ignored credential injection.

## Validation

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home \
mvn -pl foggy-dataset-mcp -am -P'!multi-db' \
  -Dtest='AiTestReportSummaryTest,ResultValidatorTest,SpringAiTestExecutorTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Result on 2026-06-04: passed, 13 tests.

Updated targeted result on 2026-06-04 after warning artifact and env-file changes: passed, 14 tests.

Updated targeted result on 2026-06-04 after tool call anomaly warning changes: passed, 16 tests.

Updated targeted result on 2026-06-04 after query payload shape observability changes: passed, 17 tests.

Updated targeted result on 2026-06-04 after fixture-level predicate-scope validation: passed, 19 tests.

Updated targeted result on 2026-06-04 after broader stable fixture rules, expression-aware matching, and observed-path diagnostics: passed, 22 tests.

Updated targeted result on 2026-06-04 after benign query payload shape divergence classification: passed, 23 tests.

Additional checks on 2026-06-04:

- `jq empty foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`: passed.
- `bash -n scripts/run-ai-llm-matrix.sh`: passed.
- `scripts/run-ai-llm-matrix.sh --env-file <temp-file> --print-selection`: passed; selection JSON stayed parseable and no secret values were printed.
- `scripts/collect-ai-warning-samples.sh` with synthetic warning inputs and `warning-review.md` generation: passed.
- `scripts/collect-ai-warning-samples.sh --source-dir <single-run-dir>` with focused real LLM warning inputs: passed; `warning-review.md` showed query payload shape `Details`.
- `scripts/collect-ai-warning-samples.sh` against local historical target reports: passed; current old reports contained zero warning samples and produced an empty review table.
- `git diff --check`: passed.
- Synthetic jq aggregation for `matrix-summary.json` warning fields and terminal warning output: passed.
- First focused real LLM attempt using the existing shell credential failed model calls with `HTTP 401 Invalid API key`; rerun with the latest temporary credential override passed. No credential was written to repository files.
- Parent workspace `/Users/fengjianguang/foggy-projects/foggy-data-mcp/.env.local` contains generic `OPENAI_*` settings. The focused Gemini matrix mapped them to `AI_TEST_OPENAI_*` only in the shell process and stripped a trailing `/v1` from the base URL; no credential was written to repository files or docs.

Focused real LLM evidence on 2026-06-04:

```bash
scripts/run-ai-llm-matrix.sh \
  --models gpt-oss-120b-medium,gemini-3-flash \
  --case-ids QUERY-002 \
  --continue-on-error \
  --run-id focused-warning-query002-20260604
scripts/collect-ai-warning-samples.sh
```

Result:

- Run ID: `focused-warning-query002-20260604`.
- Models: `gpt-oss-120b-medium`, `gemini-3-flash`.
- Case ID: `QUERY-002`.
- Matrix result count: 4, passed: 4, failed: 0.
- Warning count: 2, warning cases: 1.
- Warning categories: `tool_business_error=1`, `unknown_model_probe=1`.
- Tool business error count: 1.
- Warning case: `QUERY-002:spring-ai/gpt-oss-120b-medium`.
- `spring-ai/gpt-oss-120b-medium` probed nonexistent `ProductInfoModel` through `dataset.describe_model_internal` and recovered.
- `spring-ai/gemini-3-flash` completed the same case with zero warnings.

Focused query-mix evidence on 2026-06-04:

```bash
scripts/run-ai-llm-matrix.sh \
  --models gpt-oss-120b-medium,gemini-3-flash \
  --case-ids FILTER-001,AGG-001,SORT-001,COMPLEX-001 \
  --continue-on-error \
  --run-id focused-warning-query-mix-20260604
scripts/collect-ai-warning-samples.sh
```

Result before query payload shape observability:

- Run ID: `focused-warning-query-mix-20260604`.
- Matrix result count: 16, passed: 16, failed: 0.
- Warning count: 0.
- Manual log review found a report blind spot in `COMPLEX-001`: one model placed `salesAmount > 500` in detail-row `slice`, while another placed `totalSales > 500` in aggregate `having`.
- The validator and report passed both paths, so query payload shape comparison was added as warning-level observability rather than pass/fail semantics.

Focused predicate-scope validation evidence on 2026-06-04:

```bash
scripts/run-ai-llm-matrix.sh \
  --models gemini-pro-agent,gemini-3-flash \
  --case-ids COMPLEX-001 \
  --run-id focused-predicate-scope-complex001-latest-key-20260604
scripts/collect-ai-warning-samples.sh \
  --source-dir foggy-dataset-mcp/target/ai-test-reports/focused-predicate-scope-complex001-latest-key-20260604 \
  --output-dir foggy-dataset-mcp/target/ai-warning-review-focused-predicate-scope-complex001-latest-key-20260604
```

Result:

- Run ID: `focused-predicate-scope-complex001-latest-key-20260604`.
- Models: `gemini-pro-agent`, `gemini-3-flash`.
- Case ID: `COMPLEX-001`.
- Matrix result count: 4, passed: 4, failed: 0.
- Warning count: 2, warning cases: 1.
- Warning categories: `query_payload_shape_divergence=2`.
- Tool business error count: 0.
- Both Gemini models placed `salesAmount > 500` in `slice`, satisfying the new fixture-level predicate-scope rules.
- Warning review details showed direct baseline versus Gemini payload shape differences, including slice, groupBy, and orderBy fields.

Focused tool-argument fixture rule evidence on 2026-06-04:

```bash
bash -lc 'set -euo pipefail; set -a; source /Users/fengjianguang/foggy-projects/foggy-data-mcp/.env.local; set +a; base="${OPENAI_BASE_URL%/}"; base="${base%/v1}"; export JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home; export PATH="$JAVA_HOME/bin:$PATH"; export AI_TEST_OPENAI_API_KEY="$OPENAI_API_KEY"; export AI_TEST_OPENAI_BASE_URL="$base"; scripts/run-ai-llm-matrix.sh --models gemini-pro-agent,gemini-3-flash --base-url "$AI_TEST_OPENAI_BASE_URL" --case-ids FILTER-001,FILTER-002,AGG-001,AGG-002,DIM-001,SORT-001,COMPLEX-001 --continue-on-error --run-id focused-tool-argument-rules-gemini-java17-20260604'
scripts/collect-ai-warning-samples.sh \
  --source-dir foggy-dataset-mcp/target/ai-test-reports/focused-tool-argument-rules-gemini-java17-20260604 \
  --output-dir foggy-dataset-mcp/target/ai-warning-review-focused-tool-argument-rules-gemini-java17-20260604
```

Result:

- Run ID: `focused-tool-argument-rules-gemini-java17-20260604`.
- Models: `gemini-pro-agent`, `gemini-3-flash`.
- Case IDs: `FILTER-001`, `FILTER-002`, `AGG-001`, `AGG-002`, `DIM-001`, `SORT-001`, `COMPLEX-001`.
- Matrix result count: 28, passed: 28, failed: 0.
- Per-model LLM result: `gemini-pro-agent` 7/7 passed; `gemini-3-flash` 7/7 passed.
- Direct baseline result: 14/14 passed across the two model passes.
- Warning count: 12, warning cases: 7.
- Warning categories: `query_payload_shape_divergence=12`.
- Tool business error count: 0.
- New fixture-level `tool_argument_rules` passed for both Gemini models on all selected cases.
- Warning review details showed expected benign shape variation such as extra ID grouping, alias/orderBy differences, chart export versus query tool choice, and direct baseline versus LLM limit/default differences.

Focused benign query payload shape classification evidence on 2026-06-04:

```bash
bash -lc 'set -euo pipefail; set -a; source /Users/fengjianguang/foggy-projects/foggy-data-mcp/.env.local; set +a; base="${OPENAI_BASE_URL%/}"; base="${base%/v1}"; export JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home; export PATH="$JAVA_HOME/bin:$PATH"; export AI_TEST_OPENAI_API_KEY="$OPENAI_API_KEY"; export AI_TEST_OPENAI_BASE_URL="$base"; scripts/run-ai-llm-matrix.sh --models gemini-pro-agent,gemini-3-flash --base-url "$AI_TEST_OPENAI_BASE_URL" --case-ids FILTER-001,FILTER-002,AGG-001,AGG-002,DIM-001,SORT-001,COMPLEX-001 --continue-on-error --run-id focused-tool-argument-rules-benign-classification-gemini-20260604'
scripts/collect-ai-warning-samples.sh \
  --source-dir foggy-dataset-mcp/target/ai-test-reports/focused-tool-argument-rules-benign-classification-gemini-20260604 \
  --output-dir foggy-dataset-mcp/target/ai-warning-review-focused-tool-argument-rules-benign-classification-gemini-20260604
```

Result:

- Run ID: `focused-tool-argument-rules-benign-classification-gemini-20260604`.
- Models: `gemini-pro-agent`, `gemini-3-flash`.
- Case IDs: `FILTER-001`, `FILTER-002`, `AGG-001`, `AGG-002`, `DIM-001`, `SORT-001`, `COMPLEX-001`.
- Matrix result count: 28, passed: 28, failed: 0.
- Per-model LLM result: `gemini-pro-agent` 7/7 passed; `gemini-3-flash` 7/7 passed.
- Direct baseline result: 14/14 passed across the two model passes.
- Warning count: 12, warning cases: 7.
- Warning categories: `benign_query_payload_shape_divergence=9`, `query_payload_shape_divergence=3`.
- Tool business error count: 0.
- Benign classification covered alias/orderBy/limit/mode variation, redundant dimension `$id` grouping, and chart-export-versus-query shape variation where normalized semantic signatures matched.
- Semantic warnings remained for cases whose normalized predicates or grouping semantics still differed, for example `FILTER-002` and `COMPLEX-001`.

Artifacts:

- `foggy-dataset-mcp/target/ai-test-reports/focused-warning-query002-20260604/matrix-summary.json`
- `foggy-dataset-mcp/target/ai-test-reports/focused-warning-query002-20260604/warnings.json`
- `foggy-dataset-mcp/target/ai-test-reports/focused-warning-query002-20260604/warnings.jsonl`
- `foggy-dataset-mcp/target/ai-warning-samples/warning-summary.json`
- `foggy-dataset-mcp/target/ai-warning-samples/warning-review.md`
- `foggy-dataset-mcp/target/ai-test-reports/focused-predicate-scope-complex001-latest-key-20260604/matrix-summary.json`
- `foggy-dataset-mcp/target/ai-test-reports/focused-predicate-scope-complex001-latest-key-20260604/warnings.json`
- `foggy-dataset-mcp/target/ai-test-reports/focused-predicate-scope-complex001-latest-key-20260604/warnings.jsonl`
- `foggy-dataset-mcp/target/ai-warning-review-focused-predicate-scope-complex001-latest-key-20260604/warning-review.md`
- `foggy-dataset-mcp/target/ai-test-reports/focused-tool-argument-rules-gemini-java17-20260604/matrix-summary.json`
- `foggy-dataset-mcp/target/ai-test-reports/focused-tool-argument-rules-gemini-java17-20260604/warnings.json`
- `foggy-dataset-mcp/target/ai-test-reports/focused-tool-argument-rules-gemini-java17-20260604/warnings.jsonl`
- `foggy-dataset-mcp/target/ai-warning-review-focused-tool-argument-rules-gemini-java17-20260604/warning-review.md`
- `foggy-dataset-mcp/target/ai-test-reports/focused-tool-argument-rules-benign-classification-gemini-20260604/matrix-summary.json`
- `foggy-dataset-mcp/target/ai-test-reports/focused-tool-argument-rules-benign-classification-gemini-20260604/warnings.json`
- `foggy-dataset-mcp/target/ai-test-reports/focused-tool-argument-rules-benign-classification-gemini-20260604/warnings.jsonl`
- `foggy-dataset-mcp/target/ai-warning-review-focused-tool-argument-rules-benign-classification-gemini-20260604/warning-review.md`

## Follow-Up

- Continue collecting focused real LLM warning samples, especially cases where final pass masks intermediate model/tool recovery behavior.
- Run `scripts/collect-ai-warning-samples.sh` after real LLM runs to maintain local aggregate samples by warning type, model, case, and run.
- Promote repeated semantic shape divergences into additional fixture rules only when the expected semantics are unambiguous.
- Treat non-zero `warningCount` as warning-level evidence, not an immediate case failure, until enough samples show whether recovery behavior correlates with unstable answers.
