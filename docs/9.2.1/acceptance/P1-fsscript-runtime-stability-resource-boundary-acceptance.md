---
acceptance_scope: feature
version: 9.2.1
target: P1-fsscript-runtime-stability-resource-boundary
doc_role: acceptance-record
doc_purpose: 记录 foggy-fsscript 运行时稳定性与资源边界优化的功能级正式验收结论
status: signed-off
decision: accepted
signed_off_by: codex
signed_off_at: 2026-06-25
reviewed_by: codex
blocking_items: []
follow_up_required: yes
evidence_count: 10
---

# Feature Acceptance

## Document Purpose

- doc_type: acceptance
- intended_for: signoff-owner / reviewer / owning-module
- purpose: 记录 `P1-fsscript-runtime-stability-resource-boundary` 的正式验收结论与证据摘要。

## Background

- Version: 9.2.1
- Target: P1-fsscript-runtime-stability-resource-boundary
- Owner: `foggy-fsscript`
- Goal: 对 `foggy-fsscript` 模块自身的资源身份、import 边界、动态 bundle 并发、卸载清理、加载失败和异常处理风险完成运行时稳定性修复。
- Scope boundary: `/api/bundles` 管理接口暴露、鉴权策略、FSScript 沙箱和反射权限模型不属于本次签收范围。

## Acceptance Basis

- [version README] `docs/9.2.1/README.md`
- [feature workitem] `docs/9.2.1/workitems/P1-fsscript-runtime-stability-resource-boundary.md`
- [implementation quality gate] `docs/9.2.1/quality/P1-fsscript-runtime-stability-resource-boundary-implementation-quality.md`
- [test coverage audit] `docs/9.2.1/coverage/P1-fsscript-runtime-stability-resource-boundary-coverage-audit.md`

## Checklist

- [x] scope 内功能点已全部交付。
- [x] 原始 acceptance criteria 已逐项覆盖。
- [x] 关键测试已通过。
- [x] 体验验证已完成，或明确标记 `N/A`。
- [x] 文档、配置、依赖项已闭环。
- [x] 实现质量检查已完成，结论为 `ready-for-coverage-audit`。
- [x] 测试覆盖审计已完成，结论为 `ready-for-acceptance`。

## Evidence

- Requirement:
  - `docs/9.2.1/workitems/P1-fsscript-runtime-stability-resource-boundary.md`
- Test:
  - `mvn -pl foggy-fsscript test`: 360 passed.
  - `mvn -pl foggy-core test`: 91 passed.
  - `mvn -pl foggy-dataset-model,foggy-dataset-mcp -am -DskipTests compile`: build success.
  - `ResourceFsscriptClosureDefinitionSpaceTest`: resource identity coverage.
  - `FsscriptImportBoundaryTest`: import containment coverage.
  - `DynamicBundleManagementTest`: dynamic bundle concurrency/snapshot coverage.
  - `DynamicBundleLifecycleTest`: cache and watcher cleanup coverage.
  - `BundleLoadFailureTest`: stale cached-load and serious-loader-error coverage.
  - `FsscriptRuntimeFailureTest` and `ImportBeanExpFailureTest`: serious `Error` propagation coverage.
  - `rg -n "catch\s*\(\s*Throwable|printStackTrace\s*\(" foggy-fsscript/src/main/java`: no matches.
- Experience:
  - N/A. Backend/runtime-only workitem; no UI page, form, dialog, button, or frontend data display change.
- Artifact:
  - `docs/9.2.1/quality/P1-fsscript-runtime-stability-resource-boundary-implementation-quality.md`
  - `docs/9.2.1/coverage/P1-fsscript-runtime-stability-resource-boundary-coverage-audit.md`

## Failed Items

- none

## Risks / Open Items

- Security management endpoint exposure and authorization policy remain intentionally out of scope. This should be evaluated as the next separate security workstream.
- `.claude/skills/**` deletion exists in the working tree but is unrelated to this workitem and is excluded from this signoff scope.

## Final Decision

Decision: accepted.

The `foggy-fsscript` runtime stability and resource boundary workitem meets its scoped acceptance criteria. Required tests and evidence are present, implementation quality gate and coverage audit are complete, and no blocking defect was found inside the defined non-security scope.

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex
- signed_off_at: 2026-06-25
- acceptance_record: docs/9.2.1/acceptance/P1-fsscript-runtime-stability-resource-boundary-acceptance.md
- blocking_items: none
- follow_up_required: yes
