---
acceptance_scope: feature
doc_role: feature_acceptance
doc_purpose: Sign off Runtime API auth-code technical coverage closure for 9.2.1.
version: 9.2.1
target: P1-runtime-api-auth-code-technical-closure
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

- Version: 9.2.1
- Target: P1-runtime-api-auth-code-technical-closure
- Owner: `foggy-runtime-api`
- Goal: 对 Runtime API auth-code gate 做技术覆盖收口，补齐 datasource test 管理探测入口，并用测试锁定管理面覆盖清单与非目标边界。
- Scope boundary: 不引入安全分层、RBAC、用户权限、轮换、审计或查询读取鉴权。

## Acceptance Basis

- [version README] `docs/9.2.1/README.md`
- [feature workitem] `docs/9.2.1/workitems/P1-runtime-api-auth-code-technical-closure.md`
- [previous auth-code gate] `docs/9.2.1/workitems/P1-runtime-api-auth-code-management-gate.md`

## Checklist

- [x] Runtime API 管理面接口已盘点并分类。
- [x] `POST /api/v1/datasources/{name}/test` 已纳入 auth-code gate。
- [x] 授权码契约保持 `X-Foggy-Runtime-Code` 与 `Authorization: Bearer <code>`。
- [x] 管理面覆盖清单已有单元测试锁定。
- [x] 查询、SQL、compose、fsscript 执行和只读/导出入口未被本次管理 gate 扩大拦截。
- [x] capabilities warning 和 dev-guide 文档已同步。
- [x] 体验验证已明确标记 `N/A`。

## Evidence

- Requirement:
  - `docs/9.2.1/workitems/P1-runtime-api-auth-code-technical-closure.md`
- Test:
  - `mvn -pl foggy-runtime-api -Dtest=RuntimeApiAuthCodeGateTest test`: 9 passed.
  - `mvn -pl foggy-runtime-api test`: 41 passed.
  - `RuntimeApiAuthCodeGateTest#shouldRequireAuthCodeForRuntimeManagementOperationInventory`
  - `RuntimeApiAuthCodeGateTest#shouldLeaveReadAndExecutionEndpointsOutsideManagementAuthGate`
- Experience:
  - N/A. Backend/API-only technical closure; no UI or frontend interaction changed.
- Artifact:
  - `docs/dev-guide/bundle-namespace.md`

## Risks / Open Items

- Query/read and script execution endpoints remain outside this management gate by design.
- Auth-code remains a shared secret suitable for this internal technical boundary, not a customer-facing permission model.

## Failed Items

- none

## Final Decision

Decision: accepted.

The Runtime API auth-code technical closure meets its scoped acceptance criteria. Datasource test is now protected by the same auth-code gate, management coverage is locked by unit tests, and no complex security model was introduced.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-25
- acceptance_record: docs/9.2.1/acceptance/P1-runtime-api-auth-code-technical-closure-acceptance.md
- blocking_items: none
- follow_up_required: no
