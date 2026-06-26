---
doc_role: version_followup_plan
doc_purpose: Track 9.2.10 FSScript bitwise-operator robustness follow-up.
version: 9.2.10
status: signed-off
created_at: 2026-06-26
updated_at: 2026-06-26
---

# 9.2.10 FSScript BitwiseAnd Operand Robustness Follow-Up

## Document Purpose

- doc_type: version-summary
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track the next non-security FSScript code-risk cleanup after the 9.2.9 division-operator signoff.

## Scope

9.2.10 continues the FSScript code-risk mainline. It focuses on deterministic bitwise runtime risks that can be exposed by focused unit tests before fixing.

## Work Items

| Item | Doc | Status | Owner Module | Summary |
|---|---|---|---|---|
| BitwiseAnd operand robustness | `workitems/P1-fsscript-bitwise-and-operand-robustness.md` | signed-off | `foggy-fsscript` | Prevent `&` from failing with `ClassCastException` when operands are non-number values, while preserving existing numeric and null behavior. |

## Guardrails

- No Runtime API security, auth-code, RBAC, audit, or permission-model changes.
- No FSScript syntax change.
- No broad arithmetic or bitwise semantics redesign.
- Preserve existing `&` numeric behavior.
- Preserve existing `&` null behavior: when either operand is missing, the result is `0`.
- Prefer test-first exposure for each selected code risk.

## Progress Summary

- development: completed
- testing: completed
- quality: completed
- coverage: completed
- acceptance: signed-off
- experience: N/A, backend/runtime-only work.

## Verification Summary

| Scope | Command | Result |
|---|---|---|
| Red test before fix | `mvn -pl foggy-fsscript -Dtest=ArithmeticOperatorTest test` | Failed as expected: 9 tests run, 2 errors. |
| Focused test after fix | `mvn -pl foggy-fsscript -Dtest=ArithmeticOperatorTest test` | Passed: 9 tests run, 0 failures, 0 errors. |
| Related expression tests | `mvn -pl foggy-fsscript "-Dtest=ArithmeticOperatorTest,ComparisonOperatorTest,OperatorPrecedenceTest,InNotInExpTest" test` | Passed: 61 tests run, 0 failures, 0 errors. |
| Full module regression | `mvn -pl foggy-fsscript test` | Passed: 380 tests run, 0 failures, 0 errors, 0 skipped. |

## Signoff Records

| Record | Path | Status |
|---|---|---|
| Implementation quality gate | `quality/P1-fsscript-bitwise-and-operand-robustness-implementation-quality.md` | reviewed, ready-for-coverage-audit |
| Test coverage audit | `coverage/P1-fsscript-bitwise-and-operand-robustness-coverage-audit.md` | reviewed, ready-for-acceptance |
| Version acceptance | `acceptance/version-signoff.md` | signed-off, accepted |

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-26
- acceptance_record: docs/9.2.10/acceptance/version-signoff.md
- blocking_items: none
- follow_up_required: no
