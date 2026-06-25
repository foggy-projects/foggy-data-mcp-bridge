---
doc_role: version_followup_plan
doc_purpose: Track 9.2.6 FSScript runtime correctness follow-up.
version: 9.2.6
status: ready-for-acceptance
created_at: 2026-06-25
updated_at: 2026-06-25
---

# 9.2.6 FSScript Runtime Correctness Follow-Up

## Document Purpose

- doc_type: version-summary
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: Track the next code-level FSScript risk cleanup after the 9.2.1 runtime stability signoff and later Runtime API lifecycle follow-ups.

## Scope

9.2.6 continues the non-security FSScript mainline. It focuses on deterministic runtime correctness risks that can be exposed by focused unit tests before fixing.

## Work Items

| Item | Doc | Status | Owner Module | Summary |
|---|---|---|---|---|
| MethodFinder null-argument matching | `workitems/P1-fsscript-methodfinder-null-argument-matching.md` | ready-for-acceptance | `foggy-fsscript` | Fix multi-argument reflection method matching when one or more arguments are `null`. |

## Guardrails

- No Runtime API security, auth-code, RBAC, audit, or permission-model changes.
- No FSScript syntax change.
- No public Java API change unless explicitly documented in the work item.
- Prefer test-first exposure for each selected code risk.

## Progress Summary

- development: completed
- testing: completed
- experience: N/A, backend/runtime-only work.

## Verification Summary

| Scope | Command | Result |
|---|---|---|
| Red test before fix | `mvn -pl foggy-fsscript -Dtest=MethodFinderTest test` | Failed as expected: 5 tests run, 4 failures. |
| Focused test after fix | `mvn -pl foggy-fsscript -Dtest=MethodFinderTest test` | Passed: 5 tests run, 0 failures. |
| Related expression/import-bean tests | `mvn -pl foggy-fsscript "-Dtest=MethodFinderTest,ImportBeanExpTest,ImportBeanExpFailureTest,FunctionExpTest,DotExpTest" test` | Passed: 18 tests run, 0 failures. |
| Full module regression | `mvn -pl foggy-fsscript test` | Passed: 367 tests run, 0 failures, 0 errors, 0 skipped. |
