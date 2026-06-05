---
doc_role: workitem
doc_purpose: Track AI cancelled-order route-intent fixture hardening.
version: 9.1.0
status: implemented
created_at: 2026-06-05
updated_at: 2026-06-05
---

# P2 AI Cancelled Order Intent Fixture

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Record fixture coverage for cancelled-order intent.
- source_type: optimization
- priority: P2
- delivery_mode: single-root-delivery

## Background

`FactOrderQueryModel` exposes both `orderStatus` and `paymentStatus`. Cancelled-order intent belongs to the order lifecycle state, while refund, unpaid, partial-paid, and paid intents belong to payment settlement state.

The prompt asks to display both status fields so the model has access to the visible payment column, but the filter must still use `orderStatus = CANCELLED`.

## Target Outcome

AI route-intent fixtures should fail when a cancelled-order query drops the lifecycle predicate or incorrectly uses payment status as the cancelled-order predicate.

## Ownership And Code Areas

| Area | Owner | Scope |
|---|---|---|
| AI ecommerce fixture | `foggy-dataset-mcp` | Add cancelled-order route-intent case against `FactOrderQueryModel` |
| AI result validator | `foggy-dataset-mcp` | Reuse existing value and forbidden-field validation |
| Versioned tracking | docs | This workitem and execution evidence |

## Implementation

- Added `ROUTE-ORDER-006`: cancelled-order detail must use `FactOrderQueryModel`.
- The direct baseline selects `orderId`, `orderStatus`, `paymentStatus`, `customer$caption`, and `payAmount`.
- The direct baseline filters `orderStatus = CANCELLED`.
- The expected tool argument rules require the `orderStatus = CANCELLED` predicate.
- The expected tool argument rules forbid `paymentStatus` in `slice`.

## Acceptance Criteria

- The fixture passes when `dataset.query_model` uses `FactOrderQueryModel`.
- The fixture passes only when `slice` includes `orderStatus = CANCELLED`.
- The fixture fails when the AI uses `paymentStatus` as the cancelled-order predicate.
- Existing AI test case JSON remains valid.
- Existing AI validator, report, and executor tests remain green.

## Constraints And Non-Goals

- This change does not modify production query routing logic.
- This change does not modify TM/QM model metadata.
- This change does not add new validator behavior.
- This change does not tune prompts.

## Progress Tracking

| Dimension | Status | Evidence |
|---|---|---|
| development | complete | Added `ROUTE-ORDER-006` cancelled-order intent fixture requiring `orderStatus = CANCELLED`. |
| testing | complete | JSON validation passed; targeted Maven tests passed; focused Gemini LLM matrix passed 4/4 with 0 failures and 0 tool business errors. |
| experience | N/A | Test/fixture capability only; no UI or product workflow changes. |

## Execution Check-In

Completed work:

- Added cancelled-order route-intent fixture coverage.
- Reused existing value and forbidden-field validation.
- Kept scope to AI test fixtures and versioned tracking.

Touched code paths:

- `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
- `docs/9.1.0/workitems/P2-ai-cancelled-order-intent-fixture-20260605.md`

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
- `scripts/run-ai-llm-matrix.sh --models gemini-pro-agent,gemini-3-flash --base-url "$AI_TEST_OPENAI_BASE_URL" --case-ids ROUTE-ORDER-006 --continue-on-error --run-id focused-cancelled-order-intent-gemini-20260605`

Results:

- JSON fixture validation: pass.
- Whitespace diff check: pass.
- Targeted Maven regression tests: pass, 31 tests, 0 failures, 0 errors.
- Focused Gemini matrix: pass, resultCount=4, passed=4, failed=0, warnings=2, toolBusinessErrors=0.

Focused LLM evidence:

- `gemini-3-flash` used `FactOrderQueryModel` with `slice: orderStatus = CANCELLED` and did not use `paymentStatus` in `slice`.
- `gemini-pro-agent` used `FactOrderQueryModel` with `slice: orderStatus = CANCELLED` and did not use `paymentStatus` in `slice`; it paged through the cancelled-order result set.
- Both warning records were classified as `benign_query_payload_shape_divergence`: the semantic signature stayed `FactOrderQueryModel` plus `orderStatus|=|CANCELLED`; only `limit`/pagination shape differed from the direct baseline.
