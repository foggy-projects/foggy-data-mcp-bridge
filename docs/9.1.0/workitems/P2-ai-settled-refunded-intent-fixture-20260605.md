---
doc_role: workitem
doc_purpose: Track AI settled/refunded payment-status route-intent fixture hardening.
version: 9.1.0
status: implemented
created_at: 2026-06-05
updated_at: 2026-06-05
---

# P2 AI Settled Refunded Intent Fixture

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Record fixture coverage for settled-or-refunded order payment intent.
- source_type: optimization
- priority: P2
- delivery_mode: single-root-delivery

## Background

`FactOrderQueryModel` exposes both order lifecycle status and payment settlement status. After adding the not-fully-paid route-intent fixture, the next paired case is the positive settlement side: "paid or refunded" should be routed to `paymentStatus`, not to `orderStatus`.

The model already exposes the payment alias `settled_or_refunded=[PAID, REFUNDED]`, so this fixture validates whether the AI path preserves that business intent in the tool-call predicate.

## Target Outcome

AI route-intent fixtures should fail when a settled-or-refunded order query uses an order lifecycle predicate or drops one of the expected payment settlement values.

## Ownership And Code Areas

| Area | Owner | Scope |
|---|---|---|
| AI ecommerce fixture | `foggy-dataset-mcp` | Add settled/refunded payment route-intent case against `FactOrderQueryModel` |
| AI result validator | `foggy-dataset-mcp` | Reuse existing `tool_argument_rules.value` and forbidden field validation |
| Versioned tracking | docs | This workitem and execution evidence |

## Implementation

- Added `ROUTE-ORDER-004`: settled-or-refunded order detail must use `FactOrderQueryModel`.
- The direct baseline selects `orderId`, `paymentStatus`, `customer$caption`, and `payAmount`.
- The direct baseline filters `paymentStatus in [PAID, REFUNDED]`.
- The expected tool argument rules require full payment-status value coverage.
- The expected tool argument rules forbid `orderStatus` in `slice`.

## Acceptance Criteria

- The fixture passes when `dataset.query_model` uses `FactOrderQueryModel`.
- The fixture passes only when `slice` includes `paymentStatus` with `PAID` and `REFUNDED`.
- The fixture fails when the AI uses `orderStatus` as the payment-settlement predicate.
- Existing AI test case JSON remains valid.
- Existing AI validator, report, and executor tests remain green.

## Constraints And Non-Goals

- This change does not modify production query routing logic.
- This change does not modify TM/QM model metadata.
- This change does not add new validator behavior.
- This change does not tune prompts.
- Fixture data does not need rows for every allowed alias value; the tool-call predicate still must preserve the full business intent.

## Progress Tracking

| Dimension | Status | Evidence |
|---|---|---|
| development | complete | Added `ROUTE-ORDER-004` settled/refunded payment-status intent fixture. |
| testing | complete | JSON validation, targeted Maven tests, and focused Gemini matrix passed. |
| experience | N/A | Test/fixture capability only; no UI or product workflow changes. |

## Execution Check-In

Completed work:

- Added settled/refunded route-intent fixture coverage.
- Reused existing condition value coverage and forbidden-field validation.
- Kept scope to AI test fixtures and versioned tracking.

Touched code paths:

- `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
- `docs/9.1.0/workitems/P2-ai-settled-refunded-intent-fixture-20260605.md`

Self-check:

- Scope implemented as intended: pass.
- Non-goals preserved: pass.
- Code paths listed: pass.
- Basic self-review completed: pass.
- Self-check conclusion: pass.
- Formal quality gate required before coverage audit: no.
- Remaining blockers: none known.

## Validation

Validation commands:

- `jq empty foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`: pass.
- `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-mcp -am -P'!multi-db' -Dtest='ResultValidatorTest,SpringAiTestExecutorTest,AiTestReportSummaryTest' -Dsurefire.failIfNoSpecifiedTests=false test`: pass, 31 tests.
- `scripts/run-ai-llm-matrix.sh --models gemini-pro-agent,gemini-3-flash --base-url "$AI_TEST_OPENAI_BASE_URL" --case-ids ROUTE-ORDER-004 --continue-on-error --run-id focused-settled-refunded-intent-gemini-20260605`: pass.

Focused LLM matrix result:

- `resultCount=4`, `passed=4`, `failed=0`.
- `warningCount=2`, `warningCategories.benign_query_payload_shape_divergence=2`.
- `toolBusinessErrors=0`.
- `spring-ai/gemini-pro-agent`: pass.
- `spring-ai/gemini-3-flash`: pass.

Semantic evidence:

- Both Gemini runs used `FactOrderQueryModel`.
- Both Gemini runs preserved `slice.paymentStatus in [PAID, REFUNDED]`.
- No LLM run used `orderStatus` as the payment-settlement predicate.
- The two warnings were benign query payload shape differences: direct baseline uses `limit=20`, Gemini calls used omitted or `limit=100`; semantic signatures remained equivalent.
