---
doc_role: workitem
doc_purpose: Track AI shipped-order route-intent fixture hardening.
version: 9.1.0
status: implemented
created_at: 2026-06-05
updated_at: 2026-06-05
---

# P2 AI Shipped Order Intent Fixture

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Record fixture coverage for shipped-order intent.
- source_type: optimization
- priority: P2
- delivery_mode: single-root-delivery

## Background

`FactOrderQueryModel` exposes `shipDate`, `orderStatus`, and `paymentStatus`. These fields are close enough that an LLM may route a shipped-order request to settlement state or over-filter with a shipping-date predicate instead of the governed order lifecycle state.

The ecommerce model does not expose a separate `deliveryStatus`. Under the current model contract, shipped-order intent is represented by lifecycle state `orderStatus = SHIPPED`; `shipDate` is a display/date attribute and can be null even for rows whose lifecycle state is `SHIPPED`.

Calibration note: the first focused Gemini run used an incorrect fixture assumption requiring `shipDate is not null`. Both Gemini models instead generated `orderStatus = SHIPPED`, with no `paymentStatus` filter, which matches the current TM/QM semantics. The fixture was corrected to assert lifecycle-state routing rather than shipping-date non-null filtering.

## Target Outcome

AI route-intent fixtures should fail when a shipped-order query drops the lifecycle predicate, incorrectly uses payment status as the shipped-order predicate, or over-filters by requiring `shipDate`.

## Ownership And Code Areas

| Area | Owner | Scope |
|---|---|---|
| AI ecommerce fixture | `foggy-dataset-mcp` | Add shipped-order route-intent case against `FactOrderQueryModel` |
| AI result validator | `foggy-dataset-mcp` | Reuse existing `tool_argument_rules.operator` and forbidden field validation |
| Versioned tracking | docs | This workitem and execution evidence |

## Implementation

- Added `ROUTE-ORDER-005`: shipped-order detail must use `FactOrderQueryModel`.
- The direct baseline selects `orderId`, `shipDate`, `orderStatus`, `customer$caption`, and `payAmount`.
- The direct baseline filters `orderStatus = SHIPPED`.
- The expected tool argument rules require the `orderStatus = SHIPPED` predicate.
- The expected tool argument rules forbid `paymentStatus` and `shipDate` in `slice`.

## Acceptance Criteria

- The fixture passes when `dataset.query_model` uses `FactOrderQueryModel`.
- The fixture passes only when `slice` includes `orderStatus = SHIPPED`.
- The fixture fails when the AI uses `paymentStatus` as the shipped-order predicate.
- The fixture fails when the AI adds `shipDate` as a shipped-order filter.
- Existing AI test case JSON remains valid.
- Existing AI validator, report, and executor tests remain green.

## Constraints And Non-Goals

- This change does not modify production query routing logic.
- This change does not modify TM/QM model metadata.
- This change does not add new validator behavior.
- This change does not tune prompts.
- This change intentionally uses `orderStatus` because it is the governed lifecycle state for shipped orders in the ecommerce order query model.

## Progress Tracking

| Dimension | Status | Evidence |
|---|---|---|
| development | complete | Added `ROUTE-ORDER-005` shipped-order intent fixture requiring `orderStatus = SHIPPED`. |
| testing | complete | JSON and diff checks passed; targeted Maven passed 31 tests; corrected focused Gemini matrix passed 4/4. |
| experience | N/A | Test/fixture capability only; no UI or product workflow changes. |

## Execution Check-In

Completed work:

- Added shipped-order route-intent fixture coverage.
- Reused existing value and forbidden-field validation.
- Corrected the fixture after calibration showed `shipDate is not null` was a test-data assumption, not the current model contract.
- Kept scope to AI test fixtures and versioned tracking.

Touched code paths:

- `foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
- `docs/9.1.0/workitems/P2-ai-shipped-order-intent-fixture-20260605.md`

Self-check:

- Scope implemented as intended: pass.
- Non-goals preserved: pass.
- Code paths listed: pass.
- Basic self-review completed: pass.
- Self-check conclusion: pass.
- Formal quality gate required before coverage audit: no.
- Remaining blockers: none known.

## Validation

Commands:

- `jq empty foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json`
- `git diff --check`
- `JAVA_HOME=/Users/fengjianguang/.jdk/temurin-17/Contents/Home mvn -pl foggy-dataset-mcp -am -P'!multi-db' -Dtest='ResultValidatorTest,SpringAiTestExecutorTest,AiTestReportSummaryTest' -Dsurefire.failIfNoSpecifiedTests=false test`
- `scripts/run-ai-llm-matrix.sh --models gemini-pro-agent,gemini-3-flash --base-url "$AI_TEST_OPENAI_BASE_URL" --case-ids ROUTE-ORDER-005 --continue-on-error --run-id focused-shipped-order-intent-gemini-fixed-20260605`

Results:

- JSON validation: pass.
- Diff whitespace check: pass.
- Targeted Maven tests: pass, 31 tests, 0 failures, 0 errors.
- Focused Gemini matrix: pass, `resultCount=4`, `passed=4`, `failed=0`, `warnings=2`, `toolBusinessErrors=0`.
- Warning classification: two `benign_query_payload_shape_divergence` warnings caused by pagination/limit shape differences.
- Semantic evidence: both `gemini-pro-agent` and `gemini-3-flash` queried `FactOrderQueryModel` with `orderStatus = SHIPPED`; neither used `paymentStatus` nor `shipDate` as a filter.

Calibration evidence:

- Initial run `focused-shipped-order-intent-gemini-20260605` failed 2/4 because the fixture incorrectly required `shipDate is not null`.
- The failure was a fixture/model-contract mismatch: generated tool calls used the current governed lifecycle state `orderStatus = SHIPPED`.
- The corrected fixture now protects the intended route semantics while avoiding a false assertion on nullable display data.
