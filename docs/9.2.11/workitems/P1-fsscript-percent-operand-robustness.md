---
type: bug
bug_source: code-review
version: 9.2.11
ticket: P1-fsscript-percent-operand-robustness
severity: major
status: signed-off
reproduction_status: confirmed-by-code-review
reproduction_evidence: red-unit-test
test_strategy: unit-test
automation_decision: required
owner: foggy-fsscript
owner_module: foggy-fsscript
created_at: 2026-06-26
updated_at: 2026-06-26
---

# P1 FSScript Percent Operand Robustness

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track a FSScript `%` operator bug where invalid or zero right operands leak Java `ArithmeticException`.

## Background

During the FSScript code-risk follow-up after 9.2.10, `PERCENT` was found to convert non-number operands to `0` and then execute Java integer modulo directly. This already implies a tolerant operator style, but the right operand can still become `0`, which causes `ArithmeticException: / by zero`.

## Problem Statement

The `%` operator has an unsafe right operand path:

- Right operand null: converted to `0`, then `% 0` throws `ArithmeticException`.
- Right operand non-number: converted to `0`, then `% 0` throws `ArithmeticException`.
- Right operand numeric zero: `% 0` throws `ArithmeticException`.
- Numeric behavior and existing left fallback behavior are not explicitly protected by focused tests.

## Target Outcome

- `%` no longer throws `ArithmeticException` for right null, non-number, or zero operands.
- Numeric modulo behavior remains unchanged:
  - `5 % 2` returns `1`.
- Existing left fallback behavior remains unchanged:
  - `null % 2` returns `0`.
  - `'x' % 2` returns `0`.
- Clarified right fallback behavior:
  - `5 % null` returns `0`.
  - `5 % 'x'` returns `0`.
  - `5 % 0` returns `0`.
- The original bug is exposed by a focused failing unit test before the fix.

## Touched Code Areas

- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/fun/PERCENT.java`
- `foggy-fsscript/src/test/java/com/foggyframework/fsscript/exp/ArithmeticOperatorTest.java`

## Acceptance Criteria

- A focused unit test fails before the fix for invalid or zero right `%` operands.
- The same focused unit test passes after the fix.
- Numeric and left fallback `%` behavior remains covered and unchanged.
- `mvn -pl foggy-fsscript test` passes.

## Constraints / Non-Goals

- No FSScript syntax change.
- No Runtime API security, auth-code, RBAC, audit, or permission-model change.
- No broad arithmetic-operator redesign.
- No behavior change for `/`, `&`, `+`, `-`, `*`, logical operators, or increment/decrement operators in this batch.

## Progress Tracking

| Item | Status | Notes |
|---|---|---|
| Workitem recorded | done | 9.2.11 work item created. |
| Red test | done | `ArithmeticOperatorTest` failed before the fix for right null, non-number, and zero operands. |
| Implementation | done | `PERCENT` now guards invalid or zero right operands before modulo. |
| Verification | done | Focused, related, and full `foggy-fsscript` regressions passed. |
| Quality | done | Implementation quality gate completed with no blocker. |
| Coverage | done | Coverage audit completed with no blocker. |
| Acceptance | done | 9.2.11 version acceptance signed off. |

## Experience Progress

experience: N/A

Reason: Backend/runtime-only modulo operator fix; no UI or manual workflow changes.

## Execution Checklist

- [x] Record 9.2.11 work item.
- [x] Add failing unit test for right null operand.
- [x] Add failing unit test for right non-number operand.
- [x] Add failing unit test for right zero operand.
- [x] Add numeric and left fallback compatibility coverage.
- [x] Fix `PERCENT`.
- [x] Run focused test.
- [x] Run related expression tests.
- [x] Run full `foggy-fsscript` regression.
- [x] Record execution check-in and acceptance readiness.

## Execution Check-In

| Scope | Evidence | Result |
|---|---|---|
| Red test before fix | `mvn -pl foggy-fsscript -Dtest=ArithmeticOperatorTest test` | Failed as expected: 14 tests run, 3 errors. Right null, non-number, and zero operands all failed with `ArithmeticException: / by zero` at `PERCENT.java:37`. |
| Focused test after fix | `mvn -pl foggy-fsscript -Dtest=ArithmeticOperatorTest test` | Passed: 14 tests run, 0 failures, 0 errors, 0 skipped. |
| Related expression tests | `mvn -pl foggy-fsscript "-Dtest=ArithmeticOperatorTest,ComparisonOperatorTest,OperatorPrecedenceTest,InNotInExpTest" test` | Passed: 66 tests run, 0 failures, 0 errors, 0 skipped. |
| Full module regression | `mvn -pl foggy-fsscript test` | Passed: 385 tests run, 0 failures, 0 errors, 0 skipped. |

## Acceptance Readiness

status: signed-off

Reason: Red-test evidence, implementation, verification, quality review, coverage audit, and acceptance are complete with no blocking item.
