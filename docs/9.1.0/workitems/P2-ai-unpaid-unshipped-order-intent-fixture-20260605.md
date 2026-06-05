---
doc_role: workitem
doc_purpose: Track AI unpaid-unshipped order route-intent fixture hardening.
version: 9.1.0
status: implemented
created_at: 2026-06-05
updated_at: 2026-06-05
---

# P2 AI Unpaid Unshipped Order Intent Fixture

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Record fixture coverage for combined payment-state and shipment-state order intent.
- source_type: optimization
- priority: P2
- delivery_mode: single-root-delivery

## Background

`FactOrderQueryModel` separates payment settlement state (`paymentStatus`) from order lifecycle state (`orderStatus`). Previous fixtures covered each state family separately. Combined questions such as unpaid and unshipped orders require both predicates to be preserved.

Local calibration showed `paymentStatus = PAID` plus open lifecycle states has 0 rows in the current fixture data, while unpaid open orders have 1,940 rows. This case therefore uses unpaid and unshipped orders so the direct baseline can remain `NOT_EMPTY` while still testing combined payment/lifecycle routing.

## Target Outcome

AI route-intent fixtures should fail when a combined unpaid-unshipped query only applies payment status, only applies lifecycle status, converts unshipped intent to a nullable `shipDate` predicate, or routes away from `FactOrderQueryModel`.

## Ownership And Code Areas

| Area | Owner | Scope |
|---|---|---|
| AI ecommerce fixture | `foggy-dataset-mcp` | Add unpaid-unshipped route-intent case against `FactOrderQueryModel` |
| AI result validator | `foggy-dataset-mcp` | Reuse existing value and forbidden-field validation |
| Versioned tracking | docs | This workitem and execution evidence |

## Implementation

- Added `ROUTE-ORDER-008`: unpaid-unshipped order detail must use `FactOrderQueryModel`.
- The direct baseline selects `orderId`, `shipDate`, `orderStatus`, `paymentStatus`, `customer$caption`, and `payAmount`.
- The direct baseline filters `paymentStatus = UNPAID`.
- The direct baseline also filters `orderStatus in [PENDING, CONFIRMED, PROCESSING]`.
- The expected tool argument rules require both payment and lifecycle predicates.
- The expected tool argument rules forbid `shipDate` in `slice`.

## Acceptance Criteria

- The fixture passes when `dataset.query_model` uses `FactOrderQueryModel`.
- The fixture passes only when `slice` includes `paymentStatus = UNPAID`.
- The fixture passes only when `slice` includes the full open-state set: `PENDING`, `CONFIRMED`, and `PROCESSING`.
- The fixture fails when the AI uses `shipDate` as the unshipped-order predicate.
- Existing AI test case JSON remains valid.
- Existing AI validator, report, and executor tests remain green.

## Constraints And Non-Goals

- This change does not modify production query routing logic.
- This change does not modify TM/QM model metadata.
- This change does not add new validator behavior.
- This change does not tune prompts.
- This change intentionally avoids a paid-unshipped fixture until fixture data contains non-empty paid-open lifecycle rows.

## Progress Tracking

| Dimension | Status | Evidence |
|---|---|---|
| development | complete | Added `ROUTE-ORDER-008` unpaid-unshipped intent fixture requiring payment and lifecycle predicates. |
| testing | complete | JSON validation, diff check, targeted Maven tests, and focused Gemini matrix executed. |
| experience | N/A | Test/fixture capability only; no UI or product workflow changes. |

## Execution Check-In

Completed work:

- Added combined unpaid-unshipped route-intent fixture coverage.
- Reused existing value and forbidden-field validation.
- Kept scope to AI test fixtures and versioned tracking.

Touched code paths:

- `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
- `docs/9.1.0/workitems/P2-ai-unpaid-unshipped-order-intent-fixture-20260605.md`

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
- Targeted Maven command:
  - `mvn -pl foggy-dataset-mcp -am -P'!multi-db' -Dtest='ResultValidatorTest,SpringAiTestExecutorTest,AiTestReportSummaryTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - Result: pass, 31 tests, 0 failures, 0 errors; AI test loader reported 20 test cases.
- Focused LLM matrix:
  - `scripts/run-ai-llm-matrix.sh --models gemini-pro-agent,gemini-3-flash --case-ids ROUTE-ORDER-008 --continue-on-error --run-id focused-unpaid-unshipped-order-intent-gemini-20260605`
  - Result: `resultCount=4`, `passed=2`, `failed=2`, `warnings=2`, `toolBusinessErrors=0`.
  - Direct baselines passed for both matrix legs.
  - `gemini-3-flash` failed the lifecycle predicate rule by using `orderStatus not in [SHIPPED, DELIVERED, COMPLETED, CANCELLED, REFUNDED]` instead of `orderStatus in [PENDING, CONFIRMED, PROCESSING]`.
  - `gemini-pro-agent` failed because the conversation contained earlier `shipDate is null` and negative lifecycle predicate attempts before a later correct open-state query. This is recorded as useful model drift rather than loosening the fixture.

## Follow-Up Notes

- Keep `ROUTE-ORDER-008` strict: payment and lifecycle predicates must both be explicit.
- Do not convert "unshipped" to `shipDate is null`; shipping date is an output field here, not the governed route-intent predicate.
- A paid-unshipped fixture can be added later after seed data contains non-empty `PAID` plus open lifecycle rows.
