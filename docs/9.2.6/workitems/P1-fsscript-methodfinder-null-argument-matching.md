---
type: bug
bug_source: code-review
version: 9.2.6
ticket: P1-fsscript-methodfinder-null-argument-matching
severity: major
status: ready-for-acceptance
reproduction_status: confirmed-by-code-review
reproduction_evidence: confirmed-by-red-unit-test
test_strategy: unit-test
automation_decision: required
owner: foggy-fsscript
owner_module: foggy-fsscript
created_at: 2026-06-25
updated_at: 2026-06-25
---

# P1 FSScript MethodFinder Null-Argument Matching

## Document Purpose

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track a FSScript reflection method-resolution bug where `null` arguments can cause later arguments to be skipped during matching.

## Background

The FSScript runtime resolves Java method calls through `MethodFinder`. During code review, `findMethod` and `autoFixArgsAndFindMethod` were found to use an index variable that is not advanced when the current argument is `null`.

That means a multi-argument call such as `(null, "wrong-type")` can match a method whose second parameter is not compatible with `"wrong-type"`, because the matcher repeatedly checks the earlier `null` argument instead of the later concrete argument.

## Problem Statement

Incorrect method selection shifts the failure from deterministic resolution time to invocation time, usually as `IllegalArgumentException: argument type mismatch`. In overloaded or reflective FSScript calls this can produce confusing runtime errors or choose the wrong candidate.

## Target Outcome

- `MethodFinder.findMethod` must advance the argument index for every parameter, including `null` arguments.
- `MethodFinder.autoFixArgsAndFindMethod` must use the same corrected index behavior.
- Valid calls containing `null` should still match compatible methods.
- Invalid later arguments must prevent a method match instead of failing during invocation.

## Touched Code Areas

- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/exp/MethodFinder.java`
- `foggy-fsscript/src/test/java/com/foggyframework/fsscript/exp/MethodFinderTest.java`

## Acceptance Criteria

- A focused unit test fails before the fix for a call where the first argument is `null` and a later argument has an incompatible type.
- The same focused unit test passes after the fix.
- Existing FSScript expression and import-bean method-call tests continue to pass.
- `mvn -pl foggy-fsscript test` passes.

## Constraints / Non-Goals

- No FSScript syntax change.
- No auth-code, security, permission, audit, or Runtime API behavior change.
- No broad reflection policy redesign.

## Progress Tracking

| Item | Status | Notes |
|---|---|---|
| Workitem recorded | done | 9.2.6 work item created. |
| Red test | done | `MethodFinderTest` failed before the fix for null-leading calls with incompatible later arguments and null-to-primitive matching. |
| Implementation | done | Fixed index advancement for every argument and rejected `null` for primitive parameters. |
| Verification | done | Focused, related, and full `foggy-fsscript` tests passed after the fix. |

## Experience Progress

experience: N/A

Reason: Backend/runtime-only reflection matching fix; no UI or manual workflow changes.

## Execution Checklist

- [x] Add failing unit test for null first argument plus incompatible later argument.
- [x] Add unit test for valid null argument matching.
- [x] Add null-to-primitive rejection tests.
- [x] Fix `findMethod`.
- [x] Fix `autoFixArgsAndFindMethod`.
- [x] Run focused test.
- [x] Run related FSScript expression tests.
- [x] Run full `foggy-fsscript` regression.
- [x] Record execution check-in and acceptance readiness.

## Execution Check-In

### Reproduction Evidence

Before the fix, the focused unit test exposed the resolver bug:

```text
mvn -pl foggy-fsscript -Dtest=MethodFinderTest test
```

Result: failed as expected, 5 tests run with 4 failures. The failures covered null-leading calls with incompatible later arguments and `null` matching primitive parameters.

### Implementation Notes

- `findMethod` now advances the argument index once per parameter, including `null` arguments.
- `autoFixArgsAndFindMethod` now uses the same corrected index behavior and writes converted map arguments back to the correct argument slot.
- Both matching paths reject `null` for primitive parameters instead of returning a method that cannot be invoked.

### Verification Evidence

| Scope | Command | Result |
|---|---|---|
| Focused test | `mvn -pl foggy-fsscript -Dtest=MethodFinderTest test` | Passed: 5 tests run, 0 failures. |
| Related expression/import-bean tests | `mvn -pl foggy-fsscript "-Dtest=MethodFinderTest,ImportBeanExpTest,ImportBeanExpFailureTest,FunctionExpTest,DotExpTest" test` | Passed: 18 tests run, 0 failures. |
| Full module regression | `mvn -pl foggy-fsscript test` | Passed: 367 tests run, 0 failures, 0 errors, 0 skipped. |

## Acceptance Readiness

status: ready-for-acceptance

Reason: Required unit reproduction, implementation fix, related regression, and full `foggy-fsscript` module regression are complete. This item is ready for quality/coverage review and signoff.
