---
doc_role: workitem
doc_purpose: Track AI paid-shipped order route-intent fixture hardening.
version: 9.1.0
status: implemented
created_at: 2026-06-05
updated_at: 2026-06-05
---

# P2 AI Paid Shipped Order Intent Fixture

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Record fixture coverage for combined paid and shipped order intent.
- source_type: optimization
- priority: P2
- delivery_mode: single-root-delivery

## Background

`FactOrderQueryModel` separates payment settlement state (`paymentStatus`) from order lifecycle state (`orderStatus`). After adding unpaid-unshipped coverage, the paired positive fulfillment case is paid and shipped orders.

Local calibration showed `paymentStatus = PAID` plus `orderStatus = SHIPPED` has 2,051 rows in the current fixture data. Some shipped rows still have nullable `shipDate`, so shipped intent must continue to use governed lifecycle state rather than a shipping-date nullability predicate.

## Target Outcome

AI route-intent fixtures should fail when a combined paid-shipped query only applies payment status, only applies lifecycle status, converts shipped intent to a nullable `shipDate` predicate, or routes away from `FactOrderQueryModel`.

## Ownership And Code Areas

| Area | Owner | Scope |
|---|---|---|
| AI ecommerce fixture | `foggy-dataset-mcp` | Add paid-shipped route-intent case against `FactOrderQueryModel` |
| AI result validator | `foggy-dataset-mcp` | Reuse existing value and forbidden-field validation |
| Versioned tracking | docs | This workitem and execution evidence |

## Implementation

- Added `ROUTE-ORDER-009`: paid-shipped order detail must use `FactOrderQueryModel`.
- The direct baseline selects `orderId`, `shipDate`, `orderStatus`, `paymentStatus`, `customer$caption`, and `payAmount`.
- The direct baseline filters `paymentStatus = PAID`.
- The direct baseline also filters `orderStatus = SHIPPED`.
- The expected tool argument rules require both payment and lifecycle predicates.
- The expected tool argument rules forbid `shipDate` in `slice`.

## Acceptance Criteria

- The fixture passes when `dataset.query_model` uses `FactOrderQueryModel`.
- The fixture passes only when `slice` includes `paymentStatus = PAID`.
- The fixture passes only when `slice` includes `orderStatus = SHIPPED`.
- The fixture fails when the AI uses `shipDate` as the shipped-order predicate.
- Existing AI test case JSON remains valid.
- Existing AI validator, report, and executor tests remain green.

## Constraints And Non-Goals

- This change does not modify production query routing logic.
- This change does not modify TM/QM model metadata.
- This change does not add new validator behavior.
- This change does not tune prompts.
- This change intentionally keeps `shipDate` as an output field, not a filter.

## Progress Tracking

| Dimension | Status | Evidence |
|---|---|---|
| development | complete | Added `ROUTE-ORDER-009` paid-shipped intent fixture requiring payment and lifecycle predicates. |
| testing | complete | JSON validation, diff check, targeted Maven tests, and focused Gemini matrix passed. |
| experience | N/A | Test/fixture capability only; no UI or product workflow changes. |

## Execution Check-In

Completed work:

- Added combined paid-shipped route-intent fixture coverage.
- Reused existing value and forbidden-field validation.
- Kept scope to AI test fixtures and versioned tracking.

Touched code paths:

- `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
- `docs/9.1.0/workitems/P2-ai-paid-shipped-order-intent-fixture-20260605.md`

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
  - Result: pass, 31 tests, 0 failures, 0 errors; AI test loader reported 21 test cases.
- Focused LLM matrix:
  - `scripts/run-ai-llm-matrix.sh --models gemini-pro-agent,gemini-3-flash --case-ids ROUTE-ORDER-009 --continue-on-error --run-id focused-paid-shipped-order-intent-gemini-20260605`
  - Result: `resultCount=4`, `passed=4`, `failed=0`, `warnings=2`, `toolBusinessErrors=0`.
  - Warning classification: two `benign_query_payload_shape_divergence` records.
  - Semantic evidence: direct baseline, `gemini-pro-agent`, and `gemini-3-flash` all preserved `FactOrderQueryModel` with `paymentStatus = PAID` and `orderStatus = SHIPPED`.

## Follow-Up Notes

- Keep `ROUTE-ORDER-009` strict: paid-shipped intent must preserve both settlement and lifecycle predicates.
- Do not convert shipped intent to `shipDate is not null`; current fixture rows can be shipped while `shipDate` is null.
- This case is a stable positive counterpart to `ROUTE-ORDER-008`, which intentionally captured model drift for unpaid-unshipped wording.
