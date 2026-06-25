---
acceptance_scope: feature
doc_role: feature_acceptance
doc_purpose: Sign off Runtime API model validation bundle cleanup for 9.2.2.
version: 9.2.2
target: P1-runtime-model-validation-bundle-cleanup
status: signed-off
decision: accepted
signed_off_by: codex
signed_off_at: 2026-06-25
reviewed_by: codex
blocking_items: []
follow_up_required: no
evidence_count: 5
---

# Feature Acceptance

## Background

- Version: 9.2.2
- Target: P1-runtime-model-validation-bundle-cleanup
- Owner: `foggy-runtime-api`, FSScript bundle runtime behavior
- Goal: 确保 `/models/validate` 注册的临时 validation bundle 在本次请求结束后释放，即使 `clearExisting=false`。
- Scope boundary: 不引入或修改安全分层、RBAC、用户权限、审计、轮换或 auth-code 合约。

## Acceptance Basis

- [version README] `docs/9.2.2/README.md`
- [feature workitem] `docs/9.2.2/workitems/P1-runtime-model-validation-bundle-cleanup.md`
- [implementation quality] `docs/9.2.2/quality/P1-runtime-model-validation-bundle-cleanup-implementation-quality.md`
- [coverage audit] `docs/9.2.2/coverage/P1-runtime-model-validation-bundle-cleanup-coverage-audit.md`

## Checklist

- [x] `clearExisting=false` 成功校验后清理本次 validation bundle。
- [x] `clearExisting=false` 失败校验后仍清理本次 validation bundle。
- [x] `clearExisting` 仍只表示是否在校验前清理已存在的同名 bundle。
- [x] Runtime API envelope、诊断错误和 auth-code gate 行为未变化。
- [x] Runtime API 与 FSScript 模块回归通过。
- [x] 体验验证明确标记 `N/A`。

## Evidence

- Test-first exposure:
  - 修复前 `mvn -pl foggy-runtime-api -Dtest=RuntimeCapabilitiesControllerEnabledTest#shouldCleanupValidationBundleWhenClearExistingIsFalse test` failed because `removeBundle("runtime-validation-dev")` was not invoked.
- Post-fix focused:
  - `mvn -pl foggy-runtime-api -Dtest=RuntimeCapabilitiesControllerEnabledTest#shouldCleanupValidationBundleWhenClearExistingIsFalse test`: 1 passed.
- Runtime API validation class:
  - `mvn -pl foggy-runtime-api -Dtest=RuntimeCapabilitiesControllerEnabledTest test`: 32 passed.
- Runtime API full regression:
  - `mvn -pl foggy-runtime-api test`: 42 passed.
- FSScript full regression:
  - `mvn -pl foggy-fsscript test`: 362 passed.

## Risks / Open Items

- Registry persistence hardening and any broader runtime management cleanup remain future work candidates.
- Auth-code remains the agreed lightweight management gate; no customer-facing security model was introduced.

## Failed Items

- none

## Final Decision

Decision: accepted.

The Runtime API model validation bundle cleanup meets its scoped acceptance criteria. The previously stateful `clearExisting=false` validation path is now temporary again, and success/failure cleanup behavior is locked by tests.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-25
- acceptance_record: docs/9.2.2/acceptance/P1-runtime-model-validation-bundle-cleanup-acceptance.md
- blocking_items: none
- follow_up_required: no
