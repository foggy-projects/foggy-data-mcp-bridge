---
doc_role: workitem
doc_purpose: Track AI route-intent fixture hardening for query model selection.
version: 9.1.0
status: implemented
created_at: 2026-06-04
updated_at: 2026-06-04
---

# P2 AI Route Intent Fixtures

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Record query-model route intent validation and fixture expansion for AI test cases.
- source_type: optimization
- priority: P2
- delivery_mode: single-root-delivery

## Background

AI LLM matrix samples showed that prompt tuning alone cannot guarantee stable model selection across providers. Some questions are semantically close but must route to different query models, such as sales purchase detail versus order backlog or order payable amount analysis.

Existing fixtures already had `target_model`, but validator evidence mainly checked successful tool usage and payload/result semantics. A model could satisfy a generic `dataset.query_model` tool expectation while still choosing the wrong query model.

## Target Outcome

Route-intent fixtures should fail when the AI calls `dataset.query_model` against the wrong query model. This makes model-selection drift visible as a first-class validation failure rather than only a downstream result mismatch.

## Ownership And Code Areas

| Area | Owner | Scope |
|---|---|---|
| AI result validator | `foggy-dataset-mcp` | Enforce `target_model` for successful `dataset.query_model` calls |
| AI ecommerce fixture | `foggy-dataset-mcp` | Add order-route intent cases against visible query models |
| AI validator tests | `foggy-dataset-mcp` | Regression coverage for correct and incorrect route target |
| Versioned tracking | docs | This workitem and execution check-in |

## Implementation

- Added `target_model` validation in `ResultValidator`.
- The validator now inspects successful query-compatible tool calls and accepts model-like argument keys: `model`, `modelName`, `queryModel`, `queryModelName`, `qm`, and `qmCode`.
- Added a passed rule when the expected target query model is observed.
- Added a failed rule when the expected target query model is missing, including observed model values for triage.
- Added `ROUTE-ORDER-001`: order backlog detail must use `FactOrderQueryModel` and filter `orderStatus`.
- Added `ROUTE-ORDER-002`: order payable amount by sales team must use `FactOrderQueryModel` and group by `salesTeam$caption`.
- Added validator regression tests for accepting the expected target model and rejecting a wrong sales model route.

## Acceptance Criteria

- A `dataset.query_model` fixture with `target_model` passes when the tool call uses the expected model.
- The same fixture fails when the tool call uses another model.
- Route-intent failure messages include the expected target and observed model values.
- Order backlog fixture is present and requires `orderStatus` filtering.
- Order payable by sales team fixture is present and requires `salesTeam$caption` grouping plus `payAmount` selection.
- Existing AI validator, report, and executor tests remain green.

## Constraints And Non-Goals

- This change does not modify production query routing logic.
- This change does not add service-ticket or inventory route fixtures yet because the current AI integration model list exposes only `FactSalesQueryModel` and `FactOrderQueryModel`.
- This change does not make LLM provider instability fail as a route-intent issue.
- This change does not tune prompts; it adds durable fixture and validator evidence.

## Progress Tracking

| Dimension | Status | Evidence |
|---|---|---|
| development | complete | Added target-model validation, validator regression tests, and two order-route fixtures. |
| testing | complete | Targeted Maven test command passed with 29 tests on 2026-06-04. |
| experience | N/A | Test/fixture capability only; no UI or product workflow changes. |

## Execution Check-In

Completed work:

- Implemented `target_model` enforcement for `dataset.query_model` AI validation.
- Added route-target pass/fail evidence to validation rules.
- Added order backlog and order payable-by-team route-intent fixtures.
- Kept fixture scope aligned with the current AI integration visible model list.
- Recorded this workitem under `docs/9.1.0/workitems/`.

Touched code paths:

- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/ResultValidator.java`
- `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/ai/ResultValidatorTest.java`
- `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
- `docs/9.1.0/workitems/P2-ai-route-intent-fixtures-20260604.md`

Self-check:

- Scope implemented as intended: pass.
- Non-goals preserved: pass.
- Code paths listed: pass.
- Basic self-review completed: pass.
- Self-check conclusion: self-check-only.
- Formal quality gate required before coverage audit: no.
- Remaining blockers: none for validator-level route fixture hardening.

## Validation

```bash
JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home \
mvn -pl foggy-dataset-mcp -am -P'!multi-db' \
  -Dtest='ResultValidatorTest,SpringAiTestExecutorTest,AiTestReportSummaryTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Result on 2026-06-04: passed, 29 tests.

Additional checks on 2026-06-04:

- `jq empty foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`: passed.
