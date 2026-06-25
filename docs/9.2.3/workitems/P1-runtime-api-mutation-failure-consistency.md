---
doc_role: workitem
doc_purpose: Track P1 Runtime API mutation failure consistency fixes.
version: 9.2.3
target: P1-runtime-api-mutation-failure-consistency
status: ready-for-acceptance
created_at: 2026-06-25
updated_at: 2026-06-25
owner_modules:
  - foggy-runtime-api
priority: P1
source_type: code-review
reproduction_status: confirmed-by-failing-test
test_strategy: integration-test
automation_decision: required
---

# P1 Runtime API Mutation Failure Consistency

## Background

Runtime API management endpoints mutate in-memory FSScript bundle state and filesystem model resources. Current failure paths include operations that can report a failed request after partially changing runtime state or files on disk.

This is a code correctness and lifecycle consistency issue, not a security layering issue.

## Risks

1. Bundle update rollback gap:
   - `PUT /api/v1/bundles/{name}` removes an existing runtime-managed bundle before registering the replacement.
   - If replacement registration fails, the response is a failure but the previous runtime bundle has already been removed.

2. Resource save partial commit:
   - `POST /api/v1/resources/save` validates and writes files inside the same loop.
   - If an earlier file is valid and a later file is invalid or has a conflict, the response is a failure but earlier files may already be written.

## Target Outcome

- Failed runtime-managed bundle updates must not lose the previously active bundle.
- Failed resource save batches caused by deterministic request validation or conflict checks must not write earlier files.
- Existing Runtime API envelopes, endpoint paths, and auth-code gate behavior must stay unchanged.
- P2 persistence-temp cleanup and atomic-move compatibility remain documented follow-ups unless they block this iteration.

## Test-First Plan

| Step | Expected Failing Evidence Before Fix | Target Fix |
|---|---|---|
| Add bundle update rollback test | Updating an existing bundle with a failing replacement registration does not restore the old bundle | Restore the removed existing runtime bundle when replacement registration fails |
| Add resource batch prevalidation test | Saving a batch with first valid file and later invalid file leaves the first file on disk | Prevalidate the complete request before writing any file |
| Run focused Runtime API regression | New tests fail before code fix and pass after code fix | Keep behavior scoped to mutation consistency |
| Run Runtime API full regression | Existing endpoints remain compatible | No API contract expansion |

## Acceptance Criteria

- [x] Failed update of an existing runtime-managed bundle attempts to restore the previous runtime bundle.
- [x] Failed update keeps the existing registry record unchanged.
- [x] Resource save batch with later deterministic validation failure writes no earlier files.
- [x] Runtime API focused test passes.
- [x] Runtime API full test suite passes.
- [x] Quality and coverage records are written under `docs/9.2.3`.

## Progress Tracking

| Item | Module | Status | Notes |
|---|---|---|---|
| Version docs | docs | done | 9.2.3 directory and initial workitem created. |
| Test-first exposure | foggy-runtime-api | done | Added failing tests for bundle rollback and resource batch prevalidation. |
| Runtime consistency fixes | foggy-runtime-api | done | Restores old bundle on failed replacement registration; prevalidates resource save batch before writing. |
| Verification | foggy-runtime-api | done | Focused, class-level, and module Runtime API tests passed. |
| Quality / coverage writeback | docs | done | Quality gate and coverage audit written. |

## Test Evidence

| Evidence | Result |
|---|---|
| Pre-fix bundle rollback exposure | `mvn -pl foggy-runtime-api -Dtest=RuntimeCapabilitiesControllerEnabledTest#shouldRestoreExistingRuntimeManagedBundleWhenUpdateRegistrationFails+shouldRejectInvalidResourceSaveBatchWithoutPartialWrites test` failed before fix. Bundle rollback assertion expected restored old `addExternalBundle` call twice but saw only one call. |
| Pre-fix resource batch exposure | Same focused command failed before fix. Resource batch assertion expected `model/Partial.tm` to be absent, but the first valid file was written before the later invalid file failed. |
| Post-fix focused Runtime API regression | `mvn -pl foggy-runtime-api -Dtest=RuntimeCapabilitiesControllerEnabledTest#shouldRestoreExistingRuntimeManagedBundleWhenUpdateRegistrationFails+shouldRejectInvalidResourceSaveBatchWithoutPartialWrites test`: passed, 2 tests. |
| Runtime API class regression | `mvn -pl foggy-runtime-api -Dtest=RuntimeCapabilitiesControllerEnabledTest test`: passed, 34 tests. |
| Runtime API full regression | `mvn -pl foggy-runtime-api test`: passed, 44 tests. |

## Execution Check-In

- completed_work: bundle update rollback restoration and resource save batch prevalidation implemented.
- tests_added_or_updated: `RuntimeCapabilitiesControllerEnabledTest#shouldRestoreExistingRuntimeManagedBundleWhenUpdateRegistrationFails`, `RuntimeCapabilitiesControllerEnabledTest#shouldRejectInvalidResourceSaveBatchWithoutPartialWrites`.
- changed_code: `RuntimeBundlesController`, `RuntimeResourcesController`.
- compatibility: Runtime API response envelope, endpoint paths, error codes for covered failures, and auth-code gate behavior unchanged.
- non_goals_preserved: no RBAC, user permission, audit, rotation, or security-layering change.

## Quality / Coverage

- quality_record: `docs/9.2.3/quality/P1-runtime-api-mutation-failure-consistency-implementation-quality.md`
- coverage_record: `docs/9.2.3/coverage/P1-runtime-api-mutation-failure-consistency-coverage-audit.md`
- readiness: ready-for-acceptance

## Non-Goals

- No security model changes.
- No auth-code contract changes.
- No RBAC, user permission, audit, or rotation implementation.
- No FSScript syntax, TM/QM format, datasource credential handling, or SQL probe policy changes.
