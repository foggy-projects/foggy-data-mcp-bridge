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
- Business errors are detected from numeric tool result `code` values where `code != 200`.
- String route codes such as `ROUTING_TERMINAL_CLARIFY` are ignored and are not classified as tool business errors.
- Error details are intentionally concise: source, tool names, sequence, duration, code, exCode, message, and the model-like argument value.

## Acceptance Criteria

- Final-passed LLM cases can expose intermediate business-error tool results.
- `code=200` RX wrappers are not counted as business errors.
- String route codes such as `ROUTING_TERMINAL_CLARIFY` are not counted as business errors.
- Report fields are available at root, per-model, per-case, and case-comparison model levels.
- Existing AI report, validator, and executor tests remain green.

## Constraints And Non-Goals

- Do not change AI case pass/fail semantics in this workitem.
- Do not record full tool result payloads in summaries.
- Do not make non-zero `toolBusinessErrorCount` fail CI until more real LLM samples are collected.
- No UI or product workflow changes are included.

## Progress Tracking

| Dimension | Status | Evidence |
|---|---|---|
| development | complete | Added report aggregation and concise error extraction in `AiTestReportSummary`. |
| testing | complete | Targeted Maven test command passed with 13 tests. |
| experience | N/A | Pure test/report JSON observability change; no UI or user-facing interaction flow. |

## Execution Check-In

Completed work:

- Implemented numeric business-error detection for AI tool call results.
- Added root, model, case, and case-comparison summary counters.
- Added regression tests for `code=600` pass-with-recovery and `code=200` wrapper ignore behavior.
- Recorded this workitem under `docs/9.1.0/workitems/`.

Touched code paths:

- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/AiTestReportSummary.java`
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/AiTestReportSummaryTest.java`
- `docs/9.1.0/workitems/P2-ai-llm-report-tool-business-error-observability-20260604.md`

Self-check:

- Scope implemented as intended: pass.
- Non-goals preserved: pass.
- Code paths listed: pass.
- Basic self-review completed: pass.
- Self-check conclusion: self-check-only.
- Formal quality gate required before coverage audit: no.
- Remaining blockers: none for report support; fresh real LLM repro depends on provider quota and model behavior.

## Validation

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home \
mvn -pl foggy-dataset-mcp -am -P'!multi-db' \
  -Dtest='AiTestReportSummaryTest,ResultValidatorTest,SpringAiTestExecutorTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Result on 2026-06-04: passed, 13 tests.

## Follow-Up

- Re-run a focused real LLM case such as `QUERY-002` when provider quota is available to capture a fresh report with `toolBusinessErrorCount`.
- Treat non-zero `toolBusinessErrorCount` as a report warning, not an immediate case failure, until enough samples show whether recovery behavior correlates with unstable answers.
