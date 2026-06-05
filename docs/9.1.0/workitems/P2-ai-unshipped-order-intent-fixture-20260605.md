---
doc_role: workitem
doc_purpose: Track AI unshipped-order route-intent fixture hardening.
version: 9.1.0
status: implemented
created_at: 2026-06-05
updated_at: 2026-06-05
---

# P2 AI Unshipped Order Intent Fixture

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Record fixture coverage for unshipped-order intent.
- source_type: optimization
- priority: P2
- delivery_mode: single-root-delivery

## Background

`FactOrderQueryModel` exposes both `orderStatus` and `shipDate`. The ecommerce model does not expose a separate delivery status. Under the current model contract, unshipped-order intent is represented by open lifecycle states: `PENDING`, `CONFIRMED`, and `PROCESSING`.

`shipDate` is a display/date attribute. A query that treats unshipped orders as `shipDate is null` can overfit nullable fixture data instead of following the governed lifecycle state.

## Target Outcome

AI route-intent fixtures should fail when an unshipped-order query drops the lifecycle predicate, only checks a partial open-state set, or incorrectly uses `shipDate` as the unshipped predicate.

## Ownership And Code Areas

| Area | Owner | Scope |
|---|---|---|
| AI ecommerce fixture | `foggy-dataset-mcp` | Add unshipped-order route-intent case against `FactOrderQueryModel` |
| AI result validator | `foggy-dataset-mcp` | Reuse existing value and forbidden-field validation |
| Versioned tracking | docs | This workitem and execution evidence |

## Implementation

- Added `ROUTE-ORDER-007`: unshipped-order detail must use `FactOrderQueryModel`.
- The direct baseline selects `orderId`, `shipDate`, `orderStatus`, `customer$caption`, and `payAmount`.
- The direct baseline filters `orderStatus in [PENDING, CONFIRMED, PROCESSING]`.
- The expected tool argument rules require full open-state value coverage.
- The expected tool argument rules forbid `shipDate` and `paymentStatus` in `slice`.

## Acceptance Criteria

- The fixture passes when `dataset.query_model` uses `FactOrderQueryModel`.
- The fixture passes only when `slice` includes the full open-state set: `PENDING`, `CONFIRMED`, and `PROCESSING`.
- The fixture fails when the AI uses `shipDate` as the unshipped-order predicate.
- The fixture fails when the AI uses `paymentStatus` as the unshipped-order predicate.
- Existing AI test case JSON remains valid.
- Existing AI validator, report, and executor tests remain green.

## Constraints And Non-Goals

- This change does not modify production query routing logic.
- This change does not modify TM/QM model metadata.
- This change does not add new validator behavior.
- This change does not tune prompts.
- This change intentionally keeps the current model contract: shipment intent is governed by lifecycle state, not nullable `shipDate`.

## Progress Tracking

| Dimension | Status | Evidence |
|---|---|---|
| development | complete | Added `ROUTE-ORDER-007` unshipped-order intent fixture requiring full open lifecycle state coverage. |
| testing | complete | JSON validation passed; targeted Maven tests passed; focused Gemini LLM matrix captured one intentional model drift failure. |
| experience | N/A | Test/fixture capability only; no UI or product workflow changes. |

## Execution Check-In

Completed work:

- Added unshipped-order route-intent fixture coverage.
- Reused existing value and forbidden-field validation.
- Kept scope to AI test fixtures and versioned tracking.

Touched code paths:

- `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
- `docs/9.1.0/workitems/P2-ai-unshipped-order-intent-fixture-20260605.md`

Self-check:

- Scope implemented as intended: pass.
- Non-goals preserved: pass.
- Code paths listed: pass.
- Basic self-review completed: pass.
- Self-check conclusion: pass.
- Formal quality gate required before coverage audit: no.
- Remaining blockers: none known.

## Validation

Commands executed:

- `jq empty foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
- `git diff --check`
- `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-mcp -am -P'!multi-db' -Dtest='ResultValidatorTest,SpringAiTestExecutorTest,AiTestReportSummaryTest' -Dsurefire.failIfNoSpecifiedTests=false test`
- `scripts/run-ai-llm-matrix.sh --models gemini-pro-agent,gemini-3-flash --base-url "$AI_TEST_OPENAI_BASE_URL" --case-ids ROUTE-ORDER-007 --continue-on-error --run-id focused-unshipped-order-intent-gemini-20260605`

Results:

- JSON fixture validation: pass.
- Whitespace diff check: pass.
- Targeted Maven regression tests: pass, 31 tests, 0 failures, 0 errors.
- Focused Gemini matrix: resultCount=4, passed=3, failed=1, warnings=2, toolBusinessErrors=0.

Focused LLM evidence:

- `gemini-pro-agent` used `FactOrderQueryModel` with `slice: orderStatus in [PENDING, CONFIRMED, PROCESSING]`, did not use `shipDate` or `paymentStatus` in `slice`, and paged through the result set.
- `gemini-3-flash` used `FactOrderQueryModel` but generated `slice: orderStatus not in [SHIPPED, DELIVERED, COMPLETED, CANCELLED, REFUNDED]`. The validator failed it because the query does not positively require the governed open-state set and can include non-open lifecycle values such as `PAID` from runtime data.
- The failed rule was `TOOL_ARGUMENT:slice:orderStatus`, expected value coverage `[PENDING, CONFIRMED, PROCESSING]`, observed `orderStatus not in`.

Warning classification:

- One `query_payload_shape_divergence` warning is semantic and corresponds to the `gemini-3-flash` negative-state predicate.
- One `benign_query_payload_shape_divergence` warning is caused by `gemini-pro-agent` pagination/limit shape differences; the semantic signature stayed `FactOrderQueryModel` plus `orderStatus in [PENDING, CONFIRMED, PROCESSING]`.

Calibration note:

- This fixture is intentionally kept strict. The focused matrix failure is useful model-drift evidence rather than a reason to broaden the expected contract to negative closed-state filtering.
