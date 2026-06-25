---
audit_scope: feature
audit_mode: pre-acceptance-check
version: 9.2.3
target: P1-runtime-api-mutation-failure-consistency
status: reviewed
conclusion: ready-for-acceptance
reviewed_by: codex
reviewed_at: 2026-06-25
follow_up_required: no
---

# Test Coverage Audit

## Background

This audit maps the 9.2.3 Runtime API mutation failure consistency workitem to executable evidence. The covered risks are failed bundle update rollback and resource save batch partial-write prevention.

## Audit Basis

- Workitem: `docs/9.2.3/workitems/P1-runtime-api-mutation-failure-consistency.md`
- Quality gate: `docs/9.2.3/quality/P1-runtime-api-mutation-failure-consistency-implementation-quality.md`
- Main test suite: `RuntimeCapabilitiesControllerEnabledTest`

## Coverage Matrix

| Requirement / Risk | Priority | Evidence Type | Evidence | Status |
|---|---:|---|---|---|
| Failed update of an existing runtime-managed bundle must not lose the previous active runtime bundle | P1 | integration-test | `RuntimeCapabilitiesControllerEnabledTest#shouldRestoreExistingRuntimeManagedBundleWhenUpdateRegistrationFails` | covered |
| Failed bundle update must keep the existing registry record unchanged | P1 | integration-test | Same test checks bundle list still points to the old path after failed replacement registration | covered |
| Resource save batch with a later deterministic validation failure must not write earlier valid files | P1 | integration-test | `RuntimeCapabilitiesControllerEnabledTest#shouldRejectInvalidResourceSaveBatchWithoutPartialWrites` | covered |
| Existing Runtime API behavior remains compatible | P1 | regression-test | `mvn -pl foggy-runtime-api -Dtest=RuntimeCapabilitiesControllerEnabledTest test` and `mvn -pl foggy-runtime-api test` | covered |
| Auth-code gate contract remains unchanged | P1 | regression-test | `mvn -pl foggy-runtime-api test` includes existing auth-code gate tests | covered |

## Evidence Summary

| Command | Result |
|---|---|
| `mvn -pl foggy-runtime-api -Dtest=RuntimeCapabilitiesControllerEnabledTest#shouldRestoreExistingRuntimeManagedBundleWhenUpdateRegistrationFails+shouldRejectInvalidResourceSaveBatchWithoutPartialWrites test` before fix | failed, 2 expected failures exposed bundle rollback gap and resource partial-write gap |
| `mvn -pl foggy-runtime-api -Dtest=RuntimeCapabilitiesControllerEnabledTest#shouldRestoreExistingRuntimeManagedBundleWhenUpdateRegistrationFails+shouldRejectInvalidResourceSaveBatchWithoutPartialWrites test` after fix | passed, 2 tests |
| `mvn -pl foggy-runtime-api -Dtest=RuntimeCapabilitiesControllerEnabledTest test` | passed, 34 tests |
| `mvn -pl foggy-runtime-api test` | passed, 44 tests |

## Gaps

No blocking coverage gap is recorded for this P1 workitem.

Residual risks:

- Registry persistence failure handling and rollback semantics remain outside this workitem.
- Atomic write temporary-file cleanup behavior remains a non-blocking follow-up.
- Cross-module `foggy-fsscript` tests were not rerun because the changed implementation surface is limited to `foggy-runtime-api`.

## Conclusion

Conclusion: `ready-for-acceptance`.

The two selected P1 risks were exposed by failing tests before implementation, fixed in `foggy-runtime-api`, and verified through focused, class-level, and module-level Runtime API regression.
