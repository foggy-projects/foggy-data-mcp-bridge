---
acceptance_scope: feature
doc_role: feature_acceptance
doc_purpose: Sign off Runtime API auth-code management gate for 9.2.1.
version: 9.2.1
target: P1-runtime-api-auth-code-management-gate
status: signed-off
decision: accepted
signed_off_by: codex
signed_off_at: 2026-06-25
reviewed_by: codex
blocking_items: []
follow_up_required: no
evidence_count: 7
---

# Feature Acceptance

## Background

- Version: 9.2.1
- Target: P1-runtime-api-auth-code-management-gate
- Owner: `foggy-runtime-api`, `foggy-fsscript`
- Goal: 对 Runtime API 管理写操作增加轻量授权码 gate，并默认关闭旧 FSScript bundle 管理 Controller，避免未保护的管理写入口继续默认暴露。
- Scope boundary: 本次签收不引入用户、角色、租户级权限模型，不处理查询读取鉴权、授权码轮换、密钥托管或审计日志。

## Acceptance Basis

- [version README] `docs/9.2.1/README.md`
- [feature workitem] `docs/9.2.1/workitems/P1-runtime-api-auth-code-management-gate.md`
- [implementation quality gate] `docs/9.2.1/quality/P1-runtime-api-auth-code-management-gate-implementation-quality.md`
- [test coverage audit] `docs/9.2.1/coverage/P1-runtime-api-auth-code-management-gate-coverage-audit.md`

## Checklist

- [x] scope 内功能点已全部交付。
- [x] Runtime API 授权码配置、effective security mode 和 fail-closed 行为已实现。
- [x] 受保护管理写操作缺失或错误授权码时会被拒绝。
- [x] `X-Foggy-Runtime-Code` 和 `Authorization: Bearer` 成功路径已覆盖。
- [x] 旧 `foggy-fsscript` `/api/bundles/**` Controller 默认不装配，并保留显式 opt-in。
- [x] launcher 环境变量绑定和部署文档已更新。
- [x] 关键测试已通过。
- [x] 体验验证已完成，或明确标记 `N/A`。
- [x] 实现质量检查已完成，结论为 `ready-for-coverage-audit`。
- [x] 测试覆盖审计已完成，结论为 `ready-for-acceptance`。

## Evidence

- Requirement:
  - `docs/9.2.1/workitems/P1-runtime-api-auth-code-management-gate.md`
- Test:
  - `mvn -pl foggy-runtime-api -Dtest=RuntimeApiAuthCodeGateTest test`: 6 passed.
  - `mvn -pl foggy-fsscript -Dtest=BundleManagementControllerConditionalTest test`: 2 passed.
  - `mvn -pl foggy-runtime-api test`: 38 passed.
  - `mvn -pl foggy-fsscript test`: 362 passed.
  - `mvn -pl foggy-mcp-launcher -DskipTests compile`: build success.
- Experience:
  - N/A. Backend/API-only workitem; no UI page, form, dialog, button, or frontend data display change.
- Artifact:
  - `docs/9.2.1/quality/P1-runtime-api-auth-code-management-gate-implementation-quality.md`
  - `docs/9.2.1/coverage/P1-runtime-api-auth-code-management-gate-coverage-audit.md`
  - `docs/dev-guide/bundle-namespace.md`

## Risks / Open Items

- Query/read endpoints such as `query.execute` and `sql.query` remain outside this workitem by design.
- Auth-code is a shared secret, not per-user authorization; RBAC, audit, rotation, and credential storage remain non-goals.
- If legacy `/api/bundles/**` is explicitly re-enabled in a standalone FSScript-only app without runtime-api, deployment must provide its own protection.

## Failed Items

- none

## Final Decision

Decision: accepted.

The Runtime API auth-code management gate meets its scoped acceptance criteria. Required tests and evidence are present, implementation quality gate and coverage audit are complete, and no blocking defect was found inside the defined shared-secret gate scope.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-25
- acceptance_record: docs/9.2.1/acceptance/P1-runtime-api-auth-code-management-gate-acceptance.md
- blocking_items: none
- follow_up_required: no
