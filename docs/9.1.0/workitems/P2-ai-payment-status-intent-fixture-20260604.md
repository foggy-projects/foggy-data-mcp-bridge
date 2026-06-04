---
doc_role: workitem
doc_purpose: Track AI payment-status route-intent fixture hardening.
version: 9.1.0
status: implemented
created_at: 2026-06-04
updated_at: 2026-06-04
---

# P2 AI Payment Status Intent Fixture

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Record fixture coverage for distinguishing order lifecycle status from payment settlement status.
- source_type: optimization
- priority: P2
- delivery_mode: single-root-delivery

## Background

The order query model exposes both `orderStatus` and `paymentStatus`. These fields are semantically close but answer different business questions:

- `orderStatus`: lifecycle stage such as pending, confirmed, processing, shipped, completed, or cancelled.
- `paymentStatus`: settlement state such as unpaid, partial, paid, refunding, or refunded.

After route-intent validation gained condition value coverage, the next high-value fixture is a payment-status question that should not be satisfied by an order lifecycle filter.

## Target Outcome

AI route-intent fixtures should fail when a "not fully paid order" query uses `orderStatus` or misses part of the expected payment-status value set.

## Ownership And Code Areas

| Area | Owner | Scope |
|---|---|---|
| AI ecommerce fixture | `foggy-dataset-mcp` | Add a payment-status route-intent case against `FactOrderQueryModel` |
| AI result validator | `foggy-dataset-mcp` | Reuse existing `tool_argument_rules.value` and forbidden field validation |
| Versioned tracking | docs | This workitem and execution check-in |

## Implementation

- Added `ROUTE-ORDER-003`: not-fully-paid order detail must use `FactOrderQueryModel`.
- The direct baseline selects `orderId`, `paymentStatus`, `customer$caption`, and `payAmount`.
- The direct baseline filters `paymentStatus in [UNPAID, PARTIAL]`.
- The expected tool argument rules require `paymentStatus` value coverage.
- The expected tool argument rules forbid `orderStatus` in `slice` so lifecycle-stage confusion becomes a validation failure.

## Acceptance Criteria

- A not-fully-paid order fixture passes when `dataset.query_model` uses `FactOrderQueryModel`.
- The fixture passes only when `slice` includes `paymentStatus` with `UNPAID` and `PARTIAL`.
- The fixture fails when the AI uses `orderStatus` as the payment-settlement predicate.
- Existing AI test case JSON remains valid.
- Existing AI validator, report, and executor tests remain green.

## Constraints And Non-Goals

- This change does not modify production query routing logic.
- This change does not add new validator behavior; it reuses condition value coverage and forbidden-field rules.
- This change does not tune prompts.
- This change does not require the fixture data to contain rows for every allowed alias value; the tool-call predicate still must preserve the full business intent.

## Progress Tracking

| Dimension | Status | Evidence |
|---|---|---|
| development | complete | Added `ROUTE-ORDER-003` payment-status intent fixture. |
| testing | complete | JSON validation, targeted Maven tests, and focused Gemini matrix passed. |
| experience | N/A | Test/fixture capability only; no UI or product workflow changes. |

## Execution Check-In

Completed work:

- Added payment-status route-intent fixture coverage.
- Reused existing value coverage and forbidden-field validation instead of adding new test-only validation code.
- Kept fixture scope aligned with the current AI integration visible model list.

Touched code paths:

- `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
- `docs/9.1.0/workitems/P2-ai-payment-status-intent-fixture-20260604.md`

Self-check:

- Scope implemented as intended: pass.
- Non-goals preserved: pass.
- Code paths listed: pass.
- Basic self-review completed: pass.
- Self-check conclusion: pass.
- Formal quality gate required before coverage audit: no.
- Remaining blockers: none known.

## Validation

- `jq empty foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
  - Result: pass.
- `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-mcp -am -P'!multi-db' -Dtest='ResultValidatorTest,SpringAiTestExecutorTest,AiTestReportSummaryTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - Result: pass, 31 tests, 0 failures, 0 errors.
- `scripts/run-ai-llm-matrix.sh --models gemini-pro-agent,gemini-3-flash --case-ids ROUTE-ORDER-003 --continue-on-error --run-id focused-payment-status-intent-gemini-20260604`
  - Result: pass, 4/4 results passed, 0 failed, 2 benign query payload shape warnings, 0 tool business errors.
  - Semantic payload evidence: both Gemini models used `FactOrderQueryModel` with `paymentStatus in [UNPAID, PARTIAL]`; no `orderStatus` payment predicate was observed.
