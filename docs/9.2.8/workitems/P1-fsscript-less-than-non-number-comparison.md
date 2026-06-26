---
type: bug
bug_source: code-review
version: 9.2.8
ticket: P1-fsscript-less-than-non-number-comparison
severity: major
status: signed-off
reproduction_status: confirmed-by-code-review
reproduction_evidence: confirmed-by-red-unit-test
test_strategy: unit-test
automation_decision: required
owner: foggy-fsscript
owner_module: foggy-fsscript
created_at: 2026-06-26
updated_at: 2026-06-26
---

# P1 FSScript Less-Than Non-Number Comparison

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track a FSScript comparison-operator bug where `<` and `<=` cannot evaluate non-number operands even though `>` and `>=` can.

## Background

During the FSScript code-risk review, `GT` and `GT_equal` were found to support both numeric comparison and non-number fallback comparison through `toString().compareTo(...)`.

`LT` and `LT_equal` do not follow the same contract. They cast both operands directly to `Number`, then compare `doubleValue()`. Expressions that compare string values, including ISO date strings commonly used in generated model scripts, compile successfully but fail during evaluation with `ClassCastException`.

## Problem Statement

The comparison family is asymmetric:

- `>` and `>=` can compare non-number values such as `'2019-06-30' > '2019-05-01'`.
- `<` and `<=` fail for equivalent non-number expressions such as `'2019-05-01' < '2019-06-30'`.

This creates a deterministic runtime correctness bug in FSScript scripts that use lexical string or date-string ordering.

## Target Outcome

- `<` supports non-number fallback comparison consistently with `>`.
- `<=` supports non-number fallback comparison consistently with `>=`.
- Existing numeric `<` and `<=` behavior remains unchanged.
- The original bug is exposed by a focused failing unit test before the fix.

## Touched Code Areas

- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/fun/LT.java`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/fun/LT_equal.java`
- `foggy-fsscript/src/test/java/com/foggyframework/fsscript/exp/ComparisonOperatorTest.java`

## Acceptance Criteria

- A focused unit test fails before the fix for string/date-string `<` and `<=`.
- The same focused unit test passes after the fix.
- Numeric `<` and `<=` behavior remains covered and unchanged.
- `mvn -pl foggy-fsscript test` passes.

## Constraints / Non-Goals

- No FSScript syntax change.
- No Runtime API security, auth-code, RBAC, audit, or permission-model change.
- No broad comparison-semantics redesign beyond aligning `<` and `<=` with the already implemented `>` and `>=` non-number fallback behavior.

## Progress Tracking

| Item | Status | Notes |
|---|---|---|
| Workitem recorded | done | 9.2.8 work item created. |
| Red test | done | `ComparisonOperatorTest` fails before the fix for string/date-string `<` and `<=`. |
| Implementation | done | `LT` and `LT_equal` now preserve numeric/null comparison behavior and add non-number fallback comparison. |
| Verification | done | Focused, related, and full `foggy-fsscript` regressions passed after the fix. |
| Quality | done | Implementation quality gate reviewed with no blocking item. |
| Coverage | done | Coverage audit found no blocking evidence gap. |
| Acceptance | done | 9.2.8 version acceptance signed off. |

## Experience Progress

experience: N/A

Reason: Backend/runtime-only comparison operator fix; no UI or manual workflow changes.

## Execution Checklist

- [x] Record 9.2.8 work item.
- [x] Add failing unit test for non-number `<`.
- [x] Add failing unit test for non-number `<=`.
- [x] Add numeric regression coverage.
- [x] Fix `LT`.
- [x] Fix `LT_equal`.
- [x] Run focused test.
- [x] Run full `foggy-fsscript` regression.
- [x] Record execution check-in and acceptance readiness.

## Acceptance Readiness

status: signed-off

Reason: Required red-test evidence, implementation fix, focused regression, related regression, full `foggy-fsscript` regression, implementation quality review, coverage audit, and version acceptance are complete.

## Execution Check-In

### Reproduction Evidence

Before the fix, the focused unit test exposed the operator asymmetry:

```text
mvn -pl foggy-fsscript -Dtest=ComparisonOperatorTest test
```

Result: failed as expected, 4 tests run with 2 errors. The failing tests were:

- `lessThanShouldCompareNonNumberValuesLikeGreaterThan`: `ClassCastException` at `LT.java:25`
- `lessThanOrEqualShouldCompareNonNumberValuesLikeGreaterThanOrEqual`: `ClassCastException` at `LT_equal.java:25`

### Implementation Notes

- `LT` now evaluates operands as `Object`, delegates to `LT(Object, Object)`, and preserves the existing numeric/null comparison rule by treating `null` as `0` only when both operands are numeric-or-null.
- `LT_equal` now delegates to `LTE(Object, Object)` and reuses the same numeric/null helper from `LT`.
- Non-number operands now use `toString().compareTo(...)`, matching the fallback style already implemented by `GT` and `GT_equal`.
- Mixed number/string operands now follow the existing `GT` family behavior and compare through string fallback rather than failing with a numeric cast.

### Verification Evidence

| Scope | Command | Result |
|---|---|---|
| Focused test after fix | `mvn -pl foggy-fsscript -Dtest=ComparisonOperatorTest test` | Passed: 4 tests run, 0 failures, 0 errors. |
| Related expression tests | `mvn -pl foggy-fsscript "-Dtest=ComparisonOperatorTest,OperatorPrecedenceTest,InNotInExpTest" test` | Passed: 52 tests run, 0 failures, 0 errors. |
| Full module regression | `mvn -pl foggy-fsscript test` | Passed: 371 tests run, 0 failures, 0 errors, 0 skipped. |

## Execution Check-In Summary

- completed_work: Red test, implementation, focused verification, related verification, full module regression, and work item writeback completed.
- touched_code_paths: `LT.java`, `LT_equal.java`, `ComparisonOperatorTest.java`.
- self_check: passed; change is local to comparison operators and tests map to the failing behavior.
- test_status: pass.
- remaining_risks: no blocking implementation risk found before quality review.
- acceptance_readiness: signed-off.

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-26
- acceptance_record: docs/9.2.8/acceptance/version-signoff.md
- blocking_items: none
- follow_up_required: no
