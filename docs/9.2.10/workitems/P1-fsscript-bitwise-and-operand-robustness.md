---
type: bug
bug_source: code-review
version: 9.2.10
ticket: P1-fsscript-bitwise-and-operand-robustness
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

# P1 FSScript BitwiseAnd Operand Robustness

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track a FSScript bitwise `&` operator bug where `BitwiseAnd` directly casts operands to `Number` and fails for non-number values.

## Background

During the FSScript code-risk follow-up after 9.2.9, `BitwiseAnd` was found to cast both operands directly to `Number` before applying its existing null handling. This creates the same runtime failure class that was fixed for division: scripts parse successfully, but evaluation can fail with `ClassCastException`.

## Problem Statement

The bitwise `&` operator has unsafe operand handling:

- Left operand non-number: `ClassCastException` before the existing null rule can apply.
- Right operand non-number: `ClassCastException` before the existing null rule can apply.
- Numeric and null behavior is not explicitly protected by focused tests.

## Target Outcome

- `&` no longer throws `ClassCastException` for non-number operands.
- Numeric bitwise-and behavior remains unchanged.
- Existing null behavior remains unchanged:
  - `null & 1` returns `0`.
  - `1 & null` returns `0`.
  - `null & null` returns `0`.
- Non-number operands follow the existing missing/null fallback and return `0` when either side is not numeric.
- The original bug is exposed by a focused failing unit test before the fix.

## Touched Code Areas

- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/fun/BitwiseAnd.java`
- `foggy-fsscript/src/test/java/com/foggyframework/fsscript/exp/ArithmeticOperatorTest.java`

## Acceptance Criteria

- A focused unit test fails before the fix for non-number `&` operands.
- The same focused unit test passes after the fix.
- Numeric and null `&` behavior remains covered and unchanged.
- `mvn -pl foggy-fsscript test` passes.

## Constraints / Non-Goals

- No FSScript syntax change.
- No Runtime API security, auth-code, RBAC, audit, or permission-model change.
- No broad bitwise-operator redesign.
- No behavior change for `/`, `%`, `+`, `-`, `*`, or increment/decrement operators in this batch.
- No change to logical `&&`.

## Progress Tracking

| Item | Status | Notes |
|---|---|---|
| Workitem recorded | done | 9.2.10 work item created. |
| Red test | done | `ArithmeticOperatorTest` failed before the fix for non-number left and right operands. |
| Implementation | done | `BitwiseAnd` now resolves operands through a type-safe `Number` helper before applying existing bitwise-and rules. |
| Verification | done | Focused, related, and full `foggy-fsscript` regressions passed after the fix. |
| Quality | done | Implementation quality gate reviewed with no blocking item. |
| Coverage | done | Coverage audit found no blocking evidence gap. |
| Acceptance | done | 9.2.10 version acceptance signed off. |

## Experience Progress

experience: N/A

Reason: Backend/runtime-only bitwise operator fix; no UI or manual workflow changes.

## Execution Checklist

- [x] Record 9.2.10 work item.
- [x] Add failing unit test for non-number left operand.
- [x] Add failing unit test for non-number right operand.
- [x] Add numeric/null bitwise-and compatibility coverage.
- [x] Fix `BitwiseAnd`.
- [x] Run focused test.
- [x] Run related expression tests.
- [x] Run full `foggy-fsscript` regression.
- [x] Record execution check-in and acceptance readiness.

## Acceptance Readiness

status: signed-off

Reason: Required red-test evidence, implementation fix, focused regression, related regression, full `foggy-fsscript` regression, implementation quality review, coverage audit, and version acceptance are complete.

## Execution Check-In

### Reproduction Evidence

Before the fix, the focused unit test exposed the bitwise-and operand cast bug:

```text
mvn -pl foggy-fsscript -Dtest=ArithmeticOperatorTest test
```

Result: failed as expected, 9 tests run with 2 errors. The failing tests were:

- `bitwiseAndShouldTreatNonNumberLeftOperandLikeMissingOperand`: `ClassCastException` at `BitwiseAnd.java:19`
- `bitwiseAndShouldTreatNonNumberRightOperandLikeMissingOperand`: `ClassCastException` at `BitwiseAnd.java:20`

### Implementation Notes

- `BitwiseAnd` now evaluates both operands and converts them with `asNumber(Object)` instead of directly casting.
- Non-number operands now follow the existing missing/null fallback and return `0` when either side is not numeric.
- Numeric bitwise-and behavior and existing null fallback remain unchanged.
- Obsolete commented boolean-and implementation notes were removed from `BitwiseAnd`.
- No other arithmetic, bitwise, logical, parser, syntax, Runtime API, auth-code, RBAC, permission, or audit behavior changed in this batch.

### Verification Evidence

| Scope | Command | Result |
|---|---|---|
| Focused test after fix | `mvn -pl foggy-fsscript -Dtest=ArithmeticOperatorTest test` | Passed: 9 tests run, 0 failures, 0 errors. |
| Related expression tests | `mvn -pl foggy-fsscript "-Dtest=ArithmeticOperatorTest,ComparisonOperatorTest,OperatorPrecedenceTest,InNotInExpTest" test` | Passed: 61 tests run, 0 failures, 0 errors. |
| Full module regression | `mvn -pl foggy-fsscript test` | Passed: 380 tests run, 0 failures, 0 errors, 0 skipped. |

## Execution Check-In Summary

- completed_work: Red test, implementation, focused verification, related verification, full module regression, and work item writeback completed.
- touched_code_paths: `BitwiseAnd.java`, `ArithmeticOperatorTest.java`.
- self_check: passed; change is local to the bitwise-and operator and tests map to both the failing behavior and compatibility behavior.
- test_status: pass.
- remaining_risks: no blocking implementation risk found before quality review.
- acceptance_readiness: signed-off.

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-26
- acceptance_record: docs/9.2.10/acceptance/version-signoff.md
- blocking_items: none
- follow_up_required: no
