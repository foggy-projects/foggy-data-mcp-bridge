---
doc_role: workitem
doc_purpose: Track P1 cleanup for Runtime API model validation bundle lifecycle.
version: 9.2.2
target: P1-runtime-model-validation-bundle-cleanup
status: signed-off
created_at: 2026-06-25
updated_at: 2026-06-25
owner_modules:
  - foggy-runtime-api
  - foggy-fsscript
priority: P1
---

# P1 Runtime Model Validation Bundle Cleanup

## Background

`POST /api/v1/models/validate` temporarily registers a FSScript external bundle named `runtime-validation[-namespace]` so TM/QM files in an arbitrary model directory can be loaded and checked.

The `clearExisting` request flag is documented as whether to clear an existing bundle with the same name before validation. The current implementation also uses it to decide whether the newly registered validation bundle is removed after validation. When `clearExisting=false`, a successful validation can leave the temporary validation bundle in the runtime context.

## Risk

- Validation becomes a stateful mutation even when the caller only asks for a check.
- Later validations can collide with or observe stale `runtime-validation[-namespace]` bundles.
- Model loader behavior can be affected by a previous validation directory.
- The issue is code lifecycle/resource cleanup, not a security-layering problem.

## Target Outcome

- `clearExisting=false` must only skip pre-validation removal of an already existing validation bundle.
- A bundle registered by the current validation request must always be removed in `finally`.
- Cleanup must run for both successful and failed validations.
- Existing response envelope and validation error behavior must stay unchanged.

## Test-First Plan

| Step | Expected Failing Evidence Before Fix | Target Fix |
|---|---|---|
| Change `clearExisting=false` test expectation | Existing implementation does not call `removeBundle("runtime-validation-dev")` | Remove the newly registered bundle regardless of `clearExisting` |
| Add/keep validation failure coverage | Cleanup remains visible when validation returns errors | Keep cleanup in `finally` and do not mask validation errors |
| Run Runtime API regression | Existing runtime endpoints remain compatible | No API contract expansion |

## Acceptance Criteria

- [x] `clearExisting=false` validation succeeds and still cleans up the newly registered validation bundle.
- [x] Failed validation still cleans up the newly registered validation bundle.
- [x] Runtime API focused test passes.
- [x] Runtime API full test suite passes.
- [x] Quality, coverage, and acceptance records are written under `docs/9.2.2`.

## Progress Tracking

| Item | Module | Status | Notes |
|---|---|---|---|
| Version docs | docs | done | 9.2.2 directory and initial workitem created. |
| Test-first exposure | foggy-runtime-api | done | Updated `RuntimeCapabilitiesControllerEnabledTest`; the first changed expectation failed before the code fix because `removeBundle` was not called. |
| Runtime cleanup fix | foggy-runtime-api | done | `RuntimeModelsController#validateModelDirectory` now removes any validation bundle registered by the current request in `finally`. |
| Verification | foggy-runtime-api, foggy-fsscript | done | Runtime API focused/full tests and FSScript module regression passed. |

## Test Evidence

| Evidence | Result |
|---|---|
| Pre-fix focused exposure | `mvn -pl foggy-runtime-api -Dtest=RuntimeCapabilitiesControllerEnabledTest#shouldCleanupValidationBundleWhenClearExistingIsFalse test` failed because `removeBundle("runtime-validation-dev")` was not invoked. |
| Post-fix focused exposure | Same command passed: 1 test, 0 failures. |
| Validation lifecycle class regression | `mvn -pl foggy-runtime-api -Dtest=RuntimeCapabilitiesControllerEnabledTest test`: 32 tests, 0 failures. |
| Runtime API full regression | `mvn -pl foggy-runtime-api test`: 42 tests, 0 failures. |
| FSScript full regression | `mvn -pl foggy-fsscript test`: 362 tests, 0 failures. |

## Execution Check-In

- completed_work: `/models/validate` no longer leaves the current request's temporary validation bundle behind when `clearExisting=false`.
- tests_added_or_updated:
  - `RuntimeCapabilitiesControllerEnabledTest#shouldCleanupValidationBundleWhenClearExistingIsFalse`
  - `RuntimeCapabilitiesControllerEnabledTest#shouldCleanupValidationBundleWhenClearExistingIsFalseAndValidationFails`
- changed_code:
  - `RuntimeModelsController#validateModelDirectory`
- compatibility: response envelope, validation diagnostics, auth-code gate, and FSScript syntax behavior unchanged.
- non_goals_preserved: no RBAC, user permission, audit, rotation, or security-layering change.

## Quality / Coverage / Acceptance

- quality_record: `docs/9.2.2/quality/P1-runtime-model-validation-bundle-cleanup-implementation-quality.md`
- coverage_record: `docs/9.2.2/coverage/P1-runtime-model-validation-bundle-cleanup-coverage-audit.md`
- acceptance_record: `docs/9.2.2/acceptance/P1-runtime-model-validation-bundle-cleanup-acceptance.md`
- acceptance_status: signed-off

## Non-Goals

- No security model changes.
- No auth-code contract changes.
- No RBAC, user permission, audit, or rotation implementation.
- No FSScript syntax or TM/QM format changes.
