---
doc_role: workitem
doc_purpose: Track AI completed and completed-paid order route-intent fixture hardening.
version: 9.1.0
status: implemented
created_at: 2026-06-05
updated_at: 2026-06-05
---

# P2 AI Completed Paid Order Intent Fixtures

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Record fixture coverage for completed-order lifecycle intent and completed-paid combined intent.
- source_type: optimization
- priority: P2
- delivery_mode: single-root-delivery

## Background

`FactOrderQueryModel` exposes both `orderStatus` and `paymentStatus`. Chinese wording such as "已完成订单" and "已完成支付" is easy to conflate: the former belongs to order lifecycle state, while the latter belongs to payment settlement state.

Local calibration showed `orderStatus = COMPLETED` plus `paymentStatus = PAID` has 11,966 rows in the current fixture data. The data also contains 2,006 rows whose `orderStatus` is `PAID`, which is tracked separately as a data/enum contract anomaly and should not become the expected lifecycle contract for completed-order intent.

## Target Outcome

AI route-intent fixtures should fail when a completed-order query uses payment status as the lifecycle predicate, or when a completed-paid query drops either the lifecycle or settlement predicate.

## Ownership And Code Areas

| Area | Owner | Scope |
|---|---|---|
| AI ecommerce fixture | `foggy-dataset-mcp` | Add completed and completed-paid route-intent cases against `FactOrderQueryModel` |
| AI result validator | `foggy-dataset-mcp` | Reuse existing value and forbidden-field validation |
| Versioned tracking | docs | This workitem and execution evidence |

## Implementation

- Added `ROUTE-ORDER-010`: completed-order detail must use `FactOrderQueryModel` with `orderStatus = COMPLETED`.
- `ROUTE-ORDER-010` forbids `paymentStatus` in `slice` so "completed order" does not collapse into payment-state filtering.
- Added `ROUTE-ORDER-011`: completed-paid order detail must use both `orderStatus = COMPLETED` and `paymentStatus = PAID`.
- Both fixtures select `orderId`, `orderStatus`, `paymentStatus`, `customer$caption`, and `payAmount`.

## Acceptance Criteria

- `ROUTE-ORDER-010` passes only when `slice` includes `orderStatus = COMPLETED`.
- `ROUTE-ORDER-010` fails when `slice` uses `paymentStatus` as the completed-order predicate.
- `ROUTE-ORDER-011` passes only when `slice` includes both `orderStatus = COMPLETED` and `paymentStatus = PAID`.
- Existing AI test case JSON remains valid.
- Existing AI validator, report, and executor tests remain green.

## Constraints And Non-Goals

- This change does not modify production query routing logic.
- This change does not modify TM/QM model metadata.
- This change does not add new validator behavior.
- This change does not tune prompts.
- This change does not normalize the anomalous `orderStatus = PAID` fixture rows.

## Progress Tracking

| Dimension | Status | Evidence |
|---|---|---|
| development | complete | Added `ROUTE-ORDER-010` and `ROUTE-ORDER-011`. |
| testing | complete | JSON validation, targeted Maven tests, focused Gemini matrix, and order-route batch passed. |
| experience | N/A | Test/fixture capability only; no UI or product workflow changes. |

## Execution Check-In

Completed work:

- Added completed-order lifecycle fixture coverage.
- Added completed-paid combined payment/lifecycle fixture coverage.
- Recorded local data calibration for non-empty direct baselines.
- Kept scope to AI test fixtures and versioned tracking.

Touched code paths:

- `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
- `docs/9.1.0/workitems/P2-ai-completed-paid-order-intent-fixtures-20260605.md`
- `docs/9.1.0/workitems/P2-order-status-paid-enum-anomaly-20260605.md`

Self-check:

- Scope implemented as intended: pass.
- Non-goals preserved: pass.
- Code paths listed: pass.
- Basic self-review completed: pass.
- Self-check conclusion: pass.
- Formal quality gate required before coverage audit: no.
- Remaining blockers: none known.

## Validation

- `jq empty foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`: pass.
- `git diff --check`: pass.
- Targeted Maven regression:
  - Command: `mvn -pl foggy-dataset-mcp -am -P'!multi-db' -Dtest='ResultValidatorTest,SpringAiTestExecutorTest,AiTestReportSummaryTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - Result: pass, 31 tests, 0 failures, 0 errors.
- Focused LLM matrix:
  - Run id: `focused-completed-paid-order-intent-gemini3flash-20260605`
  - Models: `gemini-3-flash`
  - Cases: `ROUTE-ORDER-010`, `ROUTE-ORDER-011`
  - Result: 4/4 passed, 0 failed, 2 benign payload-shape warnings, 0 tool business errors.
- Order route batch:
  - Run id: `order-route-suite-gemini3flash-20260605`
  - Models: `gemini-3-flash`
  - Cases: `ROUTE-ORDER-001` through `ROUTE-ORDER-011`
  - Result: 22/22 passed, 0 failed, 11 payload-shape warnings, 0 tool business errors.
  - Note: one existing semantic warning remains on `ROUTE-ORDER-007`: the LLM included `orderStatus = PAID` in the open-order lifecycle set. This is tracked as part of the `orderStatus = PAID` enum/data anomaly and did not fail the current rule set.

Interrupted evidence:

- An earlier mixed-model focused matrix using `gemini-pro-agent` plus `gemini-3-flash` was interrupted after `gemini-pro-agent` stalled during a paged response chain. The clean validation evidence above uses a fresh single-model run id and should be used for this workitem.
