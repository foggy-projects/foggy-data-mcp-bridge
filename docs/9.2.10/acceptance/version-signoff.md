---
acceptance_scope: version
version: 9.2.10
target: FSScript BitwiseAnd Operand Robustness
doc_role: acceptance-record
doc_purpose: Record formal version acceptance and signoff decision for 9.2.10.
status: signed-off
decision: accepted
signed_off_by: codex
signed_off_at: 2026-06-26
reviewed_by: codex
blocking_items: []
follow_up_required: no
evidence_count: 7
---

# Version Acceptance

## Document Purpose

- doc_type: acceptance
- intended_for: signoff-owner / reviewer / root-controller
- purpose: Record the formal 9.2.10 acceptance decision and evidence summary.

## Background

- Version: 9.2.10
- Scope: FSScript `&` runtime robustness for non-number operands.
- Goal: Ensure bitwise-and no longer fails with `ClassCastException` for non-number operands while preserving existing numeric and null fallback behavior.
- Boundary: No FSScript syntax change, public Java API change, Runtime API behavior change, auth-code change, RBAC change, permission model change, audit change, or security-layer redesign.

## Acceptance Basis

- `docs/9.2.10/README.md`
- `docs/9.2.10/workitems/P1-fsscript-bitwise-and-operand-robustness.md`
- `docs/9.2.10/quality/P1-fsscript-bitwise-and-operand-robustness-implementation-quality.md`
- `docs/9.2.10/coverage/P1-fsscript-bitwise-and-operand-robustness-coverage-audit.md`
- `foggy-fsscript/src/main/java/com/foggyframework/fsscript/fun/BitwiseAnd.java`
- `foggy-fsscript/src/test/java/com/foggyframework/fsscript/exp/ArithmeticOperatorTest.java`

## Module Summary

| Module | Owner | Status | Acceptance Record | Notes |
|---|---|---|---|---|
| `foggy-fsscript` | codex | signed-off | `docs/9.2.10/acceptance/version-signoff.md` | `&` non-number operand robustness fix accepted with focused red-test evidence and full module regression. |

## Checklist

- [x] Version goal is documented in `docs/9.2.10/README.md`.
- [x] Work item records bug source, target outcome, constraints, execution check-in, and verification evidence.
- [x] Red-test evidence exists for the original left and right operand runtime failures.
- [x] Implementation quality gate is reviewed and has no blocking item.
- [x] Coverage audit maps every acceptance item to unit or module-regression evidence.
- [x] Focused post-fix test passed on 2026-06-26.
- [x] Related expression regression passed on 2026-06-26.
- [x] Full `foggy-fsscript` regression passed on 2026-06-26.
- [x] Experience verification is `N/A` because this is backend/runtime-only utility behavior with no UI surface.
- [x] Guardrails were preserved: no syntax, API, Runtime API, auth-code, RBAC, permission, audit, or security-layer change.

## Evidence

- Work item:
  - `docs/9.2.10/workitems/P1-fsscript-bitwise-and-operand-robustness.md`
- Quality:
  - `docs/9.2.10/quality/P1-fsscript-bitwise-and-operand-robustness-implementation-quality.md`
- Coverage:
  - `docs/9.2.10/coverage/P1-fsscript-bitwise-and-operand-robustness-coverage-audit.md`
- Test:
  - Before fix: `mvn -pl foggy-fsscript -Dtest=ArithmeticOperatorTest test` failed as expected, 9 tests run with 2 errors.
  - After fix: `mvn -pl foggy-fsscript -Dtest=ArithmeticOperatorTest test` passed, 9 tests run, 0 failures, 0 errors, 0 skipped.
  - After fix: `mvn -pl foggy-fsscript "-Dtest=ArithmeticOperatorTest,ComparisonOperatorTest,OperatorPrecedenceTest,InNotInExpTest" test` passed, 61 tests run, 0 failures, 0 errors, 0 skipped.
  - After fix: `mvn -pl foggy-fsscript test` passed, 380 tests run, 0 failures, 0 errors, 0 skipped.
- Experience:
  - N/A. No UI page, form, navigation, browser interaction, external user workflow, or visual behavior was changed.

## Risks / Open Items

- No blocking item.
- No required follow-up for 9.2.10.
- Non-blocking future cleanup: evaluate `%` right-denominator behavior and increment/decrement operand casts as separate work items.

## Final Decision

Decision: `accepted`.

9.2.10 satisfies the documented FSScript correctness scope. The original bug was exposed by a focused red test, the fix is constrained to `BitwiseAnd`, existing numeric and null fallback behavior remains protected by regression tests, full `foggy-fsscript` regression passes, and no blocking quality or coverage gap remains.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-26
- acceptance_record: docs/9.2.10/acceptance/version-signoff.md
- blocking_items: none
- follow_up_required: no
