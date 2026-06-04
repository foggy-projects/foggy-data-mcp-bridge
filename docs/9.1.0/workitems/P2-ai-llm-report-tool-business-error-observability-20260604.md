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
- Existing AI report, validator, and executor tests remain green.

## Constraints And Non-Goals

- Do not change AI case pass/fail semantics in this workitem.
- Do not record full tool result payloads in summaries.
- Do not make non-zero `toolBusinessErrorCount` fail CI until more real LLM samples are collected.
- No UI or product workflow changes are included.

## Progress Tracking

| Dimension | Status | Evidence |
|---|---|---|
| development | complete | Added report aggregation, concise error extraction, warning-layer fields, matrix script warning output, warning artifacts, safe env-file loading, warning sample aggregation, and warning classification for unknown model probes, tool call anomalies, and repeated describe-model calls. |
| testing | complete | Targeted Maven test command passed with 16 tests; shell syntax, env-file print-selection, synthetic warning sample aggregation, local historical report aggregation, and diff checks passed. |
| experience | N/A | Pure test/report JSON observability change; no UI or user-facing interaction flow. |

## Execution Check-In

Completed work:

- Implemented numeric business-error detection for AI tool call results.
- Added root, model, case, and case-comparison summary counters.
- Added warning-layer JSON fields and matrix terminal summary output.
- Added `warnings.json` and `warnings.jsonl` artifact generation for downstream sample collection.
- Added local env file loading for ignored `.ai-test.env`, `.env.local`, explicit `AI_TEST_ENV_FILE`, and `--env-file FILE`.
- Added cross-run warning sample collector with JSONL and summary artifacts.
- Added warning classification for `unknown_model_probe`, `empty_tool_result`, `tool_result_parse_error`, `tool_call_failure`, and `model_describe_retry`.
- Added regression tests for `code=600` pass-with-recovery and `code=200` wrapper ignore behavior.
- Added regression coverage for repeated describe-model calls.
- Added regression coverage for empty tool results, JSON parse failures, and ordinary tool call failures.
- Recorded this workitem under `docs/9.1.0/workitems/`.

Touched code paths:

- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/AiTestReportSummary.java`
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/AiTestReportSummaryTest.java`
- `scripts/run-ai-llm-matrix.sh`
- `docs/9.1.0/workitems/P2-ai-llm-report-tool-business-error-observability-20260604.md`

Self-check:

- Scope implemented as intended: pass.
- Non-goals preserved: pass.
- Code paths listed: pass.
- Basic self-review completed: pass.
- Self-check conclusion: self-check-only.
- Formal quality gate required before coverage audit: no.
- Remaining blockers: none for report support; focused real LLM rerun was not executed in this environment because `AI_TEST_OPENAI_API_KEY` is not injected.

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

Additional checks on 2026-06-04:

- `bash -n scripts/run-ai-llm-matrix.sh`: passed.
- `scripts/run-ai-llm-matrix.sh --env-file <temp-file> --print-selection`: passed; selection JSON stayed parseable and no secret values were printed.
- `scripts/collect-ai-warning-samples.sh` with synthetic warning inputs: passed.
- `scripts/collect-ai-warning-samples.sh` against local historical target reports: passed; current old reports contained zero warning samples.
- `git diff --check`: passed.
- Synthetic jq aggregation for `matrix-summary.json` warning fields and terminal warning output: passed.

## Follow-Up

- Re-run a focused real LLM case such as `QUERY-002` after injecting `AI_TEST_OPENAI_API_KEY` through an ignored local env file to capture a fresh report with `warningCount`, `toolBusinessErrorCount`, `warnings.json`, and `warnings.jsonl`.
- Run `scripts/collect-ai-warning-samples.sh` after real LLM runs to maintain local aggregate samples by warning type, model, case, and run.
- Treat non-zero `warningCount` as warning-level evidence, not an immediate case failure, until enough samples show whether recovery behavior correlates with unstable answers.
