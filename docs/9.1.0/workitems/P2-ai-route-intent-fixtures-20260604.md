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
- Added optional `tool_argument_rules.value` validation for condition rules. When a rule declares a scalar or list value, the validator requires matching condition values to cover the full expected set.
- Tightened `ROUTE-ORDER-001` from generic pending-order wording to open-order backlog wording. The fixture now requires `orderStatus` to cover `PENDING`, `CONFIRMED`, and `PROCESSING`.

## Acceptance Criteria

- A `dataset.query_model` fixture with `target_model` passes when the tool call uses the expected model.
- The same fixture fails when the tool call uses another model.
- Route-intent failure messages include the expected target and observed model values.
- Order backlog fixture is present and requires `orderStatus` filtering.
- Order backlog fixture fails when the model only filters a subset of expected open-order states.
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
| development | complete | Added target-model validation, condition-value coverage validation, validator regression tests, and two order-route fixtures. |
| testing | complete | Targeted Maven test command passed with 31 tests on 2026-06-04. Focused Gemini route-intent matrix passed 12/12 after value-rule tightening. |
| experience | N/A | Test/fixture capability only; no UI or product workflow changes. |

## Execution Check-In

Completed work:

- Implemented `target_model` enforcement for `dataset.query_model` AI validation.
- Implemented optional condition value coverage enforcement for `tool_argument_rules`.
- Added route-target pass/fail evidence to validation rules.
- Added order backlog and order payable-by-team route-intent fixtures.
- Clarified open-order backlog wording and required the three expected open states.
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

Result on 2026-06-04 after condition-value coverage tightening: passed, 31 tests.

Additional checks on 2026-06-04:

- `jq empty foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`: passed.

Focused LLM matrix evidence before value-rule tightening:

```bash
scripts/run-ai-llm-matrix.sh \
  --models gemini-pro-agent,gemini-3-flash \
  --case-ids FILTER-002,ROUTE-ORDER-001,ROUTE-ORDER-002 \
  --continue-on-error \
  --run-id focused-route-intent-gemini-20260604
```

Result on 2026-06-04: passed 12/12, failed 0, warnings 6, tool business errors 0.

Follow-up from this matrix:

- `ROUTE-ORDER-001` produced a semantic warning because the previous question wording said pending backlog while the direct baseline used all open-order states.
- The fixture was tightened so this gap is validated through `tool_argument_rules.value` instead of remaining only a query payload divergence warning.

Focused LLM matrix evidence after value-rule tightening:

```bash
scripts/run-ai-llm-matrix.sh \
  --models gemini-pro-agent,gemini-3-flash \
  --case-ids FILTER-002,ROUTE-ORDER-001,ROUTE-ORDER-002 \
  --continue-on-error \
  --run-id focused-route-intent-value-rules-gemini-20260604
```

Result on 2026-06-04: passed 12/12, failed 0, warnings 6, tool business errors 0.

Observed outcome:

- `gemini-pro-agent` and `gemini-3-flash` both generated `orderStatus in [PENDING, CONFIRMED, PROCESSING]` for `ROUTE-ORDER-001`.
- Remaining warnings are `benign_query_payload_shape_divergence`; semantic signatures match for the route-intent fields.
