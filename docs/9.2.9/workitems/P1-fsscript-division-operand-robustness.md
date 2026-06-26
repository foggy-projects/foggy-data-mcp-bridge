---
type: bug
bug_source: code-review
version: 9.2.9
ticket: P1-fsscript-division-operand-robustness
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

# P1 FSScript Division Operand Robustness

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track a FSScript division-operator bug where `/` directly casts operands to `Number` and fails for non-number values.

## Background

During the FSScript code-risk review, `DIVISION` was found to cast both operands directly to `Number` before applying its existing null handling. Other arithmetic operators such as `Multiply` and `Reduce` evaluate operands as `Object` and tolerate non-number values by treating them as zero-like operands in their local arithmetic rules.

`DIVISION` therefore has a deterministic runtime failure path for scripts such as `'x' / 2` or `2 / 'x'`: they parse successfully, but evaluation throws `ClassCastException` instead of returning through the operator's scripted runtime semantics.

## Problem Statement

The division operator has unsafe operand handling:

- Left operand non-number: `ClassCastException` before the existing left-null rule can apply.
- Right operand non-number: `ClassCastException` before the existing right-null `Double.NaN` rule can apply.
- Numeric/null/zero behavior is not explicitly protected by focused tests.

## Target Outcome

- `/` no longer throws `ClassCastException` for non-number operands.
- Numeric division behavior remains unchanged.
- Existing null behavior remains unchanged:
  - `null / 2` returns `0.0`.
  - `2 / null` returns `Double.NaN`.
  - `null / null` returns `0.0` because the existing left-null rule wins first.
- Existing numeric zero-division behavior remains unchanged:
  - `2 / 0` returns positive infinity.
- The original bug is exposed by a focused failing unit test before the fix.

## Touched Code Areas

- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/fun/DIVISION.java`
- `foggy-fsscript/src/test/java/com/foggyframework/fsscript/exp/ArithmeticOperatorTest.java`

## Acceptance Criteria

- A focused unit test fails before the fix for non-number division operands.
- The same focused unit test passes after the fix.
- Numeric, null, and zero-division behavior remains covered and unchanged.
- `mvn -pl foggy-fsscript test` passes.

## Constraints / Non-Goals

- No FSScript syntax change.
- No Runtime API security, auth-code, RBAC, audit, or permission-model change.
- No broad arithmetic-operator redesign.
- No behavior change for `+`, `-`, `*`, `%`, or bitwise operators in this batch.
- No change to Java double zero-division compatibility.

## Progress Tracking

| Item | Status | Notes |
|---|---|---|
| Workitem recorded | done | 9.2.9 work item created. |
| Red test | done | `ArithmeticOperatorTest` failed before the fix for non-number left and right operands. |
| Implementation | done | `DIVISION` now resolves operands through a type-safe `Number` helper before applying existing division rules. |
| Verification | done | Focused, related, and full `foggy-fsscript` regressions passed after the fix. |
| Quality | done | Implementation quality gate reviewed with no blocking item. |
| Coverage | done | Coverage audit found no blocking evidence gap. |
| Acceptance | done | 9.2.9 version acceptance signed off. |

## Experience Progress

experience: N/A

Reason: Backend/runtime-only arithmetic operator fix; no UI or manual workflow changes.

## Execution Checklist

- [x] Record 9.2.9 work item.
- [x] Add failing unit test for non-number left operand.
- [x] Add failing unit test for non-number right operand.
- [x] Add numeric/null/zero division compatibility coverage.
- [x] Fix `DIVISION`.
- [x] Run focused test.
- [x] Run related expression tests.
- [x] Run full `foggy-fsscript` regression.
- [x] Record execution check-in and acceptance readiness.

## Acceptance Readiness

status: signed-off

Reason: Required red-test evidence, implementation fix, focused regression, related regression, full `foggy-fsscript` regression, implementation quality review, coverage audit, and version acceptance are complete.

## Execution Check-In

### Reproduction Evidence

Before the fix, the focused unit test exposed the division operand cast bug:

```text
mvn -pl foggy-fsscript -Dtest=ArithmeticOperatorTest test
```

Result: failed as expected, 5 tests run with 2 errors. The failing tests were:

- `divisionShouldTreatNonNumberLeftOperandLikeMissingLeftOperand`: `ClassCastException` at `DIVISION.java:17`
- `divisionShouldTreatNonNumberRightOperandLikeMissingRightOperand`: `ClassCastException` at `DIVISION.java:18`

### Implementation Notes

- `DIVISION` now evaluates both operands and converts them with `asNumber(Object)` instead of directly casting.
- Non-number left operands now follow the existing left-missing rule and return `0.0`.
- Non-number right operands now follow the existing right-missing rule and return `Double.NaN`.
- Numeric division, null handling, and Java double zero-division compatibility remain unchanged.
- No other arithmetic operator behavior changed in this batch.

### Verification Evidence

| Scope | Command | Result |
|---|---|---|
| Focused test after fix | `mvn -pl foggy-fsscript -Dtest=ArithmeticOperatorTest test` | Passed: 5 tests run, 0 failures, 0 errors. |
| Related expression tests | `mvn -pl foggy-fsscript "-Dtest=ArithmeticOperatorTest,ComparisonOperatorTest,OperatorPrecedenceTest,InNotInExpTest" test` | Passed: 57 tests run, 0 failures, 0 errors. |
| Full module regression | `mvn -pl foggy-fsscript test` | Passed: 376 tests run, 0 failures, 0 errors, 0 skipped. |

## Execution Check-In Summary

- completed_work: Red test, implementation, focused verification, related verification, full module regression, and work item writeback completed.
- touched_code_paths: `DIVISION.java`, `ArithmeticOperatorTest.java`.
- self_check: passed; change is local to the division operator and tests map to both the failing behavior and compatibility behavior.
- test_status: pass.
- remaining_risks: no blocking implementation risk found before quality review.
- acceptance_readiness: signed-off.

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-26
- acceptance_record: docs/9.2.9/acceptance/version-signoff.md
- blocking_items: none
- follow_up_required: no
